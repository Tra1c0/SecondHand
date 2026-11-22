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

/** Stage 94: the word being edited with delete is shown as it stands; the typed form of a corrected word left of it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CurrentWordShownTest {
    private fun info(word: String, kind: Int = SuggestedWordInfo.KIND_CORRECTION) =
        SuggestedWordInfo(word, "", 100, kind, Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)

    private fun words(vararg words: String, autoCorrect: Boolean = false) =
        SuggestedWords(ArrayList(words.mapIndexed { i, w -> info(w, if (i == 0) SuggestedWordInfo.KIND_TYPED else SuggestedWordInfo.KIND_CORRECTION) }),
            null, info(words[0], SuggestedWordInfo.KIND_TYPED), true, autoCorrect, false, SuggestedWords.INPUT_STYLE_TYPING, SuggestedWords.NOT_A_SEQUENCE_NUMBER)

    private fun SuggestedWords.list() = (0 until size()).map { getWord(it) }

    @Test fun aResumedWordWithoutAlternativesIsShownInsteadOfAnEmptyStrip() {
        // the owner's case: "Jungs" resumed, the engine offers nothing but the word itself (index 0, never shown)
        val shown = CurrentWordShown.arrange(words("Jungs"), "Jungs", null)
        assertEquals(listOf("Jungs", "Jungs"), shown.list()) // index 1 = the centre
        assertFalse(shown.mWillAutoCorrect)
    }

    @Test fun theTypedFormOfACorrectionStandsLeftAndTheCurrentWordInTheCentre() {
        val shown = CurrentWordShown.arrange(words("Jung", "Junge", "Jungs", "jung"), "Jung", "jungs")
        assertEquals(listOf("Jung", "Jung", "jungs", "Junge", "Jungs", "jung"), shown.list())
    }

    @Test fun nothingIsRepeated() {
        val shown = CurrentWordShown.arrange(words("hallo", "hallo", "Hallo"), "hallo", "hallo")
        assertEquals(listOf("hallo", "hallo", "Hallo"), shown.list())
    }

    @Test fun aPendingAutocorrectionKeepsItsOrder() {
        val pending = words("bur", "but", "bit", autoCorrect = true)
        assertEquals(pending.list(), CurrentWordShown.arrange(pending, "bur", null).list())
    }

    @Test fun anEmptyListStillShowsTheWord() {
        val shown = CurrentWordShown.arrange(SuggestedWords.getEmptyInstance(), "Haus", null)
        assertEquals(listOf("Haus", "Haus"), shown.list())
    }
}
