package app.gameport.core.steam

import app.gameport.core.model.LaunchOption
import app.gameport.core.steam.cache.LaunchOptionsStore
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.launchOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The arguments Steam lists for starting a game: kept on the device, and asked again whenever a session is there to ask. */
@Singleton
class LaunchOptionsRepository @Inject constructor(
    private val sessions: SteamSessionHolder,
    private val store: LaunchOptionsStore,
) {
    private val changes = MutableStateFlow(0)

    /** What was last listed for [appId]; empty while it was never asked. */
    fun cached(appId: Int): List<LaunchOption> = store.read(appId).orEmpty()

    fun observe(appId: Int): Flow<List<LaunchOption>> = changes.map { cached(appId) }

    /** Asks Steam again. Without a session, or when Steam does not answer, what is kept stays as it is. */
    suspend fun refresh(appId: Int) {
        val session = sessions.current.value ?: return
        val listed = runCatching { session.launchOptions(appId) }.getOrNull() ?: return
        if (store.read(appId) == listed) return
        store.write(appId, listed)
        changes.value += 1
    }
}
