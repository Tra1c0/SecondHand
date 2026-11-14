// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.agentkeyboard.core.CommandGrammar.AppName
import java.util.concurrent.Executors

/**
 * Stage 52: the apps a command can name (`@lang whatsapp german`): package name and label of every
 * app with a launcher entry, like the app chooser of the snippet editor. Read on a background thread
 * when the keyboard loads the commands, at most every [MAX_AGE_MILLIS]; only in memory, never logged.
 * Until the first read is done a typed app name is unknown.
 */
object InstalledApps {
    private const val TAG = "InstalledApps"
    /** A list older than this is read again at the next [refresh]. */
    const val MAX_AGE_MILLIS = 10 * 60 * 1000L

    /** Where the apps come from; replaced in tests (then [refresh] reads at once on the calling thread). */
    fun interface Source { fun read(context: Context): List<AppName> }

    val SYSTEM: Source = Source { context ->
        val manager = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        manager.queryIntentActivities(launcher, 0).distinctBy { it.activityInfo.packageName }
            .map { AppName(it.activityInfo.packageName, it.activityInfo.loadLabel(manager).toString()) }
            .filter { it.packageName != context.packageName }
    }

    @JvmStatic var source: Source = SYSTEM
        set(value) { field = value; apps = emptyList(); readAt = 0L }

    @Volatile var apps: List<AppName> = emptyList()
        private set
    @Volatile private var readAt = 0L
    @Volatile private var reading = false
    private val worker by lazy { Executors.newSingleThreadExecutor { Thread(it, "agent-apps").apply { isDaemon = true } } }

    /** Reads the apps again when the list is missing or old; the system source off the main thread. */
    fun refresh(context: Context) {
        val now = System.currentTimeMillis()
        if (reading || (readAt != 0L && now - readAt < MAX_AGE_MILLIS)) return
        val app = context.applicationContext ?: context
        val current = source
        if (current !== SYSTEM) { read(app, current, now); return }
        reading = true
        worker.execute { try { read(app, current, now) } finally { reading = false } }
    }

    private fun read(context: Context, from: Source, now: Long) {
        apps = try { from.read(context) } catch (e: Exception) {
            Log.w(TAG, "apps could not be read: ${e.javaClass.simpleName}")
            emptyList()
        }
        readAt = now
    }

    /** The label of [packageName], or the package name when the app is unknown. */
    fun label(packageName: String): String = apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
}
