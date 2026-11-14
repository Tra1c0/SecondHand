// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.res.Configuration
import android.text.InputType
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.utils.CapsModeUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Stage 48: the typed word first after a correction, and no capitals after abbreviations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TypedWordFirstTest {
    private fun info(word: String, kind: Int = SuggestedWordInfo.KIND_CORRECTION) =
        SuggestedWordInfo(word, "", 100, kind, Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)

    private fun words(vararg words: String, style: Int = SuggestedWords.INPUT_STYLE_TYPING, autoCorrect: Boolean = false) =
        SuggestedWords(ArrayList(words.mapIndexed { i, w -> info(w, if (i == 0) SuggestedWordInfo.KIND_TYPED else SuggestedWordInfo.KIND_CORRECTION) }),
            null, info(words[0], SuggestedWordInfo.KIND_TYPED), false, autoCorrect, false, style, SuggestedWords.NOT_A_SEQUENCE_NUMBER)

    private fun SuggestedWords.list() = (0 until size()).map { getWord(it) }

    @Test fun memoryMatchesStartAndWordOnly() {
        val memory = TypedWordFirst()
        memory.corrected(6, "hello", "hullo")
        assertEquals("hullo", memory.resumed(6, "hello"))
        assertEquals("hullo", memory.typedFor("hello", true))
        assertNull(memory.typedFor("hello", false)) // not a resumed word
        assertNull(memory.typedFor("hellos", true)) // changed since
        assertEquals("hello", memory.correctionUndoneBy("hullo", "hello", true))
        assertNull(memory.correctionUndoneBy("hull", "hello", true))
        assertNull(memory.resumed(0, "hello")) // another place
        assertNull(memory.typedFor("hello", true)) // and the active word is forgotten
        assertNull(memory.resumed(6, "Hello")) // the text there changed
        // undone with backspace: the typed word stands there, nothing left to undo
        memory.reverted(6, "hullo")
        assertEquals("hullo", memory.resumed(6, "hullo"))
        assertNull(memory.correctionUndoneBy("hullo", "hullo", true))
        // nothing to remember without a change, and a new field forgets everything
        memory.corrected(20, "same", "same")
        assertNull(memory.resumed(20, "same"))
        memory.corrected(30, "OK", "ok")
        memory.clear()
        assertNull(memory.resumed(30, "OK"))
    }

    @Test fun memoryIsBounded() {
        val memory = TypedWordFirst()
        repeat(TypedWordFirst.MAX_ENTRIES + 1) { memory.corrected(it * 10, "word$it", "wrod$it") }
        assertNull(memory.resumed(0, "word0"))
        assertEquals("wrod1", memory.resumed(10, "word1"))
    }

    @Test fun arrangePutsTheTypedWordAtTheStartSlotWithItsCase() {
        val arranged = TypedWordFirst.arrange(words("hello", "hull", "iPhone", "hallo"), "iPhone")
        assertEquals(listOf("hello", "hull", "iPhone", "hallo"), arranged.list())
        assertTrue(arranged.getInfo(2).isKindOf(SuggestedWordInfo.KIND_TYPED))
        assertEquals(SuggestedWords.INPUT_STYLE_TYPING, arranged.mInputStyle)
        assertFalse(arranged.mWillAutoCorrect)
        // from the suggestion spans of the editor (recorrection style), too
        assertEquals(listOf("hello", "hull", "hullo"), TypedWordFirst.arrange(
            words("hello", "hullo", "hull", style = SuggestedWords.INPUT_STYLE_RECORRECTION), "hullo").list())
        // alone it takes the centre
        assertEquals(listOf("hullo", "hullo"), TypedWordFirst.arrange(words("hullo"), "hullo").list())
    }

    @Test fun arrangeLeavesOtherSuggestionsAlone() {
        val pending = words("hullo", "hello", style = SuggestedWords.INPUT_STYLE_TYPING, autoCorrect = true)
        assertSame(pending, TypedWordFirst.arrange(pending, "hullo"))
        val predictions = words("the", "a", style = SuggestedWords.INPUT_STYLE_PREDICTION)
        assertSame(predictions, TypedWordFirst.arrange(predictions, "the"))
        val gesture = words("hello", "hull", style = SuggestedWords.INPUT_STYLE_TAIL_BATCH)
        assertSame(gesture, TypedWordFirst.arrange(gesture, "hullo"))
    }

    private fun spacing(locale: Locale): SpacingAndPunctuations {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return SpacingAndPunctuations(context.createConfigurationContext(configuration).resources, false)
    }

    private fun sentenceCaps(text: String, spacing: SpacingAndPunctuations) =
        CapsModeUtils.getCapsMode(text, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, spacing, false) != 0

    @Test fun noCapitalAfterKnownAbbreviations() {
        val german = spacing(Locale.GERMAN)
        listOf("Es kostet ca. ", "Rot bzw. ", "Das ist z. B. ", "Das heißt d. h. ", "Siehe vgl. ", "Herr Dr. ", "Haus Nr. ",
            "Das ist z.B. ", "am 3. ", "ca. ").forEach { assertFalse(sentenceCaps(it, german), it) }
        // real sentence ends stay capitalized, also after words that often end a sentence
        listOf("Hallo. ", "Äpfel, Birnen usw. ", "Das war Plan B. ", "Ja! ", "Wirklich? ", "Max M. ", "").forEach {
            assertTrue(sentenceCaps(it, german), it)
        }
        val english = spacing(Locale.ENGLISH)
        listOf("Apples vs. ", "Hello Mr. ", "It is approx. ", "e.g. ").forEach { assertFalse(sentenceCaps(it, english), it) }
        listOf("Hello. ", "Fruit etc. ", "Plan B. ").forEach { assertTrue(sentenceCaps(it, english), it) }
        // the German words are not abbreviations in English: "Nr. " does not stop capitals there
        assertTrue(sentenceCaps("Haus Nr. ", english))
    }

    // stage 48 review N2/N3
    @Test fun abbreviationListAfterReview() {
        val german = spacing(Locale.GERMAN)
        listOf("Termin bzgl. ", "Das gilt insb. ", "Das gilt insbes. ", "Das ist lt. ", "Das ist gem. ", "Das sind mind. ",
            "Das ist allg. ", "Das ist ugs. ", "Das ist eigtl. ", "Das ist o. ä. ", "Das sind u. a. ")
            .forEach { assertFalse(sentenceCaps(it, german), it) }
        // often the end of a sentence: capitals stay
        listOf("Bis Fr. ", "Im 19. Jh. ", "Im 19. Jhd. ").forEach { assertTrue(sentenceCaps(it, german), it) }
        val english = spacing(Locale.ENGLISH)
        listOf("John Smith Jr. ", "John Smith Sr. ", "Main St. ", "Dear Ms. ").forEach { assertTrue(sentenceCaps(it, english), it) }
        // initials keep their capitals, in both languages
        assertTrue(sentenceCaps("Von J. K. ", english))
        assertTrue(sentenceCaps("Von J. K. ", german))
    }

    // stage 48 review N1/N4
    @Test fun aCommitAtTheSamePlaceForgetsTheOldCorrection() {
        val memory = TypedWordFirst()
        memory.corrected(0, "hello", "hullo")
        // the field was cleared and another word committed at the same start
        memory.committedAt(0)
        assertNull(memory.resumed(0, "hello"))
        // a new correction there is remembered again (committedAt runs before corrected)
        memory.committedAt(0)
        memory.corrected(0, "hello", "hullo")
        assertEquals("hullo", memory.resumed(0, "hello"))
        // a revert without a remembered correction (a gesture word) adds nothing
        memory.reverted(10, "Hello")
        assertNull(memory.resumed(10, "Hello"))
    }
}
