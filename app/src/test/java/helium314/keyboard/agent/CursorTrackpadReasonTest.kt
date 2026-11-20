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

    @Test fun theStartDecidesWithAMargin() {
        val g = gestures(up = SpaceAction.UNDO, down = SpaceAction.REDO)
        assertEquals(Start.UNDECIDED, CursorTrackpad.decideStart(g, -9.0, 0.0))
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, -10.0, 0.0)) // a plain sideways swipe: decided at the old claim distance
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 10.0, 6.0)) // 10 : 6 is 1.67 : 1
        assertEquals(Start.UNDECIDED, CursorTrackpad.decideStart(g, 10.0, 8.0)) // a diagonal waits ...
        assertEquals(Start.UNDECIDED, CursorTrackpad.decideStart(g, 15.0, 15.0))
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 16.0, 16.0)) // ... until 16 dp, a tie is sideways
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 16.0, 17.0)) // then the larger axis
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 5.0, -10.0)) // 2 : 1 vertically at 10 dp
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 0.0, 12.0))
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 17.0, 16.0))
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
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(gestures(up = cur, left = SpaceAction.UNDO), 0.0, 12.0, multiLine = false)) // cursor movement: the trackpad, flat there
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
