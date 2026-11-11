// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.*
import kotlinx.coroutines.*

/** Text data for one displayed send approval, bound by identity to its preparing runner. */
class WorkflowDraftApproval internal constructor(internal val request: ModelRequest) {
    val model: String get() = request.model
    val prompt: String get() = request.input
    override fun toString() = "WorkflowDraftApproval(model=$model, chars=${prompt.length})"
}

/** Main-thread controller for configuration generation, with no editor or activation authority. */
class WorkflowDraftRunner(
    private val scope: CoroutineScope,
    private val provider: () -> ModelProvider,
    private val isAllowed: () -> Boolean,
    journal: ActionRunJournal,
    private val timeoutMillis: Long = 65_000,
) {
    init { require(timeoutMillis in 1..65_000) }
    private val runs = JournaledRun(journal)
    private var generation = 0L
    private var pending: WorkflowDraftApproval? = null
    private var job: Job? = null
    var isRunning = false
        private set

    /** Preparing only freezes the exact text/model for display; it performs no I/O. */
    fun prepare(prompt: String, model: String): WorkflowDraftApproval? {
        pending = null
        if (isRunning || !scope.isActive || !allowed()) return null
        return runCatching { WorkflowDraftApproval(WorkflowBuilderContract.request(prompt, model)) }
            .getOrNull()?.also { pending = it }
    }

    /** Must follow a separate send click after the host has displayed this approval. */
    fun start(approval: WorkflowDraftApproval, onComplete: (Result<WorkflowDefinition>) -> Unit): Boolean {
        if (pending !== approval) return false
        pending = null // Consume before launching, including synchronous/reentrant work.
        if (isRunning || !scope.isActive || !allowed()) return false
        val epoch = ++generation
        isRunning = true
        val operation = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    val draft = withTimeoutOrNull(timeoutMillis) {
                        val timedContext = currentCoroutineContext()
                        fun checkpoint() {
                            timedContext.ensureActive()
                            if (epoch != generation || !allowed()) throw CancellationException("STALE")
                        }
                        runs.execute(RunSubject.WorkflowBuilder, "openai", approval.model, beforeRun = ::checkpoint) {
                            val backend = provider()
                            if (backend.id != "openai" || !backend.capabilities.workflowDrafts)
                                throw CloudFailure(CloudError.INVALID_REQUEST)
                            checkpoint()
                            val response = backend.generate(approval.request) {}
                            checkpoint()
                            try { WorkflowBuilderContract.decode(response.text, approval.model) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { throw CloudFailure(CloudError.INVALID_RESPONSE) }
                        }
                    } ?: throw CloudFailure(CloudError.TIMEOUT)
                    Result.success(draft)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Result.failure<WorkflowDefinition>(failure.neutralFailure()) }
                ensureActive()
                if (epoch == generation) {
                    isRunning = false
                    if (allowed()) onComplete(result)
                }
            } finally {
                if (epoch == generation) isRunning = false
            }
        }
        job = operation
        if (!operation.start()) {
            if (epoch == generation) isRunning = false
            return false
        }
        return true
    }

    private fun allowed() = runCatching(isAllowed).getOrDefault(false)

    fun cancel() {
        generation++
        pending = null
        isRunning = false
        job?.cancel()
        job = null
    }
}
