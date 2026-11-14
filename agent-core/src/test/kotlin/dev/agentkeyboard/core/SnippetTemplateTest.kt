// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.DateFormatting.Language
import dev.agentkeyboard.core.SnippetTemplate.Issue.Kind
import org.junit.Test
import kotlin.test.*

class SnippetTemplateTest {
    // Thursday, 12 November 2026, 18:45
    private fun values(
        today: CivilDate = CivilDate(2026, 11, 12),
        language: Language = Language.DE,
        clipboard: String? = null,
        fields: Map<String, String> = emptyMap(),
    ) = SnippetTemplate.Values(today, ClockTime(18, 45), language, clipboard, fields)

    private fun expand(text: String, values: SnippetTemplate.Values = values()) = SnippetTemplate.parse(text).expand(values).text

    @Test fun theOwnersExampleWorks() {
        val template = SnippetTemplate.parse("Hallo {?Name}, danke für die Einladung am {datum+2t}, leider kann ich nicht. {cursor}")
        assertEquals(listOf("Name"), template.fields)
        assertTrue(template.issues.isEmpty())
        val expansion = template.expand(values(fields = mapOf("Name" to "Lena")))
        assertEquals("Hallo Lena, danke für die Einladung am 14.11.2026, leider kann ich nicht. ", expansion.text)
        assertEquals(expansion.text.length, expansion.cursor)
    }

    @Test fun datesMoveByDaysAndWeeksAcrossMonthAndYearEnds() {
        val newYearsEve = values(today = CivilDate(2026, 12, 31))
        assertEquals("31.12.2026", expand("{datum}", newYearsEve))
        assertEquals("01.01.2027", expand("{datum+1t}", newYearsEve))
        assertEquals("30.12.2026", expand("{datum-1t}", newYearsEve))
        assertEquals("07.01.2027", expand("{datum+1w}", newYearsEve))
        assertEquals("17.12.2026", expand("{datum-2w}", newYearsEve))
        assertEquals("31.12.2026", expand("{datum+0t}", newYearsEve))
        assertEquals("29.02.2028", expand("{datum-1t}", values(today = CivilDate(2028, 3, 1)))) // leap year
        assertEquals("28.02.2027", expand("{datum-1t}", values(today = CivilDate(2027, 3, 1))))
        assertEquals("01.01.2026", expand("{datum+1t}", values(today = CivilDate(2025, 12, 31))))
        assertEquals("31.01.2026", expand("{datum-1t}", values(today = CivilDate(2026, 2, 1))))
        assertEquals("01.12.2026", expand("{datum+1t}", values(today = CivilDate(2026, 11, 30))))
        assertEquals("07.09.2029", expand("{datum+999t}", values(today = CivilDate(2026, 12, 13))))
        // outside the calendar's years: empty, never an exception
        assertEquals("", expand("{datum+1t}", values(today = CivilDate(9999, 12, 31))))
    }

    @Test fun weekdaysAndTimesFollowTheLanguage() {
        assertEquals("Donnerstag", expand("{wochentag}"))
        assertEquals("Samstag", expand("{wochentag+2t}"))
        assertEquals("Donnerstag", expand("{wochentag+1w}"))
        assertEquals("Mittwoch", expand("{wochentag-1t}"))
        assertEquals("Freitag", expand("{wochentag+1t}", values(today = CivilDate(2026, 12, 31))))
        assertEquals("18:45", expand("{uhrzeit}"))
        assertEquals("Thursday 11/12/2026 6:45 PM", expand("{wochentag} {datum} {uhrzeit}", values(language = Language.EN)))
        assertEquals("jueves 12/11/2026 18:45", expand("{wochentag} {datum} {uhrzeit}", values(language = Language.ES)))
    }

    @Test fun keyboardLanguagesMapWithGermanAsDefault() {
        assertEquals(Language.DE, SnippetTemplate.language("de"))
        assertEquals(Language.DE, SnippetTemplate.language("de_CH"))
        assertEquals(Language.EN, SnippetTemplate.language("en_US"))
        assertEquals(Language.EN, SnippetTemplate.language("EN"))
        assertEquals(Language.ES, SnippetTemplate.language("es-419"))
        assertEquals(Language.DE, SnippetTemplate.language("fr"))
        assertEquals(Language.DE, SnippetTemplate.language(""))
    }

    @Test fun placeholderNamesIgnoreCase() {
        assertEquals("12.11.2026 18:45 Donnerstag", expand("{Datum} {UHRZEIT} {WochenTag}"))
        assertEquals("14.11.2026", expand("{DATUM+2T}"))
        assertEquals("x", expand("{CURSOR}x"))
    }

    @Test fun clipboardIsInsertedOnlyWhenGiven() {
        assertEquals("Link: https://example.org", expand("Link: {zwischenablage}", values(clipboard = "https://example.org")))
        assertEquals("Link: ", expand("Link: {zwischenablage}"))
        assertTrue(SnippetTemplate.parse("{zwischenablage}").usesClipboard)
        assertFalse(SnippetTemplate.parse("{datum}").usesClipboard)
        assertFalse(SnippetTemplate.parse("{{zwischenablage}").usesClipboard)
    }

    @Test fun fieldsAreAskedOnceByNameWithoutCase() {
        val template = SnippetTemplate.parse("{?Name} und {?Ort}, liebe {?name}! {? Ort }")
        assertEquals(listOf("Name", "Ort"), template.fields)
        assertEquals("Anna und Rom, liebe Anna! Rom", template.expand(values(fields = mapOf("NAME" to "Anna", "ort" to "Rom"))).text)
        // an unfilled field is empty
        assertEquals("Hallo !", expand("Hallo {?Name}!"))
        assertEquals(listOf("Vor- und Zuname", "Nr. 2"), SnippetTemplate.parse("{?Vor- und Zuname} {?Nr. 2}").fields)
    }

    @Test fun invalidFieldNamesStayLiteral() {
        for (bad in listOf("{?}", "{? }", "{?a:b}", "{??}", "{?" + "x".repeat(SnippetTemplate.MAX_FIELD_NAME + 1) + "}")) {
            val template = SnippetTemplate.parse(bad)
            assertTrue(template.fields.isEmpty(), bad)
            assertEquals(bad, template.expand(values()).text)
            assertEquals(listOf(SnippetTemplate.Issue(Kind.UNKNOWN, bad)), template.issues)
        }
    }

    @Test fun theCursorMarkIsRemovedAndOnlyTheFirstCounts() {
        val expansion = SnippetTemplate.parse("Hallo {cursor}Welt{cursor}!").expand(values())
        assertEquals("Hallo Welt!", expansion.text)
        assertEquals(6, expansion.cursor)
        assertEquals(listOf(SnippetTemplate.Issue(Kind.EXTRA_CURSOR, "{CURSOR}")), SnippetTemplate.parse("{cursor}{CURSOR}").issues)
        assertNull(SnippetTemplate.parse("ohne").expand(values()).cursor)
        assertEquals(0, SnippetTemplate.parse("{cursor}am Anfang").expand(values()).cursor)
        // the index counts UTF-16 units, as the editor does
        assertEquals(2, SnippetTemplate.parse("😀{cursor}x").expand(values()).cursor)
        // after expanded values
        assertEquals("Anna".length + 1, SnippetTemplate.parse("{?n} {cursor}").expand(values(fields = mapOf("n" to "Anna"))).cursor)
    }

    @Test fun doubleBracesAreALiteralBrace() {
        assertEquals("{datum}", expand("{{datum}"))
        assertEquals("{ und }", expand("{{ und }"))
        assertEquals("a{12.11.2026", expand("a{{{datum}"))
        assertEquals("}}", expand("}}"))
        assertTrue(SnippetTemplate.parse("{{datum}").issues.isEmpty())
        assertTrue(SnippetTemplate.parse("{{datum}").isPlain)
        assertFalse(SnippetTemplate.parse("{datum}").isPlain)
    }

    @Test fun unknownPlaceholdersStayAsTypedAndAreReported() {
        val template = SnippetTemplate.parse("{date} {datum+2} {datum+1x} {datum + 1t} {datum+1000t} {} {uhrzeit}")
        assertEquals("{date} {datum+2} {datum+1x} {datum + 1t} {datum+1000t} {} 18:45", template.expand(values()).text)
        assertEquals(listOf("{date}", "{datum+2}", "{datum+1x}", "{datum + 1t}", "{datum+1000t}", "{}"), template.issues.map { it.text })
        assertTrue(template.issues.all { it.kind == Kind.UNKNOWN })
    }

    @Test fun malformedAndNestedBracesNeverThrowAndStayLiteral() {
        assertEquals("{a12.11.2026}", expand("{a{datum}}"))
        assertEquals(listOf(SnippetTemplate.Issue(Kind.UNCLOSED, "{a")), SnippetTemplate.parse("{a{datum}}").issues)
        assertEquals("{datum", expand("{datum"))
        assertEquals(Kind.UNCLOSED, SnippetTemplate.parse("{datum").issues.single().kind)
        assertEquals("{datum\n}", expand("{datum\n}"))
        assertEquals("{", expand("{"))
        assertEquals("}{", expand("}{"))
        val long = "{" + "x".repeat(SnippetTemplate.MAX_PLACEHOLDER + 5) + "}"
        assertEquals(long, expand(long))
        assertEquals(Kind.UNCLOSED, SnippetTemplate.parse(long).issues.first().kind)
        assertEquals("{{", expand("{{{{"))
        // every mix of braces and words parses and expands without an exception
        val pieces = listOf("{", "}", "{{", "?", "datum", "cursor", "+1t", "\n", "x")
        var seed = 7
        repeat(2000) {
            val text = buildString {
                repeat(8) { seed = (seed * 1103515245 + 12345) and 0x7fffffff; append(pieces[seed % pieces.size]) }
            }
            SnippetTemplate.parse(text).expand(values(fields = mapOf("x" to "y")))
        }
    }

    @Test fun plainTextExpandsToItself() {
        val text = "Anna Müller\nHauptstraße 1\n10115 Berlin"
        val template = SnippetTemplate.parse(text)
        assertTrue(template.isPlain)
        assertTrue(template.issues.isEmpty())
        assertEquals(text, template.expand(values()).text)
        assertEquals("", SnippetTemplate.parse("").expand(values()).text)
    }

    @Test fun literalTemplatesKeepEveryBrace() {
        val text = "{{datum}} {datum} {?Name} {cursor}"
        val template = SnippetTemplate.literal(text)
        assertTrue(template.isPlain)
        assertTrue(template.fields.isEmpty())
        assertTrue(template.issues.isEmpty())
        val expansion = template.expand(values())
        assertEquals(text, expansion.text)
        assertNull(expansion.cursor)
    }

    @Test fun valuesNeverShowTheirTexts() {
        assertFalse(values(clipboard = "geheim", fields = mapOf("a" to "geheim")).toString().contains("geheim"))
        assertFalse(SnippetTemplate.parse("geheim").expand(values()).toString().contains("geheim"))
    }

    @Test fun appRestrictions() {
        assertTrue(SnippetApps.isValidPackage("com.whatsapp"))
        assertTrue(SnippetApps.isValidPackage("org.telegram.messenger"))
        for (bad in listOf("", "whatsapp", "com.", ".com", "com..x", "1com.x", "com.what sapp", "com.x/y", "a." + "b".repeat(SnippetApps.MAX_PACKAGE)))
            assertFalse(SnippetApps.isValidPackage(bad), bad)
        assertTrue(SnippetApps.allows(emptyList(), null))
        assertTrue(SnippetApps.allows(emptyList(), "com.x"))
        assertTrue(SnippetApps.allows(listOf("com.x", "com.y"), "com.y"))
        assertFalse(SnippetApps.allows(listOf("com.x"), "com.y"))
        assertFalse(SnippetApps.allows(listOf("com.x"), null))
    }
}
