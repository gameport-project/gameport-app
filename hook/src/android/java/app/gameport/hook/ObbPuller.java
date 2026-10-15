package app.gameport.hook;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Places the game's expansion files in the game's own folder. On some devices GamePort cannot write the expansion folder of another app, but the game can write its
 * own: GamePort keeps the files and serves them to the game, which copies them here. Runs in the game's process, started by GamePort and for nothing else.
 */
final class ObbPuller {
    interface Log {
        void info(String message);
    }

    /** What came of it: [ok], or [reason] in a few words. */
    static final class Result {
        final boolean ok;
        final String reason;

        Result(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }
    }

    private static final int BUFFER_BYTES = 1 << 20;
    private static final long REPORT_EVERY_MS = 1_500;

    private ObbPuller() {}

    static Result run(Context context, Log log) {
        String pkg = context.getPackageName();
        Uri cloud = Owner.cloud(context);
        try {
            Bundle list = Calls.call(context, cloud, "obb_list", pkg, null);
            String[] names = list == null ? null : list.getStringArray("names");
            long[] sizes = list == null ? null : list.getLongArray("sizes");
            if (names == null || sizes == null || names.length != sizes.length) return new Result(false, "GamePort gave no list of files");
            if (names.length == 0) return new Result(true, null);
            File dir = context.getObbDir();
            if (dir == null) return new Result(false, "the game has no folder for expansion files");
            if (!dir.isDirectory() && !dir.mkdirs()) return new Result(false, "the folder of expansion files cannot be made: " + dir.getPath());
            long total = 0;
            for (long size : sizes) total += size;
            long[] done = {0};
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.startsWith(".")) return new Result(false, "a file has a name that is not allowed");
                File target = new File(dir, name);
                if (target.isFile() && target.length() == sizes[i]) {
                    done[0] += sizes[i];
                    continue;
                }
                Result copied = copy(context, cloud, pkg, name, sizes[i], new File(dir, name + ".part"), target, done, total, log);
                if (!copied.ok) return copied;
            }
            report(context, cloud, pkg, total, total);
            log.info("obb: " + names.length + " file(s) placed in " + dir.getPath());
            return new Result(true, null);
        } catch (Throwable t) {
            log.info("obb: failed: " + t);
            return new Result(false, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static Result copy(Context context, Uri cloud, String pkg, String name, long size, File part, File target, long[] done, long total, Log log) throws IOException {
        Uri uri = cloud.buildUpon().appendPath("obb").appendQueryParameter("pkg", pkg).appendQueryParameter("name", name).build();
        ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r");
        if (descriptor == null) return new Result(false, "GamePort would not give " + name);
        long written = 0;
        long lastReport = 0;
        byte[] buffer = new byte[BUFFER_BYTES];
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor); OutputStream out = new FileOutputStream(part)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                written += read;
                done[0] += read;
                long now = System.currentTimeMillis();
                if (now - lastReport > REPORT_EVERY_MS) {
                    lastReport = now;
                    report(context, cloud, pkg, done[0], total);
                }
            }
        }
        if (written != size) {
            part.delete();
            return new Result(false, name + " arrived with " + written + " bytes instead of " + size);
        }
        if (target.exists() && !target.delete()) return new Result(false, "the old " + name + " cannot be replaced");
        if (!part.renameTo(target)) return new Result(false, name + " cannot be put in place");
        target.setReadable(true, false);
        log.info("obb: " + name + " placed (" + size + " bytes)");
        return new Result(true, null);
    }

    private static void report(Context context, Uri cloud, String pkg, long done, long total) {
        try {
            Bundle extras = new Bundle();
            extras.putLong("done", done);
            extras.putLong("total", total);
            Calls.call(context, cloud, "obb_progress", pkg, extras);
        } catch (Throwable ignored) {
            // GamePort is stopped for a moment: the next report catches up.
        }
    }
}
