package app.gameport.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** A trophy in gold, lighter at the top and deeper at the bottom like a metal cup. */
@Composable
fun GoldTrophy(size: Dp, modifier: Modifier = Modifier) {
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
