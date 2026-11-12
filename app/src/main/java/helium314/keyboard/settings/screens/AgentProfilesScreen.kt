// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.ProfileGroup
import helium314.keyboard.agent.FlatPreset
import helium314.keyboard.agent.KeyboardProfileStore
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.AgentDocumentIO
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AgentProfilesScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { KeyboardProfileStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Pair<ProfileGroup, Boolean>?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var exportContent by rememberSaveable { mutableStateOf<String?>(null) }
    var importGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val encoded = exportContent
        exportContent = null
        if (uri != null && encoded != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { AgentDocumentIO.writeJson(context, uri, encoded) }
                    error = false; notice = R.string.agent_file_exported
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val group = importGroup?.let { runCatching { ProfileGroup.valueOf(it) }.getOrNull() }
        importGroup = null
        if (uri != null && group != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        store.importSaved(AgentDocumentIO.readJson(context, uri), group)
                    }
                    revision++; error = false; notice = R.string.agent_profile_imported
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    }

    fun runChange(change: () -> Unit) {
        runCatching(change).onSuccess { revision++; error = false }.onFailure { error = true }
    }

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_profiles_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.agent_profiles_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            key(revision) {
                val flatPreset = remember { FlatPreset(context) }
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.agent_flat_preset_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.agent_flat_preset_description), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy, onClick = { runChange { flatPreset.apply() } }) {
                                Text(stringResource(R.string.agent_flat_preset_apply))
                            }
                            TextButton(enabled = !busy && flatPreset.canRestore, onClick = { runChange { flatPreset.restore() } }) {
                                Text(stringResource(R.string.agent_flat_preset_restore))
                            }
                        }
                    }
                }
                store.availableGroups.forEach { group ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(group.titleResource()), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(group.descriptionResource()), style = MaterialTheme.typography.bodyMedium)
                            if (group == ProfileGroup.GESTURES) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.agent_space_long_press), Modifier.weight(1f))
                                    Switch(checked = context.prefs().getBoolean(AgentPreferences.SPACE_LONG_PRESS, false),
                                        onCheckedChange = { enabled -> runChange {
                                            check(context.prefs().edit().putBoolean(AgentPreferences.SPACE_LONG_PRESS, enabled).commit())
                                        } })
                                }
                                Text(stringResource(R.string.agent_space_long_press_description), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy, onClick = {
                                    if (store.hasSaved(group)) pending = group to false
                                    else runChange { store.saveCurrent(group) }
                                }) { Text(stringResource(R.string.agent_profile_save)) }
                                TextButton(enabled = !busy && store.hasSaved(group), onClick = {
                                    runChange { store.restoreSaved(group) }
                                }) { Text(stringResource(R.string.agent_profile_apply)) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy && store.hasSaved(group), onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            exportContent = withContext(Dispatchers.IO) { requireNotNull(store.exportSaved(group)) }
                                            saveFile.launch("AgentKeyboard-${group.name.lowercase(java.util.Locale.ROOT)}-profile.json")
                                        } catch (_: Exception) { error = true }
                                        finally { busy = false }
                                    }
                                }) { Text(stringResource(R.string.agent_file_export)) }
                                TextButton(enabled = !busy, onClick = {
                                    importGroup = group.name
                                    openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                                }) { Text(stringResource(R.string.agent_file_import)) }
                            }
                            TextButton(enabled = !busy, onClick = { pending = group to true }) {
                                Text(stringResource(R.string.agent_profile_reset))
                            }
                        }
                    }
                }
            }
            if (notice != 0) Text(stringResource(notice))
        }
    }
    pending?.let { (group, reset) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(group.titleResource())) },
            text = { Text(stringResource(if (reset) R.string.agent_profile_reset_confirm else R.string.agent_profile_overwrite_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    runChange { if (reset) store.reset(group) else store.saveCurrent(group) }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (error) {
        AlertDialog(
            onDismissRequest = { error = false },
            text = { Text(stringResource(R.string.agent_profile_error)) },
            confirmButton = { TextButton(onClick = { error = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

private fun ProfileGroup.titleResource() = when (this) {
    ProfileGroup.DESIGN -> R.string.agent_profile_design
    ProfileGroup.LAYOUT -> R.string.agent_profile_layout
    ProfileGroup.GESTURES -> R.string.agent_profile_gestures
}

private fun ProfileGroup.descriptionResource() = when (this) {
    ProfileGroup.DESIGN -> R.string.agent_profile_design_description
    ProfileGroup.LAYOUT -> R.string.agent_profile_layout_description
    ProfileGroup.GESTURES -> R.string.agent_profile_gestures_description
}
