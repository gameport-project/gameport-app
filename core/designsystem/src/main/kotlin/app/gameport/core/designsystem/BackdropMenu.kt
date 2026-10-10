package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Opens under the right edge of its button and never runs off the window. */
private object BelowButton : PopupPositionProvider {
    private const val GAP = 6

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.bottom + GAP).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/**
 * A menu that opens against the button that carries it, as a stock menu does, but on the background the player chose for the app, with the same
 * rounded corners and fine outline as the windows. The stock menu keeps a band of its own above and below its entries, which stays see-through
 * under any colour given to the entries: this one paints the whole surface. Put it in the same box as its button.
 */
@Composable
fun BackdropMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!expanded) return
    val shape = RoundedCornerShape(20.dp)
    Popup(popupPositionProvider = BelowButton, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
      // A popup is a window of its own, outside the surface of the page: its text would be black without a colour given here.
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .shadow(8.dp, shape)
                .clip(shape)
                .background(Brush.linearGradient(LocalBackdropColors.current.toList()))
                .border(1.dp, GlassBorder, shape)
                .width(IntrinsicSize.Max)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
      }
    }
}
