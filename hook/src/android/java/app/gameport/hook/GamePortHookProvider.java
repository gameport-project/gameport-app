package app.gameport.hook;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
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
    /** How long the question about another device that plays waits for the player: then the game starts without its ticket. */
    private static final long PLAY_CHOICE_WAIT_MS = 90_000;
    private static final long UPLOAD_AFTER_PAUSE_MS = 300;
    // How often the saves are looked at while the game runs (see [SaveSync#poll]), and how long the game's end waits for them to be sent.
    private static final long SAVE_POLL_MS = 4_000;
    private static final long EXIT_FLUSH_MS = 10_000;
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

    // Set when GamePort started this process only to send the game's saves (see [calledByGamePort] and the `catchup` call).
    private volatile boolean catchUpMode;
    private boolean startedUp;
    private final Object startLock = new Object();

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        // The Workshop page the game opens from its own button is handed to GamePort instead of the browser.
        WorkshopRedirect.install(context);
        // Where the APK is, for the Steamworks shim, which reads its config out of it. Asking the system is surer than finding the APK among
        // the memory mappings of the process, which the shim used to do alone.
        try {
            android.system.Os.setenv("GAMEPORT_APK", context.getApplicationInfo().sourceDir, true);
        } catch (Throwable t) {
            Log.w(TAG, "could not tell the shim where the APK is", t);
        }
        try {
            File root = Environment.getExternalStorageDirectory();
            File files = context.getExternalFilesDir(null);
            File backups = new File(files != null ? files : context.getFilesDir(), "gameport-backup");
            // The private data of the game is in the folder that holds the data of every app; only this process can read it.
            // Where the game was seen writing its saves is kept in a small file of its own folder, which the sync never reads as a save.
            SaveRoots roots = new SaveRoots(root, context.getDataDir().getParentFile(), context.getPackageName(), new File(context.getFilesDir(), "gameport/saves_place"));
            sync = new SaveSync(roots, backups, new ProviderLink(context), new SaveSync.Log() {
                @Override public void info(String message) { Log.i(TAG, message); }
            }, CONFLICT_WAIT_MS);
            if (startedToSendSaves(context)) {
                // GamePort wants the saves of a game it is not playing sent. Nothing else is set up: the account must not be said to play
                // the game, and no time is counted. If a screen of the game opens meanwhile, the usual start-up happens before it does.
                catchUpMode = true;
                Log.i(TAG, "started by GamePort to send the saves");
                startUpWhenAScreenOpens(context);
                return true;
            }
            startUp(context);
        } catch (Throwable t) {
            Log.w(TAG, "save sync unavailable; the game runs without it", t);
        }
        return true;
    }

    /** Everything that is done when a game starts: sync the saves before it reads them, then the Steam ticket, the achievements and the watchers. */
    private void startUp(Context context) {
        synchronized (startLock) {
            if (startedUp) return;
            startedUp = true;
        }
        long start = SystemClock.elapsedRealtime();
        try {
            applyXrSettings(context);
            // Before the game reads its saves.
            sync.syncAtLaunch();
            Log.i(TAG, "launch sync finished after " + (SystemClock.elapsedRealtime() - start) + " ms");
            // The player cancelled the launch: nothing else is set up, the game closes.
            if (prepareSteamTicket(context)) return;
            watchTicketRequests(context);
            watchRelayRequests(context, "workshop");
            watchRelayRequests(context, "dlc");
            watchBrowseRequests(context);
            pullAchievementsFromSteam(context);
            watchAchievements(context);
            reportControllerProfile(context);
            reportControllerProfileLater(context);

            watchForUploads(context);
            // What the game says in the system log, for a problem report.
            sessionLog = new SessionLog(context);
            sessionLog.start();
            // A game that quits by itself (System.exit) leaves no activity callback: this still tells GamePort it is gone.
            final Context closing = context.getApplicationContext();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override public void run() {
                    if (sessionLog != null) sessionLog.flushNow();
                    flushSavesBeforeExit();
                    tellGamePort(closing, "closed");
                }
            }, "gameport-closed"));
        } catch (Throwable t) {
            Log.w(TAG, "save sync unavailable; the game runs without it", t);
        }
    }

    /** True when GamePort asked for this process to be started to send the game's saves, and not for the game to be played. */
    private static boolean startedToSendSaves(Context context) {
        try {
            Bundle result = Calls.call(context, Owner.cloud(context), "catchup", context.getPackageName(), null);
            return result != null && result.getBoolean("catchup", false);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The usual start-up runs just before the game's first screen is created, so a game started while the saves were being sent is complete. */
    private void startUpWhenAScreenOpens(final Context context) {
        final Application application = (Application) context.getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPreCreated(Activity a, Bundle b) {
                application.unregisterActivityLifecycleCallbacks(this);
                Log.i(TAG, "a screen of the game is opening: the usual start-up");
                catchUpMode = false;
                startUp(context);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    /**
     * GamePort asks the game to send its saves: only the sending that exists (it never downloads, and leaves a conflict for the player
     * to decide when the game is next started). Nobody else may ask.
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("obb_pull".equals(method)) return pullExpansionFiles();
        if ("obb_report".equals(method)) return reportExpansionFiles();
        if ("clear_saves".equals(method)) return putSavesAside(extras);
        if (!"catchup".equals(method)) return super.call(method, arg, extras);
        Context context = getContext();
        if (context == null || sync == null || !calledByGamePort(context)) return null;
        Bundle out = new Bundle();
        try {
            sync.uploadIfChanged();
            out.putString("status", "OK");
        } catch (Throwable t) {
            Log.w(TAG, "could not send the saves", t);
            out.putString("status", "ERROR");
        }
        if (catchUpMode) {
            tellGamePort(context, "catchup_done");
            exitIfNobodyPlays();
        }
        return out;
    }

    /**
     * GamePort asks for the saves on this device to be put aside (a test, a clean start): the game's own folders can only be reached from here. The rules come
     * with the call, so nothing is asked of Steam, and nothing is removed from it. A process started only for this goes away again.
     */
    private Bundle putSavesAside(Bundle extras) {
        Context context = getContext();
        if (context == null || sync == null || !calledByGamePort(context)) return null;
        Bundle out = new Bundle();
        try {
            java.util.List<SaveRule> rules = new java.util.ArrayList<>();
            String[] lines = extras == null ? null : extras.getStringArray("rules");
            if (lines != null) for (String line : lines) rules.add(SaveRule.parse(line));
            out.putInt("moved", sync.putLocalSavesAside(rules));
            out.putString("status", "OK");
        } catch (Throwable t) {
            Log.w(TAG, "could not put the saves aside", t);
            out.putString("status", "ERROR");
        }
        exitIfNobodyPlays();
        return out;
    }

    /**
     * GamePort asks which expansion files the game holds, by size: the game can list its own folder, which no other app can read. Only sizes leave the game, never a
     * name or a content. Nobody else may ask. A process started only for this goes away again, as it does after sending saves.
     */
    private Bundle reportExpansionFiles() {
        Context context = getContext();
        if (context == null || !calledByGamePort(context)) return null;
        Bundle out = new Bundle();
        java.util.ArrayList<Long> sizes = new java.util.ArrayList<>();
        boolean listed = false;
        try {
            java.io.File folder = context.getObbDir();
            java.io.File[] files = folder == null ? null : folder.listFiles();
            if (files != null) {
                listed = true;
                for (java.io.File file : files) {
                    // A file still being copied is not a file the game can load.
                    if (file.isFile() && !file.getName().endsWith(".part")) sizes.add(file.length());
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not list the expansion files", t);
        }
        long[] list = new long[sizes.size()];
        for (int i = 0; i < list.length; i++) list[i] = sizes.get(i);
        out.putString("status", listed ? "OK" : "UNREADABLE");
        out.putLongArray("sizes", list);
        if (catchUpMode) exitIfNobodyPlays();
        return out;
    }

    private final java.util.concurrent.atomic.AtomicBoolean pullingExpansionFiles = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * GamePort asks the game to place its expansion files in its own folder (it cannot write that folder itself on some devices). Answers at once and does the copying
     * in the background, telling GamePort how far it is and when it is done. Nobody else may ask.
     */
    private Bundle pullExpansionFiles() {
        final Context context = getContext();
        if (context == null || !calledByGamePort(context)) return null;
        Bundle out = new Bundle();
        out.putString("status", "STARTED");
        if (!pullingExpansionFiles.compareAndSet(false, true)) return out;
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                ObbPuller.Result result;
                try {
                    result = ObbPuller.run(context, new ObbPuller.Log() {
                        @Override public void info(String message) { Log.i(TAG, message); }
                    });
                } finally {
                    pullingExpansionFiles.set(false);
                }
                try {
                    Bundle done = new Bundle();
                    done.putBoolean("ok", result.ok);
                    if (result.reason != null) done.putString("reason", result.reason);
                    Calls.call(context, Owner.cloud(context), "obb_done", context.getPackageName(), done);
                } catch (Throwable t) {
                    Log.w(TAG, "could not tell GamePort the files are placed", t);
                }
                // Started for this and nothing else: the process goes away, unless a screen of the game has opened since.
                if (catchUpMode) exitIfNobodyPlays();
            }
        }, "gameport-obb");
        worker.setDaemon(true);
        worker.start();
        return out;
    }

    private static boolean calledByGamePort(Context context) {
        String[] packages = context.getPackageManager().getPackagesForUid(android.os.Binder.getCallingUid());
        if (packages != null) for (String name : packages) if (Owner.packageName(context).equals(name)) return true;
        return false;
    }

    /** The saves are sent: the process was only started for that, so it goes away, unless a screen of the game has opened since. */
    private void exitIfNobodyPlays() {
        new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                synchronized (startLock) {
                    if (startedUp) return;
                }
                Log.i(TAG, "saves sent, nobody plays: leaving");
                // A normal exit, so it is not mistaken for a crash.
                System.exit(0);
            }
        }, 3_000);
    }

    /**
     * Asks GamePort for a Steam session ticket made with the signed-in account and leaves it in the
     * game's folder. The Steam shim returns it when the game asks for one, so the game's own servers
     * accept the login. Without it (GamePort offline) the shim answers with its placeholder ticket.
     */
    /**
     * The game is not to be played: its first screen is closed as soon as it exists, so the system sees a game that was closed (and does not start
     * it again, as it would a process that died on its own), and the process leaves with it.
     */
    private void quitTheGame(Context context) {
        final Application application = (Application) context.getApplicationContext();
        final Handler main = new Handler(android.os.Looper.getMainLooper());
        final Runnable leave = new Runnable() {
            @Override public void run() {
                System.exit(0);
            }
        };
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { a.finishAndRemoveTask(); }
            @Override public void onActivityDestroyed(Activity a) { main.postDelayed(leave, 300); }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        });
        // No screen ever opens: the process leaves anyway.
        main.postDelayed(leave, 15_000);
    }

    /** Puts the question in front of the game and waits for the answer: QUIT, KICK or PLAY (also when there is none in time). */
    private String askAboutOtherDevice(Context context) {
        try {
            new ProviderLink(context).showConflict();
            long deadline = SystemClock.elapsedRealtime() + PLAY_CHOICE_WAIT_MS;
            while (SystemClock.elapsedRealtime() < deadline) {
                Bundle answer = Calls.call(context, Owner.cloud(context), "playchoice", context.getPackageName(), null);
                String choice = answer == null ? "PENDING" : answer.getString("choice", "PENDING");
                if ("NONE".equals(choice)) {
                    // GamePort was stopped and started again since the question was put: it no longer knows it.
                    Calls.call(context, Owner.cloud(context), "ticket", context.getPackageName(), null);
                    new ProviderLink(context).showConflict();
                } else if (!"PENDING".equals(choice)) {
                    return choice;
                }
                Thread.sleep(300);
            }
            Calls.call(context, Owner.cloud(context), "playgiveup", context.getPackageName(), null);
        } catch (Throwable t) {
            Log.w(TAG, "the question about the other device could not be put", t);
        }
        return "PLAY";
    }

    private boolean prepareSteamTicket(Context context) {
        File files = context.getExternalFilesDir(null);
        File dir = new File(files != null ? files : context.getFilesDir(), "gameport");
        File target = new File(dir, "steam_ticket.bin");
        target.delete();
        try {
            Bundle result = Calls.call(context, Owner.cloud(context), "ticket", context.getPackageName(), null);
            if (result != null && "BLOCKED".equals(result.getString("status"))) {
                // Steam says another device plays with the account: the player decides, in front of the game, before it goes on.
                String choice = askAboutOtherDevice(context);
                if ("QUIT".equals(choice)) {
                    Log.i(TAG, "the player cancelled the launch: another device plays with the account");
                    quitTheGame(context);
                    return true;
                } else if ("KICK".equals(choice)) {
                    Bundle takeOver = new Bundle();
                    takeOver.putBoolean("takeOver", true);
                    result = Calls.call(context, Owner.cloud(context), "ticket", context.getPackageName(), takeOver);
                } else {
                    Log.i(TAG, "the player chose to play without the Steam ticket");
                    result = null;
                }
            }
            byte[] ticket = result == null ? null : result.getByteArray("ticket");
            if (ticket == null || ticket.length == 0) {
                Log.i(TAG, "no Steam ticket (GamePort offline, or Steam gave none)");
                return false;
            }
            dir.mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(target);
            out.write(ticket);
            out.close();
            Log.i(TAG, "Steam session ticket ready (" + ticket.length + " bytes)");
        } catch (Throwable t) {
            Log.w(TAG, "could not get a Steam ticket", t);
        }
        return false;
    }

    /**
     * The OpenXR layer leaves the kind of controllers it translated (Steam Frame or Meta) and the controls the game used the last time it ran.
     * GamePort is told, so its controller page is offered for this game.
     */
    /**
     * The layer writes that file when the game attaches its controls, a few seconds after the start: it is read again then, and once more later, so the
     * controller page knows this run's controls during the run and not only at the next start.
     */
    private void reportControllerProfileLater(final Context context) {
        final Context app = context.getApplicationContext();
        Thread later = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Thread.sleep(12_000);
                    reportControllerProfile(app);
                    Thread.sleep(40_000);
                    reportControllerProfile(app);
                    // The layer decides half a minute after the game starts asking for its actions that the game cannot hear the controllers.
                    Thread.sleep(25_000);
                    reportControllerProfile(app);
                    Thread.sleep(30_000);
                    reportControllerProfile(app);
                } catch (InterruptedException ignored) {
                    // The game is closing.
                }
            }
        }, "gameport-controls");
        later.setDaemon(true);
        later.start();
    }

    private void reportControllerProfile(Context context) {
        try {
            File files = context.getExternalFilesDir(null);
            File marker = new File(new File(files != null ? files : context.getFilesDir(), "gameport"), "xr_controls.txt");
            if (!marker.isFile()) return;
            java.util.List<String> controls = new java.util.ArrayList<>();
            String source = "";
            boolean both = false;
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(marker));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("source=")) source = line.substring("source=".length());
                    else if (line.equals("both=1")) both = true;
                    // Other "name=value" lines are for the log and for people; a control is "hand:group".
                    else if (!line.isEmpty() && line.indexOf('=') < 0) controls.add(line);
                }
            } finally {
                in.close();
            }
            // A file with no control says there is nothing to remap (any more): the app is told too, so an old offer goes.
            Bundle extras = new Bundle();
            extras.putString("source", source);
            extras.putStringArray("controls", controls.toArray(new String[0]));
            extras.putBoolean("both", both);
            // The layer leaves this mark when, after a minute, not one action of the game answered active.
            extras.putBoolean("silent", new File(marker.getParentFile(), "xr_silent.txt").isFile());
            // The layer leaves this mark when the headset has no play area and it gave the game a space that follows the recentering.
            extras.putBoolean("stageFallback", new File(marker.getParentFile(), "xr_stage_fallback.txt").isFile());
            Calls.call(context, Owner.cloud(context), "controller_profile", context.getPackageName(), extras);
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
                            Calls.call(context, Owner.cloud(context), "warm", context.getPackageName(), null);
                        }
                        if (request.exists()) {
                            // The first line is the id of the request, the second (when there is one) the service a web ticket is for.
                            String[] lines = readLines(request, 2);
                            String id = lines[0];
                            String identity = lines[1];
                            request.delete();
                            long begun = SystemClock.elapsedRealtime();
                            // A ticket asked for while the game runs never opens the question: it was put when the game started.
                            Bundle quiet = new Bundle();
                            quiet.putBoolean("quiet", true);
                            if (identity != null && !identity.isEmpty()) quiet.putString("identity", identity);
                            Bundle result = Calls.call(context, Owner.cloud(context), "ticket", context.getPackageName(), quiet);
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
     * A Workshop item that GamePort has downloaded waits in the game's expansion folder, the one place GamePort and the game both reach. Before the game is told where it
     * is, it is copied into the game's own data folder (made by the game itself, so nothing about it is special), and that is the folder the game is given. The copy is made
     * once, in a part folder that is renamed when whole. If anything fails, the game is given the original folder.
     */
    private static final java.util.Set<String> toldWorkshop = new java.util.HashSet<>();

    /** Said once for each different thing: this runs every second, and a line each time would fill the log. */
    private static void toldOnce(String line) {
        synchronized (toldWorkshop) {
            if (toldWorkshop.add(line)) Log.i(TAG, line);
        }
    }

    private static String inGameFolder(Context context, String item) {
        try {
            String[] fields = item.split("\t", -1);
            if (fields.length < 5 || !"INSTALLED".equals(fields[1])) {
                if (fields.length >= 2) toldOnce("workshop item " + fields[0] + " is " + fields[1] + ": nothing to copy yet");
                return item;
            }
            File files = context.getExternalFilesDir(null);
            if (files == null) { toldOnce("workshop item " + fields[0] + " not copied: the game has no external files folder"); return item; }
            long id = Long.parseLong(fields[0]);
            long expected = Long.parseLong(fields[3]);
            File root = new File(files, "gameport/workshop");
            File target = new File(root, fields[0]);
            if (folderSize(target) != expected || expected == 0) {
                if (!pullWorkshopItem(context, id, root, target, expected)) return item;
            }
            fields[4] = target.getPath();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < fields.length; i++) out.append(i == 0 ? "" : "\t").append(fields[i]);
            toldOnce("workshop item " + fields[0] + " is given from " + target.getPath());
            return out.toString();
        } catch (Throwable t) {
            toldOnce("workshop item copy failed: " + t);
            Log.w(TAG, "could not copy a workshop item into the game's folder", t);
            return item;
        }
    }

    /**
     * Copies a Workshop item from GamePort, which serves its files, into the game's own folder: a folder GamePort makes cannot be entered by the game, and the one the game
     * makes can. Made in a part folder, renamed when the sizes add up.
     */
    private static boolean pullWorkshopItem(Context context, long id, File root, File target, long expected) throws java.io.IOException {
        android.net.Uri cloud = Owner.cloud(context);
        String pkg = context.getPackageName();
        Bundle extras = new Bundle();
        extras.putLong("id", id);
        Bundle list = Calls.call(context, cloud, "workshop_files", pkg, extras);
        String[] names = list == null ? null : list.getStringArray("names");
        long[] sizes = list == null ? null : list.getLongArray("sizes");
        if (names == null || sizes == null || names.length != sizes.length || names.length == 0) { toldOnce("workshop item " + id + " not copied: GamePort gave no list of files"); return false; }
        File part = new File(root, id + ".part");
        deleteTree(part);
        deleteTree(target);
        long total = 0;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name.startsWith("/") || name.contains("..")) { toldOnce("workshop item " + id + " not copied: a file has a name that is not allowed"); deleteTree(part); return false; }
            File out = new File(part, name);
            File parent = out.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) { toldOnce("workshop item " + id + " not copied: cannot make " + parent.getPath()); deleteTree(part); return false; }
            android.net.Uri uri = cloud.buildUpon().appendPath("workshop").appendQueryParameter("pkg", pkg).appendQueryParameter("id", String.valueOf(id)).appendQueryParameter("name", name).build();
            android.os.ParcelFileDescriptor descriptor = Calls.openFile(context, uri, "r");
            if (descriptor == null) { toldOnce("workshop item " + id + " not copied: GamePort would not give " + name); deleteTree(part); return false; }
            java.io.InputStream in = new java.io.FileInputStream(descriptor.getFileDescriptor());
            try {
                java.io.FileOutputStream stream = new java.io.FileOutputStream(out);
                try {
                    byte[] buffer = new byte[1 << 20];
                    int read;
                    while ((read = in.read(buffer)) > 0) { stream.write(buffer, 0, read); total += read; }
                } finally {
                    stream.close();
                }
            } finally {
                try { in.close(); } catch (java.io.IOException ignored) { }
                descriptor.close();
            }
        }
        if (total != expected || !part.renameTo(target)) {
            toldOnce("workshop item " + id + " not copied: " + total + " bytes came, " + expected + " expected");
            deleteTree(part);
            return false;
        }
        Log.i(TAG, "workshop item " + id + " copied into the game's own folder (" + total / 1024 + " KB, " + names.length + " file(s))");
        return true;
    }

    /** "id:size" for each item whose copy in the game's folder is whole (a part folder is not). */
    private static String[] heldWorkshopCopies(Context context) {
        java.util.ArrayList<String> held = new java.util.ArrayList<>();
        File files = context.getExternalFilesDir(null);
        File[] folders = files == null ? null : new File(files, "gameport/workshop").listFiles();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory() && folder.getName().matches("[0-9]+")) held.add(folder.getName() + ":" + folderSize(folder));
            }
        }
        return held.toArray(new String[0]);
    }

    /** The copies of items GamePort no longer lists (the account left them) go from the game's folder, with any part folder a stopped copy left. */
    private static void pruneWorkshopCopies(Context context, java.util.Set<String> keep) {
        File files = context.getExternalFilesDir(null);
        File[] folders = files == null ? null : new File(files, "gameport/workshop").listFiles();
        if (folders == null) return;
        for (File folder : folders) {
            String name = folder.getName();
            boolean part = name.endsWith(".part");
            String id = part ? name.substring(0, name.length() - 5) : name;
            if (!id.matches("[0-9]+") || (!part && keep.contains(id))) continue;
            long size = folderSize(folder);
            deleteTree(folder);
            Log.i(TAG, "workshop item " + id + (part ? " (a copy that was stopped)" : " (the account left it)") + " taken away from the game's folder (" + size / 1024 + " KB freed)");
        }
    }

    private static long folderSize(File folder) {
        long total = 0;
        File[] children = folder.listFiles();
        if (children == null) return 0;
        for (File child : children) total += child.isDirectory() ? folderSize(child) : child.length();
        return total;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void copyTree(File source, File target) throws java.io.IOException {
        if (source.isDirectory()) {
            if (!target.mkdirs() && !target.isDirectory()) throw new java.io.IOException("cannot make " + target);
            File[] children = source.listFiles();
            if (children != null) for (File child : children) copyTree(child, new File(target, child.getName()));
            return;
        }
        java.io.FileInputStream in = new java.io.FileInputStream(source);
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(target);
            try {
                byte[] buffer = new byte[1 << 20];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static void writeInstalledDlc(Context context, String ids, String withContent, String ownedApps) {
        if (ids == null) return;
        try {
            File files = context.getExternalFilesDir(null);
            File folder = new File(files != null ? files : context.getFilesDir(), "gameport/steam_settings");
            if (!folder.isDirectory() && !folder.mkdirs()) return;
            StringBuilder text = new StringBuilder("# c: has files of its own, i: is on the device\n");
            if (withContent != null) for (String id : withContent.split(",")) if (!id.isEmpty()) text.append("c:").append(id).append('\n');
            for (String id : ids.split(",")) if (!id.isEmpty()) text.append("i:").append(id).append('\n');
            writeAll(new File(folder, "dlc_installed.txt"), text.toString().getBytes("UTF-8"));
            // Every app the account can use: a game that asks about any extra content is told whether the account has it. Not written while Steam has not answered.
            if (ownedApps != null && !ownedApps.isEmpty()) writeAll(new File(folder, "owned_apps.txt"), ownedApps.replace(',', '\n').concat("\n").getBytes("UTF-8"));
        } catch (Throwable t) {
            Log.w(TAG, "could not write the list of installed extra contents", t);
        }
    }

    /**
     * A page of Steam the game opens in its overlay (a Workshop page, where a player subscribes to an item) is opened in the headset's browser: the shim leaves the address
     * in {@code browse.request}. Only pages of Steam are opened.
     */
    private void watchBrowseRequests(final Context context) {
        File files = context.getExternalFilesDir(null);
        final File request = new File(new File(files != null ? files : context.getFilesDir(), "gameport"), "browse.request");
        Thread watcher = new Thread(new Runnable() {
            @Override public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (request.isFile()) {
                            String url = readText(request);
                            request.delete();
                            if (url != null) url = url.trim();
                            if (url != null && (url.startsWith("https://steamcommunity.com/") || url.startsWith("https://store.steampowered.com/"))) {
                                android.content.Intent view = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
                                view.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                // The Workshop page of a game opens in GamePort, where the account is already signed in; any other page, or a GamePort
                                // that does not answer, leaves it to the browser.
                                boolean inGamePort = false;
                                if (WorkshopRedirect.workshopApp(android.net.Uri.parse(url)) != 0) {
                                    try {
                                        android.content.Intent own = new android.content.Intent(view).setPackage(Owner.packageName(context));
                                        context.startActivity(own);
                                        inGamePort = true;
                                    } catch (Throwable t) {
                                        Log.w(TAG, "GamePort did not take the Workshop page", t);
                                    }
                                }
                                if (!inGamePort) context.startActivity(view);
                                Log.i(TAG, inGamePort ? "opened a Workshop page in GamePort" : "opened a Steam page in the browser");
                            }
                        }
                        Thread.sleep(700);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable t) {
                        Log.w(TAG, "could not open the page", t);
                        try { Thread.sleep(2_000); } catch (InterruptedException e) { return; }
                    }
                }
            }
        }, "gameport-browse");
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * What the game asks of Steam that GamePort does (the Workshop items it fetches, the extra contents it installs; see the shim): the shim lists in {@code <kind>.request} the ids the game asked for, one per line, and this thread
     * asks GamePort about them every moment, until every one is installed or has failed, and leaves what it answers in {@code <kind>.state} (one line per item, fields
     * separated by tabs). The shim reads that file to tell the game how far each item is.
     */
    private void watchRelayRequests(final Context context, final String kind) {
        File files = context.getExternalFilesDir(null);
        final File dir = new File(files != null ? files : context.getFilesDir(), "gameport");
        final File request = new File(dir, kind + ".request");
        final File state = new File(dir, kind + ".state");
        Thread watcher = new Thread(new Runnable() {
            @Override public void run() {
                long seen = 0;
                boolean settled = false;
                int lastCount = -1;
                boolean lastDone = false;
                // The Workshop items the account is subscribed to are asked about from time to time, even when the game asked for none.
                long lastPoll = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        long stamp = request.lastModified();
                        if (stamp != seen) { seen = stamp; settled = false; }
                        boolean periodic = "workshop".equals(kind) && SystemClock.elapsedRealtime() - lastPoll > 20_000;
                        if ((request.isFile() && !settled) || periodic) {
                            lastPoll = SystemClock.elapsedRealtime();
                            java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                            String requested = request.isFile() ? readText(request) : "";
                            for (String line : (requested == null ? "" : requested).split("\n")) {
                                try { if (!line.trim().isEmpty()) ids.add(Long.parseLong(line.trim())); } catch (NumberFormatException ignored) { }
                            }
                            if (!ids.isEmpty() || periodic) {
                                long[] array = new long[ids.size()];
                                for (int i = 0; i < array.length; i++) array[i] = ids.get(i);
                                Bundle extras = new Bundle();
                                extras.putLongArray("ids", array);
                                // What the game holds whole already, so that GamePort can drop its own copy of those.
                                if ("workshop".equals(kind)) extras.putStringArray("have", heldWorkshopCopies(context));
                                Bundle result = Calls.call(context, Owner.cloud(context), kind, context.getPackageName(), extras);
                                String[] items = result == null ? null : result.getStringArray("items");
                                if (items != null) {
                                    StringBuilder text = new StringBuilder();
                                    boolean allDone = true;
                                    java.util.HashSet<String> answered = new java.util.HashSet<>();
                                    for (String item : items) {
                                        if ("workshop".equals(kind)) {
                                            answered.add(item.split("\t", 2)[0]);
                                            item = inGameFolder(context, item);
                                        }
                                        text.append(item).append('\n');
                                        String[] fields = item.split("\t", -1);
                                        if (fields.length > 1 && !"INSTALLED".equals(fields[1]) && !"FAILED".equals(fields[1])) allDone = false;
                                    }
                                    writeAll(state, text.toString().getBytes("UTF-8"));
                                    // An item the account left is taken away from the game's folder too, but only when GamePort read the subscriptions.
                                    if ("workshop".equals(kind) && result.getBoolean("known", false)) pruneWorkshopCopies(context, answered);
                                    settled = allDone;
                                    if (items.length != lastCount || allDone != lastDone) Log.i(TAG, kind + ": " + items.length + " item(s) answered" + (allDone ? ", all settled" : ""));
                                    lastCount = items.length;
                                    lastDone = allDone;
                                }
                            }
                        }
                        Thread.sleep(settled ? 500 : 700);
                    } catch (InterruptedException e) {
                        return;
                    } catch (Throwable t) {
                        Log.w(TAG, kind + " request failed", t);
                        try { Thread.sleep(2_000); } catch (InterruptedException e) { return; }
                    }
                }
            }
        }, "gameport-" + kind);
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
            Bundle result = Calls.call(context, Owner.cloud(context), "earned", context.getPackageName(), extras);
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
            Bundle result = Calls.call(context, Owner.cloud(context), "achievement", context.getPackageName(), extras);
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

    /** Whether GamePort's patch left this mark in the game's manifest (see the patches that write one). */
    private static boolean hasPatchMark(Context context, String key) {
        try {
            android.content.pm.ApplicationInfo info = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData != null && info.metaData.containsKey(key);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String readLine(File file) {
        try {
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(file));
            try { return in.readLine(); } finally { in.close(); }
        } catch (Throwable t) {
            return null;
        }
    }

    /** The first [count] lines of the file, null for the ones it does not have. */
    private static String[] readLines(File file, int count) {
        String[] lines = new String[count];
        try {
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(file));
            try { for (int i = 0; i < count; i++) lines[i] = in.readLine(); } finally { in.close(); }
        } catch (Throwable t) {
            // what could not be read stays null
        }
        return lines;
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
        String preferFrame = "0";
        String localFloor = "auto";
        String eyeFov = "auto";
        try {
            Bundle config = Calls.call(context, Owner.cloud(context), "config", context.getPackageName(), null);
            if (config == null) throw new java.io.IOException("no answer");
            seated = config.getBoolean("seated") ? "1" : "0";
            eyeCm = String.valueOf(config.getInt("eyeCm"));
            String map = config.getString("xrMap");
            controls = map == null ? "" : map;
            // The extra contents on the device now, for the Steam interface to answer "installed" for them only (its file is read when the game asks).
            writeInstalledDlc(context, config.getString("dlcInstalled"), config.getString("dlcWithContent"), config.getString("ownedApps"));
            String platform = config.getString("xrFamily");
            family = platform == null ? "" : platform;
            preferFrame = config.getBoolean("xrPreferFrame") ? "1" : "0";
            String recenter = config.getString("xrRecenter");
            localFloor = recenter == null || recenter.isEmpty() ? "auto" : recenter;
            String eyeFoveation = config.getString("xrEyeFoveation");
            eyeFov = eyeFoveation == null || eyeFoveation.isEmpty() ? "auto" : eyeFoveation;
            String mode = config.getString("returnMode");
            // A GamePort from before the choice only says whether to come back for a game it started.
            returnMode = mode != null ? mode : (config.getBoolean("returnToGamePort") ? "app" : "never");
            java.io.FileWriter out = new java.io.FileWriter(cache);
            out.write(seated + "\n" + eyeCm + "\n" + controls + "\n" + family + "\n" + preferFrame + "\n" + localFloor + "\n" + eyeFov + "\n");
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
                String again = in.readLine();
                preferFrame = again == null ? "0" : again;
                String floor = in.readLine();
                localFloor = floor == null || floor.equals("0") ? "auto" : (floor.equals("1") ? "on" : floor);
                String fov = in.readLine();
                eyeFov = fov == null || fov.isEmpty() ? "auto" : fov;
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
            // The Steam Frame's controls, for a game that also has its own for the device's controllers (off by default).
            android.system.Os.setenv("GAMEPORT_XR_PREFER_FRAME", preferFrame, true);
            // The play space that the headset's recentering moves, in place of the fixed one a game asks for (auto, on or off).
            android.system.Os.setenv("GAMEPORT_XR_LOCAL_FLOOR", localFloor, true);
            // The foveation that follows the eyes: kept where the device can do it (auto), always (on) or never (off).
            android.system.Os.setenv("GAMEPORT_XR_EYE_FOVEATION", eyeFov, true);
            // A game whose passthrough GamePort declared (the player chose it): the layer offers the alpha blend mode that shows the real world when the headset does not.
            android.system.Os.setenv("GAMEPORT_XR_ALPHA_BLEND", hasPatchMark(context, "app.gameport.passthrough_feature") ? "1" : "0", true);
            Log.i(TAG, "seated mode " + seated + ", eye height " + eyeCm + " cm, controller overrides " + (controls.isEmpty() ? "none" : controls));
            Log.i(TAG, "patches: vr_game_activity=" + hasPatchMark(context, "app.gameport.vr_game_activity") + " ovrplugin_stand_in=" + hasPatchMark(context, "app.gameport.ovrplugin_stand_in") + " passthrough_feature=" + hasPatchMark(context, "app.gameport.passthrough_feature"));
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
            @Override public void run() { sync.flush(); }
        };
        // The saves are looked at all the time the game runs and sent once they stop changing: waiting for the game to end is not enough, a
        // game that quits by itself leaves no time for it.
        final Runnable poll = new Runnable() {
            @Override public void run() {
                try {
                    sync.poll(SystemClock.elapsedRealtime());
                } catch (Throwable t) {
                    Log.w(TAG, "could not look at the saves", t);
                }
                background.postDelayed(this, SAVE_POLL_MS);
            }
        };
        background.postDelayed(poll, SAVE_POLL_MS);
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
                // A splash activity closes itself right after it started the activity that runs the game, which is not created yet: that is a hand-over, not the end.
                boolean handOver = a.getClass().getSimpleName().endsWith("SplashActivity");
                boolean returning = last && a.isFinishing() && !handOver && ("all".equals(returnMode) || ("app".equals(returnMode) && byGamePort) || ("library".equals(returnMode) && !byGamePort));
                Log.i(TAG, "activity destroyed: last=" + last + " finishing=" + a.isFinishing() + " returnMode=" + returnMode
                        + " byGamePort=" + byGamePort + " handOver=" + handOver + " -> " + (returning ? "opening GamePort" : "staying away"));
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

    /**
     * The game is ending (it quit by itself, or was closed): one last look at its saves, which are sent if they were not, for a few seconds
     * at most. What cannot be sent now is left for GamePort to send, which is told the game is closed.
     */
    private void flushSavesBeforeExit() {
        if (sync == null || catchUpMode) return;
        Thread flush = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    boolean sent = sync.flush();
                    Log.i(TAG, "saves at exit: " + (sent ? "all sent" : "some left to send"));
                } catch (Throwable t) {
                    Log.w(TAG, "could not send the saves at exit", t);
                }
            }
        }, "gameport-exit-sync");
        flush.setDaemon(true);
        flush.start();
        try {
            flush.join(EXIT_FLUSH_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

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
                    .setClassName(Owner.packageName(context), "app.gameport.MainActivity")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Log.w(TAG, "could not open GamePort again", t);
        }
    }

    /**
     * The game's witness of life: an object that lives and dies with this process. GamePort keeps it and is told at once, by Android, when the
     * process is gone, however it ended (the system kills a game left from the menu without a word). It goes with the signs that say the game
     * is being played.
     */
    private static final android.os.Binder LIFE = new android.os.Binder();

    private static void tellGamePort(Context context, String method) {
        try {
            Bundle extras = null;
            if ("alive".equals(method) || "resumed".equals(method) || "paused".equals(method)) {
                extras = new Bundle();
                extras.putBinder("life", LIFE);
            }
            Calls.call(context, Owner.cloud(context), method, context.getPackageName(), extras);
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
