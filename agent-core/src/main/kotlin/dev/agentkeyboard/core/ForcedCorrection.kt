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

    private const val CAP_FLAGS = FLAG_CAP_CHARACTERS or FLAG_CAP_WORDS or FLAG_CAP_SENTENCES

    /** Why a field is never forced (the card names it). */
    enum class Excluded { NOT_TEXT, PASSWORD, ADDRESS, NAME, FILTER, SEARCH, GO_NEXT, ASCII }

    /**
     * Why a field with [inputType] and [imeOptions] is not forced even when its app is on the list; null when it
     * qualifies: a text field that is no password, e-mail, URI, filter, phonetic, person-name or postal-address
     * field, whose action is not search, that does not force ASCII and, when it is single-line, whose action is
     * not go or next. Login and form fields (user names) are single-line and ask for GO or NEXT; Android's
     * `TextView` also sets NEXT by itself for chat fields, which are multi-line and stay eligible.
     */
    fun excludedReason(inputType: Int, imeOptions: Int): Excluded? {
        if (inputType and TYPE_MASK_CLASS != TYPE_CLASS_TEXT) return Excluded.NOT_TEXT
        when (inputType and TYPE_MASK_VARIATION) {
            FieldReport.VARIATION_PASSWORD, FieldReport.VARIATION_VISIBLE_PASSWORD, FieldReport.VARIATION_WEB_PASSWORD -> return Excluded.PASSWORD
            FieldReport.VARIATION_URI, FieldReport.VARIATION_EMAIL_ADDRESS, FieldReport.VARIATION_WEB_EMAIL_ADDRESS -> return Excluded.ADDRESS
            FieldReport.VARIATION_PERSON_NAME, FieldReport.VARIATION_POSTAL_ADDRESS -> return Excluded.NAME
            FieldReport.VARIATION_FILTER, FieldReport.VARIATION_PHONETIC -> return Excluded.FILTER
        }
        val action = imeOptions and IME_MASK_ACTION
        if (action == IME_ACTION_SEARCH) return Excluded.SEARCH
        if (imeOptions and FieldReport.IME_FLAG_FORCE_ASCII != 0) return Excluded.ASCII
        if ((action == FieldReport.IME_ACTION_GO || action == FieldReport.IME_ACTION_NEXT) && inputType and FieldReport.FLAG_MULTI_LINE == 0)
            return Excluded.GO_NEXT
        return null
    }

    /** Whether a field may be forced when its app is on the list ([excludedReason] is null). */
    fun eligible(inputType: Int, imeOptions: Int): Boolean = excludedReason(inputType, imeOptions) == null

    /**
     * The `inputType` the capitalisation reads: [inputType] with `TYPE_TEXT_FLAG_CAP_SENTENCES` added when
     * [forced] and the app set no `CAP_*` flag at all (an app that asks for words or characters keeps that).
     */
    fun capsInputType(inputType: Int, forced: Boolean): Int =
        if (forced && inputType and TYPE_MASK_CLASS == TYPE_CLASS_TEXT && inputType and CAP_FLAGS == 0) inputType or FLAG_CAP_SENTENCES else inputType
}
