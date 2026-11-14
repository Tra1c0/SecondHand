// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.ReleaseNotes
import dev.agentkeyboard.core.UpdateReleases
import helium314.keyboard.agent.updates.UpdatePrompts
import helium314.keyboard.agent.updates.Updates
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsDestination

const val UPDATE_DIALOG_TAG = "agent_update_dialog"
const val UPDATE_INSTALL_TAG = "agent_update_dialog_install"
const val UPDATE_LATER_TAG = "agent_update_dialog_later"
const val WHATS_NEW_TAG = "agent_update_whats_new"
const val UPDATE_NOTES_MORE_TAG = "agent_update_notes_more"

/** Blocks of the notes shown before "Alle Notizen anzeigen". */
private const val COLLAPSED_BLOCKS = 6

/**
 * Stage 54: the update dialogs when the settings open ("Neu in Build N" once after an update, then
 * "Build N ist da" while the last check knows a newer build, not when the settings opened on the
 * Updates page itself, [onUpdatesPage]). True while one of them is shown.
 * Reads only stored results and the notes in the APK; "Installieren" opens the Updates page, which
 * starts the download once for that build (the user's click here), "Später" hides it for that build.
 */
@Composable
fun AgentUpdatePrompts(
    onUpdatesPage: Boolean = false,
    installed: Long? = null,
    freshInstall: (() -> Boolean)? = null,
    onInstall: (Long) -> Unit = {
        UpdatePrompts.requestInstall(it)
        SettingsDestination.navigateTo(SettingsDestination.AgentUpdates)
    },
): Boolean {
    val context = LocalContext.current
    val version = remember { installed ?: runCatching { Updates.installedVersion(context) }.getOrDefault(0L) }
    fun next() = if (version <= 0) null
        else if (freshInstall != null) UpdatePrompts.next(context, version, !onUpdatesPage, freshInstall)
        else UpdatePrompts.next(context, version, !onUpdatesPage)
    var prompt by remember { mutableStateOf(next()) }
    when (val shown = prompt ?: return false) {
        is UpdatePrompts.Prompt.WhatsNew -> {
            LaunchedEffect(shown.version) { UpdatePrompts.markSeen(context, shown.version) }
            AlertDialog(
                onDismissRequest = { prompt = next() },
                confirmButton = { TextButton(onClick = { prompt = next() }) { Text(stringResource(R.string.agent_update_whats_new_ok)) } },
                title = { Text(stringResource(R.string.agent_update_whats_new_title, shown.version)) },
                text = { ReleaseNotesText(shown.notes, truncatedBuild = null) },
                modifier = Modifier.testTag(WHATS_NEW_TAG),
            )
        }
        is UpdatePrompts.Prompt.Available -> AlertDialog(
            // back or outside: closed for now, it comes again next time
            onDismissRequest = { prompt = null },
            confirmButton = {
                TextButton(onClick = { prompt = null; onInstall(shown.build) }, Modifier.testTag(UPDATE_INSTALL_TAG)) {
                    Text(stringResource(R.string.agent_update_dialog_install))
                }
            },
            dismissButton = {
                TextButton(onClick = { UpdatePrompts.later(context, shown.build); prompt = null }, Modifier.testTag(UPDATE_LATER_TAG)) {
                    Text(stringResource(R.string.agent_update_dialog_later))
                }
            },
            title = { Text(stringResource(R.string.agent_update_dialog_title, shown.build)) },
            text = { ReleaseNotesText(shown.notes, truncatedBuild = shown.build.takeIf { shown.truncated }) },
            modifier = Modifier.testTag(UPDATE_DIALOG_TAG),
        )
    }
    return true
}

/** The notes as simple Markdown: headings bold, list items with a dot; long notes collapsed, scrollable. */
@Composable
private fun ReleaseNotesText(notes: String, truncatedBuild: Long?) {
    val blocks = remember(notes) { ReleaseNotes.blocks(notes) }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (blocks.isEmpty()) Text(stringResource(R.string.agent_update_notes_none))
        for (block in if (expanded) blocks else blocks.take(COLLAPSED_BLOCKS)) when (block) {
            is ReleaseNotes.Block.Heading -> Text(block.text, style = if (block.level <= 1) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            is ReleaseNotes.Block.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium)
                Text(block.text, style = MaterialTheme.typography.bodyMedium)
            }
            is ReleaseNotes.Block.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (blocks.size > COLLAPSED_BLOCKS) TextButton(onClick = { expanded = !expanded }, Modifier.testTag(UPDATE_NOTES_MORE_TAG)) {
            Text(stringResource(if (expanded) R.string.agent_update_notes_less else R.string.agent_update_notes_more))
        }
        if (truncatedBuild != null && (expanded || blocks.size <= COLLAPSED_BLOCKS))
            Text(stringResource(R.string.agent_update_notes_truncated, "${UpdateReleases.RELEASES_PAGE}/tag/build-$truncatedBuild"),
                style = MaterialTheme.typography.bodySmall)
    }
}
