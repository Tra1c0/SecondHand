// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import dev.agentkeyboard.core.clip.ShelfDrag.Event
import dev.agentkeyboard.core.clip.ShelfDrag.Grab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 102 (`docs/SCREENSHOTS-KONZEPT.md`, E9): the touch logic of the shelf, with a fake clock and a fake layout. */
class ShelfDragTest {
    /** A row of targets 100 px wide: x < 100 card 1, < 200 card 2, < 300 folder 7, < 400 the new folder area, < 500 out, else none. */
    private val geometry = ShelfDrag.Geometry { x, _ ->
        when {
            x < 0 -> ShelfTarget.None
            x < 100 -> ShelfTarget.Card(1)
            x < 200 -> ShelfTarget.Card(2)
            x < 300 -> ShelfTarget.Folder(7)
            x < 400 -> ShelfTarget.NewFolder
            x < 500 -> ShelfTarget.Out
            else -> ShelfTarget.None
        }
    }
    private val drag = ShelfDrag(geometry, ShelfDrag.Config(slopPx = 10f, liftMs = 350, dwellMs = 360))
    private val shot1 = Grab.Shot(1, null)

    @Test fun aShortTouchIsATap() {
        assertTrue(drag.down(0, 50f, 50f, 0, shot1))
        assertNull(drag.move(0, 53f, 52f, 100))
        assertEquals(Event.Tap(shot1), drag.up(0, 53f, 52f, 200))
        assertEquals(ShelfDrag.Phase.IDLE, drag.phase)
    }

    @Test fun movingBeforeTheLiftScrollsTheList() {
        drag.down(0, 50f, 50f, 0, shot1)
        assertEquals(Event.Scroll, drag.move(0, 50f, 80f, 100))
        assertFalse(drag.capturing)
        assertNull(drag.up(0, 50f, 80f, 200))
    }

    @Test fun nothingOfTheShelfIsNotTracked() {
        assertFalse(drag.down(0, 50f, 50f, 0, null))
        assertNull(drag.up(0, 50f, 50f, 10))
    }

    @Test fun holdingLiftsAndLettingGoOnTheSpotOnlyOpensTheMenu() {
        drag.down(0, 50f, 50f, 0, shot1)
        assertNull(drag.tick(300))
        assertEquals(Event.Lift(shot1), drag.tick(350))
        assertNull(drag.tick(400)) // once
        assertTrue(drag.capturing)
        assertNull(drag.move(0, 55f, 50f, 500)) // within the slop
        assertEquals(Event.Menu(shot1), drag.up(0, 55f, 50f, 600))
    }

    @Test fun aLateTimerStillLiftsBeforeAMove() {
        drag.down(0, 50f, 50f, 0, shot1)
        assertEquals(Event.Lift(shot1), drag.move(0, 51f, 50f, 400))
        assertEquals(Event.Start(shot1), drag.move(0, 150f, 50f, 450))
    }

    @Test fun anUpAfterTheHoldTimeWithoutTimerIsTheMenuNotATap() {
        drag.down(0, 50f, 50f, 0, shot1)
        assertEquals(Event.Menu(shot1), drag.up(0, 50f, 50f, 500))
    }

    @Test fun draggingHoversAndDropsOnTheTargetUnderTheFinger() {
        drag.down(0, 50f, 50f, 0, shot1)
        drag.tick(350)
        assertEquals(Event.Start(shot1), drag.move(0, 70f, 50f, 400))
        assertEquals(ShelfTarget.Card(1), drag.target)
        assertEquals(Event.Hover(ShelfTarget.Folder(7)), drag.move(0, 250f, 50f, 450))
        assertNull(drag.move(0, 260f, 50f, 500)) // still the folder
        assertEquals(Event.Drop(shot1, ShelfTarget.Folder(7)), drag.up(0, 260f, 50f, 600))
        assertFalse(drag.capturing)
    }

    @Test fun backToTheStartDropsOnItsOwnCard() {
        // A11: the model makes that a no-op (ScreenshotShelfTest.cancelledOrPointlessDropsChangeNothing)
        drag.down(0, 50f, 50f, 0, shot1)
        drag.tick(350)
        drag.move(0, 350f, 50f, 400)
        drag.move(0, 50f, 50f, 500)
        assertEquals(Event.Drop(shot1, ShelfTarget.Card(1)), drag.up(0, 50f, 50f, 600))
    }

    @Test fun cancelAndASecondFingerEndWithoutADrop() {
        drag.down(0, 50f, 50f, 0, shot1)
        drag.tick(350)
        drag.move(0, 250f, 50f, 400)
        assertEquals(Event.Cancel(shot1), drag.cancel())
        assertNull(drag.up(0, 250f, 50f, 500)) // the rest of the gesture does nothing
        drag.down(0, 50f, 50f, 1000, shot1)
        drag.tick(1350)
        assertFalse(drag.down(1, 300f, 50f, 1400, Grab.Shot(2, null)))
        assertEquals(Event.Cancel(shot1), drag.secondFinger())
        assertNull(drag.up(0, 300f, 50f, 1500))
        assertNull(drag.cancel()) // nothing left to cancel
    }

    @Test fun aSecondFingerOnAPressForgetsIt() {
        drag.down(0, 50f, 50f, 0, shot1)
        assertNull(drag.secondFinger())
        assertNull(drag.up(0, 50f, 50f, 100))
    }

    @Test fun otherPointersAreIgnored() {
        drag.down(0, 50f, 50f, 0, shot1)
        drag.tick(350)
        assertNull(drag.move(3, 250f, 50f, 400))
        assertEquals(ShelfDrag.Phase.LIFTED, drag.phase)
        assertNull(drag.up(3, 250f, 50f, 450))
    }

    @Test fun holdingOverOutDwellsOnceAndAgainAfterLeaving() {
        drag.down(0, 50f, 50f, 0, shot1)
        drag.tick(350)
        drag.move(0, 450f, 50f, 400)
        assertEquals(ShelfTarget.Out, drag.target)
        assertNull(drag.tick(700))
        assertEquals(Event.Dwell(ShelfTarget.Out), drag.tick(760))
        assertNull(drag.tick(900))
        drag.move(0, 350f, 50f, 950)
        drag.move(0, 450f, 50f, 1000)
        assertEquals(Event.Dwell(ShelfTarget.Out), drag.move(0, 455f, 50f, 1400))
    }

    @Test fun aFolderIsNotDraggedMovingCancelsItsLift() {
        val folder = Grab.Folder(7)
        drag.down(0, 250f, 50f, 0, folder)
        drag.tick(350)
        assertEquals(Event.Cancel(folder), drag.move(0, 400f, 50f, 400))
        assertFalse(drag.capturing)
        drag.down(0, 250f, 50f, 1000, folder)
        drag.tick(1350)
        assertEquals(Event.Menu(folder), drag.up(0, 250f, 50f, 1400))
    }

    @Test fun dropOutsideIsNone() {
        drag.down(0, 50f, 50f, 0, shot1)
        drag.tick(350)
        drag.move(0, 600f, 50f, 400)
        assertEquals(Event.Drop(shot1, ShelfTarget.None), drag.up(0, 600f, 50f, 500))
    }
}
