// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

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
     * typed letter got lost, so 6 dp, docs/VALIDATION.md).
     */
    const val SLOP_DP = 6f

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

    /** The area around a drawing grows by this share of its larger side, but at least by a key width. */
    const val AREA_MARGIN = 0.6f

    /**
     * Stage 36 (review H1): whether a touch at ([x], [y]) is near enough to the waiting drawing
     * [strokes] (screen units) to be a further stroke of it: inside its bounding box grown on every
     * side by [AREA_MARGIN] of its larger side, at least by [minMargin] (a key width). Anything farther
     * away ends the drawing and is typed as usual, so typing right after a symbol loses nothing.
     */
    fun nearDrawing(strokes: List<InkStroke>, x: Float, y: Float, minMargin: Float): Boolean {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (s in strokes) for (i in 0 until s.size) {
            minX = minOf(minX, s.x(i)); maxX = maxOf(maxX, s.x(i)); minY = minOf(minY, s.y(i)); maxY = maxOf(maxY, s.y(i))
        }
        if (minX > maxX) return false
        val margin = maxOf(AREA_MARGIN * maxOf(maxX - minX, maxY - minY), minMargin)
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
