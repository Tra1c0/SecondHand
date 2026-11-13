// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 36: the tap rule in the pause and the pause that follows the user's pace. */
class InkTimingTest {
    @Test fun aTouchIsATapOnlyBelowEightDp() {
        // follow-up review F3: 8 dp like Android's touch slop (a rolling finger moves more than 4 dp, P101)
        assertTrue(InkTap.isTap(0f))
        assertTrue(InkTap.isTap(7.9f))
        assertFalse(InkTap.isTap(8f))
        assertFalse(InkTap.isTap(9f)) // was a tap with the old 10 dp
    }

    @Test fun furtherStrokesStartOnlyNearTheDrawing() {
        // review H1, F2: a 300 px dash grows by 0.6 of its size (180), at least by 3 key widths
        val dash = listOf(InkStroke.of(100f, 300f, 400f, 300f))
        assertTrue(InkTap.nearDrawing(dash, 250f, 300f, 36f))
        assertTrue(InkTap.nearDrawing(dash, 579f, 121f, 36f))
        assertFalse(InkTap.nearDrawing(dash, 581f, 300f, 36f))
        assertFalse(InkTap.nearDrawing(dash, 250f, 481f, 36f))
        assertTrue(InkTap.nearDrawing(dash, 250f, 479f, 36f))
        val short = listOf(InkStroke.of(100f, 300f, 140f, 300f)) // small: at least 3 keys (108)
        assertTrue(InkTap.nearDrawing(short, 247f, 300f, 36f))
        assertFalse(InkTap.nearDrawing(short, 249f, 300f, 36f))
        assertFalse(InkTap.nearDrawing(emptyList(), 150f, 300f, 36f))
    }

    /**
     * A synthetic measurement of the rules, not of real fingers: letter taps last 90 ms in the median
     * (log-normal, σ 0.45) and move 1.2 dp per axis (normal), one in six "rolled" by 3 dp per axis;
     * intended short strokes (arrow barbs, the bar of ≤, the slash of ≠ drawn small) are 5–40 dp long,
     * drawn at 100–600 dp/s with a 20–120 ms rest. Printed for docs/VALIDATION.md; the bounds below hold
     * for this model only.
     */
    @Test fun falseTapsAndFalseStrokesOfTheRules() {
        val random = Random(36)
        fun gauss() = sqrt(-2 * kotlin.math.ln(random.nextDouble(1e-12, 1.0))) * kotlin.math.cos(2 * Math.PI * random.nextDouble())
        class Touch(val moved: Float, val millis: Long)
        val taps = List(20_000) {
            val sigma = if (random.nextInt(6) == 0) 3.0 else 1.2
            Touch(hypot(sigma * gauss(), sigma * gauss()).toFloat(), (90 * exp(0.45 * gauss())).toLong())
        }
        val strokes = List(20_000) {
            val length = random.nextDouble(5.0, 40.0)
            Touch(length.toFloat(), (1000 * length / random.nextDouble(100.0, 600.0) + random.nextDouble(20.0, 120.0)).toLong())
        }
        val rules = linkedMapOf<String, (Touch) -> Boolean>(
            "old (< 10 dp)" to { t -> t.moved <= 10f },
            "proposed (< 4 dp and < 180 ms)" to { t -> t.moved < 4f && t.millis < InkTap.QUICK_MILLIS },
            "stage 36 first version (< 4 dp)" to { t -> t.moved < 4f },
            "stage 36 second version (< 6 dp)" to { t -> t.moved < 6f },
            "stage 36 (< 8 dp)" to { t -> InkTap.isTap(t.moved) },
        )
        val rates = rules.mapValues { (_, isTap) ->
            taps.count { !isTap(it) }.toDouble() / taps.size to strokes.count { isTap(it) }.toDouble() / strokes.size
        }
        rates.forEach { (name, r) -> println("tap rule $name: false strokes ${"%.1f".format(100 * r.first)} %, false taps ${"%.1f".format(100 * r.second)} %") }
        val chosen = rates.getValue("stage 36 (< 8 dp)")
        val old = rates.getValue("old (< 10 dp)")
        // follow-up review F3: fewer short strokes are taps than with 10 dp, but more than with 6 dp
        assertTrue(chosen.second < old.second, "short strokes taken as taps: $chosen vs $old")
        assertTrue(chosen.first <= rates.getValue("stage 36 second version (< 6 dp)").first)
        // in this model at most 1 % of the letters tapped near a drawing become strokes, and at most 10 %
        // of the intended short strokes (5–40 dp) become taps
        assertTrue(chosen.first < 0.01, "$chosen")
        assertTrue(chosen.second < 0.10, "$chosen")
    }

    @Test fun thePauseStartsAt900MsAndFollowsTheMedianGap() {
        val pace = InkPace()
        assertEquals(900, pace.pauseMillis)
        pace.onGap(300); pace.onGap(320)
        assertEquals(900, pace.pauseMillis) // two gaps are no pace yet
        pace.onGap(340)
        assertEquals(800, pace.pauseMillis) // 2.5 × 320
        repeat(9) { pace.onGap(100) }
        assertEquals(600, pace.pauseMillis) // fast hands: never below 600 ms
        repeat(9) { pace.onGap(900) }
        assertEquals(1500, pace.pauseMillis) // slow hands: never above 1.5 s
        pace.onGap(-5) // a clock that went backwards is no gap
        assertEquals(1500, pace.pauseMillis)
        pace.clear()
        assertEquals(900, pace.pauseMillis)
    }

    @Test fun oneOddGapDoesNotMoveThePause() {
        val pace = InkPace()
        repeat(5) { pace.onGap(400) }
        pace.onGap(5_000) // the user paused to think
        assertEquals(1000, pace.pauseMillis)
    }
}
