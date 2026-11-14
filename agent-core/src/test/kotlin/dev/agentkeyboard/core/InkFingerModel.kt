// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Stage 36: a synthetic finger for the recognition benchmark, harder than the stage-27 perturbation.
 * The shapes are written here on purpose and not taken from [InkSymbols]: every symbol is drawn in
 * the ways a hand plausibly draws it (one or several strokes, head in one piece or as two barbs,
 * proportions drawn at random), then the strokes come in random order and direction, shortened,
 * bent, with a hook where the finger lifts, jitter, uneven sampling, rotation up to ±15° and
 * stretched up to 25 % per axis. Nothing here is measured on a real phone.
 */
object InkFingerModel {
    /** A stroke as points in the 100 × 100 box, y downward. */
    private class P(val x: Double, val y: Double)
    private typealias Path = List<P>

    private fun p(x: Number, y: Number) = P(x.toDouble(), y.toDouble())
    private fun seg(vararg xy: Number): Path = (xy.indices step 2).map { p(xy[it], xy[it + 1]) }
    private fun arc(cx: Double, cy: Double, rx: Double, ry: Double, from: Double, to: Double): Path {
        val steps = maxOf(4, (abs(to - from) / 12).toInt())
        return (0..steps).map { k -> val a = (from + (to - from) * k / steps) * PI / 180; P(cx + rx * cos(a), cy + ry * sin(a)) }
    }

    private fun List<Path>.mapPoints(f: (P) -> P) = map { s -> s.map(f) }
    private fun List<Path>.mirrorX() = mapPoints { P(100 - it.x, it.y) }
    private fun List<Path>.mirrorY() = mapPoints { P(it.x, 100 - it.y) }
    private fun List<Path>.transpose() = mapPoints { P(it.y, it.x) }

    /** An arrow head at ([tx], [ty]) pointing along [angle] (radians), drawn in one of three ways. */
    private fun head(tx: Double, ty: Double, angle: Double, r: Random): List<Path> {
        val len = r.nextDouble(16.0, 34.0)
        val spread = Math.toRadians(r.nextDouble(25.0, 48.0))
        val a1 = angle + PI - spread; val a2 = angle + PI + spread
        val b1 = P(tx + len * cos(a1), ty + len * sin(a1)); val b2 = P(tx + len * r.nextDouble(0.8, 1.2) * cos(a2), ty + len * sin(a2))
        val tip = P(tx, ty)
        return when (r.nextInt(3)) {
            0 -> listOf(listOf(b1, tip, b2)) // "<" in one stroke
            1 -> listOf(listOf(b1, tip), listOf(b2, tip)) // two barbs
            else -> listOf(listOf(b1, tip), listOf(tip, b2))
        }
    }

    private fun rightArrow(r: Random): List<Path> {
        val y = 50.0
        val shaft = seg(0, y, 100, y)
        val h = head(100.0, y, 0.0, r)
        // sometimes in one stroke: the shaft, then one barb and back, then the other
        if (r.nextInt(4) == 0 && h.size == 1) return listOf(shaft + listOf(h[0][0], h[0][1], h[0][2]))
        return listOf(shaft) + h
    }

    private fun upRightArrow(r: Random): List<Path> {
        val shaft = seg(0, 100, 100, 0)
        val len = r.nextDouble(25.0, 50.0)
        return if (r.nextBoolean()) listOf(shaft, seg(100 - len, 0, 100, 0, 100, len))
        else listOf(shaft, seg(100 - len, 0, 100, 0), seg(100, len, 100, 0))
    }

    private fun bothArrow(r: Random): List<Path> =
        listOf(seg(0, 50, 100, 50)) + head(100.0, 50.0, 0.0, r) + head(0.0, 50.0, PI, r)

    private fun doubleArrow(r: Random): List<Path> {
        val gap = r.nextDouble(12.0, 20.0)
        // bars from well short of the tip to under the head, a head from small to large
        val end = r.nextDouble(65.0, 98.0)
        val tipX = 100.0
        val reach = r.nextDouble(1.5, 3.5) * gap
        val back = r.nextDouble(20.0, 50.0)
        val bars = listOf(seg(0, 50 - gap, end, 50 - gap), seg(0, 50 + gap, end, 50 + gap))
        val headStrokes = if (r.nextBoolean()) listOf(seg(tipX - back, 50 - reach, tipX, 50, tipX - back, 50 + reach))
            else listOf(seg(tipX - back, 50 - reach, tipX, 50), seg(tipX - back, 50 + reach, tipX, 50))
        return bars + headStrokes
    }

    private fun bars(r: Random): List<Path> {
        val gap = r.nextDouble(12.0, 22.0)
        val a = r.nextDouble(0.0, 8.0); val b = r.nextDouble(92.0, 100.0)
        return listOf(seg(a, 50 - gap, b, 50 - gap), seg(r.nextDouble(0.0, 8.0), 50 + gap, r.nextDouble(92.0, 100.0), 50 + gap))
    }

    private fun notEquals(r: Random): List<Path> {
        val slant = Math.toRadians(r.nextDouble(55.0, 80.0))
        val half = r.nextDouble(35.0, 50.0)
        val cx = r.nextDouble(42.0, 58.0)
        return bars(r) + listOf(seg(cx - half * cos(slant), 50 + half * sin(slant), cx + half * cos(slant), 50 - half * sin(slant)))
    }

    private fun wave(y: Double, amp: Double, r: Random): Path {
        val cycles = r.nextDouble(0.8, 1.2)
        return (0..20).map { k -> val x = 5.0 * k; P(x, y - amp * sin(2 * PI * cycles * x / 100)) }
    }

    private fun lessEqual(r: Random): List<Path> {
        val gap = r.nextDouble(12.0, 25.0)
        return listOf(seg(90, 0, 10, 35, 90, 70), seg(10, 70 + gap, 90, 70 + gap))
    }

    private fun circle(cx: Double, cy: Double, rx: Double, ry: Double, r: Random): Path {
        val start = r.nextDouble(-120.0, 0.0)
        return arc(cx, cy, rx, ry, start, start + r.nextDouble(330.0, 375.0))
    }

    private val shapes: Map<String, (Random) -> List<Path>> = linkedMapOf(
        "→" to ::rightArrow,
        "←" to { r -> rightArrow(r).mirrorX() },
        "↓" to { r -> rightArrow(r).transpose() },
        "↑" to { r -> rightArrow(r).transpose().mirrorY() },
        "↗" to ::upRightArrow,
        "↘" to { r -> upRightArrow(r).mirrorY() },
        "↔" to ::bothArrow,
        "⇒" to ::doubleArrow,
        "=" to ::bars,
        "≠" to ::notEquals,
        "≈" to { r -> val gap = r.nextDouble(14.0, 22.0); listOf(wave(50 - gap, r.nextDouble(7.0, 12.0), r), wave(50 + gap, r.nextDouble(7.0, 12.0), r)) },
        "~" to { r -> listOf(wave(50.0, r.nextDouble(10.0, 18.0), r)) },
        "≤" to ::lessEqual,
        "≥" to { r -> lessEqual(r).mirrorX() },
        "+" to { r -> listOf(seg(r.nextDouble(45.0, 55.0), 0, r.nextDouble(45.0, 55.0), 100), seg(0, r.nextDouble(45.0, 55.0), 100, r.nextDouble(45.0, 55.0))) },
        "±" to { r -> listOf(seg(50, 0, 50, 70), seg(10, 35, 90, 35), seg(10, r.nextDouble(88.0, 100.0), 90, r.nextDouble(88.0, 100.0))) },
        "×" to { r -> listOf(seg(0, 0, 100, 100), seg(100, 0, 0, 100)) },
        "÷" to { r -> listOf(seg(0, 50, 100, 50), seg(r.nextDouble(42.0, 58.0), r.nextDouble(10.0, 25.0)), seg(r.nextDouble(42.0, 58.0), r.nextDouble(75.0, 90.0))) },
        "–" to { _ -> listOf(seg(0, 50, 100, 50)) },
        "|" to { _ -> listOf(seg(50, 0, 50, 100)) },
        "∞" to { r -> val w = r.nextDouble(0.8, 1.0)
            listOf((0..36).map { k -> val t = 2 * PI * k / 36; P(50 + 50 * w * sin(t), 25 + 25 * sin(2 * t)) }) },
        "√" to { r -> listOf(seg(r.nextDouble(0.0, 8.0), 55, 18, 48, 38, 100, 64, 0, 100, 0)) },
        "π" to { r -> listOf(seg(0, 12, 100, 12), seg(32, 12, r.nextDouble(24.0, 32.0), 100), seg(68, 12, r.nextDouble(68.0, 76.0), 100)) },
        "°" to { r -> listOf(circle(50.0, 50.0, 50.0, r.nextDouble(42.0, 50.0), r)) },
        "✓" to { r -> listOf(seg(0, r.nextDouble(45.0, 70.0), r.nextDouble(25.0, 38.0), 100, 100, 0)) },
        "♥" to { r ->
            val heart = (0..40).map { k -> val t = 2 * PI * k / 40
                P(50 + 48 * sin(t).let { it * it * it }, 45 - 3 * (13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))) }
            if (r.nextBoolean()) listOf(heart) else listOf(heart.subList(0, 21), heart.subList(20, 41).reversed())
        },
        "★" to { _ -> listOf((0..5).map { k -> val a = (-90 + 144 * k) * PI / 180; P(50 + 50 * cos(a), 50 + 50 * sin(a)) }) },
        "€" to { r -> val c = arc(60.0, 50.0, 45.0, 50.0, r.nextDouble(310.0, 325.0), r.nextDouble(35.0, 50.0))
            if (r.nextInt(3) == 0) listOf(c, seg(0, 50, 70, 50)) else listOf(c, seg(0, 40, 66, 40), seg(0, 60, 66, 60)) },
        "$" to { r -> val s = arc(50.0, 28.0, 32.0, 22.0, -30.0, -270.0) + arc(50.0, 72.0, 32.0, 22.0, 270.0, 510.0)
            listOf(s, seg(50, r.nextDouble(-5.0, 5.0), 50, r.nextDouble(95.0, 105.0))) },
        "§" to { _ -> listOf(arc(50.0, 18.0, 25.0, 16.0, -30.0, -270.0) + arc(50.0, 50.0, 25.0, 16.0, 270.0, 450.0) + arc(50.0, 82.0, 25.0, 16.0, 270.0, 510.0)) },
        "&" to { _ -> listOf(seg(100, 100, 30, 35, 24, 15, 40, 0, 56, 10, 50, 30, 10, 65, 14, 90, 40, 100, 70, 86, 96, 55)) },
        "@" to { r -> listOf(arc(50.0, 50.0, 18.0, 18.0, 0.0, 360.0) + seg(68, 64, 80, 66) + arc(50.0, 50.0, 48.0, 48.0, 20.0, r.nextDouble(-320.0, -280.0))) },
        "#" to { r -> val s = r.nextDouble(0.0, 12.0)
            listOf(seg(32 + s, 0, 32 - s, 100), seg(68 + s, 0, 68 - s, 100), seg(0, 32, 100, 32), seg(0, 68, 100, 68)) },
        "%" to { r -> val rr = r.nextDouble(12.0, 20.0)
            listOf(circle(rr, rr, rr, rr, r), seg(85, 0, 15, 100), circle(100 - rr, 100 - rr, rr, rr, r)) },
        // stage 43: symbols with dots, the dots placed by hand
        "‰" to { r -> val rr = r.nextDouble(10.0, 15.0)
            listOf(circle(rr + 2, rr + 2, rr, rr, r), seg(68, 0, 18, 100), circle(52.0, 100 - rr, rr, rr, r), circle(100 - rr, 100 - rr, rr, rr, r)) },
        "!" to { r -> exclamation(r) },
        "¡" to { r -> exclamation(r).mirrorY() },
        "?" to { r -> question(r) },
        "¿" to { r -> question(r).mirrorX().mirrorY() },
        "(" to { r -> listOf(arc(75.0, 50.0, r.nextDouble(30.0, 45.0), 50.0, 245.0, 115.0)) },
        ")" to { r -> listOf(arc(25.0, 50.0, r.nextDouble(30.0, 45.0), 50.0, -65.0, 65.0)) },
        "[" to { _ -> listOf(seg(70, 0, 30, 0, 30, 100, 70, 100)) },
        "]" to { _ -> listOf(seg(30, 0, 70, 0, 70, 100, 30, 100)) },
        "{" to { _ -> listOf(seg(72, 0, 54, 4, 50, 18, 50, 40, 30, 50, 50, 60, 50, 82, 54, 96, 72, 100)) },
        "}" to { _ -> listOf(seg(28, 0, 46, 4, 50, 18, 50, 40, 70, 50, 50, 60, 50, 82, 46, 96, 28, 100)) },
    )

    val symbols: Set<String> get() = shapes.keys

    // --- stage 43: symbols with dots, drawn with the dots tapped ---

    /** A dot as a finger taps it, or (with [circles]) now and then a tiny circle of up to 3 units. */
    private fun dotOrCircle(cx: Double, cy: Double, r: Random, circles: Boolean): Path =
        if (circles && r.nextBoolean()) circle(cx, cy, r.nextDouble(1.0, 3.0), r.nextDouble(1.0, 3.0), r) else seg(cx, cy)

    private fun exclamation(r: Random): List<Path> {
        val x = r.nextDouble(45.0, 55.0)
        return listOf(seg(50, 0, x, r.nextDouble(60.0, 82.0)), seg(x + r.nextDouble(-6.0, 6.0), r.nextDouble(90.0, 100.0)))
    }

    private fun question(r: Random): List<Path> {
        val hook = arc(50.0, 27.0, r.nextDouble(24.0, 32.0), r.nextDouble(20.0, 27.0), r.nextDouble(185.0, 210.0), r.nextDouble(390.0, 430.0))
        val x = r.nextDouble(44.0, 56.0)
        return listOf(hook + seg(x, r.nextDouble(56.0, 62.0), x, r.nextDouble(68.0, 80.0)), seg(x + r.nextDouble(-6.0, 6.0), r.nextDouble(90.0, 100.0)))
    }

    private fun percent(r: Random, circles: Boolean): List<Path> = listOf(seg(85, 0, 15, 100),
        dotOrCircle(r.nextDouble(10.0, 26.0), r.nextDouble(10.0, 26.0), r, circles),
        dotOrCircle(r.nextDouble(74.0, 90.0), r.nextDouble(74.0, 90.0), r, circles))

    private fun perMille(r: Random, circles: Boolean): List<Path> = listOf(seg(68, 0, 18, 100),
        dotOrCircle(r.nextDouble(10.0, 24.0), r.nextDouble(10.0, 24.0), r, circles),
        dotOrCircle(r.nextDouble(46.0, 58.0), r.nextDouble(78.0, 90.0), r, circles),
        dotOrCircle(r.nextDouble(78.0, 90.0), r.nextDouble(78.0, 90.0), r, circles))

    private val dotted: Map<String, (Random) -> List<Path>> = linkedMapOf(
        "÷" to shapes.getValue("÷"),
        "%" to { r -> percent(r, false) },
        // the same with a tiny circle instead of a dot now and then (expected: %)
        "%○" to { r -> percent(r, true) },
        "‰" to { r -> perMille(r, false) },
        "!" to ::exclamation,
        "¡" to { r -> exclamation(r).mirrorY() },
        "?" to ::question,
        "¿" to { r -> question(r).mirrorX().mirrorY() },
    )

    /** Stage 43: what [drawDotted] knows; its first character is the symbol ("%○": % with tiny circles). */
    val dottedSymbols: Set<String> get() = dotted.keys

    /**
     * Stage 43: [text] drawn with its dots tapped (% and ‰ now and then with tiny circles instead):
     * the strokes first in random order (a drawing starts with a stroke, a lone tap is a letter), then
     * the dots in random order. Otherwise as noisy as [draw] with `hard`.
     */
    fun drawDotted(text: String, r: Random): List<InkStroke> {
        val paths = dotted.getValue(text)(r)
        val (dots, lines) = paths.partition { path -> path.maxOf { it.x } - path.minOf { it.x } <= 8 && path.maxOf { it.y } - path.minOf { it.y } <= 8 }
        return noisy(lines.shuffled(r) + dots.shuffled(r), r, hard = true, keepOrder = true)
    }

    /** One finger drawing of [text] in screen pixels; the strokes in the order they were drawn. */
    fun draw(text: String, r: Random, hard: Boolean = true): List<InkStroke> = noisy(shapes.getValue(text)(r), r, hard)

    /**
     * ⇒ as the owner may have drawn it when it turned into ≠ (build 14; the drawing itself is not
     * known): the bars run on under a tall, narrow head, so the head crosses the bars like a slash.
     */
    fun ownersDoubleArrow(r: Random): List<InkStroke> {
        val gap = r.nextDouble(12.0, 18.0)
        val end = r.nextDouble(80.0, 100.0)
        val reach = r.nextDouble(2.4, 3.5) * gap
        val back = r.nextDouble(8.0, 30.0) // down to a head so narrow that it looks like a stroke across the bars
        val bars = listOf(seg(0, 50 - gap, end, 50 - gap), seg(0, 50 + gap, end, 50 + gap))
        val head = if (r.nextBoolean()) listOf(seg(100 - back, 50 - reach, 100, 50, 100 - back, 50 + reach))
            else listOf(seg(100 - back, 50 - reach, 100, 50), seg(100 - back, 50 + reach, 100, 50))
        return noisy(bars + head, r, true)
    }

    private fun noisy(strokes: List<Path>, r: Random, hard: Boolean, keepOrder: Boolean = false): List<InkStroke> {
        val rotation = Math.toRadians(r.nextDouble(-1.0, 1.0) * if (hard) 15.0 else 8.0)
        val stretch = if (hard) 0.25 else 0.12
        val sx = r.nextDouble(1 - stretch, 1 + stretch); val sy = r.nextDouble(1 - stretch, 1 + stretch)
        val size = r.nextDouble(1.2, 4.0)
        val ox = r.nextDouble(0.0, 600.0); val oy = r.nextDouble(0.0, 300.0)
        val ordered = if (hard && !keepOrder && r.nextBoolean()) strokes.shuffled(r) else strokes
        return ordered.map { path ->
            var s = if (hard && r.nextBoolean()) path.reversed() else path
            if (s.size > 1 && hard) s = trimmed(s, r)
            val dense = sampled(s, r)
            if (dense.size > 1 && hard && r.nextInt(5) == 0) dense.addAll(hook(dense, r))
            val shiftX = r.nextDouble(-4.0, 4.0); val shiftY = r.nextDouble(-4.0, 4.0)
            val wob = if (hard) 3.5 else 2.0
            val ax = r.nextDouble(-wob, wob); val ay = r.nextDouble(-wob, wob); val phase = r.nextDouble(0.0, 2 * PI)
            val jitter = if (hard) 1.8 else 1.2
            InkStroke(FloatArray(2 * dense.size) { k ->
                val i = k / 2
                val along = PI * i / maxOf(1, dense.size - 1) + phase
                val x = (dense[i].x - 50) * sx + shiftX + ax * sin(along) + r.nextDouble(-jitter, jitter)
                val y = (dense[i].y - 50) * sy + shiftY + ay * sin(along) + r.nextDouble(-jitter, jitter)
                if (k % 2 == 0) ((x * cos(rotation) - y * sin(rotation)) * size + ox).toFloat()
                else ((x * sin(rotation) + y * cos(rotation)) * size + oy).toFloat()
            })
        }
    }

    /** Both ends cut by up to 12 % of the length: a finger starts late and lifts early. */
    private fun trimmed(path: Path, r: Random): Path {
        val cum = DoubleArray(path.size)
        for (i in 1 until path.size) cum[i] = cum[i - 1] + hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y)
        val total = cum.last()
        if (total <= 0) return path
        val from = r.nextDouble(0.0, 0.12) * total; val to = total - r.nextDouble(0.0, 0.12) * total
        fun at(t: Double): P {
            var i = 1
            while (i < path.size - 1 && cum[i] < t) i++
            val len = cum[i] - cum[i - 1]
            val f = if (len <= 0) 0.0 else ((t - cum[i - 1]) / len).coerceIn(0.0, 1.0)
            return P(path[i - 1].x + f * (path[i].x - path[i - 1].x), path[i - 1].y + f * (path[i].y - path[i - 1].y))
        }
        return listOf(at(from)) + path.indices.filter { cum[it] > from && cum[it] < to }.map { path[it] } + listOf(at(to))
    }

    /** Touch events: a point every 2–7 units, faster in the middle of a segment. */
    private fun sampled(path: Path, r: Random): ArrayList<P> {
        val out = ArrayList<P>()
        out += path[0]
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            val len = hypot(b.x - a.x, b.y - a.y)
            var t = 0.0
            while (true) {
                t += r.nextDouble(2.0, 7.0)
                if (t >= len) break
                out += P(a.x + (b.x - a.x) * t / len, a.y + (b.y - a.y) * t / len)
            }
            out += b
        }
        return out
    }

    /** A small flick where the finger lifts, 3–7 units off to a side. */
    private fun hook(points: List<P>, r: Random): List<P> {
        val last = points.last(); val before = points[points.size - 2]
        val dir = atan2(last.y - before.y, last.x - before.x) + (if (r.nextBoolean()) 1 else -1) * r.nextDouble(1.6, 2.6)
        val len = r.nextDouble(3.0, 7.0)
        return listOf(P(last.x + len / 2 * cos(dir), last.y + len / 2 * sin(dir)), P(last.x + len * cos(dir), last.y + len * sin(dir)))
    }
}
