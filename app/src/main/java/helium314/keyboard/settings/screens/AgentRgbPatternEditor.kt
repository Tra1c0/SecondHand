// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbBlend
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.RgbCurve
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbGenerator
import dev.agentkeyboard.core.RgbLayer
import dev.agentkeyboard.core.RgbLayerReaction
import dev.agentkeyboard.core.RgbLook
import dev.agentkeyboard.core.RgbOwnPattern
import dev.agentkeyboard.core.RgbOwnRenderer
import dev.agentkeyboard.core.RgbPaletteMode
import dev.agentkeyboard.core.RgbPatternError
import dev.agentkeyboard.core.RgbPatternLibrary
import dev.agentkeyboard.core.RgbPatternLayer
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbStop
import dev.agentkeyboard.core.RgbTarget
import dev.agentkeyboard.core.RgbWaveform
import helium314.keyboard.agent.MyDesigns
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The editor of one own light pattern (stage 34), shown in place of the RGB page: the live preview
 * at the top (the keyboard mock with the real frame rate, taps try the typing reactions), below
 * it the name with duplicate, export and delete, the layers, and for the chosen layer its shape and
 * blend mode, colour stops (the colour picker of the design editor) and palette mode, the motion
 * (speed, direction, scale, offset, opacity and the curve editor of stage 29) and the reaction to
 * typing. Every finished change is saved at once ([onChange]); while a slider or a curve point is
 * dragged only the preview follows (a draft), so neither the page nor the setting changes per move.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RgbPatternEditor(
    pattern: RgbOwnPattern,
    preview: (RgbOwnPattern) -> RgbSettings,
    design: MyDesigns.Design,
    animate: Boolean,
    onChange: (RgbOwnPattern) -> Unit,
    onRename: (String) -> Boolean,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    // the pattern being dragged: read only by the preview
    val draft = remember(pattern.id) { mutableStateOf<RgbOwnPattern?>(null) }
    var layerIndex by rememberSaveable(pattern.id) { mutableIntStateOf(0) }
    var stopIndex by rememberSaveable(pattern.id) { mutableIntStateOf(0) }
    var selectedPoint by rememberSaveable(pattern.id) { mutableIntStateOf(-1) }
    var colorToPick by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val li = layerIndex.coerceIn(0, pattern.layers.size - 1)
    val layer = pattern.layers[li]
    val si = stopIndex.coerceIn(0, layer.stops.size - 1)

    fun commit(p: RgbOwnPattern) {
        draft.value = null
        onChange(p.normalized())
    }
    fun withLayer(edit: (RgbPatternLayer) -> RgbPatternLayer) =
        pattern.copy(layers = pattern.layers.toMutableList().also { it[li] = edit(it[li]) }).normalized()
    fun editLayer(edit: (RgbPatternLayer) -> RgbPatternLayer) = commit(withLayer(edit))
    fun dragLayer(edit: (RgbPatternLayer) -> RgbPatternLayer) { draft.value = withLayer(edit) }
    fun finishDrag() { draft.value?.let { commit(it) } }

    SearchSettingsScreen(onBack, stringResource(R.string.agent_rgb_pattern_editor_title), emptyList()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LivePreview(design, { preview(draft.value ?: pattern) }, animate)
            RgbNote(stringResource(R.string.agent_rgb_pattern_preview_label, preview(pattern).fps))
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .testTag("agent_rgb_pattern_editor")
        ) {
            RgbSection(R.string.agent_rgb_pattern_name) {
                var name by remember(pattern.id) { mutableStateOf(pattern.name) }
                var invalid by remember(pattern.id) { mutableStateOf(false) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { text ->
                        name = text
                        invalid = !onRename(text)
                    },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) { { Text(stringResource(R.string.agent_rgb_pattern_name_invalid)) } } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("agent_rgb_pattern_name"),
                )
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onDuplicate, Modifier.testTag("agent_rgb_pattern_duplicate")) { Text(stringResource(R.string.agent_rgb_pattern_duplicate)) }
                    OutlinedButton(onExport, Modifier.testTag("agent_rgb_pattern_export")) { Text(stringResource(R.string.agent_rgb_pattern_export)) }
                    TextButton({ confirmDelete = true }, Modifier.testTag("agent_rgb_pattern_delete")) {
                        Text(stringResource(R.string.agent_rgb_pattern_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                RgbNote(stringResource(R.string.agent_rgb_pattern_local_note))
            }

            RgbSection(R.string.agent_rgb_pattern_group_layers) {
                RgbChips(pattern.layers.indices.toList(), { it == li }, { stringResource(R.string.agent_rgb_pattern_layer_n, it + 1) },
                    "agent_rgb_pattern_layer_") { layerIndex = it; stopIndex = 0; selectedPoint = -1 }
                RgbNote(stringResource(R.string.agent_rgb_pattern_layers_note))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pattern.layers.size < RgbOwnPattern.MAX_LAYERS) FilledTonalButton({
                        // a new layer on top: a soft white sparkle over what is there, easy to see and change
                        commit(pattern.copy(layers = pattern.layers + RgbPatternLayer(RgbGenerator.SPARKLE,
                            listOf(RgbStop(0f, WHITE), RgbStop(1f, WHITE)), blend = RgbBlend.SCREEN, opacity = 80)))
                        layerIndex = pattern.layers.size; stopIndex = 0; selectedPoint = -1
                    }, Modifier.testTag("agent_rgb_pattern_layer_add")) { Text(stringResource(R.string.agent_rgb_pattern_layer_add)) }
                    if (pattern.layers.size > 1) OutlinedButton({
                        commit(pattern.copy(layers = pattern.layers.filterIndexed { i, _ -> i != li }))
                        layerIndex = (li - 1).coerceAtLeast(0); stopIndex = 0; selectedPoint = -1
                    }, Modifier.testTag("agent_rgb_pattern_layer_remove")) { Text(stringResource(R.string.agent_rgb_pattern_layer_remove)) }
                    if (li < pattern.layers.size - 1) TextButton({ commit(pattern.copy(layers = swap(pattern.layers, li, li + 1))); layerIndex = li + 1 },
                        Modifier.testTag("agent_rgb_pattern_layer_up")) { Text(stringResource(R.string.agent_rgb_pattern_layer_up)) }
                    if (li > 0) TextButton({ commit(pattern.copy(layers = swap(pattern.layers, li, li - 1))); layerIndex = li - 1 },
                        Modifier.testTag("agent_rgb_pattern_layer_down")) { Text(stringResource(R.string.agent_rgb_pattern_layer_down)) }
                }
            }

            RgbSection(R.string.agent_rgb_pattern_group_shape) {
                RgbChips(RgbGenerator.entries, { it == layer.generator }, { generatorLabel(it) }, "agent_rgb_pattern_gen_") { g ->
                    editLayer { it.copy(generator = g) }
                }
                RgbLabel(stringResource(R.string.agent_rgb_pattern_blend))
                RgbChips(RgbBlend.entries, { it == layer.blend }, { blendLabel(it) }, "agent_rgb_pattern_blend_") { b -> editLayer { it.copy(blend = b) } }
            }

            RgbSection(R.string.agent_rgb_pattern_group_colors) {
                StopBar(layer)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    layer.stops.forEachIndexed { i, stop ->
                        val chosen = i == si
                        val label = stringResource(R.string.agent_rgb_pattern_stop_n, i + 1)
                        Spacer(Modifier.size(36.dp).background(Color(stop.color), CircleShape)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClickLabel = label) { if (chosen) colorToPick = i else stopIndex = i }
                            .semantics { role = Role.Button; selected = chosen }
                            .testTag("agent_rgb_pattern_stop_$i"))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ colorToPick = si }, Modifier.testTag("agent_rgb_pattern_stop_color")) {
                        Text(stringResource(R.string.agent_rgb_pattern_stop_color))
                    }
                    if (layer.stops.size < RgbPatternLayer.MAX_STOPS) TextButton({
                        // a new stop half way to the next one (or the end), in the colour found there
                        val here = layer.stops[si]
                        val next = layer.stops.getOrNull(si + 1)?.position ?: 1f
                        val at = (here.position + next) / 2f
                        val color = RgbOwnRenderer.lookup(RgbOwnRenderer.stopTable(layer.stops, false), at)
                        editLayer { it.copy(stops = it.stops + RgbStop(at, color)) }
                        stopIndex = si + 1
                    }, Modifier.testTag("agent_rgb_pattern_stop_add")) { Text(stringResource(R.string.agent_rgb_pattern_stop_add)) }
                    if (layer.stops.size > RgbPatternLayer.MIN_STOPS) TextButton({
                        editLayer { it.copy(stops = it.stops.filterIndexed { i, _ -> i != si }) }
                        stopIndex = (si - 1).coerceAtLeast(0)
                    }, Modifier.testTag("agent_rgb_pattern_stop_remove")) { Text(stringResource(R.string.agent_rgb_pattern_stop_remove)) }
                }
                val stop = layer.stops[si]
                DraftSlider({ stringResource(R.string.agent_rgb_pattern_stop_position, si + 1, (it * 100).roundToInt()) }, stop.position, 0f..1f, 99,
                    "agent_rgb_pattern_stop_position", { v -> dragLayer { l -> l.copy(stops = l.stops.toMutableList().also { s -> s[si] = s[si].copy(position = v) }) } },
                    ::finishDrag)
                RgbChips(RgbPaletteMode.entries, { it == layer.palette }, { paletteLabel(it) }, "agent_rgb_pattern_palette_") { m ->
                    editLayer { it.copy(palette = m) }
                }
            }

            RgbSection(R.string.agent_rgb_pattern_group_motion) {
                DraftSlider({ stringResource(R.string.agent_rgb_pattern_speed, decimal(it)) }, layer.speed, -RgbPatternLayer.MAX_SPEED..RgbPatternLayer.MAX_SPEED,
                    31, "agent_rgb_pattern_speed", { v -> dragLayer { it.copy(speed = v) } }, ::finishDrag)
                DraftSlider({ stringResource(R.string.agent_rgb_pattern_angle, it.roundToInt()) }, layer.angle.toFloat(), 0f..355f, 70,
                    "agent_rgb_pattern_angle", { v -> dragLayer { it.copy(angle = v.roundToInt()) } }, ::finishDrag)
                DraftSlider({ stringResource(R.string.agent_rgb_pattern_scale, decimal(it)) }, layer.scale, RgbPatternLayer.MIN_SCALE..RgbPatternLayer.MAX_SCALE,
                    30, "agent_rgb_pattern_scale", { v -> dragLayer { it.copy(scale = v) } }, ::finishDrag)
                DraftSlider({ stringResource(R.string.agent_rgb_pattern_offset_x, (it * 100).roundToInt()) }, layer.offsetX, -1f..1f, 39,
                    "agent_rgb_pattern_offset_x", { v -> dragLayer { it.copy(offsetX = v) } }, ::finishDrag)
                DraftSlider({ stringResource(R.string.agent_rgb_pattern_offset_y, (it * 100).roundToInt()) }, layer.offsetY, -1f..1f, 39,
                    "agent_rgb_pattern_offset_y", { v -> dragLayer { it.copy(offsetY = v) } }, ::finishDrag)
                DraftSlider({ stringResource(R.string.agent_rgb_pattern_opacity, it.roundToInt()) }, layer.opacity.toFloat(), 0f..100f, 19,
                    "agent_rgb_pattern_opacity", { v -> dragLayer { it.copy(opacity = v.roundToInt()) } }, ::finishDrag)
                RgbLabel(stringResource(R.string.agent_rgb_pattern_curve))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    RgbCurveEditor(
                        curve = layer.curve,
                        selected = selectedPoint.takeIf { it in layer.curve.points.indices } ?: -1,
                        onSelect = { selectedPoint = it },
                        onChange = { curve, done -> if (done) editLayer { it.copy(curve = curve) } else dragLayer { it.copy(curve = curve) } },
                        axisColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        curveColor = MaterialTheme.colorScheme.onSurface,
                        accent = MaterialTheme.colorScheme.primary,
                        removeColor = MaterialTheme.colorScheme.error,
                        description = stringResource(R.string.agent_rgb_curve_description, layer.curve.points.size),
                        xLabel = stringResource(R.string.agent_rgb_axis_x),
                        yLabel = stringResource(R.string.agent_rgb_axis_y),
                    )
                }
                val preset = RgbCurve.presetOf(layer.curve)
                Spacer(Modifier.height(8.dp))
                RgbChips(RgbWaveform.entries, { preset == it }, { waveLabel(it) }, "agent_rgb_pattern_wave_") { w ->
                    editLayer { it.copy(curve = RgbCurve.preset(w)) }
                    selectedPoint = -1
                }
            }

            RgbSection(R.string.agent_rgb_pattern_group_typing) {
                RgbChips(RgbLayerReaction.entries, { it == layer.reaction }, { reactionLabel(it) }, "agent_rgb_pattern_reaction_") { r ->
                    editLayer { it.copy(reaction = r) }
                }
                RgbNote(stringResource(R.string.agent_rgb_pattern_typing_note))
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    colorToPick?.let { index ->
        val stops = layer.stops
        if (index !in stops.indices) {
            colorToPick = null
            return
        }
        ColorPickerDialog(
            onDismissRequest = { colorToPick = null },
            initialColor = stops[index].color,
            title = stringResource(R.string.agent_rgb_pattern_stop_n, index + 1),
            showDefault = false,
            onDefault = {},
        ) { color -> editLayer { it.copy(stops = stops.toMutableList().also { list -> list[index] = list[index].copy(color = color) }) } }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text(stringResource(R.string.agent_rgb_pattern_delete_question, pattern.name)) },
        confirmButton = {
            TextButton({ confirmDelete = false; onDelete() }, Modifier.testTag("agent_rgb_pattern_delete_confirm")) {
                Text(stringResource(R.string.agent_rgb_pattern_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.agent_rgb_pattern_cancel)) } },
    )
}

/**
 * A slider that keeps its value while dragged ([onDrag] goes to the preview only) and reports
 * [onDone] once on release: the editor around it is not recomposed per move.
 */
@Composable
private fun DraftSlider(label: @Composable (Float) -> String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, tag: String,
                        onDrag: (Float) -> Unit, onDone: () -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label(local), style = MaterialTheme.typography.bodyMedium)
        Slider(local.coerceIn(range), { local = it; onDrag(it) }, valueRange = range, steps = steps, onValueChangeFinished = onDone,
            modifier = Modifier.testTag(tag))
    }
}

/** The colour stops of [layer] as the table the light uses (OKLCH blend), with a mark per stop. */
@Composable
private fun StopBar(layer: RgbPatternLayer) {
    val table = remember(layer.stops, layer.curve.cyclic) { RgbOwnRenderer.stopTable(layer.stops, layer.curve.cyclic) }
    val mark = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).testTag("agent_rgb_pattern_stops")) {
        val n = 64
        val w = size.width / n
        for (i in 0 until n) drawRect(Color(RgbOwnRenderer.lookup(table, (i + 0.5f) / n)), Offset(i * w, 0f), Size(w + 1f, size.height))
        for (stop in layer.stops) drawRect(mark, Offset(stop.position * (size.width - 3f), size.height * 0.6f), Size(3f, size.height * 0.4f))
    }
}

private fun <T> swap(list: List<T>, a: Int, b: Int): List<T> = list.toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }

private const val WHITE = 0xFFFFFFFF.toInt()

@Composable
internal fun generatorLabel(g: RgbGenerator) = stringResource(when (g) {
    RgbGenerator.FILL -> R.string.agent_rgb_gen_fill
    RgbGenerator.LINEAR -> R.string.agent_rgb_gen_linear
    RgbGenerator.RADIAL -> R.string.agent_rgb_gen_radial
    RgbGenerator.CONIC -> R.string.agent_rgb_gen_conic
    RgbGenerator.WAVES -> R.string.agent_rgb_gen_waves
    RgbGenerator.PLASMA -> R.string.agent_rgb_gen_plasma
    RgbGenerator.NOISE -> R.string.agent_rgb_gen_noise
    RgbGenerator.STRIPES -> R.string.agent_rgb_gen_stripes
    RgbGenerator.RIPPLE -> R.string.agent_rgb_gen_ripple
    RgbGenerator.SPOTLIGHT -> R.string.agent_rgb_gen_spotlight
    RgbGenerator.CONTOUR -> R.string.agent_rgb_gen_contour
    RgbGenerator.SPARKLE -> R.string.agent_rgb_gen_sparkle
})

@Composable
private fun blendLabel(b: RgbBlend) = stringResource(when (b) {
    RgbBlend.NORMAL -> R.string.agent_rgb_blend_normal
    RgbBlend.ADD -> R.string.agent_rgb_blend_add
    RgbBlend.MULTIPLY -> R.string.agent_rgb_blend_multiply
    RgbBlend.SCREEN -> R.string.agent_rgb_blend_screen
})

@Composable
private fun reactionLabel(r: RgbLayerReaction) = stringResource(when (r) {
    RgbLayerReaction.NONE -> R.string.agent_rgb_reaction_none
    RgbLayerReaction.PULSE -> R.string.agent_rgb_reaction_pulse
    RgbLayerReaction.RIPPLE -> R.string.agent_rgb_reaction_ripple
    RgbLayerReaction.KEY_GLOW -> R.string.agent_rgb_reaction_key_glow
})

@Composable
private fun paletteLabel(m: RgbPaletteMode) = stringResource(when (m) {
    RgbPaletteMode.FIXED -> R.string.agent_rgb_pattern_palette_fixed
    RgbPaletteMode.HUE_CYCLE -> R.string.agent_rgb_pattern_palette_hue
})

/**
 * The own patterns as small still pictures (like the built-in gallery), the chosen one framed.
 * Pictures depend on the pattern and the look's colour options only and are made in the background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OwnGallery(library: RgbPatternLibrary, look: RgbLook, onPick: (RgbOwnPattern) -> Unit) {
    val cache = remember { HashMap<OwnPictureKey, Bitmap>() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        library.patterns.forEach { pattern ->
            val selected = look.usesOwn && look.own?.id == pattern.id
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.width(96.dp).clip(shape).clickable { onPick(pattern) }
                    .semantics { this.selected = selected; role = Role.RadioButton }
                    .testTag("agent_rgb_own_${pattern.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val key = OwnPictureKey(pattern, look.saturation, look.brightness, look.mix, look.reverse)
                val picture by produceState(cache[key], key) {
                    cache[key]?.let { value = it; return@produceState }
                    val made = withContext(Dispatchers.Default) { ownPicture(key) }
                    if (cache.size >= 2 * RgbPatternLibrary.MAX_PATTERNS) cache.clear()
                    cache[key] = made
                    value = made
                }
                Canvas(
                    Modifier.fillMaxWidth().height(54.dp).clip(shape)
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                ) {
                    drawRect(Color.Black)
                    val bitmap = picture ?: return@Canvas
                    drawImage(bitmap.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
                Text(
                    pattern.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }
    }
}

private data class OwnPictureKey(val pattern: RgbOwnPattern, val saturation: Int, val brightness: Int, val mix: Int, val reverse: Boolean)

/** A still picture of an own pattern after a moment of motion: 32 x 18 light values over black (background thread). */
private fun ownPicture(key: OwnPictureKey): Bitmap {
    val engine = RgbEngine(RgbSettings(enabled = true, own = key.pattern, saturation = key.saturation, brightness = key.brightness,
        mix = key.mix, reverse = key.reverse))
    engine.aspect = 54f / 96f
    engine.advance(0)
    var t = 0L
    while (t < 1200) { t += 100; engine.advance(t) }
    val cols = 32
    val rows = 18
    val pixels = IntArray(cols * rows) { i ->
        RgbColors.applyLight(0xFF000000.toInt(), engine.light((i % cols + 0.5f) / cols, (i / cols + 0.5f) / rows, -1, t))
    }
    return Bitmap.createBitmap(pixels, cols, rows, Bitmap.Config.ARGB_8888)
}

/**
 * What the editor's preview shows: the page's settings with [pattern] as the look of [target] for
 * every element (lighting on; with neither keys nor background lit, both are lit for the preview).
 */
internal fun previewWith(settings: RgbSettings, target: RgbTarget, pattern: RgbOwnPattern): RgbSettings {
    val look = settings.lookFor(target).copy(own = pattern)
    var base = settings.copy(enabled = true, separate = false)
    if (!base.lights(RgbTarget.KEYS) && !base.lights(RgbTarget.BACKGROUND))
        base = base.copy(layers = base.layers + RgbLayer.KEY_BACKGROUND + RgbLayer.KEYBOARD_BACKGROUND, areas = base.areas + RgbArea.KEYS)
    return look.applyTo(base)
}

/** Name of the pattern a look draws: its own pattern's name, else the built-in pattern's label. */
@Composable
internal fun lookLabel(look: RgbLook): String = if (look.usesOwn) look.own!!.name else patternLabel(look.pattern)

/** A file name from [name]: letters, digits, space, dash and underscore only. */
internal fun fileName(name: String): String {
    val clean = name.map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }.joinToString("").trim().take(32)
    return "AgentKeyboard-Lichtmuster-${clean.ifEmpty { "Muster" }}.json"
}

@Composable
internal fun errorLabels(): Map<RgbPatternError, String> = RgbPatternError.entries.associateWith { e ->
    stringResource(when (e) {
        RgbPatternError.TOO_LARGE -> R.string.agent_rgb_own_error_too_large
        RgbPatternError.NOT_JSON -> R.string.agent_rgb_own_error_not_json
        RgbPatternError.WRONG_FORMAT -> R.string.agent_rgb_own_error_wrong_format
        RgbPatternError.NEWER_VERSION -> R.string.agent_rgb_own_error_newer
        RgbPatternError.UNKNOWN_FIELD -> R.string.agent_rgb_own_error_unknown_field
        RgbPatternError.BAD_VALUE -> R.string.agent_rgb_own_error_bad_value
        RgbPatternError.NO_LAYERS -> R.string.agent_rgb_own_error_no_layers
        RgbPatternError.TOO_MANY_LAYERS -> R.string.agent_rgb_own_error_too_many_layers
        RgbPatternError.BAD_NAME -> R.string.agent_rgb_own_error_bad_name
    })
}
