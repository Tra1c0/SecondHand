// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Unit conversion for `@conv` (stage 24f): length, mass, volume (US kitchen units), temperature
 * and speed. No currencies. Pure and offline; exact factors with [BigDecimal].
 *
 * Input: a number (comma or point as decimal mark, optional sign) and a unit, e.g. "5 oz",
 * "100 f", "2,5 Pfund"; optionally a target after "in", "to", "nach", "->", "→" or "=",
 * e.g. "5 oz in g". Without a target, up to three usual targets are offered.
 */
object UnitConversion {
    enum class Dimension { LENGTH, MASS, VOLUME, TEMPERATURE, SPEED }

    /**
     * A unit with its [factor] to the base unit of its dimension (m, kg, l, km/h); temperatures
     * use formulas instead. [aliases] are folded like [fold].
     */
    enum class Measure(val symbol: String, val dimension: Dimension, factor: String, vararg aliases: String) {
        MM("mm", Dimension.LENGTH, "0.001", "mm", "millimeter", "millimetre", "millimetern"),
        CM("cm", Dimension.LENGTH, "0.01", "cm", "zentimeter", "centimeter", "centimetre", "zentimetern"),
        M("m", Dimension.LENGTH, "1", "m", "meter", "metre", "metern", "meters", "metres"),
        KM("km", Dimension.LENGTH, "1000", "km", "kilometer", "kilometre", "kilometern", "kilometers", "kilometres"),
        IN("in", Dimension.LENGTH, "0.0254", "in", "inch", "inches", "zoll", "\""),
        FT("ft", Dimension.LENGTH, "0.3048", "ft", "foot", "feet", "fuss", "'"),
        YD("yd", Dimension.LENGTH, "0.9144", "yd", "yard", "yards"),
        MI("mi", Dimension.LENGTH, "1609.344", "mi", "mile", "miles", "meile", "meilen"),
        G("g", Dimension.MASS, "0.001", "g", "gr", "gramm", "gram", "grams", "gramme"),
        KG("kg", Dimension.MASS, "1", "kg", "kilo", "kilos", "kilogramm", "kilogram", "kilograms"),
        OZ("oz", Dimension.MASS, "0.028349523125", "oz", "unze", "unzen", "ounce", "ounces"),
        LB("lb", Dimension.MASS, "0.45359237", "lb", "lbs", "pfund", "pound", "pounds"),
        ML("ml", Dimension.VOLUME, "0.001", "ml", "milliliter", "millilitre", "millilitern"),
        L("l", Dimension.VOLUME, "1", "l", "liter", "litre", "litern", "liters", "litres"),
        CUP("cup", Dimension.VOLUME, "0.2365882365", "cup", "cups", "tasse", "tassen"),
        TBSP("tbsp", Dimension.VOLUME, "0.01478676478125", "tbsp", "tablespoon", "tablespoons", "el", "essloffel"),
        TSP("tsp", Dimension.VOLUME, "0.00492892159375", "tsp", "teaspoon", "teaspoons", "tl", "teeloffel"),
        FLOZ("fl oz", Dimension.VOLUME, "0.0295735295625", "floz", "fluidounce", "fluidounces"),
        C("°C", Dimension.TEMPERATURE, "1", "c", "°", "°c", "celsius", "grad", "gradc", "gradcelsius"),
        F("°F", Dimension.TEMPERATURE, "1", "f", "°f", "fahrenheit", "gradf", "gradfahrenheit"),
        K("K", Dimension.TEMPERATURE, "1", "k", "kelvin"),
        KMH("km/h", Dimension.SPEED, "1", "kmh", "kph", "stundenkilometer", "kilometerprostunde"),
        MPH("mph", Dimension.SPEED, "1.609344", "mph", "meilenprostunde", "milesperhour");

        val factor = BigDecimal(factor)
        val aliases: List<String> = aliases.map(::fold)
    }

    data class Conversion(val value: BigDecimal, val from: Measure, val result: BigDecimal, val to: Measure)

    /** The usual targets for each unit, the most likely first. */
    private val TARGETS = mapOf(
        Measure.MM to listOf(Measure.IN, Measure.CM),
        Measure.CM to listOf(Measure.IN, Measure.FT, Measure.M),
        Measure.M to listOf(Measure.FT, Measure.YD, Measure.CM),
        Measure.KM to listOf(Measure.MI, Measure.M),
        Measure.IN to listOf(Measure.CM, Measure.MM),
        Measure.FT to listOf(Measure.M, Measure.CM),
        Measure.YD to listOf(Measure.M),
        Measure.MI to listOf(Measure.KM, Measure.M),
        Measure.G to listOf(Measure.OZ, Measure.LB),
        Measure.KG to listOf(Measure.LB, Measure.OZ),
        Measure.OZ to listOf(Measure.G, Measure.LB),
        Measure.LB to listOf(Measure.KG, Measure.G),
        Measure.ML to listOf(Measure.FLOZ, Measure.CUP, Measure.TSP),
        Measure.L to listOf(Measure.CUP, Measure.FLOZ, Measure.ML),
        Measure.CUP to listOf(Measure.ML, Measure.L),
        Measure.TBSP to listOf(Measure.ML, Measure.TSP),
        Measure.TSP to listOf(Measure.ML),
        Measure.FLOZ to listOf(Measure.ML),
        Measure.C to listOf(Measure.F, Measure.K),
        Measure.F to listOf(Measure.C),
        Measure.K to listOf(Measure.C, Measure.F),
        Measure.KMH to listOf(Measure.MPH),
        Measure.MPH to listOf(Measure.KMH),
    )

    private val QUANTITY = Regex("""^([+\-−]?)(\d{1,12})(?:[.,](\d{1,10}))?\s*(\S.*)$""")
    private val TARGET_SEPARATOR = Regex("""\s+(?:in|to|nach|->|→|=)(?=\s)""", RegexOption.IGNORE_CASE)
    private val CALC = MathContext.DECIMAL64
    private val ABSOLUTE_ZERO_C = BigDecimal("-273.15")

    /** Lower case, umlauts as plain vowels, without spaces, dots and slashes ("km/h" → "kmh", "Fuß" → "fuss"). */
    fun fold(text: String): String {
        val builder = StringBuilder(text.length)
        for (char in text.lowercase()) when (char) {
            'ä' -> builder.append('a')
            'ö' -> builder.append('o')
            'ü' -> builder.append('u')
            'ß' -> builder.append("ss")
            ' ', '.', '/', '\t' -> {}
            else -> builder.append(char)
        }
        return builder.toString()
    }

    fun measure(text: String): Measure? {
        val folded = fold(text)
        if (folded.isEmpty()) return null
        return Measure.values().firstOrNull { folded in it.aliases }
    }

    /** The conversions for [text], empty if it is not a quantity with a known unit (or below absolute zero). */
    fun conversions(text: String): List<Conversion> {
        val input = text.trim()
        // an explicit target, e.g. "5 oz in g"; "5 in" alone stays a length in inches
        for (match in TARGET_SEPARATOR.findAll(input)) {
            val quantity = quantity(input.substring(0, match.range.first)) ?: continue
            val target = measure(input.substring(match.range.last + 1)) ?: continue
            if (target.dimension != quantity.second.dimension) continue
            return listOfNotNull(convert(quantity.first, quantity.second, target))
        }
        val (value, from) = quantity(input) ?: return emptyList()
        return TARGETS[from].orEmpty().mapNotNull { convert(value, from, it) }
    }

    private fun quantity(text: String): Pair<BigDecimal, Measure>? {
        val match = QUANTITY.matchEntire(text.trim()) ?: return null
        val (sign, whole, fraction, unit) = match.destructured
        val number = BigDecimal(whole + (if (fraction.isEmpty()) "" else ".$fraction"))
        val measure = measure(unit) ?: return null
        return (if (sign.isEmpty() || sign == "+") number else number.negate()) to measure
    }

    /** Converts exactly where the factors allow, then rounds for display; null below absolute zero. */
    fun convert(value: BigDecimal, from: Measure, to: Measure): Conversion? {
        require(from.dimension == to.dimension)
        val raw = if (from.dimension == Dimension.TEMPERATURE) {
            val celsius = when (from) {
                Measure.F -> (value - BigDecimal(32)).multiply(BigDecimal(5)).divide(BigDecimal(9), CALC)
                Measure.K -> value + ABSOLUTE_ZERO_C
                else -> value
            }
            if (celsius < ABSOLUTE_ZERO_C) return null
            when (to) {
                Measure.F -> celsius.multiply(BigDecimal(9)).divide(BigDecimal(5), CALC) + BigDecimal(32)
                Measure.K -> celsius - ABSOLUTE_ZERO_C
                else -> celsius
            }
        } else value.multiply(from.factor).divide(to.factor, CALC)
        return Conversion(value, from, round(raw, temperature = from.dimension == Dimension.TEMPERATURE), to)
    }

    /**
     * Temperatures to two decimals (273,15 K); otherwise whole numbers from 1000 on and four
     * significant digits below; no trailing zeros.
     */
    fun round(value: BigDecimal, temperature: Boolean = false): BigDecimal {
        if (value.signum() == 0) return BigDecimal.ZERO
        val rounded = if (temperature) value.setScale(2, RoundingMode.HALF_UP)
            else if (value.abs() >= BigDecimal(1000)) value.setScale(0, RoundingMode.HALF_UP)
            else value.round(MathContext(4, RoundingMode.HALF_UP))
        val stripped = rounded.stripTrailingZeros()
        if (stripped.signum() == 0) return BigDecimal.ZERO
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    /** "5 oz = 141,7 g" with the decimal mark of [locale]. */
    fun format(conversion: Conversion, locale: MathExpression.Locale): String {
        val value = conversion.value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        return "${MathExpression.formatNumber(value, locale)} ${conversion.from.symbol} = " +
            "${MathExpression.formatNumber(conversion.result, locale)} ${conversion.to.symbol}"
    }
}
