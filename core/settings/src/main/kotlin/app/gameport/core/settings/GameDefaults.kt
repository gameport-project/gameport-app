package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.GameDefaultsList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What the developer tells the app about the games from the relay's console (for now: the games that declare the headset's passthrough, see [GameDefaultsList]). The last list
 * read is kept on this device and asked again when it is old, at most once a day, and when a game is about to be patched ([FOR_A_PATCH_MS]), which is when it matters. Until
 * one was read (the very first start without a network) the list is empty, and a list that cannot be read never replaces the one in use.
 */
@Singleton
class GameDefaults @Inject constructor(@ApplicationContext private val context: Context) {
    private val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val kept = File(context.cacheDir, "game-defaults-relay.json")
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var fetch: suspend () -> String? = { download(if (debuggable) "$RELAY/test/game-defaults" else "$RELAY/game-defaults") }
    internal var now: () -> Long = System::currentTimeMillis

    private val _list = MutableStateFlow(runCatching { kept.readText() }.getOrNull()?.let(GameDefaultsList::fromRelay) ?: GameDefaultsList())

    /** The list in use, and what it becomes when the relay says otherwise. */
    val lists: StateFlow<GameDefaultsList> = _list.asStateFlow()

    /** The list in use now. */
    val list: GameDefaultsList get() = _list.value

    /** Reads the list when GamePort starts, if what is kept is old. */
    fun start() {
        scope.launch { refreshIfStale() }
    }

    /** Asks the relay again when what is kept is older than [maxAgeMs] (or was never read). Safe to call whenever; one reading at a time. */
    suspend fun refreshIfStale(maxAgeMs: Long = if (debuggable) DEBUG_REFRESH_MS else REFRESH_MS) {
        lock.withLock {
            if (kept.exists() && now() - kept.lastModified() < maxAgeMs) return
            val text = fetch() ?: return
            val read = GameDefaultsList.fromRelay(text) ?: return
            runCatching { kept.writeText(text) }
            _list.value = read
        }
    }

    private suspend fun download(address: String): String? = withContext(Dispatchers.IO) {
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
        /** A game about to be patched asks for a list no older than this, so what was just added in the console is used. */
        const val FOR_A_PATCH_MS = 10 * 60 * 1000L

        private const val RELAY = "https://gameport-relay.gameport.workers.dev"
        private const val TIMEOUT_MS = 10_000
        private const val REFRESH_MS = 24 * 60 * 60 * 1000L
        private const val DEBUG_REFRESH_MS = 20 * 1000L
    }
}
