// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.RejectedCorrections
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
import helium314.keyboard.agent.AgentDocumentIO
import helium314.keyboard.agent.LearningData
import helium314.keyboard.agent.LearningSource
import helium314.keyboard.agent.UserHistoryLearningSource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Pause, inspect, export and delete what the keyboard learned from typing. All on this device. */
@Composable
fun AgentLearningScreen(onClickBack: () -> Unit, sourceFactory: (() -> LearningSource)? = null) {
    val context = LocalContext.current
    val source = remember { sourceFactory?.invoke() ?: UserHistoryLearningSource(context) }
    val scope = rememberCoroutineScope()
    val enabled = context.prefs().getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS)
    var paused by remember { mutableStateOf(LearningData.isPaused(context)) }
    var counts by remember { mutableStateOf<Map<String, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Learned words are private: deliberately not rememberSaveable, never in saved instance state.
    var exportContent by remember { mutableStateOf<String?>(null) }

    fun run(done: Int, work: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { work(); notice = done }
            catch (_: Exception) { notice = R.string.agent_learning_failed }
            finally { busy = false }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val encoded = exportContent
        exportContent = null
        if (uri != null && encoded != null) run(R.string.agent_file_exported) {
            withContext(Dispatchers.IO) { AgentDocumentIO.writeJson(context, uri, encoded, LearningData.MAX_EXPORT_BYTES) }
        } else if (uri != null) {
            // The screen was recreated while the file picker was open: the words were deliberately
            // not kept. Say so and remove the empty document instead of leaving a 0-byte "export".
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            notice = R.string.agent_learning_failed
        }
    }

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_learning_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_learning_description))
            Text(stringResource(if (enabled) R.string.agent_learning_enabled else R.string.agent_learning_disabled),
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.agent_learning_pause), Modifier.weight(1f))
                Switch(checked = paused, enabled = !busy, onCheckedChange = { value ->
                    runCatching { LearningData.setPaused(context, value) }.onSuccess { paused = value; notice = 0 }
                        .onFailure { notice = R.string.agent_learning_failed }
                })
            }
            Text(stringResource(R.string.agent_learning_pause_description), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.agent_learning_data), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(enabled = !busy, onClick = {
                run(0) { counts = withContext(Dispatchers.IO) { LearningData.counts(source) } }
            }) { Text(stringResource(R.string.agent_learning_count)) }
            counts?.let { loaded ->
                if (loaded.isEmpty()) Text(stringResource(R.string.agent_learning_empty))
                loaded.forEach { (language, words) ->
                    Text(stringResource(R.string.agent_learning_language_count,
                        Locale.forLanguageTag(language).getDisplayName(Locale.getDefault()), words))
                }
            }
            OutlinedButton(enabled = !busy, onClick = {
                run(0) {
                    exportContent = withContext(Dispatchers.IO) { LearningData.export(source) }
                    saveFile.launch("agent-keyboard-learned-words.json")
                }
            }) { Text(stringResource(R.string.agent_learning_export)) }
            Text(stringResource(R.string.agent_learning_export_description), style = MaterialTheme.typography.bodySmall)
            Button(enabled = !busy, onClick = { confirmDelete = true }) { Text(stringResource(R.string.agent_learning_delete)) }
            if (notice != 0) Text(stringResource(notice))
            HorizontalDivider()
            AutocorrectSection(busy)
        }
    }
    if (confirmDelete) ConfirmationDialog(
        onDismissRequest = { confirmDelete = false },
        onConfirmed = {
            confirmDelete = false
            run(R.string.agent_learning_deleted) {
                withContext(Dispatchers.IO) { source.deleteAll() }
                counts = emptyMap()
            }
        },
        content = { Text(stringResource(R.string.agent_learning_delete_confirm)) },
        confirmButtonText = stringResource(R.string.agent_learning_delete),
    )
}

/** Stage 20a: rules that keep known words, and the opt-in memory of undone corrections. */
@Composable
private fun AutocorrectSection(busy: Boolean) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var keepUmlaut by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true)) }
    var keepValid by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false)) }
    var remember by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, false)) }
    var stored by remember { mutableIntStateOf(RejectedCorrections.all(context).size) }
    var confirmClear by remember { mutableStateOf(false) }

    @Composable
    fun toggle(title: Int, description: Int, checked: Boolean, key: String, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(title), Modifier.weight(1f))
            Switch(checked = checked, enabled = !busy, onCheckedChange = { value ->
                if (prefs.edit().putBoolean(key, value).commit()) onChange(value)
            })
        }
        Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
    }

    Text(stringResource(R.string.agent_autocorrect_title), style = MaterialTheme.typography.titleMedium)
    toggle(R.string.agent_autocorrect_keep_umlaut, R.string.agent_autocorrect_keep_umlaut_description,
        keepUmlaut, AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS) { keepUmlaut = it }
    toggle(R.string.agent_autocorrect_keep_valid, R.string.agent_autocorrect_keep_valid_description,
        keepValid, AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS) { keepValid = it }
    toggle(R.string.agent_autocorrect_remember, R.string.agent_autocorrect_remember_description,
        remember, AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED) { remember = it }
    Text(stringResource(R.string.agent_autocorrect_stored, stored))
    OutlinedButton(enabled = !busy && stored > 0, onClick = { confirmClear = true }) {
        Text(stringResource(R.string.agent_autocorrect_clear))
    }
    if (confirmClear) ConfirmationDialog(
        onDismissRequest = { confirmClear = false },
        onConfirmed = {
            confirmClear = false
            runCatching { RejectedCorrections.clear(context) }
            stored = RejectedCorrections.all(context).size
        },
        content = { Text(stringResource(R.string.agent_autocorrect_clear_confirm)) },
        confirmButtonText = stringResource(R.string.agent_autocorrect_clear),
    )
}
