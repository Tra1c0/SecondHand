// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import dev.agentkeyboard.core.MathExpression
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * The calculator of the `@math` command (stage 24b), shown in place of the keys. It computes on
 * the device only; the result is inserted at the cursor only by the tap on "Insert".
 */
class CalculatorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    /** Called with the text to insert. */
    var onInsert: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val expression = StringBuilder()
    private var style = MathExpression.Style.EQUATION
    private var locale = MathExpression.Locale.COMMA
    private val input: TextView
    private val preview: TextView
    private val styleRow = LinearLayout(context)
    private val insert: Button
    private var colorsReady = false
    private var keyColor = 0
    private var functionalColor = 0
    private var accentColor = 0
    private var textColor = 0
    private var accentTextColor = 0

    init {
        orientation = VERTICAL
        isClickable = true
        visibility = View.GONE
        readColors()
        val padding = (6 * dp).toInt()
        setPadding(padding, padding, padding, padding)

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            setText(R.string.agent_calculator_title)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(key(context.getString(R.string.agent_panel_close), R.id.agent_calculator_close, functional = true) { onClose?.invoke() },
            LayoutParams(LayoutParams.WRAP_CONTENT, (36 * dp).toInt()))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        input = TextView(context).apply {
            id = R.id.agent_calculator_input
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
        }
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        preview = TextView(context).apply {
            id = R.id.agent_calculator_preview
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextColor(textColor)
            alpha = 0.75f
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.START
        }
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        styleRow.orientation = HORIZONTAL
        styleRow.id = R.id.agent_calculator_styles
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(styleRow) },
            LayoutParams(LayoutParams.MATCH_PARENT, (40 * dp).toInt()))

        insert = key(context.getString(R.string.agent_calculator_insert), R.id.agent_calculator_insert, accent = true) { insertResult() }
        val rows = listOf(
            listOf("(", ")", "%", "÷", "⌫"),
            listOf("7", "8", "9", "×", "√"),
            listOf("4", "5", "6", "−", "^"),
            listOf("1", "2", "3", "+", "C"),
        )
        for (row in rows) {
            val line = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
            for (label in row) line.addView(keyFor(label), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(line, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val last = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
        last.addView(keyFor("0"), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        last.addView(keyFor(","), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        last.addView(insert, LayoutParams(0, LayoutParams.MATCH_PARENT, 3f))
        addView(last, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun readColors() {
        val colors = runCatching { Settings.getValues()?.mColors }.getOrNull()
        val surface = context.obtainStyledAttributes(R.styleable.AgentSurface)
        val fallbackBackground = surface.getColor(R.styleable.AgentSurface_android_colorBackground, 0xFFFFFFFF.toInt())
        val fallbackText = surface.getColor(R.styleable.AgentSurface_android_textColorPrimary, 0xFF000000.toInt())
        surface.recycle()
        if (colors == null) {
            setBackgroundColor(fallbackBackground)
            keyColor = fallbackBackground; functionalColor = fallbackBackground; accentColor = fallbackText
            textColor = fallbackText; accentTextColor = fallbackBackground
            return
        }
        setBackgroundColor(colors.get(ColorType.MAIN_BACKGROUND) or 0xFF000000.toInt())
        keyColor = colors.get(ColorType.KEY_BACKGROUND)
        functionalColor = colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)
        accentColor = colors.get(ColorType.ACTION_KEY_BACKGROUND)
        textColor = colors.get(ColorType.KEY_TEXT)
        accentTextColor = colors.get(ColorType.ACTION_KEY_ICON)
        colorsReady = true
    }

    private fun keyFor(label: String): Button = when (label) {
        "⌫" -> key(label, R.id.agent_calculator_delete, functional = true, description = context.getString(R.string.agent_calculator_delete)) {
            if (expression.isNotEmpty()) expression.setLength(expression.length - 1); refresh()
        }
        "C" -> key(label, R.id.agent_calculator_clear, functional = true, description = context.getString(R.string.agent_calculator_clear)) {
            expression.setLength(0); refresh()
        }
        else -> key(label, View.NO_ID, functional = !label[0].isDigit() && label != ",") {
            if (expression.length < MathExpression.MAX_INPUT) expression.append(label)
            refresh()
        }
    }

    private fun key(label: String, viewId: Int, functional: Boolean = false, accent: Boolean = false,
                    description: String? = null, action: () -> Unit) = Button(context).apply {
        if (viewId != View.NO_ID) id = viewId
        text = label
        if (description != null) contentDescription = description
        isAllCaps = false
        minimumWidth = 0
        minimumHeight = 0
        minWidth = 0
        minHeight = 0
        stateListAnimator = null
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, if (label.length > 2) 15f else 20f)
        setTextColor(if (accent) accentTextColor else textColor)
        setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        background = GradientDrawable().apply {
            cornerRadius = 8 * dp
            setColor(if (accent) accentColor else if (functional) functionalColor else keyColor)
        }
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
        }
        setOnClickListener { action() }
    }.also { button ->
        // margins only take effect through the parent's layout params, which addView replaces
        button.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                (v.layoutParams as? MarginLayoutParams)?.setMargins((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt())
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    /** Shows the calculator with [initial] as the expression (the argument of `@math`, may be empty). */
    fun show(initial: String, language: String) {
        locale = MathExpression.Locale.forLanguage(language)
        style = MathCommand.storedStyle(context)
        expression.setLength(0)
        expression.append(initial.take(MathExpression.MAX_INPUT).replace('*', '×').replace('/', '÷').replace('-', '−'))
        buildStyles()
        refresh()
        visibility = View.VISIBLE
        announceForAccessibility(context.getString(R.string.agent_calculator_title))
    }

    private fun buildStyles() {
        styleRow.removeAllViews()
        for (option in MathExpression.Style.entries) {
            val chip = key(context.getString(styleTitle(option)), View.NO_ID, functional = option != style, accent = option == style) {
                style = option
                context.prefs().edit { putString(AgentPreferences.CALCULATOR_STYLE, option.name) }
                buildStyles()
                refresh()
            }
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            chip.isSelected = option == style
            styleRow.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
    }

    /** The text the insert button would insert now, or null while the expression is incomplete. */
    fun currentText(): String? {
        val outcome = MathExpression.evaluate(expression.toString()) as? MathExpression.Value ?: return null
        return MathExpression.format(outcome, style, locale)
    }

    private fun refresh() {
        input.text = expression.toString()
        val outcome = MathExpression.evaluate(expression.toString())
        preview.text = when (outcome) {
            is MathExpression.Value -> MathExpression.format(outcome, style, locale)
            is MathExpression.Failure -> if (outcome.reason == MathExpression.Reason.EMPTY || outcome.reason == MathExpression.Reason.SYNTAX) ""
                else context.getString(failureText(outcome.reason))
        }
        insert.isEnabled = outcome is MathExpression.Value
        insert.alpha = if (insert.isEnabled) 1f else 0.5f
    }

    private fun insertResult() {
        val text = currentText() ?: return
        onInsert?.invoke(text)
    }

    fun hide() {
        visibility = View.GONE
        expression.setLength(0)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        (parent as? AgentKeyboardFrame)?.updateOverlayAccessibility()
    }

    /** For tests and the controller: what is typed in the calculator. */
    val typed: String get() = expression.toString()

    companion object {
        fun styleTitle(style: MathExpression.Style) = when (style) {
            MathExpression.Style.EQUATION -> R.string.agent_calculator_style_equation
            MathExpression.Style.RESULT -> R.string.agent_calculator_style_result
            MathExpression.Style.LATEX -> R.string.agent_calculator_style_latex
            MathExpression.Style.UNICODE -> R.string.agent_calculator_style_unicode
        }

        fun failureText(reason: MathExpression.Reason) = when (reason) {
            MathExpression.Reason.DIVISION_BY_ZERO -> R.string.agent_calculator_division_by_zero
            MathExpression.Reason.NEGATIVE_ROOT -> R.string.agent_calculator_negative_root
            MathExpression.Reason.TOO_LARGE -> R.string.agent_calculator_too_large
            MathExpression.Reason.TOO_LONG -> R.string.agent_calculator_too_long
            MathExpression.Reason.EMPTY, MathExpression.Reason.SYNTAX -> R.string.agent_calculator_incomplete
        }
    }
}
