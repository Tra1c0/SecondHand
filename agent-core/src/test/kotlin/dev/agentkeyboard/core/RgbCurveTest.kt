// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

/** Stage 29d: the own curve (monotone cubic through control points), its limits, text form and the migration of `wave=`. */
class RgbCurveTest {
    private fun curve(vararg xy: Float) = RgbCurve.of(xy.toList().chunked(2) { RgbCurve.Point(it[0], it[1]) })!!

    @Test fun runsThroughEveryPointAndNeverOvershoots() {
        val c = curve(0f, 0f, 0.3f, 0.9f, 0.55f, 0.95f, 0.7f, 0.2f, 0.85f, 0.6f, 1f, 1f)
        for (p in c.points) assertEquals(p.y, c.value(p.x.coerceAtMost(0.99999f)).let { if (p.x == 1f) c.value(1f - 1e-6f) else it }, 0.002f)
        // monotone between points: within the range of the two neighbours, no wiggles
        for (i in 0 until c.points.size - 1) {
            val a = c.points[i]
            val b = c.points[i + 1]
            var last = a.y
            for (k in 1..49) { // x = 1 itself is the start of the next cycle
                val v = c.value(a.x + (b.x - a.x) * k / 50f)
                assertTrue(v >= minOf(a.y, b.y) - 1e-5f && v <= maxOf(a.y, b.y) + 1e-5f, "segment $i")
                if (b.y >= a.y) assertTrue(v >= last - 1e-5f) else assertTrue(v <= last + 1e-5f)
                last = v
            }
        }
        // the sketch of the owner: rises, peak, deep dip, rises again; never outside 0..1
        val sketch = curve(0f, 0f, 0.35f, 0.68f, 0.6f, 0.86f, 0.7f, 0.34f, 0.84f, 0.5f, 1f, 0.82f)
        for (i in 0..1000) assertTrue(sketch.value(i / 1000f) in 0f..1f)
    }

    @Test fun isSmoothWithoutJumps() {
        // 29b: neighbouring samples differ only by the slope, no sudden steps
        val c = curve(0f, 0.1f, 0.2f, 0.8f, 0.5f, 0.3f, 0.8f, 0.9f, 1f, 0.1f)
        val n = 2000
        var worst = 0f
        for (i in 1 until n) worst = maxOf(worst, abs(c.value(i / n.toFloat()) - c.value((i - 1) / n.toFloat())))
        assertTrue(worst < 0.01f, "largest step $worst")
        // a closed curve also wraps without a jump
        assertTrue(abs(c.value(0.9999f) - c.value(0f)) < 0.002f)
        assertTrue(c.closed)
    }

    @Test fun limitsArePinned() {
        // ends stay at x = 0 and x = 1, values clamp, x sorted, too close points dropped
        val c = RgbCurve.of(listOf(RgbCurve.Point(0.2f, 2f), RgbCurve.Point(0.5f, -1f), RgbCurve.Point(0.505f, 0.5f), RgbCurve.Point(0.9f, 0.4f)))!!
        assertEquals(listOf(RgbCurve.Point(0f, 1f), RgbCurve.Point(0.5f, 0f), RgbCurve.Point(1f, 0.4f)), c.points)
        assertNull(RgbCurve.of(listOf(RgbCurve.Point(0f, 0f))), "one point")
        assertNull(RgbCurve.of(listOf(RgbCurve.Point(0f, Float.NaN), RgbCurve.Point(1f, 0f))), "NaN")
        assertNull(RgbCurve.of(List(RgbCurve.MAX_POINTS + 1) { RgbCurve.Point(it / 30f, 0f) }), "too many")
        assertNull(RgbCurve.of(listOf(RgbCurve.Point(0.5f, 0f), RgbCurve.Point(0.502f, 1f))), "both points collapse")
    }

    @Test fun movingKeepsOrderAndEnds() {
        val c = curve(0f, 0f, 0.5f, 1f, 1f, 0f)
        // the ends move only up and down
        assertEquals(RgbCurve.Point(0f, 0.3f), c.move(0, 0.4f, 0.3f).points[0])
        assertEquals(RgbCurve.Point(1f, 0.7f), c.move(2, 0.2f, 0.7f).points[2])
        // a middle point cannot pass its neighbours, and values clamp to 0..1
        assertEquals(RgbCurve.Point(0.99f, 1f), c.move(1, 5f, 3f).points[1])
        assertEquals(RgbCurve.Point(0.01f, 0f), c.move(1, -1f, -2f).points[1])
        assertEquals(c, c.move(7, 0.5f, 0.5f), "unknown index")
        assertEquals(c.move(1, 0.2f, Float.NaN).points[1].y, 0f, "NaN counts as 0")
    }

    @Test fun addingAndRemovingPoints() {
        val c = curve(0f, 0f, 1f, 1f)
        val (added, index) = c.add(0.4f, 0.9f)
        assertEquals(1, index)
        assertEquals(RgbCurve.Point(0.4f, 0.9f), added.points[1])
        assertEquals(-1, added.add(0.405f, 0.2f).second, "too close to a point")
        // the + button puts the point on the curve in the widest gap
        val (plus, at) = added.addInWidestGap()
        assertEquals(RgbCurve.Point(0.7f, added.value(0.7f).let { (it * 1000).toInt() / 1000f }).x, plus.points[at].x)
        assertEquals(added.value(0.7f), plus.points[at].y, 0.001f)
        // removing: never an end, never below two points
        assertEquals(c, added.remove(1))
        assertFalse(c.canRemove(0))
        assertEquals(c, c.remove(0))
        assertEquals(c, c.remove(1))
        assertTrue(added.canRemove(1))
        // full: no more points
        var full = c
        while (full.points.size < RgbCurve.MAX_POINTS) full = full.addInWidestGap().first
        assertEquals(-1, full.addInWidestGap().second)
    }

    @Test fun textRoundTripAndValidation() {
        for (w in RgbWaveform.entries) {
            val p = RgbCurve.preset(w)
            assertEquals(p, RgbCurve.parse(p.encode()), "$w")
        }
        assertEquals("0:0,1:1", RgbCurve.preset(RgbWaveform.SAWTOOTH).encode())
        assertEquals("0:0,0.3:1,0.45:1,0.75:0,1:0", RgbCurve.preset(RgbWaveform.PULSE).encode())
        val own = curve(0f, 0.123f, 0.333f, 0.5f, 1f, 0.75f)
        assertEquals(own, RgbCurve.parse(own.encode()))
        for (bad in listOf(null, "", "0:0", "0:0;1:1", "a:b,1:1", "0:0,1", "0:0,NaN:1", "0:0,Infinity:1",
            List(RgbCurve.MAX_POINTS + 1) { "${it / 30f}:0" }.joinToString(","), "0:0," + "1".repeat(500) + ":1"))
            assertNull(RgbCurve.parse(bad), "$bad")
        // out of range values are clamped, unsorted points sorted
        assertEquals(curve(0f, 1f, 0.5f, 0f, 1f, 0f), RgbCurve.parse("1:0,0.5:-3,0:7"))
    }

    @Test fun presetsAreRecognised() {
        for (w in RgbWaveform.entries) assertEquals(w, RgbCurve.presetOf(RgbCurve.preset(w)))
        for (n in 2..12) assertEquals(n, RgbCurve.stepCount(RgbCurve.preset(RgbWaveform.STEPS, n)))
        assertEquals(RgbCurve.MAX_POINTS, RgbCurve.preset(RgbWaveform.STEPS, 12).points.size)
        assertNull(RgbCurve.presetOf(curve(0f, 0f, 0.4f, 0.9f, 1f, 1f)))
        // palette wrapping: rising curves wrap, back-and-forth curves do not
        assertTrue(RgbCurve.preset(RgbWaveform.SAWTOOTH).cyclic)
        assertTrue(RgbCurve.preset(RgbWaveform.STEPS).cyclic)
        for (w in listOf(RgbWaveform.SINE, RgbWaveform.TRIANGLE, RgbWaveform.PULSE)) assertFalse(RgbCurve.preset(w).cyclic, "$w")
    }

    @Test fun stageTwentySixTextBecomesThePresetCurve() {
        // exactly what stage 26 wrote for each waveform
        for (w in RgbWaveform.entries) {
            val old = "v=1;on=1;pattern=CIRCLE;wave=${w.name};steps=6;period=4000;reverse=0;width=1.0;palette=rainbow;" +
                "colors=FF0050,00C8FF,9B4DFF;sat=100;bright=100;mix=100;layers=KEY_BACKGROUND;areas=LETTERS,FUNCTIONAL,SPACE,NUMBER_ROW;" +
                "tap=NONE;reactive=0;glow=800;fps=30;saver=1"
            val s = RgbSettings.parse(old)
            assertEquals(RgbCurve.preset(w, 6), s.curve, "$w")
            assertEquals(RgbPattern.CIRCLE, s.pattern)
            assertTrue(s.enabled)
            // one look for every element, keys smooth
            assertFalse(s.separate)
            for (t in RgbTarget.entries) assertEquals(RgbLook.of(s), s.lookFor(t))
            // and written again in the new form, which no longer needs "wave"
            assertFalse(s.encode().contains("wave="))
            assertEquals(s, RgbSettings.parse(s.encode()))
        }
        assertEquals(RgbCurve.preset(RgbWaveform.STEPS, 12), RgbSettings.parse("wave=STEPS;steps=99").curve, "steps clamped")
        // a broken curve falls back to the old waveform if present, else to the default
        assertEquals(RgbCurve.preset(RgbWaveform.SINE), RgbSettings.parse("curve=0:0;wave=SINE").curve)
        assertEquals(RgbCurve.DEFAULT, RgbSettings.parse("curve=garbage").curve)
    }

    @Test fun ownLooksPerElement() {
        val shared = RgbSettings(enabled = true, pattern = RgbPattern.LED, layers = RgbLayer.entries.toSet(), areas = RgbArea.entries.toSet())
        assertEquals(RgbPattern.LED, shared.forTarget(RgbTarget.SUGGESTIONS).pattern)
        val split = shared.withSeparate(true)
        assertEquals(RgbTarget.entries.toSet(), split.looks.keys, "every element starts with the shared look")
        val ink = split.withLook(RgbTarget.BACKGROUND, split.lookFor(RgbTarget.BACKGROUND).copy(pattern = RgbPattern.INK, periodMs = 12_000))
        assertEquals(RgbPattern.INK, ink.forTarget(RgbTarget.BACKGROUND).pattern)
        assertEquals(12_000, ink.forTarget(RgbTarget.BACKGROUND).periodMs)
        assertEquals(RgbPattern.LED, ink.forTarget(RgbTarget.KEYS).pattern)
        // back to "same everywhere": the shared look again, own looks kept for later
        val same = ink.withSeparate(false)
        assertEquals(RgbPattern.LED, same.forTarget(RgbTarget.BACKGROUND).pattern)
        assertEquals(RgbPattern.INK, same.withSeparate(true).forTarget(RgbTarget.BACKGROUND).pattern)
        // one engine per distinct look, all on one clock
        val engines = RgbEngines(ink)
        assertEquals(2, engines.all.size)
        assertSame(engines.forTarget(RgbTarget.KEYS), engines.forTarget(RgbTarget.LABELS))
        engines.advance(0); engines.advance(100)
        assertEquals(100.0 / 4000, engines.forTarget(RgbTarget.KEYS).phase, 1e-9)
        assertEquals(100.0 / 12_000, engines.forTarget(RgbTarget.BACKGROUND).phase, 1e-9)
        engines.tap(0.5f, 0.5f, 1, 100)
        // a broken own look in the text falls back field by field to the shared look
        val parsed = RgbSettings.parse(ink.encode().replace("b.period=12000", "b.period=x").replace("b.pattern=INK", "b.pattern=NOPE"))
        assertEquals(RgbPattern.LED, parsed.looks[RgbTarget.BACKGROUND]?.pattern)
        assertEquals(4000, parsed.looks[RgbTarget.BACKGROUND]?.periodMs)
        // own looks are clamped like the shared one
        assertEquals(RgbSettings.PERIOD_RANGE.last, RgbSettings.parse("sep=1;k.period=999999").forTarget(RgbTarget.KEYS).periodMs)
    }

    @Test fun movesOnlyWhenSomethingLitMoves() {
        val still = RgbSettings(enabled = true, pattern = RgbPattern.STATIC_RAINBOW, layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND))
        assertFalse(still.moves())
        val split = still.withSeparate(true)
        val bgInk = split.withLook(RgbTarget.BACKGROUND, split.lookFor(RgbTarget.BACKGROUND).copy(pattern = RgbPattern.INK))
        assertTrue(bgInk.moves())
        // an element that is not lit does not count
        assertFalse(bgInk.copy(layers = setOf(RgbLayer.KEY_BACKGROUND)).moves())
        assertEquals(RgbRun.Mode.RUNNING, RgbRun.mode(bgInk, true, false, true, false))
    }
}
