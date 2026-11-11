// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.agentkeyboard.core.*
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentSkillsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.UUID
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
class SkillSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun prepare(): Application {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        shadowOf(app.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        for (name in listOf("agent_skill_keys.json", "agent_workflows.json")) {
            val file = File(credentialContext(app).noBackupFilesDir, name)
            file.delete(); File(file.path + ".bak").delete()
        }
        return app
    }

    private fun workflow(name: String) = WorkflowDefinition(UUID.randomUUID().toString(), 1, name,
        listOf(WorkflowStep.Local("first", WorkflowInput.Selection, LocalTextAction.UPPERCASE)))

    @Test fun choosingAWorkflowSavesOnlyItsReferenceAndAValidUnicodeLabel() {
        val app = prepare()
        val originalPreferences = app.prefs().all.toMap()
        val workflow = workflow("x".repeat(31) + "😀")
        val workflows = WorkflowStore(app)
        workflows.save(workflow, expectedGeneration = workflows.snapshot().generation)
        val before = workflows.export()
        val skills = SkillBindingStore(app)
        compose.setContent { MaterialTheme { AgentSkillsScreen {} } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("agent_skill_action_1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("agent_skill_action_1").performScrollTo().performClick()
        compose.onNodeWithText("Saved workflow").performScrollTo().performClick()
        compose.onNodeWithTag("agent_skill_action_1").assertTextContains("Saved workflow")
        compose.onNodeWithTag("agent_skill_save_1").assertIsNotEnabled()
        compose.onNodeWithTag("agent_skill_workflow_1").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(workflow.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(workflow.name).performClick()
        compose.onNodeWithTag("agent_skill_save_1").performScrollTo().performClick()
        compose.waitUntil(10_000) { skills.read().first().action == BuiltinAction.WORKFLOW }
        val binding = skills.read().first()
        assertEquals(workflow.id, binding.workflowId)
        assertEquals("x".repeat(31), binding.label)
        assertEquals(before, workflows.export())
        assertEquals(originalPreferences, app.prefs().all)
    }

    @Test fun aMissingImportedReferenceIsPreservedInsteadOfSelectingTheFirstWorkflow() {
        val app = prepare()
        val workflows = WorkflowStore(app)
        workflows.save(workflow("Some other workflow"), expectedGeneration = workflows.snapshot().generation)
        val missing = UUID.randomUUID().toString()
        val skills = SkillBindingStore(app)
        skills.set(SkillBinding(1, "Missing reference", BuiltinAction.WORKFLOW, missing))
        compose.setContent { MaterialTheme { AgentSkillsScreen {} } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Missing reference").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("agent_skill_label_1").performScrollTo().performTextClearance()
        compose.onNodeWithTag("agent_skill_label_1").performTextInput("Renamed missing")
        compose.onNodeWithTag("agent_skill_save_1").performScrollTo().performClick()
        compose.waitUntil(10_000) { skills.read().first().label == "Renamed missing" }
        assertEquals(missing, skills.read().first().workflowId)
        assertEquals(BuiltinAction.WORKFLOW, skills.read().first().action)
    }
}
