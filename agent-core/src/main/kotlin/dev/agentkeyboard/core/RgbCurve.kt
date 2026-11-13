// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The fixed shapes of stage 26, now presets of [RgbCurve] (stage 29d) and the source of the migration of `wave=`. */
enum class RgbWaveform { SINE, TRIANGLE, SAWTOOTH, STEPS, PULSE }

/**
 * Own "Verlauf" curve of the RGB lighting (stage 29d): x is the position within one cycle of the
 * pattern (time and place together, 0..1), y the position in the colour palette (for "Atmen" the
 * brightness), 0..1. The curve runs through every control point; between them it is a monotone
 * cubic (Fritsch–Carlson), so it never overshoots: flat parts stay flat, values stay within 0..1.
 *
 * Invariants (kept by [of]): 2..[MAX_POINTS] points, x strictly increasing by at least [MIN_GAP],
 * first x = 0, last x = 1, every value finite and within 0..1, rounded to 1/1000. A curve whose two
 * ends lie at the same height is closed: its slope at the ends is shared, so the cycle wraps
 * without a kink. Immutable and allocation-free in [value], so it can run for every key per frame.
 */
class RgbCurve private constructor(val points: List<Point>) {
    data class Point(val x: Float, val y: Float)

    private val xs = FloatArray(points.size) { points[it].x }
    private val ys = FloatArray(points.size) { points[it].y }
    private val ms = tangents(xs, ys)

    /** Ends at the same height: the curve returns to its start, the colours go back and forth. */
    val closed: Boolean get() = abs(ys[0] - ys[ys.size - 1]) <= CLOSED_TOLERANCE

    /**
     * The palette wraps (last colour leads back to the first) when the curve rises or falls by at
     * least half the range over the cycle (Sägezahn, Stufen): then the jump from the end back to
     * the start lands on a neighbouring colour. Closed curves use the open palette, as in stage 26.
     */
    val cyclic: Boolean get() = abs(ys[ys.size - 1] - ys[0]) >= 0.5f

    /** The curve at cycle position [u]; only the fraction of [u] counts. NaN or infinity counts as 0. */
    fun value(u: Float): Float {
        if (!u.isFinite()) return ys[0]
        val x = u - floor(u)
        val n = xs.size
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] <= x) lo = mid else hi = mid
        }
        val h = xs[hi] - xs[lo]
        val t = ((x - xs[lo]) / h).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        val v = (2 * t3 - 3 * t2 + 1) * ys[lo] + (t3 - 2 * t2 + t) * h * ms[lo] +
            (-2 * t3 + 3 * t2) * ys[hi] + (t3 - t2) * h * ms[hi]
        return v.coerceIn(0f, 1f)
    }

    /** Compact text: `x:y` pairs separated by `,`, at most three decimals (`0:0,0.5:1,1:0`). */
    fun encode(): String = points.joinToString(",") { "${num(it.x)}:${num(it.y)}" }

    /** Curve with the point at [index] moved to ([x], [y]); ends keep their x, neighbours keep [MIN_GAP]. */
    fun move(index: Int, x: Float, y: Float): RgbCurve {
        if (index !in points.indices) return this
        val nx = when (index) {
            0 -> 0f
            points.size - 1 -> 1f
            else -> {
                // review: neighbours exactly 2 × MIN_GAP apart can make lo > hi by one ulp in float;
                // coerceIn would throw inside the editor's pointer coroutine and take the keyboard down
                val lo = points[index - 1].x + MIN_GAP
                val hi = points[index + 1].x - MIN_GAP
                if (lo >= hi) points[index].x else finite(x).coerceIn(lo, hi)
            }
        }
        return of(points.toMutableList().also { it[index] = Point(nx, finite(y)) }) ?: this
    }

    /**
     * Curve with a new point at ([x], [y]) (or this curve if it is full or [x] is too close to an
     * existing point), and the index of the new point (-1 if none was added).
     */
    fun add(x: Float, y: Float): Pair<RgbCurve, Int> {
        if (points.size >= MAX_POINTS) return this to -1
        val qx = q(finite(x).coerceIn(0f, 1f))
        if (points.any { abs(it.x - qx) < MIN_GAP - 1e-6f }) return this to -1
        val list = (points + Point(qx, finite(y))).sortedBy { it.x }
        val curve = of(list) ?: return this to -1
        return curve to curve.points.indexOfFirst { it.x == qx }
    }

    /** A new point in the middle of the widest gap, on the curve (the "+" button). */
    fun addInWidestGap(): Pair<RgbCurve, Int> {
        var best = 0
        for (i in 0 until points.size - 1) if (points[i + 1].x - points[i].x > points[best + 1].x - points[best].x) best = i
        val x = (points[best].x + points[best + 1].x) / 2f
        return add(x, value(x))
    }

    /** Curve without the point at [index]; the two ends and the last two points cannot be removed. */
    fun remove(index: Int): RgbCurve {
        if (index <= 0 || index >= points.size - 1 || points.size <= MIN_POINTS) return this
        return of(points.filterIndexed { i, _ -> i != index }) ?: this
    }

    /** True if [index] may be removed (not an end, more than [MIN_POINTS] points). */
    fun canRemove(index: Int) = index > 0 && index < points.size - 1 && points.size > MIN_POINTS

    override fun equals(other: Any?) = other is RgbCurve && other.points == points
    override fun hashCode() = points.hashCode()
    override fun toString() = "RgbCurve(${encode()})"

    companion object {
        const val MIN_POINTS = 2
        /** Enough for the "Stufen" preset with 12 steps (two points per step). */
        const val MAX_POINTS = 24
        /** Smallest distance between neighbouring points: a "jump" still takes 1 % of the cycle. */
        const val MIN_GAP = 0.01f
        private const val CLOSED_TOLERANCE = 0.005f

        val SAWTOOTH = preset(RgbWaveform.SAWTOOTH)
        /** Default of the settings: the stage 26 default "Sägezahn". */
        val DEFAULT = SAWTOOTH

        /**
         * A valid curve from [points], or null if fewer than two usable points remain. Values are
         * clamped to 0..1 and rounded to 1/1000, points sorted by x; a point closer than [MIN_GAP]
         * to the previous one is dropped; the first point is moved to x = 0, the last to x = 1.
         */
        fun of(points: List<Point>): RgbCurve? {
            if (points.size < MIN_POINTS || points.size > MAX_POINTS) return null
            if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
            val sorted = points.map { Point(q(it.x.coerceIn(0f, 1f)), q(it.y.coerceIn(0f, 1f))) }.sortedBy { it.x }
            val kept = ArrayList<Point>(sorted.size)
            for (p in sorted) if (kept.isEmpty() || p.x - kept.last().x >= MIN_GAP - 1e-6f) kept.add(p)
            if (kept.size < MIN_POINTS) return null
            kept[0] = Point(0f, kept[0].y)
            // the last point becomes x = 1; drop points that would then be too close to it
            while (kept.size > MIN_POINTS && 1f - kept[kept.size - 2].x < MIN_GAP - 1e-6f) kept.removeAt(kept.size - 2)
            kept[kept.size - 1] = Point(1f, kept.last().y)
            if (kept[kept.size - 1].x - kept[kept.size - 2].x < MIN_GAP - 1e-6f) return null
            return RgbCurve(kept)
        }

        /** Reads [encode] text; null for anything malformed, too long or with too many points. */
        fun parse(text: String?): RgbCurve? {
            if (text.isNullOrBlank() || text.length > MAX_POINTS * 16) return null
            val parts = text.split(',')
            if (parts.size > MAX_POINTS) return null
            val list = parts.map { part ->
                val i = part.indexOf(':')
                if (i <= 0) return null
                val x = part.substring(0, i).trim().toFloatOrNull() ?: return null
                val y = part.substring(i + 1).trim().toFloatOrNull() ?: return null
                Point(x, y)
            }
            return of(list)
        }

        /** The stage 26 shape [waveform] as control points ([steps] only for [RgbWaveform.STEPS], 2..12). */
        fun preset(waveform: RgbWaveform, steps: Int = 4): RgbCurve = of(when (waveform) {
            RgbWaveform.SAWTOOTH -> listOf(Point(0f, 0f), Point(1f, 1f))
            // nine samples of 0.5 - 0.5 cos(2 pi x): within 0.02 of the old sine
            RgbWaveform.SINE -> (0..8).map { i -> Point(i / 8f, (0.5 - 0.5 * cos(2 * Math.PI * i / 8)).toFloat()) }
            // straight lines; the extra points keep the corners sharp (rounded over 5 % of the cycle)
            RgbWaveform.TRIANGLE -> listOf(Point(0f, 0f), Point(0.05f, 0.1f), Point(0.45f, 0.9f), Point(0.5f, 1f),
                Point(0.55f, 0.9f), Point(0.95f, 0.1f), Point(1f, 0f))
            // eased rise, hold, eased fall, rest: zero slopes at every point give exactly the old smoothstep
            RgbWaveform.PULSE -> listOf(Point(0f, 0f), Point(0.3f, 1f), Point(0.45f, 1f), Point(0.75f, 0f), Point(1f, 0f))
            RgbWaveform.STEPS -> {
                val n = steps.coerceIn(2, 12)
                val list = mutableListOf(Point(0f, 0f))
                for (k in 1 until n) {
                    list.add(Point(k / n.toFloat() - MIN_GAP, (k - 1) / n.toFloat()))
                    list.add(Point(k / n.toFloat(), k / n.toFloat()))
                }
                list.add(Point(1f, (n - 1) / n.toFloat()))
                list
            }
        })!!

        /** The preset this curve equals, or null for an own curve (steps: any count 2..12). */
        fun presetOf(curve: RgbCurve): RgbWaveform? = RgbWaveform.entries.firstOrNull { w ->
            if (w == RgbWaveform.STEPS) stepCount(curve) != null else preset(w) == curve
        }

        /** The number of steps if [curve] is a "Stufen" preset, else null. */
        fun stepCount(curve: RgbCurve): Int? = (2..12).firstOrNull { preset(RgbWaveform.STEPS, it) == curve }

        private fun finite(v: Float) = if (v.isFinite()) v else 0f
        private fun q(v: Float) = (v * 1000f).roundToInt() / 1000f
        private fun num(v: Float): String {
            val i = (v * 1000f).roundToInt()
            if (i % 1000 == 0) return (i / 1000).toString()
            return (i / 1000f).toString().let { if (it.contains('E')) "0" else it }
        }

        /** Fritsch–Carlson tangents; closed curves share one slope at both ends. */
        private fun tangents(xs: FloatArray, ys: FloatArray): FloatArray {
            val n = xs.size
            val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
            val m = FloatArray(n)
            for (k in 1 until n - 1) m[k] = if (d[k - 1] * d[k] <= 0f) 0f else (d[k - 1] + d[k]) / 2f
            if (abs(ys[0] - ys[n - 1]) <= CLOSED_TOLERANCE && n > 2) {
                val a = d[n - 2]
                val b = d[0]
                val shared = if (a * b <= 0f) 0f else (a + b) / 2f
                m[0] = shared
                m[n - 1] = shared
            } else {
                m[0] = d[0]
                m[n - 1] = d[n - 2]
            }
            // keep every segment monotone (no overshoot)
            for (k in 0 until n - 1) {
                if (d[k] == 0f) { m[k] = 0f; m[k + 1] = 0f; continue }
                var a = m[k] / d[k]
                var b = m[k + 1] / d[k]
                if (a < 0f) { m[k] = 0f; a = 0f }
                if (b < 0f) { m[k + 1] = 0f; b = 0f }
                val s = a * a + b * b
                if (s > 9f) {
                    val tau = 3f / sqrt(s)
                    m[k] = tau * a * d[k]
                    m[k + 1] = tau * b * d[k]
                }
            }
            return m
        }
    }
}
