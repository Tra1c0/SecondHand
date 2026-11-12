// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.BuiltinAction
import helium314.keyboard.agent.*
import helium314.keyboard.latin.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Used only within the lifecycle/unlock-gated AI settings surface. */
@Composable
internal fun AgentRunStatus() {
    val context = LocalContext.current
    val store = remember { EncryptedActionRunStore(context) }
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<ActionRunRecord>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var workflowNames by remember { mutableStateOf<Map<Pair<String, Int>, String>>(emptyMap()) }
    fun refresh() {
        busy = true
        scope.launch {
            try {
                records = store.records()
                workflowNames = withContext(Dispatchers.IO) { runCatching {
                    WorkflowStore(context).read().associate { (it.id to it.revision) to it.name }
                }.getOrDefault(emptyMap()) }
                error = false
            }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(store) { refresh() }
    HorizontalDivider()
    Text(stringResource(R.string.agent_runs_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.agent_runs_description), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(enabled = !busy, onClick = ::refresh) { Text(stringResource(R.string.agent_runs_refresh)) }
        TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { store.clear(); records = emptyList(); error = false }
                catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.agent_runs_clear)) }
    }
    if (error) Text(stringResource(R.string.agent_runs_unavailable))
    else if (records.isEmpty() && !busy) Text(stringResource(R.string.agent_runs_empty))
    records.forEach { record ->
        key(record.id) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val title = when (val subject = record.subject) {
                        is RunSubject.TextAction -> stringResource(textActionTitle(subject.action))
                        is RunSubject.Workflow -> workflowNames[subject.definitionId to subject.revision]?.let {
                            stringResource(R.string.agent_runs_named_workflow, it, subject.revision)
                        } ?: stringResource(R.string.agent_runs_workflow_title, subject.revision)
                        RunSubject.WorkflowBuilder -> stringResource(R.string.agent_runs_builder_title)
                        is RunSubject.Tool -> stringResource(subject.tool.titleResource())
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (record.provider != null) Text(record.provider + " · " + record.model)
                    when (val subject = record.subject) {
                        is RunSubject.Workflow -> Text(stringResource(R.string.agent_runs_workflow_reference, subject.definitionId.take(8), record.id.take(8)),
                            style = MaterialTheme.typography.bodySmall)
                        is RunSubject.TextAction -> subject.workflow?.let { link ->
                            Text(stringResource(R.string.agent_runs_step_reference, link.runId.take(8), link.stepId), style = MaterialTheme.typography.bodySmall)
                        }
                        RunSubject.WorkflowBuilder, is RunSubject.Tool -> Unit
                    }
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.startedAt)),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(when (record.state) {
                        ActionRunState.RUNNING -> R.string.agent_runs_running
                        ActionRunState.SUCCEEDED -> when (record.subject) {
                            is RunSubject.Workflow -> R.string.agent_runs_workflow_succeeded
                            RunSubject.WorkflowBuilder -> R.string.agent_runs_builder_succeeded
                            is RunSubject.TextAction -> R.string.agent_runs_succeeded
                            is RunSubject.Tool -> if (record.subject.tool.external) R.string.agent_runs_tool_handed_off
                                else R.string.agent_runs_tool_test_succeeded
                        }
                        ActionRunState.FAILED -> R.string.agent_runs_failed
                        ActionRunState.CANCELLED -> R.string.agent_runs_cancelled
                        ActionRunState.UNKNOWN -> R.string.agent_runs_unknown
                    }))
                }
            }
        }
    }
}

/**
 * Title of a journaled text action. The free question of `@ki` (AiTextAction.ASK) has no built-in
 * action on purpose, so it must not be looked up there: a missing entry would crash this page.
 */
internal fun textActionTitle(action: dev.agentkeyboard.core.AiTextAction): Int =
    BuiltinAction.entries.firstOrNull { it.ai == action }?.titleResource() ?: R.string.agent_command_ki_task
