// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 55 "Diagnose: Letztes Feld": why a field had no suggestions, no capitalisation or no
 * autocorrection. Pure: the app reads the values the engine really used (`InputAttributes`,
 * `SettingsValues`) into a [Snapshot]; [verdict] names the first reason that switched something off.
 *
 * A snapshot never holds text of the field, its hint, its name or its label: only the app's package
 * name, the raw `inputType` and `imeOptions` numbers, the subtype's language tag, booleans and when it
 * was taken. A password field keeps nothing but [Snapshot.Password]; a field typed in incognito or
 * while the device was locked keeps nothing but [Snapshot.Hidden].
 */
object FieldReport {
    // android.text.InputType and EditorInfo values (stable platform API), so the core stays free of Android
    const val TYPE_MASK_CLASS = 0x0000000f
    const val TYPE_MASK_VARIATION = 0x00000ff0
    const val TYPE_CLASS_TEXT = 1
    const val TYPE_CLASS_NUMBER = 2
    const val TYPE_CLASS_PHONE = 3
    const val TYPE_CLASS_DATETIME = 4
    const val VARIATION_URI = 0x10
    const val VARIATION_EMAIL_ADDRESS = 0x20
    const val VARIATION_EMAIL_SUBJECT = 0x30
    const val VARIATION_SHORT_MESSAGE = 0x40
    const val VARIATION_LONG_MESSAGE = 0x50
    const val VARIATION_PERSON_NAME = 0x60
    const val VARIATION_POSTAL_ADDRESS = 0x70
    const val VARIATION_PASSWORD = 0x80
    const val VARIATION_VISIBLE_PASSWORD = 0x90
    const val VARIATION_WEB_EDIT_TEXT = 0xa0
    const val VARIATION_FILTER = 0xb0
    const val VARIATION_PHONETIC = 0xc0
    const val VARIATION_WEB_EMAIL_ADDRESS = 0xd0
    const val VARIATION_WEB_PASSWORD = 0xe0
    const val NUMBER_VARIATION_PASSWORD = 0x10
    const val FLAG_CAP_CHARACTERS = 0x1000
    const val FLAG_CAP_WORDS = 0x2000
    const val FLAG_CAP_SENTENCES = 0x4000
    const val FLAG_AUTO_CORRECT = 0x8000
    const val FLAG_AUTO_COMPLETE = 0x10000
    const val FLAG_MULTI_LINE = 0x20000
    const val FLAG_IME_MULTI_LINE = 0x40000
    const val FLAG_NO_SUGGESTIONS = 0x80000
    const val NUMBER_FLAG_SIGNED = 0x1000
    const val NUMBER_FLAG_DECIMAL = 0x2000
    const val IME_MASK_ACTION = 0xff
    const val IME_ACTION_SEARCH = 3
    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
    const val IME_FLAG_FORCE_ASCII = 0x80000000.toInt()

    enum class FieldType {
        /** `TYPE_NULL`: the app reports no text field at all. */
        NONE, TEXT, SEARCH, URL, EMAIL, PASSWORD, PERSON_NAME, POSTAL_ADDRESS, SHORT_MESSAGE, LONG_MESSAGE,
        EMAIL_SUBJECT, WEB_TEXT, FILTER, PHONETIC, NUMBER, PHONE, DATETIME,
    }

    /** The capitalisation the field asks for with its `TYPE_TEXT_FLAG_CAP_*` flags. */
    enum class Caps { NONE, SENTENCES, WORDS, CHARACTERS }

    /** The flags of a field that matter here (capitalisation has its own row). */
    enum class Flag { MULTI_LINE, IME_MULTI_LINE, NO_SUGGESTIONS, AUTO_CORRECT, AUTO_COMPLETE, SIGNED, DECIMAL, FORCE_ASCII }

    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and TYPE_MASK_VARIATION
        return when (inputType and TYPE_MASK_CLASS) {
            TYPE_CLASS_TEXT -> variation == VARIATION_PASSWORD || variation == VARIATION_VISIBLE_PASSWORD || variation == VARIATION_WEB_PASSWORD
            TYPE_CLASS_NUMBER -> variation == NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    fun fieldType(inputType: Int, imeOptions: Int): FieldType {
        if (inputType == 0) return FieldType.NONE
        if (isPassword(inputType)) return FieldType.PASSWORD
        return when (inputType and TYPE_MASK_CLASS) {
            TYPE_CLASS_NUMBER -> FieldType.NUMBER
            TYPE_CLASS_PHONE -> FieldType.PHONE
            TYPE_CLASS_DATETIME -> FieldType.DATETIME
            TYPE_CLASS_TEXT -> when (inputType and TYPE_MASK_VARIATION) {
                VARIATION_URI -> FieldType.URL
                VARIATION_EMAIL_ADDRESS, VARIATION_WEB_EMAIL_ADDRESS -> FieldType.EMAIL
                else -> if (imeOptions and IME_MASK_ACTION == IME_ACTION_SEARCH) FieldType.SEARCH
                    else when (inputType and TYPE_MASK_VARIATION) {
                        VARIATION_EMAIL_SUBJECT -> FieldType.EMAIL_SUBJECT
                        VARIATION_SHORT_MESSAGE -> FieldType.SHORT_MESSAGE
                        VARIATION_LONG_MESSAGE -> FieldType.LONG_MESSAGE
                        VARIATION_PERSON_NAME -> FieldType.PERSON_NAME
                        VARIATION_POSTAL_ADDRESS -> FieldType.POSTAL_ADDRESS
                        VARIATION_WEB_EDIT_TEXT -> FieldType.WEB_TEXT
                        VARIATION_FILTER -> FieldType.FILTER
                        VARIATION_PHONETIC -> FieldType.PHONETIC
                        else -> FieldType.TEXT
                    }
            }
            else -> FieldType.NONE
        }
    }

    /** As `TextUtils.getCapsMode` reads the request: characters before words before sentences; only text fields. */
    fun caps(inputType: Int): Caps = when {
        inputType and TYPE_MASK_CLASS != TYPE_CLASS_TEXT -> Caps.NONE
        inputType and FLAG_CAP_CHARACTERS != 0 -> Caps.CHARACTERS
        inputType and FLAG_CAP_WORDS != 0 -> Caps.WORDS
        inputType and FLAG_CAP_SENTENCES != 0 -> Caps.SENTENCES
        else -> Caps.NONE
    }

    fun flags(inputType: Int, imeOptions: Int): List<Flag> = buildList {
        when (inputType and TYPE_MASK_CLASS) {
            TYPE_CLASS_TEXT -> {
                if (inputType and FLAG_MULTI_LINE != 0) add(Flag.MULTI_LINE)
                if (inputType and FLAG_IME_MULTI_LINE != 0) add(Flag.IME_MULTI_LINE)
                if (inputType and FLAG_NO_SUGGESTIONS != 0) add(Flag.NO_SUGGESTIONS)
                if (inputType and FLAG_AUTO_CORRECT != 0) add(Flag.AUTO_CORRECT)
                if (inputType and FLAG_AUTO_COMPLETE != 0) add(Flag.AUTO_COMPLETE)
            }
            TYPE_CLASS_NUMBER -> {
                if (inputType and NUMBER_FLAG_SIGNED != 0) add(Flag.SIGNED)
                if (inputType and NUMBER_FLAG_DECIMAL != 0) add(Flag.DECIMAL)
            }
        }
        if (imeOptions and IME_FLAG_FORCE_ASCII != 0) add(Flag.FORCE_ASCII)
    }

    sealed interface Snapshot {
        /** A password field: nothing else is kept. */
        object Password : Snapshot
        /** Incognito (setting or a field that forbids learning) or a locked device: nothing is kept. */
        data class Hidden(val locked: Boolean) : Snapshot

        /**
         * A normal field. Defaults are a multi-line text field with everything on, for tests.
         * App side from `InputAttributes`, keyboard side from `SettingsValues` and the settings it reads.
         */
        data class Field(
            val packageName: String? = null,
            val inputType: Int = TYPE_CLASS_TEXT or FLAG_CAP_SENTENCES or FLAG_MULTI_LINE,
            val imeOptions: Int = 0,
            /** When the snapshot was taken, on the clock the app chose (elapsed realtime). */
            val atMillis: Long = 0,
            /** `InputAttributes.mShouldShowSuggestions`: no password and no `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. */
            val appSuggestions: Boolean = true,
            /** `InputAttributes.mInputTypeShouldAutoCorrect`: the flag, or a multi-line field that is no URL or e-mail. */
            val appAutoCorrect: Boolean = true,
            /** The setting "Änderungsvorschläge". */
            val suggestionsSetting: Boolean = true,
            /** `mSuggestionStripHiddenPerUserSettings`: the toolbar mode leaves no room for suggestions. */
            val stripHidden: Boolean = false,
            /** `mSuggestionsEnabled`: the strip shows suggestions in this field. */
            val suggestionsShown: Boolean = true,
            /** `needsToLookupSuggestions()`: the engine looks up words at all (also for autocorrection only). */
            val lookups: Boolean = true,
            /** `mAutoCorrectionEnabledPerUserSettings`. */
            val autoCorrectSetting: Boolean = true,
            /** The setting "Mehr Autokorrektur": also in fields that do not ask for it. */
            val moreAutoCorrect: Boolean = false,
            /** `mUrlDetectionEnabled`: autocorrection also in URL and e-mail fields. */
            val urlDetection: Boolean = false,
            /** `mAutoCorrectEnabled`. */
            val autoCorrectEnabled: Boolean = true,
            /** The setting "Autom. Groß-/Kleinschreibung". */
            val autoCapSetting: Boolean = true,
            /** `mAutoCap`: the setting and a script with capital letters. */
            val autoCap: Boolean = true,
            /** The keyboard's "Lernen pausieren" (learning only). */
            val learningPaused: Boolean = false,
            /** The language of the subtype the engine used (`SettingsValues.mLocale`). */
            val languageTag: String = "de",
        ) : Snapshot {
            val type: FieldType get() = fieldType(inputType, imeOptions)
            val caps: Caps get() = caps(inputType)
            val flags: List<Flag> get() = flags(inputType, imeOptions)
        }
    }

    /**
     * What is only known when the page asks: the main dictionary of the snapshot's language (file
     * present, loaded by the engine; null = unknown), whether the cursor was inside a command at the
     * last strip update, and how many strip updates since the field started showed the keyboard's own
     * chips instead of suggestions.
     */
    data class Live(
        val dictionaryPresent: Boolean? = true,
        val dictionaryLoaded: Boolean? = true,
        val inCommand: Boolean = false,
        val stripUpdates: Int = 0,
        val chipUpdates: Int = 0,
        val lastStripChips: Boolean = false,
    )

    enum class Finding {
        PASSWORD, LOCKED, INCOGNITO,
        NO_TEXT_FIELD, NOT_TEXT,
        STRIP_HIDDEN, SUGGESTIONS_SETTING_OFF, APP_NO_SUGGESTIONS,
        NO_DICTIONARY, DICTIONARY_NOT_LOADED,
        AUTOCORRECT_SETTING_OFF, AUTOCORRECT_ADDRESS, AUTOCORRECT_APP, AUTOCORRECT_OFF,
        CAPS_SETTING_OFF, CAPS_SCRIPT, CAPS_APP,
        IN_COMMAND, CHIPS,
        ALL_ON,
    }

    /** The first reason, and the language tag for the findings that name the language. */
    data class Verdict(val finding: Finding, val languageTag: String? = null)

    /** What really runs in the field, after every switch of the app and of the keyboard. */
    data class Effective(val suggestions: Boolean, val autoCorrect: Boolean, val caps: Boolean)

    fun effective(field: Snapshot.Field, live: Live): Effective {
        val text = field.inputType and TYPE_MASK_CLASS == TYPE_CLASS_TEXT
        return Effective(
            suggestions = field.suggestionsShown,
            autoCorrect = field.autoCorrectEnabled && field.lookups && live.dictionaryLoaded != false && !live.inCommand,
            caps = text && field.autoCap && field.caps != Caps.NONE,
        )
    }

    /**
     * The first reason that switched something off, in this order: the field's privacy, a field that
     * is no text, suggestions, the dictionary, autocorrection, capitalisation, then the keyboard's own
     * state at the last strip update (a command, its chips). [Finding.ALL_ON] when nothing is off.
     */
    fun verdict(snapshot: Snapshot, live: Live = Live()): Verdict = when (snapshot) {
        Snapshot.Password -> Verdict(Finding.PASSWORD)
        is Snapshot.Hidden -> Verdict(if (snapshot.locked) Finding.LOCKED else Finding.INCOGNITO)
        is Snapshot.Field -> field(snapshot, live)
    }

    private fun field(f: Snapshot.Field, live: Live): Verdict {
        val language = f.languageTag
        val type = f.type
        if (type == FieldType.NONE) return Verdict(Finding.NO_TEXT_FIELD)
        if (type == FieldType.NUMBER || type == FieldType.PHONE || type == FieldType.DATETIME) return Verdict(Finding.NOT_TEXT)
        if (!f.suggestionsShown) return Verdict(when {
            f.stripHidden -> Finding.STRIP_HIDDEN
            !f.suggestionsSetting -> Finding.SUGGESTIONS_SETTING_OFF
            else -> Finding.APP_NO_SUGGESTIONS
        })
        if (live.dictionaryPresent == false) return Verdict(Finding.NO_DICTIONARY, language)
        if (live.dictionaryLoaded == false) return Verdict(Finding.DICTIONARY_NOT_LOADED, language)
        if (!f.autoCorrectEnabled || !f.lookups) return Verdict(when {
            !f.autoCorrectSetting -> Finding.AUTOCORRECT_SETTING_OFF
            (type == FieldType.URL || type == FieldType.EMAIL) && !f.urlDetection -> Finding.AUTOCORRECT_ADDRESS
            !f.appAutoCorrect && !f.moreAutoCorrect -> Finding.AUTOCORRECT_APP
            else -> Finding.AUTOCORRECT_OFF
        })
        if (!f.autoCapSetting) return Verdict(Finding.CAPS_SETTING_OFF)
        if (!f.autoCap) return Verdict(Finding.CAPS_SCRIPT, language)
        if (f.caps == Caps.NONE) return Verdict(Finding.CAPS_APP)
        if (live.inCommand) return Verdict(Finding.IN_COMMAND)
        if (live.lastStripChips) return Verdict(Finding.CHIPS)
        return Verdict(Finding.ALL_ON)
    }
}
