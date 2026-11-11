// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.agent.OnDeviceSpeechEngine
import helium314.keyboard.agent.SpeechEngine
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen

/**
 * Explains dictation and asks for the microphone. A keyboard service cannot show the system's
 * permission dialog itself, so this page is the only place where the permission is requested.
 */
@Composable
fun AgentDictationScreen(onClickBack: () -> Unit, engineFactory: (() -> SpeechEngine)? = null) {
    val context = LocalContext.current
    val engine = remember { engineFactory?.invoke() ?: OnDeviceSpeechEngine(context) }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var available by remember { mutableStateOf(engine.available) }
    var allowed by remember { mutableStateOf(granted()) }
    var refused by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // The permission and the system recognizer can change while this page is in the background.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { available = engine.available; allowed = granted() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        allowed = result
        refused = !result
    }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_dictation_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_dictation_description))
            Text(stringResource(if (available) R.string.agent_dictation_available else R.string.agent_dictation_unavailable),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (allowed) R.string.agent_dictation_permission_granted else R.string.agent_dictation_permission_missing),
                style = MaterialTheme.typography.titleMedium)
            if (!allowed && available) Button(onClick = { request.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(stringResource(R.string.agent_dictation_allow))
            }
            if (refused && !allowed) Text(stringResource(R.string.agent_dictation_permission_refused))
            Text(stringResource(R.string.agent_dictation_howto), style = MaterialTheme.typography.bodySmall)
        }
    }
}
