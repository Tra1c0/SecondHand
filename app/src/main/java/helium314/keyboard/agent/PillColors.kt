// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.Seen
import helium314.keyboard.latin.suggestions.StripIconColors

/**
 * Fork, stage 88c: the colours of a pill of the tool grid, computed against the surface the pill REALLY has: the design's key colour
 * (it may be translucent, the owner's is white at about half) laid over the ground of the grid. Build 43 computed the contrast
 * against the key colour as if it were opaque, so the text of every pill was 1.1:1 against its own pill.
 *
 * - the ground is a [Seen] colour (the grid paints it opaque), the surface is always opaque and distinct from the ground of the grid;
 * - text and icon have [StripIconColors.NORMAL_CONTRAST] against it; the design's own direction is kept (light text stays light), and if
 *   even white cannot reach it, the SURFACE moves a little towards the ground first, before the text turns to the other side;
 * - "in the bar": a duller surface (towards the ground, at least [IN_BAR_STEP] from the normal surface where the ground leaves room), not
 *   an alpha, so the text stays readable; the outline of the pill is a second cue;
 * - "not available": text and icon at half strength, but at least 3:1.
 */
internal object PillColors {
    class Look(val fill: Int, val text: Int, val icon: Int, val pressed: Int = fill)

    private const val MIN_FROM_GROUND = 1.3
    /** An in-bar surface is never nearer to the ground than this (it would be a hole in the grid), and at least [IN_BAR_STEP] from the normal one. */
    private const val IN_BAR_FROM_GROUND = 1.1
    private const val IN_BAR_STEP = 1.15
    private const val UNAVAILABLE_MIN = 3.0

    /** What a key colour looks like on [ground]: opaque. */
    fun surface(key: Int, ground: Seen): Seen = Seen.over(key, ground)

    fun normal(key: Int, ground: Seen, text: Int, icon: Int): Look =
        resolve(separate(surface(key, ground).argb, ground.argb), ground.argb, text, icon)

    fun inBar(key: Int, ground: Seen, text: Int, icon: Int): Look {
        val g = ground.argb
        val base = separate(surface(key, ground).argb, g)
        // towards the ground, about 60 % of the way; further if the surface does not differ from the normal one by IN_BAR_STEP yet;
        // never so near the ground that it vanishes into it (a design whose keys have the colour of the ground has little room: then
        // the step is as large as that room allows)
        var best = base
        var bestScore = Double.NEGATIVE_INFINITY
        var u = 0.02f
        while (u <= 1f) {
            val next = RgbColors.lerp(base, g, u)
            if (RgbColors.contrast(next, g) >= IN_BAR_FROM_GROUND) {
                val step = RgbColors.contrast(next, base)
                // the step counts up to IN_BAR_STEP; among those that reach it, the one nearest to 60 % wins
                val score = if (step >= IN_BAR_STEP) 10.0 - Math.abs(u - 0.6f) else step
                if (score > bestScore) { bestScore = score; best = next }
            }
            u += 0.02f
        }
        return resolve(best, g, text, icon)
    }

    fun unavailable(look: Look): Look {
        val on = Seen.of(look.fill) // the fill of a Look is opaque
        return Look(look.fill, StripIconColors.off(look.text, on, UNAVAILABLE_MIN), StripIconColors.off(look.icon, on, UNAVAILABLE_MIN), look.pressed)
    }

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
                val next = RgbColors.lerp(start, ground, u)
                if (RgbColors.contrast(next, ground) < MIN_FROM_GROUND) break
                if (reachable(text, next)) { fill = next; break }
            }
        }
        val on = Seen.of(fill) // opaque already
        val t = StripIconColors.ensureContrast(text, on)
        // pressed: the surface moves AWAY from the text colour, so the contrast only grows while pressed
        val away = if (RgbColors.luminance(t) >= RgbColors.luminance(fill)) RgbColors.BLACK else RgbColors.WHITE
        return Look(fill, t, StripIconColors.ensureContrast(icon, on), RgbColors.lerp(fill, away, 0.15f))
    }
}
