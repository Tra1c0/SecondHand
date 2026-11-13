// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.TimeZone

/**
 * Finds a date and a time in a short typed text for `@termin` and `@datum` (stage 24f), in
 * German, English and Spanish: "morgen 18 Uhr Zahnarzt", "Samstag halb acht Kino",
 * "am 3.12. um 9:30 Meeting", "tomorrow 6pm dinner", "mañana a las 8 cena". The words that are
 * not part of the date or time are the title. Pure and offline; "now" is passed in.
 *
 * Rules:
 * - Dates: heute/today/hoy, morgen/tomorrow/mañana, übermorgen, gestern, "+3"/"-1", "in 3 Tagen",
 *   weekdays (next occurrence, today included unless its time has passed), 24.12., 24.12.2026,
 *   24/12 (12/24 when the keyboard language is English), 2026-12-24, "24. Dezember",
 *   "December 24th", "24 de diciembre". A date without a year is its next occurrence.
 * - Times: 18:30, 18.30 Uhr, 18 Uhr (30), 18h30, 6pm, 6:30 p.m., halb acht, viertel nach/vor
 *   acht, viertel acht, dreiviertel acht, zehn nach acht, half past seven, quarter to eight,
 *   "a las ocho y media", "8 menos cuarto", noon/Mittag/mediodía. A bare number or number word
 *   is a time only after um/at/a las/gegen or before Uhr/o'clock/am/pm.
 * - Day periods (abends, nachmittags, "in the evening", "de la noche", tonight …) move a time
 *   to the afternoon or evening. Without am/pm or a period, colloquial times (number words,
 *   halb/viertel, "at 7", "a las 7") from 1 to 7 o'clock are in the afternoon or evening:
 *   "halb acht" is 19:30. Digits with a colon or "Uhr" are read as 24-hour times.
 * - A time without a date is today if it is still ahead, otherwise tomorrow.
 * - Impossible dates or times (31.2., 25 Uhr) or a weekday that does not fit the date give null,
 *   as does a text without date and time.
 */
object EventParser {
    const val MAX_INPUT = 200
    /** Largest day offset ("+N", "in N Tagen"). */
    const val MAX_OFFSET_DAYS = 3660

    /** A found date with an optional time; [title] are the remaining words. */
    data class Result(val date: CivilDate, val time: ClockTime?, val title: String) {
        /** Wall-clock start: the time, or midnight for an all-day date. */
        val start: LocalMoment get() = LocalMoment(date, time ?: ClockTime(0, 0))
    }

    fun parse(text: String, nowMillis: Long, zone: TimeZone, language: String): Result? =
        parse(text, LocalMoment.of(nowMillis, zone), language)

    /**
     * [language] is the keyboard language; it only decides 12/24 vs. 24/12 and "am" (German "am" is not a.m.).
     * [alarm] reads the text as an alarm time ([AlarmParser]): colloquial times stay in the morning
     * ("halb sieben" is 6:30, not 18:30) and a bare number is an hour ("7 Training").
     */
    fun parse(text: String, now: LocalMoment, language: String, alarm: Boolean = false): Result? {
        if (text.length > MAX_INPUT) return null
        val tokens = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }.map { Token(it, keyOf(it)) }
        if (tokens.isEmpty()) return null
        return Scan(tokens, now, language.lowercase().substringBefore('_').substringBefore('-'), alarm).run()
    }

    private val WHITESPACE = Regex("\\s+")

    private class Token(val raw: String, val key: String)

    private fun keyOf(raw: String): String =
        raw.lowercase().replace('’', '\'').trimStart('(', '"', '\'', '„', '“', '«').trimEnd(',', ';', '!', '?', ')', '"', '\'', '“', '”', '»', ':')

    private sealed interface DatePart
    private data class Exact(val date: CivilDate) : DatePart
    private data class YearLess(val month: Int, val day: Int) : DatePart
    private data class Offset(val days: Int) : DatePart

    private enum class Marker { AM, PM }
    private enum class Period { MORNING, NOON, AFTERNOON, EVENING, NIGHT }

    private class PendingTime(val hour: Int, val minute: Int, val colloquial: Boolean, var marker: Marker?)

    private val RELATIVE = mapOf(
        "heute" to 0, "today" to 0, "hoy" to 0,
        "morgen" to 1, "tomorrow" to 1, "mañana" to 1, "manana" to 1,
        "übermorgen" to 2, "uebermorgen" to 2,
        "gestern" to -1, "yesterday" to -1, "ayer" to -1,
        "vorgestern" to -2, "anteayer" to -2,
    )

    private val WEEKDAYS: Map<String, Int> = buildMap {
        listOf(
            listOf("montag", "monday", "lunes", "mon", "mo."),
            listOf("dienstag", "tuesday", "martes", "tue", "tues", "di."),
            listOf("mittwoch", "wednesday", "miércoles", "miercoles", "wed", "mi."),
            listOf("donnerstag", "thursday", "jueves", "thu", "thur", "thurs", "do."),
            listOf("freitag", "friday", "viernes", "fri", "fr."),
            listOf("samstag", "sonnabend", "saturday", "sábado", "sabado", "sat", "sa."),
            listOf("sonntag", "sunday", "domingo", "sun", "so."),
        ).forEachIndexed { index, names -> names.forEach { put(it, index + 1) } }
    }

    private val MONTHS: Map<String, Int> = buildMap {
        listOf(
            listOf("januar", "jänner", "january", "enero", "jan", "ene"),
            listOf("februar", "feber", "february", "febrero", "feb"),
            listOf("märz", "maerz", "march", "marzo", "mär", "mrz", "mar"),
            listOf("april", "abril", "apr", "abr"),
            listOf("mai", "may", "mayo"),
            listOf("juni", "june", "junio", "jun"),
            listOf("juli", "july", "julio", "jul"),
            listOf("august", "agosto", "aug", "ago"),
            listOf("september", "septiembre", "setiembre", "sep", "sept"),
            listOf("oktober", "october", "octubre", "okt", "oct"),
            listOf("november", "noviembre", "nov"),
            listOf("dezember", "december", "diciembre", "dez", "dec", "dic"),
        ).forEachIndexed { index, names -> names.forEach { put(it, index + 1) } }
    }

    private val HOUR_WORDS = mapOf(
        "ein" to 1, "eins" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "fuenf" to 5,
        "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "elf" to 11, "zwölf" to 12, "zwoelf" to 12,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
        "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
    )

    private val MINUTE_WORDS = mapOf(
        "fünf" to 5, "fuenf" to 5, "zehn" to 10, "zwanzig" to 20,
        "five" to 5, "ten" to 10, "twenty" to 20, "twenty-five" to 25,
        "cinco" to 5, "diez" to 10, "veinte" to 20, "veinticinco" to 25,
    )

    /** Small numbers for "in drei Tagen" / "in a week". */
    private val COUNT_WORDS = HOUR_WORDS + mapOf("einem" to 1, "einer" to 1, "eine" to 1, "a" to 1, "an" to 1, "un" to 1)

    /** Words between parts that belong to the date or time when a date or time follows them. */
    private val DATE_CONNECTORS = setOf(
        "am", "an", "on", "el", "the", "den", "dem", "für", "for", "para", "ab",
        "nächsten", "nächster", "nächste", "naechsten", "kommenden", "kommender", "diesen", "dieser",
        "this", "next", "coming", "próximo", "próxima", "proximo", "proxima", "este", "esta",
    )
    private val TIME_CONNECTORS = setOf("um", "at", "gegen", "ab", "a", "las", "la", "around", "ca.", "circa", "sobre", "hacia", "@")

    private val AM_WORDS = setOf("am", "a.m.", "a.m")
    private val PM_WORDS = setOf("pm", "p.m.", "p.m")

    private val SINGLE_PERIODS = mapOf(
        "morgens" to Period.MORNING, "früh" to Period.MORNING, "frueh" to Period.MORNING,
        "vormittags" to Period.MORNING, "vormittag" to Period.MORNING,
        "nachmittags" to Period.AFTERNOON, "nachmittag" to Period.AFTERNOON,
        "abends" to Period.EVENING, "abend" to Period.EVENING,
        "nachts" to Period.NIGHT, "nacht" to Period.NIGHT,
    )

    /** Multi-word periods; [today] when the phrase also names the day ("tonight"). */
    private class PeriodPhrase(val words: List<String>, val period: Period, val today: Boolean = false)

    private val PHRASES: List<PeriodPhrase> = buildList {
        for ((word, period) in listOf("morning" to Period.MORNING, "afternoon" to Period.AFTERNOON, "evening" to Period.EVENING)) {
            add(PeriodPhrase(listOf("in", "the", word), period))
            add(PeriodPhrase(listOf("this", word), period, today = true))
        }
        add(PeriodPhrase(listOf("at", "night"), Period.NIGHT))
        add(PeriodPhrase(listOf("tonight"), Period.EVENING, today = true))
        for ((word, period) in listOf("mañana" to Period.MORNING, "manana" to Period.MORNING, "madrugada" to Period.NIGHT,
            "tarde" to Period.AFTERNOON, "noche" to Period.EVENING)) {
            for (lead in listOf("de", "por", "en")) add(PeriodPhrase(listOf(lead, "la", word), period))
            if (word != "madrugada") add(PeriodPhrase(listOf("esta", word), period, today = true))
        }
        for ((word, period) in listOf("morgen" to Period.MORNING, "vormittag" to Period.MORNING,
            "nachmittag" to Period.AFTERNOON, "abend" to Period.EVENING)) add(PeriodPhrase(listOf("am", word), period))
    }.sortedByDescending { it.words.size }

    private val NOON_WORDS = setOf("mittag", "mittags", "noon", "midday", "mediodía", "mediodia")

    private val STRONG_DOT_DATE = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{2}|\\d{4})?$")
    private val WEAK_DOT = Regex("^(\\d{1,2})\\.(\\d{1,2})$")
    private val SLASH_DATE = Regex("^(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?$")
    private val ISO_DATE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")
    private val DAY_NUMBER = Regex("^(\\d{1,2})(?:\\.|st|nd|rd|th)?$")
    private val YEAR = Regex("^\\d{4}$")
    private val OFFSET = Regex("^([+-])(\\d{1,4})$")
    private val COLON_TIME = Regex("^(\\d{1,2}):(\\d{2})$")
    private val MARKED_TIME = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm|a\\.m\\.?|p\\.m\\.?)$")
    private val H_TIME = Regex("^(\\d{1,2})h(\\d{2})?$")
    private val NUMBER = Regex("^\\d{1,2}$")
    private val TWO_DIGITS = Regex("^\\d{2}$")

    private fun year(text: String?): Int? = when {
        text.isNullOrEmpty() -> null
        text.length == 2 -> 2000 + text.toInt()
        else -> text.toInt()
    }

    /** Thrown for something that looks like a date or time but cannot be one; the whole text then gives no result. */
    private class Impossible : Exception()

    private class Scan(val tokens: List<Token>, val now: LocalMoment, val language: String, val alarm: Boolean) {
        val used = BooleanArray(tokens.size)
        var date: DatePart? = null
        var weekday: Int? = null
        var time: PendingTime? = null
        var period: Period? = null
        /** Tokens of a period word; given back to the title if there is no time. */
        var periodTokens: IntRange? = null

        fun key(i: Int): String? = tokens.getOrNull(i)?.key

        fun run(): Result? = try {
            scan()
            resolve()
        } catch (_: Impossible) {
            null
        }

        private fun scan() {
            var i = 0
            while (i < tokens.size) {
                val end = matchPeriod(i).takeIf { it > 0 } ?: matchPart(i)
                if (end > i) {
                    for (k in i until end) used[k] = true
                    i = end
                } else i++
            }
        }

        private fun matchPeriod(i: Int): Int {
            if (period != null) return -1
            for (phrase in PHRASES) {
                if (phrase.words.indices.all { key(i + it) == phrase.words[it] }) {
                    period = phrase.period
                    if (phrase.today && date == null && weekday == null) date = Offset(0)
                    else periodTokens = i until i + phrase.words.size
                    return i + phrase.words.size
                }
            }
            val single = SINGLE_PERIODS[key(i)] ?: return -1
            period = single
            periodTokens = i..i
            return i + 1
        }

        /** Connector words, then a date or a time. */
        private fun matchPart(i: Int): Int {
            var j = i
            var timeIntroduced = false
            while (true) {
                val k = key(j) ?: return -1
                if (k in TIME_CONNECTORS) timeIntroduced = true else if (k !in DATE_CONNECTORS) break
                j++
            }
            if (!timeIntroduced) matchDate(j).let { if (it > 0) return it }
            matchTime(j, timeIntroduced).let { if (it > 0) return it }
            // "am 3.12. um 9:30": a time connector does not stop a date that clearly is one
            if (timeIntroduced) matchDate(j).let { if (it > 0) return it }
            return -1
        }

        private fun setDate(part: DatePart): Boolean {
            if (date != null) return false
            date = part
            return true
        }

        private fun matchDate(j: Int): Int {
            val k = key(j) ?: return -1
            RELATIVE[k]?.let { return if (setDate(Offset(it))) j + 1 else -1 }
            if (k == "pasado" && key(j + 1).let { it == "mañana" || it == "manana" }) return if (setDate(Offset(2))) j + 2 else -1
            if (k == "day" && key(j + 1) == "after" && key(j + 2) == "tomorrow") return if (setDate(Offset(2))) j + 3 else -1
            OFFSET.matchEntire(k)?.let { match ->
                val days = match.groupValues[2].toInt()
                if (days > MAX_OFFSET_DAYS) throw Impossible()
                return if (setDate(Offset(if (match.groupValues[1] == "-") -days else days))) j + 1 else -1
            }
            if (k == "in" || k == "en") matchInDays(j + 1)?.let { (days, end) -> return if (setDate(Offset(days))) end else -1 }
            WEEKDAYS[k]?.let {
                if (weekday != null) return -1
                weekday = it
                return j + 1
            }
            STRONG_DOT_DATE.matchEntire(k)?.let { match ->
                val (day, month) = match.groupValues[1].toInt() to match.groupValues[2].toInt()
                return if (setDate(datePart(day, month, year(match.groupValues[3])))) j + 1 else -1
            }
            WEAK_DOT.matchEntire(k)?.let { match ->
                // "3.12" is a date, "18.30 Uhr" a time
                if (key(j + 1) == "uhr" || key(j + 1) == "h") return -1
                val (day, month) = match.groupValues[1].toInt() to match.groupValues[2].toInt()
                if (month !in 1..12 || day !in 1..31) return -1
                return if (setDate(datePart(day, month, null))) j + 1 else -1
            }
            SLASH_DATE.matchEntire(k)?.let { match ->
                val (first, second) = match.groupValues[1].toInt() to match.groupValues[2].toInt()
                val (day, month) = if (language == "en") second to first else first to second
                return if (setDate(datePart(day, month, year(match.groupValues[3])))) j + 1 else -1
            }
            ISO_DATE.matchEntire(k)?.let { match ->
                val date = CivilDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
                    ?: throw Impossible()
                return if (setDate(Exact(date))) j + 1 else -1
            }
            // "24. Dezember (2026)", "24 de diciembre (de 2026)", "24th of December"
            DAY_NUMBER.matchEntire(k)?.let { match ->
                var m = j + 1
                if (key(m) == "de" || key(m) == "of") m++
                val month = MONTHS[key(m)?.trimEnd('.')] ?: return -1
                m++
                var year: Int? = null
                if (key(m) == "de" && key(m + 1)?.let { YEAR.matches(it) } == true) m++
                if (key(m)?.let { YEAR.matches(it) } == true) { year = key(m)!!.toInt(); m++ }
                return if (setDate(datePart(match.groupValues[1].toInt(), month, year))) m else -1
            }
            // "December 24th(, 2026)"
            MONTHS[k.trimEnd('.')]?.let { month ->
                val day = key(j + 1)?.let { DAY_NUMBER.matchEntire(it) } ?: return -1
                var m = j + 2
                var year: Int? = null
                if (key(m)?.let { YEAR.matches(it) } == true) { year = key(m)!!.toInt(); m++ }
                return if (setDate(datePart(day.groupValues[1].toInt(), month, year))) m else -1
            }
            return -1
        }

        /** "3 Tagen", "einer Woche", "2 weeks", "3 días" after "in"/"en". */
        private fun matchInDays(j: Int): Pair<Int, Int>? {
            val k = key(j) ?: return null
            val count = if (NUMBER.matches(k) || k.length in 3..4 && k.all { it.isDigit() }) k.toInt() else COUNT_WORDS[k] ?: return null
            val factor = when (key(j + 1)) {
                "tag", "tage", "tagen", "day", "days", "día", "días", "dia", "dias" -> 1
                "woche", "wochen", "week", "weeks", "semana", "semanas" -> 7
                else -> return null
            }
            val days = count * factor
            if (days > MAX_OFFSET_DAYS) throw Impossible()
            return days to j + 2
        }

        private fun datePart(day: Int, month: Int, year: Int?): DatePart {
            if (year == null) {
                if (month !in 1..12 || day !in 1..CivilDate.lengthOfMonth(2000, month)) throw Impossible() // 2000: leap year
                return YearLess(month, day)
            }
            return Exact(CivilDate.of(year, month, day) ?: throw Impossible())
        }

        private fun hourNumber(k: String?): Int? = when {
            k == null -> null
            NUMBER.matches(k) -> k.toInt()
            else -> HOUR_WORDS[k]
        }

        private fun minuteNumber(k: String?): Int? = when {
            k == null -> null
            NUMBER.matches(k) -> k.toInt().takeIf { it in 1..59 }
            else -> MINUTE_WORDS[k]
        }

        private fun isMarker(k: String?, pm: Boolean): Boolean =
            if (pm) k in PM_WORDS else k in AM_WORDS && (k != "am" || language != "de")

        private fun setTime(hour: Int, minute: Int, colloquial: Boolean, marker: Marker? = null): Boolean {
            if (time != null) return false
            if (minute !in 0..59) throw Impossible()
            if (marker != null && hour !in 1..12) throw Impossible()
            if (hour !in 0..23) throw Impossible()
            time = PendingTime(hour, minute, colloquial, marker)
            return true
        }

        /** Hour before "halb"/"viertel …"/"… to N": "halb acht" is 7:30, "halb eins" 12:30. */
        private fun before(hour: Int) = if (hour <= 1) 12 else hour - 1

        private fun matchTime(j: Int, introduced: Boolean): Int {
            val end = matchTimeBase(j, introduced)
            if (end < 0) return end
            var e = end
            if (key(e) == "uhr") e++
            val pending = time!!
            if (pending.marker == null) {
                val marker = when {
                    isMarker(key(e), pm = false) -> Marker.AM
                    isMarker(key(e), pm = true) -> Marker.PM
                    else -> null
                }
                if (marker != null) {
                    if (pending.hour !in 1..12) throw Impossible()
                    pending.marker = marker
                    e++
                }
            }
            return e
        }

        private fun matchTimeBase(j: Int, introduced: Boolean): Int {
            val k = key(j) ?: return -1
            fun hourAt(i: Int) = hourNumber(key(i))?.takeIf { it in 1..12 }
            when (k) {
                "halb" -> hourAt(j + 1)?.let { return if (setTime(before(it), 30, true)) j + 2 else -1 }
                "viertel" -> {
                    when (key(j + 1)) {
                        "nach" -> hourAt(j + 2)?.let { return if (setTime(it, 15, true)) j + 3 else -1 }
                        "vor" -> hourAt(j + 2)?.let { return if (setTime(before(it), 45, true)) j + 3 else -1 }
                        else -> hourAt(j + 1)?.let { return if (setTime(before(it), 15, true)) j + 2 else -1 }
                    }
                }
                "dreiviertel" -> hourAt(j + 1)?.let { return if (setTime(before(it), 45, true)) j + 2 else -1 }
                "half" -> if (key(j + 1) == "past") hourAt(j + 2)?.let { return if (setTime(it, 30, true)) j + 3 else -1 }
                "quarter" -> when (key(j + 1)) {
                    "past", "after" -> hourAt(j + 2)?.let { return if (setTime(it, 15, true)) j + 3 else -1 }
                    "to", "before" -> hourAt(j + 2)?.let { return if (setTime(before(it), 45, true)) j + 3 else -1 }
                }
            }
            if (k in NOON_WORDS) return if (setTime(12, 0, false)) j + 1 else -1
            // "zehn nach acht", "5 vor 8", "ten past six", "twenty to nine"
            minuteNumber(k)?.let { minutes ->
                if (minutes < 30) {
                    when (key(j + 1)) {
                        "nach", "past", "after" -> hourAt(j + 2)?.let { return if (setTime(it, minutes, true)) j + 3 else -1 }
                        "vor", "to", "before" -> hourAt(j + 2)?.let { return if (setTime(before(it), 60 - minutes, true)) j + 3 else -1 }
                    }
                }
            }
            COLON_TIME.matchEntire(k)?.let { return if (setTime(it.groupValues[1].toInt(), it.groupValues[2].toInt(), false)) j + 1 else -1 }
            WEAK_DOT.matchEntire(k)?.let {
                if (!introduced && key(j + 1) != "uhr" && key(j + 1) != "h") return -1
                return if (setTime(it.groupValues[1].toInt(), it.groupValues[2].toInt(), false)) j + 1 else -1
            }
            MARKED_TIME.matchEntire(k)?.let {
                val marker = if (it.groupValues[3].startsWith("a")) Marker.AM else Marker.PM
                val minute = it.groupValues[2].ifEmpty { "0" }.toInt()
                return if (setTime(it.groupValues[1].toInt(), minute, false, marker)) j + 1 else -1
            }
            H_TIME.matchEntire(k)?.let {
                return if (setTime(it.groupValues[1].toInt(), it.groupValues[2].ifEmpty { "0" }.toInt(), false)) j + 1 else -1
            }
            val hour = hourNumber(k) ?: return -1
            val word = !NUMBER.matches(k)
            val next = key(j + 1)
            return when {
                next == "uhr" -> {
                    // "18 Uhr 30"
                    val minutes = key(j + 2)?.takeIf { TWO_DIGITS.matches(it) }?.toInt()
                    if (setTime(hour, minutes ?: 0, colloquial = word)) j + if (minutes != null) 3 else 2 else -1
                }
                next == "h" -> if (setTime(hour, 0, colloquial = false)) j + 2 else -1
                next == "o'clock" || next == "oclock" -> if (setTime(hour, 0, colloquial = true)) j + 2 else -1
                isMarker(next, pm = false) || isMarker(next, pm = true) -> if (setTime(hour, 0, colloquial = false)) j + 1 else -1
                next == "y" && key(j + 2) == "media" -> if (setTime(hour, 30, true)) j + 3 else -1
                next == "y" && key(j + 2) == "cuarto" -> if (setTime(hour, 15, true)) j + 3 else -1
                next == "menos" && key(j + 2) == "cuarto" -> if (setTime(before(hour), 45, true)) j + 3 else -1
                next == "y" && minuteNumber(key(j + 2)) != null && introduced ->
                    if (setTime(hour, minuteNumber(key(j + 2))!!, true)) j + 3 else -1
                introduced || alarm && !word -> if (setTime(hour, 0, colloquial = true)) j + 1 else -1
                else -> -1
            }
        }

        private fun resolveTime(): ClockTime? {
            val pending = time ?: return null
            var hour = pending.hour
            val marker = pending.marker
            val period = period
            when {
                marker == Marker.AM -> if (hour == 12) hour = 0
                marker == Marker.PM -> if (hour < 12) hour += 12
                period == Period.AFTERNOON || period == Period.EVENING -> if (hour in 1..11) hour += 12
                period == Period.NIGHT -> if (hour in 6..11) hour += 12
                period == Period.NOON -> if (hour in 1..5) hour += 12
                period == Period.MORNING -> {}
                pending.colloquial -> if (!alarm && hour in 1..7) hour += 12
            }
            return ClockTime(hour, pending.minute)
        }

        private fun resolve(): Result? {
            val clock = resolveTime()
            val today = now.date
            var day: CivilDate? = when (val part = date) {
                is Exact -> part.date
                is Offset -> today.plusDays(part.days.toLong())
                is YearLess -> (0..8).asSequence().mapNotNull { CivilDate.of(today.year + it, part.month, part.day) }
                    .firstOrNull { it >= today } ?: throw Impossible()
                null -> null
            }
            val wanted = weekday
            if (day == null && wanted != null) {
                var ahead = Math.floorMod(wanted - today.dayOfWeek, 7)
                if (ahead == 0 && clock != null && clock <= now.time) ahead = 7
                day = today.plusDays(ahead.toLong())
            }
            if (wanted != null && day!!.dayOfWeek != wanted) return null
            if (day == null) {
                if (clock == null) return null
                day = if (clock > now.time) today else today.plusDays(1)
            }
            if (clock == null) periodTokens?.forEach { used[it] = false } // "heute Abend Kino" without a time keeps "Abend"
            val title = tokens.indices.filter { !used[it] }.joinToString(" ") { tokens[it].raw }
                .trim { it.isWhitespace() || it in TITLE_TRIM }
            return Result(day, clock, title)
        }
    }

    private const val TITLE_TRIM = ",;:-–—"
}
