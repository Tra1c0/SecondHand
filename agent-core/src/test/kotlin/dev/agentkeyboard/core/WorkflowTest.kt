// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import java.util.UUID
import kotlin.coroutines.*
import kotlin.test.*

class WorkflowTest {
    private val selection = WorkflowInput.Selection
    private fun definition(vararg steps: WorkflowStep) = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Example", steps.toList())
    private fun local(id: String = "first", input: WorkflowInput = selection, action: LocalTextAction = LocalTextAction.UPPERCASE) =
        WorkflowStep.Local(id, input, action)
    private fun ai(id: String = "second", input: WorkflowInput = WorkflowInput.StepResult("first"),
                   action: AiTextAction = AiTextAction.CORRECT, provider: WorkflowProvider = WorkflowProvider.OPENAI) =
        WorkflowStep.Ai(id, input, action, provider, "model", TranslationLanguage.GERMAN, WritingTone.PROFESSIONAL)
    private fun runner(runAi: suspend (WorkflowStep.Ai, String) -> String = { _, _ -> error("must stay offline") }) =
        WorkflowRunner(setOf(WorkflowProvider.OPENAI), runAi)

    private fun <T> execute(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }

    @Test fun localChainIsSequentialAndNeedsNoProvider() {
        val workflow = definition(local(), local("second", WorkflowInput.StepResult("first"), LocalTextAction.CLEAN_SPACES))
        val result = execute { runner().execute(workflow, "  Grüße  ") }
        assertEquals("GRÜSSE", result.text)
        assertTrue(result.allowsReplacement)
    }

    @Test fun modelReceivesOnlyTheDeclaredIntermediateAndFrozenParameters() {
        var calls = 0
        val workflow = definition(local(), ai(action = AiTextAction.TRANSLATE))
        val result = execute { runner { step, input ->
            calls++; assertEquals("HALLO", input)
            assertEquals(TranslationLanguage.GERMAN, step.language)
            assertEquals(WritingTone.PROFESSIONAL, step.tone)
            "Ergebnis"
        }.execute(workflow, "Hallo") }
        assertEquals(1, calls)
        assertEquals("Ergebnis", result.text)
    }

    @Test fun explicitSelectionReferenceDoesNotAccidentallyUseThePreviousResult() {
        val result = execute { runner().execute(definition(local(), local("second", selection, LocalTextAction.LOWERCASE)), "Hallo") }
        assertEquals("hallo", result.text)
    }

    @Test fun forwardSelfMissingAndDuplicateReferencesAreRejectedBeforeAnyExecution() {
        for (reference in listOf("first", "second", "absent")) {
            assertFailsWith<IllegalArgumentException> { definition(local(input = WorkflowInput.StepResult(reference)), local("second")) }
        }
        assertFailsWith<IllegalArgumentException> { definition(local(), local()) }
    }

    @Test fun configurationCannotContainUrlsSpoofedLabelsOrExcessiveSteps() {
        assertFailsWith<IllegalArgumentException> { definition(ai("first", selection).copy(model = "https://invalid.test")) }
        assertFailsWith<IllegalArgumentException> { definition(local("bad\ncode")) }
        assertFailsWith<IllegalArgumentException> { definition(*(1..9).map { local("step$it") }.toTypedArray()) }
        assertFailsWith<IllegalArgumentException> { definition() }
        for (name in listOf("", " ", "x".repeat(65), "safe\u202Ehidden", "line\nbreak", "\uD800")) {
            assertFailsWith<IllegalArgumentException> { WorkflowDefinition(UUID.randomUUID().toString(), 1, name, listOf(local())) }
        }
    }

    @Test fun changingTheCallersListCannotChangeAnApprovedDefinition() {
        val original = mutableListOf<WorkflowStep>(local())
        val workflow = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Example", original)
        original.clear()
        assertEquals(1, workflow.steps.size)
        assertFails { (workflow.steps as MutableList).clear() }
    }

    @Test fun unavailableSecondProviderIsRejectedBeforeTheFirstRequest() {
        var calls = 0
        val workflow = definition(ai("first", selection), ai(provider = WorkflowProvider.CLAUDE))
        val failure = assertFailsWith<WorkflowFailure> { execute { runner { _, _ -> calls++; "result" }.execute(workflow, "Hallo") } }
        assertEquals(WorkflowError.UNSUPPORTED_PROVIDER, failure.error)
        assertEquals(0, calls)
    }

    @Test fun oversizedOrMalformedSelectionNeverReachesTheProvider() {
        val workflow = definition(ai("first", selection))
        for (input in listOf("", "x".repeat(8193), "\uD800")) {
            val failure = assertFailsWith<WorkflowFailure> { execute { runner().execute(workflow, input) } }
            assertEquals(WorkflowError.INVALID_INPUT, failure.error)
        }
    }

    @Test fun oversizedIntermediateIsNotSentToTheNextModel() {
        val workflow = definition(local(), ai())
        val failure = assertFailsWith<WorkflowFailure> { execute { runner().execute(workflow, "ß".repeat(8192)) } }
        assertEquals(WorkflowError.INVALID_INPUT, failure.error)
    }

    @Test fun malformedOrOversizedModelOutputCannotBecomeAResult() {
        val workflow = definition(ai("first", selection))
        for (output in listOf("\uDC00", "x".repeat(16385))) {
            val failure = assertFailsWith<WorkflowFailure> { execute { runner { _, _ -> output }.execute(workflow, "Hallo") } }
            assertEquals(WorkflowError.INVALID_OUTPUT, failure.error)
        }
    }

    @Test fun totalTextBudgetBoundsEvenLocalExpansion() {
        val workflow = definition(local(), local("second", WorkflowInput.StepResult("first")))
        val failure = assertFailsWith<WorkflowFailure> { execute { runner().execute(workflow, "ß".repeat(8192)) } }
        assertEquals(WorkflowError.TEXT_BUDGET, failure.error)
    }

    @Test fun analysisRemainsReadOnlyAfterALocalTransformation() {
        val workflow = definition(ai("first", selection, AiTextAction.ANALYZE), local("second", WorkflowInput.StepResult("first")))
        val result = execute { runner { _, _ -> "uncertain interpretation" }.execute(workflow, "Hallo") }
        assertEquals("UNCERTAIN INTERPRETATION", result.text)
        assertFalse(result.allowsReplacement)
    }

    @Test fun checkpointFailureStopsBeforeAnyFurtherModelRequest() {
        var checkpoints = 0; var calls = 0
        val runner = WorkflowRunner(setOf(WorkflowProvider.OPENAI), { _, _ -> calls++; "result" }, {
            if (++checkpoints == 2) throw IllegalStateException("stale editor")
        })
        assertFailsWith<IllegalStateException> { execute { runner.execute(definition(local(), ai()), "Hallo") } }
        assertEquals(0, calls)
    }

    @Test fun diagnosticsDoNotRevealSourceResultOrDefinitionName() {
        assertFalse(ModelRequest("model", "secret instruction", "secret input").toString().contains("secret"))
        assertFalse(WorkflowResult("secret result", false).toString().contains("secret"))
        val workflow = WorkflowDefinition(UUID.randomUUID().toString(), 1, "secret name", listOf(local()))
        assertFalse(workflow.toString().contains("secret"))
    }

    @Test fun literalConditionsNeverInterpretRegexAndNegationIsExplicit() {
        val condition = TextCondition(TextPredicate.CONTAINS_LITERAL, ".*")
        assertFalse(condition.matches("any text"))
        assertTrue(condition.matches("literal .* inside"))
        assertTrue(condition.copy(negated = true).matches("any text"))
        assertFalse(TextCondition(TextPredicate.CONTAINS_LITERAL, "Hello").matches("hello"))
        assertTrue(TextCondition(TextPredicate.NON_EMPTY).matches(" "))
        assertFalse(TextCondition(TextPredicate.NON_EMPTY).matches(""))
    }

    @Test fun conditionBoundsAndUtf16ThresholdsAreDeterministic() {
        assertTrue(TextCondition(TextPredicate.LENGTH_AT_LEAST, minimumLength = 2).matches("😀"))
        assertFalse(TextCondition(TextPredicate.LENGTH_AT_LEAST, minimumLength = 3).matches("😀"))
        for (length in listOf(0, 8193)) assertFails { TextCondition(TextPredicate.LENGTH_AT_LEAST, minimumLength = length) }
        for (literal in listOf("", "x".repeat(129), "\uD800", "\u202Ehidden", "\u0000"))
            assertFails { TextCondition(TextPredicate.CONTAINS_LITERAL, literal) }
        assertFails { TextCondition(TextPredicate.NON_EMPTY, "unused") }
        assertFails { TextCondition(TextPredicate.CONTAINS_LITERAL, "hello", minimumLength = 1) }
    }

    @Test fun conditionalDiagnosticsDoNotRevealLiteralConfiguration() {
        assertFalse(TextCondition(TextPredicate.CONTAINS_LITERAL, "secret pattern").toString().contains("secret"))
    }

    @Test fun falseConditionalAnalysisDoesNotCallAModelOrRestrictUnanalyzedInput() {
        val step = ai("first", selection, AiTextAction.ANALYZE).copy(
            condition = TextCondition(TextPredicate.CONTAINS_LITERAL, "absent"))
        val result = execute { runner().execute(definition(step), "Hallo") }
        assertEquals("Hallo", result.text)
        assertTrue(result.allowsReplacement)
        assertEquals(emptyList(), result.executedSteps)
        assertEquals(listOf("first"), result.skippedSteps)
    }

    @Test fun trueConditionalAnalysisStillPreventsReplacementDownstream() {
        val step = ai("first", selection, AiTextAction.ANALYZE).copy(condition = TextCondition(TextPredicate.NON_EMPTY))
        val workflow = definition(step, local("second", WorkflowInput.StepResult("first")))
        val result = execute { runner { _, _ -> "uncertain" }.execute(workflow, "Hallo") }
        assertEquals("UNCERTAIN", result.text)
        assertFalse(result.allowsReplacement)
        assertEquals(listOf("first", "second"), result.executedSteps)
        assertTrue(result.skippedSteps.isEmpty())
    }

    @Test fun emptyIntermediateCanBypassAModelWithoutBecomingAnInvalidRequest() {
        val workflow = definition(local(action = LocalTextAction.CLEAN_SPACES),
            ai().copy(condition = TextCondition(TextPredicate.NON_EMPTY)))
        val result = execute { runner().execute(workflow, "   ") }
        assertEquals("", result.text)
        assertTrue(result.allowsReplacement)
    }

    @Test fun skippedModelCannotRemoveAnEarlierAnalysisRestriction() {
        var calls = 0
        val workflow = definition(ai("first", selection, AiTextAction.ANALYZE),
            ai().copy(condition = TextCondition(TextPredicate.CONTAINS_LITERAL, "absent")))
        val result = execute { runner { _, _ -> calls++; "analysis" }.execute(workflow, "Hallo") }
        assertEquals(1, calls)
        assertEquals("analysis", result.text)
        assertFalse(result.allowsReplacement)
    }

    @Test fun skippedStepReusesItsInputWithoutAllocatingAnotherBudgetedOutput() {
        val workflow = definition(local(), ai().copy(condition = TextCondition(TextPredicate.CONTAINS_LITERAL, "absent")))
        val result = execute { runner().execute(workflow, "ß".repeat(8192)) }
        assertEquals(16384, result.text.length)
        assertTrue(result.allowsReplacement)
    }

    @Test fun eightBoundedLocalStepsExecuteInDeclaredOrder() {
        val steps = (1..8).map { number -> local("step$number",
            if (number == 1) selection else WorkflowInput.StepResult("step${number - 1}"), LocalTextAction.CLEAN_SPACES) }
        val result = execute { runner().execute(definition(*steps.toTypedArray()), "  Hallo  ") }
        assertEquals("Hallo", result.text)
        assertEquals(steps.map { it.id }, result.executedSteps)
    }

    @Test fun nonAdjacentReferenceUsesTheNamedEarlierResult() {
        val workflow = definition(local(), local("second", WorkflowInput.StepResult("first"), LocalTextAction.LOWERCASE),
            local("third", WorkflowInput.StepResult("first"), LocalTextAction.CLEAN_SPACES))
        val result = execute { runner().execute(workflow, "Hallo") }
        assertEquals("HALLO", result.text)
    }

    @Test fun modelRequestBudgetIsIndependentFromTheLargerStepBudget() {
        assertFailsWith<IllegalArgumentException> { definition(*(1..4).map { ai("step$it", selection) }.toTypedArray()) }
        var calls = 0
        val result = execute { runner { _, _ -> calls++; "result" }.execute(
            definition(*(1..3).map { ai("step$it", selection) }.toTypedArray()), "Hallo") }
        assertEquals(3, calls)
        assertEquals("result", result.text)
    }
}
