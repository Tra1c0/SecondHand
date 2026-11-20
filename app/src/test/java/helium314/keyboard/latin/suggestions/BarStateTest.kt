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
}
