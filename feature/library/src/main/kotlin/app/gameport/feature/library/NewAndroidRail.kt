package app.gameport.feature.library

import androidx.compose.foundation.background
import app.gameport.core.model.NewRailSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.GameImage
import app.gameport.core.designsystem.GlassChip
import app.gameport.core.designsystem.PricePill
import app.gameport.core.model.StorePrice
import app.gameport.core.designsystem.glass
import app.gameport.core.model.AndroidApp

/** The proportions of the picture of a banner by size: the header Steam publishes for every app, then wider and wider hero pictures. */
private fun ratioOf(size: NewRailSize) = when (size) {
    NewRailSize.COMPACT -> 2.14f
    NewRailSize.MEDIUM -> 2.4f
    NewRailSize.WIDE -> 3.0f
}

/** How tall the banners are next to a cover: a bit more than half its width, so well under the height of the covers (1.5 times their width). */
private const val HEIGHT_OF_COVER_WIDTH = 0.62f

/** The share of the width a banner takes, once the size is not the compact one: the next game stays in sight beside a wide one. */
private fun shareOf(size: NewRailSize) = when (size) {
    NewRailSize.COMPACT -> 0f
    NewRailSize.MEDIUM -> 0.45f
    NewRailSize.WIDE -> 0.8f
}

/**
 * The games whose Android version arrived this week, as a row of banners above the covers: low as the covers by default, or wider up to nearly the
 * whole width. A game of the library and a game the account does not have are shown alike: both lead to the page of the game.
 */
@Composable
internal fun NewAndroidRail(apps: List<AndroidApp>, prices: Map<Int, StorePrice>, coverWidth: Dp, size: NewRailSize, onHighlight: (AndroidApp?) -> Unit, onClick: (Int) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            stringResource(R.string.library_row_new_android),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val ratio = ratioOf(size)
            val width = if (size == NewRailSize.COMPACT) coverWidth * HEIGHT_OF_COVER_WIDTH * ratio else (maxWidth - 64.dp) * shareOf(size)
            val height = width / ratio
            LazyRow(
                contentPadding = PaddingValues(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(apps, key = { it.appid }) { app -> Banner(app, prices[app.appid], width, height, size, onHighlight, onClick) }
            }
        }
    }
}

@Composable
private fun Banner(app: AndroidApp, price: StorePrice?, width: Dp, height: Dp, size: NewRailSize, onHighlight: (AndroidApp?) -> Unit, onClick: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pointed = hovered
    LaunchedEffect(pointed) { if (pointed) onHighlight(app) }
    Box(
        Modifier
            .height(height)
            .width(width)
            .glass(shape)
            .clip(shape)
            .hoverable(source)
            // Not focusable: a focused banner is brought into view, and the row scrolled by itself when the pointer touched a banner cut by the edge.
            .pointerInput(app.appid) { detectTapGestures { onClick(app.appid) } }
            .semantics(mergeDescendants = true) { role = Role.Button; onClick { onClick(app.appid); true } },
    ) {
        val wide = size != NewRailSize.COMPACT
        // A wide banner shows the hero picture; the compact one the header, which has the proportions of the box.
        val image = if (wide) app.banner ?: app.header ?: app.cover else app.header ?: app.banner ?: app.cover
        if (image != null) {
            GameImage(url = image, fallbackUrl = if (wide) app.header else app.banner, contentDescription = null, modifier = Modifier.fillMaxWidth().height(height))
        }
        // The name stays readable on any picture.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))).padding(start = 10.dp, top = if (wide) 40.dp else 16.dp, bottom = if (wide) 12.dp else 6.dp, end = 10.dp)) {
            Text(app.name, style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The price at the top left, so the name keeps the whole width at the bottom.
        price?.let { PricePill(it, modifier = Modifier.align(Alignment.TopStart).padding(8.dp), compact = !wide) }
        if (app.vr) {
            GlassChip(stringResource(R.string.library_tab_vr), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), verticalPadding = 2.dp)
        }
    }
}
