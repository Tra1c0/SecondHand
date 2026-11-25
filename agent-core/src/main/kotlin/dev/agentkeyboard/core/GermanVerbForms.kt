// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 101 (owner's findings "ich übermittle" -> "Übermittler", "ich marschiere" -> "marschierte", "ich faselte" ->
 * "fädelte", "ich fiedel" -> "Friedel"): whether a typed word is a regular form of a known German verb. Such a word
 * is protected from a silent replacement (like rule D-115 of AdaptKey: unknown, but regularly inflected); it is never
 * produced. Pure, no dictionary and no text is kept here; [isInfinitive] answers from the bundled verb list.
 *
 * Recognised, for a stem s of an infinitive "s+en" (machen, marschieren):
 * - s+e, s+st, s+t, s+en, s+te, s+test, s+ten, s+tet (not for stems that need an extra e, see below),
 * - for stems ending in d or t, or in m/n after another consonant (arbeiten, atmen, rechnen): s+e, s+en, s+est, s+et,
 *   s+ete, s+etest, s+eten, s+etet;
 * and for a stem s of an infinitive "s+n" with s ending in "el" or "er" (fiedeln, wandern):
 * - s alone (colloquial first person "ich fiedel", "ich wander"), s+e, s+st, s+t, s+n, s+te, s+test, s+ten, s+tet,
 * - the short first person without the e of the stem ("fiedle", "wandre").
 * Irregular verbs are not known: "gehte" counts as a regular past of "gehen" (protected, never produced).
 */
object GermanVerbForms {
    const val MIN_LETTERS = 4

    private val plainEndings = listOf("e", "st", "t", "en", "te", "test", "ten", "tet")
    private val extraEEndings = listOf("e", "en", "est", "et", "ete", "etest", "eten", "etet")
    private val elErEndings = listOf("", "e", "st", "t", "n", "te", "test", "ten", "tet")

    /** Whether [word] (lower case letters, as typed) is a regular form of an infinitive that [isInfinitive] knows. */
    @JvmStatic
    fun isRegularForm(word: String, isInfinitive: (String) -> Boolean): Boolean {
        if (word.length < MIN_LETTERS || !word.all { it.isLetter() && it.isLowerCase() }) return false
        for (ending in plainEndings) {
            val stem = stemBefore(word, ending) ?: continue
            if (needsExtraE(stem) && ending != "e" && ending != "en") continue
            if (isInfinitive(stem + "en")) return true
        }
        for (ending in extraEEndings) {
            val stem = stemBefore(word, ending) ?: continue
            if (needsExtraE(stem) && isInfinitive(stem + "en")) return true
        }
        for (ending in elErEndings) {
            val stem = stemBefore(word, ending) ?: continue
            if (stem.length >= 4 && (stem.endsWith("el") || stem.endsWith("er")) && isInfinitive(stem + "n")) return true
        }
        // "fiedle" -> "fiedel", "wandre" -> "wander"
        if (word.length >= 5 && (word.endsWith("le") || word.endsWith("re"))) {
            val stem = word.dropLast(2) + "e" + word[word.length - 2]
            if (isInfinitive(stem + "n")) return true
        }
        return false
    }

    private fun stemBefore(word: String, ending: String): String? =
        if (word.endsWith(ending) && word.length - ending.length >= 2) word.dropLast(ending.length) else null

    /** "arbeit", "red", "atm", "rechn": the forms take an extra e ("arbeitet", "atmete"). */
    private fun needsExtraE(stem: String): Boolean {
        val last = stem.last()
        if (last == 'd' || last == 't') return true
        if ((last == 'm' || last == 'n') && stem.length >= 2) {
            val before = stem[stem.length - 2]
            return before !in "aeiouäöüylrmnh"
        }
        return false
    }
}
