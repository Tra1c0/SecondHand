// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clipboard guard (stage 44). Example keys are put together from pieces so that the source file
 * itself contains no key-shaped string; none of them is a real key.
 */
class ClipGuardTest {
    private fun kind(text: String, marked: Boolean = false) = ClipGuard.classify(text, marked).kind
    private fun code(text: String) = ClipGuard.classify(text).also { assertEquals(ClipKind.OTP, it.kind, text) }.code
    private fun reason(text: String) = ClipGuard.classify(text).also { assertEquals(ClipKind.SECRET, it.kind, text) }.reason

    private val openAi = "sk" + "-proj-" + "Ab3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY3z" + "7f3a"
    private val anthropic = "sk" + "-ant-api03-" + "x9Y8w7V6u5T4s3R2q1P0o9N8m7L6k5J4"
    private val github = "gh" + "p_" + "aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wX2"
    private val githubPat = "github" + "_pat_" + "11ABCDEFG0123456789_abcdefghijklmnop"
    private val aws = "AK" + "IA" + "IOSFODNN7EXAMPLE"
    private val slack = "xo" + "xb-" + "123456789012-abcdefABCDEF"
    private val google = "AI" + "za" + "SyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q"
    private val stripe = "sk" + "_live_" + "4eC39HqLyjWDarjtT1zdp7dc"
    private val jwt = "ey" + "JhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." + "ey" + "JzdWIiOiIxMjM0NTY3ODkwIn0." +
        "dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
    private val privateKey = "-----BEGIN " + "OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXktdjEAAAAA\n-----END OPENSSH PRIVATE KEY-----"

    // --- secrets ------------------------------------------------------------------------------

    @Test fun knownKeyFormatsAreSecrets() {
        for (key in listOf(openAi, anthropic, github, githubPat, aws, slack, google, stripe)) {
            assertEquals(SecretReason.API_KEY, reason(key), key.take(6))
            // also inside a line or a code snippet, and with spaces around
            assertEquals(ClipKind.SECRET, kind("OPENAI_API_KEY=$key"), key.take(6))
            assertEquals(ClipKind.SECRET, kind("  const key = \"$key\";\n"), key.take(6))
        }
        assertEquals(SecretReason.JWT, reason(jwt))
        assertEquals(ClipKind.SECRET, kind("Authorization: Bearer $jwt"))
        assertEquals(SecretReason.PRIVATE_KEY, reason(privateKey))
    }

    @Test fun markedClipsAreSecretsWhateverTheyContain() {
        assertEquals(SecretReason.MARKED, ClipGuard.classify("Hallo Welt", markedSensitive = true).reason)
        assertEquals(ClipKind.SECRET, kind("123456", marked = true))
        assertEquals(ClipKind.SECRET, kind("https://example.com", marked = true))
        // an empty clip stays nothing
        assertEquals(ClipKind.TEXT, kind("  ", marked = true))
    }

    @Test fun randomTokensAndPasswordsAreSecrets() {
        assertEquals(SecretReason.TOKEN, reason("Zq8Lm2Xv9Rt4Pk7Wn3Bs6Hd1")) // 24 random
        assertEquals(SecretReason.TOKEN, reason("wJalrXUtnFEMI/K7MDENG/bPxRfiCY3xA9pL2kQ8")) // AWS secret style
        assertEquals(SecretReason.TOKEN, reason("q3Zx9vL2mN8pR4tY7wK1sD6fG0hJ5aB2cE9uI3oP="))
        assertEquals(SecretReason.PASSWORD, reason("Tr0ub4dor&3"))
        assertEquals(SecretReason.PASSWORD, reason("P@ssw0rd!"))
        assertEquals(SecretReason.PASSWORD, reason("Sommer2024!"))
        assertEquals(SecretReason.PASSWORD, reason("x7#Kq9!mZ"))
    }

    // --- normal words, links, hashes, code ---------------------------------------------------

    @Test fun germanWordsAndSentencesStayNormal() {
        for (text in listOf(
            "Hallo", "Donaudampfschifffahrtsgesellschaftskapitän", "Straßenbahnhaltestelle", "Grüße",
            "Bis morgen um 8 Uhr!", "Kostenstelle-A12", "COVID-19-Test", "Bericht_2024_Q3_final",
            "Rechnungsnummer2024Dezember", "MyVeryLongVariableName42", "ThisIsAVeryLongCamelCaseIdentifier2024",
            "Ärztekammer-Nordrhein", "e-Mail", "z.B.", "3,50€", "12.05.2025", "14:30", "v1.2.3", "C++17",
        )) assertEquals(ClipKind.TEXT, kind(text), text)
    }

    @Test fun linksStayLinks() {
        for (url in listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://example.com/path?token=abc",
            "www.tagesschau.de", "https://maps.app.goo.gl/AbC123xYz9",
            "https://github.com/HeliBorg/HeliBoard/commit/0a1b2c3d4e5f60718293a4b5c6d7e8f901234567",
        )) assertEquals(ClipKind.LINK, kind(url), url)
        // a link in a sentence is normal text
        assertEquals(ClipKind.TEXT, kind("guck mal https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test fun hashesUuidsAndPathsAreNoSecrets() {
        for (text in listOf(
            "0a1b2c3d4e5f60718293a4b5c6d7e8f901234567", // git commit
            "da311ee", // short git hash
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", // sha256
            "D41D8CD98F00B204E9800998ECF8427E", // md5 upper case
            "123e4567-e89b-12d3-a456-426614174000", // UUID
            "#FF8800", "src/main/kotlin/dev/agentkeyboard/core/clip/ClipGuard.kt", "/home/user/Download/Foto_2024.jpg",
            "C:\\Users\\Max\\Bericht2024.docx", "com.example.myapp.v2", "@Max_Muster1", "#Sommer2024",
            "Foto_2024-05-12.JPG", "max.mustermann@example.com",
        )) assertTrue(kind(text) != ClipKind.SECRET, text)
    }

    @Test fun codeSnippetsWithoutKeysStayNormal() {
        assertEquals(ClipKind.TEXT, kind("val x = listOf(1, 2, 3).map { it * 2 }"))
        assertEquals(ClipKind.TEXT, kind("git commit -m \"Fix crash\" && git push origin main"))
        assertEquals(ClipKind.TEXT, kind("for (i in 0 until 10) println(i)"))
    }

    @Test fun base64BlobsDecided() {
        // short random base64 (a key or token) is a secret
        assertEquals(ClipKind.SECRET, kind("dGhpc0lzQVNlY3JldEtleTEyMzQ1Ng=="))
        // a data URI or a long blob (pictures, certificates) is data, not a secret
        assertEquals(ClipKind.TEXT, kind("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"))
        val blob = "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlz".repeat(4)
        assertEquals(ClipKind.TEXT, kind(blob))
        // base64 of plain words without digits has long lower-case runs only by chance; text with spaces stays normal
        assertEquals(ClipKind.TEXT, kind("TWFu IGlz IGRp c3Rp bmd1"))
    }

    // --- one-time codes ----------------------------------------------------------------------

    @Test fun bareCodes() {
        assertEquals("123456", code("123456"))
        assertEquals("4821", code("4821"))
        assertEquals("12345678", code("12345678"))
        assertEquals("123456", code("123 456"))
        assertEquals("123456", code("123-456"))
        assertEquals("482913", code(" 482913\n"))
        // a year alone is no code, nor are 3 or 9 digits
        assertEquals(ClipKind.TEXT, kind("2024"))
        assertEquals(ClipKind.TEXT, kind("123"))
        assertTrue(kind("123456789") != ClipKind.OTP)
    }

    @Test fun codesInSentencesGerman() {
        assertEquals("123456", code("Dein Code lautet 123456"))
        assertEquals("482913", code("Ihr Bestätigungscode: 482913. Gültig für 10 Minuten."))
        assertEquals("123456", code("G-123456 ist dein Google-Bestätigungscode."))
        assertEquals("7391", code("Ihre mTAN für die Überweisung von 250,00 € lautet 7391"))
        assertEquals("552190", code("Verwende 552190 als Einmalpasswort für dein Konto."))
        assertEquals("883421", code("Dein Code für 2025: 883421"))
    }

    @Test fun codesInSentencesEnglishAndSpanish() {
        assertEquals("482913", code("Your verification code is 482913"))
        assertEquals("1234", code("Your PIN: 1234. Do not share it."))
        assertEquals("246810", code("246810 is your Instagram code. Don't share it."))
        assertEquals("5678", code("Tu código es 5678"))
        assertEquals("903172", code("Tu código de verificación de WhatsApp: 903-172"))
        assertEquals("481516", code("Usa la clave 481516 para entrar"))
    }

    @Test fun sentencesWithoutClearCodeStayNormal() {
        assertEquals(ClipKind.TEXT, kind("Wir treffen uns am 12.05.2025 um 14:30"))
        assertEquals(ClipKind.TEXT, kind("Der Code ist im Repository unter src/"))
        assertEquals(ClipKind.TEXT, kind("Ruf mich an: +49 151 23456789, Code folgt"))
        assertEquals(ClipKind.TEXT, kind("Der Preis beträgt 1299,00 € inkl. Versand"))
        assertEquals(ClipKind.TEXT, kind("Ich habe 1234 Fotos und 5678 Videos"))
        assertEquals(ClipKind.TEXT, kind("Die Spinne lief 1234 Meter weit"))
        val long = "Code 123456 " + "bla ".repeat(80)
        assertEquals(ClipKind.TEXT, kind(long))
    }

    @Test fun codeWordsCountOnlyAsWholeWords() {
        // review of stage 44 (B2): substrings of ordinary words announced a code before
        for (text in listOf(
            "Kannst du noch einmal die Rechnung 2023114 prüfen?",
            "Bestätigung Ihrer Bestellung 12345678",
            "Barcode 40071234",
            "Der QR-Code 12345678 hängt an der Tür",
            "Promo-Code 2024 gilt bis Freitag",
            "Verifizierung der Kundennummer 5551234 läuft",
            "Spinnenclave 4821", "Encode 123456 as base64",
        )) assertEquals(ClipKind.TEXT, kind(text), text)
        // compounds that do announce a code
        assertEquals("482913", code("Ihr Sicherheitscode 482913"))
        assertEquals("482913", code("Dein SMS-Code: 482913"))
        assertEquals("903172", code("Dein WhatsApp-Code: 903-172"))
        assertEquals("4711", code("Einmal-PIN 4711, gültig 5 Minuten"))
        assertEquals("552190", code("Your one-time password is 552190"))
    }

    @Test fun aCodeInAMessageStaysInTheHistoryABareCodeDoesNot() {
        val inText = ClipGuard.classify("Dein Code lautet 123456")
        assertTrue(inText.inText)
        assertFalse(inText.keepsOutOfHistory)
        assertFalse(ClipGuard.classify("123456").inText)
        assertTrue(ClipGuard.classify("123456").keepsOutOfHistory)
        assertTrue(ClipGuard.classify("P@ssw0rd!").keepsOutOfHistory)
        assertFalse(ClipGuard.classify("Hallo").keepsOutOfHistory)
        assertFalse(inText.toString().contains("123456"))
    }

    @Test fun aPasswordWordWithItsValueIsASecret() {
        // review of stage 44 (B6): masked secret instead of a code in plain text
        for (text in listOf(
            "Das WLAN-Passwort ist 84736291", "Das WLAN-Passwort lautet Sommer2024", "Passwort: Sommerwiese",
            "password=hunter2x", "Mi contraseña es gato_77", "Kennwort lautet Xk9#mP2q.",
        )) assertEquals(SecretReason.PASSWORD, reason(text), text)
        assertEquals(ClipGuard.HIDDEN, ClipGuard.mask("Das WLAN-Passwort ist 84736291"))
        for (text in listOf(
            "Das Passwort ist falsch", "Ich habe mein Passwort vergessen", "Das Passwort ist 12 Zeichen lang",
            "Passwort ist mindestens acht Zeichen lang",
        )) assertEquals(ClipKind.TEXT, kind(text), text)
    }

    @Test fun aPasswordWordNeedsAValueThatLooksLikeOne() {
        // second review of stage 44 (N8): a note about a password is no secret
        for (text in listOf(
            "Passwort: bitte beim Admin erfragen", "Passwort: Erfragen beim Admin", "Passwort: geheim",
            "Kennwort = folgt", "Das Passwort ist abc", "Password: ask IT",
        )) assertEquals(ClipKind.TEXT, kind(text), text)
        for (text in listOf("Passwort: Sommer2024 bitte ändern", "WLAN-Passwort: Sommerwiese, Raum 3", "Password: x7Kq9mZ2"))
            assertEquals(SecretReason.PASSWORD, reason(text), text)
    }

    @Test fun aLettersOnlyPasswordValueMustEndItsLine() {
        // third review of stage 44: not even one more word may follow a letters-only value
        for (text in listOf("Passwort: Erfragen Sie", "Kennwort = Frag Peter", "Password: Ask him"))
            assertEquals(ClipKind.TEXT, kind(text), text)
        for (text in listOf("Passwort: Sommerwiese", "Passwort: Sommerwiese\nRaum 3", "Passwort: Sommerwiese; Raum 3", "Password: x7Kq9mZ2 now"))
            assertEquals(SecretReason.PASSWORD, reason(text), text)
    }

    @Test fun moreKeyFormatsAndIdentifiersThatAreNone() {
        // review of stage 44 (B9)
        val telegram = "123456789" + ":" + "AAHx" + "dqTcvCH1vGWJxfSeofSAs0K5PALDsaw"
        assertEquals(SecretReason.API_KEY, reason(telegram))
        assertEquals(ClipKind.SECRET, kind("TOKEN=$telegram"))
        val bearer = "Authorization: Bearer " + "abc123DEF456ghi789JKL0mn"
        assertEquals(SecretReason.API_KEY, reason(bearer))
        assertEquals(ClipKind.TEXT, kind("Bearer tokens are explained in the docs"))
        for (text in listOf("B2B-Shop", "camelCase2_x", "Win11-Pro", "iPhone15-Pro", "Q3_2024-report"))
            assertTrue(kind(text) != ClipKind.SECRET, text)
        // the password positives stay
        assertEquals(SecretReason.PASSWORD, reason("Tr0ub4dor&3"))
        assertEquals(SecretReason.PASSWORD, reason("x7#Kq9!mZ"))
    }

    // --- personal data -----------------------------------------------------------------------

    @Test fun ibans() {
        assertEquals(ClipKind.IBAN, kind("DE89370400440532013000"))
        assertEquals(ClipKind.IBAN, kind("DE89 3704 0044 0532 0130 00"))
        assertEquals(ClipKind.IBAN, kind("AT611904300234573201"))
        assertEquals(ClipKind.IBAN, kind("AT61 1904 3002 3457 3201"))
        assertEquals(ClipKind.IBAN, kind("ES9121000418450200051332"))
        assertEquals(ClipKind.IBAN, kind("ES91 2100 0418 4502 0005 1332"))
        assertEquals(ClipKind.IBAN, kind("IBAN: DE89 3704 0044 0532 0130 00"))
        // wrong length is no IBAN (and no secret either)
        assertTrue(kind("DE8937040044053201300") != ClipKind.IBAN)
        assertTrue(kind("DE8937040044053201300") != ClipKind.SECRET)
    }

    @Test fun phoneNumbers() {
        assertEquals(ClipKind.PHONE, kind("+49 151 23456789"))
        assertEquals(ClipKind.PHONE, kind("0151 23456789"))
        assertEquals(ClipKind.PHONE, kind("+43 1 5354800"))
        assertEquals(ClipKind.PHONE, kind("+34 912 345 678"))
        assertEquals(ClipKind.PHONE, kind("030/12345678"))
        assertEquals(ClipKind.PHONE, kind("(030) 1234567"))
        // dates and short numbers are no phone numbers
        assertTrue(kind("2024-05-12") != ClipKind.PHONE)
        assertTrue(kind("12.05.2025") != ClipKind.PHONE)
    }

    @Test fun emails() {
        assertEquals(ClipKind.EMAIL, kind("max.mustermann@example.com"))
        assertEquals(ClipKind.EMAIL, kind("anna+news@web.de"))
        assertEquals(ClipKind.EMAIL, kind("mailto:info@firma.co.at"))
        assertEquals(ClipKind.TEXT, kind("schreib an max@example.com"))
        assertTrue(kind("max@") != ClipKind.EMAIL)
    }

    @Test fun addresses() {
        assertEquals(ClipKind.ADDRESS, kind("Musterstraße 12, 10115 Berlin"))
        assertEquals(ClipKind.ADDRESS, kind("Max Mustermann\nHauptstr. 5a\n80331 München"))
        assertEquals(ClipKind.ADDRESS, kind("Am Ring 7\n1010 Wien"))
        assertEquals(ClipKind.ADDRESS, kind("Stephansplatz 1, 1010 Wien"))
        assertEquals(ClipKind.ADDRESS, kind("Calle Mayor 5, 28013 Madrid"))
        assertEquals(ClipKind.ADDRESS, kind("Avenida de la Constitución, 12, 41001 Sevilla"))
        assertEquals(ClipKind.ADDRESS, kind("1600 Amphitheatre Parkway Drive, Mountain View, CA 94043"))
        assertEquals(ClipKind.TEXT, kind("Wir sehen uns in Berlin"))
        assertEquals(ClipKind.TEXT, kind("10115 Berlin")) // a city alone is no address
        assertEquals(ClipKind.TEXT, kind("Ich wohne seit 2019 in der Nähe vom Ring"))
    }

    // --- masking -----------------------------------------------------------------------------

    @Test fun maskShowsPrefixAndLastFourOnly() {
        assertEquals("sk-proj-…7f3a", ClipGuard.mask(openAi))
        assertEquals("ghp_…wX2".length + 1, ClipGuard.mask(github).length)
        assertTrue(ClipGuard.mask(github).startsWith("ghp_…"))
        assertEquals("AKIA…MPLE", ClipGuard.mask(aws))
        assertEquals("eyJ…sR8U", ClipGuard.mask(jwt))
        assertEquals("sk-proj-…7f3a", ClipGuard.mask("OPENAI_API_KEY=$openAi"))
        assertEquals("••••…Hd1".length + 1, ClipGuard.mask("Zq8Lm2Xv9Rt4Pk7Wn3Bs6Hd1").length)
        // short secrets, sentences and key blocks show no character
        assertEquals(ClipGuard.HIDDEN, ClipGuard.mask("P@ssw0rd!"))
        assertEquals(ClipGuard.HIDDEN, ClipGuard.mask("Hallo Welt"))
        assertEquals(ClipGuard.HIDDEN, ClipGuard.mask(privateKey))
    }

    @Test fun maskNeverRevealsMoreThanFourCharactersOfTheSecretPart() {
        for (secret in listOf(openAi, anthropic, github, githubPat, aws, slack, google, stripe, jwt,
            "Zq8Lm2Xv9Rt4Pk7Wn3Bs6Hd1", "Tr0ub4dor&3", "wJalrXUtnFEMI/K7MDENG/bPxRfiCY3xA9pL2kQ8")) {
            val masked = ClipGuard.mask(secret)
            val prefixLength = masked.indexOf('…').coerceAtLeast(0)
            val body = secret.substring(prefixLength.coerceAtMost(secret.length))
            for (i in 0..body.length - 5) assertFalse(masked.contains(body.substring(i, i + 5)), "${secret.take(4)} at $i")
        }
    }

    @Test fun toStringNeverShowsText() {
        val result = ClipGuard.classify("Dein Code lautet 123456")
        assertFalse(result.toString().contains("123456"))
        assertNull(ClipGuard.classify("Hallo").code)
    }

    @Test fun categories() {
        assertEquals(ClipCategory.SECRET, ClipGuard.classify(openAi).category)
        assertEquals(ClipCategory.OTP, ClipGuard.classify("123456").category)
        assertEquals(ClipCategory.PERSONAL, ClipGuard.classify("DE89370400440532013000").category)
        assertEquals(ClipCategory.NORMAL, ClipGuard.classify("https://example.com").category)
        assertEquals(ClipCategory.NORMAL, ClipGuard.classify("Hallo").category)
    }

    @Test fun veryLongClipsOnlyLookForKeys() {
        val long = "a".repeat(ClipGuard.MAX_LENGTH + 1)
        assertEquals(ClipKind.TEXT, kind(long + " 123456 Code"))
        assertEquals(ClipKind.SECRET, kind(long + "\n" + openAi))
        val huge = "a".repeat(ClipGuard.MAX_SCAN + 1)
        assertEquals(ClipKind.TEXT, kind(huge + "\n" + openAi))
        assertEquals(ClipKind.SECRET, kind(huge + "\n" + privateKey))
    }
}
