// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Stage 20b: learning own typos from words picked in the strip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TypoMemoryTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private fun settings(vararg flags: Pair<String, Boolean>): SettingsValues {
        val edit = app.prefs().edit().clear()
        flags.forEach { (key, value) -> edit.putBoolean(key, value) }
        edit.commit()
        Settings.getInstance().loadSettings(app, Locale.GERMANY, InputAttributes(EditorInfo(), false, app.packageName))
        return Settings.getValues()
    }
    private val on = AgentPreferences.AUTOCORRECT_LEARN_TYPOS to true

    @Before fun clear() = TypoMemory.clear(app)
    @After fun restore() { TypoMemory.clear(app); settings() }

    private fun confirm(typed: String, meant: String, previous: String? = null, s: SettingsValues = settings(on)) =
        TypoMemory.confirm(app, typed, meant, previous, s)

    @Test fun learnsOnlyWhenAllowed() {
        val off = settings()
        repeat(3) { confirm("nciht", "nicht", s = off) }
        repeat(3) { confirm("nciht", "nicht", s = settings(on, AgentPreferences.LEARNING_PAUSED to true)) }
        repeat(3) { confirm("nciht", "nicht", s = settings(on, Settings.PREF_ALWAYS_INCOGNITO_MODE to true)) }
        assertEquals(0, TypoMemory.all(app).size)
    }

    @Test fun threeEqualConfirmationsAreNeeded() {
        val s = settings(on)
        confirm("nciht", "nicht", s = s); confirm("nciht", "nicht", s = s)
        assertNull(TypoMemory.meantWord(app, "nciht", null, false))
        confirm("Nciht", "Nicht", s = s) // case does not matter
        assertEquals("nicht", TypoMemory.meantWord(app, "NCIHT", null, false))
    }

    @Test fun competingMeaningsBlockEachOtherUntilOneDominates() {
        val s = settings(on)
        repeat(3) { confirm("teh", "the", s = s) }
        repeat(2) { confirm("teh", "tech", s = s) } // 3 of 5 = 60 %
        assertNull(TypoMemory.meantWord(app, "teh", null, false))
        repeat(9) { confirm("teh", "the", s = s) } // 12 of 14 > 80 %
        assertEquals("the", TypoMemory.meantWord(app, "teh", null, false))
    }

    @Test fun aRealWordIsOnlyCorrectedInItsLearnedContext() {
        val s = settings(on)
        repeat(3) { confirm("mochte", "möchte", "ich", s) }
        assertEquals("möchte", TypoMemory.meantWord(app, "mochte", "Ich", true))
        assertNull(TypoMemory.meantWord(app, "mochte", "er", true)) // other context
        assertNull(TypoMemory.meantWord(app, "mochte", null, true)) // no context
        assertEquals("möchte", TypoMemory.meantWord(app, "mochte", null, false)) // not a word: total counts
    }

    @Test fun completionsDigitsAndSameWordsAreNotTypos() {
        val s = settings(on)
        repeat(3) { confirm("exam", "example", s = s) }
        repeat(3) { confirm("ab1", "abc", s = s) }
        repeat(3) { confirm("Hallo", "hallo", s = s) }
        repeat(3) { confirm("a", "an", s = s) }
        assertEquals(0, TypoMemory.all(app).size)
    }

    @Test fun capitalsOfNounsAreKeptCapitalsOfSentenceStartsAreNot() {
        val s = settings(on)
        repeat(3) { confirm("hasu", "Haus", s = s) } // typed small, picked capital: a noun
        assertEquals("Haus", TypoMemory.meantWord(app, "hasu", null, false))
        repeat(3) { confirm("Nciht", "Nicht", s = s) } // capital from the sentence start
        assertEquals("nicht", TypoMemory.meantWord(app, "nciht", null, false))
    }

    @Test fun completionsWithUmlautAndManyMeaningsAreHandled() {
        val s = settings(on)
        repeat(3) { confirm("Mad", "Mädchen", s = s) }
        repeat(3) { confirm("stras", "Straße", s = s) }
        assertEquals(0, TypoMemory.all(app).size)
        listOf("aaa", "bbb", "ccc", "ddd", "eee", "fff").forEachIndexed { i, w -> repeat(i + 1) { confirm("xyq", w, s = s) } }
        assertEquals(5, TypoMemory.all(app).size) // at most five meanings per typed form, the rarest goes
        assertEquals(setOf("bbb", "ccc", "ddd", "eee", "fff"), TypoMemory.all(app).map { it.meant }.toSet())
    }

    @Test fun anUndoneCorrectionDropsThePair() {
        val s = settings(on)
        repeat(3) { confirm("nciht", "nicht", s = s) }
        TypoMemory.forget(app, "Nciht", "Nicht")
        assertNull(TypoMemory.meantWord(app, "nciht", null, false))
        assertEquals(0, TypoMemory.all(app).size)
        TypoMemory.forget(app, "unknown", "word") // nothing to forget is fine
    }

    @Test fun survivesARestartAndIsBounded() {
        val s = settings(on)
        repeat(3) { confirm("nciht", "nicht", "das", s) }
        // letters only: typed forms with digits are never learned
        fun word(n: Int) = (0 until 3).joinToString("") { ((n / Math.pow(26.0, it.toDouble()).toInt()) % 26 + 97).toChar().toString() }
        repeat(TypoMemory.MAX_TYPED_FORMS) { confirm("q" + word(it), "x" + word(it), s = s) }
        TypoMemory.resetForTest()
        assertEquals(TypoMemory.MAX_TYPED_FORMS, TypoMemory.all(app).size)
        assertNull(TypoMemory.meantWord(app, "nciht", "das", false)) // the oldest was dropped
        repeat(3) { confirm("nciht", "nicht", "das", s) }
        TypoMemory.resetForTest()
        assertEquals("nicht", TypoMemory.meantWord(app, "nciht", "das", false))
        assertEquals(TypoMemory.Entry("nciht", "nicht", 3, "de"), TypoMemory.all(app).first()) // most recent first
    }

    // stage 51: a pair belongs to the keyboard language it was learned in
    @Test fun aPairAppliesOnlyInItsLanguage() {
        val german = settings(on)
        repeat(3) { confirm("dnake", "danke", s = german) }
        assertEquals("danke", TypoMemory.meantWord(app, "dnake", null, false, "de"))
        assertNull(TypoMemory.meantWord(app, "dnake", null, false, "es"))
        Settings.getInstance().loadSettings(app, Locale.forLanguageTag("es"), InputAttributes(EditorInfo(), false, app.packageName))
        repeat(3) { confirm("dnake", "dante", s = Settings.getValues()) } // the same letters mean another word in Spanish
        assertEquals("dante", TypoMemory.meantWord(app, "dnake", null, false, "es"))
        assertEquals("danke", TypoMemory.meantWord(app, "dnake", null, false, "de"))
        // review N10: the list keeps the language
        assertEquals(listOf(TypoMemory.Entry("dnake", "dante", 3, "es"), TypoMemory.Entry("dnake", "danke", 3, "de")), TypoMemory.all(app))
        TypoMemory.resetForTest()
        assertEquals("danke", TypoMemory.meantWord(app, "dnake", null, false, "de"), "the language survives a restart")
        // an undo drops the pair in every language
        TypoMemory.forget(app, "dnake", "danke")
        assertNull(TypoMemory.meantWord(app, "dnake", null, false, "de"))
        assertEquals("dante", TypoMemory.meantWord(app, "dnake", null, false, "es"))
    }

    @Test fun aPairFromBeforeStage51AppliesWhereTheLanguageKnowsItsWord() {
        AgentStorageContexts.keyboard(app).getSharedPreferences(TypoMemory.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString("pairs", """[{"typed":"nciht","meant":[{"word":"nicht","form":"nicht","total":3,"contexts":{}}]}]""").commit()
        TypoMemory.resetForTest()
        assertEquals("nicht", TypoMemory.meantWord(app, "nciht", null, false, "de") { it == "nicht" })
        assertNull(TypoMemory.meantWord(app, "nciht", null, false, "es") { false })
        assertEquals(listOf(TypoMemory.Entry("nciht", "nicht", 3, "")), TypoMemory.all(app)) // from before stage 51: no language
    }

    // review of stage 51 (M2): another word picked for a word of another language kept here forgets it, only in this language
    @Test fun pickingAnotherWordForgetsAWordOfAnotherLanguageKeptHere() {
        fun inTextField(vararg flags: Pair<String, Boolean>): SettingsValues {
            settings(*flags)
            Settings.getInstance().loadSettings(app, Locale.GERMANY, InputAttributes(
                EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT }, false, app.packageName))
            return Settings.getValues()
        }
        AgentStorageContexts.keyboard(app).getSharedPreferences(ForeignWords.FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString("data", """{"candidates":[],"remembered":[{"lang":"de","word":"hola","from":"es","seen":1},""" +
                """{"lang":"en","word":"hola","from":"es","seen":1}]}""").commit()
        ForeignWords.resetForTest()
        fun kept() = ForeignWords.all(app).map { it.language }.sorted()
        try {
            confirm("hola", "Hola", s = inTextField()) // a change of case
            confirm("hola", "holanda", s = inTextField()) // a word finished
            confirm("hola", "hold", s = inTextField(AgentPreferences.LEARNING_PAUSED to true)) // paused: nothing changes
            assertEquals(listOf("de", "en"), kept())
            confirm("hola", "hold", s = inTextField()) // also without "Meine Tippfehler lernen"
            assertEquals(listOf("en"), kept())
            assertEquals(0, TypoMemory.all(app).size)
            ForeignWords.resetForTest()
            assertEquals(listOf("en"), kept(), "forgotten in storage too")
        } finally {
            ForeignWords.clear(app)
        }
    }
}
