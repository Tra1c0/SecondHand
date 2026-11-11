// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.WorkflowDefinition
import dev.agentkeyboard.core.WorkflowInput
import helium314.keyboard.latin.R

internal fun WorkflowDefinition.inputSummary(context: Context, input: WorkflowInput): String = when (input) {
    WorkflowInput.Selection -> context.getString(R.string.agent_workflow_input_selection)
    is WorkflowInput.StepResult -> {
        val index = steps.indexOfFirst { it.id == input.stepId }
        require(index >= 0)
        context.getString(R.string.agent_workflow_input_step, index + 1)
    }
}
