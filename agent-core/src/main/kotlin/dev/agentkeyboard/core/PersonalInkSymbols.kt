// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 36: personal templates, the user's own handwriting of known symbols. A drawing is kept when
 * the user picks an alternative chip after a wrong recognition (only with the learning switch on) or
 * draws a symbol in the calibration of the settings page. At most [PER_SYMBOL] drawings per symbol,
 * the newest win; at most [MAX_SYMBOLS] symbols, the one changed longest ago goes first (review M2:
 * every template costs time on the main thread). Only the normalized clouds are kept, in the format
 * of [OwnInkSymbols] (docs/STORAGE.md). A drawing with dots is kept only for a symbol with dots
 * (review M3): a stray tap kept with a dash would make taps on a line dots (InkDots, P113).
 */
object PersonalInkSymbols {
    const val PER_SYMBOL = 3
    const val MAX_SYMBOLS = 40

    fun parse(encoded: String?): List<OwnInkSymbols.Symbol> = OwnInkSymbols.parse(encoded, MAX_SYMBOLS, PER_SYMBOL)

    fun encode(symbols: List<OwnInkSymbols.Symbol>): String = OwnInkSymbols.encode(symbols, MAX_SYMBOLS, PER_SYMBOL)

    /**
     * [symbols] with [cloud] added for [text]: the newest drawing last, the oldest dropped beyond
     * [PER_SYMBOL]; the changed symbol moves to the end, the first is dropped beyond [MAX_SYMBOLS].
     * A text an own symbol could not have is refused (unchanged list), and so is a drawing with dots
     * unless [symbolHasDots] (the symbol's own templates have dots, like ÷; see [hasDots]).
     */
    fun add(symbols: List<OwnInkSymbols.Symbol>, text: String, cloud: InkCloud, symbolHasDots: Boolean = false): List<OwnInkSymbols.Symbol> {
        if (OwnInkSymbols.textProblem(text) != null) return symbols
        if (cloud.dotCount > 0 && !symbolHasDots) return symbols
        val old = symbols.firstOrNull { it.text == text }
        val samples = ((old?.samples ?: emptyList()) + cloud).takeLast(PER_SYMBOL)
        return (symbols.filter { it.text != text } + OwnInkSymbols.Symbol(text, samples)).takeLast(MAX_SYMBOLS)
    }

    /** True when one of [templates] for [text] has dots (then a drawing of it may have dots too). */
    fun hasDots(templates: List<InkTemplate>, text: String): Boolean = templates.any { it.text == text && it.cloud.dotCount > 0 }

    /** All drawings together, for the overview of learned data. */
    fun drawings(symbols: List<OwnInkSymbols.Symbol>): Int = symbols.sumOf { it.samples.size }

    /** [symbols] without [text]. */
    fun remove(symbols: List<OwnInkSymbols.Symbol>, text: String) = symbols.filter { it.text != text }

    /** The templates for the recognizer; not "own" (they type a known symbol). */
    fun templates(symbols: List<OwnInkSymbols.Symbol>): List<InkTemplate> =
        symbols.flatMap { symbol -> symbol.samples.map { InkTemplate(symbol.text, it) } }
}
