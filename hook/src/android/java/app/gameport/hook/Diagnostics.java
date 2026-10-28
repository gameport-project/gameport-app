package app.gameport.hook;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;

/**
 * What a report needs to tell many kinds of trouble apart, gathered once in the game's own process and handed over as files of the report (see
 * {@link SessionLog#putDiagnostic}): when the screens of the game came and went, whether the game can write where it keeps its data, whether the device reaches the
 * servers online games use, and what each thread was doing at a few moments. Nothing here changes what the game does, and nothing leaves the device but the report
 * the player sends.
 */
final class Diagnostics {
    private static final String TAG = "GPHook";
    private static final long[] STACK_MOMENTS_MS = {5_000, 10_000, 20_000, 40_000, 80_000};
    /** Servers that online games of the headsets reach: a connection to the port 443 only (no request is made), to tell a blocked or broken network from a service that refuses. */
    private static final String[] HOSTS = {"api.epicgames.dev", "api.steampowered.com", "api.gameanalytics.com", "www.google.com"};

    private final Context context;
    private final SessionLog sink;
    private final long started = System.currentTimeMillis();
    private final ArrayDeque<String> lifecycle = new ArrayDeque<>();

    Diagnostics(Context context, SessionLog sink) {
        this.context = context.getApplicationContext();
        this.sink = sink;
    }

    void start() {
        watchScreens();
        later("storage", 3_000, new Runnable() { @Override public void run() { sink.putDiagnostic("storage", storage()); } });
        later("network", 4_000, new Runnable() { @Override public void run() { network(); } });
        later("stacks", STACK_MOMENTS_MS[0], new Runnable() { @Override public void run() { stacks(); } });
    }

    private void later(String name, final long afterMs, final Runnable work) {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(afterMs);
                    work.run();
                } catch (Throwable t) {
                    Log.w(TAG, "a diagnostic stopped", t);
                }
            }
        }, "gameport-diag-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    private String since() {
        return String.format(java.util.Locale.US, "%6.2f s", (System.currentTimeMillis() - started) / 1000.0);
    }

    /** When each screen of the game was created, shown, paused and ended: a game that never reaches the screen is told from one that does and then stops. */
    private void watchScreens() {
        if (!(context instanceof Application)) return;
        ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            private void note(Activity activity, String event) {
                synchronized (lifecycle) {
                    lifecycle.addLast(since() + "  " + activity.getClass().getSimpleName() + "  " + event);
                    if (lifecycle.size() > 200) lifecycle.removeFirst();
                    StringBuilder text = new StringBuilder();
                    for (String line : lifecycle) text.append(line).append('\n');
                    sink.putDiagnostic("lifecycle", text.toString());
                }
            }
            @Override public void onActivityCreated(Activity a, Bundle b) { note(a, "created"); }
            @Override public void onActivityStarted(Activity a) { note(a, "started"); }
            @Override public void onActivityResumed(Activity a) { note(a, "resumed"); }
            @Override public void onActivityPaused(Activity a) { note(a, "paused"); }
            @Override public void onActivityStopped(Activity a) { note(a, "stopped"); }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { note(a, "destroyed" + (a.isFinishing() ? " (finishing)" : "")); }
        });
    }

    private String granted(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "granted" : "not granted";
    }

    /** Whether the game can read and write where it keeps its files and the expansion files, tried with a small file of its own that is removed at once. */
    private String storage() {
        StringBuilder t = new StringBuilder();
        t.append("READ_EXTERNAL_STORAGE: ").append(granted("android.permission.READ_EXTERNAL_STORAGE")).append('\n');
        t.append("WRITE_EXTERNAL_STORAGE: ").append(granted("android.permission.WRITE_EXTERNAL_STORAGE")).append('\n');
        if (Build.VERSION.SDK_INT >= 30) {
            try { t.append("all files access: ").append(Environment.isExternalStorageManager()).append('\n'); } catch (Throwable ignored) { }
        }
        File[] places = {context.getFilesDir(), context.getCacheDir(), context.getExternalFilesDir(null), context.getObbDir(), Environment.getExternalStorageDirectory()};
        String[] names = {"internal files", "cache", "external files (Android/data)", "expansion files (Android/obb)", "shared storage root"};
        for (int i = 0; i < places.length; i++) {
            File place = places[i];
            t.append('\n').append(names[i]).append(": ");
            if (place == null) { t.append("none\n"); continue; }
            t.append(place.getPath()).append('\n');
            t.append("  exists=").append(place.exists()).append(" read=").append(place.canRead()).append(" write=").append(place.canWrite())
                    .append(" free=").append(place.getUsableSpace() / (1024 * 1024)).append(" MB\n");
            t.append("  write test: ").append(writeTest(place)).append('\n');
            String[] children = place.list();
            if (children != null) {
                Arrays.sort(children);
                t.append("  holds ").append(children.length).append(" entries");
                int shown = 0;
                for (String child : children) {
                    if (shown++ >= 25) { t.append(" ..."); break; }
                    t.append(shown == 1 ? ": " : ", ").append(child);
                }
                t.append('\n');
            }
        }
        return t.toString();
    }

    private static String writeTest(File folder) {
        if (!folder.exists()) return "the folder does not exist";
        File file = null;
        try {
            file = File.createTempFile("gpprobe", ".tmp", folder);
            FileOutputStream out = new FileOutputStream(file);
            out.write(new byte[1024]);
            out.close();
            FileInputStream in = new FileInputStream(file);
            int n = in.read(new byte[1024]);
            in.close();
            return n == 1024 ? "written and read back" : "read back " + n + " bytes";
        } catch (Throwable t) {
            return "failed (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")";
        } finally {
            if (file != null) file.delete();
        }
    }

    /** The state of the network as the system sees it, and whether the servers online games use can be reached (name resolution and a connection to port 443 only). */
    private void network() {
        StringBuilder t = new StringBuilder();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            LinkProperties link = active == null ? null : cm.getLinkProperties(active);
            if (caps == null) {
                t.append("no active network\n");
            } else {
                t.append("wifi=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).append(" cellular=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                        .append(" ethernet=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)).append(" vpn=").append(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).append('\n');
                t.append("internet=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).append(" validated=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        .append(" metered=").append(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                        .append(" captive portal=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)).append('\n');
            }
            if (link != null) {
                t.append("dns servers=").append(link.getDnsServers().size());
                if (Build.VERSION.SDK_INT >= 28) t.append(" private dns active=").append(link.isPrivateDnsActive()).append(" named=").append(link.getPrivateDnsServerName() != null);
                t.append(" proxy=").append(link.getHttpProxy() != null).append('\n');
            }
        } catch (Throwable e) {
            t.append("the network state cannot be read (").append(e.getClass().getSimpleName()).append(")\n");
        }
        sink.putDiagnostic("network-state", t.toString());
        StringBuilder reach = new StringBuilder();
        for (String host : HOSTS) {
            long begin = System.currentTimeMillis();
            String address;
            try {
                InetAddress[] all = InetAddress.getAllByName(host);
                address = all.length + " address(es) in " + (System.currentTimeMillis() - begin) + " ms";
                long connect = System.currentTimeMillis();
                Socket socket = new Socket();
                try {
                    socket.connect(new InetSocketAddress(all[0], 443), 5_000);
                    address += ", connected in " + (System.currentTimeMillis() - connect) + " ms";
                } finally {
                    try { socket.close(); } catch (Throwable ignored) { }
                }
            } catch (Throwable e) {
                address = "failed after " + (System.currentTimeMillis() - begin) + " ms (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
            }
            reach.append(host).append(": ").append(address).append('\n');
            sink.putDiagnostic("reach", reach.toString());
        }
    }

    /** What the main thread and the others were doing, as Java sees them, at a few moments: a game that does not answer shows where its screen thread waits. */
    private void stacks() {
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < STACK_MOMENTS_MS.length; i++) {
            try {
                if (i > 0) Thread.sleep(STACK_MOMENTS_MS[i] - STACK_MOMENTS_MS[i - 1]);
            } catch (InterruptedException e) {
                return;
            }
            all.append("--- after ").append(since().trim()).append('\n');
            Thread main = Looper.getMainLooper().getThread();
            all.append("main thread (").append(main.getState()).append("):\n");
            appendFrames(all, main.getStackTrace(), 14);
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                Thread thread = entry.getKey();
                if (thread == main || thread.getName().startsWith("gameport-")) continue;
                all.append(thread.getName()).append(" (").append(thread.getState()).append("):\n");
                appendFrames(all, entry.getValue(), 6);
            }
            sink.putDiagnostic("stacks", all.toString());
        }
    }

    private static void appendFrames(StringBuilder out, StackTraceElement[] frames, int limit) {
        for (int i = 0; i < frames.length && i < limit; i++) out.append("    ").append(frames[i]).append('\n');
        if (frames.length == 0) out.append("    (no Java frame)\n");
    }
}
