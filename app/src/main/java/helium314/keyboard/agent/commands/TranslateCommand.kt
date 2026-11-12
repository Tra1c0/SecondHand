// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.widget.Toast
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.StyledTranslation
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings

/**
 * `@translate "text" style` (stage 24f): one chip that opens the text actions with a send preview of
 * exactly the request built from the quoted text and the typed style ([StyledTranslation.request])
 * and the provider and model it would go to ([KeyboardCommands.Host.translateAi]). Like `@ki`, the
 * tap changes nothing in the field and sends nothing; the request starts only after "Send" in the
 * panel, and the translation replaces the command only after its own tap on "Insert".
 *
 * Without a closed, non-blank quote there is only a hint chip; its tap shows how to type the command.
 */
object TranslateCommand : KeyboardCommands.Command {
    override val name = "translate"
    override val protectsArgument = true
    override val longArgument = true

    /** Characters of the style shown on the chip; the preview in the panel shows all of it. */
    private const val CHIP_STYLE = 40

    override fun description(context: Context) =
        context.getString(R.string.agent_command_translate, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        val context = host.context
        val parsed = StyledTranslation.parse(call.argument)
            ?: return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_translate_hint),
                context.getString(R.string.agent_command_translate_usage, call.prefix.toString())) {
                Toast.makeText(context, context.getString(R.string.agent_command_translate_usage, call.prefix.toString()),
                    Toast.LENGTH_LONG).show()
            })
        val style = if (parsed.style.length <= CHIP_STYLE) parsed.style
            else parsed.style.take(CHIP_STYLE - 1).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }.trimEnd() + "…"
        return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_translate_chip, style),
            context.getString(R.string.agent_command_translate_open, parsed.style)) {
            host.translateAi(call, StyledTranslation.request(parsed))
        })
    }
}
