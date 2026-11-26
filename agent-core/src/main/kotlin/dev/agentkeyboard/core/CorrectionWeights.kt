// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.pow

/**
 * Stage 107 (owner, 2026-11-26: "erst bei mehrfachem Zurücknehmen … auf Häufigkeit zurückführen … nicht endgültig"): whether a
 * correction "typed -> corrected" is held back, from how often the owner kept it ([confirmed]: committed automatically and
 * left standing, or picked in the strip) and how often he took it back ([rejected]: Backspace right after it, or changed later
 * in the field). Pure, numbers only.
 *
 * Rule: held back while there is a rejection and the confirmations are fewer than [REJECTION_WEIGHT] times the rejections
 * (one rejection weighs as much as three confirmations). Both fade with a half-life of [HALF_LIFE_DAYS]; a rejection that has
 * faded below [FORGET_BELOW] is forgotten, so nothing is final.
 * Examples: 10 kept, 1 taken back -> applied (10 >= 3); 2 kept, 2 taken back -> held back (2 < 6), after 4 more picks of the
 * correction -> applied again (6 >= 6); 0 kept, 1 taken back -> held back, applied again after about 60 days (1 -> 0.25).
 */
object CorrectionWeights {
    const val REJECTION_WEIGHT = 3.0
    const val HALF_LIFE_DAYS = 30.0
    const val FORGET_BELOW = 0.25
    private const val DAY_MILLIS = 86_400_000.0

    @JvmStatic
    fun blocks(confirmed: Double, rejected: Double): Boolean = rejected > 0.0 && confirmed < REJECTION_WEIGHT * rejected

    /** [value] after [ageMillis] of fading. */
    @JvmStatic
    fun faded(value: Double, ageMillis: Long): Double = value * 0.5.pow(ageMillis / (HALF_LIFE_DAYS * DAY_MILLIS))
}
