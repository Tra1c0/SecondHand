// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.memory

import java.util.Locale
import kotlin.math.pow

/**
 * Stage 46 "Gedächtnis": pure rules of the phrase memory, no Android, no network, nothing logged.
 *
 * The memory keeps word sequences of 2 to [PhraseBook.MAX_WORDS] words from the owner's own
 * committed words ([PhraseBook.learn]), weighted by frequency and recency ([PhraseBook.HALF_LIFE_DAYS]),
 * and predicts from the words before the cursor ([PhraseBook.predict]): the next word, and when it
 * is sure, the whole remaining sequence. It never changes text itself: the app only appends a
 * prediction after the cursor on a tap.
 */
object PhraseText {
    /** Longest token that counts as a word. */
    const val MAX_TOKEN = 32
    /** Characters that end a sentence part: nothing is learned or predicted across them. */
    private val SENTENCE_END = charArrayOf('.', '!', '?', '…', '\n', ';', '¡', '¿', '(', ')', '"', '“', '„', '«', '»')
    /** Punctuation a token may end with; it stays in the stored text but not in the key. */
    private val TRAILING = charArrayOf(',', ':')

    /** The tokens of the sentence part that ends at the end of [before] (whitespace separated, in order). */
    fun currentSentence(before: CharSequence): List<String> {
        var start = before.length
        while (start > 0 && before[start - 1] !in SENTENCE_END) start--
        return before.substring(start).split(' ', '\t', ' ').filter { it.isNotEmpty() }
    }

    /** "Grüße," → "grüße"; null if [token] is no word (digits, @, URL parts, symbols, too long). */
    fun key(token: String): String? {
        val trimmed = token.trimEnd(*TRAILING)
        if (trimmed.isEmpty() || trimmed.length > MAX_TOKEN) return null
        if (!trimmed.first().isLetter() || !trimmed.last().isLetter()) return null
        if (trimmed.any { !(it.isLetter() || it == '\'' || it == '’' || it == '-') }) return null
        return trimmed.lowercase(Locale.ROOT)
    }

    /** The keys of [tokens] while all of them are words; stops (exclusive) at the last non-word from the end. */
    fun trailingWords(tokens: List<String>): List<String> {
        val out = ArrayList<String>()
        for (token in tokens.asReversed()) out.add(key(token) ?: break)
        out.reverse()
        return out
    }
}

/** One remembered sequence. [key] is its words in lower case without trailing commas, [text] as last typed. */
class Phrase(val key: String, val text: String, val weight: Double, val day: Long, val pinned: Boolean) {
    val words: Int get() = key.count { it == ' ' } + 1
    override fun toString() = "Phrase($words words, weight=${"%.2f".format(Locale.ROOT, weight)}, pinned=$pinned)" // never the text
}

/**
 * What the memory predicts after the words before the cursor. [next] is the next word in its typed
 * form with [nextShare] (its share of the times the context words were followed by anything, or
 * ended), [rest] the whole remaining sequence ([restWords] words, at least 2) with [restShare].
 * [rest] is null below the confidence thresholds of [PhraseBook].
 */
class PhrasePrediction(
    val contextWords: Int,
    val next: String?,
    val nextShare: Double,
    val rest: String?,
    val restWords: Int,
    val restShare: Double,
    /** The key of the whole remembered sequence behind [rest] (for reinforcing it on a tap), or null. */
    val restKey: String?,
) {
    override fun toString() = "PhrasePrediction(context=$contextWords, next=${next != null}, rest=$restWords)"
}

/**
 * The bounded, decaying phrase store. Not thread-safe: the app guards it with a lock.
 *
 * Learning: for a run of words the owner committed one after another in the same sentence part,
 * each commit adds the sequences that end with the new word. A 2-word sequence is always counted;
 * a longer one only once its first and its last part (one word shorter) were each seen at least
 * [GROW_AT] times, so random sentences add few entries and only repeated phrases grow long. A new
 * longer sequence starts at 1, so the sure rest of a phrase grows by about a word every few uses
 * (measured in the tests: starting it from the weight of its parts made random word runs look sure).
 * Reinforcement adds 1 to the decayed weight. At most [MAX_ENTRIES]; beyond that the weakest
 * (oldest among equals) that are not pinned go first. Pinned phrases neither decay nor go.
 */
class PhraseBook private constructor(private val entries: LinkedHashMap<String, Entry>) {
    private class Entry(var text: String, var weight: Double, var day: Long, var pinned: Boolean)

    constructor() : this(LinkedHashMap())

    /**
     * Review N1: the keys by their first two words, in insertion order. Every key that continues a
     * context shares the context's first two words, so [predictKeys] reads one small bucket instead
     * of all entries on every strip update.
     */
    private val byHead = HashMap<String, LinkedHashSet<String>>().also { index ->
        entries.keys.forEach { index.getOrPut(head(it)) { LinkedHashSet() }.add(it) }
    }

    private fun put(key: String, entry: Entry) {
        entries[key] = entry
        byHead.getOrPut(head(key)) { LinkedHashSet() }.add(key)
    }

    private fun drop(key: String) {
        if (entries.remove(key) == null) return
        val head = head(key)
        byHead[head]?.let { if (it.remove(key) && it.isEmpty()) byHead.remove(head) }
    }

    /** A copy for writing it out on another thread. */
    fun copy(): PhraseBook {
        val copy = PhraseBook()
        for ((key, e) in entries) copy.put(key, Entry(e.text, e.weight, e.day, e.pinned))
        return copy
    }

    val size: Int get() = entries.size

    /** The weight of [key] on [today], decayed unless pinned; 0 when unknown. */
    fun weight(key: String, today: Long): Double = entries[key]?.let { effective(it, today) } ?: 0.0

    private fun effective(entry: Entry, today: Long): Double {
        if (entry.pinned) return entry.weight
        val age = (today - entry.day).coerceAtLeast(0)
        return entry.weight * 0.5.pow(age / HALF_LIFE_DAYS)
    }

    /**
     * The owner committed the last word of [run] (surface tokens of one sentence part, every one a
     * word, oldest first). Adds the sequences of 2..[MAX_WORDS] words that end with it.
     */
    fun learn(run: List<String>, today: Long) {
        val tokens = run.takeLast(MAX_WORDS)
        val keys = tokens.map { PhraseText.key(it) ?: return }
        if (keys.size < 2) return
        for (n in 2..keys.size) {
            val from = keys.size - n
            val key = keys.subList(from, keys.size).joinToString(" ")
            val text = tokens.subList(from, tokens.size).joinToString(" ").take(MAX_TEXT)
            if (n > 2 && !entries.containsKey(key)) {
                val head = weight(keys.subList(from, keys.size - 1).joinToString(" "), today)
                val tail = weight(keys.subList(from + 1, keys.size).joinToString(" "), today)
                // a new longer sequence starts at 1: counting it from its parts would also make random word runs look sure
                if (head < GROW_AT || tail < GROW_AT) break
            }
            reinforce(key, text, today, 1.0)
        }
        evictIfFull(today)
    }

    /** Adds [amount] to [key] (e.g. a tapped prediction); unknown keys are created with [text]. */
    fun reinforce(key: String, text: String, today: Long, amount: Double) {
        val entry = entries[key]
        if (entry == null) {
            put(key, Entry(text, amount, today, false))
            return
        }
        entry.weight = (effective(entry, today) + amount).coerceAtMost(MAX_WEIGHT)
        if (!entry.pinned) entry.day = today
        entry.text = text
    }

    private fun evictIfFull(today: Long) {
        if (entries.size <= MAX_ENTRIES) return
        val target = MAX_ENTRIES * 95 / 100
        val removable = entries.entries.filter { !it.value.pinned }
            .sortedWith(compareBy({ effective(it.value, today) }, { it.value.day }))
        val drop = (entries.size - target).coerceAtMost(removable.size)
        for (i in 0 until drop) drop(removable[i].key)
    }

    /**
     * Prediction from [before], the text before the cursor ending with a space. The longest context
     * of 2..[MAX_WORDS]-1 words at the end of the sentence part that the memory saw at least
     * [MIN_CONTEXT] times decides; null without such a context or without any continuation.
     */
    fun predict(before: CharSequence, today: Long, restMinShare: Double = REST_MIN_SHARE): PhrasePrediction? {
        if (before.isEmpty() || !before.last().isWhitespace()) return null
        val context = PhraseText.trailingWords(PhraseText.currentSentence(before)).takeLast(MAX_WORDS - 1)
        return predictKeys(context, today, restMinShare)
    }

    /** As [predict], for the context words in key form (e.g. the previous words the engine knows). */
    fun predictKeys(context: List<String>, today: Long, restMinShare: Double = REST_MIN_SHARE): PhrasePrediction? {
        for (m in context.size downTo 2) {
            val contextKey = context.takeLast(m).joinToString(" ")
            val total = weight(contextKey, today)
            if (total < MIN_CONTEXT) continue
            val prefix = "$contextKey "
            var next: String? = null
            var nextWeight = 0.0
            var rest: Entry? = null
            var restKey: String? = null
            var restWeight = 0.0
            var restWords = 0
            for (key in byHead[head(contextKey)] ?: continue) {
                if (!key.startsWith(prefix)) continue
                val entry = entries.getValue(key)
                val w = effective(entry, today)
                val extra = key.count { it == ' ' } + 1 - m
                if (extra == 1 && w > nextWeight) {
                    nextWeight = w
                    next = entry.text.substringAfterLast(' ')
                }
                // the longest sure sequence; among equally long ones the stronger
                if (extra >= 2 && w >= REST_MIN_SUPPORT && w / total >= restMinShare &&
                    (extra > restWords || (extra == restWords && w > restWeight))) {
                    rest = entry; restKey = key; restWeight = w; restWords = extra
                }
            }
            if (next == null) continue
            val nextShare = (nextWeight / total).coerceAtMost(1.0)
            val shownNext = next.takeIf { nextWeight >= NEXT_MIN_SUPPORT && nextShare >= NEXT_MIN_SHARE }
            val restText = rest?.text?.split(' ')?.takeLast(restWords)?.joinToString(" ")
            return PhrasePrediction(m, shownNext, nextShare, restText, if (restText == null) 0 else restWords,
                if (restText == null) 0.0 else (restWeight / total).coerceAtMost(1.0), restKey)
        }
        return null
    }

    /** All phrases of at least [minWords] words, strongest first (pinned first). */
    fun phrases(today: Long, minWords: Int = 2): List<Phrase> =
        entries.entries.map { Phrase(it.key, it.value.text, effective(it.value, today), it.value.day, it.value.pinned) }
            .filter { it.words >= minWords }
            .sortedWith(compareByDescending<Phrase> { it.pinned }.thenByDescending { it.weight })

    /**
     * Forgets [key] and every longer sequence that contains it, so it is never offered again (until
     * the owner types it again). Returns how many entries went.
     */
    fun delete(key: String): Int {
        val padded = " $key "
        val gone = entries.keys.filter { " $it ".contains(padded) }
        gone.forEach { drop(it) }
        return gone.size
    }

    /** Pins or unpins [key]; a pinned phrase keeps its current weight. False if unknown or too many pins. */
    fun pin(key: String, pinned: Boolean, today: Long): Boolean {
        val entry = entries[key] ?: return false
        if (pinned == entry.pinned) return true
        if (pinned && entries.values.count { it.pinned } >= MAX_PINNED) return false
        entry.weight = effective(entry, today)
        entry.day = today
        entry.pinned = pinned
        return true
    }

    fun clear() { entries.clear(); byHead.clear() }

    /** "v1" and one line per entry: weight;day;pinned;text. Readable on purpose: the owner's data. */
    fun encode(): String = buildString {
        append(VERSION)
        for (entry in entries.values) {
            append('\n').append("%.3f".format(Locale.ROOT, entry.weight)).append(';').append(entry.day).append(';')
                .append(if (entry.pinned) '1' else '0').append(';').append(entry.text)
        }
    }

    companion object {
        const val VERSION = "v1"
        const val MAX_WORDS = 8
        const val MAX_ENTRIES = 2000
        const val MAX_PINNED = 200
        const val MAX_TEXT = 8 * (PhraseText.MAX_TOKEN + 2)
        const val MAX_WEIGHT = 1000.0
        /** Weight halves every this many days without use. */
        const val HALF_LIFE_DAYS = 30.0
        /** A sequence grows by a word once both shorter parts were seen this often. */
        const val GROW_AT = 2.0
        /** A context counts once it was seen this often. */
        const val MIN_CONTEXT = 2.0
        /** The next word: seen at least twice after the context and at least 40 % of the time (measured, see the tests). */
        const val NEXT_MIN_SUPPORT = 2.0
        const val NEXT_MIN_SHARE = 0.4
        /** The whole rest: seen at least 3 times and at least 70 % of the time after the context (measured). */
        const val REST_MIN_SUPPORT = 3.0
        const val REST_MIN_SHARE = 0.7

        /** The first two words of [key] (the whole key for two words). */
        private fun head(key: String): String {
            val first = key.indexOf(' ')
            if (first < 0) return key
            val second = key.indexOf(' ', first + 1)
            return if (second < 0) key else key.substring(0, second)
        }

        /** Reads [encode]d text; unknown or broken lines are skipped, never an exception. */
        fun parse(text: String?, today: Long): PhraseBook {
            val book = PhraseBook()
            if (text == null) return book
            val lines = text.lineSequence().iterator()
            if (!lines.hasNext() || lines.next() != VERSION) return book
            for (line in lines) {
                val parts = line.split(';', limit = 4)
                if (parts.size != 4) continue
                val weight = parts[0].toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_WEIGHT } ?: continue
                // a clock that went back keeps the entry (as of today)
                val day = parts[1].toLongOrNull()?.takeIf { it >= 0 }?.coerceAtMost(today) ?: continue
                val tokens = parts[3].split(' ').filter { it.isNotEmpty() }
                if (tokens.size < 2 || tokens.size > MAX_WORDS) continue
                val keys = tokens.map { PhraseText.key(it) }
                if (keys.any { it == null }) continue
                book.put(keys.joinToString(" "), Entry(tokens.joinToString(" "), weight, day, parts[2] == "1"))
                if (book.entries.size >= MAX_ENTRIES) break
            }
            return book
        }
    }
}
