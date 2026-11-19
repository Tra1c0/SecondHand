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
) {
    sealed interface PullOutcome {
        data class Success(val candidate: Candidate) : PullOutcome

        /** [error] is the short error text of the attempt (code and SDK text), [advice] what the screen should offer. */
        data class Failed(val candidate: Candidate, val error: String, val advice: PullPlanner.Advice) : PullOutcome
    }

    sealed interface TestOutcome {
        data class Ok(val result: TestResult) : TestOutcome

        /** [loadFailed]: the failure was while loading the model (the pulled candidate is then given up). */
        data class Failed(val loadFailed: Boolean) : TestOutcome
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

    @Volatile
    private var stopRequested = false

    private var attempts = 0

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
            return found
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail("Modellspeicher prüfen", t)
            return presentModel
        }
    }

    suspend fun pull(candidate: Candidate, onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit): PullOutcome {
        val step = "Modell herunterladen (${candidate.modelName}, Chipset ${candidate.chipset})"
        attempts++
        tracker.begin(step)
        log.add("")
        log.add("Schritt 1, Versuch $attempts: $step ...")
        val free = probe.freeStorageBytes()
        log.add("  Modell-ID: ${candidate.modelName}, Chipset-ID: ${candidate.chipset}")
        log.add("  Freier Speicher vorher: " + (if (free >= 0) Metrics.gb(free) else "unbekannt") + ", Netz: " + probe.networkType())
        val started = clock.nowMs()
        var failureCode: Int? = null
        var failureMessage: String? = null
        var failedWithException: Throwable? = null
        var completed = false
        try {
            port.pull(candidate.modelName, candidate.chipset).collect { update ->
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
                tracker.begin(loadStep)
                val t0 = clock.nowMs()
                val info = try {
                    port.load(modelName)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    fail(loadStep, t)
                    return TestOutcome.Failed(loadFailed = true)
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
