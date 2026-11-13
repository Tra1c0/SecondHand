// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * `@translate "text" style` (stage 24f): the argument of the command split into the text to
 * translate (inside the first pair of quotes) and the style the user asks for (everything else,
 * e.g. "natürliches slang spanisch"). [request] is exactly what is sent and shown before sending.
 */
object StyledTranslation {
    /** Used when nothing but the quoted text was typed. */
    const val DEFAULT_STYLE = "natural English"
    const val STYLE_LABEL = "Translation style"
    const val TEXT_LABEL = "Text to translate"

    data class Parsed(val text: String, val style: String, val defaultStyle: Boolean)

    /** Opening quotes and the characters that may close them. */
    private val CLOSERS: Map<Char, String> = mapOf(
        '"' to "\"",
        '„' to "“”\"",
        '“' to "”“\"",
        '”' to "”\"",
        '«' to "»",
        '»' to "«",
        '\'' to "'’",
        '‚' to "‘’'",
    )
    /** Quotes that are also apostrophes ("don't", "it's"): they only count at a word boundary. */
    private const val APOSTROPHES = "'’‘‚"

    /**
     * The text from the first opening quote to the LAST matching closing quote, and the style around
     * it, or null without a closed, non-blank quote. Taking the last closer keeps quotes inside the
     * text ("abgesehen davon funktioniert "@translate" super"); the style after it therefore must not
     * use the same kind of quote. A quote that is also an apostrophe opens only after a space or at
     * the start, and closes only before a space, punctuation or the end.
     */
    fun parse(argument: String): Parsed? {
        if (argument.any { it == '\n' || it == '\r' }) return null
        var open = -1
        for (i in argument.indices) {
            val c = argument[i]
            if (c !in CLOSERS) continue
            if (c in APOSTROPHES && i > 0 && !argument[i - 1].isWhitespace()) continue
            open = i
            break
        }
        if (open < 0) return null
        val closers = CLOSERS.getValue(argument[open])
        var close = -1
        for (j in argument.length - 1 downTo open + 1) {
            val c = argument[j]
            if (c !in closers) continue
            if (c in APOSTROPHES && j + 1 < argument.length && argument[j + 1].isLetterOrDigit()) continue
            close = j
            break
        }
        if (close < 0) return null
        val text = argument.substring(open + 1, close).trim()
        if (text.isEmpty()) return null
        val style = listOf(argument.substring(0, open).trim(), argument.substring(close + 1).trim())
            .filter { it.isNotEmpty() }.joinToString(" ")
        return if (style.isEmpty()) Parsed(text, DEFAULT_STYLE, true) else Parsed(text, style, false)
    }

    /** The user message of the request: the style and the text in two labelled sections, nothing else. */
    fun request(parsed: Parsed): String = "$STYLE_LABEL:\n${parsed.style}\n\n$TEXT_LABEL:\n${parsed.text}"
}
