// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

/** Stage 24f `@ki`: the typed question as the source of one approved request. */
class QuestionCaptureTest {
    private val policy = ExecutionPolicy()
    private val text = EditorAccess(isText = true)
    private fun session() = EditorSession().apply { start() }

    @Test fun questionIsTakenAtAPlainCursorEvenWhileItsLastWordIsComposing() {
        val token = session().token
        val snapshot = (policy.captureQuestion(token, text.copy(isComposing = true), 9, 9, "Wie spät?") as CaptureResult.Captured).snapshot
        assertEquals("Wie spät?", snapshot.text)
        assertEquals(9, snapshot.selectionStart)
        assertEquals(9, snapshot.selectionEnd)
        assertEquals(CaptureResult.Denied(DenialReason.NO_CURSOR), policy.captureQuestion(token, text, 4, 9, "Frage"))
        assertEquals(CaptureResult.Denied(DenialReason.NO_CURSOR), policy.captureQuestion(token, text, -1, -1, "Frage"))
        assertEquals(CaptureResult.Denied(DenialReason.NO_EDITOR), policy.captureQuestion(null, text, 4, 4, "Frage"))
    }

    @Test fun protectedEditorsAndUnusableQuestionsAreRefused() {
        val token = session().token
        for ((access, reason) in listOf(
            text.copy(isPassword = true) to DenialReason.PASSWORD, text.copy(isIncognito = true) to DenialReason.INCOGNITO,
            text.copy(forbidsLearning = true) to DenialReason.NO_LEARNING, text.copy(isLocked = true) to DenialReason.LOCKED,
            EditorAccess() to DenialReason.UNSUPPORTED_EDITOR)) {
            assertEquals(CaptureResult.Denied(reason), policy.captureQuestion(token, access, 4, 4, "Frage"))
        }
        assertEquals(CaptureResult.Denied(DenialReason.INVALID_TEXT), policy.captureQuestion(token, text, 4, 4, "  "))
        assertEquals(CaptureResult.Denied(DenialReason.INVALID_TEXT), policy.captureQuestion(token, text, 4, 4, "broken \uD83D"))
        assertEquals(CaptureResult.Denied(DenialReason.TOO_LARGE), policy.captureQuestion(token, text, 4, 4, "x".repeat(8_193)))
    }

    @Test fun questionStaysCurrentInTheSameEditorButNeverReplacesASelection() {
        val session = session()
        val snapshot = (policy.captureQuestion(session.token, text, 4, 4, "Frage") as CaptureResult.Captured).snapshot
        assertTrue(policy.isCurrentQuestion(snapshot, session.token, text))
        session.invalidate() // a moved cursor does not change what was approved
        assertTrue(policy.isCurrentQuestion(snapshot, session.token, text.copy(isComposing = true)))
        assertFalse(policy.isCurrentQuestion(snapshot, session.token, text.copy(isIncognito = true)))
        assertFalse(policy.isCurrentQuestion(snapshot, null, text))
        // an empty selection never matches what applyDenial requires
        assertEquals(DenialReason.STALE_CONTEXT, policy.applyDenial(snapshot, session.token, text, 4, 4, null))
        session.start() // another editor
        assertFalse(policy.isCurrentQuestion(snapshot, session.token, text))
        // a selection snapshot is no question
        val selection = (policy.capture(session.token, text, 0, 5, "Hallo") as CaptureResult.Captured).snapshot
        assertFalse(policy.isCurrentQuestion(selection, session.token, text))
        assertEquals("TextSnapshot(chars=5, token=${snapshot.token})", snapshot.toString()) // no text
    }
}
