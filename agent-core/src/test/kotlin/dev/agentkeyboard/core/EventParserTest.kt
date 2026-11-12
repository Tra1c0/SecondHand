// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import java.util.TimeZone
import kotlin.test.*

class EventParserTest {
    /** Thursday, 12 November 2026, 18:45. */
    private val now = LocalMoment(CivilDate(2026, 11, 12), ClockTime(18, 45))

    private fun parse(text: String, language: String = "de") = EventParser.parse(text, now, language)

    private fun assertEvent(text: String, date: CivilDate, time: ClockTime?, title: String, language: String = "de") {
        val result = assertNotNull(parse(text, language), text)
        assertEquals(date, result.date, text)
        assertEquals(time, result.time, text)
        assertEquals(title, result.title, text)
    }

    private fun date(month: Int, day: Int, year: Int = 2026) = CivilDate(year, month, day)
    private fun at(hour: Int, minute: Int = 0) = ClockTime(hour, minute)

    @Test fun examplesFromTheOrder() {
        assertEvent("morgen 18 Uhr Zahnarzt", date(11, 13), at(18), "Zahnarzt")
        assertEvent("Samstag halb acht Kino", date(11, 14), at(19, 30), "Kino")
        assertEvent("am 3.12. um 9:30 Meeting", date(12, 3), at(9, 30), "Meeting")
        assertEvent("tomorrow 6pm dinner", date(11, 13), at(18), "dinner", "en")
        assertEvent("mañana a las 8 cena", date(11, 13), at(8), "cena", "es")
    }

    @Test fun germanColloquialTimes() {
        assertEvent("morgen halb neun Frühstück", date(11, 13), at(8, 30), "Frühstück")
        assertEvent("morgen halb eins Mittag", date(11, 13), at(12, 30), "Mittag")
        assertEvent("morgen viertel nach acht Bus", date(11, 13), at(8, 15), "Bus")
        assertEvent("morgen viertel vor neun Bus", date(11, 13), at(8, 45), "Bus")
        assertEvent("morgen viertel neun Bus", date(11, 13), at(8, 15), "Bus")
        assertEvent("morgen dreiviertel neun Bus", date(11, 13), at(8, 45), "Bus")
        assertEvent("morgen zehn nach acht Bus", date(11, 13), at(8, 10), "Bus")
        assertEvent("morgen 5 vor 9 Bus", date(11, 13), at(8, 55), "Bus")
        // 1 to 7 o'clock in words is afternoon or evening unless a period says otherwise
        assertEvent("morgen halb drei Kaffee", date(11, 13), at(14, 30), "Kaffee")
        assertEvent("morgen um sieben Essen", date(11, 13), at(19), "Essen")
        assertEvent("morgen früh um sieben Joggen", date(11, 13), at(7), "Joggen")
        assertEvent("morgen sieben Uhr morgens Joggen", date(11, 13), at(7), "Joggen")
        assertEvent("morgen acht Uhr abends Party", date(11, 13), at(20), "Party")
        assertEvent("morgen abend um 8 Party", date(11, 13), at(20), "Party")
        assertEvent("morgen nachts um 2 Flug", date(11, 13), at(2), "Flug")
        assertEvent("morgen mittags Essen", date(11, 13), at(12), "Essen")
    }

    @Test fun germanDigitTimes() {
        assertEvent("morgen 7 Uhr Joggen", date(11, 13), at(7), "Joggen") // digits with Uhr are 24-hour
        assertEvent("morgen 18:30 Training", date(11, 13), at(18, 30), "Training")
        assertEvent("morgen 18.30 Uhr Training", date(11, 13), at(18, 30), "Training")
        assertEvent("morgen um 18.30 Training", date(11, 13), at(18, 30), "Training")
        assertEvent("morgen 18 Uhr 30 Training", date(11, 13), at(18, 30), "Training")
        assertEvent("morgen 18h30 Training", date(11, 13), at(18, 30), "Training")
        assertEvent("morgen 07:00 Training", date(11, 13), at(7), "Training")
        assertEvent("Training morgen um 20 Uhr", date(11, 13), at(20), "Training")
    }

    @Test fun germanDates() {
        assertEvent("übermorgen Party", date(11, 14), null, "Party")
        assertEvent("heute 20 Uhr Kino", date(11, 12), at(20), "Kino")
        assertEvent("+3 Abgabe", date(11, 15), null, "Abgabe")
        assertEvent("in 3 Tagen Abgabe", date(11, 15), null, "Abgabe")
        assertEvent("in zwei Wochen Urlaub", date(11, 26), null, "Urlaub")
        assertEvent("24.12. Bescherung", date(12, 24), null, "Bescherung")
        assertEvent("Bescherung am 24.12.2027 um 17 Uhr", date(12, 24, 2027), at(17), "Bescherung")
        assertEvent("3.12.26 Zahnarzt", date(12, 3), null, "Zahnarzt")
        assertEvent("am 3.12 Zahnarzt", date(12, 3), null, "Zahnarzt")
        assertEvent("24. Dezember Bescherung", date(12, 24), null, "Bescherung")
        assertEvent("2026-12-24 Bescherung", date(12, 24), null, "Bescherung")
        assertEvent("am nächsten Freitag Feier", date(11, 13), null, "Feier")
        assertEvent("Sa. 14.11. Kino", date(11, 14), null, "Kino")
    }

    @Test fun datesWithoutYearAreTheNextOccurrence() {
        assertEvent("1.1. Neujahr", date(1, 1, 2027), null, "Neujahr")
        assertEvent("12.11. Treffen", date(11, 12), null, "Treffen") // today counts as next
        assertEvent("11.11. Laterne", date(11, 11, 2027), null, "Laterne")
        assertEvent("29.2. Schalttag", date(2, 29, 2028), null, "Schalttag")
    }

    @Test fun weekdaysAreTheNextOccurrence() {
        assertEvent("Montag Sport", date(11, 16), null, "Sport")
        assertEvent("Donnerstag Sport", date(11, 12), null, "Sport") // today, all day
        assertEvent("Donnerstag 20 Uhr Sport", date(11, 12), at(20), "Sport") // still ahead today
        assertEvent("Donnerstag 9 Uhr Sport", date(11, 19), at(9), "Sport") // passed today
        assertEvent("Friday at 7 drinks", date(11, 13), at(19), "drinks", "en")
        assertEvent("el sábado a las 10 fútbol", date(11, 14), at(10), "fútbol", "es")
    }

    @Test fun timeWithoutDateIsTodayIfAheadOtherwiseTomorrow() {
        assertEvent("20 Uhr Kino", date(11, 12), at(20), "Kino")
        assertEvent("um 9 Uhr Arzt", date(11, 13), at(9), "Arzt")
        assertEvent("18:45 jetzt", date(11, 13), at(18, 45), "jetzt")
    }

    @Test fun english() {
        assertEvent("dinner tomorrow at 7:30 pm", date(11, 13), at(19, 30), "dinner", "en")
        assertEvent("tomorrow 9am standup", date(11, 13), at(9), "standup", "en")
        assertEvent("tomorrow 12am release", date(11, 13), at(0), "release", "en")
        assertEvent("tomorrow 12pm lunch", date(11, 13), at(12), "lunch", "en")
        assertEvent("tomorrow 6 p.m. dinner", date(11, 13), at(18), "dinner", "en")
        assertEvent("tomorrow at half past seven movie", date(11, 13), at(19, 30), "movie", "en")
        assertEvent("tomorrow at a quarter to eight movie", date(11, 13), at(19, 45), "movie", "en")
        assertEvent("tomorrow at eight o'clock in the morning run", date(11, 13), at(8), "run", "en")
        assertEvent("tonight at 8 party", date(11, 12), at(20), "party", "en")
        assertEvent("December 24th party", date(12, 24), null, "party", "en")
        assertEvent("on the 3rd of December meeting", date(12, 3), null, "meeting", "en")
        assertEvent("12/24 party", date(12, 24), null, "party", "en")
        assertEvent("next monday noon lunch", date(11, 16), at(12), "lunch", "en")
        assertEvent("in 2 days at 10 dentist", date(11, 14), at(10), "dentist", "en")
    }

    @Test fun spanish() {
        assertEvent("mañana a las ocho y media de la noche cena", date(11, 13), at(20, 30), "cena", "es")
        assertEvent("mañana a las 9 y cuarto clase", date(11, 13), at(9, 15), "clase", "es")
        assertEvent("mañana a las 9 menos cuarto clase", date(11, 13), at(8, 45), "clase", "es")
        assertEvent("mañana por la mañana a las 9 clase", date(11, 13), at(9), "clase", "es")
        assertEvent("pasado mañana a la una comida", date(11, 14), at(13), "comida", "es")
        assertEvent("24 de diciembre cena", date(12, 24), null, "cena", "es")
        assertEvent("24/12 cena", date(12, 24), null, "cena", "es")
        assertEvent("esta noche a las 9 cine", date(11, 12), at(21), "cine", "es")
        assertEvent("el viernes a mediodía comida", date(11, 13), at(12), "comida", "es")
    }

    @Test fun periodWordsStayInTheTitleWithoutTime() {
        assertEvent("heute abend Kino", date(11, 12), null, "abend Kino")
        assertEvent("Kino, morgen um 20 Uhr", date(11, 13), at(20), "Kino")
        assertEvent("morgen 18 Uhr", date(11, 13), at(18), "")
    }

    @Test fun wordsAreOnlyATimeInContext() {
        // bare numbers and number words without um/at/Uhr belong to the title
        assertEvent("morgen 3 Freunde treffen", date(11, 13), null, "3 Freunde treffen")
        assertEvent("morgen acht Leute", date(11, 13), null, "acht Leute")
        assertEvent("morgen um 8 am Bahnhof", date(11, 13), at(8), "am Bahnhof")
        assertEvent("Kino am Samstag", date(11, 14), null, "Kino")
    }

    @Test fun invalidGivesNothing() {
        assertNull(parse("Zahnarzt"))
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("31.2. Feier"))
        assertNull(parse("31.11.2026 Feier"))
        assertNull(parse("morgen 25 Uhr Feier"))
        assertNull(parse("morgen 18:75 Feier"))
        assertNull(parse("tomorrow 13pm dinner", "en"))
        assertNull(parse("2026-02-30 Feier"))
        assertNull(parse("Samstag 15.11. Kino")) // 15.11.2026 is a Sunday
        assertNull(parse("+9999 Feier"))
        assertNull(parse("x".repeat(EventParser.MAX_INPUT + 1)))
    }

    @Test fun nowComesFromMillisAndZone() {
        val zone = TimeZone.getTimeZone("Europe/Berlin")
        // 2026-11-12T23:30Z is already Friday 00:30 in Berlin
        val millis = 1_794_526_200_000L
        val berlin = assertNotNull(EventParser.parse("morgen", millis, zone, "de"))
        assertEquals(date(11, 14), berlin.date)
        val utc = assertNotNull(EventParser.parse("morgen", millis, TimeZone.getTimeZone("UTC"), "de"))
        assertEquals(date(11, 13), utc.date)
    }
}
