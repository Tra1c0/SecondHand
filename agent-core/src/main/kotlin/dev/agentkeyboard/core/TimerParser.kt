// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Reads the duration of `@timer 10 min Nudeln` in German, English and Spanish. The words that are
 * not part of the duration are the timer's label. Pure and offline.
 *
 * Rules:
 * - A part is a number with a unit: "10 min", "10min", "90 s", "1,5 h", "2 Stunden", "zehn Minuten",
 *   "eine Stunde", "an hour", "una hora", "anderthalb Stunden". Units: h/Std/Stunde(n)/hour(s)/hr(s)/
 *   hora(s), m/min/Minute(n)/minute(s)/minuto(s), s/sek/sec/seg/Sekunde(n)/second(s)/segundo(s).
 * - Halves: "halbe Stunde", "eine halbe Stunde", "half an hour", "half a minute", "media hora";
 *   "eine Stunde und halb", "an hour and a half", "una hora y media" add half the unit.
 * - Parts follow each other, optionally joined by und/and/y: "1 h 20 min", "2 min 30 s". A bare
 *   number right after a part counts in the next smaller unit: "1 h 20" is 1:20:00, "2 min 30" 2:30.
 * - "1:30" is minutes and seconds, "1:30:00" hours, minutes and seconds; "1:30 h" hours and
 *   minutes, "1:30 min" minutes and seconds.
 * - A bare number at the start counts in minutes: "@timer 10 Nudeln".
 * - Each unit once. The duration is one run of words; everything else is the title.
 * - Null without a duration, for zero, more than [MAX_SECONDS], leftover letters glued to a
 *   duration ("10minx") or input over [MAX_INPUT] characters.
 */
object TimerParser {
    const val MAX_INPUT = 200
    /** The clock app's limit for a timer: 24 hours. */
    const val MAX_SECONDS = 24 * 60 * 60

    /** The length in whole seconds and the remaining words. */
    data class Result(val seconds: Int, val title: String)

    /** "10:00" for ten minutes, "1:20:00" for an hour and twenty minutes. */
    fun format(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }

    fun parse(text: String): Result? {
        if (text.length > MAX_INPUT) return null
        val raws = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (raws.isEmpty()) return null
        val pieces = mutableListOf<Piece>()
        raws.forEachIndexed { index, raw ->
            val key = keyOf(raw)
            if (GLUED.matches(key) && !NUMBER.matches(key)) PIECE.findAll(key).forEach { pieces += Piece(it.value, index) }
            else pieces += Piece(key, index)
        }
        return Scan(pieces).run(raws)
    }

    private val WHITESPACE = Regex("\\s+")
    /** "10min", "1h20", "1h30m", "1,5h": digits and letters glued together. */
    private val GLUED = Regex("^(\\d{1,5}(?:[.,]\\d{1,3})?[a-zäöüß.]+)+(\\d{1,5})?$")
    private val PIECE = Regex("\\d+(?:[.,]\\d+)?|[^\\d]+")
    private val NUMBER = Regex("^\\d{1,5}(?:[.,]\\d{1,3})?$")
    private val COLON = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$")

    private class Piece(val key: String, val raw: Int)

    private fun keyOf(raw: String): String =
        raw.lowercase().replace('’', '\'').trimStart('(', '"', '\'', '„', '“', '«')
            .trimEnd(',', ';', '!', '?', ')', '"', '\'', '“', '”', '»', ':')

    private const val HOUR = 3600
    private const val MINUTE = 60
    private const val SECOND = 1

    private val UNITS: Map<String, Int> = buildMap {
        listOf("h", "hr", "hrs", "std", "stunde", "stunden", "hour", "hours", "hora", "horas").forEach { put(it, HOUR) }
        listOf("m", "min", "mins", "minute", "minuten", "minutes", "minuto", "minutos").forEach { put(it, MINUTE) }
        listOf("s", "sek", "sec", "secs", "seg", "segs", "sekunde", "sekunden", "second", "seconds", "segundo", "segundos")
            .forEach { put(it, SECOND) }
    }

    private val NUMBER_WORDS: Map<String, Double> = buildMap {
        listOf("ein", "eins", "eine", "einen", "einer", "a", "an", "one", "un", "una", "uno").forEach { put(it, 1.0) }
        listOf("anderthalb", "eineinhalb").forEach { put(it, 1.5) }
        put("zweieinhalb", 2.5)
        listOf(
            listOf("zwei", "two", "dos") to 2, listOf("drei", "three", "tres") to 3, listOf("vier", "four", "cuatro") to 4,
            listOf("fünf", "fuenf", "five", "cinco") to 5, listOf("sechs", "six", "seis") to 6,
            listOf("sieben", "seven", "siete") to 7, listOf("acht", "eight", "ocho") to 8, listOf("neun", "nine", "nueve") to 9,
            listOf("zehn", "ten", "diez") to 10, listOf("elf", "eleven", "once") to 11, listOf("zwölf", "zwoelf", "twelve", "doce") to 12,
            listOf("fünfzehn", "fuenfzehn", "fifteen", "quince") to 15, listOf("zwanzig", "twenty", "veinte") to 20,
            listOf("dreißig", "dreissig", "thirty", "treinta") to 30, listOf("vierzig", "forty", "cuarenta") to 40,
            listOf("fünfundvierzig", "fuenfundvierzig", "forty-five") to 45, listOf("sechzig", "sixty", "sesenta") to 60,
            listOf("neunzig", "ninety", "noventa") to 90,
        ).forEach { (words, value) -> words.forEach { put(it, value.toDouble()) } }
    }

    private val HALF = setOf("halb", "halbe", "halben", "half", "media", "medio")
    private val ARTICLES = setOf("eine", "einer", "ne", "a", "an", "una", "un")
    private val JOINERS = setOf("und", "and", "y", "&", "+")
    private val LEADS = setOf("für", "fuer", "for", "por", "durante", "in", "en")

    private fun unit(key: String?): Int? = key?.let { UNITS[it.trimEnd('.')] }

    private fun number(key: String?): Double? = when {
        key == null -> null
        NUMBER.matches(key) -> key.replace(',', '.').toDouble()
        else -> NUMBER_WORDS[key]
    }

    private class Scan(val pieces: List<Piece>) {
        val used = BooleanArray(pieces.size)
        var total = 0.0
        val units = mutableSetOf<Int>()

        fun key(i: Int) = pieces.getOrNull(i)?.key

        fun run(raws: List<String>): Result? {
            var found = false
            for (start in pieces.indices) {
                val end = group(start)
                if (end > start) {
                    for (k in start until end) used[k] = true
                    found = true
                    break
                }
                total = 0.0
                units.clear()
            }
            if (!found) return null
            // a raw word is either all duration or all title
            val usedRaw = BooleanArray(raws.size)
            for ((i, piece) in pieces.withIndex()) {
                if (used[i]) usedRaw[piece.raw] = true
            }
            for ((i, piece) in pieces.withIndex()) if (usedRaw[piece.raw] && !used[i]) return null
            val seconds = Math.round(total)
            if (seconds < 1 || seconds > MAX_SECONDS) return null
            val title = raws.indices.filter { !usedRaw[it] }.joinToString(" ") { raws[it] }
                .trim { it.isWhitespace() || it in TITLE_TRIM }
            return Result(seconds.toInt(), title)
        }

        /** The end of a duration starting at [start], or -1. */
        private fun group(start: Int): Int {
            var i = start
            // "für 10 min", "for an hour": a lead word only before a duration
            if (key(i) in LEADS) i++
            val first = i
            var end = tryPart(i, lastUnit = null)
            if (end < 0) {
                // a bare number at the start of the text is minutes ("10 Nudeln", not "2x")
                if (first != (if (key(0) in LEADS) 1 else 0)) return -1
                val k = key(first) ?: return -1
                if (!NUMBER.matches(k) || !endsWord(first) || first > 0 && pieces[first - 1].raw == pieces[first].raw) return -1
                total += k.replace(',', '.').toDouble() * MINUTE
                return first + 1
            }
            var lastUnit = lastUnitOf
            while (true) {
                var next = end
                if (key(next) in JOINERS) next++
                val after = tryPart(next, lastUnit)
                if (after < 0) break
                end = after
                lastUnit = lastUnitOf
            }
            return end
        }

        private var lastUnitOf = 0

        /** True if piece [i] is the last piece of its word. */
        private fun endsWord(i: Int) = i + 1 >= pieces.size || pieces[i + 1].raw != pieces[i].raw

        /** [part] that leaves nothing behind when it fails halfway ("20 min 1:30:00"). */
        private fun tryPart(i: Int, lastUnit: Int?): Int {
            val savedTotal = total
            val savedUnits = units.toSet()
            val savedLast = lastUnitOf
            val end = part(i, lastUnit)
            if (end < 0) {
                total = savedTotal
                units.clear()
                units += savedUnits
                lastUnitOf = savedLast
            }
            return end
        }

        private fun add(value: Double, unit: Int): Boolean {
            if (unit in units) return false
            units += unit
            total += value * unit
            lastUnitOf = unit
            return true
        }

        /** One part at [i]; [lastUnit] lets a bare number count in the next smaller unit. */
        private fun part(i: Int, lastUnit: Int?): Int {
            val k = key(i) ?: return -1
            COLON.matchEntire(k)?.let { match ->
                val (a, b) = match.groupValues[1].toInt() to match.groupValues[2].toInt()
                val c = match.groupValues[3]
                if (b > 59) return -1
                if (c.isNotEmpty()) {
                    if (c.toInt() > 59) return -1
                    return if (add(a.toDouble(), HOUR) && add(b.toDouble(), MINUTE) && add(c.toDouble(), SECOND)) i + 1 else -1
                }
                return when (unit(key(i + 1))) {
                    HOUR -> if (add(a.toDouble(), HOUR) && add(b.toDouble(), MINUTE)) i + 2 else -1
                    MINUTE -> if (add(a.toDouble(), MINUTE) && add(b.toDouble(), SECOND)) i + 2 else -1
                    else -> if (add(a.toDouble(), MINUTE) && add(b.toDouble(), SECOND)) i + 1 else -1
                }
            }
            // "halbe Stunde", "eine halbe Stunde", "half an hour", "media hora"
            run {
                var j = i
                if (key(j) in ARTICLES && key(j + 1) in HALF) j++
                if (key(j) in HALF) {
                    var u = j + 1
                    if (key(u) in ARTICLES) u++
                    val unit = unit(key(u)) ?: return@run
                    return if (add(0.5, unit)) u + 1 else -1
                }
            }
            val value = number(k) ?: return -1
            val unit = unit(key(i + 1))
            if (unit == null) {
                // "1 h 20", "2 min 30": the next smaller unit, whole numbers below 60 only
                val smaller = when (lastUnit) { HOUR -> MINUTE; MINUTE -> SECOND; else -> return -1 }
                if (!NUMBER.matches(k) || !endsWord(i) || value != Math.floor(value) || value >= 60) return -1
                return if (add(value, smaller)) i + 1 else -1
            }
            if (!add(value, unit)) return -1
            var end = i + 2
            // "eine Stunde und halb", "an hour and a half", "una hora y media"
            var j = end
            if (key(j) in JOINERS) {
                j++
                if (key(j) in ARTICLES && key(j + 1) in HALF) j++
                if (key(j) in HALF && unit(key(j + 1)) == null) {
                    total += 0.5 * unit
                    end = j + 1
                }
            }
            return end
        }
    }

    private const val TITLE_TRIM = ",;:-–—"
}
