package app.gameport.core.patch

import app.gameport.core.patch.patches.PlatformLibsPatch
import app.gameport.core.patch.patches.PlatformLibsSource
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlatformLibsPatchTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val libDir = "lib/arm64-v8a/"
    private val gameLoader = ByteArray(32) { 1 }
    private val gameOpenXr = ByteArray(48) { 5 }

    private val libs = mapOf(
        "libovrplatformloader.so" to ByteArray(64) { 0x10 },
        "libovrplatformloader_meta.so" to ByteArray(64) { 0x20 },
        "libovrplatformloader_meta_q1.so" to ByteArray(64) { 0x30 },
        "libpxrplatformloader.so" to ByteArray(64) { 0x40 },
    )

    private val fakeSource = PlatformLibsSource { name -> libs[name] }

    @Test
    fun injectsTheFourLoadersAndReplacesTheGamesOwn() {
        val input = createApk(withLoader = true)
        val output = patch(input, PlatformLibsPatch(fakeSource))

        ZipFile(output).use { zip ->
            libs.forEach { (name, bytes) ->
                assertArrayEquals(name, bytes, zip.bytes(libDir + name))
                assertEquals(name, ZipEntry.STORED, zip.getEntry(libDir + name).method)
            }
            // The game's own OpenXR loader is not ours to touch.
            assertArrayEquals(gameOpenXr, zip.bytes(libDir + "libopenxr_loader.so"))
        }
    }

    @Test
    fun doesNothingWhenTheGameHasNoPlatformLoader() {
        val input = createApk(withLoader = false)
        val output = patch(input, PlatformLibsPatch(fakeSource))

        ZipFile(output).use { zip ->
            libs.keys.forEach { name -> assertNull(name, zip.getEntry(libDir + name)) }
            assertArrayEquals(gameOpenXr, zip.bytes(libDir + "libopenxr_loader.so"))
        }
    }

    @Test
    fun doesNothingWhenTheSourceHasNoLibraries() {
        val input = createApk(withLoader = true)
        val output = patch(input, PlatformLibsPatch())

        ZipFile(output).use { zip ->
            // The game keeps its own loader, untouched.
            assertArrayEquals(gameLoader, zip.bytes(libDir + "libovrplatformloader.so"))
            listOf("libovrplatformloader_meta.so", "libovrplatformloader_meta_q1.so", "libpxrplatformloader.so")
                .forEach { name -> assertNull(name, zip.getEntry(libDir + name)) }
        }
    }

    @Test
    fun hasNoDuplicateEntriesAndAlignsTheLibraries() {
        val input = createApk(withLoader = true)
        val output = patch(input, PlatformLibsPatch(fakeSource))

        val names = ZipFile(output).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        assertEquals("duplicate entries: $names", names.size, names.distinct().size)

        org.apache.commons.compress.archivers.zip.ZipFile.builder().setFile(output).get().use { zip ->
            libs.keys.forEach { name ->
                val entry = zip.getEntry(libDir + name)
                assertNotNull(name, entry)
                assertEquals(name, 0L, entry.dataOffset % 16384)
            }
        }
    }

    private fun patch(input: File, patch: PlatformLibsPatch): File {
        val output = temp.newFile()
        ApkPatchEngine(SigningKeyStore(temp.newFolder().resolve("key.p12"))).patch(
            input = input,
            output = output,
            patches = listOf(patch),
            context = PatchContext(steamAppId = 1, steamId = 2, personaName = "test"),
            assets = PatchAssets(
                BinarySource { ByteArray(1).inputStream() },
                BinarySource { ByteArray(1).inputStream() },
                BinarySource { ByteArray(1).inputStream() },
            ),
        )
        return output
    }

    private fun createApk(withLoader: Boolean): File {
        val file = temp.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest/>".toByteArray())
            zip.closeEntry()
            if (withLoader) {
                zip.putNextEntry(ZipEntry(libDir + "libovrplatformloader.so"))
                zip.write(gameLoader)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(libDir + "libopenxr_loader.so"))
            zip.write(gameOpenXr)
            zip.closeEntry()
        }
        return file
    }

    private fun ZipFile.bytes(name: String): ByteArray? =
        getEntry(name)?.let { entry -> getInputStream(entry).use { it.readBytes() } }
}
