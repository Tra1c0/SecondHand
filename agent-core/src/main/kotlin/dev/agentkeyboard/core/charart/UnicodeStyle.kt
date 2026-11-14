// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.charart

import java.text.Normalizer
import java.util.Locale

/**
 * Text styles made of Unicode letters (stage 53, takes over stage 39): a [family] with [bold] and
 * [italic], plus lines through or under every character ([strike], [underline]). All combine;
 * where Unicode has no letters for a combination (double-struck bold, circled italic …) the closest
 * existing one is used and [Styled.approximated] says so. Letters with accents keep them as
 * combining marks (ä → 𝐚̈), ß and anything without a styled form stay as they are.
 */
data class UnicodeStyle(
    val family: Family = Family.SERIF,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
) {
    enum class Family { SERIF, SANS, SCRIPT, FRAKTUR, DOUBLE_STRUCK, MONOSPACE, CIRCLED, SQUARED, SMALL_CAPS, UPSIDE_DOWN }

    /** [text] in this style; [approximated] if a requested part of the style does not exist in Unicode. */
    class Styled(val text: String, val approximated: Boolean, val emojiLetters: Boolean)

    val isPlain: Boolean get() = family == Family.SERIF && !bold && !italic && !strike && !underline

    fun apply(text: String): Styled {
        val (alphabet, approximated) = resolve()
        val emojiLetters = alphabet == Alphabet.NEGATIVE_SQUARED && text.uppercase(Locale.ROOT).any { it in EMOJI_SQUARED }
        val source = if (family == Family.UPSIDE_DOWN) upsideDown(text) else text
        val out = StringBuilder()
        for (line in source.split('\n').withIndex()) {
            if (line.index > 0) out.append('\n')
            val decomposed = Normalizer.normalize(line.value, Normalizer.Form.NFD)
            var index = 0
            while (index < decomposed.length) {
                val codePoint = decomposed.codePointAt(index)
                index += Character.charCount(codePoint)
                if (Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()) { out.appendCodePoint(codePoint); continue }
                out.append(alphabet.map(codePoint))
                // marks after the letter and its own accents; none after a line's end
                if (strike) out.append('̶')
                if (underline) out.append('̲')
            }
        }
        val composed = Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
        return Styled(composed, approximated, emojiLetters)
    }

    /** The Unicode alphabet that comes closest, and whether it lacks part of the request. */
    private fun resolve(): Pair<Alphabet, Boolean> = when (family) {
        Family.SERIF -> when {
            bold && italic -> Alphabet.BOLD_ITALIC
            bold -> Alphabet.BOLD
            italic -> Alphabet.ITALIC
            else -> Alphabet.PLAIN
        } to false
        Family.SANS -> when {
            bold && italic -> Alphabet.SANS_BOLD_ITALIC
            bold -> Alphabet.SANS_BOLD
            italic -> Alphabet.SANS_ITALIC
            else -> Alphabet.SANS
        } to false
        // script letters lean already: italic changes nothing and is no loss
        Family.SCRIPT -> (if (bold) Alphabet.BOLD_SCRIPT else Alphabet.SCRIPT) to false
        Family.FRAKTUR -> (if (bold) Alphabet.BOLD_FRAKTUR else Alphabet.FRAKTUR) to italic
        Family.DOUBLE_STRUCK -> Alphabet.DOUBLE_STRUCK to (bold || italic)
        Family.MONOSPACE -> Alphabet.MONOSPACE to (bold || italic)
        Family.CIRCLED -> (if (bold) Alphabet.NEGATIVE_CIRCLED else Alphabet.CIRCLED) to italic
        Family.SQUARED -> (if (bold) Alphabet.NEGATIVE_SQUARED else Alphabet.SQUARED) to italic
        Family.SMALL_CAPS -> Alphabet.SMALL_CAPS to (bold || italic)
        Family.UPSIDE_DOWN -> Alphabet.PLAIN to (bold || italic)
    }

    private enum class Alphabet(
        private val capital: Int = 0, private val small: Int = 0, private val digit: Int = 0,
        private val exceptions: Map<Char, Int> = emptyMap(), private val capitalsOnly: Boolean = false,
    ) {
        PLAIN,
        BOLD(0x1D400, 0x1D41A, 0x1D7CE),
        ITALIC(0x1D434, 0x1D44E, exceptions = mapOf('h' to 0x210E)),
        BOLD_ITALIC(0x1D468, 0x1D482),
        SCRIPT(0x1D49C, 0x1D4B6, exceptions = mapOf('B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131, 'H' to 0x210B,
            'I' to 0x2110, 'L' to 0x2112, 'M' to 0x2133, 'R' to 0x211B, 'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134)),
        BOLD_SCRIPT(0x1D4D0, 0x1D4EA),
        FRAKTUR(0x1D504, 0x1D51E, exceptions = mapOf('C' to 0x212D, 'H' to 0x210C, 'I' to 0x2111, 'R' to 0x211C, 'Z' to 0x2128)),
        BOLD_FRAKTUR(0x1D56C, 0x1D586),
        DOUBLE_STRUCK(0x1D538, 0x1D552, 0x1D7D8, mapOf('C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119,
            'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124)),
        SANS(0x1D5A0, 0x1D5BA, 0x1D7E2),
        SANS_BOLD(0x1D5D4, 0x1D5EE, 0x1D7EC),
        SANS_ITALIC(0x1D608, 0x1D622),
        SANS_BOLD_ITALIC(0x1D63C, 0x1D656),
        MONOSPACE(0x1D670, 0x1D68A, 0x1D7F6),
        CIRCLED(0x24B6, 0x24D0),
        NEGATIVE_CIRCLED(0x1F150, capitalsOnly = true),
        SQUARED(0x1F130, capitalsOnly = true),
        NEGATIVE_SQUARED(0x1F170, capitalsOnly = true),
        SMALL_CAPS;

        fun map(codePoint: Int): String {
            if (this == PLAIN || codePoint > 0xFFFF) return String(Character.toChars(codePoint))
            val char = codePoint.toChar()
            if (this == SMALL_CAPS) return SMALL_CAPS_LETTERS[char]?.toString() ?: char.toString()
            exceptions[char]?.let { return String(Character.toChars(it)) }
            val letter = if (capitalsOnly && char in 'a'..'z') char.uppercaseChar() else char
            val mapped = when (letter) {
                in 'A'..'Z' -> if (capital != 0) capital + (letter - 'A') else 0
                in 'a'..'z' -> if (small != 0) small + (letter - 'a') else 0
                in '0'..'9' -> when {
                    digit != 0 -> digit + (letter - '0')
                    this == CIRCLED -> if (letter == '0') 0x24EA else 0x2460 + (letter - '1')
                    else -> 0
                }
                else -> 0
            }
            return if (mapped == 0) letter.toString() else String(Character.toChars(mapped))
        }
    }

    companion object {
        /** Negative squared letters that most phones show as colourful emojis (🅰 🅱 🅾 🅿). */
        private val EMOJI_SQUARED = setOf('A', 'B', 'O', 'P')

        private val SMALL_CAPS_LETTERS: Map<Char, Char> = "abcdefghijklmnopqrstuvwxyz"
            .zip("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ").toMap()

        private val UPSIDE_DOWN: Map<Char, String> = buildMap {
            "abcdefghijklmnopqrstuvwxyz".toList().zip(listOf("ɐ", "q", "ɔ", "p", "ǝ", "ɟ", "ƃ", "ɥ", "ᴉ", "ɾ", "ʞ", "l", "ɯ",
                "u", "o", "d", "b", "ɹ", "s", "ʇ", "n", "ʌ", "ʍ", "x", "ʎ", "z")).forEach { (a, b) -> put(a, b) }
            "ACEFGJLMPTUVWY".toList().zip(listOf("∀", "Ɔ", "Ǝ", "Ⅎ", "⅁", "ſ", "˥", "W", "Ԁ", "⊥", "∩", "Λ", "M", "⅄"))
                .forEach { (a, b) -> put(a, b) }
            "1234567".toList().zip(listOf("Ɩ", "ᄅ", "Ɛ", "ㄣ", "ϛ", "9", "ㄥ")).forEach { (a, b) -> put(a, b) }
            put('9', "6"); put('.', "˙"); put(',', "'"); put('\'', ","); put('?', "¿"); put('!', "¡")
            put('(', ")"); put(')', "("); put('[', "]"); put(']', "["); put('{', "}"); put('}', "{")
            put('<', ">"); put('>', "<"); put('_', "‾"); put('&', "⅋")
        }

        /** Each line turned by 180°: characters reversed and flipped where Unicode has a flipped form. */
        fun upsideDown(text: String): String = text.split('\n').joinToString("\n") { line ->
            val plain = Normalizer.normalize(line, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            plain.reversed().map { UPSIDE_DOWN[it] ?: it.toString() }.joinToString("")
        }
    }
}
