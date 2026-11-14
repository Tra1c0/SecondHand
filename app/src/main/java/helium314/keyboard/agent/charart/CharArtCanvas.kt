// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.charart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import dev.agentkeyboard.core.charart.Photo
import dev.agentkeyboard.core.charart.Sketch
import dev.agentkeyboard.core.charart.SketchOp
import dev.agentkeyboard.core.charart.SketchPoint
import dev.agentkeyboard.core.charart.SketchRaster
import helium314.keyboard.latin.R
import kotlin.math.abs
import kotlin.math.min

/**
 * The square drawing area of the character art window (stage 53). It shows what [sketch] draws
 * (at the sketch's own pixels, so it looks like what the art is made of) over the photo template
 * and adds one step per finger: pen and eraser strokes, a line or circle from press to release, a
 * fill where the finger lifts. Nothing leaves the view but [onChanged].
 */
class CharArtCanvas(context: Context, val sketch: Sketch) : View(context) {
    enum class Tool { PEN, ERASER, LINE, CIRCLE, FILL }

    var tool = Tool.PEN
    /** Index into [Sketch.WIDTHS]. */
    var widthIndex = 1
    /** The raster after every change; [done] is false while a finger still draws. */
    var onChanged: ((raster: SketchRaster, done: Boolean) -> Unit)? = null
    var paperColor = 0xFFFFFFFF.toInt()
    var inkColor = 0xFF000000.toInt()
    var frameColor = 0x33000000

    private var photo: Photo? = null
    private var photoBitmap: Bitmap? = null
    /** Shown weaker when it is only a template to draw over. */
    var photoShownFaint = false
        set(value) { field = value; invalidate() }

    private val points = ArrayList<SketchPoint>()
    private var start: SketchPoint? = null
    private var pending: SketchOp? = null
    var raster: SketchRaster = sketch.rasterize()
        private set
    private val pixels = Bitmap.createBitmap(sketch.size, sketch.size, Bitmap.Config.ARGB_8888)
    private val colors = IntArray(sketch.size * sketch.size)
    private val area = RectF()
    private val paint = Paint()
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val framePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = resources.displayMetrics.density }

    init {
        contentDescription = context.getString(R.string.agent_char_art_canvas)
        id = R.id.agent_char_art_canvas
        redraw()
    }

    fun setPhoto(photo: Photo?, bitmap: Bitmap?) {
        this.photo = photo
        photoBitmap = bitmap
        invalidate()
    }

    /** Drops a step still being drawn and draws again, without telling [onChanged] (the window resets). */
    fun reset() {
        pending = null; start = null; points.clear()
        redraw()
    }

    /** Draws the steps again, e.g. after an undo, and tells [onChanged]. */
    fun refresh() {
        redraw()
        onChanged?.invoke(raster, true)
    }

    /**
     * Shows the finished steps plus [pending]. Review of stage 53 (M3): the sketch keeps the finished
     * steps drawn and a stroke in progress only adds its new points, so a move costs the new points
     * and one pass over the pixels, not all steps again.
     */
    private fun redraw() {
        val step = pending
        raster = if (step == null) sketch.rasterize() else sketch.preview(step)
        val size = sketch.size
        val ink = raster.ink.values
        val erased = raster.erased.values
        for (index in colors.indices) colors[index] = when {
            ink[index] >= 0.5f -> inkColor
            erased[index] >= 0.5f -> paperColor
            else -> 0
        }
        pixels.setPixels(colors, 0, size, 0, 0, size, size)
        invalidate()
    }

    /** The square in the middle of the view; touches are read against it, so it is set with the size. */
    private fun updateArea() {
        val side = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat().coerceAtLeast(0f)
        val left = paddingLeft + (width - paddingLeft - paddingRight - side) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - side) / 2f
        area.set(left, top, left + side, top + side)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateArea()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateArea()
        val side = area.width()
        if (side <= 0f) return
        val left = area.left
        val top = area.top
        paint.color = paperColor
        canvas.drawRect(area, paint)
        val place = photo
        val bitmap = photoBitmap
        if (place != null && bitmap != null) {
            photoPaint.alpha = if (photoShownFaint) 90 else 170
            canvas.drawBitmap(bitmap, null, RectF(left + place.left * side, top + place.top * side,
                left + place.right * side, top + place.bottom * side), photoPaint)
        }
        // the drawing's own pixels, sharp
        canvas.drawBitmap(pixels, Rect(0, 0, sketch.size, sketch.size), area, null)
        framePaint.color = frameColor
        canvas.drawRect(area, framePaint)
    }

    private fun point(event: MotionEvent, index: Int = 0): SketchPoint? {
        if (area.width() <= 0f) return null
        return SketchPoint((event.getX(index) - area.left) / area.width(), (event.getY(index) - area.top) / area.height())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val at = point(event) ?: return false
        val width = Sketch.WIDTHS[widthIndex.coerceIn(0, Sketch.WIDTHS.lastIndex)]
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                points.clear(); points += at; start = at
                pending = provisional(at, width)
                // a fill shows only when the finger lifts: nothing to draw now
                if (pending != null) { redraw(); onChanged?.invoke(raster, false) }
            }
            MotionEvent.ACTION_MOVE -> {
                if (start == null) return true
                if (tool == Tool.FILL) return true // no fill computed while dragging (review of stage 53, M3)
                for (h in 0 until event.historySize) {
                    if (area.width() > 0f) addPoint(SketchPoint((event.getHistoricalX(h) - area.left) / area.width(),
                        (event.getHistoricalY(h) - area.top) / area.height()))
                }
                addPoint(at)
                pending = provisional(at, width)
                redraw(); onChanged?.invoke(raster, false)
            }
            MotionEvent.ACTION_UP -> {
                if (start == null) return true
                addPoint(at)
                val op = if (tool == Tool.FILL) SketchOp.Fill(at) else provisional(at, width)
                pending = null
                start = null
                if (op != null) sketch.add(op)
                redraw(); onChanged?.invoke(raster, true)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                pending = null; start = null; points.clear()
                redraw(); onChanged?.invoke(raster, true)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /**
     * A point of the stroke: at most [Sketch.MAX_POINTS] (the sketch would drop the rest anyway) and
     * none closer than half a pixel of the drawing to the one before, which draws nothing new.
     */
    private fun addPoint(point: SketchPoint) {
        if (points.size >= Sketch.MAX_POINTS) return
        val last = points.lastOrNull()
        if (last != null && abs(point.x - last.x) * sketch.size < 0.5f && abs(point.y - last.y) * sketch.size < 0.5f) return
        points += point
    }

    /**
     * The step the finger is drawing now, or null for a fill (that comes only when the finger lifts).
     * A stroke shares the growing list of points: the sketch reads it at once and copies it on [Sketch.add].
     */
    private fun provisional(at: SketchPoint, width: Float): SketchOp? {
        val from = start ?: return null
        return when (tool) {
            Tool.PEN -> SketchOp.Stroke(points, width)
            Tool.ERASER -> SketchOp.Stroke(points, width * 2f, erase = true)
            Tool.LINE -> SketchOp.Line(from, at, width)
            Tool.CIRCLE -> SketchOp.Ellipse(from, at, width)
            Tool.FILL -> null
        }
    }
}
