// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.prompts

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import helium314.keyboard.agent.AgentKeyboardFrame
import helium314.keyboard.agent.Prompt
import helium314.keyboard.agent.promptLabel
import helium314.keyboard.agent.promptPreview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * Stage 74b: the prompt store in place of the keys, opened by the icon in the popup of the comma key.
 * Built like the other windows of the fork (views, colours of the keyboard's colour set, an overlay of
 * `AgentKeyboardFrame`). It shows the prompts in the user's order (title or first line, two lines of
 * preview), "Save copied text as prompt" as the first row and "Manage" in the header. It holds only
 * what it shows while it is open; a tap on a row reports the prompt's id to the controller, which
 * inserts the stored text once. Nothing here reads the editor or the clipboard, logs or uses the network.
 */
class PromptsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    /** The id of the tapped prompt. */
    var onPick: ((String) -> Unit)? = null
    /** The tap on "Save copied text as prompt". */
    var onSaveClip: (() -> Unit)? = null
    var onManage: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private var backgroundColor = 0
    private var keyColor = 0
    private var functionalColor = 0
    private var textColor = 0
    private var accentColor = 0
    private var accentTextColor = 0

    private val status = TextView(context)
    private val list = LinearLayout(context)
    private val scroll = ScrollView(context)

    /** The ids of the rows shown now, in order (tests and the controller's checks). */
    var shownIds: List<String> = emptyList()
        private set

    init {
        orientation = VERTICAL
        isClickable = true
        visibility = View.GONE
        readColors()
        val padding = (6 * dp).toInt()
        setPadding(padding, padding, padding, padding)

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            setText(R.string.agent_prompts_title)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        }.also { androidx.core.view.ViewCompat.setAccessibilityHeading(it, true) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(button(context.getString(R.string.agent_prompts_window_manage), R.id.agent_prompts_manage, functional = true) { onManage?.invoke() },
            LayoutParams(LayoutParams.WRAP_CONTENT, (36 * dp).toInt()).apply { marginEnd = (4 * dp).toInt() })
        header.addView(button(context.getString(R.string.agent_panel_close), R.id.agent_prompts_close, functional = true) { onClose?.invoke() },
            LayoutParams(LayoutParams.WRAP_CONTENT, (36 * dp).toInt()))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        status.apply {
            id = R.id.agent_prompts_status
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.apply { id = R.id.agent_prompts_list; orientation = VERTICAL }
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Opens the window with [prompts] in the store's order; null: the store cannot be used now. */
    fun show(prompts: List<Prompt>?) {
        showStatus(null)
        fill(prompts)
        scroll.scrollTo(0, 0)
        visibility = View.VISIBLE
        announceForAccessibility(context.getString(R.string.agent_prompts_title))
    }

    /** The same window with a new list (after "Save copied text as prompt"). */
    fun refresh(prompts: List<Prompt>?) = fill(prompts)

    /** Closes the window and forgets everything it showed; nothing to do while it is closed. */
    fun hide() {
        if (visibility != View.VISIBLE) return
        visibility = View.GONE
        list.removeAllViews()
        shownIds = emptyList()
        showStatus(null)
    }

    /** A short message under the header, or none for null. */
    fun showStatus(message: String?) {
        status.text = message.orEmpty()
        status.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        (parent as? AgentKeyboardFrame)?.updateOverlayAccessibility()
    }

    private fun fill(prompts: List<Prompt>?) {
        list.removeAllViews()
        if (prompts == null) {
            shownIds = emptyList()
            list.addView(note(context.getString(R.string.agent_prompts_window_unavailable), R.id.agent_prompts_empty))
            return
        }
        list.addView(button(context.getString(R.string.agent_prompts_save_clip), R.id.agent_prompts_save_clip, accent = true) { onSaveClip?.invoke() },
            rowParams())
        if (prompts.isEmpty()) list.addView(note(context.getString(R.string.agent_prompts_window_empty), R.id.agent_prompts_empty))
        for (prompt in prompts) list.addView(row(prompt), rowParams())
        shownIds = prompts.map { it.id }
    }

    private fun rowParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (4 * dp).toInt() }

    private fun note(text: String, viewId: Int) = TextView(context).apply {
        id = viewId
        this.text = text
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        setPadding((8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
    }

    /** One prompt: its title (or first line) and up to two lines of preview; a tap reports its id. */
    private fun row(prompt: Prompt) = LinearLayout(context).apply {
        orientation = VERTICAL
        tag = prompt.id
        val label = promptLabel(prompt)
        contentDescription = context.getString(R.string.agent_prompts_insert_description, label)
        setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
        minimumHeight = (44 * dp).toInt()
        background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(keyColor) }
        isClickable = true
        isFocusable = true
        addView(TextView(context).apply {
            text = label
            gravity = Gravity.START
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // the row speaks for them
        })
        addView(TextView(context).apply {
            text = promptPreview(prompt.text)
            gravity = Gravity.START
            setTextColor(textColor)
            alpha = 0.75f
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        setOnClickListener { onPick?.invoke(prompt.id) }
    }

    private fun button(label: String, viewId: Int, functional: Boolean = false, accent: Boolean = false, action: () -> Unit) =
        TextView(context).apply {
            id = viewId
            text = label
            gravity = Gravity.START or Gravity.CENTER_VERTICAL // wraps its text; centred single lines do not draw in render tests (P40)
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            setTextColor(if (accent) accentTextColor else textColor)
            if (accent) minimumHeight = (44 * dp).toInt()
            setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            background = GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(if (accent) accentColor else if (functional) functionalColor else keyColor)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun readColors() {
        val colors = runCatching { Settings.getValues()?.mColors }.getOrNull()
        if (colors == null) {
            val surface = context.obtainStyledAttributes(R.styleable.AgentSurface)
            backgroundColor = surface.getColor(R.styleable.AgentSurface_android_colorBackground, 0xFFFFFFFF.toInt())
            textColor = surface.getColor(R.styleable.AgentSurface_android_textColorPrimary, 0xFF000000.toInt())
            surface.recycle()
            keyColor = blend(backgroundColor, textColor, 0.08f)
            functionalColor = blend(backgroundColor, textColor, 0.16f)
            accentColor = textColor
            accentTextColor = backgroundColor
        } else {
            backgroundColor = colors.get(ColorType.MAIN_BACKGROUND) or 0xFF000000.toInt()
            keyColor = colors.get(ColorType.KEY_BACKGROUND)
            functionalColor = colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)
            accentColor = colors.get(ColorType.ACTION_KEY_BACKGROUND)
            textColor = colors.get(ColorType.KEY_TEXT)
            accentTextColor = colors.get(ColorType.ACTION_KEY_ICON)
        }
        setBackgroundColor(backgroundColor)
    }

    private fun blend(from: Int, to: Int, share: Float): Int {
        fun channel(shift: Int) = ((from shr shift and 0xFF) * (1 - share) + (to shr shift and 0xFF) * share).toInt() and 0xFF
        return 0xFF000000.toInt() or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
