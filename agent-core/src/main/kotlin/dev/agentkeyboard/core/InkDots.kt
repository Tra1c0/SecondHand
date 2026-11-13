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
 * Stage 31: is a quick tap while a drawn symbol waits for its next stroke a dot of that symbol, or
 * a letter? A tap on its own looks exactly like a dot, so the drawing decides:
 *
 * A tap is a dot only when (1) some known symbol has dots (built-in: ÷; own symbols drawn with
 * dots), (2) the strokes drawn so far, without earlier dots, look like that symbol without its dots
 * (a line for ÷; `$P` distance at most [BASE_DISTANCE]), (3) the tap lies close to one of that
 * symbol's dots, measured after laying the symbol's dotless part over the drawn strokes (same
 * centre, same larger side): seen from the centre of the dotless part, at most [DOT_SIDEWAYS] of the
 * symbol's size to the side of the dot and at most [DOT_ALONG] nearer or farther (for ÷: roughly
 * straight above or below the middle of the line), and (4) no whole symbol fits the strokes
 * clearly better than that dotless part ([CLEAR_FACTOR]): a finished → is close enough to a line for
 * (2), but it is an arrow, so a tap after it is a letter.
 *
 * Trade-off: a letter tapped within the pause right above or below the middle of a drawn dash
 * still becomes a dot (and the drawing ÷); about one key per neighbouring row. Every other tap
 * types its letter after the symbol. Not a rule: asking `$P` whether the drawing with the tap fits
 * ÷ better than "–" (stage 31 fix, tried): with one dot it is lopsided, a first dot below the line
 * lost to "–" although it sat exactly where ÷ has it, and it hardly sorted out any letter.
 *
 * Pass the keyboard's own [whole] recognizer: rule 4 asks it for exactly the drawing that is written
 * when the tap is a letter, and its memo answers that second question without matching again.
 */
class InkDots(templates: List<InkTemplate>, private val whole: InkRecognizer = InkRecognizer(templates)) {
    /**
     * A symbol with dots: its dotless part as a cloud, that part's box, the dots, in the template's
     * units, and the direction of the dotless part when it is long and thin (a line), else null.
     */
    private class Shape(val base: InkCloud, val box: Box, val dots: List<Point>, val axis: Float?)

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

    private val shapes: List<Shape> = templates.mapNotNull { shapeOf(it.cloud) }
    /** Matches drawn strokes against the dotless parts; the "text" of a match is the index in [shapes]. */
    private val bases = InkRecognizer(shapes.mapIndexed { index, shape -> InkTemplate(index.toString(), shape.base) })
    /** True when no known symbol has dots: every tap is a letter. */
    val isEmpty: Boolean get() = shapes.isEmpty()

    /** Whether a tap at ([x], [y]) is a dot of the drawing [strokes] (screen coordinates, strokes so far). */
    fun isDot(strokes: List<InkStroke>, x: Float, y: Float): Boolean {
        if (shapes.isEmpty() || strokes.isEmpty()) return false
        val size = Box.of(strokes).size
        // earlier dots are not part of what the dotless part is compared with
        val lines = strokes.filter { it.size > 0 && Box.of(listOf(it)).size > DOT_EXTENT * size }
        if (lines.isEmpty()) return false
        val cloud = InkCloud.of(lines) ?: return false
        val box = Box.of(lines)
        if (box.size <= 0f) return false
        for (match in bases.recognize(cloud).candidates) {
            if (match.distance > BASE_DISTANCE) break
            val shape = shapes[match.text.toInt()]
            // lay the dotless part over the drawn lines: same centre, same larger side, and a tilted
            // line turned straight, so that "above its middle" means above the line as drawn
            val scale = shape.box.size / box.size
            var px = x - box.cx; var py = y - box.cy
            val turn = turn(axisOf(cloud.xs, cloud.ys), shape.axis)
            if (turn != 0f) { val c = cos(turn); val s = sin(turn); val rx = px * c - py * s; py = px * s + py * c; px = rx }
            val tx = shape.box.cx + px * scale
            val ty = shape.box.cy + py * scale
            if (shape.dots.none { near(shape, it, tx, ty) }) continue
            // (4) the drawing as it would be written now: the same question as when the tap is a letter
            val best = whole.recognize(strokes).candidates.firstOrNull()?.distance ?: return true
            return match.distance <= best * CLEAR_FACTOR + CLEAR_SLACK
        }
        return false
    }

    /** (3): the tap ([tx], [ty], template units) roughly in line with [dot] as seen from the centre of the dotless part. */
    private fun near(shape: Shape, dot: Point, tx: Float, ty: Float): Boolean {
        val dx = tx - dot.x; val dy = ty - dot.y
        val ox = dot.x - shape.box.cx; val oy = dot.y - shape.box.cy
        val reach = hypot(ox, oy)
        if (reach < 1e-3f) return hypot(dx, dy) <= DOT_SIDEWAYS // a dot in the middle: no direction
        val along = (dx * ox + dy * oy) / reach
        val sideways = (dx * oy - dy * ox) / reach
        return abs(sideways) <= DOT_SIDEWAYS && abs(along) <= DOT_ALONG
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
        /** The drawn strokes must be this close to the dotless part of a symbol (as close as an accepted drawing). */
        const val BASE_DISTANCE = InkRecognizer.ACCEPT_DISTANCE
        /** How far a tap may be to the side of a dot, in shares of the symbol's larger side (stage 31 fix: was a 0.3 circle). */
        const val DOT_SIDEWAYS = 0.12f
        /** How much nearer to or farther from the dotless part than the dot a tap may be, same unit. */
        const val DOT_ALONG = 0.25f
        /** The dotless part may fit up to this factor (plus [CLEAR_SLACK]) worse than the best whole symbol. */
        const val CLEAR_FACTOR = 1.25f
        const val CLEAR_SLACK = 0.1f

        /** The dotless part and the dots of a template cloud, or null if it has no dots or nothing else. */
        private fun shapeOf(cloud: InkCloud): Shape? {
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
            return Shape(base, Box.of(lines), dots, axisOf(base.xs, base.ys))
        }
    }
}
