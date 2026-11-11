// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.CloudError
import helium314.keyboard.agent.providers.CloudFailure
import kotlinx.coroutines.*

/** Main-thread session controller. Epoch checks protect against non-cooperative late providers. */
class ApprovedActionRunner(
    private val scope: CoroutineScope,
    private val provider: () -> ModelProvider,
    private val isCurrent: (TextSnapshot) -> Boolean,
    private val journal: ActionRunJournal,
    private val workflowTimeoutMillis: Long = 95_000,
) {
    init { require(workflowTimeoutMillis in 1..95_000) }
    private val modelCall = JournaledModelCall(journal)
    private val runs = JournaledRun(journal)
    private var generation = 0L
    private var job: Job? = null
    var isRunning = false
        private set

    fun start(source: TextSnapshot, model: String, instruction: String, action: AiTextAction = AiTextAction.CORRECT,
              onComplete: (Result<String>) -> Unit): Boolean = startOperation(source, onComplete) {
        modelCall.execute(action, ModelRequest(model, instruction, source), provider()) { requireCurrent(source) }
    }

    /** The host must already have displayed and consumed approval for this immutable definition. */
    fun startWorkflow(source: TextSnapshot, definition: WorkflowDefinition,
                      onComplete: (Result<WorkflowResult>) -> Unit): Boolean = startOperation(source, onComplete) {
        // Non-cancellable journal completion may outlast this budget; late publication is still guarded.
        withTimeoutOrNull(workflowTimeoutMillis) {
            // Capture this timed execution's context, not its outer supervisor or caller job.
            val executionContext = currentCoroutineContext()
            suspend fun execute(parentId: String?): WorkflowResult {
                val runner = WorkflowRunner(setOf(WorkflowProvider.OPENAI), { step, input ->
                    val link = parentId?.let { WorkflowStepLink(it, definition.id, definition.revision, step.id) }
                    modelCall.execute(RunSubject.TextAction(step.action, link),
                        ModelRequest(step.model, step.action.instruction(step.language, step.tone), input),
                        provider()) { requireCurrent(source) }
                }, {
                    executionContext.ensureActive()
                    requireCurrent(source)
                })
                return runner.execute(definition, source.text)
            }
            if (journal.supportsWorkflowRecords) {
                runs.execute(RunSubject.Workflow(definition.id, definition.revision), beforeRun = { requireCurrent(source) }) {
                    execute(it)
                }
            } else {
                if (definition.steps.any { it is WorkflowStep.Ai }) throw CloudFailure(CloudError.STORAGE)
                execute(null) // No encryption capability: local work remains available, without a fake persisted record.
            }
        }
            ?: throw CloudFailure(CloudError.TIMEOUT)
    }

    /**
     * The host must already have displayed the exact request and consumed its approval. The intent
     * is durable before the launcher runs; a hand-over is never repeated and never means "saved".
     */
    fun startTool(source: TextSnapshot, request: ToolRequest, launcher: ToolLauncher,
                  onComplete: (Result<ToolOutcome>) -> Unit): Boolean = startOperation(source, onComplete) {
        suspend fun run(): ToolOutcome = when (request.tool) {
            ToolId.TEST_COUNT -> ToolOutcome.Text(Tools.runTest(request))
            ToolId.CALENDAR_INSERT -> {
                requireCurrent(source)
                if (!launcher.launch(request)) throw ToolUnavailable()
                ToolOutcome.HandedOff
            }
        }
        if (journal.supportsWorkflowRecords) {
            runs.execute(RunSubject.Tool(request.tool), beforeRun = { requireCurrent(source) }) { run() }
        } else {
            // No durable intent is possible: only the tool without an external effect stays available.
            if (request.tool.external) throw CloudFailure(CloudError.STORAGE)
            run()
        }
    }

    private fun requireCurrent(source: TextSnapshot) {
        if (!isCurrent(source)) throw CancellationException("STALE")
    }

    private fun <T> startOperation(source: TextSnapshot, onComplete: (Result<T>) -> Unit,
                                   execute: suspend () -> T): Boolean {
        if (isRunning || !scope.isActive || !isCurrent(source)) return false
        val epoch = ++generation
        isRunning = true
        val operation = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = try {
                    Result.success(execute())
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Result.failure<T>(failure.neutralFailure()) }
                ensureActive()
                if (epoch == generation) {
                    isRunning = false
                    if (isCurrent(source)) onComplete(outcome)
                }
            } finally {
                if (epoch == generation) isRunning = false
            }
        }
        job = operation // Install ownership before synchronous/reentrant work can call cancel().
        if (!operation.start()) {
            if (epoch == generation) isRunning = false
            return false
        }
        return true
    }

    fun cancel() {
        generation++
        isRunning = false
        job?.cancel()
        job = null
    }
}
