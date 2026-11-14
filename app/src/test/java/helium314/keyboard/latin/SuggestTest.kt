// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.core.content.edit
import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.EmojiSuggestions
import helium314.keyboard.agent.RejectedCorrections
import helium314.keyboard.agent.TypoMemory
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_SHORTCUT
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo.KIND_WHITELIST
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("NonAsciiCharacters")
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowBinaryDictionaryUtils::class,
    ShadowFacilitator::class,
])
class SuggestTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val suggest get() = latinIME.mInputLogic.mSuggest

    private val confidenceModest = 0.24f
    private val confidenceAggressive = 0.65f
    private val confidenceVeryAggressive = 0.9f

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    @BeforeTest fun reset() {
        latinIME.prefs().edit { clear() }
        currentTypingLocale = Locale.ENGLISH
        tapTypingSuggestions = suggestionResults(emptyList())
        glideTypingSuggestions = suggestionResults(emptyList())
        nextWordSuggestions = suggestionResults(emptyList())
        RejectedCorrections.clear(latinIME)
        TypoMemory.clear(latinIME)
        EmojiSuggestions.setTableForTest(null) // no emojis unless a test loads the table
    }

    @Test fun `'on' to 'in' if 'in' was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "on",
            listOf(suggestion("on", 1800000, locale), suggestion("in", 600000, locale)),
            suggestion("in", 240, locale),
            null, // never typed "on" in this context
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first suggestion score is too low
    }

    @Test fun `'ill' to 'I'll' if 'ill' not used before in this context, and I'll is whitelisted`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            null,
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
        // correction because both empty scores are 0, which should be fine (next check is comparing empty scores)
    }

    @Test fun `not 'ill' to 'I'll' if only 'ill' was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            suggestion("ill", 200, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first empty score not high enough
    }

    @Test fun `'ill' to 'I'll' if both have same ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 200, locale),
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no 'ill' to 'I'll' if 'ill' has somewhat better ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 211, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `no English 'I' for Polish 'i' when typing in Polish`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale("pl"),
            confidenceVeryAggressive
        )
        assert(!result.last()) // should not be corrected
        // not even checking at modest and aggressive thresholds, this is a locale thing
        // if very aggressive, still no correction because locale matches with typed word only
    }

    @Test fun `English 'I' instead of Polish 'i' when typing in English`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale.ENGLISH,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no English 'in' instead of French 'un' when typing in French`() {
        val result = shouldBeAutoCorrected(
            "un",
            listOf(suggestion("in", Int.MAX_VALUE, Locale.ENGLISH), suggestion("un", 1500000, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because of locale matching
    }

    @Test fun `no 'né' instead of 'ne'`() {
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, Locale.FRENCH), suggestion("né", 1900000-1, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because score is lower
    }

    @Test fun `'né' instead of 'ne' if 'né' in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            null,
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `'né' instead of 'ne' if 'né' has clearly better score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 215, locale),
            suggestion("ne", 200, locale),
            locale,
            confidenceModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no 'né' instead of 'ne' if both with same score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `no 'ne' instead of 'né'`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "né",
            listOf(suggestion("ne", 600000, locale), suggestion("né", 1600000, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            confidenceModest
        )
        assert(!result.last()) // should not be corrected
        // not even allowed to check because of low score for ne
    }

    @Test fun `shortcuts might be autocorrected by default`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 700000, locale, true)),
            null,
            null,
            locale,
            confidenceAggressive
        )
        assert(result.last()) // should be corrected

        val result2 = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 300000, locale, true)),
            null,
            null,
            locale,
            confidenceModest
        )
        assert(!result2.last()) // should not be corrected
    }

    @Test fun `shortcuts are not autocorrected when setting is off`() {
        val prefs = latinIME.prefs()
        prefs.edit { putBoolean(Settings.PREF_AUTOCORRECT_SHORTCUTS, false) }
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "gd",
            listOf(suggestion("good", 12000000, locale, true)),
            null,
            null,
            locale,
            confidenceAggressive
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `typed word is first suggestion`() { // first suggestion will not be shown to the user
        tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 123, currentTypingLocale)))
        val results = getSuggestedWords(false, "henlo", CapsMode.OFF)
        assertEquals(listOf("henlo", "hello"), results.mSuggestedWordInfoList.map { it.mWord })

        tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 123, currentTypingLocale)))
        val results2 = getSuggestedWords(false, "hello", CapsMode.OFF)
        assertEquals(listOf("hello"), results2.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `typed word is second suggestion if autocorrect is pending`() {
        enableAutocorrect(confidenceVeryAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("hello", 650000, currentTypingLocale), // 600000 is the limit
            suggestion("hell", 620000, currentTypingLocale),
            suggestion("hem", 100000, currentTypingLocale),
        ))
        val results = getSuggestedWords(false, "henlo", CapsMode.OFF)
        assert(results.mWillAutoCorrect)
        assertEquals(5, results.mSuggestedWordInfoList.size)
        assertEquals("henlo", results.mSuggestedWordInfoList[0].mWord) // typed word, not shown
        assertEquals("hello", results.mSuggestedWordInfoList[1].mWord) // autocorrection is first suggestion
        assertEquals("henlo", results.mSuggestedWordInfoList[2].mWord) // if autocorrection is pending, typed word is next suggestion
        assertEquals("hell", results.mSuggestedWordInfoList[3].mWord)
        assertEquals("hem", results.mSuggestedWordInfoList[4].mWord)
    }

    @Test fun `CenterSuggestionTextToEnter has typed text or autocorrect as first suggestion`() {
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("surge", 1000),
            suggestion("sure", 935),
            suggestion("siege", 925),
        ))
        val results = getSuggestedWords(false, "suge", CapsMode.OFF)
        assertEquals(listOf("suge", "surge", "sure", "siege"), results.mSuggestedWordInfoList.map { it.mWord })

        latinIME.prefs().edit { putBoolean(Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER, true) }
        // first suggestion duplicated because the first element in the list is not shown to the user
        val results2 = getSuggestedWords(false, "suge", CapsMode.OFF)
        assertEquals(listOf("suge", "suge", "surge", "sure", "siege"), results2.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `empty typed word gives next word suggestions`() {
        nextWordSuggestions = suggestionResults(listOf(
            suggestion("another", 24, currentTypingLocale),
            suggestion("next", 123, currentTypingLocale),
        ))
        val results = getSuggestedWords(false, "", CapsMode.OFF)
        assertEquals(listOf("next", "another"), results.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `single letter suggestions are suppressed dependent on score of other suggestions`() {
        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("a", 100),
            suggestion("next", 95), // more than 94% -> moved to top
        ))
        val results = getSuggestedWords(true, "", CapsMode.OFF)
        assertEquals(listOf("next", "a"), results.mSuggestedWordInfoList.map { it.mWord })
        assertEquals(2, results.mSuggestedWordInfoList.size)
        assertEquals(93, results.mSuggestedWordInfoList[1].mScore) // score is 93% of original

        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("a", 100),
            suggestion("next", 90),
        ))
        val results2 = getSuggestedWords(true, "", CapsMode.OFF)
        assertEquals(listOf("a", "next"), results2.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `prefer next word suggestions`() {
        // This behavior uses personal history; the private fork requires opt-in.
        latinIME.prefs().edit { putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true) }
        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("ab", 1000),
            suggestion("next", 935),
            suggestion("something", 925), // not used because less than 93%
        ))
        nextWordSuggestions = suggestionResults(listOf(
            suggestion("next", 180),
            suggestion("something", 175),
            suggestion("ab", 160), // not used because less than 170
        ))
        val results = getSuggestedWords(true, "", CapsMode.OFF)
        assertEquals(listOf("next", "ab", "something"), results.mSuggestedWordInfoList.map { it.mWord })
        assertEquals(listOf(935, 1000, 925), results.mSuggestedWordInfoList.map { it.mScore })

        nextWordSuggestions = suggestionResults(listOf(
            suggestion("next", 180),
            suggestion("something", 175),
            suggestion("ab", 172), // now it's used
        ))
        assertEquals("ab", getSuggestedWords(true, "", CapsMode.OFF).mSuggestedWordInfoList[0].mWord)
    }

    @Test fun `single quotes at end are attached to suggestions`() {
        // the single quote is a word connector, so it's part of the typed word if at the end (or in the middle, but we're not interested in that)
        // the suggestions are for the normal word without ', and then the ' is attached
        // this has weird effects, because there are suggestions where the ' does not fit
        //  e.g. for emoji suggestions and languages where ' isn't used in words
        // todo: improve the situation on this!
        //  disable for shortcuts?
        //  maybe also could be removed from word connectors for a bunch of languages

        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("ab", 1000),
            suggestion("next", 935),
            suggestion("something", 925),
            suggestion("🎄", 924),
        ))
        val results = getSuggestedWords(false, "someword'", CapsMode.OFF)
        assertEquals(listOf("someword'", "ab'", "next'", "something'", "🎄'"), results.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `tap typing suggestions depend on typed word capitalization, ignoring automatic caps modes`() {
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        listOf(CapsMode.OFF, CapsMode.AUTO, CapsMode.AUTO_LOCKED).forEach { mode ->
            // not capitalized -> original suggestions
            assertEquals(listOf("but", "buy", "bit", "😢", "Butter"),
                getSuggestedWords(false, "but", mode).mSuggestedWordInfoList.map { it.mWord })

            // capitalized -> capitalized suggestions
            assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
                getSuggestedWords(false, "But", mode).mSuggestedWordInfoList.map { it.mWord })

            // more than one uppercase character, but not all -> original suggestions
            assertEquals(listOf("BuT", "but", "buy", "bit", "😢", "Butter"),
                getSuggestedWords(false, "BuT", mode).mSuggestedWordInfoList.map { it.mWord })

            // full uppercase typed word -> uppercase suggestions
            assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
                getSuggestedWords(false, "BUT", mode).mSuggestedWordInfoList.map { it.mWord })
        }
    }

    @Test fun `next word and glide typing suggestions use automatic caps modes`() {
        glideTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))
        assertEquals(listOf("but", "buy", "bit", "😢", "Butter"),
            getSuggestedWords(true, "", CapsMode.OFF).mSuggestedWordInfoList.map { it.mWord })
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
            getSuggestedWords(true, "", CapsMode.AUTO).mSuggestedWordInfoList.map { it.mWord })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(true, "", CapsMode.AUTO_LOCKED).mSuggestedWordInfoList.map { it.mWord })

        nextWordSuggestions = glideTypingSuggestions
        assertEquals(listOf("but", "buy", "bit", "😢", "Butter"),
            getSuggestedWords(false, "", CapsMode.OFF).mSuggestedWordInfoList.map { it.mWord })
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
            getSuggestedWords(false, "", CapsMode.AUTO).mSuggestedWordInfoList.map { it.mWord })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(false, "", CapsMode.AUTO_LOCKED).mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `no autocorrect if more than one uppercase character in typed word, but not all uppercase`() {
        enableAutocorrect(confidenceVeryAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 650000),
            suggestion("bit", 620000),
            suggestion("buy", 500000),
            suggestion("😢", 100000),
            suggestion("Butter", 80000),
        ))

        val result = getSuggestedWords(false, "bur", CapsMode.OFF)
        assert(result.mWillAutoCorrect)
        assertEquals(listOf("bur", "but", "bur", "bit", "buy", "😢", "Butter"), result.mSuggestedWordInfoList.map { it.mWord })

        val result2 = getSuggestedWords(false, "Bur", CapsMode.OFF)
        assert(result2.mWillAutoCorrect)
        assertEquals(listOf("Bur", "But", "Bur", "Bit", "Buy", "😢", "Butter"), result2.mSuggestedWordInfoList.map { it.mWord })

        val result3 = getSuggestedWords(false, "BUr", CapsMode.OFF)
        assert(!result3.mWillAutoCorrect)
        assertEquals(listOf("BUr", "but", "bit", "buy", "😢", "Butter"), result3.mSuggestedWordInfoList.map { it.mWord })

        val result4 = getSuggestedWords(false, "BUR", CapsMode.OFF)
        assert(result4.mWillAutoCorrect)
        assertEquals(listOf("BUR", "BUT", "BUR", "BIT", "BUY", "😢", "BUTTER"), result4.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `suggestions use manual caps modes`() {
        // added in https://github.com/HeliBorg/HeliBoard/pull/1807 / cb0eae695f0cbd061e5bbcc416d6e14d18d869d8
        // manual caps mode can be set any time, even if there are already suggestions
        // on the phone this will give different suggestions because there is different proximityInfo (coming from keyboard, which is different when shifted)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "but", CapsMode.MANUAL)
        assert(!result.mWillAutoCorrect)
        assertEquals(listOf("but", "But", "Buy", "Bit", "😢", "Butter"), result.mSuggestedWordInfoList.map { it.mWord })

        // caps lock -> uppercase suggestions
        assertEquals(listOf("but", "BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(false, "but", CapsMode.MANUAL_LOCKED).mSuggestedWordInfoList.map { it.mWord })

        // same for glide typing
        glideTypingSuggestions = tapTypingSuggestions
        val result2 = getSuggestedWords(true, "", CapsMode.MANUAL)
        assert(!result2.mWillAutoCorrect)
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"), result2.mSuggestedWordInfoList.map { it.mWord })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(true, "", CapsMode.MANUAL_LOCKED).mSuggestedWordInfoList.map { it.mWord })

        // same for next word suggestions
        nextWordSuggestions = tapTypingSuggestions
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"),
            getSuggestedWords(false, "", CapsMode.MANUAL).mSuggestedWordInfoList.map { it.mWord })
        assertEquals(listOf("BUT", "BUY", "BIT", "😢", "BUTTER"),
            getSuggestedWords(false, "", CapsMode.MANUAL_LOCKED).mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `autocorrect will "correct" capitalization with manual caps modes`() {
        enableAutocorrect(confidenceAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "but", CapsMode.MANUAL)
        assert(result.mWillAutoCorrect)
        assertEquals(listOf("but", "But", "Buy", "Bit", "😢", "Butter"), result.mSuggestedWordInfoList.map { it.mWord })

        // caps lock -> uppercase suggestions
        val result2 = getSuggestedWords(false, "but", CapsMode.MANUAL_LOCKED)
        assert(result2.mWillAutoCorrect)
        assertEquals(listOf("but", "BUT", "BUY", "BIT", "😢", "BUTTER"), result2.mSuggestedWordInfoList.map { it.mWord })

        glideTypingSuggestions = tapTypingSuggestions
        val result3 = getSuggestedWords(true, "", CapsMode.MANUAL)
        // mWillAutoCorrect is true on phone, because after glide typing is done, the result is set as typed word
        // when pressing shift to capitalize it, autocorrect will only work if the typed word changes when capitalized. this does not work without typed word
        //assert(result3.mWillAutoCorrect)
        assertEquals("but", result3.typedWordInfo.mWord)
        assertEquals(listOf("But", "Buy", "Bit", "😢", "Butter"), result3.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun `no caps mode autocorrect if word has many uppercase letters`() {
        // bug report in https://github.com/HeliBorg/HeliBoard/issues/2162
        enableAutocorrect(confidenceAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 100),
            suggestion("buy", 95),
            suggestion("bit", 90),
            suggestion("😢", 85),
            suggestion("Butter", 80),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "buT", CapsMode.MANUAL)
        assert(!result.mWillAutoCorrect)
        assertEquals(listOf("buT", "BuT", "But", "Buy", "Bit", "😢", "Butter"), result.mSuggestedWordInfoList.map { it.mWord })

        // caps lock -> uppercase suggestions
        val result2 = getSuggestedWords(false, "buT", CapsMode.MANUAL_LOCKED)
        assert(!result2.mWillAutoCorrect)
        assertEquals(listOf("buT", "BUT", "BUY", "BIT", "😢", "BUTTER"), result2.mSuggestedWordInfoList.map { it.mWord })
    }

    // fork, stage 35
    private fun loadEmojiKeywords() = EmojiSuggestions.setTableForTest(
        latinIME.assets.open("emoji_keywords.bin").use { dev.agentkeyboard.core.EmojiKeywords.read(it) })

    private fun isEmoji(word: String) = StringUtils.mightBeEmoji(word.codePointAt(0))

    @Test fun emojiSuggestionsGoBehindTheFirstTwoWords() {
        currentTypingLocale = Locale.GERMAN
        loadEmojiKeywords()
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("Katze", 1000), suggestion("Katzenfutter", 900), suggestion("Kater", 800)))
        // typed word (not shown), two words, the emojis of the word form, the rest
        assertEquals(listOf("Katzen", "Katze", "Katzenfutter", "🐈", "🐱", "Kater"),
            getSuggestedWords(false, "Katzen", CapsMode.OFF).mSuggestedWordInfoList.map { it.mWord })
        // without a table (not loaded yet) nothing changes
        EmojiSuggestions.setTableForTest(null)
        assertEquals(listOf("Katzen", "Katze", "Katzenfutter", "Kater"),
            getSuggestedWords(false, "Katzen", CapsMode.OFF).mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun emojiSuggestionsNeverBecomeTheAutocorrection() {
        loadEmojiKeywords()
        enableAutocorrect(confidenceVeryAggressive)
        tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 650000)))
        val result = getSuggestedWords(false, "helo", CapsMode.OFF) // a typo of the keyword "hello"
        val words = result.mSuggestedWordInfoList.map { it.mWord }
        assertTrue(result.mWillAutoCorrect)
        assertEquals("hello", words[SuggestedWords.INDEX_OF_AUTO_CORRECTION])
        assertTrue(words.any { isEmoji(it) }, "$words")
        assertTrue(words.indexOfFirst { isEmoji(it) } > SuggestedWords.INDEX_OF_AUTO_CORRECTION + 1, "$words")

        // only an emoji to offer: it is shown, but no autocorrection points at it
        currentTypingLocale = Locale.GERMAN
        tapTypingSuggestions = suggestionResults(emptyList())
        val alone = getSuggestedWords(false, "Kaze", CapsMode.OFF)
        assertFalse(alone.mWillAutoCorrect)
        assertEquals(listOf("Kaze", "🐈", "🐱"), alone.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun emojiSuggestionsWithTheTypedWordInTheMiddle() {
        currentTypingLocale = Locale.GERMAN
        loadEmojiKeywords()
        latinIME.prefs().edit { putBoolean(Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER, true) }
        val result = getSuggestedWords(false, "Kaze", CapsMode.OFF)
        assertFalse(result.mWillAutoCorrect)
        // the typed word is shown in the middle, the emojis only beside it
        assertEquals(listOf("Kaze", "Kaze", "🐈", "🐱"), result.mSuggestedWordInfoList.map { it.mWord })
    }

    @Test fun emojiSuggestionsAskTheLanguageOfTheDictionaryThatKnowsTheWord() {
        currentTypingLocale = Locale.GERMAN
        loadEmojiKeywords()
        // German main language, the English dictionary knows "love": English keywords
        tapTypingSuggestions = suggestionResults(listOf(suggestion("love", 1500000, Locale.ENGLISH), suggestion("lobe", 900, Locale.GERMAN)))
        assertTrue(getSuggestedWords(false, "love", CapsMode.OFF).mSuggestedWordInfoList.any { it.mWord == "❤️" })
        // the German dictionary knows "Tag": no English price tag
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Tag", 1500000, Locale.GERMAN), suggestion("Tage", 900, Locale.GERMAN)))
        assertTrue(getSuggestedWords(false, "Tag", CapsMode.OFF).mSuggestedWordInfoList.none { it.mWord == "🏷️" })
        // a word no dictionary knows asks German and English
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Party", 900, Locale.GERMAN)))
        assertTrue(getSuggestedWords(false, "partyy", CapsMode.OFF).mSuggestedWordInfoList.any { isEmoji(it.mWord) })
    }

    @Test fun emojiSuggestionsCountHeliBoardsOwnEmojis() {
        currentTypingLocale = Locale.GERMAN
        loadEmojiKeywords()
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("Katze", 1000), suggestion("Katzenfutter", 900), suggestion("🐱", 700)))
        val words = getSuggestedWords(false, "Katzen", CapsMode.OFF).mSuggestedWordInfoList.map { it.mWord }
        assertEquals(listOf("Katzen", "Katze", "Katzenfutter", "🐱", "🐈"), words) // at most two, the dictionary's first
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("Katze", 1000), suggestion("🐱", 700), suggestion("😺", 600)))
        assertEquals(2, getSuggestedWords(false, "Katzen", CapsMode.OFF).mSuggestedWordInfoList.count { isEmoji(it.mWord) })
    }

    @Test fun emojiSuggestionsNotForPredictionsAndNotForRealWordsByTypo() {
        currentTypingLocale = Locale.GERMAN
        loadEmojiKeywords()
        nextWordSuggestions = suggestionResults(listOf(suggestion("Katze", 100)))
        assertTrue(getSuggestedWords(false, "", CapsMode.OFF).mSuggestedWordInfoList.none { isEmoji(it.mWord) })
        // "habe" is a dictionary word: no typo guess "Hase"
        tapTypingSuggestions = suggestionResults(listOf(suggestion("habe", 1500000), suggestion("haben", 900)))
        assertTrue(getSuggestedWords(false, "habe", CapsMode.OFF).mSuggestedWordInfoList.none { isEmoji(it.mWord) })
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Hase", 900)))
        assertTrue(getSuggestedWords(false, "Hade", CapsMode.OFF).mSuggestedWordInfoList.any { isEmoji(it.mWord) })
    }

    // fork, stage 20a
    @Test fun `known word is not replaced by its umlaut variant unless the user allows it`() {
        currentTypingLocale = Locale.GERMAN
        enableAutocorrect(confidenceVeryAggressive)
        fun mochte(): SuggestedWords {
            tapTypingSuggestions = suggestionResults(listOf(
                suggestion("möchte", 650000), // above the limit
                suggestion("mochte", 300000), // the typed word is a known word, below the limit
            ))
            return getSuggestedWords(false, "mochte", CapsMode.OFF)
        }
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, false) }
        assert(mochte().mWillAutoCorrect) // upstream behaviour: replaced

        latinIME.prefs().edit { remove(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS) } // default: keep
        val kept = mochte()
        assertFalse(kept.mWillAutoCorrect)
        assertTrue(kept.mSuggestedWordInfoList.any { it.mWord == "möchte" }) // still offered

        // an unknown word with an umlaut variant is still corrected
        tapTypingSuggestions = suggestionResults(listOf(suggestion("möchte", 650000)))
        assert(getSuggestedWords(false, "mochte", CapsMode.OFF).mWillAutoCorrect)

        // a word of another active language does not count as known: English "fur" still becomes "für"
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("für", 650000, Locale.GERMAN),
            suggestion("fur", 300000, Locale.ENGLISH),
        ))
        assert(getSuggestedWords(false, "fur", CapsMode.OFF).mWillAutoCorrect)
    }

    @Test fun `known words can be protected completely`() {
        enableAutocorrect(confidenceVeryAggressive)
        fun hell(): SuggestedWords {
            tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 650000), suggestion("hell", 300000)))
            return getSuggestedWords(false, "hell", CapsMode.OFF)
        }
        assert(hell().mWillAutoCorrect)
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, true) }
        assertFalse(hell().mWillAutoCorrect)
        // words that are not in the dictionary are still corrected
        tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 650000)))
        assert(getSuggestedWords(false, "henlo", CapsMode.OFF).mWillAutoCorrect)
    }

    @Test fun `an undone correction is not applied again while remembering is on`() {
        enableAutocorrect(confidenceVeryAggressive)
        fun henlo(): SuggestedWords {
            tapTypingSuggestions = suggestionResults(listOf(suggestion("hello", 650000)))
            return getSuggestedWords(false, "henlo", CapsMode.OFF)
        }
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, true) }
        RejectedCorrections.remember(latinIME, "henlo", "hello", Settings.getValues())
        assertFalse(henlo().mWillAutoCorrect)
        assertTrue(henlo().mSuggestedWordInfoList.any { it.mWord == "hello" })
        // case does not matter; control: another typo is still corrected
        assertFalse(getSuggestedWords(false, "Henlo", CapsMode.OFF).mWillAutoCorrect)
        assert(getSuggestedWords(false, "Hrllo", CapsMode.OFF).mWillAutoCorrect)
        // switched off: the pair is ignored again, but kept
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, false) }
        assert(henlo().mWillAutoCorrect)
        assertEquals(1, RejectedCorrections.all(latinIME).size)
    }

    // fork, stage 20b
    @Test fun `a learned typo is corrected automatically, also to a word no dictionary offered`() {
        enableAutocorrect(confidenceModest)
        tapTypingSuggestions = suggestionResults(listOf(suggestion("nichts", 200000), suggestion("nicht", 150000)))
        assertFalse(getSuggestedWords(false, "nciht", CapsMode.OFF).mWillAutoCorrect) // too unsure on its own

        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, true) }
        repeat(3) { TypoMemory.confirm(latinIME, "nciht", "nicht", null, Settings.getValues()) }
        val learned = getSuggestedWords(false, "nciht", CapsMode.OFF)
        assert(learned.mWillAutoCorrect)
        assertEquals("nicht", learned.mSuggestedWordInfoList[1].mWord) // the autocorrection
        assertEquals("Nicht", getSuggestedWords(false, "nciht", CapsMode.MANUAL).mSuggestedWordInfoList[1].mWord)

        tapTypingSuggestions = suggestionResults(emptyList()) // no dictionary knows it
        repeat(3) { TypoMemory.confirm(latinIME, "lgtm", "looks good", null, Settings.getValues()) }
        val own = getSuggestedWords(false, "lgtm", CapsMode.OFF)
        assert(own.mWillAutoCorrect)
        assertEquals("looks good", own.mSuggestedWordInfoList[1].mWord)

        // autocorrect off: nothing is applied
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTO_CORRECTION, false) }
        assertFalse(getSuggestedWords(false, "lgtm", CapsMode.OFF).mWillAutoCorrect)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTO_CORRECTION, true) }

        // switched off: learned pairs are not used
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, false) }
        assertFalse(getSuggestedWords(false, "lgtm", CapsMode.OFF).mWillAutoCorrect)

        // an undone pair wins over a learned one
        latinIME.prefs().edit {
            putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, true)
            putBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, true)
        }
        RejectedCorrections.remember(latinIME, "lgtm", "looks good", Settings.getValues())
        assertFalse(getSuggestedWords(false, "lgtm", CapsMode.OFF).mWillAutoCorrect)
    }

    @Test fun `a learned pair for a real word needs its context and respects never change correct words`() {
        currentTypingLocale = Locale.GERMAN
        enableAutocorrect(confidenceModest)
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, true) }
        repeat(3) { TypoMemory.confirm(latinIME, "mochte", "möchte", "ich", Settings.getValues()) }
        // "mochte" is a main dictionary word, its best-ranked occurrence comes from another source
        tapTypingSuggestions = suggestionResults(listOf(
            SuggestedWordInfo("mochte", "", 400000, KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, Dictionary.DICTIONARY_USER_TYPED, 0, 0),
            suggestion("mochte", 300000),
            suggestion("möchte", 250000),
        ))
        fun after(previous: String) = suggest.getSuggestedWords(composer("mochte"), NgramContext(NgramContext.WordInfo(previous)),
            keyboard(), Settings.getValues().mSettingsValuesForSuggestion, true, 0, 0)
        assert(after("ich").mWillAutoCorrect)
        assertFalse(after("er").mWillAutoCorrect) // other context: the real word stays

        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, true) }
        assertFalse(after("ich").mWillAutoCorrect)
    }

    // fork, stage 48
    @Test fun `a known word never gets only another case automatically`() {
        currentTypingLocale = Locale.GERMAN
        enableAutocorrect(confidenceVeryAggressive)
        // German verb and noun, the noun ranked first (dictionary or typing history)
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Essen", 650000), suggestion("essen", 300000)))
        val essen = getSuggestedWords(false, "essen", CapsMode.OFF)
        assertFalse(essen.mWillAutoCorrect)
        assertTrue(essen.mSuggestedWordInfoList.any { it.mWord == "Essen" }) // still offered
        // a real typo is still corrected, also to a capitalized word
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Essen", 650000)))
        assert(getSuggestedWords(false, "Esen", CapsMode.OFF).mWillAutoCorrect)
        // an unknown lowercase form is not correctly spelled: "berlin" becomes "Berlin"
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Berlin", 650000)))
        assert(getSuggestedWords(false, "berlin", CapsMode.OFF).mWillAutoCorrect)
        // review M1: another active language knowing the lowercase form does not block it ("hand" -> "Hand")
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Hand", 650000), suggestion("hand", 300000, Locale.ENGLISH)))
        assert(getSuggestedWords(false, "hand", CapsMode.OFF).mWillAutoCorrect)
        // the same language does block it
        tapTypingSuggestions = suggestionResults(listOf(suggestion("Hand", 650000), suggestion("hand", 300000)))
        assertFalse(getSuggestedWords(false, "hand", CapsMode.OFF).mWillAutoCorrect)

        // English: "may" stays, the whitelisted "i" -> "I" of the dictionary stays automatic
        currentTypingLocale = Locale.ENGLISH
        tapTypingSuggestions = suggestionResults(listOf(suggestion("May", 650000), suggestion("may", 300000)))
        assertFalse(getSuggestedWords(false, "may", CapsMode.OFF).mWillAutoCorrect)
        tapTypingSuggestions = suggestionResults(listOf(suggestion("I", Int.MAX_VALUE), suggestion("i", 300000)))
        assert(getSuggestedWords(false, "i", CapsMode.OFF).mWillAutoCorrect)
    }

    private fun composer(word: String) = WordComposer().apply {
        StringUtils.toCodePointArray(word).forEach {
            val e = Event.createEventForCodePointFromAlreadyTypedText(it, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)
            applyProcessedEvent(processEvent(e))
        }
        adviseCapitalizedModeBeforeFetchingSuggestions(CapsMode.OFF)
    }

    private fun keyboard() = Keyboard(KeyboardParams().apply {
        GRID_HEIGHT = 1; GRID_WIDTH = 1; mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET)
    })

    @Test fun `normal autocorrect works with manual caps modes`() {
        enableAutocorrect(confidenceAggressive)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 650000),
            suggestion("bit", 620000),
            suggestion("buy", 500000),
            suggestion("😢", 100000),
            suggestion("Butter", 80000),
        ))

        // shift -> capitalized suggestions
        val result = getSuggestedWords(false, "bur", CapsMode.MANUAL)
        assert(result.mWillAutoCorrect)
        assertEquals(listOf("bur", "But", "Bur", "Bit", "Buy", "😢", "Butter"), result.mSuggestedWordInfoList.map { it.mWord })

        // caps lock -> uppercase suggestions
        val result2 = getSuggestedWords(false, "bur", CapsMode.MANUAL_LOCKED)
        assert(result2.mWillAutoCorrect)
        assertEquals(listOf("bur", "BUT", "BUR", "BIT", "BUY", "😢", "BUTTER"), result2.mSuggestedWordInfoList.map { it.mWord })

        // score too low will correct to capitalized typed word (except if using very aggressive threshold)
        tapTypingSuggestions = suggestionResults(listOf(
            suggestion("but", 6500),
            suggestion("bit", 6200),
            suggestion("buy", 5000),
            suggestion("😢", 1000),
            suggestion("Butter", 800),
        ))
        val result3 = getSuggestedWords(false, "bur", CapsMode.MANUAL)
        assert(result3.mWillAutoCorrect)
        assertEquals(listOf("bur", "Bur", "But", "Bit", "Buy", "😢", "Butter"), result3.mSuggestedWordInfoList.map { it.mWord })

        val result4 = getSuggestedWords(false, "bur", CapsMode.MANUAL_LOCKED)
        assert(result4.mWillAutoCorrect)
        assertEquals(listOf("bur", "BUR", "BUT", "BIT", "BUY", "😢", "BUTTER"), result4.mSuggestedWordInfoList.map { it.mWord })
    }

    // fork, stage 46: an own word of the memory (slang kept three times) is never corrected automatically
    @Test fun `own word of the memory stays as typed and the correction stays a suggestion`() {
        enableAutocorrect(confidenceVeryAggressive)
        fun krass(): SuggestedWords {
            tapTypingSuggestions = suggestionResults(listOf(suggestion("kraft", 650000)))
            return getSuggestedWords(false, "krass", CapsMode.OFF)
        }
        assert(krass().mWillAutoCorrect) // upstream behaviour: replaced
        helium314.keyboard.agent.Memory.resetForTest()
        helium314.keyboard.agent.AgentStorageContexts.keyboard(latinIME)
            .getSharedPreferences(helium314.keyboard.agent.Memory.FILE, android.content.Context.MODE_PRIVATE)
            .edit { putString(helium314.keyboard.agent.Memory.OWN_WORDS, "v1\no;krass") }
        assert(krass().mWillAutoCorrect, { "only with the memory switched on" })
        latinIME.prefs().edit { putBoolean(AgentPreferences.MEMORY, true) }
        val kept = krass()
        assertFalse(kept.mWillAutoCorrect)
        assertTrue(kept.mSuggestedWordInfoList.any { it.mWord == "kraft" }) // still offered
        // also not by a learned typo pair
        latinIME.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, true) }
        repeat(3) { TypoMemory.confirm(latinIME, "krass", "kraft", null, Settings.getValues()) }
        assertFalse(krass().mWillAutoCorrect)
        // removed on the page: corrected again
        assertTrue(helium314.keyboard.agent.Memory.removeOwnWord(latinIME, "krass"))
        assert(krass().mWillAutoCorrect)
        helium314.keyboard.agent.Memory.resetForTest()
    }

    // review of stage 46 (N2): the own word as a completion stays behind a real automatic correction
    @Test fun `own word completion never takes the place of the automatic correction`() {
        enableAutocorrect(confidenceVeryAggressive)
        helium314.keyboard.agent.Memory.resetForTest()
        helium314.keyboard.agent.AgentStorageContexts.keyboard(latinIME)
            .getSharedPreferences(helium314.keyboard.agent.Memory.FILE, android.content.Context.MODE_PRIVATE)
            .edit { putString(helium314.keyboard.agent.Memory.OWN_WORDS, "v1\no;krassomat") }
        latinIME.prefs().edit { putBoolean(AgentPreferences.MEMORY, true) }
        // the memory offers own words only in a text field (the settings of this test have none by default)
        latinIME.mSettings.loadSettings(latinIME, latinIME.mSettings.current.mLocale, InputAttributes(
            android.view.inputmethod.EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT }, false, latinIME.packageName))
        tapTypingSuggestions = suggestionResults(listOf(suggestion("kraft", 650000)))
        val words = getSuggestedWords(false, "kras", CapsMode.OFF)
        assertTrue(words.mWillAutoCorrect)
        assertEquals("kraft", words.mSuggestedWordInfoList[SuggestedWords.INDEX_OF_AUTO_CORRECTION].mWord)
        val own = words.mSuggestedWordInfoList.indexOfFirst { it.mWord == "krassomat" }
        assertTrue(own > SuggestedWords.INDEX_OF_AUTO_CORRECTION, words.mSuggestedWordInfoList.map { it.mWord }.toString())
        assertTrue(words.mSuggestedWordInfoList[own].isKindOf(SuggestedWordInfo.KIND_COMPLETION))
        helium314.keyboard.agent.Memory.resetForTest()
    }

    // fork, stage 51: the "fur" rule of 20a/49 holds until the owner kept "fur" in German as a word of
    // another language; then it stays, only in German, and a remembered word is offered, never the correction
    @Test fun `stage 51 English fur becomes für until the owner kept it in German`() {
        currentTypingLocale = Locale.GERMAN
        enableAutocorrect(confidenceVeryAggressive)
        latinIME.prefs().edit { putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true) }
        fun inLanguage(locale: Locale) = latinIME.mSettings.loadSettings(latinIME, locale, InputAttributes(
            android.view.inputmethod.EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT }, false, latinIME.packageName))
        inLanguage(Locale.GERMAN)
        fun fur(): SuggestedWords {
            tapTypingSuggestions = suggestionResults(listOf(suggestion("für", 650000, Locale.GERMAN), suggestion("fur", 300000, Locale.ENGLISH)))
            return getSuggestedWords(false, "fur", CapsMode.OFF)
        }
        assert(fur().mWillAutoCorrect)
        val foreign = helium314.keyboard.agent.ForeignWords
        try {
            helium314.keyboard.agent.AgentStorageContexts.keyboard(latinIME).getSharedPreferences(foreign.FILE, android.content.Context.MODE_PRIVATE)
                .edit { putString("data", """{"candidates":[],"remembered":[{"lang":"de","word":"fur","from":"en","seen":1},""" +
                    """{"lang":"de","word":"gracias","from":"es","seen":2}]}""") }
            foreign.resetForTest()
            val kept = fur()
            assertFalse(kept.mWillAutoCorrect)
            assertTrue(kept.mSuggestedWordInfoList.any { it.mWord == "für" }) // still offered
            // a remembered word is offered behind the automatic correction, never in its place
            tapTypingSuggestions = suggestionResults(listOf(suggestion("Grab", 650000, Locale.GERMAN)))
            val words = getSuggestedWords(false, "gra", CapsMode.OFF)
            assertTrue(words.mWillAutoCorrect)
            assertEquals("Grab", words.mSuggestedWordInfoList[SuggestedWords.INDEX_OF_AUTO_CORRECTION].mWord)
            val offered = words.mSuggestedWordInfoList.indexOfFirst { it.mWord == "gracias" }
            assertTrue(offered > SuggestedWords.INDEX_OF_AUTO_CORRECTION, words.mSuggestedWordInfoList.map { it.mWord }.toString())
            // only in German
            inLanguage(Locale.forLanguageTag("es"))
            assert(fur().mWillAutoCorrect)
            // and only with "Personalisierte Vorschläge"
            inLanguage(Locale.GERMAN)
            assertFalse(fur().mWillAutoCorrect)
            latinIME.prefs().edit { putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, false) }
            inLanguage(Locale.GERMAN)
            assert(fur().mWillAutoCorrect)
        } finally {
            foreign.clear(latinIME)
        }
    }

    // review of stage 51 (N5): results of another main language (a language command switched the settings,
    // the dictionaries follow later) tell nothing about the keyboard language: the word counts as known
    @Test fun `stage 51 review results of another main language never make a word of another language`() {
        enableAutocorrect(confidenceVeryAggressive)
        latinIME.prefs().edit { putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true) }
        latinIME.mSettings.loadSettings(latinIME, Locale.GERMAN, InputAttributes(
            android.view.inputmethod.EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT }, false, latinIME.packageName))
        val foreign = helium314.keyboard.agent.ForeignWords
        val worker = foreign.worker
        var checks = 0
        foreign.worker = java.util.concurrent.Executor { checks++ } // only counted
        fun committed(): Int {
            tapTypingSuggestions = suggestionResults(listOf(suggestion("gracias", 1500000, Locale.forLanguageTag("es"))))
            getSuggestedWords(false, "gracias", CapsMode.OFF)
            val before = checks
            foreign.onWordCommitted(latinIME, Settings.getValues(), android.view.inputmethod.EditorInfo().apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT; packageName = latinIME.packageName }, "gracias", "gracias", { "gracias" }, 7)
            return checks - before
        }
        try {
            foreign.clear(latinIME)
            currentTypingLocale = Locale.ENGLISH // the facilitator is still on English, the keyboard on German
            assertEquals(0, committed()) // before: checked as a word German does not know
            currentTypingLocale = Locale.GERMAN
            assertEquals(1, committed())
        } finally {
            foreign.worker = worker
            foreign.clear(latinIME)
        }
    }

    // review of stage 46 (M1): a remembered abbreviation is never corrected (nor expanded) automatically
    @Test fun `remembered abbreviation stays as typed`() {
        enableAutocorrect(confidenceVeryAggressive)
        fun mfg(): SuggestedWords {
            tapTypingSuggestions = suggestionResults(listOf(suggestion("mag", 650000)))
            return getSuggestedWords(false, "mfg", CapsMode.OFF)
        }
        assert(mfg().mWillAutoCorrect) // upstream behaviour: replaced
        helium314.keyboard.agent.Memory.resetForTest()
        helium314.keyboard.agent.AgentStorageContexts.keyboard(latinIME)
            .getSharedPreferences(helium314.keyboard.agent.Memory.FILE, android.content.Context.MODE_PRIVATE)
            .edit { putString(helium314.keyboard.agent.Memory.SHORTCUTS, "v1\n2;CREATED;mfg;mit freundlichen Grüßen") }
        latinIME.prefs().edit { putBoolean(AgentPreferences.MEMORY, true) }
        val kept = mfg()
        assertFalse(kept.mWillAutoCorrect)
        assertTrue(kept.mSuggestedWordInfoList.none { it.mWord.contains(' ') }, "no expansion in the strip of the engine")
        helium314.keyboard.agent.Memory.resetForTest()
    }

    private fun getSuggestedWords(gesture: Boolean, typedWord: String, capsMode: CapsMode): SuggestedWords {
        val wc = WordComposer()
        if (gesture) wc.setBatchInputPointers(InputPointers(1))
        if (typedWord.isNotEmpty()) {
            StringUtils.toCodePointArray(typedWord).forEach {
                val e = Event.createEventForCodePointFromAlreadyTypedText(it, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)
                wc.applyProcessedEvent(wc.processEvent(e))
            }
        }
        // we ignore the special treatment of CapsMode.AUTO here (depends on settings, input type, and text before cursor)
        if (gesture)
            wc.setCapitalizedModeAtStartComposingTime(capsMode) // done in inputlogic when entering batch mode
        wc.adviseCapitalizedModeBeforeFetchingSuggestions(capsMode) // done in inputLogic before getSuggestedWords

        val params = KeyboardParams()
        val elementId = when (capsMode) {
            CapsMode.MANUAL_LOCKED -> KeyboardElement.ALPHABET_SHIFT_LOCKED
            CapsMode.MANUAL -> KeyboardElement.ALPHABET_MANUAL_SHIFTED
            CapsMode.AUTO -> KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED
            else -> KeyboardElement.ALPHABET // off
        }
        params.GRID_HEIGHT = 1
        params.GRID_WIDTH = 1
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(elementId)

        suggest.clearNextWordSuggestionsCache()
        return suggest.getSuggestedWords(
            wc, NgramContext.EMPTY_PREV_WORDS_INFO, Keyboard(params), Settings.getValues().mSettingsValuesForSuggestion,
            Settings.getValues().mAutoCorrectEnabled, 0, 0
        )
    }

    private fun enableAutocorrect(autoCorrectThreshold: Float) {
        latinIME.prefs().edit {
            putBoolean(Settings.PREF_AUTO_CORRECTION, true)
            putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, autoCorrectThreshold)
            putBoolean(Settings.PREF_MORE_AUTO_CORRECTION, true)
        }
        suggest.setAutoCorrectionThreshold(Settings.getValues().mAutoCorrectionThreshold)
    }

    private fun shouldBeAutoCorrected(word: String, // typed word
                              suggestions: List<SuggestedWordInfo>, // suggestions ordered by score, including suggestion for typed word if in dictionary
                              firstSuggestionForEmpty: SuggestedWordInfo?, // first suggestion if typed word would be empty (null if none)
                              typedWordSuggestionForEmpty: SuggestedWordInfo?, // suggestion for actually typed word if typed word would be empty (null if none)
                              typingLocale: Locale, // used for checking whether suggestion locale is the same, relevant e.g. for English i -> I shortcut, but we want Polish i
                              autoCorrectThreshold: Float
    ): List<Boolean> {
        enableAutocorrect(autoCorrectThreshold)
        currentTypingLocale = typingLocale
        val suggestionsContainer = ArrayList<SuggestedWordInfo>().apply { addAll(suggestions) }
        val suggestionResults = SuggestionResults(suggestions.size, false, false)
        suggestions.forEach { suggestionResults.add(it) }

        // store the original SuggestedWordInfo for typed word, as it will be removed
        // we may want to re-add it in case auto-correction happens, so that the original word can at least be selected
        val typedWordFirstOccurrenceWordInfo: SuggestedWordInfo? = suggestionsContainer.firstOrNull { it.mWord == word }

        val firstOccurrenceOfTypedWordInSuggestions =
            SuggestedWordInfo.removeDupsAndTypedWord(word, suggestionsContainer)

        return suggest.shouldBeAutoCorrected(
            StringUtils.getTrailingSingleQuotesCount(word),
            word,
            suggestionsContainer.firstOrNull(), // todo: get from suggestions? mostly it's just removing the typed word, right?
            { firstSuggestionForEmpty to typedWordSuggestionForEmpty },
            true, // doesn't make sense otherwise
            WordComposer.getComposerForTest(false),
            suggestionResults,
            firstOccurrenceOfTypedWordInSuggestions,
            typedWordFirstOccurrenceWordInfo
        ).toList()
    }
}

private var currentTypingLocale = Locale.ENGLISH
private var tapTypingSuggestions = suggestionResults(emptyList())
private var glideTypingSuggestions = suggestionResults(emptyList())
private var nextWordSuggestions = suggestionResults(emptyList())

fun suggestion(word: String, score: Int, locale: Locale = currentTypingLocale, shortcut: Boolean = false) =
    SuggestedWordInfo(
        word,
        "", // irrelevant

        // typically IntMax for whitelisted, 1.5M for exact match, 600k for close match
        // when previous word context is empty, scores are usually 200+ if word is known and somewhat often used, 0 if unknown
        score,
        if (score == Int.MAX_VALUE) KIND_WHITELIST
            else if (shortcut) KIND_SHORTCUT // whitelist & shortcut only counts a whitelist
            else KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, // shortcuts seem to never have this flag
        TestDict(locale),
        0, // irrelevant
        0 // irrelevant?
    )

fun suggestionResults(suggestions: List<SuggestedWordInfo>, isBeginningOfSentence: Boolean = false) =
    SuggestionResults(18, isBeginningOfSentence, false).apply { addAll(suggestions) }

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator {
    @Implementation
    fun getCurrentLocale(): Locale = currentTypingLocale
    @Implementation
    fun getMainLocale(): Locale = currentTypingLocale
    @Implementation
    fun hasAtLeastOneInitializedMainDictionary() = true // otherwise no autocorrect
    @Implementation
    // what is relevant for facilitator?
    // only detecting whether we want next word suggestions (typed word empty and not batch mode)
    fun getSuggestionResults(composedData: ComposedData, ngramContext: NgramContext, keyboard: Keyboard,
                             settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, inputStyle: Int
    ): SuggestionResults = when {
        composedData.mIsBatchMode -> glideTypingSuggestions
        composedData.mTypedWord.isEmpty() -> nextWordSuggestions
        else -> tapTypingSuggestions
    }
}

private class TestDict(locale: Locale) : Dictionary(TYPE_MAIN, locale) {
    override fun getSuggestions(
        composedData: ComposedData?,
        ngramContext: NgramContext?,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo> {
        return ArrayList()
    }

    override fun isInDictionary(word: String?): Boolean = false
}
