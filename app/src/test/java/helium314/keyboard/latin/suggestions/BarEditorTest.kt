// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.suggestions.BarEditor.Result
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarKey.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 86: the model of the edit mode: insert, remove, move, the limit, and what is saved when. */
class BarEditorTest {
    private var stored: List<ToolbarKey> = listOf(UNDO, REDO, COPY)
    private val writes = mutableListOf<List<ToolbarKey>>()
    private fun editor() = BarEditor({ stored }, { stored = it; writes += it })

    @Test fun aToolDraggedFromTheGridIsInsertedAtTheNearestSlotAndTheOthersMoveOverBeforeTheDrop() {
        val e = editor()
        e.dragStart(PASTE, fromBar = false)
        assertTrue(e.dragOverBar(1))
        assertEquals(listOf(UNDO, PASTE, REDO, COPY), e.preview) // the others shifted, visibly
        assertEquals(listOf(UNDO, PASTE, REDO, COPY), e.shown)
        assertEquals(listOf(UNDO, REDO, COPY), e.bar) // nothing saved yet
        assertEquals(emptyList(), writes)
        e.dragOverBar(0)
        assertEquals(listOf(PASTE, UNDO, REDO, COPY), e.shown)
        assertEquals(Result.CHANGED, e.dropOnBar(3))
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), stored)
        assertEquals(1, writes.size) // saved on the drop, once
        assertNull(e.preview); assertNull(e.dragged)
    }

    @Test fun aToolDraggedWithinTheBarIsMoved() {
        val e = editor()
        e.dragStart(UNDO, fromBar = true)
        e.dragOverBar(2)
        assertEquals(listOf(REDO, COPY, UNDO), e.shown)
        assertEquals(Result.CHANGED, e.dropOnBar(2))
        assertEquals(listOf(REDO, COPY, UNDO), stored)
        // dropping it where it was changes nothing and writes nothing
        writes.clear()
        e.dragStart(REDO, fromBar = true)
        assertEquals(Result.UNCHANGED, e.dropOnBar(0))
        assertEquals(emptyList(), writes)
    }

    @Test fun aToolDraggedOutOfTheBarOntoTheGridIsRemoved() {
        val e = editor()
        e.dragStart(REDO, fromBar = true)
        e.dragLeftBar()
        assertEquals(listOf(UNDO, COPY), e.shown) // gone from the bar while the finger is outside
        assertEquals(Result.CHANGED, e.dropOnGrid())
        assertEquals(listOf(UNDO, COPY), stored)
        // a tool from the grid dropped on the grid does nothing
        e.dragStart(PASTE, fromBar = false)
        assertEquals(Result.UNCHANGED, e.dropOnGrid())
        assertEquals(listOf(UNDO, COPY), stored)
    }

    @Test fun aDragThatEndsWithoutADropSavesNothingAndTheBarIsAsBefore() {
        val e = editor()
        e.dragStart(REDO, fromBar = true)
        e.dragLeftBar()
        e.dragEnd() // the keyboard was closed mid-drag, or the finger went elsewhere
        assertEquals(listOf(UNDO, REDO, COPY), e.shown)
        assertEquals(emptyList(), writes)
        e.dragStart(PASTE, fromBar = false)
        e.dragOverBar(0)
        e.dragEnd()
        assertEquals(listOf(UNDO, REDO, COPY), e.shown)
        assertEquals(emptyList(), writes)
        assertEquals(listOf(UNDO, REDO, COPY), stored)
    }

    @Test fun aFullBarTakesNoNinthToolButStillLetsTheOthersMove() {
        stored = BarTools.offered.take(BarTools.MAX)
        val e = editor()
        val extra = BarTools.offered.last()
        e.dragStart(extra, fromBar = false)
        assertFalse(e.dragOverBar(0))
        assertTrue(e.full)
        assertNull(e.preview)
        assertEquals(Result.FULL, e.dropOnBar(0))
        assertEquals(BarTools.offered.take(BarTools.MAX), stored)
        assertEquals(Result.FULL, e.add(extra))
        assertTrue(e.full)
        // a tool already in a full bar moves
        e.dragStart(stored.first(), fromBar = true)
        assertTrue(e.dragOverBar(3))
        assertEquals(Result.CHANGED, e.dropOnBar(3))
        assertEquals(BarTools.MAX, stored.size)
        // after taking one out there is room again
        assertEquals(Result.CHANGED, e.remove(stored.last()))
        assertEquals(Result.CHANGED, e.add(extra))
        assertFalse(e.full)
        assertEquals(BarTools.MAX, stored.size)
    }

    @Test fun theAlternativeWithoutDraggingAddsAtTheEndRemovesAndShifts() {
        val e = editor()
        assertEquals(Result.CHANGED, e.add(PASTE))
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), stored)
        assertEquals(Result.UNCHANGED, e.add(PASTE))
        assertEquals(Result.CHANGED, e.shift(PASTE, -1))
        assertEquals(listOf(UNDO, REDO, PASTE, COPY), stored)
        assertEquals(Result.UNCHANGED, e.shift(UNDO, -1))
        assertEquals(Result.UNCHANGED, e.shift(COPY, 1))
        assertEquals(Result.CHANGED, e.toggle(REDO))
        assertEquals(listOf(UNDO, PASTE, COPY), stored)
        assertEquals(Result.CHANGED, e.toggle(REDO))
        assertEquals(listOf(UNDO, PASTE, COPY, REDO), stored)
        assertEquals(Result.CHANGED, e.remove(UNDO))
        assertEquals(Result.UNCHANGED, e.remove(UNDO))
        assertEquals(5, writes.size) // one write per change, none for refused or unchanged ones
    }

    @Test fun anEmptyBarCanBeFilledAndEmptiedAgain() {
        stored = emptyList()
        val e = editor()
        assertEquals(emptyList(), e.shown)
        e.dragStart(AGENT, fromBar = false)
        assertTrue(e.dragOverBar(0))
        assertEquals(Result.CHANGED, e.dropOnBar(0))
        assertEquals(listOf(AGENT), stored)
        assertEquals(Result.CHANGED, e.remove(AGENT))
        assertEquals(emptyList(), stored)
    }

    @Test fun aToolThatIsInTheBarAlreadyIsMovedWhenItComesFromTheGrid() {
        val e = editor()
        e.dragStart(COPY, fromBar = false) // the pill of a tool that is in the bar
        assertTrue(e.dragOverBar(0))
        assertEquals(listOf(COPY, UNDO, REDO), e.shown)
        assertEquals(Result.CHANGED, e.dropOnBar(0))
        assertEquals(listOf(COPY, UNDO, REDO), stored)
    }

    @Test fun theStoredFormatSurvivesAFreshEditorAndTheMigrationIsNotTouchedWithoutAnEdit() {
        val before = BarTools.format(listOf(UNDO, REDO, COPY))
        val e = editor()
        assertEquals(emptyList(), writes) // opening and closing the edit mode writes nothing
        e.add(PASTE)
        val fresh = BarEditor({ BarTools.parse(BarTools.format(stored))!! }, { stored = it })
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), fresh.bar)
        assertTrue(before != BarTools.format(fresh.bar))
        fresh.reload()
        assertEquals(fresh.bar, fresh.shown)
    }

    private fun hiddenVoice() = BarEditor({ stored }, { stored = it; writes += it }, { it == VOICE })

    @Test fun aHiddenVoiceKeyInTheMiddleDoesNotShiftTheDropPlace() {
        stored = listOf(UNDO, VOICE, REDO, COPY) // the voice key is in the list but has no slot: visible slots are UNDO, REDO, COPY
        val e = hiddenVoice()
        e.dragStart(PASTE, fromBar = false)
        // between redo (slot 1) and copy (slot 2): the list index is 3, not 2
        assertEquals(3, e.listIndexForSlot(2))
        assertTrue(e.dragOverBar(e.listIndexForSlot(2)))
        assertEquals(Result.CHANGED, e.dropOnBar(e.listIndexForSlot(2)))
        assertEquals(listOf(UNDO, VOICE, REDO, PASTE, COPY), stored)
        // first slot: before undo; past all slots: the end
        e.dragStart(CUT, fromBar = false)
        assertEquals(0, e.listIndexForSlot(0))
        assertEquals(stored.size, e.listIndexForSlot(9))
    }

    @Test fun aToolMovedInTheBarLandsOnTheVisibleSlotWhateverHiddenToolsLieBetween() {
        stored = listOf(UNDO, VOICE, REDO, COPY)
        val e = hiddenVoice()
        e.dragStart(UNDO, fromBar = true)
        // to the second visible slot (behind redo)
        val index = e.listIndexForSlot(1)
        assertEquals(Result.CHANGED, e.dropOnBar(index))
        assertEquals(listOf(VOICE, REDO, UNDO, COPY), stored)
    }

    @Test fun shiftingSkipsAHiddenToolAndOnlyOffersActionsWithAVisibleNeighbour() {
        stored = listOf(UNDO, VOICE, REDO)
        val e = hiddenVoice()
        assertTrue(e.canShift(UNDO, 1)); assertFalse(e.canShift(UNDO, -1))
        assertTrue(e.canShift(REDO, -1)); assertFalse(e.canShift(REDO, 1))
        assertEquals(Result.CHANGED, e.shift(UNDO, 1)) // a visible change: undo is behind redo now
        assertEquals(listOf(VOICE, REDO, UNDO), stored)
        // with only hidden neighbours there is nothing to move
        stored = listOf(UNDO, VOICE)
        val only = hiddenVoice()
        assertFalse(only.canShift(UNDO, 1))
        assertEquals(Result.UNCHANGED, only.shift(UNDO, 1))
    }

    @Test fun undoTakesBackTheLastChangeOnceAndResetGivesTheStandardBar() {
        val e = editor()
        assertFalse(e.canUndo)
        e.add(PASTE)
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), stored)
        assertTrue(e.canUndo)
        assertEquals(Result.CHANGED, e.undo())
        assertEquals(listOf(UNDO, REDO, COPY), stored)
        assertEquals(Result.UNCHANGED, e.undo()) // one level
        // reset is a change too and can be taken back
        assertEquals(Result.CHANGED, e.reset())
        assertEquals(BarTools.standard, stored)
        assertEquals(listOf(AGENT, SETTINGS, CLIPBOARD, UNDO, VOICE), BarTools.standard)
        e.undo()
        assertEquals(listOf(UNDO, REDO, COPY), stored)
        e.reset(); assertEquals(Result.UNCHANGED, e.reset()) // already the standard bar: nothing to take back from a second reset
    }

    @Test fun slotCountAfterADropCountsOnlyVisibleToolsAndTheDraggedOne() {
        val e = BarEditor({ listOf(UNDO, VOICE, REDO) }, { }, { it == VOICE })
        e.dragStart(PASTE, fromBar = false)
        assertEquals(3, e.slotCountAfterDrop())
        e.dragStart(UNDO, fromBar = true)
        assertEquals(2, e.slotCountAfterDrop())
    }
}
