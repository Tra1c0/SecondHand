// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.SharedPreferences
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.runInLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.checkTimestampFormat
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.htmlToAnnotated
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.withHtmlLink
import helium314.keyboard.latin.common.Links
import helium314.keyboard.settings.GetIconOrEmpty
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.ReorderDialog
import helium314.keyboard.settings.dialogs.SliderDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import java.util.Locale

/*
 * Stage 38d: the area page "Tasten & Sprachen", 1:1 from docs/referenzen/einstellungen-mockup/Tasten.dc.html.
 * Every row reads and writes the real preference with the same side effects as the old pages
 * (Optionen, Erweitert) and opens the same dialogs; the old pages stay reachable.
 */

/** Gradients of the language cards, in the order of the mockup, then repeated. */
private val languageGradients = listOf(
    Color(0xFF5B8CFF) to Color(0xFFA9C7FF),
    Color(0xFF22D3EE) to Color(0xFFA5F3FC),
    Color(0xFFFFB547) to Color(0xFFFFE0A3),
)

/** One enabled language as shown on its card. */
class LanguageCardInfo(val subtype: InputMethodSubtype, val label: String, val hint: String, val active: Boolean)

/**
 * The cards of the enabled languages: label = the language in its own language, hint = layout
 * and the dictionaries this language has, plus "aktiv" for the language the keyboard uses now.
 */
fun languageCards(context: Context, prefs: SharedPreferences): List<LanguageCardInfo> {
    val selected = SubtypeSettings.getSelectedSubtype(prefs).toSettingsSubtype()
    val res = context.resources
    return SubtypeSettings.getEnabledSubtypes(true).map { subtype ->
        val locale = subtype.locale()
        val parts = mutableListOf(SubtypeLocaleUtils.getLayoutDisplayNameInSystemLocale(subtype.mainLayoutNameOrQwerty(), locale))
        if (locale.language != SubtypeLocaleUtils.NO_LANGUAGE) {
            if (hasMainDictionary(context, locale)) parts += res.getString(R.string.agent_keys_hint_main_dict)
            if (hasEmojiDictionary(context, locale)) parts += res.getString(R.string.agent_keys_hint_emoji_dict)
            if (parts.size == 1) parts += res.getString(R.string.agent_keys_hint_no_dict)
        }
        val active = subtype.toSettingsSubtype() == selected
        if (active) parts += res.getString(R.string.agent_keys_hint_active)
        // P128: the name in its own language through a configuration context, never displayName(locale)
        val label = runInLocale(context, locale) { locale.localizedDisplayName(it.resources) }
        LanguageCardInfo(subtype, label, parts.joinToString(" · "), active)
    }
}

private fun hasMainDictionary(context: Context, locale: Locale): Boolean {
    val (userDicts, hasInternal) = getUserAndInternalDictionaries(context, locale)
    return hasInternal || userDicts.any { it.name.startsWith(Dictionary.TYPE_MAIN) }
}

/** Read only: an emoji dictionary in the cache folder or bundled for this language (nothing is extracted here). */
private fun hasEmojiDictionary(context: Context, locale: Locale): Boolean {
    if (DictionaryInfoUtils.getCachedDictsForLocale(locale, context).any { it.name.substringBefore("_").substringBefore(".") == Dictionary.TYPE_EMOJI })
        return true
    val bundled = DictionaryInfoUtils.getAssetsDictionaryList(context)?.filter { it.substringBefore("_") == Dictionary.TYPE_EMOJI }
    if (bundled.isNullOrEmpty()) return false
    return LocaleUtils.getBestMatch(locale, bundled) { DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(it) } != null
}

/** The route of the existing page of one language (layouts, dictionaries, secondary languages). */
fun subtypeRoute(subtype: InputMethodSubtype) = SettingsDestination.Subtype + subtype.toSettingsSubtype().toPref()

/** Which open dialog the page shows. */
private enum class KeysDialog { SWITCH_BEHAVIOR, CURRENCIES, HINT_SOURCE, POPUP_ORDER, MORE_POPUP_KEYS, LONG_PRESS, TIMESTAMP, EMOJI_DICT }

@Composable
fun AgentAreaKeysScreen(onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    val area = SettingsArea.KEYS
    val context = LocalContext.current
    val prefs = context.prefs()
    // P121: recompose when a preference changes elsewhere (SettingsActivity counts) or here
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    var written by remember { mutableIntStateOf(0) }
    if ((changed?.value ?: 0) + written < 0) Log.v("irrelevant", "read both counters to recompose on a change")
    var dialog by rememberSaveable { mutableStateOf<KeysDialog?>(null) }
    var returnOpen by rememberSaveable { mutableStateOf(false) }

    fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun write(change: SharedPreferences.Editor.() -> Unit, after: () -> Unit = {}) { prefs.edit { change() }; after(); written++ }
    val reload = { KeyboardSwitcher.getInstance().reloadKeyboard() }
    val themeReload = { KeyboardSwitcher.getInstance().setThemeNeedsReload() }

    @Composable
    fun SwitchRow(key: String, default: Boolean, title: String, summary: String?, after: () -> Unit = {}) {
        SettingSwitchRow(title, summary, area.dot, bool(key, default), { v -> write({ putBoolean(key, v) }, after) },
            area.from, area.to, Modifier.testTag(KEYS_ROW_TAG + key))
    }
    @Composable
    fun Nav(tag: String, title: String, summary: String?, value: String?, onClick: () -> Unit) {
        SettingRow(title, summary, area.dot, onClick, Modifier.testTag(tag), value)
    }

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

            // languages: one card per enabled language, a tap opens its page
            val cards = remember(SubtypeSettings.getEnabledSubtypes(true), prefs.getString(Settings.PREF_SELECTED_SUBTYPE, null)) {
                languageCards(context, prefs)
            }
            FeatureCard(stringResource(R.string.agent_keys_languages_card), spaced = true) {
                cards.forEachIndexed { i, card -> LanguageCard(card, i) { onNavigate(subtypeRoute(card.subtype)) } }
            }

            SectionCard(stringResource(R.string.agent_keys_section_languages)) {
                Nav(AREA_ROW_TAG + SettingsDestination.Languages, stringResource(R.string.agent_keys_add_language),
                    stringResource(R.string.agent_keys_add_language_sub), "+") { onNavigate(SettingsDestination.Languages) }
                SwitchRow(Settings.PREF_SAVE_SUBTYPE_PER_APP, Defaults.PREF_SAVE_SUBTYPE_PER_APP,
                    stringResource(R.string.save_subtype_per_app), stringResource(R.string.agent_keys_per_app_sub))
                SwitchRow(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY,
                    stringResource(R.string.show_language_switch_key), stringResource(R.string.agent_keys_switch_key_sub), reload)
                Nav(KEYS_ROW_TAG + Settings.PREF_LANGUAGE_SWITCH_KEY, stringResource(R.string.language_switch_key_behavior),
                    switchBehaviors().firstOrNull { it.second == prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, Defaults.PREF_LANGUAGE_SWITCH_KEY) }?.first, null
                ) { dialog = KeysDialog.SWITCH_BEHAVIOR }
            }

            SectionCard(stringResource(R.string.agent_keys_section_extra)) {
                val numberRow = bool(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW)
                SwitchRow(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW,
                    stringResource(R.string.number_row), stringResource(R.string.agent_keys_number_row_sub), themeReload)
                // the conditional rows of the old options page, under the same conditions
                if (SubtypeSettings.getEnabledSubtypes(true).any { it.locale().language in localesWithLocalizedNumberRow })
                    SwitchRow(Settings.PREF_LOCALIZED_NUMBER_ROW, Defaults.PREF_LOCALIZED_NUMBER_ROW,
                        stringResource(R.string.localized_number_row), stringResource(R.string.localized_number_row_summary)) {
                        KeyboardLayoutSet.onSystemLocaleChanged(); reload()
                    }
                if (bool(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS) && numberRow)
                    SwitchRow(Settings.PREF_SHOW_NUMBER_ROW_HINTS, Defaults.PREF_SHOW_NUMBER_ROW_HINTS,
                        stringResource(R.string.number_row_hints), null, themeReload)
                if (!numberRow)
                    SwitchRow(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, Defaults.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS,
                        stringResource(R.string.number_row_in_symbols), null, themeReload)
                SwitchRow(Settings.PREF_SHOW_EMOJI_KEY, Defaults.PREF_SHOW_EMOJI_KEY,
                    stringResource(R.string.show_emoji_key), stringResource(R.string.agent_keys_emoji_key_sub), reload)
                // emoji descriptions need an emoji dictionary, as on the old page (SwitchPreferenceWithEmojiDictWarning)
                val hasEmojiDict = remember(cards) { DictionaryInfoUtils.getLocalesWithEmojiDicts(context).isNotEmpty() }
                SettingSwitchRow(stringResource(R.string.show_emoji_descriptions), null, area.dot,
                    bool(Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, Defaults.PREF_SHOW_EMOJI_DESCRIPTIONS && hasEmojiDict),
                    { v -> write({ putBoolean(Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, v) }); if (v && !hasEmojiDict) dialog = KeysDialog.EMOJI_DICT },
                    area.from, area.to, Modifier.testTag(KEYS_ROW_TAG + Settings.PREF_SHOW_EMOJI_DESCRIPTIONS))
                SwitchRow(Settings.PREF_SHOW_DPAD_KEY, Defaults.PREF_SHOW_DPAD_KEY,
                    stringResource(R.string.agent_keys_dpad), stringResource(R.string.agent_keys_dpad_sub), reload)
                Nav(KEYS_ROW_TAG + Settings.PREF_CUSTOM_CURRENCY_KEY, stringResource(R.string.agent_keys_currencies),
                    stringResource(R.string.agent_keys_currencies_sub),
                    prefs.getString(Settings.PREF_CUSTOM_CURRENCY_KEY, Defaults.PREF_CUSTOM_CURRENCY_KEY)!!.ifBlank { stringResource(R.string.button_default) }
                ) { dialog = KeysDialog.CURRENCIES }
            }

            SectionCard(stringResource(R.string.agent_keys_section_popups)) {
                val hints = bool(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)
                SwitchRow(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS,
                    stringResource(R.string.agent_keys_hints), stringResource(R.string.agent_keys_hints_sub), reload)
                if (hints)
                    Nav(KEYS_ROW_TAG + Settings.PREF_POPUP_KEYS_HINT_ORDER, stringResource(R.string.hint_source),
                        orderSummary(context, prefs.getString(Settings.PREF_POPUP_KEYS_HINT_ORDER, Defaults.PREF_POPUP_KEYS_HINT_ORDER)!!), null
                    ) { dialog = KeysDialog.HINT_SOURCE }
                SwitchRow(Settings.PREF_SHOW_POPUP_HINTS, Defaults.PREF_SHOW_POPUP_HINTS,
                    stringResource(R.string.show_popup_hints), stringResource(R.string.show_popup_hints_summary), themeReload)
                SwitchRow(Settings.PREF_POPUP_ON, Defaults.PREF_POPUP_ON,
                    stringResource(R.string.popup_on_keypress), stringResource(R.string.agent_keys_popup_on_sub), reload)
                Nav(KEYS_ROW_TAG + Settings.PREF_POPUP_KEYS_ORDER, stringResource(R.string.agent_keys_popup_order),
                    orderSummary(context, prefs.getString(Settings.PREF_POPUP_KEYS_ORDER, Defaults.PREF_POPUP_KEYS_ORDER)!!), null
                ) { dialog = KeysDialog.POPUP_ORDER }
                SwitchRow(Settings.PREF_REMOVE_REDUNDANT_POPUPS, Defaults.PREF_REMOVE_REDUNDANT_POPUPS,
                    stringResource(R.string.agent_keys_redundant), stringResource(R.string.agent_keys_redundant_sub), themeReload)
                SwitchRow(Settings.PREF_SHOW_TLD_POPUP_KEYS, Defaults.PREF_SHOW_TLD_POPUP_KEYS,
                    stringResource(R.string.show_tld_popup_keys), stringResource(R.string.show_tld_popup_keys_summary), themeReload)
                // the values are long sentences: shown as summary, not on the right
                Nav(KEYS_ROW_TAG + Settings.PREF_MORE_POPUP_KEYS, stringResource(R.string.show_popup_keys_title),
                    stringResource(LocaleKeyboardInfos.morePopupKeysResId(prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)!!)), null
                ) { dialog = KeysDialog.MORE_POPUP_KEYS }
                Nav(KEYS_ROW_TAG + Settings.PREF_KEY_LONGPRESS_TIMEOUT, stringResource(R.string.agent_keys_long_press),
                    stringResource(R.string.agent_keys_long_press_sub),
                    stringResource(R.string.abbreviation_unit_milliseconds, prefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, Defaults.PREF_KEY_LONGPRESS_TIMEOUT).toString())
                ) { dialog = KeysDialog.LONG_PRESS }
            }

            SectionCard(stringResource(R.string.agent_keys_section_layouts)) {
                Nav(AREA_ROW_TAG + SettingsDestination.Layouts, stringResource(R.string.agent_keys_secondary),
                    stringResource(R.string.settings_screen_secondary_layouts), null) { onNavigate(SettingsDestination.Layouts) }
                val returns = returnSwitches.count { (key, default, _) -> bool(key, default) }
                Nav(KEYS_RETURN_TAG, stringResource(R.string.agent_keys_return), stringResource(R.string.agent_keys_return_sub),
                    stringResource(R.string.agent_keys_return_value, returns, returnSwitches.size)) { returnOpen = !returnOpen }
                if (returnOpen) returnSwitches.forEach { (key, default, title) ->
                    SwitchRow(key, default, stringResource(title), null)
                }
                // a short format on the right as in the mockup ("HH:mm"); a long one (the default) under the title,
                // the value column of SettingRow has no width limit and would squeeze the title
                val format = prefs.getString(Settings.PREF_TIMESTAMP_FORMAT, Defaults.PREF_TIMESTAMP_FORMAT).orEmpty()
                val sub = stringResource(R.string.agent_keys_timestamp_sub)
                Nav(KEYS_ROW_TAG + Settings.PREF_TIMESTAMP_FORMAT, stringResource(R.string.agent_keys_timestamp),
                    if (format.length > 10) "$sub · $format" else sub, format.takeIf { it.length <= 10 }) { dialog = KeysDialog.TIMESTAMP }
            }

            // the old options page for everything not on an area page (vibration/sound: 38e, clipboard: 38f)
            SectionCard(null) {
                Nav(AREA_ROW_TAG + SettingsDestination.Preferences, stringResource(R.string.agent_keys_more_options),
                    stringResource(R.string.agent_keys_more_options_sub), null) { onNavigate(SettingsDestination.Preferences) }
            }
        }

        KeysDialogs(dialog, prefs, { dialog = null }, { written++ })
    }
}

/** Short summary of a popup order: the enabled entries, named as in the reorder dialog. */
private fun orderSummary(context: Context, value: String): String =
    value.split(Separators.ENTRY).map { it.split(Separators.KV) }.filter { it.last().toBoolean() }
        .joinToString(", ") { orderEntryName(it.first(), context) }

private fun orderEntryName(name: String, context: Context): String {
    val text = name.lowercase().getStringResourceOrName("", context)
    return if (text != name.lowercase()) text else name.lowercase().getStringResourceOrName("popup_keys_", context)
}

@Composable
private fun switchBehaviors() = listOf(
    stringResource(R.string.switch_language) to "internal",
    stringResource(R.string.language_switch_key_switch_input_method) to "input_method",
    stringResource(R.string.language_switch_key_switch_both) to "both",
)

/** The four "back to the letters" switches of Erweitert, in its order. */
private val returnSwitches = listOf(
    Triple(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, Defaults.PREF_ABC_AFTER_SYMBOL_SPACE, R.string.after_symbol_and_space),
    Triple(Settings.PREF_ABC_AFTER_NUMPAD_SPACE, Defaults.PREF_ABC_AFTER_NUMPAD_SPACE, R.string.after_numpad_and_space),
    Triple(Settings.PREF_ABC_AFTER_EMOJI, Defaults.PREF_ABC_AFTER_EMOJI, R.string.after_emoji),
    Triple(Settings.PREF_ABC_AFTER_CLIP, Defaults.PREF_ABC_AFTER_CLIP, R.string.after_clip),
)

private val localesWithLocalizedNumberRow = listOf("ar", "bn", "ckb", "fa", "gu", "hi", "kn", "mr", "ne", "th", "ur")

private class OrderItem(val name: String, var state: Boolean)

/** The dialogs of the old pages, with the same writes and side effects. */
@Composable
private fun KeysDialogs(dialog: KeysDialog?, prefs: SharedPreferences, onDismiss: () -> Unit, onWritten: () -> Unit) {
    fun edit(change: SharedPreferences.Editor.() -> Unit) { prefs.edit { change() }; onWritten() }
    when (dialog) {
        null -> {}
        KeysDialog.SWITCH_BEHAVIOR -> {
            val items = switchBehaviors()
            val selected = items.firstOrNull { it.second == prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, Defaults.PREF_LANGUAGE_SWITCH_KEY) }
            ListPickerDialog(
                onDismissRequest = onDismiss, items = items, selectedItem = selected,
                onItemSelected = {
                    if (it == selected) return@ListPickerDialog
                    edit { putString(Settings.PREF_LANGUAGE_SWITCH_KEY, it.second) }
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                },
                title = { Text(stringResource(R.string.language_switch_key_behavior)) }, getItemName = { it.first },
            )
        }
        KeysDialog.MORE_POPUP_KEYS -> {
            val items = listOf(LocaleKeyboardInfos.POPUP_KEYS_NORMAL, LocaleKeyboardInfos.POPUP_KEYS_MAIN,
                LocaleKeyboardInfos.POPUP_KEYS_MORE, LocaleKeyboardInfos.POPUP_KEYS_ALL)
                .map { stringResource(LocaleKeyboardInfos.morePopupKeysResId(it)) to it }
            val selected = items.firstOrNull { it.second == prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS) }
            ListPickerDialog(
                onDismissRequest = onDismiss, items = items, selectedItem = selected,
                onItemSelected = {
                    if (it == selected) return@ListPickerDialog
                    edit { putString(Settings.PREF_MORE_POPUP_KEYS, it.second) }
                    KeyboardLayoutSet.onSystemLocaleChanged()
                },
                title = { Text(stringResource(R.string.show_popup_keys_title)) }, getItemName = { it.first },
            )
        }
        KeysDialog.CURRENCIES -> TextInputDialog(
            onDismissRequest = onDismiss,
            textInputLabel = { Text(stringResource(R.string.customize_currencies_detail)) },
            initialText = prefs.getString(Settings.PREF_CUSTOM_CURRENCY_KEY, Defaults.PREF_CUSTOM_CURRENCY_KEY)!!,
            onConfirmed = { edit { putString(Settings.PREF_CUSTOM_CURRENCY_KEY, it) }; KeyboardLayoutSet.onSystemLocaleChanged() },
            title = { Text(stringResource(R.string.customize_currencies)) },
            neutralButtonText = if (prefs.contains(Settings.PREF_CUSTOM_CURRENCY_KEY)) stringResource(R.string.button_default) else null,
            onNeutral = { edit { remove(Settings.PREF_CUSTOM_CURRENCY_KEY) }; KeyboardLayoutSet.onSystemLocaleChanged() },
            checkTextValid = { text -> text.splitOnWhitespace().none { it.length > 8 } },
        )
        KeysDialog.TIMESTAMP -> TextInputDialog(
            onDismissRequest = onDismiss,
            onConfirmed = { edit { putString(Settings.PREF_TIMESTAMP_FORMAT, it) }; KeyboardSwitcher.getInstance().setThemeNeedsReload() },
            initialText = prefs.getString(Settings.PREF_TIMESTAMP_FORMAT, Defaults.PREF_TIMESTAMP_FORMAT) ?: "",
            title = { Text(stringResource(R.string.timestamp_format_title)) },
            description = { Text(stringResource(R.string.timestamp_description)) },
            checkTextValid = { checkTimestampFormat(it) },
            onNeutral = { edit { remove(Settings.PREF_TIMESTAMP_FORMAT) }; onDismiss() },
            neutralButtonText = stringResource(R.string.button_default),
        )
        KeysDialog.LONG_PRESS -> SliderDialog(
            onDismissRequest = onDismiss,
            onDone = { edit { putInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, it.toInt()) } },
            initialValue = prefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, Defaults.PREF_KEY_LONGPRESS_TIMEOUT).toFloat(),
            range = 100f..700f,
            title = { Text(stringResource(R.string.prefs_key_longpress_timeout_settings)) },
            positionString = { stringResource(R.string.abbreviation_unit_milliseconds, it.toInt().toString()) },
            showDefault = true,
            onDefault = { edit { remove(Settings.PREF_KEY_LONGPRESS_TIMEOUT) } },
            defaultValue = Defaults.PREF_KEY_LONGPRESS_TIMEOUT.toFloat(),
            integerValues = true,
        )
        KeysDialog.HINT_SOURCE -> OrderDialog(Settings.PREF_POPUP_KEYS_HINT_ORDER, Defaults.PREF_POPUP_KEYS_HINT_ORDER,
            stringResource(R.string.hint_source), prefs, onDismiss, onWritten)
        KeysDialog.POPUP_ORDER -> OrderDialog(Settings.PREF_POPUP_KEYS_ORDER, Defaults.PREF_POPUP_KEYS_ORDER,
            stringResource(R.string.popup_order), prefs, onDismiss, onWritten)
        KeysDialog.EMOJI_DICT -> {
            // same text as SwitchPreferenceWithEmojiDictWarning
            val link = stringResource(R.string.dictionary_link_text).withHtmlLink(Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX.replace("raw", "src")
                + Links.DICTIONARY_EMOJI_CLDR_SUFFIX)
            InfoDialog(stringResource(R.string.emoji_dictionary_required, link).htmlToAnnotated(), onDismissRequest = onDismiss)
        }
    }
}

/** The reorder dialog of ReorderSwitchPreference: order and on/off of each popup source. */
@Composable
private fun OrderDialog(key: String, default: String, title: String, prefs: SharedPreferences, onDismiss: () -> Unit, onWritten: () -> Unit) {
    val ctx = LocalContext.current
    val items = prefs.getString(key, default)!!.split(Separators.ENTRY).map {
        val both = it.split(Separators.KV)
        OrderItem(both.first(), both.last().toBoolean())
    }
    ReorderDialog(
        onConfirmed = { reordered ->
            prefs.edit { putString(key, reordered.joinToString(Separators.ENTRY) { it.name + Separators.KV + it.state }) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
            onWritten()
        },
        onDismissRequest = onDismiss,
        onNeutral = { prefs.edit { remove(key) }; onWritten() },
        neutralButtonText = if (prefs.contains(key)) stringResource(R.string.button_default) else null,
        items = items,
        title = { Text(title) },
        displayItem = { item ->
            var checked by rememberSaveable { mutableStateOf(item.state) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyboardIconsSet.instance.GetIconOrEmpty(item.name)
                Text(orderEntryName(item.name, ctx), Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = { item.state = it; checked = it })
            }
        },
        getKey = { it.name },
    )
}

/**
 * A language card: the look of a choice card of the kit (gradient, spaced capital label, hint,
 * glowing pill; the active language at full opacity with the white outline, the others dimmed
 * with readable text, [choiceSurface]), but a button that opens the page of this language, not a choice.
 */
@Composable
private fun LanguageCard(card: LanguageCardInfo, index: Int, onClick: () -> Unit) {
    val (from, to) = languageGradients[index % languageGradients.size]
    val activeText = stringResource(R.string.agent_keys_hint_active)
    Box(
        Modifier.fillMaxWidth()
            .choiceSurface(card.active, from, to, AgentSettingsTokens.Text, glow = index % languageGradients.size)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.agent_settings_open, card.label), onClick = onClick)
            .then(if (card.active) Modifier.semantics { stateDescription = activeText } else Modifier)
            .heightIn(min = 92.dp).testTag(LANGUAGE_CARD_TAG + index),
    ) {
        val text = choiceText(from, to)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.label.uppercase(Locale.getDefault()), style = AgentSettingsType.ChoiceLabel, color = text)
            Text(card.hint, style = AgentSettingsType.Small.copy(fontWeight = FontWeight.Medium), color = text)
        }
    }
}

const val LANGUAGE_CARD_TAG = "agent_keys_language_"
const val KEYS_ROW_TAG = "agent_keys_row_"
const val KEYS_RETURN_TAG = "agent_keys_return"
