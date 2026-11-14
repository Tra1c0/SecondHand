// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.memory

import java.util.Locale

/**
 * Stage 46: slang and deliberate misspellings become own words. A use is a word the owner committed
 * as typed although no main dictionary knows it, or an automatic correction the owner undid. After
 * [PROMOTE_AT] uses the word is the owner's: the app no longer corrects it automatically and offers
 * it while its start is typed. Words only (letters, `'` and `-`), 2 to [MAX_WORD] characters.
 * Not thread-safe; the app guards it with a lock.
 */
class OwnWordBook private constructor(
    private val uses: LinkedHashMap<String, Use>,
    private val own: LinkedHashMap<String, String>,
) {
    private class Use(var form: String, var count: Int, var day: Long)

    constructor() : this(LinkedHashMap(), LinkedHashMap())

    /** Own words in their typed form, most recently added last. */
    val words: List<String> get() = own.values.toList()

    /** Uses counted so far for words that are not own yet: (form, count), most recent last. */
    val candidates: List<Pair<String, Int>> get() = uses.values.map { it.form to it.count }

    fun isOwn(word: String): Boolean = own.containsKey(norm(word))

    /**
     * One use of [word] on [today]. Returns true if the word just became an own word. Uses older
     * than [FORGET_DAYS] start again.
     */
    fun noteUse(word: String, today: Long): Boolean {
        if (!isCandidate(word)) return false
        val key = norm(word)
        if (own.containsKey(key)) return false
        val use = uses.remove(key)?.takeIf { today - it.day <= FORGET_DAYS } ?: Use(word, 0, today)
        use.count++
        use.day = today
        if (word.drop(1).any { it.isUpperCase() } || !word.first().isUpperCase()) use.form = word
        if (use.count >= PROMOTE_AT) {
            own[key] = use.form
            while (own.size > MAX_OWN) own.remove(own.keys.first())
            return true
        }
        uses[key] = use
        while (uses.size > MAX_CANDIDATES) uses.remove(uses.keys.first())
        return false
    }

    /** Own words that start with [typed] (at least [MIN_PREFIX] characters) and are longer, in their form. */
    fun completions(typed: String, max: Int = 1): List<String> {
        if (typed.length < MIN_PREFIX) return emptyList()
        val start = norm(typed)
        return own.entries.filter { it.key.length > start.length && it.key.startsWith(start) }.map { it.value }.take(max)
    }

    /** Removes [word] from the own words and from the counted uses; true if it was known. */
    fun remove(word: String): Boolean {
        val key = norm(word)
        return (own.remove(key) != null) or (uses.remove(key) != null)
    }

    fun clear() { own.clear(); uses.clear() }

    /** "v1", then "o;form" per own word and "u;count;day;form" per counted word. */
    fun encode(): String = buildString {
        append(VERSION)
        own.values.forEach { append("\no;").append(it) }
        uses.values.forEach { append("\nu;").append(it.count).append(';').append(it.day).append(';').append(it.form) }
    }

    companion object {
        const val VERSION = "v1"
        const val PROMOTE_AT = 3
        const val MAX_WORD = 32
        const val MIN_PREFIX = 2
        const val MAX_OWN = 500
        const val MAX_CANDIDATES = 500
        const val FORGET_DAYS = 90L

        private fun norm(word: String) = word.lowercase(Locale.ROOT)

        /** A word that can become own: letters (any script) with inner `'` or `-`, no digits. */
        fun isCandidate(word: String): Boolean =
            word.length in 2..MAX_WORD && word.first().isLetter() && word.last().isLetter() &&
                word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }

        /** Reads [encode]d text; broken lines are skipped, never an exception. */
        fun parse(text: String?, today: Long): OwnWordBook {
            val book = OwnWordBook()
            if (text == null) return book
            val lines = text.lineSequence().iterator()
            if (!lines.hasNext() || lines.next() != VERSION) return book
            for (line in lines) {
                if (line.startsWith("o;")) {
                    val form = line.substring(2)
                    if (isCandidate(form) && book.own.size < MAX_OWN) book.own[norm(form)] = form
                } else if (line.startsWith("u;")) {
                    val parts = line.split(';', limit = 4)
                    if (parts.size != 4) continue
                    val count = parts[1].toIntOrNull()?.takeIf { it in 1 until PROMOTE_AT } ?: continue
                    val day = parts[2].toLongOrNull()?.takeIf { it >= 0 }?.coerceAtMost(today) ?: continue
                    if (!isCandidate(parts[3]) || book.uses.size >= MAX_CANDIDATES) continue
                    book.uses[norm(parts[3])] = Use(parts[3], count, day)
                }
            }
            return book
        }
    }
}
