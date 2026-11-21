// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.suggestions.BarDrag.Event
import helium314.keyboard.latin.suggestions.BarDrag.Phase
import helium314.keyboard.latin.suggestions.BarDrag.Target
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarKey.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 88: the touch logic of customizing the bar (hold, slop, scroll, zone, gap, drop, cancel, full bar, second finger),
 * with a fake clock and a fake layout: 1000 px wide row, the bar zone is everything above y = 200, nothing here is a view.
 */
class BarDragTest {
    private var stored: List<ToolbarKey> = listOf(UNDO, REDO, COPY)
    private val writes = mutableListOf<List<ToolbarKey>>()
    private var hiddenVoice = false
    private val editor = BarEditor({ stored }, { stored = it; writes += it }, { it == VOICE && hiddenVoice })
    private var now = 1_000L
    private val slop = 20f

    /** [count] slots evenly spread over 1000 px: the centres are at (i + 0.5) * 1000 / count. */
    private val geometry = object : BarDrag.Geometry {
        override fun inBarZone(x: Float, y: Float) = y <= 200f
        override fun slotAt(x: Float, count: Int) = (0 until count).minByOrNull { Math.abs((it + 0.5f) * 1000f / count - x) } ?: 0
    }
    private fun drag(stored: List<ToolbarKey> = this.stored): BarDrag {
        this.stored = stored; editor.reload()
        return BarDrag(editor, geometry, BarDrag.Config(holdMs = 180, slopPx = slop))
    }
    private fun centre(i: Int, count: Int) = (i + 0.5f) * 1000f / count

    @Test fun aHoldOnAPillPicksTheToolUpAfterTheHoldTimeAndNotBefore() {
        val d = drag()
        assertTrue(d.down(0, 100f, 500f, now, Target.Pill(PASTE)))
        assertEquals(emptyList(), d.tick(now + 100))
        assertEquals(Phase.PRESSED, d.phase)
        val events = d.tick(now + 180)
        assertTrue(events.first() is Event.PickedUp && (events.first() as Event.PickedUp).key == PASTE && !(events.first() as Event.PickedUp).fromBar)
        assertEquals(Phase.DRAGGING, d.phase)
        assertEquals(PASTE, d.dragged)
        assertEquals(emptyList(), d.tick(now + 400)) // only once
    }

    @Test fun aSidewaysMoveOnAPillPicksUpAtOnceAVerticalOneScrollsAndNeverPicksUpLater() {
        var d = drag()
        d.down(0, 100f, 500f, now, Target.Pill(PASTE))
        assertEquals(emptyList(), d.move(0, 105f, 500f, now + 10)) // inside the slop
        assertTrue(d.move(0, 130f, 505f, now + 20).any { it is Event.PickedUp })
        // vertical: scroll
        d = drag()
        d.down(0, 100f, 500f, now, Target.Pill(PASTE))
        assertEquals(listOf(Event.Scroll), d.move(0, 102f, 440f, now + 20).map { it })
        assertEquals(Phase.IDLE, d.phase) // the scroll view owns the gesture and may never send its end: idle at once
        assertEquals(emptyList(), d.tick(now + 500)) // the hold time passes while scrolling: no pick-up
        assertEquals(emptyList(), d.move(0, 300f, 100f, now + 600)) // no matter where the finger goes
        assertEquals(emptyList(), d.up(0, 300f, 100f, now + 700))
        assertEquals(Phase.IDLE, d.phase)
        assertEquals(listOf(UNDO, REDO, COPY), stored)
        // and a new finger is taken at once
        assertTrue(d.down(0, 100f, 500f, now + 800, Target.Pill(PASTE)))
    }

    @Test fun aMoveOnABarSlotPicksUpInAnyDirection() {
        for ((dx, dy) in listOf(30f to 0f, 0f to 30f, -30f to -5f)) {
            val d = drag()
            d.down(0, 300f, 100f, now, Target.Slot(REDO))
            val events = d.move(0, 300f + dx, 100f + dy, now + 15)
            assertTrue(events.first().let { it is Event.PickedUp && it.key == REDO && it.fromBar }, "$dx $dy")
        }
    }

    @Test fun aShortTouchIsATapOnASlotAndOnAPill() {
        var d = drag()
        d.down(0, 300f, 100f, now, Target.Slot(REDO))
        var e = d.up(0, 301f, 100f, now + 90).single() as Event.Tapped
        assertEquals(REDO, e.key); assertFalse(e.onPill)
        d = drag()
        d.down(0, 300f, 500f, now, Target.Pill(PASTE))
        e = d.up(0, 300f, 500f, now + 90).single() as Event.Tapped
        assertTrue(e.onPill)
        assertEquals(Phase.IDLE, d.phase)
    }

    @Test fun theGapFollowsTheFingerToEveryPlaceIncludingBeforeTheFirstAndAfterTheLast() {
        for (target in 0..3) {
            val d = drag(listOf(UNDO, REDO, COPY))
            d.down(0, 500f, 500f, now, Target.Pill(PASTE))
            d.tick(now + 200)
            val events = d.move(0, centre(target, 4), 100f, now + 220)
            assertTrue(events.any { it is Event.GapMoved && it.slot == target }, "gap $target: $events")
            assertEquals(target, d.gap)
            assertEquals(listOf(UNDO, REDO, COPY).toMutableList().also { it.add(target, PASTE) }, editor.shown)
            assertEquals(listOf(UNDO, REDO, COPY), editor.bar) // not saved yet
            val dropped = d.up(0, centre(target, 4), 100f, now + 300).single { it is Event.Dropped } as Event.Dropped
            assertEquals(BarEditor.Result.CHANGED, dropped.result)
            assertEquals(listOf(UNDO, REDO, COPY).toMutableList().also { it.add(target, PASTE) }, stored, "drop at $target")
            assertEquals(1, writes.size.also { writes.clear() }) // saved once
        }
    }

    @Test fun movingAlongTheBarMovesTheGapOnlyWhenTheNearestPlaceChanges() {
        val d = drag(listOf(UNDO, REDO, COPY))
        d.down(0, 500f, 500f, now, Target.Pill(PASTE)); d.tick(now + 200)
        assertTrue(d.move(0, 100f, 100f, now + 210).any { it is Event.GapMoved })
        assertEquals(emptyList(), d.move(0, 110f, 100f, now + 220)) // same place: no event
        assertEquals(1, d.move(0, 400f, 100f, now + 230).count { it is Event.GapMoved })
    }

    @Test fun leavingTheZoneClosesTheGapAndAToolFromTheBarIsThenOutOfIt() {
        var d = drag()
        d.down(0, centre(1, 3), 100f, now, Target.Slot(REDO))
        d.move(0, centre(1, 3) + 40f, 100f, now + 10)
        assertEquals(listOf(UNDO, REDO, COPY), editor.shown)
        val out = d.move(0, centre(1, 3) + 40f, 600f, now + 20)
        assertTrue(out.any { it is Event.GapMoved && it.slot == null })
        assertNull(d.gap)
        assertEquals(listOf(UNDO, COPY), editor.shown) // the others have closed up
        assertEquals(listOf(UNDO, REDO, COPY), stored) // not yet saved
        val dropped = d.up(0, centre(1, 3), 600f, now + 30).single { it is Event.Dropped } as Event.Dropped
        assertFalse(dropped.toBar)
        assertEquals(listOf(UNDO, COPY), stored)
        // a tool from the grid dropped outside changes nothing
        d = drag(listOf(UNDO, REDO))
        d.down(0, 500f, 500f, now, Target.Pill(PASTE)); d.tick(now + 200)
        d.move(0, 600f, 520f, now + 210); d.move(0, 600f, 560f, now + 220)
        assertEquals(listOf(UNDO, REDO), editor.shown)
        d.up(0, 600f, 560f, now + 300)
        assertEquals(listOf(UNDO, REDO), stored)
    }

    @Test fun cancelGivesBackTheStateBeforeTheDragAndSavesNothing() {
        val d = drag()
        d.down(0, centre(0, 3), 100f, now, Target.Slot(UNDO))
        d.move(0, centre(0, 3) + 40f, 100f, now + 10)
        d.move(0, 800f, 100f, now + 20)
        assertEquals(listOf(REDO, COPY, UNDO), editor.shown)
        assertEquals(listOf(Event.Cancelled), d.cancel())
        assertEquals(listOf(UNDO, REDO, COPY), editor.shown)
        assertEquals(emptyList(), writes)
        assertEquals(Phase.IDLE, d.phase)
        // cancelling a touch that is not a drag does nothing
        d.down(0, 100f, 100f, now, Target.Pill(PASTE))
        assertEquals(emptyList(), d.cancel())
    }

    @Test fun aHoldThatIsLiftedOnTheSpotPutsTheToolBack() {
        val d = drag()
        d.down(0, 500f, 100f, now, Target.Pill(PASTE)); d.tick(now + 200)
        val events = d.up(0, 503f, 102f, now + 300)
        assertEquals(listOf(Event.Cancelled), events)
        assertEquals(emptyList(), writes)
        assertEquals(listOf(UNDO, REDO, COPY), editor.shown)
    }

    @Test fun aFullBarShowsNoGapSaysSoOnceAndTheDropDoesNothing() {
        val eight = BarTools.offered.filter { it !in BarTools.MIC_KEYS }.take(8)
        val d = drag(eight)
        val extra = BarTools.offered.first { it !in eight && it !in BarTools.MIC_KEYS }
        d.down(0, 500f, 500f, now, Target.Pill(extra)); d.tick(now + 200)
        val first = d.move(0, 300f, 100f, now + 210)
        assertEquals(1, first.count { it is Event.Full })
        assertNull(d.gap)
        assertEquals(eight, editor.shown)
        assertEquals(0, d.move(0, 320f, 100f, now + 220).count { it is Event.Full }) // not again while it stays full
        val dropped = d.up(0, 320f, 100f, now + 300).single { it is Event.Dropped } as Event.Dropped
        assertEquals(BarEditor.Result.FULL, dropped.result)
        assertEquals(eight, stored)
        assertEquals(emptyList(), writes)
        // a tool of a full bar can still be moved
        val d2 = drag(eight)
        d2.down(0, centre(0, 8), 100f, now, Target.Slot(eight[0]))
        d2.move(0, centre(0, 8) + 40f, 100f, now + 10)
        d2.move(0, centre(7, 8), 100f, now + 20)
        d2.up(0, centre(7, 8), 100f, now + 30)
        assertEquals(eight.drop(1) + eight[0], stored)
    }

    @Test fun anEmptyBarTakesATool() {
        val d = drag(emptyList())
        d.down(0, 500f, 500f, now, Target.Pill(UNDO)); d.tick(now + 200)
        d.move(0, 500f, 100f, now + 210)
        d.up(0, 500f, 100f, now + 300)
        assertEquals(listOf(UNDO), stored)
    }

    @Test fun aSecondFingerIsIgnored() {
        val d = drag()
        assertTrue(d.down(0, 500f, 500f, now, Target.Pill(PASTE)))
        assertFalse(d.down(1, 100f, 100f, now + 10, Target.Slot(UNDO))) // another finger while one is down: not tracked
        d.tick(now + 200)
        assertEquals(emptyList(), d.move(1, 100f, 100f, now + 210)) // moves of the other pointer do nothing
        assertEquals(emptyList(), d.up(1, 100f, 100f, now + 220))
        assertEquals(Phase.DRAGGING, d.phase)
        assertEquals(PASTE, d.dragged)
        assertTrue(d.move(0, centre(1, 4), 100f, now + 230).any { it is Event.GapMoved })
    }

    @Test fun nothingUnderTheFingerIsNotTracked() {
        val d = drag()
        assertFalse(d.down(0, 5f, 5f, now, null))
        assertEquals(Phase.IDLE, d.phase)
    }

    @Test fun aHiddenVoiceKeyKeepsItsListPlaceWhileTheSlotsCountOnlyVisibleTools() {
        hiddenVoice = true
        val d = drag(listOf(UNDO, VOICE, REDO, COPY))
        d.down(0, 500f, 500f, now, Target.Pill(PASTE)); d.tick(now + 200)
        d.move(0, centre(2, 4), 100f, now + 210) // visible slots after the drop: undo, redo, paste, copy
        assertEquals(listOf(UNDO, VOICE, REDO, PASTE, COPY), editor.shown)
        d.up(0, centre(2, 4), 100f, now + 300)
        assertEquals(listOf(UNDO, VOICE, REDO, PASTE, COPY), stored)
    }

    @Test fun everyTouchEndsInIdleAndTheNextOneStartsClean() {
        val d = drag()
        d.down(0, 500f, 500f, now, Target.Pill(PASTE)); d.tick(now + 200); d.move(0, 100f, 100f, now + 210)
        d.up(0, 100f, 100f, now + 300)
        assertEquals(Phase.IDLE, d.phase)
        assertNull(d.dragged); assertNull(d.gap)
        assertTrue(d.down(0, 500f, 500f, now + 400, Target.Pill(EMOJI)))
        assertEquals(Phase.PRESSED, d.phase)
        assertNotNull(d.holdDueAt().takeIf { it < Long.MAX_VALUE })
    }
}
