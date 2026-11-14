// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import dev.agentkeyboard.core.UrlDetector

/**
 * Removes tracking parameters from web addresses (stage 40, Smart Paste), offline. Only the query
 * parameters of a fixed list go: every `utm_…`, the click ids of ad networks (`fbclid`, `gclid`,
 * `dclid`, `gbraid`, `wbraid`, `msclkid`, `yclid`, `twclid`, `ttclid`, `gclsrc`), mail tools
 * (`mc_eid`, `mc_cid`, `_hsenc`, `_hsmi`, `mkt_tok`, `oly_anon_id`, `oly_enc_id`, `vero_id`),
 * Instagram (`igshid`, `igsh`), Google (`_gl`, `srsltid`) and, only on YouTube and Spotify, the share
 * id `si`. Everything else stays as written and in its order: path, other parameters (`v=`, `t=`,
 * `id=`, `q=`, `page=`), their encoding, empty parts and the fragment. Parameters are split at `&`
 * only; a part with a `;` in it stays whole (it may hold more than one parameter). Nothing is added
 * and nothing is opened.
 */
object LinkCleaner {
    /** Longer texts are left alone. */
    const val MAX_TEXT = 4000

    private val NAMES = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "mc_eid", "mc_cid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id",
        "igshid", "igsh", "_gl", "srsltid",
    )
    private const val PREFIX = "utm_"
    /** Parameters that are tracking only on these hosts (and their subdomains). */
    private val HOST_NAMES = mapOf(
        "youtube.com" to setOf("si"),
        "youtu.be" to setOf("si"),
        "spotify.com" to setOf("si"),
    )

    private val SCHEME = Regex("(?i)^https?://")

    /**
     * [url] without its tracking parameters, or null if it is no web address (see [UrlDetector]) or
     * has nothing to remove. An address without scheme stays without one.
     */
    fun clean(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || !UrlDetector.isUrl(trimmed)) return null
        return cleanAddress(trimmed)
    }

    /**
     * [text] with every `http(s)://` address in it cleaned, everything else unchanged; null if no
     * address had anything to remove.
     */
    fun cleanText(text: String): String? {
        if (text.length > MAX_TEXT || !text.contains("://")) return null
        val out = StringBuilder(text.length)
        var changed = false
        var i = 0
        val starts = Regex("(?i)https?://").findAll(text).map { it.range.first }.toList()
        for (start in starts) {
            if (start < i) continue
            // the address runs to the next whitespace; closing punctuation at its end is no part of it
            var end = start
            while (end < text.length && !text[end].isWhitespace()) end++
            var token = text.substring(start, end)
            while (token.isNotEmpty() && token.last() in CLOSING &&
                !(token.last() == ')' && token.count { it == '(' } >= token.count { it == ')' })) token = token.dropLast(1)
            out.append(text, i, start)
            val cleaned = if (UrlDetector.isUrl(token)) cleanAddress(token) else null
            if (cleaned != null) { out.append(cleaned); changed = true } else out.append(token)
            i = start + token.length
        }
        if (!changed) return null
        out.append(text, i, text.length)
        return out.toString()
    }

    private const val CLOSING = ".,;:!?)]}>\"'»“”‘’«…"

    /** The address without tracking parameters, or null if nothing was removed. */
    private fun cleanAddress(address: String): String? {
        val hashAt = address.indexOf('#')
        val beforeHash = if (hashAt >= 0) address.substring(0, hashAt) else address
        val fragment = if (hashAt >= 0) address.substring(hashAt) else ""
        val queryAt = beforeHash.indexOf('?')
        if (queryAt < 0) return null
        val base = beforeHash.substring(0, queryAt)
        val parts = beforeHash.substring(queryAt + 1).split('&')
        val host = host(base)
        val hostNames = HOST_NAMES.entries.filter { (domain, _) -> host == domain || host.endsWith(".$domain") }.flatMap { it.value }.toSet()
        val kept = parts.filter { part ->
            val name = part.substringBefore('=').lowercase()
            // review of stage 40 (B6): some servers also split at ';' ("utm_source=x;id=2"), so such a part stays whole
            ';' in part || !(name.startsWith(PREFIX) || name in NAMES || name in hostNames)
        }
        if (kept.size == parts.size) return null
        // the other parts stay as written, empty ones too ("id=1&&b=2"); only nothing at all drops the "?"
        val query = if (kept.all { it.isEmpty() }) "" else kept.joinToString("&")
        return base + (if (query.isEmpty()) "" else "?$query") + fragment
    }

    /** Lower-case host of [base] (scheme, host, optional port and path), without "www.". */
    private fun host(base: String): String {
        val rest = SCHEME.find(base)?.let { base.substring(it.range.last + 1) } ?: base
        return rest.substringBefore('/').substringBefore(':').lowercase().removePrefix("www.")
    }
}
