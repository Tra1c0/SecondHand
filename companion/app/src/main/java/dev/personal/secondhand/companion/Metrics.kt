package dev.personal.secondhand.companion

import java.util.Locale

// Pure arithmetic and text comparison of the spike (stage 80). No Android, no SDK: unit-tested.
object Metrics {
    /** tokens / seconds, or null when there is nothing meaningful to divide by. */
    fun tokensPerSecond(tokens: Long, millis: Double): Double? {
        if (tokens <= 0 || millis <= 0.0 || millis.isNaN() || millis.isInfinite()) return null
        return tokens * 1000.0 / millis
    }

    /**
     * The speed the app measured itself: the first token only ends the wait for the answer, so the
     * speed counts the tokens after it, over the time from the first to the last token.
     */
    fun appDecodeSpeed(tokenCount: Long, firstTokenAtMs: Long, lastTokenAtMs: Long): Double? =
        tokensPerSecond(tokenCount - 1, (lastTokenAtMs - firstTokenAtMs).toDouble())

    /** Below this the NPU is unlikely (Qualcomm's own table says about 16 to 21 tokens/s on the Galaxy chip). */
    const val SUSPICIOUSLY_SLOW_TOKENS_PER_SECOND = 8.0

    fun removeThinkBlock(text: String): String =
        text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")

    fun hasThinkBlock(text: String): Boolean = text.contains("<think>") || text.contains("</think>")

    /** Whitespace at the edges and runs of whitespace do not count as a difference; everything else does. */
    fun normalized(text: String): String = removeThinkBlock(text).trim().replace(Regex("\\s+"), " ")

    fun sameAsExpected(answer: String, expected: String): Boolean = normalized(answer) == normalized(expected)

    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    /** German number format for the report (decimal comma), fixed so tests do not depend on the device locale. */
    fun num(value: Double, digits: Int = 1): String = String.format(Locale.GERMAN, "%.${digits}f", value)

    fun numOrDash(value: Double?, digits: Int = 1): String = if (value == null) "-" else num(value, digits)

    fun mb(bytes: Long): String = num(bytes / 1048576.0, 0) + " MB"

    fun gb(bytes: Long): String = num(bytes / 1073741824.0, 1) + " GB"
}
