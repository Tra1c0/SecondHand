// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.charart

import org.junit.Test
import kotlin.test.*

/** The drawing area, the photo template and black and white (stage 53). */
class SketchTest {
    private fun p(x: Float, y: Float) = SketchPoint(x, y)
    private fun inkCount(raster: Raster) = raster.values.count { it >= 0.5f }

    @Test fun penLineEllipseAndUndo() {
        val sketch = Sketch(size = 40)
        assertTrue(sketch.isEmpty)
        assertNull(sketch.rasterize().inkBounds())
        assertTrue(sketch.add(SketchOp.Stroke(listOf(p(0.1f, 0.1f), p(0.9f, 0.1f)), 0.05f)))
        val line = sketch.rasterize()
        assertEquals(1f, line.ink[20, 4])
        assertEquals(0f, line.ink[20, 20])
        assertTrue(sketch.add(SketchOp.Ellipse(p(0.2f, 0.3f), p(0.8f, 0.9f), 0.05f)))
        val both = sketch.rasterize()
        assertEquals(1f, both.ink[20, 12]) // top of the ellipse
        assertEquals(0f, both.ink[20, 24]) // its middle stays empty: an outline
        assertTrue(sketch.undo())
        assertEquals(line.ink.values.toList(), sketch.rasterize().ink.values.toList())
        assertTrue(sketch.undo())
        assertFalse(sketch.undo())
        assertFalse(sketch.add(SketchOp.Stroke(emptyList(), 0.05f)))
        // points outside the area are kept on its border
        sketch.add(SketchOp.Line(p(-1f, 0.5f), p(2f, 0.5f), 0.05f))
        assertEquals(SketchOp.Line(p(0f, 0.5f), p(1f, 0.5f), 0.05f), sketch.ops.single())
        // an ellipse without height is a line
        sketch.clear()
        sketch.add(SketchOp.Ellipse(p(0.1f, 0.5f), p(0.9f, 0.5f), 0.05f))
        assertEquals(1f, sketch.rasterize().ink[20, 20])
    }

    @Test fun fillStopsAtInkAndTheBorder() {
        val sketch = Sketch(size = 40)
        sketch.add(SketchOp.Ellipse(p(0.2f, 0.2f), p(0.8f, 0.8f), 0.05f))
        sketch.add(SketchOp.Fill(p(0.5f, 0.5f)))
        val filled = sketch.rasterize()
        assertEquals(1f, filled.ink[20, 20])
        assertEquals(0f, filled.ink[2, 2]) // outside the ellipse stays paper
        val inside = inkCount(filled.ink)
        sketch.undo()
        sketch.add(SketchOp.Fill(p(0.02f, 0.02f))) // around it instead
        val around = sketch.rasterize()
        assertEquals(1f, around.ink[2, 2])
        assertEquals(0f, around.ink[20, 20])
        assertTrue(inkCount(around.ink) > inside)
        // filling ink changes nothing
        sketch.clear()
        sketch.add(SketchOp.Line(p(0f, 0.5f), p(1f, 0.5f), 0.1f))
        val before = sketch.rasterize().ink.values.toList()
        sketch.add(SketchOp.Fill(p(0.5f, 0.5f)))
        assertEquals(before, sketch.rasterize().ink.values.toList())
    }

    @Test fun eraserRemovesInkAndThePhoto() {
        val sketch = Sketch(size = 40)
        sketch.add(SketchOp.Line(p(0f, 0.5f), p(1f, 0.5f), 0.2f))
        sketch.add(SketchOp.Stroke(listOf(p(0.5f, 0f), p(0.5f, 1f)), 0.2f, erase = true))
        val raster = sketch.rasterize()
        assertEquals(0f, raster.ink[20, 20])
        assertEquals(1f, raster.erased[20, 20])
        assertEquals(1f, raster.ink[5, 20])
        // over a black photo the erased strip becomes paper
        val photo = Photo(Raster(10, 10, FloatArray(100) { 1f }), 0f, 0f, 1f, 1f)
        val art = CharArt.render(PictureElement(raster, photo, PhotoOptions(dither = Dither.NONE), Look(style = ArtStyle.HALF_BLOCKS)), null,
            output = OutputOptions(TargetApp.GENERIC, width = 8, codeBlock = false))
        assertEquals(listOf("███${CharArt.PAD}${CharArt.PAD}███").map { it.replace(CharArt.PAD, TextColumns.BRAILLE_BLANK) }, art.lines.take(1))
    }

    @Test fun ditherAndThreshold() {
        val gray = Raster(8, 8, FloatArray(64) { 0.5f })
        assertEquals(64, gray.toBits(0.5f, Dither.NONE).let { bits -> (0 until 64).count { bits[it % 8, it / 8] } })
        assertEquals(0, gray.toBits(0.6f, Dither.NONE).let { bits -> (0 until 64).count { bits[it % 8, it / 8] } })
        val ordered = gray.toBits(0.5f, Dither.ORDERED)
        assertEquals(listOf("#.#.#.#.", ".#.#.#.#", "#.#.#.#.", ".#.#.#.#"), ordered.rows().take(4))
        val diffused = gray.toBits(0.5f, Dither.FLOYD_STEINBERG)
        val count = (0 until 64).count { diffused[it % 8, it / 8] }
        assertTrue(count in 28..36, "about half of a 50 % gray is ink: $count")
        // contrast pushes gray away from the middle, invert swaps paper and ink
        assertEquals(0.75f, Raster(1, 1, floatArrayOf(0.625f)).withContrast(2f)[0, 0])
        assertEquals(0.25f, Raster(1, 1, floatArrayOf(0.75f)).inverted()[0, 0])
        // outline keeps only the edge
        val square = Bits.parse(listOf("###", "###", "###"))
        assertEquals(listOf("###", "#.#", "###"), square.outline().rows())
        assertEquals(listOf("..#"), Bits.parse(listOf("#..")).mirrored().rows())
    }

    /** Review of stage 53, M3: the kept raster and the live preview give exactly what drawing every step again gives. */
    @Test fun keptRasterAndPreviewMatchDrawingEverythingAgain() {
        fun fresh(ops: List<SketchOp>, size: Int = 40) = Sketch(size).also { s -> ops.forEach { s.add(it) } }.rasterize()
        fun same(a: SketchRaster, b: SketchRaster) {
            assertContentEquals(a.ink.values, b.ink.values)
            assertContentEquals(a.erased.values, b.erased.values)
        }
        val sketch = Sketch(size = 40)
        sketch.add(SketchOp.Ellipse(p(0.2f, 0.2f), p(0.8f, 0.8f), 0.05f))
        val first = sketch.rasterize()
        assertSame(first, sketch.rasterize()) // kept, not drawn again
        sketch.add(SketchOp.Fill(p(0.5f, 0.5f)))
        assertEquals(0f, first.ink[20, 20]) // a raster handed out before stays as it was
        same(fresh(sketch.ops), sketch.rasterize())
        // a stroke drawn point by point, as the finger moves, with points outside the area
        val points = ArrayList<SketchPoint>()
        var shown: SketchRaster? = null
        for (i in 0..30) {
            points += p(-0.1f + i * 0.04f, 0.1f + (i % 7) * 0.12f)
            shown = sketch.preview(SketchOp.Stroke(points, 0.05f, erase = i > 20))
        }
        // an eraser changes the kind of stroke: drawn again from the kept raster
        same(fresh(sketch.ops + SketchOp.Stroke(points.toList(), 0.05f, erase = true)), shown!!)
        val pen = ArrayList<SketchPoint>()
        for (i in 0..30) { pen += p(0.05f + i * 0.03f, 0.9f - (i % 5) * 0.1f); shown = sketch.preview(SketchOp.Stroke(pen, 0.08f)) }
        same(fresh(sketch.ops + SketchOp.Stroke(pen.toList(), 0.08f)), shown)
        // the finished stroke, then undo and clear draw again
        sketch.add(SketchOp.Stroke(pen.toList(), 0.08f))
        same(fresh(sketch.ops), sketch.rasterize())
        sketch.add(SketchOp.Line(p(0f, 0f), p(1f, 1f), 0.1f))
        assertTrue(sketch.undo())
        same(fresh(sketch.ops), sketch.rasterize())
        // a line being dragged: each preview starts again from the kept steps
        sketch.preview(SketchOp.Line(p(0f, 0f), p(1f, 0f), 0.1f))
        same(fresh(sketch.ops + SketchOp.Line(p(0f, 0f), p(0f, 1f), 0.1f)), sketch.preview(SketchOp.Line(p(0f, 0f), p(0f, 1f), 0.1f)))
        sketch.clear()
        assertNull(sketch.rasterize().inkBounds())
    }

    /**
     * Review of stage 53, M3: a long stroke over a full drawing stays fast because every move draws only
     * its new points. The bound is generous (a slow CI machine); drawing everything again per move took
     * far longer on the same data.
     */
    @Test fun aLongStrokeOverAFullDrawingStaysFast() {
        val sketch = Sketch()
        for (i in 0 until Sketch.MAX_OPS - 1) {
            val y = (i % 40) / 40f
            sketch.add(SketchOp.Line(p(0f, y), p(1f, 1f - y), 0.025f))
        }
        sketch.rasterize()
        val points = ArrayList<SketchPoint>()
        val started = System.nanoTime()
        for (i in 0 until Sketch.MAX_POINTS) {
            points += p((i % 200) / 200f, (i / 200) / 10f)
            sketch.preview(SketchOp.Stroke(points, 0.045f))
        }
        sketch.add(SketchOp.Stroke(points, 0.045f))
        // filling the whole area many times
        val empty = Sketch()
        repeat(50) { empty.add(SketchOp.Fill(p(0.5f, 0.5f))); empty.undo(); empty.rasterize(SketchOp.Fill(p(0.5f, 0.5f))) }
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(millis < 5_000, "$millis ms for ${Sketch.MAX_POINTS} moves and 50 fills")
        assertEquals(Sketch.MAX_OPS, sketch.ops.size)
    }

    @Test fun photoTemplate() {
        // a wide photo sits in the middle of the square area
        val wide = Photo.fitted(Raster(20, 10))
        assertEquals(listOf(0f, 0.25f, 1f, 0.75f), listOf(wide.left, wide.top, wide.right, wide.bottom))
        val tall = Photo.fitted(Raster(10, 20))
        assertEquals(listOf(0.25f, 0f, 0.75f, 1f), listOf(tall.left, tall.top, tall.right, tall.bottom))
        // a gradient from white to black: the art of its right half is darker
        val gradient = Raster(40, 40, FloatArray(1_600) { (it % 40) / 39f })
        val output = OutputOptions(TargetApp.GENERIC, width = 20, codeBlock = false)
        val art = CharArt.render(PictureElement(null, Photo.fitted(gradient), PhotoOptions(dither = Dither.NONE), Look(style = ArtStyle.ASCII)), null, output = output)
        assertEquals(20, art.columns)
        assertEquals("${TextColumns.BRAILLE_BLANK}", art.lines[0].take(1))
        assertEquals("@", art.lines[0].takeLast(1))
        // only a template to draw over: the photo itself is not part of the art
        val template = CharArt.render(PictureElement(Sketch().rasterize(), Photo.fitted(gradient), PhotoOptions(use = false)), null, output = output)
        assertTrue(template.isEmpty)
        // inverting the photo darkens only the photo, not the paper around it
        val invertedWide = CharArt.render(PictureElement(null, Photo.fitted(Raster(20, 10)), PhotoOptions(invert = true, dither = Dither.NONE),
            Look(style = ArtStyle.HALF_BLOCKS), crop = false), null, output = output.copy(brailleBlanks = false))
        // rows 3 to 7 of 10: the photo's place, its edges as half blocks; the empty rows around it are dropped
        assertEquals(listOf("▄".repeat(20)) + List(4) { "█".repeat(20) } + listOf("▀".repeat(20)), invertedWide.lines)
    }
}
