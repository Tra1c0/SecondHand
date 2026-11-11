// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class InsertionAnchorTest {
    private val policy = ExecutionPolicy()
    private val text = EditorAccess(isText = true)
    private fun session() = EditorSession().apply { start() }

    @Test fun anchorNeedsAPlainCursorInAnAllowedEditor() {
        val token = session().token
        val captured = policy.captureAnchor(token, text, 4, 4) as AnchorResult.Captured
        assertEquals(4, captured.anchor.position)
        assertEquals(AnchorResult.Denied(DenialReason.NO_CURSOR), policy.captureAnchor(token, text, 4, 9))
        assertEquals(AnchorResult.Denied(DenialReason.NO_CURSOR), policy.captureAnchor(token, text, -1, -1))
        assertEquals(AnchorResult.Denied(DenialReason.NO_EDITOR), policy.captureAnchor(null, text, 4, 4))
        for ((access, reason) in listOf(
            text.copy(isPassword = true) to DenialReason.PASSWORD, text.copy(isIncognito = true) to DenialReason.INCOGNITO,
            text.copy(forbidsLearning = true) to DenialReason.NO_LEARNING, text.copy(isLocked = true) to DenialReason.LOCKED,
            text.copy(isComposing = true) to DenialReason.COMPOSITION, EditorAccess() to DenialReason.UNSUPPORTED_EDITOR)) {
            assertEquals(AnchorResult.Denied(reason), policy.captureAnchor(token, access, 4, 4))
        }
    }

    @Test fun insertionIsRefusedAfterAnyChangeAndForUnusableText() {
        val session = session()
        val anchor = (policy.captureAnchor(session.token, text, 4, 4) as AnchorResult.Captured).anchor
        assertNull(policy.insertDenial(anchor, session.token, text, 4, 4, "Hallo"))
        assertEquals(DenialReason.STALE_CONTEXT, policy.insertDenial(anchor, session.token, text, 5, 5, "Hallo"))
        assertEquals(DenialReason.STALE_CONTEXT, policy.insertDenial(anchor, session.token, text, 4, 6, "Hallo"))
        assertEquals(DenialReason.PASSWORD, policy.insertDenial(anchor, session.token, text.copy(isPassword = true), 4, 4, "Hallo"))
        assertEquals(DenialReason.INVALID_TEXT, policy.insertDenial(anchor, session.token, text, 4, 4, ""))
        assertEquals(DenialReason.INVALID_TEXT, policy.insertDenial(anchor, session.token, text, 4, 4, "broken \uD83D"))
        assertEquals(DenialReason.TOO_LARGE, policy.insertDenial(anchor, session.token, text, 4, 4, "x".repeat(8_193)))
        session.invalidate() // same coordinates later are still a different editor state
        assertEquals(DenialReason.STALE_CONTEXT, policy.insertDenial(anchor, session.token, text, 4, 4, "Hallo"))
        assertFalse(anchor.toString().contains("position"))
    }
}
