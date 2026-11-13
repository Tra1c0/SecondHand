// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import androidx.annotation.VisibleForTesting
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbFrameStats
import dev.agentkeyboard.core.RgbOverlay
import dev.agentkeyboard.core.RgbPattern
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * The light of one element as a picture (stage 29b/29c): a small grid of light values ([COLUMNS]
 * x up to [MAX_ROWS]) stretched with bilinear filtering over the element, plus the sharp detail tile
 * of texture patterns ([RgbOverlay]) as a repeating shader that only moves. Used by the keyboard
 * ([RgbKeyPainter]) and the preview of the settings page, so both look the same. Call from one
 * thread (the UI thread, or a background thread that hands the finished instance over, like the
 * pattern gallery).
 *
 * The grid is computed on a background thread ([RgbWork], build-14 lag): [update] takes the grid
 * that is ready (a copy of 48 x up to 32 values into the bitmap), and asks for the next one only if
 * the light can have changed (phase, flow, size, taps); a still pattern costs nothing per frame.
 * The picture is thus one grid behind the clock (one frame while running); the very first frame
 * of an element draws no light, the finished grid redraws through [RgbWork]'s listeners.
 * Double-buffered: the job writes a buffer the UI thread does not show, the UI thread swaps.
 *
 * Shaders are in view coordinates: [setArea] maps the grid onto the lit area once per frame; a key
 * is drawn through its alpha mask at its view position, so neighbouring keys and the background
 * continue each other without seams.
 *
 * Tiles come from [RgbTiles]: built on a background thread, so the first frames of a texture
 * pattern are drawn without its detail until the tile is ready (then a redraw is asked for).
 */
class RgbTexture {
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var cols = COLUMNS
    private var rows = 2
    private var fieldShader: BitmapShader? = null
    private val fieldMatrix = Matrix()
    private val fieldPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    /** The area the grid was last stretched over (tests compare drawn pixels with the bilinear grid). */
    @VisibleForTesting internal var areaLeft = 0f; private set
    @VisibleForTesting internal var areaTop = 0f; private set
    @VisibleForTesting internal var areaWidth = 1f; private set
    @VisibleForTesting internal var areaHeight = 1f; private set

    private var tilePattern: RgbPattern? = null
    private var tileShader: BitmapShader? = null
    private val tileMatrix = Matrix()
    private val tileValues = FloatArray(9)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    /** Average colour the tile adds (alpha = coverage), for label contrast; 0 without a tile. */
    var tileMean = 0
        private set
    val hasTile get() = tileShader != null
    /** True once a light grid was computed or copied ([updateTileOnly] alone leaves it false). */
    val hasField get() = bitmap != null

    /** Where the time of background grids is counted (the keyboard's [RgbLighting.frameStats]); null: not counted. */
    var stats: RgbFrameStats? = null

    // background grid: guarded by [lock]; [job] and [spare] belong to the job while [inFlight]
    private val lock = Any()
    private var inFlight = false
    private var ready: IntArray? = null
    private var readyRows = 0
    private var spare: IntArray? = null
    private var job: RgbEngine? = null
    private var jobSource: RgbEngine? = null
    // what the last requested grid was made from: a new one only when one of these changes
    private var askedEngine: RgbEngine? = null
    private var askedPhase = Double.NaN
    private var askedFlow = Double.NaN
    private var askedAspect = Float.NaN
    private var askedRows = -1
    private var askedKeyId = Int.MIN_VALUE
    private var askedNow = Long.MIN_VALUE
    /** Grids made in the background for this texture (tests). */
    @VisibleForTesting internal var gridsMade = 0; private set

    /**
     * The grid for [engine] at [nowMs] over a [width] x [height] area at ([left], [top]) of the
     * canvas, for element [keyId] (-1 background and strip, [RgbEngine.FIELD_KEYS] under the keys).
     * By default the grid comes from the background (see the class comment); [wait]: computed and
     * a missing tile built on this thread (background threads only: gallery pictures).
     */
    fun update(engine: RgbEngine, left: Float, top: Float, width: Float, height: Float, nowMs: Long, keyId: Int, wait: Boolean = false) {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        val newRows = (COLUMNS * h / w).roundToInt().coerceIn(2, MAX_ROWS)
        if (wait) {
            val grid = IntArray(COLUMNS * newRows)
            computeGrid(engine, grid, newRows, keyId, nowMs)
            show(grid, newRows)
        } else {
            takeReady()
            // the effects depend on the clock only while a tap fades
            val effectNow = if (engine.hasActiveEffect(nowMs)) nowMs else Long.MIN_VALUE
            if (engine !== askedEngine || engine.phase != askedPhase || engine.flow != askedFlow || engine.aspect != askedAspect ||
                newRows != askedRows || keyId != askedKeyId || effectNow != askedNow) ask(engine, newRows, keyId, nowMs, effectNow)
        }
        if (bitmap != null) setArea(left, top, w, h)
        updateTile(engine, left, top, w, h, wait)
    }

    /** Shows the grid the background has finished, if any. */
    private fun takeReady() {
        val grid: IntArray
        val gridRows: Int
        synchronized(lock) {
            grid = ready ?: return
            gridRows = readyRows
            ready = null
            // the shown grid is free now: the next job writes there
            if (pixels.size == COLUMNS * gridRows) spare = pixels
        }
        show(grid, gridRows)
    }

    private fun show(grid: IntArray, gridRows: Int) {
        if (bitmap == null || gridRows != rows) {
            rows = gridRows
            cols = COLUMNS
            bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            fieldShader = bitmapShader(bitmap!!, Shader.TileMode.CLAMP)
            generation++
        }
        pixels = grid
        bitmap!!.setPixels(pixels, 0, cols, 0, 0, cols, rows)
    }

    /** Asks the background for the grid of [engine]'s present state, unless a grid is still being made (then the next update asks). */
    private fun ask(engine: RgbEngine, gridRows: Int, keyId: Int, nowMs: Long, effectNow: Long) {
        val copy: RgbEngine
        val buffer: IntArray
        synchronized(lock) {
            if (inFlight) return
            if (job == null || jobSource !== engine) {
                job = RgbEngine(engine.settings)
                jobSource = engine
            }
            copy = job!!
            buffer = spare?.takeIf { it.size == COLUMNS * gridRows } ?: IntArray(COLUMNS * gridRows)
            spare = null
            inFlight = true
        }
        copy.copyStateFrom(engine)
        askedEngine = engine; askedPhase = engine.phase; askedFlow = engine.flow; askedAspect = engine.aspect
        askedRows = gridRows; askedKeyId = keyId; askedNow = effectNow
        val sink = stats
        RgbWork.run {
            val start = System.nanoTime()
            try {
                computeGrid(copy, buffer, gridRows, keyId, nowMs)
                synchronized(lock) {
                    ready?.let { spare = it }
                    ready = buffer
                    readyRows = gridRows
                    gridsMade++
                }
            } finally {
                synchronized(lock) { inFlight = false }
                sink?.recordBackground(android.os.SystemClock.uptimeMillis(), System.nanoTime() - start)
            }
        }
    }

    private fun computeGrid(engine: RgbEngine, grid: IntArray, gridRows: Int, keyId: Int, nowMs: Long) {
        for (r in 0 until gridRows) for (c in 0 until COLUMNS) {
            val x = (c + 0.5f) / COLUMNS
            val y = (r + 0.5f) / gridRows
            grid[r * COLUMNS + c] = engine.effects(engine.base(x, y), x, y, keyId, nowMs)
        }
    }

    /** Only the tile and its position (keys in one colour each draw no grid, only the detail). */
    fun updateTileOnly(engine: RgbEngine, left: Float, top: Float, width: Float, height: Float) =
        updateTile(engine, left, top, width.coerceAtLeast(1f), height.coerceAtLeast(1f), false)

    private fun setArea(left: Float, top: Float, w: Float, h: Float) {
        areaLeft = left; areaTop = top; areaWidth = w; areaHeight = h
        fieldMatrix.setScale(w / cols, h / rows)
        fieldMatrix.postTranslate(left, top)
        fieldShader!!.setLocalMatrix(fieldMatrix)
        fieldPaint.shader = fieldShader
    }

    /** Copies the grid of [other] (same engine and element): no second computation. */
    fun copyFrom(other: RgbTexture) {
        if (other.bitmap == null) return
        if (bitmap == null || other.rows != rows) {
            rows = other.rows
            cols = other.cols
            bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            fieldShader = bitmapShader(bitmap!!, Shader.TileMode.CLAMP)
            generation++
        }
        if (pixels.size != other.pixels.size) pixels = IntArray(other.pixels.size)
        System.arraycopy(other.pixels, 0, pixels, 0, pixels.size)
        bitmap!!.setPixels(pixels, 0, cols, 0, 0, cols, rows)
        areaLeft = other.areaLeft; areaTop = other.areaTop; areaWidth = other.areaWidth; areaHeight = other.areaHeight
        fieldMatrix.set(other.fieldMatrix)
        fieldShader!!.setLocalMatrix(fieldMatrix)
        fieldPaint.shader = fieldShader
        if (tilePattern != other.tilePattern || tileBitmap !== other.tileBitmap) generation++
        tilePattern = other.tilePattern
        tileShader = other.tileShader
        tileBitmap = other.tileBitmap
        tileMatrix.set(other.tileMatrix)
        tileMean = other.tileMean
        tilePaint.shader = tileShader
    }

    private fun updateTile(engine: RgbEngine, left: Float, top: Float, width: Float, height: Float, wait: Boolean) {
        val pattern = engine.settings.pattern
        // a new pattern, or a tile that was still being built: ask again (cheap while it is pending)
        if (pattern != tilePattern || (tileShader == null && RgbOverlay.hasOverlay(pattern))) {
            val tile = if (wait) RgbTiles.build(pattern) else RgbTiles.ready(pattern)
            if (pattern != tilePattern || tile != null) {
                tilePattern = pattern
                tileShader = tile?.let { bitmapShader(it.bitmap, Shader.TileMode.REPEAT) }
                tileBitmap = tile?.bitmap
                tileMean = tile?.mean ?: 0
                generation++
                tilePaint.shader = tileShader
            }
        }
        val shader = tileShader ?: return
        RgbOverlay.transform(pattern, width, height, engine.phase, engine.settings.reverse, tileValues)
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
        // SparseArray: no boxed key per key and frame
        val own = keyShaders.get(id) ?: KeyShaders().also { keyShaders.put(id, it) }
        if (own.generation != generation) {
            own.field = bitmap?.let { bitmapShader(it, Shader.TileMode.CLAMP) }
            own.tile = tileBitmap?.let { bitmapShader(it, Shader.TileMode.REPEAT) }
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
        if (keyShaders.size() > MAX_KEY_SHADERS) keyShaders.clear()
    }

    /** The grid value at column [c], row [r] (tests: the bilinear expectation). */
    @VisibleForTesting internal fun gridPixel(c: Int, r: Int): Int = pixels[r.coerceIn(0, rows - 1) * cols + c.coerceIn(0, cols - 1)]
    @VisibleForTesting internal val gridColumns get() = cols
    @VisibleForTesting internal val gridRows get() = rows

    private class KeyShaders {
        var field: BitmapShader? = null
        var tile: BitmapShader? = null
        var generation = -1
    }
    private val keyShaders = SparseArray<KeyShaders>()
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

        /**
         * A bitmap shader that always filters bilinearly: from API 33 the shader's own filter mode
         * wins over the paint's flag, so it is set explicitly (P105); before, the paint flag decides.
         */
        fun bitmapShader(bitmap: Bitmap, mode: Shader.TileMode): BitmapShader {
            val shader = BitmapShader(bitmap, mode, mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) shader.filterMode = BitmapShader.FILTER_MODE_LINEAR
            return shader
        }
    }
}

/**
 * Background work of the lighting (build-14 lag): the light grids of [RgbTexture] on their own
 * thread, and the listeners told on the main thread when a grid or a tile ([RgbTiles]) is ready
 * (the keyboard redraws when it has no running frames, the settings preview when it stands still).
 */
object RgbWork {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val defaultExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "rgb-light").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }
    }
    /** Where grids are computed (tests replace it). */
    @VisibleForTesting internal var executor: Executor? = null
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val lock = Object()
    private var pending = 0
    /** The thread the last grid was computed on (tests). */
    @Volatile @VisibleForTesting internal var lastThread: Thread? = null
        private set

    /** Runs [work] on the lighting thread, then tells the listeners. */
    fun run(work: () -> Unit) {
        synchronized(lock) { pending++ }
        (executor ?: defaultExecutor).execute {
            try {
                lastThread = Thread.currentThread()
                work()
            } finally {
                synchronized(lock) { pending--; lock.notifyAll() }
                notifyReady()
            }
        }
    }

    /** Tells every listener on the main thread that something made in the background is ready. */
    fun notifyReady() { main.post { for (l in listeners) l() } }

    fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    /** Waits until no grid is being computed, at most [timeoutMs] (tests). */
    @VisibleForTesting internal fun awaitIdle(timeoutMs: Long = 10_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (pending > 0) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) return false
                lock.wait(left)
            }
        }
        return true
    }
}

/**
 * The detail tiles of the texture patterns (stage 29c), one per pattern, made on a background
 * thread: a big tile takes 20–40 ms on a desktop JVM and more on a phone, too long for a frame on the
 * UI thread. [ready] never blocks: it returns the tile or asks for it and returns null; when a tile
 * is done, [RgbWork]'s listeners are told on the main thread (the keyboard and the settings preview redraw).
 * The cache holds every tiled pattern (five, about 1.3 MB of bitmaps) and only the bitmap and its
 * mean colour, not the pixel array; [clear] empties it (light switched off, low memory).
 */
object RgbTiles {
    class Tile(val bitmap: Bitmap, val mean: Int)

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val lock = Object()
    private val tiles = HashMap<RgbPattern, Tile>()
    private val pending = HashSet<RgbPattern>()
    /** Raised by [clear]: a build that started before is not stored. */
    private var epoch = 0
    private val defaultExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "rgb-tiles").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
    }
    /** Where tiles are built (tests replace it to hold or count builds). */
    @VisibleForTesting internal var executor: Executor? = null
    /** Number of tiles made since start (tests). */
    private val buildCount = java.util.concurrent.atomic.AtomicInteger()
    @VisibleForTesting internal val builds get() = buildCount.get()
    /** The thread the last tile was made on (tests: never the main thread for [ready]). */
    @Volatile @VisibleForTesting internal var lastBuildThread: Thread? = null
        private set

    /** Every pattern with a tile: the cache keeps all of them. */
    val TILED: List<RgbPattern> = RgbPattern.entries.filter { RgbOverlay.hasOverlay(it) }

    /** The tile of [pattern] if it is made; otherwise starts making it in the background and returns null. Never blocks. */
    fun ready(pattern: RgbPattern): Tile? {
        if (!RgbOverlay.hasOverlay(pattern)) return null
        val started: Int
        synchronized(lock) {
            tiles[pattern]?.let { return it }
            if (!pending.add(pattern)) return null
            started = epoch
        }
        (executor ?: defaultExecutor).execute { make(pattern, started) }
        return null
    }

    /** Asks for the tiles of [patterns] ahead of drawing (the keyboard when its setting is read). */
    fun request(patterns: Collection<RgbPattern>) { for (p in patterns) ready(p) }

    /** The tile of [pattern], made on the calling thread if needed: background threads only (gallery pictures, tests). */
    fun build(pattern: RgbPattern): Tile? {
        if (!RgbOverlay.hasOverlay(pattern)) return null
        synchronized(lock) { tiles[pattern]?.let { return it } }
        val tile = makeTile(pattern) ?: return null
        synchronized(lock) { tiles[pattern]?.let { return it }; tiles[pattern] = tile }
        return tile
    }

    private fun make(pattern: RgbPattern, started: Int) {
        val tile = runCatching { makeTile(pattern) }.getOrNull()
        synchronized(lock) {
            pending.remove(pattern)
            if (tile != null && started == epoch) tiles[pattern] = tile
            lock.notifyAll()
        }
        // also after a clear: whoever still draws asks again on the redraw
        RgbWork.notifyReady()
    }

    private fun makeTile(pattern: RgbPattern): Tile? {
        val tile = RgbOverlay.tile(pattern) ?: return null
        val bitmap = Bitmap.createBitmap(tile.pixels, tile.width, tile.height, Bitmap.Config.ARGB_8888)
        lastBuildThread = Thread.currentThread()
        buildCount.incrementAndGet()
        // the pixel array is dropped here: the bitmap and the mean colour are all drawing needs
        return Tile(bitmap, tile.mean)
    }

    /** Drops every tile (bitmaps still in use by a shader stay alive until it is replaced). */
    fun clear() {
        synchronized(lock) {
            tiles.clear()
            epoch++
        }
    }

    /** Patterns whose tile is made (tests). */
    @VisibleForTesting internal fun cached(): Set<RgbPattern> = synchronized(lock) { tiles.keys.toSet() }

    /** Waits until no tile is being made, at most [timeoutMs] (tests). True if none is pending. */
    @VisibleForTesting internal fun awaitIdle(timeoutMs: Long = 10_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (pending.isNotEmpty()) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) return false
                lock.wait(left)
            }
        }
        return true
    }
}
