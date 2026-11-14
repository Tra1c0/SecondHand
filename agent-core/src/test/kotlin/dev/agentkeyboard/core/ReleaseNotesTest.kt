// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class ReleaseNotesTest {
    @Test fun newestCarriesTheBodyOfItsOwnRelease() {
        fun release(n: Long, body: String) = ReleaseInfo("build-$n", false, listOf(
            ReleaseAsset(UpdateReleases.apkName(n), 1000, UpdateReleases.downloadUrl("build-$n", UpdateReleases.apkName(n))),
            ReleaseAsset(UpdateReleases.checksumName(n), 100, UpdateReleases.downloadUrl("build-$n", UpdateReleases.checksumName(n))),
        ), body)
        assertEquals("neu in 9", UpdateReleases.newest(listOf(release(8, "neu in 8"), release(9, "neu in 9")))!!.notes)
        assertEquals("", UpdateReleases.newest(listOf(ReleaseInfo("build-3", false, release(3, "").assets)))!!.notes)
    }

    @Test fun cleanKeepsLinesAndDropsControlCharacters() {
        assertEquals("a\nb\tc\nd", ReleaseNotes.clean("  a\r\nb\tc\u0007\rd\u001b \n"))
    }

    @Test fun capKeepsShortTextAndCutsLongTextAtALineWithinTheByteLimit() {
        ReleaseNotes.cap("- kurz").let { assertEquals("- kurz", it.text); assertFalse(it.truncated) }
        val long = (1..2000).joinToString("\n") { "- Punkt $it mit Umlaut ä" }
        val capped = ReleaseNotes.cap(long)
        assertTrue(capped.truncated)
        assertTrue(capped.text.toByteArray(Charsets.UTF_8).size <= ReleaseNotes.MAX_STORED_BYTES)
        assertTrue(capped.text.endsWith("ä"), "cut at the end of a line")
        assertTrue(long.startsWith(capped.text))
        // exactly at the limit: nothing cut
        assertFalse(ReleaseNotes.cap("x".repeat(ReleaseNotes.MAX_STORED_BYTES)).truncated)
    }

    @Test fun capNeverSplitsACodePoint() {
        // one line of emojis (4 bytes each): no line break to cut at
        val capped = ReleaseNotes.cap("😀".repeat(10), maxBytes = 10)
        assertTrue(capped.truncated)
        assertEquals("😀😀", capped.text)
        assertEquals("ää", ReleaseNotes.cap("äää", maxBytes = 5).text)
    }

    @Test fun blocksRenderHeadingsListsAndParagraphsSimply() {
        val blocks = ReleaseNotes.blocks("""
            # Agent Keyboard – nächster Stand

            - Neu: **Gedächtnis** (anfangs aus). Einstellungen → `Tippen`
              und weiter in der nächsten Zeile.
            * Behoben: [Link](https://example.com) im Text
            1. Erstens

            Ein Absatz
            über zwei Zeilen.
            ## Nächster Build ##
        """.trimIndent())
        assertEquals(listOf(
            ReleaseNotes.Block.Heading("Agent Keyboard – nächster Stand", 1),
            ReleaseNotes.Block.Bullet("Neu: Gedächtnis (anfangs aus). Einstellungen → Tippen und weiter in der nächsten Zeile."),
            ReleaseNotes.Block.Bullet("Behoben: Link im Text"),
            ReleaseNotes.Block.Bullet("1. Erstens"),
            ReleaseNotes.Block.Paragraph("Ein Absatz über zwei Zeilen."),
            ReleaseNotes.Block.Heading("Nächster Build", 2),
        ), blocks)
        assertEquals(emptyList(), ReleaseNotes.blocks("  \n\n"))
    }
}
