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
data class CompatEntry(val counts: CompatCounts, val name: String?, val inLibrary: Boolean, val art: app.gameport.core.model.Art, val vr: Boolean? = null) {
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

/** Whether a game belongs on the page of this device: a VR game means nothing on a phone or a tablet. A game not known to be VR (not in the library or the catalog) stays. */
internal fun shownOnDevice(entry: CompatEntry, isHeadset: Boolean): Boolean = isHeadset || entry.vr != true

/** The games players reported, with names from the account's library and, for the other games, from Steam. */
private fun entries(summaryGames: List<CompatCounts>, library: Map<Int, app.gameport.core.model.Game>, steamNames: Map<Int, String>, catalog: app.gameport.core.model.AndroidCatalog?) =
    summaryGames.map {
        // The cover comes from the library, else from the catalog, else from Steam's standard names: never written here.
        val art = library[it.appId]?.capsuleArt
            ?: catalog?.of(it.appId)?.art(app.gameport.core.model.ArtSlot.COVER, app.gameport.core.model.ArtSlot.HEADER, app.gameport.core.model.ArtSlot.BANNER, app.gameport.core.model.ArtSlot.CAPSULE)
            ?: app.gameport.core.model.Covers.capsule(it.appId)
        CompatEntry(it, library[it.appId]?.name ?: steamNames[it.appId], inLibrary = it.appId in library, art = art, vr = library[it.appId]?.androidBuild?.isVr ?: catalog?.of(it.appId)?.vr)
    }

@HiltViewModel
class CompatViewModel @Inject constructor(
    private val compat: CompatRepository,
    library: SteamLibraryRepository,
    private val appNames: AppNameRepository,
    device: DeviceProfile,
    adult: app.gameport.core.settings.AdultApps,
    // Also read so that the adult apps in it are known before the page lists anything.
    catalog: app.gameport.core.sync.AndroidCatalogRepository,
) : ViewModel() {
    private val hidden = adult.hidden
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CompatFilter.ALL)
    private val libraryNames = library.observeLibrary()

    /** The library, with the app ids that are left out of every list. */
    private val libraryAndHidden = combine(libraryNames, hidden, catalog.observe()) { lib, leftOut, cat -> Triple(lib, leftOut, cat) }

    val uiState: StateFlow<CompatUiState> = combine(compat.observeSummary(), libraryAndHidden, appNames.names, query, filter) { summary, (lib, leftOut, cat), steamNames, text, chosen ->
        if (summary == null) return@combine CompatUiState.Unavailable
        val needle = text.trim()
        val shown = entries(summary.games.filter { it.appId !in leftOut }, lib.games.associateBy { it.appId }, steamNames, cat)
            .filter { needle.isEmpty() || it.name?.contains(needle, ignoreCase = true) == true || it.counts.appId.toString().contains(needle) }
            .filter { shownOnDevice(it, device.isHeadset) }
            .filter { matches(it, chosen, device.kind) }
            // The most reported games first, then by name; a game whose name is not known goes after the ones that have one.
            .sortedWith(compareByDescending<CompatEntry> { it.players }.thenBy { it.name == null }.thenBy { it.name.orEmpty().lowercase() })
        CompatUiState.Content(shown, text, chosen, device.kind)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompatUiState.Loading)

    init {
        // Opening the page asks the relay again when what is kept is old, then Steam for the names of the games the account does not have.
        viewModelScope.launch { compat.refreshIfStale() }
        viewModelScope.launch {
            combine(compat.observeSummary().filterNotNull(), libraryNames.filter { !it.isScanning }, hidden) { summary, lib, leftOut ->
                val owned = lib.games.map { it.appId }.toSet()
                summary.games.map { it.appId }.filter { it !in owned && it !in leftOut }
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
