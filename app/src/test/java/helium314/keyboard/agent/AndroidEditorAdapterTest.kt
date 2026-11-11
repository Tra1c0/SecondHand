// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import dev.agentkeyboard.core.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidEditorAdapterTest {
    private val connection = mock(InputConnection::class.java)
    private var connectionReads = 0
    private var commits = 0
    private var locked = false
    private var incognito = false
    private var failCommit = false
    private val adapter = AndroidEditorAdapter(
        connection = { connectionReads++; connection },
        liveAccess = { it.copy(isLocked = locked, isIncognito = incognito) },
        commitSelection = { _, _ -> commits++; if (failCommit) error("transport lost") else ApplyOutcome.SUBMITTED },
    )

    @Before fun start() {
        `when`(connection.getSelectedText(0)).thenReturn("Hallo")
        adapter.startEditor(InputType.TYPE_CLASS_TEXT, 0, 0, 5)
    }

    private fun capture() = assertIs<CaptureResult.Captured>(adapter.capture()).snapshot

    @Test fun passwordVariantsAndLearningFlagsAreCheckedBeforeAnyConnectionRead() {
        val types = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_PHONE,
        )
        for (type in types) {
            adapter.startEditor(type, 0, 0, 5)
            assertIs<CaptureResult.Denied>(adapter.capture())
        }
        adapter.startEditor(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, 0, 5)
        assertIs<CaptureResult.Denied>(adapter.capture())
        assertEquals(0, connectionReads)
    }

    @Test fun lockIncognitoCompositionAndExcessiveRangesDoNotReadText() {
        locked = true
        assertIs<CaptureResult.Denied>(adapter.capture())
        locked = false; incognito = true
        assertIs<CaptureResult.Denied>(adapter.capture())
        incognito = false
        adapter.selectionChanged(0, 5, 0, 5)
        assertIs<CaptureResult.Denied>(adapter.capture())
        adapter.selectionChanged(0, 8193, -1, -1)
        assertIs<CaptureResult.Denied>(adapter.capture())
        assertEquals(0, connectionReads)
    }

    @Test fun captureOnlyReadsSelectedText() {
        assertEquals("Hallo", capture().text)
        verify(connection).getSelectedText(0)
        verifyNoMoreInteractions(connection)
    }

    @Test fun callbackOrLocalEditInvalidatesEvenIfTextAndCoordinatesReturn() {
        val old = capture()
        adapter.selectionChanged(1, 6, -1, -1)
        adapter.selectionChanged(0, 5, -1, -1)
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(old, "HALLO"))
        val current = capture()
        adapter.invalidate()
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(current, "HALLO"))
        assertEquals(0, commits)
    }

    @Test fun switchingAndFinishingEditorsRejectsOldResults() {
        val old = capture()
        adapter.startEditor(InputType.TYPE_CLASS_TEXT, 0, 0, 5)
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(old, "HALLO"))
        val current = capture()
        adapter.finishEditor()
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(current, "HALLO"))
        assertEquals(0, commits)
    }

    @Test fun changedSourceTextIsRecheckedImmediatelyBeforeApply() {
        val old = capture()
        `when`(connection.getSelectedText(0)).thenReturn("WELT!")
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(old, "HALLO"))
        assertEquals(0, commits)
    }

    @Test fun privacyChangesBlockApplyBeforeAnotherRead() {
        val old = capture()
        locked = true
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(old, "HALLO"))
        assertEquals(1, connectionReads)
        assertEquals(0, commits)
    }

    @Test fun successfulAndUncertainAttemptsConsumeTheirTokenOnce() {
        val old = capture()
        assertEquals(ApplyOutcome.SUBMITTED, adapter.apply(old, "HALLO"))
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(old, "HALLO"))
        assertEquals(1, commits)
        val next = capture()
        failCommit = true
        assertEquals(ApplyOutcome.UNKNOWN, adapter.apply(next, "HALLO"))
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(next, "HALLO"))
        assertEquals(2, commits)
    }

    @Test fun invalidationObserversCannotChangeEditorsBeforeTheCommit() {
        val old = capture()
        var observedCommits = -1
        adapter.onInvalidated = {
            observedCommits = commits
            adapter.finishEditor()
        }
        assertEquals(ApplyOutcome.SUBMITTED, adapter.apply(old, "HALLO"))
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(1, observedCommits)
        assertEquals(1, commits)
        assertEquals(ApplyOutcome.REJECTED, adapter.apply(old, "HALLO"))
    }

    @Test fun tokensExpireSynchronouslyButObserversCannotReenterAnInputMutation() {
        val old = capture()
        var observed = false
        adapter.onInvalidated = { observed = true }
        adapter.invalidate()
        assertFalse(adapter.isCurrent(old))
        assertFalse(observed)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue(observed)
    }
}
