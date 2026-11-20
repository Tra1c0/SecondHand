// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.CursorTrackpad.SpaceReason
import helium314.keyboard.agent.CursorTrackpad.Start
import kotlin.test.Test
import kotlin.test.assertEquals

/** Stage 87c: the pure decisions of the cursor trackpad (reasons and the start of a swipe). */
class CursorTrackpadReasonTest {
    private fun gestures(left: SpaceAction = SpaceAction.MOVE_CURSOR, right: SpaceAction = SpaceAction.MOVE_CURSOR,
                         up: SpaceAction = SpaceAction.NONE, down: SpaceAction = SpaceAction.NONE) =
        SpaceGestureSettings(mapOf(SpaceGesture.SWIPE_LEFT to left, SpaceGesture.SWIPE_RIGHT to right, SpaceGesture.SWIPE_UP to up, SpaceGesture.SWIPE_DOWN to down))

    @Test fun reasons() {
        assertEquals(SpaceReason.QUALIFIES_ALL_AXES, CursorTrackpad.spaceReason(gestures()))
        assertEquals(SpaceReason.QUALIFIES_ALL_AXES, CursorTrackpad.spaceReason(gestures(up = SpaceAction.MOVE_CURSOR, down = SpaceAction.MOVE_CURSOR)))
        assertEquals(SpaceReason.QUALIFIES_HORIZONTAL_START, CursorTrackpad.spaceReason(gestures(up = SpaceAction.UNDO, down = SpaceAction.REDO)))
        assertEquals(SpaceReason.QUALIFIES_HORIZONTAL_START, CursorTrackpad.spaceReason(gestures(up = SpaceAction.UNDO)))
        assertEquals(SpaceReason.NOT_LEFT_RIGHT_NOT_CURSOR, CursorTrackpad.spaceReason(gestures(left = SpaceAction.SWITCH_LANGUAGE)))
        assertEquals(SpaceReason.NOT_LEFT_RIGHT_NOT_CURSOR, CursorTrackpad.spaceReason(gestures(right = SpaceAction.NONE, up = SpaceAction.UNDO)))
        assertEquals(SpaceReason.NOT_SETTING_OFF, CursorTrackpad.spaceReason(gestures(), enabled = false))
        assertEquals(SpaceReason.NOT_SELECTION_PRESENT, CursorTrackpad.spaceReason(gestures(up = SpaceAction.UNDO), hasSelection = true))
        // the name of stage 87 keeps working
        assertEquals(true, CursorTrackpad.appliesToSpace(gestures(up = SpaceAction.UNDO)))
        assertEquals(false, CursorTrackpad.appliesToSpace(gestures(left = SpaceAction.UNDO)))
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

    @Test fun aFreeVerticalDirectionStartsTheTrackpadToo() {
        val g = gestures(up = SpaceAction.UNDO, down = SpaceAction.NONE)
        assertEquals(Start.LEGACY, CursorTrackpad.decideStart(g, 0.0, -12.0))
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(g, 0.0, 12.0))
        val h = gestures(up = SpaceAction.UNDO, down = SpaceAction.MOVE_CURSOR)
        assertEquals(Start.TRACKPAD, CursorTrackpad.decideStart(h, 2.0, 12.0))
    }
}
