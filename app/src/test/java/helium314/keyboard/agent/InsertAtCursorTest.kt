// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.text.Editable
import android.text.InputType
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import dev.agentkeyboard.core.*
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
class InsertAtCursorTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var field: EditText
    private lateinit var rich: RichInputConnection
    private var commitCount = 0
    private var reject = false
    private var loseReply = false
    private var unresponsive = false
    private var failReadsAfterCommit = false

    @Before fun setup() {
        field = EditText(app).apply { setText("prefix  suffix"); setSelection(7) }
        val connection = object : BaseInputConnection(field, true) {
            override fun getEditable(): Editable = field.editableText
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commitCount++
                if (reject) return false
                val result = super.commitText(text, newCursorPosition)
                if (failReadsAfterCommit) unresponsive = true
                if (loseReply) error("lost reply")
                return result
            }
            // A frozen app or a dead connection answers every read with null.
            override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? =
                if (unresponsive) null else super.getTextBeforeCursor(length, flags)
            override fun getSelectedText(flags: Int): CharSequence? =
                if (unresponsive) null else super.getSelectedText(flags)
        }
        val parent = mock(InputMethodService::class.java)
        `when`(parent.currentInputConnection).thenReturn(connection)
        rich = RichInputConnection(parent)
        assertTrue(rich.resetCachesUponCursorMoveAndReturnSuccess(7, 7, false))
    }

    @Test fun insertsOnceAtTheCursorAndSynchronizesCaches() {
        assertEquals(ApplyOutcome.SUBMITTED, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals("prefix Hallo suffix", field.text.toString())
        assertEquals(12, rich.expectedSelectionStart)
        assertEquals(12, rich.expectedSelectionEnd)
        assertEquals(1, commitCount)
    }

    @Test fun emptyTextWrongPlaceOrNewSelectionNeverCommits() {
        // The cursor really is at 7 here, so only the guards keep these from committing.
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("", 7))
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", 3)) // not the previewed place
        assertEquals(0, commitCount)
        field.setSelection(0, 6) // the app selected text behind the keyboard's back
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals("prefix  suffix", field.text.toString())
        assertEquals(0, commitCount)
    }

    @Test fun unresponsiveEditorIsNeverTreatedAsNothingSelected() {
        // The real state: a selection the keyboard was not told about, in an app that stopped
        // answering. "No selected text" (null) proves nothing then; a commit would replace it later.
        field.setSelection(0, 6)
        unresponsive = true
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals(0, commitCount)
        assertEquals("prefix  suffix", field.text.toString())
    }

    @Test fun rejectedOrLostCommitIsUnknownClearsCachesAndIsNeverRepeated() {
        reject = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals("prefix  suffix", field.text.toString())
        assertEquals(1, commitCount)
        assertEquals(-1, rich.expectedSelectionStart)
        assertEquals(-1, rich.expectedSelectionEnd)
        // With unknown coordinates nothing can be inserted, not even at "position -1".
        reject = false
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", -1))
        assertEquals(1, commitCount)

        loseReply = true
        assertTrue(rich.resetCachesUponCursorMoveAndReturnSuccess(7, 7, false))
        assertEquals(ApplyOutcome.UNKNOWN, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals("prefix Hallo suffix", field.text.toString()) // the editor took it; the keyboard cannot know
        assertEquals(2, commitCount)
        assertEquals(-1, rich.expectedSelectionStart)
        assertEquals(-1, rich.expectedSelectionEnd)
    }

    @Test fun successfulCommitWithUnavailableRefreshIsReportedAsUncertain() {
        failReadsAfterCommit = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals("prefix Hallo suffix", field.text.toString())
        assertEquals(1, commitCount)
        assertEquals(-1, rich.expectedSelectionStart)
        assertEquals(-1, rich.expectedSelectionEnd)
    }

    @Test fun adapterConsumesTheAnchorOnceAndRefusesAfterAnyEditorChange() {
        val inserted = mutableListOf<Pair<Int, String>>()
        var outcome = ApplyOutcome.SUBMITTED
        var access = EditorAccess()
        var reads = 0
        val adapter = AndroidEditorAdapter({ reads++; null }, { it.copy(isIncognito = access.isIncognito) },
            { _, _ -> error("insertion never replaces a selection") })
        assertIs<AnchorResult.Denied>(adapter.captureAnchor()) // no editor yet
        adapter.startEditor(InputType.TYPE_CLASS_TEXT, 0, 7, 7)
        val anchor = (adapter.captureAnchor() as AnchorResult.Captured).anchor
        assertEquals(ApplyOutcome.REJECTED, adapter.insert(anchor, "Hallo")) // no commit path installed
        adapter.commitInsertion = { target, text -> inserted.add(target.position to text); outcome }
        assertTrue(adapter.isCurrent(anchor))
        assertEquals(ApplyOutcome.SUBMITTED, adapter.insert(anchor, "Hallo"))
        assertEquals(ApplyOutcome.REJECTED, adapter.insert(anchor, "Hallo")) // double click
        assertEquals(listOf(7 to "Hallo"), inserted)

        val moved = (adapter.captureAnchor() as AnchorResult.Captured).anchor
        adapter.selectionChanged(9, 9, -1, -1)
        assertFalse(adapter.isCurrent(moved))
        assertEquals(ApplyOutcome.REJECTED, adapter.insert(moved, "x"))

        val uncertain = (adapter.captureAnchor() as AnchorResult.Captured).anchor
        outcome = ApplyOutcome.UNKNOWN
        assertEquals(ApplyOutcome.UNKNOWN, adapter.insert(uncertain, "y"))
        assertEquals(ApplyOutcome.REJECTED, adapter.insert(uncertain, "y")) // never retried
        assertEquals(2, inserted.size)

        val private = (adapter.captureAnchor() as AnchorResult.Captured).anchor
        access = EditorAccess(isIncognito = true)
        assertEquals(ApplyOutcome.REJECTED, adapter.insert(private, "z"))
        assertEquals(AnchorResult.Denied(DenialReason.INCOGNITO), adapter.captureAnchor())
        access = EditorAccess()
        adapter.selectionChanged(2, 5, -1, -1)
        assertEquals(AnchorResult.Denied(DenialReason.NO_CURSOR), adapter.captureAnchor())
        adapter.startEditor(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0, 0, 0)
        assertEquals(AnchorResult.Denied(DenialReason.PASSWORD), adapter.captureAnchor())
        assertEquals(2, inserted.size)
        assertEquals(0, reads) // anchors and insertions never read text from the editor
    }
}
