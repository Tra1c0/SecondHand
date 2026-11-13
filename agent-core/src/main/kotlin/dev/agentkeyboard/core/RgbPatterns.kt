// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Smooth value noise (quintic fade, fixed seed): deterministic, allocation-free, periodic with
 * [period] lattice cells (a power of two up to 256), so tiles built from it repeat without seams.
 */
object RgbNoise {
    private val perm = IntArray(512).also { p ->
        val base = IntArray(256) { it }
        var seed = 0x2F6E2B1
        for (i in 255 downTo 1) {
            seed = seed * 1103515245 + 12345
            val j = ((seed ushr 8) and 0x7FFFFFFF) % (i + 1)
            val t = base[i]; base[i] = base[j]; base[j] = t
        }
        for (i in 0 until 512) p[i] = base[i and 255]
    }

    private fun lattice(ix: Int, iy: Int, mask: Int): Float = perm[perm[ix and mask] + (iy and mask)] / 255f

    /** Noise 0..1 at ([x], [y]); repeats every [period] units in both directions. */
    fun noise(x: Float, y: Float, period: Int = 256): Float {
        val mask = (period.coerceIn(1, 256)) - 1
        val fx = floor(x)
        val fy = floor(y)
        val ix = fx.toInt()
        val iy = fy.toInt()
        val tx = fade(x - fx)
        val ty = fade(y - fy)
        val a = lattice(ix, iy, mask)
        val b = lattice(ix + 1, iy, mask)
        val c = lattice(ix, iy + 1, mask)
        val d = lattice(ix + 1, iy + 1, mask)
        val top = a + (b - a) * tx
        val bottom = c + (d - c) * tx
        return top + (bottom - top) * ty
    }

    /** Three octaves of [noise], 0..1; periodic with [period] (the base octave's cells). */
    fun fbm(x: Float, y: Float, period: Int = 64): Float {
        var sum = 0f
        var amp = 0.5f
        var px = x
        var py = y
        var per = period
        repeat(3) {
            sum += amp * noise(px, py, per)
            px = px * 2f + 17.3f
            py *= 2f
            per = (per * 2).coerceAtMost(256)
            amp *= 0.5f
        }
        return sum / 0.875f
    }

    private fun fade(t: Float) = t * t * t * (t * (t * 6f - 15f) + 10f)
}

/**
 * The animated texture patterns of stage 29c, as maths without android.graphics. Each pattern has
 * a smooth colour field ([field]: colour position, brightness, whitening) that the engine turns
 * into light, and optionally a fine detail layer ([RgbOverlay]: LED grid, hatching, slats, contour
 * lines, diamond outlines) that the app draws over it as a repeating tile.
 */
object RgbPatterns {
    /** Result of [field], written into a reusable holder so the engine stays allocation-free. */
    class Sample {
        /** Colour position in cycles (only the fraction counts). */
        var u = 0f
        /** Brightness factor 0..1 (dark parts of the pattern). */
        var brightness = 1f
        /** Share of white mixed in 0..1 (pale parts). */
        var whiten = 0f
        /** Share of grey mixed in 0..1 (the colourless middle of [RgbPattern.HATCH]). */
        var grey = 0f
    }

    /**
     * The colour field of a texture pattern at ([x], [y]) (0..1 across the lit area) for
     * [aspect] = height / width, at [time] (cycles of the configured period, so "Dauer" sets the
     * speed of the flow too) and with [width] colour cycles across the keyboard. [colourShift] is
     * the colour position added by the clock (+phase or -phase).
     */
    fun field(pattern: RgbPattern, x: Float, y: Float, aspect: Float, time: Float, colourShift: Float, width: Float, out: Sample) {
        val yy = y * aspect
        out.brightness = 1f
        out.whiten = 0f
        out.grey = 0f
        when (pattern) {
            RgbPattern.INK -> {
                // domain-warped noise: colour bands follow the warped flow like ink in water,
                // black where there is no ink
                val t = time * 0.3f
                val px = x * 2.0f
                val py = yy * 2.0f
                val qx = RgbNoise.fbm(px + t * 0.4f, py - t * 0.15f)
                val qy = RgbNoise.fbm(px + 5.2f - t * 0.25f, py + 1.3f + t * 0.3f)
                val wx = px + 1.8f * qx
                val wy = py + 1.8f * qy
                val rx = RgbNoise.fbm(wx * 0.9f + 3.1f + t * 0.2f, wy * 0.9f + 7.7f)
                val band = 0.55f * wx + 0.35f * wy + 1.2f * rx
                out.u = colourShift + width * 0.3f * band
                val density = RgbNoise.fbm(wx * 1.2f + 11.3f, wy * 1.2f - 4.1f + t * 0.1f)
                out.brightness = smooth(0.27f, 0.6f, density) * (0.6f + 0.4f * smooth(0.3f, 0.8f, rx))
            }
            RgbPattern.DIAMONDS -> {
                // bright diagonal rainbow with broad, soft lighter bands (outlines: overlay)
                val wave = 0.06f * sin(2f * PI.toFloat() * (0.7f * x + 0.5f * yy) + time * 0.6f)
                out.u = colourShift + width * (0.42f * x - 0.38f * yy + wave)
                val band = 0.5f + 0.5f * sin(2f * PI.toFloat() * ((x + yy) * 1.6f) - time * 0.4f)
                out.whiten = 0.08f + 0.14f * band
                out.brightness = 0.9f + 0.1f * band
            }
            RgbPattern.SLATS -> {
                // two colour worlds with a hard change along a slowly wandering line
                val edge = 0.5f + 0.3f * sin(2f * PI.toFloat() * time * 0.12f) + 0.1f * (y - 0.5f)
                val right = x > edge
                out.u = colourShift + if (right) width * (0.05f * yy) + 0.5f else width * (0.25f * (1f - y) - 0.12f)
                val d = (x - edge) / 0.04f
                val dip = 1f - 0.97f * exp(-d * d)
                val light = if (right) 0.38f + 0.62f * smooth(-0.1f, 1.0f, 0.3f * x + 0.9f * y)
                else 0.18f + 0.82f * smooth(0.05f, 1.15f, 0.65f * x + 0.9f * y)
                out.brightness = light * dip
            }
            RgbPattern.HATCH -> {
                // rainbow turning around a wandering centre, colourless middle, dark vignette
                val cx = 0.5f + 0.07f * sin(time * 0.9f)
                val cy = 0.5f * aspect + 0.05f * cos(time * 0.7f)
                val turns = max(1f, width.roundToInt().toFloat())
                val angle = atan2(yy - cy, x - cx) / (2f * PI.toFloat())
                out.u = colourShift + turns * angle
                val r = sqrt((x - cx) * (x - cx) + (yy - cy) * (yy - cy))
                out.grey = 0.55f * exp(-(r / 0.16f) * (r / 0.16f))
                val vx = (x - 0.5f) / 0.5f
                val vy = (y - 0.5f) / 0.5f
                val v = sqrt(vx * vx * 0.8f + vy * vy * 0.6f)
                out.brightness = 1f - 0.92f * smooth(0.45f, 1.25f, v)
            }
            RgbPattern.LED -> {
                // flowing rainbow with pale patches; the LED grid is the overlay
                val flow = 0.10f * sin(2f * PI.toFloat() * (1.1f * x - 0.7f * yy) + time * 2.2f) +
                    0.07f * sin(2f * PI.toFloat() * (0.6f * x + 1.3f * yy) - time * 1.5f)
                out.u = colourShift + width * (0.5f * x + 0.45f * yy + flow)
                out.whiten = 0.22f * smooth(0.55f, 1f, 0.5f + 0.5f * sin(2f * PI.toFloat() * (0.8f * x + 0.5f * yy) + time * 0.9f))
            }
            RgbPattern.TOPO -> {
                // soft rainbow hills with darker valleys; the contour lines are the overlay
                val t = time * 0.12f
                val h = RgbNoise.fbm(x * 1.4f + t, yy * 1.4f - t * 0.7f)
                out.u = colourShift + width * (0.45f * x + 0.3f * yy + 0.6f * (h - 0.5f))
                val shade = RgbNoise.fbm(x * 1.1f + 7.1f - t * 0.6f, yy * 1.1f + 3.3f + t * 0.5f)
                out.brightness = 0.5f + 0.5f * smooth(0.3f, 0.7f, shade)
            }
            else -> out.u = colourShift
        }
    }

    /**
     * The flow of [pattern]'s [field] repeats after this many cycles: every time term above is a
     * whole number of turns (sines) or of noise periods (64 lattice cells) after it. The engine wraps
     * its flow time there, so the wrap is invisible (an arbitrary wrap jumped). Change it together
     * with the time factors in [field]; `RgbPatternsTest.theFlowRepeatsExactlyAtItsPeriod` checks it.
     */
    fun flowPeriod(pattern: RgbPattern): Double = when (pattern) {
        // t = 0.3 time; noise shifts 0.4, 0.15, 0.25, 0.3, 0.2, 0.1 t: all multiples of 64 at t = 1280
        RgbPattern.INK -> 1280.0 / 0.3
        // sines with 0.6 and 0.4 time: 6 pi and 4 pi turns at 10 pi
        RgbPattern.DIAMONDS -> 10.0 * PI
        // sin(2 pi 0.12 time): one turn
        RgbPattern.SLATS -> 1.0 / 0.12
        // sin 0.9 time, cos 0.7 time (hatch); 2.2, 1.5, 0.9 time (LED): whole turns at 20 pi
        RgbPattern.HATCH, RgbPattern.LED -> 20.0 * PI
        // t = 0.12 time; noise shifts 1, 0.7, 0.6, 0.5 t: all multiples of 64 at t = 640
        RgbPattern.TOPO -> 640.0 / 0.12
        else -> 4096.0
    }

    fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/** A repeating detail tile: ARGB colours (not premultiplied), row by row. */
class RgbTile(val width: Int, val height: Int, val pixels: IntArray) {
    /** The average colour the tile adds, as ARGB (alpha = average coverage); for label contrast. */
    val mean: Int = run {
        var a = 0.0; var r = 0.0; var g = 0.0; var b = 0.0
        for (p in pixels) {
            val pa = (p ushr 24) / 255.0
            a += pa; r += ((p shr 16) and 0xFF) * pa; g += ((p shr 8) and 0xFF) * pa; b += (p and 0xFF) * pa
        }
        if (a <= 0.0) 0 else RgbColors.argb((a / pixels.size * 255).roundToInt(), (r / a).roundToInt(), (g / a).roundToInt(), (b / a).roundToInt())
    }
}

/**
 * Fine detail of the texture patterns (stage 29c) as tiles that are made once and then only moved:
 * the app draws them with a repeating bitmap shader over the light, so the detail is sharp at
 * screen resolution while the per-frame work stays a matrix ([transform]). Pure and deterministic.
 */
object RgbOverlay {
    private const val LED_TILE = 32
    private const val HATCH_TILE = 12
    private const val SLAT_TILE = 96
    const val BIG_TILE = 384
    private val TWO_PI = (2 * PI).toFloat()

    fun hasOverlay(pattern: RgbPattern) = pattern in setOf(RgbPattern.DIAMONDS, RgbPattern.SLATS, RgbPattern.HATCH, RgbPattern.LED, RgbPattern.TOPO)

    /** The tile of [pattern], or null if it has none. Big tiles (contours, diamonds) take some ms: make once, cache. */
    fun tile(pattern: RgbPattern): RgbTile? = when (pattern) {
        RgbPattern.LED -> ledTile()
        RgbPattern.HATCH -> stripeTile(HATCH_TILE) { t ->
            // a dark line over 45 % of the period with soft edges
            val d = abs(t - 0.5f) * 2f
            argb(0.42f * (1f - RgbPatterns.smooth(0.35f, 0.6f, d)), 0)
        }
        RgbPattern.SLATS -> stripeTile(SLAT_TILE) { t ->
            when {
                t < 0.07f -> argb(0.85f * (1f - RgbPatterns.smooth(0.03f, 0.07f, t)) + 0.3f, 0)
                t < 0.2f -> argb(0.28f * (1f - RgbPatterns.smooth(0.07f, 0.2f, t)), 0xFFFFFF)
                else -> argb(0.5f * RgbPatterns.smooth(0.2f, 1f, t), 0)
            }
        }
        RgbPattern.TOPO -> contourTile()
        RgbPattern.DIAMONDS -> diamondTile()
        else -> null
    }

    /**
     * The drift of every tile repeats after this many cycles of the phase (all drift speeds below
     * times 200 are whole numbers), so the engine may take whole multiples of it off its phase.
     */
    const val DRIFT_PERIOD = 200.0

    /**
     * Matrix (android.graphics.Matrix value order, 9 values) that maps tile pixels to view pixels
     * for a [viewWidth] x [viewHeight] view at [phase] (cycles; the drift wraps with the tile).
     */
    fun transform(pattern: RgbPattern, viewWidth: Float, viewHeight: Float, phase: Double, reverse: Boolean): FloatArray =
        transform(pattern, viewWidth, viewHeight, phase, reverse, FloatArray(9))

    /** [transform] into [out] (9 values), allocation-free for the per-frame path; returns [out]. */
    fun transform(pattern: RgbPattern, viewWidth: Float, viewHeight: Float, phase: Double, reverse: Boolean, out: FloatArray): FloatArray {
        val w = if (viewWidth.isFinite() && viewWidth > 1f) viewWidth else 1f
        val dir = if (reverse) -1.0 else 1.0
        // tile pixel size on screen, rotation in degrees, drift in tile pixels along x and y
        val scale: Float
        val degrees: Float
        var dx = 0f
        var dy = 0f
        when (pattern) {
            RgbPattern.LED -> { scale = w / 34f / LED_TILE; degrees = 0f }
            RgbPattern.HATCH -> { scale = max(4f, w / 165f) / HATCH_TILE; degrees = -123f; dx = drift(phase, 0.25, dir) * HATCH_TILE }
            RgbPattern.SLATS -> { scale = w / 13f / SLAT_TILE; degrees = -125f; dx = drift(phase, 0.2, dir) * SLAT_TILE }
            RgbPattern.TOPO -> { scale = w * 0.8f / BIG_TILE; degrees = 0f; dx = drift(phase, 0.035, dir) * BIG_TILE; dy = drift(phase, 0.025, dir) * BIG_TILE }
            RgbPattern.DIAMONDS -> { scale = w * 0.7f / BIG_TILE; degrees = 0f; dx = drift(phase, 0.03, dir) * BIG_TILE; dy = -drift(phase, 0.03, dir) * BIG_TILE }
            else -> { scale = 1f; degrees = 0f }
        }
        val rad = Math.toRadians(degrees.toDouble())
        val a = (scale * cos(rad)).toFloat()
        val b = (-scale * sin(rad)).toFloat()
        val d = (scale * sin(rad)).toFloat()
        val e = (scale * cos(rad)).toFloat()
        out[0] = a; out[1] = b; out[2] = a * -dx + b * -dy
        out[3] = d; out[4] = e; out[5] = d * -dx + e * -dy
        out[6] = 0f; out[7] = 0f; out[8] = 1f
        return out
    }

    private fun drift(phase: Double, speed: Double, dir: Double): Float {
        val v = phase * speed * dir
        return (v - floor(v)).toFloat()
    }

    private fun argb(alpha: Float, rgb: Int) = ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    private inline fun stripeTile(size: Int, profile: (Float) -> Int): RgbTile =
        RgbTile(size, 1, IntArray(size) { profile((it + 0.5f) / size) })

    /** Dark grey panel with one round hole: the light shows through as an LED. */
    private fun ledTile(): RgbTile {
        val n = LED_TILE
        val radius = n * 0.39f
        val c = n / 2f
        return RgbTile(n, n, IntArray(n * n) { i ->
            val dx = i % n + 0.5f - c
            val dy = i / n + 0.5f - c
            val dist = sqrt(dx * dx + dy * dy)
            argb((dist - radius + 0.5f).coerceIn(0f, 1f) * 0.92f, 0x2E2E2E)
        })
    }

    /** Height lines of a periodic, gently rolling hill field, every fourth one dashed. */
    private fun contourTile(): RgbTile {
        val n = BIG_TILE
        val levels = 16f
        // two smooth octaves only: large hills like a map, no small bumps
        fun h(px: Float, py: Float) =
            0.75f * RgbNoise.noise(px / n * 4f, py / n * 4f, 4) + 0.25f * RgbNoise.noise(px / n * 8f + 0.5f, py / n * 8f + 0.5f, 8)
        val pixels = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val px = x + 0.5f
            val py = y + 0.5f
            val g = h(px, py) * levels
            val gx = (h(px + 0.5f, py) - h(px - 0.5f, py)) * levels
            val gy = (h(px, py + 0.5f) - h(px, py - 0.5f)) * levels
            val grad = max(sqrt(gx * gx + gy * gy), 1e-4f)
            val nearest = floor(g + 0.5f)
            val dist = abs(g - nearest) / grad // pixels to the nearest line
            var alpha = (1.1f - dist).coerceIn(0f, 1f) * 0.6f
            if (Math.floorMod(nearest.toInt(), 4) == 0) {
                val dash = (px + py) * 46f / (2f * n)
                if (dash - floor(dash) > 0.55f) alpha = 0f else alpha *= 0.8f
            }
            pixels[y * n + x] = argb(alpha, 0xFFFFFF)
        }
        return RgbTile(n, n, pixels)
    }

    /** Glowing outlines of squares standing on a corner, some doubled, plus a few long light streaks. */
    private fun diamondTile(): RgbTile {
        val n = BIG_TILE
        // centre x, centre y, half diagonal, doubled
        val shapes = arrayOf(
            floatArrayOf(0.22f, 0.28f, 0.17f, 1f), floatArrayOf(0.66f, 0.18f, 0.12f, 0f),
            floatArrayOf(0.55f, 0.62f, 0.2f, 1f), floatArrayOf(0.12f, 0.78f, 0.1f, 0f),
            floatArrayOf(0.88f, 0.55f, 0.09f, 1f), floatArrayOf(0.4f, 0.95f, 0.07f, 0f),
        )
        val pixels = IntArray(n * n)
        val sqrt2 = sqrt(2f)
        for (y in 0 until n) for (x in 0 until n) {
            val fx = (x + 0.5f) / n
            val fy = (y + 0.5f) / n
            var best = Float.MAX_VALUE
            for (s in shapes) {
                var dx = abs(fx - s[0]); if (dx > 0.5f) dx = 1f - dx
                var dy = abs(fy - s[1]); if (dy > 0.5f) dy = 1f - dy
                val l1 = dx + dy
                best = min(best, abs(l1 - s[2]) / sqrt2)
                if (s[3] > 0f) best = min(best, abs(l1 - s[2] - 0.035f) / sqrt2)
            }
            val dPx = best * n
            val core = (1.4f - dPx).coerceIn(0f, 1f)
            val glow = exp(-dPx / 5f)
            val streak = 0.5f + 0.5f * cos(TWO_PI * (fx + fy) * 3f)
            val alpha = max(core * 0.8f, glow * 0.38f) + 0.07f * RgbPatterns.smooth(0.92f, 1f, streak)
            pixels[y * n + x] = argb(alpha, 0xFFFFFF)
        }
        return RgbTile(n, n, pixels)
    }
}
