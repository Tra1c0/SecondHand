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
 * The update check: one GET to the public releases API of [UpdateReleases.REPOSITORY] (no token,
 * nothing typed), and on the user's own click the download of one build. Blocking functions, call
 * them on IO. Callers: the Updates page, the opt-in daily job and (stage 56) [UpdateOnOpen] on its
 * own background thread when the keyboard opens; never on the typing path.
 */
object Updates {
    fun installedVersion(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /**
     * Checks once, stores and returns the result. Never throws. Stage 56: sends the ETag of the
     * stored result as `If-None-Match` while it was compared with [installed]; a 304 keeps that
     * result (known build, notes, ETag) and only writes the status and [UpdateSettings.checkedAt].
     */
    fun check(context: Context, client: () -> GitHubUpdateClient, installed: Long = installedVersion(context)): UpdateStatus {
        // credential-protected: before the first unlock there is no stored state and no ETag to send
        val stored = runCatching { UpdateSettings(context) }.getOrNull()
        val sent = runCatching { stored?.let { s -> s.etag?.takeIf { s.comparedVersion == installed } } }.getOrNull()
        var newer: AvailableBuild? = null
        var fresh: GitHubUpdateClient.ReleaseList.Fresh? = null
        val status = try {
            when (val list = client().releaseList(sent)) {
                // unchanged since the stored result, which was compared with this same installed version
                GitHubUpdateClient.ReleaseList.Unchanged ->
                    stored!!.newerBuild(installed)?.let { UpdateStatus.Available(it) } ?: UpdateStatus.Current(installed)
                is GitHubUpdateClient.ReleaseList.Fresh -> {
                    fresh = list
                    newer = UpdateReleases.newest(list.releases)?.takeIf { UpdateReleases.isNewer(it, installed) }
                    newer?.let { UpdateStatus.Available(it.number) } ?: UpdateStatus.Current(installed)
                }
            }
        } catch (failure: UpdateFailure) { UpdateStatus.Failed(failure.error) }
        catch (_: Exception) { UpdateStatus.Failed(UpdateError.SERVICE) }
        runCatching {
            (stored ?: UpdateSettings(context)).let {
                it.status = status
                it.checkedAt = System.currentTimeMillis()
                // stage 54: build, release text and compared version for the dialog and the keyboard chip;
                // a failed check knows nothing new and keeps what the last successful one found (and its ETag)
                fresh?.let { list -> if (status !is UpdateStatus.Failed) it.remember(newer, installed, list.etag) }
            }
        }
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
