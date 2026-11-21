package app.gameport.core.patch

import app.gameport.core.patch.json.element
import app.gameport.core.patch.patches.PackageRenamePatch
import app.gameport.core.patch.unity.TestArchive
import app.gameport.core.patch.unity.UnityIdentity
import app.gameport.core.patch.unity.UnityNames
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ArchiveFile
import com.reandroid.archive.io.ZipFileInput
import com.reandroid.json.JSONArray
import com.reandroid.json.JSONObject
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The patch that makes a Unity game's company and product names follow its package ([PackageRenamePatch.followUnityNames]), tried on small games built in memory: an APK is a zip, and
 * this patch only reads and replaces files of it, so a zip with the files it looks at is the game.
 */
class PackageRenameUnityTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val came = "com.PineStudio.EscapeSimulator"
    private val renamed = "com.gameport.g1435790"
    private val archivePath = "assets/bin/Data/data.unity3d"
    private val settingsPath = "assets/bin/Data/globalgamemanagers"
    private val originalNames = UnityNames("Pine Studio", "Escape Simulator")

    private fun settings(company: String = "Pine Studio", product: String = "Escape Simulator") =
        ByteArray(36) { 0x41 } + TestArchive.serialized(company, product) + ByteArray(80) { 0x42 }

    private fun archive(settings: ByteArray = settings()) =
        TestArchive.build(listOf("globalgamemanagers" to settings, "level0" to ByteArray(500) { (it * 7).toByte() }, "zeros" to ByteArray(300)), blockSize = 64)

    private fun manifest(): JSONObject = JSONObject().put(
        "nodes",
        JSONArray().put(JSONObject().put("node_type", "element").put("name", "manifest").put("attributes", JSONArray()).put("nodes", JSONArray().put(element("application")))),
    )

    private fun zip(entries: Map<String, ByteArray>): File = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    private fun <T> withSession(zip: File, block: (PatchSession) -> T): T {
        val archive = ArchiveFile(ZipFileInput(zip))
        val apk = ApkModule("base", archive.createZipEntryMap()).apply { setCloseable(archive) }
        try {
            return block(PatchSession(apk, null, folder.newFolder()))
        } finally {
            apk.close()
        }
    }

    private fun namesIn(archive: ByteArray, packageName: String): UnityNames? {
        val stream = TestArchive.unpack(archive)
        return UnityIdentity.locate(stream) { UnityIdentity.derive(it) == packageName }.firstOrNull()?.names
    }

    private fun streamedBytes(session: PatchSession, path: String): ByteArray? = session.streamed[path]?.file?.readBytes()

    @Test
    fun `a renamed game gets the names of its new package and the old ones are written down`() {
        val manifest = manifest()
        withSession(zip(mapOf(archivePath to archive(), "classes.dex" to ByteArray(10)))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came = came, current = came, target = renamed)
            val written = streamedBytes(session, archivePath)
            assertNotNull("the data archive is written again", written)
            val names = namesIn(written!!, renamed)
            assertNotNull("the names now give the new package", names)
            assertEquals(originalNames.company.length, names!!.company.length)
            assertEquals(originalNames.product.length, names.product.length)
            assertNull("the old names are gone", namesIn(written, came))
            val streamed = session.streamed.getValue(archivePath)
            assertEquals(written.size.toLong(), streamed.size)
            assertEquals(CRC32().apply { update(written) }.value, streamed.crc)
        }
        assertEquals("Pine Studio", PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
        assertEquals("Escape Simulator", PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_PRODUCT))
    }

    @Test
    fun `patching again under the same package changes nothing`() {
        val manifest = manifest()
        var first: ByteArray? = null
        withSession(zip(mapOf(archivePath to archive()))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            first = streamedBytes(session, archivePath)
        }
        withSession(zip(mapOf(archivePath to first!!))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came = came, current = renamed, target = renamed)
            assertTrue("nothing to write", session.streamed.isEmpty())
        }
        assertEquals("Pine Studio", PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `putting the game back under its own package restores its names exactly`() {
        val manifest = manifest()
        val original = archive()
        var first: ByteArray? = null
        withSession(zip(mapOf(archivePath to original))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            first = streamedBytes(session, archivePath)
        }
        withSession(zip(mapOf(archivePath to first!!))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came = came, current = renamed, target = came)
            val restored = streamedBytes(session, archivePath)
            assertNotNull(restored)
            assertArrayEquals("every byte of the data is what it was", TestArchive.unpack(original), TestArchive.unpack(restored!!))
            assertEquals(originalNames, namesIn(restored, came))
        }
    }

    @Test
    fun `moving a renamed game to another package of GamePort keeps the names it came with`() {
        val manifest = manifest()
        var first: ByteArray? = null
        withSession(zip(mapOf(archivePath to archive()))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            first = streamedBytes(session, archivePath)
        }
        withSession(zip(mapOf(archivePath to first!!))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came = came, current = renamed, target = "com.gameport.g42")
            val moved = streamedBytes(session, archivePath)
            assertNotNull(moved)
            assertNotNull(namesIn(moved!!, "com.gameport.g42"))
        }
        assertEquals("Pine Studio", PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `a game that keeps its package is left alone`() {
        val manifest = manifest()
        withSession(zip(mapOf(archivePath to archive()))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, came)
            assertTrue(session.streamed.isEmpty())
        }
        assertNull(PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `a game that keeps its settings in a file of their own is handled`() {
        val manifest = manifest()
        withSession(zip(mapOf(settingsPath to settings()))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            val written = session.additions.getValue(settingsPath)
            assertEquals(settings().size, written.size)
            assertNotNull(UnityIdentity.locate(written) { UnityIdentity.derive(it) == renamed }.firstOrNull())
        }
        assertEquals("Pine Studio", PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `a game with no Unity data is left alone`() {
        val manifest = manifest()
        withSession(zip(mapOf("classes.dex" to ByteArray(10)))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            assertTrue(session.streamed.isEmpty())
            assertTrue(session.additions.isEmpty())
        }
        assertNull(PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `a game whose names do not give its package is left alone`() {
        val manifest = manifest()
        withSession(zip(mapOf(archivePath to archive(settings("Pine Studio", "Another Game"))))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            assertTrue(session.streamed.isEmpty())
        }
        assertNull(PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `a package that does not fit in the names of the game is left alone`() {
        val manifest = manifest()
        withSession(zip(mapOf(archivePath to archive(settings("A", "B"))))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, "com.A.B", "com.A.B", renamed)
            assertTrue(session.streamed.isEmpty())
        }
        assertNull(PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `an archive that cannot be read does not stop the patching and is left alone`() {
        val manifest = manifest()
        withSession(zip(mapOf(archivePath to ByteArray(2000) { (it * 31).toByte() }))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            assertTrue(session.streamed.isEmpty())
            assertTrue(session.additions.isEmpty())
        }
        assertNull(PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `an archive of a version that is not handled is left alone`() {
        val manifest = manifest()
        val bytes = TestArchive.build(listOf("globalgamemanagers" to settings()), version = 6)
        withSession(zip(mapOf(archivePath to bytes))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            assertTrue(session.streamed.isEmpty())
        }
        assertNull(PackageRenamePatch.recordedMeta(manifest, PackageRenamePatch.META_COMPANY))
    }

    @Test
    fun `the temporary files are deleted when the work is done`() {
        val manifest = manifest()
        withSession(zip(mapOf(archivePath to archive()))) { session ->
            PackageRenamePatch.followUnityNames(session, manifest, came, came, renamed)
            val file = session.streamed.getValue(archivePath).file
            assertTrue(file.exists())
            session.cleanUp()
            assertFalse(file.exists())
        }
    }

    @Test
    fun `a value written in the manifest replaces the one that was there`() {
        val manifest = manifest()
        PackageRenamePatch.putMeta(manifest, "app.gameport.test", "one")
        PackageRenamePatch.putMeta(manifest, "app.gameport.test", "two")
        PackageRenamePatch.putMeta(manifest, "app.gameport.other", "three")
        assertEquals("two", PackageRenamePatch.recordedMeta(manifest, "app.gameport.test"))
        assertEquals("three", PackageRenamePatch.recordedMeta(manifest, "app.gameport.other"))
        assertNull(PackageRenamePatch.recordedMeta(manifest, "app.gameport.missing"))
        val application = manifest.optJSONArray("nodes").getJSONObject(0).optJSONArray("nodes").getJSONObject(0)
        assertEquals(2, application.optJSONArray("nodes").length())
    }

    @Test
    fun `an entry written from a file is stored as it is next to the entries copied`() {
        val kept = ByteArray(100) { 7 }
        val input = zip(mapOf("kept.bin" to kept, "replaced.bin" to ByteArray(50) { 1 }))
        val content = ByteArray(5000) { (it % 200).toByte() }
        val source = folder.newFile().also { it.writeBytes(content) }
        val output = folder.newFile()
        ZipRewriter.rewrite(
            input = input,
            output = output,
            replaced = emptyMap(),
            stored = emptyMap(),
            dropIf = { false },
            streamed = mapOf("replaced.bin" to PatchSession.StreamedFile(source, content.size.toLong(), CRC32().apply { update(content) }.value)),
        )
        ZipFile(output).use { zip ->
            assertEquals(2, zip.size())
            assertArrayEquals(kept, zip.getInputStream(zip.getEntry("kept.bin")).readBytes())
            val entry = zip.getEntry("replaced.bin")
            assertEquals(ZipEntry.STORED, entry.method)
            assertEquals(CRC32().apply { update(content) }.value, entry.crc)
            assertArrayEquals(content, zip.getInputStream(entry).readBytes())
        }
    }
}
