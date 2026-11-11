// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import dev.agentkeyboard.core.*
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.settings.screens.AgentWorkflowsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
class WorkflowSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun damagedConfigurationRequiresAnExplicitSecondConfirmationBeforeRecovery() {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        shadowOf(app.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        val file = File(credentialContext(app).noBackupFilesDir, "agent_workflows.json")
        File(file.path + ".bak").delete()
        file.writeText("damaged")
        val store = WorkflowStore(app)
        compose.setContent { MaterialTheme { AgentWorkflowsScreen {} } }
        assertNotNull(store.recoveryToken())
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Reset unreadable workflows") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Create workflow").assertIsNotEnabled()
        compose.onNodeWithText("Reset unreadable workflows").performScrollTo().performClick()
        assertEquals("damaged", file.readText())
        compose.onNodeWithText("Delete damaged configuration").assertIsDisplayed()
        captureDialog("workflow-recovery")
        compose.onNodeWithText(app.getString(android.R.string.cancel)).performClick()
        assertEquals("damaged", file.readText())
        compose.onNodeWithText("Reset unreadable workflows").performScrollTo().performClick()
        compose.onNodeWithText("Delete damaged configuration").performClick()
        compose.waitUntil(10_000) { runCatching { store.read().isEmpty() }.getOrDefault(false) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Create workflow") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reset unreadable workflows").assertDoesNotExist()
    }

    @Test fun laterStepsCanSelectAnEarlierResultInsteadOfOnlyThePreviousOne() {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        shadowOf(app.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        val file = File(credentialContext(app).noBackupFilesDir, "agent_workflows.json")
        file.delete(); File(file.path + ".bak").delete()
        val store = WorkflowStore(app)
        compose.setContent { MaterialTheme { AgentWorkflowsScreen {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No saved workflows.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create workflow").performClick()
        compose.onNodeWithText("Workflow name").performTextInput("Earlier result")
        compose.onNodeWithText("Add step").performScrollTo().performClick()
        compose.onNodeWithText("Result of step 2").performScrollTo().performClick()
        compose.onAllNodesWithText("Result of step 1").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Save workflow").performClick()
        compose.waitUntil(10_000) { store.read().isNotEmpty() }
        val definition = store.read().single()
        assertEquals(3, definition.steps.size)
        assertEquals(WorkflowInput.StepResult(definition.steps.first().id), definition.steps.last().input)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun conditionEditorStoresAValidatedRuleOnlyAfterSave() {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        shadowOf(app.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        val file = File(credentialContext(app).noBackupFilesDir, "agent_workflows.json")
        file.delete(); File(file.path + ".bak").delete()
        val store = WorkflowStore(app)
        compose.setContent { MaterialTheme { AgentWorkflowsScreen {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No saved workflows.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Create workflow").performClick()
        compose.onNodeWithText("Workflow name").performTextInput("Conditional entry")
        compose.onAllNodesWithText("Always run").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Contains text").performClick()
        assertEquals(0, store.read().size)
        compose.onNodeWithText("Save workflow").assertIsNotEnabled()
        compose.onNodeWithText("Comparison text").performScrollTo().performTextInput("marker")
        compose.onNodeWithContentDescription("Invert condition").performScrollTo().performClick()
        compose.onNodeWithText("Contains text").performScrollTo().performClick()
        compose.onAllNodesWithText("Contains text").onLast().performClick() // Same choice must preserve the rule.
        captureDialog("workflow-condition-editor")
        compose.onNodeWithText("Save workflow").performClick()
        compose.waitUntil(10_000) { store.read().isNotEmpty() }
        val rule = store.read().single().steps.first().condition
        assertNotNull(rule)
        assertEquals(TextPredicate.CONTAINS_LITERAL, rule.predicate)
        assertEquals("marker", rule.literal)
        assertTrue(rule.negated)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun pickerReturnAfterStopShowsAReviewAndNeverActivatesWithoutASecondClick() {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        shadowOf(app.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        val file = File(credentialContext(app).noBackupFilesDir, "agent_workflows.json")
        file.delete(); File(file.path + ".bak").delete()
        val store = WorkflowStore(app)
        val transportInitializedBefore = helium314.keyboard.agent.providers.CloudHttpClient.isInitialized
        fun definition(name: String) = WorkflowDefinition(UUID.randomUUID().toString(), 1, name, listOf(
            WorkflowStep.Local("step1", WorkflowInput.Selection, LocalTextAction.CLEAN_SPACES)))
        store.save(definition("Original"), expectedGeneration = store.snapshot().generation)
        val incoming = definition("Imported")
        val uri = Uri.parse("content://agent.test.documents/workflows.json")
        shadowOf(app.contentResolver).registerInputStream(uri,
            ByteArrayInputStream(WorkflowStore.encode(listOf(incoming)).toByteArray()))
        compose.setContent { MaterialTheme { AgentWorkflowsScreen {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Original").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Import file").performClick()
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnUiThread {
            compose.activity.activityResultRegistry.dispatchResult(started.requestCode, Activity.RESULT_OK, Intent().setData(uri))
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Review workflow import").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Original", store.read().single().name)
        compose.onNodeWithText("Imported").assertExists()
        assertEquals(transportInitializedBefore, helium314.keyboard.agent.providers.CloudHttpClient.isInitialized)
        captureDialog("workflow-import")
        compose.onNodeWithText("Activate configuration").performClick()
        compose.waitUntil(10_000) { store.read().single().id == incoming.id }
        assertEquals("Imported", store.read().single().name)
    }

    private fun captureDialog(name: String) {
        val preview = File("build/agent-previews/$name.png").apply { parentFile!!.mkdirs() }
        compose.runOnUiThread {
            val view = ShadowDialog.getLatestDialog().window!!.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
