// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import helium314.keyboard.agent.commands.EmojiMixer
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
        (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("x", secrets[0]))
        SymbolDrawing.saveOwnSymbols(app, emptyList())
    }

    @After fun tearDown() {
        HapticChain.deviceForTest = null
        EmojiMixer.resetForTest()
        SubtypeSwitchLog.clear()
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

    @Test fun aRefusingNetworkIsShownAsNotReachable() {
        EmojiMixer.calls = { OkHttpClient.Builder().addInterceptor { throw java.io.IOException("blocked") }.build() }
        page()
        compose.onNodeWithTag("agent_diagnose_gstatic").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("nicht erreichbar: Netzwerkfehler", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun theSettingsListLeadsToThePage() {
        compose.setContent { Theme(true) { helium314.keyboard.settings.screens.MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            onClickAgentDiagnose = { opened = true }) } }
        compose.waitForIdle()
        compose.onNode(hasText("Diagnose")).performScrollTo().performClick()
        assertTrue(opened)
    }

    private var opened = false
}
