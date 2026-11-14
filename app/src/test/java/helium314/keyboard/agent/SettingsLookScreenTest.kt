// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.Intent
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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import dev.agentkeyboard.core.RgbLayer
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.BACK_TAG
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.LOOK_DIALOG_TAG
import helium314.keyboard.settings.screens.LOOK_EDIT_TAG
import helium314.keyboard.settings.screens.LOOK_NEW_TAG
import helium314.keyboard.settings.screens.LOOK_RGB_SUMMARY_TAG
import helium314.keyboard.settings.screens.LOOK_RGB_SWITCH_TAG
import helium314.keyboard.settings.screens.LOOK_ROW_TAG
import helium314.keyboard.settings.screens.LOOK_TILE_TAG
import helium314.keyboard.settings.screens.ROW_CUSTOM_FONT
import helium314.keyboard.settings.screens.ROW_DAY_NIGHT
import helium314.keyboard.settings.screens.ROW_EMOJI
import helium314.keyboard.settings.screens.ROW_FONT_SIZE
import helium314.keyboard.settings.screens.ROW_GAP
import helium314.keyboard.settings.screens.ROW_HEIGHT
import helium314.keyboard.settings.screens.ROW_IMAGE
import helium314.keyboard.settings.screens.ROW_NAVBAR
import helium314.keyboard.settings.screens.ROW_PADDING
import helium314.keyboard.settings.screens.ROW_SPLIT
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage 38b: the area page "Aussehen" after Aussehen.dc.html, on the real preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2200dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsLookScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private val routes = mutableListOf<String>()

    private fun files() = listOf(Settings.getCustomFontFile(app), Settings.getCustomEmojiFontFile(app)) +
        listOf(false, true).flatMap { night -> listOf(false, true).map { Settings.getCustomBackgroundFile(app, night, it) } }

    // Fallstrick P32: the settings singleton outlives the test, so reload the defaults
    @Before @After fun clear() {
        prefs.edit().clear().commit()
        MyDesigns.imageDir(app).deleteRecursively()
        files().forEach { it.delete() }
        Settings.getInstance().loadSettings(app)
    }

    private fun show() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentAreaScreen(SettingsArea.LOOK, onNavigate = { routes += it }, onClickBack = {}) } }
        compose.waitForIdle()
    }

    private fun row(tag: String) = compose.onNodeWithTag(LOOK_ROW_TAG + tag).performScrollTo()
    private fun tile(id: String) = compose.onNodeWithTag(LOOK_TILE_TAG + id)
    private fun text(res: Int) = app.getString(res)
    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun toggle(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
    private fun selected(id: String) = tile(id).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)

    private fun design(id: String, name: String) = MyDesigns.Design(
        id = id, name = name, style = KeyboardTheme.STYLE_ROUNDED,
        colors = mapOf(KeyboardTheme.COLOR_BACKGROUND to 0xFF0D1B3D.toInt(), KeyboardTheme.COLOR_ACCENT to 0xFF4C6FFF.toInt()),
    )

    @Test fun pageHasTheMockupTextsInTheMockupOrder() {
        // "Tastenabstand" only with key borders, as on the old appearance page
        prefs.edit().putBoolean(Settings.PREF_THEME_KEY_BORDERS, true).commit()
        show()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaLook).assertExists()
        compose.onNodeWithTag(HERO_TAG).assert(hasText("01")).assert(hasText("Aussehen")).assert(hasText("Wie deine Tastatur aussieht und leuchtet."))
        listOf("Einstellungen", "Designs", "tippen = verwenden", "+ Neues Design", "Bearbeiten", "RGB-Licht",
            "Größe & Form", "Tastaturhöhe", "Hoch, quer, eingeklappt", "Tastenabstand", "Lücke zwischen den Tasten",
            "Ränder", "Unten und seitlich", "Geteilte Tastatur", "Im Querformat teilen",
            "Schrift & Emoji", "Schriftgröße", "Tasten und Hinweise", "Eigene Schrift", "Aus Datei laden", "Emoji",
            "Größe, Hautton, Emoji-Schrift", "Hintergrund & Farben", "Hintergrundbild", "Hoch- und Querformat",
            "Tag/Nacht automatisch", "Folgt Android", "Navigationsleiste einfärben", "Unter der Tastatur",
            "Alle Aussehen-Einstellungen")
            .forEach { assertTrue(exists(it), "missing text: $it") }
        // order from top to bottom: hero, designs, RGB, the three sections, their rows in mockup order
        fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        fun topOfText(t: String) = compose.onNodeWithText(t).fetchSemanticsNode().boundsInRoot.top
        val tops = listOf(top(HERO_TAG), top(LOOK_NEW_TAG), top(AREA_ROW_TAG + SettingsDestination.AgentRgb), topOfText("Größe & Form"),
            top(LOOK_ROW_TAG + ROW_HEIGHT), top(LOOK_ROW_TAG + ROW_GAP), top(LOOK_ROW_TAG + ROW_PADDING), top(LOOK_ROW_TAG + ROW_SPLIT),
            topOfText("Schrift & Emoji"), top(LOOK_ROW_TAG + ROW_FONT_SIZE), top(LOOK_ROW_TAG + ROW_CUSTOM_FONT), top(LOOK_ROW_TAG + ROW_EMOJI),
            topOfText("Hintergrund & Farben"), top(LOOK_ROW_TAG + ROW_IMAGE), top(LOOK_ROW_TAG + ROW_DAY_NIGHT), top(LOOK_ROW_TAG + ROW_NAVBAR),
            top(AREA_ROW_TAG + SettingsDestination.Appearance))
        assertEquals(tops.sorted(), tops)
        // the old card "Seiten" is gone
        assertFalse(exists(text(R.string.agent_area_pages)))
    }

    @Test fun rowsShowTheRealDefaultValues() {
        show()
        mapOf(ROW_HEIGHT to "100 %", ROW_PADDING to "Standard", ROW_SPLIT to "Aus", ROW_FONT_SIZE to "100 %",
            ROW_CUSTOM_FONT to "Keine", ROW_EMOJI to "Standard", ROW_IMAGE to "Keins")
            .forEach { (tag, value) -> row(tag).assert(hasText(value)) }
        // switches show the real defaults (Android 9: day/night and navigation bar colour are off by default)
        assertEquals(ToggleableState.Off, toggle(LOOK_ROW_TAG + ROW_DAY_NIGHT))
        assertEquals(ToggleableState.Off, toggle(LOOK_ROW_TAG + ROW_NAVBAR))
        // without key borders (the default) there is no key gap row, as on the old appearance page
        compose.onNodeWithTag(LOOK_ROW_TAG + ROW_GAP).assertDoesNotExist()
    }

    @Test fun keyGapRowFollowsTheKeyBorders() {
        prefs.edit().putBoolean(Settings.PREF_THEME_KEY_BORDERS, true).commit()
        show()
        row(ROW_GAP).assert(hasText("Tastenabstand")).assert(hasText("1,0"))
        prefs.edit().putBoolean(Settings.PREF_THEME_KEY_BORDERS, false).commit()
        compose.waitForIdle()
        compose.onNodeWithTag(LOOK_ROW_TAG + ROW_GAP).assertDoesNotExist()
    }

    @Test fun fileOnlyChangesShowAfterResumeDeleteAndClosingTheDialog() {
        show()
        row(ROW_CUSTOM_FONT).assert(hasText("Keine"))
        row(ROW_IMAGE).assert(hasText("Keins"))
        // the file picker pauses the activity; its result writes only a file
        Settings.getCustomFontFile(app).apply { parentFile?.mkdirs() }.writeText("font")
        Settings.getCustomBackgroundFile(app, false, false).apply { parentFile?.mkdirs() }.writeText("image")
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        row(ROW_CUSTOM_FONT).assert(hasText("Eigene"))
        row(ROW_IMAGE).assert(hasText("Eigenes"))
        // "Löschen" of the own font changes no preference either
        row(ROW_CUSTOM_FONT).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.delete)).performClick()
        compose.waitForIdle()
        row(ROW_CUSTOM_FONT).assert(hasText("Keine"))
        // a file removed from the image dialog shows once the dialog is closed
        row(ROW_IMAGE).performClick()
        compose.waitForIdle()
        Settings.getCustomBackgroundFile(app, false, false).delete()
        compose.onNodeWithText(text(R.string.dialog_close)).performClick()
        compose.waitForIdle()
        row(ROW_IMAGE).assert(hasText("Keins"))
    }

    @Test fun emojiDialogReadsItsConditionsAgainWhileOpen() {
        show()
        row(ROW_EMOJI).performClick()
        compose.waitForIdle()
        assertTrue(exists(text(R.string.prefs_emoji_font_scale)))
        assertFalse(exists(text(R.string.prefs_emoji_key_fit)))
        assertFalse(exists(text(R.string.prefs_emoji_skin_tone)))
        // written while the dialog is open (e.g. by its own scale dialog)
        prefs.edit().putFloat(Settings.PREF_EMOJI_FONT_SCALE, 1.2f).putInt(Settings.PREF_EMOJI_MAX_SDK, 30).commit()
        compose.waitForIdle()
        compose.onNodeWithTag(LOOK_DIALOG_TAG + ROW_EMOJI).assertExists()
        assertTrue(exists(text(R.string.prefs_emoji_key_fit)))
        assertTrue(exists(text(R.string.prefs_emoji_skin_tone)))
        prefs.edit().putFloat(Settings.PREF_EMOJI_FONT_SCALE, 1f).commit()
        compose.waitForIdle()
        assertFalse(exists(text(R.string.prefs_emoji_key_fit)))
    }

    @Test fun rowsFollowChangedPreferencesAndFiles() {
        prefs.edit()
            .putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
            .putFloat(MyDesigns.HEIGHT_KEY, 0.9f)
            .putFloat(MyDesigns.GAP_KEY, 1.5f)
            .putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 1, 3), 1.2f)
            .putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, true)
            .putFloat(Settings.PREF_FONT_SCALE, 1.2f)
            .putString(Settings.PREF_EMOJI_SKIN_TONE, "🏽")
            .putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
            .putBoolean(Settings.PREF_NAVBAR_COLOR, true)
            .commit()
        Settings.getCustomFontFile(app).apply { parentFile?.mkdirs() }.writeText("font")
        Settings.getCustomBackgroundFile(app, true, true).apply { parentFile?.mkdirs() }.writeText("image")
        show()
        mapOf(ROW_HEIGHT to "90 %", ROW_GAP to "1,5", ROW_PADDING to "Angepasst", ROW_SPLIT to "Querformat", ROW_FONT_SIZE to "120 %",
            ROW_CUSTOM_FONT to "Eigene", ROW_EMOJI to "Angepasst", ROW_IMAGE to "Eigenes")
            .forEach { (tag, value) -> row(tag).assert(hasText(value)) }
        assertEquals(ToggleableState.On, toggle(LOOK_ROW_TAG + ROW_DAY_NIGHT))
        assertEquals(ToggleableState.On, toggle(LOOK_ROW_TAG + ROW_NAVBAR))
    }

    @Test fun switchesWriteTheRealPreferences() {
        RgbLighting.write(prefs, RgbSettings(pattern = RgbPattern.TOPO, fps = 60))
        show()
        listOf(LOOK_ROW_TAG + ROW_DAY_NIGHT, LOOK_ROW_TAG + ROW_NAVBAR, LOOK_RGB_SWITCH_TAG).forEach {
            compose.onNodeWithTag(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            compose.onNodeWithTag(it).performScrollTo().performClick()
        }
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false))
        assertTrue(prefs.getBoolean(Settings.PREF_NAVBAR_COLOR, false))
        assertEquals(RgbSettings(enabled = true, pattern = RgbPattern.TOPO, fps = 60), RgbLighting.read(prefs))
        listOf(LOOK_ROW_TAG + ROW_DAY_NIGHT, LOOK_ROW_TAG + ROW_NAVBAR, LOOK_RGB_SWITCH_TAG).forEach { assertEquals(ToggleableState.On, toggle(it), it) }
        // and back: the RGB switch changes nothing but "enabled"
        listOf(LOOK_ROW_TAG + ROW_DAY_NIGHT, LOOK_ROW_TAG + ROW_NAVBAR, LOOK_RGB_SWITCH_TAG).forEach { compose.onNodeWithTag(it).performScrollTo().performClick() }
        compose.waitForIdle()
        assertFalse(prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, true))
        assertFalse(prefs.getBoolean(Settings.PREF_NAVBAR_COLOR, true))
        assertEquals(RgbSettings(pattern = RgbPattern.TOPO, fps = 60), RgbLighting.read(prefs))
        assertEquals(ToggleableState.Off, toggle(LOOK_RGB_SWITCH_TAG))
    }

    @Test fun rgbCardSummarisesPatternRateAndTargets() {
        RgbLighting.write(prefs, RgbSettings(enabled = true, pattern = RgbPattern.TOPO,
            layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND)))
        show()
        compose.onNodeWithTag(LOOK_RGB_SUMMARY_TAG, useUnmergedTree = true).assert(hasText("Höhenlinien · 30 Hz · Tasten & Hintergrund"))
    }

    @Test fun rgbSummaryListsThreeTargetsAndTheRateWhileOff() {
        RgbLighting.write(prefs, RgbSettings(pattern = RgbPattern.CIRCLE, fps = 60,
            layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.LABEL, RgbLayer.KEYBOARD_BACKGROUND)))
        show()
        compose.onNodeWithTag(LOOK_RGB_SUMMARY_TAG, useUnmergedTree = true).assert(hasText("${text(R.string.agent_rgb_pattern_circle)} · 60 Hz · Tasten, Hintergrund & Beschriftung"))
    }

    @Test fun rgbCardAndLastRowOpenTheirPagesAsListedForReachability() {
        show()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentRgb).performScrollTo().performClick()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.Appearance).performScrollTo().performClick()
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentDesign).performScrollTo().performClick()
        assertEquals(listOf(SettingsDestination.AgentRgb, SettingsDestination.Appearance, SettingsDestination.AgentDesign), routes)
        routes.add(0, routes.removeAt(2))
        // P119: exactly what this page opens is listed for the area, in page order
        assertEquals(routes, SettingsAreas.entries(SettingsArea.LOOK).map { it.route })
        compose.onNodeWithTag(BACK_TAG).performScrollTo().performClick()
    }

    @Test fun designTilesUseTheRealDesigns() {
        show()
        // default colours "light" are the chosen built-in tile; only the first two rows show
        assertEquals(true, selected(KeyboardTheme.THEME_LIGHT))
        tile(KeyboardTheme.THEME_OCEAN).assertDoesNotExist()
        compose.onNodeWithTag(LOOK_EDIT_TAG).assertIsNotEnabled()
        tile(KeyboardTheme.THEME_DARK).performClick()
        compose.waitForIdle()
        assertEquals(KeyboardTheme.THEME_DARK, prefs.getString(Settings.PREF_THEME_COLORS, null))
        assertEquals(true, selected(KeyboardTheme.THEME_DARK))
        assertEquals(false, selected(KeyboardTheme.THEME_LIGHT))
        // the flat template applies the flat preset
        tile(DesignTiles.FLAT).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(DesignTiles.FLAT, DesignTiles.selectedId(prefs))
        // "Alle Designs" opens the design page with every tile
        val all = DesignTiles.sections(prefs).sumOf { it.second.size }
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentDesign).assert(hasText("Alle $all Designs")).performClick()
        assertEquals(listOf(SettingsDestination.AgentDesign), routes)
    }

    @Test fun aChosenTileFromTheDesignPageIsShownToo() {
        prefs.edit().putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_OCEAN).commit()
        show()
        // six tiles: the first five and the chosen one
        assertEquals(true, selected(KeyboardTheme.THEME_OCEAN))
        assertEquals(6, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).fetchSemanticsNodes().size)
    }

    @Test fun ownDesignsComeFirstAndOpenTheEditor() {
        MyDesigns.save(app, design("d1", "Nachtblau"))
        show()
        val own = tile(MyDesigns.THEME_PREFIX + "d1")
        own.assert(hasText("Nachtblau"))
        assertTrue(own.fetchSemanticsNode().boundsInRoot.top <= tile(KeyboardTheme.THEME_LIGHT).fetchSemanticsNode().boundsInRoot.top)
        own.performClick()
        compose.waitForIdle()
        assertEquals("d1", MyDesigns.selectedId(prefs))
        assertEquals(true, selected(MyDesigns.THEME_PREFIX + "d1"))
        assertEquals(false, selected(KeyboardTheme.THEME_LIGHT))
        // "Bearbeiten" edits the chosen own design in the existing editor
        compose.onNodeWithTag(LOOK_EDIT_TAG).assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_editor").assertExists()
        assertTrue(exists("Nachtblau"))
    }

    @Test fun newDesignAndLongPressOpenTheEditor() {
        show()
        compose.onNodeWithTag(LOOK_NEW_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_editor").assertExists()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaLook).assertDoesNotExist()
    }

    @Test fun longPressOnAnOwnDesignOpensTheEditor() {
        MyDesigns.save(app, design("d2", "Pastell"))
        show()
        tile(MyDesigns.THEME_PREFIX + "d2").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_editor").assertExists()
        // a long press only edits, it does not select
        assertEquals(null, MyDesigns.selectedId(prefs))
    }

    @Test fun rowsOpenTheExistingDialogs() {
        show()
        // keyboard height: HeliBoard's scale dialog with the dimensions
        row(ROW_HEIGHT).performClick()
        compose.waitForIdle()
        assertTrue(exists(text(R.string.landscape)), "scale dialog")
        assertTrue(exists(text(R.string.button_default)))
        compose.onNodeWithText(text(android.R.string.cancel)).performClick()
        compose.waitForIdle()
        // groups: a dialog with the upstream rows of these settings
        mapOf(
            ROW_PADDING to listOf(R.string.prefs_bottom_padding_scale, R.string.prefs_side_padding_scale),
            ROW_FONT_SIZE to listOf(R.string.prefs_font_scale, R.string.prefs_hint_font_scale),
            ROW_EMOJI to listOf(R.string.custom_emoji_font, R.string.prefs_emoji_font_scale),
            ROW_IMAGE to listOf(R.string.customize_background_image, R.string.customize_background_image_landscape),
        ).forEach { (tag, titles) ->
            row(tag).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(LOOK_DIALOG_TAG + tag).assertExists()
            titles.forEach { assertTrue(exists(text(it)), "$tag: ${text(it)}") }
            compose.onNodeWithText(text(R.string.dialog_close)).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(LOOK_DIALOG_TAG + tag).assertDoesNotExist()
        }
        // an upstream row in the group opens its own dialog
        row(ROW_PADDING).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.prefs_side_padding_scale)).performClick()
        compose.waitForIdle()
        assertTrue(exists(text(R.string.split)), "side padding dialog has the split dimension")
    }

    @Test fun splitDialogSwitchesTheRealPreferences() {
        show()
        row(ROW_SPLIT).performClick()
        compose.waitForIdle()
        assertFalse(exists(text(R.string.split_spacer_scale)))
        compose.onNodeWithText(text(R.string.landscape)).performClick()
        compose.waitForIdle()
        assertTrue(prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, false))
        assertFalse(prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false))
        // with a split mode on, the spacer setting appears as on the old page
        assertTrue(exists(text(R.string.split_spacer_scale)))
    }

    @Test fun customFontRunsTheUpstreamFlow() {
        show()
        // no font yet: the file picker opens at once
        row(ROW_CUSTOM_FONT).performClick()
        compose.waitForIdle()
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
    }

    @Test fun customFontOffersLoadAndDeleteWhenOneIsThere() {
        Settings.getCustomFontFile(app).apply { parentFile?.mkdirs() }.writeText("font")
        show()
        row(ROW_CUSTOM_FONT).assert(hasText("Eigene")).performClick()
        compose.waitForIdle()
        assertTrue(exists(text(R.string.load)))
        compose.onNodeWithText(text(R.string.delete)).performClick()
        compose.waitForIdle()
        assertFalse(Settings.getCustomFontFile(app).exists())
    }

    @Test fun everyClickableIsAtLeast44dp() {
        MyDesigns.save(app, design("d3", "Blau"))
        prefs.edit().putBoolean(Settings.PREF_THEME_KEY_BORDERS, true).commit() // shows "Tastenabstand"
        show()
        val min = 44 * app.resources.displayMetrics.density - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        // back, 6 tiles, "Alle Designs", two buttons, RGB card and switch, 11 rows
        assertTrue(clickables.size >= 1 + 6 + 1 + 2 + 2 + 11, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderLookPage() {
        show()
        val image = capture("settings-area_look")
        val hero = compose.onNodeWithTag(HERO_TAG).fetchSemanticsNode().boundsInRoot
        assertClose(SettingsArea.LOOK.from.toArgb(), image.getPixel(hero.left.toInt() + 24, hero.top.toInt() + 24), 40)
        // the RGB banner starts teal at the left and ends amber at the right (stripes add some white)
        val card = compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.AgentRgb).fetchSemanticsNode().boundsInRoot
        val y = card.top.toInt() + 48 * 2
        assertClose(0xFF00C2A8.toInt(), image.getPixel(card.left.toInt() + 30, y), 70)
        assertClose(0xFFFFB547.toInt(), image.getPixel(card.right.toInt() - 30, y), 70)
        // the chosen tile has the amber border
        val chosen = tile(KeyboardTheme.THEME_LIGHT).fetchSemanticsNode().boundsInRoot
        assertClose(SettingsArea.LOOK.to.toArgb(), image.getPixel(chosen.left.toInt() + 2, chosen.center.y.toInt()), 40)
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
