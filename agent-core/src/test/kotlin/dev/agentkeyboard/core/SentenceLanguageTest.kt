// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.SentenceLanguage.Evidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentenceLanguageTest {
    @Test fun `no evidence keeps the old rule`() = assertFalse(SentenceLanguage.blocksCorrection(0, 0))

    @Test fun `only L2 words block the correction`() {
        assertTrue(SentenceLanguage.blocksCorrection(0, 1))
        assertTrue(SentenceLanguage.blocksCorrection(0, 3))
    }

    @Test fun `any L1 word keeps the old rule`() {
        assertFalse(SentenceLanguage.blocksCorrection(1, 0))
        assertFalse(SentenceLanguage.blocksCorrection(1, 1))
        assertFalse(SentenceLanguage.blocksCorrection(2, 1))
    }

    @Test fun `classification by the dictionaries`() {
        assertEquals(Evidence.ONLY_L1, SentenceLanguage.evidenceOf(knownByL1 = true, knownByOther = false))
        assertEquals(Evidence.ONLY_L2, SentenceLanguage.evidenceOf(knownByL1 = false, knownByOther = true))
        assertEquals(Evidence.NEUTRAL, SentenceLanguage.evidenceOf(knownByL1 = true, knownByOther = true))
        assertEquals(Evidence.NEUTRAL, SentenceLanguage.evidenceOf(knownByL1 = false, knownByOther = false))
    }

    // stage 101: frequencies as in the real German / English main dictionaries
    @Test fun `classification by frequencies with a clear lead`() {
        assertEquals(Evidence.ONLY_L2, SentenceLanguage.evidenceOfFrequencies(97, 181)) // but
        assertEquals(Evidence.ONLY_L2, SentenceLanguage.evidenceOfFrequencies(75, 151)) // story
        assertEquals(Evidence.ONLY_L1, SentenceLanguage.evidenceOfFrequencies(211, 129)) // die
        assertEquals(Evidence.NEUTRAL, SentenceLanguage.evidenceOfFrequencies(160, 201)) // was
        assertEquals(Evidence.NEUTRAL, SentenceLanguage.evidenceOfFrequencies(78, 118)) // cool
        assertEquals(Evidence.ONLY_L1, SentenceLanguage.evidenceOfFrequencies(162, -1)) // damit
        assertEquals(Evidence.ONLY_L2, SentenceLanguage.evidenceOfFrequencies(-1, 0)) // a word only the other knows, even with 0
        assertEquals(Evidence.NEUTRAL, SentenceLanguage.evidenceOfFrequencies(-1, -1))
    }
}
