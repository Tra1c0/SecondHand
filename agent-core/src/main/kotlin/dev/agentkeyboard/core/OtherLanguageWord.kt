// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.text.Normalizer
import java.util.Locale

/**
 * Stage 101 (owner: "God" -> "Tod", "sucks" -> "Ducks", "cooled" -> "cooler" on the German layout, where no English
 * dictionary is active): a typed word that no active dictionary knows but the main dictionary of another of the owner's
 * enabled languages does, is not replaced silently by a word of the keyboard language. Pure decision; the app looks the
 * word and the previous words of the sentence up and passes only numbers.
 *
 * The word stays (the correction is offered) when
 * 1. the correction is no twin that differs only by accents or umlaut dots ("fur" -> "für" stays allowed, stage 46/51),
 * 2. the sentence so far does not lean to the keyboard language (more previous words count for it than for the other
 *    language: no protection), and
 * 3. the word is common in the other language: [MIN_FREQUENCY_IN_SENTENCE] when the sentence already leans to that
 *    language, [MIN_FREQUENCY_ALONE] without any evidence (first word, only neutral words before).
 */
object OtherLanguageWord {
    const val MIN_FREQUENCY_IN_SENTENCE = 60
    const val MIN_FREQUENCY_ALONE = 100

    /**
     * [frequency]: the word's frequency (0..255) in the other language, below zero unknown. [forKeyboard], [forOther]: how many
     * of the previous words of the sentence count for the keyboard language and for the other language.
     */
    @JvmStatic
    fun protects(frequency: Int, accentTwin: Boolean, forKeyboard: Int, forOther: Int): Boolean {
        if (frequency < 0 || accentTwin) return false
        if (forKeyboard > forOther) return false
        return frequency >= if (forOther > 0) MIN_FREQUENCY_IN_SENTENCE else MIN_FREQUENCY_ALONE
    }

    /** [typed] and [correction] differ, but are equal in lower case without accents and umlaut dots ("fur" / "für", "lame" / "Lamé"). */
    @JvmStatic
    fun isAccentTwin(typed: String, correction: String): Boolean {
        val a = typed.lowercase(Locale.ROOT)
        val b = correction.lowercase(Locale.ROOT)
        return a != b && strip(a) == strip(b)
    }

    private fun strip(word: String) = Normalizer.normalize(word, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
}
