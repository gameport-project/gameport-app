package app.gameport.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import app.gameport.core.model.ContinueScope
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.Game
import app.gameport.core.model.LibrarySort
import app.gameport.core.model.Library
import app.gameport.core.steam.SteamLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Content(
        /** Games after search and filters. */
        val games: List<Game>,
        val isScanning: Boolean,
        val query: String,
        val tab: LibraryTab,
        /** The VR / Flat tabs are only offered on headsets. */
        val showTabs: Boolean,
        val libraryIsEmpty: Boolean,
        /** Every game this device can show, whatever tab is selected: the "continue" row spans VR and flat games. */
        val anyKind: List<Game> = games,
        /** Games that need the player's attention (an outdated patch). */
        val attention: Set<Int> = emptySet(),
        /** What is shown besides the covers: the artwork behind them, the names, their size. */
        val appearance: DisplaySettings = DisplaySettings(),
        /** The rows of the home: the last games started, the starred ones, then everything in the chosen order. */
        val continueGames: List<Game> = emptyList(),
        val favoriteGames: List<Game> = emptyList(),
        val allGames: List<Game> = games,
        val favoriteIds: Set<Int> = emptySet(),
        val installedIds: Set<Int> = emptySet(),
        /** The game started most recently, which the banner shows until another is highlighted. */
        val lastPlayed: Game? = null,
        /** Names of the installed games Steam has a newer build for. */
        val updates: List<String> = emptyList(),
    ) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: SteamLibraryRepository,
    headsetDetector: HeadsetDetector,
    attention: AttentionSource,
    appearance: AppearancePreference,
    history: HistorySource,
    private val actions: LibraryActions,
    private val updates: UpdatesSource,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(LibraryTab.VR)
    private val showTabs = headsetDetector.isHeadset()

    val uiState: StateFlow<LibraryUiState> = combine(
        combine(repository.observeLibrary(), query, tab, attention.observe(), combine(appearance.observe(), history.observe(), ::Pair)) { library, query, tab, outdated, (shown, played) ->
            library.toUiState(query, tab, showTabs).copy(attention = outdated).arranged(shown, played)
        },
        updates.observe(),
    ) { state, updatable -> state.copy(updates = updatable) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

    init {
        // Asked when the library opens (that is, when the app starts).
        viewModelScope.launch { runCatching { updates.check() } }
    }

    fun onQueryChanged(value: String) = query.update { value }

    fun onTabSelected(value: LibraryTab) = tab.update { value }

    fun onSortSelected(sort: LibrarySort) = actions.setSort(sort)

    fun playIntent(game: Game): Intent? = actions.playIntent(game)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

enum class LibraryTab { VR, FLAT }

internal fun Library.toUiState(query: String, tab: LibraryTab, showTabs: Boolean): LibraryUiState.Content {
    val needle = query.trim()
    // A search looks through every game, so a match never hides behind the other tab.
    val useTabs = showTabs && needle.isEmpty()
    val visible = games.filter { game ->
        (needle.isEmpty() || game.name.contains(needle, ignoreCase = true)) &&
            // A device without VR shows no VR game; a headset splits them in two tabs.
            (showTabs || game.androidBuild?.isVr != true) &&
            (!useTabs || (game.androidBuild?.isVr == true) == (tab == LibraryTab.VR))
    }
    return LibraryUiState.Content(
        games = visible,
        isScanning = isScanning,
        query = query,
        tab = tab,
        showTabs = showTabs,
        libraryIsEmpty = games.isEmpty(),
        // Games started or installed here, VR or not; a device without VR still shows no VR game.
        anyKind = this.games.filter { game -> showTabs || game.androidBuild?.isVr != true },
    )
}

/** How many games the "continue" row lists. */
private const val CONTINUE_LIMIT = 12

/**
 * Splits the filtered games into the home's rows. A search shows one flat list of matches instead of
 * the rows. The sort only applies to the "all games" row; "recently played" means played on this device:
 * the games started here come first, then those installed here but never started, then the others.
 */
internal fun LibraryUiState.Content.arranged(display: DisplaySettings, history: PlayHistory): LibraryUiState.Content {
    val installedIds = history.installedAt.keys
    val visible = games.filter { !display.hideUninstalled || it.appId in installedIds }
    val byName = compareBy<Game> { it.name.lowercase() }
    val newestInstall = compareByDescending<Game> { history.installedAt[it.appId] ?: 0L }
    // Played here, most recent first; a game removed from this device is no longer counted as played.
    val played = visible.filter { it.appId in installedIds && it.appId in history.lastPlayed }.sortedByDescending { history.lastPlayed[it.appId] }
    val unplayed = visible.filter { it.appId in installedIds && it.appId !in history.lastPlayed }.sortedWith(newestInstall.then(byName))
    // The "continue" row ignores the VR / Flat tab: it lists every kind of game, tabs or not.
    val everywhere = anyKind.filter { !display.hideUninstalled || it.appId in installedIds }
    val byTab = display.continueScope == ContinueScope.TAB && showTabs
    val playedAnywhere = (if (byTab) visible else everywhere).filter { it.appId in installedIds && it.appId in history.lastPlayed }.sortedByDescending { history.lastPlayed[it.appId] }
    val unplayedAnywhere = (if (byTab) visible else everywhere).filter { it.appId in installedIds && it.appId !in history.lastPlayed }.sortedWith(newestInstall.then(byName))
    val others = visible.filter { it.appId !in installedIds }.sortedWith(byName)
    val sorted = when (display.sort) {
        LibrarySort.NAME -> visible.sortedWith(byName)
        LibrarySort.RECENTLY_PLAYED -> played + unplayed + others
        LibrarySort.RECENTLY_INSTALLED -> visible.filter { it.appId in installedIds }.sortedWith(newestInstall.then(byName)) + others
    }
    val searching = query.isNotBlank()
    return copy(
        appearance = display,
        allGames = sorted,
        // Until games have been started here, the row is filled with the installed ones, the newest first.
        continueGames = if (searching || !display.showContinue) emptyList() else (playedAnywhere + unplayedAnywhere).take(CONTINUE_LIMIT),
        favoriteGames = if (searching || !display.showFavorites) emptyList() else visible.filter { it.appId in history.favorites }.sortedWith(byName),
        favoriteIds = history.favorites,
        installedIds = installedIds.toSet(),
        lastPlayed = playedAnywhere.firstOrNull(),
    )
}
