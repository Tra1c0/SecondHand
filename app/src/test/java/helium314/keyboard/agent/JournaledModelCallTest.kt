// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class JournaledModelCallTest {
    @Test fun builderRequestHasOnlyExplicitTextAndUsesItsOwnJournalSubject() = runBlocking {
        val journal = TestRunJournal()
        var checked = false
        val provider = object : ModelProvider {
            override val id = "test"; override val capabilities = ProviderCapabilities()
            override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
                assertTrue(checked)
                assertEquals("Describe workflow", request.input)
                return ModelResponse("draft response")
            }
        }
        val result = JournaledModelCall(journal).execute(RunSubject.WorkflowBuilder,
            ModelRequest("model", "Build a bounded definition.", "Describe workflow"), provider) { checked = true }
        assertEquals("draft response", result)
        assertEquals(RunSubject.WorkflowBuilder, journal.subjects.values.single())
        assertEquals(ActionRunState.SUCCEEDED, journal.states.values.single())
    }

    @Test fun aRevokedBuilderApprovalPreventsTheProviderAfterTheIntentCommit() = runBlocking {
        val journal = TestRunJournal()
        val provider = object : ModelProvider {
            override val id = "test"; override val capabilities = ProviderCapabilities()
            override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse = error("must not send")
        }
        assertFailsWith<CancellationException> {
            JournaledModelCall(journal).execute(RunSubject.WorkflowBuilder, ModelRequest("model", "Task", "Instruction"), provider) {
                throw CancellationException("REVOKED")
            }
        }
        assertEquals(ActionRunState.CANCELLED, journal.states.values.single())
    }
}
