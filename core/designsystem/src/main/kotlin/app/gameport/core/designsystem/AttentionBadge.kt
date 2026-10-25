package app.gameport.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small orange "!" that tells the player a game needs their attention, such as an outdated patch. */
@Composable
fun AttentionBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFFFF9800)),
        contentAlignment = Alignment.Center,
    ) {
        // Drawn, not typed: a letter sits on a text baseline and drifts to the bottom of a small circle, and an icon
        // keeps the padding of its own square. Here the bar and the dot are centred by arithmetic, in both directions.
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val width = s * 0.15f
            val bar = s * 0.34f
            val gap = s * 0.09f
            val total = bar + gap + width
            val top = (s - total) / 2f
            val left = (s - width) / 2f
            drawRoundRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(width, bar), cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f))
            drawCircle(Color.White, radius = width / 2f, center = androidx.compose.ui.geometry.Offset(s / 2f, top + bar + gap + width / 2f))
        }
    }
}

/** A small orange wrench, the icon of the patch button: a game on a cover that has a patch to apply. */
@Composable
fun PatchBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFFFF9800)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.Build,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/** A small green download arrow that tells the player a game has an update, which is good news rather than something wrong. */
@Composable
fun UpdateBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFF66BB6A)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Rounded.Download,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.72f),
        )
    }
}

/** A small blue question mark: the game has a question for the player, who answers it on the game's page. Same size as the other badges. */
@Composable
fun QuestionBadge(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(KindBlue),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.QuestionMark,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.7f),
        )
    }
}
