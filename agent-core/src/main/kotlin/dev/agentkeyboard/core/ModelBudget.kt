// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.max

/**
 * Stage 59: the size and time budgets of one model call, in one place so that the transport, the
 * providers, the runner and the tests agree. No I/O.
 */
object ModelBudget {
    /** Instruction limit of one request (unchanged). */
    const val MAX_INSTRUCTION_CHARS = 4096
    /** Text a request may carry as input, UTF-16 chars; the default of [ExecutionPolicy.maxSelectionChars]. */
    const val MAX_INPUT_CHARS = 32_768
    /** One result, UTF-16 chars; the default of [ExecutionPolicy.maxResultChars]. */
    const val MAX_RESULT_CHARS = 65_536

    /** Floor, ceiling and base of the output budget of a text request, in tokens. */
    const val MIN_OUTPUT_TOKENS = 4096
    const val MAX_OUTPUT_TOKENS = 16_384
    private const val BASE_OUTPUT_TOKENS = 2048
    /** Providers whose cap also counts thinking tokens never get less than this (Anthropic, Gemini). */
    const val THINKING_PROVIDER_MIN_TOKENS = 8192
    /** A workflow draft keeps its own, smaller budget: a bounded JSON. */
    const val WORKFLOW_DRAFT_TOKENS = 4096

    /** Time budgets in milliseconds: the wait for the answer (no streaming), one call, one whole workflow. */
    const val READ_TIMEOUT_MS = 120_000L
    const val CALL_TIMEOUT_MS = 150_000L
    const val WORKFLOW_TIMEOUT_MS = 310_000L

    /**
     * The output tokens a TEXT request of [inputChars] characters may use (reasoning tokens count
     * against it): `clamp(inputChars / 2 + 2048, 4096, 16384)`.
     */
    fun outputTokens(inputChars: Int): Int =
        (max(0, inputChars).toLong() / 2 + BASE_OUTPUT_TOKENS)
            .coerceIn(MIN_OUTPUT_TOKENS.toLong(), MAX_OUTPUT_TOKENS.toLong()).toInt()

    /** The same for the providers whose cap counts thinking tokens too: at least [THINKING_PROVIDER_MIN_TOKENS]. */
    fun thinkingProviderTokens(inputChars: Int): Int = max(THINKING_PROVIDER_MIN_TOKENS, outputTokens(inputChars))

    /** The output cap a [request] sends to an OpenAI-style provider. */
    fun outputTokens(request: ModelRequest): Int =
        if (request.outputFormat == ModelOutputFormat.WORKFLOW_DRAFT) WORKFLOW_DRAFT_TOKENS else outputTokens(request.input.length)

    /** The output cap a [request] sends to Anthropic and Gemini. */
    fun thinkingProviderTokens(request: ModelRequest): Int =
        if (request.outputFormat == ModelOutputFormat.WORKFLOW_DRAFT) WORKFLOW_DRAFT_TOKENS
        else thinkingProviderTokens(request.input.length)
}
