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
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.children
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Fork, stage 85: the geometry of the fixed bar. The tool row has no scrolling and no expand arrow: it is a row of
 * cells of equal width, one per tool, and a cell is as wide as the row divided by the number of tools. So the
 * place of a slot depends only on how many tools there are, never on which ones or on what is typed. With
 * more than [BarTools.FULL_SIZE_MAX] tools the icons are smaller (compact row). The last tool is drawn in a circle
 * when it is a microphone key. A tool that is unavailable in the field stays in its cell, dimmed.
 */
internal object FixedBar {
    private const val CIRCLE_MAX_DP = 34f
    /** Compact row: the icon gets this much room above and below inside the 40 dp strip (24 dp icon at full size). */
    private const val COMPACT_PADDING_DP = 10f
    const val DIMMED_ALPHA = 0.38f

    /**
     * Replaces the horizontal scroll view of the strip by a plain container with the same id around the same
     * `toolbar` row, and returns it. The row is stretched over the room between the left key and the end.
     */
    fun install(wrapper: ViewGroup, scroll: View, toolbar: ViewGroup): FrameLayout {
        val index = wrapper.indexOfChild(scroll)
        val id = scroll.id
        (scroll as? HorizontalScrollView)?.removeAllViews() ?: (scroll as ViewGroup).removeAllViews()
        wrapper.removeView(scroll)
        val container = FrameLayout(wrapper.context)
        container.id = id
        container.visibility = View.GONE
        container.addView(toolbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        (toolbar as LinearLayout).apply { minimumWidth = 0; setPadding(0, 0, 0, 0) }
        wrapper.addView(container, index, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        (wrapper as? LinearLayout)?.layoutTransition = null // the bar switches without animation, in one layout pass
        return container
    }

    /** The tool buttons of the row that take part in the layout (the voice key may be hidden without a voice input). */
    fun slots(toolbar: ViewGroup): List<ImageButton> =
        toolbar.children.filter { it.visibility != View.GONE }.filterIsInstance<ImageButton>().toList()

    /** Sets the cells, the compact size and the circle of the last microphone key. Cheap; call after every change of the row. */
    fun arrange(toolbar: ViewGroup, flat: Boolean, colors: Colors) {
        val density = toolbar.resources.displayMetrics.density
        val slots = slots(toolbar)
        val compact = BarTools.isCompact(slots.size)
        toolbar.children.filterIsInstance<ImageButton>().forEach { button ->
            button.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            val vertical = if (compact) Math.round(COMPACT_PADDING_DP * density) else 0
            button.setPadding(0, vertical, 0, vertical)
            button.scaleType = if (compact) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER
            if (flat) button.background = null else colors.setBackground(button, ColorType.STRIP_BACKGROUND)
            colors.setColor(button, ColorType.TOOL_BAR_KEY)
            button.setTag(R.id.agent_bar_circle, null)
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
    fun setAvailable(button: View, available: Boolean, label: CharSequence?, unavailableLabel: (CharSequence?) -> CharSequence?) {
        button.isEnabled = available
        button.alpha = if (available) 1f else DIMMED_ALPHA
        button.contentDescription = if (available) label else unavailableLabel(label)
    }

    /** The index among [slots] of the cell that contains the horizontal position [x] of the row, clamped to the row. */
    fun slotIndexAt(toolbar: ViewGroup, x: Float): Int {
        val slots = slots(toolbar)
        if (slots.isEmpty()) return 0
        val index = slots.indexOfFirst { x < it.right }
        return if (index < 0) slots.size - 1 else index
    }

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
