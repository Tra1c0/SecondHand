// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.FieldReport.FLAG_CAP_CHARACTERS
import dev.agentkeyboard.core.FieldReport.FLAG_CAP_SENTENCES
import dev.agentkeyboard.core.FieldReport.FLAG_CAP_WORDS
import dev.agentkeyboard.core.FieldReport.IME_ACTION_SEARCH
import dev.agentkeyboard.core.FieldReport.IME_MASK_ACTION
import dev.agentkeyboard.core.FieldReport.TYPE_CLASS_TEXT
import dev.agentkeyboard.core.FieldReport.TYPE_MASK_CLASS
import dev.agentkeyboard.core.FieldReport.TYPE_MASK_VARIATION

/**
 * Stage 61 "Korrektur in Apps erzwingen": the pure part. Some apps (the owner's report: Instagram chats)
 * send message fields with `TYPE_TEXT_FLAG_NO_SUGGESTIONS` and no `CAP_*` flags, so the keyboard shows
 * no suggestions, does not autocorrect and does not capitalise. For a package the owner listed, such a
 * field is treated like a normal message field. This object only says which fields qualify; the list of
 * apps and the use in `SettingsValues` and `InputLogic` are on the app side (`ForcedApps`).
 *
 * Nothing of the field's text is looked at; only the numbers of `inputType` and `imeOptions`.
 */
object ForcedCorrection {
    /** Apps forced until the owner changes the list (lead's assumption, stage 61; removable). */
    val DEFAULT_PACKAGES: Set<String> = setOf("com.instagram.android")

    /** `TYPE_TEXT_VARIATION_*` that are never forced: addresses, names, filters and every password. */
    private val EXCLUDED_VARIATIONS = setOf(
        FieldReport.VARIATION_URI, FieldReport.VARIATION_EMAIL_ADDRESS, FieldReport.VARIATION_WEB_EMAIL_ADDRESS,
        FieldReport.VARIATION_PERSON_NAME, FieldReport.VARIATION_POSTAL_ADDRESS,
        FieldReport.VARIATION_PASSWORD, FieldReport.VARIATION_VISIBLE_PASSWORD, FieldReport.VARIATION_WEB_PASSWORD,
        FieldReport.VARIATION_FILTER, FieldReport.VARIATION_PHONETIC,
    )

    private const val CAP_FLAGS = FLAG_CAP_CHARACTERS or FLAG_CAP_WORDS or FLAG_CAP_SENTENCES

    /**
     * Whether a field with [inputType] and [imeOptions] may be forced when its app is on the list: a text
     * field that is no password, e-mail, URI, filter, phonetic, person-name or postal-address field and
     * whose action is not search, go or next and that does not force ASCII.
     */
    fun eligible(inputType: Int, imeOptions: Int): Boolean {
        if (inputType and TYPE_MASK_CLASS != TYPE_CLASS_TEXT) return false
        if ((inputType and TYPE_MASK_VARIATION) in EXCLUDED_VARIATIONS) return false
        // login and form fields (user names, account names) ask for GO or NEXT; SEND, DONE, NONE and UNSPECIFIED stay
        val action = imeOptions and IME_MASK_ACTION
        if (action == IME_ACTION_SEARCH || action == FieldReport.IME_ACTION_GO || action == FieldReport.IME_ACTION_NEXT) return false
        return imeOptions and FieldReport.IME_FLAG_FORCE_ASCII == 0
    }

    /**
     * The `inputType` the capitalisation reads: [inputType] with `TYPE_TEXT_FLAG_CAP_SENTENCES` added when
     * [forced] and the app set no `CAP_*` flag at all (an app that asks for words or characters keeps that).
     */
    fun capsInputType(inputType: Int, forced: Boolean): Int =
        if (forced && inputType and TYPE_MASK_CLASS == TYPE_CLASS_TEXT && inputType and CAP_FLAGS == 0) inputType or FLAG_CAP_SENTENCES else inputType
}
