// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale

/**
 * Stage 107 (owner, 2026-11-26: "Mur habe ich noch nie benutzt … mit Gewichten arbeiten", "aif" -> "auf"): whether a word that
 * the main dictionary knows, but only rarely ("mur" 56, "AIF" 77), is replaced by its one very common neighbouring-key word
 * ("nur" 177, "auf" 194), even with "Richtige Wörter nie ändern". Pure decision; the app passes numbers only.
 *
 * The weight is the owner's own use, read from the typing history (it rises with every committed word, so every correction
 * left standing raises the candidate and every typed word kept raises the typed word): the candidate must be in use
 * ([HISTORY_USED], about twice) and ahead of the typed word by [HISTORY_LEAD] points, or the typed word must never have been
 * committed. Without a usable history (learning off, incognito, new device, candidate not yet used) the sentence before must
 * make the candidate clearly likelier ([CONTEXT_LEAD], the engine's own rule for next-word scores). An undone correction
 * ([rejected]) wins over everything, at once.
 */
object RareWordWeights {
    /** Highest main-dictionary frequency (0..255, any case form) that counts as a rare word: "mur" 56, "ALR" 69, "AIF" 77, "BSI" 78. */
    const val RARE_MAX = 80
    /** Lowest main-dictionary frequency of the candidate: German 150 ("nur" 177, "auf" 194; "alt" 135 does not qualify). */
    const val COMMON_MIN_DE = 150
    const val COMMON_MIN_OTHER = 170
    /** History frequency of a word used about twice (HeliBoard: 111 after two uses, 120 after three). */
    const val HISTORY_USED = 111
    /** How far the candidate must be ahead of the typed word in the history (the values rise slowly with each use). */
    const val HISTORY_LEAD = 20
    /** Next-word score lead of the candidate over the typed word in this context (as the engine's `shouldBeAutoCorrected`). */
    const val CONTEXT_LEAD = 20

    @JvmStatic
    fun minCandidate(language: String): Int = if (language.lowercase(Locale.ROOT) == "de") COMMON_MIN_DE else COMMON_MIN_OTHER

    /**
     * [typedFrequency], [candidateFrequency]: main-dictionary frequencies (below zero unknown). [historyTyped], [historyCandidate]:
     * typing-history frequencies (below zero: never committed), [hasHistory]: a typing history is in use. [contextCandidate],
     * [contextTyped]: next-word scores in this sentence, null when the engine does not predict the word here.
     */
    @JvmStatic
    fun corrects(
        typedFrequency: Int, candidateFrequency: Int, language: String, rejected: Boolean,
        hasHistory: Boolean, historyTyped: Int, historyCandidate: Int, contextCandidate: Int?, contextTyped: Int?,
    ): Boolean {
        if (rejected) return false
        if (typedFrequency < 0 || typedFrequency > RARE_MAX) return false
        if (candidateFrequency < minCandidate(language)) return false
        if (hasHistory && historyCandidate >= HISTORY_USED) return historyTyped < 0 || historyCandidate - historyTyped >= HISTORY_LEAD
        // the owner uses the typed word, not the candidate: it stays
        if (hasHistory && historyTyped >= HISTORY_USED) return false
        return contextCandidate != null && (contextTyped == null || contextCandidate - contextTyped > CONTEXT_LEAD)
    }
}
