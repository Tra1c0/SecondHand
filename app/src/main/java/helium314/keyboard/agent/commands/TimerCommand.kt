// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.TimerParser
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SearchCommand
import helium314.keyboard.latin.R

/**
 * `@timer 10 min Nudeln`: reads the duration on the device ([TimerParser], at most 24 hours) and
 * offers one chip that opens the clock app's "new timer" screen ([AlarmClock.ACTION_SET_TIMER])
 * with the length in seconds and the remaining words as label. The clock app shows the timer (no
 * [AlarmClock.EXTRA_SKIP_UI]), so the user starts it there. Same permission and same rules as
 * [AlarmCommand]: nothing before the tap, the command text goes only after a successful start.
 */
object TimerCommand : KeyboardCommands.Command {
    override val name = "timer"

    override fun templates(host: KeyboardCommands.Host) =
        listOf("5 min", "10 min", "halbe Stunde", "1 h").map { KeyboardCommands.Template(it, "$it ") }
    override val protectsArgument = true

    /** Replaced in tests, so that they need no clock app. */
    @JvmStatic var launcher: SearchCommand.Launcher = SearchCommand.SYSTEM_LAUNCHER

    override fun description(context: Context) =
        context.getString(R.string.agent_command_timer, KeyboardCommands.prefix(context).toString())

    /** The "set timer" intent; the keyboard is a service, so it starts a new task. */
    fun intent(seconds: Int, title: String): Intent = Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        .apply { if (title.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, title) }
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (call.argument.isBlank()) return emptyList()
        val context = host.context
        val found = TimerParser.parse(call.argument)
            ?: return listOf(ClockCommands.hint(context, context.getString(R.string.agent_command_timer_hint),
                context.getString(R.string.agent_command_timer_usage, call.prefix.toString())))
        val length = TimerParser.format(found.seconds)
        val label = if (found.title.isEmpty()) context.getString(R.string.agent_command_timer_chip_untitled, length)
            else context.getString(R.string.agent_command_timer_chip, length, found.title)
        return listOf(KeyboardCommands.Chip(label, context.getString(R.string.agent_command_timer_open, label)) {
            ClockCommands.start(host, call, launcher, intent(found.seconds, found.title))
        })
    }
}
