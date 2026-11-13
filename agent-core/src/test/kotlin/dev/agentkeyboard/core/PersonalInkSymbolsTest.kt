// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 36: personal templates (the user's handwriting of known symbols). */
class PersonalInkSymbolsTest {
    private fun cloud(seed: Int) = InkCloud.of(listOf(InkStroke.of(0f, 0f, 100f, seed.toFloat())))!!

    @Test fun atMostFivePerSymbolAndTheNewestWin() {
        var symbols = emptyList<OwnInkSymbols.Symbol>()
        repeat(7) { symbols = PersonalInkSymbols.add(symbols, "→", cloud(it)) }
        val arrow = symbols.single()
        assertEquals(PersonalInkSymbols.PER_SYMBOL, arrow.samples.size)
        // the two oldest went: the last kept drawing is the newest
        assertEquals(cloud(6).ys.toList(), arrow.samples.last().ys.toList())
        assertEquals(cloud(2).ys.toList(), arrow.samples.first().ys.toList())
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
        val parsed = PersonalInkSymbols.parse(PersonalInkSymbols.encode(symbols))
        assertEquals(listOf("≠" to 5, "%" to 1), parsed.map { it.text to it.samples.size })
        assertTrue(InkRecognizer.greedyCloudMatch(symbols[0].samples[3], parsed[0].samples[3]) < 0.05f)
        // own symbols keep their limit of three drawings: a personal line is not an own symbol
        assertTrue(OwnInkSymbols.parse(PersonalInkSymbols.encode(symbols)).none { it.text == "≠" })
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
