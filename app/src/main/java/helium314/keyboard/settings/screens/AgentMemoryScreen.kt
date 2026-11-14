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
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen

/**
 * "Was ich mir gemerkt habe": what the keyboard keeps from typing, one section per kind, each with
 * its own entries and actions. Stage 49 has the corrections section; stage 46 (memory of phrases)
 * adds its section to [memorySections]. Entries are shown only here, never in the keyboard.
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
private val memorySections: List<@Composable () -> Unit> = listOf({ CorrectionsSection() })

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
                Modifier.testTag(MEMORY_DELETE_TAG + entry.typed)) { Text(stringResource(R.string.agent_memory_delete)) }
        }
    }
    Text(stringResource(R.string.agent_memory_candidates), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.agent_memory_candidates_description), style = MaterialTheme.typography.bodySmall)
    if (candidates.isEmpty()) Text(stringResource(R.string.agent_memory_none), style = MaterialTheme.typography.bodySmall)
    candidates.forEach { entry ->
        EntryRow(entry) {
            TextButton(onClick = { runCatching { FieldCorrectionMemory.adopt(context, entry) }; reload() },
                Modifier.testTag(MEMORY_ADOPT_TAG + entry.typed)) { Text(stringResource(R.string.agent_memory_adopt)) }
            TextButton(onClick = { runCatching { FieldCorrectionMemory.delete(context, entry) }; reload() },
                Modifier.testTag(MEMORY_DELETE_TAG + entry.typed)) { Text(stringResource(R.string.agent_memory_delete)) }
        }
    }
}

@Composable
private fun EntryRow(entry: FieldCorrectionMemory.Entry, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(MEMORY_ENTRY_TAG + entry.typed), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.agent_memory_pair, entry.typed, entry.meant), Modifier.weight(1f))
        actions()
    }
}
