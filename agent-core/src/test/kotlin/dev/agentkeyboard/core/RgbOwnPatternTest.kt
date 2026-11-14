// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.*

/** Stage 34: own light patterns — generators, blend modes, curves, tap reactions, settings and cost. */
class RgbOwnPatternTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val noTaps = LongArray(RgbEngine.MAX_TAPS) { Long.MIN_VALUE }
    private val zeroF = FloatArray(RgbEngine.MAX_TAPS)

    private fun pattern(vararg layers: RgbPatternLayer) = RgbOwnPattern("p1", "Test", layers.toList()).normalized()
    private fun renderer(vararg layers: RgbPatternLayer) = RgbOwnRenderer(pattern(*layers))
    private fun solid(c: Int) = listOf(RgbStop(0f, c), RgbStop(1f, c))
    private fun redBlue() = listOf(RgbStop(0f, red), RgbStop(1f, blue))

    private fun RgbOwnRenderer.at(x: Float, y: Float, phase: Double = 0.0, aspect: Float = 0.5f, now: Long = 0,
                                  tx: FloatArray = zeroF, ty: FloatArray = zeroF, tt: LongArray = noTaps, glow: Int = 1000, reverse: Boolean = false) =
        sample(x, y, aspect, phase, reverse, now, tx, ty, tt, glow)

    private fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
    private fun alpha(c: Int) = c ushr 24
    private fun delta(a: Int, b: Int) = maxOf(abs(ch(a, 16) - ch(b, 16)), abs(ch(a, 8) - ch(b, 8)), abs(ch(a, 0) - ch(b, 0)))
    private fun assertColor(expected: Int, actual: Int, tolerance: Int = 1, message: String = "") =
        assertTrue(delta(expected, actual) <= tolerance, "$message expected ${hex(expected)} got ${hex(actual)}")
    private fun hex(c: Int) = String.format("%08X", c)
    /** The stop table at [v] (sawtooth curve: the colour position itself). */
    private fun table(stops: List<RgbStop>, v: Float, cyclic: Boolean = true) = RgbOwnRenderer.lookup(RgbOwnRenderer.stopTable(stops, cyclic), v)

    // --- generators: golden pixels ---

    @Test fun fillRunsThroughTheStopsOverTime() {
        val r = renderer(RgbPatternLayer(RgbGenerator.FILL, redBlue()))
        assertEquals(0xFFFF0000.toInt(), r.at(0.1f, 0.9f), "phase 0: the first stop exactly")
        assertColor(RgbColors.mixOklch(red, blue, 0.25f), r.at(0.7f, 0.2f, phase = 0.25), message = "phase 0.25")
        assertEquals(r.at(0.1f, 0.1f, phase = 0.25), r.at(0.9f, 0.9f, phase = 0.25), "one colour everywhere")
    }

    @Test fun linearMatchesTheGradientAtEveryAngle() {
        val horizontal = renderer(RgbPatternLayer(RgbGenerator.LINEAR, redBlue(), speed = 0f))
        // u = t - x: x = 0.25 -> 0.75 of the stops
        assertColor(RgbColors.mixOklch(red, blue, 0.75f), horizontal.at(0.25f, 0.4f), message = "x 0.25")
        assertEquals(horizontal.at(0.25f, 0.1f), horizontal.at(0.25f, 0.9f), "horizontal: the same down a column")
        val vertical = renderer(RgbPatternLayer(RgbGenerator.LINEAR, redBlue(), speed = 0f, angle = 90))
        // angle 90: y takes the place of x, whatever the aspect
        assertColor(RgbColors.mixOklch(red, blue, 0.75f), vertical.at(0.6f, 0.25f, aspect = 0.4f), 2, "vertical")
        assertColor(vertical.at(0.1f, 0.25f, aspect = 0.4f), vertical.at(0.9f, 0.25f, aspect = 0.4f), message = "vertical: the same along a row")
        val doubled = renderer(RgbPatternLayer(RgbGenerator.LINEAR, redBlue(), speed = 0f, scale = 2f))
        assertColor(doubled.at(0.1f, 0.5f), doubled.at(0.6f, 0.5f), message = "scale 2 repeats twice across")
        val shifted = renderer(RgbPatternLayer(RgbGenerator.LINEAR, redBlue(), speed = 0f, offsetX = 0.25f))
        assertEquals(horizontal.at(0.3f, 0.5f), shifted.at(0.55f, 0.5f), "the offset moves the pattern")
    }

    @Test fun radialAndConicGoldenPixels() {
        val radial = renderer(RgbPatternLayer(RgbGenerator.RADIAL, redBlue(), speed = 0f))
        assertEquals(red, radial.at(0.5f, 0.5f), "the centre is the first stop")
        val r = 0.25f / RgbOwnRenderer.halfDiagonal(0.5f)
        assertColor(table(redBlue(), 1f - r), radial.at(0.75f, 0.5f), message = "a quarter to the right")
        assertEquals(radial.at(0.75f, 0.5f), radial.at(0.5f, 0.5f + 0.25f / 0.5f), "circles stay round with the aspect")
        val conic = renderer(RgbPatternLayer(RgbGenerator.CONIC, redBlue(), speed = 0f))
        assertEquals(red, conic.at(0.9f, 0.5f), "angle 0: the first stop")
        // a quarter turn (straight down, y grows downwards): u = -0.25 -> 0.75
        assertColor(table(redBlue(), 0.75f), conic.at(0.5f, 0.9f), message = "quarter turn")
        val two = renderer(RgbPatternLayer(RgbGenerator.CONIC, redBlue(), speed = 0f, scale = 2f))
        assertColor(two.at(0.9f, 0.5f), two.at(0.1f, 0.5f), message = "two repeats: opposite sides alike")
    }

    @Test fun boundedShapesFollowTheirFormula() {
        val waves = renderer(RgbPatternLayer(RgbGenerator.WAVES, redBlue(), speed = 0f))
        assertColor(table(redBlue(), 0.5f), waves.at(0.5f, 0.5f), message = "waves at the centre: the middle")
        val plasma = renderer(RgbPatternLayer(RgbGenerator.PLASMA, redBlue(), speed = 0f))
        assertColor(table(redBlue(), 0.5f), plasma.at(0.5f, 0.5f), message = "plasma at the centre: the middle")
        val noise = renderer(RgbPatternLayer(RgbGenerator.NOISE, redBlue(), speed = 0f))
        // angle 0, centre: a = b = 0, t = 0: fbm(0.6 + 40, 40)
        val f = RgbNoise.fbm(0.6f + 40f, 40f)
        assertColor(table(redBlue(), ((f - 0.5f) * 2.2f + 0.5f).coerceIn(0f, 0.9999f)), noise.at(0.5f, 0.5f), message = "noise")
        // the filled shapes cover everything and their colour stays on the table of the stops
        val stopColors = RgbOwnRenderer.stopTable(redBlue(), true).toSet()
        for (g in listOf(RgbGenerator.WAVES, RgbGenerator.PLASMA, RgbGenerator.NOISE)) {
            val rr = renderer(RgbPatternLayer(g, redBlue(), speed = 1f))
            for (i in 0 until 50) {
                val c = rr.at(i / 50f, (i % 7) / 7f, phase = i * 0.37)
                assertEquals(255, alpha(c), "$g covers everything")
                assertTrue(stopColors.any { delta(it, c) <= 4 }, "$g: ${hex(c)} lies on the stops")
            }
        }
    }

    @Test fun stripesHaveGaps() {
        val stripes = renderer(RgbPatternLayer(RgbGenerator.STRIPES, solid(red), speed = 0f))
        // six stripes across: w = 6 x; f = 0.25 is inside, 0.75 in the gap
        assertEquals(255, alpha(stripes.at(0.25f / 6f, 0.5f)), "inside a stripe")
        assertEquals(0, stripes.at(0.75f / 6f, 0.5f), "in the gap nothing")
        assertEquals(255, alpha(stripes.at(1.25f / 6f, 0.5f)), "the next stripe")
    }

    @Test fun contourLinesAndSparklesAreSparse() {
        val contour = renderer(RgbPatternLayer(RgbGenerator.CONTOUR, solid(red), speed = 0f))
        val sparkle = renderer(RgbPatternLayer(RgbGenerator.SPARKLE, solid(red), speed = 0f))
        for ((name, r) in listOf("contour" to contour, "sparkle" to sparkle)) {
            var empty = 0
            var bright = 0
            for (row in 0 until 32) for (col in 0 until 48) {
                val a = alpha(r.at((col + 0.5f) / 48, (row + 0.5f) / 32, phase = 0.3))
                if (a == 0) empty++
                if (a > 100) bright++
            }
            assertTrue(empty > 300, "$name leaves room between lines or sparks ($empty empty)")
            assertTrue(bright > 4, "$name draws something bright ($bright bright)")
        }
        // a spark is brightest at the centre of its cell (angle 0, 10 cells across, +1000 cells) and twinkles with time
        val twinkling = renderer(RgbPatternLayer(RgbGenerator.SPARKLE, solid(red), speed = 1f))
        val cell = 1f / 10f
        var best = 0
        var bestPhase = 0.0
        for (k in 0 until 100) { val a = alpha(twinkling.at(0.5f + cell / 2, 0.5f + cell, phase = k / 100.0)); if (a > best) { best = a; bestPhase = k / 100.0 } }
        assertTrue(best > 200, "the spark of a cell lights up within one cycle")
        assertTrue((0 until 100).any { alpha(twinkling.at(0.5f + cell / 2, 0.5f + cell, phase = it / 100.0)) == 0 }, "and goes dark again")
        assertTrue(alpha(twinkling.at(0.5f + cell / 2 + cell * 0.45f, 0.5f + cell, phase = bestPhase)) < best, "darker towards the edge")
    }

    @Test fun rippleAndSpotlightFollowTheTaps() {
        val tx = FloatArray(8); val ty = FloatArray(8); val tt = LongArray(8) { Long.MIN_VALUE }
        val ripple = renderer(RgbPatternLayer(RgbGenerator.RIPPLE, solid(red), speed = 0f))
        assertEquals(0, ripple.at(0.8f, 0.5f, tx = tx, ty = ty, tt = tt), "no tap, no ring")
        tx[0] = 0.5f; ty[0] = 0.5f; tt[0] = 0
        // after a quarter of the glow time the ring is 0.3 wide and at 3/4 strength
        assertEquals((0.75f * 255).roundToInt(), alpha(ripple.at(0.8f, 0.5f, now = 250, tx = tx, ty = ty, tt = tt)))
        assertEquals(0, ripple.at(0.5f, 0.5f, now = 250, tx = tx, ty = ty, tt = tt), "inside the ring nothing")
        assertEquals(0, ripple.at(0.8f, 0.5f, now = 1000, tx = tx, ty = ty, tt = tt), "faded")

        val spot = renderer(RgbPatternLayer(RgbGenerator.SPOTLIGHT, solid(red), speed = 0f))
        val none = LongArray(8) { Long.MIN_VALUE }
        assertEquals(255, alpha(spot.at(0.5f, 0.5f, tt = none)), "before any tap: at the centre")
        assertEquals(0, spot.at(0.95f, 0.5f, tt = none))
        tx[0] = 0.2f; ty[0] = 0.5f; tt[0] = 0
        assertEquals(255, alpha(spot.at(0.2f, 0.5f, now = 1000, tx = tx, ty = ty, tt = tt)), "settled on the key")
        // half way through the glide (90 of 180 ms, smoothstep 0.5): between the centre and the key
        assertEquals(255, alpha(spot.at(0.35f, 0.5f, now = 90, tx = tx, ty = ty, tt = tt)), "gliding")
        tx[1] = 0.8f; ty[1] = 0.5f; tt[1] = 500
        assertEquals(255, alpha(spot.at(0.8f, 0.5f, now = 1000, tx = tx, ty = ty, tt = tt)), "follows the newest key")
        assertTrue(alpha(spot.at(0.2f, 0.5f, now = 1000, tx = tx, ty = ty, tt = tt)) == 0, "left the old one")
    }

    // --- blend modes ---

    @Test fun blendModesFollowTheW3CFormulas() {
        val a = 0xFFC86432.toInt() // 200, 100, 50
        val b = 0xFF64C8FA.toInt() // 100, 200, 250
        fun blended(mode: RgbBlend, opacity: Int = 50) =
            renderer(RgbPatternLayer(RgbGenerator.FILL, solid(a), speed = 0f), RgbPatternLayer(RgbGenerator.FILL, solid(b), speed = 0f, blend = mode, opacity = opacity))
                .at(0.5f, 0.5f)
        fun expect(f: (Float, Float) -> Float): Int {
            fun c(x: Int, y: Int) = ((0.5f * f(x / 255f, y / 255f) + 0.5f * x / 255f) * 255f).roundToInt()
            return RgbColors.argb(255, c(200, 100), c(100, 200), c(50, 250))
        }
        assertColor(expect { _, s -> s }, blended(RgbBlend.NORMAL), 2, "normal")
        assertColor(expect { d, s -> minOf(1f, d + s) }, blended(RgbBlend.ADD), 2, "add")
        assertColor(expect { d, s -> d * s }, blended(RgbBlend.MULTIPLY), 2, "multiply")
        assertColor(expect { d, s -> d + s - d * s }, blended(RgbBlend.SCREEN), 2, "screen")
        for (m in RgbBlend.entries) assertEquals(255, alpha(blended(m)), "$m over an opaque layer stays opaque")
        // over nothing every mode is the source itself; two half layers cover three quarters
        for (m in RgbBlend.entries) {
            val alone = renderer(RgbPatternLayer(RgbGenerator.FILL, solid(b), speed = 0f, blend = m, opacity = 50)).at(0.5f, 0.5f)
            assertColor(b, alone, message = "$m alone")
            assertEquals(128, alpha(alone))
        }
        val two = renderer(RgbPatternLayer(RgbGenerator.FILL, solid(a), speed = 0f, opacity = 50),
            RgbPatternLayer(RgbGenerator.FILL, solid(b), speed = 0f, opacity = 50)).at(0.5f, 0.5f)
        assertEquals(191, alpha(two))
        assertEquals(0, renderer(RgbPatternLayer(RgbGenerator.FILL, solid(b), opacity = 0)).at(0.5f, 0.5f), "opacity 0: nothing")
    }

    @Test fun blendFunctionsAreSeparable() {
        assertEquals(0.3f, RgbOwnRenderer.blend(RgbBlend.NORMAL, 0.9f, 0.3f))
        assertEquals(1f, RgbOwnRenderer.blend(RgbBlend.ADD, 0.9f, 0.3f))
        assertEquals(0.27f, RgbOwnRenderer.blend(RgbBlend.MULTIPLY, 0.9f, 0.3f), 1e-6f)
        assertEquals(0.93f, RgbOwnRenderer.blend(RgbBlend.SCREEN, 0.9f, 0.3f), 1e-6f)
    }

    // --- curves, palettes, typing ---

    @Test fun theMotionCurveShapesTheColourPosition() {
        val steps = RgbCurve.preset(RgbWaveform.STEPS, 4)
        val r = renderer(RgbPatternLayer(RgbGenerator.LINEAR, redBlue(), speed = 0f, curve = steps))
        // u = -x: x 0.30 and 0.45 lie in the same quarter of the cycle
        assertEquals(r.at(0.30f, 0.5f), r.at(0.45f, 0.5f), "one step, one colour")
        assertNotEquals(r.at(0.30f, 0.5f), r.at(0.55f, 0.5f), "the next step another")
        val sine = RgbCurve.preset(RgbWaveform.SINE)
        val s = renderer(RgbPatternLayer(RgbGenerator.FILL, redBlue(), curve = sine))
        assertColor(table(redBlue(), sine.value(0.3f), sine.cyclic), s.at(0.5f, 0.5f, phase = 0.3), message = "curve value")
    }

    @Test fun stopTablesRunRoundOrHoldTheirEnds() {
        val stops = listOf(RgbStop(0.25f, red), RgbStop(0.75f, blue))
        val open = RgbOwnRenderer.stopTable(stops, false)
        assertEquals(red, open[0]); assertEquals(red, open[RgbOwnRenderer.TABLE / 4])
        assertEquals(blue, open[RgbOwnRenderer.TABLE])
        val round = RgbOwnRenderer.stopTable(stops, true)
        // half way from blue (0.75) round to red (1.25) is 1.0 = 0.0
        assertColor(RgbColors.mixOklch(blue, red, 0.5f), round[0])
        assertEquals(round[0], round[RgbOwnRenderer.TABLE], "the ends meet")
        assertEquals(red, round[RgbOwnRenderer.TABLE / 4])
        // saturation and brightness of the look apply to the stops
        val dim = RgbOwnRenderer.stopTable(solid(red), false, saturation = 100, brightness = 50)
        assertColor(0xFF800000.toInt(), dim[0])
    }

    @Test fun hueCycleTurnsTheColoursOncePerCycle() {
        val r = renderer(RgbPatternLayer(RgbGenerator.FILL, solid(red), speed = 0f, palette = RgbPaletteMode.HUE_CYCLE))
        val start = r.at(0.5f, 0.5f, phase = 0.0)
        assertColor(red, start, 1, "phase 0: unchanged")
        val half = r.at(0.5f, 0.5f, phase = 0.5)
        assertTrue(ch(half, 16) < 80 && ch(half, 8) > 100 && ch(half, 0) > 100, "half a turn: red becomes cyan-ish ${hex(half)}")
        assertEquals(start, r.at(0.5f, 0.5f, phase = 1.0), "a whole turn is back")
        assertEquals(half, r.at(0.5f, 0.5f, phase = 1.5))
        assertTrue(pattern(RgbPatternLayer(speed = 0f, palette = RgbPaletteMode.HUE_CYCLE)).moves, "a turning hue moves")
        assertFalse(pattern(RgbPatternLayer(speed = 0f)).moves, "speed 0 and fixed colours stand still")
    }

    @Test fun typingReactionsArePureFunctionsOfTheTaps() {
        val tx = floatArrayOf(0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val ty = floatArrayOf(0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val tt = LongArray(8) { Long.MIN_VALUE }.also { it[0] = 1000 }
        val pulse = renderer(RgbPatternLayer(RgbGenerator.FILL, solid(red), reaction = RgbLayerReaction.PULSE))
        assertEquals(0, pulse.at(0.5f, 0.5f, now = 999, tt = LongArray(8) { Long.MIN_VALUE }), "no tap: no pulse")
        assertEquals(255, alpha(pulse.at(0.9f, 0.5f, now = 1000, tx = tx, ty = ty, tt = tt)), "full at the tap, everywhere")
        assertEquals((RgbOwnRenderer.fade(500, 1000) * 255).roundToInt(), alpha(pulse.at(0.9f, 0.5f, now = 1500, tx = tx, ty = ty, tt = tt)))
        assertEquals(0, pulse.at(0.9f, 0.5f, now = 2000, tx = tx, ty = ty, tt = tt), "faded after the glow time")

        val ring = renderer(RgbPatternLayer(RgbGenerator.FILL, solid(red), reaction = RgbLayerReaction.RIPPLE))
        // 250 ms: the ring is 0.3 from the key
        assertTrue(alpha(ring.at(0.6f, 0.5f, now = 1250, tx = tx, ty = ty, tt = tt)) > 180)
        assertEquals(0, ring.at(0.3f, 0.5f, now = 1250, tx = tx, ty = ty, tt = tt), "not at the key any more")

        val glow = renderer(RgbPatternLayer(RgbGenerator.FILL, solid(red), reaction = RgbLayerReaction.KEY_GLOW))
        assertEquals(255, alpha(glow.at(0.3f, 0.5f, now = 1000, tx = tx, ty = ty, tt = tt)), "the typed key lights up")
        assertEquals(0, glow.at(0.6f, 0.5f, now = 1000, tx = tx, ty = ty, tt = tt), "other keys stay dark")
        assertTrue(alpha(glow.at(0.3f, 0.5f, now = 1600, tx = tx, ty = ty, tt = tt)) in 1..120, "and fades")
        // the same inputs always give the same light, also after other calls
        val first = glow.at(0.31f, 0.52f, now = 1300, tx = tx, ty = ty, tt = tt)
        glow.at(0.9f, 0.1f, now = 5000, tx = tx, ty = ty, tt = tt)
        assertEquals(first, glow.at(0.31f, 0.52f, now = 1300, tx = tx, ty = ty, tt = tt))
    }

    @Test fun samplesAreDeterministicAndTimeRepeatsExactly() {
        val layers = arrayOf(
            RgbPatternLayer(RgbGenerator.PLASMA, redBlue(), speed = 0.35f, angle = 33, scale = 1.4f),
            RgbPatternLayer(RgbGenerator.SPARKLE, solid(0xFFFFFFFF.toInt()), speed = -1.25f, blend = RgbBlend.SCREEN, opacity = 70),
            RgbPatternLayer(RgbGenerator.CONTOUR, solid(blue), speed = 0.5f, blend = RgbBlend.ADD, palette = RgbPaletteMode.HUE_CYCLE),
            RgbPatternLayer(RgbGenerator.NOISE, redBlue(), speed = 2f, blend = RgbBlend.MULTIPLY, opacity = 40),
        )
        val a = renderer(*layers)
        val b = renderer(*layers)
        for (i in 0 until 200) {
            val x = (i % 20) / 20f + 0.02f
            val y = (i / 20) / 10f + 0.03f
            val p = i * 0.137
            val va = a.at(x, y, phase = p)
            assertEquals(va, b.at(x, y, phase = p), "deterministic")
            // speeds are steps of 1/20: 20 cycles later every layer is where it was
            val later = a.at(x, y, phase = p + 20)
            assertTrue(delta(va, later) <= 1 && abs(alpha(va) - alpha(later)) <= 1, "repeats after 20 cycles")
            // and across the engine's phase wrap (whole drift periods of 200 cycles)
            val wrapped = a.at(x, y, phase = p + RgbOverlay.DRIFT_PERIOD * 5000.0)
            assertTrue(delta(va, wrapped) <= 2 && abs(alpha(va) - alpha(wrapped)) <= 2, "repeats across the wrap: ${hex(va)} ${hex(wrapped)}")
        }
    }

    // --- normalizing and the settings ---

    @Test fun everyNumberIsClampedAndRounded() {
        val wild = RgbPatternLayer(stops = List(12) { RgbStop(1f - it / 12f, 0x00123456 + it) }, speed = 99f, angle = -30, scale = 100f,
            offsetX = -7f, offsetY = Float.NaN, opacity = 300).normalized()
        assertEquals(RgbPatternLayer.MAX_SPEED, wild.speed)
        assertEquals(330, wild.angle)
        assertEquals(RgbPatternLayer.MAX_SCALE, wild.scale)
        assertEquals(-1f, wild.offsetX); assertEquals(0f, wild.offsetY)
        assertEquals(100, wild.opacity)
        assertEquals(RgbPatternLayer.MAX_STOPS, wild.stops.size)
        assertEquals(wild.stops.sortedBy { it.position }, wild.stops, "sorted")
        assertTrue(wild.stops.all { (it.color ushr 24) == 0xFF }, "opaque")
        assertEquals(-0.05f, RgbPatternLayer(speed = -0.033f).normalized().speed)
        assertEquals(1f, RgbPatternLayer(speed = Float.POSITIVE_INFINITY).normalized().speed)
        assertEquals(RgbPatternLayer.DEFAULT_STOPS, RgbPatternLayer(stops = listOf(RgbStop(0f, red))).normalized().stops)
        val many = RgbOwnPattern("p1", "  Viele  ", List(9) { RgbPatternLayer() }).normalized()
        assertEquals(RgbOwnPattern.MAX_LAYERS, many.layers.size)
        assertEquals("Viele", many.name)
        for (bad in listOf("", "   ", "a".repeat(33), "Muster\n2", "https://x.de", "www.muster", "<b>", "a‮b"))
            assertNull(RgbOwnPattern.cleanName(bad), bad)
        assertEquals("Polarlicht ✨", RgbOwnPattern.cleanName("Polarlicht ✨"))
    }

    @Test fun theSettingsStoreOnlyTheIdAndResolveIt() {
        val own = pattern(RgbPatternLayer(RgbGenerator.RADIAL, redBlue()))
        val library = RgbPatternLibrary(listOf(own))
        val s = RgbSettings(enabled = true, pattern = RgbPattern.CIRCLE, own = own)
        val text = s.encode()
        assertTrue(";own=p1" in text, text)
        assertFalse("RADIAL" in text, "the layers are not in the settings text")
        val read = RgbSettings.parse(text)
        assertEquals("p1", read.own?.id)
        assertFalse(read.own!!.usable, "only a reference until resolved")
        assertEquals(s.normalized(), read.resolve(library))
        // a deleted pattern: the element draws its built-in pattern again
        val gone = read.resolve(RgbPatternLibrary.EMPTY)
        assertNull(gone.own)
        assertEquals(RgbPattern.CIRCLE, gone.pattern)
        // own looks per element: only the element that has one
        val sep = RgbSettings(enabled = true, own = own).withSeparate(true).let { it.withLook(RgbTarget.BACKGROUND, it.lookFor(RgbTarget.BACKGROUND).copy(own = null)) }
        val parsed = RgbSettings.parse(sep.encode()).resolve(library)
        assertEquals(own, parsed.lookFor(RgbTarget.KEYS).own)
        assertNull(parsed.lookFor(RgbTarget.BACKGROUND).own, "never inherited from the shared look")
        assertEquals(setOf("p1"), parsed.ownIds())
        // an id with other characters is ignored
        assertNull(RgbSettings.parse("v=2;on=1;own=../x").own)
        assertNull(RgbSettings.parse("v=2;on=1;own=P1").own)
    }

    @Test fun movingAndTapsFollowTheOwnPattern() {
        val still = pattern(RgbPatternLayer(speed = 0f))
        val spot = pattern(RgbPatternLayer(RgbGenerator.SPOTLIGHT, speed = 0f))
        val sStill = RgbSettings(enabled = true, own = still)
        assertFalse(sStill.moves(), "a still own pattern needs no frames")
        assertTrue(RgbSettings(enabled = true, own = pattern(RgbPatternLayer())).moves())
        assertFalse(sStill.reactsToTaps())
        assertTrue(RgbSettings(enabled = true, own = spot).reactsToTaps(), "a spotlight follows the keys")
        assertTrue(RgbSettings(enabled = true, own = pattern(RgbPatternLayer(reaction = RgbLayerReaction.PULSE))).reactsToTaps())
        // the engine: no tile, no phase while still, an effect while the spot glides or a reaction fades
        val e = RgbEngine(RgbSettings(enabled = true, own = spot))
        assertNull(e.tilePattern)
        assertEquals(RgbPattern.INK, RgbEngine(RgbSettings(enabled = true, pattern = RgbPattern.INK)).tilePattern)
        e.advance(0); e.advance(100)
        assertEquals(0.0, e.phase)
        assertFalse(e.hasActiveEffect(100))
        e.tap(0.2f, 0.5f, 7, 200)
        assertTrue(e.hasActiveEffect(300))
        assertFalse(e.hasActiveEffect(200 + 1000), "the glow time (800 ms) and the glide are over")
        // the engine's light is the renderer's sample with the settings' mix and tap effects on top
        assertEquals(255, e.light(0.2f, 0.5f, -1, 1000) ushr 24)
        assertEquals(0, e.light(0.9f, 0.5f, -1, 1000) ushr 24)
        // a bare reference (library not read) draws the built-in pattern
        val ref = RgbEngine(RgbSettings(enabled = true, pattern = RgbPattern.CIRCLE, own = RgbOwnPattern.reference("p9")))
        assertNull(ref.own)
        assertEquals(RgbEngine(RgbSettings(enabled = true, pattern = RgbPattern.CIRCLE)).light(0.3f, 0.3f, -1, 0), ref.light(0.3f, 0.3f, -1, 0))
    }

    @Test fun duplicatesOfTheBuiltInPatternsAreValidAndTheWavesMatch() {
        for (p in RgbPattern.entries) for (rainbow in listOf(true, false)) {
            val made = RgbOwnPattern.from(RgbLook(pattern = p, rainbow = rainbow), "p3", "Kopie")
            assertTrue(made.layers.size in 1..RgbOwnPattern.MAX_LAYERS, "$p")
            assertEquals(made, made.normalized(), "$p is already normal")
            assertEquals(p != RgbPattern.STATIC_RAINBOW, made.moves, "$p moves like the original")
        }
        // the waves and the circle with own colours: the same light as the built-in pattern
        val palette = listOf(0xFFFF0050.toInt(), 0xFF00C8FF.toInt(), 0xFF9B4DFF.toInt())
        for (p in listOf(RgbPattern.WAVE_HORIZONTAL, RgbPattern.WAVE_VERTICAL, RgbPattern.CIRCLE)) {
            val look = RgbLook(pattern = p, rainbow = false, palette = palette, waveWidth = 1.5f)
            val builtIn = RgbEngine(look.applyTo(RgbSettings(enabled = true)))
            val own = RgbEngine(look.copy(own = RgbOwnPattern.from(look, "p1", "Kopie")).applyTo(RgbSettings(enabled = true)))
            for (e in listOf(builtIn, own)) { e.aspect = 0.5f; e.advance(0); e.advance(90); e.advance(180) }
            var worst = 0
            for (i in 0 until 60) {
                val x = (i % 10) / 10f + 0.05f
                val y = (i / 10) / 6f + 0.04f
                worst = maxOf(worst, delta(builtIn.light(x, y, -1, 180), own.light(x, y, -1, 180)))
            }
            assertTrue(worst <= 3, "$p: the copy differs by $worst")
        }
    }

    @Test fun theLibraryNamesAndNumbersItsPatterns() {
        var lib = RgbPatternLibrary.EMPTY
        val (l1, a) = lib.add(pattern(RgbPatternLayer()).copy(name = "Welle"))!!
        lib = l1
        val (l2, b) = lib.add(pattern(RgbPatternLayer()).copy(name = "Welle"))!!
        lib = l2
        assertEquals("p1", a.id); assertEquals("p2", b.id)
        assertEquals("Welle 2", b.name)
        lib = lib.rename("p1", "Polar")!!
        assertEquals("Polar", lib["p1"]!!.name)
        assertNull(lib.rename("p1", "https://evil"), "an address is no name")
        lib = lib.remove("p1")
        assertEquals(listOf("p2"), lib.patterns.map { it.id })
        assertEquals("p3", lib.nextId(), "a new id never reuses a used one")
        repeat(RgbPatternLibrary.MAX_PATTERNS) { lib = lib.add(pattern(RgbPatternLayer()))?.first ?: lib }
        assertEquals(RgbPatternLibrary.MAX_PATTERNS, lib.patterns.size)
        assertNull(lib.add(pattern(RgbPatternLayer())), "full")
        assertEquals("a".repeat(30) + " 2", RgbPatternLibrary(listOf(RgbOwnPattern("p1", "a".repeat(32), emptyList()))).uniqueName("a".repeat(32)))
    }

    // --- cost per frame ---

    /**
     * The cost of one frame (the 1634 values of [RgbPatternsTest.oneFrameOfEachPatternFitsTheBudget]:
     * grid 48 x 32, 45 keys twice, strip) for the heaviest own patterns, against the heaviest built-in
     * pattern (ink) on the same JVM in the same run. Four layers may cost at most what two
     * different built-in looks already may (P214), and never more than 4 ms.
     */
    @Test fun ownPatternsStayWithinTheBudget() {
        fun cost(settings: RgbSettings): Double {
            val e = RgbEngine(settings.copy(tapReaction = RgbTapReaction.RIPPLE, glowMs = 5000)).apply { aspect = 0.65f }
            for (i in 0 until RgbEngine.MAX_TAPS) e.tap(i / 8f, 0.5f, i, 0)
            fun frame(t: Long): Int {
                var sum = 0
                for (r in 0 until 32) for (c in 0 until 48) sum += e.light((c + 0.5f) / 48, (r + 0.5f) / 32, -1, t)
                for (k in 0 until 45) { sum += e.light(k / 45f, (k % 5) / 5f, k, t); sum += e.light(k / 45f, (k % 5) / 5f, -1, t) }
                for (s in 0 until 8) sum += e.light(s / 8f, 0f, -1, t)
                return sum
            }
            repeat(60) { frame(it.toLong()) }
            var best = Double.MAX_VALUE
            var sink = 0
            repeat(3) {
                val start = System.nanoTime()
                for (f in 0 until 60) { e.advance(f * 33L); sink += frame(f * 33L % 5000) }
                best = minOf(best, (System.nanoTime() - start) / 1e6 / 60)
            }
            if (sink == 42) println()
            return best
        }
        val ink = cost(RgbSettings(enabled = true, pattern = RgbPattern.INK))
        val heavy = mapOf(
            "4 x noise" to List(4) { RgbPatternLayer(RgbGenerator.NOISE, speed = 1f, blend = RgbBlend.SCREEN, opacity = 60) },
            "4 x contour" to List(4) { RgbPatternLayer(RgbGenerator.CONTOUR, speed = 1f, blend = RgbBlend.ADD, palette = RgbPaletteMode.HUE_CYCLE) },
            "plasma, sparkle, ripple, spotlight" to listOf(RgbPatternLayer(RgbGenerator.PLASMA), RgbPatternLayer(RgbGenerator.SPARKLE, blend = RgbBlend.SCREEN),
                RgbPatternLayer(RgbGenerator.RIPPLE, blend = RgbBlend.ADD), RgbPatternLayer(RgbGenerator.SPOTLIGHT, reaction = RgbLayerReaction.KEY_GLOW)),
            "1 x linear" to listOf(RgbPatternLayer()),
        )
        val report = StringBuilder("INK (built-in, heaviest): ${"%.2f".format(ink)} ms per frame\n")
        for ((name, layers) in heavy) {
            val ms = cost(RgbSettings(enabled = true, own = RgbOwnPattern("p1", name, layers)))
            report.append("$name: ${"%.2f".format(ms)} ms per frame\n")
            assertTrue(ms <= maxOf(2 * ink, 0.5) && ms < 4.0, "$name: $ms ms per frame against ink $ink ms")
        }
        println(report)
        File("build/rgb-previews").mkdirs()
        File("build/rgb-previews/own-pattern-cost.txt").writeText(report.toString())
    }

    /** Nothing per sample is allocated: the same renderer gives the same light a million times without garbage. */
    @Test fun samplingDoesNotAllocate() {
        val r = renderer(RgbPatternLayer(RgbGenerator.CONTOUR, palette = RgbPaletteMode.HUE_CYCLE), RgbPatternLayer(RgbGenerator.SPOTLIGHT, blend = RgbBlend.ADD))
        val bean = java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean ?: return
        repeat(20_000) { r.at(it / 20_000f, 0.5f, phase = it * 0.001) }
        val before = bean.getThreadAllocatedBytes(Thread.currentThread().id)
        var sink = 0
        for (i in 0 until 100_000) sink += r.at((i % 48) / 48f, (i % 32) / 32f, phase = i * 0.0003)
        val bytes = bean.getThreadAllocatedBytes(Thread.currentThread().id) - before
        assertTrue(bytes < 64 * 1024, "$bytes bytes allocated for 100000 samples [$sink]")
    }
}
