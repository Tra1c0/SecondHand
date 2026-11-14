// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsNavHost
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.KEYS_RETURN_TAG
import helium314.keyboard.settings.screens.KEYS_ROW_TAG
import helium314.keyboard.settings.screens.LANGUAGE_CARD_TAG
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
import helium314.keyboard.settings.screens.TILE_TAG
import helium314.keyboard.settings.screens.subtypeRoute
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 38d: the area page "Tasten & Sprachen" (mockup Tasten.dc.html) on the real preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsKeysScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val routes = mutableListOf<String>()

    private val german by lazy { SubtypeSettings.getResourceSubtypesForLocale("de".constructLocale()).first() }
    private val english by lazy { SubtypeSettings.getResourceSubtypesForLocale("en-US".constructLocale()).first() }
    private val spanish by lazy { SubtypeSettings.getResourceSubtypesForLocale("es".constructLocale()).first() }

    /** Keys this page writes; removed after each test (Fallstrick P32). */
    private val written = listOf(Settings.PREF_ENABLED_SUBTYPES, Settings.PREF_SELECTED_SUBTYPE, Settings.PREF_SAVE_SUBTYPE_PER_APP,
        Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Settings.PREF_LANGUAGE_SWITCH_KEY, Settings.PREF_SHOW_NUMBER_ROW,
        Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, Settings.PREF_SHOW_NUMBER_ROW_HINTS, Settings.PREF_SHOW_EMOJI_KEY,
        Settings.PREF_SHOW_DPAD_KEY, Settings.PREF_CUSTOM_CURRENCY_KEY, Settings.PREF_SHOW_HINTS, Settings.PREF_POPUP_ON,
        Settings.PREF_REMOVE_REDUNDANT_POPUPS, Settings.PREF_SHOW_POPUP_HINTS, Settings.PREF_SHOW_TLD_POPUP_KEYS,
        Settings.PREF_KEY_LONGPRESS_TIMEOUT, Settings.PREF_TIMESTAMP_FORMAT, Settings.PREF_ABC_AFTER_SYMBOL_SPACE,
        Settings.PREF_ABC_AFTER_NUMPAD_SPACE, Settings.PREF_ABC_AFTER_EMOJI, Settings.PREF_ABC_AFTER_CLIP,
        Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, Settings.PREF_MORE_POPUP_KEYS)

    @After fun reset() {
        app.prefs().edit().apply { written.forEach { remove(it) } }.commit()
        SubtypeSettings.reloadEnabledSubtypes(app)
        Settings.getInstance().loadSettings(app)
    }

    private fun start(vararg enabled: android.view.inputmethod.InputMethodSubtype, selected: android.view.inputmethod.InputMethodSubtype? = null) {
        initPreview(app)
        enabled.forEach { SubtypeSettings.addEnabledSubtype(app.prefs(), it) }
        selected?.let { SubtypeSettings.setSelectedSubtype(app.prefs(), it) }
        compose.setContent { Theme(dark = true) { AgentAreaScreen(SettingsArea.KEYS, onNavigate = { routes += it }, onClickBack = {}) } }
        compose.waitForIdle()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test fun showsTheMockupTextsInItsSections() {
        start(german)
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaKeys).assertExists()
        compose.onNodeWithTag(HERO_TAG).assert(hasText("03")).assert(hasText("Tasten & Sprachen"))
            .assert(hasText("Welche Tasten es gibt und in welchen Sprachen du schreibst."))
        listOf("SPRACHEN", "Sprachen", "Sprache hinzufügen", "Layouts und Wörterbücher", "Sprache pro App merken",
            "Jede App behält ihre Sprache", "Sprachwechseltaste", "Sprache oder Tastatur wechseln",
            "Zusatztasten", "Zahlenzeile", "Immer über den Buchstaben", "Emoji-Taste", "Neben der Leertaste",
            "Steuerkreuz-Taste", "Pfeile zum Cursor-Bewegen", "Währungen", "€ und bis zu 6 weitere",
            "Popups & Hinweise", "Tasten-Hinweise", "Kleine Zeichen in der Ecke", "Popup bei Tastendruck", "Vergrößerte Taste",
            "Reihenfolge der Popups", "Doppelte Popups weglassen", "Was schon auf der Tastatur ist", "Langdruck-Dauer",
            "Bis das Popup kommt", "300\u00A0ms" /* non-breaking space of the upstream unit */, "Layouts", "Symbole, Zahlen, Ziffernblock", "Sekundäre Layouts",
            "Zurück zu den Buchstaben", "Nach Leertaste, Emoji, Zwischenablage", "Zeitstempel-Format",
            "Für die Zeitstempel-Taste · ${Defaults.PREF_TIMESTAMP_FORMAT}", "+", "Standard")
            .forEach { assertTrue(exists(it), "missing text: $it") }
        // the "Seiten" card of 38a is gone on this area
        assertFalse(exists(app.getString(R.string.agent_area_pages_note)))
        // section order as in the mockup
        val tops = listOf("Sprache hinzufügen", "Zahlenzeile", "Tasten-Hinweise", "Symbole, Zahlen, Ziffernblock")
            .map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test fun oneCardPerEnabledLanguageWithLayoutDictionariesAndTheActiveOne() {
        start(german, english, spanish, selected = english)
        val enabled = SubtypeSettings.getEnabledSubtypes(true)
        assertEquals(3, enabled.size)
        enabled.forEachIndexed { i, subtype ->
            val node = compose.onNodeWithTag(LANGUAGE_CARD_TAG + i).fetchSemanticsNode()
            // a button that opens the language, not a radio button
            assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
            assertEquals(null, node.config.getOrNull(SemanticsProperties.Selected))
            val active = subtype == english
            assertEquals(if (active) "aktiv" else null, node.config.getOrNull(SemanticsProperties.StateDescription), subtype.locale().toLanguageTag())
        }
        // labels: the language in its own language, in capitals; hints: layout and the bundled dictionaries
        val de = enabled.indexOf(german); val en = enabled.indexOf(english); val es = enabled.indexOf(spanish)
        compose.onNodeWithTag(LANGUAGE_CARD_TAG + de).assert(hasText("DEUTSCH")).assert(hasText("QWERTZ · Hauptwörterbuch · Emoji-Wörterbuch"))
        compose.onNodeWithTag(LANGUAGE_CARD_TAG + en).assert(hasText("ENGLISH (US)")).assert(hasText("QWERTY · Hauptwörterbuch · Emoji-Wörterbuch · aktiv"))
        compose.onNodeWithTag(LANGUAGE_CARD_TAG + es).assert(hasText("ESPAÑOL", substring = true)).assert(hasText("Hauptwörterbuch", substring = true))
        // P128: naming a language in its own language must not switch the app's resources
        assertTrue(exists("Sprache hinzufügen") && exists("Zusatztasten"), "page texts not German any more")
        assertEquals("de", app.resources.configuration.locales[0].language)
        // a tap opens the existing page of that language
        enabled.indices.forEach { compose.onNodeWithTag(LANGUAGE_CARD_TAG + it).performScrollTo().performClick() }
        assertEquals(enabled.map { subtypeRoute(it) }, routes)
        assertTrue(routes.all { it.startsWith(SettingsDestination.Subtype) })
    }

    @Test fun switchesWriteTheRealPreferences() {
        start(german)
        val prefs = app.prefs()
        fun state(key: String) = compose.onNodeWithTag(KEYS_ROW_TAG + key).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
        val switches = mapOf(
            Settings.PREF_SAVE_SUBTYPE_PER_APP to Defaults.PREF_SAVE_SUBTYPE_PER_APP,
            Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY to Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY,
            Settings.PREF_SHOW_EMOJI_KEY to Defaults.PREF_SHOW_EMOJI_KEY,
            Settings.PREF_SHOW_DPAD_KEY to Defaults.PREF_SHOW_DPAD_KEY,
            Settings.PREF_POPUP_ON to Defaults.PREF_POPUP_ON,
            Settings.PREF_REMOVE_REDUNDANT_POPUPS to Defaults.PREF_REMOVE_REDUNDANT_POPUPS,
            Settings.PREF_SHOW_POPUP_HINTS to Defaults.PREF_SHOW_POPUP_HINTS,
            Settings.PREF_SHOW_TLD_POPUP_KEYS to Defaults.PREF_SHOW_TLD_POPUP_KEYS,
        )
        switches.forEach { (key, default) ->
            compose.onNodeWithTag(KEYS_ROW_TAG + key).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            assertEquals(ToggleableState(default), state(key), key)
            compose.onNodeWithTag(KEYS_ROW_TAG + key).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(!default, prefs.getBoolean(key, default), key)
            assertEquals(ToggleableState(!default), state(key), key)
        }
        // number row: on hides "Zahlenzeile in Symbol-Ansicht" and shows the number row hints, as on the old page
        assertTrue(exists(app.getString(R.string.number_row_in_symbols)))
        assertFalse(exists(app.getString(R.string.number_row_hints)))
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_SHOW_NUMBER_ROW).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false))
        assertFalse(exists(app.getString(R.string.number_row_in_symbols)))
        assertTrue(exists(app.getString(R.string.number_row_hints)))
        // key hints off hides the hint source, as on the old page
        assertTrue(exists(app.getString(R.string.hint_source)))
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_SHOW_HINTS).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_HINTS, true))
        assertFalse(exists(app.getString(R.string.hint_source)))
    }

    @Test fun backToTheLettersShowsTheFourSwitchesOfErweitert() {
        start(german)
        val prefs = app.prefs()
        compose.onNodeWithTag(KEYS_RETURN_TAG).assert(hasText("1 von 4")) // only "after symbol and space" is on by default
        assertFalse(exists(app.getString(R.string.after_emoji)))
        compose.onNodeWithTag(KEYS_RETURN_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        listOf(R.string.after_symbol_and_space, R.string.after_numpad_and_space, R.string.after_emoji, R.string.after_clip)
            .forEach { assertTrue(exists(app.getString(it)), app.getString(it)) }
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_ABC_AFTER_EMOJI).performScrollTo().performClick()
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_ABC_AFTER_SYMBOL_SPACE).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(Settings.PREF_ABC_AFTER_EMOJI, false))
        assertFalse(prefs.getBoolean(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, true))
        compose.onNodeWithTag(KEYS_RETURN_TAG).assert(hasText("1 von 4"))
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_ABC_AFTER_CLIP).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(KEYS_RETURN_TAG).assert(hasText("2 von 4"))
    }

    @Test fun rowsOpenTheDialogsOfTheOldPagesAndWriteTheirValues() {
        start(german)
        val prefs = app.prefs()
        // language switch key behaviour: list of the old options page
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_LANGUAGE_SWITCH_KEY).assert(hasText(app.getString(R.string.switch_language)))
            .performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.language_switch_key_switch_both)).performClick()
        compose.waitForIdle()
        assertEquals("both", prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, null))
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_LANGUAGE_SWITCH_KEY).assert(hasText(app.getString(R.string.language_switch_key_switch_both)))

        // currencies: the text dialog of Erweitert
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_CUSTOM_CURRENCY_KEY).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.customize_currencies)))
        compose.onNode(hasSetTextAction()).performTextReplacement("€ $ £")
        compose.onNodeWithText(app.getString(android.R.string.ok)).performClick()
        compose.waitForIdle()
        assertEquals("€ $ £", prefs.getString(Settings.PREF_CUSTOM_CURRENCY_KEY, null))
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_CUSTOM_CURRENCY_KEY).assert(hasText("€ $ £"))

        // timestamp format: invalid text is refused, a valid one written
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_TIMESTAMP_FORMAT).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.timestamp_format_title)))
        compose.onNode(hasSetTextAction()).performTextReplacement("HH:mm")
        compose.onNodeWithText(app.getString(android.R.string.ok)).performClick()
        compose.waitForIdle()
        assertEquals("HH:mm", prefs.getString(Settings.PREF_TIMESTAMP_FORMAT, null))
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_TIMESTAMP_FORMAT).assert(hasText("HH:mm"))

        // long press delay: the slider dialog of Erweitert (100–700 ms)
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_KEY_LONGPRESS_TIMEOUT).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.prefs_key_longpress_timeout_settings)))
        compose.onNodeWithText(app.getString(android.R.string.cancel)).performClick()
        compose.waitForIdle()
        assertFalse(exists(app.getString(R.string.prefs_key_longpress_timeout_settings)))

        // popup order and hint source: the reorder dialog with the popup sources
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_POPUP_KEYS_ORDER).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.popup_order)))
        assertTrue(exists(app.getString(R.string.popup_keys_symbols)))
        compose.onNodeWithText(app.getString(android.R.string.cancel)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_POPUP_KEYS_HINT_ORDER).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(exists(app.getString(R.string.hint_source)))
        compose.onNodeWithText(app.getString(android.R.string.cancel)).performClick()
        compose.waitForIdle()

        // more popup keys: list of Erweitert
        compose.onNodeWithTag(KEYS_ROW_TAG + Settings.PREF_MORE_POPUP_KEYS).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos.morePopupKeysResId("all"))).performClick()
        compose.waitForIdle()
        assertEquals("all", prefs.getString(Settings.PREF_MORE_POPUP_KEYS, null))
    }

    @Test fun everyOldEntryOfThisAreaIsReachable() {
        start(german)
        // the pages this area opens; P119 checks them through SettingsAreas.entries
        val pages = SettingsAreas.entries(SettingsArea.KEYS).filter { it.visible() }.map { it.route }
        assertEquals(setOf(SettingsDestination.Languages, SettingsDestination.Preferences, SettingsDestination.Layouts), pages.toSet())
        pages.forEach { compose.onNodeWithTag(AREA_ROW_TAG + it).performScrollTo().performClick() }
        assertEquals(pages, routes)
        // every setting of the old options page (input, additional keys) except clipboard, vibration
        // and sound (still on the options page, row "Weitere Optionen"), and the matching Erweitert entries
        val onThisPage = listOf(Settings.PREF_SHOW_HINTS, Settings.PREF_POPUP_KEYS_HINT_ORDER, Settings.PREF_POPUP_KEYS_ORDER,
            Settings.PREF_SHOW_POPUP_HINTS, Settings.PREF_SHOW_TLD_POPUP_KEYS, Settings.PREF_POPUP_ON, Settings.PREF_SAVE_SUBTYPE_PER_APP,
            Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, Settings.PREF_SHOW_NUMBER_ROW, Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS,
            Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Settings.PREF_LANGUAGE_SWITCH_KEY, Settings.PREF_SHOW_EMOJI_KEY,
            Settings.PREF_SHOW_DPAD_KEY, Settings.PREF_REMOVE_REDUNDANT_POPUPS, Settings.PREF_KEY_LONGPRESS_TIMEOUT,
            Settings.PREF_CUSTOM_CURRENCY_KEY, Settings.PREF_MORE_POPUP_KEYS, Settings.PREF_TIMESTAMP_FORMAT)
        onThisPage.forEach { compose.onNodeWithTag(KEYS_ROW_TAG + it).assertExists() }
        compose.onNodeWithTag(KEYS_RETURN_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        listOf(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, Settings.PREF_ABC_AFTER_NUMPAD_SPACE, Settings.PREF_ABC_AFTER_EMOJI, Settings.PREF_ABC_AFTER_CLIP)
            .forEach { compose.onNodeWithTag(KEYS_ROW_TAG + it).assertExists() }
        assertTrue(exists("Weitere Optionen"))
    }

    @Test fun everyClickableIsAtLeast44dp() {
        start(german, english)
        val min = 44 * app.resources.displayMetrics.density - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(clickables.size >= 25, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    @Test fun theNavigationHostOpensTheLanguagePage() {
        initPreview(app)
        SubtypeSettings.addEnabledSubtype(app.prefs(), german)
        compose.setContent { Theme(dark = true) { SettingsNavHost(onClickBack = {}) } }
        compose.waitForIdle()
        compose.onNodeWithTag(TILE_TAG + SettingsDestination.AreaKeys).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaKeys).assertExists()
        val index = SubtypeSettings.getEnabledSubtypes(true).indexOf(german)
        compose.onNodeWithTag(LANGUAGE_CARD_TAG + index).performScrollTo().performClick()
        compose.waitForIdle()
        // the subtype page of upstream (its popup order section), not this area page
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaKeys).assertDoesNotExist()
        assertTrue(exists(app.getString(R.string.popup_order_and_hint_source)))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaKeys).assertExists()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderPage() {
        start(german, english, spanish, selected = german)
        lateinit var bitmap: Bitmap
        // captureToImage waits for a redraw that never comes in Robolectric (Fallstrick P15)
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val file = File("build/agent-previews/settings-area_keys-full.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // the first language card starts with the mockup blue at full opacity (it is the active one)
        val index = SubtypeSettings.getEnabledSubtypes(true).indexOf(german)
        val card = compose.onNodeWithTag(LANGUAGE_CARD_TAG + index).fetchSemanticsNode().boundsInRoot
        val expected = listOf(0xFF5B8CFF.toInt(), 0xFF22D3EE.toInt(), 0xFFFFB547.toInt())[index]
        val pixel = bitmap.getPixel(card.left.toInt() + 6, card.top.toInt() + 40)
        val d = listOf(16, 8, 0).maxOf { kotlin.math.abs((expected shr it and 0xFF) - (pixel shr it and 0xFF)) }
        assertTrue(d <= 40, "card colour #%08X".format(pixel))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0B0B0F).toArgb(), bitmap.getPixel(2, bitmap.height - 2))
    }
}
