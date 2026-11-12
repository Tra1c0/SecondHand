// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.Places
import helium314.keyboard.latin.R

/**
 * `@ort Adresse` (stage 24f): "Karte: …" replaces the command by a Google Maps search link (only
 * inserted, never sent), "In Karten öffnen" hands the address to a maps app with a geo: intent.
 * A saved place ([SavedPlaces]) stands for its address: `@ort zuhause`; `@ort` alone offers all
 * saved places. Nothing leaves the keyboard before a tap. The current location is not offered.
 */
object PlaceCommand : KeyboardCommands.Command {
    override val name = "ort"
    override val protectsArgument = true

    /** Replaced in tests, so that they need no maps app. */
    @JvmStatic var launcher: SearchCommand.Launcher = SearchCommand.SYSTEM_LAUNCHER

    override fun description(context: Context) =
        context.getString(R.string.agent_command_place, KeyboardCommands.prefix(context).toString())

    fun geoIntent(query: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(Places.geoUri(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        val argument = call.argument.trim()
        val context = host.context
        if (argument.isEmpty()) {
            // one link chip per saved place
            return SavedPlaces.load(context).map { linkChip(host, call, "${it.name} – ${it.address}", it.address) }
        }
        val saved = Places.find(SavedPlaces.load(context), argument)
        val query = saved?.address ?: argument
        val shown = if (saved != null) "${saved.name} – ${saved.address}" else argument
        return listOf(
            linkChip(host, call, shown, query),
            KeyboardCommands.Chip(context.getString(R.string.agent_command_place_open),
                context.getString(R.string.agent_command_place_open_description, shown)) {
                if (launcher.start(context, geoIntent(query))) {
                    // only after a successful start, like SearchCommand (P43)
                    host.replace(call, "")
                } else Toast.makeText(context, R.string.agent_command_place_none, Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun linkChip(host: KeyboardCommands.Host, call: CommandCall, shown: String, query: String) =
        KeyboardCommands.Chip(host.context.getString(R.string.agent_command_place_chip, shown),
            host.context.getString(R.string.agent_command_place_link, shown)) { host.replace(call, Places.mapsLink(query)) }
}
