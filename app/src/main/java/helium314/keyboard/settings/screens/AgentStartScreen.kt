// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.agentkeyboard.core.RgbTarget
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.DesignTiles
import helium314.keyboard.agent.MyDesigns
import helium314.keyboard.agent.RgbLighting
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.ExpandableSearchField
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination

/**
 * Stage 38a: the start page of the settings (mockup Main.dc.html). Title with search, the look
 * card ("Ändern" opens the appearance area), four quick toggles on the real preferences, the seven
 * area tiles and the footer pills. Shown for every keyboard style.
 */
@Composable
fun AgentStartScreen(onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(TextFieldValue()) }
    fun closeSearch() { showSearch = false; searchText = TextFieldValue() }
    BackHandler { if (showSearch) closeSearch() else onClickBack() }
    AgentSettingsTheme {
        Column(Modifier.fillMaxSize().background(AgentSettingsTokens.Background).windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(START_TAG)) {
            if (showSearch) {
                StartTitle(onSearch = { closeSearch() }, Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp))
                ExpandableSearchField(
                    expanded = true, onDismiss = { closeSearch() }, search = searchText, onSearchChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AgentSettingsTokens.Card, unfocusedContainerColor = AgentSettingsTokens.Card,
                        focusedIndicatorColor = AgentSettingsTokens.Border, unfocusedIndicatorColor = AgentSettingsTokens.Border,
                    ),
                )
                if (searchText.text.isNotBlank()) {
                    val items = SettingsActivity.settingsContainer.filter(searchText.text)
                    LazyColumn(Modifier.fillMaxSize()) { items(items) { it.Preference() } }
                }
            } else StartContent(onNavigate, onSearch = { showSearch = true })
        }
    }
}

@Composable
private fun StartTitle(onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.agent_settings_title), style = AgentSettingsType.PageTitle, color = AgentSettingsTokens.Text,
            modifier = Modifier.weight(1f).semantics { heading() })
        AgentRoundButton(LineIcon.SEARCH, stringResource(R.string.agent_settings_search), onSearch, Modifier.testTag(SEARCH_TAG))
    }
}

@Composable
private fun StartContent(onNavigate: (String) -> Unit, onSearch: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    // recompose when a preference changes elsewhere (SettingsActivity counts) or here
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    var written by remember { mutableIntStateOf(0) }
    if ((changed?.value ?: 0) + written < 0) Log.v("irrelevant", "read both counters to recompose on a change")
    fun toggle(key: String, value: Boolean) { prefs.edit { putBoolean(key, value) }; written++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = AgentSettingsTokens.PagePadding, end = AgentSettingsTokens.PagePadding, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StartTitle(onSearch, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
        LookCard(lookName(context, prefs), lightName(prefs)) { onNavigate(SettingsArea.LOOK.route) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.agent_settings_quick), style = AgentSettingsType.Label, color = AgentSettingsTokens.Muted,
                modifier = Modifier.padding(horizontal = 4.dp))
            val rgb = RgbLighting.read(prefs)
            ChipToggles(listOf(
                ChipItem(stringResource(R.string.agent_settings_quick_incognito),
                    prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE),
                    { toggle(Settings.PREF_ALWAYS_INCOGNITO_MODE, it) }, QUICK_INCOGNITO),
                ChipItem(stringResource(R.string.agent_settings_quick_rgb), rgb.enabled,
                    { RgbLighting.write(prefs, rgb.copy(enabled = it).normalized()); written++ }, QUICK_RGB),
                ChipItem(stringResource(R.string.agent_settings_quick_vibration),
                    prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON),
                    { toggle(Settings.PREF_VIBRATE_ON, it) }, QUICK_VIBRATION),
                ChipItem(stringResource(R.string.agent_settings_quick_symbols),
                    prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, false),
                    { toggle(AgentPreferences.SYMBOL_DRAWING, it) }, QUICK_SYMBOLS),
            ))
        }

        AreaGrid(onNavigate)

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            FooterPill(stringResource(R.string.agent_settings_footer_diagnose), { onNavigate(SettingsDestination.AgentDiagnose) },
                Modifier.testTag(FOOTER_TAG + SettingsDestination.AgentDiagnose))
            FooterPill(stringResource(R.string.agent_settings_footer_updates, BuildConfig.VERSION_CODE),
                { onNavigate(SettingsDestination.AgentUpdates) }, Modifier.testTag(FOOTER_TAG + SettingsDestination.AgentUpdates))
            FooterPill(stringResource(R.string.agent_settings_footer_about), { onNavigate(SettingsDestination.About) },
                Modifier.testTag(FOOTER_TAG + SettingsDestination.About))
        }
    }
}

/** The tiles as in the mockup: 01 wide, 02/03, 04/05, 06 wide, 07 wide (rows from [SettingsArea.wide]). */
@Composable
private fun AreaGrid(onNavigate: (String) -> Unit) {
    val rows = buildList {
        var pending: SettingsArea? = null
        SettingsArea.entries.forEach { area ->
            when {
                area.wide -> { pending?.let { add(listOf(it)) }; pending = null; add(listOf(area)) }
                pending == null -> pending = area
                else -> { add(listOf(pending!!, area)); pending = null }
            }
        }
        pending?.let { add(listOf(it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { area -> AreaTile(area, { onNavigate(area.route) }, Modifier.weight(1f).fillMaxSize()) }
            }
        }
    }
}

/** Card with a small keyboard in the light's colours, the current look and "Ändern". */
@Composable
private fun LookCard(design: String, light: String, onChange: () -> Unit) {
    val t = AgentSettingsTokens
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(t.CardRadius)).background(t.Card)
            .border(1.dp, t.Border, RoundedCornerShape(t.CardRadius)).padding(14.dp).testTag(LOOK_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)).background(LOOK_GRADIENT).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(4) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(10) { Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(7.dp)).background(KEY_SHADE)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.agent_settings_your_look), style = AgentSettingsType.Label, color = t.Muted)
                Text(stringResource(R.string.agent_settings_look_value, design, light), style = AgentSettingsType.LookName, color = t.Text,
                    modifier = Modifier.testTag(LOOK_NAME_TAG))
            }
            Box(
                Modifier.heightIn(min = t.MinTouch).widthIn(min = t.MinTouch).clip(RoundedCornerShape(22.dp)).background(t.Text)
                    .clickable(role = Role.Button, onClick = onChange).padding(horizontal = 16.dp, vertical = 12.dp).testTag(LOOK_CHANGE_TAG),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.agent_settings_change), style = AgentSettingsType.Button, color = t.OnGradient) }
        }
    }
}

/**
 * Name of the current look: an own design by its name, the flat template, otherwise the key
 * style ("Abgerundet" as in the mockup).
 */
internal fun lookName(context: Context, prefs: SharedPreferences): String {
    MyDesigns.selectedId(prefs)?.let { id -> MyDesigns.get(prefs, id)?.name?.takeIf { it.isNotBlank() }?.let { return it } }
    if (DesignTiles.selectedId(prefs) == DesignTiles.FLAT) return context.getString(R.string.agent_design_flat)
    val style = prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE) ?: Defaults.PREF_THEME_STYLE
    return style.getStringResourceOrName("style_name_", context)
}

/** The pattern of the keys' light, or "aus". */
@Composable
private fun lightName(prefs: SharedPreferences): String {
    val rgb = RgbLighting.read(prefs)
    return if (rgb.enabled) patternLabel(rgb.lookFor(RgbTarget.KEYS).pattern) else stringResource(R.string.agent_settings_light_off)
}

private val LOOK_GRADIENT = cssGradient(120f, listOf(
    0f to androidx.compose.ui.graphics.Color(0xFF00C2A8), 0.35f to androidx.compose.ui.graphics.Color(0xFF3D7BFF),
    0.6f to androidx.compose.ui.graphics.Color(0xFF8B5CF6), 0.8f to androidx.compose.ui.graphics.Color(0xFFFF4D8D),
    1f to androidx.compose.ui.graphics.Color(0xFFFFB547),
))
private val KEY_SHADE = androidx.compose.ui.graphics.Color(0x8C0B0B0F)

const val START_TAG = "agent_settings_start"
const val SEARCH_TAG = "agent_settings_search"
const val LOOK_TAG = "agent_settings_look"
const val LOOK_NAME_TAG = "agent_settings_look_name"
const val LOOK_CHANGE_TAG = "agent_settings_look_change"
const val FOOTER_TAG = "agent_settings_footer_"
const val QUICK_INCOGNITO = "incognito"
const val QUICK_RGB = "rgb"
const val QUICK_VIBRATION = "vibration"
const val QUICK_SYMBOLS = "symbols"
