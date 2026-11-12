// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.UnitConversion.Measure
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.*

class UnitConversionTest {
    private fun texts(input: String, locale: MathExpression.Locale = MathExpression.Locale.COMMA) =
        UnitConversion.conversions(input).map { UnitConversion.format(it, locale) }

    @Test fun offersUsualTargetsWithTheDecimalMarkOfTheLanguage() {
        assertEquals(listOf("5 oz = 141,7 g", "5 oz = 0,3125 lb"), texts("5 oz"))
        assertEquals(listOf("5 oz = 141.7 g", "5 oz = 0.3125 lb"), texts("5 oz", MathExpression.Locale.POINT))
        assertEquals(listOf("100 °F = 37,78 °C"), texts("100 f"))
        assertEquals(listOf("1 in = 2,54 cm", "1 in = 25,4 mm"), texts("1 in"))
        assertEquals(listOf("10 km = 6,214 mi", "10 km = 10.000 m"), texts("10 km"))
        assertEquals(listOf("1 mi = 1,609 km", "1 mi = 1609 m"), texts("1 mi"))
        assertEquals(listOf("1 cup = 236,6 ml", "1 cup = 0,2366 l"), texts("1 cup"))
        assertEquals(listOf("1,5 l = 6,34 cup", "1,5 l = 50,72 fl oz", "1,5 l = 1500 ml"), texts("1.5 l"))
        assertEquals(listOf("100 km/h = 62,14 mph"), texts("100 km/h"))
        assertEquals(listOf("60 mph = 96,56 km/h"), texts("60 mph"))
    }

    @Test fun readsGermanNamesCaseAndDecimalComma() {
        assertEquals(listOf("2,5 lb = 1,134 kg", "2,5 lb = 1134 g"), texts("2,5 Pfund"))
        assertEquals(listOf("3 ft = 0,9144 m", "3 ft = 91,44 cm"), texts("3 Fuß"))
        assertEquals(texts("12 in"), texts("12 Zoll"))
        assertEquals(texts("20 c"), texts("20 Grad"))
        assertEquals(texts("20 c"), texts("20°C"))
        assertEquals(texts("1 tbsp"), texts("1 EL"))
        assertEquals(listOf("1 tbsp = 14,79 ml", "1 tbsp = 3 tsp"), texts("1 tbsp"))
        assertEquals(texts("5 oz"), texts("5OZ"))
        assertEquals(texts("5 oz"), texts("5,0 Unzen"))
    }

    @Test fun temperaturesUseFormulasAndStopAtAbsoluteZero() {
        assertEquals(listOf("0 °C = 32 °F", "0 °C = 273,15 K"), texts("0 c"))
        assertEquals(listOf("−40 °C = −40 °F", "−40 °C = 233,15 K"), texts("-40 c"))
        assertEquals(listOf("0 K = −273,15 °C", "0 K = −459,67 °F"), texts("0 k"))
        assertTrue(texts("-300 c").isEmpty())
        assertTrue(texts("-1 k").isEmpty())
    }

    @Test fun anExplicitTargetGivesOneConversion() {
        assertEquals(listOf("5 oz = 141,7 g"), texts("5 oz in g"))
        assertEquals(listOf("5 oz = 0,1417 kg"), texts("5 oz to kg"))
        assertEquals(listOf("5 in = 12,7 cm"), texts("5 in in cm"))
        assertEquals(listOf("5 in = 12,7 cm", "5 in = 127 mm"), texts("5 in"))
        assertEquals(listOf("212 °F = 100 °C"), texts("212 f nach c"))
        assertEquals(listOf("1 km = 1000 m"), texts("1 km → m"))
        assertTrue(texts("5 oz in km").isEmpty()) // different dimensions
    }

    @Test fun ignoresAnythingElse() {
        for (input in listOf("", "5", "oz", "5 xyz", "fünf oz", "5 oz 3", "1e5 m", "5 €", "1234567890123 m"))
            assertTrue(texts(input).isEmpty(), input)
    }

    @Test fun roundsForDisplay() {
        assertEquals(BigDecimal("141.7"), UnitConversion.round(BigDecimal("141.747615625")))
        assertEquals(BigDecimal("16404"), UnitConversion.round(BigDecimal("16404.199")))
        assertEquals(BigDecimal("0"), UnitConversion.round(BigDecimal("0.000")))
        assertEquals(BigDecimal("3"), UnitConversion.round(BigDecimal("3.0000001")))
        assertEquals(BigDecimal("273.15"), UnitConversion.round(BigDecimal("273.15"), temperature = true))
        assertEquals(BigDecimal("0"), UnitConversion.round(BigDecimal("-0.001"), temperature = true))
        assertEquals(Measure.LB, UnitConversion.measure("Pfund"))
        assertEquals(Measure.KMH, UnitConversion.measure("km/h"))
        assertNull(UnitConversion.measure(""))
    }
}
