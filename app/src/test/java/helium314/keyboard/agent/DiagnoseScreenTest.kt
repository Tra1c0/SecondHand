// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentDiagnoseScreen
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage 30, Einstellungen → Diagnose: the values a phone reports, no contents, the copy button, the
 * tick chain test, and the gstatic check that asks the network exactly once and only on a tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h2000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class DiagnoseScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()

    private class FakeVibrator : HapticChain.Device {
        override val sdk = 30
        override val hasVibrator = true
        override val hasAmplitudeControl = true
        val played = mutableListOf<HapticChain.Plan>()
        override fun supportsPrimitive(primitive: Int) = primitive == HapticChain.PRIMITIVE_TICK || primitive == HapticChain.PRIMITIVE_CLICK
        override fun play(plan: HapticChain.Plan) { played += plan }
    }

    private val vibrator = FakeVibrator()
    private val requests = mutableListOf<Request>()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(2000)
    private val http = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
        val request = chain.request()
        synchronized(requests) { requests += request }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("ok")
            .body(png.toResponseBody("image/png".toMediaType())).build()
    }.build()

    /** Contents the page must never show or copy. */
    private val secrets = listOf("GEHEIM-ZWISCHENABLAGE", "sk-GEHEIMER-SCHLUESSEL", "com.example.geheim", "Geheimsymbol")

    @Before fun setUp() {
        app.prefs().edit().clear().putBoolean(AgentPreferences.SYMBOL_DRAWING, true)
            .putString("agent_test_marker", secrets[1]).commit()
        HapticChain.deviceForTest = vibrator
        EmojiMixer.resetForTest()
        EmojiMixer.calls = { http }
        SubtypeSwitchLog.clear()
        LastField.clear() // stage 55: no field of another test class
        (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("x", secrets[0]))
        SymbolDrawing.saveOwnSymbols(app, emptyList())
    }

    @After fun tearDown() {
        HapticChain.deviceForTest = null
        EmojiMixer.resetForTest()
        SubtypeSwitchLog.clear()
        LastField.clear()
        app.prefs().edit().clear().commit()
    }

    private fun page() {
        compose.setContent { Theme(true) { AgentDiagnoseScreen {} } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Paare", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun row(text: String) = compose.onNode(hasText(text, substring = true)).performScrollTo().assertExists()

    @Test fun showsWhatThePhoneDoesWithoutAnyContent() {
        // a language switch by command, as LatinIME reports it (the package of the app is never passed)
        val german = SubtypeSettings.getResourceSubtypesForLocale("de".constructLocale()).first()
        SubtypeSwitchLog.expectCommand(german)
        SubtypeSwitchLog.internalSwitch(german)
        SubtypeSwitchLog.onSwitched(german)
        page()
        // haptics: the tier this vibrator gets, its primitives, and the key vibration setting
        row("Primitive (tick)") // a detent tick
        row("Wellenform mit regelbarer Stärke") // the last detent: this vibrator lacks quick_rise/thud
        row("click, tick")
        row("Tastenvibration (die Rastpunkte")
        // drawn symbols
        row("Gesten-Bibliothek")
        row("Hauptwörterbuch")
        row("Gestenerkennung")
        // the last language switch with its reason
        row("Sprachbefehl (@deutsch …) → de")
        // Emoji-Mix: the real table, nothing loaded
        row("147000 Paare, 619 Emojis")
        row("nicht geprüft")
        // RGB lighting cost: nothing measured in this test (the keyboard never drew with light)
        row("Gemessene Bilder"); row("keine – Tastatur mit RGB öffnen")
        // app and phone: versions, no identifiers
        row("App-Version"); row("API 28")
        assertEquals(emptyList(), requests, "the page itself asks nobody")
        // nothing secret anywhere on the page
        for (secret in secrets) compose.onAllNodes(hasText(secret, substring = true)).assertCountEquals(0)
        // no screenshot protection: the page is meant to be passed on
        assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Test fun copyPutsExactlyTheShownValuesOnTheClipboard() {
        page()
        compose.onNodeWithTag("agent_diagnose_copy").performScrollTo().performClick()
        compose.waitForIdle()
        val clip = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
        val copied = clip.getItemAt(0).text.toString()
        assertTrue(copied.startsWith("Agent Keyboard – Diagnose"), copied)
        for (line in listOf("Vibrationsmotor: ja", "Stärke regelbar (Amplitude): ja", "Rastpunkt spielt: Primitive (tick)", "Letzter Rastpunkt spielt: Wellenform mit regelbarer Stärke",
            "Symbole malen: an", "Letzter Wechsel: keiner", "Paartabelle: 147000 Paare, 619 Emojis", "www.gstatic.com: nicht geprüft",
            "Android: "))
            assertTrue(line in copied, "$line in\n$copied")
        // every copied value is on the page
        for (value in copied.lines().drop(1).filter { ": " in it }.map { it.substringAfter(": ") })
            compose.onAllNodes(hasText(value, substring = false)).onFirst().assertExists()
        for (secret in secrets) assertFalse(secret in copied, secret)
        assertEquals(emptyList(), requests)
    }

    @Test fun theTestButtonPlaysTheChainOnce() {
        page()
        assertTrue(vibrator.played.isEmpty())
        compose.onNodeWithTag("agent_diagnose_test").performScrollTo().performClick()
        compose.waitForIdle()
        // exactly once, the plan this vibrator gets (primitives, as shown on the page)
        assertEquals(1, vibrator.played.size)
        assertEquals(Diagnose.chain(vibrator)!!.plan, vibrator.played.single())
        assertEquals(Diagnose.Tier.PRIMITIVES, Diagnose.chain(vibrator)!!.tier)
        assertEquals(Diagnose.Tier.WAVEFORM, Diagnose.chain(vibrator)!!.stretchTier) // no quick_rise/thud here
    }

    @Test fun gstaticIsAskedExactlyOnceAndOnlyOnATap() {
        page()
        Thread.sleep(200)
        compose.waitForIdle()
        assertEquals(emptyList(), requests, "nothing without a tap")
        compose.onNodeWithTag("agent_diagnose_gstatic").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("erreichbar, Testbild geladen", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val request = synchronized(requests) { requests.single() }
        assertEquals("www.gstatic.com", request.url.host)
        assertEquals("GET", request.method)
        assertTrue(request.url.encodedPath.startsWith("/android/keyboard/emojikitchen/"), request.url.toString())
        assertEquals(null, request.header("Cookie"))
        assertEquals(null, request.header("Authorization"))
        // the image is not kept
        assertEquals(0, EmojiMixer.cache(app).directory.listFiles()?.size ?: 0)
        compose.waitForIdle()
        assertEquals(1, requests.size)
    }

    @Test fun theSectionsAreBuiltOnceAndTheDictionaryFilesListedOffTheMainThread() {
        // review N4: recompositions (here the gstatic check: running, then its result) build nothing again
        val lookups = Diagnose.dictionaryLookups
        page()
        row("Hauptwörterbuch")
        compose.onNodeWithTag("agent_diagnose_gstatic").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("erreichbar, Testbild geladen", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, Diagnose.dictionaryLookups - lookups)
        assertFalse(Diagnose.dictionaryLookupOnMainThread)
        row("Hauptwörterbuch")
    }

    @Test fun aRefusingNetworkIsShownAsNotReachable() {
        EmojiMixer.calls = { OkHttpClient.Builder().addInterceptor { throw java.io.IOException("blocked") }.build() }
        page()
        compose.onNodeWithTag("agent_diagnose_gstatic").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("nicht erreichbar: Netzwerkfehler", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    // stage 38a: the start page leads to the page with its footer pill "Diagnose" (and System & Daten, SettingsStartScreenTest)
    @Test fun theSettingsListLeadsToThePage() {
        var route = ""
        compose.setContent { Theme(true) { helium314.keyboard.settings.screens.MainSettingsScreen(onNavigate = { route = it }, onClickBack = {}) } }
        compose.waitForIdle()
        compose.onNode(hasText("Diagnose")).performScrollTo().performClick()
        assertEquals(helium314.keyboard.settings.SettingsDestination.AgentDiagnose, route)
    }

    // --- stage 55: the card "Letztes Feld" ---

    /** Texts of the field the card must never show or copy. */
    private val fieldTexts = listOf("GEHEIMER-HINWEIS", "GEHEIMES-LABEL", "geheimes_feld")

    /** As LatinIME does when the keyboard starts in [editor]: settings loaded for the field, then the snapshot. */
    private fun startIn(editor: EditorInfo) {
        Settings.getInstance().loadSettings(app, java.util.Locale.GERMAN, InputAttributes(editor, false, app.packageName))
        LastField.capture(app, editor, Settings.getValues(), null)
    }

    private fun field(inputType: Int, imeOptions: Int = 0) = EditorInfo().apply {
        this.inputType = inputType
        this.imeOptions = imeOptions
        packageName = "com.example.suche"
        hintText = fieldTexts[0]
        label = fieldTexts[1]
        fieldName = fieldTexts[2]
    }

    private fun exact(text: String) = compose.onNode(hasText(text, substring = false)).performScrollTo().assertExists()

    @Test fun theLastFieldShowsWhatTheEngineUsedAndNoTextOfTheField() {
        // a single-line search field: suggestions yes, capitals at sentence starts, no autocorrection (the app does not ask)
        startIn(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_SEARCH))
        page()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Urteil")).fetchSemanticsNodes().isNotEmpty() }
        exact("com.example.suche")
        exact("Suche")
        exact("0x00004001") // inputType (no workaround: the app's and the engine's are the same)
        exact("0x00000003") // imeOptions
        exact("Satzanfänge")
        exact("Deutsch (de)")
        exact("Vorschläge an, Autokorrektur aus, Großschreibung an")
        exact("Keine Autokorrektur: Die App verlangt sie in diesem Feld nicht, und „Mehr Autokorrektur“ ist aus.")
        for (text in fieldTexts) compose.onAllNodes(hasText(text, substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("agent_diagnose_copy").performScrollTo().performClick()
        compose.waitForIdle()
        val copied = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
        for (line in listOf("Letztes Feld", "App: com.example.suche", "Feldtyp: Suche", "App erlaubt Vorschläge: ja",
            "App verlangt Großschreibung: Satzanfänge", "App wünscht Autokorrektur: nein", "Wörterbuch der Sprache: vorhanden",
            "Urteil: Keine Autokorrektur: Die App verlangt sie"))
            assertTrue(line in copied, "$line in\n$copied")
        for (text in fieldTexts) assertFalse(text in copied, text)
        // review N1: verdict and result first; N2: no rows that are always off; N6: the clip is marked sensitive
        val card = copied.substringAfter("\n\nLetztes Feld\n").lines()
        assertTrue(card[0].startsWith("Urteil: "), card[0])
        assertTrue(card[1].startsWith("Ergebnis: "), card[1])
        for (gone in listOf("Inkognito:", "Gerät gesperrt:", "Sensibles Feld")) assertFalse(gone in copied, gone)
        assertTrue("„Immer Vorschläge anzeigen“: aus" in copied, copied)
        val clip = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
        // meant to be passed on, no field text: not marked, so the clipboard guard does not file it in the vault (lead)
        assertNotEquals(true, clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE"))
        assertTrue("Letztes Feld" in clip.getItemAt(0).text.toString())
    }

    @Test fun firefoxsWorkaroundIsNamedWithBothNumbers() {
        // review H1: the web field asks for autocorrection; HeliBoard itself adds NO_SUGGESTIONS and WEB_EDIT_TEXT
        val raw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        startIn(field(raw).apply { packageName = "org.mozilla.firefox" })
        page()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Urteil")).fetchSemanticsNodes().isNotEmpty() }
        exact("Keine Vorschläge: Die Tastatur schaltet sie in Webfeldern von Firefox selbst ab (HeliBoard-Workaround); " +
            "ändern lässt sich das nur mit „Immer Vorschläge anzeigen“ an und „Nicht immer Vorschläge für Web-Editierfelder anzeigen“ aus.")
        exact("0x0004c001") // what Firefox sent
        exact("0x000cc0a1") // what the engine uses
        exact("Webfeld in Firefox: HeliBoard ändert den Feldtyp")
        compose.onAllNodes(hasText("Die App verbietet", substring = true)).assertCountEquals(0)
    }

    @Test fun whatCoveredTheStripIsNotAllOn() {
        // review M3: a field with everything on, but the expanded toolbar lay over the words at the last change
        startIn(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        LastField.onStripView(newContent = true, wordsOffered = true, external = false, toolbar = false, emojis = false)
        LastField.onStripView(newContent = false, wordsOffered = true, external = false, toolbar = true, emojis = false)
        page()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Urteil")).fetchSemanticsNodes().isNotEmpty() }
        exact("Vorschläge verdeckt: Zuletzt lag die ausgeklappte Symbolleiste über den Vorschlägen.")
        exact("Vorschläge an, Autokorrektur an, Großschreibung an; Leiste zuletzt verdeckt: Symbolleiste")
        compose.onAllNodes(hasText("Alles an.")).assertCountEquals(0)
        exact("ob hinter Chips oder Pillen Wörter bereitlagen")
    }

    @Test fun aDictionaryCheckThatFailsIsUnknownNotMissing() {
        // review N4
        val lookup = Diagnose.mainDictionaryLookup
        Diagnose.mainDictionaryLookup = { _, _ -> throw SecurityException("no files") }
        try {
            startIn(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
            val section = Diagnose.lastField(app, android.os.SystemClock.elapsedRealtime())
            val rows = section.rows.associate { it.label to it.value }
            assertEquals("unbekannt", rows["Wörterbuch der Sprache"])
            assertFalse(rows.getValue("Urteil").contains("Wörterbuch"), rows.getValue("Urteil"))
        } finally {
            Diagnose.mainDictionaryLookup = lookup
        }
    }

    @Test fun aFieldThatForbidsSuggestionsNamesTheApp() {
        startIn(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        page()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Urteil")).fetchSemanticsNodes().isNotEmpty() }
        exact("mehrzeilig, keine Vorschläge")
        exact("Keine Vorschläge: Die App verbietet sie in diesem Feld (damit auch keine Autokorrektur); „Immer Vorschläge anzeigen“ setzt sich darüber hinweg.")
        exact("Vorschläge aus, Autokorrektur aus, Großschreibung aus")
    }

    @Test fun aPasswordFieldLeavesOnlyPasswortfeld() {
        startIn(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        // the password field replaces the field before: its app is gone too
        startIn(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(dev.agentkeyboard.core.FieldReport.Snapshot.Password, LastField.current(app))
        page()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Urteil")).fetchSemanticsNodes().isNotEmpty() }
        exact("Passwortfeld")
        exact("Passwortfeld: Vorschläge, Autokorrektur und Lernen sind hier immer aus.")
        compose.onAllNodes(hasText("com.example", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("Feldtyp")).assertCountEquals(0)
        for (text in fieldTexts) compose.onAllNodes(hasText(text, substring = true)).assertCountEquals(0)
    }

    @Test fun incognitoAndALockedDeviceRememberNothing() {
        startIn(field(InputType.TYPE_CLASS_TEXT))
        app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        startIn(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertEquals(dev.agentkeyboard.core.FieldReport.Snapshot.Hidden(locked = false), LastField.current(app))
        // an app's own incognito (no personalized learning) as well
        app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        startIn(field(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertEquals(dev.agentkeyboard.core.FieldReport.Snapshot.Hidden(locked = false), LastField.current(app))
        val keyguard = app.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        org.robolectric.Shadows.shadowOf(keyguard).setIsDeviceLocked(true)
        try {
            startIn(field(InputType.TYPE_CLASS_TEXT))
            assertEquals(dev.agentkeyboard.core.FieldReport.Snapshot.Hidden(locked = true), LastField.current(app))
            page()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Urteil")).fetchSemanticsNodes().isNotEmpty() }
            exact("Inkognito/gesperrt: nichts gemerkt")
            exact("Gesperrt: nichts gemerkt; bei gesperrtem Gerät zeigt die Tastatur keine Vorschläge.")
            compose.onAllNodes(hasText("com.example", substring = true)).assertCountEquals(0)
        } finally {
            org.robolectric.Shadows.shadowOf(keyguard).setIsDeviceLocked(false)
        }
    }

    @Test fun withoutAFieldTheCardSaysSo() {
        page()
        exact("noch keins, seit die Tastatur läuft (nur im Speicher)")
    }
}
