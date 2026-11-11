// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.text.Editable
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import dev.agentkeyboard.core.ApplyOutcome
import helium314.keyboard.latin.RichInputConnection
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SelectedTextCommitTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var field: EditText
    private lateinit var rich: RichInputConnection
    private var commitCount = 0
    private var reject = false
    private var loseReply = false
    private var failRefresh = false

    @Before fun setup() {
        field = EditText(app).apply { setText("prefix Hallo suffix"); setSelection(7, 12) }
        val connection = object : BaseInputConnection(field, true) {
            override fun getEditable(): Editable = field.editableText
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commitCount++
                if (reject) return false
                val result = super.commitText(text, newCursorPosition)
                if (loseReply) error("lost reply")
                return result
            }
            override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? =
                if (failRefresh) null else super.getTextBeforeCursor(length, flags)
        }
        val parent = mock(InputMethodService::class.java)
        `when`(parent.currentInputConnection).thenReturn(connection)
        rich = RichInputConnection(parent)
        assertTrue(rich.resetCachesUponCursorMoveAndReturnSuccess(7, 12, false))
    }

    @Test fun replacesExactlyTheSelectionAndSynchronizesCaches() {
        assertEquals(ApplyOutcome.SUBMITTED, rich.replaceSelectedTextIfUnchanged("Hallo", ".com", 7, 12))
        assertEquals("prefix .com suffix", field.text.toString())
        assertEquals(11, rich.expectedSelectionStart)
        assertEquals(11, rich.expectedSelectionEnd)
        assertEquals(1, commitCount)
    }

    @Test fun changedSelectionNeverRestoresOrDeletesAnOldRange() {
        field.setSelection(0, 6)
        assertEquals(ApplyOutcome.REJECTED, rich.replaceSelectedTextIfUnchanged("Hallo", "HALLO", 7, 12))
        assertEquals("prefix Hallo suffix", field.text.toString())
        assertEquals(0, commitCount)
    }

    @Test fun rejectedRequestHasNoFallbackInsertion() {
        reject = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.replaceSelectedTextIfUnchanged("Hallo", "HALLO", 7, 12))
        assertEquals("prefix Hallo suffix", field.text.toString())
        assertEquals(1, commitCount)
        assertEquals(-1, rich.expectedSelectionStart)
        assertEquals(-1, rich.expectedSelectionEnd)
    }

    @Test fun lostReplyDoesNotCauseAnotherCommit() {
        loseReply = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.replaceSelectedTextIfUnchanged("Hallo", "HALLO", 7, 12))
        assertEquals("prefix HALLO suffix", field.text.toString())
        assertEquals(1, commitCount)
        assertEquals(-1, rich.expectedSelectionStart)
        assertEquals(-1, rich.expectedSelectionEnd)
    }

    @Test fun successfulCommitWithUnavailableRefreshIsReportedAsUncertain() {
        failRefresh = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.replaceSelectedTextIfUnchanged("Hallo", "HALLO", 7, 12))
        assertEquals("prefix HALLO suffix", field.text.toString())
        assertEquals(1, commitCount)
    }

    @Test fun authoritativeCursorRefreshRestoresNormalTypingAfterUncertainCommit() {
        loseReply = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.replaceSelectedTextIfUnchanged("Hallo", "HALLO", 7, 12))
        loseReply = false
        assertTrue(rich.resetCachesUponCursorMoveAndReturnSuccess(field.selectionStart, field.selectionEnd, false))
        rich.beginBatchEdit()
        rich.commitText("!", 1)
        rich.endBatchEdit()
        assertEquals("prefix HALLO! suffix", field.text.toString())
        assertEquals(field.selectionStart, rich.expectedSelectionStart)
    }
}
