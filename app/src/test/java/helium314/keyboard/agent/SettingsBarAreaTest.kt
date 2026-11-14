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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import helium314.keyboard.agent.commands.EmojiMixCommand
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.BAR_COMMANDS_SWITCH_TAG
import helium314.keyboard.settings.screens.BAR_COMMAND_TAG
import helium314.keyboard.settings.screens.BAR_CUSTOM_TAG
import helium314.keyboard.settings.screens.BAR_MODE_TAG
import helium314.keyboard.settings.screens.BAR_RETENTION_TAG
import helium314.keyboard.settings.screens.BAR_SKILLS_TAG
import helium314.keyboard.settings.screens.BAR_SKILL_PIN_TAG
import helium314.keyboard.settings.screens.BAR_SWITCH_TAG
import helium314.keyboard.settings.screens.BarModes
import helium314.keyboard.settings.screens.CHOICE_TAG
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
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

/** Stage 38f: the area page "Leiste & Befehle" on the real preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsBarAreaTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private val routes = mutableListOf<String>()

    private val touched = listOf(Settings.PREF_TOOLBAR_MODE, Settings.PREF_AUTO_SHOW_TOOLBAR, Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE,
        Settings.PREF_PINNED_TOOLBAR_KEYS, Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME,
        Settings.PREF_CLIPBOARD_USE_FILES, AgentPreferences.SCREENSHOT_CHIP, EmojiMixCommand.STRIP_PREFERENCE,
        AgentPreferences.COMMANDS, AgentPreferences.COMMANDS_DISABLED, AgentPreferences.COMMAND_PREFIX)

    // Fallstrick P32: the settings singleton outlives the test, so reload the defaults
    @After fun reset() {
        prefs.edit().apply { touched.forEach { remove(it) } }.commit()
        KeyboardCommands.load(app)
        Settings.getInstance().loadSettings(app)
    }

    private fun show() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentAreaScreen(SettingsArea.BAR, onNavigate = { routes += it }, onClickBack = {}) } }
        compose.waitForIdle()
    }

    private fun card(mode: ToolbarMode) = compose.onNodeWithTag(CHOICE_TAG + BAR_MODE_TAG + mode.name)
    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun state(tag: String) = node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
    private fun on(value: Boolean) = if (value) ToggleableState.On else ToggleableState.Off
    private fun click(tag: String) { node(tag).performScrollTo().performClick(); compose.waitForIdle() }

    @Test fun modeMappingReadsAndWritesTheRealPreference() {
        val p = prefs
        // HeliBoard's default is EXPANDABLE = "Tasten + Vorschläge"
        assertEquals(0, BarModes.current(p))
        listOf(ToolbarMode.EXPANDABLE, ToolbarMode.SUGGESTION_STRIP, ToolbarMode.HIDDEN).forEachIndexed { i, mode ->
            BarModes.apply(p, i)
            assertEquals(mode, Settings.readToolbarMode(p))
            assertEquals(i, BarModes.current(p))
        }
        // the fourth upstream mode matches no card
        p.edit().putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.TOOLBAR_KEYS.name).commit()
        assertEquals(-1, BarModes.current(p))
    }

    @Test fun cardsWriteTheModeAndTheModeSelectsTheCard() {
        show()
        val modes = BarModes.cards
        modes.forEach { card(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)) }
        card(ToolbarMode.EXPANDABLE).assertIsSelected()
        node(BAR_CUSTOM_TAG).assertDoesNotExist()
        for (mode in listOf(ToolbarMode.SUGGESTION_STRIP, ToolbarMode.HIDDEN, ToolbarMode.EXPANDABLE)) {
            click(CHOICE_TAG + BAR_MODE_TAG + mode.name)
            assertEquals(mode.name, prefs.getString(Settings.PREF_TOOLBAR_MODE, null))
            modes.forEach { if (it == mode) card(it).assertIsSelected() else card(it).assertIsNotSelected() }
        }
    }

    @Test fun toolbarKeysOnlyIsACustomSetting() {
        prefs.edit().putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.TOOLBAR_KEYS.name).commit()
        show()
        BarModes.cards.forEach { card(it).assertIsNotSelected() }
        node(BAR_CUSTOM_TAG).assert(hasText("Eigene Einstellung: „Nur Leisten-Tasten“. Eine Karte wählen, um es zu ändern."))
        click(CHOICE_TAG + BAR_MODE_TAG + ToolbarMode.HIDDEN.name)
        assertEquals(ToolbarMode.HIDDEN, Settings.readToolbarMode(prefs))
        node(BAR_CUSTOM_TAG).assertDoesNotExist()
    }

    @Test fun commandChipsToggleTheRealPreferences() {
        // a value written elsewhere shows on the chip
        KeyboardCommands.setCommandEnabled(app, "mix", false)
        show()
        val names = KeyboardCommands.registry.map { it.name }
        assertEquals(20, names.size)
        for (name in names) {
            val tag = BAR_COMMAND_TAG + name
            node(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assert(hasText("@$name"))
            assertEquals(on(name != "mix"), state(tag), name)
        }
        // @math off and on again; @contact asks for its permission when switched on (no dialog in Robolectric)
        click(BAR_COMMAND_TAG + "math")
        assertFalse(KeyboardCommands.isCommandEnabled(app, "math"))
        assertEquals(ToggleableState.Off, state(BAR_COMMAND_TAG + "math"))
        assertTrue(KeyboardCommands.isCommandEnabled(app, "url"))
        click(BAR_COMMAND_TAG + "math")
        assertTrue(KeyboardCommands.isCommandEnabled(app, "math"))
        assertEquals(ToggleableState.On, state(BAR_COMMAND_TAG + "math"))

        // the master switch in the header: off disables the chips, the per-command state stays
        node(BAR_COMMANDS_SWITCH_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        assertEquals(ToggleableState.On, state(BAR_COMMANDS_SWITCH_TAG))
        click(BAR_COMMANDS_SWITCH_TAG)
        assertFalse(KeyboardCommands.isEnabled(app))
        assertEquals(ToggleableState.Off, state(BAR_COMMANDS_SWITCH_TAG))
        node(BAR_COMMAND_TAG + "math").assertIsNotEnabled()
        assertTrue(KeyboardCommands.isCommandEnabled(app, "math"))
        click(BAR_COMMANDS_SWITCH_TAG)
        assertTrue(KeyboardCommands.isEnabled(app))
        node(BAR_COMMAND_TAG + "math").assertIsEnabled()
        // the prefix comes from the real preference
        assertTrue(compose.onAllNodesWithText("Präfix @").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun switchesWriteTheirPreferences() {
        show()
        val keys = mapOf(
            Settings.PREF_AUTO_SHOW_TOOLBAR to Defaults.PREF_AUTO_SHOW_TOOLBAR,
            Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE to Defaults.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE,
            Settings.PREF_ENABLE_CLIPBOARD_HISTORY to Defaults.PREF_ENABLE_CLIPBOARD_HISTORY,
            EmojiMixCommand.STRIP_PREFERENCE to true,
            AgentPreferences.SMART_PASTE to true, // stage 40
        )
        for ((key, initial) in keys) {
            val tag = BAR_SWITCH_TAG + key
            node(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsEnabled()
            assertEquals(on(initial), state(tag), key)
            click(tag)
            assertEquals(!initial, prefs.getBoolean(key, initial), key)
            assertEquals(on(!initial), state(tag), key)
        }
        // with the history on now, "Bilder und Dateien" is enabled and writes its key
        assertTrue(prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false))
        val files = BAR_SWITCH_TAG + Settings.PREF_CLIPBOARD_USE_FILES
        assertEquals(on(Defaults.PREF_CLIPBOARD_USE_FILES), state(files))
        click(files)
        assertEquals(!Defaults.PREF_CLIPBOARD_USE_FILES, prefs.getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, true))
        // screenshots: the switch writes the key (the permission request has no dialog in Robolectric)
        val shots = BAR_SWITCH_TAG + AgentPreferences.SCREENSHOT_CHIP
        assertEquals(ToggleableState.Off, state(shots))
        click(shots)
        assertTrue(ScreenshotChip.isEnabled(app))
        node(shots).assert(hasText("Zugriff auf Bilder fehlt: noch keine Pille"))
        click(shots)
        assertFalse(ScreenshotChip.isEnabled(app))
        node(shots).assert(hasText("Neue Screenshots als Pille"))
    }

    @Test fun dependentRowsAreDisabledLikeOnTheOldPages() {
        prefs.edit().putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.HIDDEN.name).putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false).commit()
        show()
        // the old toolbar page shows auto-show only for EXPANDABLE and swipe-to-hide not when hidden
        node(BAR_SWITCH_TAG + Settings.PREF_AUTO_SHOW_TOOLBAR).assertIsNotEnabled().assert(hasText("Nur mit Tasten + Vorschläge"))
        node(BAR_SWITCH_TAG + Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE).assertIsNotEnabled().assert(hasText("Nicht bei versteckter Leiste"))
        // the options page shows retention and files only with the history
        node(BAR_RETENTION_TAG).assertIsNotEnabled()
        node(BAR_SWITCH_TAG + Settings.PREF_CLIPBOARD_USE_FILES).assertIsNotEnabled()
        click(CHOICE_TAG + BAR_MODE_TAG + ToolbarMode.SUGGESTION_STRIP.name)
        node(BAR_SWITCH_TAG + Settings.PREF_AUTO_SHOW_TOOLBAR).assertIsNotEnabled()
        node(BAR_SWITCH_TAG + Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE).assertIsEnabled()
        click(CHOICE_TAG + BAR_MODE_TAG + ToolbarMode.EXPANDABLE.name)
        node(BAR_SWITCH_TAG + Settings.PREF_AUTO_SHOW_TOOLBAR).assertIsEnabled()
        click(BAR_SWITCH_TAG + Settings.PREF_ENABLE_CLIPBOARD_HISTORY)
        node(BAR_RETENTION_TAG).assertIsEnabled()
        node(BAR_SWITCH_TAG + Settings.PREF_CLIPBOARD_USE_FILES).assertIsEnabled()
    }

    @Test fun skillPinsAndRetentionUseTheRealValues() {
        prefs.edit().putInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, 60).putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        addPinnedKey(prefs, ToolbarKey.SKILL_1)
        addPinnedKey(prefs, ToolbarKey.SKILL_3)
        show()
        assertEquals(2, BarModes.pinnedSkills(prefs))
        node(BAR_SKILLS_TAG).assert(hasText("2 von 3"))
        node(BAR_RETENTION_TAG).assert(hasText("60 min"))

        // the dialog pins skill key 2 and unpins 3 on OK
        click(BAR_SKILLS_TAG)
        compose.onNodeWithTag(BAR_SKILL_PIN_TAG + 2).performClick()
        compose.onNodeWithTag(BAR_SKILL_PIN_TAG + 3).performClick()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        val pinned = getPinnedToolbarKeys(prefs)
        assertTrue(ToolbarKey.SKILL_1 in pinned && ToolbarKey.SKILL_2 in pinned && ToolbarKey.SKILL_3 !in pinned, "$pinned")
        node(BAR_SKILLS_TAG).assert(hasText("2 von 3"))

        // the retention dialog is the old slider dialog; "Standard" removes the key
        click(BAR_RETENTION_TAG)
        assertTrue(compose.onAllNodesWithText("Speicherdauer des Zwischenablage-Verlaufs").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Standard").performClick()
        compose.waitForIdle()
        assertFalse(prefs.contains(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME))
        node(BAR_RETENTION_TAG).assert(hasText("${Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME} min"))
    }

    @Test fun everyOldPageOfTheAreaIsStillARow() {
        show()
        // every page the area listed before 38f is a row here (P119), plus the options page for the rest of the clipboard
        val expected = SettingsAreas.entries(SettingsArea.BAR).map { it.route } + SettingsDestination.Preferences
        // stage 45 adds the page "Meine Daten & Vorschläge"
        assertEquals(listOf(SettingsDestination.Toolbar, SettingsDestination.AgentCommands, SettingsDestination.AgentScreenshots,
            SettingsDestination.AgentMyData, SettingsDestination.Preferences), expected)
        expected.forEach { click(AREA_ROW_TAG + it) }
        assertEquals(expected, routes)
    }

    @Test fun showsTheTextsOfTheMockup() {
        show()
        node(AREA_TAG + SettingsDestination.AreaBar).assertExists()
        node(HERO_TAG).assert(hasText("05")).assert(hasText("Leiste & Befehle"))
            .assert(hasText("Die Leiste über den Tasten und was du dort mit @ aufrufst."))
        listOf("LEISTE", "TASTEN + VORSCHLÄGE", "Ausklappbar, angeheftete Knöpfe bleiben", "NUR VORSCHLÄGE", "Knöpfe nur beim Antippen",
            "VERSTECKT", "Mehr Platz für Tasten", "@-Befehle", "Präfix @",
            "Antippen schaltet einzeln an/aus. Textbausteine, Orte und Bildschirm-Lesen stehen beim jeweiligen Befehl.",
            "Leiste", "Knöpfe wählen und sortieren", "Werkzeuge, Anheften, Zwischenablage", "Automatisch zeigen", "Bei Eingabe oder Auswahl",
            "Wischen blendet aus", "Auf der Leiste nach unten", "Skill-Tasten anheften", "Die drei eigenen Aktionen",
            "Zwischenablage & Bilder", "Zwischenablage-Verlauf", "Kopiertes wiederfinden", "Aufbewahrung",
            "Bilder und Dateien", "Screenshots anbieten", "Neue Screenshots als Pille",
            "Emoji-Mix in der Leiste", "Nach zwei getippten Emojis", "Alle Befehls-Einstellungen")
            .forEach { assertTrue(compose.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "missing text: $it") }
        // all twenty commands of the mockup, in its order
        assertEquals(listOf("math", "contact", "url", "such", "zeichen", "emoji", "conv", "snip", "clip", "wf", "ki", "datum", "termin",
            "ort", "qr", "translate", "wecker", "timer", "lang", "mix"), KeyboardCommands.registry.map { it.name })
    }

    @Test fun everyClickableIsAtLeast44dp() {
        show()
        val min = 44 * app.resources.displayMetrics.density - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(clickables.size >= 1 + 3 + 1 + 20 + 4 + 5 + 3, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderTheBarPage() {
        // as in the mockup: history, files, screenshots and emoji mix on
        prefs.edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).putBoolean(AgentPreferences.SCREENSHOT_CHIP, true)
            .putInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, 60).putBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, true).commit()
        show()
        lateinit var image: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        val file = File("build/agent-previews/settings-area_bar.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val hero = node(HERO_TAG).fetchSemanticsNode().boundsInRoot
        assertClose(0xFFFFC94D.toInt(), image.getPixel(hero.left.toInt() + 24, hero.top.toInt() + 24), 40)
        // the chosen card at full colour, the others with the gradient dimmed over the dark card (choiceDim)
        val chosen = card(ToolbarMode.EXPANDABLE).fetchSemanticsNode().boundsInRoot
        assertClose(0xFFFFC94D.toInt(), image.getPixel(chosen.left.toInt() + 6, chosen.top.toInt() + 40), 40)
        val other = card(ToolbarMode.SUGGESTION_STRIP).fetchSemanticsNode().boundsInRoot
        val dimmed = image.getPixel(other.left.toInt() + 6, other.top.toInt() + 40)
        assertTrue((dimmed shr 16 and 0xFF) < 0xE0, "NUR VORSCHLÄGE is dimmed: #%08X".format(dimmed))
        // an enabled command chip starts with the area gradient
        val chip = node(BAR_COMMAND_TAG + "math").fetchSemanticsNode().boundsInRoot
        assertClose(0xFFFFC94D.toInt(), image.getPixel(chip.left.toInt() + 8, chip.top.toInt() + chip.height.toInt() / 2), 48)
        assertEquals(AgentSettingsTokens.Background.toArgb(), image.getPixel(2, 2))
    }

    private fun assertClose(expected: Int, actual: Int, tolerance: Int) {
        val d = listOf(16, 8, 0).maxOf { shift -> kotlin.math.abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF)) }
        assertTrue(d <= tolerance, "expected ~#%08X, got #%08X".format(expected, actual))
    }
}
