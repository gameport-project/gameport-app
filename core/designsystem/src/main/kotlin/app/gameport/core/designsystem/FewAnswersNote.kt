package app.gameport.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * The line that says the verdict rests on few answers and may not be reliable: on a line of its own, in the orange of everything that is about compatibility,
 * with the exclamation mark in a tinted circle, as on the covers.
 */
@Composable
fun FewAnswersNote(text: String, modifier: Modifier = Modifier) = CautionNote(text, modifier)

/** A word of caution on a line of its own: the exclamation mark in a tinted circle and the text, in the orange of everything about compatibility. */
@Composable
fun CautionNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(CompatOrange.copy(alpha = 0.22f))
                .border(1.dp, CompatOrange.copy(alpha = 0.65f), CircleShape),
        ) {
            CompatGlyph(Glyph.EXCLAMATION, CompatOrange, height = 8.dp, modifier = Modifier.align(Alignment.Center))
        }
        Text(text, color = CompatOrange, style = MaterialTheme.typography.labelMedium)
    }
}
