// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import kotlin.math.abs
import kotlin.math.max

/**
 * Detents along a sideways space bar swipe whose action runs once (stage 33, e.g. undo and redo).
 * The owner's sketch: from the touch-down point three detents, at 0.8 and 1.6 key widths and the
 * last a little further ([AT_KEY_WIDTHS]). Each detent the finger crosses plays one light tick; the
 * last one completes the gesture, plays a stronger and longer "stretch" and runs the action. Lifting
 * the finger before it does nothing. Moving back clearly behind a detent ([BACK_KEY_WIDTHS]) lets it
 * tick again on the way forward. After the last detent the swipe does nothing more until the finger
 * is lifted: one action per swipe.
 *
 * A plain state machine without Android, fed by KeyboardActionListenerImpl.onSpaceSwipeDistance
 * (PointerTracker reports the distance from the touch-down point on every move) and reset by
 * onEndSpaceSwipe. All distances of the gesture are here.
 */
class SpaceDetents {
    enum class Event { NONE, DETENT, COMPLETE }

    /** Detents currently behind the finger, the last one not counted. */
    var passed = 0
        private set

    /** The last detent was reached in this swipe; nothing more happens until [end]. */
    var completed = false
        private set

    /**
     * The finger is [distancePx] (current x minus touch-down x, signed) away in a swipe that started
     * to the left ([left]) or right; [unitPx] is one key width ([unitPx] of the companion).
     * Returns what to play or do now.
     */
    fun move(left: Boolean, distancePx: Int, unitPx: Float): Event {
        if (completed) return Event.NONE
        val along = if (left) -distancePx else distancePx
        val reached = AT_KEY_WIDTHS.count { along >= it * unitPx }
        if (reached == AT_KEY_WIDTHS.size) {
            completed = true
            passed = reached - 1
            return Event.COMPLETE
        }
        if (reached > passed) {
            passed = reached // a fast move over two detents ticks once
            return Event.DETENT
        }
        // back: a detent is left only clearly behind it, so a finger resting on it does not buzz
        while (passed > 0 && along < (AT_KEY_WIDTHS[passed - 1] - BACK_KEY_WIDTHS) * unitPx) passed--
        return Event.NONE
    }

    /** The finger was lifted (or the swipe cancelled): a swipe before the last detent did nothing. */
    fun end() {
        passed = 0
        completed = false
    }

    companion object {
        /** Detents in key widths from the touch-down point; the last one runs the action. */
        @JvmField val AT_KEY_WIDTHS = floatArrayOf(0.8f, 1.6f, 2.65f)

        /** How far behind a passed detent (key widths) the finger must go back to tick it again. */
        const val BACK_KEY_WIDTHS = 0.2f

        /** Key width used when the keyboard reports a smaller one (dp), so detents never crowd the start. */
        const val MIN_KEY_WIDTH_DP = 20f

        /**
         * A sideways move this long (dp) while holding space cancels the hold at once, before the
         * swipe starts (PointerTracker's first step is 10 dp): a slow swipe never runs the hold action.
         */
        const val HOLD_CANCEL_DP = 10 // review: 6 dp (below Android's touch slop) ended holds by a rolling finger

        /**
         * The key width the detents count in: the most common key width, but at most a seventh of the
         * keyboard. Letter rows have 9 to 11 keys and stay untouched; number, phone and numpad layouts
         * have keys of about a quarter of the width, where the last detent would otherwise need two
         * thirds of the screen (review of stage 33).
         */
        @JvmStatic fun keyWidth(mostCommonKeyWidthPx: Int, keyboardWidthPx: Int): Int =
            if (keyboardWidthPx <= 0) mostCommonKeyWidthPx else minOf(mostCommonKeyWidthPx, keyboardWidthPx / 7)

        /** One key width in px for [move]: the keyboard's most common key width, at least [MIN_KEY_WIDTH_DP]. */
        @JvmStatic fun unitPx(keyWidthPx: Int, density: Float): Float = max(keyWidthPx.toFloat(), MIN_KEY_WIDTH_DP * density)

        /** Whether a move by [dx], [dy] from the touch-down point is clearly sideways and long enough to end a hold. */
        @JvmStatic fun cancelsHold(dx: Int, dy: Int, thresholdPx: Int): Boolean = abs(dx) >= thresholdPx && abs(dx) > abs(dy)
    }
}
