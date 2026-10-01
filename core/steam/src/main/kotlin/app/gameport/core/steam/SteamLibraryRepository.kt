package app.gameport.core.steam

import app.gameport.core.model.Game
import app.gameport.core.model.Library
import kotlinx.coroutines.flow.Flow

interface SteamLibraryRepository {
    /** Owned and family-shared games that ship a native Android build, updated as Steam answers. */
    fun observeLibrary(): Flow<Library>

    fun observeGame(appId: Int): Flow<Game?>
}
