// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getStringResourceOrName
import java.util.Locale

/**
 * Fork, stage 85: the panel of all tools in the key area, in every style. The left key of the fixed bar opens it;
 * the strip above stays visible and usable, touches there pass through this view (as with [FlatMenuView], whose
 * layout it follows: pills 48 dp high in two columns, 24 dp side margins, 8 dp gaps). A tap on a pill closes the
 * panel first and then reports the tool, so the tool runs exactly like its key in the bar. A tool that the field
 * does not allow is dimmed and does not react. Nothing is animated.
 */
class ToolGridView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    /** One pill: the tool and whether the current field allows it. */
    class Tool(val key: ToolbarKey, val available: Boolean = true)

    /** Called after the panel has closed itself because a pill was tapped. */
    var onTool: ((ToolbarKey) -> Unit)? = null
    /** Called whenever the panel goes away, for whatever reason. */
    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = visibility == VISIBLE

    private val content = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val pills = LinkedHashMap<ToolbarKey, Button>()

    init {
        visibility = GONE
        content.orientation = LinearLayout.VERTICAL
        // swallow touches between the pills so that they do not reach the keys underneath
        content.isClickable = true
        content.isSoundEffectsEnabled = false
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        (parent as? AgentKeyboardFrame)?.updateOverlayAccessibility()
    }

    /** The pill of [key], or null when the panel does not list it. */
    fun pill(key: ToolbarKey): Button? = pills[key]

    /** The tools in the order of the panel, as built by the last [open]. */
    val listed: List<ToolbarKey> get() = pills.keys.toList()

    /** Shows [tools] below the strip, which is [stripHeight] pixels high. */
    fun open(stripHeight: Int, tools: List<Tool>) {
        build(tools)
        (scroll.layoutParams as LayoutParams).topMargin = stripHeight
        scroll.scrollTo(0, 0)
        scroll.requestLayout()
        visibility = VISIBLE
    }

    fun close() {
        if (!isOpen) return
        visibility = GONE
        onClosed?.invoke()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) close() // the keyboard was hidden
    }

    private fun dp(value: Float) = Math.round(value * resources.displayMetrics.density)

    private fun build(tools: List<Tool>) {
        content.removeAllViews()
        pills.clear()
        val colors = Settings.getValues()?.mColors
        val background = colors?.get(ColorType.MAIN_BACKGROUND) ?: FlatStyle.BACKGROUND
        val pill = colors?.get(ColorType.KEY_BACKGROUND) ?: FlatStyle.KEY
        val text = colors?.get(ColorType.KEY_TEXT) ?: FlatStyle.LABEL
        val icon = colors?.get(ColorType.KEY_ICON) ?: FlatStyle.FUNCTIONAL_LABEL

        scroll.setBackgroundColor(background)
        content.setPadding(dp(SIDE_DP), dp(SIDE_DP), dp(SIDE_DP), dp(SIDE_DP))
        tools.chunked(COLUMNS).forEachIndexed { rowIndex, rowTools ->
            val row = LinearLayout(context)
            rowTools.forEachIndexed { column, tool ->
                val params = LinearLayout.LayoutParams(0, dp(PILL_HEIGHT_DP), 1f)
                if (column > 0) params.marginStart = dp(GAP_DP)
                val button = pillButton(tool, pill, text, icon)
                pills[tool.key] = button
                row.addView(button, params)
            }
            // a last row with one pill keeps the column width of the others
            if (rowTools.size < COLUMNS) repeat(COLUMNS - rowTools.size) {
                row.addView(View(context), LinearLayout.LayoutParams(0, dp(PILL_HEIGHT_DP), 1f).apply { marginStart = dp(GAP_DP) })
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (rowIndex > 0) params.topMargin = dp(GAP_DP)
            content.addView(row, params)
        }
    }

    private fun pillButton(tool: Tool, pillColor: Int, textColor: Int, iconColor: Int): Button {
        val key = tool.key
        val button = Button(context, null, 0) // no default style: no shadow, no minimum size, no caps
        val label = key.name.lowercase(Locale.ROOT).getStringResourceOrName("", context)
        button.tag = key
        button.text = label
        button.isAllCaps = false
        button.setTextColor(textColor)
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
        button.typeface = FlatStyle.labelTypeface
        FlatStyle.scrollWhenTooLong(button)
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.isSoundEffectsEnabled = false
        button.background = roundedBackground(pillColor, dp(PILL_HEIGHT_DP) / 2f)
        button.setPadding(dp(ICON_START_DP), 0, dp(ICON_START_DP), 0)
        val start = KeyboardIconsSet.instance.getNewDrawable(key.name, context)?.mutate()?.apply { setTint(iconColor) }
            ?.let { InsetDrawable(it, 0, 0, dp(ICON_GAP_DP), 0) }
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(start, null, null, null)
        button.isEnabled = tool.available
        button.alpha = if (tool.available) 1f else DIMMED_ALPHA
        button.contentDescription = if (tool.available) label else context.getString(R.string.agent_bar_tool_unavailable, label)
        button.setOnClickListener { act(key) }
        return button
    }

    private fun act(key: ToolbarKey) {
        if (pills[key]?.isEnabled == false) return // dimmed: the field does not allow it
        close()
        onTool?.invoke(key)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        val pressed = ColorUtils.blendARGB(color, Color.WHITE, 0.15f)
        setColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(pressed, color)))
    }

    companion object {
        const val COLUMNS = 2
        private const val SIDE_DP = 24f
        private const val GAP_DP = 8f
        private const val PILL_HEIGHT_DP = 48f
        private const val TEXT_DP = 16f
        private const val ICON_START_DP = 18f
        private const val ICON_GAP_DP = 15f
        private const val DIMMED_ALPHA = 0.38f
    }
}
