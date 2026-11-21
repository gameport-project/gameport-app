package app.gameport.hook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Drops the copy Unity keeps of a game's compiled code when the game was installed again since.
 *
 * An IL2CPP game's code is described by one file, {@code global-metadata.dat}, which holds every string of the game. Unity copies it out of the APK into the game's folder of
 * files ({@code Android/data/<package>/files/il2cpp/Metadata}) the first time the game runs, and reads that copy from then on: it does not take the APK's again when the APK is
 * replaced by one of the same size. A patch that changes a string of the game without changing its size (the way to the game's own folders, written with the package name it
 * came with) therefore never reaches a game that has already run: the game goes on with the file of its first run. This runs when the game starts, before Unity does, and removes
 * the copy when the game was installed again since the last time it ran. Unity makes it again from the APK it has now. Nothing else of the game's folder is touched: saves,
 * settings and the rest are not Unity's cache.
 */
final class Il2cppCache {
    private Il2cppCache() {}

    /** Where Unity keeps its copy, under the game's folder of files. */
    static final String COPY = "il2cpp/Metadata";

    /**
     * Removes the copy of the game's code when [installedAt] (when the game was last installed) is not the one written in [marker] by the last run, then writes it. Returns true when a
     * copy was removed. When the copy cannot be removed the marker is left as it was, so the next start tries again.
     */
    static boolean refresh(File externalFiles, File marker, long installedAt) {
        String now = Long.toString(installedAt);
        if (now.equals(read(marker))) return false;
        File copy = new File(externalFiles, COPY);
        boolean existed = copy.exists();
        if (existed && !deleteTree(copy)) return false;
        write(marker, now);
        return existed;
    }

    private static String read(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(File file, String text) {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text);
        } catch (IOException e) {
            // Without a marker the copy is removed again at the next start: Unity makes it again each time, which only costs a copy.
        }
    }

    /** Deletes a folder and what it holds. A link is removed, never followed. */
    private static boolean deleteTree(File file) {
        File[] children = file.isDirectory() && !isLink(file) ? file.listFiles() : null;
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        return file.delete() || !file.exists();
    }

    private static boolean isLink(File file) {
        try {
            return !file.getCanonicalPath().equals(new File(file.getParentFile().getCanonicalFile(), file.getName()).getPath());
        } catch (IOException e) {
            return true;
        }
    }
}
