package app.gameport.feature.library

import app.gameport.core.designsystem.StatusNoticeWidth
import app.gameport.core.designsystem.ConnectionNotice
import app.gameport.core.model.SteamConnection
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.ErrorOutline
import app.gameport.core.designsystem.GlassIconButton
import android.view.KeyEvent as AndroidKeyEvent
import android.content.Intent
import androidx.compose.animation.Crossfade
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import app.gameport.core.model.ContinueScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import app.gameport.core.model.DisplaySettings
import app.gameport.core.model.Game
import app.gameport.core.model.LibrarySort
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GamePortLogo
import androidx.compose.foundation.layout.size
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.glass
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import app.gameport.core.designsystem.GlassSearchField
import app.gameport.core.designsystem.PillTabs

@Composable
fun LibraryScreen(
    onGameClick: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LibraryContent(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onTabSelected = viewModel::onTabSelected,
        onSortSelected = viewModel::onSortSelected,
        onFiltersChanged = viewModel::onFiltersChanged,
        // The game's page explains first what it must (a missing permission), so it opens instead when the game cannot start straight away.
        onPlay = { game -> viewModel.playIntent(game)?.let(context::startActivity) ?: onGameClick(game.appId) },
        onGameClick = onGameClick,
        onOpenDownloads = onOpenDownloads,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onFiltersChanged: (LibraryFilters) -> Unit,
    onPlay: (Game) -> Unit,
    onGameClick: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
        when (uiState) {
            LibraryUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is LibraryUiState.Content -> Shelf(uiState, onQueryChanged, onTabSelected, onSortSelected, onFiltersChanged, onPlay, onGameClick, onOpenDownloads, onOpenSettings)
        }
    }
}

/**
 * The home: the highlighted game's banner, then rows of covers (the last games started, the starred
 * ones, all of them) scrolling down. The scroll settles on a row, so a cover is never left cut in the
 * middle. The artwork of the highlighted game fills the screen behind; the highlight follows the
 * controller ray or gamepad focus, and stays on the last game pointed at.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Shelf(
    state: LibraryUiState.Content,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onFiltersChanged: (LibraryFilters) -> Unit,
    onPlay: (Game) -> Unit,
    onGameClick: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var highlightedId by remember(state.tab, state.query) { mutableStateOf<Int?>(null) }
    val highlighted = remember(state, highlightedId) {
        (state.continueGames + state.favoriteGames + state.allGames).firstOrNull { it.appId == highlightedId }
            ?: state.lastPlayed
            ?: state.allGames.firstOrNull()
    }
    // The list below the header changes with the tab and the search: it starts again from its top, instead of staying at a position that no longer means anything.
    LaunchedEffect(state.tab, state.query) { listState.scrollToItem(0) }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    val display = state.appearance

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusTarget()
            .onPreviewKeyEvent { event ->
                if (!state.showTabs || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_BUTTON_L1 -> onTabSelected(LibraryTab.VR).let { true }
                    AndroidKeyEvent.KEYCODE_BUTTON_R1 -> onTabSelected(LibraryTab.FLAT).let { true }
                    else -> false
                }
            },
    ) {
        if (display.backdrop) Backdrop(highlighted, display.backdropStrength)

        Column(Modifier.fillMaxSize()) {
            Header(state, onQueryChanged, onTabSelected, { filtersOpen = true }, onOpenDownloads, onOpenSettings)

            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.continueGames.isNotEmpty()) {
                    item(key = "continue") { Rail(RowKind.CONTINUE, state.continueGames, state, onGameClick, onPlay) { highlightedId = it } }
                }
                if (state.favoriteGames.isNotEmpty()) {
                    item(key = "favorites") { Rail(RowKind.FAVORITES, state.favoriteGames, state, onGameClick, onPlay) { highlightedId = it } }
                }
                if (state.allGames.isEmpty()) {
                    item(key = "empty") {
                        val message = when {
                            state.isScanning -> R.string.library_scanning
                            state.libraryIsEmpty -> R.string.library_empty
                            else -> R.string.library_no_match
                        }
                        Text(stringResource(message), modifier = Modifier.padding(32.dp))
                    }
                } else {
                    item(key = "all") {
                        Rail(
                            kind = RowKind.ALL,
                            games = state.allGames,
                            state = state,
                            onGameClick = onGameClick,
                            onPlay = onPlay,
                            trailing = { SortPill(display.sort, onSortSelected) },
                            onHighlight = { highlightedId = it },
                        )
                    }
                }
            }
        }

        // Over the top edge, so showing or hiding it never moves the content below.
        if (state.isScanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }

        FilterOverlay(open = filtersOpen, filters = state.filters, onChange = onFiltersChanged, onClose = { filtersOpen = false })
    }
}

/** The artwork of [game] behind the library: the weaker [strength] is, the darker and blurrier it gets. */
@Composable
private fun Backdrop(game: Game?, strength: Int) {
    // 50 is the look the library always had; below it the art is dimmed more and blurred, above it clearer.
    val dim = ((100 - strength) / 50f).coerceAtLeast(0.1f)
    val blur = (((50 - strength).coerceAtLeast(0)) * 0.4f).dp
    Crossfade(targetState = game, label = "backdrop", modifier = Modifier.fillMaxSize()) { shown ->
        if (shown != null) {
            GameImage(url = shown.heroUrl, fallbackUrl = shown.headerUrl, contentDescription = null, modifier = Modifier.fillMaxSize().blur(blur))
        }
    }
    fun shade(alpha: Float) = Color(0xFF101418).copy(alpha = (alpha * dim).coerceIn(0f, 1f))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to shade(0.8f), 0.45f to shade(0.4f), 1f to shade(0.95f))))
}

/** A titled row of covers. */
@Composable
private fun Rail(
    kind: RowKind,
    games: List<Game>,
    state: LibraryUiState.Content,
    onGameClick: (Int) -> Unit,
    onPlay: (Game) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onHighlight: (Int) -> Unit,
) {
    val display = state.appearance
    // On a headset the "continue" row says whose games it lists: both tabs', or the tab shown.
    val title = if (kind == RowKind.CONTINUE && state.showTabs) {
        stringResource(
            when {
                display.continueScope == ContinueScope.ALL -> R.string.library_row_continue_all
                state.tab == LibraryTab.VR -> R.string.library_row_continue_vr
                else -> R.string.library_row_continue_flat
            },
        )
    } else {
        stringResource(kind.title)
    }
    // The zoom and the ring of the highlighted cover reach past its edges; the row keeps that room so nothing is cut or covers the title above.
    val overshoot = (display.coverSize.dp * 1.5f * ZOOM_OVERSHOOT + RING_ROOM_DP).dp

    // The pill that names the highlighted game is drawn here, over the covers and not inside the card, so a long name is
    // never cut at the card's edge. It stays while the card or the pill itself is pointed at or has the focus.
    val scope = rememberCoroutineScope()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val cardPositions = remember { mutableStateMapOf<Int, Offset>() }
    var activeId by remember { mutableStateOf<Int?>(null) }
    var pillHeld by remember { mutableStateOf(false) }
    var hide by remember { mutableStateOf<Job?>(null) }
    fun hideSoon(id: Int) {
        hide?.cancel()
        // A short delay lets the pointer travel from the cover to the pill without the pill vanishing on the way.
        hide = scope.launch {
            delay(PILL_HIDE_DELAY_MS)
            if (!pillHeld && activeId == id) activeId = null
        }
    }

    Box(Modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        Column {
            Row(
                Modifier.padding(start = 32.dp, end = 32.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                trailing?.invoke()
            }
            LazyRow(
                // With the pill, the room above the covers is the pill's own.
                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = if (display.heroBanner) 0.dp else overshoot, bottom = overshoot),
                horizontalArrangement = Arrangement.spacedBy(display.coverSpacing.dp.dp),
            ) {
                items(games, key = { it.appId }) { game ->
                    GameCard(
                        game = game,
                        onClick = { onGameClick(game.appId) },
                        needsAttention = game.appId in state.attention,
                        showTitle = display.coverTitles,
                        favorite = game.appId in state.favoriteIds,
                        hover = display.hoverAnimation,
                        showPill = display.heroBanner,
                        pillSize = display.pillSize,
                        keepHighlighted = pillHeld && activeId == game.appId,
                        onPositioned = { cardPositions[game.appId] = it },
                        onHighlightChanged = { active ->
                            if (active) {
                                hide?.cancel()
                                activeId = game.appId
                                onHighlight(game.appId)
                            } else {
                                hideSoon(game.appId)
                            }
                        },
                        modifier = Modifier.width(display.coverSize.dp.dp),
                    )
                }
            }
        }
        if (display.heroBanner) {
            val shown = games.firstOrNull { it.appId == activeId }
            val position = shown?.let { cardPositions[it.appId] }
            if (shown != null && position != null) {
                val installed = shown.appId in state.installedIds
                PlayPill(
                    name = shown.name,
                    size = display.pillSize,
                    canPlay = installed,
                    // Starting the game from the library, or its page when it is not installed.
                    onPlay = { if (installed) onPlay(shown) else onGameClick(shown.appId) },
                    onHeld = { held ->
                        pillHeld = held
                        if (held) hide?.cancel() else hideSoon(shown.appId)
                    },
                    modifier = Modifier.offset { IntOffset((position.x - origin.x).roundToInt(), (position.y - origin.y).roundToInt()) },
                )
            }
        }
    }
}

/** How long the pill stays after the pointer leaves the cover, for the trip to the pill. */
private const val PILL_HIDE_DELAY_MS = 180L

/** The rows of the home. */
private enum class RowKind(val title: Int) {
    CONTINUE(R.string.library_row_continue),
    FAVORITES(R.string.library_row_favorites),
    ALL(R.string.library_row_all),
}

/** The order of the "all games" row, chosen in a small menu. */
@Composable
private fun SortPill(sort: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun label(value: LibrarySort) = when (value) {
        LibrarySort.NAME -> R.string.library_sort_name
        LibrarySort.RECENTLY_PLAYED -> R.string.library_sort_played
        LibrarySort.RECENTLY_INSTALLED -> R.string.library_sort_installed
    }
    Box {
        // Low on purpose: the pill above a highlighted cover sits right under this row and must not touch it.
        Row(
            Modifier
                .glass(RoundedCornerShape(50))
                .clickable { open = true }
                .padding(start = 10.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.library_sort, stringResource(label(sort))), style = MaterialTheme.typography.labelMedium, color = Color.White)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(text = { Text(stringResource(label(option))) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun Header(
    state: LibraryUiState.Content,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GamePortLogo(Modifier.size(40.dp))
            GlassSearchField(
                value = state.query,
                onValueChange = onQueryChanged,
                placeholder = stringResource(R.string.library_search_hint),
                modifier = Modifier.weight(1f),
            )
            // The number of filters narrowing the list sits on the button, so a filter left on is never a mystery.
            Box {
                GlassIconButton(onClick = onOpenFilters) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.library_filters))
                }
                val active = state.filters.activeCount
                if (active > 0) {
                    Text(
                        text = active.toString(),
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
            GlassIconButton(onClick = onOpenDownloads) {
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.library_downloads))
            }
            GlassIconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.library_settings))
            }
        }
        if (state.showTabs || state.connection != SteamConnection.ONLINE) {
            val tabs: @Composable () -> Unit = {
                PillTabs(
                    labels = listOf(stringResource(R.string.library_tab_vr), stringResource(R.string.library_tab_flat)),
                    selectedIndex = state.tab.ordinal,
                    onSelect = { onTabSelected(LibraryTab.entries[it]) },
                    modifier = Modifier.width(StatusNoticeWidth),
                )
            }
            val offline: @Composable () -> Unit = { ConnectionNotice(state.connection, onClick = onOpenSettings) }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Wide enough, the tabs stay centred and the notice sits at the left; otherwise it goes below the tabs.
                if (maxWidth >= StatusNoticeWidth * 3 + 64.dp) {
                    Box(Modifier.fillMaxWidth().height(44.dp)) {
                        Box(Modifier.align(Alignment.CenterStart)) { offline() }
                        if (state.showTabs) Box(Modifier.align(Alignment.Center)) { tabs() }
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.showTabs) tabs()
                        offline()
                    }
                }
            }
        }
        if (state.updates.isNotEmpty()) {
            GlassChip(
                label = if (state.updates.size == 1) stringResource(R.string.library_update_one, state.updates.first())
                else stringResource(R.string.library_update_many, state.updates.size),
                onClick = onOpenDownloads,
                accent = Color(0xFF66BB6A),
            )
        }
    }
}


/** How much of its height a highlighted cover grows by, per side, and the room for the ring around it. */
private const val ZOOM_OVERSHOOT = 0.016f
private const val RING_ROOM_DP = 8f
