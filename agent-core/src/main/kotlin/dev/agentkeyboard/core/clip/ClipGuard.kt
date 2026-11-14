// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import dev.agentkeyboard.core.UrlDetector
import dev.agentkeyboard.core.format.IbanCheck
import dev.agentkeyboard.core.format.PhoneFormat
import kotlin.math.ln

/** What the clipboard guard (stage 44) does with a clip. */
enum class ClipCategory {
    /** Kept in memory only, masked, gone after the first paste or [ClipGuard.SECRET_MILLIS]. */
    SECRET,
    /** Kept in memory only, in its own section, gone after [ClipGuard.OTP_MILLIS]. */
    OTP,
    /** Normal history entry with a label. */
    PERSONAL,
    /** Normal history entry. */
    NORMAL,
}

enum class ClipKind(val category: ClipCategory) {
    SECRET(ClipCategory.SECRET),
    OTP(ClipCategory.OTP),
    IBAN(ClipCategory.PERSONAL),
    PHONE(ClipCategory.PERSONAL),
    EMAIL(ClipCategory.PERSONAL),
    ADDRESS(ClipCategory.PERSONAL),
    LINK(ClipCategory.NORMAL),
    TEXT(ClipCategory.NORMAL),
}

/** Why a clip counts as a secret; for tests and the docs, never shown with the text. */
enum class SecretReason { MARKED, PRIVATE_KEY, API_KEY, JWT, TOKEN, PASSWORD }

/**
 * The guard's verdict. [code] is the one-time code alone (digits) for [ClipKind.OTP], else null.
 * [inText]: the code stands in a message ("Dein Code lautet 123456"); such a clip stays in the
 * history as it is and the vault gets the code as well (see [keepsOutOfHistory]).
 * [toString] never contains clip text.
 */
class ClipClass(val kind: ClipKind, val code: String? = null, val reason: SecretReason? = null, val inText: Boolean = false) {
    val category get() = kind.category
    /** Secrets and bare one-time codes never go into the history database; a code in a message does. */
    val keepsOutOfHistory get() = kind == ClipKind.SECRET || (kind == ClipKind.OTP && !inText)
    override fun equals(other: Any?) = other is ClipClass && other.kind == kind && other.code == code && other.reason == reason &&
        other.inText == inText
    override fun hashCode() = ((kind.hashCode() * 31 + (code?.hashCode() ?: 0)) * 31 + (reason?.hashCode() ?: 0)) * 31 + inText.hashCode()
    override fun toString() = "ClipClass($kind${reason?.let { ", $it" } ?: ""}${if (code != null) ", code" else ""}${if (inText) ", in text" else ""})"
}

/**
 * Clipboard guard (stage 44): sorts a copied text without asking. Pure Kotlin, no network, nothing
 * logged. Conservative by design: a clip only becomes [ClipKind.SECRET] on a strong signal (marked
 * sensitive by the copying app or copied in a password field, a private key block, a known key
 * format, a JWT, or a single token that looks random or like a password); hex strings (git hashes,
 * checksums, UUIDs, colours), words, URLs, paths, e-mail addresses, numbers and anything with
 * spaces (except the strong signals) stay normal. A missed secret ends up in the normal history the
 * user opted into, a false secret only leaves the history after two minutes; so doubtful cases stay
 * normal. Decisions are documented in docs/ARCHITECTURE.md → Zwischenablage-Wächter.
 */
object ClipGuard {
    /** Longer clips are only checked for key formats and private key blocks; everything else is normal text. */
    const val MAX_LENGTH = 4096
    /** Longer clips are only checked for a private key block. */
    const val MAX_SCAN = 65536
    /** Secrets leave the vault after the first paste, at the latest after this time (lead assumption). */
    const val SECRET_MILLIS = 2 * 60 * 1000L
    /** One-time codes leave the vault after this time. */
    const val OTP_MILLIS = 10 * 60 * 1000L
    /** One-time code sentences longer than this are normal text (an SMS is shorter). */
    const val MAX_OTP_SENTENCE = 300
    /** Address clips longer than this are normal text. */
    const val MAX_ADDRESS = 200

    private val PRIVATE_KEY = Regex("-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----")

    /** Known key formats, found anywhere in the clip (also `KEY=sk-…` lines and code snippets). */
    private val API_KEYS = listOf(
        "sk-(?:proj-|ant-(?:api\\d\\d-)?|svcacct-|admin-)?[A-Za-z0-9_\\-]{20,}", // OpenAI, Anthropic
        "(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{16,}", // Stripe
        "gh[pousr]_[A-Za-z0-9]{36,}", // GitHub
        "github_pat_[A-Za-z0-9_]{22,}",
        "(?:AKIA|ASIA)[A-Z0-9]{16}", // AWS access key id
        "xox[abposr]-[A-Za-z0-9\\-]{10,}", // Slack
        "AIza[A-Za-z0-9_\\-]{35}", // Google
        "glpat-[A-Za-z0-9_\\-]{20,}", // GitLab
        "hf_[A-Za-z0-9]{30,}", // Hugging Face
        "npm_[A-Za-z0-9]{36}",
        "\\d{8,10}:[A-Za-z0-9_\\-]{35}", // Telegram bot token
        "[Bb]earer\\s+(?=[A-Za-z0-9\\-._~+/]*\\d)[A-Za-z0-9\\-._~+/]{20,}=*", // a long bearer token (with a digit)
    ).map { Regex("(?<![A-Za-z0-9_])(?:$it)(?![A-Za-z0-9_])") }

    private val JWT = Regex("(?<![A-Za-z0-9_\\-])eyJ[A-Za-z0-9_\\-]{8,}\\.eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}")

    /** Shown in front of the last characters of a masked key. */
    private val MASK_PREFIXES = listOf(
        "sk-proj-", "sk-ant-", "sk-svcacct-", "sk-admin-", "sk_live_", "sk_test_", "rk_live_", "rk_test_", "sk-",
        "github_pat_", "ghp_", "gho_", "ghu_", "ghs_", "ghr_", "AKIA", "ASIA", "xoxb-", "xoxp-", "xoxa-", "xoxo-",
        "xoxs-", "xoxr-", "AIza", "glpat-", "hf_", "npm_", "eyJ",
    )

    private val BARE_OTP = Regex("\\d{4,8}|\\d{3}[ -]\\d{3}|\\d{4}[ -]\\d{4}")
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}")
    private val DATE = Regex("\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}")
    private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private const val TOKEN_CHARS = "+/=_-.~"

    /**
     * Words that announce a one-time code (de, en, es), only as whole words: "code" alone or in a
     * compound ("Bestätigungscode", "SMS-Code", "WhatsApp-Code"; not "Barcode", "QR-Code",
     * "Promo-Code"), "einmal" only in Einmalcode/Einmalpasswort/Einmal-PIN, never "Bestätigung" or
     * "Verifizierung" alone (an order confirmation number is no code). Password words announce a
     * secret instead ([PASSWORD_VALUE]). Explicit boundaries: `\b` is ASCII-only on newer JDKs.
     */
    private val OTP_WORDS = Regex(
        "(?i)(?<![\\p{L}\\d_\\-])(?:" +
            "(?:[\\p{L}\\d]+-)?(?:bestätigungs|sicherheits|zugangs|anmelde|login|verifizierungs|verifikations|aktivierungs|" +
            "freigabe|authentifizierungs|autorisierungs|einmal|pass|sms|auth|otp|pin|tan|2fa)-?code|" +
            "(?!(?:qr|bar|promo|rabatt|gutschein|post|zip|area|farb|color|colour|html|source|quell|programm|country|länder)-)" +
            "[\\p{L}\\d]+-code|code|c[oó]digo|otp|passcode|clave|" +
            "einmal-?(?:passwort|kennwort|pin|tan)|one-time|one time|m?tan|sms-?tan|pin" +
            ")(?![\\p{L}\\d_])"
    )
    /** A password word with its value right after it ("WLAN-Passwort ist 84736291", "Password: x7Kq9mZ2"). */
    private val PASSWORD_VALUE = Regex(
        "(?i)(?<![\\p{L}\\d_])(passwort|kennwort|password|passwd|contraseña|wlan-schlüssel|wifi-key)(?![\\p{L}\\d_])" +
            "(?:\\s*([:=])\\s*|\\s+(?:ist|lautet|heißt|is|es)\\s*:?\\s*)" +
            "(\\S{4,64}?)[.,;!?]?(?=\\s|$)"
    )
    /** Words right before a password word that make it a one-time password (then the code rule decides). */
    private val ONE_TIME_BEFORE = Regex("(?i)(one[- ]time|single[- ]use|einmal|temporäres|temporary|otp)[\\s-]*$")
    /** A code in a sentence: 4–8 digits, or 3+3 / 4+4 with one space or dash. */
    private val OTP_IN_TEXT = Regex("(?<![\\d+\\p{L}])(\\d{3}[ -]\\d{3}|\\d{4}[ -]\\d{4}|\\d{4,8})(?![\\d\\p{L}])")

    private val POSTCODE_CITY = Regex("(?<![\\d\\p{L}])\\d{4,5}\\s+\\p{Lu}[\\p{L}\\-]+")
    private val STREET_DE = Regex(
        "(?i)(?<![\\p{L}])\\p{L}*(straße|strasse|str\\.|weg|gasse|platz|allee|ring|damm|ufer|steig|chaussee|" +
            "markt|graben|kai|zeile|promenade)\\s*\\d{1,4}\\s?[a-z]?(?![\\d\\p{L}])"
    )
    private val STREET_ES = Regex(
        "(?i)(?<![\\p{L}])(calle|c/|avenida|avda\\.?|plaza|pza\\.|paseo|carrer|camino|ronda|travesía)\\s*[\\p{L} .'\\-]{2,40}?,?\\s*(n[ºo°.]\\s*)?\\d{1,4}(?!\\d)"
    )
    private val STREET_EN = Regex(
        "(?i)(?<![\\d\\p{L}])\\d{1,5}\\s+[\\p{L} .'\\-]{2,40}?\\s(street|st\\.?|avenue|ave\\.?|road|rd\\.?|lane|ln\\.?|drive|dr\\.?|boulevard|blvd\\.?)(?![\\p{L}])"
    )
    private val ZIP_EN = Regex("(?<!\\d)\\d{5}(?!\\d)|\\b[A-Z]{1,2}\\d[A-Z\\d]? \\d[A-Z]{2}\\b")

    /**
     * Sorts [text]. [markedSensitive]: the copying app marked the clip (`EXTRA_IS_SENSITIVE`) or it
     * was copied while a password field had the focus. [region]: region for phone numbers without a
     * country code (see [PhoneFormat.region]).
     */
    fun classify(text: String, markedSensitive: Boolean = false, region: String = "DE"): ClipClass {
        val t = text.trim()
        if (t.isEmpty()) return ClipClass(ClipKind.TEXT)
        if (markedSensitive) return secret(SecretReason.MARKED)
        if (PRIVATE_KEY.containsMatchIn(t)) return secret(SecretReason.PRIVATE_KEY)
        if (t.length > MAX_SCAN) return ClipClass(ClipKind.TEXT)
        if (API_KEYS.any { it.containsMatchIn(t) }) return secret(SecretReason.API_KEY)
        if (JWT.containsMatchIn(t)) return secret(SecretReason.JWT)
        if (t.length > MAX_LENGTH) return ClipClass(ClipKind.TEXT)

        if (BARE_OTP.matches(t) && !isYear(t)) return ClipClass(ClipKind.OTP, t.filter { it.isDigit() })
        val iban = t.removePrefix("IBAN:").removePrefix("IBAN").trim()
        if (IbanCheck.normalize(iban) != null) return ClipClass(ClipKind.IBAN)
        if (EMAIL.matches(t.removePrefix("mailto:"))) return ClipClass(ClipKind.EMAIL)
        if (t.length <= 30 && !DATE.matches(t) && PhoneFormat.isValid(t, region)) return ClipClass(ClipKind.PHONE)
        val singleToken = t.none { it.isWhitespace() }
        if (singleToken && UrlDetector.isUrl(t)) return ClipClass(ClipKind.LINK)
        if (singleToken) tokenSecret(t)?.let { return secret(it) }
        if (passwordInSentence(t)) return secret(SecretReason.PASSWORD)
        otpInSentence(t)?.let { return ClipClass(ClipKind.OTP, it, inText = true) }
        if (isAddress(t)) return ClipClass(ClipKind.ADDRESS)
        return ClipClass(ClipKind.TEXT)
    }

    private fun secret(reason: SecretReason) = ClipClass(ClipKind.SECRET, reason = reason)

    private fun isYear(t: String) = t.length == 4 && t.toInt() in 1900..2099

    /** A random-looking token or a password-like word, or null. */
    private fun tokenSecret(t: String): SecretReason? {
        if (t.contains("://") || t.startsWith("www.", ignoreCase = true) || t.startsWith("data:")) return null
        if (t[0] in "@#/~\\" || t.startsWith("./") || '\\' in t) return null // handles, tags, paths
        if (t.contains('@') && EMAIL.containsMatchIn(t)) return null
        val compact = t.replace("-", "")
        if (compact.all { it.isHex() }) return null // git hashes, checksums, UUIDs, colours
        if (UUID.matches(t)) return null
        if (t.all { it.isDigit() || it in " .,:/+-" }) return null // numbers, dates, times
        val upper = t.count { it in 'A'..'Z' }
        val lower = t.count { it in 'a'..'z' }
        val digits = t.count { it in '0'..'9' }
        val symbols = t.count { !it.isLetterOrDigit() }
        val lowerRun = longestRun(t) { it in 'a'..'z' }
        if (t.length in 20..200 && t.all { it.isAsciiLetterOrDigit() || it in TOKEN_CHARS }) {
            val enough = if (t.length < 32) digits >= 2 && lowerRun <= 5 else digits >= 3 && lowerRun <= 8
            if (upper >= 1 && lower >= 1 && enough && entropy(t) >= 3.5 && !dottedName(t)) return SecretReason.TOKEN
        }
        if (t.length in 8..32 && upper >= 1 && lower >= 1 && digits >= 1 && symbols >= 1
            && lowerRun <= 5 && digitTouchesLetter(t) && !FILE_ENDING.containsMatchIn(t) && t.all { it.code in 0x21..0x7e }
            && !separatedName(t)
        ) return SecretReason.PASSWORD
        return null
    }

    /**
     * Names like "B2B-Shop", "Win11-Pro", "camelCase2_x": the only symbols are `-`, `_` or `.`, each
     * between two letters or digits. A password has another symbol ("Sommer2024!", "P@ssw0rd!").
     */
    private fun separatedName(t: String) = t.indices.all { i ->
        t[i].isLetterOrDigit() || (t[i] in "-_." && i > 0 && i < t.lastIndex && t[i - 1].isLetterOrDigit() && t[i + 1].isLetterOrDigit())
    }

    /**
     * A password word with a value right after it (second review N8: the value must look like one).
     * The value has at least [PASSWORD_VALUE_MIN] characters and a digit or a symbol; after ":" or
     * "=" also letters only in upper and lower case, when no further words follow on its line
     * ("Passwort: Sommerwiese"). "Das Passwort ist falsch" and "Passwort: bitte beim Admin
     * erfragen" stay text. Not after "one-time"/"einmal" (a one-time password is a code).
     */
    private fun passwordInSentence(t: String): Boolean {
        if (t.length > MAX_OTP_SENTENCE) return false
        return PASSWORD_VALUE.findAll(t).any { m ->
            val value = m.groupValues[3]
            if (value.length < PASSWORD_VALUE_MIN) return@any false
            val afterColon = m.groupValues[2].isNotEmpty()
            val strong = value.any { it.isDigit() } || value.any { !it.isLetterOrDigit() }
            val mixedCase = value.any { it.isUpperCase() } && value.any { it.isLowerCase() }
            val rest = t.substring(m.range.last + 1).substringBefore('\n').trim()
            val alone = rest.isEmpty() || m.value.last() in ",;" || rest[0] in ",;|" || rest.split(WHITESPACE).size <= 1
            (strong || (afterColon && mixedCase && alone)) && !ONE_TIME_BEFORE.containsMatchIn(t.substring(0, m.range.first))
        }
    }
    private const val PASSWORD_VALUE_MIN = 6
    private val WHITESPACE = Regex("\\s+")

    private val FILE_ENDING = Regex("\\.[A-Za-z]{2,4}$")

    /** `com.example.app` style names: dots between letter segments. */
    private fun dottedName(t: String): Boolean {
        val parts = t.split('.')
        return parts.size >= 3 && parts.all { part -> part.isNotEmpty() && part.count { it.isLetter() } * 2 > part.length }
    }

    private fun digitTouchesLetter(t: String) = t.indices.any { i ->
        t[i].isDigit() && ((i > 0 && t[i - 1].isLetter()) || (i < t.lastIndex && t[i + 1].isLetter()))
    }

    private inline fun longestRun(t: String, test: (Char) -> Boolean): Int {
        var best = 0
        var run = 0
        for (c in t) { if (test(c)) { run++; if (run > best) best = run } else run = 0 }
        return best
    }

    /** Shannon entropy in bits per character. */
    fun entropy(t: String): Double {
        if (t.isEmpty()) return 0.0
        val counts = t.groupingBy { it }.eachCount()
        return counts.values.sumOf { val p = it.toDouble() / t.length; -p * ln(p) / ln(2.0) }
    }

    /**
     * The one code of a short message that names a code (de/en/es keywords): the candidate right
     * after a keyword if there is one, else the only candidate. Dates, times, decimals, amounts,
     * digits glued to letters and numbers in groups (phone numbers) are no candidates; years count
     * only when alone.
     */
    private fun otpInSentence(t: String): String? {
        if (t.length > MAX_OTP_SENTENCE) return null
        val keywords = OTP_WORDS.findAll(t).toList()
        if (keywords.isEmpty()) return null
        val candidates = OTP_IN_TEXT.findAll(t).filter { m ->
            val before = t.getOrNull(m.range.first - 1)
            val beforeBefore = t.getOrNull(m.range.first - 2)
            val after = t.getOrNull(m.range.last + 1)
            val afterAfter = t.getOrNull(m.range.last + 2)
            // part of a date, time, decimal, amount or a number written in groups (phone numbers)
            val joinedBefore = before != null && before in ".,:/ -" && beforeBefore?.isDigit() == true
            val joinedAfter = after != null && after in ".,:/ -€$%" && (afterAfter?.isDigit() == true || after in "€$%")
            val currencyBefore = before != null && before in "€$"
            !joinedBefore && !joinedAfter && !currencyBefore
        }.toList()
        if (candidates.isEmpty()) return null
        val nonYears = candidates.filter { !isYear(it.value) }.ifEmpty { candidates }
        // a code right after a keyword ("Code: 123456", "código es 1234")
        val following = nonYears.filter { c -> keywords.any { k -> c.range.first > k.range.last && c.range.first - k.range.last <= 25 } }
        val chosen = when {
            following.size == 1 -> following[0]
            nonYears.size == 1 -> nonYears[0]
            following.isNotEmpty() -> following.minByOrNull { c -> keywords.filter { it.range.last < c.range.first }.minOf { c.range.first - it.range.last } }
            else -> null
        } ?: return null
        return chosen.value.filter { it.isDigit() }
    }

    private fun isAddress(t: String): Boolean {
        if (t.length > MAX_ADDRESS || t.lines().size > 4) return false
        if (POSTCODE_CITY.containsMatchIn(t) && (STREET_DE.containsMatchIn(t) || STREET_ES.containsMatchIn(t))) return true
        return STREET_EN.containsMatchIn(t) && ZIP_EN.containsMatchIn(t)
    }

    /**
     * What may be shown of a secret: a known key prefix and, for keys of at least 20 characters,
     * the last four characters ("sk-…7f3a"); shorter secrets show no character at all. Used for the
     * screen and for TalkBack alike.
     */
    fun mask(text: String): String {
        val t = text.trim()
        val key = (API_KEYS + JWT).firstNotNullOfOrNull { it.find(t) }?.value
        val token = key ?: t.takeIf { it.none { c -> c.isWhitespace() } && !PRIVATE_KEY.containsMatchIn(it) }
        if (token == null) return HIDDEN
        val prefix = MASK_PREFIXES.firstOrNull { token.startsWith(it) }.orEmpty()
        val tail = if (token.length >= 20) token.takeLast(4) else ""
        return when {
            tail.isEmpty() -> if (prefix.isEmpty()) HIDDEN else "$prefix…"
            else -> "${prefix.ifEmpty { "••••" }}…$tail"
        }
    }

    /** The mask of a secret that shows no character. */
    const val HIDDEN = "••••••••"

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
