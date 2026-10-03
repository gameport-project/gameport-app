package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
import app.gameport.core.designsystem.GlassBorder
import app.gameport.core.designsystem.GoldTrophy
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassButton
import app.gameport.core.designsystem.glass
import app.gameport.core.model.Achievement
import app.gameport.core.model.AchievementArtwork
import app.gameport.core.model.AchievementList
import java.text.DateFormat
import kotlinx.coroutines.delay
import java.util.Date

/** How many of the latest unlocked achievements the game page shows. */
private const val PREVIEW_COUNT = 3

/**
 * The height of one achievement of the strip, whatever is open: the picture and its margin. The text is kept to one line for the name and
 * one for what it asks, so the line never grows while an item unfolds.
 */
private val STRIP_ITEM_HEIGHT = 52.dp

/** How long a picture must be pointed at or focused before it opens. */
private const val OPEN_DELAY_MS = 100L

/** From this width the latest achievements share one line. */
private val STRIP_MIN_WIDTH = 480.dp

/** The game page's glance at the achievements: how many are unlocked and the latest ones, with a way to the full list. */
@Composable
internal fun AchievementsCard(list: AchievementList, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val recent = list.recent(PREVIEW_COUNT)
    Column(modifier.glass(RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldTrophy(26.dp)
            Text(stringResource(R.string.achievements_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.achievements_count, list.unlockedCount, list.total), style = MaterialTheme.typography.titleMedium)
        }
        AchievementsProgress(list)
        if (recent.isEmpty()) {
            Text(stringResource(R.string.achievements_none_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.achievements_latest), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            // Wide (landscape): one line, the latest unlocked open and the others reduced to their picture, which open when pointed at or focused.
            // Narrow: the three of them one under the other.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= STRIP_MIN_WIDTH) {
                    LatestStrip(list.appId, recent)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { recent.forEach { AchievementRow(list.appId, it, iconSize = 44.dp) } }
                }
            }
        }
        GlassButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.achievements_see_all))
        }
    }
}

/**
 * The latest unlocked achievements on one line, in order. One is open, filling what the others leave, with its name, what it asks for
 * and its date; the others are only their picture. Pointing at a picture (a controller ray or a mouse) or moving the focus to it (a gamepad)
 * opens that one and closes the one that was open, the widths easing from one to the other. The one pointed at or focused has the thin
 * border of the card that holds them.
 */
@Composable
private fun LatestStrip(appId: Int, items: List<Achievement>) {
    var open by remember(items) { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val closed = STRIP_ITEM_HEIGHT
        val openWidth = maxWidth - (closed + gap) * (items.size - 1)
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, achievement ->
                val width by animateDpAsState(if (index == open) openWidth else closed, tween(240), label = "achievement width")
                StripItem(appId, achievement, isOpen = index == open, onOpen = { open = index }, modifier = Modifier.width(width).height(STRIP_ITEM_HEIGHT))
            }
        }
    }
}

@Composable
private fun StripItem(appId: Int, achievement: Achievement, isOpen: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val hovered by source.collectIsHoveredAsState()
    val selected = focused || hovered
    // Opens once the picture has been pointed at or focused for a moment, so crossing the line does not make everything unfold; a click opens it at once.
    LaunchedEffect(selected) {
        if (selected) {
            delay(OPEN_DELAY_MS)
            onOpen()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val border by animateColorAsState(if (selected) GlassBorder else Color.Transparent, tween(150), label = "achievement border")
    Row(
        modifier
            .clip(shape)
            .border(1.dp, border, shape)
            .hoverable(source)
            // Clickable also makes it reachable by a gamepad; the picture alone has no label, so the name is given for screen readers.
            .clickable(interactionSource = source, indication = null, onClick = onOpen)
            .semantics { contentDescription = achievement.title }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AchievementIcon(appId, achievement, 44.dp)
        AnimatedVisibility(
            visible = isOpen,
            modifier = Modifier.weight(1f),
            enter = fadeIn(tween(200, delayMillis = 90)),
            exit = fadeOut(tween(80)),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(achievement.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (achievement.description.isNotEmpty()) {
                        Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (achievement.unlockedAt > 0) {
                    Text(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(achievement.unlockedAt * 1000L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AchievementsProgress(list: AchievementList, modifier: Modifier = Modifier) {
    val fraction = if (list.total == 0) 0f else list.unlockedCount.toFloat() / list.total
    LinearProgressIndicator(progress = { fraction }, modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
}

/** One achievement: its picture (in colour when unlocked, grey when not), its name and what it asks for, and when it was unlocked. */
@Composable
internal fun AchievementRow(appId: Int, achievement: Achievement, iconSize: Dp, modifier: Modifier = Modifier) {
    val concealed = achievement.hidden && !achievement.unlocked
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AchievementIcon(appId, achievement, iconSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(achievement.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val description = if (concealed) stringResource(R.string.achievements_hidden) else achievement.description
            if (description.isNotEmpty()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (achievement.unlocked && achievement.unlockedAt > 0) {
            Text(
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(achievement.unlockedAt * 1000L)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AchievementIcon(appId: Int, achievement: Achievement, size: Dp) {
    val shape = RoundedCornerShape(8.dp)
    val file = if (achievement.unlocked) achievement.icon else achievement.iconGray ?: achievement.icon
    val urls = AchievementArtwork.urls(appId, file)
    if (urls.isEmpty()) {
        Box(Modifier.size(size).clip(shape).glass(shape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(size / 2), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        GameImage(
            url = urls.first(),
            fallbackUrl = urls.getOrNull(1),
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape).alpha(if (achievement.unlocked) 1f else 0.75f),
        )
    }
}
