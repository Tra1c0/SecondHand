// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class EditorPolicyTest {
    private val policy = ExecutionPolicy()
    private val session = EditorSession().apply { start() }
    private val normal = EditorAccess(isText = true)

    private fun snapshot(text: String = "Hallo") = assertIs<CaptureResult.Captured>(
        policy.capture(session.token, normal, 2, 2 + text.length, text)
    ).snapshot

    @Test fun sensitiveEditorsAreDeniedBeforeCapture() {
        val cases = listOf(
            normal.copy(isLocked = true) to DenialReason.LOCKED,
            normal.copy(isPassword = true) to DenialReason.PASSWORD,
            normal.copy(isIncognito = true) to DenialReason.INCOGNITO,
            normal.copy(forbidsLearning = true) to DenialReason.NO_LEARNING,
            normal.copy(isText = false) to DenialReason.UNSUPPORTED_EDITOR,
            normal.copy(isComposing = true) to DenialReason.COMPOSITION,
        )
        for ((access, reason) in cases)
            assertEquals(CaptureResult.Denied(reason), policy.capture(session.token, access, 0, 5, "Hallo"))
    }

    @Test fun noActiveEditorIsDenied() {
        session.finish()
        assertEquals(CaptureResult.Denied(DenialReason.NO_EDITOR),
            policy.capture(session.token, normal, 0, 5, "Hallo"))
    }

    @Test fun selectionsAreRequiredAndMustMatchTheirUtf16Bounds() {
        assertEquals(CaptureResult.Denied(DenialReason.NO_SELECTION),
            policy.capture(session.token, normal, -1, -1, null))
        assertEquals(CaptureResult.Denied(DenialReason.NO_SELECTION),
            policy.capture(session.token, normal, 2, 2, ""))
        assertEquals(CaptureResult.Denied(DenialReason.INVALID_TEXT),
            policy.capture(session.token, normal, 0, 10, "Hallo"))
    }

    @Test fun reversedSelectionIsNormalized() {
        val captured = assertIs<CaptureResult.Captured>(policy.capture(session.token, normal, 7, 2, "Hallo"))
        assertEquals(2, captured.snapshot.selectionStart)
        assertEquals(7, captured.snapshot.selectionEnd)
    }

    @Test fun oversizedAndBrokenUnicodeSelectionsAreRejected() {
        assertEquals(CaptureResult.Denied(DenialReason.TOO_LARGE),
            policy.capture(session.token, normal, 0, 8193, "a".repeat(8193)))
        for (broken in listOf("\uD83D", "\uDE00", "a\uD83Db"))
            assertEquals(CaptureResult.Denied(DenialReason.INVALID_TEXT),
                policy.capture(session.token, normal, 0, broken.length, broken))
        assertIs<CaptureResult.Captured>(policy.capture(session.token, normal, 0, 2, "😀"))
    }

    @Test fun sameFieldAndTextAfterSessionRestartAreStillStale() {
        val old = snapshot()
        session.start()
        assertEquals(DenialReason.STALE_CONTEXT, policy.applyDenial(old, session.token, normal, 2, 7, "Hallo"))
    }

    @Test fun aNewServiceOwnerCannotReviveAnotherOwnersSnapshot() {
        val old = snapshot()
        val newOwner = EditorSession().apply { start() }
        assertNotEquals(old.token, newOwner.token)
        assertFalse(newOwner.consume(old.token))
        assertEquals(DenialReason.STALE_CONTEXT,
            policy.applyDenial(old, newOwner.token, normal, 2, 7, "Hallo"))
    }

    @Test fun movingAwayAndBackCannotReviveAResult() {
        val old = snapshot()
        session.invalidate()
        session.invalidate()
        assertEquals(DenialReason.STALE_CONTEXT, policy.applyDenial(old, session.token, normal, 2, 7, "Hallo"))
    }

    @Test fun unchangedCoordinatesDoNotAuthorizeChangedText() {
        val old = snapshot()
        assertEquals(DenialReason.STALE_CONTEXT, policy.applyDenial(old, session.token, normal, 2, 7, "WELT!"))
        assertEquals(DenialReason.STALE_CONTEXT, policy.applyDenial(old, session.token, normal, 3, 8, "Hallo"))
    }

    @Test fun permissionsAreCheckedAgainAtApply() {
        val old = snapshot()
        assertEquals(DenialReason.PASSWORD,
            policy.applyDenial(old, session.token, normal.copy(isPassword = true), 2, 7, "Hallo"))
        assertNull(policy.applyDenial(old, session.token, normal, 2, 7, "Hallo"))
    }

    @Test fun aMutationTokenCanOnlyBeConsumedOnce() {
        val old = snapshot()
        assertTrue(session.consume(old.token))
        assertFalse(session.consume(old.token))
        assertEquals(DenialReason.STALE_CONTEXT, policy.applyDenial(old, session.token, normal, 2, 7, "Hallo"))
    }

    @Test fun resultLimitsAllowIntentionalEmptyReplacementButNotMalformedText() {
        assertNull(policy.resultDenial(""))
        assertEquals(DenialReason.TOO_LARGE, policy.resultDenial("a".repeat(16385)))
        assertEquals(DenialReason.INVALID_TEXT, policy.resultDenial("\uD83D"))
    }

    @Test fun diagnosticRepresentationsOmitSensitiveText() {
        val captured = snapshot("geheim")
        assertFalse(captured.toString().contains("geheim"))
        assertFalse(ModelRequest("model", "geheim", captured).toString().contains("geheim"))
        assertFalse(ModelResponse("geheim").toString().contains("geheim"))
    }
}
