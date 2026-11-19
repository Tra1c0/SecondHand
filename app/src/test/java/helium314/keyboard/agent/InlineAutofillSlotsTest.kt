// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 79 (W-b): the row of chips keeps the service's order whatever the order of the callbacks. */
class InlineAutofillSlotsTest {
    /** The row as the container would hold it: apply the insert positions the way `container.addView(view, position)` does. */
    private class Row(count: Int) {
        val slots = InlineAutofillSlots(count)
        val row = ArrayList<Int>()
        fun arrive(index: Int, hasView: Boolean = true) {
            val position = slots.onResult(index, hasView)
            if (position >= 0) row.add(position, index)
        }
    }

    @Test fun inOrderArrivalAppendsEachChip() {
        val r = Row(3)
        r.arrive(0); r.arrive(1); r.arrive(2)
        assertEquals(listOf(0, 1, 2), r.row)
        assertTrue(r.slots.isComplete)
    }

    @Test fun reverseArrivalStillGivesTheServicesOrder() {
        val r = Row(3)
        r.arrive(2); r.arrive(1); r.arrive(0)
        assertEquals(listOf(0, 1, 2), r.row)
    }

    @Test fun everyOrderOfFourCallbacksGivesTheServicesOrder() {
        fun permutations(items: List<Int>): List<List<Int>> =
            if (items.size <= 1) listOf(items) else items.flatMap { first -> permutations(items - first).map { listOf(first) + it } }
        for (order in permutations(listOf(0, 1, 2, 3))) {
            val r = Row(4)
            order.forEach { r.arrive(it) }
            assertEquals(listOf(0, 1, 2, 3), r.row, "arrival order $order")
        }
    }

    @Test fun aFailedInflationLeavesNoGapAndBlocksNoOne() {
        val r = Row(3)
        r.arrive(2); r.arrive(1, hasView = false); r.arrive(0)
        assertEquals(listOf(0, 2), r.row)
        assertTrue(r.slots.isComplete)
    }

    @Test fun aChipThatNeverArrivesDoesNotHoldBackTheOthers() {
        val r = Row(3)
        r.arrive(2); r.arrive(0)
        assertEquals(listOf(0, 2), r.row)
        assertFalse(r.slots.isComplete)
        // and when it comes late, it still lands between them
        r.arrive(1)
        assertEquals(listOf(0, 1, 2), r.row)
    }

    @Test fun aSecondCallbackForTheSameIndexIsIgnored() {
        val r = Row(2)
        r.arrive(1); r.arrive(1); r.arrive(0)
        assertEquals(listOf(0, 1), r.row)
        assertEquals(2, r.slots.placed)
    }

    @Test fun aFailureCannotBeTurnedIntoAChipByALateDuplicate() {
        val r = Row(2)
        r.arrive(0, hasView = false); r.arrive(0, hasView = true); r.arrive(1)
        assertEquals(listOf(1), r.row)
    }

    @Test fun unknownIndexesAreIgnored() {
        val r = Row(2)
        r.arrive(-1); r.arrive(2); r.arrive(7)
        assertTrue(r.row.isEmpty())
        assertFalse(r.slots.isComplete)
    }

    @Test fun emptyResponseIsComplete() {
        assertTrue(InlineAutofillSlots(0).isComplete)
        assertEquals(-1, InlineAutofillSlots(0).onResult(0, true))
    }
}
