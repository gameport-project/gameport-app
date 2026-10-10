package app.gameport.core.sync

import android.content.Context
import app.gameport.core.model.CatalogTexts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The texts of the catalog (short descriptions and tag names), one language at a time. As for the catalog, what was read is kept and asked again when
 * it is old; English and French ship with GamePort, so a first start without a network has them. Another language shows nothing until it is read.
 */
@Singleton
class CatalogTextsRepository internal constructor(
    private val folder: File,
    private val snapshot: (String) -> String?,
    private val transport: CatalogTransport,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        folder = context.filesDir,
        snapshot = { code -> runCatching { context.assets.open("android-catalog/text/$code.json").bufferedReader().use { it.readText() } }.getOrNull() },
        transport = HttpCatalogTransport(),
    )

    private val lock = Mutex()
    private val states = ConcurrentHashMap<String, MutableStateFlow<CatalogTexts?>>()
    private val files = ConcurrentHashMap<String, KeptFile>()

    private fun kept(code: String) = files.getOrPut(code) { KeptFile(folder, "catalog-text-$code.json", "v1/text/$code.json", transport, now, REFRESH_MS) }

    private fun state(code: String) = states.getOrPut(code) { MutableStateFlow(CatalogTexts.parse(kept(code).read()) ?: CatalogTexts.parse(snapshot(code))) }

    /** The texts in [code] (a file name of `v1/text/`, such as `fr` or `pt-BR`), or null while there are none. */
    fun observe(code: String): Flow<CatalogTexts?> = state(code).asStateFlow()

    /** Asks for newer texts in [code] when the ones kept are old. */
    suspend fun refreshIfStale(code: String) {
        lock.withLock {
            kept(code).refreshIfStale { CatalogTexts.parse(it)?.lang == code }?.let { state(code).value = CatalogTexts.parse(it) }
        }
    }

    private companion object {
        const val REFRESH_MS = 24 * 60 * 60 * 1000L
    }
}
