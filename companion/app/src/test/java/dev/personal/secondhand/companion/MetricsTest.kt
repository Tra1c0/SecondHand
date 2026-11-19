package dev.personal.secondhand.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsTest {
    @Test fun tokensPerSecondIsTokensOverSeconds() {
        assertEquals(20.0, Metrics.tokensPerSecond(100, 5000.0)!!, 1e-9)
        assertEquals(2.5, Metrics.tokensPerSecond(5, 2000.0)!!, 1e-9)
    }

    @Test fun tokensPerSecondRefusesNonsense() {
        assertNull(Metrics.tokensPerSecond(0, 1000.0))
        assertNull(Metrics.tokensPerSecond(10, 0.0))
        assertNull(Metrics.tokensPerSecond(10, -5.0))
        assertNull(Metrics.tokensPerSecond(10, Double.NaN))
    }

    @Test fun appDecodeSpeedDoesNotCountTheFirstToken() {
        // 11 tokens, first at t=300, last at t=800: 10 tokens in 0.5 s = 20 tokens/s
        assertEquals(20.0, Metrics.appDecodeSpeed(11, 300, 800)!!, 1e-9)
        assertNull(Metrics.appDecodeSpeed(1, 300, 300))
        assertNull(Metrics.appDecodeSpeed(5, 300, 300))
    }

    @Test fun sameAsExpectedIgnoresEdgesAndWhitespaceRuns() {
        val expected = "Wir haben gestern einen Spaziergang gemacht."
        assertTrue(Metrics.sameAsExpected("  Wir haben  gestern einen\nSpaziergang gemacht.\n", expected))
        assertFalse(Metrics.sameAsExpected("Wir haben gestern einen Spatziergang gemacht.", expected))
        assertFalse(Metrics.sameAsExpected("Wir haben gestern einen Spaziergang gemacht", expected))
    }

    @Test fun anEmptyThinkBlockDoesNotCountAsADifference() {
        val expected = "Das ist richtig."
        assertTrue(Metrics.sameAsExpected("<think>\n\n</think>\n\nDas ist richtig.", expected))
        assertTrue(Metrics.hasThinkBlock("<think>\n\n</think>\n\nDas ist richtig."))
        assertFalse(Metrics.hasThinkBlock(expected))
    }

    @Test fun numbersUseTheGermanDecimalComma() {
        assertEquals("20,5", Metrics.num(20.46))
        assertEquals("-", Metrics.numOrDash(null))
        assertEquals("1,5 GB", Metrics.gb(1610612736L))
    }

    @Test fun fixedTestsAreWhatTheSpecSays() {
        val s = FixedTests.SENTENCE
        assertFalse(Metrics.sameAsExpected(s.userText, s.expected!!)) // the input really has typos
        assertTrue(s.userText.contains("Spatziergang") && s.userText.contains("dannach"))
        assertEquals(96, s.maxTokens)
        assertTrue(s.systemInstruction.startsWith("Korrigiere Rechtschreibung und Grammatik."))
        assertTrue(Metrics.wordCount(FixedTests.PARAGRAPH.userText) in 70..95)
    }
}
