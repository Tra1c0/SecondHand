// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.ScreenshotChip
import helium314.keyboard.agent.commands.EmojiMixCommand
import helium314.keyboard.agent.skillToolbarKey
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.SliderDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

/**
 * Stage 38f: the three bar cards of the mockup on HeliBoard's toolbar mode. TASTEN + VORSCHLÄGE =
 * EXPANDABLE, NUR VORSCHLÄGE = SUGGESTION_STRIP, VERSTECKT = HIDDEN. The fourth mode TOOLBAR_KEYS
 * ("Nur Leisten-Tasten") matches no card and shows as "Eigene Einstellung".
 */
object BarModes {
    val cards = listOf(ToolbarMode.EXPANDABLE, ToolbarMode.SUGGESTION_STRIP, ToolbarMode.HIDDEN)

    /** Index of the card for the stored mode, or -1 for TOOLBAR_KEYS. */
    fun current(prefs: SharedPreferences): Int = cards.indexOf(Settings.readToolbarMode(prefs))

    /** Writes the mode of card [index] as the old list preference does (with a theme reload). */
    fun apply(prefs: SharedPreferences, index: Int) {
        prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, cards[index].name) }
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    /** How many of the three skill keys are pinned to the bar. */
    fun pinnedSkills(prefs: SharedPreferences): Int = (1..3).count { skillToolbarKey(it) in getPinnedToolbarKeys(prefs) }
}

/**
 * Stage 38f: the area page "Leiste & Befehle", 1:1 from Leiste.dc.html. Hero, the bar mode
 * ([BarModes]), the @ commands as chips on the real command preferences with the master switch in
 * the card header, the sections Leiste and Zwischenablage & Bilder, and rows to the old pages for
 * the rest (prefix, snippets, places, screen reading, screenshots, more clipboard options).
 * Rows that open a page carry [AREA_ROW_TAG] + route, as listed in [SettingsAreas.entries].
 */
@Composable
fun AgentAreaBarScreen(onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    val area = SettingsArea.BAR
    val context = LocalContext.current
    val prefs = context.prefs()
    // recompose when a preference changes elsewhere (SettingsActivity counts) or here (P121)
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    var written by remember { mutableIntStateOf(0) }
    if ((changed?.value ?: 0) + written < 0) Log.v("irrelevant", "read both counters to recompose on a change")
    fun observe() = (changed?.value ?: 0) + written
    fun bool(key: String, default: Boolean): Boolean { observe(); return prefs.getBoolean(key, default) }

    // runtime permissions: a keyboard service cannot show the system dialog, so this page asks
    val permissions = rememberPermissions(context)
    var showSkills by remember { mutableStateOf(false) }
    var showRetention by remember { mutableStateOf(false) }

    val mode = Settings.readToolbarMode(prefs).also { observe() }
    val history = bool(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)

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

            val card = BarModes.current(prefs)
            ChoiceCards(
                stringResource(R.string.agent_bar_mode),
                listOf(
                    Choice(stringResource(R.string.agent_bar_mode_expandable), stringResource(R.string.agent_bar_mode_expandable_hint),
                        Color(0xFFFFC94D), Color(0xFFFFE7A3), BAR_MODE_TAG + ToolbarMode.EXPANDABLE.name),
                    Choice(stringResource(R.string.agent_bar_mode_suggestions), stringResource(R.string.agent_bar_mode_suggestions_hint),
                        Color(0xFFFF9F43), Color(0xFFFFD29A), BAR_MODE_TAG + ToolbarMode.SUGGESTION_STRIP.name),
                    Choice(stringResource(R.string.agent_bar_mode_hidden), stringResource(R.string.agent_bar_mode_hidden_hint),
                        Color(0xFF3A3A42), Color(0xFF6B6B76), BAR_MODE_TAG + ToolbarMode.HIDDEN.name),
                ),
                selected = card,
                onSelect = { BarModes.apply(prefs, it); written++ },
            ) {
                if (card < 0) Text(stringResource(R.string.agent_bar_mode_custom, stringResource(R.string.toolbar_mode_toolbar_keys)),
                    style = AgentSettingsType.Small, color = AgentSettingsTokens.Muted, modifier = Modifier.testTag(BAR_CUSTOM_TAG))
            }

            CommandsCard(area, ::observe, permissions) { written++ }

            SectionCard(stringResource(R.string.agent_bar_section)) {
                SettingRow(stringResource(R.string.agent_bar_keys), stringResource(R.string.agent_bar_keys_sub), area.dot,
                    { onNavigate(SettingsDestination.Toolbar) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.Toolbar))
                BarSwitch(R.string.agent_bar_auto_show, R.string.agent_bar_auto_show_sub, Settings.PREF_AUTO_SHOW_TOOLBAR,
                    Defaults.PREF_AUTO_SHOW_TOOLBAR, mode == ToolbarMode.EXPANDABLE, R.string.agent_bar_auto_show_off, ::bool) { _, _ -> written++ }
                BarSwitch(R.string.agent_bar_swipe, R.string.agent_bar_swipe_sub, Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE,
                    Defaults.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE, mode != ToolbarMode.HIDDEN, R.string.agent_bar_swipe_off, ::bool) { _, _ -> written++ }
                // pinned keys only show next to suggestions, as the old page shows their list only then
                val pinnedShown = mode == ToolbarMode.EXPANDABLE || mode == ToolbarMode.SUGGESTION_STRIP
                SettingRow(stringResource(R.string.agent_bar_skills),
                    stringResource(if (pinnedShown) R.string.agent_bar_skills_sub else R.string.agent_bar_skills_off), area.dot,
                    { showSkills = true }, Modifier.testTag(BAR_SKILLS_TAG),
                    value = stringResource(R.string.agent_bar_skills_value, observe().let { BarModes.pinnedSkills(prefs) }))
            }

            SectionCard(stringResource(R.string.agent_bar_clipboard)) {
                BarSwitch(R.string.agent_bar_history, R.string.agent_bar_history_sub, Settings.PREF_ENABLE_CLIPBOARD_HISTORY,
                    Defaults.PREF_ENABLE_CLIPBOARD_HISTORY, true, null, ::bool) { _, _ ->
                    // as the old switch: entries that are not pinned go when the history is switched
                    ClipboardDao.getInstance(context)?.clearNonPinned()
                    written++
                }
                val minutes = observe().let { prefs.getInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME) }
                BarNavRow(stringResource(R.string.agent_bar_retention),
                    stringResource(if (history) R.string.agent_bar_retention_sub else R.string.agent_bar_needs_history),
                    retentionText(minutes), history, BAR_RETENTION_TAG) { showRetention = true }
                BarSwitch(R.string.agent_bar_files, R.string.agent_bar_files_sub, Settings.PREF_CLIPBOARD_USE_FILES,
                    Defaults.PREF_CLIPBOARD_USE_FILES, history, R.string.agent_bar_needs_history, ::bool) { _, _ ->
                    ClipboardDao.getInstance(context)?.cleanupFiles(prefs)
                    written++
                }
                val screenshots = bool(AgentPreferences.SCREENSHOT_CHIP, false)
                BarSwitchRow(stringResource(R.string.agent_bar_screenshots),
                    stringResource(if (screenshots && !permissions.screenshots) R.string.agent_bar_screenshots_missing else R.string.agent_bar_screenshots_sub),
                    { bool(AgentPreferences.SCREENSHOT_CHIP, false) }, true, BAR_SWITCH_TAG + AgentPreferences.SCREENSHOT_CHIP) { on ->
                    // the same steps as the screenshot page
                    prefs.edit { putBoolean(AgentPreferences.SCREENSHOT_CHIP, on) }
                    if (!on) {
                        ScreenshotChip.cleanUp(context, System.currentTimeMillis(), all = true)
                        ScreenshotChip.removeFromHistory(context)
                    }
                    if (on && !permissions.screenshots) permissions.ask(ScreenshotChip.permission())
                    written++
                }
                BarSwitchRow(stringResource(R.string.agent_bar_mix), stringResource(R.string.agent_bar_mix_sub),
                    { observe(); EmojiMixCommand.stripEnabled(context) }, true, BAR_SWITCH_TAG + EmojiMixCommand.STRIP_PREFERENCE) { on ->
                    EmojiMixCommand.setStripEnabled(context, on)
                    written++
                }
                // stage 40: Smart Paste rides along with the clipboard pill, which has its own switch
                val pill = bool(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, Defaults.PREF_SUGGEST_CLIPBOARD_CONTENT)
                BarSwitchRow(stringResource(R.string.agent_smart_paste_setting),
                    stringResource(if (pill) R.string.agent_smart_paste_setting_sub else R.string.agent_smart_paste_setting_no_pill),
                    { bool(AgentPreferences.SMART_PASTE, true) }, true, BAR_SWITCH_TAG + AgentPreferences.SMART_PASTE) { on ->
                    prefs.edit { putBoolean(AgentPreferences.SMART_PASTE, on) }
                    written++
                }
            }

            SectionCard(null) {
                SettingRow(stringResource(R.string.agent_bar_all_commands), stringResource(R.string.agent_bar_all_commands_sub), area.dot,
                    { onNavigate(SettingsDestination.AgentCommands) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentCommands))
                SettingRow(stringResource(R.string.agent_bar_screenshot_page), stringResource(R.string.agent_bar_screenshot_page_sub), area.dot,
                    { onNavigate(SettingsDestination.AgentScreenshots) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentScreenshots),
                    value = stringResource(if (permissions.screenshots) R.string.agent_bar_screenshot_allowed else R.string.agent_bar_screenshot_missing))
                SettingRow(stringResource(R.string.agent_bar_more_clipboard), stringResource(R.string.agent_bar_more_clipboard_sub), area.dot,
                    { onNavigate(SettingsDestination.Preferences) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.Preferences))
                // stage 45: one row to the suggestions switch and "Meine Daten"
                SettingRow(stringResource(R.string.agent_my_data_title), stringResource(R.string.agent_my_data_row_sub), area.dot,
                    { onNavigate(SettingsDestination.AgentMyData) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentMyData))
            }
        }
        if (showSkills) SkillPinDialog(prefs, { showSkills = false }) { written++ }
        if (showRetention) SliderDialog(
            onDismissRequest = { showRetention = false },
            onDone = {
                prefs.edit { putInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, it.toInt()) }
                ClipboardDao.getInstance(context)?.clearOldClips(true)
                written++
            },
            initialValue = prefs.getInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME).toFloat(),
            range = 1f..121f,
            title = { Text(stringResource(R.string.clipboard_history_retention_time)) },
            positionString = { retentionText(it.toInt()) },
            showDefault = true,
            onDefault = {
                prefs.edit { remove(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME) }
                ClipboardDao.getInstance(context)?.clearOldClips(true)
                written++
            },
            defaultValue = Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME.toFloat(),
            integerValues = true,
        )
    }
}

/** The value text of the old retention slider: minutes, above 120 no limit. */
@Composable
private fun retentionText(minutes: Int) =
    if (minutes > 120) stringResource(R.string.settings_no_limit) else stringResource(R.string.abbreviation_unit_minutes, minutes.toString())

/** Granted runtime permissions of this page, refreshed on resume, and a way to ask for one. */
private class BarPermissions(val granted: Set<String>, val ask: (String) -> Unit) {
    val screenshots get() = ScreenshotChip.permission() in granted
    fun has(permission: String?) = permission == null || permission in granted
}

@Composable
private fun rememberPermissions(context: Context): BarPermissions {
    val wanted = remember { (KeyboardCommands.registry.mapNotNull { it.permission } + ScreenshotChip.permission()).toSet() }
    fun granted() = wanted.filter { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }.toSet()
    var allowed by remember { mutableStateOf(granted()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        // the permission can change in the system settings while this page is in the background
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = granted() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = granted() }
    return BarPermissions(allowed) { request.launch(it) }
}

/**
 * The card "@-Befehle": title, the prefix and the master switch "Getippte Befehle erkennen" in the
 * header, every built-in command as a chip on its own preference, the caption of the mockup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandsCard(area: SettingsArea, observe: () -> Int, permissions: BarPermissions, onWritten: () -> Unit) {
    val context = LocalContext.current
    val t = AgentSettingsTokens
    observe()
    val enabled = KeyboardCommands.isEnabled(context)
    val prefix = KeyboardCommands.prefix(context)
    FeatureCard(null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.agent_bar_commands), style = AgentSettingsType.SectionTitle, color = t.Text,
                modifier = Modifier.weight(1f).semantics { heading() })
            Text(stringResource(R.string.agent_bar_prefix, prefix.toString()), style = AgentSettingsType.Label, color = t.Muted)
            GradientSwitch(enabled, { KeyboardCommands.setEnabled(context, it); onWritten() }, area.from, area.to,
                Modifier.testTag(BAR_COMMANDS_SWITCH_TAG), description = stringResource(R.string.agent_commands_switch))
        }
        FlowRow(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardCommands.registry.forEach { command ->
                val on = KeyboardCommands.isCommandEnabled(context, command.name)
                val shape = RoundedCornerShape(20.dp)
                val (background, border, text) = if (on) Triple(cssGradient(120f, area.from, area.to), Color.Transparent, t.OnGradient)
                    else Triple(Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), t.SwitchOff, t.Chevron)
                Box(
                    Modifier.heightIn(min = t.MinTouch).widthIn(min = t.MinTouch).clip(shape).background(background).border(1.dp, border, shape)
                        .toggleable(value = on, enabled = enabled, role = Role.Switch) { value ->
                            KeyboardCommands.setCommandEnabled(context, command.name, value)
                            val permission = command.permission
                            if (value && !permissions.has(permission)) permissions.ask(permission!!)
                            onWritten()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp).testTag(BAR_COMMAND_TAG + command.name),
                    contentAlignment = Alignment.Center,
                ) { Text("$prefix${command.name}", style = AgentSettingsType.Button, color = text) }
            }
        }
        // an enabled command whose permission is missing (stage 24c contacts) can ask for it here
        KeyboardCommands.registry.filter { enabled && KeyboardCommands.isCommandEnabled(context, it.name) && !permissions.has(it.permission) }
            .forEach { command ->
                Text(stringResource(R.string.agent_bar_permission, "$prefix${command.name}"), style = AgentSettingsType.Small, color = t.Soft,
                    modifier = Modifier.fillMaxWidth().heightIn(min = t.MinTouch).clip(RoundedCornerShape(t.RowRadius))
                        .clickable(role = Role.Button) { permissions.ask(command.permission!!) }.padding(vertical = 12.dp)
                        .testTag(BAR_PERMISSION_TAG + command.name))
            }
        Text(stringResource(if (enabled) R.string.agent_bar_commands_caption else R.string.agent_bar_commands_off),
            style = AgentSettingsType.Small, color = t.Muted)
    }
}

/** A switch row bound to one boolean preference; [onWritten] runs after the value is stored. */
@Composable
private fun BarSwitch(
    title: Int,
    summary: Int,
    key: String,
    default: Boolean,
    enabled: Boolean,
    disabledHint: Int?,
    read: (String, Boolean) -> Boolean,
    onWritten: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    BarSwitchRow(stringResource(title), stringResource(if (enabled || disabledHint == null) summary else disabledHint),
        { read(key, default) }, enabled, BAR_SWITCH_TAG + key) {
        context.prefs().edit { putBoolean(key, it) }
        onWritten(key, it)
    }
}

/**
 * [SettingSwitchRow] of the kit with an enabled state, reading its value in its own scope (P126):
 * disabled rows are at 45 % and cannot be toggled.
 */
@Composable
private fun BarSwitchRow(title: String, summary: String, checked: () -> Boolean, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val area = SettingsArea.BAR
    val on = checked()
    Row(
        Modifier.fillMaxWidth().heightIn(min = AgentSettingsTokens.MinTouch).clip(RoundedCornerShape(AgentSettingsTokens.RowRadius))
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .alpha(if (enabled) 1f else 0.45f).padding(12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(area.dot))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AgentSettingsType.RowTitle, color = AgentSettingsTokens.Text)
            Text(summary, style = AgentSettingsType.Small, color = AgentSettingsTokens.Muted)
        }
        GradientSwitch(on, null, area.from, area.to)
    }
}

/** [SettingRow] with an enabled state, for a row that depends on another switch. */
@Composable
private fun BarNavRow(title: String, summary: String, value: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val area = SettingsArea.BAR
    Row(
        Modifier.fillMaxWidth().heightIn(min = AgentSettingsTokens.MinTouch).clip(RoundedCornerShape(AgentSettingsTokens.RowRadius))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.agent_settings_open, title), onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f).padding(12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(area.dot))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AgentSettingsType.RowTitle, color = AgentSettingsTokens.Text)
            Text(summary, style = AgentSettingsType.Small, color = AgentSettingsTokens.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = AgentSettingsType.Label, color = AgentSettingsTokens.Soft)
            AgentLineIcon(LineIcon.CHEVRON, AgentSettingsTokens.Chevron, 16.dp)
        }
    }
}

/** The three skill keys as switches; OK pins or unpins them like the skill page (addPinnedKey). */
@Composable
private fun SkillPinDialog(prefs: SharedPreferences, onDismiss: () -> Unit, onWritten: () -> Unit) {
    val keys = (1..3).map { skillToolbarKey(it) }
    var pinned by remember { mutableStateOf(keys.filter { it in getPinnedToolbarKeys(prefs) }.toSet()) }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismiss,
        onConfirmed = {
            val before = getPinnedToolbarKeys(prefs)
            keys.forEach { key ->
                if (key in pinned && key !in before) addPinnedKey(prefs, key)
                if (key !in pinned && key in before) removePinnedKey(prefs, key)
            }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
            onWritten()
        },
        title = { Text(stringResource(R.string.agent_bar_skills)) },
        content = {
            Column {
                keys.forEachIndexed { i, key ->
                    Row(Modifier.fillMaxWidth().heightIn(min = AgentSettingsTokens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.agent_skill_slot, i + 1), Modifier.weight(1f))
                        Switch(checked = key in pinned, onCheckedChange = { pinned = if (it) pinned + key else pinned - key },
                            modifier = Modifier.testTag(BAR_SKILL_PIN_TAG + (i + 1)))
                    }
                }
            }
        },
    )
}

const val BAR_MODE_TAG = "bar_mode_"
const val BAR_CUSTOM_TAG = "bar_mode_custom"
const val BAR_SWITCH_TAG = "bar_switch_"
const val BAR_COMMAND_TAG = "bar_command_"
const val BAR_COMMANDS_SWITCH_TAG = "bar_commands_switch"
const val BAR_PERMISSION_TAG = "bar_permission_"
const val BAR_SKILLS_TAG = "bar_skills"
const val BAR_SKILL_PIN_TAG = "bar_skill_pin_"
const val BAR_RETENTION_TAG = "bar_retention"
