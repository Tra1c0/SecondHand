// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import dev.agentkeyboard.core.PendingBatchGate
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.SuggestedWords
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BatchDeliveryTest {
    private val ui = mock(LatinIME.UIHandler::class.java)
    private val logic = mock(InputLogic::class.java)
    private val handler = InputLogicHandler(ui, logic)
    private val words = mock(SuggestedWords::class.java)
    private val gate: PendingBatchGate get() = ReflectionHelpers.getField(handler, "mBatchGate")

    @After fun close() {
        handler.reset()
        handler.mNonUIThreadHandler.looper.quitSafely()
    }

    private fun deliverTail(generation: Long) {
        ReflectionHelpers.callInstanceMethod<Unit>(handler, "showGestureSuggestionsWithPreviewVisuals",
            ClassParameter.from(SuggestedWords::class.java, words),
            ClassParameter.from(Boolean::class.javaPrimitiveType, true),
            ClassParameter.from(Long::class.javaPrimitiveType, generation))
    }

    @Test fun postedTailKeepsAgentActionsBlockedUntilUiConsumption() {
        handler.onStartBatchInput()
        val generation = gate.generation()
        deliverTail(generation)
        verify(ui).showTailBatchInputResult(words, generation)
        assertFalse(handler.isInBatchInput)
        assertTrue(handler.hasPendingBatchInput())
        assertTrue(handler.acceptsTail(generation))
        handler.completeTail(generation)
        assertFalse(handler.hasPendingBatchInput())
    }

    @Test fun runningWorkerCannotPostAfterEditorReset() {
        handler.onStartBatchInput()
        val generation = gate.generation()
        handler.reset()
        deliverTail(generation)
        verify(ui, never()).showTailBatchInputResult(words, generation)
        assertFalse(handler.acceptsTail(generation))
        assertFalse(handler.hasPendingBatchInput())
        verify(ui, atLeastOnce()).cancelPendingBatchInput()
    }

    @Test fun queuedOldTailCannotBeAcceptedAfterNewGestureStarts() {
        handler.onStartBatchInput()
        val generation = gate.generation()
        deliverTail(generation)
        handler.onStartBatchInput()
        assertFalse(handler.acceptsTail(generation))
        handler.completeTail(generation)
        assertTrue(handler.hasPendingBatchInput())
    }
}
