// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.updates.AndroidApkInspector
import helium314.keyboard.agent.updates.GitHubUpdateClient
import helium314.keyboard.agent.updates.PackageInstallerSession
import helium314.keyboard.agent.updates.UpdateError
import helium314.keyboard.agent.updates.UpdateFailure
import helium314.keyboard.agent.updates.UpdateHttp
import helium314.keyboard.agent.updates.UpdateSettings
import helium314.keyboard.agent.updates.UpdateStatus
import helium314.keyboard.agent.updates.Updates
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The opt-in update check (no account, no token): daily switch, "check now", last result and,
 * when a newer build exists, download and install after the user's own click.
 */
@Composable
fun AgentUpdatesScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var accessible by remember { mutableStateOf(credentialAccessAllowed(context)) }
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { accessible = credentialAccessAllowed(context); resumed++ }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!accessible) {
        // The settings live in credential-protected storage, unreadable before the first unlock.
        SearchSettingsScreen(onClickBack, stringResource(R.string.agent_updates_title), emptyList()) {
            Text(stringResource(R.string.agent_updates_unlock), Modifier.padding(16.dp))
        }
        return
    }
    AgentUpdatesUnlockedScreen(onClickBack, resumed)
}

@Composable
private fun AgentUpdatesUnlockedScreen(onClickBack: () -> Unit, resumed: Int) {
    val context = LocalContext.current
    val settings = remember { UpdateSettings(context) }
    val scope = rememberCoroutineScope()
    val installed = remember { runCatching { Updates.installedVersion(context) }.getOrDefault(0L) }
    var daily by remember { mutableStateOf(settings.daily) }
    var status by remember { mutableStateOf(settings.status) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var message by remember { mutableIntStateOf(0) }
    var notificationsRefused by remember { mutableStateOf(false) }
    var work by remember { mutableStateOf<Job?>(null) }
    fun canInstall() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    var installAllowed by remember { mutableStateOf(canInstall()) }
    LaunchedEffect(resumed) {
        // The installer's result and the "install unknown apps" setting can change in the background.
        installAllowed = canInstall()
        if (!busy) status = runCatching { settings.status }.getOrDefault(status)
        withContext(Dispatchers.IO) { Updates.cleanup(context) }
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsRefused = !it
    }

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_updates_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_updates_description))
            Text(stringResource(R.string.agent_updates_repository, UpdateReleases.REPOSITORY),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.agent_updates_installed, installed), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.agent_updates_daily), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.agent_updates_daily_summary), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = daily, onCheckedChange = { enabled ->
                    runCatching { Updates.setDaily(context, enabled) }.onSuccess { daily = enabled }
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            if (daily && notificationsRefused) Text(stringResource(R.string.agent_updates_notifications_refused))
            OutlinedButton(enabled = !busy, onClick = {
                busy = true; message = 0
                work = scope.launch {
                    try {
                        status = withContext(Dispatchers.IO) {
                            Updates.check(context, { GitHubUpdateClient(UpdateHttp.calls(context)) }, installed)
                        }
                    } finally { busy = false }
                }
            }) { Text(stringResource(if (busy && progress == null && !verifying) R.string.agent_updates_checking else R.string.agent_updates_check_now)) }
            Text(statusText(status), style = MaterialTheme.typography.titleMedium)
            val available = status as? UpdateStatus.Available
            if (available != null && available.build > installed) {
                if (!installAllowed) {
                    Text(stringResource(R.string.agent_updates_install_permission))
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                "package:${context.packageName}".toUri()))
                        }
                    }) { Text(stringResource(R.string.agent_updates_install_permission_open)) }
                }
                Button(enabled = !busy && installAllowed, modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true; message = 0; progress = 0L to 0L
                    work = scope.launch {
                        val job = coroutineContext
                        try {
                            withContext(Dispatchers.IO) {
                                val inspector = AndroidApkInspector(context)
                                Updates.downloadAndInstall(context, GitHubUpdateClient(UpdateHttp.calls(context)), inspector,
                                    inspector.installed(), PackageInstallerSession(context), { job.ensureActive() }) { done, total ->
                                    // snapshot state may be written from this IO thread
                                    progress = done to total; verifying = total > 0 && done >= total
                                }
                            }
                            message = R.string.agent_updates_install_started
                        } catch (failure: UpdateFailure) {
                            status = UpdateStatus.Failed(failure.error)
                            runCatching { settings.status = status }
                        } catch (failure: Exception) {
                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                            status = UpdateStatus.Failed(UpdateError.INSTALL)
                        } finally { busy = false; progress = null; verifying = false }
                    }
                }) { Text(stringResource(R.string.agent_updates_install, available.build)) }
            }
            progress?.let { (done, total) ->
                if (verifying) Text(stringResource(R.string.agent_updates_verifying))
                else Text(stringResource(R.string.agent_updates_downloading, done / 1_048_576, total / 1_048_576))
                if (total > 0) LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { work?.cancel() }) { Text(stringResource(R.string.agent_updates_cancel)) }
            }
            if (message != 0) Text(stringResource(message))
            Text(stringResource(R.string.agent_updates_friends, UpdateReleases.RELEASES_PAGE),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun statusText(status: UpdateStatus): String = when (status) {
    UpdateStatus.Unknown -> stringResource(R.string.agent_updates_status_none)
    is UpdateStatus.Current -> stringResource(R.string.agent_updates_status_current, status.installed)
    is UpdateStatus.Available -> stringResource(R.string.agent_updates_status_available, status.build)
    is UpdateStatus.Failed -> stringResource(when (status.error) {
        UpdateError.LOCKED -> R.string.agent_updates_error_locked
        UpdateError.NOT_FOUND -> R.string.agent_updates_error_not_found
        UpdateError.RATE_LIMIT -> R.string.agent_updates_error_rate_limit
        UpdateError.SERVICE -> R.string.agent_updates_error_service
        UpdateError.NETWORK -> R.string.agent_updates_error_network
        UpdateError.TIMEOUT -> R.string.agent_updates_error_timeout
        UpdateError.INVALID_RESPONSE -> R.string.agent_updates_error_invalid_response
        UpdateError.TOO_LARGE -> R.string.agent_updates_error_too_large
        UpdateError.STORAGE -> R.string.agent_updates_error_storage
        UpdateError.CHECKSUM -> R.string.agent_updates_error_checksum
        UpdateError.WRONG_PACKAGE -> R.string.agent_updates_error_wrong_package
        UpdateError.WRONG_SIGNATURE -> R.string.agent_updates_error_wrong_signature
        UpdateError.NOT_NEWER -> R.string.agent_updates_error_not_newer
        UpdateError.INSTALL -> R.string.agent_updates_error_install
    })
}
