// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorrectionWeightsTest {
    private val day = 86_400_000L

    @Test fun `the owner's examples`() {
        assertFalse(CorrectionWeights.blocks(10.0, 1.0)) // one rejection after many confirmations does not tip it
        assertTrue(CorrectionWeights.blocks(2.0, 2.0)) // several do
        assertFalse(CorrectionWeights.blocks(6.0, 2.0)) // four more picks of the correction: applied again
        assertTrue(CorrectionWeights.blocks(0.0, 1.0)) // one rejection without confirmations: held back
        assertTrue(CorrectionWeights.blocks(1.0, 1.0)) // the automatic commit itself and one Backspace
        assertFalse(CorrectionWeights.blocks(3.0, 1.0))
        assertFalse(CorrectionWeights.blocks(5.0, 0.0)) // never taken back
    }

    @Test fun `weights fade, nothing is final`() {
        assertEquals(1.0, CorrectionWeights.faded(1.0, 0), 1e-9)
        assertEquals(0.5, CorrectionWeights.faded(1.0, 30 * day), 1e-9)
        assertEquals(0.25, CorrectionWeights.faded(1.0, 60 * day), 1e-9)
        assertTrue(CorrectionWeights.faded(1.0, 61 * day) < CorrectionWeights.FORGET_BELOW)
        // fading both keeps the balance; forgetting a faded rejection ends the hold
        assertTrue(CorrectionWeights.blocks(CorrectionWeights.faded(2.0, 10 * day), CorrectionWeights.faded(2.0, 10 * day)))
    }
}
