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

private object NoCatalog : AndroidCatalogSource {
    override fun observe(): Flow<app.gameport.core.model.AndroidCatalog?> = flowOf(null)

    override suspend fun refreshIfStale() = Unit
}

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
        val viewModel = LibraryViewModel(FakeLibrary(MutableSharedFlow()), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates, NoCatalog)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
        collector.cancel()
    }

    private class Catalog(vararg apps: app.gameport.core.model.AndroidApp) : AndroidCatalogSource {
        private val catalog = app.gameport.core.model.AndroidCatalog(count = apps.size, apps = apps.toList())
        override fun observe(): Flow<app.gameport.core.model.AndroidCatalog?> = flowOf(catalog)

        override suspend fun refreshIfStale() = Unit
    }

    private fun arrival(id: Int, vr: Boolean, daysAgo: Long) = app.gameport.core.model.AndroidApp(
        appid = id, name = "Android $id", vr = vr, installable = true, android = "ready",
        androidSince = java.time.LocalDate.now().minusDays(daysAgo).toString(),
    )

    private fun viewModel(catalog: AndroidCatalogSource) = LibraryViewModel(
        FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) },
        { flowOf(PlayHistory()) }, NoActions, NoUpdates, catalog,
    )

    @Test
    fun `the new Android versions of the week reach the home, for the tab shown`() = runTest {
        val viewModel = viewModel(Catalog(arrival(10, vr = true, daysAgo = 1), arrival(11, vr = false, daysAgo = 2), arrival(12, vr = true, daysAgo = 30)))
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(listOf(10), (viewModel.uiState.value as LibraryUiState.Content).newAndroid.map { it.appid })
        viewModel.onTabSelected(LibraryTab.FLAT)
        assertEquals(listOf(11), (viewModel.uiState.value as LibraryUiState.Content).newAndroid.map { it.appid })
        collector.cancel()
    }

    @Test
    fun `the row lists the new versions of games the account does not have, the latest arrival first`() = runTest {
        // Game 1 is in the library: its new version is not told here. 20 arrived a day ago, 21 five days ago, 22 today.
        val viewModel = viewModel(Catalog(arrival(21, vr = true, daysAgo = 5), arrival(1, vr = true, daysAgo = 1), arrival(20, vr = true, daysAgo = 1), arrival(22, vr = true, daysAgo = 0)))
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(listOf(22, 20, 21), (viewModel.uiState.value as LibraryUiState.Content).newAndroid.map { it.appid })
        collector.cancel()
    }

    @Test
    fun `a search shows no row of new Android versions`() = runTest {
        val viewModel = viewModel(Catalog(arrival(10, vr = true, daysAgo = 1)))
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("beat")
        assertEquals(emptyList<Int>(), (viewModel.uiState.value as LibraryUiState.Content).newAndroid.map { it.appid })
        collector.cancel()
    }

    @Test
    fun `what the players say of the games on this device reaches the home`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory(compat = mapOf(vrGame.appId to app.gameport.core.model.CompatLevel.FAILS, flatGame.appId to app.gameport.core.model.CompatLevel.WORKS))) }, NoActions, NoUpdates, NoCatalog)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(
            mapOf(vrGame.appId to app.gameport.core.model.CompatLevel.FAILS, flatGame.appId to app.gameport.core.model.CompatLevel.WORKS),
            (viewModel.uiState.value as LibraryUiState.Content).compat,
        )
        collector.cancel()
    }

    @Test
    fun `search matches names regardless of case`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates, NoCatalog)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("farm")

        assertEquals(listOf(flatGame), (viewModel.uiState.value as LibraryUiState.Content).games)
        collector.cancel()
    }

    @Test
    fun `tabs split vr and flat games on a headset`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { true }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates, NoCatalog)
        val collector = backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        assertEquals(listOf(vrGame), (viewModel.uiState.value as LibraryUiState.Content).games)
        viewModel.onTabSelected(LibraryTab.FLAT)
        assertEquals(listOf(flatGame), (viewModel.uiState.value as LibraryUiState.Content).games)
        collector.cancel()
    }

    @Test
    fun `off a headset there are no tabs and no vr games`() = runTest {
        val viewModel = LibraryViewModel(FakeLibrary(flowOf(Library(listOf(vrGame, flatGame), false))), HeadsetDetector { false }, { flowOf(emptySet()) }, { flowOf(DisplaySettings()) }, { flowOf(PlayHistory()) }, NoActions, NoUpdates, NoCatalog)
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

    override fun hide(appId: Int) = Unit

    override fun toggleFavorite(appId: Int) = Unit

    override fun update(game: Game) = Unit

    override fun repatch(appId: Int) = Unit
}
