// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.UserDictionary
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AREA_ROW_TAG
import helium314.keyboard.settings.screens.AREA_TAG
import helium314.keyboard.settings.screens.AgentAreaScreen
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.CHOICE_TAG
import helium314.keyboard.settings.screens.HERO_TAG
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
import helium314.keyboard.settings.screens.TYPING_CUSTOM_TAG
import helium314.keyboard.settings.screens.TYPING_LEVEL_TAG
import helium314.keyboard.settings.screens.TYPING_NAMES_TAG
import helium314.keyboard.settings.screens.TYPING_SWITCH_TAG
import helium314.keyboard.settings.screens.TypingAutocorrect
import helium314.keyboard.settings.screens.TypingAutocorrect.Level
import helium314.keyboard.settings.screens.WordCounts
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

/** Stage 38c: the area page "Tippen & Korrektur" on the real preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsTypingAreaTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private val routes = mutableListOf<String>()

    private val touched = listOf(Settings.PREF_AUTO_CORRECTION, Settings.PREF_AUTO_CORRECT_CONFIDENCE,
        AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS,
        AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, AgentPreferences.AUTOCORRECT_LEARN_TYPOS, Settings.PREF_AUTO_CAP,
        Settings.PREF_AUTOCORRECT_SHORTCUTS, Settings.PREF_SHOW_SUGGESTIONS, Settings.PREF_BIGRAM_PREDICTIONS,
        Settings.PREF_SUGGEST_EMOJIS, Settings.PREF_SUGGEST_PUNCTUATION, Settings.PREF_USE_CONTACTS, Settings.PREF_USE_APPS,
        Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, Settings.PREF_AUTOSPACE_AFTER_SUGGESTION,
        AgentPreferences.LEARNING_PAUSED, Settings.PREF_ALWAYS_INCOGNITO_MODE, Settings.PREF_TOOLBAR_MODE,
        SpaceGesture.DOUBLE_TAP.key, Settings.PREF_KEY_USE_PERSONALIZED_DICTS)

    // Fallstrick P32: the settings singleton outlives the test, so reload the defaults
    @After fun reset() {
        prefs.edit().apply { touched.forEach { remove(it) } }.commit()
        Settings.getInstance().loadSettings(app)
    }

    private fun show() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentAreaScreen(SettingsArea.TYPING, onNavigate = { routes += it }, onClickBack = {}) } }
        compose.waitForIdle()
    }

    private fun card(level: String) = compose.onNodeWithTag(CHOICE_TAG + TYPING_LEVEL_TAG + level)
    private fun switch(key: String) = compose.onNodeWithTag(TYPING_SWITCH_TAG + key)
    private fun state(key: String) = switch(key).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    @Test fun mappingReadsAndWritesTheRealPreferences() {
        val p = prefs
        // HeliBoard's defaults (autocorrect on, known words may change, confidence 0.24) are no level
        assertNull(TypingAutocorrect.current(p))
        TypingAutocorrect.apply(p, Level.OFF)
        assertFalse(p.getBoolean(Settings.PREF_AUTO_CORRECTION, true))
        assertEquals(Level.OFF, TypingAutocorrect.current(p))

        TypingAutocorrect.apply(p, Level.GENTLE)
        assertTrue(p.getBoolean(Settings.PREF_AUTO_CORRECTION, false))
        assertTrue(p.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false))
        assertTrue(p.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, false))
        // 0.24 is already "Mäßig": kept, not rewritten
        assertFalse(p.contains(Settings.PREF_AUTO_CORRECT_CONFIDENCE))
        assertEquals(Level.GENTLE, TypingAutocorrect.current(p))

        TypingAutocorrect.apply(p, Level.BOLD)
        assertTrue(p.getBoolean(Settings.PREF_AUTO_CORRECTION, false))
        assertFalse(p.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, true))
        assertEquals(0.9f, p.getFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0f))
        // the umlaut rule is not part of BOLD
        assertTrue(p.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, false))
        assertEquals(Level.BOLD, TypingAutocorrect.current(p))

        // GENTLE from a strong confidence sets the default again; OFF keeps the rest for later
        TypingAutocorrect.apply(p, Level.GENTLE)
        assertEquals(Defaults.PREF_AUTO_CORRECT_CONFIDENCE, p.getFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0f))
        TypingAutocorrect.apply(p, Level.OFF)
        assertTrue(p.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false))
        assertEquals(Level.OFF, TypingAutocorrect.current(p))

        // band edges: 0.40 is still "Mäßig", 0.80 is not yet "Sehr stark"
        p.edit().putBoolean(Settings.PREF_AUTO_CORRECTION, true).putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0.40f).commit()
        assertEquals(Level.GENTLE, TypingAutocorrect.current(p))
        p.edit().putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0.41f).commit()
        assertNull(TypingAutocorrect.current(p))
        p.edit().putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false).putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0.80f).commit()
        assertNull(TypingAutocorrect.current(p))
        p.edit().putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0.81f).commit()
        assertEquals(Level.BOLD, TypingAutocorrect.current(p))
        // a fine-tuned value inside the band stays when the level is picked again
        TypingAutocorrect.apply(p, Level.BOLD)
        assertEquals(0.81f, p.getFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0f))
        // GENTLE needs the umlaut rule
        TypingAutocorrect.apply(p, Level.GENTLE)
        p.edit().putBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, false).commit()
        assertNull(TypingAutocorrect.current(p))
    }

    @Test fun pickingACardWritesThePreferencesAndThePreferencesSelectTheCard() {
        show()
        val levels = listOf("off", "gentle", "bold")
        levels.forEach { card(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsNotSelected() }
        compose.onNodeWithTag(TYPING_CUSTOM_TAG).assertExists()

        card("gentle").performClick()
        compose.waitForIdle()
        assertEquals(Level.GENTLE, TypingAutocorrect.current(prefs))
        card("gentle").assertIsSelected(); card("off").assertIsNotSelected(); card("bold").assertIsNotSelected()
        compose.onNodeWithTag(TYPING_CUSTOM_TAG).assertDoesNotExist()
        // the rule rows show what the level wrote
        assertEquals(ToggleableState.On, state(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS))

        card("bold").performClick()
        compose.waitForIdle()
        assertEquals(Level.BOLD, TypingAutocorrect.current(prefs))
        assertEquals(0.9f, prefs.getFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0f))
        card("bold").assertIsSelected(); card("gentle").assertIsNotSelected()

        card("off").performClick()
        compose.waitForIdle()
        assertFalse(prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, true))
        card("off").assertIsSelected(); card("bold").assertIsNotSelected()

        // a switch of the rules turns a level into "Eigene Einstellung"
        card("gentle").performClick()
        compose.waitForIdle()
        switch(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(prefs.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true))
        levels.forEach { card(it).assertIsNotSelected() }
        compose.onNodeWithTag(TYPING_CUSTOM_TAG).assertExists()
    }

    @Test fun preferencesWrittenElsewhereSelectTheCard() {
        prefs.edit().putBoolean(Settings.PREF_AUTO_CORRECTION, true).putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false)
            .putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, 0.95f).commit()
        show()
        card("bold").assertIsSelected()
        card("gentle").assertIsNotSelected(); card("off").assertIsNotSelected()
        compose.onNodeWithTag(TYPING_CUSTOM_TAG).assertDoesNotExist()
    }

    @Test fun switchesWriteTheirPreferences() {
        prefs.edit().putBoolean(Settings.PREF_SUGGEST_EMOJIS, false).commit() // the emoji default depends on the dictionary
        show()
        val keys = mapOf(
            AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS to true, AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED to false,
            AgentPreferences.AUTOCORRECT_LEARN_TYPOS to false, Settings.PREF_AUTO_CAP to Defaults.PREF_AUTO_CAP,
            Settings.PREF_AUTOCORRECT_SHORTCUTS to Defaults.PREF_AUTOCORRECT_SHORTCUTS, Settings.PREF_SHOW_SUGGESTIONS to Defaults.PREF_SHOW_SUGGESTIONS,
            Settings.PREF_BIGRAM_PREDICTIONS to Defaults.PREF_BIGRAM_PREDICTIONS, Settings.PREF_SUGGEST_EMOJIS to false,
            Settings.PREF_SUGGEST_PUNCTUATION to Defaults.PREF_SUGGEST_PUNCTUATION,
            Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD to Defaults.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
            Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION to Defaults.PREF_AUTOSPACE_AFTER_PUNCTUATION,
            Settings.PREF_AUTOSPACE_AFTER_SUGGESTION to Defaults.PREF_AUTOSPACE_AFTER_SUGGESTION,
            Settings.PREF_ALWAYS_INCOGNITO_MODE to Defaults.PREF_ALWAYS_INCOGNITO_MODE,
        )
        assertEquals(13, keys.size)
        for ((key, initial) in keys) {
            switch(key).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsEnabled()
            assertEquals(if (initial) ToggleableState.On else ToggleableState.Off, state(key), key)
            switch(key).performScrollTo().performClick()
            compose.waitForIdle()
            // the emoji warning may open without an emoji dictionary; close it
            if (compose.onAllNodesWithText("OK").fetchSemanticsNodes().isNotEmpty()) {
                compose.onAllNodesWithText("OK")[0].performClick(); compose.waitForIdle()
            }
            assertEquals(!initial, prefs.getBoolean(key, initial), key)
            assertEquals(if (initial) ToggleableState.Off else ToggleableState.On, state(key), key)
        }
        // pausing learning goes through LearningData and keeps what was learned
        assertFalse(LearningData.isPaused(app))
        switch(AgentPreferences.LEARNING_PAUSED).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(LearningData.isPaused(app))
        assertEquals(ToggleableState.On, state(AgentPreferences.LEARNING_PAUSED))
        assertFalse(prefs.contains(Settings.PREF_KEY_USE_PERSONALIZED_DICTS))
    }

    @Test fun dependentSwitchesAreDisabledLikeOnTheOldPage() {
        prefs.edit().putBoolean(Settings.PREF_AUTO_CORRECTION, false).putString(Settings.PREF_TOOLBAR_MODE, "TOOLBAR_KEYS")
            .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, true).putString(SpaceGesture.DOUBLE_TAP.key, SpaceAction.EMOJI.name).commit()
        assertTrue(SpaceGestures.doubleTapAssigned(prefs))
        show()
        listOf(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED,
            AgentPreferences.AUTOCORRECT_LEARN_TYPOS, Settings.PREF_AUTOCORRECT_SHORTCUTS,
            // no suggestion strip: no word suggestions, and with autocorrect off no emoji suggestions either
            Settings.PREF_SHOW_SUGGESTIONS, Settings.PREF_SUGGEST_EMOJIS,
            // a space gesture owns the double tap
            Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD).forEach { switch(it).assertIsNotEnabled() }
        compose.onNodeWithTag(TYPING_SWITCH_TAG + Settings.PREF_SHOW_SUGGESTIONS).assert(hasText("Nur, wenn die Leiste Vorschläge zeigt (Leiste & Befehle)"))
        listOf(Settings.PREF_AUTO_CAP, Settings.PREF_BIGRAM_PREDICTIONS, Settings.PREF_SUGGEST_PUNCTUATION,
            Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, Settings.PREF_ALWAYS_INCOGNITO_MODE).forEach { switch(it).assertIsEnabled() }
        // switching autocorrect on with a card enables its rules
        card("gentle").performClick()
        compose.waitForIdle()
        switch(AgentPreferences.AUTOCORRECT_LEARN_TYPOS).assertIsEnabled()
        switch(Settings.PREF_SUGGEST_EMOJIS).assertIsEnabled()
    }

    @Test fun navigationRowsOpenTheExistingPagesWithRealValues() {
        Robolectric.buildContentProvider(FakeUserDictionary::class.java).create(UserDictionary.AUTHORITY)
        prefs.edit().putBoolean(Settings.PREF_USE_APPS, true).commit()
        show()
        compose.waitUntil(2_000) { compose.onAllNodesWithText("12").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.PersonalDictionaries).assert(hasText("12"))
        val counts = WordCounts.read(app)
        assertEquals(12, counts.personalWords)
        compose.onNodeWithTag(AREA_ROW_TAG + SettingsDestination.Dictionaries)
            .assert(hasText(counts.dictionaries.size.toString()))
            .assert(hasText(counts.dictionaries.joinToString(", ").ifEmpty { "Kein Wörterbuch für deine Sprachen" }))
        compose.onNodeWithTag(TYPING_NAMES_TAG).assert(hasText("Apps"))

        // every page the area listed before 38c is still a row here (P119), plus the personal dictionary and the name row
        val expected = SettingsAreas.entries(SettingsArea.TYPING).map { it.route } + SettingsDestination.PersonalDictionaries
        expected.forEach { compose.onNodeWithTag(AREA_ROW_TAG + it).performScrollTo().performClick() }
        compose.onNodeWithTag(TYPING_NAMES_TAG).performScrollTo().performClick()
        assertEquals(expected + SettingsDestination.TextCorrection, routes)
    }

    @Test fun showsTheTextsOfTheMockup() {
        show()
        compose.onNodeWithTag(AREA_TAG + SettingsDestination.AreaTyping).assertExists()
        compose.onNodeWithTag(HERO_TAG).assert(hasText("02")).assert(hasText("Tippen & Korrektur"))
            .assert(hasText("Was die Tastatur verbessert, vorschlägt und lernt."))
        listOf("AUTOKORREKTUR", "AUS", "Nur Vorschläge, nie automatisch", "BEHUTSAM", "Nur Wörter, die es nicht gibt · Umlaute bleiben",
            "MUTIG", "Korrigiert auch knappe Fälle",
            "Regeln", "Umlaut-Wörter behalten", "mochte bleibt mochte", "Rückgängig gemachte Korrekturen merken", "Löschtaste direkt danach",
            "Meine Tippfehler lernen", "Nach dreimaliger Wahl", "Großschreibung am Satzanfang", "Abkürzungen erweitern",
            "Aus dem persönlichen Wörterbuch",
            "Vorschläge", "Wortvorschläge", "Während du tippst", "Nächstes Wort vorschlagen", "Aus dem vorigen Wort", "Emoji-Vorschläge",
            "Tolerant: Wortformen, Tippfehler", "Satzzeichen vorschlagen", "Wenn nichts anderes passt", "Kontakte und App-Namen", "Namen erkennen",
            "Leerzeichen & Satzzeichen", "Doppeltipp = Punkt", "Punkt und Leerzeichen", "Leerzeichen nach Punkt", "Leerzeichen nach Vorschlag",
            "Beim Antippen",
            "Wörter & Lernen", "Wörterbücher", "Persönliches Wörterbuch", "Eigene Wörter", "Gelernte Wörter", "Zählen, exportieren, löschen",
            "Lernen pausieren", "Nichts Neues merken", "Inkognito erzwingen", "Nie lernen", "Alle Korrektur-Einstellungen")
            .forEach { assertTrue(compose.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "missing text: $it") }
    }

    @Test fun everyClickableIsAtLeast44dp() {
        show()
        val min = 44 * app.resources.displayMetrics.density - 1
        val clickables = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(clickables.size >= 1 + 3 + 5 + 5 + 3 + 5 + 1, "clickables: ${clickables.size}")
        clickables.forEach { assertTrue(it.size.height >= min && it.size.width >= min, "too small: ${it.size} ${it.config}") }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderTheTypingPage() {
        prefs.edit().putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, true).commit() // BEHUTSAM, as in the mockup
        show()
        lateinit var image: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        val file = File("build/agent-previews/settings-area_typing.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val hero = compose.onNodeWithTag(HERO_TAG).fetchSemanticsNode().boundsInRoot
        assertClose(0xFF00C2A8.toInt(), image.getPixel(hero.left.toInt() + 24, hero.top.toInt() + 24), 40)
        // the chosen card at full colour, the others at 55 % over the dark card
        val gentle = card("gentle").fetchSemanticsNode().boundsInRoot
        assertClose(0xFF00C2A8.toInt(), image.getPixel(gentle.left.toInt() + 6, gentle.top.toInt() + 40), 40)
        val bold = card("bold").fetchSemanticsNode().boundsInRoot
        val dimmed = image.getPixel(bold.left.toInt() + 6, bold.top.toInt() + 40)
        assertTrue((dimmed shr 16 and 0xFF) < 0xE0, "BOLD is dimmed: #%08X".format(dimmed))
        assertEquals(AgentSettingsTokens.Background.toArgb(), image.getPixel(2, 2))
    }

    private fun assertClose(expected: Int, actual: Int, tolerance: Int) {
        val d = listOf(16, 8, 0).maxOf { shift -> kotlin.math.abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF)) }
        assertTrue(d <= tolerance, "expected ~#%08X, got #%08X".format(expected, actual))
    }
}

/** Twelve words in the personal dictionary. */
class FakeUserDictionary : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
        MatrixCursor(arrayOf(UserDictionary.Words._ID)).apply { repeat(12) { addRow(arrayOf<Any>(it)) } }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
