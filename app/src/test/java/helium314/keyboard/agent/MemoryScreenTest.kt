// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.agentkeyboard.core.memory.PhraseBook
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AgentMemoryScreen
import helium314.keyboard.settings.screens.MEMORY_APP_TAG
import helium314.keyboard.settings.screens.MEMORY_FOREIGN_DELETE_TAG
import helium314.keyboard.settings.screens.MEMORY_PHRASE_DELETE_TAG
import helium314.keyboard.settings.screens.MEMORY_OWN_TAG
import helium314.keyboard.settings.screens.MEMORY_PHRASE_TAG
import helium314.keyboard.settings.screens.MEMORY_PIN_TAG
import helium314.keyboard.settings.screens.MEMORY_SWITCH_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 46: the page "Gedächtnis" (phrases pin and delete, own words, excluded apps, switch). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class MemoryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val storage get() = AgentStorageContexts.keyboard(app).getSharedPreferences(Memory.FILE, Context.MODE_PRIVATE)
    private val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)

    @Before fun setUp() {
        app.prefs().edit().clear().commit()
        storage.edit().clear().commit()
        // two phrases as the keyboard stores them: readable text with weight and day
        val book = PhraseBook()
        repeat(12) { for (k in 2..5) book.learn("ich melde mich sobald fertig".split(' ').take(k), today) }
        repeat(4) { for (k in 2..3) book.learn("bis gleich dann".split(' ').take(k), today) }
        storage.edit().putString(Memory.PHRASES, book.encode()).putString(Memory.OWN_WORDS, "v1\no;krass\no;nö").commit()
        Memory.resetForTest()
    }

    @After fun tearDown() {
        storage.edit().clear().commit()
        app.prefs().edit().clear().commit()
        Memory.resetForTest()
    }

    private fun show() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentMemoryScreen(onClickBack = {}) } }
        compose.waitForIdle()
    }

    @Test fun switchIsOffAtFirstAndWritesThePreference() {
        show()
        compose.onNodeWithTag(MEMORY_SWITCH_TAG).assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(MEMORY_SWITCH_TAG).assertIsOn()
        assertTrue(app.prefs().getBoolean(AgentPreferences.MEMORY, false))
    }

    @Test fun phrasesArePinnedAndDeleted() {
        show()
        val strongest = Memory.phrases(app).first()
        // the strongest phrase is the first row
        compose.onNodeWithTag(MEMORY_PHRASE_TAG + 0).performScrollTo().assert(hasAnyDescendant(hasText(strongest.text, substring = true)))
        compose.onNodeWithTag(MEMORY_PIN_TAG + 0).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(Memory.phrases(app).first { it.key == strongest.key }.pinned)
        // written at once, as readable text
        Memory.resetForTest()
        assertTrue(Memory.phrases(app).first { it.key == strongest.key }.pinned)
        // the page shows it as pinned; unpinning works the same way
        compose.onNodeWithTag(MEMORY_PIN_TAG + 0).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(Memory.phrases(app).first { it.key == strongest.key }.pinned)

        val before = Memory.phrases(app, 2).size
        val first = Memory.phrases(app).first()
        compose.onNodeWithTag(MEMORY_PHRASE_DELETE_TAG + 0).performScrollTo().performClick()
        compose.waitForIdle()
        Memory.resetForTest()
        val after = Memory.phrases(app, 2)
        assertTrue(after.none { " ${it.key} ".contains(" ${first.key} ") }, "the phrase and every longer one with it are gone")
        assertTrue(after.size < before)
        assertFalse(storage.getString(Memory.PHRASES, "")!!.contains(first.text))
    }

    @Test fun ownWordsAndExcludedAppsAreRemoved() {
        assertTrue(Memory.setExcluded(app, "com.example.chat", true))
        show()
        compose.onNodeWithText("krass").performScrollTo()
        // newest first: "nö", then "krass"
        compose.onNodeWithTag(MEMORY_OWN_TAG + 1).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("nö"), Memory.ownWords(app))
        compose.onNodeWithText("com.example.chat").performScrollTo()
        compose.onNodeWithTag(MEMORY_APP_TAG + 0).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(Memory.isExcluded(app, "com.example.chat"))
    }

    // stage 51: the words of other languages, with the language they come from and the one they are kept in
    @Test fun wordsOfOtherLanguagesAreShownAndDeleted() {
        AgentStorageContexts.keyboard(app).getSharedPreferences(ForeignWords.FILE, Context.MODE_PRIVATE).edit()
            .putString("data", """{"candidates":[],"remembered":[{"lang":"de","word":"hola","from":"es","seen":1}]}""").commit()
        ForeignWords.resetForTest()
        try {
            show()
            compose.onNodeWithText("hola (Spanisch), in Deutsch").performScrollTo()
            compose.onNodeWithTag(MEMORY_FOREIGN_DELETE_TAG + "de_hola").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(emptyList(), ForeignWords.all(app))
            ForeignWords.resetForTest()
            assertEquals(emptyList(), ForeignWords.all(app), "deleted in storage too")
        } finally {
            ForeignWords.clear(app)
        }
    }
}
