// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/** Overlays follow the engine's measured size and never enlarge the IME. */
class AgentKeyboardFrame(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboard = getChildAt(0)
        measureChildWithMargins(keyboard, widthMeasureSpec, 0, heightMeasureSpec, 0)
        setMeasuredDimension(resolveSize(keyboard.measuredWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(keyboard.measuredHeight + paddingTop + paddingBottom, heightMeasureSpec))
        for (index in 1 until childCount) {
            getChildAt(index).measure(MeasureSpec.makeMeasureSpec((measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0), MeasureSpec.EXACTLY))
        }
    }

    fun updateOverlayAccessibility() {
        val show = (1 until childCount).any { getChildAt(it).visibility == View.VISIBLE }
        getChildAt(0).importantForAccessibility = if (show)
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }
}
