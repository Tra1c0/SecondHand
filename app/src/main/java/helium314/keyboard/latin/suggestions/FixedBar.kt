// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.view.children
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Fork, stage 85: the geometry of the fixed bar. The tool row has no scrolling and no expand arrow. Its buttons have
 * one size and equal gaps before, between and after them (spacers of equal weight, one layout pass), and the right
 * margin equals the left margin of the left key. So the centres of the left key and the tools are evenly spread
 * from edge to edge, and the place of a slot depends only on how many tools there are, never on which ones or on
 * what is typed. With more than [BarTools.FULL_SIZE_MAX] tools the buttons and icons are smaller (compact row). The
 * last tool is drawn in a circle when it is a microphone key. A tool that is unavailable in the field stays in its
 * place, dimmed.
 */
internal object FixedBar {
    private const val CIRCLE_MAX_DP = 34f
    /** Margin of the left key and of the right end of the row. */
    private const val EDGE_DP = 6f
    private const val FLAT_BUTTON_DP = 34f
    private const val BUTTON_DP = 40f
    private const val COMPACT_BUTTON_DP = 30f
    /** Compact row: the 24 dp icon is shown at 20 dp, so it gets this much room above and below inside the 40 dp strip. */
    private const val COMPACT_PADDING_DP = 10f
    const val DIMMED_ALPHA = 0.38f

    fun edgePx(density: Float) = Math.round(EDGE_DP * density)

    /** The left key of the other styles: as big as a tool button and as far from the edge as the row is from the right one. */
    fun arrangeLeftKey(expandKey: ImageButton) {
        val density = expandKey.resources.displayMetrics.density
        val size = Math.round(BUTTON_DP * density)
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
     * Edit mode (stage 86): every slot gets an outline so that the places are visible; the tool that is being dragged
     * ([ghost]) is shown half transparent where it would land. Call after [arrange].
     */
    fun markEditing(toolbar: ViewGroup, flat: Boolean, colors: Colors, ghost: ToolbarKey?) {
        val density = toolbar.resources.displayMetrics.density
        val line = if (flat) FlatStyle.STRIP_CIRCLE_ICON else colors.get(ColorType.ACTION_KEY_BACKGROUND)
        slots(toolbar).forEach { button ->
            if (isCircle(button)) return@forEach // the circle is outline enough
            button.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(android.graphics.Color.TRANSPARENT)
                setStroke(Math.round(1.5f * density), line)
            }
        }
    }

    /** The tool buttons of the row that take part in the layout (the voice key may be hidden without a voice input). */
    fun slots(toolbar: ViewGroup): List<ImageButton> =
        toolbar.children.filter { it.visibility != View.GONE }.filterIsInstance<ImageButton>().toList()

    /** Sets the sizes, the gaps, the compact size and the circle of the last microphone key. Cheap; call after every change of the row. */
    fun arrange(toolbar: ViewGroup, expandKey: ImageButton, flat: Boolean, colors: Colors) {
        val density = toolbar.resources.displayMetrics.density
        val slots = slots(toolbar)
        val compact = BarTools.isCompact(slots.size)
        val width = Math.round((if (compact) COMPACT_BUTTON_DP else if (flat) FLAT_BUTTON_DP else BUTTON_DP) * density)
        // the left key is as wide as a tool, so that the centres of all buttons are evenly spread
        val stripHeight = expandKey.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        (expandKey.layoutParams as LinearLayout.LayoutParams).apply {
            this.width = width
            height = if (flat || compact) width else minOf(width, stripHeight)
        }
        expandKey.requestLayout()
        val children = toolbar.children.toList()
        children.forEachIndexed { i, view ->
            if (view !is ImageButton) {
                // the gap before a button follows the button
                view.visibility = children.getOrNull(i + 1)?.visibility ?: View.GONE
                return@forEachIndexed
            }
            view.id = R.id.agent_bar_tool
            view.layoutParams = LinearLayout.LayoutParams(width, if (flat || compact) width else LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            val vertical = if (compact) Math.round(width * 0.17f) else 0
            view.setPadding(vertical, vertical, vertical, vertical)
            view.scaleType = if (compact) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER
            if (flat) view.background = null else colors.setBackground(view, ColorType.STRIP_BACKGROUND)
            colors.setColor(view, ColorType.TOOL_BAR_KEY)
            view.setTag(R.id.agent_bar_circle, null)
        }
        val last = slots.lastOrNull() ?: return
        if (last.tag in BarTools.MIC_KEYS) circle(last, flat, colors, density)
    }

    private fun circle(button: ImageButton, flat: Boolean, colors: Colors, density: Float) {
        val fill = if (flat) FlatStyle.STRIP_CIRCLE else colors.get(ColorType.ACTION_KEY_BACKGROUND)
        val icon = if (flat) FlatStyle.STRIP_CIRCLE_ICON else colors.get(ColorType.ACTION_KEY_ICON)
        button.background = CircleBadge(fill, Math.round(CIRCLE_MAX_DP * density))
        button.drawable?.mutate()?.let {
            it.setTintList(null)
            it.setColorFilter(icon, PorterDuff.Mode.SRC_IN)
        }
        button.setTag(R.id.agent_bar_circle, true)
    }

    /** Whether the slot is drawn as the circle at the right end. */
    fun isCircle(button: View) = button.getTag(R.id.agent_bar_circle) == true

    /** Dims a slot in place; it keeps its cell. A dimmed slot does not react. */
    fun setAvailable(button: View, available: Boolean, editing: Boolean, label: CharSequence?, unavailableLabel: (CharSequence?) -> CharSequence?) {
        // customizing is about the layout: while the bar is edited a dimmed tool can still be taken out or moved
        button.isEnabled = available || editing
        button.alpha = if (available) 1f else DIMMED_ALPHA
        button.contentDescription = if (available) label else unavailableLabel(label)
    }

    /**
     * The horizontal centres of the slots in the row, from the count alone (as the layout puts them: one button width, equal
     * gaps before each button). Independent of whether the views have been laid out since the last change, which matters
     * while the slots are rebuilt on every drag event.
     */
    fun slotCentres(toolbar: ViewGroup, flat: Boolean): List<Float> {
        val count = slots(toolbar).size
        val width = toolbar.width.toFloat()
        if (count == 0 || width <= 0f) return slots(toolbar).map { (it.left + it.right) / 2f }
        val density = toolbar.resources.displayMetrics.density
        val button = (if (BarTools.isCompact(count)) COMPACT_BUTTON_DP else if (flat) FLAT_BUTTON_DP else BUTTON_DP) * density
        val gap = (width - count * button) / count
        return (0 until count).map { (it + 1) * gap + it * button + button / 2f }
    }

    /** Where a new tool would be inserted at the horizontal position [x]: after as many slots as have their centre left of it. */
    fun insertionIndexAt(toolbar: ViewGroup, flat: Boolean, x: Float): Int = slotCentres(toolbar, flat).count { it < x }

    /** The index among the slots of the one whose centre is nearest to the horizontal position [x] of the row. */
    fun slotIndexAt(toolbar: ViewGroup, flat: Boolean, x: Float): Int =
        slotCentres(toolbar, flat).let { centres -> centres.indices.minByOrNull { Math.abs(centres[it] - x) } ?: 0 }

    /** A filled circle in the middle of its bounds, at most [maxDiameter] wide; a little lighter while pressed. */
    private class CircleBadge(private val color: Int, private val maxDiameter: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CircleBadge.color }
        private var pressed = false

        override fun draw(canvas: Canvas) {
            val b = bounds
            val radius = minOf(b.width(), b.height(), maxDiameter) / 2f
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

    /** The tool of a slot button. */
    fun keyOf(button: View) = button.tag as? ToolbarKey
}
