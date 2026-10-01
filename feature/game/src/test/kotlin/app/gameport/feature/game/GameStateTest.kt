package app.gameport.feature.game

import app.gameport.core.model.Game
import app.gameport.core.model.Library
import app.gameport.core.model.Ownership
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStateTest {
    private val game = Game(7, "Seven", Ownership.OWNED, androidBuild = null)

    @Test
    fun `shows the game once the library contains it`() = runTest {
        val state = flowOf(Library(listOf(game), isScanning = true)).toGameState(7).first()
        assertEquals(GameUiState.Content(game), state)
    }

    @Test
    fun `keeps loading while the scan may still find the game`() = runTest {
        assertEquals(GameUiState.Loading, flowOf(Library(emptyList(), isScanning = true)).toGameState(7).first())
    }

    @Test
    fun `reports not found once the scan is over`() = runTest {
        assertEquals(GameUiState.NotFound, flowOf(Library(emptyList(), isScanning = false)).toGameState(7).first())
    }
}
