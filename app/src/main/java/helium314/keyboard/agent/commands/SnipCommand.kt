// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import java.util.Locale

/**
 * `@snip name` (stage 24f): the user's own text snippets, kept in [SnippetStore] and edited on the
 * command settings page. `@snip` lists them, `@snip ad` those whose name starts with or contains
 * "ad"; a tap on a snippet replaces the command by its text. Local only; nothing is logged.
 */
object SnipCommand : KeyboardCommands.Command {
    private const val TAG = "SnipCommand"
    const val MAX_CHIPS = 10

    /** Where snippets come from; replaced in tests. */
    fun interface Source { fun read(context: Context): List<Snippet> }

    @JvmStatic var source: Source = Source { SnippetStore(it).read() }

    /** Read once per typed command; dropped when the command is gone. */
    private var cached: List<Snippet>? = null

    override val name = "snip"
    override val protectsArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_snip, KeyboardCommands.prefix(context).toString())

    override fun reset() { cached = null }

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        // find() already refuses these fields; checked again so that nothing is read there
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { reset(); return emptyList() }
        val context = host.context
        // unreadable (e.g. locked) storage shows nothing, not an invitation to add snippets
        val snippets = cached ?: (read(context) ?: return emptyList()).also { cached = it }
        if (snippets.isEmpty()) return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_snip_add),
            context.getString(R.string.agent_command_snip_add_description)) { openSettings(context, SettingsDestination.AgentCommands) })
        return match(call.argument, snippets).map { snippet ->
            KeyboardCommands.Chip("${snippet.name}: ${preview(snippet.text)}",
                context.getString(R.string.agent_command_snip_insert, snippet.name)) {
                if (host.replace(call, snippet.text)) reset()
            }
        }
    }

    private fun read(context: Context): List<Snippet>? = try {
        source.read(context)
    } catch (e: Exception) {
        // texts are never logged
        Log.w(TAG, "snippets could not be read: ${e.javaClass.simpleName}")
        null
    }

    /**
     * Snippets for the typed [query], best first: same name, name starts with it, name contains it.
     * Without a query all snippets by name. Case does not matter. At most [MAX_CHIPS].
     */
    fun match(query: String, snippets: List<Snippet>): List<Snippet> {
        val typed = query.trim().lowercase(Locale.ROOT)
        return snippets.mapNotNull { snippet ->
            val name = snippet.name.lowercase(Locale.ROOT)
            val score = when {
                typed.isEmpty() -> 1
                name == typed -> 0
                name.startsWith(typed) -> 1
                name.contains(typed) -> 2
                else -> return@mapNotNull null
            }
            Triple(snippet, score, name)
        }.sortedWith(compareBy({ it.second }, { it.third })).take(MAX_CHIPS).map { it.first }
    }

    /** The start of [text] on one line, for a chip label. */
    fun preview(text: String, length: Int = 30): String {
        val line = text.trim().replace(Regex("\\s+"), " ")
        return if (line.length <= length) line else line.take(length).trimEnd() + "…"
    }

    /** The keyboard cannot show settings itself: a settings page opens in its own task (P39). */
    internal fun openSettings(context: Context, destination: String) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                // an existing settings task would only come to the front and ignore the destination (P39)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, destination)
        )
    }
}
