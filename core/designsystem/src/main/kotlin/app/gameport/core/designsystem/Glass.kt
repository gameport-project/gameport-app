package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// The translucent "glass" look shared by the tabs, the search field and the cards.
val GlassFill = Color.White.copy(alpha = 0.10f)

/** The blue of a demo or a playtest: not the full game. Soft and slightly see-through, like the other labels. */
val KindBlue = Color(0xFF4FA8E8)
val GlassBorder = Color.White.copy(alpha = 0.14f)

fun Modifier.glass(shape: Shape): Modifier = this.clip(shape).background(GlassFill).border(1.dp, GlassBorder, shape)

@Composable
fun glassTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GlassFill,
    unfocusedContainerColor = GlassFill,
    focusedBorderColor = Color.White.copy(alpha = 0.40f),
    unfocusedBorderColor = GlassBorder,
    focusedPlaceholderColor = Color.White.copy(alpha = 0.60f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.60f),
    cursorColor = Color.White,
)

/** A small label in the glass style; tappable when [onClick] is given. */
@Composable
fun GlassChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentColor: Color = Color.White,
    /** Tints the chip's fill and outline: a coloured label rather than a neutral one. */
    accent: Color? = null,
    /** An icon before the label. */
    leading: (@Composable () -> Unit)? = null,
    /** Room above and below the label; less makes a chip that fits in a line of text. */
    verticalPadding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    androidx.compose.foundation.layout.Box(
        modifier
            .then(
                if (accent == null) Modifier.glass(shape)
                else Modifier.clip(shape).background(accent.copy(alpha = 0.22f)).border(1.dp, accent.copy(alpha = 0.65f), shape),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = verticalPadding),
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            leading?.invoke()
            androidx.compose.material3.Text(label, color = contentColor, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        }
    }
}

/** A button with the glass look: a translucent fill and a fine outline instead of a bare label. */
@Composable
fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.material3.ButtonDefaults.ContentPadding, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = GlassFill, contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
        contentPadding = contentPadding,
        content = content,
    )
}

/** A round icon button with the glass look. */
@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.glass(androidx.compose.foundation.shape.CircleShape),
        content = content,
    )
}
