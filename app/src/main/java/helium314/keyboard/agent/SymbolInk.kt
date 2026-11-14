// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.agentkeyboard.core.InkCloud
import dev.agentkeyboard.core.InkDots
import dev.agentkeyboard.core.InkGroup
import dev.agentkeyboard.core.InkPace
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkSymbols
import dev.agentkeyboard.core.InkTap
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
 * - While a symbol waits for its next stroke ([pauseMillis] after the last one, stage 36: 900 ms,
 *   then following the user's pace, [InkPace]), a touch that starts on a key that types a character
 *   (no space, no modifier) near the drawing (review H1, [InkTap.nearDrawing]) is ink right away
 *   (PointerTracker asks [continuesInk] and then reports [onInkDown]/[onInkMove]/[onInkUp]), so short
 *   strokes and dots (÷, ≠) count too. A touch farther away ends the drawing and is typed as usual.
 * - A touch in that pause that moves less than [InkTap.SLOP_DP] is a tap (stage 36: 8 dp, was 10). Near
 *   a dot of a symbol with dots it is a possible dot ([dotCheck], [InkDots], stage 43): it joins the
 *   drawing as one point and waits; when the drawing is written, possible dots that do not make it a
 *   symbol with dots are typed as letters after it, in order ([tapper]). Any other tap writes the
 *   waiting symbol and PointerTracker then types the tapped key (stage 31).
 * - After the pause the strokes go to [sink] (the keyboard: [SymbolDrawing]); any other key
 *   press first finishes a waiting symbol ([flush]). Stage 36: the waiting drawing is also shown to
 *   the sink ([Sink.onWaiting]) [PREVIEW_DELAY_MILLIS] after its last stroke, for the preview chip.
 *
 * Only the first finger draws: points of other pointers in a batch are dropped. A second finger
 * during a continuation stroke ends the drawing ([onSecondFinger], PointerTracker): a stroke that is
 * still a tap is a letter of two-thumb typing (the symbol is written, the tap becomes a key press),
 * a real stroke ends the drawing with it (written, review M1). Strokes live only in memory until they are recognized; nothing
 * is stored or logged.
 */
object SymbolInk {
    interface Sink {
        /** A finished drawing, to be recognized and written. */
        fun onDrawing(strokes: List<InkStroke>)
        /**
         * Stage 36: the waiting drawing so far (shortly after a stroke); empty when it was dropped. Stage 43:
         * [possibleDots] are the indices of possible dots in it ([DotCheck.keeps] decides about them).
         */
        fun onWaiting(strokes: List<InkStroke>, possibleDots: List<Int>) {}
        /** Follow-up review F1: a further stroke begins; a preview shown so far is out of date. */
        fun onStrokeStarted() {}
    }

    private var sinkRef: WeakReference<Sink>? = null
    /** Receives every finished drawing; held weakly (the keyboard service owns it). */
    @JvmStatic var sink: Sink?
        get() = sinkRef?.get()
        set(value) { sinkRef = value?.let { WeakReference(it) } }

    private val group = InkGroup()
    private val pace = InkPace()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val token = Any()
    private val due = Runnable { finish() }
    /** Stage 36 review M2: the preview follows only after [PREVIEW_DELAY_MILLIS] without a new stroke. */
    private val previewToken = Any()
    private val preview = Runnable { if (!group.isEmpty && inkPointer < 0) sink?.onWaiting(group.drawn, possible.map { it.index }) }

    /** No preview while strokes follow each other quickly; it is matched off the main thread anyway. */
    const val PREVIEW_DELAY_MILLIS = 120L

    /**
     * Decides whether a quick tap may be a dot of the waiting drawing (stage 31) and, stage 43, whether
     * the possible dots [keeps] of the finished drawing stay dots (indices in it); the default keeps them.
     */
    fun interface DotCheck {
        fun isDot(strokes: List<InkStroke>, x: Float, y: Float): Boolean
        fun keeps(strokes: List<InkStroke>, possibleDots: List<Int>): Boolean = true
    }

    private val builtInDots by lazy { InkDots(InkSymbols.templates) }
    /** Dots of the built-in symbols only (÷, %, ‰, !, ¡, ?, ¿). */
    val builtInDotCheck = object : DotCheck {
        override fun isDot(strokes: List<InkStroke>, x: Float, y: Float) = builtInDots.isDot(strokes, x, y)
        override fun keeps(strokes: List<InkStroke>, possibleDots: List<Int>) = builtInDots.keeps(strokes, possibleDots)
    }
    /** [builtInDotCheck] by default; [SymbolDrawing.attach] adds the own symbols. */
    @JvmStatic var dotCheck: DotCheck = builtInDotCheck

    /** Stage 43: types a possible dot that was a letter after all, at its down point (PointerTracker). */
    fun interface Tapper { fun type(pointerId: Int, x: Int, y: Int) }
    @JvmStatic var tapper: Tapper? = null

    /** Stage 43: a possible dot: its index in the group, where it touched down, and its gap for the pace. */
    private class Possible(val index: Int, val pointerId: Int, val x: Int, val y: Int, val gap: Long)
    private val possible = ArrayList<Possible>()

    /** The continuation stroke being drawn: its pointer and points, or -1. */
    private var inkPointer = -1
    private var points = FloatArray(256)
    private var pointCount = 0
    /** When the last stroke of the waiting drawing ended (uptime), for the pace; -1 without one. */
    private var lastStrokeEnd = -1L
    /** The gap before the continuation stroke being drawn; it counts for the pace only if that is a stroke. */
    private var inkGap = -1L

    /** True when the setting is on; gesture typing is then off (SettingsValues). */
    @JvmStatic fun isEnabled(): Boolean = Settings.getValues()?.mSymbolDrawing == true

    /** A symbol has strokes and waits for more (or for the pause to end). */
    @JvmStatic val isWaiting: Boolean get() = !group.isEmpty

    /** How many strokes the waiting drawing has (follow-up review F1: is a preview still current?). */
    @JvmStatic val waitingStrokes: Int get() = group.size

    /** How long a drawing waits now for its next stroke (stage 36: follows the user's pace). */
    @JvmStatic val pauseMillis: Long get() = pace.pauseMillis

    /** The longest pause there can be; tests wait this long. */
    const val MAX_PAUSE_MILLIS = InkPace.MAX_MILLIS

    /**
     * Stage 36: keys on which a further stroke may start: every key that types a character, not
     * only letters (a stroke of ≤ or ⇒ often starts on the comma or period key; before, such a
     * stroke wrote the symbol so far and began a new one). Space, enter and the modifiers stay keys.
     */
    @JvmStatic fun startsInkOn(code: Int, isModifier: Boolean): Boolean =
        !isModifier && code > 0 && !Character.isWhitespace(code) && Character.isDefined(code)

    // --- first stroke: HeliBoard's gesture detection, via KeyboardActionListenerImpl ---

    /**
     * Stage 43, PointerTracker: the last letter typing time to give HeliBoard's gesture detection. With
     * drawn symbols, its fast-typing cooldown (up to six key widths of swipe right after typing) is off
     * ([startsAfterTyping] decides instead); without, nothing changes.
     */
    @JvmStatic fun gestureTypingTime(eventTime: Long, lastLetterTypingTime: Long): Long =
        if (isEnabled()) eventTime - Int.MAX_VALUE else lastLetterTypingTime

    /** Stage 43: a touch [sinceTypingMillis] after the last typed letter (InkTap.AFTER_TYPING_MILLIS). */
    @JvmStatic fun isAfterTyping(sinceTypingMillis: Long): Boolean = InkTap.isAfterTyping(sinceTypingMillis)

    /** Stage 43: right after typing, a first stroke [movedPx] from its down point starts ink (InkTap.START_AFTER_TYPING_KEYS). */
    @JvmStatic fun startsAfterTyping(movedPx: Int, keyWidthPx: Int): Boolean = InkTap.startsAfterTyping(movedPx.toFloat(), keyWidthPx.toFloat())

    @JvmStatic fun onBatchStart() {
        handler.removeCallbacksAndMessages(token)
        handler.removeCallbacksAndMessages(previewToken)
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

    /**
     * PointerTracker: a new touch on a character key at ([x], [y]) continues the waiting symbol as ink
     * when it starts near the drawing (stage 36 review H1, [InkTap.nearDrawing], at least [keyWidth]
     * around it); a touch farther away ends the drawing and is typed as usual.
     */
    @JvmStatic fun continuesInk(x: Int, y: Int, keyWidth: Int): Boolean =
        isEnabled() && isWaiting && inkPointer < 0 && InkTap.nearDrawing(group.drawn, x.toFloat(), y.toFloat(), keyWidth.toFloat())

    /**
     * True while a continuation stroke is drawn. A second finger then ends it ([onSecondFinger]);
     * the first finger's moves draw no more trail.
     */
    @JvmStatic fun isDrawing(): Boolean = inkPointer >= 0

    /** The pointer that draws the continuation stroke, or -1. */
    @JvmStatic val drawingPointer: Int get() = inkPointer

    /**
     * A second finger touches down while a continuation stroke is drawn (stage 31 fix, P104). When
     * that stroke is still a tap (two thumbs typing in the pause; up to [InkTap.SECOND_FINGER_SLOP_DP],
     * review M1), the waiting symbol is written now and true comes back: PointerTracker then presses
     * the tapped key like any tap, and the second finger is handled as usual. A real stroke in progress
     * ends the drawing with it and writes the symbol (false; review M1: before, it dropped the drawing).
     * The first finger's up then ends quietly.
     */
    @JvmStatic fun onSecondFinger(): Boolean {
        if (inkPointer < 0) return false
        val stroke = InkStroke(points.copyOf(2 * pointCount))
        inkPointer = -1
        pointCount = 0
        if (stroke.size > 0 && moved(stroke) < InkTap.SECOND_FINGER_SLOP_DP) {
            finish()
            return true
        }
        if (stroke.size > 0) group.add(stroke, SystemClock.uptimeMillis()) // refused beyond the limits: written without it
        finish()
        return false
    }

    @JvmStatic fun onInkDown(pointerId: Int, x: Int, y: Int) {
        handler.removeCallbacksAndMessages(token)
        handler.removeCallbacksAndMessages(previewToken)
        inkGap = if (lastStrokeEnd >= 0) SystemClock.uptimeMillis() - lastStrokeEnd else -1
        inkPointer = pointerId
        sink?.onStrokeStarted()
        pointCount = 0
        addPoint(x, y)
    }

    @JvmStatic fun onInkMove(pointerId: Int, x: Int, y: Int) {
        if (pointerId == inkPointer) addPoint(x, y)
    }

    /**
     * The end of a continuation stroke. True when it was a tap that is no possible dot of the waiting
     * drawing: the symbol is written right now (possible dots before it typed after it, stage 43), and
     * PointerTracker then types the tapped key like any tap, so the letter follows (stage 31).
     */
    @JvmStatic fun onInkUp(pointerId: Int, x: Int, y: Int): Boolean {
        if (pointerId != inkPointer) return false
        addPoint(x, y)
        inkPointer = -1
        val stroke = InkStroke(points.copyOf(2 * pointCount))
        pointCount = 0
        if (isTap(stroke)) {
            val cx = centreX(stroke); val cy = centreY(stroke)
            // stage 43: a possible dot joins as one point and waits; it stays a dot or becomes its letter
            // when the drawing is written (finish)
            if (group.size < InkCloud.MAX_STROKES && dotCheck.isDot(group.drawn, cx, cy)) {
                possible += Possible(group.size, pointerId, stroke.x(0).toInt(), stroke.y(0).toInt(), inkGap)
                add(InkStroke(floatArrayOf(cx, cy)))
                return false
            }
            finish()
            return true
        }
        // the pace follows the gaps between strokes of one symbol, not those before a typed letter
        if (inkGap >= 0) pace.onGap(inkGap)
        add(stroke)
        return false
    }

    /** No point of [stroke] is [InkTap.SLOP_DP] or more away from where it went down. */
    private fun isTap(stroke: InkStroke): Boolean = InkTap.isTap(moved(stroke))

    /** How far (dp) the farthest point of [stroke] is from where it went down. */
    private fun moved(stroke: InkStroke): Float {
        val density = Resources.getSystem().displayMetrics.density
        var moved = 0.0
        for (i in 1 until stroke.size)
            moved = maxOf(moved, Math.hypot((stroke.x(i) - stroke.x(0)).toDouble(), (stroke.y(i) - stroke.y(0)).toDouble()))
        return (moved / density).toFloat()
    }

    private fun centreX(stroke: InkStroke) = (0 until stroke.size).map { stroke.x(it) }.average().toFloat()
    private fun centreY(stroke: InkStroke) = (0 until stroke.size).map { stroke.y(it) }.average().toFloat()

    // --- end of a symbol ---

    /** Another key or input comes first: recognize a waiting symbol now, before that input. */
    @JvmStatic fun flush() {
        if (isWaiting && inkPointer < 0) finish()
    }

    /**
     * Stage 36: the preview chip in the strip was tapped. The waiting strokes are taken from the group
     * and go to [write] with the number of strokes drawn (possible dots that are letters already left
     * out, stage 43; they are typed after [write]). False while a stroke is still being drawn or
     * nothing waits.
     */
    @JvmStatic fun takeWaiting(write: (strokes: List<InkStroke>, drawn: Int) -> Unit): Boolean {
        if (!isWaiting || inkPointer >= 0) return false
        handler.removeCallbacksAndMessages(token)
        handler.removeCallbacksAndMessages(previewToken)
        lastStrokeEnd = -1
        resolve(write)
        return true
    }

    /** Drops everything (editor change, cancelled touch); nothing is recognized. */
    @JvmStatic fun cancel() {
        handler.removeCallbacksAndMessages(token)
        handler.removeCallbacksAndMessages(previewToken)
        val hadStrokes = !group.isEmpty
        group.clear()
        possible.clear()
        inkPointer = -1
        pointCount = 0
        lastStrokeEnd = -1
        if (hadStrokes) sink?.onWaiting(emptyList(), emptyList())
    }

    /** Tests: the pace starts afresh (it lives as long as the process otherwise). */
    internal fun resetPace() = pace.clear()

    private fun addPoint(x: Int, y: Int) {
        if (2 * pointCount + 2 > points.size) {
            if (points.size >= 2 * InkGroup.MAX_POINTS) return // the group refuses such a stroke anyway
            points = points.copyOf(points.size * 2)
        }
        points[2 * pointCount] = x.toFloat(); points[2 * pointCount + 1] = y.toFloat()
        pointCount++
    }

    private fun add(stroke: InkStroke) {
        val now = SystemClock.uptimeMillis()
        // too many strokes or points is no symbol: drop the whole drawing
        if (!group.add(stroke, now)) return cancel()
        lastStrokeEnd = now
        schedule()
        handler.removeCallbacksAndMessages(previewToken)
        handler.postAtTime(preview, previewToken, now + PREVIEW_DELAY_MILLIS)
    }

    private fun schedule() {
        handler.removeCallbacksAndMessages(token)
        if (group.isEmpty) return
        // postAtTime, not postDelayed: tests replace delayed messages of all handlers (P51)
        handler.postAtTime(due, token, SystemClock.uptimeMillis() + pace.pauseMillis)
    }

    private fun finish() {
        handler.removeCallbacksAndMessages(token)
        handler.removeCallbacksAndMessages(previewToken)
        lastStrokeEnd = -1
        if (group.isEmpty) return
        resolve { strokes, _ -> sink?.onDrawing(strokes) }
    }

    /**
     * Stage 43: takes the drawing and decides about its possible dots ([DotCheck.keeps]). Kept: the whole
     * drawing goes to [write], and their gaps count for the pace. Else the drawing without them goes to
     * [write], and then each is typed as the letter where it touched down, in the order they came.
     */
    private fun resolve(write: (strokes: List<InkStroke>, drawn: Int) -> Unit) {
        val strokes = group.take()
        val taps = possible.toList()
        possible.clear()
        if (taps.isEmpty()) return write(strokes, strokes.size)
        if (dotCheck.keeps(strokes, taps.map { it.index })) {
            for (tap in taps) if (tap.gap >= 0) pace.onGap(tap.gap)
            return write(strokes, strokes.size)
        }
        val letters = taps.map { it.index }.toSet()
        write(strokes.filterIndexed { i, _ -> i !in letters }, strokes.size)
        for (tap in taps) tapper?.type(tap.pointerId, tap.x, tap.y)
    }
}
