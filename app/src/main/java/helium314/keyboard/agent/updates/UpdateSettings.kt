// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.content.Context
import helium314.keyboard.agent.security.credentialContext

/** Result of the last check, shown on the Updates page. Never contains a response body. */
sealed class UpdateStatus {
    data object Unknown : UpdateStatus()
    data class Current(val installed: Long) : UpdateStatus()
    data class Available(val build: Long) : UpdateStatus()
    data class Failed(val error: UpdateError) : UpdateStatus()
}

/**
 * Non-secret update settings in credential-protected preferences (`agent_updates`), never part
 * of the keyboard's exportable settings. The check needs no credential (public release repository).
 */
class UpdateSettings(context: Context) {
    private val storage = credentialContext(context).getSharedPreferences("agent_updates", Context.MODE_PRIVATE)

    /** Opt-in daily background check, default off. */
    var daily: Boolean
        get() = storage.getBoolean("daily", false)
        set(value) { check(storage.edit().putBoolean("daily", value).commit()) }

    /** Highest build a notification was already shown for, so each build notifies once. */
    var notifiedBuild: Long
        get() = storage.getLong("notified_build", 0)
        set(value) { check(storage.edit().putLong("notified_build", value).commit()) }

    /** When the last check finished (epoch milliseconds, also for a failed one), 0 = never. Shown on "System & Daten". */
    var checkedAt: Long
        get() = storage.getLong("checked_at", 0)
        set(value) { check(storage.edit().putLong("checked_at", value).commit()) }

    var status: UpdateStatus
        get() {
            val number = storage.getLong("status_number", 0)
            return when (storage.getString("status", null)) {
                "current" -> UpdateStatus.Current(number)
                "available" -> UpdateStatus.Available(number)
                "failed" -> UpdateError.entries.firstOrNull { it.name == storage.getString("status_error", null) }
                    ?.let { UpdateStatus.Failed(it) } ?: UpdateStatus.Unknown
                else -> UpdateStatus.Unknown
            }
        }
        set(value) {
            val editor = storage.edit()
            when (value) {
                UpdateStatus.Unknown -> editor.remove("status").remove("status_number").remove("status_error")
                is UpdateStatus.Current -> editor.putString("status", "current").putLong("status_number", value.installed)
                is UpdateStatus.Available -> editor.putString("status", "available").putLong("status_number", value.build)
                is UpdateStatus.Failed -> editor.putString("status", "failed").putString("status_error", value.error.name)
            }
            check(editor.commit())
        }
}
