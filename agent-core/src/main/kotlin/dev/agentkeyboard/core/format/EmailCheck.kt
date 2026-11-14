// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.format

import java.util.Locale

/**
 * E-mail hints (stage 41): spaces, a typo in the domain of a common provider, a missing `@`
 * before such a domain. Only a small local list of providers; every other domain is left alone.
 */
object EmailCheck {
    /** Common providers (international, German-speaking, Spanish). Order decides between equally close ones. */
    val PROVIDERS = listOf(
        "gmail.com", "googlemail.com", "web.de", "gmx.de", "gmx.net", "gmx.at", "gmx.ch", "gmx.com",
        "t-online.de", "posteo.de", "outlook.com", "outlook.de", "outlook.es", "hotmail.com", "hotmail.de",
        "hotmail.es", "live.com", "live.de", "msn.com", "yahoo.com", "yahoo.de", "yahoo.es", "icloud.com",
        "me.com", "mac.com", "mailbox.org", "freenet.de", "aol.com", "arcor.de", "protonmail.com", "proton.me",
        "telefonica.net",
    )

    /** At most this many domain suggestions (equally close providers). */
    const val MAX_SUGGESTIONS = 2

    /** Allowed edits: one for short provider names (up to 7 characters, e.g. `web.de`), else two. */
    fun maxEdits(provider: String) = if (provider.length <= 7) 1 else 2

    fun check(text: String): List<FormatHint> {
        if (text.any { it.isWhitespace() }) {
            val joined = text.filterNot { it.isWhitespace() }
            return if (joined.isEmpty() || joined == text) emptyList() else listOf(FormatHint.EmailSpaces(joined))
        }
        val at = text.indexOf('@')
        if (at < 0) return listOfNotNull(missingAt(text))
        // several @ or nothing before or after it: nothing reliable to say
        if (at == 0 || at != text.lastIndexOf('@') || at == text.length - 1) return emptyList()
        val local = text.substring(0, at)
        return domains(text.substring(at + 1)).map { FormatHint.EmailDomain(it, "$local@$it") }
    }

    /** The providers the typed [domain] most likely means; empty for a provider, an unknown domain or one still being typed. */
    fun domains(domain: String): List<String> {
        val lower = domain.lowercase(Locale.ROOT)
        val trimmed = lower.trimEnd('.')
        // a trailing dot after a known provider: the provider without it
        if (trimmed != lower && trimmed in PROVIDERS) return listOf(trimmed)
        if (lower in PROVIDERS) return emptyList()
        // still typing ("gmail.co" on the way to "gmail.com"), or no dot yet: no hint
        if ('.' !in trimmed || PROVIDERS.any { it.startsWith(trimmed) }) return emptyList()
        var best = Int.MAX_VALUE
        val found = mutableListOf<String>()
        for (provider in PROVIDERS) {
            val d = distance(trimmed, provider)
            if (d > maxEdits(provider) || d > best) continue
            if (d < best) { best = d; found.clear() }
            found += provider
        }
        return found.take(MAX_SUGGESTIONS)
    }

    /** "maxgmail.com" or "max.gmail.com" → "max@gmail.com"; only for a provider of the list. */
    private fun missingAt(text: String): FormatHint? {
        val lower = text.lowercase(Locale.ROOT)
        // the longest provider wins: "max.hotmail.de" is hotmail.de, not a ".de" of something else
        val provider = PROVIDERS.filter { lower.endsWith(it) }.maxByOrNull { it.length } ?: return null
        val before = text.substring(0, text.length - provider.length)
        // glued to the name only for names of three letters or more: "name.com" is no "na" at me.com
        if (!before.endsWith(".") && provider.substringBefore('.').length < 3) return null
        val local = before.removeSuffix(".")
        if (local.isEmpty() || !local.last().isLetterOrDigit()) return null
        return FormatHint.EmailMissingAt(provider, "$local@$provider")
    }

    /** Optimal string alignment distance (adjacent swaps count once). */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
            d[i][j] = v
        }
        return d[a.length][b.length]
    }
}
