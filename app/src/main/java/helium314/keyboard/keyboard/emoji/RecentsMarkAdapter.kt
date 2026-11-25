// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import kotlin.math.roundToInt

/**
 * Fork, stage 97: the heading "Zuletzt benutzt" above ([heading]) or the thin divider below the recent emojis in the continuous emoji
 * list (see [EmojiListPositions]). One item while there are recent emojis, none otherwise. Colors: the key text color of the design
 * with alpha, so it shows on a dark background (white symbols) as on a light one. View types of their own, because the list shares
 * view types between its adapters (ConcatAdapter without isolated view types; the emoji pages use the default type 0).
 */
class RecentsMarkAdapter(private val heading: Boolean, visible: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var visible = visible
        private set

    fun updateVisible(value: Boolean) {
        if (value == visible) return
        visible = value
        if (value) notifyItemInserted(0) else notifyItemRemoved(0)
    }

    override fun getItemCount() = if (visible) 1 else 0

    override fun getItemViewType(position: Int) = if (heading) VIEW_TYPE_HEADING else VIEW_TYPE_DIVIDER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val color = Settings.getValues().mColors.get(ColorType.KEY_TEXT)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        val view = if (heading) TextView(context).apply {
            setText(R.string.emoji_recents_heading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ColorUtils.setAlphaComponent(color, 0xB3))
            setPadding(dp(12), dp(6), dp(12), dp(2))
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            ViewCompat.setAccessibilityHeading(this, true)
        } else View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(color, 0x66))
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(1))).apply {
                setMargins(dp(12), dp(6), dp(12), dp(8))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

    companion object {
        const val VIEW_TYPE_HEADING = 1
        const val VIEW_TYPE_DIVIDER = 2
    }
}
