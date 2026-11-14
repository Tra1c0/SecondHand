// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.contentDescription
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
import dev.agentkeyboard.core.RgbOwnPattern
import dev.agentkeyboard.core.RgbPatternError
import dev.agentkeyboard.core.RgbPatternLibrary
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTapReaction
import dev.agentkeyboard.core.RgbTarget
import dev.agentkeyboard.core.RgbWaveform
import helium314.keyboard.agent.MyDesigns
import helium314.keyboard.agent.RgbLighting
import helium314.keyboard.agent.RgbOwnPatterns
import helium314.keyboard.agent.RgbTexture
import helium314.keyboard.agent.RgbTiles
import helium314.keyboard.agent.RgbWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.settings.preferences.Preference
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** How often the option list of [AgentRgbScreen] was composed (tests: frames must not recompose it). */
@VisibleForTesting
internal var optionsCompositions = 0

/** Pictures of the pattern gallery made so far (tests: dragging a curve point makes none). */
@VisibleForTesting
internal val galleryPictures = AtomicInteger()

/** The light of the last shown preview (tests compare its pixels with the grid). */
@VisibleForTesting
internal var lastPreviewLight: PreviewLight? = null

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
 * the own curve (29d) as an editor, and with "Same pattern everywhere" off a look per element,
 * the frame rate as two colour areas with a moving light (37, [FrameRateCard]).
 * With Android animations off the preview stands still, like the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentRgbScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    // own patterns of the first build of stage 34 get random ids before anything is read (stage 34 review, M1)
    remember { RgbOwnPatterns.migrate(context) }
    var settings by remember { mutableStateOf(RgbLighting.read(prefs)) }
    // a curve point being dragged: only the preview reads this, so the page is not recomposed per
    // move, and the preview at most once per frame (build-14 lag)
    val draft = remember { mutableStateOf<RgbSettings?>(null) }
    /** Shown immediately; written to the preference unless [commit] is false (a curve point being dragged: preview only). */
    fun change(value: RgbSettings, commit: Boolean = true) {
        val normalized = value.normalized()
        if (!commit) {
            draft.value = normalized
            return
        }
        draft.value = null
        settings = normalized
        RgbLighting.write(prefs, normalized)
    }
    val design = remember { MyDesigns.newDesign(context, "") }
    val animationsOn = remember { FlatStyle.animationsEnabled(context) }
    val animate = settings.enabled && animationsOn
    var colorToPick by rememberSaveable { mutableStateOf<Int?>(null) }
    var targetIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPoint by rememberSaveable { mutableIntStateOf(-1) }
    val target = if (settings.separate) RgbTarget.entries[targetIndex] else RgbTarget.KEYS
    val look = settings.lookFor(target)
    fun changeLook(commit: Boolean = true, edit: (RgbLook) -> RgbLook) = change(settings.withLook(target, edit(settings.lookFor(target))), commit)

    // own patterns (stage 34): the library, the one being edited, the file flows
    var library by remember { mutableStateOf(RgbOwnPatterns.library(prefs)) }
    // stored patterns this version cannot read: kept as they are, nothing is written over them
    var locked by remember { mutableStateOf(RgbOwnPatterns.locked(prefs)) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    val lockedText = stringResource(R.string.agent_rgb_own_locked)
    /**
     * The library changed: stored at once (the stored lighting and design profile follow); the
     * page's settings take the new definitions by id (a deleted one falls back). False while locked.
     */
    fun changeLibrary(changed: RgbPatternLibrary): Boolean {
        if (!RgbOwnPatterns.write(context, changed)) {
            locked = true
            toast(lockedText)
            return false
        }
        library = changed
        settings = settings.refresh(changed)
        return true
    }
    val defaultName = stringResource(R.string.agent_rgb_own_default_name)
    fun open(pattern: RgbOwnPattern) {
        editing = pattern.id
        changeLook { it.copy(own = pattern) }
    }
    val errorText = errorLabels()
    val fullText = stringResource(R.string.agent_rgb_own_full, RgbPatternLibrary.MAX_PATTERNS)
    val importedText = stringResource(R.string.agent_rgb_own_imported, "%s")
    val exportedText = stringResource(R.string.agent_rgb_own_exported, "%s")
    val failedText = stringResource(R.string.agent_rgb_own_failed, "%s")
    val writeFailedText = stringResource(R.string.agent_rgb_own_write_failed)
    val unreadableText = stringResource(R.string.agent_rgb_own_error_unreadable)
    val noAccessText = stringResource(R.string.agent_rgb_own_error_no_access)
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pattern = exportId?.let { library[it] }
        exportId = null
        if (uri == null || pattern == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { RgbOwnPatterns.exportFile(context, uri, pattern) }.isSuccess }
            withContext(Dispatchers.Main) { toast(if (ok) exportedText.format(pattern.name) else writeFailedText) }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { RgbOwnPatterns.importFile(context, uri, defaultName) }.getOrDefault(RgbOwnPatterns.Imported.Unreadable)
            }
            withContext(Dispatchers.Main) {
                library = RgbOwnPatterns.library(prefs)
                toast(when (result) {
                    is RgbOwnPatterns.Imported.Added -> importedText.format(result.pattern.name)
                    is RgbOwnPatterns.Imported.Refused -> failedText.format(errorText.getValue(result.error))
                    RgbOwnPatterns.Imported.Full -> fullText
                    RgbOwnPatterns.Imported.Unreadable -> failedText.format(unreadableText)
                    RgbOwnPatterns.Imported.NoAccess -> failedText.format(noAccessText)
                    RgbOwnPatterns.Imported.Locked -> lockedText
                })
            }
        }
    }
    fun export(pattern: RgbOwnPattern) {
        exportId = pattern.id
        saveFile.launch(fileName(pattern.name))
    }
    // "<name> (Kopie)" for a copy of an own pattern, "<name> (eigen)" for one made from a built-in pattern; the name is cut to fit
    val copyTemplate = stringResource(R.string.agent_rgb_own_copy_name, NAME_SLOT)
    val ownTemplate = stringResource(R.string.agent_rgb_own_from_builtin_name, NAME_SLOT)
    fun copyName(name: String) = RgbOwnPattern.fitName(copyTemplate, name, NAME_SLOT)
    fun ownName(name: String) = RgbOwnPattern.fitName(ownTemplate, name, NAME_SLOT)
    /** A new own pattern from [source] (a built-in look or an own pattern), opened in the editor. */
    fun create(source: RgbOwnPattern) {
        val (changed, made) = library.add(source, defaultName) ?: run { toast(fullText); return }
        if (changeLibrary(changed)) open(made)
    }

    val edited = editing?.let { library[it] }
    if (edited != null) {
        RgbPatternEditor(
            pattern = edited,
            preview = { previewWith(settings, target, it) },
            design = design,
            animate = animationsOn,
            onChange = { changeLibrary(library.replace(it)) },
            onRename = { name -> library.rename(edited.id, name)?.takeIf { changeLibrary(it) }?.get(edited.id)?.name },
            // the current state (a rename just saved by these buttons is already in it)
            onDuplicate = { (library[edited.id] ?: edited).let { create(it.copy(name = copyName(it.name))) } },
            onExport = { export(library[edited.id] ?: edited) },
            onDelete = {
                if (changeLibrary(library.remove(edited.id))) editing = null
            },
            onBack = { editing = null },
        )
        return
    }

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_rgb_title), emptyList()) {
        // the large live preview, fixed above the options
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LivePreview(design, { draft.value ?: settings }, animate)
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
            RgbSection {
                RgbSwitch(stringResource(R.string.agent_rgb_enabled), settings.enabled, "agent_rgb_enabled", strong = true) {
                    change(settings.copy(enabled = it))
                }
            }

            RgbSection(R.string.agent_rgb_group_look) {
                RgbSwitch(stringResource(R.string.agent_rgb_same_everywhere), !settings.separate, "agent_rgb_same_everywhere") {
                    change(settings.withSeparate(!it))
                }
                RgbNote(stringResource(R.string.agent_rgb_same_everywhere_summary))
                if (settings.separate) {
                    Spacer(Modifier.height(8.dp))
                    RgbChips(RgbTarget.entries, { it == target }, { targetLabel(it) }, "agent_rgb_target_") {
                        targetIndex = it.ordinal
                        selectedPoint = -1
                    }
                    if (!settings.lights(target)) RgbNote(stringResource(R.string.agent_rgb_target_off, targetLabel(target)))
                    if (target == RgbTarget.LABELS || target == RgbTarget.SUGGESTIONS || target == RgbTarget.TOOLBAR)
                        RgbNote(stringResource(R.string.agent_rgb_target_point_note))
                }
                Spacer(Modifier.height(12.dp))
                PatternGallery(look, look.reverse) { changeLook { l -> l.copy(pattern = it, own = null) } }
                // own patterns (stage 34): next to the built-in ones, chosen per element the same way
                RgbLabel(stringResource(R.string.agent_rgb_own_title))
                if (locked) RgbNote(lockedText, Modifier.testTag("agent_rgb_own_locked"))
                else if (library.patterns.isEmpty()) RgbNote(stringResource(R.string.agent_rgb_own_empty))
                else OwnGallery(library, look) { p -> changeLook { it.copy(own = p) } }
                if (!locked) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val own = look.own?.takeIf { look.usesOwn }
                    val sourceName = own?.name ?: patternLabel(look.pattern)
                    FilledTonalButton({
                        create(own?.copy(name = copyName(own.name)) ?: RgbOwnPattern.from(look, "", ownName(sourceName)))
                    }, Modifier.testTag("agent_rgb_own_new")) { Text(stringResource(R.string.agent_rgb_own_new, sourceName)) }
                    if (own != null) OutlinedButton({ editing = own.id }, Modifier.testTag("agent_rgb_own_edit")) {
                        Text(stringResource(R.string.agent_rgb_own_edit))
                    }
                    TextButton({ openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                        Modifier.testTag("agent_rgb_own_import")) { Text(stringResource(R.string.agent_rgb_own_import)) }
                }
                if (look.usesOwn) RgbNote(stringResource(R.string.agent_rgb_own_note))
            }

            if (!look.usesOwn) RgbSection(R.string.agent_rgb_group_wave) {
                RgbNote(stringResource(R.string.agent_rgb_curve_summary))
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
                RgbLabel(stringResource(R.string.agent_rgb_curve_presets))
                val preset = RgbCurve.presetOf(look.curve)
                RgbChips(RgbWaveform.entries, { preset == it }, { waveLabel(it) }, "agent_rgb_wave_") { w ->
                    changeLook { it.copy(curve = RgbCurve.preset(w)) }
                    selectedPoint = -1
                }
                if (preset == null) RgbNote(stringResource(R.string.agent_rgb_curve_own))
                RgbCurve.stepCount(look.curve)?.let { steps ->
                    RgbSlider(stringResource(R.string.agent_rgb_steps, steps), steps.toFloat(),
                        RgbSettings.STEPS_RANGE.first.toFloat()..RgbSettings.STEPS_RANGE.last.toFloat(),
                        RgbSettings.STEPS_RANGE.last - RgbSettings.STEPS_RANGE.first - 1, "agent_rgb_steps") { v ->
                        changeLook { it.copy(curve = RgbCurve.preset(RgbWaveform.STEPS, v.roundToInt())) }
                    }
                }
            }

            RgbSection(R.string.agent_rgb_group_speed) {
                RgbSlider(stringResource(R.string.agent_rgb_period, seconds(look.periodMs)), look.periodMs / 1000f,
                    0.5f..30f, 58, "agent_rgb_period") { v -> changeLook { it.copy(periodMs = (v * 2).roundToInt() * 500) } }
                RgbSwitch(stringResource(R.string.agent_rgb_reverse), look.reverse, "agent_rgb_reverse") { v -> changeLook { it.copy(reverse = v) } }
                if (!look.usesOwn) RgbSlider(stringResource(R.string.agent_rgb_width, decimal(look.waveWidth)), look.waveWidth,
                    RgbSettings.WIDTH_MIN..RgbSettings.WIDTH_MAX, 14, "agent_rgb_width") { v ->
                    changeLook { it.copy(waveWidth = (v * 4).roundToInt() / 4f) }
                }
            }

            RgbSection(R.string.agent_rgb_group_colors) {
                if (!look.usesOwn) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(look.rainbow, { changeLook { it.copy(rainbow = true) } }, { Text(stringResource(R.string.agent_rgb_palette_rainbow)) },
                        Modifier.testTag("agent_rgb_palette_rainbow"))
                    FilterChip(!look.rainbow, { changeLook { it.copy(rainbow = false) } }, { Text(stringResource(R.string.agent_rgb_palette_own)) },
                        Modifier.testTag("agent_rgb_palette_own"))
                }
                if (!look.rainbow && !look.usesOwn) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        look.palette.forEachIndexed { i, color ->
                            val label = stringResource(R.string.agent_rgb_color_n, i + 1)
                            ColorSwatch(color, stringResource(R.string.agent_rgb_color_description, i + 1, RgbSettings.hex(color)), label,
                                null, "agent_rgb_color_$i") { colorToPick = i }
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
                RgbSlider(stringResource(R.string.agent_rgb_saturation, look.saturation), look.saturation.toFloat(), 0f..100f, 19,
                    "agent_rgb_saturation") { v -> changeLook { it.copy(saturation = v.roundToInt()) } }
                RgbSlider(stringResource(R.string.agent_rgb_brightness, look.brightness), look.brightness.toFloat(),
                    RgbSettings.BRIGHTNESS_MIN.toFloat()..100f, 17, "agent_rgb_brightness") { v -> changeLook { it.copy(brightness = v.roundToInt()) } }
                RgbSlider(stringResource(R.string.agent_rgb_mix, look.mix), look.mix.toFloat(), 0f..100f, 19,
                    "agent_rgb_mix") { v -> changeLook { it.copy(mix = v.roundToInt()) } }
            }

            RgbSection(R.string.agent_rgb_group_layers) {
                RgbChips(RgbLayer.entries, { it in settings.layers }, { layerLabel(it) }, "agent_rgb_layer_") {
                    change(settings.copy(layers = settings.layers.toggle(it)))
                }
                RgbNote(stringResource(R.string.agent_rgb_contrast_note))
                RgbSwitch(stringResource(R.string.agent_rgb_solid_keys), settings.solidKeys, "agent_rgb_solid_keys") {
                    change(settings.copy(solidKeys = it))
                }
                RgbNote(stringResource(R.string.agent_rgb_solid_keys_summary))
            }

            RgbSection(R.string.agent_rgb_group_areas) {
                RgbChips(RgbArea.entries, { it in settings.areas }, { areaLabel(it) }, "agent_rgb_area_") {
                    change(settings.copy(areas = settings.areas.toggle(it)))
                }
                RgbNote(stringResource(R.string.agent_rgb_strip_note))
            }

            RgbSection(R.string.agent_rgb_group_tap) {
                RgbChips(RgbTapReaction.entries, { settings.tapReaction == it }, { tapLabel(it) }, "agent_rgb_tap_") { change(settings.copy(tapReaction = it)) }
                RgbSwitch(stringResource(R.string.agent_rgb_reactive), settings.reactiveOnly, "agent_rgb_reactive") { change(settings.copy(reactiveOnly = it)) }
                RgbNote(stringResource(R.string.agent_rgb_reactive_summary))
                RgbSlider(stringResource(R.string.agent_rgb_glow, seconds(settings.glowMs)), settings.glowMs / 1000f, 0.2f..5f, 23,
                    "agent_rgb_glow") { change(settings.copy(glowMs = (it * 5).roundToInt() * 200)) }
            }

            // the frame rate as two colour areas with a moving light (stage 37), the battery option below
            FrameRateCard(settings.fps, { change(settings.copy(fps = it)) }) {
                RgbSwitch(stringResource(R.string.agent_rgb_power_save), settings.pauseInPowerSave, "agent_rgb_power_save") {
                    change(settings.copy(pauseInPowerSave = it))
                }
                RgbNote(stringResource(R.string.agent_rgb_battery_note))
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

/** Engines the live preview has made (tests: a dragged curve point makes at most one per frame). */
@VisibleForTesting
internal var previewEngines = 0

/**
 * The large live preview of [AgentRgbScreen]: the same engines and textures as the keyboard; taps
 * try the tap effects. [shown] is read here only, so a dragged curve point (a draft) recomposes the
 * preview, at most once per frame, and not the page. The textures stay across changes (their grids
 * come from the background, [RgbTexture]), so a change shows without a blank frame.
 */
@Composable
internal fun LivePreview(design: MyDesigns.Design, shown: () -> RgbSettings, animate: Boolean) {
    val settings = shown()
    val previous = remember { arrayOfNulls<RgbEngines>(1) }
    // a changed option goes on where the light was instead of starting over
    val engines = remember(settings) {
        previewEngines++
        RgbEngines(settings).also { e -> previous[0]?.let { e.continueFrom(it) }; previous[0] = e }
    }
    var time by remember { mutableLongStateOf(0L) }
    val taps = remember(engines) { mutableListOf<Offset>() }
    val fps = settings.fps
    LaunchedEffect(engines, animate) {
        if (!animate) return@LaunchedEffect
        val pacer = RgbFramePacer(fps)
        while (true) withFrameNanos { nanos ->
            if (pacer.shouldRender(nanos)) {
                val ms = nanos / 1_000_000
                engines.advance(ms)
                time = ms
            }
        }
    }
    // a grid or tile made in the background: redraw a still preview (running frames redraw anyway)
    var made by remember { mutableIntStateOf(0) }
    val still by androidx.compose.runtime.rememberUpdatedState(!animate)
    DisposableEffect(Unit) {
        val listener: () -> Unit = { if (still) made++ }
        RgbWork.addListener(listener)
        onDispose {
            RgbWork.removeListener(listener)
            lastPreviewLight = null
        }
    }
    val background = remember { RgbTexture() }
    val keys = remember { RgbTexture() }
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
            light = remember(engines) { PreviewLight(engines, { made; time }, taps, background, keys).also { lastPreviewLight = it } })
    }
}

/**
 * The patterns as small still pictures in the colours of [look], the chosen one framed. Each picture
 * is the same light the keyboard draws (grid plus tile). The pictures depend on the pattern and the
 * colours only ([GalleryKey]: not on the curve, the speed or the wave width), are made on a
 * background thread after the colours have rested for a moment (a slider being dragged makes none)
 * and are kept for the page; until a new one is ready the old one stays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternGallery(look: RgbLook, reverse: Boolean, onPick: (RgbPattern) -> Unit) {
    val cache = remember { HashMap<GalleryKey, GalleryPicture>() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RgbPattern.entries.forEach { pattern ->
            val selected = !look.usesOwn && look.pattern == pattern
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.width(96.dp).clip(shape).clickable { onPick(pattern) }
                    .semantics { this.selected = selected; role = Role.RadioButton }
                    .testTag("agent_rgb_pattern_$pattern"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val key = GalleryKey(pattern, look.rainbow, look.palette, look.saturation, look.brightness, look.mix, reverse)
                val thumb by produceState(cache[key], key) {
                    cache[key]?.let { value = it; return@produceState }
                    // the first pictures at once, later ones once the colours rest
                    if (value != null) delay(GALLERY_DEBOUNCE_MS)
                    val made = withContext(Dispatchers.Default) { galleryPicture(key) }
                    if (cache.size >= GALLERY_KEEP) cache.clear()
                    cache[key] = made
                    value = made
                }
                Canvas(
                    Modifier.fillMaxWidth().height(54.dp).clip(shape)
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
                ) {
                    drawRect(Color.Black)
                    val picture = thumb ?: return@Canvas
                    drawImage(picture.field.asImageBitmap(), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    picture.texture?.let { texture -> drawIntoCanvas { texture.drawTileRect(it.nativeCanvas, 0f, 0f, size.width, size.height, 1f) } }
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

/** What a gallery picture depends on: the pattern and the colours. */
private data class GalleryKey(val pattern: RgbPattern, val rainbow: Boolean, val palette: List<Int>, val saturation: Int,
                              val brightness: Int, val mix: Int, val reverse: Boolean)

private class GalleryPicture(val field: Bitmap, val texture: RgbTexture?)

internal const val GALLERY_DEBOUNCE_MS = 250L
/** Twelve patterns: two sets of colours. */
private const val GALLERY_KEEP = 24

/**
 * A still picture of [key] after a moment of motion (default curve, speed and width): 32 x 18
 * light values; texture tile in thumbnail scale. Background thread only (the tile may be made here).
 */
private fun galleryPicture(key: GalleryKey): GalleryPicture {
    galleryPictures.incrementAndGet()
    val look = RgbLook(pattern = key.pattern, reverse = key.reverse, rainbow = key.rainbow, palette = key.palette,
        saturation = key.saturation, brightness = key.brightness, mix = key.mix)
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
    val texture = if (dev.agentkeyboard.core.RgbOverlay.hasOverlay(key.pattern)) RgbTexture().also {
        // the tile at the size it has on a phone keyboard, scaled down with the picture
        it.update(engine, 0f, 0f, 96f * 3f, 54f * 3f, t, -1, wait = true)
    } else null
    return GalleryPicture(bitmap, texture)
}

private fun <T> Set<T>.toggle(item: T) = if (item in this) this - item else this + item

private fun seconds(ms: Int) = decimal(ms / 1000f)
internal fun decimal(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    val pattern = when { hundredths % 100 == 0 -> "%.0f"; hundredths % 10 == 0 -> "%.1f"; else -> "%.2f" }
    return String.format(Locale.getDefault(), pattern, hundredths / 100f)
}

/** One group of options as a flat card (29a): title, then the options. */
@Composable
internal fun RgbSection(title: Int? = null, content: @Composable ColumnScope.() -> Unit) {
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
internal fun <T> RgbChips(items: List<T>, selected: (T) -> Boolean, label: @Composable (T) -> String, tagPrefix: String, onClick: (T) -> Unit) {
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
internal fun RgbLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
internal fun RgbNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp))
}

/**
 * A colour as a 36 dp circle in a 48 dp touch target, named for TalkBack by [description]
 * ("Farbe 2, #FF0050"); [chosen] (null: nothing to choose) draws a thick ring and reports it as selected.
 */
@Composable
internal fun ColorSwatch(color: Int, description: String, clickLabel: String, chosen: Boolean?, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = clickLabel, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button; if (chosen != null) selected = chosen }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.size(36.dp).background(Color(color), CircleShape)
            .border(if (chosen == true) 3.dp else 1.dp, if (chosen == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
    }
}

/** Stands for the name in a name template ("%1$s (Kopie)"): a private use character no name can hold. */
internal const val NAME_SLOT = "\uE000"

@Composable
internal fun RgbSwitch(label: String, checked: Boolean, tag: String, strong: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun RgbSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, tag: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value.coerceIn(range), onChange, valueRange = range, steps = steps, modifier = Modifier.testTag(tag))
    }
}

@Composable
internal fun targetLabel(t: RgbTarget) = stringResource(when (t) {
    RgbTarget.KEYS -> R.string.agent_rgb_target_keys
    RgbTarget.BACKGROUND -> R.string.agent_rgb_target_background
    RgbTarget.LABELS -> R.string.agent_rgb_target_labels
    RgbTarget.SUGGESTIONS -> R.string.agent_rgb_target_suggestions
    RgbTarget.TOOLBAR -> R.string.agent_rgb_target_toolbar
})

@Composable
internal fun patternLabel(p: RgbPattern) = stringResource(patternLabelRes(p))

/** The label of a built-in pattern (also for the Diagnose page, outside Compose). */
fun patternLabelRes(p: RgbPattern) = when (p) {
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
}

@Composable
internal fun waveLabel(w: RgbWaveform) = stringResource(when (w) {
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
