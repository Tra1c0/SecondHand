// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.format

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/**
 * Phone numbers (stage 41) through libphonenumber (Apache-2.0, metadata in the library, no
 * network): a valid number in another form gets its international format, e.g. "0151 23456789" in
 * Germany → "+49 151 23456789", "0043 1 234567" → "+43 1 234567".
 */
object PhoneFormat {
    /** Characters a typed number may contain; anything else (letters, `*`, `#`, `;`) is left alone. */
    private const val ALLOWED = "0123456789+ -/(). "
    /** Fewer digits are no complete number worth a hint; more are no phone number (E.164: 15). */
    const val MIN_DIGITS = 6
    const val MAX_DIGITS = 17

    /** Region for numbers without a country code: [country] if it is a region libphonenumber knows, else by [language]. */
    fun region(language: String, country: String): String {
        val upper = country.uppercase(Locale.ROOT)
        if (upper.length == 2 && upper in util.supportedRegions) return upper
        return when (language.lowercase(Locale.ROOT)) {
            "de" -> "DE"
            "es" -> "ES"
            "en" -> "US"
            "fr" -> "FR"
            "it" -> "IT"
            "pt" -> "PT"
            "nl" -> "NL"
            else -> "ZZ" // unknown: only numbers with a country code
        }
    }

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /** The international format of [text] if it is a valid number and looks different, else null. */
    fun suggest(text: String, region: String): String? {
        if (text.any { it !in ALLOWED }) return null
        val digits = text.count { it in '0'..'9' }
        if (digits < MIN_DIGITS || digits > MAX_DIGITS) return null
        // a plus sign only at the start
        if (text.lastIndexOf('+') > 0) return null
        val number = try { util.parse(text, region) } catch (_: NumberParseException) { return null }
        if (!util.isValidNumber(number)) return null
        val formatted = util.format(number, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        return formatted.takeIf { it != text }
    }
}
