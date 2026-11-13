// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Build-14 lag: the self-measurement of the lighting work and the engine state copy for background grids. */
class RgbFrameStatsTest {
    /** The Diagnose page is opened after the keyboard was hidden: it shows the last seconds with light. */
    @Test fun theLastActivityIsSummedUpAlsoLaterOn() {
        val stats = RgbFrameStats(windowMs = 5000)
        assertEquals(0, stats.summaryOfLastActivity().frames)
        stats.recordUi(10_000, 2_000_000, 8_000_000)
        stats.recordUi(14_000, 4_000_000, 12_000_000)
        stats.recordUi(20_000, 1_000_000, 6_000_000) // a later burst: only it counts
        stats.recordBackground(19_500, 500_000)
        val last = stats.summaryOfLastActivity()
        assertEquals(1, last.frames)
        assertEquals(1.0, last.uiAvgMs, 1e-9)
        assertEquals(6.0, last.drawMaxMs, 1e-9)
        assertEquals(1, last.jobs)
        assertEquals(0, stats.summary(60_000).frames) // "now" a minute later: nothing
    }

    @Test fun averageAndMaximumOverTheLastSeconds() {
        val stats = RgbFrameStats(windowMs = 5000, capacity = 8)
        assertEquals(RgbFrameStats.Summary(0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0), stats.summary(1000))
        stats.recordUi(1000, 2_000_000, 10_000_000)
        stats.recordUi(2000, 4_000_000, 6_000_000)
        stats.recordBackground(2000, 1_000_000)
        val s = stats.summary(3000)
        assertEquals(2, s.frames)
        assertEquals(3.0, s.uiAvgMs, 1e-9)
        assertEquals(4.0, s.uiMaxMs, 1e-9)
        assertEquals(8.0, s.drawAvgMs, 1e-9)
        assertEquals(10.0, s.drawMaxMs, 1e-9)
        assertEquals(1, s.jobs)
        assertEquals(1.0, s.backgroundMaxMs, 1e-9)
        // older than the window: gone
        val later = stats.summary(6500)
        assertEquals(1, later.frames)
        assertEquals(4.0, later.uiAvgMs, 1e-9)
        // the ring keeps the last entries only
        repeat(20) { stats.recordUi(7000L + it, 1_000_000) }
        assertEquals(8, stats.summary(7100).frames)
        assertEquals(1.0, stats.summary(7100).uiMaxMs, 1e-9)
        stats.clear()
        assertEquals(0, stats.summary(7100).frames)
    }

    @Test fun aCopiedEngineLightsLikeTheOriginal() {
        for (p in RgbPattern.entries) {
            val settings = RgbSettings(enabled = true, pattern = p, tapReaction = RgbTapReaction.RIPPLE, glowMs = 3000)
            val ui = RgbEngine(settings).apply { aspect = 0.62f }
            ui.advance(0)
            var t = 0L
            repeat(40) { t += 33; ui.advance(t) }
            ui.tap(0.3f, 0.4f, 7, t - 200)
            val copy = RgbEngine(settings)
            copy.copyStateFrom(ui)
            for (i in 0 until 30) {
                val x = (i % 6) / 6f + 0.05f
                val y = (i / 6) / 5f + 0.1f
                assertEquals(ui.light(x, y, -1, t), copy.light(x, y, -1, t), "$p at $x,$y")
                assertEquals(ui.light(x, y, RgbEngine.FIELD_KEYS, t), copy.light(x, y, RgbEngine.FIELD_KEYS, t), "$p keys at $x,$y")
            }
            // the UI engine goes on, the copy keeps its moment
            val before = copy.light(0.5f, 0.5f, -1, t)
            ui.advance(t + 500)
            assertEquals(before, copy.light(0.5f, 0.5f, -1, t))
        }
    }

    @Test fun enginesWithTheSameColoursShareTheirPaletteTable() {
        val a = RgbEngine(RgbSettings(enabled = true, saturation = 77))
        a.light(0.2f, 0.2f, -1, 0)
        val made = RgbPaletteTables.made
        // another curve (a dragged point; both wrap around the palette): the same colours, no new table
        val b = RgbEngine(RgbSettings(enabled = true, saturation = 77, curve = RgbCurve.preset(RgbWaveform.STEPS, 5)))
        b.light(0.2f, 0.2f, -1, 0)
        assertEquals(made, RgbPaletteTables.made)
        assertEquals(a.paletteAt(0.37f, true), b.paletteAt(0.37f, true))
        // other colours: their own table
        val c = RgbEngine(RgbSettings(enabled = true, saturation = 76))
        c.light(0.2f, 0.2f, -1, 0)
        assertTrue(RgbPaletteTables.made > made)
        assertSame(RgbPaletteTables.get(listOf("x")) { IntArray(1) }, RgbPaletteTables.get(listOf("x")) { IntArray(2) })
    }
}
