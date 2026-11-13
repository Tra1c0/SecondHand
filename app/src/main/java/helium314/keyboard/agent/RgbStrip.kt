// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.res.ColorStateList
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbColors
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * RGB light for the suggestion strip (stage 26c): text colour of the word suggestions
 * ([RgbArea.SUGGESTIONS]) and icon colour of the strip buttons ([RgbArea.TOOLBAR], including the
 * Flat-style buttons). Only colours are changed, never text or layout. The original colour is kept
 * in a view tag and comes back when the light goes off; a colour the strip set in the meantime
 * (new suggestions, activated tool) becomes the new original. Always readable against the strip
 * background ([RgbColors.readable]).
 */
object RgbStrip {
    private class Original(val text: ColorStateList?, val filter: ColorFilter?) {
        var lastColor = 0
        var lastFilter: ColorFilter? = null
    }

    private val stripLocation = IntArray(2)
    private val viewLocation = IntArray(2)

    /** Colours (or with [on] false restores) all lit views of [strip]. */
    fun paint(strip: View, on: Boolean) {
        val s = RgbLighting.settings
        val words = strip.findViewById<ViewGroup>(R.id.suggestions_strip)
        val buttons = listOfNotNull<View>(strip.findViewById(R.id.suggestions_strip_toolbar_key),
            strip.findViewById(R.id.toolbar), strip.findViewById(R.id.pinned_keys))
        val lightWords = on && RgbArea.SUGGESTIONS in s.areas
        val lightButtons = on && RgbArea.TOOLBAR in s.areas
        if (lightWords || lightButtons) strip.getLocationInWindow(stripLocation)
        val colors = Settings.getValues().mColors
        val background = colors.get(ColorType.STRIP_BACKGROUND)
        words?.let { group ->
            for (i in 0 until group.childCount) {
                val view = group.getChildAt(i) as? TextView ?: continue
                if (lightWords && view.visibility == View.VISIBLE) paintText(view, strip, background) else restore(view)
            }
        }
        val icon = colors.get(ColorType.TOOL_BAR_KEY)
        buttons.forEach { forEachImage(it) { view ->
            if (lightButtons && visibleWithin(view, strip)) paintImage(view, strip, icon, background) else restore(view)
        } }
    }

    /** Visible up to [root] (not [View.isShown], which also needs a window). */
    private fun visibleWithin(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }

    private fun forEachImage(view: View, block: (ImageView) -> Unit) {
        if (view is ImageView) block(view)
        else if (view is ViewGroup) for (i in 0 until view.childCount) forEachImage(view.getChildAt(i), block)
    }

    private fun light(view: View, strip: View): Int {
        view.getLocationInWindow(viewLocation)
        val x = (viewLocation[0] - stripLocation[0] + view.width / 2f) / strip.width.coerceAtLeast(1)
        return RgbLighting.engine.light(x.coerceIn(0f, 1f), 0f, -1, RgbLighting.drawTimeMs)
    }

    private fun paintText(view: TextView, strip: View, background: Int) {
        var original = view.getTag(R.id.agent_rgb_original) as? Original
        if (original == null || view.currentTextColor != original.lastColor) {
            original = Original(view.textColors, null)
            view.setTag(R.id.agent_rgb_original, original)
        }
        val theme = original.text?.defaultColor ?: view.currentTextColor
        val color = RgbColors.label(theme, light(view, strip), true, background)
        original.lastColor = color
        if (view.currentTextColor != color) view.setTextColor(color)
    }

    private fun paintImage(view: ImageView, strip: View, theme: Int, background: Int) {
        var original = view.getTag(R.id.agent_rgb_original) as? Original
        if (original == null || view.colorFilter !== original.lastFilter) {
            original = Original(null, view.colorFilter)
            view.setTag(R.id.agent_rgb_original, original)
        }
        val color = RgbColors.label(theme, light(view, strip), true, background) or BLACK
        if (original.lastFilter != null && original.lastColor == color) return
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        original.lastColor = color
        original.lastFilter = filter
        view.colorFilter = filter
    }

    private fun restore(view: View) {
        val original = view.getTag(R.id.agent_rgb_original) as? Original ?: return
        view.setTag(R.id.agent_rgb_original, null)
        if (view is TextView && view.currentTextColor == original.lastColor) original.text?.let { view.setTextColor(it) }
        if (view is ImageView && view.colorFilter === original.lastFilter) view.colorFilter = original.filter
    }

    private const val BLACK = 0xFF000000.toInt()
}
