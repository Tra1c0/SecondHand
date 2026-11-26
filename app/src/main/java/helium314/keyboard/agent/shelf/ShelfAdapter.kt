// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.shelf

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import dev.agentkeyboard.core.clip.ShelfItem
import helium314.keyboard.latin.R

/** A frame as high as it is wide: tiles keep their size before their picture is there (no layout jump). */
class SquareFrame(context: Context) : FrameLayout(context) {
    /** Drawn over the children (the accent frame of a drop target); View.setForeground needs Android 6. */
    var outline: android.graphics.drawable.Drawable? = null
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY))
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        outline?.let { it.setBounds(0, 0, width, height); it.draw(canvas) }
    }
}

/**
 * Stage 102b: what the clipboard's swipe does with a tile of the shelf ([ShelfUi.swipes]): unpinned screenshots are swiped
 * away to the left like unpinned clips (hidden from the clipboard, never deleted from the gallery); pinned ones and folders not.
 */
interface ShelfSwipes {
    fun swipeDirs(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Int
    fun swiped(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder)
}

/** The colours of the keyboard's design for the shelf (E6): no colour of its own. */
class ShelfColors(val card: Int, val text: Int, val accent: Int, val pin: Int, val typeface: Typeface?)

/**
 * Stage 102: the tiles of the shelf, for the clipboard list (folders and cards, [inFolder] false) and for the grid of an
 * open folder (cards only). Tiles are not clickable for the finger (the clipboard view's touch logic handles taps and
 * long presses, `ShelfUi`); [onTap] and [onLongTap] are their accessibility actions. While a screenshot is dragged, its
 * tile stays as a faded place holder ([lifted]); the target under the finger is outlined in the accent colour ([hover]).
 */
class ShelfAdapter(
    private val palette: ShelfColors,
    private val pinIcon: Int,
    private val inFolder: Boolean,
    private val names: (dev.agentkeyboard.core.clip.ShelfFolder) -> String,
    private val describeTime: (Long) -> String,
    private val onTap: (ShelfItem) -> Unit,
    private val onLongTap: (ShelfItem) -> Unit,
) : RecyclerView.Adapter<ShelfAdapter.Holder>() {
    var items: List<ShelfItem> = emptyList()
        private set
    var lifted: String? = null
        set(value) { if (field != value) { val old = field; field = value; refreshKey(old); refreshKey(value) } }
    var hover: String? = null
        set(value) { if (field != value) { val old = field; field = value; refreshKey(old); refreshKey(value) } }
    /** A new folder: its tile grows once when it is bound ([ShelfAnimations.grow]). */
    var grows: String? = null

    init { setHasStableIds(false) }

    /** Replaces the items with granular changes (moves, inserts, removes), so the list animates instead of jumping. */
    fun submit(next: List<ShelfItem>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(o: Int, n: Int) = key(old[o]) == key(next[n])
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == next[n]
        }, true)
        items = next
        diff.dispatchUpdatesTo(this)
    }

    fun indexOf(key: String) = items.indexOfFirst { key(it) == key }

    private fun refreshKey(key: String?) {
        if (key == null) return
        val index = indexOf(key)
        if (index >= 0) notifyItemChanged(index, PAYLOAD_STATE)
    }

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = if (items[position] is ShelfItem.Folder) TYPE_FOLDER else TYPE_CARD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val dp = context.resources.displayMetrics.density
        val tile = SquareFrame(context).apply {
            id = if (viewType == TYPE_FOLDER) R.id.agent_shelf_folder else R.id.agent_shelf_card
            background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(palette.card) }
            clipToOutline = true
            isHapticFeedbackEnabled = false
            isSoundEffectsEnabled = false
            // never clickable for the finger: ShelfUi; these are the actions of TalkBack and switch access
            setOnClickListener { holderItem(this)?.let(onTap) }
            setOnLongClickListener { holderItem(this)?.let(onLongTap); true }
        }
        val params = if (inFolder) RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else StaggeredGridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val margin = Math.round((if (inFolder) 3 else 4) * dp)
        params.setMargins(margin, margin, margin, margin)
        tile.layoutParams = params
        val images = ArrayList<ImageView>()
        var label: TextView? = null
        if (viewType == TYPE_FOLDER) {
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(gap(dp), gap(dp), gap(dp), 0) }
            repeat(2) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                repeat(2) {
                    val image = miniImage(context, dp)
                    images += image
                    row.addView(image, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(gap(dp) / 2, gap(dp) / 2, gap(dp) / 2, gap(dp) / 2)
                    })
                }
                column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
            label = TextView(context).apply {
                setTextColor(palette.text)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
                typeface = palette.typeface
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Math.round(6 * dp), 0, Math.round(6 * dp), 0)
            }
            column.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(26 * dp)))
            tile.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            images += image
            tile.addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val pin = ImageView(context).apply {
            setImageResource(pinIcon)
            setColorFilter(palette.pin)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.card) }
            val p = Math.round(3 * dp)
            setPadding(p, p, p, p)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        tile.addView(pin, FrameLayout.LayoutParams(Math.round(22 * dp), Math.round(22 * dp), Gravity.TOP or Gravity.START).apply {
            setMargins(Math.round(5 * dp), Math.round(5 * dp), 0, 0)
        })
        return Holder(tile, images, label, pin)
    }

    private fun gap(dp: Float) = Math.round(6 * dp)

    private fun miniImage(context: Context, dp: Float) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply { cornerRadius = 4 * dp; setColor(0x22000000) }
        clipToOutline = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun holderItem(view: View): ShelfItem? = (view.tag as? String)?.let { key -> items.firstOrNull { key(it) == key } }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position], full = true)

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_STATE }) holder.bind(items[position], full = false)
        else holder.bind(items[position], full = true)
    }

    inner class Holder(val tile: SquareFrame, private val images: List<ImageView>, private val label: TextView?, private val pin: ImageView) :
        RecyclerView.ViewHolder(tile) {
        fun bind(item: ShelfItem, full: Boolean) {
            val key = key(item)
            tile.tag = key
            val context = tile.context
            if (full) {
                // from the screen, not the tile: the first bind has no width yet, and a new size would decode again (review, finding 6)
                val size = context.resources.displayMetrics.widthPixels / if (inFolder) 3 else 2
                when (item) {
                    is ShelfItem.Folder -> {
                        val name = names(item.folder)
                        label?.text = context.getString(R.string.agent_shelf_tile_label, name, item.shots.size)
                        images.forEachIndexed { i, view -> ShelfThumbnails.bind(view, item.shots.getOrNull(i)?.let { Shelf.shot(it.id) }, size / 2) }
                        tile.contentDescription = context.getString(R.string.agent_shelf_folder_description, name, item.shots.size) +
                            if (item.pinned) context.getString(R.string.agent_shelf_pinned_suffix) else ""
                    }
                    is ShelfItem.Card -> {
                        ShelfThumbnails.bind(images[0], Shelf.shot(item.shot.id), size)
                        tile.contentDescription = context.getString(R.string.agent_shelf_card_description, describeTime(item.shot.addedMillis)) +
                            if (item.pinned) context.getString(R.string.agent_shelf_pinned_suffix) else ""
                    }
                }
                pin.visibility = if (item.pinned) View.VISIBLE else View.GONE
            }
            tile.alpha = if (key == lifted) 0.35f else 1f
            val outlined = key == hover
            tile.outline = if (outlined) GradientDrawable().apply {
                cornerRadius = 8 * context.resources.displayMetrics.density
                setStroke(Math.round(3 * context.resources.displayMetrics.density), palette.accent)
            } else null
            tile.animate().cancel()
            val scale = if (outlined) 1.06f else 1f
            if (tile.scaleX != scale) tile.animate().scaleX(scale).scaleY(scale).setDuration(110L).start()
            if (key == grows) { grows = null; ShelfAnimations.grow(tile) }
        }
    }

    companion object {
        const val TYPE_FOLDER = 1
        const val TYPE_CARD = 2
        private const val PAYLOAD_STATE = "state"
        fun key(item: ShelfItem) = when (item) {
            is ShelfItem.Folder -> folderKey(item.folder.id)
            is ShelfItem.Card -> shotKey(item.shot.id)
        }
        fun folderKey(id: Long) = "f$id"
        fun shotKey(id: Long) = "s$id"
    }
}

/** Stage 102: the small animations of the shelf; none blocks a touch, and with system animations off none runs. */
object ShelfAnimations {
    /** A new folder grows with a slight overshoot (E9). */
    fun grow(view: View) {
        view.scaleX = 0.6f; view.scaleY = 0.6f; view.alpha = 0.4f
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(240L)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.2f)).start()
    }
}
