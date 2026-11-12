// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.agent.commands.ScreenReaderService
import helium314.keyboard.latin.R

/**
 * Stage 24e: explains the screen reader for `@url` and leads to the system's accessibility
 * settings, where only the user can switch it on. Shown on the commands page.
 */
@Composable
fun AgentScreenReaderSection(prefix: Char) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(ScreenReaderService.isAvailable) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // the service is switched on in the system settings while this page is in the background
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) on = ScreenReaderService.isAvailable
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.agent_screen_reader_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.agent_screen_reader_explanation, prefix.toString()))
        Text(stringResource(if (on) R.string.agent_screen_reader_on else R.string.agent_screen_reader_off),
            style = MaterialTheme.typography.titleSmall)
        Button(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }) { Text(stringResource(R.string.agent_screen_reader_open_settings)) }
        Text(stringResource(R.string.agent_screen_reader_restricted))
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)))
            }
        }) { Text(stringResource(R.string.agent_screen_reader_app_info)) }
    }
}
