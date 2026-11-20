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
    @JvmStatic fun claims(sidewaysOnly: Boolean, dx: Double, dy: Double, claim: Double = CLAIM_DP): Boolean =
        Math.abs(dx) >= claim || !sidewaysOnly && Math.abs(dy) >= claim && Math.abs(dx) < Math.abs(dy)

    /** Why the space bar is, or is not, a cursor trackpad now (stage 87c; the diagnose text of stage 87b can say it). */
    enum class SpaceReason {
        /** Every direction is "Cursor bewegen" or nothing (and at least one is the cursor): the trackpad takes every swipe, as in stage 87. */
        QUALIFIES_ALL_AXES,
        /** At least one direction is "Cursor bewegen", another has an action: the START direction of a swipe decides (the cursor's direction: free cursor, the action's direction: that action). */
        QUALIFIES_START_DECIDES,
        NOT_SETTING_OFF,
        /** No direction of the space bar is "Cursor bewegen": nothing would start the trackpad; bind one direction to it (Gesten & Gefühl, Leertaste). */
        NOT_NO_CURSOR_DIRECTION;

        val qualifies: Boolean get() = this == QUALIFIES_ALL_AXES || this == QUALIFIES_START_DECIDES
    }

    private val directions = listOf(SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_RIGHT, SpaceGesture.SWIPE_UP, SpaceGesture.SWIPE_DOWN)

    /**
     * Whether the space bar is a trackpad for [gestures] ([enabled]: the switch). It is when at least one direction is "Cursor bewegen"
     * (stage 87c; stage 87 needed left and right and free up and down). A selection does not matter here: the trackpad collapses it.
     */
    @JvmStatic fun spaceReason(gestures: SpaceGestureSettings, enabled: Boolean = true): SpaceReason = when {
        !enabled -> SpaceReason.NOT_SETTING_OFF
        directions.none { gestures[it] == SpaceAction.MOVE_CURSOR } -> SpaceReason.NOT_NO_CURSOR_DIRECTION
        directions.all { isFree(gestures, it) } -> SpaceReason.QUALIFIES_ALL_AXES
        else -> SpaceReason.QUALIFIES_START_DECIDES
    }

    /** A direction without an action of its own (nothing or cursor movement): the trackpad may take it. */
    @JvmStatic fun isFree(gestures: SpaceGestureSettings, gesture: SpaceGesture): Boolean =
        gestures[gesture] == SpaceAction.NONE || gestures[gesture] == SpaceAction.MOVE_CURSOR

    /** Whether the space bar is a trackpad with the switch on (the name from stage 87; true for both qualifying reasons). */
    @JvmStatic fun appliesToSpace(gestures: SpaceGestureSettings): Boolean = spaceReason(gestures).qualifies

    /** What the start of a swipe decides, for the reason QUALIFIES_START_DECIDES (UNDECIDED: less than [CLAIM_DP] on both axes, a tap or a hold so far). */
    enum class Start { UNDECIDED, TRACKPAD, LEGACY }

    /**
     * The direction a swipe started in, from its travel ([dx], [dy]) in dp, by the rule of the old swipe in PointerTracker.onKeySwipe: it is
     * vertical when the vertical travel has reached [CLAIM_DP] and is strictly longer than the sideways one (so a tie is sideways), else sideways;
     * the sign gives the direction ([claim]: the threshold in the unit of [dx] and [dy], dp by default, px with the tracker's step, PointerTracker.getPointerStep()). The old swipe decides at the first move that reaches [CLAIM_DP] on either axis, with exactly this comparison.
     */
    @JvmStatic fun startDirection(dx: Double, dy: Double, claim: Double = CLAIM_DP): SpaceGesture = when {
        Math.abs(dy) >= claim && Math.abs(dx) < Math.abs(dy) -> if (dy < 0) SpaceGesture.SWIPE_UP else SpaceGesture.SWIPE_DOWN
        else -> if (dx < 0) SpaceGesture.SWIPE_LEFT else SpaceGesture.SWIPE_RIGHT
    }

    /**
     * The start of a swipe for the reason [SpaceReason.QUALIFIES_START_DECIDES], from the travel ([dx], [dy]) in dp since touch-down.
     * Nothing is decided below [CLAIM_DP] on both axes. At the first report that reaches it the start is decided at once, with the comparison the
     * old swipe uses to tell a sideways from a vertical swipe ([startDirection]): there is no waiting zone, so a touch is never pending longer than
     * the old code would have kept it a tap. A direction bound to "Cursor bewegen" is the trackpad, a direction bound to an action runs that action
     * (legacy), a direction bound to nothing is the trackpad too (some direction is the cursor, nothing else would happen there), except an upward or
     * downward start in a field that is not [multiLine], where nothing should happen (legacy: the swipe is swallowed).
     */
    @JvmStatic fun decideStart(gestures: SpaceGestureSettings, dx: Double, dy: Double, multiLine: Boolean = true, claim: Double = CLAIM_DP): Start {
        if (Math.max(Math.abs(dx), Math.abs(dy)) < claim) return Start.UNDECIDED
        val direction = startDirection(dx, dy, claim)
        return when (gestures[direction]) {
            SpaceAction.MOVE_CURSOR -> Start.TRACKPAD
            SpaceAction.NONE -> if (!direction.isHorizontal && !multiLine) Start.LEGACY else Start.TRACKPAD
            else -> Start.LEGACY
        }
    }

    /**
     * Where a selection collapses when the trackpad starts in [direction]: towards the start for left and up, towards the end for right and
     * down, as arrow keys do in text editors. [rtl]: in a right-to-left language the finger's left is towards the end of the text.
     */
    @JvmStatic fun collapsesToStart(direction: SpaceGesture, rtl: Boolean): Boolean = when (direction) {
        SpaceGesture.SWIPE_LEFT -> !rtl
        SpaceGesture.SWIPE_RIGHT -> rtl
        SpaceGesture.SWIPE_UP -> true
        else -> false
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
