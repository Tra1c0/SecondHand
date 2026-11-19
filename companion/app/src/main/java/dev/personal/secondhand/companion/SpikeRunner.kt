package dev.personal.secondhand.companion

import kotlinx.coroutines.CancellationException

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
        data class Failed(val loadFailed: Boolean, val bundleNewerThanRuntime: Boolean = false) : TestOutcome
    }

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

    /** The model name of the bundle that is in the SDK's store (the first candidate model found), or null. */
    @Volatile
    var presentModel: String? = null
        private set

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

    /** Looks for a complete bundle of any candidate model in the SDK's store; returns its model name. */
    suspend fun refreshModelPresent(): String? {
        try {
            val found = PullPlanner.CANDIDATES.map { it.modelName }.distinct().firstOrNull { port.isModelPresent(it) }
            presentModel = found
            presentFacts = null
            if (found != null) {
                val facts = readFacts(found)
                presentFacts = facts
                log.add("Vorhandenes Paket: $found, gebaut mit QAIRT ${facts?.qairt ?: "unbekannt"}")
            }
            return found
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail("Modellspeicher prüfen", t)
            return presentModel
        }
    }

    private suspend fun readFacts(modelName: String): BundleFacts? = try {
        port.inspect(modelName)?.let { BundleFacts.extract(it.texts["metadata.json"], it.texts["htp_backend_ext_config.json"]) }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        null
    }

    suspend fun pull(candidate: Candidate, onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit): PullOutcome {
        val step = "Modell herunterladen (${candidate.modelName}, Chipset ${candidate.chipset})"
        attempts++
        tracker.begin(step)
        log.add("")
        log.add("Schritt 1, Versuch $attempts: $step ...")
        val free = probe.freeStorageBytes()
        log.add("  Modell-ID: ${candidate.modelName}, Chipset-ID: ${candidate.chipset}, Ausgabe: ${candidate.release ?: "neueste"}")
        log.add("  Freier Speicher vorher: " + (if (free >= 0) Metrics.gb(free) else "unbekannt") + ", Netz: " + probe.networkType())
        val started = clock.nowMs()
        var failureCode: Int? = null
        var failureMessage: String? = null
        var failedWithException: Throwable? = null
        var completed = false
        try {
            port.pull(candidate.modelName, candidate.chipset, candidate.release).collect { update ->
                when (update) {
                    is PullUpdate.Progress -> onProgress(update.downloadedBytes, update.totalBytes)
                    is PullUpdate.Completed -> completed = true
                    is PullUpdate.Failed -> {
                        failureCode = update.code
                        failureMessage = update.message
                    }
                }
            }
        } catch (e: CancellationException) {
            log.add("Download abgebrochen (ein erneuter Tipp auf Knopf 1 setzt fort).")
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
            return PullOutcome.Failed(candidate, ErrorText.shortError(code, message, ErrorText.className(exception)),
                PullPlanner.adviceAfterPullFailure(code, message))
        }
        if (!completed) {
            val code = failureCode ?: -1
            log.addAll(ErrorText.describePull(step, code, failureMessage ?: "Der Download endete ohne Abschlussmeldung"))
            tracker.failed(step, "PullEvent.Error")
            return PullOutcome.Failed(candidate, ErrorText.shortError(code, failureMessage, "PullEvent.Error"),
                PullPlanner.adviceAfterPullFailure(code, failureMessage))
        }
        val present = try {
            port.isModelPresent(candidate.modelName)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail(step, t)
            return PullOutcome.Failed(candidate, ErrorText.shortError(null, ErrorText.sdkMessage(t), ErrorText.className(t)),
                PullPlanner.adviceAfterPullFailure(null, null))
        }
        if (!present) {
            log.add("FEHLER bei Schritt: $step")
            log.add("  Der Download meldet 'fertig', aber das SDK findet das Modell nicht im Modellspeicher.")
            tracker.failed(step, "ModelNotPresent")
            return PullOutcome.Failed(candidate, "Download fertig, Modell nicht im Speicher",
                PullPlanner.Advice(giveUp = false, repeatUseful = true, deleteFirst = true, cancelled = false))
        }
        presentModel = candidate.modelName
        val facts = readFacts(candidate.modelName)
        presentFacts = facts
        log.add("  Paket gebaut mit QAIRT: ${facts?.qairt ?: "unbekannt"} (erwartet für diesen Kandidaten: ${candidate.expectedQairt ?: "egal"})")
        if (candidate.expectedQairt != null && facts?.qairt != null && QairtVersion.majorMinor(facts.qairt) != candidate.expectedQairt) {
            log.add("FEHLER bei Schritt: $step")
            log.add("  Das geladene Paket ist mit QAIRT ${facts.qairt} gebaut, erwartet war ${candidate.expectedQairt}: Die Wahl der Ausgabe ${candidate.release} hat im SDK nicht gewirkt.")
            tracker.failed(step, "WrongBundleVersion")
            return PullOutcome.Failed(candidate, "Paket hat QAIRT ${facts.qairt} statt ${candidate.expectedQairt}",
                PullPlanner.Advice(giveUp = true, repeatUseful = false, deleteFirst = false, cancelled = false))
        }
        log.add("Download fertig (${candidate.modelName}, ${candidate.chipset}) in ${Metrics.num((clock.nowMs() - started) / 1000.0, 0)} s.")
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

    suspend fun runTest(test: FixedTest, onText: (String) -> Unit): TestOutcome {
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
                val compat = diagnoseBeforeLoad(modelName)
                tracker.begin(loadStep)
                val marker = "secondhand-load-" + clock.nowMs()
                diag.markLog(marker)
                val t0 = clock.nowMs()
                val info = try {
                    port.load(modelName)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    fail(loadStep, t)
                    afterLoadFailure(marker)
                    return TestOutcome.Failed(loadFailed = true, bundleNewerThanRuntime = compat == Compat.BUNDLE_NEWER)
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
    private suspend fun diagnoseBeforeLoad(modelName: String): Compat {
        log.add("")
        log.add("Befunde vor dem Laden (jede Zeile sagt, welche Vermutung sie prüft):")
        val runtimeQairt = diag.runtimeQairtVersion()
        this.runtimeQairt = runtimeQairt
        var compat = Compat.UNKNOWN
        try {
            val inspection = port.inspect(modelName)
            if (inspection == null) {
                log.add("  Paket nicht lesbar: das SDK kennt keinen Ordner für $modelName.")
            } else {
                val facts = BundleFacts.extract(inspection.texts["metadata.json"], inspection.texts["htp_backend_ext_config.json"])
                presentFacts = facts
                log.addAll(DiagnoseFormatter.bundleLines(inspection, facts, runtimeQairt))
                compat = QairtVersion.compat(facts.qairt, runtimeQairt)
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
        return compat
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

    /**
     * Removes every candidate model (complete or partial, as far as the SDK removes partial ones). "Not in the
     * store" counts as fine.
     */
    suspend fun deleteModels(): Boolean {
        val step = "Modell löschen"
        try {
            if (loaded) unload()
            var allOk = true
            for (name in PullPlanner.CANDIDATES.map { it.modelName }.distinct()) {
                val rc = port.deleteModel(name)
                val ok = rc == 0 || rc == SdkCodes.FILE_NOT_FOUND
                if (!ok) allOk = false
                log.add(
                    when (rc) {
                        0 -> "Gelöscht: $name"
                        SdkCodes.FILE_NOT_FOUND -> "Nichts zu löschen: $name"
                        else -> "Löschen von $name: SDK-Ergebniscode $rc (0 = ok)"
                    },
                )
            }
            if (allOk) presentModel = null
            return allOk
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
