// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.FieldReport
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.compat.isUserLocked
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.prefs
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Stage 55 "Diagnose: Letztes Feld": the last field the keyboard was shown in, as the engine saw it,
 * for the card on Einstellungen → Diagnose ([Diagnose.lastField]).
 *
 * Memory only, never stored or logged, gone with the process (the settings run in the keyboard's
 * process, like [SubtypeSwitchLog]). Holds a [FieldReport.Snapshot]: the package name, `inputType`,
 * `imeOptions`, the language tag and booleans read from the `InputAttributes` and `SettingsValues`
 * the engine uses; never the field's text, hint, label or name. A password field leaves only
 * [FieldReport.Snapshot.Password], incognito or a locked device only [FieldReport.Snapshot.Hidden];
 * either replaces the field before.
 *
 * Fed by one line in `LatinIME.onStartInputViewInternal` ([capture], after the settings were loaded
 * for the field), by [SubtypeSwitchLog.onSwitched] ([onSubtypeSwitched]: the next read takes the
 * settings the engine reloaded for the new language) and by the strip ([onStrip], from
 * [KeyboardCommands.createChipsView]). Called from the engine: nothing in here may throw.
 */
object LastField {
    private var snapshot: FieldReport.Snapshot? = null
    /** The engine's dictionaries, asked when the page opens (they load in the background). */
    private var dictionaries: WeakReference<DictionaryFacilitator>? = null
    /** A language switch since [capture]: [current] takes the settings the engine reloaded for this field. */
    private var switched = false
    private var stripUpdates = 0
    private var chipUpdates = 0
    private var lastStripChips = false
    private var inCommand = false

    /** `LatinIME.onStartInputViewInternal`, once the settings for [editorInfo] are loaded. */
    @JvmStatic @Synchronized fun capture(context: Context, editorInfo: EditorInfo?, settings: SettingsValues?, dictionaries: DictionaryFacilitator?) {
        runCatching {
            val values = settings ?: return
            // a field of the keyboard's own settings (their search on the way to the diagnose page)
            // keeps the snapshot of the app before it (lead, stage 55)
            if (editorInfo?.packageName == context.packageName) return
            snapshot = snapshotOf(context, editorInfo?.imeOptions ?: 0, values, SystemClock.elapsedRealtime())
            this.dictionaries = dictionaries?.let { WeakReference(it) }
            switched = false
            stripUpdates = 0
            chipUpdates = 0
            lastStripChips = false
            inCommand = false
        }
    }

    /** [SubtypeSwitchLog.onSwitched]: the subtype changes; the engine reloads its settings right after. */
    @JvmStatic @Synchronized fun onSubtypeSwitched() {
        switched = true
    }

    /** Every strip update with a strip: whether the keyboard's own chips stood there instead of suggestions. */
    @JvmStatic @Synchronized fun onStrip(chips: Boolean, insideCommand: Boolean) {
        if (snapshot !is FieldReport.Snapshot.Field) return
        if (stripUpdates < Int.MAX_VALUE) stripUpdates++
        if (chips && chipUpdates < Int.MAX_VALUE) chipUpdates++
        lastStripChips = chips
        inCommand = insideCommand
    }

    /** The snapshot, null before the keyboard was shown in a field. */
    @Synchronized fun current(context: Context): FieldReport.Snapshot? {
        val field = snapshot as? FieldReport.Snapshot.Field
        if (field != null && switched) runCatching {
            // the settings LatinIME reloaded after the switch, if they still belong to this field
            val values = Settings.getValues()
            val attributes = values?.mInputAttributes
            if (attributes != null && attributes.mInputType == field.inputType
                && attributes.mTargetApplicationPackageName.orEmpty() == field.packageName.orEmpty()) {
                snapshot = when (val fresh = snapshotOf(context, field.imeOptions, values, field.atMillis)) {
                    is FieldReport.Snapshot.Field -> fresh.copy(atMillis = field.atMillis)
                    else -> fresh
                }
                switched = false
            }
        }
        return snapshot
    }

    /** What the page asks now; [dictionaryPresent] is the file check of [Diagnose] (off the main thread). */
    @Synchronized fun live(languageTag: String, dictionaryPresent: Boolean?): FieldReport.Live = FieldReport.Live(
        dictionaryPresent = dictionaryPresent,
        dictionaryLoaded = dictionaryLoaded(languageTag),
        inCommand = inCommand,
        stripUpdates = stripUpdates,
        chipUpdates = chipUpdates,
        lastStripChips = lastStripChips,
    )

    /**
     * Whether the engine has an initialized main dictionary, as autocorrection asks it
     * (`hasAtLeastOneInitializedMainDictionary`); null when the engine is gone or its dictionaries are
     * now for another language than [languageTag].
     */
    private fun dictionaryLoaded(languageTag: String): Boolean? = runCatching {
        val facilitator = dictionaries?.get() ?: return null
        if (facilitator.mainLocale.language != Locale.forLanguageTag(languageTag).language) return null
        facilitator.hasAtLeastOneInitializedMainDictionary()
    }.getOrNull()

    /** The values of [settings] (loaded for the field) as a snapshot; [imeOptions] from the field's EditorInfo. */
    internal fun snapshotOf(context: Context, imeOptions: Int, settings: SettingsValues, atMillis: Long): FieldReport.Snapshot {
        val attributes = settings.mInputAttributes
        val inputType = attributes.mInputType
        if (attributes.mIsPasswordField || InputTypeUtils.isAnyPasswordInputType(inputType) || FieldReport.isPassword(inputType))
            return FieldReport.Snapshot.Password
        if (settings.mIsLocked || isDeviceLocked(context) || isUserLocked(context)) return FieldReport.Snapshot.Hidden(locked = true)
        // incognito: the setting, a field that forbids learning (InputAttributes folds it in only for text fields)
        if (settings.mIncognitoModeEnabled || attributes.mNoLearning || imeOptions and FieldReport.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            return FieldReport.Snapshot.Hidden(locked = false)
        val prefs = context.prefs()
        return FieldReport.Snapshot.Field(
            packageName = attributes.mTargetApplicationPackageName?.takeIf { it.isNotEmpty() },
            inputType = inputType,
            imeOptions = imeOptions,
            atMillis = atMillis,
            appSuggestions = attributes.mShouldShowSuggestions,
            appAutoCorrect = attributes.mInputTypeShouldAutoCorrect,
            suggestionsSetting = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS),
            stripHidden = settings.mSuggestionStripHiddenPerUserSettings,
            suggestionsShown = settings.mSuggestionsEnabled,
            lookups = settings.needsToLookupSuggestions(),
            autoCorrectSetting = settings.mAutoCorrectionEnabledPerUserSettings,
            moreAutoCorrect = prefs.getBoolean(Settings.PREF_MORE_AUTO_CORRECTION, Defaults.PREF_MORE_AUTO_CORRECTION),
            urlDetection = settings.mUrlDetectionEnabled,
            autoCorrectEnabled = settings.mAutoCorrectEnabled,
            autoCapSetting = prefs.getBoolean(Settings.PREF_AUTO_CAP, Defaults.PREF_AUTO_CAP),
            autoCap = settings.mAutoCap,
            learningPaused = settings.mLearningPaused,
            languageTag = settings.mLocale.toLanguageTag(),
        )
    }

    @JvmStatic @Synchronized fun clear() {
        snapshot = null
        dictionaries = null
        switched = false
        stripUpdates = 0
        chipUpdates = 0
        lastStripChips = false
        inCommand = false
    }
}
