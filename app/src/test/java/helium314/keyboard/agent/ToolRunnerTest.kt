// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.CloudError
import helium314.keyboard.agent.providers.CloudFailure
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class ToolRunnerTest {
    private fun source(text: String = "Dentist\nBring the referral"): TextSnapshot {
        val session = EditorSession().apply { start() }
        return (ExecutionPolicy().capture(session.token!!, EditorAccess(isText = true), 0, text.length, text)
            as CaptureResult.Captured).snapshot
    }
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private fun runner(scope: CoroutineScope, journal: ActionRunJournal, isCurrent: (TextSnapshot) -> Boolean = { true }) =
        ApprovedActionRunner(scope, { error("tools never construct a model provider") }, isCurrent, journal)

    @Test fun testToolReportsLocallyAndNeverCallsTheLauncher() {
        val scope = scope(); val journal = TestRunJournal(); var outcome: ToolOutcome? = null
        try {
            val source = source()
            assertTrue(runner(scope, journal).startTool(source, Tools.prepare(ToolId.TEST_COUNT, source.text),
                { error("no external effect") }) { outcome = it.getOrThrow() })
            assertEquals("chars=26 words=4 lines=2", (outcome as ToolOutcome.Text).text)
            assertEquals(RunSubject.Tool(ToolId.TEST_COUNT), journal.subjects.values.single())
            assertEquals(ActionRunState.SUCCEEDED, journal.states.values.single())
        } finally { scope.cancel() }
    }

    @Test fun calendarHandOverHappensExactlyOnceAfterTheIntentIsRecorded() {
        val scope = scope(); var outcome: ToolOutcome? = null
        val launched = mutableListOf<ToolRequest>()
        val journal = TestRunJournal()
        try {
            val source = source(); val request = Tools.prepare(ToolId.CALENDAR_INSERT, source.text)
            val runner = runner(scope, journal)
            assertTrue(runner.startTool(source, request, {
                assertEquals(ActionRunState.RUNNING, journal.states.values.single()) // intent before effect
                launched.add(it); true
            }) { outcome = it.getOrThrow() })
            assertSame(request, launched.single())
            assertEquals(ToolOutcome.HandedOff, outcome)
            assertEquals(ActionRunState.SUCCEEDED, journal.states.values.single())
            assertFalse(runner.isRunning)
        } finally { scope.cancel() }
    }

    @Test fun missingCalendarAppFailsWithoutRetryOrUnknownState() {
        val scope = scope(); val journal = TestRunJournal(); var failure: Throwable? = null; var calls = 0
        try {
            val source = source()
            assertTrue(runner(scope, journal).startTool(source, Tools.prepare(ToolId.CALENDAR_INSERT, source.text),
                { calls++; false }) { failure = it.exceptionOrNull() })
            assertIs<ToolUnavailable>(failure)
            assertEquals(1, calls)
            assertEquals(ActionRunState.FAILED, journal.states.values.single())
        } finally { scope.cancel() }
    }

    @Test fun staleEditorOrUnsavedIntentPreventsTheHandOver() {
        val scope = scope()
        try {
            val source = source(); val request = Tools.prepare(ToolId.CALENDAR_INSERT, source.text)
            // Stale before start: nothing begins.
            val idle = TestRunJournal()
            assertFalse(runner(scope, idle) { false }.startTool(source, request, { error("stale") }) { error("stale") })
            assertTrue(idle.states.isEmpty())
            // Editor changes while the intent is being written: no launch, no published outcome.
            var current = true
            val changing = object : ActionRunJournal {
                val inner = TestRunJournal()
                override suspend fun begin(subject: RunSubject, provider: String?, model: String?) =
                    inner.begin(subject, provider, model).also { current = false }
                override suspend fun finish(id: String, state: ActionRunState) = inner.finish(id, state)
            }
            assertTrue(runner(scope, changing) { current }.startTool(source, request, { error("stale") }) { error("stale") })
            assertEquals(ActionRunState.CANCELLED, changing.inner.states.values.single())
            // The intent cannot be stored: the launcher is never reached.
            val broken = object : ActionRunJournal {
                override suspend fun begin(subject: RunSubject, provider: String?, model: String?): String = throw RunStoreFailure()
                override suspend fun finish(id: String, state: ActionRunState) = error("never begun")
            }
            var failure: Throwable? = null
            assertTrue(runner(scope, broken).startTool(source, request, { error("no intent") }) { failure = it.exceptionOrNull() })
            assertEquals(CloudError.STORAGE, (failure as CloudFailure).error)
        } finally { scope.cancel() }
    }

    @Test fun withoutDurableJournalOnlyTheLocalTestToolRuns() {
        val scope = scope(); val journal = TestRunJournal(supportsWorkflowRecords = false)
        try {
            val source = source(); var text: String? = null; var failure: Throwable? = null
            val runner = runner(scope, journal)
            assertTrue(runner.startTool(source, Tools.prepare(ToolId.TEST_COUNT, source.text), { error("local") }) {
                text = (it.getOrThrow() as ToolOutcome.Text).text
            })
            assertEquals("chars=26 words=4 lines=2", text)
            assertTrue(runner.startTool(source, Tools.prepare(ToolId.CALENDAR_INSERT, source.text), { error("no journal") }) {
                failure = it.exceptionOrNull()
            })
            assertEquals(CloudError.STORAGE, (failure as CloudFailure).error)
            assertTrue(journal.states.isEmpty())
        } finally { scope.cancel() }
    }
}
