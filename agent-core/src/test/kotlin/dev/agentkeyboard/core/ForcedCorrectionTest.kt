// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 61: which fields the per-app forcing of suggestions, autocorrection and capitals may touch. */
class ForcedCorrectionTest {
    private val text = FieldReport.TYPE_CLASS_TEXT

    @Test fun anInstagramLikeMessageFieldQualifies() {
        // single line, NO_SUGGESTIONS, no CAP flag
        assertTrue(ForcedCorrection.eligible(text or FieldReport.FLAG_NO_SUGGESTIONS, 0))
        assertTrue(ForcedCorrection.eligible(text, 0))
        assertTrue(ForcedCorrection.eligible(text or FieldReport.VARIATION_SHORT_MESSAGE, 0))
        assertTrue(ForcedCorrection.eligible(text or FieldReport.VARIATION_LONG_MESSAGE or FieldReport.FLAG_MULTI_LINE, 4))
        assertTrue(ForcedCorrection.eligible(text or FieldReport.VARIATION_EMAIL_SUBJECT, 0))
        assertTrue(ForcedCorrection.eligible(text or FieldReport.VARIATION_WEB_EDIT_TEXT, 0))
    }

    @Test fun addressesNamesFiltersAndPasswordsNeverQualify() {
        for (variation in listOf(FieldReport.VARIATION_URI, FieldReport.VARIATION_EMAIL_ADDRESS, FieldReport.VARIATION_WEB_EMAIL_ADDRESS,
            FieldReport.VARIATION_PERSON_NAME, FieldReport.VARIATION_POSTAL_ADDRESS, FieldReport.VARIATION_PASSWORD,
            FieldReport.VARIATION_VISIBLE_PASSWORD, FieldReport.VARIATION_WEB_PASSWORD, FieldReport.VARIATION_FILTER,
            FieldReport.VARIATION_PHONETIC)) {
            assertFalse(ForcedCorrection.eligible(text or variation, 0), "variation 0x${variation.toString(16)}")
            // the flags of the app change nothing about it
            assertFalse(ForcedCorrection.eligible(text or variation or FieldReport.FLAG_NO_SUGGESTIONS or FieldReport.FLAG_MULTI_LINE, 0))
        }
    }

    @Test fun aSearchActionNeverQualifies() {
        assertFalse(ForcedCorrection.eligible(text, FieldReport.IME_ACTION_SEARCH))
        // other bits of imeOptions do not hide the action (flags above the action mask)
        assertFalse(ForcedCorrection.eligible(text or FieldReport.FLAG_NO_SUGGESTIONS, FieldReport.IME_ACTION_SEARCH or FieldReport.IME_FLAG_NO_PERSONALIZED_LEARNING))
        // send (4) and done (6) are message actions
        assertTrue(ForcedCorrection.eligible(text, 4))
        assertTrue(ForcedCorrection.eligible(text, 6 or FieldReport.IME_FLAG_NO_PERSONALIZED_LEARNING))
    }

    @Test fun loginFieldsAndAsciiFieldsNeverQualify() {
        // GO and NEXT are the actions of login and form fields (user names, account names)
        assertFalse(ForcedCorrection.eligible(text, FieldReport.IME_ACTION_GO))
        assertFalse(ForcedCorrection.eligible(text or FieldReport.FLAG_NO_SUGGESTIONS, FieldReport.IME_ACTION_NEXT))
        assertFalse(ForcedCorrection.eligible(text, FieldReport.IME_FLAG_FORCE_ASCII))
        assertFalse(ForcedCorrection.eligible(text, 4 or FieldReport.IME_FLAG_FORCE_ASCII))
        // NONE (1), UNSPECIFIED (0), SEND (4), DONE (6) and AUTO_COMPLETE stay eligible
        for (action in listOf(0, 1, 4, 6)) assertTrue(ForcedCorrection.eligible(text, action), "action $action")
        assertTrue(ForcedCorrection.eligible(text or FieldReport.FLAG_AUTO_COMPLETE, 4))
    }

    @Test fun goAndNextOnlyExcludeSingleLineFields() {
        // Android's TextView sets NEXT by itself for a chat field: multi-line fields stay eligible
        val chat = text or FieldReport.FLAG_MULTI_LINE or FieldReport.FLAG_NO_SUGGESTIONS
        assertTrue(ForcedCorrection.eligible(chat, FieldReport.IME_ACTION_NEXT))
        assertTrue(ForcedCorrection.eligible(chat, FieldReport.IME_ACTION_GO))
        assertFalse(ForcedCorrection.eligible(text or FieldReport.FLAG_NO_SUGGESTIONS, FieldReport.IME_ACTION_NEXT))
        // search and ASCII stay excluded in a multi-line field
        assertFalse(ForcedCorrection.eligible(chat, FieldReport.IME_ACTION_SEARCH))
        assertFalse(ForcedCorrection.eligible(chat, FieldReport.IME_FLAG_FORCE_ASCII))
    }

    @Test fun theReasonIsNamed() {
        assertEquals(ForcedCorrection.Excluded.PASSWORD, ForcedCorrection.excludedReason(text or FieldReport.VARIATION_PASSWORD, 0))
        assertEquals(ForcedCorrection.Excluded.ADDRESS, ForcedCorrection.excludedReason(text or FieldReport.VARIATION_EMAIL_ADDRESS, 0))
        assertEquals(ForcedCorrection.Excluded.NAME, ForcedCorrection.excludedReason(text or FieldReport.VARIATION_PERSON_NAME, 0))
        assertEquals(ForcedCorrection.Excluded.FILTER, ForcedCorrection.excludedReason(text or FieldReport.VARIATION_FILTER, 0))
        assertEquals(ForcedCorrection.Excluded.SEARCH, ForcedCorrection.excludedReason(text, FieldReport.IME_ACTION_SEARCH))
        assertEquals(ForcedCorrection.Excluded.GO_NEXT, ForcedCorrection.excludedReason(text, FieldReport.IME_ACTION_GO))
        assertEquals(ForcedCorrection.Excluded.ASCII, ForcedCorrection.excludedReason(text, FieldReport.IME_FLAG_FORCE_ASCII))
        assertEquals(ForcedCorrection.Excluded.NOT_TEXT, ForcedCorrection.excludedReason(FieldReport.TYPE_CLASS_NUMBER, 0))
        assertEquals(null, ForcedCorrection.excludedReason(text, 4))
    }

    @Test fun onlyTextFieldsQualify() {
        assertFalse(ForcedCorrection.eligible(0, 0))
        assertFalse(ForcedCorrection.eligible(FieldReport.TYPE_CLASS_NUMBER, 0))
        assertFalse(ForcedCorrection.eligible(FieldReport.TYPE_CLASS_NUMBER or FieldReport.NUMBER_VARIATION_PASSWORD, 0))
        assertFalse(ForcedCorrection.eligible(FieldReport.TYPE_CLASS_PHONE, 0))
        assertFalse(ForcedCorrection.eligible(FieldReport.TYPE_CLASS_DATETIME, 0))
    }

    @Test fun sentenceCapitalsOnlyWhenForcedAndTheAppAsksForNone() {
        val plain = text or FieldReport.FLAG_NO_SUGGESTIONS
        assertEquals(plain or FieldReport.FLAG_CAP_SENTENCES, ForcedCorrection.capsInputType(plain, forced = true))
        assertEquals(plain, ForcedCorrection.capsInputType(plain, forced = false))
        // an app that asked for something keeps it
        for (flag in listOf(FieldReport.FLAG_CAP_WORDS, FieldReport.FLAG_CAP_CHARACTERS, FieldReport.FLAG_CAP_SENTENCES))
            assertEquals(plain or flag, ForcedCorrection.capsInputType(plain or flag, forced = true))
        // not a text field: untouched
        assertEquals(FieldReport.TYPE_CLASS_NUMBER, ForcedCorrection.capsInputType(FieldReport.TYPE_CLASS_NUMBER, forced = true))
    }

    @Test fun instagramIsTheDefault() {
        assertEquals(setOf("com.instagram.android"), ForcedCorrection.DEFAULT_PACKAGES)
    }
}
