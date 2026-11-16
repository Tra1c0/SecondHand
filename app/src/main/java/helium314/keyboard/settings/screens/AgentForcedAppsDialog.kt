// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.ForcedApps
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs

const val FORCED_APPS_ROW_TAG = "agent_forced_apps_row"
const val FORCED_APPS_DIALOG_TAG = "agent_forced_apps_dialog"
const val FORCED_APPS_ADD_TAG = "agent_forced_apps_add"
const val FORCED_APPS_REMOVE_TAG = "agent_forced_apps_remove_"

/**
 * Stage 61: the list of apps for "Korrektur in Apps erzwingen" (Tippen & Korrektur → Regeln): each app with
 * its label (the package manager's, when the app is visible; else the package name), remove, and add from the
 * installed apps with the chooser of the snippet editor. Writes at once ([ForcedApps]); applies from the next field.
 */
@Composable
fun AgentForcedAppsDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var packages by remember { mutableStateOf(ForcedApps.read(prefs).sorted()) }
    var labels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var choosing by remember { mutableStateOf(false) }
    // the labels of the listed apps, off the main thread (an app that is not visible shows its package name)
    LaunchedEffect(packages) {
        val all = launchableApps(context).toMap()
        labels = packages.mapNotNull { pkg -> (all[pkg] ?: appLabel(context, pkg))?.let { pkg to it } }.toMap()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(FORCED_APPS_DIALOG_TAG),
        title = { Text(stringResource(R.string.agent_forced_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.agent_forced_intro), style = MaterialTheme.typography.bodySmall)
                if (packages.isEmpty()) Text(stringResource(R.string.agent_forced_empty), style = MaterialTheme.typography.bodyMedium)
                for (pkg in packages) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(labels[pkg]?.let { "$it ($pkg)" } ?: pkg, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        ForcedApps.remove(prefs, pkg)
                        packages = ForcedApps.read(prefs).sorted()
                        onChanged()
                    }, modifier = Modifier.testTag(FORCED_APPS_REMOVE_TAG + pkg)) { Text(stringResource(R.string.agent_forced_remove)) }
                }
                if (packages.size < ForcedApps.MAX_APPS)
                    TextButton(onClick = { choosing = true }, modifier = Modifier.testTag(FORCED_APPS_ADD_TAG)) {
                        Text(stringResource(R.string.agent_forced_add))
                    }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.agent_forced_close)) } },
    )
    if (choosing) AppChooser(
        onChosen = { pkg, _ ->
            choosing = false
            if (ForcedApps.add(prefs, pkg)) {
                packages = ForcedApps.read(prefs).sorted()
                onChanged()
            }
        },
        onDismiss = { choosing = false },
    )
}

/** The label of an app that has no launcher entry, null when the package manager does not know it. */
private fun appLabel(context: Context, packageName: String): String? = runCatching {
    val manager = context.packageManager
    manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString().takeIf { it.isNotBlank() && it != packageName }
}.getOrNull()
