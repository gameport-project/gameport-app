package app.gameport.core.patch

import app.gameport.core.patch.patches.XrLoaderPatch
import com.reandroid.apk.ApkModule
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XrLoaderPatchTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a loader is recent when it holds the marker`() {
        assertTrue(XrLoaderPatch.isRecent("abc LoaderInitData not initialized xyz".toByteArray()))
        assertFalse(XrLoaderPatch.isRecent(ByteArray(4096) { 7 }))
        assertFalse(XrLoaderPatch.isRecent(ByteArray(0)))
    }

    /** Runs the real engine on an APK you provide: GAMEPORT_TEST_APK=/path/to/game.apk, and GAMEPORT_TEST_LOADER=/path/to/recent/libopenxr_loader.so. */
    @Test
    fun replacesOnlyAnOldLoader() {
        val source = System.getenv("GAMEPORT_TEST_APK")?.let(::File)
        val loader = System.getenv("GAMEPORT_TEST_LOADER")?.let(::File)
        assumeTrue("set GAMEPORT_TEST_APK and GAMEPORT_TEST_LOADER to run", source?.isFile == true && loader?.isFile == true)
        val output = temp.newFile()
        ApkPatchEngine(SigningKeyStore(temp.newFolder().resolve("key.p12"))).patch(
            input = source!!,
            output = output,
            patches = listOf(XrLoaderPatch),
            context = PatchContext(steamAppId = 1, steamId = 2, personaName = "test"),
            assets = PatchAssets(BinarySource { ByteArray(1).inputStream() }, BinarySource { ByteArray(1).inputStream() }, xrLoader = BinarySource { loader!!.inputStream() }),
        )
        val path = XrLoaderPatch.LIBRARY_PATH
        val before = ZipFile(source).use { zip -> zip.getEntry(path)?.let { zip.getInputStream(it).readBytes() } }
        val after = ZipFile(output).use { zip -> zip.getEntry(path)?.let { zip.getInputStream(it).readBytes() } }
        println("ships a loader: ${before != null}, recent: ${before?.let(XrLoaderPatch::isRecent)}")
        when {
            before == null -> assertEquals(null, after)
            XrLoaderPatch.isRecent(before) -> assertArrayEquals(before, after)
            else -> assertArrayEquals(loader!!.readBytes(), after)
        }
        // Everything else in the APK is where it was, and the manifest is the same.
        assertEquals(ApkModule.loadApkFile(source).androidManifest.toJson().toString(), ApkModule.loadApkFile(output).androidManifest.toJson().toString())
    }
}
