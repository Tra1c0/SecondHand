// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random
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
    private fun flow(strokes: List<InkStroke>): List<String> {
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
            val out = flow(drawn + taps)
            runs++
            letters += taps.size
            val typed = out.filter { it.startsWith("key:") }.map { it.substring(4).toInt() }.filter { it >= drawn.size }
            lostLetters += taps.size - typed.size
            if (typed.size < taps.size) lostBy.merge(text + "→" + out.first(), taps.size - typed.size, Int::plus)
            if (typed != typed.sorted()) disordered++
            if (out.filter { !it.startsWith("key:") || it.substring(4).toInt() < drawn.size } != alone) wrongSymbol++
        }
        println("dotflow letters: $lostLetters of $letters letters lost, symbol changed in $wrongSymbol of $runs drawings, letters out of order $disordered")
        println("dotflow letters lost after: $lostBy")
        // stage 31: 3 of 1875 lost, nothing changed; stage 43: 2 lost, both after a vertical line (! and ¡)
        assertTrue(lostLetters <= letters / 200, "$lostLetters of $letters: $lostBy")
        assertTrue(wrongSymbol <= runs / 100, "$wrongSymbol of $runs")
        assertTrue(disordered == 0)
    }

    companion object {
        const val DENSITY = 2.75f
        const val KEY = 36 * DENSITY
        const val ROW = 55 * DENSITY
        const val RUNS = 200
        const val LETTER_RUNS = 20

        fun isTap(s: InkStroke): Boolean {
            var moved = 0f
            for (i in 1 until s.size) moved = maxOf(moved, hypot(s.x(i) - s.x(0), s.y(i) - s.y(0)))
            return InkTap.isTap(moved / DENSITY)
        }

        fun centreX(s: InkStroke) = (0 until s.size).map { s.x(it) }.average().toFloat()
        fun centreY(s: InkStroke) = (0 until s.size).map { s.y(it) }.average().toFloat()

        /** [strokes] scaled to 1.5–3 rows tall, somewhere on a keyboard of 10 keys by 3 rows. */
        fun placed(strokes: List<InkStroke>, r: Random): List<InkStroke> {
            val xs = strokes.flatMap { s -> (0 until s.size).map { s.x(it) } }; val ys = strokes.flatMap { s -> (0 until s.size).map { s.y(it) } }
            val minX = xs.min(); val minY = ys.min()
            val size = (r.nextDouble(1.5, 3.0) * ROW).toFloat()
            val scale = size / maxOf(xs.max() - minX, ys.max() - minY)
            val ox = r.nextDouble(0.0, maxOf(1.0, (10 * KEY - (xs.max() - minX) * scale).toDouble())).toFloat()
            val oy = r.nextDouble(0.0, maxOf(1.0, (3 * ROW - (ys.max() - minY) * scale).toDouble())).toFloat()
            return strokes.map { s -> InkStroke(FloatArray(s.coordinates.size) { i ->
                if (i % 2 == 0) (s.coordinates[i] - minX) * scale + ox else (s.coordinates[i] - minY) * scale + oy }) }
        }
    }
}
