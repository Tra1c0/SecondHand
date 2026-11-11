// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.UserManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.CloudHttpClient
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.UUID
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkflowBuilderSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Provider(private val respond: suspend () -> String) : ModelProvider {
        override val id = "openai"
        override val capabilities = ProviderCapabilities(workflowDrafts = true)
        val requests = mutableListOf<ModelRequest>()
        override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
            requests.add(request); return ModelResponse(respond())
        }
    }
    private fun proposal() = """{"name":"Generated draft","steps":[{"id":"correct","input":"selection","condition":null,"kind":"ai","action":"ai.correct","language":"de","tone":"PROFESSIONAL"}]}"""
    private fun prepare(): Application {
        val app: Application = RuntimeEnvironment.getApplication()
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(false)
        shadowOf(app.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        val file = File(credentialContext(app).noBackupFilesDir, "agent_workflows.json")
        file.delete(); File(file.path + ".bak").delete()
        ModelSettings(app).setOpenAiModel("approved-model")
        return app
    }
    private fun screen(backend: Provider, journal: TestRunJournal,
                       registry: SaveableStateRegistry = SaveableStateRegistry(null) { true }) {
        val factory: WorkflowDraftRunnerFactory = { scope, allowed -> WorkflowDraftRunner(scope, { backend }, allowed, journal) }
        compose.setContent {
            CompositionLocalProvider(LocalWorkflowDraftRunnerFactory provides factory,
                LocalSaveableStateRegistry provides registry) { MaterialTheme { AgentWorkflowsScreen {} } }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No saved workflows.").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openBuilder(prompt: String) {
        compose.onNodeWithText("Design with AI").performScrollTo().performClick()
        compose.onNodeWithTag("agent_builder_prompt").performTextInput(prompt)
        compose.onNodeWithText("Review sending").performClick()
        compose.onNodeWithText("Review builder request").assertIsDisplayed()
    }

    @Test fun sendingAndSavingAreSeparateAndTheDisplayedModelIsFrozen() {
        val app = prepare(); val store = WorkflowStore(app); val journal = TestRunJournal()
        val response = CompletableDeferred<String>(); val backend = Provider { response.await() }
        val initialized = CloudHttpClient.isInitialized
        screen(backend, journal)
        openBuilder("Correct the selected text")
        assertTrue(backend.requests.isEmpty()); assertTrue(journal.states.isEmpty()); assertTrue(store.read().isEmpty())
        compose.onNodeWithTag("agent_builder_approved_prompt").assertTextEquals("Correct the selected text")
        ModelSettings(app).setOpenAiModel("changed-model")
        captureDialog("builder-send-review")
        compose.onNodeWithText("Send to OpenAI").performClick()
        compose.waitUntil(10_000) { backend.requests.size == 1 }
        compose.onNodeWithText("Send to OpenAI").assertIsNotEnabled()
        assertEquals("approved-model", backend.requests.single().model)
        assertEquals(ModelOutputFormat.WORKFLOW_DRAFT, backend.requests.single().outputFormat)
        compose.runOnUiThread { response.complete(proposal()) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Review workflow draft").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(store.read().isEmpty())
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertTrue(ShadowDialog.getLatestDialog().window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        assertEquals(ActionRunState.SUCCEEDED, journal.states.values.single())
        compose.onNodeWithText("approved-model").assertExists()
        captureDialog("builder-draft-review")
        compose.onNodeWithText("Save workflow").performClick()
        compose.waitUntil(10_000) { store.read().isNotEmpty() }
        val saved = store.read().single()
        assertEquals("Generated draft", saved.name); assertEquals(1, saved.revision)
        assertEquals("approved-model", (saved.steps.single() as WorkflowStep.Ai).model)
        assertEquals(1, backend.requests.size)
        assertEquals(initialized, CloudHttpClient.isInitialized)
    }

    @Test fun anInterveningConfigurationChangeCannotBeOverwrittenBySavingTheDraft() {
        val app = prepare(); val store = WorkflowStore(app); val journal = TestRunJournal()
        val backend = Provider { proposal() }
        screen(backend, journal); openBuilder("Correct text")
        compose.onNodeWithText("Send to OpenAI").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Review workflow draft").fetchSemanticsNodes().isNotEmpty() }
        val imported = WorkflowDefinition(UUID.randomUUID().toString(), 1, "New import", listOf(
            WorkflowStep.Local("clean", WorkflowInput.Selection, LocalTextAction.CLEAN_SPACES)))
        store.replaceAll(listOf(imported), store.snapshot().generation)
        val before = store.export()
        compose.onNodeWithText("Save workflow").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(app.getString(helium314.keyboard.latin.R.string.agent_workflow_error)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before, store.export())
        assertEquals(imported.id, store.read().single().id)
        assertEquals(1, backend.requests.size)
    }

    @Test fun incognitoClosesTheApprovalAndNoPromptEntersSavedInstanceState() {
        val app = prepare(); val journal = TestRunJournal(); val backend = Provider { proposal() }
        val registry = SaveableStateRegistry(null) { true }
        val prompt = "private-builder-request-63941"
        val initialSecure = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE
        screen(backend, journal, registry); openBuilder(prompt)
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        compose.onNodeWithTag("agent_builder_approved_prompt").assertTextEquals(prompt)
        val saved = compose.runOnIdle { registry.performSave() }
        assertFalse(strings(saved).any { prompt in it })
        compose.runOnUiThread { app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("agent_builder_approved_prompt").fetchSemanticsNodes().isEmpty() }
        assertTrue(backend.requests.isEmpty()); assertTrue(journal.states.isEmpty())
        compose.waitForIdle()
        assertEquals(initialSecure, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        compose.onNodeWithText("Design with AI").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_builder_prompt").assertDoesNotExist()
        compose.onNodeWithText(app.getString(helium314.keyboard.latin.R.string.agent_builder_unavailable)).assertExists()
        compose.runOnUiThread { app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit() }
        compose.onNodeWithText("Design with AI").performScrollTo().performClick()
        compose.onNodeWithTag("agent_builder_prompt").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun backgroundingCancelsTheRequestAndReturningStartsWithAnEmptyForm() {
        val app = prepare(); val journal = TestRunJournal(); var cancelled = false
        val backend = Provider { try { awaitCancellation() } finally { cancelled = true } }
        screen(backend, journal); openBuilder("private foreground instruction")
        compose.onNodeWithText("Send to OpenAI").performClick()
        compose.waitUntil(10_000) { backend.requests.size == 1 }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        assertTrue(cancelled)
        assertEquals(ActionRunState.CANCELLED, journal.states.values.single())
        assertTrue(WorkflowStore(app).read().isEmpty())
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No saved workflows.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Design with AI").performScrollTo().performClick()
        compose.onNodeWithTag("agent_builder_prompt").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(1, backend.requests.size)
    }

    @Test fun anUnsavedProposalRemainsProtectedAndIsDiscardedOnIncognitoOrPause() {
        val app = prepare(); val journal = TestRunJournal(); val backend = Provider { proposal() }
        val registry = SaveableStateRegistry(null) { true }
        val initialSecure = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE
        screen(backend, journal, registry)
        for (incognito in listOf(true, false)) {
            openBuilder("private draft instruction")
            compose.onNodeWithText("Send to OpenAI").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Review workflow draft").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(strings(compose.runOnIdle { registry.performSave() }).any { "private draft instruction" in it || "Generated draft" in it })
            if (incognito) {
                compose.runOnUiThread { app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit() }
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Review workflow draft").fetchSemanticsNodes().isEmpty() }
                compose.runOnUiThread { app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit() }
            } else {
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                compose.waitForIdle()
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("No saved workflows.").fetchSemanticsNodes().isNotEmpty() }
            }
            assertTrue(WorkflowStore(app).read().isEmpty())
            compose.waitForIdle()
            assertEquals(initialSecure, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            compose.onNodeWithText("Save workflow").assertDoesNotExist()
        }
        assertEquals(2, backend.requests.size)
    }

    @Test fun aQueuedSaveCannotPersistADiscardedProposalAfterPrivacyReturns() {
        val app = prepare(); val store = WorkflowStore(app); val journal = TestRunJournal()
        val backend = Provider { proposal() }
        screen(backend, journal); openBuilder("private proposal")
        compose.onNodeWithText("Send to OpenAI").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Review workflow draft").fetchSemanticsNodes().isNotEmpty() }
        val save = assertNotNull(compose.onNodeWithText("Save workflow").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action)
        compose.runOnUiThread {
            app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
            app.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
            save() // Deliver the old UI callback after the original proposal has been discarded.
        }
        compose.waitForIdle()
        assertTrue(store.read().isEmpty())
        compose.onNodeWithText("Save workflow").assertDoesNotExist()
        compose.onNodeWithText("Design with AI").performScrollTo().performClick()
        compose.onNodeWithTag("agent_builder_prompt").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnUiThread { save() }
        compose.waitForIdle()
        assertTrue(store.read().isEmpty())
    }

    private fun strings(value: Any?): Sequence<String> = when (value) {
        is String -> sequenceOf(value)
        is Map<*, *> -> value.values.asSequence().flatMap(::strings)
        is Iterable<*> -> value.asSequence().flatMap(::strings)
        is Array<*> -> value.asSequence().flatMap(::strings)
        is Bundle -> value.keySet().asSequence().flatMap { strings(value.get(it)) }
        else -> emptySequence()
    }
    private fun captureDialog(name: String) {
        compose.waitForIdle()
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
