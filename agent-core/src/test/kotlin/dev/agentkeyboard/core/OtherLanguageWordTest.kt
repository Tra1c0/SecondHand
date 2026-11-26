// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OtherLanguageWordTest {
    @Test fun `the owner's English words on the German layout stay`() {
        assertTrue(OtherLanguageWord.protects(124, false, 0, 0)) // "God" as first word
        assertTrue(OtherLanguageWord.protects(79, false, 1, 2)) // "sucks" after "God damit that"
        assertTrue(OtherLanguageWord.protects(109, false, 0, 0)) // "cooled" after the neutral "thats"
    }

    @Test fun `a German sentence, a rare word and an accent twin do not protect`() {
        assertFalse(OtherLanguageWord.protects(129, false, 2, 0)) // "wine" after "das ist"
        assertFalse(OtherLanguageWord.protects(129, false, 2, 1))
        assertFalse(OtherLanguageWord.protects(86, false, 0, 0)) // "lame" without evidence (rule C keeps "Lamé" away)
        assertTrue(OtherLanguageWord.protects(86, false, 0, 1)) // with English evidence
        assertFalse(OtherLanguageWord.protects(59, false, 0, 1))
        assertFalse(OtherLanguageWord.protects(116, true, 0, 2)) // "fur" -> "für"
        assertFalse(OtherLanguageWord.protects(-1, false, 0, 3))
    }

    @Test fun `accent twins`() {
        assertTrue(OtherLanguageWord.isAccentTwin("fur", "für"))
        assertTrue(OtherLanguageWord.isAccentTwin("lame", "Lamé"))
        assertTrue(OtherLanguageWord.isAccentTwin("esta", "está"))
        assertFalse(OtherLanguageWord.isAccentTwin("god", "Tod"))
        assertFalse(OtherLanguageWord.isAccentTwin("hand", "Hand")) // case only is no twin
    }
}
