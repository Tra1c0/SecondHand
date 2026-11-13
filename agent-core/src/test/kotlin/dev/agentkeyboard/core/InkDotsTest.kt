// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 31: a quick tap in the pause is a dot only where a symbol with dots needs one. */
class InkDotsTest {
    private val dots = InkDots(InkSymbols.templates)

    /** A hand-drawn stroke: [stroke] in the 100 box, turned, stretched, wobbled, at [size] px per 100 and offset. */
    private fun hand(strokes: List<InkStroke>, random: Random): List<InkStroke> {
        val angle = Math.toRadians(random.nextDouble(-8.0, 8.0))
        val sx = random.nextDouble(0.85, 1.15); val sy = random.nextDouble(0.85, 1.15)
        val size = random.nextDouble(1.5, 4.0)
        val ox = random.nextDouble(0.0, 600.0); val oy = random.nextDouble(0.0, 300.0)
        return strokes.map { stroke ->
            val dense = ArrayList<Float>()
            for (i in 0 until stroke.size) {
                if (i > 0) for (p in 1..5) {
                    val t = p / 6f
                    dense += stroke.x(i - 1) + t * (stroke.x(i) - stroke.x(i - 1)); dense += stroke.y(i - 1) + t * (stroke.y(i) - stroke.y(i - 1))
                }
                dense += stroke.x(i); dense += stroke.y(i)
            }
            InkStroke(FloatArray(dense.size) { k ->
                val i = k / 2
                val x = (dense[2 * i] - 50) * sx + random.nextDouble(-1.5, 1.5)
                val y = (dense[2 * i + 1] - 50) * sy + random.nextDouble(-1.5, 1.5)
                if (k % 2 == 0) ((x * cos(angle) - y * sin(angle)) * size + ox).toFloat()
                else ((x * sin(angle) + y * cos(angle)) * size + oy).toFloat()
            })
        }
    }

    private fun line(x1: Number, y1: Number, x2: Number, y2: Number) =
        InkStroke(floatArrayOf(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()))

    @Test fun bothDotsOfADivisionSignCount() {
        val random = Random(31)
        var first = 0; var both = 0; val n = 300
        repeat(n) {
            // the dots somewhere between close to the line and far from it, a little off the middle
            val off = random.nextDouble(25.0, 50.0)
            val drawn = hand(listOf(line(0, 50, 100, 50),
                InkStroke(floatArrayOf((50 + random.nextDouble(-10.0, 10.0)).toFloat(), (50 - off).toFloat())),
                InkStroke(floatArrayOf((50 + random.nextDouble(-10.0, 10.0)).toFloat(), (50 + off).toFloat()))), random)
            val (dash, upper, lower) = drawn
            if (dots.isDot(listOf(dash), upper.x(0), upper.y(0))) {
                first++
                if (dots.isDot(listOf(dash, upper), lower.x(0), lower.y(0))) both++
            }
        }
        assertTrue(both >= n * 0.95, "first $first, both $both of $n")
    }

    @Test fun tapsAwayFromTheDotsOfADashAreLetters() {
        val dash = listOf(line(100, 300, 400, 300))
        assertTrue(dots.isDot(dash, 250f, 190f)) // above the middle, where ÷ has its dot
        assertTrue(dots.isDot(dash, 255f, 410f)) // below
        assertFalse(dots.isDot(dash, 110f, 200f)) // above the left end
        assertFalse(dots.isDot(dash, 400f, 420f)) // below the right end
        assertFalse(dots.isDot(dash, 250f, 300f)) // on the line
        assertFalse(dots.isDot(dash, 700f, 300f)) // far to the side
    }

    @Test fun afterSymbolsWithoutDotsATapIsALetter() {
        val random = Random(7)
        var dotted = 0; var total = 0
        // finished symbols that do not look like a line; taps all around them
        for (symbol in InkSymbols.all.filter { it.text in setOf("✓", "+", "×", "∞", "√", "°", "♥", "★", "€", "$", "§", "&", "@", "#", "%", "(", "[", "{", "π", "≤") })
            for (variant in symbol.variants) repeat(40) {
                val drawn = hand(variant, random)
                val xs = drawn.flatMap { s -> (0 until s.size).map { s.x(it) } }; val ys = drawn.flatMap { s -> (0 until s.size).map { s.y(it) } }
                val w = maxOf(xs.max() - xs.min(), ys.max() - ys.min())
                total++
                if (dots.isDot(drawn, random.nextDouble((xs.min() - w).toDouble(), (xs.max() + w).toDouble()).toFloat(),
                        random.nextDouble((ys.min() - w).toDouble(), (ys.max() + w).toDouble()).toFloat())) dotted++
            }
        assertTrue(dotted <= total / 100, "$dotted of $total taps became dots")
    }

    @Test fun afterFinishedSymbolsMadeOfLinesATapWhereDivisionDotsWouldBeIsALetter() {
        // an arrow is close enough to a line, but it is an arrow (rule 4)
        val random = Random(11)
        var dotted = 0; var total = 0
        for (symbol in InkSymbols.all.filter { it.text in setOf("→", "←", "↔", "⇒", "=", "≠", "≈", "~") })
            for (variant in symbol.variants) repeat(40) {
                val drawn = hand(variant, random)
                val xs = drawn.flatMap { s -> (0 until s.size).map { s.x(it) } }; val ys = drawn.flatMap { s -> (0 until s.size).map { s.y(it) } }
                val cx = (xs.min() + xs.max()) / 2; val cy = (ys.min() + ys.max()) / 2
                val w = maxOf(xs.max() - xs.min(), ys.max() - ys.min())
                for (side in listOf(-1, 1)) {
                    total++
                    if (dots.isDot(drawn, cx, cy + side * 0.38f * w)) dotted++
                }
            }
        assertTrue(dotted <= total / 20, "$dotted of $total taps became dots")
    }

    @Test fun withoutSymbolsWithDotsEveryTapIsALetter() {
        val none = InkDots(InkSymbols.templates.filter { it.text != "÷" })
        assertTrue(none.isEmpty)
        assertFalse(none.isDot(listOf(line(100, 300, 400, 300)), 250f, 190f))
        assertFalse(InkDots(emptyList()).isDot(listOf(line(100, 300, 400, 300)), 250f, 190f))
    }

    @Test fun ownSymbolsWithDotsCount() {
        // an own "!" (line and a dot below), drawn three times like on the settings page
        val samples = List(3) { InkCloud.of(listOf(line(50, 0, 50, 75), InkStroke(floatArrayOf(50f, 100f))))!! }
        val own = InkDots(OwnInkSymbols.Symbol("!", samples).templates() + InkSymbols.templates)
        val stroke = listOf(line(300, 100, 300, 250))
        assertTrue(own.isDot(stroke, 302f, 300f)) // below the line
        assertFalse(own.isDot(stroke, 302f, 50f)) // above it
        assertFalse(dots.isDot(stroke, 302f, 300f)) // without the own symbol: a letter
        // stored and read again, the dot is still known
        val again = OwnInkSymbols.parse(OwnInkSymbols.encode(listOf(OwnInkSymbols.Symbol("!", samples))))
        assertTrue(InkDots(again.flatMap { it.templates() }).isDot(stroke, 302f, 300f))
        assertEquals("!", InkRecognizer(own.let { OwnInkSymbols.Symbol("!", samples).templates() } + InkSymbols.templates)
            .recognize(stroke + InkStroke(floatArrayOf(302f, 300f))).best?.text)
    }

    @Test fun earlierDotsAreNotPartOfTheLine() {
        val dash = line(100, 300, 400, 300)
        val upper = InkStroke(floatArrayOf(250f, 190f))
        assertTrue(dots.isDot(listOf(dash, upper), 252f, 412f))
        assertFalse(dots.isDot(listOf(dash, upper), 110f, 420f))
    }
}
