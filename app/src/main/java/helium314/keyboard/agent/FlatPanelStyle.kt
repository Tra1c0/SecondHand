// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import kotlin.math.abs

/**
 * Look of the action panel in the Flat style, after the reference screenshot of the writing
 * tools (3.25 px per dp): header with a round back button and a 22 dp title, the result in a
 * card with 28 dp corners and a 2 dp border in a fixed warm gradient, and every action as a chip
 * of 36 dp height. Ids, view types and callbacks of [AgentPanelView] stay as they are; only the
 * order changes: the result block moves above the action chips, the general description and the
 * dictation chip below them.
 *
 * While a request is running the card shows four bars instead of text. They pulse slowly in
 * steps of [PULSE_STEP_MS], which redraws the card about twelve times a second and only while
 * the request runs. With system animations off the bars stand still.
 */
internal class FlatPanelStyle private constructor(private val result: TextView, colors: Colors?) {
    private val density = result.resources.displayMetrics.density
    private val background = colors?.get(ColorType.MAIN_BACKGROUND) ?: FlatStyle.BACKGROUND
    private val surface = colors?.get(ColorType.KEY_BACKGROUND) ?: FlatStyle.KEY
    private val textColor = colors?.get(ColorType.KEY_TEXT) ?: FlatStyle.LABEL
    private val secondaryText = colors?.get(ColorType.FUNCTIONAL_KEY_TEXT) ?: FlatStyle.FUNCTIONAL_LABEL
    private val accent = colors?.get(ColorType.ACTION_KEY_BACKGROUND) ?: FlatStyle.ACCENT

    private val skeleton = SkeletonDrawable(density)
    private val card: Drawable = cardBackground(null)
    private val busyCard: Drawable = cardBackground(skeleton)
    private var busySince = 0L
    private val pulse = object : Runnable {
        override fun run() {
            val phase = ((SystemClock.uptimeMillis() - busySince) % PULSE_PERIOD_MS) / PULSE_PERIOD_MS.toFloat()
            // triangle wave between 55 % and 100 %
            skeleton.alpha = Math.round(255 * (0.55f + 0.45f * abs(1f - 2f * phase)))
            result.postDelayed(this, PULSE_STEP_MS)
        }
    }

    private fun dp(value: Float) = Math.round(value * density)

    /** Bars instead of text while a request runs. Every other panel state switches them off again. */
    fun setBusy(busy: Boolean) {
        result.removeCallbacks(pulse)
        skeleton.alpha = 255
        result.background = if (busy) busyCard else card
        result.minHeight = dp(if (busy) BUSY_CARD_MIN_DP else CARD_MIN_DP)
        val animate = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
        if (busy && animate && result.isAttachedToWindow) {
            busySince = SystemClock.uptimeMillis()
            result.postDelayed(pulse, PULSE_STEP_MS)
        }
    }

    /** Chip look for an action button, also for the workflow choices that are created later. */
    fun styleChip(button: Button, filled: Int = surface) {
        button.background = GradientDrawable().apply {
            cornerRadius = dp(if (filled == accent) CHIP_HEIGHT_DP / 2 else CHIP_RADIUS_DP).toFloat()
            val pressed = ColorUtils.blendARGB(filled, 0xFFFFFFFF.toInt(), 0.15f)
            val disabled = ColorUtils.blendARGB(filled, background, 0.6f)
            color = ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(disabled, pressed, filled)
            )
        }
        button.stateListAnimator = null // no elevation animation
        button.elevation = 0f
        button.setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(ColorUtils.setAlphaComponent(textColor, 0x66), textColor)
        ))
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, CHIP_TEXT_DP)
        button.typeface = FlatStyle.labelTypeface
        button.minimumHeight = dp(CHIP_HEIGHT_DP)
        button.minHeight = dp(CHIP_HEIGHT_DP)
        // room for a second line: long labels wrap instead of being cut
        button.setPadding(dp(CHIP_PADDING_DP), dp(CHIP_VERTICAL_DP), dp(CHIP_PADDING_DP), dp(CHIP_VERTICAL_DP))
        button.maxLines = 2
        (button.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(dp(CHIP_GAP_DP / 2), dp(CHIP_GAP_DP / 2), dp(CHIP_GAP_DP / 2), dp(CHIP_GAP_DP / 2))
    }

    private fun roundButton(button: Button, icon: Int) {
        val size = dp(ROUND_BUTTON_DP)
        val inset = dp((ROUND_BUTTON_DP - 24f) / 2)
        val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(surface) }
        val glyph = ContextCompat.getDrawable(button.context, icon)?.mutate()?.apply { setTint(textColor) }
        button.contentDescription = button.text
        button.text = ""
        button.background = if (glyph == null) circle else LayerDrawable(arrayOf(circle, glyph)).apply { setLayerInset(1, inset, inset, inset, inset) }
        button.stateListAnimator = null
        button.elevation = 0f
        button.minimumWidth = 0
        button.minWidth = 0
        button.minimumHeight = 0
        button.minHeight = 0
        button.setPadding(0, 0, 0, 0)
        button.layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_VERTICAL }
    }

    private fun cardBackground(content: Drawable?): Drawable {
        val border = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(BORDER_START, BORDER_END)).apply {
            cornerRadius = dp(CARD_RADIUS_DP).toFloat()
        }
        val fill = GradientDrawable().apply {
            cornerRadius = dp(CARD_RADIUS_DP - CARD_BORDER_DP).toFloat()
            setColor(surface)
        }
        val layers = listOfNotNull(border, fill, content).toTypedArray()
        return LayerDrawable(layers).apply {
            val inset = dp(CARD_BORDER_DP)
            setLayerInset(1, inset, inset, inset, inset)
            if (content != null) setLayerInset(2, dp(CARD_PADDING_DP), dp(SKELETON_TOP_DP), dp(SKELETON_END_DP), 0)
        }
    }

    private fun apply(panel: AgentPanelView, body: LinearLayout) {
        panel.setBackgroundColor(background)
        body.setPadding(dp(SIDE_DP), dp(4f), dp(SIDE_DP), dp(12f))

        val close = panel.findViewById<Button>(R.id.agent_close)
        val settings = panel.findViewById<Button>(R.id.agent_cloud_settings)
        val send = panel.findViewById<Button>(R.id.agent_cloud_send)
        val applyButton = panel.findViewById<Button>(R.id.agent_apply)
        val heading = close.parent as LinearLayout

        // texts first, buttons are TextViews too
        forEachView(body) { view ->
            when {
                view is Button -> styleChip(view, if (view === send || view === applyButton) accent else surface)
                view is TextView -> {
                    view.setTextColor(if (view.id == View.NO_ID) secondaryText else textColor)
                    view.typeface = FlatStyle.labelTypeface
                }
            }
        }

        // header: back button in a circle, title, settings in a circle
        heading.gravity = Gravity.CENTER_VERTICAL
        heading.minimumHeight = dp(HEADER_HEIGHT_DP)
        heading.removeView(close)
        heading.addView(close, 0)
        roundButton(close, R.drawable.ic_arrow_back)
        roundButton(settings, R.drawable.sym_keyboard_settings_lxx)
        (heading.getChildAt(1) as TextView).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, TITLE_TEXT_DP)
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(TITLE_GAP_DP)
        }

        // result block (heading, card, apply/copy) above the action chips, directly below the send/cancel pair
        val resultIndex = body.indexOfChild(result)
        val block = listOf(body.getChildAt(resultIndex - 1), result, body.getChildAt(resultIndex + 1))
        block.forEach { body.removeView(it) }
        val after = body.indexOfChild(panel.findViewById(R.id.agent_cloud_cancel))
        block.forEachIndexed { offset, view -> body.addView(view, after + 1 + offset) }

        // the general description and the dictation chip go below the actions, so that status, text and
        // result start directly under the header
        val description = body.getChildAt(body.indexOfChild(heading) + 1)
        val dictate = panel.findViewById<View>(R.id.agent_dictate)
        listOf(description, dictate).forEach { body.removeView(it); body.addView(it) }

        result.setTextSize(TypedValue.COMPLEX_UNIT_DIP, CARD_TEXT_DP)
        result.setPadding(dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP))
        (result.layoutParams as ViewGroup.MarginLayoutParams).setMargins(0, dp(4f), 0, dp(8f))
        setBusy(false)
        result.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { result.removeCallbacks(pulse) }
        })
    }

    private fun forEachView(group: ViewGroup, action: (View) -> Unit) {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is ViewGroup) {
                // rows of chips: a two-line chip must not be shifted down by baseline alignment
                (child as? LinearLayout)?.let { it.isBaselineAligned = false; it.gravity = Gravity.CENTER_VERTICAL }
                forEachView(child, action)
            } else action(child)
        }
    }

    /** Four pills, the last one shorter, in a faint warm tint of the card colour. */
    private class SkeletonDrawable(private val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BAR_COLOR }
        private val bar = RectF()
        private var currentAlpha = 255

        override fun draw(canvas: Canvas) {
            val height = BAR_HEIGHT_DP * density
            val pitch = BAR_PITCH_DP * density
            val area = bounds
            for (index in 0 until BAR_COUNT) {
                val top = area.top + index * pitch
                if (top + height > area.bottom) break
                val width = if (index == BAR_COUNT - 1) area.width() * LAST_BAR_FRACTION else area.width().toFloat()
                bar.set(area.left.toFloat(), top, area.left + width, top + height)
                canvas.drawRoundRect(bar, height / 2, height / 2, paint)
            }
        }

        override fun setAlpha(alpha: Int) {
            if (alpha == currentAlpha) return
            currentAlpha = alpha
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun getAlpha() = currentAlpha
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val SIDE_DP = 16f
        private const val HEADER_HEIGHT_DP = 40f
        private const val ROUND_BUTTON_DP = 34f
        private const val TITLE_TEXT_DP = 22f
        private const val TITLE_GAP_DP = 12.6f
        private const val CARD_RADIUS_DP = 28f
        private const val CARD_BORDER_DP = 2f
        private const val CARD_PADDING_DP = 16f
        private const val CARD_TEXT_DP = 16f
        private const val CARD_MIN_DP = 56f
        private const val BUSY_CARD_MIN_DP = 146f
        private const val SKELETON_TOP_DP = 22.5f
        private const val SKELETON_END_DP = 31.7f
        private const val BAR_COUNT = 4
        private const val BAR_HEIGHT_DP = 20.7f
        private const val BAR_PITCH_DP = 25.5f
        private const val LAST_BAR_FRACTION = 0.72f
        private const val BAR_COLOR = 0xFF514745.toInt()
        private const val BORDER_START = 0xFF5C4F22.toInt() // dim olive-gold at the upper left
        private const val BORDER_END = 0xFFBC9C8F.toInt()   // rose-beige at the lower right
        private const val CHIP_HEIGHT_DP = 36f
        private const val CHIP_RADIUS_DP = 16f
        private const val CHIP_GAP_DP = 4f
        private const val CHIP_PADDING_DP = 10f
        private const val CHIP_VERTICAL_DP = 6f
        private const val CHIP_TEXT_DP = 14f
        private const val PULSE_PERIOD_MS = 1800L
        private const val PULSE_STEP_MS = 80L

        /** Returns the style when the Flat key style is active, otherwise null and the panel keeps its look. */
        fun attachIfActive(panel: AgentPanelView, body: LinearLayout): FlatPanelStyle? {
            val colors = Settings.getValues()?.mColors ?: return null
            if (!FlatStyle.isActive(colors)) return null
            return FlatPanelStyle(panel.findViewById(R.id.agent_result), colors).also { it.apply(panel, body) }
        }
    }
}
