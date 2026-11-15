// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.FieldReport
import dev.agentkeyboard.core.FieldReport.Finding
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.settings.Settings
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 55: the snapshot of the card "Letztes Feld" from a real EditorInfo and the SettingsValues
 * HeliBoard computes for it (the engine path through LatinIME: InputLogicTest.lastField*).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LastFieldTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        app.prefs().edit().clear().commit()
        LastField.clear()
    }

    @After fun tearDown() {
        LastField.clear()
        app.prefs().edit().clear().commit()
    }

    private fun editor(inputType: Int, imeOptions: Int = 0, packageName: String = "com.example.chat") = EditorInfo().apply {
        this.inputType = inputType
        this.imeOptions = imeOptions
        this.packageName = packageName
    }

    private fun load(editor: EditorInfo, locale: Locale = Locale.GERMAN) =
        Settings.getInstance().loadSettings(app, locale, InputAttributes(editor, false, app.packageName))

    private fun start(editor: EditorInfo, locale: Locale = Locale.GERMAN): FieldReport.Snapshot? {
        load(editor, locale)
        LastField.capture(app, editor, Settings.getValues(), null)
        return LastField.current(app)
    }

    @Test fun theCoreKnowsThePlatformsNumbers() {
        assertEquals(InputType.TYPE_MASK_CLASS, FieldReport.TYPE_MASK_CLASS)
        assertEquals(InputType.TYPE_MASK_VARIATION, FieldReport.TYPE_MASK_VARIATION)
        assertEquals(InputType.TYPE_CLASS_TEXT, FieldReport.TYPE_CLASS_TEXT)
        assertEquals(InputType.TYPE_CLASS_NUMBER, FieldReport.TYPE_CLASS_NUMBER)
        assertEquals(InputType.TYPE_CLASS_PHONE, FieldReport.TYPE_CLASS_PHONE)
        assertEquals(InputType.TYPE_CLASS_DATETIME, FieldReport.TYPE_CLASS_DATETIME)
        assertEquals(InputType.TYPE_TEXT_VARIATION_URI, FieldReport.VARIATION_URI)
        assertEquals(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, FieldReport.VARIATION_EMAIL_ADDRESS)
        assertEquals(InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT, FieldReport.VARIATION_EMAIL_SUBJECT)
        assertEquals(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, FieldReport.VARIATION_SHORT_MESSAGE)
        assertEquals(InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, FieldReport.VARIATION_LONG_MESSAGE)
        assertEquals(InputType.TYPE_TEXT_VARIATION_PERSON_NAME, FieldReport.VARIATION_PERSON_NAME)
        assertEquals(InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS, FieldReport.VARIATION_POSTAL_ADDRESS)
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, FieldReport.VARIATION_PASSWORD)
        assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, FieldReport.VARIATION_VISIBLE_PASSWORD)
        assertEquals(InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT, FieldReport.VARIATION_WEB_EDIT_TEXT)
        assertEquals(InputType.TYPE_TEXT_VARIATION_FILTER, FieldReport.VARIATION_FILTER)
        assertEquals(InputType.TYPE_TEXT_VARIATION_PHONETIC, FieldReport.VARIATION_PHONETIC)
        assertEquals(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, FieldReport.VARIATION_WEB_EMAIL_ADDRESS)
        assertEquals(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, FieldReport.VARIATION_WEB_PASSWORD)
        assertEquals(InputType.TYPE_NUMBER_VARIATION_PASSWORD, FieldReport.NUMBER_VARIATION_PASSWORD)
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, FieldReport.FLAG_CAP_CHARACTERS)
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_WORDS, FieldReport.FLAG_CAP_WORDS)
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, FieldReport.FLAG_CAP_SENTENCES)
        assertEquals(InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, FieldReport.FLAG_AUTO_CORRECT)
        assertEquals(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE, FieldReport.FLAG_AUTO_COMPLETE)
        assertEquals(InputType.TYPE_TEXT_FLAG_MULTI_LINE, FieldReport.FLAG_MULTI_LINE)
        assertEquals(InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE, FieldReport.FLAG_IME_MULTI_LINE)
        assertEquals(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, FieldReport.FLAG_NO_SUGGESTIONS)
        assertEquals(InputType.TYPE_NUMBER_FLAG_SIGNED, FieldReport.NUMBER_FLAG_SIGNED)
        assertEquals(InputType.TYPE_NUMBER_FLAG_DECIMAL, FieldReport.NUMBER_FLAG_DECIMAL)
        assertEquals(EditorInfo.IME_MASK_ACTION, FieldReport.IME_MASK_ACTION)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, FieldReport.IME_ACTION_SEARCH)
        assertEquals(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, FieldReport.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(EditorInfo.IME_FLAG_FORCE_ASCII, FieldReport.IME_FLAG_FORCE_ASCII)
    }

    @Test fun aMessageFieldWithEverythingOn() {
        val field = assertIs<FieldReport.Snapshot.Field>(start(editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)))
        // exactly what SettingsValues says
        val values = Settings.getValues()
        assertEquals("com.example.chat", field.packageName)
        assertEquals(values.mSuggestionsEnabled, field.suggestionsShown)
        assertEquals(values.mAutoCorrectEnabled, field.autoCorrectEnabled)
        assertEquals(values.needsToLookupSuggestions(), field.lookups)
        assertEquals(values.mAutoCap, field.autoCap)
        assertEquals(values.mInputAttributes.mShouldShowSuggestions, field.appSuggestions)
        assertEquals(values.mInputAttributes.mInputTypeShouldAutoCorrect, field.appAutoCorrect)
        assertEquals("de", field.languageTag)
        assertEquals(Finding.ALL_ON, FieldReport.verdict(field).finding)
        assertEquals(FieldReport.FieldType.SHORT_MESSAGE, field.type)
    }

    @Test fun realFieldsGetTheirReason() {
        fun finding(inputType: Int, imeOptions: Int = 0) =
            FieldReport.verdict(start(editor(inputType, imeOptions))!!).finding
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(Finding.APP_NO_SUGGESTIONS, finding(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertEquals(Finding.AUTOCORRECT_ADDRESS, finding(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(Finding.AUTOCORRECT_ADDRESS, finding(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(Finding.AUTOCORRECT_APP, finding(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertEquals(Finding.CAPS_APP, finding(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertEquals(Finding.NOT_TEXT, finding(InputType.TYPE_CLASS_NUMBER))
        assertEquals(Finding.NO_TEXT_FIELD, finding(InputType.TYPE_NULL))
        assertEquals(Finding.PASSWORD, finding(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(Finding.PASSWORD, finding(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        // "Mehr Autokorrektur" corrects the single-line field
        app.prefs().edit().putBoolean(Settings.PREF_MORE_AUTO_CORRECTION, true).commit()
        assertEquals(Finding.ALL_ON, finding(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        // the keyboard's own settings
        app.prefs().edit().putBoolean(Settings.PREF_AUTO_CORRECTION, false).commit()
        assertEquals(Finding.AUTOCORRECT_SETTING_OFF, finding(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        app.prefs().edit().putBoolean(Settings.PREF_SHOW_SUGGESTIONS, false).commit()
        assertEquals(Finding.SUGGESTIONS_SETTING_OFF, finding(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        app.prefs().edit().putBoolean(Settings.PREF_SHOW_SUGGESTIONS, true).putBoolean(Settings.PREF_AUTO_CORRECTION, true)
            .putBoolean(Settings.PREF_AUTO_CAP, false).commit()
        assertEquals(Finding.CAPS_SETTING_OFF, finding(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        // the learning pause is shown, it switches nothing of the three off
        app.prefs().edit().putBoolean(Settings.PREF_AUTO_CAP, true).putBoolean(AgentPreferences.LEARNING_PAUSED, true).commit()
        val paused = assertIs<FieldReport.Snapshot.Field>(start(editor(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)))
        assertTrue(paused.learningPaused)
        assertEquals(Finding.ALL_ON, FieldReport.verdict(paused).finding)
    }

    @Test fun aLanguageSwitchInTheFieldTakesTheReloadedSettings() {
        val message = editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        start(message)
        // the engine reloads its settings for the same field after onCurrentInputMethodSubtypeChanged
        LastField.onSubtypeSwitched()
        load(message, Locale.forLanguageTag("es"))
        assertEquals("es", assertIs<FieldReport.Snapshot.Field>(LastField.current(app)).languageTag)
        // settings loaded for another field (another app) say nothing about the last one
        start(message)
        LastField.onSubtypeSwitched()
        load(editor(InputType.TYPE_CLASS_TEXT, packageName = "com.example.other"), Locale.forLanguageTag("es"))
        assertEquals("de", assertIs<FieldReport.Snapshot.Field>(LastField.current(app)).languageTag)
        // without a switch a reload (a changed setting) leaves the snapshot as the field had it
        start(message)
        load(message, Locale.forLanguageTag("es"))
        assertEquals("de", assertIs<FieldReport.Snapshot.Field>(LastField.current(app)).languageTag)
    }

    @Test fun theStripCountsChipsAndTheCommandOfTheField() {
        start(editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        LastField.onStrip(chips = false, insideCommand = false)
        LastField.onStrip(chips = true, insideCommand = true)
        val live = LastField.live("de", dictionaryPresent = true)
        assertEquals(2, live.stripUpdates)
        assertEquals(1, live.chipUpdates)
        assertTrue(live.lastStripChips)
        assertTrue(live.inCommand)
        assertNull(live.dictionaryLoaded, "no engine dictionaries here: unknown, no claim")
        // a new field starts counting again
        start(editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertEquals(0, LastField.live("de", null).stripUpdates)
    }

    @Test fun aPasswordOrIncognitoFieldReplacesTheFieldBefore() {
        start(editor(InputType.TYPE_CLASS_TEXT))
        assertEquals(FieldReport.Snapshot.Password, start(editor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
        start(editor(InputType.TYPE_CLASS_TEXT))
        // the keyboard's own fields of "Meine Daten" never learn: incognito as well
        val myData = editor(InputType.TYPE_CLASS_TEXT).apply { privateImeOptions = AgentPreferences.NO_LEARNING_FIELD }
        assertEquals(FieldReport.Snapshot.Hidden(locked = false), start(myData))
        // and nothing about a strip is counted while hidden
        LastField.onStrip(chips = true, insideCommand = true)
        assertEquals(0, LastField.live("de", null).stripUpdates)
    }
}
