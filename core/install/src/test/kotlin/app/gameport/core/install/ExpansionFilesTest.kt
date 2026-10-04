package app.gameport.core.install

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExpansionFilesTest {
    @get:Rule val folder = TemporaryFolder()

    private val pkg = "com.example.game"

    private fun download(vararg files: String): File = folder.newFolder("download").also { root ->
        files.forEach { path -> File(root, path).apply { parentFile?.mkdirs(); writeText("data of $path") } }
    }

    private fun place(downloaded: File, target: File = File(folder.root, "obb/$pkg")) = ExpansionFiles.place(downloaded, target, pkg, skipped = "patched")

    @Test
    fun `a download that holds only the APK places nothing and does not create the folder`() {
        val target = File(folder.root, "obb/$pkg")
        assertEquals(emptyList<String>(), place(download("app.apk"), target))
        assertFalse(target.exists())
    }

    @Test
    fun `the patched APKs and what the downloader keeps are left alone`() {
        val downloaded = download("app.apk", "patched/app.apk", ".DepotDownloader/state")
        assertEquals(emptyList<String>(), place(downloaded))
        assertTrue(File(downloaded, "patched/app.apk").exists())
    }

    @Test
    fun `an expansion file is moved to the top of the folder`() {
        val downloaded = download("app.apk", "main.416.$pkg.obb")
        val target = File(folder.root, "obb/$pkg")
        assertEquals(listOf("main.416.$pkg.obb"), place(downloaded, target))
        assertEquals("data of main.416.$pkg.obb", File(target, "main.416.$pkg.obb").readText())
        assertFalse(File(downloaded, "main.416.$pkg.obb").exists())
    }

    @Test
    fun `data too large for the APK is placed under the name it was published with`() {
        val downloaded = download("app.apk", "main_assets_all.bundle")
        val target = File(folder.root, "obb/$pkg")
        assertEquals(listOf("main_assets_all.bundle"), place(downloaded, target))
        assertTrue(File(target, "main_assets_all.bundle").isFile)
    }

    @Test
    fun `the way to the folder spelled out by the depot is not repeated, and folders below it are kept`() {
        val downloaded = download("app.apk", "Android/obb/$pkg/a.bundle", "obb/b.bundle", "obb/aa/c.bundle", "other/d.bundle")
        val target = File(folder.root, "obb/$pkg")
        // The depot has an obb folder: what is outside it is not for the device.
        assertEquals(setOf("a.bundle", "b.bundle", "aa/c.bundle"), place(downloaded, target).toSet())
        assertTrue(File(target, "aa/c.bundle").isFile)
        assertFalse(File(target, "other").exists())
    }

    @Test
    fun `folders below the top are kept when the depot has no obb folder`() {
        val downloaded = download("app.apk", "data/aa/c.bundle", "top.bundle")
        val target = File(folder.root, "obb/$pkg")
        assertEquals(setOf("data/aa/c.bundle", "top.bundle"), place(downloaded, target).toSet())
    }

    @Test
    fun `a file already there is replaced`() {
        val target = File(folder.root, "obb/$pkg").apply { mkdirs() }
        File(target, "main_assets_all.bundle").writeText("old")
        place(download("main_assets_all.bundle"), target)
        assertEquals("data of main_assets_all.bundle", File(target, "main_assets_all.bundle").readText())
    }

    @Test
    fun `an update replaces the expansion files of the old version, whatever they were renamed to`() {
        val target = File(folder.root, "obb/$pkg").apply { mkdirs() }
        File(target, "main.130.$pkg.obb").writeText("old, renamed to follow the patched version")
        File(target, "patch.130.$pkg.obb").writeText("old patch")
        val downloaded = download("app.apk", "main.120.$pkg.obb")
        assertEquals(listOf("main.120.$pkg.obb"), place(downloaded, target))
        assertFalse(File(target, "main.130.$pkg.obb").exists())
        assertEquals("data of main.120.$pkg.obb", File(target, "main.120.$pkg.obb").readText())
        // The patch the new download does not bring is not its to remove.
        assertTrue(File(target, "patch.130.$pkg.obb").exists())
        // The new file then follows the patched version and is the one the game finds.
        assertEquals(listOf("main.130.$pkg.obb"), ExpansionFiles.align(target, pkg, 130))
        assertEquals("data of main.120.$pkg.obb", File(target, "main.130.$pkg.obb").readText())
    }

    @Test
    fun `files follow the version of the APK installed and are not renamed over another`() {
        val target = File(folder.root, "obb/$pkg").apply { mkdirs() }
        File(target, "main.110.$pkg.obb").writeText("main")
        File(target, "chunk.1.$pkg.obb").writeText("chunk")
        File(target, "patch.140.$pkg.obb").writeText("already there")
        File(target, "patch.120.$pkg.obb").writeText("other")
        assertEquals(listOf("main.140.$pkg.obb"), ExpansionFiles.align(target, pkg, 140))
        assertTrue(File(target, "chunk.1.$pkg.obb").exists())
        assertEquals("already there", File(target, "patch.140.$pkg.obb").readText())
        assertTrue(File(target, "patch.120.$pkg.obb").exists())
        assertEquals(emptyList<String>(), ExpansionFiles.align(File(folder.root, "nowhere"), pkg, 140))
    }

    @Test
    fun `a depot with an obb folder only has that folder placed, not the unpacked APK or the debug files`() {
        val downloaded = download(
            "app.apk", "AndroidManifest.xml", "res/layout.xml", "Game_BackUpThisFolder/symbols.bin",
            "obb/assets/level1.bundle", "obb/main.416.$pkg.obb",
        )
        val target = File(folder.root, "obb/$pkg")
        assertEquals(setOf("assets/level1.bundle", "main.416.$pkg.obb"), place(downloaded, target).toSet())
        assertFalse(File(target, "AndroidManifest.xml").exists())
        assertFalse(File(target, "res").exists())
        assertFalse(File(target, "Game_BackUpThisFolder").exists())
    }

    @Test
    fun `a depot with no obb folder still has everything but its APK placed`() {
        val downloaded = download("app.apk", "main_assets_all.bundle", "data/level.bin")
        val target = File(folder.root, "obb/$pkg")
        assertEquals(setOf("main_assets_all.bundle", "data/level.bin"), place(downloaded, target).toSet())
    }

    @Test
    fun `a placed file is readable by all, for the game that is not its owner`() {
        val downloaded = download("app.apk", "obb/assets/level1.bundle", "obb/main.416.$pkg.obb")
        // What the downloader leaves: a file only its owner and group can read.
        File(downloaded, "obb/assets/level1.bundle").setReadable(false, false)
        File(downloaded, "obb/assets/level1.bundle").setReadable(true, true)
        val target = File(folder.root, "obb/$pkg")
        place(downloaded, target)
        assertTrue(File(target, "assets/level1.bundle").canRead())
        // Readable by others too: checked through the POSIX permissions where the platform has them.
        val permissions = java.nio.file.Files.getPosixFilePermissions(File(target, "assets/level1.bundle").toPath())
        assertTrue(java.nio.file.attribute.PosixFilePermission.OTHERS_READ in permissions)
        assertTrue(java.nio.file.attribute.PosixFilePermission.OTHERS_READ in java.nio.file.Files.getPosixFilePermissions(File(target, "main.416.$pkg.obb").toPath()))
    }

    @Test
    fun `the same file under the old and the new name is kept once, a different one is left alone`() {
        val target = File(folder.root, "obb/$pkg").apply { mkdirs() }
        File(target, "main.110.$pkg.obb").writeText("same")
        File(target, "main.140.$pkg.obb").writeText("same")
        assertEquals(emptyList<String>(), ExpansionFiles.align(target, pkg, 140))
        assertFalse(File(target, "main.110.$pkg.obb").exists())
        File(target, "patch.110.$pkg.obb").writeText("short")
        File(target, "patch.140.$pkg.obb").writeText("a longer one")
        ExpansionFiles.align(target, pkg, 140)
        assertTrue(File(target, "patch.110.$pkg.obb").exists())
    }

    @Test
    fun `files placed before they were opened to all are opened when aligned`() {
        val target = File(folder.root, "obb/$pkg").apply { mkdirs() }
        val file = File(target, "assets/a.bundle").apply { parentFile.mkdirs(); writeText("x"); setReadable(false, false); setReadable(true, true) }
        ExpansionFiles.align(target, pkg, 140)
        assertTrue(java.nio.file.attribute.PosixFilePermission.OTHERS_READ in java.nio.file.Files.getPosixFilePermissions(file.toPath()))
    }
}
