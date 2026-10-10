package app.gameport.feature.game

import app.gameport.core.model.Library
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The game stays "loading" while the library is still being scanned and it has not shown up yet. */
internal fun Flow<Library>.toGameState(appId: Int): Flow<GameUiState> = map { library ->
    val game = library.games.firstOrNull { it.appId == appId }
    when {
        game != null -> GameUiState.Content(game)
        library.isScanning -> GameUiState.Loading
        else -> GameUiState.NotFound
    }
}
