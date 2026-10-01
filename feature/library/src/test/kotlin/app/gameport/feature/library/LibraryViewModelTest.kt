package app.gameport.feature.library

import app.gameport.core.model.AndroidBuild
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.Game
import app.gameport.core.model.LibrarySort
import app.gameport.core.model.Library
import app.gameport.core.model.Ownership
import app.gameport.core.steam.SteamLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

private object NoUpdates : UpdatesSource {
    override fun observe(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun check() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val vrGame = game(1, "Beat Cave", vr = true)
    private val flatGame = game(2, "Farm Town", vr = false)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts in loading state until the repository emits`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(MutableSharedFlow()), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
        collector.cancel()
    }

    @Test
    fun `search matches names regardless of case`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("farm")

        assertEquals(listOf(flatGame), (viewModel.uiState.value as LibraryUiState.Content).games)
        collector.cancel()
    }

    @Test
    fun `tabs split vr and flat games on a headset`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(listOf(vrGame), (viewModel.uiState.value as LibraryUiState.Content).games)
        viewModel.onTabSelected(LibraryTab.FLAT)
        assertEquals(listOf(flatGame), (viewModel.uiState.value as LibraryUiState.Content).games)
        collector.cancel()
    }

    @Test
    fun `off a headset there are no tabs and no vr games`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { false }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.onTabSelected(LibraryTab.FLAT)

        val state = viewModel.uiState.value as LibraryUiState.Content
        assertFalse(state.showTabs)
        assertEquals(listOf(flatGame), state.games)
        collector.cancel()
    }

    private fun game(id: Int, name: String, vr: Boolean) =
        Game(id, name, Ownership.OWNED, AndroidBuild(packageName = null, isVr = vr))

    private class FakeLibrary(private val library: Flow<Library>) : SteamLibraryRepository {
        override fun observeLibrary(): Flow<Library> = library

        override fun observeGame(appId: Int): Flow<Game?> = flowOf(null)
    }
}

private object NoActions : LibraryActions {
    override fun setSort(sort: LibrarySort) = Unit

    override fun playIntent(game: Game): android.content.Intent? = null
}
