// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class ApprovedActionRunnerTest {
    private fun source(): TextSnapshot {
        val session = EditorSession().apply { start() }
        return (ExecutionPolicy().capture(session.token!!, EditorAccess(isText = true), 0, 5, "Hallo") as CaptureResult.Captured).snapshot
    }
    private class Provider(val responses: List<CompletableDeferred<String>>) : ModelProvider {
        override val id = "offline-test"
        override val capabilities = ProviderCapabilities()
        var calls = 0
        override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
            val response = responses[calls++]
            // Deliberately ignores cancellation: tests the epoch guard independently of transport.
            return withContext(NonCancellable) { ModelResponse(response.await()) }
        }
    }

    @Test fun staleSelectionCannotStartAProvider() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var created = 0
        val runner = ApprovedActionRunner(scope, { created++; error("must not run") }, { false }, TestRunJournal())
        try {
            assertFalse(runner.start(source(), "model", "task") { error("must not publish") })
            assertEquals(0, created)
        } finally { scope.cancel() }
    }

    @Test fun cancelledOldResultCannotPublishOrEndANewerRun() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val old = CompletableDeferred<String>(); val fresh = CompletableDeferred<String>()
        val provider = Provider(listOf(old, fresh))
        val results = mutableListOf<String>()
        val runner = ApprovedActionRunner(scope, { provider }, { true }, TestRunJournal())
        try {
            assertTrue(runner.start(source(), "model", "task") { results.add(it.getOrThrow()) })
            assertFalse(runner.start(source(), "model", "task") { error("double start") })
            runner.cancel()
            assertTrue(runner.start(source(), "model", "task") { results.add(it.getOrThrow()) })
            old.complete("old")
            assertTrue(results.isEmpty())
            assertTrue(runner.isRunning)
            fresh.complete("fresh")
            assertEquals(listOf("fresh"), results)
            assertFalse(runner.isRunning)
        } finally { old.complete(""); fresh.complete(""); runner.cancel(); scope.cancel() }
    }

    @Test fun editorChangeWhileWaitingDiscardsResult() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<String>()
        var current = true
        var published = false
        val runner = ApprovedActionRunner(scope, { Provider(listOf(response)) }, { current }, TestRunJournal())
        try {
            assertTrue(runner.start(source(), "model", "task") { published = true })
            current = false
            response.complete("result")
            assertFalse(published)
            assertFalse(runner.isRunning)
        } finally { runner.cancel(); scope.cancel() }
    }

    @Test fun intentAndCompletionAreDurableBeforeRequestAndPublication() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val events = mutableListOf<String>()
        val journal = object : ActionRunJournal {
            override suspend fun begin(action: RunSubject, provider: String?, model: String?): String {
                assertEquals(RunSubject.TextAction(AiTextAction.REPLY), action); events.add("begin"); return "id"
            }
            override suspend fun finish(id: String, state: ActionRunState) {
                assertEquals(ActionRunState.SUCCEEDED, state); events.add("finish")
            }
        }
        val provider = object : ModelProvider {
            override val id = "test"; override val capabilities = ProviderCapabilities()
            override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
                events.add("request"); return ModelResponse("result")
            }
        }
        try {
            ApprovedActionRunner(scope, { provider }, { true }, journal).start(source(), "model", "task", AiTextAction.REPLY) {
                assertEquals("result", it.getOrThrow()); events.add("publish")
            }
            assertEquals(listOf("begin", "request", "finish", "publish"), events)
        } finally { scope.cancel() }
    }

    @Test fun cancellationDuringIntentCommitStillOwnsAndClosesThatRecord() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val committed = CompletableDeferred<Unit>()
        val finishes = mutableListOf<Pair<String, ActionRunState>>()
        val journal = object : ActionRunJournal {
            override suspend fun begin(action: RunSubject, provider: String?, model: String?): String {
                committed.await(); return "committed-id"
            }
            override suspend fun finish(id: String, state: ActionRunState) { finishes.add(id to state) }
        }
        val provider = Provider(emptyList())
        val runner = ApprovedActionRunner(scope, { provider }, { true }, journal)
        try {
            runner.start(source(), "model", "task") { error("cancelled result") }
            runner.cancel()
            committed.complete(Unit)
            assertEquals(0, provider.calls)
            assertEquals(listOf("committed-id" to ActionRunState.CANCELLED), finishes)
            assertFalse(runner.isRunning)
        } finally { committed.complete(Unit); scope.cancel() }
    }

    @Test fun storageFailureBeforeIntentNeverCallsProvider() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val provider = Provider(emptyList())
        val journal = object : ActionRunJournal {
            override suspend fun begin(action: RunSubject, provider: String?, model: String?): String = throw RunStoreFailure()
            override suspend fun finish(id: String, state: ActionRunState) = error("no record")
        }
        var failure: Throwable? = null
        try {
            ApprovedActionRunner(scope, { provider }, { true }, journal).start(source(), "model", "task") { failure = it.exceptionOrNull() }
            assertEquals(0, provider.calls)
            assertEquals(CloudError.STORAGE, (failure as CloudFailure).error)
        } finally { scope.cancel() }
    }

    @Test fun completionWriteFailureCannotPublishAnUnjournaledSuccess() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = object : ActionRunJournal {
            override suspend fun begin(action: RunSubject, provider: String?, model: String?) = "id"
            override suspend fun finish(id: String, state: ActionRunState): Unit = throw RunStoreFailure()
        }
        val response = CompletableDeferred("result")
        var failure: Throwable? = null
        try {
            ApprovedActionRunner(scope, { Provider(listOf(response)) }, { true }, journal).start(source(), "model", "task") {
                failure = it.exceptionOrNull()
            }
            assertEquals(CloudError.STORAGE, (failure as CloudFailure).error)
        } finally { scope.cancel() }
    }

    @Test fun networkTimeoutIsUnknownWithoutRetry() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val journal = TestRunJournal()
        var calls = 0
        val provider = object : ModelProvider {
            override val id = "test"; override val capabilities = ProviderCapabilities()
            override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
                calls++; throw CloudFailure(CloudError.TIMEOUT)
            }
        }
        try {
            ApprovedActionRunner(scope, { provider }, { true }, journal).start(source(), "model", "task") {
                assertTrue(it.isFailure)
            }
            assertEquals(1, calls)
            assertEquals(ActionRunState.UNKNOWN, journal.states.values.single())
        } finally { scope.cancel() }
    }
}
