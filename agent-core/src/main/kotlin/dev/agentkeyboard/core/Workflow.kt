// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Collections
import java.util.UUID

enum class WorkflowProvider(val id: String) { OPENAI("openai"), CLAUDE("claude"), GEMINI("gemini"), LOCAL("local") }

sealed interface WorkflowInput {
    data object Selection : WorkflowInput
    data class StepResult(val stepId: String) : WorkflowInput
}

sealed interface WorkflowStep {
    val id: String
    val input: WorkflowInput
    val condition: TextCondition?

    data class Local(override val id: String, override val input: WorkflowInput, val action: LocalTextAction,
                     override val condition: TextCondition? = null) : WorkflowStep
    data class Ai(override val id: String, override val input: WorkflowInput,
                  val action: AiTextAction, val provider: WorkflowProvider, val model: String,
                  val language: TranslationLanguage = TranslationLanguage.ENGLISH,
                  val tone: WritingTone = WritingTone.NEUTRAL,
                  override val condition: TextCondition? = null) : WorkflowStep
}

/** Immutable, versioned configuration. No URLs, code, tools, credentials or editor authority. */
class WorkflowDefinition(val id: String, val revision: Int, val name: String, steps: List<WorkflowStep>) {
    val steps: List<WorkflowStep> = Collections.unmodifiableList(steps.toList())
    init {
        require(UUID.fromString(id).toString() == id && revision in 1..1_000_000)
        require(name.isNotBlank() && name.length <= 64 && ExecutionPolicy().resultDenial(name) == null)
        require(name.none { it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' ||
            it in '\u200E'..'\u200F' || it == '\u061C' })
        require(this.steps.size in 1..MAX_STEPS)
        val preceding = hashSetOf<String>()
        this.steps.forEach { step ->
            require(step.id.matches(Regex("[a-z][a-z0-9_]{0,23}")) && step.id !in preceding)
            require(step.input == WorkflowInput.Selection ||
                (step.input as? WorkflowInput.StepResult)?.stepId in preceding)
            if (step is WorkflowStep.Ai) require(ModelRequest.validModel(step.model))
            preceding.add(step.id)
        }
        require(this.steps.count { it is WorkflowStep.Ai } <= MAX_MODEL_CALLS)
    }

    override fun toString() = "WorkflowDefinition(id=$id, revision=$revision, steps=${steps.size})"

    companion object {
        const val FORMAT_VERSION = 3
        const val MAX_STEPS = 8
        const val MAX_MODEL_CALLS = 3
    }
}

enum class WorkflowError { INVALID_INPUT, INVALID_OUTPUT, TEXT_BUDGET, UNSUPPORTED_PROVIDER }
class WorkflowFailure(val error: WorkflowError) : Exception(error.name)

class WorkflowResult(val text: String, val allowsReplacement: Boolean,
                     executedSteps: List<String> = emptyList(), skippedSteps: List<String> = emptyList()) {
    val executedSteps: List<String> = Collections.unmodifiableList(executedSteps.toList())
    val skippedSteps: List<String> = Collections.unmodifiableList(skippedSteps.toList())
    override fun toString() = "WorkflowResult(chars=${text.length}, allowsReplacement=$allowsReplacement, executed=${executedSteps.size}, skipped=${skippedSteps.size})"
}

/** Deterministic sequential runner. The host supplies approval, cancellation, timeout and journal. */
class WorkflowRunner(
    private val availableProviders: Set<WorkflowProvider>,
    private val runAi: suspend (WorkflowStep.Ai, String) -> String,
    private val checkpoint: () -> Unit = {},
) {
    suspend fun execute(definition: WorkflowDefinition, selection: String): WorkflowResult {
        if (definition.steps.filterIsInstance<WorkflowStep.Ai>().any { it.provider !in availableProviders })
            throw WorkflowFailure(WorkflowError.UNSUPPORTED_PROVIDER)
        val policy = ExecutionPolicy()
        if (selection.length !in 1..policy.maxSelectionChars || policy.resultDenial(selection) != null)
            throw WorkflowFailure(WorkflowError.INVALID_INPUT)
        val results = hashMapOf<String, WorkflowResult>()
        val executed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var totalChars = selection.length
        definition.steps.forEach { step ->
            checkpoint()
            val input = when (val ref = step.input) {
                WorkflowInput.Selection -> WorkflowResult(selection, true)
                is WorkflowInput.StepResult -> results.getValue(ref.stepId)
            }
            val execute = step.condition?.matches(input.text) != false
            if (execute && step is WorkflowStep.Ai && input.text.length !in 1..policy.maxSelectionChars)
                throw WorkflowFailure(WorkflowError.INVALID_INPUT)
            val text = if (!execute) input.text else when (step) {
                is WorkflowStep.Local -> step.action.transform(input.text)
                is WorkflowStep.Ai -> runAi(step, input.text)
            }
            checkpoint()
            if (policy.resultDenial(text) != null) throw WorkflowFailure(WorkflowError.INVALID_OUTPUT)
            if (execute) totalChars += text.length // Skips retain the existing immutable string, without a new output.
            if (totalChars > MAX_TOTAL_CHARS) throw WorkflowFailure(WorkflowError.TEXT_BUDGET)
            val allowsReplacement = input.allowsReplacement && (!execute || step !is WorkflowStep.Ai || step.action.allowsReplacement)
            results[step.id] = WorkflowResult(text, allowsReplacement)
            if (execute) executed.add(step.id) else skipped.add(step.id)
        }
        val result = results.getValue(definition.steps.last().id)
        return WorkflowResult(result.text, result.allowsReplacement, executed, skipped)
    }

    companion object { const val MAX_TOTAL_CHARS = 32_768 }
}
