// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.FieldReport.Caps
import dev.agentkeyboard.core.FieldReport.FieldType
import dev.agentkeyboard.core.FieldReport.Finding
import dev.agentkeyboard.core.FieldReport.Flag
import dev.agentkeyboard.core.FieldReport.Live
import dev.agentkeyboard.core.FieldReport.Snapshot
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 55: the verdict of the card "Letztes Feld" from the values the engine used. */
class FieldReportTest {
    private val text = FieldReport.TYPE_CLASS_TEXT
    private fun verdict(field: Snapshot.Field, live: Live = Live()) = FieldReport.verdict(field, live)

    @Test fun everythingOnIsAllOn() {
        assertEquals(FieldReport.Verdict(Finding.ALL_ON), verdict(Snapshot.Field()))
        assertEquals(FieldReport.Effective(suggestions = true, autoCorrect = true, caps = true, stripCovered = false), FieldReport.effective(Snapshot.Field(), Live()))
    }

    @Test fun aNoSuggestionsFlagMeansTheAppForbidsThem() {
        // as InputAttributes and SettingsValues compute it: no suggestions shown, no lookups, no autocorrection
        val field = Snapshot.Field(inputType = text or FieldReport.FLAG_NO_SUGGESTIONS or FieldReport.FLAG_CAP_SENTENCES,
            appSuggestions = false, appAutoCorrect = false, suggestionsShown = false, lookups = false)
        assertEquals(Finding.APP_NO_SUGGESTIONS, verdict(field).finding)
        assertEquals(listOf(Flag.NO_SUGGESTIONS), field.flags)
        assertFalse(FieldReport.effective(field, Live()).autoCorrect)
        // "always show suggestions" overrides the flag: then the strip shows them and nothing is forbidden
        assertTrue(verdict(field.copy(suggestionsShown = true, lookups = true, moreAutoCorrect = true)).finding != Finding.APP_NO_SUGGESTIONS)
    }

    @Test fun suggestionsOffByTheKeyboardComeBeforeTheApp() {
        val forbidden = Snapshot.Field(appSuggestions = false, suggestionsShown = false, lookups = false)
        assertEquals(Finding.SUGGESTIONS_SETTING_OFF, verdict(forbidden.copy(suggestionsSetting = false)).finding)
        assertEquals(Finding.STRIP_HIDDEN, verdict(forbidden.copy(stripHidden = true)).finding)
        // the setting alone: autocorrection still looks up words
        assertEquals(Finding.SUGGESTIONS_SETTING_OFF, verdict(Snapshot.Field(suggestionsSetting = false, suggestionsShown = false)).finding)
    }

    @Test fun noCapsFlagsMeanTheAppAsksForNoCapitals() {
        val field = Snapshot.Field(inputType = text or FieldReport.FLAG_MULTI_LINE)
        assertEquals(Caps.NONE, field.caps)
        assertEquals(Finding.CAPS_APP, verdict(field).finding)
        assertFalse(FieldReport.effective(field, Live()).caps)
        assertEquals(Caps.WORDS, FieldReport.caps(text or FieldReport.FLAG_CAP_WORDS))
        assertEquals(Caps.CHARACTERS, FieldReport.caps(text or FieldReport.FLAG_CAP_CHARACTERS or FieldReport.FLAG_CAP_SENTENCES))
        assertEquals(Caps.NONE, FieldReport.caps(FieldReport.TYPE_CLASS_NUMBER or FieldReport.FLAG_CAP_SENTENCES))
        // the setting and a script without capitals come first
        assertEquals(Finding.CAPS_SETTING_OFF, verdict(field.copy(autoCapSetting = false, autoCap = false)).finding)
        assertEquals(FieldReport.Verdict(Finding.CAPS_SCRIPT, "ar"), verdict(Snapshot.Field(autoCap = false, languageTag = "ar")))
    }

    @Test fun aUrlFieldDoesNotCorrect() {
        // URI is excluded from the multi-line rule (no app wish), and without URL detection SettingsValues turns it off anyway
        val url = Snapshot.Field(inputType = text or FieldReport.VARIATION_URI, appAutoCorrect = false, autoCorrectEnabled = false)
        assertEquals(FieldType.URL, url.type)
        assertEquals(Finding.AUTOCORRECT_ADDRESS, verdict(url).finding)
        assertEquals(FieldType.EMAIL, FieldReport.fieldType(text or FieldReport.VARIATION_WEB_EMAIL_ADDRESS, 0))
        // with URL detection the reason is that the app did not ask for it
        assertEquals(Finding.AUTOCORRECT_APP, verdict(url.copy(urlDetection = true)).finding)
    }

    @Test fun aSingleLineFieldWithoutTheFlagIsNotCorrectedUnlessMoreAutocorrection() {
        val search = Snapshot.Field(inputType = text or FieldReport.FLAG_CAP_SENTENCES, imeOptions = FieldReport.IME_ACTION_SEARCH,
            appAutoCorrect = false, autoCorrectEnabled = false)
        assertEquals(FieldType.SEARCH, search.type)
        assertEquals(Finding.AUTOCORRECT_APP, verdict(search).finding)
    }

    @Test fun autocorrectionOffInTheSettings() {
        val field = Snapshot.Field(autoCorrectSetting = false, autoCorrectEnabled = false)
        assertEquals(Finding.AUTOCORRECT_SETTING_OFF, verdict(field).finding)
        val effective = FieldReport.effective(field, Live())
        assertTrue(effective.suggestions)
        assertFalse(effective.autoCorrect)
    }

    @Test fun aPasswordFieldKeepsNothingButItsKind() {
        assertEquals(Finding.PASSWORD, FieldReport.verdict(Snapshot.Password).finding)
        for (type in listOf(text or FieldReport.VARIATION_PASSWORD, text or FieldReport.VARIATION_VISIBLE_PASSWORD,
            text or FieldReport.VARIATION_WEB_PASSWORD, FieldReport.TYPE_CLASS_NUMBER or FieldReport.NUMBER_VARIATION_PASSWORD))
            assertTrue(FieldReport.isPassword(type), type.toString(16))
        assertFalse(FieldReport.isPassword(text or FieldReport.VARIATION_URI))
    }

    @Test fun noDictionaryForTheLanguage() {
        val spanish = Snapshot.Field(languageTag = "es")
        assertEquals(FieldReport.Verdict(Finding.NO_DICTIONARY, "es"), verdict(spanish, Live(dictionaryPresent = false, dictionaryLoaded = false)))
        assertEquals(FieldReport.Verdict(Finding.DICTIONARY_NOT_LOADED, "es"), verdict(spanish, Live(dictionaryLoaded = false)))
        assertFalse(FieldReport.effective(spanish, Live(dictionaryLoaded = false)).autoCorrect)
        // unknown (the engine moved on to another language): no claim
        assertEquals(Finding.ALL_ON, verdict(spanish, Live(dictionaryPresent = null, dictionaryLoaded = null)).finding)
        // a field that forbids suggestions names the app, not the dictionary
        assertEquals(Finding.APP_NO_SUGGESTIONS, verdict(spanish.copy(appSuggestions = false, suggestionsShown = false, lookups = false),
            Live(dictionaryPresent = false)).finding)
    }

    @Test fun aRefusedComposingTextIsNamedInTheVerdict() {
        assertFalse(verdict(Snapshot.Field(), Live()).composingRefused)
        val refused = verdict(Snapshot.Field(), Live(composingRefused = 3))
        assertTrue(refused.composingRefused)
        // the reason stays the first finding; the count is a separate sentence
        assertEquals(Finding.ALL_ON, refused.finding)
    }

    @Test fun aLockedDeviceRemembersNothing() {
        assertEquals(FieldReport.Verdict(Finding.LOCKED), FieldReport.verdict(Snapshot.Locked))
    }

    @Test fun aFieldWithoutLearningKeepsItsVerdictAndSaysWhy() {
        // stage 61: the reason is kept where the owner needs it; only the app is not
        val noLearning = Snapshot.Field(inputType = text or FieldReport.FLAG_NO_SUGGESTIONS, appSuggestions = false, appAutoCorrect = false,
            suggestionsShown = false, lookups = false, noLearning = true)
        assertEquals(FieldReport.Verdict(Finding.APP_NO_SUGGESTIONS, learning = FieldReport.Learning.FIELD_ASKS_NONE), verdict(noLearning))
        assertTrue(noLearning.learningOff)
        assertEquals(FieldReport.Learning.INCOGNITO, verdict(Snapshot.Field(incognito = true)).learning)
        assertEquals(FieldReport.Learning.ON, verdict(Snapshot.Field()).learning)
    }

    @Test fun aForcedFieldSaysWhatTheForcingChanged() {
        // a single-line field with NO_SUGGESTIONS and no CAP flag in a forced app, as SettingsValues computes it
        val forced = Snapshot.Field(inputType = text or FieldReport.FLAG_NO_SUGGESTIONS, appSuggestions = false, appAutoCorrect = false,
            forced = true, appForced = true, suggestionsShown = true, lookups = true, autoCorrectEnabled = true)
        assertEquals(Caps.NONE, forced.caps)
        assertEquals(Caps.SENTENCES, forced.capsUsed)
        val verdict = verdict(forced)
        assertEquals(Finding.ALL_ON, verdict.finding)
        assertEquals(listOf(FieldReport.Forced.SUGGESTIONS, FieldReport.Forced.AUTOCORRECT, FieldReport.Forced.CAPS), verdict.forced)
        assertEquals(FieldReport.Effective(suggestions = true, autoCorrect = true, caps = true, stripCovered = false), FieldReport.effective(forced, Live()))
        // the same field not forced: no capitals, and the verdict names none of the three as forced
        val plain = forced.copy(forced = false, appForced = false, suggestionsShown = false, lookups = false, autoCorrectEnabled = false)
        assertEquals(emptyList(), verdict(plain).forced)
        assertFalse(FieldReport.effective(plain, Live()).caps)
        // the owner's switches win: nothing is claimed as forced that they switched off
        assertEquals(listOf(FieldReport.Forced.AUTOCORRECT, FieldReport.Forced.CAPS), verdict(forced.copy(suggestionsSetting = false)).forced)
        assertEquals(listOf(FieldReport.Forced.SUGGESTIONS, FieldReport.Forced.CAPS), verdict(forced.copy(autoCorrectSetting = false)).forced)
        assertEquals(listOf(FieldReport.Forced.SUGGESTIONS, FieldReport.Forced.AUTOCORRECT), verdict(forced.copy(autoCapSetting = false)).forced)
        // an app that already asks for capitals, autocorrection and suggestions has nothing forced
        val asks = Snapshot.Field(inputType = text or FieldReport.FLAG_CAP_SENTENCES or FieldReport.FLAG_AUTO_CORRECT, forced = true)
        assertEquals(emptyList(), verdict(asks).forced)
    }

    @Test fun insideACommandAndChipsInTheStrip() {
        assertEquals(Finding.IN_COMMAND, verdict(Snapshot.Field(), Live(inCommand = true)).finding)
        assertFalse(FieldReport.effective(Snapshot.Field(), Live(inCommand = true)).autoCorrect)
        assertEquals(Finding.CHIPS, verdict(Snapshot.Field(), Live(strip = FieldReport.Strip.KEYBOARD_CHIPS, stripUpdates = 4, chipUpdates = 3)).finding)
        // a permanent reason of the field comes first
        assertEquals(Finding.CAPS_APP, verdict(Snapshot.Field(inputType = text), Live(inCommand = true)).finding)
    }

    @Test fun whatCoversTheStripIsNamedAndNeverAllOn() {
        val field = Snapshot.Field()
        fun finding(strip: FieldReport.Strip?, words: Boolean) = verdict(field, Live(strip = strip, wordsOffered = words)).finding
        assertEquals(Finding.PILL, finding(FieldReport.Strip.PILL, words = false))
        assertEquals(Finding.WORDS_UNDER_TOOLBAR, finding(FieldReport.Strip.TOOLBAR, words = true))
        assertEquals(Finding.STRIP_TOOLBAR, finding(FieldReport.Strip.TOOLBAR, words = false))
        assertEquals(Finding.WORDS_UNDER_EMOJIS, finding(FieldReport.Strip.EMOJIS, words = true))
        assertEquals(Finding.STRIP_EMOJIS, finding(FieldReport.Strip.EMOJIS, words = false))
        assertEquals(Finding.STRIP_NOT_CAPTURED, finding(null, words = false))
        assertEquals(Finding.ALL_ON, finding(FieldReport.Strip.EMPTY, words = false))
        // the result line knows it too
        assertTrue(FieldReport.effective(field, Live(strip = FieldReport.Strip.TOOLBAR, wordsOffered = true)).stripCovered)
        assertTrue(FieldReport.effective(field, Live(strip = FieldReport.Strip.PILL, wordsOffered = false)).stripCovered)
        assertFalse(FieldReport.effective(field, Live(strip = FieldReport.Strip.TOOLBAR, wordsOffered = false)).stripCovered)
    }

    @Test fun firefoxsWorkaroundIsTheKeyboardsNotTheApps() {
        // AppWorkarounds: IME_MULTI_LINE + AUTO_CORRECT in Firefox become NO_SUGGESTIONS + WEB_EDIT_TEXT
        val raw = text or FieldReport.FLAG_IME_MULTI_LINE or FieldReport.FLAG_AUTO_CORRECT or FieldReport.FLAG_CAP_SENTENCES
        val field = Snapshot.Field(inputType = raw or FieldReport.FLAG_NO_SUGGESTIONS or FieldReport.VARIATION_WEB_EDIT_TEXT,
            rawInputType = raw, appSuggestions = false, suggestionsShown = false, lookups = false)
        assertTrue(field.workaround)
        assertTrue(field.workaroundForbidsSuggestions)
        assertTrue(field.appAllowsSuggestions)
        assertEquals(Finding.WORKAROUND_NO_SUGGESTIONS, verdict(field).finding)
        // the app's own flag in a web field is the app's; "Immer Vorschläge anzeigen" leaves web fields out with its exception
        val own = field.copy(rawInputType = field.inputType)
        assertFalse(own.workaround)
        assertTrue(own.webText)
        assertEquals(Finding.APP_NO_SUGGESTIONS_WEB, verdict(own).finding)
        assertEquals(Finding.APP_NO_SUGGESTIONS_WEB, verdict(own.copy(alwaysShowSetting = true)).finding)
        // without the exception, or outside web fields, the setting itself is the way out
        assertEquals(Finding.APP_NO_SUGGESTIONS, verdict(own.copy(alwaysShowExceptWeb = false)).finding)
        val plain = text or FieldReport.FLAG_NO_SUGGESTIONS
        assertEquals(Finding.APP_NO_SUGGESTIONS, verdict(own.copy(inputType = plain, rawInputType = plain)).finding)
    }

    @Test fun fieldsThatAreNoText() {
        assertEquals(Finding.NO_TEXT_FIELD, verdict(Snapshot.Field(inputType = 0)).finding)
        assertEquals(FieldType.NONE, FieldReport.fieldType(0, 0))
        for ((type, expected) in listOf(FieldReport.TYPE_CLASS_NUMBER to FieldType.NUMBER, FieldReport.TYPE_CLASS_PHONE to FieldType.PHONE,
            FieldReport.TYPE_CLASS_DATETIME to FieldType.DATETIME)) {
            assertEquals(expected, FieldReport.fieldType(type, 0))
            assertEquals(Finding.NOT_TEXT, verdict(Snapshot.Field(inputType = type)).finding)
        }
        assertEquals(listOf(Flag.SIGNED, Flag.DECIMAL),
            FieldReport.flags(FieldReport.TYPE_CLASS_NUMBER or FieldReport.NUMBER_FLAG_SIGNED or FieldReport.NUMBER_FLAG_DECIMAL, 0))
    }

    @Test fun variationsAndFlagsHaveTheirNames() {
        assertEquals(FieldType.SHORT_MESSAGE, FieldReport.fieldType(text or FieldReport.VARIATION_SHORT_MESSAGE, 0))
        assertEquals(FieldType.WEB_TEXT, FieldReport.fieldType(text or FieldReport.VARIATION_WEB_EDIT_TEXT, 0))
        assertEquals(FieldType.SEARCH, FieldReport.fieldType(text or FieldReport.VARIATION_WEB_EDIT_TEXT, FieldReport.IME_ACTION_SEARCH))
        // a URL field with the search action stays a URL field (the browser's address bar)
        assertEquals(FieldType.URL, FieldReport.fieldType(text or FieldReport.VARIATION_URI, FieldReport.IME_ACTION_SEARCH))
        assertEquals(listOf(Flag.MULTI_LINE, Flag.AUTO_CORRECT, Flag.FORCE_ASCII),
            FieldReport.flags(text or FieldReport.FLAG_MULTI_LINE or FieldReport.FLAG_AUTO_CORRECT or FieldReport.FLAG_CAP_SENTENCES,
                FieldReport.IME_FLAG_FORCE_ASCII))
    }

    @Test fun aSnapshotHoldsNoTextOfTheField() {
        // only the package, numbers, the language tag and booleans: no field for hint, label, field name or content
        val strings = Snapshot.Field::class.java.declaredFields.filter { it.type == String::class.java }.map { it.name }.toSet()
        assertEquals(setOf("packageName", "languageTag"), strings)
        assertTrue(Snapshot.Field::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .all { it.type == String::class.java || it.type.isPrimitive }, "only strings named above and primitives")
        // a field without learning keeps no package: the same class, the flag says so (stage 61); nothing else about the app
        assertTrue(Snapshot.Field::class.java.declaredFields.map { it.name }.containsAll(listOf("noLearning", "incognito", "forced", "appForced")))
        assertTrue(Snapshot.Locked::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.isEmpty())
    }
}
