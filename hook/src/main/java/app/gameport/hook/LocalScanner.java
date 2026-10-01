package app.gameport.hook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lists the save files a game currently has, following the rules Steam publishes for it. */
final class LocalScanner {
    /** Folders GamePort itself keeps inside a game's data: never save files, and backups hold copies of them. */
    private static final String[] OWN_FOLDERS = {"gameport-backup", "gameport"};

    private final File root;

    LocalScanner(File root) {
        this.root = root;
    }

    List<LocalFile> scan(List<SaveRule> rules) throws IOException {
        List<LocalFile> found = new ArrayList<>();
        for (SaveRule rule : rules) {
            File dir = rule.localDir.isEmpty() ? root : new File(root, rule.localDir);
            walk(dir, rule.localDir, rule, found);
        }
        // Two rules can cover the same file (several patterns in one folder); count it once.
        List<LocalFile> unique = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (LocalFile file : found) if (seen.add(file.rel)) unique.add(file);
        Collections.sort(unique, new Comparator<LocalFile>() {
            @Override public int compare(LocalFile a, LocalFile b) { return a.rel.compareTo(b.rel); }
        });
        return unique;
    }

    private void walk(File dir, String relDir, SaveRule rule, List<LocalFile> out) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            String rel = relDir.isEmpty() ? child.getName() : relDir + "/" + child.getName();
            if (child.isDirectory()) {
                if (rule.recursive && !isOwnFolder(child.getName())) walk(child, rel, rule, out);
            } else if (Glob.matches(rule.pattern, child.getName())) {
                out.add(new LocalFile(rel, sha1(child), child.length(), child.lastModified()));
            }
        }
    }

    private static boolean isOwnFolder(String name) {
        for (String own : OWN_FOLDERS) if (own.equals(name)) return true;
        return false;
    }

    static String sha1(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) digest.update(buffer, 0, read);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
