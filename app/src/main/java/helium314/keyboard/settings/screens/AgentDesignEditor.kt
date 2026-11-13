// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbLayer
import helium314.keyboard.agent.DesignLook
import helium314.keyboard.agent.RgbKeyPainter
import helium314.keyboard.compat.locale
import helium314.keyboard.agent.MyDesigns
import helium314.keyboard.agent.MyDesigns.Design
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.utils.IntentUtils
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/** Keeps the edited design across configuration changes (it holds no secrets, only look values). */
val designSaver = Saver<Design, String>(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString(it) })

/**
 * Design editor (stage 23f2): every change shows at once in [DesignPreview] above the controls;
 * nothing is written before "Save and use". A picked image waits in a pending file until then.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentDesignEditor(initial: Design, isNew: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var design by rememberSaveable(stateSaver = designSaver) { mutableStateOf(initial) }
    var pendingImage by rememberSaveable { mutableStateOf(false) }
    var imageVersion by remember { mutableIntStateOf(0) }
    var colorToPick by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var imageError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pendingFile = remember { MyDesigns.pendingImageFile(context) }
    fun close() {
        pendingFile.delete()
        onClose()
    }
    fun requestClose() {
        if (design != initial || pendingImage) confirmDiscard = true else close()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    pendingFile.parentFile?.mkdirs()
                    FileUtils.copyContentUriToNewFile(uri, context, pendingFile)
                    MyDesigns.isImage(pendingFile)
                }.getOrDefault(false)
            }
            if (ok) {
                pendingImage = true
                design = design.copy(hasImage = true)
                imageVersion++
            } else {
                pendingFile.delete()
                imageError = true
            }
        }
    }
    val imageFile = if (pendingImage) pendingFile else MyDesigns.imageFile(context, design.id)
    val image = remember(design.hasImage, pendingImage, imageVersion) {
        if (design.hasImage) decodePreviewImage(imageFile) else null
    }
    val title = if (isNew) stringResource(R.string.agent_design_new) else stringResource(R.string.agent_design_edit)
    SearchSettingsScreen(::requestClose, title, emptyList()) {
        DesignPreview(design, image, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .testTag("agent_design_editor")
        ) {
            OutlinedTextField(
                value = design.name,
                onValueChange = { design = design.copy(name = it.take(40)) },
                label = { Text(stringResource(R.string.agent_design_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("agent_design_name"),
            )

            GroupTitle(R.string.agent_design_group_shape)
            Text(stringResource(R.string.agent_design_style), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MyDesigns.STYLES.forEach { style ->
                    FilterChip(
                        selected = design.style == style,
                        onClick = { design = design.copy(style = style) },
                        label = { Text(stringResource(styleLabel(style))) },
                        modifier = Modifier.testTag("agent_design_style_$style"),
                    )
                }
            }
            if (design.style != KeyboardTheme.STYLE_FLAT)
                SwitchRow(R.string.agent_design_borders, design.keyBorders, "agent_design_borders") {
                    design = design.copy(keyBorders = it)
                }
            SwitchRow(R.string.agent_design_corner_own, design.cornerRadius != null, "agent_design_corner_own") {
                design = design.copy(cornerRadius = if (it) defaultCornerRadius(design.style) else null)
            }
            design.cornerRadius?.let { radius ->
                SliderRow(stringResource(R.string.agent_design_corner, radius.roundToInt()), radius, MyDesigns.CORNER_RANGE,
                    steps = MyDesigns.CORNER_RANGE.endInclusive.toInt() - 1, tag = "agent_design_corner") {
                    design = design.copy(cornerRadius = it.roundToInt().toFloat())
                }
            }
            SliderRow(stringResource(R.string.agent_design_gap, (design.keyGap * 100).roundToInt()), design.keyGap,
                MyDesigns.GAP_RANGE, steps = 19, tag = "agent_design_gap") { design = design.copy(keyGap = round2(it)) }
            SliderRow(stringResource(R.string.agent_design_height, (design.keyHeight * 100).roundToInt()), design.keyHeight,
                MyDesigns.HEIGHT_RANGE, steps = 19, tag = "agent_design_height") { design = design.copy(keyHeight = round2(it)) }

            GroupTitle(R.string.agent_design_group_colors)
            val colors = remember(design) { MyDesigns.colors(context, design) }
            MyDesigns.COLOR_NAMES.forEach { name ->
                val shown = design.color(name) ?: colors.get(colorTypeOf(name))
                Row(
                    Modifier.fillMaxWidth().clickable { colorToPick = name }.padding(vertical = 8.dp)
                        .testTag("agent_design_color_$name"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(36.dp).background(Color(shown), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    Text(stringResource(colorLabel(name)), Modifier.padding(start = 16.dp))
                }
            }

            GroupTitle(R.string.agent_design_group_font)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + DesignLook.FONTS).forEach { font ->
                    FilterChip(
                        selected = design.font == font,
                        onClick = { design = design.copy(font = font) },
                        label = { Text(stringResource(fontLabel(font))) },
                        modifier = Modifier.testTag("agent_design_font_${font ?: "style"}"),
                    )
                }
            }

            GroupTitle(R.string.agent_design_group_image)
            Text(stringResource(R.string.agent_design_image_note), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    launcher.launch(IntentUtils.getResolvableTypeIntent(context, "image/*").setAction(Intent.ACTION_OPEN_DOCUMENT))
                }, modifier = Modifier.testTag("agent_design_image_pick")) { Text(stringResource(R.string.agent_design_image_pick)) }
                if (design.hasImage)
                    OutlinedButton(onClick = {
                        pendingFile.delete()
                        pendingImage = false
                        design = design.copy(hasImage = false)
                    }, modifier = Modifier.testTag("agent_design_image_remove")) { Text(stringResource(R.string.agent_design_image_remove)) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val saved = MyDesigns.save(context, design.copy(name = design.name.trim()), if (pendingImage) pendingFile else null)
                        // save already re-selects a design that is in use
                        if (MyDesigns.selectedId(context.prefs()) != saved.id) MyDesigns.select(context, saved)
                        close()
                    },
                    enabled = design.name.isNotBlank(),
                    modifier = Modifier.testTag("agent_design_save"),
                ) { Text(stringResource(R.string.agent_design_save)) }
                TextButton(onClick = ::requestClose) { Text(stringResource(R.string.agent_design_cancel)) }
            }
            if (!isNew)
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(bottom = 24.dp).testTag("agent_design_delete")) {
                    Text(stringResource(R.string.agent_design_delete), color = MaterialTheme.colorScheme.error)
                }
        }
    }
    colorToPick?.let { name ->
        val colors = MyDesigns.colors(context, design)
        ColorPickerDialog(
            onDismissRequest = { colorToPick = null },
            initialColor = design.color(name) ?: colors.get(colorTypeOf(name)),
            title = stringResource(colorLabel(name)),
            showDefault = false,
            onDefault = {},
        ) { color -> design = design.copy(colors = design.colors + (name to color)) }
    }
    if (confirmDelete)
        ConfirmationDialog(
            onDismissRequest = { confirmDelete = false },
            onConfirmed = {
                MyDesigns.delete(context, design.id)
                close()
            },
            content = { Text(stringResource(R.string.agent_design_delete_confirm, initial.name)) },
            confirmButtonText = stringResource(R.string.delete),
        )
    if (confirmDiscard)
        ConfirmationDialog(
            onDismissRequest = { confirmDiscard = false },
            onConfirmed = ::close,
            content = { Text(stringResource(R.string.agent_design_discard)) },
        )
    if (imageError) InfoDialog(stringResource(R.string.agent_design_image_error)) { imageError = false }
    // a pending image from an editor that was closed by the system does not belong to this one
    LaunchedEffect(Unit) { if (!pendingImage) pendingFile.delete() }
}

private fun round2(value: Float) = (value * 20).roundToInt() / 20f

@Composable
private fun GroupTitle(res: Int) {
    Text(
        stringResource(res),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(res: Int, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(res), Modifier.weight(1f))
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

private fun styleLabel(style: String) = when (style) {
    KeyboardTheme.STYLE_MATERIAL -> R.string.agent_design_style_material
    KeyboardTheme.STYLE_FLAT -> R.string.agent_design_style_flat
    else -> R.string.agent_design_style_rounded
}

private fun fontLabel(font: String?) = when (font) {
    DesignLook.FONT_REGULAR -> R.string.agent_design_font_regular
    DesignLook.FONT_MEDIUM -> R.string.agent_design_font_medium
    DesignLook.FONT_BOLD -> R.string.agent_design_font_bold
    else -> R.string.agent_design_font_style
}

private fun colorLabel(name: String) = when (name) {
    KeyboardTheme.COLOR_BACKGROUND -> R.string.select_color_background
    KeyboardTheme.COLOR_KEYS -> R.string.select_color_key_background
    KeyboardTheme.COLOR_FUNCTIONAL_KEYS -> R.string.agent_design_color_functional
    KeyboardTheme.COLOR_TEXT -> R.string.select_color_key
    KeyboardTheme.COLOR_HINT_TEXT -> R.string.select_color_key_hint
    else -> R.string.agent_design_color_accent
}

private fun colorTypeOf(name: String) = when (name) {
    KeyboardTheme.COLOR_BACKGROUND -> ColorType.MAIN_BACKGROUND
    KeyboardTheme.COLOR_KEYS -> ColorType.KEY_BACKGROUND
    KeyboardTheme.COLOR_FUNCTIONAL_KEYS -> ColorType.FUNCTIONAL_KEY_BACKGROUND
    KeyboardTheme.COLOR_TEXT -> ColorType.KEY_TEXT
    KeyboardTheme.COLOR_HINT_TEXT -> ColorType.KEY_HINT_TEXT
    else -> ColorType.ACTION_KEY_BACKGROUND
}

/** Corner radius of the key shapes of each style in dp, as in their drawables. */
fun defaultCornerRadius(style: String): Float = when (style) {
    KeyboardTheme.STYLE_ROUNDED -> 10f
    KeyboardTheme.STYLE_FLAT -> 6f
    else -> 5f
}

/** Decodes [file] small enough for the preview (about 1000 px wide). */
private fun decodePreviewImage(file: File): ImageBitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1000) sample *= 2
    return runCatching {
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

/** What the preview draws, read from the same Colors object the keyboard would use. */
data class PreviewLook(
    val background: Int, val strip: Int, val key: Int, val functional: Int, val space: Int, val accent: Int,
    val label: Int, val functionalLabel: Int, val hint: Int, val spaceLabel: Int, val actionIcon: Int, val suggestion: Int,
    val keyBackgrounds: Boolean, val style: String, val cornerRadius: Float?, val gap: Float, val height: Float,
    val weight: FontWeight,
) {
    companion object {
        fun of(context: Context, design: Design): PreviewLook {
            val colors = MyDesigns.colors(context, design)
            val borders = design.style == KeyboardTheme.STYLE_FLAT || design.keyBorders
            return PreviewLook(
                background = colors.get(ColorType.MAIN_BACKGROUND),
                strip = colors.get(ColorType.STRIP_BACKGROUND),
                key = colors.get(ColorType.KEY_BACKGROUND),
                functional = colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND),
                space = colors.get(ColorType.SPACE_BAR_BACKGROUND),
                accent = colors.get(ColorType.ACTION_KEY_BACKGROUND),
                label = colors.get(ColorType.KEY_TEXT),
                functionalLabel = colors.get(ColorType.FUNCTIONAL_KEY_TEXT),
                hint = colors.get(ColorType.KEY_HINT_TEXT),
                spaceLabel = colors.get(ColorType.SPACE_BAR_TEXT),
                actionIcon = colors.get(ColorType.ACTION_KEY_ICON),
                suggestion = colors.get(ColorType.SUGGESTED_WORD),
                keyBackgrounds = borders,
                style = design.style,
                cornerRadius = design.cornerRadius,
                gap = design.keyGap,
                height = design.keyHeight,
                weight = when (design.font) {
                    DesignLook.FONT_REGULAR -> FontWeight.Normal
                    DesignLook.FONT_MEDIUM -> FontWeight.Medium
                    DesignLook.FONT_BOLD -> FontWeight.Bold
                    else -> if (design.style == KeyboardTheme.STYLE_FLAT) FontWeight.Medium else FontWeight.Normal
                },
            )
        }
    }
}

private const val STRIP_DP = 40
private const val ROW_DP = 50
private const val PADDING_DP = 6

/** Height of the preview for a key height scale. */
fun previewHeight(heightScale: Float): Dp = (STRIP_DP + PADDING_DP * 2 + ROW_DP * 4 * heightScale).dp

/**
 * Live preview of a design: suggestion strip, four rows of a German layout with hints on the top
 * row, shift and delete as function keys, a space bar with the language and Enter in the accent
 * colour. Colours come from [PreviewLook.of]; shapes, gaps, height and font from the design.
 */
@Composable
fun DesignPreview(design: Design, image: ImageBitmap?, modifier: Modifier = Modifier, light: PreviewLight? = null) {
    val context = LocalContext.current
    val look = remember(design) { PreviewLook.of(context, design) }
    val measurer = rememberTextMeasurer()
    val description = stringResource(R.string.agent_design_preview)
    val configuration = LocalConfiguration.current
    val language = remember(configuration) { configuration.locale().let { it.getDisplayLanguage(it) } }
    Canvas(
        modifier.fillMaxWidth().height(previewHeight(look.height))
            .testTag("agent_design_preview")
            .semantics { contentDescription = description }
    ) { drawKeyboard(look, image, measurer, language, light) }
}

/**
 * RGB light for the preview (stage 26): the same engine and colour rules as the keyboard
 * ([helium314.keyboard.agent.RgbKeyPainter]). [pendingTaps] are taps in preview pixels; drawing
 * hands each to the engine with the key under it and removes it. [timeMs] is read only while
 * drawing, so an animation clock in a Compose state only redraws the preview and does not
 * recompose the page around it.
 */
class PreviewLight(val engine: RgbEngine, val timeMs: () -> Long, val pendingTaps: MutableList<Offset> = mutableListOf()) {
    val settings get() = engine.settings
    fun lit(area: RgbArea) = settings.enabled && area in settings.areas
}

private class PreviewKey(val label: String, val width: Float, val kind: Kind, val hint: String? = null) {
    enum class Kind { LETTER, FUNCTIONAL, PILL, SPACE, ENTER }
}

private val ROWS = listOf(
    "qwertzuiopü".map { PreviewKey(it.toString(), 1f, PreviewKey.Kind.LETTER) }
        .mapIndexed { i, k -> if (i < 10) PreviewKey(k.label, 1f, k.kind, ((i + 1) % 10).toString()) else k },
    "asdfghjklöä".map { PreviewKey(it.toString(), 1f, PreviewKey.Kind.LETTER) },
    listOf(PreviewKey("⇧", 1.5f, PreviewKey.Kind.FUNCTIONAL)) + "yxcvbnm".map { PreviewKey(it.toString(), 1f, PreviewKey.Kind.LETTER) } +
        PreviewKey("⌫", 1.5f, PreviewKey.Kind.FUNCTIONAL),
    listOf(
        PreviewKey("?123", 1.5f, PreviewKey.Kind.PILL), PreviewKey(",", 1f, PreviewKey.Kind.LETTER),
        PreviewKey("", 5f, PreviewKey.Kind.SPACE), PreviewKey(".", 1f, PreviewKey.Kind.LETTER),
        PreviewKey("↵", 1.5f, PreviewKey.Kind.ENTER),
    ),
)

private fun DrawScope.drawKeyboard(look: PreviewLook, image: ImageBitmap?, measurer: TextMeasurer, language: String,
                                   light: PreviewLight? = null) {
    // not laid out yet (or squeezed to nothing): positions would divide by zero
    if (size.width <= 0f || size.height <= 0f) return
    drawRect(Color(look.background))
    if (image != null) drawCover(image)
    else drawRect(Color(look.strip), size = Size(size.width, STRIP_DP.dp.toPx()))
    val stripHeight = STRIP_DP.dp.toPx()
    val keysHeight = (size.height - stripHeight).coerceAtLeast(1f)
    val engine = light?.engine
    val now = light?.timeMs?.invoke() ?: 0L
    engine?.aspect = keysHeight / size.width
    val keyboardLayer = light != null && light.settings.enabled && RgbLayer.KEYBOARD_BACKGROUND in light.settings.layers
    val backgroundLayer = light != null && light.settings.enabled && RgbLayer.KEY_BACKGROUND in light.settings.layers
    val labelLayer = light != null && light.settings.enabled && RgbLayer.LABEL in light.settings.layers
    if (engine != null && keyboardLayer) {
        // the same coarse grid as the keyboard, here as rectangles
        val cols = RgbKeyPainter.GRID_COLUMNS
        val rows = (cols * keysHeight / size.width).roundToInt().coerceIn(2, RgbKeyPainter.GRID_MAX_ROWS)
        val cw = size.width / cols
        val ch = keysHeight / rows
        for (r in 0 until rows) for (c in 0 until cols) {
            val value = engine.light((c + 0.5f) / cols, (r + 0.5f) / rows, -1, now)
            if (RgbColors.alpha(value) > 0) drawRect(Color(value), Offset(c * cw, stripHeight + r * ch), Size(cw + 0.5f, ch + 0.5f))
        }
    }
    // strip: three suggestions
    listOf("Hallo", "danke", "gern").forEachIndexed { i, word ->
        val center = size.width * (i * 2 + 1) / 6f
        val wordColor = if (engine != null && light.lit(RgbArea.SUGGESTIONS))
            RgbColors.label(look.suggestion, engine.light(center / size.width, 0f, -1, now), true, look.strip)
        else look.suggestion
        val layout = measurer.measure(word, TextStyle(color = Color(wordColor), fontSize = 15.sp))
        drawText(layout, topLeft = Offset(center - layout.size.width / 2f, (stripHeight - layout.size.height) / 2f))
    }
    val padding = PADDING_DP.dp.toPx()
    val rowHeight = (size.height - stripHeight - padding * 2) / ROWS.size
    // gaps as in HeliBoard: a share of the key width and row height, scaled by the gap setting
    val hGap = size.width * 0.018f * look.gap
    val vGap = rowHeight * 0.11f * look.gap
    val unit = (size.width - padding * 2) / 11f
    val ownRadius = look.cornerRadius?.dp?.toPx()
    val styleRadius = defaultCornerRadius(look.style).dp.toPx()
    ROWS.forEachIndexed { rowIndex, row ->
        val rowWidth = row.sumOf { it.width.toDouble() }.toFloat() * unit
        var x = (size.width - rowWidth) / 2
        val top = stripHeight + padding + rowIndex * rowHeight
        row.forEachIndexed { keyIndex, key ->
            val rect = Rect(x + hGap / 2, top + vGap / 2, x + key.width * unit - hGap / 2, top + rowHeight - vGap / 2)
            x += key.width * unit
            val keyId = rowIndex * 100 + keyIndex
            val cx = rect.center.x / size.width
            val cy = (rect.center.y - stripHeight) / keysHeight
            light?.pendingTaps?.removeAll { tap ->
                val hit = tap.x >= rect.left - hGap / 2 && tap.x < rect.right + hGap / 2 && tap.y >= rect.top - vGap / 2 && tap.y < rect.bottom + vGap / 2
                if (hit) engine?.tap(cx, cy, keyId, now)
                hit
            }
            val area = when (key.kind) {
                PreviewKey.Kind.SPACE -> RgbArea.SPACE
                PreviewKey.Kind.LETTER -> if (key.label == "," || key.label == ".") RgbArea.FUNCTIONAL else RgbArea.LETTERS
                else -> RgbArea.FUNCTIONAL
            }
            val inArea = engine != null && light.lit(area)
            val fillLit = inArea && backgroundLayer
            val labelLit = inArea && labelLayer
            // as on the keyboard: labels stay readable whenever something behind them is lit
            val keyLit = fillLit || labelLit || keyboardLayer
            val keyLight = if (fillLit || labelLit) engine!!.light(cx, cy, keyId, now) else 0
            val pill = look.style == KeyboardTheme.STYLE_FLAT && (key.kind == PreviewKey.Kind.PILL || key.kind == PreviewKey.Kind.ENTER)
            val radius = if (pill) rect.height / 2 else ownRadius ?: styleRadius
            val fill = when (key.kind) {
                PreviewKey.Kind.LETTER -> look.key
                PreviewKey.Kind.FUNCTIONAL, PreviewKey.Kind.PILL -> look.functional
                PreviewKey.Kind.SPACE -> look.space
                PreviewKey.Kind.ENTER -> look.accent
            }
            val shown = look.keyBackgrounds || key.kind == PreviewKey.Kind.ENTER
            val themeFill = if (shown) fill else android.graphics.Color.TRANSPARENT
            val litFill = if (fillLit) RgbColors.applyLight(themeFill, keyLight) else themeFill
            // without key backgrounds only Enter keeps its accent shape, as in the keyboard (unless lit)
            if (shown || fillLit) {
                val enterRadius = if (!look.keyBackgrounds) rect.height / 2 else radius
                drawRoundRect(Color(litFill), rect.topLeft, rect.size, CornerRadius(enterRadius, enterRadius))
            }
            val themeColor = when (key.kind) {
                PreviewKey.Kind.ENTER -> look.actionIcon
                PreviewKey.Kind.FUNCTIONAL, PreviewKey.Kind.PILL -> look.functionalLabel
                PreviewKey.Kind.SPACE -> look.spaceLabel
                PreviewKey.Kind.LETTER -> look.label
            }
            val keyboardBack = if (engine != null && keyboardLayer)
                RgbColors.applyLight(look.background or 0xFF000000.toInt(), engine.light(cx, cy, -1, now)) else look.background
            val color = if (keyLit) RgbColors.label(themeColor, keyLight, labelLit, RgbColors.over(litFill, keyboardBack)) else themeColor
            val text = if (key.kind == PreviewKey.Kind.SPACE) language else key.label
            val fontSize = when (key.kind) {
                PreviewKey.Kind.SPACE, PreviewKey.Kind.PILL -> rowHeight * 0.26f
                else -> rowHeight * 0.42f
            }
            drawLabel(measurer, text, rect, color, fontSize, look.weight)
            key.hint?.let { hint ->
                val hintColor = if (keyLit) RgbColors.label(look.hint, keyLight, labelLit, RgbColors.over(litFill, keyboardBack)) else look.hint
                val layout = measurer.measure(hint, TextStyle(color = Color(hintColor), fontSize = (rowHeight * 0.2f).toSp(), fontWeight = look.weight))
                drawText(layout, topLeft = Offset(rect.right - layout.size.width - rect.width * 0.12f, rect.top + rect.height * 0.04f))
            }
        }
    }
    // a tap between the keys or on the strip still starts ripples and the reactive light
    light?.pendingTaps?.let { taps ->
        taps.forEach { engine?.tap(it.x / size.width, (it.y - stripHeight) / keysHeight, -1, now) }
        taps.clear()
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, rect: Rect, color: Int, fontSize: Float, weight: FontWeight) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, TextStyle(color = Color(color), fontSize = fontSize.toSp(), fontWeight = weight), maxLines = 1)
    val left = rect.left + (rect.width - layout.size.width) / 2
    val top = rect.top + (rect.height - layout.size.height) / 2
    clipRect(rect.left, rect.top, rect.right, rect.bottom) { drawText(layout, topLeft = Offset(left, top)) }
}

/** Draws [image] stretched over the whole preview, as the keyboard view draws its background drawable. */
private fun DrawScope.drawCover(image: ImageBitmap) {
    drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
}
