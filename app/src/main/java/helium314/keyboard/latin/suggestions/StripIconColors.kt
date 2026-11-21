// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.ImageView
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.Seen
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors

/**
 * Fork, stage 88: the colours of the icons in the fixed bar, with a guaranteed contrast in every design (rule A2 of
 * `docs/LEISTE-KONZEPT.md`). The theme's colour list for toolbar keys is read (as the RGB light does) and every
 * state colour is moved towards white or black until it has [NORMAL_CONTRAST] against the strip. The unavailable
 * look is a real colour (half of the normal colour over the strip), never the alpha of the view. Pure arithmetic on
 * ARGB colours plus the read of the theme list; nothing here draws.
 */
internal object StripIconColors {
    /** What a normal icon gets against the strip (WCAG AA for text; icons need 3, this is the target). */
    const val NORMAL_CONTRAST = 4.5
    /** Never below this, whatever the design says. */
    const val MIN_CONTRAST = 3.0
    /** What an unavailable icon still has: visibly "off", still recognisable. */
    const val OFF_CONTRAST = 2.0
    const val OFF_MIX = 0.5f
    /** Between a switched-on and a switched-off tool of a theme that tells them apart: at least this contrast ratio. */
    const val MIN_STATE_STEP = 1.25

    private val PRESSED = intArrayOf(android.R.attr.state_pressed)
    private val PRESSED_ACTIVATED = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_activated)
    private val ACTIVATED = intArrayOf(android.R.attr.state_activated)
    private val NONE = intArrayOf()

    /** What is really behind the icons: the strip colour laid over the keyboard background (each may be translucent; the keyboard background over black). */
    fun seenBackground(colors: Colors): Seen = Seen.over(colors.get(ColorType.STRIP_BACKGROUND), Seen.of(colors.get(ColorType.MAIN_BACKGROUND)))

    /**
     * What is really behind the icon of the left key: the disc (the flat style paints the functional key colour as an oval over the strip,
     * and the colour may be translucent), or the strip. In the other styles the expand key's background colour over the strip.
     */
    fun leftKeySurface(colors: Colors, flatStyle: Boolean, circle: Boolean): Seen {
        val strip = seenBackground(colors)
        return if (flatStyle) { if (circle) Seen.over(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND), strip) else strip }
        else Seen.over(colors.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND), strip)
    }

    /** The same as an opaque colour int (for tags and colour arithmetic). */
    fun background(colors: Colors): Int = seenBackground(colors).argb

    /**
     * [fg] moved towards white or black (the nearer way to more contrast; the hue stays as long as possible) until it has [target] against
     * [surface]. Opaque. The background is a [Seen] colour on purpose (stage 88c): a translucent design colour cannot be passed as a
     * background, it has to be laid over what is behind it first ([Seen.over]); the contrast is that of what is drawn.
     */
    fun ensureContrast(fg: Int, surface: Seen, target: Double = NORMAL_CONTRAST): Int {
        val bg = surface.argb
        val solid = RgbColors.over(fg, bg)
        if (RgbColors.contrast(solid, bg) >= target) return solid
        // keep the direction the design chose (a lighter icon on a darker ground gets lighter), the other one only if that cannot reach the contrast
        val preferred = if (RgbColors.luminance(solid) >= RgbColors.luminance(bg)) RgbColors.WHITE else RgbColors.BLACK
        val other = if (preferred == RgbColors.BLACK) RgbColors.WHITE else RgbColors.BLACK
        val way = if (RgbColors.contrast(preferred, bg) >= target) preferred else other
        var t = 0f
        var result = solid
        while (t < 1f && RgbColors.contrast(result, bg) < target) {
            t += 0.02f
            result = RgbColors.lerp(solid, way, t)
        }
        return result
    }

    /** The "off" colour of an icon with the normal colour [normal]: half of it over [bg], lifted to [OFF_CONTRAST] if that is too faint. */
    fun off(normal: Int, surface: Seen, minContrast: Double = OFF_CONTRAST): Int {
        val bg = surface.argb
        var t = OFF_MIX
        var result = RgbColors.lerp(bg or RgbColors.BLACK, normal or RgbColors.BLACK, t)
        while (t < 1f && RgbColors.contrast(result, bg) < minContrast) {
            t += 0.05f
            result = RgbColors.lerp(bg or RgbColors.BLACK, normal or RgbColors.BLACK, t)
        }
        return result
    }

    /** The colour list the theme gives toolbar keys (probed, as [helium314.keyboard.agent.RgbStrip] does), or null for a theme that uses a colour filter. */
    fun themeTint(colors: Colors, context: Context): ColorStateList? {
        val probe = TintProbe()
        val view = ImageView(context)
        view.setImageDrawable(probe)
        runCatching { colors.setColor(view, ColorType.TOOL_BAR_KEY) }
        return probe.tint
    }

    /** The theme's colour list with every state colour given the contrast of the rule. */
    fun guaranteed(colors: Colors, context: Context): ColorStateList = guaranteed(colors, themeTint(colors, context))

    fun guaranteed(colors: Colors, theme: ColorStateList?): ColorStateList {
        val seen = seenBackground(colors)
        val bg = seen.argb
        val fallback = colors.get(ColorType.TOOL_BAR_KEY)
        fun original(state: IntArray) = theme?.getColorForState(state, theme.defaultColor) ?: fallback
        // 1. the minimum contrast for every state: this is what wins over everything below
        fun of(state: IntArray) = ensureContrast(original(state), seen)
        var on = of(ACTIVATED)
        var off = of(NONE)
        // 2. a theme that shows a switched-on tool differently from a switched-off one keeps the difference: the switched-on colour goes
        //    further away from the ground (on ITS side, so its contrast only grows) until it is not the weaker of the two and 1.25:1 apart;
        //    where even white or black is not enough, it stays at that. If "on" is at that end and still not apart (the switched-off colour is
        //    itself at the end), the switched-off colour comes back towards the ground, but never below the minimum contrast
        if (original(ACTIVATED) != original(NONE)) {
            fun apart() = RgbColors.contrast(on, bg) >= RgbColors.contrast(off, bg) && RgbColors.contrast(on, off) >= MIN_STATE_STEP
            val extreme = if (RgbColors.luminance(on) >= RgbColors.luminance(bg)) RgbColors.WHITE else RgbColors.BLACK
            val start = on
            var t = 0f
            while (t < 1f && !apart()) { t = (t + 0.05f).coerceAtMost(1f); on = RgbColors.lerp(start, extreme, t) }
            fun tooClose() = RgbColors.contrast(on, off) < MIN_STATE_STEP || RgbColors.contrast(on, bg) < RgbColors.contrast(off, bg)
            if (tooClose()) {
                val offStart = off
                var u = 0f
                while (u < 1f && tooClose()) {
                    u = (u + 0.004f).coerceAtMost(1f)
                    val next = RgbColors.lerp(offStart, bg or RgbColors.BLACK, u)
                    if (RgbColors.contrast(next, bg) < NORMAL_CONTRAST) break
                    off = next
                }
            }
        }
        // 3. the hard rule, last: no state leaves here below the floor
        fun hard(c: Int) = if (RgbColors.contrast(c, bg) < MIN_CONTRAST) ensureContrast(c, seen) else c
        return ColorStateList(arrayOf(PRESSED_ACTIVATED, PRESSED, ACTIVATED, NONE),
            intArrayOf(hard(of(PRESSED_ACTIVATED)), hard(of(PRESSED)), hard(on), hard(off)))
    }

    /** The colour of an icon at rest in [tint] (not pressed, not activated). */
    fun normalOf(tint: ColorStateList) = tint.getColorForState(NONE, tint.defaultColor)

    private class TintProbe : Drawable() {
        var tint: ColorStateList? = null
        override fun setTintList(tint: ColorStateList?) { this.tint = tint }
        override fun draw(canvas: Canvas) = Unit
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
