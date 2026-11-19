package dev.personal.secondhand.companion

// Formats everything the owner pastes back (stage 80): device header, test numbers. Pure, unit-tested.

data class DeviceHeader(
    val manufacturer: String,
    val model: String,
    val device: String,
    val hardware: String,
    val socManufacturer: String?,
    val socModel: String?,
    val androidRelease: String,
    val apiLevel: Int,
    val supportedAbis: String,
    val freeStorageBytes: Long,
    val totalRamBytes: Long,
    val appVersion: String,
)

data class TestResult(
    val test: FixedTest,
    val answer: String,
    /** null when the model was already loaded for this test. */
    val loadMs: Long?,
    val appFirstTokenMs: Long?,
    val appTokenEvents: Long,
    val appDecodeSpeed: Double?,
    val sdk: GenProfile?,
    val memBefore: MemSnapshot,
    val memAfter: MemSnapshot,
    val tempBeforeC: Double?,
    val tempAfterC: Double?,
    val totalMs: Long,
    val stoppedByUser: Boolean,
)

object ReportFormatter {
    fun headerLines(h: DeviceHeader): List<String> = listOf(
        "Gerät: ${h.manufacturer} ${h.model} (${h.device})",
        "Chip laut System: " + soc(h) + ", Hardware: ${h.hardware}",
        "Android ${h.androidRelease} (SDK/API ${h.apiLevel}), ABI: ${h.supportedAbis}",
        "Arbeitsspeicher gesamt: ${Metrics.gb(h.totalRamBytes)}",
        "Freier Speicher (App-Ordner): ${Metrics.gb(h.freeStorageBytes)}",
        "App: ${h.appVersion}",
    )

    private fun soc(h: DeviceHeader): String {
        val parts = listOfNotNull(h.socManufacturer?.takeIf { it.isNotBlank() }, h.socModel?.takeIf { it.isNotBlank() })
        return if (parts.isEmpty()) "nicht gemeldet (Android 11 oder älter)" else parts.joinToString(" ")
    }

    fun testLines(r: TestResult): List<String> {
        val lines = mutableListOf<String>()
        lines += "=== ${r.test.title} ==="
        if (r.stoppedByUser) lines += "ABGEBROCHEN mit Stopp (die Zahlen unten sind unvollständig)"
        lines += "Antwort: " + r.answer.ifBlank { "(leer)" }
        lines += "Ladezeit: " + (r.loadMs?.let { Metrics.num(it / 1000.0, 1) + " s" } ?: "-, Modell war schon geladen")
        lines += "Zeit bis zum ersten Token (App, inkl. Chat-Vorlage): " +
            (r.appFirstTokenMs?.let { it.toString() + " ms" } ?: "-")
        r.sdk?.let {
            lines += "Zeit bis zum ersten Token (SDK): " + Metrics.num(it.ttftMs, 0) + " ms"
            lines += "Tokens pro Sekunde beim Schreiben (SDK): " + Metrics.num(it.decodingSpeed, 1) +
                " (" + it.generatedTokens + " Tokens in " + Metrics.num(it.decodeTimeMs, 0) + " ms; " +
                "rechnerisch " + Metrics.numOrDash(Metrics.tokensPerSecond(it.generatedTokens, it.decodeTimeMs)) + ")"
            lines += "Eingabe lesen (SDK): " + Metrics.num(it.prefillSpeed, 1) + " Tokens/s (" +
                it.promptTokens + " Tokens in " + Metrics.num(it.promptTimeMs, 0) + " ms)"
            lines += "Stopp-Grund laut SDK: " + (it.stopReason?.takeIf { s -> s.isNotBlank() } ?: "(keiner)")
        } ?: run { lines += "SDK-Messwerte: keine (die Antwort endete ohne Abschlussmeldung)" }
        lines += "Tokens pro Sekunde (von der App gemessen, ${r.appTokenEvents} Stücke): " +
            Metrics.numOrDash(r.appDecodeSpeed)
        val speed = r.sdk?.decodingSpeed ?: r.appDecodeSpeed
        if (speed != null && speed < Metrics.SUSPICIOUSLY_SLOW_TOKENS_PER_SECOND) {
            lines += "ACHTUNG: auffällig langsam (unter ${Metrics.num(Metrics.SUSPICIOUSLY_SLOW_TOKENS_PER_SECOND, 0)} " +
                "Tokens/s); Qualcomm nennt 16 bis 21 auf dem Galaxy-Chip. Läuft es wirklich auf der NPU?"
        }
        lines += "Gesamtdauer des Tests: " + Metrics.num(r.totalMs / 1000.0, 1) + " s"
        lines += "App-Speicher vorher -> nachher:"
        lines += "  nativer Heap (Debug.getNativeHeapAllocatedSize): " + Metrics.mb(r.memBefore.nativeHeapBytes) +
            " -> " + Metrics.mb(r.memAfter.nativeHeapBytes)
        lines += "  PSS gesamt (Debug.MemoryInfo): " + Metrics.mb(r.memBefore.pssBytes) + " -> " +
            Metrics.mb(r.memAfter.pssBytes) + " (NPU-Speicher ist evtl. nicht enthalten)"
        lines += "  frei im ganzen System (ActivityManager.MemoryInfo.availMem): " +
            Metrics.mb(r.memBefore.systemAvailBytes) + " -> " + Metrics.mb(r.memAfter.systemAvailBytes)
        lines += "Akku-Temperatur vorher -> nachher: " + temp(r.tempBeforeC) + " -> " + temp(r.tempAfterC)
        if (Metrics.hasThinkBlock(r.answer)) lines += "ACHTUNG: Die Antwort enthält einen Denkblock (think), obwohl das Denken aus sein sollte."
        r.test.expected?.let { expected ->
            if (Metrics.sameAsExpected(r.answer, expected)) {
                lines += "Antwort = erwarteter Satz: JA"
            } else {
                lines += "Antwort = erwarteter Satz: NEIN"
                lines += "  Erwartet: $expected"
            }
        } ?: run {
            val shorter = Metrics.normalized(r.answer).length < Metrics.normalized(r.test.userText).length
            lines += "Antwort kürzer als der Originalabsatz: " + if (shorter) "JA" else "NEIN"
            lines += "Wörter: Original ${Metrics.wordCount(r.test.userText)}, Antwort ${Metrics.wordCount(Metrics.removeThinkBlock(r.answer))}"
        }
        return lines
    }

    private fun temp(c: Double?): String = if (c == null) "nicht gemeldet" else Metrics.num(c, 1) + " °C"

    fun fullReport(header: List<String>, log: List<String>, now: String): String =
        (listOf("SecondHand Begleit-Spike (Etappe 80) - Bericht vom $now") + header + listOf("") + log)
            .joinToString("\n")
}
