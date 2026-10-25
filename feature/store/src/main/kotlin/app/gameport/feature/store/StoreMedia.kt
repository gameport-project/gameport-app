package app.gameport.feature.store

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassIconButton
import app.gameport.core.model.AppMedia
import app.gameport.core.model.Art
import app.gameport.core.model.MediaTrailer

/** One thing to look at on the page of a game: a trailer or a screenshot. */
private sealed interface MediaItem {
    data class Trailer(val trailer: MediaTrailer) : MediaItem

    data class Shot(val path: String) : MediaItem
}

/** The trailers, then the screenshots, as thumbnails in a row. A click opens the viewer on the one chosen. */
@Composable
internal fun MediaGallery(appId: Int, media: AppMedia) {
    val items = remember(media) { media.t.map { MediaItem.Trailer(it) } + media.s.map { MediaItem.Shot(it) } }
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(items) { index, item ->
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.size(width = 224.dp, height = 126.dp).clip(shape).clickable { open = index }) {
                when (item) {
                    is MediaItem.Trailer -> {
                        GameImage(art = Art(item.trailer.thumbnail), contentDescription = null, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.storemedia_trailer), tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    is MediaItem.Shot -> GameImage(art = Art(media.screenshotThumbnail(appId, item.path)), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
    open?.let { start -> MediaViewer(appId, media, items, start, onClose = { open = null }) }
}

/** The picture or the trailer on the whole screen, with the arrows to go from one to the next. */
@Composable
private fun MediaViewer(appId: Int, media: AppMedia, items: List<MediaItem>, start: Int, onClose: () -> Unit) {
    var index by remember { mutableIntStateOf(start.coerceIn(0, items.lastIndex)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (val item = items[index]) {
                is MediaItem.Shot -> GameImage(
                    art = Art(media.screenshotFull(appId, item.path), listOf(media.screenshotThumbnail(appId, item.path))),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                is MediaItem.Trailer -> TrailerPlayer(item.trailer, Modifier.fillMaxSize())
            }
            GlassIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.storemedia_close), tint = Color.White)
            }
            if (items.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(onClick = { index = (index - 1 + items.size) % items.size }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.storemedia_previous), tint = Color.White)
                    }
                    Text("${index + 1} / ${items.size}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    GlassIconButton(onClick = { index = (index + 1) % items.size }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.storemedia_next), tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Plays the trailer with the player Android has: its stream first, then the short loop if the stream cannot be played. */
@Composable
private fun TrailerPlayer(trailer: MediaTrailer, modifier: Modifier) {
    val sources = remember(trailer) { trailer.sources }
    var source by remember(trailer) { mutableIntStateOf(0) }
    var failed by remember(trailer) { mutableStateOf(sources.isEmpty()) }
    if (failed) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.storemedia_unplayable), color = Color.White, modifier = Modifier.fillMaxWidth().padding(32.dp))
        }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ ->
                    if (source < sources.lastIndex) source++ else failed = true
                    true
                }
            }
        },
        update = { view ->
            val wanted = sources[source]
            if (view.tag != wanted) {
                view.tag = wanted
                view.setVideoURI(Uri.parse(wanted))
            }
        },
        onRelease = { it.stopPlayback() },
    )
}
