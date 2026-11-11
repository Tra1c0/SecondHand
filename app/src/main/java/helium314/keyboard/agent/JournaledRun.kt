// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.providers.*
import kotlinx.coroutines.*

/** Durable intent and terminal metadata around approved work; the value is never persisted. */
internal class JournaledRun(private val journal: ActionRunJournal) {
    suspend fun <T> execute(subject: RunSubject, provider: String? = null, model: String? = null,
                           beforeRun: () -> Unit, operation: suspend (String) -> T): T {
        var runId: String? = null
        try {
            val outcome = try {
                withContext(NonCancellable) { runId = journal.begin(subject, provider, model) }
                currentCoroutineContext().ensureActive()
                beforeRun()
                val result = operation(requireNotNull(runId))
                currentCoroutineContext().ensureActive()
                Result.success(result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Result.failure<T>(failure.neutralFailure()) }
            val error = (outcome.exceptionOrNull() as? CloudFailure)?.error
            val state = when {
                outcome.isSuccess -> ActionRunState.SUCCEEDED
                error in setOf(CloudError.NETWORK, CloudError.TIMEOUT, CloudError.SERVICE,
                    CloudError.INVALID_RESPONSE, CloudError.TOO_LARGE) -> ActionRunState.UNKNOWN
                else -> ActionRunState.FAILED
            }
            try { withContext(NonCancellable) { runId?.let { journal.finish(it, state) } } }
            catch (_: Exception) { throw CloudFailure(CloudError.STORAGE) }
            currentCoroutineContext().ensureActive()
            return outcome.getOrThrow()
        } catch (cancelled: CancellationException) {
            val timeout = generateSequence<Throwable>(cancelled) { it.cause }.take(8).any { it is TimeoutCancellationException }
            withContext(NonCancellable) {
                runId?.let { runCatching { journal.finish(it,
                    if (timeout) ActionRunState.UNKNOWN else ActionRunState.CANCELLED) } }
            }
            throw cancelled
        }
    }
}
