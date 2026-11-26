// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

import kotlin.test.Test
import kotlin.test.assertEquals

/** Stage 105 with the shipped lexicon and dictionary, alone and together with stage 104 (one edit, no double marks). */
class AutoPunctuationTest {
    private fun after(typed: String, repair: Boolean = false, german: Boolean = true, context: SentenceContext = RealLexicon.Context()): String {
        val edit = RetroCheck.check(typed, RealLexicon.lexicon, context, RetroCheck.Options(repair = repair, punctuate = true,
            german = german, lineBreak = typed.endsWith("\n"))) ?: return typed
        assertEquals(typed.substring(edit.start), edit.old)
        return typed.substring(0, edit.start) + edit.replacement
    }

    private fun stays(vararg typed: String) = typed.forEach { assertEquals(it, after(it), "must stay: ${it.replace("\n", "⏎")}") }

    @Test fun `comma before a subordinate clause once it is over`() {
        assertEquals("Ich weiß, dass er kommt.", after("Ich weiß dass er kommt."))
        assertEquals("Ich weiß, dass er kommt und ", after("Ich weiß dass er kommt und "))
        assertEquals("Ich weiß nicht, ob sie das schafft\n", after("Ich weiß nicht ob sie das schafft\n").replace(".\n", "\n"))
        assertEquals("Wir bleiben hier, weil es regnet.", after("Wir bleiben hier weil es regnet."))
        assertEquals("Ich warte, bis er kommt.", after("Ich warte bis er kommt."))
        assertEquals("Es ist größer, als ich dachte.", after("Es ist größer als ich dachte."))
        assertEquals("Ich bin müde, auch wenn ich geschlafen habe.", after("Ich bin müde auch wenn ich geschlafen habe."))
        assertEquals("Ich gehe, ohne dass er es merkt.", after("Ich gehe ohne dass er es merkt."))
    }

    @Test fun `the comma waits until the clause is over`() = stays("Ich weiß dass er kommt ", "Ich weiß dass er ")

    @Test fun `comma after a clause in front`() {
        assertEquals("Wenn du kommst, bringe ich Kuchen mit ", after("Wenn du kommst bringe ich Kuchen mit "))
        assertEquals("Wenn du kommst, bringe Kuchen mit ", after("Wenn du kommst bringe Kuchen mit "))
        stays("Wenn du kommen kannst ", "Wenn ein Spiel verspätet beginnt erst ")
    }

    @Test fun `no comma where none belongs`() = stays(
        "Ich bin damit zufrieden.", "Ich war gestern da er war auch da.", "Bis morgen.", "Das ist so gut wie neu.",
        "Er arbeitet als Lehrer in Berlin.", "Ich komme mit, wenn du willst.", "Ich weiß, dass er kommt.",
        "Hör auf damit er nervt eh ", "Seit gestern regnet es.",
    )

    @Test fun `full stop and question mark at the last word before a new line`() {
        assertEquals("Ich komme morgen vorbei.\n", after("Ich komme morgen vorbei\n"))
        assertEquals("Kommst du morgen?\n", after("Kommst du morgen\n"))
        assertEquals("Wie geht es dir?\n", after("Wie geht es dir\n"))
        assertEquals("Wo bist du gerade?\n", after("Wo bist du gerade\n"))
        assertEquals("Ich komme morgen vorbei. \n", after("Ich komme morgen vorbei \n"))
        assertEquals("Hallo Max\nIch komme morgen vorbei.\n", after("Hallo Max\nIch komme morgen vorbei\n"))
    }

    @Test fun `no mark where it does not belong`() = stays(
        "- Milch kaufen\n", "1. Brot holen und essen\n", "* wir gehen dann\n", "Liebe Grüße\n", "Einkaufsliste für morgen\n",
        "Ich komme morgen.\n", "Kommst du morgen?\n", "Wir sehen uns!\n", "Ich komme um 15\n", "Schau mal auf www.example.com\n",
        "Ich komme morgen 😀\n", "Das war usw\n", "Ich habe einen PKW\n", "Kann ich nur empfehlen\n", "Kaufen Sie bei uns\n",
        "if (a == b) return x\n", "Ich komme morgen vorbei",
    )

    @Test fun `english on the German keyboard and the English keyboard`() {
        assertEquals("Are you coming tomorrow?\n", after("Are you coming tomorrow\n"))
        assertEquals("What do you think?\n", after("What do you think\n"))
        assertEquals("I will be there soon.\n", after("I will be there soon\n", german = false))
        stays("Yo tengo que ir ahora\n")
    }

    @Test fun `a mark the owner took back does not come again`() {
        val context = RealLexicon.Context(rejected = setOf("vorbei" to "vorbei.", "weiß" to "weiß,"))
        assertEquals("Ich komme morgen vorbei\n", after("Ich komme morgen vorbei\n", context = context))
        assertEquals("Ich weiß dass er kommt.", after("Ich weiß dass er kommt.", context = context))
    }

    @Test fun `stages 104 and 105 in one sentence make one edit without double marks`() {
        assertEquals("Ich glaube, dass er kommt.\n", after("Ich glaube das er kommt\n", repair = true))
        assertEquals("Ich habe das nicht gewusst.\n", after("Ich haben das nicht gewusst\n", repair = true))
        assertEquals("Mir geht es gut.\n", after("Mir gehen es gut\n", repair = true))
        assertEquals("Ihr seid schon da?\n".replace("?", "."), after("Ihr seit schon da\n", repair = true))
        assertEquals("Ich weiß, dass du recht hast.\n", after("Ich weiß, das du recht hast\n", repair = true))
        val edit = RetroCheck.check("Ich glaube das er kommt\n", RealLexicon.lexicon, RealLexicon.Context(),
            RetroCheck.Options(repair = true, punctuate = true, german = true, lineBreak = true))!!
        assertEquals(listOf("das" to "dass", "glaube" to "glaube,", "kommt" to "kommt."), edit.pairs)
        assertEquals("glaube das er kommt\n", edit.old)
    }
}
