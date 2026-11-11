// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.BuiltinAction
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey

fun BuiltinAction.titleResource() = when (this) {
    BuiltinAction.NONE -> R.string.agent_skill_none
    BuiltinAction.UPPERCASE -> R.string.agent_action_uppercase
    BuiltinAction.LOWERCASE -> R.string.agent_action_lowercase
    BuiltinAction.CLEAN_SPACES -> R.string.agent_action_spaces
    BuiltinAction.CORRECT -> R.string.agent_cloud_correct
    BuiltinAction.TRANSLATE -> R.string.agent_cloud_translate
    BuiltinAction.REPHRASE -> R.string.agent_cloud_rephrase
    BuiltinAction.SUMMARIZE -> R.string.agent_cloud_summarize
    BuiltinAction.REPLY -> R.string.agent_cloud_reply
    BuiltinAction.ANALYZE -> R.string.agent_cloud_analyze
    BuiltinAction.WORKFLOW -> R.string.agent_skill_workflow
}

fun skillToolbarKey(slot: Int) = when (slot) {
    1 -> ToolbarKey.SKILL_1
    2 -> ToolbarKey.SKILL_2
    3 -> ToolbarKey.SKILL_3
    else -> error("Unknown skill slot")
}

internal fun workflowSkillLabel(name: String): String = name.trim().take(32).let {
    if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it
}
