// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnglishITest {
    @Test fun `the forms`() {
        assertEquals("I", EnglishI.capitalized("i"))
        assertEquals("I'm", EnglishI.capitalized("i'm"))
        assertEquals("I'll", EnglishI.capitalized("i'll"))
        assertEquals("I'd", EnglishI.capitalized("i'd"))
        assertEquals("I've", EnglishI.capitalized("i've"))
        assertEquals("I’m", EnglishI.capitalized("i’m"))
    }

    @Test fun `real words and capitals are not touched`() {
        assertNull(EnglishI.capitalized("im"))
        assertNull(EnglishI.capitalized("ill"))
        assertNull(EnglishI.capitalized("id"))
        assertNull(EnglishI.capitalized("I"))
        assertNull(EnglishI.capitalized("ich"))
        assertNull(EnglishI.capitalized(""))
    }

    @Test fun `English layout always, another one only with an English sentence`() {
        assertTrue(EnglishI.applies("en", 0, 0))
        assertTrue(EnglishI.applies("de", 2, 0)) // "cool story but i"
        assertFalse(EnglishI.applies("de", 0, 0)) // first word: "i hob"
        assertFalse(EnglishI.applies("de", 1, 1)) // "ich … but i"
        assertFalse(EnglishI.applies("de", 0, 1)) // "ich glaube i"
    }
}
