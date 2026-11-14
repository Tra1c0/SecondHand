// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.format

/**
 * Stage 41 "Formatwächter": hints for fields with an unambiguous type. Pure functions, no
 * Android, no network. Nothing here changes text: a [FormatHint] only says what a tap on its
 * chip would write ([FormatHint.replacement], the whole new field text) or that it is a note only.
 */
enum class FieldKind {
    /** `TYPE_TEXT_VARIATION_EMAIL_ADDRESS` / `WEB_EMAIL_ADDRESS`. */
    EMAIL,
    /** `TYPE_CLASS_PHONE`. */
    PHONE,
    /** `TYPE_CLASS_NUMBER` with `TYPE_NUMBER_FLAG_DECIMAL`. */
    DECIMAL,
    /** Any other plain text field: only an IBAN that fills the whole field is checked. */
    TEXT,
}

sealed interface FormatHint {
    /** The whole new field text a tap writes, or null for a note that changes nothing. */
    val replacement: String?

    /** The address contains spaces; [replacement] is the address without them. */
    data class EmailSpaces(override val replacement: String) : FormatHint
    /** The domain looks like a typo of the common provider [domain]. */
    data class EmailDomain(val domain: String, override val replacement: String) : FormatHint
    /** No `@`, but the text ends with the provider [domain]. */
    data class EmailMissingAt(val domain: String, override val replacement: String) : FormatHint
    /** A valid phone number in another form than [replacement] (international format). */
    data class Phone(override val replacement: String) : FormatHint
    /**
     * A complete IBAN ([valid] = check digits right, modulo 97). [grouped] is the IBAN in blocks of
     * four; a tap writes it only for a valid IBAN that is not grouped like that yet.
     */
    data class Iban(val valid: Boolean, val grouped: String, override val replacement: String?) : FormatHint
    /** A decimal number with the other separator than the language uses; [suggested] shows the usual form. Note only. */
    data class Decimal(val suggested: String, val separator: Char) : FormatHint {
        override val replacement: String? get() = null
    }
}

object FormatGuard {
    /** Longer field texts are not checked (an e-mail address has at most 254 characters, but fields are short). */
    const val MAX_LENGTH = 100

    /**
     * Hints for [text], the whole content of a field of [kind]. [language] is the language of the
     * field or keyboard ("de"), [country] its country if known ("AT", may be empty). Empty when
     * there is nothing to say; never more than two hints.
     */
    fun check(kind: FieldKind, text: String, language: String, country: String = ""): List<FormatHint> {
        if (text.isEmpty() || text.length > MAX_LENGTH) return emptyList()
        return when (kind) {
            FieldKind.EMAIL -> EmailCheck.check(text)
            FieldKind.PHONE -> listOfNotNull(PhoneFormat.suggest(text, PhoneFormat.region(language, country))?.let { FormatHint.Phone(it) })
            FieldKind.DECIMAL -> listOfNotNull(DecimalHint.check(text, language))
            FieldKind.TEXT -> listOfNotNull(IbanCheck.hint(text))
        }
    }
}
