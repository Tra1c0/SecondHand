// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.suggest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextSuggestTest {
    private val minute = 60_000L
    private val none = ContextWeights.empty(100)

    private fun cue(text: String, kind: SuggestKind) = ContextCues.find(text).firstOrNull { it.kind == kind }

    private fun score(text: String, candidate: Candidate, app: AppCategory = AppCategory.OTHER, field: FieldClass = FieldClass.TEXT,
                      weights: ContextWeights = none): Double? {
        val match = cue(text, candidate.kind) ?: return null
        return ContextScorer.score(match, candidate, app, field, weights)
    }

    private fun offered(text: String, candidate: Candidate, app: AppCategory = AppCategory.OTHER, field: FieldClass = FieldClass.TEXT,
                        weights: ContextWeights = none) = (score(text, candidate, app, field, weights) ?: -1.0) >= ContextScorer.THRESHOLD

    // --- cue words ---

    @Test fun theOwnersExampleOffersTheLinkJustCopied() {
        val link = Candidate(SuggestKind.LINK, own = false, ageMillis = 2 * minute)
        assertTrue(offered("guck mal, hier dieses Video ", link))
        assertTrue(offered("guck mal, hier dieses Video ", link, AppCategory.MESSENGER))
        assertEquals("media", cue("guck mal, hier dieses Video ", SuggestKind.LINK)!!.cueId)
        // en and es
        assertTrue(offered("check this video ", link))
        assertTrue(offered("mira este vídeo ", link))
        assertTrue(offered("Schau mal, der Link ", link))
    }

    @Test fun cuesInEachLanguageAnnounceTheirKind() {
        for ((text, kind) in listOf(
            "meine Nummer ist " to SuggestKind.PHONE, "my number is " to SuggestKind.PHONE, "mi número es " to SuggestKind.PHONE,
            "Tel: " to SuggestKind.PHONE,
            "IBAN: " to SuggestKind.IBAN, "meine IBAN ist " to SuggestKind.IBAN, "mi IBAN es " to SuggestKind.IBAN,
            "Adresse: " to SuggestKind.ADDRESS, "my address is " to SuggestKind.ADDRESS, "mi dirección es " to SuggestKind.ADDRESS,
            "Code: " to SuggestKind.OTP, "der Code ist " to SuggestKind.OTP, "el código es " to SuggestKind.OTP,
            "meine Mail ist " to SuggestKind.EMAIL, "E-Mail: " to SuggestKind.EMAIL, "mi correo es " to SuggestKind.EMAIL,
            "Mein Name ist " to SuggestKind.NAME, "me llamo " to SuggestKind.NAME,
        )) assertTrue(cue(text, kind) != null, "\"$text\" → $kind")
    }

    @Test fun noCueInTheMiddleOfAWordOrWithoutTheColonOfASingleWord() {
        // the word being typed keeps its suggestions: nothing before the space
        assertNull(cue("guck mal, hier dieses Video", SuggestKind.LINK))
        // single data words need their colon
        assertNull(cue("ich schreibe dir eine Mail ", SuggestKind.EMAIL))
        assertNull(cue("der Code ", SuggestKind.OTP))
        assertNull(cue("die IBAN ", SuggestKind.IBAN))
        assertTrue(cue("die IBAN: ", SuggestKind.IBAN) != null)
        // a cue inside another word
        assertNull(cue("hierbei ", SuggestKind.LINK))
        assertNull(cue("Videothek ", SuggestKind.LINK))
        // e-mail is not "mail"
        assertEquals("email", cue("E-Mail: ", SuggestKind.EMAIL)!!.cueId)
    }

    @Test fun dataCuesStandDirectlyBeforeTheCursor() {
        assertTrue(cue("meine Nummer ist ", SuggestKind.PHONE) != null)
        assertNull(cue("meine Nummer ist schon alt, sorry ", SuggestKind.PHONE))
        // link cues may stand earlier in the sentence, closeness lowers them
        val near = cue("hier dieses Video ", SuggestKind.LINK)!!.strength
        val far = cue("Video, das ich gestern beim Frühstück mit allen angeschaut habe ", SuggestKind.LINK)!!.strength
        assertTrue(near > far, "$near > $far")
        assertNull(cue("Video und dann haben wir noch sehr lange über alles Mögliche geredet und gelacht und so ", SuggestKind.LINK))
    }

    @Test fun whatIsAlreadyTypedAfterTheCueIsNotOfferedAgain() {
        assertNull(cue("guck mal https://example.com/x ", SuggestKind.LINK))
        assertNull(cue("meine Nummer ist 0151 ", SuggestKind.PHONE))
        assertNull(cue("meine Mail ist max@ ", SuggestKind.EMAIL))
    }

    @Test fun onlyTheLastLineCounts() {
        assertNull(cue("guck mal\nAlles gut bei dir? ", SuggestKind.LINK))
        assertTrue(cue("Hallo\nguck mal ", SuggestKind.LINK) != null)
    }

    @Test fun strengthIsBoundedAndCombinesDistinctCues() {
        val many = cue("guck mal, schau mal, hier ist der Link zum Video ", SuggestKind.LINK)!!
        assertTrue(many.strength <= 1.0)
        val one = cue("hier ", SuggestKind.LINK)!!
        assertTrue(many.strength > one.strength)
    }

    // --- freshness, app, threshold ---

    @Test fun freshnessFallsWithAgeAndEndsAfterTenMinutes() {
        fun f(age: Long) = ContextScorer.freshness(Candidate(SuggestKind.LINK, false, age))
        assertTrue(f(0) > f(2 * minute) && f(2 * minute) > f(9 * minute))
        assertEquals(0.7, f(0), 1e-9)
        assertEquals(Double.NEGATIVE_INFINITY, f(11 * minute))
        assertEquals(Double.NEGATIVE_INFINITY, f(-1))
        assertEquals(ContextScorer.OWN, ContextScorer.freshness(Candidate(SuggestKind.PHONE, true)))
    }

    @Test fun aFreshClipAloneNeverReachesTheThreshold() {
        // the best case without a cue: link copied now, messenger, short message field, maximum learned weight
        val best = ContextScorer.freshMax(SuggestKind.LINK) + ContextScorer.app(AppCategory.MESSENGER, SuggestKind.LINK) +
            ContextScorer.field(FieldClass.SHORT_MESSAGE, SuggestKind.LINK)
        assertTrue(best < ContextScorer.THRESHOLD, "$best")
        assertTrue(ContextCues.find("Wie geht es dir? ").isEmpty())
    }

    @Test fun anOldLinkNeedsMoreThanAWeakCue() {
        assertFalse(offered("hier ", Candidate(SuggestKind.LINK, false, 9 * minute)))
        assertFalse(offered("guck mal, hier dieses Video ", Candidate(SuggestKind.LINK, false, 9 * minute)))
        assertTrue(offered("hier ", Candidate(SuggestKind.LINK, false, 0), AppCategory.MESSENGER, FieldClass.SHORT_MESSAGE))
        // a browser lowers links, a messenger raises them
        val text = "guck mal hier "
        val link = Candidate(SuggestKind.LINK, false, 3 * minute)
        assertTrue(score(text, link, AppCategory.BROWSER)!! < score(text, link, AppCategory.OTHER)!!)
        assertTrue(score(text, link, AppCategory.MESSENGER)!! > score(text, link, AppCategory.OTHER)!!)
    }

    @Test fun ownDataPassesWithAClearCue() {
        assertTrue(offered("meine Nummer ist ", Candidate(SuggestKind.PHONE, own = true)))
        assertTrue(offered("IBAN: ", Candidate(SuggestKind.IBAN, own = true)))
        assertTrue(offered("Adresse: ", Candidate(SuggestKind.ADDRESS, own = true)))
        assertTrue(offered("Code: ", Candidate(SuggestKind.OTP, own = false, ageMillis = minute)))
        // the kind must match the cue
        val match = cue("IBAN: ", SuggestKind.IBAN)!!
        assertEquals(Double.NEGATIVE_INFINITY, ContextScorer.score(match, Candidate(SuggestKind.PHONE, true), AppCategory.OTHER, FieldClass.TEXT, none))
    }

    @Test fun appCategoriesFromThePackageName() {
        assertEquals(AppCategory.MESSENGER, AppCategory.of("com.whatsapp"))
        assertEquals(AppCategory.MESSENGER, AppCategory.of("org.telegram.messenger"))
        assertEquals(AppCategory.MESSENGER, AppCategory.of("org.thoughtcrime.securesms"))
        assertEquals(AppCategory.MAIL, AppCategory.of("com.google.android.gm"))
        assertEquals(AppCategory.BROWSER, AppCategory.of("com.android.chrome"))
        assertEquals(AppCategory.BROWSER, AppCategory.of("org.mozilla.firefox"))
        assertEquals(AppCategory.OTHER, AppCategory.of("com.whatsappfake"))
        assertEquals(AppCategory.OTHER, AppCategory.of(null))
    }

    // --- fields ---

    @Test fun fieldTargetsFromTypeHintAndIdName() {
        fun t(c: FieldClass, hint: String = "", name: String = "", autofill: List<String> = emptyList()) =
            FieldTargets.target(FieldSignals(c, hint, name, autofill))
        assertEquals(FieldTarget(SuggestKind.EMAIL), t(FieldClass.EMAIL))
        assertEquals(FieldTarget(SuggestKind.PHONE), t(FieldClass.PHONE))
        assertEquals(FieldTarget(SuggestKind.NAME), t(FieldClass.PERSON_NAME))
        assertEquals(FieldTarget(SuggestKind.ADDRESS), t(FieldClass.POSTAL_ADDRESS))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.TEXT, autofill = listOf("smsOTPCode")))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.TEXT, autofill = listOf("one-time-code")))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.NUMBER, "Bestätigungscode"))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.TEXT, "Code eingeben"))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.TEXT, "OTP"))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.PHONE, "Verification code"))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.TEXT, name = "otpInput"))
        assertEquals(FieldTarget(SuggestKind.OTP), t(FieldClass.TEXT, name = "verification_code"))
        assertEquals(FieldTarget(SuggestKind.OTP, weak = true), t(FieldClass.NUMBER))
        assertEquals(FieldTarget(SuggestKind.IBAN), t(FieldClass.TEXT, "IBAN"))
        assertEquals(FieldTarget(SuggestKind.EMAIL), t(FieldClass.TEXT, "E-Mail-Adresse"))
        assertEquals(FieldTarget(SuggestKind.PHONE), t(FieldClass.TEXT, "Handynummer"))
        assertEquals(FieldTarget(SuggestKind.ADDRESS), t(FieldClass.TEXT, "Lieferadresse"))
        assertEquals(FieldTarget(SuggestKind.NAME), t(FieldClass.TEXT, "Name"))
    }

    @Test fun codeLikeFieldsThatAreNoCodeFields() {
        fun t(hint: String, c: FieldClass = FieldClass.TEXT) = FieldTargets.target(FieldSignals(c, hint))
        assertNull(t("Postleitzahl / ZIP code"))
        assertNull(t("Gutscheincode"))
        assertNull(t("Promo code"))
        assertNull(t("Código postal"))
        assertNull(t("Benutzername"))
        assertNull(t("Nachricht"))
        assertNull(t(""))
        // the e-mail confirmation of a password is no e-mail field
        assertNull(t("Passwort für E-Mail"))
        assertNull(FieldTargets.target(FieldSignals(FieldClass.OTHER, "Code")))
        assertNull(t("Vorname"))
    }

    @Test fun aFieldValueFitsAnEmptyFieldOrWhatIsTyped() {
        assertTrue(FieldTargets.fits("", "max@example.com", SuggestKind.EMAIL))
        assertTrue(FieldTargets.fits("Ma", "max@example.com", SuggestKind.EMAIL))
        assertFalse(FieldTargets.fits("max@example.com", "max@example.com", SuggestKind.EMAIL))
        assertFalse(FieldTargets.fits("anna", "max@example.com", SuggestKind.EMAIL))
        assertTrue(FieldTargets.fits("12", "123456", SuggestKind.OTP))
        assertTrue(FieldTargets.fits("+49 151", "+4915112345678", SuggestKind.PHONE))
        assertFalse(FieldTargets.fits("", "", SuggestKind.NAME))
    }

    // --- learning ---

    @Test fun acceptingRaisesAndIgnoringLowersWithinBounds() {
        var w = ContextWeights.empty(10)
        w = w.accept("media", SuggestKind.LINK, 10)
        val once = w.get("media", SuggestKind.LINK)
        assertTrue(once > 0)
        repeat(200) { w = w.accept("media", SuggestKind.LINK, 10) }
        val high = w.get("media", SuggestKind.LINK)
        assertTrue(high <= ContextWeights.MAX && high > once)
        repeat(500) { w = w.ignore("media", SuggestKind.LINK, 10) }
        val low = w.get("media", SuggestKind.LINK)
        assertTrue(low >= ContextWeights.MIN && low < 0)
        // other cues and kinds are untouched
        assertEquals(0.0, w.get("look", SuggestKind.LINK))
        assertEquals(0.0, w.get("media", SuggestKind.PHONE))
        // unknown ids are never stored
        assertEquals(w.size, w.accept("guck mal, hier", SuggestKind.LINK, 10).size)
    }

    @Test fun ignoringStepsAreSmallerThanAccepting() {
        val start = ContextWeights.empty(1)
        val up = start.accept("here", SuggestKind.LINK, 1).get("here", SuggestKind.LINK)
        val down = start.ignore("here", SuggestKind.LINK, 1).get("here", SuggestKind.LINK)
        assertTrue(up > -down && down < 0)
    }

    @Test fun learnedWeightsChangeWhetherAChipShows() {
        val link = Candidate(SuggestKind.LINK, false, 0)
        val text = "hier "
        assertFalse(offered(text, link))
        var w = ContextWeights.empty(100)
        repeat(5) { w = w.accept("here", SuggestKind.LINK, 100) }
        assertTrue(offered(text, link, weights = w))
        repeat(40) { w = w.ignore("media", SuggestKind.LINK, 100) }
        assertFalse(offered("dieses Video ", Candidate(SuggestKind.LINK, false, 4 * minute), weights = w))
    }

    @Test fun weightsFadeWithAHalfLife() {
        val w = ContextWeights.empty(0).accept("phone", SuggestKind.PHONE, 0)
        val start = w.get("phone", SuggestKind.PHONE)
        assertEquals(start / 2, w.decayed(30).get("phone", SuggestKind.PHONE), 1e-9)
        assertEquals(start / 4, w.decayed(60).get("phone", SuggestKind.PHONE), 1e-9)
        assertEquals(0, w.decayed(3000).size)
        // a clock going back changes nothing
        assertEquals(start, w.decayed(-5).get("phone", SuggestKind.PHONE))
        // a change on a later day fades first
        val later = w.accept("phone", SuggestKind.PHONE, 30).get("phone", SuggestKind.PHONE)
        assertEquals(start / 2 + ContextWeights.ACCEPT_RATE * (ContextWeights.MAX - start / 2), later, 1e-9)
    }

    @Test fun encodedWeightsHoldOnlyIdsAndNumbers() {
        val w = ContextWeights.empty(5).accept("media", SuggestKind.LINK, 5).ignore("iban", SuggestKind.IBAN, 5)
        val encoded = w.encode()
        assertTrue(Regex("v1;\\d+(;[a-z]+:[A-Z]+=-?\\d+\\.\\d{4})*").matches(encoded), encoded)
        val read = ContextWeights.parse(encoded, 5)
        assertEquals(w.get("media", SuggestKind.LINK), read.get("media", SuggestKind.LINK), 1e-4)
        assertEquals(w.get("iban", SuggestKind.IBAN), read.get("iban", SuggestKind.IBAN), 1e-4)
        assertFalse(w.toString().contains("media"))
    }

    @Test fun malformedStoredWeightsAreDroppedNotThrown() {
        assertEquals(0, ContextWeights.parse(null, 1).size)
        assertEquals(0, ContextWeights.parse("garbage", 1).size)
        assertEquals(0, ContextWeights.parse("v2;1;media:LINK=0.1", 1).size)
        assertEquals(0, ContextWeights.parse("v1;1;" + "x".repeat(5000), 1).size)
        val read = ContextWeights.parse("v1;1;media:LINK=9;secret text:LINK=0.2;look:NOPE=0.1;here:LINK=NaN;phone:PHONE=abc;look:LINK=-0.2", 1)
        assertEquals(ContextWeights.MAX, read.get("media", SuggestKind.LINK))
        assertEquals(-0.2, read.get("look", SuggestKind.LINK), 1e-9)
        assertEquals(2, read.size)
        // a day in the future is treated as today
        assertEquals(1L, ContextWeights.parse("v1;99999;media:LINK=0.1", 1).day)
    }
}
