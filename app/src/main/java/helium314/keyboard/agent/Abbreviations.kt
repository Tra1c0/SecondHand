// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import java.util.Locale

/**
 * Stage 48: abbreviations whose period does not end a sentence, so the next word is not capitalized
 * automatically ("ca. zehn", "bzw. das", "z. B. wenn"). HeliBoard already knows the forms with inner
 * periods ("z.B.", "e.g."); this adds words without one and the spaced German forms. Only words
 * that practically never end a sentence are listed ("usw." and "etc." often do, so they are not).
 */
object Abbreviations {
    private val GERMAN = setOf(
        "bzw", "ca", "vgl", "ggf", "evtl", "inkl", "zzgl", "exkl", "bspw", "sog", "ehem", "geb",
        "dr", "prof", "nr", "hr", "fr", "str", "abs", "bd", "jh", "jhd", "st",
    )
    private val ENGLISH = setOf("approx", "vs", "mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr", "mt", "ca", "cf", "viz")

    /**
     * Whether the period right after `text[start, end)` (letters only) closes a known abbreviation.
     * Also true for a single letter after another single letter with period and space ("z. B",
     * "d. h", "u. a"), in every language.
     */
    @JvmStatic
    fun isAbbreviation(text: CharSequence, start: Int, end: Int, german: Boolean, english: Boolean): Boolean {
        if (start < 0 || end <= start || end > text.length) return false
        val word = text.subSequence(start, end).toString().lowercase(Locale.ROOT)
        if (german && word in GERMAN) return true
        if (english && word in ENGLISH) return true
        return end - start == 1 && start >= 3 && text[start - 1] == ' ' && text[start - 2] == '.'
            && text[start - 3].isLetter() && (start == 3 || !text[start - 4].isLetter())
    }
}
