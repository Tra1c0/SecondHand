// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.ToolId
import org.json.JSONObject

internal object RunSubjectCodec {
    fun encode(subject: RunSubject): JSONObject = when (subject) {
        is RunSubject.TextAction -> JSONObject().put("kind", "text-action").put("action", subject.action.name)
            .put("workflow", subject.workflow?.let { link -> JSONObject().put("runId", link.runId)
                .put("definitionId", link.definitionId).put("revision", link.revision).put("stepId", link.stepId) } ?: JSONObject.NULL)
        is RunSubject.Workflow -> JSONObject().put("kind", "workflow").put("definitionId", subject.definitionId).put("revision", subject.revision)
        RunSubject.WorkflowBuilder -> JSONObject().put("kind", "workflow-builder")
        is RunSubject.Tool -> JSONObject().put("kind", "tool").put("tool", subject.tool.id)
    }

    /** Tool subjects exist from schema 3; an older document cannot introduce them. */
    fun decode(value: JSONObject, version: Int = SCHEMA): RunSubject = when (value.strictString("kind")) {
        "text-action" -> {
            value.requireFields(setOf("kind", "action", "workflow"))
            val raw = value.get("workflow")
            val link = if (raw == JSONObject.NULL) null else {
                val row = raw as? JSONObject ?: error("INVALID")
                row.requireFields(setOf("runId", "definitionId", "revision", "stepId"))
                WorkflowStepLink(row.strictString("runId"), row.strictString("definitionId"), row.strictInt("revision"), row.strictString("stepId"))
            }
            RunSubject.TextAction(AiTextAction.valueOf(value.strictString("action")), link)
        }
        "workflow" -> {
            value.requireFields(setOf("kind", "definitionId", "revision"))
            RunSubject.Workflow(value.strictString("definitionId"), value.strictInt("revision"))
        }
        "workflow-builder" -> {
            value.requireFields(setOf("kind"))
            RunSubject.WorkflowBuilder
        }
        "tool" -> {
            require(version >= 3)
            value.requireFields(setOf("kind", "tool"))
            RunSubject.Tool(ToolId.fromId(value.strictString("tool")) ?: error("INVALID"))
        }
        else -> error("INVALID")
    }

    const val SCHEMA = 3

    fun validate(subject: RunSubject, provider: String?, model: String?) {
        if (subject is RunSubject.Workflow || subject is RunSubject.Tool) require(provider == null && model == null)
        else require(provider != null && provider.matches(Regex("[a-z0-9._-]{1,40}")) && model != null && ModelSettings.validModel(model))
    }
}

internal fun JSONObject.requireFields(fields: Set<String>) = require(keys().asSequence().toSet() == fields)
internal fun JSONObject.strictString(key: String) = get(key) as? String ?: error("INVALID")
internal fun JSONObject.nullableString(key: String): String? = get(key).let { if (it == JSONObject.NULL) null else it as? String ?: error("INVALID") }
internal fun JSONObject.strictLong(key: String): Long = (get(key) as? Number)?.let {
    require(it is Int || it is Long); it.toLong()
} ?: error("INVALID")
internal fun JSONObject.strictInt(key: String): Int = strictLong(key).let {
    require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()); it.toInt()
}
