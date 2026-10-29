package app.gameport.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** One stop of a guided tour: what to point at, and the words. */
data class TourStep(val target: String, @StringRes val title: Int, @StringRes val text: Int, val demo: String? = null)

/** What a demonstration may do on the screen: show a tap on a part, as a finger would, while the page changes as that tap would change it. */
interface TourDemoScope {
    fun tap(target: String)
}

/** Where each part of the screen that a tour can point at is, in the window. A part leaves when it leaves the screen. */
class TourTargets {
    val bounds = mutableStateMapOf<String, Rect>()

    /** What the pages offer to act out when a step names it, by name. */
    val demos = mutableMapOf<String, suspend TourDemoScope.() -> Unit>()

    /** What puts a page back as it was after its demonstrations, by the same name; run when the tour ends, whatever the way it ends. */
    val restores = mutableMapOf<String, () -> Unit>()

    /** The demonstrations that ran, so only what was changed is put back. */
    val ran = mutableSetOf<String>()
}

/**
 * Lets the tour of this page act out [block] when a step names [id]. What it changes is put back by [restore] when the tour ends, by its last step or because
 * the player left, so a demonstration never leaves a setting changed.
 */
@Composable
fun TourDemo(id: String, restore: (() -> Unit)? = null, block: suspend TourDemoScope.() -> Unit) {
    val targets = LocalTourTargets.current
    val latest = androidx.compose.runtime.rememberUpdatedState(block)
    val latestRestore = androidx.compose.runtime.rememberUpdatedState(restore)
    DisposableEffect(id, targets) {
        targets.demos[id] = { latest.value(this) }
        targets.restores[id] = { latestRestore.value?.invoke() }
        onDispose {
            targets.demos.remove(id)
            targets.restores.remove(id)
        }
    }
}

val LocalTourTargets = staticCompositionLocalOf { TourTargets() }

/** Marks a part of the screen as something a guided tour can point at. */
fun Modifier.tourTarget(id: String): Modifier = composed {
    val targets = LocalTourTargets.current
    DisposableEffect(id, targets) { onDispose { targets.bounds.remove(id) } }
    onGloballyPositioned { if (it.isAttached) targets.bounds[id] = it.boundsInRoot() }
}

/**
 * A guided tour over the screen: the screen dims, the part of a step stays lit with a ring, and a card on the player's background says what it is.
 * A step whose part is not on the screen (a button a device does not have) is left out. [onFinish] says whether the player went to the end.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TourOverlay(steps: List<TourStep>, lastStepLabel: String? = null, onFinish: (completed: Boolean) -> Unit) {
    val targets = LocalTourTargets.current
    // The parts place themselves a moment after the page appears: the steps are chosen once they have.
    var chosen by remember(steps) { mutableStateOf<List<TourStep>?>(null) }
    LaunchedEffect(steps) {
        // A page may still be loading its data: wait for its first part to show up, then for the others to settle.
        var waited = 0L
        while (waited < LOADING_MS && steps.none { targets.bounds.containsKey(it.target) }) {
            delay(100)
            waited += 100
        }
        delay(PLACING_MS)
        chosen = steps.filter { targets.bounds.containsKey(it.target) }
    }
    val list = chosen ?: return
    if (list.isEmpty()) {
        // A page with nothing to point at (still loading, or empty) is passed over, so the complete tour goes on to the next page.
        LaunchedEffect(Unit) { onFinish(true) }
        return
    }
    var index by remember(list) { mutableIntStateOf(0) }
    val step = list[index]
    val last = index == list.lastIndex
    val target = targets.bounds[step.target]
    val next: () -> Unit = { if (last) onFinish(true) else index++ }
    // Leaving is always possible, and asks first: a tap on the wrong button would otherwise end a tour that is only half read.
    var leaving by remember { mutableStateOf(false) }
    BackHandler(enabled = !leaving) { leaving = true }

    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    // The overlay keeps the focus, so a controller or keyboard never reaches the page behind it; it comes back after the question about leaving.
    LaunchedEffect(index, leaving) { if (!leaving) runCatching { focus.requestFocus() } }
    // A step may act out something on the page behind it, with a tap shown where a finger would be.
    var tap by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    LaunchedEffect(index) {
        val demo = step.demo?.let { targets.demos[it] } ?: return@LaunchedEffect
        val scope = object : TourDemoScope {
            override fun tap(target: String) {
                targets.bounds[target]?.let { tap = it.center to ((tap?.second ?: 0) + 1) }
            }
        }
        delay(450)
        targets.ran += step.demo
        demo(scope)
    }
    // The tour is over, to its last step or because the player left: whatever the demonstrations changed is put back.
    DisposableEffect(Unit) {
        onDispose {
            targets.ran.toList().forEach { targets.restores[it]?.invoke() }
            targets.ran.clear()
        }
    }
    val tapRing = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(tap?.second) {
        if (tap != null) {
            tapRing.snapTo(0f)
            tapRing.animateTo(1f, androidx.compose.animation.core.tween(600))
        }
    }
    var window by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var card by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { window = it }
            .focusRequester(focus)
            .focusTarget()
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> next().let { true }
                    Key.DirectionLeft -> (if (index > 0) index-- else Unit).let { true }
                    else -> false
                }
            },
    ) {
        val pad = with(density) { RING_PADDING.toPx() }
        // A part scrolled out of the screen is not lit: the card goes to the middle and says what it is.
        val lit = target?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }?.takeIf { it.bottom > 0 && it.top < window.height }
        val ring = MaterialTheme.colorScheme.primary
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            // A step that acts something out on the page lets it show through.
            drawRect(Color.Black.copy(alpha = if (step.demo != null) 0.4f else 0.72f))
            if (lit != null) {
                val corner = CornerRadius(with(density) { 16.dp.toPx() })
                drawRoundRect(Color.Black, lit.topLeft, lit.size, corner, blendMode = BlendMode.Clear)
                drawRoundRect(ring, lit.topLeft, lit.size, corner, style = Stroke(with(density) { 2.dp.toPx() }))
            }
            tap?.let { (at, _) ->
                val radius = with(density) { (14.dp + 26.dp * tapRing.value).toPx() }
                drawCircle(Color.White.copy(alpha = 0.9f * (1f - tapRing.value)), radius, at)
                drawCircle(Color.White.copy(alpha = 0.85f * (1f - tapRing.value)), with(density) { 8.dp.toPx() }, at)
            }
        }
        // The card goes under the lit part when there is room, above it otherwise, and in the middle when nothing is lit.
        val margin = with(density) { 16.dp.toPx() }
        val offset = run {
            val x = lit?.let { (it.center.x - card.width / 2f).coerceIn(margin, (window.width - card.width - margin).coerceAtLeast(margin)) }
                ?: ((window.width - card.width) / 2f)
            val y = when {
                lit == null -> (window.height - card.height) / 2f
                lit.bottom + margin + card.height <= window.height - margin -> lit.bottom + margin
                lit.top - margin - card.height >= margin -> lit.top - margin - card.height
                else -> (window.height - card.height) / 2f
            }
            IntOffset(x.roundToInt(), y.roundToInt())
        }
        val shape = RoundedCornerShape(24.dp)
        // The card is not on a surface, so the color of its text is set: black would be left on the dark background.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .offset { offset }
                .onSizeChanged { card = it }
                // The card is as wide as its text asks, up to the screen, so a long button label never wraps: the card grows with it.
                .widthIn(max = with(density) { (window.width - 2 * margin).coerceAtLeast(0f).toDp() })
                .clip(shape)
                .background(Brush.linearGradient(LocalBackdropColors.current.toList()))
                .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.tour_progress, index + 1, list.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(step.title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.widthIn(max = TEXT_WIDTH))
            Text(stringResource(step.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = TEXT_WIDTH))
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { leaving = true }) { Text(stringResource(R.string.tour_skip)) }
                if (index > 0) {
                    OutlinedButton(onClick = { index-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text(stringResource(R.string.tour_back), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Button(onClick = next) {
                    // On the last step the button says where it goes when another page follows, so it never reads as the end of everything.
                    Text(lastStepLabel.takeIf { last } ?: stringResource(if (last) R.string.tour_done else R.string.tour_next), maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 6.dp))
                    Icon(if (last && lastStepLabel == null) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
        }
        if (leaving) {
            BackdropDialog(
                onDismissRequest = { leaving = false },
                title = { Text(stringResource(R.string.tour_leave_title)) },
                text = { Text(stringResource(R.string.tour_leave_text)) },
                confirmButton = { Button(onClick = { onFinish(false) }) { Text(stringResource(R.string.tour_leave)) } },
                dismissButton = { OutlinedButton(onClick = { leaving = false }) { Text(stringResource(R.string.tour_continue)) } },
            )
        }
    }
}

/** How long the parts of a page are given to place themselves before a tour picks its steps. */
private const val PLACING_MS = 500L

/** The longest a tour waits for a page to show its first part. */
private const val LOADING_MS = 4_000L

/** How wide the words of a card run before they wrap; the buttons under them may make the card wider. */
private val TEXT_WIDTH = 380.dp

/** How far the ring stands from the part it surrounds. */
private val RING_PADDING = 6.dp

/** Starts the guided tour of a page; set once at the root of the app, so a page does not need to know how tours run. */
val LocalTourStarter = staticCompositionLocalOf<(app.gameport.core.model.TourKind) -> Unit> { {} }

/** The help button of a page: a round question mark that starts the tour of that page. */
@Composable
fun TourHelpButton(kind: app.gameport.core.model.TourKind, modifier: Modifier = Modifier) {
    val start = LocalTourStarter.current
    GlassIconButton(onClick = { start(kind) }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.tour_help))
    }
}

/** The names of the parts of the screen the tours point at, shared by the pages that carry them and the tours that list them. */
object TourIds {
    const val HOME_TABS = "home_tabs"
    const val HOME_NEW = "home_new"
    const val HOME_ALL = "home_all"
    const val HOME_SEARCH = "home_search"
    const val HOME_FILTERS = "home_filters"
    const val HOME_STORE = "home_store"
    const val HOME_DOWNLOADS = "home_downloads"
    const val HOME_COMPAT = "home_compat"
    const val HOME_SETTINGS = "home_settings"

    const val GAME_LABELS = "game_labels"
    const val GAME_ACTIONS = "game_actions"
    const val GAME_SAVES = "game_saves"
    const val GAME_REPORT = "game_report"
    const val GAME_COMPAT = "game_compat"
    const val GAME_CONTROLLERS = "game_controllers"
    const val GAME_SETTINGS = "game_settings"
    const val GAME_ISSUES = "game_issues"
    const val GAME_INFO = "game_info"
    const val GAME_MENU = "game_menu"
    const val GAME_NOTES = "game_notes"

    const val STORE_TABS = "store_tabs"
    const val STORE_SEARCH = "store_search"
    const val STORE_FILTERS = "store_filters"
    const val STORE_SORT = "store_sort"
    const val STORE_VIEW = "store_view"
    const val STORE_VIEW_LIST = "store_view_list"
    const val STORE_VIEW_COVERS = "store_view_covers"
    const val DEMO_STORE_LIST = "demo_store_list"
    const val DEMO_STORE_COLUMNS = "demo_store_columns"
    const val DEMO_STORE_COLUMNS_AGAIN = "demo_store_columns_again"
    const val DEMO_STORE_COVERS = "demo_store_covers"
    const val STORE_STATE = "store_state"
    const val STORE_LIST = "store_list"

    const val STOREGAME_LABELS = "storegame_labels"
    const val STOREGAME_BUTTONS = "storegame_buttons"
    const val STOREGAME_DETAILS = "storegame_details"

    const val TABLE_SEARCH = "table_search"
    const val TABLE_FILTERS = "table_filters"
    const val TABLE_CARD = "table_card"
    const val TABLE_UNFOLD = "table_unfold"

    const val DOWNLOADS_HEADER = "downloads_header"
    const val DOWNLOADS_UPDATES = "downloads_updates"
    const val DOWNLOADS_ENTRY = "downloads_entry"

    const val GAMECOMPAT_FRAME = "gamecompat_frame"
    const val GAMECOMPAT_RECENTER = "gamecompat_recenter"

    const val CONTROLLERS_SWITCH = "controllers_switch"
    const val CONTROLLERS_CARDS = "controllers_cards"
    const val CONTROLLERS_RESET = "controllers_reset"

    const val SETTINGS_CATEGORIES = "settings_categories"
    const val SETTINGS_PAGE = "settings_page"
    const val SETTINGS_APPEARANCE_ITEM = "settings_appearance_item"
    const val SETTINGS_HELP_ITEM = "settings_help_item"
    const val DEMO_SETTINGS_LOOK = "demo_settings_look"
    const val DEMO_SETTINGS_HELP = "demo_settings_help"

    const val SAVES_REFRESH = "saves_refresh"
    const val SAVES_SUMMARY = "saves_summary"
    const val SAVES_FILES = "saves_files"
    const val SAVES_ACTIONS = "saves_actions"
}

/**
 * The moment between two pages of the complete tour: the screen dims, the mark of the next page grows in the middle with its name, and the page opens
 * behind it. [onDone] is called when it is over.
 */
@Composable
fun TourTransition(caption: String, title: String, icon: @Composable () -> Unit, onDone: () -> Unit) {
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        grow.animateTo(1f, androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        delay(700)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f * grow.value.coerceAtMost(1f))).pointerInput(Unit) { detectTapGestures { } }, Alignment.Center) {
        Column(
            Modifier.graphicsLayer { scaleX = 0.5f + 0.5f * grow.value; scaleY = 0.5f + 0.5f * grow.value; alpha = grow.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(120.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = 0.14f)).border(2.dp, Color.White.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                Alignment.Center,
            ) { icon() }
            Text(caption, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
    }
}
