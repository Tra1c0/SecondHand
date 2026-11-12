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
 * signs `× ÷ − ·`, `√` and `∛` (root of the following factor), `²`, `³`, `!` and parentheses.
 * `%` divides the number before it by 100. Constants `π`/`pi` and `e`; functions [FUNCTIONS],
 * arguments separated by `;` (the comma is a decimal mark). Angles are in degrees. A number or a
 * closing parenthesis directly before `(`, a constant, a function or a root multiplies (`2π`).
 * Functions without a finite decimal result are computed in double precision: the result is
 * rounded to 12 significant digits and marked approximate when the shown value is rounded.
 */
object MathExpression {
    const val MAX_INPUT = 200
    /** Internal precision of divisions and roots. */
    private val CONTEXT = MathContext(40, RoundingMode.HALF_EVEN)
    /** Digits after the decimal mark that are shown; more is rounded and marked as approximate. */
    const val MAX_FRACTION_DIGITS = 10
    private const val MAX_EXPONENT = 999
    private val MAX_MAGNITUDE = BigDecimal.TEN.pow(100)
    /** Exact powers with more digits than this are computed with [CONTEXT] instead. */
    private const val MAX_EXACT_DIGITS = 400L

    sealed interface Node
    data class Number(val text: String, val value: BigDecimal) : Node
    data class Unary(val operator: Char, val operand: Node) : Node
    /** [implicit]: a multiplication written without a sign, like `2π`. */
    data class Binary(val operator: Char, val left: Node, val right: Node, val implicit: Boolean = false) : Node
    data class Group(val inner: Node) : Node
    data class Root(val operand: Node) : Node
    data class Percent(val operand: Node) : Node
    data class Factorial(val operand: Node) : Node
    /** `π` or `e`. */
    data class Constant(val name: String) : Node
    /** One of [FUNCTIONS]; `root` has two arguments (radicand; index). */
    data class Function(val name: String, val args: List<Node>) : Node

    /** Function names with their number of arguments; German and English spellings. */
    val FUNCTIONS: Map<String, Int> = mapOf(
        "sin" to 1, "cos" to 1, "tan" to 1, "asin" to 1, "acos" to 1, "atan" to 1,
        "ln" to 1, "log" to 1, "lg" to 1, "exp" to 1, "abs" to 1, "sqrt" to 1, "wurzel" to 1, "root" to 2,
    )
    private val TRIG = setOf("sin", "cos", "tan")
    private val PI = BigDecimal("3.141592653589793238462643383279502884197")
    private val E = BigDecimal("2.718281828459045235360287471352662497757")
    private const val MAX_FACTORIAL = 69 // 69! < 10^100 < 70!

    sealed interface Outcome
    /** [exact] is false when the shown value is rounded (e.g. 1/3 or √2). */
    data class Value(val tree: Node, val value: BigDecimal, val exact: Boolean) : Outcome
    data class Failure(val reason: Reason) : Outcome
    /** [UNDEFINED]: outside the domain of a function, e.g. ln(0), tan(90°), (−1)!. */
    enum class Reason { EMPTY, SYNTAX, DIVISION_BY_ZERO, NEGATIVE_ROOT, TOO_LARGE, TOO_LONG, UNDEFINED }

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
            is Factorial -> factorial(compute(node.operand, inexact))
            is Constant -> { inexact(); if (node.name == "π") PI else E }
            is Function -> function(node.name, node.args.map { compute(it, inexact) }, inexact)
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
        if (base.signum() == 0 && exponent.signum() < 0) throw Fail(Reason.DIVISION_BY_ZERO)
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
            // the digits grow with the exponent (0,99^999 has about 2000): beyond that compute rounded
            if (base.precision().toLong() * whole > MAX_EXACT_DIGITS) {
                inexact()
                return base.pow(whole, CONTEXT)
            }
            return base.pow(whole)
        }
        if (base.signum() == 0) throw Fail(Reason.DIVISION_BY_ZERO)
        return divide(BigDecimal.ONE, power(base, BigDecimal(-whole), inexact), inexact)
    }

    private fun factorial(value: BigDecimal): BigDecimal {
        val n = try { value.intValueExact() } catch (e: ArithmeticException) { throw Fail(Reason.UNDEFINED) }
        if (n < 0) throw Fail(Reason.UNDEFINED)
        if (n > MAX_FACTORIAL) throw Fail(Reason.TOO_LARGE)
        var result = BigDecimal.ONE
        for (i in 2..n) result = result.multiply(BigDecimal(i))
        return result
    }

    private fun function(name: String, args: List<BigDecimal>, inexact: () -> Unit): BigDecimal {
        val x = args[0]
        when (name) {
            "abs" -> return x.abs()
            "sqrt" -> return squareRoot(x, inexact)
            "root" -> return root(x, args[1], inexact)
        }
        val d = x.toDouble()
        val result = when (name) {
            in TRIG -> {
                val radians = Math.toRadians(d)
                // exact multiples of 90° give exact zeros, not 6e-17
                val sin = snap(Math.sin(radians))
                val cos = snap(Math.cos(radians))
                when (name) {
                    "sin" -> sin
                    "cos" -> cos
                    else -> if (cos == 0.0) throw Fail(Reason.UNDEFINED) else snap(sin / cos)
                }
            }
            "asin", "acos" -> {
                if (d < -1 || d > 1) throw Fail(Reason.UNDEFINED)
                Math.toDegrees(if (name == "asin") Math.asin(d) else Math.acos(d))
            }
            "atan" -> Math.toDegrees(Math.atan(d))
            "ln", "log" -> {
                if (x.signum() <= 0) throw Fail(Reason.UNDEFINED)
                if (name == "ln") Math.log(d) else Math.log10(d)
            }
            "exp" -> Math.exp(d)
            else -> throw Fail(Reason.SYNTAX)
        }
        return fromDouble(result, inexact)
    }

    private fun snap(value: Double) = if (kotlin.math.abs(value) < 1e-12) 0.0 else value

    /** A double result as a decimal of 12 significant digits; approximate unless that is short (sin 30° = 0,5). */
    private fun fromDouble(value: Double, inexact: () -> Unit): BigDecimal {
        if (value.isNaN() || value.isInfinite()) throw Fail(Reason.TOO_LARGE)
        if (value == 0.0) return BigDecimal.ZERO
        val rounded = BigDecimal(value).round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        if (rounded.scale() > MAX_FRACTION_DIGITS - 2) inexact()
        return rounded
    }

    /** The [index]-th root; odd roots of negative numbers are negative. Exact when it powers back. */
    private fun root(value: BigDecimal, index: BigDecimal, inexact: () -> Unit): BigDecimal {
        val n = try { index.intValueExact() } catch (e: ArithmeticException) { throw Fail(Reason.UNDEFINED) }
        if (n < 2 || n > MAX_EXPONENT) throw Fail(Reason.UNDEFINED)
        if (n == 2) return squareRoot(value, inexact)
        if (value.signum() < 0 && n % 2 == 0) throw Fail(Reason.NEGATIVE_ROOT)
        if (value.signum() == 0) return BigDecimal.ZERO
        val magnitude = Math.pow(value.abs().toDouble(), 1.0 / n)
        val candidate = BigDecimal(magnitude).round(MathContext(12, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val signed = if (value.signum() < 0) candidate.negate() else candidate
        if (signed.pow(n).compareTo(value) == 0) return signed
        inexact()
        return if (value.signum() < 0) BigDecimal(magnitude, CONTEXT).negate() else BigDecimal(magnitude, CONTEXT)
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
                val next = peek()
                // 2π, 2(1+3), 3√2, (1+2)(3+4): a factor right after another multiplies
                if (next != null && startsImplicitFactor(next)) {
                    node = Binary('*', node, power(), implicit = true)
                    continue
                }
                val op = when (next) { '*', '×', '·' -> '*'; '/', '÷', ':' -> '/'; else -> return node }
                index++
                node = Binary(op, node, unary())
            }
        }

        private fun startsImplicitFactor(char: Char) = char == '(' || char == 'π' || char == '√' || char == '∛' || char.isLetter()

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
                    '!' -> { index++; Factorial(node) }
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
                char == '∛' -> { index++; Function("root", listOf(postfix(), Number("3", BigDecimal(3)))) }
                char == 'π' -> { index++; Constant("π") }
                char.isDigit() || char == ',' || char == '.' -> number()
                char.isLetter() -> word()
                else -> throw Fail(Reason.SYNTAX)
            }
        }

        private fun word(): Node {
            val start = index
            while (index < text.length && text[index].isLetter()) index++
            val name = text.substring(start, index).lowercase()
            when (name) {
                "pi" -> return Constant("π")
                "e" -> return Constant("e")
            }
            val count = FUNCTIONS[name] ?: throw Fail(Reason.SYNTAX)
            if (peek() != '(') throw Fail(Reason.SYNTAX)
            index++
            val args = mutableListOf(sum())
            while (peek() == ';') { index++; args += sum() }
            if (peek() != ')' || args.size != count) throw Fail(Reason.SYNTAX)
            index++
            // one spelling per function in the output
            val canonical = when (name) { "wurzel" -> "sqrt"; "lg" -> "log"; else -> name }
            return Function(canonical, args)
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
        is Factorial -> plainAtom(node.operand, locale, unicode) + "!"
        is Constant -> node.name
        is Function -> plainFunction(node, locale, unicode)
        is Binary -> when (node.operator) {
            '^' -> {
                val exponent = superscript(node.right, locale)
                val base = if (node.left is Binary && node.left.operator == '^') "(${plain(node.left, locale, unicode)})"
                    else plain(node.left, locale, unicode)
                if (unicode && exponent != null) base + exponent
                else base + "^" + plain(node.right, locale, unicode)
            }
            '/' -> {
                val fraction = if (unicode) unicodeFraction(node) else null
                fraction ?: "${plain(node.left, locale, unicode)} ÷ ${plain(node.right, locale, unicode)}"
            }
            '*' -> if (node.implicit && node.right !is Number) plain(node.left, locale, unicode) + plain(node.right, locale, unicode)
                else "${plain(node.left, locale, unicode)} × ${plain(node.right, locale, unicode)}"
            else -> "${plain(node.left, locale, unicode)} ${symbol(node.operator)} ${plain(node.right, locale, unicode)}"
        }
    }

    /** Numbers, constants and groups stand alone; anything else gets parentheses (√(1 + 3), (2 + 1)!). */
    private fun plainAtom(node: Node, locale: Locale, unicode: Boolean): String {
        val text = plain(node, locale, unicode)
        return if (node is Number || node is Constant || node is Group || node is Function) text else "($text)"
    }

    private fun plainFunction(node: Function, locale: Locale, unicode: Boolean): String {
        val arg = node.args[0]
        return when (node.name) {
            // angles are degrees: shown, so that nobody reads radians
            in TRIG -> "${node.name}(${plain(unwrap(arg), locale, unicode)}°)"
            "sqrt" -> "√" + plainAtom(arg, locale, unicode)
            "root" -> {
                val index = node.args[1]
                val sign = when {
                    index is Number && index.value.compareTo(BigDecimal(3)) == 0 -> "∛"
                    index is Number && index.value.compareTo(BigDecimal(4)) == 0 -> "∜"
                    else -> (superscript(index, locale) ?: "(${plain(index, locale, unicode)})") + "√"
                }
                sign + plainAtom(arg, locale, unicode)
            }
            "abs" -> "|${plain(unwrap(arg), locale, unicode)}|"
            else -> "${node.name}(${plain(unwrap(arg), locale, unicode)})"
        }
    }

    private fun symbol(operator: Char) = when (operator) { '*' -> "×"; '-' -> "−"; else -> operator.toString() }

    private fun latex(node: Node, locale: Locale): String = when (node) {
        is Number -> formatNumber(node.value, locale, latex = true)
        is Group -> "\\left(${latex(node.inner, locale)}\\right)"
        is Unary -> (if (node.operator == '-') "-" else "+") + latex(node.operand, locale)
        is Percent -> latex(node.operand, locale) + "\\,\\%"
        is Root -> "\\sqrt{${latex(unwrap(node.operand), locale)}}"
        is Factorial -> (if (node.operand is Number || node.operand is Group || node.operand is Constant) latex(node.operand, locale)
            else "\\left(${latex(node.operand, locale)}\\right)") + "!"
        is Constant -> if (node.name == "π") "\\pi" else "e"
        is Function -> latexFunction(node, locale)
        is Binary -> when (node.operator) {
            '^' -> {
                val base = if (node.left is Binary && node.left.operator == '^') "\\left(${latex(node.left, locale)}\\right)"
                    else latex(node.left, locale)
                "$base^{${latex(unwrap(node.right), locale)}}"
            }
            '/' -> "\\frac{${latex(unwrap(node.left), locale)}}{${latex(unwrap(node.right), locale)}}"
            '*' -> if (node.implicit && node.right !is Number) "${latex(node.left, locale)} ${latex(node.right, locale)}"
                else "${latex(node.left, locale)} \\cdot ${latex(node.right, locale)}"
            '-' -> "${latex(node.left, locale)} - ${latex(node.right, locale)}"
            else -> "${latex(node.left, locale)} + ${latex(node.right, locale)}"
        }
    }

    private fun latexFunction(node: Function, locale: Locale): String {
        val arg = latex(unwrap(node.args[0]), locale)
        return when (node.name) {
            in TRIG -> "\\${node.name}\\left($arg^\\circ\\right)"
            "asin", "acos", "atan" -> "\\arc${node.name.drop(1)}\\left($arg\\right)"
            "ln", "log", "exp" -> "\\${node.name}\\left($arg\\right)"
            "abs" -> "\\left|$arg\\right|"
            "sqrt" -> "\\sqrt{$arg}"
            "root" -> "\\sqrt[${latex(unwrap(node.args[1]), locale)}]{$arg}"
            else -> "\\operatorname{${node.name}}\\left($arg\\right)"
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
