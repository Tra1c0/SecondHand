// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.RgbTarget
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import java.lang.ref.WeakReference

/**
 * RGB light for the suggestion strip (stage 26c): text colour of the word suggestions
 * ([RgbArea.SUGGESTIONS]) and icon colour of the strip buttons ([RgbArea.TOOLBAR], including the
 * Flat-style buttons). Only colours are changed, never text or layout. The original colour is kept
 * in a view tag and comes back when the light goes off; a colour the strip set in the meantime
 * (new suggestions) becomes the new original. Always readable against the strip background
 * ([RgbColors.label]).
 *
 * Toolbar buttons are coloured by the theme with a tint list (activated = tool on, otherwise off,
 * some themes also pressed). The light keeps those states: only the activated state takes the
 * light; switched-off and pressed buttons keep their theme colour, and the alpha of every state is
 * kept. Buttons coloured by a view colour filter (expand key) get the light as a filter.
 *
 * Cost per frame: the views are collected once per strip build (and again only when the children
 * change); per view one position lookup and one light value; a new colour object only when the
 * colour of that view has changed.
 */
object RgbStrip {
    private class Original(val text: ColorStateList?, val filter: ColorFilter?, val tint: ColorStateList?) {
        var lastColor = 0
        var lastFilter: ColorFilter? = null
        var lastTint: ColorStateList? = null
    }

    private val stripLocation = IntArray(2)
    private val viewLocation = IntArray(2)

    /** Views of one strip, collected once per build; kept in a tag of the strip, so nothing static holds views. */
    private class Views {
        var valid = false
        val groups = arrayOfNulls<ViewGroup>(3)
        val groupChildren = Array(3) { ArrayList<View>() }
        val words = ArrayList<TextView>()
        val images = ArrayList<ImageView>()
    }

    // the theme's tint list of toolbar keys, read once per colour set
    private var probedColors: WeakReference<Colors>? = null
    private var toolbarTint: ColorStateList? = null

    /** Set by [invalidate]: the strip may have given its views their theme colours again. */
    private var forceNext = false

    /** The strip was rebuilt or changed: collect its views and set every colour again on the next [paint]. */
    fun invalidate(strip: View) {
        (strip.getTag(R.id.agent_rgb_strip_views) as? Views)?.valid = false
        forceNext = true
    }

    /** Colours (or with [on] false restores) all lit views of [strip]. */
    fun paint(strip: View, on: Boolean) {
        val views = collect(strip)
        val words = views.words
        val images = views.images
        val force = forceNext
        forceNext = false
        val s = RgbLighting.settings
        val lightWords = on && RgbArea.SUGGESTIONS in s.areas
        val lightButtons = on && RgbArea.TOOLBAR in s.areas
        if (lightWords || lightButtons) strip.getLocationInWindow(stripLocation)
        val colors = Settings.getValues().mColors
        val background = colors.get(ColorType.STRIP_BACKGROUND)
        for (view in words) {
            if (lightWords && view.visibility == View.VISIBLE) paintText(view, strip, background) else restore(view)
        }
        // restore first: a pinned copy shares its drawable with the toolbar button
        var restored = force
        for (view in images) if (!lightButtons || !visibleWithin(view, strip)) restored = restore(view) || restored
        if (!lightButtons) return
        val tint = tintOf(colors, strip)
        val filterColor = colors.get(ColorType.TOOL_BAR_EXPAND_KEY)
        for (view in images) if (visibleWithin(view, strip)) paintImage(view, strip, tint, filterColor, background, restored)
    }

    private fun collect(strip: View): Views {
        val views = strip.getTag(R.id.agent_rgb_strip_views) as? Views ?: Views().also { strip.setTag(R.id.agent_rgb_strip_views, it) }
        if (views.valid && views.unchanged()) return views
        views.valid = true
        val groups = views.groups
        groups[0] = strip.findViewById(R.id.suggestions_strip)
        groups[1] = strip.findViewById(R.id.toolbar)
        groups[2] = strip.findViewById(R.id.pinned_keys)
        views.words.clear()
        views.images.clear()
        strip.findViewById<ImageView>(R.id.suggestions_strip_toolbar_key)?.let { views.images.add(it) }
        for (i in groups.indices) {
            val list = views.groupChildren[i]
            list.clear()
            val group = groups[i] ?: continue
            for (c in 0 until group.childCount) list.add(group.getChildAt(c))
        }
        views.groupChildren[0].forEach { if (it is TextView) views.words.add(it) }
        for (i in 1..2) views.groupChildren[i].forEach { forEachImage(it) { image -> views.images.add(image) } }
        return views
    }

    /** True while every collected group still has exactly the children it had (no allocation). */
    private fun Views.unchanged(): Boolean {
        for (i in groups.indices) {
            val group = groups[i] ?: continue
            val list = groupChildren[i]
            if (group.childCount != list.size) return false
            for (c in list.indices) if (group.getChildAt(c) !== list[c]) return false
        }
        return true
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

    /**
     * The tint list the theme gives toolbar keys, read by letting [colors] colour a probe that
     * records it (Colors has no getter). Null if the theme uses a colour filter instead.
     */
    private fun tintOf(colors: Colors, strip: View): ColorStateList? {
        if (probedColors?.get() === colors) return toolbarTint
        val probe = TintProbe()
        val view = ImageView(strip.context)
        view.setImageDrawable(probe)
        runCatching { colors.setColor(view, ColorType.TOOL_BAR_KEY) }
        probedColors = WeakReference(colors)
        toolbarTint = probe.tint
        return toolbarTint
    }

    private class TintProbe : Drawable() {
        var tint: ColorStateList? = null
        override fun setTintList(tint: ColorStateList?) { this.tint = tint }
        override fun draw(canvas: Canvas) = Unit
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Light for [view] from the engine of [target] (words: suggestions, buttons: toolbar; stage 29). */
    private fun light(view: View, strip: View, target: RgbTarget): Int {
        view.getLocationInWindow(viewLocation)
        val x = (viewLocation[0] - stripLocation[0] + view.width / 2f) / strip.width.coerceAtLeast(1)
        return RgbLighting.engineFor(target).light(x.coerceIn(0f, 1f), 0f, -1, RgbLighting.drawTimeMs)
    }

    private fun paintText(view: TextView, strip: View, background: Int) {
        var original = view.getTag(R.id.agent_rgb_original) as? Original
        if (original == null || view.currentTextColor != original.lastColor) {
            original = Original(view.textColors, null, null)
            view.setTag(R.id.agent_rgb_original, original)
        }
        val theme = original.text?.defaultColor ?: view.currentTextColor
        val color = RgbColors.label(theme, light(view, strip, RgbTarget.SUGGESTIONS), true, background)
        original.lastColor = color
        if (view.currentTextColor != color) view.setTextColor(color)
    }

    private fun paintImage(view: ImageView, strip: View, tint: ColorStateList?, filterColor: Int, background: Int, force: Boolean) {
        val drawable = view.drawable ?: return
        // a view colour filter (expand key, themes without a tint list) gets the light as a filter
        val useFilter = tint == null || view.colorFilter != null
        // an icon with its own colour filter on the drawable (Flat round button) keeps its colour
        if (!useFilter && drawable.colorFilter != null) return
        var original = view.getTag(R.id.agent_rgb_original) as? Original
        if (original == null || (useFilter && view.colorFilter !== original.lastFilter)) {
            original = Original(null, view.colorFilter, if (useFilter) null else tint)
            view.setTag(R.id.agent_rgb_original, original)
        }
        val light = light(view, strip, RgbTarget.TOOLBAR)
        if (useFilter) {
            val color = RgbColors.label(filterColor, light, true, background)
            if (!force && original.lastFilter != null && original.lastColor == color) return
            val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            original.lastColor = color
            original.lastFilter = filter
            view.colorFilter = filter
            return
        }
        val theme = tint ?: return
        val color = RgbColors.label(theme.getColorForState(ACTIVATED, theme.defaultColor), light, true, background)
        if (!force && original.lastTint != null && original.lastColor == color) return
        val lit = litTint(theme, color)
        original.lastColor = color
        original.lastTint = lit
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, lit)
    }

    /**
     * [theme] with the activated state (tool on) in [activatedColor]. Switched off keeps the theme
     * colour; pressed keeps the theme colour where the theme shows pressing with its own colour.
     */
    internal fun litTint(theme: ColorStateList, activatedColor: Int): ColorStateList {
        fun of(state: IntArray) = theme.getColorForState(state, theme.defaultColor)
        val pressedActivated = of(PRESSED_ACTIVATED).takeIf { it != of(ACTIVATED) } ?: activatedColor
        return ColorStateList(
            arrayOf(PRESSED_ACTIVATED, PRESSED, ACTIVATED, NONE),
            intArrayOf(pressedActivated, of(PRESSED), activatedColor, of(NONE))
        )
    }

    /** Returns true if something was restored. */
    private fun restore(view: View): Boolean {
        val original = view.getTag(R.id.agent_rgb_original) as? Original ?: return false
        view.setTag(R.id.agent_rgb_original, null)
        if (view is TextView && view.currentTextColor == original.lastColor) original.text?.let { view.setTextColor(it) }
        if (view is ImageView) {
            if (original.lastFilter != null && view.colorFilter === original.lastFilter) view.colorFilter = original.filter
            if (original.lastTint != null) view.drawable?.let { DrawableCompat.setTintList(it, original.tint) }
        }
        return true
    }

    private val PRESSED = intArrayOf(android.R.attr.state_pressed)
    private val PRESSED_ACTIVATED = intArrayOf(android.R.attr.state_pressed, android.R.attr.state_activated)
    private val ACTIVATED = intArrayOf(android.R.attr.state_activated)
    private val NONE = intArrayOf()
}
