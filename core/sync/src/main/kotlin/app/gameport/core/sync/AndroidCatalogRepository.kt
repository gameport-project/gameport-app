package app.gameport.core.sync

import android.content.Context
import app.gameport.core.model.AndroidCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the place the catalog is published at answers to a request for one of its files. */
sealed interface CatalogAnswer {
    /** The file is the one already held (the answer to a conditional request). */
    data object NotModified : CatalogAnswer

    /** The file, with the tag that names this version of it. */
    data class Body(val text: String, val etag: String?) : CatalogAnswer

    /** No answer worth keeping: no network, an error, a missing file. */
    data object Unavailable : CatalogAnswer
}

fun interface CatalogTransport {
    /** Asks for [path], and for the file only if it is no longer the version named by [etag]. */
    suspend fun get(path: String, etag: String?): CatalogAnswer
}

class HttpCatalogTransport(private val base: String = BASE) : CatalogTransport {
    override suspend fun get(path: String, etag: String?): CatalogAnswer = withContext(Dispatchers.IO) {
        val connection = URL("$base/$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (etag != null) connection.setRequestProperty("If-None-Match", etag)
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> CatalogAnswer.Body(connection.inputStream.bufferedReader().use { it.readText() }, connection.getHeaderField("ETag"))
                HttpURLConnection.HTTP_NOT_MODIFIED -> CatalogAnswer.NotModified
                else -> CatalogAnswer.Unavailable
            }
        } catch (e: java.io.IOException) {
            CatalogAnswer.Unavailable
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** Where the catalog repository publishes its files. The only place that names it: changing where they are served from is a change of this line. */
        const val BASE = "https://raw.githubusercontent.com/gameport-project/gameport-catalog/main"
    }
}

/**
 * A file of the catalog repository kept on the device: asked again when it is old, with a conditional request so a file that did not change costs
 * next to nothing, and replaced only by an answer that [accepts] says is what it should be.
 */
internal class KeptFile(
    private val folder: File,
    private val name: String,
    private val path: String,
    private val transport: CatalogTransport,
    private val now: () -> Long,
    private val maxAgeMs: Long,
) {
    private val file = File(folder, name)
    private val etagFile = File(folder, "$name.etag")

    fun read(): String? = runCatching { file.readText() }.getOrNull()

    /** The text of the new file when an answer replaced the one kept; null when nothing changed (still fresh, not modified, no answer, or an answer refused). */
    suspend fun refreshIfStale(accepts: (String) -> Boolean): String? {
        if (file.exists() && now() - file.lastModified() < maxAgeMs) return null
        when (val answer = transport.get(path, etag = etagFile.takeIf { file.exists() && it.exists() }?.readText())) {
            CatalogAnswer.NotModified -> file.setLastModified(now())
            CatalogAnswer.Unavailable -> Unit
            is CatalogAnswer.Body -> {
                if (!accepts(answer.text)) return null
                val temporary = File(folder, "$name.tmp")
                runCatching {
                    temporary.writeText(answer.text)
                    temporary.renameTo(file)
                    file.setLastModified(now())
                    if (answer.etag != null) etagFile.writeText(answer.etag) else etagFile.delete()
                }
                return answer.text
            }
        }
        return null
    }
}

/**
 * The Steam apps that have an Android build, read from the catalog repository. What was last read is kept on the device and asked again when it is a few
 * hours old. The catalog shipped with GamePort is what shows until the first answer, and when none ever comes. An answer that is not a catalog never
 * replaces the one kept.
 */
@Singleton
class AndroidCatalogRepository internal constructor(
    folder: File,
    private val snapshot: () -> String?,
    transport: CatalogTransport,
    now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        folder = context.filesDir,
        snapshot = { runCatching { context.assets.open(SNAPSHOT).bufferedReader().use { it.readText() } }.getOrNull() },
        transport = HttpCatalogTransport(),
    )

    private val kept = KeptFile(folder, "android-catalog.json", "v1/catalog.json", transport, now, REFRESH_MS)
    private val lock = Mutex()
    private val catalog = MutableStateFlow(AndroidCatalog.parse(kept.read()) ?: AndroidCatalog.parse(snapshot()))

    /** The catalog, or null if neither a kept copy nor the shipped one can be read. */
    fun observe(): Flow<AndroidCatalog?> = catalog.asStateFlow()

    val current: AndroidCatalog? get() = catalog.value

    /** Asks for a newer file when the one kept is old. Safe to call whenever a screen that needs the catalog opens. */
    suspend fun refreshIfStale() {
        lock.withLock {
            kept.refreshIfStale { AndroidCatalog.parse(it) != null }?.let { catalog.value = AndroidCatalog.parse(it) }
        }
    }

    private companion object {
        const val SNAPSHOT = "android-catalog/catalog.json"
        const val REFRESH_MS = 3 * 60 * 60 * 1000L
    }
}
