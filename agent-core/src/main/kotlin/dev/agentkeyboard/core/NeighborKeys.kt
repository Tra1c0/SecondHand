// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale

/**
 * Stage 101 (owner: "icj" stays, meant "ich"): a very common short word typed with one letter replaced by a neighbouring key.
 * The engine does not change a word that the typing history knows (every committed word is learned, typos included), so
 * the keyboard looks for it itself, like [SwappedLetters]. Pure functions, no dictionary and no text is kept here.
 *
 * Narrow on purpose: [MIN_LETTERS] to [MAX_LETTERS] letters, no capital after the first letter, exactly one letter replaced
 * by a key next to it (QWERTZ for German, QWERTY otherwise), the result reaches the threshold of the keyboard language
 * ([minFrequency]) and exactly one replacement qualifies. Whether the typed word is known is the caller's question.
 */
object NeighborKeys {
    const val MIN_LETTERS = 3
    const val MAX_LETTERS = 5

    /**
     * Smallest main-dictionary frequency that counts as very common: German 130 ("ich" 139, "nur" 177, "noch" 176, "auf" 194;
     * "dir" 108 and "mir" 125 are left to the swapped letters), English 150 like [SwappedLetters], any other language 140.
     */
    const val MIN_FREQUENCY_DE = 130
    const val MIN_FREQUENCY_EN = 150
    const val MIN_FREQUENCY_OTHER = 140

    private val qwertz = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
    private val qwerty = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val qwertySpanish = listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")

    @JvmStatic
    fun minFrequency(language: String): Int = when (language.lowercase(Locale.ROOT)) {
        "de" -> MIN_FREQUENCY_DE
        "en" -> MIN_FREQUENCY_EN
        else -> MIN_FREQUENCY_OTHER
    }

    private fun layoutRows(language: String) = when (language.lowercase(Locale.ROOT)) {
        "de" -> qwertz
        "es" -> qwertySpanish
        else -> qwerty
    }

    /**
     * The keys next to [c] on the letter rows of [language]: left and right, and the two touching keys of the row above and
     * of the row below (the rows are staggered).
     */
    @JvmStatic
    fun neighbors(c: Char, language: String): Set<Char> {
        val rows = layoutRows(language)
        val r = rows.indexOfFirst { c in it }
        if (r < 0) return emptySet()
        val i = rows[r].indexOf(c)
        val result = LinkedHashSet<Char>()
        fun add(row: Int, index: Int) { if (row in rows.indices && index in rows[row].indices) result.add(rows[row][index]) }
        add(r, i - 1); add(r, i + 1)
        add(r - 1, i); add(r - 1, i + 1)
        add(r + 1, i - 1); add(r + 1, i)
        return result
    }

    /** The distinct words that arise from [word] by replacing one letter with a neighbouring key. */
    @JvmStatic
    fun replacements(word: String, language: String): List<String> {
        val result = LinkedHashSet<String>()
        for (i in word.indices) for (n in neighbors(word[i], language)) {
            result.add(word.substring(0, i) + n + word.substring(i + 1))
        }
        return result.toList()
    }

    /**
     * The lower case word [typed] most likely means, or null. [frequencyOf] gives the frequency of a lower case word in the
     * main dictionary of the keyboard language (below zero unknown). Asked only for 3 to 5 letters without a capital after
     * the first letter, so other words cost no lookup.
     */
    @JvmStatic
    fun candidate(typed: String, language: String, frequencyOf: (String) -> Int): String? {
        if (typed.length < MIN_LETTERS || typed.length > MAX_LETTERS || !typed.all { it.isLetter() }) return null
        if (typed.drop(1).any { it.isUpperCase() }) return null
        val lower = typed.lowercase(Locale.ROOT)
        val minimum = minFrequency(language)
        return replacements(lower, language).filter { it != lower && frequencyOf(it) >= minimum }.singleOrNull()
    }
}
