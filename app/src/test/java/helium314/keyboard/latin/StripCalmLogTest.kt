// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.ShadowBinaryDictionaryUtils
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.ClipVault
import helium314.keyboard.agent.LockState
import helium314.keyboard.agent.TypingPerf
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.suggestions.BarState
import helium314.keyboard.latin.suggestions.SuggestionStripView
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 89 (part 1, measurement): what the fixed bar shows after every key of a typed sentence and of ten backspaces, through the
 * real `KeyboardActionListenerImpl`, `LatinIME`, `InputLogic` and strip view (see [TypingRig]), with the state of [BarState] sampled
 * at every call into the editor and every counted place of work, so a switch inside one key is seen too.
 *
 * The protocol of today is held in `src/test/resources/stage89/strip-protocol-*.txt`. When the rule of the bar changes (see
 * `docs/LEISTE-RUHE-KONZEPT.md`), replace those files with the expected sequence of the new rule (the test writes the actual one to
 * `build/strip-protocol-*.txt`); nothing else in this class needs to change. Nothing here has been seen on a device.
 *
 * Columns: key | state right after the key (before the delayed messages run) | state after they ran | every state seen during the key
 * (T = tools, W = content shown; + = the last update offered content, - = nothing offered) | words the strip holds after the key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class, ShadowProximityInfo::class,
    ShadowBinaryDictionaryUtils::class, ShadowLocaleManagerCompat::class])
class StripCalmLogTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val rig = TypingRig(latinIME)

    @Before fun setUp() {
        rig.setUp()
        rig.known = rig.known + setOf("hallo", "Hallo", "wie", "geht", "es", "dir", "gut", "Gut", "und", "Und", "ich")
    }
    @After fun tearDown() { ShadowInputMethodService.reset(); LockState.resetForTest(); ClipVault.clear() }

    private val strip: SuggestionStripView get() =
        latinIME.javaClass.getDeclaredField("mSuggestionStripView").apply { isAccessible = true }.get(latinIME) as SuggestionStripView
    private val barState: BarState get() =
        SuggestionStripView::class.java.getDeclaredField("barState").apply { isAccessible = true }.get(strip) as BarState
    private val stripWords: String get() = SuggestionStripView::class.java.getDeclaredField("suggestedWords")
        .apply { isAccessible = true }.get(strip).let { w -> w as SuggestedWords; (0 until w.size()).joinToString(",") { w.getWord(it) } }

    /**
     * What the owner sees in the bar: T tools, W words, C a chip or pill (external view), E the words area with nothing in it.
     * (Stage 89, critic K1: counting only tools against content would not see "words, then empty, then words" as a flicker.)
     */
    private fun state(): String {
        if (barState.show == BarState.Show.TOOLS) return "T"
        if (strip.isExternalSuggestionVisible) return "C"
        return if (stripWords.isEmpty()) "E" else "W"
    }

    private val b = TypingRig.BACKSPACE_MARK
    private val uiThread = android.os.Looper.getMainLooper().thread

    private fun protocol(script: String, slowConnection: Boolean = false, resumeAfterDelete: Boolean = false): List<String> {
        // multi-line: Enter is a paragraph (a single-line field sends the editor action instead)
        rig.startField("", restarting = true,
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        if (slowConnection) {
            // an app whose editor answers slowly (more than 200 ms once): for ten minutes every backspace shows the neutral strip
            val connection = latinIME.mInputLogic.mConnection
            connection.javaClass.getDeclaredField("mLastSlowInputConnectionTime").apply { isAccessible = true }
                .setLong(connection, android.os.SystemClock.uptimeMillis())
        }
        rig.settle()
        val rows = ArrayList<String>()
        rows.add("FIELD|${state()}|${state()}|${state()}|${stripWords}") // the new, empty field before the first key
        var seen = ArrayList<String>()
        fun sample() {
            if (Thread.currentThread() !== uiThread) return
            val s = state()
            if (seen.last() != s) seen.add(s)
        }
        ShadowInputMethodService.callListener = { _, _ -> sample() }
        TypingPerf.workListener = { _ -> sample() }
        try {
            for (c in script) {
                val code = if (c == b) KeyCode.DELETE else c.code
                seen = arrayListOf(state())
                // an app that does not keep the composing region (a web view): the delete finds no composing word
                if (resumeAfterDelete && c == b) latinIME.mInputLogic.finishInput()
                rig.tapFast(code)
                // the app answers a delete with a selection update the keyboard does not expect (what a slow app does): resume suggestions
                if (resumeAfterDelete && c == b) latinIME.mHandler.postResumeSuggestions(true)
                val afterKey = state()
                sample()
                rig.settle()
                sample()
                if (c == '\n') println("after ENTER: text=[" + rig.text.replace("\n","<NL>") + "] composing=[" + rig.composing + "]")
                val label = if (c == b) "<-" else if (c == '\n') "ENTER" else if (c == ' ') "SPACE" else "$c"
                rows.add("$label|$afterKey|${state()}|${seen.joinToString(">")}|${stripWords}")
            }
            println("final text: " + rig.text.replace("\n", "<NL>"))
        } finally {
            ShadowInputMethodService.callListener = null
            TypingPerf.workListener = null
        }
        return rows
    }

    /** The visible kinds in order: the state before the first key, then after every key and after the delayed messages of it. */
    private fun visible(rows: List<String>): List<String> =
        listOf(rows[0].split("|")[1]) + rows.drop(1).flatMap { r -> r.split("|").let { listOf(it[1], it[2]) } }

    /** Every visible change of what the bar shows, empty included (critic K1). */
    private fun switches(rows: List<String>) = visible(rows).zipWithNext().count { (a, c) -> a != c }

    /** Changes between the tools and anything else. */
    private fun toolSwitches(rows: List<String>) = visible(rows).zipWithNext().count { (a, c) -> (a == "T") != (c == "T") }

    /** The visible changes caused by the rows [from] until the end (the rows after row [from] - 1). */
    private fun switchesFrom(rows: List<String>, from: Int) = switches(rows.subList(from - 1, rows.size))

    private fun check(name: String, rows: List<String>) {
        val text = rows.joinToString("\n") + "\n"
        File("build/strip-protocol-$name.txt").apply { parentFile?.mkdirs() }.writeText(text)
        val expected = javaClass.getResourceAsStream("/stage89/strip-protocol-$name.txt")?.bufferedReader()?.readLines()
            ?: throw AssertionError("no recorded protocol yet: written to build/strip-protocol-$name.txt (copy to src/test/resources/stage89/)")
        assertEquals(expected.size, rows.size, "number of keys")
        for (i in rows.indices) assertEquals(expected[i], rows[i], "key ${i + 1}")
    }

    private val sentence = "Hallo, wie geht es dir. Gut\nUnd"
    private val tenBackspaces = b.toString().repeat(10)

    // ---- the protocol of the new rule (stage 89): golden files plus counted claims ----

    /** No next-word predictions: the words area is empty after most spaces. */
    @Test fun sentenceWithoutPredictions() {
        rig.predictions = emptyList()
        val rows = protocol(sentence + tenBackspaces)
        println("visible changes without predictions: ${switches(rows)}, tools: ${toolSwitches(rows)}")
        assertEquals(1, toolSwitches(rows), "the tools go away with the first key and never come back: " + visible(rows))
        check("no-predictions", rows)
    }

    /** The dictionary offers next words after a space. */
    @Test fun sentenceWithPredictions() {
        rig.predictions = listOf("wie", "und", "ich")
        val rows = protocol(sentence + tenBackspaces)
        println("visible changes with predictions: ${switches(rows)}, tools: ${toolSwitches(rows)}")
        assertEquals(1, toolSwitches(rows), visible(rows).toString())
        check("with-predictions", rows)
    }

    /** An app with a slow editor connection: every backspace asks for the neutral strip (InputLogic line 1731). */
    @Test fun sentenceWithSlowConnection() {
        rig.predictions = emptyList()
        val rows = protocol(sentence + tenBackspaces, slowConnection = true)
        println("visible changes with slow connection: ${switches(rows)}, tools: ${toolSwitches(rows)}")
        assertEquals(1, toolSwitches(rows), visible(rows).toString())
        check("slow-connection", rows)
    }

    /**
     * The suspected way on the owner's phone (critic K1): slow connection, and the app's selection update after every delete makes the
     * keyboard resume suggestions. Deleting inside a word must not change what the bar shows at all: not to the tools, not to empty and back.
     */
    @Test fun deletingInsideAWordWithSlowConnectionAndResumeDoesNotChangeTheBar() {
        rig.predictions = emptyList()
        val rows = protocol("Hallo wie" + b + b, slowConnection = true, resumeAfterDelete = true)
        val firstDelete = rows.indexOfFirst { it.startsWith("<-|") } // row index; row 0 is the field start
        println("slow + resume, rows: " + rows.drop(firstDelete))
        assertEquals(0, switchesFrom(rows, firstDelete + 1), "inside the word 'wie': " + rows.drop(firstDelete))
    }

    /** The same without a slow connection: the resume after a delete inside a word shows the same words, no empty in between. */
    @Test fun deletingInsideAWordWithResumeDoesNotChangeTheBar() {
        rig.predictions = emptyList()
        val rows = protocol("Hallo wie" + b + b, resumeAfterDelete = true)
        val firstDelete = rows.indexOfFirst { it.startsWith("<-|") }
        assertEquals(0, switchesFrom(rows, firstDelete + 1), rows.drop(firstDelete).toString())
    }

    @Test fun theProtocolSeesTheBar() {
        val rows = protocol("Hallo ")
        assertTrue(rows.isNotEmpty() && rows.all { it.split("|").size == 5 }, rows.toString())
    }
}
