// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Flat style and its preset. The geometry test pins the key positions measured on the
 * reference screenshot: 1200 px wide, keyboard view 793 px high, measured from the top of that view
 * (39 px above the first row).
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FlatStyleTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val prefs get() = latinIME.prefs()
    private val preset get() = FlatPreset(latinIME)

    @Before fun clear() {
        prefs.edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        reloadSettings()
    }

    private fun reloadSettings() {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    @Test fun presetSetsTheLookAndRestoreBringsBackThePreviousValues() {
        prefs.edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_ROUNDED)
            .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
            .putFloat(Settings.PREF_FONT_SCALE, 1.25f).commit()
        assertFalse(preset.canRestore)

        preset.apply()
        assertEquals(KeyboardTheme.STYLE_FLAT, prefs.getString(Settings.PREF_THEME_STYLE, null))
        assertEquals(KeyboardTheme.THEME_FLAT_BLACK, prefs.getString(Settings.PREF_THEME_COLORS, null))
        assertEquals(KeyboardTheme.THEME_FLAT_BLACK, prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false))
        assertEquals(1f, prefs.getFloat(Settings.PREF_FONT_SCALE, 0f))
        assertEquals(FlatPreset.FUNCTIONAL_LAYOUT, prefs.getString("layout_FUNCTIONAL", null))
        assertEquals(FlatStyle.BOTTOM_ROW_SCALE, prefs.getFloat("bottom_row_scale_false_false", 0f))
        assertEquals(1f, prefs.getFloat("side_padding_scale_false_false_false", 0f))
        assertTrue(prefs.contains("keyboard_height_scale_false_false"))
        // strip: three tools between the left key and the pinned dictation key
        assertEquals(listOf(ToolbarKey.CLIPBOARD, ToolbarKey.EMOJI, ToolbarKey.AGENT), getEnabledToolbarKeys(prefs))
        assertEquals(listOf(ToolbarKey.DICTATE), getPinnedToolbarKeys(prefs))
        assertEquals(ToolbarMode.EXPANDABLE, Settings.readToolbarMode(prefs))
        assertTrue(prefs.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, false))
        assertTrue(prefs.getBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, false))
        assertTrue(preset.canRestore)

        preset.restore()
        assertEquals(KeyboardTheme.STYLE_ROUNDED, prefs.getString(Settings.PREF_THEME_STYLE, null))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true))
        assertEquals(1.25f, prefs.getFloat(Settings.PREF_FONT_SCALE, 0f))
        // values that were not set before are removed again, not written as defaults
        preset.values().keys.filterNot { it in setOf(Settings.PREF_THEME_STYLE, Settings.PREF_SHOW_NUMBER_ROW, Settings.PREF_FONT_SCALE) }
            .forEach { assertFalse(prefs.contains(it), it) }
        assertFalse(preset.canRestore)
    }

    @Test fun applyingTwiceKeepsTheFirstBackup() {
        prefs.edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_HOLO).commit()
        preset.apply()
        preset.apply()
        preset.restore()
        assertEquals(KeyboardTheme.STYLE_HOLO, prefs.getString(Settings.PREF_THEME_STYLE, null))
    }

    @Test fun presetDoesNotTouchLearningHistoryOrOtherPreferences() {
        prefs.edit().putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, false)
            .putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false)
            .putString("unrelated", "kept").commit()
        val before = prefs.all.filterKeys { it !in preset.values().keys }
        preset.apply()
        assertEquals(before, prefs.all.filterKeys { it !in preset.values().keys })
        preset.restore()
        assertEquals(before, prefs.all)
    }

    @Test fun heightScaleMakesTheKeyboardSixtySixPercentOfTheWidth() {
        // reference phone: 1200 x 2670 px at 3.25 px per dp, default height 205.6 dp
        val scale = FlatPreset.heightScale(1200, 2670, 205.6f * 3.25f, 0.46f, -0.618f)
        assertEquals(793, Math.round(888 * scale))
        // the same proportion on a 1080 x 2400 px screen at 2.625 px per dp
        val other = FlatPreset.heightScale(1080, 2400, 205.6f * 2.625f, 0.46f, -0.618f)
        assertEquals(714, Math.round(717 * other))
        // out-of-range results are limited to the range of the height setting
        assertEquals(1.5f, FlatPreset.heightScale(4000, 4100, 100f, 0.46f, 0.1f))
        assertEquals(0.3f, FlatPreset.heightScale(100, 9000, 2000f, 0.46f, 0.1f))
    }

    @Test fun flatStyleAlwaysResolvesToItsOwnThemeAndMeasuredColours() {
        prefs.edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_FLAT)
            .putBoolean(Settings.PREF_THEME_KEY_BORDERS, false).commit()
        assertEquals(KeyboardTheme.THEME_ID_FLAT, KeyboardTheme.getKeyboardTheme(latinIME).themeId)
        assertEquals(R.style.KeyboardTheme_Flat, KeyboardTheme.getKeyboardTheme(latinIME).mStyleId)
        assertTrue(KeyboardTheme.STYLE_FLAT in KeyboardTheme.STYLES)
        assertTrue(KeyboardTheme.THEME_FLAT_BLACK in KeyboardTheme.getAvailableDefaultColors(prefs, true))

        val colors = FlatStyle.colors(KeyboardTheme.STYLE_FLAT, true, null)
        fun hex(type: ColorType) = String.format("#%06X", colors.get(type) and 0xFFFFFF)
        assertEquals("#000000", hex(ColorType.MAIN_BACKGROUND))
        assertEquals("#000000", hex(ColorType.STRIP_BACKGROUND))
        assertEquals("#000000", hex(ColorType.NAVIGATION_BAR))
        assertEquals("#4C4C4C", hex(ColorType.KEY_BACKGROUND))
        assertEquals("#4C4C4C", hex(ColorType.SPACE_BAR_BACKGROUND))
        assertEquals("#0D0D0D", hex(ColorType.FUNCTIONAL_KEY_BACKGROUND))
        assertEquals("#6A96EF", hex(ColorType.ACTION_KEY_BACKGROUND))
        assertEquals("#FFFFFF", hex(ColorType.KEY_TEXT))
        assertEquals("#FFFFFF", hex(ColorType.ACTION_KEY_ICON))
        assertEquals("#FFFFFF", hex(ColorType.SPACE_BAR_TEXT))
        assertEquals("#DBDBDB", hex(ColorType.KEY_HINT_TEXT))
        assertEquals("#B7B7B7", hex(ColorType.FUNCTIONAL_KEY_TEXT))
        assertEquals("#B7B7B7", hex(ColorType.KEY_ICON))
        assertEquals("#B7B7B7", hex(ColorType.SHIFT_KEY_ICON))
        // every colour type is filled, none falls back to the hash-based placeholder
        ColorType.entries.forEach { assertEquals(0xFF, colors.get(it) ushr 24 or 0xFF, it.name) }
        assertTrue(FlatStyle.isActive(colors))
    }

    @Test fun keyGeometryMatchesTheReferenceScreenshot() {
        preset.apply()
        reloadSettings()
        val keyboard = buildKeyboard(1200, 793)
        fun key(label: String) = keyboard.sortedKeys.single { it.label == label }
        fun key(code: Int) = keyboard.sortedKeys.single { it.code == code }
        fun assertNear(expected: Int, actual: Int, what: String) =
            assertTrue(abs(expected - actual) <= 1, "$what: expected $expected, was $actual")

        // rows: top edge and height of a key, reference values relative to the top of the keyboard view
        listOf(key("1") to (39 to 117), key("q") to (178 to 123), key("a") to (327 to 124),
            key("y") to (477 to 124), key(Constants.CODE_SPACE) to (627 to 134)).forEach { (key, expected) ->
            assertNear(expected.first, key.y, "top of ${key.label}")
            assertNear(expected.second, key.height, "height of ${key.label}")
        }
        // the last row ends 32 px above the bottom edge
        assertNear(793 - 32, key(Constants.CODE_SPACE).y + key(Constants.CODE_SPACE).height, "bottom edge")

        // columns: ten 118.6 px cells from x = 7, keys 16 px apart
        assertNear(15, key("q").x, "left edge of q")
        assertNear(102, key("q").width, "width of q")
        assertNear(1200 - 15, key("p").x + key("p").width, "right edge of p")
        assertNear(16, key("w").x - (key("q").x + key("q").width), "gap")
        assertNear(74, key("a").x, "left edge of a")
        // 1.5 cells; the reference rounds them to 161 px on the left and 163 px on the right edge
        assertNear(162, key(KeyCode.SHIFT).width, "shift")
        assertNear(162, key(KeyCode.DELETE).width, "delete")
        assertNear(459, key(Constants.CODE_SPACE).width, "space")
        // the reference has the space bar 2 px left of the 10-column grid (428 instead of 430)
        assertNear(429, key(Constants.CODE_SPACE).x, "left edge of space")
        assertNear(162, key(Constants.CODE_ENTER).width, "enter")

        // key types: emoji is drawn like a letter key, comma and period like functional keys
        assertEquals(Key.BACKGROUND_TYPE_NORMAL, key(KeyCode.EMOJI).backgroundType)
        assertEquals(Key.BACKGROUND_TYPE_FUNCTIONAL, key(",").backgroundType)
        assertEquals(Key.BACKGROUND_TYPE_FUNCTIONAL, key(".").backgroundType)
        assertEquals("%", key("q").hintLabel)
        assertEquals(null, key("1").hintLabel)

        // only the layout switch key asks for the pill among the functional keys
        val normal = ContextCompat.getDrawable(latinIME, R.drawable.btn_keyboard_key_flat)!!
        val functional = ContextCompat.getDrawable(latinIME, R.drawable.btn_keyboard_key_functional_flat)!!
        // the action key takes its drawable from keyBackground, see Colors.selectAndColorDrawable
        fun stateOf(key: Key) = key.selectBackgroundDrawable(normal, functional, normal, normal).state.toList()
        assertTrue(android.R.attr.state_single in stateOf(key(KeyCode.SYMBOL_ALPHA)))
        assertFalse(android.R.attr.state_single in stateOf(key(KeyCode.SHIFT)))
        assertFalse(android.R.attr.state_single in stateOf(key(KeyCode.DELETE)))
        assertTrue(android.R.attr.state_active in stateOf(key(Constants.CODE_ENTER)))
    }

    @Test fun otherStylesKeepEqualRowsAndTheirIcons() {
        prefs.edit().putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true).commit()
        reloadSettings()
        val keyboard = buildKeyboard(1200, 793, R.style.KeyboardTheme_LXX_Base_Border)
        val heights = listOf("1", "q", "a").map { label -> keyboard.sortedKeys.single { it.label == label }.height }
        assertEquals(1, heights.toSet().size, heights.toString())
        assertNotEquals(R.drawable.sym_keyboard_shift_flat, KeyboardIconsSet.instance.iconIds["shift_key"])

        preset.apply()
        reloadSettings()
        KeyboardIconsSet.instance.loadIcons(latinIME)
        assertEquals(R.drawable.sym_keyboard_shift_flat, KeyboardIconsSet.instance.iconIds["shift_key"])
        assertEquals(R.drawable.sym_keyboard_return_flat, KeyboardIconsSet.instance.iconIds["enter_key"])
    }

    private fun buildKeyboard(width: Int, height: Int, theme: Int = R.style.KeyboardTheme_Flat): Keyboard {
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.editorInfo = EditorInfo()
        layoutParams.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz", true))
        layoutParams.keyboardWidth = width
        layoutParams.keyboardHeight = height
        layoutParams.settingsValues = Settings.getValues()
        val builder = KeyboardBuilder(ContextThemeWrapper(latinIME, theme), KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(KeyboardId(KeyboardElement.ALPHABET, layoutParams))
        return builder.build()
    }
}
