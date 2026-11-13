// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class OwnInkSymbolsTest {
    private val w = InkCloud.of(listOf(InkStroke.of(0f, 0f, 25f, 100f, 50f, 40f, 75f, 100f, 100f, 0f)))!!
    private val v = InkCloud.of(listOf(InkStroke.of(0f, 0f, 50f, 100f, 100f, 0f)))!!

    @Test fun roundTripKeepsTextAndDrawings() {
        val symbols = listOf(OwnInkSymbols.Symbol("👋", listOf(w, w, v)), OwnInkSymbols.Symbol("50%", listOf(v)))
        val parsed = OwnInkSymbols.parse(OwnInkSymbols.encode(symbols))
        assertEquals(listOf("👋", "50%"), parsed.map { it.text })
        assertEquals(3, parsed[0].samples.size)
        // the stored cloud matches like the original one
        val recognizer = InkRecognizer(parsed[0].templates())
        assertEquals("👋", recognizer.recognize(w).best?.text)
        assertTrue(InkRecognizer.greedyCloudMatch(w, parsed[0].samples[0]) < 0.05f)
    }

    @Test fun textRules() {
        assertNull(OwnInkSymbols.textProblem("€"))
        assertNull(OwnInkSymbols.textProblem("👨‍👩‍👧")) // 5 code points
        assertNull(OwnInkSymbols.textProblem("12345678"))
        assertEquals(OwnInkSymbols.TextProblem.TOO_LONG, OwnInkSymbols.textProblem("123456789"))
        assertEquals(OwnInkSymbols.TextProblem.EMPTY, OwnInkSymbols.textProblem(" "))
        assertEquals(OwnInkSymbols.TextProblem.EMPTY, OwnInkSymbols.textProblem(""))
        assertEquals(OwnInkSymbols.TextProblem.CONTROL, OwnInkSymbols.textProblem("a\nb"))
    }

    @Test fun brokenInputIsSkippedNeverThrown() {
        val good = OwnInkSymbols.encode(listOf(OwnInkSymbols.Symbol("x", listOf(v))))
        assertEquals(emptyList(), OwnInkSymbols.parse(null).map { it.text })
        assertEquals(emptyList(), OwnInkSymbols.parse("").map { it.text })
        assertEquals(emptyList(), OwnInkSymbols.parse("v2\nx\t0,0,0").map { it.text })
        val line = good.lines()[1]
        val broken = listOf(
            "v1", "nur text", "y\t1,2,3", "z\t" + line.substringAfter('\t').replaceFirst(",", ",x"),
            "%Z\t" + line.substringAfter('\t'), "%4\t" + line.substringAfter('\t'),
            "far\t" + line.substringAfter('\t').replaceFirst(Regex("^-?\\d+"), "5000"),
            "stroke\t" + line.substringAfter('\t').replaceFirst(Regex(",\\d+;"), ",99;"),
            line,
        ).joinToString("\n")
        assertEquals(listOf("x"), OwnInkSymbols.parse(broken).map { it.text })
        assertEquals(emptyList(), OwnInkSymbols.parse("v1\n" + "x".repeat(OwnInkSymbols.MAX_ENCODED_LENGTH)).map { it.text })
    }

    @Test fun limitsOnWrite() {
        val many = List(OwnInkSymbols.MAX_SYMBOLS + 5) { OwnInkSymbols.Symbol("s$it", listOf(v, v, v, v)) }
        val parsed = OwnInkSymbols.parse(OwnInkSymbols.encode(many))
        assertEquals(OwnInkSymbols.MAX_SYMBOLS, parsed.size)
        assertTrue(parsed.all { it.samples.size == OwnInkSymbols.SAMPLES })
        // invalid texts and empty drawings are not written
        val invalid = listOf(OwnInkSymbols.Symbol("", listOf(v)), OwnInkSymbols.Symbol("ok", emptyList()))
        assertEquals("v1", OwnInkSymbols.encode(invalid))
    }
}
