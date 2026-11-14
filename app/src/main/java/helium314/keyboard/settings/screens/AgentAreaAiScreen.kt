// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentkeyboard.core.BuiltinAction
import dev.agentkeyboard.core.SkillBinding
import dev.agentkeyboard.core.TranslationLanguage
import dev.agentkeyboard.core.WorkflowDefinition
import dev.agentkeyboard.core.WorkflowProvider
import dev.agentkeyboard.core.WritingTone
import helium314.keyboard.agent.EncryptedActionRunStore
import helium314.keyboard.agent.ModelSettings
import helium314.keyboard.agent.OnDeviceSpeechEngine
import helium314.keyboard.agent.SkillBindingStore
import helium314.keyboard.agent.WorkflowStore
import helium314.keyboard.agent.providers.CloudProviders
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.titleResource
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SettingsDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Stage 38g: the area page "KI & Automationen", 1:1 from docs/referenzen/einstellungen-mockup/KI.dc.html.
 * Provider choice on the real provider preference, whether a key is stored (never the key itself:
 * only [EncryptedCredentialStore.contains]), the three skill keys with their real names, target
 * language and tone, workflows and dictation. Keys, models, skill slots and workflows are edited
 * on their existing pages. Like the old AI page: FLAG_SECURE while shown, and on a locked device
 * nothing of the credential-protected storage is opened (Direct Boot).
 * [dictationAvailable] replaces the on-device recognizer check in tests.
 */
@Composable
fun AgentAreaAiScreen(onNavigate: (String) -> Unit, onClickBack: () -> Unit, dictationAvailable: (() -> Boolean)? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var accessible by remember { mutableStateOf(credentialAccessAllowed(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessible = credentialAccessAllowed(context)
            if (event == Lifecycle.Event.ON_STOP) accessible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val window = context.activity()?.window
    DisposableEffect(window) {
        SecureWindow.acquire(window)
        onDispose { SecureWindow.release(window) }
    }
    val area = SettingsArea.AI
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
            if (accessible) UnlockedContent(onNavigate, dictationAvailable)
            else LockedContent(onNavigate)
        }
    }
}

/** Locked device: no credential-protected storage is touched; the old pages stay reachable (they show their own lock note). */
@Composable
private fun LockedContent(onNavigate: (String) -> Unit) {
    val area = SettingsArea.AI
    FeatureCard(null, Modifier.testTag(AI_LOCKED_TAG)) {
        Text(stringResource(R.string.agent_ai_unlock), style = AgentSettingsType.Label, color = AgentSettingsTokens.Soft)
    }
    SectionCard(stringResource(R.string.agent_ai_area_pages)) {
        SettingsAreas.entries(area).forEach { entry ->
            SettingRow(stringResource(entry.title), stringResource(entry.summary), area.dot, { onNavigate(entry.route) },
                Modifier.testTag(AREA_ROW_TAG + entry.route))
        }
    }
}

@Composable
private fun UnlockedContent(onNavigate: (String) -> Unit, dictationAvailable: (() -> Boolean)?) {
    val context = LocalContext.current
    val models = remember { ModelSettings(context) }
    var provider by remember { mutableStateOf(runCatching { models.provider }.getOrDefault(WorkflowProvider.OPENAI)) }
    var language by remember { mutableStateOf(runCatching { models.translationLanguage }.getOrDefault(TranslationLanguage.ENGLISH)) }
    var tone by remember { mutableStateOf(runCatching { models.writingTone }.getOrDefault(WritingTone.NEUTRAL)) }
    var skills by remember { mutableStateOf<List<SkillBinding>?>(null) }
    var skillsFailed by remember { mutableStateOf(false) }
    var workflows by remember { mutableStateOf<List<WorkflowDefinition>?>(null) }
    LaunchedEffect(Unit) {
        // read once per visit: the skill and workflow pages change them, and this page is composed anew after them
        runCatching { withContext(Dispatchers.IO) { SkillBindingStore(context).read() } }
            .onSuccess { skills = it }.onFailure { skillsFailed = true }
        workflows = runCatching { withContext(Dispatchers.IO) { WorkflowStore(context).read() } }.getOrNull()
    }
    ProviderCard(models, provider, { provider = it }, onNavigate)
    SkillCard(skills, skillsFailed, onNavigate)
    TextsSection(language, tone, onLanguage = { target ->
        runCatching { models.setTranslationLanguage(target) }.onSuccess { language = target }
    }, onTone = { target ->
        runCatching { models.setWritingTone(target) }.onSuccess { tone = target }
    })
    WorkflowsSection(workflows, onNavigate)
    DictationSection(dictationAvailable, onNavigate)
}

/** "ANBIETER": the three providers as choice cards on [ModelSettings.provider], then whether its key is stored. */
@Composable
private fun ProviderCard(models: ModelSettings, provider: WorkflowProvider, onProvider: (WorkflowProvider) -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { EncryptedCredentialStore(context) }
    // the key itself is never read here, only whether its file exists
    var key by remember { mutableStateOf<KeyState>(KeyState.CHECKING) }
    LaunchedEffect(provider) {
        key = KeyState.CHECKING
        val slot = CloudProviders.slot(provider)
        key = runCatching { withContext(Dispatchers.IO) { store.contains(slot) } }
            .fold({ if (it) KeyState.SAVED else KeyState.MISSING }, { KeyState.UNAVAILABLE })
    }
    val choices = CloudProviders.SUPPORTED.map { target ->
        val (from, to) = providerColors(target)
        val model = runCatching { models.model(target) }.getOrDefault("")
        Choice(CloudProviders.displayName(target),
            if (model.isEmpty()) stringResource(R.string.agent_ai_area_model_own) else stringResource(R.string.agent_ai_area_model, model),
            from, to, PROVIDER_TAG + target.id)
    }
    ChoiceCards(
        stringResource(R.string.agent_ai_area_provider), choices,
        selected = CloudProviders.SUPPORTED.indexOf(provider),
        onSelect = { i ->
            val target = CloudProviders.SUPPORTED[i]
            if (target != provider) runCatching { models.setProvider(target) }.onSuccess { onProvider(target) }
        },
    ) { KeyStatusRow(key) { onNavigate(SettingsDestination.AgentAi) } }
}

private enum class KeyState { CHECKING, SAVED, MISSING, UNAVAILABLE }

/** Lock icon, the status text and "Ändern" (opens the key entry of the old AI page). */
@Composable
private fun KeyStatusRow(key: KeyState, onChange: () -> Unit) {
    val t = AgentSettingsTokens
    val shape = RoundedCornerShape(t.RowRadius)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(t.Inset).border(1.dp, t.Border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp).testTag(KEY_STATUS_TAG),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LockIcon(if (key == KeyState.SAVED) KEY_SAVED else t.Chevron)
        Text(stringResource(when (key) {
            KeyState.SAVED -> R.string.agent_ai_area_key_saved
            KeyState.MISSING -> R.string.agent_ai_area_key_missing
            KeyState.CHECKING -> R.string.agent_ai_area_key_checking
            KeyState.UNAVAILABLE -> R.string.agent_ai_area_key_unavailable
        }), style = AgentSettingsType.Label, color = t.Soft, modifier = Modifier.weight(1f))
        val buttonShape = RoundedCornerShape(18.dp)
        Box(
            // 44 dp instead of the mockup's 40 dp: the minimum touch size of the settings (P120)
            Modifier.heightIn(min = t.MinTouch).widthIn(min = t.MinTouch).clip(buttonShape).border(1.dp, t.SwitchOff, buttonShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.agent_ai_area_key_change_label), onClick = onChange)
                .padding(horizontal = 12.dp, vertical = 8.dp).testTag(AREA_ROW_TAG + SettingsDestination.AgentAi),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.agent_ai_area_key_change), style = AgentSettingsType.Label.copy(fontWeight = FontWeight.SemiBold), color = t.Text)
        }
    }
}

/** The lock of the mockup: body 14 × 9 at (5, 11), radius 2, shackle 8..16 up to y 4, in an 18 dp box (viewBox 24). */
@Composable
private fun LockIcon(color: Color) {
    Canvas(Modifier.size(18.dp).clearAndSetSemantics { }) {
        val s = size.width / 24f
        val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round)
        drawRoundRect(color, Offset(5 * s, 11 * s), Size(14 * s, 9 * s), CornerRadius(2 * s), style = stroke)
        drawLine(color, Offset(8 * s, 11 * s), Offset(8 * s, 8 * s), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(16 * s, 11 * s), Offset(16 * s, 8 * s), stroke.width, StrokeCap.Round)
        drawArc(color, 180f, 180f, false, Offset(8 * s, 4 * s), Size(8 * s, 8 * s), style = stroke)
    }
}

/** "Skill-Tasten": three tiles with slot number and the real name (label, else the action), each opening the skill page. */
@Composable
private fun SkillCard(skills: List<SkillBinding>?, failed: Boolean, onNavigate: (String) -> Unit) {
    FeatureCard(stringResource(R.string.agent_ai_area_skills)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { slot ->
                val binding = skills?.firstOrNull { it.slot == slot }
                val name = when {
                    failed -> stringResource(R.string.agent_ai_area_skill_unavailable)
                    binding == null -> ""
                    binding.label.isNotBlank() -> binding.label
                    else -> stringResource(binding.action.titleResource())
                }
                SkillTile(slot, name, SKILL_COLORS[slot - 1], { onNavigate(SettingsDestination.AgentSkills) },
                    // the first tile carries the row tag of the skill page (SettingsAreas, P119)
                    Modifier.weight(1f).fillMaxHeight().testTag(if (slot == 1) AREA_ROW_TAG + SettingsDestination.AgentSkills else SKILL_TAG + slot))
            }
        }
    }
}

@Composable
private fun SkillTile(slot: Int, name: String, color: Color, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    val label = stringResource(R.string.agent_settings_open, stringResource(R.string.agent_ai_area_skill_label, slot, name))
    Column(
        modifier.heightIn(min = 96.dp).clip(shape).background(color)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(slot.toString(), style = AgentSettingsType.SectionTitle.copy(fontSize = 22.sp), color = AgentSettingsTokens.OnGradient)
        Text(name, style = AgentSettingsType.Small.copy(fontWeight = FontWeight.SemiBold), color = AgentSettingsTokens.OnGradient)
    }
}

/** "Texte": target language and tone with the lists of the old AI page. */
@Composable
private fun TextsSection(language: TranslationLanguage, tone: WritingTone, onLanguage: (TranslationLanguage) -> Unit, onTone: (WritingTone) -> Unit) {
    val area = SettingsArea.AI
    var menu by remember { mutableStateOf<String?>(null) }
    SectionCard(stringResource(R.string.agent_ai_area_texts)) {
        Box {
            SettingRow(stringResource(R.string.agent_ai_area_language), stringResource(R.string.agent_ai_area_language_summary), area.dot,
                { menu = LANGUAGE_ROW }, Modifier.testTag(AI_ROW_TAG + LANGUAGE_ROW), languageName(language))
            DropdownMenu(expanded = menu == LANGUAGE_ROW, onDismissRequest = { menu = null }) {
                TranslationLanguage.entries.forEach { target ->
                    DropdownMenuItem(text = { Text(languageName(target)) }, onClick = { menu = null; onLanguage(target) })
                }
            }
        }
        Box {
            SettingRow(stringResource(R.string.agent_ai_area_tone), stringResource(R.string.agent_ai_area_tone_summary), area.dot,
                { menu = TONE_ROW }, Modifier.testTag(AI_ROW_TAG + TONE_ROW), stringResource(tone.titleResource()))
            DropdownMenu(expanded = menu == TONE_ROW, onDismissRequest = { menu = null }) {
                WritingTone.entries.forEach { target ->
                    DropdownMenuItem(text = { Text(stringResource(target.titleResource())) }, onClick = { menu = null; onTone(target) })
                }
            }
        }
    }
}

/** "Workflows": the real count and the limits of [WorkflowDefinition]; all three rows open the workflow page. */
@Composable
private fun WorkflowsSection(workflows: List<WorkflowDefinition>?, onNavigate: (String) -> Unit) {
    val area = SettingsArea.AI
    val open = { onNavigate(SettingsDestination.AgentWorkflows) }
    SectionCard(stringResource(R.string.agent_ai_area_workflows)) {
        SettingRow(stringResource(R.string.agent_ai_area_my_workflows),
            stringResource(R.string.agent_ai_area_my_workflows_summary, WorkflowDefinition.MAX_STEPS, WorkflowDefinition.MAX_MODEL_CALLS),
            area.dot, open, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentWorkflows), workflows?.size?.toString())
        SettingRow(stringResource(R.string.agent_ai_area_new_workflow), stringResource(R.string.agent_ai_area_new_workflow_summary),
            area.dot, open, Modifier.testTag(AI_ROW_TAG + NEW_WORKFLOW_ROW), "+")
        SettingRow(stringResource(R.string.agent_ai_area_import_export), stringResource(R.string.agent_ai_area_import_export_summary),
            area.dot, open, Modifier.testTag(AI_ROW_TAG + IMPORT_EXPORT_ROW))
    }
}

/** "Diktat & Verlauf": the on-device recognizer and the microphone permission as the dictation page reads them; the run status of the old AI page. */
@Composable
private fun DictationSection(dictationAvailable: (() -> Boolean)?, onNavigate: (String) -> Unit) {
    val area = SettingsArea.AI
    val context = LocalContext.current
    fun available() = runCatching { dictationAvailable?.invoke() ?: OnDeviceSpeechEngine(context).available }.getOrDefault(false)
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var recognizer by remember { mutableStateOf(available()) }
    var allowed by remember { mutableStateOf(granted()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { recognizer = available(); allowed = granted() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    SectionCard(stringResource(R.string.agent_ai_area_dictation_history)) {
        SettingRow(stringResource(R.string.agent_ai_area_dictation),
            stringResource(if (allowed) R.string.agent_ai_area_dictation_allowed else R.string.agent_ai_area_dictation_not_allowed),
            area.dot, { onNavigate(SettingsDestination.AgentDictation) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentDictation),
            stringResource(when {
                !recognizer -> R.string.agent_ai_area_dictation_unavailable
                !allowed -> R.string.agent_ai_area_dictation_permission
                else -> R.string.agent_ai_area_dictation_ready
            }))
        SettingRow(stringResource(R.string.agent_ai_area_runs),
            stringResource(R.string.agent_ai_area_runs_summary, EncryptedActionRunStore.MAX_RECORDS,
                TimeUnit.MILLISECONDS.toDays(EncryptedActionRunStore.MAX_AGE_MS).toInt()),
            area.dot, { onNavigate(SettingsDestination.AgentAi) }, Modifier.testTag(AI_ROW_TAG + RUNS_ROW))
    }
}

private fun languageName(language: TranslationLanguage) =
    Locale.forLanguageTag(language.code).getDisplayLanguage(Locale.getDefault())

/** The gradients of the mockup's provider cards (120°). */
private fun providerColors(provider: WorkflowProvider): Pair<Color, Color> = when (provider) {
    WorkflowProvider.CLAUDE -> Color(0xFFFFB547) to Color(0xFFFFE0A3)
    WorkflowProvider.GEMINI -> Color(0xFF818CF8) to Color(0xFFC7D2FE)
    else -> Color(0xFF22D3EE) to Color(0xFFA5F3FC)
}

private val SKILL_COLORS = listOf(Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFFA5B4FC))
private val KEY_SAVED = Color(0xFF7CF0C8)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

const val PROVIDER_TAG = "agent_ai_provider_"
const val KEY_STATUS_TAG = "agent_ai_key_status"
const val SKILL_TAG = "agent_ai_skill_"
const val AI_ROW_TAG = "agent_ai_row_"
const val AI_LOCKED_TAG = "agent_ai_locked"
const val LANGUAGE_ROW = "language"
const val TONE_ROW = "tone"
const val NEW_WORKFLOW_ROW = "new_workflow"
const val IMPORT_EXPORT_ROW = "import_export"
const val RUNS_ROW = "runs"
