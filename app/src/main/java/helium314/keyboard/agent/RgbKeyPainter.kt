// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.RgbLayer
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import kotlin.math.roundToInt

/**
 * Applies the RGB light while a [KeyboardView] draws (stage 26). The view calls, in this order:
 * [beginDraw] once per draw, [drawKeyboardBackground] after its own background, and per key
 * [beginKey], [tintBackground]/[restoreBackground] around the key background, [labelColor] for
 * label, hint (also hint icons and the popup hint) colours and [tintIcon]/[restoreIcon] around the icon.
 *
 * Cost per frame: one light value per key, one per cell of a small grid for the keyboard
 * background (at most 24 x 16 pixels, scaled up with filtering), and a colour filter object per
 * lit key. Shared drawables get their own colour filter back right after each key.
 */
class RgbKeyPainter {
    private var width = 1
    private var height = 1
    private lateinit var colors: Colors
    private var keyboardLayer = false
    private var backgroundLayer = false
    private var labelLayer = false

    // current key
    private var lit = false
    private var keyBackgroundLit = false
    private var labelLit = false
    private var keyLight = 0
    private var keyTheme = 0
    private var behind = 0
    private var tinted: Drawable? = null
    private var tintedFilter: ColorFilter? = null
    private var tintedIcon: Drawable? = null
    private var tintedIconFilter: ColorFilter? = null

    // keyboard background grid
    private var grid: Bitmap? = null
    private var gridPixels = IntArray(0)
    private val gridPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gridDst = Rect()

    fun beginDraw(view: KeyboardView) {
        width = view.width.coerceAtLeast(1)
        height = view.height.coerceAtLeast(1)
        colors = Settings.getValues().mColors
        val s = RgbLighting.settings
        val on = s.enabled && RgbLighting.mode != dev.agentkeyboard.core.RgbRun.Mode.OFF
        keyboardLayer = on && RgbLayer.KEYBOARD_BACKGROUND in s.layers
        backgroundLayer = on && RgbLayer.KEY_BACKGROUND in s.layers
        labelLayer = on && RgbLayer.LABEL in s.layers
        RgbLighting.engine.aspect = height.toFloat() / width
        if (keyboardLayer) fillGrid() else grid = null
    }

    private fun fillGrid() {
        val cols = GRID_COLUMNS
        val rows = (cols * height.toFloat() / width).roundToInt().coerceIn(2, GRID_MAX_ROWS)
        val bitmap = grid?.takeIf { it.width == cols && it.height == rows }
            ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).also {
                grid = it
                gridPixels = IntArray(cols * rows)
            }
        val engine = RgbLighting.engine
        val now = RgbLighting.drawTimeMs
        for (r in 0 until rows) for (c in 0 until cols)
            gridPixels[r * cols + c] = engine.light((c + 0.5f) / cols, (r + 0.5f) / rows, -1, now)
        bitmap.setPixels(gridPixels, 0, cols, 0, 0, cols, rows)
    }

    /** Draws the lit keyboard background (its alpha is the strength) over what is already there. */
    fun drawKeyboardBackground(canvas: Canvas) {
        val bitmap = grid ?: return
        gridDst.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, gridDst, gridPaint)
    }

    /** [x], [y]: top left of the key in view coordinates. */
    fun beginKey(key: Key, x: Int, y: Int) {
        val area = areaOf(key)
        val inArea = area != null && area in RgbLighting.settings.areas
        keyBackgroundLit = inArea && backgroundLayer
        labelLit = inArea && labelLayer
        // a lit keyboard background alone also needs readable labels (keys without borders)
        lit = area != null && (keyBackgroundLit || labelLit || keyboardLayer)
        if (!lit) return
        val cx = (x + key.drawWidth / 2f) / width
        val cy = (y + key.height / 2f) / height
        val now = RgbLighting.drawTimeMs
        keyLight = if (keyBackgroundLit || labelLit) RgbLighting.engine.light(cx, cy, keyId(key), now) else 0
        keyTheme = themeBackground(key)
        val keyboardBack = colors.get(ColorType.MAIN_BACKGROUND).let {
            if (keyboardLayer) RgbColors.applyLight(it or BLACK, RgbLighting.engine.light(cx, cy, -1, now)) else it
        }
        val keyBack = if (keyBackgroundLit) RgbColors.applyLight(keyTheme, keyLight) else keyTheme
        behind = RgbColors.over(keyBack, keyboardBack)
    }

    fun tintBackground(background: Drawable) {
        if (!lit || !keyBackgroundLit || RgbColors.alpha(keyLight) == 0) return
        // the selected child of a state list, never the container: a container keeps a filter
        // for all children it selects later and would drop their tint (see ARCHITECTURE P60)
        val target = background.current ?: return
        var color = RgbColors.applyLight(keyTheme, keyLight)
        if (android.R.attr.state_pressed in background.state) color = RgbColors.pressed(color)
        tinted = target
        tintedFilter = target.colorFilter
        target.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun restoreBackground() {
        val target = tinted ?: return
        target.colorFilter = tintedFilter
        tinted = null
        tintedFilter = null
    }

    /**
     * Colour of a label, hint or icon of the current key: lit with the light if labels are a layer,
     * and always readable against the (possibly lit) key background. Alpha of [themeColor] is kept.
     */
    fun labelColor(themeColor: Int): Int {
        if (!lit) return themeColor
        return RgbColors.label(themeColor, keyLight, labelLit, behind)
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

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        const val GRID_COLUMNS = 24
        const val GRID_MAX_ROWS = 16

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
