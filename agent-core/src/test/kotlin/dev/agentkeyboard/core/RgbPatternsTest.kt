// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.*

/** Stage 29b/29c: smooth colours, the six texture patterns, their tiles and the cost per frame. */
class RgbPatternsTest {
    private val textures = RgbPattern.entries.filter { it.textured }
    private fun on(pattern: RgbPattern, change: RgbSettings.() -> RgbSettings = { this }) =
        RgbSettings(enabled = true, pattern = pattern).change()

    private fun engineAt(pattern: RgbPattern, ms: Long, change: RgbSettings.() -> RgbSettings = { this }): RgbEngine {
        val e = RgbEngine(on(pattern, change)).apply { aspect = 0.65f }
        e.advance(0)
        var t = 0L
        while (t < ms) { t = minOf(ms, t + 50); e.advance(t) }
        return e
    }

    private fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
    private fun maxChannelDelta(a: Int, b: Int) = maxOf(abs(ch(a, 16) - ch(b, 16)), abs(ch(a, 8) - ch(b, 8)), abs(ch(a, 0) - ch(b, 0)))
    private fun lum(c: Int) = RgbColors.luminance(c or 0xFF000000.toInt())

    @Test fun texturesAreDeterministicAndMove() {
        for (p in textures) {
            val a = engineAt(p, 1000)
            val b = engineAt(p, 1000)
            val later = engineAt(p, 3000)
            var moved = 0
            for (i in 0 until 40) {
                val x = (i % 8) / 8f + 0.06f
                val y = (i / 8) / 5f + 0.1f
                assertEquals(a.light(x, y, -1, 1000), b.light(x, y, -1, 1000), "$p is deterministic")
                if (a.light(x, y, -1, 1000) != later.light(x, y, -1, 3000)) moved++
            }
            assertTrue(moved > 20, "$p moves: $moved of 40 points changed")
        }
    }

    /** 29b: across a fine row of samples (like the background grid) neighbours differ only a little. */
    @Test fun texturesHaveNoJumpsInSpace() {
        // worst neighbour difference per channel over a 96-column grid, all rows; slats: away from its deliberate edge
        val limits = mapOf(RgbPattern.INK to 45, RgbPattern.DIAMONDS to 24, RgbPattern.SLATS to 40, RgbPattern.HATCH to 40,
            RgbPattern.LED to 32, RgbPattern.TOPO to 30)
        for (p in textures) {
            val e = engineAt(p, 2500)
            var worst = 0
            var where = ""
            for (r in 0 until 64) for (c in 1 until 96) {
                val x = c / 96f
                val y = r / 64f
                if (p == RgbPattern.SLATS && abs(x - slatEdge(e, y)) < 0.06f) continue
                if (p == RgbPattern.HATCH && near(x, y * 0.65f, e)) continue
                val d = maxChannelDelta(e.light(x, y, -1, 0), e.light((c - 1) / 96f, y, -1, 0))
                if (d > worst) { worst = d; where = "x=$x y=$y" }
            }
            println("$p: largest neighbour step $worst of 255 at 96 columns ($where)")
            assertTrue(worst <= limits.getValue(p), "$p: step $worst at $where")
        }
    }

    // the edge of the slats wanders: find it as the darkest point of the row
    private fun slatEdge(e: RgbEngine, y: Float): Float = (0..200).minByOrNull { lum(e.light(it / 200f, y, -1, 0)) }!! / 200f
    // the hatch rainbow turns around a centre: the colours meet there
    private fun near(x: Float, yy: Float, e: RgbEngine): Boolean {
        val t = (e.phase % 4096.0).toFloat()
        val cx = 0.5f + 0.07f * kotlin.math.sin(t * 0.9f)
        val cy = 0.5f * 0.65f + 0.05f * kotlin.math.cos(t * 0.7f)
        return (x - cx) * (x - cx) + (yy - cy) * (yy - cy) < 0.04f
    }

    /** 29b: at the slowest speed every frame changes a colour by a few steps only, without jumps. */
    @Test fun noJumpsOverTime() {
        for (p in RgbPattern.entries - RgbPattern.STATIC_RAINBOW) {
            val e = engineAt(p, 0) { copy(periodMs = 30_000) }
            val points = List(30) { (it % 6) / 6f + 0.05f to (it / 6) / 5f + 0.1f }
            var before = points.map { e.light(it.first, it.second, -1, 0) }
            var worst = 0
            var t = 0L
            repeat(300) { // ten seconds at 30 frames per second
                t += 33
                e.advance(t)
                val now = points.map { e.light(it.first, it.second, -1, t) }
                for (i in now.indices) worst = max(worst, maxChannelDelta(now[i], before[i]))
                before = now
            }
            println("$p: largest change per frame at 30 s per cycle: $worst of 255")
            assertTrue(worst <= 8, "$p jumps by $worst in one frame")
        }
    }

    @Test fun rainbowHasNoCreasesAndOwnColoursNoGreyMiddle() {
        // second difference of the colour channels along the rainbow: the HSV corners at yellow,
        // cyan and magenta are creases (the slope of a channel turns at once); rounded, it turns gradually
        val e = RgbEngine(on(RgbPattern.WAVE_HORIZONTAL))
        fun crease(color: (Float) -> Int): Double {
            var worst = 0.0
            val n = 120 // 3° steps: rounding to whole channel values does not hide the corners
            for (i in 1 until n) {
                val a = color((i - 1) / n.toFloat())
                val b = color(i / n.toFloat())
                val c = color((i + 1) / n.toFloat())
                for (shift in listOf(0, 8, 16)) worst = maxOf(worst, abs(ch(a, shift) - 2.0 * ch(b, shift) + ch(c, shift)))
            }
            return worst
        }
        val smooth = crease { e.paletteAt(it, true) }
        val hsv = crease { RgbColors.hsv(it * 360f, 1f, 1f) }
        println("rainbow crease: HSV $hsv, smoothed $smooth")
        assertTrue(smooth < hsv / 2.5, "smoothed $smooth vs HSV $hsv")
        // red to blue: OKLCH keeps the middle bright and coloured, sRGB gives a dark purple
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val mid = RgbColors.mixOklch(red, blue, 0.5f)
        assertTrue(lum(mid) > lum(RgbColors.lerp(red, blue, 0.5f)) * 1.3, "OKLCH middle is brighter")
        assertTrue(RgbColors.toHsv(mid)[1] > 0.5f, "and still coloured")
        // exact ends
        assertEquals(red, RgbColors.mixOklch(red, blue, 0f))
        assertEquals(blue, RgbColors.mixOklch(red, blue, 1f))
        // OKLab round trip
        val lab = FloatArray(3)
        for (c in listOf(0xFF123456, 0xFFFFFFFF, 0xFF000000, 0xFFFF8800).map { it.toInt() }) {
            RgbColors.toOklab(c, lab)
            assertTrue(maxChannelDelta(c, RgbColors.fromOklab(lab[0], lab[1], lab[2])) <= 1)
        }
    }

    @Test fun eachPatternLooksLikeItsReference() {
        // ink: black water and bright ink in the same picture
        val ink = engineAt(RgbPattern.INK, 1200)
        val inkLums = grid(ink).map { lum(it) }
        assertTrue(inkLums.count { it < 0.01 } > inkLums.size / 10, "ink has dark water")
        assertTrue(inkLums.count { it > 0.08 } > inkLums.size / 5, "and bright swirls")
        // slats: across the edge the colours differ by about half the rainbow
        val slats = engineAt(RgbPattern.SLATS, 1000)
        val y = 0.7f
        val edge = slatEdge(slats, y)
        val left = RgbColors.toHsv(slats.light(edge - 0.12f, y, -1, 0))[0]
        val right = RgbColors.toHsv(slats.light(edge + 0.12f, y, -1, 0))[0]
        val diff = abs(((left - right) % 360f + 540f) % 360f - 180f)
        assertTrue(diff > 90f, "hard colour change: hues $left and $right")
        // hatch: dark corners (vignette), bright middle ring
        val hatch = engineAt(RgbPattern.HATCH, 500)
        assertTrue(lum(hatch.light(0.02f, 0.03f, -1, 0)) < 0.02, "dark corner")
        assertTrue(lum(hatch.light(0.3f, 0.5f, -1, 0)) > 0.1, "lit inside")
        // LED, diamonds, topo: a rainbow across the keyboard (several hues present)
        for (p in listOf(RgbPattern.LED, RgbPattern.DIAMONDS, RgbPattern.TOPO, RgbPattern.INK)) {
            val hues = grid(engineAt(p, 700)).filter { lum(it) > 0.02 }.map { (RgbColors.toHsv(it)[0] / 60f).toInt() }.toSet()
            assertTrue(hues.size >= 3, "$p shows several colours: $hues")
        }
        // the palette setting applies to textures too: own colours only
        val own = engineAt(RgbPattern.LED, 700) { copy(rainbow = false, palette = listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())) }
        assertTrue(grid(own).all { RgbColors.green(it) < 90 }, "no green from a red-blue palette")
    }

    private fun grid(e: RgbEngine) = (0 until 32 * 20).map { i -> e.light((i % 32 + 0.5f) / 32, (i / 32 + 0.5f) / 20, -1, 0) }

    @Test fun tilesAreSeamlessAndShaped() {
        for (p in RgbPattern.entries) assertEquals(RgbOverlay.hasOverlay(p), RgbOverlay.tile(p) != null, "$p")
        val led = RgbOverlay.tile(RgbPattern.LED)!!
        assertEquals(0, led.pixels[led.width / 2 * led.width + led.width / 2] ushr 24, "LED: the light shows through the hole")
        assertTrue(led.pixels[0] ushr 24 > 200, "and the panel around it is dark and solid")
        assertTrue(RgbColors.luminance(led.pixels[0] or 0xFF000000.toInt()) < 0.05)
        // contours and diamonds repeat without a seam: the first and last column/row continue each other
        for (p in listOf(RgbPattern.TOPO, RgbPattern.DIAMONDS)) {
            val t = RgbOverlay.tile(p)!!
            var lineCoverage = 0
            fun a(x: Int, y: Int) = t.pixels[y * t.width + x] ushr 24
            // the step across the seam is like any step inside the tile (average over the edge)
            var seamX = 0.0; var seamY = 0.0; var inside = 0.0
            for (i in 0 until t.height) { seamX += abs(a(0, i) - a(t.width - 1, i)); seamY += abs(a(i, 0) - a(i, t.height - 1)) }
            for (i in 0 until t.height) for (k in 0 until t.width - 1) inside += abs(a(k, i) - a(k + 1, i))
            seamX /= t.height; seamY /= t.width; inside /= t.height * (t.width - 1.0)
            for (px in t.pixels) if (px ushr 24 > 60) lineCoverage++
            assertTrue(seamX <= inside * 2 + 2 && seamY <= inside * 2 + 2, "$p seam: $seamX / $seamY vs $inside")
            assertTrue(lineCoverage in t.pixels.size / 50..t.pixels.size / 2, "$p has lines: $lineCoverage")
        }
        // stripe tiles: hatching darkens, slats have dark gaps and a light edge
        val hatch = RgbOverlay.tile(RgbPattern.HATCH)!!
        assertTrue(hatch.pixels.any { it ushr 24 > 80 } && hatch.pixels.any { it ushr 24 < 10 })
        val slats = RgbOverlay.tile(RgbPattern.SLATS)!!
        assertTrue(slats.pixels.any { it ushr 24 > 200 && (it and 0xFFFFFF) == 0 } && slats.pixels.any { (it and 0xFFFFFF) == 0xFFFFFF })
        // the mean feeds label contrast: the LED panel is mostly dark grey
        assertTrue(led.mean ushr 24 > 100)
    }

    @Test fun tileTransformScalesToTheViewAndDriftsWithTheClock() {
        val m0 = RgbOverlay.transform(RgbPattern.LED, 1080f, 700f, 0.0, false)
        assertEquals(1080f / 34f / 32f, m0[0], 1e-4f, "one LED is a 34th of the width")
        assertEquals(0f, m0[1], 1e-6f)
        val a = RgbOverlay.transform(RgbPattern.TOPO, 1080f, 700f, 1.0, false)
        val b = RgbOverlay.transform(RgbPattern.TOPO, 1080f, 700f, 2.0, false)
        assertNotEquals(a[2], b[2], "contours drift")
        val r = RgbOverlay.transform(RgbPattern.TOPO, 1080f, 700f, 1.0, true)
        assertNotEquals(a[2], r[2], "reverse drifts the other way")
        // the drift wraps with the tile: never a jump larger than one tile
        val tile = RgbOverlay.BIG_TILE * a[0]
        for (k in 0..100) assertTrue(abs(RgbOverlay.transform(RgbPattern.TOPO, 1080f, 700f, k * 7.3, false)[2]) <= tile + 1f)
        // rotated stripes
        val h = RgbOverlay.transform(RgbPattern.HATCH, 1080f, 700f, 0.0, false)
        assertTrue(abs(h[1]) > 0.1f * abs(h[0]), "hatching is diagonal")
        // a view without width does not break
        RgbOverlay.transform(RgbPattern.LED, Float.NaN, 0f, Double.NaN, false)
    }

    @Test fun keyFieldAndOverlayStrengthFollowTheTapRules() {
        val flash = RgbEngine(on(RgbPattern.INK) { copy(tapReaction = RgbTapReaction.FLASH, reactiveOnly = true, glowMs = 1000) })
        assertEquals(0, RgbColors.alpha(flash.light(0.5f, 0.5f, -1, 0)), "background stays dark")
        assertTrue(RgbColors.alpha(flash.light(0.5f, 0.5f, RgbEngine.FIELD_KEYS, 0)) > 0, "the key field is there, keys fade it")
        assertEquals(0f, flash.keyFactor(3, 0))
        flash.tap(0.5f, 0.5f, 3, 0)
        assertEquals(1f, flash.keyFactor(3, 0))
        assertEquals(0f, flash.keyFactor(4, 0))
        assertEquals(0f, flash.overlayStrength(0), "reactive flash: no detail on the background")
        val plain = RgbEngine(on(RgbPattern.LED) { copy(mix = 40, tapReaction = RgbTapReaction.FLASH) })
        assertEquals(0.4f, plain.overlayStrength(0), 1e-6f)
        plain.tap(0.5f, 0.5f, 3, 0)
        assertEquals(RgbEngine.HIGHLIGHT, plain.keyHighlight(3, 0), 1e-6f)
        assertEquals(0f, plain.keyHighlight(4, 0))
        val reactive = RgbEngine(on(RgbPattern.LED) { copy(reactiveOnly = true, glowMs = 1000) })
        assertEquals(0f, reactive.overlayStrength(0))
        reactive.tap(0.1f, 0.1f, 1, 0)
        assertEquals(1f, reactive.overlayStrength(0), 1e-6f)
    }

    /**
     * Pictures for looking at the patterns (not a pixel test): the 48 x 32 grid stretched with
     * bilinear filtering and the tile on top, as the keyboard background draws them, black theme.
     * Written to agent-core/build/rgb-previews/.
     */
    @Test fun writesPreviewPictures() {
        val w = 720
        val h = 468
        val dir = java.io.File("build/rgb-previews").apply { mkdirs() }
        for (p in RgbPattern.entries) for ((name, ms) in listOf("a" to 1500L, "b" to 4500L)) {
            val e = engineAt(p, ms).apply { aspect = h.toFloat() / w }
            val cols = 48
            val rows = 31
            val grid = IntArray(cols * rows) { e.light((it % cols + 0.5f) / cols, (it / cols + 0.5f) / rows, -1, ms) }
            val tile = RgbOverlay.tile(p)
            val m = RgbOverlay.transform(p, w.toFloat(), h.toFloat(), e.phase, false)
            // inverse of the 2 x 3 matrix: view pixel -> tile pixel
            val det = m[0] * m[4] - m[1] * m[3]
            val image = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (y in 0 until h) for (x in 0 until w) {
                val gx = ((x + 0.5f) / w * cols - 0.5f).coerceIn(0f, cols - 1f)
                val gy = ((y + 0.5f) / h * rows - 0.5f).coerceIn(0f, rows - 1f)
                val x0 = gx.toInt().coerceAtMost(cols - 2); val y0 = gy.toInt().coerceAtMost(rows - 2)
                val fx = gx - x0; val fy = gy - y0
                fun at(cx: Int, cy: Int) = grid[cy * cols + cx]
                val top = RgbColors.lerp(at(x0, y0), at(x0 + 1, y0), fx)
                val bottom = RgbColors.lerp(at(x0, y0 + 1), at(x0 + 1, y0 + 1), fx)
                val light = RgbColors.lerp(top, bottom, fy)
                var c = RgbColors.applyLight(0xFF000000.toInt(), light)
                if (tile != null) {
                    val vx = x + 0.5f - m[2]; val vy = y + 0.5f - m[5]
                    val tx = (m[4] * vx - m[1] * vy) / det
                    val ty = (-m[3] * vx + m[0] * vy) / det
                    val ix = Math.floorMod(kotlin.math.floor(tx).toInt(), tile.width)
                    val iy = Math.floorMod(kotlin.math.floor(ty).toInt(), tile.height)
                    c = RgbColors.over(tile.pixels[iy * tile.width + ix], c)
                }
                image.setRGB(x, y, c and 0xFFFFFF)
            }
            javax.imageio.ImageIO.write(image, "png", java.io.File(dir, "${p.name.lowercase()}-$name.png"))
        }
        assertTrue(java.io.File(dir, "ink-a.png").length() > 1000)
    }

    /**
     * Coarse cost check, not a benchmark: one keyboard frame of each pattern = the 48 x 32 grid of
     * the background (or the smooth light under the keys) plus 45 key centres twice and 8 strip
     * points. Prints the average per frame; the tiles are made once per pattern and timed too.
     */
    @Test fun oneFrameOfEachPatternFitsTheBudget() {
        val report = StringBuilder()
        for (p in RgbPattern.entries) {
            val e = RgbEngine(on(p) { copy(tapReaction = RgbTapReaction.RIPPLE, glowMs = 5000) }).apply { aspect = 0.65f }
            for (i in 0 until RgbEngine.MAX_TAPS) e.tap(i / 8f, 0.5f, i, 0)
            fun frame(t: Long): Int {
                var sum = 0
                for (r in 0 until 32) for (c in 0 until 48) sum += e.light((c + 0.5f) / 48, (r + 0.5f) / 32, -1, t)
                for (k in 0 until 45) { sum += e.light(k / 45f, (k % 5) / 5f, k, t); sum += e.light(k / 45f, (k % 5) / 5f, -1, t) }
                for (s in 0 until 8) sum += e.light(s / 8f, 0f, -1, t)
                return sum
            }
            repeat(60) { frame(it.toLong()) } // warm-up
            val frames = 120
            val start = System.nanoTime()
            var sink = 0
            for (f in 0 until frames) { e.advance(f * 33L); sink += frame(f * 33L % 5000) }
            val micros = (System.nanoTime() - start) / 1000.0 / frames
            val tileStart = System.nanoTime()
            val tile = RgbOverlay.tile(p)
            val tileMs = (System.nanoTime() - tileStart) / 1e6
            report.append("$p: ${"%.0f".format(micros)} µs per frame (1634 values)" +
                (if (tile != null) ", tile ${tile.width}x${tile.height} once ${"%.1f".format(tileMs)} ms" else "") + " [$sink]\n")
            assertTrue(micros < 15_000, "$p: $micros µs per frame, far too slow for 30 frames per second")
        }
        println(report)
    }
}
