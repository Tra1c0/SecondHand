// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.ShadowBinaryDictionaryUtils
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
import kotlin.test.assertTrue

/**
 * Stage 84 "Symbole ohne Verzögerung": how much work one key causes, counted (not timed), letters against symbols.
 * The table of [theWorkTable] is the measurement the stage is based on; the other tests are in this class too
 * once there is something to prove (see the stage in docs/VALIDATION.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class, ShadowProximityInfo::class,
    ShadowBinaryDictionaryUtils::class, ShadowLocaleManagerCompat::class])
class SymbolLatencyTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val rig = TypingRig(latinIME)

    @Before fun setUp() { rig.setUp() }
    @After fun tearDown() { ShadowInputMethodService.reset(); LockState.resetForTest(); ClipVault.clear() }

    private class Case(val name: String, val prepare: TypingRig.() -> Unit, val key: Char, val fast: Boolean = false)

    private fun cases(pill: Boolean) = if (!pill) listOf(
        Case("a letter in a word      (hel+l)", { startField(""); type("hel") }, 'l'),
        Case("b letter starts a word  (hello_+w)", { startField(""); type("hello "); }, 'w'),
        Case("c space after a word    (hello+_)", { startField(""); type("hello") }, ' '),
        Case("d . after a word        (hello+.)", { startField(""); type("hello") }, '.'),
        Case("e @ after a word        (hello+@)", { startField(""); type("hello") }, '@'),
        Case("e - after a word        (hello+-)", { startField(""); type("hello") }, '-'),
        Case("e ( after a word        (hello+()", { startField(""); type("hello") }, '('),
        Case("e @ on the symbol page  (hello+@)", { startField(""); type("hello"); symbolPage() }, '@'),
        Case("e - on the symbol page  (hello+-)", { startField(""); type("hello"); symbolPage() }, '-'),
        Case("e , after a word        (hello+,)", { startField(""); type("hello") }, ','),
        Case("e ! after a word        (hello+!)", { startField(""); type("hello") }, '!'),
        Case("f . after a space       (hello_+.)", { startField(""); type("hello ") }, '.'),
        Case("f @ after a space       (hello_+@)", { startField(""); type("hello ") }, '@'),
        Case("f - after a space       (hello_+-)", { startField(""); type("hello ") }, '-'),
        Case("g second symbol         (hello-+-)", { startField(""); type("hello-") }, '-'),
        Case("g second symbol         (hello@+-)", { startField(""); type("hello@") }, '-'),
        Case("fast c space, update pending", { startField(""); type("hell"); tapFast('o'.code) }, ' ', fast = true),
        Case("fast d . , update pending", { startField(""); type("hell"); tapFast('o'.code) }, '.', fast = true),
        Case("fast e - , update pending", { startField(""); type("hell"); tapFast('o'.code) }, '-', fast = true),
        Case("autocorrect: teh+_", { startField(""); type("teh") }, ' '),
        Case("autocorrect: teh+.", { startField(""); type("teh") }, '.'),
        Case("i toolbar open: - after a space", { startField(""); type("hello "); toolbarVisible(true) }, '-'),
        Case("i toolbar open: - after a word", { startField(""); type("hello"); toolbarVisible(true) }, '-'),
    ) else listOf(
        Case("h pill: - after a space", { startField(""); type("hello ") }, '-'),
        Case("h pill: . after a word", { startField(""); type("hello") }, '.'),
        Case("h pill: letter in a word", { startField(""); type("hel") }, 'l'),
        Case("h pill: space after a word", { startField(""); type("hello") }, ' '),
        Case("h pill: second symbol", { startField(""); type("hello -") }, '-'),
    )

    private fun row(c: Case, t: TypingRig.Timeline) = String.format(
        "%-36s reads%2d writes%2d wait%2d | neutral%2d words%2d ext%2d chips%2d clip%2d/%d | before%2d inBatch%2d after%2d down%2d later%2d | lookups%2d",
        c.name, t.reads, t.writes, t.suggestWaits, t.stripNeutral, t.stripWords, t.stripExternal, t.chipEvaluations,
        t.clipboardService, t.clipViews, t.beforeEdit, t.insideBatchAfterEdit, t.afterBatch, t.onDown, t.later, t.lookups)

    @Test fun theWorkTable() {
        val out = StringBuilder()
        out.appendLine("work of one key (UI thread): reads/writes = calls into the editor; wait = main thread waits for the suggestion thread;")
        out.appendLine("neutral/words/ext = strip rebuilds; chips = chip sources evaluated; clip = clipboard service calls / pill views built;")
        out.appendLine("before = work before the character reaches the editor, inBatch = work after it but before endBatchEdit, after = after it;")
        out.appendLine("down = key down, later = the delayed suggestion update; lookups = dictionary look-ups (suggestion thread)")
        for (c in cases(false)) {
            rig.setUp()
            c.prepare(rig)
            val t = rig.measureKey(c.key.code, c.fast)
            out.appendLine(row(c, t))
            out.appendLine("      up: " + t.names("up"))
            if (t.later > 0) out.appendLine("      later: " + t.names("later"))
        }
        for (c in cases(true)) {
            rig.setUp(withClipboardPill = true)
            rig.clip("some copied text")
            c.prepare(rig)
            val t = rig.measureKey(c.key.code, c.fast)
            out.appendLine(row(c, t))
            out.appendLine("      up: " + t.names("up"))
        }
        println(out)
        File("build/latency84-work-table.txt").apply { parentFile?.mkdirs() }.writeText(out.toString())
    }

    /** Which symbols the keyboard treats as part of a word and which end it, and what each costs in the batch (English, after a word). */
    @Test fun theSymbolSurvey() {
        val out = StringBuilder("symbol after \"hello\": kind (word = stays in the composing word, separator = ends it), reads, work in the batch after the character, work before it\n")
        for (c in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~€£¥§°".toList()) {
            rig.startField(""); rig.type("hello")
            val t = rig.measureKey(c.code)
            val kind = if (rig.composing.endsWith(c)) "word     " else "separator"
            out.appendLine(String.format("%s  %s reads%2d inBatch%2d before%2d later%2d", c, kind, t.reads, t.insideBatchAfterEdit, t.beforeEdit, t.later))
        }
        println(out)
        File("build/latency84-symbols.txt").apply { parentFile?.mkdirs() }.writeText(out.toString())
    }

    /** For the report only, no assertion: the median wall time of the main thread's part, per case (the editor here is instant). */
    @Test fun theWallClockProbe() {
        val out = StringBuilder("median wall time (microseconds) of key up / of the delayed update, 200 runs after 30 warm-up runs; Robolectric, no phone\n")
        class Probe(val name: String, val field: String, val key: Char, val pill: Boolean = false, val toolbar: Boolean = false, val symbols: Boolean = false)
        val probes = listOf(
            Probe("a letter in a word (hel+l)", "hel", 'l'),
            Probe("b letter starts a word", "hello ", 'w'),
            Probe("c space after a word", "hello", ' '),
            Probe("d . after a word", "hello", '.'),
            Probe("e @ after a word", "hello", '@'),
            Probe("e - after a word", "hello", '-'),
            Probe("e - on the symbol page", "hello", '-', symbols = true),
            Probe("f - after a space", "hello ", '-'),
            Probe("g second symbol", "hello-", '-'),
            Probe("h pill: - after a space", "hello ", '-', pill = true),
            Probe("i toolbar open: - after a space", "hello ", '-', toolbar = true),
        )
        for (p in probes) {
            rig.setUp(withClipboardPill = p.pill)
            if (p.pill) rig.clip("some copied text")
            val up = ArrayList<Long>(); val later = ArrayList<Long>()
            for (run in 0 until 230) {
                rig.startField(p.field, restarting = run > 0)
                if (p.symbols) rig.symbolPage()
                if (p.toolbar) rig.toolbarVisible(true)
                rig.idle()
                val code = p.key.code
                rig.down(code)
                val t0 = System.nanoTime()
                rig.up(code)
                val t1 = System.nanoTime()
                rig.idle()
                val t2 = System.nanoTime()
                if (run >= 30) { up += t1 - t0; later += t2 - t1 }
            }
            up.sort(); later.sort()
            out.appendLine(String.format("%-34s up %6d us   delayed %6d us", p.name, up[up.size / 2] / 1000, later[later.size / 2] / 1000))
        }
        println(out)
        File("build/latency84-wall-clock.txt").apply { parentFile?.mkdirs() }.writeText(out.toString())
    }

    private fun after(prefix: String, key: Char): TypingRig.Timeline { rig.startField(""); rig.type(prefix); return rig.measureKey(key.code) }

    @Test fun aSeparatorsStripWorkRunsAfterTheBatchEditEnds() {
        // the app shows nothing new before the keyboard ends the batch edit, so the character has to be out of the batch first
        for (prefix in listOf("hello", "hello ")) for (c in ".,!(") {
            val t = after(prefix, c)
            assertEquals(0, t.beforeEdit, "$prefix$c: nothing before the character\n${t.names()}")
            assertEquals(0, t.insideBatchAfterEdit, "$prefix$c: no strip work inside the batch\n${t.names()}")
            assertEquals(1, t.stripNeutral, "$prefix$c: the strip is still made, once\n${t.names()}")
            assertEquals(1, t.stripWords, "$prefix$c\n${t.names()}")
            assertTrue(t.afterBatch > 0, "$prefix$c: ... after the batch\n${t.names()}")
        }
    }

    @Test fun theNeutralStripAsksEachChipSourceOnce() {
        val t = after("hello", '.')
        for (source in listOf("CHIPS_COMMAND", "CHIPS_SYMBOL", "CHIPS_SELECTION", "CHIPS_CLIPBOARD", "CHIPS_UPDATE"))
            assertEquals(1, t.count(source), "$source\n${t.names()}")
        assertEquals(5, t.chipEvaluations, t.names())
    }

    @Test fun aSymbolThatStartsAWordDoesItsReadsOutsideTheBatch() {
        for (c in "-@1#_") {
            val t = after("hello ", c)
            assertEquals(0, t.readsInsideBatchAfterEdit, "hello $c\n${t.names()}")
        }
        // and nothing is read less than before: the reads moved, the same ones are asked
        val t = after("hello ", '-')
        assertEquals(3, t.reads, t.names())
    }

    @Test fun theRigTypesLikeAFinger() {
        rig.type("hello")
        assertEquals("hello", rig.text)
        rig.tap(' '.code)
        assertEquals("hello ", rig.text)
        rig.type("teh ")
        assertEquals("hello the ", rig.text, "the stub dictionary corrects teh")
    }
}
