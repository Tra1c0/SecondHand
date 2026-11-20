// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*

/**
 * Stage 83: a drawn symbol must be logical. A symbol the drawing lacks strokes of ("%" for a single slash,
 * "÷" for a dash, "€" for the arc alone) is never the best match; a bare slash is "/", a bare backslash "\".
 */
class InkLogicTest {
    private val recognizer = InkRecognizer(InkSymbols.templates)

    /** A straight stroke at [degrees] above the horizontal (screen pixels, y down), [length] long, optionally reversed. */
    private fun straight(degrees: Double, length: Float = 220f, reversed: Boolean = false, tilt: Double = 0.0): InkStroke {
        val a = Math.toRadians(degrees + tilt)
        val dx = (cos(a) * length).toFloat(); val dy = (-sin(a) * length).toFloat()
        val from = floatArrayOf(100f - dx / 2, 100f - dy / 2, 100f, 100f, 100f + dx / 2, 100f + dy / 2)
        val points = if (reversed) floatArrayOf(from[4], from[5], from[2], from[3], from[0], from[1]) else from
        return InkStroke(points)
    }

    private fun best(vararg strokes: InkStroke) = recognizer.recognize(strokes.toList()).best?.text

    @Test fun aSlashStrokeIsASlashAndABackslashStrokeABackslash() {
        // bottom left to top right is a slash at positive angles (y grows downward on the screen)
        for (degrees in listOf(40.0, 50.0, 60.0, 70.0, 80.0)) for (reversed in listOf(false, true)) {
            val result = recognizer.recognize(listOf(straight(degrees, reversed = reversed)))
            assertEquals("/", result.best?.text, "slash $degrees° reversed=$reversed: ${result.candidates.take(4)}")
            val back = recognizer.recognize(listOf(straight(180 - degrees, reversed = reversed)))
            assertEquals("\\", back.best?.text, "backslash ${180 - degrees}° reversed=$reversed: ${back.candidates.take(4)}")
        }
    }

    @Test fun aHandDrawnSlashIsNeverPercent() {
        val random = Random(83)
        var total = 0; var slash = 0
        repeat(300) {
            val degrees = random.nextDouble(40.0, 80.0)
            val result = recognizer.recognize(listOf(straight(degrees, random.nextDouble(120.0, 400.0).toFloat(), random.nextBoolean(), random.nextDouble(-8.0, 8.0))))
            assertNotEquals("%", result.best?.text, "$degrees°: ${result.candidates.take(4)}")
            total++; if (result.best?.text == "/") slash++
        }
        assertTrue(slash >= total * 95 / 100, "$slash of $total slashes")
    }

    @Test fun noSingleStrokeIsPercentWhateverItsAngle() {
        for (degrees in 0..180 step 5) for (reversed in listOf(false, true)) {
            val result = recognizer.recognize(listOf(straight(degrees.toDouble(), reversed = reversed)))
            assertNotEquals("%", result.best?.text, "$degrees°: ${result.candidates.take(4)}")
            assertNotEquals("‰", result.best?.text)
            // a candidate the drawing is not complete for is only ever offered after the best match
            assertTrue(result.candidates.firstOrNull { !it.complete }?.let { it.text != result.best?.text } ?: true)
        }
    }

    @Test fun percentNeedsItsDotsOrCircles() {
        val slash = straight(60.0, 200f)
        assertEquals("%", best(slash, InkStroke.of(60f, 40f), InkStroke.of(140f, 160f)))
        assertNotEquals("%", best(slash, InkStroke.of(60f, 40f))) // one dot is no percent
        val circle = { cx: Float, cy: Float -> InkStroke(FloatArray(2 * 13) { k -> val t = 2 * Math.PI * (k / 2) / 12
            if (k % 2 == 0) (cx + 18 * cos(t)).toFloat() else (cy + 18 * sin(t)).toFloat() }) }
        assertEquals("%", best(circle(60f, 30f), straight(60.0, 200f), circle(140f, 170f)))
    }

    @Test fun otherPartialDrawingsAreNotTheWholeSymbol() {
        val dash = InkStroke.of(20f, 80f, 300f, 80f)
        assertEquals("–", best(dash))
        assertEquals("–", best(dash, InkStroke.of(160f, 40f))) // a dash and one dot is no ÷ (needs two)
        assertEquals("÷", best(dash, InkStroke.of(160f, 40f), InkStroke.of(160f, 120f)))
        val vertical = InkStroke.of(100f, 10f, 100f, 140f)
        assertEquals("|", best(vertical)) // not "!": it lacks its dot
        assertEquals("!", best(InkStroke.of(100f, 10f, 100f, 112f), InkStroke.of(100f, 140f)))
        assertEquals("=", best(InkStroke.of(20f, 60f, 300f, 60f), InkStroke.of(20f, 100f, 300f, 100f))) // not ≠ or ±
        // the arc of the euro sign alone is not a euro sign, the arc and a bar is
        val arc = InkStroke(FloatArray(2 * 20) { k -> val t = Math.toRadians(320.0 + 80.0 * (k / 2) / 19)
            if (k % 2 == 0) (160 + 90 * cos(t)).toFloat() else (100 + 100 * sin(t)).toFloat() })
        assertNotEquals("€", best(arc))
    }

    @Test fun anIncompleteCandidateIsNeverBestAndOnlyASecondaryOffer() {
        val result = InkResult(listOf(InkMatch("%", 0.5f, false, complete = false), InkMatch("/", 1.0f, false)))
        assertEquals("/", result.best?.text)
        assertEquals(listOf("%"), result.alternatives.map { it.text })
        // nothing complete close enough: no best match at all, not the incomplete one
        assertNull(InkResult(listOf(InkMatch("%", 0.5f, false, complete = false), InkMatch("/", 2.5f, false))).best)
        assertNull(InkResult(listOf(InkMatch("%", 0.5f, false, complete = false))).best)
    }

    /**
     * The general rule on every built-in drawing: leave strokes out of it and the result is never a symbol
     * that has no drawing with so few strokes (the audit of stage 83 found 65 such results before the rule).
     */
    @Test fun noPartialDrawingBecomesASymbolThatNeedsMoreStrokes() {
        val bad = ArrayList<String>()
        for (symbol in InkSymbols.all) for ((index, variant) in symbol.variants.withIndex()) {
            if (variant.size > 5) continue
            for (mask in 1 until (1 shl variant.size) - 1) {
                val subset = variant.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
                val best = recognizer.recognize(subset).best?.text ?: continue
                val needs = InkSymbols.all.first { it.text == best }.variants.minOf { it.size }
                if (needs > subset.size) bad += "${symbol.text}#$index $mask -> $best (needs $needs, drawn ${subset.size})"
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("; "))
    }

    @Test fun everyBuiltInSymbolStillNeedsAllItsStrokesButIsFoundWithThem() {
        for (symbol in InkSymbols.all) for (variant in symbol.variants) {
            val result = recognizer.recognize(variant)
            assertEquals(symbol.text, result.best?.text, "${symbol.text}: ${result.candidates.take(3)}")
            assertTrue(result.candidates.first { it.text == symbol.text }.complete)
        }
    }

    @Test fun anOwnSymbolDrawnInOneStrokeIsCompleteForOneStroke() {
        val own = InkTemplate("Z", InkCloud.of(listOf(InkStroke.of(0f, 0f, 100f, 0f, 0f, 100f, 100f, 100f)))!!, own = true)
        val result = InkRecognizer(InkSymbols.templates + own).recognize(listOf(InkStroke.of(0f, 0f, 100f, 0f, 0f, 100f, 100f, 100f)))
        assertEquals("Z", result.best?.text)
    }
}
