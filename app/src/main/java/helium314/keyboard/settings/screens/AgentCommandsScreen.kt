// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

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
import dev.agentkeyboard.core.CommandSyntax
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen

/** Stage 24: switch, prefix and one switch per command. */
@Composable
fun AgentCommandsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(KeyboardCommands.isEnabled(context)) }
    var prefix by remember { mutableStateOf(KeyboardCommands.prefix(context)) }
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
                    })
                }
            }
            AgentScreenReaderSection(prefix) // stage 24e
        }
    }
}
