// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Commands typed into the field, like `@math` or `@contact Anna`. This file only recognizes them
 * in the text before the cursor; nothing here edits text or runs anything.
 *
 * Rules: the prefix starts the text or follows whitespace or an opening bracket, so that mail
 * addresses (`a@b.de`) never count. The name consists of ASCII letters and is compared without
 * case. A known name may be followed by one space and an argument on the same line.
 */
object CommandSyntax {
    /**
     * Prefixes that are no word separators in the keyboard: the command stays one word while it is
     * typed, so the autocorrection sees and protects it. `/`, `!` or `#`-like separators would not work.
     */
    val ALLOWED_PREFIXES: List<Char> = listOf('@', '#', '$', '%', '~', '§')
    const val DEFAULT_PREFIX: Char = '@'
    const val MAX_NAME = 16
    const val MAX_ARGUMENT = 200
    /** Characters the caller must read before the cursor; one more tells whether the field starts there. */
    const val LOOKBACK = 1 + MAX_NAME + 1 + MAX_ARGUMENT

    private val OPENERS = setOf('(', '[', '{', '"', '\'', '„', '“', '«', '¿', '¡')

    fun isAllowedPrefix(prefix: Char) = prefix in ALLOWED_PREFIXES

    /**
     * What is typed before the cursor, or null. [before] is the text before the cursor, up to
     * [LOOKBACK] + 1 characters; [names] are the enabled command names in lower case.
     */
    fun parse(before: CharSequence, prefix: Char, names: Collection<String>): CommandContext? {
        if (!isAllowedPrefix(prefix) || names.isEmpty()) return null
        // a window cut by the caller does not tell what precedes its first character
        val fieldStartsInWindow = before.length <= LOOKBACK
        var index = before.length - 1
        val lowest = maxOf(0, before.length - LOOKBACK)
        while (index >= lowest) {
            if (before[index] == '\n') return null // commands never span lines
            if (before[index] == prefix) {
                val atBoundary = if (index == 0) fieldStartsInWindow else before[index - 1].isWhitespace() || before[index - 1] in OPENERS
                if (atBoundary) parseAt(before, index, prefix, names)?.let { return it }
            }
            index--
        }
        return null
    }

    private fun parseAt(before: CharSequence, start: Int, prefix: Char, names: Collection<String>): CommandContext? {
        var end = start + 1
        while (end < before.length && end - start - 1 < MAX_NAME && before[end].isAsciiLetter()) end++
        val typed = before.substring(start + 1, end).lowercase()
        val text = before.substring(start)
        if (end == before.length) {
            val candidates = names.filter { it.startsWith(typed) }.sorted()
            if (typed in names) return CommandCall(start, text, prefix, typed, "")
            return if (candidates.isEmpty()) null else CommandNames(start, text, prefix, typed, candidates)
        }
        if (typed !in names || before[end] != ' ') return null
        val argument = before.substring(end + 1)
        if (argument.length > MAX_ARGUMENT || argument.any { it == '\n' || it == '\r' }) return null
        return CommandCall(start, text, prefix, typed, argument)
    }

    /** True for a word that is a command name with its prefix, e.g. `@math`; used to keep it from learning and correction. */
    fun isCommandWord(word: CharSequence, prefix: Char, names: Collection<String>): Boolean {
        if (word.length < 2 || word[0] != prefix || !isAllowedPrefix(prefix)) return false
        val name = word.substring(1).lowercase()
        return name in names
    }

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
}

/** A recognized command before the cursor. [text] is exactly what was typed, from the prefix to the cursor. */
sealed interface CommandContext {
    /** Index of the prefix in the text that was parsed. */
    val start: Int
    val text: String
    val prefix: Char
}

/** A prefix with a name that is not complete yet: offer the [candidates]. An empty name offers all. */
data class CommandNames(
    override val start: Int,
    override val text: String,
    override val prefix: Char,
    val typed: String,
    val candidates: List<String>,
) : CommandContext

/** A complete name, maybe with an argument (empty when none was typed). */
data class CommandCall(
    override val start: Int,
    override val text: String,
    override val prefix: Char,
    val name: String,
    val argument: String,
) : CommandContext
