// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GermanShortFormsTest {
    private val german = setOf("gibt", "wird", "dreht", "geht", "war", "hat", "ist", "klappt", "recht", "nicht")
    private val lemmas = setOf("drehen", "gehen", "klappen", "rechen")
    private fun target(word: String) = GermanShortForms.target(word, { it in german }) { it in lemmas }

    @Test fun `the owner's forms`() {
        assertEquals("gibt's", target("gibts"))
        assertEquals("wird's", target("wirds"))
        assertEquals("dreht's", target("drehts"))
        assertEquals("geht's", target("gehts"))
        assertEquals("war's", target("wars"))
        assertEquals("hat's", target("hats"))
        assertEquals("ist's", target("ists"))
        assertEquals("klappt's", target("klappts"))
    }

    @Test fun `no verb before the s, or a verb the dictionary does not know`() {
        assertNull(target("dings"))
        assertNull(target("kaufts")) // "kauft" is not in this test's dictionary
        assertNull(target("gibt")) // no s
        assertNull(target("its")) // too short
        assertNull(target("Gibts")) // the caller lowers a capital at the beginning of a sentence
        assertNull(target("gibt's"))
    }

    @Test fun `real words are the caller's question`() {
        // "rechts" would be "recht's" (a verb form of "rechen"); the caller keeps it because the dictionary knows "rechts"
        assertEquals("recht's", target("rechts"))
    }
}
