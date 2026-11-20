// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.CursorTrackpad.SpaceReason
import helium314.keyboard.agent.CursorTrackpad.Start
import kotlin.test.Test
import kotlin.test.assertEquals

/** Stage 87c: the pure decisions of the cursor trackpad (reasons, the start of a swipe, where a selection collapses). */
class CursorTrackpadReasonTest {
    private val cur = SpaceAction.MOVE_CURSOR
    private fun gestures(left: SpaceAction = cur, right: SpaceAction = cur, up: SpaceAction = SpaceAction.NONE, down: SpaceAction = SpaceAction.NONE) =
        SpaceGestureSettings(mapOf(SpaceGesture.SWIPE_LEFT to left, SpaceGesture.SWIPE_RIGHT to right, SpaceGesture.SWIPE_UP to up, SpaceGesture.SWIPE_DOWN to down))

    @Test fun reasons() {
        assertEquals(SpaceReason.QUALIFIES_ALL_AXES, CursorTrackpad.spaceReason(gestures()))
        assertEquals(SpaceReason.QUALIFIES_ALL_AXES, CursorTrackpad.spaceReason(gestures(up = cur, down = cur)))
        assertEquals(SpaceReason.QUALIFIES_START_DECIDES, CursorTrackpad.spaceReason(gestures(up = SpaceAction.UNDO, down = SpaceAction.REDO)))
        assertEquals(SpaceReason.QUALIFIES_START_DECIDES, CursorTrackpad.spaceReason(gestures(up = SpaceAction.UNDO)))
        // at least one direction is enough (stage 87c)
        assertEquals(SpaceReason.QUALIFIES_START_DECIDES, CursorTrackpad.spaceReason(gestures(right = SpaceAction.REDO)))
        assertEquals(SpaceReason.QUALIFIES_START_DECIDES, CursorTrackpad.spaceReason(gestures(left = SpaceAction.SWITCH_LANGUAGE, right = SpaceAction.SWITCH_LANGUAGE, up = cur, down = cur)))
        assertEquals(SpaceReason.QUALIFIES_START_DECIDES, CursorTrackpad.spaceReason(gestures(left = SpaceAction.SWITCH_LANGUAGE)))
        assertEquals(SpaceReason.QUALIFIES_ALL_AXES, CursorTrackpad.spaceReason(gestures(right = SpaceAction.NONE))) // one cursor direction, the rest free
        assertEquals(SpaceReason.NOT_NO_CURSOR_DIRECTION, CursorTrackpad.spaceReason(gestures(left = SpaceAction.NONE, right = SpaceAction.NONE)))
        assertEquals(SpaceReason.NOT_NO_CURSOR_DIRECTION, CursorTrackpad.spaceReason(gestures(left = SpaceAction.UNDO, right = SpaceAction.REDO, up = SpaceAction.UNDO)))
        assertEquals(SpaceReason.NOT_SETTING_OFF, CursorTrackpad.spaceReason(gestures(), enabled = false))
        // the name of stage 87 keeps working
        assertEquals(true, CursorTrackpad.appliesToSpace(gestures(up = SpaceAction.UNDO)))
        assertEquals(false, CursorTrackpad.appliesToSpace(gestures(left = SpaceAction.NONE, right = SpaceAction.NONE)))
    }

    @Test fun theStartIsDecidedAtTheFirstReportOf10DpWithTheComparisonOfTheOldSwipe() {
        val g = gestures(up = SpaceAction.UNDO, down = SpaceAction.REDO)
        assertEquals(Start.UNDECIDED, CursorTrackpad.decideStart(g, -9.0, 0.0))
        assertEquals(Start.UNDECIDED, CursorTrackpad.decideStart(g, 9.9, -9.9)) // below 10 dp on both axes: a tap or a hold so far
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, -10.0, 0.0)) // a plain sideways swipe: decided at the old claim distance
        // no waiting zone (review of stage 87c): where the old swipe has begun, the start is decided
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 9.0, -12.0)) // the old swipe: vertical, up = undo
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 9.0, 12.0)) // vertical, down = redo
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 12.0, 9.0)) // the vertical travel has not reached 10 dp: sideways
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 12.0, 11.0)) // both over 10, sideways is longer: sideways
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 11.0, 12.0)) // vertical is longer: vertical
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 12.0, 12.0)) // a tie is sideways, as in the old swipe
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 10.0, 8.0))
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 0.0, 12.0))
    }

    @Test fun theStartAgreesWithTheOldSwipeOnEveryPointOfAGrid() {
        // the old swipe (PointerTracker.onKeySwipe, density 1): vertical when |dy| >= 10 and |dx| < |dy|, else sideways when |dx| >= 10, else nothing yet
        val g = gestures(left = cur, right = SpaceAction.REDO, up = SpaceAction.UNDO, down = SpaceAction.NONE)
        for (dx in -30..30) for (dy in -30..30) {
            val old: SpaceGesture? = when {
                Math.abs(dy) >= 10 && Math.abs(dx) < Math.abs(dy) -> if (dy < 0) SpaceGesture.SWIPE_UP else SpaceGesture.SWIPE_DOWN
                Math.abs(dx) >= 10 -> if (dx < 0) SpaceGesture.SWIPE_LEFT else SpaceGesture.SWIPE_RIGHT
                else -> null
            }
            val expected = when (old) {
                null -> Start.UNDECIDED
                SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_DOWN -> Start.TRACKPAD // cursor; nothing (multi-line)
                else -> Start.LEGACY // redo, undo
            }
            assertEquals(expected, CursorTrackpad.decideStart(g, dx.toDouble(), dy.toDouble(), multiLine = true), "dx=$dx dy=$dy")
            if (old != null) assertEquals(old, CursorTrackpad.startDirection(dx.toDouble(), dy.toDouble()), "direction dx=$dx dy=$dy")
        }
    }

    @Test fun theStartAgreesWithTheOldSwipeInPixelsAtEveryDensity() {
        // review N-1: the old swipe begins at PointerTracker.sPointerStep = floor(10 * density) px (26 px at 2.625, 27 at 2.75, 30 at 3.0);
        // the raw px are compared with that value, so there is no px in which the old swipe has begun and the trackpad still waits
        val g = gestures(left = cur, right = SpaceAction.REDO, up = SpaceAction.UNDO, down = SpaceAction.NONE)
        for (density in listOf(1.0f, 2.625f, 2.75f, 3.0f)) {
            val step = Math.floor(10.0 * density).toInt()
            for (dx in -3 * step..3 * step) for (dy in -3 * step..3 * step) {
                val old: SpaceGesture? = when {
                    Math.abs(dy) >= step && Math.abs(dx) < Math.abs(dy) -> if (dy < 0) SpaceGesture.SWIPE_UP else SpaceGesture.SWIPE_DOWN
                    Math.abs(dx) >= step -> if (dx < 0) SpaceGesture.SWIPE_LEFT else SpaceGesture.SWIPE_RIGHT
                    else -> null
                }
                val expected = when (old) {
                    null -> Start.UNDECIDED
                    SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_DOWN -> Start.TRACKPAD
                    else -> Start.LEGACY
                }
                assertEquals(expected, CursorTrackpad.decideStart(g, dx.toDouble(), dy.toDouble(), true, step.toDouble()), "density $density dx=$dx dy=$dy")
                if (old != null) assertEquals(old, CursorTrackpad.startDirection(dx.toDouble(), dy.toDouble(), step.toDouble()), "direction density $density dx=$dx dy=$dy")
            }
            // the two flows of the review: a finger resting just inside the vertical range, a lift right after the first move
            assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, step - 1.0, -(step + 2.0), true, step.toDouble()), "resting at ($step-1, -($step+2))")
            assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, -step.toDouble(), step - 1.0, true, step.toDouble()), "left at the step: the cursor")
            assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, -step.toDouble(), step - 6.0, true, step.toDouble()), "12/9 scaled: sideways, the vertical travel is below the step")
            assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, -step.toDouble(), 10.0, true, step.toDouble()), "26/10 px: sideways, and 27/28 later is the same swipe")
            assertEquals(Start.UNDECIDED, CursorTrackpad.decideStart(g, step - 1.0, step - 1.0, true, step.toDouble()))
        }
    }

    @Test fun theStartDirectionDecidesByItsOwnBinding() {
        // left = cursor only, right = redo: left start is the trackpad, right start the action
        val g = gestures(right = SpaceAction.REDO)
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, -12.0, 0.0))
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 12.0, 0.0))
        // up and down are the cursor, left and right switch the language
        val h = gestures(left = SpaceAction.SWITCH_LANGUAGE, right = SpaceAction.SWITCH_LANGUAGE, up = cur, down = cur)
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(h, 0.0, -12.0))
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(h, -12.0, 0.0))
        // nothing bound: the trackpad too, except a vertical start in a single-line field (nothing should happen there)
        val k = gestures(up = SpaceAction.UNDO, down = SpaceAction.NONE)
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(k, 0.0, 12.0, multiLine = true))
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(k, 0.0, 12.0, multiLine = false))
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(gestures(up = cur, left = SpaceAction.UNDO), 0.0, -12.0, multiLine = false)) // cursor movement: the trackpad, flat there
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(gestures(right = SpaceAction.REDO, up = SpaceAction.NONE), -12.0, 0.0, multiLine = false)) // sideways: no difference
    }

    @Test fun aSelectionCollapsesTowardsTheStartingDirection() {
        assertEquals(true, CursorTrackpad.collapsesToStart(SpaceGesture.SWIPE_LEFT, rtl = false))
        assertEquals(false, CursorTrackpad.collapsesToStart(SpaceGesture.SWIPE_RIGHT, rtl = false))
        assertEquals(true, CursorTrackpad.collapsesToStart(SpaceGesture.SWIPE_UP, rtl = false))
        assertEquals(false, CursorTrackpad.collapsesToStart(SpaceGesture.SWIPE_DOWN, rtl = false))
        assertEquals(false, CursorTrackpad.collapsesToStart(SpaceGesture.SWIPE_LEFT, rtl = true)) // the finger's left is towards the end in right-to-left text
        assertEquals(SpaceGesture.SWIPE_UP, CursorTrackpad.startDirection(3.0, -12.0))
        assertEquals(SpaceGesture.SWIPE_LEFT, CursorTrackpad.startDirection(-12.0, 3.0))
    }
}
