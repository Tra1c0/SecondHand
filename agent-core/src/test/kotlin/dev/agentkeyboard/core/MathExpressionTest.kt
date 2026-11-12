// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.MathExpression.Reason
import dev.agentkeyboard.core.MathExpression.Style
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.*

class MathExpressionTest {
    private val de = MathExpression.Locale.COMMA
    private val en = MathExpression.Locale.POINT

    private fun value(input: String) = MathExpression.evaluate(input) as MathExpression.Value
    private fun format(input: String, style: Style = Style.EQUATION, locale: MathExpression.Locale = de) =
        MathExpression.format(value(input), style, locale)
    private fun failure(input: String) = (MathExpression.evaluate(input) as MathExpression.Failure).reason

    @Test fun computesExactlyWithDecimals() {
        assertEquals(BigDecimal("0.3"), value("0,1 + 0,2").value) // no binary rounding error
        assertTrue(value("0,1 + 0,2").exact)
        assertEquals(BigDecimal("42"), value("12*3,5").value)
        assertEquals(BigDecimal("42"), value("12 × 3.5").value)
        assertEquals(BigDecimal("7"), value("1+2*3").value)
        assertEquals(BigDecimal("9"), value("(1+2)*3").value)
        assertEquals(BigDecimal("-9"), value("-3^2").value)
        assertEquals(BigDecimal("512"), value("2^3^2").value)
        assertEquals(BigDecimal("0.5"), value("2^-1").value)
        assertEquals(BigDecimal("9"), value("3²").value)
        assertEquals(BigDecimal("100"), value("50%*200").value)
        assertEquals(BigDecimal("2.5"), value("10 ÷ 4").value)
        assertEquals(BigDecimal("4"), value("√16").value)
        assertTrue(value("√16").exact)
        assertEquals(BigDecimal("1.5"), value("√2,25").value)
        assertEquals(BigDecimal("-3"), value("2 − 5").value)
    }

    @Test fun roundedResultsAreMarked() {
        val third = value("1/3")
        assertFalse(third.exact)
        assertEquals(BigDecimal("0.3333333333"), third.value)
        assertEquals("1 ÷ 3 ≈ 0,3333333333", format("1/3"))
        assertFalse(value("√2").exact)
        assertEquals("1.4142135624", value("√2").value.toPlainString())
        assertEquals("1.4142135624", value("2^0,5").value.toPlainString())
        assertFalse(value("2^0,5").exact)
    }

    @Test fun failures() {
        assertEquals(Reason.EMPTY, failure("  "))
        assertEquals(Reason.SYNTAX, failure("(1+2"))
        assertEquals(Reason.SYNTAX, failure("1,2,3"))
        assertEquals(Reason.SYNTAX, failure("2 +"))
        assertEquals(Reason.SYNTAX, failure("abc"))
        assertEquals(Reason.SYNTAX, failure("1 2"))
        assertEquals(Reason.DIVISION_BY_ZERO, failure("10/0"))
        assertEquals(Reason.DIVISION_BY_ZERO, failure("0^-1"))
        assertEquals(Reason.NEGATIVE_ROOT, failure("√(-4)"))
        assertEquals(Reason.NEGATIVE_ROOT, failure("(-8)^0,5"))
        assertEquals(Reason.TOO_LARGE, failure("10^1000"))
        assertEquals(Reason.TOO_LARGE, failure("9^999"))
        assertEquals(Reason.TOO_LARGE, failure("(10^90)*(10^20)"))
        assertEquals(BigDecimal.TEN.pow(90), value("10^90").value)
        assertEquals(Reason.DIVISION_BY_ZERO, failure("0^-0,5"))
        assertEquals(Reason.TOO_LONG, failure("1+".repeat(101) + "1"))
    }

    @Test fun formats() {
        assertEquals("12 × 3,5 = 42", format("12*3,5"))
        assertEquals("42", format("12*3,5", Style.RESULT))
        assertEquals("12 \\cdot 3{,}5 = 42", format("12*3,5", Style.LATEX))
        assertEquals("12 × 3.5 = 42", format("12*3,5", locale = en))
        assertEquals("2^10 = 1024", format("2^10"))
        assertEquals("2¹⁰ = 1024", format("2^10", Style.UNICODE))
        assertEquals("10⁻² = 0,01", format("10^-2", Style.UNICODE))
        assertEquals("2^{10} = 1024", format("2^10", Style.LATEX))
        assertEquals("½ + ¾ = 1,25", format("1/2 + 3/4", Style.UNICODE))
        assertEquals("1 ÷ 2 + 3 ÷ 4 = 1,25", format("1/2 + 3/4"))
        assertEquals("\\frac{1 + 2}{3} = 1", format("(1+2)/3", Style.LATEX))
        assertEquals("(1 + 2) × 3 = 9", format("(1+2)*3"))
        assertEquals("\\left(1 + 2\\right) \\cdot 3 = 9", format("(1+2)*3", Style.LATEX))
        assertEquals("√2 ≈ 1,4142135624", format("√2"))
        assertEquals("\\sqrt{2} \\approx 1{,}4142135624", format("√2", Style.LATEX))
        assertEquals("50 % × 200 = 100", format("50%*200"))
        assertEquals("2 − 5 = −3", format("2-5"))
        assertEquals("−3 × 2 = −6", format("-3*2"))
    }

    @Test fun numberGrouping() {
        assertEquals("1.234.567,5", MathExpression.formatNumber(BigDecimal("1234567.5"), de))
        assertEquals("1,234,567.5", MathExpression.formatNumber(BigDecimal("1234567.5"), en))
        assertEquals("1234", MathExpression.formatNumber(BigDecimal("1234"), de)) // four digits stay together
        assertEquals("12.345", MathExpression.formatNumber(BigDecimal("12345"), de))
        assertEquals("1\\,234\\,567{,}5", MathExpression.formatNumber(BigDecimal("1234567.5"), de, latex = true))
        assertEquals("−12.345", MathExpression.formatNumber(BigDecimal("-12345"), de))
        assertEquals(de, MathExpression.Locale.forLanguage("de"))
        assertEquals(de, MathExpression.Locale.forLanguage("es"))
        assertEquals(en, MathExpression.Locale.forLanguage("en"))
    }

    @Test fun hugePrecisionStaysCheap() {
        val start = System.nanoTime()
        val tiny = value("(0,99^999)^999")
        assertFalse(tiny.exact)
        assertEquals(BigDecimal.ZERO, tiny.value) // rounded to 10 places
        assertFalse(value("(1,000000000000000000000000000000000000000000000000000000001^999)^99").exact)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
        assertTrue(value("0,5^10").exact)
    }

    @Test fun powerOfPowerIsBracketed() {
        assertEquals(BigDecimal(64), value("2²^3").value)
        assertEquals("(2^2)^3 = 64", format("2²^3"))
        assertEquals("(2²)³ = 64", format("2²^3", Style.UNICODE))
        assertEquals("\\left(2^{2}\\right)^{3} = 64", format("2²^3", Style.LATEX))
    }

    @Test fun functionsConstantsAndFactorial() {
        assertEquals(BigDecimal("4"), value("sqrt(16)").value)
        assertEquals(BigDecimal("4"), value("Wurzel(16)").value)
        assertTrue(value("sqrt(16)").exact)
        assertEquals(BigDecimal("3"), value("∛27").value)
        assertTrue(value("∛27").exact)
        assertEquals(BigDecimal("-2"), value("root(-8;3)").value)
        assertEquals(BigDecimal("2"), value("root(16;4)").value)
        assertEquals(BigDecimal("0.5"), value("sin(30)").value) // degrees
        assertTrue(value("sin(30)").exact)
        assertEquals(BigDecimal.ZERO, value("cos(90)").value)
        assertEquals(BigDecimal("1"), value("tan(45)").value)
        assertEquals(BigDecimal("45"), value("atan(1)").value)
        assertEquals(BigDecimal("30"), value("asin(0,5)").value)
        assertEquals(BigDecimal("3"), value("log(1000)").value)
        assertEquals(BigDecimal("3"), value("lg(1000)").value)
        assertFalse(value("ln(2)").exact)
        assertEquals("0.6931471806", value("ln(2)").value.toPlainString())
        assertEquals(BigDecimal("120"), value("5!").value)
        assertEquals(BigDecimal("24"), value("(2+2)!").value)
        assertFalse(value("π").exact)
        assertEquals("3.1415926536", value("π").value.toPlainString())
        assertEquals("6.2831853072", value("2π").value.toPlainString())
        assertEquals(BigDecimal("8"), value("2(1+3)").value)
        assertEquals(BigDecimal("21"), value("(1+2)(3+4)").value)
        assertEquals(BigDecimal("6"), value("3√4").value)
        assertEquals(BigDecimal("1"), value("abs(-1)").value)
    }

    @Test fun functionFailures() {
        assertEquals(Reason.UNDEFINED, failure("ln(0)"))
        assertEquals(Reason.UNDEFINED, failure("log(-1)"))
        assertEquals(Reason.UNDEFINED, failure("tan(90)"))
        assertEquals(Reason.UNDEFINED, failure("asin(2)"))
        assertEquals(Reason.UNDEFINED, failure("(-1)!"))
        assertEquals(Reason.UNDEFINED, failure("2,5!"))
        assertEquals(Reason.UNDEFINED, failure("root(8;1)"))
        assertEquals(Reason.TOO_LARGE, failure("70!"))
        assertEquals(Reason.NEGATIVE_ROOT, failure("root(-16;4)"))
        assertEquals(Reason.SYNTAX, failure("sin 30"))
        assertEquals(Reason.SYNTAX, failure("foo(2)"))
        assertEquals(Reason.SYNTAX, failure("root(8)"))
        assertEquals(Reason.SYNTAX, failure("sin(30;2)"))
    }

    @Test fun functionFormats() {
        assertEquals("sin(30°) = 0,5", format("sin(30)"))
        assertEquals("\\sin\\left(30^\\circ\\right) = 0{,}5", format("sin(30)", Style.LATEX))
        assertEquals("√16 = 4", format("sqrt(16)"))
        assertEquals("√(9 + 7) = 4", format("wurzel(9+7)"))
        assertEquals("∛27 = 3", format("root(27;3)"))
        assertEquals("⁵√32 = 2", format("root(32;5)", Style.UNICODE))
        assertEquals("\\sqrt[3]{27} = 3", format("∛27", Style.LATEX))
        assertEquals("2π ≈ 6,2831853072", format("2pi"))
        assertEquals("2 \\pi \\approx 6{,}2831853072", format("2π", Style.LATEX))
        assertEquals("5! = 120", format("5!"))
        assertEquals("ln(2) ≈ 0,6931471806", format("ln(2)"))
        assertEquals("\\ln\\left(2\\right) \\approx 0{,}6931471806", format("ln(2)", Style.LATEX))
        assertEquals("|−3| = 3", format("abs(-3)"))
        assertEquals("\\arcsin\\left(0{,}5\\right) = 30", format("asin(0,5)", Style.LATEX))
        assertEquals("2(1 + 3) = 8", format("2(1+3)"))
    }
}
