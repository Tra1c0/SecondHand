// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.EmojiNames
import helium314.keyboard.latin.R

/**
 * `@emoji Wort` (stage 24f): emojis by German or English keyword from a fixed table, on the device.
 * HeliBoard's emoji search needs a downloaded emoji dictionary, so it is not used here.
 */
object EmojiCommand : KeyboardCommands.Command {
    override val name = "emoji"
    override val protectsArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_emoji, KeyboardCommands.prefix(context).toString())

    override fun chips(host: KeyboardCommands.Host, call: CommandCall) =
        CharacterCommand.symbolChips(host, call, EmojiNames.table)

    /** The groups of the table, e.g. "tiere": `@emoji tiere` then shows all animals. */
    override fun templates(host: KeyboardCommands.Host) = CharacterCommand.groupTemplates(EmojiNames.table)
}
