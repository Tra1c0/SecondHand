// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.DateFormatting
import dev.agentkeyboard.core.EventParser
import dev.agentkeyboard.core.LocalMoment
import helium314.keyboard.latin.R
import java.util.TimeZone

/**
 * `@datum` (stage 24f): today's date and the time in several formats of the keyboard language;
 * `@datum morgen`, `+3`, `freitag`, `24.12.` (anything [EventParser] reads without leftover words)
 * gives another day. Local; a tap replaces the command text by the chosen text.
 */
object DateCommand : KeyboardCommands.Command {
    override val name = "datum"
    // "morgen" or "freitag" must not be corrected to "Morgen"/"Freitag" while typing
    override val protectsArgument = true

    /** The current time in milliseconds; replaced in tests. */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }
    /** Replaced in tests. */
    @JvmStatic var zone: () -> TimeZone = { TimeZone.getDefault() }

    override fun description(context: Context) =
        context.getString(R.string.agent_command_date, KeyboardCommands.prefix(context).toString())

    /** The texts offered for [argument], empty if it is no date. */
    fun texts(argument: String, language: String): List<String> {
        val now = LocalMoment.of(clock(), zone())
        val lang = DateFormatting.Language.of(language)
        if (argument.isBlank()) return DateFormatting.variants(now.date, now.time, lang)
        val found = EventParser.parse(argument, now, language) ?: return emptyList()
        if (found.title.isNotEmpty()) return emptyList()
        // today without a typed time also offers the current time
        val time = found.time ?: now.time.takeIf { found.date == now.date }
        return DateFormatting.variants(found.date, time, lang)
    }

    /** Days in the keyboard language as [EventParser] reads them; German for other languages. */
    override fun templates(host: KeyboardCommands.Host) = when (DateFormatting.Language.of(host.language)) {
        DateFormatting.Language.EN -> listOf("today", "tomorrow", "+7", "friday")
        DateFormatting.Language.ES -> listOf("hoy", "mañana", "+7", "viernes")
        else -> listOf("heute", "morgen", "übermorgen", "+7", "freitag")
    }.map { KeyboardCommands.Template(it) }

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> =
        texts(call.argument, host.language).map { text ->
            KeyboardCommands.Chip(text, host.context.getString(R.string.agent_command_insert_text, text)) { host.replace(call, text) }
        }
}
