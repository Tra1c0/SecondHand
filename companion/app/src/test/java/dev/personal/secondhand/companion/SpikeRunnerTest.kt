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

    init {
        // the default fake store holds the Instruct model, like after a finished pull
        runBlocking { runner.refreshModelPresent() }
        port.calls.clear()
    }

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

    private val instruct = PullPlanner.INSTRUCT_GALAXY
    private val qwen3 = PullPlanner.QWEN3_GALAXY

    @Test fun pullSuccessLogsModelChipsetStorageAndNetwork() = runBlocking {
        port.present.clear()
        port.pullUpdates = listOf(PullUpdate.Progress(100, 1000), PullUpdate.Completed)
        port.present += instruct.modelName // appears after the pull
        val seen = mutableListOf<Pair<Long, Long>>()
        val out = runner.pull(instruct) { d, t -> seen += d to t }
        assertEquals(SpikeRunner.PullOutcome.Success(instruct), out)
        assertEquals(listOf(100L to 1000L), seen)
        assertEquals(instruct.modelName, runner.presentModel)
        val text = logText()
        assertTrue(text.contains("Modell-ID: ai-hub-models/Qwen3-4B-Instruct-2507, Chipset-ID: SM8750-AC"))
        assertTrue(text.contains("Freier Speicher vorher: 90,0 GB, Netz: WLAN"))
    }

    @Test fun chipsetUnavailableIsReportedWithCodeNameAndGivesUp() = runBlocking {
        port.pullUpdates = listOf(PullUpdate.Failed(-100015, "geniex_model_pull failed (rc=-100015)"))
        val out = runner.pull(instruct) { _, _ -> } as SpikeRunner.PullOutcome.Failed
        assertEquals(instruct, out.candidate)
        assertTrue(out.advice.giveUp)
        assertFalse(out.advice.repeatUseful)
        assertTrue(out.error.startsWith("-100015 (COMMON_CHIPSET_UNAVAILABLE)"))
        assertTrue(logText().contains("  SDK-Code: -100015 (COMMON_CHIPSET_UNAVAILABLE laut GenieX-Quelltext)"))
        assertTrue(logText().contains("FEHLER bei Schritt: Modell herunterladen (ai-hub-models/Qwen3-4B-Instruct-2507, Chipset SM8750-AC)"))
    }

    @Test fun afterAFailureTheNextCandidateIsOfferedButNeverPulledOnItsOwn() = runBlocking {
        port.pullUpdates = listOf(PullUpdate.Failed(-100015, "x"))
        val out = runner.pull(instruct) { _, _ -> } as SpikeRunner.PullOutcome.Failed
        val next = PullPlanner.next(out.candidate, setOf(out.candidate.id))
        assertEquals(qwen3, next)
        // exactly one pull request went out, the one that was tapped
        assertEquals(listOf("pull:${instruct.modelName}:SM8750-AC"), port.calls.filter { it.startsWith("pull:") })
    }

    @Test fun attemptsAreNumberedAndEachLogsItsModelAndChipset() = runBlocking {
        port.pullUpdates = listOf(PullUpdate.Failed(-100005, "connection reset"))
        runner.pull(instruct) { _, _ -> }
        runner.pull(qwen3) { _, _ -> }
        val text = logText()
        assertTrue(text.contains("Versuch 1: Modell herunterladen (ai-hub-models/Qwen3-4B-Instruct-2507"))
        assertTrue(text.contains("Versuch 2: Modell herunterladen (ai-hub-models/Qwen3-4B, Chipset SM8750-AC"))
        assertTrue(text.contains("Modell-ID: ai-hub-models/Qwen3-4B, Chipset-ID: SM8750-AC"))
    }

    @Test fun networkTypeAndFreeStorageComeFromTheProbe() = runBlocking {
        val probe = FakeProbe().apply { network = "Mobilfunk"; free = 3L * 1073741824 }
        val r = SpikeRunner(port, probe, clock, tracker, log)
        port.pullUpdates = listOf(PullUpdate.Failed(-100005, "x"))
        r.pull(instruct) { _, _ -> }
        assertTrue(logText().contains("Freier Speicher vorher: 3,0 GB, Netz: Mobilfunk"))
    }

    @Test fun networkErrorAllowsRepeatAndKeepsTheCandidate() = runBlocking {
        port.pullUpdates = listOf(PullUpdate.Failed(-100005, "connection reset by peer"))
        val out = runner.pull(instruct) { _, _ -> } as SpikeRunner.PullOutcome.Failed
        assertFalse(out.advice.giveUp)
        assertTrue(out.advice.repeatUseful)
    }

    @Test fun pullExceptionWithAKnownCodeIsAdvisedByItsCode() = runBlocking {
        port.pullThrows = NpuException(-100015, "chipset not available", "com.geniex.sdk.ModelManagerWrapper\$GenieXModelError")
        val out = runner.pull(instruct) { _, _ -> } as SpikeRunner.PullOutcome.Failed
        assertTrue(out.advice.giveUp)
    }

    @Test fun completedButModelMissingIsAFailureThatAsksToDeleteFirst() = runBlocking {
        port.present.clear()
        val out = runner.pull(qwen3) { _, _ -> } as SpikeRunner.PullOutcome.Failed
        assertTrue(out.advice.deleteFirst)
        assertTrue(logText().contains("findet das Modell nicht"))
    }

    @Test fun refreshFindsTheFirstPresentCandidateModel() = runBlocking {
        port.present.clear()
        assertNull(runner.refreshModelPresent())
        port.present += qwen3.modelName
        assertEquals(qwen3.modelName, runner.refreshModelPresent())
        port.present += instruct.modelName
        assertEquals(instruct.modelName, runner.refreshModelPresent())
    }

    @Test fun testWithoutAModelSaysSo() = runBlocking {
        port.present.clear()
        runner.refreshModelPresent()
        assertEquals(SpikeRunner.TestOutcome.Failed(loadFailed = false), runner.runTest(FixedTests.SENTENCE) { })
        assertTrue(logText().contains("Kein Modell im Speicher"))
    }

    @Test fun hubListLogsTheQwen3EntriesWithTheirChipsets() = runBlocking {
        port.hub = listOf(
            HubEntry("qwen3_4b", listOf("qualcomm-snapdragon-8-elite-for-galaxy", "qualcomm-snapdragon-x-elite")),
            HubEntry("whisper_tiny", listOf("x")),
        )
        runner.listHub()
        val t = logText()
        assertTrue(t.contains("2 Modelle mit NPU-Paket, davon 1 mit 'qwen3'"))
        assertTrue(t.contains("qwen3_4b: qualcomm-snapdragon-8-elite-for-galaxy, qualcomm-snapdragon-x-elite"))
        assertFalse(t.contains("whisper_tiny"))
    }

    @Test fun hubListFailureIsReportedWithClassAndText() = runBlocking {
        port.hubError = NpuException(-100005, "network error", "java.io.IOException")
        runner.listHub()
        assertTrue(logText().contains("FEHLER bei Schritt: Paketliste bei Qualcomm abfragen"))
        assertTrue(logText().contains("  Klasse: java.io.IOException"))
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
        assertEquals(listOf(PullPlanner.INSTRUCT_GALAXY.modelName), port.loadedNames)
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
        r2.refreshModelPresent()
        r2.requestStop()
        assertTrue(slow.calls.contains("stop"))
        // a new test resets the flag: the stop belongs to the run it was tapped in
        val out = r2.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Ok
        assertFalse(out.result.stoppedByUser)
    }

    @Test fun deleteUnloadsFirstThenRemovesEveryCandidateModel() = runBlocking {
        runner.runTest(FixedTests.SENTENCE) { }
        port.deleteResults[PullPlanner.QWEN3_GALAXY.modelName] = -100004 // not in the store: fine
        assertTrue(runner.deleteModels())
        assertEquals(
            listOf("load", "generate:96", "unload", "delete:ai-hub-models/Qwen3-4B-Instruct-2507", "delete:ai-hub-models/Qwen3-4B"),
            port.calls,
        )
        assertFalse(runner.loaded)
        assertNull(runner.presentModel)
        assertTrue(logText().contains("Nichts zu löschen: ai-hub-models/Qwen3-4B"))
    }

    @Test fun deleteWithErrorCodeIsReportedAsNotOk() = runBlocking {
        runner.refreshModelPresent()
        port.deleteResult = 4
        assertFalse(runner.deleteModels())
        assertTrue(logText().contains("SDK-Ergebniscode 4"))
        assertEquals(PullPlanner.INSTRUCT_GALAXY.modelName, runner.presentModel)
    }

    @Test fun noLogLineContainsTheModelTextOfAFailedGeneration() = runBlocking {
        // the model text is shown live on the screen, but a failure report must not repeat the prompt
        port.generateEvents = listOf(GenEvent.Failed(NpuException(null, "x", "E")))
        runner.runTest(FixedTests.PARAGRAPH) { }
        assertFalse(logText().contains("Berger"))
    }
}
