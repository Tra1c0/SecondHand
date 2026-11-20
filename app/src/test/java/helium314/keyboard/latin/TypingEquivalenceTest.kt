// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.ClipVault
import helium314.keyboard.agent.LockState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Stage 84: what ends up in the editor did not change while the strip work moved. For about forty typed sequences
 * (words, typos the stub dictionary corrects, symbols, spaces, backspaces) the editor transcript (every write the keyboard
 * made, in order, with its text), the final text and the words of the strip are compared with the transcript recorded from
 * the code as it was before stage 84 (`src/test/resources/stage84/typing-transcripts.txt`), once with the delayed
 * suggestion update running between the keys and once with the next key coming before it (the fast typist).
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class, ShadowProximityInfo::class,
    ShadowBinaryDictionaryUtils::class, ShadowLocaleManagerCompat::class])
class TypingEquivalenceTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val rig = TypingRig(latinIME)

    @Before fun setUp() { rig.setUp() }
    @After fun tearDown() { ShadowInputMethodService.reset(); LockState.resetForTest(); ClipVault.clear() }

    private val b = TypingRig.BACKSPACE_MARK

    private val sequences = listOf(
        "words" to "hello world ",
        "commaPeriod" to "hello, world. ",
        "typo" to "teh ",
        "typoInSentence" to "teh quick adn the ",
        "twoTypos" to "helo wrold ",
        "typosWithWords" to "wiht taht ",
        "typoInBrackets" to "(teh) ",
        "typoThenPeriod" to "teh.",
        "typoThenComma" to "teh, adn the end!",
        "mail" to "hello@example.com ",
        "hyphens" to "a-b c-d ",
        "apostrophe" to "it's the end ",
        "capitals" to "Hello World. teh ",
        "doubleSpace" to "hello  world",
        "dots" to "hello .. ",
        "arithmetic" to "1+1=2 and 3*4 ",
        "revertTypoWithBackspace" to "teh ${b}",
        "revertTypoTwice" to "teh ${b}${b}",
        "typoPeriodBackspace" to "teh.${b}",
        "typoPeriodSpaceBackspace" to "teh. ${b}",
        "periodBackspaceTwice" to "hello.${b}${b}",
        "deleteIntoWord" to "hello${b}${b}${b} world",
        "colonSemicolon" to "the end;teh:adn ",
        "quotes" to "\"teh\" ",
        "slash" to "teh/adn ",
        "questionExclamation" to "hello?! teh ",
        "squareCurly" to "[teh] {adn} ",
        "repeatedTypo" to "teh teh teh ",
        "sentences" to "a. b. c. ",
        "currency" to "\$5 for teh ",
        "dash" to "hello - teh ",
        "dashBeforeWord" to "teh -adn ",
        "atInWord" to "teh@adn ",
        "expression" to "x=(teh+adn) ",
        "ellipsis" to "end. . . teh",
        "underscores" to "teh _adn_ ",
        "mixedPunctuation" to "helo, wrold! teh? ",
        "commaBackspaces" to "teh,${b}${b}adn ",
        "periodBackspaceWord" to "hello world.${b} teh ",
        "ampersand" to "&teh& ",
        "percentHash" to "50% #teh ",
    )

    private fun transcript(name: String, script: String, fast: Boolean): String {
        rig.startField("", restarting = true)
        rig.idle()
        val timeline = rig.record { rig.play(script, fast) }
        val writes = timeline.steps.filter { it.ui && it.what in WRITES }
            .joinToString(";") { it.what + (it.text?.let { t -> ":" + t } ?: "") }
        val words = latinIME.mInputLogic.mSuggestedWords.let { w -> (0 until w.size()).joinToString(",") { w.getWord(it) } }
        // the words of the strip after a backspace (the suggestions come back for the word the cursor is in) or after the fast
        // typist's last key depend on which of two messages is answered first when the machine is busy: only the editor is compared then
        val strip = if (fast || script.contains(b)) "" else "|strip=$words"
        return "$name|${if (fast) "fast" else "normal"}|text=${rig.text}|composing=${rig.composing}$strip|writes=$writes"
            .replace("\n", "⏎")
    }

    private fun all() = sequences.flatMap { (name, script) -> listOf(false, true).map { fast -> transcript(name, script, fast) } }

    @Test fun theEditorTranscriptsAreTheOnesRecordedBeforeStage84() {
        val actual = all()
        val expected = javaClass.getResourceAsStream("/stage84/typing-transcripts.txt")?.bufferedReader()?.readLines()
        if (expected == null) {
            File("build/typing-transcripts.txt").apply { parentFile?.mkdirs() }.writeText(actual.joinToString("\n") + "\n")
            throw AssertionError("no recorded transcripts yet: written to build/typing-transcripts.txt (copy to src/test/resources/stage84/)")
        }
        assertEquals(expected.size, actual.size, "number of transcripts")
        for (i in actual.indices) assertEquals(expected[i], actual[i], "transcript ${i + 1}")
    }

    @Test fun theTranscriptsAreTheSameEveryTime() {
        val first = all()
        for (round in 1..2) {
            val again = all()
            for (i in first.indices) assertEquals(first[i], again[i], "round $round, transcript ${i + 1}")
        }
    }

    @Test fun theSequencesDoSomething() {
        // guards the fixture itself: corrections happen, symbols end words, backspace reverts
        val lines = all()
        assertNotNull(lines.firstOrNull { it.startsWith("typo|normal|text=the ") }, lines.first { it.startsWith("typo|normal") })
        assertNotNull(lines.firstOrNull { it.startsWith("typoThenPeriod|normal|text=the.") }, lines.first { it.startsWith("typoThenPeriod|normal") })
        assertNotNull(lines.firstOrNull { it.startsWith("revertTypoWithBackspace|normal|text=teh") }, lines.first { it.startsWith("revertTypoWithBackspace|normal") })
        assertNotNull(lines.firstOrNull { it.startsWith("doubleSpace|normal|text=hello. world") }, lines.first { it.startsWith("doubleSpace|normal") })
    }

    private companion object {
        val WRITES = setOf("setComposingText", "commitText", "finishComposingText", "deleteSurroundingText", "sendKeyEvent",
            "setSelection", "setComposingRegion", "commitCorrection")
    }
}
