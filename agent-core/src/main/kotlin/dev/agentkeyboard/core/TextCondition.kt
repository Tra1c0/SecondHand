// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

enum class TextPredicate { NON_EMPTY, CONTAINS_LITERAL, LENGTH_AT_LEAST }

/** Literal, deterministic predicates; no regex, scripting or model-selected capabilities. */
data class TextCondition(
    val predicate: TextPredicate,
    val literal: String = "",
    val minimumLength: Int = 0,
    val negated: Boolean = false,
) {
    init {
        when (predicate) {
            TextPredicate.NON_EMPTY -> require(literal.isEmpty() && minimumLength == 0)
            TextPredicate.CONTAINS_LITERAL -> {
                require(literal.length in 1..128 && minimumLength == 0)
                require(ExecutionPolicy().resultDenial(literal) == null)
                require(literal.none { (it.isISOControl() && it != '\n' && it != '\t') ||
                    it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it in '\u200E'..'\u200F' || it == '\u061C' })
            }
            TextPredicate.LENGTH_AT_LEAST -> require(literal.isEmpty() && minimumLength in 1..8192)
        }
    }

    fun matches(input: String): Boolean {
        val matches = when (predicate) {
            TextPredicate.NON_EMPTY -> input.isNotEmpty()
            TextPredicate.CONTAINS_LITERAL -> input.contains(literal, ignoreCase = false)
            TextPredicate.LENGTH_AT_LEAST -> input.length >= minimumLength
        }
        return if (negated) !matches else matches
    }

    override fun toString() = "TextCondition(predicate=$predicate, minimumLength=$minimumLength, negated=$negated)"
}
