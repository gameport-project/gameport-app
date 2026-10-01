package app.gameport.core.steam

import app.gameport.core.model.Game
import app.gameport.core.model.Library
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/** One scan shared by every screen; it restarts when the signed-in session changes. */
@Singleton
class JavaSteamLibraryRepository @Inject constructor(
    sessions: SteamSessionHolder,
    private val cacheStore: LibraryCacheStore,
) : SteamLibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val library: Flow<Library> = sessions.current
        .flatMapLatest { session -> session?.androidGames(cacheStore) ?: emptyFlow() }
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override fun observeLibrary(): Flow<Library> = library

    override fun observeGame(appId: Int): Flow<Game?> = library.map { lib -> lib.games.firstOrNull { it.appId == appId } }

    private companion object {
        const val STOP_TIMEOUT_MS = 30_000L
    }
}
