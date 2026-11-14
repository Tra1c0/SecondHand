// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.charart

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A point on the square drawing area, 0..1 from the left and from the top. */
data class SketchPoint(val x: Float, val y: Float) {
    fun clamped() = SketchPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/** One step on the drawing area; [width] is a share of the area's side. */
sealed class SketchOp {
    data class Stroke(val points: List<SketchPoint>, val width: Float, val erase: Boolean = false) : SketchOp()
    data class Line(val from: SketchPoint, val to: SketchPoint, val width: Float) : SketchOp()
    /** An ellipse (a circle when drawn square) inside the box from [from] to [to], as an outline. */
    data class Ellipse(val from: SketchPoint, val to: SketchPoint, val width: Float) : SketchOp()
    /** Fills the area around [at] that has no ink, up to the ink around it or the border. */
    data class Fill(val at: SketchPoint) : SketchOp()
}

/** What the drawing area holds: [ink] (1 drawn), and where the eraser went over a photo ([erased], 1). */
class SketchRaster(val ink: Raster, val erased: Raster) {
    /** The part with ink as 0..1 coordinates (left, top, right, bottom), or null without ink. */
    fun inkBounds(): FloatArray? {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (y in 0 until ink.height) for (x in 0 until ink.width) if (ink[x, y] >= 0.5f) {
            minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y)
        }
        if (maxX < 0) return null
        return floatArrayOf(minX.toFloat() / ink.width, minY.toFloat() / ink.height,
            (maxX + 1f) / ink.width, (maxY + 1f) / ink.height)
    }
}

/**
 * The drawing of the character art window as a list of steps (stage 53): pen, eraser, line,
 * ellipse and fill, each undoable. Steps are kept in 0..1 coordinates and drawn again at [size]
 * pixels for every change, so the result does not depend on the screen. Nothing is stored.
 */
class Sketch(val size: Int = SIZE) {
    private val steps = mutableListOf<SketchOp>()
    val ops: List<SketchOp> get() = steps
    val isEmpty: Boolean get() = steps.isEmpty()

    /** Adds [op]; false (and nothing added) once [MAX_OPS] steps are drawn or for a stroke without points. */
    fun add(op: SketchOp): Boolean {
        if (steps.size >= MAX_OPS) return false
        if (op is SketchOp.Stroke && op.points.isEmpty()) return false
        steps += when (op) {
            is SketchOp.Stroke -> op.copy(points = op.points.take(MAX_POINTS).map { it.clamped() })
            is SketchOp.Line -> op.copy(from = op.from.clamped(), to = op.to.clamped())
            is SketchOp.Ellipse -> op.copy(from = op.from.clamped(), to = op.to.clamped())
            is SketchOp.Fill -> op.copy(at = op.at.clamped())
        }
        return true
    }

    /** Takes back the last step; false if there is none. */
    fun undo(): Boolean = steps.removeLastOrNull() != null

    fun clear() = steps.clear()

    /** All steps drawn at [size]×[size] pixels, with [extra] (a line still being dragged) on top. */
    fun rasterize(extra: SketchOp? = null): SketchRaster {
        val ink = Raster(size, size)
        val erased = Raster(size, size)
        for (op in if (extra == null) steps else steps + extra) draw(op, ink, erased)
        return SketchRaster(ink, erased)
    }

    private fun draw(op: SketchOp, ink: Raster, erased: Raster) {
        val radius = { width: Float -> max(0.5f, width * size / 2f) }
        when (op) {
            is SketchOp.Stroke -> {
                val points = op.points
                for (i in points.indices) {
                    val a = points[if (i == 0) 0 else i - 1]
                    segment(a, points[i], radius(op.width)) { x, y ->
                        if (op.erase) { ink[x, y] = 0f; erased[x, y] = 1f } else { ink[x, y] = 1f; erased[x, y] = 0f }
                    }
                }
            }
            is SketchOp.Line -> segment(op.from, op.to, radius(op.width)) { x, y -> ink[x, y] = 1f; erased[x, y] = 0f }
            is SketchOp.Ellipse -> ellipse(op, radius(op.width)) { x, y -> ink[x, y] = 1f; erased[x, y] = 0f }
            is SketchOp.Fill -> fill(op.at, ink, erased)
        }
    }

    /** Every pixel whose centre is at most [r] pixels from the segment [a]–[b]. */
    private inline fun segment(a: SketchPoint, b: SketchPoint, r: Float, paint: (Int, Int) -> Unit) {
        val ax = a.x * size; val ay = a.y * size; val bx = b.x * size; val by = b.y * size
        val left = floor(min(ax, bx) - r).toInt().coerceAtLeast(0)
        val right = floor(max(ax, bx) + r).toInt().coerceAtMost(size - 1)
        val top = floor(min(ay, by) - r).toInt().coerceAtLeast(0)
        val bottom = floor(max(ay, by) + r).toInt().coerceAtMost(size - 1)
        val dx = bx - ax; val dy = by - ay
        val length2 = dx * dx + dy * dy
        for (y in top..bottom) for (x in left..right) {
            val px = x + 0.5f; val py = y + 0.5f
            val t = if (length2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / length2).coerceIn(0f, 1f)
            val ex = ax + t * dx - px; val ey = ay + t * dy - py
            if (ex * ex + ey * ey <= r * r) paint(x, y)
        }
    }

    private inline fun ellipse(op: SketchOp.Ellipse, r: Float, paint: (Int, Int) -> Unit) {
        val cx = (op.from.x + op.to.x) / 2f * size
        val cy = (op.from.y + op.to.y) / 2f * size
        val rx = abs(op.to.x - op.from.x) / 2f * size
        val ry = abs(op.to.y - op.from.y) / 2f * size
        // a box without height or width is only a line
        if (rx < 1f || ry < 1f) { segment(op.from, op.to, r, paint); return }
        val mean = (rx + ry) / 2f
        val left = floor(cx - rx - r).toInt().coerceAtLeast(0)
        val right = floor(cx + rx + r).toInt().coerceAtMost(size - 1)
        val top = floor(cy - ry - r).toInt().coerceAtLeast(0)
        val bottom = floor(cy + ry + r).toInt().coerceAtMost(size - 1)
        for (y in top..bottom) for (x in left..right) {
            val nx = (x + 0.5f - cx) / rx; val ny = (y + 0.5f - cy) / ry
            if (abs(sqrt(nx * nx + ny * ny) - 1f) * mean <= r) paint(x, y)
        }
    }

    private fun fill(at: SketchPoint, ink: Raster, erased: Raster) {
        val startX = (at.x * size).toInt().coerceIn(0, size - 1)
        val startY = (at.y * size).toInt().coerceIn(0, size - 1)
        if (ink[startX, startY] >= 0.5f) return
        val seen = BooleanArray(size * size)
        val stack = IntArray(size * size)
        var top = 0
        stack[top++] = startY * size + startX
        seen[startY * size + startX] = true
        while (top > 0) {
            val index = stack[--top]
            val x = index % size; val y = index / size
            ink[x, y] = 1f; erased[x, y] = 0f
            for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx !in 0 until size || ny !in 0 until size) continue
                val next = ny * size + nx
                if (seen[next] || ink[nx, ny] >= 0.5f) continue
                seen[next] = true
                stack[top++] = next
            }
        }
    }

    companion object {
        /** Pixels per side of the drawing; enough for 40 braille columns of a square picture. */
        const val SIZE = 160
        const val MAX_OPS = 400
        const val MAX_POINTS = 2_000
        /** Pen widths as a share of the side: thin, medium, thick. */
        val WIDTHS = floatArrayOf(0.025f, 0.045f, 0.08f)
    }
}
