// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 36: personal templates (the user's handwriting of known symbols). */
class PersonalInkSymbolsTest {
    private fun cloud(seed: Int) = InkCloud.of(listOf(InkStroke.of(0f, 0f, 100f, seed.toFloat())))!!

    @Test fun atMostThreePerSymbolAndTheNewestWin() {
        // review M2: 3 per symbol and 40 symbols (was 5 and 80), every template costs matching time
        assertEquals(3, PersonalInkSymbols.PER_SYMBOL)
        assertEquals(40, PersonalInkSymbols.MAX_SYMBOLS)
        var symbols = emptyList<OwnInkSymbols.Symbol>()
        repeat(7) { symbols = PersonalInkSymbols.add(symbols, "→", cloud(it)) }
        val arrow = symbols.single()
        assertEquals(PersonalInkSymbols.PER_SYMBOL, arrow.samples.size)
        // the four oldest went: the last kept drawing is the newest
        assertEquals(cloud(6).ys.toList(), arrow.samples.last().ys.toList())
        assertEquals(cloud(4).ys.toList(), arrow.samples.first().ys.toList())
    }

    @Test fun aDrawingWithDotsIsKeptOnlyForASymbolWithDots() {
        // review M3: a stray tap with a dash must not become a dash with a dot (InkDots, P113)
        val dashWithDot = InkCloud.of(listOf(InkStroke.of(0f, 50f, 100f, 50f), InkStroke.of(50f, 10f)))!!
        assertEquals(1, dashWithDot.dotCount)
        assertTrue(PersonalInkSymbols.add(emptyList(), "–", dashWithDot).isEmpty())
        assertTrue(PersonalInkSymbols.add(emptyList(), "–", dashWithDot, symbolHasDots = false).isEmpty())
        assertEquals(listOf("÷"), PersonalInkSymbols.add(emptyList(), "÷", dashWithDot, symbolHasDots = true).map { it.text })
        assertTrue(PersonalInkSymbols.hasDots(InkSymbols.templates, "÷"))
        assertTrue(!PersonalInkSymbols.hasDots(InkSymbols.templates, "–"))
        // a drawing without dots is kept for any symbol
        assertEquals(1, PersonalInkSymbols.drawings(PersonalInkSymbols.add(emptyList(), "÷", cloud(1))))
    }

    @Test fun theSymbolChangedLongestAgoGoesFirst() {
        var symbols = emptyList<OwnInkSymbols.Symbol>()
        repeat(PersonalInkSymbols.MAX_SYMBOLS) { symbols = PersonalInkSymbols.add(symbols, "s$it", cloud(it)) }
        symbols = PersonalInkSymbols.add(symbols, "s0", cloud(1)) // s0 changed again: now the newest
        symbols = PersonalInkSymbols.add(symbols, "new", cloud(2))
        assertEquals(PersonalInkSymbols.MAX_SYMBOLS, symbols.size)
        assertTrue(symbols.none { it.text == "s1" })
        assertEquals(listOf("s0", "new"), symbols.takeLast(2).map { it.text })
    }

    @Test fun storedLikeOwnSymbolsAndReadBack() {
        var symbols = emptyList<OwnInkSymbols.Symbol>()
        repeat(5) { symbols = PersonalInkSymbols.add(symbols, "≠", cloud(it)) }
        symbols = PersonalInkSymbols.add(symbols, "%", cloud(9)) // escaped in the format
        val encoded = PersonalInkSymbols.encode(symbols)
        val parsed = PersonalInkSymbols.parse(encoded)
        assertEquals(listOf("≠" to 3, "%" to 1), parsed.map { it.text to it.samples.size })
        assertTrue(InkRecognizer.greedyCloudMatch(symbols[0].samples[2], parsed[0].samples[2]) < 0.05f)
        // a line with more drawings than allowed was not written by this code: skipped, the rest kept
        val line = encoded.lines()[1]
        val tooMany = "v1\n" + line + "\t" + line.substringAfter('\t') + "\n" + encoded.lines()[2]
        assertEquals(listOf("%"), PersonalInkSymbols.parse(tooMany).map { it.text })
        assertTrue(PersonalInkSymbols.parse("v1\n≠\tbroken").isEmpty())
        assertTrue(PersonalInkSymbols.parse(null).isEmpty())
    }

    @Test fun removeAndRefusedTexts() {
        var symbols = PersonalInkSymbols.add(emptyList(), "→", cloud(1))
        symbols = PersonalInkSymbols.add(symbols, "", cloud(2)) // no text: refused
        symbols = PersonalInkSymbols.add(symbols, "123456789", cloud(2)) // too long: refused
        assertEquals(listOf("→"), symbols.map { it.text })
        assertTrue(PersonalInkSymbols.remove(symbols, "→").isEmpty())
    }

    @Test fun aPersonalTemplateWinsOverABuiltInOne() {
        // a hand that draws ≠ with a short, steep slash far right: by default close to ⇒ or =
        val mine = listOf(InkStroke.of(0f, 40f, 100f, 40f), InkStroke.of(0f, 60f, 100f, 60f), InkStroke.of(80f, 75f, 88f, 25f))
        val random = Random(3)
        fun wobbly() = mine.map { s -> InkStroke(FloatArray(s.coordinates.size) { s.coordinates[it] + random.nextDouble(-1.5, 1.5).toFloat() }) }
        val personal = PersonalInkSymbols.add(emptyList(), "≠", InkCloud.of(mine)!!)
        val recognizer = InkRecognizer(PersonalInkSymbols.templates(personal) + InkSymbols.templates)
        repeat(20) { assertEquals("≠", recognizer.recognize(wobbly()).best?.text) }
    }
}
