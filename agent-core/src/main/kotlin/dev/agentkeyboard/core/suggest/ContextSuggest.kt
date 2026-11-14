// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.suggest

import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Stage 45 "Feld- und Kontext-Vorschläge": pure rules, no network, nothing logged. What the strip
 * may offer ([SuggestKind]), which field asks for what ([FieldTargets]), which words before the
 * cursor announce what ([ContextCues]), the weighted score with its threshold ([ContextScorer]) and
 * the bounded learned weights ([ContextWeights]). The app side only maps `EditorInfo`, the vault
 * and "Meine Daten" onto these types and inserts on a tap.
 */
enum class SuggestKind { EMAIL, PHONE, IBAN, ADDRESS, NAME, OTP, LINK }

/** The kind of app, from its package name only. */
enum class AppCategory {
    MESSENGER, MAIL, BROWSER, OTHER;

    companion object {
        private val MESSENGERS = listOf(
            "com.whatsapp", "org.telegram", "org.thunderdog.challegram", "org.thoughtcrime.securesms", "ch.threema",
            "com.facebook.orca", "com.instagram.android", "com.discord", "com.slack", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.android.mms", "com.viber.voip", "jp.naver.line", "com.snapchat.android",
            "im.vector.app", "com.microsoft.teams", "com.skype", "com.wire", "eu.siacs.conversations", "org.mozilla.fenix.messages",
        )
        private val MAILS = listOf(
            "com.google.android.gm", "com.fsck.k9", "net.thunderbird", "com.microsoft.office.outlook", "ch.protonmail",
            "com.samsung.android.email", "com.android.email", "de.gmx.mobile", "de.web.mobile", "com.yahoo.mobile.client.android.mail",
            "eu.faircode.email",
        )
        private val BROWSERS = listOf(
            "com.android.chrome", "com.chrome", "org.mozilla.", "com.brave.browser", "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile", "com.microsoft.emmx", "com.opera.", "com.vivaldi.browser", "org.chromium.", "com.kiwibrowser",
            "org.bromite", "app.vanadium.browser", "com.google.android.apps.chrome",
        )

        /** By prefix of the package name; unknown apps are [OTHER]. */
        fun of(packageName: String?): AppCategory {
            val name = packageName?.lowercase(Locale.ROOT) ?: return OTHER
            // "org.mozilla." is a prefix, "com.whatsapp" the app and its own sub-packages
            fun matches(list: List<String>) = list.any { if (it.endsWith('.')) name.startsWith(it) else name == it || name.startsWith("$it.") }
            return when {
                matches(MESSENGERS) -> MESSENGER
                matches(MAILS) -> MAIL
                matches(BROWSERS) -> BROWSER
                else -> OTHER
            }
        }
    }
}

/** The input type class of a field, mapped from `EditorInfo.inputType` by the app. */
enum class FieldClass { TEXT, SHORT_MESSAGE, EMAIL, PHONE, NUMBER, PERSON_NAME, POSTAL_ADDRESS, OTHER }

/**
 * What the app knows about the focused field. [hint] is the hint text and label, [fieldName] the
 * id name (`EditorInfo.fieldName`), [autofillHints] whatever autofill hints the app can see (the
 * platform hands none to keyboards through `EditorInfo`; kept for callers that have some).
 */
class FieldSignals(
    val fieldClass: FieldClass,
    val hint: String = "",
    val fieldName: String = "",
    val autofillHints: List<String> = emptyList(),
) {
    override fun toString() = "FieldSignals($fieldClass)"
}

/**
 * The field asks for [kind]. [weak]: only the input type says so (a plain number field may be a
 * code field); then only a very fresh code is offered ([FieldTargets.WEAK_OTP_MILLIS]).
 */
data class FieldTarget(val kind: SuggestKind, val weak: Boolean = false)

object FieldTargets {
    /** A plain number field without a code hint gets only a code copied at most this long ago. */
    const val WEAK_OTP_MILLIS = 3 * 60 * 1000L
    /** A field with a clear code hint gets codes up to this age (the vault keeps them 10 minutes). */
    const val OTP_MILLIS = 10 * 60 * 1000L

    private val AUTOFILL = mapOf(
        "emailaddress" to SuggestKind.EMAIL, "email" to SuggestKind.EMAIL,
        "phone" to SuggestKind.PHONE, "phonenumber" to SuggestKind.PHONE, "tel" to SuggestKind.PHONE,
        "telephonenumber" to SuggestKind.PHONE, "phonenational" to SuggestKind.PHONE,
        "smsotpcode" to SuggestKind.OTP, "2faappotpcode" to SuggestKind.OTP, "onetimecode" to SuggestKind.OTP, "otp" to SuggestKind.OTP,
        "postaladdress" to SuggestKind.ADDRESS, "streetaddress" to SuggestKind.ADDRESS, "addressline1" to SuggestKind.ADDRESS,
        "personname" to SuggestKind.NAME, "name" to SuggestKind.NAME,
        "iban" to SuggestKind.IBAN,
    )

    /** Words that make a "code" field something else (postal code, voucher, promo, captcha). */
    private val NOT_OTP = Regex("post|zip|plz|gutschein|rabatt|promo|coupon|voucher|cupon|cupón|descuento|captcha|qr|bar|referral|invite|einlad|country|land|area|vorwahl|sort|bic|swift|blz|bank|iban")
    private val OTP_WORDS = Regex(
        "(?<![\\p{L}])(otp|code|codes|bestätigungscode|bestaetigungscode|verifizierungscode|sicherheitscode|einmalcode|" +
            "einmalpasswort|verification|verify|verificación|verificacion|código|codigo|tan|mtan|smstan|2fa|passcode|onetimecode|" +
            "smscode|authcode)(?![\\p{L}])"
    )
    private val EMAIL_WORDS = Regex("(?<![\\p{L}])(e-mail|email|e mail|mail|correo|emailadresse|mailadresse)(?![\\p{L}])")
    private val NOT_EMAIL = Regex("password|passwort|kennwort|contraseña|confirm|bestätig|wiederhol|repeat")
    private val PHONE_WORDS = Regex(
        "(?<![\\p{L}])(telefon|telefonnummer|phone|handy|handynummer|mobil|mobile|mobilnummer|teléfono|telefono|móvil|movil|" +
            "rufnummer|tel|celular|phonenumber)(?![\\p{L}])"
    )
    private val IBAN_WORDS = Regex("(?<![\\p{L}])iban(?![\\p{L}])")
    private val ADDRESS_WORDS = Regex(
        "(?<![\\p{L}])(adresse|address|dirección|direccion|straße|strasse|anschrift|lieferadresse|rechnungsadresse|street|calle|" +
            "domicilio)(?![\\p{L}])"
    )
    private val NOT_ADDRESS = Regex("e-mail|email|mail|ip|web|url|correo")
    private val NAME_HINTS = setOf("name", "ihr name", "dein name", "vollständiger name", "vor- und nachname", "full name", "your name",
        "nombre", "nombre completo", "tu nombre", "fullname", "personname")

    /** Hint text, label and id name, lower case, camel case and `_` split into words. */
    private fun words(field: FieldSignals): String {
        val id = field.fieldName.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('.', ' ')
        return (field.hint + " " + id).lowercase(Locale.ROOT).trim()
    }

    /** What the field asks for, or null for a normal text field. */
    fun target(field: FieldSignals): FieldTarget? {
        for (raw in field.autofillHints) {
            val hint = raw.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
            AUTOFILL[hint]?.let { return FieldTarget(it) }
        }
        if (field.fieldClass == FieldClass.OTHER) return null
        val words = words(field)
        val codeLike = field.fieldClass in setOf(FieldClass.TEXT, FieldClass.NUMBER, FieldClass.PHONE)
        if (codeLike && OTP_WORDS.containsMatchIn(words) && !NOT_OTP.containsMatchIn(words)) return FieldTarget(SuggestKind.OTP)
        when (field.fieldClass) {
            FieldClass.EMAIL -> return FieldTarget(SuggestKind.EMAIL)
            FieldClass.PHONE -> return FieldTarget(SuggestKind.PHONE)
            FieldClass.PERSON_NAME -> return FieldTarget(SuggestKind.NAME)
            FieldClass.POSTAL_ADDRESS -> return FieldTarget(SuggestKind.ADDRESS)
            else -> {}
        }
        if (field.fieldClass == FieldClass.TEXT) {
            if (IBAN_WORDS.containsMatchIn(words)) return FieldTarget(SuggestKind.IBAN)
            if (EMAIL_WORDS.containsMatchIn(words) && !NOT_EMAIL.containsMatchIn(words)) return FieldTarget(SuggestKind.EMAIL)
            if (PHONE_WORDS.containsMatchIn(words)) return FieldTarget(SuggestKind.PHONE)
            if (ADDRESS_WORDS.containsMatchIn(words) && !NOT_ADDRESS.containsMatchIn(words)) return FieldTarget(SuggestKind.ADDRESS)
            if (field.hint.lowercase(Locale.ROOT).trim().trimEnd('*', ':', ' ') in NAME_HINTS) return FieldTarget(SuggestKind.NAME)
        }
        if (field.fieldClass == FieldClass.NUMBER) return FieldTarget(SuggestKind.OTP, weak = true)
        return null
    }

    /**
     * Whether [value] fits what is already in the field: an empty field, or the start of [value]
     * (case ignored; for codes and phone numbers only digits and `+` count), but not [value] itself.
     */
    fun fits(typed: String, value: String, kind: SuggestKind): Boolean {
        if (typed.isEmpty()) return value.isNotEmpty()
        val (t, v) = if (kind == SuggestKind.OTP || kind == SuggestKind.PHONE || kind == SuggestKind.IBAN)
            typed.filter { it.isLetterOrDigit() || it == '+' }.lowercase(Locale.ROOT) to value.filter { it.isLetterOrDigit() || it == '+' }.lowercase(Locale.ROOT)
        else typed.lowercase(Locale.ROOT) to value.lowercase(Locale.ROOT)
        return t.isNotEmpty() && v.startsWith(t) && v != t
    }
}

/** The best cue for [kind] before the cursor: its stable id (for learning) and strength (0..1). */
data class CueMatch(val kind: SuggestKind, val cueId: String, val strength: Double)

/**
 * Cue words before the cursor (de, en, es). Each cue has a stable id (the only thing learning
 * stores, never the typed text), its phrases, the kinds it announces with a weight, and how far
 * before the cursor it may stand: link cues may stand earlier in the sentence ("guck mal, hier
 * dieses Video"), data cues ("meine Nummer ist", "IBAN:") directly before the cursor.
 */
object ContextCues {
    /** Only this much text before the cursor is read, and only after the last line break. */
    const val WINDOW = 80
    /** Longest distance from the end of a link cue to the cursor that still counts. */
    const val LINK_DISTANCE = 60
    /** Data cues stand directly before the cursor: at most this many characters (": ", " "). */
    const val DATA_DISTANCE = 3
    /**
     * A data cue followed directly by ":" (as "IBAN:") counts this much more. A data cue of a single
     * word ("Mail", "Code", "IBAN") counts only with that colon: "eine Mail schreiben" asks for nothing.
     */
    const val COLON_BONUS = 0.1

    class Cue(val id: String, val kinds: Map<SuggestKind, Double>, val maxDistance: Int, val phrases: List<String>)

    val CUES: List<Cue> = listOf(
        Cue("look", mapOf(SuggestKind.LINK to 0.45), LINK_DISTANCE, listOf(
            "guck mal", "schau mal", "guck dir", "schau dir", "sieh mal", "sieh dir", "check das", "check mal", "checkt mal", "guckt mal",
            "schaut mal", "look at", "check this", "check it out", "check out", "have a look", "take a look", "see this", "watch this",
            "mira esto", "mira este", "mira esta", "mira", "echa un vistazo", "fíjate", "fijate", "mirad")),
        Cue("here", mapOf(SuggestKind.LINK to 0.2), LINK_DISTANCE, listOf(
            "hier", "da ist", "here", "here is", "here's", "aquí", "aqui", "aquí está", "aquí tienes", "ahí")),
        Cue("media", mapOf(SuggestKind.LINK to 0.4), LINK_DISTANCE, listOf(
            "video", "videos", "link", "links", "artikel", "seite", "website", "webseite", "clip", "song", "lied", "post", "beitrag",
            "rezept", "article", "page", "site", "recipe", "enlace", "vídeo", "artículo", "articulo", "página", "pagina", "canción",
            "cancion", "publicación", "receta")),
        Cue("phone", mapOf(SuggestKind.PHONE to 0.75), DATA_DISTANCE, listOf(
            "meine nummer ist", "meine nummer", "meine handynummer", "meine handynummer ist", "meine telefonnummer",
            "meine telefonnummer ist", "ruf mich an unter", "erreichst du mich unter", "erreichbar unter", "nummer", "tel", "telefon",
            "handy", "my number is", "my number", "my phone number is", "call me at", "phone", "mi número es", "mi numero es",
            "mi número", "mi numero", "mi teléfono es", "mi telefono es", "llámame al", "llamame al", "teléfono", "telefono", "móvil")),
        Cue("email", mapOf(SuggestKind.EMAIL to 0.75), DATA_DISTANCE, listOf(
            "meine mail ist", "meine e-mail ist", "meine e-mail-adresse ist", "meine e-mail-adresse", "meine mailadresse",
            "meine mailadresse ist", "meine e-mail", "meine mail", "e-mail", "email", "mail", "schreib mir an", "my email is",
            "my email", "my e-mail is", "email me at", "mi correo es", "mi correo", "correo", "mi email es", "mi email")),
        Cue("iban", mapOf(SuggestKind.IBAN to 0.75), DATA_DISTANCE, listOf(
            "iban", "meine iban", "meine iban ist", "meine iban lautet", "kontonummer", "bankverbindung", "überweis an", "my iban",
            "my iban is", "bank details", "account number", "mi iban", "mi iban es", "número de cuenta", "numero de cuenta", "cuenta")),
        Cue("address", mapOf(SuggestKind.ADDRESS to 0.75), DATA_DISTANCE, listOf(
            "adresse", "meine adresse", "meine adresse ist", "meine adresse lautet", "anschrift", "lieferadresse", "ich wohne in",
            "address", "my address", "my address is", "i live at", "dirección", "direccion", "mi dirección", "mi direccion",
            "mi dirección es", "mi direccion es", "vivo en")),
        Cue("code", mapOf(SuggestKind.OTP to 0.75), DATA_DISTANCE, listOf(
            "code", "der code ist", "der code lautet", "code ist", "code lautet", "bestätigungscode", "otp", "tan", "the code is",
            "code is", "código", "codigo", "el código es", "el codigo es", "código es")),
        Cue("name", mapOf(SuggestKind.NAME to 0.75), DATA_DISTANCE, listOf(
            "ich heiße", "ich heisse", "mein name ist", "my name is", "me llamo", "mi nombre es")),
    )

    /** Ids a learned weight may have; anything else is dropped when weights are read. */
    val IDS: Set<String> = CUES.map { it.id }.toSet()

    private fun isLetter(c: Char) = c.isLetterOrDigit() || c == '-'

    /** Whether the text after a cue already holds what the cue announces (then nothing is offered). */
    private fun alreadyThere(kind: SuggestKind, after: String): Boolean = when (kind) {
        SuggestKind.LINK -> after.contains("http") || after.contains("www.") || Regex("[a-z0-9-]+\\.[a-z]{2,}/").containsMatchIn(after)
        SuggestKind.EMAIL -> after.contains('@')
        SuggestKind.PHONE, SuggestKind.IBAN, SuggestKind.OTP -> after.count { it.isDigit() } >= 3
        SuggestKind.ADDRESS, SuggestKind.NAME -> after.any { it.isLetterOrDigit() }
    }

    /**
     * The cues before the cursor, one [CueMatch] per kind. Nothing in the middle of a word: the
     * text must end with a space, a line's start or ":" (the strip then does not hide the word
     * suggestions of a word being typed). Strength: best cue × closeness, plus half the second
     * and a quarter of the third distinct cue, at most 1.
     */
    fun find(textBefore: CharSequence): List<CueMatch> {
        if (textBefore.isEmpty()) return emptyList()
        val last = textBefore[textBefore.length - 1]
        if (!(last.isWhitespace() || last == ':')) return emptyList()
        val start = max(0, textBefore.length - WINDOW)
        var text = textBefore.subSequence(start, textBefore.length).toString().lowercase(Locale.ROOT)
        val lineBreak = text.lastIndexOf('\n', text.length - 2)
        if (lineBreak >= 0) text = text.substring(lineBreak + 1)
        if (text.isBlank()) return emptyList()
        val found = HashMap<SuggestKind, MutableList<Pair<String, Double>>>()
        for (cue in CUES) {
            var best = -1.0
            var bestKindsBonus = 0.0
            for (phrase in cue.phrases) {
                var index = text.lastIndexOf(phrase)
                while (index >= 0) {
                    val phraseEnd = index + phrase.length
                    val okBefore = index == 0 || !isLetter(text[index - 1])
                    val okAfter = phraseEnd >= text.length || !isLetter(text[phraseEnd])
                    if (okBefore && okAfter) {
                        val after = text.substring(phraseEnd)
                        val distance = after.trimEnd().length
                        val data = cue.maxDistance == DATA_DISTANCE
                        val colon = data && after.trimStart().startsWith(":")
                        val needsColon = data && ' ' !in phrase
                        if (distance <= cue.maxDistance && (colon || !needsColon) && cue.kinds.keys.none { alreadyThere(it, after) }) {
                            val closeness = if (distance <= 8) 1.0 else max(0.4, 1.0 - (distance - 8) / 60.0)
                            if (closeness > best) { best = closeness; bestKindsBonus = if (colon) COLON_BONUS else 0.0 }
                        }
                        break
                    }
                    index = if (index == 0) -1 else text.lastIndexOf(phrase, index - 1)
                }
            }
            if (best < 0) continue
            for ((kind, weight) in cue.kinds)
                found.getOrPut(kind) { ArrayList() } += cue.id to (weight * best + bestKindsBonus)
        }
        return found.map { (kind, cues) ->
            val sorted = cues.sortedByDescending { it.second }
            val strength = sorted.getOrNull(0)!!.second + 0.5 * (sorted.getOrNull(1)?.second ?: 0.0) + 0.25 * (sorted.getOrNull(2)?.second ?: 0.0)
            CueMatch(kind, sorted[0].first, min(1.0, strength))
        }.sortedByDescending { it.strength }
    }
}

/** Something that could be inserted: from "Meine Daten" ([own]) or a clip copied [ageMillis] ago. */
data class Candidate(val kind: SuggestKind, val own: Boolean, val ageMillis: Long = 0L)

/**
 * The local weighted score of a context suggestion: cue strength + what is offered (own entry, or
 * a clip by freshness) + app + field + the learned weight. A chip only from [THRESHOLD]; without a
 * cue the rest stays below it, so a fresh clip alone never shows a chip in a normal text field.
 */
object ContextScorer {
    const val THRESHOLD = 1.0
    /** An entry of "Meine Daten" has no age. */
    const val OWN = 0.45
    /** Clips older than this are never offered. */
    const val MAX_AGE_MILLIS = 10 * 60 * 1000L
    /** Freshness falls with this time constant: e ^ (−age / τ). */
    const val TAU_MILLIS = 6 * 60 * 1000L

    /** Freshness of a clip just copied, by kind: a link copied a moment ago weighs the most. */
    fun freshMax(kind: SuggestKind): Double = when (kind) {
        SuggestKind.LINK -> 0.7
        SuggestKind.OTP -> 0.6
        SuggestKind.PHONE, SuggestKind.EMAIL, SuggestKind.IBAN, SuggestKind.ADDRESS -> 0.55
        SuggestKind.NAME -> 0.3
    }

    fun freshness(candidate: Candidate): Double {
        if (candidate.own) return OWN
        if (candidate.ageMillis < 0 || candidate.ageMillis > MAX_AGE_MILLIS) return Double.NEGATIVE_INFINITY
        return freshMax(candidate.kind) * exp(-candidate.ageMillis.toDouble() / TAU_MILLIS)
    }

    fun app(app: AppCategory, kind: SuggestKind): Double = when (app) {
        AppCategory.MESSENGER -> when (kind) { SuggestKind.LINK -> 0.15; SuggestKind.PHONE, SuggestKind.ADDRESS -> 0.05; else -> 0.0 }
        AppCategory.MAIL -> when (kind) { SuggestKind.IBAN -> 0.1; SuggestKind.EMAIL, SuggestKind.PHONE, SuggestKind.ADDRESS -> 0.05; else -> 0.0 }
        AppCategory.BROWSER -> if (kind == SuggestKind.LINK) -0.1 else 0.0
        AppCategory.OTHER -> 0.0
    }

    fun field(field: FieldClass, kind: SuggestKind): Double =
        if (field == FieldClass.SHORT_MESSAGE && kind == SuggestKind.LINK) 0.1 else 0.0

    /** The score of offering [candidate] for [match]; compare with [THRESHOLD]. */
    fun score(match: CueMatch, candidate: Candidate, app: AppCategory, field: FieldClass, weights: ContextWeights): Double {
        if (match.kind != candidate.kind) return Double.NEGATIVE_INFINITY
        return match.strength + freshness(candidate) + app(app, candidate.kind) + field(field, candidate.kind) +
            weights.get(match.cueId, match.kind)
    }
}

/**
 * Learned weights per cue id and kind (stage 45 context learning): only numbers and fixed ids,
 * never typed text. Accepting a chip moves the weight towards [MAX], passing it by towards [MIN]
 * (smaller steps); both bounded. Weights fade with a half-life of [HALF_LIFE_DAYS] days. Immutable:
 * every change returns a new instance.
 */
class ContextWeights private constructor(private val values: Map<String, Double>, val day: Long) {
    fun get(cueId: String, kind: SuggestKind): Double = values[key(cueId, kind)] ?: 0.0

    val size: Int get() = values.size

    /** The chip for [cueId] and [kind] was tapped. */
    fun accept(cueId: String, kind: SuggestKind, today: Long): ContextWeights = change(cueId, kind, today) { it + ACCEPT_RATE * (MAX - it) }

    /** The chip for [cueId] and [kind] was shown and went away without a tap. */
    fun ignore(cueId: String, kind: SuggestKind, today: Long): ContextWeights = change(cueId, kind, today) { it - IGNORE_RATE * (it - MIN) }

    private fun change(cueId: String, kind: SuggestKind, today: Long, step: (Double) -> Double): ContextWeights {
        if (cueId !in ContextCues.IDS) return this
        val faded = decayed(today)
        val key = key(cueId, kind)
        val next = HashMap(faded.values)
        next[key] = step(faded.values[key] ?: 0.0).coerceIn(MIN, MAX)
        return ContextWeights(next, faded.day)
    }

    /** The weights faded from [day] to [today]; tiny weights are dropped. A clock going back changes nothing. */
    fun decayed(today: Long): ContextWeights {
        if (today <= day) return this
        val factor = 0.5.pow((today - day).toDouble() / HALF_LIFE_DAYS)
        val next = values.mapValues { it.value * factor }.filterValues { kotlin.math.abs(it) >= DROP }
        return ContextWeights(next, today)
    }

    /** "v1;<day>;<id>:<KIND>=<weight>;…", ids and kinds only from the fixed tables. */
    fun encode(): String = buildString {
        append(VERSION).append(';').append(day)
        values.toSortedMap().forEach { (key, value) -> append(';').append(key).append('=').append(String.format(Locale.ROOT, "%.4f", value)) }
    }

    override fun toString() = "ContextWeights(${values.size})"

    companion object {
        const val MAX = 0.4
        const val MIN = -0.4
        const val ACCEPT_RATE = 0.3
        const val IGNORE_RATE = 0.12
        const val HALF_LIFE_DAYS = 30.0
        private const val DROP = 0.001
        private const val VERSION = "v1"
        /** Longer stored text is not read (the full table is far shorter). */
        const val MAX_ENCODED = 4096

        val EMPTY = ContextWeights(emptyMap(), 0L)

        fun empty(today: Long) = ContextWeights(emptyMap(), today)

        private fun key(cueId: String, kind: SuggestKind) = "$cueId:${kind.name}"

        /** Reads [encoded]; anything unknown, out of range or malformed is dropped, never thrown. */
        fun parse(encoded: String?, today: Long): ContextWeights {
            if (encoded.isNullOrEmpty() || encoded.length > MAX_ENCODED) return empty(today)
            val parts = encoded.split(';')
            if (parts.size < 2 || parts[0] != VERSION) return empty(today)
            val day = parts[1].toLongOrNull()?.takeIf { it in 0..today } ?: today
            val values = HashMap<String, Double>()
            for (part in parts.drop(2)) {
                val (key, raw) = part.split('=').takeIf { it.size == 2 } ?: continue
                val (id, kindName) = key.split(':').takeIf { it.size == 2 } ?: continue
                if (id !in ContextCues.IDS) continue
                val kind = SuggestKind.entries.firstOrNull { it.name == kindName } ?: continue
                val value = raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                values[key(id, kind)] = value.coerceIn(MIN, MAX)
            }
            return ContextWeights(values, day).decayed(today)
        }
    }
}
