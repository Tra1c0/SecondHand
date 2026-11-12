// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.clipboard

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Clipboard view of the Flat style, measured on the reference screenshot (3.25 px per dp):
 * a header in the strip (back button in a circle, title, the clipboard toolbar keys in circles),
 * a section label, and two columns of cards with 8 dp corners down to the lower edge. There is no
 * bottom key row; the back button returns to the keys.
 */
internal object FlatClipboard {
    /** Round keys after the title; more would push the title out of a phone-wide header. */
    const val MAX_HEADER_KEYS = 3
    private const val BUTTON_DP = 34f
    private const val EDGE_DP = 6f
    private const val STRIP_TOP_DP = 4f // as in FlatStrip, so that the header sits where the toolbar sits
    private const val TITLE_GAP_DP = 13f
    private const val TITLE_TEXT_DP = 22f
    private const val ACTION_GAP_DP = 16f
    private const val LABEL_HEIGHT_DP = 25f
    private const val LABEL_START_DP = 11f
    private const val LABEL_TEXT_DP = 13f
    private const val CARD_RADIUS_DP = 8f
    private const val CARD_SIDE_PX_PER_WIDTH = 15f / 1200f // half of the 30 px gap between the columns
    private const val CARD_VERTICAL_DP = 4f
    private const val CARD_PADDING_DP = 12f
    private const val CARD_TEXT_DP = 16f

    private fun View.dp(value: Float) = Math.round(value * resources.displayMetrics.density)

    /** Replaces the plain row of toolbar keys in [strip] by back button, title and round action keys. */
    fun buildHeader(strip: ViewGroup, keys: List<ImageButton>, colors: Colors, onBack: View.OnClickListener) {
        val scroll = strip.parent as? HorizontalScrollView
        scroll?.isFillViewport = true
        scroll?.setPadding(0, strip.dp(STRIP_TOP_DP), 0, 0)
        strip.removeAllViews() // drops the spacer that pushed the keys to the right
        (strip as? LinearLayout)?.gravity = Gravity.CENTER_VERTICAL
        val button = strip.dp(BUTTON_DP)
        val circle = colors.get(ColorType.KEY_BACKGROUND)
        val icon = colors.get(ColorType.KEY_TEXT)

        val back = ImageButton(strip.context, null, 0).apply {
            id = R.id.agent_clipboard_back
            contentDescription = context.getString(R.string.agent_clipboard_back)
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_arrow_back)?.mutate()?.apply { setTint(icon) })
            setOnClickListener(onBack)
        }
        strip.addView(asCircle(back, circle), LinearLayout.LayoutParams(button, button).apply { marginStart = strip.dp(EDGE_DP) })

        val title = TextView(strip.context).apply {
            id = R.id.agent_clipboard_title
            text = context.getString(R.string.agent_clipboard_title)
            setTextColor(icon)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, TITLE_TEXT_DP)
            typeface = FlatStyle.labelTypeface
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
        }
        strip.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = strip.dp(TITLE_GAP_DP) })

        // The header does not scroll: back button and title must stay visible. It takes the first
        // keys of the clipboard toolbar; closing is the back button's job.
        val shown = keys.filter { it.tag != ToolbarKey.CLOSE_HISTORY }.take(MAX_HEADER_KEYS)
        shown.forEachIndexed { index, key ->
            key.drawable?.mutate()?.setTint(icon)
            strip.addView(asCircle(key, circle), LinearLayout.LayoutParams(button, button).apply {
                marginStart = if (index == 0) 0 else strip.dp(ACTION_GAP_DP)
                if (index == shown.lastIndex) marginEnd = strip.dp(EDGE_DP)
            })
        }
    }

    private fun asCircle(button: ImageButton, color: Int): ImageButton {
        button.scaleType = ImageView.ScaleType.CENTER
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        return button
    }

    /** Adds the section label above the list and hides the bottom key row. Returns the height the label takes. */
    fun arrangeList(view: LinearLayout, bottomRow: View, colors: Colors): Int {
        bottomRow.visibility = View.GONE
        val height = view.dp(LABEL_HEIGHT_DP)
        val label = TextView(view.context).apply {
            id = R.id.agent_clipboard_section
            text = context.getString(R.string.agent_clipboard_recent)
            setTextColor(colors.get(ColorType.TOOL_BAR_KEY))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, LABEL_TEXT_DP)
            typeface = FlatStyle.labelTypeface
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(LABEL_START_DP), 0, 0, 0)
        }
        view.addView(label, 0, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height))
        return height
    }

    /** One rounded rectangle per card; pictures are clipped to it. */
    fun styleCard(card: View, text: TextView, image: ImageView, colors: Colors, listWidth: Int) {
        card.background = GradientDrawable().apply {
            cornerRadius = card.dp(CARD_RADIUS_DP).toFloat()
            setColor(colors.get(ColorType.KEY_BACKGROUND))
        }
        card.clipToOutline = true
        val side = Math.round(listWidth * CARD_SIDE_PX_PER_WIDTH)
        (card.layoutParams as RecyclerView.LayoutParams).apply {
            marginStart = side; marginEnd = side
            topMargin = card.dp(CARD_VERTICAL_DP); bottomMargin = card.dp(CARD_VERTICAL_DP)
        }
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, CARD_TEXT_DP)
        text.setTextColor(colors.get(ColorType.KEY_TEXT))
        text.typeface = FlatStyle.labelTypeface
        val padding = card.dp(CARD_PADDING_DP)
        text.setPadding(padding, padding, padding, padding)
        // the content column has margins in the layout; cards manage their own insets
        (text.parent as? View)?.let { column ->
            (column.layoutParams as ViewGroup.MarginLayoutParams).setMargins(0, 0, 0, 0)
        }
        image.adjustViewBounds = false
        image.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    /** Pictures fill a square card: as wide as a column, cropped around the centre. */
    fun squareImage(image: ImageView, listWidth: Int, columns: Int, listPadding: Int) {
        if (image.drawable !is BitmapDrawable) { // a file type icon, not a picture: keep it small
            image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            image.layoutParams = image.layoutParams.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT; height = ViewGroup.LayoutParams.WRAP_CONTENT }
            return
        }
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        val side = Math.round(listWidth * CARD_SIDE_PX_PER_WIDTH)
        val size = ((listWidth - listPadding) / columns - 2 * side).coerceAtLeast(1)
        image.layoutParams = image.layoutParams.apply { width = size; height = size }
    }

    // ---- suggestion chip in the strip ----

    /** Longest clip label that is shown on the chip of a picture; longer or missing labels become "Image". */
    const val MAX_CHIP_LABEL = 24
    private const val CHIP_COLOR = 0xFF191919.toInt()
    private const val CHIP_HEIGHT_DP = 34f
    private const val CHIP_THUMBNAIL_DP = 26f
    private const val CHIP_INSET_DP = 4f
    private const val CHIP_TEXT_DP = 16f

    /**
     * Dark pill with a round thumbnail in front of the label. There is no close cross; a long press
     * on the chip calls [onDismiss].
     */
    fun styleChip(
        chip: ViewGroup, text: TextView, image: ImageView, close: View, hasImage: Boolean,
        imageLabel: String?, onDismiss: () -> Unit,
    ) {
        chip.background = GradientDrawable().apply {
            cornerRadius = chip.dp(CHIP_HEIGHT_DP) / 2f
            setColor(CHIP_COLOR)
        }
        chip.layoutParams?.height = chip.dp(CHIP_HEIGHT_DP)
        chip.setPadding(0, 0, 0, 0)
        close.visibility = View.GONE
        val dismiss = View.OnLongClickListener { onDismiss(); true }
        text.setOnLongClickListener(dismiss)
        image.setOnLongClickListener(dismiss)

        text.setTextColor(FlatStyle.LABEL)
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, CHIP_TEXT_DP)
        text.typeface = FlatStyle.labelTypeface
        text.setPadding(chip.dp(10f), 0, chip.dp(14f), 0)
        if (!hasImage) return

        text.setCompoundDrawablesRelative(null, null, null, null)
        text.text = imageLabel ?: chip.context.getString(R.string.agent_clipboard_image)
        val size = chip.dp(CHIP_THUMBNAIL_DP)
        chip.removeView(image)
        chip.addView(image, 0, LinearLayout.LayoutParams(size, size).apply { marginStart = chip.dp(CHIP_INSET_DP) })
        image.setPadding(0, 0, 0, 0)
        image.adjustViewBounds = false
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(CHIP_COLOR) }
        image.clipToOutline = true
    }
}
