package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SaveRootsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private void write(File file) throws Exception {
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), "{}".getBytes("UTF-8"));
    }

    @Test
    public void aPathWithoutTheMarkStaysInTheSharedStorage() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        SaveRoots roots = new SaveRoots(shared, internal, "com.Studio.Game");

        assertEquals(new File(shared, "Android/data/com.Studio.Game/files"), roots.resolve("Android/data/com.Studio.Game/files"));
    }

    @Test
    public void withNothingYetTheSharedStorageIsUsedAndThePackageKeepsItsCase() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        SaveRoots roots = new SaveRoots(shared, internal, "com.Studio.Game");

        // The rule Steam publishes writes the package in lower case; the real folder keeps the case of the package.
        assertEquals(new File(shared, "Android/data/com.Studio.Game/files/saves"), roots.resolve("@internal/com.studio.game/files/saves"));
    }

    @Test
    public void filesAlreadyInThePrivateDataKeepThePrivateDataInUse() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        write(new File(internal, "com.Studio.Game/files/saves/slot1.json"));
        SaveRoots roots = new SaveRoots(shared, internal, "com.Studio.Game");
        roots.prepare(Arrays.asList(new SaveRule("@internal/com.studio.game/files/saves", "*", true)));

        assertEquals(new File(internal, "com.Studio.Game/files/saves"), roots.resolve("@internal/com.studio.game/files/saves"));
        assertEquals(new File(internal, "com.Studio.Game/files/saves/slot1.json"), roots.resolve("@internal/com.studio.game/files/saves/slot1.json"));
    }

    @Test
    public void filesInBothPlacesMeanTheSharedStorage() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        write(new File(internal, "com.Studio.Game/files/saves/a.json"));
        write(new File(shared, "Android/data/com.Studio.Game/files/saves/b.json"));
        SaveRoots roots = new SaveRoots(shared, internal, "com.Studio.Game");
        roots.prepare(Arrays.asList(new SaveRule("@internal/com.studio.game/files/saves", "*", true)));

        assertEquals(new File(shared, "Android/data/com.Studio.Game/files/saves"), roots.resolve("@internal/com.studio.game/files/saves"));
    }

    @Test
    public void theScannerFindsTheSavesUnderTheRulesName() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        write(new File(shared, "Android/data/com.Studio.Game/files/saves/slot1.json"));
        SaveRoots roots = new SaveRoots(shared, internal, "com.Studio.Game");

        List<LocalFile> files = new LocalScanner(roots).scan(Arrays.asList(new SaveRule("@internal/com.studio.game/files/saves", "*", true)));

        assertEquals(1, files.size());
        assertEquals("@internal/com.studio.game/files/saves/slot1.json", files.get(0).rel);
    }

    private SaveRoots roots(File shared, File internal) {
        return new SaveRoots(shared, internal, "com.Studio.Game", new File(internal, "place"));
    }

    private static void touch(File file, long when) {
        file.setLastModified(when);
    }

    @Test
    public void whileThePlaceIsUnknownTheOtherPlaceIsGivenToPutTheFilesIn() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        SaveRoots roots = roots(shared, internal);

        assertEquals(new File(internal, "com.Studio.Game/files/saves/a"), roots.alternate("@internal/com.studio.game/files/saves/a"));
        assertEquals(null, roots.alternate("Android/data/com.Studio.Game/files/x"));
    }

    @Test
    public void aGameSeenWritingInTheSharedStorageKeepsThemThere() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        write(new File(internal, "com.Studio.Game/files/saves/old"));
        File written = new File(shared, "Android/data/com.Studio.Game/files/saves/new");
        write(written);
        SaveRoots roots = roots(shared, internal);
        List<SaveRule> rules = Arrays.asList(new SaveRule("@internal/com.studio.game/files/saves", "*", true));
        touch(new File(internal, "com.Studio.Game/files/saves/old"), 1_000L);
        touch(written, 9_000L);

        String learnt = roots.observe(rules, 5_000L);
        roots.prepare(rules);

        assertTrue(learnt.contains("shared storage"));
        assertEquals(new File(shared, "Android/data/com.Studio.Game/files/saves"), roots.resolve("@internal/com.studio.game/files/saves"));
        // Once it is known, no second copy is asked for.
        assertEquals(null, roots.alternate("@internal/com.studio.game/files/saves/a"));
    }

    @Test
    public void aGameSeenWritingInThePrivateDataKeepsThemThereEvenWhenTheSharedStorageHoldsFiles() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        File imported = new File(shared, "Android/data/com.Studio.Game/files/saves/imported");
        File written = new File(internal, "com.Studio.Game/files/saves/new");
        write(imported);
        write(written);
        touch(imported, 1_000L);
        touch(written, 9_000L);
        SaveRoots roots = roots(shared, internal);
        List<SaveRule> rules = Arrays.asList(new SaveRule("@internal/com.studio.game/files/saves", "*", true));

        roots.observe(rules, 5_000L);
        roots.prepare(rules);

        assertEquals(new File(internal, "com.Studio.Game/files/saves"), roots.resolve("@internal/com.studio.game/files/saves"));
    }

    @Test
    public void nothingIsLearntWhenBothPlacesChangedOrNeitherDid() throws Exception {
        File shared = temp.newFolder("shared");
        File internal = temp.newFolder("internal");
        File a = new File(shared, "Android/data/com.Studio.Game/files/saves/a");
        File b = new File(internal, "com.Studio.Game/files/saves/b");
        write(a);
        write(b);
        SaveRoots roots = roots(shared, internal);
        List<SaveRule> rules = Arrays.asList(new SaveRule("@internal/com.studio.game/files/saves", "*", true));
        touch(a, 9_000L);
        touch(b, 9_000L);

        assertEquals(null, roots.observe(rules, 5_000L));
        touch(a, 1_000L);
        touch(b, 1_000L);
        assertEquals(null, roots.observe(rules, 5_000L));
    }
}
