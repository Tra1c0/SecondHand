// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GermanVerbFormsTest {
    /** A part of the bundled verb list (all of these are in `german_verb_lemmas.txt`). */
    private val lemmas = setOf("übermitteln", "marschieren", "faseln", "fiedeln", "wandern", "machen", "gehen", "arbeiten", "atmen", "reden")
    private fun form(word: String) = GermanVerbForms.isRegularForm(word) { it in lemmas }

    @Test fun `the owner's findings are regular forms`() {
        assertTrue(form("übermittle")) // ich übermittle (short form of -eln)
        assertTrue(form("marschiere"))
        assertTrue(form("faselte"))
        assertTrue(form("fiedel")) // colloquial "ich fiedel"
    }

    @Test fun `present, past and the -eln and -ern forms`() {
        for (word in listOf("mache", "machst", "macht", "machen", "machte", "machtest", "machten", "machtet",
            "fiedele", "fiedelst", "fiedelt", "fiedeln", "fiedelte", "fiedle",
            "wander", "wandere", "wandre", "wanderst", "wandert", "wanderte",
            "marschierst", "marschiert", "marschierte", "übermittelt", "übermittelte")) {
            assertTrue(form(word), word)
        }
    }

    @Test fun `stems that take an extra e`() {
        assertTrue(form("arbeite"))
        assertTrue(form("arbeitest"))
        assertTrue(form("arbeitet"))
        assertTrue(form("arbeitete"))
        assertTrue(form("atmete"))
        assertTrue(form("redete"))
        assertFalse(form("arbeitte"))
        assertFalse(form("arbeitst"))
    }

    @Test fun `typos that only look like forms are not protected`() {
        assertFalse(form("machre"))
        assertFalse(form("mavhe"))
        assertFalse(form("übermittler")) // a noun, not a verb form
        assertFalse(form("faselbe"))
    }

    @Test fun `irregular verbs are not known, a regular-looking past is protected and never produced`() {
        // documented weakness: "gehte" counts as a past of "gehen"
        assertTrue(form("gehte"))
    }

    @Test fun `short words, capitals and other characters are no forms`() {
        assertFalse(form("geh")) // too short
        assertFalse(form("Mache")) // the caller lowers a capital at the beginning of a sentence
        assertFalse(form("mach'"))
        assertFalse(form("mache1"))
        assertFalse(GermanVerbForms.isRegularForm("marschiere") { false })
    }
}
