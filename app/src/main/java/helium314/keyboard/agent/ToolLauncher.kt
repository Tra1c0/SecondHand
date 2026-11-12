// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import dev.agentkeyboard.core.ToolId
import dev.agentkeyboard.core.ToolRequest

/** What an approved tool run produced. A hand-over says nothing about what the other app saved. */
sealed interface ToolOutcome {
    class Text(val text: String) : ToolOutcome {
        override fun toString() = "ToolOutcome.Text(chars=${text.length})"
    }
    data object HandedOff : ToolOutcome
}

/** No installed app accepted the hand-over; nothing left the keyboard. */
class ToolUnavailable : Exception("TOOL_UNAVAILABLE")

/** Performs the single external effect of an approved request. Returns false if no app took it. */
fun interface ToolLauncher {
    fun launch(request: ToolRequest): Boolean
}

/**
 * Opens the calendar app's own "new event" screen with the previewed title and description.
 * The user completes or discards the event there. No calendar permission is requested and the
 * keyboard never reads the calendar.
 */
class CalendarToolLauncher(private val context: Context) : ToolLauncher {
    override fun launch(request: ToolRequest): Boolean {
        require(request.tool == ToolId.CALENDAR_INSERT)
        return try {
            context.startActivity(intent(request))
            true
        } catch (_: ActivityNotFoundException) { false }
        catch (_: SecurityException) { false }
    }

    companion object {
        fun intent(request: ToolRequest): Intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, request.value("title"))
            .putExtra(CalendarContract.Events.DESCRIPTION, request.value("description"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /**
         * The calendar app's "new event" screen with a start and end (`@termin`, stage 24f); like
         * [intent] it needs no calendar permission. [title] is left out when blank.
         */
        fun eventDraft(title: String, beginMillis: Long, endMillis: Long, allDay: Boolean): Intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .apply { if (title.isNotBlank()) putExtra(CalendarContract.Events.TITLE, title) }
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
