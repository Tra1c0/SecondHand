// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

/**
 * Stages 104/105: the finite forms of German verbs, for the sentence check. Regular verbs follow [derive] (the same pattern
 * as `tools/build_german_lexicon.py`); the verbs whose forms differ come from the bundled file `german_verb_forms.txt`
 * (UniMorph German, CC BY-SA 3.0; "sein" and "tun" from the standard paradigm). [isLemma] answers from the bundled verb list
 * (`german_verb_lemmas.txt`, stage 72). Verbs with an inseparable or separable prefix ("vergeben", "abgeben") take the forms
 * of their base verb ("vergibt", "abgibt"). No text is kept, only the lexicon.
 *
 * Slots: 1..6 = ich, du, er/sie/es, wir, ihr, sie/Sie.
 */
class GermanVerbs(private val isLemma: (String) -> Boolean, irregularLines: Sequence<String>) {
    enum class Mood { PRESENT, PAST, SUBJUNCTIVE_1, SUBJUNCTIVE_2 }

    /** One finite reading of a word: [lemma], [mood], [slot] (1..6). */
    data class Reading(val lemma: String, val mood: Mood, val slot: Int)

    private val overrides = HashMap<String, Map<String, String>>()
    /** Every finite form of the irregular verbs -> its readings. */
    private val irregularForms = HashMap<String, MutableList<Reading>>()

    init {
        for (line in irregularLines) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split('\t')
            if (parts.size < 2) continue
            overrides[parts[0]] = parts.drop(1).mapNotNull { field ->
                val eq = field.indexOf('=')
                if (eq <= 0) null else field.substring(0, eq) to field.substring(eq + 1)
            }.toMap()
        }
        for (lemma in overrides.keys) {
            val forms = derive(lemma) ?: continue
            forms.forEachIndexed { i, form -> irregularForms.getOrPut(form) { ArrayList(2) }.add(Reading(lemma, MOODS[i / 6], i % 6 + 1)) }
        }
    }

    /** Whether the forms of [lemma] come from the file (UniMorph) rather than from the regular pattern. */
    fun isListed(lemma: String): Boolean = lemma in overrides || baseOfPrefixed(lemma) != null

    /** Whether [lemma] is a verb this lexicon can conjugate. */
    fun knows(lemma: String): Boolean = lemma in overrides || isLemma(lemma) && derive(lemma) != null

    /** The finite readings of [word] (lower case as typed; a capital first letter is allowed). Empty: no finite verb form. */
    fun readings(word: String): List<Reading> {
        if (word.length < 2 || word.length > 30 || !word.all { it.isLetter() }) return emptyList()
        val w = word.lowercase()
        val out = ArrayList<Reading>(4)
        irregularForms[w]?.let { out.addAll(it) }
        // a prefixed irregular verb: "vergibt" = "ver" + "gibt"
        for (prefix in PREFIXES) {
            if (w.length <= prefix.length + 1 || !w.startsWith(prefix)) continue
            val rest = w.substring(prefix.length)
            irregularForms[rest]?.forEach { r ->
                val lemma = prefix + r.lemma
                if (lemma !in overrides && isLemma(lemma)) out.add(Reading(lemma, r.mood, r.slot))
            }
        }
        // regular verbs: strip each possible ending, try the infinitive, and compare with the derived paradigm
        for (ending in ENDINGS) {
            if (w.length - ending.length < 2 || !w.endsWith(ending)) continue
            val stem = w.substring(0, w.length - ending.length)
            for (inf in arrayOf(stem + "en", stem + "n")) {
                if (inf in overrides || !isLemma(inf) || baseOfPrefixed(inf) != null) continue
                val forms = derive(inf) ?: continue
                forms.forEachIndexed { i, form ->
                    if (form == w) {
                        val r = Reading(inf, MOODS[i / 6], i % 6 + 1)
                        if (r !in out) out.add(r)
                    }
                }
            }
        }
        return out
    }

    /** The form of [lemma] in [mood] and [slot], or null when the lemma is unknown. */
    fun form(lemma: String, mood: Mood, slot: Int): String? {
        if (slot !in 1..6) return null
        val forms = derive(lemma) ?: return null
        return forms[MOODS.indexOf(mood) * 6 + slot - 1]
    }

    /** "vergeben" -> ("ver", "geben") when "geben" is irregular and the prefixed verb has no line of its own. */
    private fun baseOfPrefixed(lemma: String): Pair<String, String>? {
        if (lemma in overrides) return null
        for (prefix in PREFIXES) {
            if (lemma.length > prefix.length + 2 && lemma.startsWith(prefix) && lemma.substring(prefix.length) in overrides)
                return prefix to lemma.substring(prefix.length)
        }
        return null
    }

    /** All 24 forms in the order P1..P6, T1..T6, C1..C6, K1..K6, or null. */
    private fun derive(lemma: String): List<String>? {
        baseOfPrefixed(lemma)?.let { (prefix, base) -> return derive(base)?.map { prefix + it } }
        if (lemma !in overrides && !isLemma(lemma)) return null
        return derive(lemma, overrides[lemma] ?: emptyMap())
    }

    companion object {
        private val MOODS = Mood.entries
        private val ENDINGS = arrayOf("", "e", "st", "est", "t", "et", "en", "n", "te", "test", "ten", "tet", "ete", "etest", "eten", "etet")
        /** Inseparable and the common separable prefixes, longest first. */
        private val PREFIXES = listOf(
            "zurück", "zusammen", "weiter", "wieder", "herum", "hinaus", "heraus", "vorbei", "hinein", "herein",
            "über", "unter", "durch", "nach", "ein", "aus", "auf", "mit", "vor", "weg", "hin", "her", "los", "fest",
            "miss", "emp", "ent", "ver", "zer", "an", "ab", "be", "er", "ge", "zu",
        ).sortedByDescending { it.length }
        private val ORDER = listOf("P", "T", "C", "K").flatMap { m -> (1..6).map { "$m$it" } }

        /** The regular pattern with the overrides of a line ("p1/p2/p3/p5", "t" past, "k" subjunctive II, or a slot). */
        @JvmStatic
        fun derive(lemma: String, over: Map<String, String>): List<String>? {
            val (stem, kind) = stemOf(lemma) ?: return null
            val e = if (needsE(stem, kind)) "e" else ""
            val sibilant = stem.last() in "sßxz"
            val p2: String
            val p3: String
            if (kind == Kind.EN) {
                p2 = stem + e + if (sibilant && e.isEmpty()) "t" else "st"
                p3 = "$stem${e}t"
            } else {
                p2 = stem + "st"
                p3 = stem + "t"
            }
            val present = listOf(over["p1"] ?: (stem + "e"), over["p2"] ?: p2, over["p3"] ?: p3, lemma, over["p5"] ?: p3, lemma)
            val regularPast = "$stem${e}te"
            val past = pastForms(over["t"] ?: regularPast)
            val subjunctive2 = pastForms(over["k"] ?: over["t"] ?: regularPast)
            val subjunctive1 = listOf(stem + "e", stem + "est", stem + "e", lemma, stem + "et", lemma)
            val forms = (present + past + subjunctive1 + subjunctive2).toMutableList()
            ORDER.forEachIndexed { i, slot -> over[slot]?.let { forms[i] = it } }
            return forms
        }

        private enum class Kind { EN, EL, N }

        private fun stemOf(lemma: String): Pair<String, Kind>? = when {
            lemma.length > 4 && (lemma.endsWith("eln") || lemma.endsWith("ern")) -> lemma.dropLast(1) to Kind.EL
            lemma.length > 3 && lemma.endsWith("en") -> lemma.dropLast(2) to Kind.EN
            lemma.length > 2 && lemma.endsWith("n") -> lemma.dropLast(1) to Kind.N
            else -> null
        }

        private fun needsE(stem: String, kind: Kind): Boolean {
            if (kind != Kind.EN) return false
            val last = stem.last()
            if (last == 'd' || last == 't') return true
            return (last == 'm' || last == 'n') && stem.length >= 2 && stem[stem.length - 2] !in "aeiouäöüylrmnh"
        }

        private fun pastForms(t: String): List<String> = when {
            t.endsWith("e") -> listOf(t, t + "st", t, t + "n", t + "t", t + "n")
            t.last() == 'd' || t.last() == 't' -> listOf(t, t + "est", t, t + "en", t + "et", t + "en")
            t.last() in "sßxz" -> listOf(t, t + "t", t, t + "en", t + "t", t + "en")
            else -> listOf(t, t + "st", t, t + "en", t + "t", t + "en")
        }
    }
}
