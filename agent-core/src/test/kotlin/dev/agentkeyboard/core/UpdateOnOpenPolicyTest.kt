// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class UpdateOnOpenPolicyTest {
    private val minute = 60_000L

    @Test fun atMostEveryFifteenMinutesSinceTheLastAttempt() {
        assertTrue(UpdateOnOpenPolicy.due(now = 1_000, lastAttempt = 0, failures = 0), "never tried")
        val last = 1_000_000_000L
        assertFalse(UpdateOnOpenPolicy.due(last, last, 0))
        assertFalse(UpdateOnOpenPolicy.due(last + 14 * minute + 59_999, last, 0))
        assertTrue(UpdateOnOpenPolicy.due(last + 15 * minute, last, 0))
        assertTrue(UpdateOnOpenPolicy.due(last + 60 * minute, last, 0))
    }

    @Test fun eachFailureDoublesTheWaitUpToFourHours() {
        assertEquals(listOf(15L, 30, 60, 120, 240, 240, 240), (0..6).map { UpdateOnOpenPolicy.interval(it) / minute })
        assertEquals(15L, UpdateOnOpenPolicy.interval(-3) / minute)
        assertEquals(240L, UpdateOnOpenPolicy.interval(Int.MAX_VALUE) / minute)
        val last = 5_000_000_000L
        assertFalse(UpdateOnOpenPolicy.due(last + 29 * minute, last, failures = 1))
        assertTrue(UpdateOnOpenPolicy.due(last + 30 * minute, last, failures = 1))
        assertFalse(UpdateOnOpenPolicy.due(last + 239 * minute, last, failures = 9))
        assertTrue(UpdateOnOpenPolicy.due(last + 240 * minute, last, failures = 9))
    }

    @Test fun aSuccessResetsTheBackoffAndFailuresStopCountingAtTheCap() {
        assertEquals(1, UpdateOnOpenPolicy.failuresAfter(0, success = false))
        assertEquals(2, UpdateOnOpenPolicy.failuresAfter(1, success = false))
        assertEquals(UpdateOnOpenPolicy.MAX_FAILURES, UpdateOnOpenPolicy.failuresAfter(UpdateOnOpenPolicy.MAX_FAILURES, success = false))
        assertEquals(UpdateOnOpenPolicy.MAX_FAILURES, UpdateOnOpenPolicy.failuresAfter(Int.MAX_VALUE, success = false))
        assertEquals(1, UpdateOnOpenPolicy.failuresAfter(-5, success = false))
        assertEquals(0, UpdateOnOpenPolicy.failuresAfter(3, success = true))
        // a failure then a success: the next check is due after 15 minutes again
        val failures = UpdateOnOpenPolicy.failuresAfter(UpdateOnOpenPolicy.failuresAfter(0, false), true)
        assertTrue(UpdateOnOpenPolicy.due(15 * minute + 1, 1, failures))
    }

    @Test fun aClockSetBackAllowsOneCheck() {
        assertTrue(UpdateOnOpenPolicy.due(now = 1_000, lastAttempt = 10_000_000, failures = 0))
    }

    @Test fun onlyAPlainETagIsKept() {
        assertEquals("W/\"0f1e2d\"", UpdateOnOpenPolicy.etag("W/\"0f1e2d\""))
        assertEquals("\"abc\"", UpdateOnOpenPolicy.etag("\"abc\""))
        for (bad in listOf(null, "", "\"a b\"", "\"ä\"", "\"a\"\n", "\"a\"\r\nX-Evil: 1", "\"" + "a".repeat(UpdateOnOpenPolicy.MAX_ETAG_LENGTH) + "\""))
            assertNull(UpdateOnOpenPolicy.etag(bad), bad)
        assertNotNull(UpdateOnOpenPolicy.etag("a".repeat(UpdateOnOpenPolicy.MAX_ETAG_LENGTH)))
    }
}
