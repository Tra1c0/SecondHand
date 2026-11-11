// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.ToolId
import java.util.UUID

internal fun requireRunIdentifier(id: String) {
    require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "INVALID_IDENTIFIER" }
}

data class WorkflowStepLink(val runId: String, val definitionId: String, val revision: Int, val stepId: String) {
    init {
        requireRunIdentifier(runId); requireRunIdentifier(definitionId)
        require(revision in 1..1_000_000 && stepId.matches(Regex("[a-z][a-z0-9_]{0,23}")))
    }
}

sealed interface RunSubject {
    data class TextAction(val action: AiTextAction, val workflow: WorkflowStepLink? = null) : RunSubject
    data class Workflow(val definitionId: String, val revision: Int) : RunSubject {
        init { requireRunIdentifier(definitionId); require(revision in 1..1_000_000) }
    }
    data object WorkflowBuilder : RunSubject
    /** A built-in tool run. For an external tool, success means handed over, never "saved". */
    data class Tool(val tool: ToolId) : RunSubject
}

enum class ActionRunState { RUNNING, SUCCEEDED, FAILED, CANCELLED, UNKNOWN }

/** Metadata only: never contains prompts, source, result, credentials or editor authority. */
data class ActionRunRecord(
    val id: String,
    val subject: RunSubject,
    val provider: String?,
    val model: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val state: ActionRunState,
    internal val owner: String,
)

interface ActionRunJournal {
    /** Older Android versions keep local workflows available without claiming durable status. */
    val supportsWorkflowRecords: Boolean get() = true
    suspend fun begin(subject: RunSubject, provider: String? = null, model: String? = null): String
    suspend fun begin(action: AiTextAction, provider: String, model: String): String =
        begin(RunSubject.TextAction(action), provider, model)
    suspend fun finish(id: String, state: ActionRunState)
}
