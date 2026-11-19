package dev.personal.secondhand.companion

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeRunnerTest {
    private val clock = FakeClock()
    private val port = FakePort(clock)
    private val tracker = FakeTracker()
    private val log = ReportLog()
    private val runner = SpikeRunner(port, FakeProbe(), clock, tracker, log)

    private fun logText() = log.text()

    @Test fun initOnAnUnsupportedDeviceDoesNotCrashAndSaysSo() = runBlocking {
        port.initError = NpuException(null, "libnpu_jni.so not found", "java.lang.UnsatisfiedLinkError")
        assertNull(runner.init())
        assertFalse(runner.supported)
        assertEquals("Auf diesem Gerät nicht unterstützt: java.lang.UnsatisfiedLinkError", runner.initFailureLine)
        assertTrue(logText().contains("FEHLER bei Schritt: GenieX-SDK starten"))
        assertTrue(tracker.events.any { it.startsWith("failed:GenieX-SDK starten:java.lang.UnsatisfiedLinkError") })
    }

    @Test fun anyThrowableAtInitIsCaught() = runBlocking {
        port.initError = IllegalStateException("boom")
        assertNull(runner.init())
        assertEquals("Auf diesem Gerät nicht unterstützt: java.lang.IllegalStateException", runner.initFailureLine)
    }

    @Test fun initOkMarksTheRunnerAsSupported() = runBlocking {
        assertNotNull(runner.init())
        assertTrue(runner.supported)
        assertNull(runner.initFailureLine)
        assertTrue(logText().contains("QAIRT-Plugin: 2.50"))
    }

    @Test fun pullSuccessReportsTheChipset() = runBlocking {
        port.pullUpdates = listOf(PullUpdate.Progress(100, 1000), PullUpdate.Completed)
        val seen = mutableListOf<Pair<Long, Long>>()
        val out = runner.pull("SM8750") { d, t -> seen += d to t }
        assertEquals(SpikeRunner.PullOutcome.Success("SM8750"), out)
        assertEquals(listOf(100L to 1000L), seen)
        assertEquals(listOf("SM8750"), port.pulledChipsets)
    }

    @Test fun pullNotFoundOnSm8750OffersTheGalaxyBundleButDoesNotPullIt() = runBlocking {
        port.pullUpdates = listOf(PullUpdate.Failed(5, "Requested chipset not available for this model"))
        val out = runner.pull("SM8750") { _, _ -> }
        assertEquals(SpikeRunner.PullOutcome.Failed("SM8750", offerGalaxyBundle = true), out)
        assertEquals(listOf("SM8750"), port.pulledChipsets) // only the documented id was requested
        assertTrue(logText().contains("  SDK-Code: 5"))
        assertTrue(logText().contains("  SDK-Text: Requested chipset not available for this model"))
        assertTrue(logText().contains("FEHLER bei Schritt: Modell herunterladen (Chipset SM8750)"))
    }

    @Test fun pullExceptionWithNotFoundTextAlsoOffersTheGalaxyBundle() = runBlocking {
        port.pullThrows = NpuException(2, "model not found", "com.geniex.sdk.ModelManagerWrapper\$GenieXModelError")
        val out = runner.pull("SM8750") { _, _ -> }
        assertEquals(SpikeRunner.PullOutcome.Failed("SM8750", true), out)
    }

    @Test fun networkFailureOffersNothing() = runBlocking {
        port.pullUpdates = listOf(PullUpdate.Failed(9, "connection reset by peer"))
        val out = runner.pull("SM8750") { _, _ -> }
        assertEquals(SpikeRunner.PullOutcome.Failed("SM8750", false), out)
    }

    @Test fun completedButModelMissingIsAFailure() = runBlocking {
        port.present = false
        val out = runner.pull("SM8750-AC") { _, _ -> }
        assertEquals(SpikeRunner.PullOutcome.Failed("SM8750-AC", false), out)
        assertTrue(logText().contains("findet das Modell nicht"))
    }

    @Test fun sentenceTestComputesLoadTimeTokensAndVerdict() = runBlocking {
        val shown = mutableListOf<String>()
        val out = runner.runTest(FixedTests.SENTENCE) { shown += it } as SpikeRunner.TestOutcome.Ok
        val r = out.result
        assertEquals(4000L, r.loadMs)
        assertEquals(300L, r.appFirstTokenMs)
        assertEquals(11L, r.appTokenEvents)
        // 11 tokens per SDK, first at +300, last at +300+10*50: 10 tokens / 0.5 s
        assertEquals(20.0, r.appDecodeSpeed!!, 1e-9)
        assertTrue(Metrics.sameAsExpected(r.answer, FixedTests.SENTENCE.expected!!))
        assertEquals(11, shown.size)
        assertTrue(r.memAfter.pssBytes > r.memBefore.pssBytes)
        assertTrue(r.tempAfterC!! > r.tempBeforeC!!)
        assertEquals(listOf("load", "generate:96"), port.calls)
        assertTrue(logText().contains("Antwort = erwarteter Satz: JA"))
        assertTrue(runner.loaded)
    }

    @Test fun secondTestReusesTheLoadedModel() = runBlocking {
        runner.runTest(FixedTests.SENTENCE) { }
        val out = runner.runTest(FixedTests.PARAGRAPH) { } as SpikeRunner.TestOutcome.Ok
        assertNull(out.result.loadMs)
        assertEquals(listOf("load", "generate:96", "generate:192"), port.calls)
    }

    @Test fun loadFailureIsReportedWithStepAndMarksTheLoadAsFailed() = runBlocking {
        port.loadError = NpuException(-3, "PARAM_NOT_SUPPORTED", "com.geniex.sdk.ModelManagerWrapper\$GenieXModelError")
        val out = runner.runTest(FixedTests.SENTENCE) { }
        assertEquals(SpikeRunner.TestOutcome.Failed(loadFailed = true), out)
        assertFalse(runner.loaded)
        assertTrue(logText().contains("FEHLER bei Schritt: Modell auf der NPU laden"))
        assertTrue(logText().contains("  SDK-Code: -3"))
        assertFalse(port.calls.any { it.startsWith("generate") })
    }

    @Test fun aBundleThatIsNotQairtIsDetectedAsFallbackAndNotCalledNpu() = runBlocking {
        port.loadInfo = LoadInfo("llama_cpp", null)
        val out = runner.runTest(FixedTests.SENTENCE) { }
        assertEquals(SpikeRunner.TestOutcome.Failed(loadFailed = true), out)
        assertTrue(logText().contains("Laufzeit laut Modellspeicher: llama_cpp (erwartet: qairt)"))
        assertTrue(port.calls.contains("unload"))
        assertFalse(runner.loaded)
    }

    @Test fun generationErrorIsReportedWithItsStepAndKeepsTheModelLoaded() = runBlocking {
        port.generateEvents = listOf(GenEvent.Token("Wir"), GenEvent.Failed(NpuException(null, "stream broke", "java.lang.IllegalStateException")))
        val out = runner.runTest(FixedTests.SENTENCE) { }
        assertEquals(SpikeRunner.TestOutcome.Failed(loadFailed = false), out)
        assertTrue(logText().contains("FEHLER bei Schritt: Antwort erzeugen (Test 2: festen Satz korrigieren)"))
        assertTrue(runner.loaded)
    }

    @Test fun answerWithoutCompletionStillGivesTheAppMeasuredNumbers() = runBlocking {
        port.profile = null
        val out = runner.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Ok
        assertNull(out.result.sdk)
        assertNotNull(out.result.appDecodeSpeed)
        assertTrue(logText().contains("SDK-Messwerte: keine"))
    }

    @Test fun stopIsPassedToThePortAndFlagsTheResult() = runBlocking {
        val slow = FakePort(clock)
        val r2 = SpikeRunner(slow, FakeProbe(), clock, tracker, log)
        slow.tokens = listOf("a ", "b ")
        r2.requestStop()
        assertTrue(slow.calls.contains("stop"))
        // a new test resets the flag: the stop belongs to the run it was tapped in
        val out = r2.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Ok
        assertFalse(out.result.stoppedByUser)
    }

    @Test fun deleteUnloadsFirstAndReportsTheCode() = runBlocking {
        runner.runTest(FixedTests.SENTENCE) { }
        assertTrue(runner.deleteModel())
        assertEquals(listOf("load", "generate:96", "unload", "delete"), port.calls)
        assertFalse(runner.loaded)
    }

    @Test fun deleteWithErrorCodeIsReportedAsNotOk() = runBlocking {
        port.deleteResult = 4
        assertFalse(runner.deleteModel())
        assertTrue(logText().contains("SDK-Ergebniscode 4"))
    }

    @Test fun noLogLineContainsTheModelTextOfAFailedGeneration() = runBlocking {
        // the model text is shown live on the screen, but a failure report must not repeat the prompt
        port.generateEvents = listOf(GenEvent.Failed(NpuException(null, "x", "E")))
        runner.runTest(FixedTests.PARAGRAPH) { }
        assertFalse(logText().contains("Berger"))
    }
}
