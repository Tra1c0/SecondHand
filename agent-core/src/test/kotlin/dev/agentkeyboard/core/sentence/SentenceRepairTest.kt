// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage 104 with the shipped lexicon and the shipped German main dictionary. [after] is what the field shows once the
 * check ran on [typed] (the text before the cursor after the last word ended); the owner's seven examples are cases of
 * general rules, the counter cases show where the rules must keep still.
 */
class SentenceRepairTest {
    private fun after(typed: String, context: SentenceContext = RealLexicon.Context(), lineBreak: Boolean = typed.endsWith("\n")): String {
        val edit = RetroCheck.check(typed, RealLexicon.lexicon, context, RetroCheck.Options(repair = true, punctuate = false,
            german = true, lineBreak = lineBreak)) ?: return typed
        assertEquals(typed.substring(edit.start), edit.old, "the edit describes the text as it stands")
        return typed.substring(0, edit.start) + edit.replacement
    }

    private fun stays(vararg typed: String) = typed.forEach { assertEquals(it, after(it), "must stay: $it") }

    // ------------------------------------------------------------ the owner's examples

    @Test fun `example 1 mir gehen es gut`() = assertEquals("Mir geht es gut ", after("Mir gehen es gut "))

    @Test fun `example 2 ich haben das nicht gewusst`() {
        assertEquals("Ich habe das ", after("Ich haben das "))
        assertEquals("Ich habe das nicht gewusst ", after("Ich haben das nicht gewusst "))
    }

    @Test fun `example 3 er habe mir ein Gefallen getan - the verb, the article stays open`() {
        // "Gefallen" is masculine (a favour) and neuter (pleasure) in the German Wiktionary: "ein Gefallen" is a grammatical
        // accusative of the neuter noun, so the article is not guessed (docs/NACHKORREKTUR-KONZEPT.md, section 3)
        assertEquals("Er hat mir ein Gefallen getan ", after("Er habe mir ein Gefallen getan "))
    }

    @Test fun `example 4 zu hause - the capital, seit stays open`() {
        // "Zeit gestern" is grammatical as far as the lexicon can tell; only word pair frequencies could decide it
        assertEquals("Ich bin Zeit gestern wieder zu Hause.", after("Ich bin Zeit gestern wieder zu hause."))
        assertEquals("Ich bin Zeit gestern wieder zu Hause und ", after("Ich bin Zeit gestern wieder zu hause und "))
    }

    @Test fun `example 5 ihr seit schon da`() = assertEquals("Ihr seid schon da ", after("Ihr seit schon da "))

    @Test fun `example 6 das machen kein Sinn - the verb once the phrase is over, keinen stays open`() {
        // "Das macht kein Mensch" has the same structure and is right: the article is a collocation, not grammar
        assertEquals("Das macht kein Sinn.", after("Das machen kein Sinn."))
        assertEquals("Das macht kein Sinn mehr ", after("Das machen kein Sinn mehr "))
        // right after "Sinn" an "und …" could still make the subject plural
        stays("Das machen kein Sinn ")
    }

    @Test fun `example 7 mit der Polen Zeit`() {
        assertEquals("Ich war mit der Polizei ", after("Ich war mit der Polen Zeit "))
        assertEquals("mit der Polizei ", after("mit der Polen Zeit "))
    }

    // ------------------------------------------------------------ the same rules on other sentences (no special cases)

    @Test fun `subject and verb in other sentences`() {
        assertEquals("Du hast recht ", after("Du habt recht "))
        assertEquals("Wir gehen jetzt ", after("Wir geht jetzt "))
        assertEquals("Heute habe ich keine Zeit ", after("Heute haben ich keine Zeit "))
        assertEquals("Er gibt mir das ", after("Er geben mir das "))
        assertEquals("Ich hatte keine Ahnung ", after("Ich hatten keine Ahnung "))
        assertEquals("Hast du Zeit ", after("Haben du Zeit "))
        assertEquals("Er vergibt mir nie ", after("Er vergeben mir nie "))
    }

    @Test fun `articles after a preposition`() {
        assertEquals("Ich komme mit dem Hund mit ", after("Ich komme mit der Hund mit "))
        assertEquals("Das ist für den Mann.", after("Das ist für dem Mann."))
        assertEquals("Ich war bei meinem Bruder und ", after("Ich war bei mein Bruder und "))
        // right after the noun a second capital word could make it a name or an adjective ("von der Zürcher Prognos AG")
        stays("Ich komme mit der Hund ", "Heimfrid Wolff von der Zürcher Prognos AG ")
    }

    @Test fun `a compound typed as two words`() = assertEquals("Die Haustür ist offen ", after("Die Haus Tür ist offen "))

    @Test fun `das becomes dass before a clause`() {
        assertEquals("Ich glaube dass er kommt.", after("Ich glaube das er kommt."))
        assertEquals("Ich weiß, dass du recht hast.", after("Ich weiß, das du recht hast."))
    }

    // ------------------------------------------------------------ what must stay

    @Test fun `correct sentences stay`() = stays(
        "Sie haben das ", "Wir haben das nicht gewusst ", "Mir geht es gut ", "Ich habe das nicht gewusst ",
        "Das macht keinen Sinn mehr ", "Ihr seid schon da ", "Ich bin seit gestern wieder zu Hause.", "Er hat mir einen Gefallen getan ",
        "Das macht kein Mensch freiwillig ", "Das Buch, das er liest, ist gut.", "Ich komme mit den Polen ",
        "Es gibt viele Orte ", "Es kommen viele Leute ", "Kinder spielen gern ", "Das gefällt ihr bestimmt ",
        "Jetzt hat ihr Freund Schluss gemacht ", "Um ein Uhr komme ich ", "Wir treffen uns um ein Uhr ",
        "Ich war im letzten Jahr dort ", "Man nehme zwei Eier ",
    )

    @Test fun `a subject that may still come stays undecided`() = stays(
        "Dann machen es die anderen ", "Dann machen es ", "Das machen ", "Das machen wir ", "Die sehen richtig gut aus ",
        "Dann kommen mein Bruder und meine Schwester ", "Morgen kommen die Jungs weil ich Geburtstag habe ",
        "Gestern haben meine Eltern gesagt ich soll kommen ", "Gestern waren Max und ich im Kino ", "Heute gehen Lisa und ich ",
        "Ich haben ",
    )

    @Test fun `clauses without commas are not misread`() = stays(
        "Lass das ich mach das schon ", "Ich habe das ich weiß nicht mehr wo ", "Ich glaube das er kommt ",
    )

    @Test fun `ellipses, imperatives and wishes stay`() = stays(
        "Wir seit gestern hier ", "Ich auch ", "Gehe du voran ", "Kameraden singt!", "Er bis morgen weg ",
    )

    @Test fun `names, slang, other languages stay`() {
        stays("Max haben wir gestern gesehen ", "Digga was geht ab ", "wallah ich schwöre ", "I have no idea what das ist ",
            "Yo tengo que ir ahora ", "Reiser fuhr nach Hause ")
        // a protected word (own word, slang, personal dictionary) is never changed
        assertEquals("Ich haben das ", after("Ich haben das ", RealLexicon.Context(protected = setOf("haben"))))
    }

    @Test fun `a change the owner took back is not made again`() {
        assertEquals("Ich haben das ", after("Ich haben das ", RealLexicon.Context(rejected = setOf("haben" to "habe"))))
        assertEquals("Ihr seit schon da ", after("Ihr seit schon da ", RealLexicon.Context(rejected = setOf("seit" to "seid"))))
    }

    @Test fun `only the last sentence is judged`() {
        assertEquals("Ich haben das. Mir geht es gut ", after("Ich haben das. Mir gehen es gut "))
    }

    @Test fun `links, code and numbers are not judged`() = stays(
        "Ich haben das auf www.example.com ", "Ich haben das = gut ", "Ich haben das @max ",
    )
}
