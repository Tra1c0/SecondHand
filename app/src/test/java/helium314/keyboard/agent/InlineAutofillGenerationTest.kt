// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Stage 79 (W-c): responses and late inflation callbacks of an older field or a finished input do nothing. */
class InlineAutofillGenerationTest {
    private fun started() = InlineAutofillGeneration().also { it.open() }

    @Test fun aResponseInAnActiveInputIsCurrent() {
        val g = started()
        val token = g.beginResponse()
        assertNotEquals(InlineAutofillGeneration.NONE, token)
        assertTrue(g.isCurrent(token))
    }

    @Test fun noResponseIsAcceptedBeforeAnyInputStarted() {
        val g = InlineAutofillGeneration()
        assertEquals(InlineAutofillGeneration.NONE, g.beginResponse())
        assertFalse(g.isCurrent(InlineAutofillGeneration.NONE))
        assertFalse(g.isCurrent(0))
    }

    @Test fun aResponseAfterTheInputFinishedIsRefused() {
        val g = started()
        g.close()
        assertEquals(InlineAutofillGeneration.NONE, g.beginResponse())
    }

    @Test fun aCallbackInFlightWhenTheInputFinishesIsStale() {
        val g = started()
        val token = g.beginResponse()
        g.close()
        assertFalse(g.isCurrent(token))
    }

    @Test fun aCallbackOfTheOldFieldIsStaleOnceTheNextFieldStarted() {
        val g = started()
        val old = g.beginResponse()
        g.close()
        g.open() // the next field
        assertFalse(g.isCurrent(old))
        val fresh = g.beginResponse()
        assertTrue(g.isCurrent(fresh))
        assertFalse(g.isCurrent(old))
    }

    // Changed after the device run of stage 79 (two of three chips): open() used to make the response in flight stale.
    @Test fun aRestartOfTheSameInputKeepsTheResponseInFlightCurrent() {
        val g = started()
        val token = g.beginResponse()
        g.open() // onStartInputInternal(restarting = true), no finish in between
        assertTrue(g.isCurrent(token))
    }

    @Test fun aSecondRequestForTheSameFieldKeepsTheResponseInFlightCurrent() {
        val g = started()
        val token = g.beginResponse()
        g.open() // onCreateInlineSuggestionsRequest again
        g.open()
        assertTrue(g.isCurrent(token))
        assertTrue(g.isCurrent(g.beginResponse()))
    }

    @Test fun lateCallbacksOfAValidResponseStillAddTheirChipsAfterARestart() {
        val g = started()
        val slots = InlineAutofillSlots(3)
        val token = g.beginResponse()
        val row = ArrayList<Int>()
        fun callback(index: Int) {
            if (!g.isCurrent(token)) return
            val position = slots.onResult(index, true)
            if (position >= 0) row.add(position, index)
        }
        callback(0)
        g.open() // restart or second request while the others are still being inflated
        callback(2)
        callback(1)
        assertEquals(listOf(0, 1, 2), row)
    }

    @Test fun aFieldThatStartsAfterAFinishMakesOldCallbacksStale() {
        val g = started()
        val old = g.beginResponse()
        g.close()
        g.open()
        assertFalse(g.isCurrent(old))
    }

    @Test fun aNewerResponseMakesTheOlderOneStaleButNotItself() {
        val g = started()
        val first = g.beginResponse()
        val second = g.beginResponse()
        assertFalse(g.isCurrent(first))
        assertTrue(g.isCurrent(second))
    }

    @Test fun closingTheChipsMakesInFlightCallbacksStaleButKeepsAcceptingResponses() {
        val g = started()
        val token = g.beginResponse()
        g.invalidate()
        assertFalse(g.isCurrent(token))
        assertTrue(g.isCurrent(g.beginResponse()))
    }

    @Test fun staleCallbackAddsNothingToTheRow() {
        // what InlineAutofillUtils.createView does per callback: current check first, then the slot bookkeeping
        val g = started()
        val slots = InlineAutofillSlots(2)
        val token = g.beginResponse()
        val row = ArrayList<Int>()
        fun callback(index: Int) {
            if (!g.isCurrent(token)) return
            val position = slots.onResult(index, true)
            if (position >= 0) row.add(position, index)
        }
        callback(1)
        g.close() // the field changed while the other chip was still being inflated
        callback(0)
        assertEquals(listOf(1), row)
    }
}
