// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 43: the whole way of a drawing through the pause, as SymbolInk decides it, with the finger
 * model: the first stroke starts the drawing, a touch that moves less than [InkTap.SLOP_DP] is a tap,
 * a touch outside [InkTap.nearDrawing] ends the drawing. Screen units are px of a phone with
 * [DENSITY]; keys [KEY] wide, rows [ROW] tall. Nothing here is measured on a real phone.
 */
class InkDotFlowTest {
    private val recognizer = InkRecognizer(InkSymbols.templates)
    private val dots = InkDots(InkSymbols.templates, recognizer)

    /** What comes out, in order: a written symbol ("?" for not recognized) or "key:i" for stroke i typed as a letter. */
    private fun flow(strokes: List<InkStroke>, near: MutableSet<Int>? = null): List<String> {
        val out = ArrayList<String>()
        val group = ArrayList<InkStroke>()
        // stage 43: possible dots, by their index in the group, and the stroke they are
        val possible = LinkedHashMap<Int, Int>()
        fun write() {
            if (group.isEmpty()) return
            val keep = dots.keeps(group, possible.keys)
            val drawing = if (keep) group.toList() else group.filterIndexed { k, _ -> k !in possible }
            out += recognizer.recognize(drawing).best?.text ?: "?"
            if (!keep) possible.values.forEach { out += "key:$it" }
            group.clear(); possible.clear()
        }
        for ((i, s) in strokes.withIndex()) {
            val tap = isTap(s)
            if (group.isEmpty()) { if (tap) out += "key:$i" else group += s; continue }
            if (!InkTap.nearDrawing(group, s.x(0), s.y(0), KEY)) { write(); if (tap) out += "key:$i" else group += s; continue }
            if (!tap) { group += s; continue }
            near?.add(i) // a tap that starts inside the area of the waiting drawing
            // a possible dot joins as one point where it was tapped (SymbolInk)
            if (dots.isDot(group, centreX(s), centreY(s))) { possible[group.size] = i; group += InkStroke(floatArrayOf(centreX(s), centreY(s))) }
            else { write(); out += "key:$i" }
        }
        write()
        return out
    }

    @Test fun dotSymbolsDrawnWithTappedDots() {
        val random = Random(43)
        val report = StringBuilder()
        var right = 0; var total = 0
        val rates = HashMap<String, Int>()
        for (text in InkFingerModel.dottedSymbols) {
            var ok = 0; var withLetter = 0; var lost = 0
            repeat(RUNS) {
                val drawn = placed(InkFingerModel.drawDotted(text, random), random)
                val out = flow(drawn)
                when {
                    out == listOf(text.take(1)) -> ok++
                    out.any { it.startsWith("key:") } -> withLetter++
                    else -> lost++
                }
            }
            right += ok; total += RUNS; rates[text] = ok
            report.append("$text $ok/$RUNS (with letter $withLetter, other $lost)  ")
        }
        println("dotflow symbols: $report")
        println("dotflow symbols total: $right/$total")
        // stage 31 (before stage 43): ÷ 142/200, % with tapped dots 0/200, with tiny circles 10/200, the
        // others were not built in; stage 43: ÷ 197, % 195, % with tiny circles 106 (docs/VALIDATION.md)
        assertTrue(rates.getValue("÷") >= RUNS * 0.95 && rates.getValue("%") >= RUNS * 0.95, "$report")
        for (text in listOf("‰", "!", "¡", "?", "¿")) assertTrue(rates.getValue(text) >= RUNS * 0.75, "$text: $report")
        assertTrue(rates.getValue("%○") >= RUNS * 0.45, "$report")
    }

    @Test fun lettersTypedRightAfterASymbolWithoutDots() {
        val random = Random(44)
        var letters = 0; var lostLetters = 0; var wrongSymbol = 0; var runs = 0; var disordered = 0
        var nearLetters = 0; var nearLost = 0
        val lostBy = LinkedHashMap<String, Int>()
        val plain = InkFingerModel.symbols.filter { text -> InkFingerModel.dottedSymbols.none { it.take(1) == text } }
        for (text in plain) repeat(LETTER_RUNS) {
            val drawn = placed(InkFingerModel.draw(text, random), random)
            val alone = flow(drawn) // the drawing without the letters after it
            // then a short word: one to four letters on random keys, all within the pause
            val taps = List(1 + random.nextInt(4)) {
                val x = KEY * (random.nextInt(10) + 0.5f) + random.nextDouble(-8.0, 8.0).toFloat() * DENSITY
                val y = ROW * (random.nextInt(3) + 0.5f) + random.nextDouble(-8.0, 8.0).toFloat() * DENSITY
                InkStroke(floatArrayOf(x, y, x + 2, y + 1))
            }
            val near = HashSet<Int>()
            val out = flow(drawn + taps, near)
            runs++
            letters += taps.size
            val typed = out.filter { it.startsWith("key:") }.map { it.substring(4).toInt() }.filter { it >= drawn.size }
            lostLetters += taps.size - typed.size
            // review N6: only the taps inside the area of the drawing (the others end it and are typed)
            val nearTaps = near.filter { it >= drawn.size }
            nearLetters += nearTaps.size
            nearLost += nearTaps.count { it !in typed }
            if (typed.size < taps.size) lostBy.merge(text + "→" + out.first(), taps.size - typed.size, Int::plus)
            if (typed != typed.sorted()) disordered++
            if (out.filter { !it.startsWith("key:") || it.substring(4).toInt() < drawn.size } != alone) wrongSymbol++
        }
        println("dotflow letters: $lostLetters of $letters letters lost, symbol changed in $wrongSymbol of $runs drawings, letters out of order $disordered")
        println("dotflow letters lost after: $lostBy")
        println("dotflow letters inside the area: $nearLost of $nearLetters lost")
        // stage 31: 3 of 1875 lost, nothing changed; stage 43: 2 lost, both after a vertical line (! and ¡)
        assertTrue(lostLetters <= letters / 200, "$lostLetters of $letters: $lostBy")
        // review N6 (2026-11-14): 2 of 597 inside the area (the same two as above)
        assertTrue(nearLost <= nearLetters / 100, "$nearLost of $nearLetters inside the area")
        assertTrue(wrongSymbol <= runs / 100, "$wrongSymbol of $runs")
        assertTrue(disordered == 0)
    }

    /**
     * Stage 43 review M1: fast taps right after typing, as they may slide (an assumed model, nothing
     * measured on a phone): four in five move like a normal tap (|N(0, 0.1)| key widths), one in five
     * rolls onto the next key (0.2–1.2 key widths), within ±35° of the row, slightly bent, with 1 dp
     * jitter. Reports how many reach the start of a drawing right after typing
     * ([InkTap.START_AFTER_TYPING_KEYS]) and how many of those [InkTap.isSlidTap] gives back as letters.
     */
    @Test fun slidingTapsRightAfterTypingStayLetters() {
        val random = Random(4301)
        var over35 = 0; var started = 0; var letters = 0; var strokes = 0
        repeat(TAPS) {
            val tap = slidingTap(random)
            var moved = 0f
            for (i in 1 until tap.size) moved = maxOf(moved, hypot(tap.x(i) - tap.x(0), tap.y(i) - tap.y(0)))
            if (moved >= 0.35f * KEY) over35++
            if (!InkTap.isTap(moved / DENSITY)) strokes++ // near a waiting drawing a further stroke (P111)
            if (!InkTap.startsAfterTyping(moved, KEY)) return@repeat
            started++
            if (InkTap.isSlidTap(tap, KEY)) letters++
        }
        println("aftertyping taps: $TAPS, from 0.35 keys $over35, start a drawing (0.75 keys) $started, of these typed as letters $letters, lost ${started - letters}; a stroke near a drawing (8 dp) $strokes")
        // stage 43 review M1 (2026-11-14): 10000 taps, 1727 from 0.35 keys, 923 start a drawing, all 923 letters;
        // 2206 move 8 dp or more (near a waiting drawing a further stroke)
        assertTrue(started - letters <= started / 100, "$started started, $letters letters")
    }

    /**
     * Stage 43 review M1: what [InkTap.isSlidTap] costs: one-stroke symbols of the finger model drawn
     * right after typing, at a given size (larger side in key widths), that come out as a letter
     * instead; and how many of those drawn at the usual size (1.5–3 rows) are not recognized (now a
     * letter instead of "Nicht erkannt").
     */
    @Test fun shortStrokesRightAfterTyping() {
        val random = Random(4302)
        val report = StringBuilder()
        val single = InkFingerModel.symbols.filter { text -> InkFingerModel.draw(text, Random(1)).size == 1 }
        val rates = LinkedHashMap<String, Int>()
        for ((from, to) in listOf(0.75 to 1.0, 1.0 to 1.5, 1.5 to 2.3, 2.3 to 4.6)) {
            var asLetter = 0; var dashes = 0; var dashLetters = 0; var total = 0
            for (text in single) repeat(SHORT_RUNS) {
                val drawn = InkFingerModel.draw(text, random)
                if (drawn.size != 1) return@repeat
                val stroke = sized(drawn, random.nextDouble(from, to).toFloat() * KEY, random).single()
                total++
                val letter = InkTap.isSlidTap(stroke, KEY)
                if (letter) asLetter++
                if (text == "–" || text == "|") { dashes++; if (letter) dashLetters++ }
            }
            rates["$from"] = asLetter
            report.append("$from–$to keys: $asLetter/$total as letter (– and | $dashLetters/$dashes)  ")
        }
        var unknown = 0; var drawings = 0
        for (text in single) repeat(SHORT_RUNS) {
            val drawn = InkFingerModel.draw(text, random)
            if (drawn.size != 1) return@repeat
            drawings++
            if (recognizer.recognize(placed(drawn, random)).best == null) unknown++
        }
        println("aftertyping short strokes: $report")
        println("aftertyping not recognized at 1.5–3 rows: $unknown/$drawings")
        // from 1.5 key widths hardly ever a sliding tap (a closed or bent stroke whose farthest point is
        // nearer), at usual sizes never
        assertTrue(rates.getValue("1.5") <= 680 / 100, "$report")
        assertEquals(0, rates.getValue("2.3"))
    }

    companion object {
        const val DENSITY = 2.75f
        const val KEY = 36 * DENSITY
        const val ROW = 55 * DENSITY
        const val RUNS = 200
        const val LETTER_RUNS = 20
        const val TAPS = 10_000
        const val SHORT_RUNS = 40

        /** A fast tap right after typing, as [slidingTapsRightAfterTypingStayLetters] assumes it. */
        fun slidingTap(r: Random): InkStroke {
            val length = KEY * if (r.nextInt(5) == 0) r.nextDouble(0.2, 1.2) else abs(r.nextGaussian(0.1))
            val angle = Math.toRadians(r.nextDouble(-35.0, 35.0)) + if (r.nextBoolean()) 0.0 else PI
            val bend = r.nextDouble(-0.1, 0.1) * length
            val x0 = KEY * (r.nextInt(10) + 0.5); val y0 = ROW * (r.nextInt(3) + 0.5)
            val points = 3 + r.nextInt(6)
            return InkStroke(FloatArray(2 * points) { k ->
                val t = (k / 2).toDouble() / (points - 1)
                val along = length * t; val across = bend * sin(PI * t)
                val jitter = if (k < 2) 0.0 else r.nextDouble(-1.0, 1.0) * DENSITY
                if (k % 2 == 0) (x0 + along * cos(angle) - across * sin(angle) + jitter).toFloat()
                else (y0 + along * sin(angle) + across * cos(angle) + jitter).toFloat()
            })
        }

        private fun Random.nextGaussian(sigma: Double): Double {
            // Box–Muller
            val u = nextDouble().coerceAtLeast(1e-12); val v = nextDouble()
            return sigma * sqrt(-2 * ln(u)) * cos(2 * PI * v)
        }

        fun isTap(s: InkStroke): Boolean {
            var moved = 0f
            for (i in 1 until s.size) moved = maxOf(moved, hypot(s.x(i) - s.x(0), s.y(i) - s.y(0)))
            return InkTap.isTap(moved / DENSITY)
        }

        fun centreX(s: InkStroke) = (0 until s.size).map { s.x(it) }.average().toFloat()
        fun centreY(s: InkStroke) = (0 until s.size).map { s.y(it) }.average().toFloat()

        /** [strokes] scaled to 1.5–3 rows tall, somewhere on a keyboard of 10 keys by 3 rows. */
        fun placed(strokes: List<InkStroke>, r: Random): List<InkStroke> = sized(strokes, (r.nextDouble(1.5, 3.0) * ROW).toFloat(), r)

        /** [strokes] scaled to a larger side of [size] px, somewhere on a keyboard of 10 keys by 3 rows. */
        fun sized(strokes: List<InkStroke>, size: Float, r: Random): List<InkStroke> {
            val xs = strokes.flatMap { s -> (0 until s.size).map { s.x(it) } }; val ys = strokes.flatMap { s -> (0 until s.size).map { s.y(it) } }
            val minX = xs.min(); val minY = ys.min()
            val scale = size / maxOf(xs.max() - minX, ys.max() - minY)
            val ox = r.nextDouble(0.0, maxOf(1.0, (10 * KEY - (xs.max() - minX) * scale).toDouble())).toFloat()
            val oy = r.nextDouble(0.0, maxOf(1.0, (3 * ROW - (ys.max() - minY) * scale).toDouble())).toFloat()
            return strokes.map { s -> InkStroke(FloatArray(s.coordinates.size) { i ->
                if (i % 2 == 0) (s.coordinates[i] - minX) * scale + ox else (s.coordinates[i] - minY) * scale + oy }) }
        }
    }
}
