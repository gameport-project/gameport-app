package app.gameport.core.steam

import app.gameport.core.model.AchievementList
import app.gameport.core.model.AuthState
import app.gameport.core.model.SteamLanguage
import app.gameport.core.steam.cache.AchievementCache
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.achievements
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The achievements of a game for the signed-in account, read from Steam and kept for when there is no connection.
 * Nothing is written to Steam from here.
 */
@Singleton
class AchievementsRepository @Inject constructor(
    private val auth: SteamAuthRepository,
    private val sessions: SteamSessionHolder,
    private val cache: AchievementCache,
) {
    /**
     * What was kept first (when there is something), then what Steam says now. Offline mode, a missing connection or
     * an answer that does not come leave what was kept.
     */
    fun observe(appId: Int): Flow<AchievementList?> = flow {
        val steamId = (auth.authState.value as? AuthState.SignedIn)?.account?.steamId ?: return@flow
        cache.read(appId, steamId)?.let { emit(it) }
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return@flow
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return@flow
        val fresh = runCatching { session.achievements(appId, steamLanguage()) }.getOrNull() ?: return@flow
        cache.write(fresh, steamId)
        emit(fresh)
    }

    /** The language of the player: the one chosen for GamePort, or the device's when none is, as Steam names it. */
    private fun steamLanguage(): String = Locale.getDefault().let { SteamLanguage.of(it.language, it.country, it.script) }

    private companion object {
        const val SESSION_WAIT_MS = 10_000L
    }
}
