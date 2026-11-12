// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.commands.SnipCommand
import helium314.keyboard.agent.commands.Snippet
import helium314.keyboard.agent.commands.SnippetStore
import helium314.keyboard.latin.R

/**
 * Stage 24f: the snippets of `@snip` on the command settings page: list, add, edit, delete.
 * Saving happens only on "Save"; nothing is logged.
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
        val isNew = original.name.isEmpty()
        var name by remember(original) { mutableStateOf(original.name) }
        var text by remember(original) { mutableStateOf(original.text) }
        var failed by remember(original) { mutableStateOf(false) }
        val others = snippets.orEmpty().filterNot { !isNew && it.name.equals(original.name, ignoreCase = true) }
        val nameError = when {
            name.isNotEmpty() && !SnippetStore.isValidName(name) ->
                stringResource(R.string.agent_snippets_name_invalid, SnippetStore.MAX_NAME)
            others.any { it.name.equals(name, ignoreCase = true) } -> stringResource(R.string.agent_snippets_name_taken)
            else -> null
        }
        val textError = if (text.length > SnippetStore.MAX_TEXT) stringResource(R.string.agent_snippets_text_invalid, SnippetStore.MAX_TEXT) else null
        val valid = nameError == null && SnippetStore.isValidName(name) && SnippetStore.isValidText(text)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (isNew) R.string.agent_snippets_add else R.string.agent_snippets_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { if (it.length <= SnippetStore.MAX_NAME + 1) name = it.trim() },
                        label = { Text(stringResource(R.string.agent_snippets_name)) }, singleLine = true,
                        isError = nameError != null, supportingText = nameError?.let { { Text(it) } })
                    OutlinedTextField(value = text, onValueChange = { if (it.length <= SnippetStore.MAX_TEXT + 1) text = it },
                        label = { Text(stringResource(R.string.agent_snippets_text, SnippetStore.MAX_TEXT)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 3, maxLines = 8,
                        isError = textError != null, supportingText = { Text(textError ?: "${text.length} / ${SnippetStore.MAX_TEXT}") })
                    if (failed) Text(stringResource(R.string.agent_snippets_unavailable), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    val saved = runCatching { store.put(Snippet(name, text), if (isNew) null else original.name) }.isSuccess
                    if (saved) { snippets = load(); editing = null } else failed = true
                }) { Text(stringResource(R.string.agent_snippets_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.agent_snippets_cancel)) } },
        )
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
