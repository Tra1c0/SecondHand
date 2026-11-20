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
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.StripIconColors
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getStringResourceOrName
import java.util.Locale

/**
 * Fork, stage 85: the panel of all tools in the key area, in every style. The left key of the fixed bar opens it;
 * the strip above stays visible and usable, touches there pass through this view (as with [FlatMenuView], whose
 * layout it follows: pills 48 dp high in two columns, 24 dp side margins, 8 dp gaps). A tap on a pill closes the
 * panel first and then reports the tool, so the tool runs exactly like its key in the bar. A tool that the field
 * does not allow is dimmed and does not react. Nothing is animated.
 *
 * Stage 86: a button "Anpassen" at the top starts the edit mode of the bar ([onEditRequested]). While [editListener] is
 * set, the panel shows the hint "Zum Personalisieren halten und ziehen" instead, a tap on a pill adds the tool to the
 * bar or takes it out ([EditListener.onTap]), holding a pill starts a drag ([EditListener.onDrag]), and a tool dropped on
 * the panel is reported to [dropListener]. Tools that are in the bar are shown dimmed ([setMarked]).
 */
class ToolGridView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    /** One pill: the tool and whether the current field allows it. */
    class Tool(val key: ToolbarKey, val available: Boolean = true, val reason: helium314.keyboard.latin.utils.BarTools.Reason? = null)

    /** What the panel asks of the edit mode of the bar (stage 86). */
    interface EditListener {
        /** A tap on a pill: the tool goes into the bar or out of it. */
        fun onTap(key: ToolbarKey)
        /** Holding a pill: starts the drag of the tool; true when it started. */
        fun onDrag(pill: View, key: ToolbarKey): Boolean
        /** Actions for screen readers on the pill of [key]: the label and what it does (true when it did something). */
        fun actions(key: ToolbarKey): List<Pair<Int, () -> Boolean>>
    }

    /** A pill of a tool that the field does not allow was tapped: the owner of the panel says why (stage 88). */
    var onUnavailable: ((ToolbarKey, helium314.keyboard.latin.utils.BarTools.Reason?) -> Unit)? = null
    /** Called after the panel has closed itself because a pill was tapped. */
    var onTool: ((ToolbarKey) -> Unit)? = null
    /** The button "Anpassen" was tapped; the owner of the panel starts the edit mode. */
    var onEditRequested: (() -> Unit)? = null
    /** Non-null while the bar is being edited. */
    var editListener: EditListener? = null
        private set
    /** Where a dragged tool dropped on the panel goes (the tool leaves the bar). */
    var dropListener: OnDragListener? = null
        set(value) { field = value; content.setOnDragListener(value); scroll.setOnDragListener(value) }
    private var hintText: CharSequence? = null
    private var marked = emptySet<ToolbarKey>()
    private var lastTools = emptyList<Tool>()
    private var pillBackground = 0
    private val reasons = HashMap<ToolbarKey, helium314.keyboard.latin.utils.BarTools.Reason?>()
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

    /** Why the pill of [key] is in the "off" look, or null when the tool is available (stage 88). */
    fun reason(key: ToolbarKey) = reasons[key]

    /** The pill of [key], or null when the panel does not list it. */
    fun pill(key: ToolbarKey): Button? = pills[key]

    /** The tools in the order of the panel, as built by the last [open]. */
    val listed: List<ToolbarKey> get() = pills.keys.toList()

    /** The hint line of the edit mode, null for the standard one. */
    val hint: TextView? get() = content.findViewById(R.id.agent_tool_grid_hint)

    /** Switches the edit mode on (with the listener) or off (null); the pills are built again. */
    fun setEditing(listener: EditListener?) {
        editListener = listener
        hintText = null
        if (listener == null) marked = emptySet()
        if (lastTools.isNotEmpty()) build(lastTools)
    }

    /** A line instead of the standard hint, e.g. that the bar is full; null for the standard hint again. */
    fun setHint(text: CharSequence?) {
        hintText = text
        hint?.text = text ?: context.getString(R.string.agent_grid_edit_hint)
    }

    /** The tools that are in the bar are dimmed in the panel while it is edited. */
    fun setMarked(keys: Set<ToolbarKey>) {
        marked = keys
        if (editListener == null) return
        pills.forEach { (key, pill) -> applyMark(key, pill) }
    }

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
        editListener = null // the edit mode ends with the panel; what was dropped is saved already
        onClosed?.invoke()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) close() // the keyboard was hidden
    }

    private fun dp(value: Float) = Math.round(value * resources.displayMetrics.density)

    private fun build(tools: List<Tool>) {
        lastTools = tools
        content.removeAllViews()
        pills.clear()
        reasons.clear()
        val colors = Settings.getValues()?.mColors
        val background = colors?.get(ColorType.MAIN_BACKGROUND) ?: FlatStyle.BACKGROUND
        val pill = colors?.get(ColorType.KEY_BACKGROUND) ?: FlatStyle.KEY
        val text = colors?.get(ColorType.KEY_TEXT) ?: FlatStyle.LABEL
        val icon = colors?.get(ColorType.KEY_ICON) ?: FlatStyle.FUNCTIONAL_LABEL
        pillBackground = pill

        scroll.setBackgroundColor(background)
        content.setPadding(dp(SIDE_DP), dp(SIDE_DP), dp(SIDE_DP), dp(SIDE_DP))
        content.addView(header(text, icon, pill), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(GAP_DP) })
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

    /** The hint line while editing, else the button that starts the edit mode. */
    private fun header(textColor: Int, iconColor: Int, pillColor: Int): View {
        if (editListener != null) {
            return TextView(context).apply {
                id = R.id.agent_tool_grid_hint
                text = hintText ?: context.getString(R.string.agent_grid_edit_hint)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
                gravity = Gravity.CENTER
                setPadding(0, dp(GAP_DP), 0, dp(GAP_DP))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
        }
        return Button(context, null, 0).apply {
            id = R.id.agent_tool_grid_edit
            text = context.getString(R.string.agent_grid_edit)
            isAllCaps = false
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
            typeface = FlatStyle.labelTypeface
            gravity = Gravity.CENTER
            isSoundEffectsEnabled = false
            background = roundedBackground(pillColor, dp(EDIT_HEIGHT_DP) / 2f)
            minimumWidth = 0; minimumHeight = 0
            setPadding(dp(ICON_START_DP), 0, dp(ICON_START_DP), 0)
            val edit = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_edit)?.mutate()?.apply { setTint(iconColor) }
                ?.let { InsetDrawable(it, 0, 0, dp(GAP_DP), 0) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(edit, null, null, null)
            setOnClickListener { onEditRequested?.invoke() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(EDIT_HEIGHT_DP)).apply { gravity = Gravity.END }
        }
    }

    private fun applyMark(key: ToolbarKey, pill: Button) {
        val label = key.name.lowercase(Locale.ROOT).getStringResourceOrName("", context)
        val inBar = key in marked
        pill.alpha = if (inBar) MARKED_ALPHA else 1f
        pill.contentDescription = if (inBar) context.getString(R.string.agent_bar_in_bar, label) else label
    }

    private fun pillButton(tool: Tool, pillColor: Int, textColor: Int, iconColor: Int): Button {
        val key = tool.key
        val button = Button(context, null, 0) // no default style: no shadow, no minimum size, no caps
        val label = key.name.lowercase(Locale.ROOT).getStringResourceOrName("", context)
        // the text and the icon have the guaranteed contrast against the pill; an unavailable tool gets the "off" colour (a real colour, not an alpha)
        val off = !tool.available && editListener == null
        val text = StripIconColors.ensureContrast(textColor, pillColor).let { if (off) StripIconColors.off(it, pillColor) else it }
        val iconTint = StripIconColors.ensureContrast(iconColor, pillColor).let { if (off) StripIconColors.off(it, pillColor) else it }
        button.tag = key
        button.text = label
        button.isAllCaps = false
        button.setTextColor(text)
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
        button.typeface = FlatStyle.labelTypeface
        FlatStyle.scrollWhenTooLong(button)
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.isSoundEffectsEnabled = false
        button.background = roundedBackground(pillColor, dp(PILL_HEIGHT_DP) / 2f)
        button.setPadding(dp(ICON_START_DP), 0, dp(ICON_START_DP), 0)
        val start = KeyboardIconsSet.instance.getNewDrawable(key.name, context)?.mutate()?.apply { setTint(iconTint) }
            ?.let { InsetDrawable(it, 0, 0, dp(ICON_GAP_DP), 0) }
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(start, null, null, null)
        val editing = editListener
        if (editing != null) {
            // customizing is about the layout of the bar: also a tool the field does not allow can be moved
            button.setOnClickListener { editing.onTap(key) }
            editing.actions(key).forEach { (labelRes, action) ->
                androidx.core.view.ViewCompat.addAccessibilityAction(button, context.getString(labelRes)) { _, _ -> action() }
            }
            applyMark(key, button)
            return button
        }
        reasons[key] = if (tool.available) null else (tool.reason ?: helium314.keyboard.latin.utils.BarTools.Reason.PRIVATE_FIELD)
        // a tool the field does not allow stays tappable for the touch system: the tap says why (stage 88)
        button.contentDescription = if (tool.available) label else context.getString(R.string.agent_bar_tool_unavailable, label)
        button.setOnClickListener { act(key) }
        return button
    }

    private fun act(key: ToolbarKey) {
        if (key in reasons && reasons[key] != null) { onUnavailable?.invoke(key, reasons[key]); return } // "off": say why, stay open
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
        private const val MARKED_ALPHA = 0.5f
        private const val EDIT_HEIGHT_DP = 40f
    }
}
