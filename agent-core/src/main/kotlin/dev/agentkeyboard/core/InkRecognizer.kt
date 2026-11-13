// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Drawn symbols (stage 27): a local `$P` point-cloud recognizer (Vatavu, Anthony, Wobbrock 2012).
 * A drawing is one or more strokes; the recognizer ignores stroke order and direction and compares
 * clouds of [InkCloud.SIZE] points. No network, no learning: the templates are the built-in list
 * ([InkSymbols]) plus the own symbols the user drew in the settings.
 */

/** One stroke as raw coordinates (x0, y0, x1, y1, …), screen orientation (y grows downward). */
class InkStroke(val coordinates: FloatArray) {
    init { require(coordinates.size % 2 == 0) { "x and y pairs" } }
    val size: Int get() = coordinates.size / 2
    fun x(i: Int) = coordinates[2 * i]
    fun y(i: Int) = coordinates[2 * i + 1]

    /** Path length; 0 for a dot. */
    val length: Float get() {
        var total = 0f
        for (i in 1 until size) total += hypot(x(i) - x(i - 1), y(i) - y(i - 1))
        return total
    }

    companion object {
        fun of(vararg xy: Float) = InkStroke(xy)
    }
}

/**
 * A normalized point cloud: exactly [SIZE] points, scaled uniformly into the unit box (the aspect
 * ratio stays, so "–" and "|" differ) and centred on its centroid. [strokes] keeps the stroke
 * number of every point (only for storage and drawing previews; matching ignores it).
 */
class InkCloud internal constructor(val xs: FloatArray, val ys: FloatArray, val strokes: IntArray) {
    init { require(xs.size == SIZE && ys.size == SIZE && strokes.size == SIZE) }

    companion object {
        const val SIZE = 32
        /** At most this many strokes count; a symbol with more is not a symbol. */
        const val MAX_STROKES = 12
        /** A short stroke or a dot still gets this share of the points, so dots (÷) count. */
        private const val MIN_SHARE = 0.06f

        /** The cloud of [strokes], or null without points or with more than [MAX_STROKES] strokes. */
        fun of(strokes: List<InkStroke>): InkCloud? {
            val drawn = strokes.filter { it.size > 0 }
            if (drawn.isEmpty() || drawn.size > MAX_STROKES) return null
            val lengths = drawn.map { it.length }
            val total = lengths.sum()
            val shares = lengths.map { if (total <= 0f) 1f else max(it, MIN_SHARE * total) }
            val counts = apportion(shares, SIZE)
            val xs = FloatArray(SIZE); val ys = FloatArray(SIZE); val ids = IntArray(SIZE)
            var next = 0
            drawn.forEachIndexed { index, stroke ->
                resample(stroke, counts[index]) { x, y ->
                    xs[next] = x; ys[next] = y; ids[next] = index; next++
                }
            }
            return normalized(xs, ys, ids)
        }

        /**
         * A cloud from stored points (already normalized once, see [OwnInkSymbols]); normalized
         * again so a stored cloud matches exactly like a fresh one. Null for a wrong point count
         * or values that are not finite.
         */
        fun fromPoints(xs: FloatArray, ys: FloatArray, strokes: IntArray): InkCloud? {
            if (xs.size != SIZE || ys.size != SIZE || strokes.size != SIZE) return null
            if (xs.any { !it.isFinite() } || ys.any { !it.isFinite() }) return null
            if (strokes.any { it < 0 || it >= MAX_STROKES }) return null
            return normalized(xs.copyOf(), ys.copyOf(), strokes.copyOf())
        }

        /** [cloud] turned by [degrees] around its centroid, normalized again. */
        internal fun rotated(cloud: InkCloud, degrees: Float): InkCloud {
            val r = Math.toRadians(degrees.toDouble())
            val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
            val xs = FloatArray(SIZE) { cloud.xs[it] * c - cloud.ys[it] * s }
            val ys = FloatArray(SIZE) { cloud.xs[it] * s + cloud.ys[it] * c }
            return normalized(xs, ys, cloud.strokes.copyOf())
        }

        /** Largest remainder: [n] points over the shares, every stroke at least one. */
        private fun apportion(shares: List<Float>, n: Int): IntArray {
            val sum = shares.sum()
            val exact = shares.map { it / sum * (n - shares.size) }
            val counts = IntArray(shares.size) { 1 + floor(exact[it]).toInt() }
            var left = n - counts.sum()
            val order = exact.indices.sortedByDescending { exact[it] - floor(exact[it]) }
            var i = 0
            while (left > 0) { counts[order[i % order.size]]++; left--; i++ }
            return counts
        }

        /** [count] points evenly along the stroke (both ends included), a single point at its middle. */
        private inline fun resample(stroke: InkStroke, count: Int, out: (Float, Float) -> Unit) {
            val length = stroke.length
            if (stroke.size == 1 || length <= 0f) {
                repeat(count) { out(stroke.x(0), stroke.y(0)) }
                return
            }
            for (k in 0 until count) {
                val target = if (count == 1) length / 2 else length * k / (count - 1)
                var walked = 0f
                var placed = false
                for (i in 1 until stroke.size) {
                    val segment = hypot(stroke.x(i) - stroke.x(i - 1), stroke.y(i) - stroke.y(i - 1))
                    if (segment > 0f && walked + segment >= target) {
                        val t = (target - walked) / segment
                        out(stroke.x(i - 1) + t * (stroke.x(i) - stroke.x(i - 1)),
                            stroke.y(i - 1) + t * (stroke.y(i) - stroke.y(i - 1)))
                        placed = true
                        break
                    }
                    walked += segment
                }
                if (!placed) out(stroke.x(stroke.size - 1), stroke.y(stroke.size - 1))
            }
        }

        private fun normalized(xs: FloatArray, ys: FloatArray, ids: IntArray): InkCloud {
            val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
            val scale = max(maxX - minX, maxY - minY).let { if (it > 1e-6f) it else 1f }
            var cx = 0f; var cy = 0f
            for (i in 0 until SIZE) {
                xs[i] = (xs[i] - minX) / scale; ys[i] = (ys[i] - minY) / scale
                cx += xs[i]; cy += ys[i]
            }
            cx /= SIZE; cy /= SIZE
            for (i in 0 until SIZE) { xs[i] -= cx; ys[i] -= cy }
            return InkCloud(xs, ys, ids)
        }
    }
}

/** A symbol the recognizer knows: the [text] it inserts and one drawing of it. */
class InkTemplate(val text: String, val cloud: InkCloud, val own: Boolean = false)

/** A candidate: [distance] is the `$P` distance of its closest template (smaller is better). */
class InkMatch(val text: String, val distance: Float, val own: Boolean) {
    override fun toString() = "$text(${"%.2f".format(distance)})"
}

/** The outcome of one drawing: [best] is null when nothing was close enough ("Nicht erkannt"). */
class InkResult(val candidates: List<InkMatch>) {
    val best: InkMatch? get() = candidates.firstOrNull()?.takeIf { first ->
        first.distance <= InkRecognizer.ACCEPT_DISTANCE ||
            // a sloppy drawing is still clear when nothing else comes close
            (first.distance <= InkRecognizer.CLEAR_DISTANCE &&
                (candidates.getOrNull(1)?.distance ?: Float.MAX_VALUE) >= first.distance * InkRecognizer.CLEAR_MARGIN)
    }
    /** Up to [InkRecognizer.ALTERNATIVES] further candidates after [best], each also close enough. */
    val alternatives: List<InkMatch> get() = if (best == null) emptyList()
        else candidates.drop(1).filter { it.distance <= InkRecognizer.ALTERNATIVE_DISTANCE }.take(InkRecognizer.ALTERNATIVES)
}

class InkRecognizer(templates: List<InkTemplate>) {
    private val templates = templates.toList()

    /** All texts ranked by their closest template, at most one entry per text. */
    fun recognize(strokes: List<InkStroke>): InkResult {
        val cloud = InkCloud.of(strokes) ?: return InkResult(emptyList())
        return recognize(cloud)
    }

    fun recognize(cloud: InkCloud): InkResult {
        val best = LinkedHashMap<String, InkMatch>()
        // a hand rarely draws exactly upright: also try the drawing slightly turned (pure $P has no
        // rotation tolerance); far less than the 45° between → and ↗ or + and ×
        val turned = TOLERATED_ROTATIONS.map { if (it == 0f) cloud else InkCloud.rotated(cloud, it) }
        for (template in templates) {
            var d = Float.MAX_VALUE
            for (candidate in turned) d = min(d, greedyCloudMatch(candidate, template.cloud, d))
            val known = best[template.text]
            // an own symbol with the same text as a built-in one is the same candidate
            if (known == null || d < known.distance) best[template.text] = InkMatch(template.text, d, template.own)
        }
        return InkResult(best.values.sortedBy { it.distance })
    }

    companion object {
        /**
         * The best match must be at least this close. Calibrated with [InkSymbols] (see
         * InkRecognizerTest): perturbed built-in drawings stay well below, random scribbles above.
         */
        const val ACCEPT_DISTANCE = 1.3f
        /** Further away, the best match still counts when the next one is [CLEAR_MARGIN] times as far. */
        const val CLEAR_DISTANCE = 2.0f
        const val CLEAR_MARGIN = 1.2f
        /** Alternatives further away than this are not offered (they would only be noise). */
        const val ALTERNATIVE_DISTANCE = 2.2f
        const val ALTERNATIVES = 3
        /** Degrees the drawing is also turned by before matching. */
        val TOLERATED_ROTATIONS = floatArrayOf(-10f, 0f, 10f).toList()

        /** `$P` GreedyCloudMatch with epsilon 0.5: both directions from every `step`-th start. */
        fun greedyCloudMatch(a: InkCloud, b: InkCloud, bound: Float = Float.MAX_VALUE): Float {
            val n = InkCloud.SIZE
            val step = floor(n.toDouble().pow(0.5)).toInt()
            var best = bound
            var start = 0
            while (start < n) {
                best = min(best, min(cloudDistance(a, b, start, best), cloudDistance(b, a, start, best)))
                start += step
            }
            return best
        }

        private fun cloudDistance(a: InkCloud, b: InkCloud, start: Int, bound: Float): Float {
            val n = InkCloud.SIZE
            val matched = BooleanArray(n)
            var sum = 0f
            var i = start
            var weightIndex = 0
            do {
                var nearest = -1
                var nearestDistance = Float.MAX_VALUE
                for (j in 0 until n) if (!matched[j]) {
                    val dx = a.xs[i] - b.xs[j]; val dy = a.ys[i] - b.ys[j]
                    val d = dx * dx + dy * dy
                    if (d < nearestDistance) { nearestDistance = d; nearest = j }
                }
                matched[nearest] = true
                val weight = 1f - weightIndex.toFloat() / n
                sum += weight * sqrt(nearestDistance)
                if (sum >= bound) return sum // cannot beat the best start any more
                weightIndex++
                i = (i + 1) % n
            } while (i != start)
            return sum
        }

        /** Rounds a distance for messages and tests. */
        fun rounded(distance: Float) = (distance * 100).roundToInt() / 100f
    }
}

/**
 * Strokes that belong to one symbol: a stroke that ends less than [pauseMillis] before the next
 * one starts belongs to the same symbol (≠, ÷, €). The keyboard recognizes after the pause.
 */
class InkGroup(val pauseMillis: Long = PAUSE_MILLIS) {
    private val strokes = ArrayList<InkStroke>()
    private var points = 0
    /** End time of the last stroke, or -1 while empty. */
    var lastEnd: Long = -1
        private set

    val isEmpty: Boolean get() = strokes.isEmpty()
    val size: Int get() = strokes.size

    /** When the group is complete, or -1 while empty. */
    val dueAt: Long get() = if (isEmpty) -1 else lastEnd + pauseMillis

    /** True while a new stroke starting at [time] still belongs to this group. */
    fun continuesAt(time: Long) = !isEmpty && time - lastEnd < pauseMillis

    /**
     * Adds a stroke that ended at [endTime]. False (and nothing added) when the group already holds
     * [InkCloud.MAX_STROKES] strokes or [MAX_POINTS] points: such a drawing is no symbol.
     */
    fun add(stroke: InkStroke, endTime: Long): Boolean {
        if (stroke.size == 0) return false
        if (strokes.size >= InkCloud.MAX_STROKES || points + stroke.size > MAX_POINTS) return false
        strokes += stroke
        points += stroke.size
        lastEnd = endTime
        return true
    }

    /** The strokes so far; the group is empty afterwards. */
    fun take(): List<InkStroke> = strokes.toList().also { clear() }

    fun clear() { strokes.clear(); points = 0; lastEnd = -1 }

    companion object {
        const val PAUSE_MILLIS = 500L
        const val MAX_POINTS = 4096
    }
}
