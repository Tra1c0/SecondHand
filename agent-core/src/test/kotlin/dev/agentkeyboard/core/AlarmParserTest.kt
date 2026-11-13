// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class AlarmParserTest {
    /** Thursday, 12 November 2026, 18:45. */
    private val now = LocalMoment(CivilDate(2026, 11, 12), ClockTime(18, 45))

    private fun assertAlarm(text: String, hour: Int, minute: Int, title: String = "", dayIgnored: Boolean = false, language: String = "de") {
        val result = assertNotNull(AlarmParser.parse(text, now, language), text)
        assertEquals(ClockTime(hour, minute), result.time, text)
        assertEquals(title, result.title, text)
        assertEquals(dayIgnored, result.dayIgnored, text)
    }

    @Test fun examplesFromTheOrder() {
        assertAlarm("7:30", 7, 30)
        assertAlarm("7 Uhr", 7, 0)
        assertAlarm("halb acht", 7, 30)
        assertAlarm("6:45 morgen", 6, 45)
        assertAlarm("6:30 Training", 6, 30, "Training")
    }

    @Test fun alarmTimesStayInTheMorningUnlessSaidOtherwise() {
        assertAlarm("7", 7, 0)
        assertAlarm("7 Training", 7, 0, "Training")
        assertAlarm("um 7", 7, 0)
        assertAlarm("viertel nach sechs Laufen", 6, 15, "Laufen")
        assertAlarm("abends halb acht", 19, 30)
        assertAlarm("19:30", 19, 30)
        assertAlarm("6pm", 18, 0, language = "en")
        assertAlarm("6:30 am run", 6, 30, "run", language = "en")
        assertAlarm("a las siete y media", 7, 30, language = "es")
        // the plain event parser keeps reading colloquial times as afternoon or evening
        assertEquals(ClockTime(19, 30), EventParser.parse("halb acht Kino", now, "de")!!.time)
        assertNull(EventParser.parse("7 Training", now, "de")) // a bare number is no event time
    }

    @Test fun aDayThatIsNotTheNextOccurrenceIsReportedAsIgnored() {
        // 7:00 is next tomorrow (now is 18:45): "morgen" fits
        assertAlarm("morgen 7", 7, 0)
        // 20:00 is next today: "heute" fits, "morgen" cannot be set
        assertAlarm("heute 20 Uhr", 20, 0)
        assertAlarm("morgen 20 Uhr", 20, 0, dayIgnored = true)
        assertAlarm("Montag 7:00", 7, 0, dayIgnored = true)
        assertAlarm("24.12. 7:00 Bescherung", 7, 0, "Bescherung", dayIgnored = true)
        assertEquals(CivilDate(2026, 11, 13), AlarmParser.nextDay(ClockTime(18, 45), now))
        assertEquals(CivilDate(2026, 11, 12), AlarmParser.nextDay(ClockTime(18, 46), now))
    }

    @Test fun invalidInputGivesNull() {
        for (text in listOf("", "Training", "morgen", "Montag Training", "25 Uhr", "7:61", "31.2. 7:00", "x".repeat(EventParser.MAX_INPUT + 1))) {
            assertNull(AlarmParser.parse(text, now, "de"), text)
        }
    }
}
