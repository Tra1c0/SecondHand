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

    private fun state(): String = (if (barState.show == BarState.Show.TOOLS) "T" else "W") + (if (barState.hasContent) "+" else "-")

    private val b = TypingRig.BACKSPACE_MARK
    private val uiThread = android.os.Looper.getMainLooper().thread

    private fun protocol(script: String, slowConnection: Boolean = false): List<String> {
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
                rig.tapFast(code)
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

    /** How often the bar changes between tools and content over the whole run: a count of the changes of the first letter of the trace. */
    private fun switches(rows: List<String>) = rows.sumOf { row ->
        val trace = row.split("|")[3].split(">")
        trace.zipWithNext().count { (a, c) -> a[0] != c[0] }
    }

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

    /** No next-word predictions (the stub dictionary knows none): the case that shows the tools after every space. */
    @Test fun todayWithoutPredictions() {
        rig.predictions = emptyList()
        val rows = protocol(sentence + tenBackspaces)
        println("switches without predictions: " + switches(rows))
        check("no-predictions", rows)
    }

    /** The dictionary offers next words after a space and at the start of a line: the bar should then stay on the words. */
    @Test fun todayWithPredictions() {
        rig.predictions = listOf("wie", "und", "ich")
        val rows = protocol(sentence + tenBackspaces)
        println("switches with predictions: " + switches(rows))
        check("with-predictions", rows)
    }

    /** An app with a slow editor connection (hasSlowInputConnection): every backspace asks for the neutral strip (InputLogic line 1731). */
    @Test fun todayWithSlowConnection() {
        rig.predictions = emptyList()
        val rows = protocol(sentence + tenBackspaces, slowConnection = true)
        println("switches with slow connection: " + switches(rows))
        check("slow-connection", rows)
    }

    @Test fun theProtocolSeesTheBar() {
        val rows = protocol("Hallo ")
        assertTrue(rows.isNotEmpty() && rows.all { it.split("|").size == 5 }, rows.toString())
    }
}
