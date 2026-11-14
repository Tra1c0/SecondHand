// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.agentkeyboard.core.memory.ShortcutBook
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.Memory
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlin.math.roundToInt

const val MEMORY_SWITCH_TAG = "agent_memory_switch"
const val MEMORY_SNIPPETS_TAG = "agent_memory_snippets"
const val MEMORY_PHRASE_TAG = "agent_memory_phrase_"
const val MEMORY_PIN_TAG = "agent_memory_pin_"
const val MEMORY_DELETE_TAG = "agent_memory_delete_"
const val MEMORY_OWN_TAG = "agent_memory_own_"
const val MEMORY_OFFER_TAG = "agent_memory_offer_"
const val MEMORY_APP_TAG = "agent_memory_app_"
const val MEMORY_CLEAR_TAG = "agent_memory_clear"

/** Phrases shown on the page at most (strongest first); the rest stay in the memory. */
const val MEMORY_MAX_SHOWN = 100

/**
 * Stage 46: the page "Gedächtnis", opened from "Tippen & Korrektur" → Wörter & Lernen. Until stage
 * 49's page "Was ich mir gemerkt habe" exists on the same branch, the memory has this own page; its
 * sections are [MemorySections], so that page can show them as they are.
 */
@Composable
fun AgentMemoryScreen(onClickBack: () -> Unit) {
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_memory_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MemorySections()
        }
    }
}

/**
 * The memory's sections: the two switches, phrases (pin, delete), own words, abbreviation offers,
 * excluded apps and "Alles vergessen". Reads the memory directly (device-protected, small); every
 * change is written at once.
 */
@Composable
fun ColumnScope.MemorySections() {
    val context = LocalContext.current
    val prefs = context.prefs()
    var version by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(Memory.isEnabled(context)) }
    var snippets by remember { mutableStateOf(Memory.snippetsEnabled(context)) }
    var choosingApp by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    if (Memory.deviceLocked(context)) {
        Text(stringResource(R.string.agent_memory_locked))
        return
    }
    fun bump() { version++ }

    SwitchRow(R.string.agent_memory_switch, R.string.agent_memory_switch_sub, enabled, MEMORY_SWITCH_TAG) {
        prefs.edit { putBoolean(AgentPreferences.MEMORY, it) }
        enabled = it
    }
    SwitchRow(R.string.agent_memory_snippets, R.string.agent_memory_snippets_sub, snippets, MEMORY_SNIPPETS_TAG) {
        prefs.edit { putBoolean(AgentPreferences.MEMORY_SNIPPETS, it) }
        snippets = it
    }
    HorizontalDivider()

    // read again after every change on this page
    val phrases = remember(version) { Memory.phrases(context).take(MEMORY_MAX_SHOWN) }
    val own = remember(version) { Memory.ownWords(context) }
    val offers = remember(version) { Memory.offers(context) }
    val apps = remember(version) { Memory.excludedApps(context).sorted() }

    Text(stringResource(R.string.agent_memory_phrases), style = MaterialTheme.typography.titleMedium)
    if (phrases.isEmpty()) Text(stringResource(R.string.agent_memory_phrases_none), style = MaterialTheme.typography.bodySmall)
    phrases.forEachIndexed { i, phrase ->
        Row(Modifier.fillMaxWidth().testTag(MEMORY_PHRASE_TAG + i), verticalAlignment = Alignment.CenterVertically) {
            Text(if (phrase.pinned) stringResource(R.string.agent_memory_phrase_pinned, phrase.text)
                else stringResource(R.string.agent_memory_phrase_weight, phrase.text, phrase.weight.roundToInt().coerceAtLeast(1)),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { Memory.pinPhrase(context, phrase.key, !phrase.pinned); bump() }, Modifier.testTag(MEMORY_PIN_TAG + i)) {
                Text(stringResource(if (phrase.pinned) R.string.agent_memory_unpin else R.string.agent_memory_pin))
            }
            TextButton(onClick = { Memory.deletePhrase(context, phrase.key); bump() }, Modifier.testTag(MEMORY_DELETE_TAG + i)) {
                Text(stringResource(R.string.agent_memory_delete))
            }
        }
    }
    HorizontalDivider()

    Text(stringResource(R.string.agent_memory_own_words), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.agent_memory_own_words_sub), style = MaterialTheme.typography.bodySmall)
    if (own.isEmpty()) Text(stringResource(R.string.agent_memory_own_words_none), style = MaterialTheme.typography.bodySmall)
    own.forEachIndexed { i, word ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(word, Modifier.weight(1f))
            TextButton(onClick = { Memory.removeOwnWord(context, word); bump() }, Modifier.testTag(MEMORY_OWN_TAG + i)) {
                Text(stringResource(R.string.agent_memory_delete))
            }
        }
    }
    HorizontalDivider()

    Text(stringResource(R.string.agent_memory_offers), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.agent_memory_offers_sub), style = MaterialTheme.typography.bodySmall)
    if (offers.isEmpty()) Text(stringResource(R.string.agent_memory_offers_none), style = MaterialTheme.typography.bodySmall)
    offers.forEachIndexed { i, offer ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.agent_memory_offer_row, offer.short, offer.expansion), Modifier.weight(1f))
            if (offer.state == ShortcutBook.State.CREATED) Text(stringResource(R.string.agent_memory_offer_created_state),
                style = MaterialTheme.typography.bodySmall)
            else TextButton(onClick = { Memory.acceptOffer(context, offer); bump() }, Modifier.testTag(MEMORY_OFFER_TAG + i)) {
                Text(stringResource(R.string.agent_memory_offer_create))
            }
            TextButton(onClick = { Memory.removeOffer(context, offer); bump() }) { Text(stringResource(R.string.agent_memory_offer_discard)) }
        }
    }
    HorizontalDivider()

    Text(stringResource(R.string.agent_memory_apps), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.agent_memory_apps_sub), style = MaterialTheme.typography.bodySmall)
    if (apps.isEmpty()) Text(stringResource(R.string.agent_memory_apps_none), style = MaterialTheme.typography.bodySmall)
    apps.forEachIndexed { i, app ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(app, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { Memory.setExcluded(context, app, false); bump() }, Modifier.testTag(MEMORY_APP_TAG + i)) {
                Text(stringResource(R.string.agent_memory_apps_remove))
            }
        }
    }
    if (apps.size < Memory.MAX_EXCLUDED) TextButton(onClick = { choosingApp = true }) { Text(stringResource(R.string.agent_memory_apps_add)) }
    HorizontalDivider()

    OutlinedButton(onClick = { confirmClear = true }, Modifier.testTag(MEMORY_CLEAR_TAG)) { Text(stringResource(R.string.agent_memory_clear)) }

    if (choosingApp) AppChooser(onChosen = { pkg, _ -> Memory.setExcluded(context, pkg, true); choosingApp = false; bump() },
        onDismiss = { choosingApp = false })
    if (confirmClear) ConfirmationDialog(onDismissRequest = { confirmClear = false },
        onConfirmed = { Memory.clear(context); bump() },
        content = { Text(stringResource(R.string.agent_memory_clear_confirm)) })
}

@Composable
private fun SwitchRow(title: Int, summary: Int, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, modifier = Modifier.testTag(tag), onCheckedChange = onChange)
    }
    Text(stringResource(summary), style = MaterialTheme.typography.bodySmall)
}
