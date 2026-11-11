// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Button
import android.widget.TextView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DictationUiTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test fun panelSeparatesListeningReviewAndInsertion() {
        val view = AgentPanelView(app)
        var dictate = 0; var done = 0; var cancelled = 0; var applied = 0
        view.onDictate = { dictate++ }; view.onSendCloud = { done++ }; view.onCancelCloud = { cancelled++ }; view.onApply = { applied++ }
        val dictateButton = view.findViewById<Button>(R.id.agent_dictate)
        val send = view.findViewById<Button>(R.id.agent_cloud_send)
        val cancel = view.findViewById<Button>(R.id.agent_cloud_cancel)
        val apply = view.findViewById<Button>(R.id.agent_apply)
        val copy = view.findViewById<Button>(R.id.agent_copy)
        assertFalse(dictateButton.isEnabled) // hidden panel
        view.showStatus(R.string.agent_panel_select_text, true) // no selection: text actions off, dictation offered
        assertTrue(dictateButton.isEnabled)
        assertFalse(view.findViewById<Button>(R.id.agent_uppercase).isEnabled)
        dictateButton.performClick()
        assertEquals(1, dictate)

        view.showDictationListening("Hallo We")
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("Hallo We", view.findViewById<TextView>(R.id.agent_cloud_confirmation).text.toString())
        assertEquals(app.getString(R.string.agent_dictation_listening), view.findViewById<TextView>(R.id.agent_status).text.toString())
        assertEquals(app.getString(R.string.agent_dictation_done), send.text.toString())
        assertEquals(app.getString(R.string.agent_tool_cancel), cancel.text.toString())
        assertFalse(dictateButton.isEnabled)
        assertFalse(apply.isEnabled) // nothing can be inserted while the microphone is on
        assertFalse(copy.isEnabled)
        assertFalse(view.findViewById<Button>(R.id.agent_cloud_correct).isEnabled)
        send.performClick(); cancel.performClick()
        assertEquals(1, done); assertEquals(1, cancelled); assertEquals(0, applied)

        view.showDictationReview("Hallo Welt")
        assertEquals("Hallo Welt", view.findViewById<TextView>(R.id.agent_result).text.toString())
        assertEquals(View.GONE, send.visibility)
        assertEquals(app.getString(R.string.agent_dictation_insert), apply.text.toString())
        assertTrue(apply.isEnabled); assertTrue(copy.isEnabled); assertTrue(dictateButton.isEnabled)
        assertEquals(0, applied)
        apply.performClick()
        assertEquals(1, applied)

        view.showDictationStatus(R.string.agent_dictation_no_speech)
        assertEquals("", view.findViewById<TextView>(R.id.agent_result).text.toString())
        assertFalse(apply.isEnabled)
        assertEquals(app.getString(R.string.agent_panel_apply), apply.text.toString()) // back to "replace"
        // A later selection result must not inherit dictation labels.
        view.showSource("Hallo")
        view.showResult("HALLO")
        assertEquals(app.getString(R.string.agent_panel_apply), apply.text.toString())
        view.clear()
        assertFalse(dictateButton.isEnabled)
    }

    @Test fun onlyTheOnDeviceRecognizerExistsAndItReportsItsOwnAbsence() {
        // SDK 28 has no on-device recognizer API: unavailable, and a start fails instead of falling back.
        val engine = OnDeviceSpeechEngine(app)
        assertFalse(engine.available)
        val errors = mutableListOf<DictationError>()
        engine.start("de-DE", object : SpeechEngine.Listener {
            override fun onPartial(text: String) = error("no recognition")
            override fun onFinal(text: String) = error("no recognition")
            override fun onError(error: DictationError) { errors.add(error) }
        })
        assertEquals(listOf(DictationError.UNAVAILABLE), errors)
        engine.stop(); engine.cancel() // harmless without a recognizer
        assertEquals(DictationError.PERMISSION, OnDeviceSpeechEngine.map(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(DictationError.NO_SPEECH, OnDeviceSpeechEngine.map(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(DictationError.NO_SPEECH, OnDeviceSpeechEngine.map(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(DictationError.BUSY, OnDeviceSpeechEngine.map(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(DictationError.BUSY, OnDeviceSpeechEngine.map(10))
        assertEquals(DictationError.LANGUAGE, OnDeviceSpeechEngine.map(12))
        assertEquals(DictationError.LANGUAGE, OnDeviceSpeechEngine.map(13))
        assertEquals(DictationError.FAILED, OnDeviceSpeechEngine.map(SpeechRecognizer.ERROR_NETWORK)) // never "retry online"
    }

    @Test fun dictationKeyHasItsOwnCodeAndIsOffByDefault() {
        assertEquals(KeyCode.AGENT_DICTATE, getCodeForToolbarKey(ToolbarKey.DICTATE))
        assertEquals(-12004, KeyCode.AGENT_DICTATE)
        assertNotEquals(KeyCode.VOICE_INPUT, KeyCode.AGENT_DICTATE) // the system voice keyboard key is something else
        assertTrue(helium314.keyboard.latin.utils.defaultToolbarPref.contains("DICTATE:false"))
    }
}
