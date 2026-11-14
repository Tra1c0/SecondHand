// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentkeyboard.core.format.IbanCheck
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.FieldSuggestionChips
import helium314.keyboard.agent.MyData
import helium314.keyboard.agent.MyDataStore
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MY_DATA_SWITCH_TAG = "agent_my_data_switch"
const val MY_DATA_FIELD_TAG = "agent_my_data_field_"
const val MY_DATA_SAVE_TAG = "agent_my_data_save"
const val MY_DATA_DELETE_TAG = "agent_my_data_delete"
const val MY_DATA_LOCKED_TAG = "agent_my_data_locked"

/**
 * Stage 45: the page "Meine Daten & Vorschläge". The switch for field and context suggestions (in
 * the keyboard's normal preferences, no data) and the optional "Meine Daten" card: name, two e-mail
 * addresses, two phone numbers, two addresses, two IBANs, empty at first. The data is read and
 * written only through [MyDataStore] (encrypted, CE, no backup) and only while the device is
 * unlocked (P134/P145: [MyDataStore] is created only inside the unlocked content); the window is
 * secure while the page shows, and nothing is kept in saved state.
 */
@Composable
fun AgentMyDataScreen(onClickBack: () -> Unit, storeFactory: ((Context) -> MyDataStore)? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var accessible by remember { mutableStateOf(credentialAccessAllowed(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessible = credentialAccessAllowed(context)
            // the typed data leaves memory with the unlocked content when the page goes away
            if (event == Lifecycle.Event.ON_STOP) accessible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val prefs = context.prefs()
    var enabled by remember { mutableStateOf(FieldSuggestionChips.isEnabled(context)) }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_my_data_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.agent_my_data_switch), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, modifier = Modifier.testTag(MY_DATA_SWITCH_TAG), onCheckedChange = { value ->
                    prefs.edit { putBoolean(AgentPreferences.FIELD_SUGGESTIONS, value) }
                    enabled = value
                })
            }
            Text(stringResource(R.string.agent_my_data_switch_sub), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.agent_my_data_learning), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.agent_my_data_card), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.agent_my_data_description), style = MaterialTheme.typography.bodySmall)
            if (accessible) MyDataCard(storeFactory)
            else Text(stringResource(R.string.agent_my_data_unlock), Modifier.testTag(MY_DATA_LOCKED_TAG))
        }
    }
}

/** The entries of "Meine Daten"; created only while unlocked. */
@Composable
private fun MyDataCard(storeFactory: ((Context) -> MyDataStore)?) {
    val context = LocalContext.current
    val store = remember { storeFactory?.invoke(context) ?: MyDataStore(context) }
    val scope = rememberCoroutineScope()
    // deliberately not rememberSaveable: never in saved instance state
    val values = remember { mutableStateListOf(*Array(SLOTS.size) { "" }) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var notice by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val window = context.activity()?.window
    DisposableEffect(window) {
        SecureWindow.acquire(window)
        onDispose {
            for (i in values.indices) values[i] = ""
            SecureWindow.release(window)
        }
    }
    LaunchedEffect(store) {
        runCatching { withContext(Dispatchers.IO) { store.read() } }
            .onSuccess { data -> toSlots(data).forEachIndexed { i, v -> values[i] = v }; loaded = true }
            .onFailure { notice = R.string.agent_my_data_failed }
        busy = false
    }
    SLOTS.forEachIndexed { index, slot ->
        OutlinedTextField(
            value = values[index],
            onValueChange = { if (it.length <= MyData.MAX_LENGTH) { values[index] = it; notice = 0 } },
            label = { Text(if (slot.number == 0) stringResource(slot.label) else stringResource(slot.label, slot.number)) },
            singleLine = true,
            enabled = !busy && loaded,
            // the keyboard never learns what is typed here (review of stage 45, B5)
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = slot.keyboard,
                platformImeOptions = PlatformImeOptions(AgentPreferences.NO_LEARNING_FIELD)),
            modifier = Modifier.fillMaxWidth().testTag(MY_DATA_FIELD_TAG + index),
        )
    }
    val invalidIban = SLOTS.indices.firstOrNull { SLOTS[it].label == R.string.agent_my_data_iban && values[it].isNotBlank() &&
        IbanCheck.normalize(values[it].trim()) == null }
    if (invalidIban != null) Text(stringResource(R.string.agent_my_data_iban_invalid, SLOTS[invalidIban].number),
        style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(enabled = !busy && loaded, modifier = Modifier.testTag(MY_DATA_SAVE_TAG), onClick = {
            busy = true
            val data = fromSlots(values.toList())
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { store.save(data) } }
                    .onSuccess { notice = R.string.agent_my_data_saved }.onFailure { notice = R.string.agent_my_data_failed }
                busy = false
            }
        }) { Text(stringResource(R.string.agent_my_data_save)) }
        OutlinedButton(enabled = !busy && loaded, modifier = Modifier.testTag(MY_DATA_DELETE_TAG), onClick = { confirmDelete = true }) {
            Text(stringResource(R.string.agent_my_data_delete))
        }
    }
    if (notice != 0) Text(stringResource(notice))
    if (confirmDelete) ConfirmationDialog(
        onDismissRequest = { confirmDelete = false },
        onConfirmed = {
            confirmDelete = false
            busy = true
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { store.delete() } }
                    .onSuccess { for (i in values.indices) values[i] = ""; notice = R.string.agent_my_data_deleted }
                    .onFailure { notice = R.string.agent_my_data_failed }
                busy = false
            }
        },
        content = { Text(stringResource(R.string.agent_my_data_delete_confirm)) },
        confirmButtonText = stringResource(R.string.agent_my_data_delete),
    )
}

private class Slot(val label: Int, val number: Int, val keyboard: KeyboardType)

/** The fields of the card in order: name, two of each list. */
private val SLOTS = listOf(
    Slot(R.string.agent_my_data_name, 0, KeyboardType.Text),
    Slot(R.string.agent_my_data_email, 1, KeyboardType.Email), Slot(R.string.agent_my_data_email, 2, KeyboardType.Email),
    Slot(R.string.agent_my_data_phone, 1, KeyboardType.Phone), Slot(R.string.agent_my_data_phone, 2, KeyboardType.Phone),
    Slot(R.string.agent_my_data_address, 1, KeyboardType.Text), Slot(R.string.agent_my_data_address, 2, KeyboardType.Text),
    Slot(R.string.agent_my_data_iban, 1, KeyboardType.Ascii), Slot(R.string.agent_my_data_iban, 2, KeyboardType.Ascii),
)

private fun toSlots(data: MyData): List<String> {
    fun two(list: List<String>) = listOf(list.getOrElse(0) { "" }, list.getOrElse(1) { "" })
    return listOf(data.name) + two(data.emails) + two(data.phones) + two(data.addresses) + two(data.ibans)
}

private fun fromSlots(values: List<String>): MyData =
    MyData(values[0], values.subList(1, 3), values.subList(3, 5), values.subList(5, 7), values.subList(7, 9)).cleaned()

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
