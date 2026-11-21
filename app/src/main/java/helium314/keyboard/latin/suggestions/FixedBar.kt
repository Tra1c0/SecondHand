// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.Seen
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.content.Context
import android.content.res.ColorStateList
import androidx.core.graphics.drawable.DrawableCompat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.view.children
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Fork, stage 85, refined in stage 88: the geometry of the fixed bar. The tool row has no scrolling and no expand arrow.
 * Every item (the left key and each tool) has one cell width and equal gaps before, between and after (spacers of equal
 * weight, one layout pass), the margin at both ends is the same ([EDGE_DP]): the **centres are evenly spread** from edge
 * to edge, as in the owner's reference screenshots (also with the circle at the end), and the place of a slot depends only
 * on the number of tools, never on which tool it is or what is typed. Every glyph is scaled into one box (24 dp, 20 dp in
 * the compact row from [BarTools.FULL_SIZE_MAX] + 1 tools), so the visible gaps between plain glyphs are equal too; only
 * the circle of a last microphone key (34 dp) is wider than a glyph. A tool that is unavailable in the field stays in its
 * place in the "off" look (a real colour, a lock), see [StripIconColors].
 */
/** Counters for the test that a strip update while typing builds, looks up and arranges nothing for the bar (stage 85 review, point 9). */
internal object BarCounters {
    var gridLookups = 0
    var barBuilds = 0
    var arranges = 0
    var labelLookups = 0
    fun reset() { gridLookups = 0; barBuilds = 0; arranges = 0; labelLookups = 0 }
}

internal object FixedBar {
    /** Margin of the left key and of the right end of the row. */
    private const val EDGE_DP = 6f
    private const val GLYPH_DP = 24f
    private const val COMPACT_GLYPH_DP = 20f
    private const val FULL_CELL_DP = 40f
    private const val FLAT_CELL_DP = 34f
    private const val COMPACT_CELL_DP = 30f
    private const val CIRCLE_DP = 34f
    private const val COMPACT_CIRCLE_DP = 30f
    /** The disc behind the left key is a circle of 20 in a 24.8 viewport (`toolbar_expand_key_background`). */
    private const val DISC_FRACTION = 20f / 24.8f

    /** All measures of one arrangement, in pixels. Every item has the same cell, so the centres are evenly spread. */
    class Sizes(private val density: Float, flat: Boolean, compact: Boolean, stripHeight: Int) {
        private fun px(dp: Float) = Math.round(dp * density)
        /** The flat look has its own icons, drawn for the whole 34 dp button (the reference screenshots): they stay at their size ([centred]). */
        val centred = flat && !compact
        /** The width of every button (and of the left key); the height is [height]. */
        val cell = px(if (compact) COMPACT_CELL_DP else if (flat) FLAT_CELL_DP else FULL_CELL_DP)
        val glyph = if (centred) cell else px(if (compact) COMPACT_GLYPH_DP else GLYPH_DP)
        val circle = minOf(px(if (compact) COMPACT_CIRCLE_DP else CIRCLE_DP), cell)
        /** Room for the finger around the glyph on each side. */
        val hit = (cell - glyph) / 2
        val circleCell = cell
        val height = if (flat || compact) cell else stripHeight
        val leftHeight = if (flat || compact) cell else minOf(cell, stripHeight)
        /** The visible box of the left key: its disc, or only the glyph in the flat style (no disc). */
        val leftVisual = if (flat) minOf(glyph, px(GLYPH_DP)) else Math.round(DISC_FRACTION * minOf(cell, leftHeight))
        /** From the screen edge to the left key and from the last button to the screen edge. */
        val edge = px(EDGE_DP)
        val leftMarginStart = edge
        val leftMarginEnd = 0
        val rowMarginEnd = edge
    }

    fun sizes(view: View, flat: Boolean, count: Int): Sizes {
        val stripHeight = view.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        return Sizes(view.resources.displayMetrics.density, flat, BarTools.isCompact(count), stripHeight)
    }

    fun edgePx(density: Float) = Math.round(EDGE_DP * density)

    /** The left key of the other styles: as big as a tool button and as far from the edge as the row is from the right one. */
    fun arrangeLeftKey(expandKey: ImageButton) {
        val density = expandKey.resources.displayMetrics.density
        val size = Math.round(FULL_CELL_DP * density)
        val stripHeight = expandKey.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        (expandKey.layoutParams as LinearLayout.LayoutParams).apply { width = size; height = minOf(size, stripHeight); marginStart = edgePx(density) }
        expandKey.scaleType = ImageView.ScaleType.CENTER
    }

    /**
     * Replaces the horizontal scroll view of the strip by a plain container with the same id around the same
     * `toolbar` row, and returns it. The row is stretched over the room between the left key and the right margin;
     * a spacer is put before every button.
     */
    fun install(wrapper: ViewGroup, scroll: View, toolbar: ViewGroup): FrameLayout {
        val index = wrapper.indexOfChild(scroll)
        val id = scroll.id
        (scroll as ViewGroup).removeAllViews()
        wrapper.removeView(scroll)
        val container = FrameLayout(wrapper.context)
        container.id = id
        container.visibility = View.GONE
        (toolbar as LinearLayout).apply { minimumWidth = 0; setPadding(0, 0, 0, 0) }
        container.addView(toolbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        wrapper.addView(container, index, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = edgePx(wrapper.resources.displayMetrics.density)
        })
        (wrapper as? LinearLayout)?.layoutTransition = null // the bar switches without animation, in one layout pass
        return container
    }

    /** A tool button of the bar: draws the lock of the "off" look. */
    fun newButton(context: Context): ImageButton = BarToolButton(context)

    /** Fills the row with [buttons], a spacer before each; the spacers spread them (see [arrange]). */
    fun populate(toolbar: ViewGroup, buttons: List<ImageButton>) {
        toolbar.removeAllViews()
        buttons.forEach { button ->
            val gap = Space(toolbar.context)
            gap.setTag(R.id.agent_bar_gap, true)
            toolbar.addView(gap, LinearLayout.LayoutParams(0, 0, 1f))
            toolbar.addView(button)
        }
    }

    /**
     * Edit mode (stage 86): every slot gets an outline so that the places are visible. The slot of the tool that is being
     * dragged ([ghost]) is the gap: an empty, filled slot where it would land (stage 88). Call after [arrange].
     */
    fun markEditing(toolbar: ViewGroup, flat: Boolean, colors: Colors, ghost: ToolbarKey?) {
        val density = toolbar.resources.displayMetrics.density
        val line = if (flat) FlatStyle.STRIP_CIRCLE_ICON else colors.get(ColorType.ACTION_KEY_BACKGROUND)
        slots(toolbar).forEach { button ->
            val isGhost = ghost != null && button.tag == ghost
            button.imageAlpha = if (isGhost) 0 else 255
            if (isCircle(button) && !isGhost) return@forEach // the circle is outline enough
            button.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(if (isGhost) androidx.core.graphics.ColorUtils.setAlphaComponent(line, 0x44) else android.graphics.Color.TRANSPARENT)
                setStroke(Math.round(1.5f * density), line)
            }
        }
    }

    /** The tool buttons of the row that take part in the layout (the voice key may be hidden without a voice input). */
    fun slots(toolbar: ViewGroup): List<ImageButton> =
        toolbar.children.filter { it.visibility != View.GONE }.filterIsInstance<ImageButton>().toList()

    /** Sets the sizes, the gaps, the compact size, the colours and the circle of the last microphone key. Cheap; call after every change of the row. */
    fun arrange(toolbar: ViewGroup, expandKey: ImageButton, flat: Boolean, colors: Colors) {
        val slots = slots(toolbar)
        val s = sizes(toolbar, flat, slots.size)
        val lastIsCircle = slots.lastOrNull()?.tag in BarTools.MIC_KEYS
        (expandKey.layoutParams as LinearLayout.LayoutParams).apply {
            width = s.cell; height = s.leftHeight; marginStart = s.leftMarginStart; marginEnd = s.leftMarginEnd
        }
        expandKey.requestLayout()
        ((toolbar.parent as? View)?.layoutParams as? LinearLayout.LayoutParams)?.apply { marginEnd = s.rowMarginEnd }
        val tint = StripIconColors.guaranteed(colors, toolbar.context)
        val stripBackground = StripIconColors.background(colors)
        val children = toolbar.children.toList()
        children.forEachIndexed { i, view ->
            if (view !is ImageButton) {
                // the gap before a button follows the button
                view.visibility = children.getOrNull(i + 1)?.visibility ?: View.GONE
                return@forEachIndexed
            }
            val circle = lastIsCircle && view === slots.last()
            val width = if (circle) s.circleCell else s.cell
            view.id = R.id.agent_bar_tool
            view.layoutParams = LinearLayout.LayoutParams(width, s.height).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            // the glyph is scaled into one box for every tool, so that they all look equally big
            view.setPadding((width - s.glyph) / 2, (s.height - s.glyph) / 2, (width - s.glyph) / 2, (s.height - s.glyph) / 2)
            view.scaleType = if (s.centred) ImageView.ScaleType.CENTER else ImageView.ScaleType.FIT_CENTER
            if (flat) view.background = null else colors.setBackground(view, ColorType.STRIP_BACKGROUND)
            view.setTag(R.id.agent_bar_circle, null)
            view.setTag(R.id.agent_bar_tint, tint)
            view.setTag(R.id.agent_bar_icon_bg, stripBackground)
            if (circle) circle(view, flat, colors, s, tint)
            applyIcon(view)
        }
    }

    private fun circle(button: ImageButton, flat: Boolean, colors: Colors, s: Sizes, tint: ColorStateList) {
        val fill = if (flat) FlatStyle.STRIP_CIRCLE else colors.get(ColorType.ACTION_KEY_BACKGROUND)
        // an icon on the accent circle is a large graphic object: 3:1 is the rule here (the design's white on a pink accent stays white)
        // the badge paints its colour OPAQUE (its paint alpha is forced to 0xFF, see CircleBadge): what is seen is the RGB of the colour, whatever
        // alpha the design gave it (checked against pixels in BarIconVisibilityTest, stage 88c review B)
        val drawn = fill or RgbColors.BLACK
        val icon = StripIconColors.ensureContrast(if (flat) FlatStyle.STRIP_CIRCLE_ICON else colors.get(ColorType.ACTION_KEY_ICON), Seen.of(drawn), StripIconColors.MIN_CONTRAST)
        button.background = CircleBadge(fill, s.circle)
        button.setTag(R.id.agent_bar_circle, s.circle)
        button.setTag(R.id.agent_bar_tint, ColorStateList.valueOf(icon))
        button.setTag(R.id.agent_bar_icon_bg, drawn)
    }

    /** Whether the slot is drawn as the circle at the right end. */
    fun isCircle(button: View) = button.getTag(R.id.agent_bar_circle) is Int

    /** The icon colour of [button] for its state: the guaranteed colour, or the "off" colour. The circle's icon keeps a colour filter, as before. */
    private fun applyIcon(button: ImageButton) {
        val drawable = button.drawable ?: return
        val tint = button.getTag(R.id.agent_bar_tint) as? ColorStateList ?: return
        val bg = button.getTag(R.id.agent_bar_icon_bg) as? Int ?: return
        val off = button.getTag(R.id.agent_bar_off) != null
        val offColor = StripIconColors.off(StripIconColors.normalOf(tint), Seen.of(bg)) // the tag holds an opaque colour (strip background or a drawn circle)
        if (isCircle(button)) {
            drawable.setTintList(null)
            drawable.colorFilter = PorterDuffColorFilter(if (off) offColor else StripIconColors.normalOf(tint), PorterDuff.Mode.SRC_IN)
        } else {
            DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
            DrawableCompat.setTintList(drawable, if (off) ColorStateList.valueOf(offColor) else tint)
            drawable.state = button.drawableState // a drawable only becomes stateful with the tint list
        }
        (button as? BarToolButton)?.badge(off, offColor)
    }

    /**
     * The "off" look of an unavailable tool: it keeps its cell, its size and its place; the icon gets a real colour (half of
     * the normal one over the strip, at least 2:1) and a small lock, so it is recognisable and clearly not available. A
     * dimmed tool does not react, except while the bar is edited (then it can still be moved or taken out).
     */
    fun setAvailable(button: View, reason: BarTools.Reason?, editing: Boolean, label: CharSequence?, unavailableLabel: (CharSequence?) -> CharSequence?) {
        // the tool stays enabled for the touch system so that a tap can say why it does nothing; the click handlers check [reasonOf]
        button.isEnabled = true
        button.alpha = 1f
        button.contentDescription = if (reason == null) label else unavailableLabel(label)
        if (button.getTag(R.id.agent_bar_off) == reason) return
        button.setTag(R.id.agent_bar_off, reason)
        (button as? ImageButton)?.let { applyIcon(it) }
    }

    /** Why the tool of [button] does nothing in this field, or null when it is available. */
    fun reasonOf(button: View) = button.getTag(R.id.agent_bar_off) as? BarTools.Reason

    /** The line that says why a tool does nothing, in the place of the tools (stage 88); it goes away by itself or when touched. */
    fun showHint(container: ViewGroup, text: CharSequence, colors: Colors) {
        val background = StripIconColors.background(colors)
        val d = container.resources.displayMetrics.density
        val hint = container.findViewById<HintView>(R.id.agent_bar_hint) ?: HintView(container.context).apply {
            id = R.id.agent_bar_hint
            setPadding(Math.round(8 * d), 0, Math.round(8 * d), 0)
            container.addView(this, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        hint.setBackgroundColor(background)
        hint.setTextColor(StripIconColors.ensureContrast(colors.get(ColorType.TOOL_BAR_KEY), StripIconColors.seenBackground(colors)))
        hint.show(text)
    }

    private const val HINT_MS = 2500L

    private class HintView(context: Context) : TextView(context) {
        private val hide = Runnable { visibility = View.GONE }

        init {
            gravity = android.view.Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener { visibility = View.GONE }
            visibility = View.GONE
        }

        fun show(message: CharSequence) {
            text = message
            visibility = View.VISIBLE
            bringToFront()
            removeCallbacks(hide)
            postDelayed(hide, HINT_MS)
            announceForAccessibility(message)
        }
    }

    fun hideHint(container: ViewGroup) {
        container.findViewById<View>(R.id.agent_bar_hint)?.visibility = View.GONE
    }

    /** The visible box of the left key (its disc, or the glyph in the flat style) in the coordinates of its parent, for the arrangement of [count] tools. */
    fun leftBox(expandKey: View, flat: Boolean, count: Int): RectF {
        val v = sizes(expandKey, flat, count).leftVisual
        val cx = (expandKey.left + expandKey.right) / 2f
        val cy = (expandKey.top + expandKey.bottom) / 2f
        return RectF(cx - v / 2f, cy - v / 2f, cx + v / 2f, cy + v / 2f)
    }

    /** The visible box of a slot in the coordinates of its row: the circle, or the glyph area. */
    fun visualBox(button: View): RectF {
        val circle = button.getTag(R.id.agent_bar_circle) as? Int
        if (circle != null) {
            val cx = (button.left + button.right) / 2f
            val cy = (button.top + button.bottom) / 2f
            return RectF(cx - circle / 2f, cy - circle / 2f, cx + circle / 2f, cy + circle / 2f)
        }
        return RectF((button.left + button.paddingLeft).toFloat(), (button.top + button.paddingTop).toFloat(),
            (button.right - button.paddingRight).toFloat(), (button.bottom - button.paddingBottom).toFloat())
    }

    /**
     * The horizontal centres of [count] slots in the row, from the count alone (as the layout puts them: one cell width, equal
     * gaps before each cell). Independent of whether the views have been laid out since the last change. The circle of a last
     * microphone key is a few dp wider; this is the layout without it, which does not depend on which tool is where, so the
     * place under a finger never flickers while a gap opens.
     */
    fun slotCentres(toolbar: ViewGroup, flat: Boolean, count: Int = slots(toolbar).size): List<Float> {
        val width = toolbar.width.toFloat()
        if (count == 0) return emptyList()
        if (width <= 0f) return slots(toolbar).map { (it.left + it.right) / 2f }
        val cell = sizes(toolbar, flat, count).cell.toFloat()
        val gap = (width - count * cell) / count
        return (0 until count).map { (it + 1) * gap + it * cell + cell / 2f }
    }

    /** The index among [count] slots of the one whose centre is nearest to the horizontal position [x] of the row. */
    fun slotIndexAt(toolbar: ViewGroup, flat: Boolean, x: Float, count: Int): Int =
        slotCentres(toolbar, flat, count).let { centres -> centres.indices.minByOrNull { Math.abs(centres[it] - x) } ?: 0 }

    /** A filled circle in the middle of its bounds, [diameter] wide; a little lighter while pressed. */
    private class CircleBadge(private val color: Int, private val diameter: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CircleBadge.color }
        private var pressed = false

        override fun draw(canvas: Canvas) {
            val b = bounds
            val radius = minOf(b.width(), b.height(), diameter) / 2f
            paint.alpha = if (pressed) 0xB0 else 0xFF
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), radius, paint)
        }

        override fun isStateful() = true
        override fun onStateChange(state: IntArray): Boolean {
            val now = state.contains(android.R.attr.state_pressed)
            if (now == pressed) return false
            pressed = now
            invalidateSelf()
            return true
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(filter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** Shows or hides the small redo arrow on the undo tool (stage 88: "Kontextwerkzeuge"); the slot keeps its size and place. */
    fun setRedoOffered(button: View, offered: Boolean) {
        (button as? BarToolButton)?.redoOffered(offered)
    }

    fun isRedoOffered(button: View) = (button as? BarToolButton)?.redo == true

    /** A tool button that draws a small lock at the lower right of its glyph while the tool is unavailable (the second sign besides the colour), and the redo arrow after an undo. */
    private class BarToolButton(context: Context) : ImageButton(context, null, R.attr.suggestionWordStyle) {
        private var off = false
        var redo = false
            private set

        fun redoOffered(value: Boolean) { if (redo != value) { redo = value; invalidate() } }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG)

        fun badge(off: Boolean, color: Int) {
            paint.color = color
            stroke.color = color
            if (this.off != off) { this.off = off; invalidate() } else if (off) invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (redo) drawRedoArrow(canvas)
            if (!off) return
            val d0 = resources.displayMetrics.density
            // the lock follows the size of the glyph (the compact row has smaller glyphs)
            val d = d0 * ((width - paddingLeft - paddingRight) / (GLYPH_DP * d0)).coerceIn(0.7f, 1f)
            val right = width - paddingRight + 2f * d
            val bottom = height - paddingBottom + 1.5f * d
            val w = 9f * d
            val h = 6.5f * d
            val left = right - w
            val top = bottom - h
            // a halo in the colour behind the icon, so that the lock reads where it lies over the glyph
            (getTag(R.id.agent_bar_icon_bg) as? Int)?.let { behind ->
                halo.color = behind
                halo.strokeWidth = 4f * d
                canvas.drawRoundRect(left - 1.2f * d, top - 1.2f * d, right + 1.2f * d, bottom + 1.2f * d, 2.4f * d, 2.4f * d, halo.apply { style = Paint.Style.FILL })
                halo.style = Paint.Style.STROKE
                canvas.drawArc(left + 1.6f * d, top - 4.4f * d, right - 1.6f * d, top + 3.2f * d, 180f, 180f, false, halo)
            }
            canvas.drawRoundRect(left, top, right, bottom, 1.5f * d, 1.5f * d, paint)
            stroke.strokeWidth = 1.4f * d
            canvas.drawArc(left + 1.6f * d, top - 4.4f * d, right - 1.6f * d, top + 3.2f * d, 180f, 180f, false, stroke)
        }

        /** A small arrow that turns clockwise (redo) at the upper right of the glyph, in the colour of the icon. */
        private fun drawRedoArrow(canvas: Canvas) {
            val d = resources.displayMetrics.density
            val colour = (getTag(R.id.agent_bar_tint) as? ColorStateList)?.let { StripIconColors.normalOf(it) } ?: return
            stroke.color = colour
            paint.color = colour
            stroke.strokeWidth = 1.6f * d
            val right = width - paddingRight + 3f * d
            val top = paddingTop - 3f * d
            val r = 4f * d
            val cx = right - r
            val cy = top + r
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 200f, 250f, false, stroke)
            // the head at the end of the arc, pointing clockwise
            val a = Math.toRadians(450.0)
            val hx = cx + (r * Math.cos(a)).toFloat()
            val hy = cy + (r * Math.sin(a)).toFloat()
            val path = android.graphics.Path().apply {
                moveTo(hx - 2.6f * d, hy - 1.6f * d); lineTo(hx + 2.4f * d, hy - 0.4f * d); lineTo(hx - 0.4f * d, hy + 2.4f * d); close()
            }
            canvas.drawPath(path, paint)
        }
    }

    /** The tool of a slot button. */
    fun keyOf(button: View) = button.tag as? ToolbarKey
}
