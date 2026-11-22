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
import helium314.keyboard.latin.utils.prefs
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
                // the delayed messages run in steps of 10 ms (fake clock), so a change inside the 300 ms after the key is seen, not only its end
                repeat(40) { rig.idle(10); Thread.sleep(2); sample() }
                // stage 89b: a typing pace (600 ms per key in all), not rig.settle() (1.2 s more): a pause of 800 ms brings the tools by design
                repeat(4) { rig.idle(50); Thread.sleep(10) }
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

    /** The visible kinds in order (every state seen during and after each key, 10 ms steps, consecutive duplicates removed). */
    private fun visible(rows: List<String>): List<String> =
        rows.flatMap { r -> r.split("|")[3].split(">") }.fold(ArrayList<String>()) { acc, k -> if (acc.lastOrNull() != k) acc.add(k); acc }

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
        check("no-predictions", rows) // first, so that CI prints the protocol even when a claim below fails
        assertEquals(1, toolSwitches(rows), "the tools go away with the first key and never come back: " + visible(rows))
        assertEquals(0, visible(rows).count { it.startsWith("E") }, "never an empty words area (stage 89b): " + visible(rows))
    }

    /** The dictionary offers next words after a space. */
    @Test fun sentenceWithPredictions() {
        rig.predictions = listOf("wie", "und", "ich")
        val rows = protocol(sentence + tenBackspaces)
        println("visible changes with predictions: ${switches(rows)}, tools: ${toolSwitches(rows)}")
        check("with-predictions", rows)
        assertEquals(1, toolSwitches(rows), visible(rows).toString())
    }

    /** An app with a slow editor connection: every backspace asks for the neutral strip (InputLogic line 1731). */
    @Test fun sentenceWithSlowConnection() {
        rig.predictions = emptyList()
        val rows = protocol(sentence + tenBackspaces, slowConnection = true)
        println("visible changes with slow connection: ${switches(rows)}, tools: ${toolSwitches(rows)}")
        check("slow-connection", rows)
        assertEquals(1, toolSwitches(rows), visible(rows).toString())
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

    /**
     * Types [script] with [paceMs] (fake clock) between the keys and returns what the bar showed, sampled every 10 ms from the first key
     * until [paceMs] after the last one. [dictionaryDelayMs] holds the answer of a resume after a delete back (a slow app, F3).
     */
    private fun paced(script: String, paceMs: Int, slowConnection: Boolean = false, resumeAfterDelete: Boolean = false, resumeDelayMs: Int = 0): List<String> {
        rig.startField("", restarting = true)
        rig.settle()
        if (slowConnection) {
            val connection = latinIME.mInputLogic.mConnection
            connection.javaClass.getDeclaredField("mLastSlowInputConnectionTime").apply { isAccessible = true }
                .setLong(connection, android.os.SystemClock.uptimeMillis())
        }
        val kinds = ArrayList<String>()
        for (c in script) {
            val code = if (c == b) KeyCode.DELETE else c.code
            if (resumeAfterDelete && c == b) latinIME.mInputLogic.finishInput()
            rig.tapFast(code)
            kinds.add(state())
            // the app's selection update arrives late: the resume (100 ms after it) answers after resumeDelayMs + 100 ms
            if (resumeAfterDelete && c == b) { repeat(resumeDelayMs / 10) { rig.idle(10); Thread.sleep(2); kinds.add(state()) }; latinIME.mHandler.postResumeSuggestions(true) }
            repeat(paceMs / 10) { rig.idle(10); Thread.sleep(2); kinds.add(state()) }
        }
        return kinds
    }

    private fun noEmptyBetween(kinds: List<String>) = kinds.dropWhile { it == "T" }.count { it == "E" }

    /** F1: at a normal typing pace the words area does not blink empty between two words (a pause longer than the debounce may). */
    @Test fun atANormalTypingPaceTheWordsAreaNeverGoesEmptyBetweenWords() {
        for (pace in listOf(200, 300)) {
            rig.predictions = emptyList()
            val kinds = paced("wie geht es dir", pace)
            assertEquals(0, noEmptyBetween(kinds.dropLast(pace / 10)), "pace $pace ms: " + kinds.joinToString(""))
        }
    }

    /**
     * F3: a slow app answers the resume of a delete late. What this shows (honest): the debounce (250 ms) is shorter than the answer
     * (250 ms until the app's selection update, then 100 ms for the resume), so the words area goes empty for about the time the answer
     * is later than the debounce (here about 100 ms) and fills again. The debounce does not hide that: no constant fits an app whose
     * answer time is unknown. The diagnose card ("leer", "langsam") shows whether this is the way on the owner's phone.
     */
    @Test fun aResumeThatAnswersAfterTheDebounceShowsAShortEmptyBlink() {
        rig.predictions = emptyList()
        val kinds = paced("Hallo wie" + b + b, 150, slowConnection = true, resumeAfterDelete = true, resumeDelayMs = 250)
        println("late resume, kinds per 10 ms: " + kinds.joinToString(""))
        val runs = Regex("E+").findAll(kinds.joinToString("")).map { it.value.length }.toList()
        assertTrue(runs.all { it <= 15 }, "an empty phase longer than 150 ms: $runs")
    }

    /** The same answer within the debounce (100 ms after the app's update, 200 ms after the key): no empty phase at all. */
    @Test fun aResumeThatAnswersWithinTheDebounceShowsNoEmptyPhase() {
        rig.predictions = emptyList()
        val kinds = paced("Hallo wie" + b + b, 150, slowConnection = true, resumeAfterDelete = true, resumeDelayMs = 100)
        assertEquals(0, noEmptyBetween(kinds), kinds.joinToString(""))
    }

    /** F2: the keyboard could not read the text (position unknown, cache empty) in the middle of a text: that is no empty field. */
    @Test fun anUnreadableFieldMidTextDoesNotEndTheSession() {
        rig.predictions = emptyList()
        rig.startField("", restarting = true)
        rig.settle()
        rig.play("Hallo wie ")
        rig.settle()
        val connection = latinIME.mInputLogic.mConnection
        val cls = connection.javaClass
        cls.getDeclaredField("mExpectedSelStart").apply { isAccessible = true }.setInt(connection, -1)
        cls.getDeclaredField("mExpectedSelEnd").apply { isAccessible = true }.setInt(connection, -1)
        val cache = cls.getDeclaredField("mCommittedTextBeforeComposingText").apply { isAccessible = true }.get(connection)
        (cache as StringBuilder).setLength(0)
        latinIME.setNeutralSuggestionStrip()
        // stage 89b: after the pause of rig.settle() the tools show by design; the claim is that the session is not ended
        assertTrue(strip.isWritingSession(), "the session goes on: " + state())
    }

    /** F5: the field is emptied and the dictionary offers next words there: the words stay, the session is over. */
    @Test fun anEmptiedFieldWithPredictionsEndsTheSession() {
        rig.predictions = listOf("wie", "und", "ich")
        rig.startField("", restarting = true)
        rig.settle()
        rig.play("ab"); rig.settle()
        assertTrue(strip.isWritingSession())
        rig.play("" + b + b); rig.settle()
        assertEquals("", rig.text)
        assertTrue(!strip.isWritingSession(), "the session ended with the emptied field")
        assertEquals("W", state(), "the predictions stand")
        latinIME.setNeutralSuggestionStrip()
        assertEquals("T", state(), "a later neutral update shows the tools")
    }

    /** F4: no count in an incognito field; and a new field's first kind is no change. */
    @Test fun anIncognitoFieldIsNotCounted() {
        helium314.keyboard.agent.BarCalmCounters.resetForTest()
        latinIME.prefs().edit().putBoolean(helium314.keyboard.latin.settings.Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        rig.startField("")
        rig.settle()
        assertEquals(0, helium314.keyboard.agent.BarCalmCounters.total.fieldStarts)
        latinIME.prefs().edit().putBoolean(helium314.keyboard.latin.settings.Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        rig.startField("")
        assertEquals(1, helium314.keyboard.agent.BarCalmCounters.total.fieldStarts)
    }

    private val wordViews: List<android.widget.TextView> get() =
        @Suppress("UNCHECKED_CAST") (SuggestionStripView::class.java.getDeclaredField("wordViews").apply { isAccessible = true }.get(strip) as List<android.widget.TextView>)

    /** Outdated words (a neutral update waits for the debounce) insert nothing when tapped. */
    @Test fun aTapOnOutdatedWordsInsertsNothing() {
        rig.predictions = emptyList()
        rig.startField("", restarting = true, inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        helium314.keyboard.agent.BarCalmCounters.resetForTest()
        rig.settle()
        rig.type("Hallo"); rig.settle()
        assertEquals("W", state())
        val before = rig.text
        rig.tapFast(','.code) // the neutral update: the words of "Hallo" stand for 800 ms (stage 89b), outdated
        // a view without a width gets no text in a test: a word view of the strip is stood in for by a view with the tag the layout gives it
        val view = android.widget.TextView(latinIME).apply { tag = 0 }
        strip.onClick(view)
        assertEquals(before + ",", rig.text, "nothing was picked")
        assertEquals(1, helium314.keyboard.agent.BarCalmCounters.total.droppedTaps)
        repeat(90) { rig.idle(10) } // the debounce is over: a real pause, the tools (stage 89b), never an empty words area
        assertEquals("T", state())
    }

    /** "Leiste automatisch anzeigen" does not show the tools after Enter while the user writes; at the start of a field it still does. */
    @Test fun autoShowDoesNotShowTheToolsAfterEnterInTheSession() {
        latinIME.prefs().edit().putBoolean(helium314.keyboard.latin.settings.Settings.PREF_AUTO_SHOW_TOOLBAR, true).commit()
        val multi = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        rig.startField("", inputType = multi, imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        rig.settle()
        assertEquals("T", state(), "a new field: the tools")
        rig.play("Hallo wie"); rig.settle()
        rig.tapFast('\n'.code); rig.idle(300) // before the pause of 800 ms (stage 89b), which brings the tools by design
        assertTrue(state() != "T", "after Enter in the session: " + state())
    }

    /** A field that cannot show words keeps the tools (the rule does not apply). */
    @Test fun aPasswordFieldKeepsTheTools() {
        rig.startField("", inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        rig.settle()
        rig.play("abc d"); rig.settle()
        assertEquals("T", state())
    }

    /** The counters hold numbers only. */
    @Test fun theCountersCountTheKindsAndTheDeletesWithoutText() {
        helium314.keyboard.agent.BarCalmCounters.resetForTest()
        rig.startField("", restarting = true)
        rig.settle()
        rig.play("Hallo wie" + b + b)
        val total = helium314.keyboard.agent.BarCalmCounters.total
        assertEquals(2, total.deletes.sum())
        assertTrue(total.changes() >= 1, "the first key changed the bar from the tools: " + total.changesTo.toList())
        assertTrue(total.changesTo[helium314.keyboard.agent.BarCalmCounters.Kind.TOOLS.ordinal] == 0, "the tools never came back: " + total.changesTo.toList())
    }

    @Test fun theProtocolSeesTheBar() {
        val rows = protocol("Hallo ")
        assertTrue(rows.isNotEmpty() && rows.all { it.split("|").size == 5 }, rows.toString())
    }
}
