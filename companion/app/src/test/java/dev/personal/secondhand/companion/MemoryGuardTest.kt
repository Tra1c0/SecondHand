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
        assertTrue(t.contains("frei im System: 810 MB, App (RSS): 2900 MB, dma-buf der App: nicht lesbar, freier Auslagerungsspeicher: 6100 MB, 4,2 s nach Beginn"))
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

class DmabufTest {
    private val mb = 1048576L

    @Test fun fdinfoOfADmabufIsRecognisedOtherFdsAreNot() {
        val dma = "pos:\t0\nflags:\t02\nmnt_id:\t14\nino:\t1234\nsize:    \t8388608\ncount:\t2\nexp_name:\tqcom,system\nname:\t<none>\n"
        assertEquals(1234L to 8388608L, DmaBufParse.fdinfo(dma))
        assertNull(DmaBufParse.fdinfo("pos:\t0\nflags:\t0100002\nmnt_id:\t30\nino:\t9\n"))
        assertNull(DmaBufParse.fdinfo("size: 5\n")) // a size without exp_name is no dma-buf
    }

    @Test fun duplicatedFdsOfTheSameBufferCountOnce() {
        val a = "ino:\t1\nsize:\t1000\nexp_name:\tsystem\n"
        val b = "ino:\t1\nsize:\t1000\nexp_name:\tsystem\n"
        val c = "ino:\t2\nsize:\t500\nexp_name:\tsystem\n"
        assertEquals(1500L, DmaBufParse.sumFdinfos(listOf(a, b, c, "pos: 0\n")))
    }

    @Test fun mapsEntriesWithDmabufNamesAreSummed() {
        val maps = listOf(
            "7a00000000-7a40000000 rw-s 00000000 00:0e 4096 /dmabuf:system",
            "7b00000000-7b00100000 rw-s 00000000 00:0e 4097 /memfd:something",
            "7c00000000-7c00200000 rw-s 00000000 00:0e 4098 anon_inode:dmabuf",
        )
        assertEquals(0x40000000L + 0x200000L, DmaBufParse.mapsBytes(maps))
    }

    @Test fun theBrakeFiresBeforeTheHyperOsLimit() {
        assertTrue(MemoryGuard.DMABUF_BRAKE_BYTES < MemoryGuard.HYPEROS_DMABUF_LIMIT_BYTES)
        assertEquals(MemoryGuard.Decision.Continue, MemoryGuard.decide(5_000L * mb, 0, 1_300L * mb))
        assertTrue((MemoryGuard.decide(5_000L * mb, 0, 1_400L * mb) as MemoryGuard.Decision.Abort).reason.contains("dma-buf"))
        assertEquals(MemoryGuard.Decision.Continue, MemoryGuard.decide(5_000L * mb, 0, -1))
    }

    @Test fun theLimitIsTheOneFromTheSystemText() {
        assertEquals(1_572_864L * 1024, MemoryGuard.HYPEROS_DMABUF_LIMIT_BYTES)
        // observed: 3,966,224 kB with 1,742,958,592 bytes of context binaries
        val ratio = 3_966_224L * 1024.0 / 1_742_958_592L
        assertTrue(ratio > 2.2 && ratio < MemoryGuard.DMABUF_FACTOR)
    }

    @Test fun everyRealBundleIsPredictedOverTheLimitEvenTheSmallestOne() {
        val bins06b = 311_328_768L + 435_679_232L
        val bins17b = 622_596_096L + 289_697_792L + 283_385_856L + 547_278_848L
        val bins4b = 778_178_560L + 659_521_536L + 659_537_920L + 1_057_546_240L
        for (b in listOf(bins06b, bins17b, bins4b)) {
            val v = MemoryGuard.fullVerdict(b, 100_000_000_000L)
            assertFalse("bin $b", v.dmabufOk)
            assertTrue(v.memoryOk)
            assertFalse(v.ok)
        }
        assertTrue(MemoryGuard.fullVerdict(bins06b, 100_000_000_000L, Long.MAX_VALUE).ok)
    }

    @Test fun theWatchdogBrakesOnDmabufAndPersistsTheReason() {
        val clock = FakeClock()
        val meter = FakeMeter().apply { dmabuf = 100L * mb }
        val store = MemSampleStore()
        var kills = 0
        val d = LoadWatchdog(meter, store, clock, { kills++ }, intervalMs = 1_000_000)
        d.start("Laden")
        meter.dmabuf = 500L * mb; d.tick()
        assertEquals(0, kills)
        meter.dmabuf = 1_400L * mb; clock.now += 100; d.tick()
        assertEquals(1, kills)
        assertTrue(store.stored!!.selfKill!!.contains("dma-buf"))
        assertEquals(1_400L, store.stored!!.last!!.dmabufMb)
        assertTrue(store.stored!!.reportLines().joinToString().contains("dma-buf der App: 1400 MB"))
        val why = store.stored!!.selfKill!!
        assertTrue(why.startsWith("Selbst beendet: dma-buf der App stieg auf 1400 MB"))
        assertTrue(why.contains("so weit kam das Laden: nach 0,1 s, dma-buf 1400 MB"))
    }

    @Test fun storedRunsOfTheOldFormatStillParse() {
        val r = StoredRun.parse("step=x\nlast=100,3000,200,6000\nmin=3000\nn=2\ntrims=\nselfkill=\nfinished=false\n")!!
        assertEquals(-1L, r.last!!.dmabufMb)
        assertTrue(r.reportLines().joinToString().contains("dma-buf der App: nicht lesbar"))
    }

    @Test fun theSelfKillLineSaysHowFarTheLoadGotRelativeToTheBinaries() {
        val clock = FakeClock()
        val meter = FakeMeter().apply { dmabuf = 100L * mb }
        val store = MemSampleStore()
        val d = LoadWatchdog(meter, store, clock, { }, intervalMs = 1_000_000)
        d.setBinBytes(1_742_958_592L)
        d.start("Laden")
        meter.dmabuf = 1_400L * mb; clock.now += 2500; d.tick()
        val why = store.stored!!.selfKill!!
        assertTrue(why, why.contains("0,84-fache der Rechenprogramme (1662 MB)"))
        assertTrue(why.contains("nach 2,5 s"))
        assertTrue(why.contains("Grenze") || why.contains("HyperOS beendet ab 1536 MB"))
    }

    @Test fun thresholdMarginIsJustifiedByTheRealLimit() {
        val limitMib = MemoryGuard.HYPEROS_DMABUF_LIMIT_BYTES / mb
        val brakeMib = MemoryGuard.DMABUF_BRAKE_BYTES / mb
        assertEquals(1536L, limitMib)
        assertEquals(1350L, brakeMib)
        assertTrue(brakeMib * 100 / limitMib in 85..92)
    }

    @Test fun theUnitOfTheProcFileIsDecidedFromTheTextOrFromTheOtherSums() {
        // "kB" in the text wins
        assertEquals(3_966_224L * 1024, DmaBufParse.procBytes("3966224 kB\n", 0))
        // no unit, the fd/map sums say about 800 MB: 819200 as kB = 800 MiB fits, as bytes = 0.8 MiB does not
        assertEquals(819_200L * 1024, DmaBufParse.procBytes("819200\n", 800L * mb))
        // no unit, the sums say about 800 MB: 838860800 as bytes fits
        assertEquals(838_860_800L, DmaBufParse.procBytes("838860800\n", 800L * mb))
        // nothing to compare with: kilobytes
        assertEquals(1000L * 1024, DmaBufParse.procBytes("1000\n", 0))
        assertNull(DmaBufParse.procBytes("no number", 0))
        assertEquals(42L, DmaBufParse.firstNumber("dmabuf_rss: 42 kB"))
    }

    @Test fun withoutAReadableDmabufValueOnlyTheSmallestModelIsAllowed() {
        val bins06b = 311_328_768L + 435_679_232L
        val bins17b = 622_596_096L + 289_697_792L + 283_385_856L + 547_278_848L
        val small = MemoryGuard.fullVerdict(bins06b, 100_000_000_000L, Long.MAX_VALUE, meterReadable = false)
        assertFalse(small.noMeter)
        assertTrue(small.ok)
        val big = MemoryGuard.fullVerdict(bins17b, 100_000_000_000L, Long.MAX_VALUE, meterReadable = false)
        assertTrue(big.noMeter)
        assertFalse(big.ok)
        assertTrue(MemoryGuard.fullVerdict(bins17b, 100_000_000_000L, Long.MAX_VALUE, meterReadable = true).ok)
    }

    @Test fun procFilesFromARealKernelFixtureParse() {
        // fdinfo of a dma-buf fd on a GKI 6.x kernel (fields as printed by dma_buf_show_fdinfo)
        val fdinfo = "pos:\t0\nflags:\t02000002\nmnt_id:\t23\nino:\t51\nsize:   \t134217728\ncount:\t2\nexp_name:\tqcom,system\nname:\t\n"
        assertEquals(51L to 134217728L, DmaBufParse.fdinfo(fdinfo))
        val maps = listOf(
            "7e00000000-7e08000000 rw-s 00000000 00:0a 51  /dmabuf:qcom,system (deleted)",
            "7f00000000-7f00001000 r--p 00000000 fd:00 99  /system/lib64/libc.so",
        )
        assertEquals(0x08000000L, DmaBufParse.mapsBytes(maps))
    }
}

class InstanceCounterTest {
    @Test fun countsCurrentPeakCreatedAndReleased() {
        val c = InstanceCounter()
        c.onCreated()
        assertEquals(1, c.current)
        c.onReleased()
        c.onCreated()
        c.onCreated() // a second instance at the same time: the peak shows it
        assertEquals(2, c.peak)
        c.onReleased(); c.onReleased(); c.onReleased() // an extra release never goes below zero
        assertEquals(0, c.current)
        assertEquals(3, c.created)
        assertEquals(4, c.released)
        assertTrue(c.summary().contains("höchstens gleichzeitig 2"))
    }

    @Test fun aSequentialLoadAfterReleaseNeverExceedsOne() {
        val c = InstanceCounter()
        repeat(3) { c.onCreated(); c.onReleased() }
        assertEquals(1, c.peak)
    }
}
