// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.charart

/**
 * How many columns text takes in a chat (stage 53), as an estimate in units of one narrow letter:
 * emojis and wide East Asian characters 2, combining marks and joiners 0, everything else 1.
 * Chats use proportional fonts, so this only says what lines up in a monospace code block and
 * roughly how wide a line is elsewhere.
 */
object TextColumns {
    const val BRAILLE_BLANK = '\u2800'

    fun of(text: String): Int = graphemes(text).sumOf { width(it) }

    /**
     * The user-perceived characters of [text]: a letter with its marks, an emoji with its modifiers,
     * joined emoji sequences and flags. Own rules instead of [java.text.BreakIterator], which on older
     * JVMs splits skin tones and joined emojis; the same on every device.
     */
    fun graphemes(text: String): List<String> {
        val out = mutableListOf<String>()
        var current = StringBuilder()
        var joinNext = false
        var openFlag = false
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            val regional = codePoint in 0x1F1E6..0x1F1FF
            val extends = isMark(codePoint) || codePoint in 0x1F3FB..0x1F3FF || codePoint == 0x20E3 ||
                codePoint in 0xE0020..0xE007F || joinNext || regional && openFlag
            if (!extends && current.isNotEmpty()) { out += current.toString(); current = StringBuilder() }
            openFlag = if (regional) !(openFlag && extends) else if (extends) openFlag else false
            current.appendCodePoint(codePoint)
            joinNext = codePoint == 0x200D
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    /** Columns of one grapheme. */
    fun width(grapheme: String): Int {
        if (grapheme.isEmpty()) return 0
        val first = grapheme.codePointAt(0)
        if (isMark(first)) return 0
        if (isEmoji(grapheme)) return 2
        return if (isWide(first)) 2 else 1
    }

    fun isEmoji(grapheme: String): Boolean {
        val first = grapheme.codePointAt(0)
        if (grapheme.contains('️')) return true
        return first in 0x1F300..0x1FAFF || first in 0x1F000..0x1F0FF || first in 0x1F1E6..0x1F1FF ||
            first in 0x1F170..0x1F251 && first in EMOJI_ENCLOSED || first in EMOJI_BMP
    }

    /** Letters of the mathematical alphabets, circled and squared letters: text styles, not emojis. */
    fun isStyledLetter(codePoint: Int): Boolean =
        codePoint in 0x1D400..0x1D7FF || codePoint in 0x2100..0x214F || codePoint in 0x2460..0x24FF ||
            codePoint in 0x1F130..0x1F189 || codePoint in SMALL_CAPS

    /** Braille, block and box drawing characters: the material of art. */
    fun isArt(codePoint: Int): Boolean = codePoint in 0x2800..0x28FF || codePoint in 0x2580..0x259F || codePoint in 0x2500..0x257F

    private fun isMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() || codePoint == 0x200D || codePoint in 0xFE00..0xFE0F
    }

    private fun isWide(codePoint: Int): Boolean =
        codePoint in 0x1100..0x115F || codePoint in 0x2E80..0xA4CF || codePoint in 0xAC00..0xD7A3 ||
            codePoint in 0xF900..0xFAFF || codePoint in 0xFE30..0xFE4F || codePoint in 0xFF00..0xFF60 || codePoint in 0xFFE0..0xFFE6

    /** Characters below U+1F300 that phones draw as emojis without a variation selector. */
    private val EMOJI_BMP = setOf(0x231A, 0x231B, 0x23E9, 0x23EA, 0x23EB, 0x23EC, 0x23F0, 0x23F3, 0x25FD, 0x25FE,
        0x2614, 0x2615, 0x2648, 0x2649, 0x264A, 0x264B, 0x264C, 0x264D, 0x264E, 0x264F, 0x2650, 0x2651, 0x2652, 0x2653,
        0x267F, 0x2693, 0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE, 0x26C4, 0x26C5, 0x26CE, 0x26D4, 0x26EA, 0x26F2, 0x26F3,
        0x26F5, 0x26FA, 0x26FD, 0x2705, 0x270A, 0x270B, 0x2728, 0x274C, 0x274E, 0x2753, 0x2754, 0x2755, 0x2757, 0x2795,
        0x2796, 0x2797, 0x27B0, 0x27BF, 0x2B1B, 0x2B1C, 0x2B50, 0x2B55)

    private val EMOJI_ENCLOSED = setOf(0x1F170, 0x1F171, 0x1F17E, 0x1F17F, 0x1F18E) + (0x1F191..0x1F19A) + (0x1F1E6..0x1F1FF) +
        setOf(0x1F201, 0x1F202, 0x1F21A, 0x1F22F) + (0x1F232..0x1F23A) + setOf(0x1F250, 0x1F251)

    private val SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ".map { it.code }.toSet()
}
