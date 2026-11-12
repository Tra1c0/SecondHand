// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

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
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.ScreenshotChip
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen

/**
 * Switch and image permission for the screenshot chip. A keyboard service cannot show the
 * system's permission dialog itself, so the permission is requested here.
 */
@Composable
fun AgentScreenshotScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ScreenshotChip.isEnabled(context)) }
    var allowed by remember { mutableStateOf(ScreenshotChip.hasPermission(context)) }
    var refused by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // the permission can change in the system settings while this page is in the background
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = ScreenshotChip.hasPermission(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        allowed = result
        refused = !result
    }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_screenshot_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_screenshot_description))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.agent_screenshot_switch), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    context.prefs().edit { putBoolean(AgentPreferences.SCREENSHOT_CHIP, it) }
                    if (!it) ScreenshotChip.cleanUp(context, System.currentTimeMillis(), all = true)
                    if (it && !allowed) request.launch(ScreenshotChip.permission())
                })
            }
            if (enabled) {
                Text(stringResource(if (allowed) R.string.agent_screenshot_permission_granted else R.string.agent_screenshot_permission_missing),
                    style = MaterialTheme.typography.titleMedium)
                if (!allowed) Button(onClick = { request.launch(ScreenshotChip.permission()) }) {
                    Text(stringResource(R.string.agent_screenshot_allow))
                }
                if (!allowed && ScreenshotChip.hasOnlySelectedPhotos(context))
                    Text(stringResource(R.string.agent_screenshot_selected_only))
                else if (refused && !allowed) Text(stringResource(R.string.agent_screenshot_permission_refused))
            }
        }
    }
}
