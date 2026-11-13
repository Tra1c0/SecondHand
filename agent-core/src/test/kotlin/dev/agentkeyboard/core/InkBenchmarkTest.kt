// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * Stage 36: recognition of synthetic finger drawings ([InkFingerModel]), per symbol, with the
 * confusions the owner reported (⇒ read as ≠) and their neighbours. Fixed seeds, so the numbers in
 * docs/VALIDATION.md can be reproduced; they say nothing about a real phone.
 */
class InkBenchmarkTest {
    private val recognizer = InkRecognizer(InkSymbols.templates)

    class Outcome(val correct: Int, val total: Int, val rejected: Int, val confusions: Map<Pair<String, String>, Int>,
            val perSymbol: Map<String, IntArray>) {
        fun rate(a: String, b: String) = (confusions[a to b] ?: 0).toDouble() / (perSymbol[a]?.get(1) ?: 1)
        fun report(name: String) {
            println("bench $name: $correct/$total correct (${"%.1f".format(100.0 * correct / total)} %), $rejected not recognized, ${total - correct - rejected} wrong")
            println("bench $name per symbol: " + perSymbol.entries.joinToString("  ") { (t, c) -> "$t ${100 * c[0] / c[1]}" })
            confusions.entries.sortedByDescending { it.value }.take(12).forEach { (pair, n) -> println("bench $name confusion: ${pair.first} -> ${pair.second}: $n") }
            for ((a, b) in PAIRS) println("bench $name pair: $a -> $b ${"%.1f".format(100 * rate(a, b))} %")
        }
    }

    @Test fun fingerDrawingsHard() {
        val o = run(recognizer::recognize, PER_SYMBOL, 36, hard = true)
        o.report("hard")
        assertTrue(o.correct >= o.total * MIN_HARD, "hard: ${o.correct}/${o.total}")
        for ((text, c) in o.perSymbol) assertTrue(c[0] >= c[1] * MIN_PER_SYMBOL, "hard $text: ${c[0]}/${c[1]}")
        for ((a, b) in PAIRS) assertTrue(o.rate(a, b) <= MAX_PAIR, "hard $a -> $b: ${o.rate(a, b)}")
        assertTrue(o.total - o.correct - o.rejected <= o.total * MAX_WRONG, "wrong: ${o.total - o.correct - o.rejected}")
    }

    @Test fun fingerDrawingsMild() {
        val o = run(recognizer::recognize, PER_SYMBOL, 37, hard = false)
        o.report("mild")
        assertTrue(o.correct >= o.total * MIN_MILD, "mild: ${o.correct}/${o.total}")
    }

    @Test fun theOwnersDoubleArrowIsNoNotEquals() {
        val (right, notEquals) = owners(recognizer::recognize)
        println("bench owner ⇒: $right/$OWNER_RUNS ⇒, $notEquals ≠")
        assertTrue(right >= OWNER_RUNS * 0.9, "⇒ $right of $OWNER_RUNS")
        assertTrue(notEquals <= OWNER_RUNS * 0.02, "≠ $notEquals of $OWNER_RUNS")
    }

    companion object {
        const val PER_SYMBOL = 50
        const val OWNER_RUNS = 200
        /** Pairs the owner reported or that look alike; each confusion rate is printed and bounded. */
        val PAIRS = listOf("⇒" to "≠", "≠" to "⇒", "→" to "↗", "↗" to "→", "=" to "≈", "≈" to "=", "=" to "≠", "≠" to "=",
            "→" to "⇒", "⇒" to "→", "+" to "×", "×" to "+")
        // stage 36 measured 96.2 % / 99.9 % (docs/VALIDATION.md); the bars leave a little room
        const val MIN_HARD = 0.95
        const val MIN_MILD = 0.98
        const val MIN_PER_SYMBOL = 0.70
        const val MAX_PAIR = 0.08
        const val MAX_WRONG = 0.03

        fun run(recognize: (List<InkStroke>) -> InkResult, perSymbol: Int, seed: Int, hard: Boolean): Outcome {
            val random = Random(seed)
            var correct = 0; var total = 0; var rejected = 0
            val confusions = HashMap<Pair<String, String>, Int>()
            val counts = LinkedHashMap<String, IntArray>()
            for (text in InkFingerModel.symbols) repeat(perSymbol) {
                val best = recognize(InkFingerModel.draw(text, random, hard)).best?.text
                total++
                val c = counts.getOrPut(text) { IntArray(2) }
                c[1]++
                when (best) {
                    text -> { correct++; c[0]++ }
                    null -> rejected++
                    else -> confusions.merge(text to best, 1, Int::plus)
                }
            }
            return Outcome(correct, total, rejected, confusions, counts)
        }

        /** How often the owner's ⇒ is ⇒ and how often ≠. */
        fun owners(recognize: (List<InkStroke>) -> InkResult): Pair<Int, Int> {
            val random = Random(14)
            var right = 0; var notEquals = 0
            repeat(OWNER_RUNS) {
                when (recognize(InkFingerModel.ownersDoubleArrow(random)).best?.text) {
                    "⇒" -> right++
                    "≠" -> notEquals++
                }
            }
            return right to notEquals
        }
    }
}
