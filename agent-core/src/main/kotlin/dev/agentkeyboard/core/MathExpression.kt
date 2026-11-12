// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The calculator of the `@math` command (stage 24b): parses, computes exactly with decimals and
 * prints the calculation in several formats. No `eval`, no scripting, no network.
 *
 * Input: numbers with `,` or `.` as decimal mark (no thousands separators), `+ - * / ^ %`, the
 * signs `× ÷ − ·`, `√` (square root of the following factor), `²` and `³`, and parentheses.
 * `%` divides the number before it by 100.
 */
object MathExpression {
    const val MAX_INPUT = 200
    /** Internal precision of divisions and roots. */
    private val CONTEXT = MathContext(40, RoundingMode.HALF_EVEN)
    /** Digits after the decimal mark that are shown; more is rounded and marked as approximate. */
    const val MAX_FRACTION_DIGITS = 10
    private const val MAX_EXPONENT = 999
    private val MAX_MAGNITUDE = BigDecimal.TEN.pow(100)

    sealed interface Node
    data class Number(val text: String, val value: BigDecimal) : Node
    data class Unary(val operator: Char, val operand: Node) : Node
    data class Binary(val operator: Char, val left: Node, val right: Node) : Node
    data class Group(val inner: Node) : Node
    data class Root(val operand: Node) : Node
    data class Percent(val operand: Node) : Node

    sealed interface Outcome
    /** [exact] is false when the shown value is rounded (e.g. 1/3 or √2). */
    data class Value(val tree: Node, val value: BigDecimal, val exact: Boolean) : Outcome
    data class Failure(val reason: Reason) : Outcome
    enum class Reason { EMPTY, SYNTAX, DIVISION_BY_ZERO, NEGATIVE_ROOT, TOO_LARGE, TOO_LONG }

    private class Fail(val reason: Reason) : Exception(null, null, false, false)

    fun evaluate(input: String): Outcome {
        if (input.length > MAX_INPUT) return Failure(Reason.TOO_LONG)
        if (input.isBlank()) return Failure(Reason.EMPTY)
        return try {
            val tree = Parser(input).parseAll()
            var exact = true
            val value = compute(tree) { exact = false }
            val shown = round(value)
            if (shown.compareTo(value) != 0) exact = false
            Value(tree, shown, exact)
        } catch (e: Fail) {
            Failure(e.reason)
        } catch (e: ArithmeticException) {
            Failure(Reason.TOO_LARGE)
        }
    }

    private fun round(value: BigDecimal): BigDecimal {
        val rounded = if (value.scale() > MAX_FRACTION_DIGITS) value.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_EVEN) else value
        val stripped = rounded.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    private fun compute(node: Node, inexact: () -> Unit): BigDecimal {
        val result = when (node) {
            is Number -> node.value
            is Group -> compute(node.inner, inexact)
            is Unary -> if (node.operator == '-') compute(node.operand, inexact).negate() else compute(node.operand, inexact)
            is Percent -> compute(node.operand, inexact).divide(BigDecimal(100))
            is Root -> squareRoot(compute(node.operand, inexact), inexact)
            is Binary -> {
                val left = compute(node.left, inexact)
                val right = compute(node.right, inexact)
                when (node.operator) {
                    '+' -> left.add(right)
                    '-' -> left.subtract(right)
                    '*' -> left.multiply(right)
                    '/' -> divide(left, right, inexact)
                    '^' -> power(left, right, inexact)
                    else -> throw Fail(Reason.SYNTAX)
                }
            }
        }
        if (result.abs() > MAX_MAGNITUDE) throw Fail(Reason.TOO_LARGE)
        return result
    }

    private fun divide(left: BigDecimal, right: BigDecimal, inexact: () -> Unit): BigDecimal {
        if (right.signum() == 0) throw Fail(Reason.DIVISION_BY_ZERO)
        return try { left.divide(right) } catch (e: ArithmeticException) {
            inexact() // no finite decimal: 1/3
            left.divide(right, CONTEXT)
        }
    }

    private fun power(base: BigDecimal, exponent: BigDecimal, inexact: () -> Unit): BigDecimal {
        val whole = try { exponent.intValueExact() } catch (e: ArithmeticException) {
            // a fractional exponent: only roots of non-negative bases, computed in double precision
            if (exponent.abs() > BigDecimal(MAX_EXPONENT)) throw Fail(Reason.TOO_LARGE)
            if (base.signum() < 0) throw Fail(Reason.NEGATIVE_ROOT)
            inexact()
            val result = Math.pow(base.toDouble(), exponent.toDouble())
            if (result.isNaN() || result.isInfinite()) throw Fail(Reason.TOO_LARGE)
            return BigDecimal(result, MathContext(15))
        }
        if (kotlin.math.abs(whole) > MAX_EXPONENT) throw Fail(Reason.TOO_LARGE)
        if (whole >= 0) {
            // estimate the size first: 10^100^999 must not be computed
            if (base.abs() > BigDecimal.ONE && (base.precision() - base.scale() - 1).toLong() * whole > 100) throw Fail(Reason.TOO_LARGE)
            return base.pow(whole)
        }
        if (base.signum() == 0) throw Fail(Reason.DIVISION_BY_ZERO)
        return divide(BigDecimal.ONE, power(base, BigDecimal(-whole), inexact), inexact)
    }

    /** Newton's method in decimals; BigDecimal.sqrt needs Android 13. */
    private fun squareRoot(value: BigDecimal, inexact: () -> Unit): BigDecimal {
        if (value.signum() < 0) throw Fail(Reason.NEGATIVE_ROOT)
        if (value.signum() == 0) return BigDecimal.ZERO
        var x = BigDecimal(Math.sqrt(value.toDouble()), CONTEXT)
        if (x.signum() == 0) x = BigDecimal.ONE
        val two = BigDecimal(2)
        repeat(100) {
            val next = x.add(value.divide(x, CONTEXT)).divide(two, CONTEXT)
            if (next.compareTo(x) == 0) return@repeat
            x = next
        }
        val rounded = x.round(MathContext(30))
        // exact when the rounded root squares back to the input (√16, √2,25)
        val stripped = rounded.stripTrailingZeros()
        if (stripped.multiply(stripped).compareTo(value) == 0) return stripped
        inexact()
        return x
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseAll(): Node {
            val node = sum()
            skipSpace()
            if (index != text.length) throw Fail(Reason.SYNTAX)
            return node
        }

        private fun skipSpace() { while (index < text.length && text[index].isWhitespace()) index++ }

        private fun peek(): Char? { skipSpace(); return text.getOrNull(index) }

        private fun sum(): Node {
            var node = product()
            while (true) {
                val op = when (peek()) { '+' -> '+'; '-', '−' -> '-'; else -> return node }
                index++
                node = Binary(op, node, product())
            }
        }

        private fun product(): Node {
            var node = unary()
            while (true) {
                val op = when (peek()) { '*', '×', '·' -> '*'; '/', '÷', ':' -> '/'; else -> return node }
                index++
                node = Binary(op, node, unary())
            }
        }

        private fun unary(): Node = when (peek()) {
            '-', '−' -> { index++; Unary('-', unary()) }
            '+' -> { index++; Unary('+', unary()) }
            else -> power()
        }

        /** Right-associative: 2^3^2 = 2^9. A negative exponent is allowed: 2^-1. */
        private fun power(): Node {
            val base = postfix()
            if (peek() == '^') {
                index++
                return Binary('^', base, unary())
            }
            return base
        }

        private fun postfix(): Node {
            var node = atom()
            while (true) {
                node = when (peek()) {
                    '%' -> { index++; Percent(node) }
                    '²' -> { index++; Binary('^', node, Number("2", BigDecimal(2))) }
                    '³' -> { index++; Binary('^', node, Number("3", BigDecimal(3))) }
                    else -> return node
                }
            }
        }

        private fun atom(): Node {
            val char = peek() ?: throw Fail(Reason.SYNTAX)
            return when {
                char == '(' -> {
                    index++
                    val inner = sum()
                    if (peek() != ')') throw Fail(Reason.SYNTAX)
                    index++
                    Group(inner)
                }
                char == '√' -> { index++; Root(postfix()) }
                char.isDigit() || char == ',' || char == '.' -> number()
                else -> throw Fail(Reason.SYNTAX)
            }
        }

        private fun number(): Node {
            val start = index
            var marks = 0
            while (index < text.length && (text[index].isDigit() || text[index] == ',' || text[index] == '.')) {
                if (!text[index].isDigit()) marks++
                index++
            }
            val raw = text.substring(start, index)
            if (marks > 1 || raw == "," || raw == "." || raw.count { it.isDigit() } > 60) throw Fail(Reason.SYNTAX)
            val normalized = raw.replace(',', '.').let { if (it.startsWith('.')) "0$it" else it }.trimEnd('.')
            val value = try { BigDecimal(normalized) } catch (e: NumberFormatException) { throw Fail(Reason.SYNTAX) }
            return Number(raw, value)
        }
    }

    // ---------- output ----------

    enum class Style { EQUATION, RESULT, LATEX, UNICODE }

    /** Decimal mark and digit grouping of the language the user types in. */
    data class Locale(val decimalMark: Char, val groupMark: Char) {
        companion object {
            val COMMA = Locale(',', '.')
            val POINT = Locale('.', ',')
            /** German, Spanish and most of Europe write 3,5; English writes 3.5. */
            fun forLanguage(language: String): Locale = if (language.lowercase() in POINT_LANGUAGES) POINT else COMMA
            private val POINT_LANGUAGES = setOf("en", "ja", "zh", "ko", "hi", "th", "he", "ga", "mt")
        }
    }

    /** The text to insert for [value] in the given [style]. */
    fun format(value: Value, style: Style, locale: Locale): String {
        val result = formatNumber(value.value, locale, style == Style.LATEX)
        val equals = if (value.exact) "=" else if (style == Style.LATEX) "\\approx" else "≈"
        return when (style) {
            Style.RESULT -> result
            Style.EQUATION -> "${plain(value.tree, locale, unicode = false)} $equals $result"
            Style.UNICODE -> "${plain(value.tree, locale, unicode = true)} $equals $result"
            Style.LATEX -> "${latex(value.tree, locale)} $equals $result"
        }
    }

    /** Digits grouped by three; at most [MAX_FRACTION_DIGITS] after the mark. */
    fun formatNumber(value: BigDecimal, locale: Locale, latex: Boolean = false): String {
        val plain = value.abs().toPlainString()
        val whole = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "")
        val grouped = if (whole.length > 4) whole.reversed().chunked(3).joinToString(groupSeparator(locale, latex)) { it }.reversed()
            else whole
        val mark = if (latex && locale.decimalMark == ',') "{,}" else locale.decimalMark.toString()
        val sign = if (value.signum() < 0) (if (latex) "-" else "−") else ""
        return sign + grouped + (if (fraction.isEmpty()) "" else mark + fraction)
    }

    private fun groupSeparator(locale: Locale, latex: Boolean): String {
        // reversed: the separator text is reversed again with the digits
        val separator = when {
            latex -> "\\,"
            else -> locale.groupMark.toString()
        }
        return separator.reversed()
    }

    private fun plain(node: Node, locale: Locale, unicode: Boolean): String = when (node) {
        is Number -> formatNumber(node.value, locale)
        is Group -> "(${plain(node.inner, locale, unicode)})"
        is Unary -> (if (node.operator == '-') "−" else "+") + plain(node.operand, locale, unicode)
        is Percent -> plain(node.operand, locale, unicode) + " %"
        is Root -> "√" + plain(node.operand, locale, unicode)
        is Binary -> when (node.operator) {
            '^' -> {
                val exponent = superscript(node.right, locale)
                if (unicode && exponent != null) plain(node.left, locale, true) + exponent
                else plain(node.left, locale, unicode) + "^" + plain(node.right, locale, unicode)
            }
            '/' -> {
                val fraction = if (unicode) unicodeFraction(node) else null
                fraction ?: "${plain(node.left, locale, unicode)} ÷ ${plain(node.right, locale, unicode)}"
            }
            else -> "${plain(node.left, locale, unicode)} ${symbol(node.operator)} ${plain(node.right, locale, unicode)}"
        }
    }

    private fun symbol(operator: Char) = when (operator) { '*' -> "×"; '-' -> "−"; else -> operator.toString() }

    private fun latex(node: Node, locale: Locale): String = when (node) {
        is Number -> formatNumber(node.value, locale, latex = true)
        is Group -> "\\left(${latex(node.inner, locale)}\\right)"
        is Unary -> (if (node.operator == '-') "-" else "+") + latex(node.operand, locale)
        is Percent -> latex(node.operand, locale) + "\\,\\%"
        is Root -> "\\sqrt{${latex(unwrap(node.operand), locale)}}"
        is Binary -> when (node.operator) {
            '^' -> "${latex(node.left, locale)}^{${latex(unwrap(node.right), locale)}}"
            '/' -> "\\frac{${latex(unwrap(node.left), locale)}}{${latex(unwrap(node.right), locale)}}"
            '*' -> "${latex(node.left, locale)} \\cdot ${latex(node.right, locale)}"
            '-' -> "${latex(node.left, locale)} - ${latex(node.right, locale)}"
            else -> "${latex(node.left, locale)} + ${latex(node.right, locale)}"
        }
    }

    /** Braces already group in LaTeX: \frac{(1+2)}{3} would print the parentheses. */
    private fun unwrap(node: Node): Node = if (node is Group) node.inner else node

    private val SUPERSCRIPTS = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵',
        '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻')

    /** Whole-number exponents as superscripts (2³, 10⁻⁶); null for anything else. */
    private fun superscript(node: Node, locale: Locale): String? {
        val value = when {
            node is Number -> node.value
            node is Unary && node.operator == '-' && node.operand is Number -> (node.operand as Number).value.negate()
            else -> return null
        }
        val whole = try { value.intValueExact() } catch (e: ArithmeticException) { return null }
        return whole.toString().map { SUPERSCRIPTS.getValue(it) }.joinToString("")
    }

    private val FRACTIONS = mapOf(
        (1 to 2) to "½", (1 to 3) to "⅓", (2 to 3) to "⅔", (1 to 4) to "¼", (3 to 4) to "¾",
        (1 to 5) to "⅕", (2 to 5) to "⅖", (3 to 5) to "⅗", (4 to 5) to "⅘", (1 to 6) to "⅙", (5 to 6) to "⅚",
        (1 to 7) to "⅐", (1 to 8) to "⅛", (3 to 8) to "⅜", (5 to 8) to "⅝", (7 to 8) to "⅞", (1 to 9) to "⅑", (1 to 10) to "⅒")

    /** A written fraction of two small whole numbers (1/2 → ½). */
    private fun unicodeFraction(node: Binary): String? {
        val top = (node.left as? Number)?.value?.let { wholeOrNull(it) } ?: return null
        val bottom = (node.right as? Number)?.value?.let { wholeOrNull(it) } ?: return null
        return FRACTIONS[top to bottom]
    }

    private fun wholeOrNull(value: BigDecimal): Int? = try { value.intValueExact() } catch (e: ArithmeticException) { null }
}
