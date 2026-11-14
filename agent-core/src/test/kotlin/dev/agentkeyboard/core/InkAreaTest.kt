// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * Stage 36 (follow-up review F2): further strokes start inside the area around the strokes drawn so
 * far ([InkTap.nearDrawing]), for drawings 2–3 key rows tall: every built-in drawing in its stroke
 * order and direction, and the finger model with its random orders and directions. Units are dp: a
 * key [KEY] wide, a row [ROW] tall.
 */
class InkAreaTest {
    private fun scaled(strokes: List<InkStroke>, size: Float): List<InkStroke> {
        val xs = strokes.flatMap { s -> (0 until s.size).map { s.x(it) } }; val ys = strokes.flatMap { s -> (0 until s.size).map { s.y(it) } }
        val minX = xs.min(); val minY = ys.min()
        val scale = size / maxOf(xs.max() - minX, ys.max() - minY)
        return strokes.map { s -> InkStroke(FloatArray(s.coordinates.size) { i ->
            if (i % 2 == 0) (s.coordinates[i] - minX) * scale + 50 else (s.coordinates[i] - minY) * scale + 30 }) }
    }

    /** The smallest key margin (in key widths) that accepts every further stroke of [strokes]; 0 if the share of the size does. */
    private fun needed(strokes: List<InkStroke>): Float {
        var worst = 0f
        for (k in 1 until strokes.size) {
            val drawn = strokes.subList(0, k)
            val x = strokes[k].x(0); val y = strokes[k].y(0)
            if (InkTap.nearDrawing(drawn, x, y, KEY, 0f)) continue
            var lo = 0f; var hi = 20f
            repeat(30) { val mid = (lo + hi) / 2; if (InkTap.nearDrawing(drawn, x, y, KEY, mid)) hi = mid else lo = mid }
            worst = maxOf(worst, hi)
        }
        return worst
    }

    @Test fun furtherStrokesOfTheBuiltInSymbolsStartInsideTheArea() {
        val outside = ArrayList<String>()
        var most = 0f
        for (rows in listOf(2f, 2.5f, 3f)) for (symbol in InkSymbols.all) for ((v, variant) in symbol.variants.withIndex()) {
            val need = needed(scaled(variant, rows * ROW))
            most = maxOf(most, need)
            if (need > InkTap.KEY_MARGIN) outside += "${symbol.text} v$v at $rows rows: ${"%.2f".format(need)} keys"
        }
        println("area: built-in drawings need at most ${"%.2f".format(most)} key widths (margin ${InkTap.KEY_MARGIN})")
        assertTrue(outside.isEmpty(), outside.joinToString())
    }

    /**
     * Stage 43: a lone tap is typed, it never starts a drawing; so a drawing the finger model begins with
     * a dot (! ? ‰ ÷ in random order) begins with its first stroke, and the dots before it come after.
     */
    private fun startingWithAStroke(strokes: List<InkStroke>): List<InkStroke> {
        val first = strokes.indexOfFirst { it.size > 1 }
        if (first <= 0) return strokes
        return listOf(strokes[first]) + strokes.filterIndexed { i, _ -> i != first }
    }

    @Test fun furtherStrokesOfFingerDrawingsInAnyOrderMostlyStartInsideTheArea() {
        val random = Random(36)
        val needs = ArrayList<Float>()
        val worst = HashMap<String, Int>()
        for (rows in listOf(2f, 2.5f, 3f)) for (text in InkFingerModel.symbols) repeat(40) {
            val need = needed(scaled(startingWithAStroke(InkFingerModel.draw(text, random, hard = true)), rows * ROW))
            needs += need
            if (need > InkTap.KEY_MARGIN) worst.merge(text, 1, Int::plus)
        }
        val outside = needs.count { it > InkTap.KEY_MARGIN }
        for (t in listOf(1.5f, 2f, 2.5f, 3f, 3.5f, 4f)) println("area: finger drawings needing more than $t keys: ${needs.count { it > t }}/${needs.size}")
        println("area: outside with margin ${InkTap.KEY_MARGIN}: $outside/${needs.size}, by symbol $worst")
        // the rest are drawings that start far from where they began, e.g. the arrow head first, then
        // the shaft from its far end (the finger model reverses strokes at random)
        assertTrue(outside <= needs.size * 0.03, "$outside of ${needs.size}: $worst")
    }

    @Test fun aKeyFarFromASmallDrawingIsOutside() {
        // review H1 stays: next to a small "≈" (1.5 keys wide) the rest of the row is typed as usual
        val waves = scaled(listOf(InkStroke.of(0f, 0f, 50f, 10f, 100f, 0f), InkStroke.of(0f, 30f, 50f, 40f, 100f, 30f)), 1.5f * KEY)
        val right = waves.flatMap { s -> (0 until s.size).map { s.x(it) } }.max()
        assertTrue(!InkTap.nearDrawing(waves, right + (InkTap.KEY_MARGIN + 0.5f) * KEY, 40f, KEY))
        assertTrue(InkTap.nearDrawing(waves, right + (InkTap.KEY_MARGIN - 0.1f) * KEY, 40f, KEY))
    }

    private companion object {
        const val KEY = 36f
        const val ROW = 55f
    }
}
