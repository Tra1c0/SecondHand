// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.Prompt
import helium314.keyboard.agent.PromptError
import helium314.keyboard.agent.PromptFilter
import helium314.keyboard.agent.PromptFolder
import helium314.keyboard.agent.PromptLibrary
import helium314.keyboard.agent.PromptStore
import helium314.keyboard.agent.PromptStoreState
import helium314.keyboard.agent.promptLabel
import helium314.keyboard.agent.promptPreview
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
const val PROMPTS_CANCEL_TAG = "agent_prompts_cancel"
const val PROMPTS_DISCARD_TAG = "agent_prompts_discard"
const val PROMPTS_KEEP_EDITING_TAG = "agent_prompts_keep_editing"
const val PROMPTS_RESET_TAG = "agent_prompts_reset"
const val PROMPTS_RESET_CONFIRM_TAG = "agent_prompts_reset_confirm"
// stage 77: folders
const val PROMPTS_FILTER_ALL_TAG = "agent_prompts_filter_all"
const val PROMPTS_FILTER_NONE_TAG = "agent_prompts_filter_none"
const val PROMPTS_FILTER_FOLDER_TAG = "agent_prompts_filter_folder_"
const val PROMPTS_FOLDER_NEW_TAG = "agent_prompts_folder_new"
const val PROMPTS_FOLDER_RENAME_TAG = "agent_prompts_folder_rename"
const val PROMPTS_FOLDER_DELETE_TAG = "agent_prompts_folder_delete"
const val PROMPTS_FOLDER_DELETE_CONFIRM_TAG = "agent_prompts_folder_delete_confirm"
const val PROMPTS_FOLDER_SORT_TAG = "agent_prompts_folder_sort"
const val PROMPTS_FOLDER_COUNT_TAG = "agent_prompts_folder_count"
const val PROMPTS_FOLDER_NAME_FIELD_TAG = "agent_prompts_folder_name_field"
const val PROMPTS_FOLDER_SAVE_TAG = "agent_prompts_folder_save"
const val PROMPTS_FOLDER_CANCEL_TAG = "agent_prompts_folder_cancel"
const val PROMPTS_FOLDER_ERROR_TAG = "agent_prompts_folder_error"
const val PROMPTS_ROW_FOLDER_TAG = "agent_prompts_row_folder_"
const val PROMPTS_PICK_NONE_TAG = "agent_prompts_pick_none"
const val PROMPTS_PICK_FOLDER_TAG = "agent_prompts_pick_folder_"

/**
 * Stage 74a: the page "Prompt-Ablage": prompts (texts for AI chats) kept until deleted, in the user's
 * own order. The list is read from and written to [PromptStore] only (local, credential-protected, no
 * backup); nothing is logged, and the clipboard is read only when "Paste from clipboard" is tapped.
 * The editor is a card on the page, not a dialog (P78: text fields in dialogs never get idle in
 * Robolectric). The keyboard-side view is a later stage.
 *
 * Stage 77: folders (one level). A row of chips filters the list ("All", each folder, "No folder" when folders exist and some
 * prompt has none); "New folder", "Rename folder", "Delete folder" (the prompts of a deleted folder are kept, in "No folder")
 * and "Sort folders" manage them; the prompt editor has a folder picker, and a new prompt starts in the folder shown. The
 * folder editor is a card on the page like the prompt editor (P78).
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
    // null: not readable now (locked device or unusable file, see [state])
    var library by remember { mutableStateOf(store.loadLibrary()) }
    var state by remember { mutableStateOf(store.state()) }
    // null: closed; "": a new prompt; else the id of the prompt being edited. Saved with the activity state (rotation, dark mode).
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    // null: closed; "": a new folder; else the id of the folder being renamed
    var editingFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    // which prompts the list shows, see [PromptFilter.encode]
    var filterCode by rememberSaveable { mutableStateOf(PromptFilter.All.encode()) }
    var deleting by remember { mutableStateOf<Prompt?>(null) }
    var deletingFolder by remember { mutableStateOf<PromptFolder?>(null) }
    var sorting by remember { mutableStateOf(false) }
    var sortingFolders by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    fun reload() { library = store.loadLibrary(); state = store.state() }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.agent_prompts_description), style = MaterialTheme.typography.bodySmall)
        val content = library
        if (content == null) {
            Text(stringResource(R.string.agent_prompts_unavailable), Modifier.testTag(PROMPTS_UNAVAILABLE_TAG))
            // an unreadable file (not a locked device) is a dead end without this
            if (state == PromptStoreState.UNUSABLE) OutlinedButton(modifier = Modifier.testTag(PROMPTS_RESET_TAG),
                onClick = { notice = 0; resetting = true }) { Text(stringResource(R.string.agent_prompts_reset)) }
            if (notice != 0) Text(stringResource(notice), Modifier.testTag(PROMPTS_NOTICE_TAG), color = MaterialTheme.colorScheme.error)
            return@Column
        }
        // a folder deleted meanwhile, or "no folder" with nothing in it: back to all
        val filter = content.effective(PromptFilter.decode(filterCode))
        val current = content.prompts
        val shown = content.promptsIn(filter)
        val openId = editingId
        if (openId != null) {
            // a prompt deleted meanwhile (restored state, another page of the store): the editor closes with a notice
            val open = if (openId.isEmpty()) Prompt("", "", "") else current.firstOrNull { it.id == openId }
            if (open == null) { editingId = null; notice = R.string.agent_prompts_gone }
            else {
                // the editor is a card on the page; the list and its buttons are not shown meanwhile
                PromptEditorCard(open, store, content.folders, filter.folderIdForNew, onReload = ::reload,
                    onSaved = { reload(); editingId = null },
                    onGone = { reload(); notice = R.string.agent_prompts_gone; editingId = null }, onDismiss = { editingId = null })
                return@Column
            }
        }
        val folderOpenId = editingFolderId
        if (folderOpenId != null) {
            val open = if (folderOpenId.isEmpty()) PromptFolder("", "") else content.folders.firstOrNull { it.id == folderOpenId }
            if (open == null) { editingFolderId = null; notice = R.string.agent_prompts_folder_gone }
            else {
                FolderEditorCard(open, store, content.folders,
                    onSaved = { saved ->
                        reload(); editingFolderId = null
                        // a new folder is shown at once, so the next prompt lands in it
                        if (open.id.isEmpty() && saved != null) filterCode = PromptFilter.Folder(saved.id).encode()
                    },
                    onGone = { reload(); notice = R.string.agent_prompts_folder_gone; editingFolderId = null },
                    onDismiss = { editingFolderId = null })
                return@Column
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = current.size < PromptStore.MAX_PROMPTS, modifier = Modifier.testTag(PROMPTS_ADD_TAG),
                onClick = { notice = 0; editingId = "" }) { Text(stringResource(R.string.agent_prompts_new)) }
            OutlinedButton(enabled = shown.size > 1, modifier = Modifier.testTag(PROMPTS_SORT_TAG),
                onClick = { notice = 0; sorting = true }) { Text(stringResource(R.string.agent_prompts_sort)) }
        }
        if (content.folders.isNotEmpty()) {
            // the filter chips: all, each folder, and "no folder" when some prompt has none
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val allText = stringResource(R.string.agent_prompts_chip_all)
                val allDescription = stringResource(R.string.agent_prompts_filter_all_description)
                FilterChip(filter == PromptFilter.All, { filterCode = PromptFilter.All.encode() }, { Text(allText) },
                    Modifier.testTag(PROMPTS_FILTER_ALL_TAG).semantics { contentDescription = allDescription })
                for (folder in content.folders) {
                    val description = stringResource(R.string.agent_prompts_filter_folder_description, folder.name)
                    FilterChip(filter == PromptFilter.Folder(folder.id), { filterCode = PromptFilter.Folder(folder.id).encode() },
                        { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        Modifier.testTag(PROMPTS_FILTER_FOLDER_TAG + folder.id).semantics { contentDescription = description })
                }
                if (content.offersNoFolder()) {
                    val noneText = stringResource(R.string.agent_prompts_chip_none)
                    val noneDescription = stringResource(R.string.agent_prompts_filter_none_description)
                    FilterChip(filter == PromptFilter.NoFolder, { filterCode = PromptFilter.NoFolder.encode() }, { Text(noneText) },
                        Modifier.testTag(PROMPTS_FILTER_NONE_TAG).semantics { contentDescription = noneDescription })
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = content.folders.size < PromptStore.MAX_FOLDERS, modifier = Modifier.testTag(PROMPTS_FOLDER_NEW_TAG),
                onClick = { notice = 0; editingFolderId = "" }) { Text(stringResource(R.string.agent_prompts_folder_new)) }
            if (filter is PromptFilter.Folder) {
                val folder = content.folders.first { it.id == filter.id }
                OutlinedButton(modifier = Modifier.testTag(PROMPTS_FOLDER_RENAME_TAG),
                    onClick = { notice = 0; editingFolderId = folder.id }) { Text(stringResource(R.string.agent_prompts_folder_rename)) }
                OutlinedButton(modifier = Modifier.testTag(PROMPTS_FOLDER_DELETE_TAG),
                    onClick = { notice = 0; deletingFolder = folder }) { Text(stringResource(R.string.agent_prompts_folder_delete)) }
            }
            if (content.folders.size > 1) OutlinedButton(modifier = Modifier.testTag(PROMPTS_FOLDER_SORT_TAG),
                onClick = { notice = 0; sortingFolders = true }) { Text(stringResource(R.string.agent_prompts_folder_sort)) }
        }
        if (current.size >= PromptStore.MAX_PROMPTS)
            Text(stringResource(R.string.agent_prompts_full, PromptStore.MAX_PROMPTS), style = MaterialTheme.typography.bodySmall)
        if (notice != 0) Text(stringResource(notice), Modifier.testTag(PROMPTS_NOTICE_TAG), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.agent_prompts_count, current.size, PromptStore.MAX_PROMPTS), Modifier.testTag(PROMPTS_COUNT_TAG),
            style = MaterialTheme.typography.bodySmall)
        if (content.folders.isNotEmpty()) Text(stringResource(R.string.agent_prompts_folder_count, content.folders.size, PromptStore.MAX_FOLDERS),
            Modifier.testTag(PROMPTS_FOLDER_COUNT_TAG), style = MaterialTheme.typography.bodySmall)
        if (shown.isEmpty()) Text(stringResource(if (filter is PromptFilter.Folder) R.string.agent_prompts_folder_none_in else R.string.agent_prompts_none),
            Modifier.testTag(PROMPTS_EMPTY_TAG))
        for (prompt in shown) {
            Row(Modifier.fillMaxWidth().testTag(PROMPTS_ROW_TAG + prompt.id), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rowLabel(prompt), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(rowPreview(prompt.text), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    // in the "All" list a prompt says which folder it is in
                    val folderName = if (filter == PromptFilter.All) content.folders.firstOrNull { it.id == prompt.folderId }?.name else null
                    if (folderName != null) Text(folderName, Modifier.testTag(PROMPTS_ROW_FOLDER_TAG + prompt.id), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(modifier = Modifier.testTag(PROMPTS_EDIT_TAG + prompt.id),
                    onClick = { notice = 0; editingId = prompt.id }) { Text(stringResource(R.string.agent_prompts_edit)) }
                TextButton(modifier = Modifier.testTag(PROMPTS_DELETE_TAG + prompt.id),
                    onClick = { notice = 0; deleting = prompt }) { Text(stringResource(R.string.agent_prompts_delete)) }
            }
        }
    }

    deleting?.let { prompt ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.agent_prompts_delete_confirm)) },
            confirmButton = {
                TextButton(modifier = Modifier.testTag(PROMPTS_DELETE_CONFIRM_TAG), onClick = {
                    deleting = null
                    val result = store.delete(prompt.id)
                    notice = when (result.error) {
                        null -> 0
                        PromptError.NOT_FOUND -> R.string.agent_prompts_gone
                        else -> R.string.agent_prompts_save_failed
                    }
                    reload()
                }) { Text(stringResource(R.string.agent_prompts_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.agent_prompts_cancel)) } },
        )
    }

    deletingFolder?.let { folder ->
        // the count is read now, from the store: the prompts of the folder are kept and move to "no folder"
        val count = library?.prompts?.count { it.folderId == folder.id } ?: 0
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            text = { Text(stringResource(R.string.agent_prompts_folder_delete_confirm, folder.name, count)) },
            confirmButton = {
                TextButton(modifier = Modifier.testTag(PROMPTS_FOLDER_DELETE_CONFIRM_TAG), onClick = {
                    deletingFolder = null
                    notice = when (store.deleteFolder(folder.id).error) {
                        null -> 0
                        PromptError.FOLDER_NOT_FOUND -> R.string.agent_prompts_folder_gone
                        else -> R.string.agent_prompts_folder_failed
                    }
                    filterCode = PromptFilter.All.encode()
                    reload()
                }) { Text(stringResource(R.string.agent_prompts_folder_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.agent_prompts_cancel)) } },
        )
    }

    if (resetting) AlertDialog(
        onDismissRequest = { resetting = false },
        text = { Text(stringResource(R.string.agent_prompts_reset_confirm)) },
        confirmButton = {
            TextButton(modifier = Modifier.testTag(PROMPTS_RESET_CONFIRM_TAG), onClick = {
                resetting = false
                // a readable file is left alone: the page only reloads
                notice = store.reset().error.let { if (it == null || it == PromptError.READABLE) 0 else R.string.agent_prompts_reset_failed }
                reload()
            }) { Text(stringResource(R.string.agent_prompts_reset)) }
        },
        dismissButton = { TextButton(onClick = { resetting = false }) { Text(stringResource(R.string.agent_prompts_cancel)) } },
    )

    if (sorting) library?.let { content ->
        // the prompts of the list shown: all of them, or those of one folder (or of "no folder"), whose places they keep among the others
        val filter = content.effective(PromptFilter.decode(filterCode))
        ReorderDialog(
            onDismissRequest = { sorting = false },
            onConfirmed = { reordered ->
                sorting = false
                notice = confirmPromptOrder(store, filter, reordered.map { it.id })
                reload()
            },
            items = content.promptsIn(filter),
            getKey = { it.id },
            title = { Text(stringResource(R.string.agent_prompts_sort_title)) },
            displayItem = { item -> Text(rowLabel(item), Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }

    if (sortingFolders) library?.let { content ->
        ReorderDialog(
            onDismissRequest = { sortingFolders = false },
            onConfirmed = { reordered ->
                sortingFolders = false
                notice = confirmFolderOrder(store, reordered.map { it.id })
                reload()
            },
            items = content.folders,
            getKey = { it.id },
            title = { Text(stringResource(R.string.agent_prompts_folder_sort_title)) },
            displayItem = { item -> Text(item.name, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

/**
 * Saves the order chosen in the sort dialog; returns the notice to show (0: none). If the list changed
 * between showing the dialog and confirming, nothing is saved and the notice says so.
 */
internal fun confirmPromptOrder(store: PromptStore, ids: List<String>): Int {
    val result = store.reorder(ids)
    return when {
        result.ok -> 0
        result.error == PromptError.INVALID_ORDER -> R.string.agent_prompts_sort_changed
        else -> R.string.agent_prompts_sort_failed
    }
}

/**
 * Stage 77: like [confirmPromptOrder] for the list shown under [filter]: with "All" the whole order, otherwise only the
 * prompts of that folder ("no folder" included) change among their own places.
 */
internal fun confirmPromptOrder(store: PromptStore, filter: PromptFilter, ids: List<String>): Int {
    if (filter == PromptFilter.All) return confirmPromptOrder(store, ids)
    val result = store.reorderInFolder(filter.folderIdForNew, ids)
    return when (result.error) {
        null -> 0
        PromptError.INVALID_ORDER, PromptError.FOLDER_NOT_FOUND -> R.string.agent_prompts_sort_changed
        else -> R.string.agent_prompts_sort_failed
    }
}

/** Stage 77: saves the folder order chosen in the sort dialog; the notice to show (0: none). */
internal fun confirmFolderOrder(store: PromptStore, ids: List<String>): Int {
    val result = store.reorderFolders(ids)
    return when {
        result.ok -> 0
        result.error == PromptError.INVALID_ORDER -> R.string.agent_prompts_sort_changed
        else -> R.string.agent_prompts_sort_failed
    }
}

/** The title of a prompt, or the first non-blank line of its text when it has none (shared with the keyboard window). */
private fun rowLabel(prompt: Prompt): String = promptLabel(prompt)

/** At most the first 300 characters of the text; see [promptPreview] (shared with the keyboard window). */
internal fun rowPreview(text: String): String = promptPreview(text)

/**
 * Editor of one prompt (a new one when [original] has no id), shown as a card on the page. Saves only on "Save". With
 * [folders] it has a folder picker; a new prompt starts in [defaultFolderId] (the folder the list shows).
 */
@Composable
private fun PromptEditorCard(original: Prompt, store: PromptStore, folders: List<PromptFolder>, defaultFolderId: String?, onReload: () -> Unit,
                             onSaved: () -> Unit, onGone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isNew = original.id.isEmpty()
    val startFolder = if (isNew) defaultFolderId else original.folderId
    // the folder chosen in the picker; one that does not exist any more counts as "no folder" (what the picker shows)
    var pickedFolder by rememberSaveable(original.id) { mutableStateOf(startFolder) }
    val folderId = pickedFolder?.takeIf { id -> folders.any { it.id == id } }
    // saved with the activity state, per prompt (the id, not the object read from the store this time)
    var title by rememberSaveable(original.id) { mutableStateOf(original.title) }
    // an existing text opens with the cursor at its end
    var text by rememberSaveable(original.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(original.text, TextRange(original.text.length)))
    }
    var message by remember(original) { mutableStateOf("") }
    var askDiscard by rememberSaveable(original.id) { mutableStateOf(false) }
    // back and "Cancel": unchanged closes at once, changed asks first (the typed text is not lost by a slip).
    // The state is read when the handler runs, not captured at composition: the back callback outlives recompositions.
    fun cancel() { if (title != original.title || text.text != original.text || pickedFolder != startFolder) askDiscard = true else onDismiss() }
    BackHandler { cancel() }
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (isNew) R.string.agent_prompts_editor_new else R.string.agent_prompts_editor_edit),
            style = MaterialTheme.typography.titleMedium)
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
        if (folders.isNotEmpty()) {
            Text(stringResource(R.string.agent_prompts_folder_picker_label), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val noneDescription = stringResource(R.string.agent_prompts_folder_picker_none_description)
                FilterChip(folderId == null, { pickedFolder = null }, { Text(stringResource(R.string.agent_prompts_folder_picker_none)) },
                    Modifier.testTag(PROMPTS_PICK_NONE_TAG).semantics { contentDescription = noneDescription })
                for (folder in folders) {
                    val description = stringResource(R.string.agent_prompts_folder_picker_description, folder.name)
                    FilterChip(folderId == folder.id, { pickedFolder = folder.id }, { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        Modifier.testTag(PROMPTS_PICK_FOLDER_TAG + folder.id).semantics { contentDescription = description })
                }
            }
        }
        if (error == PromptError.INVALID_TEXT) Text(stringResource(R.string.agent_prompts_invalid_text), color = MaterialTheme.colorScheme.error)
        if (message.isNotEmpty()) Text(message, Modifier.testTag(PROMPTS_NOTICE_TAG), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = error == null, modifier = Modifier.testTag(PROMPTS_SAVE_TAG), onClick = {
                val result = if (isNew) store.add(title, text.text, folderId) else store.update(original.id, title, text.text, folderId)
                when (result.error) {
                    null -> onSaved()
                    PromptError.NOT_FOUND -> onGone()
                    // the folder was deleted meanwhile: the picker shows the folders again, now without it
                    PromptError.FOLDER_NOT_FOUND -> { message = context.getString(R.string.agent_prompts_folder_gone); onReload() }
                    PromptError.FULL -> message = context.getString(R.string.agent_prompts_full, PromptStore.MAX_PROMPTS)
                    PromptError.FILE_FULL -> message = context.getString(R.string.agent_prompts_file_full)
                    PromptError.INVALID_TEXT -> message = context.getString(R.string.agent_prompts_invalid_text)
                    else -> message = context.getString(R.string.agent_prompts_save_failed)
                }
            }) { Text(stringResource(R.string.agent_prompts_save)) }
            OutlinedButton(modifier = Modifier.testTag(PROMPTS_CANCEL_TAG), onClick = ::cancel) {
                Text(stringResource(R.string.agent_prompts_cancel))
            }
        }
    }
    if (askDiscard) AlertDialog(
        onDismissRequest = { askDiscard = false },
        text = { Text(stringResource(R.string.agent_prompts_discard_confirm)) },
        confirmButton = {
            TextButton(modifier = Modifier.testTag(PROMPTS_DISCARD_TAG), onClick = { askDiscard = false; onDismiss() }) {
                Text(stringResource(R.string.agent_prompts_discard))
            }
        },
        dismissButton = {
            TextButton(modifier = Modifier.testTag(PROMPTS_KEEP_EDITING_TAG), onClick = { askDiscard = false }) {
                Text(stringResource(R.string.agent_prompts_keep_editing))
            }
        },
    )
}

/**
 * Stage 77: editor of one folder name (a new folder when [original] has no id), a card on the page like the prompt editor.
 * [onSaved] gets the stored folder. The name is checked as typed ([PromptStore.folderNameError]); the store checks it again.
 */
@Composable
private fun FolderEditorCard(original: PromptFolder, store: PromptStore, folders: List<PromptFolder>, onSaved: (PromptFolder?) -> Unit,
                             onGone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isNew = original.id.isEmpty()
    var name by rememberSaveable(original.id) { mutableStateOf(original.name) }
    var message by remember(original) { mutableStateOf("") }
    BackHandler { onDismiss() }
    val clean = PromptStore.cleanFolderName(name)
    val error = PromptStore.folderNameError(clean, folders, original.id.ifEmpty { null })
    // "empty" is not shown as an error before anything was typed; the Save button is off meanwhile
    val errorText = when (error) {
        PromptError.FOLDER_NAME_TOO_LONG -> stringResource(R.string.agent_prompts_folder_name_too_long, PromptStore.MAX_FOLDER_NAME)
        PromptError.FOLDER_NAME_INVALID -> stringResource(R.string.agent_prompts_folder_name_invalid)
        PromptError.FOLDER_NAME_TAKEN -> stringResource(R.string.agent_prompts_folder_name_taken)
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if (isNew) R.string.agent_prompts_folder_editor_new else R.string.agent_prompts_folder_editor_rename),
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { if (it.length <= PromptStore.MAX_FOLDER_NAME + 1) { name = it; message = "" } },
            modifier = Modifier.fillMaxWidth().testTag(PROMPTS_FOLDER_NAME_FIELD_TAG),
            label = { Text(stringResource(R.string.agent_prompts_folder_field_name)) }, singleLine = true, isError = errorText != null,
            supportingText = { Text(errorText ?: "${clean.length} / ${PromptStore.MAX_FOLDER_NAME}") })
        if (message.isNotEmpty()) Text(message, Modifier.testTag(PROMPTS_FOLDER_ERROR_TAG), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = error == null, modifier = Modifier.testTag(PROMPTS_FOLDER_SAVE_TAG), onClick = {
                val result = if (isNew) store.addFolder(name) else store.renameFolder(original.id, name)
                when (result.error) {
                    null -> onSaved(result.folder)
                    PromptError.FOLDER_NOT_FOUND -> onGone()
                    PromptError.FOLDER_NAME_TAKEN -> message = context.getString(R.string.agent_prompts_folder_name_taken)
                    PromptError.FOLDERS_FULL -> message = context.getString(R.string.agent_prompts_folders_full, PromptStore.MAX_FOLDERS)
                    PromptError.FILE_FULL -> message = context.getString(R.string.agent_prompts_file_full)
                    else -> message = context.getString(R.string.agent_prompts_folder_failed)
                }
            }) { Text(stringResource(R.string.agent_prompts_save)) }
            OutlinedButton(modifier = Modifier.testTag(PROMPTS_FOLDER_CANCEL_TAG), onClick = onDismiss) {
                Text(stringResource(R.string.agent_prompts_cancel))
            }
        }
    }
}
