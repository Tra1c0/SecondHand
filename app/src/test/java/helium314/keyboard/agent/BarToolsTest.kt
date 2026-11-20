// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarKey.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Stage 85: the model of the fixed bar, without any view or preference store. */
class BarToolsTest {
    @Test fun aUserWhoNeverCustomisedGetsTheFirstFiveOfTheOldOrderWithTheMicrophoneAtTheEnd() {
        // the order a fresh installation has (defaultToolbarPref), pinned: the action key
        val enabled = listOf(AGENT, SETTINGS, VOICE, CLIPBOARD, UNDO, REDO, SELECT_WORD, COPY, PASTE, LEFT, RIGHT)
        assertEquals(listOf(AGENT, SETTINGS, CLIPBOARD, UNDO, VOICE), BarTools.migrate(enabled, listOf(AGENT)))
    }

    @Test fun pinnedKeysThatAreNotAmongTheFirstFiveStayInTheBar() {
        // the flat preset: three tools and the dictation key pinned; the dictation key is the circle at the end
        assertEquals(listOf(CLIPBOARD, EMOJI, AGENT, DICTATE), BarTools.migrate(listOf(CLIPBOARD, EMOJI, AGENT), listOf(DICTATE)))
        // a pinned skill key beyond the first five is added (nothing the user had pinned disappears), up to eight
        val order = listOf(AGENT, SETTINGS, CLIPBOARD, UNDO, REDO, SELECT_WORD, COPY)
        assertEquals(listOf(AGENT, SETTINGS, CLIPBOARD, UNDO, REDO, SKILL_1), BarTools.migrate(order, listOf(SKILL_1)))
        val many = listOf(SKILL_1, SKILL_2, SKILL_3, PROMPTS, CHAR_ART, DESIGN)
        assertEquals(BarTools.MAX, BarTools.migrate(order, many).size)
    }

    @Test fun theMicrophoneOnlyMovesWhenNoMicrophoneKeyIsLastAlready() {
        assertEquals(listOf(AGENT, UNDO, DICTATE), BarTools.migrate(listOf(AGENT, UNDO, DICTATE), emptyList()))
        // voice before dictation: the first voice key goes last, dictation stays where it is
        assertEquals(listOf(DICTATE, UNDO, VOICE), BarTools.migrate(listOf(VOICE, DICTATE, UNDO), emptyList()))
        assertEquals(emptyList(), BarTools.migrate(emptyList(), emptyList()))
        assertEquals(listOf(UNDO), BarTools.migrate(listOf(UNDO, UNDO, CLOSE_HISTORY), listOf(UNDO)))
    }

    @Test fun theStoredFormatRoundTripsAndKeepsEveryOtherToolAsDisabled() {
        val bar = listOf(CLIPBOARD, AGENT, VOICE)
        val text = BarTools.format(bar)
        assertTrue(text.startsWith("CLIPBOARD:true|AGENT:true|VOICE:true|"))
        assertEquals(bar, BarTools.parse(text))
        // every offered tool appears exactly once, the internal "close history" key never
        val names = text.split("|").map { it.substringBefore(":") }
        assertEquals(BarTools.offered.map { it.name }.toSet(), names.toSet())
        assertEquals(names.size, names.toSet().size)
        assertFalse(CLOSE_HISTORY.name in names)
        // the old toolbar format is the same, so the settings dialog edits it unchanged
        assertEquals(listOf(UNDO, REDO), BarTools.parse("UNDO:true|COPY:false|REDO:true"))
    }

    @Test fun parsingLimitsToEightAndTellsMissingOrGarbledFromAChoice() {
        assertEquals(null, BarTools.parse(null))
        assertEquals(null, BarTools.parse("")) // blank: no usable value
        assertEquals(null, BarTools.parse("   "))
        assertEquals(emptyList(), BarTools.parse("UNDO:false")) // a real choice: no tools
        // a tool a later version dropped is ignored, duplicates and the internal close key too
        assertEquals(listOf(UNDO), BarTools.parse("NOPE:true|UNDO:true|UNDO:true|COPY:false|CLOSE_HISTORY:true"))
        // garbled: an entry that is not NAME:true or NAME:false makes the whole value unusable (the caller uses the migration)
        assertEquals(null, BarTools.parse("UNDO:true|COPY:maybe"))
        assertEquals(null, BarTools.parse("UNDO:true|broken"))
        assertEquals(null, BarTools.parse("UNDO,true;COPY,true")) // the old test format with the wrong separators
        assertEquals(null, BarTools.parse("UNDO:true||COPY:true"))
        val nine = ToolbarKey.entries.filter { it != CLOSE_HISTORY }.take(9).joinToString("|") { it.name + ":true" }
        assertEquals(BarTools.MAX, BarTools.parse(nine)!!.size)
        assertEquals(ToolbarKey.entries.filter { it != CLOSE_HISTORY }.take(8), BarTools.parse(nine))
    }

    @Test fun insertShiftsTheOthersAndRespectsTheLimit() {
        val bar = listOf(UNDO, REDO, COPY)
        assertEquals(listOf(UNDO, PASTE, REDO, COPY), BarTools.insert(bar, PASTE, 1))
        assertEquals(listOf(PASTE, UNDO, REDO, COPY), BarTools.insert(bar, PASTE, -5))
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), BarTools.insert(bar, PASTE, 99))
        assertSame(bar, BarTools.insert(bar, UNDO, 0)) // already there
        assertSame(bar, BarTools.insert(bar, CLOSE_HISTORY, 0)) // never offered
        val full = BarTools.offered.take(BarTools.MAX)
        assertSame(full, BarTools.insert(full, BarTools.offered.last(), 0)) // the ninth does not fit
        assertEquals(full.size + 0, BarTools.add(full, BarTools.offered.last()).size)
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), BarTools.add(bar, PASTE))
    }

    @Test fun removeAndMoveKeepTheRest() {
        val bar = listOf(UNDO, REDO, COPY, PASTE)
        assertEquals(listOf(UNDO, COPY, PASTE), BarTools.remove(bar, REDO))
        assertEquals(bar, BarTools.remove(bar, CUT))
        assertEquals(listOf(REDO, COPY, UNDO, PASTE), BarTools.move(bar, UNDO, 2))
        assertEquals(listOf(PASTE, UNDO, REDO, COPY), BarTools.move(bar, PASTE, 0))
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), BarTools.move(bar, PASTE, 99))
        assertSame(bar, BarTools.move(bar, CUT, 0))
        assertEquals(listOf(REDO, UNDO, COPY, PASTE), BarTools.shift(bar, UNDO, 1))
        assertSame(bar, BarTools.shift(bar, UNDO, -1)) // at the left end
        assertSame(bar, BarTools.shift(bar, PASTE, 1)) // at the right end
        assertSame(bar, BarTools.shift(bar, CUT, 1)) // not in the bar
    }

    @Test fun theGridListsEveryOfferedToolOnceAndTheFirstOnesAreTheForksOwn() {
        assertEquals(BarTools.offered.toSet(), BarTools.gridOrder.toSet())
        assertEquals(BarTools.offered.size, BarTools.gridOrder.size)
        assertEquals(listOf(AGENT, DICTATE), BarTools.gridOrder.take(2))
        // the new tools of the fork are among them: the store, the character art and the design page
        assertTrue(listOf(PROMPTS, CHAR_ART, DESIGN).all { it in BarTools.gridOrder })
    }

    @Test fun theRowIsCompactFromSixTools() {
        assertEquals((0..8).map { it > 5 }, (0..8).map { BarTools.isCompact(it) })
    }

    @Test fun onlyTheToolsWithOwnFieldChecksAreDimmedAndOnlyWhereTheyRefuse() {
        for (key in BarTools.offered) {
            val protectedTool = key in setOf(AGENT, SKILL_1, SKILL_2, SKILL_3, DICTATE, PROMPTS, CHAR_ART)
            assertEquals(protectedTool, BarTools.isUnavailable(key, incognitoOrProtectedField = true, locked = false), key.name)
            assertEquals(protectedTool, BarTools.isUnavailable(key, incognitoOrProtectedField = false, locked = true), key.name)
            assertFalse(BarTools.isUnavailable(key, incognitoOrProtectedField = false, locked = false), key.name)
        }
        // the keys that edit or move text stay usable in a password field
        assertFalse(BarTools.isUnavailable(UNDO, true, true))
        assertFalse(BarTools.isUnavailable(CLIPBOARD, true, true))
    }

    @Test fun theStoreIsDimmedInNumberLayoutsLikeItsPopupKeyAndTheCharacterArtIsNot() {
        assertTrue(BarTools.isUnavailable(PROMPTS, incognitoOrProtectedField = false, locked = false, numberLayout = true))
        assertFalse(BarTools.isUnavailable(PROMPTS, false, false, numberLayout = false))
        assertFalse(BarTools.isUnavailable(CHAR_ART, false, false, numberLayout = true)) // CharArtKey.popupKeys has no such rule
        assertFalse(BarTools.isUnavailable(UNDO, false, false, numberLayout = true))
        assertTrue(BarTools.isUnavailable(CHAR_ART, true, false, numberLayout = false)) // the field rules still hold
    }

    @Test fun onlyTheClipboardClearingToolDestroysSomethingWithOneTap() {
        assertEquals(setOf(CLEAR_CLIPBOARD), BarTools.DESTRUCTIVE)
    }
}
