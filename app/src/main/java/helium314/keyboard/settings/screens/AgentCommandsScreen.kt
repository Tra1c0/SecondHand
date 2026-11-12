// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.pm.PackageManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentkeyboard.core.CommandSyntax
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen

/**
 * Stage 24: switch, prefix and one switch per command. A command that needs a runtime permission
 * (24c contacts) asks for it here: a keyboard service cannot show the system's permission dialog.
 */
@Composable
fun AgentCommandsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(KeyboardCommands.isEnabled(context)) }
    var prefix by remember { mutableStateOf(KeyboardCommands.prefix(context)) }
    fun granted() = KeyboardCommands.registry.mapNotNull { it.permission }.toSet()
        .filter { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }.toSet()
    var allowed by remember { mutableStateOf(granted()) }
    var refused by remember { mutableStateOf(setOf<String>()) }
    var asked by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // the permission can change in the system settings while this page is in the background
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = granted() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        val permission = asked ?: return@rememberLauncherForActivityResult
        allowed = if (result) allowed + permission else allowed - permission
        refused = if (result) refused - permission else refused + permission
    }
    fun ask(permission: String) { asked = permission; request.launch(permission) }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_commands_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_commands_description, prefix.toString()))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.agent_commands_switch), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    KeyboardCommands.setEnabled(context, it)
                })
            }
            if (!enabled) return@Column
            Text(stringResource(R.string.agent_commands_prefix), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.agent_commands_prefix_description))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in CommandSyntax.ALLOWED_PREFIXES) {
                    FilterChip(selected = option == prefix, onClick = {
                        prefix = option
                        KeyboardCommands.setPrefix(context, option)
                    }, label = { Text(option.toString(), style = MaterialTheme.typography.titleMedium) })
                }
            }
            Text(stringResource(R.string.agent_commands_list), style = MaterialTheme.typography.titleMedium)
            if (KeyboardCommands.registry.isEmpty()) Text(stringResource(R.string.agent_commands_none))
            for (command in KeyboardCommands.registry) {
                var on by remember(command.name) { mutableStateOf(KeyboardCommands.isCommandEnabled(context, command.name)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$prefix${command.name}", style = MaterialTheme.typography.titleMedium)
                        Text(command.description(context), style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = on, onCheckedChange = {
                        on = it
                        KeyboardCommands.setCommandEnabled(context, command.name, it)
                        val permission = command.permission
                        if (it && permission != null && permission !in allowed) ask(permission)
                    })
                }
                val permission = command.permission
                if (on && permission != null && permission !in allowed) {
                    Text(stringResource(R.string.agent_commands_permission_missing, "$prefix${command.name}"))
                    Button(onClick = { ask(permission) }) { Text(stringResource(R.string.agent_commands_permission_allow)) }
                    if (permission in refused) Text(stringResource(R.string.agent_commands_permission_refused))
                }
            }
            AgentScreenReaderSection(prefix) // stage 24e
            AgentSnippetsSection(prefix) // stage 24f
        }
    }
}
