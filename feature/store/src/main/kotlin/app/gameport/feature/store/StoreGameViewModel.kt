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
import kotlinx.coroutines.flow.combine
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
    ) : StoreGameUiState
}

@HiltViewModel
class StoreGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: AndroidCatalogRepository,
    private val texts: CatalogTextsRepository,
    library: SteamLibraryRepository,
    compat: CompatRepository,
    private val priceRepository: PriceRepository,
    private val accountRegion: AccountRegion,
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

    val uiState: StateFlow<StoreGameUiState> = combine(
        catalog.observe(), texts.observe(language), texts.observe(ENGLISH), library.observeLibrary(), combine(compat.observe(appId), pricing) { players, priced -> players to priced },
    ) { listed, mine, english, owned, (players, priced) ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoreGameUiState.Loading)

    init {
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
    }
}
