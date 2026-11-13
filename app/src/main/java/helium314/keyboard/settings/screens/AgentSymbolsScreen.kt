// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.InkCloud
import dev.agentkeyboard.core.InkGroup
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkSymbols
import dev.agentkeyboard.core.OwnInkSymbols
import dev.agentkeyboard.core.PersonalInkSymbols
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.SymbolDrawing
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference

/** Settings key of the row that opens [AgentSymbolsScreen] (search; the switch itself is [AgentPreferences.SYMBOL_DRAWING]). */
const val AGENT_SYMBOLS_ENTRY = "agent_symbols_entry"

/** For search and the gesture typing page: the switch and the row that opens the page. */
fun createAgentSymbolSettings(context: Context) = listOf(
    Setting(context, AgentPreferences.SYMBOL_DRAWING, R.string.agent_symbols_switch, R.string.agent_symbols_switch_summary) {
        SwitchPreference(it, false)
    },
    Setting(context, AGENT_SYMBOLS_ENTRY, R.string.agent_symbols_title, R.string.agent_symbols_summary) {
        Preference(name = it.title, description = it.description,
            onClick = { SettingsDestination.navigateTo(SettingsDestination.AgentSymbols) }) { NextScreenIcon() }
    },
)

/**
 * Stage 27 "Symbole malen": the switch, the built-in symbols as small drawings, and the own symbols
 * (draw [OwnInkSymbols.SAMPLES] times, choose the text). Stage 36: the personal templates (learning
 * switch, list with delete, calibration). Nothing here leaves the phone.
 */
@Composable
fun AgentSymbolsScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    var own by remember { mutableStateOf(SymbolDrawing.ownSymbols(context)) }
    var adding by remember { mutableStateOf(false) }
    var personal by remember { mutableStateOf(SymbolDrawing.personalSymbols(context)) }
    var calibrating by remember { mutableStateOf(false) }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_symbols_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            SwitchPreference(name = stringResource(R.string.agent_symbols_switch), key = AgentPreferences.SYMBOL_DRAWING,
                default = false, description = stringResource(R.string.agent_symbols_switch_summary))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.agent_symbols_how))
                Text(stringResource(R.string.agent_symbols_local), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.agent_symbols_own), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.agent_symbols_own_description))
                if (own.isEmpty()) Text(stringResource(R.string.agent_symbols_own_empty), style = MaterialTheme.typography.bodySmall)
                own.forEachIndexed { index, symbol ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("agent_symbol_own_$index")) {
                        CloudPreview(symbol.samples.first(), Modifier.size(40.dp))
                        Text(symbol.text, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                        val delete = stringResource(R.string.agent_symbols_delete, symbol.text)
                        TextButton(onClick = {
                            own = own.filterIndexed { i, _ -> i != index }
                            SymbolDrawing.saveOwnSymbols(context, own)
                        }, modifier = Modifier.semantics { contentDescription = delete }) { Text(stringResource(R.string.delete)) }
                    }
                }
                if (adding) AddSymbolCard(onDismiss = { adding = false }) { symbol ->
                    own = own + symbol
                    SymbolDrawing.saveOwnSymbols(context, own)
                    adding = false
                }
                else if (own.size >= OwnInkSymbols.MAX_SYMBOLS) Text(stringResource(R.string.agent_symbols_full))
                else Button(onClick = { adding = true }, modifier = Modifier.testTag("agent_symbol_add")) {
                    Text(stringResource(R.string.agent_symbols_add))
                }
                // stage 36: personal templates
                Text(stringResource(R.string.agent_symbols_personal), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.agent_symbols_personal_description))
            }
            SwitchPreference(name = stringResource(R.string.agent_symbols_learning), key = AgentPreferences.SYMBOL_LEARNING,
                default = false, description = stringResource(R.string.agent_symbols_learning_summary))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (personal.isEmpty()) Text(stringResource(R.string.agent_symbols_personal_empty), style = MaterialTheme.typography.bodySmall)
                personal.forEach { symbol ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("agent_symbol_personal_${symbol.text}")) {
                        CloudPreview(symbol.samples.last(), Modifier.size(40.dp))
                        Text(symbol.text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.agent_symbols_personal_count, symbol.samples.size),
                            Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
                        val delete = stringResource(R.string.agent_symbols_delete, symbol.text)
                        TextButton(onClick = {
                            personal = PersonalInkSymbols.remove(personal, symbol.text)
                            SymbolDrawing.savePersonalSymbols(context, personal)
                        }, modifier = Modifier.semantics { contentDescription = delete }) { Text(stringResource(R.string.delete)) }
                    }
                }
                if (personal.isNotEmpty()) TextButton(onClick = {
                    personal = emptyList()
                    SymbolDrawing.savePersonalSymbols(context, personal)
                }, modifier = Modifier.testTag("agent_symbol_personal_clear")) { Text(stringResource(R.string.agent_symbols_personal_delete_all)) }
                if (calibrating) CalibrationCard(onDone = { calibrating = false }) { text, cloud ->
                    // saved at once: stopping half way keeps what was drawn
                    personal = PersonalInkSymbols.add(SymbolDrawing.personalSymbols(context), text, cloud)
                    SymbolDrawing.savePersonalSymbols(context, personal)
                }
                else {
                    Text(stringResource(R.string.agent_symbols_calibrate_description), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { calibrating = true }, modifier = Modifier.testTag("agent_symbol_calibrate")) {
                        Text(stringResource(R.string.agent_symbols_calibrate))
                    }
                }
                Text(stringResource(R.string.agent_symbols_builtin), style = MaterialTheme.typography.titleMedium)
                InkSymbols.all.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { symbol ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                                StrokesPreview(symbol.variants.first(), Modifier.size(36.dp))
                                Text(symbol.text, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.width(56.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * Draw [OwnInkSymbols.SAMPLES] times, then type the text; [onSave] gets the new symbol. A card in
 * the page, not a dialog: a text field in a Compose dialog never gets idle in Robolectric, so the
 * flow could not be tested (docs/VALIDATION.md, stage 27).
 */
@Composable
private fun AddSymbolCard(onDismiss: () -> Unit, onSave: (OwnInkSymbols.Symbol) -> Unit) {
    var samples by remember { mutableStateOf(listOf<InkCloud>()) }
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var text by remember { mutableStateOf("") }
    val drawing = samples.size < OwnInkSymbols.SAMPLES
    val problem = OwnInkSymbols.textProblem(text)
    // stage 31: the same limits as a drawing on the keyboard; such a drawing would be no symbol
    val tooManyStrokes = strokes.size > InkCloud.MAX_STROKES
    val tooLong = strokes.sumOf { it.size } > MAX_PAD_POINTS
    Card(Modifier.fillMaxWidth().testTag("agent_symbol_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.agent_symbols_add), style = MaterialTheme.typography.titleMedium)
            if (drawing) {
                Text(stringResource(R.string.agent_symbols_drawing, samples.size + 1, OwnInkSymbols.SAMPLES))
                Text(stringResource(R.string.agent_symbols_draw_hint), style = MaterialTheme.typography.bodySmall)
                DrawingPad(strokes, { strokes = it }, Modifier.fillMaxWidth().aspectRatio(1f))
                if (tooManyStrokes || tooLong) Text(stringResource(if (tooManyStrokes) R.string.agent_symbols_too_many_strokes
                    else R.string.agent_symbols_too_long), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("agent_symbol_pad_limit"))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    samples.forEach { CloudPreview(it, Modifier.size(40.dp)) }
                }
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text(stringResource(R.string.agent_symbols_text)) },
                    isError = problem == OwnInkSymbols.TextProblem.TOO_LONG,
                    supportingText = if (problem == OwnInkSymbols.TextProblem.TOO_LONG) {
                        { Text(stringResource(R.string.agent_symbols_text_too_long)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().testTag("agent_symbol_text"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                if (drawing && strokes.isNotEmpty()) TextButton(onClick = { strokes = emptyList() }) {
                    Text(stringResource(R.string.agent_symbols_redraw))
                }
                if (drawing) TextButton(enabled = strokes.isNotEmpty() && !tooManyStrokes && !tooLong, onClick = {
                    InkCloud.of(strokes.map { stroke -> InkStroke(FloatArray(2 * stroke.size) { i -> if (i % 2 == 0) stroke[i / 2].x else stroke[i / 2].y }) })
                        ?.let { samples = samples + it }
                    strokes = emptyList()
                }) { Text(stringResource(R.string.agent_symbols_next)) }
                else TextButton(enabled = problem == null, onClick = { onSave(OwnInkSymbols.Symbol(text, samples)) }) {
                    Text(stringResource(R.string.agent_symbols_save))
                }
            }
        }
    }
}

/**
 * Stage 36: draw every built-in symbol [CALIBRATION_SAMPLES] times; each drawing goes to [onSample]
 * at once (a personal template). "Überspringen" goes to the next symbol, "Fertig" stops.
 */
@Composable
private fun CalibrationCard(onDone: () -> Unit, onSample: (String, InkCloud) -> Unit) {
    var index by remember { mutableStateOf(0) }
    var sample by remember { mutableStateOf(1) }
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    val symbol = InkSymbols.all[index]
    val tooManyStrokes = strokes.size > InkCloud.MAX_STROKES
    val tooLong = strokes.sumOf { it.size } > MAX_PAD_POINTS
    fun nextSymbol() {
        strokes = emptyList()
        sample = 1
        if (index + 1 >= InkSymbols.all.size) onDone() else index++
    }
    Card(Modifier.fillMaxWidth().testTag("agent_symbol_calibration")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.agent_symbols_calibrate), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StrokesPreview(symbol.variants.first(), Modifier.size(36.dp))
                Text(stringResource(R.string.agent_symbols_calibrate_step, symbol.text, sample, CALIBRATION_SAMPLES,
                    index + 1, InkSymbols.all.size), Modifier.padding(start = 12.dp).testTag("agent_symbol_calibration_step"))
            }
            DrawingPad(strokes, { strokes = it }, Modifier.fillMaxWidth().aspectRatio(1f))
            if (tooManyStrokes || tooLong) Text(stringResource(if (tooManyStrokes) R.string.agent_symbols_too_many_strokes
                else R.string.agent_symbols_too_long), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.agent_symbols_calibrate_done)) }
                TextButton(onClick = { nextSymbol() }) { Text(stringResource(R.string.agent_symbols_calibrate_skip)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (strokes.isNotEmpty()) TextButton(onClick = { strokes = emptyList() }) { Text(stringResource(R.string.agent_symbols_redraw)) }
                TextButton(enabled = strokes.isNotEmpty() && !tooManyStrokes && !tooLong, onClick = {
                    InkCloud.of(strokes.map { stroke -> InkStroke(FloatArray(2 * stroke.size) { i -> if (i % 2 == 0) stroke[i / 2].x else stroke[i / 2].y }) })
                        ?.let { onSample(symbol.text, it) }
                    if (sample < CALIBRATION_SAMPLES) { sample++; strokes = emptyList() } else nextSymbol()
                }) { Text(stringResource(R.string.agent_symbols_next)) }
            }
        }
    }
}

/** Stage 36: how often the calibration asks for each built-in symbol. */
const val CALIBRATION_SAMPLES = 2

/** Points of all strokes of one drawing in [DrawingPad]; like [InkGroup.MAX_POINTS] on the keyboard. */
private const val MAX_PAD_POINTS = InkGroup.MAX_POINTS

/**
 * A square to draw in with the finger; every touch from down to up is one stroke. Stops taking points
 * one after [MAX_PAD_POINTS] for all strokes together: exactly that many are a drawing, like on the
 * keyboard (InkGroup), one more is too long and the card asks to draw again (stage 31 fix).
 */
@Composable
private fun DrawingPad(strokes: List<List<Offset>>, onChange: (List<List<Offset>>) -> Unit, modifier: Modifier) {
    val current = rememberUpdatedState(strokes)
    val change = rememberUpdatedState(onChange)
    var live by remember { mutableStateOf(listOf<Offset>()) }
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier
        .border(1.dp, MaterialTheme.colorScheme.outline)
        .testTag("agent_symbol_pad")
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                var points = listOf(down.position)
                live = points
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (pointer.position != points.last() && current.value.sumOf { it.size } + points.size <= MAX_PAD_POINTS)
                        points = points + pointer.position
                    pointer.consume()
                    live = points
                    if (!pointer.pressed) break
                }
                live = emptyList()
                change.value(current.value + listOf(points))
            }
        }) {
        (strokes + listOf(live)).forEach { drawPolyline(it, color) }
    }
}

private fun DrawScope.drawPolyline(points: List<Offset>, color: Color, width: Float = 6f) {
    if (points.size == 1) drawCircle(color, width, points[0])
    for (i in 1 until points.size) drawLine(color, points[i - 1], points[i], width, StrokeCap.Round)
}

/** A built-in symbol drawn from its strokes (100 × 100 box). */
@Composable
private fun StrokesPreview(strokes: List<InkStroke>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val scale = size.minDimension / 100f
        strokes.forEach { stroke ->
            drawPolyline(List(stroke.size) { Offset(stroke.x(it) * scale, stroke.y(it) * scale) }, color, 3f)
        }
    }
}

/** A stored drawing: its normalized points joined per stroke. */
@Composable
private fun CloudPreview(cloud: InkCloud, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val scale = size.minDimension * 0.9f
        val centre = Offset(size.width / 2, size.height / 2)
        var start = 0
        for (i in 1..InkCloud.SIZE) {
            if (i == InkCloud.SIZE || cloud.strokes[i] != cloud.strokes[start]) {
                drawPolyline((start until i).map { centre + Offset(cloud.xs[it] * scale, cloud.ys[it] * scale) }, color, 3f)
                start = i
            }
        }
    }
}
