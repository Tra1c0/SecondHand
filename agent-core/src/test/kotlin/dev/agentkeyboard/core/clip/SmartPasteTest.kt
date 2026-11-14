// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import dev.agentkeyboard.core.CivilDate
import dev.agentkeyboard.core.ClockTime
import dev.agentkeyboard.core.LocalMoment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Smart Paste (stage 40): the kinds and their actions, the conservative gates for parcel numbers
 * and dates, and the link cleaner. Check digits of the tracking examples were computed separately
 * (UPS: the public sample number, S10 and GS1 by hand).
 */
class SmartPasteTest {
    /** Saturday, 14 November 2026, 10:00. */
    private val now = LocalMoment(CivilDate(2026, 11, 14), ClockTime(10, 0))

    private fun actions(text: String, language: String = "de", marked: Boolean = false) =
        SmartPaste.recognize(text, marked, "DE", now, language)

    private fun date(text: String, language: String = "de") = ClipDates.find(text, now, language)
    private fun track(text: String) = TrackingNumbers.find(text)

    // --- kinds and their actions ---------------------------------------------------------------

    @Test fun anAddressOpensTheMapOrBecomesAPlace() {
        assertEquals(listOf(SmartAction.OpenMap("Musterstraße 12, 10115 Berlin"), SmartAction.SavePlace("Musterstraße 12, 10115 Berlin")),
            actions("Musterstraße 12, 10115 Berlin"))
        // lines become one search line of street and postcode with city (review B7: the name line stays out)
        assertEquals(listOf(SmartAction.OpenMap("Hauptstr. 5a, 80331 München"), SmartAction.SavePlace("Hauptstr. 5a, 80331 München")),
            actions("Max Mustermann\nHauptstr. 5a\n80331 München"))
    }

    @Test fun anAddressSearchHoldsOnlyStreetAndPostcodeRunningTextGetsNoPlace() {
        // review of stage 40 (B7): labels, phone and mail lines stay out of the search and the place
        val delivery = "Musterweg 12, 12345 Musterstadt"
        assertEquals(listOf(SmartAction.OpenMap(delivery), SmartAction.SavePlace(delivery)),
            actions("Lieferadresse: Musterweg 12\n12345 Musterstadt\nTel. 0151 23456789"))
        assertEquals(listOf(SmartAction.OpenMap(delivery), SmartAction.SavePlace(delivery)),
            actions("Rechnungsadresse:\nFirma GmbH\nMusterweg 12\n12345 Musterstadt"))
        assertEquals(listOf(SmartAction.OpenMap(delivery), SmartAction.SavePlace(delivery)),
            actions("Adresse: Musterweg 12, 12345 Musterstadt, E-Mail info@example.com"))
        // street names as written, the city with more words, a country line left out
        for ((clip, query) in listOf(
            "Am Marktplatz 5, 10115 Berlin" to "Am Marktplatz 5, 10115 Berlin",
            "Berliner Straße 5\n10115 Berlin\nDeutschland" to "Berliner Straße 5, 10115 Berlin",
            "Am Alten Markt 3\n06108 Halle (Saale)" to "Am Alten Markt 3, 06108 Halle (Saale)",
            "Hauptstraße 5\n60311 Frankfurt am Main" to "Hauptstraße 5, 60311 Frankfurt am Main",
            "Konrad-Adenauer-Straße 12-14, 53113 Bonn" to "Konrad-Adenauer-Straße 12-14, 53113 Bonn",
            "Musterstraße 12 10115 Berlin" to "Musterstraße 12, 10115 Berlin",
            "Calle Mayor, 5, 28013 Madrid" to "Calle Mayor, 5, 28013 Madrid",
            "123 Main Street, Springfield, IL 62704" to "123 Main Street, Springfield, IL 62704",
        )) assertEquals(listOf(SmartAction.OpenMap(query), SmartAction.SavePlace(query)), actions(clip), clip)
        // running text: only the map, with the street and the postcode picked out, never a place
        for ((clip, query) in listOf(
            "Treffen am Marktplatz 5, 10115 Berlin" to "Marktplatz 5, 10115 Berlin",
            "Ich wohne in der Hauptstraße 5 seit 10 Jahren in 10115 Berlin" to "Hauptstraße 5, 10115 Berlin",
            "Kommst du morgen zur Musterstraße 12 in 10115 Berlin?" to "Musterstraße 12, 10115 Berlin",
            "Wir treffen uns in der Berliner Straße 5, 10115 Berlin" to "Berliner Straße 5, 10115 Berlin",
            "Termin Hauptstraße 5, 10115 Berlin" to "Hauptstraße 5, 10115 Berlin",
        )) assertEquals(listOf<SmartAction>(SmartAction.OpenMap(query)), actions(clip), clip)
    }

    @Test fun aPhoneNumberDialsOrMakesAContact() {
        // review of stage 40 (B1): the E.164 form goes to the dialer and the contacts app
        assertEquals(listOf(SmartAction.Dial("+4915123456789"), SmartAction.AddContact("+4915123456789")), actions("+49 151 23456789"))
        for (clip in listOf(" 030/12345678 ", "(030) 12345678", "0049 30 12345678", "+49 30 12345678"))
            assertEquals(listOf(SmartAction.Dial("+493012345678"), SmartAction.AddContact("+493012345678")), actions(clip), clip)
        assertEquals("+4915123456789", dev.agentkeyboard.core.format.PhoneFormat.e164("0151 23456789", "DE"))
        assertNull(dev.agentkeyboard.core.format.PhoneFormat.e164("12345", "DE"))
    }

    @Test fun bareDigitRunsAreNoNumbersToCall() {
        // review of stage 40 (B1): a DHL piece code, an SAP order number, digits without "+", "00" or "0"
        for (clip in listOf("340434161094", "4500012345", "3012345678", "9876543210", "15123456789", "30 12345678")) {
            val found = actions(clip)
            assertTrue(found.none { it is SmartAction.Dial || it is SmartAction.AddContact }, "$clip: $found")
        }
        // the guard's verdict stays (its label in the history, stage 44); only the call chips need a number written as one
        assertEquals(ClipKind.PHONE, ClipGuard.classify("4500012345").kind)
        // with the carrier named, the piece code is a parcel number
        assertEquals(listOf<SmartAction>(SmartAction.Track(Tracking(Carrier.DHL, "340434161094"))), actions("DHL Sendung 340434161094"))
    }

    @Test fun onlyCodesAndIbansGoInAsPrivateText() {
        // review of stage 40 (B2): a link without tracking goes in like the pill and takes no private-text mark
        assertTrue(SmartAction.InsertCode("123456").insertsPrivately)
        assertTrue(SmartAction.InsertIban("DE89 3704 0044 0532 0130 00").insertsPrivately)
        assertFalse(SmartAction.InsertClean("https://example.com").insertsPrivately)
        assertTrue(SmartAction.InsertClean("https://example.com").inserts)
        for (action in listOf(SmartAction.OpenMap("x"), SmartAction.Dial("+4930"), SmartAction.IbanInvalid))
            assertFalse(action.insertsPrivately, action.toString())
    }

    @Test fun anIbanIsInsertedInBlocksOnlyWithRightCheckDigits() {
        assertEquals(listOf(SmartAction.InsertIban("DE89 3704 0044 0532 0130 00")), actions("DE89370400440532013000"))
        assertEquals(listOf(SmartAction.InsertIban("DE89 3704 0044 0532 0130 00")), actions("IBAN: DE89370400440532013000"))
        assertEquals(listOf(SmartAction.InsertIban("ES91 2100 0418 4502 0005 1332")), actions("es9121000418450200051332"))
        // already written in blocks: the clipboard pill inserts the same, so no chip
        assertEquals(emptyList(), actions("DE89 3704 0044 0532 0130 00"))
        // wrong check digits: only the hint, nothing to insert
        assertEquals(listOf<SmartAction>(SmartAction.IbanInvalid), actions("DE88370400440532013000"))
        assertFalse(SmartAction.IbanInvalid.inserts)
        assertTrue(SmartAction.InsertIban("x").inserts)
    }

    @Test fun anEmailAddressWritesAMail() {
        assertEquals(listOf(SmartAction.WriteMail("max.mustermann@example.com")), actions("max.mustermann@example.com"))
        assertEquals(listOf(SmartAction.WriteMail("info@firma.co.at")), actions("mailto:info@firma.co.at"))
    }

    @Test fun aLinkWithTrackingIsOfferedCleanOneWithoutGetsNothing() {
        assertEquals(listOf(SmartAction.InsertClean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42")),
            actions("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42&si=Ab12Cd34Ef&utm_source=share"))
        assertEquals(emptyList(), actions("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42"))
        assertEquals(emptyList(), actions("heise.de"))
    }

    @Test fun aCodeInAMessageInsertsOnlyItsDigitsACodeAloneGetsNothing() {
        assertEquals(listOf(SmartAction.InsertCode("123456")), actions("Dein Code lautet 123456"))
        assertEquals(listOf(SmartAction.InsertCode("482913")), actions("Ihr Bestätigungscode: 482-913. Gültig für 10 Minuten."))
        assertEquals(emptyList(), actions("482913"))
    }

    @Test fun secretsAndMarkedClipsGetNothing() {
        val key = "sk" + "-proj-" + "Ab3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY3z" + "7f3a"
        assertEquals(emptyList(), actions(key))
        assertEquals(emptyList(), actions("Passwort: Sommer2024!"))
        // a clip the app marked sensitive gets nothing, whatever it looks like
        assertEquals(emptyList(), actions("Musterstraße 12, 10115 Berlin", marked = true))
        assertEquals(emptyList(), actions("+49 151 23456789", marked = true))
        assertEquals(emptyList(), actions("   "))
        assertEquals(emptyList(), actions("Musterstraße 12, 10115 Berlin " + "x".repeat(SmartPaste.MAX_TEXT)))
    }

    @Test fun textGetsParcelDateAndCleanLinkActions() {
        val found = actions("Dein DHL-Paket 00340434161094042557 kommt morgen zwischen 10:00 und 12:00 Uhr: https://www.dhl.de/x?utm_campaign=y")
        assertEquals(3, found.size, found.toString())
        assertEquals(SmartAction.Track(Tracking(Carrier.DHL, "00340434161094042557")), found[0])
        val event = (found[1] as SmartAction.CreateEvent).event
        assertEquals(CivilDate(2026, 11, 15), event.date)
        assertEquals(ClockTime(10, 0), event.time)
        assertEquals(SmartAction.InsertClean("Dein DHL-Paket 00340434161094042557 kommt morgen zwischen 10:00 und 12:00 Uhr: https://www.dhl.de/x"),
            found[2])
        assertEquals(emptyList(), actions("Hallo, wie geht's?"))
    }

    @Test fun actionsNeverShowTheClipInToString() {
        val all = listOf(
            SmartAction.OpenMap("Musterstraße 12"), SmartAction.SavePlace("Musterstraße 12"), SmartAction.Dial("0151 23456789"),
            SmartAction.AddContact("0151 23456789"), SmartAction.InsertIban("DE89 3704"), SmartAction.WriteMail("max@example.com"),
            SmartAction.InsertClean("https://example.com"), SmartAction.InsertCode("123456"),
            SmartAction.Track(Tracking(Carrier.UPS, "1Z999AA10123456784")),
            SmartAction.CreateEvent(assertNotNull(date("Zahnarzt am 15.03.2027 um 14:30"))),
        )
        for (action in all) {
            val shown = action.toString()
            for (secret in listOf("Muster", "0151", "DE89", "max@", "example", "123456", "1Z999", "Zahnarzt")) assertFalse(secret in shown, shown)
        }
    }

    // --- parcel numbers --------------------------------------------------------------------------

    @Test fun distinctiveParcelNumbersNeedNoCarrierName() {
        assertEquals(Tracking(Carrier.UPS, "1Z999AA10123456784"), track("1Z999AA10123456784"))
        assertEquals(Tracking(Carrier.UPS, "1Z12345E6605272234"), track("Deine Sendung 1Z12345E6605272234 ist unterwegs."))
        assertEquals(Tracking(Carrier.UPS, "1Z999AA10123456784"), track("1Z 999 AA1 01 2345 6784"))
        assertEquals(Tracking(Carrier.DEUTSCHE_POST, "RR123456785DE"), track("RR123456785DE"))
        assertEquals(Tracking(Carrier.DEUTSCHE_POST, "RR123456785DE"), track("RR 1234 5678 5DE"))
        assertEquals(Tracking(Carrier.DEUTSCHE_POST, "LX473124829DE"), track("Einschreiben (LX473124829DE)"))
        assertEquals(Tracking(Carrier.DHL, "00340434161094042557"), track("00340434161094042557"))
        assertEquals(Tracking(Carrier.DHL, "00340434161094015902"), track("0034 0434 1610 9401 5902"))
        assertEquals(Tracking(Carrier.DHL, "JJD000390007830225613"), track("Sendungsnummer: JJD000390007830225613"))
        assertEquals(Tracking(Carrier.HERMES, "H1000470012345678901"), track("H1000470012345678901"))
    }

    @Test fun wrongCheckDigitsAndOtherFormatsAreNoParcelNumbers() {
        assertNull(track("1Z999AA10123456785")) // check digit 4
        assertNull(track("RR123456784DE")) // check digit 5
        assertNull(track("00340434161094042558")) // GS1 check digit 7
        assertNull(track("RR123456785CN")) // only Deutsche Post (DE) items
        assertFalse(TrackingNumbers.upsValid("1Z999AA1012345678X"))
        assertTrue(TrackingNumbers.s10Valid("EE000000005DE"))
        assertTrue(TrackingNumbers.gs1Valid("340434161094042557"))
    }

    @Test fun plainDigitRunsNeedTheCarrierName() {
        // without a name: a customer, order or account number stays a number
        assertNull(track("01234567890123"))
        assertNull(track("Bestellnummer 01234567890123"))
        assertNull(track("Kundennummer 123456789012"))
        assertNull(track("12345678901"))
        assertEquals(Tracking(Carrier.DPD, "01234567890123"), track("DPD Paketnummer 01234567890123"))
        assertEquals(Tracking(Carrier.HERMES, "01234567890123"), track("Hermes: Ihre Sendung 01234567890123 wurde zugestellt"))
        assertEquals(Tracking(Carrier.DHL, "340434161094"), track("DHL Sendungsnummer 340434161094"))
        assertEquals(Tracking(Carrier.GLS, "12345678901"), track("GLS-Paket 12345678901 ist im Depot"))
        assertEquals(Tracking(Carrier.GLS, "ZFG2D3K1"), track("GLS Track-ID ZFG2D3K1"))
        // the first carrier named decides between DPD and Hermes for 14 digits
        assertEquals(Carrier.DPD, track("DPD statt Hermes: 01234567890123")?.carrier)
        // words of the text are no number ("Hermes" alone, a GLS id in small letters)
        assertNull(track("Hermes bringt heute"))
        assertNull(track("GLS Track-ID zfg2d3k1"))
    }

    @Test fun customerOrderAndInvoiceNumbersAndBicsAreNoParcelNumbers() {
        // review of stage 40 (B5): a number right after its label is that number, also with the carrier named
        for (text in listOf("DHL Kundennummer 123456789012", "Bestellung 123456789012 wird mit DHL versendet",
            "DHL Kunden-Nr. 123456789012", "Hermes Auftragsnummer: 01234567890123", "DPD Rechnungsnummer 01234567890123",
            "DHL customer number 123456789012", "GLS order no. 12345678901", "Order number 1Z999AA10123456784",
            "Kundennummer: 1Z999AA10123456784", "Rechnung RR123456785DE")) assertNull(track(text), text)
        // GLS's 8-character id: only near "Track-ID", never a BIC
        for (text in listOf("GLS Bank BIC GENODEM1", "GLS Ref AB12CD34", "ab12cd34 GLS", "GLS Track-ID GENODEM1"))
            assertNull(track(text), text)
        assertEquals(Tracking(Carrier.GLS, "ZFG2D3K1"), track("GLS TrackID: ZFG2D3K1"))
        // a label further back does not count
        assertEquals(Tracking(Carrier.DHL, "340434161094"), track("Kundennummer 12345, DHL Sendungsnummer 340434161094"))
        assertEquals(Tracking(Carrier.UPS, "1Z999AA10123456784"), track("Your parcel 1Z999AA10123456784"))
    }

    @Test fun postcodesPhoneNumbersAndAmountsAreNoParcelNumbers() {
        assertNull(track("10115 Berlin"))
        assertNull(track("PLZ 80331, DHL Packstation 123"))
        assertNull(track("DHL Hotline 0228 4333112"))
        assertNull(track("+49 151 23456789"))
        assertNull(track("DPD: 1.234.567,89 €"))
        assertNull(track("DE89370400440532013000"))
        assertNull(track("x".repeat(TrackingNumbers.MAX_LENGTH + 1) + " 1Z999AA10123456784"))
    }

    @Test fun trackingPagesComeOnlyFromTheFixedTable() {
        assertEquals("https://www.ups.com/track?loc=de_DE&tracknum=1Z999AA10123456784", Tracking(Carrier.UPS, "1Z999AA10123456784").url)
        assertEquals("https://tracking.dpd.de/status/de_DE/parcel/01234567890123", Tracking(Carrier.DPD, "01234567890123").url)
        assertEquals("https://www.dhl.de/de/privatkunden/pakete-empfangen/verfolgen.html?piececode=00340434161094042557",
            Tracking(Carrier.DHL, "00340434161094042557").url)
        assertEquals("https://gls-group.com/DE/de/paketverfolgung?match=12345678901", Tracking(Carrier.GLS, "12345678901").url)
        assertTrue(Tracking(Carrier.HERMES, "H1000470012345678901").url.startsWith("https://www.myhermes.de/"))
        assertTrue(Tracking(Carrier.DEUTSCHE_POST, "RR123456785DE").url.startsWith("https://www.dhl.de/"))
        // nothing but capitals and digits reaches a page
        for (bad in listOf("1z999aa10123456784", "123&evil=1", "1234567/../x", "12345678 9", "1234567")) assertNull(Carrier.DPD.trackingUrl(bad), bad)
        assertEquals("Tracking(UPS)", Tracking(Carrier.UPS, "1Z999AA10123456784").toString())
    }

    // --- dates -----------------------------------------------------------------------------------

    @Test fun calendarDatesAndTimesInDeEnEs() {
        date("Termin am 15.03.2027 um 14:30 Zahnarzt").let {
            assertEquals(CivilDate(2027, 3, 15), it?.date)
            assertEquals(ClockTime(14, 30), it?.time)
            assertEquals("Termin Zahnarzt", it?.title)
        }
        assertEquals(CivilDate(2026, 11, 15) to ClockTime(18, 0), date("Wir sehen uns morgen um 18 Uhr.")?.let { it.date to it.time })
        assertEquals(CivilDate(2026, 12, 15) to null, date("15. Dezember Weihnachtsfeier")?.let { it.date to it.time })
        assertEquals(CivilDate(2026, 12, 3), date("Abgabe bis 03.12.")?.date)
        assertEquals(CivilDate(2026, 12, 3), date("Abgabe am 3.12.")?.date)
        assertEquals(CivilDate(2027, 1, 20), date("Kick-off 2027-01-20")?.date)
        assertEquals(CivilDate(2027, 1, 20), date("Kick-off 20.01.2027.")?.date)
        assertEquals(CivilDate(2026, 11, 15) to ClockTime(18, 0), date("Meeting tomorrow at 6pm", "en")?.let { it.date to it.time })
        assertEquals(CivilDate(2026, 12, 24), date("December 24th party", "en")?.date)
        assertEquals(CivilDate(2026, 11, 15) to ClockTime(21, 0), date("Cena mañana a las 9 de la noche", "es")?.let { it.date to it.time })
        assertEquals(CivilDate(2027, 3, 3), date("Reunión el 3 de marzo de 2027", "es")?.date)
        // a time with a mark and no day: today while it is still ahead
        assertEquals(CivilDate(2026, 11, 14) to ClockTime(18, 30), date("Treffen um 18:30 vor dem Kino")?.let { it.date to it.time })
        assertEquals(CivilDate(2026, 11, 15) to ClockTime(19, 30), date("Sonntag halb acht Kino")?.let { it.date to it.time })
        // today is a Saturday: "Samstag" with a time still ahead is today
        assertEquals(CivilDate(2026, 11, 14), date("Samstag halb acht Kino")?.date)
    }

    @Test fun versionNumbersAndAmountsAreNoDates() {
        for (text in listOf("Version 2.1", "Update 1.2.3 ist da", "Android 14.1.2", "Kapitel 3.2.", "Preis 3.10 €", "Preis: 12.50",
            "Version 2.1 um 18 Uhr fertig", "v2.1.0 released", "Seite 4.11")) assertNull(date(text), text)
        assertNull(actions("Version 2.1").firstOrNull { it is SmartAction.CreateEvent })
        // review of stage 40 (B3): three parts with a two-digit last one are a version without "am"/"on"/"el" before them
        for (text in listOf("PHP 8.3.28 ist erschienen", "Version 1.2.30", "Version 2.10.27", "App 2.3.27 released", "Firmware 3.1.99",
            "Update 1.12.28 ist da", "Release 1.10.26", "Version 10.11.30", "Update 03.12.", "Build 12.11.2027"))
            assertNull(date(text), text)
        assertEquals(emptyList(), actions("Version 1.2.30"))
        // a date written so is still one with "am" before it, or with two digits for day and month
        assertEquals(CivilDate(2028, 3, 8) to ClockTime(10, 0), date("am 8.3.28 um 10 Uhr")?.let { it.date to it.time })
        assertEquals(CivilDate(2028, 3, 8), date("Termin 08.03.28")?.date)
        assertEquals(CivilDate(2026, 12, 3), date("Update am 3.12.")?.date)
    }

    @Test fun openingHoursAndAvailabilityAreNoEvents() {
        // review of stage 40 (B3)
        for (text in listOf("Öffnungszeiten: Montag bis Freitag 9 bis 18 Uhr", "Hotline ab 8 Uhr erreichbar", "Mo-Fr 9-18 Uhr",
            "Sa bis So Flohmarkt um 10 Uhr", "Sprechzeiten morgen 8 bis 12 Uhr", "Wir sind täglich ab 9 Uhr für Sie da",
            "ÖFFNUNGSZEITEN heute bis 20 Uhr", "Morgen um 9 Uhr GEÖFFNET"))
            assertNull(date(text), text)
        assertNull(date("Opening hours Mon to Fri 9am to 5pm", "en"))
        assertNull(date("Horario de lunes a viernes a las 9", "es"))
    }

    @Test fun dayWordsAloneUnmarkedTimesAndGreetingsAreNoDates() {
        for (text in listOf("bis morgen", "Samstag", "Guten Morgen! Wie geht's?", "Abfahrt 18:30 Gleis 4", "Fahrt 4h",
            "see you tomorrow", "hasta mañana", "Ergebnis 3:21", "")) assertNull(date(text), text)
    }

    @Test fun pastDatesAndYearLessDatesThatJustPassedAreNoDates() {
        assertNull(date("Rechnung vom 03.11.2026"))
        assertNull(date("geboren am 12.05.1985"))
        // year-less: the next 3 November is almost a year ahead, so it most likely just passed
        assertNull(date("Rechnung vom 03.11."))
        assertNull(date("gestern um 18 Uhr"))
        assertNull(date("Termin am 15.03.2027 um 14:30\n\n\n\nund noch eine Zeile"))
        assertNull(date("Termin am 15.03.2027 " + "x".repeat(ClipDates.MAX_LENGTH)))
    }

    // --- links -----------------------------------------------------------------------------------

    @Test fun trackingParametersGoNeededParametersStay() {
        assertEquals("https://shop.example.de/p?id=5&color=red#reviews",
            LinkCleaner.clean("https://shop.example.de/p?utm_source=newsletter&id=5&fbclid=IwAR0abc&color=red&gclid=Cj0K#reviews"))
        assertEquals("https://example.com/a", LinkCleaner.clean("https://example.com/a?utm_source=x&UTM_Medium=y"))
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", LinkCleaner.clean("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=a1b2c3"))
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42", LinkCleaner.clean("https://youtu.be/dQw4w9WgXcQ?si=Ab12&t=42"))
        assertEquals("https://www.instagram.com/p/Cx1/", LinkCleaner.clean("https://www.instagram.com/p/Cx1/?igshid=MzRlODBiNWFlZA=="))
        assertEquals("https://news.example.org/x?page=2", LinkCleaner.clean("https://news.example.org/x?mc_eid=1a2b&page=2&mc_cid=9&_hsenc=p2&srsltid=AfmB"))
        assertEquals("www.shop.de/p", LinkCleaner.clean("www.shop.de/p?utm_campaign=winter"))
        // "si" is a share id only on YouTube and Spotify; elsewhere it may be needed and stays
        assertNull(LinkCleaner.clean("https://example.com/search?q=si&si=2&page=1"))
        // encoding, order and empty values of the other parameters stay as written
        assertEquals("https://example.com/s?q=a%20b&x=&y", LinkCleaner.clean("https://example.com/s?q=a%20b&utm_term=t&x=&y"))
        // names that only start like a tracking parameter stay
        assertNull(LinkCleaner.clean("https://example.com/s?utmost=1&gclidx=2&siren=3"))
        assertNull(LinkCleaner.clean("https://example.com/plain"))
        assertNull(LinkCleaner.clean("javascript:alert(1)?utm_source=x"))
        assertNull(LinkCleaner.clean("keine Adresse"))
    }

    @Test fun aPartWithASemicolonStaysWholeEmptyPartsStay() {
        // review of stage 40 (B6): "utm_source=x;id=2" may hold the needed id=2 (servers that split at ';')
        assertNull(LinkCleaner.clean("https://example.com/a?utm_source=x;id=2"))
        assertEquals("https://example.com/a?id=1;utm_medium=y", LinkCleaner.clean("https://example.com/a?utm_source=x&id=1;utm_medium=y"))
        // the other parts as written, empty ones too; nothing left: no "?"
        assertEquals("https://example.com/a?id=1&&&b=2", LinkCleaner.clean("https://example.com/a?id=1&&utm_source=x&&b=2"))
        assertEquals("https://example.com/a", LinkCleaner.clean("https://example.com/a?&utm_source=x&"))
    }

    @Test fun linksInTextAreCleanedTheRestStays() {
        assertEquals("Schau mal: https://youtu.be/dQw4w9WgXcQ. Und (https://example.com/a?id=1) danach!",
            LinkCleaner.cleanText("Schau mal: https://youtu.be/dQw4w9WgXcQ?si=Ab12. Und (https://example.com/a?id=1&utm_medium=social) danach!"))
        assertNull(LinkCleaner.cleanText("Schau mal: https://youtu.be/dQw4w9WgXcQ und gut"))
        assertNull(LinkCleaner.cleanText("kein Link ?utm_source=x"))
        assertEquals(listOf(SmartAction.InsertClean("Toller Song https://open.spotify.com/track/1x")),
            actions("Toller Song https://open.spotify.com/track/1x?si=abc"))
    }

    // --- places ----------------------------------------------------------------------------------

    @Test fun aSavedPlaceIsNamedAfterTheFirstPartOfItsAddress() {
        assertEquals("Musterstraße 12", SmartPaste.placeName("Musterstraße 12, 10115 Berlin", emptyList()))
        assertEquals("Musterstraße 12 2", SmartPaste.placeName("Musterstraße 12, 80331 München", listOf("musterstraße 12", "Zuhause")))
        assertEquals("Musterstraße 12 3", SmartPaste.placeName("Musterstraße 12, 80331 München", listOf("Musterstraße 12", "Musterstraße 12 2")))
        val long = SmartPaste.placeName("A".repeat(60) + ", 10115 Berlin", emptyList())
        assertEquals(dev.agentkeyboard.core.Places.MAX_NAME, long.length)
        assertTrue(SmartPaste.placeName("A".repeat(60), listOf("A".repeat(40))).endsWith(" 2"))
    }
}
