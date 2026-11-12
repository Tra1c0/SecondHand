// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.settings.SettingsDestination

/**
 * Menu of the Flat style: six pills and an edit button over the key area, opened by the left key
 * of the strip. The strip above stays visible and usable; touches there pass through this view.
 *
 * Layout from the reference screenshots (3.25 px per dp): pills 48 dp high in two columns with
 * 24 dp side margins and 8 dp gaps, first row 24 dp below the strip, edit pill 72 x 32 dp at the
 * right, 24 dp below the last row. There is no animation: the view is shown and hidden.
 */
class FlatMenuView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    /** What a pill does. The receiver closes the menu first, then acts. */
    sealed interface Action {
        data class Code(val code: Int) : Action
        data class OpenSettings(val destination: String) : Action
    }

    private class Entry(val id: Int, val label: Int, val icon: String, val action: Action, val opensScreen: Boolean = false)

    private val entries = listOf(
        Entry(R.id.agent_menu_design, R.string.agent_menu_design, ToolbarKey.SETTINGS.name, Action.OpenSettings(SettingsDestination.Appearance), true),
        Entry(R.id.agent_menu_undo, R.string.agent_menu_undo, ToolbarKey.UNDO.name, Action.Code(KeyCode.UNDO)),
        Entry(R.id.agent_menu_one_handed, R.string.agent_menu_one_handed, ToolbarKey.ONE_HANDED.name, Action.Code(KeyCode.TOGGLE_ONE_HANDED_MODE)),
        Entry(R.id.agent_menu_emoji, R.string.agent_menu_emoji, ToolbarKey.EMOJI.name, Action.Code(KeyCode.EMOJI), true),
        Entry(R.id.agent_menu_incognito, R.string.agent_menu_incognito, ToolbarKey.INCOGNITO.name, Action.Code(KeyCode.TOGGLE_INCOGNITO_MODE)),
        Entry(R.id.agent_menu_floating, R.string.agent_menu_floating, ToolbarKey.FLOATING.name, Action.Code(KeyCode.TOGGLE_FLOATING_WINDOW)),
    )

    /** Called after the menu has closed itself because a pill was tapped. */
    var onAction: ((Action) -> Unit)? = null
    /** Called whenever the menu goes away, for whatever reason. */
    var onClosed: (() -> Unit)? = null

    val isOpen: Boolean get() = visibility == VISIBLE

    private val content = LinearLayout(context)
    private var built = false

    init {
        visibility = GONE
        content.orientation = LinearLayout.VERTICAL
        // swallow touches between the pills so that they do not reach the keys underneath
        content.isClickable = true
        content.isSoundEffectsEnabled = false
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Shows the menu below the strip, which is [stripHeight] pixels high. */
    fun open(stripHeight: Int) {
        if (!built) { build(); built = true }
        (content.layoutParams as LayoutParams).topMargin = stripHeight
        content.requestLayout()
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

    private fun build() {
        val colors = Settings.getValues()?.mColors
        val background = colors?.get(ColorType.MAIN_BACKGROUND) ?: FlatStyle.BACKGROUND
        val pill = colors?.get(ColorType.KEY_BACKGROUND) ?: FlatStyle.KEY
        val text = colors?.get(ColorType.KEY_TEXT) ?: FlatStyle.LABEL
        val icon = colors?.get(ColorType.KEY_ICON) ?: FlatStyle.FUNCTIONAL_LABEL

        content.setBackgroundColor(background)
        content.setPadding(dp(SIDE_DP), dp(SIDE_DP), dp(SIDE_DP), 0)
        val chevron = ContextCompat.getDrawable(context, R.drawable.ic_arrow_right)
        entries.chunked(COLUMNS).forEachIndexed { rowIndex, rowEntries ->
            val row = LinearLayout(context)
            rowEntries.forEachIndexed { column, entry ->
                val params = LinearLayout.LayoutParams(0, dp(PILL_HEIGHT_DP), 1f)
                if (column > 0) params.marginStart = dp(GAP_DP)
                row.addView(pillButton(entry, pill, text, icon, chevron), params)
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (rowIndex > 0) params.topMargin = dp(GAP_DP)
            content.addView(row, params)
        }
        content.addView(editButton(icon), LinearLayout.LayoutParams(dp(EDIT_WIDTH_DP), dp(EDIT_HEIGHT_DP)).apply {
            topMargin = dp(SIDE_DP)
            gravity = Gravity.END
        })
    }

    private fun pillButton(entry: Entry, pillColor: Int, textColor: Int, iconColor: Int, chevron: Drawable?): Button {
        val button = Button(context, null, 0) // no default style: no shadow, no minimum size, no caps
        button.id = entry.id
        button.text = context.getString(entry.label)
        button.isAllCaps = false
        button.setTextColor(textColor)
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_DP)
        button.typeface = FlatStyle.labelTypeface
        button.isSingleLine = true
        button.ellipsize = android.text.TextUtils.TruncateAt.END
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.isSoundEffectsEnabled = false
        button.background = roundedBackground(pillColor, dp(PILL_HEIGHT_DP) / 2f)
        button.setPadding(dp(ICON_START_DP), 0, dp(ICON_START_DP), 0)
        button.compoundDrawablePadding = dp(ARROW_GAP_DP)
        // the wider gap after the icon is part of the drawable, so that the arrow can sit closer to the label
        val start = KeyboardIconsSet.instance.getNewDrawable(entry.icon, context)?.mutate()?.apply { setTint(iconColor) }
            ?.let { InsetDrawable(it, 0, 0, dp(ICON_GAP_DP - ARROW_GAP_DP), 0) }
        val end = if (entry.opensScreen) chevron?.constantState?.newDrawable()?.mutate()?.apply { setTint(iconColor) } else null
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(start, null, end, null)
        button.setOnClickListener { act(entry.action) }
        return button
    }

    private fun editButton(iconColor: Int): ImageButton {
        val button = ImageButton(context, null, 0)
        button.id = R.id.agent_menu_edit
        button.contentDescription = context.getString(R.string.agent_menu_edit)
        button.scaleType = ImageView.ScaleType.CENTER
        button.isSoundEffectsEnabled = false
        button.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_edit)?.mutate()?.apply { setTint(iconColor) })
        button.background = GradientDrawable().apply {
            cornerRadius = dp(EDIT_HEIGHT_DP) / 2f
            setColor(Color.TRANSPARENT)
            setStroke(dp(1f), ColorUtils.setAlphaComponent(iconColor, 0xC0))
        }
        button.setOnClickListener { act(Action.OpenSettings(SettingsDestination.Toolbar)) }
        return button
    }

    private fun act(action: Action) {
        close()
        onAction?.invoke(action)
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        val pressed = ColorUtils.blendARGB(color, Color.WHITE, 0.15f)
        setColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(pressed, color)))
    }

    private companion object {
        const val COLUMNS = 2
        const val SIDE_DP = 24f
        const val GAP_DP = 8f
        const val PILL_HEIGHT_DP = 48f
        const val TEXT_DP = 16f
        /** The 24 dp icon is centred 30 dp from the left edge of the pill, the label starts at 57 dp. */
        const val ICON_START_DP = 18f
        const val ICON_GAP_DP = 15f
        const val ARROW_GAP_DP = 4f
        const val EDIT_WIDTH_DP = 72f
        const val EDIT_HEIGHT_DP = 32f
    }
}
