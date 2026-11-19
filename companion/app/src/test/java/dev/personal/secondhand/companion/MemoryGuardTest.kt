package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGuardTest {
    private val mb = 1048576L
    private val gb = 1073741824L

    // the context binaries of the three bundles (v0.66.0), summed from the zip directories
    private val bins4b = 778_178_560L + 659_521_536L + 659_537_920L + 1_057_546_240L
    private val bins17b = 622_596_096L + 289_697_792L + 283_385_856L + 547_278_848L
    private val bins06b = 311_328_768L + 435_679_232L

    @Test fun theRuleIsTwiceTheBinariesPlusHalfAGibibyte() {
        val v = MemoryGuard.verdict(bins4b, 10 * gb)
        assertEquals((bins4b * 2.0).toLong() + 512 * mb, v.neededBytes)
        assertTrue(v.ok)
    }

    @Test fun theObservedKillWouldHaveBeenRefused() {
        // 4B bundle (3.2 GB) with about 4.2 GB available ended in the low-memory killer on the phone
        val v = MemoryGuard.verdict(bins4b, 4_200_000_000L)
        assertFalse(v.ok)
        assertTrue(v.neededBytes > 6L * gb)
    }

    @Test fun theSmallBundlesPassWithAboutFourGigabytesAvailable() {
        assertTrue(MemoryGuard.verdict(bins17b, 4_200_000_000L).ok)
        assertTrue(MemoryGuard.verdict(bins06b, 2_500_000_000L).ok)
        assertFalse(MemoryGuard.verdict(bins17b, 3_000_000_000L).ok)
    }

    @Test fun theRuleTextNamesTheNumbers() {
        val t = MemoryGuard.verdict(bins4b, 4_200_000_000L).ruleText()
        assertTrue(t.contains("2-Fache"))
        assertTrue(t.contains("512 MB"))
        assertTrue(t.contains("frei sind jetzt 3,9 GB"))
    }

    @Test fun decideAbortsUnderTheFloorAndOnCriticalTrim() {
        assertEquals(MemoryGuard.Decision.Continue, MemoryGuard.decide(3 * gb, 0))
        assertEquals(MemoryGuard.Decision.Continue, MemoryGuard.decide(900 * mb, 10)) // RUNNING_LOW alone is no abort
        assertTrue(MemoryGuard.decide(700 * mb, 0) is MemoryGuard.Decision.Abort)
        assertTrue((MemoryGuard.decide(3 * gb, 15) as MemoryGuard.Decision.Abort).reason.contains("TRIM_MEMORY_RUNNING_CRITICAL"))
        assertEquals(MemoryGuard.Decision.Continue, MemoryGuard.decide(-1, 0)) // unreadable is no abort
    }

    @Test fun storedRunSurvivesTheTextRoundTripAndReportsWhatHappened() {
        val run = StoredRun("Modell laden", MemSample(4200, 810, 2900, 6100), 790, 21, listOf(TrimEvent(10, 3000), TrimEvent(15, 4100)), "freier Speicher fiel auf 790 MB", false)
        val back = StoredRun.parse(run.serialize())!!
        assertEquals(run, back)
        val t = back.reportLines().joinToString("\n")
        assertTrue(t.contains("frei im System: 810 MB, App (RSS): 2900 MB, freier Auslagerungsspeicher: 6100 MB, 4,2 s nach Beginn"))
        assertTrue(t.contains("niedrigster Wert frei: 790 MB; 21 Messungen"))
        assertTrue(t.contains("TRIM_MEMORY_RUNNING_LOW nach 3,0 s, TRIM_MEMORY_RUNNING_CRITICAL nach 4,1 s"))
        assertTrue(t.contains("Notbremse"))
    }

    @Test fun parseRefusesGarbageAndHandlesEmptyFields() {
        assertNull(StoredRun.parse(null))
        assertNull(StoredRun.parse("nonsense"))
        val r = StoredRun.parse("step=x\nlast=\nmin=-1\nn=0\ntrims=\nselfkill=\nfinished=true\n")!!
        assertNull(r.last)
        assertTrue(r.trims.isEmpty())
        assertTrue(r.finished)
        assertNull(r.selfKill)
    }

    // ---- the watchdog ----

    private val clock = FakeClock()
    private val meter = FakeMeter()
    private val store = MemSampleStore()
    private var kills = 0
    private fun dog() = LoadWatchdog(meter, store, clock, { kills++ }, intervalMs = 1_000_000) // the thread sleeps; the tests call tick()

    @Test fun everySampleIsPersistedSynchronouslyAndTheMinimumIsKept() {
        val d = dog()
        d.start("Laden")
        clock.now += 200; meter.avail = 3_000L * mb; d.tick()
        clock.now += 200; meter.avail = 2_400L * mb; meter.rss = 1_500L * mb; d.tick()
        clock.now += 200; meter.avail = 2_900L * mb; d.tick()
        val run = store.stored!!
        assertEquals(4, run.samples) // the sample at start plus three
        assertEquals(2_400L, run.minAvailMb)
        assertEquals(MemSample(600, 2_900, 1_500, 6_000), run.last)
        assertFalse(run.finished)
        assertTrue(store.saves.size >= 4)
        d.stop()
    }

    @Test fun belowTheFloorTheAppEndsItselfOnceAndThePersistedRunSaysWhy() {
        val d = dog()
        d.start("Laden")
        clock.now += 200; meter.avail = 600L * mb; d.tick()
        d.tick() // after the abort nothing happens any more
        assertEquals(1, kills)
        val run = store.stored!!
        assertTrue(run.selfKill!!.contains("600 MB"))
        assertFalse(run.finished)
        assertTrue(run.reportLines().joinToString().contains("Notbremse"))
    }

    @Test fun aCriticalTrimDuringTheLoadEndsTheApp() {
        val d = dog()
        d.start("Laden")
        d.onTrim(10)
        assertEquals(0, kills)
        d.onTrim(15)
        assertEquals(1, kills)
        assertEquals(listOf(TrimEvent(10, 0), TrimEvent(15, 0)), store.stored!!.trims)
    }

    @Test fun trimEventsOutsideALoadAreIgnored() {
        val d = dog()
        d.onTrim(15)
        assertEquals(0, kills)
        assertNull(store.stored)
        d.start("Laden")
        d.stop()
        d.onTrim(15)
        assertEquals(0, kills)
    }

    @Test fun aNormalEndMarksTheRunFinishedSoNextStartReportsNothing() {
        val d = dog()
        d.start("Laden")
        clock.now += 200; meter.avail = 2_000L * mb; d.tick()
        val lines = d.stop()
        assertTrue(store.stored!!.finished)
        assertTrue(lines.single().contains("2 Messungen, niedrigster freier Speicher 2000 MB"))
        d.tick()
        assertEquals(2, store.stored!!.samples) // stopped: no more samples
    }

    @Test fun aNewStartForgetsTheOldRun() {
        val d = dog()
        d.start("Erster")
        meter.avail = 100L * mb; d.tick()
        assertEquals(1, kills)
        meter.avail = 4_000L * mb
        d.start("Zweiter")
        assertEquals("Zweiter", store.stored!!.step)
        assertNull(store.stored!!.selfKill)
        d.stop()
    }
}
