// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class WorkflowDraftRunnerTest {
    private fun proposal(name: String = "Clean") = """{"name":"$name","steps":[{"id":"clean","input":"selection","condition":null,"kind":"local","action":"local.clean_spaces"}]}"""
    private class Provider(override val id: String = "openai", drafts: Boolean = true,
                           private val respond: suspend (ModelRequest) -> String) : ModelProvider {
        override val capabilities = ProviderCapabilities(workflowDrafts = drafts)
        val requests = mutableListOf<ModelRequest>()
        override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
            requests.add(request); return ModelResponse(respond(request))
        }
    }
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test fun preparingDoesNoIoAndASeparateSendConsumesApprovalExactlyOnce() {
        val scope = scope(); val journal = TestRunJournal(); val backend = Provider { proposal() }
        var constructed = 0; var result: WorkflowDefinition? = null
        val runner = WorkflowDraftRunner(scope, { constructed++; backend }, { true }, journal)
        try {
            val approval = assertNotNull(runner.prepare("Clean whitespace", "approved-model"))
            assertEquals(0, constructed); assertTrue(journal.states.isEmpty())
            assertEquals("Clean whitespace", approval.prompt); assertEquals("approved-model", approval.model)
            assertFalse(approval.toString().contains(approval.prompt))
            assertTrue(runner.start(approval) { result = it.getOrThrow() })
            assertFalse(runner.start(approval) { error("second send") })
            assertEquals("Clean", assertNotNull(result).name)
            assertEquals(1, backend.requests.size)
            assertEquals(ModelOutputFormat.WORKFLOW_DRAFT, backend.requests.single().outputFormat)
            assertEquals("Clean whitespace", backend.requests.single().input)
            assertEquals(RunSubject.WorkflowBuilder, journal.subjects.values.single())
            assertEquals(ActionRunState.SUCCEEDED, journal.states.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun approvalsCannotBeReusedAfterCancelReplacementLockOrAcrossRunners() {
        val scope = scope(); val journal = TestRunJournal(); var allowed = true
        val backend = Provider { proposal() }
        val runner = WorkflowDraftRunner(scope, { backend }, { allowed }, journal)
        val other = WorkflowDraftRunner(scope, { backend }, { true }, journal)
        try {
            val first = assertNotNull(runner.prepare("First", "model"))
            val second = assertNotNull(runner.prepare("Second", "model"))
            assertFalse(runner.start(first) { error("stale") })
            assertFalse(other.start(second) { error("wrong owner") })
            runner.cancel()
            assertFalse(runner.start(second) { error("cancelled") })
            val locked = assertNotNull(runner.prepare("Third", "model"))
            allowed = false
            assertFalse(runner.start(locked) { error("locked") })
            assertNull(runner.prepare("Fourth", "model"))
            allowed = true
            assertFalse(runner.start(locked) { error("already consumed") })
            assertNull(runner.prepare("", "model"))
            assertTrue(journal.states.isEmpty()); assertTrue(backend.requests.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun policyChangeWhileWritingIntentBlocksProviderAndClosesTheRun() {
        val scope = scope(); val records = TestRunJournal(); var allowed = true; var constructed = 0
        val journal = object : ActionRunJournal by records {
            override suspend fun begin(subject: RunSubject, provider: String?, model: String?) =
                records.begin(subject, provider, model).also { allowed = false }
        }
        val runner = WorkflowDraftRunner(scope, { constructed++; error("blocked") }, { allowed }, journal)
        try {
            val approval = assertNotNull(runner.prepare("Translate", "model"))
            runner.start(approval) { error("policy changed") }
            assertEquals(0, constructed)
            assertEquals(ActionRunState.CANCELLED, records.states.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun synchronousCancelDuringIntentKeepsTheDurableIdForTerminalCleanup() {
        val scope = scope(); val records = TestRunJournal()
        lateinit var runner: WorkflowDraftRunner
        val journal = object : ActionRunJournal by records {
            override suspend fun begin(subject: RunSubject, provider: String?, model: String?) =
                records.begin(subject, provider, model).also { runner.cancel() }
        }
        runner = WorkflowDraftRunner(scope, { error("cancelled before provider") }, { true }, journal)
        try {
            runner.start(assertNotNull(runner.prepare("Clean", "model"))) { error("cancelled") }
            assertEquals(ActionRunState.CANCELLED, records.states.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun intentFailurePreventsProviderConstructionAndFinishFailurePreventsDraftPublication() {
        for (failBegin in listOf(true, false)) {
            val scope = scope(); val records = TestRunJournal(); var constructed = 0
            val backend = Provider { proposal() }
            val journal = object : ActionRunJournal by records {
                override suspend fun begin(subject: RunSubject, provider: String?, model: String?): String {
                    if (failBegin) throw RunStoreFailure()
                    return records.begin(subject, provider, model)
                }
                override suspend fun finish(id: String, state: ActionRunState) { throw RunStoreFailure() }
            }
            val runner = WorkflowDraftRunner(scope, { constructed++; backend }, { true }, journal)
            var result: Result<WorkflowDefinition>? = null
            try {
                runner.start(assertNotNull(runner.prepare("Clean", "model"))) { result = it }
                assertEquals(CloudError.STORAGE, (assertNotNull(result).exceptionOrNull() as CloudFailure).error)
                assertEquals(if (failBegin) 0 else 1, constructed)
                assertFalse(runner.isRunning)
            } finally { scope.cancel() }
        }
    }

    @Test fun invalidOutputDoesNotBecomeASuccessfulDraftOrLeakItsContents() {
        val scope = scope(); val records = TestRunJournal(); val backend = Provider { "private invalid proposal" }
        val runner = WorkflowDraftRunner(scope, { backend }, { true }, records)
        var result: Result<WorkflowDefinition>? = null
        try {
            runner.start(assertNotNull(runner.prepare("Clean", "model"))) { result = it }
            val error = assertNotNull(result).exceptionOrNull() as CloudFailure
            assertEquals(CloudError.INVALID_RESPONSE, error.error)
            assertNull(error.cause); assertFalse(error.toString().contains("private"))
            assertEquals(ActionRunState.UNKNOWN, records.states.values.single())
        } finally { scope.cancel() }
    }

    @Test fun unsupportedProvidersOrMissingStructuredCapabilityNeverReceiveThePrompt() {
        for (backend in listOf(Provider("claude") { proposal() }, Provider(drafts = false) { proposal() })) {
            val scope = scope(); val records = TestRunJournal()
            val runner = WorkflowDraftRunner(scope, { backend }, { true }, records)
            var result: Result<WorkflowDefinition>? = null
            try {
                runner.start(assertNotNull(runner.prepare("Clean", "model"))) { result = it }
                assertEquals(CloudError.INVALID_REQUEST, (assertNotNull(result).exceptionOrNull() as CloudFailure).error)
                assertTrue(backend.requests.isEmpty())
                assertEquals(ActionRunState.FAILED, records.states.values.single())
            } finally { scope.cancel() }
        }
    }

    @Test fun timeoutPublishesNeutralFailureAndMarksTheUnansweredRequestUnknown() = runBlocking {
        val scope = scope(); val records = TestRunJournal(); val outcome = CompletableDeferred<Result<WorkflowDefinition>>()
        val backend = Provider { awaitCancellation() }
        val runner = WorkflowDraftRunner(scope, { backend }, { true }, records, timeoutMillis = 100)
        try {
            runner.start(assertNotNull(runner.prepare("Clean", "model"))) { outcome.complete(it) }
            val result = withTimeout(3000) { outcome.await() }
            assertEquals(CloudError.TIMEOUT, (result.exceptionOrNull() as CloudFailure).error)
            assertEquals(ActionRunState.UNKNOWN, records.states.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun lateCancelledResponseCannotOverwriteOrEndANewerGeneration() {
        val scope = scope(); val records = TestRunJournal(); val old = CompletableDeferred<String>()
        var call = 0; val seen = mutableListOf<String>()
        val backend = Provider { if (++call == 1) withContext(NonCancellable) { old.await() } else proposal("New") }
        val runner = WorkflowDraftRunner(scope, { backend }, { true }, records)
        try {
            runner.start(assertNotNull(runner.prepare("Old", "model"))) { seen.add(it.getOrThrow().name) }
            runner.cancel()
            runner.start(assertNotNull(runner.prepare("New", "model"))) { seen.add(it.getOrThrow().name) }
            assertEquals(listOf("New"), seen)
            old.complete(proposal("Old"))
            assertEquals(listOf("New"), seen)
            assertFalse(runner.isRunning)
            assertEquals(listOf(ActionRunState.CANCELLED, ActionRunState.SUCCEEDED), records.states.values.toList())
        } finally { scope.cancel() }
    }

    @Test fun policyLossDuringResponseOrCompletionDiscardsThePreparedText() {
        for (duringCompletion in listOf(true, false)) {
            val scope = scope(); val records = TestRunJournal(); var allowed = true
            val backend = Provider { if (!duringCompletion) allowed = false; proposal() }
            val journal = object : ActionRunJournal by records {
                override suspend fun finish(id: String, state: ActionRunState) {
                    records.finish(id, state)
                    if (duringCompletion) allowed = false
                }
            }
            val runner = WorkflowDraftRunner(scope, { backend }, { allowed }, journal)
            try {
                runner.start(assertNotNull(runner.prepare("Clean", "model"))) { error("no longer allowed") }
                assertEquals(if (duringCompletion) ActionRunState.SUCCEEDED else ActionRunState.CANCELLED,
                    records.states.values.single())
                assertFalse(runner.isRunning)
            } finally { scope.cancel() }
        }
    }
}
