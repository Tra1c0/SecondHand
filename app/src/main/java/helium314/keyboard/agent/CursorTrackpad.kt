// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.CursorTrack

/**
 * Stage 87: the decisions around the cursor trackpad that need no touch or editor, so they are testable
 * on their own. The state machine itself lives in KeyboardActionListenerImpl.onCursorTrack.
 */
object CursorTrackpad {
    /** A drag becomes a swipe at this distance from the touch-down point (dp), as the old swipe did at its first 10 dp step. */
    const val CLAIM_DP = 10.0
    /** Characters read before and after the cursor for the word edges. */
    const val WINDOW_CHARS = 200

    /**
     * The space bar becomes a trackpad only where its sideways swipes move the cursor and the vertical ones are free
     * (nothing or cursor movement). A swipe bound to another action (language, undo, touchpad mode, ...) keeps working as chosen.
     */
    @JvmStatic fun appliesToSpace(gestures: SpaceGestureSettings): Boolean =
        gestures[SpaceGesture.SWIPE_LEFT] == SpaceAction.MOVE_CURSOR && gestures[SpaceGesture.SWIPE_RIGHT] == SpaceAction.MOVE_CURSOR &&
            listOf(SpaceGesture.SWIPE_UP, SpaceGesture.SWIPE_DOWN).all { gestures[it] == SpaceAction.NONE || gestures[it] == SpaceAction.MOVE_CURSOR }

    /** Consecutive steps on the same axis in the same direction, to be executed as one move with one tick. */
    class Run(val axis: CursorTrack.Axis, val direction: Int, val count: Int, val chars: Int, val detent: Boolean)

    @JvmStatic fun runs(steps: List<CursorTrack.Step>): List<Run> {
        val out = ArrayList<Run>()
        var i = 0
        while (i < steps.size) {
            val first = steps[i]
            var count = 0
            var chars = 0
            var detent = false
            while (i < steps.size && steps[i].axis == first.axis && steps[i].direction == first.direction && (steps[i].chars != 0) == (first.chars != 0)) { // a run never mixes steps with and without a position

                count++; chars += steps[i].chars; detent = detent || steps[i].detent; i++
            }
            out += Run(first.axis, first.direction, count, chars, detent)
        }
        return out
    }
}
