// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.charart.TextColumns
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 87, "Cursor als Trackpad": the pure core that turns a finger path into discrete cursor moves.
 * No Android: the app side feeds the finger position and, when it can, the text around the cursor,
 * and executes the returned [Step]s (setSelection by [Step.chars], DPAD_UP/DOWN for lines).
 *
 * Input: [begin], then [moveTo] with the finger's displacement in dp from the touch-down point and
 * the event time in ms; optionally [setText] (a window of the text around the cursor) when
 * [needsText] says so. Output: unit steps, one grapheme cluster horizontally or one line vertically.
 *
 * What it does, in the order of the data flow (all constants are first guesses, see the companion):
 *  1. Time grid. The path is resampled on a fixed [TICK_MS] grid by linear interpolation, so the
 *     result does not depend on how often the touch panel reports (60 Hz and 120 Hz give the same
 *     moves for the same path).
 *  2. Speed. The finger speed is the chord over the last [WINDOW_MS] (not the path length), so a
 *     trembling finger counts as slow.
 *  3. Curve. Travel per step is [HORIZONTAL_STEP_DP] / [VERTICAL_STEP_DP] while slow; above
 *     [GAIN_FROM_DPS] the gain rises along a smoothstep to [HORIZONTAL_GAIN] / [VERTICAL_GAIN] at
 *     [GAIN_TO_DPS]. Continuous, monotone, no jumps.
 *  4. Axis. One axis at a time (dominant-axis lock). Recent travel per axis decays over [AXIS_MEMORY_DP]
 *     of path; the axis switches only when the other one is [AXIS_SWITCH_RATIO] times larger. Travel
 *     the other axis made meanwhile is kept as a credit and handed to the new axis at the switch.
 *  5. Play. A reversal first swallows [PLAY_DP] of travel (the first move of an axis has none) (like backlash in a gear), so jitter cannot
 *     flip the cursor back and forth.
 *  6. Magnetism as hysteresis, never as snapping: a cluster step that LEAVES a word edge (word/space/
 *     punctuation change, reached by a step) costs 1 + [magnetism] * [MAX_DETENT_EXTRA] * speed factor
 *     steps of travel. The speed factor is 1 for a slow finger and falls to 0 between
 *     [DETENT_FULL_BELOW_DPS] and [DETENT_NONE_ABOVE_DPS]. Inside a word, and at the start position,
 *     the cost is 1.
 *  7. After a vertical step horizontal steps wait (their travel is carried) until [setText] or [setTextUnavailable]
 *     says where the cursor is; the editor needs time to move it.
 *  8. Carry. What is left of a step's travel stays in the axis' remainder; nothing is lost or counted
 *     twice. The remainder is dropped only at an axis switch (the credit replaces it) and where the
 *     text ends (no wind-up beyond the end).
 *
 * Horizontal means along the text's logical index: the app inverts the sign of x for right-to-left.
 */
class CursorTrack(
    /** Delete slider: only horizontal, no axis decision. */
    private val horizontalOnly: Boolean = false,
    magnetism: Float = DEFAULT_MAGNETISM,
) {
    enum class Axis { HORIZONTAL, VERTICAL }

    /**
     * One step. [direction] is -1 (left/up) or +1 (right/down). [chars] is the signed UTF-16 distance of
     * a horizontal step in the text window, or 0 when the text is unknown (the app then moves by one
     * grapheme cluster itself). [boundary]: the step landed on a word edge; [detent]: additionally the
     * finger was slow enough to feel it, so the app may give the stronger tick.
     */
    data class Step(val axis: Axis, val direction: Int, val chars: Int = 0, val boundary: Boolean = false, val detent: Boolean = false)

    /** 0..1; 0 switches the detents off. */
    var magnetism: Float = magnetism.coerceIn(0f, 1f)
        set(value) { field = value.coerceIn(0f, 1f) }

    /** Delete slider: horizontal moves forward (towards the end of the text) may not exceed this many chars; null = no limit. */
    var forwardLimitChars: Int? = null

    private class Sample(val t: Long, val x: Double, val y: Double)
    private class AxisState { var x = 0.0; var y = 0.0; var u = 0.0; var engaged = false
        fun reset() { x = 0.0; y = 0.0; u = 0.0; engaged = false } }

    private val samples = ArrayList<Sample>()
    private var tickT = 0L
    private var begun = false
    private val h = AxisState()
    private val v = AxisState()
    private var axis: Axis? = null
    private var ax = 0.0
    private var ay = 0.0
    private var creditH = 0.0
    private var creditV = 0.0
    private var sticky = false
    private var speed = 0.0 // dp/s at the last tick, for the detent flag
    private var gridX = 0.0 // finger position at the last processed tick
    private var gridY = 0.0

    // text window
    private var text: Window? = null
    private var unavailable = false
    private var needsTextFrom = 0L
    private var lastRequest = NEVER

    /** Starts a new gesture at time [tMs] (displacement 0,0). Keeps [magnetism], drops everything else. */
    fun begin(tMs: Long) {
        samples.clear(); samples += Sample(tMs, 0.0, 0.0)
        tickT = tMs; begun = true
        h.reset(); v.reset(); axis = null; ax = 0.0; ay = 0.0; creditH = 0.0; creditV = 0.0
        sticky = false; speed = 0.0; gridX = 0.0; gridY = 0.0
        text = null; unavailable = false; needsTextFrom = tMs; lastRequest = NEVER
        forwardLimitChars = null
    }

    /** The axis the track is locked to now (null before the first decision). */
    val currentAxis: Axis? get() = if (horizontalOnly) Axis.HORIZONTAL else axis

    /**
     * Whether the app should call [setText] (or [setTextUnavailable]) before the next [moveTo]: no text
     * yet, the text is stale after a vertical move, or the cursor came near the end of a truncated window.
     * Asks at most once per [REQUEST_GAP_MS].
     */
    fun needsText(tMs: Long): Boolean {
        if (unavailable || !begun) return false
        val w = text
        val wanted = when {
            w == null -> tMs >= needsTextFrom
            !w.atStart && w.pos < EDGE_MARGIN -> true
            !w.atEnd && w.text.length - w.pos < EDGE_MARGIN -> true
            else -> false
        }
        return wanted && tMs - lastRequest >= REQUEST_GAP_MS
    }

    /**
     * The text around the cursor: [before] ends at the cursor, [after] starts there. [atStart]/[atEnd]
     * say that the window reaches the real start/end of the field (else it is cut and its edge is not a word edge).
     */
    fun setText(before: CharSequence, after: CharSequence, atStart: Boolean, atEnd: Boolean, tMs: Long = 0L) {
        text = Window(before.toString() + after.toString(), before.length, atStart, atEnd)
        unavailable = false
        lastRequest = tMs
    }

    /**
     * The app sent an arrow key: the cursor is on its way somewhere the track cannot know. Like after a vertical step of its own, horizontal
     * steps wait (their travel is carried) until [setText] or [setTextUnavailable].
     */
    fun holdForEditor(tMs: Long) {
        text = null; unavailable = false; needsTextFrom = tMs + SETTLE_MS; sticky = false
    }

    /** The app cannot read the text: steps come without [Step.chars] and without detents, until the next vertical move. */
    fun setTextUnavailable(tMs: Long = 0L) {
        text = null; unavailable = true; lastRequest = tMs
    }

    /** True while a text window is set. */
    val hasText: Boolean get() = text != null

    /**
     * The finger is at ([xDp], [yDp]) from the touch-down point at time [tMs]. Returns the steps that
     * became due, in order. Positive x is towards the end of the text, positive y downwards.
     */
    fun moveTo(xDp: Double, yDp: Double, tMs: Long): List<Step> {
        check(begun) { "begin first" }
        val out = ArrayList<Step>(2)
        val last = samples.last()
        val t = max(tMs, last.t)
        if (t - last.t > MAX_GAP_MS) {
            // no events while the finger rests: it rested until just before this one
            samples += Sample(t - GAP_MOVE_MS, last.x, last.y)
            if (tickT < t - GAP_MOVE_MS) tickT += ((t - GAP_MOVE_MS - tickT) / TICK_MS) * TICK_MS
            gridX = last.x; gridY = last.y
        }
        samples += Sample(t, xDp, yDp)
        while (tickT + TICK_MS <= t) {
            tickT += TICK_MS
            val (px, py) = positionAt(tickT)
            val (wx, wy) = positionAt(tickT - WINDOW_MS)
            // horizontal-only: vertical finger travel is ignored altogether, also for the speed
            speed = (if (horizontalOnly) abs(px - wx) else hypot(px - wx, py - wy)) / WINDOW_MS * 1000.0
            val dx = px - gridX; val dy = py - gridY
            gridX = px; gridY = py
            process(dx, dy, tickT, out)
        }
        trim(t)
        return out
    }

    private fun trim(now: Long) {
        // keep one sample before the speed window and the grid position
        val keepFrom = min(now, tickT) - WINDOW_MS - TICK_MS
        var drop = 0
        while (drop + 1 < samples.size - 1 && samples[drop + 1].t <= keepFrom) drop++
        if (drop > 0) samples.subList(0, drop).clear()
    }

    /** Linear interpolation along the samples; before the first sample the finger is at the first, after the last at the last. */
    private fun positionAt(t: Long): Pair<Double, Double> {
        val first = samples[0]
        if (t <= first.t) return first.x to first.y
        var i = samples.size - 1
        if (t >= samples[i].t) return samples[i].x to samples[i].y
        while (i > 0 && samples[i - 1].t >= t) i--
        val a = samples[i - 1]; val b = samples[i]
        val f = if (b.t == a.t) 1.0 else (t - a.t).toDouble() / (b.t - a.t)
        return (a.x + (b.x - a.x) * f) to (a.y + (b.y - a.y) * f)
    }

    private fun process(dx: Double, dy: Double, t: Long, out: MutableList<Step>) {
        val len = hypot(dx, dy)
        if (len == 0.0) return
        // nothing decays before the first axis decision, so a short drag loses no travel
        val decay = if (!horizontalOnly && axis == null) 1.0 else exp(-len / AXIS_MEMORY_DP)
        ax = ax * decay + abs(dx)
        ay = ay * decay + abs(dy)
        creditH = creditH * decay + dx * gain(speed, HORIZONTAL_GAIN) / HORIZONTAL_STEP_DP
        creditV = creditV * decay + dy * gain(speed, VERTICAL_GAIN) / VERTICAL_STEP_DP

        var switched = false
        if (!horizontalOnly) {
            val current = axis
            val next = when {
                current == null -> if (ax + ay >= LOCK_MIN_DP) (if (ax >= ay) Axis.HORIZONTAL else Axis.VERTICAL) else null
                current == Axis.HORIZONTAL && ay >= AXIS_SWITCH_RATIO * ax -> Axis.VERTICAL
                current == Axis.VERTICAL && ax >= AXIS_SWITCH_RATIO * ay -> Axis.HORIZONTAL
                else -> current
            }
            if (next != current) {
                axis = next
                // the new axis starts with the travel it made on the side; the old one's remainder is dropped
                if (next == Axis.HORIZONTAL) { h.reset(); h.u = creditH; creditH = 0.0; v.reset() }
                else if (next == Axis.VERTICAL) { v.reset(); v.u = creditV; creditV = 0.0; h.reset() }
                // the credit may already hold whole steps (a switch after a long diagonal)
                if (next != null) { emit(next, t, out); switched = true }
            }
        }
        if (switched) return // this tick's travel is already in the credit
        when (currentAxis) {
            Axis.HORIZONTAL -> {
                creditH = 0.0
                advance(h, dx * gain(speed, HORIZONTAL_GAIN) / HORIZONTAL_STEP_DP, PLAY_DP / HORIZONTAL_STEP_DP / 2)
                emit(Axis.HORIZONTAL, t, out)
            }
            Axis.VERTICAL -> {
                creditV = 0.0
                advance(v, dy * gain(speed, VERTICAL_GAIN) / VERTICAL_STEP_DP, PLAY_DP / VERTICAL_STEP_DP / 2)
                emit(Axis.VERTICAL, t, out)
            }
            null -> Unit
        }
    }

    /** Backlash: the output follows the finger only once the finger left the play ([half] each side). */
    private fun advance(s: AxisState, w: Double, half: Double) {
        if (!s.engaged && w != 0.0) { s.engaged = true; s.y = s.x - half * (if (w > 0) 1 else -1) } // the first move takes no play
        s.x += w
        val before = s.y
        s.y = s.y.coerceIn(s.x - half, s.x + half)
        s.u += s.y - before
    }

    private fun emit(on: Axis, t: Long, out: MutableList<Step>) {
        if (on == Axis.VERTICAL) {
            while (v.u >= 1.0 || v.u <= -1.0) {
                val dir = if (v.u > 0) 1 else -1
                v.u -= dir
                out += Step(Axis.VERTICAL, dir)
                // the cursor jumped somewhere the track cannot know: horizontal steps wait until the app reads the text again
                text = null; unavailable = false; needsTextFrom = t + SETTLE_MS; sticky = false
            }
            return
        }
        while (true) {
            val cost = if (sticky) 1.0 + magnetism * MAX_DETENT_EXTRA * detentFactor(speed) else 1.0
            val dir = when { h.u >= cost -> 1; h.u <= -cost -> -1; else -> break }
            val w = text
            // after a vertical step the cursor is somewhere the track cannot know until the app has read the editor again:
            // horizontal travel is kept (carried) and applied then, not applied to a stale position
            if (w == null && !unavailable) break
            if (w == null) {
                h.u -= dir * cost
                out += Step(Axis.HORIZONTAL, dir)
                sticky = false
                continue
            }
            val target = if (dir > 0) w.nextBoundary() else w.previousBoundary()
            val chars = if (target < 0) 0 else target - w.pos
            val blocked = target < 0 || (dir > 0 && forwardLimitChars?.let { chars > it } == true)
            if (blocked) {
                // the end of the text (or of the allowed range): no wind-up, the finger can come straight back
                h.u = 0.0; h.y = h.x
                break
            }
            h.u -= dir * cost
            w.pos = target
            forwardLimitChars = forwardLimitChars?.minus(chars)
            val onEdge = w.isBoundary(target)
            sticky = onEdge
            out += Step(Axis.HORIZONTAL, dir, chars, boundary = onEdge, detent = onEdge && magnetism > 0f && detentFactor(speed) >= DETENT_FELT_FROM)
        }
    }

    /** A cut at the stop; true if the track has a window and its position is on a cluster boundary. */
    private class Window(val text: String, var pos: Int, val atStart: Boolean, val atEnd: Boolean) {
        val bounds: IntArray
        val classes: IntArray

        init {
            val clusters = TextColumns.graphemes(text)
            bounds = IntArray(clusters.size + 1)
            var at = 0
            for ((i, c) in clusters.withIndex()) { bounds[i] = at; at += c.length }
            bounds[clusters.size] = at
            classes = IntArray(clusters.size) { classOf(clusters[it]) }
            for (i in clusters.indices) {
                // an apostrophe between letters belongs to the word: no edge in "don't"
                val c = clusters[i]
                if ((c == "'" || c == "’") && i > 0 && i + 1 < clusters.size && classes[i - 1] == WORD && classes[i + 1] == WORD) classes[i] = WORD
            }
        }

        /** The next cluster start after pos, or -1. */
        fun nextBoundary(): Int {
            var i = java.util.Arrays.binarySearch(bounds, pos)
            i = if (i >= 0) i + 1 else -i - 1
            return if (i < bounds.size) bounds[i] else -1
        }

        /** The previous cluster start before pos, or -1. */
        fun previousBoundary(): Int {
            var i = java.util.Arrays.binarySearch(bounds, pos)
            i = if (i >= 0) i - 1 else -i - 2
            return if (i >= 0) bounds[i] else -1
        }

        fun isBoundary(p: Int): Boolean {
            val k = java.util.Arrays.binarySearch(bounds, p)
            if (k < 0) return false
            val n = classes.size
            val left = if (k > 0) classes[k - 1] else if (atStart) SPACE else return false
            val right = if (k < n) classes[k] else if (atEnd) SPACE else return false
            return left != right
        }

        private fun classOf(cluster: String): Int {
            val cp = cluster.codePointAt(0)
            return when {
                Character.isWhitespace(cp) || cp == 0xA0 -> SPACE
                Character.isLetterOrDigit(cp) || cp == '_'.code ||
                    Character.getType(cp) == Character.NON_SPACING_MARK.toInt() -> WORD
                else -> OTHER
            }
        }
    }

    companion object {
        // ---- design constants: first guesses, to be tuned by feel on the device (docs/VALIDATION.md, stage 87)
        /** Slow finger: one cluster per this many dp (the old space swipe: 10 dp). */
        const val HORIZONTAL_STEP_DP = 13.0
        /** Slow finger: one line per this many dp. A line is 2 steps of travel because a line jump is a big move. */
        const val VERTICAL_STEP_DP = 26.0
        /** Gain at and above [GAIN_TO_DPS]: 4x means one cluster per 3.25 dp. */
        const val HORIZONTAL_GAIN = 4.0
        const val VERTICAL_GAIN = 2.0
        /** Below this finger speed (dp/s) the gain is 1 (precision). */
        const val GAIN_FROM_DPS = 90.0
        /** From this speed the gain is at its maximum. */
        const val GAIN_TO_DPS = 1100.0
        /** Default magnetism: leaving a word edge costs 35 % of a step more (about 4.5 dp at a slow finger). */
        const val DEFAULT_MAGNETISM = 0.35f
        /** Extra cost of leaving a word edge at magnetism 1 (100 %): one more step of travel. */
        const val MAX_DETENT_EXTRA = 1.0
        /** Full detent at or below this speed (dp/s) ... */
        const val DETENT_FULL_BELOW_DPS = 120.0
        /** ... and none at or above this one. */
        const val DETENT_NONE_ABOVE_DPS = 600.0
        /** The app may give the stronger tick when the detent factor is at least this. */
        const val DETENT_FELT_FROM = 0.25
        /** Swallowed travel at a reversal (total play, dp). */
        const val PLAY_DP = 3.0
        /** Recent path length that counts for the axis decision (dp). */
        const val AXIS_MEMORY_DP = 20.0
        /** The other axis must be this many times larger to switch (hysteresis between about 27 and 58 degrees). */
        const val AXIS_SWITCH_RATIO = 1.6
        /** Path (dp, summed over both axes) before the first axis decision. */
        const val LOCK_MIN_DP = 8.0
        /** Resampling grid, speed window and gap handling (ms). */
        const val TICK_MS = 4L
        const val WINDOW_MS = 60L
        const val MAX_GAP_MS = 100L
        const val GAP_MOVE_MS = 16L
        /** After a vertical step the editor needs this long before its text can be read again (ms). */
        const val SETTLE_MS = 100L
        const val REQUEST_GAP_MS = 60L
        /** Refresh the window when the cursor is this close to a truncated edge (chars). */
        const val EDGE_MARGIN = 24

        private const val NEVER = Long.MIN_VALUE / 2
        private const val SPACE = 0
        private const val WORD = 1
        private const val OTHER = 2

        private fun smoothstep(x: Double): Double { val c = x.coerceIn(0.0, 1.0); return c * c * (3 - 2 * c) }

        /** Travel multiplier at finger speed [dps] (dp/s): 1 up to [GAIN_FROM_DPS], [maxGain] from [GAIN_TO_DPS], smooth between. */
        fun gain(dps: Double, maxGain: Double): Double = 1.0 + (maxGain - 1.0) * smoothstep((dps - GAIN_FROM_DPS) / (GAIN_TO_DPS - GAIN_FROM_DPS))

        /** 1 for a slow finger, 0 for a fast one: how much of the detent is felt. */
        fun detentFactor(dps: Double): Double = 1.0 - smoothstep((dps - DETENT_FULL_BELOW_DPS) / (DETENT_NONE_ABOVE_DPS - DETENT_FULL_BELOW_DPS))

        /** The slider value (0..100 %) as magnetism. */
        fun magnetismOfPercent(percent: Int): Float = (percent.coerceIn(0, 100)) / 100f
    }
}
