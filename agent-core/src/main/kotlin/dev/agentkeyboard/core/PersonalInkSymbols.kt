// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 36: personal templates, the user's own handwriting of known symbols. A drawing is kept when
 * the user picks an alternative chip after a wrong recognition (only with the learning switch on) or
 * draws a symbol in the calibration of the settings page. At most [PER_SYMBOL] drawings per symbol,
 * the newest win; at most [MAX_SYMBOLS] symbols, the one changed longest ago goes first. Only the
 * normalized clouds are kept, in the format of [OwnInkSymbols] (docs/STORAGE.md).
 */
object PersonalInkSymbols {
    const val PER_SYMBOL = 5
    const val MAX_SYMBOLS = 80

    fun parse(encoded: String?): List<OwnInkSymbols.Symbol> = OwnInkSymbols.parse(encoded, MAX_SYMBOLS, PER_SYMBOL)

    fun encode(symbols: List<OwnInkSymbols.Symbol>): String = OwnInkSymbols.encode(symbols, MAX_SYMBOLS, PER_SYMBOL)

    /**
     * [symbols] with [cloud] added for [text]: the newest drawing last, the oldest dropped beyond
     * [PER_SYMBOL]; the changed symbol moves to the end, the first is dropped beyond [MAX_SYMBOLS].
     * A text an own symbol could not have is refused (unchanged list).
     */
    fun add(symbols: List<OwnInkSymbols.Symbol>, text: String, cloud: InkCloud): List<OwnInkSymbols.Symbol> {
        if (OwnInkSymbols.textProblem(text) != null) return symbols
        val old = symbols.firstOrNull { it.text == text }
        val samples = ((old?.samples ?: emptyList()) + cloud).takeLast(PER_SYMBOL)
        return (symbols.filter { it.text != text } + OwnInkSymbols.Symbol(text, samples)).takeLast(MAX_SYMBOLS)
    }

    /** [symbols] without [text]. */
    fun remove(symbols: List<OwnInkSymbols.Symbol>, text: String) = symbols.filter { it.text != text }

    /** The templates for the recognizer; not "own" (they type a known symbol). */
    fun templates(symbols: List<OwnInkSymbols.Symbol>): List<InkTemplate> =
        symbols.flatMap { symbol -> symbol.samples.map { InkTemplate(symbol.text, it) } }
}
