// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.content.Context
import dev.agentkeyboard.core.AvailableBuild
import dev.agentkeyboard.core.ReleaseNotes
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

    /**
     * Stage 54: the newest build above the installed one that the last successful check found (0 =
     * none). Only [Updates.check] writes it; a failed check or a failed install keeps it, so the
     * keyboard chip and the settings dialog stay while a newer build is known.
     */
    val knownBuild: Long get() = storage.getLong("known_build", 0)

    /** The release text of [knownBuild], [ReleaseNotes.cap]ped (plain Markdown, at most 8 KiB). */
    val knownNotes: String get() = storage.getString("known_notes", null).orEmpty()
    val knownNotesTruncated: Boolean get() = storage.getBoolean("known_notes_truncated", false)

    /** The installed version code the last successful check compared with (0 = never checked). */
    val comparedVersion: Long get() = storage.getLong("compared_version", 0)

    /** Stores what a successful check found: [build] above [installed], or null for none. One commit. */
    fun remember(build: AvailableBuild?, installed: Long) {
        val notes = ReleaseNotes.cap(build?.notes.orEmpty())
        val editor = storage.edit().putLong("compared_version", installed)
        if (build == null) editor.remove("known_build").remove("known_notes").remove("known_notes_truncated")
        else editor.putLong("known_build", build.number).putString("known_notes", notes.text)
            .putBoolean("known_notes_truncated", notes.truncated)
        check(editor.commit())
    }

    /** The known build if it is newer than [installed], else null (gone once that build or a later one is installed). */
    fun newerBuild(installed: Long): Long? = knownBuild.takeIf { it > installed }

    /** "Später" in the dialog "Build N ist da": no dialog for builds up to this one (the keyboard chip stays). */
    var laterBuild: Long
        get() = storage.getLong("later_build", 0)
        set(value) { check(storage.edit().putLong("later_build", value).commit()) }

    /** The version code whose "Neu in Build N" dialog was shown (or that was installed fresh), 0 = none yet. */
    var seenVersion: Long
        get() = storage.getLong("seen_version", 0)
        set(value) { check(storage.edit().putLong("seen_version", value).commit()) }

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
