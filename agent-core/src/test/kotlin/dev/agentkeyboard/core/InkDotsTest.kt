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

    // stage 31 fix (review M2): fast typing right after a drawn dash ("E-Mail") must not lose letters

    /** A phone keyboard: 10 keys of 36 dp per row, rows of 55 dp; the centre of key [k] in row [row]. */
    private fun keyX(k: Int) = 18f + 36 * k
    private fun rowY(row: Int) = 27.5f + 55 * row

    /** A hand-drawn dash from ([x0], [y0]), [length] long, slightly tilted and wobbly. */
    private fun dash(random: Random, x0: Float, y0: Float, length: Float): InkStroke {
        val n = 20; val angle = random.nextDouble(-0.08, 0.08)
        return InkStroke(FloatArray(2 * (n + 1)) { k ->
            val t = (k / 2).toFloat() / n
            if (k % 2 == 0) (x0 + t * length * cos(angle) + random.nextDouble(-1.5, 1.5)).toFloat()
            else (y0 + t * length * sin(angle) + random.nextDouble(-1.5, 1.5)).toFloat()
        })
    }

    @Test fun lettersBesideTheMiddleOfADashAreLetters() {
        // a dash over four keys of the middle row (90..234, middle 162 = the centre of key 4)
        val drawn = listOf(line(90, rowY(2), 234, rowY(2)))
        for (row in listOf(1, 3)) {
            // the neighbours of the key right above or below the middle, and every key further out
            for (k in (0..9) - 4) assertFalse(dots.isDot(drawn, keyX(k), rowY(row)), "key $k in row $row")
            // the key straight above or below the middle stays a dot: that is where ÷ has its dots
            assertTrue(dots.isDot(drawn, keyX(4), rowY(row)), "middle key in row $row")
        }
        // two rows away is no dot either
        assertFalse(dots.isDot(listOf(line(90, rowY(1), 234, rowY(1))), keyX(4), rowY(3)))
    }

    @Test fun lettersTappedAroundADrawnDashAreRarelyDots() {
        val random = Random(30)
        var dotted = 0; var total = 0
        repeat(400) {
            // a dash over three to six keys in the second or third row, letters tapped in the rows next to it
            val row = 1 + random.nextInt(2)
            val y0 = rowY(row) + random.nextDouble(-10.0, 10.0).toFloat()
            val length = (3 + random.nextInt(4)) * 36f + random.nextDouble(-15.0, 15.0).toFloat()
            val x0 = random.nextDouble(5.0, (355 - length).toDouble()).toFloat()
            val drawn = listOf(dash(random, x0, y0, length))
            for (tapRow in listOf(row - 1, row + 1)) for (k in 0..9) {
                total++
                if (dots.isDot(drawn, keyX(k) + random.nextDouble(-8.0, 8.0).toFloat(),
                        rowY(tapRow) + random.nextDouble(-8.0, 8.0).toFloat())) dotted++
            }
        }
        // about one key per row stays a dot (the one straight above or below the middle): 826 of 8000;
        // with the 0.3 circle before the fix 1972 of 8000
        assertTrue(dotted <= total * 12 / 100, "$dotted of $total taps became dots")
    }

    @Test fun aDivisionSignDrawnOverTheKeysKeepsBothDots() {
        val random = Random(31)
        var both = 0; var lowerFirst = 0; val n = 300
        repeat(n) {
            val length = random.nextDouble(100.0, 220.0).toFloat(); val x0 = random.nextDouble(0.0, 140.0).toFloat(); val y0 = 110f
            val drawn = dash(random, x0, y0, length)
            val middle = x0 + length / 2
            val off = random.nextDouble(0.25, 0.5).toFloat() * length
            val upper = InkStroke(floatArrayOf(middle + random.nextDouble(-0.1, 0.1).toFloat() * length, y0 - off))
            val lower = InkStroke(floatArrayOf(middle + random.nextDouble(-0.1, 0.1).toFloat() * length, y0 + off))
            if (dots.isDot(listOf(drawn), upper.x(0), upper.y(0)) && dots.isDot(listOf(drawn, upper), lower.x(0), lower.y(0))) both++
            // the other order counts the same
            if (dots.isDot(listOf(drawn), lower.x(0), lower.y(0)) && dots.isDot(listOf(drawn, lower), upper.x(0), upper.y(0))) lowerFirst++
        }
        assertTrue(both >= n * 0.95 && lowerFirst >= n * 0.95, "both dots $both, lower dot first $lowerFirst of $n")
    }

    @Test fun aTapThatIsNoDotIsRecognizedOnceAndTheWrittenDrawingIsFree() {
        // review N2: rule 4 asks for the very drawing that is written when the tap is a letter
        val recognizer = InkRecognizer(InkSymbols.templates)
        val dots = InkDots(InkSymbols.templates, recognizer)
        val arrow = listOf(line(100, 300, 400, 300), InkStroke(floatArrayOf(340f, 240f, 400f, 300f, 340f, 360f)))
        assertFalse(dots.isDot(arrow, 250f, 190f)) // where ÷ has a dot, but the drawing is an arrow
        assertEquals(1, recognizer.recognitions)
        assertEquals("→", recognizer.recognize(arrow).best?.text) // what SymbolInk.finish then writes
        assertEquals(1, recognizer.recognitions)
        // another drawing is matched as before
        recognizer.recognize(listOf(line(100, 300, 400, 300)))
        assertEquals(2, recognizer.recognitions)
    }

    @Test fun earlierDotsAreNotPartOfTheLine() {
        val dash = line(100, 300, 400, 300)
        val upper = InkStroke(floatArrayOf(250f, 190f))
        assertTrue(dots.isDot(listOf(dash, upper), 252f, 412f))
        assertFalse(dots.isDot(listOf(dash, upper), 110f, 420f))
    }
}
