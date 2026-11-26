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

    // stage 101 (owner: "alr" -> "ALR", "aif" -> "AIF", "lame" -> "Lamé")
    @Test fun aLowerCaseWordNeverBecomesCapitalsOnly() {
        assertTrue(CorrectionGuards.blocksTarget("alr", "ALR", de, false))
        assertTrue(CorrectionGuards.blocksTarget("aif", "AIF", de, false))
        assertTrue(CorrectionGuards.blocksTarget("Aif", "AIF", de, false)) // a capital at the beginning of a sentence
        assertTrue(CorrectionGuards.blocksTarget("usa", "USA", Locale.ENGLISH, false))
        // typed in capitals, one letter, a normal noun, or wanted by the dictionary / a shortcut: no veto
        assertFalse(CorrectionGuards.blocksTarget("ALR", "ALT", de, false))
        assertFalse(CorrectionGuards.blocksTarget("i", "I", Locale.ENGLISH, false))
        assertFalse(CorrectionGuards.blocksTarget("ort", "Ort", de, false))
        assertFalse(CorrectionGuards.blocksTarget("aif", "AIF", de, true))
    }

    @Test fun aGermanCorrectionNeverBringsInAForeignAccent() {
        assertTrue(CorrectionGuards.blocksTarget("lame", "Lamé", de, false))
        assertTrue(CorrectionGuards.blocksTarget("creme", "Crème", de, false))
        // umlauts and ß are German, an accent the owner typed himself stays possible, other languages are not judged
        assertFalse(CorrectionGuards.blocksTarget("fur", "für", de, false))
        assertFalse(CorrectionGuards.blocksTarget("strase", "Straße", de, false))
        assertFalse(CorrectionGuards.blocksTarget("Cafée", "Café", de, false))
        assertFalse(CorrectionGuards.blocksTarget("esta", "está", Locale("es"), false))
        assertFalse(CorrectionGuards.blocksTarget("lame", "Lamé", de, true))
    }
}
