// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.test.*

class ModelBudgetTest {
    @Test fun outputTokensScaleWithTheInputAndStayInsideTheClamp() {
        // review of stage 59: the floor is 8192 for every provider (reasoning tokens count everywhere)
        assertEquals(8192, ModelBudget.outputTokens(0))
        assertEquals(8192, ModelBudget.outputTokens(100))
        assertEquals(8192, ModelBudget.outputTokens(4096))
        assertEquals(8192, ModelBudget.outputTokens(8192)) // 6144 -> floor
        assertEquals(8192, ModelBudget.outputTokens(12288)) // exactly 8192
        assertEquals(10240, ModelBudget.outputTokens(16384))
        assertEquals(16384, ModelBudget.outputTokens(28672)) // 14336 + 2048
        assertEquals(16384, ModelBudget.outputTokens(ModelBudget.MAX_INPUT_CHARS))
        assertEquals(16384, ModelBudget.outputTokens(Int.MAX_VALUE))
        assertEquals(8192, ModelBudget.outputTokens(-5))
    }

    @Test fun outputTokensNeverDecreaseWithMoreInput() {
        var last = 0
        for (chars in 0..40_000 step 97) { val now = ModelBudget.outputTokens(chars); assertTrue(now >= last); last = now }
    }

    @Test fun allProvidersGetTheSameBudgetOfAtLeastEightThousandTokensAndNeverMoreThanSixteenThousand() {
        assertEquals(8192, ModelBudget.thinkingProviderTokens(10))
        assertEquals(8192, ModelBudget.thinkingProviderTokens(10_000)) // 7048 -> 8192
        for (chars in listOf(0, 10, 10_000, 20_000, 32_768, Int.MAX_VALUE)) {
            assertEquals(ModelBudget.outputTokens(chars), ModelBudget.thinkingProviderTokens(chars))
            assertTrue(ModelBudget.outputTokens(chars) in 8192..16_384)
        }
        assertEquals(16384, ModelBudget.thinkingProviderTokens(ModelBudget.MAX_INPUT_CHARS))
        assertEquals(ModelBudget.outputTokens(20_000), ModelBudget.thinkingProviderTokens(20_000))
    }

    @Test fun requestsPickTheirBudgetByOutputFormat() {
        val text = ModelRequest("m", "i", "x".repeat(20_000))
        assertEquals(12_048, ModelBudget.outputTokens(text))
        assertEquals(12_048, ModelBudget.thinkingProviderTokens(text))
        val draft = ModelRequest("m", "i", "x".repeat(20_000), ModelOutputFormat.WORKFLOW_DRAFT)
        assertEquals(4096, ModelBudget.outputTokens(draft))
        assertEquals(4096, ModelBudget.thinkingProviderTokens(draft))
    }

    @Test fun limitsAndBudgetsAreCoherent() {
        val policy = ExecutionPolicy()
        assertEquals(32_768, policy.maxSelectionChars)
        assertEquals(65_536, policy.maxResultChars)
        assertEquals(131_072, WorkflowRunner.MAX_TOTAL_CHARS)
        assertEquals(4096, ModelBudget.MAX_INSTRUCTION_CHARS)
        assertTrue(ModelBudget.READ_TIMEOUT_MS < ModelBudget.CALL_TIMEOUT_MS)
        assertTrue(ModelBudget.CALL_TIMEOUT_MS < ModelBudget.WORKFLOW_TIMEOUT_MS)
        assertEquals(120_000L, ModelBudget.READ_TIMEOUT_MS)
        assertEquals(150_000L, ModelBudget.CALL_TIMEOUT_MS)
        assertEquals(310_000L, ModelBudget.WORKFLOW_TIMEOUT_MS)
    }
}
