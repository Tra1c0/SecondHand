// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class PendingBatchGateTest {
    @Test fun agentWorkRemainsBlockedAfterWorkerStopsAndBeforeUiConsumesTail() {
        val gate = PendingBatchGate()
        gate.start()
        val token = gate.generation()
        assertTrue(gate.isRunning())
        assertTrue(gate.postTail(token))
        assertFalse(gate.isRunning())
        assertTrue(gate.hasPending())
        gate.complete(token)
        assertFalse(gate.hasPending())
    }

    @Test fun canceledWorkerCannotPostAfterReset() {
        val gate = PendingBatchGate()
        gate.start()
        val old = gate.generation()
        gate.cancel()
        assertFalse(gate.acceptsUpdate(old))
        assertFalse(gate.postTail(old))
        assertFalse(gate.hasPending())
    }

    @Test fun queuedTailCannotMutateAfterEditorReset() {
        val gate = PendingBatchGate()
        gate.start()
        val old = gate.generation()
        gate.postTail(old)
        gate.cancel()
        assertFalse(gate.acceptsTail(old))
    }

    @Test fun oldCompletionCannotUnblockANewerSwipe() {
        val gate = PendingBatchGate()
        gate.start()
        val old = gate.generation()
        gate.postTail(old)
        gate.start()
        gate.complete(old)
        assertTrue(gate.hasPending())
        assertFalse(gate.acceptsTail(old))
    }
}
