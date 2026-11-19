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
        data class Success(val chipset: String) : PullOutcome
        data class Failed(val chipset: String, val offerGalaxyBundle: Boolean) : PullOutcome
    }

    sealed interface TestOutcome {
        data class Ok(val result: TestResult) : TestOutcome

        /** [loadFailed]: the failure was while loading the model (the pulled variant is then marked as failed). */
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

    @Volatile
    private var stopRequested = false

    suspend fun init(): InitInfo? {
        val step = "GenieX-SDK starten"
        tracker.begin(step)
        return try {
            val info = port.init()
            supported = true
            log.add("GenieX-SDK gestartet. SDK-Version: ${info.sdkVersion ?: "nicht gemeldet"}, " +
                "QAIRT-Plugin: ${info.qairtPluginVersion?.ifBlank { null } ?: "nicht gemeldet"}")
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

    suspend fun refreshModelPresent(): Boolean = try {
        port.isModelPresent()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        fail("Modellspeicher prüfen", t)
        false
    }

    suspend fun pull(chipset: String, onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit): PullOutcome {
        val step = "Modell herunterladen (Chipset $chipset)"
        tracker.begin(step)
        log.add("Schritt 1: $step ...")
        val started = clock.nowMs()
        var failureCode: Int? = null
        var failureMessage: String? = null
        var failedWithException: Throwable? = null
        var completed = false
        try {
            port.pull(chipset).collect { update ->
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
            log.add("Download abgebrochen (ein erneuter Tipp setzt fort).")
            throw e
        } catch (t: Throwable) {
            failedWithException = t
        }
        val exception = failedWithException
        if (exception != null) {
            fail(step, exception)
            return PullOutcome.Failed(chipset, PullPlanner.afterPullFailure(chipset, null, ErrorText.sdkMessage(exception)) ==
                PullPlanner.AfterFailure.OfferGalaxyBundle)
        }
        if (!completed) {
            val code = failureCode ?: -1
            log.addAll(ErrorText.describePull(step, code, failureMessage ?: "Der Download endete ohne Abschlussmeldung"))
            tracker.failed(step, "PullEvent.Error")
            val offer = PullPlanner.afterPullFailure(chipset, code, failureMessage) == PullPlanner.AfterFailure.OfferGalaxyBundle
            return PullOutcome.Failed(chipset, offer)
        }
        val present = try {
            port.isModelPresent()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fail(step, t)
            return PullOutcome.Failed(chipset, false)
        }
        if (!present) {
            log.add("FEHLER bei Schritt: $step")
            log.add("  Der Download meldet 'fertig', aber das SDK findet das Modell nicht im Modellspeicher.")
            tracker.failed(step, "ModelNotPresent")
            return PullOutcome.Failed(chipset, false)
        }
        log.add("Download fertig (Chipset $chipset) in ${Metrics.num((clock.nowMs() - started) / 1000.0, 0)} s.")
        tracker.end(step)
        return PullOutcome.Success(chipset)
    }

    suspend fun runTest(test: FixedTest, onText: (String) -> Unit): TestOutcome {
        stopRequested = false
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
                    port.load()
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

    suspend fun deleteModel(): Boolean {
        val step = "Modell löschen"
        try {
            if (loaded) unload()
            val rc = port.deleteModel()
            log.add(if (rc == 0) "Modell aus dem Speicher des SDK gelöscht." else "Modell löschen: SDK-Ergebniscode $rc (0 = ok).")
            return rc == 0
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
