// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AgentPromptsScreen
import helium314.keyboard.settings.screens.PROMPTS_ADD_TAG
import helium314.keyboard.settings.screens.PROMPTS_CANCEL_TAG
import helium314.keyboard.settings.screens.PROMPTS_COUNT_TAG
import helium314.keyboard.settings.screens.PROMPTS_DISCARD_TAG
import helium314.keyboard.settings.screens.PROMPTS_KEEP_EDITING_TAG
import helium314.keyboard.settings.screens.PROMPTS_DELETE_CONFIRM_TAG
import helium314.keyboard.settings.screens.PROMPTS_DELETE_TAG
import helium314.keyboard.settings.screens.PROMPTS_EDIT_TAG
import helium314.keyboard.settings.screens.PROMPTS_EMPTY_TAG
import helium314.keyboard.settings.screens.PROMPTS_NOTICE_TAG
import helium314.keyboard.settings.screens.PROMPTS_PASTE_TAG
import helium314.keyboard.settings.screens.PROMPTS_RESET_CONFIRM_TAG
import helium314.keyboard.settings.screens.PROMPTS_RESET_TAG
import helium314.keyboard.settings.screens.PROMPTS_ROW_TAG
import helium314.keyboard.settings.screens.PROMPTS_SAVE_TAG
import helium314.keyboard.settings.screens.PROMPTS_SORT_TAG
import helium314.keyboard.settings.screens.PROMPTS_TEXT_FIELD_TAG
import helium314.keyboard.settings.screens.PROMPTS_TITLE_FIELD_TAG
import helium314.keyboard.settings.screens.PROMPTS_UNAVAILABLE_TAG
import helium314.keyboard.settings.screens.confirmPromptOrder
import helium314.keyboard.settings.screens.rowPreview
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 74a: the page "Prompt-Ablage" (empty state, editor card, delete, sort, paste, reset, store order). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class PromptsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private var accessible = true
    private fun newStore(context: Context = app) = PromptStore(context) { accessible }
    private val file get() = File(credentialContext(app).noBackupFilesDir, PromptStore.FILE_NAME)
    private val clipboard get() = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Before fun setUp() {
        accessible = true
        file.delete(); File(file.path + ".bak").delete()
        app.prefs().edit().clear().commit()
    }

    @After fun tearDown() {
        file.delete(); File(file.path + ".bak").delete()
        app.prefs().edit().clear().commit()
    }

    private fun show() {
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentPromptsScreen(onClickBack = {}, storeFactory = { newStore(it) }) } }
        compose.waitForIdle()
    }

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick(); compose.waitForIdle() }

    private fun addThroughEditor(title: String, text: String) {
        click(PROMPTS_ADD_TAG)
        if (title.isNotEmpty()) compose.onNodeWithTag(PROMPTS_TITLE_FIELD_TAG).performTextInput(title)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput(text)
        click(PROMPTS_SAVE_TAG)
    }

    @Test fun showsTheEmptyStateAndTheCount() {
        show()
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assert(hasText("Noch keine Prompts."))
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("0 von 200"))
        compose.onNodeWithTag(PROMPTS_SORT_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertIsEnabled()
    }

    @Test fun addingThroughTheEditorShowsTheRowAndStoresIt() {
        show()
        addThroughEditor("Mail", "Schreibe eine freundliche Mail")
        val stored = newStore().read().single()
        assertEquals("Mail", stored.title)
        assertEquals("Schreibe eine freundliche Mail", stored.text)
        compose.onNodeWithTag(PROMPTS_ROW_TAG + stored.id).assert(hasAnyDescendant(hasText("Mail")))
            .assert(hasAnyDescendant(hasText("Schreibe eine freundliche Mail")))
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("1 von 200"))
    }

    @Test fun theListIsNotReachableWhileTheEditorIsOpen() {
        newStore().add("a", "1")
        show()
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_SORT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assertExists()
    }

    @Test fun aPromptWithoutATitleShowsTheFirstLineOfItsText() {
        newStore().add("", "\nErste Zeile\nzweite Zeile")
        show()
        val id = newStore().read().single().id
        compose.onNodeWithTag(PROMPTS_ROW_TAG + id).assert(hasAnyDescendant(hasText("Erste Zeile")))
    }

    @Test fun theSaveButtonNeedsTextAndRespectsTheLimits() {
        show()
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("   ")
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("x")
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsEnabled()
        compose.onNodeWithTag(PROMPTS_TITLE_FIELD_TAG).performTextInput("t".repeat(PromptStore.MAX_TITLE + 1))
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
        assertTrue(newStore().read().isEmpty())
    }

    @Test fun editKeepsThePositionAndTheCursorIsAtTheEnd() {
        val store = newStore()
        store.add("eins", "1"); store.add("zwei", "2")
        show()
        val second = store.read()[1]
        click(PROMPTS_EDIT_TAG + second.id)
        assertEquals("1", fieldText(PROMPTS_TEXT_FIELD_TAG))
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("!")
        click(PROMPTS_SAVE_TAG)
        assertEquals(listOf("zwei", "eins"), store.read().map { it.title })
        assertEquals(second.id, store.read()[1].id)
        assertEquals("1!", store.read()[1].text)
        assertEquals("2", store.read()[0].text)
    }

    @Test fun editingAPromptDeletedMeanwhileShowsTheNotice() {
        val store = newStore()
        val a = store.add("a", "1").prompt!!
        show()
        click(PROMPTS_EDIT_TAG + a.id)
        store.delete(a.id)
        click(PROMPTS_SAVE_TAG)
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assert(hasText("Diesen Prompt gibt es nicht mehr."))
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assertExists()
        assertTrue(store.read().isEmpty())
    }

    @Test fun deleteNeedsTheConfirmation() {
        val store = newStore()
        val a = store.add("a", "1").prompt!!
        show()
        click(PROMPTS_DELETE_TAG + a.id)
        // a cancelled dialog deletes nothing
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(1, store.read().size)
        click(PROMPTS_DELETE_TAG + a.id)
        assertEquals(1, store.read().size)
        compose.onNodeWithTag(PROMPTS_DELETE_CONFIRM_TAG).performClick()
        compose.waitForIdle()
        assertTrue(store.read().isEmpty())
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assertExists()
    }

    @Test fun theRowsFollowTheOrderOfTheStore() {
        val store = newStore()
        val a = store.add("a", "1").prompt!!
        val b = store.add("b", "2").prompt!!
        val c = store.add("c", "3").prompt!!
        store.reorder(listOf(b.id, a.id, c.id))
        show()
        val tops = listOf(b, a, c).map { p -> compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops, "$tops")
        assertEquals(3, tops.toSet().size)
    }

    @Test fun aStaleOrderShowsTheListChangedNoticeAndKeepsTheStore() {
        val store = newStore()
        val a = store.add("a", "1").prompt!!
        val b = store.add("b", "2").prompt!!
        val c = store.add("c", "3").prompt!!
        // the dialog showed c, b, a; meanwhile a was deleted; confirming sends the old ids
        store.delete(a.id)
        assertEquals(R.string.agent_prompts_sort_changed, confirmPromptOrder(store, listOf(a.id, b.id, c.id)))
        assertEquals(listOf("c", "b"), store.read().map { it.title })
        // and one that is a valid permutation is saved and shown in the new order
        assertEquals(0, confirmPromptOrder(store, listOf(b.id, c.id)))
        show()
        val tops = listOf(b, c).map { p -> compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(tops[0] < tops[1], "$tops")
    }

    @Test fun lockedDeviceShowsTheHintAndNeitherListNorResetButton() {
        file.parentFile!!.mkdirs()
        file.writeText("garbage")
        accessible = false
        show()
        compose.onNodeWithTag(PROMPTS_UNAVAILABLE_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_RESET_TAG).assertDoesNotExist()
        assertEquals("garbage", file.readText())
    }

    @Test fun anUnusableFileOffersAResetThatNeedsTheConfirmation() {
        file.parentFile!!.mkdirs()
        file.writeText("garbage")
        show()
        compose.onNodeWithTag(PROMPTS_UNAVAILABLE_TAG).assertExists()
        click(PROMPTS_RESET_TAG)
        // cancelled: the file stays
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals("garbage", file.readText())
        click(PROMPTS_RESET_TAG)
        compose.onNodeWithTag(PROMPTS_RESET_CONFIRM_TAG).performClick()
        compose.waitForIdle()
        assertFalse(file.exists())
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_RESET_TAG).assertDoesNotExist()
    }

    @Test fun aHealthyStoreHasNoResetButton() {
        newStore().add("a", "1")
        show()
        compose.onNodeWithTag(PROMPTS_RESET_TAG).assertDoesNotExist()
    }

    @Test fun pasteInsertsAtTheCursorOnlyAfterATap() {
        clipboard.setPrimaryClip(ClipData.newPlainText("t", " aus der Zwischenablage"))
        show()
        click(PROMPTS_ADD_TAG)
        // opening the editor reads nothing from the clipboard
        assertEquals("", fieldText(PROMPTS_TEXT_FIELD_TAG))
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("Hallo")
        click(PROMPTS_PASTE_TAG)
        assertEquals("Hallo aus der Zwischenablage", fieldText(PROMPTS_TEXT_FIELD_TAG))
        click(PROMPTS_SAVE_TAG)
        assertEquals("Hallo aus der Zwischenablage", newStore().read().single().text)
    }

    @Test fun aTooLongClipInsertsNothingAndSaysSo() {
        clipboard.setPrimaryClip(ClipData.newPlainText("t", "y".repeat(PromptStore.MAX_TEXT + 1)))
        show()
        click(PROMPTS_ADD_TAG)
        assertEquals("", fieldText(PROMPTS_TEXT_FIELD_TAG))
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assertDoesNotExist()
        click(PROMPTS_PASTE_TAG)
        assertEquals("", fieldText(PROMPTS_TEXT_FIELD_TAG))
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
    }

    @Test fun existingTextPlusClipOverTheLimitInsertsNothingAndSaysSo() {
        val existing = "x".repeat(PromptStore.MAX_TEXT - 10)
        val a = newStore().add("a", existing).prompt!!
        clipboard.setPrimaryClip(ClipData.newPlainText("t", "z".repeat(20)))
        show()
        click(PROMPTS_EDIT_TAG + a.id)
        click(PROMPTS_PASTE_TAG)
        assertEquals(existing, fieldText(PROMPTS_TEXT_FIELD_TAG))
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assertExists()
        // a clip that still fits is inserted at the cursor (the end)
        clipboard.setPrimaryClip(ClipData.newPlainText("t", "z".repeat(10)))
        click(PROMPTS_PASTE_TAG)
        assertEquals(existing + "z".repeat(10), fieldText(PROMPTS_TEXT_FIELD_TAG))
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assertDoesNotExist()
    }

    @Test fun aRowShowsAtMostThe300FirstCharactersAndNeverCutsAPair() {
        val long = "a".repeat(PromptStore.MAX_TEXT)
        assertEquals(300, rowPreview(long).length)
        assertEquals("kurz", rowPreview("  kurz \n"))
        val emoji = "😀"
        val cut = rowPreview("a".repeat(299) + emoji + "tail")
        assertEquals("a".repeat(299), cut)
        assertFalse(PromptStore.hasLoneSurrogate(cut))
    }

    // ---- review of 74a: discard guard, saved state, back ----

    private fun nodeText(tag: String) = fieldText(tag)
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }

    @Test fun cancelWithChangesAsksAndKeepsTheTextOnKeepEditingAndDiscardsOnConfirm() {
        show()
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("abc")
        click(PROMPTS_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertExists()
        compose.onNodeWithText("Änderungen verwerfen?").assertExists()
        compose.onNodeWithTag(PROMPTS_KEEP_EDITING_TAG).performClick(); compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertDoesNotExist()
        assertEquals("abc", nodeText(PROMPTS_TEXT_FIELD_TAG))
        click(PROMPTS_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).performClick(); compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertExists()
        assertTrue(newStore().read().isEmpty())
    }

    @Test fun cancelWithoutChangesClosesAtOnce() {
        val a = newStore().add("t", "x").prompt!!
        show()
        click(PROMPTS_ADD_TAG)
        click(PROMPTS_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assertDoesNotExist()
        click(PROMPTS_EDIT_TAG + a.id)
        click(PROMPTS_CANCEL_TAG) // an existing prompt, nothing typed
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertExists()
        // typing something and typing it away again is no change
        click(PROMPTS_EDIT_TAG + a.id)
        compose.onNodeWithTag(PROMPTS_TITLE_FIELD_TAG).performTextInput("q")
        click(PROMPTS_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertExists() // a changed title asks too
    }

    @Test fun theBackButtonClosesTheEditorInsteadOfLeavingThePageAndAsksOnChanges() {
        var left = 0
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentPromptsScreen(onClickBack = { left++ }, storeFactory = { newStore(it) }) } }
        compose.waitForIdle()
        click(PROMPTS_ADD_TAG)
        back()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assertDoesNotExist() // the editor closed
        assertEquals(0, left)
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("abc")
        back()
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertExists()
        assertEquals("abc", nodeText(PROMPTS_TEXT_FIELD_TAG))
        assertEquals(0, left)
    }

    @Test fun theEditorStateSurvivesAnActivityRestore() {
        newStore().add("Titel", "Text")
        val tester = StateRestorationTester(compose)
        initPreview(app)
        tester.setContent { Theme(dark = true) { AgentPromptsScreen(onClickBack = {}, storeFactory = { newStore(it) }) } }
        compose.waitForIdle()
        val id = newStore().read().single().id
        click(PROMPTS_EDIT_TAG + id)
        compose.onNodeWithTag(PROMPTS_TITLE_FIELD_TAG).performTextInput("neu")
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput(" mehr")
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("Titelneu", nodeText(PROMPTS_TITLE_FIELD_TAG))
        assertEquals("Text mehr", nodeText(PROMPTS_TEXT_FIELD_TAG))
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertDoesNotExist() // still the editor, not the list
        assertEquals("Text", newStore().read().single().text) // nothing was saved by the restore
    }

    // ---- review of 74a: failures, editor hides the list, invalid text, preview length ----

    @Test fun aFailedDeleteShowsTheFailureNotice() {
        val a = newStore().add("a", "1").prompt!!
        show()
        click(PROMPTS_DELETE_TAG + a.id)
        accessible = false // locked meanwhile
        compose.onNodeWithTag(PROMPTS_DELETE_CONFIRM_TAG).performClick(); compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assert(hasText(app.getString(R.string.agent_prompts_save_failed)))
        accessible = true
        assertEquals(1, newStore().read().size)
    }

    @Test fun aDeleteOnAnUnreadableFileShowsTheFailureNotice() {
        val a = newStore().add("a", "1").prompt!!
        show()
        click(PROMPTS_DELETE_TAG + a.id)
        file.writeText("garbage")
        compose.onNodeWithTag(PROMPTS_DELETE_CONFIRM_TAG).performClick(); compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assert(hasText(app.getString(R.string.agent_prompts_save_failed)))
        assertEquals("garbage", file.readText())
    }

    @Test fun aFailedResetShowsItsNotice() {
        file.parentFile!!.mkdirs(); file.writeText("garbage")
        show()
        click(PROMPTS_RESET_TAG)
        accessible = false // locked before the confirmation
        compose.onNodeWithTag(PROMPTS_RESET_CONFIRM_TAG).performClick(); compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assert(hasText(app.getString(R.string.agent_prompts_reset_failed)))
        assertEquals("garbage", file.readText())
    }

    @Test fun aResetOfAFileThatHasBecomeReadableOnlyReloads() {
        file.parentFile!!.mkdirs(); file.writeText("garbage")
        show()
        click(PROMPTS_RESET_TAG)
        // repaired meanwhile
        file.writeText(PromptStore.encode(listOf(Prompt("id1", "t", "text"))))
        compose.onNodeWithTag(PROMPTS_RESET_CONFIRM_TAG).performClick(); compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_ROW_TAG + "id1").assertExists() // the prompt is still there and listed
        assertEquals(1, newStore().read().size)
    }

    @Test fun withTheEditorOpenNoRowAndNoRowButtonIsInTheTree() {
        val a = newStore().add("a", "1").prompt!!
        show()
        click(PROMPTS_EDIT_TAG + a.id)
        compose.onNodeWithTag(PROMPTS_ROW_TAG + a.id).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_EDIT_TAG + a.id).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_DELETE_TAG + a.id).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assertExists()
    }

    @Test fun theEditorShowsTheInvalidTextMessageForABrokenCharacter() {
        show()
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("ab\uD83D")
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.agent_prompts_invalid_text)).assertExists()
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
    }

    private fun texts(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        listOfNotNull(node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }) + node.children.flatMap { texts(it) }

    @Test fun aRowOfALongTextRendersThe300CharacterPreview() {
        val long = "w".repeat(1000)
        val a = newStore().add("Titel", long).prompt!!
        show()
        val shown = texts(compose.onNodeWithTag(PROMPTS_ROW_TAG + a.id, useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(shown.contains("Titel"))
        assertEquals(listOf(300), shown.filter { it.startsWith("w") }.map { it.length })
    }
}
