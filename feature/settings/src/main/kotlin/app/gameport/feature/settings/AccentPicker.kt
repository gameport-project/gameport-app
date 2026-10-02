package app.gameport.feature.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.gameport.core.designsystem.BackdropDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.gameport.core.designsystem.DangerTextButton
import kotlin.math.roundToInt

/** Picks any accent colour with three bars: hue, saturation and brightness, and shows a button in that colour. */
@Composable
internal fun AccentPickerDialog(initial: Long, title: Int, minBrightness: Float = 0.2f, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val start = FloatArray(3).also { AndroidColor.colorToHSV(initial.toInt(), it) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    val picked = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    BackdropDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                Bar(stringResource(R.string.settings_accent_hue), hue / 360f, hueBrush(), picked) { hue = it * 360f }
                Bar(
                    stringResource(R.string.settings_accent_saturation), saturation,
                    Brush.horizontalGradient(listOf(Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0f, brightness))), Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, brightness))))),
                    picked,
                ) { saturation = it }
                Bar(
                    stringResource(R.string.settings_accent_brightness), brightness,
                    Brush.horizontalGradient(listOf(Color.Black, Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f))))),
                    picked,
                ) { brightness = it.coerceAtLeast(minBrightness) }
                // A button as it will look in the chosen colour (the theme is overridden here only; the button itself does nothing).
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = picked, onPrimary = if (picked.luminance() > 0.5f) Color(0xFF14192A) else Color.White)) {
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_accent_preview)) }
                }
            }
        },
        confirmButton = { Button(onClick = { onPick(picked.toArgbLong()) }) { Text(stringResource(R.string.settings_accent_use)) } },
        dismissButton = { DangerTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

private fun Color.toArgbLong(): Long = (0xFF000000L or (red * 255).roundToInt().toLong().shl(16) or (green * 255).roundToInt().toLong().shl(8) or (blue * 255).roundToInt().toLong())

private fun hueBrush() = Brush.horizontalGradient(List(7) { Color(AndroidColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })

/** A coloured bar with a handle, set by touching or dragging along it. */
@Composable
private fun Bar(label: String, fraction: Float, brush: Brush, handleColor: Color, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        var widthPx by remember { mutableFloatStateOf(1f) }
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(32.dp)
                // A gamepad or keyboard moves the handle with left and right.
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onChange((fraction - KEY_STEP).coerceIn(0f, 1f)); true }
                        Key.DirectionRight -> { onChange((fraction + KEY_STEP).coerceIn(0f, 1f)); true }
                        else -> false
                    }
                }
                .focusable()
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .clip(RoundedCornerShape(50))
                .background(brush)
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                .pointerInput(Unit) { detectTapGestures { onChange((it.x / widthPx).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> onChange((change.position.x / widthPx).coerceIn(0f, 1f)) } },
        ) {
            Box(
                Modifier
                    .offset { IntOffset((fraction.coerceIn(0f, 1f) * widthPx).roundToInt() - 14.dp.roundToPx(), 2.dp.roundToPx()) }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(handleColor)
                    .border(3.dp, Color.White, CircleShape),
            )
        }
    }
}

private const val KEY_STEP = 0.04f
