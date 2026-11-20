// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.text.InputType
import dev.agentkeyboard.core.CursorTrack

/**
 * Stage 87: the decisions around the cursor trackpad that need no touch or editor, so they are testable
 * on their own. The state machine itself lives in KeyboardActionListenerImpl.onCursorTrack.
 */
object CursorTrackpad {
    /** A drag becomes a swipe at this distance (dp) on the swiping axis, as the old swipe did at its first 10 dp step. */
    const val CLAIM_DP = 10.0
    /** Characters read before and after the cursor for the word edges. */
    const val WINDOW_CHARS = 200
    /** Samples kept while a finger has not become a swipe (a finger resting on the key). */
    const val MAX_PENDING = 64
    /** Lines asked for but not yet sent as arrow keys (one arrow goes out per answer of the editor), at most this many either way. */
    const val MAX_PENDING_LINES = 3
    /** After an arrow key the horizontal moves wait at most this long (ms) for the editor's new cursor position. */
    const val SELECTION_WAIT_MS = 300L

    /**
     * Whether a drag of ([dx], [dy]) dp from the touch-down point is a swipe and no tap any more: the old tolerance, 10 dp on
     * the swiping axis (the delete slider only counts sideways, a vertical drag counts when it is longer than the sideways one).
     */
    @JvmStatic fun claims(sidewaysOnly: Boolean, dx: Double, dy: Double): Boolean =
        Math.abs(dx) >= CLAIM_DP || !sidewaysOnly && Math.abs(dy) >= CLAIM_DP && Math.abs(dx) < Math.abs(dy)

    /**
     * The space bar becomes a trackpad only where its sideways swipes move the cursor and the vertical ones are free
     * (nothing or cursor movement). A swipe bound to another action (language, undo, touchpad mode, ...) keeps working as chosen.
     */
    @JvmStatic fun appliesToSpace(gestures: SpaceGestureSettings): Boolean = spaceBlocker(gestures) == null

    /**
     * Stage 87b: the first of the four swipes (left, right, up, down) that keeps the space bar from being a trackpad, with the action it
     * is bound to; null where the space bar qualifies. The one rule behind [appliesToSpace], the settings line and the diagnose card.
     */
    @JvmStatic fun spaceBlocker(gestures: SpaceGestureSettings): Pair<SpaceGesture, SpaceAction>? {
        for (gesture in listOf(SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_RIGHT)) {
            val action = gestures[gesture]
            if (action != SpaceAction.MOVE_CURSOR) return gesture to action
        }
        for (gesture in listOf(SpaceGesture.SWIPE_UP, SpaceGesture.SWIPE_DOWN)) {
            val action = gestures[gesture]
            if (action != SpaceAction.NONE && action != SpaceAction.MOVE_CURSOR) return gesture to action
        }
        return null
    }

    /** Stage 87b: the input type is of class text (a field kind, flags only). */
    @JvmStatic fun isTextClass(inputType: Int?): Boolean = inputType != null && inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT

    /** Stage 87b: the field says it is multi-line (the flag alone, as reported by the app). */
    @JvmStatic fun hasMultiLineFlag(inputType: Int?): Boolean = inputType != null && inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

    /** Vertical moves only where the field is known to be multi-line: class text with the multi-line flag. */
    @JvmStatic fun isMultiLine(inputType: Int?): Boolean = isTextClass(inputType) && hasMultiLineFlag(inputType)

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
