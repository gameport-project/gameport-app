package app.gameport.hook;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A small record of how the run goes, kept in a file of the game's own folder: it does not depend on GamePort being there. Everything else the hook collects lives in memory and goes
 * to GamePort over a connection that breaks when the headset stops GamePort, or with the process itself; this stays, and the run that follows hands it over as the record of the run before.
 *
 * Every two seconds a line says how the run is: the sends to GamePort that worked and failed, the lines of the system log read, how many threads the game has and what they do (the one
 * that works the most, the ones blocked in the kernel and where). A thread of its own writes it, one second apart: a gap between two beats is a stretch where the process did not run
 * (frozen, or stopped by the system). The file is bounded (two files of about 100 kilobytes), and nothing in it says who plays or what: times, counters and names of threads.
 *
 * Nothing here may ever disturb the game: every step is guarded, and a failure is silent.
 */
final class BlackBox {
    private static final String TAG = "GPHook";
    private static final long MAX_BYTES = 100_000;
    private static final int PREVIOUS_CHARS = 60_000;
    private static final long BEAT_MS = 1_000;
    private static final long GAP_NOTED_MS = 2_500;

    private final File file;
    private final File older;
    private final File carried;
    private final long startedAt = SystemClock.elapsedRealtime();
    private final Object lock = new Object();
    private final AtomicLong logLines = new AtomicLong();
    private volatile int sendsOk;
    private volatile int sendsFailed;
    private volatile String lastSend = "none";
    private volatile String previous = "";

    BlackBox(Context context) {
        File folder = new File(context.getExternalFilesDir(null), "gameport");
        folder.mkdirs();
        file = new File(folder, "blackbox.txt");
        older = new File(folder, "blackbox.old.txt");
        carried = new File(folder, "blackbox.prev.txt");
        previous = takeOverTheRunBefore();
    }

    /**
     * What the run before left, to be handed over: the record it kept, added after what an earlier run left that was never handed over (GamePort was not there to take it). It stays in
     * its own file until {@link #previousHandedOver()}, so a run that ends before it could be sent does not lose it.
     */
    private String takeOverTheRunBefore() {
        try {
            String text = read(carried) + read(older) + read(file);
            older.delete();
            file.delete();
            if (text.isEmpty()) return "";
            if (text.length() > PREVIOUS_CHARS) text = text.substring(text.length() - PREVIOUS_CHARS);
            write(carried, text);
            return text;
        } catch (Throwable t) {
            return "";
        }
    }

    /** The record of the runs before this one that was not handed over yet, empty when there is none. */
    String previous() {
        return previous;
    }

    /** GamePort took it: it is not kept any more, and not sent again. */
    void previousHandedOver() {
        previous = "";
        try {
            carried.delete();
        } catch (Throwable ignored) {
            // kept for the next try
        }
    }

    /** One more line of the system log was read: the count tells a game that stopped writing from a reader that stopped reading. */
    void line() {
        logLines.incrementAndGet();
    }

    /** What a hand-over to GamePort came to, in words and in the counters of the beats. */
    void sendResult(boolean worked, long millis, String detail) {
        if (worked) sendsOk++;
        else sendsFailed++;
        lastSend = (worked ? "ok " : "failed ") + millis + "ms" + (detail == null || detail.isEmpty() ? "" : " " + detail);
        note("send " + lastSend);
    }

    /** One line, dated in seconds from the start of this process. */
    void note(String text) {
        try {
            String line = String.format(Locale.ROOT, "+%.1fs %s\n", (SystemClock.elapsedRealtime() - startedAt) / 1000.0, text);
            synchronized (lock) {
                if (file.length() > MAX_BYTES) {
                    older.delete();
                    file.renameTo(older);
                }
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(line.getBytes("UTF-8"));
                }
            }
        } catch (Throwable ignored) {
            // the record is a help, never a duty
        }
    }

    void start() {
        Thread beat = new Thread(new Runnable() {
            @Override public void run() { beat(); }
        }, "gameport-box");
        beat.setDaemon(true);
        beat.start();
    }

    private void beat() {
        try {
            note("start pid=" + android.os.Process.myPid() + " sdk=" + Build.VERSION.SDK_INT);
            long last = SystemClock.elapsedRealtime();
            int tick = 0;
            Map<Integer, Long> ticks = new HashMap<>();
            while (true) {
                Thread.sleep(BEAT_MS);
                long now = SystemClock.elapsedRealtime();
                long gap = now - last;
                last = now;
                tick++;
                // This thread sleeps one second: more than that between two beats is a stretch where the process did not run.
                if (gap > GAP_NOTED_MS) note("GAP " + gap + "ms between two beats: the process did not run for that long");
                if (tick % 2 == 0) note(state(ticks));
            }
        } catch (InterruptedException e) {
            // the process ends
        } catch (Throwable t) {
            Log.w(TAG, "the beat of the record stopped", t);
        }
    }

    /** How the run is at this moment, in one line: see the class's words. */
    private String state(Map<Integer, Long> before) {
        StringBuilder out = new StringBuilder("beat sends=").append(sendsOk).append('/').append(sendsFailed)
                .append(" last=").append(lastSend).append(" logLines=").append(logLines.get());
        try {
            File[] tasks = new File("/proc/self/task").listFiles();
            if (tasks == null) return out.append(" threads=?").toString();
            int threads = 0;
            int blocked = 0;
            String busiest = "";
            long busiestDelta = 0;
            long ownDelta = 0;
            StringBuilder where = new StringBuilder();
            Map<Integer, Long> now = new HashMap<>();
            for (File task : tasks) {
                try {
                    String stat = small(new File(task, "stat"));
                    int open = stat.indexOf('('), close = stat.lastIndexOf(')');
                    if (open < 0 || close < open) continue;
                    String[] rest = stat.substring(close + 2).split(" ");
                    int tid = Integer.parseInt(task.getName());
                    long ticks = rest.length > 12 ? Long.parseLong(rest[11]) + Long.parseLong(rest[12]) : 0;
                    threads++;
                    now.put(tid, ticks);
                    Long was = before.get(tid);
                    long delta = was == null ? 0 : ticks - was;
                    String name = stat.substring(open + 1, close);
                    // What GamePort's own threads cost, in the same ticks (a hundredth of a second of one core): it says in every report whether the hook weighs on the game.
                    if (name.startsWith("gameport")) ownDelta += delta;
                    if (delta > busiestDelta) {
                        busiestDelta = delta;
                        busiest = name;
                    }
                    // A thread blocked in the kernel (uninterruptible: the disk, a driver) and what it waits in, which a thread that merely sleeps does not show.
                    if ("D".equals(rest[0])) {
                        blocked++;
                        if (where.length() < 160) where.append(' ').append(name).append('=').append(wait(task));
                    }
                } catch (Throwable ignored) {
                    // a thread that ended meanwhile
                }
            }
            before.clear();
            before.putAll(now);
            out.append(" threads=").append(threads).append(" busiest=").append(busiest.isEmpty() ? "-" : busiest + ":" + busiestDelta).append(" gameportTicks=").append(ownDelta)
                    .append(" blocked=").append(blocked).append(where);
            out.append(" rssMB=").append(residentMegabytes());
        } catch (Throwable t) {
            out.append(" threads=?");
        }
        return out.toString();
    }

    /** Where a blocked thread waits in the kernel, or "?" when the system does not say. */
    private static String wait(File task) {
        try {
            String text = small(new File(task, "wchan")).trim();
            return text.isEmpty() || "0".equals(text) ? "?" : text;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static long residentMegabytes() {
        try {
            String[] fields = small(new File("/proc/self/statm")).trim().split(" ");
            return Long.parseLong(fields[1]) * 4 / 1024;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String small(File file) throws java.io.IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[2048];
            int n = in.read(buffer);
            return n <= 0 ? "" : new String(buffer, 0, n, "UTF-8");
        }
    }

    private static String read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0 && out.size() < 400_000) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static void write(File f, String text) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(text.getBytes("UTF-8"));
        }
    }
}
