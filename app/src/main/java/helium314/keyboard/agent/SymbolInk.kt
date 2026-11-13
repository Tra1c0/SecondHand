// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.agentkeyboard.core.InkDots
import dev.agentkeyboard.core.InkGroup
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkSymbols
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.settings.Settings
import java.lang.ref.WeakReference

/**
 * Stage 27 "Symbole malen": the touch side. With the setting on, gesture typing is off and a
 * stroke that HeliBoard would have started as a gesture (same start detection) arrives here as ink
 * instead of going to the dictionary:
 *
 * - The first stroke of a symbol comes through the unchanged batch callbacks of PointerTracker,
 *   routed here by KeyboardActionListenerImpl ([onBatchStart], [onBatchEnd], [onBatchCancel]).
 * - While a symbol waits for its next stroke ([InkGroup.PAUSE_MILLIS] after the last one), a touch
 *   that starts on a letter key is ink right away (PointerTracker asks [continuesInk] and then
 *   reports [onInkDown]/[onInkMove]/[onInkUp]), so short strokes and dots (÷, ≠) count too.
 * - A quick tap in that pause is a dot only where the drawing needs one ([dotCheck], [InkDots]);
 *   any other tap writes the waiting symbol and PointerTracker then types the tapped letter
 *   (stage 31: a letter typed right after a symbol is no longer lost).
 * - After the pause the strokes go to [sink] (the keyboard: [SymbolDrawing]); any other key
 *   press first finishes a waiting symbol ([flush]).
 *
 * Only the first finger draws: points of other pointers in a batch are dropped, and a second
 * finger during a continuation stroke drops the drawing (PointerTracker). Strokes live only in memory
 * until they are recognized; nothing is stored or logged.
 */
object SymbolInk {
    fun interface Sink { fun onDrawing(strokes: List<InkStroke>) }

    private var sinkRef: WeakReference<Sink>? = null
    /** Receives every finished drawing; held weakly (the keyboard service owns it). */
    @JvmStatic var sink: Sink?
        get() = sinkRef?.get()
        set(value) { sinkRef = value?.let { WeakReference(it) } }

    private val group = InkGroup()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val token = Any()
    private val due = Runnable { finish() }

    /** Decides whether a quick tap is a dot of the waiting drawing (stage 31). */
    fun interface DotCheck { fun isDot(strokes: List<InkStroke>, x: Float, y: Float): Boolean }

    private val builtInDots by lazy { InkDots(InkSymbols.templates) }
    /** Dots of the built-in symbols only (÷). */
    val builtInDotCheck = DotCheck { strokes, x, y -> builtInDots.isDot(strokes, x, y) }
    /** [builtInDotCheck] by default; [SymbolDrawing.attach] adds the own symbols. */
    @JvmStatic var dotCheck: DotCheck = builtInDotCheck

    /** The continuation stroke being drawn: its pointer and points, or -1. */
    private var inkPointer = -1
    private var points = FloatArray(256)
    private var pointCount = 0

    /** True when the setting is on; gesture typing is then off (SettingsValues). */
    @JvmStatic fun isEnabled(): Boolean = Settings.getValues()?.mSymbolDrawing == true

    /** A symbol has strokes and waits for more (or for the pause to end). */
    @JvmStatic val isWaiting: Boolean get() = !group.isEmpty

    // --- first stroke: HeliBoard's gesture detection, via KeyboardActionListenerImpl ---

    @JvmStatic fun onBatchStart() {
        handler.removeCallbacksAndMessages(token)
    }

    /** The finished gesture: [pointers] holds all points since the touch went down. */
    @JvmStatic fun onBatchEnd(pointers: InputPointers?) {
        val size = pointers?.pointerSize ?: 0
        if (size == 0) return schedule()
        val ids = pointers!!.pointerIds
        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        val first = ids[0]
        var count = 0
        for (i in 0 until size) if (ids[i] == first) count++
        val coordinates = FloatArray(2 * count)
        var next = 0
        for (i in 0 until size) if (ids[i] == first) {
            coordinates[next++] = xs[i].toFloat(); coordinates[next++] = ys[i].toFloat()
        }
        add(InkStroke(coordinates))
    }

    /** HeliBoard gave up the gesture (moved far above the keyboard, cancelled touch): no symbol. */
    @JvmStatic fun onBatchCancel() = cancel()

    // --- further strokes of a waiting symbol, from PointerTracker ---

    /** PointerTracker: a new touch on a letter key continues the waiting symbol as ink. */
    @JvmStatic fun continuesInk(): Boolean = isEnabled() && isWaiting && inkPointer < 0

    /**
     * True while a continuation stroke is drawn. A second finger drops the drawing instead
     * (PointerTracker); the first finger's moves then draw no more trail.
     */
    @JvmStatic fun isDrawing(): Boolean = inkPointer >= 0

    @JvmStatic fun onInkDown(pointerId: Int, x: Int, y: Int) {
        handler.removeCallbacksAndMessages(token)
        inkPointer = pointerId
        pointCount = 0
        addPoint(x, y)
    }

    @JvmStatic fun onInkMove(pointerId: Int, x: Int, y: Int) {
        if (pointerId == inkPointer) addPoint(x, y)
    }

    /**
     * The end of a continuation stroke. True when it was a quick tap that is no dot of the waiting
     * drawing: the symbol is written right now, and PointerTracker then types the tapped key like
     * any tap, so the letter follows the symbol (stage 31).
     */
    @JvmStatic fun onInkUp(pointerId: Int, x: Int, y: Int): Boolean {
        if (pointerId != inkPointer) return false
        addPoint(x, y)
        inkPointer = -1
        val stroke = InkStroke(points.copyOf(2 * pointCount))
        pointCount = 0
        if (isTap(stroke) && !dotCheck.isDot(group.drawn, centreX(stroke), centreY(stroke))) {
            finish()
            return true
        }
        add(stroke)
        return false
    }

    /** No point of [stroke] is more than [TAP_SLOP_DP] away from where it went down. */
    private fun isTap(stroke: InkStroke): Boolean {
        val slop = TAP_SLOP_DP * Resources.getSystem().displayMetrics.density
        for (i in 1 until stroke.size)
            if (Math.hypot((stroke.x(i) - stroke.x(0)).toDouble(), (stroke.y(i) - stroke.y(0)).toDouble()) > slop) return false
        return true
    }

    private fun centreX(stroke: InkStroke) = (0 until stroke.size).map { stroke.x(it) }.average().toFloat()
    private fun centreY(stroke: InkStroke) = (0 until stroke.size).map { stroke.y(it) }.average().toFloat()

    /** A touch that moves at most this far (dp) is a tap, not a stroke. */
    const val TAP_SLOP_DP = 10f

    // --- end of a symbol ---

    /** Another key or input comes first: recognize a waiting symbol now, before that input. */
    @JvmStatic fun flush() {
        if (isWaiting && inkPointer < 0) finish()
    }

    /** Drops everything (editor change, cancelled touch); nothing is recognized. */
    @JvmStatic fun cancel() {
        handler.removeCallbacksAndMessages(token)
        group.clear()
        inkPointer = -1
        pointCount = 0
    }

    private fun addPoint(x: Int, y: Int) {
        if (2 * pointCount + 2 > points.size) {
            if (points.size >= 2 * InkGroup.MAX_POINTS) return // the group refuses such a stroke anyway
            points = points.copyOf(points.size * 2)
        }
        points[2 * pointCount] = x.toFloat(); points[2 * pointCount + 1] = y.toFloat()
        pointCount++
    }

    private fun add(stroke: InkStroke) {
        // too many strokes or points is no symbol: drop the whole drawing
        if (!group.add(stroke, SystemClock.uptimeMillis())) return cancel()
        schedule()
    }

    private fun schedule() {
        handler.removeCallbacksAndMessages(token)
        if (group.isEmpty) return
        // postAtTime, not postDelayed: tests replace delayed messages of all handlers (P51)
        handler.postAtTime(due, token, SystemClock.uptimeMillis() + group.pauseMillis)
    }

    private fun finish() {
        handler.removeCallbacksAndMessages(token)
        if (group.isEmpty) return
        val strokes = group.take()
        sink?.onDrawing(strokes)
    }
}
