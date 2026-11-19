// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwappedLettersTest {
    /** Frequencies as in the German and English main dictionaries (0..255, below zero unknown). */
    private val frequencies = mapOf(
        "dir" to 108, "die" to 211, "und" to 213, "ich" to 139, "nicht" to 183, "the" to 222, "and" to 212,
        "sie" to 184, "sei" to 154, "bei" to 186, "rid" to 60, "ide" to 20, "mnu" to 30,
    )
    private val frequency: (String) -> Int = { frequencies[it] ?: -1 }

    private fun candidate(typed: String, known: Boolean = false, frequencyOf: (String) -> Int = frequency) =
        SwappedLetters.candidate(typed, known, frequencyOf)

    @Test fun `swaps lists every distinct swap of two adjacent different letters`() {
        assertEquals(listOf("dir", "ird"), SwappedLetters.swaps("idr"))
        // equal neighbours give nothing, the other pair gives one word
        assertEquals(listOf("lal"), SwappedLetters.swaps("lla"))
        assertEquals(emptyList(), SwappedLetters.swaps("aaa"))
    }

    @Test fun `a swapped very common word is found`() {
        assertEquals("dir", candidate("idr"))
        assertEquals("die", candidate("dei"))
        assertEquals("und", candidate("udn"))
        assertEquals("the", candidate("teh"))
        assertEquals("nicht", candidate("nciht"))
    }

    @Test fun `case of the typed word does not matter, the result is lower case`() {
        assertEquals("dir", candidate("Idr"))
        assertEquals("dir", candidate("IDR"))
    }

    @Test fun `a known typed word is never changed`() {
        assertNull(candidate("sie", known = true)) // "sei" is a word too
        assertNull(candidate("sei", known = true))
    }

    @Test fun `two qualifying swaps are an ambiguity`() {
        // "abc" can become "bac" and "acb"
        val both = mapOf("bac" to 211, "acb" to 150)
        assertNull(candidate("abc", frequencyOf = { both[it] ?: -1 }))
        // a swap that is not very common does not count as a second candidate
        assertEquals("bac", candidate("abc", frequencyOf = { mapOf("bac" to 211, "acb" to 20)[it] ?: -1 }))
    }

    @Test fun `a candidate below the threshold or unknown is not taken`() {
        assertNull(candidate("idr", frequencyOf = { if (it == "dir") SwappedLetters.MIN_FREQUENCY - 1 else -1 }))
        assertEquals("dir", candidate("idr", frequencyOf = { if (it == "dir") SwappedLetters.MIN_FREQUENCY else -1 }))
        assertNull(candidate("idr", frequencyOf = { -1 })) // cannot be judged: nothing happens
    }

    @Test fun `only three to five letters, only letters`() {
        assertNull(candidate("id")) // two letters
        assertNull(candidate("nciths")) // six letters
        assertNull(candidate("id1"))
        assertNull(candidate("i-r"))
        assertNull(candidate("id'"))
        assertNull(candidate(""))
        // five letters is still fine
        assertEquals("nicht", candidate("nciht"))
    }

    @Test fun `the same letters twice in a row are no swap`() {
        assertNull(candidate("aaa", frequencyOf = { 255 }))
    }

    @Test fun `the frequencies of the documented words reach the threshold`() {
        for (word in listOf("dir", "die", "und", "ich", "nicht", "the", "and")) {
            assertEquals(true, frequencies.getValue(word) >= SwappedLetters.MIN_FREQUENCY, word)
        }
    }
}
