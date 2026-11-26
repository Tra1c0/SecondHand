// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RareWordWeightsTest {
    private fun corrects(typed: Int = 56, candidate: Int = 177, rejected: Boolean = false, history: Boolean = true,
                         hTyped: Int = -1, hCandidate: Int = 160, cCandidate: Int? = null, cTyped: Int? = null) =
        RareWordWeights.corrects(typed, candidate, "de", rejected, history, hTyped, hCandidate, cCandidate, cTyped)

    @Test fun `the owner's cases`() {
        assertTrue(corrects()) // mur -> nur, "nur" used, "mur" never
        assertTrue(corrects(typed = 77, candidate = 194, hCandidate = 170)) // AIF -> auf
        assertFalse(corrects(typed = 69, candidate = 135)) // ALR -> alt: "alt" is not very common
        assertFalse(corrects(typed = 78, candidate = 186, hTyped = 160, hCandidate = 170)) // BSI used as often as "bei"
    }

    @Test fun `a kept typo in the history does not protect it, a word used as often does`() {
        assertTrue(corrects(hTyped = 111, hCandidate = 160))
        assertTrue(corrects(hTyped = 140, hCandidate = 160)) // exactly the lead
        assertFalse(corrects(hTyped = 141, hCandidate = 160))
        assertFalse(corrects(hTyped = 130, hCandidate = -1)) // the owner uses the typed word, not the candidate
    }

    @Test fun `without a usable history only the sentence decides`() {
        assertFalse(corrects(history = false, hCandidate = -1))
        assertFalse(corrects(hCandidate = 100)) // the candidate is not in use yet: like no history
        assertTrue(corrects(history = false, hCandidate = -1, cCandidate = 300))
        assertTrue(corrects(history = false, hCandidate = -1, cCandidate = 300, cTyped = 279))
        assertFalse(corrects(history = false, hCandidate = -1, cCandidate = 300, cTyped = 280))
    }

    @Test fun `an undone correction, a common typed word and an unknown one never`() {
        assertFalse(corrects(rejected = true))
        assertFalse(corrects(typed = 81))
        assertFalse(corrects(typed = -1))
        assertFalse(corrects(candidate = 149))
        assertTrue(corrects(candidate = 150))
    }

    @Test fun `thresholds`() {
        assertEquals(150, RareWordWeights.minCandidate("de"))
        assertEquals(170, RareWordWeights.minCandidate("en"))
        assertFalse(RareWordWeights.corrects(56, 160, "en", false, true, -1, 160, null, null))
    }
}
