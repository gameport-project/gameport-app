package app.gameport.core.settings

import android.content.Context
import app.gameport.core.model.IncompatibleList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The games confirmed as incompatible. They are left out of the home unless the player chose to show them anyway, and the player is told once
 * for each of them that is in the library: [acknowledged] keeps the games already told.
 *
 * The list is kept by the developer in the relay, so a game made compatible there is so for everyone at once: the last list read is kept on this device
 * and asked again when it is old. Until one was read (the very first start without a network) the list is empty, so nothing is hidden by mistake; a list that
 * cannot be read never replaces the one in use.
 */
@Singleton
class IncompatibleGames @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("gameport_incompatible", Context.MODE_PRIVATE)
    private val _showAnyway = MutableStateFlow(prefs.getBoolean(SHOW_ANYWAY, false))
    private val _acknowledged = MutableStateFlow(prefs.getStringSet(ACKNOWLEDGED, emptySet()).orEmpty().mapNotNull(String::toIntOrNull).toSet())

    private val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val kept = File(context.cacheDir, "incompatible-relay.json")
    private val lock = Mutex()
    internal var fetch: suspend () -> String? = { download(if (debuggable) "$RELAY/test/incompatible" else "$RELAY/incompatible") }
    internal var now: () -> Long = System::currentTimeMillis

    private val _list = MutableStateFlow(runCatching { kept.readText() }.getOrNull()?.let(IncompatibleList::fromRelay) ?: IncompatibleList())

    /** The list in use, and what it becomes when the relay says otherwise. */
    val lists: StateFlow<IncompatibleList> = _list.asStateFlow()

    /** The list in use now. */
    val list: IncompatibleList get() = _list.value

    /** Asks the relay again when what is kept is old (or was never read). Safe to call whenever a page opens. */
    suspend fun refreshIfStale() {
        lock.withLock {
            if (kept.exists() && now() - kept.lastModified() < (if (debuggable) DEBUG_REFRESH_MS else REFRESH_MS)) return
            val text = fetch() ?: return
            val read = IncompatibleList.fromRelay(text) ?: return
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

    /** The player wants these games on the home anyway, to try them. */
    val showAnyway: StateFlow<Boolean> = _showAnyway.asStateFlow()

    /** The games the player has been told about (they pressed "got it"). */
    val acknowledged: StateFlow<Set<Int>> = _acknowledged.asStateFlow()

    /** The app ids to leave out of the home: none while the player shows them anyway. */
    fun hiddenIds(showAnyway: Boolean, from: IncompatibleList = list): Set<Int> = if (showAnyway) emptySet() else from.games.map { it.appId }.toSet()

    fun setShowAnyway(show: Boolean) {
        prefs.edit().putBoolean(SHOW_ANYWAY, show).apply()
        _showAnyway.value = show
    }

    fun acknowledge(appIds: Collection<Int>) {
        val updated = _acknowledged.value + appIds
        prefs.edit().putStringSet(ACKNOWLEDGED, updated.map(Int::toString).toSet()).apply()
        _acknowledged.value = updated
    }

    private companion object {
        const val RELAY = "https://gameport-relay.gameport.workers.dev"
        const val TIMEOUT_MS = 10_000
        const val REFRESH_MS = 10 * 60 * 1000L
        const val DEBUG_REFRESH_MS = 20 * 1000L
        const val SHOW_ANYWAY = "show_anyway"
        const val ACKNOWLEDGED = "acknowledged"
    }
}
