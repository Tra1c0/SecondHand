// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.MathExpression
import dev.agentkeyboard.core.UnitConversion
import helium314.keyboard.latin.R

/**
 * `@conv 5 oz` (stage 24f): unit conversions on the device ([UnitConversion]), one chip per target
 * like "5 oz = 141,7 g"; a tap replaces the command by that text. The argument is protected so that
 * unit names like "oz" are not autocorrected.
 */
object ConvertCommand : KeyboardCommands.Command {
    override val name = "conv"
    override val protectsArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_conv, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        val locale = MathExpression.Locale.forLanguage(host.language)
        return UnitConversion.conversions(call.argument).map { conversion ->
            val text = UnitConversion.format(conversion, locale)
            KeyboardCommands.Chip(text, host.context.getString(R.string.agent_command_insert_text, text)) { host.replace(call, text) }
        }
    }
}
