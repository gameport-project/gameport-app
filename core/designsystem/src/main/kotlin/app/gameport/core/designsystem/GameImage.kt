package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.gameport.core.model.Art
import coil3.compose.AsyncImage

/**
 * Remote artwork of a game. Tries the pictures of [art] in order; if none loads (some games, flat ones especially, have no cover or hero on Steam) a
 * neutral gradient with a controller is shown. The only way a game picture is shown: [art] comes from the model (see Art), never from a file name
 * written where it is used.
 */
@Composable
fun GameImage(
    art: Art,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val candidates = remember(art) { art.candidates }
    var index by remember(candidates) { mutableStateOf(0) }
    var failed by remember(candidates) { mutableStateOf(candidates.isEmpty()) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (failed) {
            Placeholder()
        } else {
            AsyncImage(
                model = candidates[index],
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = { if (index < candidates.lastIndex) index++ else failed = true },
            )
        }
    }
}

@Composable
private fun Placeholder() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(LocalBackdropColors.current.toList())),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.SportsEsports,
            contentDescription = null,
            modifier = Modifier.size(PLACEHOLDER_ICON).alpha(PLACEHOLDER_ALPHA),
            tint = colors.onSurfaceVariant,
        )
    }
}

private val PLACEHOLDER_ICON = 40.dp
private const val PLACEHOLDER_ALPHA = 0.7f
