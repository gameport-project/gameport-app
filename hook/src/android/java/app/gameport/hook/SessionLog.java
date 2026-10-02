package app.gameport.hook;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
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
    private static final Uri BASE = Uri.parse("content://app.gameport.cloud");
    private static final int HEAD_LINES = 1500;
    private static final int TAIL_LINES = 3500;
    private static final int MAX_ENGINE_FILES = 4;
    private static final int MAX_ENGINE_BYTES = 120_000;
    private static final int MAX_TRACE_BYTES = 1_500_000;
    /** The first minutes are the ones that explain a game that closes at once: send often, then rarely. */
    private static final long[] EARLY_FLUSHES_MS = {2_000, 5_000, 10_000, 20_000, 40_000};
    private static final long LATER_FLUSH_MS = 30_000;

    private final Context context;
    private final ArrayList<String> head = new ArrayList<>();
    private final ArrayDeque<String> tail = new ArrayDeque<>();
    /** The runtime's once-a-second performance lines (frame rate, CPU and GPU levels), kept apart because they would flood the log. */
    private final ArrayDeque<String> performance = new ArrayDeque<>();
    private static final int PERFORMANCE_LINES = 180;
    private Handler handler;
    private int version;
    private int sentVersion = -1;
    private boolean exitSent;
    private int flushes;
    private volatile String launchInfo = "";
    private String lastEngineDigest = "";

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
        scheduleNext();
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
                synchronized (head) {
                    if (head.size() < HEAD_LINES) head.add(line);
                    else {
                        tail.addLast(line);
                        if (tail.size() > TAIL_LINES) tail.removeFirst();
                    }
                    version++;
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
        synchronized (head) {
            at = version;
            if (at == sentVersion && exitSent) return;
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
        extras.putString("launch", launchInfo);
        extras.putString("libraries", loadedLibraries());
        String engine = engineLogs();
        String digest = Integer.toHexString(engine.hashCode());
        if (!digest.equals(lastEngineDigest) && !engine.isEmpty()) {
            extras.putByteArray("engineGz", gzip(engine));
            lastEngineDigest = digest;
        }
        if (!exitSent) addPreviousExit(extras);
        try {
            Bundle result = context.getContentResolver().call(BASE, "log", context.getPackageName(), extras);
            if (result != null) {
                sentVersion = at;
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
