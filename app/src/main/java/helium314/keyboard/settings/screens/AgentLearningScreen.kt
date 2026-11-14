// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import helium314.keyboard.agent.TouchCalibration
import helium314.keyboard.agent.TouchModel
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.RejectedCorrections
import helium314.keyboard.agent.TypoMemory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.AgentDocumentIO
import helium314.keyboard.agent.LearningData
import helium314.keyboard.agent.LearningSource
import helium314.keyboard.agent.UserHistoryLearningSource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Pause, inspect, export and delete what the keyboard learned from typing. All on this device. */
@Composable
fun AgentLearningScreen(onClickBack: () -> Unit, sourceFactory: (() -> LearningSource)? = null) {
    val context = LocalContext.current
    val source = remember { sourceFactory?.invoke() ?: UserHistoryLearningSource(context) }
    val scope = rememberCoroutineScope()
    val enabled = context.prefs().getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS)
    var paused by remember { mutableStateOf(LearningData.isPaused(context)) }
    var symbolDrawings by remember { mutableIntStateOf(dev.agentkeyboard.core.PersonalInkSymbols.drawings(
        helium314.keyboard.agent.SymbolDrawing.personalSymbols(context))) }
    var counts by remember { mutableStateOf<Map<String, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Learned words are private: deliberately not rememberSaveable, never in saved instance state.
    var exportContent by remember { mutableStateOf<String?>(null) }

    fun run(done: Int, work: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { work(); notice = done }
            catch (_: Exception) { notice = R.string.agent_learning_failed }
            finally { busy = false }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val encoded = exportContent
        exportContent = null
        if (uri != null && encoded != null) run(R.string.agent_file_exported) {
            withContext(Dispatchers.IO) { AgentDocumentIO.writeJson(context, uri, encoded, LearningData.MAX_EXPORT_BYTES) }
        } else if (uri != null) {
            // The screen was recreated while the file picker was open: the words were deliberately
            // not kept. Say so and remove the empty document instead of leaving a 0-byte "export".
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
            notice = R.string.agent_learning_failed
        }
    }

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_learning_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_learning_description))
            Text(stringResource(if (enabled) R.string.agent_learning_enabled else R.string.agent_learning_disabled),
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.agent_learning_pause), Modifier.weight(1f))
                Switch(checked = paused, enabled = !busy, onCheckedChange = { value ->
                    runCatching { LearningData.setPaused(context, value) }.onSuccess { paused = value; notice = 0 }
                        .onFailure { notice = R.string.agent_learning_failed }
                })
            }
            Text(stringResource(R.string.agent_learning_pause_description), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text(stringResource(R.string.agent_learning_data), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(enabled = !busy, onClick = {
                run(0) { counts = withContext(Dispatchers.IO) { LearningData.counts(source) } }
            }) { Text(stringResource(R.string.agent_learning_count)) }
            counts?.let { loaded ->
                if (loaded.isEmpty()) Text(stringResource(R.string.agent_learning_empty))
                loaded.forEach { (language, words) ->
                    Text(stringResource(R.string.agent_learning_language_count,
                        Locale.forLanguageTag(language).getDisplayName(Locale.getDefault()), words))
                }
            }
            OutlinedButton(enabled = !busy, onClick = {
                run(0) {
                    exportContent = withContext(Dispatchers.IO) { LearningData.export(source) }
                    saveFile.launch("agent-keyboard-learned-words.json")
                }
            }) { Text(stringResource(R.string.agent_learning_export)) }
            Text(stringResource(R.string.agent_learning_export_description), style = MaterialTheme.typography.bodySmall)
            // stage 36 review N4: the drawn symbols learned from corrections and the calibration
            Text(stringResource(R.string.agent_learning_symbols, symbolDrawings), style = MaterialTheme.typography.bodySmall)
            Button(enabled = !busy, onClick = { confirmDelete = true }) { Text(stringResource(R.string.agent_learning_delete)) }
            if (notice != 0) Text(stringResource(notice))
            HorizontalDivider()
            AutocorrectSection(busy)
            HorizontalDivider()
            TouchSection(busy)
        }
    }
    if (confirmDelete) ConfirmationDialog(
        onDismissRequest = { confirmDelete = false },
        onConfirmed = {
            confirmDelete = false
            run(R.string.agent_learning_deleted) {
                withContext(Dispatchers.IO) {
                    source.deleteAll()
                    // everything learned from typing, as the confirmation says
                    TypoMemory.clear(context)
                    helium314.keyboard.agent.FieldCorrectionMemory.clear(context) // stage 49
                    RejectedCorrections.clear(context)
                    TouchModel.clear(context)
                    helium314.keyboard.agent.SymbolDrawing.clearPersonalSymbols(context)
                    // stage 45: the weights of the context suggestions (numbers only)
                    helium314.keyboard.agent.ContextLearning.clear(context)
                }
                counts = emptyMap()
                symbolDrawings = 0
            }
        },
        content = { Text(stringResource(R.string.agent_learning_delete_confirm)) },
        confirmButtonText = stringResource(R.string.agent_learning_delete),
    )
}

/** Stage 20a: rules that keep known words, and the opt-in memory of undone corrections. */
@Composable
private fun AutocorrectSection(busy: Boolean) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var keepUmlaut by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true)) }
    var keepValid by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false)) }
    var remember by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, false)) }
    var stored by remember { mutableIntStateOf(RejectedCorrections.all(context).size) }
    var learnTypos by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, false)) }
    var typos by remember { mutableStateOf(TypoMemory.all(context)) }
    var confirmClearTypos by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    @Composable
    fun toggle(title: Int, description: Int, checked: Boolean, key: String, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(title), Modifier.weight(1f))
            Switch(checked = checked, enabled = !busy, onCheckedChange = { value ->
                if (prefs.edit().putBoolean(key, value).commit()) onChange(value)
            })
        }
        Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
    }

    Text(stringResource(R.string.agent_autocorrect_title), style = MaterialTheme.typography.titleMedium)
    toggle(R.string.agent_autocorrect_keep_umlaut, R.string.agent_autocorrect_keep_umlaut_description,
        keepUmlaut, AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS) { keepUmlaut = it }
    toggle(R.string.agent_autocorrect_keep_valid, R.string.agent_autocorrect_keep_valid_description,
        keepValid, AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS) { keepValid = it }
    toggle(R.string.agent_autocorrect_remember, R.string.agent_autocorrect_remember_description,
        remember, AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED) { remember = it }
    toggle(R.string.agent_autocorrect_learn, R.string.agent_autocorrect_learn_description,
        learnTypos, AgentPreferences.AUTOCORRECT_LEARN_TYPOS) { learnTypos = it }
    Text(stringResource(R.string.agent_autocorrect_typos, typos.size,
        typos.count { it.third >= TypoMemory.MIN_CONFIRMATIONS }))
    OutlinedButton(enabled = !busy && typos.isNotEmpty(), onClick = { confirmClearTypos = true }) {
        Text(stringResource(R.string.agent_autocorrect_typos_clear))
    }
    Text(stringResource(R.string.agent_autocorrect_stored, stored))
    OutlinedButton(enabled = !busy && stored > 0, onClick = { confirmClear = true }) {
        Text(stringResource(R.string.agent_autocorrect_clear))
    }
    if (confirmClearTypos) ConfirmationDialog(
        onDismissRequest = { confirmClearTypos = false },
        onConfirmed = {
            confirmClearTypos = false
            runCatching { TypoMemory.clear(context) }
            typos = TypoMemory.all(context)
        },
        content = { Text(stringResource(R.string.agent_autocorrect_typos_clear_confirm)) },
        confirmButtonText = stringResource(R.string.agent_autocorrect_typos_clear),
    )
    if (confirmClear) ConfirmationDialog(
        onDismissRequest = { confirmClear = false },
        onConfirmed = {
            confirmClear = false
            runCatching { RejectedCorrections.clear(context) }
            stored = RejectedCorrections.all(context).size
        },
        content = { Text(stringResource(R.string.agent_autocorrect_clear_confirm)) },
        confirmButtonText = stringResource(R.string.agent_autocorrect_clear),
    )
}

/** Stage 22a: personal hit boxes for letter keys, with a calibration. */
@Composable
private fun TouchSection(busy: Boolean) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var adapt by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.ADAPT_TOUCH, false)) }
    remember { TouchModel.loadStored(context); true } // the keyboard may not have been shown since a restart
    var learned by remember { mutableIntStateOf(TouchModel.all().count { it.value.third >= TouchModel.MIN_SAMPLES }) }
    var calibrating by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // stage 50: short-term layer and scenes
    var dynamic by remember { mutableStateOf(prefs.getBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, false)) }
    var scenes by remember { mutableStateOf(TouchModel.scenes()) }
    var hasPrevious by remember { mutableStateOf(TouchModel.hasPrevious(context)) }
    var confirmRestore by remember { mutableStateOf(false) }
    fun refresh() {
        learned = TouchModel.all().count { it.value.third >= TouchModel.MIN_SAMPLES }
        scenes = TouchModel.scenes()
        hasPrevious = TouchModel.hasPrevious(context)
    }

    Text(stringResource(R.string.agent_touch_title), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.agent_touch_adapt), Modifier.weight(1f))
        Switch(checked = adapt, enabled = !busy, modifier = Modifier.testTag("agent_touch_adapt"), onCheckedChange = { value ->
            if (prefs.edit().putBoolean(AgentPreferences.ADAPT_TOUCH, value).commit()) {
                adapt = value; TouchModel.enabled = value; TouchModel.dynamic = value && dynamic
            }
        })
    }
    Text(stringResource(R.string.agent_touch_adapt_description), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.agent_touch_learned, learned))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { calibrating = true }) { Text(stringResource(R.string.agent_touch_calibrate)) }
        OutlinedButton(enabled = !busy && (learned > 0 || scenes.isNotEmpty()), onClick = { confirmReset = true }) {
            Text(stringResource(R.string.agent_touch_reset))
        }
    }
    OutlinedButton(enabled = !busy && hasPrevious, onClick = { confirmRestore = true }) {
        Text(stringResource(R.string.agent_touch_restore_previous))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.agent_touch_dynamic), Modifier.weight(1f))
        Switch(checked = adapt && dynamic, enabled = !busy && adapt, modifier = Modifier.testTag("agent_touch_dynamic"), onCheckedChange = { value ->
            if (prefs.edit().putBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, value).commit()) { dynamic = value; TouchModel.dynamic = adapt && value }
        })
    }
    Text(stringResource(R.string.agent_touch_dynamic_description), style = MaterialTheme.typography.bodySmall)
    if (adapt && dynamic || scenes.isNotEmpty()) {
        Text(stringResource(R.string.agent_touch_scenes_title), style = MaterialTheme.typography.titleSmall)
        if (scenes.isEmpty()) Text(stringResource(R.string.agent_touch_scenes_none), style = MaterialTheme.typography.bodySmall)
        scenes.forEach { scene ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.agent_touch_scene, describeScene(context, scene), scene.sessions), Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = {
                    runCatching { TouchModel.deleteScene(context, scene.id) }
                    refresh()
                }) { Text(stringResource(R.string.agent_touch_scene_delete)) }
            }
        }
        if (TouchModel.promotions() > 0)
            Text(stringResource(R.string.agent_touch_promotions, TouchModel.promotions()), style = MaterialTheme.typography.bodySmall)
    }
    if (calibrating) CalibrationDialog(onDone = {
        calibrating = false
        refresh()
    })
    if (confirmReset) ConfirmationDialog(
        onDismissRequest = { confirmReset = false },
        onConfirmed = {
            confirmReset = false
            runCatching { TouchModel.clear(context) }
            refresh()
        },
        content = { Text(stringResource(R.string.agent_touch_reset_confirm)) },
        confirmButtonText = stringResource(R.string.agent_touch_reset),
    )
    if (confirmRestore) ConfirmationDialog(
        onDismissRequest = { confirmRestore = false },
        onConfirmed = {
            confirmRestore = false
            runCatching { TouchModel.restorePrevious(context) }
            refresh()
        },
        content = { Text(stringResource(R.string.agent_touch_restore_previous_confirm)) },
        confirmButtonText = stringResource(R.string.agent_touch_restore_previous),
    )
}

/** Stage 50: a scene in words, e.g. "abends, ungenauer, trifft tiefer"; never text. */
internal fun describeScene(context: android.content.Context, scene: TouchModel.SceneInfo): String {
    val bucket = when (scene.bucket) {
        dev.agentkeyboard.core.TouchDynamics.Bucket.MORNING -> R.string.agent_touch_bucket_morning
        dev.agentkeyboard.core.TouchDynamics.Bucket.DAY -> R.string.agent_touch_bucket_day
        dev.agentkeyboard.core.TouchDynamics.Bucket.EVENING -> R.string.agent_touch_bucket_evening
        dev.agentkeyboard.core.TouchDynamics.Bucket.NIGHT -> R.string.agent_touch_bucket_night
    }
    val traits = scene.traits.map {
        when (it) {
            dev.agentkeyboard.core.TouchDynamics.Trait.LESS_PRECISE -> R.string.agent_touch_trait_less_precise
            dev.agentkeyboard.core.TouchDynamics.Trait.MORE_PRECISE -> R.string.agent_touch_trait_more_precise
            dev.agentkeyboard.core.TouchDynamics.Trait.LOWER -> R.string.agent_touch_trait_lower
            dev.agentkeyboard.core.TouchDynamics.Trait.HIGHER -> R.string.agent_touch_trait_higher
            dev.agentkeyboard.core.TouchDynamics.Trait.LEFT -> R.string.agent_touch_trait_left
            dev.agentkeyboard.core.TouchDynamics.Trait.RIGHT -> R.string.agent_touch_trait_right
            dev.agentkeyboard.core.TouchDynamics.Trait.FASTER -> R.string.agent_touch_trait_faster
            dev.agentkeyboard.core.TouchDynamics.Trait.SLOWER -> R.string.agent_touch_trait_slower
            dev.agentkeyboard.core.TouchDynamics.Trait.MORE_CORRECTIONS -> R.string.agent_touch_trait_corrections
        }
    }
    return (listOf(bucket) + traits).joinToString(", ") { context.getString(it) }
}

@Composable
internal fun CalibrationDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    var touches by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Map<Int, Triple<Float, Float, Int>>?>(null) }
    DisposableEffect(Unit) {
        TouchCalibration.start()
        onDispose { TouchCalibration.start() } // touches are not kept beyond this dialog
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.agent_touch_calibrate), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.agent_touch_calibrate_description), style = MaterialTheme.typography.bodySmall)
                Text(TouchCalibration.TEXT, style = MaterialTheme.typography.bodyLarge)
                val current = result
                if (current == null) {
                    androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.fillMaxWidth(), factory = { ctx ->
                        android.widget.EditText(ctx).apply {
                            privateImeOptions = AgentPreferences.TOUCH_CALIBRATION_FIELD
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            minLines = 3
                            addTextChangedListener(object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                override fun afterTextChanged(s: android.text.Editable?) { touches = TouchCalibration.touchCount() }
                            })
                            requestFocus()
                        }
                    })
                    Text(stringResource(R.string.agent_touch_counted, touches))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDone) { Text(stringResource(android.R.string.cancel)) }
                        Button(enabled = touches > 0, onClick = { result = TouchCalibration.result() }) {
                            Text(stringResource(R.string.agent_touch_evaluate))
                        }
                    }
                } else {
                    if (current.isEmpty()) Text(stringResource(R.string.agent_touch_too_few))
                    current.entries.sortedByDescending { kotlin.math.abs(it.value.first) + kotlin.math.abs(it.value.second) }
                        .forEach { (code, value) -> Text(describe(context, code, value)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDone) { Text(stringResource(R.string.agent_touch_discard)) }
                        Button(enabled = current.isNotEmpty(), onClick = {
                            // calibration touches count like typed ones, plus enough to be used right away
                            runCatching { TouchModel.replace(context, current.mapValues { (_, v) ->
                                Triple(v.first, v.second, v.third + TouchModel.MIN_SAMPLES) }) }
                            onDone()
                        }) { Text(stringResource(R.string.agent_touch_apply)) }
                    }
                }
            }
        }
    }
}

private fun describe(context: android.content.Context, code: Int, value: Triple<Float, Float, Int>): String {
    fun percent(v: Float) = kotlin.math.round(kotlin.math.abs(v) * 100).toInt()
    val horizontal = context.getString(if (value.first < 0) R.string.agent_touch_left else R.string.agent_touch_right, percent(value.first))
    val vertical = context.getString(if (value.second < 0) R.string.agent_touch_up else R.string.agent_touch_down, percent(value.second))
    return context.getString(R.string.agent_touch_key, String(Character.toChars(code)).uppercase(), horizontal, vertical, value.third)
}
