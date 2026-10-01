package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val GamePortColors = darkColorScheme(
    primary = Color(0xFFF5F5F7),
    onPrimary = Color(0xFF14192A),
    // The screens are see-through: the gradient behind them (see GamePortTheme) is the background.
    background = Color.Transparent,
    onBackground = Color(0xFFE3E8EF),
    surface = Color(0xFF171C22),
    onSurface = Color(0xFFE3E8EF),
    surfaceVariant = Color(0xFF222932),
    onSurfaceVariant = Color(0xFFB4BDC9),
    outline = Color.White.copy(alpha = 0.28f),
    outlineVariant = Color.White.copy(alpha = 0.14f),
)

/** [accent] colours the buttons, switches and highlights; the text on it is dark or white, whichever reads better. */
@Composable
fun GamePortTheme(
    accent: Color = GamePortColors.primary,
    gradientStart: Color = BackdropStart,
    gradientEnd: Color = BackdropEnd,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = GamePortColors.copy(primary = accent, onPrimary = if (accent.luminance() > 0.5f) Color(0xFF14192A) else Color.White), typography = GamePortTypography) {
        CompositionLocalProvider(LocalBackdropColors provides (gradientStart to gradientEnd)) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))) {
                content()
            }
        }
    }
}

/** The two colours of the gradient behind the pages, as the player chose them. */
val LocalBackdropColors = compositionLocalOf { BackdropStart to BackdropEnd }

// The slate-to-blue backdrop every page sits on, the same one games without artwork show.
val BackdropStart = Color(0xFF222932)
val BackdropEnd = Color(0xFF3C4C7A)

// Buttons read as labels rather than as stock Android: a little larger and heavier.
private val GamePortTypography = Typography().let { base ->
    base.copy(labelLarge = base.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp))
}
