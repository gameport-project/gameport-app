package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SaveSyncTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File root;
    private File backups;
    private FakeLink link;

    @Before
    public void setUp() throws IOException {
        root = temp.newFolder("root");
        backups = temp.newFolder("backups");
        link = new FakeLink();
    }

    private SaveSync sync() {
        return new SaveSync(root, backups, link, new SaveSync.Log() {
            @Override public void info(String message) {}
        }, 2_000);
    }

    private void write(String rel, String content) throws IOException {
        File file = new File(root, rel);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes("UTF-8"));
    }

    private String read(String rel) throws IOException {
        return new String(Files.readAllBytes(new File(root, rel).toPath()), "UTF-8");
    }

    private static String sha1(String content) throws IOException {
        File f = File.createTempFile("sha", ".tmp");
        try {
            Files.write(f.toPath(), content.getBytes("UTF-8"));
            return LocalScanner.sha1(f);
        } finally {
            f.delete();
        }
    }

    @Test
    public void downloadsCloudFilesBeforeTheGameStartsAndTellsGamePort() throws IOException {
        link.plan = new GamePortLink.Plan("DOWNLOAD", Arrays.asList(new GamePortLink.Download("cloud/a", "g/Slot_0/a.sav", sha1("cloud save"), 5_000L)),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        link.cloudContent.put("cloud/a", "cloud save");

        sync().syncAtLaunch();

        assertEquals("cloud save", read("g/Slot_0/a.sav"));
        assertEquals(5_000L, new File(root, "g/Slot_0/a.sav").lastModified());
        assertTrue(link.acknowledged);
        assertTrue(link.ended);
    }

    @Test
    public void keepsTheOldLocalFileInABackupWhenTheCloudReplacesIt() throws IOException {
        write("g/a.sav", "local save");
        link.plan = new GamePortLink.Plan("DOWNLOAD", Arrays.asList(new GamePortLink.Download("cloud/a", "g/a.sav", sha1("cloud save"), 0L)),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        link.cloudContent.put("cloud/a", "cloud save");

        sync().syncAtLaunch();

        assertEquals("cloud save", read("g/a.sav"));
        File backupDay = backups.listFiles()[0];
        assertEquals("local save", new String(Files.readAllBytes(new File(backupDay, "g/a.sav").toPath()), "UTF-8"));
    }

    @Test
    public void refusesACorruptDownloadAndKeepsTheLocalSave() throws IOException {
        write("g/a.sav", "local save");
        link.plan = new GamePortLink.Plan("DOWNLOAD", Arrays.asList(new GamePortLink.Download("cloud/a", "g/a.sav", sha1("expected"), 0L)),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        link.cloudContent.put("cloud/a", "something else");

        sync().syncAtLaunch();

        assertEquals("local save", read("g/a.sav"));
        assertFalse(new File(root, "g/a.sav.gpdl").exists());
    }

    @Test
    public void aConflictAnsweredWithCloudBacksUpEverythingThenDownloads() throws IOException {
        write("g/a.sav", "local save");
        link.plan = new GamePortLink.Plan("CONFLICT", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        link.forcedPlans.put("cloud", new GamePortLink.Plan("DOWNLOAD", Arrays.asList(new GamePortLink.Download("cloud/a", "g/a.sav", sha1("cloud save"), 0L)),
                Collections.<String>emptyList(), Collections.<String>emptyList()));
        link.cloudContent.put("cloud/a", "cloud save");
        link.choice = "CLOUD";

        sync().syncAtLaunch();

        assertTrue(link.conflictShown);
        assertEquals("cloud save", read("g/a.sav"));
        File backupDay = backups.listFiles()[0];
        assertEquals("local save", new String(Files.readAllBytes(new File(backupDay, "g/a.sav").toPath()), "UTF-8"));
    }

    @Test
    public void aConflictWithoutAnAnswerLeavesEverythingAlone() throws IOException {
        write("g/a.sav", "local save");
        link.plan = new GamePortLink.Plan("CONFLICT", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        link.choice = "PENDING";

        sync().syncAtLaunch();

        assertEquals("local save", read("g/a.sav"));
        assertFalse(link.acknowledged);
    }

    @Test
    public void aConflictPostponedByThePlayerLeavesEverythingAlone() throws IOException {
        write("g/a.sav", "local save");
        link.plan = new GamePortLink.Plan("CONFLICT", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        link.choice = "LATER";

        long started = System.currentTimeMillis();
        sync().syncAtLaunch();

        assertEquals("local save", read("g/a.sav"));
        assertTrue("did not wait for the timeout", System.currentTimeMillis() - started < 1_500);
    }

    @Test
    public void ignoresItsOwnBackupFolderWhenScanning() throws IOException {
        write("g/a.sav", "real save");
        write("g/gameport-backup/20260101-000000/a.sav", "an old backup");
        write("g/gameport/notes.sav", "shim data");

        java.util.List<LocalFile> files = new LocalScanner(root).scan(java.util.Arrays.asList(new SaveRule("g", "*.sav", true)));

        assertEquals(1, files.size());
        assertEquals("g/a.sav", files.get(0).rel);
    }

    @Test
    public void backupPathsDropTheGamesOwnFolder() {
        assertEquals("Slot_0/a.es3", SaveSync.shortPath("Android/data/de.erthu.ancientdungeonfull/files/Slot_0/a.es3"));
        assertEquals("other/place/a.sav", SaveSync.shortPath("other/place/a.sav"));
    }

    @Test
    public void uploadsLocalChangesWhilePlaying() throws IOException {
        write("g/a.sav", "new progress");
        link.plan = new GamePortLink.Plan("UPLOAD", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Arrays.asList("g/a.sav"));

        sync().uploadIfChanged();

        assertEquals("new progress", link.pushed.get("g/a.sav"));
        assertTrue(link.committed);
    }

    @Test
    public void neverDownloadsWhileTheGameIsRunning() throws IOException {
        link.plan = new GamePortLink.Plan("DOWNLOAD", Arrays.asList(new GamePortLink.Download("cloud/a", "g/a.sav", sha1("cloud save"), 0L)),
                Collections.<String>emptyList(), Collections.<String>emptyList());
        link.cloudContent.put("cloud/a", "cloud save");

        sync().uploadIfChanged();

        assertFalse(new File(root, "g/a.sav").exists());
    }

    @Test
    public void doesNotBotherGamePortWhenNothingChangedSinceTheLastSync() throws IOException {
        write("g/a.sav", "same");
        link.plan = new GamePortLink.Plan("NONE", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        SaveSync sync = sync();

        sync.syncAtLaunch();
        int callsAfterLaunch = link.planCalls;
        sync.uploadIfChanged();

        assertEquals(callsAfterLaunch, link.planCalls);
    }

    @Test
    public void skipsWhenGamePortIsOffline() throws IOException {
        link.status = "OFFLINE";
        write("g/a.sav", "local");

        sync().syncAtLaunch();

        assertEquals(0, link.planCalls);
        assertEquals("local", read("g/a.sav"));
    }

    private GamePortLink.Plan upload(String rel) {
        return new GamePortLink.Plan("UPLOAD", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Arrays.asList(rel));
    }

    @Test
    public void aSaveIsSentWhileTheGameRunsOnceItHasStoppedChanging() throws IOException {
        SaveSync sync = sync();
        link.plan = new GamePortLink.Plan("NONE", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        sync.syncAtLaunch();
        write("g/a.sav", "progress");
        link.plan = upload("g/a.sav");

        sync.poll(1_000);
        assertFalse("a file that may still be written is not sent at the first look", link.committed);
        sync.poll(5_000);
        assertEquals("progress", link.pushed.get("g/a.sav"));
        assertTrue(link.committed);
    }

    @Test
    public void aSaveStillChangingIsNotSent() throws IOException {
        SaveSync sync = sync();
        sync.syncAtLaunch();
        write("g/a.sav", "one");
        link.plan = upload("g/a.sav");
        sync.poll(1_000);
        write("g/a.sav", "two, longer");
        sync.poll(5_000);
        assertFalse(link.committed);
        sync.poll(9_000);
        assertEquals("two, longer", link.pushed.get("g/a.sav"));
    }

    @Test
    public void whatIsNotSentIsNotMistakenForSentAndIsTriedAgainAfterAWhile() throws IOException {
        SaveSync sync = sync();
        sync.syncAtLaunch();
        write("g/a.sav", "progress");
        link.plan = upload("g/a.sav");
        link.commitResult = false;
        sync.poll(1_000);
        sync.poll(5_000);
        assertTrue(link.committed);
        link.committed = false;
        sync.poll(9_000);
        assertFalse("not tried again at once", link.committed);
        link.commitResult = true;
        sync.poll(5_000 + SaveSync.RETRY_AFTER_MS);
        assertTrue(link.committed);
    }

    @Test
    public void gamePortIsToldWhatTheSavesAreEvenWhenNothingCanBeSent() throws IOException {
        SaveSync sync = sync();
        sync.syncAtLaunch();
        write("g/a.sav", "progress");
        link.plan = new GamePortLink.Plan("NONE", Collections.<GamePortLink.Download>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        link.status = "OFFLINE";
        int before = link.observed;
        assertTrue(sync.reportLocal());
        assertEquals(before + 1, link.observed);
        sync.reportLocal();
        assertEquals("the same saves are not told twice", before + 1, link.observed);
    }

    @Test
    public void theLastLookBeforeTheGameGoesSendsWhatWasNot() throws IOException {
        SaveSync sync = sync();
        sync.syncAtLaunch();
        write("g/a.sav", "last progress");
        link.plan = upload("g/a.sav");

        assertTrue(sync.flush());
        assertEquals("last progress", link.pushed.get("g/a.sav"));
    }

    @Test
    public void aGameWhoseSyncCouldNotBeginIsLookedAtAgainLater() throws IOException {
        SaveSync sync = sync();
        link.status = "OFFLINE";
        sync.syncAtLaunch();
        link.status = "READY";
        write("g/a.sav", "progress");
        link.plan = upload("g/a.sav");

        sync.poll(SaveSync.RETRY_AFTER_MS);

        assertTrue(link.committed);
    }

    private final class FakeLink implements GamePortLink {
        String status = "READY";
        Plan plan = new Plan("NONE", Collections.<Download>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList());
        Map<String, Plan> forcedPlans = new HashMap<>();
        Map<String, String> cloudContent = new HashMap<>();
        Map<String, String> pushed = new HashMap<>();
        String choice = "PENDING";
        boolean acknowledged, committed, ended, conflictShown, commitResult = true;
        int planCalls, observed;

        @Override public Begin begin() {
            return new Begin(status, new ArrayList<>(Arrays.asList(new SaveRule("g", "*.sav", true))));
        }
        @Override public Plan plan(List<LocalFile> files, String force) {
            planCalls++;
            return force != null && forcedPlans.containsKey(force) ? forcedPlans.get(force) : plan;
        }
        @Override public String conflictChoice() { return choice; }
        @Override public void showConflict() { conflictShown = true; }
        @Override public InputStream fetch(String cloudName) throws IOException {
            return new ByteArrayInputStream(cloudContent.get(cloudName).getBytes("UTF-8"));
        }
        @Override public OutputStream push(final String rel) {
            return new ByteArrayOutputStream() {
                @Override public void close() throws IOException {
                    super.close();
                    pushed.put(rel, new String(toByteArray(), "UTF-8"));
                }
            };
        }
        @Override public boolean acknowledge(List<LocalFile> files) { acknowledged = true; return true; }
        @Override public boolean observe(List<LocalFile> files) { observed++; return true; }
        @Override public boolean commit() { committed = true; return commitResult; }
        @Override public void end() { ended = true; }
    }
}
