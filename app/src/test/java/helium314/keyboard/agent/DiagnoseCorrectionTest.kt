// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentDiagnoseScreen
import helium314.keyboard.settings.screens.TypingAutocorrect
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

/** Stage 90a: the Diagnose card "Korrektur & Sprache": settings and counts, never a word of the typing history or of a field. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h2000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class DiagnoseCorrectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val none = Diagnose.CorrectionCounts(emptyMap(), 0, 0, 0)

    @Before fun setUp() { app.prefs().edit { clear() }; LastField.clear() }
    @After fun tearDown() { app.prefs().edit { clear() }; LastField.clear() }

    private fun rows(counts: Diagnose.CorrectionCounts = none) = Diagnose.correction(app, counts).rows.associate { it.label to it.value }

    @Test fun showsTheLevelAndTheSettingsThatDecideTheCorrection() {
        // untouched settings: autocorrect on, strength 0.24, "never change correct words" off: no level matches exactly
        var r = rows()
        assertEquals("eigene Einstellung (keine Stufe passt genau)", r["Korrekturstufe"])
        assertEquals("0.24", r["Korrekturstärke (Schwelle)"])
        assertEquals("aus", r["Richtige Wörter nie ändern"])
        assertEquals("aus", r["Personalisierte Vorschläge"])
        assertEquals("aus", r["Meine Tippfehler lernen"])
        assertEquals("aus", r["Trefferzonen an mich anpassen"])
        assertEquals("an", r["Folgewort-Vorschläge"])
        TypingAutocorrect.apply(app.prefs(), TypingAutocorrect.Level.GENTLE)
        r = rows()
        assertEquals("Behutsam", r["Korrekturstufe"])
        assertEquals("an", r["Richtige Wörter nie ändern"])
        TypingAutocorrect.apply(app.prefs(), TypingAutocorrect.Level.BOLD)
        assertEquals("Mutig", rows()["Korrekturstufe"])
        TypingAutocorrect.apply(app.prefs(), TypingAutocorrect.Level.OFF)
        assertEquals("Aus", rows()["Korrekturstufe"])
    }

    @Test fun showsTheLearningSwitchesAndTheLayout() {
        app.prefs().edit {
            putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
            putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, true)
            putBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, true)
            putBoolean(AgentPreferences.MEMORY, true)
            putBoolean(AgentPreferences.ADAPT_TOUCH, true)
            putBoolean(Settings.PREF_BIGRAM_PREDICTIONS, false)
        }
        val r = rows()
        assertEquals("an", r["Personalisierte Vorschläge"])
        assertEquals("an", r["Meine Tippfehler lernen"])
        assertEquals("an", r["Rückgängig gemachte Korrekturen merken"])
        assertEquals("an", r["Merken, wie ich schreibe (Gedächtnis)"])
        assertEquals("an", r["Trefferzonen an mich anpassen"])
        assertEquals("aus", r["Folgewort-Vorschläge"])
        assertTrue(r["Aktives Layout"]!!.isNotBlank(), "layout row: $r")
        assertTrue(r.keys.any { it.startsWith("Zweite Sprachen") }, "second languages are listed per active language: ${r.keys}")
    }

    @Test fun showsOnlyCountsAndNoWordOfTheLearnedData() {
        val typo = "GEHEIMTIPPFEHLER"; val meant = "GEHEIMZIELWORT"; val history = "GEHEIMVERLAUFSWORT"
        app.prefs().edit { putBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, true) }
        val settings = Settings.getValues()
        repeat(3) { TypoMemory.confirm(app, typo, meant, null, settings) }
        val counts = Diagnose.CorrectionCounts(mapOf("de" to 12, "en" to 3), TypoMemory.all(app).size, 2, 5)
        assertEquals(1, counts.typoPairs)
        val card = Diagnose.correction(app, counts)
        val r = card.rows.associate { it.label to it.value }
        assertEquals("de: 12, en: 3", r["Gelernte Wörter (Anzahl je Sprache)"])
        assertEquals("1", r["Gelernte Tippfehler-Paare (Anzahl)"])
        assertEquals("2", r["Im Feld gelernte Korrekturen (Anzahl)"])
        assertEquals("5", r["Zurückgenommene Korrekturen (Anzahl)"])
        val text = Diagnose.text(app, listOf(card))
        for (secret in listOf(typo, meant, history)) assertFalse(secret in text, "the card shows $secret")
        // unreadable data is "unbekannt", not a made-up zero
        val unknown = rows(Diagnose.CorrectionCounts(null, null, null, null))
        assertEquals("unbekannt", unknown["Gelernte Wörter (Anzahl je Sprache)"])
        assertEquals("unbekannt", unknown["Gelernte Tippfehler-Paare (Anzahl)"])
    }

    @Test fun theCardIsOnThePageAndInTheCopiedText() {
        compose.setContent { Theme(true) { AgentDiagnoseScreen {} } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Korrekturstufe", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Richtige Wörter nie ändern", substring = true)).performScrollTo().assertExists()
        assertTrue("Korrektur & Sprache" in Diagnose.text(app, listOf(Diagnose.correction(app, none))))
    }
}
