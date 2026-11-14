// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentkeyboard.core.ProfileGroup
import helium314.keyboard.agent.FlatPreset
import helium314.keyboard.agent.KeyboardProfileStore
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.updates.GitHubUpdateClient
import helium314.keyboard.agent.updates.UpdateError
import helium314.keyboard.agent.updates.UpdateHttp
import helium314.keyboard.agent.updates.UpdateSettings
import helium314.keyboard.agent.updates.UpdateStatus
import helium314.keyboard.agent.updates.Updates
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.IntentUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SpannableStringUtils
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.dialogs.SliderDialog
import helium314.keyboard.settings.preferences.BackupRestorePreference
import helium314.keyboard.settings.preferences.LoadGestureLibPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/*
 * Stage 38h: the area page "System & Daten", 1:1 from docs/referenzen/einstellungen-mockup/System.dc.html.
 * Updates card on the stored result of the real check (network only on "Jetzt prüfen"), profile
 * cards as actions on KeyboardProfileStore and FlatPreset, and the rows of the old pages Erweitert
 * and Über with their own dialogs. The old pages stay reachable (rows tagged AREA_ROW_TAG + route).
 */

/** Gradients of the profile cards, from the mockup: saved profiles, the flat look, a new profile. */
private val savedGradient = Color(0xFF94A3B8) to Color(0xFFE2E8F0)
private val flatGradient = Color(0xFF3A3A42) to Color(0xFF9C9CA6)
private val newGradient = Color(0xFF22D3EE) to Color(0xFFA5F3FC)

/** Text colour of the "UPDATES" label and the start colour of the card gradient (mockup). */
private val updatesLabel = Color(0xFF7CF0C8)
private val updatesTint = Color(0xFF1B3A2E)

/** What the updates card says, from the stored result of the last check and the installed build. */
class UpdateCardText(val headline: String, val detail: String, val available: Long?)

/**
 * Headline: "Build N verfügbar" only for a build newer than the installed one, "Build N ist aktuell"
 * only when the last check ran on this build; a result from before an update says "Build N installiert".
 */
fun updateCardText(context: Context, status: UpdateStatus, installed: Long, checkedAt: Long, daily: Boolean, now: Long): UpdateCardText {
    val res = context.resources
    val available = (status as? UpdateStatus.Available)?.build?.takeIf { it > installed }
    val headline = when {
        available != null -> res.getString(R.string.agent_system_updates_available, available)
        status is UpdateStatus.Current && status.installed == installed -> res.getString(R.string.agent_system_updates_current, installed)
        status is UpdateStatus.Failed && status.error in installErrors -> res.getString(R.string.agent_system_updates_install_failed)
        status is UpdateStatus.Failed -> res.getString(R.string.agent_system_updates_failed)
        status is UpdateStatus.Unknown -> res.getString(R.string.agent_system_updates_never)
        else -> res.getString(R.string.agent_system_updates_installed, installed)
    }
    val parts = mutableListOf<String>()
    if (status is UpdateStatus.Failed) parts += res.getString(updateErrorText(status.error)).removeSuffix(".")
    parts += if (checkedAt > 0) res.getString(R.string.agent_system_updates_checked, checkedWhen(context, checkedAt, now))
    else res.getString(R.string.agent_updates_installed, installed)
    parts += res.getString(if (daily) R.string.agent_system_updates_daily_on else R.string.agent_system_updates_daily_off)
    return UpdateCardText(headline, parts.joinToString(" · "), available)
}

/** Errors of download and install (AgentUpdatesScreen stores them too), not of the check. */
private val installErrors = setOf(UpdateError.STORAGE, UpdateError.CHECKSUM, UpdateError.WRONG_PACKAGE,
    UpdateError.WRONG_SIGNATURE, UpdateError.NOT_NEWER, UpdateError.INSTALL)

/**
 * "gerade eben" under a minute, "vor 2 Stunden" up to a week (DateUtils capitalises it in German,
 * mid-sentence it is lowercased, P137), after that DateUtils gives a date: "am 3. Nov.".
 */
private fun checkedWhen(context: Context, checkedAt: Long, now: Long): String {
    val res = context.resources
    val age = now - checkedAt
    if (age < DateUtils.MINUTE_IN_MILLIS) return res.getString(R.string.agent_system_updates_just_now)
    val span = DateUtils.getRelativeTimeSpanString(checkedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    return if (age < DateUtils.WEEK_IN_MILLIS) span.replaceFirstChar { it.lowercase(Locale.getDefault()) }
        else res.getString(R.string.agent_system_updates_on_date, span)
}

/** One card of "PROFILE": a saved profile ([group]), the flat look (null group, not [isNew]) or "+ NEUES PROFIL". */
private class ProfileCardInfo(val tag: String, val label: String, val hint: String, val colors: Pair<Color, Color>,
                              val active: Boolean, val group: ProfileGroup? = null, val isNew: Boolean = false)

private fun ProfileGroup.title() = when (this) {
    ProfileGroup.DESIGN -> R.string.agent_profile_design
    ProfileGroup.LAYOUT -> R.string.agent_profile_layout
    ProfileGroup.GESTURES -> R.string.agent_profile_gestures
}

/** "Android 16" for an emoji level, as on the old page (SliderPreference of Erweitert). */
fun emojiVersionName(sdk: Int, unknown: String): String = "Android " + when (sdk) {
    21 -> "5.0"; 22 -> "5.1"; 23 -> "6"; 24 -> "7.0"; 25 -> "7.1"; 26 -> "8.0"; 27 -> "8.1"; 28 -> "9"
    29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"
    else -> return unknown
}

/** Which open dialog the page shows. */
private enum class SystemDialog { EMOJI_VERSION, APPLY, PROFILE_ERROR }

/**
 * [updateClient] makes the client for "Jetzt prüfen" (tests pass an offline fake); the page never
 * builds it before that tap.
 */
@Composable
fun AgentAreaSystemScreen(
    onNavigate: (String) -> Unit,
    onClickBack: () -> Unit,
    updateClient: (Context) -> GitHubUpdateClient = { GitHubUpdateClient(UpdateHttp.calls(it)) },
) {
    val area = SettingsArea.SYSTEM
    val context = LocalContext.current
    val prefs = context.prefs()
    // P121: recompose when a preference changes elsewhere (SettingsActivity counts) or here
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    var written by remember { mutableIntStateOf(0) }
    if ((changed?.value ?: 0) + written < 0) Log.v("irrelevant", "read both counters to recompose on a change")
    var dialog by rememberSaveable { mutableStateOf<SystemDialog?>(null) }
    var applyTag by rememberSaveable { mutableStateOf<String?>(null) }

    // P145: the update settings live in credential-protected storage, which throws before the first
    // unlock (Direct Boot); everything else here is device-protected (SettingsActivity's base context)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var accessible by remember { mutableStateOf(credentialAccessAllowed(context)) }
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { accessible = credentialAccessAllowed(context); resumed++ }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun write(change: SharedPreferences.Editor.() -> Unit) { prefs.edit { change() }; written++ }

    @Composable
    fun Nav(tag: String, title: String, summary: String?, value: String? = null, onClick: () -> Unit) {
        SettingRow(title, summary, area.dot, onClick, Modifier.testTag(tag), value)
    }
    @Composable
    fun SwitchRow(key: String, default: Boolean, title: String, summary: String) {
        SettingSwitchRow(title, summary, area.dot, bool(key, default), { v -> write { putBoolean(key, v) } },
            area.from, area.to, Modifier.testTag(SYSTEM_ROW_TAG + key))
    }

    val store = remember { KeyboardProfileStore(context) }
    val flat = remember { FlatPreset(context) }
    // read the stores only after a change or a resume, not on every composition (P136)
    val version = (changed?.value ?: 0) + written
    val cards = remember(version, resumed) { profileCards(context, store, flat) }

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

            UpdatesCard(accessible, resumed, onNavigate, updateClient)

            FeatureCard(stringResource(R.string.agent_system_profiles), spaced = true) {
                cards.forEachIndexed { i, card ->
                    ProfileCard(card, i) {
                        if (card.isNew) onNavigate(SettingsDestination.AgentProfiles)
                        else { applyTag = card.tag; dialog = SystemDialog.APPLY }
                    }
                }
            }

            SectionCard(stringResource(R.string.agent_system_section_backup)) {
                val backup = remember { Setting(context, SettingsWithoutKey.BACKUP_RESTORE, R.string.backup_restore_title) {} }
                BackupRestorePreference(backup) { open ->
                    Nav(SYSTEM_ROW_TAG + SettingsWithoutKey.BACKUP_RESTORE, stringResource(R.string.agent_system_backup),
                        stringResource(R.string.agent_system_backup_sub), onClick = open)
                }
                Nav(SYSTEM_EXPORT_TAG, stringResource(R.string.agent_system_export), stringResource(R.string.agent_system_export_sub)) {
                    onNavigate(SettingsDestination.AgentProfiles)
                }
            }

            SectionCard(stringResource(R.string.agent_system_section_advanced)) {
                Nav(SYSTEM_ROW_TAG + Settings.PREF_EMOJI_MAX_SDK, stringResource(R.string.agent_system_emoji),
                    stringResource(R.string.agent_system_emoji_sub),
                    emojiVersionName(prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0), stringResource(R.string.agent_system_emoji_unknown))
                ) { dialog = SystemDialog.EMOJI_VERSION }
                SwitchRow(Settings.PREF_URL_DETECTION, Defaults.PREF_URL_DETECTION,
                    stringResource(R.string.agent_system_url), stringResource(R.string.agent_system_url_sub))
                SwitchRow(Settings.PREF_ENABLE_HARDWARE_KEYBOARD, Defaults.PREF_ENABLE_HARDWARE_KEYBOARD,
                    stringResource(R.string.agent_system_hardware), stringResource(R.string.agent_system_hardware_sub))
                // the same conditions as on the old page Erweitert
                if (BuildConfig.BUILD_TYPE != "nouserlib") {
                    val lib = remember { Setting(context, SettingsWithoutKey.LOAD_GESTURE_LIB, R.string.load_gesture_library) {} }
                    LoadGestureLibPreference(lib) { open ->
                        Nav(SYSTEM_ROW_TAG + SettingsWithoutKey.LOAD_GESTURE_LIB, stringResource(R.string.agent_system_gesture_lib),
                            stringResource(R.string.agent_system_gesture_lib_sub), onClick = open)
                    }
                }
                if (BuildConfig.DEBUG || bool(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, Defaults.PREF_SHOW_DEBUG_SETTINGS))
                    Nav(SYSTEM_ROW_TAG + SettingsDestination.Debug, stringResource(R.string.agent_system_debug),
                        stringResource(R.string.agent_system_debug_sub)) { onNavigate(SettingsDestination.Debug) }
                SettingsAreas.entries(area).firstOrNull { it.route == SettingsDestination.DataGathering }?.takeIf { it.visible() }?.let {
                    Nav(AREA_ROW_TAG + it.route, stringResource(it.title), stringResource(it.summary)) { onNavigate(it.route) }
                }
                // leftovers of Erweitert (numpad symbols, emoji on a physical keyboard, app icon, delete swipe …)
                Nav(AREA_ROW_TAG + SettingsDestination.Advanced, stringResource(R.string.agent_system_all_advanced),
                    stringResource(R.string.agent_system_all_advanced_sub)) { onNavigate(SettingsDestination.Advanced) }
            }

            SectionCard(stringResource(R.string.agent_system_section_about)) {
                AboutRows(onNavigate, { title, summary, tag, onClick -> Nav(tag, title, summary, onClick = onClick) })
            }
        }

        when (dialog) {
            null -> {}
            SystemDialog.EMOJI_VERSION -> {
                val unknown = stringResource(R.string.agent_system_emoji_unknown)
                val reload = { SupportedEmojis.load(context); KeyboardSwitcher.getInstance().setThemeNeedsReload() }
                // the slider of the old page Erweitert, with its range, default and side effects
                SliderDialog(
                    onDismissRequest = { dialog = null },
                    onDone = { write { putInt(Settings.PREF_EMOJI_MAX_SDK, it.toInt()) }; reload() },
                    initialValue = prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0).toFloat(),
                    range = 21f..36f,
                    title = { Text(stringResource(R.string.prefs_key_emoji_max_sdk)) },
                    positionString = { emojiVersionName(it.toInt(), unknown) },
                    showDefault = true,
                    onDefault = { write { remove(Settings.PREF_EMOJI_MAX_SDK) }; reload() },
                    defaultValue = 0f,
                    integerValues = true,
                )
            }
            SystemDialog.APPLY -> {
                val card = cards.firstOrNull { it.tag == applyTag }
                if (card == null || store == null || flat == null) LaunchedEffect(Unit) { dialog = null }
                else AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(stringResource(R.string.agent_system_apply_title, card.label)) },
                    text = { Text(stringResource(if (card.group == null) R.string.agent_system_apply_flat else R.string.agent_system_apply_saved)) },
                    confirmButton = {
                        TextButton(onClick = {
                            val ok = runCatching { card.group?.let { store.restoreSaved(it) } ?: flat.apply() }.isSuccess
                            written++
                            dialog = if (ok) null else SystemDialog.PROFILE_ERROR
                        }, Modifier.testTag(SYSTEM_APPLY_TAG)) { Text(stringResource(R.string.agent_system_apply)) }
                    },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.cancel)) } },
                )
            }
            SystemDialog.PROFILE_ERROR -> AlertDialog(
                onDismissRequest = { dialog = null },
                text = { Text(stringResource(R.string.agent_profile_error)) },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.ok)) } },
            )
        }
    }
}

/**
 * Saved profiles first (one card per area that has one), then the flat look, then "+ NEUES PROFIL".
 * These are actions, not one selection: a card is highlighted while the current settings equal it.
 */
private fun profileCards(context: Context, store: KeyboardProfileStore, flat: FlatPreset): List<ProfileCardInfo> {
    val res = context.resources
    val saved = store.availableGroups.filter { store.hasSaved(it) }.map { group ->
        ProfileCardInfo(PROFILE_CARD_TAG + group.name, res.getString(group.title()), res.getString(R.string.agent_system_profile_saved_hint),
            savedGradient, store.matchesCurrent(group), group)
    }
    return saved + listOf(
        ProfileCardInfo(PROFILE_CARD_TAG + "FLAT", res.getString(R.string.agent_system_flat), res.getString(R.string.agent_system_flat_hint),
            flatGradient, runCatching { flat.isApplied() }.getOrDefault(false)),
        ProfileCardInfo(AREA_ROW_TAG + SettingsDestination.AgentProfiles, res.getString(R.string.agent_system_new_profile),
            res.getString(R.string.agent_system_new_profile_hint), newGradient, false, isNew = true),
    )
}

/** A profile card in the look of [ChoiceCards], but a button (an action, P136). */
@Composable
private fun ProfileCard(card: ProfileCardInfo, index: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AgentSettingsTokens.ChoiceRadius)
    val outline = AgentSettingsTokens.Text
    val activeText = stringResource(R.string.agent_system_profile_active)
    val label = if (card.isNew) stringResource(R.string.agent_settings_open, stringResource(R.string.agent_profiles_title))
        else stringResource(R.string.agent_system_apply_title, card.label)
    Box(
        Modifier.fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (card.active) {
                    val w = 3.dp.toPx(); val off = 2.dp.toPx() + w / 2
                    drawRoundRect(outline, topLeft = Offset(-off, -off), size = Size(size.width + 2 * off, size.height + 2 * off),
                        cornerRadius = CornerRadius(AgentSettingsTokens.ChoiceRadius.toPx() + off), style = Stroke(w))
                }
            }
            .alpha(if (card.active) 1f else 0.55f)
            .clip(shape).background(cssGradient(120f, card.colors.first, card.colors.second))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .then(if (card.active) Modifier.semantics { stateDescription = activeText } else Modifier)
            .heightIn(min = 92.dp).testTag(card.tag),
    ) {
        ProfileGlow(index)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.label.uppercase(Locale.getDefault()), style = AgentSettingsType.ChoiceLabel, color = AgentSettingsTokens.OnGradient)
            Text(card.hint, style = AgentSettingsType.Small.copy(fontWeight = FontWeight.Medium), color = AgentSettingsTokens.OnGradient)
        }
    }
}

/**
 * The glowing pill of the mockup: 42 × 22 dp, 14 dp from the top, 18 + 40 dp × (index mod 3) from
 * the right (the mockup has three cards; further cards repeat, so the pill never reaches the title).
 */
@Composable
private fun BoxScope.ProfileGlow(index: Int) {
    Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 18.dp + 40.dp * (index % 3)).size(42.dp, 22.dp)
        .clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.75f)))
}

/**
 * The updates card. While the device is locked it only says so, like the old page (the settings
 * live in credential-protected storage). The network is used only on "Jetzt prüfen".
 */
@Composable
private fun UpdatesCard(accessible: Boolean, resumed: Int, onNavigate: (String) -> Unit, updateClient: (Context) -> GitHubUpdateClient) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(AgentSettingsTokens.CardRadius)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(AgentSettingsTokens.Card).border(1.dp, AgentSettingsTokens.Border, shape)
            .background(cssGradient(135f, listOf(0f to updatesTint, 0.7f to AgentSettingsTokens.Card)))
            .padding(18.dp).testTag(SYSTEM_UPDATES_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!accessible) {
            Text(stringResource(R.string.agent_system_updates_label), style = labelStyle, color = updatesLabel)
            Text(stringResource(R.string.agent_updates_unlock), style = AgentSettingsType.Label, color = AgentSettingsTokens.Soft)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardButton(stringResource(R.string.agent_system_diagnose), false, Modifier.weight(1f).testTag(AREA_ROW_TAG + SettingsDestination.AgentDiagnose)) {
                    onNavigate(SettingsDestination.AgentDiagnose)
                }
            }
            return@Column
        }
        val settings = remember { UpdateSettings(context) }
        val installed = remember { runCatching { Updates.installedVersion(context) }.getOrDefault(0L) }
        var status by remember { mutableStateOf(runCatching { settings.status }.getOrDefault(UpdateStatus.Unknown)) }
        var checkedAt by remember { mutableStateOf(runCatching { settings.checkedAt }.getOrDefault(0L)) }
        var daily by remember { mutableStateOf(runCatching { settings.daily }.getOrDefault(false)) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(resumed) {
            // the daily job and the Updates page change these in the background
            if (!busy) runCatching { status = settings.status; checkedAt = settings.checkedAt; daily = settings.daily }
        }
        val text = updateCardText(context, status, installed, checkedAt, daily, System.currentTimeMillis())
        // label, headline and detail open the old Updates page (daily switch, download and install)
        Column(
            Modifier.fillMaxWidth()
                // no rounded clip here: it cut off the first letters of label and detail (P137)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.agent_settings_open, stringResource(R.string.agent_updates_title))) {
                    onNavigate(SettingsDestination.AgentUpdates)
                }.testTag(AREA_ROW_TAG + SettingsDestination.AgentUpdates),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.agent_system_updates_label), style = labelStyle, color = updatesLabel, modifier = Modifier.weight(1f))
                AgentLineIcon(LineIcon.CHEVRON, AgentSettingsTokens.Chevron, 16.dp)
            }
            Text(text.headline, style = headlineStyle, color = AgentSettingsTokens.Text, modifier = Modifier.testTag(SYSTEM_UPDATES_HEADLINE_TAG))
            Text(text.detail, style = AgentSettingsType.Label, color = AgentSettingsTokens.Soft, modifier = Modifier.testTag(SYSTEM_UPDATES_DETAIL_TAG))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(stringResource(if (busy) R.string.agent_updates_checking else R.string.agent_updates_check_now), true,
                Modifier.weight(1f).testTag(SYSTEM_CHECK_TAG), enabled = !busy) {
                busy = true
                scope.launch {
                    try {
                        status = withContext(Dispatchers.IO) { Updates.check(context, { updateClient(context) }, installed) }
                        checkedAt = runCatching { settings.checkedAt }.getOrDefault(System.currentTimeMillis())
                    } finally { busy = false }
                }
            }
            CardButton(stringResource(R.string.agent_system_diagnose), false, Modifier.weight(1f).testTag(AREA_ROW_TAG + SettingsDestination.AgentDiagnose)) {
                onNavigate(SettingsDestination.AgentDiagnose)
            }
        }
        // download and install stay on the Updates page (progress, permission, cancel)
        text.available?.let { build ->
            CardButton(stringResource(R.string.agent_system_updates_install, build), true, Modifier.fillMaxWidth().testTag(SYSTEM_INSTALL_TAG)) {
                onNavigate(SettingsDestination.AgentUpdates)
            }
        }
    }
}

private val labelStyle = AgentSettingsType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
private val headlineStyle = AgentSettingsType.SectionTitle.copy(fontSize = 26.sp)

/** The buttons of the updates card: light (filled) or outlined, 44 dp high, round. */
@Composable
private fun CardButton(text: String, light: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.heightIn(min = AgentSettingsTokens.MinTouch).clip(shape)
            .then(if (light) Modifier.background(AgentSettingsTokens.Text) else Modifier.border(1.dp, AgentSettingsTokens.SwitchOff, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AgentSettingsType.Button, color = if (light) AgentSettingsTokens.OnGradient else AgentSettingsTokens.Text,
            modifier = Modifier.alpha(if (enabled) 1f else 0.6f))
    }
}

/**
 * The rows of "Über" with the actions of the old page (AboutScreen): five taps on the version
 * unlock the debug settings, the licence link, the hidden features dialog and saving the log.
 */
@Composable
private fun AboutRows(onNavigate: (String) -> Unit, row: @Composable (String, String, String, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    var taps by rememberSaveable { mutableIntStateOf(0) }
    row(stringResource(R.string.english_ime_name), stringResource(R.string.agent_system_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        SYSTEM_ROW_TAG + SettingsWithoutKey.VERSION) {
        if (prefs.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, Defaults.PREF_SHOW_DEBUG_SETTINGS) || BuildConfig.DEBUG) return@row
        taps++
        if (taps < 5) return@row
        prefs.edit { putBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, true) }
        Toast.makeText(context, R.string.prefs_debug_settings_enabled, Toast.LENGTH_LONG).show()
    }
    row(stringResource(R.string.agent_system_license), stringResource(R.string.agent_system_license_sub), SYSTEM_ROW_TAG + SettingsWithoutKey.LICENSE) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Links.LICENSE.toUri())) }
    }
    row(stringResource(R.string.agent_system_hidden), stringResource(R.string.agent_system_hidden_sub), SYSTEM_ROW_TAG + SettingsWithoutKey.HIDDEN_FEATURES) {
        showHiddenFeatures(context)
    }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        // as on the old page: warnings of logcat and the app's own log, written on IO
        scope.launch(Dispatchers.IO) {
            context.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                os.writer().use { writer ->
                    val logcat = Runtime.getRuntime().exec("logcat -d -b all *:W").inputStream.use { it.reader().readText() }
                    writer.write(logcat + "\n\n" + Log.getLog().joinToString("\n"))
                }
            }
        }
    }
    row(stringResource(R.string.save_log), stringResource(R.string.agent_system_log_sub), SYSTEM_ROW_TAG + SettingsWithoutKey.SAVE_LOG) {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Calendar.getInstance().time)
        val intent = IntentUtils.getResolvableTypeIntent(context, "text/plain").setAction(Intent.ACTION_CREATE_DOCUMENT)
            .putExtra(Intent.EXTRA_TITLE, context.getString(R.string.english_ime_name).replace(" ", "_") + "_log_$date.txt")
        runCatching { launcher.launch(intent) }
    }
    // wiki, community and source links stay on the old page
    row(stringResource(R.string.agent_system_about_all), stringResource(R.string.agent_system_about_all_sub), AREA_ROW_TAG + SettingsDestination.About) {
        onNavigate(SettingsDestination.About)
    }
}

/** The dialog of AboutScreen's "Versteckte Funktionen" (an AlertDialog with a link, as there). */
private fun showHiddenFeatures(context: Context) {
    val link = ("<a href=\"https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()\">"
            + context.getString(R.string.hidden_features_text) + "</a>")
    val message = SpannableStringUtils.fromHtml(context.getString(R.string.hidden_features_message, link))
    val dialog = AlertDialog.Builder(context)
        .setIcon(R.drawable.ic_settings_about_hidden_features)
        .setTitle(R.string.hidden_features_title)
        .setMessage(message)
        .setPositiveButton(R.string.dialog_close, null)
        .create()
    dialog.show()
    (dialog.findViewById<View>(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
}

const val SYSTEM_ROW_TAG = "agent_system_row_"
const val SYSTEM_UPDATES_TAG = "agent_system_updates"
const val SYSTEM_UPDATES_HEADLINE_TAG = "agent_system_updates_headline"
const val SYSTEM_UPDATES_DETAIL_TAG = "agent_system_updates_detail"
const val SYSTEM_CHECK_TAG = "agent_system_check"
const val SYSTEM_INSTALL_TAG = "agent_system_install"
const val SYSTEM_EXPORT_TAG = "agent_system_export"
const val SYSTEM_APPLY_TAG = "agent_system_apply"
const val PROFILE_CARD_TAG = "agent_system_profile_"
