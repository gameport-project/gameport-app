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
    fun `the way to the folder spelled out by the depot is not repeated, and folders below are kept`() {
        val downloaded = download("app.apk", "Android/obb/$pkg/a.bundle", "obb/b.bundle", "aa/c.bundle")
        val target = File(folder.root, "obb/$pkg")
        assertEquals(setOf("a.bundle", "b.bundle", "aa/c.bundle"), place(downloaded, target).toSet())
        assertTrue(File(target, "aa/c.bundle").isFile)
    }

    @Test
    fun `a file already there is replaced`() {
        val target = File(folder.root, "obb/$pkg").apply { mkdirs() }
        File(target, "main_assets_all.bundle").writeText("old")
        place(download("main_assets_all.bundle"), target)
        assertEquals("data of main_assets_all.bundle", File(target, "main_assets_all.bundle").readText())
    }
}
