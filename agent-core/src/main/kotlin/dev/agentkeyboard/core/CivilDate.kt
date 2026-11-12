// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * A calendar date without a time zone (stage 24f, `@datum` and `@termin`). The app's minSdk is 21
 * and core library desugaring is not enabled, so `java.time` is not available on all devices;
 * this small type does the date arithmetic with plain integers (proleptic Gregorian calendar).
 */
data class CivilDate(val year: Int, val month: Int, val day: Int) : Comparable<CivilDate> {
    init {
        require(isValid(year, month, day)) { "invalid date" }
    }

    /** Days since 1970-01-01. */
    val epochDay: Long get() = daysFromCivil(year, month, day)

    /** 1 = Monday … 7 = Sunday. */
    val dayOfWeek: Int get() = Math.floorMod(epochDay + 3, 7L).toInt() + 1

    fun plusDays(days: Long): CivilDate = ofEpochDay(epochDay + days)

    override fun compareTo(other: CivilDate) = epochDay.compareTo(other.epochDay)

    companion object {
        const val MIN_YEAR = 1
        const val MAX_YEAR = 9999

        fun isLeapYear(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        fun lengthOfMonth(year: Int, month: Int) = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        fun isValid(year: Int, month: Int, day: Int) =
            year in MIN_YEAR..MAX_YEAR && month in 1..12 && day in 1..lengthOfMonth(year, month)

        /** The date, or null if it does not exist (31.2.). */
        fun of(year: Int, month: Int, day: Int): CivilDate? = if (isValid(year, month, day)) CivilDate(year, month, day) else null

        // Howard Hinnant's days_from_civil / civil_from_days
        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = Math.floorDiv(y, 400L)
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097 + doe - 719468
        }

        fun ofEpochDay(epochDay: Long): CivilDate {
            val z = epochDay + 719468
            val era = Math.floorDiv(z, 146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
            return CivilDate(year, month, day)
        }
    }
}

/** A wall-clock time of day, minute precision. */
data class ClockTime(val hour: Int, val minute: Int) : Comparable<ClockTime> {
    init {
        require(hour in 0..23 && minute in 0..59) { "invalid time" }
    }

    override fun compareTo(other: ClockTime) = compareValuesBy(this, other, { it.hour }, { it.minute })
}

/** A date and time on the wall clock of some zone. */
data class LocalMoment(val date: CivilDate, val time: ClockTime) : Comparable<LocalMoment> {
    override fun compareTo(other: LocalMoment) = compareValuesBy(this, other, { it.date }, { it.time })

    /** The instant this wall-clock time denotes in [zone] (the zone's rules decide gaps and overlaps). */
    fun toEpochMillis(zone: TimeZone): Long = calendar(zone).run {
        clear()
        set(date.year, date.month - 1, date.day, this@LocalMoment.time.hour, this@LocalMoment.time.minute, 0)
        timeInMillis
    }

    companion object {
        /** Purely Gregorian, like [CivilDate], also before 1582. */
        private fun calendar(zone: TimeZone) = GregorianCalendar(zone).apply { gregorianChange = java.util.Date(Long.MIN_VALUE) }

        /** The wall-clock time in [zone] at [epochMillis]. */
        fun of(epochMillis: Long, zone: TimeZone): LocalMoment = calendar(zone).run {
            timeInMillis = epochMillis
            LocalMoment(
                CivilDate(get(GregorianCalendar.YEAR), get(GregorianCalendar.MONTH) + 1, get(GregorianCalendar.DAY_OF_MONTH)),
                ClockTime(get(GregorianCalendar.HOUR_OF_DAY), get(GregorianCalendar.MINUTE)),
            )
        }
    }
}
