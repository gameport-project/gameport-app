package app.gameport.core.sync

import android.content.Context
import app.gameport.core.model.PriceHistory
import app.gameport.core.model.PriceIndex
import app.gameport.core.model.PriceNow
import app.gameport.core.model.PricePoint
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
 * The prices, read from the catalog repository in the pieces an app shows: the index, the current prices of one country for the lists, and the history of one game for
 * its page. Each piece is kept on the device and asked again when it is a few hours old; what GamePort ships shows until the first answer, and when none ever comes.
 * A piece that is not of the version this app reads never replaces the one kept.
 */
@Singleton
class PriceRepository internal constructor(
    private val folder: File,
    private val asset: (String) -> String?,
    private val transport: CatalogTransport,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        folder = File(context.filesDir, "prices").also { it.mkdirs() },
        asset = { path -> runCatching { context.assets.open("$ASSETS/$path").bufferedReader().use { it.readText() } }.getOrNull() },
        transport = HttpCatalogTransport(),
    )

    private val lock = Mutex()
    // One KeptFile for each file, so what it remembers (the network gave no answer a moment ago) lasts from one call to the next.
    private val files = ConcurrentHashMap<String, KeptFile>()
    private fun kept(name: String, path: String, maxAgeMs: Long) = files.getOrPut("$name|$path") { KeptFile(folder, name, path, transport, now, maxAgeMs) }

    private val indexFile = kept("index.json", "$REMOTE/index.json", REFRESH_MS)
    private val index = MutableStateFlow(PriceIndex.parse(indexFile.read()) ?: PriceIndex.parse(asset("index.json")))
    private val nows = ConcurrentHashMap<String, MutableStateFlow<PriceNow?>>()

    /** The countries and their currencies, or null if neither a kept copy nor the shipped one can be read. */
    fun observeIndex(): Flow<PriceIndex?> = index.asStateFlow()

    private fun nowFlow(country: String) = nows.getOrPut(country) {
        MutableStateFlow(PriceNow.parse(kept("now-$country.json", "", 0).read()) ?: PriceNow.parse(asset("now/$country.json")))
    }

    /** What every game costs now in [country], or null if neither a kept copy nor the shipped one can be read. */
    fun observeNow(country: String): Flow<PriceNow?> = nowFlow(country).asStateFlow()

    /** Asks again for the index and for the prices of [country] when what is kept is old. Safe to call whenever a screen that shows prices opens. */
    suspend fun refreshIfStale(country: String?) = lock.withLock {
        indexFile.refreshIfStale { PriceIndex.parse(it) != null }?.let { index.value = PriceIndex.parse(it) }
        if (country != null) {
            kept("now-$country.json", "$REMOTE/now/$country.json", REFRESH_MS).refreshIfStale { PriceNow.parse(it) != null }?.let { nowFlow(country).value = PriceNow.parse(it) }
        }
    }

    /** The history of a game as it is on the device now, kept or shipped, with no network: what the page shows at once. Lines by country. */
    fun historyOnDevice(appId: Int): Map<String, List<PricePoint>>? =
        PriceHistory.parse(kept("history-$appId.json", "", 0).read()) ?: PriceHistory.parse(asset("history/$appId.json"))

    /** Asks again for the history of a game when what is kept is old; the new lines when an answer came, null otherwise. */
    suspend fun refreshHistory(appId: Int): Map<String, List<PricePoint>>? =
        kept("history-$appId.json", "$REMOTE/history/$appId.json", HISTORY_REFRESH_MS).refreshIfStale { PriceHistory.parse(it) != null }?.let(PriceHistory::parse)

    private companion object {
        const val ASSETS = "android-catalog/prices"
        const val REMOTE = "v1/prices"
        const val REFRESH_MS = 3 * 60 * 60 * 1000L
        const val HISTORY_REFRESH_MS = 6 * 60 * 60 * 1000L
    }
}
