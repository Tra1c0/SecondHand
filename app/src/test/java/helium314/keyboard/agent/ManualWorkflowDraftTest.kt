// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class ManualWorkflowDraftTest {
    @Test fun creationNeedsAValidNameAndKeepsModelParametersFixed() {
        val draft = ManualWorkflowDraft.new("chosen-model", TranslationLanguage.FRENCH, WritingTone.FRIENDLY)
        assertFails { draft.definition() }
        val definition = draft.copy(name = "My workflow").definition()
        assertEquals(1, definition.revision)
        assertEquals("chosen-model", (definition.steps[1] as WorkflowStep.Ai).model)
        assertEquals(TranslationLanguage.FRENCH, (definition.steps[1] as WorkflowStep.Ai).language)
    }

    @Test fun proposalPreservesRevisionOneAndReferenceMeaningBeforeFirstSave() {
        val definition = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Proposal", listOf(
            WorkflowStep.Ai("generated", WorkflowInput.Selection, AiTextAction.CORRECT, WorkflowProvider.OPENAI, "approved-model"),
            WorkflowStep.Local("tail", WorkflowInput.StepResult("generated"), LocalTextAction.CLEAN_SPACES)))
        val draft = ManualWorkflowDraft.proposal(definition).definition()
        assertEquals(definition.id, draft.id); assertEquals(1, draft.revision)
        assertEquals("approved-model", (draft.steps.first() as WorkflowStep.Ai).model)
        assertEquals(WorkflowInput.StepResult("step1"), draft.steps.last().input)
        assertFails { ManualWorkflowDraft.proposal(WorkflowDefinition(definition.id, 2, definition.name, definition.steps)) }
    }

    @Test fun editingPreservesProviderAndInputMeaningWhileAdvancingRevision() {
        val original = WorkflowDefinition(UUID.randomUUID().toString(), 7, "Imported", listOf(
            WorkflowStep.Ai("custom_id", WorkflowInput.Selection, AiTextAction.ANALYZE, WorkflowProvider.CLAUDE, "chosen-model"),
            WorkflowStep.Local("other_id", WorkflowInput.StepResult("custom_id"), LocalTextAction.UPPERCASE)))
        val edited = ManualWorkflowDraft.edit(original).definition()
        assertEquals(original.id, edited.id)
        assertEquals(8, edited.revision)
        assertEquals(WorkflowProvider.CLAUDE, (edited.steps[0] as WorkflowStep.Ai).provider)
        assertEquals(WorkflowInput.StepResult(edited.steps[0].id), edited.steps[1].input)
    }

    @Test fun explicitOriginalInputIsNotChangedIntoAnIntermediateReference() {
        val draft = ManualWorkflowDraft.new().copy(name = "Example")
        val definition = draft.copy(steps = draft.steps.map { it.copy(inputStepIndex = null) }).definition()
        assertTrue(definition.steps.all { it.input == WorkflowInput.Selection })
        assertFails { draft.copy(steps = listOf(ManualWorkflowStep(BuiltinAction.NONE))).definition() }
        assertFails { draft.copy(steps = listOf(ManualWorkflowStep(BuiltinAction.WORKFLOW))).definition() }
    }

    @Test fun editingPreservesImportedConditionsWhileIncompleteFieldsStayInTheDraft() {
        val original = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Conditional", listOf(
            WorkflowStep.Local("first", WorkflowInput.Selection, LocalTextAction.UPPERCASE,
                TextCondition(TextPredicate.CONTAINS_LITERAL, "marker", negated = true))))
        val draft = ManualWorkflowDraft.edit(original)
        assertEquals(original.steps.single().condition, draft.definition().steps.single().condition)
        val incomplete = draft.copy(steps = draft.steps.map { it.copy(condition = ManualWorkflowCondition(TextPredicate.LENGTH_AT_LEAST)) })
        assertFails { incomplete.definition() }
        val complete = incomplete.copy(steps = incomplete.steps.map { it.copy(condition = it.condition!!.copy(minimumLength = "100")) })
        assertEquals(100, complete.definition().steps.single().condition!!.minimumLength)
    }

    @Test fun importedNonAdjacentReferencesSurviveCanonicalRenamingAndEditing() {
        val original = WorkflowDefinition(UUID.randomUUID().toString(), 1, "Graph", listOf(
            WorkflowStep.Local("upper", WorkflowInput.Selection, LocalTextAction.UPPERCASE),
            WorkflowStep.Local("lower", WorkflowInput.StepResult("upper"), LocalTextAction.LOWERCASE),
            WorkflowStep.Local("again", WorkflowInput.StepResult("upper"), LocalTextAction.CLEAN_SPACES)))
        val draft = ManualWorkflowDraft.edit(original)
        assertEquals(0, draft.steps.last().inputStepIndex)
        assertEquals(WorkflowInput.StepResult("step1"), draft.definition().steps.last().input)
        val changed = draft.copy(steps = draft.steps.mapIndexed { index, step -> if (index == 2) step.copy(inputStepIndex = 1) else step })
        assertEquals(WorkflowInput.StepResult("step2"), changed.definition().steps.last().input)
        for (invalid in listOf(-1, 2, 3)) assertFails {
            draft.copy(steps = draft.steps.mapIndexed { index, step -> if (index == 2) step.copy(inputStepIndex = invalid) else step }).definition()
        }
    }
}
