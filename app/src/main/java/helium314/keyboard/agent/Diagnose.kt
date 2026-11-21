// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.os.Looper
import android.text.format.Formatter
import android.view.accessibility.AccessibilityManager
import dev.agentkeyboard.core.EmojiMixTable
import dev.agentkeyboard.core.FieldReport
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbEngines
import dev.agentkeyboard.core.RgbLook
import dev.agentkeyboard.core.RgbPatternLibrary
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTarget
import helium314.keyboard.agent.commands.EmojiMixClient
import helium314.keyboard.compat.locale as configurationLocale
import helium314.keyboard.agent.commands.EmojiMixer
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.settings.screens.TypingAutocorrect
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.getUserAndInternalDictionaries
import java.util.Locale

/**
 * Stage 30: what the page Einstellungen → Diagnose shows, so the owner can send the lead what the
 * phone really does. Every value is a state, a count or a version: never text from a field, the
 * clipboard, a key, learned data or an identifier of the phone (no IMEI, serial or account). Nothing
 * here is stored or logged. The only network use is [checkGstatic], and only on a tap.
 */
object Diagnose {
    class Row(val label: String, val value: String)
    enum class Part { TRACKPAD, LAST_FIELD, CORRECTION, HAPTICS, SYMBOLS, LANGUAGE, EMOJI_MIX, LIGHTING, TYPING, AUTOFILL, DEVICE }
    /**
     * [appPackage]/[appForced] (stage 61, only the card "Letztes Feld", only when the app is remembered): the
     * package of the app and whether it is on the list of forced apps, for the button of the card.
     */
    class Section(val part: Part, val title: String, val rows: List<Row>, val appPackage: String? = null, val appForced: Boolean = false)

    // --- haptics (HapticChain) ---

    /** `VibrationEffect.Composition.PRIMITIVE_*` (API 30, low tick API 31) by their names. */
    private val PRIMITIVES = listOf(1 to "click", 2 to "thud", 3 to "spin", 4 to "quick_rise", 5 to "slow_rise",
        6 to "quick_fall", HapticChain.PRIMITIVE_TICK to "tick", 8 to "low_tick")

    enum class Tier { PRIMITIVES, WAVEFORM, PULSES }
    /**
     * What the space-bar detents play (stage 33): the [plan] of the last detent ("stretch") for the
     * test button; [tier]/[primitive] of a light detent tick, [stretchTier]/[stretchPrimitive] of the
     * last detent (it needs other primitives, so its tier can be lower).
     */
    class Chain(val plan: HapticChain.Plan, val tier: Tier, val primitive: Int?, val stretchTier: Tier, val stretchPrimitive: Int?)

    /**
     * The only use of HapticChain's plan API on this page (adapt here when the chain changes): what
     * the chain plays on [device] (the phone's vibrator), null without a vibrator.
     */
    fun chain(device: HapticChain.Device?): Chain? {
        if (device == null || !device.hasVibrator) return null
        fun tierOf(plan: HapticChain.Plan) = when (plan) {
            is HapticChain.Plan.Primitives -> Tier.PRIMITIVES to plan.steps.last().primitive
            is HapticChain.Plan.Waveform -> Tier.WAVEFORM to null
            is HapticChain.Plan.Pulses -> Tier.PULSES to null
        }
        val (tier, primitive) = tierOf(HapticChain.plan(HapticChain.Feel.DETENT, device))
        val stretch = HapticChain.plan(HapticChain.Feel.STRETCH, device)
        val (stretchTier, stretchPrimitive) = tierOf(stretch)
        return Chain(stretch, tier, primitive, stretchTier, stretchPrimitive)
    }

    private fun tierText(context: Context, tier: Tier?, primitive: Int?) = when (tier) {
        null -> context.getString(R.string.agent_diagnose_tier_none)
        Tier.PRIMITIVES -> context.getString(R.string.agent_diagnose_tier_primitives,
            PRIMITIVES.firstOrNull { it.first == primitive }?.second ?: primitive.toString())
        Tier.WAVEFORM -> context.getString(R.string.agent_diagnose_tier_waveform)
        Tier.PULSES -> context.getString(R.string.agent_diagnose_tier_pulses)
    }

    fun haptics(context: Context, device: HapticChain.Device?): Section {
        val vibrator = device?.hasVibrator == true
        val primitives = when {
            device == null || !vibrator -> context.getString(R.string.agent_diagnose_none)
            device.sdk < Build.VERSION_CODES.R -> context.getString(R.string.agent_diagnose_primitives_old)
            else -> PRIMITIVES.filter { device.supportsPrimitive(it.first) }.joinToString(", ") { it.second }
                .ifEmpty { context.getString(R.string.agent_diagnose_none) }
        }
        val chain = chain(device)
        val tier = tierText(context, chain?.tier, chain?.primitive)
        val stretchTier = tierText(context, chain?.stretchTier, chain?.stretchPrimitive)
        val keyVibration = context.prefs().getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON)
        return Section(Part.HAPTICS, context.getString(R.string.agent_diagnose_haptics), listOf(
            Row(context.getString(R.string.agent_diagnose_vibrator), yesNo(context, vibrator)),
            Row(context.getString(R.string.agent_diagnose_amplitude), yesNo(context, vibrator && device?.hasAmplitudeControl == true)),
            Row(context.getString(R.string.agent_diagnose_primitives), primitives),
            Row(context.getString(R.string.agent_diagnose_tier), tier),
            Row(context.getString(R.string.agent_diagnose_tier_stretch), stretchTier),
            Row(context.getString(R.string.agent_diagnose_key_vibration), onOff(context, keyVibration)),
        ))
    }

    // --- drawn symbols ---

    fun symbols(context: Context): Section {
        val prefs = context.prefs()
        val drawing = prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, false)
        val gestureTyping = JniUtils.sHaveGestureLib && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT)
        val locale = currentLocale(context)
        val mainDictionary = locale?.let { hasMainDictionary(context, it) } == true
        val touchExploration = (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.isTouchExplorationEnabled == true
        // the same conditions as GestureEnabler (LatinIME: gesture typing or drawn symbols by the user)
        val handling = when {
            !drawing && !gestureTyping -> context.getString(R.string.agent_diagnose_gestures_off_user)
            !mainDictionary -> context.getString(R.string.agent_diagnose_gestures_off_dictionary)
            touchExploration -> context.getString(R.string.agent_diagnose_gestures_off_talkback)
            else -> context.getString(R.string.agent_diagnose_gestures_on)
        }
        return Section(Part.SYMBOLS, context.getString(R.string.agent_diagnose_symbols), listOf(
            Row(context.getString(R.string.agent_symbols_title), onOff(context, drawing)),
            Row(context.getString(R.string.agent_diagnose_gesture_library),
                context.getString(if (JniUtils.sHaveGestureLib) R.string.agent_diagnose_present else R.string.agent_diagnose_gesture_library_missing)),
            Row(context.getString(R.string.agent_diagnose_main_dictionary, locale?.toLanguageTag() ?: "?"),
                context.getString(if (mainDictionary) R.string.agent_diagnose_present else R.string.agent_diagnose_missing)),
            Row(context.getString(R.string.agent_diagnose_talkback), onOff(context, touchExploration)),
            Row(context.getString(R.string.agent_diagnose_gestures), handling),
        ))
    }

    private fun currentLocale(context: Context): Locale? =
        runCatching { SubtypeSettings.getSelectedSubtype(context.prefs()).locale() }.getOrNull()

    /** How often [hasMainDictionary] listed the files, and whether the last time was on the main thread; for tests. */
    @Volatile internal var dictionaryLookups = 0
        private set
    @Volatile internal var dictionaryLookupOnMainThread = false
        private set

    /**
     * An internal or user-added main dictionary for [locale], as the dictionary page counts them.
     * Lists files: the page asks once, off the main thread (stage 30 fix).
     */
    private fun hasMainDictionary(context: Context, locale: Locale): Boolean = mainDictionary(context, locale) ?: false

    /** As [hasMainDictionary]; null when the files cannot be listed (review of stage 55, N4: "unknown", never "missing"). */
    internal fun mainDictionary(context: Context, locale: Locale): Boolean? = runCatching {
        dictionaryLookups++
        dictionaryLookupOnMainThread = Looper.myLooper() == Looper.getMainLooper()
        mainDictionaryLookup(context, locale)
    }.getOrNull()

    /** Replaceable in tests (a lookup that fails). */
    internal var mainDictionaryLookup: (Context, Locale) -> Boolean = { context, locale ->
        val (userDicts, internal) = getUserAndInternalDictionaries(context, locale)
        internal || userDicts.any { it.name.startsWith(helium314.keyboard.latin.dictionary.Dictionary.TYPE_MAIN) }
    }

    // --- the last field (stage 55, LastField) ---

    /**
     * The card "Letztes Feld": what the app allowed, what the keyboard switched off, the language and
     * its dictionary, and one verdict. Lists the dictionary files of the field's language and asks
     * the package manager for the app's name: call off the main thread. The values come from
     * [LastField]; nothing from the field's text.
     */
    fun lastField(context: Context, nowElapsedMillis: Long): Section {
        val title = context.getString(R.string.agent_diagnose_field_title)
        val label = context.getString(R.string.agent_diagnose_field)
        val verdict = context.getString(R.string.agent_diagnose_field_verdict)
        return when (val snapshot = LastField.current(context)) {
            null -> Section(Part.LAST_FIELD, title, listOf(Row(label, context.getString(R.string.agent_diagnose_field_none))))
            FieldReport.Snapshot.Password -> Section(Part.LAST_FIELD, title, listOf(
                Row(verdict, verdictText(context, FieldReport.verdict(snapshot))),
                Row(label, context.getString(R.string.agent_diagnose_field_password)),
            ))
            FieldReport.Snapshot.Locked -> Section(Part.LAST_FIELD, title, listOf(
                Row(verdict, verdictText(context, FieldReport.verdict(snapshot))),
                Row(label, context.getString(R.string.agent_diagnose_field_locked)),
            ))
            is FieldReport.Snapshot.Field -> {
                val locale = runCatching { Locale.forLanguageTag(snapshot.languageTag) }.getOrNull()
                val present = locale?.let { mainDictionary(context, it) }
                fieldSection(context, snapshot, LastField.live(snapshot.languageTag, present), nowElapsedMillis)
            }
        }
    }

    /** The rows of a [FieldReport.Snapshot.Field] with what the page found out now ([live]). */
    fun fieldSection(context: Context, field: FieldReport.Snapshot.Field, live: FieldReport.Live, nowElapsedMillis: Long): Section {
        fun s(id: Int) = context.getString(id)
        val seconds = ((nowElapsedMillis - field.atMillis) / 1000).coerceAtLeast(0)
        val flags = field.flags.joinToString(", ") { flagText(context, it) }.ifEmpty { s(R.string.agent_diagnose_none) }
        val dictionary = when {
            live.dictionaryPresent == false -> s(R.string.agent_diagnose_missing)
            live.dictionaryLoaded == true -> s(R.string.agent_diagnose_field_dictionary_loaded)
            live.dictionaryLoaded == false -> s(R.string.agent_diagnose_field_dictionary_not_loaded)
            live.dictionaryPresent == true -> s(R.string.agent_diagnose_present)
            else -> s(R.string.agent_diagnose_field_unknown)
        }
        val effective = FieldReport.effective(field, live)
        fun count(n: Int) = if (n == 0 || live.stripUpdates == 0) s(R.string.agent_diagnose_none)
            else context.getString(R.string.agent_diagnose_field_chips_value, n, live.stripUpdates)
        val stripNow = stripText(context, live.strip)
        val result = context.getString(R.string.agent_diagnose_field_result_value,
            onOff(context, effective.suggestions), onOff(context, effective.autoCorrect), onOff(context, effective.caps))
        val alwaysShow = when {
            !field.alwaysShowSetting -> s(R.string.agent_diagnose_off)
            field.alwaysShowExceptWeb -> s(R.string.agent_diagnose_field_always_show_except_web)
            else -> s(R.string.agent_diagnose_on)
        }
        fun hex(value: Int) = String.format(Locale.ROOT, "0x%08x", value)
        val rows = buildList {
            // review of stage 55 (N1): verdict and result first
            add(Row(s(R.string.agent_diagnose_field_verdict), verdictText(context, FieldReport.verdict(field, live))))
            add(Row(s(R.string.agent_diagnose_field_result),
                if (effective.stripCovered) context.getString(R.string.agent_diagnose_field_result_covered, result, stripNow) else result))
            // stage 61: a field without learning keeps neither package nor app name
            add(Row(s(R.string.agent_diagnose_field_app), when {
                field.learningOff -> s(R.string.agent_diagnose_field_app_not_kept)
                else -> field.packageName?.let { appName(context, it) } ?: s(R.string.agent_diagnose_field_unknown)
            }))
            add(Row(s(R.string.agent_diagnose_field_forced), when {
                field.forced -> s(R.string.agent_diagnose_field_forced_yes)
                field.appForced -> context.getString(R.string.agent_diagnose_field_forced_listed, excludedText(context, field))
                else -> s(R.string.agent_diagnose_field_forced_no)
            }))
            add(Row(s(R.string.agent_diagnose_field_when), ago(context, seconds)))
            add(Row(s(R.string.agent_diagnose_field_type), typeText(context, field.type)))
            add(Row(s(R.string.agent_diagnose_field_flags), flags))
            // review of stage 55 (H1): what the app sent and what the engine uses, when HeliBoard changed it
            if (field.workaround) {
                add(Row(s(R.string.agent_diagnose_field_input_type_app), hex(field.rawInputType)))
                add(Row(s(R.string.agent_diagnose_field_input_type_keyboard), hex(field.inputType)))
                add(Row(s(R.string.agent_diagnose_field_workaround), s(R.string.agent_diagnose_field_workaround_firefox)))
            } else add(Row("inputType", hex(field.inputType)))
            add(Row("imeOptions", hex(field.imeOptions)))
            add(Row(s(R.string.agent_diagnose_field_app_suggestions), yesNo(context, field.appAllowsSuggestions)))
            add(Row(s(R.string.agent_diagnose_field_app_caps), capsText(context, field.caps)))
            add(Row(s(R.string.agent_diagnose_field_app_autocorrect), yesNo(context, field.appAutoCorrect)))
            add(Row(s(R.string.agent_diagnose_field_app_completions), yesNo(context, field.appCompletions)))
            add(Row(s(R.string.agent_diagnose_field_language), languageName(context, field.languageTag)))
            add(Row(s(R.string.agent_diagnose_field_dictionary), dictionary))
            add(Row(s(R.string.agent_diagnose_field_suggestions_setting), onOff(context, field.suggestionsSetting)))
            add(Row(s(R.string.agent_diagnose_field_always_show), alwaysShow))
            add(Row(s(R.string.agent_diagnose_field_strip),
                s(if (field.stripHidden) R.string.agent_diagnose_field_strip_hidden else R.string.agent_diagnose_field_strip_shown)))
            add(Row(s(R.string.agent_diagnose_field_autocorrect_setting), onOff(context, field.autoCorrectSetting)))
            add(Row(s(R.string.agent_diagnose_field_more_autocorrect), onOff(context, field.moreAutoCorrect)))
            add(Row(s(R.string.agent_diagnose_field_url_detection), onOff(context, field.urlDetection)))
            add(Row(s(R.string.agent_diagnose_field_autocap_setting), onOff(context, field.autoCapSetting)))
            // review N2: incognito, locked and sensitive fields never leave a Field snapshot, so no rows for them
            add(Row(s(R.string.agent_diagnose_field_learning_paused), yesNo(context, field.learningPaused)))
            add(Row(s(R.string.agent_diagnose_field_strip_last), stripNow))
            add(Row(s(R.string.agent_diagnose_field_covered), count(live.coveredUpdates)))
            add(Row(s(R.string.agent_diagnose_field_chips), count(live.chipUpdates)))
            add(Row(s(R.string.agent_diagnose_field_in_command), yesNo(context, live.inCommand)))
            add(Row(s(R.string.agent_diagnose_field_refused), if (live.composingRefused == 0) s(R.string.agent_diagnose_none)
                else context.getString(R.string.agent_diagnose_field_refused_value, live.composingRefused)))
            add(Row(s(R.string.agent_diagnose_field_not_captured), s(R.string.agent_diagnose_field_not_captured_value)))
        }
        return Section(Part.LAST_FIELD, s(R.string.agent_diagnose_field_title), rows,
            appPackage = field.packageName?.takeIf { !field.learningOff }, appForced = field.appForced)
    }

    /** Stage 61: in plain words why forcing does not apply to a field of a listed app. */
    private fun excludedText(context: Context, field: FieldReport.Snapshot.Field): String = context.getString(
        when (dev.agentkeyboard.core.ForcedCorrection.excludedReason(field.inputType, field.imeOptions)) {
            dev.agentkeyboard.core.ForcedCorrection.Excluded.PASSWORD -> R.string.agent_diagnose_excluded_password
            dev.agentkeyboard.core.ForcedCorrection.Excluded.ADDRESS -> R.string.agent_diagnose_excluded_address
            dev.agentkeyboard.core.ForcedCorrection.Excluded.NAME -> R.string.agent_diagnose_excluded_name
            dev.agentkeyboard.core.ForcedCorrection.Excluded.FILTER -> R.string.agent_diagnose_excluded_filter
            dev.agentkeyboard.core.ForcedCorrection.Excluded.SEARCH -> R.string.agent_diagnose_excluded_search
            dev.agentkeyboard.core.ForcedCorrection.Excluded.GO_NEXT -> R.string.agent_diagnose_excluded_go_next
            dev.agentkeyboard.core.ForcedCorrection.Excluded.ASCII -> R.string.agent_diagnose_excluded_ascii
            dev.agentkeyboard.core.ForcedCorrection.Excluded.NOT_TEXT, null -> R.string.agent_diagnose_excluded_not_text
        })

    private fun stripText(context: Context, strip: FieldReport.Strip?) = context.getString(when (strip) {
        null -> R.string.agent_diagnose_field_not_captured_short
        FieldReport.Strip.WORDS -> R.string.agent_diagnose_strip_words
        FieldReport.Strip.EMPTY -> R.string.agent_diagnose_strip_empty
        FieldReport.Strip.TOOLBAR -> R.string.agent_diagnose_strip_toolbar
        FieldReport.Strip.EMOJIS -> R.string.agent_diagnose_strip_emojis
        FieldReport.Strip.KEYBOARD_CHIPS -> R.string.agent_diagnose_strip_chips
        FieldReport.Strip.PILL -> R.string.agent_diagnose_strip_pill
    })

    /** "WhatsApp (com.whatsapp)" when the package manager knows the app, else the package name. */
    private fun appName(context: Context, packageName: String): String = runCatching {
        val manager = context.packageManager
        val name = manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        if (name.isBlank() || name == packageName) packageName else "$name ($packageName)"
    }.getOrDefault(packageName)

    /** "Spanisch (es)" in the language of the page. */
    private fun languageName(context: Context, tag: String): String {
        val name = displayLanguage(context, tag)
        return if (name == tag) tag else "$name ($tag)"
    }

    private fun displayLanguage(context: Context, tag: String): String = runCatching {
        Locale.forLanguageTag(tag).getDisplayLanguage(context.resources.configuration.configurationLocale())
    }.getOrNull().orEmpty().ifEmpty { tag }

    /** The verdict as one sentence. */
    fun verdictText(context: Context, verdict: FieldReport.Verdict): String {
        val parts = buildList {
            add(findingText(context, verdict))
            // stage 61: what the forcing of this app switched on, and why nothing is learned here
            for (forced in verdict.forced) add(context.getString(when (forced) {
                FieldReport.Forced.SUGGESTIONS -> R.string.agent_diagnose_verdict_forced_suggestions
                FieldReport.Forced.AUTOCORRECT -> R.string.agent_diagnose_verdict_forced_autocorrect
                FieldReport.Forced.CAPS -> R.string.agent_diagnose_verdict_forced_caps
            }))
            if (verdict.composingRefused) add(context.getString(R.string.agent_diagnose_verdict_refused))
            when (verdict.learning) {
                FieldReport.Learning.FIELD_ASKS_NONE -> add(context.getString(R.string.agent_diagnose_verdict_learning_field))
                FieldReport.Learning.INCOGNITO -> add(context.getString(R.string.agent_diagnose_verdict_learning_incognito))
                FieldReport.Learning.ON -> {}
            }
        }
        return parts.joinToString(" ")
    }

    private fun findingText(context: Context, verdict: FieldReport.Verdict): String {
        val language = verdict.languageTag?.let { displayLanguage(context, it) }.orEmpty()
        return when (verdict.finding) {
            FieldReport.Finding.PASSWORD -> context.getString(R.string.agent_diagnose_verdict_password)
            FieldReport.Finding.LOCKED -> context.getString(R.string.agent_diagnose_verdict_locked)
            FieldReport.Finding.NO_TEXT_FIELD -> context.getString(R.string.agent_diagnose_verdict_no_text_field)
            FieldReport.Finding.NOT_TEXT -> context.getString(R.string.agent_diagnose_verdict_not_text)
            FieldReport.Finding.STRIP_HIDDEN -> context.getString(R.string.agent_diagnose_verdict_strip_hidden)
            FieldReport.Finding.SUGGESTIONS_SETTING_OFF -> context.getString(R.string.agent_diagnose_verdict_suggestions_setting)
            FieldReport.Finding.WORKAROUND_NO_SUGGESTIONS -> context.getString(R.string.agent_diagnose_verdict_workaround_no_suggestions)
            FieldReport.Finding.APP_NO_SUGGESTIONS -> context.getString(R.string.agent_diagnose_verdict_app_no_suggestions)
            FieldReport.Finding.APP_NO_SUGGESTIONS_WEB -> context.getString(R.string.agent_diagnose_verdict_app_no_suggestions_web)
            FieldReport.Finding.NO_DICTIONARY -> context.getString(R.string.agent_diagnose_verdict_no_dictionary, language)
            FieldReport.Finding.DICTIONARY_NOT_LOADED -> context.getString(R.string.agent_diagnose_verdict_dictionary_not_loaded, language)
            FieldReport.Finding.AUTOCORRECT_SETTING_OFF -> context.getString(R.string.agent_diagnose_verdict_autocorrect_setting)
            FieldReport.Finding.AUTOCORRECT_ADDRESS -> context.getString(R.string.agent_diagnose_verdict_autocorrect_address)
            FieldReport.Finding.AUTOCORRECT_APP -> context.getString(R.string.agent_diagnose_verdict_autocorrect_app)
            FieldReport.Finding.AUTOCORRECT_OFF -> context.getString(R.string.agent_diagnose_verdict_autocorrect_off)
            FieldReport.Finding.CAPS_SETTING_OFF -> context.getString(R.string.agent_diagnose_verdict_caps_setting)
            FieldReport.Finding.CAPS_SCRIPT -> context.getString(R.string.agent_diagnose_verdict_caps_script, language)
            FieldReport.Finding.CAPS_APP -> context.getString(R.string.agent_diagnose_verdict_caps_app)
            FieldReport.Finding.IN_COMMAND -> context.getString(R.string.agent_diagnose_verdict_in_command)
            FieldReport.Finding.CHIPS -> context.getString(R.string.agent_diagnose_verdict_chips)
            FieldReport.Finding.PILL -> context.getString(R.string.agent_diagnose_verdict_pill)
            FieldReport.Finding.WORDS_UNDER_TOOLBAR -> context.getString(R.string.agent_diagnose_verdict_words_under_toolbar)
            FieldReport.Finding.WORDS_UNDER_EMOJIS -> context.getString(R.string.agent_diagnose_verdict_words_under_emojis)
            FieldReport.Finding.STRIP_TOOLBAR -> context.getString(R.string.agent_diagnose_verdict_strip_toolbar)
            FieldReport.Finding.STRIP_EMOJIS -> context.getString(R.string.agent_diagnose_verdict_strip_emojis)
            FieldReport.Finding.STRIP_NOT_CAPTURED -> context.getString(R.string.agent_diagnose_verdict_strip_not_captured)
            FieldReport.Finding.ALL_ON -> context.getString(R.string.agent_diagnose_verdict_all_on)
        }
    }

    private fun typeText(context: Context, type: FieldReport.FieldType) = context.getString(when (type) {
        FieldReport.FieldType.NONE -> R.string.agent_diagnose_type_none
        FieldReport.FieldType.TEXT -> R.string.agent_diagnose_type_text
        FieldReport.FieldType.SEARCH -> R.string.agent_diagnose_type_search
        FieldReport.FieldType.URL -> R.string.agent_diagnose_type_url
        FieldReport.FieldType.EMAIL -> R.string.agent_diagnose_type_email
        FieldReport.FieldType.PASSWORD -> R.string.agent_diagnose_field_password
        FieldReport.FieldType.PERSON_NAME -> R.string.agent_diagnose_type_person_name
        FieldReport.FieldType.POSTAL_ADDRESS -> R.string.agent_diagnose_type_postal_address
        FieldReport.FieldType.SHORT_MESSAGE -> R.string.agent_diagnose_type_short_message
        FieldReport.FieldType.LONG_MESSAGE -> R.string.agent_diagnose_type_long_message
        FieldReport.FieldType.EMAIL_SUBJECT -> R.string.agent_diagnose_type_email_subject
        FieldReport.FieldType.WEB_TEXT -> R.string.agent_diagnose_type_web_text
        FieldReport.FieldType.FILTER -> R.string.agent_diagnose_type_filter
        FieldReport.FieldType.PHONETIC -> R.string.agent_diagnose_type_phonetic
        FieldReport.FieldType.NUMBER -> R.string.agent_diagnose_type_number
        FieldReport.FieldType.PHONE -> R.string.agent_diagnose_type_phone
        FieldReport.FieldType.DATETIME -> R.string.agent_diagnose_type_datetime
    })

    private fun flagText(context: Context, flag: FieldReport.Flag) = context.getString(when (flag) {
        FieldReport.Flag.MULTI_LINE -> R.string.agent_diagnose_flag_multi_line
        FieldReport.Flag.IME_MULTI_LINE -> R.string.agent_diagnose_flag_ime_multi_line
        FieldReport.Flag.NO_SUGGESTIONS -> R.string.agent_diagnose_flag_no_suggestions
        FieldReport.Flag.AUTO_CORRECT -> R.string.agent_diagnose_flag_auto_correct
        FieldReport.Flag.AUTO_COMPLETE -> R.string.agent_diagnose_flag_auto_complete
        FieldReport.Flag.SIGNED -> R.string.agent_diagnose_flag_signed
        FieldReport.Flag.DECIMAL -> R.string.agent_diagnose_flag_decimal
        FieldReport.Flag.FORCE_ASCII -> R.string.agent_diagnose_flag_force_ascii
    })

    private fun capsText(context: Context, caps: FieldReport.Caps) = context.getString(when (caps) {
        FieldReport.Caps.NONE -> R.string.agent_diagnose_none
        FieldReport.Caps.SENTENCES -> R.string.agent_diagnose_caps_sentences
        FieldReport.Caps.WORDS -> R.string.agent_diagnose_caps_words
        FieldReport.Caps.CHARACTERS -> R.string.agent_diagnose_caps_characters
    })

    // --- language switches (SubtypeSwitchLog) ---

    fun languages(context: Context, entries: List<SubtypeSwitchLog.Entry>, nowElapsedMillis: Long): Section {
        val prefs = context.prefs()
        val rows = mutableListOf(
            Row(context.getString(R.string.agent_diagnose_language_now), currentLocale(context)?.toLanguageTag() ?: "?"),
            Row(context.getString(R.string.agent_diagnose_per_app),
                onOff(context, prefs.getBoolean(Settings.PREF_SAVE_SUBTYPE_PER_APP, Defaults.PREF_SAVE_SUBTYPE_PER_APP))),
        )
        if (entries.isEmpty()) rows += Row(context.getString(R.string.agent_diagnose_switch_last), context.getString(R.string.agent_diagnose_switch_none))
        entries.forEachIndexed { index, entry ->
            val label = if (index == 0) context.getString(R.string.agent_diagnose_switch_last)
                else context.getString(R.string.agent_diagnose_switch_before, index + 1)
            val seconds = ((nowElapsedMillis - entry.atElapsedMillis) / 1000).coerceAtLeast(0)
            rows += Row(label, context.getString(R.string.agent_diagnose_switch_value, reason(context, entry.reason), entry.languageTag, ago(context, seconds)))
        }
        return Section(Part.LANGUAGE, context.getString(R.string.agent_diagnose_language), rows)
    }

    fun reason(context: Context, reason: SubtypeSwitchLog.Reason): String = context.getString(when (reason) {
        SubtypeSwitchLog.Reason.APP_HINT -> R.string.agent_diagnose_reason_hint
        SubtypeSwitchLog.Reason.HINT_ENDED -> R.string.agent_diagnose_reason_hint_ended
        SubtypeSwitchLog.Reason.PER_APP -> R.string.agent_diagnose_reason_per_app
        SubtypeSwitchLog.Reason.COMMAND -> R.string.agent_diagnose_reason_command
        SubtypeSwitchLog.Reason.KEYBOARD -> R.string.agent_diagnose_reason_keyboard
        SubtypeSwitchLog.Reason.SYSTEM -> R.string.agent_diagnose_reason_system
        SubtypeSwitchLog.Reason.KEPT_BY_CHOICE -> R.string.agent_diagnose_reason_kept
    })

    private fun ago(context: Context, seconds: Long) =
        if (seconds < 120) context.getString(R.string.agent_diagnose_seconds_ago, seconds)
        else context.getString(R.string.agent_diagnose_minutes_ago, seconds / 60)

    // --- Emoji-Mix ---

    /** The result of the reachability check; [Running] while it runs. */
    sealed interface Reach {
        object Running : Reach
        class Reachable(val bytes: Int) : Reach
        /** The server answered, but has no image for the test pair (404). */
        object NoImage : Reach
        class Failed(val reason: EmojiMixClient.Reason) : Reach
        /** Without the pair table there is no allowed URL to ask for. */
        object NoTable : Reach
    }

    /** The pair whose image the check loads (in the table, see EmojiMixTest). */
    const val TEST_LEFT = "🐶"
    const val TEST_RIGHT = "🐱"

    /** The pair table from the asset (off the main thread), or null when it cannot be read. */
    fun readTable(context: Context): EmojiMixTable? =
        runCatching { EmojiMixer.tableSource(context).use { EmojiMixTable.read(it) } }.getOrNull()

    /** Number and bytes of the stored images (off the main thread). */
    fun cacheStats(context: Context): Pair<Int, Long> {
        val cache = EmojiMixer.cache(context)
        val files = cache.directory.listFiles()?.filter { it.isFile }.orEmpty()
        return files.size to files.sumOf { it.length() }
    }

    /**
     * After a tap only: one GET of the test pair's image from www.gstatic.com, with the Emoji-Mix
     * client and its checks (table URL only, no cookies, no redirects, PNG). The image is not kept.
     * Blocking: call off the main thread.
     */
    fun checkGstatic(context: Context, table: EmojiMixTable?): Reach {
        val mix = table?.find(TEST_LEFT, TEST_RIGHT) ?: return Reach.NoTable
        return try {
            when (val result = EmojiMixClient(EmojiMixer.calls(context)).fetch(mix.url)) {
                is EmojiMixClient.Result.Image -> Reach.Reachable(result.bytes.size)
                EmojiMixClient.Result.Missing -> Reach.NoImage
                is EmojiMixClient.Result.Failed -> Reach.Failed(result.reason)
            }
        } catch (_: Exception) {
            Reach.Failed(EmojiMixClient.Reason.INTERNAL)
        }
    }

    /** [table] null and [tableRead] false: still reading. [cache]: count and bytes, null while counting. */
    fun emojiMix(context: Context, table: EmojiMixTable?, tableRead: Boolean, cache: Pair<Int, Long>?, reach: Reach?): Section {
        val tableText = when {
            table != null -> context.getString(R.string.agent_diagnose_table_value, table.pairCount, table.emojiCount)
            tableRead -> context.getString(R.string.agent_diagnose_table_unreadable)
            else -> context.getString(R.string.agent_diagnose_reading)
        }
        val cacheText = cache?.let { (count, bytes) ->
            context.getString(R.string.agent_diagnose_cache_value, count, Formatter.formatShortFileSize(context, bytes))
        } ?: context.getString(R.string.agent_diagnose_reading)
        val reachText = when (reach) {
            null -> context.getString(R.string.agent_diagnose_gstatic_unchecked)
            Reach.Running -> context.getString(R.string.agent_diagnose_gstatic_running)
            is Reach.Reachable -> context.getString(R.string.agent_diagnose_gstatic_ok, Formatter.formatShortFileSize(context, reach.bytes.toLong()))
            Reach.NoImage -> context.getString(R.string.agent_diagnose_gstatic_no_image)
            Reach.NoTable -> context.getString(R.string.agent_diagnose_table_unreadable)
            is Reach.Failed -> context.getString(R.string.agent_diagnose_gstatic_failed, failure(context, reach.reason))
        }
        return Section(Part.EMOJI_MIX, context.getString(R.string.agent_diagnose_emoji_mix), listOf(
            Row(context.getString(R.string.agent_diagnose_table), tableText),
            Row(context.getString(R.string.agent_diagnose_cache), cacheText),
            Row(context.getString(R.string.agent_diagnose_gstatic), reachText),
        ))
    }

    private fun failure(context: Context, reason: EmojiMixClient.Reason) = context.getString(when (reason) {
        EmojiMixClient.Reason.NETWORK -> R.string.agent_diagnose_failure_network
        EmojiMixClient.Reason.TIMEOUT -> R.string.agent_diagnose_failure_timeout
        EmojiMixClient.Reason.REDIRECT -> R.string.agent_diagnose_failure_redirect
        EmojiMixClient.Reason.SERVICE -> R.string.agent_diagnose_failure_service
        EmojiMixClient.Reason.NOT_PNG, EmojiMixClient.Reason.TOO_LARGE -> R.string.agent_diagnose_failure_answer
        EmojiMixClient.Reason.NOT_ALLOWED, EmojiMixClient.Reason.STORAGE, EmojiMixClient.Reason.INTERNAL -> R.string.agent_diagnose_failure_internal
    })

    // --- RGB lighting cost (build 14 feedback: the keyboard lagged) ---

    /** What drawing the light cost in the last seconds the keyboard was lit ([RgbLighting.lastFrameWork]). */
    fun lighting(context: Context, work: dev.agentkeyboard.core.RgbFrameStats.Summary, patterns: List<Row> = emptyList(),
        failure: String? = null): Section {
        fun ms(avg: Double, max: Double) = context.getString(R.string.agent_diagnose_ms, avg, max)
        val rows = if (work.frames == 0 && work.jobs == 0) listOf(Row(context.getString(R.string.agent_diagnose_light_frames),
            context.getString(R.string.agent_diagnose_light_none)))
        else listOf(
            Row(context.getString(R.string.agent_diagnose_light_frames), work.frames.toString()),
            Row(context.getString(R.string.agent_diagnose_light_ui), ms(work.uiAvgMs, work.uiMaxMs)),
            Row(context.getString(R.string.agent_diagnose_light_draw), ms(work.drawAvgMs, work.drawMaxMs)),
            Row(context.getString(R.string.agent_diagnose_light_background), "${work.jobs} · " + ms(work.backgroundAvgMs, work.backgroundMaxMs)),
        )
        // stage 70 (S13): the painter threw and the light is off for this session; only the exception class is shown
        val failed = if (failure == null) emptyList() else listOf(Row(context.getString(R.string.agent_diagnose_light_failed), failure))
        return Section(Part.LIGHTING, context.getString(R.string.agent_diagnose_lighting), failed + rows + patterns)
    }

    /**
     * The patterns the lit elements use and what one light grid of each costs on this phone,
     * measured now (48 x 32 values, the best of three). Own patterns (stage 34) appear as "eigenes
     * Muster n" (their place in [library]), never by the name the user gave them: the page can be
     * copied into a report. Measure off the main thread; reads only the setting, no text.
     */
    fun lightPatterns(context: Context, settings: RgbSettings, library: RgbPatternLibrary = RgbPatternLibrary.EMPTY): List<Row> {
        if (!settings.enabled) return emptyList()
        val lit = RgbTarget.entries.filter { settings.lights(it) }
        if (lit.isEmpty()) return emptyList()
        fun name(look: RgbLook) = if (look.usesOwn)
            context.getString(R.string.agent_diagnose_light_own_n, library.patterns.indexOfFirst { it.id == look.own!!.id } + 1)
            else context.getString(helium314.keyboard.settings.screens.patternLabelRes(look.pattern))
        val engines = RgbEngines(settings)
        val rows = mutableListOf(Row(context.getString(R.string.agent_diagnose_light_patterns),
            lit.map { name(settings.lookFor(it)) }.distinct().joinToString(", ")))
        val measured = HashSet<RgbEngine>()
        for (target in lit) {
            val engine = engines.forTarget(target)
            if (!measured.add(engine)) continue
            val ms = gridMs(engine)
            rows += Row(context.getString(R.string.agent_diagnose_light_grid, name(settings.lookFor(target))),
                String.format(Locale.getDefault(), "%.2f ms", ms))
        }
        return rows
    }

    /** Time of one 48 x 32 light grid of [engine] in ms, the best of three after a warm-up. */
    fun gridMs(engine: RgbEngine): Double {
        engine.aspect = 0.45f
        engine.advance(0)
        engine.advance(40)
        var sink = 0
        var best = Double.MAX_VALUE
        repeat(4) { round ->
            val start = System.nanoTime()
            for (r in 0 until 32) for (c in 0 until 48) sink += engine.light((c + 0.5f) / 48, (r + 0.5f) / 32, -1, 40)
            if (round > 0) best = minOf(best, (System.nanoTime() - start) / 1e6)
        }
        if (sink == Int.MIN_VALUE) best = 0.0
        return best
    }

    // --- typing cost (stage 64a) ---

    private fun hookName(context: Context, hook: TypingPerf.Hook) = context.getString(when (hook) {
        TypingPerf.Hook.COMMAND -> R.string.agent_diagnose_typing_hook_command
        TypingPerf.Hook.STRIP_CHIPS -> R.string.agent_diagnose_typing_hook_strip
        TypingPerf.Hook.FORMAT_CHIPS -> R.string.agent_diagnose_typing_hook_format
        TypingPerf.Hook.FIELD_CHIPS -> R.string.agent_diagnose_typing_hook_field
        TypingPerf.Hook.MEMORY_CHIPS -> R.string.agent_diagnose_typing_hook_memory
        TypingPerf.Hook.UPDATE_CHIP -> R.string.agent_diagnose_typing_hook_update
        TypingPerf.Hook.FIELD_START -> R.string.agent_diagnose_typing_hook_start
        TypingPerf.Hook.WORD_COMMIT -> R.string.agent_diagnose_typing_hook_word
        TypingPerf.Hook.LEARNING -> R.string.agent_diagnose_typing_hook_learning
    })

    /**
     * The card "Tippen": what the fork's own code cost per key press since the process started or the last
     * reset ([TypingPerf]). Numbers and fixed hook names only; [nowMillis] says how long ago the count began.
     * Failures of single commands ([TypingPerf.failures]) are listed by command name and exception class.
     */
    fun typing(context: Context, snapshot: TypingPerf.Snapshot, nowMillis: Long,
               failures: List<TypingPerf.Failure> = TypingPerf.failures()): Section {
        fun ms(nanos: Long) = String.format(Locale.getDefault(), "%.1f ms", nanos / 1e6)
        fun avg(average: Double, max: Int) = context.getString(R.string.agent_diagnose_typing_requests_value,
            String.format(Locale.getDefault(), "%.1f", average), max)
        val rows = mutableListOf(Row(context.getString(R.string.agent_diagnose_typing_good), context.getString(R.string.agent_diagnose_typing_good_value)))
        if (snapshot.presses == 0) {
            rows += Row(context.getString(R.string.agent_diagnose_typing_presses), context.getString(R.string.agent_diagnose_typing_none))
        } else {
            val minutes = ((nowMillis - snapshot.sinceMillis).coerceAtLeast(0) / 60_000).toInt()
            rows += Row(context.getString(R.string.agent_diagnose_typing_presses),
                context.getString(R.string.agent_diagnose_typing_presses_value, snapshot.presses, minutes))
            rows += Row(context.getString(R.string.agent_diagnose_typing_median), ms(snapshot.medianNanos))
            rows += Row(context.getString(R.string.agent_diagnose_typing_p95), ms(snapshot.p95Nanos))
            rows += Row(context.getString(R.string.agent_diagnose_typing_max), ms(snapshot.maxNanos))
            rows += Row(context.getString(R.string.agent_diagnose_typing_app), avg(snapshot.appAverage, snapshot.appMax))
            rows += Row(context.getString(R.string.agent_diagnose_typing_system), avg(snapshot.systemAverage, snapshot.systemMax))
            snapshot.slowest.forEachIndexed { index, run ->
                rows += Row(context.getString(R.string.agent_diagnose_typing_slow, index + 1, hookName(context, run.hook)),
                    context.getString(R.string.agent_diagnose_typing_slow_value, ms(run.nanos), run.requests))
            }
        }
        // a command that threw was skipped (stage 64c): its name and the class of the error, never the message
        for (failure in failures) rows += Row(context.getString(R.string.agent_diagnose_typing_failure, failure.name),
            context.getString(R.string.agent_diagnose_typing_failure_value, failure.count, failure.errorClass))
        return Section(Part.TYPING, context.getString(R.string.agent_diagnose_typing), rows)
    }

    /**
     * Stage 79b: the card "Autofill-Vorschläge": what the keyboard did with the inline suggestions of a password
     * manager since it started, as numbers only (never a title or a text), to read after a miss.
     */
    fun autofill(context: Context, snapshot: InlineAutofillCounters.Snapshot): Section {
        fun s(id: Int) = context.getString(id)
        val rows = mutableListOf<Row>()
        val last = snapshot.last
        if (snapshot.accepted + snapshot.empty + snapshot.refused == 0) {
            rows += Row(s(R.string.agent_diagnose_autofill_responses), s(R.string.agent_diagnose_autofill_none))
        } else {
            rows += Row(s(R.string.agent_diagnose_autofill_responses), context.getString(
                R.string.agent_diagnose_autofill_responses_value, snapshot.accepted, snapshot.empty, snapshot.refused))
        }
        if (last != null) {
            rows += Row(s(R.string.agent_diagnose_autofill_requested), last.count.toString())
            rows += Row(s(R.string.agent_diagnose_autofill_arrived), last.withView.toString())
            rows += Row(s(R.string.agent_diagnose_autofill_null), last.nullViews.toString())
            rows += Row(s(R.string.agent_diagnose_autofill_stale), last.staleDrops.toString())
            rows += Row(s(R.string.agent_diagnose_autofill_pending), last.pending.toString())
            rows += Row(s(R.string.agent_diagnose_autofill_placed), last.placed.toString())
            rows += Row(s(R.string.agent_diagnose_autofill_first), if (last.firstCallbackMillis < 0) s(R.string.agent_diagnose_autofill_no_callback)
                else context.getString(R.string.agent_diagnose_autofill_ms, last.firstCallbackMillis))
            rows += Row(s(R.string.agent_diagnose_autofill_shown), yesNo(context, last.shown))
        }
        rows += Row(s(R.string.agent_diagnose_autofill_width), when {
            snapshot.stripWidthPx < 0 -> s(R.string.agent_diagnose_autofill_no_request)
            snapshot.stripWidthPx == 0 -> s(R.string.agent_diagnose_autofill_not_laid_out)
            else -> context.getString(R.string.agent_diagnose_autofill_px, snapshot.stripWidthPx)
        })
        rows += Row(s(R.string.agent_diagnose_autofill_input), s(if (snapshot.generationOpen) R.string.agent_diagnose_autofill_input_open else R.string.agent_diagnose_autofill_input_closed))
        return Section(Part.AUTOFILL, s(R.string.agent_diagnose_autofill), rows)
    }

    /**
     * Stage 87b: the card "Cursor-Trackpad": the configuration in words (from the preferences, with the functions the listener uses)
     * and what the trackpad decided since the keyboard started: numbers, enum names and flags only, never a text or an app name.
     */
    fun trackpad(context: Context, prefs: android.content.SharedPreferences, snapshot: CursorTrackCounters.Snapshot): Section {
        fun s(id: Int) = context.getString(id)
        val space = snapshot.space
        val delete = snapshot.delete
        val rows = mutableListOf<Row>()
        rows += Row(s(R.string.agent_trackdiag_row_switch), onOff(context, CursorTrackVerdict.trackpadOn(prefs)))
        rows += Row(s(R.string.agent_trackdiag_row_horizontal), CursorTrackText.pair(context, prefs, SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_RIGHT))
        rows += Row(s(R.string.agent_trackdiag_row_vertical), CursorTrackText.pair(context, prefs, SpaceGesture.SWIPE_UP, SpaceGesture.SWIPE_DOWN))
        rows += Row(s(R.string.agent_trackdiag_row_space_now), CursorTrackText.spaceNow(context, CursorTrackVerdict.space(prefs)))
        rows += Row(s(R.string.agent_trackdiag_row_delete_swipe), onOff(context, prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE)))
        rows += Row(s(R.string.agent_trackdiag_row_delete_now), CursorTrackText.deleteNow(context, CursorTrackVerdict.delete(prefs)))
        rows += Row(s(R.string.agent_trackdiag_row_space_touches), space.touches.toString())
        rows += Row(s(R.string.agent_trackdiag_row_space_decisions), CursorTrackText.decisions(context, space, true))
        rows += Row(s(R.string.agent_trackdiag_row_space_action), CursorTrackText.lastAction(context, space))
        rows += Row(s(R.string.agent_trackdiag_row_space_collapsed), CursorTrackText.collapsed(context, space))
        rows += Row(s(R.string.agent_trackdiag_row_space_steps), context.getString(R.string.agent_trackdiag_steps,
            space.steps, space.verticalArrows, space.suppressedArrows, space.detents))
        rows += Row(s(R.string.agent_trackdiag_row_space_field), CursorTrackText.field(context, space))
        rows += Row(s(R.string.agent_trackdiag_row_space_last), CursorTrackText.last(context, space, true))
        rows += Row(s(R.string.agent_trackdiag_row_delete_touches), delete.touches.toString())
        rows += Row(s(R.string.agent_trackdiag_row_delete_decisions), CursorTrackText.decisions(context, delete, false))
        rows += Row(s(R.string.agent_trackdiag_row_delete_steps), context.getString(R.string.agent_trackdiag_steps_delete, delete.steps, delete.detents))
        rows += Row(s(R.string.agent_trackdiag_row_delete_lifts), if (delete.lifts == 0) s(R.string.agent_trackdiag_lifts_none)
            else if (delete.lastProtected) context.getString(R.string.agent_trackdiag_lifts_protected, delete.lifts)
            else context.getString(R.string.agent_trackdiag_lifts, delete.lifts, delete.liftsDeleted, delete.lastSelectedChars))
        rows += Row(s(R.string.agent_trackdiag_row_delete_last), CursorTrackText.last(context, delete, false))
        return Section(Part.TRACKPAD, s(R.string.agent_trackdiag_title), rows)
    }

    // --- correction and language (stage 90a) ---

    /**
     * Stage 90a: sizes only, read off the main thread: learned words per language of the typing history ([learnedWords], null when it
     * cannot be read), learned typo pairs, pairs learned in the field, corrections taken back. Never a word.
     */
    class CorrectionCounts(val learnedWords: Map<String, Int>?, val typoPairs: Int?, val fieldPairs: Int?, val rejectedPairs: Int?)

    fun correctionCounts(context: Context): CorrectionCounts = CorrectionCounts(
        runCatching { LearningData.counts(UserHistoryLearningSource(context)) }.getOrNull(),
        runCatching { TypoMemory.all(context).size }.getOrNull(),
        runCatching { FieldCorrectionMemory.all(context).size }.getOrNull(),
        runCatching { RejectedCorrections.all(context).size }.getOrNull())

    /**
     * The card "Korrektur & Sprache": the settings that decide what the automatic correction does and the sizes of what the keyboard
     * learned. States, language tags, layout names and numbers only: no word, no field text, nothing from the typing history.
     */
    fun correction(context: Context, counts: CorrectionCounts): Section {
        fun s(id: Int) = context.getString(id)
        val prefs = context.prefs()
        fun pref(key: String, default: Boolean) = prefs.getBoolean(key, default)
        val rows = mutableListOf<Row>()
        val level = TypingAutocorrect.current(prefs)
        rows += Row(s(R.string.agent_diagnose_corr_level), when (level) {
            TypingAutocorrect.Level.OFF -> s(R.string.agent_typing_level_off)
            TypingAutocorrect.Level.GENTLE -> s(R.string.agent_typing_level_gentle)
            TypingAutocorrect.Level.BOLD -> s(R.string.agent_typing_level_bold)
            null -> s(R.string.agent_diagnose_corr_level_custom)
        })
        rows += Row(s(R.string.agent_diagnose_corr_strength), String.format(Locale.ROOT, "%.2f",
            prefs.getFloat(Settings.PREF_AUTO_CORRECT_CONFIDENCE, Defaults.PREF_AUTO_CORRECT_CONFIDENCE)))
        rows += Row(s(R.string.agent_diagnose_corr_keep_valid), onOff(context, pref(AgentPreferences.AUTOCORRECT_KEEP_VALID_WORDS, false)))
        rows += Row(s(R.string.agent_diagnose_corr_keep_umlaut), onOff(context, pref(AgentPreferences.AUTOCORRECT_KEEP_UMLAUT_VARIANTS, true)))
        rows += Row(s(R.string.agent_diagnose_corr_swapped), onOff(context, pref(AgentPreferences.AUTOCORRECT_SWAPPED_LETTERS, true)))
        rows += Row(s(R.string.agent_diagnose_corr_sentence), onOff(context, pref(AgentPreferences.AUTOCORRECT_SENTENCE_CONTEXT, true)))
        val selected = runCatching { SubtypeSettings.getSelectedSubtype(prefs) }.getOrNull()
        rows += Row(s(R.string.agent_diagnose_corr_layout), selected?.let { "${it.locale().toLanguageTag()} (${it.mainLayoutNameOrQwerty()})" } ?: s(R.string.agent_diagnose_field_unknown))
        val enabled = runCatching { SubtypeSettings.getEnabledSubtypes(true) }.getOrDefault(emptyList())
        rows += Row(s(R.string.agent_diagnose_corr_active_layouts), if (enabled.isEmpty()) s(R.string.agent_diagnose_field_unknown) else enabled.size.toString())
        if (enabled.isEmpty()) rows += Row(s(R.string.agent_diagnose_corr_second), s(R.string.agent_diagnose_field_unknown))
        enabled.forEach { subtype ->
            val second = getSecondaryLocales(subtype.extraValue).joinToString(", ") { it.toLanguageTag() }
            rows += Row(context.getString(R.string.agent_diagnose_corr_second_of, subtype.locale().toLanguageTag(), subtype.mainLayoutNameOrQwerty()),
                second.ifEmpty { s(R.string.agent_diagnose_none) })
        }
        rows += Row(s(R.string.agent_diagnose_corr_personalized), onOff(context, pref(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS)))
        rows += Row(s(R.string.agent_diagnose_corr_learn_typos), onOff(context, pref(AgentPreferences.AUTOCORRECT_LEARN_TYPOS, false)))
        rows += Row(s(R.string.agent_diagnose_corr_remember_rejected), onOff(context, pref(AgentPreferences.AUTOCORRECT_REMEMBER_REJECTED, false)))
        rows += Row(s(R.string.agent_diagnose_corr_memory), onOff(context, pref(AgentPreferences.MEMORY, false)))
        rows += Row(s(R.string.agent_diagnose_corr_paused), onOff(context, pref(AgentPreferences.LEARNING_PAUSED, false)))
        rows += Row(s(R.string.agent_diagnose_corr_touch), onOff(context, pref(AgentPreferences.ADAPT_TOUCH, false)))
        rows += Row(s(R.string.agent_diagnose_corr_next_word), onOff(context, pref(Settings.PREF_BIGRAM_PREDICTIONS, Defaults.PREF_BIGRAM_PREDICTIONS)))
        val unknown = s(R.string.agent_diagnose_field_unknown)
        rows += Row(s(R.string.agent_diagnose_corr_learned_words), counts.learnedWords?.let { map ->
            if (map.isEmpty()) "0" else map.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}: ${it.value}" }
        } ?: unknown)
        rows += Row(s(R.string.agent_diagnose_corr_typo_pairs), counts.typoPairs?.toString() ?: unknown)
        rows += Row(s(R.string.agent_diagnose_corr_field_pairs), counts.fieldPairs?.toString() ?: unknown)
        rows += Row(s(R.string.agent_diagnose_corr_rejected), counts.rejectedPairs?.toString() ?: unknown)
        return Section(Part.CORRECTION, s(R.string.agent_diagnose_corr_title), rows)
    }

    // --- app and phone ---

    fun device(context: Context): Section = Section(Part.DEVICE, context.getString(R.string.agent_diagnose_device), listOf(
        Row(context.getString(R.string.agent_diagnose_app), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})"),
        // stage 62: the state a screenshot has to prove (debuggable apps run slower, debug mode logs words)
        Row(context.getString(R.string.agent_diagnose_debuggable),
            yesNo(context, context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)),
        Row(context.getString(R.string.agent_diagnose_debug_mode), yesNo(context, helium314.keyboard.latin.define.DebugFlags.DEBUG_ENABLED)),
        Row(context.getString(R.string.agent_diagnose_android), "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        Row(context.getString(R.string.agent_diagnose_model), "${Build.MANUFACTURER} ${Build.MODEL}"),
    ))

    /** Exactly what the page shows, as text for the clipboard. */
    fun text(context: Context, sections: List<Section>): String = buildString {
        append(context.getString(R.string.agent_diagnose_copy_heading))
        for (section in sections) {
            append("\n\n").append(section.title)
            for (row in section.rows) append('\n').append(row.label).append(": ").append(row.value)
        }
    }

    private fun yesNo(context: Context, value: Boolean) =
        context.getString(if (value) R.string.agent_diagnose_yes else R.string.agent_diagnose_no)

    private fun onOff(context: Context, value: Boolean) =
        context.getString(if (value) R.string.agent_diagnose_on else R.string.agent_diagnose_off)
}
