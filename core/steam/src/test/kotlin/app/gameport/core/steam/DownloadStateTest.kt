package app.gameport.core.steam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadStateTest {
    @get:Rule val folder = TemporaryFolder()

    private fun directory(vararg files: String): File = folder.newFolder("game").also { root ->
        files.forEach { File(root, it).apply { parentFile?.mkdirs(); writeText("x") } }
    }

    @Test
    fun `a folder with files and a memory of the downloader forgets that memory`() {
        val root = directory("data/level.bundle", "${DownloadState.FOLDER}/depot.config", "${DownloadState.FOLDER}/1_2.manifest")
        assertTrue(DownloadState.forgetForResume(root))
        assertFalse(File(root, DownloadState.FOLDER).exists())
        // The files themselves stay: the downloader checks them, and keeps the ones that are right.
        assertTrue(File(root, "data/level.bundle").exists())
    }

    @Test
    fun `a new folder has nothing to forget`() {
        val root = folder.newFolder("new")
        assertFalse(DownloadState.forgetForResume(root))
    }

    @Test
    fun `a folder whose only content is the memory has nothing to check, so it is left as it is`() {
        val root = directory("${DownloadState.FOLDER}/depot.config")
        assertFalse(DownloadState.forgetForResume(root))
        assertEquals(true, File(root, "${DownloadState.FOLDER}/depot.config").exists())
    }
}
