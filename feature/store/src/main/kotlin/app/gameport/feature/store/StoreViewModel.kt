package app.gameport.feature.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gameport.core.device.DeviceProfile
import app.gameport.core.model.AndroidApp
import app.gameport.core.model.AndroidCatalog
import app.gameport.core.model.CatalogLanguages
import app.gameport.core.model.CompatLevel
import app.gameport.core.model.CoverSize
import app.gameport.core.settings.UserSettings
import app.gameport.core.steam.AccountRegion
import app.gameport.core.steam.SteamLibraryRepository
import app.gameport.core.sync.AndroidCatalogRepository
import app.gameport.core.sync.CatalogTextsRepository
import app.gameport.core.sync.CompatRepository
import app.gameport.core.sync.PriceRepository
import app.gameport.core.model.StorePrice
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StoreUiState {
    data object Loading : StoreUiState

    /** Neither a catalog kept on the device nor the one shipped could be read. */
    data object Unavailable : StoreUiState

    data class Content(
        /** The games after the search and the filters. */
        val apps: List<AndroidApp>,
        /** How many games the catalog lists for this device, whatever the search and the filters. */
        val total: Int,
        /** The apps of the account's Steam library, listed in GamePort or not. */
        val ownedIds: Set<Int>,
        /** Those GamePort lists in its library: only these have a page to open there. */
        val libraryIds: Set<Int> = emptySet(),
        /** The games whose Android version arrived this week. */
        val newIds: Set<Int>,
        val query: String,
        val filters: StoreFilters,
        /** The filters now are the ones the player asked to keep. */
        val kept: Boolean,
        /** False on a device without VR, which lists flat games only. */
        val vrDevice: Boolean,
        /** False when the player did not say they are an adult: the adult filter is not offered, since no adult game is listed. */
        val adultFilter: Boolean = true,
        /** The width of a cover, the one the player set for the home. */
        val coverWidth: Int = CoverSize.SMALL.dp,
        /** What the players of this kind of device say of the games they were enough to try. */
        val levels: Map<Int, CompatLevel> = emptyMap(),
        /** The games a compatibility filter leaves out because nobody reported on them for this device (see [withoutReports]), also the count of the "not tested yet" entry. */
        val withoutReports: Int = 0,
        val deviceKind: String = "",
        /** The picture of the game pointed at fills the page behind, as on the home, with the strength the player set for it. */
        val backdrop: Boolean = true,
        val backdropStrength: Int = 50,
        val viewMode: StoreViewMode,
        /** How many games a row of the list holds, 2 to 4. */
        val listColumns: Int = 4,
        /** How the list is ordered. */
        val sort: StoreSort = StoreSort.DEFAULT,
        /** What Steam sells the games for now in the country of the device, from the history the catalog keeps. */
        val prices: Map<Int, StorePrice> = emptyMap(),
        /** The games on sale that the account does not have and that can be installed, the biggest discount first: the carousel of the store. */
        val promos: List<AndroidApp> = emptyList(),
        /** The languages the games have, for the list the filter looks at. */
        val languageOptions: List<LanguageOption>,
        val tagOptions: List<TagOption>,
        /** The kinds of app the catalog holds (games, demos, betas, applications), for the filter. */
        val typeOptions: List<TypeOption> = emptyList(),
    ) : StoreUiState
}

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val catalog: AndroidCatalogRepository,
    private val texts: CatalogTextsRepository,
    private val keptFilters: StoreFilterStore,
    userSettings: UserSettings,
    compat: CompatRepository,
    private val accountRegion: AccountRegion,
    private val priceRepository: PriceRepository,
    library: SteamLibraryRepository,
    device: DeviceProfile,
    private val age: app.gameport.core.settings.AgeStore,
) : ViewModel() {
    private val language = CatalogLanguages.of(Locale.getDefault())
    private val query = MutableStateFlow("")

    // The list opens with the filters the player asked to keep; without any, with the games that can be installed and that the account does not have, the VR ones on a headset.
    private val filters = MutableStateFlow(keptFilters.kept() ?: StoreFilters(quick = setOfNotNull(StoreFilter.INSTALLABLE, StoreFilter.NOT_OWNED, StoreFilter.VR.takeIf { device.isHeadset })))
    private val viewMode = MutableStateFlow(keptFilters.viewMode())
    private val listColumns = MutableStateFlow(keptFilters.listColumns())
    private val sort = MutableStateFlow(keptFilters.sort())
    private val kept = MutableStateFlow(keptFilters.kept())

    /** The name of a tag in the language of the device, or in English where it has none. */
    private val tagNames: Flow<(Int) -> String?> = combine(texts.observe(language), texts.observe(ENGLISH)) { mine, english ->
        { id: Int -> mine?.tagName(id) ?: english?.tagName(id) }
    }

    /** What every game costs now in the country that fits the account: its currency, then the country Steam sees it in, then the device. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pricesNow: Flow<Map<Int, StorePrice>> =
        combine(priceRepository.observeIndex(), accountRegion.currency, accountRegion.country) { index, currency, country ->
            index?.countryFor(country ?: Locale.getDefault().country, currency)
        }.distinctUntilChanged().flatMapLatest { country ->
            if (country == null) flowOf(emptyMap()) else priceRepository.observeNow(country).map { it?.all().orEmpty() }
        }

    val uiState: StateFlow<StoreUiState> = combine(
        catalog.observe(), library.observeLibrary(), tagNames, combine(compat.observeLevels(), pricesNow) { levels, prices -> levels to prices },
        combine(query, filters, kept, viewMode, combine(listColumns, sort) { columns, order -> columns to order }) { text, chosen, keptNow, mode, display -> arrayOf(text, chosen, keptNow, mode, display) },
    ) { listed, owned, nameOf, levelsAndPrices, shown ->
        val (levels, prices) = levelsAndPrices
        val text = shown[0] as String
        // A filter on adult content kept from before is ignored while the player may not see adult games: there are none to filter.
        val canSeeAdult = age.canSeeAdult.value
        val chosen = (shown[1] as StoreFilters).let { picked -> if (canSeeAdult) picked else picked.copy(quick = picked.quick - StoreFilter.ADULT - StoreFilter.NOT_ADULT) }
        val keptNow = shown[2] as StoreFilters?
        val mode = shown[3] as StoreViewMode
        @Suppress("UNCHECKED_CAST")
        val (columns, order) = shown[4] as Pair<Int, StoreSort>
        if (listed == null) return@combine StoreUiState.Unavailable
        val today = LocalDate.now()
        val libraryIds = owned.games.map { it.appId }.toSet()
        val ids = libraryIds + owned.ownedAppIds
        val forDevice = listed.apps.filter { device.isHeadset || !it.vr }
        StoreUiState.Content(
            apps = storeList(listed.apps, text, chosen, ids, today, device.isHeadset, prices, order, levels),
            total = forDevice.size,
            ownedIds = ids,
            libraryIds = libraryIds,
            newIds = AndroidCatalog(apps = listed.apps).arrivals(today).map { it.appid }.toSet(),
            query = text,
            filters = chosen,
            kept = keptNow != null && keptNow == chosen,
            vrDevice = device.isHeadset,
            adultFilter = canSeeAdult,
            viewMode = mode,
            listColumns = columns,
            sort = order,
            prices = prices,
            promos = forDevice.filter { (StoreFilter.VR !in chosen.quick || it.vr) && (StoreFilter.FLAT !in chosen.quick || !it.vr) }.filter { it.installable && it.appid !in ids && ((prices[it.appid] as? StorePrice.Paid)?.discountPercent ?: 0) > 0 }
                .sortedByDescending { (prices[it.appid] as StorePrice.Paid).discountPercent }.take(PROMO_LIMIT),
            levels = levels,
            withoutReports = withoutReports(listed.apps, text, chosen, ids, today, device.isHeadset, prices, levels),
            deviceKind = device.kind,
            backdrop = userSettings.display.value.backdrop,
            backdropStrength = userSettings.display.value.backdropStrength,
            coverWidth = userSettings.display.value.coverSize.dp,
            languageOptions = languageOptions(forDevice, chosen.languageKind),
            tagOptions = tagOptions(forDevice, nameOf),
            typeOptions = typeOptions(forDevice),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoreUiState.Loading)

    init {
        viewModelScope.launch { runCatching { catalog.refreshIfStale() } }
        viewModelScope.launch {
            runCatching { priceRepository.refreshIfStale(priceRepository.observeIndex().first()?.countryFor(accountRegion.country.value ?: Locale.getDefault().country, accountRegion.currency.value)) }
        }
        viewModelScope.launch { runCatching { texts.refreshIfStale(language) } }
        if (language != ENGLISH) viewModelScope.launch { runCatching { texts.refreshIfStale(ENGLISH) } }
    }

    fun onQueryChanged(text: String) = query.update { text }

    fun onSortChosen(order: StoreSort) {
        keptFilters.setSort(order)
        sort.value = order
    }

    fun onFilterToggled(filter: StoreFilter) = filters.update { it.toggled(filter) }

    /** The compatibility filter goes off, so the games nobody reported on are listed again. */
    fun onCompatFilterRemoved() = filters.update { it.copy(quick = it.quick - COMPAT_FILTERS) }

    /** A choice among filters that exclude each other (a tab, a state): the ones of the group go off, then [chosen] goes on, or none for "all". */
    fun onGroupChosen(group: Set<StoreFilter>, chosen: StoreFilter?) = filters.update { it.copy(quick = it.quick - group + listOfNotNull(chosen)) }

    fun onLanguageKindChosen(kind: LanguageKind) = filters.update { it.copy(languageKind = kind) }

    fun onLanguageToggled(code: String) = filters.update { it.withLanguage(code) }

    fun onTagToggled(tagId: Int) = filters.update { it.withTag(tagId) }

    fun onTypeToggled(type: String) = filters.update { it.withType(type) }

    /** The list button: 2 columns, then 3, then 4, then 2 again. */
    fun onListColumnsToggled() {
        val next = if (listColumns.value >= 4) 2 else listColumns.value + 1
        keptFilters.setListColumns(next)
        listColumns.value = next
    }

    fun onListColumnsChosen(columns: Int) {
        keptFilters.setListColumns(columns)
        listColumns.value = columns
    }

    fun onViewModeChosen(mode: StoreViewMode) {
        keptFilters.setViewMode(mode)
        viewMode.value = mode
    }

    fun onReset() = filters.update { StoreFilters() }

    /** Keeps the filters now on, or forgets the ones kept when they are the same. */
    fun onKeepToggled() {
        if (kept.value != null && kept.value == filters.value) {
            keptFilters.forget()
            kept.value = null
        } else {
            keptFilters.keep(filters.value)
            kept.value = filters.value
        }
    }

    private companion object {
        const val ENGLISH = "en"
        const val PROMO_LIMIT = 12
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
