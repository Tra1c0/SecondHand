// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

import dev.agentkeyboard.core.sentence.GermanNouns.Companion.ACC
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.DAT
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.FEM
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.MASC
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.NEUT
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.NOM
import dev.agentkeyboard.core.sentence.GermanNouns.Companion.bit
import dev.agentkeyboard.core.sentence.GermanVerbs.Mood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stages 104/105: the shipped lexicon files and the regular pattern of GermanVerbs. */
class GermanLexiconTest {
    private val verbs = RealLexicon.lexicon.verbs
    private val nouns = RealLexicon.lexicon.nouns

    @Test fun `present tense of regular and irregular verbs`() {
        assertEquals(listOf("mache", "machst", "macht", "machen", "macht", "machen"), (1..6).map { verbs.form("machen", Mood.PRESENT, it) })
        assertEquals(listOf("arbeite", "arbeitest", "arbeitet"), (1..3).map { verbs.form("arbeiten", Mood.PRESENT, it) })
        assertEquals(listOf("bin", "bist", "ist", "sind", "seid", "sind"), (1..6).map { verbs.form("sein", Mood.PRESENT, it) })
        assertEquals(listOf("habe", "hast", "hat", "haben", "habt", "haben"), (1..6).map { verbs.form("haben", Mood.PRESENT, it) })
        assertEquals(listOf("gebe", "gibst", "gibt"), (1..3).map { verbs.form("geben", Mood.PRESENT, it) })
        assertEquals("vergibt", verbs.form("vergeben", Mood.PRESENT, 3))
        assertEquals("tut", verbs.form("tun", Mood.PRESENT, 3))
        assertEquals("ging", verbs.form("gehen", Mood.PAST, 3))
        assertEquals("hätte", verbs.form("haben", Mood.SUBJUNCTIVE_2, 1))
        assertEquals("sammelt", verbs.form("sammeln", Mood.PRESENT, 3))
    }

    @Test fun `readings of finite forms`() {
        assertTrue(verbs.readings("geht").any { it.lemma == "gehen" && it.slot == 3 && it.mood == Mood.PRESENT })
        assertTrue(verbs.readings("seid").any { it.lemma == "sein" && it.slot == 5 })
        assertTrue(verbs.readings("Habe").any { it.lemma == "haben" && it.slot == 1 })
        assertTrue(verbs.readings("seit").isEmpty())
        assertTrue(verbs.readings("Zeit").isEmpty())
        assertTrue(verbs.readings("gewusst").isEmpty(), "a participle is no finite form")
    }

    /** The forms of the common irregular verbs are words of the German main dictionary (no invented forms). */
    @Test fun `the common irregular forms are dictionary words`() {
        val common = listOf("sein", "haben", "werden", "können", "müssen", "sollen", "wollen", "dürfen", "mögen", "wissen", "geben",
            "nehmen", "sehen", "lesen", "essen", "fahren", "laufen", "schlafen", "tragen", "fallen", "halten", "lassen", "helfen",
            "sprechen", "treffen", "vergessen", "gehen", "kommen", "stehen", "finden", "bleiben", "liegen", "denken", "bringen", "tun")
        common.forEach { assertTrue(verbs.isListed(it), "$it comes from the file") }
        // the main dictionary lacks some real forms ("nimmst", "fährst"), so the forms are compared with the paradigm
        assertEquals(listOf("nehme", "nimmst", "nimmt", "nehmen", "nehmt", "nehmen"), (1..6).map { verbs.form("nehmen", Mood.PRESENT, it) })
        assertEquals(listOf("fahre", "fährst", "fährt"), (1..3).map { verbs.form("fahren", Mood.PRESENT, it) })
        assertEquals(listOf("stand", "lag", "lief", "kam", "fand", "blieb", "dachte", "brachte", "tat"),
            listOf("stehen", "liegen", "laufen", "kommen", "finden", "bleiben", "denken", "bringen", "tun").map { verbs.form(it, Mood.PAST, 1) })
        // most forms the file adds are words of the main dictionary (UniMorph is the source, the dictionary a cross-check)
        val forms = common.flatMap { lemma -> (1..6).mapNotNull { verbs.form(lemma, Mood.PRESENT, it) } }
        val known = forms.count { (RealLexicon.german[it] ?: -1) >= 0 }
        assertTrue(known * 10 >= forms.size * 8, "dictionary knows $known of ${forms.size}")
        val file = RealLexicon.repoFile("app/src/main/assets/agent/german_verb_forms.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(file.size > 300, "the irregular verbs: ${file.size}")
    }

    @Test fun `nouns with gender and case`() {
        assertTrue(nouns.size > 30_000)
        val polizei = nouns.readings("Polizei")
        assertTrue(polizei and bit(FEM, DAT) != 0 && polizei and bit(MASC, NOM) == 0)
        assertTrue(nouns.readings("Hause") and bit(NEUT, DAT) != 0)
        val gefallen = nouns.readings("Gefallen")
        assertTrue(gefallen and bit(MASC, ACC) != 0 && gefallen and bit(NEUT, ACC) != 0, "both genders of Gefallen")
        assertEquals(0, nouns.readings("Berlin"), "no names")
        assertTrue(nouns.withPrefix("Poliz").contains("Polizei"))
    }

    @Test fun `typing distance`() {
        assertEquals(3, SentenceRepair.distance("polenzeit", "polizei"))
        assertEquals(1, SentenceRepair.distance("seit", "zeit"))
        assertEquals(1, SentenceRepair.distance("hasu", "haus"))
    }
}
