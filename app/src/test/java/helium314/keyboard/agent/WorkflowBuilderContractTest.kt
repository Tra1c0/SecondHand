// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkflowBuilderContractTest {
    private val local = """{"id":"clean","input":"selection","condition":null,"kind":"local","action":"local.clean_spaces"}"""
    private val ai = """{"id":"translate","input":"step:clean","condition":null,"kind":"ai","action":"ai.translate","language":"de","tone":"PROFESSIONAL"}"""
    private fun draft(steps: String = "$local,$ai") = """{"name":"Kurze Übersetzung 😀","steps":[$steps]}"""

    @Test fun proposalBecomesANewImmutableDefinitionWithOnlyTheApprovedModel() {
        val first = WorkflowBuilderContract.decode(draft(), "approved-model")
        val second = WorkflowBuilderContract.decode(draft(), "approved-model")
        assertNotEquals(first.id, second.id)
        assertEquals(1, first.revision)
        assertEquals("Kurze Übersetzung 😀", first.name)
        assertEquals(WorkflowInput.StepResult("clean"), first.steps.last().input)
        val modelStep = first.steps.last() as WorkflowStep.Ai
        assertEquals(WorkflowProvider.OPENAI, modelStep.provider)
        assertEquals("approved-model", modelStep.model)
        assertEquals(TranslationLanguage.GERMAN, modelStep.language)
        assertEquals(WritingTone.PROFESSIONAL, modelStep.tone)
        assertEquals(first.steps, WorkflowStore.decode(WorkflowStore.encode(listOf(first))).single().steps)
        assertFalse(first.toString().contains(first.name))
    }

    @Test fun modelCannotSupplyAnExistingIdentityProviderModelPromptOrTool() {
        val valid = draft()
        val invalid = listOf(
            valid.replace("\"name\":", "\"id\":\"00000000-0000-0000-0000-000000000000\",\"name\":"),
            valid.replace("\"name\":", "\"revision\":2,\"name\":"),
            valid.replace("\"kind\":\"ai\"", "\"kind\":\"ai\",\"provider\":\"claude\""),
            valid.replace("\"kind\":\"ai\"", "\"kind\":\"ai\",\"model\":\"expensive-model\""),
            valid.replace("\"kind\":\"ai\"", "\"kind\":\"ai\",\"prompt\":\"custom\""),
            valid.replace("local.clean_spaces", "workflow"),
            valid.replace("\"kind\":\"local\"", "\"kind\":\"tool\""),
            valid.replace("\"kind\":\"local\"", "\"kind\":\"local\",\"url\":\"https://invalid.test\""),
        )
        invalid.forEach { assertFails { WorkflowBuilderContract.decode(it, "approved-model") } }
    }

    @Test fun conditionsAndNonAdjacentBackwardReferencesUseTheSameCoreValidation() {
        val conditional = local.replace("null", """{"predicate":"CONTAINS_LITERAL","literal":"marker","minimumLength":0,"negated":true}""")
        val middle = local.replace("\"clean\"", "\"middle\"")
        val definition = WorkflowBuilderContract.decode(draft("$conditional,$middle,$ai"), "model")
        assertEquals(TextCondition(TextPredicate.CONTAINS_LITERAL, "marker", negated = true), definition.steps.first().condition)
        assertEquals(WorkflowInput.StepResult("clean"), definition.steps.last().input)
        assertFails { WorkflowBuilderContract.decode(draft(conditional.replace("\"literal\":\"marker\"", "\"literal\":\"\"")), "model") }
        assertFails { WorkflowBuilderContract.decode(draft(ai.replace("step:clean", "step:translate")), "model") }
    }

    @Test fun malformedTypesUnicodeAndStructuralLimitsAreRejectedWithoutRepair() {
        val invalid = listOf("{}", "[]", "```json\n${draft()}\n```", draft() + " trailing",
            """{"name":"","steps":[]}""", draft().replace("\"name\":", "\"name\":true,\"unknown\":"),
            draft().replace("\"de\"", "false"), draft().replace("\"PROFESSIONAL\"", "\"custom\""),
            draft().replace("step:clean", "step:future"), draft().replace("\"translate\"", "\"clean\""),
            draft().replace("Kurze Übersetzung 😀", "\\uD800"), draft().replace("Kurze Übersetzung 😀", "x".repeat(65)),
            draft((1..9).joinToString(",") { local.replace("\"clean\"", "\"step$it\"") }),
            draft((1..4).joinToString(",") { ai.replace("\"translate\"", "\"step$it\"").replace("step:clean", "selection") }),
            "x".repeat(16385))
        invalid.forEach { assertFails { WorkflowBuilderContract.decode(it, "model") } }
        assertFails { WorkflowBuilderContract.decode(draft(), "https://invalid.test") }
    }

    @Test fun promptIsBoundedTextWithoutEditorAuthorityOrSavedContentInDiagnostics() {
        val request = WorkflowBuilderContract.request("Korrigiere danach übersetze", "model")
        assertEquals(ModelOutputFormat.WORKFLOW_DRAFT, request.outputFormat)
        assertEquals("Korrigiere danach übersetze", request.input)
        assertTrue(request.instruction.length in 1..4096)
        assertFalse(request.toString().contains(request.input))
        for (prompt in listOf("", "   ", "x".repeat(4097), "\uD800"))
            assertFails { WorkflowBuilderContract.request(prompt, "model") }
        assertEquals(4096, WorkflowBuilderContract.request("x".repeat(4096), "model").input.length)
        assertEquals(ModelOutputFormat.TEXT, ModelRequest("model", "instruction", "input").outputFormat)
    }

    @Test fun everySchemaObjectIsClosedAndAllItsFieldsAreRequired() {
        fun verify(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    if (element["type"] == JsonPrimitive("object")) {
                        assertEquals(JsonPrimitive(false), element["additionalProperties"])
                        assertEquals(element.getValue("properties").jsonObject.keys,
                            element.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet())
                    }
                    element.values.forEach(::verify)
                }
                is JsonArray -> element.forEach(::verify)
                else -> Unit
            }
        }
        val schema = WorkflowBuilderContract.schema()
        verify(schema)
        val variants = schema.getValue("properties").jsonObject.getValue("steps").jsonObject
            .getValue("items").jsonObject.getValue("anyOf").jsonArray
        assertEquals(2, variants.size)
        val modelStep = variants.last().jsonObject.getValue("properties").jsonObject
        assertFalse("provider" in modelStep || "model" in modelStep || "prompt" in modelStep)
        assertEquals(BuiltinAction.entries.filter { it.ai != null }.map { it.id }.toSet(),
            modelStep.getValue("action").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toSet())
    }
}
