// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

/**
 * Overlays follow the engine's measured size and never enlarge the IME. They end above the
 * system navigation bar: the keyboard keeps empty padding there, an overlay would put buttons
 * under the bar where they cannot be tapped.
 */
class AgentKeyboardFrame(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {
    /** Height at the bottom of this frame that the navigation bar covers. */
    var overlayBottomInset = 0
        internal set(value) {
            val bounded = value.coerceAtLeast(0)
            if (field != bounded) { field = bounded; requestLayout() }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboard = getChildAt(0)
        measureChildWithMargins(keyboard, widthMeasureSpec, 0, heightMeasureSpec, 0)
        setMeasuredDimension(resolveSize(keyboard.measuredWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(keyboard.measuredHeight + paddingTop + paddingBottom, heightMeasureSpec))
        val overlayHeight = (measuredHeight - paddingTop - paddingBottom - overlayBottomInset).coerceAtLeast(0)
        for (index in 1 until childCount) {
            getChildAt(index).measure(MeasureSpec.makeMeasureSpec((measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(overlayHeight, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val covered = coveredByNavigationBar()
        // Changing the inset requests another layout pass; post so it is not dropped mid-layout.
        if (covered != overlayBottomInset) post { overlayBottomInset = covered }
    }

    /** How far this frame reaches into the navigation bar area of its window, in pixels. */
    private fun coveredByNavigationBar(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0 // older systems inset the IME window themselves
        val bar = rootWindowInsets?.getInsets(WindowInsets.Type.navigationBars())?.bottom ?: return 0
        if (bar <= 0) return 0
        val location = IntArray(2)
        getLocationInWindow(location)
        return navigationOverlap(location[1] + height, rootView.height, bar)
    }

    fun updateOverlayAccessibility() {
        val show = (1 until childCount).any { getChildAt(it).visibility == View.VISIBLE }
        getChildAt(0).importantForAccessibility = if (show)
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    companion object {
        /** Pure geometry: the part of [0, frameBottom] that lies below the bar's top edge. */
        internal fun navigationOverlap(frameBottom: Int, windowHeight: Int, barHeight: Int): Int =
            (frameBottom - (windowHeight - barHeight)).coerceIn(0, barHeight.coerceAtLeast(0))
    }
}
