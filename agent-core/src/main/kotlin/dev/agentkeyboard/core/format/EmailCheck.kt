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

    /**
     * Real domains that look like a typo of a provider (review of stage 41): other countries of a
     * provider, and other services one or two letters away (`online.de` is no `t-online.de`,
     * `mail.com` no `gmail.com`). Never a hint for these.
     */
    val KNOWN_DOMAINS: Set<String> = PROVIDERS.toSet() + setOf(
        "hotmail.at", "hotmail.ch", "hotmail.fr", "hotmail.it", "hotmail.co.uk", "outlook.at", "outlook.fr",
        "outlook.it", "live.at", "live.fr", "live.it", "posteo.at", "posteo.ch", "posteo.net", "posteo.eu",
        "online.de", "mail.com", "mail.de", "email.de", "gmail.de", "proton.ch", "protonmail.ch",
        "yahoo.fr", "yahoo.it", "yahoo.ca", "yahoo.co.uk", "aol.de", "gmx.li", "gmx.fr", "web.at",
    )

    /** The top-level domains of [PROVIDERS]; a typed TLD may be a typo only of one of these. */
    val COMMON_TLDS: Set<String> = PROVIDERS.map { it.substringAfterLast('.') }.toSet()

    /**
     * Real top-level domains (country codes of Europe and the Americas and the usual generic ones):
     * a domain with one of them is compared only with providers of the same TLD, `hotmail.fr` is
     * never `hotmail.de`.
     */
    val VALID_TLDS: Set<String> = COMMON_TLDS + setOf(
        "ad", "al", "ar", "au", "ba", "be", "bg", "bo", "br", "by", "ca", "cl", "cn", "co", "cr", "cu", "cy",
        "cz", "dk", "do", "ec", "ee", "eu", "fi", "fr", "gr", "gt", "hn", "hr", "hu", "ie", "il", "in", "io",
        "is", "it", "jp", "kr", "li", "lt", "lu", "lv", "mc", "md", "mk", "mt", "mx", "ni", "nl", "no", "nz",
        "pa", "pe", "pl", "pr", "pt", "py", "ro", "rs", "ru", "se", "si", "sk", "sm", "sv", "tr", "ua", "uk",
        "us", "uy", "va", "ve", "za", "biz", "edu", "gov", "info", "int", "mil", "name", "pro", "app", "dev",
        "email", "online", "shop", "tech", "xyz",
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

    /**
     * The providers the typed [domain] most likely means; empty for a known domain, an unknown one
     * or one still being typed. A real TLD ([VALID_TLDS]) is kept: only providers with the same TLD
     * are compared. Another TLD counts only as a typo of a provider TLD (`con`, `cmo`, `nte`, `ed`).
     */
    fun domains(domain: String): List<String> {
        val lower = domain.lowercase(Locale.ROOT)
        val trimmed = lower.trimEnd('.')
        // a trailing dot after a known provider: the provider without it
        if (trimmed != lower && trimmed in PROVIDERS) return listOf(trimmed)
        if (trimmed in KNOWN_DOMAINS) return emptyList()
        // still typing ("gmail.co" on the way to "gmail.com"), or no dot yet: no hint
        if ('.' !in trimmed || PROVIDERS.any { it.startsWith(trimmed) }) return emptyList()
        val tld = trimmed.substringAfterLast('.')
        val tlds = if (tld in VALID_TLDS) setOf(tld) else COMMON_TLDS.filter { distance(tld, it) <= 1 }.toSet()
        if (tlds.isEmpty()) return emptyList()
        var best = Int.MAX_VALUE
        val found = mutableListOf<String>()
        for (provider in PROVIDERS) {
            if (provider.substringAfterLast('.') !in tlds) continue
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
