// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

import dev.agentkeyboard.core.CorrectionRules
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.ACC
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.DAT
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.GEN
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.NOM
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.PLURAL
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.bit
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.inCases
import dev.agentkeyboard.core.sentence.GermanVerbs.Mood
import kotlin.math.abs
import kotlin.math.min

/** What the sentence check asks of the keyboard: the main dictionary, the owner's words and his rejections. */
interface SentenceContext {
    /** Frequency (0..255) of [word] exactly as written in the main dictionary of the keyboard language; below 0: unknown. */
    fun frequency(word: String): Int
    /** [word] is the owner's on purpose: an own word, slang, a kept word of another language, a personal dictionary entry. */
    fun isProtected(word: String): Boolean
    /** The owner took the change of [old] to [new] back (weights of stage 107, or in this field). */
    fun isRejected(old: String, new: String): Boolean
}

/** The bundled lexicon of the sentence check. */
class SentenceLexicon(val verbs: GermanVerbs, val nouns: GermanNouns)

/** One change in the text: [start] until [end] (offsets in the text that was checked) becomes [replacement]. */
data class Change(val start: Int, val end: Int, val old: String, val replacement: String, val rule: String)

/**
 * Stage 104: corrects a word of the last sentence before the cursor once the rest of the sentence shows that it is wrong
 * (docs/NACHKORREKTUR-KONZEPT.md, rules R1 to R6). Every change needs: German as the language of the sentence, exactly one
 * candidate, a word the owner did not protect or reject, and complete evidence (a word after the decisive one, or the end
 * of the sentence). Pure: the text is read, never kept.
 */
class SentenceRepair(private val lexicon: SentenceLexicon, private val context: SentenceContext) {
    private val verbs get() = lexicon.verbs
    private val nouns get() = lexicon.nouns

    /** All changes for the last sentence of [text] (the text before the cursor), in text order, not overlapping. */
    fun repair(text: String): List<Change> {
        val sentence = SentenceText.lastSentence(text) ?: return emptyList()
        if (!isGerman(sentence)) return emptyList()
        val out = ArrayList<Change>()
        agreement(sentence)?.let { out.add(it) }
        dass(sentence)?.let { out.add(it) }
        prepositionCase(sentence, out)
        roleCase(sentence, out)
        nounAfterPreposition(sentence, out)
        joinedWords(sentence, out)
        return accepted(out)
    }

    private fun accepted(changes: List<Change>): List<Change> {
        val sorted = changes.filter { it.old != it.replacement && !context.isRejected(it.old, it.replacement) }.sortedBy { it.start }
        val out = ArrayList<Change>()
        for (c in sorted) if (out.isEmpty() || out.last().end <= c.start) out.add(c)
        return out
    }

    /** Most words must be German words (main dictionary, nouns or grammar words), otherwise nothing is judged. */
    private fun isGerman(s: Sentence): Boolean {
        val words = s.words
        if (words.size < 2) return false
        val german = words.count { known(it.text) }
        return german * 10 >= words.size * 8
    }

    private fun known(word: String): Boolean = context.frequency(word) >= 0 || context.frequency(word.lowercase()) >= 0 ||
        nouns.readings(word) != 0 || verbs.readings(word).isNotEmpty() || GermanWords.pronoun(word, true) != null ||
        GermanWords.determiner(word) != null || word.lowercase() in GermanWords.objectPronouns

    // ------------------------------------------------------------------ R1, R2: subject and finite verb

    /** A possible subject of the clause: its slots, nominative only or not, and the token index after it. */
    private class Candidate(val slots: Set<Int>, val nominativeOnly: Boolean, val after: Int, val personal: String?,
                            val decides: Boolean = true, val start: Int = -1)

    private fun clauseEnd(t: List<Tok>, from: Int): Int {
        var i = from
        while (i < t.size) {
            val tok = t[i]
            if (tok.kind == Tok.Kind.PUNCT || tok.kind == Tok.Kind.OTHER) return i
            if (tok.isWord && i > from && (tok.lower in GermanWords.subordinators || tok.lower in GermanWords.coordinators)) return i
            i++
        }
        return i
    }

    /** An article (with adjectives) and a noun from [i]: the readings of the phrase and the index after it, or null. */
    private fun nounPhrase(t: List<Tok>, i: Int, end: Int): Pair<Int, Int>? {
        val det = GermanWords.determiner(t[i].text) ?: return null
        var j = i + 1
        while (j < end && t[j].isWord && !t[j].capitalized && isAdjectiveForm(t[j].lower)) j++
        if (j >= end || !t[j].capitalized) return null
        val noun = nouns.readings(t[j].text)
        if (noun == 0) return null
        val readings = det.readings and noun
        return readings to j + 1
    }

    private fun isAdjectiveEnding(w: String) = w.length > 3 && (w.endsWith("e") || w.endsWith("en") || w.endsWith("er") ||
        w.endsWith("es") || w.endsWith("em"))

    private fun isAdjectiveForm(w: String) = w.length > 2 && (w.endsWith("e") || w.endsWith("en") || w.endsWith("er") ||
        w.endsWith("es") || w.endsWith("em")) && context.frequency(w) >= 0 && GermanWords.determiner(w) == null

    private fun nominativeSlots(readings: Int): Set<Int> {
        val out = HashSet<Int>()
        for (g in 0..2) if (readings and bit(g, NOM) != 0) out.add(3)
        if (readings and bit(PLURAL, NOM) != 0) out.add(6)
        return out
    }

    private fun onlyNominative(readings: Int) = readings != 0 && inCases(readings, 1 shl NOM) == readings

    private fun reportedSpeech(s: Sentence): Boolean {
        if (s.words.any { it.lower in GermanWords.reportMarkers }) return true
        val before = SentenceText.tokenize(s.before.takeLast(160))
        return before.any { tok -> tok.isWord && verbs.readings(tok.text).any { it.lemma in GermanWords.reportingVerbs } } ||
            s.before.trimEnd().endsWith(":")
    }

    private fun finiteReadings(word: String, subjunctive1: Boolean) =
        verbs.readings(word).filter { subjunctive1 || it.mood != Mood.SUBJUNCTIVE_1 }

    private fun agreement(s: Sentence): Change? {
        val t = s.tokens
        var i = 0
        if (i < t.size && t[i].isWord && t[i].lower in GermanWords.coordinators) i++
        // "Ja, …", "Digga, …": a single word before a comma is no part of the clause
        if (i + 1 < t.size && t[i].isWord && t[i + 1].text == "," && verbs.readings(t[i].text).isEmpty()) i += 2
        if (i >= t.size || !t[i].isWord) return null
        // from the second word on: the word at the verb's place may be a conjunction typed instead of the verb ("Ihr seit")
        val end = clauseEnd(t, i + 1)
        if (end - i < 2) return null
        val first = t[i]
        if (first.lower in GermanWords.subordinators && first.lower !in GermanWords.questionWords) return null
        val candidates = ArrayList<Candidate>()
        var v: Int
        var personalPrefield: GermanWords.Pronoun? = null
        val verbFirst = first.lower !in GermanWords.questionWords && GermanWords.pronoun(first.text, true) == null &&
            GermanWords.determiner(first.text) == null && first.lower !in GermanWords.prefieldAdverbs &&
            GermanWords.prepositionCases(first.text) == 0 && verbs.readings(first.text).isNotEmpty() &&
            t.getOrNull(i + 1)?.capitalized != true &&
            // also a noun ("Haben", "Essen"): a verb only with a subject pronoun right after it
            (nouns.readings(first.text) == 0 || GermanWords.pronoun(t.getOrNull(i + 1)?.text ?: "", false)?.nominativeOnly == true)
        if (verbFirst) {
            v = i
        } else {
            val pronoun = GermanWords.pronoun(first.text, true)
            val next = t.getOrNull(i + 1)
            val np = if (pronoun == null || next?.capitalized == true) nounPhrase(t, i, end) else null
            when {
                np != null -> {
                    if (nominativeSlots(np.first).isNotEmpty())
                        candidates.add(Candidate(nominativeSlots(np.first), onlyNominative(np.first), np.second, null))
                    v = np.second
                }
                pronoun != null -> {
                    // "Ihr" in front is also the dative ("Ihr gefällt das"): a possible subject, not a sure one
                    candidates.add(Candidate(pronoun.slots, pronoun.nominativeOnly, i + 1, first.lower))
                    if (pronoun.nominativeOnly || first.lower == "ihr") personalPrefield = pronoun
                    v = i + 1
                    while (personalPrefield != null && v < end && t[v].lower in GermanWords.appositives) v++
                }
                first.lower in GermanWords.objectPronouns || first.lower in GermanWords.prefieldAdverbs ||
                    first.lower in GermanWords.questionWords -> v = i + 1
                // a bare plural noun ("Kinder spielen"); a bare singular one is rarely the subject and often no noun ("Positiv")
                // a bare plural noun ("Kinder spielen") may be the subject, but names look the same ("Reiser fuhr"): it can only
                // stop a change, never decide one
                first.capitalized && verbs.readings(first.text).isEmpty() && 6 in nominativeSlots(nouns.readings(first.text)) &&
                    3 !in nominativeSlots(nouns.readings(first.text)) -> {
                    candidates.add(Candidate(setOf(6), false, i + 1, null, decides = false))
                    v = i + 1
                }
                else -> return null
            }
        }
        if (v >= end || !t[v].isWord) return null
        val verb = t[v]
        // the finite verb: lower case unless it starts the sentence, and no article of a following noun ("Daher meine Bewertung")
        if (v > i && verb.capitalized) return null
        if (GermanWords.determiner(verb.text) != null && t.getOrNull(v + 1)?.capitalized == true) return null
        if (context.isProtected(verb.text)) return null
        val reported = reportedSpeech(s)
        val readings = finiteReadings(verb.text, reported)
        if (readings.isEmpty()) {
            return if (personalPrefield != null && personalPrefield.slots.size == 1 && v + 1 < t.size) homophone(verb, personalPrefield.slots.first(), reported)
            else null
        }
        // the middle field: who else can be the subject
        var k = v + 1
        val middle = ArrayList<Tok>()
        while (k < end) {
            val tok = t[k]
            if (!tok.isWord) return null
            val np = nounPhrase(t, k, end)
            val pronoun = GermanWords.pronoun(tok.text, false)
            when {
                np != null -> {
                    if (nominativeSlots(np.first).isNotEmpty())
                        candidates.add(Candidate(nominativeSlots(np.first), onlyNominative(np.first), np.second, null, start = k))
                    k = np.second
                }
                pronoun != null -> {
                    candidates.add(Candidate(pronoun.slots, pronoun.nominativeOnly, k + 1, tok.lower, start = k))
                    k++
                }
                tok.lower in GermanWords.objectPronouns -> k++
                tok.capitalized -> {
                    val slots = nominativeSlots(nouns.readings(tok.text))
                    if (slots.isEmpty() && nouns.readings(tok.text) == 0) return null // a name or an unknown word: could be the subject
                    if (slots.isNotEmpty()) candidates.add(Candidate(slots, false, k + 1, null, decides = false))
                    k++
                }
                GermanWords.determiner(tok.text) != null -> return null // an article without a known noun after it
                context.frequency(tok.text) >= 0 || verbs.readings(tok.text).isNotEmpty() || verbs.knows(tok.lower) -> { middle.add(tok); k++ }
                else -> return null
            }
        }
        if (candidates.none { it.decides }) return null
        val nominative = candidates.filter { it.nominativeOnly }
        if (nominative.size > 1) return null
        val subject: Candidate
        val expected: Set<Int>
        if (nominative.size == 1) {
            subject = nominative.first()
            expected = subject.slots
            // a sure subject later in the clause next to another possible one: two clauses without a comma ("… gesagt ich soll");
            // right after the verb it is the subject of this clause ("Heute haben ich keine Zeit", "Das machen kein Sinn")
            if (subject.after > v && subject.start != v + 1 && candidates.size > 1) return null
        } else {
            val union = candidates.flatMap { it.slots }.toSet()
            if (union.size != 1) return null
            subject = candidates.filter { it.decides }.maxBy { it.after }
            expected = union
            // nothing after the verb but a mark ("Das weiche, …"): no clause to judge
            if (middle.isEmpty() && candidates.all { it.after <= v }) return null
        }
        if (expected.size != 1) return null
        val slot = expected.first()
        // evidence: a word after the verb; when the subject is not the pronoun right before the verb, the clause after the
        // subject must not be able to bring another subject ("Dann kommen mein Bruder und …", "Das machen die Kinder")
        if (v + 1 >= t.size) return null
        val adjacentSubject = subject.nominativeOnly && subject.after <= v
        if (!adjacentSubject) {
            val closed = s.closed && end == t.size || end < t.size && t[end].kind == Tok.Kind.PUNCT
            if (subject.after <= v && !closed) return null // only the word before the verb: the real subject may still come
            val after = t.getOrNull(subject.after)
            if (after == null && !closed) return null
            // after a sure subject only "und …" could add to it; after a possible one any phrase could be the real subject
            if (after != null && after.isWord && (after.lower in GermanWords.coordinators || !subject.nominativeOnly &&
                    (GermanWords.determiner(after.text) != null || GermanWords.pronoun(after.text, false) != null || after.capitalized))) return null
            if (after != null && (after.kind == Tok.Kind.NUMBER || after.kind == Tok.Kind.OTHER)) return null
        }
        // the subjunctive I of reported speech: only "er/sie/es" right before the verb make "Er habe" a slip for "Er hat"
        // ("Man nehme", "gebe es" stay: wishes and inverted reported speech)
        val subjunctiveSlip = adjacentSubject && subject.personal in setOf("er", "sie", "es")
        val effective = if (!subjunctiveSlip && !reported) verbs.readings(verb.text) else readings
        if (effective.any { it.slot in expected }) return null
        // another word of the clause could be its finite verb ("Das essen war gut"): the verb place is not what it seems
        if (middle.any { w -> !w.capitalized && finiteReadings(w.text, true).any { it.slot == slot } }) return null
        if (readings.any { it.lemma in GermanWords.copulas } && subject.personal !in setOf("ich", "du", "er", "wir", "ihr")) return null
        // an "ihr" form without "ihr" can be the imperative of a call ("Kameraden singt!")
        if (readings.any { it.slot == 5 && it.mood == Mood.PRESENT } && !subject.nominativeOnly) return null
        // a verb in front with "du" can be the imperative ("Gehe du voran"); in front, only a pronoun is a sure subject
        if (verbFirst && (subject.personal == null || subject.personal == "du" && readings.any { it.slot == 1 && it.mood == Mood.PRESENT })) return null
        val targets = readings.mapNotNull { verbs.form(it.lemma, it.mood, slot) }.toSet()
        if (targets.size != 1) return null
        val target = matchCase(verb.text, targets.first())
        // a form of the regular pattern must be confirmed by the dictionary (a rare irregular verb may be missing from the
        // file); forms from the file are trusted (the dictionary lacks many: "nimmst", "fährst")
        if (readings.none { verbs.isListed(it.lemma) } && context.frequency(target.lowercase()) < 0 && context.frequency(target) < 0) return null
        return Change(verb.start, verb.end, verb.text, target, "R1")
    }

    /** R2: "Ihr seit" -> "seid": a word that sounds the same and is the verb form the subject needs. */
    private fun homophone(word: Tok, slot: Int, subjunctive1: Boolean): Change? {
        val w = word.lower
        if (word.capitalized || w in GermanWords.appositives || w in GermanWords.prefieldAdverbs) return null
        val closedClass = GermanWords.prepositionCases(w) != 0 || w in GermanWords.prepositionOrConjunction ||
            w in GermanWords.subordinators || w in GermanWords.coordinators
        if (!closedClass) return null
        val candidates = soundAlikes(w).filter { v -> finiteReadings(v, subjunctive1).any { it.slot == slot } }.toSet()
        if (candidates.size != 1) return null
        val target = candidates.first()
        if (finiteReadings(target, subjunctive1).none { verbs.isListed(it.lemma) } && context.frequency(target) < 0) return null
        return Change(word.start, word.end, word.text, target, "R2")
    }

    /** Spellings of the same sound: final d/t, g/k, b/p, s/ß/ss, and ä/e. */
    private fun soundAlikes(w: String): Set<String> {
        val out = LinkedHashSet<String>()
        val last = w.last()
        val pairs = mapOf('t' to "d", 'd' to "t", 'k' to "g", 'g' to "k", 'p' to "b", 'b' to "p")
        pairs[last]?.let { out.add(w.dropLast(1) + it) }
        if (w.endsWith("ss")) { out.add(w.dropLast(2) + "ß"); out.add(w.dropLast(1)) }
        if (w.endsWith("ß")) out.add(w.dropLast(1) + "ss")
        if (last == 's' && !w.endsWith("ss")) out.add(w.dropLast(1) + "ß")
        for (i in w.indices) {
            if (w[i] == 'ä') out.add(w.substring(0, i) + "e" + w.substring(i + 1))
            if (w[i] == 'e') out.add(w.substring(0, i) + "ä" + w.substring(i + 1))
        }
        out.remove(w)
        return out
    }

    private fun matchCase(model: String, word: String) =
        if (model.first().isUpperCase()) word.replaceFirstChar { it.uppercaseChar() } else word

    // ------------------------------------------------------------------ R3: das -> dass

    private val subjectPronouns = setOf("ich", "du", "er", "sie", "es", "wir", "ihr", "man")

    private fun dass(s: Sentence): Change? {
        val t = s.tokens
        for (j in 1 until t.size - 2) {
            val tok = t[j]
            if (tok.text != "das" || !t[j + 1].isWord || t[j + 1].lower !in subjectPronouns || t[j + 1].text == "Sie") continue
            var p = j - 1
            if (t[p].text == ",") p--
            val prev = t.getOrNull(p) ?: continue
            if (!prev.isWord || prev.capitalized) continue // a noun before: "das" can be its relative pronoun
            val prevIsVerb = verbs.readings(prev.text).isNotEmpty() || verbs.knows(prev.lower)
            if (!prevIsVerb) continue
            val pronoun = GermanWords.pronoun(t[j + 1].text, false) ?: continue
            val end = clauseEnd(t, j + 2)
            if (end <= j + 2) continue
            val last = t[end - 1]
            // verb last, agreeing with the pronoun: a subordinate clause, but only once the clause has ended (a mark, a conjunction,
            // the end of the sentence); right after "ich weiß" it may still be a new main clause ("Ich habe das ich weiß nicht")
            if (!last.isWord || finiteReadings(last.text, true).none { it.slot in pronoun.slots }) continue
            val over = if (end == t.size) s.closed else t[end].kind == Tok.Kind.PUNCT || t[end].lower in GermanWords.coordinators
            if (!over) continue
            if (context.isProtected("das")) continue
            return Change(tok.start, tok.end, tok.text, "dass", "R3")
        }
        return null
    }

    // ------------------------------------------------------------------ R4: article after a preposition, R4b: by role

    private fun prepositionCase(s: Sentence, out: MutableList<Change>) {
        val t = s.tokens
        for (p in 0 until t.size - 2) {
            val prep = t[p]
            // "um" also starts "um … zu" and times ("um ein Uhr"): not judged
            if (!prep.isWord || prep.capitalized && p > 0 || prep.lower in GermanWords.prepositionOrConjunction || prep.lower == "um") continue
            val cases = GermanWords.prepositionCases(prep.text)
            if (cases == 0) continue
            // "vom Hof aus einen Tunnel": "aus" closing "von"; "um … zu" with an infinitive is no preposition
            if (prep.lower == "aus" && t.subList(0, p).any { it.lower == "von" || it.lower == "vom" }) continue
            articleFor(t, p + 1, cases, "R4")?.let { out.add(it) }
        }
    }

    /** The article at [d] directly before a noun gets the form that agrees with it in one of [cases]. */
    private fun articleFor(t: List<Tok>, d: Int, cases: Int, rule: String): Change? {
        val detTok = t[d]
        val det = GermanWords.determiner(detTok.text) ?: return null
        if (det.family == "welch") return null // a relative or question word ("nach welchem Frauen")
        val nounTok = t.getOrNull(d + 1) ?: return null
        if (!nounTok.capitalized || context.isProtected(nounTok.text)) return null
        val noun = nouns.readings(nounTok.text)
        // a common noun only (rare forms are often names or old datives: "die Saale"), no nominalised verb ("das Fußballspielen")
        if (noun == 0 || nouns.frequency(nounTok.text) < MIN_ARTICLE_NOUN_FREQUENCY || endsInVerb(nounTok.text)) return null
        // the number "ein" before a time: "gegen ein Uhr"
        if (detTok.lower == "ein" && nounTok.text == "Uhr") return null
        // an old dative in -e ("dem Tage", "die Saale" as a river) is no evidence
        if (noun == (noun and (GermanNouns.bit(GermanNouns.MASC, DAT) or GermanNouns.bit(GermanNouns.NEUT, DAT))) &&
            nounTok.text.endsWith("e") && nouns.readings(nounTok.text.dropLast(1)) != 0) return null
        if (inCases(det.readings and noun, cases) != 0) return null
        // the evidence is complete only once the noun is followed by something that ends the phrase: no second capital word
        // (a name, "Kölner Dachwohnung"), no hyphen ("Stahl-"); a word also written in lower case may be a nominalisation
        val next = t.getOrNull(d + 2) ?: return null
        if (next.capitalized || next.text == "-" || next.kind == Tok.Kind.OTHER) return null
        if (context.frequency(nounTok.lower) >= 0) return null
        val forms = GermanWords.family(det.family).filter { (_, readings) -> inCases(readings and noun, cases) != 0 }.keys
        if (forms.size != 1) return null
        // every gender reading of the noun in these cases must agree with this one form (no mixed genders)
        val target = matchCase(detTok.text, forms.first())
        if (context.isProtected(detTok.text)) return null
        return Change(detTok.start, detTok.end, detTok.text, target, rule)
    }

    /** R4b: with a nominative-only subject, a dative pronoun and no copula, an article and noun in the middle are accusative. */
    private fun roleCase(s: Sentence, out: MutableList<Change>) {
        val t = s.tokens
        val end = clauseEnd(t, 0)
        val words = t.subList(0, end)
        val subjectCount = words.count { GermanWords.pronoun(it.text, it === t.first())?.nominativeOnly == true }
        if (subjectCount != 1) return
        if (words.none { it.lower in GermanWords.dativePronouns }) return
        if (words.any { w -> finiteReadings(w.text, true).any { it.lemma in GermanWords.copulas } }) return
        for (d in 1 until end - 1) {
            if (GermanWords.prepositionCases(t[d - 1].text) != 0 || GermanWords.contraction(t[d - 1].text) != 0) continue
            if (GermanWords.determiner(t[d].text) == null || !t[d + 1].capitalized) continue
            articleFor(t, d, 1 shl ACC, "R4b")?.let { out.add(it) }
        }
    }

    // ------------------------------------------------------------------ R5: "zu hause" -> "zu Hause"

    private fun nounAfterPreposition(s: Sentence, out: MutableList<Change>) {
        val t = s.tokens
        for (p in 0 until t.size - 1) {
            val prep = t[p]
            if (!prep.isWord || prep.lower in GermanWords.prepositionOrConjunction) continue
            var cases = GermanWords.prepositionCases(prep.text)
            var article = GermanWords.contraction(prep.text)
            var w = p + 1
            if (cases == 0 && article == 0) continue
            if (article != 0) cases = GermanNouns.cases(article)
            GermanWords.determiner(t[w].text)?.let { if (w + 1 < t.size) { article = it.readings; w++ } }
            val tok = t[w]
            if (!tok.isWord || tok.capitalized || context.isProtected(tok.text)) continue
            val upper = tok.text.replaceFirstChar { it.uppercaseChar() }
            val noun = nouns.readings(upper)
            if (noun == 0) continue
            val fits = if (article != 0) inCases(article and noun, cases) else inCases(noun, cases)
            if (fits == 0) continue
            // only a word the dictionary does not know in lower case ("hause"); a known one may be an adjective ("im letzten Jahr")
            if (context.frequency(tok.text) >= 0) continue
            // with an adjective's ending the next word must show that no noun follows ("zum äußeren Rand")
            if (isAdjectiveEnding(tok.lower)) {
                val next = t.getOrNull(w + 1)
                if (next == null && !s.closed || next != null && (next.capitalized || next.kind == Tok.Kind.OTHER)) continue
            }
            out.add(Change(tok.start, tok.end, tok.text, upper, "R5"))
        }
    }

    // ------------------------------------------------------------------ R6: two words that are one ("der Polen Zeit")

    private fun joinedWords(s: Sentence, out: MutableList<Change>) {
        val t = s.tokens
        for (d in 0 until t.size - 2) {
            val det = GermanWords.determiner(t[d].text) ?: continue
            val a = t[d + 1]
            val b = t[d + 2]
            if (!a.capitalized || !b.capitalized) continue
            // two known German nouns, and no third capital word (a name of several words, "die Territory Art")
            if (nouns.readings(a.text) == 0 || nouns.readings(b.text) == 0) continue
            if (t.getOrNull(d + 3)?.let { it.capitalized || it.text == "-" } == true) continue
            if (context.isProtected(a.text) || context.isProtected(b.text)) continue
            val cases = (t.getOrNull(d - 1)?.let { GermanWords.prepositionCases(it.text) } ?: 0)
                .let { if (it == 0) (1 shl NOM) or (1 shl DAT) or (1 shl ACC) else it }
            // the article fits the first word in the case the place needs: two words ("der Frau Blumen")
            if (inCases(det.readings and nouns.readings(a.text), cases) != 0) continue
            val candidate = closestNoun(a.text + b.text.lowercase(), det.readings, cases) ?: continue
            out.add(Change(a.start, b.end, s.full.substring(a.start, b.end), candidate, "R6"))
        }
    }

    /** The one noun of the lexicon closest to [joined] (typing distance) that agrees with the article in [cases]. */
    private fun closestNoun(joined: String, article: Int, cases: Int): String? {
        val lower = joined.lowercase()
        val limit = maxOf(1, lower.length / 3)
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        var tie = false
        for (form in nouns.withPrefix(joined.substring(0, 2))) {
            if (abs(form.length - joined.length) > limit) continue
            if (inCases(article and nouns.readings(form), cases) == 0) continue
            val dist = distance(lower, form.lowercase())
            if (dist > limit) continue
            // a compound split in two ("Haus Tür") needs a known word, a word with typing errors a common one
            if (nouns.frequency(form) < if (dist == 0) MIN_COMPOUND_FREQUENCY else MIN_JOINED_FREQUENCY) continue
            if (dist < bestDistance) { best = form; bestDistance = dist; tie = false } else if (dist == bestDistance && form != best) tie = true
        }
        return if (tie) null else best
    }

    /** "Fußballspielen", "Tragen": the noun ends in a verb's infinitive (a nominalised verb, neuter, often missing in the data). */
    private fun endsInVerb(noun: String): Boolean {
        val lower = noun.lowercase()
        if (!lower.endsWith("n")) return false
        for (cut in 0..lower.length - 5) if (verbs.knows(lower.substring(cut))) return true
        return false
    }

    companion object {
        /** An article is only changed before a noun the dictionary knows this well (frequency of the form). */
        const val MIN_ARTICLE_NOUN_FREQUENCY = 70
        /** A joined word must be a common noun (main dictionary frequency). */
        const val MIN_JOINED_FREQUENCY = 100
        /** A compound written as two words needs only to be in the main dictionary with this frequency. */
        const val MIN_COMPOUND_FREQUENCY = 40

        /** Damerau-Levenshtein distance (substitution, insertion, deletion, swap of neighbours: 1 each). */
        @JvmStatic
        fun distance(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1] || CorrectionRules.sameBaseLetter(a[i - 1], b[j - 1])) 0 else 1
                var v = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = min(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
            }
            return d[a.length][b.length]
        }
    }
}
