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
}
