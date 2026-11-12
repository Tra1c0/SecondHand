// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings

/**
 * `@ki Frage` (stage 24f, two taps since Variante A): the chip names the provider and is the send
 * button. Its tap is the one-time approval of exactly the typed question and starts one run through
 * the same approved runner, journal and cancellation as the other AI actions
 * ([KeyboardCommands.Host.sendAi]); nothing is sent before it. The answer then shows as a chip in
 * the strip and replaces the command only after its own tap ([AiCommandChips]).
 */
object AskCommand : KeyboardCommands.Command {
    override val name = "ki"
    override val protectsArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_ki, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { KeyboardCommands.dropAiRequests(); return emptyList() }
        val question = call.argument.trim()
        if (question.isEmpty()) return emptyList()
        val context = host.context
        return AiCommandChips.chips(host, call, AiTextAction.ASK, question,
            { provider -> context.getString(R.string.agent_command_ki_chip, provider, AiCommandChips.shorten(question, AiCommandChips.SHOWN)) },
            { provider -> context.getString(R.string.agent_command_ki_open, provider, question) })
    }

    /** No command typed any more: a request sent from the strip is cancelled and its answer dropped. */
    override fun reset() = KeyboardCommands.dropAiRequests()
}
