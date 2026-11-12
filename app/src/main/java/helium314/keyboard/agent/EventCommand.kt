// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.DateFormatting
import dev.agentkeyboard.core.EventParser
import dev.agentkeyboard.core.LocalMoment
import helium314.keyboard.latin.R

/**
 * `@termin morgen 18 Uhr Zahnarzt` (stage 24f): reads date and time on the device
 * ([EventParser]) and offers one chip that opens the calendar app's "new event" screen with that
 * start, one hour long (all day without a time), and the remaining words as title
 * ([CalendarToolLauncher.eventDraft]). No calendar permission: the calendar app saves the event
 * only if the user does there. The command text is removed only after the app was started.
 */
object EventCommand : KeyboardCommands.Command {
    override val name = "termin"
    override val protectsArgument = true

    const val DURATION_MILLIS = 60 * 60 * 1000L

    /** Replaced in tests, so that they need no calendar app. */
    @JvmStatic var launcher: SearchCommand.Launcher = SearchCommand.SYSTEM_LAUNCHER

    override fun description(context: Context) =
        context.getString(R.string.agent_command_event, KeyboardCommands.prefix(context).toString())

    /** The event draft for [found]: wall-clock start in the zone of [DateCommand.zone]. */
    fun intent(found: EventParser.Result): Intent {
        val zone = DateCommand.zone()
        val begin = found.start.toEpochMillis(zone)
        val end = if (found.time != null) begin + DURATION_MILLIS
            else LocalMoment(found.date.plusDays(1), found.start.time).toEpochMillis(zone)
        return CalendarToolLauncher.eventDraft(found.title, begin, end, allDay = found.time == null)
    }

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (call.argument.isBlank()) return emptyList()
        val now = LocalMoment.of(DateCommand.clock(), DateCommand.zone())
        val found = EventParser.parse(call.argument, now, host.language) ?: return emptyList()
        val whenText = DateFormatting.short(found.date, found.time, DateFormatting.Language.of(host.language))
        val label = if (found.title.isEmpty()) host.context.getString(R.string.agent_command_event_chip_untitled, whenText)
            else host.context.getString(R.string.agent_command_event_chip, whenText, found.title)
        return listOf(KeyboardCommands.Chip(label, host.context.getString(R.string.agent_command_event_open, label)) {
            if (launcher.start(host.context, intent(found))) {
                // only after a successful start, like SearchCommand (P43)
                host.replace(call, "")
            } else Toast.makeText(host.context, R.string.agent_command_event_none, Toast.LENGTH_SHORT).show()
        })
    }
}
