package app.gameport.hook;

/** Where a game keeps synced saves: a folder under the shared storage root and a file pattern. */
final class SaveRule {
    final String localDir;
    final String pattern;
    final boolean recursive;

    SaveRule(String localDir, String pattern, boolean recursive) {
        this.localDir = localDir;
        this.pattern = pattern;
        this.recursive = recursive;
    }

    /** Parses "localDir TAB pattern TAB recursive TAB cloudPrefix" as GamePort sends it. */
    static SaveRule parse(String line) {
        String[] parts = line.split("\t", -1);
        return new SaveRule(parts[0], parts[1], "1".equals(parts[2]));
    }
}
