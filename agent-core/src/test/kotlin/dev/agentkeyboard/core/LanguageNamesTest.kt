// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class LanguageNamesTest {
    /** Every built-in command name at the time of stage 24g plus the language command itself. */
    private val commands = listOf("math", "contact", "url", "such", "zeichen", "emoji", "conv", "snip", "clip", "wf",
        "ki", "datum", "termin", "ort", "qr", "translate", "wecker", "timer", "lang")
    private val matcher = LanguageMatcher(listOf("de", "en-US", "es").map {
        LanguageMatcher.Language(it.substringBefore('-'), LanguageNames.aliases(it))
    }, commands)

    private fun match(typed: String) = matcher.match(typed)

    @Test fun ownerExamplesNameTheirLanguage() {
        for (typed in listOf("sp", "spain", "spanisch", "spnaish", "espanol", "español", "ESPAÑA", "castellano", "spa", "es"))
            assertEquals(listOf("es"), match(typed), typed)
        for (typed in listOf("ger", "deutsch", "deustch", "germany", "deutschland", "österreich", "oesterreich", "de", "Deutsch"))
            assertEquals(listOf("de"), match(typed), typed)
        for (typed in listOf("eng", "english", "englisch", "inglés", "ingles", "usa", "uk", "england", "en", "amerika"))
            assertEquals(listOf("en"), match(typed), typed)
    }

    @Test fun prefixesFromTwoLetters() {
        assertEquals(listOf("es"), match("spani"))
        assertEquals(listOf("de"), match("deut"))
        assertEquals(listOf("en"), match("engl"))
        assertTrue(match("s").isEmpty()) // one letter is never enough
        assertTrue(match("").isEmpty())
    }

    @Test fun mentionsAndCommandTyposNameNothing() {
        for (typed in listOf("lewin", "max", "anna", "tom", "paul", "lisa", "peter", "maria", "jan", "ben", "leon", "emma",
                "mia", "sara", "chris", "david", "julia", "felix", "mtah", "tranlate", "math", "url", "kino", "x"))
            assertTrue(match(typed).isEmpty(), typed)
    }

    @Test fun genericWordsNameAllLanguages() {
        assertEquals(listOf("de", "en", "es"), match("sprache"))
        assertEquals(listOf("de", "en", "es"), match("Language"))
    }

    @Test fun distanceRules() {
        assertEquals(0, LanguageNames.allowedEdits(2))
        assertEquals(1, LanguageNames.allowedEdits(3))
        assertEquals(1, LanguageNames.allowedEdits(5))
        assertEquals(2, LanguageNames.allowedEdits(6))
        assertEquals(1, LanguageNames.distance("spnaish", "spanish")) // a swap is one edit
        assertEquals(1, LanguageNames.distance("deustch", "deutsch"))
        assertEquals(2, LanguageNames.distance("spansh", "spanisch"))
        assertEquals(3, LanguageNames.distance("", "abc"))
        assertFalse(LanguageNames.isClose("ef", "es")) // two letters: exact only
        assertTrue(LanguageNames.isClose("gre", "ger"))
        assertFalse(LanguageNames.isClose("gxx", "ger"))
        assertTrue(LanguageNames.isClose("englsih", "english"))
        assertFalse(LanguageNames.isClose("enxxsh", "english"))
    }

    @Test fun normalization() {
        assertEquals("espanol", LanguageNames.normalize("Español"))
        assertEquals("osterreich", LanguageNames.normalize("Österreich"))
        assertEquals("grossbritannien", LanguageNames.normalize("Großbritannien"))
        assertEquals("unitedstates", LanguageNames.normalize("United States"))
        assertEquals("ingles", LanguageNames.normalize("inglés"))
    }

    @Test fun aliasesComeFromLocaleAndTheTables() {
        val spanish = LanguageNames.aliases("es")
        for (alias in listOf("es", "spa", "spanish", "spanisch", "espanol", "spain", "spanien", "espana", "mexico", "mexiko", "mx", "sp"))
            assertTrue(alias in spanish, alias)
        val english = LanguageNames.aliases("en-GB")
        for (alias in listOf("en", "eng", "english", "englisch", "ingles", "gb", "us", "unitedstates", "england"))
            assertTrue(alias in english, alias)
        // a language Locale does not know only has its code
        assertEquals(setOf("zz"), LanguageNames.aliases("zz"))
        assertTrue(LanguageNames.aliases("").isEmpty())
    }

    @Test fun aliasesCloseToACommandNameAreOnlyMatchedExactly() {
        val urdu = LanguageMatcher(listOf(LanguageMatcher.Language("xx", listOf("urlx", "ortu", "kinder"))), commands)
        assertTrue(urdu.match("urly").isEmpty()) // "urlx" is a typo of "url" away: no typo match
        assertEquals(listOf("xx"), urdu.match("urlx"))
        assertEquals(listOf("xx"), urdu.match("kindr")) // far from every command
    }

    @Test fun exactMatchesWinAndSeveralLanguagesMayMatch() {
        val two = LanguageMatcher(listOf(LanguageMatcher.Language("a", listOf("canada", "spanien")),
            LanguageMatcher.Language("b", listOf("castellano", "ca", "spanish"))))
        assertEquals(listOf("b"), two.match("ca")) // exact beats the prefix of "canada"
        assertEquals(listOf("a"), two.match("cana"))
        assertEquals(listOf("a", "b"), two.match("span")) // both start with it: both, in order
        assertEquals(listOf("b", "a"), two.match("spanisj")) // a typo of "spanish" ranks before "spanien"
    }
}
