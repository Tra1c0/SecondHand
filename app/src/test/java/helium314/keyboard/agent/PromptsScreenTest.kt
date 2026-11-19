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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import helium314.keyboard.settings.screens.PROMPTS_FILTER_ALL_TAG
import helium314.keyboard.settings.screens.PROMPTS_FILTER_NONE_TAG
import helium314.keyboard.settings.screens.PROMPTS_FILTER_FOLDER_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_NEW_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_RENAME_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_DELETE_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_DELETE_CONFIRM_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_SORT_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_COUNT_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_NAME_FIELD_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_SAVE_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_CANCEL_TAG
import helium314.keyboard.settings.screens.PROMPTS_FOLDER_ERROR_TAG
import helium314.keyboard.settings.screens.PROMPTS_ROW_FOLDER_TAG
import helium314.keyboard.settings.screens.PROMPTS_PICK_NONE_TAG
import helium314.keyboard.settings.screens.PROMPTS_PICK_FOLDER_TAG
import helium314.keyboard.settings.screens.confirmFolderOrder
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

/** Stage 74a: the page "Ablage" (formerly "Prompt-Ablage") (empty state, editor card, delete, sort, paste, reset, store order). */
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
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assert(hasText("Noch keine Einträge."))
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
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assert(hasText("Diesen Eintrag gibt es nicht mehr."))
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

    @Test fun withTheListShownTheSystemBackLeavesThePage() {
        var left = 0
        initPreview(app)
        compose.setContent { Theme(dark = true) { AgentPromptsScreen(onClickBack = { left++ }, storeFactory = { newStore(it) }) } }
        compose.waitForIdle()
        back()
        assertEquals(1, left, "the page's back handler leaves the page")
        click(PROMPTS_ADD_TAG)
        back()
        assertEquals(1, left, "with the editor open the back only closes the editor")
        back()
        assertEquals(2, left, "the editor closed again: the back leaves the page")
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
        assertEquals("neuTitel", nodeText(PROMPTS_TITLE_FIELD_TAG)) // typed at the cursor, which is at the start of a title
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

    // ---- stage 77: folders ----

    private fun folder(store: PromptStore, name: String) = store.addFolder(name).folder!!
    private fun add(store: PromptStore, title: String, folder: PromptFolder?) = store.add(title, "text of $title", folder?.id).prompt!!
    private fun chip(f: PromptFolder) = PROMPTS_FILTER_FOLDER_TAG + f.id
    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).assertExists() }.isSuccess
    private fun newFolderThroughTheEditor(name: String) {
        click(PROMPTS_FOLDER_NEW_TAG)
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextInput(name)
        click(PROMPTS_FOLDER_SAVE_TAG)
    }

    @Test fun withoutFoldersThereIsNoChipRowAndOnlyNewFolderIsOffered() {
        val store = newStore()
        add(store, "a", null)
        show()
        assertFalse(exists(PROMPTS_FILTER_ALL_TAG))
        assertFalse(exists(PROMPTS_FILTER_NONE_TAG))
        assertFalse(exists(PROMPTS_FOLDER_COUNT_TAG))
        assertFalse(exists(PROMPTS_FOLDER_RENAME_TAG))
        assertFalse(exists(PROMPTS_FOLDER_DELETE_TAG))
        assertFalse(exists(PROMPTS_FOLDER_SORT_TAG))
        compose.onNodeWithTag(PROMPTS_FOLDER_NEW_TAG).assertExists().assertIsEnabled()
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("1 von 200"))
    }

    @Test fun aNewFolderIsMadeInTheEditorCardIsShownAtOnceAndStoredTrimmed() {
        val store = newStore()
        val p = add(store, "a", null)
        show()
        click(PROMPTS_FOLDER_NEW_TAG)
        // a card on the page: the list and its buttons are not in the tree meanwhile (P78 pattern)
        assertFalse(exists(PROMPTS_ROW_TAG + p.id))
        assertFalse(exists(PROMPTS_ADD_TAG))
        compose.onNodeWithTag(PROMPTS_FOLDER_SAVE_TAG).assertIsNotEnabled() // empty name
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextInput("  Arbeit ")
        click(PROMPTS_FOLDER_SAVE_TAG)
        val f = store.readFolders().single()
        assertEquals("Arbeit", f.name)
        compose.onNodeWithTag(chip(f)).assertIsSelected() // the new folder is the one shown, so the next prompt lands in it
        compose.onNodeWithTag(PROMPTS_FILTER_ALL_TAG).assertIsNotSelected()
        compose.onNodeWithTag(PROMPTS_FOLDER_COUNT_TAG).assert(hasText("1 von 50 Ordnern"))
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assert(hasText("In diesem Ordner sind noch keine Einträge."))
        assertFalse(exists(PROMPTS_ROW_TAG + p.id), "the prompt without folder is not in this folder")
        compose.onNodeWithTag(PROMPTS_FILTER_NONE_TAG).assertExists()
    }

    @Test fun theFolderNameIsCheckedWhileTypingAndNothingBadIsStored() {
        val store = newStore()
        folder(store, "Arbeit")
        show()
        click(PROMPTS_FOLDER_NEW_TAG)
        val field = compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG)
        field.performTextInput("ARBEIT")
        compose.onNodeWithTag(PROMPTS_FOLDER_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithText("Einen Ordner mit diesem Namen gibt es schon.").assertExists()
        field.performTextReplacement("x".repeat(PromptStore.MAX_FOLDER_NAME + 1))
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_FOLDER_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithText("Der Name ist zu lang (höchstens 40 Zeichen).").assertExists()
        field.performTextReplacement("x".repeat(PromptStore.MAX_FOLDER_NAME))
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_FOLDER_SAVE_TAG).assertIsEnabled()
        field.performTextReplacement("   ")
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_FOLDER_SAVE_TAG).assertIsNotEnabled()
        // cancel (the typed name is a change, so it asks) stores nothing and brings the list back
        click(PROMPTS_FOLDER_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("Arbeit"), store.readFolders().map { it.name })
        compose.onNodeWithTag(PROMPTS_FOLDER_NEW_TAG).assertExists()
    }

    @Test fun aStoreThatRefusesTheNameShowsTheReasonAndKeepsTheCard() {
        val store = newStore()
        show()
        click(PROMPTS_FOLDER_NEW_TAG)
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextInput("Neu")
        // the name is taken behind the page's back (the page still thinks it is free)
        folder(store, "neu")
        click(PROMPTS_FOLDER_SAVE_TAG)
        compose.onNodeWithTag(PROMPTS_FOLDER_ERROR_TAG).assert(hasText("Einen Ordner mit diesem Namen gibt es schon."))
        assertEquals(listOf("neu"), store.readFolders().map { it.name })
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).assertExists()
    }

    @Test fun theFiftiethFolderIsTheLastOne() {
        val store = newStore()
        repeat(PromptStore.MAX_FOLDERS - 1) { folder(store, "f$it") }
        show()
        compose.onNodeWithTag(PROMPTS_FOLDER_NEW_TAG).assertIsEnabled()
        newFolderThroughTheEditor("letzter")
        assertEquals(PromptStore.MAX_FOLDERS, store.readFolders().size)
        compose.onNodeWithTag(PROMPTS_FOLDER_NEW_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_FOLDER_COUNT_TAG).assert(hasText("50 von 50 Ordnern"))
    }

    @Test fun aChipFiltersTheListAndAllShowsEverythingWithTheFolderNameOnEachRow() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B")
        val pa = add(store, "pa", a); val pb = add(store, "pb", b); val pn = add(store, "pn", null)
        show()
        // all: every prompt, with the name of its folder
        for (p in listOf(pa, pb, pn)) compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).assertExists()
        compose.onNodeWithTag(PROMPTS_ROW_FOLDER_TAG + pa.id).assert(hasText("A"))
        assertFalse(exists(PROMPTS_ROW_FOLDER_TAG + pn.id))
        click(chip(a))
        compose.onNodeWithTag(chip(a)).assertIsSelected()
        compose.onNodeWithTag(PROMPTS_ROW_TAG + pa.id).assertExists()
        assertFalse(exists(PROMPTS_ROW_TAG + pb.id))
        assertFalse(exists(PROMPTS_ROW_TAG + pn.id))
        assertFalse(exists(PROMPTS_ROW_FOLDER_TAG + pa.id), "inside a folder the row needs no folder name")
        click(PROMPTS_FILTER_NONE_TAG)
        compose.onNodeWithTag(PROMPTS_ROW_TAG + pn.id).assertExists()
        assertFalse(exists(PROMPTS_ROW_TAG + pa.id))
        // the total count stays the total
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("3 von 200"))
        click(PROMPTS_FILTER_ALL_TAG)
        for (p in listOf(pa, pb, pn)) compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).assertExists()
    }

    @Test fun theChipsFollowTheFolderOrderAndNoFolderShowsOnlyWhenSomePromptHasNone() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B")
        add(store, "pa", a)
        store.reorderFolders(listOf(b.id, a.id))
        show()
        val tops = listOf(PROMPTS_FILTER_ALL_TAG, chip(b), chip(a)).map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(tops.sorted(), tops, "$tops")
        assertEquals(3, tops.toSet().size)
        assertFalse(exists(PROMPTS_FILTER_NONE_TAG))
    }

    @Test fun renamingAFolderKeepsItsPlaceAndItsPrompts() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B")
        val p = add(store, "p", a)
        show()
        click(chip(a))
        click(PROMPTS_FOLDER_RENAME_TAG)
        assertEquals("A", fieldText(PROMPTS_FOLDER_NAME_FIELD_TAG))
        // another folder's name is refused, the own name in another spelling is fine
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextReplacement("b")
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_FOLDER_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextReplacement(" Büro ")
        click(PROMPTS_FOLDER_SAVE_TAG)
        assertEquals(listOf("Büro", "B"), store.readFolders().map { it.name })
        assertEquals(a.id, store.readFolders().first().id)
        assertEquals(a.id, store.read().single { it.id == p.id }.folderId)
        compose.onNodeWithTag(chip(a)).assertIsSelected()
        compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).assertExists()
        // the rename button belongs to a shown folder only
        click(PROMPTS_FILTER_ALL_TAG)
        assertFalse(exists(PROMPTS_FOLDER_RENAME_TAG))
        assertFalse(exists(PROMPTS_FOLDER_DELETE_TAG))
        assertEquals(b.id, store.readFolders()[1].id)
    }

    @Test fun deletingAFolderAsksSaysWhereThePromptsGoAndKeepsThem() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B")
        val p1 = add(store, "p1", a); val p2 = add(store, "p2", a); val p3 = add(store, "p3", b)
        show()
        click(chip(a))
        click(PROMPTS_FOLDER_DELETE_TAG)
        compose.onNodeWithText("Den Ordner „A“ löschen? Seine Einträge (2) werden nicht gelöscht, sie wandern nach „Ohne Ordner“.").assertExists()
        // a cancelled dialog deletes nothing
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(2, store.readFolders().size)
        click(PROMPTS_FOLDER_DELETE_TAG)
        assertEquals(2, store.readFolders().size)
        compose.onNodeWithTag(PROMPTS_FOLDER_DELETE_CONFIRM_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("B"), store.readFolders().map { it.name })
        assertEquals(setOf(p1.id, p2.id, p3.id), store.read().map { it.id }.toSet(), "no prompt was deleted")
        assertEquals(listOf(null, null, b.id), store.read().sortedBy { listOf(p1.id, p2.id, p3.id).indexOf(it.id) }.map { it.folderId })
        // the page falls back to all and the prompts are listed, the ones of the deleted folder as "no folder"
        compose.onNodeWithTag(PROMPTS_FILTER_ALL_TAG).assertIsSelected()
        for (p in listOf(p1, p2, p3)) compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).assertExists()
        click(PROMPTS_FILTER_NONE_TAG)
        compose.onNodeWithTag(PROMPTS_ROW_TAG + p1.id).assertExists()
        compose.onNodeWithTag(PROMPTS_ROW_TAG + p2.id).assertExists()
        assertFalse(exists(PROMPTS_ROW_TAG + p3.id))
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("3 von 200"))
    }

    @Test fun deletingTheLastFolderRemovesTheChipRowAndKeepsEveryPrompt() {
        val store = newStore()
        val a = folder(store, "A")
        val p = add(store, "p", a)
        show()
        click(chip(a))
        click(PROMPTS_FOLDER_DELETE_TAG)
        compose.onNodeWithText("Den Ordner „A“ löschen? Seine Einträge (1) werden nicht gelöscht, sie wandern nach „Ohne Ordner“.").assertExists()
        compose.onNodeWithTag(PROMPTS_FOLDER_DELETE_CONFIRM_TAG).performClick()
        compose.waitForIdle()
        assertTrue(store.readFolders().isEmpty())
        assertEquals(listOf(p.id), store.read().map { it.id })
        assertFalse(exists(PROMPTS_FILTER_ALL_TAG))
        compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).assertExists()
    }

    @Test fun aFolderDeletedBehindThePagesBackIsReportedOnDelete() {
        val store = newStore()
        val a = folder(store, "A")
        show()
        click(chip(a))
        click(PROMPTS_FOLDER_DELETE_TAG)
        store.deleteFolder(a.id)
        compose.onNodeWithTag(PROMPTS_FOLDER_DELETE_CONFIRM_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assert(hasText("Diesen Ordner gibt es nicht mehr."))
    }

    @Test fun foldersCanBeSortedOnlyFromTwoOnAndTheDialogListsThem() {
        val store = newStore()
        val a = folder(store, "Alpha")
        show()
        assertFalse(exists(PROMPTS_FOLDER_SORT_TAG))
        newFolderThroughTheEditor("Beta")
        click(PROMPTS_FOLDER_SORT_TAG)
        // each name is on its chip and in the dialog
        compose.onAllNodesWithText("Alpha").assertCountEquals(2)
        compose.onAllNodesWithText("Beta").assertCountEquals(2)
        compose.onNodeWithText("OK").performClick() // unchanged order
        compose.waitForIdle()
        assertEquals(listOf("Alpha", "Beta"), store.readFolders().map { it.name })
        assertEquals(a.id, store.readFolders().first().id)
    }

    @Test fun theFolderOrderIsSavedAndAStaleOneIsReported() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B"); val c = folder(store, "C")
        val p = add(store, "p", b)
        assertEquals(0, confirmFolderOrder(store, listOf(c.id, a.id, b.id)))
        assertEquals(listOf("C", "A", "B"), store.readFolders().map { it.name })
        assertEquals(b.id, store.read().single { it.id == p.id }.folderId)
        store.deleteFolder(a.id)
        assertEquals(R.string.agent_prompts_sort_changed, confirmFolderOrder(store, listOf(a.id, b.id, c.id)))
        assertEquals(listOf("C", "B"), store.readFolders().map { it.name })
        accessible = false
        assertEquals(R.string.agent_prompts_sort_failed, confirmFolderOrder(store, listOf(b.id, c.id)))
        // and the page shows the saved order
        accessible = true
        show()
        val lefts = listOf(PROMPTS_FILTER_ALL_TAG, chip(c), chip(b)).map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(lefts.sorted(), lefts, "$lefts")
    }

    @Test fun sortingInsideAFolderListsOnlyItsPromptsAndChangesOnlyTheirPlaces() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B")
        // global order: n2 a3 b1 a2 n1 a1
        val a1 = add(store, "a1", a); val n1 = add(store, "n1", null); val a2 = add(store, "a2", a)
        val b1 = add(store, "b1", b); val a3 = add(store, "a3", a); val n2 = add(store, "n2", null)
        show()
        click(chip(a))
        click(PROMPTS_SORT_TAG)
        // a title is in the row on the page and in the dialog; b1 and n1 are on neither if the dialog lists only this folder
        compose.onAllNodesWithText("a3").assertCountEquals(2)
        compose.onAllNodesWithText("a1").assertCountEquals(2)
        assertFalse(runCatching { compose.onNodeWithText("b1").assertExists() }.isSuccess, "only the prompts of the shown folder are in the dialog")
        assertFalse(runCatching { compose.onNodeWithText("n1").assertExists() }.isSuccess)
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        // what the confirmed dialog does: only the places of A's prompts are permuted
        assertEquals(0, confirmPromptOrder(store, PromptFilter.Folder(a.id), listOf(a1.id, a3.id, a2.id)))
        assertEquals(listOf("n2", "a1", "b1", "a3", "n1", "a2"), store.read().map { it.title })
        assertEquals(0, confirmPromptOrder(store, PromptFilter.NoFolder, listOf(n1.id, n2.id)))
        assertEquals(listOf("n1", "a1", "b1", "a3", "n2", "a2"), store.read().map { it.title })
        // a stale list (a prompt of the folder was deleted meanwhile) is reported and saves nothing
        store.delete(a3.id)
        val before = store.read().map { it.id }
        assertEquals(R.string.agent_prompts_sort_changed, confirmPromptOrder(store, PromptFilter.Folder(a.id), listOf(a1.id, a3.id, a2.id)))
        assertEquals(R.string.agent_prompts_sort_changed, confirmPromptOrder(store, PromptFilter.Folder("gone"), emptyList()))
        assertEquals(before, store.read().map { it.id })
        // "all" still orders everything
        assertEquals(0, confirmPromptOrder(store, PromptFilter.All, before.reversed()))
        assertEquals(before.reversed(), store.read().map { it.id })
        assertEquals(b1.id, store.read().single { it.folderId == b.id }.id)
    }

    @Test fun aPromptIsMovedBetweenFoldersInTheEditorAndKeepsItsPlace() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B")
        val p1 = add(store, "p1", a); val p2 = add(store, "p2", null)
        show()
        click(PROMPTS_EDIT_TAG + p1.id)
        // the picker shows the folder of the prompt
        compose.onNodeWithTag(PROMPTS_PICK_FOLDER_TAG + a.id).assertIsSelected()
        compose.onNodeWithTag(PROMPTS_PICK_NONE_TAG).assertIsNotSelected()
        click(PROMPTS_PICK_FOLDER_TAG + b.id)
        click(PROMPTS_SAVE_TAG)
        assertEquals(b.id, store.read().single { it.id == p1.id }.folderId)
        assertEquals(listOf(p2.id, p1.id), store.read().map { it.id }, "the place in the global order is kept")
        assertEquals("text of p1", store.read().single { it.id == p1.id }.text)
        // and out of every folder
        click(PROMPTS_EDIT_TAG + p1.id)
        compose.onNodeWithTag(PROMPTS_PICK_FOLDER_TAG + b.id).assertIsSelected()
        click(PROMPTS_PICK_NONE_TAG)
        click(PROMPTS_SAVE_TAG)
        assertEquals(null, store.read().single { it.id == p1.id }.folderId)
        // a plain edit of the text keeps the folder
        click(PROMPTS_EDIT_TAG + p2.id)
        click(PROMPTS_PICK_FOLDER_TAG + a.id)
        click(PROMPTS_SAVE_TAG)
        assertEquals(a.id, store.read().single { it.id == p2.id }.folderId)
        click(PROMPTS_EDIT_TAG + p2.id)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput(" mehr")
        click(PROMPTS_SAVE_TAG)
        assertEquals(a.id, store.read().single { it.id == p2.id }.folderId)
    }

    @Test fun theEditorHasNoFolderPickerWithoutFolders() {
        val store = newStore()
        val p = add(store, "p", null)
        show()
        click(PROMPTS_EDIT_TAG + p.id)
        assertFalse(exists(PROMPTS_PICK_NONE_TAG))
        click(PROMPTS_SAVE_TAG)
        assertEquals(null, store.read().single().folderId)
    }

    @Test fun aNewPromptStartsInTheFolderShownAndInNoFolderOtherwise() {
        val store = newStore()
        val a = folder(store, "A"); val b = folder(store, "B")
        show()
        // all: no folder
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_PICK_NONE_TAG).assertIsSelected()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("eins")
        click(PROMPTS_SAVE_TAG)
        assertEquals(null, store.read().single().folderId)
        // a folder shown: the picker starts on it and the prompt is stored there
        click(chip(a))
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_PICK_FOLDER_TAG + a.id).assertIsSelected()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("zwei")
        click(PROMPTS_SAVE_TAG)
        assertEquals(a.id, store.read().first().folderId)
        assertEquals("zwei", store.read().first().text)
        // the default can be changed in the editor
        click(PROMPTS_ADD_TAG)
        click(PROMPTS_PICK_FOLDER_TAG + b.id)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("drei")
        click(PROMPTS_SAVE_TAG)
        assertEquals(b.id, store.read().first().folderId)
        // "no folder" shown: no folder
        click(PROMPTS_FILTER_NONE_TAG)
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_PICK_NONE_TAG).assertIsSelected()
    }

    @Test fun changingOnlyTheFolderCountsAsAChangeForCancel() {
        val store = newStore()
        val a = folder(store, "A")
        val p = add(store, "p", null)
        show()
        click(PROMPTS_EDIT_TAG + p.id)
        click(PROMPTS_CANCEL_TAG) // unchanged: closes at once
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertExists()
        click(PROMPTS_EDIT_TAG + p.id)
        click(PROMPTS_PICK_FOLDER_TAG + a.id)
        click(PROMPTS_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).performClick()
        compose.waitForIdle()
        assertEquals(null, store.read().single().folderId, "nothing was saved")
    }

    @Test fun aFolderDeletedWhileThePromptEditorIsOpenIsReportedAndTheSaveGoesToNoFolder() {
        val store = newStore()
        val a = folder(store, "A")
        show()
        click(chip(a))
        click(PROMPTS_ADD_TAG)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("Text")
        store.deleteFolder(a.id)
        click(PROMPTS_SAVE_TAG)
        compose.onNodeWithTag(PROMPTS_NOTICE_TAG).assert(hasText("Diesen Ordner gibt es nicht mehr."))
        assertTrue(store.read().isEmpty(), "nothing was saved into a folder that is gone")
        // the picker was reloaded without the folder: saving again stores the prompt without folder
        assertFalse(exists(PROMPTS_PICK_NONE_TAG))
        click(PROMPTS_SAVE_TAG)
        assertEquals(null, store.read().single().folderId)
    }

    @Test fun theChosenChipAndTheFolderEditorSurviveAnActivityRestore() {
        val store = newStore()
        val a = folder(store, "A"); folder(store, "B")
        add(store, "p", a)
        val tester = StateRestorationTester(compose)
        initPreview(app)
        tester.setContent { Theme(dark = true) { AgentPromptsScreen(onClickBack = {}, storeFactory = { newStore(it) }) } }
        compose.waitForIdle()
        click(chip(a))
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag(chip(a)).assertIsSelected()
        click(PROMPTS_FOLDER_RENAME_TAG)
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextInput("x")
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("Ax", fieldText(PROMPTS_FOLDER_NAME_FIELD_TAG)) // the cursor of an existing name is at its end
        assertEquals("A", store.readFolders().first().name, "nothing was saved by the restore")
    }

    @Test fun anUnusableFileWithAMissingFolderShowsTheResetDeadEndAndNoFolderControls() {
        file.parentFile!!.mkdirs()
        val bad = """{"storageVersion":2,"folders":[{"id":"f1","name":"A"}],"prompts":[{"id":"p","title":"","text":"x","folderId":"f2"}]}"""
        file.writeText(bad)
        show()
        compose.onNodeWithTag(PROMPTS_UNAVAILABLE_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_RESET_TAG).assertExists()
        assertFalse(exists(PROMPTS_FOLDER_NEW_TAG))
        assertFalse(exists(PROMPTS_FILTER_ALL_TAG))
        assertEquals(bad, file.readText(), "the page changed nothing")
    }

    @Test fun aChangedFolderNameAsksBeforeCancelAndBackDiscardAndUnchangedClosesAtOnce() {
        val store = newStore()
        val a = folder(store, "A")
        show()
        click(chip(a))
        // unchanged: closes at once
        click(PROMPTS_FOLDER_RENAME_TAG)
        click(PROMPTS_FOLDER_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_FOLDER_NEW_TAG).assertExists()
        click(PROMPTS_FOLDER_RENAME_TAG)
        back()
        compose.onNodeWithTag(PROMPTS_FOLDER_NEW_TAG).assertExists()
        // changed: Cancel asks, "keep editing" keeps the typed name
        click(PROMPTS_FOLDER_RENAME_TAG)
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextInput("zwei")
        click(PROMPTS_FOLDER_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_KEEP_EDITING_TAG).performClick()
        compose.waitForIdle()
        assertEquals("Azwei", fieldText(PROMPTS_FOLDER_NAME_FIELD_TAG))
        // Back asks the same; discarding closes and saves nothing
        back()
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_FOLDER_NEW_TAG).assertExists()
        assertEquals("A", store.readFolders().single().name)
        // a new folder with a typed name asks too
        click(PROMPTS_FOLDER_NEW_TAG)
        compose.onNodeWithTag(PROMPTS_FOLDER_NAME_FIELD_TAG).performTextInput("neu")
        click(PROMPTS_FOLDER_CANCEL_TAG)
        compose.onNodeWithTag(PROMPTS_DISCARD_TAG).assertExists()
    }

    @Test fun theFolderDeleteDialogCountsWhatTheStoreHoldsWhenItOpens() {
        val store = newStore()
        val a = folder(store, "A")
        add(store, "p1", a)
        show()
        click(chip(a))
        // the keyboard window (or another page) changed the store meanwhile: two more prompts in the folder
        add(store, "p2", a); add(store, "p3", a)
        click(PROMPTS_FOLDER_DELETE_TAG)
        compose.onNodeWithText("Den Ordner „A“ löschen? Seine Einträge (3) werden nicht gelöscht, sie wandern nach „Ohne Ordner“.").assertExists()
    }

    @Test fun thePageReloadsTheStoreWhenItResumes() {
        val store = newStore()
        show()
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("0 von 200"))
        val p = add(store, "p", null) // changed by the keyboard window while the page was away
        compose.runOnUiThread { compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED) }
        compose.runOnUiThread { compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED) }
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).assertExists()
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("1 von 200"))
    }
}
