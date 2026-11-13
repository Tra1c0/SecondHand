// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import dev.agentkeyboard.core.AlarmParser
import dev.agentkeyboard.core.ClockTime
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.DateFormatting
import dev.agentkeyboard.core.LocalMoment
import helium314.keyboard.agent.DateCommand
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SearchCommand
import helium314.keyboard.latin.R

/**
 * `@wecker 6:30 Training`: reads the time on the device ([AlarmParser]) and offers one chip that
 * opens the clock app's "new alarm" screen ([AlarmClock.ACTION_SET_ALARM]) with hour, minute and
 * the remaining words as label. The clock app shows the alarm (no [AlarmClock.EXTRA_SKIP_UI]), so
 * the user confirms it there. The intent has no day: a typed day that is not the next occurrence
 * of the time is shown as ignored on the chip and in a toast after the start.
 *
 * Needs the install-time permission `com.android.alarm.permission.SET_ALARM` (manifest), no prompt.
 * Nothing happens before the tap; the command text is removed only after the clock app started.
 */
object AlarmCommand : KeyboardCommands.Command {
    override val name = "wecker"
    override val protectsArgument = true

    /** Replaced in tests, so that they need no clock app. */
    @JvmStatic var launcher: SearchCommand.Launcher = SearchCommand.SYSTEM_LAUNCHER

    override fun description(context: Context) =
        context.getString(R.string.agent_command_alarm, KeyboardCommands.prefix(context).toString())

    /** The "set alarm" intent; the keyboard is a service, so it starts a new task. */
    fun intent(time: ClockTime, title: String): Intent = Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
        .apply { if (title.isNotEmpty()) putExtra(AlarmClock.EXTRA_MESSAGE, title) }
        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (call.argument.isBlank()) return emptyList()
        val context = host.context
        val now = LocalMoment.of(DateCommand.clock(), DateCommand.zone())
        val found = AlarmParser.parse(call.argument, now, host.language)
            ?: return listOf(ClockCommands.hint(context, context.getString(R.string.agent_command_alarm_hint),
                context.getString(R.string.agent_command_alarm_usage, call.prefix.toString())))
        val timeText = DateFormatting.time(found.time, DateFormatting.Language.of(host.language))
        val plain = if (found.title.isEmpty()) context.getString(R.string.agent_command_alarm_chip_untitled, timeText)
            else context.getString(R.string.agent_command_alarm_chip, timeText, found.title)
        val label = if (found.dayIgnored) context.getString(R.string.agent_command_alarm_chip_day_ignored, plain) else plain
        return listOf(KeyboardCommands.Chip(label, context.getString(R.string.agent_command_alarm_open, label)) {
            if (ClockCommands.start(host, call, launcher, intent(found.time, found.title)) && found.dayIgnored)
                Toast.makeText(context, context.getString(R.string.agent_command_alarm_day_note, timeText), Toast.LENGTH_LONG).show()
        })
    }
}

/** What `@wecker` and `@timer` share: the start of the clock app and the hint chip. */
internal object ClockCommands {
    /**
     * Starts [intent] and only then removes the command text: a failed start keeps it and says
     * "no clock app", and the field is not emptied before the app switch (P43). True if started.
     */
    fun start(host: KeyboardCommands.Host, call: CommandCall, launcher: SearchCommand.Launcher, intent: Intent): Boolean {
        if (!launcher.start(host.context, intent)) {
            Toast.makeText(host.context, R.string.agent_command_clock_none, Toast.LENGTH_SHORT).show()
            return false
        }
        host.replace(call, "")
        return true
    }

    /** A muted chip for an argument without a time or duration; its tap only shows how to type it. */
    fun hint(context: Context, label: String, usage: String) =
        KeyboardCommands.Chip(label, usage, KeyboardCommands.Chip.Kind.MUTED) {
            Toast.makeText(context, usage, Toast.LENGTH_LONG).show()
        }
}
