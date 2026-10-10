package app.gameport.feature.game

import app.gameport.core.model.Game
import app.gameport.core.model.Library
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The game stays "loading" while the library is still being scanned and it has not shown up yet. */
internal fun Flow<Library>.toGameState(appId: Int): Flow<GameUiState> = map { gameStateOf(it, appId, unowned = null) }

/** [unowned] is the game as the table of compatibility knows it, for a game players reported and the account does not have. */
internal fun gameStateOf(library: Library, appId: Int, unowned: Game?): GameUiState {
    val game = library.games.firstOrNull { it.appId == appId }
    return when {
        game != null -> GameUiState.Content(game)
        library.isScanning -> GameUiState.Loading
        unowned != null -> GameUiState.Content(unowned)
        else -> GameUiState.NotFound
    }
}
