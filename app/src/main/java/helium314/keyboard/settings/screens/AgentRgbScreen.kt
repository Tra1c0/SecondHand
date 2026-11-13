// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbCurve
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbEngines
import dev.agentkeyboard.core.RgbFramePacer
import dev.agentkeyboard.core.RgbLayer
import dev.agentkeyboard.core.RgbLook
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTapReaction
import dev.agentkeyboard.core.RgbTarget
import dev.agentkeyboard.core.RgbWaveform
import helium314.keyboard.agent.MyDesigns
import helium314.keyboard.agent.RgbLighting
import helium314.keyboard.agent.RgbTexture
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.settings.preferences.Preference
import java.util.Locale
import kotlin.math.roundToInt

/** How often the option list of [AgentRgbScreen] was composed (tests: frames must not recompose it). */
@VisibleForTesting
internal var optionsCompositions = 0

/** Key of the entry on the appearance page that opens [AgentRgbScreen]. */
const val AGENT_RGB_SETTING = "agent_rgb_lighting_entry"

fun createAgentRgbSetting(context: Context) =
    Setting(context, AGENT_RGB_SETTING, R.string.agent_rgb_title, R.string.agent_rgb_summary) { setting ->
        Preference(
            name = setting.title,
            description = setting.description,
            onClick = { SettingsDestination.navigateTo(SettingsDestination.AgentRgb) },
            modifier = Modifier.testTag(AGENT_RGB_SETTING),
        )
    }

/**
 * "RGB lighting" (stage 26, new look 29a): a large live preview at the top that uses the same
 * engines as the keyboard (taps on it try the tap effects), below it grouped cards with every
 * option of [RgbSettings], saved on each change. Pattern choice as a gallery of small pictures,
 * the own curve (29d) as an editor, and with "Same pattern everywhere" off a look per element.
 * With Android animations off the preview stands still, like the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentRgbScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var settings by remember { mutableStateOf(RgbLighting.read(prefs)) }
    /** Shown immediately; written to the preference unless [commit] is false (a curve point being dragged). */
    fun change(value: RgbSettings, commit: Boolean = true) {
        val normalized = value.normalized()
        settings = normalized
        if (commit) RgbLighting.write(prefs, normalized)
    }
    val design = remember { MyDesigns.newDesign(context, "") }
    val previous = remember { arrayOfNulls<RgbEngines>(1) }
    // a changed option goes on where the light was instead of starting over
    val engines = remember(settings) { RgbEngines(settings).also { e -> previous[0]?.let { e.continueFrom(it) }; previous[0] = e } }
    var time by remember { mutableLongStateOf(0L) }
    val taps = remember(engines) { mutableListOf<Offset>() }
    val animate = settings.enabled && remember { FlatStyle.animationsEnabled(context) }
    LaunchedEffect(engines, animate) {
        if (!animate) return@LaunchedEffect
        val pacer = RgbFramePacer(settings.fps)
        while (true) withFrameNanos { nanos ->
            if (pacer.shouldRender(nanos)) {
                val ms = nanos / 1_000_000
                engines.advance(ms)
                time = ms
            }
        }
    }
    var colorToPick by rememberSaveable { mutableStateOf<Int?>(null) }
    var targetIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPoint by remember { mutableIntStateOf(-1) }
    val target = if (settings.separate) RgbTarget.entries[targetIndex] else RgbTarget.KEYS
    val look = settings.lookFor(target)
    fun changeLook(commit: Boolean = true, edit: (RgbLook) -> RgbLook) = change(settings.withLook(target, edit(settings.lookFor(target))), commit)

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_rgb_title), emptyList()) {
        // the large live preview, fixed above the options
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            ) {
                DesignPreview(design, null,
                    Modifier.testTag("agent_rgb_preview").pointerInput(engines, animate) {
                        detectTapGestures { offset ->
                            if (!animate) return@detectTapGestures
                            taps.add(offset)
                            time = time + 1 // redraw now, even between frames
                        }
                    },
                    // the clock is read inside the preview's drawing only: frames redraw the preview, not the options
                    light = remember(engines) { PreviewLight(engines, { time }, taps) })
            }
            Text(
                stringResource(if (settings.enabled && !animate) R.string.agent_rgb_static_note else R.string.agent_rgb_preview_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .testTag("agent_rgb_options")
        ) {
            SideEffect { optionsCompositions++ }
            SectionCard {
                SwitchRow(stringResource(R.string.agent_rgb_enabled), settings.enabled, "agent_rgb_enabled", strong = true) {
                    change(settings.copy(enabled = it))
                }
            }

            SectionCard(R.string.agent_rgb_group_look) {
                SwitchRow(stringResource(R.string.agent_rgb_same_everywhere), !settings.separate, "agent_rgb_same_everywhere") {
                    change(settings.withSeparate(!it))
                }
                Note(stringResource(R.string.agent_rgb_same_everywhere_summary))
                if (settings.separate) {
                    Spacer(Modifier.height(8.dp))
                    Chips(RgbTarget.entries, { it == target }, { targetLabel(it) }, "agent_rgb_target_") {
                        targetIndex = it.ordinal
                        selectedPoint = -1
                    }
                    if (!settings.lights(target)) Note(stringResource(R.string.agent_rgb_target_off, targetLabel(target)))
                    if (target == RgbTarget.LABELS || target == RgbTarget.SUGGESTIONS || target == RgbTarget.TOOLBAR)
                        Note(stringResource(R.string.agent_rgb_target_point_note))
                }
                Spacer(Modifier.height(12.dp))
                PatternGallery(look, settings.reverse) { changeLook { l -> l.copy(pattern = it) } }
            }

            SectionCard(R.string.agent_rgb_group_wave) {
                Note(stringResource(R.string.agent_rgb_curve_summary))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    RgbCurveEditor(
                        curve = look.curve,
                        selected = selectedPoint.takeIf { it in look.curve.points.indices } ?: -1,
                        onSelect = { selectedPoint = it },
                        onChange = { curve, commit -> changeLook(commit) { it.copy(curve = curve) } },
                        axisColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        curveColor = MaterialTheme.colorScheme.onSurface,
                        accent = MaterialTheme.colorScheme.primary,
                        removeColor = MaterialTheme.colorScheme.error,
                        description = stringResource(R.string.agent_rgb_curve_description, look.curve.points.size),
                        xLabel = stringResource(R.string.agent_rgb_axis_x),
                        yLabel = stringResource(R.string.agent_rgb_axis_y),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val (curve, at) = look.curve.addInWidestGap()
                            if (at >= 0) { changeLook { it.copy(curve = curve) }; selectedPoint = at }
                        },
                        enabled = look.curve.points.size < RgbCurve.MAX_POINTS,
                        modifier = Modifier.testTag("agent_rgb_curve_add"),
                    ) { Text(stringResource(R.string.agent_rgb_curve_add)) }
                    OutlinedButton(
                        onClick = { changeLook { it.copy(curve = it.curve.remove(selectedPoint)) }; selectedPoint = -1 },
                        enabled = look.curve.canRemove(selectedPoint),
                        modifier = Modifier.testTag("agent_rgb_curve_remove"),
                    ) { Text(stringResource(R.string.agent_rgb_curve_remove)) }
                }
                Label(stringResource(R.string.agent_rgb_curve_presets))
                val preset = RgbCurve.presetOf(look.curve)
                Chips(RgbWaveform.entries, { preset == it }, { waveLabel(it) }, "agent_rgb_wave_") { w ->
                    changeLook { it.copy(curve = RgbCurve.preset(w)) }
                    selectedPoint = -1
                }
                if (preset == null) Note(stringResource(R.string.agent_rgb_curve_own))
                RgbCurve.stepCount(look.curve)?.let { steps ->
                    SliderRow(stringResource(R.string.agent_rgb_steps, steps), steps.toFloat(),
                        RgbSettings.STEPS_RANGE.first.toFloat()..RgbSettings.STEPS_RANGE.last.toFloat(),
                        RgbSettings.STEPS_RANGE.last - RgbSettings.STEPS_RANGE.first - 1, "agent_rgb_steps") { v ->
                        changeLook { it.copy(curve = RgbCurve.preset(RgbWaveform.STEPS, v.roundToInt())) }
                    }
                }
            }

            SectionCard(R.string.agent_rgb_group_speed) {
                SliderRow(stringResource(R.string.agent_rgb_period, seconds(look.periodMs)), look.periodMs / 1000f,
                    0.5f..30f, 58, "agent_rgb_period") { v -> changeLook { it.copy(periodMs = (v * 2).roundToInt() * 500) } }
                SwitchRow(stringResource(R.string.agent_rgb_reverse), look.reverse, "agent_rgb_reverse") { v -> changeLook { it.copy(reverse = v) } }
                SliderRow(stringResource(R.string.agent_rgb_width, decimal(look.waveWidth)), look.waveWidth,
                    RgbSettings.WIDTH_MIN..RgbSettings.WIDTH_MAX, 14, "agent_rgb_width") { v ->
                    changeLook { it.copy(waveWidth = (v * 4).roundToInt() / 4f) }
                }
            }

            SectionCard(R.string.agent_rgb_group_colors) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(look.rainbow, { changeLook { it.copy(rainbow = true) } }, { Text(stringResource(R.string.agent_rgb_palette_rainbow)) },
                        Modifier.testTag("agent_rgb_palette_rainbow"))
                    FilterChip(!look.rainbow, { changeLook { it.copy(rainbow = false) } }, { Text(stringResource(R.string.agent_rgb_palette_own)) },
                        Modifier.testTag("agent_rgb_palette_own"))
                }
                if (!look.rainbow) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        look.palette.forEachIndexed { i, color ->
                            val label = stringResource(R.string.agent_rgb_color_n, i + 1)
                            Spacer(Modifier.size(36.dp).background(Color(color), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable(onClickLabel = label) { colorToPick = i }
                                .semantics { role = Role.Button }
                                .testTag("agent_rgb_color_$i"))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (look.palette.size < RgbSettings.MAX_COLORS)
                            TextButton({ changeLook { it.copy(palette = it.palette + it.palette.last()) } },
                                Modifier.testTag("agent_rgb_color_add")) { Text(stringResource(R.string.agent_rgb_color_add)) }
                        if (look.palette.size > RgbSettings.MIN_COLORS)
                            TextButton({ changeLook { it.copy(palette = it.palette.dropLast(1)) } },
                                Modifier.testTag("agent_rgb_color_remove")) { Text(stringResource(R.string.agent_rgb_color_remove)) }
                    }
                }
                SliderRow(stringResource(R.string.agent_rgb_saturation, look.saturation), look.saturation.toFloat(), 0f..100f, 19,
                    "agent_rgb_saturation") { v -> changeLook { it.copy(saturation = v.roundToInt()) } }
                SliderRow(stringResource(R.string.agent_rgb_brightness, look.brightness), look.brightness.toFloat(),
                    RgbSettings.BRIGHTNESS_MIN.toFloat()..100f, 17, "agent_rgb_brightness") { v -> changeLook { it.copy(brightness = v.roundToInt()) } }
                SliderRow(stringResource(R.string.agent_rgb_mix, look.mix), look.mix.toFloat(), 0f..100f, 19,
                    "agent_rgb_mix") { v -> changeLook { it.copy(mix = v.roundToInt()) } }
            }

            SectionCard(R.string.agent_rgb_group_layers) {
                Chips(RgbLayer.entries, { it in settings.layers }, { layerLabel(it) }, "agent_rgb_layer_") {
                    change(settings.copy(layers = settings.layers.toggle(it)))
                }
                Note(stringResource(R.string.agent_rgb_contrast_note))
                SwitchRow(stringResource(R.string.agent_rgb_solid_keys), settings.solidKeys, "agent_rgb_solid_keys") {
                    change(settings.copy(solidKeys = it))
                }
                Note(stringResource(R.string.agent_rgb_solid_keys_summary))
            }

            SectionCard(R.string.agent_rgb_group_areas) {
                Chips(RgbArea.entries, { it in settings.areas }, { areaLabel(it) }, "agent_rgb_area_") {
                    change(settings.copy(areas = settings.areas.toggle(it)))
                }
                Note(stringResource(R.string.agent_rgb_strip_note))
            }

            SectionCard(R.string.agent_rgb_group_tap) {
                Chips(RgbTapReaction.entries, { settings.tapReaction == it }, { tapLabel(it) }, "agent_rgb_tap_") { change(settings.copy(tapReaction = it)) }
                SwitchRow(stringResource(R.string.agent_rgb_reactive), settings.reactiveOnly, "agent_rgb_reactive") { change(settings.copy(reactiveOnly = it)) }
                Note(stringResource(R.string.agent_rgb_reactive_summary))
                SliderRow(stringResource(R.string.agent_rgb_glow, seconds(settings.glowMs)), settings.glowMs / 1000f, 0.2f..5f, 23,
                    "agent_rgb_glow") { change(settings.copy(glowMs = (it * 5).roundToInt() * 200)) }
            }

            SectionCard(R.string.agent_rgb_group_battery) {
                Label(stringResource(R.string.agent_rgb_fps))
                Chips(listOf(30, 60), { settings.fps == it }, { stringResource(R.string.agent_rgb_fps_value, it) }, "agent_rgb_fps_") {
                    change(settings.copy(fps = it))
                }
                SwitchRow(stringResource(R.string.agent_rgb_power_save), settings.pauseInPowerSave, "agent_rgb_power_save") {
                    change(settings.copy(pauseInPowerSave = it))
                }
                Note(stringResource(R.string.agent_rgb_battery_note))
            }
            Spacer(Modifier.size(24.dp))
        }
    }
    colorToPick?.let { index ->
        val palette = look.palette
        if (index !in palette.indices) {
            colorToPick = null
            return
        }
        ColorPickerDialog(
            onDismissRequest = { colorToPick = null },
            initialColor = palette[index],
            title = stringResource(R.string.agent_rgb_color_n, index + 1),
            showDefault = false,
            onDefault = {},
        ) { color -> changeLook { it.copy(palette = palette.toMutableList().also { list -> list[index] = color }) } }
    }
}

/**
 * The patterns as small still pictures in the colours of [look] (made off the frame loop, once per
 * look), the chosen one framed. Each picture is the same light the keyboard draws: grid plus tile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternGallery(look: RgbLook, reverse: Boolean, onPick: (RgbPattern) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RgbPattern.entries.forEach { pattern ->
            val selected = look.pattern == pattern
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.width(96.dp).clip(shape).clickable { onPick(pattern) }
                    .semantics { this.selected = selected; role = Role.RadioButton }
                    .testTag("agent_rgb_pattern_$pattern"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val thumb = remember(look.copy(pattern = pattern), reverse) { thumbnail(look.copy(pattern = pattern)) }
                Canvas(
                    Modifier.fillMaxWidth().height(54.dp).clip(shape)
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                ) {
                    drawRect(Color.Black)
                    drawImage(thumb.first.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    thumb.second?.let { texture -> drawIntoCanvas { texture.drawTileRect(it.nativeCanvas, 0f, 0f, size.width, size.height, 1f) } }
                }
                Text(
                    patternLabel(pattern),
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

/** A still picture of [look] after a moment of motion: 32 x 18 light values; texture tile in thumbnail scale. */
private fun thumbnail(look: RgbLook): Pair<Bitmap, RgbTexture?> {
    val engine = RgbEngine(look.applyTo(RgbSettings(enabled = true)))
    engine.aspect = 54f / 96f
    engine.advance(0)
    var t = 0L
    while (t < 1200) { t += 100; engine.advance(t) }
    val cols = 32
    val rows = 18
    val pixels = IntArray(cols * rows) { i ->
        val light = engine.light((i % cols + 0.5f) / cols, (i / cols + 0.5f) / rows, -1, t)
        dev.agentkeyboard.core.RgbColors.applyLight(0xFF000000.toInt(), light)
    }
    val bitmap = Bitmap.createBitmap(pixels, cols, rows, Bitmap.Config.ARGB_8888)
    val texture = if (dev.agentkeyboard.core.RgbOverlay.hasOverlay(look.pattern)) RgbTexture().also {
        // the tile at the size it has on a phone keyboard, scaled down with the picture
        it.update(engine, 0f, 0f, 96f * 3f, 54f * 3f, t, -1)
    } else null
    return bitmap to texture
}

private fun <T> Set<T>.toggle(item: T) = if (item in this) this - item else this + item

private fun seconds(ms: Int) = decimal(ms / 1000f)
private fun decimal(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    val pattern = when { hundredths % 100 == 0 -> "%.0f"; hundredths % 10 == 0 -> "%.1f"; else -> "%.2f" }
    return String.format(Locale.getDefault(), pattern, hundredths / 100f)
}

/** One group of options as a flat card (29a): title, then the options. */
@Composable
private fun SectionCard(title: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (title != null) Text(
                stringResource(title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(items: List<T>, selected: (T) -> Boolean, label: @Composable (T) -> String, tagPrefix: String, onClick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = selected(item),
                onClick = { onClick(item) },
                label = { Text(label(item)) },
                modifier = Modifier.testTag(tagPrefix + item.toString()),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, tag: String, strong: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, tag: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value.coerceIn(range), onChange, valueRange = range, steps = steps, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun targetLabel(t: RgbTarget) = stringResource(when (t) {
    RgbTarget.KEYS -> R.string.agent_rgb_target_keys
    RgbTarget.BACKGROUND -> R.string.agent_rgb_target_background
    RgbTarget.LABELS -> R.string.agent_rgb_target_labels
    RgbTarget.SUGGESTIONS -> R.string.agent_rgb_target_suggestions
    RgbTarget.TOOLBAR -> R.string.agent_rgb_target_toolbar
})

@Composable
private fun patternLabel(p: RgbPattern) = stringResource(when (p) {
    RgbPattern.WAVE_HORIZONTAL -> R.string.agent_rgb_pattern_wave_horizontal
    RgbPattern.WAVE_VERTICAL -> R.string.agent_rgb_pattern_wave_vertical
    RgbPattern.WAVE_DIAGONAL -> R.string.agent_rgb_pattern_wave_diagonal
    RgbPattern.CIRCLE -> R.string.agent_rgb_pattern_circle
    RgbPattern.BREATHE -> R.string.agent_rgb_pattern_breathe
    RgbPattern.STATIC_RAINBOW -> R.string.agent_rgb_pattern_static
    RgbPattern.INK -> R.string.agent_rgb_pattern_ink
    RgbPattern.DIAMONDS -> R.string.agent_rgb_pattern_diamonds
    RgbPattern.SLATS -> R.string.agent_rgb_pattern_slats
    RgbPattern.HATCH -> R.string.agent_rgb_pattern_hatch
    RgbPattern.LED -> R.string.agent_rgb_pattern_led
    RgbPattern.TOPO -> R.string.agent_rgb_pattern_topo
})

@Composable
private fun waveLabel(w: RgbWaveform) = stringResource(when (w) {
    RgbWaveform.SINE -> R.string.agent_rgb_wave_sine
    RgbWaveform.TRIANGLE -> R.string.agent_rgb_wave_triangle
    RgbWaveform.SAWTOOTH -> R.string.agent_rgb_wave_sawtooth
    RgbWaveform.STEPS -> R.string.agent_rgb_wave_steps
    RgbWaveform.PULSE -> R.string.agent_rgb_wave_pulse
})

@Composable
private fun layerLabel(l: RgbLayer) = stringResource(when (l) {
    RgbLayer.KEY_BACKGROUND -> R.string.agent_rgb_layer_key_background
    RgbLayer.KEYBOARD_BACKGROUND -> R.string.agent_rgb_layer_keyboard_background
    RgbLayer.LABEL -> R.string.agent_rgb_layer_label
})

@Composable
private fun areaLabel(a: RgbArea) = stringResource(when (a) {
    RgbArea.LETTERS -> R.string.agent_rgb_area_letters
    RgbArea.FUNCTIONAL -> R.string.agent_rgb_area_functional
    RgbArea.SPACE -> R.string.agent_rgb_area_space
    RgbArea.NUMBER_ROW -> R.string.agent_rgb_area_number_row
    RgbArea.SUGGESTIONS -> R.string.agent_rgb_area_suggestions
    RgbArea.TOOLBAR -> R.string.agent_rgb_area_toolbar
})

@Composable
private fun tapLabel(t: RgbTapReaction) = stringResource(when (t) {
    RgbTapReaction.NONE -> R.string.agent_rgb_tap_none
    RgbTapReaction.FLASH -> R.string.agent_rgb_tap_flash
    RgbTapReaction.RIPPLE -> R.string.agent_rgb_tap_ripple
    RgbTapReaction.SPEED_UP -> R.string.agent_rgb_tap_speed
})
