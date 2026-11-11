// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.TextCondition
import dev.agentkeyboard.core.TextPredicate
import helium314.keyboard.latin.R

internal fun TextCondition.summary(context: Context): String {
    val rule = when (predicate) {
        TextPredicate.NON_EMPTY -> context.getString(R.string.agent_condition_non_empty)
        TextPredicate.CONTAINS_LITERAL -> context.getString(R.string.agent_condition_contains,
            literal.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\"", "\\\"")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"))
        TextPredicate.LENGTH_AT_LEAST -> context.getString(R.string.agent_condition_length, minimumLength)
    }
    return context.getString(if (negated) R.string.agent_condition_negated else R.string.agent_condition_required, rule)
}
