// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbOverlay
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbTile
import kotlin.math.roundToInt

/**
 * The light of one element as a picture (stage 29b/29c): a small grid of light values ([COLUMNS]
 * x up to [MAX_ROWS], computed by the engine once per frame) stretched with bilinear filtering over
 * the element, plus the sharp detail tile of texture patterns ([RgbOverlay]) as a repeating shader
 * that only moves. Used by the keyboard ([RgbKeyPainter]) and the preview of the settings page, so
 * both look the same. Not thread-safe: UI thread only.
 *
 * Shaders are in view coordinates: [setArea] maps the grid onto the lit area once per frame; a key
 * is drawn through its alpha mask at its view position, so neighbouring keys and the background
 * continue each other without seams.
 */
class RgbTexture {
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var cols = COLUMNS
    private var rows = 2
    private var fieldShader: BitmapShader? = null
    private val fieldMatrix = Matrix()
    private val fieldPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private var tilePattern: RgbPattern? = null
    private var tileShader: BitmapShader? = null
    private val tileMatrix = Matrix()
    private val tileValues = FloatArray(9)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    /** Average colour the tile adds (alpha = coverage), for label contrast; 0 without a tile. */
    var tileMean = 0
        private set
    val hasTile get() = tileShader != null

    /**
     * Computes the grid for [engine] at [nowMs] over a [width] x [height] area at ([left], [top]) of
     * the canvas, for element [keyId] (-1 background and strip, [RgbEngine.FIELD_KEYS] under the keys).
     */
    fun update(engine: RgbEngine, left: Float, top: Float, width: Float, height: Float, nowMs: Long, keyId: Int) {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        val newRows = (COLUMNS * h / w).roundToInt().coerceIn(2, MAX_ROWS)
        if (bitmap == null || newRows != rows) {
            rows = newRows
            cols = COLUMNS
            bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            pixels = IntArray(cols * rows)
            fieldShader = BitmapShader(bitmap!!, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            generation++
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = (c + 0.5f) / cols
            val y = (r + 0.5f) / rows
            pixels[r * cols + c] = engine.effects(engine.base(x, y), x, y, keyId, nowMs)
        }
        bitmap!!.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        fieldMatrix.setScale(w / cols, h / rows)
        fieldMatrix.postTranslate(left, top)
        fieldShader!!.setLocalMatrix(fieldMatrix)
        fieldPaint.shader = fieldShader
        updateTile(engine, left, top, w, h)
    }

    /** Copies the grid of [other] (same engine and element): no second computation. */
    fun copyFrom(other: RgbTexture) {
        if (other.bitmap == null) return
        if (bitmap == null || other.rows != rows) {
            rows = other.rows
            cols = other.cols
            bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            pixels = IntArray(cols * rows)
            fieldShader = BitmapShader(bitmap!!, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            generation++
        }
        System.arraycopy(other.pixels, 0, pixels, 0, pixels.size)
        bitmap!!.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        fieldMatrix.set(other.fieldMatrix)
        fieldShader!!.setLocalMatrix(fieldMatrix)
        fieldPaint.shader = fieldShader
        if (tilePattern != other.tilePattern) generation++
        tilePattern = other.tilePattern
        tileShader = other.tileShader
        tileBitmap = other.tileBitmap
        tileMatrix.set(other.tileMatrix)
        tileMean = other.tileMean
        tilePaint.shader = tileShader
    }

    private fun updateTile(engine: RgbEngine, left: Float, top: Float, width: Float, height: Float) {
        val pattern = engine.settings.pattern
        if (pattern != tilePattern) {
            tilePattern = pattern
            val tile = tileBitmap(pattern)
            tileShader = tile?.let { BitmapShader(it.first, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
            tileBitmap = tile?.first
            generation++
            tileMean = tile?.second?.mean ?: 0
            tilePaint.shader = tileShader
        }
        val shader = tileShader ?: return
        val values = RgbOverlay.transform(pattern, width, height, engine.phase, engine.settings.reverse)
        System.arraycopy(values, 0, tileValues, 0, 9)
        tileMatrix.setValues(tileValues)
        tileMatrix.postTranslate(left, top)
        shader.setLocalMatrix(tileMatrix)
    }

    /** The light over the rectangle (alpha of the grid is the strength), then the tile with [tileAlpha] 0..1. */
    fun drawRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, tileAlpha: Float) {
        if (fieldShader == null) return
        fieldPaint.alpha = 255
        canvas.drawRect(left, top, right, bottom, fieldPaint)
        drawTileRect(canvas, left, top, right, bottom, tileAlpha)
    }

    fun drawTileRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, tileAlpha: Float) {
        if (tileShader == null || tileAlpha <= 0f) return
        tilePaint.alpha = (tileAlpha.coerceIn(0f, 1f) * 255).roundToInt()
        canvas.drawRect(left, top, right, bottom, tilePaint)
    }

    /** The light through a key shape: a round rectangle (preview) with [fieldAlpha] and [tileAlpha] 0..1. */
    fun drawRoundRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, fieldAlpha: Float, tileAlpha: Float) {
        if (fieldShader != null && fieldAlpha > 0f) {
            fieldPaint.alpha = (fieldAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, fieldPaint)
        }
        if (tileShader != null && tileAlpha > 0f) {
            tilePaint.alpha = (tileAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, tilePaint)
        }
    }

    /**
     * The light through the alpha [mask] of a key whose top left is at ([left], [top]) in view
     * coordinates; the canvas must already be translated there and the mask is drawn at (0, 0). The
     * field with [fieldAlpha], then the tile with [tileAlpha].
     *
     * A shader on a drawn bitmap is placed in the bitmap's own space on some renderers and in the
     * canvas space on others; with the mask at the canvas origin both are the same. Each key gets
     * its own shader objects ([id]), because a display list may keep a shader by reference until the
     * frame is drawn: one shared shader with a matrix changed per key would put every key at the last
     * position.
     */
    fun drawMask(canvas: Canvas, mask: Bitmap, id: Int, left: Float, top: Float, fieldAlpha: Float, tileAlpha: Float) {
        val own = keyShaders.getOrPut(id) { KeyShaders() }
        if (own.generation != generation) {
            own.field = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            own.tile = tileBitmap?.let { BitmapShader(it, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
            own.generation = generation
        }
        val field = own.field
        if (field != null && fieldAlpha > 0f) {
            keyMatrix.set(fieldMatrix)
            keyMatrix.postTranslate(-left, -top)
            field.setLocalMatrix(keyMatrix)
            keyPaint.shader = field
            keyPaint.alpha = (fieldAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            canvas.drawBitmap(mask, 0f, 0f, keyPaint)
        }
        val tile = own.tile
        if (tile != null && tileShader != null && tileAlpha > 0f) {
            keyMatrix.set(tileMatrix)
            keyMatrix.postTranslate(-left, -top)
            tile.setLocalMatrix(keyMatrix)
            keyPaint.shader = tile
            keyPaint.alpha = (tileAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            canvas.drawBitmap(mask, 0f, 0f, keyPaint)
        }
        keyPaint.shader = null
        if (keyShaders.size > MAX_KEY_SHADERS) keyShaders.clear()
    }

    private class KeyShaders {
        var field: BitmapShader? = null
        var tile: BitmapShader? = null
        var generation = -1
    }
    private val keyShaders = HashMap<Int, KeyShaders>()
    private val keyMatrix = Matrix()
    private val keyPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    /** Changes whenever the field bitmap or the tile bitmap is replaced: per-key shaders are made again. */
    private var generation = 0
    private var tileBitmap: Bitmap? = null

    companion object {
        /** Columns of the light grid (stage 26: 24); 48 keeps gradients free of visible creases. */
        const val COLUMNS = 48
        const val MAX_ROWS = 32
        private const val MAX_KEY_SHADERS = 256

        /** Tiles are made once per pattern (big ones take some ms) and kept while in use; at most [KEEP]. */
        private const val KEEP = 3
        private val tiles = LinkedHashMap<RgbPattern, Pair<Bitmap, RgbTile>>(4, 0.75f, true)

        fun tileBitmap(pattern: RgbPattern): Pair<Bitmap, RgbTile>? {
            if (!RgbOverlay.hasOverlay(pattern)) return null
            tiles[pattern]?.let { return it }
            val tile = RgbOverlay.tile(pattern) ?: return null
            val bitmap = Bitmap.createBitmap(tile.pixels, tile.width, tile.height, Bitmap.Config.ARGB_8888)
            val entry = bitmap to tile
            tiles[pattern] = entry
            while (tiles.size > KEEP) tiles.remove(tiles.keys.first())
            return entry
        }
    }
}
