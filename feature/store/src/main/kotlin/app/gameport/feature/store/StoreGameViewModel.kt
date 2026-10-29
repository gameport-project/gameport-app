package app.gameport.feature.store

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.gameport.core.model.AndroidApp
import app.gameport.core.model.CatalogLanguages
import app.gameport.core.model.Compat
import app.gameport.core.model.PriceStory
import app.gameport.core.model.StorePrice
import app.gameport.core.steam.AccountRegion
import app.gameport.core.steam.FreeLicenseOutcome
import app.gameport.core.steam.FreeLicenseRepository
import app.gameport.core.steam.SteamLibraryRepository
import app.gameport.core.sync.AndroidCatalogRepository
import app.gameport.core.sync.CatalogTextsRepository
import app.gameport.core.sync.CompatRepository
import app.gameport.core.sync.PriceRepository
import app.gameport.core.model.PricePoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface StoreGameUiState {
    data object Loading : StoreGameUiState

    data class Content(
        /** The game, or null when the catalog does not list it. */
        val app: AndroidApp?,
        val appId: Int,
        /** Its short description in the language of the device, or in English where the catalog has no translation yet. */
        val summary: String?,
        /** The names of its heaviest tags, in the language of the device. */
        val tags: List<String>,
        /** The account has the game, so its page in the library exists. */
        val owned: Boolean,
        /** What the players of this kind of device say of it, when enough of them did. */
        val players: Compat?,
        /** What Steam sells it for in the country of the device, from the history the catalog keeps; null when it has none. */
        val price: StorePrice?,
        /** What the history of its price tells; null when the catalog has none worth telling. */
        val story: PriceStory?,
        /** The history of the price is being read: its place shows a skeleton. */
        val storyLoading: Boolean = false,
        /** Steam is being asked to add the game to the library. */
        val adding: Boolean = false,
        /** What came of the last request to add it, until the page is left or another request starts. */
        val added: FreeLicenseOutcome? = null,
    ) : StoreGameUiState
}

@HiltViewModel
class StoreGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: AndroidCatalogRepository,
    private val texts: CatalogTextsRepository,
    private val library: SteamLibraryRepository,
    compat: CompatRepository,
    private val priceRepository: PriceRepository,
    private val accountRegion: AccountRegion,
    private val freeLicenses: FreeLicenseRepository,
    private val mediaRepository: app.gameport.core.sync.MediaRepository,
    private val dlcRepository: app.gameport.core.sync.StoreDlcRepository,
) : ViewModel() {
    private val appId = savedStateHandle.toRoute<StoreGameRoute>().appId
    private val language = CatalogLanguages.of(Locale.getDefault())

    /** The country whose prices fit the account: the currency it is billed in, then the country Steam sees it in, then the device. */
    private val country: Flow<String?> =
        combine(priceRepository.observeIndex(), accountRegion.currency, accountRegion.country) { index, currency, place ->
            index?.countryFor(place ?: Locale.getDefault().country, currency)
        }.distinctUntilChanged()

    /** The lines of the history of this game by country, as the device has them (kept or shipped), then as the answer of the network gave them. */
    private val history = MutableStateFlow<Map<String, List<PricePoint>>?>(null)
    private val historyRead = MutableStateFlow(false)

    private data class Priced(val price: StorePrice? = null, val story: PriceStory? = null, val loading: Boolean = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pricing: Flow<Priced> = country.flatMapLatest { place ->
        if (place == null) flowOf(Priced(loading = true)) else combine(priceRepository.observeNow(place), priceRepository.observeIndex(), history, historyRead) { now, index, lines, read ->
            Priced(
                price = now?.priceOf(appId),
                story = lines?.get(place)?.let { PriceStory.of(it, index?.currencies?.get(place).orEmpty()) },
                loading = !read,
            )
        }
    }

    /** The request to add the game to the library: running, and what it ended with. */
    private data class Adding(val running: Boolean = false, val outcome: FreeLicenseOutcome? = null)

    private val adding = MutableStateFlow(Adding())

    /** Asks Steam to add the game to the account. Only for a free game the account does not have, and only one request at a time. */
    fun onAddToLibrary() {
        val shown = uiState.value as? StoreGameUiState.Content ?: return
        if (shown.owned || shown.adding || shown.price != StorePrice.Free) return
        adding.value = Adding(running = true)
        viewModelScope.launch {
            val outcome = freeLicenses.add(appId)
            adding.value = Adding(outcome = outcome)
        }
    }

    val uiState: StateFlow<StoreGameUiState> = combine(
        catalog.observe(), texts.observe(language), texts.observe(ENGLISH), library.observeLibrary(), combine(compat.observe(appId), pricing, adding) { players, priced, request -> Triple(players, priced, request) },
    ) { listed, mine, english, owned, (players, priced, request) ->
        val app = listed?.of(appId)
        StoreGameUiState.Content(
            app = app,
            appId = appId,
            summary = mine?.summary(appId) ?: english?.summary(appId),
            tags = app?.tags.orEmpty().mapNotNull { mine?.tagName(it) ?: english?.tagName(it) },
            owned = owned.games.any { it.appId == appId },
            players = players,
            price = priced.price,
            story = priced.story,
            storyLoading = priced.loading,
            adding = request.running,
            added = request.outcome,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoreGameUiState.Loading)

    /** The screenshots and trailers of the page, once the device has the file that holds them. */
    val media: StateFlow<app.gameport.core.model.AppMedia?> = mediaRepository.observe(appId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The extra content of the game as Steam's shop lists it, asked for in the background when the page opens: nothing is shown until it is there. */
    data class DlcState(val items: List<app.gameport.core.sync.StoreDlc> = emptyList(), val loading: Boolean = true, val owned: Set<Int> = emptySet())

    private val _dlc = MutableStateFlow(DlcState())
    /** The extra content, with which of it the account already has (read from what Steam says the account can use, so a free one already added shows no button). */
    val dlc: StateFlow<DlcState> = combine(_dlc, library.observeLibrary().map { it.ownedAppIds }.distinctUntilChanged()) { state, owned -> state.copy(owned = owned) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DlcState())

    /** The request to add a free extra content to the library: running, and what it ended with. */
    data class DlcAdd(val running: Boolean = false, val outcome: FreeLicenseOutcome? = null)

    private val _dlcAdds = MutableStateFlow<Map<Int, DlcAdd>>(emptyMap())
    val dlcAdds: StateFlow<Map<Int, DlcAdd>> = _dlcAdds.asStateFlow()

    /** Asks Steam to add a free extra content to the account, one request at a time for each. Only for what the shop says is free. */
    fun onAddDlc(dlcId: Int) {
        val item = _dlc.value.items.firstOrNull { it.appId == dlcId } ?: return
        if (!item.free || _dlcAdds.value[dlcId]?.running == true) return
        _dlcAdds.value = _dlcAdds.value + (dlcId to DlcAdd(running = true))
        viewModelScope.launch {
            val outcome = freeLicenses.add(dlcId)
            _dlcAdds.value = _dlcAdds.value + (dlcId to DlcAdd(outcome = outcome))
        }
    }

    init {
        viewModelScope.launch {
            // The country of the prices: the one of the account when it is known soon, else the device's.
            val place = kotlinx.coroutines.withTimeoutOrNull(DLC_COUNTRY_WAIT_MS) { country.first { it != null } } ?: Locale.getDefault().country.ifEmpty { "US" }
            val items = runCatching { dlcRepository.of(appId, place, language) }.getOrDefault(emptyList())
            _dlc.value = DlcState(items, loading = false)
        }
        viewModelScope.launch { runCatching { mediaRepository.refreshIfStale() } }
        viewModelScope.launch { runCatching { catalog.refreshIfStale() } }
        // The history is read from the device at once, so the page has it without waiting; then the network is asked, and the page takes what it brings.
        viewModelScope.launch {
            history.value = priceRepository.historyOnDevice(appId)
            historyRead.value = true
            runCatching { priceRepository.refreshHistory(appId) }.getOrNull()?.let { history.value = it }
            runCatching { priceRepository.refreshIfStale(country.first()) }
        }
        viewModelScope.launch { runCatching { texts.refreshIfStale(language) } }
        if (language != ENGLISH) viewModelScope.launch { runCatching { texts.refreshIfStale(ENGLISH) } }
    }

    private companion object {
        const val ENGLISH = "en"
        const val STOP_TIMEOUT_MS = 5_000L
        const val DLC_COUNTRY_WAIT_MS = 2_000L
    }
}
