package app.gameport.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp

/** The marks that say what players think of a game: they are the same in a label of the page of a game and in the circle on a cover. */
enum class Glyph(val aspect: Float) {
    /** It works. */
    CHECK(1.3f),

    /** Opinions are divided. */
    EXCLAMATION(0.3f),

    /** It has problems, or it cannot run. */
    CROSS(1f),

    /** It works, but not with the network: the sign of a connection, crossed out. */
    NO_NETWORK(1.4f),
}

/**
 * A mark drawn so that what is seen is exactly [height] tall and centred in its own box, whatever the mark: the standard icons sit in a frame with
 * room around them, so they are smaller and lower than the text they stand beside. The box is as tall as [height] and as wide as the mark needs.
 */
@Composable
fun CompatGlyph(glyph: Glyph, tint: Color, height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.height(height).width(height * glyph.aspect)) {
        val w = size.width
        val h = size.height
        val stroke = h * 0.16f
        // The round ends go half a stroke beyond the line: the line is kept that far from the edge, so the mark fills its box and no more.
        val inset = stroke / 2
        val left = inset
        val right = w - inset
        val top = inset
        val bottom = h - inset
        fun at(x: Float, y: Float) = Offset(left + (right - left) * x, top + (bottom - top) * y)
        when (glyph) {
            Glyph.CHECK -> {
                drawLine(tint, at(0f, 0.55f), at(0.36f, 1f), stroke, StrokeCap.Round)
                drawLine(tint, at(0.36f, 1f), at(1f, 0f), stroke, StrokeCap.Round)
            }
            Glyph.CROSS -> {
                drawLine(tint, at(0f, 0f), at(1f, 1f), stroke, StrokeCap.Round)
                drawLine(tint, at(1f, 0f), at(0f, 1f), stroke, StrokeCap.Round)
            }
            Glyph.NO_NETWORK -> {
                // Two arcs of a connection above a point, and a line across them.
                val dot = stroke * 0.7f
                val centre = Offset(w / 2, bottom - dot)
                val outer = centre.y - top
                for (radius in listOf(outer, outer * 0.5f)) {
                    drawArc(
                        tint, startAngle = -135f, sweepAngle = 90f, useCenter = false,
                        topLeft = Offset(centre.x - radius, centre.y - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                drawCircle(tint, radius = dot, center = centre)
                drawLine(tint, Offset(left + w * 0.12f, top), Offset(right - w * 0.12f, bottom), stroke, StrokeCap.Round)
            }
            Glyph.EXCLAMATION -> {
                drawLine(tint, at(0.5f, 0f), at(0.5f, 0.6f), stroke, StrokeCap.Round)
                drawCircle(tint, radius = stroke * 0.62f, center = Offset(w / 2, bottom - stroke * 0.12f))
            }
        }
    }
}
