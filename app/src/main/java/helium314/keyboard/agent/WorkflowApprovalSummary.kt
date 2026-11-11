// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.*
import helium314.keyboard.latin.R

/** Actual typed operations, never the imported name alone, describe the approval. */
internal fun workflowApprovalSummary(context: Context, definition: WorkflowDefinition, characters: Int): String = buildString {
    append(context.getString(R.string.agent_workflow_approval_header, definition.name, definition.revision, characters))
    definition.steps.forEachIndexed { index, step ->
        val action = when (step) {
            is WorkflowStep.Local -> BuiltinAction.entries.single { it.local == step.action }
            is WorkflowStep.Ai -> BuiltinAction.entries.single { it.ai == step.action }
        }
        append('\n')
        append(context.getString(R.string.agent_workflow_step_description, index + 1, context.getString(action.titleResource()),
            definition.inputSummary(context, step.input)))
        if (step is WorkflowStep.Ai) {
            append(" · ").append(step.provider.id).append(" / ").append(step.model)
            append(" · ").append(step.language.code).append(" · ").append(context.getString(step.tone.titleResource()))
        } else append(" · ").append(context.getString(R.string.agent_workflow_on_device))
        step.condition?.let { append('\n').append(it.summary(context)) }
    }
    val calls = definition.steps.count { it is WorkflowStep.Ai }
    append("\n\n")
    append(context.getString(if (calls == 0) R.string.agent_workflow_approval_local else R.string.agent_workflow_approval_cloud, calls))
    if (definition.steps.any { it.condition != null }) append('\n').append(context.getString(R.string.agent_condition_fallback))
}
