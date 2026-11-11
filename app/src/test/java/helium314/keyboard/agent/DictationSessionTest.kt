// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import org.junit.Test
import kotlin.test.*

class DictationSessionTest {
    private class FakeEngine : SpeechEngine {
        override var available = true
        var listener: SpeechEngine.Listener? = null
        var language: String? = null
        var starts = 0; var stops = 0; var cancels = 0
        var failOnStart: DictationError? = null
        override fun start(languageTag: String, listener: SpeechEngine.Listener) {
            starts++; language = languageTag; this.listener = listener
            failOnStart?.let { listener.onError(it) }
        }
        override fun stop() { stops++ }
        override fun cancel() { cancels++ }
    }
    private class Ui : DictationSession.Ui {
        val events = mutableListOf<String>()
        override fun listening(partial: String) { events.add("listening:$partial") }
        override fun review(text: String) { events.add("review:$text") }
        override fun failed(error: DictationError) { events.add("failed:$error") }
        override fun denied(reason: DenialReason) { events.add("denied:$reason") }
    }

    private val policy = ExecutionPolicy()
    private val editor = EditorSession().apply { start() }
    private var access = EditorAccess(isText = true)
    private var selection = 5 to 5
    private val inserted = mutableListOf<Pair<Int, String>>()
    private var outcome = ApplyOutcome.SUBMITTED
    private var granted = true
    private val engine = FakeEngine()
    private val ui = Ui()
    private val session = DictationSession({ engine },
        { policy.captureAnchor(editor.token, access, selection.first, selection.second) },
        { anchor -> editor.isCurrent(anchor.token) && policy.editorDenial(access) == null && selection == (anchor.position to anchor.position) },
        { anchor, text -> if (!editor.consume(anchor.token)) ApplyOutcome.REJECTED else { inserted.add(anchor.position to text); outcome } },
        { granted }, { "de-DE" }, ui)

    private fun moveCursor(to: Int) { editor.invalidate(); selection = to to to }

    @Test fun nothingIsRecordedBeforeEditorRecognizerAndMicrophoneAreChecked() {
        selection = 2 to 9
        assertFalse(session.start())
        selection = 5 to 5
        access = access.copy(isPassword = true)
        assertFalse(session.start())
        access = EditorAccess(isText = true, isIncognito = true)
        assertFalse(session.start())
        access = EditorAccess(isText = true)
        engine.available = false
        assertFalse(session.start())
        engine.available = true
        granted = false
        assertFalse(session.start())
        assertEquals(listOf("denied:NO_CURSOR", "denied:PASSWORD", "denied:INCOGNITO", "failed:UNAVAILABLE", "failed:PERMISSION"), ui.events)
        assertEquals(0, engine.starts) // the microphone was never touched
        assertFalse(session.isActive)
    }

    @Test fun heardTextIsOnlyInsertedByAnExplicitClickAndOnlyOnce() {
        assertTrue(session.start())
        assertEquals("de-DE", engine.language)
        assertTrue(session.isListening)
        assertFalse(session.start()) // no second microphone session
        assertEquals(1, engine.starts)
        engine.listener!!.onPartial("Hallo")
        assertNull(session.reviewText)
        session.finish()
        assertEquals(1, engine.stops)
        engine.listener!!.onFinal("  Hallo Welt ")
        assertEquals(listOf("listening:", "listening:Hallo", "review:Hallo Welt"), ui.events)
        assertEquals("Hallo Welt", session.reviewText)
        assertFalse(session.isListening)
        assertTrue(inserted.isEmpty()) // hearing something never inserts it
        assertEquals(ApplyOutcome.SUBMITTED, session.insert())
        assertEquals(listOf(5 to "Hallo Welt"), inserted)
        assertEquals(ApplyOutcome.REJECTED, session.insert()) // double click
        assertEquals(1, inserted.size)
        assertFalse(session.isActive)
        assertNull(session.reviewText)
    }

    @Test fun insertingWhileStillListeningIsRefused() {
        assertTrue(session.start())
        engine.listener!!.onPartial("Hal")
        assertEquals(ApplyOutcome.REJECTED, session.insert())
        assertTrue(session.isListening)
        assertTrue(inserted.isEmpty())
    }

    @Test fun editorChangeWhileListeningStopsTheMicrophoneAndIgnoresLateResults() {
        assertTrue(session.start())
        assertFalse(session.editorChanged()) // nothing changed yet
        moveCursor(7)
        assertTrue(session.editorChanged())
        assertEquals(1, engine.cancels)
        assertFalse(session.isActive)
        engine.listener!!.onPartial("late")
        engine.listener!!.onFinal("late text")
        assertEquals(listOf("listening:"), ui.events)
        assertEquals(ApplyOutcome.REJECTED, session.insert())
        assertTrue(inserted.isEmpty())
    }

    @Test fun editorChangeDuringReviewDiscardsTheText() {
        assertTrue(session.start())
        engine.listener!!.onFinal("Hallo")
        access = access.copy(isIncognito = true) // privacy state changed while the text was on screen
        assertTrue(session.editorChanged())
        assertNull(session.reviewText)
        assertEquals(0, engine.cancels) // the microphone was already off
        access = EditorAccess(isText = true)
        assertEquals(ApplyOutcome.REJECTED, session.insert())
        assertTrue(inserted.isEmpty())
    }

    @Test fun textThatArrivesAfterTheCursorMovedIsNeverOffered() {
        assertTrue(session.start())
        moveCursor(1) // the host did not report it to the session yet
        engine.listener!!.onFinal("Hallo")
        assertEquals(listOf("listening:", "denied:STALE_CONTEXT"), ui.events)
        assertFalse(session.isActive)
        assertNull(session.reviewText)
    }

    @Test fun cancelErrorsSilenceAndOversizedTextLeaveNothingBehind() {
        assertTrue(session.start())
        assertTrue(session.cancel())
        assertEquals(1, engine.cancels)
        assertFalse(session.cancel()) // nothing left to discard
        for ((deliver, expected) in listOf<Pair<(SpeechEngine.Listener) -> Unit, String>>(
            { l: SpeechEngine.Listener -> l.onError(DictationError.NO_SPEECH) } to "failed:NO_SPEECH",
            { l: SpeechEngine.Listener -> l.onError(DictationError.LANGUAGE) } to "failed:LANGUAGE",
            { l: SpeechEngine.Listener -> l.onFinal("   ") } to "failed:NO_SPEECH",
            { l: SpeechEngine.Listener -> l.onFinal("x".repeat(8_193)) } to "denied:TOO_LARGE",
            { l: SpeechEngine.Listener -> l.onFinal("broken \uD83D") } to "failed:FAILED")) {
            assertTrue(session.start())
            deliver(engine.listener!!)
            assertEquals(expected, ui.events.last())
            assertFalse(session.isActive)
            assertNull(session.reviewText)
        }
        assertTrue(inserted.isEmpty())
    }

    @Test fun recognizerThatFailsImmediatelyDoesNotLeaveAnOpenSession() {
        engine.failOnStart = DictationError.UNAVAILABLE
        assertFalse(session.start())
        assertEquals("failed:UNAVAILABLE", ui.events.last())
        assertFalse(session.isActive)
        engine.failOnStart = null
        assertTrue(session.start()) // and a later attempt works
    }

    @Test fun lateCallbacksOfAnOlderRecognitionNeverReachANewOne() {
        assertTrue(session.start())
        val old = engine.listener!!
        session.cancel()
        assertTrue(session.start())
        old.onPartial("old words")
        old.onFinal("old text")
        old.onError(DictationError.FAILED)
        assertTrue(session.isListening)
        assertEquals(listOf("listening:", "listening:"), ui.events)
        engine.listener!!.onFinal("new text")
        assertEquals("new text", session.reviewText)
    }

    @Test fun uncertainInsertIsReportedAndNeverRetried() {
        assertTrue(session.start())
        engine.listener!!.onFinal("Hallo")
        outcome = ApplyOutcome.UNKNOWN
        assertEquals(ApplyOutcome.UNKNOWN, session.insert())
        assertEquals(ApplyOutcome.REJECTED, session.insert())
        assertEquals(1, inserted.size)
    }

    @Test fun partialTextShownWhileListeningIsBounded() {
        assertTrue(session.start())
        engine.listener!!.onPartial("y".repeat(5_000))
        assertEquals(2_000, ui.events.last().removePrefix("listening:").length)
    }
}
