package dev.personal.secondhand.companion

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

// The spike's flow: start the SDK, pull, run the two fixed tests, unload, delete (stage 80).
// Talks only to NpuPort/Probe/Clock/StepTracker, so a fake of them drives the unit tests.
// Every failure is written to the ReportLog as class, SDK code and SDK text plus the failed step.
class SpikeRunner(
    private val port: NpuPort,
    private val probe: Probe,
    private val clock: Clock,
    private val tracker: StepTracker,
    private val log: ReportLog,
    private val diag: Diagnostics = NoDiagnostics,
    private val store: StoreFiles = NoStore,
    private val downloader: ZipDownloader = NoDownloader,
    private val watchdog: Watchdog = NoWatchdog,
) {
    sealed interface PullOutcome {
        data class Success(val candidate: Candidate) : PullOutcome

        /** [error] is the short error text of the attempt (code and SDK text), [advice] what the screen should offer. */
        data class Failed(val candidate: Candidate, val error: String, val advice: PullPlanner.Advice) : PullOutcome
    }

    sealed interface TestOutcome {
        data class Ok(val result: TestResult) : TestOutcome

        /**
         * [loadFailed]: the failure was while loading the model. [bundleNewerThanRuntime]: the bundle was built with a
         * newer QAIRT than the runtime in this app, so an older release of the bundle is the next thing to try.
         */
        data class Failed(
            val loadFailed: Boolean,
            val bundleNewerThanRuntime: Boolean = false,
            /** The bundle's metadata.json has `model_files` as a list: SDK 0.10.0 cannot read it (the real cause seen on the phone). */
            val metadataFormatTooNew: Boolean = false,
        ) : TestOutcome

        /** The memory guard refused the load (nothing was loaded): [verdict] says what was needed and what is free. */
        data class Blocked(val verdict: MemoryGuard.Verdict, val model: String) : TestOutcome
    }

    /** A bundle in the SDK's store with what its files say: the version and the size of the context binaries. */
    data class PresentBundle(val name: String, val facts: BundleFacts?, val binBytes: Long)

    @Volatile
    var supported: Boolean = false
        private set

    /** Set when the SDK could not start: the line the screen shows as the banner. */
    @Volatile
    var initFailureLine: String? = null
        private set

    @Volatile
    var loaded: Boolean = false
        private set

    /** The store name of the bundle the tests use: the first of [PullPlanner.ALL_MODEL_NAMES] that is in the SDK's store, or null. */
    @Volatile
    var presentModel: String? = null
        private set

    /** Every bundle of this app in the SDK's store (candidate names first), and the one the tests use ([presentModel]). */
    @Volatile
    var presentBundles: List<PresentBundle> = emptyList()
        private set

    @Volatile
    private var selected: String? = null

    /** What the files of the present bundle say about it (null: unreadable or no bundle). */
    @Volatile
    var presentFacts: BundleFacts? = null
        private set

    /** The QAIRT version compiled into the SDK's runtime, as read before the last load (null: not read). */
    @Volatile
    var runtimeQairt: String? = null
        private set

    @Volatile
    private var stopRequested = false

    private var attempts = 0

    private companion object {
        const val MAX_LOG_LINES = 150
    }

    suspend fun init(): InitInfo? {
        val step = "GenieX-SDK starten"
        tracker.begin(step)
        return try {
            val info = port.init()
            supported = true
            log.add("GenieX-SDK gestartet. SDK-Version: ${info.sdkVersion ?: "nicht gemeldet"}, " +
                "QAIRT-Plugin: ${info.qairtPluginVersion?.ifBlank { null } ?: "nicht gemeldet"}")
            log.add("Chip-Kennung laut SDK (lokal gelesen): ${info.detectedChipset?.ifBlank { null } ?: "nicht gemeldet"}")
            if (info.qairtPluginVersion.isNullOrBlank()) {
                log.add("Warnung: Das QAIRT-Plugin (NPU) meldet keine Version. Möglicherweise ist es nicht registriert.")
            }
            info.qairtRuntimePath?.takeIf { it.isNotBlank() }?.let { log.add("QAIRT-Laufzeitpfad: $it") }
            tracker.end(step)
            info
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            supported = false
            initFailureLine = ErrorText.unsupportedDeviceLine(t)
            fail(step, t)
            null
        }
    }

    /** Looks for the complete bundles of this app in the SDK's store; returns the name of the one the tests use. */
    suspend fun refreshModelPresent(): String? {
        try {
            val names = PullPlanner.ALL_MODEL_NAMES.filter { port.isModelPresent(it) }
            val bundles = names.map { n ->
                val inspection = try { port.inspect(n) } catch (e: CancellationException) { throw e } catch (t: Throwable) { null }
                val facts = inspection?.let { BundleFacts.extract(it.texts["metadata.json"], it.texts["htp_backend_ext_config.json"]) }
                PresentBundle(n, facts, inspection?.files?.filter { it.name.endsWith(".bin") }?.sumOf { it.size } ?: 0L)
            }
            presentBundles = bundles
            val chosen = bundles.firstOrNull { it.name == selected } ?: bundles.firstOrNull()
            presentModel = chosen?.name
            presentFacts = chosen?.facts
            bundles.forEach { log.add("Vorhandenes Paket: ${it.name}, gebaut mit QAIRT ${it.facts?.qairt ?: "unbekannt"}, Rechenprogramme ${Metrics.gb(it.binBytes)}") }
            return presentModel
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail("Modellspeicher prüfen", t)
            return presentModel
        }
    }

    /** Chooses which present bundle the tests use; a loaded model of another bundle is unloaded first. */
    suspend fun selectModel(name: String): Boolean {
        val b = presentBundles.firstOrNull { it.name == name } ?: return false
        if (loaded && presentModel != name) unload()
        selected = name
        presentModel = name
        presentFacts = b.facts
        log.add("Test-Modell gewählt: $name (Rechenprogramme ${Metrics.gb(b.binBytes)})")
        return true
    }

    private suspend fun readFacts(modelName: String): BundleFacts? = try {
        port.inspect(modelName)?.let { BundleFacts.extract(it.texts["metadata.json"], it.texts["htp_backend_ext_config.json"]) }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        null
    }

    /**
     * Downloads the zip of the candidate's release itself (resumable, checked against the known size and the zip's CRC32s)
     * and imports it into the SDK's store (LOCALFS). Phases for the screen: "Laden", "Prüfen", "Importieren".
     */
    suspend fun download(candidate: Candidate, onProgress: (phase: String, done: Long, total: Long) -> Unit): PullOutcome {
        val step = "Modell herunterladen (${candidate.modelName}, Ausgabe ${candidate.release})"
        attempts++
        tracker.begin(step)
        log.add("")
        log.add("Schritt 1, Versuch $attempts: $step ...")
        val free = store.freeBytes().takeIf { it >= 0 } ?: probe.freeStorageBytes()
        val zipPath = store.downloadsDir() + "/" + candidate.zipFileName
        val alreadyThere = if (store.exists("$zipPath.part")) store.treeBytes("$zipPath.part") else 0L
        log.add("  Modell-Name im SDK: ${candidate.modelName}, Ausgabe: ${candidate.release}, gebaut mit QAIRT ${candidate.expectedQairt}")
        log.add("  Quelle: ${candidate.zipUrl}")
        log.add("  Erwartete Größe: ${candidate.zipBytes} Bytes" + if (alreadyThere > 0) ", davon schon da: $alreadyThere (Fortsetzen)" else "")
        log.add("  Freier Speicher vorher: " + (if (free >= 0) Metrics.gb(free) else "unbekannt") + ", Netz: " + probe.networkType())
        val needed = ResumePlan.spaceNeeded(candidate.zipBytes, alreadyThere, candidate.unpackedBytes)
        if (free in 0 until needed) {
            log.add("FEHLER bei Schritt: $step")
            log.add("  Zu wenig freier Speicher: gebraucht werden etwa ${Metrics.gb(needed)} (Zip, entpacktes Modell und Reserve), frei sind ${Metrics.gb(free)}.")
            tracker.failed(step, "NoSpace")
            return PullOutcome.Failed(candidate, "zu wenig Speicher (${Metrics.gb(free)} frei, ${Metrics.gb(needed)} nötig)",
                PullPlanner.adviceAfterDownloadFailure(DownloadException.Kind.NO_SPACE, null))
        }
        val started = clock.nowMs()
        val job = kotlin.coroutines.coroutineContext[Job]
        val cancelled = { job?.isActive == false }
        fun downloadFailed(e: DownloadException): PullOutcome {
            if (e.kind == DownloadException.Kind.CANCELLED) {
                log.add("Download abgebrochen (ein erneuter Tipp auf Knopf 1 setzt fort).")
                tracker.failed(step, "Cancelled")
            } else {
                log.add("FEHLER bei Schritt: $step")
                log.add("  Klasse: DownloadException (${e.kind})" + (e.httpStatus?.let { ", HTTP-Status $it" } ?: ""))
                log.add("  Text: ${e.message}")
                tracker.failed(step, "DownloadException")
            }
            return PullOutcome.Failed(candidate, "${e.kind}: ${e.message}", PullPlanner.adviceAfterDownloadFailure(e.kind, e.httpStatus))
        }
        try {
            withContext(Dispatchers.IO) {
                downloader.download(candidate.zipUrl, zipPath, candidate.zipBytes, { d, t -> onProgress("Laden", d, t) }, cancelled)
            }
        } catch (e: DownloadException) {
            return downloadFailed(e)
        }
        log.add("  Zip geladen in ${Metrics.num((clock.nowMs() - started) / 1000.0, 0)} s, Größe stimmt (${candidate.zipBytes} Bytes).")
        try {
            val sum = withContext(Dispatchers.IO) { downloader.verifyZip(zipPath, { d, t -> onProgress("Prüfen", d, t) }, cancelled) }
            log.add("  Zip geprüft: ${sum.entries} Dateien, ${sum.binFiles} .bin-Teile, metadata.json vorhanden, Prüfsummen (CRC32) aller Dateien stimmen.")
        } catch (e: DownloadException) {
            if (e.kind == DownloadException.Kind.ZIP_INVALID) {
                store.deleteTree(zipPath) // a damaged zip is never kept: the next attempt downloads it again
                log.add("  Die beschädigte Zip-Datei wurde gelöscht.")
            }
            return downloadFailed(e)
        }
        // import into the SDK's store (no network)
        log.add("  Importiere in den Modellspeicher des SDK (LOCALFS) ...")
        var failureCode: Int? = null
        var failureMessage: String? = null
        var failedWithException: Throwable? = null
        var completed = false
        try {
            port.importLocal(candidate.modelName, zipPath).collect { update ->
                when (update) {
                    is PullUpdate.Progress -> onProgress("Importieren", update.downloadedBytes, update.totalBytes)
                    is PullUpdate.Completed -> completed = true
                    is PullUpdate.Failed -> {
                        failureCode = update.code
                        failureMessage = update.message
                    }
                }
            }
        } catch (e: CancellationException) {
            log.add("Import abgebrochen (die geladene Zip-Datei bleibt; Knopf 1 importiert sie erneut).")
            tracker.failed(step, "Cancelled")
            throw e
        } catch (t: Throwable) {
            failedWithException = t
        }
        val exception = failedWithException
        if (exception != null) {
            fail(step, exception)
            val code = ErrorText.sdkCode(exception)
            val message = ErrorText.sdkMessage(exception)
            return PullOutcome.Failed(candidate, ErrorText.shortError(code, message, ErrorText.className(exception)), PullPlanner.adviceAfterPullFailure(code, message))
        }
        if (!completed) {
            val code = failureCode ?: -1
            log.addAll(ErrorText.describePull(step, code, failureMessage ?: "Der Import endete ohne Abschlussmeldung"))
            tracker.failed(step, "PullEvent.Error")
            return PullOutcome.Failed(candidate, ErrorText.shortError(code, failureMessage, "PullEvent.Error"), PullPlanner.adviceAfterPullFailure(code, failureMessage))
        }
        val present = try {
            port.isModelPresent(candidate.modelName)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail(step, t)
            return PullOutcome.Failed(candidate, ErrorText.shortError(null, ErrorText.sdkMessage(t), ErrorText.className(t)), PullPlanner.adviceAfterPullFailure(null, null))
        }
        if (!present) {
            log.add("FEHLER bei Schritt: $step")
            log.add("  Der Import meldet 'fertig', aber das SDK findet das Modell nicht im Modellspeicher.")
            tracker.failed(step, "ModelNotPresent")
            return PullOutcome.Failed(candidate, "Import fertig, Modell nicht im Speicher", PullPlanner.Advice(giveUp = false, repeatUseful = true, deleteFirst = true, cancelled = false))
        }
        val facts = readFacts(candidate.modelName)
        if (facts?.qairt != null && QairtVersion.majorMinor(facts.qairt) != candidate.expectedQairt) {
            log.add("FEHLER bei Schritt: $step")
            log.add("  Das Paket ist mit QAIRT ${facts.qairt} gebaut, erwartet war ${candidate.expectedQairt}: Die Zip-Datei war nicht die erwartete Ausgabe.")
            tracker.failed(step, "WrongBundleVersion")
            return PullOutcome.Failed(candidate, "Paket hat QAIRT ${facts.qairt} statt ${candidate.expectedQairt}", PullPlanner.Advice(giveUp = true, repeatUseful = false, deleteFirst = false, cancelled = false))
        }
        selected = candidate.modelName
        log.add("  Paket gebaut mit QAIRT: ${facts?.qairt ?: "unbekannt"}, Metadaten-Format: " + when (facts?.modelFilesIsList) {
            true -> "Liste (neu)"
            false -> "Objekt (das SDK kann es lesen)"
            null -> "unbekannt"
        })
        if (store.deleteTree(zipPath)) log.add("  Zip-Datei nach dem Import gelöscht (Speicher frei).") else log.add("  Zip-Datei konnte nicht gelöscht werden: $zipPath")
        refreshModelPresent()
        log.add("Download und Import fertig (${candidate.modelName}) in ${Metrics.num((clock.nowMs() - started) / 1000.0, 0)} s.")
        tracker.end(step)
        return PullOutcome.Success(candidate)
    }

    /** Asks Qualcomm's list which models have an NPU bundle for which chipsets and writes the Qwen3 entries to the report. */
    suspend fun listHub() {
        val step = "Paketliste bei Qualcomm abfragen"
        tracker.begin(step)
        log.add("")
        log.add("$step (nur eine Liste, kein Modell-Download; Netz: ${probe.networkType()}) ...")
        try {
            val all = port.listHub()
            val qwen = all.filter { it.name.contains("qwen3", ignoreCase = true) }
            log.add("Qualcomms Liste: ${all.size} Modelle mit NPU-Paket, davon ${qwen.size} mit 'qwen3' im Namen.")
            qwen.forEach { log.add("  ${it.name}: " + it.chipsets.joinToString(", ").ifBlank { "(keine Chips)" }) }
            tracker.end(step)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail(step, t)
        }
    }

    suspend fun runTest(test: FixedTest, ignoreMemoryGuard: Boolean = false, onText: (String) -> Unit): TestOutcome {
        stopRequested = false
        val modelName = presentModel
        if (modelName == null) {
            log.add("Kein Modell im Speicher: erst Knopf 1 tippen.")
            return TestOutcome.Failed(loadFailed = false)
        }
        val testStart = clock.nowMs()
        log.add("")
        log.add("Schritt ${test.id}: ${test.title} ...")
        val memBefore = probe.memory()
        val tempBefore = probe.batteryTempC()
        var loadMs: Long? = null
        try {
            if (!loaded) {
                val loadStep = "Modell auf der NPU laden (nur qairt, kein CPU/GPU-Ersatz)"
                val pre = diagnoseBeforeLoad(modelName)
                // the memory guard: decide before the load whether it can fit (stage 80e: the 4B bundle ended in the low-memory killer)
                val avail = probe.memAvailableBytes().takeIf { it >= 0 } ?: probe.memory().systemAvailBytes
                val verdict = MemoryGuard.verdict(if (pre.binBytes > 0) pre.binBytes else MemoryGuard.UNKNOWN_BIN_BYTES, avail)
                log.add("Speicherwächter vor dem Laden (prüft ${DiagnoseFormatter.H4}): " + verdict.ruleText())
                if (!verdict.ok && !ignoreMemoryGuard) {
                    log.add("NICHT GELADEN: Es ist zu wenig Speicher frei, das Laden würde das Telefon in Speichermangel bringen (so endete der Versuch mit dem 4B-Modell).")
                    return TestOutcome.Blocked(verdict, modelName)
                }
                if (!verdict.ok) log.add("Auf ausdrücklichen Wunsch trotzdem geladen; die Notbremse (Selbstbeendigung der App bei Speichermangel) bleibt aktiv.")
                tracker.begin(loadStep)
                watchdog.start(loadStep)
                val marker = "secondhand-load-" + clock.nowMs()
                diag.markLog(marker)
                val t0 = clock.nowMs()
                val info = try {
                    port.load(modelName).also { log.addAll(watchdog.stop()) }
                } catch (e: CancellationException) {
                    log.addAll(watchdog.stop())
                    throw e
                } catch (t: Throwable) {
                    log.addAll(watchdog.stop())
                    fail(loadStep, t)
                    afterLoadFailure(marker)
                    return TestOutcome.Failed(loadFailed = true, bundleNewerThanRuntime = pre.compat == Compat.BUNDLE_NEWER, metadataFormatTooNew = pre.formatTooNew)
                }
                loadMs = clock.nowMs() - t0
                val runtime = info.manifestRuntime
                if (!runtime.isNullOrBlank() && runtime != GeniexConstants.RUNTIME_QAIRT) {
                    // the SDK's model store says this is not a qairt (NPU) bundle: do not call this an NPU test
                    val e = NpuException(null, "Laufzeit laut Modellspeicher: $runtime (erwartet: qairt)", "NpuFallbackDetected")
                    port.unload()
                    fail(loadStep, e)
                    return TestOutcome.Failed(loadFailed = true)
                }
                loaded = true
                tracker.end(loadStep)
                log.add("Modell geladen in ${Metrics.num(loadMs / 1000.0, 1)} s (Laufzeit laut Modellspeicher: ${runtime ?: "nicht gemeldet"}).")
            }
            val step = "Antwort erzeugen (${test.title})"
            tracker.begin(step)
            val genStart = clock.nowMs()
            var firstTokenAt: Long? = null
            var lastTokenAt = genStart
            var events = 0L
            val answer = StringBuilder()
            var profile: GenProfile? = null
            var error: Throwable? = null
            port.generate(test.systemInstruction, test.userText, test.maxTokens).collect { e ->
                when (e) {
                    is GenEvent.Token -> {
                        val now = clock.nowMs()
                        if (firstTokenAt == null) firstTokenAt = now
                        lastTokenAt = now
                        events++
                        answer.append(e.text)
                        onText(answer.toString())
                    }
                    is GenEvent.Done -> profile = e.profile
                    is GenEvent.Failed -> error = e.error
                }
            }
            val failure = error
            if (failure != null) {
                fail(step, failure)
                return TestOutcome.Failed(loadFailed = false)
            }
            val memAfter = probe.memory()
            val tempAfter = probe.batteryTempC()
            val first = firstTokenAt
            val sdk = profile
            val result = TestResult(
                test = test,
                answer = answer.toString(),
                loadMs = loadMs,
                appFirstTokenMs = first?.let { it - genStart },
                appTokenEvents = events,
                appDecodeSpeed = first?.let { Metrics.appDecodeSpeed(sdk?.generatedTokens ?: events, it, lastTokenAt) },
                sdk = sdk,
                memBefore = memBefore,
                memAfter = memAfter,
                tempBeforeC = tempBefore,
                tempAfterC = tempAfter,
                totalMs = clock.nowMs() - testStart,
                stoppedByUser = stopRequested,
            )
            tracker.end(step)
            log.addAll(ReportFormatter.testLines(result))
            return TestOutcome.Ok(result)
        } catch (e: CancellationException) {
            log.add("Test abgebrochen.")
            throw e
        }
    }

    /** Writes the facts that decide whether the load can work; returns how the bundle's QAIRT compares with the runtime's. */
    private data class Pre(val compat: Compat, val formatTooNew: Boolean, val binBytes: Long)

    private suspend fun diagnoseBeforeLoad(modelName: String): Pre {
        log.add("")
        log.add("Befunde vor dem Laden (jede Zeile sagt, welche Vermutung sie prüft):")
        val runtimeQairt = diag.runtimeQairtVersion()
        this.runtimeQairt = runtimeQairt
        var compat = Compat.UNKNOWN
        var formatTooNew = false
        var binBytes = 0L
        try {
            val inspection = port.inspect(modelName)
            if (inspection == null) {
                log.add("  Paket nicht lesbar: das SDK kennt keinen Ordner für $modelName.")
            } else {
                val facts = BundleFacts.extract(inspection.texts["metadata.json"], inspection.texts["htp_backend_ext_config.json"])
                presentFacts = facts
                log.addAll(DiagnoseFormatter.bundleLines(inspection, facts, runtimeQairt))
                compat = QairtVersion.compat(facts.qairt, runtimeQairt)
                formatTooNew = facts.modelFilesIsList == true
                binBytes = inspection.files.filter { it.name.endsWith(".bin") }.sumOf { it.size }
                val backup = compatBackupPath(modelName)
                if (store.exists(backup)) log.add("  Hinweis: Die Metadaten dieses Pakets wurden von der App angepasst (Kompatibilitäts-Metadaten); das Original ist gesichert unter $backup.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log.addAll(ErrorText.describe("Paket untersuchen", t))
        }
        val m = probe.memory()
        log.add("Arbeitsspeicher direkt vor dem Laden (prüft ${DiagnoseFormatter.H4}): frei im System ${Metrics.mb(m.systemAvailBytes)}, " +
            "PSS der App ${Metrics.mb(m.pssBytes)}, nativer Heap ${Metrics.mb(m.nativeHeapBytes)}")
        diag.environmentLines().takeIf { it.isNotEmpty() }?.let { log.addAll(it) }
        diag.nativeLoadLines().takeIf { it.isNotEmpty() }?.let { log.addAll(it) }
        return Pre(compat, formatTooNew, binBytes)
    }

    /** After a failed load: the process facts and the runtime's own log lines, filtered. */
    private fun afterLoadFailure(marker: String) {
        diag.processLines().takeIf { it.isNotEmpty() }?.let {
            log.add("")
            log.add("Prozess nach dem gescheiterten Laden (prüft ${DiagnoseFormatter.H2} und ${DiagnoseFormatter.H3}):")
            log.addAll(it)
        }
        val lines = LogFilter.relevant(LogFilter.afterMarker(diag.logSince(marker), marker), FixedTests.forbiddenLogFragments(), MAX_LOG_LINES)
        log.add("")
        log.add("Protokoll der Laufzeit (eigene Log-Zeilen dieser App seit Beginn des Ladens, gefiltert auf den KI-Chip-Teil; " +
            "die Prüftexte sind fest eingebaut, Zeilen mit ihnen werden entfernt; prüft ${DiagnoseFormatter.H1}, ${DiagnoseFormatter.H3}, ${DiagnoseFormatter.H4}):")
        if (lines.isEmpty()) log.add("  (keine passenden Zeilen; das System gibt der App ihr Protokoll vielleicht nicht frei)") else lines.forEach { log.add("  $it") }
    }

    fun availBytesNow(): Long = probe.memAvailableBytes().takeIf { it >= 0 } ?: probe.memory().systemAvailBytes

    fun freeStorageBytesNow(): Long = probe.freeStorageBytes()

    fun networkTypeNow(): String = probe.networkType()

    suspend fun requestStop() {
        stopRequested = true
        port.stop()
    }

    suspend fun unload() {
        val step = "Modell entladen"
        try {
            port.unload()
            loaded = false
            log.add("Modell entladen.")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail(step, t)
        }
    }

    private fun compatBackupPath(modelName: String): String =
        store.backupDir() + "/" + modelName.replace('/', '_') + "/metadata.json.original"

    private fun storeLeftovers(): List<String> {
        val root = store.modelsRoot()
        return store.listDirs(root).flatMap { org -> store.listDirs("$root/$org").map { "$org/$it" } }
    }

    /**
     * Removes every model of this app from the SDK's store and checks the result: after the SDK's own remove the files may
     * still be there (seen on the phone: "Gelöscht" and the bundle was still present), so the app deletes what is left in
     * the SDK's folder itself (it is the app's private folder) and reports "Gelöscht" only when the folders are gone.
     */
    suspend fun deleteModels(): Boolean {
        val step = "Modell löschen"
        try {
            if (loaded) unload()
            val results = PullPlanner.ALL_MODEL_NAMES.map { it to port.deleteModel(it) }
            log.add("")
            log.add("Löschen: SDK-Ergebnis je Modell: " + results.joinToString(", ") { (n, rc) -> "$n=$rc" } + " (0 = gelöscht, ${SdkCodes.FILE_NOT_FOUND} = war nicht da)")
            val root = store.modelsRoot()
            val left = storeLeftovers()
            if (left.isNotEmpty()) {
                log.add("Das SDK hat ${left.size} Ordner nicht entfernt: ${left.joinToString(", ")}. Die App löscht sie selbst:")
                for (dir in left) {
                    val path = "$root/$dir"
                    val bytes = store.treeBytes(path)
                    val ok = store.deleteTree(path)
                    log.add("  ${if (ok) "entfernt" else "NICHT entfernt"}: $dir (${Metrics.gb(bytes)})")
                }
                for (org in store.listDirs(root)) if (store.listDirs("$root/$org").isEmpty()) store.deleteTree("$root/$org")
            }
            val dl = store.downloadsDir()
            if (store.exists(dl)) {
                val bytes = store.treeBytes(dl)
                if (bytes > 0) log.add("  Heruntergeladene Zip-Dateien: " + if (store.deleteTree(dl)) "entfernt (${Metrics.gb(bytes)})" else "NICHT entfernt")
            }
            val stillInSdk = PullPlanner.ALL_MODEL_NAMES.filter { port.isModelPresent(it) }
            val after = storeLeftovers()
            val ok = stillInSdk.isEmpty() && after.isEmpty()
            presentModel = stillInSdk.firstOrNull()
            if (ok) {
                presentFacts = null
                presentBundles = emptyList()
                selected = null
            }
            log.add(
                if (ok) "Gelöscht und geprüft: Der Modellspeicher des SDK ist leer."
                else "NICHT vollständig gelöscht. Noch da: " + (stillInSdk.map { "SDK: $it" } + after.map { "Ordner: $it" }).joinToString(", "),
            )
            return ok
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail(step, t)
            return false
        }
    }

    /**
     * Compatibility metadata (an option, never automatic): writes a metadata.json in the old object form next to the
     * original (kept as a backup outside the model folder). It lets SDK 0.10.0 read a bundle of release v0.67.0 or newer; whether
     * the context binaries built with QAIRT 2.51 then load on the 2.50.40 runtime is not known.
     */
    suspend fun applyCompatMetadata(): Boolean {
        val step = "Kompatibilitäts-Metadaten schreiben"
        val name = presentModel
        log.add("")
        log.add("$step (Option, kein Download) ...")
        if (name == null) {
            log.add("  Kein Paket im Modellspeicher.")
            return false
        }
        try {
            val inspection = port.inspect(name)
            val dir = inspection?.modelDir
            val text = inspection?.texts?.get("metadata.json")
            if (dir == null || text == null) {
                log.add("  metadata.json des Pakets nicht lesbar.")
                return false
            }
            when (val r = MetadataCompat.convert(text)) {
                is MetadataCompat.Result.AlreadyOldFormat -> {
                    log.add("  Nicht nötig: model_files ist schon ein Objekt.")
                    return false
                }
                is MetadataCompat.Result.Failed -> {
                    log.add("  Umwandlung nicht möglich: ${r.reason}")
                    return false
                }
                is MetadataCompat.Result.Converted -> {
                    val backup = compatBackupPath(name)
                    if (!store.exists(backup) && !store.copyFile("$dir/metadata.json", backup)) {
                        log.add("  Sicherung des Originals fehlgeschlagen ($backup): nichts geändert.")
                        return false
                    }
                    if (!store.writeText("$dir/metadata.json", r.json)) {
                        log.add("  Schreiben fehlgeschlagen: nichts mehr sicher, Original liegt unter $backup.")
                        return false
                    }
                    val facts = readFacts(name)
                    presentFacts = facts
                    log.add("  Umgewandelt: ${r.files} Graph-Dateien, ${r.tensors} Tensoren, model_files jetzt ein Objekt; runtime, precision, dtype und supplementary_files im alten Stil.")
                    log.add("  Original gesichert unter $backup. Prüfung nach dem Schreiben: Format " + when (facts?.modelFilesIsList) {
                        false -> "Objekt (ok)"
                        true -> "immer noch Liste (FEHLER)"
                        null -> "nicht lesbar (FEHLER)"
                    })
                    log.add("  Das ändert keinen Wert (Formen, Typen, Quantisierung sind gleich); es ändert nichts an den Rechenprogrammen (.bin), die mit QAIRT ${facts?.qairt ?: "?"} gebaut sind.")
                    return facts?.modelFilesIsList == false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail(step, t)
            return false
        }
    }

    private fun fail(step: String, t: Throwable) {
        tracker.failed(step, ErrorText.className(t))
        log.addAll(ErrorText.describe(step, t))
    }
}
