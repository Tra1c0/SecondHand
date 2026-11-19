// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale

/**
 * Stage 75: a very common short word typed with two adjacent letters swapped ("idr" for "dir",
 * "teh" for "the"). The engine's own ranking does not reliably offer the word, so the keyboard looks
 * for it itself. Pure functions, no dictionary and no text is kept here.
 *
 * The rule is deliberately narrow: only a typed word of [MIN_LETTERS] to [MAX_LETTERS] letters that no
 * dictionary knows, only the swap of exactly one pair of different adjacent letters, only a result
 * whose frequency in the main dictionary is at least [MIN_FREQUENCY], and only when exactly one swap
 * qualifies (two qualifying swaps are a real ambiguity: nothing is corrected).
 */
object SwappedLetters {
    const val MIN_LETTERS = 3
    const val MAX_LETTERS = 5

    /**
     * Smallest dictionary frequency (the 0..255 unigram probability of the main dictionaries; "ich" 139,
     * "nicht" 183, "und" 213, "dir" 108 in the German dictionary, "the" 222, "and" 212 in the English one)
     * that counts as very common. About the 700 most frequent words of three to five letters in German
     * and the 2500 most frequent in English reach it.
     */
    const val MIN_FREQUENCY = 100

    /** The distinct words that arise from [word] by swapping one pair of adjacent, different letters. */
    @JvmStatic
    fun swaps(word: String): List<String> {
        val result = LinkedHashSet<String>()
        for (i in 0 until word.length - 1) {
            if (word[i] == word[i + 1]) continue
            val chars = word.toCharArray()
            chars[i] = word[i + 1]
            chars[i + 1] = word[i]
            result.add(String(chars))
        }
        return result.toList()
    }

    /**
     * The lower case word that [typed] most likely means, or null. [typedIsKnown]: some dictionary knows
     * the typed word (then it is never changed). [frequencyOf] gives the main-dictionary frequency of a
     * lower case word, anything below zero when it is unknown or cannot be judged (the word is then
     * not a candidate).
     */
    @JvmStatic
    fun candidate(typed: String, typedIsKnown: Boolean, frequencyOf: (String) -> Int): String? {
        if (typedIsKnown || typed.length < MIN_LETTERS || typed.length > MAX_LETTERS || !typed.all { it.isLetter() }) return null
        val lower = typed.lowercase(Locale.ROOT)
        val qualifying = swaps(lower).filter { it != lower && frequencyOf(it) >= MIN_FREQUENCY }
        return qualifying.singleOrNull()
    }
}
