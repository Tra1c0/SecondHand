// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class LanguageNamesTest {
    /** Every built-in command name at the time of stage 24g plus the language command itself. */
    private val commands = listOf("math", "contact", "url", "such", "zeichen", "emoji", "conv", "snip", "clip", "wf",
        "ki", "datum", "termin", "ort", "qr", "translate", "wecker", "timer", "lang")
    private val matcher = LanguageMatcher(listOf("de", "en-US", "es").map {
        LanguageMatcher.Language(it.substringBefore('-'), LanguageNames.classifiedAliases(it))
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

    @Test fun prefixesFromThreeLetters() {
        assertEquals(listOf("es"), match("spani"))
        assertEquals(listOf("de"), match("deut"))
        assertEquals(listOf("en"), match("engl"))
        assertEquals(listOf("es"), match("mex")) // a started country name
        assertTrue(match("s").isEmpty()) // one letter is never enough
        assertTrue(match("").isEmpty())
        // two letters only name a language exactly: no started names, no country codes
        for (typed in listOf("us", "ca", "co", "pe", "pa", "ar", "ch", "au", "me", "al", "an", "re", "ge", "ne", "un", "ve",
                "mx", "gb", "at", "ge", "ma", "da"))
            assertTrue(match(typed).isEmpty(), typed)
        for ((typed, id) in listOf("es" to "es", "de" to "de", "en" to "en", "sp" to "es", "uk" to "en"))
            assertEquals(listOf(id), match(typed), typed)
    }

    @Test fun mentionsAndCommandTyposNameNothing() {
        for (typed in listOf("lewin", "max", "anna", "tom", "paul", "lisa", "peter", "maria", "jan", "ben", "leon", "emma",
                "mia", "sara", "chris", "david", "julia", "felix", "mtah", "tranlate", "math", "url", "kino", "x"))
            assertTrue(match(typed).isEmpty(), typed)
    }

    @Test fun theReviewersMentionsNameNothing() {
        // isa≠usa, gerd/gero≠ger (3-letter short forms take no typos), gerda≠german (5 letters: one typo),
        // inge/ines≠ingles, espen≠espana (no typos of countries), den≠deu (codes exact only)
        for (typed in listOf("isa", "gerd", "gero", "gerda", "inge", "ines", "espen", "den", "lana", "lange", "matt", "clio",
                "uri", "urs", "gerhard", "ingrid", "colin", "irene", "esteban", "dennis", "brian", "casper", "vera", "angela"))
            assertTrue(match(typed).isEmpty(), typed)
        // the trade-off that stays: three started letters name a language while a mention is typed
        // (@ger…, @ing…, @ale…, @col…), and some whole names are aliases or close to one
        for ((typed, id) in listOf("ger" to "de", "ing" to "en", "ale" to "de", "col" to "es", "ted" to "de", "per" to "es",
                "alle" to "de", "peru" to "es", "amelia" to "en", "alexan" to "de"))
            assertEquals(listOf(id), match(typed), typed)
    }

    @Test fun typosNeedFourLettersAndTheFirstLetter() {
        assertEquals(listOf("es"), match("spnaish"))
        assertEquals(listOf("es"), match("spansh"))
        assertEquals(listOf("en"), match("englsih"))
        assertEquals(listOf("de"), match("deustch"))
        assertTrue(match("psanish").isEmpty()) // first letter swapped: no typo
        assertTrue(match("ermany").isEmpty()) // country names take no typos, and the first letter is missing
        assertTrue(match("gre").isEmpty()) // three letters take no typos
        assertTrue(match("deu").let { it == listOf("de") }) // the ISO 639-2 code, exactly
        assertTrue(match("deux").isEmpty()) // codes take no typos
    }

    @Test fun aFinishedWordOnlyNamesExactly() {
        for ((typed, id) in listOf("sp" to "es", "spain" to "es", "español" to "es", "ger" to "de", "deutsch" to "de",
                "usa" to "en", "inglés" to "en", "castellano" to "es"))
            assertEquals(listOf(id), matcher.match(typed, exactOnly = true), typed)
        for (typed in listOf("spnaish", "spa n", "deut", "ale", "alle", "amelia", "deustch"))
            assertTrue(matcher.match(typed, exactOnly = true).isEmpty(), typed)
        assertEquals(listOf("de", "en", "es"), matcher.match("sprache", exactOnly = true))
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
        // the shorter word sets the allowance; up to four letters only a swap of neighbours
        assertTrue(LanguageNames.isClose("mtah", "math"))
        assertFalse(LanguageNames.isClose("matt", "math"))
        assertFalse(LanguageNames.isClose("lange", "lang"))
        assertFalse(LanguageNames.isClose("gerda", "german")) // five letters: one typo, this is two
        assertTrue(LanguageNames.isClose("spansh", "spanish"))
        assertTrue(LanguageNames.isTypo("deustch", "deutsch"))
        assertFalse(LanguageNames.isTypo("psanish", "spanish")) // other first letter
        assertFalse(LanguageNames.isTypo("gre", "ger")) // fewer than four letters
        assertTrue(LanguageNames.isNear("urly", "url")) // the looser check for reserved names
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
        for (alias in listOf("es", "spa", "spanish", "spanisch", "espanol", "spain", "spanien", "espana", "mexico", "mexiko", "sp"))
            assertTrue(alias in spanish, alias)
        val english = LanguageNames.aliases("en-GB")
        for (alias in listOf("en", "eng", "english", "englisch", "ingles", "unitedstates", "unitedkingdom", "england"))
            assertTrue(alias in english, alias)
        // country codes start too many mentions (@us, @ca, @ch): no aliases
        for (code in listOf("gb", "us", "ca", "au")) assertFalse(code in english, code)
        assertFalse("mx" in spanish)
        val kinds = LanguageNames.classifiedAliases("es")
        assertEquals(AliasKind.CODE, kinds["es"])
        assertEquals(AliasKind.CODE, kinds["spa"])
        assertEquals(AliasKind.NAME, kinds["spanisch"])
        assertEquals(AliasKind.SHORT, kinds["sp"])
        assertEquals(AliasKind.COUNTRY, kinds["espana"])
        // names in another script normalize to nothing: only the Latin-script names remain
        assertTrue(LanguageNames.aliases("ru").none { it.isEmpty() })
        assertEquals("", LanguageNames.normalize("Русский"))
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
