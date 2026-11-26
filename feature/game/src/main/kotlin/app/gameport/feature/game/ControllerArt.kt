package app.gameport.feature.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gameport.core.model.Hand

private val Used = Color(0xFFE8EBF0)
internal val Selected = Color(0xFF0A84FF)
internal val Amber = Color(0xFFFFB340)
private val Scrim = Color(0xFF0B1020)

/**
 * One controller drawn by hand: its [label] above, the body with a light reflection, the controls dark when unused and lit in the colour of their kind when
 * they are in [lights]. A touch on a lit control of [touchable] calls [onTap] with it.
 */
@Composable
internal fun ControllerView(
    style: ControllerStyle,
    hand: Hand,
    label: String,
    lights: Map<String, ControllerLight>,
    touchable: Set<String>,
    onTap: ((ControlZone) -> Unit)?,
    modifier: Modifier = Modifier,
    /** Controls drawn with a thin outline: the ones a touch can pick as the destination. */
    pickable: Set<String> = emptySet(),
    /** Called with the drawing's position, so something can be drawn across several controllers. */
    onPlaced: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
    /** Called with the control under the pointer, or null when it leaves them. */
    onHover: ((ControlZone?) -> Unit)? = null,
    /** The controls the pointer can be over: by default the touchable ones. */
    hoverable: Set<String> = touchable,
    /** A choice is being made on this controller: what cannot be picked fades, what can pulses. */
    pickMode: Boolean = false,
) {
    val zones = remember(style, hand) { ControllerArtLayout.zones(style, hand) }
    val body = remember(style, hand) { ControllerArtLayout.body(style, hand) }
    val palette = remember(style) { ControllerArtLayout.palette(style) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val pulse = if (pickMode) rememberInfiniteTransition(label = "pick").animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse").value else 1f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f / ControllerArtLayout.HEIGHT)
                .then(if (onPlaced != null) Modifier.onGloballyPositioned(onPlaced) else Modifier)
                .semantics { contentDescription = label }
                .pointerInput(zones, hoverable, onHover) {
                    if (onHover != null) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val at = event.changes.firstOrNull()?.position
                                val hit = if (event.type == PointerEventType.Exit || at == null) null
                                else ControllerArtLayout.zoneAt(zones.filter { it.group in hoverable }, at.x / size.width, at.y / size.width)
                                onHover(hit)
                            }
                        }
                    }
                }
                .pointerInput(zones, touchable, onTap) {
                    if (onTap != null) {
                        detectTapGestures { at ->
                            val hit = ControllerArtLayout.zoneAt(zones.filter { it.group in touchable }, at.x / size.width, at.y / size.width)
                            if (hit != null) onTap(hit)
                        }
                    }
                },
        ) {
            val s = size.width
            fun px(p: Point) = Offset(p.x * s, p.y * s)
            val edge = Color(palette.edge)
            val bodyBrush = Brush.linearGradient(listOf(Color(palette.top), Color(palette.bottom)), start = Offset(0f, 0f), end = Offset(s * 0.6f, s * 1.5f))
            val line = with(density) { 1.2.dp.toPx() }

            // The handle: an outline, then the body over it.
            val handle = Path().apply { moveTo(px(body.handleFrom).x, px(body.handleFrom).y); lineTo(px(body.handleTo).x, px(body.handleTo).y) }
            drawPath(handle, edge, style = Stroke(body.handleWidth * s + line * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(handle, bodyBrush, style = Stroke(body.handleWidth * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // A soft reflection along the handle.
            drawPath(handle, Color.White.copy(alpha = if (style == ControllerStyle.PICO) 0.35f else 0.07f), style = Stroke(body.handleWidth * s * 0.22f, cap = StrokeCap.Round))

            // The head: edge, body, bezel and a reflection at its top left.
            val c = px(body.headCenter)
            val r = body.headRadius * s
            drawCircle(edge, r + line, c)
            drawCircle(bodyBrush, r, c)
            drawCircle(edge.copy(alpha = 0.45f), r * if (style == ControllerStyle.QUEST) 0.93f else 0.9f, c, style = Stroke(line))
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = if (style == ControllerStyle.PICO) 0.55f else 0.18f), Color.Transparent), center = c + Offset(-r * 0.35f, -r * 0.45f), radius = r * 0.95f),
                r, c,
            )

            if (pickMode) {
                // The body fades behind the controls that can be picked.
                val scrim = Scrim.copy(alpha = 0.4f)
                drawPath(handle, scrim, style = Stroke(body.handleWidth * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(scrim, r, c)
            }
            for (zone in zones) drawZone(zone, lights[zone.group], zone.group in pickable, pickMode, pulse, style, hand, palette, s, line, measurer)
        }
    }
}

private fun DrawScope.drawZone(zone: ControlZone, light: ControllerLight?, pickable: Boolean, pickMode: Boolean, pulse: Float, style: ControllerStyle, hand: Hand, palette: ControllerPalette, s: Float, line: Float, measurer: TextMeasurer) {
    val center = Offset(zone.cx * s, zone.cy * s)
    val size = Size(zone.w * s, zone.h * s)
    val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
    // Only two colours: a soft white for what is used, the blue of the system for what is selected and where it goes.
    val lit = light?.let { if (it.selected) Selected else Used }
    val strength = if (light == null) 0f else if (light.emphasised) 1f else 0.35f
    val base = Color(palette.button)

    fun shape(color: Color, grow: Float = 0f, stroke: Stroke? = null) {
        val tl = Offset(topLeft.x - grow, topLeft.y - grow)
        val sz = Size(size.width + grow * 2, size.height + grow * 2)
        when (zone.shape) {
            ZoneShape.CIRCLE -> if (stroke == null) drawOval(color, tl, sz) else drawOval(color, tl, sz, style = stroke)
            ZoneShape.PILL -> drawRoundRect(color, tl, sz, CornerRadius(sz.height / 2), style = stroke ?: androidx.compose.ui.graphics.drawscope.Fill)
            ZoneShape.SQUARE -> drawRoundRect(color, tl, sz, CornerRadius(sz.width * 0.18f), style = stroke ?: androidx.compose.ui.graphics.drawscope.Fill)
        }
    }

    if (lit != null) if (light?.selected == true && lit != null) shape(lit.copy(alpha = 0.22f), grow = s * 0.018f)
    shape(base)
    if (zone.ring != null) shape(Color(zone.ring), grow = line * 0.5f, stroke = Stroke(line * 2.2f))
    if (zone.group == "thumbstick") {
        // A stick: a well, and a cap on top of it.
        drawCircle(Color(palette.edge).copy(alpha = 0.55f), size.width * 0.36f, center, style = Stroke(line))
        drawCircle(if (lit != null) lit.copy(alpha = strength) else Color(palette.edge).copy(alpha = 0.35f), size.width * 0.27f, center)
    } else if (lit != null) {
        shape(lit.copy(alpha = strength))
    } else if (zone.shape == ZoneShape.PILL) {
        // An unused trigger, bumper or grip: a soft grey fill with a thicker edge, so it reads on a light body (Pico) and a dark one (Quest, Steam Frame) alike.
        shape(Color(0xFF9AA2AD))
        shape(Color(palette.edge).copy(alpha = 0.7f), stroke = Stroke(line * 1.6f))
    } else {
        shape(Color(palette.edge).copy(alpha = 0.55f), stroke = Stroke(line))
    }
    // Letters and the menu lines, so the buttons are known at a glance.
    val ink = if (lit != null) Color(0xFF0E1116).copy(alpha = if (strength > 0.5f) 0.9f else 0.6f) else Color(palette.letter).copy(alpha = 0.85f)
    when {
        zone.group in setOf("a", "b", "x", "y") -> {
            val text = measurer.measure(zone.group.uppercase(), TextStyle(fontSize = (size.height * 0.5f).toSp(), fontWeight = FontWeight.SemiBold, color = ink))
            drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
        }
        style == ControllerStyle.FRAME && (zone.group == "trigger" || zone.group == "shoulder") -> {
            // Engraved in the pill: L or R for the hand, T for the trigger and B for the bumper.
            val name = (if (hand == Hand.LEFT) "L" else "R") + if (zone.group == "trigger") "T" else "B"
            val text = measurer.measure(name, TextStyle(fontSize = (size.height * 0.6f).toSp(), fontWeight = FontWeight.SemiBold, color = ink))
            drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
        }
        zone.group == "menu" -> {
            val half = size.height * 0.28f
            for (dy in listOf(-0.16f, 0f, 0.16f)) drawLine(ink, Offset(center.x - half, center.y + size.height * dy), Offset(center.x + half, center.y + size.height * dy), line)
        }
    }
    if (pickable && light?.selected != true) {
        // A control that can be added: a hollow ring that pulses softly, blue edged with white so it reads on the dark controllers and on the white ones.
        shape(Selected.copy(alpha = 0.18f * pulse), grow = s * 0.03f)
        shape(Selected.copy(alpha = pulse), grow = s * 0.016f, stroke = Stroke(line * 2.6f))
        shape(Color.White.copy(alpha = 0.9f * pulse), grow = s * 0.007f, stroke = Stroke(line * 1.1f))
    } else if (pickMode && light?.selected != true) {
        shape(Scrim.copy(alpha = 0.6f))
    }
    if (light?.anchor == true) {
        // The control being changed: solid, with a white ring around it.
        shape(Color.White, grow = s * 0.016f, stroke = Stroke(line * 2.4f))
    }
    if (pickMode && light?.selected == true && light.anchor != true) {
        // A control that is linked now: solid, with a check mark.
        val tick = Stroke(line * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(center.x - size.width * 0.24f, center.y)
            lineTo(center.x - size.width * 0.06f, center.y + size.height * 0.2f)
            lineTo(center.x + size.width * 0.26f, center.y - size.height * 0.22f)
        }
        drawPath(path, Color.White, style = tick)
    }
    if (light != null && light.linked && !light.selected) shape(Color.White.copy(alpha = 0.6f), grow = s * 0.012f, stroke = Stroke(line * 1.1f))
}
