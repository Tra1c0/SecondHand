// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.EmojiMixTable
import helium314.keyboard.agent.Diagnose
import helium314.keyboard.agent.HapticChain
import helium314.keyboard.agent.SubtypeSwitchLog
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage 30: Einstellungen → Diagnose. Shows what [Diagnose] reads (states, counts, versions, never
 * contents), a test of the tick chain, the reachability of www.gstatic.com only on a tap, and copies
 * exactly the shown values. No FLAG_SECURE: nothing here is secret, and a screenshot is a way to
 * pass it on.
 */
@Composable
fun AgentDiagnoseScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val device = remember { HapticChain.deviceFor(context) }
    var table by remember { mutableStateOf<EmojiMixTable?>(null) }
    var tableRead by remember { mutableStateOf(false) }
    var cache by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var reach by remember { mutableStateOf<Diagnose.Reach?>(null) }
    // stage 30 fix: every section is built once (as the page opens), not on each recomposition
    var symbols by remember { mutableStateOf<Diagnose.Section?>(null) }
    LaunchedEffect(Unit) {
        val app = context.applicationContext ?: context
        // lists the dictionary files: off the main thread
        symbols = withContext(Dispatchers.IO) { Diagnose.symbols(context) }
        // the table the keyboard has in memory (main thread), else read from the asset; both offline
        table = EmojiMixer.table(context) ?: withContext(Dispatchers.IO) { Diagnose.readTable(app) }
        tableRead = true
        cache = withContext(Dispatchers.IO) { Diagnose.cacheStats(context) }
    }
    val haptics = remember { Diagnose.haptics(context, device) }
    val languages = remember { Diagnose.languages(context, SubtypeSwitchLog.entries(), SystemClock.elapsedRealtime()) }
    val phone = remember { Diagnose.device(context) }
    // stage 34: the patterns in use (own ones by name) and one grid of each measured now, off the main thread
    var lightPatterns by remember { mutableStateOf(emptyList<Diagnose.Row>()) }
    LaunchedEffect(Unit) {
        val settings = helium314.keyboard.agent.RgbLighting.read(context.prefs())
        val library = helium314.keyboard.agent.RgbOwnPatterns.library(context.prefs())
        lightPatterns = withContext(Dispatchers.Default) { Diagnose.lightPatterns(context, settings, library) }
    }
    val lighting = remember(lightPatterns) { Diagnose.lighting(context, helium314.keyboard.agent.RgbLighting.lastFrameWork(), lightPatterns) }
    val emojiMix = remember(table, tableRead, cache, reach) { Diagnose.emojiMix(context, table, tableRead, cache, reach) }
    val sections = remember(symbols, emojiMix, lighting) { listOfNotNull(haptics, symbols, languages, emojiMix, lighting, phone) }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_diagnose_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.agent_diagnose_intro))
            Button(onClick = { copy(context, Diagnose.text(context, sections)) }, modifier = Modifier.testTag("agent_diagnose_copy")) {
                Text(stringResource(R.string.agent_diagnose_copy))
            }
            sections.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                section.rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(row.label, Modifier.weight(1f))
                        Text(row.value, Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
                when (section.part) {
                    Diagnose.Part.HAPTICS -> {
                        val plan = Diagnose.chain(device)?.plan
                        Text(stringResource(R.string.agent_diagnose_test_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = plan != null, onClick = {
                            if (plan != null) runCatching { device?.play(plan) } // a vendor vibrator may refuse
                        }, modifier = Modifier.testTag("agent_diagnose_test")) { Text(stringResource(R.string.agent_diagnose_test)) }
                    }
                    Diagnose.Part.EMOJI_MIX -> {
                        Text(stringResource(R.string.agent_diagnose_gstatic_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = tableRead && reach != Diagnose.Reach.Running, onClick = {
                            val app = context.applicationContext ?: context
                            val current = table
                            reach = Diagnose.Reach.Running
                            scope.launch { reach = withContext(Dispatchers.IO) { Diagnose.checkGstatic(app, current) } }
                        }, modifier = Modifier.testTag("agent_diagnose_gstatic")) { Text(stringResource(R.string.agent_diagnose_gstatic_check)) }
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.agent_diagnose_title), text))
    // from Android 13 on the system shows its own confirmation
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        Toast.makeText(context, R.string.agent_diagnose_copied, Toast.LENGTH_SHORT).show()
}
