package app.gameport.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gameport.core.designsystem.AttentionBadge
import app.gameport.core.designsystem.GameImage
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
@Composable
internal fun GameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    needsAttention: Boolean = false,
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
                .clickable(onClick = onClick),
        ) {
            GameImage(url = game.capsuleUrl, fallbackUrl = game.headerUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            if (titleOnCover) {
                Box(Modifier.matchParentSizeBottomFade())
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = if (favorite) 32.dp else 10.dp),
                )
            }
            if (favorite) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = FavoriteRed, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(18.dp))
            }
            Badges(game, Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (needsAttention) AttentionBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
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
    val labels = listOfNotNull(
        when {
            game.androidBuild?.isVr == true -> stringResource(R.string.library_kind_vr)
            game.ownership == Ownership.FAMILY_SHARED -> stringResource(R.string.library_family_shared)
            else -> null
        },
        // A demo or a beta says so: it is not the full game.
        when (game.kind) {
            AppKind.DEMO -> stringResource(R.string.library_label_demo)
            AppKind.BETA -> stringResource(R.string.library_label_beta)
            AppKind.GAME -> null
        },
    )
    if (labels.isEmpty()) return
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
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

