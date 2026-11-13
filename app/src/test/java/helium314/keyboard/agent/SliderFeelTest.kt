// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.SliderFeel.Impulse
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 28: where the sliders tick and snap, and how one drag behaves (plain code, own clock). */
class SliderFeelTest {
    private class Recorder : SliderFeel.Sink {
        val played = mutableListOf<Pair<Long, Impulse>>()
        var starts = 0
        var now = 0L
        override fun play(impulse: Impulse) { played += now to impulse }
        override fun start() { starts++ }
        fun count(impulse: Impulse) = played.count { it.second == impulse }
        val last get() = played.lastOrNull()?.second
    }

    private val sink = Recorder()
    private fun drag(feel: SliderFeel) = SliderDrag(feel, sink) { sink.now }

    /** Review of stage 28: an end next to a round value ("system default", "no limit") is reachable by dragging. */
    @Test fun anEndNextToARoundValueIsReachedByDragging() {
        fun dragTo(feel: SliderFeel, from: Float, to: Float): Float {
            val drag = drag(feel)
            drag.begin(from)
            var shown = from
            val n = 200
            for (k in 1..n) { sink.now += 30; shown = drag.next(from + (to - from) * k / n) }
            return shown
        }
        // key vibration -1..100 ms: -1 = system default, not 0 ms
        assertEquals(-1f, dragTo(SliderFeel(-1f..100f, defaultValue = -1f, integer = true), 50f, -1f))
        // key sound volume -0.01..1: -0.01 = system default, not volume 0
        assertEquals(-0.01f, dragTo(SliderFeel(-0.01f..1f, defaultValue = -0.01f), 0.5f, -0.01f))
        // clipboard file size 1..1001: 1001 = no limit, not 1000
        assertEquals(1001f, dragTo(SliderFeel(1f..1001f, defaultValue = 20f, integer = true), 500f, 1001f))
        // starting on the neighbour of an end works too
        assertEquals(1001f, dragTo(SliderFeel(1f..1001f, defaultValue = 20f, integer = true), 990f, 1001f))
        // the end is felt as a detent
        assertEquals(Impulse.DETENT, sink.last)
    }

    @Test fun ticksAndDetentsFollowTheRangeOfEachSlider() {
        // touchpad sensitivity 0..100 (Int): ticks every 5, detents every 10
        SliderFeel(0f..100f, defaultValue = 50f, integer = true).let {
            assertEquals(5f, it.tickStep)
            assertEquals(10f, it.roundStep)
            assertEquals((0..10).map { i -> i * 10f }, it.detents)
            assertEquals(1.5f, it.radius)
        }
        // keyboard height 0.3..1.5, default 1.0: 0.5 / 1.0 / 1.5 and the lower end
        SliderFeel(0.3f..1.5f, defaultValue = 1f).let {
            assertEquals(0.05f, it.tickStep)
            assertEquals(0.5f, it.roundStep)
            assertEquals(listOf(0.3f, 0.5f, 1f, 1.5f), it.detents)
        }
        // font scale 0.5..1.5 (shown in percent): every 10 %
        assertEquals((5..15).map { it / 10f }, SliderFeel(0.5f..1.5f, defaultValue = 1f).detents)
        // key vibration -1..100 ms, default -1 (system): multiples of 10 ms and -1; 0 lies within the
        // zones of -1 and would hold the finger before the end (review), so -1 wins
        SliderFeel(-1f..100f, defaultValue = -1f, integer = true).let {
            assertEquals(5f, it.tickStep)
            assertEquals(listOf(-1f) + (1..10).map { i -> i * 10f }, it.detents)
        }
        // long press 100..700 ms: multiples of 100
        assertEquals(listOf(100f, 200f, 300f, 400f, 500f, 600f, 700f), SliderFeel(100f..700f, defaultValue = 300f, integer = true).detents)
        // a default outside the range is no detent (Android version 21..36, default 0); whole-number ticks
        SliderFeel(21f..36f, defaultValue = 0f, integer = true).let {
            assertEquals(1f, it.tickStep)
            assertEquals(listOf(21f, 25f, 30f, 35f, 36f), it.detents)
        }
        // discrete: gesture trail 100..1900 in steps of 10: a tick every 5th step (36 over the range)
        SliderFeel(100f..1900f, steps = 179, defaultValue = 800f, integer = true).let {
            assertEquals(10f, it.stepSize)
            assertEquals(50f, it.tickStep)
            assertEquals(listOf(100f, 500f, 800f, 1000f, 1500f, 1900f), it.detents)
        }
        // design editor key height 0.5..1.5 in 20 steps: round values at least four steps apart
        SliderFeel(0.5f..1.5f, steps = 19, defaultValue = 1f).let {
            assertEquals(0.05f, it.tickStep)
            assertEquals(listOf(0.5f, 1f, 1.5f), it.detents)
        }
        // detents of a discrete slider lie on its steps
        assertEquals(listOf(0f, 5f, 6f, 10f, 15f, 20f, 24f), SliderFeel(0f..24f, steps = 23, defaultValue = 6f).detents)
        // the caller may choose the round values
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), SliderFeel(0f..1f, roundStep = 0.25f).detents)
    }

    @Test fun aSlowDragTicksBetweenAndFeelsEveryDetent() {
        val feel = SliderFeel(0f..100f, defaultValue = 50f)
        val drag = drag(feel)
        drag.begin(0f)
        val shown = mutableListOf<Float>()
        var raw = 0f
        while (raw < 100f) {
            raw += 0.25f
            sink.now += 30
            shown += drag.next(raw)
        }
        assertEquals(1, sink.starts)
        // 10, 20 … 100 are entered; the ticks every 5 in between are 5, 15 … 95
        assertEquals(10, sink.count(Impulse.DETENT))
        assertEquals(10, sink.count(Impulse.TICK))
        assertEquals(100f, shown.last(), "the end is reached exactly")
        assertTrue(shown.none { value -> feel.detents.any { it != value && abs(it - value) <= feel.radius } },
            "near a detent the value is the detent itself")
    }

    @Test fun aQuickDragTicksAtMostEvery25Milliseconds() {
        val drag = drag(SliderFeel(0f..100f))
        drag.begin(0f)
        var raw = 0f
        while (raw < 100f) {
            raw += 2f
            sink.now += 5
            drag.next(raw)
        }
        val ticks = sink.played.filter { it.second == Impulse.TICK }.map { it.first }
        val detents = sink.played.filter { it.second == Impulse.DETENT }.map { it.first }
        assertTrue(ticks.zipWithNext().all { (a, b) -> b - a >= SliderFeel.MIN_GAP_MS })
        assertTrue(detents.zipWithNext().all { (a, b) -> b - a >= SliderFeel.MIN_GAP_MS })
        // a tick never follows a detent within the gap (a detent may follow a tick at once)
        assertTrue(ticks.all { tick -> detents.none { tick > it && tick - it < SliderFeel.MIN_GAP_MS } })
        assertTrue(sink.played.size < 20, "fewer impulses than boundaries: ${sink.played.size}")
        assertTrue(detents.isNotEmpty())
    }

    @Test fun theValueSnapsInAndIsLetGoOnlyBeyondTheZone() {
        val drag = drag(SliderFeel(0f..100f, defaultValue = 50f))
        drag.begin(40f)
        sink.now = 100
        assertEquals(47f, drag.next(47f))
        assertEquals(50f, drag.next(48.6f), "pulled in")
        assertEquals(Impulse.DETENT, sink.last)
        assertEquals(50f, drag.next(51.4f), "held while the finger is inside the zone")
        assertEquals(51.6f, drag.next(51.6f), "let go past the zone")
        // coming back slowly reaches the values next to the detent
        assertEquals(50.8f, drag.next(50.8f))
        assertEquals(49.3f, drag.next(49.3f))
        // once the finger was far enough away, the detent pulls again
        assertEquals(45f, drag.next(45f))
        assertEquals(50f, drag.next(49f))
        // leaving to the side it came from lets go as well
        assertEquals(48.4f, drag.next(48.4f))
        assertEquals(49.2f, drag.next(49.2f))
    }

    @Test fun aStartNextToADetentDoesNotJumpButAStartOnItHolds() {
        val feel = SliderFeel(0f..100f, defaultValue = 50f)
        drag(feel).let {
            it.begin(49.2f)
            assertEquals(49.4f, it.next(49.4f), "a value set before next to the detent stays reachable")
            assertEquals(50.7f, it.next(50.7f))
        }
        drag(feel).let {
            it.begin(50f)
            assertEquals(50f, it.heldAt)
            assertEquals(50f, it.next(50.9f), "a touch a little off the thumb keeps the detent")
            assertEquals(52f, it.next(52f))
        }
        assertTrue(sink.played.none { it.second == Impulse.DETENT }, "starting at or next to a detent is not entering it")
    }

    @Test fun aMoveAcrossADetentIsFeltThoughItDoesNotHold() {
        val drag = drag(SliderFeel(0f..100f, defaultValue = 50f))
        drag.begin(42f)
        assertEquals(57f, drag.next(57f))
        assertEquals(listOf(Impulse.DETENT), sink.played.map { it.second })
    }

    @Test fun aDiscreteSliderGivesTheDetentImpulseOnTheDetentStep() {
        // key gap of the design editor: 0.5..2.5 in 20 steps, default 1.0
        val feel = SliderFeel(0.5f..2.5f, steps = 19, defaultValue = 1f)
        assertEquals(listOf(0.5f, 1f, 1.5f, 2f, 2.5f), feel.detents)
        val drag = drag(feel)
        drag.begin(0.5f)
        val impulses = mutableListOf<Impulse?>()
        for (i in 1..20) {
            sink.now += 30
            val before = sink.played.size
            val step = 0.5f + 2f * i / 20f
            assertEquals(step, drag.next(step), "steps are wider than the zone: no snapping")
            impulses += sink.played.getOrNull(before)?.second
        }
        assertEquals(listOf(5, 10, 15, 20), impulses.withIndex().filter { it.value == Impulse.DETENT }.map { it.index + 1 })
        assertEquals(16, impulses.count { it == Impulse.TICK })
    }

    @Test fun anEmptyRangeHasNothingToFeel() {
        val feel = SliderFeel(1f..1f, defaultValue = 1f)
        assertEquals(emptyList(), feel.detents)
        val drag = drag(feel)
        drag.begin(1f)
        assertEquals(1f, drag.next(1f))
        assertEquals(emptyList(), sink.played)
    }
}
