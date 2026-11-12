// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** The daily job of the opt-in update check: network required, about every 24 hours. */
object UpdateScheduler {
    /** No other job of this app uses JobScheduler; the value only has to stay unique within the app. */
    const val JOB_ID = 0x41475550
    val PERIOD_MILLIS = TimeUnit.HOURS.toMillis(24)

    private fun scheduler(context: Context) =
        context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    fun schedule(context: Context) {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIOD_MILLIS)
            .setPersisted(true) // survives a reboot (RECEIVE_BOOT_COMPLETED is already declared)
            .build()
        scheduler(context).schedule(job)
    }

    fun cancel(context: Context) = scheduler(context).cancel(JOB_ID)

    fun pending(context: Context): JobInfo? = scheduler(context).allPendingJobs.firstOrNull { it.id == JOB_ID }
}

class UpdateCheckJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try { Updates.runScheduled(applicationContext) }
            catch (_: Exception) { } // a failed run waits for the next period, nothing is retried early
            finally { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }
}

/** The single notification of the update check, in its own channel "Updates". */
object UpdateNotifier {
    const val CHANNEL = "agent_updates"
    private const val NOTIFICATION_ID = 0x41475550

    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens the Updates page; the download only starts there, after the user's own click. */
    fun pageIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, AgentPreferences.UPDATES_DESTINATION)

    @SuppressLint("MissingPermission") // allowed() checks POST_NOTIFICATIONS first
    fun show(context: Context, build: Long) {
        if (!allowed(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                context.getString(R.string.agent_updates_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, pageIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_agent_settings_updates)
            .setContentTitle(context.getString(R.string.agent_updates_notification, build))
            .setContentText(context.getString(R.string.agent_updates_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { } // permission withdrawn in the meantime
    }
}
