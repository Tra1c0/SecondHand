// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsNavHost
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.BACK_TAG
import helium314.keyboard.settings.screens.CHIP_TAG
import helium314.keyboard.settings.screens.FOOTER_TAG
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.LOOK_CHANGE_TAG
import helium314.keyboard.settings.screens.LOOK_NAME_TAG
import helium314.keyboard.settings.screens.MainSettingsScreen
import helium314.keyboard.settings.screens.QUICK_INCOGNITO
import helium314.keyboard.settings.screens.QUICK_RGB
import helium314.keyboard.settings.screens.QUICK_SYMBOLS
import helium314.keyboard.settings.screens.QUICK_VIBRATION
import helium314.keyboard.settings.screens.SEARCH_TAG
import helium314.keyboard.settings.screens.START_TAG
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
import helium314.keyboard.settings.screens.TILE_TAG
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
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 38a: the new start page, the seven area routes and that no old entry got lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h1180dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsStartScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val routes = mutableListOf<String>()

    private fun start() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { MainSettingsScreen(onNavigate = { routes += it }, onClickBack = {}) } }
        compose.waitForIdle()
    }

    // Fallstrick P32: the settings singleton outlives the test, so reload the defaults
    @After fun reset() {
        app.prefs().edit().remove(Settings.PREF_ALWAYS_INCOGNITO_MODE).remove(Settings.PREF_VIBRATE_ON)
            .remove(AgentPreferences.SYMBOL_DRAWING).remove(RgbLighting.PREF).remove(Settings.PREF_THEME_STYLE).commit()
        Settings.getInstance().loadSettings(app)
    }

    @Test fun eachTileOpensItsAreaRouteInTheMockupGrid() {
        start()
        assertEquals(listOf("area_look", "area_typing", "area_keys", "area_gestures", "area_bar", "area_ai", "area_system"),
            SettingsArea.entries.map { it.route })
        val bounds = SettingsArea.entries.associateWith { compose.onNodeWithTag(TILE_TAG + it.route).fetchSemanticsNode().boundsInRoot }
        // 01 wide; 02/03 and 04/05 side by side; 06 and 07 wide
        val full = bounds.getValue(SettingsArea.LOOK).width
        listOf(SettingsArea.AI, SettingsArea.SYSTEM).forEach { assertEquals(full, bounds.getValue(it).width, 1f) }
        for ((left, right) in listOf(SettingsArea.TYPING to SettingsArea.KEYS, SettingsArea.GESTURES to SettingsArea.BAR)) {
            val l = bounds.getValue(left); val r = bounds.getValue(right)
            assertEquals(l.top, r.top, 1f)
            assertTrue(r.left > l.right, "$right right of $left")
            assertTrue(l.width < full / 2, "$left is a half tile")
        }
        // order from top to bottom
        val tops = listOf(SettingsArea.LOOK, SettingsArea.TYPING, SettingsArea.GESTURES, SettingsArea.AI, SettingsArea.SYSTEM).map { bounds.getValue(it).top }
        assertEquals(tops.sorted(), tops)
        // the exact texts of the mockup
        listOf("Aussehen", "Designs, RGB-Licht, Größe, Schrift", "Tippen & Korrektur", "Autokorrektur, Vorschläge, Lernen",
            "Tasten & Sprachen", "Sprachen, Zahlenzeile, Popups", "Gesten & Gefühl", "Leertaste, Malen, Vibration, Ton",
            "Leiste & Befehle", "Leiste, @-Befehle, Zwischenablage", "KI & Automationen",
            "Anbieter, Skill-Tasten, Workflows, Diktat", "System & Daten", "Profile, Sicherung, Updates, Diagnose",
            "Einstellungen", "Schnell umschalten", "Dein Look", "Ändern")
            .forEach { assertTrue(compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty(), "missing text: $it") }

        SettingsArea.entries.forEach { compose.onNodeWithTag(TILE_TAG + it.route).performScrollTo().performClick() }
        assertEquals(SettingsArea.entries.map { it.route }, routes)
        // "Ändern" on the look card opens Aussehen
        compose.onNodeWithTag(LOOK_CHANGE_TAG).performScrollTo().performClick()
        assertEquals(SettingsDestination.AreaLook, routes.last())
    }

    @Test fun quickTogglesSwitchTheRealPreferences() {
        start()
        val prefs = app.prefs()
        fun state(tag: String) = compose.onNodeWithTag(CHIP_TAG + tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
        listOf(QUICK_INCOGNITO, QUICK_RGB, QUICK_VIBRATION, QUICK_SYMBOLS).forEach {
            compose.onNodeWithTag(CHIP_TAG + it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            assertEquals(ToggleableState.Off, state(it), it)
        }
        listOf(QUICK_INCOGNITO, QUICK_RGB, QUICK_VIBRATION, QUICK_SYMBOLS).forEach { compose.onNodeWithTag(CHIP_TAG + it).performClick() }
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false))
        assertTrue(prefs.getBoolean(Settings.PREF_VIBRATE_ON, false))
        assertTrue(prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, false))
        assertTrue(RgbLighting.read(prefs).enabled)
        listOf(QUICK_INCOGNITO, QUICK_RGB, QUICK_VIBRATION, QUICK_SYMBOLS).forEach { assertEquals(ToggleableState.On, state(it), it) }

        // the RGB chip keeps every other light setting
        compose.onNodeWithTag(CHIP_TAG + QUICK_RGB).performClick()
        compose.onNodeWithTag(CHIP_TAG + QUICK_VIBRATION).performClick()
        compose.waitForIdle()
        assertFalse(RgbLighting.read(prefs).enabled)
        assertEquals(RgbSettings.DEFAULT, RgbLighting.read(prefs))
        assertFalse(prefs.getBoolean(Settings.PREF_VIBRATE_ON, true))
        assertEquals(ToggleableState.Off, state(QUICK_RGB))
    }

    @Test fun lookCardNamesStyleAndLightPattern() {
        app.prefs().edit().putString(Settings.PREF_THEME_STYLE, "Rounded").commit()
        RgbLighting.write(app.prefs(), RgbSettings(enabled = true, pattern = RgbPattern.TOPO))
        start()
        compose.onNodeWithTag(LOOK_NAME_TAG).assert(hasText("Abgerundet · Licht: Höhenlinien"))
    }

    @Test fun footerShowsTheRealBuildNumberAndOpensItsPages() {
        start()
        compose.onNodeWithText("Updates · Build ${BuildConfig.VERSION_CODE}").performScrollTo().assertIsDisplayed()
        listOf(SettingsDestination.AgentDiagnose, SettingsDestination.AgentUpdates, SettingsDestination.About)
            .forEach { compose.onNodeWithTag(FOOTER_TAG + it).performScrollTo().performClick() }
        assertEquals(listOf(SettingsDestination.AgentDiagnose, SettingsDestination.AgentUpdates, SettingsDestination.About), routes)
    }

    @Test fun searchFindsTheExistingSettings() {
        start()
        compose.onNodeWithTag(SEARCH_TAG).performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("Vibration bei")
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText(app.getString(R.string.vibrate_on_keypress)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun everyClickableIsAtLeast44dp() {
        start()
        val min = with(compose.density) { 44.dpToPxCompat() } - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(clickables.size >= 4 + 7 + 3 + 2, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    private fun Int.dpToPxCompat() = this * app.resources.displayMetrics.density

    /**
     * The 22 entries of the old start page (MainSettingsScreen before 38a, callbacks wired in
     * SettingsNavHost), in their old order. Each must be on an area page.
     */
    private val oldMainList = listOf(
        SettingsDestination.AgentAi, SettingsDestination.AgentSkills, SettingsDestination.AgentWorkflows, SettingsDestination.AgentDictation,
        SettingsDestination.AgentScreenshots, SettingsDestination.AgentCommands, SettingsDestination.AgentLearning,
        SettingsDestination.AgentProfiles, SettingsDestination.AgentUpdates, SettingsDestination.Languages, SettingsDestination.Preferences,
        SettingsDestination.Appearance, SettingsDestination.Toolbar, SettingsDestination.GestureTyping, SettingsDestination.AgentSymbols,
        SettingsDestination.DataGathering, SettingsDestination.TextCorrection, SettingsDestination.Layouts, SettingsDestination.Dictionaries,
        SettingsDestination.Advanced, SettingsDestination.AgentDiagnose, SettingsDestination.About,
    )

    /** Pages that only open from another page (with an argument or as a sub-page), not from the start. */
    private val subPages = setOf(SettingsDestination.Settings, SettingsDestination.DataReview, SettingsDestination.Debug,
        SettingsDestination.Colors, SettingsDestination.ColorsNight, SettingsDestination.PersonalDictionaries,
        SettingsDestination.PersonalDictionary, SettingsDestination.Subtype)

    @Test fun everyOldMainEntryAndEveryPageIsOnAnAreaPage() {
        assertEquals(22, oldMainList.size)
        val mapped = SettingsArea.entries.flatMap { area -> SettingsAreas.entries(area).map { it.route } }.toSet()
        oldMainList.forEach { assertTrue(it in mapped, "old entry $it is on no area page") }
        // every destination of the navigation host: an area, on an area page, or a sub-page
        val destinations = SettingsDestination::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.isAccessible = true; it.get(null) as String }
        assertTrue(destinations.size > 30, "destinations: $destinations")
        val areas = SettingsArea.entries.map { it.route }.toSet()
        destinations.forEach { assertTrue(it in mapped || it in areas || it in subPages, "destination $it is not reachable from an area") }
        // conditional entries follow the same conditions as on the old start page
        val gestures = SettingsAreas.entries(SettingsArea.GESTURES).single { it.route == SettingsDestination.GestureTyping }
        assertEquals(helium314.keyboard.latin.utils.JniUtils.sHaveGestureLib, gestures.visible())
    }

    @Test fun eachAreaPageHasItsHeroAndOpensItsPages() {
        initPreview(app)
        var area by androidx.compose.runtime.mutableStateOf(SettingsArea.LOOK)
        var back = 0
        compose.setContent { Theme(dark = true) { AgentAreaScreen(area, onNavigate = { routes += it }, onClickBack = { back++ }) } }
        for (a in SettingsArea.entries) {
            area = a
            compose.waitForIdle()
            compose.onNodeWithTag(AREA_TAG + a.route).assertExists()
            compose.onNodeWithTag(HERO_TAG).assert(hasText(a.number)).assert(hasText(app.getString(a.title)))
                .assert(hasText(app.getString(a.subtitle)))
            routes.clear()
            val visible = SettingsAreas.entries(a).filter { it.visible() }
            assertTrue(visible.size >= 3, "${a.route}: ${visible.size} rows")
            visible.forEach { compose.onNodeWithTag(AREA_ROW_TAG + it.route).performScrollTo().performClick() }
            assertEquals(visible.map { it.route }, routes, a.route)
        }
        compose.onNodeWithTag(BACK_TAG).performScrollTo().performClick()
        assertEquals(1, back)
        // 38f: "Symbolleiste" is "Leiste" everywhere; the bar page opens the toolbar page from "Knöpfe wählen und sortieren"
        area = SettingsArea.BAR
        compose.waitForIdle()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.Toolbar).assert(hasText("Knöpfe wählen und sortieren"))
        assertEquals("Leiste", app.getString(R.string.settings_screen_toolbar))
    }

    @Test fun theNavigationHostOpensAreasAndKeepsOldRoutes() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { SettingsNavHost(onClickBack = {}) } }
        compose.waitForIdle()
        compose.onNodeWithTag(START_TAG).assertExists()
        compose.onNodeWithTag(TILE_TAG + SettingsDestination.AreaSystem).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaSystem).assertExists()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.About).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaSystem).assertDoesNotExist()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaSystem).assertExists()
        // an old route still opens directly (intents OPEN_SETTINGS_DESTINATION / OPEN_AI_SETTINGS use this)
        SettingsDestination.navigateTo(SettingsDestination.Appearance)
        compose.waitUntil(2_000) { compose.onAllNodesWithText(app.getString(R.string.settings_screen_appearance)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaSystem).assertDoesNotExist()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderStartPageAndAreaPages() {
        initPreview(app)
        var area by androidx.compose.runtime.mutableStateOf<SettingsArea?>(null)
        compose.setContent {
            Theme(dark = true) {
                val a = area
                if (a == null) MainSettingsScreen(onNavigate = {}, onClickBack = {}) else AgentAreaScreen(a, {}, {})
            }
        }
        compose.waitForIdle()
        val image = capture("settings-start")
        // page background and the first tile's gradient start as in the mockup
        assertEquals(AgentSettingsTokens.Background.toArgb(), image.getPixel(2, image.height - 2))
        val tile = compose.onNodeWithTag(TILE_TAG + SettingsDestination.AreaLook).fetchSemanticsNode().boundsInRoot
        assertClose(0xFFFF4D8D.toInt(), image.getPixel(tile.left.toInt() + 20, tile.top.toInt() + 20), 40)
        assertClose(0xFFFFB547.toInt(), image.getPixel(tile.right.toInt() - 20, tile.bottom.toInt() - 20), 40)
        for (a in SettingsArea.entries) {
            area = a
            compose.waitForIdle()
            // 38c/38f: SettingsTypingAreaTest and SettingsBarAreaTest write their whole pages as settings-<route>.png
            val page = capture(if (a == SettingsArea.TYPING || a == SettingsArea.BAR) "settings-${a.route}-top" else "settings-${a.route}")
            val hero = compose.onNodeWithTag(HERO_TAG).fetchSemanticsNode().boundsInRoot
            assertClose(a.from.toArgb(), page.getPixel(hero.left.toInt() + 24, hero.top.toInt() + 24), 40)
        }
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
