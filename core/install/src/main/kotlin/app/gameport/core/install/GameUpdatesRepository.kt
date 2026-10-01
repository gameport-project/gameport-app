package app.gameport.core.install

import android.util.Log
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.currentBuilds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** An installed game for which Steam has published a newer build. */
data class GameUpdate(val appId: Int, val name: String)

/**
 * Finds out which installed games have a newer build on Steam by comparing the build id of each
 * depot with the one the game was installed from. A game installed before builds were recorded is
 * taken as up to date the first time it is seen.
 */
@Singleton
class GameUpdatesRepository @Inject constructor(
    private val installed: InstalledGames,
    private val builds: InstalledBuilds,
    private val sessions: SteamSessionHolder,
) {
    private val _updates = MutableStateFlow<List<GameUpdate>>(emptyList())
    val updates: StateFlow<List<GameUpdate>> = _updates.asStateFlow()

    /** Asks Steam again; does nothing while signed out. */
    suspend fun check() {
        val session = sessions.current.value ?: return
        val appIds = installed.all().keys
        if (appIds.isEmpty()) {
            _updates.value = emptyList()
            return
        }
        val current = runCatching { session.currentBuilds(appIds) }
            .onFailure { Log.w(TAG, "update check failed", it) }
            .getOrNull() ?: return
        _updates.value = appIds.mapNotNull { appId ->
            val now = current[appId] ?: return@mapNotNull null
            val recorded = builds.get(appId)
            if (recorded == null) {
                // Installed before builds were recorded: assume it is current, so nothing is flagged wrongly.
                builds.put(appId, InstalledBuild(manifests = now.manifests))
                return@mapNotNull null
            }
            val changed = recorded.manifests.any { (depotId, manifestId) ->
                val latest = now.manifests[depotId] ?: return@any false
                latest != 0L && latest != manifestId
            }
            if (changed) GameUpdate(appId, now.name) else null
        }.sortedBy { it.name.lowercase() }
    }

    /** The game was just installed from the newest build. */
    fun markCurrent(appId: Int) {
        _updates.update { list -> list.filterNot { it.appId == appId } }
    }

    /** Forgets the update list (used when signing out). */
    fun clear() {
        _updates.value = emptyList()
    }

    private companion object {
        const val TAG = "GPUpdates"
    }
}
