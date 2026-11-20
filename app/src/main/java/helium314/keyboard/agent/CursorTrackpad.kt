// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

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

    /** Stage 87c: a swipe whose start is this much longer on one axis than on the other starts on that axis. */
    const val DOMINANCE = 1.5
    /** Stage 87c: a start between the two (a diagonal) waits up to this travel (dp, the larger axis), then the larger axis decides; a tie is horizontal. */
    const val DECIDE_CAP_DP = 16.0

    /** Why the space bar is, or is not, a cursor trackpad now (stage 87c; the diagnose text of stage 87b can say it). */
    enum class SpaceReason {
        /** Left and right move the cursor and up and down are free (nothing or cursor movement): all four directions from the first move. */
        QUALIFIES_ALL_AXES,
        /** Left and right move the cursor, up and/or down run another action: a swipe that starts sideways is the trackpad, one that starts vertically runs the action. */
        QUALIFIES_HORIZONTAL_START,
        NOT_SETTING_OFF,
        NOT_LEFT_RIGHT_NOT_CURSOR,
        /** A selection is present (for instance after "select all" on hold): the old swipe runs, the trackpad would end it. */
        NOT_SELECTION_PRESENT;

        val qualifies: Boolean get() = this == QUALIFIES_ALL_AXES || this == QUALIFIES_HORIZONTAL_START
    }

    /**
     * Whether the space bar is a trackpad for [gestures] ([enabled]: the switch; [hasSelection]). The order of the reasons is the order
     * of the checks. A vertical swipe bound to an action no longer switches the trackpad off (stage 87c): it only has to start vertically.
     */
    @JvmStatic fun spaceReason(gestures: SpaceGestureSettings, enabled: Boolean = true, hasSelection: Boolean = false): SpaceReason = when {
        !enabled -> SpaceReason.NOT_SETTING_OFF
        gestures[SpaceGesture.SWIPE_LEFT] != SpaceAction.MOVE_CURSOR || gestures[SpaceGesture.SWIPE_RIGHT] != SpaceAction.MOVE_CURSOR -> SpaceReason.NOT_LEFT_RIGHT_NOT_CURSOR
        hasSelection -> SpaceReason.NOT_SELECTION_PRESENT
        verticalIsFree(gestures, SpaceGesture.SWIPE_UP) && verticalIsFree(gestures, SpaceGesture.SWIPE_DOWN) -> SpaceReason.QUALIFIES_ALL_AXES
        else -> SpaceReason.QUALIFIES_HORIZONTAL_START
    }

    /** An up or down swipe without an action of its own (nothing or cursor movement): the trackpad may take it. */
    @JvmStatic fun verticalIsFree(gestures: SpaceGestureSettings, gesture: SpaceGesture): Boolean =
        gestures[gesture] == SpaceAction.NONE || gestures[gesture] == SpaceAction.MOVE_CURSOR

    /** Whether ignoring the selection and the switch the space bar is a trackpad (the name from stage 87; true for both qualifying reasons). */
    @JvmStatic fun appliesToSpace(gestures: SpaceGestureSettings): Boolean = spaceReason(gestures).qualifies

    /** What the start of a swipe decides, for the reason QUALIFIES_HORIZONTAL_START. */
    enum class Start { UNDECIDED, TRACKPAD, LEGACY }

    /**
     * The start of a swipe on a space bar with a vertical action ([SpaceReason.QUALIFIES_HORIZONTAL_START]), from the travel ([dx], [dy])
     * in dp since touch-down. Nothing is decided below [CLAIM_DP] on the larger axis. A start at least [DOMINANCE] times longer sideways than
     * vertically is the trackpad (all four directions from then on); at least [DOMINANCE] times longer vertically it runs the action of
     * that direction (up or down by the sign of [dy]) when it has one, and is the trackpad when that direction is free (nothing or
     * cursor movement). In between (a diagonal) the touch waits until the larger axis has [DECIDE_CAP_DP], then the larger axis decides, a tie
     * is sideways. A plain sideways swipe is decided at 10 dp, as the claim of the old swipe.
     */
    @JvmStatic fun decideStart(gestures: SpaceGestureSettings, dx: Double, dy: Double): Start {
        val ax = Math.abs(dx)
        val ay = Math.abs(dy)
        val larger = Math.max(ax, ay)
        if (larger < CLAIM_DP) return Start.UNDECIDED
        val vertical = if (dy < 0) SpaceGesture.SWIPE_UP else SpaceGesture.SWIPE_DOWN
        val verticalStart = if (verticalIsFree(gestures, vertical)) Start.TRACKPAD else Start.LEGACY
        return when {
            ax >= DOMINANCE * ay -> Start.TRACKPAD
            ay >= DOMINANCE * ax -> verticalStart
            larger < DECIDE_CAP_DP -> Start.UNDECIDED
            ax >= ay -> Start.TRACKPAD
            else -> verticalStart
        }
    }

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
