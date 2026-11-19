package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTextTest {
    @Test fun sdkExceptionShowsClassCodeTextAndStep() {
        val e = NpuException(-7, "PARAM_NOT_SUPPORTED: nCtx", "com.geniex.sdk.ModelManagerWrapper\$GenieXModelError")
        val lines = ErrorText.describe("Modell laden", e)
        assertEquals("FEHLER bei Schritt: Modell laden", lines[0])
        assertTrue(lines.contains("  Klasse: com.geniex.sdk.ModelManagerWrapper\$GenieXModelError"))
        assertTrue(lines.contains("  SDK-Code: -7"))
        assertTrue(lines.contains("  SDK-Text: PARAM_NOT_SUPPORTED: nCtx"))
        assertTrue(lines.any { it.startsWith("  Hinweis (Vermutung der App):") && "nCtx" in it })
    }

    @Test fun plainThrowableShowsItsOwnClassAndNoCode() {
        val lines = ErrorText.describe("X", IllegalStateException("kaputt"))
        assertTrue(lines.contains("  Klasse: java.lang.IllegalStateException"))
        assertFalse(lines.any { it.contains("SDK-Code") })
        assertTrue(lines.contains("  SDK-Text: kaputt"))
    }

    @Test fun missingTextIsSaidSo() {
        assertTrue(ErrorText.describe("X", RuntimeException()).contains("  SDK-Text: (keiner)"))
    }

    @Test fun missingNpuLibrariesAreReportedAsUnsupportedDevice() {
        val t = UnsatisfiedLinkError("dlopen failed: library libnpu_jni.so not found")
        assertTrue(ErrorText.isUnsupportedDevice(t))
        assertEquals("Auf diesem Gerät nicht unterstützt: java.lang.UnsatisfiedLinkError", ErrorText.unsupportedDeviceLine(t))
        // the adapter wraps it; the original class must survive
        val wrapped = NpuException(null, t.message, t.javaClass.name, t)
        assertEquals("Auf diesem Gerät nicht unterstützt: java.lang.UnsatisfiedLinkError", ErrorText.unsupportedDeviceLine(wrapped))
        assertTrue(ErrorText.describe("GenieX-SDK starten", wrapped).contains("  Auf diesem Gerät nicht unterstützt: java.lang.UnsatisfiedLinkError"))
    }

    @Test fun unsupportedClassInTheCauseChainCounts() {
        assertTrue(ErrorText.isUnsupportedDevice(RuntimeException("x", NoClassDefFoundError("com/geniex/sdk/GenieXSdk"))))
        assertFalse(ErrorText.isUnsupportedDevice(IllegalArgumentException("x")))
    }

    @Test fun pullErrorEventHasCodeAndMessage() {
        val lines = ErrorText.describePull("Modell herunterladen", 12, "Requested chipset not available for this model")
        assertTrue(lines.contains("  SDK-Code: 12"))
        assertTrue(lines.contains("  SDK-Text: Requested chipset not available for this model"))
        assertTrue(lines.any { "kein fertiges Modellpaket" in it })
    }

    @Test fun causesAreListed() {
        val lines = ErrorText.describe("X", RuntimeException("outer", java.io.IOException("No space left on device")))
        assertTrue(lines.contains("  Ursache: java.io.IOException: No space left on device"))
    }

    @Test fun knownSdkCodesGetTheirNameAndAHint() {
        val lines = ErrorText.describePull("Modell herunterladen", -100015, "geniex_model_pull failed (rc=-100015)")
        assertTrue(lines.contains("  SDK-Code: -100015 (COMMON_CHIPSET_UNAVAILABLE laut GenieX-Quelltext)"))
        assertTrue(lines.any { it.contains("SM8750-AC") && it.contains("Hinweis") })
    }

    @Test fun shortErrorCombinesCodeNameAndText() {
        assertEquals("-100005 (COMMON_NETWORK): timed out", ErrorText.shortError(-100005, "timed out", "X"))
        assertEquals("-7: boom", ErrorText.shortError(-7, "boom", "X"))
        assertEquals("boom", ErrorText.shortError(null, "boom", "X"))
        assertEquals("java.io.IOException", ErrorText.shortError(null, " ", "java.io.IOException"))
    }
}
