package app.gameport.hook;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;

/**
 * Added to a patched game. Android creates a provider before the game's own code runs, whichever
 * way the game was started, so this is where the game's saves are synced with Steam Cloud: newer
 * cloud saves are brought down before the game reads anything, and changes are sent up when the
 * game is left, as Steam does when a game exits. Nothing is synced while the game is in front.
 */
public final class GamePortHookProvider extends ContentProvider {
    private static final String TAG = "GPHook";
    private static final long CONFLICT_WAIT_MS = 180_000;
    private static final long UPLOAD_AFTER_PAUSE_MS = 1_500;
    private static final long ALIVE_INTERVAL_MS = 30_000L;
    private static final long WARM_INTERVAL_MS = 25_000;

    private SaveSync sync;
    // Whether GamePort asked to be opened again when the game it started closes, and whether it started this game.
    // For which games to open GamePort again when the game closes: "never", "app" (started from GamePort), "library" (started from elsewhere) or "all".
    private volatile String returnMode = "app";
    private volatile boolean launchedByGamePort;
    private int liveActivities;
    private Handler background;
    private SessionLog sessionLog;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        long start = SystemClock.elapsedRealtime();
        try {
            applyXrSettings(context);
            File root = Environment.getExternalStorageDirectory();
            File files = context.getExternalFilesDir(null);
            File backups = new File(files != null ? files : context.getFilesDir(), "gameport-backup");
            sync = new SaveSync(root, backups, new ProviderLink(context), new SaveSync.Log() {
                @Override public void info(String message) { Log.i(TAG, message); }
            }, CONFLICT_WAIT_MS);

            // Before the game reads its saves.
            sync.syncAtLaunch();
            Log.i(TAG, "launch sync finished after " + (SystemClock.elapsedRealtime() - start) + " ms");
            prepareSteamTicket(context);
            watchTicketRequests(context);
            pullAchievementsFromSteam(context);
            watchAchievements(context);
            reportControllerProfile(context);

            watchForUploads(context);
            // What the game says in the system log, for a problem report.
            sessionLog = new SessionLog(context);
            sessionLog.start();
            // A game that quits by itself (System.exit) leaves no activity callback: this still tells GamePort it is gone.
            final Context closing = context.getApplicationContext();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override public void run() {
                    if (sessionLog != null) sessionLog.flushNow();
                    tellGamePort(closing, "closed");
                }
            }, "gameport-closed"));
        } catch (Throwable t) {
            Log.w(TAG, "save sync unavailable; the game runs without it", t);
        }
        return true;
    }

    /**
     * Asks GamePort for a Steam session ticket made with the signed-in account and leaves it in the
     * game's folder. The Steam shim returns it when the game asks for one, so the game's own servers
     * accept the login. Without it (GamePort offline) the shim answers with its placeholder ticket.
     */
    private void prepareSteamTicket(Context context) {
        File files = context.getExternalFilesDir(null);
        File dir = new File(files != null ? files : context.getFilesDir(), "gameport");
        File target = new File(dir, "steam_ticket.bin");
        target.delete();
        try {
            Bundle result = context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), "ticket", context.getPackageName(), null);
            byte[] ticket = result == null ? null : result.getByteArray("ticket");
            if (ticket == null || ticket.length == 0) {
                Log.i(TAG, "no Steam ticket (GamePort offline, or Steam gave none)");
                return;
            }
            dir.mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(target);
            out.write(ticket);
            out.close();
            Log.i(TAG, "Steam session ticket ready (" + ticket.length + " bytes)");
        } catch (Throwable t) {
            Log.w(TAG, "could not get a Steam ticket", t);
        }
    }

    /**
     * The OpenXR layer leaves the kind of controllers it translated (Steam Frame or Meta) and the controls the game used the last time it ran.
     * GamePort is told, so its controller page is offered for this game.
     */
    private void reportControllerProfile(Context context) {
        try {
            File files = context.getExternalFilesDir(null);
            File marker = new File(new File(files != null ? files : context.getFilesDir(), "gameport"), "xr_controls.txt");
            if (!marker.isFile()) return;
            java.util.List<String> controls = new java.util.ArrayList<>();
            String source = "";
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(marker));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("source=")) source = line.substring("source=".length());
                    else if (!line.isEmpty()) controls.add(line);
                }
            } finally {
                in.close();
            }
            if (controls.isEmpty()) return;
            Bundle extras = new Bundle();
            extras.putString("source", source);
            extras.putStringArray("controls", controls.toArray(new String[0]));
            context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), "controller_profile", context.getPackageName(), extras);
            Log.i(TAG, "reported " + controls.size() + " " + source + " controls");
        } catch (Throwable t) {
            Log.w(TAG, "could not report the controller profile", t);
        }
    }

    /**
     * A session ticket is good for one use and goes stale, so the shim asks for a fresh one each time
     * the game does: it drops a request file with an id, and this thread answers by leaving a new
     * ticket and a "ready" file carrying the same id.
     */
    private void watchTicketRequests(final Context context) {
        File files = context.getExternalFilesDir(null);
        final File dir = new File(files != null ? files : context.getFilesDir(), "gameport");
        final File request = new File(dir, "steam_ticket.request");
        final File ticketFile = new File(dir, "steam_ticket.bin");
        final File ready = new File(dir, "steam_ticket.ready");
        Thread watcher = new Thread(new Runnable() {
            @Override public void run() {
                long lastWarm = SystemClock.elapsedRealtime();
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Keep GamePort running and connected to Steam, so a ticket takes a moment when asked for.
                        if (SystemClock.elapsedRealtime() - lastWarm > WARM_INTERVAL_MS) {
                            lastWarm = SystemClock.elapsedRealtime();
                            context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), "warm", context.getPackageName(), null);
                        }
                        if (request.exists()) {
                            String id = readLine(request);
                            request.delete();
                            long begun = SystemClock.elapsedRealtime();
                            Bundle result = context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), "ticket", context.getPackageName(), null);
                            byte[] ticket = result == null ? null : result.getByteArray("ticket");
                            if (ticket != null && ticket.length > 0) {
                                writeAll(ticketFile, ticket);
                                Log.i(TAG, "fresh Steam session ticket for request " + id + " (" + ticket.length + " bytes, " + (SystemClock.elapsedRealtime() - begun) + " ms)");
                            } else {
                                ticketFile.delete();
                                Log.i(TAG, "no fresh Steam ticket for request " + id);
                            }
                            writeAll(ready, (id == null ? "" : id).getBytes("UTF-8"));
                        }
                        Thread.sleep(80);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable t) {
                        Log.w(TAG, "ticket request failed", t);
                        try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                    }
                }
            }
        }, "gameport-ticket");
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Before the game reads its record of unlocked achievements, GamePort adds to it what the Steam account has: achievements earned on
     * another device are then known here and the game does not unlock them again. Nothing is ever taken out of the record. If Steam
     * cannot be asked in time, or the account has nothing more, the file is left as it is.
     */
    private void pullAchievementsFromSteam(Context context) {
        try {
            File files = context.getExternalFilesDir(null);
            File dir = new File(files != null ? files : context.getFilesDir(), "gameport");
            String appId = readLine(new File(dir, "appid.txt"));
            if (appId == null || appId.trim().isEmpty()) appId = configValue(readAsset(context, "gameport/steam.cfg"), "appid");
            if (appId == null || appId.trim().isEmpty()) return;
            File record = new File(dir, "Goldberg SteamEmu Saves/" + appId.trim() + "/achievements.json");
            String current = record.isFile() ? readText(record) : "";
            // A call carries little: a record too big, or one that cannot be read, is not touched.
            if (current == null || current.length() > 400_000) return;
            Bundle extras = new Bundle();
            extras.putString("current", current);
            Bundle result = context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), "earned", context.getPackageName(), extras);
            String merged = result == null ? null : result.getString("merged");
            if (merged == null || merged.isEmpty()) return;
            File parent = record.getParentFile();
            if (parent != null) parent.mkdirs();
            File temp = new File(parent, "achievements.json.gameport");
            writeAll(temp, merged.getBytes("UTF-8"));
            if (!temp.renameTo(record)) {
                temp.delete();
                return;
            }
            Log.i(TAG, "the achievements of the Steam account were added to the game's record");
        } catch (Throwable t) {
            Log.w(TAG, "could not bring the achievements of the Steam account", t);
        }
    }

    /** The value of [key] in a `key=value` per line text, or null. */
    private static String configValue(String text, String key) {
        if (text == null) return null;
        for (String line : text.split("\\r?\\n")) {
            int at = line.indexOf('=');
            if (at > 0 && line.substring(0, at).trim().equals(key)) return line.substring(at + 1).trim();
        }
        return null;
    }

    /**
     * The Steamworks shim records the achievements a game unlocks in a file of its own, which is read every couple of seconds. GamePort is told
     * of the ones that are new, to announce them. What was unlocked before the game started does not count: that is the file as it was found,
     * and what GamePort baked into the game from the account's own unlocks.
     */
    private void watchAchievements(final Context context) {
        File files = context.getExternalFilesDir(null);
        final File dir = new File(files != null ? files : context.getFilesDir(), "gameport");
        final java.util.Set<String> known = new java.util.HashSet<>();
        known.addAll(earnedTimes(readAsset(context, "gameport/achievements_earned.json")).keySet());
        Thread watcher = new Thread(new Runnable() {
            @Override public void run() {
                long modified = -1;
                long length = -1;
                boolean existedAtStart = achievementsFile(dir) != null && achievementsFile(dir).isFile();
                boolean baselined = false;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        File file = achievementsFile(dir);
                        if (file != null && file.isFile() && (file.lastModified() != modified || file.length() != length)) {
                            modified = file.lastModified();
                            length = file.length();
                            java.util.Map<String, Long> earned = earnedTimes(readText(file));
                            if (!baselined && existedAtStart) {
                                known.addAll(earned.keySet());
                            } else {
                                java.util.List<String> fresh = new java.util.ArrayList<>();
                                for (String name : earned.keySet()) if (!known.contains(name)) fresh.add(name);
                                if (!fresh.isEmpty() && tellAchievements(context, fresh, earned)) known.addAll(fresh);
                            }
                            baselined = true;
                        }
                        Thread.sleep(2_000);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable t) {
                        Log.w(TAG, "could not read the achievements", t);
                        try { Thread.sleep(5_000); } catch (InterruptedException e) { return; }
                    }
                }
            }
        }, "gameport-achievements");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** The file of the unlocked achievements of the shim, once the game's app id is known; null before. */
    private static File achievementsFile(File gameportDir) {
        String appId = readLine(new File(gameportDir, "appid.txt"));
        if (appId == null || appId.trim().isEmpty()) return null;
        return new File(gameportDir, "Goldberg SteamEmu Saves/" + appId.trim() + "/achievements.json");
    }

    private static boolean tellAchievements(Context context, java.util.List<String> names, java.util.Map<String, Long> earned) {
        try {
            Bundle extras = new Bundle();
            extras.putStringArray("names", names.toArray(new String[0]));
            // When each was unlocked, in seconds, as the shim recorded it, so the notification shows the real time.
            long[] times = new long[names.size()];
            for (int i = 0; i < times.length; i++) {
                Long at = earned.get(names.get(i));
                times[i] = at == null ? 0L : at;
            }
            extras.putLongArray("times", times);
            Bundle result = context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), "achievement", context.getPackageName(), extras);
            Log.i(TAG, "told GamePort about " + names.size() + " unlocked achievement(s)");
            return result != null && "OK".equals(result.getString("status"));
        } catch (Throwable t) {
            Log.w(TAG, "could not tell GamePort about the achievements", t);
            return false;
        }
    }

    /** The achievements marked as earned in a JSON object of `{"name": {"earned": true, "earned_time": seconds}}`, with their times (0 when unknown). */
    private static java.util.Map<String, Long> earnedTimes(String json) {
        java.util.Map<String, Long> earned = new java.util.LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) return earned;
        try {
            org.json.JSONObject all = new org.json.JSONObject(json);
            java.util.Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                org.json.JSONObject entry = all.optJSONObject(name);
                if (entry != null && entry.optBoolean("earned", false)) earned.put(name, entry.optLong("earned_time", 0L));
            }
        } catch (Throwable t) {
            // A file being written, or not ours: nothing to read yet.
        }
        return earned;
    }

    private static String readAsset(Context context, String path) {
        try {
            java.io.InputStream in = context.getAssets().open(path);
            try { return readAll(in); } finally { in.close(); }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readText(File file) {
        try {
            java.io.InputStream in = new java.io.FileInputStream(file);
            try { return readAll(in); } finally { in.close(); }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
        return out.toString("UTF-8");
    }

    private static String readLine(File file) {
        try {
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(file));
            try { return in.readLine(); } finally { in.close(); }
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeAll(File file, byte[] bytes) throws java.io.IOException {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
        try { out.write(bytes); } finally { out.close(); }
        if (!tmp.renameTo(file)) {
            file.delete();
            tmp.renameTo(file);
        }
    }

    /**
     * Hands the player's seated-mode settings to GamePort's OpenXR layer, which reads them from the
     * environment when the game creates its OpenXR instance. The last answer is kept, so the setting
     * still applies when GamePort cannot be reached.
     */
    private void applyXrSettings(Context context) {
        File cache = new File(context.getFilesDir(), "gameport-xr.cfg");
        String seated = "0";
        String eyeCm = "0";
        String controls = "";
        String family = "";
        try {
            Bundle config = context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), "config", context.getPackageName(), null);
            if (config == null) throw new java.io.IOException("no answer");
            seated = config.getBoolean("seated") ? "1" : "0";
            eyeCm = String.valueOf(config.getInt("eyeCm"));
            String map = config.getString("xrMap");
            controls = map == null ? "" : map;
            String platform = config.getString("xrFamily");
            family = platform == null ? "" : platform;
            String mode = config.getString("returnMode");
            // A GamePort from before the choice only says whether to come back for a game it started.
            returnMode = mode != null ? mode : (config.getBoolean("returnToGamePort") ? "app" : "never");
            java.io.FileWriter out = new java.io.FileWriter(cache);
            out.write(seated + "\n" + eyeCm + "\n" + controls + "\n" + family + "\n");
            out.close();
        } catch (Throwable t) {
            try {
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(cache));
                seated = in.readLine();
                eyeCm = in.readLine();
                String map = in.readLine();
                controls = map == null ? "" : map;
                String platform = in.readLine();
                family = platform == null ? "" : platform;
                in.close();
            } catch (Throwable ignored) {
                // No earlier answer either: seated mode stays off.
            }
        }
        try {
            android.system.Os.setenv("GAMEPORT_XR_SEATED", seated, true);
            android.system.Os.setenv("GAMEPORT_XR_EYE_CM", eyeCm, true);
            // The player's controller mapping for this game (empty: the layer's defaults).
            android.system.Os.setenv("GAMEPORT_XR_MAP", controls, true);
            // The kind of controllers this device has, from GamePort's own platform detection.
            android.system.Os.setenv("GAMEPORT_XR_FAMILY", family, true);
            Log.i(TAG, "seated mode " + seated + ", eye height " + eyeCm + " cm, controller overrides " + (controls.isEmpty() ? "none" : controls));
        } catch (Throwable t) {
            Log.w(TAG, "could not pass the seated settings on", t);
        }
    }

    /**
     * Sends changes up when the game leaves the screen (home button, headset taken off) and when it
     * is destroyed. On a headset a game is rarely closed for good, so leaving it stands for the exit
     * Steam syncs on. Coming straight back cancels the upload.
     */
    private void watchForUploads(Context context) {
        HandlerThread thread = new HandlerThread("gameport-sync");
        thread.start();
        background = new Handler(thread.getLooper());
        final Runnable upload = new Runnable() {
            @Override public void run() { sync.uploadIfChanged(); }
        };
        final Context appContext = context.getApplicationContext();
        // The time the game is really on screen: GamePort is told when it comes, regularly while it stays, and when it leaves.
        // A device asleep or a game left behind sends nothing, so that time is never counted.
        final Runnable beat = new Runnable() {
            @Override public void run() {
                if (resumedActivities <= 0) return;
                tellGamePort(appContext, "alive");
                background.postDelayed(this, ALIVE_INTERVAL_MS);
            }
        };
        Application application = (Application) appContext;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) {
                background.removeCallbacks(upload);
                if (resumedActivities++ == 0) {
                    background.post(new Runnable() { @Override public void run() { tellGamePort(appContext, "resumed"); } });
                    background.removeCallbacks(beat);
                    background.postDelayed(beat, ALIVE_INTERVAL_MS);
                }
            }
            @Override public void onActivityPaused(Activity a) {
                background.postDelayed(upload, UPLOAD_AFTER_PAUSE_MS);
                if (resumedActivities > 0 && --resumedActivities == 0) {
                    background.removeCallbacks(beat);
                    background.post(new Runnable() { @Override public void run() { tellGamePort(appContext, "paused"); } });
                    if (sessionLog != null) sessionLog.flushSoon();
                }
            }
            @Override public void onActivityDestroyed(Activity a) {
                background.post(upload);
                if (sessionLog != null) sessionLog.flushSoon();
                // The game was closed (not just turned): bring GamePort back, which Horizon does not do by itself.
                boolean last = --liveActivities <= 0;
                boolean byGamePort = launchedByGamePort;
                boolean returning = last && a.isFinishing() && ("all".equals(returnMode) || ("app".equals(returnMode) && byGamePort) || ("library".equals(returnMode) && !byGamePort));
                Log.i(TAG, "activity destroyed: last=" + last + " finishing=" + a.isFinishing() + " returnMode=" + returnMode
                        + " byGamePort=" + byGamePort + " -> " + (returning ? "opening GamePort" : "staying away"));
                if (returning) openGamePort(a);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {
                if (liveActivities++ == 0 && sessionLog != null) sessionLog.setLaunchInfo(describe(a));
                if (a.getIntent() != null && a.getIntent().getBooleanExtra("app.gameport.launched", false)) launchedByGamePort = true;
            }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        });
    }

    private volatile int resumedActivities;

    /** How the game's first screen was started: its class, the intent's action, categories, flags and the names of its extras. */
    private static String describe(Activity a) {
        StringBuilder out = new StringBuilder("activity=").append(a.getClass().getName()).append('\n');
        android.content.Intent intent = a.getIntent();
        if (intent == null) return out.append("intent=none\n").toString();
        out.append("action=").append(intent.getAction()).append('\n');
        out.append("categories=").append(intent.getCategories()).append('\n');
        out.append("flags=0x").append(Integer.toHexString(intent.getFlags())).append('\n');
        out.append("launchedByGamePort=").append(intent.getBooleanExtra("app.gameport.launched", false)).append('\n');
        Bundle extras = intent.getExtras();
        StringBuilder names = new StringBuilder();
        if (extras != null) for (String key : extras.keySet()) names.append(key).append(' ');
        out.append("extras=").append(names.toString().trim()).append('\n');
        return out.toString();
    }

    private static void openGamePort(Context context) {
        try {
            context.startActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .setClassName("app.gameport", "app.gameport.MainActivity")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Log.w(TAG, "could not open GamePort again", t);
        }
    }

    private static void tellGamePort(Context context, String method) {
        try {
            context.getContentResolver().call(Uri.parse("content://app.gameport.cloud"), method, context.getPackageName(), null);
        } catch (Throwable t) {
            Log.w(TAG, "could not tell GamePort: " + method, t);
        }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
