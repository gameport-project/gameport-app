package app.gameport.feature.game

import androidx.compose.foundation.layout.Arrangement
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
import java.util.Date

/** How many of the latest unlocked achievements the game page shows. */
private const val PREVIEW_COUNT = 3

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
            recent.forEach { AchievementRow(list.appId, it, iconSize = 44.dp) }
        }
        GlassButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.achievements_see_all))
        }
    }
}

/** A trophy in gold, lighter at the top and deeper at the bottom like a metal cup. */
@Composable
internal fun GoldTrophy(size: Dp, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.EmojiEvents,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(size)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val gold = Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFF2B705), Color(0xFFC98A00)))
                onDrawWithContent {
                    drawContent()
                    drawRect(gold, blendMode = BlendMode.SrcIn)
                }
            },
    )
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
