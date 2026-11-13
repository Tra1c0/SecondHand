// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LearningDataTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private class FakeSource(private val data: MutableMap<String, List<LearnedWord>>) : LearningSource {
        var deleted = 0
        override fun languages() = data.keys.sorted()
        override fun words(language: String) = data.getValue(language)
        override fun deleteAll() { deleted++; data.clear() }
    }

    @Before fun reset() {
        app.prefs().edit().clear().commit()
        // App.onCreate writes emoji_max_sdk from a background coroutine at an unknown moment; writing
        // it here first makes the key set below independent of test order (the background then skips)
        helium314.keyboard.keyboard.emoji.SupportedEmojis.load(app)
    }

    @Test fun pauseIsOffByDefaultPersistsAndReachesTheEngineSettings() {
        assertFalse(LearningData.isPaused(app))
        Settings.getInstance().loadSettings(app)
        assertFalse(Settings.getValues().mLearningPaused)
        assertFalse(LearningData.blocksLearning(Settings.getValues().mLearningPaused))
        val before = app.prefs().all.keys.toSet()
        LearningData.setPaused(app, true)
        assertTrue(LearningData.isPaused(app))
        Settings.getInstance().loadSettings(app)
        assertTrue(LearningData.blocksLearning(Settings.getValues().mLearningPaused))
        // Pausing is not the same switch as learning itself: that one deletes when turned off.
        assertEquals(before + AgentPreferences.LEARNING_PAUSED, app.prefs().all.keys)
        assertEquals(setOf(Settings.PREF_EMOJI_MAX_SDK), before) // nothing else was written before pausing
        LearningData.setPaused(app, false)
        Settings.getInstance().loadSettings(app)
        assertFalse(Settings.getValues().mLearningPaused)
    }

    @Test fun exportIsCompleteReadableAndContainsOnlyWordsAndLikelihood() {
        val source = FakeSource(mutableMapOf(
            "de" to listOf(LearnedWord("Grüße", 90), LearnedWord("Äpfel", 120), LearnedWord("abc", 120)),
            "en-US" to emptyList()))
        assertEquals(mapOf("de" to 3, "en-US" to 0), LearningData.counts(source))
        val root = JSONObject(LearningData.export(source))
        assertEquals(setOf("format", "version", "languages"), root.keys().asSequence().toSet())
        assertEquals("agent-keyboard-learning", root.getString("format"))
        assertEquals(1, root.getInt("version"))
        val german = root.getJSONArray("languages").getJSONObject(0)
        assertEquals("de", german.getString("language"))
        val words = german.getJSONArray("words")
        // Most likely first, then alphabetical: stable output for identical data.
        assertEquals(listOf("abc", "Äpfel", "Grüße"), (0 until words.length()).map { words.getJSONObject(it).getString("word") })
        assertEquals(setOf("word", "probability"), words.getJSONObject(0).keys().asSequence().toSet())
        assertEquals(0, root.getJSONArray("languages").getJSONObject(1).getJSONArray("words").length())
        assertEquals(LearningData.export(source), LearningData.export(source))
        assertFalse(LearnedWord("private", 1).toString().contains("private"))
    }

    @Test fun unreadableLanguageFailsTheWholeCountAndExport() {
        // The real source throws when a dictionary cannot be read in time; "0 words" would be a lie.
        val source = object : LearningSource {
            override fun languages() = listOf("de", "en-US")
            override fun words(language: String) = if (language == "de") listOf(LearnedWord("Hallo", 1)) else error("UNREADABLE")
            override fun deleteAll() = error("not used")
        }
        assertFails { LearningData.counts(source) }
        assertFails { LearningData.export(source) }
    }

    @Test fun oversizedExportIsRefusedInsteadOfCut() {
        val many = (0 until 40_000).map { LearnedWord("wort$it".padEnd(24, 'x'), 1) }
        assertFails { LearningData.export(FakeSource(mutableMapOf("de" to many))) }
    }

    @Test fun realSourceListsOnlyTypingHistoryFilesOfTheEngineStorage() {
        val files = AgentStorageContexts.keyboard(app).filesDir
        files.mkdirs()
        val names = listOf("UserHistoryDictionary.de.dict", "UserHistoryDictionary.en-US.dict",
            "UserHistoryDictionary..dict", "UserHistoryDictionary.de.tmp", "OtherDictionary.fr.dict",
            "UserHistoryDictionary.x y.dict")
        names.forEach { File(files, it).mkdirs() }
        try {
            val source = UserHistoryLearningSource(app)
            assertEquals(listOf("de", "en-US"), source.languages())
            assertFails { source.words("fr") } // never opens a dictionary that is not a typing history
        } finally { names.forEach { File(files, it).deleteRecursively() } }
    }
}
