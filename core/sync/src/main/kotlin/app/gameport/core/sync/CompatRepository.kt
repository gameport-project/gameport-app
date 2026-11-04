package app.gameport.core.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import app.gameport.core.model.Compat
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.CompatReports
import app.gameport.core.model.CompatRules
import app.gameport.core.model.CompatSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads what the relay publishes: the text of its answer, or null when it did not answer clearly. */
fun interface SummaryTransport {
    suspend fun get(): String?
}

class HttpSummaryTransport(private val address: String) : SummaryTransport {
    override suspend fun get(): String? = withContext(Dispatchers.IO) {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** The real totals for a release build, the test ones (their own table in the relay) for a debug build. */
        fun addressFor(debuggable: Boolean) = if (debuggable) "$RELAY/test/summary" else "$RELAY/summary"

        internal const val RELAY = "https://gameport-relay.gameport.workers.dev"
    }
}

/** Reads what the relay lists for one game (its reports, by model and version of GamePort): the text of its answer, or null when it did not answer clearly. */
fun interface ReportsTransport {
    suspend fun get(appId: Int): String?
}

class HttpReportsTransport(private val address: String) : ReportsTransport {
    override suspend fun get(appId: Int): String? = withContext(Dispatchers.IO) {
        val connection = URL("$address/$appId").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000

        /** The real votes for a release build, the test ones for a debug build. */
        fun addressFor(debuggable: Boolean) = if (debuggable) "${HttpSummaryTransport.RELAY}/test/reports" else "${HttpSummaryTransport.RELAY}/reports"
    }
}

/**
 * What the players say about each game, read from the relay. The last answer is kept on this device, so the pages of the games show it without
 * a connection. It is asked again when it is older than a few minutes; an answer that cannot be read never replaces the one kept.
 */
@Singleton
class CompatRepository @Inject constructor(@ApplicationContext private val context: Context, private val device: app.gameport.core.device.DeviceProfile, private val incompatible: app.gameport.core.settings.IncompatibleGames) {
    private val file = File(context.cacheDir, "compat-summary.json")
    private val lock = Mutex()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Development only: a debug build shows the totals and the reports found in its files folder, in `debug-compat.json` (the relay's `/summary`) and
     * `debug-compat-reports.json` (its `/reports/<appId>`), instead of asking the relay, so the details of a game can be looked at with made-up values on the emulator
     * (`adb -s emulator-5554 shell run-as app.gameport.dev sh -c "cat > files/debug-compat.json"`). A release build never reads them.
     */
    private fun debugFile(name: String): String? = if (debuggable) runCatching { File(context.filesDir, name).readText() }.getOrNull() else null

    private val debugSummary = CompatSummary.parse(debugFile("debug-compat.json"))
    private val summary = MutableStateFlow(debugSummary ?: CompatSummary.parse(runCatching { file.readText() }.getOrNull()))
    internal var transport: SummaryTransport = HttpSummaryTransport(HttpSummaryTransport.addressFor(debuggable))
    internal var reportsTransport: ReportsTransport = HttpReportsTransport(HttpReportsTransport.addressFor(debuggable))
    private val reportsKept = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, CompatReports>>()
    internal var now: () -> Long = System::currentTimeMillis

    // A test build asks again after seconds, so what was just sent can be seen; a release build after minutes, to spare the relay.
    private val refreshMs = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) DEBUG_REFRESH_MS else REFRESH_MS

    /** The verdict of the players for this device: its exact model when two or more of them answered, else its kind of device, else every device; null while nobody answered. */
    fun observe(appId: Int): Flow<Compat?> = summary.map { CompatRules.ofDevice(it?.of(appId), device.kind, device.model) }

    /** What the players of every kind of device said about one game, for its details; null while nothing about it was read. */
    fun observeCounts(appId: Int): Flow<app.gameport.core.model.CompatCounts?> = summary.map { it?.of(appId) }

    /** Everything the relay publishes, for the page that lists the games and their numbers; null until something was read. */
    fun observeSummary(): Flow<CompatSummary?> = summary

    /** What the players who use the same kind of device say of each game they said enough about: for the covers of the home. */
    fun observeLevels(): Flow<Map<Int, CompatLevel>> = combine(summary, incompatible.lists) { all, confirmed ->
        // A game confirmed as incompatible is shown as one that fails, whatever the players said.
        all?.games.orEmpty().mapNotNull { counts -> CompatRules.of(counts, device.kind)?.let { counts.appId to it.level } }.toMap() + confirmed.games.associate { it.appId to CompatLevel.FAILS }
    }

    /**
     * What the relay lists for one game, line by line (model, answer, version of GamePort), for its details; asked when they are opened and kept for a few minutes.
     * Null when the relay cannot be reached and nothing was read before: the totals of the models show meanwhile.
     */
    suspend fun reports(appId: Int): app.gameport.core.model.CompatReports? {
        CompatReports.parse(debugFile("debug-compat-reports.json"))?.let { return it }
        val kept = reportsKept[appId]
        if (kept != null && now() - kept.first < refreshMs) return kept.second
        val read = CompatReports.parse(reportsTransport.get(appId)) ?: return kept?.second
        reportsKept[appId] = now() to read
        return read
    }

    /** Reads the totals when GamePort starts, and asks again when it is opened later and what is kept is old. */
    fun start() {
        scope.launch { refreshIfStale() }
    }

    /** Asks the relay again when what is kept is old. Safe to call whenever a page opens. */
    suspend fun refreshIfStale() {
        incompatible.refreshIfStale()
        // The made-up totals of a debug build stay as they are.
        if (debugSummary != null) return
        lock.withLock {
            if (now() - file.lastModified() < refreshMs && summary.value != null) return
            val text = transport.get() ?: return
            val read = CompatSummary.parse(text) ?: return
            runCatching { file.writeText(text) }
            summary.value = read
        }
    }

    private companion object {
        const val REFRESH_MS = 10 * 60 * 1000L
        const val DEBUG_REFRESH_MS = 20 * 1000L
    }
}
