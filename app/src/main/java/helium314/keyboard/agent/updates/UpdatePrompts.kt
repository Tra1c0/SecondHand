// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.content.Context
import android.view.View
import android.view.ViewGroup
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import java.io.ByteArrayOutputStream

/**
 * Stage 54: which update dialog the settings show on opening. Reads only what the update check
 * stored and the notes embedded in the APK; nothing here asks the network.
 */
object UpdatePrompts {
    /** Generated at build time from `release-notes/next.md` (`app/build.gradle.kts`, task `embed*ReleaseNotes`). */
    const val NOTES_ASSET = "agent/release_notes.md"
    /** The file is ours, read bounded anyway. */
    private const val MAX_EMBEDDED_BYTES = 256 * 1024

    sealed class Prompt {
        /** Once after the installed version changed: "Neu in Build N" with the notes from the APK. */
        data class WhatsNew(val version: Long, val notes: String) : Prompt()
        /** The last check found a newer build: "Build N ist da" with its release text. */
        data class Available(val build: Long, val notes: String, val truncated: Boolean) : Prompt()
    }

    /** One-shot: "Installieren" in the dialog lets the Updates page start the download of this build once. */
    @Volatile private var installRequest = 0L

    fun requestInstall(build: Long) { installRequest = build }

    /** True once for the build [requestInstall] named; any call clears the request. */
    fun takeInstallRequest(build: Long): Boolean = (installRequest == build).also { installRequest = 0L }

    /**
     * The dialog to show now, or null. "Neu in" comes first; a fresh install (never updated) only
     * remembers its version without a dialog. Nothing while the device is locked (credential storage).
     * [available] false (the settings opened on the Updates page itself): only "Neu in".
     */
    fun next(context: Context, installed: Long, available: Boolean = true,
             freshInstall: () -> Boolean = { isFreshInstall(context) }): Prompt? {
        if (!credentialAccessAllowed(context)) return null
        return runCatching {
            val settings = UpdateSettings(context)
            if (installed > settings.seenVersion) {
                if (settings.seenVersion == 0L && freshInstall()) settings.seenVersion = installed
                else return@runCatching Prompt.WhatsNew(installed, embeddedNotes(context))
            }
            if (!available) return@runCatching null
            val build = settings.newerBuild(installed) ?: return@runCatching null
            if (build <= settings.laterBuild) null
            else Prompt.Available(build, settings.knownNotes, settings.knownNotesTruncated)
        }.getOrNull()
    }

    /** Called when "Neu in Build N" is shown: it never comes again for this version. */
    fun markSeen(context: Context, version: Long) { runCatching { UpdateSettings(context).seenVersion = version } }

    /** "Später": no dialog for this build any more; the keyboard chip stays until it is installed. */
    fun later(context: Context, build: Long) { runCatching { UpdateSettings(context).laterBuild = build } }

    /** The notes of this APK, "" if missing. */
    fun embeddedNotes(context: Context): String = runCatching {
        context.assets.open(NOTES_ASSET).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (output.size() < MAX_EMBEDDED_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_EMBEDDED_BYTES - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toString("UTF-8")
        }
    }.getOrDefault("")

    /** Installed and never updated since (both times equal): no "Neu in" dialog for the first version. */
    private fun isFreshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true)
}

/**
 * Stage 54: the chip "⬆ Build N verfügbar" on the neutral strip of the keyboard (owner: always
 * while a newer build is known). It names only the build number; a tap opens the Updates page,
 * which downloads only after its own click. Never while the device is locked.
 */
object UpdateChip {
    /** Tag of the chip row in the strip. */
    const val TAG = "agent_update_chip"

    /** Replaced in tests: whether the device (or the user) is locked right now; no settings can open there. */
    @JvmStatic var locked: (Context) -> Boolean = { !credentialAccessAllowed(it) }

    @Volatile private var installedCache = 0L

    /** Replaced in tests: the installed version code, read once per process (an update restarts the process). */
    @JvmStatic var installed: (Context) -> Long = { context ->
        installedCache.takeIf { it > 0 } ?: runCatching { Updates.installedVersion(context) }.getOrNull()
            ?.also { installedCache = it } ?: Long.MAX_VALUE
    }

    /**
     * The build the chip names, or null: none known above the installed version, keyboard settings
     * locked, or the device locked right now (asked live only when there is a build to name).
     */
    fun build(context: Context, settings: SettingsValues?): Long? {
        if (settings == null || settings.mIsLocked) return null
        // credential-protected storage: before the first unlock it throws, then there is no chip
        val known = runCatching { UpdateSettings(context).knownBuild }.getOrDefault(0L)
        if (known <= 0 || known <= installed(context)) return null
        return known.takeUnless { locked(context) }
    }

    /** The chip row for the neutral strip (from `LatinIME.setNeutralSuggestionStrip`), or null. */
    @JvmStatic fun createView(context: Context, parent: ViewGroup): View? {
        val build = build(context, Settings.getValues()) ?: return null
        val chip = KeyboardCommands.Chip(context.getString(R.string.agent_update_chip, build),
            context.getString(R.string.agent_update_chip_description, build)) { open(context) }
        val row = KeyboardCommands.chipRow(parent, listOf(chip)) as ViewGroup
        // not a command row: code that looks for typed command chips must not find this one
        row.getChildAt(0).id = View.NO_ID
        row.tag = TAG
        return row
    }

    /** Opens the Updates page (the same intent as the notification), never while locked. */
    fun open(context: Context) {
        if (locked(context)) return
        runCatching { context.startActivity(UpdateNotifier.pageIntent(context)) }
    }
}
