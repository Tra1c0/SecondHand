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
        for (degrees in listOf(35.0, 45.0, 55.0, 65.0, 70.0)) for (reversed in listOf(false, true)) {
            val result = recognizer.recognize(listOf(straight(degrees, reversed = reversed)))
            assertEquals("/", result.best?.text, "slash $degrees° reversed=$reversed: ${result.candidates.take(4)}")
            val back = recognizer.recognize(listOf(straight(180 - degrees, reversed = reversed)))
            assertEquals("\\", back.best?.text, "backslash ${180 - degrees}° reversed=$reversed: ${back.candidates.take(4)}")
        }
    }

    @Test fun aSlightlyTiltedDashOrVerticalStaysADashOrBar() {
        for (tilt in listOf(-10.0, -5.0, 0.0, 5.0, 10.0)) for (reversed in listOf(false, true)) {
            assertEquals("–", best(straight(tilt, reversed = reversed)), "dash $tilt°")
            assertEquals("|", best(straight(90 + tilt, reversed = reversed)), "bar ${90 + tilt}°")
        }
    }

    @Test fun aHandDrawnSlashIsNeverPercent() {
        val random = Random(83)
        var total = 0; var slash = 0
        repeat(300) {
            val degrees = random.nextDouble(35.0, 70.0)
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

    @Test fun aCompleteCandidateBeatsAnIncompleteOneButNotWhenNoneIsAccepted() {
        // "/" is complete and accepted: "%" (incomplete, closer in raw distance) is only an alternative
        val slash = InkResult(listOf(InkMatch("%", 0.5f, false, complete = false), InkMatch("/", 1.0f, false)))
        assertEquals("/", slash.best?.text)
        assertEquals(listOf("%"), slash.alternatives.map { it.text })
        // no complete candidate accepted (too far): the closest incomplete one is the best match (a hook is a "?")
        val hook = InkResult(listOf(InkMatch("?", 1.0f, false, complete = false), InkMatch("(", 2.5f, false)))
        assertEquals("?", hook.best?.text)
        // nothing accepted at all
        assertNull(InkResult(listOf(InkMatch("%", 2.45f, false, complete = false), InkMatch("/", 2.7f, false))).best)
        // a sloppy complete candidate (accepted only as the clear one) gives way to a close incomplete one
        assertEquals("?", InkResult(listOf(InkMatch("?", 1.5f, false, complete = false), InkMatch("°", 2.2f, false))).best?.text)
        assertEquals("°", InkResult(listOf(InkMatch("?", 1.0f, false, complete = false), InkMatch("°", 1.5f, false))).best?.text)
        // alternatives do not need a best match: the incomplete ones are offered, a complete one that was not accepted is not
        val none = InkResult(listOf(InkMatch("%", 2.45f, false, complete = false), InkMatch("/", 2.5f, false)))
        assertNull(none.best)
        assertEquals(listOf("%"), none.alternatives.map { it.text })
    }

    // --- stage 83 follow-up: symbols people draw with fewer strokes than the first template must still be found ---

    /** A polyline in the 100 box, enlarged to finger size. */
    private fun poly(vararg xy: Number) = InkStroke(FloatArray(xy.size) { xy[it].toFloat() * 2.5f })

    private fun arc(cx: Double, cy: Double, rx: Double, ry: Double, from: Double, to: Double): List<Number> {
        val points = ArrayList<Number>()
        for (k in 0..24) { val a = Math.toRadians(from + (to - from) * k / 24); points += cx + rx * cos(a); points += cy + ry * sin(a) }
        return points
    }

    private fun draw(vararg parts: List<Number>) = parts.map { poly(*it.toTypedArray()) }

    private fun bestOf(strokes: List<InkStroke>) = recognizer.recognize(strokes).best?.text

    @Test fun symbolsDrawnWithFewerStrokesThanTheirFirstTemplateAreStillFound() {
        val hook = arc(50.0, 27.0, 28.0, 25.0, 195.0, 405.0) + listOf(50, 60, 50, 76)
        assertEquals("?", bestOf(draw(hook)), "? without its dot")
        assertEquals("¿", bestOf(draw(hook.map { 100 - it.toDouble() })), "¿ without its dot")
        assertEquals("+", bestOf(draw(listOf(50, 0, 50, 100, 50, 50, 0, 50, 100, 50))), "+ in one stroke")
        assertEquals("×", bestOf(draw(listOf(0, 0, 100, 100, 50, 50, 100, 0, 0, 100))), "× in one stroke")
        assertEquals("=", bestOf(draw(listOf(0, 34, 100, 34, 0, 66, 100, 66))), "= in one stroke")
        assertEquals("#", bestOf(draw(listOf(38, 0, 26, 100), listOf(76, 0, 64, 100), listOf(0, 32, 100, 32, 0, 68, 100, 68))), "# in three strokes")
        assertEquals("↗", bestOf(draw(listOf(0, 100, 100, 0, 50, 0, 100, 0, 100, 50))), "↗ in one stroke")
        assertEquals("↘", bestOf(draw(listOf(0, 0, 100, 100, 50, 100, 100, 100, 100, 50))), "↘ in one stroke")
        val circle = { cx: Double, cy: Double -> arc(cx, cy, 15.0, 15.0, 0.0, 360.0) }
        assertEquals("%", bestOf(draw(circle(20.0, 20.0) + listOf(85, 0, 15, 100) + circle(80.0, 80.0))), "% in one stroke")
        assertEquals("%", bestOf(draw(circle(20.0, 20.0) + listOf(85, 0, 15, 100), circle(80.0, 80.0))), "% in two strokes")
        assertEquals("±", bestOf(draw(listOf(50, 0, 50, 70, 50, 35, 10, 35, 90, 35), listOf(10, 100, 90, 100))), "± in two strokes")
        assertEquals("≠", bestOf(draw(listOf(0, 34, 100, 34, 0, 66, 100, 66), listOf(30, 100, 70, 0))), "≠ in two strokes")
        assertEquals("π", bestOf(draw(listOf(0, 12, 100, 12), listOf(32, 12, 32, 100, 50, 50, 68, 12, 68, 90))), "π in two strokes")
        val s = arc(50.0, 28.0, 34.0, 22.0, 335.0, 450.0) + arc(50.0, 72.0, 34.0, 22.0, 270.0, 515.0)
        assertEquals("$", bestOf(draw(s + listOf(50, 0, 50, 100))), "$ in one stroke")
    }

    @Test fun aCompleteSimplerSymbolStillWinsOverTheFuller() {
        assertEquals("–", best(InkStroke.of(20f, 80f, 300f, 80f), InkStroke.of(160f, 40f)), "dash and one dot")
        assertEquals("/", best(straight(60.0), InkStroke.of(60f, 40f)), "slash and one dot")
        assertEquals("|", best(InkStroke.of(100f, 10f, 100f, 140f)), "bar without its dot")
    }

    /**
     * The angles of a single straight stroke (pixels, y down, 0° flat, 90° upright; the mirrored angle is the
     * backslash). A slightly rising dash and a slightly leaning bar keep their meaning.
     */
    @Test fun theAnglesOfASingleStrokeArePinned() {
        val expected = mapOf(0 to "–", 5 to "–", 10 to "–", 15 to "–", 20 to "/", 25 to "/", 30 to "/", 45 to "/", 60 to "/",
            70 to "/", 75 to "/", 80 to "|", 90 to "|")
        for ((degrees, text) in expected) for (reversed in listOf(false, true)) {
            assertEquals(text, best(straight(degrees.toDouble(), reversed = reversed)), "$degrees°")
            assertEquals(if (text == "/") "\\" else text, best(straight(180.0 - degrees, reversed = reversed)), "${180 - degrees}° (mirrored)")
        }
    }

    @Test fun anIncompleteNearestTemplateDoesNotCutTheTurningOfTheCompleteOnes() {
        // a slash tilted by up to 10° off the 60° template: the incomplete "%" may be the nearest template upright
        for (tilt in listOf(-10.0, -6.0, 6.0, 10.0)) {
            val result = recognizer.recognize(listOf(straight(60.0 + tilt)))
            assertEquals("/", result.best?.text, "$tilt: ${result.candidates.take(4)}")
        }
    }

    /**
     * The general rule on every built-in drawing: leave strokes out of it and the first result is a symbol
     * with a template that fits the strokes drawn, unless no such symbol is accepted; then it is an incomplete
     * one (the audit of stage 83 found 65 results before the rule; 22 remain, all partial drawings of that very
     * symbol that no simpler symbol matches: ↔ ⇒ ≠ = ? $ # % ¿).
     */
    @Test fun anIncompleteFirstResultOnlyComesWhenNoCompleteSymbolIsAccepted() {
        val incompleteFirst = ArrayList<String>()
        for (symbol in InkSymbols.all) for (variant in symbol.variants) {
            if (variant.size > 5) continue
            for (mask in 1 until (1 shl variant.size) - 1) {
                val subset = variant.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
                val result = recognizer.recognize(subset)
                val best = result.best ?: continue
                if (best.complete) continue
                incompleteFirst += best.text
                // no simpler symbol was accepted, except the sloppy one that gave way to a close incomplete match
                val simpler = InkResult(result.candidates.filter { it.complete }).best
                assertTrue(simpler == null || simpler.distance > InkRecognizer.ACCEPT_DISTANCE, "${symbol.text} $mask: ${result.candidates.take(3)}")
            }
        }
        println("stage 83 audit: ${incompleteFirst.size} incomplete first results: $incompleteFirst")
        assertTrue(incompleteFirst.size <= 40, "${incompleteFirst.size}: $incompleteFirst")
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
