// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import helium314.keyboard.latin.suggestions.BarState.LeftKey
import helium314.keyboard.latin.suggestions.BarState.Show
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 85: the state machine of the fixed bar. */
class BarStateTest {
    @Test fun nothingTypedShowsTheToolsAndTheLeftKeyIsTheGrid() {
        val state = BarState()
        assertEquals(Show.TOOLS, state.show)
        assertEquals(LeftKey.GRID, state.leftKey)
        assertFalse(state.hasContent)
    }

    @Test fun contentReplacesTheToolsAndNeutralBringsThemBack() {
        val state = BarState()
        state.onContent() // typing: words
        assertEquals(Show.CONTENT, state.show)
        assertEquals(LeftKey.SHOW_TOOLS, state.leftKey)
        state.onNeutral() // the word is done
        assertEquals(Show.TOOLS, state.show)
        assertEquals(LeftKey.GRID, state.leftKey)
    }

    @Test fun theLeftKeyWhileContentShowsGoesToTheToolsAndTheNextContentComesBack() {
        val state = BarState()
        state.onContent()
        assertEquals(LeftKey.SHOW_TOOLS, state.onLeftKey())
        assertEquals(Show.TOOLS, state.show)
        assertTrue(state.toolsChosen)
        assertTrue(state.hasContent) // the content still waits
        assertEquals(LeftKey.GRID, state.leftKey)
        state.onContent() // the next key
        assertEquals(Show.CONTENT, state.show)
        assertFalse(state.toolsChosen)
    }

    @Test fun theLeftKeyWhileTheToolsShowOnlyOpensTheGridAndChangesNothing() {
        val state = BarState()
        assertEquals(LeftKey.GRID, state.onLeftKey())
        assertEquals(Show.TOOLS, state.show)
        assertFalse(state.toolsChosen)
        state.onContent(); state.onLeftKey()
        assertEquals(LeftKey.GRID, state.onLeftKey()) // a second tap is the grid again
        assertEquals(Show.TOOLS, state.show)
    }

    @Test fun theOldVisibilityCallsMapToForcingTheToolsOrTheContent() {
        val state = BarState()
        state.onContentRequested() // no content: an empty suggestion area would leave a blank bar
        assertEquals(Show.TOOLS, state.show)
        state.onContent()
        state.onToolsRequested() // "auto show toolbar" for a selection
        assertEquals(Show.TOOLS, state.show)
        state.onContentRequested() // "auto hide" with suggestions
        assertEquals(Show.CONTENT, state.show)
        state.onNeutral()
        state.onToolsRequested()
        state.onContentRequested()
        assertEquals(Show.TOOLS, state.show)
    }

    @Test fun resetMeansToolsAndNothingWaiting() {
        val state = BarState()
        state.onContent(); state.onLeftKey()
        state.reset()
        assertEquals(Show.TOOLS, state.show)
        assertFalse(state.hasContent)
        assertFalse(state.toolsChosen)
    }

    @Test fun theToolsChosenForASelectionAreStickyUntilTheSelectionEndsOrWordsCome() {
        val state = BarState()
        state.onContent(selection = true)
        assertEquals(LeftKey.SHOW_TOOLS, state.onLeftKey())
        assertTrue(state.stickyTools)
        assertEquals(LeftKey.SHOW_CONTENT, state.leftKey) // the key now brings the chips back
        // the range changes: the chips are rebuilt, the tools stay
        repeat(3) { state.onContent(selection = true); assertEquals(Show.TOOLS, state.show); assertTrue(state.stickyTools) }
        state.onContentRequested() // an "auto hide" call does not take the tools away either
        assertEquals(Show.TOOLS, state.show)
        // the toggle: chips, then tools again
        assertEquals(LeftKey.SHOW_CONTENT, state.onLeftKey())
        assertEquals(Show.CONTENT, state.show)
        assertFalse(state.stickyTools)
        assertEquals(LeftKey.SHOW_TOOLS, state.onLeftKey())
        assertTrue(state.stickyTools)
        // the selection ends: neutral, and the next selection starts with the chips
        state.onNeutral()
        assertFalse(state.stickyTools)
        state.onContent(selection = true)
        assertEquals(Show.CONTENT, state.show)
        // words end the choice
        state.onLeftKey()
        state.onContent(selection = false)
        assertEquals(Show.CONTENT, state.show)
        assertFalse(state.stickyTools)
    }

    @Test fun aChoiceOfTheToolsOverWordsIsNotStickyAndOtherContentIsNotASelection() {
        val state = BarState()
        state.onContent() // words
        state.onLeftKey()
        assertFalse(state.stickyTools)
        state.onContent(selection = true) // a selection chip arrives after typing: chips
        assertEquals(Show.CONTENT, state.show)
        state.onContent(selection = false) // a clipboard pill: a different content, not sticky
        state.onLeftKey()
        state.onContent(selection = true)
        assertEquals(Show.CONTENT, state.show)
    }

    // ---- stage 89: the writing session ----

    private fun calmState() = BarState().apply { setCalm(true) }

    @Test fun outsideACalmFieldTypingChangesNothing() {
        val state = BarState() // calm is off: a password field, a number field, other modes
        state.onTyped()
        assertFalse(state.writing)
        state.onContent(); state.onNeutral()
        assertEquals(Show.TOOLS, state.show)
    }

    @Test fun aNewFieldShowsTheToolsAndTheFirstKeyStartsTheSession() {
        val state = calmState()
        state.onNeutral()
        assertEquals(Show.TOOLS, state.show)
        state.onTyped()
        assertTrue(state.writing)
        assertEquals(Show.TOOLS, state.show) // the key changes nothing by itself: the words come with the update
        state.onContent()
        assertEquals(Show.CONTENT, state.show)
    }

    @Test fun inTheSessionNeutralKeepsTheWordsAreaUntilTheFieldIsEmptied() {
        val state = calmState()
        state.onTyped(); state.onContent()
        state.onNeutral() // a space with nothing to offer
        assertEquals(Show.CONTENT, state.show)
        assertFalse(state.hasContent)
        assertEquals(LeftKey.SHOW_TOOLS, state.leftKey)
        state.onPause() // stage 89b: the hold of the strip ran out: the tools, the session goes on
        assertEquals(Show.TOOLS, state.show)
        assertTrue(state.writing)
        state.onNeutral() // and a further neutral update does not empty the bar
        assertEquals(Show.TOOLS, state.show)
        state.onContent(); state.onPause() // a pause with words changes nothing
        assertEquals(Show.CONTENT, state.show)
        state.onNeutral(fieldEmpty = true)
        assertEquals(Show.TOOLS, state.show)
        assertFalse(state.writing)
        state.onContent(); state.onNeutral() // not a session any more: as before
        assertEquals(Show.TOOLS, state.show)
    }

    @Test fun aDeleteInAFieldWithTextStartsTheSessionToo() {
        val state = calmState()
        state.onNeutral(); state.onTyped() // the hook calls onTyped for the delete key as well
        state.onNeutral()
        assertTrue(state.writing)
        assertEquals(Show.TOOLS, state.show)
    }

    @Test fun theToolsTheUserAskedForStayUntilTheNextTypedKey() {
        val state = calmState()
        state.onTyped(); state.onContent()
        assertEquals(LeftKey.SHOW_TOOLS, state.onLeftKey())
        assertTrue(state.pinned)
        assertEquals(LeftKey.GRID, state.leftKey)
        state.onContent(); assertEquals(Show.TOOLS, state.show) // words (a resume after an undo) do not take them
        state.onNeutral(); assertEquals(Show.TOOLS, state.show)
        state.onContentRequested(); assertEquals(Show.TOOLS, state.show)
        state.onTyped(); assertFalse(state.pinned)
        state.onContent(); assertEquals(Show.CONTENT, state.show)
    }

    @Test fun aToolTappedWhileTheToolsShowKeepsThem() {
        val state = calmState()
        state.onTyped(); state.onContent()
        state.onToolTapped() // the bar shows words: no tool was tapped, nothing happens
        assertFalse(state.pinned)
        state.onLeftKey(); state.onTyped() // tools, then a key: back to the session
        state.onNeutral(); state.onToolsRequested() // the tools show (auto show, selection)
        state.onToolTapped()
        assertTrue(state.pinned)
        state.onNeutral(); assertEquals(Show.TOOLS, state.show)
    }

    @Test fun selectionChipsWinOverPinnedToolsAndEndThem() {
        val state = calmState()
        state.onTyped(); state.onContent(); state.onLeftKey()
        assertTrue(state.pinned)
        state.onContent(selection = true)
        assertEquals(Show.CONTENT, state.show)
        assertFalse(state.pinned)
    }

    @Test fun contentRequestedInTheSessionShowsTheWordsAreaEvenWithoutContent() {
        val state = calmState()
        state.onTyped(); state.onNeutral(); state.onToolsRequested()
        state.onContentRequested()
        assertEquals(Show.CONTENT, state.show)
    }

    @Test fun resetEndsTheSession() {
        val state = calmState()
        state.onTyped(); state.onContent(); state.onLeftKey()
        state.reset()
        assertFalse(state.writing); assertFalse(state.pinned); assertEquals(Show.TOOLS, state.show)
    }
}
