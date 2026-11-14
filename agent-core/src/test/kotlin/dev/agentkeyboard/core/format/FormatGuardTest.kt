// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.format

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatGuardTest {
    private fun email(text: String) = FormatGuard.check(FieldKind.EMAIL, text, "de")
    private fun domains(text: String) = email(text).map { (it as FormatHint.EmailDomain).replacement }

    // --- e-mail ---------------------------------------------------------------------------

    @Test fun domainTyposOfCommonProviders() {
        assertEquals(listOf("max@gmail.com"), domains("max@gmial.com"))
        assertEquals(listOf("max@gmail.com"), domains("max@gmai.com"))
        assertEquals(listOf("max@gmail.com"), domains("max@gamil.com"))
        assertEquals(listOf("max@gmail.com"), domains("max@gmail.con"))
        assertEquals(listOf("max@gmail.com"), domains("max@gnail.cmo"))
        assertEquals(listOf("anna@web.de"), domains("anna@wbe.de"))
        assertEquals(listOf("anna@gmx.net"), domains("anna@gmx.nte"))
        assertEquals(listOf("a.b@t-online.de"), domains("a.b@tonline.de"))
        assertEquals(listOf("a.b@t-online.de"), domains("a.b@t-onlnie.de"))
        assertEquals(listOf("x@posteo.de"), domains("x@poste.de"))
        assertEquals(listOf("x@outlook.com"), domains("x@outlok.com"))
        assertEquals(listOf("x@hotmail.com"), domains("x@hotmial.com"))
        assertEquals(listOf("x@yahoo.com"), domains("x@yaho.com"))
        assertEquals(listOf("x@icloud.com"), domains("x@icluod.com"))
        assertEquals(listOf("x@mailbox.org"), domains("x@mailbx.org"))
        assertEquals(listOf("x@freenet.de"), domains("x@freenet.d3"))
        val hint = email("max@gmial.com").single() as FormatHint.EmailDomain
        assertEquals("gmail.com", hint.domain)
    }

    @Test fun correctProvidersAndUnknownDomainsGetNoHint() {
        for (text in listOf("max@gmail.com", "anna@web.de", "x@gmx.de", "x@gmx.net", "x@gmx.at", "x@gmx.ch",
            "x@t-online.de", "x@posteo.de", "x@outlook.com", "x@hotmail.com", "x@yahoo.com", "x@icloud.com",
            "x@mailbox.org", "x@freenet.de", "x@firma-mueller.de", "info@example.org", "x@uni-heidelberg.de"))
            assertEquals(emptyList(), email(text), text)
    }

    @Test fun realDomainsOfOtherCountriesOrServicesGetNoHint() {
        // review of stage 41: these were offered as typos of a provider
        for (domain in listOf("hotmail.at", "hotmail.ch", "hotmail.fr", "hotmail.it", "outlook.at", "outlook.fr",
            "outlook.it", "posteo.at", "posteo.net", "online.de", "mail.com", "proton.ch", "yahoo.fr", "yahoo.it",
            "yahoo.ca", "HOTMAIL.FR", "mail.de", "gmail.de", "live.at", "yahoo.co.uk", "hotmail.co.uk"))
            assertEquals(emptyList(), email("x@$domain"), domain)
        // a real TLD is kept: a provider name with another country's TLD is not "corrected" to .de/.com
        for (domain in listOf("gmx.it", "web.fr", "gmail.nl", "outlook.pl", "freenet.eu", "gmx.co"))
            assertEquals(emptyList(), email("x@$domain"), domain)
        // still a typo: of the name with the same TLD, or of the TLD itself
        assertEquals(listOf("x@hotmail.de"), domains("x@hotmial.de"))
        assertEquals(listOf("x@gmail.com"), domains("x@gmail.cpm"))
        assertEquals(listOf("x@gmail.com"), domains("x@gmail.comm"))
        assertEquals(listOf("x@web.de"), domains("x@web.ed"))
        assertEquals(listOf("x@gmx.net"), domains("x@gmx.nte"))
        // an unknown TLD that is no typo of a provider TLD: nothing to say
        assertEquals(emptyList(), email("x@gmail.qqq"))
        assertTrue(EmailCheck.KNOWN_DOMAINS.containsAll(EmailCheck.PROVIDERS))
        for (domain in EmailCheck.KNOWN_DOMAINS) assertEquals(emptyList(), EmailCheck.domains(domain), domain)
    }

    @Test fun upperCaseIsCompared() {
        assertEquals(emptyList(), email("Max@GMAIL.COM"))
        assertEquals(listOf("Max@gmail.com"), domains("Max@GMIAL.COM"))
        assertEquals(listOf("MAX@web.de"), domains("MAX@WBE.DE"))
    }

    @Test fun trailingDotAfterAProviderIsRemoved() {
        assertEquals(listOf("max@gmail.com"), domains("max@gmail.com."))
        assertEquals(listOf("max@web.de"), domains("max@WEB.DE."))
        // an unknown domain with a dot at the end is still being typed
        assertEquals(emptyList(), email("max@firma."))
        assertEquals(emptyList(), email("max@firma.de."))
    }

    @Test fun umlautDomains() {
        // an umlaut typo of a provider
        assertEquals(listOf("max@gmail.com"), domains("max@gmäil.com"))
        assertEquals(listOf("max@web.de"), domains("max@wéb.de"))
        // real internationalized domains are not touched
        assertEquals(emptyList(), email("info@müller.de"))
        assertEquals(emptyList(), email("post@bäckerei-schön.at"))
        assertEquals(emptyList(), email("jörg@straße.de"))
    }

    @Test fun domainStillBeingTypedGetsNoHint() {
        for (text in listOf("max@g", "max@gm", "max@gmail", "max@gmail.", "max@gmail.c", "max@gmail.co", "max@we",
            "max@web.d", "max@t-online", "max@", "max", "@gmail.com"))
            assertEquals(emptyList(), email(text), text)
    }

    @Test fun shortProvidersAllowOneEditOnly() {
        assertEquals(1, EmailCheck.maxEdits("web.de"))
        assertEquals(2, EmailCheck.maxEdits("gmail.com"))
        // two edits away from web.de: no hint
        assertEquals(emptyList(), email("x@wxy.de"))
        // three edits away from gmail.com
        assertEquals(emptyList(), email("x@gxyzl.com"))
    }

    @Test fun equallyCloseProvidersGiveAtMostTwo() {
        // "gmx.dt" is one edit from gmx.de and one from gmx.at
        val both = domains("x@gmx.dt")
        assertEquals(listOf("x@gmx.de", "x@gmx.at"), both)
        assertTrue(EmailCheck.domains("hotmail.dx").size <= EmailCheck.MAX_SUGGESTIONS)
    }

    @Test fun severalAtSignsGetNoDomainHint() {
        assertEquals(emptyList(), email("a@b@gmial.com"))
    }

    @Test fun spacesAreRemoved() {
        assertEquals(listOf(FormatHint.EmailSpaces("max@gmail.com")), email("max @gmail.com"))
        assertEquals(listOf(FormatHint.EmailSpaces("maxmustermann@web.de")), email("max mustermann@web.de"))
        assertEquals(listOf(FormatHint.EmailSpaces("max@web.de")), email("max@web.de "))
        assertEquals(listOf(FormatHint.EmailSpaces("max@web.de")), email(" max@web.de"))
        assertEquals(listOf(FormatHint.EmailSpaces("max@web.de")), email("max@web.de\t"))
        assertEquals(emptyList(), email("   "))
    }

    @Test fun missingAtBeforeAProvider() {
        assertEquals("max@gmail.com", (email("max.gmail.com").single() as FormatHint.EmailMissingAt).replacement)
        assertEquals("max@gmail.com", (email("maxgmail.com").single() as FormatHint.EmailMissingAt).replacement)
        assertEquals("anna.b@web.de", (email("anna.b.web.de").single() as FormatHint.EmailMissingAt).replacement)
        assertEquals("anna@t-online.de", (email("annat-online.de").single() as FormatHint.EmailMissingAt).replacement)
        // the longest provider: hotmail.de, not a shorter one at the end
        val hot = email("Kim.hotmail.de").single() as FormatHint.EmailMissingAt
        assertEquals("hotmail.de", hot.domain)
        assertEquals("Kim@hotmail.de", hot.replacement)
        // upper case at the end still counts, the typed name keeps its case
        assertEquals("Max@gmail.com", (email("Max.GMAIL.COM").single() as FormatHint.EmailMissingAt).replacement)
    }

    @Test fun missingAtNeedsANameAndAKnownProvider() {
        assertEquals(emptyList(), email("gmail.com"))
        assertEquals(emptyList(), email(".gmail.com"))
        assertEquals(emptyList(), email("max.firma.de"))
        assertEquals(emptyList(), email("max.mustermann"))
        // a two-letter provider name is never glued to the name: "name.com" is no "na" at me.com
        assertEquals(emptyList(), email("name.com"))
        assertEquals("max@me.com", (email("max.me.com").single() as FormatHint.EmailMissingAt).replacement)
    }

    @Test fun distanceCountsAdjacentSwapsOnce() {
        assertEquals(0, EmailCheck.distance("gmail.com", "gmail.com"))
        assertEquals(1, EmailCheck.distance("gmial.com", "gmail.com"))
        assertEquals(1, EmailCheck.distance("gmai.com", "gmail.com"))
        assertEquals(2, EmailCheck.distance("gmial.co", "gmail.com"))
        assertEquals(3, EmailCheck.distance("", "abc"))
    }

    // --- IBAN -----------------------------------------------------------------------------

    private fun iban(text: String) = FormatGuard.check(FieldKind.TEXT, text, "de").singleOrNull() as FormatHint.Iban?

    @Test fun validIbansFromGermanyAustriaSpain() {
        for (text in listOf("DE89370400440532013000", "DE02120300000000202051", "AT611904300234573201", "AT483200000012345864",
            "ES9121000418450200051332", "ES7921000813610123456789", "CH9300762011623852957", "GB82WEST12345698765432",
            "NL91ABNA0417164300", "FR1420041010050500013M02606")) {
            val hint = iban(text)!!
            assertTrue(hint.valid, text)
        }
        assertEquals("DE89 3704 0044 0532 0130 00", iban("DE89370400440532013000")!!.grouped)
        assertEquals("AT61 1904 3002 3457 3201", iban("AT611904300234573201")!!.grouped)
        assertEquals("ES91 2100 0418 4502 0005 1332", iban("ES9121000418450200051332")!!.grouped)
    }

    @Test fun groupingIsOfferedOnlyForAValidUngroupedIban() {
        assertEquals("DE89 3704 0044 0532 0130 00", iban("DE89370400440532013000")!!.replacement)
        assertEquals("DE89 3704 0044 0532 0130 00", iban("de89370400440532013000")!!.replacement)
        assertEquals("AT61 1904 3002 3457 3201", iban("AT61 19043002 34573201")!!.replacement)
        // already grouped: green dot, nothing to write
        val grouped = iban("DE89 3704 0044 0532 0130 00")!!
        assertTrue(grouped.valid)
        assertNull(grouped.replacement)
        // invalid: red dot, never a change
        val wrong = iban("DE89370400440532013001")!!
        assertFalse(wrong.valid)
        assertNull(wrong.replacement)
    }

    @Test fun invalidIbansAreRed() {
        for (text in listOf("DE89370400440532013001", "DE98370400440532013000", "AT611904300234573202",
            "ES9121000418450200051333", "ES1921000418450200051332", "DE89 3704 0044 0532 0130 01"))
            assertFalse(iban(text)!!.valid, text)
    }

    @Test fun onlyTextThatClearlyIsAnIbanCounts() {
        for (text in listOf("DE8937040044053201300", "DE893704004405320130000", "XX89370400440532013000",
            "DE89-3704-0044-0532-0130-00", "DEAB370400440532013000", "1E89370400440532013000", "IBAN DE89370400440532013000",
            "DE89370400440532013000 ", " DE89370400440532013000", "DE89  3704 0044 0532 0130 00", "Hallo", "DE89 3704",
            "Überweisung an DE89 3704 0044 0532 0130 00"))
            assertNull(iban(text), text)
    }

    @Test fun ibanMathRejectsGarbage() {
        assertFalse(IbanCheck.isValid("DE8"))
        assertFalse(IbanCheck.isValid("DE89-70400440532013000"))
        assertNull(IbanCheck.normalize(""))
    }

    @Test fun ibanIsCheckedOnlyInTextFields() {
        val text = "DE89370400440532013000"
        assertEquals(emptyList(), FormatGuard.check(FieldKind.EMAIL, text, "de"))
        assertEquals(emptyList(), FormatGuard.check(FieldKind.PHONE, text, "de"))
        assertEquals(emptyList(), FormatGuard.check(FieldKind.DECIMAL, text, "de"))
    }

    // --- phone ----------------------------------------------------------------------------

    private fun phone(text: String, language: String = "de", country: String = "") =
        (FormatGuard.check(FieldKind.PHONE, text, language, country).singleOrNull() as FormatHint.Phone?)?.replacement

    @Test fun nationalNumbersGetTheirCountryCode() {
        assertEquals("+49 1512 3456789", phone("015123456789"))
        assertEquals("+49 1512 3456789", phone("0151 23456789"))
        assertEquals("+49 30 1234567", phone("030 1234567"))
        assertEquals("+49 30 1234567", phone("030/1234567"))
        assertEquals("+43 664 1234567", phone("0664 1234567", "de", "AT"))
        assertEquals("+41 44 668 18 00", phone("044 668 18 00", "de", "CH"))
        assertEquals("+34 612 34 56 78", phone("612345678", "es"))
        assertEquals("+1 650-253-0000", phone("(650) 253-0000", "en"))
        assertEquals("+1 650-253-0000", phone("6502530000", "en", "US"))
    }

    @Test fun countryCodesAreNormalised() {
        assertEquals("+49 1512 3456789", phone("004915123456789"))
        assertEquals("+49 1512 3456789", phone("+4915123456789"))
        assertEquals("+49 1512 3456789", phone("+49 (0)151 23456789"))
        assertEquals("+43 1 234567", phone("0043 1 234567"))
        assertEquals("+34 612 34 56 78", phone("+34612345678", "de"))
        // a foreign number with code is recognised whatever the keyboard language
        assertEquals("+1 650-253-0000", phone("+16502530000", "es"))
    }

    @Test fun alreadyFormattedAndInvalidNumbersGetNoHint() {
        assertNull(phone("+49 1512 3456789"))
        assertNull(phone("+43 664 1234567", "de", "AT"))
        assertNull(phone("12345")) // too short
        assertNull(phone("0151")) // too short
        assertNull(phone("0000000000"))
        assertNull(phone("123456789012345678")) // too long
        assertNull(phone("*100#")) // service codes stay as they are
        assertNull(phone("0151 2345678x"))
        assertNull(phone("49+15123456789"))
        assertNull(phone("6502530000", "xx")) // unknown region and no country code
    }

    @Test fun regionFromCountryThenLanguage() {
        assertEquals("AT", PhoneFormat.region("de", "AT"))
        assertEquals("AT", PhoneFormat.region("de", "at"))
        assertEquals("DE", PhoneFormat.region("de", ""))
        assertEquals("ES", PhoneFormat.region("es", ""))
        assertEquals("US", PhoneFormat.region("en", ""))
        assertEquals("ZZ", PhoneFormat.region("xx", ""))
        assertEquals("DE", PhoneFormat.region("de", "Q1"))
    }

    // --- decimals -------------------------------------------------------------------------

    private fun decimal(text: String, language: String) =
        FormatGuard.check(FieldKind.DECIMAL, text, language).singleOrNull() as FormatHint.Decimal?

    @Test fun decimalSeparatorFollowsTheLanguage() {
        assertEquals(FormatHint.Decimal("12,5", ','), decimal("12.5", "de"))
        assertEquals(FormatHint.Decimal("3,50", ','), decimal("3.50", "es"))
        assertEquals(FormatHint.Decimal("-0,25", ','), decimal("-0.25", "de"))
        assertEquals(FormatHint.Decimal("12.5", '.'), decimal("12,5", "en"))
        assertNull(decimal("12,5", "de"))
        assertNull(decimal("12.5", "en"))
        assertEquals(FormatHint.Decimal("12,5", ','), decimal("12.5", "DE"))
    }

    @Test fun decimalNoteIsANoteOnly() {
        assertNull(decimal("12.5", "de")!!.replacement)
    }

    @Test fun decimalAmbiguousOrUnknownGetsNoNote() {
        assertNull(decimal("1.000", "de")) // thousands separator
        assertNull(decimal("1,000", "en"))
        assertNull(decimal("12", "de"))
        assertNull(decimal("12.", "de"))
        assertNull(decimal(".5", "de"))
        assertNull(decimal("1.2.3", "de"))
        assertNull(decimal("12.5", "xx"))
        assertNull(decimal("abc", "de"))
    }

    // --- general --------------------------------------------------------------------------

    @Test fun emptyAndLongTextsAreNotChecked() {
        for (kind in FieldKind.entries) assertEquals(emptyList(), FormatGuard.check(kind, "", "de"))
        val long = "a".repeat(FormatGuard.MAX_LENGTH) + "@gmial.com"
        assertEquals(emptyList(), email(long))
    }

    @Test fun plainTextGetsNoEmailOrPhoneHint() {
        assertEquals(emptyList(), FormatGuard.check(FieldKind.TEXT, "max@gmial.com", "de"))
        assertEquals(emptyList(), FormatGuard.check(FieldKind.TEXT, "0151 23456789", "de"))
        assertEquals(emptyList(), FormatGuard.check(FieldKind.TEXT, "12.5", "de"))
    }
}
