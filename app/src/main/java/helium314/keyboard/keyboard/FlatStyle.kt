// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.drawable.DrawableCompat
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.AllColors
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors
import java.util.EnumMap
import java.util.Locale

/**
 * Everything that makes up the "Flat" key style besides its XML resources: solid keys without
 * bevel, pills for the action and the layout switch key, a shorter number row and its own icons
 * and colours. All numbers were measured on reference screenshots that are 1200 px wide with a
 * keyboard view of 793 px (39 px top padding, 722 px rows, 32 px bottom padding), so they are
 * stored as ratios.
 *
 * Upstream classes only call into this object, which keeps their own changes to single lines.
 */
object FlatStyle {
    /**
     * A single-line label that does not fit scrolls through like a ticker, as in the reference.
     * The ticker runs only while the view is selected; TalkBack is told it is not, so that it does
     * not announce every pill as "selected". With animations turned off in the system settings
     * the label is cut with an ellipsis instead.
     */
    @JvmStatic
    fun scrollWhenTooLong(view: android.widget.TextView) {
        view.isSingleLine = true
        if (!animationsEnabled(view.context)) {
            view.ellipsize = android.text.TextUtils.TruncateAt.END
            return
        }
        view.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        view.marqueeRepeatLimit = -1
        view.isSelected = true
        view.accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isSelected = false
            }
        }
    }

    /** False when the owner turned animations off (developer options or "remove animations"). */
    @JvmStatic
    fun animationsEnabled(context: android.content.Context): Boolean =
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    /** Height of the keyboard view (top padding to bottom padding) relative to its width: 793 / 1200. */
    const val HEIGHT_TO_WIDTH = 0.6609f

    /** Side padding setting that turns the common 8 % side padding into the 7 px of 1200 of the reference. */
    const val SIDE_PADDING_SCALE = 0.073f

    /** Bottom row scale that turns five equal 149.6 px rows into a 160 px bottom row (a little more, heights are truncated). */
    const val BOTTOM_ROW_SCALE = 1.07f

    /** Number row height before the letter rows take up the slack: 139 px beside 149.67 px rows. */
    private const val NUMBER_ROW_SCALE = 0.947f

    /** The gap below the number row is 22 px instead of 26 px. */
    private const val NUMBER_ROW_GAP_SCALE = 22f / 26f

    private const val LOWERCASE_LIFT = 0.085f

    /** Bullet between the language codes on the space bar, with a thin space on each side of the normal one. */
    const val LANGUAGE_SEPARATOR = " \u2009\u2022\u2009 "

    const val BACKGROUND = Color.BLACK
    val KEY = Color.rgb(0x4C, 0x4C, 0x4C)
    val FUNCTIONAL_KEY = Color.rgb(0x0D, 0x0D, 0x0D)
    val ACCENT = Color.rgb(0x6A, 0x96, 0xEF)
    const val LABEL = Color.WHITE
    val HINT = Color.rgb(0xDB, 0xDB, 0xDB)
    val FUNCTIONAL_LABEL = Color.rgb(0xB7, 0xB7, 0xB7)
    val TOOLBAR_ICON = Color.rgb(0x99, 0x99, 0x99)
    /** A toolbar switch that is off. */
    val TOOLBAR_ICON_OFF = Color.rgb(0x4D, 0x4D, 0x4D)
    /** Round background and glyph of the key at the right end of the strip. */
    val STRIP_CIRCLE = Color.rgb(0x4B, 0x4B, 0x4B)
    val STRIP_CIRCLE_ICON = Color.rgb(0xC9, 0xC9, 0xC9)

    /** Icons that replace the Material ones in this style. Their intrinsic sizes are the measured ones. */
    @JvmField
    val iconOverrides: Map<String, Int> = mapOf(
        "shift_key" to R.drawable.sym_keyboard_shift_flat,
        "shift_key_shifted" to R.drawable.sym_keyboard_shifted_flat,
        "shift_key_locked" to R.drawable.sym_keyboard_shift_lock_flat,
        "delete_key" to R.drawable.sym_keyboard_delete_flat,
        "enter_key" to R.drawable.sym_keyboard_return_flat,
        "go_key" to R.drawable.sym_keyboard_go_flat,
        "emoji" to R.drawable.sym_keyboard_smiley_flat,
        // strip above the keys: left key, clipboard, panel and dictation
        "toolbar_key" to R.drawable.sym_keyboard_grid_flat,
        "clipboard" to R.drawable.sym_keyboard_clipboard_flat,
        "agent" to R.drawable.sym_keyboard_pen_flat,
        "dictate" to R.drawable.sym_keyboard_mic_flat,
    )

    /**
     * Medium weight of the system font. The reference strokes lie between regular and medium (stem
     * 9.05 px at a cap height of 58.6 px; Roboto has 7.5 px at 400 and 9.8 px at 500), medium is the
     * nearer one. A weight in between is not requested because a font with static weights resolves
     * it unpredictably.
     */
    @JvmStatic
    val labelTypeface: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    @JvmStatic
    fun isActive(colors: Colors): Boolean = colors.themeStyle == KeyboardTheme.STYLE_FLAT

    /** Labels, hints and the space bar text use [labelTypeface] in this style. */
    @JvmStatic
    fun typeface(colors: Colors, fallback: Typeface): Typeface = if (isActive(colors)) labelTypeface else fallback

    /**
     * A single lowercase letter sits a little higher than a capital letter or digit, which are
     * centred on their cap height: measured 5 px at a cap height of 58.6 px.
     */
    @JvmStatic
    fun lowercaseLift(colors: Colors, label: String, capHeight: Float): Float {
        if (!isActive(colors) || label.length != 1 || !Character.isLowerCase(label[0])) return 0f
        return capHeight * LOWERCASE_LIFT
    }

    /**
     * Makes the first row, which is the number row, lower and gives the difference to the rows
     * between it and the bottom row. The sum of all row heights does not change.
     */
    @JvmStatic
    fun shortenNumberRow(rows: List<List<KeyParams>>) {
        val middleRows = rows.size - 2
        if (middleRows < 1) return
        val middleScale = (middleRows + 1 - NUMBER_ROW_SCALE) / middleRows
        rows.forEachIndexed { index, row ->
            when (index) {
                0 -> row.forEach {
                    it.mHeight *= NUMBER_ROW_SCALE
                    it.mVerticalGapScale = NUMBER_ROW_GAP_SCALE
                }
                rows.lastIndex -> Unit
                else -> row.forEach { it.mHeight *= middleScale }
            }
        }
    }

    /** The built-in colour set. Every colour type is filled, the listed ones differ from [DefaultColors]. */
    fun colors(themeStyle: String, hasBorders: Boolean, backgroundImage: Drawable?): Colors {
        val base = DefaultColors(
            themeStyle, hasBorders,
            accent = ACCENT, background = BACKGROUND, keyBackground = KEY, functionalKey = FUNCTIONAL_KEY,
            spaceBar = KEY, keyText = LABEL, keyHintText = HINT, suggestionText = LABEL, spaceBarText = LABEL,
        )
        // without key backgrounds only the palette applies; AllColors would paint every key opaque
        if (!hasBorders) return base
        val map = EnumMap<ColorType, Int>(ColorType::class.java)
        ColorType.entries.forEach { map[it] = base.get(it) }
        map[ColorType.MAIN_BACKGROUND] = BACKGROUND
        map[ColorType.STRIP_BACKGROUND] = BACKGROUND
        map[ColorType.NAVIGATION_BAR] = BACKGROUND
        map[ColorType.KEY_BACKGROUND] = KEY
        map[ColorType.SPACE_BAR_BACKGROUND] = KEY
        map[ColorType.FUNCTIONAL_KEY_BACKGROUND] = FUNCTIONAL_KEY
        map[ColorType.ACTION_KEY_BACKGROUND] = ACCENT
        map[ColorType.ACTION_KEY_ICON] = LABEL
        map[ColorType.FUNCTIONAL_KEY_TEXT] = FUNCTIONAL_LABEL
        map[ColorType.KEY_ICON] = FUNCTIONAL_LABEL
        // keeps the setting "accent colour for the shifted icon"
        map[ColorType.SHIFT_KEY_ICON] = if (base.accentShiftedIcon) ACCENT else FUNCTIONAL_LABEL
        map[ColorType.TOOL_BAR_KEY] = TOOLBAR_ICON
        map[ColorType.TOOL_BAR_EXPAND_KEY] = TOOLBAR_ICON
        return ToggleAwareColors(AllColors(map, themeStyle, hasBorders, backgroundImage))
    }

    /**
     * AllColors tints every icon the same whether its key is activated or not. Toolbar keys that
     * are switches (incognito, one-handed, autocorrect, split) need a visible off state.
     */
    private class ToggleAwareColors(private val all: Colors) : Colors by all {
        private val toolbarKeyTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
            intArrayOf(TOOLBAR_ICON, TOOLBAR_ICON_OFF)
        )

        override fun setColor(view: ImageView, color: ColorType) {
            val drawable = view.drawable
            if (color != ColorType.TOOL_BAR_KEY || drawable == null) return all.setColor(view, color)
            DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
            DrawableCompat.setTintList(drawable, toolbarKeyTint)
            // a drawable only becomes stateful with the tint list and would keep its old, empty state
            drawable.state = view.drawableState
        }
    }

    /**
     * Short codes of the keyboard language and the additional languages for the space bar,
     * e.g. "DE • EN • ES".
     */
    @JvmStatic
    fun languageCodes(main: Locale, secondary: List<Locale>, display: Locale): String =
        (listOf(main) + secondary).joinToString(LANGUAGE_SEPARATOR) { it.language.uppercase(display) }
}
