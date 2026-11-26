// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

import dev.agentkeyboard.core.sentence.GermanNouns.Companion.ACC
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.DAT
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.FEM
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.GEN
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.MASC
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.NEUT
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.NOM
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.PLURAL
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.bit

/**
 * Stages 104/105: the closed word classes of German grammar (articles, pronouns, prepositions with their case, conjunctions,
 * question words) as the sentence check needs them. Grammar, not a list of errors. All keys in lower case.
 */
object GermanWords {
    /** A personal pronoun as a possible subject: its [slots] (1..6, see [GermanVerbs]) and whether it is nominative only. */
    class Pronoun(val slots: Set<Int>, val nominativeOnly: Boolean)

    private val pronouns = mapOf(
        "ich" to Pronoun(setOf(1), true), "du" to Pronoun(setOf(2), true), "er" to Pronoun(setOf(3), true),
        "wir" to Pronoun(setOf(4), true), "man" to Pronoun(setOf(3), true), "wer" to Pronoun(setOf(3), true),
        // also dative or accusative (es, sie, das), dative (ihr), plural or singular (sie)
        "ihr" to Pronoun(setOf(5), false), "sie" to Pronoun(setOf(3, 6), false), "es" to Pronoun(setOf(3), false),
        "das" to Pronoun(setOf(3), false), "die" to Pronoun(setOf(3, 6), false), "dies" to Pronoun(setOf(3), false), "dieses" to Pronoun(setOf(3), false),
        "was" to Pronoun(setOf(3), false), "jemand" to Pronoun(setOf(3), false), "niemand" to Pronoun(setOf(3), false),
        "alle" to Pronoun(setOf(6), false), "beide" to Pronoun(setOf(6), false),
    )

    /** [word] as a possible subject pronoun; "Sie" in the middle of a sentence is the polite form (slot 6). */
    fun pronoun(word: String, sentenceStart: Boolean): Pronoun? {
        if (word == "Sie" && !sentenceStart) return Pronoun(setOf(6), false)
        return pronouns[word.lowercase()]
    }

    /** Pronouns that are never the subject (dative or accusative only). */
    val objectPronouns = setOf("mir", "dir", "ihm", "uns", "euch", "ihnen", "mich", "dich", "ihn", "sich", "einander")
    /** Of them, the dative only ones: a clause with one has its dative filled. */
    val dativePronouns = setOf("mir", "dir", "ihm", "ihnen")

    // --- articles and other determiners: readings mask in the bit scheme of GermanNouns ---

    private fun m(vararg readings: Pair<Int, Int>): Int = readings.fold(0) { acc, (g, c) -> acc or bit(g, c) }

    private val definite = mapOf(
        "der" to m(MASC to NOM, FEM to GEN, FEM to DAT, PLURAL to GEN),
        "die" to m(FEM to NOM, FEM to ACC, PLURAL to NOM, PLURAL to ACC),
        "das" to m(NEUT to NOM, NEUT to ACC),
        "den" to m(MASC to ACC, PLURAL to DAT),
        "dem" to m(MASC to DAT, NEUT to DAT),
        "des" to m(MASC to GEN, NEUT to GEN),
    )
    private val einEndings = mapOf(
        "" to m(MASC to NOM, NEUT to NOM, NEUT to ACC),
        "e" to m(FEM to NOM, FEM to ACC, PLURAL to NOM, PLURAL to ACC),
        "en" to m(MASC to ACC, PLURAL to DAT),
        "em" to m(MASC to DAT, NEUT to DAT),
        "er" to m(FEM to GEN, FEM to DAT, PLURAL to GEN),
        "es" to m(MASC to GEN, NEUT to GEN),
    )
    private val dieserEndings = mapOf(
        "er" to m(MASC to NOM, FEM to GEN, FEM to DAT, PLURAL to GEN),
        "e" to m(FEM to NOM, FEM to ACC, PLURAL to NOM, PLURAL to ACC),
        "es" to m(NEUT to NOM, NEUT to ACC, MASC to GEN, NEUT to GEN),
        "en" to m(MASC to ACC, PLURAL to DAT),
        "em" to m(MASC to DAT, NEUT to DAT),
    )
    private const val PLURAL_BITS = 0xF000
    /** ein-type stems (endings of [einEndings]); "ein" has no plural. */
    private val einStems = listOf("kein", "mein", "dein", "sein", "ihr", "unser", "euer", "eur", "ein")
    private val dieserStems = listOf("dies", "jed", "jen", "welch", "manch", "solch")

    /** One determiner: its family stem (to build the other forms) and the readings of this form. */
    class Determiner(val family: String, val readings: Int)

    /** [word] as a determiner, or null. "euer" and "unser" without ending count as the bare form. */
    fun determiner(word: String): Determiner? {
        val w = word.lowercase()
        definite[w]?.let { return Determiner("d", it) }
        for (stem in einStems) {
            if (!w.startsWith(stem)) continue
            val ending = w.substring(stem.length)
            if (stem == "euer" && ending.isNotEmpty()) continue
            var readings = einEndings[ending] ?: continue
            if (stem == "ein") readings = readings and PLURAL_BITS.inv()
            return Determiner(if (stem == "euer") "eur" else stem, readings)
        }
        for (stem in dieserStems) {
            if (!w.startsWith(stem)) continue
            var readings = dieserEndings[w.substring(stem.length)] ?: continue
            if (stem == "jed") readings = readings and PLURAL_BITS.inv()
            return Determiner(stem, readings)
        }
        return null
    }

    /** All forms of a determiner family with their readings. */
    fun family(family: String): Map<String, Int> = when (family) {
        "d" -> definite
        in dieserStems -> dieserEndings.mapKeys { family + it.key }
            .mapValues { if (family == "jed") it.value and PLURAL_BITS.inv() else it.value }
        "eur" -> einEndings.mapKeys { if (it.key.isEmpty()) "euer" else "eur" + it.key }
        else -> einEndings.mapKeys { family + it.key }.mapValues { if (family == "ein") it.value and PLURAL_BITS.inv() else it.value }
    }

    // --- prepositions: the cases they govern (bits 0..3 of NOM, GEN, DAT, ACC) ---

    private const val D = 1 shl DAT
    private const val A = 1 shl ACC
    private const val G = 1 shl GEN
    private val prepositions = mapOf(
        "mit" to D, "nach" to D, "bei" to D, "von" to D, "zu" to D, "aus" to D, "gegenüber" to D, "ab" to D, "samt" to D,
        "durch" to A, "für" to A, "gegen" to A, "ohne" to A, "um" to A,
        "wegen" to (G or D), "trotz" to (G or D), "statt" to (G or D), "anstatt" to (G or D), "innerhalb" to (G or D),
        "außerhalb" to (G or D), "laut" to (G or D),
        "in" to (D or A), "an" to (D or A), "auf" to (D or A), "über" to (D or A), "unter" to (D or A), "vor" to (D or A),
        "hinter" to (D or A), "neben" to (D or A), "zwischen" to (D or A),
    )
    /** Preposition and article in one word: the readings of the article inside. */
    private val contractions = mapOf(
        "zum" to m(MASC to DAT, NEUT to DAT), "zur" to m(FEM to DAT), "im" to m(MASC to DAT, NEUT to DAT),
        "am" to m(MASC to DAT, NEUT to DAT), "beim" to m(MASC to DAT, NEUT to DAT), "vom" to m(MASC to DAT, NEUT to DAT),
        "ins" to m(NEUT to ACC), "ans" to m(NEUT to ACC), "aufs" to m(NEUT to ACC), "fürs" to m(NEUT to ACC),
        "durchs" to m(NEUT to ACC), "ums" to m(NEUT to ACC),
    )

    /** The cases [word] governs as a preposition (bits of the case numbers), 0 if it is none. */
    fun prepositionCases(word: String): Int = prepositions[word.lowercase()] ?: 0

    /** The article readings inside a contraction ("zum" = zu dem), 0 if it is none. */
    fun contraction(word: String): Int = contractions[word.lowercase()] ?: 0

    /** Prepositions that are also conjunctions ("seit er da ist", "während sie schläft", "bis er kommt"): never judged. */
    val prepositionOrConjunction = setOf("seit", "während", "bis")

    val subordinators = setOf(
        "dass", "weil", "ob", "obwohl", "obgleich", "wenn", "falls", "sobald", "solange", "nachdem", "bevor", "ehe",
        "damit", "während", "seitdem", "sodass", "indem", "sofern", "als", "wie", "bis", "seit", "da",
    )
    val coordinators = setOf("und", "oder", "aber", "denn", "sondern", "doch", "sowie", "bzw")
    val questionWords = setOf(
        "wer", "was", "wann", "wo", "wie", "warum", "weshalb", "wieso", "weswegen", "woher", "wohin", "womit", "wofür",
        "worüber", "woran", "worauf", "wovon", "wozu", "wem", "wen", "wessen", "welche", "welcher", "welches", "welchen",
        "welchem",
    )
    /** Adverbs that fill the first place of a main clause on their own. */
    val prefieldAdverbs = setOf(
        "heute", "gestern", "morgen", "dann", "jetzt", "hier", "da", "dort", "deshalb", "deswegen", "darum", "trotzdem",
        "leider", "vielleicht", "bald", "immer", "oft", "nie", "niemals", "schon", "gerade", "grade", "also", "natürlich",
        "eigentlich", "sonst", "danach", "davor", "damals", "endlich", "plötzlich", "zuerst", "später", "nachher", "vorher",
        "manchmal", "meistens", "wahrscheinlich", "sicher", "bestimmt", "außerdem", "dafür", "dazu", "daher", "hoffentlich",
        "übrigens", "gleich", "sofort", "wieder", "abends", "morgens", "mittags", "nachts", "inzwischen", "mittlerweile",
        "irgendwann", "jedenfalls", "zumindest", "bisher", "wo", "wann", "wie", "warum", "weshalb", "wieso", "woher", "wohin",
    )
    /** Words right after a subject pronoun that belong to it ("Ich selbst habe", "Wir alle sind"). */
    val appositives = setOf("selbst", "selber", "allein", "alle", "beide", "auch", "nur", "zusammen", "persönlich", "aber",
        "hingegen", "dagegen", "wiederum", "jedenfalls", "zumindest")
    val copulas = setOf("sein", "werden", "bleiben", "heißen", "scheinen")
    /** Verbs after which a subjunctive I reports what someone said. */
    val reportingVerbs = setOf("sagen", "meinen", "behaupten", "erzählen", "erklären", "schreiben", "berichten", "betonen",
        "fragen", "antworten", "mitteilen", "versichern", "vermuten", "glauben", "denken")
    /** Words in a sentence that mark reported speech on their own. */
    val reportMarkers = setOf("angeblich", "laut", "offenbar", "anscheinend")

    /** Common abbreviations (lower case, without the point). */
    val abbreviations = setOf("usw", "bzw", "etc", "ca", "vgl", "evtl", "ggf", "inkl", "zzgl", "bspw", "dr", "nr", "str",
        "tel", "mio", "mrd", "min", "max", "std", "abs", "geb", "jh", "jhd", "prof", "hr", "fr", "uvm", "usf", "mfg", "lg", "vg")
}
