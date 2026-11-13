// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbFramePacer
import dev.agentkeyboard.core.RgbLayer
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTapReaction
import dev.agentkeyboard.core.RgbWaveform
import helium314.keyboard.agent.MyDesigns
import helium314.keyboard.agent.RgbLighting
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
 * "RGB lighting" (stage 26): every option of [RgbSettings], saved on each change, with a live
 * preview at the top that uses the same engine as the keyboard. Taps on the preview try the tap
 * effects. With Android animations off the preview stands still, like the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentRgbScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var settings by remember { mutableStateOf(RgbLighting.read(prefs)) }
    fun change(value: RgbSettings) {
        val normalized = value.normalized()
        settings = normalized
        RgbLighting.write(prefs, normalized)
    }
    val design = remember { MyDesigns.newDesign(context, "") }
    val engine = remember(settings) { RgbEngine(settings) }
    var time by remember { mutableLongStateOf(0L) }
    val taps = remember(engine) { mutableListOf<Offset>() }
    val animate = settings.enabled && remember { FlatStyle.animationsEnabled(context) }
    LaunchedEffect(engine, animate) {
        if (!animate) return@LaunchedEffect
        val pacer = RgbFramePacer(settings.fps)
        while (true) withFrameNanos { nanos ->
            if (pacer.shouldRender(nanos)) {
                val ms = nanos / 1_000_000
                engine.advance(ms)
                time = ms
            }
        }
    }
    var colorToPick by rememberSaveable { mutableStateOf<Int?>(null) }

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_rgb_title), emptyList()) {
        DesignPreview(design, null,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("agent_rgb_preview").pointerInput(engine, animate) {
                detectTapGestures { offset ->
                    if (!animate) return@detectTapGestures
                    taps.add(offset)
                    time = time + 1 // redraw now, even between frames
                }
            },
            // the clock is read inside the preview's drawing only: frames redraw the preview, not the options
            light = remember(engine) { PreviewLight(engine, { time }, taps) })
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .testTag("agent_rgb_options")
        ) {
            SideEffect { optionsCompositions++ }
            SwitchRow(stringResource(R.string.agent_rgb_enabled), settings.enabled, "agent_rgb_enabled") { change(settings.copy(enabled = it)) }
            Note(stringResource(if (settings.enabled && !animate) R.string.agent_rgb_static_note else R.string.agent_rgb_preview_hint))

            Group(R.string.agent_rgb_group_pattern)
            Chips(RgbPattern.entries, { settings.pattern == it }, { patternLabel(it) }, "agent_rgb_pattern_") { change(settings.copy(pattern = it)) }

            Group(R.string.agent_rgb_group_wave)
            Chips(RgbWaveform.entries, { settings.waveform == it }, { waveLabel(it) }, "agent_rgb_wave_") { change(settings.copy(waveform = it)) }
            if (settings.waveform == RgbWaveform.STEPS)
                SliderRow(stringResource(R.string.agent_rgb_steps, settings.steps), settings.steps.toFloat(),
                    RgbSettings.STEPS_RANGE.first.toFloat()..RgbSettings.STEPS_RANGE.last.toFloat(),
                    RgbSettings.STEPS_RANGE.last - RgbSettings.STEPS_RANGE.first - 1, "agent_rgb_steps") {
                    change(settings.copy(steps = it.roundToInt()))
                }

            Group(R.string.agent_rgb_group_speed)
            SliderRow(stringResource(R.string.agent_rgb_period, seconds(settings.periodMs)), settings.periodMs / 1000f,
                0.5f..30f, 58, "agent_rgb_period") { change(settings.copy(periodMs = (it * 2).roundToInt() * 500)) }
            SwitchRow(stringResource(R.string.agent_rgb_reverse), settings.reverse, "agent_rgb_reverse") { change(settings.copy(reverse = it)) }
            SliderRow(stringResource(R.string.agent_rgb_width, decimal(settings.waveWidth)), settings.waveWidth,
                RgbSettings.WIDTH_MIN..RgbSettings.WIDTH_MAX, 14, "agent_rgb_width") {
                change(settings.copy(waveWidth = (it * 4).roundToInt() / 4f))
            }

            Group(R.string.agent_rgb_group_colors)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.rainbow, { change(settings.copy(rainbow = true)) }, { Text(stringResource(R.string.agent_rgb_palette_rainbow)) },
                    Modifier.testTag("agent_rgb_palette_rainbow"))
                FilterChip(!settings.rainbow, { change(settings.copy(rainbow = false)) }, { Text(stringResource(R.string.agent_rgb_palette_own)) },
                    Modifier.testTag("agent_rgb_palette_own"))
            }
            if (!settings.rainbow) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    settings.palette.forEachIndexed { i, color ->
                        val label = stringResource(R.string.agent_rgb_color_n, i + 1)
                        Spacer(Modifier.size(36.dp).background(Color(color), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClickLabel = label) { colorToPick = i }
                            .semantics { role = Role.Button }
                            .testTag("agent_rgb_color_$i"))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (settings.palette.size < RgbSettings.MAX_COLORS)
                        TextButton({ change(settings.copy(palette = settings.palette + settings.palette.last())) },
                            Modifier.testTag("agent_rgb_color_add")) { Text(stringResource(R.string.agent_rgb_color_add)) }
                    if (settings.palette.size > RgbSettings.MIN_COLORS)
                        TextButton({ change(settings.copy(palette = settings.palette.dropLast(1))) },
                            Modifier.testTag("agent_rgb_color_remove")) { Text(stringResource(R.string.agent_rgb_color_remove)) }
                }
            }
            SliderRow(stringResource(R.string.agent_rgb_saturation, settings.saturation), settings.saturation.toFloat(), 0f..100f, 19,
                "agent_rgb_saturation") { change(settings.copy(saturation = it.roundToInt())) }
            SliderRow(stringResource(R.string.agent_rgb_brightness, settings.brightness), settings.brightness.toFloat(),
                RgbSettings.BRIGHTNESS_MIN.toFloat()..100f, 17, "agent_rgb_brightness") { change(settings.copy(brightness = it.roundToInt())) }
            SliderRow(stringResource(R.string.agent_rgb_mix, settings.mix), settings.mix.toFloat(), 0f..100f, 19,
                "agent_rgb_mix") { change(settings.copy(mix = it.roundToInt())) }

            Group(R.string.agent_rgb_group_layers)
            Chips(RgbLayer.entries, { it in settings.layers }, { layerLabel(it) }, "agent_rgb_layer_") {
                change(settings.copy(layers = settings.layers.toggle(it)))
            }
            Note(stringResource(R.string.agent_rgb_contrast_note))

            Group(R.string.agent_rgb_group_areas)
            Chips(RgbArea.entries, { it in settings.areas }, { areaLabel(it) }, "agent_rgb_area_") {
                change(settings.copy(areas = settings.areas.toggle(it)))
            }
            Note(stringResource(R.string.agent_rgb_strip_note))

            Group(R.string.agent_rgb_group_tap)
            Chips(RgbTapReaction.entries, { settings.tapReaction == it }, { tapLabel(it) }, "agent_rgb_tap_") { change(settings.copy(tapReaction = it)) }
            SwitchRow(stringResource(R.string.agent_rgb_reactive), settings.reactiveOnly, "agent_rgb_reactive") { change(settings.copy(reactiveOnly = it)) }
            Note(stringResource(R.string.agent_rgb_reactive_summary))
            SliderRow(stringResource(R.string.agent_rgb_glow, seconds(settings.glowMs)), settings.glowMs / 1000f, 0.2f..5f, 23,
                "agent_rgb_glow") { change(settings.copy(glowMs = (it * 5).roundToInt() * 200)) }

            Group(R.string.agent_rgb_group_battery)
            Text(stringResource(R.string.agent_rgb_fps), style = MaterialTheme.typography.bodyMedium)
            Chips(listOf(30, 60), { settings.fps == it }, { stringResource(R.string.agent_rgb_fps_value, it) }, "agent_rgb_fps_") {
                change(settings.copy(fps = it))
            }
            SwitchRow(stringResource(R.string.agent_rgb_power_save), settings.pauseInPowerSave, "agent_rgb_power_save") {
                change(settings.copy(pauseInPowerSave = it))
            }
            Note(stringResource(R.string.agent_rgb_battery_note))
            Spacer(Modifier.size(24.dp))
        }
    }
    colorToPick?.let { index ->
        val palette = settings.palette
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
        ) { color -> change(settings.copy(palette = palette.toMutableList().also { it[index] = color })) }
    }
}

private fun <T> Set<T>.toggle(item: T) = if (item in this) this - item else this + item

private fun seconds(ms: Int) = decimal(ms / 1000f)
private fun decimal(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    val pattern = when { hundredths % 100 == 0 -> "%.0f"; hundredths % 10 == 0 -> "%.1f"; else -> "%.2f" }
    return String.format(Locale.getDefault(), pattern, hundredths / 100f)
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
private fun Group(res: Int) {
    Text(
        stringResource(res),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
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
private fun patternLabel(p: RgbPattern) = stringResource(when (p) {
    RgbPattern.WAVE_HORIZONTAL -> R.string.agent_rgb_pattern_wave_horizontal
    RgbPattern.WAVE_VERTICAL -> R.string.agent_rgb_pattern_wave_vertical
    RgbPattern.WAVE_DIAGONAL -> R.string.agent_rgb_pattern_wave_diagonal
    RgbPattern.CIRCLE -> R.string.agent_rgb_pattern_circle
    RgbPattern.BREATHE -> R.string.agent_rgb_pattern_breathe
    RgbPattern.STATIC_RAINBOW -> R.string.agent_rgb_pattern_static
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
