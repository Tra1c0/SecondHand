// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentkeyboard.core.*
import helium314.keyboard.agent.*
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AgentWorkflowsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val stateHolder = rememberSaveableStateHolder()
    var accessible by remember { mutableStateOf(credentialAccessAllowed(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessible = credentialAccessAllowed(context)
            if (event == Lifecycle.Event.ON_STOP) accessible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!accessible) {
        SearchSettingsScreen(onClickBack, stringResource(R.string.agent_workflows_title), emptyList()) {
            Text(stringResource(R.string.agent_ai_unlock), Modifier.padding(16.dp))
        }
    } else stateHolder.SaveableStateProvider("workflow-settings") { AgentWorkflowsUnlockedScreen(onClickBack) }
}

@Composable
private fun AgentWorkflowsUnlockedScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WorkflowStore(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var configuration by remember { mutableStateOf<WorkflowConfiguration?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<WorkflowRecovery?>(null) }
    var resetting by remember { mutableStateOf<WorkflowRecovery?>(null) }
    var notice by remember { mutableIntStateOf(0) }
    var builder by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reviewingProposal by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ManualWorkflowDraft?>(null) }
    var editRevision by remember { mutableStateOf<Int?>(null) }
    var editGeneration by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Pair<WorkflowDefinition, String>?>(null) }
    var pendingImport by remember { mutableStateOf<Pair<List<WorkflowDefinition>, String>?>(null) }
    var importGeneration by rememberSaveable { mutableStateOf<String?>(null) }
    var exportContent by rememberSaveable { mutableStateOf<String?>(null) }

    fun refresh() {
        busy = true
        scope.launch {
            try {
                val (loaded, resetToken) = withContext(Dispatchers.IO) {
                    val loaded = runCatching { store.snapshot() }.getOrNull()
                    loaded to if (loaded == null) runCatching { store.recoveryToken() }.getOrNull() else null
                }
                configuration = loaded; recovery = resetToken; error = loaded == null
            }
            finally { busy = false }
        }
    }
    LaunchedEffect(store) { refresh() }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val encoded = exportContent; exportContent = null
        if (uri != null && encoded != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { AgentDocumentIO.writeJson(context, uri, encoded, WorkflowStore.MAX_BYTES) }
                    error = false; notice = R.string.agent_file_exported
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val generation = importGeneration; importGeneration = null
        if (uri != null && generation != null) {
            busy = true
            scope.launch {
                try {
                    val draft = withContext(Dispatchers.IO) {
                        WorkflowStore.decode(AgentDocumentIO.readJson(context, uri, WorkflowStore.MAX_BYTES))
                    }
                    pendingImport = draft to generation; error = false
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
    }

    val displayedConfiguration = configuration
    val displayedRecovery = recovery
    val displayedError = error
    val displayedBusy = busy
    val displayedNotice = notice
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_workflows_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_workflows_description))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !displayedBusy && displayedConfiguration != null, onClick = {
                    exportContent = WorkflowStore.encode(displayedConfiguration!!.definitions)
                    saveFile.launch("AgentKeyboard-workflows.json")
                }) { Text(stringResource(R.string.agent_file_export)) }
                TextButton(enabled = !displayedBusy && displayedConfiguration != null, onClick = {
                    importGeneration = displayedConfiguration!!.generation
                    openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }) { Text(stringResource(R.string.agent_file_import)) }
                TextButton(enabled = !displayedBusy, onClick = ::refresh) { Text(stringResource(R.string.agent_runs_refresh)) }
            }
            Button(enabled = !displayedBusy && displayedConfiguration != null && displayedConfiguration!!.definitions.size < WorkflowStore.MAX_WORKFLOWS,
                onClick = {
                    editGeneration = displayedConfiguration!!.generation; editRevision = null; error = false; reviewingProposal = false
                    val settings = ModelSettings(context)
                    editing = ManualWorkflowDraft.new(settings.openAiModel, settings.translationLanguage, settings.writingTone)
                }) { Text(stringResource(R.string.agent_workflow_create)) }
            OutlinedButton(enabled = !displayedBusy && displayedConfiguration != null && displayedConfiguration.definitions.size < WorkflowStore.MAX_WORKFLOWS,
                onClick = {
                    if (!workflowDraftAllowed(context, lifecycle)) {
                        notice = R.string.agent_builder_unavailable; error = false
                        return@OutlinedButton
                    }
                    runCatching { ModelSettings(context).openAiModel }.onSuccess {
                        builder = it to displayedConfiguration!!.generation
                    }.onFailure { error = true }
                }) { Text(stringResource(R.string.agent_builder_title)) }
            displayedConfiguration?.definitions?.forEach { definition ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorkflowDescription(definition)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !displayedBusy, onClick = {
                                editGeneration = displayedConfiguration!!.generation; editRevision = definition.revision; reviewingProposal = false
                                editing = ManualWorkflowDraft.edit(definition); error = false
                            }) { Text(stringResource(R.string.agent_workflow_edit)) }
                            TextButton(enabled = !displayedBusy, onClick = { deleting = definition to displayedConfiguration!!.generation }) {
                                Text(stringResource(R.string.agent_workflow_delete))
                            }
                        }
                    }
                }
            }
            displayedRecovery?.let { token ->
                TextButton(enabled = !displayedBusy, onClick = { resetting = token }) {
                    Text(stringResource(R.string.agent_workflow_reset_unreadable))
                }
            }
            if (displayedError) Text(stringResource(R.string.agent_workflow_error))
            else if (displayedNotice != 0) Text(stringResource(displayedNotice))
            if (displayedConfiguration?.definitions?.isEmpty() == true) Text(stringResource(R.string.agent_workflow_empty))
        }
    }
    if (builder != null || (reviewingProposal && editing != null)) ProtectWorkflowDraft {
        builder = null
        if (reviewingProposal) editing = null
        reviewingProposal = false
    }
    builder?.let { launch ->
        val (model, generation) = launch
        WorkflowBuilderDialog(model, onDismiss = { if (builder === launch) builder = null }, onPrepared = prepared@ { definition ->
            if (builder !== launch) return@prepared
            builder = null; editGeneration = generation; editRevision = null; error = false
            reviewingProposal = true; editing = ManualWorkflowDraft.proposal(definition)
        })
    }
    resetting?.let { token ->
        AlertDialog(onDismissRequest = { if (!busy) resetting = null },
            title = { Text(stringResource(R.string.agent_workflow_reset_unreadable)) },
            text = { Text(stringResource(R.string.agent_workflow_reset_explanation)) },
            dismissButton = { TextButton(enabled = !displayedBusy, onClick = { resetting = null }) {
                Text(stringResource(android.R.string.cancel))
            } }, confirmButton = { TextButton(enabled = !displayedBusy, onClick = {
                busy = true
                scope.launch {
                    try {
                        configuration = withContext(Dispatchers.IO) { store.resetUnreadable(token); store.snapshot() }
                        recovery = null; error = false; notice = 0
                    } catch (_: Exception) { error = true }
                    finally { busy = false; resetting = null }
                }
            }) { Text(stringResource(R.string.agent_workflow_reset_confirm)) } })
    }
    editing?.let { initial ->
        val isProposal = reviewingProposal
        val generation = editGeneration
        val revision = editRevision
        WorkflowEditDialog(initial, displayedBusy, displayedError, isProposal,
            onDismiss = { if (!busy && editing === initial) editing = null }, onSave = save@ { definition ->
            if (busy || editing !== initial) return@save
            if (isProposal && !workflowDraftAllowed(context, lifecycle)) { editing = null; return@save }
            busy = true
            scope.launch {
                try {
                    configuration = withContext(Dispatchers.IO) {
                        store.save(definition, revision, generation); store.snapshot()
                    }
                    editing = null; error = false; notice = R.string.agent_workflow_saved
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        })
    }
    deleting?.let { (definition, generation) ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text(stringResource(R.string.agent_workflow_delete)) },
            text = { Text(definition.name) }, dismissButton = {
                TextButton(enabled = !displayedBusy, onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) }
            }, confirmButton = {
                TextButton(enabled = !displayedBusy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            configuration = withContext(Dispatchers.IO) { store.delete(definition.id, definition.revision, generation); store.snapshot() }
                            error = false
                        } catch (_: Exception) { error = true }
                        finally { busy = false; deleting = null }
                    }
                }) { Text(stringResource(R.string.agent_workflow_delete)) }
            })
    }
    pendingImport?.let { (definitions, generation) ->
        AlertDialog(onDismissRequest = { if (!busy) pendingImport = null },
            title = { Text(stringResource(R.string.agent_workflow_import_review)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.agent_workflow_import_replace, definitions.size))
                    definitions.forEach { WorkflowDescription(it) }
                    if (displayedError) Text(stringResource(R.string.agent_workflow_error))
                }
            }, dismissButton = {
                TextButton(enabled = !displayedBusy, onClick = { pendingImport = null }) { Text(stringResource(android.R.string.cancel)) }
            }, confirmButton = {
                TextButton(enabled = !displayedBusy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            configuration = withContext(Dispatchers.IO) { store.replaceAll(definitions, generation); store.snapshot() }
                            pendingImport = null; error = false; notice = R.string.agent_workflow_imported
                        } catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.agent_workflow_activate)) }
            })
    }
}

@Composable
private fun WorkflowDescription(definition: WorkflowDefinition) {
    val context = LocalContext.current
    Text(definition.name, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.agent_workflow_revision, definition.revision), style = MaterialTheme.typography.bodySmall)
    definition.steps.forEachIndexed { index, step ->
        val action = when (step) {
            is WorkflowStep.Local -> BuiltinAction.entries.single { it.local == step.action }
            is WorkflowStep.Ai -> BuiltinAction.entries.single { it.ai == step.action }
        }
        Text(stringResource(R.string.agent_workflow_step_description, index + 1, stringResource(action.titleResource()),
            definition.inputSummary(context, step.input)))
        if (step is WorkflowStep.Ai) Text(step.provider.id + " · " + step.model + " · " + step.language.code + " · " + stringResource(step.tone.titleResource()),
            style = MaterialTheme.typography.bodySmall)
        step.condition?.let { Text(it.summary(context), style = MaterialTheme.typography.bodySmall) }
    }
    if (definition.steps.any { it.condition != null }) Text(stringResource(R.string.agent_condition_fallback), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun WorkflowEditDialog(initial: ManualWorkflowDraft, busy: Boolean, error: Boolean, proposal: Boolean,
                               onDismiss: () -> Unit, onSave: (WorkflowDefinition) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val valid = runCatching { draft.definition() }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = if (proposal) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit), title = { Text(stringResource(if (proposal) R.string.agent_builder_draft_review else R.string.agent_workflow_edit)) }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (proposal) Text(stringResource(R.string.agent_builder_save_explanation))
            OutlinedTextField(value = draft.name, onValueChange = { if (it.length <= 64) draft = draft.copy(name = it) },
                enabled = !busy, label = { Text(stringResource(R.string.agent_workflow_name)) }, singleLine = true,
                keyboardOptions = if (proposal) KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password) else KeyboardOptions.Default)
            Text(stringResource(R.string.agent_workflow_config_limits, WorkflowDefinition.MAX_STEPS, WorkflowDefinition.MAX_MODEL_CALLS),
                style = MaterialTheme.typography.bodySmall)
            draft.steps.forEachIndexed { index, step ->
                Text(stringResource(R.string.agent_workflow_step_number, index + 1), style = MaterialTheme.typography.titleSmall)
                fun update(value: ManualWorkflowStep) { draft = draft.copy(steps = draft.steps.mapIndexed { i, old -> if (i == index) value else old }) }
                WorkflowChoice(step.action, BuiltinAction.entries.filter { it.local != null || it.ai != null }, !busy,
                    { stringResource(it.titleResource()) }, { update(step.copy(action = it)) })
                if (index > 0) WorkflowChoice(step.inputStepIndex, listOf<Int?>(null) + (0 until index).toList(), !busy,
                    { previous -> if (previous == null) stringResource(R.string.agent_workflow_input_selection)
                        else stringResource(R.string.agent_workflow_input_step, previous + 1) },
                    { update(step.copy(inputStepIndex = it)) })
                if (step.action.ai != null) {
                    WorkflowChoice(step.provider, listOf(WorkflowProvider.OPENAI), !busy, { it.id }, { update(step.copy(provider = it)) })
                    OutlinedTextField(value = step.model, onValueChange = { if (it.length <= 100) update(step.copy(model = it)) },
                        enabled = !busy, label = { Text(stringResource(R.string.agent_ai_model)) }, singleLine = true)
                    WorkflowChoice(step.language, TranslationLanguage.entries, !busy, { it.code }, { update(step.copy(language = it)) })
                    WorkflowChoice(step.tone, WritingTone.entries, !busy, { stringResource(it.titleResource()) }, { update(step.copy(tone = it)) })
                }
                WorkflowChoice(step.condition?.predicate, listOf<TextPredicate?>(null) + TextPredicate.entries, !busy, {
                    stringResource(when (it) {
                        null -> R.string.agent_condition_always
                        TextPredicate.NON_EMPTY -> R.string.agent_condition_non_empty
                        TextPredicate.CONTAINS_LITERAL -> R.string.agent_condition_choice_contains
                        TextPredicate.LENGTH_AT_LEAST -> R.string.agent_condition_choice_length
                    })
                }, { predicate ->
                    if (predicate != step.condition?.predicate)
                        update(step.copy(condition = predicate?.let { ManualWorkflowCondition(it,
                            minimumLength = if (it == TextPredicate.LENGTH_AT_LEAST) "100" else "") }))
                })
                step.condition?.let { condition ->
                    when (condition.predicate) {
                        TextPredicate.CONTAINS_LITERAL -> OutlinedTextField(value = condition.literal,
                            onValueChange = { if (it.length <= 128) update(step.copy(condition = condition.copy(literal = it))) },
                            enabled = !busy, label = { Text(stringResource(R.string.agent_condition_literal)) },
                            keyboardOptions = if (proposal) KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                            supportingText = { Text(stringResource(R.string.agent_condition_literal_help)) })
                        TextPredicate.LENGTH_AT_LEAST -> OutlinedTextField(value = condition.minimumLength,
                            onValueChange = { if (it.length <= 4) update(step.copy(condition = condition.copy(minimumLength = it))) },
                            enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text(stringResource(R.string.agent_condition_minimum_length)) })
                        TextPredicate.NON_EMPTY -> Unit
                    }
                    val invertLabel = stringResource(R.string.agent_condition_invert)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(invertLabel, Modifier.weight(1f))
                        Switch(checked = condition.negated, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = invertLabel },
                            onCheckedChange = { update(step.copy(condition = condition.copy(negated = it))) })
                    }
                    Text(stringResource(R.string.agent_condition_fallback), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (draft.steps.size < WorkflowDefinition.MAX_STEPS) TextButton(enabled = !busy, onClick = {
                draft = draft.copy(steps = draft.steps + ManualWorkflowStep(BuiltinAction.CLEAN_SPACES, inputStepIndex = draft.steps.lastIndex))
            }) { Text(stringResource(R.string.agent_workflow_add_step)) }
            if (draft.steps.size > 1) TextButton(enabled = !busy, onClick = { draft = draft.copy(steps = draft.steps.dropLast(1)) }) {
                Text(stringResource(R.string.agent_workflow_remove_step))
            }
            if (error) Text(stringResource(R.string.agent_workflow_error))
        }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        confirmButton = { TextButton(enabled = !busy && valid != null, onClick = { valid?.let(onSave) }) {
            Text(stringResource(R.string.agent_workflow_save))
        } })
}

@Composable
private fun <T> WorkflowChoice(value: T, options: List<T>, enabled: Boolean, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text(label(value)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { expanded = false; onSelect(option) }) }
        }
    }
}
