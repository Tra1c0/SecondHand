// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Date and time texts for `@datum` and the `@termin` chip (stage 24f) in German, English (US
 * order) and Spanish. Names and patterns are fixed here instead of coming from the platform's
 * locale data, so the output is the same on every device and in tests. Other keyboard languages
 * get the English texts.
 */
object DateFormatting {
    enum class Language(
        val weekdays: List<String>,
        val shortWeekdays: List<String>,
        val months: List<String>,
    ) {
        DE(
            listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"),
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"),
            listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"),
        ),
        EN(
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
        ),
        ES(
            listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"),
            listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom"),
            listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"),
        );

        companion object {
            /** [language] is a code like "de" or "de_CH"; anything else than de/es is English. */
            fun of(language: String): Language = when (language.lowercase().substringBefore('_').substringBefore('-')) {
                "de" -> DE
                "es" -> ES
                else -> EN
            }
        }
    }

    private fun two(value: Int) = value.toString().padStart(2, '0')

    /** 12.11.2026 / 11/12/2026 / 12/11/2026 */
    fun numeric(date: CivilDate, language: Language): String = when (language) {
        Language.DE -> "${two(date.day)}.${two(date.month)}.${date.year}"
        Language.EN -> "${two(date.month)}/${two(date.day)}/${date.year}"
        Language.ES -> "${two(date.day)}/${two(date.month)}/${date.year}"
    }

    /** Donnerstag, 12. November 2026 / Thursday, November 12, 2026 / jueves, 12 de noviembre de 2026 */
    fun long(date: CivilDate, language: Language): String {
        val weekday = language.weekdays[date.dayOfWeek - 1]
        val month = language.months[date.month - 1]
        return when (language) {
            Language.DE -> "$weekday, ${date.day}. $month ${date.year}"
            Language.EN -> "$weekday, $month ${date.day}, ${date.year}"
            Language.ES -> "$weekday, ${date.day} de $month de ${date.year}"
        }
    }

    /** 2026-11-12 in every language. */
    fun iso(date: CivilDate): String = "${date.year.toString().padStart(4, '0')}-${two(date.month)}-${two(date.day)}"

    /** 18:45 / 6:45 PM / 18:45 */
    fun time(time: ClockTime, language: Language): String = when (language) {
        Language.EN -> {
            val hour = (time.hour + 11) % 12 + 1
            "$hour:${two(time.minute)} ${if (time.hour < 12) "AM" else "PM"}"
        }
        else -> "${two(time.hour)}:${two(time.minute)}"
    }

    /** 12.11.2026, 18:45 */
    fun dateTime(date: CivilDate, time: ClockTime, language: Language) = "${numeric(date, language)}, ${time(time, language)}"

    /**
     * The texts `@datum` offers, in this order: numeric, long, ISO, and with a [time] also the
     * time and numeric date with time.
     */
    fun variants(date: CivilDate, time: ClockTime?, language: Language): List<String> {
        val texts = mutableListOf(numeric(date, language), long(date, language), iso(date))
        if (time != null) {
            texts += time(time, language)
            texts += dateTime(date, time, language)
        }
        return texts
    }

    /** Short form for a chip: "Sa 14.11. 19:30" / "Sat 11/14 7:30 PM" / "sáb 14/11 19:30"; without time only the date. */
    fun short(date: CivilDate, time: ClockTime?, language: Language): String {
        val weekday = language.shortWeekdays[date.dayOfWeek - 1]
        val day = when (language) {
            Language.DE -> "$weekday ${two(date.day)}.${two(date.month)}."
            Language.EN -> "$weekday ${date.month}/${date.day}"
            Language.ES -> "$weekday ${two(date.day)}/${two(date.month)}"
        }
        return if (time == null) day else "$day ${time(time, language)}"
    }
}
