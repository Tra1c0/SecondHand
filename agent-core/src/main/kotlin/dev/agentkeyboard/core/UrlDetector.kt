// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Strict recognition of web addresses for `@url` (stage 24d). Only real addresses count: an
 * explicit `http`/`https` scheme, or a domain whose top-level domain looks like one in use.
 * File names (`foto.jpg`, `bericht.pdf`), version numbers (`1.2.3`), `a.b` and words joined by a
 * missing space after a full stop (`fertig.Dann`) are no addresses. Other schemes (`javascript:`,
 * `file:`, `intent:`, `ftp:`) and addresses with user info (`https://bank.de@evil.com`) never count.
 *
 * Pure Kotlin; nothing here opens anything.
 */
object UrlDetector {
    /** Longest address accepted; longer tokens are ignored. */
    const val MAX_LENGTH = 2000

    /** Country codes in use as top-level domains (ISO 3166 plus uk, eu, ac, su). */
    private val COUNTRY_TLDS = """
        ac ad ae af ag ai al am ao aq ar as at au aw ax az ba bb bd be bf bg bh bi bj bm bn bo br bs bt
        bw by bz ca cc cd cf cg ch ci ck cl cm cn co cr cu cv cw cx cy cz de dj dk dm do dz ec ee eg er
        es et eu fi fj fk fm fo fr ga gd ge gf gg gh gi gl gm gn gp gq gr gs gt gu gw gy hk hm hn hr ht
        hu id ie il im in io iq ir is it je jm jo jp ke kg kh ki km kn kp kr kw ky kz la lb lc li lk lr
        ls lt lu lv ly ma mc md me mg mh mk ml mm mn mo mp mq mr ms mt mu mv mw mx my mz na nc ne nf ng
        ni nl no np nr nu nz om pa pe pf pg ph pk pl pm pn pr ps pt pw py qa re ro rs ru rw sa sb sc sd
        se sg sh si sk sl sm sn so sr ss st su sv sx sy sz tc td tf tg th tj tk tl tm tn to tr tt tv tw
        tz ua ug uk us uy uz va vc ve vg vi vn vu wf ws ye yt za zm zw
    """.trim().split(Regex("\\s+")).toSet()

    /** Generic top-level domains common enough to be typed without `www.` or a scheme. */
    private val GENERIC_TLDS = """
        com org net info biz edu gov mil int arpa name pro mobi aero coop museum jobs tel travel asia cat
        app dev page blog shop store online site website web xyz top club tech cloud news media live
        art design studio digital email link click space world one global agency group company ltd
        gmbh inc team social network solutions services systems software support center academy school
        university education health care bank finance money law legal life love family games game fun
        photo photos pictures video music radio film events today community church africa wiki
        berlin hamburg koeln cologne bayern nrw ruhr saarland wien swiss tirol bio eco green energy
        google youtube amazon apple microsoft android chat mail
    """.trim().split(Regex("\\s+")).toSet()

    /** Country codes that are far more often file extensions when written without `www.` or scheme. */
    private val FILE_LIKE_TLDS = setOf("md", "py", "sh", "rs", "ps")

    private val OPENING = "([{<\"'«„“‚‘»"
    private val CLOSING = ".,;:!?)]}>\"'»“”‘’«…"

    /** True only for a real address, see [normalize]. */
    fun isUrl(text: CharSequence): Boolean = normalize(text) != null

    /**
     * The address to open, with `https://` added when no scheme was typed, or null if [text] is no
     * address. Surrounding whitespace is ignored; inner whitespace makes it no address.
     */
    fun normalize(text: CharSequence): String? {
        val token = text.trim().toString()
        if (token.isEmpty() || token.length > MAX_LENGTH) return null
        if (token.any { it.isWhitespace() || it.isISOControl() || it in "<>\"`\\" }) return null
        val schemeEnd = token.indexOf("://")
        return if (schemeEnd >= 0) {
            val scheme = token.substring(0, schemeEnd).lowercase()
            if (scheme != "http" && scheme != "https") return null
            val rest = token.substring(schemeEnd + 3)
            if (!validAuthority(rest, withScheme = true)) return null
            scheme + "://" + rest
        } else {
            // "mailto:", "javascript:alert(1)", "tel:" and other schemes without "//"
            val colon = token.indexOf(':')
            val isScheme = colon >= 0 && token.substring(0, colon).all { it.isLetter() } &&
                token.getOrNull(colon + 1)?.isDigit() != true // "heise.de:8080" is a port, "tel:" a scheme
            if (isScheme) return null
            if (!validAuthority(token, withScheme = false)) return null
            "https://$token"
        }
    }

    /**
     * Addresses in free text, normalized, without duplicates, in the order they appear; at most
     * [limit]. Only [MAX_SCAN] characters are looked at.
     */
    fun find(text: CharSequence, limit: Int = 10): List<String> {
        val found = LinkedHashSet<String>()
        val scanned = if (text.length > MAX_SCAN) text.subSequence(0, MAX_SCAN) else text
        for (raw in scanned.split(WHITESPACE)) {
            if (found.size >= limit) break
            val token = trimToken(raw) ?: continue
            normalize(token)?.let { found += it }
        }
        return found.toList()
    }

    /** Characters [find] looks at. */
    const val MAX_SCAN = 20_000

    private val WHITESPACE = Regex("\\s+")

    private fun trimToken(raw: String): String? {
        if (raw.length < 4 || raw.length > MAX_LENGTH + 8) return null
        var token = raw
        // "Link:https://…" or "(https://…": start at the scheme
        val scheme = Regex("(?i)https?://").find(token)
        if (scheme != null) token = token.substring(scheme.range.first)
        else {
            if ('@' in token) return null // a mail address or a mention
            token = token.trimStart { it in OPENING }
        }
        while (token.isNotEmpty()) {
            val last = token.last()
            if (last == ')' && token.count { it == '(' } >= token.count { it == ')' }) break // "wiki/Foo_(Bar)"
            if (last in CLOSING) token = token.dropLast(1) else break
        }
        return token.ifEmpty { null }
    }

    /** Host, optional port and optional path, query or fragment. */
    private fun validAuthority(rest: String, withScheme: Boolean): Boolean {
        val hostEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, hostEnd)
        if (authority.isEmpty() || '@' in authority) return false
        val colon = authority.lastIndexOf(':')
        val host = if (colon >= 0) {
            val port = authority.substring(colon + 1)
            if (port.isEmpty() || port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() !in 1..65535) return false
            authority.substring(0, colon)
        } else authority
        val path = rest.substring(hostEnd)
        return if (withScheme) validHost(host, explicit = true)
        else validHost(host, explicit = false, hasPath = path.isNotEmpty())
    }

    private fun validHost(rawHost: String, explicit: Boolean, hasPath: Boolean = false): Boolean {
        val host = rawHost.removeSuffix(".")
        if (host.isEmpty() || host.length > 253) return false
        if (explicit && (host.equals("localhost", ignoreCase = true) || isIpv4(host))) return true
        val labels = host.split('.')
        if (labels.size < 2) return false
        for (label in labels) {
            if (label.isEmpty() || label.length > 63) return false
            if (label.first() == '-' || label.last() == '-') return false
            if (!label.all { it.isLetterOrDigit() || it == '-' }) return false
        }
        val tld = labels.last()
        if (tld.length < 2 || !tld.all { it in 'a'..'z' || it in 'A'..'Z' }) return false
        if (explicit) return true
        // without a scheme: "fertig.Dann" is a missing space, "HEISE.DE" and "heise.de" are addresses
        if (tld != tld.lowercase() && tld != tld.uppercase()) return false
        val lower = tld.lowercase()
        val www = labels.first().equals("www", ignoreCase = true) && labels.size >= 3
        if (www) return true
        if (lower !in COUNTRY_TLDS && lower !in GENERIC_TLDS) return false
        if (lower in FILE_LIKE_TLDS && !hasPath) return false
        return labels.dropLast(1).any { label -> label.any { it.isLetter() } }
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } && it.toInt() <= 255 }
    }
}
