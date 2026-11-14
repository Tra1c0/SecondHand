// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryRulesTest {
    // ---- own words ----

    @Test fun aWordBecomesOwnAfterThreeUses() {
        val book = OwnWordBook()
        assertFalse(book.noteUse("krass", 0))
        assertFalse(book.noteUse("Krass", 1))
        assertFalse(book.isOwn("krass"))
        assertTrue(book.noteUse("krass", 2))
        assertTrue(book.isOwn("KRASS"))
        assertEquals(listOf("krass"), book.words)
        assertFalse(book.noteUse("krass", 3), "already own")
        assertEquals(listOf("krass"), book.completions("kra"))
        assertEquals(emptyList(), book.completions("k"), "two characters at least")
        assertEquals(emptyList(), book.completions("krass"), "only longer words")
        assertTrue(book.remove("krass"))
        assertFalse(book.isOwn("krass"))
    }

    @Test fun usesFadeAfterNinetyDaysAndNonWordsNeverCount() {
        val book = OwnWordBook()
        book.noteUse("nö", 0); book.noteUse("nö", 1)
        assertFalse(book.noteUse("nö", 200), "the old uses are forgotten")
        assertFalse(OwnWordBook.isCandidate("a"))
        assertFalse(OwnWordBook.isCandidate("x2"))
        assertFalse(OwnWordBook.isCandidate("a@b"))
        assertTrue(OwnWordBook.isCandidate("geht's"))
        repeat(5) { assertFalse(book.noteUse("123", it.toLong())) }
        val again = OwnWordBook.parse(book.encode(), 200)
        assertEquals(book.candidates, again.candidates)
        assertEquals(0, OwnWordBook.parse("v1\no;1\nu;5;0;abc\nu;1;x;abc", 0).let { it.words.size + it.candidates.size })
    }

    // ---- shortcuts ----

    @Test fun plausibleExpansions() {
        assertTrue(ShortcutRules.plausible("vlt", "vielleicht"))
        assertTrue(ShortcutRules.plausible("mfg", "mit freundlichen Grüßen"))
        assertTrue(ShortcutRules.plausible("lg", "Liebe Grüße"))
        assertFalse(ShortcutRules.plausible("bg", "bitte"), "no g in bitte")
        assertFalse(ShortcutRules.plausible("nciht", "nicht"), "a typo fix")
        assertFalse(ShortcutRules.plausible("vlt", "viel"))
        assertFalse(ShortcutRules.plausible("ds", "das"), "too short a gain")
        assertFalse(ShortcutRules.plausible("lg", "liebe grüße an alle hier und dort"))
        assertFalse(ShortcutRules.plausible("v1", "version eins"))
    }

    @Test fun trackerSeesAnInPlaceExpansion() {
        val tracker = ReplacementTracker()
        assertNull(tracker.onCommit("Das", 3, "Das"))
        assertNull(tracker.onCommit("ist", 7, "Das ist"))
        assertNull(tracker.onCommit("vlt", 11, "Das ist vlt"))
        // backspace over "vlt", then "vielleicht" in its place
        assertEquals("vlt" to "vielleicht", tracker.onCommit("vielleicht", 18, "Das ist vielleicht"))
        // several words: the initials
        tracker.reset()
        assertNull(tracker.onCommit("mfg", 3, "mfg"))
        assertNull(tracker.onCommit("mit", 3, "mit"))
        assertNull(tracker.onCommit("freundlichen", 16, "mit freundlichen"))
        assertEquals("mfg" to "mit freundlichen Grüßen", tracker.onCommit("Grüßen", 23, "mit freundlichen Grüßen"))
        // a typo fixed in place is no expansion
        tracker.reset()
        tracker.onCommit("nciht", 5, "nciht")
        assertNull(tracker.onCommit("nicht", 5, "nicht"))
        // words typed one after another are no replacement
        tracker.reset()
        tracker.onCommit("vlt", 3, "vlt")
        assertNull(tracker.onCommit("vielleicht", 14, "vlt vielleicht"))
    }

    @Test fun offerOnceAfterTwoExpansions() {
        val book = ShortcutBook()
        assertFalse(book.note("vlt", "vielleicht"))
        assertNull(book.due())
        assertTrue(book.note("vlt", "vielleicht"))
        val offer = assertNotNull(book.due())
        assertEquals("vlt", offer.short)
        book.mark("vlt", "vielleicht", ShortcutBook.State.SHOWN)
        assertNull(book.due(), "offered once")
        assertFalse(book.note("vlt", "vielleicht"), "never due again")
        assertEquals(ShortcutBook.State.SHOWN, book.offers().single().state)
        val again = ShortcutBook.parse(book.encode())
        assertEquals(3, again.offers().single().count)
        assertFalse(book.note("nciht", "nicht"))
        assertEquals(0, ShortcutBook.parse("v1\n2;READY;nciht;nicht\nx;READY;vlt;vielleicht").offers().size)
    }

    // ---- snippets ----

    private val snippets = listOf(
        SnippetMatcher.Document("adresse", "Adresse", "Musterstraße 12, 10115 Berlin"),
        SnippetMatcher.Document("iban", "Bankverbindung", "IBAN DE89 3704 0044 0532 0130 00, Max Mustermann"),
        SnippetMatcher.Document("urlaub", "Abwesend", "Ich bin bis Montag im Urlaub und antworte danach auf deine Nachricht."),
        SnippetMatcher.Document("termin", "Terminbestätigung", "Hiermit bestätige ich den Termin am {datum} um {zeit}."),
        SnippetMatcher.Document("anfahrt", "Anfahrt", "Mit der U2 bis Alexanderplatz, dann fünf Minuten zu Fuß."),
        SnippetMatcher.Document("wlan", "WLAN Passwort Gäste", "Netz: Gast, Kennwort steht am Kühlschrank."),
        SnippetMatcher.Document("danke", "Dank Bewerbung", "Vielen Dank für Ihre Bewerbung, wir melden uns in zwei Wochen."),
        SnippetMatcher.Document("rechnung", "Rechnung", "Anbei die Rechnung für den letzten Monat, zahlbar in 14 Tagen."),
    )

    @Test fun vectorsFromWordsWordPartsAndTrigrams() {
        val f = SnippetMatcher.features("Grüße")
        assertTrue("w:grusse" in f && "p:gruss" in f && "t:^gr" in f && "t:se$" in f)
        assertTrue(SnippetMatcher.features("und der die").isEmpty(), "stop words only")
        val matcher = SnippetMatcher(snippets)
        // a word part meets another form of the word
        assertEquals("adresse", matcher.scores("meine Adressen").first().first)
        // trigrams alone never match
        assertEquals(0.0, matcher.scores("Adlerhorst").first().second)
    }

    /** Typed sentence ends and the snippet the owner wants (null: none fits). */
    private val queries = listOf(
        "meine adresse ist" to "adresse", "schick mir deine adresse" to "adresse", "hier die adressen" to "adresse",
        "meine bankverbindung lautet" to "iban", "die iban für die überweisung" to "iban", "überweis es auf meine iban" to "iban",
        "ich bin im urlaub bis" to "urlaub", "bin gerade im urlaub" to "urlaub", "abwesend bis montag" to "urlaub",
        "ich bestätige den termin" to "termin", "terminbestätigung für morgen" to "termin", "der termin passt" to "termin",
        "wie ist die anfahrt" to "anfahrt", "anfahrt mit der bahn" to "anfahrt", "zum alexanderplatz fahren" to "anfahrt",
        "das wlan passwort" to "wlan", "kennwort für das wlan" to "wlan", "gäste wlan bitte" to "wlan",
        "danke für die bewerbung" to "danke", "ihre bewerbung ist da" to "danke", "die rechnung für" to "rechnung",
        "anbei die rechnung" to "rechnung", "rechnung vom letzten monat" to "rechnung",
        // nothing fits
        "wie geht es dir" to null, "wollen wir heute essen gehen" to null, "ich komme später nach hause" to null,
        "hast du den film gesehen" to null, "das wetter ist schön" to null, "kannst du mich abholen" to null,
        "der hund schläft" to null, "morgen gehen wir schwimmen" to null, "ruf mich bitte an" to null,
        "was machst du am wochenende" to null, "die katze will raus" to null, "schöne grüße an alle" to null,
        // nothing fits, but words are shared by chance
        "ich habe montag frei" to null, "nur fünf minuten noch" to null, "der letzte monat war lang" to null,
        "vielen dank dir" to null, "wir melden uns" to null, "in zwei wochen" to null, "steht im kühlschrank" to null,
    )

    private fun measure(threshold: Double): Triple<Int, Int, Int> {
        val matcher = SnippetMatcher(snippets)
        var offers = 0; var hits = 0; var wanted = 0
        for ((query, expected) in queries) {
            if (expected != null) wanted++
            val ranked = matcher.scores(query)
            val first = ranked.first()
            val second = ranked.getOrNull(1)?.second ?: 0.0
            val offered = first.second >= threshold && first.second - second >= SnippetMatcher.MARGIN
            if (offered) { offers++; if (first.first == expected) hits++ else println("  at $threshold wrong: '$query' -> ${first.first} ${"%.2f".format(first.second)} (2nd ${"%.2f".format(second)})") }
        }
        return Triple(offers, hits, wanted)
    }

    @Test fun measuredSnippetPrecision() {
        for (t in listOf(0.1, 0.2, 0.3, 0.35, 0.4, 0.5)) measure(t).let { (o, h, w) -> println("snippets at $t: precision $h/$o, coverage $h/$w") }
        val (offers, hits, wanted) = measure(SnippetMatcher.THRESHOLD)
        assertTrue(hits.toDouble() / offers >= 0.9, "precision $hits/$offers")
        assertTrue(hits.toDouble() / wanted >= 0.7, "coverage $hits/$wanted")
        val (looseOffers, looseHits, _) = measure(0.1)
        assertTrue(looseHits.toDouble() / looseOffers <= hits.toDouble() / offers)
        // best() needs two words of three letters
        assertNull(SnippetMatcher(snippets).best("adresse"))
        assertEquals("adresse", SnippetMatcher(snippets).best("meine adresse ist")?.id)
        assertNull(SnippetMatcher(emptyList()).best("meine adresse ist"))
    }
}
