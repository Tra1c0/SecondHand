// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.FieldReport
import helium314.keyboard.agent.updates.UpdateOnOpen
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
 * process, like [SubtypeSwitchLog]). Holds a [FieldReport.Snapshot]: the package name, `inputType`
 * (as the app sent it and as the engine uses it), `imeOptions`, the language tag and booleans read
 * from the `InputAttributes` and `SettingsValues` the engine uses; never the field's text, hint,
 * label or name. A password field leaves only [FieldReport.Snapshot.Password], a locked device only
 * [FieldReport.Snapshot.Locked]; either replaces the field before. A field that asks for no learning (the
 * app's flag, or the owner's incognito) keeps its type, flags and verdict but neither package nor
 * app name (stage 61: that is where the owner needs the reason). A field of the
 * keyboard's own settings changes nothing, and nothing of its strip is counted.
 *
 * Fed by one line in `LatinIME.onStartInputViewInternal` ([capture], after the settings were loaded
 * for the field), by [SubtypeSwitchLog.onSwitched] ([onSubtypeSwitched]: the next read takes the
 * settings the engine reloaded for the new language), by [KeyboardCommands.createChipsView]
 * ([onStrip]: the keyboard's chips, a command) and by `SuggestionStripView` ([onStripView]: what the
 * strip shows). Called from the engine: nothing in here may throw. One lock for every call (review
 * of stage 55, M1). Stage 56: [capture] also starts [UpdateOnOpen] (no new engine line), and
 * [stripNeutral] tells it whether the strip of the field is empty.
 */
object LastField {
    private val lock = Any()
    private var snapshot: FieldReport.Snapshot? = null
    /** The engine's dictionaries, asked when the page opens (they load in the background). */
    private var dictionaries: WeakReference<DictionaryFacilitator>? = null
    /** A language switch since [capture]: [current] takes the settings the engine reloaded for this field. */
    private var switched = false
    /** The keyboard is open in a field of its own settings: strip updates there are not the app's (M2). */
    private var ownField = false

    private class StripState(val strip: FieldReport.Strip, val wordsOffered: Boolean) {
        val covered get() = FieldReport.Live(strip = strip, wordsOffered = wordsOffered).stripCovered
    }
    /** The strip at its last change; null until it reports for this field. */
    private var strip: StripState? = null
    /** Earlier strip contents of this field (the current one not yet counted) and how many were covered. */
    private var stripUpdates = 0
    private var coveredUpdates = 0
    private var chipUpdates = 0
    /** [onStrip] said the keyboard's chips come; the next content of the strip are these chips. */
    private var pendingChips = false
    /** The external view the strip shows now is the keyboard's chips. */
    private var externalChips = false
    private var inCommand = false

    /** `LatinIME.onStartInputViewInternal`, once the settings for [editorInfo] are loaded. */
    @JvmStatic fun capture(context: Context, editorInfo: EditorInfo?, settings: SettingsValues?, dictionaries: DictionaryFacilitator?) {
        // stage 56: the same engine line starts the update check on open; it never throws, never blocks
        // and returns at once (the request runs on its own thread)
        UpdateOnOpen.onStartInputView(context, editorInfo, settings)
        synchronized(lock) {
            runCatching {
                val values = settings ?: return
                // a field of the keyboard's own settings (their search on the way to the diagnose page)
                // keeps the snapshot of the app before it (lead, stage 55); its strip is not counted (M2)
                val packageName = editorInfo?.packageName ?: values.mInputAttributes.mTargetApplicationPackageName
                ownField = packageName == context.packageName
                if (ownField) return
                snapshot = snapshotOf(context, editorInfo?.inputType ?: values.mInputAttributes.mInputType,
                    editorInfo?.imeOptions ?: 0, values, SystemClock.elapsedRealtime())
                this.dictionaries = dictionaries?.let { WeakReference(it) }
                switched = false
                strip = null
                stripUpdates = 0
                coveredUpdates = 0
                chipUpdates = 0
                pendingChips = false
                externalChips = false
                inCommand = false
            }
        }
    }

    /** [SubtypeSwitchLog.onSwitched]: the subtype changes; the engine reloads its settings right after. */
    @JvmStatic fun onSubtypeSwitched() {
        synchronized(lock) { switched = true }
    }

    private fun counting() = snapshot is FieldReport.Snapshot.Field && !ownField

    /** Every strip update with a strip ([KeyboardCommands.createChipsView]): the keyboard's own chips, a command. */
    @JvmStatic fun onStrip(chips: Boolean, insideCommand: Boolean) {
        synchronized(lock) {
            if (!counting()) return
            if (chips && chipUpdates < Int.MAX_VALUE) chipUpdates++
            pendingChips = chips
            inCommand = insideCommand
        }
    }

    /**
     * `SuggestionStripView` after new content ([newContent]: words or an external view) and after the
     * toolbar or the emoji row changed: [wordsOffered] = the strip got words (not punctuation),
     * [external] = a view in their place, [toolbar] = the expanded toolbar, [emojis] = the emoji row.
     */
    @JvmStatic fun onStripView(newContent: Boolean, wordsOffered: Boolean, external: Boolean, toolbar: Boolean, emojis: Boolean) {
        synchronized(lock) {
            if (!counting()) return
            if (newContent) {
                strip?.let {
                    if (stripUpdates < Int.MAX_VALUE) stripUpdates++
                    if (it.covered && coveredUpdates < Int.MAX_VALUE) coveredUpdates++
                }
                externalChips = external && pendingChips
                pendingChips = false
            }
            val kind = when {
                emojis -> FieldReport.Strip.EMOJIS
                external -> if (externalChips) FieldReport.Strip.KEYBOARD_CHIPS else FieldReport.Strip.PILL
                toolbar -> FieldReport.Strip.TOOLBAR
                wordsOffered -> FieldReport.Strip.WORDS
                else -> FieldReport.Strip.EMPTY
            }
            // second review of stage 55 (M-a): a report without new content can only cover the strip, never
            // uncover it. hideWindow closes an expanded toolbar (and the flat menu closes) before the
            // diagnose page opens; that must not turn "words under the toolbar" into "words".
            val last = strip
            if (!newContent && last != null && last.strip == FieldReport.Strip.TOOLBAR && kind != FieldReport.Strip.TOOLBAR
                && kind != FieldReport.Strip.EMOJIS && kind != FieldReport.Strip.PILL && kind != FieldReport.Strip.KEYBOARD_CHIPS) return
            strip = StripState(kind, wordsOffered && !external)
        }
    }

    /**
     * Stage 56: the strip of the current field last showed the empty neutral strip (no words, no
     * chips, no pill, no toolbar, no emoji row), so [UpdateOnOpen] may show the update chip there.
     * False in password, no-learning, incognito and locked fields, in the keyboard's own settings and before the
     * strip reported for this field.
     */
    @JvmStatic fun stripNeutral(): Boolean = synchronized(lock) {
        counting() && !inCommand && strip?.strip == FieldReport.Strip.EMPTY
            && (snapshot as? FieldReport.Snapshot.Field)?.learningOff == false
    }

    /** The snapshot, null before the keyboard was shown in a field. */
    fun current(context: Context): FieldReport.Snapshot? = synchronized(lock) {
        val field = snapshot as? FieldReport.Snapshot.Field
        if (field != null && switched) runCatching {
            // the settings LatinIME reloaded after the switch, if they still belong to this field
            val values = Settings.getValues()
            val attributes = values?.mInputAttributes
            if (attributes != null && attributes.mInputType == field.inputType
                && (field.learningOff || attributes.mTargetApplicationPackageName.orEmpty() == field.packageName.orEmpty())) {
                snapshot = when (val fresh = snapshotOf(context, field.rawInputType, field.imeOptions, values, field.atMillis)) {
                    is FieldReport.Snapshot.Field -> fresh.copy(atMillis = field.atMillis)
                    else -> fresh
                }
                switched = false
            }
        }
        snapshot
    }

    /** What the page asks now; [dictionaryPresent] is the file check of [Diagnose] (off the main thread, null = unknown). */
    fun live(languageTag: String, dictionaryPresent: Boolean?): FieldReport.Live = synchronized(lock) {
        val last = strip
        FieldReport.Live(
            dictionaryPresent = dictionaryPresent,
            dictionaryLoaded = dictionaryLoaded(languageTag),
            inCommand = inCommand,
            strip = last?.strip,
            wordsOffered = last?.wordsOffered == true,
            stripUpdates = stripUpdates + (if (last != null) 1 else 0),
            coveredUpdates = coveredUpdates + (if (last?.covered == true) 1 else 0),
            chipUpdates = chipUpdates,
        )
    }

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

    /**
     * The values of [settings] (loaded for the field) as a snapshot; [rawInputType] and [imeOptions]
     * as the field's EditorInfo has them (the engine's `inputType` may be changed by `AppWorkarounds`).
     */
    internal fun snapshotOf(context: Context, rawInputType: Int, imeOptions: Int, settings: SettingsValues, atMillis: Long): FieldReport.Snapshot {
        val attributes = settings.mInputAttributes
        val inputType = attributes.mInputType
        if (attributes.mIsPasswordField || InputTypeUtils.isAnyPasswordInputType(inputType) || FieldReport.isPassword(inputType)
            || FieldReport.isPassword(rawInputType))
            return FieldReport.Snapshot.Password
        // review of stage 55 (N5): SettingsValues reads the lock state when the field starts (isDeviceLocked;
        // a user not unlocked yet is behind the keyguard as well)
        if (settings.mIsLocked) return FieldReport.Snapshot.Locked
        val prefs = context.prefs()
        // stage 61: a field without learning (the field's flag, InputAttributes folds it in only for text fields, or
        // the owner's incognito) keeps its flags and verdict, but not the app: no package, no app name
        val noLearning = attributes.mNoLearning || imeOptions and FieldReport.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        val incognito = !noLearning && prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)
        val packageName = if (noLearning || incognito) null else attributes.mTargetApplicationPackageName?.takeIf { it.isNotEmpty() }
        return FieldReport.Snapshot.Field(
            packageName = packageName,
            noLearning = noLearning,
            incognito = incognito,
            forced = settings.mCorrectionForced,
            appForced = ForcedApps.isListed(prefs, packageName),
            inputType = inputType,
            rawInputType = rawInputType,
            imeOptions = imeOptions,
            atMillis = atMillis,
            appSuggestions = attributes.mShouldShowSuggestions,
            appAutoCorrect = attributes.mInputTypeShouldAutoCorrect,
            appCompletions = attributes.mApplicationSpecifiedCompletionOn,
            suggestionsSetting = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS),
            alwaysShowSetting = prefs.getBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS),
            alwaysShowExceptWeb = prefs.getBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT,
                Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT),
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

    @JvmStatic fun clear() {
        synchronized(lock) {
            snapshot = null
            dictionaries = null
            switched = false
            ownField = false
            strip = null
            stripUpdates = 0
            coveredUpdates = 0
            chipUpdates = 0
            pendingChips = false
            externalChips = false
            inCommand = false
        }
    }
}
