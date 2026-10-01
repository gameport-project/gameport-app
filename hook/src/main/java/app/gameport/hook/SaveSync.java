package app.gameport.hook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Syncs a game's saves with Steam Cloud through GamePort. It never overwrites a save without
 * keeping the old one: files it replaces or deletes are first moved to a timestamped backup folder.
 */
final class SaveSync {
    interface Log {
        void info(String message);
    }

    private final File root;
    private final File backupRoot;
    private final GamePortLink link;
    private final Log log;
    private final long conflictWaitMs;
    private String lastSyncedSignature = "";

    SaveSync(File root, File backupRoot, GamePortLink link, Log log, long conflictWaitMs) {
        this.root = root;
        this.backupRoot = backupRoot;
        this.link = link;
        this.log = log;
        this.conflictWaitMs = conflictWaitMs;
    }

    /** Before the game reads its saves: bring the cloud's newer files down, or ask about a conflict. */
    synchronized void syncAtLaunch() {
        run(true);
    }

    /** While playing or on exit: send local changes up. Never downloads, so it cannot disturb a running game. */
    synchronized void uploadIfChanged() {
        run(false);
    }

    private void run(boolean launch) {
        currentBackup = null;
        try {
            GamePortLink.Begin begin = link.begin();
            if (!"READY".equals(begin.status)) {
                log.info("sync skipped: " + begin.status);
                return;
            }
            LocalScanner scanner = new LocalScanner(root);
            List<LocalFile> files = scanner.scan(begin.rules);
            String signature = signature(files);
            if (!launch && signature.equals(lastSyncedSignature)) return;

            GamePortLink.Plan plan = link.plan(files, null);
            log.info("plan: " + plan.action);
            if ("CONFLICT".equals(plan.action)) {
                if (!launch) return;
                plan = resolveConflict(scanner, begin.rules);
                if (plan == null) return;
            }
            if ("DOWNLOAD".equals(plan.action)) {
                if (!launch) return;
                applyDownload(plan, scanner, begin.rules);
            } else if ("UPLOAD".equals(plan.action)) {
                applyUpload(plan);
            }
            lastSyncedSignature = signature(scanner.scan(begin.rules));
        } catch (Exception e) {
            log.info("sync failed, leaving saves untouched: " + e);
        } finally {
            link.end();
        }
    }

    /** Asks the player and waits; returns the plan for the side they chose, or null if they did not answer. */
    private GamePortLink.Plan resolveConflict(LocalScanner scanner, List<SaveRule> rules) throws IOException {
        link.showConflict();
        long deadline = System.currentTimeMillis() + conflictWaitMs;
        while (System.currentTimeMillis() < deadline) {
            String choice = link.conflictChoice();
            if ("LOCAL".equals(choice)) return link.plan(scanner.scan(rules), "local");
            if ("LATER".equals(choice)) {
                log.info("the player will decide later; saves left as they are");
                return null;
            }
            if ("CLOUD".equals(choice)) {
                backUpLocal(scanner.scan(rules));
                return link.plan(scanner.scan(rules), "cloud");
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        log.info("no answer to the conflict; saves left as they are");
        return null;
    }

    private void applyDownload(GamePortLink.Plan plan, LocalScanner scanner, List<SaveRule> rules) throws IOException {
        for (GamePortLink.Download download : plan.downloads) {
            File target = new File(root, download.rel);
            File temp = new File(target.getPath() + ".gpdl");
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = link.fetch(download.cloudName); OutputStream out = new FileOutputStream(temp)) {
                copy(in, out);
            }
            if (!LocalScanner.sha1(temp).equals(download.sha1)) {
                temp.delete();
                throw new IOException("downloaded file does not match: " + download.rel);
            }
            if (target.exists()) moveToBackup(target, download.rel);
            if (!temp.renameTo(target)) throw new IOException("could not place " + download.rel);
            if (download.timestamp > 0) target.setLastModified(download.timestamp);
        }
        for (String rel : plan.deleteLocal) {
            File file = new File(root, rel);
            if (file.exists()) moveToBackup(file, rel);
        }
        link.acknowledge(scanner.scan(rules));
        log.info("downloaded " + plan.downloads.size() + " file(s), removed " + plan.deleteLocal.size());
    }

    private void applyUpload(GamePortLink.Plan plan) throws IOException {
        for (String rel : plan.uploads) {
            try (InputStream in = new java.io.FileInputStream(new File(root, rel)); OutputStream out = link.push(rel)) {
                copy(in, out);
            }
        }
        boolean committed = link.commit();
        log.info("uploaded " + plan.uploads.size() + " file(s): " + (committed ? "ok" : "FAILED"));
    }

    /** Copies every current save aside before the cloud version replaces them. */
    private void backUpLocal(List<LocalFile> files) throws IOException {
        for (LocalFile file : files) {
            File source = new File(root, file.rel);
            File copy = new File(backupDir(), shortPath(file.rel));
            if (copy.getParentFile() != null) copy.getParentFile().mkdirs();
            try (InputStream in = new java.io.FileInputStream(source); OutputStream out = new FileOutputStream(copy)) {
                copy(in, out);
            }
        }
    }

    private void moveToBackup(File file, String rel) throws IOException {
        File destination = new File(backupDir(), shortPath(rel));
        if (destination.getParentFile() != null) destination.getParentFile().mkdirs();
        if (!file.renameTo(destination)) {
            try (InputStream in = new java.io.FileInputStream(file); OutputStream out = new FileOutputStream(destination)) {
                copy(in, out);
            }
            file.delete();
        }
    }

    /** A game's own folder is the same in every backup, so it is left out of the path. */
    static String shortPath(String rel) {
        return rel.replaceFirst("^Android/data/[^/]+/files/", "");
    }

    private File currentBackup;

    private File backupDir() {
        if (currentBackup == null) {
            currentBackup = new File(backupRoot, new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()));
        }
        return currentBackup;
    }

    private static String signature(List<LocalFile> files) {
        StringBuilder builder = new StringBuilder();
        for (LocalFile file : files) builder.append(file.encode()).append('\n');
        return builder.toString();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
    }
}
