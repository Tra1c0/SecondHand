// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage 96 (replaces the order of 94): left the old word, or the word as it stands while characters of it are deleted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CurrentWordShownTest {
    private fun info(word: String, kind: Int = SuggestedWordInfo.KIND_CORRECTION) =
        SuggestedWordInfo(word, "", 100, kind, Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)

    private fun words(vararg words: String, autoCorrect: Boolean = false) =
        SuggestedWords(ArrayList(words.mapIndexed { i, w -> info(w, if (i == 0) SuggestedWordInfo.KIND_TYPED else SuggestedWordInfo.KIND_CORRECTION) }),
            null, info(words[0], SuggestedWordInfo.KIND_TYPED), true, autoCorrect, false, SuggestedWords.INPUT_STYLE_TYPING, SuggestedWords.NOT_A_SEQUENCE_NUMBER)

    private fun SuggestedWords.list() = (0 until size()).map { getWord(it) }

    @Test fun aResumedWordStandsLeftAndTheEngineWordsInTheCentreAndRight() {
        // (a) the cursor went back into "Haus", nothing deleted: left the word, centre and right the other candidates
        val shown = CurrentWordShown.arrange(words("Haus", "Hans", "Haut", "Bau"), "Haus", "Haus", null)
        assertEquals(listOf("Haus", "Hans", "Haus", "Haut", "Bau"), shown.list()) // index 1 centre, 2 left, 3 right
        assertEquals(SuggestedWordInfo.KIND_TYPED, shown.getInfo(CurrentWordShown.LEFT_INDEX).kind)
        assertFalse(shown.mWillAutoCorrect)
    }

    @Test fun theTypedFormOfAResumedCorrectionStandsLeft() {
        // (a) "Jung" was corrected from "jungs": the old word left, the word itself is not repeated
        val shown = CurrentWordShown.arrange(words("Jung", "Junge", "Jungs", "jungs"), "Jung", "jungs", "jungs")
        assertEquals(listOf("Jung", "Junge", "jungs", "Jungs"), shown.list())
    }

    @Test fun whileDeletingTheWordAsItStandsStandsLeft() {
        // (b) "löschen" minus "en": "lösch" left, the engine's words in the centre and right
        val shown = CurrentWordShown.arrange(words("lösch", "lösche", "löscht"), "lösch", "lösch", null)
        assertEquals(listOf("lösch", "lösche", "lösch", "löscht"), shown.list())
    }

    @Test fun nothingIsRepeated() {
        val shown = CurrentWordShown.arrange(words("hallo", "hallo", "Hallo", "Hallo"), "hallo", "hallo", "hallo")
        assertEquals(listOf("hallo", "Hallo", "hallo"), shown.list())
    }

    @Test fun withoutOtherWordsTheWordStillStandsLeft() {
        // the owner's case of 94: "Jungs" resumed, the engine offers nothing but the word itself; a list of two would only fill the centre
        val shown = CurrentWordShown.arrange(words("Jungs"), "Jungs", "Jungs", null)
        assertEquals(listOf("Jungs", "Jungs", "Jungs"), shown.list())
    }

    @Test fun aPendingAutocorrectionKeepsItsOrder() {
        val pending = words("bur", "but", "bit", autoCorrect = true)
        assertEquals(pending.list(), CurrentWordShown.arrange(pending, "bur", "bur", null).list())
    }

    @Test fun anEmptyListStillShowsTheWordLeft() {
        val shown = CurrentWordShown.arrange(SuggestedWords.getEmptyInstance(), "Haus", "Haus", null)
        assertEquals(listOf("Haus", "Haus", "Haus"), shown.list())
        assertEquals("Haus", shown.getWord(CurrentWordShown.LEFT_INDEX))
    }
}
