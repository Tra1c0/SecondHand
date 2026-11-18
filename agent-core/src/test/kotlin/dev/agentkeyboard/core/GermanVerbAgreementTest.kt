// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.io.File

class GermanVerbAgreementTest {
    private val context = sequenceOf("nachträglich", "ich", "dass")
    private val verbs by lazy {
        sequenceOf(
            File("app/src/main/assets/agent/german_verb_lemmas.txt"),
            File("../app/src/main/assets/agent/german_verb_lemmas.txt"),
        ).first { it.isFile }.useLines { it.filterNot { line -> line.startsWith('#') }.toSet() }
    }

    private fun prefer(typed: String, first: String, alternatives: Sequence<String>, words: Sequence<String>,
                       typedIsKnown: () -> Boolean = { false }, after: () -> CharSequence?): String? =
        GermanVerbAgreement.prefer(typed, first, alternatives, words, verbs::contains, typedIsKnown, after)

    @Test fun preservesAnAlreadyTypedVerbForm() {
        assertEquals("korrigiere", prefer("korrigiere", "korrigieren", emptySequence(), context) {
            error("Preserving an explicit form needs no editor read")
        })
        assertEquals("korrigieren", prefer("korrigieren", "korrigiere", emptySequence(), context) { null })
    }

    @Test fun recoversATypoUsingTheSubjectAndAnExistingCandidate() {
        assertEquals("korrigiere", prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"), context) { "" })
        assertEquals("sortierst", prefer("sorteirst", "sortieren", sequenceOf("sortierst"),
            sequenceOf("jetzt", "du")) { "" })
    }

    @Test fun anExistingModalCanRequireTheInfinitive() {
        assertEquals("korrigieren", prefer("korrigeiren", "korrigiere", sequenceOf("korrigieren"),
            context) { " muss." })
        assertEquals("korrigieren", prefer("korrigeiren", "korrigiere", sequenceOf("korrigieren"),
            sequenceOf("später", "muss", "ich")) { "" })
        assertEquals("korrigiere", prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            context) { ". Muss das sein?" })
    }

    @Test fun handlesDifferentVerbsPersonsAndEndings() {
        data class Case(val typed: String, val first: String, val correct: String, val left: List<String>)
        val cases = listOf(
            Case("prüfstt", "prüfen", "prüfst", listOf("gerade", "du")),
            Case("sortireet", "sortieren", "sortiert", listOf("ihr")),
            Case("ärtbeitest", "arbeiten", "arbeitest", listOf("heute", "du")),
            Case("wartte", "warten", "wartet", listOf("noch", "es")),
            Case("taznt", "tanzen", "tanzt", listOf("wieder", "du")),
            Case("speichren", "speichert", "speichern", listOf("gleich", "wir")),
            Case("foedern", "fordert", "fordern", listOf("müssen", "sie")),
        )
        for (case in cases) {
            assertEquals(case.correct, prefer(case.typed, case.first, sequenceOf(case.correct),
                case.left.asSequence()) { "" }, case.typed)
        }
    }

    @Test fun preservesAKnownAlternativeWithoutAssumingASubject() {
        assertEquals("prüfen", prefer("prüfen", "prüft", emptySequence(),
            sequenceOf("alle", "Beteiligten"), typedIsKnown = { true }) { error("No editor read needed") })
        assertNull(prefer("prüfen", "prüft", emptySequence(),
            sequenceOf("alle", "Beteiligten")) { error("No editor read needed") })
        assertNull(prefer("spieltt", "spielen", sequenceOf("spielt"),
            sequenceOf("sie")) { "" })
    }

    @Test fun leavesUnrelatedOrAmbiguousSuggestionsAlone() {
        assertNull(prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            sequenceOf("nachträglich")) { error("No clear subject") })
        assertNull(prefer("korrigeire", "korrigieren", sequenceOf("verbessere"),
            context) { error("No competing form") })
        assertNull(prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            context) { null })
        assertNull(prefer("blume", "Blumen", emptySequence(), context) { null })
        assertNull(prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            sequenceOf("das", "habe", "ich")) { error("Another verb already occupies the predicate") })
    }

    @Test fun doesNotTreatPossessivesAsVerbFormsOrGuessBeforeAnExistingNoun() {
        assertNull(prefer("seinn", "seinen", sequenceOf("seine"), context) { error("No verb family") })
        assertNull(prefer("seinn", "seinen", sequenceOf("seine"), context) { " Hund sehe" })
        assertNull(prefer("meinn", "meinen", sequenceOf("meine"), context) { "" })
        assertNull(prefer("reisenn", "reisen", sequenceOf("reise"), context) { " gerne möchte" })
        assertNull(prefer("liebbe", "liebe", sequenceOf("lieben"), sequenceOf("mag", "ich")) { " Menschen" })
        assertNull(prefer("liebbe", "liebe", sequenceOf("lieben"), sequenceOf("kenne", "ich")) { " Menschen" })
        assertNull(prefer("liebbe", "liebe", sequenceOf("lieben"), sequenceOf("will", "ich")) { " Menschen" })
        assertEquals("korrigiere", prefer("korrigeire", "korrigieren", sequenceOf("korrigiere"),
            sequenceOf("es", "ich")) { "" })
    }
}
