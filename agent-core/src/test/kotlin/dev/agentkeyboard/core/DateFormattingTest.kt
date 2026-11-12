// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.DateFormatting.Language
import org.junit.Test
import java.util.TimeZone
import kotlin.test.*

class DateFormattingTest {
    private val day = CivilDate(2026, 11, 12)
    private val time = ClockTime(18, 45)

    @Test fun germanVariants() {
        assertEquals(
            listOf("12.11.2026", "Donnerstag, 12. November 2026", "2026-11-12", "18:45", "12.11.2026, 18:45"),
            DateFormatting.variants(day, time, Language.DE),
        )
        assertEquals(listOf("12.11.2026", "Donnerstag, 12. November 2026", "2026-11-12"), DateFormatting.variants(day, null, Language.DE))
        assertEquals("Mittwoch, 3. März 2027", DateFormatting.long(CivilDate(2027, 3, 3), Language.DE))
        assertEquals("03.03.2027", DateFormatting.numeric(CivilDate(2027, 3, 3), Language.DE))
    }

    @Test fun englishVariants() {
        assertEquals(
            listOf("11/12/2026", "Thursday, November 12, 2026", "2026-11-12", "6:45 PM", "11/12/2026, 6:45 PM"),
            DateFormatting.variants(day, time, Language.EN),
        )
        assertEquals("12:05 AM", DateFormatting.time(ClockTime(0, 5), Language.EN))
        assertEquals("12:00 PM", DateFormatting.time(ClockTime(12, 0), Language.EN))
        assertEquals("9:30 AM", DateFormatting.time(ClockTime(9, 30), Language.EN))
    }

    @Test fun spanishVariants() {
        assertEquals(
            listOf("12/11/2026", "jueves, 12 de noviembre de 2026", "2026-11-12", "18:45", "12/11/2026, 18:45"),
            DateFormatting.variants(day, time, Language.ES),
        )
    }

    @Test fun shortChipTexts() {
        val saturday = CivilDate(2026, 11, 14)
        assertEquals("Sa 14.11. 19:30", DateFormatting.short(saturday, ClockTime(19, 30), Language.DE))
        assertEquals("Sa 14.11.", DateFormatting.short(saturday, null, Language.DE))
        assertEquals("Sat 11/14 7:30 PM", DateFormatting.short(saturday, ClockTime(19, 30), Language.EN))
        assertEquals("sáb 14/11 19:30", DateFormatting.short(saturday, ClockTime(19, 30), Language.ES))
    }

    @Test fun languageCodes() {
        assertEquals(Language.DE, Language.of("de"))
        assertEquals(Language.DE, Language.of("de_CH"))
        assertEquals(Language.ES, Language.of("es-MX"))
        assertEquals(Language.EN, Language.of("en"))
        assertEquals(Language.EN, Language.of("fr"))
        assertEquals(Language.EN, Language.of(""))
    }

    @Test fun civilDateArithmetic() {
        assertEquals(4, CivilDate(2026, 11, 12).dayOfWeek) // Thursday
        assertEquals(4, CivilDate(1970, 1, 1).dayOfWeek)
        assertEquals(0L, CivilDate(1970, 1, 1).epochDay)
        assertEquals(CivilDate(2027, 1, 1), CivilDate(2026, 12, 31).plusDays(1))
        assertEquals(CivilDate(2028, 2, 29), CivilDate(2028, 2, 28).plusDays(1))
        assertEquals(CivilDate(2100, 3, 1), CivilDate(2100, 2, 28).plusDays(1))
        assertEquals(CivilDate(2000, 2, 29), CivilDate(2000, 3, 1).plusDays(-1))
        for (epochDay in -719_000L..2_900_000L step 997) assertEquals(epochDay, CivilDate.ofEpochDay(epochDay).epochDay)
        assertNull(CivilDate.of(2026, 2, 29))
        assertNull(CivilDate.of(2026, 13, 1))
        assertFailsWith<IllegalArgumentException> { ClockTime(24, 0) }
    }

    @Test fun localMomentUsesTheZone() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val moment = LocalMoment(CivilDate(2026, 11, 14), ClockTime(19, 30))
        assertEquals(1_794_681_000_000L, moment.toEpochMillis(berlin)) // 18:30 UTC
        assertEquals(moment, LocalMoment.of(1_794_681_000_000L, berlin))
        // summer time: 1 July 2026 12:00 in Berlin is 10:00 UTC
        assertEquals(1_782_900_000_000L, LocalMoment(CivilDate(2026, 7, 1), ClockTime(12, 0)).toEpochMillis(berlin))
    }
}
