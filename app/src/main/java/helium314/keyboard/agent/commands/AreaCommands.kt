// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R

/**
 * Stage 52: the areas of the unified command system that have no older command: `@guard` (format
 * guard per app, stage 41), `@mem` (the memory's excluded apps, stage 46) and `@group` (own groups of
 * apps). Their words, chips, Enter and undo are [CommandSystem]'s.
 */
abstract class AreaCommand(final override val name: String, private val description: Int) : KeyboardCommands.Command {
    override val protectsArgument = true
    /** Review of stage 52: only words that read as a command; other text after the area word is corrected as usual. */
    override fun protects(context: Context, call: CommandCall) = CommandSystem.protects(context, call)
    override val aliases: List<String> get() = CommandSystem.ALIASES[name].orEmpty()
    override fun description(context: Context) = context.getString(description, KeyboardCommands.prefix(context).toString())
    override fun chips(host: KeyboardCommands.Host, call: CommandCall) = CommandSystem.chips(host, call).orEmpty()
    override fun onEnter(host: KeyboardCommands.Host, call: CommandCall) = CommandSystem.onEnter(host, call) ?: false
    override fun load(context: Context) = InstalledApps.refresh(context)
}

/** `@guard banking off` (`@wächter`): the format guard's hints per app. */
object GuardCommand : AreaCommand("guard", R.string.agent_command_guard)

/** `@mem whatsapp off` (`@gedächtnis`): the memory stays out of an app. */
object MemoryCommand : AreaCommand("mem", R.string.agent_command_mem)

/** `@group messenger whatsapp signal` (`@gruppe`): own groups, targets of the other areas. */
object GroupCommand : AreaCommand("group", R.string.agent_command_group)
