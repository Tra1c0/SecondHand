package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class RunLogTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_800_000_000_000L
    private fun store(keep: Int = 3, max: Long = 200_000) = RunLogStore(File(tmp.root, "logs"), keep, max) { now }

    @Test fun theFileStartsWithTheNoUserTextStatement() {
        val s = store()
        val f = s.start()
        f.append("erste Zeile")
        val text = f.file.readText()
        assertTrue(text.lines()[0].startsWith("# SecondHand Begleit-Spike: Bericht eines Laufs, gestartet 20"))
        assertTrue(text.lines()[1].contains("Dieser Bericht enthält keine Texte des Nutzers"))
        assertTrue(text.contains("] erste Zeile"))
    }

    @Test fun everyLineIsOnDiskWithoutClosingTheFile() {
        // what a killed process leaves behind: the file is read while the writer is still open and was never closed
        val f = store().start()
        f.append("Schritt 2: läuft")
        f.append("Messung 1,0 s: frei 3000 MB")
        val onDisk = File(f.file.path).readText()
        assertTrue(onDisk.contains("Schritt 2: läuft"))
        assertTrue(onDisk.contains("Messung 1,0 s: frei 3000 MB"))
    }

    @Test fun theNextStartFindsThePreviousRunAndNotItsOwnFile() {
        val first = store().start()
        first.append("Bericht des ersten Laufs")
        now += 60_000
        val s2 = store()
        assertTrue(s2.previousRunText()!!.contains("Bericht des ersten Laufs")) // read before start()
        val second = s2.start()
        second.append("Bericht des zweiten Laufs")
        now += 60_000
        val s3 = store()
        val prev = s3.previousRunText()!!
        assertTrue(prev.contains("zweiten Laufs"))
        assertFalse(prev.contains("ersten Laufs"))
    }

    @Test fun onlyTheLastThreeRunsAreKept() {
        repeat(6) { i ->
            now += 1000
            store().start().append("Lauf $i")
        }
        val files = store().runFiles()
        assertEquals(3, files.size) // the 6th start is not counted yet: 3 kept before it, then it is created -> at most keep files
        assertTrue(files.first().readText().contains("Lauf 5"))
    }

    @Test fun aRunFileIsCappedAndSaysSo() {
        val f = store(max = 2_000).start()
        repeat(200) { f.append("Zeile mit etwas Text $it") }
        val text = f.file.readText()
        assertTrue(text.toByteArray().size < 2_500)
        assertTrue(text.contains("Datei voll, weitere Zeilen werden nicht mehr gespeichert"))
        assertEquals(1, text.lines().count { it.contains("Datei voll") })
    }

    @Test fun multiLineTextGetsOneTimestampPerLine() {
        val f = store().start()
        f.append("a\nb")
        val body = f.file.readLines().drop(2)
        assertEquals(2, body.size)
        assertTrue(body.all { it.startsWith("[") })
    }

    @Test fun theCopiedReportHoldsThePreviousRunThenTheCurrentOneClearlySeparated() {
        val c = RunLogStore.combined("# alt\n[10:00:00.000] Schritt ...", "2026-11-19 21:00:00", "AKTUELL", "2026-11-19 22:00:00")
        val lines = c.lines()
        assertEquals("===== BERICHT DES VORHERIGEN LAUFS (gestartet 2026-11-19 21:00:00, aus der Datei) =====", lines[0])
        assertTrue(c.indexOf("Schritt ...") < c.indexOf("===== AKTUELLER LAUF (gestartet 2026-11-19 22:00:00) ====="))
        assertTrue(c.endsWith("AKTUELL"))
        assertTrue(RunLogStore.combined(null, null, "x", "t").contains("Kein Bericht eines vorherigen Laufs vorhanden"))
        assertEquals("2026-11-19 21:00:00".length, RunLogStore.startOf("run-1805000000000.txt")!!.length)
        assertNull(RunLogStore.startOf("other.txt"))
    }

    // ---- the case of the fifth device run: the process is gone in the middle of a model load ----

    @Test fun aLoadThatNeverReturnsLeavesTheStepTheSamplesAndTheBrakeOnDiskForTheNextStart() {
        val logs = store()
        val runFile = logs.start()
        val clock = FakeClock()
        val meter = FakeMeter().apply { avail = 3_000L * 1048576; dmabuf = 100L * 1048576 }
        val sampleStore = MemSampleStore()
        val report = ReportLog().apply { sink = { runFile.append(it) } }
        val watchdog = LoadWatchdog(meter, sampleStore, clock, { /* the kill */ }, intervalMs = 20, onSampleLine = { report.addQuiet(it) })
        val port = FakePort(clock)
        val started = CountDownLatch(1)
        val hang = CountDownLatch(1)
        port.onLoad = { started.countDown(); hang.await(30, TimeUnit.SECONDS) } // create() never returns: the process dies here
        val tracker = FakeTracker()
        val st = FakeStore()
        val runner = SpikeRunner(port, FakeProbe(), clock, tracker, report, FakeDiagnostics(), st, FakeDownloader(st), watchdog, Long.MAX_VALUE)
        val worker = Thread { runBlocking { runner.refreshModelPresent(); runner.runTest(FixedTests.SENTENCE) { } } }
        worker.isDaemon = true
        worker.start()
        assertTrue(started.await(10, TimeUnit.SECONDS))
        Thread.sleep(150) // a few samples while the "load" hangs
        meter.dmabuf = 400L * 1048576
        Thread.sleep(150)
        // === the process is killed here: nothing is closed or flushed by the app ===
        val next = store().also { now += 5000 }
        val previous = next.previousRunText()!!
        assertTrue(previous.contains("Schritt 2: Test 2: festen Satz korrigieren ..."))
        assertTrue(previous.contains("Befunde vor dem Laden"))
        assertTrue(previous.contains("Speicherwächter vor dem Laden"))
        assertTrue("samples are in the file", previous.contains("Messung "))
        assertTrue(previous.contains("dma-buf"))
        // and the persisted run of the watchdog says what the last sample was and that the step never finished
        val stored = sampleStore.load()!!
        assertFalse(stored.finished)
        assertTrue(stored.samples >= 3)
        assertTrue(stored.last!!.dmabufMb >= 100)
        assertTrue(stored.reportLines().joinToString().contains("dma-buf der App"))
        hang.countDown()
        worker.join(5000)
    }

    @Test fun theBlockOfTheLastMeasurementIsShownWheneverAStepMarkerWasLeftEvenIfTheSamplingFinished() {
        // the 0.5 rule hid it unless the run was unfinished: with a marker left it must always appear
        val finished = StoredRun("Laden", MemSample(500, 3000, 100, 6000, 200), 2900, 5, emptyList(), null, true)
        assertTrue(StartupReport.lastRunLines(finished, interruptedStep = "Laden").isNotEmpty())
        assertTrue(StartupReport.lastRunLines(finished, interruptedStep = null).isEmpty())
        val unfinished = finished.copy(finished = false)
        assertTrue(StartupReport.lastRunLines(unfinished, interruptedStep = null).isNotEmpty())
        assertTrue(StartupReport.lastRunLines(null, interruptedStep = "Laden").single().contains("Keine gespeicherte Speichermessung"))
    }
}
