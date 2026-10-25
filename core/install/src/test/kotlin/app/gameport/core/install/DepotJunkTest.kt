package app.gameport.core.install

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DepotJunkTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `the companion file macOS writes beside a file is junk, the file itself is not`() {
        assertTrue(DepotJunk.isJunk("._Archery.Pro.apk"))
        assertTrue(DepotJunk.isJunk("obb/._main.5.com.example.obb"))
        assertFalse(DepotJunk.isJunk("Archery.Pro.apk"))
        assertFalse(DepotJunk.isJunk("obb/main.5.com.example.obb"))
    }

    @Test
    fun `a folder of a zip made on a Mac and the folder view file are junk`() {
        assertTrue(DepotJunk.isJunk("__MACOSX/data/._level.bundle"))
        assertTrue(DepotJunk.isJunk("data/.DS_Store"))
        assertFalse(DepotJunk.isJunk("data/level.bundle"))
        assertFalse(DepotJunk.isJunk("my._file.apk"))
    }

    @Test
    fun `junk is not placed on the device with the expansion files`() {
        val pkg = "com.example.game"
        val root = folder.newFolder("download")
        listOf("Game.apk", "._Game.apk", "main.5.$pkg.obb", "._main.5.$pkg.obb", "__MACOSX/x.bundle").forEach { File(root, it).apply { parentFile?.mkdirs(); writeText("x") } }
        val placed = ExpansionFiles.select(root, pkg, skipped = "patched").map { it.second }
        assertEquals(listOf("main.5.$pkg.obb"), placed.filter { it.endsWith(".obb") })
        assertFalse(placed.any { it.contains("._") || it.contains("__MACOSX") })
    }
}
