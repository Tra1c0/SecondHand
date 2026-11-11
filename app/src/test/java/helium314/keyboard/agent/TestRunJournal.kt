// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.AiTextAction
import java.util.UUID

class TestRunJournal(override val supportsWorkflowRecords: Boolean = true) : ActionRunJournal {
    val states = linkedMapOf<String, ActionRunState>()
    val subjects = linkedMapOf<String, RunSubject>()
    val modelStates get() = states.filterKeys { subjects[it] !is RunSubject.Workflow }
    val workflowStates get() = states.filterKeys { subjects[it] is RunSubject.Workflow }
    override suspend fun begin(action: RunSubject, provider: String?, model: String?): String =
        UUID.randomUUID().toString().also { states[it] = ActionRunState.RUNNING; subjects[it] = action }
    override suspend fun finish(id: String, state: ActionRunState) {
        if (states[id] == ActionRunState.RUNNING) states[id] = state
    }
}
