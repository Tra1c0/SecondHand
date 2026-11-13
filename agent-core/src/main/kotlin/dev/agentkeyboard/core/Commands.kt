// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Commands typed into the field, like `@math` or `@contact Anna`. This file only recognizes them
 * in the text before the cursor; nothing here edits text or runs anything.
 *
 * Rules: the prefix starts the text or follows whitespace or an opening bracket, so that mail
 * addresses (`a@b.de`) never count. The name consists of ASCII letters and is compared without
 * case. A known name may be followed by one space and an argument on the same line.
 *
 * Stage 24g, both opt-in: with `fuzzyNames` an unknown name that is a typo of a known one
 * ([closeNames], `@mtah`) offers that name to complete, never runs it. With `claim` a command may
 * take an unknown word as its own (`@sp` for the language command); such a word may contain any
 * letters (`@español`) and takes no argument. Order: exact name, started name, name with a typo,
 * claim. A typo or a claim inside the argument of an earlier command typed by its exact name on
 * the same line never takes over from it (`@ki frag @matt`).
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
    /** The argument limit of the few commands that name themselves in `longArguments` (`@translate`). */
    const val MAX_LONG_ARGUMENT = 500
    /** Characters the caller must read before the cursor; one more tells whether the field starts there. */
    const val LOOKBACK = 1 + MAX_NAME + 1 + MAX_LONG_ARGUMENT

    private val OPENERS = setOf('(', '[', '{', '"', '\'', '„', '“', '«', '¿', '¡')

    fun isAllowedPrefix(prefix: Char) = prefix in ALLOWED_PREFIXES

    /**
     * What is typed before the cursor, or null. [before] is the text before the cursor, up to
     * [LOOKBACK] + 1 characters; [names] are the enabled command names in lower case. Commands in
     * [longArguments] take up to [MAX_LONG_ARGUMENT] characters, all others [MAX_ARGUMENT].
     */
    fun parse(before: CharSequence, prefix: Char, names: Collection<String>,
              longArguments: Collection<String> = emptySet(), fuzzyNames: Boolean = false,
              claim: ((String) -> String?)? = null): CommandContext? {
        if (!isAllowedPrefix(prefix) || names.isEmpty()) return null
        // a window cut by the caller does not tell what precedes its first character
        val fieldStartsInWindow = before.length <= LOOKBACK
        var index = before.length - 1
        val lowest = maxOf(0, before.length - LOOKBACK)
        // stage 24g after review: a claimed word or a typo of a name only counts when no command
        // typed by its exact name is running earlier on the line (`@ki frag @matt`, `@translate … @es`)
        var weak: CommandContext? = null
        while (index >= lowest) {
            if (before[index] == '\n') return weak // commands never span lines
            if (before[index] == prefix) {
                val atBoundary = if (index == 0) fieldStartsInWindow else before[index - 1].isWhitespace() || before[index - 1] in OPENERS
                val found = if (atBoundary) parseAt(before, index, prefix, names, longArguments, fuzzyNames, claim) else null
                when {
                    found == null -> {}
                    found.isWeak() -> if (weak == null) weak = found
                    // the first exact command wins; behind a weak one only an exact call can take over
                    weak == null || found is CommandCall -> return found
                }
            }
            index--
        }
        return weak
    }

    private fun CommandContext.isWeak() = (this is CommandNames && fuzzy) || (this is CommandCall && claimed != null)

    private fun parseAt(before: CharSequence, start: Int, prefix: Char, names: Collection<String>,
                        longArguments: Collection<String>, fuzzyNames: Boolean,
                        claim: ((String) -> String?)?): CommandContext? {
        var end = start + 1
        while (end < before.length && end - start - 1 < MAX_NAME && before[end].isAsciiLetter()) end++
        // only a claim can take a word with other letters (`@español`)
        if (claim != null) while (end < before.length && end - start - 1 < MAX_NAME && before[end].isLetter()) end++
        val typed = before.substring(start + 1, end).lowercase()
        val text = before.substring(start)
        if (end == before.length) {
            if (typed in names) return CommandCall(start, text, prefix, typed, "")
            val candidates = names.filter { it.startsWith(typed) }.sorted()
            if (candidates.isNotEmpty()) return CommandNames(start, text, prefix, typed, candidates)
            val close = if (fuzzyNames) closeNames(typed, names) else emptyList()
            if (close.isNotEmpty()) return CommandNames(start, text, prefix, typed, close, fuzzy = true)
            return claimed(typed, names, claim)?.let { CommandCall(start, text, prefix, it, "", claimed = typed) }
        }
        if (before[end] != ' ') return null
        val name = if (typed in names) typed else {
            // a typo of a command name is never claimed by another command
            if (fuzzyNames && closeNames(typed, names).isNotEmpty()) return null
            claimed(typed, names, claim) ?: return null
        }
        val argument = before.substring(end + 1)
        // a claimed word takes no argument: after "@sp hola" the line is text again (autocorrect on)
        if (name != typed && argument.isNotEmpty()) return null
        val limit = if (name in longArguments) MAX_LONG_ARGUMENT else MAX_ARGUMENT
        if (argument.length > limit || argument.any { it == '\n' || it == '\r' }) return null
        return CommandCall(start, text, prefix, name, argument, claimed = typed.takeIf { name != typed })
    }

    private fun claimed(typed: String, names: Collection<String>, claim: ((String) -> String?)?): String? {
        if (claim == null || typed.isEmpty()) return null
        return claim(typed)?.takeIf { it in names }
    }

    /**
     * Known names that [typed] misses by a typo ([LanguageNames.isTypo]: both at least four letters,
     * same first letter, up to four letters only a swap), closest first; e.g. `mtah` → `math`,
     * `tranlate` → `translate`, but not `matt`, `uri` or `lana`.
     */
    fun closeNames(typed: String, names: Collection<String>): List<String> {
        if (typed.any { !it.isAsciiLetter() }) return emptyList()
        return names.filter { LanguageNames.isTypo(typed, it) }
            .sortedWith(compareBy({ LanguageNames.distance(typed, it) }, { it }))
    }

    /** True for a word that is a command name with its prefix, e.g. `@math`; used to keep it from learning and correction. */
    fun isCommandWord(word: CharSequence, prefix: Char, names: Collection<String>,
                      claim: ((String) -> String?)? = null): Boolean {
        if (word.length < 2 || word[0] != prefix || !isAllowedPrefix(prefix)) return false
        val name = word.substring(1).lowercase()
        if (name in names) return true
        // stage 24g: a word a command claims (`@sp`) is no word of the user's either
        return name.length <= MAX_NAME && name.all { it.isLetter() } && claimed(name, names, claim) != null
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

/**
 * A prefix with a name that is not complete yet: offer the [candidates]. An empty name offers all.
 * [fuzzy]: the typed name is no start of them but a typo of them (stage 24g).
 */
data class CommandNames(
    override val start: Int,
    override val text: String,
    override val prefix: Char,
    val typed: String,
    val candidates: List<String>,
    val fuzzy: Boolean = false,
) : CommandContext

/**
 * A complete name, maybe with an argument (empty when none was typed). [claimed] is the typed word
 * when the command [name] took a word that is not its name (stage 24g: `@sp` for `lang`), else null.
 */
data class CommandCall(
    override val start: Int,
    override val text: String,
    override val prefix: Char,
    val name: String,
    val argument: String,
    val claimed: String? = null,
) : CommandContext
