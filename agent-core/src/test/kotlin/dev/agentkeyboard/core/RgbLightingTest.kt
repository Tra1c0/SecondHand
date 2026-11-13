// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.*

class RgbLightingTest {
    private fun on(change: RgbSettings.() -> RgbSettings = { this }) = RgbSettings(enabled = true).change()
    private fun near(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    private fun hue(c: Int) = RgbColors.toHsv(c)[0]
    private fun hueDistance(a: Float, b: Float) = abs(((a - b) % 360f + 540f) % 360f - 180f)

    // --- the stage 26 waveforms, now presets of the own curve (29d) ---

    /** The fixed shapes exactly as stage 26 computed them, kept as the reference for the presets. */
    private fun legacy(waveform: RgbWaveform, u: Float, steps: Int): Float {
        val x = u - kotlin.math.floor(u)
        fun smooth(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
        return when (waveform) {
            RgbWaveform.SINE -> (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * x)).toFloat()
            RgbWaveform.TRIANGLE -> 1f - abs(2f * x - 1f)
            RgbWaveform.SAWTOOTH -> x
            RgbWaveform.STEPS -> kotlin.math.min(kotlin.math.floor(x * steps) / steps, 1f)
            RgbWaveform.PULSE -> when {
                x < 0.3f -> smooth(x / 0.3f)
                x < 0.45f -> 1f
                x < 0.75f -> 1f - smooth((x - 0.45f) / 0.3f)
                else -> 0f
            }
        }
    }
    private fun wave(w: RgbWaveform, u: Float, steps: Int = 4) = RgbCurve.preset(w, steps).value(u)

    @Test fun sineIsSoftAndSymmetric() {
        near(0f, wave(RgbWaveform.SINE, 0f))
        near(0.5f, wave(RgbWaveform.SINE, 0.25f))
        near(1f, wave(RgbWaveform.SINE, 0.5f))
        near(wave(RgbWaveform.SINE, 0.2f), wave(RgbWaveform.SINE, 0.8f))
        // soft: small change near the top
        assertTrue(wave(RgbWaveform.SINE, 0.5f) - wave(RgbWaveform.SINE, 0.45f) < 0.03f)
    }

    @Test fun triangleIsLinearZigzag() {
        near(0f, wave(RgbWaveform.TRIANGLE, 0f))
        near(0.5f, wave(RgbWaveform.TRIANGLE, 0.25f))
        near(1f, wave(RgbWaveform.TRIANGLE, 0.5f))
        near(0.5f, wave(RgbWaveform.TRIANGLE, 0.75f))
        // constant slope: equal steps give equal changes
        val a = wave(RgbWaveform.TRIANGLE, 0.1f) - wave(RgbWaveform.TRIANGLE, 0.05f)
        val b = wave(RgbWaveform.TRIANGLE, 0.4f) - wave(RgbWaveform.TRIANGLE, 0.35f)
        near(a, b, 0.001f)
    }

    @Test fun sawtoothRisesThenJumps() {
        near(0f, wave(RgbWaveform.SAWTOOTH, 0f))
        near(0.5f, wave(RgbWaveform.SAWTOOTH, 0.5f))
        near(0.99f, wave(RgbWaveform.SAWTOOTH, 0.99f))
        near(0f, wave(RgbWaveform.SAWTOOTH, 1f))
        near(0.25f, wave(RgbWaveform.SAWTOOTH, 2.25f))
        near(0.75f, wave(RgbWaveform.SAWTOOTH, -0.25f))
    }

    @Test fun stepsHoldExactlyTheConfiguredLevels() {
        // 29b: a step now rises within 1 % of the cycle instead of in zero time; between the
        // risers the curve holds exactly the old levels
        for (steps in listOf(2, 4, 7, 12)) {
            val samples = (0 until 1000).map { it / 1000f }
            val points = RgbCurve.preset(RgbWaveform.STEPS, steps).points
            // the risers are the segments between point 2k-1 and 2k
            val onLevel = samples.filter { u -> (1 until steps).none { k -> u > points[2 * k - 1].x && u < points[2 * k].x } &&
                (1 until steps).none { k -> u >= points[2 * k - 1].x && u < k / steps.toFloat() } }
            assertTrue(onLevel.size >= 1000 - steps * 11, "steps=$steps")
            for (u in onLevel) // levels are stored with three decimals
            assertEquals(legacy(RgbWaveform.STEPS, u, steps), wave(RgbWaveform.STEPS, u, steps), 1e-3f, "steps=$steps u=$u")
            assertEquals(steps, onLevel.map { (wave(RgbWaveform.STEPS, it, steps) * 1000).roundToInt() }.toSet().size, "steps=$steps")
        }
        near(0.5f, wave(RgbWaveform.STEPS, 0.6f, 2))
    }

    @Test fun pulseRisesHoldsFallsAndRests() {
        near(0f, wave(RgbWaveform.PULSE, 0f))
        near(1f, wave(RgbWaveform.PULSE, 0.35f))
        near(1f, wave(RgbWaveform.PULSE, 0.44f))
        assertTrue(wave(RgbWaveform.PULSE, 0.6f) in 0.1f..0.9f)
        near(0f, wave(RgbWaveform.PULSE, 0.8f))
        near(0f, wave(RgbWaveform.PULSE, 0.99f))
        // eased: slow start
        assertTrue(wave(RgbWaveform.PULSE, 0.03f) < 0.05f)
    }

    @Test fun presetsFollowTheOldShapes() {
        // largest difference to stage 26 over 2000 samples (steps: outside the 1 % risers)
        val limits = mapOf(RgbWaveform.SAWTOOTH to 1e-4f, RgbWaveform.PULSE to 1e-4f, RgbWaveform.SINE to 0.02f, RgbWaveform.TRIANGLE to 0.03f)
        for ((w, limit) in limits) {
            val worst = (0 until 2000).maxOf { abs(wave(w, it / 2000f) - legacy(w, it / 2000f, 4)) }
            assertTrue(worst <= limit, "$w differs by $worst")
        }
    }

    // --- patterns, direction, time ---

    @Test fun horizontalWaveVariesAcrossButNotDown() {
        val e = RgbEngine(on())
        assertNotEquals(e.light(0.1f, 0.5f, 1, 0), e.light(0.6f, 0.5f, 1, 0))
        assertEquals(e.light(0.3f, 0.1f, 1, 0), e.light(0.3f, 0.9f, 1, 0))
    }

    @Test fun verticalAndDiagonalWaves() {
        val v = RgbEngine(on { copy(pattern = RgbPattern.WAVE_VERTICAL) })
        assertEquals(v.light(0.1f, 0.3f, 1, 0), v.light(0.9f, 0.3f, 1, 0))
        assertNotEquals(v.light(0.5f, 0.1f, 1, 0), v.light(0.5f, 0.6f, 1, 0))
        val d = RgbEngine(on { copy(pattern = RgbPattern.WAVE_DIAGONAL) })
        assertEquals(d.light(0.2f, 0.6f, 1, 0), d.light(0.6f, 0.2f, 1, 0))
        assertNotEquals(d.light(0.2f, 0.2f, 1, 0), d.light(0.6f, 0.6f, 1, 0))
    }

    @Test fun circleIsRoundAroundTheCentre() {
        val e = RgbEngine(on { copy(pattern = RgbPattern.CIRCLE) }).apply { aspect = 1f }
        assertEquals(e.light(0.2f, 0.5f, 1, 0), e.light(0.8f, 0.5f, 1, 0))
        assertEquals(e.light(0.5f, 0.2f, 1, 0), e.light(0.2f, 0.5f, 1, 0))
        assertNotEquals(e.light(0.5f, 0.5f, 1, 0), e.light(0.9f, 0.5f, 1, 0))
    }

    @Test fun breatheIsTheSameEverywhereAndChangesStrength() {
        val e = RgbEngine(on { copy(pattern = RgbPattern.BREATHE, curve = RgbCurve.preset(RgbWaveform.SINE), periodMs = 1000) })
        assertEquals(e.light(0.1f, 0.1f, 1, 0), e.light(0.9f, 0.9f, 2, 0))
        val low = RgbColors.alpha(e.light(0.5f, 0.5f, 1, 0))
        e.advance(0); e.advance(100); e.advance(200); e.advance(300); e.advance(400); e.advance(500)
        val high = RgbColors.alpha(e.light(0.5f, 0.5f, 1, 500))
        assertTrue(high > low + 150, "low=$low high=$high")
    }

    @Test fun staticRainbowNeverMoves() {
        val e = RgbEngine(on { copy(pattern = RgbPattern.STATIC_RAINBOW) })
        val before = e.light(0.3f, 0.5f, 1, 0)
        e.advance(0); e.advance(90); e.advance(180)
        assertEquals(0.0, e.phase)
        assertEquals(before, e.light(0.3f, 0.5f, 1, 180))
    }

    @Test fun phaseFollowsThePeriodAndSkipsLongGaps() {
        val e = RgbEngine(on { copy(periodMs = 1000) })
        e.advance(0)
        for (t in 50L..500L step 50) e.advance(t)
        assertEquals(0.5, e.phase, 1e-9)
        e.advance(10_500) // keyboard was hidden: at most one step, no jump
        assertEquals(0.5 + RgbEngine.MAX_STEP_MS / 1000.0, e.phase, 1e-9)
        e.resetClock()
        e.advance(20_000)
        assertEquals(0.6, e.phase, 1e-9)
    }

    @Test fun reverseMirrorsTheDirection() {
        val fwd = RgbEngine(on { copy(curve = RgbCurve.preset(RgbWaveform.SAWTOOTH)) })
        val rev = RgbEngine(on { copy(curve = RgbCurve.preset(RgbWaveform.SAWTOOTH), reverse = true) })
        // forward: the colour at x moves to the right, so x+d later equals x now
        fwd.advance(0); for (t in 100L..1000L step 100) fwd.advance(t)
        rev.advance(0); for (t in 100L..1000L step 100) rev.advance(t)
        val d = 1000f / 4000f // one second of a four second cycle, width 1
        assertEquals(RgbEngine(on()).light(0.2f, 0.5f, 1, 0), fwd.light(0.2f + d, 0.5f, 1, 1000))
        assertEquals(RgbEngine(on { copy(reverse = true) }).light(0.6f, 0.5f, 1, 0), rev.light(0.6f - d, 0.5f, 1, 1000))
    }

    @Test fun waveWidthRepeatsTheCycle() {
        val e = RgbEngine(on { copy(waveWidth = 2f) })
        assertEquals(e.light(0.1f, 0.5f, 1, 0), e.light(0.6f, 0.5f, 1, 0))
        val one = RgbEngine(on())
        assertNotEquals(one.light(0.1f, 0.5f, 1, 0), one.light(0.6f, 0.5f, 1, 0))
    }

    // --- palette ---

    @Test fun rainbowCyclicCoversAllHuesAndWraps() {
        val e = RgbEngine(on())
        // 29b: the rainbow corners are rounded; the primaries stay where they were
        assertTrue(hueDistance(0f, hue(e.paletteAt(0f, true))) <= 1f, "red at 0")
        assertTrue(hueDistance(120f, hue(e.paletteAt(1f / 3, true))) <= 2f, "green at 1/3")
        assertTrue(hueDistance(240f, hue(e.paletteAt(2f / 3, true))) <= 2f, "blue at 2/3")
        assertEquals(e.paletteAt(0f, true), e.paletteAt(1f, true))
        // back-and-forth waveforms end on a different colour than they start
        assertNotEquals(e.paletteAt(0f, false), e.paletteAt(1f, false))
    }

    @Test fun ownPaletteInterpolatesBetweenNeighbours() {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val green = 0xFF00FF00.toInt()
        val e = RgbEngine(on { copy(rainbow = false, palette = listOf(red, blue, green)) })
        assertEquals(red, e.paletteAt(0f, false))
        assertEquals(blue, e.paletteAt(0.5f, false))
        assertEquals(green, e.paletteAt(1f, false))
        // 29b: blended in OKLCH, not in sRGB
        assertEquals(RgbColors.mixOklch(red, blue, 0.5f), e.paletteAt(0.25f, false))
        // cyclic: three segments, the last one leads back to red
        assertEquals(blue, e.paletteAt(1f / 3, true))
        assertEquals(RgbColors.mixOklch(green, red, 0.5f), e.paletteAt(5f / 6, true))
        assertEquals(red, e.paletteAt(1f, true))
    }

    @Test fun saturationAndBrightnessApplyToBothPalettes() {
        val grey = RgbEngine(on { copy(saturation = 0) }).paletteAt(0.3f, true)
        assertEquals(RgbColors.red(grey), RgbColors.green(grey))
        assertEquals(RgbColors.green(grey), RgbColors.blue(grey))
        val dim = RgbEngine(on { copy(rainbow = false, palette = listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()), brightness = 50) })
        near(0.5f, RgbColors.toHsv(dim.paletteAt(0f, false))[2], 0.01f)
    }

    @Test fun mixScalesTheStrength() {
        assertEquals(255, RgbColors.alpha(RgbEngine(on()).light(0.5f, 0.5f, 1, 0)))
        assertEquals(128, RgbColors.alpha(RgbEngine(on { copy(mix = 50) }).light(0.5f, 0.5f, 1, 0)))
        assertEquals(0, RgbColors.alpha(RgbEngine(on { copy(mix = 0) }).light(0.5f, 0.5f, 1, 0)))
        assertEquals(0, RgbEngine(RgbSettings()).light(0.5f, 0.5f, 1, 0), "off draws nothing")
    }

    // --- tap reactions ---

    @Test fun flashLightsOnlyTheTappedKeyAndFades() {
        val e = RgbEngine(on { copy(tapReaction = RgbTapReaction.FLASH, glowMs = 1000) })
        val normal = e.light(0.5f, 0.5f, 7, 0)
        e.tap(0.5f, 0.5f, 7, 0)
        val flashed = e.light(0.5f, 0.5f, 7, 0)
        assertTrue(RgbColors.luminance(flashed) > RgbColors.luminance(normal))
        assertEquals(e.light(0.5f, 0.5f, 8, 0), normal, "other key unchanged")
        assertEquals(e.light(0.5f, 0.5f, -1, 0), normal, "background is not a key")
        assertEquals(normal, e.light(0.5f, 0.5f, 7, 1000), "faded")
        assertTrue(e.hasActiveEffect(500))
        assertFalse(e.hasActiveEffect(1000))
    }

    @Test fun reactiveOnlyIsInvisibleUntilATap() {
        val e = RgbEngine(on { copy(reactiveOnly = true, glowMs = 1000) })
        assertEquals(0, RgbColors.alpha(e.light(0.5f, 0.5f, 1, 0)))
        e.tap(0.2f, 0.5f, 3, 100)
        assertEquals(255, RgbColors.alpha(e.light(0.8f, 0.5f, 1, 100)), "whole pattern appears")
        val mid = RgbColors.alpha(e.light(0.8f, 0.5f, 1, 600))
        assertTrue(mid in 1..254, "fading: $mid")
        assertEquals(0, RgbColors.alpha(e.light(0.8f, 0.5f, 1, 1100)))
    }

    @Test fun reactiveFlashAppearsOnlyAtTheTappedKey() {
        val e = RgbEngine(on { copy(reactiveOnly = true, tapReaction = RgbTapReaction.FLASH, glowMs = 1000) })
        e.tap(0.2f, 0.5f, 3, 0)
        assertEquals(255, RgbColors.alpha(e.light(0.2f, 0.5f, 3, 0)))
        assertEquals(0, RgbColors.alpha(e.light(0.3f, 0.5f, 4, 0)))
    }

    @Test fun rippleIsARingThatTravelsOutwards() {
        val e = RgbEngine(on { copy(reactiveOnly = true, tapReaction = RgbTapReaction.RIPPLE, glowMs = 1000) }).apply { aspect = 1f }
        e.tap(0.5f, 0.5f, 1, 0)
        // after 250 ms the ring is 0.3 widths away
        val at = 250L
        val ring = RgbColors.alpha(e.light(0.8f, 0.5f, -1, at))
        val inside = RgbColors.alpha(e.light(0.5f, 0.5f, -1, at))
        val outside = RgbColors.alpha(e.light(0.5f, 0.95f, -1, at))
        assertTrue(ring > 100, "ring=$ring")
        assertEquals(0, inside)
        assertEquals(0, outside)
        assertEquals(ring, RgbColors.alpha(e.light(0.5f, 0.2f, -1, at)), "round")
    }

    @Test fun speedUpBoostsThePhaseAfterATap() {
        val plain = RgbEngine(on { copy(periodMs = 1000) })
        val fast = RgbEngine(on { copy(periodMs = 1000, tapReaction = RgbTapReaction.SPEED_UP, glowMs = 1000) })
        fast.tap(0.5f, 0.5f, 1, 0)
        for (e in listOf(plain, fast)) { e.advance(0); for (t in 50L..300L step 50) e.advance(t) }
        assertTrue(fast.phase > plain.phase * 2, "fast=${fast.phase} plain=${plain.phase}")
    }

    @Test fun onlyEightTapsAreKept() {
        val e = RgbEngine(on { copy(tapReaction = RgbTapReaction.FLASH, glowMs = 5000) })
        for (i in 0 until 9) e.tap(0.5f, 0.5f, i, 0)
        assertEquals(e.light(0.5f, 0.5f, 100, 0), e.light(0.5f, 0.5f, 0, 0), "oldest tap dropped")
        assertNotEquals(e.light(0.5f, 0.5f, 100, 0), e.light(0.5f, 0.5f, 8, 0))
        e.clearTaps()
        assertFalse(e.hasActiveEffect(1))
    }

    // --- colours and contrast ---

    @Test fun hsvRoundTrip() {
        for (c in listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF123456, 0xFFFFFFFF, 0xFF000000).map { it.toInt() }) {
            val h = RgbColors.toHsv(c)
            assertEquals(c, RgbColors.hsv(h[0], h[1], h[2]))
        }
    }

    @Test fun applyLightBlendsWithTheTheme() {
        val theme = 0xFF000000.toInt()
        assertEquals(theme, RgbColors.applyLight(theme, 0x00FF0000))
        assertEquals(0xFFFF0000.toInt(), RgbColors.applyLight(theme, 0xFFFF0000.toInt()))
        assertEquals(0xFF800000.toInt(), RgbColors.applyLight(theme, 0x80FF0000.toInt()))
    }

    @Test fun labelsKeepAMinimumContrast() {
        val yellow = 0xFFFFFF00.toInt()
        val white = 0xFFFFFFFF.toInt()
        val fixed = RgbColors.readable(white, yellow)
        assertTrue(RgbColors.contrast(fixed, yellow) >= RgbColors.MIN_LABEL_CONTRAST)
        // the same colour on itself becomes readable too
        for (c in listOf(yellow, 0xFF0000FF.toInt(), 0xFF808080.toInt(), 0xFF00FF00.toInt())) {
            assertTrue(RgbColors.contrast(RgbColors.readable(c, c), c) >= RgbColors.MIN_LABEL_CONTRAST, RgbSettings.hex(c))
        }
        // already readable: unchanged, alpha kept
        assertEquals(white, RgbColors.readable(white, 0xFF000000.toInt()))
        assertEquals(0x80, RgbColors.alpha(RgbColors.readable(0x80FFFFFF.toInt(), yellow)))
    }

    @Test fun lightOnATransparentThemeIsNotDarkenedTwice() {
        val light = 0x80FF0000.toInt()
        // without key borders the theme colour is transparent: the light itself, strength as alpha
        assertEquals(light, RgbColors.applyLight(0, light))
        // painted over any background it looks like the light painted directly over that background
        for (back in listOf(0xFF000000.toInt(), 0xFF4C4C4C.toInt(), 0xFFFFFFFF.toInt())) {
            val seen = RgbColors.over(RgbColors.applyLight(0, light), back)
            val direct = RgbColors.applyLight(back, light)
            for (shift in listOf(0, 8, 16)) assertTrue(abs(((seen shr shift) and 0xFF) - ((direct shr shift) and 0xFF)) <= 1, RgbSettings.hex(back))
        }
        // a half transparent theme colour: the same as theme over background, then the light
        val half = 0x800000FF.toInt()
        val back = 0xFF000000.toInt()
        val seen = RgbColors.over(RgbColors.applyLight(half, light), back)
        val expected = RgbColors.applyLight(RgbColors.over(half, back), light)
        for (shift in listOf(0, 8, 16)) assertTrue(abs(((seen shr shift) and 0xFF) - ((expected shr shift) and 0xFF)) <= 1)
        // opaque theme colours as before
        assertEquals(0xFF800000.toInt(), RgbColors.applyLight(0xFF000000.toInt(), light))
    }

    @Test fun contrastCountsTheAlphaOfTheLabel() {
        val black = 0xFF000000.toInt()
        // grey at half alpha on black is seen as dark grey: below 3:1, although the opaque grey is above
        val dim = 0x80808080.toInt()
        assertTrue(RgbColors.contrast(dim or black, black) >= RgbColors.MIN_LABEL_CONTRAST)
        assertTrue(RgbColors.contrast(RgbColors.over(dim, black), black) < RgbColors.MIN_LABEL_CONTRAST)
        val fixed = RgbColors.readable(dim, black)
        assertEquals(0x80, RgbColors.alpha(fixed), "alpha kept")
        assertTrue(RgbColors.contrast(RgbColors.over(fixed, black), black) >= RgbColors.MIN_LABEL_CONTRAST)
        // the same through label()
        val label = RgbColors.label(dim, 0, false, black)
        assertTrue(RgbColors.contrast(RgbColors.over(label, black), black) >= RgbColors.MIN_LABEL_CONTRAST)
        // invisible stays invisible; too transparent for 3:1 becomes the best possible
        assertEquals(0, RgbColors.readable(0, black))
        assertEquals(0x10FFFFFF, RgbColors.readable(0x10808080, black))
    }

    @Test fun nanAndInfinityNeverThrow() {
        // a preview or view without width gives aspect = h / 0 and positions x / 0
        for (pattern in RgbPattern.entries) for (rainbow in listOf(true, false)) {
            val e = RgbEngine(on { copy(pattern = pattern, rainbow = rainbow, tapReaction = RgbTapReaction.RIPPLE) })
            e.aspect = Float.NaN
            assertEquals(0.45f, e.aspect, "NaN ignored")
            e.aspect = Float.POSITIVE_INFINITY
            assertEquals(0.45f, e.aspect, "infinity ignored")
            e.aspect = 0f
            assertEquals(RgbEngine.ASPECT_MIN, e.aspect)
            e.tap(Float.NaN, Float.NEGATIVE_INFINITY, 1, 0)
            for (v in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.5f)) {
                e.light(v, v, 1, 10)
                e.light(v, 0.5f, -1, 10)
            }
            e.paletteAt(Float.NaN, true)
        }
        assertEquals(RgbColors.hsv(0f, 0f, 0f), RgbColors.hsv(Float.NaN, Float.NaN, Float.NaN))
        RgbColors.hsv(Float.POSITIVE_INFINITY, 1f, 1f)
        assertEquals(0xFF000000.toInt(), RgbColors.lerp(0xFF000000.toInt(), -1, Float.NaN))
    }

    @Test fun pressedKeysStayVisiblyDifferent() {
        for (c in listOf(0xFFFFFF00.toInt(), 0xFF101010.toInt(), 0xFFFF0000.toInt()))
            assertNotEquals(c, RgbColors.pressed(c))
    }

    // --- settings ---

    @Test fun defaultsAreOffAndRoundTrip() {
        assertFalse(RgbSettings.DEFAULT.enabled)
        assertEquals(RgbSettings.DEFAULT, RgbSettings.parse(null))
        assertEquals(RgbSettings.DEFAULT, RgbSettings.parse(""))
        assertEquals(RgbSettings.DEFAULT, RgbSettings.parse(RgbSettings.DEFAULT.encode()))
        val custom = RgbSettings(true, RgbPattern.CIRCLE, RgbCurve.preset(RgbWaveform.STEPS, 6), 1500, true, 2.5f, false,
            listOf(0xFF112233.toInt(), 0xFFAABBCC.toInt(), 0xFFFFFFFF.toInt()), 40, 70, 55,
            setOf(RgbLayer.LABEL, RgbLayer.KEYBOARD_BACKGROUND), setOf(RgbArea.SPACE, RgbArea.TOOLBAR),
            RgbTapReaction.RIPPLE, true, 1200, 60, false, solidKeys = true)
        assertEquals(custom, RgbSettings.parse(custom.encode()))
        // own looks per element survive too
        val own = custom.withSeparate(true).let { s ->
            s.withLook(RgbTarget.BACKGROUND, s.lookFor(RgbTarget.BACKGROUND).copy(pattern = RgbPattern.INK, periodMs = 9000,
                curve = RgbCurve.preset(RgbWaveform.PULSE), rainbow = true))
        }
        assertEquals(own, RgbSettings.parse(own.encode()))
        assertTrue(own.encode().length < RgbSettings.MAX_ENCODED_LENGTH)
    }

    @Test fun invalidValuesClampOrFallBackPerField() {
        val parsed = RgbSettings.parse("on=1;period=5;steps=99;width=NaN;sat=-3;bright=0;mix=400;glow=999999;fps=144;" +
            "pattern=SPIRAL;wave=;colors=FF0000,nothex,#00FF00,123;layers=LABEL,BOGUS;areas=;tap=EXPLODE;reactive=yes")
        assertTrue(parsed.enabled)
        assertEquals(500, parsed.periodMs)
        // "wave=" is empty: no preset to migrate, the default curve
        assertEquals(RgbCurve.DEFAULT, parsed.curve)
        assertEquals(1f, parsed.waveWidth)
        assertEquals(0, parsed.saturation)
        assertEquals(RgbSettings.BRIGHTNESS_MIN, parsed.brightness)
        assertEquals(100, parsed.mix)
        assertEquals(5000, parsed.glowMs)
        assertEquals(60, parsed.fps)
        assertEquals(RgbPattern.WAVE_HORIZONTAL, parsed.pattern)
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()), parsed.palette)
        assertEquals(setOf(RgbLayer.LABEL), parsed.layers)
        assertEquals(emptySet(), parsed.areas)
        assertEquals(RgbTapReaction.NONE, parsed.tapReaction)
        assertFalse(parsed.reactiveOnly)
        // too few or too many colours
        assertEquals(RgbSettings.DEFAULT_PALETTE, RgbSettings.parse("colors=FF0000").palette)
        assertEquals(6, RgbSettings.parse("colors=" + List(9) { "FF0000" }.joinToString(",")).palette.size)
        assertEquals(RgbSettings.DEFAULT, RgbSettings.parse("on=1;" + "x".repeat(RgbSettings.MAX_ENCODED_LENGTH)))
        assertEquals(RgbSettings.DEFAULT, RgbSettings.parse("garbage without equals"))
        assertEquals(30, RgbSettings.parse("fps=45").fps)
    }

    @Test fun whatIsLit() {
        assertFalse(RgbSettings().lightsKeys())
        assertTrue(on().lightsKeys())
        assertFalse(on().lightsStrip())
        assertFalse(on { copy(layers = emptySet()) }.lightsKeys())
        assertFalse(on { copy(areas = setOf(RgbArea.SUGGESTIONS)) }.lightsKeys())
        assertTrue(on { copy(areas = setOf(RgbArea.SUGGESTIONS)) }.lightsStrip())
        assertTrue(on { copy(areas = emptySet(), layers = setOf(RgbLayer.KEYBOARD_BACKGROUND)) }.lightsKeys())
    }

    // --- battery rules ---

    @Test fun runModes() {
        val s = on()
        assertEquals(RgbRun.Mode.OFF, RgbRun.mode(RgbSettings(), true, false, true, false))
        assertEquals(RgbRun.Mode.OFF, RgbRun.mode(on { copy(layers = emptySet()) }, true, false, true, false))
        assertEquals(RgbRun.Mode.RUNNING, RgbRun.mode(s, true, false, true, false))
        assertEquals(RgbRun.Mode.PAUSED, RgbRun.mode(s, false, false, true, false), "hidden keyboard")
        assertEquals(RgbRun.Mode.STATIC, RgbRun.mode(s, true, true, true, false), "power save")
        assertEquals(RgbRun.Mode.RUNNING, RgbRun.mode(s.copy(pauseInPowerSave = false), true, true, true, false))
        assertEquals(RgbRun.Mode.STATIC, RgbRun.mode(s, true, false, false, false), "animations off")
        val reactive = s.copy(reactiveOnly = true)
        assertEquals(RgbRun.Mode.PAUSED, RgbRun.mode(reactive, true, false, true, false), "nothing to draw until a tap")
        assertEquals(RgbRun.Mode.RUNNING, RgbRun.mode(reactive, true, false, true, true))
        val still = s.copy(pattern = RgbPattern.STATIC_RAINBOW, tapReaction = RgbTapReaction.FLASH)
        assertEquals(RgbRun.Mode.PAUSED, RgbRun.mode(still, true, false, true, false))
        assertEquals(RgbRun.Mode.RUNNING, RgbRun.mode(still, true, false, true, true))
    }

    @Test fun pacerCapsTheFrameRate() {
        fun rendered(fps: Int, hz: Int): Int {
            val pacer = RgbFramePacer(fps)
            val frame = 1_000_000_000L / hz
            return (0 until hz).count { pacer.shouldRender(it * frame) }
        }
        assertEquals(30, rendered(30, 60))
        assertEquals(30, rendered(30, 120))
        assertEquals(30, rendered(30, 90))
        assertEquals(60, rendered(60, 60))
        assertEquals(60, rendered(60, 120))
        assertEquals(30, rendered(30, 30))
        val pacer = RgbFramePacer(30)
        assertTrue(pacer.shouldRender(0))
        assertFalse(pacer.shouldRender(16_000_000))
        pacer.reset()
        assertTrue(pacer.shouldRender(16_000_000))
    }

    @Test fun pacerSaysWhenTheNextFrameIsDue() {
        val pacer = RgbFramePacer(30)
        assertEquals(0L, pacer.nanosUntilDue(123), "first frame at once")
        assertTrue(pacer.shouldRender(0))
        val due = pacer.intervalNanos - RgbFramePacer.SLACK_NANOS
        assertEquals(due, pacer.nanosUntilDue(0))
        assertEquals(due - 5_000_000, pacer.nanosUntilDue(5_000_000))
        // a frame at the due time is accepted, one before it is not
        assertFalse(pacer.shouldRender(due - 1))
        assertEquals(1L, pacer.nanosUntilDue(due - 1))
        assertEquals(0L, pacer.nanosUntilDue(due + 50_000_000), "late: at once")
        assertTrue(pacer.shouldRender(due))
        // on a 120 Hz display only the due frames are asked for: 30 per second, not 120
        val p = RgbFramePacer(30)
        val vsync = 1_000_000_000L / 120
        var requested = 0
        var rendered = 0
        var t = 0L
        while (t < 990_000_000L) { // one second of vsyncs (the last at 991.7 ms)
            requested++
            if (p.shouldRender(t)) rendered++
            // next vsync at or after the due time
            val wait = p.nanosUntilDue(t + 1_000_000) + 1_000_000
            t += ((wait + vsync - 1) / vsync).coerceAtLeast(1) * vsync
        }
        assertEquals(30, rendered)
        assertEquals(rendered, requested, "no frame requested only to be dropped")
    }

    /**
     * Coarse cost check, not a benchmark: one frame of the busiest setting (all layers, ripple with
     * eight taps) evaluates 45 keys twice plus a 24 x 16 background grid. Prints the average.
     */
    @Test fun oneFrameOfLightIsCheap() {
        val e = RgbEngine(on { copy(pattern = RgbPattern.CIRCLE, tapReaction = RgbTapReaction.RIPPLE, glowMs = 5000) })
        for (i in 0 until RgbEngine.MAX_TAPS) e.tap(i / 8f, 0.5f, i, 0)
        fun frame(t: Long): Int {
            var sum = 0
            for (k in 0 until 45) { sum += e.light(k / 45f, (k % 5) / 5f, k, t); sum += e.light(k / 45f, (k % 5) / 5f, -1, t) }
            for (r in 0 until 16) for (c in 0 until 24) sum += e.light(c / 24f, r / 16f, -1, t)
            return sum
        }
        repeat(200) { frame(it.toLong()) } // warm-up
        val frames = 1000
        val start = System.nanoTime()
        var sink = 0
        for (f in 0 until frames) { e.advance(f * 33L); sink += frame(f * 33L % 5000) }
        val perFrameMicros = (System.nanoTime() - start) / 1000.0 / frames
        println("RGB light: ${"%.1f".format(perFrameMicros)} µs per frame (474 values), sink $sink")
        assertTrue(perFrameMicros < 10_000, "far below a 33 ms frame even on a slow test machine: $perFrameMicros µs")
    }
}
