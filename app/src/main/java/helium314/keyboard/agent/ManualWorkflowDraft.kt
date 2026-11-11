// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.providers.CloudProviders
import dev.agentkeyboard.core.*
import java.util.UUID

/** Draft fields may be incomplete; conversion is the single validation boundary. */
data class ManualWorkflowCondition(val predicate: TextPredicate, val literal: String = "",
                                   val minimumLength: String = "", val negated: Boolean = false) {
    fun condition() = TextCondition(predicate, literal,
        if (predicate == TextPredicate.LENGTH_AT_LEAST) requireNotNull(minimumLength.toIntOrNull()) else 0, negated)

    companion object {
        fun from(condition: TextCondition) = ManualWorkflowCondition(condition.predicate, condition.literal,
            if (condition.predicate == TextPredicate.LENGTH_AT_LEAST) condition.minimumLength.toString() else "", condition.negated)
    }
}

data class ManualWorkflowStep(
    val action: BuiltinAction,
    val inputStepIndex: Int? = null,
    val provider: WorkflowProvider = WorkflowProvider.OPENAI,
    val model: String = ModelSettings.DEFAULT_MODEL,
    val language: TranslationLanguage = TranslationLanguage.ENGLISH,
    val tone: WritingTone = WritingTone.NEUTRAL,
    val condition: ManualWorkflowCondition? = null,
)

/** Editing configuration never captures an editor or runs a provider. */
data class ManualWorkflowDraft(val id: String, val revision: Int, val name: String, val steps: List<ManualWorkflowStep>) {
    fun definition(): WorkflowDefinition = WorkflowDefinition(id, revision, name, steps.mapIndexed { index, step ->
        val input = step.inputStepIndex?.let { previous ->
            require(previous in 0 until index)
            WorkflowInput.StepResult("step${previous + 1}")
        } ?: WorkflowInput.Selection
        when {
            step.action.local != null -> WorkflowStep.Local("step${index + 1}", input, requireNotNull(step.action.local), step.condition?.condition())
            step.action.ai != null -> {
                // Stricter than the file format: a model ID must be usable by the step's own provider.
                require(CloudProviders.validModel(step.provider, step.model))
                WorkflowStep.Ai("step${index + 1}", input, requireNotNull(step.action.ai),
                    step.provider, step.model, step.language, step.tone, step.condition?.condition())
            }
            else -> error("Unassigned action")
        }
    })

    companion object {
        fun new(model: String = ModelSettings.DEFAULT_MODEL, language: TranslationLanguage = TranslationLanguage.ENGLISH,
                tone: WritingTone = WritingTone.NEUTRAL) = ManualWorkflowDraft(UUID.randomUUID().toString(), 1, "",
            listOf(ManualWorkflowStep(BuiltinAction.CLEAN_SPACES),
                ManualWorkflowStep(BuiltinAction.CORRECT, inputStepIndex = 0, model = model, language = language, tone = tone)))

        /** A generated proposal is new configuration, not an edit of a stored revision. */
        fun proposal(definition: WorkflowDefinition): ManualWorkflowDraft {
            require(definition.revision == 1)
            return edit(definition).copy(revision = 1)
        }

        fun edit(definition: WorkflowDefinition) = ManualWorkflowDraft(definition.id, definition.revision + 1,
            definition.name, definition.steps.map { step ->
                val inputIndex = (step.input as? WorkflowInput.StepResult)?.let { reference ->
                    definition.steps.indexOfFirst { it.id == reference.stepId }.also { require(it >= 0) }
                }
                when (step) {
                    is WorkflowStep.Local -> ManualWorkflowStep(BuiltinAction.entries.single { it.local == step.action },
                        inputIndex, condition = step.condition?.let(ManualWorkflowCondition::from))
                    is WorkflowStep.Ai -> ManualWorkflowStep(BuiltinAction.entries.single { it.ai == step.action },
                        inputIndex, step.provider, step.model, step.language, step.tone,
                        step.condition?.let(ManualWorkflowCondition::from))
                }
            })
    }
}
