package app.gameport.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ArrowCurvesTest {
    private fun ends(fx: Float, fy: Float, tx: Float, ty: Float, from: Int, to: Int) =
        ArrowEnds(Pt(fx, fy), 10f, 10f, from, Pt(tx, ty), 10f, 10f, to)

    private fun dist(a: Pt, b: Pt) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    @Test
    fun `a link leaves and reaches the side of its controls that faces the other one, not their middle`() {
        val e = ends(0f, 0f, 200f, 0f, 1, 2)
        val curve = ArrowCurves.curve(e, 0.4f, 0f, 2f)
        assertEquals(12f, dist(curve.start, e.from), 0.01f)
        assertEquals(12f, dist(curve.end, e.to), 0.01f)
        assertEquals(12f, curve.start.x, 0.01f)
        assertEquals(188f, curve.end.x, 0.01f)
    }

    @Test
    fun `links that are mostly vertical run along the vertical axis`() {
        val curve = ArrowCurves.curve(ends(0f, 0f, 10f, 300f, 1, 2), 0.4f, 0f, 2f)
        assertEquals(12f, curve.start.y, 0.01f)
        assertEquals(288f, curve.end.y, 0.01f)
    }

    @Test
    fun `arrows that do not meet stay straight`() {
        val list = listOf(ends(0f, 0f, 200f, 0f, 1, 2), ends(0f, 100f, 200f, 100f, 3, 4))
        val curves = ArrowCurves.plan(list, emptyList(), 9f, 2f)
        assertTrue(curves.all { it.shift == 0f })
    }

    @Test
    fun `a straight path over another control is bent away from it`() {
        val list = listOf(ends(0f, 0f, 200f, 0f, 1, 2))
        val curves = ArrowCurves.plan(list, listOf(Obstacle(Pt(100f, 0f), 15f, 9)), 9f, 2f)
        assertTrue(curves[0].shift != 0f)
        val middle = curves[0].at(0.5f)
        assertTrue(dist(middle, Pt(100f, 0f)) >= 15f)
    }

    @Test
    fun `the controls an arrow joins are never obstacles to it`() {
        val list = listOf(ends(0f, 0f, 200f, 0f, 1, 2))
        val curves = ArrowCurves.plan(list, listOf(Obstacle(Pt(0f, 0f), 15f, 1), Obstacle(Pt(200f, 0f), 15f, 2)), 9f, 2f)
        assertEquals(0f, curves[0].shift, 0f)
    }

    @Test
    fun `bending never leaves more crossings than the straight arrows had`() {
        val list = listOf(ends(0f, 0f, 200f, 100f, 1, 2), ends(0f, 100f, 200f, 0f, 3, 4), ends(0f, 50f, 200f, 50f, 5, 6))
        val straight = list.map { ArrowCurves.curve(it, 0.4f, 0f, 2f) }
        val curves = ArrowCurves.plan(list, emptyList(), 9f, 2f)
        assertTrue(ArrowCurves.crossings(curves, list, 2f) <= ArrowCurves.crossings(straight, list, 2f))
    }

    @Test
    fun `two arrows to the same control are not counted as crossing where they meet`() {
        val list = listOf(ends(0f, 0f, 200f, 50f, 1, 9), ends(0f, 100f, 200f, 50f, 2, 9))
        val curves = ArrowCurves.plan(list, emptyList(), 9f, 2f)
        assertEquals(0, ArrowCurves.crossings(curves, list, 2f))
    }

    @Test
    fun `a link is kept inside its area even when that means running over a control`() {
        val list = listOf(ends(0f, 0f, 200f, 0f, 1, 2))
        val bounds = Bounds(-20f, -18f, 220f, 18f)
        val curves = ArrowCurves.plan(list, listOf(Obstacle(Pt(100f, 0f), 15f, 9)), 9f, 2f, bounds)
        for (k in 0..20) assertTrue(bounds.contains(curves[0].at(k / 20f)))
    }

    @Test
    fun `no arrows, nothing placed`() {
        assertEquals(emptyList<ArrowCurve>(), ArrowCurves.plan(emptyList(), emptyList(), 9f, 2f))
    }
}
