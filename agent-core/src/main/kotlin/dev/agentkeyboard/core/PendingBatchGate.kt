// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/** Tracks a swipe through the worker-to-UI handoff, until the UI actually consumes its tail. */
class PendingBatchGate {
    private enum class Phase { IDLE, RUNNING, TAIL_POSTED }
    private var generation = 0L
    private var phase = Phase.IDLE

    @Synchronized fun start() { generation++; phase = Phase.RUNNING }
    @Synchronized fun cancel() { generation++; phase = Phase.IDLE }
    @Synchronized fun generation() = generation
    @Synchronized fun isRunning() = phase == Phase.RUNNING
    @Synchronized fun hasPending() = phase != Phase.IDLE
    @Synchronized fun acceptsUpdate(candidate: Long) = generation == candidate && phase == Phase.RUNNING
    @Synchronized fun acceptsTail(candidate: Long) = generation == candidate && phase == Phase.TAIL_POSTED

    @Synchronized fun postTail(candidate: Long): Boolean {
        if (!acceptsUpdate(candidate)) return false
        phase = Phase.TAIL_POSTED
        return true
    }

    @Synchronized fun complete(candidate: Long) {
        if (acceptsTail(candidate)) phase = Phase.IDLE
    }
}
