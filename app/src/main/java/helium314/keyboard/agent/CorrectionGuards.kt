// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import java.text.Normalizer
import java.util.Locale

/**
 * Stage 91: two vetoes on the automatic correction, rebuilt after the rules of AdaptKey (GPL-3.0, m-froehlich/adaptkey:
 * D-404 acronyms, D-354 German prefixes). They only stop the silent replacement: the candidate stays in the strip as a suggestion.
 */
object CorrectionGuards {
    /** Longest first, so that "ver" is found before "v…" prefixes of other entries. */
    private val GERMAN_PREFIXES = listOf("miss", "emp", "ent", "ver", "zer", "be", "er", "ge")

    /** A word typed in capitals only, 2 to 5 letters (ETF, AVD, ÖPNV): written on purpose, never replaced. */
    fun isAcronym(typed: String): Boolean {
        val letters = typed.filter { it.isLetter() }
        return letters.length in 2..5 && letters.length == typed.length && letters.all { it.isUpperCase() }
    }

    /** The German prefix the word starts with, or null. */
    private fun prefixOf(word: String): String? {
        val lower = word.lowercase(Locale.GERMAN)
        return GERMAN_PREFIXES.firstOrNull { lower.length > it.length + 2 && lower.startsWith(it) }
    }

    /** The correction would exchange the German prefix of [typed] ("berstehen" -> "verstehen"): a change of meaning, offer it only. */
    fun shiftsGermanPrefix(typed: String, candidate: String, candidateLocale: Locale?): Boolean {
        if (candidateLocale?.language != "de") return false
        val typedPrefix = prefixOf(typed) ?: return false
        return !candidate.lowercase(Locale.GERMAN).startsWith(typedPrefix)
    }

    /** Whether the silent replacement of [typed] by [candidate] is vetoed. */
    @JvmStatic
    fun blocks(typed: String, candidate: String?, candidateLocale: Locale?): Boolean =
        candidate != null && candidate != typed && (isAcronym(typed) || shiftsGermanPrefix(typed, candidate, candidateLocale))

    /**
     * Stage 101 (owner: "alr" -> "ALR", "aif" -> "AIF"): a word typed with lower case letters becomes a word of capitals only
     * (at least two letters). An abbreviation of a dictionary is no evidence that the owner meant it.
     */
    @JvmStatic
    fun replacesWithCapitals(typed: String, candidate: String): Boolean {
        val letters = candidate.filter { it.isLetter() }
        return letters.length >= 2 && letters.all { it.isUpperCase() } && typed.any { it.isLowerCase() }
    }

    /**
     * Stage 101 (owner: "lame" -> "Lamé"): a German correction brings in a letter with an accent German does not have (anything
     * but the umlaut dots) that the typed word does not contain.
     */
    @JvmStatic
    fun addsForeignAccent(typed: String, candidate: String, candidateLocale: Locale?): Boolean {
        if (candidateLocale?.language != "de") return false
        val typedAccents = foreignAccents(typed)
        return foreignAccents(candidate).any { it !in typedAccents }
    }

    private fun foreignAccents(word: String): Set<Char> = word.lowercase(Locale.ROOT).filter { ch ->
        val decomposed = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
        decomposed.length > 1 && decomposed.drop(1).any { it != '\u0308' }
    }.toSet()

    /**
     * Stage 101: the silent replacement of [typed] by [candidate] is vetoed because of the target ([replacesWithCapitals],
     * [addsForeignAccent]). [demanded]: a personal shortcut or a whitelist entry of the dictionary, which may set anything.
     */
    @JvmStatic
    fun blocksTarget(typed: String, candidate: String?, candidateLocale: Locale?, demanded: Boolean): Boolean =
        candidate != null && candidate != typed && !demanded &&
            (replacesWithCapitals(typed, candidate) || addsForeignAccent(typed, candidate, candidateLocale))
}
