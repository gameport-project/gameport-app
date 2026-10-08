package app.gameport.core.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import app.gameport.core.model.Compat
import app.gameport.core.model.CompatLevel
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

        private const val RELAY = "https://gameport-relay.gameport.workers.dev"
    }
}

/**
 * What the players say about each game, read from the relay. The last answer is kept on this device, so the pages of the games show it without
 * a connection. It is asked again when it is older than a few minutes; an answer that cannot be read never replaces the one kept.
 */
@Singleton
class CompatRepository @Inject constructor(@ApplicationContext private val context: Context, private val device: app.gameport.core.device.DeviceProfile) {
    private val file = File(context.cacheDir, "compat-summary.json")
    private val lock = Mutex()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val summary = MutableStateFlow(CompatSummary.parse(runCatching { file.readText() }.getOrNull()))
    internal var transport: SummaryTransport = HttpSummaryTransport(HttpSummaryTransport.addressFor(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0))
    internal var now: () -> Long = System::currentTimeMillis

    // A test build asks again after seconds, so what was just sent can be seen; a release build after minutes, to spare the relay.
    private val refreshMs = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) DEBUG_REFRESH_MS else REFRESH_MS

    /** The verdict of the players who use the same kind of device as this one, or null while there are too few answers (or none were ever read). */
    fun observe(appId: Int): Flow<Compat?> = summary.map { CompatRules.of(it?.of(appId), device.kind) }

    /** What the players who use the same kind of device say of each game they said enough about: for the covers of the home. */
    fun observeLevels(): Flow<Map<Int, CompatLevel>> = summary.map { all ->
        all?.games.orEmpty().mapNotNull { counts -> CompatRules.of(counts, device.kind)?.let { counts.appId to it.level } }.toMap()
    }

    /** Reads the totals when GamePort starts, and asks again when it is opened later and what is kept is old. */
    fun start() {
        scope.launch { refreshIfStale() }
    }

    /** Asks the relay again when what is kept is old. Safe to call whenever a page opens. */
    suspend fun refreshIfStale() {
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
