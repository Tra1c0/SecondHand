// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbRun
import dev.agentkeyboard.core.RgbTarget
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings

/**
 * Applies the RGB light while a [KeyboardView] draws (stage 26, smooth light and textures 29). The
 * view calls, in this order: [beginDraw] once per draw, [drawKeyboardBackground] after its own
 * background, and per key [beginKey], [tintBackground], [drawOverBackground] right after the key
 * background, [restoreBackground], [labelColor] for label, hint (also hint icons and the popup
 * hint) colours and [tintIcon]/[restoreIcon] around the icon.
 *
 * Each element has its own engine ([RgbLighting.engineFor]): key backgrounds [RgbTarget.KEYS],
 * keyboard background [RgbTarget.BACKGROUND], labels [RgbTarget.LABELS].
 *
 * Key backgrounds: by default (29b) the key shape is filled with the smooth light of the keys
 * ([RgbTexture], bilinear over the whole keyboard) through an alpha mask of the key drawable, so the
 * colours run across the keys without steps; with [dev.agentkeyboard.core.RgbSettings.solidKeys]
 * every key gets one colour (its centre) by a colour filter, as in stage 26. Texture patterns add
 * their detail tile through the same mask.
 *
 * Cost per frame: one grid per lit element (48 x up to 32 light values; shared when keys and
 * background use the same engine), one light value per key and element, a few draw calls per key.
 * Masks are made once per key drawable and size and cached.
 */
class RgbKeyPainter {
    private var width = 1
    private var height = 1
    private lateinit var colors: Colors
    private var keyboardLayer = false
    private var backgroundLayer = false
    private var labelLayer = false
    private var solid = false
    private var now = 0L
    private lateinit var keysEngine: RgbEngine
    private lateinit var backEngine: RgbEngine
    private lateinit var labelEngine: RgbEngine

    // current key
    private var lit = false
    private var keyBackgroundLit = false
    private var labelLit = false
    private var keyId = 0
    private var keyX = 0
    private var keyY = 0
    private var keyLight = 0
    private var labelLight = 0
    private var keyTheme = 0
    private var behind = 0
    private var tinted: Drawable? = null
    private var tintedFilter: ColorFilter? = null
    private var tintedIcon: Drawable? = null
    private var tintedIconFilter: ColorFilter? = null

    /** Light behind and between the keys, and the smooth light under the keys. */
    private val background = RgbTexture().apply { stats = RgbLighting.frameStats }
    private val keys = RgbTexture().apply { stats = RgbLighting.frameStats }
    /** UI-thread lighting work of the frame being drawn (prepare plus per-key calls), recorded at [endDraw]. */
    private var frameNanos = 0L
    private var drawStart = 0L
    private var measuring = false
    private var backgroundDrawn = false
    private var keysReady = false
    private val masks = RgbKeyMasks()
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun beginDraw(view: KeyboardView) {
        val start = System.nanoTime()
        drawStart = start
        frameNanos = 0L
        try { prepare(view) } finally {
            frameNanos += System.nanoTime() - start
            measuring = backgroundLayer || keyboardLayer || labelLayer
        }
    }

    /**
     * End of the keyboard's draw (hook at the end of KeyboardView.onDrawKeyboard): records the
     * lighting share of this frame and the whole keyboard draw ([RgbLighting.frameWork]).
     */
    fun endDraw() {
        if (!measuring) return
        measuring = false
        RgbLighting.frameStats.recordUi(android.os.SystemClock.uptimeMillis(), frameNanos, System.nanoTime() - drawStart)
    }

    private fun prepare(view: KeyboardView) {
        width = view.width.coerceAtLeast(1)
        height = view.height.coerceAtLeast(1)
        colors = Settings.getValues().mColors
        val s = RgbLighting.settings
        val on = s.enabled && RgbLighting.mode != RgbRun.Mode.OFF
        keyboardLayer = on && s.lights(RgbTarget.BACKGROUND)
        backgroundLayer = on && s.lights(RgbTarget.KEYS)
        labelLayer = on && s.lights(RgbTarget.LABELS)
        solid = s.solidKeys
        now = RgbLighting.drawTimeMs
        RgbLighting.engines.aspect = height.toFloat() / width
        keysEngine = RgbLighting.engineFor(RgbTarget.KEYS)
        backEngine = RgbLighting.engineFor(RgbTarget.BACKGROUND)
        labelEngine = RgbLighting.engineFor(RgbTarget.LABELS)
        backgroundDrawn = keyboardLayer
        if (keyboardLayer) background.update(backEngine, 0f, 0f, width.toFloat(), height.toFloat(), now, -1)
        keysReady = backgroundLayer
        if (backgroundLayer) {
            // one colour per key: the grid under the keys is never drawn, only the tile
            if (solid) keys.updateTileOnly(keysEngine, 0f, 0f, width.toFloat(), height.toFloat())
            // the same engine and no per-key reactive flash: the keys use the background's grid
            else if (keyboardLayer && keysEngine === backEngine && keysEngine.keyFactor(-1, now) == 1f) keys.copyFrom(background)
            else keys.update(keysEngine, 0f, 0f, width.toFloat(), height.toFloat(), now, RgbEngine.FIELD_KEYS)
        }
    }

    /** Draws the lit keyboard background (its alpha is the strength) over what is already there. */
    fun drawKeyboardBackground(canvas: Canvas) {
        if (!backgroundDrawn) return
        background.drawRect(canvas, 0f, 0f, width.toFloat(), height.toFloat(), backEngine.overlayStrength(now))
    }

    /** [x], [y]: top left of the key in view coordinates. */
    fun beginKey(key: Key, x: Int, y: Int) {
        val start = System.nanoTime()
        try { startKey(key, x, y) } finally { frameNanos += System.nanoTime() - start }
    }

    private fun startKey(key: Key, x: Int, y: Int) {
        val area = areaOf(key)
        val inArea = area != null && area in RgbLighting.settings.areas
        keyBackgroundLit = inArea && backgroundLayer
        labelLit = inArea && labelLayer
        // a lit keyboard background alone also needs readable labels (keys without borders)
        lit = area != null && (keyBackgroundLit || labelLit || keyboardLayer)
        if (!lit) return
        keyId = keyId(key)
        keyX = x
        keyY = y
        val cx = (x + key.drawWidth / 2f) / width
        val cy = (y + key.height / 2f) / height
        keyLight = if (keyBackgroundLit) keysEngine.light(cx, cy, keyId, now) else 0
        labelLight = if (labelLit) labelEngine.light(cx, cy, keyId, now) else 0
        keyTheme = themeBackground(key)
        var keyboardBack = colors.get(ColorType.MAIN_BACKGROUND).let {
            if (keyboardLayer) RgbColors.applyLight(it or BLACK, backEngine.light(cx, cy, -1, now)) else it
        }
        if (keyboardLayer && background.hasTile) keyboardBack = RgbColors.over(scaleAlpha(background.tileMean, backEngine.overlayStrength(now)), keyboardBack or BLACK)
        val keyBack = if (keyBackgroundLit) RgbColors.applyLight(keyTheme, keyLight) else keyTheme
        behind = RgbColors.over(keyBack, keyboardBack)
        // the detail of a texture over the key counts with its average colour
        if (keyBackgroundLit && keys.hasTile && RgbColors.alpha(keyLight) > 0)
            behind = RgbColors.over(scaleAlpha(keys.tileMean, RgbColors.alpha(keyLight) / 255f), behind)
    }

    fun tintBackground(background: Drawable) {
        if (!lit || !keyBackgroundLit || !solid || RgbColors.alpha(keyLight) == 0) return
        // the selected child of a state list, never the container: a container keeps a filter
        // for all children it selects later and would drop their tint (see ARCHITECTURE P60)
        val target = background.current ?: return
        var color = RgbColors.applyLight(keyTheme, keyLight)
        if (android.R.attr.state_pressed in background.state) color = RgbColors.pressed(color)
        tinted = target
        tintedFilter = target.colorFilter
        target.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    /**
     * Called right after the key background was drawn at ([bgX], [bgY]) relative to the key, with
     * the canvas translated to that point: the smooth light (unless keys are solid), tap effects
     * and the texture detail, all through the alpha mask of the key drawable.
     */
    fun drawOverBackground(canvas: Canvas, background: Drawable, bgX: Int, bgY: Int) {
        if (!lit || !keyBackgroundLit) return
        val start = System.nanoTime()
        try { drawLight(canvas, background, bgX, bgY) } finally { frameNanos += System.nanoTime() - start }
    }

    private fun drawLight(canvas: Canvas, background: Drawable, bgX: Int, bgY: Int) {
        val strength = RgbColors.alpha(keyLight) / 255f
        val fieldAlpha = if (solid) 0f else keysEngine.keyFactor(keyId, now)
        val tileAlpha = if (keys.hasTile) strength else 0f
        val highlight = if (solid) 0f else keysEngine.keyHighlight(keyId, now)
        val pressed = !solid && android.R.attr.state_pressed in background.state && (fieldAlpha > 0f || strength > 0f)
        if (fieldAlpha <= 0f && tileAlpha <= 0f && highlight <= 0f && !pressed) return
        val mask = masks.maskOf(background) ?: return
        // the canvas is at the top left of the key background: the mask goes at (0, 0)
        val left = (keyX + bgX).toFloat()
        val top = (keyY + bgY).toFloat()
        keys.drawMask(canvas, mask, keyId, left, top, fieldAlpha, tileAlpha)
        if (highlight > 0f) {
            effectPaint.color = Color.WHITE
            effectPaint.alpha = (highlight * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(mask, 0f, 0f, effectPaint)
        }
        if (pressed) {
            // the same feedback as RgbColors.pressed: lighter on dark light, darker on bright light
            val bright = RgbColors.luminance(RgbColors.applyLight(keyTheme or BLACK, keyLight)) > 0.4
            effectPaint.color = if (bright) Color.BLACK else Color.WHITE
            effectPaint.alpha = if (bright) 64 else 77
            canvas.drawBitmap(mask, 0f, 0f, effectPaint)
        }
    }

    fun restoreBackground() {
        val target = tinted ?: return
        target.colorFilter = tintedFilter
        tinted = null
        tintedFilter = null
    }

    /**
     * Colour of a label, hint or icon of the current key: lit with the label light if labels are a
     * layer, and always readable against the (possibly lit) key background. Alpha of [themeColor] is kept.
     */
    fun labelColor(themeColor: Int): Int {
        if (!lit) return themeColor
        return RgbColors.label(themeColor, labelLight, labelLit, behind)
    }

    /**
     * [themeColor]: the colour the view has just given [icon] (KeyboardView.setKeyIconColor: action,
     * shift (accent while shifted), key icon or key text colour). Without the label layer the icon
     * keeps exactly that colour as long as it is readable; with it, the light is based on it.
     */
    fun tintIcon(icon: Drawable?, themeColor: Int) {
        if (icon == null || !lit) return
        // icons are coloured by a filter or a tint from the theme; replace that only when needed
        val color = labelColor(themeColor)
        if (!labelLit && color == themeColor) return
        tintedIcon = icon
        tintedIconFilter = icon.colorFilter
        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun restoreIcon() {
        val icon = tintedIcon ?: return
        icon.colorFilter = tintedIconFilter
        tintedIcon = null
        tintedIconFilter = null
    }

    private fun themeBackground(key: Key): Int {
        val type = when (key.backgroundType) {
            Key.BACKGROUND_TYPE_FUNCTIONAL -> ColorType.FUNCTIONAL_KEY_BACKGROUND
            Key.BACKGROUND_TYPE_ACTION -> ColorType.ACTION_KEY_BACKGROUND
            Key.BACKGROUND_TYPE_SPACEBAR -> ColorType.SPACE_BAR_BACKGROUND
            else -> ColorType.KEY_BACKGROUND
        }
        // without key borders the keys have no visible background (except Enter)
        if (!colors.hasKeyBorders && type != ColorType.ACTION_KEY_BACKGROUND) return Color.TRANSPARENT
        return colors.get(type)
    }

    private fun scaleAlpha(color: Int, factor: Float) =
        (((color ushr 24) * factor.coerceIn(0f, 1f)).toInt() shl 24) or (color and 0xFFFFFF)

    /** Number of cached key masks (tests). */
    internal val maskCount get() = masks.size
    /** The light under the keys and behind them (tests). */
    internal val keysTexture get() = keys
    internal val backgroundTexture get() = background

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        /** Columns of the light grid (stage 26: 24, now [RgbTexture.COLUMNS]). */
        const val GRID_COLUMNS = RgbTexture.COLUMNS
        const val GRID_MAX_ROWS = RgbTexture.MAX_ROWS

        /** Stable id of a key within its keyboard, for flash effects. */
        fun keyId(key: Key) = System.identityHashCode(key) and 0x7FFFFFFF

        /** Which area a key belongs to; null for keys that are never lit (spacers). */
        fun areaOf(key: Key): RgbArea? {
            if (key.isSpacer) return null
            val code = key.code
            return when {
                code == Constants.CODE_SPACE || key.backgroundType == Key.BACKGROUND_TYPE_SPACEBAR -> RgbArea.SPACE
                code in '0'.code..'9'.code -> RgbArea.NUMBER_ROW
                key.backgroundType == Key.BACKGROUND_TYPE_FUNCTIONAL || key.backgroundType == Key.BACKGROUND_TYPE_ACTION ||
                    key.isShift || code == ','.code || code == '.'.code -> RgbArea.FUNCTIONAL
                else -> RgbArea.LETTERS
            }
        }
    }
}

/**
 * Alpha masks of key backgrounds (stage 29): the shape of the selected child of a key drawable at
 * its current size, as an ALPHA_8 bitmap, drawn with a shader to fill exactly that shape. Made once
 * per drawable and size; the drawable is drawn with a white SRC_IN filter so the theme colour (even a
 * transparent one) does not change the shape, then its own filter comes back (P60: on the child).
 */
internal class RgbKeyMasks {
    private class Entry(val drawable: Drawable, val width: Int, val height: Int, val mask: Bitmap)
    private val entries = ArrayList<Entry>()
    val size get() = entries.size

    fun maskOf(background: Drawable): Bitmap? {
        val target = background.current ?: return null
        val bounds = target.bounds
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0) return null
        for (i in entries.indices) {
            val e = entries[i]
            if (e.drawable === target && e.width == w && e.height == h) return e.mask
        }
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)
        val filter = target.colorFilter
        target.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
        target.draw(canvas)
        target.colorFilter = filter
        entries.add(Entry(target, w, h, mask))
        if (entries.size > MAX_MASKS) entries.removeAt(0)
        return mask
    }

    private companion object {
        const val MAX_MASKS = 32
    }
}
