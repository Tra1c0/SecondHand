// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stage 31, reworked in stage 43: is a quick tap while a drawn symbol waits for its next stroke a
 * dot of that symbol, or a letter? A tap on its own looks exactly like a dot, so the drawing decides,
 * in two steps:
 *
 * [isDot], when the tap comes: a tap is a possible dot when (1) some known symbol has dots (built-in:
 * ÷, %, ‰, !, ¡, ?, ¿; own symbols drawn with dots), (2) the strokes drawn so far, without earlier
 * dots, look like that symbol without its dots (a line for ÷ or !, a slash for %; distance of the
 * recognizer at most [BASE_DISTANCE]) and the drawing has fewer dots than that symbol, (3) the tap
 * lies within [DOT_REACH] of one of that symbol's dots, measured after laying the symbol's dotless part
 * over the drawn strokes (same centre, same larger side, a tilted line turned straight), and (4) no
 * whole symbol fits the strokes clearly better than that dotless part ([CLEAR_FACTOR]): a finished →
 * is close enough to a line for (2), but it is an arrow, so a tap after it is a letter.
 *
 * [keeps], when the drawing is written: the possible dots stay dots only when the whole drawing, dots
 * included, is recognized as a symbol with exactly as many dots as the drawing has. Otherwise
 * SymbolInk writes the drawing without them and types them as letters after it, in order. Stage 43
 * (owner feedback on build 19): with the strict position rule of stage 31 (0.12 to the side, 0.25
 * nearer or farther) a third of all ÷ and nearly every % with dots ended as symbol and letter.
 *
 * Trade-off: a possible dot waits with its letter until the drawing is written (at most the pause);
 * a letter is lost only when the drawing with it is a whole symbol with dots (two letters above and
 * below a dash, a letter below a vertical line: !). Not a rule: asking the recognizer whether the
 * drawing with one tap fits ÷ better than "–" (stage 31 fix, tried): with one dot it is lopsided.
 *
 * Pass the keyboard's own [whole] recognizer: rule 4 and [keeps] ask it for the drawings that are
 * written, and its memo answers the second question without matching again.
 */
class InkDots(templates: List<InkTemplate>, private val whole: InkRecognizer = InkRecognizer(templates)) {
    /**
     * A symbol with dots: its dotless part as a cloud, that part's box, the dots, in the template's
     * units, and the direction of the dotless part when it is long and thin (a line), else null.
     */
    private class Shape(val base: InkCloud, val box: Box, val dots: List<Point>, val axis: Float?, val text: String)

    private class Point(val x: Float, val y: Float)

    private class Box(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
        val size: Float get() = max(maxX - minX, maxY - minY)
        val cx: Float get() = (minX + maxX) / 2
        val cy: Float get() = (minY + maxY) / 2

        companion object {
            fun of(strokes: List<InkStroke>): Box {
                var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
                for (s in strokes) for (i in 0 until s.size) {
                    minX = min(minX, s.x(i)); maxX = max(maxX, s.x(i))
                    minY = min(minY, s.y(i)); maxY = max(maxY, s.y(i))
                }
                return Box(minX, minY, maxX, maxY)
            }
        }
    }

    private val shapes: List<Shape> = templates.mapNotNull { shapeOf(it.cloud, it.text) }
    /** Matches drawn strokes against the dotless parts; the "text" of a match is the index in [shapes]. */
    private val bases = InkRecognizer(shapes.mapIndexed { index, shape -> InkTemplate(index.toString(), shape.base) })
    /** True when no known symbol has dots: every tap is a letter. */
    val isEmpty: Boolean get() = shapes.isEmpty()

    /** Stage 43: the numbers of dots the drawings of every text have (0 for a drawing without dots). */
    private val dotCounts: Map<String, Set<Int>> = templates.groupBy { it.text }.mapValues { (_, t) -> t.map { it.cloud.dotCount }.toSet() }

    private fun hasDots(text: String) = dotCounts[text]?.any { it > 0 } == true

    /**
     * Whether a tap at ([x], [y]) may be a dot of the drawing [strokes] (screen coordinates, strokes so
     * far, earlier possible dots included); [keeps] decides when the drawing is written.
     */
    fun isDot(strokes: List<InkStroke>, x: Float, y: Float): Boolean {
        if (shapes.isEmpty() || strokes.isEmpty()) return false
        val parts = Parts.of(strokes) ?: return false
        for (match in bases.recognize(parts.cloud).candidates) {
            if (match.distance > BASE_DISTANCE) break
            val shape = shapes[match.text.toInt()]
            if (parts.dots.size >= shape.dots.size) continue // all its dots are there: a letter
            val (tx, ty) = parts.overlay(shape, x, y)
            if (shape.dots.none { hypot(tx - it.x, ty - it.y) <= DOT_REACH * shape.box.size }) continue
            // (4) the drawing as it would be written now: the same question as when the tap is a letter;
            // stage 43: only symbols without dots count (with two dots % beat the slash of ‰ here)
            val best = whole.recognize(strokes).candidates.firstOrNull { !hasDots(it.text) }?.distance ?: return true
            if (match.distance <= best * CLEAR_FACTOR + CLEAR_SLACK) return true
        }
        return false
    }

    /**
     * Stage 43: whether the possible dots [taps] (indices in [strokes]) stay dots of the drawing when
     * it is written: the whole drawing is recognized as a symbol with dots, a drawing of it has exactly
     * as many dots, and every dot of the drawing lies within [KEEP_REACH] of its own dot of that drawing
     * (the recognizer alone hardly sees where two of 32 points lie: two letters anywhere above and below
     * a dash were ÷). False: the drawing is written without them, and they are typed as letters.
     */
    fun keeps(strokes: List<InkStroke>, taps: Collection<Int>): Boolean {
        if (taps.isEmpty()) return true
        val cloud = InkCloud.of(strokes) ?: return false
        val best = whole.recognize(cloud).best ?: return false
        val parts = Parts.of(strokes) ?: return false
        if (parts.dots.isEmpty()) return false
        return shapes.any { shape -> shape.text == best.text && shape.dots.size == parts.dots.size && fits(parts, shape) }
    }

    /** Every drawn dot near its own dot of [shape] (the nearest free one, in turn). */
    private fun fits(parts: Parts, shape: Shape): Boolean {
        val free = shape.dots.toMutableList()
        for (dot in parts.dots) {
            val (tx, ty) = parts.overlay(shape, dot.x, dot.y)
            val nearest = free.minByOrNull { hypot(tx - it.x, ty - it.y) } ?: return false
            if (hypot(tx - nearest.x, ty - nearest.y) > KEEP_REACH * shape.box.size) return false
            free -= nearest
        }
        return true
    }

    /** A drawing as its lines (without dots, as a cloud and a box) and the centres of its dots. */
    private class Parts(val cloud: InkCloud, val box: Box, val dots: List<Point>) {
        /**
         * ([x], [y]) in the units of [shape]: its dotless part laid over the drawn lines, same centre, same
         * larger side, and a tilted line turned straight, so that "above its middle" means above the line as drawn.
         */
        fun overlay(shape: Shape, x: Float, y: Float): Pair<Float, Float> {
            val scale = shape.box.size / box.size
            var px = x - box.cx; var py = y - box.cy
            val turn = turn(axisOf(cloud.xs, cloud.ys), shape.axis)
            if (turn != 0f) { val c = cos(turn); val s = sin(turn); val rx = px * c - py * s; py = px * s + py * c; px = rx }
            return shape.box.cx + px * scale to shape.box.cy + py * scale
        }

        companion object {
            fun of(strokes: List<InkStroke>): Parts? {
                val drawn = strokes.filter { it.size > 0 }
                if (drawn.isEmpty()) return null
                val size = Box.of(drawn).size
                // earlier dots are not part of what the dotless part is compared with
                val (lines, dots) = drawn.partition { Box.of(listOf(it)).size > DOT_EXTENT * size }
                if (lines.isEmpty()) return null
                val cloud = InkCloud.of(lines) ?: return null
                val box = Box.of(lines)
                if (box.size <= 0f) return null
                return Parts(cloud, box, dots.map { d -> Point((0 until d.size).map { d.x(it) }.average().toFloat(), (0 until d.size).map { d.y(it) }.average().toFloat()) })
            }
        }
    }

    companion object {
        /** At most this much tilt (radians, 20°) is turned straight; more is no longer the same symbol. */
        private const val MAX_TURN = 0.35f

        /** The angle from [drawn] to [template] (both directions of long thin shapes), or 0. */
        private fun turn(drawn: Float?, template: Float?): Float {
            if (drawn == null || template == null) return 0f
            var turn = template - drawn
            while (turn > PI / 2) turn -= PI.toFloat()
            while (turn <= -PI / 2) turn += PI.toFloat()
            return if (abs(turn) <= MAX_TURN) turn else 0f
        }

        /** The direction of the main axis of the points, or null unless they lie close to a line. */
        private fun axisOf(xs: FloatArray, ys: FloatArray): Float? {
            val mx = xs.average().toFloat(); val my = ys.average().toFloat()
            var sxx = 0f; var syy = 0f; var sxy = 0f
            for (i in xs.indices) { val dx = xs[i] - mx; val dy = ys[i] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
            val half = (sxx - syy) / 2
            val spread = sqrt(half * half + sxy * sxy)
            val major = (sxx + syy) / 2 + spread; val minor = (sxx + syy) / 2 - spread
            // a line: across it at most a fifth as wide as along it
            if (major <= 0f || minor > major / 25) return null
            return 0.5f * atan2(2 * sxy, sxx - syy)
        }

        /** A stroke whose larger side is at most this share of the whole symbol is a dot. */
        const val DOT_EXTENT = 0.08f
        /** The drawn strokes must be this close to the dotless part of a symbol (stage 43: as a clear drawing, before as an accepted one, 1.8). */
        const val BASE_DISTANCE = InkRecognizer.CLEAR_DISTANCE
        /**
         * Stage 43: how far a possible dot may be from a dot of the symbol, in shares of the larger side
         * of its dotless part (stage 31: 0.12 to the side and 0.25 nearer or farther, before a 0.3
         * circle); [keeps] sorts out the letters (docs/VALIDATION.md, stage 43).
         */
        const val DOT_REACH = 0.5f
        /**
         * Stage 43: how far a kept dot may be from its dot of the symbol, same unit as [DOT_REACH]
         * (docs/VALIDATION.md, stage 43: between the finger model's ÷ and two letters around a dash).
         */
        const val KEEP_REACH = 0.35f
        /** The dotless part may fit up to this factor (plus [CLEAR_SLACK]) worse than the best whole symbol. */
        const val CLEAR_FACTOR = 1.25f
        const val CLEAR_SLACK = 0.1f

        /** The dotless part and the dots of a template cloud, or null if it has no dots or nothing else. */
        private fun shapeOf(cloud: InkCloud, text: String): Shape? {
            val strokes = LinkedHashMap<Int, MutableList<Point>>()
            for (i in 0 until InkCloud.SIZE) strokes.getOrPut(cloud.strokes[i]) { ArrayList() } += Point(cloud.xs[i], cloud.ys[i])
            val dots = ArrayList<Point>()
            val lines = ArrayList<InkStroke>()
            // a normalized cloud's larger side is 1
            for (points in strokes.values) {
                val stroke = InkStroke(FloatArray(2 * points.size) { if (it % 2 == 0) points[it / 2].x else points[it / 2].y })
                if (Box.of(listOf(stroke)).size <= DOT_EXTENT) dots += Point(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
                else lines += stroke
            }
            if (dots.isEmpty() || lines.isEmpty()) return null
            val base = InkCloud.of(lines) ?: return null
            return Shape(base, Box.of(lines), dots, axisOf(base.xs, base.ys), text)
        }
    }
}
