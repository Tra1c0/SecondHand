// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.memory

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Stage 46: finds the snippet (stage 42) that fits what is being typed, with local vectors only: no
 * model, no download, no network. Features of a text are its words, the first [PART] letters of
 * longer words ("word parts", so "Adresse" meets "Adressen") and the character trigrams of every
 * word with boundary marks; each weighted TF-IDF style over the owner's snippets, compared by cosine.
 * A snippet is offered only above [THRESHOLD], clearly ahead of the second best ([MARGIN]) and with at
 * least one shared word or word part (trigrams alone are never enough).
 */
class SnippetMatcher(documents: List<Document>) {
    /** One snippet: [id] is the caller's, [name] counts twice, [text] without placeholders. */
    class Document(val id: String, val name: String, val text: String) {
        override fun toString() = "Document(${name.length}/${text.length} chars)" // never the texts
    }

    class Match(val id: String, val score: Double, val second: Double) {
        override fun toString() = "Match(score=${"%.2f".format(Locale.ROOT, score)}, second=${"%.2f".format(Locale.ROOT, second)})"
    }

    private class Vector(val weights: Map<String, Double>, val norm: Double, val words: Set<String>)

    private val idf: Map<String, Double>
    private val defaultIdf: Double
    private val vectors: List<Pair<String, Vector>>

    init {
        val counts = documents.map { features(it.name + " " + it.name + " " + it.text.take(MAX_TEXT)) }
        val df = HashMap<String, Int>()
        counts.forEach { c -> c.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = documents.size
        idf = df.mapValues { ln((n + 1.0) / (it.value + 1.0)) + 1.0 }
        defaultIdf = ln(n + 1.0) + 1.0
        vectors = documents.zip(counts).map { (doc, c) -> doc.id to vector(c) }
    }

    val size: Int get() = vectors.size

    private fun vector(counts: Map<String, Int>): Vector {
        val weights = counts.mapValues { (feature, tf) -> (1.0 + ln(tf.toDouble())) * (idf[feature] ?: defaultIdf) * scale(feature) }
        val norm = sqrt(weights.values.sumOf { it * it })
        return Vector(weights, norm, counts.keys.filter { !it.startsWith(TRIGRAM) }.toSet())
    }

    /** Cosine of [query] with every snippet, best first (score 0 for an empty query). */
    fun scores(query: String): List<Pair<String, Double>> {
        val q = vector(features(query))
        if (q.norm == 0.0) return vectors.map { it.first to 0.0 }
        return vectors.map { (id, v) ->
            var dot = 0.0
            for ((feature, w) in q.weights) dot += w * (v.weights[feature] ?: 0.0)
            val shared = q.words.any { it in v.words }
            id to if (!shared || v.norm == 0.0) 0.0 else dot / (q.norm * v.norm)
        }.sortedByDescending { it.second }
    }

    /** The snippet to offer for [query] (the last words typed), or null. */
    fun best(query: String): Match? {
        val words = query.split(' ').count { normalize(it).length >= MIN_WORD }
        if (words < MIN_QUERY_WORDS) return null
        val ranked = scores(query)
        val first = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)?.second ?: 0.0
        if (first.second < THRESHOLD || first.second - second < MARGIN) return null
        return Match(first.first, first.second, second)
    }

    companion object {
        /** Measured on the synthetic set in the tests (precision and coverage there). */
        const val THRESHOLD = 0.35
        const val MARGIN = 0.08
        const val PART = 5
        const val MIN_WORD = 3
        const val MIN_QUERY_WORDS = 2
        /** Words of the query: the end of the sentence part. */
        const val QUERY_WORDS = 6
        const val MAX_TEXT = 400
        private const val WORD = "w:"
        private const val WORD_PART = "p:"
        private const val TRIGRAM = "t:"

        /** Words weigh most, then word parts, then trigrams. */
        private fun scale(feature: String) = when {
            feature.startsWith(WORD) -> 1.0
            feature.startsWith(WORD_PART) -> 0.7
            else -> 0.35
        }

        /** Lower case without accents and with ß as ss, so "Grüße" meets "grusse". */
        fun normalize(word: String): String =
            Normalizer.normalize(word.lowercase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "").filter { it.isLetterOrDigit() }

        /** Very common words carry no meaning for the match (compared without accents). */
        private val STOP = setOf(
            // German
            "der", "die", "das", "und", "ist", "ein", "eine", "einen", "einem", "einer", "dem", "den", "des", "mit", "fur", "auf",
            "von", "sie", "ich", "wir", "ihr", "zum", "zur", "vom", "beim", "ins", "auch", "noch", "nicht", "mir", "dir", "mich",
            "dich", "uns", "euch", "ihnen", "ihre", "sein", "seine", "mein", "meine", "meinen", "dein", "deine", "deinen", "unser",
            "wie", "was", "wer", "wann", "warum", "wenn", "dann", "weil", "dass", "aber", "oder", "als", "bis", "nach", "vor",
            "bei", "aus", "uber", "unter", "hier", "dort", "sehr", "gut", "ganz", "alle", "alles", "mehr", "viel", "vielen", "dank",
            "danke", "bitte", "gerne", "gern", "heute", "morgen", "jetzt", "nur", "schon", "mal", "habe", "hast", "hat", "haben",
            "bin", "bist", "sind", "war", "wird", "werden", "kann", "kannst", "muss", "will", "soll", "gibt", "geht", "macht",
            "steht", "melden", "eins", "zwei", "drei", "vier", "funf", "zehn", "minuten", "stunde", "stunden", "tag", "tage",
            "tagen", "woche", "wochen", "letzte", "letzten", "lang", "frei", "neu", "neue", "kurz", "gleich", "immer", "wieder",
            // English and Spanish
            "the", "and", "for", "you", "with", "this", "that", "are", "was", "have", "has", "will", "can", "just", "now", "here",
            "there", "what", "when", "where", "how", "your", "our", "from", "about", "into", "they", "them", "their", "some",
            "all", "one", "two", "thanks", "please", "que", "los", "las", "por", "una", "con", "del", "para", "como", "pero",
        )

        fun features(text: String): Map<String, Int> {
            val out = HashMap<String, Int>()
            fun add(f: String) { out[f] = (out[f] ?: 0) + 1 }
            for (raw in text.split(Regex("[\\s,.;:!?()\\[\\]{}\"„“”'’/]+"))) {
                val w = normalize(raw)
                if (w.length < MIN_WORD || w in STOP || w.all { it.isDigit() }) continue
                add(WORD + w)
                if (w.length > PART) add(WORD_PART + w.take(PART))
                val padded = "^$w$"
                for (i in 0..padded.length - 3) add(TRIGRAM + padded.substring(i, i + 3))
            }
            return out
        }
    }
}
