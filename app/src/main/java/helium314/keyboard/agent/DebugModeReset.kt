// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.DebugSettings

/**
 * Stage 62: the shipped APK is no longer debuggable, so debug mode is off by default. A phone that
 * ran the earlier builds may still have a stored "true" (debug mode defaulted to on there, and the
 * switch could have been written). This puts such a phone back to off exactly once; afterwards the
 * owner can switch debug mode on again in the settings and it stays on.
 */
object DebugModeReset {
    const val DONE_KEY = "agent_debug_mode_reset_done"

    /** Runs once per installation (also marks a fresh install, which has nothing to reset). */
    fun runOnce(prefs: SharedPreferences) {
        if (prefs.getBoolean(DONE_KEY, false)) return
        val wasOn = prefs.getBoolean(DebugSettings.PREF_DEBUG_MODE, false)
        prefs.edit {
            if (wasOn) putBoolean(DebugSettings.PREF_DEBUG_MODE, false)
            putBoolean(DONE_KEY, true)
        }
        // App.onCreate reads the flag before the upgrade check runs
        if (wasOn) DebugFlags.DEBUG_ENABLED = false
    }
}
