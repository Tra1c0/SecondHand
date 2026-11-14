// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.SnippetApps
import dev.agentkeyboard.core.SnippetTemplate
import helium314.keyboard.agent.commands.SnipCommand
import helium314.keyboard.agent.commands.Snippet
import helium314.keyboard.agent.commands.SnippetStore
import helium314.keyboard.latin.AppsManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Stage 24f: the snippets of `@snip` on the command settings page: list, add, edit, delete.
 * Saving happens only on "Save"; nothing is logged. Stage 42: templates with a live preview,
 * placeholder help and the apps a snippet is limited to.
 */
@Composable
fun AgentSnippetsSection(prefix: Char) {
    val context = LocalContext.current
    val store = remember { SnippetStore(context) }
    fun load(): List<Snippet>? = runCatching { store.read() }.getOrNull()
    var snippets by remember { mutableStateOf(load()) }
    // null: closed; a snippet with an empty name: a new one
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var deleting by remember { mutableStateOf<Snippet?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.agent_snippets_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.agent_snippets_description, prefix.toString()))
        val current = snippets
        if (current == null) {
            Text(stringResource(R.string.agent_snippets_unavailable))
            return@Column
        }
        if (current.isEmpty()) Text(stringResource(R.string.agent_snippets_none))
        for (snippet in current) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$prefix${SnipCommand.name} ${snippet.name}", style = MaterialTheme.typography.titleSmall)
                    Text(SnipCommand.preview(snippet.text, 60), style = MaterialTheme.typography.bodyMedium)
                    if (snippet.apps.isNotEmpty()) Text(stringResource(R.string.agent_snippets_apps_count, snippet.apps.size),
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { editing = snippet }) { Text(stringResource(R.string.agent_snippets_edit)) }
                TextButton(onClick = { deleting = snippet }) { Text(stringResource(R.string.agent_snippets_delete)) }
            }
        }
        if (current.size < SnippetStore.MAX_SNIPPETS) Button(onClick = { editing = Snippet("", "") }) {
            Text(stringResource(R.string.agent_snippets_add))
        } else Text(stringResource(R.string.agent_snippets_full, SnippetStore.MAX_SNIPPETS))
    }

    editing?.let { original ->
        SnippetEditor(original, snippets.orEmpty(), store, onSaved = { snippets = load(); editing = null }, onDismiss = { editing = null })
    }

    deleting?.let { snippet ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.agent_snippets_delete_confirm, snippet.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    runCatching { store.delete(snippet.name) }
                    snippets = load()
                }) { Text(stringResource(R.string.agent_snippets_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.agent_snippets_cancel)) } },
        )
    }
}

/** The placeholders the editor offers to insert at the cursor. */
private val PLACEHOLDERS = listOf("{datum}", "{datum+1t}", "{uhrzeit}", "{wochentag}", "{zwischenablage}", "{?Name}", "{cursor}")

/**
 * Stage 42: the editor of one snippet: name, text with a row of placeholders to insert at the cursor,
 * warnings for placeholders that stay as typed, a live preview and the apps it is limited to.
 */
@Composable
private fun SnippetEditor(original: Snippet, all: List<Snippet>, store: SnippetStore, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isNew = original.name.isEmpty()
    var name by remember(original) { mutableStateOf(original.name) }
    var text by remember(original) { mutableStateOf(TextFieldValue(original.text)) }
    var apps by remember(original) { mutableStateOf(original.apps) }
    var typedPackage by remember(original) { mutableStateOf("") }
    var choosingApp by remember(original) { mutableStateOf(false) }
    var failed by remember(original) { mutableStateOf(false) }
    var labels by remember(original) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val others = all.filterNot { !isNew && it.name.equals(original.name, ignoreCase = true) }
    val nameError = when {
        name.isNotEmpty() && !SnippetStore.isValidName(name) ->
            stringResource(R.string.agent_snippets_name_invalid, SnippetStore.MAX_NAME)
        others.any { it.name.equals(name, ignoreCase = true) } -> stringResource(R.string.agent_snippets_name_taken)
        else -> null
    }
    val textError = if (text.text.length > SnippetStore.MAX_TEXT) stringResource(R.string.agent_snippets_text_invalid, SnippetStore.MAX_TEXT) else null
    val valid = nameError == null && SnippetStore.isValidName(name) && SnippetStore.isValidText(text.text) && SnippetStore.isValidApps(apps)
    val template = remember(text.text) { SnippetTemplate.parse(text.text) }
    // like the keyboard: date and weekday follow the keyboard language (German by default)
    val language = remember { runCatching { Settings.getValues()?.mLocale?.language }.getOrNull() ?: Locale.getDefault().language }
    val clipboardLabel = stringResource(R.string.agent_snippets_preview_clipboard)
    val preview = remember(template, language, clipboardLabel) { SnipCommand.editorPreview(template, language, clipboardLabel) }

    fun insert(placeholder: String) {
        val selection = text.selection
        val updated = text.text.substring(0, selection.min) + placeholder + text.text.substring(selection.max)
        if (updated.length <= SnippetStore.MAX_TEXT) text = TextFieldValue(updated, TextRange(selection.min + placeholder.length))
    }

    LaunchedEffect(original) {
        // names for the stored packages, read off the main thread
        if (original.apps.isNotEmpty()) labels = launchableApps(context).toMap() + labels
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.agent_snippets_add else R.string.agent_snippets_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { if (it.length <= SnippetStore.MAX_NAME + 1) name = it.trim() },
                    label = { Text(stringResource(R.string.agent_snippets_name)) }, singleLine = true,
                    isError = nameError != null, supportingText = nameError?.let { { Text(it) } })
                OutlinedTextField(value = text, onValueChange = { if (it.text.length <= SnippetStore.MAX_TEXT + 1) text = it },
                    label = { Text(stringResource(R.string.agent_snippets_text, SnippetStore.MAX_TEXT)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 3, maxLines = 8,
                    isError = textError != null, supportingText = { Text(textError ?: "${text.text.length} / ${SnippetStore.MAX_TEXT}") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (placeholder in PLACEHOLDERS) OutlinedButton(onClick = { insert(placeholder) }) { Text(placeholder) }
                }
                Text(stringResource(R.string.agent_snippets_placeholders), style = MaterialTheme.typography.bodySmall)
                for (issue in template.issues) Text(when (issue.kind) {
                    SnippetTemplate.Issue.Kind.UNKNOWN -> stringResource(R.string.agent_snippets_issue_unknown, issue.text)
                    SnippetTemplate.Issue.Kind.UNCLOSED -> stringResource(R.string.agent_snippets_issue_unclosed, issue.text)
                    SnippetTemplate.Issue.Kind.EXTRA_CURSOR -> stringResource(R.string.agent_snippets_issue_cursor)
                }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (original.literal) Text(stringResource(R.string.agent_snippets_literal), style = MaterialTheme.typography.bodySmall)
                if (text.text.isNotBlank()) {
                    Text(stringResource(R.string.agent_snippets_preview), style = MaterialTheme.typography.titleSmall)
                    Text(preview, style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.agent_snippets_apps), style = MaterialTheme.typography.titleSmall)
                for (app in apps) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(labels[app]?.let { "$it ($app)" } ?: app, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { apps = apps - app }) { Text(stringResource(R.string.agent_snippets_apps_remove)) }
                }
                if (apps.size < SnippetApps.MAX_APPS) {
                    TextButton(onClick = { choosingApp = true }) { Text(stringResource(R.string.agent_snippets_apps_choose)) }
                    val packageError = typedPackage.isNotEmpty() && !SnippetApps.isValidPackage(typedPackage)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = typedPackage,
                            onValueChange = { if (it.length <= SnippetApps.MAX_PACKAGE + 1) typedPackage = it.trim() },
                            label = { Text(stringResource(R.string.agent_snippets_apps_package)) }, singleLine = true,
                            modifier = Modifier.weight(1f), isError = packageError,
                            supportingText = if (packageError) { { Text(stringResource(R.string.agent_snippets_apps_invalid)) } } else null)
                        TextButton(enabled = SnippetApps.isValidPackage(typedPackage), onClick = {
                            if (typedPackage !in apps) apps = apps + typedPackage
                            typedPackage = ""
                        }) { Text(stringResource(R.string.agent_snippets_apps_add)) }
                    }
                } else Text(stringResource(R.string.agent_snippets_apps_full, SnippetApps.MAX_APPS), style = MaterialTheme.typography.bodySmall)
                if (failed) Text(stringResource(R.string.agent_snippets_unavailable), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                // saved here, the text is a template from now on (no longer literal)
                val saved = runCatching { store.put(Snippet(name, text.text, apps), if (isNew) null else original.name) }.isSuccess
                if (saved) onSaved() else failed = true
            }) { Text(stringResource(R.string.agent_snippets_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.agent_snippets_cancel)) } },
    )

    if (choosingApp) AppChooser(
        onChosen = { pkg, label ->
            choosingApp = false
            labels = labels + (pkg to label)
            if (pkg !in apps && apps.size < SnippetApps.MAX_APPS && SnippetApps.isValidPackage(pkg)) apps = apps + pkg
        },
        onDismiss = { choosingApp = false },
    )
}

/** Installed apps with a launcher entry (package to label), by label; read off the main thread. */
private suspend fun launchableApps(context: Context): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    runCatching { AppsManager(context).getPackagesWithNameAndIcon().map { it.first to it.second } }.getOrDefault(emptyList())
        .sortedBy { it.second.lowercase() }
}

@Composable
private fun AppChooser(onChosen: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = launchableApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agent_snippets_apps_choose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.agent_snippets_apps_search)) })
                val list = apps
                if (list == null) Text(stringResource(R.string.agent_snippets_apps_loading))
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list.filter { query.isBlank() || it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true) }) { (pkg, label) ->
                        Column(Modifier.fillMaxWidth().clickable { onChosen(pkg, label) }.padding(vertical = 8.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(pkg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.agent_snippets_cancel)) } },
    )
}
