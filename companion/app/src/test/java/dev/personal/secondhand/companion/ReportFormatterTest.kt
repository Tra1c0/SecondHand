package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFormatterTest {
    private val header = DeviceHeader(
        manufacturer = "Xiaomi", model = "24129PN74G", device = "dada", hardware = "qcom",
        socManufacturer = "QTI", socModel = "SM8750", androidRelease = "15", apiLevel = 35,
        supportedAbis = "arm64-v8a", freeStorageBytes = 90L * 1073741824, totalRamBytes = 12L * 1073741824,
        appVersion = "Begleit-Spike 0.1",
    )

    private fun result(answer: String, sdk: GenProfile?, loadMs: Long? = 4200, app: Double? = 18.0, test: FixedTest = FixedTests.SENTENCE) =
        TestResult(
            test = test, answer = answer, loadMs = loadMs, appFirstTokenMs = 320, appTokenEvents = 11, appDecodeSpeed = app,
            sdk = sdk,
            memBefore = MemSnapshot(100L * 1048576, 300L * 1048576, 5000L * 1048576),
            memAfter = MemSnapshot(150L * 1048576, 3500L * 1048576, 2000L * 1048576),
            tempBeforeC = 31.0, tempAfterC = 33.5, totalMs = 9000, stoppedByUser = false,
        )

    @Test fun headerHasDeviceSocAndroidStorageRam() {
        val text = ReportFormatter.headerLines(header).joinToString("\n")
        assertTrue("Xiaomi 24129PN74G" in text)
        assertTrue("Chip laut System: QTI SM8750" in text)
        assertTrue("Android 15 (SDK/API 35)" in text)
        assertTrue("Arbeitsspeicher gesamt: 12,0 GB" in text)
        assertTrue("Freier Speicher (App-Ordner): 90,0 GB" in text)
    }

    @Test fun oldAndroidWithoutSocInfoIsSaidSo() {
        val text = ReportFormatter.headerLines(header.copy(socManufacturer = null, socModel = null)).joinToString("\n")
        assertTrue("nicht gemeldet (Android 11 oder älter)" in text)
    }

    @Test fun testLinesShowAllNumbersAndTheVerdict() {
        val sdk = GenProfile(310.0, 280.0, 500.0, 40, 10, 142.0, 20.0, "stop")
        val text = ReportFormatter.testLines(result(FixedTests.SENTENCE.expected!!, sdk)).joinToString("\n")
        assertTrue("Ladezeit: 4,2 s" in text)
        assertTrue("Zeit bis zum ersten Token (SDK): 310 ms" in text)
        assertTrue("Tokens pro Sekunde beim Schreiben (SDK): 20,0" in text)
        assertTrue("rechnerisch 20,0" in text)
        assertTrue("von der App gemessen, 11 Stücke): 18,0" in text)
        assertTrue("nativer Heap (Debug.getNativeHeapAllocatedSize): 100 MB -> 150 MB" in text)
        assertTrue("PSS gesamt (Debug.MemoryInfo): 300 MB -> 3500 MB" in text)
        assertTrue("Akku-Temperatur vorher -> nachher: 31,0 °C -> 33,5 °C" in text)
        assertTrue("Antwort = erwarteter Satz: JA" in text)
        assertFalse("auffällig langsam" in text)
    }

    @Test fun wrongAnswerShowsTheExpectedSentence() {
        val text = ReportFormatter.testLines(result("Irgendwas anderes", null)).joinToString("\n")
        assertTrue("Antwort = erwarteter Satz: NEIN" in text)
        assertTrue("Erwartet: " + FixedTests.SENTENCE.expected in text)
        assertTrue("SDK-Messwerte: keine" in text)
    }

    @Test fun slowSpeedIsFlaggedAsSuspicious() {
        val sdk = GenProfile(310.0, 280.0, 5000.0, 40, 10, 142.0, 2.0, "stop")
        assertTrue("auffällig langsam" in ReportFormatter.testLines(result("x", sdk)).joinToString("\n"))
    }

    @Test fun alreadyLoadedModelHasNoLoadTime() {
        assertTrue("Modell war schon geladen" in ReportFormatter.testLines(result("x", null, loadMs = null)).joinToString("\n"))
    }

    @Test fun paragraphTestHasNoExpectedSentenceButALengthCheck() {
        val text = ReportFormatter.testLines(result("Kurz und nett.", null, test = FixedTests.PARAGRAPH)).joinToString("\n")
        assertTrue("Antwort kürzer als der Originalabsatz: JA" in text)
        assertFalse("erwarteter Satz" in text)
    }

    @Test fun thinkBlockInTheAnswerIsFlagged() {
        assertTrue("Denkblock" in ReportFormatter.testLines(result("<think>hm</think>Ok", null)).joinToString("\n"))
    }

    @Test fun fullReportStartsWithTitleThenHeaderThenLog() {
        val full = ReportFormatter.fullReport(listOf("H1", "H2"), listOf("L1"), "2026-11-19 12:00").lines()
        assertEquals("SecondHand Begleit-Spike (Etappe 80) - Bericht vom 2026-11-19 12:00", full[0])
        assertEquals(listOf("H1", "H2", "", "L1"), full.drop(1))
    }
}
