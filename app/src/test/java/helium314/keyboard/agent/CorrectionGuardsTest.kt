// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CorrectionGuardsTest {
    private val de = Locale.GERMAN

    @Test fun acronymsAreNeverReplaced() {
        assertTrue(CorrectionGuards.blocks("ETF", "ETC", de))
        assertTrue(CorrectionGuards.blocks("AVD", "AND", Locale.ENGLISH))
        assertTrue(CorrectionGuards.blocks("ÖPNV", "ÖPNVs", de))
    }

    @Test fun normalCapitalsAndLongShoutingAreNoAcronyms() {
        assertFalse(CorrectionGuards.isAcronym("Hallo"))
        assertFalse(CorrectionGuards.isAcronym("I"))
        assertFalse(CorrectionGuards.isAcronym("HALLOOO")) // longer than five: shouting, the correction stays
        assertFalse(CorrectionGuards.isAcronym("H2O"))
    }

    @Test fun aCorrectionThatExchangesTheGermanPrefixIsOnlyOffered() {
        assertTrue(CorrectionGuards.blocks("berstehen", "verstehen", de))
        assertTrue(CorrectionGuards.blocks("entsetzen", "versetzen", de))
    }

    @Test fun aCorrectionThatKeepsThePrefixStillRuns() {
        assertFalse(CorrectionGuards.blocks("vergessn", "vergessen", de))
        assertFalse(CorrectionGuards.blocks("gehn", "gehen", de)) // too short to count as a prefix word
        assertFalse(CorrectionGuards.blocks("bekomen", "bekommen", de))
        assertFalse(CorrectionGuards.blocks("vrestehen", "verstehen", de)) // the typo is in the prefix itself: no prefix typed
    }

    @Test fun thePrefixRuleIsGermanOnly() {
        assertFalse(CorrectionGuards.blocks("berry", "very", Locale.ENGLISH))
        assertFalse(CorrectionGuards.blocks("berstehen", "verstehen", null))
    }
}
