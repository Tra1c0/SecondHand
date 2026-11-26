// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

/**
 * Stage 105: punctuation the owner leaves out while typing fast (docs/NACHKORREKTUR-KONZEPT.md, rules P1 to P3): the comma
 * before a subordinate clause that shows its verb at the end, the comma after a subordinate clause at the start of the
 * sentence, and the full stop or question mark at the last word of a line ended with Enter in a multi-line field. Never
 * twice, never in lists, headings, links, numbers, abbreviations, code or after an emoji; a mark the owner removed is not
 * set again ([SentenceContext.isRejected] with the pair "word" -> "word,"). Pure: the text is read, never kept.
 *
 * Every change replaces the word before the mark with the word and the mark ("glaube" -> "glaube,"), so that a rejection
 * is a pair like every other correction.
 */
class AutoPunctuation(private val lexicon: SentenceLexicon, private val context: SentenceContext) {
    private val verbs get() = lexicon.verbs
    private val nouns get() = lexicon.nouns

    /**
     * Changes for the end of [text]. [lineBreak]: the last key put a line break at the end of the text in a multi-line
     * field (P3 runs only then). [german]: the keyboard language is German (otherwise only P3 for English sentences).
     */
    fun punctuate(text: String, lineBreak: Boolean, german: Boolean): List<Change> {
        val sentence = SentenceText.lastSentence(text) ?: return emptyList()
        val out = ArrayList<Change>()
        val isGerman = german && isGerman(sentence)
        if (isGerman) {
            commaBefore(sentence, out)
            commaAfterLeadingClause(sentence, out)
        }
        if (lineBreak) endOfLine(text, sentence, isGerman)?.let { out.add(it) }
        val sorted = out.filter { !context.isRejected(it.old, it.replacement) }.sortedBy { it.start }
        val accepted = ArrayList<Change>()
        for (c in sorted) if (accepted.isEmpty() || accepted.last().end <= c.start) accepted.add(c)
        return accepted
    }

    private fun isGerman(s: Sentence): Boolean {
        val words = s.words
        if (words.size < 2) return false
        val german = words.count { w ->
            context.frequency(w.text) >= 0 || context.frequency(w.lower) >= 0 || nouns.readings(w.text) != 0 ||
                verbs.readings(w.text).isNotEmpty() || GermanWords.pronoun(w.text, true) != null
        }
        return german * 10 >= words.size * 8
    }

    private fun withMark(word: Tok, mark: String, rule: String) = Change(word.start, word.end, word.text, word.text + mark, rule)

    /** The subject at [i] (pronoun, article and noun, or a bare noun): its slots and the index after it, or null. */
    private fun subjectAt(t: List<Tok>, i: Int, end: Int, bareNoun: Boolean = true): Pair<Set<Int>, Int>? {
        val tok = t.getOrNull(i) ?: return null
        if (!tok.isWord) return null
        GermanWords.determiner(tok.text)?.let { det ->
            var j = i + 1
            while (j < end && t[j].isWord && !t[j].capitalized && t[j].lower.length > 2 && context.frequency(t[j].lower) >= 0
                && verbs.readings(t[j].text).isEmpty()) j++
            if (j < end && t[j].capitalized) {
                val readings = det.readings and nouns.readings(t[j].text)
                val slots = slotsOf(readings)
                if (slots.isNotEmpty()) return slots to j + 1
            }
        }
        if (bareNoun && tok.capitalized && i > 0 && nouns.readings(tok.text) != 0) {
            val slots = slotsOf(nouns.readings(tok.text))
            if (slots.isNotEmpty()) return slots to i + 1
        }
        GermanWords.pronoun(tok.text, i == 0)?.let { return it.slots to i + 1 }
        return null
    }

    private fun slotsOf(readings: Int): Set<Int> {
        val out = HashSet<Int>()
        for (g in 0..2) if (readings and GermanNouns.bit(g, GermanNouns.NOM) != 0) out.add(3)
        if (readings and GermanNouns.bit(GermanNouns.PLURAL, GermanNouns.NOM) != 0) out.add(6)
        return out
    }

    private fun clauseEnd(t: List<Tok>, from: Int): Int {
        var i = from
        while (i < t.size) {
            val tok = t[i]
            if (!tok.isWord) return i
            if (i > from && (tok.lower in GermanWords.subordinators || tok.lower in GermanWords.coordinators)) return i
            i++
        }
        return i
    }

    private fun agrees(word: Tok, slots: Set<Int>) = !word.capitalized && verbs.readings(word.text).any { it.slot in slots }

    /** Words that belong before the conjunction of the clause: "ohne dass", "als ob", "so dass", "statt dass". */
    private val leading = setOf("ohne", "als", "statt", "anstatt", "außer",
        // focus words that belong to the clause: "…, auch wenn", "…, besonders wenn", "…, nur weil"
        "auch", "besonders", "gerade", "selbst", "sogar", "nur", "erst", "immer", "gleich", "noch", "schon")
    /** Conjunctions too often adverbs or particles to be judged ("da" also "there"). */
    private val notJudged = setOf("da")

    /** P1: "Ich weiß dass er kommt" -> "Ich weiß, dass er kommt". */
    private fun commaBefore(s: Sentence, out: MutableList<Change>) {
        val t = s.tokens
        for (j in 1 until t.size) {
            val conj = t[j]
            if (!conj.isWord || conj.capitalized || conj.lower !in GermanWords.subordinators || conj.lower in notJudged) continue
            var before = j - 1
            while (before > 0 && t[before].isWord && (t[before].lower in leading || t[before].lower == "so" && conj.lower == "dass")) before--
            val word = t[before]
            if (!word.isWord || word.lower in GermanWords.coordinators || word.lower in leading) continue
            if (t[before + 1].kind == Tok.Kind.PUNCT) continue
            // the subject right after the conjunction: a pronoun or an article with its noun (a bare noun is "als Nachteil")
            val (slots, after) = subjectAt(t, j + 1, t.size, bareNoun = false) ?: continue
            val end = clauseEnd(t, after)
            if (end <= after) continue
            val last = t[end - 1]
            if (!agrees(last, slots)) continue
            // comparisons: "wie"/"als" take a comma only before a clause with a pronoun as its subject ("größer als man denkt");
            // "als eine Unterart angesehen wurde", "so gut wie alle", "Ebenso wie er" are no such clause
            if (conj.lower == "wie" || conj.lower == "als") {
                if (GermanWords.pronoun(t[j + 1].text, false) == null || t[j + 1].lower in setOf("alle", "beide", "das", "die", "was")) continue
                if (before == 0 || t.getOrNull(j - 2)?.lower == "so") continue
            }
            // the clause is over: a mark, a conjunction, a line break, or the finite verb of the next clause after it
            val next = t.getOrNull(end)
            val over = if (next == null) s.closed else next.kind == Tok.Kind.PUNCT || next.lower in GermanWords.coordinators ||
                next.lower in GermanWords.subordinators || !next.capitalized && verbs.readings(next.text).isNotEmpty()
            if (!over) continue
            if (context.isProtected(conj.text)) continue
            out.add(withMark(word, ",", "P1"))
        }
    }

    /** Auxiliaries and modals that end a cluster of verbs ("erkannt werden konnten"). */
    private val clusterEnds = setOf("haben", "sein", "werden", "können", "müssen", "sollen", "wollen", "dürfen", "mögen", "lassen")

    /** P2: "Wenn du kommst bringe ich Kuchen mit" -> "Wenn du kommst, bringe ich Kuchen mit". */
    private fun commaAfterLeadingClause(s: Sentence, out: MutableList<Change>) {
        val t = s.tokens
        var i = 0
        if (i < t.size && t[i].isWord && t[i].lower in GermanWords.coordinators) i++
        val conj = t.getOrNull(i) ?: return
        if (!conj.isWord || conj.lower !in GermanWords.subordinators || conj.lower in notJudged) return
        val (slots, after) = subjectAt(t, i + 1, t.size, bareNoun = false) ?: return
        var k = after
        while (k < t.size && t[k].isWord && !agrees(t[k], slots)) k++
        // a cluster of verbs ends with the auxiliary or modal: "erkannt werden konnten", "verhängt habe"
        while (k + 1 < t.size && t[k + 1].isWord && verbs.readings(t[k + 1].text).any { it.lemma in clusterEnds && it.slot in slots }) k++
        if (k + 2 >= t.size || !t[k].isWord || !t[k + 1].isWord) return
        val next = t[k + 1]
        if (next.capitalized || next.lower in GermanWords.coordinators || verbs.readings(next.text).isEmpty()) return
        // the second verb opens the main clause only if its subject or object follows it ("bringe ich", "bringe Kuchen")
        val third = t[k + 2]
        val opens = third.isWord && (GermanWords.pronoun(third.text, false) != null || GermanWords.determiner(third.text) != null ||
            third.capitalized || third.lower in GermanWords.objectPronouns)
        if (!opens) return
        out.add(withMark(t[k], ",", "P2"))
    }

    // ------------------------------------------------------------------ P3: the end of a line

    private val listStart = Regex("""^\s*([-*•–>+]|\d+[.)]|[a-zA-Z][)])\s""")
    private val enPronouns = setOf("i", "you", "he", "she", "we", "they", "it")
    private val enAux = setOf("do", "does", "did", "is", "are", "was", "were", "can", "could", "will", "would", "should", "have",
        "has", "had", "am", "shall", "may", "might", "must")
    private val enQuestion = setOf("what", "why", "how", "where", "when", "who", "which", "whose", "whom")

    private fun endOfLine(text: String, s: Sentence, isGerman: Boolean): Change? {
        if (!text.endsWith("\n")) return null
        val lineEnd = text.length - 1
        val lineStart = text.lastIndexOf('\n', lineEnd - 1) + 1
        val line = text.substring(lineStart, lineEnd)
        if (listStart.containsMatchIn(line) || line.isBlank()) return null
        val t = s.tokens
        val last = t.lastOrNull() ?: return null
        // only a plain word at the end: no mark already there, no number, emoji, link, abbreviation or capitals-only word
        if (!last.isWord || text.substring(last.end, lineEnd).isNotBlank()) return null
        if (last.lower in GermanWords.abbreviations || last.text.length >= 2 && last.text.all { it.isUpperCase() }) return null
        val words = s.words
        // words and marks only: no number, emoji or other sign in the sentence
        if (words.size < 3 || t.any { it.kind == Tok.Kind.NUMBER || it.kind == Tok.Kind.OTHER }) return null
        val mark = when {
            isGerman -> germanMark(words) ?: return null
            looksEnglish(words) -> englishMark(words) ?: return null
            else -> return null
        }
        return withMark(last, mark, "P3")
    }

    private fun germanMark(t: List<Tok>): String? {
        var i = 0
        if (t[i].lower in GermanWords.coordinators) i++
        val first = t.getOrNull(i) ?: return null
        val second = t.getOrNull(i + 1) ?: return null
        val firstIsVerb = verbs.readings(first.text).isNotEmpty() && nouns.readings(first.text) == 0 &&
            GermanWords.pronoun(first.text, true) == null && GermanWords.determiner(first.text) == null &&
            first.lower !in GermanWords.questionWords
        // another finite verb later (not just an infinitive): a second clause ("Hätte es … gegeben, hätte es …"), unsure
        val laterFinite = t.drop(i + 2).any { w -> w.isWord && !w.capitalized &&
            verbs.readings(w.text).any { r -> r.slot != 4 && r.slot != 6 || verbs.form(r.lemma, r.mood, 1) == null } }
        if (firstIsVerb) {
            val pronoun = second.lower
            // the verb first with "du"/"ihr" asks; with "ich", "wir", "man", "Sie" it is just as often a statement without its
            // first word ("Kann ich nur empfehlen") or a request ("Kaufen Sie …"): no mark at all
            if (pronoun == "du" || pronoun == "ihr") return if (laterFinite) null else "?"
            if (GermanWords.pronoun(second.text, false) != null) return null
        }
        if (first.lower in GermanWords.questionWords && !second.capitalized && verbs.readings(second.text).isNotEmpty())
            return if (laterFinite) null else "?"
        // a statement needs a finite verb (a heading, a greeting or a list item has none)
        val hasVerb = t.withIndex().any { (k, w) -> w.isWord && (k == i || !w.capitalized) && verbs.readings(w.text).isNotEmpty() }
        return if (hasVerb) "." else null
    }

    private fun looksEnglish(words: List<Tok>): Boolean {
        val english = words.count { it.lower in enPronouns || it.lower in enAux || it.lower in enQuestion ||
            it.lower in setOf("the", "a", "an", "and", "to", "of", "my", "your", "not", "this", "that") }
        return english * 3 >= words.size && words.all { w -> w.text.all { it.code < 128 } }
    }

    private fun englishMark(words: List<Tok>): String? {
        val first = words[0].lower
        val second = words[1].lower
        if (first in enAux && second in enPronouns) return "?"
        if (first in enQuestion && (second in enAux || second in setOf("about", "if"))) return "?"
        if (words.any { it.lower in enPronouns } && words.any { it.lower in enAux || it.lower.endsWith("ed") || it.lower.endsWith("s") })
            return "."
        return null
    }
}
