package app.gameport.core.steam

import app.gameport.core.model.Game
import app.gameport.core.model.Library
import kotlinx.coroutines.flow.Flow

interface SteamLibraryRepository {
    /** Owned and family-shared games that ship a native Android build, updated as Steam answers. */
    fun observeLibrary(): Flow<Library>

    fun observeGame(appId: Int): Flow<Game?>

    /**
     * Reads again what Steam publishes for one game, now: its save rules, depots and launch options may have changed with an update. The scan that
     * does it is the ordinary one, which only asks about what it does not know, so it costs one request.
     */
    fun refreshGame(appId: Int) {}
}
