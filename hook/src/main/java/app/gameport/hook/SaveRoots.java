package app.gameport.hook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a save file really is. Paths are relative to the shared storage, except the ones starting with [INTERNAL]: those come from Steam's
 * `AndroidInternalData` root, the private data of the app, and their first part is the package name.
 *
 * A game does not always keep them where Steam says. A Unity game on a headset writes its files in the shared storage
 * (`Android/data/<package>/files`) while its Steam Frame build uses the private data. Which one a game really uses is learnt by looking at
 * where it writes ([observe]) and kept in a small file; until then the folder that already holds files is used, and with none, the shared storage,
 * and what is brought to the device is put in both so the game finds it wherever it looks ([alternate]). Only the game itself can reach the private
 * data, which is why the hook, running inside the game, does the reading and writing.
 */
final class SaveRoots {
    /** Marks a path that Steam places in the private data of the app. */
    static final String INTERNAL = "@internal/";

    private static final String PRIVATE = "private";
    private static final String SHARED = "shared";

    private final File shared;
    private final File internal;
    private final String packageName;
    /** Where the game was seen writing its saves: "private" or "shared"; the file does not exist until it was seen. */
    private final File placeFile;
    /** The folder chosen for each rule folder, decided when the rules are looked at ([prepare]) and kept for the rest of the pass. */
    private final Map<String, File> chosen = new HashMap<>();

    SaveRoots(File shared, File internal, String packageName, File placeFile) {
        this.shared = shared;
        this.internal = internal;
        this.packageName = packageName;
        this.placeFile = placeFile;
    }

    SaveRoots(File shared, File internal, String packageName) {
        this(shared, internal, packageName, null);
    }

    /** Shared storage only: paths marked [INTERNAL] are looked for in a folder of their own beside it, which tests can use. */
    SaveRoots(File shared) {
        this(shared, new File(shared, INTERNAL), null, null);
    }

    File shared() {
        return shared;
    }

    /** Decides, for each rule folder, where it is. Called before the folders are read, and again each pass so a change is seen. */
    synchronized void prepare(List<SaveRule> rules) {
        chosen.clear();
        for (SaveRule rule : rules) if (rule.localDir.startsWith(INTERNAL)) chosen.put(rule.localDir, choose(rule.localDir));
    }

    synchronized File resolve(String rel) {
        if (!rel.startsWith(INTERNAL)) return new File(shared, rel);
        String best = null;
        for (String dir : chosen.keySet()) {
            if ((rel.equals(dir) || rel.startsWith(dir + "/")) && (best == null || dir.length() > best.length())) best = dir;
        }
        if (best == null) return choose(rel);
        File base = chosen.get(best);
        return rel.equals(best) ? base : new File(base, rel.substring(best.length() + 1));
    }

    /**
     * The other place the file could be, while it is not known where the game keeps its saves: what is brought to the device is put there too.
     * Null for a path that is not in doubt (not marked, or the place is known).
     */
    synchronized File alternate(String rel) {
        if (!rel.startsWith(INTERNAL) || packageName == null || knownPlace() != null) return null;
        File chosenFile = resolve(rel);
        File inPrivate = locate(rel, true);
        File inShared = locate(rel, false);
        if (inPrivate == null || inShared == null) return null;
        return chosenFile.equals(inShared) ? inPrivate : inShared;
    }

    /** The other place a file of [rel] could be, whether or not it is known where the game keeps its saves; null for a path that is not in doubt. */
    synchronized File counterpart(String rel) {
        if (!rel.startsWith(INTERNAL) || packageName == null) return null;
        File inPrivate = locate(rel, true);
        File inShared = locate(rel, false);
        if (inPrivate == null || inShared == null) return null;
        return resolve(rel).equals(inShared) ? inPrivate : inShared;
    }

    /**
     * Looks at where the game wrote since [since]: when only one of the two places changed, that is where it keeps its saves, and it is remembered.
     * Returns a line for the log when something was learnt or when Steam's place and the game's differ, null otherwise.
     */
    synchronized String observe(List<SaveRule> rules, long since) {
        if (packageName == null || placeFile == null) return null;
        for (SaveRule rule : rules) {
            if (!rule.localDir.startsWith(INTERNAL)) continue;
            File inPrivate = locate(rule.localDir, true);
            File inShared = locate(rule.localDir, false);
            if (inPrivate == null || inShared == null) continue;
            boolean privateChanged = changedSince(inPrivate, since);
            boolean sharedChanged = changedSince(inShared, since);
            if (privateChanged == sharedChanged) continue;
            String place = privateChanged ? PRIVATE : SHARED;
            if (place.equals(knownPlace())) continue;
            remember(place);
            return "saves: the game keeps them in its " + (privateChanged ? "private data" : "shared storage") + (privateChanged ? "" : ", not where Steam says (private data)");
        }
        return null;
    }

    private String knownPlace() {
        if (placeFile == null || !placeFile.isFile()) return null;
        try {
            String text = new String(Files.readAllBytes(placeFile.toPath()), "UTF-8").trim();
            return PRIVATE.equals(text) || SHARED.equals(text) ? text : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void remember(String place) {
        try {
            File parent = placeFile.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(placeFile.toPath(), place.getBytes("UTF-8"));
        } catch (IOException e) {
            // Not remembered: it is looked at again.
        }
    }

    private File choose(String rel) {
        File inPrivate = locate(rel, true);
        File inShared = locate(rel, false);
        if (inPrivate == null || inShared == null) return new File(internal, rel.substring(INTERNAL.length()));
        String place = knownPlace();
        if (PRIVATE.equals(place)) return inPrivate;
        if (SHARED.equals(place)) return inShared;
        return holdsFiles(inPrivate) && !holdsFiles(inShared) ? inPrivate : inShared;
    }

    /** The folder or file of [rel] in the private data ([inPrivate]) or in the shared storage; null when [rel] is not about this package. */
    private File locate(String rel, boolean inPrivate) {
        String rest = rel.substring(INTERNAL.length());
        int slash = rest.indexOf('/');
        String first = slash < 0 ? rest : rest.substring(0, slash);
        String below = slash < 0 ? "" : rest.substring(slash + 1);
        // Studios sometimes write the package name in lower case in the rule; the real folder keeps the case of the package.
        if (packageName == null || !first.equalsIgnoreCase(packageName)) return null;
        File base = inPrivate ? new File(internal, packageName) : new File(new File(shared, "Android/data"), packageName);
        return below.isEmpty() ? base : new File(base, below);
    }

    private static boolean holdsFiles(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File child : children) if (child.isFile() || holdsFiles(child)) return true;
        return false;
    }

    private static boolean changedSince(File dir, long since) {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile() ? child.lastModified() >= since : changedSince(child, since)) return true;
        }
        return false;
    }
}
