// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 31: is a quick tap while a drawn symbol waits for its next stroke a dot of that symbol, or
 * a letter? A tap on its own looks exactly like a dot, so the drawing decides:
 *
 * A tap is a dot only when (1) some known symbol has dots (built-in: ÷; own symbols drawn with
 * dots), (2) the strokes drawn so far, without earlier dots, look like that symbol without its dots
 * (a line for ÷; `$P` distance at most [BASE_DISTANCE]), (3) the tap lies near one of that
 * symbol's dots, measured after laying the symbol's dotless part over the drawn strokes (same
 * centre, same larger side; at most [DOT_RADIUS] of the symbol's size away), and (4) no whole
 * symbol fits the strokes clearly better than that dotless part ([CLEAR_FACTOR]): a finished → is
 * close enough to a line for (2), but it is an arrow, so a tap after it is a letter.
 *
 * Trade-off: a letter tapped within the pause right above or below the middle of a drawn dash
 * becomes a dot (and the drawing ÷). Every other tap types its letter after the symbol.
 */
class InkDots(templates: List<InkTemplate>) {
    /** A symbol with dots: its dotless part as a cloud, that part's box and the dots, in the template's units. */
    private class Shape(val base: InkCloud, val box: Box, val dots: List<Point>)

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
    /** All symbols, for (4); asked only for a tap that passed (1) to (3). */
    private val whole by lazy { InkRecognizer(templates) }

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
            // lay the dotless part over the drawn lines: same centre, same larger side
            val scale = shape.box.size / box.size
            val tx = shape.box.cx + (x - box.cx) * scale
            val ty = shape.box.cy + (y - box.cy) * scale
            if (shape.dots.none { hypot(it.x - tx, it.y - ty) <= DOT_RADIUS }) continue
            val best = whole.recognize(cloud).candidates.firstOrNull()?.distance ?: return true
            return match.distance <= best * CLEAR_FACTOR + CLEAR_SLACK
        }
        return false
    }

    companion object {
        /** A stroke whose larger side is at most this share of the whole symbol is a dot. */
        const val DOT_EXTENT = 0.08f
        /** The drawn strokes must be this close to the dotless part of a symbol (as close as an accepted drawing). */
        const val BASE_DISTANCE = InkRecognizer.ACCEPT_DISTANCE
        /** How far a tap may be from a dot, in shares of the symbol's larger side. */
        const val DOT_RADIUS = 0.3f
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
            return Shape(base, Box.of(lines), dots)
        }
    }
}
