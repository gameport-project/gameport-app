package app.gameport.feature.store

import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.animateContentSize
import app.gameport.core.designsystem.PillTabs
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.GridItemSpan
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import app.gameport.core.model.StorePrice
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.automirrored.filled.Sort
import app.gameport.core.designsystem.LocalBackdropColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gameport.core.model.CompatLevel
import app.gameport.feature.compat.CompatBadge
import app.gameport.feature.compat.CompatMark
import app.gameport.feature.compat.VerdictChip
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.BackdropMenu
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.designsystem.GlassSearchField
import app.gameport.core.designsystem.KindBlue
import app.gameport.core.designsystem.PageHeader
import app.gameport.core.designsystem.PanelGroup
import app.gameport.core.designsystem.PricePill
import app.gameport.core.designsystem.SidePanel
import app.gameport.core.designsystem.glass
import app.gameport.core.model.AndroidApp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal val StoreGreen = Color(0xFF66BB6A)
internal val StoreOrange = Color(0xFFFFB74D)
internal val StoreRed = Color(0xFFE57373)

/**
 * The Steam games that have an Android version, as a compact list: two or three games a row, each low, so a lot of them are seen at once. A game of the
 * library opens its own page; any other opens the page of the store, which sends to Steam.
 */
@Composable
fun StoreScreen(onBack: () -> Unit, onGameClick: (Int) -> Unit, onStoreGame: (Int) -> Unit, viewModel: StoreViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var pointed by remember { mutableStateOf<AndroidApp?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        (state as? StoreUiState.Content)?.takeIf { it.backdrop }?.let { ArtBackdrop(pointed, it.backdropStrength) }
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp)) {
                val content = state as? StoreUiState.Content
                // The VR / flat / all switch is at the top right, and the filter it stands for is named in front of the count.
                val tab = when {
                    content?.filters?.quick?.contains(StoreFilter.VR) == true -> 1
                    content?.filters?.quick?.contains(StoreFilter.FLAT) == true -> 2
                    else -> 0
                }
                val tabNames = listOf(stringResource(R.string.store_tab_all), stringResource(R.string.store_filter_vr), stringResource(R.string.store_tab_flat))
                Box(Modifier.fillMaxWidth()) {
                    PageHeader(
                        onBack,
                        stringResource(R.string.store_title),
                        content?.let { c ->
                            countText(c).let { count -> if (c.vrDevice && count.isNotEmpty()) tabNames[tab] + " \u00b7 " + count else count }
                        }.orEmpty(),
                    ) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = KindBlue, modifier = Modifier.size(52.dp))
                    }
                    if (content?.vrDevice == true) {
                        PillTabs(
                            labels = tabNames,
                            selectedIndex = tab,
                            onSelect = { viewModel.onGroupChosen(setOf(StoreFilter.VR, StoreFilter.FLAT), listOf(null, StoreFilter.VR, StoreFilter.FLAT)[it]) },
                            modifier = Modifier.align(Alignment.CenterEnd).width(240.dp),
                            height = 40.dp,
                        )
                    }
                }
                when (val current = state) {
                    StoreUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    StoreUiState.Unavailable -> Text(stringResource(R.string.store_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is StoreUiState.Content -> {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassSearchField(
                                value = current.query,
                                onValueChange = viewModel::onQueryChanged,
                                placeholder = stringResource(R.string.store_search_hint),
                                modifier = Modifier.weight(1f),
                            )
                            // The number of things narrowing the list sits on the button, as on the home, so a filter left on is never a mystery.
                            Box {
                                GlassIconButton(onClick = { panelOpen = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.store_filters))
                                }
                                val active = current.filters.activeCount
                                if (active > 0) {
                                    Text(
                                        text = active.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .wrapContentCenter(),
                                    )
                                }
                            }
                            SortButton(current.sort, viewModel::onSortChosen)
                            ViewModeButtons(current.viewMode, current.listColumns, viewModel::onViewModeChosen, viewModel::onListColumnsToggled)
                        }
                        StateFilters(current, viewModel)
                        if (current.apps.isEmpty()) {
                            Text(stringResource(R.string.store_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val covers = current.viewMode == StoreViewMode.COVERS
                            // Whatever narrows or orders the list, quick filter, panel, sort or search, sends it back to its top. The first composition, and the return from a
                            // game page, keep the place the list was at.
                            val shape = current.filters.encode() + "|" + current.sort.name + "|" + current.query
                            var seenShape by rememberSaveable { mutableStateOf<String?>(null) }
                            LaunchedEffect(shape) {
                                if (seenShape != null && seenShape != shape) gridState.scrollToItem(0)
                                seenShape = shape
                            }
                            // The bottom margin matches the top one.
                            LazyVerticalGrid(
                                state = gridState,
                                columns = if (covers) GridCells.Adaptive(minSize = current.coverWidth.dp) else GridCells.Fixed(current.listColumns),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (covers) 12.dp else 10.dp),
                                verticalArrangement = Arrangement.spacedBy(if (covers) 12.dp else 10.dp),
                            ) {
                                // The games on sale come first, over the whole width, and only while nothing is searched.
                                if (current.promos.isNotEmpty() && current.query.isBlank()) {
                                    item(key = "promos", span = { GridItemSpan(maxLineSpan) }) {
                                        PromoRail(
                                            current.promos,
                                            current.prices,
                                            current.levels,
                                            current.deviceKind,
                                            onPointed = { app, on -> if (on) pointed = app else if (pointed?.appid == app.appid) pointed = null },
                                            onOpen = onStoreGame,
                                        )
                                    }
                                    // The list has its own title under the sales, so the two read as two things.
                                    item(key = "all-games", span = { GridItemSpan(maxLineSpan) }) {
                                        Text(stringResource(R.string.store_all_games), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                                    }
                                }
                                items(current.apps, key = { it.appid }) { app ->
                                    val owned = app.appid in current.ownedIds
                                    val isNew = app.appid in current.newIds
                                    val onPointed = { on: Boolean -> if (on) pointed = app else if (pointed?.appid == app.appid) pointed = null }
                                    val open = { if (owned) onGameClick(app.appid) else onStoreGame(app.appid) }
                                    val level = current.levels[app.appid]
                                    val sortValue = sortValueOf(app, current.sort)
                                    Pointable(onPointed) { pointedNow ->
                                        if (covers) Cover(app, owned = owned, isNew = isNew, level = level, price = current.prices[app.appid], sortValue = sortValue, onClick = open) else GameRow(app, owned = owned, isNew = isNew, level = level, price = current.prices[app.appid], deviceKind = current.deviceKind, columns = current.listColumns, expanded = pointedNow, sortValue = sortValue, onClick = open)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        (state as? StoreUiState.Content)?.let { current ->
            FilterPanel(panelOpen, current, viewModel, onClose = { panelOpen = false })
        }
    }
}

private fun Modifier.wrapContentCenter(): Modifier = this.then(Modifier.wrapContentSize(Alignment.Center))

/** "12 of 200 games" while the list is narrowed, "200 games with an Android version" otherwise. */
@Composable
private fun countText(state: StoreUiState.Content): String = when {
    state.total == 0 -> ""
    state.query.isNotBlank() || !state.filters.isEmpty -> pluralStringResource(R.plurals.store_count_filtered, state.apps.size, state.apps.size, state.total)
    else -> pluralStringResource(R.plurals.store_count, state.total, state.total)
}

/** The name of an order, as the menu says it. */
@Composable
private fun sortName(sort: StoreSort): String = stringResource(
    when (sort) {
        StoreSort.DEFAULT -> R.string.store_sort_default
        StoreSort.NAME -> R.string.store_sort_name
        StoreSort.RATING -> R.string.store_sort_rating
        StoreSort.POPULAR -> R.string.store_sort_popular
        StoreSort.RELEASE -> R.string.store_sort_release
        StoreSort.PRICE -> R.string.store_sort_price
        StoreSort.PRICE_DESC -> R.string.store_sort_price_desc
        StoreSort.DISCOUNT -> R.string.store_sort_discount
    },
)

/** The figure a game is placed by, when the card does not show it already: its release, its reviews, its share of positive ones. */
@Composable
private fun sortValueOf(app: AndroidApp, sort: StoreSort): String? = when (sort) {
    StoreSort.RELEASE -> app.release?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull() }
    StoreSort.POPULAR -> app.reviewCount?.let { stringResource(R.string.storegame_reviews_count, it) }
    StoreSort.RATING -> app.reviewPercent?.let { "$it %" }
    else -> null
}

/** Three three-way switches on one low row: what can be installed, what the account has, and what works on this device. "All" is each one's first position, and it lists the games nobody rated too. */
@Composable
private fun StateFilters(state: StoreUiState.Content, viewModel: StoreViewModel) {
    val quick = state.filters.quick
    val availability = setOf(StoreFilter.INSTALLABLE, StoreFilter.FUTURE)
    val library = setOf(StoreFilter.OWNED, StoreFilter.NOT_OWNED)
    val compat = setOf(StoreFilter.COMPATIBLE, StoreFilter.INCOMPATIBLE)
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PillTabs(
            labels = listOf(stringResource(R.string.store_all), stringResource(R.string.store_avail_installable), stringResource(R.string.store_avail_future)),
            selectedIndex = when {
                StoreFilter.INSTALLABLE in quick -> 1
                StoreFilter.FUTURE in quick -> 2
                else -> 0
            },
            onSelect = { viewModel.onGroupChosen(availability, listOf(null, StoreFilter.INSTALLABLE, StoreFilter.FUTURE)[it]) },
            modifier = Modifier.width(310.dp),
            height = 34.dp,
            textStyle = MaterialTheme.typography.labelMedium,
        )
        PillTabs(
            labels = listOf(stringResource(R.string.store_all), stringResource(R.string.store_lib_owned), stringResource(R.string.store_lib_not_owned)),
            selectedIndex = when {
                StoreFilter.OWNED in quick -> 1
                StoreFilter.NOT_OWNED in quick -> 2
                else -> 0
            },
            onSelect = { viewModel.onGroupChosen(library, listOf(null, StoreFilter.OWNED, StoreFilter.NOT_OWNED)[it]) },
            modifier = Modifier.width(310.dp),
            height = 34.dp,
            textStyle = MaterialTheme.typography.labelMedium,
        )
        PillTabs(
            labels = listOf(stringResource(R.string.store_compat_all), stringResource(R.string.store_compat_yes), stringResource(R.string.store_compat_no)),
            selectedIndex = when {
                StoreFilter.COMPATIBLE in quick -> 1
                StoreFilter.INCOMPATIBLE in quick -> 2
                else -> 0
            },
            onSelect = { viewModel.onGroupChosen(compat, listOf(null, StoreFilter.COMPATIBLE, StoreFilter.INCOMPATIBLE)[it]) },
            modifier = Modifier.width(330.dp),
            height = 34.dp,
            textStyle = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Tells [onChange] when the pointer is over the content or it has the focus, so the page can show the game's picture behind. */
@Composable
private fun Pointable(onChange: (Boolean) -> Unit, content: @Composable (pointed: Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val on = hovered || focused
    LaunchedEffect(on) { onChange(on) }
    Box(Modifier.hoverable(source).onFocusChanged { focused = it.hasFocus }) { content(on) }
}

/** The picture of [app] behind the page, the weaker [strength] is the darker and blurrier, as on the home. */
@Composable
private fun ArtBackdrop(app: AndroidApp?, strength: Int) {
    val dim = ((100 - strength) / 50f).coerceAtLeast(0.1f)
    val blur = (((50 - strength).coerceAtLeast(0)) * 0.4f).dp
    Crossfade(targetState = app, label = "store-backdrop", modifier = Modifier.fillMaxSize()) { shown ->
        if (shown != null) {
            GameImage(
                url = shown.banner ?: shown.header ?: shown.cover ?: "",
                fallbackUrl = shown.header,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(blur),
            )
        }
    }
    fun shade(alpha: Float) = Color(0xFF101418).copy(alpha = (alpha * dim).coerceIn(0f, 1f))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to shade(0.8f), 0.45f to shade(0.4f), 1f to shade(0.95f))))
}

/** The soft green of a discount and of what is free. */
private val Mint = Color(0xFF7BD389)

/** The ink of a card of frosted glass, and its paler one. */
internal val CardInk = Color(0xFFF2F4F7)
internal val CardInkPale = Color(0xFFAAB3BF)

/** The order of the list: a button next to the filters, opening the three choices. */
@Composable
private fun SortButton(sort: StoreSort, onChosen: (StoreSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        // Icon alone while the order is the usual one; with the name of the order beside it otherwise, so the order can be read on the button.
        if (sort == StoreSort.DEFAULT) {
            GlassIconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.store_sort))
            }
        } else {
            GlassButton(onClick = { open = true }, modifier = Modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = KindBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(sortName(sort), maxLines = 1)
            }
        }
        BackdropMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                StoreSort.DEFAULT to R.string.store_sort_default,
                StoreSort.NAME to R.string.store_sort_name,
                StoreSort.RATING to R.string.store_sort_rating,
                StoreSort.POPULAR to R.string.store_sort_popular,
                StoreSort.RELEASE to R.string.store_sort_release,
                StoreSort.PRICE to R.string.store_sort_price,
                StoreSort.PRICE_DESC to R.string.store_sort_price_desc,
                StoreSort.DISCOUNT to R.string.store_sort_discount,
            ).forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { open = false; onChosen(value) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = if (value == sort) KindBlue else Color.Transparent, modifier = Modifier.size(18.dp))
                    Text(stringResource(label), color = if (value == sort) KindBlue else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** The two ways to see the games: a low list, or the covers. The one chosen is the one the list opens with next time. */
@Composable
private fun ViewModeButtons(mode: StoreViewMode, columns: Int, onChosen: (StoreViewMode) -> Unit, onColumns: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            Triple(StoreViewMode.LIST, Icons.AutoMirrored.Filled.ViewList, R.string.store_view_list),
            Triple(StoreViewMode.COVERS, Icons.Filled.GridView, R.string.store_view_covers),
        ).forEach { (value, icon, label) ->
            // The list button says how many games a row holds; pressed again while the list is shown, it goes to the other number.
            Box {
                GlassIconButton(onClick = { if (value == StoreViewMode.LIST && mode == StoreViewMode.LIST) onColumns() else onChosen(value) }, modifier = Modifier.size(48.dp)) {
                    Icon(icon, contentDescription = stringResource(label), tint = if (value == mode) KindBlue else LocalContentColor.current)
                }
                if (value == StoreViewMode.LIST && mode == StoreViewMode.LIST) {
                    Text(
                        text = columns.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                            .wrapContentSize(Alignment.Center),
                    )
                }
            }
        }
    }
}

/** A cover with the name over its bottom, and what matters at a glance on its top: VR, new, or still to come. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Cover(app: AndroidApp, owned: Boolean, isNew: Boolean, level: CompatLevel?, price: StorePrice?, sortValue: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).glass(shape).clip(shape).clickable(onClick = onClick)) {
        GameImage(
            url = app.cover ?: app.header ?: app.banner ?: "",
            fallbackUrl = app.header,
            moreFallbacks = listOfNotNull(app.capsule),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                .padding(start = 8.dp, end = 8.dp, top = 26.dp, bottom = 8.dp),
        ) {
            Text(app.name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // The mark of what players say stays at the bottom right; the price takes the room that is left at its left.
            if (price != null || level != null) {
                Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    price?.let { PricePill(it, onLight = false, compact = true, modifier = Modifier.align(Alignment.CenterStart).padding(end = if (level != null) 26.dp else 0.dp)) }
                    level?.let { CompatMark(it, size = 18.dp, modifier = Modifier.align(Alignment.CenterEnd)) }
                }
            }
        }
        if (owned) OwnedMark(Modifier.align(Alignment.TopEnd).padding(6.dp))
        FlowRow(Modifier.align(Alignment.TopStart).padding(6.dp).fillMaxWidth(0.8f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (app.vr) StoreChip(stringResource(R.string.store_filter_vr))
            if (isNew) StoreChip(stringResource(R.string.store_chip_new), accent = KindBlue)
            if (!app.installable) StoreChip(stringResource(R.string.store_chip_future), accent = StoreOrange)
            sortValue?.let { StoreChip(it, accent = KindBlue) }
        }
    }
}

private val LIST_ROW_HEIGHT = 96.dp

/**
 * One game on a card of frosted glass. With two columns the picture is at the left and the text at its right; with three or four the card is narrow, so the
 * picture is on top and the text under it, or the name and the figures would have no room. The cards of a row have the same height.
 */
@Composable
private fun GameRow(app: AndroidApp, owned: Boolean, isNew: Boolean, level: CompatLevel?, price: StorePrice?, deviceKind: String, columns: Int, expanded: Boolean, sortValue: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val stacked = columns >= 3
    val card = Modifier.fillMaxWidth().frosted(shape).clickable(onClick = onClick)
    androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides CardInk) {
    val picture: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier) {
            GameImage(
                url = app.header ?: app.capsule ?: app.cover ?: "",
                fallbackUrl = app.capsule,
                moreFallbacks = listOfNotNull(app.cover),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            // VR and "new" lie on the picture, thin; the mark of a game the account has, at its other corner.
            Row(Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (app.vr) StoreChip(stringResource(R.string.store_filter_vr))
                if (isNew) StoreChip(stringResource(R.string.store_chip_new), accent = KindBlue)
            }
            if (owned) OwnedMark(Modifier.align(Alignment.TopEnd).padding(6.dp))
            // What the list is placed by, at the bottom left, in blue, so the order can be read down the list.
            sortValue?.let { StoreChip(it, accent = KindBlue, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) }
            // The compatibility is a mark on the picture, as on the home; pointed at, it unfolds into its label.
            if (level != null) CompatBadge(level, deviceKind, expanded, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
    if (stacked) {
        Column(card) {
            picture(Modifier.fillMaxWidth().aspectRatio(460f / 215f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Two lines kept for the name, a short one included, so the cards of a row line up.
                Text(app.name, style = MaterialTheme.typography.labelLarge, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Rating(app, small = true, showCount = columns < 4, onLight = true)
                    Spacer(Modifier.weight(1f))
                    if (price != null) PricePill(price, compact = columns >= 4)
                }
            }
        }
    } else {
        Row(card.height(LIST_ROW_HEIGHT)) {
            // The picture is Steam's header, 460 by 215: as wide as it is high times that, so none of it is cut.
            picture(Modifier.width(LIST_ROW_HEIGHT * (460f / 215f)).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(app.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!app.installable) Text(stringResource(R.string.store_chip_future), style = MaterialTheme.typography.labelSmall, color = StoreOrange, modifier = Modifier.padding(end = 8.dp))
                    Rating(app, small = true, onLight = true)
                    Spacer(Modifier.weight(1f))
                    if (price != null) PricePill(price)
                }
            }
        }
    }
    }
}

/** The games on sale that the account does not have, as a low row of cards above the list. */
@Composable
private fun PromoRail(apps: List<AndroidApp>, prices: Map<Int, StorePrice>, levels: Map<Int, CompatLevel>, deviceKind: String, onPointed: (AndroidApp, Boolean) -> Unit, onOpen: (Int) -> Unit) {
    // The sales have their own title and their own row, with nothing behind them.
    Column(Modifier.padding(bottom = 4.dp).fillMaxWidth()) {
        Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.store_promos), style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps, key = { it.appid }) { app ->
                val shape = RoundedCornerShape(14.dp)
                Pointable({ on -> onPointed(app, on) }) { pointedNow ->
                    androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides CardInk) {
                        Column(Modifier.width(PROMO_WIDTH).frosted(shape).clickable { onOpen(app.appid) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(460f / 215f)) {
                                GameImage(
                                    url = app.header ?: app.capsule ?: app.cover ?: "",
                                    fallbackUrl = app.capsule,
                                    moreFallbacks = listOfNotNull(app.cover),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (app.vr) StoreChip(stringResource(R.string.store_filter_vr), modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                                // What the players of this device say, as on a cover of the home, and the price in one pill.
                                levels[app.appid]?.let { CompatBadge(it, deviceKind, pointedNow, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
                                prices[app.appid]?.let { PricePill(it, onLight = false, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) }
                            }
                            Text(app.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

private val PROMO_WIDTH = 250.dp

/** The filters that do not fit on a row of chips: the languages (in the text, the voices or the subtitles) and the genres, each in one dropdown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(open: Boolean, state: StoreUiState.Content, viewModel: StoreViewModel, onClose: () -> Unit) {
    SidePanel(
        open = open,
        title = stringResource(R.string.store_filters),
        closeLabel = stringResource(R.string.store_close),
        onClose = onClose,
        buttons = {
            // Each button takes half of the width.
            GlassButton(onClick = viewModel::onReset, enabled = !state.filters.isEmpty, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.store_reset)) }
            if (state.kept) {
                Button(onClick = viewModel::onKeepToggled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.store_kept))
                }
            } else {
                GlassButton(onClick = viewModel::onKeepToggled, enabled = !state.filters.isEmpty, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.store_keep)) }
            }
        },
    ) {
        PanelGroup(stringResource(R.string.store_group_languages)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageKind.entries.forEach { kind ->
                        GlassChip(
                            label = stringResource(
                                when (kind) {
                                    LanguageKind.TEXT -> R.string.store_language_text
                                    LanguageKind.VOICE -> R.string.store_language_voice
                                    LanguageKind.SUBTITLES -> R.string.store_language_subtitles
                                },
                            ),
                            onClick = { viewModel.onLanguageKindChosen(kind) },
                            accent = if (kind == state.filters.languageKind) KindBlue else null,
                        )
                    }
                }
                MultiSelect(
                    placeholder = stringResource(R.string.store_pick_languages),
                    options = state.languageOptions.map { Option(it.code, "${languageName(it.code)} (${it.games})", languageName(it.code)) },
                    selected = state.filters.languages,
                    onToggle = viewModel::onLanguageToggled,
                )
            }
        }
        PanelGroup(stringResource(R.string.store_group_types)) {
            MultiSelect(
                placeholder = stringResource(R.string.store_pick_types),
                options = state.typeOptions.map { Option(it.type, "${typeName(it.type)} (${it.games})", typeName(it.type)) },
                selected = state.filters.types,
                onToggle = viewModel::onTypeToggled,
            )
        }
        PanelGroup(stringResource(R.string.store_group_genres)) {
            MultiSelect(
                placeholder = stringResource(R.string.store_pick_genres),
                options = state.tagOptions.map { Option(it.id.toString(), "${it.name} (${it.games})", it.name) },
                selected = state.filters.tags.map { it.toString() }.toSet(),
                onToggle = { viewModel.onTagToggled(it.toInt()) },
            )
        }
    }
}

/** The name of a kind of app, in the plural, in the language of the device. */
@Composable
private fun typeName(type: String): String = stringResource(
    when (type) {
        "game" -> R.string.store_type_game
        "demo" -> R.string.store_type_demo
        "beta" -> R.string.store_type_beta
        "application" -> R.string.store_type_application
        else -> R.string.store_type_other
    },
)

/** A choice of a dropdown: the key it stands for, the line it has in the list, and the short name it has in the summary. */
private data class Option(val key: String, val line: String, val short: String)

/**
 * A dropdown where several things can be ticked, in one button instead of a long list of chips. The list opens against the button and stays open while
 * choosing; the button says what is ticked.
 */
@Composable
private fun MultiSelect(placeholder: String, options: List<Option>, selected: Set<String>, onToggle: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val chosen = options.filter { it.key in selected }
    val summary = when {
        chosen.isEmpty() -> placeholder
        chosen.size <= 2 -> chosen.joinToString(", ") { it.short }
        else -> chosen.take(2).joinToString(", ") { it.short } + " +" + (chosen.size - 2)
    }
    Box(Modifier.fillMaxWidth()) {
        GlassButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        BackdropMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onToggle(option.key) }.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = option.key in selected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        Text(option.line, modifier = Modifier.padding(end = 10.dp))
                    }
                }
            }
        }
    }
}

/** A language in the words of the device: "français" for `fr`, "portugais (Brésil)" for `pt-BR`. */
internal fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayName(Locale.getDefault()).replaceFirstChar { it.uppercase() }
