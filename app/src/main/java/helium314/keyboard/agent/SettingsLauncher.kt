// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import helium314.keyboard.settings.SettingsActivity

/** Opens the settings at one of [AgentPreferences.SETTINGS_DESTINATIONS], for the menu, the tool grid and the design tool. */
object SettingsLauncher {
    fun open(context: Context, destination: String) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                // clear an existing settings task: it would only come to the front and ignore the destination
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, destination)
        )
    }
}
