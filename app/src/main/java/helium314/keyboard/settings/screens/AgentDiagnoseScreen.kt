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
import androidx.compose.ui.semantics.semantics
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
    var lastField by remember { mutableStateOf<Diagnose.Section?>(null) }
    // stage 61: the choice made with the button of the card (null until it is pressed), and that it was saved
    var forceChoice by remember { mutableStateOf<Boolean?>(null) }
    var forceFull by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val app = context.applicationContext ?: context
        // stage 55: the last field, first on the page; its dictionary files and the app's name off the main thread
        val opened = SystemClock.elapsedRealtime()
        lastField = withContext(Dispatchers.IO) { Diagnose.lastField(context, opened) }
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
    val lighting = remember(lightPatterns) { Diagnose.lighting(context, helium314.keyboard.agent.RgbLighting.lastFrameWork(), lightPatterns, helium314.keyboard.agent.RgbLighting.painterFailure) }
    val emojiMix = remember(table, tableRead, cache, reach) { Diagnose.emojiMix(context, table, tableRead, cache, reach) }
    val sections = remember(lastField, symbols, emojiMix, lighting) { listOfNotNull(lastField, haptics, symbols, languages, emojiMix, lighting, phone) }
    // stage 64a: the card "Tippen" (read when the page opens and after "Zurücksetzen"), before the card of the app
    var typingVersion by remember { mutableStateOf(0) }
    val typing = remember(typingVersion) { Diagnose.typing(context, helium314.keyboard.agent.TypingPerf.snapshot(), System.currentTimeMillis()) }
    // stage 79b: the card "Autofill-Vorschläge", numbers only, read when the page opens
    val autofill = remember { Diagnose.autofill(context, helium314.keyboard.agent.InlineAutofillCounters.shared.snapshot()) }
    // stage 87b: the card "Cursor-Trackpad": configuration and decisions, numbers only, read when the page opens
    val trackpad = remember { Diagnose.trackpad(context, context.prefs(), helium314.keyboard.agent.CursorTrackCounters.shared.snapshot()) }
    // stage 89: the card "Leiste": visible changes of the bar and their causes, numbers only
    val barCalm = remember { Diagnose.barCalm(context, context.prefs(), helium314.keyboard.agent.BarCalmCounters.total, helium314.keyboard.agent.BarCalmCounters.field, helium314.keyboard.agent.BarCalmCounters.slowSeen) }
    val shown = remember(sections, typing, autofill, trackpad, barCalm) { sections.flatMap { if (it.part == Diagnose.Part.DEVICE) listOf(typing, barCalm, trackpad, autofill, it) else listOf(it) } }
    val windowWidth = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    val narrow = with(androidx.compose.ui.platform.LocalDensity.current) { windowWidth.toDp() } < 400.dp
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_diagnose_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.agent_diagnose_intro))
            Button(onClick = { copy(context, Diagnose.text(context, shown)) }, modifier = Modifier.testTag("agent_diagnose_copy")) {
                Text(stringResource(R.string.agent_diagnose_copy))
            }
            shown.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                section.rows.forEach { row ->
                    // review of stage 55 (N10): TalkBack reads label and value as one; a long value (a
                    // verdict) or a narrow screen puts the value under its label, at full width
                    val rowModifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
                    if (row.value.length > LONG_VALUE || (narrow && row.value.length > NARROW_VALUE)) {
                        Column(rowModifier) {
                            Text(row.label, style = MaterialTheme.typography.bodySmall)
                            Text(row.value)
                        }
                    } else Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(row.label, Modifier.weight(1f))
                        Text(row.value, Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
                when (section.part) {
                    Diagnose.Part.LAST_FIELD -> {
                        // stage 61: only when the card knows the app (not in fields without learning)
                        val pkg = section.appPackage
                        if (pkg != null) {
                            val listed = forceChoice ?: section.appForced
                            Text(stringResource(R.string.agent_diagnose_force_hint), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                val prefs = context.prefs()
                                if (listed) helium314.keyboard.agent.ForcedApps.remove(prefs, pkg)
                                else helium314.keyboard.agent.ForcedApps.add(prefs, pkg)
                                val now = helium314.keyboard.agent.ForcedApps.isListed(prefs, pkg)
                                // a full list refuses the app: say so, the state stays as it was
                                forceFull = !listed && !now && helium314.keyboard.agent.ForcedApps.read(prefs).size >= helium314.keyboard.agent.ForcedApps.MAX_APPS
                                forceChoice = if (forceFull) null else now
                            }, modifier = Modifier.testTag("agent_diagnose_force")) {
                                Text(stringResource(if (listed) R.string.agent_diagnose_force_remove else R.string.agent_diagnose_force_add))
                            }
                            if (forceFull) Text(stringResource(R.string.agent_diagnose_force_full), style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("agent_diagnose_force_full"))
                            else if (forceChoice != null) Text(stringResource(R.string.agent_diagnose_force_saved), style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("agent_diagnose_force_saved"))
                        }
                    }
                    Diagnose.Part.HAPTICS -> {
                        val plan = Diagnose.chain(device)?.plan
                        Text(stringResource(R.string.agent_diagnose_test_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(enabled = plan != null, onClick = {
                            if (plan != null) runCatching { device?.play(plan) } // a vendor vibrator may refuse
                        }, modifier = Modifier.testTag("agent_diagnose_test")) { Text(stringResource(R.string.agent_diagnose_test)) }
                    }
                    Diagnose.Part.TYPING -> {
                        Text(stringResource(R.string.agent_diagnose_typing_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            helium314.keyboard.agent.TypingPerf.reset()
                            helium314.keyboard.agent.TypingPerf.resetFailures()
                            typingVersion++
                        }, modifier = Modifier.testTag("agent_diagnose_typing_reset")) { Text(stringResource(R.string.agent_diagnose_typing_reset)) }
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

/** Values at most this long sit beside their label; longer ones (a verdict) go under it. */
private const val LONG_VALUE = 32
/** On a narrow screen ([narrow]) already from this length. */
private const val NARROW_VALUE = 14

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(context.getString(R.string.agent_diagnose_title), text)
    // not marked sensitive (lead, stage 55): the copy is meant to be passed on and holds no field text;
    // marked, the clipboard guard would file it as a secret in the vault
    clipboard.setPrimaryClip(clip)
    // from Android 13 on the system shows its own confirmation
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        Toast.makeText(context, R.string.agent_diagnose_copied, Toast.LENGTH_SHORT).show()
}
