// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.RgbColors
import helium314.keyboard.latin.suggestions.StripIconColors

/**
 * Fork, stage 88c: the colours of a pill of the tool grid, computed against the surface the pill REALLY has: the design's key colour
 * (it may be translucent, the owner's is white at about half) laid over the ground of the grid. Build 43 computed the contrast
 * against the key colour as if it were opaque, so the text of every pill was 1.1:1 against its own pill.
 *
 * - the surface is always opaque and distinct from the ground of the grid;
 * - text and icon have [StripIconColors.NORMAL_CONTRAST] against it; the design's own direction is kept (light text stays light), and if
 *   even white cannot reach it, the SURFACE moves a little towards the ground first, before the text turns to the other side;
 * - "in the bar": a duller surface (half-way to the ground), not an alpha, so the text stays readable;
 * - "not available": text and icon at half strength, but at least 3:1.
 */
internal object PillColors {
    class Look(val fill: Int, val text: Int, val icon: Int, val pressed: Int = fill)

    private const val MIN_FROM_GROUND = 1.3
    private const val UNAVAILABLE_MIN = 3.0

    /** What a key colour looks like on [ground]: opaque. */
    fun surface(key: Int, ground: Int) = RgbColors.over(key, ground or RgbColors.BLACK)

    fun normal(key: Int, ground: Int, text: Int, icon: Int): Look = resolve(separate(surface(key, ground), ground), ground, text, icon)

    fun inBar(key: Int, ground: Int, text: Int, icon: Int): Look {
        val base = separate(surface(key, ground), ground)
        // half-way to the ground, but never so far that it vanishes into it
        var dull = RgbColors.lerp(base, ground or RgbColors.BLACK, 0.6f)
        if (RgbColors.contrast(dull, ground) < MIN_FROM_GROUND) dull = separate(dull, ground)
        return resolve(dull, ground, text, icon)
    }

    fun unavailable(look: Look): Look =
        Look(look.fill, StripIconColors.off(look.text, look.fill, UNAVAILABLE_MIN), StripIconColors.off(look.icon, look.fill, UNAVAILABLE_MIN), look.pressed)

    /** [fill] moved away from [ground] (to white on a dark ground, else to black) until it stands out. */
    private fun separate(fill: Int, ground: Int): Int {
        if (RgbColors.contrast(fill, ground) >= MIN_FROM_GROUND) return fill
        val away = if (RgbColors.luminance(ground) < 0.18) RgbColors.WHITE else RgbColors.BLACK
        var t = 0f
        var result = fill
        while (t < 1f && RgbColors.contrast(result, ground) < MIN_FROM_GROUND) { t += 0.02f; result = RgbColors.lerp(fill, away, t) }
        return result
    }

    private fun reachable(c: Int, on: Int): Boolean {
        val towards = if (RgbColors.luminance(c) >= RgbColors.luminance(on)) RgbColors.WHITE else RgbColors.BLACK
        return RgbColors.contrast(towards, on) >= StripIconColors.NORMAL_CONTRAST
    }

    private fun resolve(start: Int, ground: Int, text: Int, icon: Int): Look {
        var fill = start
        if (!reachable(text, fill)) {
            // the design's direction cannot reach the contrast on this surface: move the surface a little towards the ground, as far as it still stands out
            var u = 0f
            while (u < 1f) {
                u += 0.02f
                val next = RgbColors.lerp(start, ground or RgbColors.BLACK, u)
                if (RgbColors.contrast(next, ground) < MIN_FROM_GROUND) break
                if (reachable(text, next)) { fill = next; break }
            }
        }
        val t = StripIconColors.ensureContrast(text, fill)
        // pressed: the surface moves AWAY from the text colour, so the contrast only grows while pressed
        val away = if (RgbColors.luminance(t) >= RgbColors.luminance(fill)) RgbColors.BLACK else RgbColors.WHITE
        return Look(fill, t, StripIconColors.ensureContrast(icon, fill), RgbColors.lerp(fill, away, 0.15f))
    }
}
