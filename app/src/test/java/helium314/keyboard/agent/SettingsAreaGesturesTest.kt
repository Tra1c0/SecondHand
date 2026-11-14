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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.BACK_TAG
import helium314.keyboard.settings.screens.CHOICE_TAG
import helium314.keyboard.settings.screens.GESTURE_TAG
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.ROW_TAG
import helium314.keyboard.settings.screens.SPACE_BAR_TAG
import helium314.keyboard.settings.screens.SWIPE_DRAW
import helium314.keyboard.settings.screens.SWIPE_GESTURE
import helium314.keyboard.settings.screens.SWIPE_NONE
import helium314.keyboard.settings.screens.SYMBOL_CALIBRATION
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.TOUCH_CALIBRATION
import org.junit.After
import org.junit.Before
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 38e: the area page "Gesten & Gefühl" (mockup Gesten.dc.html) on the real preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2200dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsAreaGesturesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val routes = mutableListOf<String>()
    private var back = 0
    private val hadLib = JniUtils.sHaveGestureLib

    @Before fun clear() {
        app.prefs().edit().clear().commit()
        SymbolDrawing.saveOwnSymbols(app, emptyList())
    }

    // Fallstrick P32: the settings singleton outlives the test, so reload the defaults
    @After fun reset() {
        JniUtils.sHaveGestureLib = hadLib
        TouchModel.enabled = false
        app.prefs().edit().clear().commit()
        Settings.getInstance().loadSettings(app)
    }

    private fun page() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentAreaScreen(SettingsArea.GESTURES, onNavigate = { routes += it }, onClickBack = { back++ }) } }
        compose.waitForIdle()
    }

    private fun gesture(g: SpaceGesture) = compose.onNodeWithTag(GESTURE_TAG + g.name)
    private fun row(key: String) = compose.onNodeWithTag(ROW_TAG + key)
    private fun toggle(key: String) = row(key).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
    private fun selected(tag: String) = compose.onNodeWithTag(CHOICE_TAG + tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)

    @Test fun pageHasTheMockupStructure() {
        page()
        compose.onNodeWithTag(AREA_TAG + SettingsArea.GESTURES.route).assertExists()
        compose.onNodeWithTag(HERO_TAG).assert(hasText("04")).assert(hasText("Gesten & Gefühl"))
            .assert(hasText("Wischen, Malen und was du dabei spürst und hörst."))
        listOf("LEERTASTE", "Tick · Tick · Überdehnen: erst am letzten Punkt passiert die Aktion.", "ÜBER DIE BUCHSTABEN WISCHEN",
            "NICHTS", "Wischen über Buchstaben tut nichts", "SYMBOLE MALEN", "→ ≠ € ✓ ♥ und eigene Symbole", "GESTENSCHREIBEN",
            "Symbole malen", "Eigene Symbole", "Dreimal malen, Text wählen", "Aus Korrekturen lernen", "Deine Handschrift merken",
            "Kalibrieren", "Jedes Symbol zweimal malen", "Spur anzeigen", "Wie lange sie bleibt",
            "Vibration & Ton", "Ton bei Tastendruck", "Lautstärke einstellbar",
            "Treffen & Wischen", "Trefferzonen anpassen", "Lernt, wo du wirklich tippst", "Trefferzonen kalibrieren",
            "Kurzen Text abtippen", "Löschen durch Wischen", "Über die Löschtaste ziehen", "Touchpad-Empfindlichkeit", "Für den Touchpad-Modus")
            .forEach { assertTrue(compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty(), "missing text: $it") }
        // the space bar shows the language of the keyboard
        val language = SubtypeLocaleUtils.getSubtypeLanguageDisplayName(SubtypeSettings.getSelectedSubtype(app.prefs()).locale())
        assertTrue(language.isNotBlank())
        compose.onNodeWithTag(SPACE_BAR_TAG).assert(hasText(language))
        // sections in the order of the mockup
        val tops = listOf("LEERTASTE", "ÜBER DIE BUCHSTABEN WISCHEN", "Aus Korrekturen lernen", "Ton bei Tastendruck", "Trefferzonen anpassen")
            .map { compose.onAllNodesWithText(it).fetchSemanticsNodes().first().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        // the six gestures in two columns: ←/→, ↑/↓, Halten/Doppeltipp
        val bounds = SpaceGesture.entries.map { gesture(it).fetchSemanticsNode().boundsInRoot }
        for (i in 0 until 6 step 2) {
            assertEquals(bounds[i].top, bounds[i + 1].top, 1f)
            assertTrue(bounds[i + 1].left > bounds[i].right)
            assertEquals(bounds[i].width, bounds[i + 1].width, 1f)
        }
        compose.onNodeWithTag(BACK_TAG).performClick()
        assertEquals(1, back)
    }

    @Test fun gestureGridShowsTheRealActionsAndOpensTheirLists() {
        val prefs = app.prefs()
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_LEFT, SpaceAction.UNDO)
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_UP, SpaceAction.COMMAND_LIST)
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_DOWN, SpaceAction.HIDE_KEYBOARD)
        SpaceGestures.write(prefs, SpaceGesture.HOLD, SpaceAction.ACTION_WHEEL)
        page()
        gesture(SpaceGesture.SWIPE_LEFT).assert(hasText("← Wischen")).assert(hasText("Rückgängig"))
        gesture(SpaceGesture.SWIPE_UP).assert(hasText("↑ Wischen")).assert(hasText("Befehlsliste"))
        gesture(SpaceGesture.SWIPE_DOWN).assert(hasText("↓ Wischen")).assert(hasText("Ausblenden"))
        gesture(SpaceGesture.HOLD).assert(hasText("Halten")).assert(hasText("Aktionsrad"))
        // without an own value: what the older settings do (HeliBoard's horizontal swipe moves the cursor)
        assertEquals(SpaceAction.MOVE_CURSOR, SpaceGestures.resolve(prefs, SpaceGesture.SWIPE_RIGHT))
        gesture(SpaceGesture.SWIPE_RIGHT).assert(hasText("→ Wischen")).assert(hasText("Cursor bewegen"))
        // double tap without action: HeliBoard's double space types a period
        gesture(SpaceGesture.DOUBLE_TAP).assert(hasText("Doppeltipp")).assert(hasText("Punkt + Leer"))
        SpaceGesture.entries.forEach { gesture(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)) }

        // the list of the old space bar page: title, choices, the current one selected
        gesture(SpaceGesture.SWIPE_LEFT).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nach links wischen").assertExists()
        compose.onNodeWithText("Wiederholen").performClick()
        compose.waitForIdle()
        assertEquals(SpaceAction.REDO, SpaceGestures.stored(prefs, SpaceGesture.SWIPE_LEFT))
        compose.onNodeWithText("Nach links wischen").assertDoesNotExist()
        gesture(SpaceGesture.SWIPE_LEFT).assert(hasText("Wiederholen"))

        // "Standard" removes the own value, the gesture follows the older settings again
        gesture(SpaceGesture.HOLD).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Standard").performClick()
        compose.waitForIdle()
        assertNull(SpaceGestures.stored(prefs, SpaceGesture.HOLD))
        gesture(SpaceGesture.HOLD).assert(hasText("Tastatur wählen"))
        assertEquals(SpaceAction.SWITCH_INPUT_METHOD, SpaceGestures.resolve(prefs, SpaceGesture.HOLD))

        // the double tap gets an action from its list (no move cursor there, as on the old page)
        gesture(SpaceGesture.DOUBLE_TAP).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Doppeltippen").assertExists()
        assertTrue(compose.onAllNodesWithText("Cursor bewegen").fetchSemanticsNodes().size == 1, "only the grid shows cursor movement")
        compose.onNodeWithText("Emoji").performClick()
        compose.waitForIdle()
        assertEquals(SpaceAction.EMOJI, SpaceGestures.stored(prefs, SpaceGesture.DOUBLE_TAP))
        gesture(SpaceGesture.DOUBLE_TAP).assert(hasText("Emoji"))
    }

    @Test fun doubleTapShowsNothingWithoutPeriod() {
        app.prefs().edit().putBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, false).commit()
        page()
        gesture(SpaceGesture.DOUBLE_TAP).assert(hasText("Nichts"))
    }

    @Test fun swipeChoiceCardsMapToTheTwoSwitches() {
        JniUtils.sHaveGestureLib = false
        val prefs = app.prefs()
        page()
        // gesture typing is on by default, but without the library swiping does nothing
        assertEquals(true, selected(SWIPE_NONE))
        compose.onNodeWithTag(CHOICE_TAG + SWIPE_GESTURE).assertIsNotEnabled()
            .assert(hasText("Bibliothek fehlt: unter System & Daten → Erweitert laden"))
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.GestureTyping).assertDoesNotExist()
        compose.onNodeWithTag(CHOICE_TAG + SWIPE_GESTURE).performClick()
        compose.waitForIdle()
        assertEquals(true, selected(SWIPE_NONE))
        assertFalse(prefs.contains(AgentPreferences.SYMBOL_DRAWING))

        compose.onNodeWithTag(CHOICE_TAG + SWIPE_DRAW).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, false))
        assertEquals(true, selected(SWIPE_DRAW))
        assertEquals(false, selected(SWIPE_NONE))

        compose.onNodeWithTag(CHOICE_TAG + SWIPE_NONE).performClick()
        compose.waitForIdle()
        assertFalse(prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, true))
        assertFalse(prefs.getBoolean(Settings.PREF_GESTURE_INPUT, true))
        assertEquals(true, selected(SWIPE_NONE))
    }

    @Test fun gestureTypingNeedsTheLibraryAndOpensItsPage() {
        JniUtils.sHaveGestureLib = true
        val prefs = app.prefs()
        prefs.edit().putBoolean(AgentPreferences.SYMBOL_DRAWING, true).putBoolean(Settings.PREF_GESTURE_INPUT, false).commit()
        page()
        assertEquals(true, selected(SWIPE_DRAW))
        compose.onNodeWithTag(CHOICE_TAG + SWIPE_GESTURE).assertIsEnabled().assert(hasText("Wörter wischen (braucht Bibliothek)")).performClick()
        compose.waitForIdle()
        assertFalse(prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, true))
        assertTrue(prefs.getBoolean(Settings.PREF_GESTURE_INPUT, false))
        assertEquals(true, selected(SWIPE_GESTURE))
        // drawn symbols keep the stored gesture typing value (they win over it, stage 31)
        compose.onNodeWithTag(CHOICE_TAG + SWIPE_DRAW).performClick()
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, false))
        assertTrue(prefs.getBoolean(Settings.PREF_GESTURE_INPUT, false))
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.GestureTyping).performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.GestureTyping), routes)
    }

    @Test fun switchesWriteTheRealPreferences() {
        val prefs = app.prefs()
        page()
        // vibration: duration and "do not disturb" only while it is on, as on the options page
        row(Settings.PREF_VIBRATION_DURATION_SETTINGS).assertDoesNotExist()
        row(Settings.PREF_KEYPRESS_SOUND_VOLUME).assertDoesNotExist()
        val switches = listOf(AgentPreferences.SYMBOL_LEARNING to false, Settings.PREF_VIBRATE_ON to false, Settings.PREF_SOUND_ON to false,
            AgentPreferences.ADAPT_TOUCH to false, Settings.PREF_DELETE_SWIPE to true)
        switches.forEach { (key, default) ->
            row(key).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            assertEquals(if (default) ToggleableState.On else ToggleableState.Off, toggle(key), key)
            row(key).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(!default, prefs.getBoolean(key, default), key)
            assertEquals(if (default) ToggleableState.Off else ToggleableState.On, toggle(key), key)
        }
        assertTrue(TouchModel.enabled, "the hit zones follow the switch at once")
        row(Settings.PREF_VIBRATION_DURATION_SETTINGS).assert(hasText("Vibrationsdauer")).assert(hasText("System"))
        row(Settings.PREF_VIBRATE_IN_DND_MODE).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, false))
        row(Settings.PREF_KEYPRESS_SOUND_VOLUME).assert(hasText("Lautstärke")).assert(hasText("System"))
    }

    @Test fun sliderRowsShowRealValuesAndOpenTheOldDialogs() {
        val prefs = app.prefs()
        prefs.edit().putBoolean(Settings.PREF_VIBRATE_ON, true).putInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, 30)
            .putInt(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION, 500).putInt(Settings.PREF_TOUCHPAD_SENSITIVITY, 70).commit()
        page()
        row(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION).assert(hasText("0,6 s"))
        row(Settings.PREF_VIBRATION_DURATION_SETTINGS).assert(hasText(app.getString(R.string.abbreviation_unit_milliseconds, "30")))
        row(Settings.PREF_TOUCHPAD_SENSITIVITY).assert(hasText("70"))

        row(Settings.PREF_TOUCHPAD_SENSITIVITY).performScrollTo().performClick()
        compose.waitForIdle()
        // the row and the title of the old slider dialog
        assertEquals(2, compose.onAllNodesWithText(app.getString(R.string.touchpad_sensitivity)).fetchSemanticsNodes().size)
        compose.onNodeWithText("Standard").performClick()
        compose.waitForIdle()
        assertFalse(prefs.contains(Settings.PREF_TOUCHPAD_SENSITIVITY))
        row(Settings.PREF_TOUCHPAD_SENSITIVITY).assert(hasText("50"))

        row(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Anzeigedauer der Gestenspur").assertExists()
        compose.onNodeWithText(app.getString(R.string.abbreviation_unit_milliseconds, "600")).assertExists() // the old dialog text: stored value + 100 ms
        compose.onNodeWithText("Standard").performClick()
        compose.waitForIdle()
        row(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION).assert(hasText("0,9 s"))
    }

    @Test fun theRestOfTheSpaceBarPageAppearsUnderItsConditions() {
        val prefs = app.prefs()
        page()
        row(Settings.PREF_TOUCHPAD_EDGE_SCROLL).assertDoesNotExist()
        row(Settings.PREF_LANGUAGE_SWIPE_DISTANCE).assertDoesNotExist()
        gesture(SpaceGesture.SWIPE_UP).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Touchpad-Modus").performClick()
        compose.waitForIdle()
        gesture(SpaceGesture.SWIPE_LEFT).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sprache wechseln").performClick()
        compose.waitForIdle()
        row(Settings.PREF_TOUCHPAD_EDGE_SCROLL).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(prefs.getBoolean(Settings.PREF_TOUCHPAD_EDGE_SCROLL, true))
        row(Settings.PREF_LANGUAGE_SWIPE_DISTANCE).assert(hasText("5")).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.prefs_language_swipe_distance)).assertExists()
    }

    @Test fun rowsOpenTheSymbolPageAndTheHitZoneCalibration() {
        page()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentSymbols).assert(hasText("Eigene Symbole")).assert(hasText("0"))
            .performScrollTo().performClick()
        row(SYMBOL_CALIBRATION).performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.AgentSymbols, SettingsDestination.AgentSymbols), routes)
        row(TOUCH_CALIBRATION).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(TouchCalibration.TEXT).assertExists()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(TouchCalibration.TEXT).assertDoesNotExist()
    }

    @Test fun everyClickableIsAtLeast44dp() {
        app.prefs().edit().putBoolean(Settings.PREF_VIBRATE_ON, true).putBoolean(Settings.PREF_SOUND_ON, true).commit()
        page()
        val min = 44 * app.resources.displayMetrics.density - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(clickables.size >= 1 + 6 + 3 + 4 + 5 + 4, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    /** The whole page as in the mockup (390 dp wide), with its state: vibration on, drawn symbols chosen. */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderGesturePage() {
        val prefs = app.prefs()
        prefs.edit().putBoolean(Settings.PREF_VIBRATE_ON, true).putBoolean(AgentPreferences.SYMBOL_DRAWING, true).commit()
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_LEFT, SpaceAction.UNDO)
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_RIGHT, SpaceAction.REDO)
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_UP, SpaceAction.COMMAND_LIST)
        SpaceGestures.write(prefs, SpaceGesture.SWIPE_DOWN, SpaceAction.HIDE_KEYBOARD)
        SpaceGestures.write(prefs, SpaceGesture.HOLD, SpaceAction.ACTION_WHEEL)
        page()
        val image = capture("settings-area_gestures-full")
        assertEquals(AgentSettingsTokens.Background.toArgb(), image.getPixel(2, image.height - 2))
        val hero = compose.onNodeWithTag(HERO_TAG).fetchSemanticsNode().boundsInRoot
        assertClose(SettingsArea.GESTURES.from.toArgb(), image.getPixel(hero.left.toInt() + 24, hero.top.toInt() + 24), 40)
        // the bar: its grey, the two detent dots and the light last dot (22 %, 42 %, 72 % of the width)
        val bar = compose.onNodeWithTag(SPACE_BAR_TAG).fetchSemanticsNode().boundsInRoot
        val d = app.resources.displayMetrics.density
        assertClose(AgentSettingsTokens.Border.toArgb(), image.getPixel((bar.left + bar.width * 0.1f).toInt(), (bar.top + bar.height / 2).toInt()), 8)
        assertClose(0xFFC084FC.toInt(), image.getPixel((bar.left + bar.width * 0.22f + 6 * d).toInt(), (bar.top + 32 * d).toInt()), 30)
        assertClose(0xFFC084FC.toInt(), image.getPixel((bar.left + bar.width * 0.42f + 6 * d).toInt(), (bar.top + 32 * d).toInt()), 30)
        assertClose(0xFFE9D5FF.toInt(), image.getPixel((bar.left + bar.width * 0.72f + 10 * d).toInt(), (bar.top + 32 * d).toInt()), 30)
        // the glow lies around the last dot, not around the others
        val glow = image.getPixel((bar.left + bar.width * 0.72f - 4 * d).toInt(), (bar.top + 32 * d).toInt())
        val plain = image.getPixel((bar.left + bar.width * 0.22f - 6 * d).toInt(), (bar.top + 32 * d).toInt())
        assertTrue((glow shr 16 and 0xFF) > (plain shr 16 and 0xFF) + 20, "glow #%08X vs #%08X".format(glow, plain))
    }

    private fun assertClose(expected: Int, actual: Int, tolerance: Int) {
        val d = listOf(16, 8, 0).maxOf { shift -> kotlin.math.abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF)) }
        assertTrue(d <= tolerance, "expected ~#%08X, got #%08X".format(expected, actual))
    }

    // captureToImage waits for a redraw that never comes in Robolectric (Fallstrick P15)
    private fun capture(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val file = File("build/agent-previews/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
