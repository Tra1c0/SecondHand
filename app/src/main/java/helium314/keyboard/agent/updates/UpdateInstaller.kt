// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/** Package name, version code and SHA-256 digests of the current signing certificates of an APK. */
data class ApkIdentity(val packageName: String, val versionCode: Long, val signers: Set<String>)

fun interface ApkInspector { fun inspect(file: File): ApkIdentity? }
fun interface ApkInstallSession { fun install(file: File) }

object ApkCheck {
    /**
     * Null if [archive] may replace [installed]: same package, exactly the same non-empty set of
     * signing certificates and a higher version code. Otherwise the reason for refusing.
     */
    fun refusal(archive: ApkIdentity?, installed: ApkIdentity): UpdateError? = when {
        archive == null -> UpdateError.WRONG_PACKAGE
        archive.packageName != installed.packageName -> UpdateError.WRONG_PACKAGE
        installed.signers.isEmpty() || archive.signers != installed.signers -> UpdateError.WRONG_SIGNATURE
        archive.versionCode <= installed.versionCode -> UpdateError.NOT_NEWER
        else -> null
    }
}

/** Reads identities with the platform's package manager. */
class AndroidApkInspector(private val context: Context) : ApkInspector {
    override fun inspect(file: File): ApkIdentity? = runCatching {
        identity(context.packageManager.getPackageArchiveInfo(file.path, signatureFlags()))
    }.getOrNull()

    fun installed(): ApkIdentity = identity(context.packageManager.getPackageInfo(context.packageName, signatureFlags()))
        ?: ApkIdentity(context.packageName, Long.MAX_VALUE, emptySet())

    @Suppress("DEPRECATION")
    private fun signatureFlags() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @SuppressLint("PackageManagerGetSignatures") // compared as a whole set, not as "any matching signature"
    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo?): ApkIdentity? {
        info ?: return null
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            info.signingInfo?.apkContentsSigners else info.signatures
        val signers = signatures.orEmpty().map { digest(it.toByteArray()) }.toSet()
        return ApkIdentity(info.packageName, PackageInfoCompat.getLongVersionCode(info), signers)
    }

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** Hands the verified file to the system installer, which asks the user before installing. */
class PackageInstallerSession(private val context: Context) : ApkInstallSession {
    override fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("update.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
                // Mutable: the installer adds the status and the confirmation intent.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        } catch (failure: Exception) {
            runCatching { installer.abandonSession(id) }
            throw UpdateFailure(UpdateError.INSTALL)
        }
    }
}

/** Receives the installer's result; shows the system confirmation when it asks for one. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updates.cleanup(context)
            else -> {
                Updates.cleanup(context)
                runCatching { UpdateSettings(context).status = UpdateStatus.Failed(UpdateError.INSTALL) }
            }
        }
    }
}
