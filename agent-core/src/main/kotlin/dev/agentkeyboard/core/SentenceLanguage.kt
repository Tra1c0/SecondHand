// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 60: the language of the sentence so far decides whether a correction may turn a word of
 * another active language into a word of the correction's language (English "name" -> German
 * "Name" inside an English sentence). Pure decision; the app classifies the previous words of the
 * sentence by the main dictionaries and asks here. No text is kept or passed in, only counts.
 *
 * L1 is the language of the correction, L2 any other active language whose main dictionary knows
 * the typed word as typed. The correction is blocked when at least one previous word is known only
 * to L2 and none is known only to L1. Words both or none know are neutral.
 */
object SentenceLanguage {
    /** How many previous words of the sentence count as evidence. */
    const val MAX_PREVIOUS_WORDS = 3

    /** Which active languages' main dictionaries know a previous word. */
    enum class Evidence { ONLY_L1, ONLY_L2, NEUTRAL }

    fun evidenceOf(knownByL1: Boolean, knownByOther: Boolean): Evidence = when {
        knownByL1 && !knownByOther -> Evidence.ONLY_L1
        knownByOther && !knownByL1 -> Evidence.ONLY_L2
        else -> Evidence.NEUTRAL
    }

    /** True: the typed word of L2 stays, the correction of L1 remains a suggestion. */
    fun blocksCorrection(onlyL1: Int, onlyL2: Int): Boolean = onlyL2 > 0 && onlyL1 == 0

    /**
     * Stage 101: how many points of the 0..255 main-dictionary frequency one language must be ahead for a word both know to
     * count for it. Read from the real dictionaries (German / English): "and" 150/212, "but" 97/181, "is" 120/200, "story"
     * 75/151 count for English, "die" 211/129 and "den" 201/106 for German; "was" 160/201, "cool" 78/118, "so", "in" stay
     * neutral.
     */
    const val DOMINANCE = 60

    /**
     * Stage 101: like [evidenceOf], from frequencies (below zero: the language does not know the word): a word only one
     * language knows counts for it, a word both know counts for the one at least [DOMINANCE] points ahead.
     */
    fun evidenceOfFrequencies(frequencyL1: Int, frequencyL2: Int): Evidence = when {
        frequencyL1 < 0 && frequencyL2 < 0 -> Evidence.NEUTRAL
        frequencyL2 < 0 -> Evidence.ONLY_L1
        frequencyL1 < 0 -> Evidence.ONLY_L2
        frequencyL1 >= frequencyL2 + DOMINANCE -> Evidence.ONLY_L1
        frequencyL2 >= frequencyL1 + DOMINANCE -> Evidence.ONLY_L2
        else -> Evidence.NEUTRAL
    }
}
