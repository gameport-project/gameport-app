package app.gameport.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compact segmented tabs with a sliding highlight. Callers own the selection, so it can be
 * driven by taps here and by shoulder buttons elsewhere.
 */
@Composable
fun PillTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .height(TAB_HEIGHT)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
        val tabWidth = maxWidth / labels.size
        val indicatorOffset by animateDpAsState(tabWidth * selectedIndex, label = "tab indicator")
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.92f)),
        )
        Row(Modifier.fillMaxHeight()) {
            labels.forEachIndexed { index, label ->
                val color by animateColorAsState(
                    if (index == selectedIndex) Color(0xFF14192A) else Color.White.copy(alpha = 0.75f),
                    label = "tab text",
                )
                Box(
                    Modifier.width(tabWidth).fillMaxHeight().clip(shape).clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = label, color = color, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private val TAB_HEIGHT = 44.dp
