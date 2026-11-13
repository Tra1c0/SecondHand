// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class TimerParserTest {
    private fun assertTimer(text: String, seconds: Int, title: String = "") {
        val result = assertNotNull(TimerParser.parse(text), text)
        assertEquals(seconds, result.seconds, text)
        assertEquals(title, result.title, text)
    }

    @Test fun examplesFromTheOrder() {
        assertTimer("10 min", 600)
        assertTimer("10 min Nudeln", 600, "Nudeln")
        assertTimer("90 s", 90)
        assertTimer("1:30", 90)
        assertTimer("1 h 20", 4800)
        assertTimer("eine Stunde", 3600)
        assertTimer("halbe Stunde", 1800)
    }

    @Test fun unitsInThreeLanguagesAndGluedForms() {
        assertTimer("10min", 600)
        assertTimer("10 Minuten Tee", 600, "Tee")
        assertTimer("zehn Minuten", 600)
        assertTimer("2 Std.", 7200)
        assertTimer("45 Sekunden", 45)
        assertTimer("90s", 90)
        assertTimer("1h20", 4800)
        assertTimer("1h30m", 5400)
        assertTimer("1,5 h", 5400)
        assertTimer("2.5 min", 150)
        assertTimer("anderthalb Stunden", 5400)
        assertTimer("5 minutes eggs", 300, "eggs")
        assertTimer("an hour", 3600)
        assertTimer("half an hour", 1800)
        assertTimer("twenty seconds", 20)
        assertTimer("an hour and a half", 5400)
        assertTimer("10 minutos pasta", 600, "pasta")
        assertTimer("media hora", 1800)
        assertTimer("una hora y media", 5400)
        assertTimer("30 segundos", 30)
        assertTimer("eine halbe Stunde Pause", 1800, "Pause")
        assertTimer("eine Stunde und halb", 5400)
    }

    @Test fun severalPartsAndBareNumbers() {
        assertTimer("1 h 20 min", 4800)
        assertTimer("2 min 30", 150)
        assertTimer("2 min und 30 s", 150)
        assertTimer("1 hora y 20", 4800)
        assertTimer("1:30:00", 5400)
        assertTimer("1:30 h", 5400)
        assertTimer("1:30 min", 90)
        assertTimer("10 Nudeln", 600, "Nudeln")
        assertTimer("für 10 min", 600)
        assertTimer("Nudeln 8 min", 480, "Nudeln")
        assertTimer("Pizza für 12 Minuten, dann wenden", 720, "Pizza dann wenden")
        // the duration is one run of words: a later number stays in the title
        assertTimer("10 min Ofen 200 Grad", 600, "Ofen 200 Grad")
        assertTimer("24 h", TimerParser.MAX_SECONDS)
    }

    @Test fun invalidInputGivesNull() {
        for (text in listOf("", "   ", "Nudeln", "0 min", "25 h", "24 h 1 s", "1:75", "10minx", "2x Eier", "min",
            "Stunde", "halbe", "x".repeat(TimerParser.MAX_INPUT + 1))) {
            assertNull(TimerParser.parse(text), text)
        }
    }

    @Test fun eachUnitOnceAndFailedPartsLeaveNothingBehind() {
        // the second "min" is not part of the duration
        assertTimer("10 min 5 min", 600, "5 min")
        // "1:30:00" would add an hour before the minutes clash; the duration stays 20 minutes
        assertTimer("20 min 1:30:00", 1200, "1:30:00")
    }

    @Test fun format() {
        assertEquals("10:00", TimerParser.format(600))
        assertEquals("1:30", TimerParser.format(90))
        assertEquals("0:05", TimerParser.format(5))
        assertEquals("1:20:00", TimerParser.format(4800))
        assertEquals("24:00:00", TimerParser.format(TimerParser.MAX_SECONDS))
    }
}
