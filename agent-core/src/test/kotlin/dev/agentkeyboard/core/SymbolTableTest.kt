// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class SymbolTableTest {
    private fun chars(query: String) = SpecialCharacters.table.find(query).map { it.symbol }
    private fun emojis(query: String) = EmojiNames.table.find(query).map { it.symbol }

    @Test fun findsCharactersByGermanAndEnglishName() {
        assertEquals(listOf("→", "←", "↑", "↓", "↔", "⇒"), chars("pfeil"))
        assertEquals(listOf("→", "←", "↑", "↓", "↔", "⇒"), chars("arrow"))
        assertEquals(listOf("→"), chars("Pfeil rechts"))
        assertEquals("°", chars("grad").first())
        assertEquals("°", chars("degree").first())
        assertEquals(listOf("≠"), chars("ungleich"))
        assertEquals("≈", chars("ungefähr").first())
        assertEquals("≈", chars("ungefahr").first()) // umlauts may be typed without dots
        assertEquals(listOf("±"), chars("plusminus"))
        assertEquals(listOf("∞"), chars("unendlich"))
        assertEquals(listOf("€"), chars("euro"))
        assertEquals(listOf("©"), chars("copyright"))
        assertEquals(listOf("✓", "✔"), chars("häkchen"))
        assertEquals(listOf("✓", "✔"), chars("check"))
        assertEquals("✗", chars("kreuz").first())
        assertEquals("♥", chars("herz").first())
        assertEquals("★", chars("stern").first())
        assertEquals("–", chars("gedankenstrich").first())
        assertEquals(listOf("„", "“", "»", "«", "”"), chars("anführungszeichen"))
        assertEquals(listOf("×", "♂"), chars("mal")) // "male" starts with it
        assertEquals(listOf("÷"), chars("geteilt"))
        assertEquals("√", chars("wurzel").first())
        assertEquals(listOf("π", "¶"), chars("pi")) // "pilcrow" only starts with it
        assertEquals(listOf("µ"), chars("mikro"))
        assertEquals(listOf("§"), chars("paragraph"))
        assertEquals(listOf("‰"), chars("promille"))
        assertEquals(listOf("²"), chars("hoch2"))
        assertTrue(SpecialCharacters.table.entries.size >= 60)
    }

    @Test fun exactNamesComeFirstAndQueriesWithoutLettersFindNothing() {
        // 🥰 ("Herzen") and 😍 ("Herzaugen") stand before ❤️ in the table but only start with "herz"
        assertEquals(listOf("❤️", "🥰", "😍"), EmojiNames.table.find("herz").take(3).map { it.symbol })
        assertEquals("✓", chars("haken").first())
        assertTrue(chars("").isEmpty())
        assertTrue(chars("  ").isEmpty())
        assertTrue(chars("!?").isEmpty())
        assertTrue(chars("xyzzy").isEmpty())
        assertEquals(3, SpecialCharacters.table.find("pfeil", limit = 3).size)
    }

    @Test fun findsEmojisByKeyword() {
        assertEquals("👍", emojis("daumen").first())
        assertEquals("👍", emojis("thumbs up").first())
        assertEquals("❤️", emojis("herz").first())
        assertTrue("💚" in emojis("herz"))
        assertEquals(listOf("💚"), emojis("herz grün"))
        assertEquals(listOf("🥳", "🎉"), emojis("party"))
        assertEquals(listOf("🐶", "💯"), emojis("hund")) // "hundert"
        assertEquals(listOf("🐶"), emojis("dog"))
        assertEquals("☕", emojis("kaffee").first())
        assertEquals("😂", emojis("lol").first())
        assertTrue(emojis("bär").contains("🐻"))
        assertTrue(emojis("bar").contains("🐻"))
        assertTrue(EmojiNames.table.entries.size >= 150)
        assertTrue(emojis("").isEmpty())
        assertTrue(emojis("qqq").isEmpty())
    }

    @Test fun tablesHaveNoDuplicateSymbolsAndOnlyFoldedNames() {
        for (table in listOf(SpecialCharacters.table, EmojiNames.table)) {
            assertEquals(table.entries.size, table.entries.map { it.symbol }.toSet().size)
            for (entry in table.entries) {
                assertTrue(entry.names.isNotEmpty(), entry.symbol)
                for (name in entry.names) assertEquals(SymbolTable.fold(name), name)
            }
        }
        assertEquals("pfeilrechts", SymbolTable.fold("Pfeil rechts"))
        assertEquals("grossergleich", SymbolTable.fold("größer gleich"))
    }

    @Test fun groupsShowAllTheirSymbolsFirstThenNameMatches() {
        val arrows = SpecialCharacters.table.search("pfeile").map { it.symbol }
        assertEquals(listOf("→", "←", "↑", "↓", "↔", "↕", "↗", "↘", "↩", "⇒", "⇐", "⇔"), arrows)
        assertEquals(arrows, SpecialCharacters.table.search("Arrows").map { it.symbol })
        assertEquals(listOf("€", "$", "£", "¥", "¢", "₹", "₽", "₩", "₺", "₿"), SpecialCharacters.table.search("Währung").map { it.symbol })
        assertEquals(SpecialCharacters.table.search("währung").map { it.symbol }, SpecialCharacters.table.search("wahrung").map { it.symbol })
        val typography = SpecialCharacters.table.search("typografie").map { it.symbol }
        assertTrue(typography.containsAll(listOf("–", "—", "…", "·", "•")))
        val symbols = SpecialCharacters.table.search("symbole").map { it.symbol }
        assertTrue(symbols.containsAll(listOf("§", "©", "®", "™", "°", "‰")))
        assertTrue(SpecialCharacters.table.search("griechisch").map { it.symbol }.containsAll(listOf("α", "β", "π", "Ω", "θ")))
        // a group name that is also a symbol name: the whole group, then nothing twice
        val ticks = SpecialCharacters.table.search("häkchen").map { it.symbol }
        assertEquals(listOf("✓", "✔", "✗", "✘", "☐", "☑", "☒"), ticks)
        val quotes = SpecialCharacters.table.search("anführungszeichen").map { it.symbol }
        assertEquals(quotes.distinct(), quotes)
        assertTrue(quotes.containsAll(listOf("„", "“", "”", "»", "«", "‚", "‘")))
        // a word that is no group: the name search as before
        assertEquals(chars("pfeil"), SpecialCharacters.table.search("pfeil").map { it.symbol })
        assertTrue(SpecialCharacters.table.group("").isEmpty())
        assertTrue(SpecialCharacters.table.group("pfei").isEmpty()) // groups only by their full name

        assertEquals(listOf("🐶", "🐱", "🐭"), EmojiNames.table.search("tiere").take(3).map { it.symbol })
        assertEquals(18, EmojiNames.table.group("tiere").size)
        assertTrue("❤️" in EmojiNames.table.search("herzen").map { it.symbol })
        assertEquals(EmojiNames.table.search("hände"), EmojiNames.table.search("haende"))
        assertTrue("🍕" in EmojiNames.table.search("essen").map { it.symbol })
        assertTrue("⛅" in EmojiNames.table.search("wetter").map { it.symbol })
    }

    @Test fun everyGroupNamesOnlySymbolsOfItsTableAndIsUnique() {
        val expected = mapOf(
            SpecialCharacters.table to listOf("pfeile", "mathe", "währung", "anführungszeichen", "häkchen", "typografie", "symbole", "griechisch"),
            EmojiNames.table to listOf("gesichter", "herzen", "hände", "tiere", "essen", "natur", "wetter", "sport", "reisen", "symbole"),
        )
        for ((table, names) in expected) {
            assertEquals(names, table.groups.map { it.name })
            val symbols = table.entries.map { it.symbol }.toSet()
            for (group in table.groups) {
                assertTrue(group.symbols.isNotEmpty(), group.name)
                assertEquals(group.symbols.distinct(), group.symbols, group.name)
                assertEquals(emptyList(), group.symbols.filter { it !in symbols }, group.name)
                assertEquals(group.symbols, table.group(group.name).map { it.symbol }, group.name)
            }
            val keys = table.groups.flatMap { it.keys }
            assertEquals(keys.distinct(), keys)
        }
    }
}
