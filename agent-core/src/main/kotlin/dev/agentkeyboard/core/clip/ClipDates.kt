// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import dev.agentkeyboard.core.EventParser
import dev.agentkeyboard.core.LocalMoment

/**
 * A date or time in a copied text for a calendar draft (stage 40, Smart Paste), German, English and
 * Spanish. The reading is [EventParser]'s (`@termin`); this gate only decides whether a copied text
 * really names a date, which is stricter than a typed command:
 * - A calendar date counts: `15.03.2026`, `15.03.26`, `2026-03-15`, `15/03/2026`, `03.12.` (two
 *   digits each), `am 3.12.`, `15. März`, `15 de marzo`, `March 15th` (month name in capitals).
 * - A day word (heute, morgen, Samstag, tomorrow, mañana …) or no date at all counts only with a
 *   time; a time without a day only with a mark (`18 Uhr`, `um 18:30`, `6pm`, `a las 8`). "bis
 *   morgen", "Samstag" or "Abfahrt 18:30" alone give nothing; "Guten Morgen" is no date.
 * - Numbers that may be versions or amounts make the text no date at all: `2.1`, `3.10` without
 *   "am"/"on"/"el" before them (a version, a price); `1.2.3` never matches a date. Review of stage
 *   40 (B3): `8.3.28` (a two-digit year with a one-digit day or month) counts only after such a word
 *   ("am 8.3.28"), else it is a version ("PHP 8.3.28"); no date-like number right after
 *   [VERSION_WORDS] ("Version 10.11.30").
 * - Opening hours and availability ("Öffnungszeiten", "Montag bis Freitag", "Mo-Fr", "erreichbar",
 *   "Hotline", "opening hours", "horario") are no event: the text gives nothing.
 * - Past dates, and year-less dates more than [MAX_AHEAD_WITHOUT_YEAR] days ahead (most likely just
 *   passed: "Rechnung vom 03.11."), give nothing.
 */
object ClipDates {
    const val MAX_LENGTH = EventParser.MAX_INPUT
    const val MAX_LINES = 4
    const val MAX_AHEAD_WITHOUT_YEAR = 300L

    private val WITH_YEAR = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2}|\\d{4})$")
    private val DOT_NO_YEAR = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.$")
    private val WEAK_DOT = Regex("^(\\d{1,2})\\.(\\d{1,2})$")
    private val ISO = Regex("^\\d{4}-\\d{1,2}-\\d{1,2}$")
    private val SLASH_YEAR = Regex("^\\d{1,2}/\\d{1,2}/(\\d{2}|\\d{4})$")
    private val DAY = Regex("^\\d{1,2}(\\.|st|nd|rd|th)?$")
    private val YEAR = Regex("^(19|20)\\d{2}$")
    private val COLON_TIME = Regex("^\\d{1,2}:\\d{2}$")
    /** "6pm", "6:30pm", "18uhr"; not "4h", which is as often a duration. */
    private val MARKED_TIME = Regex("^\\d{1,2}(:\\d{2})?(am|pm|a\\.m\\.?|p\\.m\\.?|uhr)$")
    private val H_TIME = Regex("^\\d{1,2}h\\d{2}$")
    private val NUMBER = Regex("^\\d{1,2}$")
    private val GREETING = Regex("(?i)(?<![\\p{L}])guten\\s+morgen(?![\\p{L}])")
    /** A full stop that ends a sentence after a date, a time or "Uhr" is no part of it ("um 18 Uhr.", "am 15.03.2027."). */
    private val SENTENCE_END = Regex("(?i)(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}|\\d{1,2}:\\d{2}|uhr|pm)\\.(?=\\s|$)")

    /** Full month names only: abbreviations ("mar", "ago", "may" as a verb) are words too often. */
    private val MONTHS = setOf(
        "januar", "jänner", "februar", "märz", "maerz", "april", "mai", "juni", "juli", "august", "september", "oktober",
        "november", "dezember", "january", "february", "march", "may", "june", "july", "october", "december",
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "setiembre", "octubre",
        "noviembre", "diciembre",
    )
    private val DAY_WORDS = setOf(
        "heute", "morgen", "übermorgen", "uebermorgen", "today", "tomorrow", "tonight", "hoy", "mañana", "manana",
        "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag", "sonnabend", "sonntag",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "lunes", "martes", "miércoles", "miercoles", "jueves", "viernes", "sábado", "sabado", "domingo",
    )
    /** Words before a date that make "3.12" a date ("am 3.12", "on 3.12", "el 3.12"). */
    private val DATE_LEADS = setOf("am", "an", "den", "vom", "zum", "bis", "ab", "on", "el", "from", "until", "till", "desde", "hasta")
    /** Words after which a number with dots is a version, never a date ("Version 1.2.30", "Firmware 3.1.99", "v 2.10.27"). */
    private val VERSION_WORDS = setOf(
        "version", "versión", "versao", "versão", "ver", "ver.", "v", "v.", "update", "upgrade", "release", "firmware", "build",
        "patch", "rev", "rev.", "revision", "software",
    )
    private val DOTTED = Regex("^v?\\d{1,4}(\\.\\d{1,4})+\\.?$")
    /** Opening hours and availability: a schedule, no event (review of stage 40, B3). */
    private val HOURS = Regex(
        "(?iu)(?<![\\p{L}])(?:öffnungszeit\\p{L}*|oeffnungszeit\\p{L}*|geöffnet|geoeffnet|sprechzeit\\p{L}*|sprechstunde\\p{L}*|" +
            "servicezeit\\p{L}*|geschäftszeit\\p{L}*|geschaeftszeit\\p{L}*|erreichbar\\p{L}*|hotline|werktags|täglich|taeglich|" +
            "rund um die uhr|opening hours|opening times|business hours|office hours|open daily|weekdays|daily|" +
            "horario\\p{L}*|abierto|atención al cliente|atencion al cliente)(?![\\p{L}])"
    )
    private const val WEEKDAY = "(?:mo|di|mi|do|fr|sa|so|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|mon|tue|wed|thu|fri|sat|sun|" +
        "monday|tuesday|wednesday|thursday|friday|saturday|sunday|lu|ma|ju|vi|lunes|martes|miércoles|miercoles|jueves|viernes|sábado|sabado|domingo)"
    /** A range of weekdays: "Montag bis Freitag", "Mo-Fr", "Mo.–Sa.", "Mon to Fri", "lunes a viernes". */
    private val WEEKDAY_RANGE = Regex("(?iu)(?<![\\p{L}])$WEEKDAY\\.?\\s*(?:-|–|—|bis|to|until|through|a|al|hasta)\\s*$WEEKDAY\\.?(?![\\p{L}])")
    private val TIME_LEADS = setOf("um", "at", "gegen", "ab", "las", "around", "circa", "ca.", "hacia", "sobre")
    private val TIME_MARKS = setOf("uhr", "o'clock", "oclock", "am", "pm", "a.m.", "p.m.", "a.m", "p.m")
    private val TIME_WORDS = setOf("halb", "viertel", "dreiviertel", "half", "quarter", "mittag", "noon", "mediodía", "mediodia")

    private fun keyOf(raw: String): String =
        raw.lowercase().replace('’', '\'').trimStart('(', '"', '\'', '„', '“', '«').trimEnd(',', ';', '!', '?', ')', '"', '\'', '“', '”', '»', ':')

    /** The date (and time) for a calendar draft, or null; [now] and [language] as for [EventParser.parse]. */
    fun find(text: String, now: LocalMoment, language: String): EventParser.Result? {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LENGTH || t.lines().size > MAX_LINES) return null
        val flat = SENTENCE_END.replace(GREETING.replace(t.replace(Regex("\\s+"), " "), " "), "$1").trim()
        if (HOURS.containsMatchIn(flat) || WEEKDAY_RANGE.containsMatchIn(flat)) return null
        val raws = flat.split(' ').filter { it.isNotEmpty() }
        val keys = raws.map { keyOf(it) }
        if (keys.isEmpty()) return null
        var strong = false
        var hasYear = false
        var dayWord = false
        var marked = false
        for (i in keys.indices) {
            val k = keys[i]
            val before = keys.getOrNull(i - 1)
            val after = keys.getOrNull(i + 1)
            // "Version 1.2.30", "Update 03.12.": a version number, the text names no date
            if (before in VERSION_WORDS && DOTTED.matches(k)) return null
            WITH_YEAR.matchEntire(k)?.let {
                val (day, month, year) = it.destructured
                if (validDayMonth(day, month)) {
                    // "8.3.28" is a version ("PHP 8.3.28") unless "am"/"vom"/"on"… comes before it; "08.03.28" is a date
                    if (year.length == 2 && (day.length == 1 || month.length == 1) && before !in DATE_LEADS) return null
                    strong = true
                    hasYear = true
                }
            }
            DOT_NO_YEAR.matchEntire(k)?.let {
                if (validDayMonth(it.groupValues[1], it.groupValues[2]) &&
                    ((it.groupValues[1].length == 2 && it.groupValues[2].length == 2) || before in DATE_LEADS)) strong = true
            }
            WEAK_DOT.matchEntire(k)?.let {
                // "18.30 Uhr" and "um 18.30" are times; "am 3.12" a date; "2.1" or "3.10" alone a version or an amount
                val isTime = after == "uhr" || after == "h" || before in TIME_LEADS
                if (!isTime && before !in DATE_LEADS && validDayMonth(it.groupValues[1], it.groupValues[2])) return null
                if (!isTime && before in DATE_LEADS) strong = true
                if (isTime) marked = true
            }
            if (ISO.matches(k)) { strong = true; hasYear = true }
            if (SLASH_YEAR.matches(k)) { strong = true; hasYear = true }
            if (DAY.matches(k)) {
                // "15. März", "15 de marzo", "15th of December"
                val monthAt = if (after == "de" || after == "of") i + 2 else i + 1
                if (keys.getOrNull(monthAt)?.trimEnd('.') in MONTHS) {
                    strong = true
                    if (keys.getOrNull(monthAt + 1)?.let { YEAR.matches(it) } == true ||
                        (keys.getOrNull(monthAt + 1) == "de" && keys.getOrNull(monthAt + 2)?.let { YEAR.matches(it) } == true)) hasYear = true
                }
            }
            // "March 15th" with the month in capitals, as English writes it
            if (k in MONTHS && raws[i].first().isUpperCase() && after?.let { DAY.matches(it.trimEnd('.')) } == true) {
                strong = true
                if (keys.getOrNull(i + 2)?.let { YEAR.matches(it) } == true) hasYear = true
            }
            if (k in DAY_WORDS) dayWord = true
            if (k in TIME_MARKS && before != null && (NUMBER.matches(before) || COLON_TIME.matches(before))) marked = true
            if (MARKED_TIME.matches(k) || H_TIME.matches(k)) marked = true
            if (k in TIME_LEADS && after != null && (NUMBER.matches(after) || COLON_TIME.matches(after) || after in TIME_WORDS ||
                    EventParserWords.isHourWord(after))) marked = true
            if (k == "halb" || k == "viertel" || k == "dreiviertel") marked = true
        }
        val found = EventParser.parse(flat, now, language) ?: return null
        val accepted = strong || (found.time != null && (marked || dayWord))
        if (!accepted) return null
        if (found.date < now.date) return null
        if (!hasYear && found.date > now.date.plusDays(MAX_AHEAD_WITHOUT_YEAR)) return null
        return found
    }

    private fun validDayMonth(day: String, month: String) = day.toInt() in 1..31 && month.toInt() in 1..12

    /** Hour words after "um"/"at"/"a las" ("um acht", "at seven", "a las ocho"). */
    private object EventParserWords {
        private val HOURS = setOf(
            "eins", "zwei", "drei", "vier", "fünf", "fuenf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf", "zwoelf",
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "una", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce",
        )
        fun isHourWord(word: String) = word in HOURS
    }
}
