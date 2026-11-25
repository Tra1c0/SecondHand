// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeighborKeysTest {
    /** Frequencies as in the real German and English main dictionaries (0..255, below zero unknown). */
    private val frequencies = mapOf(
        "ich" to 139, "nur" to 177, "mir" to 125, "kur" to 48, "noch" to 176, "auf" to 194, "gab" to 162, "gag" to 51,
        "alt" to 135, "ale" to 60, "löscht" to 80, "lösche" to 34, "the" to 222, "and" to 212, "dir" to 108,
    )
    private val frequency: (String) -> Int = { frequencies[it] ?: -1 }
    private fun candidate(typed: String, language: String = "de") = NeighborKeys.candidate(typed, language, frequency)

    @Test fun `neighbours on QWERTZ`() {
        assertEquals(setOf('g', 'j', 'z', 'u', 'b', 'n'), NeighborKeys.neighbors('h', "de"))
        assertTrue('h' in NeighborKeys.neighbors('j', "de"))
        assertTrue('n' in NeighborKeys.neighbors('m', "de"))
        assertTrue('u' in NeighborKeys.neighbors('i', "de"))
        // QWERTY for English: y is in the top row
        assertEquals(setOf('t', 'u', 'g', 'h'), NeighborKeys.neighbors('y', "en"))
        assertEquals(emptySet(), NeighborKeys.neighbors('1', "de"))
    }

    @Test fun `the owner's typos`() {
        assertEquals("ich", candidate("icj"))
        // "mur" has "nur" (177) next to it; that it is a dictionary word is the caller's question
        assertEquals("nur", candidate("mur"))
        assertEquals("auf", candidate("aif"))
        // two keys off, or no very common result: nothing
        assertNull(candidate("mocb"))
        assertNull(candidate("löschr")) // six letters
    }

    @Test fun `a capital first letter is fine, the result is lower case`() {
        assertEquals("ich", candidate("Icj"))
        assertNull(candidate("icJ"))
    }

    @Test fun `thresholds per language and uniqueness`() {
        assertEquals(130, NeighborKeys.minFrequency("de"))
        assertEquals(150, NeighborKeys.minFrequency("en"))
        assertEquals(140, NeighborKeys.minFrequency("fr"))
        assertNull(candidate("dor")) // "dir" 108 is below 130
        assertEquals("the", candidate("thw", "en"))
        // two qualifying results are an ambiguity
        val two = mapOf("ich" to 139, "ick" to 140)
        assertNull(NeighborKeys.candidate("icj", "de") { two[it] ?: -1 })
    }

    @Test fun `only three to five letters, only letters`() {
        assertNull(candidate("ic"))
        assertNull(candidate("ic-j"))
        assertNull(candidate("icj1"))
    }
}
