// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.*
import kotlinx.coroutines.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class ApprovedWorkflowRunnerTest {
    private fun source(): TextSnapshot {
        val session = EditorSession().apply { start() }
        return (ExecutionPolicy().capture(session.token!!, EditorAccess(isText = true), 0, 5, "Hallo") as CaptureResult.Captured).snapshot
    }
    private fun workflow(vararg steps: WorkflowStep) = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Example", steps.toList())
    private fun local() = WorkflowStep.Local("first", WorkflowInput.Selection, LocalTextAction.UPPERCASE)
    private fun ai(id: String, input: WorkflowInput = WorkflowInput.Selection, provider: WorkflowProvider = WorkflowProvider.OPENAI) =
        WorkflowStep.Ai(id, input, AiTextAction.CORRECT, provider, "model")
    private class Provider(private val respond: suspend (ModelRequest) -> String) : ModelProvider {
        val requests = mutableListOf<ModelRequest>()
        override val id = "test"
        override val capabilities = ProviderCapabilities()
        override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
            requests.add(request); return ModelResponse(respond(request))
        }
    }

    @Test fun localWorkflowNeverConstructsAProviderOrWritesAModelIntent() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = TestRunJournal()
        var result: WorkflowResult? = null
        try {
            val runner = ApprovedActionRunner(scope, { error("must stay offline") }, { true }, journal)
            assertTrue(runner.startWorkflow(source(), workflow(local())) { result = it.getOrThrow() })
            assertEquals("HALLO", result!!.text)
            assertTrue(journal.modelStates.isEmpty())
            assertEquals(ActionRunState.SUCCEEDED, journal.workflowStates.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun twoRequestsAreSequentialJournaledAndPublishOnlyTheFinalResult() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = CompletableDeferred<String>(); val second = CompletableDeferred<String>()
        val provider = Provider { if (it.input == "Hallo") first.await() else second.await() }
        val journal = TestRunJournal(); val results = mutableListOf<String>()
        val runner = ApprovedActionRunner(scope, { provider }, { true }, journal)
        val definition = workflow(ai("first"), ai("second", WorkflowInput.StepResult("first")))
        try {
            assertTrue(runner.startWorkflow(source(), definition) {
                results.add(it.getOrThrow().text)
            })
            assertEquals(1, provider.requests.size)
            assertFalse(runner.start(source(), "model", "task") { error("single flight") })
            first.complete("Intermediate")
            assertEquals(listOf("Hallo", "Intermediate"), provider.requests.map { it.input })
            assertEquals(listOf(ActionRunState.SUCCEEDED, ActionRunState.RUNNING), journal.modelStates.values.toList())
            assertTrue(results.isEmpty())
            second.complete("Final")
            assertEquals(listOf("Final"), results)
            assertTrue(journal.modelStates.values.all { it == ActionRunState.SUCCEEDED })
            val parentId = journal.workflowStates.keys.single()
            assertEquals(ActionRunState.SUCCEEDED, journal.workflowStates.getValue(parentId))
            val links = journal.subjects.values.filterIsInstance<RunSubject.TextAction>().map { it.workflow!! }
            assertEquals(listOf("first", "second"), links.map { it.stepId })
            assertTrue(links.all { it.runId == parentId && it.definitionId == definition.id && it.revision == definition.revision })
        } finally { runner.cancel(); scope.cancel() }
    }

    @Test fun privacyChangeDuringIntentCommitPreventsTheFirstRequest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var current = true
        val backing = TestRunJournal()
        val journal = object : ActionRunJournal {
            override suspend fun begin(action: RunSubject, provider: String?, model: String?): String {
                val id = backing.begin(action, provider, model); if (action is RunSubject.TextAction) current = false; return id
            }
            override suspend fun finish(id: String, state: ActionRunState) = backing.finish(id, state)
        }
        val provider = Provider { error("must not send") }
        try {
            val runner = ApprovedActionRunner(scope, { provider }, { current }, journal)
            runner.startWorkflow(source(), workflow(ai("first"))) { error("stale publication") }
            assertTrue(provider.requests.isEmpty())
            assertEquals(ActionRunState.CANCELLED, backing.modelStates.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun reentrantCancelAfterFirstCommitPreventsSecondRequestEvenWithSynchronousProvider() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backing = TestRunJournal()
        lateinit var runner: ApprovedActionRunner
        val journal = object : ActionRunJournal {
            override suspend fun begin(action: RunSubject, provider: String?, model: String?) = backing.begin(action, provider, model)
            override suspend fun finish(id: String, state: ActionRunState) {
                backing.finish(id, state)
                if (state == ActionRunState.SUCCEEDED) runner.cancel()
            }
        }
        val provider = Provider { "Intermediate" }
        runner = ApprovedActionRunner(scope, { provider }, { true }, journal)
        try {
            runner.startWorkflow(source(), workflow(ai("first"), ai("second", WorkflowInput.StepResult("first")))) {
                error("cancelled result")
            }
            assertEquals(1, provider.requests.size)
            assertEquals(ActionRunState.SUCCEEDED, backing.modelStates.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun wholeWorkflowTimeoutPublishesAFailureAndMarksUnansweredRequestUnknown() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = TestRunJournal()
        val never = CompletableDeferred<String>()
        val outcome = CompletableDeferred<Result<WorkflowResult>>()
        val provider = Provider { never.await() }
        val runner = ApprovedActionRunner(scope, { provider }, { true }, journal, workflowTimeoutMillis = 150)
        try {
            runner.startWorkflow(source(), workflow(ai("first"))) { outcome.complete(it) }
            val result = withTimeout(3000) { outcome.await() }
            assertEquals(CloudError.TIMEOUT, (result.exceptionOrNull() as CloudFailure).error)
            assertEquals(1, provider.requests.size)
            assertEquals(ActionRunState.UNKNOWN, journal.modelStates.values.single())
            assertEquals(ActionRunState.UNKNOWN, journal.workflowStates.values.single())
            assertFalse(runner.isRunning)
        } finally { runner.cancel(); scope.cancel() }
    }

    @Test fun unsupportedProviderInTheSecondStepCannotChargeTheFirst() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = TestRunJournal()
        var failure: Throwable? = null
        try {
            val runner = ApprovedActionRunner(scope, { error("must not construct") }, { true }, journal)
            runner.startWorkflow(source(), workflow(ai("first"), ai("second", provider = WorkflowProvider.GEMINI))) {
                failure = it.exceptionOrNull()
            }
            assertEquals(WorkflowError.UNSUPPORTED_PROVIDER, (failure as WorkflowFailure).error)
            assertTrue(journal.modelStates.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun secondStepFailureNeverPublishesTheSuccessfulIntermediate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = TestRunJournal()
        val provider = Provider { if (it.input == "Hallo") "Intermediate" else throw CloudFailure(CloudError.NETWORK) }
        var result: Result<WorkflowResult>? = null
        try {
            val runner = ApprovedActionRunner(scope, { provider }, { true }, journal)
            runner.startWorkflow(source(), workflow(ai("first"), ai("second", WorkflowInput.StepResult("first")))) { result = it }
            assertTrue(result!!.isFailure)
            assertEquals(listOf(ActionRunState.SUCCEEDED, ActionRunState.UNKNOWN), journal.modelStates.values.toList())
        } finally { scope.cancel() }
    }

    @Test fun lateCancelledWorkflowCannotOverwriteOrEndANewerLocalWorkflow() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val old = CompletableDeferred<String>(); val results = mutableListOf<String>()
        val provider = Provider { withContext(NonCancellable) { old.await() } }
        val journal = TestRunJournal()
        val runner = ApprovedActionRunner(scope, { provider }, { true }, journal)
        try {
            runner.startWorkflow(source(), workflow(ai("first"))) { results.add(it.getOrThrow().text) }
            runner.cancel()
            runner.startWorkflow(source(), workflow(local())) { results.add(it.getOrThrow().text) }
            old.complete("late")
            assertEquals(listOf("HALLO"), results)
            assertFalse(runner.isRunning)
            assertEquals(ActionRunState.CANCELLED, journal.modelStates.values.single())
        } finally { old.complete(""); runner.cancel(); scope.cancel() }
    }

    @Test fun localWorkRemainsAvailableWhenThePlatformCannotPersistEncryptedWorkflowStatus() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = TestRunJournal(supportsWorkflowRecords = false)
        var result: WorkflowResult? = null
        try {
            val runner = ApprovedActionRunner(scope, { error("must stay offline") }, { true }, journal)
            runner.startWorkflow(source(), workflow(local())) { result = it.getOrThrow() }
            assertEquals("HALLO", result!!.text)
            assertTrue(journal.states.isEmpty()) // No claim that an unavailable journal persisted anything.
        } finally { scope.cancel() }
    }

    @Test fun workflowIntentFailureCannotStartAnyProviderOrPublishAResult() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = object : ActionRunJournal {
            override suspend fun begin(subject: RunSubject, provider: String?, model: String?): String = throw RunStoreFailure()
            override suspend fun finish(id: String, state: ActionRunState) = error("no intent")
        }
        var failure: Throwable? = null
        try {
            val runner = ApprovedActionRunner(scope, { error("must not construct") }, { true }, journal)
            runner.startWorkflow(source(), workflow(ai("first"))) { failure = it.exceptionOrNull() }
            assertEquals(CloudError.STORAGE, (failure as CloudFailure).error)
        } finally { scope.cancel() }
    }

    @Test fun failedWorkflowCompletionWriteCannotPublishEvenAFullyComputedLocalResult() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = object : ActionRunJournal {
            override suspend fun begin(subject: RunSubject, provider: String?, model: String?) = UUID.randomUUID().toString()
            override suspend fun finish(id: String, state: ActionRunState): Unit = throw RunStoreFailure()
        }
        var failure: Throwable? = null
        try {
            val runner = ApprovedActionRunner(scope, { error("must stay offline") }, { true }, journal)
            runner.startWorkflow(source(), workflow(local())) { failure = it.exceptionOrNull() }
            assertEquals(CloudError.STORAGE, (failure as CloudFailure).error)
        } finally { scope.cancel() }
    }

    @Test fun cancellingDuringTheParentIntentDoesNotLoseItsCommittedIdentifier() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backing = TestRunJournal(); val release = CompletableDeferred<Unit>()
        val journal = object : ActionRunJournal {
            override suspend fun begin(subject: RunSubject, provider: String?, model: String?): String {
                val id = backing.begin(subject, provider, model)
                release.await(); return id
            }
            override suspend fun finish(id: String, state: ActionRunState) = backing.finish(id, state)
        }
        val runner = ApprovedActionRunner(scope, { error("cancelled before model") }, { true }, journal)
        try {
            runner.startWorkflow(source(), workflow(ai("first"))) { error("cancelled result") }
            runner.cancel(); release.complete(Unit)
            assertEquals(ActionRunState.CANCELLED, backing.workflowStates.values.single())
            assertTrue(backing.modelStates.isEmpty())
            assertFalse(runner.isRunning)
        } finally { release.complete(Unit); scope.cancel() }
    }
}
