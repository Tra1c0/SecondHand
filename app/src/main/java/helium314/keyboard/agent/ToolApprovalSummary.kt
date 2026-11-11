// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.ToolId
import dev.agentkeyboard.core.ToolRequest
import helium314.keyboard.latin.R

/** The exact target and every value that will be handed over, shown before the single approval. */
internal fun toolApprovalSummary(context: Context, request: ToolRequest): String = when (request.tool) {
    ToolId.TEST_COUNT -> context.getString(R.string.agent_tool_test_confirm, request.value("text").length)
    ToolId.CALENDAR_INSERT -> context.getString(R.string.agent_tool_calendar_confirm,
        request.value("title"), request.value("description").length, request.value("description"))
}

internal fun ToolId.titleResource() = when (this) {
    ToolId.TEST_COUNT -> R.string.agent_tool_test
    ToolId.CALENDAR_INSERT -> R.string.agent_tool_calendar
}
