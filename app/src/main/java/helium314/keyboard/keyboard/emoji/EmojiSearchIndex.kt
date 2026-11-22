// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Fork, stage 93a: the emoji search by name and keyword (Unicode CLDR annotations, assets/emoji/search_<lang>.tsv, built by
 * tools/emoji/build_search_index.py). Replaces the typo-tolerant word suggestions of the emoji dictionary, which returned at most
 * 18 candidates in the order of typing similarity, so obvious matches were missing ("herz" without the red heart).
 *
 * Rank of a term against an emoji: the whole name (0), a word of the name or a keyword (1), the start of such a word (2), inside
 * the name or a keyword (3). Several terms must all match; the ranks add up. Ties: the shorter name, then the keyboard's order of
 * the emojis (the category lists). Case, umlauts and accents do not matter ("gluecklich" finds "glücklich", "corazon" "corazón").
 */
object EmojiSearchIndex {
    class Entry(val emoji: String, val order: Int, val name: String, private val words: List<String>, private val texts: List<String>) {
        /** The rank of [term] (folded) for this emoji, or null when it does not match. */
        fun rank(term: String): Int? = when {
            texts[0] == term -> 0
            words.any { it == term } -> 1
            words.any { it.startsWith(term) } -> 2
            texts.any { it.contains(term) } -> 3
            else -> null
        }
    }

    val LANGUAGES = listOf("de", "en", "es")
    private val cache = HashMap<String, List<Entry>>()

    fun fold(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }

    @Synchronized
    fun entries(context: Context, language: String): List<Entry> = cache.getOrPut(language) {
        runCatching {
            context.assets.open("emoji/search_$language.tsv").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapIndexedNotNull { index, line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) return@mapIndexedNotNull null
                    val name = parts[1]
                    val keywords = parts.getOrNull(2)?.split('|')?.filter { it.isNotBlank() }.orEmpty()
                    val texts = (listOf(name) + keywords).map(::fold)
                    val words = texts.flatMap { it.split(' ', ':', ',', '-', '’', '\'').filter(String::isNotBlank) } + texts.drop(1)
                    Entry(parts[0], index, name, words.distinct(), texts)
                }.toList()
            }
        }.getOrDefault(emptyList())
    }

    /** True when at least one index file is there; otherwise the caller keeps the old dictionary search. */
    fun available(context: Context): Boolean = LANGUAGES.any { entries(context, it).isNotEmpty() }

    /** All emojis matching [query] in any of [languages] (the keyboard's language first), best first, without unsupported ones. */
    fun search(context: Context, query: String, languages: List<String> = LANGUAGES, supported: (String) -> Boolean = { true }): List<String> {
        val terms = fold(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        class Hit(val emoji: String, val score: Int, val nameLength: Int, val order: Int, val language: Int)
        val best = LinkedHashMap<String, Hit>()
        languages.distinct().forEachIndexed { languageIndex, language ->
            for (entry in entries(context, language)) {
                var score = 0
                var matches = true
                for (term in terms) {
                    val rank = entry.rank(term)
                    if (rank == null) { matches = false; break }
                    score += rank
                }
                if (!matches) continue
                val hit = Hit(entry.emoji, score, entry.name.length, entry.order, languageIndex)
                val old = best[entry.emoji]
                if (old == null || compare(hit.score, hit.nameLength, old.score, old.nameLength) < 0) best[entry.emoji] = hit
            }
        }
        return best.values.filter { supported(it.emoji) }
            .sortedWith(compareBy<Hit>({ it.score }, { it.nameLength }, { it.language }, { it.order }))
            .map { it.emoji }
    }

    private fun compare(score: Int, length: Int, otherScore: Int, otherLength: Int) =
        if (score != otherScore) score.compareTo(otherScore) else length.compareTo(otherLength)
}
