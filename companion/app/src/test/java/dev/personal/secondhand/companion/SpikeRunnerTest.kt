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
    private val diag = FakeDiagnostics()
    private val store = FakeStore()
    private val dl = FakeDownloader(store)
    private val runner = SpikeRunner(port, FakeProbe(), clock, tracker, log, diag, store, dl, dmabufLimitBytes = Long.MAX_VALUE)

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

    private val instruct = PullPlanner.INSTRUCT_V66
    private val qwen3 = PullPlanner.QWEN3_V66
    private val noProgress: (String, Long, Long) -> Unit = { _, _, _ -> }

    @Test fun downloadThenImportReportsEverythingAndCleansTheZip() = runBlocking {
        port.present.clear()
        port.pullUpdates = listOf(PullUpdate.Progress(100, 1000), PullUpdate.Completed)
        dl.progressAt = listOf(5L to 10L)
        val seen = mutableListOf<Triple<String, Long, Long>>()
        val out = runner.download(instruct) { ph, d, t -> seen += Triple(ph, d, t) }
        assertEquals(SpikeRunner.PullOutcome.Success(instruct), out)
        assertEquals(Triple("Laden", 5L, 10L), seen.first())
        assertEquals(Triple("Importieren", 100L, 1000L), seen.last())
        assertEquals(instruct.modelName, runner.presentModel)
        val zip = store.downloadsDir() + "/" + instruct.zipFileName
        assertEquals(listOf("download:${instruct.zipUrl}", "verify:$zip"), dl.calls)
        assertEquals(listOf("import:${instruct.modelName}:$zip"), port.calls.filter { it.startsWith("import:") })
        assertFalse(store.exists(zip)) // the zip is deleted after the import
        val t = logText()
        assertTrue(t.contains("Modell-Name im SDK: local/qwen3-4b-instruct-2507-v066, Ausgabe: v0.66.0"))
        assertTrue(t.contains("Erwartete Größe: 2659737495 Bytes"))
        assertTrue(t.contains("Freier Speicher vorher: 100,0 GB, Netz: WLAN"))
        assertTrue(t.contains("Prüfsummen (CRC32) aller Dateien stimmen"))
        assertTrue(t.contains("Zip-Datei nach dem Import gelöscht"))
    }

    @Test fun anExistingPartialDownloadIsReportedAsResume() = runBlocking {
        port.present.clear()
        store.files[store.downloadsDir() + "/" + instruct.zipFileName + ".part"] = 1_000_000_000L
        runner.download(instruct, noProgress)
        assertTrue(logText().contains("davon schon da: 1000000000 (Fortsetzen)"))
    }

    @Test fun tooLittleSpaceStopsBeforeTheDownloadAndSaysWhatIsNeeded() = runBlocking {
        port.present.clear()
        store.free = 4L * 1073741824
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertTrue(dl.calls.isEmpty())
        assertTrue(out.error.startsWith("zu wenig Speicher"))
        assertTrue(logText().contains("Zu wenig freier Speicher"))
    }

    @Test fun aNotFoundOnTheServerGivesUpAndOffersNoRepeat() = runBlocking {
        port.present.clear()
        dl.downloadError = DownloadException(DownloadException.Kind.HTTP_STATUS, "HTTP-Status 404", httpStatus = 404)
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertTrue(out.advice.giveUp)
        assertFalse(out.advice.repeatUseful)
        assertTrue(logText().contains("DownloadException (HTTP_STATUS), HTTP-Status 404"))
        assertTrue(port.calls.none { it.startsWith("import:") })
    }

    @Test fun aNetworkErrorKeepsTheCandidateAndAllowsARepeat() = runBlocking {
        port.present.clear()
        dl.downloadError = DownloadException(DownloadException.Kind.NETWORK, "SocketTimeoutException: timed out")
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertFalse(out.advice.giveUp)
        assertTrue(out.advice.repeatUseful)
    }

    @Test fun aCancelledDownloadIsNotAnError() = runBlocking {
        port.present.clear()
        dl.downloadError = DownloadException(DownloadException.Kind.CANCELLED, "abgebrochen")
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertTrue(out.advice.cancelled)
        assertTrue(logText().contains("Download abgebrochen"))
        assertFalse(logText().contains("FEHLER bei Schritt"))
    }

    @Test fun aDamagedZipIsDeletedAndNeverImported() = runBlocking {
        port.present.clear()
        dl.verifyError = DownloadException(DownloadException.Kind.ZIP_INVALID, "part2_of_4.bin: Prüfsumme (CRC32) stimmt nicht")
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertTrue(out.advice.repeatUseful)
        assertFalse(store.exists(store.downloadsDir() + "/" + instruct.zipFileName))
        assertTrue(logText().contains("beschädigte Zip-Datei wurde gelöscht"))
        assertTrue(port.calls.none { it.startsWith("import:") })
    }

    @Test fun anImportErrorIsReportedWithCodeNameAndAdvice() = runBlocking {
        port.present.clear()
        port.pullUpdates = listOf(PullUpdate.Failed(-100203, "model invalid"))
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertTrue(out.error.startsWith("-100203 (COMMON_MODEL_INVALID)"))
        assertTrue(out.advice.deleteFirst)
    }

    @Test fun anImportThatLeavesTheModelMissingIsAFailure() = runBlocking {
        port.present.clear()
        port.importAddsPresent = false
        val out = runner.download(qwen3, noProgress) as SpikeRunner.PullOutcome.Failed
        assertTrue(out.advice.deleteFirst)
        assertTrue(logText().contains("findet das Modell nicht"))
    }

    @Test fun aBundleWithTheWrongQairtVersionAfterTheImportIsAFailure() = runBlocking {
        port.present.clear()
        port.bundleQairt = "2.51.0.260929181845"
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertTrue(out.advice.giveUp)
        assertTrue(out.error.contains("2.51.0.260929181845"))
    }

    @Test fun attemptsAreNumbered() = runBlocking {
        port.present.clear()
        dl.downloadError = DownloadException(DownloadException.Kind.NETWORK, "x")
        runner.download(instruct, noProgress)
        runner.download(qwen3, noProgress)
        val t = logText()
        assertTrue(t.contains("Versuch 1: Modell herunterladen (local/qwen3-4b-instruct-2507-v066"))
        assertTrue(t.contains("Versuch 2: Modell herunterladen (local/qwen3-4b-v066"))
    }

    @Test fun networkTypeAndFreeStorageComeFromTheProbeAndTheStore() = runBlocking {
        val probe = FakeProbe().apply { network = "Mobilfunk" }
        val r = SpikeRunner(port, probe, clock, tracker, log, diag, store.also { it.free = 30L * 1073741824 }, dl, dmabufLimitBytes = Long.MAX_VALUE)
        dl.downloadError = DownloadException(DownloadException.Kind.NETWORK, "x")
        r.download(instruct, noProgress)
        assertTrue(logText().contains("Freier Speicher vorher: 30,0 GB, Netz: Mobilfunk"))
    }

    @Test fun afterAFailureTheNextCandidateIsOfferedButNeverDownloadedOnItsOwn() = runBlocking {
        port.present.clear()
        dl.downloadError = DownloadException(DownloadException.Kind.HTTP_STATUS, "HTTP-Status 403", httpStatus = 403)
        val out = runner.download(instruct, noProgress) as SpikeRunner.PullOutcome.Failed
        assertEquals(qwen3, PullPlanner.next(out.candidate, setOf(out.candidate.id)))
        assertEquals(listOf("download:${instruct.zipUrl}"), dl.calls) // only the tapped one
    }

    @Test fun refreshPrefersTheNewestCandidateOverTheLegacyBundle() = runBlocking {
        port.present.clear()
        assertNull(runner.refreshModelPresent())
        port.present += "ai-hub-models/Qwen3-4B-Instruct-2507" // the bundle of app 0.2/0.3 (release v0.67.0)
        assertEquals("ai-hub-models/Qwen3-4B-Instruct-2507", runner.refreshModelPresent())
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
        assertEquals(listOf("load", "generate:96"), port.calls.filterNot { it.startsWith("inspect") })
        assertEquals(listOf(PullPlanner.INSTRUCT_V66.modelName), port.loadedNames)
        assertTrue(logText().contains("Antwort = erwarteter Satz: JA"))
        assertTrue(runner.loaded)
    }

    @Test fun secondTestReusesTheLoadedModel() = runBlocking {
        runner.runTest(FixedTests.SENTENCE) { }
        val out = runner.runTest(FixedTests.PARAGRAPH) { } as SpikeRunner.TestOutcome.Ok
        assertNull(out.result.loadMs)
        assertEquals(listOf("load", "generate:96", "generate:192"), port.calls.filterNot { it.startsWith("inspect") })
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
        val r2 = SpikeRunner(slow, FakeProbe(), clock, tracker, log, dmabufLimitBytes = Long.MAX_VALUE)
        slow.tokens = listOf("a ", "b ")
        r2.refreshModelPresent()
        r2.requestStop()
        assertTrue(slow.calls.contains("stop"))
        // a new test resets the flag: the stop belongs to the run it was tapped in
        val out = r2.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Ok
        assertFalse(out.result.stoppedByUser)
    }

    private fun sdkLeavesFiles(vararg dirs: String) {
        dirs.forEach { store.files[store.modelsRoot() + "/" + it + "/part1_of_4.bin"] = 778_182_656L }
    }

    @Test fun deleteUnloadsFirstThenRemovesEveryModelAndChecks() = runBlocking {
        runner.runTest(FixedTests.SENTENCE) { }
        port.deleteResults[PullPlanner.QWEN3_V66.modelName] = -100004 // not in the store: fine
        port.present.clear()
        assertTrue(runner.deleteModels())
        val deletes = port.calls.filterNot { it.startsWith("inspect") }
        assertTrue(deletes.take(3) == listOf("load", "generate:96", "unload"))
        assertTrue(deletes.any { it == "delete:local/qwen3-4b-instruct-2507-v066" })
        assertTrue(deletes.any { it == "delete:ai-hub-models/Qwen3-4B" })
        assertFalse(runner.loaded)
        assertNull(runner.presentModel)
        assertTrue(logText().contains("Gelöscht und geprüft: Der Modellspeicher des SDK ist leer."))
    }

    @Test fun whenTheSdkLeavesTheFilesTheAppDeletesThemItselfAndSaysSo() = runBlocking {
        // seen on the phone: remove() returned 0, yet the bundle was still there
        sdkLeavesFiles("qualcomm/Qwen3-4B-Instruct-2507", "qualcomm/Qwen3-4B")
        store.files[store.downloadsDir() + "/x.zip"] = 5_000_000_000L
        store.onDeleted = { path -> if (path.contains("Qwen3-4B")) port.present.clear() }
        assertTrue(runner.deleteModels())
        assertTrue(store.listDirs(store.modelsRoot()).isEmpty())
        assertFalse(store.exists(store.downloadsDir()))
        val t = logText()
        assertTrue(t.contains("Das SDK hat 2 Ordner nicht entfernt: qualcomm/Qwen3-4B, qualcomm/Qwen3-4B-Instruct-2507"))
        assertTrue(t.contains("entfernt: qualcomm/Qwen3-4B (0,7 GB)"))
        assertTrue(t.contains("Heruntergeladene Zip-Dateien: entfernt"))
        assertTrue(t.contains("Gelöscht und geprüft"))
    }

    @Test fun deleteNeverSaysDeletedWhenSomethingIsStillThere() = runBlocking {
        sdkLeavesFiles("qualcomm/Qwen3-4B")
        store.undeletable += store.modelsRoot() + "/qualcomm/Qwen3-4B"
        assertFalse(runner.deleteModels())
        val t = logText()
        assertTrue(t.contains("NICHT entfernt: qualcomm/Qwen3-4B"))
        assertTrue(t.contains("NICHT vollständig gelöscht. Noch da:"))
        assertFalse(t.contains("Gelöscht und geprüft"))
        assertTrue(t.contains("Ordner: qualcomm/Qwen3-4B"))
    }

    @Test fun deleteStillReportsABundleTheSdkStillKnows() = runBlocking {
        // the fake SDK keeps knowing the Instruct bundle although no folder is left
        assertFalse(runner.deleteModels())
        assertTrue(logText().contains("SDK: local/qwen3-4b-instruct-2507-v066"))
        assertEquals(PullPlanner.INSTRUCT_V66.modelName, runner.presentModel)
    }

    @Test fun noLogLineContainsTheModelTextOfAFailedGeneration() = runBlocking {
        // the model text is shown live on the screen, but a failure report must not repeat the prompt
        port.generateEvents = listOf(GenEvent.Failed(NpuException(null, "x", "E")))
        runner.runTest(FixedTests.PARAGRAPH) { }
        assertFalse(logText().contains("Berger"))
    }

    // ---- stage 80c: diagnostics and release pinning ----

    private val fixedTextFragment = "Spaziergang"

    @Test fun diagnosticsRunBeforeTheLoadOnTheExistingFilesAndTheLoadIsRetried() = runBlocking {
        port.bundleQairt = "2.51.0.260929181845"
        port.loadError = NpuException(null, "Llm create failed: Model loading failed", "java.lang.RuntimeException")
        runner.runTest(FixedTests.SENTENCE) { }
        val t = logText()
        assertTrue(t.contains("Befunde vor dem Laden"))
        assertTrue(t.contains("part1_of_4.bin  778182656"))
        assertTrue(t.contains("Paket gebaut mit QAIRT: 2.51.0.260929181845"))
        assertTrue(t.contains("Laufzeit des SDK (libQnnHtp.so): v2.50.40.260831140417"))
        assertTrue(t.contains("Das Paket ist NEUER als die Laufzeit"))
        assertTrue(t.contains("libcdsprpc.so (Systembibliothek, nach Name): OK"))
        assertTrue(t.contains("Arbeitsspeicher direkt vor dem Laden"))
        // the order: facts first, then the load, then the process facts
        assertEquals(listOf("env", "nativeLoad", "mark", "process"), diag.calls.filter { it != "unload" })
        assertFalse(port.calls.contains("delete:x"))
        assertEquals(PullPlanner.INSTRUCT_V66.modelName, runner.presentModel) // the files stay usable, nothing is deleted
    }

    @Test fun aLoadFailureWithANewerBundleSaysSoToTheScreen() = runBlocking {
        port.bundleQairt = "2.51.0.260929181845"
        port.loadError = NpuException(null, "Model loading failed", "java.lang.RuntimeException")
        val out = runner.runTest(FixedTests.SENTENCE) { }
        assertEquals(SpikeRunner.TestOutcome.Failed(loadFailed = true, bundleNewerThanRuntime = true), out)
    }

    @Test fun aLoadFailureWithAMatchingBundleDoesNotBlameTheVersion() = runBlocking {
        port.bundleQairt = "2.50.0.260828221209"
        port.loadError = NpuException(null, "Model loading failed", "java.lang.RuntimeException")
        val out = runner.runTest(FixedTests.SENTENCE) { }
        assertEquals(SpikeRunner.TestOutcome.Failed(loadFailed = true, bundleNewerThanRuntime = false), out)
        assertTrue(logText().contains("Paket nicht neuer als die Laufzeit"))
    }

    @Test fun theRuntimeLogIsFilteredAndCutAtTheLoadMarker() = runBlocking {
        port.loadError = NpuException(null, "Model loading failed", "java.lang.RuntimeException")
        diag.log = listOf(
            "01-01 00:00:00.000 1 1 E QnnHtp: before the marker",
            "01-01 00:00:01.000 1 1 I SomeApp: unrelated",
        )
        port.onLoad = {
            diag.log = diag.log + listOf(
                "01-01 00:00:03.000 1 1 E libgeniex: [ERROR] \"Device Creation failure\"",
                "01-01 00:00:03.100 1 1 I chatty: nothing about the chip",
                "01-01 00:00:03.200 1 1 D GenieXSdk: echoed $fixedTextFragment from a prompt",
            )
        }
        runner.runTest(FixedTests.SENTENCE) { }
        val t = logText()
        assertTrue(t.contains("Protokoll der Laufzeit"))
        assertTrue(t.contains("Device Creation failure"))
        assertFalse(t.contains("before the marker")) // written before the load: cut at the marker
        assertFalse(t.contains("nothing about the chip"))
        assertFalse(t.contains(fixedTextFragment + " from a prompt"))
    }

    @Test fun anEmptyRuntimeLogIsSaidSo() = runBlocking {
        port.loadError = NpuException(null, "Model loading failed", "java.lang.RuntimeException")
        runner.runTest(FixedTests.SENTENCE) { }
        assertTrue(logText().contains("keine passenden Zeilen"))
    }

    @Test fun diagnosticsAreNotRepeatedOnceTheModelIsLoaded() = runBlocking {
        runner.runTest(FixedTests.SENTENCE) { }
        val first = port.calls.count { it.startsWith("inspect") }
        runner.runTest(FixedTests.PARAGRAPH) { }
        assertEquals(first, port.calls.count { it.startsWith("inspect") })
    }

    // ---- stage 80d: metadata format and compatibility metadata ----

    private val metaPath = "/data/geniex/models/x/metadata.json"

    @Test fun aLoadFailureOfABundleInTheNewFormatIsFlaggedAsTooNew() = runBlocking {
        port.newFormat = true
        port.loadError = NpuException(null, "Llm create failed: Model loading failed", "java.lang.RuntimeException")
        val out = runner.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Failed
        assertTrue(out.metadataFormatTooNew)
        assertTrue(logText().contains("model_files ist eine LISTE"))
        assertTrue(logText().contains("H6"))
    }

    @Test fun anObjectFormatBundleIsNotBlamedForTheFormat() = runBlocking {
        port.loadError = NpuException(null, "Model loading failed", "java.lang.RuntimeException")
        val out = runner.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Failed
        assertFalse(out.metadataFormatTooNew)
        assertTrue(logText().contains("model_files ist ein Objekt"))
    }

    @Test fun compatMetadataBacksUpTheOriginalWritesTheOldFormatAndChecksIt() = runBlocking {
        port.newFormat = true
        store.texts[metaPath] = "ORIGINAL"
        port.metaProvider = { store.texts[metaPath]?.takeIf { it != "ORIGINAL" } }
        runner.refreshModelPresent()
        assertEquals(true, runner.presentFacts?.modelFilesIsList)
        // the fake serves the generated new-format metadata until the app wrote its own
        port.metaProvider = { store.texts[metaPath]?.takeIf { it != "ORIGINAL" } }
        assertTrue(runner.applyCompatMetadata())
        val backup = "/data/files/companion-backup/local_qwen3-4b-instruct-2507-v066/metadata.json.original"
        assertTrue(store.texts.containsKey(backup))
        val written = MiniJson.parse(store.texts[metaPath]!!)
        assertTrue(MiniJson.path(written, "model_files") is Map<*, *>)
        assertEquals(false, runner.presentFacts?.modelFilesIsList)
        assertTrue(logText().contains("Original gesichert unter $backup"))
        assertTrue(logText().contains("Format Objekt (ok)"))
    }

    @Test fun compatMetadataDoesNothingWhenTheBackupFails() = runBlocking {
        port.newFormat = true
        store.copyOk = false
        runner.refreshModelPresent()
        assertFalse(runner.applyCompatMetadata())
        assertFalse(store.texts.containsKey(metaPath))
        assertTrue(logText().contains("Sicherung des Originals fehlgeschlagen"))
    }

    @Test fun compatMetadataIsNotNeededForTheOldFormat() = runBlocking {
        runner.refreshModelPresent()
        assertFalse(runner.applyCompatMetadata())
        assertTrue(logText().contains("Nicht nötig"))
        assertTrue(store.texts.isEmpty())
    }

    @Test fun anAlreadyPatchedBundleIsMarkedInTheDiagnostics() = runBlocking {
        store.texts["/data/files/companion-backup/local_qwen3-4b-instruct-2507-v066/metadata.json.original"] = "ORIGINAL"
        runner.runTest(FixedTests.SENTENCE) { }
        assertTrue(logText().contains("Die Metadaten dieses Pakets wurden von der App angepasst"))
    }

    // ---- stage 80e: memory guard, watchdog hooks, model selection ----

    private val order = mutableListOf<String>()
    private fun runnerWithWatchdog(probe: FakeProbe = FakeProbe()): Pair<SpikeRunner, FakeWatchdog> {
        val wd = FakeWatchdog(order)
        port.order = order
        return SpikeRunner(port, probe, clock, tracker, log, diag, store, dl, wd, Long.MAX_VALUE) to wd
    }

    @Test fun theGuardRefusesALoadThatDoesNotFitAndLoadsNothing() = runBlocking {
        val (r, wd) = runnerWithWatchdog(FakeProbe().apply { memAvail = 1_000_000_000L })
        r.refreshModelPresent()
        val out = r.runTest(FixedTests.SENTENCE) { }
        assertTrue(out is SpikeRunner.TestOutcome.Blocked)
        assertTrue(port.calls.none { it == "load" })
        assertEquals(0, wd.started)
        assertTrue(logText().contains("Speicherwächter vor dem Laden"))
        assertTrue(logText().contains("NICHT GELADEN"))
        assertFalse(r.loaded)
    }

    @Test fun theOwnerCanLoadAnywayAndTheWatchdogStaysOn() = runBlocking {
        val (r, wd) = runnerWithWatchdog(FakeProbe().apply { memAvail = 1_000_000_000L })
        r.refreshModelPresent()
        val out = r.runTest(FixedTests.SENTENCE, ignoreMemoryGuard = true) { }
        assertTrue(out is SpikeRunner.TestOutcome.Ok)
        assertEquals(1, wd.started)
        assertEquals(1, wd.stopped)
        assertTrue(logText().contains("Auf ausdrücklichen Wunsch trotzdem geladen"))
    }

    @Test fun theWatchdogRunsAroundTheLoadAndStopsAlsoOnAFailure() = runBlocking {
        val (r, wd) = runnerWithWatchdog()
        r.refreshModelPresent()
        port.loadError = NpuException(null, "Model loading failed", "java.lang.RuntimeException")
        r.runTest(FixedTests.SENTENCE) { }
        assertEquals(listOf("wd-start", "load", "wd-stop"), order)
        assertEquals(1, wd.stopped)
        assertTrue(logText().contains("Speicherwächter während des Ladens: fake"))
    }

    @Test fun enoughMemoryLoadsWithoutAnyDialogOutcome() = runBlocking {
        val (r, _) = runnerWithWatchdog(FakeProbe().apply { memAvail = 12L * 1073741824 })
        r.refreshModelPresent()
        assertTrue(r.runTest(FixedTests.SENTENCE) { } is SpikeRunner.TestOutcome.Ok)
        assertTrue(logText().contains("frei sind jetzt 12,0 GB"))
    }

    @Test fun selectingAnotherPresentBundleSwitchesTheTestModelAndUnloads() = runBlocking {
        port.present += PullPlanner.QWEN3_1_7B_V66.modelName
        runner.refreshModelPresent()
        // candidates come in their order: the 1.7B bundle is preferred over the 4B one
        assertEquals(PullPlanner.QWEN3_1_7B_V66.modelName, runner.presentModel)
        assertEquals(2, runner.presentBundles.size)
        runner.runTest(FixedTests.SENTENCE) { }
        assertTrue(runner.loaded)
        assertTrue(runner.selectModel(PullPlanner.INSTRUCT_V66.modelName))
        assertFalse(runner.loaded)
        assertEquals(PullPlanner.INSTRUCT_V66.modelName, runner.presentModel)
        runner.refreshModelPresent() // the choice survives a refresh
        assertEquals(PullPlanner.INSTRUCT_V66.modelName, runner.presentModel)
        assertFalse(runner.selectModel("local/not-there"))
    }

    @Test fun theBundleSizesComeFromTheBinFiles() = runBlocking {
        runner.refreshModelPresent()
        assertEquals(778182656L + 659685376L, runner.presentBundles.single().binBytes)
    }

    // ---- stage 80f: dma-buf rule and the run file ----

    @Test fun theDmabufRuleBlocksEveryRealModelBecauseOfTheHyperOsLimit() = runBlocking {
        // production default limit; the fake bundle has 1.44 GB of context binaries, 2.4x is 3.5 GB > 1.5 GB
        val r = SpikeRunner(port, FakeProbe(), clock, tracker, log, diag, store, dl)
        r.refreshModelPresent()
        val out = r.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Blocked
        assertFalse(out.verdict.dmabufOk)
        assertTrue(out.verdict.memoryOk)
        assertTrue(logText().contains("HyperOS würde die App zwangsbeenden"))
        assertTrue(logText().contains("Regel 2: Chip-Speicher (dma-buf) vorhergesagt"))
        assertTrue(port.calls.none { it == "load" })
    }

    @Test fun aMeasurementRunLoadsAnywayWithTheBrakeAndSaysSo() = runBlocking {
        val r = SpikeRunner(port, FakeProbe(), clock, tracker, log, diag, store, dl)
        r.refreshModelPresent()
        val out = r.runTest(FixedTests.SENTENCE, ignoreMemoryGuard = true) { }
        assertTrue(out is SpikeRunner.TestOutcome.Ok)
        assertTrue(logText().contains("Messversuch"))
    }

    @Test fun everyReportLineGoesToTheRunFileAndSamplesOnlyThere() {
        val lines = mutableListOf<String>()
        log.sink = { lines += it }
        log.add("auf dem Bildschirm")
        log.addAll(listOf("a", "b"))
        log.addQuiet("Messung 1,0 s: frei 3000 MB")
        assertEquals(listOf("auf dem Bildschirm", "a", "b", "Messung 1,0 s: frei 3000 MB"), lines)
        assertFalse(log.text().contains("Messung"))
    }

    // ---- stage 80f: phases and instances ----

    @Test fun theReportShowsTheMemoryAtEveryPhaseOfTheRun() = runBlocking {
        runner.init()
        runner.runTest(FixedTests.SENTENCE) { }
        runner.unload()
        val t = logText()
        for (label in listOf("nach dem Start des SDK", "direkt vor dem Laden", "nach dem Laden", "Beginn der Erzeugung", "Ende der Erzeugung", "nach dem Entladen (Freigabe)")) {
            assertTrue(label, t.contains("Speicher $label: "))
        }
        assertTrue(t.contains("Modell-Instanzen: 0"))
    }

    @Test fun anExistingInstanceIsReleasedBeforeANewLoad() = runBlocking {
        port.instances = 1
        runner.runTest(FixedTests.SENTENCE) { }
        assertTrue(logText().contains("Es besteht schon eine Modell-Instanz (1): Sie wird vor dem neuen Laden freigegeben"))
        val order = port.calls.filter { it == "unload" || it == "load" }
        assertEquals(listOf("unload", "load"), order)
    }

    @Test fun withoutAReadableDmabufTheBigModelIsNotLoadedAndTheSmallOneIs() = runBlocking {
        val wd = object : Watchdog {
            override fun start(step: String) {}
            override fun stop() = emptyList<String>()
            override fun canMeasureDmabuf() = false
        }
        val big = SpikeRunner(port, FakeProbe(), clock, tracker, log, diag, store, dl, wd, Long.MAX_VALUE)
        port.present.clear(); port.present += PullPlanner.QWEN3_1_7B_V66.modelName
        port.binBytesOverride = 1_742_958_592L
        big.refreshModelPresent()
        val out = big.runTest(FixedTests.SENTENCE) { } as SpikeRunner.TestOutcome.Blocked
        assertTrue(out.verdict.noMeter)
        assertTrue(logText().contains("nicht lesbar"))
        assertTrue(port.calls.none { it == "load" })
        port.binBytesOverride = 747_008_000L
        assertTrue(big.runTest(FixedTests.SENTENCE) { } is SpikeRunner.TestOutcome.Ok)
    }
}
