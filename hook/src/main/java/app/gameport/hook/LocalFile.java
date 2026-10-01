package app.gameport.hook;

/** A save file on the headset; [rel] is relative to the shared storage root. */
final class LocalFile {
    final String rel;
    final String sha1;
    final long size;
    final long mtime;

    LocalFile(String rel, String sha1, long size, long mtime) {
        this.rel = rel;
        this.sha1 = sha1;
        this.size = size;
        this.mtime = mtime;
    }

    String encode() {
        return rel + "\t" + sha1 + "\t" + size + "\t" + mtime;
    }
}
