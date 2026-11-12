// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.inputmethodservice.InputMethodService
import android.text.Editable
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
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

/** RichInputConnection.replaceTextBeforeCursorIfUnchanged against a real Editable (stage 24a). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CommandReplaceTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var field: EditText
    private lateinit var rich: RichInputConnection
    private var ignoreRegion = false
    private var failCommit = false
    private var commits = 0
    private var noExtractedText = false

    @Before fun setup() {
        field = EditText(app).apply { setText("Preis @math 2*3 ok"); setSelection(15) }
        val connection = object : BaseInputConnection(field, true) {
            override fun getEditable(): Editable = field.editableText
            override fun setComposingRegion(start: Int, end: Int): Boolean =
                if (ignoreRegion) true else super.setComposingRegion(start, end)
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commits++
                if (failCommit) return false
                return super.commitText(text, newCursorPosition)
            }
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = if (noExtractedText) null else ExtractedText().apply {
                startOffset = 0
                selectionStart = field.selectionStart
                selectionEnd = field.selectionEnd
                text = field.text
            }
        }
        val parent = mock(InputMethodService::class.java)
        `when`(parent.currentInputConnection).thenReturn(connection)
        rich = RichInputConnection(parent)
        assertTrue(rich.resetCachesUponCursorMoveAndReturnSuccess(15, 15, false))
    }

    private fun composing() = BaseInputConnection.getComposingSpanStart(field.editableText)

    @Test fun replacesExactlyTheCommandInOneCommit() {
        assertEquals(ApplyOutcome.SUBMITTED, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "2 × 3 = 6", 15))
        assertEquals("Preis 2 × 3 = 6 ok", field.text.toString())
        assertEquals(15, rich.expectedSelectionStart)
        assertEquals(1, commits)
        assertEquals(-1, composing())
    }

    @Test fun deletesWithAnEmptyReplacement() {
        assertEquals(ApplyOutcome.SUBMITTED, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "", 15))
        assertEquals("Preis  ok", field.text.toString())
        assertEquals(6, rich.expectedSelectionStart)
    }

    @Test fun otherTextOrPlaceIsNeverReplaced() {
        assertEquals(ApplyOutcome.REJECTED, rich.replaceTextBeforeCursorIfUnchanged("@math 2*4", "x", 15))
        assertEquals(ApplyOutcome.REJECTED, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "x", 14))
        field.setSelection(15, 18)
        assertEquals(ApplyOutcome.REJECTED, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "x", 15))
        assertEquals("Preis @math 2*3 ok", field.text.toString())
        assertEquals(0, commits)
    }

    @Test fun anEditorThatIgnoresTheRegionIsReportedUnclear() {
        ignoreRegion = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "6", 15))
        assertEquals(-1, composing())
    }

    @Test fun withoutExtractedTextTheCharacterBeforeTheCommandIsChecked() {
        noExtractedText = true
        ignoreRegion = true // the editor inserts at the cursor instead of replacing
        assertEquals(ApplyOutcome.UNKNOWN, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "", 15))
        assertEquals(ApplyOutcome.REJECTED, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "6", 15)) // caches were dropped
        ignoreRegion = false
        setup()
        assertEquals(ApplyOutcome.SUBMITTED, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "6", 15))
        assertEquals("Preis 6 ok", field.text.toString())
    }

    @Test fun aFailedCommitLeavesNoComposingRegionOverTheCommand() {
        failCommit = true
        assertEquals(ApplyOutcome.UNKNOWN, rich.replaceTextBeforeCursorIfUnchanged("@math 2*3", "6", 15))
        assertEquals("Preis @math 2*3 ok", field.text.toString())
        assertEquals(-1, composing()) // the next letter must not replace the command (P25)
    }
}
