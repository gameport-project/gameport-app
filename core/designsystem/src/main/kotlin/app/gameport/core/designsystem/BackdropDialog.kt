package app.gameport.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A dialog on the background the player chose for the app (the gradient behind the pages), instead of the stock
 * Material surface. Every dialog in GamePort uses this one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackdropDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    minWidth: Dp = 280.dp,
    maxWidth: Dp = 560.dp,
    padding: Dp = 24.dp,
    maxTextHeight: Dp = 480.dp,
    maxHeight: Dp = Dp.Unspecified,
    /** Shown between the text and the buttons, always in view: it does not scroll with the text. */
    pinned: (@Composable () -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        val shape = RoundedCornerShape(28.dp)
        Surface(shape = shape, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier
                    .widthIn(min = minWidth, max = maxWidth)
                    .clip(shape)
                    .background(Brush.linearGradient(LocalBackdropColors.current.toList()))
                    .then(if (maxHeight != Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier)
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() } }
                text?.let {
                    // With a maximum height for the whole dialog, the text takes what the title and the buttons leave and scrolls; without one, it has its own maximum.
                    val room = if (maxHeight != Dp.Unspecified) Modifier.weight(1f, fill = false) else Modifier.heightIn(max = maxTextHeight)
                    Column(room.verticalScroll(rememberScrollState())) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                    }
                }
                pinned?.invoke()
                if (confirmButton != null || dismissButton != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        dismissButton?.invoke()
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}

/** The height in pixels of the window of the app, set once at its root: a dialog window is smaller than the app's (it leaves out the bars). */
val LocalAppWindowHeightPx = androidx.compose.runtime.compositionLocalOf { 0 }

/** Gives everything inside the height of the app's window, which [dialogMaxHeight] reads. */
@Composable
fun ProvideAppWindowHeight(content: @Composable () -> Unit) {
    // The root view of the window, which is what is on screen; the window info and the configuration both leave out part of it.
    val view = androidx.compose.ui.platform.LocalView.current
    var height by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(view.rootView.height) }
    androidx.compose.runtime.DisposableEffect(view) {
        val listener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> height = view.rootView.height }
        view.rootView.addOnLayoutChangeListener(listener)
        // Read now too: the window may already have been laid out, and then no change is ever reported.
        height = view.rootView.height
        onDispose { view.rootView.removeOnLayoutChangeListener(listener) }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAppWindowHeightPx provides height, content = content)
}

/**
 * The most height a dialog may take: about [fraction] (70 % by default) of the window of the app, whatever its size. What does not fit
 * scrolls. For dialogs whose content can be long.
 */
@Composable
fun dialogMaxHeight(fraction: Float = 0.7f): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowHeight = LocalAppWindowHeightPx.current
    // The dialog window keeps about 56 dp of margin around the card, which counts in the height it is given: it is added so the card itself has the fraction asked.
    val margin = 56.dp
    if (windowHeight <= 0) return (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * fraction).dp + margin
    return with(density) { (windowHeight * fraction).toDp() } + margin
}
