// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import kotlinx.serialization.json.*
import java.util.UUID

/** Fixed capabilities for natural-language configuration. Never executes or persists a proposal. */
object WorkflowBuilderContract {
    const val MAX_PROMPT_CHARS = 4096
    const val SCHEMA_NAME = "agent_keyboard_workflow_draft_v1"
    val instruction = """
        Design one Agent Keyboard text workflow as JSON matching the supplied schema.
        Configuration only: never execute tasks. The future input is the user's selected text;
        the current user message describes the workflow, not that selection.
        Use 1 to 8 steps and at most 3 AI steps. Every step has a unique short lowercase id.
        Input is "selection" or "step:<id>" referring only to an earlier step, never itself.
        Local actions: local.uppercase, local.lowercase, local.clean_spaces.
        AI actions: ai.correct, ai.translate, ai.rephrase, ai.summarize, ai.reply, ai.analyze.
        Provider and model are assigned by the app from the user's approved OpenAI model.
        AI language is a supported BCP47 code; tone is NEUTRAL, FRIENDLY or PROFESSIONAL.
        condition:null runs always. Otherwise use NON_EMPTY, CONTAINS_LITERAL (case sensitive)
        or LENGTH_AT_LEAST (UTF-16 units); negated inverts it. For CONTAINS_LITERAL use a
        nonempty literal of at most 128 UTF-16 units and minimumLength:0. LENGTH_AT_LEAST
        uses literal:"" and minimumLength:1..8192. NON_EMPTY uses literal:"" and minimumLength:0.
        A false condition passes the input through unchanged. The last step is the final result.
        ANALYZE results and their descendants cannot replace the selected text.
        No custom prompts, code, tools, URLs, API calls, credentials, schedules or nested workflows.
        If the request cannot be represented by these actions, return name:"" and steps:[].
        Otherwise choose a concise descriptive name of at most 64 UTF-16 units without controls.
    """.trimIndent()

    fun request(prompt: String, model: String): ModelRequest {
        require(ModelRequest.validModel(model) && prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARS)
        require(ExecutionPolicy().resultDenial(prompt) == null)
        return ModelRequest(model, instruction, prompt, ModelOutputFormat.WORKFLOW_DRAFT)
    }

    /** New identity, revision and model binding always originate in this app, never in model output. */
    fun decode(text: String, approvedModel: String): WorkflowDefinition {
        require(ModelRequest.validModel(approvedModel) && ExecutionPolicy().resultDenial(text) == null)
        require(text.toByteArray(Charsets.UTF_8).size <= WorkflowStore.MAX_BYTES)
        val proposal = Json.parseToJsonElement(text).jsonObject
        require(proposal.keys == setOf("name", "steps"))
        val steps = proposal.getValue("steps").jsonArray.map { raw ->
            val step = raw.jsonObject
            val kind = step.getValue("kind").jsonPrimitive
            require(kind.isString)
            val common = setOf("id", "input", "condition", "kind", "action")
            when (kind.content) {
                "local" -> { require(step.keys == common); step }
                "ai" -> {
                    require(step.keys == common + setOf("language", "tone"))
                    JsonObject(step + mapOf("provider" to JsonPrimitive(WorkflowProvider.OPENAI.id),
                        "model" to JsonPrimitive(approvedModel)))
                }
                else -> error("Invalid draft step")
            }
        }
        // Reuse the complete versioned codec and core validation, including backward references,
        // supported actions, UTF-16, conditions, structural limits and immutable defensive copies.
        val configuration = buildJsonObject {
            put("formatVersion", WorkflowDefinition.FORMAT_VERSION)
            put("kind", "agent-keyboard/workflows")
            put("workflows", buildJsonArray { add(buildJsonObject {
                put("id", UUID.randomUUID().toString()); put("revision", 1)
                put("name", proposal.getValue("name")); put("steps", JsonArray(steps))
            }) })
        }
        return WorkflowStore.decode(configuration.toString()).single()
    }

    /** Closed objects, required fields and nested unions supported by Responses structured output. */
    fun schema(): JsonObject {
        val condition = obj(mapOf(
            "predicate" to choices(TextPredicate.entries.map { it.name }),
            "literal" to type("string"), "minimumLength" to type("integer"), "negated" to type("boolean")))
        val common = linkedMapOf<String, JsonElement>("id" to type("string"), "input" to type("string"),
            "condition" to buildJsonObject { put("anyOf", JsonArray(listOf(type("null"), condition))) })
        val local = obj(common + mapOf("kind" to choices(listOf("local")),
            "action" to choices(BuiltinAction.entries.filter { it.local != null }.map { it.id })))
        val ai = obj(common + mapOf("kind" to choices(listOf("ai")),
            "action" to choices(BuiltinAction.entries.filter { it.ai != null }.map { it.id }),
            "language" to choices(TranslationLanguage.entries.map { it.code }),
            "tone" to choices(WritingTone.entries.map { it.name })))
        return obj(mapOf("name" to type("string"), "steps" to buildJsonObject {
            put("type", "array")
            put("items", buildJsonObject { put("anyOf", JsonArray(listOf(local, ai))) })
        }))
    }

    private fun type(value: String) = buildJsonObject { put("type", value) }
    private fun choices(values: List<String>) = buildJsonObject {
        put("type", "string"); put("enum", JsonArray(values.map(::JsonPrimitive)))
    }
    private fun obj(properties: Map<String, JsonElement>) = buildJsonObject {
        put("type", "object"); put("properties", JsonObject(properties))
        put("required", JsonArray(properties.keys.map(::JsonPrimitive))); put("additionalProperties", false)
    }
}
