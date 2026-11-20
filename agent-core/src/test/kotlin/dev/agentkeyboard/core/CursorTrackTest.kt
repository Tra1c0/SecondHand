// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.CursorTrack.Axis
import dev.agentkeyboard.core.CursorTrack.Step
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 87: the pure cursor core. Finger paths in, steps out; no Android. */
class CursorTrackTest {
    /** A track with a simulated finger. Time starts at 1000 ms; positions are dp from the touch-down point. */
    private class Sim(magnetism: Float = CursorTrack.DEFAULT_MAGNETISM, horizontalOnly: Boolean = false,
                      before: String? = null, after: String = "", atStart: Boolean = true, atEnd: Boolean = true) {
        val track = CursorTrack(horizontalOnly, magnetism)
        var t = 1000L
        var x = 0.0
        var y = 0.0
        val steps = ArrayList<Step>()
        /** The finger position (x, y) when each step came out. */
        val at = ArrayList<Pair<Double, Double>>()
        init {
            track.begin(t)
            if (before == null) track.setTextUnavailable() else track.setText(before, after, atStart, atEnd)
        }

        fun feed(nx: Double, ny: Double, time: Long) {
            for (s in track.moveTo(nx, ny, time)) { steps += s; at += nx to ny }
            x = nx; y = ny; t = time
        }

        /** Straight move to ([tx], [ty]) in [ms] at [hz] samples per second. */
        fun to(tx: Double, ty: Double, ms: Long, hz: Int = 250) {
            val n = maxOf(1, (ms * hz / 1000.0).roundToLong().toInt())
            val x0 = x; val y0 = y; val t0 = t
            for (i in 1..n) feed(x0 + (tx - x0) * i / n, y0 + (ty - y0) * i / n, t0 + ms * i / n)
        }

        /** Move by ([dx], [dy]) at a constant [dps] (dp/s). */
        fun by(dx: Double, dy: Double, dps: Double, hz: Int = 250) {
            val ms = (kotlin.math.hypot(dx, dy) / dps * 1000).roundToLong().coerceAtLeast(1)
            to(x + dx, y + dy, ms, hz)
        }

        fun pause(ms: Long) { t += ms }
        val horizontal get() = steps.filter { it.axis == Axis.HORIZONTAL }
        val vertical get() = steps.filter { it.axis == Axis.VERTICAL }
    }

    private val slow = 40.0 // dp/s: far below where the curve starts

    // ---- speed curve

    @Test fun `slow finger moves one cluster per 13 dp`() {
        val s = Sim()
        s.by(66.0, 0.0, slow)
        assertEquals(5, s.horizontal.size)
        assertTrue(s.steps.all { it.direction == 1 })
    }

    @Test fun `precision at a slow finger - 12 dp is nothing, 15 dp is exactly one cluster`() {
        val a = Sim(); a.by(12.0, 0.0, slow)
        assertEquals(0, a.steps.size)
        val b = Sim(); b.by(15.0, 0.0, slow)
        assertEquals(1, b.steps.size)
    }

    @Test fun `gain is 1 when slow, the maximum when fast, monotone and without jumps`() {
        assertEquals(1.0, CursorTrack.gain(0.0, 4.0))
        assertEquals(1.0, CursorTrack.gain(CursorTrack.GAIN_FROM_DPS, 4.0))
        assertEquals(4.0, CursorTrack.gain(CursorTrack.GAIN_TO_DPS, 4.0), 1e-9)
        assertEquals(4.0, CursorTrack.gain(5000.0, 4.0), 1e-9)
        var previous = CursorTrack.gain(0.0, 4.0)
        var v = 0.5
        while (v <= 3000.0) {
            val g = CursorTrack.gain(v, 4.0)
            assertTrue(g >= previous - 1e-12, "monotone at $v")
            assertTrue(g - previous < 0.01, "no jump at $v: ${g - previous}")
            previous = g; v += 0.5
        }
        // the detent factor falls the other way, also smoothly
        var d = CursorTrack.detentFactor(0.0)
        assertEquals(1.0, d)
        assertEquals(1.0, CursorTrack.detentFactor(CursorTrack.DETENT_FULL_BELOW_DPS))
        assertEquals(0.0, CursorTrack.detentFactor(CursorTrack.DETENT_NONE_ABOVE_DPS), 1e-9)
        v = 0.5
        while (v <= 1000.0) {
            val f = CursorTrack.detentFactor(v)
            assertTrue(f <= d + 1e-12 && d - f < 0.01, "detent factor at $v")
            d = f; v += 0.5
        }
    }

    @Test fun `a fast finger accelerates - the same distance gives clearly more steps`() {
        val slowRun = Sim(); slowRun.by(300.0, 0.0, slow)
        val fastRun = Sim(); fastRun.by(300.0, 0.0, 2000.0)
        assertTrue(fastRun.steps.size * 2 >= 5 * slowRun.steps.size, "fast ${fastRun.steps.size} vs slow ${slowRun.steps.size}")
        assertTrue(fastRun.steps.size <= (300 / CursorTrack.HORIZONTAL_STEP_DP * CursorTrack.HORIZONTAL_GAIN).toInt() + 1)
    }

    @Test fun `a ramp from slow to fast has no sudden jumps in steps per sample`() {
        val s = Sim()
        val perSample = ArrayList<Int>()
        // speed rises linearly from 0 to 2000 dp/s within one second, sampled at 120 Hz
        var x = 0.0
        var time = 1000L
        val dt = 1000L / 120
        for (i in 1..120) {
            time += dt
            val v = 2000.0 * (time - 1000) / 1000.0
            x += v * dt / 1000.0
            perSample += s.track.moveTo(x, 0.0, time).size
        }
        assertTrue(perSample.max() <= 7, "most steps in one sample: ${perSample.max()}")
        // the curve is smooth: compare 10-sample sums of neighbours
        val blocks = perSample.chunked(10).map { it.sum() }
        for (i in 1 until blocks.size) assertTrue(blocks[i] >= blocks[i - 1] - 2, "blocks $blocks")
        for (i in 1 until blocks.size) assertTrue(blocks[i] - blocks[i - 1] <= 14, "no jump in $blocks")
    }

    // ---- axis

    @Test fun `a slightly diagonal horizontal drag never changes line`() {
        val s = Sim()
        s.by(200.0, 90.0, 150.0) // about 24 degrees off horizontal
        assertTrue(s.horizontal.size > 10)
        assertEquals(0, s.vertical.size)
        assertEquals(Axis.HORIZONTAL, s.track.currentAxis)
    }

    @Test fun `a deliberate vertical move switches the axis without lifting, and back again`() {
        val s = Sim()
        s.by(60.0, 0.0, 100.0)
        val horizontalBefore = s.horizontal.size
        assertTrue(horizontalBefore >= 3)
        s.by(0.0, 130.0, 100.0)
        assertEquals(Axis.VERTICAL, s.track.currentAxis)
        assertTrue(s.vertical.size in 3..5, "lines: ${s.vertical.size}")
        assertTrue(s.vertical.all { it.direction == 1 })
        val horizontalAfterVertical = s.horizontal.size
        assertTrue(horizontalAfterVertical - horizontalBefore <= 1, "the old axis stops")
        s.track.setTextUnavailable(s.t) // the app has read the editor again (after a vertical step horizontal steps wait for that)
        s.by(-120.0, 0.0, 100.0)
        assertEquals(Axis.HORIZONTAL, s.track.currentAxis)
        assertTrue(s.horizontal.drop(horizontalAfterVertical).count { it.direction == -1 } >= 5)
    }

    @Test fun `upwards is negative and a line needs more travel than a cluster`() {
        val up = Sim(); up.by(0.0, -135.0, slow)
        assertTrue(up.steps.all { it.axis == Axis.VERTICAL && it.direction == -1 })
        assertEquals(5, up.vertical.size) // 135 dp / 26 dp
        val side = Sim(); side.by(135.0, 0.0, slow)
        assertEquals(10, side.horizontal.size)
        assertTrue(side.horizontal.size >= 2 * up.vertical.size)
    }

    @Test fun `no decision before eight dp of path`() {
        val s = Sim()
        s.by(5.0, 2.0, slow)
        assertEquals(null, s.track.currentAxis)
        assertEquals(0, s.steps.size)
    }

    @Test fun `the travel made on the other axis is not lost at the switch`() {
        // 40 dp sideways, then straight down: the first line comes sooner than switch + one whole line
        val s = Sim()
        s.by(40.0, 0.0, 100.0)
        val y0 = s.y
        s.by(0.0, 60.0, 100.0)
        val firstLineAt = s.at[s.steps.indexOfFirst { it.axis == Axis.VERTICAL }].second
        // without the credit it would take the 19 dp of the switch plus a whole line (45 dp)
        assertTrue(firstLineAt - y0 < 38.0, "first line after ${firstLineAt - y0} dp of vertical travel")
    }

    @Test fun `horizontal-only ignores vertical travel also for the speed`() {
        val a = Sim(horizontalOnly = true); a.by(-60.0, 0.0, 40.0)
        val b = Sim(horizontalOnly = true); b.by(-60.0, 400.0, 400.0) // the same sideways 60 dp at 6 dp/s, a fast finger downwards
        assertEquals(a.horizontal.size, b.horizontal.size)
        assertTrue(b.horizontal.isNotEmpty())
    }

    @Test fun `delete mode is horizontal only`() {
        val s = Sim(horizontalOnly = true)
        s.by(0.0, 200.0, 100.0)
        assertEquals(0, s.steps.size)
        s.by(60.0, 0.0, 100.0)
        assertTrue(s.steps.isNotEmpty() && s.steps.all { it.axis == Axis.HORIZONTAL })
    }

    // ---- magnetism

    private val hello = "Hallo Welt"

    /** Finger distances between consecutive steps of a slow drag to the left through [text] from its end. */
    private fun leftIntervals(magnetism: Float, text: String = hello): List<Double> {
        val s = Sim(magnetism, before = text)
        s.by(-13.0 * text.length - 40, 0.0, slow, hz = 250)
        val xs = s.at.map { -it.first }
        return xs.zipWithNext { a, b -> b - a }
    }

    @Test fun `leaving a word edge costs the default 35 percent of a step more`() {
        val gaps = leftIntervals(0.35f)
        // steps: t l e W | (start of Welt = edge) then ' ' | o ... ; the step that leaves position 6 and the one leaving 5
        val extra = gaps.filter { it > 16.0 }
        assertTrue(extra.isNotEmpty(), "gaps: $gaps")
        for (g in extra) assertEquals(13.0 * 1.35, g, 1.0)
        // all others are one plain step
        for (g in gaps.filter { it <= 16.0 }) assertEquals(13.0, g, 0.6)
    }

    @Test fun `magnetism 0 means no detent at all`() {
        val gaps = leftIntervals(0f)
        assertTrue(gaps.all { abs(it - 13.0) < 0.6 }, "gaps: $gaps")
    }

    @Test fun `magnetism 100 percent costs one more step of travel`() {
        val gaps = leftIntervals(1f)
        assertTrue(gaps.any { abs(it - 26.0) < 1.0 }, "gaps: $gaps")
        assertTrue(gaps.none { it > 27.0 })
    }

    /** Positions (chars from the text start) of the steps that landed on a word edge, for a drag from the end of [text]. */
    private fun edgePositions(sim: Sim, textLength: Int): List<Int> {
        var pos = textLength
        val out = ArrayList<Int>()
        for (st in sim.steps) { pos += st.chars; if (st.boundary) out += pos }
        return out
    }

    @Test fun `inside a word there is no detent, also with an apostrophe`() {
        val text = "don't stop"
        val s = Sim(1f, before = text)
        s.by(-13.0 * 10 - 60, 0.0, slow)
        assertEquals(10, s.steps.size)
        assertEquals(listOf(6, 5, 0), edgePositions(s, text.length)) // the apostrophe (3) and the letters are no edges
        val gaps = s.at.map { -it.first }.zipWithNext { a, b -> b - a }
        assertEquals(2, gaps.count { it > 20 }, "gaps: $gaps")
    }

    @Test fun `the start position is free - only a step reached by stepping sticks`() {
        // cursor right after "Hallo", which is an edge; the first step to the right costs one plain step
        val s = Sim(1f, before = "Hallo", after = " Welt")
        s.by(60.0, 0.0, slow)
        assertTrue(s.at[0].first < 17.0, "first step at ${s.at[0].first}")
        // it landed on the edge before "W": leaving it costs two steps of travel
        assertTrue(s.at[1].first - s.at[0].first > 24.0, "second step after ${s.at[1].first - s.at[0].first} dp")
    }

    @Test fun `fast sweeps are not slowed by the detent`() {
        val text = "one two three four five six seven eight nine ten"
        val with = Sim(1f, before = text); with.by(-300.0, 0.0, 1500.0)
        val without = Sim(0f, before = text); without.by(-300.0, 0.0, 1500.0)
        assertEquals(without.steps.size, with.steps.size)
        assertTrue(with.steps.none { it.detent }, "no stronger tick at a fast finger")
        assertTrue(with.steps.any { it.boundary })
    }

    @Test fun `the stronger tick is flagged only when a slow finger reaches an edge`() {
        val s = Sim(before = hello)
        s.by(-13.0 * 10 - 40, 0.0, slow)
        assertEquals(10, s.steps.size)
        // from the end: 9, 8, 7, 6 (edge), 5 (edge), 4 ... 0 (start of the text)
        assertEquals(listOf(3, 4, 9), s.steps.withIndex().filter { it.value.detent }.map { it.index })
        assertEquals(listOf(3, 4, 9), s.steps.withIndex().filter { it.value.boundary }.map { it.index })
    }

    @Test fun `punctuation and whitespace are edges, a word at the text start too`() {
        val s = Sim(before = "ab, cd")
        s.by(-13.0 * 6 - 60, 0.0, slow)
        val edges = s.steps.withIndex().filter { it.value.boundary }.map { 6 + s.steps.take(it.index + 1).sumOf { x -> x.chars } }
        // positions: 4 (before cd), 3 (after ','), 2 (after ab), 0 (start of text)
        assertEquals(listOf(4, 3, 2, 0), edges)
    }

    // ---- jitter, carry

    @Test fun `jitter around a step threshold cannot flip the cursor back and forth`() {
        val s = Sim()
        s.by(12.4, 0.0, slow) // just under the first step
        var sign = 1.0
        repeat(40) { s.by(4.0 * sign, 0.0, 60.0); sign = -sign } // +-2 dp around the threshold, at a finger's trembling pace
        assertEquals(1, s.steps.size, "steps: ${s.steps}")
        assertEquals(1, s.steps.first().direction)
    }

    @Test fun `a reversal swallows a little play, then needs a whole step back`() {
        val s = Sim()
        s.by(15.0, 0.0, slow)
        assertEquals(1, s.steps.size)
        val n = s.steps.size
        s.by(-15.0, 0.0, slow)
        assertEquals(n, s.steps.size, "15 dp back is not enough (2 dp left over, play 3 dp, one step of 13 dp: 18 dp)")
        s.by(-4.0, 0.0, slow)
        assertEquals(n + 1, s.steps.size)
        assertEquals(-1, s.steps.last().direction)
    }

    @Test fun `the remainder is carried - however a distance is split, the steps are the same`() {
        fun run(pieces: List<Double>): Int {
            val s = Sim()
            for (p in pieces) { s.by(p, 0.0, slow); s.pause(500) }
            return s.steps.size
        }
        val total = 44.0 // 3.38 steps of travel
        val whole = run(listOf(total))
        assertEquals(whole, run(listOf(total / 2, total / 2)))
        assertEquals(whole, run(List(7) { total / 7 }))
        assertEquals(whole, run(List(20) { total / 20 }))
        assertEquals(3, whole)
    }

    // ---- text: graphemes, edges

    @Test fun `steps are whole grapheme clusters - emoji sequences, flags, accents`() {
        val family = "👨‍👩‍👧" // 8 chars
        val flag = "\uD83C\uDDE9\uD83C\uDDEA" // DE, 4 chars
        val text = "a${family}b${flag}c éx"
        val s = Sim(0f, before = text)
        s.by(-13.0 * 12 - 20, 0.0, slow)
        assertEquals(listOf(-1, -2, -1, -1, -4, -1, -8, -1), s.steps.map { it.chars })
        assertEquals(0, text.length + s.steps.sumOf { it.chars }) // and it ended at the start
    }

    @Test fun `going right over a combining accent steps over the whole cluster`() {
        val s = Sim(0f, before = "", after = "é́x")
        s.by(13.0 * 3, 0.0, slow)
        assertEquals(listOf(3, 1), s.steps.map { it.chars })
    }

    @Test fun `without text the steps still come, without chars and without detents`() {
        val a = Sim(1f, before = hello); a.by(-130.0, 0.0, slow)
        val b = Sim(1f); b.by(-130.0, 0.0, slow)
        assertTrue(b.steps.isNotEmpty())
        assertTrue(b.steps.all { it.chars == 0 && !it.detent && !it.boundary })
        // without text it moves one step per 13 dp, exactly what the text version does inside a word
        val plain = Sim(1f, before = "abcdefghijklmnopqrstuvwxyz"); plain.by(-130.0, 0.0, slow)
        assertEquals(plain.steps.size, b.steps.size)
        assertTrue(a.steps.size < b.steps.size, "the detents of the text version cost travel")
    }

    @Test fun `the end of the text stops the cursor without wind-up`() {
        val s = Sim(0f, before = "abc")
        s.by(-44.0, 0.0, slow)
        assertEquals(3, s.steps.size)
        s.by(-300.0, 0.0, slow) // far beyond the start
        assertEquals(3, s.steps.size)
        s.by(45.0, 0.0, slow) // straight back: 3 steps for 45 dp, not nothing because of 300 dp of wind-up
        assertEquals(3, s.steps.count { it.direction == 1 })
    }

    @Test fun `a forward limit holds the cursor (the delete slider cannot select past its start)`() {
        val s = Sim(0f, horizontalOnly = true, before = "hello world", after = " and more text after it") // text beyond the slide's start exists
        s.track.forwardLimitChars = 0 // nothing selected yet: no step to the right
        s.by(-13.0 * 5 - 5, 0.0, slow)
        assertEquals(5, s.steps.size)
        s.by(13.0 * 9, 0.0, slow) // sliding back to the right un-selects, but never beyond the start
        var pos = 0
        for (st in s.steps) { pos += st.chars; assertTrue(pos <= 0, "beyond the start: $pos") }
        assertEquals(0, pos)
    }

    @Test fun `a truncated window asks for text near its edge and a cut edge is no word edge`() {
        val s = Sim(1f, before = "x".repeat(30), atStart = false)
        assertFalse(s.track.needsText(1100))
        s.by(-13.0 * 8, 0.0, slow)
        assertTrue(s.track.needsText(s.t + 200))
        // the cut edge itself is never a stop: walking into it asks for text instead of stepping out of the window
        s.track.setText("y".repeat(60), "", atStart = false, atEnd = true, tMs = s.t + 200)
        assertFalse(s.track.needsText(s.t + 400))
        // a cut edge is no stop: at the end of a truncated window the cursor waits for text
        val cut = Sim(0f, before = "ab", atStart = false)
        cut.by(-13.0 * 5, 0.0, slow)
        assertEquals(2, cut.steps.size)
        assertTrue(cut.track.needsText(cut.t + 200))
    }

    @Test fun `after a vertical step the track asks for text again`() {
        val s = Sim(1f, before = hello)
        s.by(0.0, 60.0, slow)
        assertEquals(Axis.VERTICAL, s.track.currentAxis)
        assertTrue(s.vertical.isNotEmpty())
        assertFalse(s.track.hasText)
        assertTrue(s.track.needsText(s.t + 200))
        s.track.setText("new text before ", "after", true, true, s.t + 200)
        assertTrue(s.track.hasText)
        assertFalse(s.track.needsText(s.t + 220))
    }

    @Test fun `horizontal travel right after a vertical step waits for the text and is then applied, not lost`() {
        val s = Sim(0f, before = "aaa bbb ccc\nddd eee fff")
        s.by(0.0, 40.0, slow)
        val lines = s.vertical.size
        assertTrue(lines >= 1)
        val before = s.steps.size
        s.by(-80.0, 0.0, slow) // the editor has not told the new cursor position yet: nothing is applied to a stale one
        assertEquals(before, s.steps.size, "no horizontal step before the text is known")
        s.track.setText("aaa bbb ccc\nddd", " eee fff", true, true, s.t)
        s.by(-1.0, 0.0, slow) // the next movement releases the carried travel against the real position
        val horizontal = s.horizontal
        assertTrue(horizontal.size >= 4, "carried travel applied: ${horizontal.size}")
        assertTrue(horizontal.all { it.chars == -1 && it.direction == -1 })
        // also when the editor cannot be read at all: held until the app says so, then unlabelled steps
        val u = Sim(0f, before = "abc\ndef")
        u.by(0.0, 40.0, slow)
        val n = u.steps.size
        u.by(-60.0, 0.0, slow)
        assertEquals(n, u.steps.size)
        u.track.setTextUnavailable(u.t)
        u.by(-1.0, 0.0, slow)
        assertTrue(u.horizontal.isNotEmpty() && u.horizontal.all { it.chars == 0 })
    }

    // ---- determinism

    private fun plan(track: CursorTrack, hz: Int, durMs: Long, pathFn: (Double) -> Double): List<String> {
        val out = ArrayList<String>()
        track.begin(0)
        track.setText(hello.repeat(30), "", true, true)
        val times = ArrayList<Long>()
        var k = 1
        while (true) {
            val tt = (k * 1000.0 / hz).roundToLong()
            if (tt >= durMs) break
            times += tt; k++
        }
        times += durMs
        for (tt in times) for (s in track.moveTo(pathFn(tt.toDouble() / durMs), 0.0, tt)) out += "${s.axis}${s.direction}:${s.chars}:${s.detent}"
        return out
    }

    @Test fun `the same straight path gives the same moves at 50, 100 and 200 Hz and at 60 and 120 Hz`() {
        val line = { f: Double -> -400.0 * f }
        val reference = plan(CursorTrack(), 200, 800, line)
        assertTrue(reference.size > 10)
        for (hz in listOf(50, 100, 60, 120, 90, 144)) assertEquals(reference, plan(CursorTrack(), hz, 800, line), "$hz Hz")
    }

    @Test fun `a smooth accelerating and slowing path gives the same moves at 60 and 120 Hz within one step`() {
        val bell = { f: Double -> -520.0 * (1 - cos(PI * f)) / 2 }
        val a = plan(CursorTrack(), 60, 900, bell)
        val b = plan(CursorTrack(), 120, 900, bell)
        val c = plan(CursorTrack(), 240, 900, bell)
        assertTrue(abs(a.size - b.size) <= 1, "60 Hz ${a.size}, 120 Hz ${b.size}")
        assertTrue(abs(c.size - b.size) <= 1, "240 Hz ${c.size}, 120 Hz ${b.size}")
        assertTrue(a.size > 30)
    }

    @Test fun `a long pause without events is a rest, not a speed spike`() {
        val s = Sim()
        s.by(15.0, 0.0, slow)
        val n = s.steps.size
        s.pause(3000)
        s.feed(s.x + 20.0, 0.0, s.t + 40) // 20 dp within 40 ms after 3 s of rest
        assertTrue(s.steps.size - n <= 3, "steps after the pause: ${s.steps.size - n}")
    }

    @Test fun `magnetism is clamped to 0 to 100 percent`() {
        val t = CursorTrack(magnetism = 5f)
        assertEquals(1f, t.magnetism)
        t.magnetism = -2f
        assertEquals(0f, t.magnetism)
        assertEquals(0.35f, CursorTrack.magnetismOfPercent(35))
    }
}
