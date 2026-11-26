// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.OtherLanguageDictionaries
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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
        reportFrequency = emptyMap()
        reportHistory = emptyMap()
        reportPredictions = null
        helium314.keyboard.agent.RejectedCorrections.clear(latinIME)
        helium314.keyboard.agent.RuleCorrections.resetForTest()
        OtherLanguageDictionaries.source = otherLanguages(emptyMap())
    }

    private val originalSource = OtherLanguageDictionaries.source
    @AfterTest fun restore() { OtherLanguageDictionaries.source = originalSource }

    /** Stage 101: the main dictionaries of the owner's other enabled languages, language -> word -> frequency. */
    private fun otherLanguages(words: Map<String, Map<String, Int>>) = object : OtherLanguageDictionaries.Source {
        override fun languages(context: Context?, active: Set<String>) = words.keys.filter { it !in active }
        override fun frequency(language: String, word: String) = words[language]?.get(word) ?: Dictionary.NOT_A_PROBABILITY
    }

    private fun user(word: String, score: Int, locale: Locale) = SuggestedWordInfo(word, "", score,
        SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, ReportDict(Dictionary.TYPE_USER, locale), 0, 0)

    /** The owner's settings (stage 101): level "Behutsam" (keep valid words, umlaut words, strength 0.24), swapped letters and sentence context on. */
    private fun ownerSettings() {
        settings(keepValid = true)
        latinIME.prefs().edit {
            putBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true)
            putBoolean(AgentPreferences.AUTOCORRECT_SWAPPED_LETTERS, true)
            putBoolean(AgentPreferences.AUTOCORRECT_SENTENCE_CONTEXT, true)
            putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
        }
    }

    /** English and Spanish as the owner's other layouts (frequencies read from main_en-US.dict / main_es.dict). */
    private val ownerOtherLanguages = mapOf(
        "en" to mapOf("God" to 129, "god" to 124, "that" to 192, "sucks" to 79, "cooled" to 109, "lame" to 86, "fur" to 116,
            "but" to 181, "story" to 151, "cool" to 118, "the" to 222, "cat" to 140, "is" to 200, "wine" to 129, "ICJ" to 78),
        "es" to mapOf("lame" to 68),
    )
    /** The same words in the German main dictionary (main_de.dict). */
    private val ownerGerman = mapOf("de" to mapOf("damit" to 162, "but" to 97, "story" to 75, "cool" to 78, "is" to 120,
        "ich" to 139, "glaube" to 120, "das" to 195, "ist" to 180, "tippe" to 40, "gab" to 162, "nur" to 177, "noch" to 176,
        "sehe" to 120, "Ich" to 139))

    private fun germanOwner() {
        reportLocale = Locale.GERMAN
        ownerSettings()
        reportFrequency = ownerGerman
        OtherLanguageDictionaries.source = otherLanguages(ownerOtherLanguages)
    }

    private fun SuggestedWords.offers(word: String) = (0 until size()).any { getWord(it) == word }

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

    private fun type(typed: String, previous: List<String> = emptyList(), sentenceStart: Boolean = false): SuggestedWords {
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
        val ngram = if (previous.isEmpty()) (if (sentenceStart) NgramContext.BEGINNING_OF_SENTENCE else NgramContext.EMPTY_PREV_WORDS_INFO)
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
        // stage 92: fixed - a target only the typing history knows is offered, never set by itself
        assertEquals(null, words.correction(), "a history-only non-word must not replace the typed word")
        assertTrue((0 until words.size()).any { words.getWord(it) == "nicbmal" }, "it stays in the strip as a suggestion")
        // the same with the cautious level: it protects typed words, not the target of a correction
        settings(keepValid = true)
        assertEquals(null, type("nichtmal", listOf("und")).correction())
        // and for the apostrophe case of finding 2: "war's" becomes the learned typo "war'a"
        results(history("war'a", 600000, Locale.GERMAN), main("war", 300000, Locale.GERMAN))
        assertEquals(null, type("war's", listOf("und")).correction())
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
        // stage 101 (owner's finding 10): changed on purpose, the English layout gets "I" with sentence context on (the owner's setting)
        assertEquals("I", type("i", listOf("and")).correction())
        // with sentence context off the old behaviour stays: no automatic capital, offered in the strip
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_SENTENCE_CONTEXT, false) }
        val words = type("i", listOf("and"))
        assertEquals(null, words.correction(), "without sentence context: no automatic capital")
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

    // ---- stage 101: the owner's findings of 2026-11-25, with his settings ---------------------------------------------
    // German layout, no second language; English and Spanish are other layouts (their dictionaries are looked up read-only).

    // A: regular forms of German verbs that main_de lacks ("übermittle", "marschiere", "faselte", "fiedel" are in no dictionary)
    @Test fun `stage 101 finding 1 - ich uebermittle stays`() {
        germanOwner()
        results(main("Übermittler", 1500000, Locale.GERMAN))
        val words = type("übermittle", listOf("Ich"))
        assertEquals(null, words.correction())
        assertTrue(words.offers("Übermittler"), "offered in the strip")
        // control, the same engine result for a typo that is no verb form: corrected
        results(main("mache", 1500000, Locale.GERMAN))
        assertEquals("mache", type("machre", listOf("ich")).correction())
    }

    @Test fun `stage 101 findings 2, 4, 5 - marschiere, faselte, fiedel stay`() {
        germanOwner()
        results(main("marschierte", 1500000, Locale.GERMAN))
        assertEquals(null, type("marschiere", listOf("ich")).correction())
        results(main("fädelte", 1500000, Locale.GERMAN))
        assertEquals(null, type("faselte", listOf("ich")).correction())
        results(main("Friedel", 1500000, Locale.GERMAN))
        assertEquals(null, type("fiedel", listOf("ich")).correction())
        // a whitelist entry of the dictionary still applies
        results(whitelist("Friedel", Locale.GERMAN))
        assertEquals("Friedel", type("fiedel", listOf("ich")).correction())
    }

    // B: English words typed on the German layout
    @Test fun `stage 101 finding 3 - God and sucks stay in an English sentence`() {
        germanOwner()
        results(main("Tod", 1500000, Locale.GERMAN))
        // control: without the English dictionary (not loaded yet) the old behaviour
        OtherLanguageDictionaries.source = otherLanguages(emptyMap())
        assertEquals("Tod", type("God").correction())
        OtherLanguageDictionaries.source = otherLanguages(ownerOtherLanguages)
        assertEquals(null, type("God").correction())
        results(main("Ducks", 1500000, Locale.GERMAN))
        assertEquals(null, type("sucks", listOf("God", "damit", "that")).correction())
    }

    @Test fun `stage 101 finding 6 - cooled stays after thats`() {
        germanOwner()
        results(main("cooler", 1500000, Locale.GERMAN))
        assertEquals(null, type("cooled", listOf("thats")).correction())
    }

    @Test fun `stage 101 B counter cases - a German sentence and the umlaut twin fur`() {
        germanOwner()
        results(main("eine", 1500000, Locale.GERMAN))
        assertEquals("eine", type("wine", listOf("das", "ist")).correction(), "a German sentence: no protection")
        results(main("für", 1500000, Locale.GERMAN))
        assertEquals("für", type("fur", listOf("the", "cat")).correction(), "stage 46/51: the twin is still set")
    }

    // C: capitals and accents as targets
    @Test fun `stage 101 findings 7 and 8 - lame and alr stay`() {
        germanOwner()
        results(main("Lamé", 1500000, Locale.GERMAN))
        val lame = type("lame", listOf("thats"))
        assertEquals(null, lame.correction())
        assertTrue(lame.offers("Lamé"))
        // typed with the automatic capital of a sentence start, so that the engine's score alone would allow "ALR"
        // (the test's normalised score is 0 for an edit distance as long as the word: "alr" -> "ALR" would not reach it)
        results(main("ALR", 1500000, Locale.GERMAN), main("alt", 300000, Locale.GERMAN))
        assertEquals(null, type("Alr").correction())
    }

    @Test fun `stage 101 auf became AIF - aif stays, auf is offered`() {
        germanOwner()
        results(main("AIF", 1500000, Locale.GERMAN), main("auf", 600000, Locale.GERMAN))
        // capital first letter as at a sentence start (see above); in the middle of a sentence "aif" -> "AIF" is vetoed the same way
        val words = type("Aif")
        assertEquals(null, words.correction())
        assertTrue(words.offers("Auf"), "the meant word is offered")
        assertEquals(null, type("aif", listOf("versehentlich")).correction())
        // a whitelist entry of the dictionary in capitals is still set
        results(whitelist("AIF", Locale.GERMAN))
        assertEquals("AIF", type("Aif").correction())
    }

    // 9: today's behaviour, open (no dictionary and no list knows "gah")
    @Test fun `stage 101 finding 9 - gah still becomes gab`() {
        germanOwner()
        results(main("gab", 1500000, Locale.GERMAN))
        assertEquals("gab", type("gah").correction(), "open: see docs/AUTOKORREKTUR-101-KONZEPT.md")
    }

    // D: too little correction
    @Test fun `stage 101 icj only in the typing history becomes ich`() {
        germanOwner()
        // the engine treats the history entry as the typed word and offers nothing else
        results(history("icj", 600000, Locale.GERMAN))
        assertEquals("ich", type("icj", listOf("tippe")).correction())
        // in the personal dictionary (automatically or by hand: not to be told apart): stays
        results(user("icj", 600000, Locale.GERMAN))
        assertEquals(null, type("icj", listOf("tippe")).correction())
        // in an English sentence: stays
        results(history("gah", 600000, Locale.GERMAN))
        assertEquals(null, type("gah", listOf("the", "cat", "is")).correction())
    }

    @Test fun `stage 101 mur stays on Behutsam, mocb gets no new rule`() {
        germanOwner()
        // stage 107: without a typing history and without a sentence that predicts "nur" (see the stage 107 tests)
        reportPredictions = SuggestionResults(18, false, false)
        reportKnowing = mapOf("mur" to listOf(Locale.GERMAN))
        results(main("mur", 300000, Locale.GERMAN), main("nur", 1500000, Locale.GERMAN))
        assertEquals(null, type("mur", listOf("sehe")).correction(), "a dictionary word with 'Richtige Wörter nie ändern'")
        results(history("mocb", 600000, Locale.GERMAN))
        assertEquals(null, type("mocb", listOf("brauchst")).correction())
    }

    // ---- stage 107: rare dictionary words and the owner's weights ----------------------------------------------------------
    private val ownerRare = mapOf("mur" to 56, "nur" to 177, "AIF" to 77, "auf" to 194, "ALR" to 69, "alt" to 135, "BSI" to 78,
        "bei" to 186, "gebt" to 59, "geht" to 150, "sehe" to 120, "ihr" to 160)

    private fun rareOwner() {
        germanOwner()
        reportFrequency = mapOf("de" to ownerGerman.getValue("de") + ownerRare)
        reportPredictions = SuggestionResults(18, false, false) // no next-word evidence unless a test gives it
    }

    @Test fun `stage 107 mur becomes nur when the owner uses nur and never mur`() {
        rareOwner()
        reportKnowing = mapOf("mur" to listOf(Locale.GERMAN))
        results(main("mur", 300000, Locale.GERMAN), main("nur", 1500000, Locale.GERMAN))
        // without a history: stays (no sentence evidence either)
        assertEquals(null, type("mur", listOf("sehe")).correction())
        // the owner's history: "nur" used often, "mur" never
        reportHistory = mapOf("de" to mapOf("nur" to 160))
        assertEquals("nur", type("mur", listOf("sehe")).correction())
        // a few kept "mur" do not protect it while "nur" is far ahead (the trap of kept typos)
        reportHistory = mapOf("de" to mapOf("nur" to 160, "mur" to 111))
        assertEquals("nur", type("mur", listOf("sehe")).correction())
        // the owner writes "mur" about as often as "nur": stays
        reportHistory = mapOf("de" to mapOf("nur" to 160, "mur" to 150))
        assertEquals(null, type("mur", listOf("sehe")).correction())
    }

    @Test fun `stage 107 one Backspace stops the pair for good, also without the switch for undone corrections`() {
        rareOwner()
        reportKnowing = mapOf("mur" to listOf(Locale.GERMAN))
        reportHistory = mapOf("de" to mapOf("nur" to 160))
        results(main("mur", 300000, Locale.GERMAN), main("nur", 1500000, Locale.GERMAN))
        assertEquals("nur", type("mur", listOf("sehe")).correction())
        assertFalse(Settings.getValues().mRememberRejectedCorrections, "the owner's switch is not needed")
        // what InputLogic does when Backspace reverts the correction
        helium314.keyboard.agent.RejectedCorrections.remember(latinIME, "mur", "nur", Settings.getValues())
        assertEquals(1, helium314.keyboard.agent.RuleCorrections.undone.get())
        val again = type("mur", listOf("sehe"))
        assertEquals(null, again.correction())
        assertTrue(again.offers("nur"), "still offered")
    }

    @Test fun `stage 107 aif becomes auf with the owner's history, alr and bsi stay`() {
        rareOwner()
        reportHistory = mapOf("de" to mapOf("auf" to 170, "alt" to 160, "bei" to 170, "BSI" to 160))
        results(main("AIF", 1500000, Locale.GERMAN), main("auf", 600000, Locale.GERMAN))
        assertEquals("auf", type("aif", listOf("versehentlich")).correction())
        // "alt" (135) is no very common word: "alr" never becomes "alt"
        results(main("ALR", 1500000, Locale.GERMAN), main("alt", 300000, Locale.GERMAN))
        assertEquals(null, type("alr", listOf("cool")).correction())
        // the owner writes "BSI" about as often as "bei": stays
        results(main("BSI", 1500000, Locale.GERMAN))
        assertEquals(null, type("bsi", listOf("beim")).correction())
    }

    @Test fun `stage 107 gebt - stays without history and context, follows the sentence or the history`() {
        rareOwner()
        reportKnowing = mapOf("gebt" to listOf(Locale.GERMAN))
        results(main("gebt", 300000, Locale.GERMAN), main("geht", 600000, Locale.GERMAN))
        assertEquals(null, type("gebt", listOf("ihr")).correction(), "no history, no sentence evidence")
        // without a history the sentence decides: the engine predicts "geht" here, not "gebt"
        reportPredictions = SuggestionResults(18, false, false).apply { add(main("geht", 300, Locale.GERMAN)) }
        assertEquals("geht", type("gebt", listOf("es")).correction())
        reportPredictions = SuggestionResults(18, false, false).apply { add(main("geht", 300, Locale.GERMAN)); add(main("gebt", 290, Locale.GERMAN)) }
        assertEquals(null, type("gebt", listOf("ihr")).correction(), "both likely in this sentence")
        // with the owner's history: he writes "gebt" himself, so it stays
        reportPredictions = SuggestionResults(18, false, false)
        reportHistory = mapOf("de" to mapOf("geht" to 150, "gebt" to 140))
        assertEquals(null, type("gebt", listOf("ihr")).correction())
        reportHistory = mapOf("de" to mapOf("geht" to 150))
        assertEquals("geht", type("gebt", listOf("ihr")).correction(), "never typed before, 'geht' used a lot")
    }

    // ---- stage 108: German short forms get the apostrophe --------------------------------------------------------------
    private fun shortForms() {
        germanOwner()
        reportFrequency = mapOf("de" to ownerGerman.getValue("de") + mapOf("gibt" to 150, "wird" to 170, "dreht" to 90, "geht" to 150,
            "war" to 190, "hat" to 175, "und" to 213, "nichts" to 180))
        reportKnowing = mapOf("nichts" to listOf(Locale.GERMAN), "rechts" to listOf(Locale.GERMAN), "Amts" to listOf(Locale.GERMAN))
        results()
    }

    @Test fun `stage 108 gibts wirds drehts wars get the apostrophe`() {
        shortForms()
        assertEquals("gibt's", type("gibts", listOf("und")).correction())
        assertEquals("wird's", type("wirds", listOf("und")).correction())
        assertEquals("dreht's", type("drehts", listOf("und")).correction())
        assertEquals("geht's", type("gehts", listOf("wie")).correction(), "also a word of the slang list")
        assertEquals("war's", type("wars", listOf("und")).correction(), "a neutral or German sentence")
        assertEquals("Gibt's", type("Gibts", sentenceStart = true).correction(), "a capital at the beginning of a sentence")
    }

    @Test fun `stage 108 counter cases stay`() {
        shortForms()
        for (word in listOf("nichts", "rechts", "amts")) assertEquals(null, type(word, listOf("und")).correction(), word)
        assertEquals(null, type("Peters", listOf("und")).correction(), "a capital inside a sentence")
        assertEquals(null, type("wars", listOf("the", "cat")).correction(), "an English sentence")
        assertEquals(null, type("dings", listOf("und")).correction(), "no verb before the s")
        // undone once: never again
        assertEquals("gibt's", type("gibts", listOf("und")).correction())
        helium314.keyboard.agent.RejectedCorrections.remember(latinIME, "gibts", "gibt's", Settings.getValues())
        assertEquals(null, type("gibts", listOf("und")).correction())
    }

    // 10: the English "i" on the German layout
    @Test fun `stage 101 finding 10 - i in an English sentence becomes I`() {
        germanOwner()
        results()
        assertEquals("I", type("i", listOf("cool", "story", "but")).correction())
        assertEquals("I'm", type("i'm", listOf("story", "but")).correction())
        // first word, a German sentence, and "im": unchanged
        assertEquals(null, type("i").correction())
        assertEquals(null, type("i", listOf("ich", "glaube")).correction())
        assertEquals(null, type("im", listOf("cool", "story", "but")).correction())
        // without English among the owner's languages: unchanged
        OtherLanguageDictionaries.source = otherLanguages(emptyMap())
        assertEquals(null, type("i", listOf("cool", "story", "but")).correction())
    }
}

private var reportLocale: Locale = Locale.GERMAN
private var reportResults = SuggestionResults(18, false, false)
private var reportKnowing = emptyMap<String, List<Locale>>()
/** Stage 101: language -> word -> main-dictionary frequency, for the active (keyboard) language. */
private var reportFrequency = emptyMap<String, Map<String, Int>>()
/** Stage 107: language -> word -> typing-history frequency; a language listed here has a typing history. */
private var reportHistory = emptyMap<String, Map<String, Int>>()
/** Stage 107: the engine's next-word results (empty typed word); null: the same as [reportResults], as before. */
private var reportPredictions: SuggestionResults? = null

@Implements(DictionaryFacilitatorImpl::class)
class ShadowReportFacilitator {
    @Implementation fun getCurrentLocale(): Locale = reportLocale
    @Implementation fun getMainLocale(): Locale = reportLocale
    @Implementation fun hasAtLeastOneInitializedMainDictionary() = true
    @Implementation fun mainLocalesKnowing(word: String): List<Locale> = (reportKnowing[word] ?: reportKnowing[word.lowercase()]).orEmpty()
    @Implementation fun mainFrequency(word: String, language: String): Int =
        reportFrequency[language]?.get(word) ?: Dictionary.NOT_A_PROBABILITY
    @Implementation fun getSuggestionResults(composedData: ComposedData, ngramContext: NgramContext, keyboard: Keyboard,
                                             settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, inputStyle: Int
    ): SuggestionResults = if (composedData.mTypedWord.isEmpty()) reportPredictions ?: reportResults else reportResults
    @Implementation fun historyFrequency(word: String, language: String): Int = reportHistory[language]?.get(word) ?: Dictionary.NOT_A_PROBABILITY
    @Implementation fun hasHistory(language: String): Boolean = reportHistory.containsKey(language)
}

private class ReportDict(type: String, locale: Locale) : Dictionary(type, locale) {
    override fun getSuggestions(composedData: ComposedData?, ngramContext: NgramContext?, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?, sessionId: Int, weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?): ArrayList<SuggestedWordInfo> = ArrayList()
    override fun isInDictionary(word: String?): Boolean = false
}
