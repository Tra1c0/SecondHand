// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.*
import kotlinx.coroutines.*

/** One approved model call, independent of the keyboard UI. Never persists text. */
internal class JournaledModelCall(private val journal: ActionRunJournal) {
    private val runs = JournaledRun(journal)
    suspend fun execute(action: AiTextAction, request: ModelRequest, provider: ModelProvider,
                        beforeCall: () -> Unit): String = execute(RunSubject.TextAction(action), request, provider, beforeCall)

    suspend fun execute(subject: RunSubject, request: ModelRequest, provider: ModelProvider,
                        beforeCall: () -> Unit): String {
        if (subject is RunSubject.Workflow) throw CloudFailure(CloudError.INVALID_REQUEST)
        return runs.execute(subject, provider.id, request.model, beforeCall) {
            provider.generate(request) {}.text
        }
    }
}

internal fun Exception.neutralFailure(): Exception = when (this) {
    is RunStoreFailure -> CloudFailure(CloudError.STORAGE)
    is CloudFailure, is WorkflowFailure -> this
    else -> CloudFailure(CloudError.NETWORK)
}
