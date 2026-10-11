package app.gameport.feature.store

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GlassChip

/** Every label of the store has this size and an opaque fill, over a picture or not. */
@Composable
internal fun StoreChip(label: String, modifier: Modifier = Modifier, accent: Color? = null, leading: (@Composable () -> Unit)? = null) {
    GlassChip(label, modifier, accent = accent, leading = leading, textStyle = MaterialTheme.typography.labelSmall, verticalPadding = 2.dp, horizontalPadding = 9.dp, solid = true)
}

/** The mark of a game the account has, in place of a word: shown on a round dark backing so it reads over a picture. */
@Composable
internal fun OwnedMark(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.size(20.dp).background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Icon(Icons.Filled.VideogameAsset, contentDescription = androidx.compose.ui.res.stringResource(R.string.store_owned), tint = app.gameport.core.designsystem.KindBlue, modifier = Modifier.size(13.dp))
    }
}

/**
 * A card of frosted glass: the page's own colours nearly opaque under the same pale veil as the tabs, so what is behind can only be guessed, with a light that
 * pools at its upper left and a faint edge, the way a drop of water shows what is behind it. Its texts are light ([CardInk]).
 */
@androidx.compose.runtime.Composable
internal fun Modifier.frosted(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val colours = app.gameport.core.designsystem.LocalBackdropColors.current
    return this
        .clip(shape)
        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(colours.first, colours.second)), alpha = 0.92f)
        .background(Color.White.copy(alpha = 0.10f))
        .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(60f, 40f), radius = 420f))
        .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.06f))), shape)
}
