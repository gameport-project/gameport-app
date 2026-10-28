package app.gameport.hook;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

/**
 * Collects what a problem report needs from inside the game, since an app only sees its own process, and hands it
 * to GamePort: the game's system log (the first lines, which hold the start, and the latest), the engine's own log
 * files, how the last runs ended with the crash trace Android kept for the game, the libraries really loaded, and
 * how the game was started. It keeps sending while the game runs, so a crash leaves something behind.
 */
final class SessionLog {
    private static final String TAG = "GPHook";
    private static final int HEAD_LINES = 1500;
    private static final int TAIL_LINES = 3500;
    private static final int MAX_ENGINE_FILES = 4;
    private static final int MAX_ENGINE_BYTES = 120_000;
    private static final int MAX_TRACE_BYTES = 1_500_000;
    /** The first minutes are the ones that explain a game that closes at once: send often, then rarely. */
    // Close together at first: an engine writes its log in bursts, and a game that fails does so within seconds of its start.
    private static final long[] EARLY_FLUSHES_MS = {2_000, 4_000, 6_000, 8_000, 10_000, 14_000, 20_000, 30_000, 40_000};
    private static final long LATER_FLUSH_MS = 30_000;

    private final Context context;
    private final ArrayList<String> head = new ArrayList<>();
    private final ArrayDeque<String> tail = new ArrayDeque<>();

    /** What the game asked for natively and did not find, seen in its whole log and not only the lines kept (see [NativeGaps]). */
    private final NativeGaps nativeGaps = new NativeGaps();
    /** The runtime's once-a-second performance lines (frame rate, CPU and GPU levels), kept apart because they would flood the log. */
    private final ArrayDeque<String> performance = new ArrayDeque<>();
    private static final int PERFORMANCE_LINES = 180;
    /** How much data the game has received and sent, sampled every few seconds: tells whether a transfer that looks stuck moves at all. */
    private final ArrayDeque<String> network = new ArrayDeque<>();
    private static final int NETWORK_LINES = 300;
    private static final long NETWORK_EVERY_MS = 5_000;
    private int networkVersion;
    private int sentNetworkVersion = -1;
    /** What each thread of the game was doing at a few moments (state and processor time), to tell a game that waits from one that works when its log stops. */
    private final ArrayList<String> threads = new ArrayList<>();
    private static final long[] THREAD_SNAPSHOTS_MS = {8_000, 20_000, 45_000};
    private int threadsVersion;
    private int sentThreadsVersion = -1;
    /** Files of diagnostics from {@link Diagnostics}, by name: each is sent as a file of the report (diag-<name>.txt). */
    private final java.util.LinkedHashMap<String, String> diagnostics = new java.util.LinkedHashMap<>();
    private int diagnosticsVersion;
    private int sentDiagnosticsVersion = -1;
    private Handler handler;
    private int version;
    private int sentVersion = -1;
    private boolean exitSent;
    private int flushes;
    /** Counts the performance lines, so a flush knows they changed even when the main log is quiet. */
    private int performanceVersion;
    private int sentPerformanceVersion = -1;
    private String sentLibraries = "";
    private volatile String launchInfo = "";
    private String lastEngineDigest = "";
    private volatile boolean crashFlushed = false;

    SessionLog(Context context) {
        this.context = context.getApplicationContext();
    }

    /** How the game was started (action, categories, flags, extra names), recorded when its first screen is created. */
    void setLaunchInfo(String info) {
        launchInfo = info;
    }

    void start() {
        HandlerThread thread = new HandlerThread("gameport-log");
        thread.start();
        handler = new Handler(thread.getLooper());
        Thread reader = new Thread(new Runnable() {
            @Override public void run() { follow(); }
        }, "gameport-logcat");
        reader.setDaemon(true);
        reader.start();
        Thread perf = new Thread(new Runnable() {
            @Override public void run() { followPerformance(); }
        }, "gameport-perf");
        perf.setDaemon(true);
        perf.start();
        Thread net = new Thread(new Runnable() {
            @Override public void run() { followNetwork(); }
        }, "gameport-net");
        net.setDaemon(true);
        net.start();
        new Diagnostics(context, this).start();
        Thread snapshots = new Thread(new Runnable() {
            @Override public void run() { followThreads(); }
        }, "gameport-threads");
        snapshots.setDaemon(true);
        snapshots.start();
        scheduleNext();
    }

    /** Keeps a diagnostic file (replacing the one of the same name); it goes to GamePort with the next hand-over. */
    void putDiagnostic(String name, String text) {
        synchronized (diagnostics) {
            diagnostics.put(name, text);
            diagnosticsVersion++;
        }
    }

    /** Writes the state of every thread of the game (read from /proc, which a process may read for itself) at a few moments after its start. */
    private void followThreads() {
        try {
            long started = System.currentTimeMillis();
            for (long at : THREAD_SNAPSHOTS_MS) {
                long wait = at - (System.currentTimeMillis() - started);
                if (wait > 0) Thread.sleep(wait);
                StringBuilder text = new StringBuilder("after " + ((System.currentTimeMillis() - started) / 1000) + " s (tid, name, state S=sleeping R=running D=blocked, processor ticks user+system):\n");
                File[] tasks = new File("/proc/self/task").listFiles();
                if (tasks != null) {
                    Arrays.sort(tasks);
                    for (File task : tasks) {
                        try {
                            String stat = readSmall(new File(task, "stat"));
                            int open = stat.indexOf('('), close = stat.lastIndexOf(')');
                            if (open < 0 || close < open) continue;
                            String[] rest = stat.substring(close + 2).split(" ");
                            // After the name: state is field 0, utime field 11, stime field 12.
                            String state = rest.length > 0 ? rest[0] : "?";
                            long ticks = rest.length > 12 ? Long.parseLong(rest[11]) + Long.parseLong(rest[12]) : -1;
                            text.append(task.getName()).append(' ').append(stat, open + 1, close).append(' ').append(state).append(' ').append(ticks).append('\n');
                        } catch (Throwable ignored) {
                            // A thread that ended meanwhile.
                        }
                    }
                }
                synchronized (threads) {
                    threads.add(text.toString());
                    threadsVersion++;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "the threads of the game cannot be listed", t);
        }
    }

    private static String readSmall(File file) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[2048];
            int n = in.read(buffer);
            return n <= 0 ? "" : new String(buffer, 0, n, "UTF-8").trim();
        }
    }

    /**
     * Samples the bytes this game received and sent (the system's counters for its own uid, which any app may read). A line is kept when something moved, and
     * at least every half minute otherwise, so a quiet stretch shows as one.
     */
    private void followNetwork() {
        try {
            int uid = android.os.Process.myUid();
            long started = System.currentTimeMillis();
            long lastRx = TrafficStats.getUidRxBytes(uid);
            long lastTx = TrafficStats.getUidTxBytes(uid);
            if (lastRx == TrafficStats.UNSUPPORTED || lastTx == TrafficStats.UNSUPPORTED) {
                synchronized (network) { network.addLast("the system does not count this game's traffic"); networkVersion++; }
                return;
            }
            long lastWritten = 0;
            while (true) {
                Thread.sleep(NETWORK_EVERY_MS);
                long rx = TrafficStats.getUidRxBytes(uid);
                long tx = TrafficStats.getUidTxBytes(uid);
                long now = System.currentTimeMillis();
                long moved = (rx - lastRx) + (tx - lastTx);
                if (moved > 0 || now - lastWritten >= 30_000) {
                    String line = ((now - started) / 1000) + " s: received " + rx + " B (+" + (rx - lastRx) + "), sent " + tx + " B (+" + (tx - lastTx) + ")";
                    synchronized (network) {
                        network.addLast(line);
                        if (moved > 0) networkVersion++;
                        if (network.size() > NETWORK_LINES) network.removeFirst();
                    }
                    lastWritten = now;
                }
                lastRx = rx;
                lastTx = tx;
            }
        } catch (Throwable t) {
            Log.w(TAG, "the game's traffic cannot be followed", t);
        }
    }

    /** Sends what is new now (the game left the screen or is closing). */
    void flushSoon() {
        if (handler != null) handler.post(new Runnable() {
            @Override public void run() { flush(); }
        });
    }

    /** Sends what is new on the calling thread; for the last moments of the process. */
    void flushNow() {
        flush();
    }

    private void scheduleNext() {
        long delay = flushes < EARLY_FLUSHES_MS.length
                ? EARLY_FLUSHES_MS[flushes] - (flushes == 0 ? 0 : EARLY_FLUSHES_MS[flushes - 1])
                : LATER_FLUSH_MS;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                flushes++;
                flush();
                scheduleNext();
            }
        }, delay);
    }

    private void follow() {
        try {
            // Everything from information up, minus the tags that only repeat themselves every second.
            Process process = new ProcessBuilder(
                    "logcat", "-v", "threadtime", "*:I",
                    "UE:V", "UE4:V", "Unity:V", "GPHook:V", "GPSteam:V", "libc:V",
                    "VrApi:S", "ClientSharedTelemetryStats:S", "TrafficStats:S", "Choreographer:S", "HeapTaskDaemon:S",
                    "OpenGLRenderer:S", "AdrenoGLES-0:S", "NetworkChangedManager:S")
                    .redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                nativeGaps.scan(line);
                synchronized (head) {
                    if (head.size() < HEAD_LINES) head.add(line);
                    else {
                        tail.addLast(line);
                        if (tail.size() > TAIL_LINES) tail.removeFirst();
                    }
                    version++;
                }
                // A native crash writes this line from the crashing process, which then waits a moment for the crash dumper: that moment is enough to send the log
                // and the engine's own log files, which would be lost with the process otherwise.
                if (!crashFlushed && line.contains("Fatal signal ")) {
                    crashFlushed = true;
                    try { flush(); } catch (Throwable t) { Log.w(TAG, "the log cannot be sent at the crash", t); }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "the game's log cannot be followed", t);
        }
    }

    private void followPerformance() {
        try {
            Process process = new ProcessBuilder("logcat", "-v", "threadtime", "VrApi:I", "*:S").redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (performance) {
                    performance.addLast(line);
                    performanceVersion++;
                    if (performance.size() > PERFORMANCE_LINES) performance.removeFirst();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "the performance lines cannot be followed", t);
        }
    }

    private void flush() {
        String headText;
        String tailText;
        int at;
        // What is sent is more than the log: the libraries the game has loaded by now and the performance lines change without a line of the log,
        // and a game that logs little would leave them as they were a second after its start.
        String libraries = loadedLibraries();
        String librariesSignature = librariesSignature(libraries);
        int performanceAt;
        synchronized (performance) { performanceAt = performanceVersion; }
        int networkAt;
        synchronized (network) { networkAt = networkVersion; }
        int threadsAt;
        synchronized (threads) { threadsAt = threadsVersion; }
        int diagnosticsAt;
        synchronized (diagnostics) { diagnosticsAt = diagnosticsVersion; }
        synchronized (head) {
            at = version;
            if (at == sentVersion && performanceAt == sentPerformanceVersion && networkAt == sentNetworkVersion && threadsAt == sentThreadsVersion && diagnosticsAt == sentDiagnosticsVersion && librariesSignature.equals(sentLibraries) && exitSent) return;
            StringBuilder h = new StringBuilder();
            for (String line : head) h.append(line).append('\n');
            StringBuilder t = new StringBuilder();
            for (String line : tail) t.append(line).append('\n');
            headText = h.toString();
            tailText = t.toString();
        }
        Bundle extras = new Bundle();
        extras.putByteArray("headGz", gzip(headText));
        extras.putByteArray("logGz", gzip(tailText));
        StringBuilder perf = new StringBuilder();
        synchronized (performance) { for (String line : performance) perf.append(line).append('\n'); }
        extras.putByteArray("performanceGz", gzip(perf.toString()));
        StringBuilder traffic = new StringBuilder();
        synchronized (network) { for (String line : network) traffic.append(line).append('\n'); }
        extras.putByteArray("networkGz", gzip(traffic.toString()));
        StringBuilder snapshots = new StringBuilder();
        synchronized (threads) { for (String text : threads) snapshots.append(text).append('\n'); }
        extras.putByteArray("threadsGz", gzip(snapshots.toString()));
        StringBuilder diag = new StringBuilder();
        synchronized (diagnostics) {
            for (java.util.Map.Entry<String, String> file : diagnostics.entrySet()) diag.append('\u0001').append(file.getKey()).append('\n').append(file.getValue()).append('\n');
        }
        extras.putByteArray("diagGz", gzip(diag.toString()));
        extras.putString("launch", launchInfo);
        extras.putString("nativeGaps", nativeGaps.report());
        extras.putString("libraries", libraries);
        // Which run this is: GamePort keeps the log of the run before when a new one begins.
        extras.putInt("pid", android.os.Process.myPid());
        String engine = engineLogs();
        String digest = Integer.toHexString(engine.hashCode());
        if (!digest.equals(lastEngineDigest) && !engine.isEmpty()) {
            extras.putByteArray("engineGz", gzip(engine));
            lastEngineDigest = digest;
        }
        if (!exitSent) addPreviousExit(extras);
        try {
            Bundle result = Calls.call(context, Owner.cloud(context), "log", context.getPackageName(), extras);
            if (result != null) {
                sentVersion = at;
                sentPerformanceVersion = performanceAt;
                sentNetworkVersion = networkAt;
                sentThreadsVersion = threadsAt;
                sentDiagnosticsVersion = diagnosticsAt;
                sentLibraries = librariesSignature;
                exitSent = true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not hand the log to GamePort", t);
        }
    }

    private static byte[] gzip(String text) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream zip = new GZIPOutputStream(out);
            zip.write(text.getBytes("UTF-8"));
            zip.close();
            return out.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private static byte[] gzip(byte[] bytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream zip = new GZIPOutputStream(out);
            zip.write(bytes);
            zip.close();
            return out.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    /**
     * What tells that the game has loaded another library: the code files only. The other mappings (memory shared with the graphics driver, caches)
     * come and go all the time, and would make every look a change.
     */
    private static String librariesSignature(String libraries) {
        StringBuilder out = new StringBuilder();
        for (String line : libraries.split("\n")) {
            if (line.endsWith(".so") || line.endsWith(".apk") || line.endsWith(".jar") || line.endsWith(".dex") || line.endsWith(".vdex")
                    || line.endsWith(".odex") || line.endsWith(".oat") || line.endsWith(".art")) out.append(line).append('\n');
        }
        return out.toString();
    }

    /** The files this process has mapped (game libraries, GamePort's shim, the OpenXR runtime in use): one path per line. */
    private static String loadedLibraries() {
        TreeSet<String> paths = new TreeSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int slash = line.indexOf('/');
                if (slash > 0) paths.add(line.substring(slash));
            }
        } catch (Throwable t) {
            return "(not readable)";
        }
        StringBuilder out = new StringBuilder();
        for (String path : paths) out.append(path).append('\n');
        return out.toString();
    }

    /** The newest log files the game's engine wrote in the game's own folders (Unreal's Saved/Logs, for one), their last lines. */
    private String engineLogs() {
        try {
            List<File> found = new ArrayList<>();
            File[] roots = {context.getExternalFilesDir(null), context.getFilesDir(), context.getCacheDir()};
            long since = System.currentTimeMillis() - 3L * 24 * 3600 * 1000;
            for (File root : roots) if (root != null) collect(root, 0, since, found);
            Collections.sort(found, new java.util.Comparator<File>() {
                @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
            });
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(MAX_ENGINE_FILES, found.size()); i++) {
                File file = found.get(i);
                out.append("===== ").append(file.getAbsolutePath()).append(" (").append(file.length()).append(" bytes)\n");
                out.append(tailOf(file, MAX_ENGINE_BYTES)).append('\n');
            }
            return out.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static void collect(File directory, int depth, long since, List<File> out) {
        if (depth > 7) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                // GamePort's own save backups are not the engine's.
                if (!child.getName().startsWith("gameport")) collect(child, depth + 1, since, out);
            } else if (child.getName().endsWith(".log") && child.lastModified() >= since && child.length() > 0) {
                out.add(child);
            }
        }
    }

    private static String tailOf(File file, int bytes) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = in.length();
            long start = Math.max(0, length - bytes);
            in.seek(start);
            byte[] buffer = new byte[(int) (length - start)];
            in.readFully(buffer);
            return new String(buffer, "UTF-8");
        } catch (Throwable t) {
            return "(not readable)";
        }
    }

    /** How the last runs of this game ended, as Android recorded it, and the native crash trace when there was one. */
    private void addPreviousExit(Bundle extras) {
        if (Build.VERSION.SDK_INT < 30) {
            extras.putString("previousExit", "");
            return;
        }
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5);
            StringBuilder out = new StringBuilder();
            boolean traced = false;
            for (ApplicationExitInfo info : exits) {
                out.append("time=").append(info.getTimestamp())
                        .append(" reason=").append(reasonName(info.getReason()))
                        .append(" status=").append(info.getStatus())
                        .append(" importance=").append(info.getImportance())
                        .append(" pss=").append(info.getPss())
                        .append(" rss=").append(info.getRss())
                        .append(" description=").append(info.getDescription())
                        .append('\n');
                int reason = info.getReason();
                // Horizon reports some native crashes as "signaled" with the fault signal as status.
                boolean fault = reason == ApplicationExitInfo.REASON_SIGNALED && Arrays.asList(4, 6, 7, 8, 11).contains(info.getStatus());
                if (!traced && (reason == ApplicationExitInfo.REASON_CRASH_NATIVE || reason == ApplicationExitInfo.REASON_ANR || fault)) {
                    byte[] trace = readTrace(info);
                    if (trace != null) {
                        extras.putByteArray("traceGz", gzip(trace));
                        extras.putLong("traceTime", info.getTimestamp());
                        extras.putString("traceKind", reasonName(reason));
                        traced = true;
                    }
                }
            }
            extras.putString("previousExit", out.toString());
        } catch (Throwable t) {
            extras.putString("previousExit", "");
        }
    }

    private static byte[] readTrace(ApplicationExitInfo info) {
        try (InputStream in = info.getTraceInputStream()) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0 && out.size() < MAX_TRACE_BYTES) out.write(buffer, 0, read);
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_CRASH: return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
            default: return "OTHER_" + reason;
        }
    }
}
