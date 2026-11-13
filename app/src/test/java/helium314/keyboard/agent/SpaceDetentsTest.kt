// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.SpaceDetents.Event
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The detent state machine of a sideways space swipe with a one-shot action (stage 33). */
class SpaceDetentsTest {
    /** One key width in px: detents at 40, 80 and 132.5 px from the touch-down point. */
    private val unit = 50f
    private val detents = SpaceDetents()

    private fun right(vararg px: Int) = px.map { detents.move(false, it, unit) }
    private fun left(vararg px: Int) = px.map { detents.move(true, it, unit) }

    @Test fun theOwnersSketchThreeDetentsTheLastGapLonger() {
        assertEquals(listOf(0.8f, 1.6f, 2.65f), SpaceDetents.AT_KEY_WIDTHS.toList())
        val gaps = SpaceDetents.AT_KEY_WIDTHS.toList().zipWithNext { a, b -> b - a }
        assertEquals(0.8f, gaps[0], 0.001f)
        assertTrue(gaps.last() > 1f && gaps.last() < 1.1f) // "gut eine Tastenbreite"
    }

    @Test fun forwardTicksAtEachDetentAndCompletesAtTheLast() {
        assertEquals(listOf(Event.NONE, Event.NONE, Event.DETENT, Event.NONE), right(10, 39, 40, 60))
        assertEquals(1, detents.passed)
        assertEquals(listOf(Event.DETENT, Event.NONE, Event.COMPLETE), right(80, 132, 133))
        assertTrue(detents.completed)
    }

    @Test fun completesOnceUntilTheFingerIsLifted() {
        assertEquals(Event.COMPLETE, right(40, 80, 140).last())
        // on, back to the start and forward again: done for this swipe
        assertEquals(List(4) { Event.NONE }, right(300, 0, 45, 140))
        detents.end()
        assertFalse(detents.completed)
        assertEquals(listOf(Event.DETENT, Event.DETENT, Event.COMPLETE), right(40, 80, 140))
    }

    @Test fun liftingEarlyDoesNothingAndTheNextSwipeStartsAfresh() {
        assertEquals(listOf(Event.DETENT, Event.DETENT, Event.NONE), right(41, 81, 130))
        detents.end()
        assertEquals(0, detents.passed)
        // the next swipe feels its first detent again
        assertEquals(listOf(Event.NONE, Event.DETENT), right(20, 41))
    }

    @Test fun movingBackClearlyBehindADetentLetsItTickAgain() {
        assertEquals(listOf(Event.DETENT, Event.DETENT), right(45, 85))
        // just behind the second detent (less than 0.2 key widths): resting there does not buzz
        assertEquals(listOf(Event.NONE, Event.NONE, Event.NONE), right(75, 71, 82))
        assertEquals(2, detents.passed)
        // clearly back behind it: it ticks again on the way forward
        assertEquals(listOf(Event.NONE, Event.DETENT), right(69, 81))
        // back to the start: both detents tick again, then the last one completes
        assertEquals(listOf(Event.NONE, Event.DETENT, Event.DETENT, Event.COMPLETE), right(0, 41, 81, 133))
    }

    @Test fun aFastMoveOverSeveralDetentsTicksOnceOrCompletesAtOnce() {
        assertEquals(listOf(Event.DETENT, Event.COMPLETE), right(90, 140))
        detents.end()
        assertEquals(listOf(Event.COMPLETE), right(200))
    }

    @Test fun theDirectionIsTheStartDirection() {
        // a left swipe counts to the left; the other side of the start is no progress
        assertEquals(listOf(Event.NONE, Event.NONE), left(40, 200))
        assertEquals(listOf(Event.DETENT, Event.DETENT, Event.COMPLETE), left(-40, -80, -133))
        detents.end()
        assertEquals(listOf(Event.NONE, Event.NONE), right(-40, -200))
        assertFalse(detents.completed)
    }

    @Test fun theUnitIsTheKeyWidthWithAFloor() {
        assertEquals(50f, SpaceDetents.unitPx(50, 1f))
        assertEquals(105f, SpaceDetents.unitPx(105, 2.625f))
        // a tiny or missing key width: at least 20 dp, so the first detent stays beyond the 10 dp swipe step
        assertEquals(40f, SpaceDetents.unitPx(10, 2f))
        assertEquals(60f, SpaceDetents.unitPx(0, 3f))
        assertTrue(SpaceDetents.AT_KEY_WIDTHS[0] * SpaceDetents.MIN_KEY_WIDTH_DP > 10f)
    }

    @Test fun onlyAClearSidewaysMoveCancelsTheHold() {
        assertTrue(SpaceDetents.cancelsHold(6, 0, 6))
        assertTrue(SpaceDetents.cancelsHold(-6, 5, 6))
        assertFalse(SpaceDetents.cancelsHold(5, 0, 6)) // too short
        assertFalse(SpaceDetents.cancelsHold(7, 7, 6)) // as much up or down: not clearly sideways
        assertFalse(SpaceDetents.cancelsHold(0, 30, 6)) // vertical swipes keep their own rule
        // shorter than PointerTracker's 10 dp swipe step
        assertTrue(SpaceDetents.HOLD_CANCEL_DP < 10)
    }
}
