// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentMemoryScreen
import helium314.keyboard.settings.screens.MEMORY_ADOPT_TAG
import helium314.keyboard.settings.screens.MEMORY_DELETE_TAG
import helium314.keyboard.settings.screens.MEMORY_ENTRY_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 49: candidates and learned later corrections, and the page "Was ich mir gemerkt habe". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h2000dp")
class FieldCorrectionMemoryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private fun settings(vararg flags: Pair<String, Boolean>): SettingsValues {
        val edit = app.prefs().edit().clear()
        flags.forEach { (key, value) -> edit.putBoolean(key, value) }
        edit.commit()
        Settings.getInstance().loadSettings(app, Locale.GERMANY, InputAttributes(EditorInfo(), false, app.packageName))
        return Settings.getValues()
    }
    private val on = AgentPreferences.AUTOCORRECT_LEARN_TYPOS to true
    private fun entries() = FieldCorrectionMemory.all(app).map { Triple(it.typed, it.meant, it.learned) }

    @Before fun clear() { FieldCorrectionMemory.clear(app); TypoMemory.clear(app) }
    @After fun restore() { FieldCorrectionMemory.clear(app); TypoMemory.clear(app); settings() }

    @Test fun secondOccurrenceAtAnotherPlaceLearns() {
        val s = settings(on)
        assertFalse(FieldCorrectionMemory.observed(app, "Hsus", "Haus", 1, 4, s))
        assertFalse(FieldCorrectionMemory.observed(app, "Hsus", "Haus", 1, 4, s), "the same place again")
        assertEquals(listOf(Triple("hsus", "Haus", false)), entries())
        assertNull(TypoMemory.meantWord(app, "hsus", null, false))
        assertTrue(FieldCorrectionMemory.observed(app, "hsus", "Haus", 1, 30, s), "another position in the same field")
        assertEquals(listOf(Triple("hsus", "Haus", true)), entries())
        assertEquals("Haus", TypoMemory.meantWord(app, "HSUS", null, false))
        // another field counts too
        assertFalse(FieldCorrectionMemory.observed(app, "teh", "the", 2, 0, s))
        assertTrue(FieldCorrectionMemory.observed(app, "teh", "the", 3, 0, s))
        // stored: survives a restart of the keyboard
        FieldCorrectionMemory.resetForTest()
        assertEquals(setOf(Triple("hsus", "Haus", true), Triple("teh", "the", true)), entries().toSet())
    }

    @Test fun aDifferentMeaningStartsAgainAsCandidate() {
        val s = settings(on)
        FieldCorrectionMemory.observed(app, "Hsus", "Haus", 1, 4, s)
        assertFalse(FieldCorrectionMemory.observed(app, "Hsus", "Hast", 1, 9, s))
        assertEquals(listOf(Triple("hsus", "Hast", false)), entries())
    }

    @Test fun nothingWithoutPermission() {
        for (s in listOf(settings(), settings(on, AgentPreferences.LEARNING_PAUSED to true),
            settings(on, Settings.PREF_ALWAYS_INCOGNITO_MODE to true))) {
            FieldCorrectionMemory.observed(app, "Hsus", "Haus", 1, 4, s)
            FieldCorrectionMemory.observed(app, "Hsus", "Haus", 2, 4, s)
        }
        assertEquals(emptyList(), entries())
    }

    @Test fun undoingTheAutomaticCorrectionForgetsTheLearnedPair() {
        val s = settings(on)
        FieldCorrectionMemory.observed(app, "Hsus", "Haus", 1, 4, s)
        FieldCorrectionMemory.observed(app, "Hsus", "Haus", 2, 4, s)
        TypoMemory.forget(app, "Hsus", "Haus") // what backspace after the correction does
        assertEquals(emptyList(), entries())
        assertNull(TypoMemory.meantWord(app, "hsus", null, false))
    }

    private fun page() {
        compose.setContent { Theme(true) { AgentMemoryScreen {} } }
        compose.waitForIdle()
    }

    @Test fun pageShowsAdoptsAndDeletes() {
        val s = settings(on)
        FieldCorrectionMemory.observed(app, "Hsus", "Haus", 1, 4, s)
        FieldCorrectionMemory.observed(app, "Hsus", "Haus", 2, 4, s) // learned
        FieldCorrectionMemory.observed(app, "nciht", "nicht", 3, 0, s) // candidate
        page()
        compose.onNodeWithText("Was ich mir gemerkt habe").assertExists()
        compose.onNodeWithText("Korrekturen").performScrollTo()
        compose.onNodeWithTag(MEMORY_ENTRY_TAG + "de_hsus").performScrollTo()
        compose.onNodeWithText("hsus → Haus (Deutsch)").assertExists()
        compose.onNodeWithTag(MEMORY_ENTRY_TAG + "de_nciht").performScrollTo()
        compose.onNodeWithText("nciht → nicht (Deutsch)").assertExists()
        compose.onNodeWithTag(MEMORY_ADOPT_TAG + "de_hsus").assertDoesNotExist() // learned: only delete
        // adopt the candidate
        compose.onNodeWithTag(MEMORY_ADOPT_TAG + "de_nciht").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(setOf(Triple("hsus", "Haus", true), Triple("nciht", "nicht", true)), entries().toSet())
        compose.onNodeWithTag(MEMORY_ADOPT_TAG + "de_nciht").assertDoesNotExist()
        assertEquals("nicht", TypoMemory.meantWord(app, "nciht", null, false))
        // delete both
        compose.onNodeWithTag(MEMORY_DELETE_TAG + "de_hsus").performScrollTo().performClick()
        compose.onNodeWithTag(MEMORY_DELETE_TAG + "de_nciht").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), entries())
        compose.onAllNodesWithText("Noch keine").assertCountEquals(2)
        assertNull(TypoMemory.meantWord(app, "hsus", null, false))
    }

    // review of stage 51 (N10): the page shows the language of each correction; the same typed form in two languages are two rows
    @Test fun pageShowsTheLanguageOfEachCorrection() {
        AgentStorageContexts.keyboard(app).getSharedPreferences(FieldCorrectionMemory.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString("data", """{"candidates":[],"learned":[{"typed":"dnake","lang":"de","meant":"danke","seen":3},""" +
                """{"typed":"dnake","lang":"es","meant":"dante","seen":2},{"typed":"teh","meant":"the","seen":1}]}""").commit()
        FieldCorrectionMemory.resetForTest()
        settings(on)
        page()
        compose.onNodeWithTag(MEMORY_ENTRY_TAG + "de_dnake").performScrollTo()
        compose.onNodeWithText("dnake → danke (Deutsch)").assertExists()
        compose.onNodeWithTag(MEMORY_ENTRY_TAG + "es_dnake").performScrollTo()
        compose.onNodeWithText("dnake → dante (Spanisch)").assertExists()
        compose.onNodeWithTag(MEMORY_ENTRY_TAG + "_teh").performScrollTo()
        compose.onNodeWithText("teh → the").assertExists() // from before stage 51: no language
        compose.onNodeWithTag(MEMORY_DELETE_TAG + "es_dnake").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("de" to "danke", "" to "the"), FieldCorrectionMemory.all(app).map { it.language to it.meant })
    }

    private fun keyboard(element: helium314.keyboard.keyboard.KeyboardElement, locale: Locale, layout: String): helium314.keyboard.keyboard.Keyboard {
        val params = helium314.keyboard.keyboard.KeyboardLayoutSet.Params()
        params.editorInfo = EditorInfo()
        params.subtype = helium314.keyboard.latin.RichInputMethodSubtype.get(
            helium314.keyboard.latin.utils.SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(locale, layout, true))
        params.keyboardWidth = 1080
        params.keyboardHeight = 700
        params.settingsValues = Settings.getValues()
        val builder = helium314.keyboard.keyboard.internal.KeyboardBuilder(
            android.view.ContextThemeWrapper(app, helium314.keyboard.latin.R.style.KeyboardTheme_LXX_Base_Border),
            helium314.keyboard.keyboard.internal.KeyboardParams(helium314.keyboard.keyboard.internal.UniqueKeysCache.NO_CACHE))
        builder.load(helium314.keyboard.keyboard.KeyboardId(element, params))
        return builder.build()
    }

    // review N2: the neighbours come from the last alphabet keyboard, also while symbols are shown
    @Config(shadows = [helium314.keyboard.ShadowProximityInfo::class, helium314.keyboard.ShadowInputMethodManager2::class])
    @Test fun neighboursOfTheLastAlphabetKeyboard() {
        val s = settings(on) // German: without a keyboard QWERTZ
        val azerty = keyboard(helium314.keyboard.keyboard.KeyboardElement.ALPHABET, Locale.FRANCE, "azerty")
        val symbols = keyboard(helium314.keyboard.keyboard.KeyboardElement.SYMBOLS, Locale.FRANCE, "azerty")
        assertFalse(FieldCorrectionWatcher().adjacency(symbols, s).adjacent('a', 'z'), "no alphabet keyboard yet: QWERTZ")
        val watcher = FieldCorrectionWatcher()
        assertTrue(watcher.adjacency(azerty, s).adjacent('a', 'z'))
        assertTrue(watcher.adjacency(symbols, s).adjacent('a', 'z'), "symbols shown: still AZERTY")
        assertTrue(watcher.adjacency(null, s).adjacent('a', 'z'))
        assertFalse(watcher.adjacency(symbols, s).adjacent('a', 'p'))
    }

    // second review: no strong reference to a keyboard (its id holds the editor info with the field's
    // initial text); another layout is picked up, a new field starts without the old one
    @Config(shadows = [helium314.keyboard.ShadowProximityInfo::class, helium314.keyboard.ShadowInputMethodManager2::class])
    @Test fun neighboursFollowALayoutChangeAndHoldNoKeyboard() {
        val s = settings(on)
        val azerty = keyboard(helium314.keyboard.keyboard.KeyboardElement.ALPHABET, Locale.FRANCE, "azerty")
        val qwerty = keyboard(helium314.keyboard.keyboard.KeyboardElement.ALPHABET, Locale.US, "qwerty")
        val symbols = keyboard(helium314.keyboard.keyboard.KeyboardElement.SYMBOLS, Locale.US, "qwerty")
        val watcher = FieldCorrectionWatcher()
        assertTrue(watcher.adjacency(azerty, s).adjacent('z', 'e'))
        assertFalse(watcher.adjacency(qwerty, s).adjacent('z', 'e'), "another layout: QWERTY now")
        assertTrue(watcher.adjacency(qwerty, s).adjacent('z', 'x'))
        assertFalse(watcher.adjacency(symbols, s).adjacent('z', 'e'), "symbols shown: still QWERTY")
        watcher.start()
        assertTrue(watcher.adjacency(null, s).adjacent('z', 'u'), "a new field forgets the layout: German QWERTZ")
        assertFalse(watcher.adjacency(null, s).adjacent('a', 'z'))
        val held = FieldCorrectionWatcher::class.java.declaredFields.filter {
            helium314.keyboard.keyboard.Keyboard::class.java.isAssignableFrom(it.type)
                || EditorInfo::class.java.isAssignableFrom(it.type)
        }
        assertEquals(emptyList(), held.map { it.name }, "no keyboard and no editor info held strongly")
    }

    // second review: only a commit that really changed the word is the keyboard's own correction
    @Test fun aCaseOnlyRevertIsNoAutomaticCorrectionOfTheKeyboard() {
        assertEquals("ich", FieldCorrectionWatcher.keyboardCorrectionFrom(true, "ich", "Ich"))
        assertEquals("Hsus", FieldCorrectionWatcher.keyboardCorrectionFrom(true, "Hsus", "Haus"))
        assertNull(FieldCorrectionWatcher.keyboardCorrectionFrom(true, "ich", "ich"), "Ich changed back to ich")
        assertNull(FieldCorrectionWatcher.keyboardCorrectionFrom(false, "ich", "Ich"))
    }

    // stage 51: learned per keyboard language; pairs from before have none
    @Test fun aLearnedPairAppliesOnlyInItsLanguage() {
        val german = settings(on)
        FieldCorrectionMemory.observed(app, "Hsus", "Haus", 1, 4, german)
        assertTrue(FieldCorrectionMemory.observed(app, "Hsus", "Haus", 2, 4, german))
        assertEquals(listOf("de"), FieldCorrectionMemory.all(app).map { it.language })
        assertEquals("Haus", FieldCorrectionMemory.learnedFor(app, "hsus", "de"))
        assertNull(FieldCorrectionMemory.learnedFor(app, "hsus", "es"))
        FieldCorrectionMemory.resetForTest()
        assertEquals("Haus", FieldCorrectionMemory.learnedFor(app, "hsus", "de"), "the language survives a restart")
        AgentStorageContexts.keyboard(app).getSharedPreferences(FieldCorrectionMemory.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString("data", """{"candidates":[],"learned":[{"typed":"teh","meant":"the","seen":1}]}""").commit()
        FieldCorrectionMemory.resetForTest()
        assertEquals("the", FieldCorrectionMemory.learnedFor(app, "teh", "en") { it == "the" })
        assertNull(FieldCorrectionMemory.learnedFor(app, "teh", "de") { false })
        FieldCorrectionMemory.delete(app, FieldCorrectionMemory.all(app).single())
        assertEquals(emptyList(), FieldCorrectionMemory.all(app))
    }
}
