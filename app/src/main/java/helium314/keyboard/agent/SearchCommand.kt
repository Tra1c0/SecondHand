// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.latin.R
import java.net.URLEncoder

/**
 * `@such Begriff` (stage 24f): one chip that opens a web search for the typed term. The default
 * search app gets [Intent.ACTION_WEB_SEARCH]; if none takes it, a browser gets a DuckDuckGo URL.
 * Nothing leaves the keyboard before the tap; the chip shows the exact term that is handed over.
 */
object SearchCommand : KeyboardCommands.Command {
    override val name = "such"
    override val protectsArgument = true

    const val FALLBACK_URL = "https://duckduckgo.com/?q="

    /** Starts an activity; false if no app takes the intent. */
    fun interface Launcher {
        fun start(context: Context, intent: Intent): Boolean
    }

    val SYSTEM_LAUNCHER = Launcher { context, intent ->
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Replaced in tests, so that they need no browser. */
    @JvmStatic var launcher: Launcher = SYSTEM_LAUNCHER

    override fun description(context: Context) =
        context.getString(R.string.agent_command_search, KeyboardCommands.prefix(context).toString())

    /** The intents to try in order; the keyboard is a service, so each starts a new task. */
    fun intents(query: String): List<Intent> = listOf(
        Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        Intent(Intent.ACTION_VIEW, Uri.parse(FALLBACK_URL + URLEncoder.encode(query, "UTF-8")))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        val query = call.argument.trim()
        if (query.isEmpty()) return emptyList()
        val label = host.context.getString(R.string.agent_command_search_chip, query)
        return listOf(KeyboardCommands.Chip(label) { search(host, call, query) })
    }

    private fun search(host: KeyboardCommands.Host, call: CommandCall, query: String) {
        val started = intents(query).any { launcher.start(host.context, it) }
        if (!started) {
            Toast.makeText(host.context, R.string.agent_command_search_none, Toast.LENGTH_SHORT).show()
            return
        }
        // Only after a successful start: a failed start keeps the typed command, and the field is
        // not emptied before the app switch (P43). Host.replace refuses if the text changed meanwhile.
        host.replace(call, "")
    }
}
