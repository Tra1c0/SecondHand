// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.format

/**
 * Decimal separator note for number fields (stage 41). A note only: many number fields accept
 * only the point, so the field is never changed.
 */
object DecimalHint {
    /** Languages that write a decimal comma. */
    val COMMA = setOf("de", "es", "fr", "it", "pt", "nl", "pl", "ru", "uk", "tr", "cs", "sk", "sv", "da", "nb", "no",
        "fi", "el", "hu", "ro", "hr", "sl", "bg", "ca")
    /** Languages that write a decimal point. */
    val POINT = setOf("en", "ja", "zh", "ko", "hi", "he", "th")

    /** The separator of [language], or null when unknown. */
    fun separator(language: String): Char? = when (language.lowercase()) {
        in COMMA -> ','
        in POINT -> '.'
        else -> null
    }

    private val NUMBER = Regex("^-?([0-9]+)([.,])([0-9]+)$")

    /**
     * A note when [text] is one decimal number with the other separator than [language] uses.
     * Exactly three digits after it could be a thousands separator ("1.000"): no note.
     */
    fun check(text: String, language: String): FormatHint.Decimal? {
        val usual = separator(language) ?: return null
        val match = NUMBER.matchEntire(text) ?: return null
        val typed = match.groupValues[2][0]
        if (typed == usual || match.groupValues[3].length == 3) return null
        return FormatHint.Decimal(text.replace(typed, usual), usual)
    }
}
