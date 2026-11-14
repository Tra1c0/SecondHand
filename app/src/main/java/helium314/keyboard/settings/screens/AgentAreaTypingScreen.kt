// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.SharedPreferences
import android.provider.UserDictionary
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.LearningData
import helium314.keyboard.agent.SpaceGestures
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getDictionaryLocales
import helium314.keyboard.latin.utils.htmlToAnnotated
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.withHtmlLink
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.InfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stage 38c: the three autocorrect levels of the typing page, mapped onto existing preferences.
 * A level matches when the preferences are as listed; picking a level writes exactly these keys.
 * - OFF: `auto_correction` off (everything else is kept for when it is switched on again).
 * - GENTLE: `auto_correction` on, "Richtige Wörter nie ändern" on, "Umlaut-Wörter behalten" on,
 *   confidence in HeliBoard's band "Mäßig" (at most 0.40; outside it is set to the default 0.24).
 * - BOLD: `auto_correction` on, "Richtige Wörter nie ändern" off, confidence in the band
 *   "Sehr stark" (above 0.80; outside it is set to 0.9). The umlaut rule stays as it is.
 * Other combinations (for example HeliBoard's defaults) match no level ("Eigene Einstellung").
 */
object TypingAutocorrect {
    enum class Level { OFF, GENTLE, BOLD }

    /** Upper end of HeliBoard's confidence label "Mäßig" (TextCorrectionScreen). */
    const val MODEST_MAX = 0.40f
    /** Above this HeliBoard's confidence label is "Sehr stark". */
    const val VERY_STRONG_ABOVE = 0.80f
    const val GENTLE_CONFIDENCE = Defaults.PREF_AUTO_CORRECT_CONFIDENCE
    const val BOLD_CONFIDENCE = 0.9f

    private fun confidence(prefs: SharedPreferences) =
        prefs.getFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, Defaults.PREF_AUTO_CORRECT_CONFIDENCE)
    private fun keepValid(prefs: SharedPreferences) = prefs.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false)
    private fun keepUmlaut(prefs: SharedPreferences) = prefs.getBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true)

    /** The level the preferences describe, or null for a custom combination. */
    fun current(prefs: SharedPreferences): Level? {
        if (!prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION)) return Level.OFF
        val c = confidence(prefs)
        return when {
            keepValid(prefs) && keepUmlaut(prefs) && c <= MODEST_MAX -> Level.GENTLE
            !keepValid(prefs) && c > VERY_STRONG_ABOVE -> Level.BOLD
            else -> null
        }
    }

    fun apply(prefs: SharedPreferences, level: Level) {
        val c = confidence(prefs)
        prefs.edit {
            when (level) {
                Level.OFF -> putBoolean(Settings.PREF_AUTO_CORRECTION, false)
                Level.GENTLE -> {
                    putBoolean(Settings.PREF_AUTO_CORRECTION, true)
                    putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, true)
                    putBoolean(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true)
                    if (c > MODEST_MAX) putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, GENTLE_CONFIDENCE)
                }
                Level.BOLD -> {
                    putBoolean(Settings.PREF_AUTO_CORRECTION, true)
                    putBoolean(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false)
                    if (c <= VERY_STRONG_ABOVE) putFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, BOLD_CONFIDENCE)
                }
            }
        }
    }
}

/**
 * Stage 38c: the area page "Tippen & Korrektur", 1:1 from Tippen.dc.html. Hero, the autocorrect
 * levels ([TypingAutocorrect]), the sections Regeln, Vorschläge, Leerzeichen & Satzzeichen and
 * Wörter & Lernen on the real preferences, and a row to the full correction page for the rest.
 * Rows that open a page carry [AREA_ROW_TAG] + route, as listed in [SettingsAreas.entries].
 */
@Composable
fun AgentAreaTypingScreen(onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    val area = SettingsArea.TYPING
    val context = LocalContext.current
    val prefs = context.prefs()
    // recompose when a preference changes elsewhere (SettingsActivity counts) or here (P121)
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    var written by remember { mutableIntStateOf(0) }
    if ((changed?.value ?: 0) + written < 0) Log.v("irrelevant", "read both counters to recompose on a change")
    // rows read through bool() inside their own scope, so they observe both counters there too
    fun observe() = (changed?.value ?: 0) + written
    fun bool(key: String, default: Boolean): Boolean { observe(); return prefs.getBoolean(key, default) }
    fun put(key: String, value: Boolean, reload: Boolean = false) {
        prefs.edit { putBoolean(key, value) }
        if (reload) KeyboardSwitcher.getInstance().setThemeNeedsReload()
        written++
    }
    var emojiWarning by remember { mutableStateOf(false) }
    val counts by produceState<WordCounts?>(null) { value = withContext(Dispatchers.IO) { WordCounts.read(context) } }

    val autocorrect = bool(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION)
    val stripShown = Settings.readToolbarMode(prefs).let { it == ToolbarMode.SUGGESTION_STRIP || it == ToolbarMode.EXPANDABLE }
    val suggestions = stripShown && bool(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS)
    val hasEmojiDict = remember { DictionaryInfoUtils.getLocalesWithEmojiDicts(context).isNotEmpty() }
    val needsAutocorrect = stringResource(R.string.agent_typing_needs_autocorrect)

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

            val level = TypingAutocorrect.current(prefs)
            val levels = TypingAutocorrect.Level.entries
            ChoiceCards(
                stringResource(R.string.agent_typing_autocorrect),
                listOf(
                    Choice(stringResource(R.string.agent_typing_level_off), stringResource(R.string.agent_typing_level_off_hint),
                        Color(0xFF3A3A42), Color(0xFF6B6B76), TYPING_LEVEL_TAG + "off"),
                    Choice(stringResource(R.string.agent_typing_level_gentle), stringResource(R.string.agent_typing_level_gentle_hint),
                        Color(0xFF00C2A8), Color(0xFF7CF0C8), TYPING_LEVEL_TAG + "gentle"),
                    Choice(stringResource(R.string.agent_typing_level_bold), stringResource(R.string.agent_typing_level_bold_hint),
                        Color(0xFFFFC94D), Color(0xFFFF9F43), TYPING_LEVEL_TAG + "bold"),
                ),
                selected = level?.ordinal ?: -1,
                onSelect = { TypingAutocorrect.apply(prefs, levels[it]); written++ },
            ) {
                if (level == null) Text(stringResource(R.string.agent_typing_level_custom), style = AgentSettingsType.Small,
                    color = AgentSettingsTokens.Muted, modifier = Modifier.testTag(TYPING_CUSTOM_TAG))
            }

            SectionCard(stringResource(R.string.agent_typing_rules)) {
                PrefSwitch(R.string.agent_autocorrect_keep_umlaut, stringResource(R.string.agent_typing_keep_umlaut_sub),
                    AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true, autocorrect, needsAutocorrect, ::bool, ::put)
                PrefSwitch(R.string.agent_autocorrect_remember, stringResource(R.string.agent_typing_remember_sub),
                    AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, false, autocorrect, needsAutocorrect, ::bool, ::put)
                PrefSwitch(R.string.agent_autocorrect_learn, stringResource(R.string.agent_typing_learn_sub),
                    AgentPreferences.AUTOCORRECT_LEARN_TYPOS, false, autocorrect, needsAutocorrect, ::bool, ::put)
                PrefSwitch(R.string.agent_typing_auto_cap, stringResource(R.string.agent_typing_auto_cap_sub),
                    Settings.PREF_AUTO_CAP, Defaults.PREF_AUTO_CAP, true, null, ::bool, ::put)
                PrefSwitch(R.string.agent_typing_shortcuts, stringResource(R.string.agent_typing_shortcuts_sub),
                    Settings.PREF_AUTOCORRECT_SHORTCUTS, Defaults.PREF_AUTOCORRECT_SHORTCUTS, autocorrect, needsAutocorrect, ::bool, ::put)
                // stage 41: hints only, works without autocorrection
                PrefSwitch(R.string.agent_format_guard, stringResource(R.string.agent_format_guard_sub),
                    AgentPreferences.FORMAT_GUARD, true, true, null, ::bool, ::put)
            }

            SectionCard(stringResource(R.string.agent_typing_suggestions)) {
                PrefSwitch(R.string.agent_typing_show_suggestions, stringResource(R.string.agent_typing_show_suggestions_sub),
                    Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS, stripShown,
                    stringResource(R.string.agent_typing_show_suggestions_hidden), ::bool, ::put)
                PrefSwitch(R.string.agent_typing_next_word, stringResource(R.string.agent_typing_next_word_sub),
                    Settings.PREF_BIGRAM_PREDICTIONS, Defaults.PREF_BIGRAM_PREDICTIONS, true, null, ::bool) { k, v -> put(k, v, reload = true) }
                // as SwitchPreferenceWithEmojiDictWarning: off by default without an emoji dictionary, warn when switched on
                PrefSwitch(R.string.agent_typing_emoji, stringResource(R.string.agent_typing_emoji_sub),
                    Settings.PREF_SUGGEST_EMOJIS, Defaults.PREF_SUGGEST_EMOJIS && hasEmojiDict, suggestions || autocorrect,
                    stringResource(R.string.agent_typing_emoji_off), ::bool) { k, v -> put(k, v); if (v && !hasEmojiDict) emojiWarning = true }
                PrefSwitch(R.string.agent_typing_punctuation, stringResource(R.string.agent_typing_punctuation_sub),
                    Settings.PREF_SUGGEST_PUNCTUATION, Defaults.PREF_SUGGEST_PUNCTUATION, true, null, ::bool) { k, v -> put(k, v, reload = true) }
                // contacts need the permission flow of the correction page, so the row opens it
                val names = listOfNotNull(
                    if (bool(Settings.PREF_USE_CONTACTS, Defaults.PREF_USE_CONTACTS)) stringResource(R.string.agent_typing_names_contacts) else null,
                    if (bool(Settings.PREF_USE_APPS, Defaults.PREF_USE_APPS)) stringResource(R.string.agent_typing_names_apps) else null,
                ).joinToString(", ").ifEmpty { stringResource(R.string.agent_typing_names_off) }
                SettingRow(stringResource(R.string.agent_typing_names), stringResource(R.string.agent_typing_names_sub), area.dot,
                    { onNavigate(SettingsDestination.TextCorrection) }, Modifier.testTag(TYPING_NAMES_TAG), value = names)
            }

            SectionCard(stringResource(R.string.agent_typing_space)) {
                val gesture = SpaceGestures.doubleTapAssigned(prefs)
                PrefSwitch(R.string.agent_typing_double_space, stringResource(R.string.agent_typing_double_space_sub),
                    Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, Defaults.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, !gesture,
                    stringResource(R.string.agent_typing_double_space_gesture), ::bool, ::put)
                PrefSwitch(R.string.agent_typing_space_after_period, stringResource(R.string.agent_typing_space_after_period_sub),
                    Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, Defaults.PREF_AUTOSPACE_AFTER_PUNCTUATION, true, null, ::bool, ::put)
                PrefSwitch(R.string.agent_typing_space_after_suggestion, stringResource(R.string.agent_typing_space_after_suggestion_sub),
                    Settings.PREF_AUTOSPACE_AFTER_SUGGESTION, Defaults.PREF_AUTOSPACE_AFTER_SUGGESTION, true, null, ::bool, ::put)
            }

            SectionCard(stringResource(R.string.agent_typing_words)) {
                val dictionaries = counts?.dictionaries
                SettingRow(stringResource(R.string.agent_typing_dictionaries),
                    when { dictionaries == null -> null; dictionaries.isEmpty() -> stringResource(R.string.agent_typing_dictionaries_none)
                        else -> dictionaries.joinToString(", ") }, area.dot,
                    { onNavigate(SettingsDestination.Dictionaries) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.Dictionaries),
                    value = dictionaries?.size?.toString())
                SettingRow(stringResource(R.string.agent_typing_personal), stringResource(R.string.agent_typing_personal_sub), area.dot,
                    { onNavigate(SettingsDestination.PersonalDictionaries) },
                    Modifier.testTag(AREA_ROW_TAG + SettingsDestination.PersonalDictionaries), value = counts?.personalWords?.toString())
                SettingRow(stringResource(R.string.agent_typing_learned), stringResource(R.string.agent_typing_learned_sub), area.dot,
                    { onNavigate(SettingsDestination.AgentLearning) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentLearning))
                // stage 46: the memory (phrases, own words, abbreviations)
                SettingRow(stringResource(R.string.agent_memory_title), stringResource(R.string.agent_memory_row_sub), area.dot,
                    { onNavigate(SettingsDestination.AgentMemory) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentMemory))
                // pausing keeps what was learned (no deletion warning); switching learning off stays on the correction page
                TypingSwitchRow(stringResource(R.string.agent_learning_pause), stringResource(R.string.agent_typing_pause_sub),
                    { observe(); LearningData.isPaused(context) }, true, TYPING_SWITCH_TAG + AgentPreferences.LEARNING_PAUSED) { value ->
                    runCatching { LearningData.setPaused(context, value) }
                    written++
                }
                PrefSwitch(R.string.agent_typing_incognito, stringResource(R.string.agent_typing_incognito_sub),
                    Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE, true, null, ::bool) { key, value ->
                        put(key, value, reload = true) // like the old Advanced page
                    }
            }

            SectionCard(null) {
                SettingRow(stringResource(R.string.agent_typing_all), stringResource(R.string.agent_typing_all_sub), area.dot,
                    { onNavigate(SettingsDestination.TextCorrection) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.TextCorrection))
            }
        }
        if (emojiWarning) {
            val link = stringResource(R.string.dictionary_link_text).withHtmlLink(Links.DICTIONARY_URL +
                Links.DICTIONARY_DOWNLOAD_SUFFIX.replace("raw", "src") + Links.DICTIONARY_EMOJI_CLDR_SUFFIX)
            InfoDialog(stringResource(R.string.emoji_dictionary_required, link).htmlToAnnotated(), onDismissRequest = { emojiWarning = false })
        }
    }
}

/** A switch row bound to one boolean preference; [enabled] false greys it out and shows [disabledHint]. */
@Composable
private fun PrefSwitch(
    title: Int,
    summary: String,
    key: String,
    default: Boolean,
    enabled: Boolean,
    disabledHint: String?,
    read: (String, Boolean) -> Boolean,
    write: (String, Boolean) -> Unit,
) {
    TypingSwitchRow(stringResource(title), if (enabled || disabledHint == null) summary else disabledHint,
        { read(key, default) }, enabled, TYPING_SWITCH_TAG + key) { write(key, it) }
}

/**
 * [SettingSwitchRow] of the kit with an enabled state: disabled rows are at 45 % and cannot be
 * toggled (TalkBack reads them as disabled).
 */
@Composable
private fun TypingSwitchRow(title: String, summary: String, checked: () -> Boolean, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val area = SettingsArea.TYPING
    val on = checked() // read here, so a changed preference recomposes this row
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

/** Values of the nav rows that need disk or a content provider, read once off the main thread. */
class WordCounts(val dictionaries: List<String>, val personalWords: Int?) {
    companion object {
        fun read(context: Context): WordCounts {
            val languages = SubtypeSettings.getEnabledSubtypes(true).map { it.locale().language }.toSet()
            val dictionaries = runCatching {
                getDictionaryLocales(context).filter { it.language in languages }
                    .map { it.localizedDisplayName(context.resources) }.distinct().sorted()
            }.getOrDefault(emptyList())
            // all words of the personal dictionary, every language; null when the provider is not there
            val personal = runCatching {
                context.contentResolver.query(UserDictionary.Words.CONTENT_URI, arrayOf(UserDictionary.Words._ID), null, null, null)
                    ?.use { it.count }
            }.getOrNull()
            return WordCounts(dictionaries, personal)
        }
    }
}

const val TYPING_LEVEL_TAG = "typing_level_"
const val TYPING_CUSTOM_TAG = "typing_level_custom"
const val TYPING_SWITCH_TAG = "typing_switch_"
const val TYPING_NAMES_TAG = "typing_names"
