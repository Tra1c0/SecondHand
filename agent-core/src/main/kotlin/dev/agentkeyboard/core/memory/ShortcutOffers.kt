// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.memory

import java.util.Locale

/**
 * Stage 46: abbreviations the owner expands by hand. When the owner types a short form X, deletes it
 * and types its expansion Y in the same place ([ReplacementTracker]) at least [ShortcutBook.OFFER_AT]
 * times, the app offers once (a quiet chip) to create the shortcut X → Y in the personal dictionary.
 * Nothing is created without a tap.
 */
object ShortcutRules {
    const val MIN_SHORT = 2
    const val MAX_SHORT = 6
    const val MAX_EXPANSION_WORDS = 5
    const val MAX_EXPANSION = 80

    /**
     * Whether [expansion] plausibly expands [short]: X is 2–6 letters, Y has 1–5 words and at least
     * two characters more; one word must contain the letters of X in order starting with its first
     * letter ("vlt" → "vielleicht"), several words must have X as initials ("mfg" → "mit freundlichen
     * Grüßen"). Typo fixes ("nciht" → "nicht") are no expansions.
     */
    fun plausible(short: String, expansion: String): Boolean {
        val x = short.lowercase(Locale.ROOT)
        if (x.length !in MIN_SHORT..MAX_SHORT || !x.all { it.isLetter() }) return false
        val y = expansion.trim()
        if (y.length > MAX_EXPANSION || y.length < x.length + 2) return false
        val words = y.lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_EXPANSION_WORDS) return false
        if (words.any { w -> w.trimEnd(',', '.', '!', '?').let { it.isEmpty() || !it.all { c -> c.isLetter() || c == '-' || c == '\'' } } }) return false
        if (words.size == 1) {
            val w = words[0]
            if (w[0] != x[0]) return false
            var i = 0
            for (c in w) if (i < x.length && c == x[i]) i++
            return i == x.length
        }
        return words.map { it[0] }.joinToString("") == x
    }
}

/**
 * Follows the owner's committed words in one field and reports "X replaced by Y in place": a word
 * committed where an earlier word started, after that word was deleted. Positions are the editor's
 * cursor positions. Memory only, at most [MAX_RECENT] commits; [reset] on a new field.
 */
class ReplacementTracker {
    private class Commit(val word: String, val start: Int)
    private class Pending(val short: String, val start: Int)

    private val recent = ArrayList<Commit>()
    private var pending: Pending? = null

    fun reset() { recent.clear(); pending = null }

    /**
     * The owner committed [word] ending at [cursor]; [before] is the text before the cursor (ending
     * with [word]). Returns (X, Y) when this commit completes an expansion of X, else null.
     */
    fun onCommit(word: String, cursor: Int, before: CharSequence): Pair<String, String>? {
        val start = cursor - word.length
        if (start < 0 || !before.endsWith(word)) { reset(); return null }
        var found: Pair<String, String>? = null
        val waiting = pending
        if (waiting != null) {
            val offset = waiting.start - (cursor - before.length)
            if (start < waiting.start || offset < 0 || offset > before.length) pending = null
            else {
                val expansion = before.substring(offset).trim()
                val words = expansion.split(' ').count { it.isNotEmpty() }
                when {
                    ShortcutRules.plausible(waiting.short, expansion) -> { found = waiting.short to expansion; pending = null }
                    words >= ShortcutRules.MAX_EXPANSION_WORDS -> pending = null
                }
            }
        }
        if (found == null && waiting == null) {
            val replaced = recent.indexOfLast { it.start == start }
            if (replaced >= 0) {
                val old = recent.removeAt(replaced)
                if (!old.word.equals(word, ignoreCase = true) && old.word.length in ShortcutRules.MIN_SHORT..ShortcutRules.MAX_SHORT) {
                    if (ShortcutRules.plausible(old.word, word)) found = old.word to word
                    else pending = Pending(old.word, start)
                }
            }
        }
        // later commits sit behind this one: their old starts would no longer be where they were
        recent.removeAll { it.start >= start }
        recent.add(Commit(word, start))
        while (recent.size > MAX_RECENT) recent.removeAt(0)
        return found
    }

    companion object { const val MAX_RECENT = 8 }
}

/** The counted expansions and what became of each offer. Not thread-safe; the app guards it with a lock. */
class ShortcutBook private constructor(private val pairs: LinkedHashMap<String, Entry>) {
    enum class State { COUNTING, READY, SHOWN, DECLINED, CREATED }

    class Offer(val short: String, val expansion: String, val count: Int, val state: State) {
        override fun toString() = "Offer(${short.length} → ${expansion.length} chars, $count, $state)" // never the texts
    }

    private class Entry(val short: String, var expansion: String, var count: Int, var state: State)

    constructor() : this(LinkedHashMap())

    private fun key(short: String, expansion: String) = short.lowercase(Locale.ROOT) + '\u0000' + expansion.lowercase(Locale.ROOT)

    /** One more time the owner expanded [short] to [expansion] by hand; true when the offer is now due. */
    fun note(short: String, expansion: String): Boolean {
        if (!ShortcutRules.plausible(short, expansion)) return false
        val key = key(short, expansion)
        val entry = pairs.remove(key) ?: Entry(short.lowercase(Locale.ROOT), expansion, 0, State.COUNTING)
        entry.count = (entry.count + 1).coerceAtMost(9999)
        entry.expansion = expansion
        val due = entry.state == State.COUNTING && entry.count >= OFFER_AT
        if (due) entry.state = State.READY
        pairs[key] = entry
        while (pairs.size > MAX_PAIRS) pairs.remove(pairs.keys.first())
        return due
    }

    /** The offer to show in the strip now, or null: due and never shown before. */
    fun due(): Offer? = pairs.values.lastOrNull { it.state == State.READY }?.let(::offer)

    /** All pairs that reached an offer (shown or not), most recent first, for the settings page. */
    fun offers(): List<Offer> = pairs.values.filter { it.state != State.COUNTING }.reversed().map(::offer)

    private fun offer(entry: Entry) = Offer(entry.short, entry.expansion, entry.count, entry.state)

    fun mark(short: String, expansion: String, state: State) {
        pairs[key(short, expansion)]?.state = state
    }

    fun remove(short: String, expansion: String) { pairs.remove(key(short, expansion)) }

    fun clear() = pairs.clear()

    /** "v1", then "count;STATE;short;expansion" per pair. */
    fun encode(): String = buildString {
        append(VERSION)
        pairs.values.forEach { append('\n').append(it.count).append(';').append(it.state.name).append(';').append(it.short).append(';').append(it.expansion) }
    }

    companion object {
        const val VERSION = "v1"
        const val OFFER_AT = 2
        const val MAX_PAIRS = 200

        fun parse(text: String?): ShortcutBook {
            val book = ShortcutBook()
            if (text == null) return book
            val lines = text.lineSequence().iterator()
            if (!lines.hasNext() || lines.next() != VERSION) return book
            for (line in lines) {
                val parts = line.split(';', limit = 4)
                if (parts.size != 4) continue
                val count = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: continue
                val state = runCatching { State.valueOf(parts[1]) }.getOrNull() ?: continue
                if (!ShortcutRules.plausible(parts[2], parts[3]) || book.pairs.size >= MAX_PAIRS) continue
                book.pairs[book.key(parts[2], parts[3])] = Entry(parts[2].lowercase(Locale.ROOT), parts[3], count, state)
            }
            return book
        }
    }
}
