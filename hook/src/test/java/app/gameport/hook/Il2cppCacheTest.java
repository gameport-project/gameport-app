package app.gameport.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Il2cppCacheTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File files() throws Exception {
        return temp.newFolder("files");
    }

    private File marker() throws Exception {
        return new File(temp.newFolder("internal"), "gameport/il2cpp_installed_at");
    }

    private File copy(File files) throws Exception {
        File file = new File(files, "il2cpp/Metadata/global-metadata.dat");
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), new byte[] {1, 2, 3});
        return file;
    }

    private String text(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), "UTF-8").trim();
    }

    @Test
    public void aCopyMadeBeforeTheMarkerWasWrittenIsDropped() throws Exception {
        File files = files();
        File marker = marker();
        File copy = copy(files);
        assertTrue(Il2cppCache.refresh(files, marker, 1000L));
        assertFalse(copy.exists());
        assertEquals("1000", text(marker));
    }

    @Test
    public void aCopyIsKeptWhileTheGameIsNotInstalledAgain() throws Exception {
        File files = files();
        File marker = marker();
        Il2cppCache.refresh(files, marker, 1000L);
        File copy = copy(files);
        assertFalse(Il2cppCache.refresh(files, marker, 1000L));
        assertTrue(copy.exists());
    }

    @Test
    public void aCopyIsDroppedWhenTheGameWasInstalledAgain() throws Exception {
        File files = files();
        File marker = marker();
        Il2cppCache.refresh(files, marker, 1000L);
        File copy = copy(files);
        assertTrue(Il2cppCache.refresh(files, marker, 2000L));
        assertFalse(copy.exists());
        assertEquals("2000", text(marker));
    }

    @Test
    public void aFirstRunWithNoCopyJustWritesTheMarker() throws Exception {
        File files = files();
        File marker = marker();
        assertFalse(Il2cppCache.refresh(files, marker, 1000L));
        assertEquals("1000", text(marker));
    }

    @Test
    public void whatIsNotTheCopyIsNotTouched() throws Exception {
        File files = files();
        File save = new File(files, "EscapeSimulator-v13.sav");
        Files.write(save.toPath(), new byte[] {9});
        File other = new File(files, "il2cpp/other/keep.bin");
        other.getParentFile().mkdirs();
        Files.write(other.toPath(), new byte[] {7});
        copy(files);
        Il2cppCache.refresh(files, marker(), 1000L);
        assertTrue(save.exists());
        assertTrue(other.exists());
        assertFalse(new File(files, Il2cppCache.COPY).exists());
    }

    @Test
    public void aFolderOfSeveralFilesIsDroppedWhole() throws Exception {
        File files = files();
        copy(files);
        File deeper = new File(files, "il2cpp/Metadata/sub/deep.bin");
        deeper.getParentFile().mkdirs();
        Files.write(deeper.toPath(), new byte[] {5});
        assertTrue(Il2cppCache.refresh(files, marker(), 1000L));
        assertFalse(new File(files, Il2cppCache.COPY).exists());
    }

    @Test
    public void aMissingFolderOfFilesIsNotAnError() throws Exception {
        File files = new File(temp.getRoot(), "absent");
        assertFalse(Il2cppCache.refresh(files, marker(), 1000L));
    }

    @Test
    public void aMarkerThatIsNotANumberIsTakenForAnOldOne() throws Exception {
        File files = files();
        File marker = marker();
        marker.getParentFile().mkdirs();
        Files.write(marker.toPath(), "not a number\n".getBytes("UTF-8"));
        copy(files);
        assertTrue(Il2cppCache.refresh(files, marker, 1000L));
        assertEquals("1000", text(marker));
    }

    @Test
    public void aLinkIsRemovedAndNeverFollowed() throws Exception {
        File files = files();
        File outside = temp.newFolder("outside");
        File precious = new File(outside, "precious.bin");
        Files.write(precious.toPath(), new byte[] {4});
        File metadata = new File(files, "il2cpp/Metadata");
        metadata.getParentFile().mkdirs();
        try {
            Files.createSymbolicLink(metadata.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // no symbolic links on this system: nothing to check
        }
        Il2cppCache.refresh(files, marker(), 1000L);
        assertTrue("what the link pointed at is untouched", precious.exists());
    }
}
