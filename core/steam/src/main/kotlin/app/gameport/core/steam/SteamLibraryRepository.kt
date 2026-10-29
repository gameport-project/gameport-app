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

    /**
     * Asks Steam what changed since the last time and brings the library up to date: new games, a game the account already had that got an Android build, a new build. Nothing is
     * emptied: the games already known stay shown while it goes on, and only what changed is read again. [manual] is the player asking (the button of the settings): it is done now and
     * a scan follows whatever Steam says; otherwise it is the ordinary check, which waits a minute at least from the last one.
     */
    fun refreshLibrary(manual: Boolean = true) {}
}
