package app.gameport.feature.game

import kotlin.math.abs
import kotlin.math.sqrt

internal data class Pt(val x: Float, val y: Float)

/** One link to place: the middle and half size of the control it leaves and of the one it reaches (their ids tell them from the obstacles). */
internal data class ArrowEnds(val from: Pt, val fromRx: Float, val fromRy: Float, val fromId: Int, val to: Pt, val toRx: Float, val toRy: Float, val toId: Int)

/** The area the links must stay inside. */
internal data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(p: Pt): Boolean = p.x in left..right && p.y in top..bottom
}

/** A control the links should not run over. */
internal data class Obstacle(val at: Pt, val radius: Float, val id: Int)

/** A smooth S from [start] to [end], leaving and arriving along the same axis: the handles [c1] and [c2] pull it, and [shift] bends the whole S to one side. */
internal data class ArrowCurve(val start: Pt, val c1: Pt, val c2: Pt, val end: Pt, val shift: Float) {
    fun at(t: Float): Pt {
        val u = 1f - t
        val a = u * u * u
        val b = 3 * u * u * t
        val c = 3 * u * t * t
        val d = t * t * t
        return Pt(a * start.x + b * c1.x + c * c2.x + d * end.x, a * start.y + b * c1.y + c * c2.y + d * end.y)
    }
}

/**
 * The shape of the links between a control of the game and a button of the device. Each one is a smooth S that leaves the side of its control that faces
 * the other one and arrives on the side that faces it, so the letters stay readable and every link has the same look. A link whose plain S would cross
 * another, run over a control, or lie right against another one is pulled differently (stiffer or looser handles, bent to one side or the other), the
 * least that frees it, so the links fan out instead of running side by side.
 */
internal object ArrowCurves {
    /** How far the handles reach, as a share of the distance between the ends. */
    private val HANDLES = floatArrayOf(0.4f, 0.28f, 0.55f)

    /** How far the whole S is bent sideways, as a share of the distance between the ends, the plain one first. */
    private val SHIFTS = floatArrayOf(0f, 0.08f, -0.08f, 0.16f, -0.16f, 0.26f, -0.26f)
    private const val SAMPLES = 20
    private const val PASSES = 6

    /** The S for [ends] with the given handle reach and bend. It runs along the horizontal axis when the ends are farther apart that way, else along the vertical one. */
    fun curve(ends: ArrowEnds, handle: Float, shift: Float, clear: Float): ArrowCurve {
        val horizontal = abs(ends.to.x - ends.from.x) >= abs(ends.to.y - ends.from.y)
        return if (horizontal) {
            val sign = if (ends.to.x >= ends.from.x) 1f else -1f
            val start = Pt(ends.from.x + sign * (ends.fromRx + clear), ends.from.y)
            val end = Pt(ends.to.x - sign * (ends.toRx + clear), ends.to.y)
            val span = abs(end.x - start.x)
            ArrowCurve(start, Pt(start.x + sign * span * handle, start.y + shift * span), Pt(end.x - sign * span * handle, end.y + shift * span), end, shift)
        } else {
            val sign = if (ends.to.y >= ends.from.y) 1f else -1f
            val start = Pt(ends.from.x, ends.from.y + sign * (ends.fromRy + clear))
            val end = Pt(ends.to.x, ends.to.y - sign * (ends.toRy + clear))
            val span = abs(end.y - start.y)
            ArrowCurve(start, Pt(start.x + shift * span, start.y + sign * span * handle), Pt(end.x + shift * span, end.y - sign * span * handle), end, shift)
        }
    }

    /** Places every link: the plain S where nothing is in the way, the others pulled just enough. [minGap] is how close two links may run before it counts against them. */
    fun plan(ends: List<ArrowEnds>, obstacles: List<Obstacle>, minGap: Float, clear: Float, bounds: Bounds? = null): List<ArrowCurve> {
        var curves = ends.indices.map { curve(ends[it], HANDLES[0], 0f, clear) }
        repeat(PASSES) {
            var changed = false
            for (i in ends.indices) {
                var best = curves[i]
                var bestCost = Float.MAX_VALUE
                for (handle in HANDLES) for (shift in SHIFTS) {
                    val candidate = curve(ends[i], handle, shift, clear)
                    val cost = cost(i, candidate, ends, curves, obstacles, minGap, bounds) + (if (handle != HANDLES[0]) 5f else 0f)
                    if (cost < bestCost - 0.001f) { bestCost = cost; best = candidate }
                }
                if (best != curves[i]) { curves = curves.toMutableList().also { it[i] = best }; changed = true }
            }
            if (!changed) return curves
        }
        return curves
    }

    /** How many pairs of [curves] cross (ignoring where two links meet at the same control). */
    fun crossings(curves: List<ArrowCurve>, ends: List<ArrowEnds>, clear: Float): Int {
        var count = 0
        for (a in curves.indices) for (b in a + 1 until curves.size) count += crossingsBetween(curves[a], ends[a], curves[b], ends[b], clear)
        return count
    }

    private fun cost(i: Int, curve: ArrowCurve, ends: List<ArrowEnds>, others: List<ArrowCurve>, obstacles: List<Obstacle>, minGap: Float, bounds: Bounds?): Float {
        var crossings = 0
        var close = 0
        val mine = sample(curve)
        for (j in others.indices) {
            if (j == i) continue
            crossings += crossingsBetween(curve, ends[i], others[j], ends[j], minGap / 2f)
            if (nearlyAlong(mine, sample(others[j]), minGap)) close++
        }
        var hits = 0
        for (obstacle in obstacles) {
            if (obstacle.id == ends[i].fromId || obstacle.id == ends[i].toId) continue
            for (k in 2 until mine.size - 2) if (distance(mine[k], obstacle.at) < obstacle.radius) { hits++; break }
        }
        // A link that leaves its area costs more than any other fault: it is kept inside even if that means running over a control.
        val outside = if (bounds == null) 0 else mine.count { !bounds.contains(it) }
        return crossings * 1000f + hits * 200f + close * 60f + abs(curve.shift) * 40f + (if (outside > 0) 2000f + outside * 10f else 0f)
    }

    private fun sample(curve: ArrowCurve): List<Pt> = (0..SAMPLES).map { curve.at(it / SAMPLES.toFloat()) }

    private fun crossingsBetween(a: ArrowCurve, ea: ArrowEnds, b: ArrowCurve, eb: ArrowEnds, ignore: Float): Int {
        val pa = sample(a)
        val pb = sample(b)
        val sharedEnd = ea.toId == eb.toId || ea.fromId == eb.fromId
        // Links that share a control meet there: only what happens away from it counts.
        val away = if (sharedEnd) ignore * 6f else ignore
        for (i in 0 until pa.size - 1) for (j in 0 until pb.size - 1) {
            val hit = intersection(pa[i], pa[i + 1], pb[j], pb[j + 1]) ?: continue
            if (distance(hit, a.start) < away || distance(hit, a.end) < away || distance(hit, b.start) < away || distance(hit, b.end) < away) continue
            return 1
        }
        return 0
    }

    private fun nearlyAlong(a: List<Pt>, b: List<Pt>, gap: Float): Boolean {
        // Only the middle of each link counts: near its ends two links are always close.
        for (i in 3 until a.size - 3) for (j in 3 until b.size - 3) if (distance(a[i], b[j]) < gap) return true
        return false
    }

    private fun distance(a: Pt, b: Pt): Float = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    private fun intersection(p1: Pt, p2: Pt, p3: Pt, p4: Pt): Pt? {
        val d = (p2.x - p1.x) * (p4.y - p3.y) - (p2.y - p1.y) * (p4.x - p3.x)
        if (abs(d) < 1e-6f) return null
        val t = ((p3.x - p1.x) * (p4.y - p3.y) - (p3.y - p1.y) * (p4.x - p3.x)) / d
        val u = ((p3.x - p1.x) * (p2.y - p1.y) - (p3.y - p1.y) * (p2.x - p1.x)) / d
        if (t < 0f || t > 1f || u < 0f || u > 1f) return null
        return Pt(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y))
    }
}
