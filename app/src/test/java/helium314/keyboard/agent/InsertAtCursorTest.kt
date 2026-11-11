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

    @Before fun setup() {
        field = EditText(app).apply { setText("prefix  suffix"); setSelection(7) }
        val connection = object : BaseInputConnection(field, true) {
            override fun getEditable(): Editable = field.editableText
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commitCount++
                if (reject) return false
                val result = super.commitText(text, newCursorPosition)
                if (loseReply) error("lost reply")
                return result
            }
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

    @Test fun movedCursorOrNewSelectionNeverInsertsAndNeverReplaces() {
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", 3)) // not the previewed place
        field.setSelection(0, 6) // the app selected text behind the keyboard's back
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals("prefix  suffix", field.text.toString())
        assertEquals(0, commitCount)
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("", 7))
        assertEquals(ApplyOutcome.REJECTED, rich.insertTextIfCursorUnchanged("Hallo", -1))
    }

    @Test fun rejectedOrLostCommitIsUnknownWithoutASecondAttempt() {
        reject = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals("prefix  suffix", field.text.toString())
        assertEquals(1, commitCount)
        reject = false; loseReply = true
        assertTrue(rich.resetCachesUponCursorMoveAndReturnSuccess(7, 7, false))
        assertEquals(ApplyOutcome.UNKNOWN, rich.insertTextIfCursorUnchanged("Hallo", 7))
        assertEquals(2, commitCount)
    }

    @Test fun adapterConsumesTheAnchorOnceAndRefusesAfterAnyEditorChange() {
        val inserted = mutableListOf<Pair<Int, String>>()
        var outcome = ApplyOutcome.SUBMITTED
        var access = EditorAccess()
        val adapter = AndroidEditorAdapter({ error("an anchor reads no editor text") }, { it.copy(isIncognito = access.isIncognito) },
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
    }
}
