// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assert
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AgentPromptsScreen
import helium314.keyboard.settings.screens.PROMPTS_ADD_TAG
import helium314.keyboard.settings.screens.PROMPTS_COUNT_TAG
import helium314.keyboard.settings.screens.PROMPTS_DELETE_CONFIRM_TAG
import helium314.keyboard.settings.screens.PROMPTS_DELETE_TAG
import helium314.keyboard.settings.screens.PROMPTS_EDIT_TAG
import helium314.keyboard.settings.screens.PROMPTS_EMPTY_TAG
import helium314.keyboard.settings.screens.PROMPTS_PASTE_TAG
import helium314.keyboard.settings.screens.PROMPTS_ROW_TAG
import helium314.keyboard.settings.screens.PROMPTS_SAVE_TAG
import helium314.keyboard.settings.screens.PROMPTS_SORT_TAG
import helium314.keyboard.settings.screens.PROMPTS_TEXT_FIELD_TAG
import helium314.keyboard.settings.screens.PROMPTS_TITLE_FIELD_TAG
import helium314.keyboard.settings.screens.PROMPTS_UNAVAILABLE_TAG
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
import kotlin.test.assertTrue

/** Stage 74a: the page "Prompt-Ablage" (empty state, add through the editor, delete after confirm, store order). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h2400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class PromptsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private var accessible = true
    private fun newStore(context: Context = app) = PromptStore(context) { accessible }
    private val file get() = File(credentialContext(app).noBackupFilesDir, PromptStore.FILE_NAME)

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

    private fun addThroughEditor(title: String, text: String) {
        compose.onNodeWithTag(PROMPTS_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        if (title.isNotEmpty()) compose.onNodeWithTag(PROMPTS_TITLE_FIELD_TAG).performTextInput(title)
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput(text)
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).performClick()
        compose.waitForIdle()
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
        compose.onNodeWithTag(PROMPTS_ROW_TAG + stored.id).assert(hasText("Mail")).assert(hasText("Schreibe eine freundliche Mail"))
        compose.onNodeWithTag(PROMPTS_EMPTY_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROMPTS_COUNT_TAG).assert(hasText("1 von 200"))
    }

    @Test fun aPromptWithoutATitleShowsTheFirstLineOfItsText() {
        newStore().add("", "\nErste Zeile\nzweite Zeile")
        show()
        val id = newStore().read().single().id
        compose.onNodeWithTag(PROMPTS_ROW_TAG + id).assert(hasText("Erste Zeile"))
    }

    @Test fun theSaveButtonNeedsTextAndRespectsTheLimits() {
        show()
        compose.onNodeWithTag(PROMPTS_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("   ")
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("x")
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsEnabled()
        compose.onNodeWithTag(PROMPTS_TITLE_FIELD_TAG).performTextInput("t".repeat(PromptStore.MAX_TITLE + 1))
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
        assertTrue(newStore().read().isEmpty())
    }

    @Test fun editKeepsThePosition() {
        val store = newStore()
        store.add("eins", "1"); store.add("zwei", "2")
        show()
        val second = store.read()[1]
        compose.onNodeWithTag(PROMPTS_EDIT_TAG + second.id).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).performTextInput("!")
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).performClick()
        compose.waitForIdle()
        assertEquals(listOf("zwei", "eins"), store.read().map { it.title })
        assertEquals("1!", store.read()[1].text)
    }

    @Test fun deleteNeedsTheConfirmation() {
        val store = newStore()
        val a = store.add("a", "1").prompt!!
        show()
        compose.onNodeWithTag(PROMPTS_DELETE_TAG + a.id).performScrollTo().performClick()
        compose.waitForIdle()
        // a cancelled dialog deletes nothing
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(1, store.read().size)
        compose.onNodeWithTag(PROMPTS_DELETE_TAG + a.id).performScrollTo().performClick()
        compose.waitForIdle()
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
        val tops = listOf(b, a, c).map { p ->
            compose.onNodeWithTag(PROMPTS_ROW_TAG + p.id).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops, "$tops")
        assertEquals(3, tops.toSet().size)
    }

    @Test fun lockedDeviceShowsTheHintAndNoList() {
        newStore().add("a", "1")
        accessible = false
        show()
        compose.onNodeWithTag(PROMPTS_UNAVAILABLE_TAG).assertExists()
        compose.onNodeWithTag(PROMPTS_ADD_TAG).assertDoesNotExist()
    }

    @Test fun pasteFromClipboardNeedsATapAndRespectsTheLimit() {
        val manager = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("t", "aus der Zwischenablage"))
        show()
        compose.onNodeWithTag(PROMPTS_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_PASTE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assert(hasText("aus der Zwischenablage", substring = true))
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).performClick()
        compose.waitForIdle()
        assertEquals("aus der Zwischenablage", newStore().read().single().text)

        // too long: nothing is inserted
        manager.setPrimaryClip(ClipData.newPlainText("t", "y".repeat(PromptStore.MAX_TEXT + 1)))
        compose.onNodeWithTag(PROMPTS_ADD_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_PASTE_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(PROMPTS_SAVE_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROMPTS_TEXT_FIELD_TAG).assert(hasText("y").not())
    }
}
