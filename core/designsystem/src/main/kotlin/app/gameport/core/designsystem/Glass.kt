package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    /** The size of the label; a smaller style makes a smaller chip. */
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.labelLarge,
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    /** An opaque fill, for a chip over a picture, where a see-through one is hard to read. */
    solid: Boolean = false,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    androidx.compose.foundation.layout.Box(
        modifier
            .then(
                if (solid) {
                    val base = Color(0xFF14181D)
                    val fill = (accent?.copy(alpha = 0.30f) ?: Color.White.copy(alpha = 0.14f)).compositeOver(base)
                    Modifier.clip(shape).background(fill).border(1.dp, accent?.copy(alpha = 0.7f) ?: Color.White.copy(alpha = 0.25f), shape)
                } else if (accent == null) Modifier.glass(shape)
                else Modifier.clip(shape).background(accent.copy(alpha = 0.22f)).border(1.dp, accent.copy(alpha = 0.65f), shape),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        // The eye centres on the capitals, which sit a little above the middle of the line of text: the icon is moved to where they are.
        var lift by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (leading != null) {
                androidx.compose.foundation.layout.Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, lift) }) { leading() }
            }
            androidx.compose.material3.Text(
                label,
                color = contentColor,
                style = textStyle,
                onTextLayout = { layout ->
                    val capitals = with(density) { layout.layoutInput.style.fontSize.toPx() } * CAPITAL_HEIGHT_SHARE
                    val middleOfCapitals = layout.getLineBaseline(0) - capitals / 2
                    val middleOfLine = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2
                    lift = Math.round(middleOfCapitals - middleOfLine)
                },
            )
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

/** The height of a capital letter as a share of the size of the font (that of the Roboto of the app). */
private const val CAPITAL_HEIGHT_SHARE = 0.711f
