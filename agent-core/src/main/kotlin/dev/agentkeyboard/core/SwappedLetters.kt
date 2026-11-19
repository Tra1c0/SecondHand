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
 * whose frequency in the main dictionary of the keyboard language reaches the threshold of that language ([minFrequency]), and only when exactly one swap
 * qualifies (two qualifying swaps are a real ambiguity: nothing is corrected).
 */
object SwappedLetters {
    const val MIN_LETTERS = 3
    const val MAX_LETTERS = 5

    /**
     * Smallest dictionary frequency (the 0..255 unigram probability of the main dictionary of the keyboard
     * language) that counts as very common, per language. Values read from the real dictionaries:
     * - German 105: "dir" 108, "mir" 125, "dich" 112, "ich" 139, "die" 211, "und" 213 qualify; "ebd" 101
     *   (an abbreviation) does not.
     * - English 150: "the" 222, "and" 212, "for" 196, "you" 171, "are" 186 qualify; short words that are
     *   merely known, such as "hub" 119, "bat" 117, "duo" 122, "inch" 126, "sub" 131, do not.
     * - Any other language 130 (not reviewed yet, so on the cautious side).
     */
    const val MIN_FREQUENCY_DE = 105
    const val MIN_FREQUENCY_EN = 150
    const val MIN_FREQUENCY_OTHER = 130

    /** The threshold for the language code ("de", "en", ...) of the keyboard language. */
    @JvmStatic
    fun minFrequency(language: String): Int = when (language.lowercase(Locale.ROOT)) {
        "de" -> MIN_FREQUENCY_DE
        "en" -> MIN_FREQUENCY_EN
        else -> MIN_FREQUENCY_OTHER
    }

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
     * The lower case word that [typed] most likely means, or null. [language]: language code of the keyboard
     * language (chooses the threshold). [typedIsKnown]: some dictionary of any active language knows the
     * typed word in some case (then it is never changed); asked only after the cheap checks (3 to 5 letters,
     * only letters, no capital after the first letter), so a longer or shorter word costs no lookup.
     * [frequencyOf] gives the frequency of a lower case word in the dictionary of the keyboard language,
     * anything below zero when it is unknown or cannot be judged (the word is then not a candidate).
     */
    @JvmStatic
    fun candidate(typed: String, language: String, typedIsKnown: () -> Boolean, frequencyOf: (String) -> Int): String? {
        if (typed.length < MIN_LETTERS || typed.length > MAX_LETTERS || !typed.all { it.isLetter() }) return null
        if (typed.drop(1).any { it.isUpperCase() }) return null
        if (typedIsKnown()) return null
        val lower = typed.lowercase(Locale.ROOT)
        val minimum = minFrequency(language)
        val qualifying = swaps(lower).filter { it != lower && frequencyOf(it) >= minimum }
        return qualifying.singleOrNull()
    }
}
