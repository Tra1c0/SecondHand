// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.core.content.edit
import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Investigation "Untersuchung Autokorrektur" (owner report 2026-11-21, four findings). These tests record what
 * the fork does TODAY; they do not say it is right. The native dictionary cannot run here, so the engine's results
 * are fixed by each test (what the real main dictionaries contain was read from the shipped `.dict` files, see the
 * comments); what is tested is the code between the engine and the field (`Suggest`, `CorrectionGuard`).
 * Nothing in this file changes product code or another test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowBinaryDictionaryUtils::class,
    ShadowReportFacilitator::class,
])
class AutocorrectReportTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val suggest get() = latinIME.mInputLogic.mSuggest

    @BeforeTest fun reset() {
        latinIME.prefs().edit { clear() }
        reportLocale = Locale.GERMAN
        reportResults = SuggestionResults(18, false, false)
        reportKnowing = emptyMap()
    }

    private fun main(word: String, score: Int, locale: Locale) = SuggestedWordInfo(word, "", score,
        SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, ReportDict(Dictionary.TYPE_MAIN, locale), 0, 0)

    private fun history(word: String, score: Int, locale: Locale) = SuggestedWordInfo(word, "", score,
        SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, ReportDict(Dictionary.TYPE_USER_HISTORY, locale), 0, 0)

    private fun whitelist(word: String, locale: Locale) = SuggestedWordInfo(word, "", Int.MAX_VALUE,
        SuggestedWordInfo.KIND_WHITELIST, ReportDict(Dictionary.TYPE_MAIN, locale), 0, 0)

    private fun results(vararg infos: SuggestedWordInfo) { reportResults = SuggestionResults(18, false, false).apply { addAll(infos.toList()) } }

    private fun settings(keepValid: Boolean = false, threshold: Float = 0.24f) {
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_AUTO_CORRECTION, true)
            putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, threshold)
            putBoolean(Settings.PREF_MORE_AUTO_CORRECTION, true)
            putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, keepValid)
        }
        suggest.setAutoCorrectionThreshold(Settings.getValues().mAutoCorrectionThreshold)
    }

    private fun type(typed: String, previous: List<String> = emptyList()): SuggestedWords {
        val composer = WordComposer()
        StringUtils.toCodePointArray(typed).forEach {
            composer.applyProcessedEvent(composer.processEvent(
                Event.createEventForCodePointFromAlreadyTypedText(it, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)))
        }
        composer.adviseCapitalizedModeBeforeFetchingSuggestions(CapsMode.OFF)
        val params = KeyboardParams().apply {
            GRID_HEIGHT = 1; GRID_WIDTH = 1
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET)
        }
        val ngram = if (previous.isEmpty()) NgramContext.EMPTY_PREV_WORDS_INFO
            else NgramContext(*previous.reversed().map { NgramContext.WordInfo(it) }.toTypedArray())
        suggest.clearNextWordSuggestionsCache()
        return suggest.getSuggestedWords(composer, ngram, Keyboard(params), Settings.getValues().mSettingsValuesForSuggestion,
            Settings.getValues().mAutoCorrectEnabled, 0, 0)
    }

    private fun SuggestedWords.correction(): String? =
        if (mWillAutoCorrect) getWord(SuggestedWords.INDEX_OF_AUTO_CORRECTION) else null

    // ---- finding 4: "nichtmal" -> "nicbmal" -------------------------------------------------------------------
    // Read from the shipped dictionaries (main_de, main_en-US/GB, es, fr, it, nl, pl, sv, tr): "nichtmal", "Nichtmal",
    // "nicbmal", "war'a" are in NONE of them; "war's" is only in the English one (frequency 95). So a non-word like
    // "nicbmal" can only come from a dictionary that the owner's typing made: typing history, personal dictionary.

    @Test fun `reported finding 4 - a typed non-word becomes a non-word from the typing history`() {
        reportLocale = Locale.GERMAN
        settings()
        // the engine knows no "nichtmal"; its best hit is a history entry (the owner once committed "nicbmal")
        results(history("nicbmal", 600000, Locale.GERMAN), main("nicht", 300000, Locale.GERMAN))
        val words = type("nichtmal", listOf("und"))
        assertEquals("nicbmal", words.correction(), "no rule asks whether the correction is a dictionary word")
        // the same with the cautious level: it protects typed words, not the target of a correction
        settings(keepValid = true)
        assertEquals("nicbmal", type("nichtmal", listOf("und")).correction())
        // and for the apostrophe case of finding 2: "war's" becomes the learned typo "war'a"
        results(history("war'a", 600000, Locale.GERMAN), main("war", 300000, Locale.GERMAN))
        assertEquals("war'a", type("war's", listOf("und")).correction())
    }

    @Test fun `reported finding 4 - a correct typed word that a main dictionary knows is not replaced by a history non-word`() {
        // the counter-case: with the typed word in the main dictionary and "Richtige Wörter nie ändern", nothing happens
        reportLocale = Locale.GERMAN
        settings(keepValid = true)
        results(main("nichts", 1500000, Locale.GERMAN), history("nichs", 600000, Locale.GERMAN))
        assertEquals(null, type("nichts").correction())
    }

    // ---- finding 3: "i" is never capitalised --------------------------------------------------------------------
    // Read from main_en-US/GB: "i" is a word (frequency 0) with the whitelist shortcut "I" (probability 15), likewise
    // "im" -> "I'm", "id" -> "I'd", "cant" -> "can't". main_de has no whitelist entry at all.

    @Test fun `reported finding 3 - i becomes I on the default settings`() {
        reportLocale = Locale.ENGLISH
        settings(keepValid = false)
        results(whitelist("I", Locale.ENGLISH), main("i", 300000, Locale.ENGLISH))
        assertEquals("I", type("i", listOf("and")).correction())
    }

    @Test fun `reported finding 3 - i stays lower case with 'Richtige Woerter nie aendern' (level Behutsam)`() {
        reportLocale = Locale.ENGLISH
        settings(keepValid = true)
        // the dictionary has "i" as a word, so CorrectionGuard.allowsAutoCorrection sees a known typed word and keepValidWords wins
        // over the whitelist entry (the stage 48 exception for "i" -> "I" sits in another check)
        results(whitelist("I", Locale.ENGLISH), main("i", 300000, Locale.ENGLISH))
        val words = type("i", listOf("and"))
        assertEquals(null, words.correction(), "today: no automatic capital")
        assertTrue(words.mSuggestedWordInfoList.any { it.mWord == "I" }, "the capital is still offered in the strip")
    }

    @Test fun `reported finding 3 - the same for the other whitelist forms im, cant, id`() {
        reportLocale = Locale.ENGLISH
        settings(keepValid = true)
        results(whitelist("can't", Locale.ENGLISH), main("cant", 300000, Locale.ENGLISH))
        assertEquals(null, type("cant", listOf("i")).correction())
    }

    // ---- finding 1: German correction in an English sentence ------------------------------------------------------
    // Stage 60 only protects a typed word that a main dictionary of ANOTHER language knows and the correction's does not.

    @Test fun `reported finding 1 - an English typo in an English sentence is still corrected to a German word`() {
        reportLocale = Locale.GERMAN
        settings()
        reportKnowing = mapOf("the" to listOf(Locale.ENGLISH), "cat" to listOf(Locale.ENGLISH), "is" to listOf(Locale.ENGLISH, Locale.GERMAN))
        // "recieve" is unknown to every dictionary; the engine ranks a German word first (the score is high enough for
        // the engine's own threshold: at 650000 nothing is corrected on the default strength, which is not the point here)
        results(main("Rezept", 1500000, Locale.GERMAN), main("receive", 600000, Locale.ENGLISH))
        assertEquals("Rezept", type("recieve", listOf("the", "cat", "is")).correction(),
            "the sentence language is not asked for a typed word that no dictionary knows")
    }

    @Test fun `reported finding 1 - stage 60 does hold a word of the other language back`() {
        reportLocale = Locale.GERMAN
        settings()
        reportKnowing = mapOf("the" to listOf(Locale.ENGLISH), "cat" to listOf(Locale.ENGLISH), "fur" to listOf(Locale.ENGLISH))
        results(main("für", 650000, Locale.GERMAN), main("fur", 300000, Locale.ENGLISH))
        assertEquals(null, type("fur", listOf("the", "cat")).correction())
    }
}

private var reportLocale: Locale = Locale.GERMAN
private var reportResults = SuggestionResults(18, false, false)
private var reportKnowing = emptyMap<String, List<Locale>>()

@Implements(DictionaryFacilitatorImpl::class)
class ShadowReportFacilitator {
    @Implementation fun getCurrentLocale(): Locale = reportLocale
    @Implementation fun getMainLocale(): Locale = reportLocale
    @Implementation fun hasAtLeastOneInitializedMainDictionary() = true
    @Implementation fun mainLocalesKnowing(word: String): List<Locale> = (reportKnowing[word] ?: reportKnowing[word.lowercase()]).orEmpty()
    @Implementation fun mainFrequency(word: String, language: String): Int = Dictionary.NOT_A_PROBABILITY
    @Implementation fun getSuggestionResults(composedData: ComposedData, ngramContext: NgramContext, keyboard: Keyboard,
                                             settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, inputStyle: Int
    ): SuggestionResults = reportResults
}

private class ReportDict(type: String, locale: Locale) : Dictionary(type, locale) {
    override fun getSuggestions(composedData: ComposedData?, ngramContext: NgramContext?, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?, sessionId: Int, weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?): ArrayList<SuggestedWordInfo> = ArrayList()
    override fun isInDictionary(word: String?): Boolean = false
}
