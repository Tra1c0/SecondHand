// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.SpecialCharacters
import dev.agentkeyboard.core.SymbolTable
import helium314.keyboard.latin.R

/** `@zeichen Wort` (stage 24f): special characters found by German or English name or by group (`pfeile`), on the device. */
object CharacterCommand : KeyboardCommands.Command {
    override val name = "zeichen"
    override val protectsArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_chars, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall) = symbolChips(host, call, SpecialCharacters.table)

    /** The groups of the table, e.g. "pfeile": `@zeichen pfeile` then shows all arrows. */
    override fun templates(host: KeyboardCommands.Host) = groupTemplates(SpecialCharacters.table)

    internal fun groupTemplates(table: SymbolTable) = table.groups.map { KeyboardCommands.Template(it.name) }

    /**
     * One chip per symbol of the group named by the argument (all of them) and per symbol whose
     * name matches it; a tap replaces the command by the symbol.
     */
    internal fun symbolChips(host: KeyboardCommands.Host, call: CommandCall, table: SymbolTable): List<KeyboardCommands.Chip> =
        table.search(call.argument).map { entry ->
            val description = host.context.getString(R.string.agent_command_symbol_insert, entry.symbol, entry.label)
            KeyboardCommands.Chip(entry.symbol, description) { host.replace(call, entry.symbol) }
        }
}
