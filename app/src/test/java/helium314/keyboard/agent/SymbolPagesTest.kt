// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.SharedPreferences
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardMode
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.KeyboardState
import helium314.keyboard.keyboard.internal.LayoutDirective
import helium314.keyboard.keyboard.internal.ShiftMode
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 81: both symbol pages equal the Gboard pages key for key, in every style and with the number row
 * on or off, and the hints / popup keys of the letter keys did not move when the first symbol row went away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SymbolPagesTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val prefs get() = latinIME.prefs()

    private enum class Look(val themeRes: Int, val style: String?) {
        MATERIAL(R.style.KeyboardTheme_LXX_Base, KeyboardTheme.STYLE_MATERIAL),
        ROUNDED(R.style.KeyboardTheme_Rounded_Base_Border, KeyboardTheme.STYLE_ROUNDED),
        HOLO(R.style.KeyboardTheme_HoloBase, KeyboardTheme.STYLE_HOLO),
        FLAT(R.style.KeyboardTheme_Flat, null), // the preset, as the owner can apply it
    }

    @Before fun reset() = reload(Look.MATERIAL, Locale.GERMANY, numberRow = false)

    @After fun restoreDefaults() {
        prefs.edit().clear().commit()
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    private fun reload(look: Look, locale: Locale, numberRow: Boolean, extra: SharedPreferences.Editor.() -> Unit = {}) {
        prefs.edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        if (look == Look.FLAT) FlatPreset(latinIME).apply()
        else prefs.edit().putString(Settings.PREF_THEME_STYLE, look.style).commit()
        prefs.edit().putBoolean(Settings.PREF_SHOW_NUMBER_ROW, numberRow).apply(extra).commit()
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, locale, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    private fun build(look: Look, element: KeyboardElement, locale: Locale, layout: String): Keyboard {
        val context = ContextThemeWrapper(latinIME, look.themeRes)
        KeyboardIconsSet.instance.loadIcons(context)
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.mode = KeyboardMode.TEXT
        layoutParams.editorInfo = EditorInfo()
        layoutParams.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(locale, layout, true))
        layoutParams.keyboardWidth = 1200
        layoutParams.keyboardHeight = 793
        layoutParams.settingsValues = Settings.getValues()
        val builder = KeyboardBuilder(context, KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(KeyboardId(element, layoutParams))
        return builder.build()
    }

    private fun rows(keyboard: Keyboard) =
        keyboard.sortedKeys.filterNot { it.isSpacer }.groupBy { it.y }.toSortedMap().values.map { row -> row.sortedBy { it.x } }

    /** The label of a key as the owner reads it; functional keys by name. */
    private fun name(key: Key): String = when (key.code) {
        KeyCode.SHIFT -> "[shift]"
        KeyCode.DELETE -> "[del]"
        KeyCode.SYMBOL_ALPHA -> "[abc]"
        KeyCode.NUMPAD -> "[numpad]"
        KeyCode.DPAD -> "[dpad]"
        KeyCode.EMOJI -> "[emoji]"
        KeyCode.LANGUAGE_SWITCH -> "[lang]"
        Constants.CODE_SPACE -> "[space]"
        Constants.CODE_ENTER -> "[enter]"
        else -> key.label ?: "#${key.code}"
    }

    private fun pageNames(keyboard: Keyboard) = rows(keyboard).map { row -> row.map(::name) }

    private fun popups(key: Key) = key.popupKeys?.joinToString(" ") { it.mLabel ?: it.mIconName ?: "#${it.mCode}" }.orEmpty()

    /** Every key with label, hint and popup keys, one line per row. */
    private fun dump(keyboard: Keyboard) = rows(keyboard).joinToString("\n") { row ->
        row.joinToString(" ; ") { "${name(it)} | ${it.hintLabel.orEmpty()} | ${popups(it)}" }
    }

    // ---------------------------------------------------------------- the pages

    private val page1de = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "€", "_", "&", "-", "+", "(", ")", "/"),
        listOf("[shift]", "*", "\"", "'", ":", ";", "!", "?", "[del]"),
        listOf("[abc]", ",", "[numpad]", "[space]", ".", "[enter]"),
    )
    private val page2de = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆"),
        listOf("£", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        listOf("[shift]", "%", "©", "®", "™", "✓", "[", "]", "[del]"),
        listOf("[abc]", "<", "[numpad]", "[space]", ">", "[enter]"),
    )

    @Test fun bothPagesEqualGboardInEveryStyleWithTheNumberRowOnAndOff() {
        for (look in Look.values()) for (numberRow in listOf(false, true)) {
            reload(look, Locale.GERMANY, numberRow)
            val case = "$look numberRow=$numberRow"
            assertEquals(page1de, pageNames(build(look, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")), "page 1, $case")
            assertEquals(page2de, pageNames(build(look, KeyboardElement.SYMBOLS_SHIFTED, Locale.GERMANY, "qwertz")), "page 2, $case")
        }
    }

    @Test fun theCurrencyKeyFollowsTheLocaleAndNothingElseChanges() {
        for (look in listOf(Look.MATERIAL, Look.FLAT)) {
            reload(look, Locale.US, numberRow = false)
            val one = pageNames(build(look, KeyboardElement.SYMBOLS, Locale.US, "qwerty"))
            assertEquals(page1de.mapIndexed { i, row -> if (i == 1) row.map { if (it == "€") "$" else it } else row }, one, "$look")
            assertEquals(page2de, pageNames(build(look, KeyboardElement.SYMBOLS_SHIFTED, Locale.US, "qwerty")), "$look")
        }
    }

    @Test fun theNumberRowSettingDoesNotChangeAnythingOnThePages() {
        for (look in Look.values()) for (element in listOf(KeyboardElement.SYMBOLS, KeyboardElement.SYMBOLS_SHIFTED)) {
            reload(look, Locale.GERMANY, numberRow = false)
            val off = dump(build(look, element, Locale.GERMANY, "qwertz"))
            reload(look, Locale.GERMANY, numberRow = true)
            val on = dump(build(look, element, Locale.GERMANY, "qwertz"))
            assertEquals(off, on, "$look $element")
            // the old switch "number row in symbols" has no say on the built-in pages
            reload(look, Locale.GERMANY, numberRow = false) { putBoolean(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, false) }
            assertEquals(off, dump(build(look, element, Locale.GERMANY, "qwertz")), "$look $element, number row in symbols off")
        }
    }

    /**
     * With the number row on the letter page has five rows and both symbol pages four rows, each higher;
     * the keyboard has the same height. Without the number row the letter page has four rows, as the pages.
     */
    @Test fun pagesHaveFourRowsAndTheHeightOfTheLetterPage() {
        for (numberRow in listOf(false, true)) {
            reload(Look.MATERIAL, Locale.GERMANY, numberRow)
            val alphabet = build(Look.MATERIAL, KeyboardElement.ALPHABET, Locale.GERMANY, "qwertz")
            assertEquals(if (numberRow) 5 else 4, rows(alphabet).size)
            for (element in listOf(KeyboardElement.SYMBOLS, KeyboardElement.SYMBOLS_SHIFTED)) {
                val keyboard = build(Look.MATERIAL, element, Locale.GERMANY, "qwertz")
                assertEquals(4, rows(keyboard).size, "$element numberRow=$numberRow")
                // same total height as the letter page: the occupied height and the bottom edge of the last row agree
                assertEquals(alphabet.mOccupiedHeight, keyboard.mOccupiedHeight, "$element numberRow=$numberRow")
                assertEquals(rows(alphabet).last().maxOf { it.y + it.height }, rows(keyboard).last().maxOf { it.y + it.height }, "$element bottom edge")
                // the four rows are equally high (the bottom row scale is 1 by default)
                val heights = rows(keyboard).map { row -> row.maxOf { it.height } }
                assertEquals(1, heights.toSet().size, "$element row heights $heights")
            }
        }
    }

    @Test fun dpadKeyOnlyAppearsWhenSwitchedOnExplicitly() {
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = false)
        assertTrue(pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")).none { "[dpad]" in it })
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = false) { putBoolean(Settings.PREF_SHOW_DPAD_KEY, true) }
        val bottom = pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")).last()
        assertEquals(listOf("[abc]", ",", "[numpad]", "[space]", "[dpad]", ".", "[enter]"), bottom)
    }

    @Test fun theFormerFlatLayoutNamesStillGiveTheGboardPagesAndTheSameHints() {
        for (look in listOf(Look.MATERIAL, Look.FLAT)) for (numberRow in listOf(false, true)) {
            reload(look, Locale.GERMANY, numberRow)
            val plain = dump(build(look, KeyboardElement.ALPHABET, Locale.GERMANY, "qwertz"))
            reload(look, Locale.GERMANY, numberRow) {
                putString("layout_SYMBOLS", "symbols_flat")
                putString("layout_MORE_SYMBOLS", "symbols_shifted_flat")
            }
            assertEquals(page1de, pageNames(build(look, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")), "$look $numberRow")
            assertEquals(page2de, pageNames(build(look, KeyboardElement.SYMBOLS_SHIFTED, Locale.GERMANY, "qwertz")), "$look $numberRow")
            assertEquals(plain, dump(build(look, KeyboardElement.ALPHABET, Locale.GERMANY, "qwertz")), "$look $numberRow hints")
        }
    }

    // ---------------------------------------------------------------- what the special keys do

    private class RecordingActions : KeyboardState.SwitchActions {
        val log = mutableListOf<String>()
        override fun setAlphabetKeyboard(shiftMode: ShiftMode) { log += "alphabet" }
        override fun setEmojiKeyboard() { log += "emoji" }
        override fun setClipboardKeyboard() { log += "clipboard" }
        override fun setNumpadKeyboard() { log += "numpad" }
        override fun setDpadKeyboard() { log += "dpad" }
        override fun setSymbolsKeyboard() { log += "symbols" }
        override fun setSymbolsShiftedKeyboard() { log += "symbols2" }
        override fun startDoubleTapShiftKeyTimer() {}
        override fun popDoubleTapShiftKeyTimer() = false
        override fun cancelDoubleTapShiftKeyTimer() {}
        override fun setOneHandedModeEnabled(enabled: Boolean) {}
        override fun switchOneHandedMode() {}
        override fun setFloatingKeyboardEnabled(enabled: Boolean) {}
    }

    private fun press(state: KeyboardState, code: Int) =
        state.onEvent(helium314.keyboard.event.Event.createSoftwareKeypressEvent(code, 0, 0, 0, false), 0, null)

    @Test fun angleKeysTypeTheirCharacterAndTheNumpadKeyOpensTheNumpadOnBothPages() {
        for (look in listOf(Look.MATERIAL, Look.FLAT)) {
            reload(look, Locale.GERMANY, numberRow = false)
            val more = rows(build(look, KeyboardElement.SYMBOLS_SHIFTED, Locale.GERMANY, "qwertz")).last()
            val less = more.single { it.label == "<" }
            val greater = more.single { it.label == ">" }
            // a key with a character as code commits that character; there is no output text and no function code
            assertEquals('<'.code, less.code, "$look")
            assertEquals('>'.code, greater.code, "$look")
            assertEquals(null, less.outputText)
            assertEquals(null, greater.outputText)
            val one = rows(build(look, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")).last()
            assertEquals(KeyCode.NUMPAD, one.single { name(it) == "[numpad]" }.code)
            assertEquals(KeyCode.NUMPAD, more.single { name(it) == "[numpad]" }.code)
            assertTrue(more.none { it.code == KeyCode.DPAD })
        }
        // the numpad: ABC and ?123 are on its bottom row
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = false)
        val numpad = rows(build(Look.MATERIAL, KeyboardElement.NUMPAD, Locale.GERMANY, "qwertz")).last().map { it.code }
        assertTrue(KeyCode.ALPHA in numpad && KeyCode.SYMBOL in numpad, "numpad bottom row $numpad")
    }

    @Test fun theNumpadKeyOnTheSymbolPagesSwitchesAsDesigned() {
        // page 1: numpad, the same key again returns to page 1
        var actions = RecordingActions()
        var state = KeyboardState(actions)
        state.setLayout(LayoutDirective.Utility.SYMBOLS)
        press(state, KeyCode.NUMPAD)
        press(state, KeyCode.NUMPAD)
        assertEquals(listOf("symbols", "numpad", "symbols"), actions.log)
        // page 2: numpad, the same key again returns to page 2
        actions = RecordingActions(); state = KeyboardState(actions)
        state.setLayout(LayoutDirective.Utility.SYMBOLS_SHIFTED)
        press(state, KeyCode.NUMPAD)
        press(state, KeyCode.NUMPAD)
        assertEquals(listOf("symbols2", "numpad", "symbols2"), actions.log)
        // the keys of the numpad: ABC goes to the letters, ?123 to page 1 (from page 2 as well)
        actions = RecordingActions(); state = KeyboardState(actions)
        state.setLayout(LayoutDirective.Utility.SYMBOLS_SHIFTED)
        press(state, KeyCode.NUMPAD)
        press(state, KeyCode.ALPHA)
        assertEquals(listOf("symbols2", "numpad", "alphabet"), actions.log)
        actions = RecordingActions(); state = KeyboardState(actions)
        state.setLayout(LayoutDirective.Utility.SYMBOLS_SHIFTED)
        press(state, KeyCode.NUMPAD)
        press(state, KeyCode.SYMBOL)
        assertEquals(listOf("symbols2", "numpad", "symbols"), actions.log)
    }

    private fun icons(key: Key) = key.popupKeys?.map { it.mIconName ?: it.mLabel ?: "#${it.mCode}" }.orEmpty()

    @Test fun popupsOfTheAngleKeysAndTheCommaKey() {
        for (look in listOf(Look.MATERIAL, Look.FLAT)) {
            reload(look, Locale.GERMANY, numberRow = false)
            val more = rows(build(look, KeyboardElement.SYMBOLS_SHIFTED, Locale.GERMANY, "qwertz")).last()
            assertEquals(listOf("‹", "≤", "«"), icons(more.single { it.label == "<" }), "$look <")
            assertEquals(listOf("›", "≥", "»"), icons(more.single { it.label == ">" }), "$look >")
            // the comma of page 1 keeps the tools as on the letter page
            val comma = rows(build(look, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")).last().single { it.label == "," }
            val letterComma = rows(build(look, KeyboardElement.ALPHABET, Locale.GERMANY, "qwertz")).last().single { it.label == "," }
            assertEquals(listOf("clipboard_normal_key", "agent_prompts_key", "emoji_normal_key", "start_onehanded_mode_key", "floating", "settings_key"),
                icons(comma), "$look")
            assertEquals(icons(letterComma), icons(comma), "$look as on the letter page")
            assertTrue(comma.popupKeys!!.any { it.mCode == KeyCode.AGENT_PROMPTS })
        }
    }

    @Test fun dpadKeyFollowsAnExplicitSwitchBothWays() {
        // explicit false (as an old build wrote it) and unset: no arrows; explicit true: arrows, the other keys stay
        for (value in listOf(null, false)) {
            reload(Look.MATERIAL, Locale.GERMANY, numberRow = false) { if (value != null) putBoolean(Settings.PREF_SHOW_DPAD_KEY, value) }
            assertEquals(listOf("[abc]", ",", "[numpad]", "[space]", ".", "[enter]"),
                pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")).last(), "value=$value")
        }
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = false) { putBoolean(Settings.PREF_SHOW_DPAD_KEY, true) }
        assertEquals(listOf("[abc]", ",", "[numpad]", "[space]", "[dpad]", ".", "[enter]"),
            pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")).last())
        // page 2 never has the arrows
        assertEquals(page2de.last(), pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS_SHIFTED, Locale.GERMANY, "qwertz")).last())
    }

    // ---------------------------------------------------------------- other layouts keep the old behaviour

    private fun ownSymbolLayout(): String {
        val name = LayoutUtilsCustom.getLayoutName("stage81", LayoutType.SYMBOLS)
        LayoutUtilsCustom.getLayoutFile(name + "txt", LayoutType.SYMBOLS, latinIME).writeText(
            "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\n\nk\nl\nm\n\nn\no\n")
        LayoutUtilsCustom.onLayoutFileChanged()
        return name
    }

    private fun checkOldBehaviour(layoutName: String, firstRow: List<String>, hint: String) {
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = false) { putString("layout_SYMBOLS", layoutName); putBoolean(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, true) }
        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val withSwitch = pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz"))
        assertEquals(digits, withSwitch[0], "$layoutName: digits replace the first row")
        val keyboard = build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz")
        assertEquals(firstRow[0], popups(rows(keyboard)[0][0]).split(" ").last(), "$layoutName: the replaced label is a popup")
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = false) { putString("layout_SYMBOLS", layoutName); putBoolean(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, false) }
        assertEquals(firstRow, pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz"))[0], "$layoutName: switch off")
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = true) { putString("layout_SYMBOLS", layoutName) }
        val five = pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS, Locale.GERMANY, "qwertz"))
        assertEquals(5, five.size, "$layoutName: number row on gives five rows")
        assertEquals(digits, five[0])
        assertEquals(firstRow, five[1])
        // the hints of the letter keys follow this layout, not the built-in hint file
        reload(Look.MATERIAL, Locale.GERMANY, numberRow = true) { putString("layout_SYMBOLS", layoutName) }
        val q = rows(build(Look.MATERIAL, KeyboardElement.ALPHABET, Locale.GERMANY, "qwertz"))[1][0]
        assertEquals(hint, q.hintLabel, "$layoutName: hint of q")
    }

    @Test fun anOwnSymbolLayoutKeepsTheOldBehaviour() {
        checkOldBehaviour(ownSymbolLayout(), listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), "a")
    }

    @Test fun symbolsArabicKeepsTheOldBehaviour() {
        checkOldBehaviour("symbols_arabic", listOf("٪", "\\", "|", "=", "[", "]", "<", ">", "﴾", "﴿"), "٪")
    }

    @Test fun tabletAndKhiproLayoutsGetTheSameSecondPageAsTheStandard() {
        for (layout in listOf("functional_keys", "functional_keys_tablet", "functional_keys_khipro")) {
            reload(Look.MATERIAL, Locale.GERMANY, numberRow = false) { putString("layout_FUNCTIONAL", layout) }
            val more = pageNames(build(Look.MATERIAL, KeyboardElement.SYMBOLS_SHIFTED, Locale.GERMANY, "qwertz")).last()
            assertTrue("<" in more && ">" in more && "[numpad]" in more, "$layout page 2 bottom row $more")
            assertTrue(more.indexOf("<") < more.indexOf("[numpad]") && more.indexOf("[numpad]") < more.indexOf("[space]") && more.indexOf("[space]") < more.indexOf(">"), "$layout $more")
            val m = rows(build(Look.MATERIAL, KeyboardElement.SYMBOLS_SHIFTED, Locale.GERMANY, "qwertz")).last()
            assertEquals(listOf("‹", "≤", "«"), icons(m.single { it.label == "<" }), "$layout <")
            assertEquals(listOf("›", "≥", "»"), icons(m.single { it.label == ">" }), "$layout >")
        }
    }

    // ---------------------------------------------------------------- hints and popups of the letter keys

    /**
     * The fixture was recorded from the build before stage 81 (symbols.txt still had the row % \ | = [ ] < > { }).
     * The hints and popup keys of the letter keys must not have moved.
     */
    @Test fun letterKeysKeepTheirHintsAndPopupKeys() {
        val text = buildString {
            for (look in Look.values()) for ((locale, layout) in listOf(Locale.GERMANY to "qwertz", Locale.US to "qwerty"))
                for (numberRow in listOf(false, true)) {
                    reload(look, locale, numberRow)
                    append("### $look ${locale.toLanguageTag()} numberRow=$numberRow\n")
                    append(dump(build(look, KeyboardElement.ALPHABET, locale, layout))).append("\n")
                }
        }
        File("build/reports/symbols81").apply { mkdirs() }.resolve("letter_keys.txt").writeText(text)
        val fixture = javaClass.getResourceAsStream("/symbols81/letter_keys.txt")?.reader()?.readText()
        assertTrue(fixture != null, "fixture missing, copy build/reports/symbols81/letter_keys.txt to src/test/resources/symbols81/")
        assertEquals(fixture, text)
    }

    /** Written for the report: what the keys of both pages offer on long press. */
    @Test fun writePagesForTheReport() {
        val out = StringBuilder()
        for (look in listOf(Look.MATERIAL, Look.FLAT)) for (locale in listOf(Locale.GERMANY to "qwertz", Locale.US to "qwerty")) {
            reload(look, locale.first, numberRow = false)
            for (element in listOf(KeyboardElement.SYMBOLS, KeyboardElement.SYMBOLS_SHIFTED)) {
                out.append("### $look ${locale.first.toLanguageTag()} $element\n")
                out.append(dump(build(look, element, locale.first, locale.second))).append("\n")
            }
        }
        File("build/reports/symbols81").apply { mkdirs() }.resolve("pages.txt").writeText(out.toString())
    }
}
