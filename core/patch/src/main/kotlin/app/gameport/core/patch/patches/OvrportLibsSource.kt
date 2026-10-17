package app.gameport.core.patch.patches

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

const val OVRPORT_LIBS_URL = "https://files.crx.moe/ovrport/tracker/releases/download/3.3.0/libraries.zip"
const val OVRPORT_LIBS_SHA256 = "6145e1c7dfbb2733a353b402f38874f453df1ac38c9cdedc0d6b4762f99e16c4"

/** Fetches [url] into [target]. */
fun interface LibsDownloader {
    fun download(url: String, target: File)
}

/**
 * Platform libraries from OVRPort's `libraries.zip`, pinned to one release: the archive is
 * downloaded once, checked against [expectedSha256], and only then used. A file that does not
 * match is deleted and ignored, so a file changed on the server never ends up in a game.
 *
 * [prepare] may use the network, so call it off the main thread. [getLibrary] only reads the cache.
 */
class OvrportLibsSource(
    private val cacheDir: File,
    private val expectedSha256: String = OVRPORT_LIBS_SHA256,
    private val url: String = OVRPORT_LIBS_URL,
    private val downloader: LibsDownloader = HttpsDownloader,
) : PlatformLibsSource {
    private val archive get() = File(cacheDir, ARCHIVE_NAME)
    private var verified = false

    /** True when a verified archive is in the cache, downloading it first if needed. */
    fun prepare(): Boolean {
        if (ensureVerified()) return true
        archive.delete()
        cacheDir.mkdirs()
        val partial = File(cacheDir, "$ARCHIVE_NAME.part")
        return try {
            partial.delete()
            downloader.download(url, partial)
            verified = hasExpectedHash(partial) && partial.renameTo(archive)
            verified
        } catch (e: IOException) {
            false
        } finally {
            partial.delete()
        }
    }

    override fun getLibrary(name: String): ByteArray? {
        if (name !in ALLOWED || !ensureVerified()) return null
        return try {
            ZipFile(archive).use { zip ->
                zip.getEntry("lib/arm64-v8a/$name")?.let { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun ensureVerified(): Boolean {
        if (!(verified && archive.isFile)) verified = hasExpectedHash(archive)
        return verified
    }

    private fun hasExpectedHash(file: File): Boolean =
        file.isFile && sha256(file).equals(expectedSha256, ignoreCase = true)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ARCHIVE_NAME = "ovrport-libraries-3.3.0.zip"

        // Same four files as PlatformLibsPatch: nothing else in the archive is ever handed out.
        val ALLOWED = setOf(
            "libovrplatformloader.so",
            "libovrplatformloader_meta.so",
            "libovrplatformloader_meta_q1.so",
            "libpxrplatformloader.so",
        )
    }
}

/** Plain HTTPS download with timeouts and a size cap; the hash check happens afterwards. */
object HttpsDownloader : LibsDownloader {
    private const val MAX_BYTES = 200L * 1024 * 1024

    override fun download(url: String, target: File) {
        if (!url.startsWith("https://")) throw IOException("only https is allowed")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > MAX_BYTES) throw IOException("archive too large")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }
}
