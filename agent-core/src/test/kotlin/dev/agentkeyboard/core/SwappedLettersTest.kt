// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwappedLettersTest {
    /** Frequencies as in the real German and English main dictionaries (0..255, below zero unknown). */
    private val frequencies = mapOf(
        "dir" to 108, "mir" to 125, "ebd" to 101, "die" to 211, "und" to 213, "ich" to 139, "nicht" to 183,
        "the" to 222, "and" to 212, "for" to 196, "you" to 171, "are" to 186, "hub" to 119, "bat" to 117, "duo" to 122,
        "inch" to 126, "sub" to 131, "sie" to 184, "sei" to 154, "bei" to 186, "rid" to 60, "ide" to 20, "mnu" to 30,
    )
    private val frequency: (String) -> Int = { frequencies[it] ?: -1 }

    private fun candidate(typed: String, known: Boolean = false, language: String = "de", frequencyOf: (String) -> Int = frequency) =
        SwappedLetters.candidate(typed, language, { known }, frequencyOf)

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
        assertEquals("the", candidate("teh", language = "en"))
        assertEquals("nicht", candidate("nciht"))
    }

    @Test fun `case of the typed word does not matter for the first letter, the result is lower case`() {
        assertEquals("dir", candidate("Idr"))
    }

    @Test fun `a capital after the first letter stops the rule`() {
        assertNull(candidate("idR"))
        assertNull(candidate("IDR"))
        assertNull(candidate("BSi"))
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

    @Test fun `thresholds per language through the real function`() {
        // German 105
        assertEquals("dir", candidate("idr")) // 108
        assertEquals("mir", candidate("mri")) // 125
        assertNull(candidate("deb")) // ebd 101
        assertNull(candidate("idr", frequencyOf = { if (it == "dir") 104 else -1 }))
        assertEquals("dir", candidate("idr", frequencyOf = { if (it == "dir") 105 else -1 }))
        // English 150
        for ((typed, meant) in listOf("teh" to "the", "nad" to "and", "fro" to "for", "yuo" to "you", "aer" to "are"))
            assertEquals(meant, candidate(typed, language = "en"), meant)
        for (typed in listOf("uhb", "abt", "udo", "usb", "nich")) assertNull(candidate(typed, language = "en"), typed)
        assertNull(candidate("idr", language = "en", frequencyOf = { if (it == "dir") 149 else -1 }))
        assertEquals("dir", candidate("idr", language = "en", frequencyOf = { if (it == "dir") 150 else -1 }))
        // any other language 130
        assertNull(candidate("idr", language = "fr", frequencyOf = { if (it == "dir") 129 else -1 }))
        assertEquals("dir", candidate("idr", language = "fr", frequencyOf = { if (it == "dir") 130 else -1 }))
        assertNull(candidate("idr", language = "", frequencyOf = { -1 })) // cannot be judged: nothing happens
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

    @Test fun `the cheap checks come before any dictionary lookup`() {
        var lookups = 0
        for (typed in listOf("id", "nciths", "idR", "id1", "")) {
            assertNull(SwappedLetters.candidate(typed, "de", { lookups++; false }, { lookups++; 255 }), typed)
        }
        assertEquals(0, lookups)
        // a known typed word costs no frequency lookup
        assertNull(SwappedLetters.candidate("idr", "de", { lookups++; true }, { lookups += 100; 255 }))
        assertEquals(1, lookups)
    }

    @Test fun `the same letters twice in a row are no swap`() {
        assertNull(candidate("aaa", frequencyOf = { 255 }))
    }
}
