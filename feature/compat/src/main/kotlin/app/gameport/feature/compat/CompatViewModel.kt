package app.gameport.feature.compat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.device.DeviceProfile
import app.gameport.core.model.CompatCounts
import app.gameport.core.steam.AppNameRepository
import app.gameport.core.steam.SteamLibraryRepository
import app.gameport.core.sync.CompatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One game of the page: what the players said, its name when it is known, and whether the account has it. */
data class CompatEntry(val counts: CompatCounts, val name: String?, val inLibrary: Boolean) {
    val players: Int get() = counts.works + counts.offlineOnly + counts.fails
}

/** The quick filters under the search field: by what the players of this kind of device say. */
enum class CompatFilter { ALL, WORKS, FAILS }

sealed interface CompatUiState {
    data object Loading : CompatUiState

    /** Nothing was ever read from the relay, and nothing is kept. */
    data object Unavailable : CompatUiState
    data class Content(val entries: List<CompatEntry>, val query: String, val filter: CompatFilter, val deviceKind: String) : CompatUiState
}

/**
 * Whether a game passes a filter, by what the players of [deviceKind] say. A game they said too little about passes neither of the two; "offline only" is on the
 * side of what works, as everywhere else, and opinions that are divided are on neither side.
 */
internal fun matches(entry: CompatEntry, filter: CompatFilter, deviceKind: String): Boolean {
    if (filter == CompatFilter.ALL) return true
    val level = app.gameport.core.model.CompatRules.of(entry.counts, deviceKind)?.level ?: return false
    return when (filter) {
        CompatFilter.WORKS -> level == app.gameport.core.model.CompatLevel.WORKS || level == app.gameport.core.model.CompatLevel.OFFLINE_ONLY
        CompatFilter.FAILS -> level == app.gameport.core.model.CompatLevel.FAILS
        CompatFilter.ALL -> true
    }
}

/** The games players reported, with names from the account's library and, for the other games, from Steam. */
private fun entries(summaryGames: List<CompatCounts>, libraryNames: Map<Int, String>, steamNames: Map<Int, String>) =
    summaryGames.map { CompatEntry(it, libraryNames[it.appId] ?: steamNames[it.appId], inLibrary = it.appId in libraryNames) }

@HiltViewModel
class CompatViewModel @Inject constructor(
    private val compat: CompatRepository,
    library: SteamLibraryRepository,
    private val appNames: AppNameRepository,
    device: DeviceProfile,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CompatFilter.ALL)
    private val libraryNames = library.observeLibrary()

    val uiState: StateFlow<CompatUiState> = combine(compat.observeSummary(), libraryNames, appNames.names, query, filter) { summary, lib, steamNames, text, chosen ->
        if (summary == null) return@combine CompatUiState.Unavailable
        val needle = text.trim()
        val shown = entries(summary.games, lib.games.associate { it.appId to it.name }, steamNames)
            .filter { needle.isEmpty() || it.name?.contains(needle, ignoreCase = true) == true || it.counts.appId.toString().contains(needle) }
            .filter { matches(it, chosen, device.kind) }
            // The most reported games first, then by name; a game whose name is not known goes after the ones that have one.
            .sortedWith(compareByDescending<CompatEntry> { it.players }.thenBy { it.name == null }.thenBy { it.name.orEmpty().lowercase() })
        CompatUiState.Content(shown, text, chosen, device.kind)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompatUiState.Loading)

    init {
        // Opening the page asks the relay again when what is kept is old, then Steam for the names of the games the account does not have.
        viewModelScope.launch { compat.refreshIfStale() }
        viewModelScope.launch {
            combine(compat.observeSummary().filterNotNull(), libraryNames.filter { !it.isScanning }) { summary, lib ->
                val owned = lib.games.map { it.appId }.toSet()
                summary.games.map { it.appId }.filter { it !in owned }
            }.collect { appNames.resolve(it) }
        }
    }

    fun onQueryChanged(text: String) {
        query.value = text
    }

    /** A filter chosen again is taken off. */
    fun onFilterChosen(chosen: CompatFilter) {
        filter.value = if (filter.value == chosen) CompatFilter.ALL else chosen
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
