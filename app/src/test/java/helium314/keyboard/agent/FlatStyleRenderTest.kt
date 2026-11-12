// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardMode
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LanguageOnSpacebarUtils
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Draws the real keyboard view in the Flat style at the size of the reference screenshot
 * (1200 px wide, 3.25 px per dp, keyboard view 793 px) and checks colours and shapes on the pixels.
 * The picture is also written to build/reports/flat-style/ for a side-by-side comparison.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FlatStyleRenderTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val prefs get() = latinIME.prefs()

    @Before fun applyPreset() {
        prefs.edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        FlatPreset(latinIME).apply()
        prefs.edit().putString(Settings.PREF_SPACE_BAR_TEXT, listOf("DE", "EN", "ES").joinToString(FlatStyle.LANGUAGE_SEPARATOR)).commit()
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    @Test fun lowercaseKeyboardHasTheMeasuredColoursAndShapes() {
        val (keyboard, picture) = render(KeyboardElement.ALPHABET, EditorInfo(), "keyboard-lowercase.png")
        fun key(label: String) = keyboard.sortedKeys.single { it.label == label }
        fun key(code: Int) = keyboard.sortedKeys.single { it.code == code }
        fun hex(x: Int, y: Int) = String.format("#%06X", picture.getPixel(x, y) and 0xFFFFFF)

        // flat fills: a point inside the key that no label covers
        val q = key("q")
        assertEquals("#4C4C4C", hex(q.x + 12, q.y + q.height - 12))
        val space = key(Constants.CODE_SPACE)
        assertEquals("#4C4C4C", hex(space.x + 40, space.y + space.height / 2))
        val emoji = key(KeyCode.EMOJI)
        assertEquals("#4C4C4C", hex(emoji.x + 12, emoji.y + emoji.height - 12))
        val shift = key(KeyCode.SHIFT)
        assertEquals("#0D0D0D", hex(shift.x + 12, shift.y + shift.height - 12))
        val comma = key(",")
        assertEquals("#0D0D0D", hex(comma.x + 12, comma.y + comma.height - 12))
        val enter = key(Constants.CODE_ENTER)
        assertEquals("#6A96EF", hex(enter.x + enter.width / 2, enter.y + 12))
        assertEquals("#000000", hex(q.x + q.width + 8, q.y + q.height / 2)) // gap between q and w
        assertEquals("#000000", hex(3, 3))

        // 6 dp corners on normal keys: the corner pixel is background, 6 px further in it is key
        assertEquals("#000000", hex(q.x + 1, q.y + 1))
        assertEquals("#4C4C4C", hex(q.x + 7, q.y + 7))
        assertEquals("#0D0D0D", hex(shift.x + 7, shift.y + 7))
        // pills: 30 px in from the corner is still background on the layout switch and the action key
        val symbols = key(KeyCode.SYMBOL_ALPHA)
        assertEquals("#000000", hex(symbols.x + 12, symbols.y + 12))
        assertEquals("#0D0D0D", hex(symbols.x + symbols.width / 2, symbols.y + 8))
        assertEquals("#000000", hex(enter.x + 12, enter.y + 12))

        // white labels, light grey hints, grey functional icons
        assertTrue(colourCount(picture, q.x, q.y + 35, q.width, q.height - 35, Color.WHITE) > 300, "label on q")
        assertTrue(colourCount(picture, q.x + q.width / 2, q.y, q.width / 2, 40, Color.rgb(0xDB, 0xDB, 0xDB)) > 20, "hint on q")
        assertTrue(colourCount(picture, shift.x, shift.y, shift.width, shift.height, Color.rgb(0xB7, 0xB7, 0xB7)) > 300, "shift icon")
        assertTrue(colourCount(picture, enter.x, enter.y, enter.width, enter.height, Color.WHITE) > 300, "enter icon")
        assertTrue(colourCount(picture, space.x, space.y, space.width, space.height, Color.WHITE) > 300, "space bar text")
    }

    @Test fun shiftedKeyboardAndUrlFieldRender() {
        render(KeyboardElement.ALPHABET_MANUAL_SHIFTED, EditorInfo(), "keyboard-shifted.png")
        val url = EditorInfo().apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        val (keyboard, _) = render(KeyboardElement.ALPHABET, url, "keyboard-url.png", KeyboardMode.URL)
        assertTrue(keyboard.sortedKeys.any { it.label == "/" })
        render(KeyboardElement.SYMBOLS, EditorInfo(), "keyboard-symbols.png")
    }

    private fun colourCount(picture: Bitmap, left: Int, top: Int, width: Int, height: Int, colour: Int): Int {
        var count = 0
        for (y in top until top + height) for (x in left until left + width)
            if (picture.getPixel(x, y) == colour) count++
        return count
    }

    private fun render(element: KeyboardElement, editorInfo: EditorInfo, fileName: String, mode: KeyboardMode = KeyboardMode.TEXT): Pair<Keyboard, Bitmap> {
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        KeyboardIconsSet.instance.loadIcons(context)
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.mode = mode
        layoutParams.editorInfo = editorInfo
        layoutParams.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz", true))
        layoutParams.keyboardWidth = WIDTH
        layoutParams.keyboardHeight = HEIGHT
        layoutParams.settingsValues = Settings.getValues()
        val builder = KeyboardBuilder(context, KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(KeyboardId(element, layoutParams))
        val keyboard = builder.build()

        val view = MainKeyboardView(context, null)
        view.setKeyboard(keyboard)
        view.startDisplayLanguageOnSpacebar(false, LanguageOnSpacebarUtils.FORMAT_TYPE_LANGUAGE_ONLY, false)
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, WIDTH, HEIGHT)
        val picture = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawColor(Settings.getValues().mColors.get(helium314.keyboard.latin.common.ColorType.MAIN_BACKGROUND))
        view.draw(canvas)
        File("build/reports/flat-style").apply { mkdirs() }.resolve(fileName).outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return keyboard to picture
    }

    private companion object {
        const val WIDTH = 1200
        const val HEIGHT = 793
    }
}
