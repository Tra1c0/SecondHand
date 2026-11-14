// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * The small template language of `@snip` texts (stage 42). Pure and deterministic: the caller
 * passes the date, the time, the language, the clipboard text (or none) and the filled-in values.
 *
 * Placeholders (names without case):
 * - `{datum}`, `{datum+3t}`, `{datum-1t}`, `{datum+2w}`: the date, moved by days (`t`) or weeks
 *   (`w`), numeric as in [DateFormatting.numeric]; `{wochentag}` with the same offsets: the weekday.
 * - `{uhrzeit}`: the time of day.
 * - `{zwischenablage}`: the clipboard text, or nothing when the caller has none to give.
 * - `{?Name}`: a field the user fills in before the insert; the same name (without case) is asked once.
 * - `{cursor}`: where the cursor goes after the insert; only the first one counts.
 * - `{{` is a literal `{`.
 *
 * Anything else in braces stays as typed ([Issue.UNKNOWN]); a `{` without its `}` on the same line
 * stays as well ([Issue.UNCLOSED]). Nested braces are never placeholders: in `{a{datum}}` only
 * `{datum}` is one.
 */
class SnippetTemplate private constructor(val parts: List<Part>, val issues: List<Issue>) {
    sealed interface Part {
        data class Text(val text: String) : Part
        /** [weekday]: the weekday instead of the date; [days] the offset from today. */
        data class Date(val days: Int, val weekday: Boolean) : Part
        object Time : Part { override fun toString() = "Time" }
        object Clipboard : Part { override fun toString() = "Clipboard" }
        /** [name] as first written; [key] for comparing. */
        data class Field(val name: String) : Part { val key: String get() = fieldKey(name) }
        object Cursor : Part { override fun toString() = "Cursor" }
    }

    /** What the snippet editor warns about; [text] is the placeholder as typed. */
    data class Issue(val kind: Kind, val text: String) {
        enum class Kind { UNKNOWN, UNCLOSED, EXTRA_CURSOR }
    }

    /** The fields to ask, in order of first appearance, each once (as first written). */
    val fields: List<String> = parts.filterIsInstance<Part.Field>().distinctBy { it.key }.map { it.name }

    val usesClipboard: Boolean get() = parts.any { it is Part.Clipboard }

    /** True if nothing in the text is a placeholder: the expansion is the text itself (with `{{` as `{`). */
    val isPlain: Boolean get() = parts.all { it is Part.Text }

    /** What the caller knows at the moment of the insert. */
    class Values(
        val today: CivilDate,
        val time: ClockTime,
        val language: DateFormatting.Language,
        /** The clipboard text, or null when it may not or cannot be used: `{zwischenablage}` is then empty. */
        val clipboard: String? = null,
        /** Filled-in values by field name; names compare without case. A missing field is empty. */
        val fields: Map<String, String> = emptyMap(),
    ) {
        override fun toString() = "Values(${fields.size} fields)" // never the texts
    }

    /** The text to insert and where the cursor goes in it (an index into [text]), or null for its end. */
    class Expansion(val text: String, val cursor: Int?) {
        override fun toString() = "Expansion(${text.length} chars, cursor=$cursor)"
    }

    fun expand(values: Values): Expansion {
        val filled = values.fields.entries.associate { fieldKey(it.key) to it.value }
        val out = StringBuilder()
        var cursor: Int? = null
        for (part in parts) when (part) {
            is Part.Text -> out.append(part.text)
            is Part.Date -> {
                val date = shifted(values.today, part.days)
                out.append(when {
                    date == null -> ""
                    part.weekday -> values.language.weekdays[date.dayOfWeek - 1]
                    else -> DateFormatting.numeric(date, values.language)
                })
            }
            Part.Time -> out.append(DateFormatting.time(values.time, values.language))
            Part.Clipboard -> out.append(values.clipboard.orEmpty())
            is Part.Field -> out.append(filled[part.key].orEmpty())
            Part.Cursor -> if (cursor == null) cursor = out.length
        }
        return Expansion(out.toString(), cursor)
    }

    companion object {
        /** Longest text between braces that can be a placeholder. */
        const val MAX_PLACEHOLDER = 40
        /** Longest offset of a date in days or weeks (three digits). */
        const val MAX_OFFSET = 999
        const val MAX_FIELD_NAME = 24

        private val DATE = Regex("(datum|wochentag)(?:([+-])(\\d{1,3})([tw]))?")

        fun fieldKey(name: String) = name.trim().lowercase()

        /**
         * The formatting for the keyboard [language] (a code like "de" or "en_US"): English and
         * Spanish follow it, every other language (and none) gets German, the owner's default.
         */
        fun language(language: String): DateFormatting.Language =
            when (language.lowercase().substringBefore('_').substringBefore('-')) {
                "en" -> DateFormatting.Language.EN
                "es" -> DateFormatting.Language.ES
                else -> DateFormatting.Language.DE
            }

        /** A field name: 1–[MAX_FIELD_NAME] letters, digits, spaces, `-`, `_` or `.`; not only spaces. */
        fun isValidFieldName(name: String) = name.trim().length in 1..MAX_FIELD_NAME &&
            name.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '.' }

        private fun shifted(today: CivilDate, days: Int): CivilDate? {
            val day = today.epochDay + days
            // outside the years CivilDate knows (only near year 1 or 9999): nothing
            return runCatching { CivilDate.ofEpochDay(day) }.getOrNull()
        }

        /** [text] exactly as typed, braces included: snippets stored before stage 42. */
        fun literal(text: String) = SnippetTemplate(if (text.isEmpty()) emptyList() else listOf(Part.Text(text)), emptyList())

        fun parse(text: String): SnippetTemplate {
            val parts = mutableListOf<Part>()
            val issues = mutableListOf<Issue>()
            val literal = StringBuilder()
            var cursors = 0
            fun flush() { if (literal.isNotEmpty()) { parts += Part.Text(literal.toString()); literal.clear() } }
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c != '{') { literal.append(c); i++; continue }
                if (i + 1 < text.length && text[i + 1] == '{') { literal.append('{'); i += 2; continue }
                // the body ends at the first '}'; another '{' or a line break first means no placeholder here
                var end = i + 1
                while (end < text.length && end - i - 1 <= MAX_PLACEHOLDER && text[end] != '}' && text[end] != '{'
                    && text[end] != '\n' && text[end] != '\r') end++
                if (end >= text.length || text[end] != '}' || end - i - 1 > MAX_PLACEHOLDER) {
                    issues += Issue(Issue.Kind.UNCLOSED, text.substring(i, minOf(end, text.length)))
                    literal.append('{')
                    i++
                    continue
                }
                val raw = text.substring(i, end + 1)
                val part = placeholder(text.substring(i + 1, end))
                when {
                    part == null -> { issues += Issue(Issue.Kind.UNKNOWN, raw); literal.append(raw) }
                    part == Part.Cursor && cursors++ > 0 -> { issues += Issue(Issue.Kind.EXTRA_CURSOR, raw) }
                    else -> { flush(); parts += part }
                }
                i = end + 1
            }
            flush()
            return SnippetTemplate(parts, issues)
        }

        private fun placeholder(body: String): Part? {
            if (body.startsWith("?")) {
                val name = body.substring(1)
                return if (isValidFieldName(name)) Part.Field(name.trim()) else null
            }
            val lower = body.lowercase()
            when (lower) {
                "uhrzeit" -> return Part.Time
                "zwischenablage" -> return Part.Clipboard
                "cursor" -> return Part.Cursor
            }
            val match = DATE.matchEntire(lower) ?: return null
            val (kind, sign, amount, unit) = match.destructured
            val count = if (amount.isEmpty()) 0 else amount.toInt() * (if (unit == "w") 7 else 1)
            return Part.Date(if (sign == "-") -count else count, weekday = kind == "wochentag")
        }
    }
}

/** Stage 42: the apps a snippet may be offered in, by package name; none means every app. */
object SnippetApps {
    const val MAX_APPS = 20
    const val MAX_PACKAGE = 120
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    /** A Java-style package name with at least two parts, e.g. `com.whatsapp`. */
    fun isValidPackage(name: String) = name.length <= MAX_PACKAGE && PACKAGE.matches(name)

    /** True if a snippet restricted to [apps] may be offered in the app [packageName] (unknown: only without restriction). */
    fun allows(apps: List<String>, packageName: String?) = apps.isEmpty() || (packageName != null && packageName in apps)
}
