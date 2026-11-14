// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Stage 36 "Symbole malen: Erkennung überarbeiten": when is a touch in the pause a tap (a letter or
 * a dot) and when a stroke, and how long does a drawing wait for its next stroke.
 *
 * Owner feedback on build 14: short strokes of multi-stroke symbols were taken as letter taps, and
 * the pause of 500 ms was too short for a finger, so the rest of a symbol started a new one.
 */
object InkTap {
    /**
     * A touch that moves less than this (dp) from where it went down is no stroke (stage 36: 10 dp
     * before; 4 dp in the first version, review: a rolling finger moves more than 4 dp, P101, and a
     * typed letter got lost; 6 dp in the second, follow-up review F3: still below Android's 8 dp touch
     * slop, so 8 dp like a normal tap, docs/VALIDATION.md).
     */
    const val SLOP_DP = 8f

    /**
     * A second finger comes down while the first one draws: the first one counts as a tap (two thumbs
     * typing) up to this movement, as before stage 36 (review M1; a mistaken stroke is not lost either,
     * the drawing is written).
     */
    const val SECOND_FINGER_SLOP_DP = 10f

    /**
     * A quick touch is a tap below this duration. Only reported: a touch that does not move has no
     * shape whatever its duration, so it is a tap (a letter, or a dot where the drawing needs one)
     * also when held longer; making it a stroke would only add a stray dot to the drawing and lose
     * the letter (docs/VALIDATION.md, stage 36: false-stroke rate of both rules).
     */
    const val QUICK_MILLIS = 180L

    /** True when a touch that moved at most [movedDp] from its down point is a tap, not a stroke. */
    fun isTap(movedDp: Float): Boolean = movedDp < SLOP_DP

    /**
     * Stage 43 (owner feedback on build 19: drawing right after typing a word was often typed): within
     * this time after a typed letter, HeliBoard asked a swipe for up to six key widths before it starts
     * (its fast-typing cooldown, 500 ms). With drawn symbols a first stroke then starts at
     * [START_AFTER_TYPING_KEYS] instead; later at HeliBoard's usual 0.35 key widths.
     */
    const val AFTER_TYPING_MILLIS = 500L

    /**
     * Stage 43: right after typing, a first stroke starts a drawing only once it is this many key widths
     * from where it went down (about 27 dp on a phone): a tap that slides onto the next key while typing
     * fast (up to half a key) stays a tap; the line of ÷ or a small circle of % gets there. Only the
     * distance counts: a flicked stroke is as quick as a sliding tap.
     */
    const val START_AFTER_TYPING_KEYS = 0.75f

    /** True when a touch [sinceTypingMillis] after the last typed letter comes right after typing. */
    fun isAfterTyping(sinceTypingMillis: Long): Boolean = sinceTypingMillis in 0 until AFTER_TYPING_MILLIS

    /** Right after typing: whether a first stroke [movedPx] from its down point may start a drawing. */
    fun startsAfterTyping(movedPx: Float, keyWidthPx: Float): Boolean = movedPx >= START_AFTER_TYPING_KEYS * keyWidthPx

    /**
     * Stage 43 review M1: right after typing, a fast tap that slides 0.75 key widths starts a drawing
     * like a stroke does. When such a first stroke ends without a further one and is a short straight
     * line ([isSlidTap]: its farthest point at most this many key widths from where it went down), it
     * was that tap: its key is typed instead of a "–" or "/". A deliberate dash this short after a word
     * needs a further stroke (=) or more length (docs/VALIDATION.md, stage 43 follow-up review).
     */
    const val SLID_TAP_KEYS = 1.5f

    /**
     * Stage 43 review M1: straight for [isSlidTap]: no point is farther from the line from the down point
     * to the farthest point than this share of that distance (a small ~ or a hook is no sliding tap).
     */
    const val SLID_TAP_BEND = 0.25f

    /**
     * Stage 43 review M1: whether [stroke] (screen px), the only stroke of a drawing that started right
     * after typing, is a sliding tap: at most [SLID_TAP_KEYS] times [keyWidthPx] long and straight.
     */
    fun isSlidTap(stroke: InkStroke, keyWidthPx: Float): Boolean {
        if (stroke.size == 0) return true
        val x0 = stroke.x(0); val y0 = stroke.y(0)
        var far = 0; var length = 0f
        for (i in 1 until stroke.size) {
            val d = hypot(stroke.x(i) - x0, stroke.y(i) - y0)
            if (d > length) { length = d; far = i }
        }
        if (length > SLID_TAP_KEYS * keyWidthPx) return false
        if (length <= 0f) return true
        val dx = (stroke.x(far) - x0) / length; val dy = (stroke.y(far) - y0) / length
        for (i in 1 until stroke.size) {
            // distance across the line from the down point to the farthest point
            if (abs((stroke.x(i) - x0) * dy - (stroke.y(i) - y0) * dx) > SLID_TAP_BEND * length) return false
        }
        return true
    }

    /** The area around a drawing grows by this share of its larger side, but at least by [KEY_MARGIN] key widths. */
    const val AREA_MARGIN = 0.6f

    /**
     * Follow-up review F2: a small first part (the first circle of %, the bar of ≤, an arrow head drawn
     * first) must still reach the start of the next stroke. InkAreaTest, drawings 2–3 rows tall: the
     * built-in drawings need up to 2.6 key widths, 97 % of the finger model's random orders up to 3.
     * The price: within 3 keys of a drawing a typed key is a tap only below [SLOP_DP] (P111).
     */
    const val KEY_MARGIN = 3f

    /**
     * Stage 36 (review H1): whether a touch at ([x], [y]) is near enough to the waiting drawing
     * [strokes] (screen units) to be a further stroke of it: inside its bounding box grown on every
     * side by [AREA_MARGIN] of its larger side, at least by [keyMargin] times [keyWidth]. Anything
     * farther away ends the drawing and is typed as usual, so typing right after a symbol loses nothing.
     */
    fun nearDrawing(strokes: List<InkStroke>, x: Float, y: Float, keyWidth: Float, keyMargin: Float = KEY_MARGIN): Boolean {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (s in strokes) for (i in 0 until s.size) {
            minX = minOf(minX, s.x(i)); maxX = maxOf(maxX, s.x(i)); minY = minOf(minY, s.y(i)); maxY = maxOf(maxY, s.y(i))
        }
        if (minX > maxX) return false
        val margin = maxOf(AREA_MARGIN * maxOf(maxX - minX, maxY - minY), keyMargin * keyWidth)
        return x >= minX - margin && x <= maxX + margin && y >= minY - margin && y <= maxY + margin
    }
}

/**
 * The pause after a stroke during which the next stroke still belongs to the same symbol. It starts
 * at [DEFAULT_MILLIS] and follows the user's pace: [FACTOR] times the median of the last [KEPT]
 * gaps between two strokes of one symbol (lift to next touch), between [MIN_MILLIS] and
 * [MAX_MILLIS]. Kept in memory only, never stored.
 */
class InkPace {
    private val gaps = ArrayDeque<Long>()

    /** The pause to wait now, in milliseconds. */
    val pauseMillis: Long get() {
        if (gaps.size < MIN_GAPS) return DEFAULT_MILLIS
        val sorted = gaps.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        return (median * FACTOR).toLong().coerceIn(MIN_MILLIS, MAX_MILLIS)
    }

    /** A further stroke of the same symbol started [gapMillis] after the previous one ended. */
    fun onGap(gapMillis: Long) {
        if (gapMillis < 0) return
        gaps.addLast(gapMillis)
        while (gaps.size > KEPT) gaps.removeFirst()
    }

    fun clear() = gaps.clear()

    companion object {
        const val DEFAULT_MILLIS = 900L
        const val MIN_MILLIS = 600L
        const val MAX_MILLIS = 1500L
        const val FACTOR = 2.5
        const val KEPT = 9
        const val MIN_GAPS = 3
    }
}
