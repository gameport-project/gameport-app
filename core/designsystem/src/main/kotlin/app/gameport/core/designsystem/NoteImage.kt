package app.gameport.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** A picture of the release notes, as wide as its column, up to a size that keeps the text readable. When it cannot be loaded (offline), it leaves no gap. */
@Composable
fun NoteImage(url: String, description: String, modifier: Modifier = Modifier, aspectRatio: Float = 1.6f) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = description,
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(aspectRatio).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit,
        onError = { failed = true },
    )
}
