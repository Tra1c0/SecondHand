// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import dev.agentkeyboard.core.LocalTextAction
import helium314.keyboard.latin.R

/** Real buttons retain keyboard navigation and TalkBack semantics. Selection only prepares a preview. */
class AgentWheelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var onAction: ((LocalTextAction?) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    private val dp = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * dp }

    init {
        isClickable = true
        isFocusableInTouchMode = false
        contentDescription = context.getString(R.string.agent_wheel_title)
        val colors = context.obtainStyledAttributes(R.styleable.AgentSurface)
        setBackgroundColor(colors.getColor(R.styleable.AgentSurface_android_colorBackground, Color.WHITE))
        ring.color = colors.getColor(R.styleable.AgentSurface_android_textColorPrimary, Color.BLACK)
        ring.alpha = 72
        colors.recycle()
        setWillNotDraw(false)
        fun entry(title: Int, viewId: Int, action: () -> Unit) {
            addView(Button(context).apply {
                id = viewId
                setText(title)
                isAllCaps = false
                textSize = 12f
                minimumWidth = 0
                minimumHeight = (48 * dp).toInt()
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                setOnClickListener { action() }
            })
        }
        // Order is also the accessibility traversal order: menu, three utilities, close.
        entry(R.string.agent, R.id.agent_wheel_menu) { onAction?.invoke(null) }
        entry(R.string.agent_action_uppercase, R.id.agent_wheel_uppercase) { onAction?.invoke(LocalTextAction.UPPERCASE) }
        entry(R.string.agent_action_lowercase, R.id.agent_wheel_lowercase) { onAction?.invoke(LocalTextAction.LOWERCASE) }
        entry(R.string.agent_action_spaces, R.id.agent_wheel_spaces) { onAction?.invoke(LocalTextAction.CLEAN_SPACES) }
        entry(R.string.agent_panel_close, R.id.agent_wheel_close) { onClose?.invoke() }
        visibility = View.GONE
    }

    private fun compact() = width < 260 * dp || height < 160 * dp

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val compact = w < 260 * dp || h < 160 * dp
        val bw = if (compact) w / 3 else minOf((104 * dp).toInt(), w / 3)
        val bh = if (compact) h / 2 else (48 * dp).toInt()
        for (index in 0 until childCount) getChildAt(index).measure(
            MeasureSpec.makeMeasureSpec(bw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val bw = getChildAt(0).measuredWidth
        val bh = getChildAt(0).measuredHeight
        val points = if (compact()) listOf(0 to 0, bw to 0, 2 * bw to 0, bw / 2 to bh, 3 * bw / 2 to bh)
        else listOf((width - bw) / 2 to 0, 0 to (height - bh) / 2,
            width - bw to (height - bh) / 2, (width - bw) / 2 to height - bh,
            (width - bw) / 2 to (height - bh) / 2)
        points.forEachIndexed { index, (x, y) -> getChildAt(index).layout(x, y, x + bw, y + bh) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!compact()) canvas.drawOval(40 * dp, 24 * dp, width - 40 * dp, height - 24 * dp, ring)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        (parent as? AgentKeyboardFrame)?.updateOverlayAccessibility()
    }
}
