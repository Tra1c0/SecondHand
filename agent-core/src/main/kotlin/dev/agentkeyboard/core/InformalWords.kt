// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.text.Normalizer
import java.util.Locale

/**
 * Stage 71: the built-in list of informal words (slang, chat abbreviations, Denglish forms, gaming,
 * tech and music terms) that the automatic correction leaves as typed. The list is data
 * (`app/src/main/assets/agent/informal_words.txt`, made by `tools/build_informal_words.py` from the
 * SecondHand DE/EN data package, CC0-1.0); this object only reads and compares. It never suggests,
 * learns or changes anything: a word on the list stays as typed, the engine's suggestion stays a
 * suggestion. Besides the list, an expressive spelling stays as typed (package rule 9): a letter
 * stretched at the end of the word ("neee", "jaaaa", "heyyyy") or a laugh ("hahaha", "hehehe").
 * Pure functions, no I/O.
 */
object InformalWords {
    /** The list is written for German and English typing; for other languages it protects nothing. */
    private val LANGUAGES = setOf("de", "en")

    /** Longest word the list holds; a longer typed word cannot be on it. */
    const val MAX_LENGTH = 40

    /** The form words are stored and compared in: NFC, lower case, a straight apostrophe. */
    @JvmStatic
    fun normalize(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace('’', '\'')

    /** The words of the list file: one per line, blank lines and `#` comments ignored. */
    @JvmStatic
    fun parse(text: String): Set<String> = text.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith('#') }.toHashSet()

    /** Whether the list applies to typing in any of [languages] (language codes such as "de" or "en"). */
    @JvmStatic
    fun appliesTo(languages: Collection<String?>): Boolean =
        languages.any { it != null && it.lowercase(Locale.ROOT) in LANGUAGES }

    private val LAUGHTER = Regex("^(\\p{L}{2})\\1{2,}\\p{L}?$", RegexOption.IGNORE_CASE)

    private const val VOWELS = "aeiouyäöüáàâéèêíìîóòôúùû"

    /**
     * An expressive spelling: letters only, a final vowel three times or more in a row ("neee", "jaaaa"), a
     * final consonant four times or more ("yesssss"), or a two-letter syllable three times or more ("hahaha").
     * Emphasis is not a spelling mistake. A consonant three times ("addresss") stays a slip of the finger:
     * words that already end in a doubled consonant get a third one by accident.
     */
    @JvmStatic
    fun isExpressive(typed: String): Boolean {
        if (typed.length !in 3..MAX_LENGTH || !typed.all { it.isLetter() }) return false
        val last = typed.last().lowercaseChar()
        var run = 0
        for (i in typed.indices.reversed()) if (typed[i].lowercaseChar() == last) run++ else break
        return run >= (if (last in VOWELS) 3 else 4) || LAUGHTER.matches(typed)
    }

    /** Whether [typed] stays as typed: it is on the [words] list or expressive, and this applies to [languages]. */
    @JvmStatic
    fun protects(words: Set<String>, typed: String, languages: Collection<String?>): Boolean =
        typed.length in 1..MAX_LENGTH && appliesTo(languages) && (normalize(typed) in words || isExpressive(typed))
}
