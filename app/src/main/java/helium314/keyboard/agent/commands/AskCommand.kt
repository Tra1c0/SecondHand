// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings

/**
 * `@ki Frage` (stage 24f): one chip that opens the text actions with a send preview of exactly the
 * typed question and the provider and model it would go to ([KeyboardCommands.Host.askAi]). The
 * tap changes nothing in the field and sends nothing: the request starts only after "Send" in the
 * panel, through the same approved run, journal and cancellation as the other AI actions. The
 * answer replaces the command only after its own tap on "Insert".
 */
object AskCommand : KeyboardCommands.Command {
    override val name = "ki"
    override val protectsArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_ki, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        val question = call.argument.trim()
        if (question.isEmpty()) return emptyList()
        val context = host.context
        return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_ki_chip, question),
            context.getString(R.string.agent_command_ki_open, question)) {
            host.askAi(call, question)
        })
    }
}
