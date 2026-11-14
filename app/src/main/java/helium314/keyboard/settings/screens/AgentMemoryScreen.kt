// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.FieldCorrectionMemory
import helium314.keyboard.agent.ForeignWords
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import dev.agentkeyboard.core.memory.ShortcutBook
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.Memory
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlin.math.roundToInt

/**
 * "Was ich mir gemerkt habe": what the keyboard keeps from typing, one section per kind, each with
 * its own entries and actions. Stage 49 has the corrections section, stage 46 the memory
 * ([MemorySections]: phrases, own words, abbreviation offers, excluded apps), stage 51 the words of
 * other languages ([ForeignWordsSection]). The lists are shown only here; the keyboard shows only its
 * chips (stage 46).
 */
@Composable
fun AgentMemoryScreen(onClickBack: () -> Unit) {
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_memory_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_memory_description))
            memorySections.forEachIndexed { i, section ->
                if (i > 0) HorizontalDivider()
                section()
            }
        }
    }
}

/** The sections of the page, top to bottom. */
private val memorySections: List<@Composable () -> Unit> = listOf({ CorrectionsSection() }, { MemorySections() }, { ForeignWordsSection() })

const val MEMORY_ENTRY_TAG = "agent_memory_entry_"
const val MEMORY_ADOPT_TAG = "agent_memory_adopt_"
const val MEMORY_DELETE_TAG = "agent_memory_delete_"

/** Stage 49: typo corrections made later in a field, candidates and learned ones. */
@Composable
private fun CorrectionsSection() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(runCatching { FieldCorrectionMemory.all(context) }.getOrDefault(emptyList())) }
    fun reload() { entries = runCatching { FieldCorrectionMemory.all(context) }.getOrDefault(emptyList()) }

    Text(stringResource(R.string.agent_memory_corrections), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.agent_memory_corrections_description), style = MaterialTheme.typography.bodySmall)
    val (learned, candidates) = entries.partition { it.learned }
    Text(stringResource(R.string.agent_memory_learned), style = MaterialTheme.typography.titleSmall)
    if (learned.isEmpty()) Text(stringResource(R.string.agent_memory_none), style = MaterialTheme.typography.bodySmall)
    learned.forEach { entry ->
        EntryRow(entry) {
            TextButton(onClick = { runCatching { FieldCorrectionMemory.delete(context, entry) }; reload() },
                Modifier.testTag(MEMORY_DELETE_TAG + entry.tag)) { Text(stringResource(R.string.agent_memory_delete)) }
        }
    }
    Text(stringResource(R.string.agent_memory_candidates), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.agent_memory_candidates_description), style = MaterialTheme.typography.bodySmall)
    if (candidates.isEmpty()) Text(stringResource(R.string.agent_memory_none), style = MaterialTheme.typography.bodySmall)
    candidates.forEach { entry ->
        EntryRow(entry) {
            TextButton(onClick = { runCatching { FieldCorrectionMemory.adopt(context, entry) }; reload() },
                Modifier.testTag(MEMORY_ADOPT_TAG + entry.tag)) { Text(stringResource(R.string.agent_memory_adopt)) }
            TextButton(onClick = { runCatching { FieldCorrectionMemory.delete(context, entry) }; reload() },
                Modifier.testTag(MEMORY_DELETE_TAG + entry.tag)) { Text(stringResource(R.string.agent_memory_delete)) }
        }
    }
}

const val MEMORY_FOREIGN_TAG = "agent_memory_foreign_"
const val MEMORY_FOREIGN_DELETE_TAG = "agent_memory_foreign_delete_"

/** Stage 51: words of other languages kept while writing in one language, each with "Löschen"; none while locked. */
@Composable
private fun ForeignWordsSection() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(runCatching { ForeignWords.all(context) }.getOrDefault(emptyList())) }
    Text(stringResource(R.string.agent_memory_foreign), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.agent_memory_foreign_description), style = MaterialTheme.typography.bodySmall)
    if (Memory.deviceLocked(context)) {
        Text(stringResource(R.string.agent_memory_locked))
        return
    }
    if (entries.isEmpty()) Text(stringResource(R.string.agent_memory_foreign_none), style = MaterialTheme.typography.bodySmall)
    entries.forEach { entry ->
        val tag = memoryEntryTag(entry.language, entry.word)
        Row(Modifier.fillMaxWidth().testTag(MEMORY_FOREIGN_TAG + tag), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.agent_memory_foreign_entry, entry.word, languageName(entry.from), languageName(entry.language)),
                Modifier.weight(1f))
            TextButton(onClick = {
                runCatching { ForeignWords.delete(context, entry) }
                entries = runCatching { ForeignWords.all(context) }.getOrDefault(emptyList())
            }, Modifier.testTag(MEMORY_FOREIGN_DELETE_TAG + tag)) { Text(stringResource(R.string.agent_memory_delete)) }
        }
    }
}

/** The name of [language] ("es") in the language of the page. */
@Composable
private fun languageName(language: String): String {
    val shown = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current)[0]
        ?: java.util.Locale.getDefault()
    return java.util.Locale.forLanguageTag(language).getDisplayLanguage(shown).ifEmpty { language }
}

/** Review of stage 51 (N10): the test tag part of an entry kept per keyboard [language] ("" before stage 51), e.g. "de_hsus". */
fun memoryEntryTag(language: String, word: String) = language + "_" + word

private val FieldCorrectionMemory.Entry.tag get() = memoryEntryTag(language, typed)

/** A correction with the language it applies in (none for one from before stage 51, which applies where its word is known). */
@Composable
private fun EntryRow(entry: FieldCorrectionMemory.Entry, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(MEMORY_ENTRY_TAG + entry.tag), verticalAlignment = Alignment.CenterVertically) {
        Text(if (entry.language.isEmpty()) stringResource(R.string.agent_memory_pair, entry.typed, entry.meant)
            else stringResource(R.string.agent_memory_pair_in, entry.typed, entry.meant, languageName(entry.language)), Modifier.weight(1f))
        actions()
    }
}

const val MEMORY_SWITCH_TAG = "agent_memory_switch"
const val MEMORY_SNIPPETS_TAG = "agent_memory_snippets"
const val MEMORY_PHRASE_TAG = "agent_memory_phrase_"
const val MEMORY_PIN_TAG = "agent_memory_pin_"
const val MEMORY_PHRASE_DELETE_TAG = "agent_memory_phrase_delete_"
const val MEMORY_OWN_TAG = "agent_memory_own_"
const val MEMORY_OFFER_TAG = "agent_memory_offer_"
const val MEMORY_APP_TAG = "agent_memory_app_"
const val MEMORY_CLEAR_TAG = "agent_memory_clear"

/** Phrases shown on the page at most (strongest first); the rest stay in the memory. */
const val MEMORY_MAX_SHOWN = 100

/**
 * Stage 46, the memory's part of the page: the two switches, phrases (pin, delete), own words,
 * abbreviation offers, excluded apps and "Alles vergessen". Reads the memory directly
 * (device-protected, small); every change is written at once.
 */
@Composable
fun MemorySections() {
    val context = LocalContext.current
    val prefs = context.prefs()
    var version by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(Memory.isEnabled(context)) }
    var snippets by remember { mutableStateOf(Memory.snippetsEnabled(context)) }
    var choosingApp by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Text(stringResource(R.string.agent_memory_section), style = MaterialTheme.typography.titleMedium)
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
            TextButton(onClick = { Memory.deletePhrase(context, phrase.key); bump() }, Modifier.testTag(MEMORY_PHRASE_DELETE_TAG + i)) {
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
