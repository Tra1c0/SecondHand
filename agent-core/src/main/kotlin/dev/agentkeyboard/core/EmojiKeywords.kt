// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.io.InputStream
import java.text.Normalizer
import java.util.zip.GZIPInputStream

/**
 * Tolerant emoji lookup for the suggestion strip (stage 35). Pure and offline: the keywords come
 * from the CLDR table in the app's assets (`emoji_keywords.bin`, written by
 * `tools/emoji_keywords.py`), the synonyms from [EmojiSynonyms].
 *
 * A typed word is tried in tiers; the first tier with a hit decides:
 * 1. exact keyword (case, umlauts and accents folded: "KÄSE", "Kaese" and "kase" are the same),
 * 2. synonym,
 * 3. word form (a light suffix stemmer shared by German, English and Spanish: "Katzen" = "Katze"),
 * 4. word start (at least [MIN_FUZZY] letters: "Katz" finds "Katze"),
 * 5. one typo (at least [MIN_FUZZY] letters, same first letter, one letter wrong, missing, extra or
 *    two neighbours swapped: "Kaze", "Hetz").
 * Within a tier the languages are asked in the given order (main language first, then the others,
 * so German and English mix). Within a language an emoji whose name is the keyword comes first,
 * then emojis with fewer keywords (more specific), then palette order.
 */
class EmojiKeywords private constructor(private val languages: Map<String, Language>) {

    val languageTags: Set<String> get() = languages.keys

    /** Every emoji some keyword leads to, in any language. */
    val emojis: Set<String> by lazy {
        languages.values.flatMapTo(HashSet()) { language -> language.exact.values.flatten() }
    }

    /**
     * Up to [limit] emojis for [word], best first; empty if nothing fits. [isWord] says the typed
     * word is a correct dictionary word: then word starts and typos are not tried ("kann" is not a
     * typed start of "Kanne", "habe" is not a typo of "Hase"), and only the first language of
     * [languageOrder] is asked, which the caller sets to the language of the dictionary that knows
     * the word (German "Tag" is no English price tag, English "love" still is ❤️; synonyms always count).
     */
    fun suggest(word: String, languageOrder: List<String>, limit: Int = 2, isWord: Boolean = false): List<String> {
        if (limit <= 0) return emptyList()
        val query = fold(word)
        if (query.length < MIN_EXACT || query.all { it.isDigit() } || word.trim().lowercase() in STOPWORDS) return emptyList()
        val fuzzy = !isWord && query.length >= MIN_FUZZY
        val langs = if (isWord) listOfNotNull(languageOrder.firstOrNull()?.let { languages[it] })
            else languageOrder.distinct().mapNotNull { languages[it] }
        // "Gesicht" or "Tier" fit hundreds of emojis: no guess, and no fallback to weaker tiers
        if (langs.any { query in it.generic }) return emptyList()
        val tiers = listOf<() -> List<String>>(
            { if (langs.isEmpty()) emptyList() else collect(limit) { out -> langs.forEach { out(it.exact[query]) } } },
            { EmojiSynonyms.lookup(query).take(limit) },
            { stemTier(query, langs, limit) },
            { if (fuzzy) collect(limit) { out -> langs.forEach { it.prefix(query, out) } } else emptyList() },
            { if (fuzzy) collect(limit) { out -> langs.forEach { it.typo(query, out) } } else emptyList() },
        )
        for (tier in tiers) {
            val hits = tier()
            if (hits.isNotEmpty()) return hits
        }
        return emptyList()
    }

    private fun stemTier(query: String, langs: List<Language>, limit: Int): List<String> {
        val stem = stem(query)
        if (stem.length < MIN_STEM) return emptyList()
        val synonyms = EmojiSynonyms.lookupStem(stem)
        return collect(limit) { out ->
            // the stem can itself be a keyword: "mäuse" -> "maus" (whose own stem is "mau")
            langs.forEach { if (stem in it.strong) out(it.exact[stem]); out(it.stems[stem]) }
            out(EmojiSynonyms.lookup(stem))
            out(synonyms)
        }
    }

    private class Language(
        val exact: Map<String, List<String>>,
        /** Keys for more than [MAX_GENERIC] emojis where none is named by the key. */
        val generic: Set<String>,
        /** Keys that are a whole keyword or name, not only a word inside a longer keyword. */
        val strong: Set<String>,
        /** Stems of [strong] keys. */
        val stems: Map<String, List<String>>,
        /** [strong] keys, sorted, for word starts and typos. */
        val sorted: Array<String>,
    ) {
        fun prefix(query: String, out: (List<String>?) -> Unit) {
            var index = lowerBound(sorted, query)
            val matches = ArrayList<String>()
            while (index < sorted.size && sorted[index].startsWith(query) && matches.size < MAX_SCAN) {
                if (sorted[index].length > query.length) matches += sorted[index]
                index++
            }
            matches.sortedWith(compareBy({ it.length }, { it })).forEach { out(exact[it]) }
        }

        fun typo(query: String, out: (List<String>?) -> Unit) {
            val first = query[0]
            var index = lowerBound(sorted, first.toString())
            val matches = ArrayList<String>()
            while (index < sorted.size && sorted[index][0] == first) {
                val key = sorted[index]
                if (key.length >= query.length - 1 && key.length <= query.length + 1 && withinOneEdit(query, key)) matches += key
                index++
            }
            // a missing letter is the most common slip, then a wrong or swapped one, then an extra one
            matches.sortedWith(compareBy({ query.length - it.length + 1 }, { it })).forEach { out(exact[it]) }
        }
    }

    companion object {
        const val MIN_EXACT = 2
        const val MIN_STEM = 3
        const val MIN_FUZZY = 4
        private const val MIN_TOKEN = 5
        private const val MAX_SCAN = 200
        private const val MAX_GENERIC = 40

        /**
         * Emojis people use often, roughly most used first. Hand-made, loosely following Unicode's
         * emoji frequency overview, not a copy of it. They win over rarer emojis for the same keyword
         * ("Herz" -> ❤️, not the card suit ♥️).
         */
        val POPULAR = listOf(
            "😂", "❤️", "🤣", "👍", "😭", "🙏", "😘", "🥰", "😍", "😊", "🎉", "😁", "💕", "🥺", "😅", "🔥",
            "☺️", "🤦", "🤷", "🙄", "😆", "🤗", "😉", "🎂", "🤔", "👏", "🙂", "😳", "🥳", "😎", "👌",
            "💜", "😔", "💪", "✨", "💖", "👀", "😋", "😏", "😢", "👉", "💗", "😩", "💯", "🌹", "💞", "🎈",
            "💙", "😃", "😡", "💐", "😜", "🙈", "🤞", "😄", "🤤", "🙌", "🤪", "❣️", "😀", "💋", "💀", "👇",
            "💔", "😌", "💓", "🤩", "🙃", "😬", "😱", "😴", "🤭", "😐", "🌞", "😒", "😇", "🌸", "😈", "🎶",
            "✌️", "🎊", "🥵", "😞", "💚", "☀️", "🖤", "💰", "😚", "👑", "🎁", "💥", "🙋", "☹️", "😑", "🥴",
            "👈", "💩", "✅", "👋", "🤮", "😤", "🤢", "🌟", "❗", "😥", "🌈", "💛", "😝", "😫", "😲", "🖕",
            "‼️", "🔴", "🌻", "🤯", "💃", "👊", "🤬", "🏃", "😕", "👁️", "⚡", "☕", "🍀", "💦", "⭐", "🦋",
            "🤨", "🌺", "🤘", "🌷", "💝", "💤", "🤝", "🐰", "😓", "💘", "🍻", "😟", "😣", "🧐", "😠",
            "🤠", "🌙", "😛", "🤙", "🙊", "🧡", "🤡", "🤫", "🌼", "🥂", "😷", "🤓", "🥶", "😶", "😖",
            "🐶", "🐱", "🍕", "🍺", "🚗", "🏠", "🎮", "🎸", "🎵", "🍰",
        )
        private val POPULAR_RANK: Map<String, Int> = POPULAR.withIndex().associate { it.value to it.index }
        private fun popularity(emoji: String): Int = POPULAR_RANK[emoji] ?: POPULAR.size
        private const val TOP = 32

        /**
         * The emoji the keyword names comes first, together with a very popular emoji whose name
         * contains the keyword ("Herz": ❤️ "rotes Herz" before the card suit ♥️ "Herz"); a popular
         * emoji only loosely tied to the keyword comes after those ("Hut": not 🥳 first).
         */
        private fun group(hit: Hit): Int = when {
            hit.kind == 0 -> 0
            hit.nameWord && popularity(hit.row.emoji) < TOP -> 0
            hit.kind == 1 && popularity(hit.row.emoji) < POPULAR.size -> 1
            hit.kind == 1 -> 2
            else -> 3
        }

        /**
         * Function words of German, English and Spanish (lowercase, not folded: "schön" is no
         * "schon") never get emojis, although some are keywords ("die" is a die in English).
         */
        val STOPWORDS: Set<String> = setOf(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines",
            "und", "oder", "aber", "doch", "denn", "weil", "dass", "wenn", "als", "wie", "ob", "so", "nur",
            "ich", "du", "er", "sie", "es", "wir", "ihr", "mich", "dich", "sich", "mir", "dir", "uns", "euch",
            "mein", "dein", "sein", "unser", "euer", "ist", "bin", "bist", "sind", "seid", "war", "hab",
            "habe", "hast", "hat", "haben", "kann", "kannst", "können", "muss", "will", "wird", "werden",
            "mit", "für", "von", "zu", "zum", "zur", "auf", "aus", "bei", "nach", "vor", "über", "unter", "im",
            "in", "an", "am", "um", "bis", "noch", "schon", "auch", "nicht", "kein", "keine", "ja", "da",
            "hier", "dort", "dann", "jetzt", "mal", "was", "wer", "wo", "wann", "warum",
            "alles", "alle", "viel", "viele", "mehr", "sehr", "immer", "wieder", "etwas", "nichts", "man",
            "ganz", "gerade", "also", "eben", "gibt", "geht", "gehen", "macht", "mache", "machen", "sagt",
            "sagen", "kommt", "kommen", "gute", "guten", "gutes", "guter", "lass", "lasst", "soll", "sollte",
            "the", "and", "or", "but", "is", "are", "was", "were", "be", "to", "of", "on", "at", "by",
            "for", "with", "it", "its", "this", "that", "you", "he", "she", "we", "they", "me", "my", "your",
            "a", "an", "if", "not", "no", "do", "does", "did", "have", "has", "had", "can", "will",
            "all", "more", "very", "just", "get", "got", "make", "made", "too",
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "que", "y", "o", "en", "con",
            "por", "para", "se", "lo", "le", "les", "su", "sus", "mi", "tu", "yo", "al", "es", "son", "ser",
            "muy", "más", "pero", "como",
        )
        private const val HEADER = "EKW1"

        /** Reads the asset format of `tools/emoji_keywords.py`. [isSupported] drops emojis the device cannot show. */
        fun read(input: InputStream, isSupported: (String) -> Boolean = { true }): EmojiKeywords {
            val lines = GZIPInputStream(input).bufferedReader(Charsets.UTF_8).readLines()
            require(lines.firstOrNull() == HEADER) { "not an emoji keyword table" }
            val languages = LinkedHashMap<String, Language>()
            var tag: String? = null
            val rows = ArrayList<Row>()
            fun finish() { tag?.let { languages[it] = build(rows) }; rows.clear() }
            for (line in lines.drop(1)) {
                if (line.isEmpty()) continue
                if (line.startsWith("@")) { finish(); tag = line.substring(1); continue }
                val parts = line.split('\t')
                require(parts.size == 3 && tag != null) { "bad emoji keyword line" }
                if (!isSupported(parts[0])) continue
                rows += Row(parts[0], parts[1], parts[2].split('|').filter { it.isNotBlank() }, rows.size)
            }
            finish()
            return EmojiKeywords(languages)
        }

        private class Row(val emoji: String, val name: String, val keywords: List<String>, val order: Int)

        /** [kind] 0: the key is the whole name; 1: a whole keyword; 2: a word inside a longer keyword. */
        private class Hit(val row: Row, val kind: Int, val nameWord: Boolean)

        private fun build(rows: List<Row>): Language {
            val hits = HashMap<String, MutableList<Hit>>()
            fun add(key: String, hit: Hit) {
                for (variant in variants(key)) {
                    if (variant.length < MIN_EXACT) continue
                    hits.getOrPut(variant) { ArrayList(2) } += hit
                }
            }
            for (row in rows) {
                val name = fold(row.name)
                val nameWords = words(row.name).flatMap { variants(it) }.toSet()
                for (keyword in row.keywords) {
                    val words = words(keyword)
                    if (words.size == 1) {
                        add(words[0], Hit(row, if (fold(keyword) == name) 0 else 1, fold(words[0]) in nameWords))
                    } else {
                        // a word inside "zu viel gegessen" is a weak key: only exact, from five letters, no function word
                        words.filter { fold(it).length >= MIN_TOKEN && it.lowercase() !in STOPWORDS }
                            .forEach { add(it, Hit(row, 2, fold(it) in nameWords)) }
                    }
                }
                // the name itself ("Katze") is the best key for its emoji
                words(row.name).let { if (it.size == 1) add(it[0], Hit(row, 0, true)) }
            }
            // the emoji the keyword names and the very popular ones first, then popular ones, then by kind
            // known weakness: a popular emoji only loosely tied to a keyword can still come first
            // ("Hut" -> 🥳 🤠); ordering loose hits by keyword count instead made common words worse
            // ("Liebe" -> 🧡, "love" -> 🏩)
            val ranking = compareBy<Hit>({ group(it) }, { popularity(it.row.emoji) }, { it.kind },
                { it.row.keywords.size }, { it.row.order })
            val exact = HashMap<String, List<String>>(hits.size * 2)
            val generic = HashSet<String>()
            val strong = HashSet<String>()
            for ((key, list) in hits) {
                if (list.any { it.kind <= 1 }) strong += key
                val sorted = list.sortedWith(ranking)
                // a key for very many emojis ("Gesicht", "Tier") only keeps the ones it names or popular ones
                val kept = if (sorted.distinctBy { it.row.emoji }.size <= MAX_GENERIC) sorted
                    else sorted.filter { it.kind == 0 }
                if (kept.isNotEmpty()) exact[key] = kept.map { it.row.emoji }.distinct() else generic += key
            }
            val stemHits = HashMap<String, MutableList<Pair<String, List<String>>>>()
            for ((key, emojis) in exact) {
                if (key !in strong) continue
                val stem = stem(key)
                if (stem.length >= MIN_STEM) stemHits.getOrPut(stem) { ArrayList(2) } += key to emojis
            }
            val stems = HashMap<String, List<String>>(stemHits.size * 2)
            for ((stem, keys) in stemHits) {
                // the key closest to the stem first: "katze" before "katzengesicht"
                stems[stem] = keys.sortedWith(compareBy({ it.first.length }, { it.first }))
                    .flatMap { it.second }.distinct()
            }
            strong.retainAll(exact.keys)
            return Language(exact, generic, strong, stems, strong.sorted().toTypedArray())
        }

        private fun collect(limit: Int, body: ((List<String>?) -> Unit) -> Unit): List<String> {
            val result = LinkedHashSet<String>()
            body { list -> list?.forEach { if (result.size < limit) result += it } }
            return result.toList()
        }

        private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")

        /** Words of a keyword, not folded: "rotes Herz" -> [rotes, Herz]. */
        private fun words(text: String): List<String> = text.split(WORD_BREAK).filter { fold(it).isNotEmpty() }

        /** Lowercase, umlauts to their base letter, ß to ss, accents removed, only letters and digits. */
        fun fold(text: String): String {
            val builder = StringBuilder(text.length)
            for (char in text.lowercase()) when (char) {
                'ä' -> builder.append('a')
                'ö' -> builder.append('o')
                'ü' -> builder.append('u')
                'ß' -> builder.append("ss")
                else -> when {
                    char.code < 0x80 -> if (char.isLetterOrDigit()) builder.append(char)
                    char.isLetter() -> Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
                        .forEach { if (it.isLetterOrDigit()) builder.append(it) }
                    char.isDigit() -> builder.append(char)
                }
            }
            return builder.toString()
        }

        /** The keys a word is found under: "Käse" -> kase and kaese (typed without umlaut keys). */
        private fun variants(word: String): List<String> {
            val plain = fold(word)
            val spelled = fold(word.lowercase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue"))
            return if (spelled == plain) listOf(plain) else listOf(plain, spelled)
        }

        private val SUFFIXES = listOf(
            "innen", "chen", "lein", "ing", "ies", "ern", "ten", "tet", "te", "en", "er", "es", "em",
            "ed", "st", "os", "as", "e", "n", "s", "t", "a", "o",
        )

        /** Removes one inflection ending, keeping at least [MIN_STEM] letters: katzen, katze -> katz. */
        fun stem(folded: String): String {
            for (suffix in SUFFIXES) {
                // "-chen" only on longer words: "catchen" is no diminutive of "cat"
                val keep = if (suffix == "chen") 4 else MIN_STEM
                if (folded.endsWith(suffix) && folded.length - suffix.length >= keep) {
                    val base = folded.substring(0, folded.length - suffix.length)
                    return if (suffix == "ies") base + "y" else base
                }
            }
            return folded
        }

        /** Damerau (optimal string alignment) distance at most one. */
        fun withinOneEdit(a: String, b: String): Boolean {
            if (a == b) return true
            val la = a.length
            val lb = b.length
            if (la == lb) {
                var first = -1
                for (i in 0 until la) if (a[i] != b[i]) { first = i; break }
                // one substitution
                var rest = true
                for (i in first + 1 until la) if (a[i] != b[i]) { rest = false; break }
                if (rest) return true
                // one swap of neighbours
                if (first + 1 < la && a[first] == b[first + 1] && a[first + 1] == b[first]) {
                    for (i in first + 2 until la) if (a[i] != b[i]) return false
                    return true
                }
                return false
            }
            if (kotlin.math.abs(la - lb) != 1) return false
            val (short, long) = if (la < lb) a to b else b to a
            var i = 0
            while (i < short.length && short[i] == long[i]) i++
            for (j in i until short.length) if (short[j] != long[j + 1]) return false
            return true
        }

        private fun lowerBound(sorted: Array<String>, key: String): Int {
            var low = 0
            var high = sorted.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (sorted[mid] < key) low = mid + 1 else high = mid
            }
            return low
        }
    }
}
