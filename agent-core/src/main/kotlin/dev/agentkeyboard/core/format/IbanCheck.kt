// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.format

import java.util.Locale

/**
 * IBAN check (stage 41): ISO 13616 check digits (modulo 97) and the length of the country. Only a
 * text that clearly is an IBAN counts: two letters of a known country, two digits, then letters and
 * digits up to exactly the length of that country, optionally in blocks separated by single spaces.
 */
object IbanCheck {
    /** IBAN length per country (SWIFT IBAN registry). */
    val LENGTHS: Map<String, Int> = mapOf(
        "AD" to 24, "AE" to 23, "AL" to 28, "AT" to 20, "AZ" to 28, "BA" to 20, "BE" to 16, "BG" to 22,
        "BH" to 22, "BI" to 27, "BR" to 29, "BY" to 28, "CH" to 21, "CR" to 22, "CY" to 28, "CZ" to 24,
        "DE" to 22, "DJ" to 27, "DK" to 18, "DO" to 28, "EE" to 20, "EG" to 29, "ES" to 24, "FI" to 18,
        "FK" to 18, "FO" to 18, "FR" to 27, "GB" to 22, "GE" to 22, "GI" to 23, "GL" to 18, "GR" to 27,
        "GT" to 28, "HR" to 21, "HU" to 28, "IE" to 22, "IL" to 23, "IQ" to 23, "IS" to 26, "IT" to 27,
        "JO" to 30, "KW" to 30, "KZ" to 20, "LB" to 28, "LC" to 32, "LI" to 21, "LT" to 20, "LU" to 20,
        "LV" to 21, "LY" to 25, "MC" to 27, "MD" to 24, "ME" to 22, "MK" to 19, "MN" to 20, "MR" to 27,
        "MT" to 31, "MU" to 30, "NI" to 28, "NL" to 18, "NO" to 15, "OM" to 23, "PK" to 24, "PL" to 28,
        "PS" to 29, "QA" to 29, "RO" to 24, "RS" to 22, "RU" to 33, "SA" to 24, "SC" to 31, "SD" to 18,
        "SE" to 24, "SI" to 19, "SK" to 24, "SM" to 27, "SO" to 23, "ST" to 25, "SV" to 28, "TL" to 23,
        "TN" to 24, "TR" to 26, "UA" to 29, "VA" to 22, "VG" to 24, "XK" to 20, "YE" to 30,
    )

    /** The IBAN in [text] without spaces and in capitals, or null when [text] is not clearly a complete IBAN. */
    fun normalize(text: String): String? {
        // single spaces between blocks only; no other characters, no leading or trailing space
        if (text.isEmpty() || text.first() == ' ' || text.last() == ' ' || "  " in text) return null
        val compact = text.replace(" ", "").uppercase(Locale.ROOT)
        if (compact.any { it !in 'A'..'Z' && it !in '0'..'9' }) return null
        val length = LENGTHS[compact.take(2)] ?: return null
        if (compact.length != length || !compact[2].isAsciiDigit() || !compact[3].isAsciiDigit()) return null
        if (!compact[0].isAsciiLetter() || !compact[1].isAsciiLetter()) return null
        return compact
    }

    /** True if the check digits of the compact, upper-case [iban] are right (remainder 1 modulo 97). */
    fun isValid(iban: String): Boolean {
        if (iban.length < 5) return false
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        var remainder = 0
        for (c in rearranged) {
            val value = when (c) {
                in '0'..'9' -> c - '0'
                in 'A'..'Z' -> c - 'A' + 10
                else -> return false
            }
            remainder = if (value < 10) (remainder * 10 + value) % 97 else (remainder * 100 + value) % 97
        }
        return remainder == 1
    }

    /** "DE89370400440532013000" → "DE89 3704 0044 0532 0130 00". */
    fun group(iban: String) = iban.chunked(4).joinToString(" ")

    /** The hint for [text], the whole field; null if it is not clearly an IBAN. */
    fun hint(text: String): FormatHint.Iban? {
        val iban = normalize(text) ?: return null
        val valid = isValid(iban)
        val grouped = group(iban)
        return FormatHint.Iban(valid, grouped, if (valid && grouped != text) grouped else null)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'
    private fun Char.isAsciiLetter() = this in 'A'..'Z'
}
