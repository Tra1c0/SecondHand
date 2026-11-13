// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The built-in drawn symbols (stage 27): every symbol with one or more drawings ([Symbol.variants]),
 * each a list of strokes in a 100 × 100 box (y downward). Stroke order and direction do not
 * matter to the recognizer, so a variant only stands for a different shape (head size, proportions).
 * The how-to texts live in docs/ANLEITUNG.md; the settings page draws [Symbol.variants] first one.
 */
object InkSymbols {
    class Symbol(val text: String, val variants: List<List<InkStroke>>)

    // --- drawing helpers (box 0..100, y downward) ---

    private fun line(x1: Number, y1: Number, x2: Number, y2: Number) =
        InkStroke(floatArrayOf(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()))

    private fun poly(vararg xy: Number) = InkStroke(FloatArray(xy.size) { xy[it].toFloat() })

    private fun dot(x: Number, y: Number) = InkStroke(floatArrayOf(x.toFloat(), y.toFloat()))

    /** An elliptic arc from [from] to [to] degrees (0 = right, 90 = down), in steps of about 10°. */
    private fun arcPoints(cx: Number, cy: Number, rx: Number, ry: Number, from: Number, to: Number): FloatArray {
        val a = from.toDouble(); val b = to.toDouble()
        val steps = maxOf(2, (kotlin.math.abs(b - a) / 10).toInt())
        return FloatArray(2 * (steps + 1)) { k ->
            val angle = (a + (b - a) * (k / 2) / steps) * PI / 180
            if (k % 2 == 0) (cx.toDouble() + rx.toDouble() * cos(angle)).toFloat()
            else (cy.toDouble() + ry.toDouble() * sin(angle)).toFloat()
        }
    }

    private fun arc(cx: Number, cy: Number, rx: Number, ry: Number, from: Number, to: Number) =
        InkStroke(arcPoints(cx, cy, rx, ry, from, to))

    /** One stroke made of several pieces drawn without lifting the finger. */
    private fun joined(vararg parts: FloatArray) = InkStroke(parts.reduce { acc, part -> acc + part })

    private fun pts(vararg xy: Number) = FloatArray(xy.size) { xy[it].toFloat() }

    /** A curve y = f(x) sampled along x. */
    private fun curve(from: Number, to: Number, f: (Double) -> Double): InkStroke {
        val a = from.toDouble(); val b = to.toDouble()
        val steps = 24
        return InkStroke(FloatArray(2 * (steps + 1)) { k ->
            val x = a + (b - a) * (k / 2) / steps
            if (k % 2 == 0) x.toFloat() else f(x).toFloat()
        })
    }

    private fun wave(y: Double, amplitude: Double) = curve(0, 100) { y - amplitude * sin(2 * PI * it / 100) }

    /** Mirrors strokes left to right (← from →) or top to bottom. */
    private fun List<InkStroke>.mirrorX() = map { s -> InkStroke(FloatArray(s.coordinates.size) { if (it % 2 == 0) 100 - s.coordinates[it] else s.coordinates[it] }) }
    private fun List<InkStroke>.mirrorY() = map { s -> InkStroke(FloatArray(s.coordinates.size) { if (it % 2 == 1) 100 - s.coordinates[it] else s.coordinates[it] }) }
    /** Squeezes strokes around the middle of the box: a drawing that came out too narrow or too flat. */
    private fun List<InkStroke>.squeeze(fx: Double, fy: Double) = map { s -> InkStroke(FloatArray(s.coordinates.size) {
        (50 + (s.coordinates[it] - 50) * (if (it % 2 == 0) fx else fy)).toFloat() }) }
    /** Adds a narrower and a flatter copy of every drawing, for symbols whose parts sit far apart (%, @, #). */
    private fun List<List<InkStroke>>.withSqueezed() = flatMap { listOf(it, it.squeeze(0.8, 1.0), it.squeeze(1.0, 0.8)) }
    /** Swaps x and y: a horizontal drawing becomes vertical (→ becomes ↓). */
    private fun List<InkStroke>.transpose() = map { s -> InkStroke(FloatArray(s.coordinates.size) { if (it % 2 == 0) s.coordinates[it + 1] else s.coordinates[it - 1] }) }

    // --- shapes ---

    private val rightArrows = listOf(
        listOf(line(0, 50, 100, 50), poly(72, 26, 100, 50, 72, 74)),
        listOf(line(0, 50, 100, 50), poly(84, 38, 100, 50, 84, 62)),
    )
    private val upRightArrows = listOf(
        listOf(line(0, 100, 100, 0), poly(50, 0, 100, 0, 100, 50)),
        listOf(line(0, 100, 100, 0), poly(66, 0, 100, 0, 100, 34)),
    )
    private val bothArrows = listOf(
        listOf(line(0, 50, 100, 50), poly(76, 30, 100, 50, 76, 70), poly(24, 30, 0, 50, 24, 70)),
    )
    private val doubleArrows = listOf(
        listOf(line(0, 36, 80, 36), line(0, 64, 80, 64), poly(66, 12, 100, 50, 66, 88)),
    )
    private val equals = listOf(
        listOf(line(0, 34, 100, 34), line(0, 66, 100, 66)),
        listOf(line(0, 40, 100, 40), line(0, 60, 100, 60)),
    )
    private val notEquals = listOf(
        listOf(line(0, 34, 100, 34), line(0, 66, 100, 66), line(30, 100, 70, 0)),
        listOf(line(0, 38, 100, 38), line(0, 62, 100, 62), line(40, 95, 60, 5)),
    )
    private val lessEqual = listOf(
        listOf(poly(90, 0, 10, 38, 90, 76), line(10, 100, 90, 100)),
        listOf(poly(90, 0, 10, 38, 90, 76), line(10, 90, 90, 100)),
    )
    private val plus = listOf(listOf(line(50, 0, 50, 100), line(0, 50, 100, 50)))
    private val plusMinus = listOf(
        listOf(line(50, 0, 50, 70), line(10, 35, 90, 35), line(10, 100, 90, 100)),
    )
    private val times = listOf(
        listOf(line(0, 0, 100, 100), line(100, 0, 0, 100)),
    )
    private val divide = listOf(
        listOf(line(0, 50, 100, 50), dot(50, 12), dot(50, 88)),
    )
    private val infinity = listOf(
        // lemniscate of Gerono, one stroke
        listOf(InkStroke(FloatArray(2 * 37) { k ->
            val t = 2 * PI * (k / 2) / 36
            if (k % 2 == 0) (50 + 50 * cos(t)).toFloat() else (25 + 25 * sin(2 * t)).toFloat()
        })),
    )
    private val root = listOf(
        listOf(poly(0, 60, 18, 50, 40, 100, 66, 0, 100, 0)),
        listOf(poly(5, 65, 40, 100, 66, 0, 100, 0)),
    )
    private val pi = listOf(
        listOf(line(0, 12, 100, 12), line(32, 12, 26, 100), poly(68, 12, 70, 90, 84, 100)),
        listOf(line(0, 12, 100, 12), line(32, 12, 32, 100), line(68, 12, 68, 100)),
    )
    private val degree = listOf(listOf(arc(50, 50, 50, 50, 0, 360)))
    private val check = listOf(
        listOf(poly(0, 55, 35, 100, 100, 0)),
        listOf(poly(0, 70, 25, 100, 100, 0)),
    )
    private val heart = listOf(
        listOf(InkStroke(FloatArray(2 * 41) { k ->
            val t = 2 * PI * (k / 2) / 40
            val x = 16 * sin(t).let { it * it * it }
            val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
            if (k % 2 == 0) (50 + x * 3).toFloat() else (45 - y * 3).toFloat()
        })),
    )
    private val star = listOf(
        listOf(InkStroke(FloatArray(2 * 6) { k ->
            val angle = (-90 + 144 * (k / 2)) * PI / 180
            if (k % 2 == 0) (50 + 50 * cos(angle)).toFloat() else (50 + 50 * sin(angle)).toFloat()
        })),
    )
    private val euro = listOf(
        listOf(arc(60, 50, 45, 50, 320, 40), line(0, 40, 66, 40), line(0, 60, 66, 60)),
        listOf(arc(60, 50, 45, 50, 315, 45), line(5, 38, 70, 38), line(5, 58, 60, 58)),
    )
    private val sShape = joined(arcPoints(50, 28, 34, 22, 335, 90), arcPoints(50, 72, 34, 22, 270, 515))
    private val dollar = listOf(
        listOf(sShape, line(50, 0, 50, 100)),
        listOf(sShape, line(50, 0, 50, 22), line(50, 78, 50, 100)),
    )
    private val section = listOf(
        listOf(joined(arcPoints(50, 18, 25, 16, 330, 90), arcPoints(50, 50, 25, 16, 270, 450)),
            joined(arcPoints(50, 50, 25, 16, 90, 270), arcPoints(50, 82, 25, 16, 270, 510))),
        listOf(joined(arcPoints(50, 18, 25, 16, 330, 90), arcPoints(50, 50, 25, 16, 270, 450),
            arcPoints(50, 82, 25, 16, 270, 510))),
    )
    private val ampersand = listOf(
        listOf(poly(100, 100, 30, 35, 24, 15, 40, 0, 56, 10, 50, 30, 10, 65, 14, 90, 40, 100, 70, 86, 96, 55)),
    )
    private val at = listOf(
        listOf(joined(arcPoints(50, 50, 18, 18, 0, 360), pts(68, 64, 80, 66), arcPoints(50, 50, 48, 48, 20, -300))),
    )
    private val hash = listOf(
        listOf(line(38, 0, 26, 100), line(76, 0, 64, 100), line(0, 32, 100, 32), line(0, 68, 100, 68)),
        listOf(line(32, 0, 32, 100), line(68, 0, 68, 100), line(0, 32, 100, 32), line(0, 68, 100, 68)),
    )
    private val percent = listOf(
        listOf(arc(20, 20, 17, 17, 0, 360), line(85, 0, 15, 100), arc(80, 80, 17, 17, 0, 360)),
        listOf(arc(22, 22, 22, 22, 0, 360), line(90, 0, 10, 100), arc(78, 78, 22, 22, 0, 360)),
        listOf(arc(15, 15, 12, 12, 0, 360), line(80, 5, 20, 95), arc(85, 85, 12, 12, 0, 360)),
    )
    private val leftParen = listOf(listOf(arc(75, 50, 40, 50, 240, 120)), listOf(arc(70, 50, 30, 50, 250, 110)))
    private val leftBracket = listOf(listOf(poly(70, 0, 30, 0, 30, 100, 70, 100)))
    private val leftBrace = listOf(listOf(poly(72, 0, 54, 4, 50, 18, 50, 40, 30, 50, 50, 60, 50, 82, 54, 96, 72, 100)))

    val all: List<Symbol> = listOf(
        Symbol("→", rightArrows),
        Symbol("←", rightArrows.map { it.mirrorX() }),
        Symbol("↓", rightArrows.map { it.transpose() }),
        Symbol("↑", rightArrows.map { it.transpose().mirrorY() }),
        Symbol("↗", upRightArrows),
        Symbol("↘", upRightArrows.map { it.mirrorY() }),
        Symbol("↔", bothArrows),
        Symbol("⇒", doubleArrows),
        Symbol("=", equals),
        Symbol("≠", notEquals),
        Symbol("≈", listOf(listOf(wave(32.0, 10.0), wave(68.0, 10.0)))),
        Symbol("~", listOf(listOf(wave(50.0, 16.0)))),
        Symbol("≤", lessEqual),
        Symbol("≥", lessEqual.map { it.mirrorX() }),
        Symbol("+", plus),
        Symbol("±", plusMinus),
        Symbol("×", times),
        Symbol("÷", divide),
        Symbol("–", listOf(listOf(line(0, 50, 100, 50)))),
        Symbol("|", listOf(listOf(line(50, 0, 50, 100)))),
        Symbol("∞", infinity),
        Symbol("√", root),
        Symbol("π", pi),
        Symbol("°", degree),
        Symbol("✓", check),
        Symbol("♥", heart),
        Symbol("★", star),
        Symbol("€", euro),
        Symbol("$", dollar),
        Symbol("§", section),
        Symbol("&", ampersand),
        Symbol("@", at.withSqueezed()),
        Symbol("#", hash.withSqueezed()),
        Symbol("%", percent.withSqueezed()),
        Symbol("(", leftParen),
        Symbol(")", leftParen.map { it.mirrorX() }),
        Symbol("[", leftBracket),
        Symbol("]", leftBracket.map { it.mirrorX() }),
        Symbol("{", leftBrace),
        Symbol("}", leftBrace.map { it.mirrorX() }),
    )

    /** One template per drawing of every built-in symbol. */
    val templates: List<InkTemplate> by lazy {
        all.flatMap { symbol -> symbol.variants.mapNotNull { strokes -> InkCloud.of(strokes)?.let { InkTemplate(symbol.text, it) } } }
    }
}
