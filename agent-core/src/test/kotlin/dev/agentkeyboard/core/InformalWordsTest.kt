// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class InformalWordsTest {
    private val de = listOf("de")

    private fun repoFile(path: String): File =
        listOf(File("../$path"), File(path)).firstOrNull { it.isFile } ?: fail("$path not found from ${File(".").absoluteFile}")

    /** The shipped list, read from the app's assets. */
    private val shipped: Set<String> by lazy { InformalWords.parse(repoFile("app/src/main/assets/agent/informal_words.txt").readText()) }

    @Test fun `words are compared in one normal form`() {
        assertEquals("gecovert", InformalWords.normalize("GeCovert"))
        assertEquals("y'all", InformalWords.normalize("Y’all"))
        // NFC: a decomposed umlaut equals the composed one
        assertEquals(InformalWords.normalize("schön"), InformalWords.normalize("schön"))
    }

    @Test fun `a list file has one word per line and ignores blanks and comments`() {
        assertEquals(setOf("lowkey", "gecovert"), InformalWords.parse("# list\nlowkey\n\n  gecovert \n"))
    }

    @Test fun `the list applies to German and English typing only`() {
        assertTrue(InformalWords.appliesTo(listOf("de")))
        assertTrue(InformalWords.appliesTo(listOf("es", "EN")))
        assertFalse(InformalWords.appliesTo(listOf("es", "fr", null)))
        assertFalse(InformalWords.appliesTo(emptyList()))
    }

    @Test fun `a listed word stays as typed in any case`() {
        val words = setOf("lowkey", "gecovert")
        assertTrue(InformalWords.protects(words, "lowkey", de))
        assertTrue(InformalWords.protects(words, "LowKey", de))
        assertTrue(InformalWords.protects(words, "gecovert", listOf("es", "de")))
        assertFalse(InformalWords.protects(words, "lowkay", de))
        assertFalse(InformalWords.protects(words, "lowkey", listOf("es")), "the list is for German and English typing")
        assertFalse(InformalWords.protects(emptySet(), "lowkey", de))
        assertFalse(InformalWords.protects(words, "", de))
        assertFalse(InformalWords.protects(words, "x".repeat(InformalWords.MAX_LENGTH + 1), de))
    }

    @Test fun `stretched letters and laughter are emphasis, not mistakes`() {
        for (w in listOf("neee", "jaaaa", "soooo", "hääää", "yesssss", "heyyyy", "lmaoooo", "Neeee", "hahaha", "hahahaha", "hehehe", "HAHAHA", "lololol",
            "Hallooo", "grrrr"))
            assertTrue(InformalWords.isExpressive(w), w)
        // two letters in a row are spelling, a repeat inside the word is a compound or a name
        for (w in listOf("nee", "jaa", "hallo", "Schifffahrt", "Kaffeeernte", "Zoo", "haha", "mama", "Anna", "wenn", "ss", "ab", "bee", "see", "Teeei",
            "addresss", "Adresssse", "yesss", "wennn", "alll"))
            assertFalse(InformalWords.isExpressive(w), w)
        assertFalse(InformalWords.isExpressive("ja!!!"), "only letters count")
        assertFalse(InformalWords.isExpressive("nee3333"))
        assertTrue(InformalWords.protects(emptySet(), "neee", de))
        assertFalse(InformalWords.protects(emptySet(), "neee", listOf("es")))
    }

    // ---- the shipped list ----

    @Test fun `the shipped list is in the normal form, sorted and without doubles`() {
        val lines = repoFile("app/src/main/assets/agent/informal_words.txt").readLines().filter { it.isNotEmpty() }
        assertEquals(lines.size, lines.toSet().size, "doubles")
        assertEquals(lines.sorted(), lines, "sorted")
        for (line in lines) {
            assertEquals(InformalWords.normalize(line), line, "normal form of $line")
            assertTrue(line.length >= 3, "too short: $line")
            assertTrue(line.none { it.isWhitespace() }, "a phrase: $line")
            assertTrue(line.all { it.isLetterOrDigit() || it == '\'' || it == '-' }, "not a typeable word: $line")
        }
    }

    @Test fun `the shipped list holds the words it is made for and no ordinary words`() {
        assertTrue(shipped.size in 2000..4000, "size ${shipped.size}")
        for (w in listOf("gecovert", "lowkey", "metalcore", "nich", "digga", "rizzing", "respeccen", "lulz"))
            assertTrue(w in shipped, w)
        // standard words stay with the engine: a protected word is never corrected
        for (w in listOf("nicht", "für", "und", "the", "interessant", "vielleicht", "wahrscheinlich", "dont", "cant", "youre"))
            assertFalse(w in shipped, w)
    }

    @Test fun `editorial cases of the data package`() {
        val rows = javaClass.getResourceAsStream("/informal_cases.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { !it.startsWith("#") && it.isNotBlank() }.map { it.split('\t') }
        val protect = rows.filter { it[0] == "protect" }.map { it[1] }
        val open = rows.filter { it[0] == "open" }.map { it[1] }
        assertTrue(protect.size >= 40 && open.size >= 250, "fixture size ${protect.size}/${open.size}")
        // slang, Denglish, colloquial and domain words of the cases stay as typed ...
        for (w in protect) assertTrue(InformalWords.protects(shipped, w, de), "protect $w")
        // ... and no misspelling or ordinary word of the cases is on the list (it would never be corrected)
        for (w in open) assertFalse(InformalWords.protects(shipped, w, de), "open $w")
    }
}
