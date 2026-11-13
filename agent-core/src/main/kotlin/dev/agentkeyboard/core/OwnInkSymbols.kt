// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.roundToInt

/**
 * Own drawn symbols (stage 27): the user draws a symbol [SAMPLES] times in the settings and picks
 * the text it inserts. Only the normalized clouds are stored, never the raw touch points.
 *
 * Storage format (one string, see docs/STORAGE.md): first line `v1`, then one line per symbol:
 * the text percent-encoded, then for every drawing a tab and [InkCloud.SIZE] points `x,y,s`
 * separated by `;` (x and y in thousandths of the normalized box, s the stroke number).
 * [parse] never throws: a broken line is skipped, the rest is kept.
 */
object OwnInkSymbols {
    const val SAMPLES = 3
    const val MAX_SYMBOLS = 30
    const val MAX_TEXT_CODE_POINTS = 8
    /** Reading stops here; a longer value was not written by this code. */
    const val MAX_ENCODED_LENGTH = 512 * 1024
    private const val VERSION = "v1"

    class Symbol(val text: String, val samples: List<InkCloud>) {
        fun templates() = samples.map { InkTemplate(text, it, own = true) }
    }

    /** Why a text cannot be an own symbol, or null if it can. */
    enum class TextProblem { EMPTY, TOO_LONG, CONTROL }

    fun textProblem(text: String): TextProblem? = when {
        text.isBlank() -> TextProblem.EMPTY
        text.codePointCount(0, text.length) > MAX_TEXT_CODE_POINTS -> TextProblem.TOO_LONG
        text.any { Character.isISOControl(it) } -> TextProblem.CONTROL
        else -> null
    }

    /** Stage 36: the personal templates use the same format with other limits ([PersonalInkSymbols]). */
    fun encode(symbols: List<Symbol>, maxSymbols: Int = MAX_SYMBOLS, maxSamples: Int = SAMPLES): String = buildString {
        append(VERSION)
        for (symbol in symbols.take(maxSymbols)) {
            if (textProblem(symbol.text) != null || symbol.samples.isEmpty()) continue
            append('\n').append(escape(symbol.text))
            for (cloud in symbol.samples.take(maxSamples)) {
                append('\t')
                for (i in 0 until InkCloud.SIZE) {
                    if (i > 0) append(';')
                    append((cloud.xs[i] * 1000).roundToInt()).append(',')
                        .append((cloud.ys[i] * 1000).roundToInt()).append(',').append(cloud.strokes[i])
                }
            }
        }
    }

    fun parse(encoded: String?, maxSymbols: Int = MAX_SYMBOLS, maxSamples: Int = SAMPLES): List<Symbol> {
        if (encoded == null || encoded.length > MAX_ENCODED_LENGTH) return emptyList()
        val lines = encoded.split('\n')
        if (lines.firstOrNull() != VERSION) return emptyList()
        val out = ArrayList<Symbol>()
        for (line in lines.drop(1)) {
            if (out.size >= maxSymbols) break
            val parts = line.split('\t')
            val text = unescape(parts[0]) ?: continue
            if (textProblem(text) != null) continue
            if (parts.size - 1 > maxSamples) continue // not written by this code
            val clouds = parts.drop(1).mapNotNull(::parseCloud)
            if (clouds.isEmpty() || clouds.size != parts.size - 1) continue // a broken drawing spoils the symbol
            out += Symbol(text, clouds)
        }
        return out
    }

    private fun parseCloud(value: String): InkCloud? {
        val points = value.split(';')
        if (points.size != InkCloud.SIZE) return null
        val xs = FloatArray(InkCloud.SIZE); val ys = FloatArray(InkCloud.SIZE); val ids = IntArray(InkCloud.SIZE)
        for ((i, point) in points.withIndex()) {
            val fields = point.split(',')
            if (fields.size != 3) return null
            val x = fields[0].toIntOrNull() ?: return null
            val y = fields[1].toIntOrNull() ?: return null
            val s = fields[2].toIntOrNull() ?: return null
            // a normalized cloud lies within ±1 of its centroid
            if (x !in -1000..1000 || y !in -1000..1000) return null
            xs[i] = x / 1000f; ys[i] = y / 1000f; ids[i] = s
        }
        return InkCloud.fromPoints(xs, ys, ids)
    }

    private fun escape(text: String) = buildString {
        for (c in text) when (c) {
            '%', '\t', '\n', '\r' -> append('%').append("%02X".format(c.code))
            else -> append(c)
        }
    }

    private fun unescape(text: String): String? {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%') {
                if (i + 3 > text.length) return null
                val code = text.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                out.append(code.toChar()); i += 3
            } else { out.append(c); i++ }
        }
        return out.toString()
    }
}
