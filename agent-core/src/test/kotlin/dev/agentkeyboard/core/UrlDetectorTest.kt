// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class UrlDetectorTest {
    @Test fun typedDomainsGetHttpsAndSchemesStay() {
        assertEquals("https://heise.de", UrlDetector.normalize("heise.de"))
        assertEquals("https://heise.de", UrlDetector.normalize("  heise.de "))
        assertEquals("https://www.heise.de/newsticker/", UrlDetector.normalize("www.heise.de/newsticker/"))
        assertEquals("https://de.wikipedia.org/wiki/Kiel?x=1#Lage", UrlDetector.normalize("de.wikipedia.org/wiki/Kiel?x=1#Lage"))
        assertEquals("http://example.com", UrlDetector.normalize("http://example.com"))
        assertEquals("https://example.com/a", UrlDetector.normalize("HTTPS://example.com/a"))
        assertEquals("https://HEISE.DE", UrlDetector.normalize("HEISE.DE"))
        assertEquals("https://Heise.de", UrlDetector.normalize("Heise.de"))
        assertEquals("https://müller-bäcker.de", UrlDetector.normalize("müller-bäcker.de"))
        assertEquals("https://heise.de:8443/x", UrlDetector.normalize("heise.de:8443/x"))
        assertEquals("https://claude.ai", UrlDetector.normalize("claude.ai"))
        assertEquals("https://github.io", UrlDetector.normalize("github.io"))
        assertEquals("https://shop.example.berlin", UrlDetector.normalize("shop.example.berlin"))
        assertEquals("https://heise.de.", UrlDetector.normalize("heise.de."))
    }

    @Test fun explicitSchemeAllowsAnyLetterTldIpAndLocalhost() {
        assertTrue(UrlDetector.isUrl("https://intranet.firma.internal/wiki"))
        assertTrue(UrlDetector.isUrl("http://192.168.0.1:8080/status"))
        assertTrue(UrlDetector.isUrl("http://localhost:3000"))
        assertFalse(UrlDetector.isUrl("192.168.0.1")) // without scheme an IP is no address
        assertFalse(UrlDetector.isUrl("http://999.1.1.1"))
        assertFalse(UrlDetector.isUrl("https://"))
        assertFalse(UrlDetector.isUrl("https://a"))
        assertFalse(UrlDetector.isUrl("https://example.123"))
        assertFalse(UrlDetector.isUrl("https://example.com:0"))
        assertFalse(UrlDetector.isUrl("https://example.com:99999"))
        assertFalse(UrlDetector.isUrl("https://example.com:"))
    }

    @Test fun otherSchemesAndUserInfoNeverCount() {
        for (text in listOf("javascript:alert(1)", "javascript://example.com/%0Aalert(1)", "file:///sdcard/foto.jpg",
            "ftp://example.com", "intent://scan/#Intent;scheme=zxing;end", "content://media/1", "mailto:a@b.de",
            "tel:+491234", "data:text/html,hi", "https://bank.de@evil.com", "bank.de@evil.com", "max@firma.de"))
            assertFalse(UrlDetector.isUrl(text), text)
    }

    @Test fun fileNamesVersionsAndAbbreviationsAreNoAddresses() {
        for (text in listOf("foto.jpg", "bericht.pdf", "Bericht.PDF", "notes.txt", "archiv.zip", "film.mov", "setup.exe",
            "app.apk", "lied.mp3", "a.b", "1.2.3", "v1.2", "2.0beta", "3.14", "z.B.", "u.a.", "d.h.", "e.g.",
            "README.md", "main.py", "build.sh", "lib.rs", "fertig.Dann", "Satz.Neuer", "hallo.welt", "heise",
            "heise.", ".de", "heise..de", "-heise.de", "heise-.de", "12.de", "heise .de", "hei se.de",
            "https://heise.de/a b", "<heise.de>", "\"heise.de\"", ""))
            assertFalse(UrlDetector.isUrl(text), text)
        // with www. or a path, an address may end in a code that is often a file extension
        assertTrue(UrlDetector.isUrl("www.example.md"))
        assertTrue(UrlDetector.isUrl("example.sh/start"))
        assertTrue(UrlDetector.isUrl("www.example.welt"))
    }

    @Test fun findsAddressesInTextWithoutPunctuation() {
        val text = "Schau auf heise.de, dann (https://example.com/a?b=1). Mail an max@firma.de, Datei foto.jpg, " +
            "Version 1.2.3, Wiki https://de.wikipedia.org/wiki/Kiel_(Stadt). Link:https://t.co/xyz und „www.spiegel.de“ " +
            "und nochmal heise.de! Ende.Neuer Satz."
        assertEquals(listOf("https://heise.de", "https://example.com/a?b=1", "https://de.wikipedia.org/wiki/Kiel_(Stadt)",
            "https://t.co/xyz", "https://www.spiegel.de"), UrlDetector.find(text))
        assertEquals(listOf("https://heise.de"), UrlDetector.find(text, limit = 1))
        assertTrue(UrlDetector.find("").isEmpty())
        assertTrue(UrlDetector.find("Nur Text ohne Adresse. Bitte z.B. morgen.").isEmpty())
    }

    @Test fun boundsInputLength() {
        val long = "https://example.com/" + "a".repeat(UrlDetector.MAX_LENGTH)
        assertFalse(UrlDetector.isUrl(long))
        val far = " ".repeat(UrlDetector.MAX_SCAN) + "heise.de"
        assertTrue(UrlDetector.find(far).isEmpty()) // only the first MAX_SCAN characters are looked at
    }
}
