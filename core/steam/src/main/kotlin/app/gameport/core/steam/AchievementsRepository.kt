package app.gameport.core.steam

import app.gameport.core.model.AchievementList
import app.gameport.core.model.AuthState
import app.gameport.core.model.SteamLanguage
import app.gameport.core.steam.cache.AchievementCache
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.achievements
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
    private val language: app.gameport.core.model.AchievementLanguage,
) {
    /**
     * What was kept first (when there is something), then what Steam says now. Offline mode, a missing connection or
     * an answer that does not come leave what was kept.
     */
    fun observe(appId: Int): Flow<AchievementList?> = flow {
        val steamId = (auth.authState.value as? AuthState.SignedIn)?.account?.steamId ?: return@flow
        val wanted = steamLanguage()
        val kept = cache.read(appId, steamId)
        // What was kept is shown at once when it is in the language asked for; otherwise it only serves if Steam cannot be asked.
        if (kept != null && kept.language == wanted) emit(kept)
        runCatching { auth.restoreSession() }
        val session = if (auth.offline.value) null else withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() }
        val fresh = session?.let { runCatching { it.achievements(appId, wanted) }.getOrNull() }
        if (fresh == null) {
            if (kept != null && kept.language != wanted) emit(kept)
            return@flow
        }
        cache.write(fresh, steamId)
        emit(fresh)
    }

    /**
     * What the account has for [appId], to give to the game as it starts: read from Steam within [timeoutMs], else what was kept.
     * Null when neither is available. The texts do not matter here, only which achievements are unlocked, and nothing is kept.
     */
    suspend fun forSync(appId: Int, timeoutMs: Long): AchievementList? {
        val steamId = (auth.authState.value as? AuthState.SignedIn)?.account?.steamId ?: return null
        val fresh = withTimeoutOrNull(timeoutMs) {
            runCatching { auth.restoreSession() }
            if (auth.offline.value) return@withTimeoutOrNull null
            val session = sessions.current.filterNotNull().first()
            runCatching { session.achievements(appId, "english") }.getOrNull()
        }
        return fresh ?: cache.read(appId, steamId)
    }

    /**
     * The achievements of [appId] in English, read from Steam now, to be baked into a game being patched for its Steamworks shim. Null when
     * Steam cannot be asked (offline mode, no connection, no answer in time) or the game has none: the patch then simply goes without them.
     */
    suspend fun forShim(appId: Int): AchievementList? {
        runCatching { auth.restoreSession() }
        if (auth.offline.value) return null
        val session = withTimeoutOrNull(SESSION_WAIT_MS) { sessions.current.filterNotNull().first() } ?: return null
        return runCatching { session.achievements(appId, "english") }.getOrNull()?.takeIf { it.items.isNotEmpty() }
    }

    /** The texts come from the game, in as many languages as it has: they are asked in the language chosen in GamePort. */
    private fun steamLanguage(): String = language.steamName()

    private companion object {
        const val SESSION_WAIT_MS = 10_000L
    }
}
