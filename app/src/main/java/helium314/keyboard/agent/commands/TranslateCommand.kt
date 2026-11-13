// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.widget.Toast
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.StyledTranslation
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings

/**
 * `@translate "text" style` (stage 24f, two taps since Variante A): the chip names the provider and
 * the style and is the send button. Its tap is the one-time approval of exactly the request built
 * from the quoted text and the typed style ([StyledTranslation.request]) and starts one run, like
 * `@ki` ([AskCommand], [AiCommandChips]); the translation replaces the command only after its own tap.
 *
 * Without a closed, non-blank quote there is only a hint chip; its tap shows how to type the command.
 */
object TranslateCommand : KeyboardCommands.Command {
    override val name = "translate"
    override val protectsArgument = true
    override val longArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_translate, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { KeyboardCommands.dropAiRequests(); return emptyList() }
        val context = host.context
        val parsed = StyledTranslation.parse(call.argument)
            ?: return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_translate_hint),
                context.getString(R.string.agent_command_translate_usage, call.prefix.toString())) {
                Toast.makeText(context, context.getString(R.string.agent_command_translate_usage, call.prefix.toString()),
                    Toast.LENGTH_LONG).show()
            })
        val style = AiCommandChips.shorten(parsed.style, AiCommandChips.SHOWN)
        return AiCommandChips.chips(host, call, AiTextAction.TRANSLATE_STYLED, StyledTranslation.request(parsed),
            { provider -> context.getString(R.string.agent_command_translate_chip, provider, style) },
            { provider -> context.getString(R.string.agent_command_translate_open, provider, parsed.style) })
    }

    /**
     * A style and the opening quote: the user types the text and closes the quote, then the send
     * chip appears (until then the hint chip). The styles are free text for the model.
     */
    override fun templates(host: KeyboardCommands.Host) =
        listOf("spanisch", "englisch", "natürliches slang spanisch", "deutsch").map { KeyboardCommands.Template("$it \"…\"", "$it \"") }

    /** No command typed any more: a request sent from the strip is cancelled and its answer dropped. */
    override fun reset() = KeyboardCommands.dropAiRequests()
}
