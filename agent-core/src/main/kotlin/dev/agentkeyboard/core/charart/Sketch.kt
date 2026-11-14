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
 * ellipse and fill, each undoable. Steps are kept in 0..1 coordinates and drawn at [size] pixels,
 * so the result does not depend on the screen. Nothing is stored.
 *
 * Review of stage 53 (M3): the finished steps are drawn once and kept ([rasterize] without an
 * extra step costs nothing); [add] draws only the new step onto a copy, [undo] draws all steps
 * again once. The step a finger is still drawing goes through [preview], which reuses one buffer
 * and draws only the new points of a pen or eraser stroke.
 */
class Sketch(val size: Int = SIZE) {
    private val steps = mutableListOf<SketchOp>()
    val ops: List<SketchOp> get() = steps
    val isEmpty: Boolean get() = steps.isEmpty()
    /** All finished steps drawn; null when it has to be drawn again (after an undo). Never changed once returned. */
    private var finished: SketchRaster? = null
    /** The finished steps plus the step being drawn; reused by every [preview]. */
    private var live: SketchRaster? = null
    /** What [live] holds of a stroke being drawn: its base, kind and how many of its points. */
    private var liveBase: SketchRaster? = null
    private var liveWidth = -1f
    private var liveErase = false
    private var livePoints = 0
    private var liveLast: SketchPoint? = null

    /** Adds [op]; false (and nothing added) once [MAX_OPS] steps are drawn or for a stroke without points. */
    fun add(op: SketchOp): Boolean {
        if (steps.size >= MAX_OPS) return false
        if (op is SketchOp.Stroke && op.points.isEmpty()) return false
        val step = when (op) {
            is SketchOp.Stroke -> op.copy(points = op.points.take(MAX_POINTS).map { it.clamped() })
            is SketchOp.Line -> op.copy(from = op.from.clamped(), to = op.to.clamped())
            is SketchOp.Ellipse -> op.copy(from = op.from.clamped(), to = op.to.clamped())
            is SketchOp.Fill -> op.copy(at = op.at.clamped())
        }
        steps += step
        // only the new step is drawn, onto a copy: rasters handed out before stay as they were
        finished = finished?.let { done -> copyOf(done).also { draw(step, it.ink, it.erased) } }
        return true
    }

    /** Takes back the last step; false if there is none. */
    fun undo(): Boolean {
        if (steps.removeLastOrNull() == null) return false
        finished = null
        return true
    }

    fun clear() {
        steps.clear()
        finished = null
    }

    /**
     * All steps drawn at [size]×[size] pixels, with [extra] (a step still being drawn) on top. Without
     * [extra] this is the kept raster of the finished steps; with it, the same as [preview].
     */
    fun rasterize(extra: SketchOp? = null): SketchRaster = if (extra == null) finishedRaster() else preview(extra)

    /**
     * The finished steps with [op] on top, while a finger still draws it. The raster is reused by the
     * next call and by nothing else: read it before the next preview. A pen or eraser stroke that
     * continues the one shown last only draws its new points; the result is the same as drawing
     * the whole step, because a stroke paints every pixel the same way however often it is hit.
     */
    fun preview(op: SketchOp): SketchRaster {
        val base = finishedRaster()
        val buffer = live ?: SketchRaster(Raster(size, size), Raster(size, size)).also { live = it }
        val stroke = op as? SketchOp.Stroke
        val count = stroke?.points?.size?.coerceAtMost(MAX_POINTS) ?: 0
        val continues = stroke != null && liveBase === base && liveWidth == stroke.width && liveErase == stroke.erase &&
            livePoints in 1..count && stroke.points[livePoints - 1] == liveLast
        if (!continues) {
            base.ink.values.copyInto(buffer.ink.values)
            base.erased.values.copyInto(buffer.erased.values)
        }
        if (stroke != null) {
            drawStroke(stroke, if (continues) livePoints else 0, count, buffer.ink, buffer.erased)
            liveBase = base; liveWidth = stroke.width; liveErase = stroke.erase
            livePoints = count; liveLast = if (count > 0) stroke.points[count - 1] else null
        } else {
            draw(op, buffer.ink, buffer.erased)
            liveBase = null; livePoints = 0; liveLast = null
        }
        return buffer
    }

    private fun finishedRaster(): SketchRaster = finished ?: SketchRaster(Raster(size, size), Raster(size, size)).also { raster ->
        for (op in steps) draw(op, raster.ink, raster.erased)
        finished = raster
    }

    private fun copyOf(raster: SketchRaster) = SketchRaster(Raster(size, size, raster.ink.values.copyOf()), Raster(size, size, raster.erased.values.copyOf()))

    private fun radius(width: Float) = max(0.5f, width * size / 2f)

    private fun draw(op: SketchOp, ink: Raster, erased: Raster) {
        when (op) {
            is SketchOp.Stroke -> drawStroke(op, 0, op.points.size.coerceAtMost(MAX_POINTS), ink, erased)
            is SketchOp.Line -> segment(op.from, op.to, radius(op.width)) { x, y -> ink[x, y] = 1f; erased[x, y] = 0f }
            is SketchOp.Ellipse -> ellipse(op, radius(op.width)) { x, y -> ink[x, y] = 1f; erased[x, y] = 0f }
            is SketchOp.Fill -> fill(op.at, ink, erased)
        }
    }

    /** The segments of [stroke] ending at its points [from] until [until] (the first one is a dot). */
    private fun drawStroke(stroke: SketchOp.Stroke, from: Int, until: Int, ink: Raster, erased: Raster) {
        val points = stroke.points
        val r = radius(stroke.width)
        for (i in from until until) {
            val a = points[if (i == 0) 0 else i - 1]
            segment(a, points[i], r) { x, y ->
                if (stroke.erase) { ink[x, y] = 0f; erased[x, y] = 1f } else { ink[x, y] = 1f; erased[x, y] = 0f }
            }
        }
    }

    /** Every pixel whose centre is at most [r] pixels from the segment [a]–[b] (points kept on the area). */
    private inline fun segment(a: SketchPoint, b: SketchPoint, r: Float, paint: (Int, Int) -> Unit) {
        val ax = a.x.coerceIn(0f, 1f) * size; val ay = a.y.coerceIn(0f, 1f) * size
        val bx = b.x.coerceIn(0f, 1f) * size; val by = b.y.coerceIn(0f, 1f) * size
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

    /** 4-neighbour flood fill on the pixel indices themselves: no pairs, no boxing (review of stage 53, M3). */
    private fun fill(at: SketchPoint, ink: Raster, erased: Raster) {
        val n = size
        val startX = (at.x * n).toInt().coerceIn(0, n - 1)
        val startY = (at.y * n).toInt().coerceIn(0, n - 1)
        val inkValues = ink.values
        val erasedValues = erased.values
        val start = startY * n + startX
        if (inkValues[start] >= 0.5f) return
        val total = n * n
        val seen = BooleanArray(total)
        val stack = IntArray(total)
        var top = 0
        stack[top++] = start
        seen[start] = true
        while (top > 0) {
            val index = stack[--top]
            inkValues[index] = 1f
            erasedValues[index] = 0f
            val x = index % n
            if (x > 0) { val next = index - 1; if (!seen[next] && inkValues[next] < 0.5f) { seen[next] = true; stack[top++] = next } }
            if (x < n - 1) { val next = index + 1; if (!seen[next] && inkValues[next] < 0.5f) { seen[next] = true; stack[top++] = next } }
            if (index >= n) { val next = index - n; if (!seen[next] && inkValues[next] < 0.5f) { seen[next] = true; stack[top++] = next } }
            if (index < total - n) { val next = index + n; if (!seen[next] && inkValues[next] < 0.5f) { seen[next] = true; stack[top++] = next } }
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
