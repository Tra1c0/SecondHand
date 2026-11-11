// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.UUID

/** Stable file identifiers. Imports cannot introduce code, endpoints or credential references. */
enum class BuiltinAction(val id: String, val local: LocalTextAction? = null, val ai: AiTextAction? = null) {
    NONE("none"),
    UPPERCASE("local.uppercase", local = LocalTextAction.UPPERCASE),
    LOWERCASE("local.lowercase", local = LocalTextAction.LOWERCASE),
    CLEAN_SPACES("local.clean_spaces", local = LocalTextAction.CLEAN_SPACES),
    CORRECT("ai.correct", ai = AiTextAction.CORRECT),
    TRANSLATE("ai.translate", ai = AiTextAction.TRANSLATE),
    REPHRASE("ai.rephrase", ai = AiTextAction.REPHRASE),
    SUMMARIZE("ai.summarize", ai = AiTextAction.SUMMARIZE),
    REPLY("ai.reply", ai = AiTextAction.REPLY),
    ANALYZE("ai.analyze", ai = AiTextAction.ANALYZE),
    WORKFLOW("workflow");

    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } }
}

data class SkillBinding(val slot: Int, val label: String, val action: BuiltinAction, val workflowId: String? = null) {
    init {
        require(slot in 1..3)
        require(label.length <= 32 && label.none {
            it.isISOControl() || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' ||
                it in '\u200E'..'\u200F' || it == '\u061C'
        })
        require(ExecutionPolicy().resultDenial(label) == null)
        require(action == BuiltinAction.NONE || label.isNotBlank())
        if (action == BuiltinAction.WORKFLOW) {
            require(workflowId != null && UUID.fromString(workflowId).toString() == workflowId)
        } else require(workflowId == null)
    }

    companion object { fun defaults() = (1..3).map { SkillBinding(it, "", BuiltinAction.NONE) } }
}
