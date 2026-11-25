// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 101 (owner: "the i in English is still lower case"): the English pronoun typed in lower case ("i", "i'm", "i'll",
 * "i'd", "i've", also with ’) becomes "I", "I'm" … On the English layout always; on another layout only when the sentence
 * shows English: at least one previous word counts for English and none for the keyboard language ([SentenceLanguage]).
 * A first word or a word after "ich" stays ("i hob" is Bavarian). "im", "ill", "id" are never touched (real words).
 */
object EnglishI {
    private val forms = setOf("i", "i'm", "i'll", "i'd", "i've")

    /** The capitalised form of [typed], or null when [typed] is not one of the forms (exactly as typed: lower case "i" first). */
    @JvmStatic
    fun capitalized(typed: String): String? {
        if (typed.isEmpty() || typed[0] != 'i') return null
        return if (typed.replace('’', '\'') in forms) "I" + typed.substring(1) else null
    }

    /** [keyboardLanguage]: language code of the keyboard; [forEnglish], [forKeyboard]: previous words that count for each. */
    @JvmStatic
    fun applies(keyboardLanguage: String, forEnglish: Int, forKeyboard: Int): Boolean =
        keyboardLanguage == "en" || (forEnglish > 0 && forKeyboard == 0)
}
