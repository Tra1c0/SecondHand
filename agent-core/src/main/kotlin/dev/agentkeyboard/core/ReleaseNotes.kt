// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Release notes for the update dialogs (stage 54): the body of a GitHub release or the notes
 * embedded in the APK, both Markdown written by the lead (`release-notes/next.md`). Pure: no
 * links are opened, nothing is fetched; the text is only bounded, cleaned and split into simple blocks.
 */
object ReleaseNotes {
    /** At most this many UTF-8 bytes of a release body are stored (the API answer itself may be 2 MiB). */
    const val MAX_STORED_BYTES = 8 * 1024

    class Capped(val text: String, val truncated: Boolean)

    sealed class Block {
        abstract val text: String
        /** `#` to `######`; [level] 1 is the title of the file. */
        data class Heading(override val text: String, val level: Int) : Block()
        /** One list item (`-`, `*`, `+` or `1.`), with its indented continuation lines joined. */
        data class Bullet(override val text: String) : Block()
        data class Paragraph(override val text: String) : Block()
    }

    private val HEADING = Regex("""#{1,6}[ \t]+(.*)""")
    private val BULLET = Regex("""[-*+][ \t]+(.*)""")
    private val NUMBERED = Regex("""(\d{1,3}[.)])[ \t]+(.*)""")
    private val LINK = Regex("""\[([^\]\n]*)]\([^)\n]*\)""")

    /** Unix line breaks, no control characters except line break and tab, trimmed. */
    fun clean(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
        .filter { it == '\n' || it == '\t' || !it.isISOControl() }
        .trim()

    /**
     * [clean]ed and at most [maxBytes] UTF-8 bytes: cut at the last line break in the second half of
     * the allowed part, otherwise at a code point, never inside a surrogate pair.
     */
    fun cap(body: String, maxBytes: Int = MAX_STORED_BYTES): Capped {
        val text = clean(body)
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return Capped(text, false)
        var bytes = 0
        var end = 0
        while (end < text.length) {
            val codePoint = text.codePointAt(end)
            val size = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (bytes + size > maxBytes) break
            bytes += size
            end += Character.charCount(codePoint)
        }
        val part = text.substring(0, end)
        val line = part.lastIndexOf('\n')
        return Capped((if (line > end / 2) part.substring(0, line) else part).trimEnd(), true)
    }

    /** Simple Markdown: headings, list items and paragraphs; `**`, `__` and backticks dropped, links as their text. */
    fun blocks(markdown: String): List<Block> {
        val result = mutableListOf<Block>()
        var open: StringBuilder? = null
        var bullet = false
        fun flush() {
            val text = open?.toString()?.let(::inline)?.trim().orEmpty()
            if (text.isNotEmpty()) result += if (bullet) Block.Bullet(text) else Block.Paragraph(text)
            open = null
            bullet = false
        }
        for (raw in clean(markdown).lines()) {
            val line = raw.trim()
            if (line.isEmpty()) { flush(); continue }
            val heading = HEADING.matchEntire(line)
            val item = BULLET.matchEntire(line)
            val numbered = NUMBERED.matchEntire(line)
            when {
                heading != null -> {
                    flush()
                    val text = inline(heading.groupValues[1]).trim().trimEnd('#').trim()
                    if (text.isNotEmpty()) result += Block.Heading(text, line.takeWhile { it == '#' }.length)
                }
                item != null || numbered != null -> {
                    flush()
                    bullet = true
                    open = StringBuilder(item?.groupValues?.get(1) ?: "${numbered!!.groupValues[1]} ${numbered.groupValues[2]}")
                }
                open != null -> open!!.append(' ').append(line)
                else -> open = StringBuilder(line)
            }
        }
        flush()
        return result
    }

    private fun inline(text: String): String =
        LINK.replace(text) { it.groupValues[1] }.replace("**", "").replace("__", "").replace("`", "")
}
