// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.charart

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A gray image of the character art window (stage 53): 0 is paper, 1 is full ink, row by row.
 * A photo's darkness and the drawing's ink both are rasters; nothing here knows Android.
 */
class Raster(val width: Int, val height: Int, val values: FloatArray = FloatArray(width * height)) {
    init { require(width >= 0 && height >= 0 && values.size == width * height) }

    operator fun get(x: Int, y: Int): Float = if (x in 0 until width && y in 0 until height) values[y * width + x] else 0f
    operator fun set(x: Int, y: Int, value: Float) { if (x in 0 until width && y in 0 until height) values[y * width + x] = value }

    /** Contrast around middle gray: 1 keeps the image, 2 doubles every distance from 0.5, 0.5 halves it. */
    fun withContrast(contrast: Float): Raster =
        Raster(width, height, FloatArray(values.size) { ((values[it] - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f) })

    fun inverted(): Raster = Raster(width, height, FloatArray(values.size) { 1f - values[it] })

    /**
     * The part [x0]..[x1], [y0]..[y1] (in this raster's pixels, fractions allowed) as [w]×[h] pixels.
     * [Pool.AVERAGE] averages what each target pixel covers (photos), [Pool.MAX] keeps the strongest
     * value (drawn lines stay visible when made smaller); both sample each target pixel several times.
     */
    fun resampled(x0: Double, y0: Double, x1: Double, y1: Double, w: Int, h: Int, pool: Pool): Raster {
        val out = Raster(w, h)
        if (w <= 0 || h <= 0 || width == 0 || height == 0) return out
        val stepX = (x1 - x0) / w
        val stepY = (y1 - y0) / h
        val samplesX = ceil(stepX).toInt().coerceIn(1, 7) + 1
        val samplesY = ceil(stepY).toInt().coerceIn(1, 7) + 1
        for (ty in 0 until h) for (tx in 0 until w) {
            var sum = 0f
            var best = 0f
            for (sy in 0 until samplesY) for (sx in 0 until samplesX) {
                val px = (x0 + (tx + (sx + 0.5) / samplesX) * stepX).toInt()
                val py = (y0 + (ty + (sy + 0.5) / samplesY) * stepY).toInt()
                val value = this[px, py]
                sum += value
                if (value > best) best = value
            }
            out[tx, ty] = if (pool == Pool.MAX) best else sum / (samplesX * samplesY)
        }
        return out
    }

    enum class Pool { AVERAGE, MAX }

    /** Black and white after [threshold] (0.05–0.95) with [dither]; values at or above the threshold are ink. */
    fun toBits(threshold: Float, dither: Dither): Bits {
        val limit = threshold.coerceIn(0.05f, 0.95f)
        val bits = Bits(width, height)
        when (dither) {
            Dither.NONE -> for (y in 0 until height) for (x in 0 until width) bits[x, y] = this[x, y] >= limit
            Dither.ORDERED -> for (y in 0 until height) for (x in 0 until width) {
                // the threshold moves the whole matrix: 0.5 keeps it as it is
                val level = (BAYER[(y % 4) * 4 + x % 4] + 0.5f) / 16f
                bits[x, y] = this[x, y] - limit + 0.5f >= level
            }
            Dither.FLOYD_STEINBERG -> {
                val work = values.copyOf()
                for (y in 0 until height) for (x in 0 until width) {
                    val index = y * width + x
                    val ink = work[index] >= limit
                    bits[x, y] = ink
                    val error = work[index] - if (ink) 1f else 0f
                    fun spread(dx: Int, dy: Int, share: Float) {
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until width && ny < height) work[ny * width + nx] += error * share
                    }
                    spread(1, 0, 7f / 16); spread(-1, 1, 3f / 16); spread(0, 1, 5f / 16); spread(1, 1, 1f / 16)
                }
            }
        }
        return bits
    }

    companion object {
        private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

        /** From rows of text: [ink] is 1, anything else 0 (tests and fonts). */
        fun parse(rows: List<String>, ink: Char = '#'): Raster {
            val width = rows.maxOfOrNull { it.length } ?: 0
            val raster = Raster(width, rows.size)
            rows.forEachIndexed { y, row -> row.forEachIndexed { x, char -> if (char == ink) raster[x, y] = 1f } }
            return raster
        }
    }
}

/** How a gray photo becomes black and white. */
enum class Dither { NONE, ORDERED, FLOYD_STEINBERG }

/** Black and white pixels; outside the bounds everything is paper. */
class Bits(val width: Int, val height: Int, private val data: BooleanArray = BooleanArray(width * height)) {
    init { require(width >= 0 && height >= 0 && data.size == width * height) }

    operator fun get(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && data[y * width + x]
    operator fun set(x: Int, y: Int, ink: Boolean) { if (x in 0 until width && y in 0 until height) data[y * width + x] = ink }

    val isEmpty: Boolean get() = data.none { it }

    /** Only the edge: ink with paper (or the border of the image) on one of its four sides. */
    fun outline(): Bits {
        val out = Bits(width, height)
        for (y in 0 until height) for (x in 0 until width)
            out[x, y] = this[x, y] && (!this[x - 1, y] || !this[x + 1, y] || !this[x, y - 1] || !this[x, y + 1])
        return out
    }

    fun inverted(): Bits = Bits(width, height, BooleanArray(data.size) { !data[it] })

    fun mirrored(): Bits {
        val out = Bits(width, height)
        for (y in 0 until height) for (x in 0 until width) out[width - 1 - x, y] = this[x, y]
        return out
    }

    fun or(other: Bits): Bits {
        require(other.width == width && other.height == height)
        return Bits(width, height, BooleanArray(data.size) { data[it] || other.data[it] })
    }

    /** Each pixel as [kx]×[ky] pixels (big letters keep sharp edges). */
    fun scaled(kx: Int, ky: Int): Bits {
        val out = Bits(width * kx, height * ky)
        for (y in 0 until out.height) for (x in 0 until out.width) out[x, y] = this[x / kx, y / ky]
        return out
    }

    /** This image at [x], [y] of a new [w]×[h] image (paper around it). */
    fun placed(w: Int, h: Int, x: Int, y: Int): Bits {
        val out = Bits(w, h)
        for (sy in 0 until height) for (sx in 0 until width) if (this[sx, sy]) out[x + sx, y + sy] = true
        return out
    }

    /** As rows of text (tests and debugging), [ink] and [paper]. */
    fun rows(ink: Char = '#', paper: Char = '.'): List<String> =
        (0 until height).map { y -> String(CharArray(width) { x -> if (this[x, y]) ink else paper }) }

    companion object {
        fun parse(rows: List<String>, ink: Char = '#'): Bits {
            val width = rows.maxOfOrNull { it.length } ?: 0
            val bits = Bits(width, rows.size)
            rows.forEachIndexed { y, row -> row.forEachIndexed { x, char -> if (char == ink) bits[x, y] = true } }
            return bits
        }

        /** Rows of [rows] stacked with [gap] empty rows between them, each aligned left or centred. */
        fun stack(rows: List<Bits>, gap: Int, center: Boolean): Bits {
            if (rows.isEmpty()) return Bits(0, 0)
            val width = rows.maxOf { it.width }
            val height = rows.sumOf { it.height } + gap * (rows.size - 1)
            val out = Bits(width, height)
            var top = 0
            for (row in rows) {
                val left = if (center) (width - row.width) / 2 else 0
                for (y in 0 until row.height) for (x in 0 until row.width) if (row[x, y]) out[left + x, top + y] = true
                top += row.height + gap
            }
            return out
        }
    }
}

/** Rounds half up for non-negative numbers, like the widths of the window. */
internal fun roundHalfUp(value: Double): Int = (value + 1e-9).roundToInt()

internal fun clampInt(value: Int, low: Int, high: Int) = max(low, min(high, value))
