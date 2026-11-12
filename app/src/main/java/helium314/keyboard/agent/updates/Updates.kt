// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import dev.agentkeyboard.core.AvailableBuild
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import java.io.File

/**
 * The opt-in update check: one GET to the public releases API of [UpdateReleases.REPOSITORY]
 * (no token), and on the user's own click the download of one build. Blocking functions, call
 * them on IO. Nothing here runs while typing; the keyboard service never calls it.
 */
object Updates {
    fun installedVersion(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /** Checks once, stores and returns the result. Never throws. */
    fun check(context: Context, client: () -> GitHubUpdateClient, installed: Long = installedVersion(context)): UpdateStatus {
        val status = try {
            val newest = client().newest()
            if (newest != null && UpdateReleases.isNewer(newest, installed)) UpdateStatus.Available(newest.number)
            else UpdateStatus.Current(installed)
        } catch (failure: UpdateFailure) { UpdateStatus.Failed(failure.error) }
        catch (_: Exception) { UpdateStatus.Failed(UpdateError.SERVICE) }
        runCatching { UpdateSettings(context).status = status }
        return status
    }

    /**
     * Asks the API again for the newest build, downloads it, verifies its SHA-256 against the
     * published checksum and its package and certificate against the installed app, and only then
     * hands it to [session] (the system asks the user). The file is deleted in every case.
     */
    fun downloadAndInstall(context: Context, client: GitHubUpdateClient, inspector: ApkInspector,
                           installed: ApkIdentity, session: ApkInstallSession, checkCancelled: () -> Unit = {},
                           onProgress: (Long, Long) -> Unit = { _, _ -> }): AvailableBuild {
        val target = downloadFile(context)
        try {
            val build = client.newest()?.takeIf { UpdateReleases.isNewer(it, installed.versionCode) }
                ?: throw UpdateFailure(UpdateError.NOT_NEWER)
            val expected = client.expectedChecksum(build)
            checkCancelled()
            val actual = client.downloadApk(build, target, checkCancelled, onProgress)
            if (actual != expected) throw UpdateFailure(UpdateError.CHECKSUM)
            checkCancelled()
            ApkCheck.refusal(inspector.inspect(target), installed)?.let { throw UpdateFailure(it) }
            session.install(target)
            return build
        } finally {
            target.delete()
        }
    }

    /** Removes any downloaded APK, e.g. one left over by a crash. Does nothing while locked. */
    fun cleanup(context: Context) {
        if (!credentialAccessAllowed(context)) return
        runCatching { downloadDirectory(context).listFiles()?.forEach { it.delete() } }
    }

    internal fun downloadDirectory(context: Context) = File(credentialContext(context).cacheDir, "agent_update")

    private fun downloadFile(context: Context): File {
        val directory = downloadDirectory(context)
        if (!directory.isDirectory && !directory.mkdirs()) throw UpdateFailure(UpdateError.STORAGE)
        directory.listFiles()?.forEach { it.delete() }
        return File(directory, "update.apk")
    }

    /** Scheduled run: checks only while the switch is on, and notifies once per build. */
    fun runScheduled(context: Context, client: () -> GitHubUpdateClient = { GitHubUpdateClient(UpdateHttp.calls(context)) },
                     notify: (Long) -> Unit = { UpdateNotifier.show(context, it) }) {
        // Never touch credential-protected storage before the user unlocked the device.
        if (!credentialAccessAllowed(context)) return
        cleanup(context)
        val settings = UpdateSettings(context)
        if (!settings.daily) { UpdateScheduler.cancel(context); return }
        val status = check(context, client)
        if (status is UpdateStatus.Available && status.build > settings.notifiedBuild) {
            notify(status.build)
            settings.notifiedBuild = status.build
        }
    }

    /** Turns the daily check on or off; off always cancels the job. */
    fun setDaily(context: Context, enabled: Boolean) {
        UpdateSettings(context).daily = enabled
        if (enabled) UpdateScheduler.schedule(context) else UpdateScheduler.cancel(context)
    }
}
