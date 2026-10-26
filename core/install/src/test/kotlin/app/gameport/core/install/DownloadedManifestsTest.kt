package app.gameport.core.install

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadedManifestsTest {
    @get:Rule val folder = TemporaryFolder()

    private fun download(vararg names: String): File = folder.newFolder("download").also { root ->
        names.forEach { File(root, it).apply { parentFile?.mkdirs(); writeText("x") } }
    }

    @Test
    fun `the manifest of each depot is the one of the file the downloader kept`() {
        val root = download(".DepotDownloader/5065904_8208389039347839146.manifest", ".DepotDownloader/5065904_8208389039347839146.manifest.sha", ".DepotDownloader/depot.config", "app.apk")
        assertEquals(mapOf(5065904 to 8208389039347839146L), DownloadedManifests.read(root))
    }

    @Test
    fun `several depots each have their own`() {
        val root = download(".DepotDownloader/10_111.manifest", ".DepotDownloader/20_222.manifest")
        assertEquals(mapOf(10 to 111L, 20 to 222L), DownloadedManifests.read(root))
    }

    @Test
    fun `a download without the downloader's folder, or with names that are not manifests, has none`() {
        assertEquals(emptyMap<Int, Long>(), DownloadedManifests.read(download("app.apk")))
        assertEquals(emptyMap<Int, Long>(), DownloadedManifests.read(folder.newFolder("other").also { File(it, ".DepotDownloader").mkdirs(); File(it, ".DepotDownloader/notes.manifest").writeText("x") }))
    }
}
