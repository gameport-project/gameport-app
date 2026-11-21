package app.gameport.core.patch

import app.gameport.core.patch.patches.PackageRenamePatch
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ArchiveFile
import com.reandroid.archive.io.ZipFileInput
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
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

/** The paths a game wrote down with its package name, in its compiled code, follow the package it runs as ([PackageRenamePatch.patchObbPaths]), tried on small games built in memory. */
class PackageRenameCodeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val came = "com.PineStudio.EscapeSimulator"
    private val renamed = "com.gameport.g1435790"
    private val metadataPath = "assets/bin/Data/Managed/Metadata/global-metadata.dat"

    /** What the metadata of such a game holds: literals one after the other, with the four paths that name the folder of its files. */
    private fun metadata(name: String = came) = (
        "\u0000\u0001Application.dataPath/sdcard/Android/data/sdcard/Android/obb/$name/storage/emulated/0/Android/obb/$name/assets" +
            "subscriptionsAssets/XR/VRRig.prefab/sdcard/Android/obb/$name/storage/emulated/0/Android/obb/$name/assets\u0002"
        ).toByteArray(Charsets.ISO_8859_1)

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

    @Test
    fun `the metadata of an IL2CPP game follows the new package and keeps its size`() {
        val original = metadata()
        withSession(zip(mapOf(metadataPath to original))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            val streamed = session.streamed[metadataPath]
            assertNotNull("the metadata is written again", streamed)
            val written = streamed!!.file.readBytes()
            assertEquals(original.size, written.size)
            assertEquals(original.size.toLong(), streamed.size)
            assertEquals(CRC32().apply { update(written) }.value, streamed.crc)
            val text = String(written, Charsets.ISO_8859_1)
            assertFalse(text.contains(came))
            assertEquals(4, Regex("Android/obb/+" + Regex.escape(renamed)).findAll(text).count())
            assertTrue(text.contains("Application.dataPath"))
        }
    }

    @Test
    fun `putting the game back under its own name restores the metadata byte for byte`() {
        val original = metadata()
        var renamedBytes: ByteArray? = null
        withSession(zip(mapOf(metadataPath to original))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            renamedBytes = session.streamed.getValue(metadataPath).file.readBytes()
        }
        withSession(zip(mapOf(metadataPath to renamedBytes!!))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came, renamed), came)
            assertArrayEquals(original, session.streamed.getValue(metadataPath).file.readBytes())
        }
    }

    @Test
    fun `metadata that holds no such path is left alone`() {
        val bytes = "\u0000\u0001Application.dataPath/sdcard/other\u0002".toByteArray(Charsets.ISO_8859_1)
        withSession(zip(mapOf(metadataPath to bytes))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            assertTrue(session.streamed.isEmpty())
        }
    }

    @Test
    fun `metadata already under the new name is left alone`() {
        withSession(zip(mapOf(metadataPath to metadata(renamed)))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            assertTrue(session.streamed.isEmpty())
        }
    }

    @Test
    fun `the assemblies of a Mono game are rewritten in UTF-16`() {
        val dll = ("\u0003/storage/emulated/0/Android/obb/$came/assets\u0001").toByteArray(Charsets.UTF_16LE)
        withSession(zip(mapOf("assets/bin/Data/Managed/Assembly-CSharp.dll" to dll, "assets/bin/Data/Managed/System.dll" to dll))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            assertEquals("only the game's own scripts", setOf("assets/bin/Data/Managed/Assembly-CSharp.dll"), session.streamed.keys)
            val decoded = String(session.streamed.getValue("assets/bin/Data/Managed/Assembly-CSharp.dll").file.readBytes(), Charsets.UTF_16LE)
            assertFalse(decoded.contains(came))
            assertTrue(decoded.contains(renamed))
        }
    }

    @Test
    fun `a game with no compiled code of that kind is left alone`() {
        withSession(zip(mapOf("classes.dex" to ByteArray(10)))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            assertTrue(session.streamed.isEmpty())
            assertTrue(session.additions.isEmpty())
        }
    }

    @Test
    fun `a text catalog is still followed`() {
        val catalog = """{"m_InternalIds":["/sdcard/Android/obb/$came/a.bundle"]}""".toByteArray()
        withSession(zip(mapOf("assets/aa/catalog.json" to catalog))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            assertTrue(String(session.additions.getValue("assets/aa/catalog.json")).contains("obb/$renamed/a.bundle"))
        }
    }

    @Test
    fun `nothing is kept when nothing was changed`() {
        withSession(zip(mapOf(metadataPath to "nothing to change here".toByteArray()))) { session ->
            PackageRenamePatch.patchObbPaths(session, setOf(came), renamed)
            assertNull(session.streamed[metadataPath])
        }
    }
}
