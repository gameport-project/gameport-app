package app.gameport.core.patch

import app.gameport.core.patch.patches.LibsDownloader
import app.gameport.core.patch.patches.OvrportLibsSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OvrportLibsSourceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val loader = ByteArray(40) { 7 }
    private val openXr = ByteArray(40) { 9 }

    private val zipBytes: ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libovrplatformloader.so")); zip.write(loader); zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libopenxr_loader.so")); zip.write(openXr); zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/armeabi-v7a/libovrplatformloader.so")); zip.write(ByteArray(8) { 1 }); zip.closeEntry()
        }
    }.toByteArray()

    private val zipSha = sha256(zipBytes)
    private val url = "https://example.test/libraries.zip"

    private class FakeDownloader(private val bytes: ByteArray?) : LibsDownloader {
        var calls = 0

        override fun download(url: String, target: File) {
            calls++
            if (bytes == null) throw IOException("offline")
            target.writeBytes(bytes)
        }
    }

    @Test
    fun downloadsVerifiesAndReadsAllowedLibraries() {
        val downloader = FakeDownloader(zipBytes)
        val source = OvrportLibsSource(temp.newFolder("cache"), zipSha, url, downloader)

        assertTrue(source.prepare())
        assertArrayEquals(loader, source.getLibrary("libovrplatformloader.so"))
        assertEquals(1, downloader.calls)
    }

    @Test
    fun neverHandsOutOtherFilesOrOtherArchitectures() {
        val source = OvrportLibsSource(temp.newFolder("cache"), zipSha, url, FakeDownloader(zipBytes))
        assertTrue(source.prepare())

        // In the archive, but not one of the four loaders.
        assertNull(source.getLibrary("libopenxr_loader.so"))
        // One of the four, but absent from the archive.
        assertNull(source.getLibrary("libpxrplatformloader.so"))
    }

    @Test
    fun usesTheCacheTheSecondTime() {
        val cache = temp.newFolder("cache")
        assertTrue(OvrportLibsSource(cache, zipSha, url, FakeDownloader(zipBytes)).prepare())

        val second = FakeDownloader(zipBytes)
        val source = OvrportLibsSource(cache, zipSha, url, second)
        assertTrue(source.prepare())
        assertEquals(0, second.calls)
        assertArrayEquals(loader, source.getLibrary("libovrplatformloader.so"))
    }

    @Test
    fun refusesAnArchiveWithTheWrongHashAndLeavesNothingBehind() {
        val cache = temp.newFolder("cache")
        val source = OvrportLibsSource(cache, "0".repeat(64), url, FakeDownloader(zipBytes))

        assertFalse(source.prepare())
        assertNull(source.getLibrary("libovrplatformloader.so"))
        assertTrue(cache.listFiles()!!.isEmpty())
    }

    @Test
    fun downloadsAgainWhenTheCachedArchiveIsCorrupted() {
        val cache = temp.newFolder("cache")
        assertTrue(OvrportLibsSource(cache, zipSha, url, FakeDownloader(zipBytes)).prepare())
        cache.listFiles()!!.single().writeBytes(ByteArray(10))

        val downloader = FakeDownloader(zipBytes)
        val source = OvrportLibsSource(cache, zipSha, url, downloader)
        assertTrue(source.prepare())
        assertEquals(1, downloader.calls)
        assertArrayEquals(loader, source.getLibrary("libovrplatformloader.so"))
    }

    @Test
    fun failsQuietlyWhenOffline() {
        val source = OvrportLibsSource(temp.newFolder("cache"), zipSha, url, FakeDownloader(null))

        assertFalse(source.prepare())
        assertNull(source.getLibrary("libovrplatformloader.so"))
    }

    @Test
    fun getLibraryNeverUsesTheNetwork() {
        val downloader = FakeDownloader(zipBytes)
        val source = OvrportLibsSource(temp.newFolder("cache"), zipSha, url, downloader)

        assertNull(source.getLibrary("libovrplatformloader.so"))
        assertEquals(0, downloader.calls)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
