package app.gameport.core.steam

import app.gameport.core.steam.session.offlineLibrary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import app.gameport.core.model.AuthState
import app.gameport.core.model.Game
import app.gameport.core.model.Library
import app.gameport.core.model.visibleTo
import app.gameport.core.steam.cache.LibraryCacheStore
import app.gameport.core.steam.session.SteamSessionHolder
import app.gameport.core.steam.session.androidGames
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/** One scan shared by every screen; it restarts when the signed-in session changes. */
@Singleton
class JavaSteamLibraryRepository @Inject constructor(
    private val sessions: SteamSessionHolder,
    private val cacheStore: LibraryCacheStore,
    auth: SteamAuthRepository,
    private val age: app.gameport.core.settings.AgeStore,
    private val adult: app.gameport.core.settings.AdultApps,
) : SteamLibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Raised each time the library has to be read again although nothing about the session changed: a free app was added to the account. */
    private val rescans = MutableStateFlow(0)

    fun rescan() = rescans.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val library: Flow<Library> = combine(sessions.current, auth.offline, auth.authState, rescans) { session, offline, state, round -> Scan(session, offline, state, round) }
        .distinctUntilChanged()
        .flatMapLatest { (session, offline, state) ->
            when {
                session != null -> session.androidGames(cacheStore)
                // Without a connection the library is what the last scan saved.
                offline && state is AuthState.SignedIn -> offlineLibrary(cacheStore, state.account.steamId and 0xFFFFFFFFL)
                else -> emptyFlow()
            }
        }
        .onEach { lib -> adult.learn(lib.games.filter { it.isAdult }.map { it.appId }) }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** What the player may see: adult games are left out unless the date of birth says 18 or more (see AgePolicy). */
    private val visible: Flow<Library> = combine(library, age.canSeeAdult) { lib, canSeeAdult -> lib.visibleTo(canSeeAdult) }

    override fun observeLibrary(): Flow<Library> = visible

    override fun observeGame(appId: Int): Flow<Game?> = visible.map { lib -> lib.games.firstOrNull { it.appId == appId } }

    override fun refreshGame(appId: Int) {
        val account = sessions.current.value?.accountId ?: return
        cacheStore.forgetChecked(account, appId)
        rescan()
    }

    /** What a scan depends on: the session, the connection, who is signed in, and how many times it was asked again. */
    private data class Scan(val session: app.gameport.core.steam.session.SteamSession?, val offline: Boolean, val state: AuthState, val round: Int)

    private companion object {
        const val STOP_TIMEOUT_MS = 30_000L
    }
}
