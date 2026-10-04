package app.gameport.hook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** The conversation with GamePort. On a device it is the content provider; in tests a fake. */
interface GamePortLink {
    /** Result of asking GamePort to start a sync. */
    final class Begin {
        final String status;
        final List<SaveRule> rules;

        Begin(String status, List<SaveRule> rules) {
            this.status = status;
            this.rules = rules;
        }
    }

    /** What GamePort wants done after comparing the game's files with the cloud. */
    final class Plan {
        final String action;
        /** For DOWNLOAD: cloud name, local path, sha1, size and timestamp of each file to fetch. */
        final List<Download> downloads;
        final List<String> deleteLocal;
        final List<String> uploads;

        Plan(String action, List<Download> downloads, List<String> deleteLocal, List<String> uploads) {
            this.action = action;
            this.downloads = downloads;
            this.deleteLocal = deleteLocal;
            this.uploads = uploads;
        }
    }

    final class Download {
        final String cloudName;
        final String rel;
        final String sha1;
        final long timestamp;

        Download(String cloudName, String rel, String sha1, long timestamp) {
            this.cloudName = cloudName;
            this.rel = rel;
            this.sha1 = sha1;
            this.timestamp = timestamp;
        }
    }

    Begin begin() throws IOException;

    /** [force] is null, "local" or "cloud" (the player's choice after a conflict). */
    Plan plan(List<LocalFile> files, String force) throws IOException;

    /** "PENDING" until the player answers, then "LOCAL" or "CLOUD". */
    String conflictChoice() throws IOException;

    /** Opens the conflict question in front of the game. */
    void showConflict();

    InputStream fetch(String cloudName) throws IOException;

    OutputStream push(String rel) throws IOException;

    boolean acknowledge(List<LocalFile> files) throws IOException;

    /**
     * Tells GamePort what the saves are right now, without asking for anything: it needs no connection to Steam, so what GamePort shows
     * (and whether something is left to send) is true even when sending is not possible.
     */
    boolean observe(List<LocalFile> files) throws IOException;

    boolean commit() throws IOException;

    void end();
}
