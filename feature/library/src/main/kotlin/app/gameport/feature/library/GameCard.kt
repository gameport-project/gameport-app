package app.gameport.feature.library

import androidx.compose.foundation.border
import app.gameport.core.designsystem.KindBlue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gameport.core.designsystem.AttentionBadge
import app.gameport.core.designsystem.UpdateBadge
import app.gameport.core.designsystem.CompatGlyph
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.Glyph
import app.gameport.core.designsystem.glass
import app.gameport.core.model.AppKind
import app.gameport.core.model.Game
import app.gameport.core.model.HoverAnimation
import app.gameport.core.model.Ownership
import app.gameport.core.model.PillSize
import androidx.compose.ui.zIndex

/** The red of a starred game's heart. */
internal val FavoriteRed = Color(0xFFE53935)

/**
 * Portrait capsule with the title on a gradient, so games without artwork stay readable. When
 * [showPill] is on, room is kept above the cover for the pill that names the game while it is highlighted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Staying pressed on the cover opens the menu of the game's actions. */
    onLongClick: (() -> Unit)? = null,
    needsAttention: Boolean = false,
    hasUpdate: Boolean = false,
    /** What the players who use this kind of device say of the game, once enough of them did. */
    compat: app.gameport.core.model.CompatLevel? = null,
    showTitle: Boolean = true,
    favorite: Boolean = false,
    hover: HoverAnimation = HoverAnimation.FULL,
    showPill: Boolean = false,
    pillSize: PillSize = PillSize.MEDIUM,
    /** The pill above this cover is being pointed at: the cover stays highlighted with it. */
    keepHighlighted: Boolean = false,
    /** Where the card is on screen, so the row can draw the pill above it ([onPositioned] gets the card's top-left corner). */
    onPositioned: (Offset) -> Unit = {},
    onHighlightChanged: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    // A VR controller ray or a mouse "hovers" without focusing, so both count as highlighting. The hover
    // area includes the pill above the cover, so pointing at the pill keeps it there.
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val pointedOrFocused = focused || hovered
    val highlighted = pointedOrFocused || keepHighlighted
    LaunchedEffect(pointedOrFocused) { onHighlightChanged(pointedOrFocused) }
    // Full: the cover zooms and the ring drifts out around it. Reduced: no zoom, no drift, the ring only fades in. None: the ring just appears.
    val scale by animateFloatAsState(if (highlighted && hover == HoverAnimation.FULL) FOCUS_SCALE else 1f, label = "cover focus")
    val ring by animateFloatAsState(
        if (highlighted) 1f else 0f,
        when (hover) {
            HoverAnimation.FULL -> spring(dampingRatio = 0.6f, stiffness = 300f)
            HoverAnimation.REDUCED -> tween(durationMillis = 150)
            HoverAnimation.NONE -> snap()
        },
        label = "cover ring",
    )
    val ringStart = if (hover == HoverAnimation.FULL) RING_GAP_START else RING_GAP
    val ringColor = MaterialTheme.colorScheme.primary
    // The name moves to the pill while it is shown.
    val titleOnCover = showTitle && !(showPill && highlighted)

    Column(
        modifier
            .hoverable(hoverSource)
            .onFocusChanged { focused = it.hasFocus }
            .onGloballyPositioned { onPositioned(it.positionInRoot()) },
    ) {
        // The room above the cover is kept for the pill, which the row draws there (see Rail), free of this card's bounds.
        if (showPill) Spacer(Modifier.fillMaxWidth().height(pillSize.slotDp.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CAPSULE_RATIO)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind {
                    if (ring > 0.01f) {
                        val stroke = RING_STROKE.toPx()
                        val gap = (ringStart + (RING_GAP - ringStart) * ring).toPx()
                        val inset = gap + stroke / 2
                        drawRoundRect(
                            color = ringColor.copy(alpha = ring.coerceIn(0f, 1f)),
                            topLeft = Offset(-inset, -inset),
                            size = Size(size.width + 2 * inset, size.height + 2 * inset),
                            cornerRadius = CornerRadius(COVER_CORNER.toPx() + inset),
                            style = Stroke(stroke),
                        )
                    }
                }
                .clip(RoundedCornerShape(COVER_CORNER))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            GameImage(url = game.capsuleUrl, fallbackUrl = game.capsuleFallbacks.firstOrNull(), moreFallbacks = game.capsuleFallbacks.drop(1), contentDescription = null, modifier = Modifier.fillMaxSize())
            if (titleOnCover) {
                Box(Modifier.matchParentSizeBottomFade())
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // With the mark of what players say, the name rises above it.
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, top = 10.dp, bottom = if (compat != null) 42.dp else 10.dp, end = if (favorite && compat == null) 32.dp else 10.dp),
                )
            }
            if (favorite || compat != null) {
                // The mark of what players say, as small as the other marks of the cover, and the heart of a favourite beside it.
                Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    compat?.let { CompatMark(it, size = 18.dp) }
                    if (favorite) Icon(Icons.Filled.Favorite, contentDescription = null, tint = FavoriteRed, modifier = Modifier.size(18.dp))
                }
            }
            Badges(game, Modifier.align(Alignment.TopEnd).padding(8.dp))
            // Small, so they do not hide the artwork: orange for what needs attention, green for an update.
            if (needsAttention || hasUpdate) {
                Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (needsAttention) AttentionBadge(size = 18.dp)
                    if (hasUpdate) UpdateBadge(size = 18.dp)
                }
            }
        }
    }
}

/**
 * The game's name above its cover, with a play icon when the game can be started from here. It is drawn by
 * the row, over the covers, so a name longer than the cover simply carries on to the right. [onHeld] says
 * whether it is being pointed at or has the focus, which keeps it (and its cover) shown.
 */
@Composable
internal fun PlayPill(name: String, size: PillSize, canPlay: Boolean, onPlay: () -> Unit, onHeld: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val pointed by source.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(pointed, focused) { onHeld(pointed || focused) }
    Row(
        modifier
            .hoverable(source)
            .onFocusChanged { focused = it.isFocused }
            .then(if (pointed) Modifier.clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)) else Modifier.glass(shape))
            .clickable(onClick = onPlay)
            .padding(start = 10.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (pointed) MaterialTheme.colorScheme.onPrimary else Color.White
        if (canPlay) Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.library_play), tint = contentColor, modifier = Modifier.size(size.iconDp.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = size.textSp.sp),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = if (canPlay) 4.dp else 2.dp),
        )
    }
}

@Composable
internal fun Badges(game: Game, modifier: Modifier = Modifier) {
    val kind = when {
        game.androidBuild?.isVr == true -> stringResource(R.string.library_kind_vr)
        game.ownership == Ownership.FAMILY_SHARED -> stringResource(R.string.library_family_shared)
        else -> null
    }
    // A demo or a beta says so, in blue: it is not the full game.
    val notFull = when (game.kind) {
        AppKind.DEMO -> stringResource(R.string.library_label_demo)
        AppKind.BETA -> stringResource(R.string.library_label_beta)
        AppKind.GAME -> null
    }
    if (kind == null && notFull == null) return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        kind?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        notFull?.let {
            val shape = RoundedCornerShape(50)
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFD6ECFB),
                modifier = Modifier
                    .clip(shape)
                    .background(KindBlue.copy(alpha = 0.30f))
                    .border(1.dp, KindBlue.copy(alpha = 0.60f), shape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun Modifier.matchParentSizeBottomFade(): Modifier = this
    .fillMaxSize()
    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000)))

private const val CAPSULE_RATIO = 2f / 3f
private const val FOCUS_SCALE = 1.03f
private val COVER_CORNER = 14.dp
private val RING_STROKE = 1.5.dp
private val RING_GAP = 2.5.dp
private val RING_GAP_START = 0.dp


/**
 * The label of the page of the game reduced to its mark (the same mark): a check, an exclamation mark or a cross in the tint of the label, in a
 * circle as big as the other marks of a cover.
 */
@Composable
private fun CompatMark(level: app.gameport.core.model.CompatLevel, size: androidx.compose.ui.unit.Dp) {
    val (colour, glyph, words) = when (level) {
        app.gameport.core.model.CompatLevel.WORKS -> Triple(Color(0xFF66BB6A), Glyph.CHECK, R.string.card_works)
        app.gameport.core.model.CompatLevel.OFFLINE_ONLY -> Triple(Color(0xFFFFB74D), Glyph.NO_NETWORK, R.string.card_offline_only)
        app.gameport.core.model.CompatLevel.MIXED -> Triple(Color(0xFFFFB74D), Glyph.EXCLAMATION, R.string.card_mixed)
        app.gameport.core.model.CompatLevel.FAILS -> Triple(Color(0xFFE57373), Glyph.CROSS, R.string.card_fails)
    }
    val description = stringResource(words)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colour.copy(alpha = 0.22f))
            .border(1.dp, colour.copy(alpha = 0.65f), CircleShape)
            .semantics { contentDescription = description },
    ) {
        // The mark is a bit under half the height of the circle, centred in it.
        CompatGlyph(glyph, colour, height = size * (if (glyph == Glyph.NO_NETWORK) 0.5f else 0.46f), modifier = Modifier.align(Alignment.Center))
    }
}
