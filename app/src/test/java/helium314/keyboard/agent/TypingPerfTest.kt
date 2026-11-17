// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentDiagnoseScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage 64a: the recorder of what the fork's own code costs per key press ([TypingPerf]) and its card on
 * the Diagnose page. Time comes from a fake clock; nothing here is measured on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h2000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class TypingPerfTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private var now = 0L
    private val ms = 1_000_000L

    @Before fun setUp() {
        now = 0L
        TypingPerf.nanoSource = TypingPerf.NanoSource { now }
        TypingPerf.timedThread = Thread.currentThread()
        TypingPerf.reset()
        TypingPerf.resetFailures()
    }

    @After fun tearDown() {
        TypingPerf.nanoSource = TypingPerf.NanoSource { System.nanoTime() }
        TypingPerf.timedThread = null
        TypingPerf.reset()
        TypingPerf.resetFailures()
    }

    /** One key press: the fork's hooks take [millis] in total and cause the given requests. */
    private fun press(millis: Long, app: Int = 0, system: Int = 0, hook: TypingPerf.Hook = TypingPerf.Hook.STRIP_CHIPS) {
        TypingPerf.keyPress()
        TypingPerf.measure(hook) { now += millis * ms; repeat(app) { TypingPerf.app() }; repeat(system) { TypingPerf.system() } }
    }

    @Test fun aPressIsClosedByTheNextOneAndCountsItsRequests() {
        press(3, app = 2, system = 1)
        assertEquals(0, TypingPerf.snapshot().presses, "the press in progress is not counted yet")
        press(5, app = 4, system = 0)
        press(1)
        val s = TypingPerf.snapshot()
        assertEquals(2, s.presses)
        assertEquals(3.0, s.appAverage) // (2 + 4) / 2
        assertEquals(4, s.appMax)
        assertEquals(0.5, s.systemAverage)
        assertEquals(1, s.systemMax)
        assertEquals(5 * ms, s.maxNanos)
    }

    @Test fun requestsFromOtherThreadsAreCountedToo() {
        TypingPerf.keyPress()
        Thread { TypingPerf.app(); TypingPerf.app(); TypingPerf.system() }.apply { start(); join() }
        TypingPerf.keyPress()
        val s = TypingPerf.snapshot()
        assertEquals(2, s.appMax)
        assertEquals(1, s.systemMax)
    }

    @Test fun medianPercentileAndMaximum() {
        for (i in 1..100L) press(i)
        press(0)
        val s = TypingPerf.snapshot()
        assertEquals(100, s.presses)
        assertEquals(50 * ms, s.medianNanos)
        assertEquals(95 * ms, s.p95Nanos)
        assertEquals(100 * ms, s.maxNanos)
    }

    @Test fun theRingKeepsOnlyTheLastPresses() {
        press(900) // falls out below
        repeat(TypingPerf.PRESSES) { press(1) }
        press(1)
        val s = TypingPerf.snapshot()
        assertEquals(TypingPerf.PRESSES, s.presses, "bounded")
        assertEquals(1 * ms, s.maxNanos, "the old slow press is gone from the ring")
        // the slow run list keeps it (it is one of the ten slowest)
        assertEquals(900 * ms, s.slowest.first().nanos)
    }

    @Test fun nestedHooksOnlyCountOnceForThePressButAppearInTheSlowList() {
        TypingPerf.keyPress()
        TypingPerf.measure(TypingPerf.Hook.STRIP_CHIPS) {
            now += 2 * ms
            TypingPerf.measure(TypingPerf.Hook.FIELD_CHIPS) { now += 3 * ms; TypingPerf.app() }
            now += 1 * ms
        }
        TypingPerf.keyPress()
        val s = TypingPerf.snapshot()
        assertEquals(6 * ms, s.maxNanos, "the outer hook includes the inner one, which is not added again")
        assertEquals(listOf(TypingPerf.Hook.STRIP_CHIPS, TypingPerf.Hook.FIELD_CHIPS), s.slowest.map { it.hook })
        assertEquals(listOf(1, 1), s.slowest.map { it.requests })
    }

    @Test fun theTenSlowestRunsAreKeptSorted() {
        for (i in 1..25L) press(i, app = (i % 3).toInt())
        press(0)
        val s = TypingPerf.snapshot()
        assertEquals(TypingPerf.SLOWEST, s.slowest.size)
        assertEquals((16..25L).reversed().toList(), s.slowest.map { it.nanos / ms })
        assertEquals(1, s.slowest.last().requests) // 16 % 3
    }

    @Test fun hooksOffTheMainThreadAreNotTimed() {
        TypingPerf.keyPress()
        Thread { TypingPerf.measure(TypingPerf.Hook.LEARNING) { now += 50 * ms } }.apply { start(); join() }
        TypingPerf.keyPress()
        val s = TypingPerf.snapshot()
        assertEquals(0L, s.maxNanos)
        assertTrue(s.slowest.isEmpty())
    }

    @Test fun resetForgetsEverythingAndRestartsTheClock() {
        var wall = 1_000L
        TypingPerf.wallClock = { wall }
        try {
            press(5, app = 1); press(5)
            wall = 5_000L
            TypingPerf.reset()
            val s = TypingPerf.snapshot()
            assertEquals(0, s.presses)
            assertTrue(s.slowest.isEmpty())
            assertEquals(5_000L, s.sinceMillis)
            // requests made before the reset are not counted for the next press
            TypingPerf.app()
            TypingPerf.reset()
            press(1); press(1)
            assertEquals(0, TypingPerf.snapshot().appMax)
        } finally { TypingPerf.wallClock = { System.currentTimeMillis() } }
    }

    @Test fun aThrowingBlockStillEndsTheMeasurement() {
        TypingPerf.keyPress()
        runCatching { TypingPerf.measure(TypingPerf.Hook.COMMAND) { now += 4 * ms; error("x") } }
        TypingPerf.measure(TypingPerf.Hook.COMMAND) { now += 1 * ms }
        TypingPerf.keyPress()
        assertEquals(5 * ms, TypingPerf.snapshot().maxNanos, "both runs were outermost: the depth is back to zero")
    }

    @Test fun theApiTakesNoTextOnlyFixedNames() {
        val withText = TypingPerf::class.java.declaredMethods.filter { m ->
            java.lang.reflect.Modifier.isPublic(m.modifiers) && m.parameterTypes.any { it == String::class.java || CharSequence::class.java.isAssignableFrom(it) }
        }.map { it.name }.distinct()
        // the only call that takes a string is the name of a built-in command, with the exception (its class is kept, never its message)
        assertEquals(listOf("commandFailed"), withText)
        assertEquals(emptyList(), TypingPerf.Snapshot::class.java.declaredFields.filter { it.type == String::class.java || it.type == CharSequence::class.java })
        assertEquals(emptyList(), TypingPerf.Run::class.java.declaredFields.filter { it.type == String::class.java })
    }

    @Test fun aFailureKeepsTheCommandNameAndTheClassNeverTheMessage() {
        TypingPerf.commandFailed("math", IllegalStateException("GEHEIMER-TEXT"))
        TypingPerf.commandFailed("math", IllegalStateException("noch ein GEHEIMER-TEXT"))
        TypingPerf.commandFailed("snip", NumberFormatException("GEHEIMER-TEXT"))
        val failures = TypingPerf.failures()
        assertEquals(listOf("math" to 2, "snip" to 1), failures.map { it.name to it.count })
        assertEquals("IllegalStateException", failures.first().errorClass)
        val text = Diagnose.text(app, listOf(Diagnose.typing(app, TypingPerf.snapshot(), 0L)))
        assertTrue("Befehl math übersprungen: 2-mal, IllegalStateException" in text, text)
        assertFalse("GEHEIMER-TEXT" in text)
    }

    @Test fun theCardReadsWithoutExplanation() {
        val empty = Diagnose.typing(app, TypingPerf.snapshot(), 0L)
        assertEquals(Diagnose.Part.TYPING, empty.part)
        assertEquals("Guter Wert", empty.rows.first().label)
        assertTrue("16 ms" in empty.rows.first().value && "60 Hz" in empty.rows.first().value)
        assertTrue(empty.rows.any { it.value.startsWith("noch keine") })

        TypingPerf.wallClock = { 0L }
        TypingPerf.reset()
        try {
            press(2, app = 1, system = 2, hook = TypingPerf.Hook.FIELD_CHIPS)
            press(8, app = 3, system = 0, hook = TypingPerf.Hook.COMMAND)
            press(0)
            val card = Diagnose.typing(app, TypingPerf.snapshot(), 5 * 60_000L + 1)
            val rows = card.rows.associate { it.label to it.value }
            assertEquals("2, seit 5 Min.", rows["Gemessene Tastendrücke"])
            assertEquals("2,0 ms", rows["Zeit je Taste, Median"]!!.replace('.', ','))
            assertEquals("8,0 ms", rows["Zeit je Taste, Maximum"]!!.replace('.', ','))
            assertEquals("Ø 2,0, max 3", rows["Anfragen an die App je Taste"]!!.replace('.', ','))
            assertEquals("Ø 1,0, max 2", rows["Anfragen an Android je Taste"]!!.replace('.', ','))
            assertEquals("8,0 ms, 3 Anfragen", rows["Langsamster Lauf 1: Befehlserkennung"]!!.replace('.', ','))
            assertEquals("2,0 ms, 3 Anfragen", rows["Langsamster Lauf 2: Feld-Vorschlags-Chips"]!!.replace('.', ','))
            val text = Diagnose.text(app, listOf(card))
            assertTrue("Zeit je Taste, 95. Perzentil: " in text)
        } finally { TypingPerf.wallClock = { System.currentTimeMillis() } }
    }

    @Test fun thePageShowsTheCardAndTheResetButtonClearsIt() {
        app.prefs().edit().clear().commit()
        press(4, app = 1); press(4)
        compose.setContent { Theme(true) { AgentDiagnoseScreen {} } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Paare", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Tippen (Kosten", substring = true)).performScrollTo().assertExists()
        compose.onNode(hasText("Guter Wert")).performScrollTo().assertExists()
        compose.onNode(hasText("Zeit je Taste, Median")).performScrollTo().assertExists()
        compose.onNode(hasText("Gemessene Tastendrücke")).performScrollTo().assertExists()
        compose.onNodeWithTag("agent_diagnose_typing_reset").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, TypingPerf.snapshot().presses)
        compose.onNode(hasText("noch keine –", substring = true)).performScrollTo().assertExists()
        app.prefs().edit().clear().commit()
    }
}
