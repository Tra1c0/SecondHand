// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.Prompt
import helium314.keyboard.agent.PromptError
import helium314.keyboard.agent.PromptStore
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ReorderDialog

const val PROMPTS_ADD_TAG = "agent_prompts_add"
const val PROMPTS_SORT_TAG = "agent_prompts_sort"
const val PROMPTS_ROW_TAG = "agent_prompts_row_"
const val PROMPTS_EDIT_TAG = "agent_prompts_edit_"
const val PROMPTS_DELETE_TAG = "agent_prompts_delete_"
const val PROMPTS_DELETE_CONFIRM_TAG = "agent_prompts_delete_confirm"
const val PROMPTS_COUNT_TAG = "agent_prompts_count"
const val PROMPTS_EMPTY_TAG = "agent_prompts_empty"
const val PROMPTS_UNAVAILABLE_TAG = "agent_prompts_unavailable"
const val PROMPTS_NOTICE_TAG = "agent_prompts_notice"
const val PROMPTS_TITLE_FIELD_TAG = "agent_prompts_title_field"
const val PROMPTS_TEXT_FIELD_TAG = "agent_prompts_text_field"
const val PROMPTS_PASTE_TAG = "agent_prompts_paste"
const val PROMPTS_SAVE_TAG = "agent_prompts_save"

/**
 * Stage 74a: the page "Prompt-Ablage": prompts (texts for AI chats) kept until deleted, in the user's
 * own order. The list is read from and written to [PromptStore] only (local, credential-protected, no
 * backup); nothing is logged, and the clipboard is read only when "Paste from clipboard" is tapped.
 * The keyboard-side view is a later stage.
 */
@Composable
fun AgentPromptsScreen(onClickBack: () -> Unit, storeFactory: ((Context) -> PromptStore)? = null) {
    val context = LocalContext.current
    val store = remember { storeFactory?.invoke(context) ?: PromptStore(context) }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_prompts_title), emptyList()) {
        PromptsContent(store)
    }
}

@Composable
private fun PromptsContent(store: PromptStore) {
    // null: not readable now (locked device or unusable file)
    var prompts by remember { mutableStateOf(store.load()) }
    // null: closed; a prompt with an empty id: a new one
    var editing by remember { mutableStateOf<Prompt?>(null) }
    var deleting by remember { mutableStateOf<Prompt?>(null) }
    var sorting by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    fun reload() { prompts = store.load() }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.agent_prompts_description), style = MaterialTheme.typography.bodySmall)
        val current = prompts
        if (current == null) {
            Text(stringResource(R.string.agent_prompts_unavailable), Modifier.testTag(PROMPTS_UNAVAILABLE_TAG))
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = current.size < PromptStore.MAX_PROMPTS, modifier = Modifier.testTag(PROMPTS_ADD_TAG),
                onClick = { notice = 0; editing = Prompt("", "", "") }) { Text(stringResource(R.string.agent_prompts_new)) }
            OutlinedButton(enabled = current.size > 1, modifier = Modifier.testTag(PROMPTS_SORT_TAG),
                onClick = { notice = 0; sorting = true }) { Text(stringResource(R.string.agent_prompts_sort)) }
        }
        if (current.size >= PromptStore.MAX_PROMPTS)
            Text(stringResource(R.string.agent_prompts_full, PromptStore.MAX_PROMPTS), style = MaterialTheme.typography.bodySmall)
        if (notice != 0) Text(stringResource(notice), Modifier.testTag(PROMPTS_NOTICE_TAG), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.agent_prompts_count, current.size, PromptStore.MAX_PROMPTS), Modifier.testTag(PROMPTS_COUNT_TAG),
            style = MaterialTheme.typography.bodySmall)
        if (current.isEmpty()) Text(stringResource(R.string.agent_prompts_none), Modifier.testTag(PROMPTS_EMPTY_TAG))
        for (prompt in current) {
            Row(Modifier.fillMaxWidth().testTag(PROMPTS_ROW_TAG + prompt.id), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rowLabel(prompt), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(prompt.text.trim(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(modifier = Modifier.testTag(PROMPTS_EDIT_TAG + prompt.id),
                    onClick = { notice = 0; editing = prompt }) { Text(stringResource(R.string.agent_prompts_edit)) }
                TextButton(modifier = Modifier.testTag(PROMPTS_DELETE_TAG + prompt.id),
                    onClick = { notice = 0; deleting = prompt }) { Text(stringResource(R.string.agent_prompts_delete)) }
            }
        }
    }

    editing?.let { original ->
        PromptEditor(original, store, onSaved = { reload(); editing = null }, onGone = { reload(); notice = R.string.agent_prompts_gone; editing = null },
            onDismiss = { editing = null })
    }

    deleting?.let { prompt ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.agent_prompts_delete_confirm)) },
            confirmButton = {
                TextButton(modifier = Modifier.testTag(PROMPTS_DELETE_CONFIRM_TAG), onClick = {
                    deleting = null
                    val result = store.delete(prompt.id)
                    if (result.error == PromptError.NOT_FOUND) notice = R.string.agent_prompts_gone
                    reload()
                }) { Text(stringResource(R.string.agent_prompts_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.agent_prompts_cancel)) } },
        )
    }

    if (sorting) prompts?.let { shown ->
        ReorderDialog(
            onDismissRequest = { sorting = false },
            onConfirmed = { reordered ->
                sorting = false
                val result = store.reorder(reordered.map { it.id })
                // the list changed between showing the dialog and confirming: nothing was saved
                if (!result.ok) notice = if (result.error == PromptError.INVALID_ORDER) R.string.agent_prompts_sort_changed else R.string.agent_prompts_sort_failed
                reload()
            },
            items = shown,
            getKey = { it.id },
            title = { Text(stringResource(R.string.agent_prompts_sort_title)) },
            displayItem = { item -> Text(rowLabel(item), Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

/** The title of a prompt, or the first non-blank line of its text when it has none. */
private fun rowLabel(prompt: Prompt): String =
    prompt.title.ifEmpty { prompt.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty() }

/** Editor of one prompt (a new one when [original] has no id). Saves only on "Save". */
@Composable
private fun PromptEditor(original: Prompt, store: PromptStore, onSaved: () -> Unit, onGone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isNew = original.id.isEmpty()
    var title by remember(original) { mutableStateOf(original.title) }
    var text by remember(original) { mutableStateOf(TextFieldValue(original.text)) }
    var message by remember(original) { mutableStateOf("") }
    val error = PromptStore.validationError(title, text.text)
    val titleTooLong = error == PromptError.TITLE_TOO_LONG
    val textTooLong = error == PromptError.TEXT_TOO_LONG

    fun paste() {
        // the clipboard is read here, on an explicit tap, and nowhere else
        val clip = runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        if (clip.isNullOrEmpty()) { message = context.getString(R.string.agent_prompts_paste_empty); return }
        val selection = text.selection
        val updated = text.text.substring(0, selection.min) + clip + text.text.substring(selection.max)
        if (clip.length > PromptStore.MAX_TEXT || updated.length > PromptStore.MAX_TEXT) {
            message = context.getString(R.string.agent_prompts_paste_too_long, PromptStore.MAX_TEXT)
            return
        }
        text = TextFieldValue(updated, TextRange(selection.min + clip.length))
        message = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.agent_prompts_editor_new else R.string.agent_prompts_editor_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { if (it.length <= PromptStore.MAX_TITLE + 1) { title = it; message = "" } },
                    modifier = Modifier.fillMaxWidth().testTag(PROMPTS_TITLE_FIELD_TAG),
                    label = { Text(stringResource(R.string.agent_prompts_field_title)) }, singleLine = true, isError = titleTooLong,
                    supportingText = { Text(if (titleTooLong) stringResource(R.string.agent_prompts_title_too_long, PromptStore.MAX_TITLE)
                        else "${PromptStore.cleanTitle(title).length} / ${PromptStore.MAX_TITLE}") })
                OutlinedTextField(value = text, onValueChange = { if (it.text.length <= PromptStore.MAX_TEXT + 1) { text = it; message = "" } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag(PROMPTS_TEXT_FIELD_TAG),
                    label = { Text(stringResource(R.string.agent_prompts_field_text)) }, minLines = 5, maxLines = 12, isError = textTooLong,
                    supportingText = { Text(if (textTooLong) stringResource(R.string.agent_prompts_text_too_long, PromptStore.MAX_TEXT)
                        else "${text.text.length} / ${PromptStore.MAX_TEXT}") })
                OutlinedButton(modifier = Modifier.testTag(PROMPTS_PASTE_TAG), onClick = ::paste) {
                    Text(stringResource(R.string.agent_prompts_paste))
                }
                if (message.isNotEmpty()) Text(message, Modifier.testTag(PROMPTS_NOTICE_TAG), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, modifier = Modifier.testTag(PROMPTS_SAVE_TAG), onClick = {
                val result = if (isNew) store.add(title, text.text) else store.update(original.id, title, text.text)
                when {
                    result.ok -> onSaved()
                    result.error == PromptError.NOT_FOUND -> onGone()
                    result.error == PromptError.FULL -> message = context.getString(R.string.agent_prompts_full, PromptStore.MAX_PROMPTS)
                    else -> message = context.getString(R.string.agent_prompts_save_failed)
                }
            }) { Text(stringResource(R.string.agent_prompts_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.agent_prompts_cancel)) } },
    )
}
