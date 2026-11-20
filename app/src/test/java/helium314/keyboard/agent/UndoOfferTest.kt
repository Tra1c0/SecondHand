// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 88 ("Kontextwerkzeuge"): when the bar offers a redo after an undo (a heuristic: the app decides what a redo does). */
class UndoOfferTest {
    @Before @After fun clean() { UndoOffer.onChange = null; UndoOffer.reset() }

    @Test fun anUndoStartsTheOfferAndUndoAndRedoKeepIt() {
        assertFalse(UndoOffer.active)
        UndoOffer.onCode(KeyCode.UNDO); assertTrue(UndoOffer.active)
        UndoOffer.onCode(KeyCode.REDO); assertTrue(UndoOffer.active) // alternating
        UndoOffer.onCode(KeyCode.UNDO); assertTrue(UndoOffer.active)
        UndoOffer.onCode(KeyCode.UNDO); assertTrue(UndoOffer.active) // repeated undo is not broken
    }

    @Test fun aRedoAloneOffersNothing() {
        UndoOffer.onCode(KeyCode.REDO)
        assertFalse(UndoOffer.active)
    }

    @Test fun typingDeletingPastingAndTextActionsEndTheOffer() {
        for (code in listOf('a'.code, ' '.code, KeyCode.DELETE, '\n'.code, KeyCode.CLIPBOARD_PASTE, KeyCode.CLIPBOARD_CUT, KeyCode.AGENT_ACTIONS)) {
            UndoOffer.onCode(KeyCode.UNDO)
            assertTrue(UndoOffer.active, "before $code")
            UndoOffer.onCode(code)
            assertFalse(UndoOffer.active, "after $code")
        }
    }

    @Test fun aLineBreakFromShiftEnterAndTextWithoutAKeyCodeEndTheOffer() {
        UndoOffer.onCode(KeyCode.UNDO); UndoOffer.onCode(KeyCode.SHIFT_ENTER)
        assertFalse(UndoOffer.active, "shift+enter inserts a line break")
        UndoOffer.onCode(KeyCode.UNDO); UndoOffer.onTextChange()
        assertFalse(UndoOffer.active)
    }

    @Test fun keysThatChangeNoTextLeaveTheOfferAlone() {
        UndoOffer.onCode(KeyCode.UNDO)
        for (code in listOf(KeyCode.SHIFT, KeyCode.CAPS_LOCK, KeyCode.SYMBOL, KeyCode.ALPHA, KeyCode.LANGUAGE_SWITCH)) {
            UndoOffer.onCode(code)
            assertTrue(UndoOffer.active, "$code")
        }
    }

    @Test fun aNewFieldOrTheKeyboardHidingEndsTheOfferAndTheStripIsToldOnlyOnAChange() {
        var calls = 0
        UndoOffer.onChange = { calls++ }
        UndoOffer.onCode(KeyCode.UNDO); UndoOffer.onCode(KeyCode.UNDO)
        assertEquals(1, calls)
        UndoOffer.reset()
        assertFalse(UndoOffer.active)
        assertEquals(2, calls)
        UndoOffer.reset()
        assertEquals(2, calls)
    }
}
