// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
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
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.LanguageOnSpacebarUtils
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs
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


    /** The settings singleton outlives a test; later tests must not inherit the Flat style. */
    @After fun restoreDefaults() {
        latinIME.prefs().edit().clear().commit()
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
        val (symbols, _) = render(KeyboardElement.SYMBOLS, EditorInfo(), "keyboard-symbols.png")
        val (more, _) = render(KeyboardElement.SYMBOLS_SHIFTED, EditorInfo(), "keyboard-more-symbols.png")
        fun rows(keyboard: Keyboard) = keyboard.sortedKeys.groupBy { it.y }.toSortedMap().values.map { row -> row.sortedBy { it.x } }
        fun labels(row: List<helium314.keyboard.keyboard.Key>) = row.map { it.label ?: it.code.toString() }
        // stage 23a: like the reference, page 1 has the digits, page 2 its own first row and < > at the bottom
        val first = rows(symbols)
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), labels(first[0]))
        assertEquals(listOf("@", "#", "€", "_", "&", "-", "+", "(", ")", "/"), labels(first[1]))
        assertEquals(listOf("*", "\"", "'", ":", ";", "!", "?"), labels(first[2]).drop(1).dropLast(1))
        assertEquals(4, first.size)
        val second = rows(more)
        assertEquals(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ"), labels(second[0]))
        assertEquals(listOf("£", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\"), labels(second[1]))
        assertEquals(listOf("%", "©", "®", "™", "✓", "[", "]"), labels(second[2]).drop(1).dropLast(1))
        // bottom rows: ABC, comma or <, the light 1234 key, space, period or >, enter; no dpad key
        fun bottom(row: List<helium314.keyboard.keyboard.Key>) = row.map { it.code }
        assertTrue(bottom(first[3]).contains(KeyCode.NUMPAD) && !bottom(first[3]).contains(KeyCode.DPAD))
        assertTrue(bottom(second[3]).contains(KeyCode.NUMPAD))
        assertEquals(helium314.keyboard.keyboard.Key.BACKGROUND_TYPE_NORMAL, first[3].single { it.code == KeyCode.NUMPAD }.backgroundType)
        assertEquals(listOf("<", ">"), labels(second[3]).filter { it == "<" || it == ">" })
        assertEquals(4, second.size)
    }

    @Test fun stripShowsFiveEvenlySpacedButtonsWithTheLastOneInACircle() {
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        KeyboardIconsSet.instance.loadIcons(context)
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        container.findViewById<View>(R.id.emoji_tab_strip).visibility = View.GONE
        container.findViewById<View>(R.id.clipboard_strip_scroll_view).visibility = View.GONE
        val strip = container.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        strip.setToolbarVisibility(true)
        val stripHeight = 130 // 40 dp
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(stripHeight, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, WIDTH, stripHeight) // a single pass must be enough
        val picture = Bitmap.createBitmap(WIDTH, stripHeight, Bitmap.Config.ARGB_8888)
        container.draw(Canvas(picture))
        save(picture, "strip-toolbar.png")

        val toolbar = strip.findViewById<ViewGroup>(R.id.toolbar)
        val pinned = strip.findViewById<ViewGroup>(R.id.pinned_keys)
        val buttons = (listOf(strip.findViewById<View>(R.id.suggestions_strip_toolbar_key)) +
            (0 until toolbar.childCount).map(toolbar::getChildAt) + (0 until pinned.childCount).map(pinned::getChildAt))
            .filterIsInstance<ImageButton>()
        assertEquals(listOf(null, ToolbarKey.CLIPBOARD, ToolbarKey.EMOJI, ToolbarKey.AGENT, ToolbarKey.DICTATE), buttons.map { it.tag })
        // positions relative to the container; the views are not attached to a window
        fun View.leftInContainer(): Int = generateSequence(this) { it.parent as? View }.takeWhile { it !== container }.sumOf { it.left - (it.parent as View).scrollX }
        fun View.topInContainer(): Int = generateSequence(this) { it.parent as? View }.takeWhile { it !== container }.sumOf { it.top }
        val centres = buttons.map { it.leftInContainer() + it.width / 2f to it.topInContainer() + it.height / 2f }
        // reference: centres at x = 75.5, 338, 600, 861, 1124.5 and 71.5 px below the upper edge
        listOf(75.5f, 338f, 600f, 861f, 1124.5f).zip(centres).forEach { (expected, centre) ->
            assertTrue(abs(expected - centre.first) <= 2.5f, "x centre $expected, was ${centre.first}")
            assertTrue(abs(71.5f - centre.second) <= 1f, "y centre, was ${centre.second}")
        }
        buttons.forEach { assertTrue(abs(it.width - 111) <= 1 && abs(it.height - 111) <= 1, "button ${it.width} x ${it.height}") }

        fun hex(x: Int, y: Int) = String.format("#%06X", picture.getPixel(x, y) and 0xFFFFFF)
        assertEquals("#4B4B4B", hex(1124, 24)) // inside the circle, above the glyph
        assertEquals("#000000", hex(1076, 24)) // outside the circle, inside its bounding box
        assertEquals("#000000", hex(600, 5))
        assertTrue(colourCount(picture, 1069, 16, 111, 111, FlatStyle.STRIP_CIRCLE_ICON) > 500, "dictation glyph")
        listOf(20, 282, 544, 805).forEach { left ->
            assertTrue(colourCount(picture, left, 16, 111, 111, FlatStyle.TOOLBAR_ICON) > 500, "icon at $left")
        }
    }

    @Test fun symbolPagesShowRecentEmojisInsteadOfTheTools() {
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        KeyboardIconsSet.instance.loadIcons(context)
        helium314.keyboard.keyboard.emoji.RecentEmojis.set(listOf("🤠", "👍"))
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        val strip = container.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        strip.setToolbarVisibility(true)
        val toolbar = strip.findViewById<View>(R.id.toolbar_container)
        val emojis = strip.findViewById<ViewGroup>(R.id.agent_strip_emojis)
        assertEquals(View.GONE, emojis.visibility)

        strip.showRecentEmojis(true)
        assertEquals(View.VISIBLE, emojis.visibility)
        assertEquals(View.GONE, toolbar.visibility)
        val shown = (0 until emojis.childCount).map(emojis::getChildAt).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
        assertEquals(listOf("🤠", "👍", "😂", "❤️", "🙏", "😊"), shown) // recent first, then defaults without the duplicate
        assertEquals(View.VISIBLE, strip.findViewById<View>(R.id.suggestions_strip_toolbar_key).visibility) // menu stays
        assertEquals(View.VISIBLE, strip.findViewById<View>(R.id.pinned_keys).visibility) // dictation stays
        strip.setToolbarVisibility(true) // e.g. the strip went neutral: the emojis stay
        assertEquals(View.GONE, toolbar.visibility)
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(130, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, WIDTH, 130)
        val picture = Bitmap.createBitmap(WIDTH, 130, Bitmap.Config.ARGB_8888)
        container.draw(Canvas(picture))
        save(picture, "strip-emojis.png")

        // tapping an emoji moves it to the front of the recent ones
        (0 until emojis.childCount).map(emojis::getChildAt).filterIsInstance<android.widget.TextView>()[2].performClick()
        assertEquals("😂", helium314.keyboard.keyboard.emoji.RecentEmojis.get().first())

        strip.showRecentEmojis(false)
        assertEquals(View.GONE, emojis.visibility)
        assertEquals(View.VISIBLE, toolbar.visibility) // back as before
    }

    @Test fun toolbarSwitchThatIsOffIsDrawnDarker() {
        val context = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
        val key = ImageButton(context, null, 0)
        key.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.sym_keyboard_grid_flat)!!.mutate())
        Settings.getValues().mColors.setColor(key, helium314.keyboard.latin.common.ColorType.TOOL_BAR_KEY)
        fun draw(activated: Boolean): Bitmap {
            key.isActivated = activated
            key.measure(View.MeasureSpec.makeMeasureSpec(111, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(111, View.MeasureSpec.EXACTLY))
            key.layout(0, 0, 111, 111)
            return Bitmap.createBitmap(111, 111, Bitmap.Config.ARGB_8888).also { key.draw(Canvas(it)) }
        }
        assertTrue(colourCount(draw(true), 0, 0, 111, 111, FlatStyle.TOOLBAR_ICON) > 500)
        val off = draw(false)
        assertTrue(colourCount(off, 0, 0, 111, 111, FlatStyle.TOOLBAR_ICON_OFF) > 500)
        assertEquals(0, colourCount(off, 0, 0, 111, 111, FlatStyle.TOOLBAR_ICON))
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
        save(picture, fileName)
        return keyboard to picture
    }

    private fun save(picture: Bitmap, fileName: String) {
        File("build/reports/flat-style").apply { mkdirs() }.resolve(fileName).outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val WIDTH = 1200
        const val HEIGHT = 793
    }
}
