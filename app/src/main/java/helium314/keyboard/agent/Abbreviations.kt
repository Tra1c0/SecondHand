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
        "dr", "prof", "nr", "hr", "str", "abs", "bd", "st",
        "bzgl", "insb", "insbes", "lt", "gem", "mind", "allg", "ugs", "eigtl",
    )
    // review N2: "fr", "jh", "jhd" (German) and "ms", "st", "jr", "sr" (English) often end a sentence
    // ("bis Fr.", "im 19. Jh.", "John Smith Jr.") and are not listed
    private val ENGLISH = setOf("approx", "vs", "mr", "mrs", "dr", "prof", "mt", "ca", "cf", "viz")

    // stage 48 review N4: spaced letter abbreviations, known in every case ("Z. B." at a sentence start)
    private val LETTER_ABBREVIATIONS = setOf("z.b", "d.h", "u.a", "o.ä", "s.o", "s.u", "v.a", "u.u", "i.d.r")
    private const val MAX_LETTERS = 3

    /**
     * Whether the period right after `text[start, end)` (letters only) closes a known abbreviation.
     * Also true for a single letter after other single letters with period and space: a known one
     * ("z. B", "Z. B", "d. h", "U. a", "i. d. R") in every case, any other one unless both letters are
     * capitals, so initials such as "J. K. Rowling" keep their capitals (review N3, N4).
     */
    @JvmStatic
    fun isAbbreviation(text: CharSequence, start: Int, end: Int, german: Boolean, english: Boolean): Boolean {
        if (start < 0 || end <= start || end > text.length) return false
        val word = text.subSequence(start, end).toString().lowercase(Locale.ROOT)
        if (german && word in GERMAN) return true
        if (english && word in ENGLISH) return true
        if (end - start != 1) return false
        // the single letters before this one, each followed by ". ", nearest last
        val letters = ArrayList<Char>()
        letters.add(text[start])
        var p = start
        while (letters.size < MAX_LETTERS && p >= 3 && text[p - 1] == ' ' && text[p - 2] == '.' && text[p - 3].isLetter()
            && (p == 3 || !text[p - 4].isLetter())) {
            letters.add(0, text[p - 3])
            p -= 3
        }
        if (letters.size < 2) return false
        for (n in 2..letters.size) {
            val key = letters.takeLast(n).joinToString(".").lowercase(Locale.ROOT)
            if (key in LETTER_ABBREVIATIONS) return true
        }
        val previous = letters[letters.size - 2]
        return !(previous.isUpperCase() && letters.last().isUpperCase())
    }
}
