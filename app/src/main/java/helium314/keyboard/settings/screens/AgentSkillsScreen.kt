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
import dev.agentkeyboard.core.BuiltinAction
import dev.agentkeyboard.core.SkillBinding
import dev.agentkeyboard.core.WorkflowDefinition
import helium314.keyboard.agent.SkillBindingStore
import helium314.keyboard.agent.AgentDocumentIO
import helium314.keyboard.agent.skillToolbarKey
import helium314.keyboard.agent.titleResource
import helium314.keyboard.agent.WorkflowStore
import helium314.keyboard.agent.workflowSkillLabel
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.*
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AgentSkillsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SkillBindingStore(context) }
    val scope = rememberCoroutineScope()
    var bindings by remember { mutableStateOf(SkillBinding.defaults()) }
    var loaded by remember { mutableStateOf(false) }
    var workflows by remember { mutableStateOf<List<WorkflowDefinition>>(emptyList()) }
    var workflowsUnavailable by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    var exportContent by rememberSaveable { mutableStateOf<String?>(null) }
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
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    bindings = withContext(Dispatchers.IO) {
                        store.import(AgentDocumentIO.readJson(context, uri, SkillBindingStore.MAX_BYTES))
                        store.read()
                    }
                    loaded = true; error = false; notice = R.string.agent_file_imported
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(store) {
        runCatching { withContext(Dispatchers.IO) { store.read() } }
            .onSuccess { bindings = it; loaded = true }.onFailure { error = true }
        runCatching { withContext(Dispatchers.IO) { WorkflowStore(context).read() } }
            .onSuccess { workflows = it; workflowsUnavailable = false }.onFailure { workflowsUnavailable = true }
    }
    val displayedBindings = bindings
    val displayedWorkflows = workflows
    val displayedLoaded = loaded
    val displayedBusy = busy
    val displayedError = error
    val displayedWorkflowsUnavailable = workflowsUnavailable
    val displayedNotice = notice
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_skills_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_skills_intro))
            Text(stringResource(R.string.agent_skill_file_description), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = displayedLoaded && !displayedBusy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            exportContent = withContext(Dispatchers.IO) { store.export() }
                            saveFile.launch("AgentKeyboard-skill-keys.json")
                        } catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.agent_file_export)) }
                TextButton(enabled = !displayedBusy, onClick = { openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                    Text(stringResource(R.string.agent_file_import))
                }
            }
            displayedBindings.forEach { binding ->
                key(binding) {
                    var label by remember { mutableStateOf(binding.label) }
                    var action by remember { mutableStateOf(binding.action) }
                    var workflowId by remember { mutableStateOf(binding.workflowId) }
                    var workflowMenu by remember { mutableStateOf(false) }
                    var menu by remember { mutableStateOf(false) }
                    val toolbarKey = skillToolbarKey(binding.slot)
                    var pinned by remember { mutableStateOf(toolbarKey in getPinnedToolbarKeys(context.prefs())) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.agent_skill_slot, binding.slot), style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(value = label, onValueChange = { if (it.length <= 32) label = it },
                                label = { Text(stringResource(R.string.agent_skill_label)) }, singleLine = true,
                                enabled = displayedLoaded && !displayedBusy, modifier = Modifier.fillMaxWidth().testTag("agent_skill_label_${binding.slot}"))
                            Box {
                                OutlinedButton(enabled = displayedLoaded && !displayedBusy, modifier = Modifier.testTag("agent_skill_action_${binding.slot}"),
                                    onClick = { menu = true }) { Text(stringResource(action.titleResource())) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    BuiltinAction.entries.forEach { candidate ->
                                        DropdownMenuItem(text = { Text(stringResource(candidate.titleResource())) }, onClick = {
                                            menu = false
                                            action = candidate
                                            if (label.isBlank() && candidate != BuiltinAction.NONE)
                                                label = context.getString(candidate.titleResource()).take(32)
                                        })
                                    }
                                }
                            }
                            if (action == BuiltinAction.WORKFLOW) {
                                val selected = displayedWorkflows.firstOrNull { it.id == workflowId }
                                Box {
                                    OutlinedButton(enabled = displayedLoaded && !displayedBusy,
                                        modifier = Modifier.testTag("agent_skill_workflow_${binding.slot}"),
                                        onClick = { workflowMenu = true }) {
                                        Text(selected?.name ?: stringResource(if (workflowId == null)
                                            R.string.agent_skill_choose_workflow else R.string.agent_skill_workflow_missing))
                                    }
                                    DropdownMenu(expanded = workflowMenu, onDismissRequest = { workflowMenu = false }) {
                                        displayedWorkflows.forEach { workflow ->
                                            DropdownMenuItem(text = { Text(workflow.name) }, onClick = {
                                                workflowMenu = false; workflowId = workflow.id
                                                if (label.isBlank() || label == context.getString(R.string.agent_skill_workflow))
                                                    label = workflowSkillLabel(workflow.name)
                                            })
                                        }
                                    }
                                }
                                Text(stringResource(R.string.agent_skill_workflow_reference), style = MaterialTheme.typography.bodySmall)
                                if (displayedWorkflowsUnavailable) Text(stringResource(R.string.agent_workflow_error))
                                TextButton(enabled = !displayedBusy, onClick = {
                                    busy = true
                                    scope.launch {
                                        try { workflows = withContext(Dispatchers.IO) { WorkflowStore(context).read() }; workflowsUnavailable = false }
                                        catch (_: Exception) { workflowsUnavailable = true }
                                        finally { busy = false }
                                    }
                                }) { Text(stringResource(R.string.agent_skill_reload_workflows)) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.agent_skill_pin), Modifier.weight(1f))
                                val pinLabel = stringResource(R.string.agent_skill_pin)
                                Switch(checked = pinned, enabled = !displayedBusy, modifier = Modifier.semantics { contentDescription = pinLabel }, onCheckedChange = { enabled ->
                                    if (enabled) addPinnedKey(context.prefs(), toolbarKey) else removePinnedKey(context.prefs(), toolbarKey)
                                    pinned = enabled
                                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                                })
                            }
                            val draft = runCatching { SkillBinding(binding.slot, label, action,
                                if (action == BuiltinAction.WORKFLOW) workflowId else null) }.getOrNull()
                            Button(enabled = displayedLoaded && !displayedBusy && draft != null,
                                modifier = Modifier.testTag("agent_skill_save_${binding.slot}"),
                                onClick = {
                                    val saved = draft ?: return@Button
                                    busy = true
                                    scope.launch {
                                        try {
                                            bindings = withContext(Dispatchers.IO) { store.set(saved, binding); store.read() }
                                            error = false
                                        } catch (_: Exception) { error = true }
                                        finally { busy = false }
                                    }
                                }) { Text(stringResource(R.string.agent_skill_save)) }
                        }
                    }
                }
            }
            if (displayedError) Text(stringResource(R.string.agent_skill_error))
            else if (displayedNotice != 0) Text(stringResource(displayedNotice))
        }
    }
}
