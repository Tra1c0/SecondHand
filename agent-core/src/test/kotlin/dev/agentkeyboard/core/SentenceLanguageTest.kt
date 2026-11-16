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
}
