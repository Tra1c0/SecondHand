// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTarget
import helium314.keyboard.agent.DesignTiles
import helium314.keyboard.agent.MyDesigns
import helium314.keyboard.agent.RgbLighting
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.CustomFontPreference
import helium314.keyboard.settings.preferences.KeyboardScalePreference
import helium314.keyboard.settings.preferences.SwitchPreference
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Stage 38b: the area page "Aussehen", 1:1 after docs/referenzen/einstellungen-mockup/Aussehen.dc.html.
 * Every row shows the real value and opens the existing upstream dialog or preference rows; the
 * two switches and the RGB switch write the real preferences. What has no row of its own (style,
 * icon style, key borders, colours, custom icons, space bar text, bottom row height) stays on the
 * old appearance page behind "Alle Aussehen-Einstellungen".
 */

@Composable
fun AgentAreaLookScreen(onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    // recompose when a preference changes elsewhere (SettingsActivity counts) or here (P121)
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    var written by remember { mutableIntStateOf(0) }
    if ((changed?.value ?: 0) + written < 0) Log.v("irrelevant", "read both counters to recompose on a change")
    // id of the design in the editor, NEW_DESIGN for a new one, null while the page is shown
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    editing?.let { id ->
        val newName = stringResource(R.string.agent_design_new_name, MyDesigns.list(prefs).size + 1)
        val initial = remember(id) { if (id == NEW_DESIGN) MyDesigns.newDesign(context, newName) else MyDesigns.get(prefs, id) }
        if (initial == null) {
            editing = null
            return
        }
        // the existing editor, as on the design page (it has no navigation target of its own)
        AgentDesignEditor(initial, isNew = id == NEW_DESIGN) {
            editing = null
            written++
        }
        return
    }

    val area = SettingsArea.LOOK
    val settings = remember { createAppearanceSettings(context).associateBy { it.key } }
    fun bump() { written++ }
    AgentSettingsTheme {
        Column(
            Modifier.fillMaxSize().background(AgentSettingsTokens.Background).windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(start = AgentSettingsTokens.PagePadding, end = AgentSettingsTokens.PagePadding, top = 16.dp, bottom = 32.dp)
                .testTag(AREA_TAG + area.route),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackHeader(onClickBack)
            AreaHero(area)
            DesignsCard(prefs, written + (changed?.value ?: 0), onEdit = { editing = it }, onPicked = ::bump,
                onAllDesigns = { onNavigate(SettingsDestination.AgentDesign) })
            // the sections read the preferences themselves: the version makes them recompose after a change
            val version = written + (changed?.value ?: 0)
            RgbCard(prefs, version, onOpen = { onNavigate(SettingsDestination.AgentRgb) }, onChanged = ::bump)
            SizeSection(context, prefs, settings, version, ::bump)
            FontSection(context, prefs, settings, version)
            BackgroundSection(context, prefs, settings, version, onNavigate, ::bump)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Designs

/** One tile of the design grid: an own design or a built-in tile of [DesignTiles]. */
private class LookTile(val tag: String, val name: String, val from: Color, val to: Color, val selected: Boolean,
                       val select: () -> Unit, val editId: String?)

@Composable
private fun DesignsCard(prefs: SharedPreferences, version: Int, onEdit: (String) -> Unit, onPicked: () -> Unit, onAllDesigns: () -> Unit) {
    val context = LocalContext.current
    val look = DesignTiles.look(context)
    val designs = remember(version) { MyDesigns.list(prefs) }
    val ownSelected = remember(version) { MyDesigns.selectedId(prefs) }
    val tileSelected = remember(version) { DesignTiles.selectedId(prefs) }
    val sections = remember(version) { DesignTiles.sections(prefs) }
    val swatches = remember(version, look) {
        sections.flatMap { it.second }.associate { it.id to DesignTiles.swatches(context, it) }
    }
    val ownColors = remember(version, look) {
        designs.associate { d ->
            val c = MyDesigns.colors(context, d)
            d.id to (c.get(ColorType.MAIN_BACKGROUND) to c.get(ColorType.ACTION_KEY_BACKGROUND))
        }
    }
    val tiles = buildList {
        designs.forEach { d ->
            val (bg, accent) = ownColors.getValue(d.id)
            add(LookTile(d.themeName, d.name, Color(bg), Color(accent), d.id == ownSelected,
                { if (d.id != ownSelected) { MyDesigns.select(context, d); onPicked() } }, d.id))
        }
        sections.forEach { (_, list) ->
            list.forEach { tile ->
                val s = swatches.getValue(tile.id)
                // one swatch: background to accent; day/night: day background to night background
                val (from, to) = if (s.size == 1) Color(s[0].background) to Color(s[0].accent)
                    else Color(s[0].background) to Color(s[1].background)
                add(LookTile(tile.id, tileName(tile), from, to, ownSelected == null && tile.id == tileSelected,
                    { if (tile.id != tileSelected || ownSelected != null) { DesignTiles.select(context, tile); onPicked() } }, null))
            }
        }
    }
    // two rows as in the mockup (own designs first, the chosen tile always among them); every tile
    // with the colour sets of HeliBoard's editor stays on the design page behind "Alle Designs"
    val selectedIndex = tiles.indexOfFirst { it.selected }
    val shown = if (selectedIndex < SHOWN) tiles.take(SHOWN) else tiles.take(SHOWN - 1) + tiles[selectedIndex]

    FeatureCard(stringResource(R.string.agent_look_designs), hint = stringResource(R.string.agent_look_designs_hint), gap = 14.dp) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.chunked(COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { DesignTileButton(it, Modifier.weight(1f), onEdit) }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        PillButton(stringResource(R.string.agent_look_all_designs, tiles.size), light = false, enabled = true,
            modifier = Modifier.fillMaxWidth().testTag(AREA_ROW_TAG + SettingsDestination.AgentDesign)) { onAllDesigns() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.agent_look_new_design), light = true, enabled = designs.size < MyDesigns.MAX_DESIGNS,
                modifier = Modifier.weight(1f).testTag(LOOK_NEW_TAG)) { onEdit(NEW_DESIGN) }
            // edits the chosen own design; built-in looks are not editable (a new design starts from them)
            PillButton(stringResource(R.string.agent_look_edit_design), light = false, enabled = ownSelected != null,
                modifier = Modifier.weight(1f).testTag(LOOK_EDIT_TAG)) { ownSelected?.let(onEdit) }
        }
    }
}

@Composable
private fun tileName(tile: DesignTiles.Tile): String = when (tile) {
    is DesignTiles.Tile.Flat -> stringResource(R.string.agent_design_flat)
    is DesignTiles.Tile.System -> stringResource(R.string.agent_design_system)
    is DesignTiles.Tile.Colors -> tile.theme.getStringResourceOrName("theme_name_", LocalContext.current)
}

/** Tile of the mockup: inset with a 2 dp border (area colour when chosen), 58 dp swatch at 160°, name. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesignTileButton(tile: LookTile, modifier: Modifier, onEdit: (String) -> Unit) {
    val t = AgentSettingsTokens
    val shape = RoundedCornerShape(20.dp)
    val editLabel = tile.editId?.let { stringResource(R.string.agent_design_edit_named, tile.name) }
    Column(
        modifier.clip(shape).background(t.Inset).border(2.dp, if (tile.selected) SettingsArea.LOOK.to else TILE_BORDER, shape)
            .combinedClickable(
                role = Role.RadioButton,
                onLongClickLabel = editLabel,
                onLongClick = tile.editId?.let { id -> { onEdit(id) } },
                onClick = tile.select,
            )
            .semantics { selected = tile.selected }
            .padding(8.dp).testTag(LOOK_TILE_TAG + tile.tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(14.dp)).background(cssGradient(160f, tile.from, tile.to)))
        Text(tile.name, style = AgentSettingsType.Small.copy(fontWeight = FontWeight.SemiBold), color = t.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The two buttons under the grid: light ("+ Neues Design") or outlined ("Bearbeiten"), 44 dp high. */
@Composable
private fun PillButton(text: String, light: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val t = AgentSettingsTokens
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.heightIn(min = t.MinTouch).widthIn(min = t.MinTouch).alpha(if (enabled) 1f else 0.4f).clip(shape)
            .then(if (light) Modifier.background(t.Text) else Modifier.border(1.dp, t.ChipBorder, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AgentSettingsType.Button,
            color = if (light) t.OnGradient else t.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------
// RGB light

@Composable
private fun RgbCard(prefs: SharedPreferences, version: Int, onOpen: () -> Unit, onChanged: () -> Unit) {
    val t = AgentSettingsTokens
    val area = SettingsArea.LOOK
    val rgb = remember(version) { RgbLighting.read(prefs) }
    val title = stringResource(R.string.agent_look_rgb)
    val shape = RoundedCornerShape(t.CardRadius)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(t.Card).border(1.dp, t.Border, shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.agent_settings_open, title), onClick = onOpen)
            .testTag(AREA_ROW_TAG + SettingsDestination.AgentRgb),
    ) {
        RgbBanner(Modifier.fillMaxWidth().height(96.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = AgentSettingsType.SectionTitle, color = t.Text)
                Text(rgbSummary(rgb), style = AgentSettingsType.Label, color = t.Muted, modifier = Modifier.testTag(LOOK_RGB_SUMMARY_TAG))
            }
            GradientSwitch(rgb.enabled, { on ->
                // only "enabled" changes, written normalised as on the start page (P121)
                RgbLighting.write(prefs, rgb.copy(enabled = on).normalized())
                onChanged()
            }, area.from, area.to, Modifier.testTag(LOOK_RGB_SWITCH_TAG), stringResource(R.string.agent_look_rgb_switch))
        }
    }
}

/** "Höhenlinien · 30 Hz · Tasten & Hintergrund": pattern of the keys, frame rate, what is lit when on. */
@Composable
private fun rgbSummary(rgb: RgbSettings): String {
    val lit = RgbTarget.entries.filter { rgb.copy(enabled = true).lights(it) }.map { targetLabel(it) }
    val targets = when (lit.size) {
        0 -> stringResource(R.string.agent_look_rgb_nothing)
        1 -> lit[0]
        else -> stringResource(R.string.agent_look_and, lit.dropLast(1).joinToString(", "), lit.last())
    }
    return stringResource(R.string.agent_look_rgb_summary, patternLabel(rgb.lookFor(RgbTarget.KEYS).pattern), rgb.fps, targets)
}

/** The banner of the mockup: thin white stripes at 115° over the light gradient at 110°. */
@Composable
private fun RgbBanner(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(RGB_GRADIENT)
        val period = 14.dp.toPx()
        val line = 2.dp.toPx() / period
        val rad = Math.toRadians(115.0)
        val dir = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
        drawRect(Brush.linearGradient(
            0f to STRIPE, line to STRIPE, line to Color.Transparent, 1f to Color.Transparent,
            start = Offset.Zero, end = dir * period, tileMode = TileMode.Repeated,
        ))
    }
}

// ---------------------------------------------------------------------------------------------
// Rows

@Composable
private fun SizeSection(context: Context, prefs: SharedPreferences, settings: Map<String, Setting>, version: Int, onChanged: () -> Unit) {
    // read the version: Compose ignores unused parameters when it decides to skip (P124)
    check(version >= 0)
    SectionCard(stringResource(R.string.agent_look_size)) {
        val dims = listOf(stringResource(R.string.landscape), stringResource(R.string.folded))
        val height = stringResource(R.string.agent_look_height)
        KeyboardScalePreference(height, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, dims, Defaults.PREF_KEYBOARD_HEIGHT_SCALE,
            0.3f..1.5f, { "${(100 * it).toInt()}%" },
            row = { open -> SettingRow(height, stringResource(R.string.agent_look_height_sub), DOTS[0], open,
                Modifier.testTag(LOOK_ROW_TAG + ROW_HEIGHT), percent(prefs.getFloat(MyDesigns.HEIGHT_KEY, 1f))) },
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
        val gap = stringResource(R.string.agent_look_gap)
        KeyboardScalePreference(gap, Settings.PREF_KEY_GAP_SCALE_PREFIX, dims, Defaults.PREF_KEY_GAP_SCALE,
            0.5f..2.5f, { "${(100 * it).toInt()}%" },
            row = { open -> SettingRow(gap, stringResource(R.string.agent_look_gap_sub), DOTS[1], open,
                Modifier.testTag(LOOK_ROW_TAG + ROW_GAP), String.format(locale(context), "%.1f", prefs.getFloat(MyDesigns.GAP_KEY, 1f))) },
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
        GroupRow(ROW_PADDING, stringResource(R.string.agent_look_padding), stringResource(R.string.agent_look_padding_sub), DOTS[2],
            if (customized(prefs, Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, Settings.PREF_SIDE_PADDING_SCALE_PREFIX))
                stringResource(R.string.agent_look_value_custom) else stringResource(R.string.agent_look_value_default)) {
            settings.show(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, Settings.PREF_SIDE_PADDING_SCALE_PREFIX)
        }
        // the same switches as the upstream dialog of "Geteilte Tastatur", plus the spacer when one is on
        val split = splitPrefs()
        val on = split.filter { prefs.getBoolean(it.first, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) }
        GroupRow(ROW_SPLIT, stringResource(R.string.agent_look_split), stringResource(R.string.agent_look_split_sub), DOTS[3],
            if (on.isEmpty()) stringResource(R.string.agent_look_value_off) else on.joinToString(", ") { it.second }) {
            split.forEach { SwitchPreference(name = it.second, key = it.first, default = Defaults.PREF_ENABLE_SPLIT_KEYBOARD) { onChanged() } }
            if (split.any { prefs.getBoolean(it.first, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) })
                settings.show(Settings.PREF_SPLIT_SPACER_SCALE_PREFIX)
        }
    }
}

@Composable
private fun FontSection(context: Context, prefs: SharedPreferences, settings: Map<String, Setting>, version: Int) {
    // read the version: Compose ignores unused parameters when it decides to skip (P124)
    check(version >= 0)
    SectionCard(stringResource(R.string.agent_look_font)) {
        GroupRow(ROW_FONT_SIZE, stringResource(R.string.agent_look_font_size), stringResource(R.string.agent_look_font_size_sub), DOTS[0],
            percent(prefs.getFloat(Settings.PREF_FONT_SCALE, Defaults.PREF_FONT_SCALE))) {
            settings.show(Settings.PREF_FONT_SCALE)
            if (prefs.getBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)) settings.show(Settings.PREF_HINT_FONT_SCALE)
        }
        val fontFile = Settings.getCustomFontFile(context)
        val font = stringResource(R.string.agent_look_custom_font)
        settings[SettingsWithoutKey.CUSTOM_FONT]?.let { setting ->
            // the upstream flow: picker at once, or "load / delete" when a font is there
            CustomFontPreference(setting, fontFile, R.string.custom_font) { open ->
                SettingRow(font, stringResource(R.string.agent_look_custom_font_sub), DOTS[1], open, Modifier.testTag(LOOK_ROW_TAG + ROW_CUSTOM_FONT),
                    stringResource(if (fontFile.exists()) R.string.agent_look_value_own_font else R.string.agent_look_value_none_font))
            }
        }
        val emojiCustom = Settings.getCustomEmojiFontFile(context).exists()
            || prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE) != Defaults.PREF_EMOJI_FONT_SCALE
            || prefs.getString(Settings.PREF_EMOJI_SKIN_TONE, Defaults.PREF_EMOJI_SKIN_TONE) != Defaults.PREF_EMOJI_SKIN_TONE
            || prefs.getBoolean(Settings.PREF_EMOJI_KEY_FIT, Defaults.PREF_EMOJI_KEY_FIT) != Defaults.PREF_EMOJI_KEY_FIT
        GroupRow(ROW_EMOJI, stringResource(R.string.agent_look_emoji), stringResource(R.string.agent_look_emoji_sub), DOTS[3],
            stringResource(if (emojiCustom) R.string.agent_look_value_custom else R.string.agent_look_value_default)) {
            // the same conditions as on the old appearance page
            settings.show(SettingsWithoutKey.CUSTOM_EMOJI_FONT, Settings.PREF_EMOJI_FONT_SCALE)
            if (prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE) != 1f) settings.show(Settings.PREF_EMOJI_KEY_FIT)
            if (prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0) >= 24) settings.show(Settings.PREF_EMOJI_SKIN_TONE)
        }
    }
}

@Composable
private fun BackgroundSection(context: Context, prefs: SharedPreferences, settings: Map<String, Setting>, version: Int,
                              onNavigate: (String) -> Unit, onChanged: () -> Unit) {
    // read the version: Compose ignores unused parameters when it decides to skip (P124)
    check(version >= 0)
    val area = SettingsArea.LOOK
    SectionCard(stringResource(R.string.agent_look_background)) {
        val image = listOf(false, true).any { night -> listOf(false, true).any { land -> Settings.getCustomBackgroundFile(context, night, land).exists() } }
        GroupRow(ROW_IMAGE, stringResource(R.string.agent_look_image), stringResource(R.string.agent_look_image_sub), DOTS[0],
            stringResource(if (image) R.string.agent_look_value_own_image else R.string.agent_look_value_none_image)) {
            settings.show(SettingsWithoutKey.BACKGROUND_IMAGE, SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE)
        }
        // day/night exists from Android 9 on, as on the old page
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            SettingSwitchRow(stringResource(R.string.agent_look_day_night), stringResource(R.string.agent_look_day_night_sub), DOTS[1],
                prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT), { on ->
                    prefs.edit { putBoolean(Settings.PREF_THEME_DAY_NIGHT, on) }
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                    onChanged()
                }, area.from, area.to, Modifier.testTag(LOOK_ROW_TAG + ROW_DAY_NIGHT))
        SettingSwitchRow(stringResource(R.string.agent_look_navbar), stringResource(R.string.agent_look_navbar_sub), DOTS[3],
            prefs.getBoolean(Settings.PREF_NAVBAR_COLOR, Defaults.PREF_NAVBAR_COLOR), { on ->
                prefs.edit { putBoolean(Settings.PREF_NAVBAR_COLOR, on) }
                onChanged()
            }, area.from, area.to, Modifier.testTag(LOOK_ROW_TAG + ROW_NAVBAR))
        SettingRow(stringResource(R.string.agent_look_all), stringResource(R.string.agent_look_all_sub), DOTS[2],
            { onNavigate(SettingsDestination.Appearance) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.Appearance))
    }
}

/** A row that opens a dialog with the upstream preference rows of [content] (each opens its own dialog). */
@Composable
private fun GroupRow(tag: String, title: String, summary: String, dot: Color, value: String, content: @Composable () -> Unit) {
    var show by rememberSaveable { mutableStateOf(false) }
    SettingRow(title, summary, dot, { show = true }, Modifier.testTag(LOOK_ROW_TAG + tag), value)
    if (show)
        ThreeButtonAlertDialog(
            onDismissRequest = { show = false },
            onConfirmed = {},
            confirmButtonText = null,
            cancelButtonText = stringResource(R.string.dialog_close),
            title = { Text(title) },
            content = { Column(Modifier.testTag(LOOK_DIALOG_TAG + tag)) { content() } },
        )
}

@Composable
private fun Map<String, Setting>.show(vararg keys: String) = keys.forEach { this[it]?.Preference() }

@Composable
private fun splitPrefs(): List<Pair<String, String>> = listOfNotNull(
    Settings.PREF_ENABLE_SPLIT_KEYBOARD to stringResource(R.string.button_default),
    Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE to stringResource(R.string.landscape),
    if (!FoldableUtils.isFoldable) null else Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED to stringResource(R.string.folded),
    if (!FoldableUtils.isFoldable) null else
        Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED_LANDSCAPE to stringResource(R.string.folded) + " / " + stringResource(R.string.landscape),
)

/** True if a value of one of these scale settings differs from its default (the dialog removes defaults). */
private fun customized(prefs: SharedPreferences, vararg prefixes: String) =
    prefs.all.keys.any { key -> prefixes.any { key.startsWith(it + "_") } }

@Composable
private fun percent(value: Float) = stringResource(R.string.agent_look_value_percent, (100 * value).roundToInt())

private fun locale(context: Context): Locale = context.resources.configuration.locales[0]

private const val COLUMNS = 3
private const val SHOWN = 6
private const val NEW_DESIGN = "+"
private val TILE_BORDER = Color(0xFF26262D)
private val STRIPE = Color.White.copy(alpha = 0.18f)
/** Dots of the rows, as in the mockup: from pink to amber. */
private val DOTS = listOf(Color(0xFFFF6B8A), Color(0xFFFF8A6B), Color(0xFFFFA35C), Color(0xFFFFB547))
private val RGB_GRADIENT = cssGradient(110f, listOf(
    0f to Color(0xFF00C2A8), 0.4f to Color(0xFF3D7BFF), 0.65f to Color(0xFF8B5CF6), 0.85f to Color(0xFFFF4D8D), 1f to Color(0xFFFFB547),
))

const val LOOK_TILE_TAG = "agent_look_tile_"
const val LOOK_NEW_TAG = "agent_look_new"
const val LOOK_EDIT_TAG = "agent_look_edit"
const val LOOK_RGB_SWITCH_TAG = "agent_look_rgb_switch"
const val LOOK_RGB_SUMMARY_TAG = "agent_look_rgb_summary"
const val LOOK_ROW_TAG = "agent_look_row_"
const val LOOK_DIALOG_TAG = "agent_look_dialog_"
const val ROW_HEIGHT = "height"
const val ROW_GAP = "gap"
const val ROW_PADDING = "padding"
const val ROW_SPLIT = "split"
const val ROW_FONT_SIZE = "font_size"
const val ROW_CUSTOM_FONT = "custom_font"
const val ROW_EMOJI = "emoji"
const val ROW_IMAGE = "image"
const val ROW_DAY_NIGHT = "day_night"
const val ROW_NAVBAR = "navbar"
