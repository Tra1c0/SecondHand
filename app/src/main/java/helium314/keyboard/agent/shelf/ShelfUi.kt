// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.shelf

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.agentkeyboard.core.clip.ScreenshotShelf
import dev.agentkeyboard.core.clip.ShelfDrag
import dev.agentkeyboard.core.clip.ShelfFolder
import dev.agentkeyboard.core.clip.ShelfItem
import dev.agentkeyboard.core.clip.ShelfTarget
import helium314.keyboard.agent.UiPerf
import helium314.keyboard.agent.charart.CharArtKeys
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings

/**
 * Stage 102: the screenshot shelf inside the clipboard view (`docs/SCREENSHOTS-KONZEPT.md`, E6–E9): the tiles at the top
 * of the clipboard list ([mainAdapter]), the folder window over the list, the drag of a screenshot with its shadow and drop
 * areas, the small action row after a long press, and renaming with the keyboard's own small keys.
 *
 * The clipboard view gives every touch to [onTouch] first ([ShelfDrag] decides); taps, long presses and drags of tiles
 * are handled here, everything else goes on to the list as before. Nothing here blocks a touch: the model changes at
 * once, animations are only on views nobody touches (shadow) or on views that let touches through while they close.
 */
@SuppressLint("ViewConstructor")
class ShelfUi(
    private val host: Host,
    /** The frame that holds the clipboard list; the window, the shadow and the drop area are added to it. */
    private val container: FrameLayout,
    private val list: RecyclerView,
    private val palette: ShelfColors,
    pinIcon: Int,
    private val paintBackground: (View) -> Unit,
) {
    interface Host {
        fun insert(shot: Shelf.Shot)
        /** Sends ACTION_CANCEL to the views below the clipboard view's touch logic (the list and its tiles). */
        fun cancelChildren()
        fun haptic(event: HapticEvent)
    }

    enum class Touch { PASS, CONSUME }

    private val context = container.context
    private val dp = context.resources.displayMetrics.density
    private val drag = ShelfDrag({ x, y -> targetAt(x, y) },
        ShelfDrag.Config(slopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()))
    private val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

    val mainAdapter = ShelfAdapter(palette, pinIcon, inFolder = false, names = ::nameOf, describeTime = ::timeOf,
        onTap = { tap(it) }, onLongTap = { showMenu(it) })
    private val folderAdapter = ShelfAdapter(palette, pinIcon, inFolder = true, names = ::nameOf, describeTime = ::timeOf,
        onTap = { tap(it) }, onLongTap = { showMenu(it) })

    /** The open folder, or null. */
    var openFolder: Long? = null
        private set
    private var closing = false
    private var renaming = false
    private var renameText = StringBuilder()
    private var shadow: ImageView? = null
    private var grabDx = 0f
    private var grabDy = 0f
    private var menu: View? = null
    private var swallow = false
    private var pointer = -1
    private var lastX = 0f
    private var lastY = 0f
    private val listener: () -> Unit = { refresh() }
    /** Screenshots shown in the list (folders counted with their content); -1 before the first refresh of a visit. */
    private var shownShots = -1

    // ---- the folder window

    private val window = object : FrameLayout(context) {
        // while it closes, touches go to the list below (E9: animations never block)
        override fun dispatchTouchEvent(ev: MotionEvent) = if (closing) false else super.dispatchTouchEvent(ev)
    }.apply {
        id = R.id.agent_shelf_window
        visibility = View.GONE
        isClickable = true // its empty parts do not let a tap through to the list
        paintBackground(this)
    }
    private val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val back = ImageButton(context, null, 0).apply {
        id = R.id.agent_shelf_back
        contentDescription = context.getString(R.string.agent_shelf_back)
        setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_arrow_back)?.mutate()?.apply { setTint(palette.text) })
        scaleType = ImageView.ScaleType.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.card) }
        setOnClickListener { if (renaming) stopRename(save = false) else closeFolder() }
    }
    private val title = TextView(context).apply {
        id = R.id.agent_shelf_title
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        typeface = palette.typeface
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        setOnClickListener { startRename() }
    }
    private val count = TextView(context).apply {
        id = R.id.agent_shelf_count
        setTextColor(palette.text)
        alpha = 0.7f
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        typeface = palette.typeface
    }
    private val pin = ImageButton(context, null, 0).apply {
        id = R.id.agent_shelf_pin
        setImageResource(pinIcon)
        scaleType = ImageView.ScaleType.CENTER
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.card) }
        setOnClickListener { openFolder?.let { Shelf.apply(ScreenshotShelf.togglePinFolder(Shelf.state, it)) } }
    }
    private val done = TextView(context).apply {
        id = R.id.agent_shelf_name_done
        text = context.getString(R.string.agent_shelf_done)
        setTextColor(palette.text)
        gravity = Gravity.CENTER
        setPadding(Math.round(14 * dp), 0, Math.round(14 * dp), 0)
        background = GradientDrawable().apply { cornerRadius = 17 * dp; setColor(palette.accent) }
        visibility = View.GONE
        setOnClickListener { stopRename(save = true) }
    }
    private val outZone = zone(R.id.agent_shelf_out, R.string.agent_shelf_out)
    private val newInFolderZone = zone(R.id.agent_shelf_new_in_folder, R.string.agent_shelf_new_folder)
    private val zones = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = View.GONE
        addView(outZone, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(m(6), m(4), m(3), m(4)) })
        addView(newInFolderZone, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(m(3), m(4), m(6), m(4)) })
    }
    private val grid = RecyclerView(context).apply {
        id = R.id.agent_shelf_grid
        layoutManager = GridLayoutManager(context, GRID_COLUMNS)
        adapter = folderAdapter
        clipToPadding = false
        setPadding(m(5), 0, m(5), m(8))
    }
    private val keys = CharArtKeys(context).apply {
        keyColor = palette.card
        functionalColor = palette.card
        accentColor = palette.accent
        textColor = palette.text
        accentTextColor = palette.text
        german = java.util.Locale.getDefault().language == "de"
        singleLine = true // also builds the keys with the colours above
        visibility = View.GONE
        // whole characters only, and never longer than a name may be (review, finding 8)
        onText = { if (renameText.length + it.length <= ScreenshotShelf.MAX_NAME) { renameText.append(it); showRenameText() } }
        onDelete = {
            if (renameText.isNotEmpty()) {
                renameText.setLength(renameText.offsetByCodePoints(renameText.length, -1))
                showRenameText()
            }
        }
        onEnter = { stopRename(save = true) }
        onDone = { stopRename(save = true) }
    }
    /** The drop area of the list (E9, K2): at the bottom, shown while a screenshot is dragged. */
    private val newFolderZone = zone(R.id.agent_shelf_new_folder, R.string.agent_shelf_new_folder).apply { visibility = View.GONE }

    private fun m(value: Int) = Math.round(value * dp)

    private fun zone(id: Int, text: Int) = TextView(context).apply {
        this.id = id
        this.text = context.getString(text)
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        typeface = palette.typeface
        gravity = Gravity.CENTER
        background = zoneBackground(false)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun zoneBackground(active: Boolean) = GradientDrawable().apply {
        cornerRadius = 12 * dp
        setColor(if (active) (palette.accent and 0x00FFFFFF) or 0x66000000 else palette.card)
        setStroke(Math.round(2 * dp), palette.accent, 8 * dp, if (active) 0f else 5 * dp)
    }

    /** Stage 102b: swipes of the list (set on the clipboard's RecyclerView) and of the folder grid. */
    val swipes = object : ShelfSwipes {
        private fun cardOf(holder: RecyclerView.ViewHolder): ShelfItem.Card? {
            val adapter = holder.bindingAdapter as? ShelfAdapter ?: return null
            return adapter.items.getOrNull(holder.bindingAdapterPosition) as? ShelfItem.Card
        }

        override fun swipeDirs(holder: RecyclerView.ViewHolder): Int {
            val card = cardOf(holder) ?: return 0 // folders are not swiped: one stray swipe must not empty a collection
            return if (card.pinned || drag.capturing) 0 else androidx.recyclerview.widget.ItemTouchHelper.LEFT
        }

        override fun swiped(holder: RecyclerView.ViewHolder) {
            val adapter = holder.bindingAdapter as? ShelfAdapter
            val position = holder.bindingAdapterPosition
            val card = cardOf(holder)
            val next = card?.let { ScreenshotShelf.dismiss(Shelf.state, it.shot.id) } ?: Shelf.state
            // changed meanwhile (pinned, gone): the tile comes back to its place instead of staying swiped out
            if (next === Shelf.state) { if (position >= 0) adapter?.notifyItemChanged(position); return }
            Shelf.apply(next)
        }
    }

    init {
        androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) = swipes.swipeDirs(viewHolder)
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = swipes.swiped(viewHolder)
            // as for a clip in the list (half the list = one card of two columns): one tile's width of the three columns
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 1f / GRID_COLUMNS
        }).attachToRecyclerView(grid)
        header.addView(back, LinearLayout.LayoutParams(m(34), m(34)).apply { marginStart = m(6) })
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = m(12) })
        header.addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, m(34)).apply { marginEnd = m(6) })
        header.addView(count, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = m(10) })
        header.addView(pin, LinearLayout.LayoutParams(m(34), m(34)).apply { marginEnd = m(6) })
        val top = FrameLayout(context)
        top.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        top.addView(zones, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m(HEADER_DP)))
        column.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(keys, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        window.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(window, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        container.addView(newFolderZone, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m(ZONE_DP), Gravity.BOTTOM).apply {
            setMargins(m(8), 0, m(8), m(6))
        })
    }

    /** The room the list keeps at its end, so its last row can scroll above the drop area (K2). */
    val bottomRoom get() = m(ZONE_DP + 12)

    // ---- life cycle

    /** The clipboard view opens: the shelf is read, cleaned up and asked again; tiles come at once with what is known. */
    fun start() {
        shownShots = -1
        Shelf.listen(listener)
        Shelf.prepare(context, Settings.getValues())
        refresh()
    }

    /** The clipboard view closes: a drag ends without a change, the window closes, pictures are forgotten. */
    fun stop() {
        drag.cancel()?.let { endDrag(changed = false) }
        Shelf.hold(context, Settings.getValues(), false)
        dismissMenu()
        if (renaming) stopRename(save = false)
        hideWindowNow()
        Shelf.unlisten(listener)
        ShelfThumbnails.clear()
    }

    private fun refresh() {
        val shown = Shelf.isAvailable(context, Settings.getValues())
        val items = if (shown) ScreenshotShelf.items(Shelf.state, Shelf.shots, Settings.getValues()?.mClipboardHistoryPinnedFirst != false) else emptyList()
        val shotsBefore = shownShots
        shownShots = items.sumOf { if (it is ShelfItem.Folder) it.shots.size else 1 }
        mainAdapter.submit(items)
        // measuring point: a new screenshot from its media change to the frame that shows it
        if (shownShots > shotsBefore && shotsBefore >= 0) UiPerf.reportAtNextDraw(UiPerf.SHOT_VISIBLE, list) { "shots=$shownShots tiles=${items.size}" }
        val open = openFolder ?: return
        val folder = Shelf.state.folder(open)
        if (folder == null || !shown) { closeFolder(); return }
        if (!renaming) showHeader(folder)
        if (drag.phase != ShelfDrag.Phase.DRAGGING) folderAdapter.submit(folderCards(folder.id))
    }

    private fun folderCards(folder: Long): List<ShelfItem> =
        ScreenshotShelf.folderShots(Shelf.state, folder, Shelf.shots).map { ShelfItem.Card(it, it.id in Shelf.state.pinnedShots, fresh = false) }

    private fun showHeader(folder: ShelfFolder) {
        title.text = nameOf(folder)
        title.contentDescription = context.getString(R.string.agent_shelf_rename_hint, nameOf(folder))
        count.text = context.getString(R.string.agent_shelf_count, ScreenshotShelf.folderShots(Shelf.state, folder.id, Shelf.shots).size)
        pin.contentDescription = context.getString(if (folder.pinned) R.string.agent_shelf_unpin else R.string.agent_shelf_pin)
        pin.setColorFilter(if (folder.pinned) palette.pin else palette.text)
        pin.alpha = if (folder.pinned) 1f else 0.6f
    }

    fun nameOf(folder: ShelfFolder): String = folder.name
        ?: if (folder.auto || folder.number == 0) context.getString(R.string.agent_shelf_auto_name)
        else context.getString(R.string.agent_shelf_folder_name, folder.number)

    private fun timeOf(millis: Long): String = timeFormat.format(java.util.Date(millis))

    // ---- taps and menu

    /** A tap of TalkBack or switch access (the finger's taps come through [onTouch]). */
    private fun tap(item: ShelfItem) {
        when (item) {
            is ShelfItem.Folder -> openFolder(item.folder.id)
            is ShelfItem.Card -> Shelf.shot(item.shot.id)?.let { host.insert(it) }
        }
    }

    fun openFolder(id: Long, rename: Boolean = false) {
        val folder = Shelf.state.folder(id) ?: return
        dismissMenu()
        UiPerf.mark(UiPerf.FOLDER_OPEN)
        openFolder = id
        closing = false
        window.animate().cancel()
        showHeader(folder)
        folderAdapter.submit(folderCards(id))
        grid.scrollToPosition(0)
        // grows from its tile (E7): 160 ms, touchable from the first frame
        val tile = mainTileView(ShelfAdapter.folderKey(id))
        window.pivotX = tile?.let { it.left + it.width / 2f } ?: (container.width / 2f)
        window.pivotY = tile?.let { it.top + it.height / 2f } ?: 0f
        window.visibility = View.VISIBLE
        window.alpha = 0f; window.scaleX = 0.9f; window.scaleY = 0.9f
        window.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).setListener(null).start()
        UiPerf.reportAtNextDraw(UiPerf.FOLDER_OPEN, window) { "shots=${folderAdapter.itemCount}" }
        if (rename) startRename()
    }

    fun closeFolder() {
        if (openFolder == null) return
        if (renaming) stopRename(save = false)
        val tile = openFolder?.let { mainTileView(ShelfAdapter.folderKey(it)) }
        openFolder = null
        closing = true
        if (tile != null) { window.pivotX = tile.left + tile.width / 2f; window.pivotY = tile.top + tile.height / 2f }
        window.animate().cancel()
        window.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(140L).withEndAction {
            if (closing) { window.visibility = View.GONE; closing = false }
        }.start()
    }

    private fun hideWindowNow() {
        openFolder = null
        closing = false
        window.animate().cancel()
        window.visibility = View.GONE
    }

    private fun showMenu(item: ShelfItem) {
        dismissMenu()
        val tile = (if (openFolder != null) folderTileView(ShelfAdapter.key(item)) else mainTileView(ShelfAdapter.key(item)))
        val row = LinearLayout(context).apply {
            id = R.id.agent_shelf_menu
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = 18 * dp; setColor(palette.card); setStroke(m(1), palette.accent) }
            elevation = 6 * dp
            setPadding(m(4), m(4), m(4), m(4))
        }
        fun button(id: Int, text: String, action: () -> Unit) = TextView(context).apply {
            this.id = id
            this.text = text
            setTextColor(palette.text)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            typeface = palette.typeface
            gravity = Gravity.CENTER
            setPadding(m(14), 0, m(14), 0)
            setOnClickListener { dismissMenu(); action() }
        }.also { row.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, m(34))) }
        when (item) {
            is ShelfItem.Card -> button(R.id.agent_shelf_menu_pin, context.getString(
                if (item.pinned) R.string.agent_shelf_unpin_shot else R.string.agent_shelf_pin_shot)) {
                Shelf.apply(ScreenshotShelf.togglePinShot(Shelf.state, item.shot.id))
            }
            is ShelfItem.Folder -> {
                button(R.id.agent_shelf_menu_pin, context.getString(if (item.pinned) R.string.agent_shelf_unpin_shot else R.string.agent_shelf_pin_shot)) {
                    Shelf.apply(ScreenshotShelf.togglePinFolder(Shelf.state, item.folder.id))
                }
                button(R.id.agent_shelf_menu_rename, context.getString(R.string.agent_shelf_rename)) { openFolder(item.folder.id, rename = true) }
            }
        }
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val bounds = tile?.let { rectIn(it) } ?: RectF(0f, 0f, container.width.toFloat(), 0f)
        val x = (bounds.centerX() - row.measuredWidth / 2f).coerceIn(m(4).toFloat(), (container.width - row.measuredWidth - m(4)).toFloat().coerceAtLeast(0f))
        val y = if (bounds.top - row.measuredHeight - m(6) >= 0) bounds.top - row.measuredHeight - m(6) else (bounds.bottom + m(6))
            .coerceAtMost((container.height - row.measuredHeight).toFloat().coerceAtLeast(0f))
        container.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = x.toInt(); topMargin = y.toInt()
        })
        menu = row
    }

    private fun dismissMenu() {
        menu?.let { container.removeView(it) }
        menu = null
    }

    // ---- renaming with the keyboard's own small keys (E8): nothing goes into the app's field

    private fun startRename() {
        val folder = openFolder?.let { Shelf.state.folder(it) } ?: return
        renaming = true
        renameText = StringBuilder(nameOf(folder))
        title.id = R.id.agent_shelf_name_edit
        showRenameText()
        count.visibility = View.GONE
        pin.visibility = View.GONE
        done.visibility = View.VISIBLE
        grid.visibility = View.GONE
        keys.visibility = View.VISIBLE
    }

    private fun showRenameText() {
        title.text = renameText.toString() + "▏"
        title.contentDescription = renameText.toString()
    }

    private fun stopRename(save: Boolean) {
        if (!renaming) return
        renaming = false
        val id = openFolder
        if (save && id != null) Shelf.apply(ScreenshotShelf.rename(Shelf.state, id, renameText.toString()))
        title.id = R.id.agent_shelf_title
        count.visibility = View.VISIBLE
        pin.visibility = View.VISIBLE
        done.visibility = View.GONE
        grid.visibility = View.VISIBLE
        keys.visibility = View.GONE
        id?.let { Shelf.state.folder(it) }?.let { showHeader(it) }
    }

    // ---- touches

    /** The main looper, not the view's queue: a view that is not attached runs nothing posted to it. */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val lift = Runnable { handle(drag.tick(SystemClock.uptimeMillis())) }
    private val frame = object : Runnable {
        override fun run() {
            if (drag.phase != ShelfDrag.Phase.DRAGGING) return
            autoScroll()
            handle(drag.move(pointer, lastX, lastY, SystemClock.uptimeMillis()))
            handler.postDelayed(this, FRAME_MS)
        }
    }

    /** Every touch of the clipboard view, in its coordinates; [Touch.CONSUME]: the view keeps it from the list. */
    fun onTouch(event: MotionEvent, offsetX: Float, offsetY: Float): Touch {
        val x = event.getX(event.actionIndex) - offsetX
        val y = event.getY(event.actionIndex) - offsetY
        val now = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swallow = false
                // a new gesture: whatever the last one left (an end that never came) ends without a change
                if (drag.phase != ShelfDrag.Phase.IDLE) handle(drag.cancel())
                if (menu != null) {
                    val inMenu = menu?.let { rectIn(it).contains(x, y) } == true
                    if (!inMenu) { dismissMenu(); swallow = true; return Touch.CONSUME }
                    return Touch.PASS
                }
                if (renaming) return Touch.PASS
                pointer = event.getPointerId(0)
                lastX = x; lastY = y
                // a touch that stops a fling only stops it, as upstream (review, finding 7)
                val scrolling = (if (openFolder != null) grid else list).scrollState != RecyclerView.SCROLL_STATE_IDLE
                if (!scrolling && drag.down(pointer, x, y, now, grabAt(x, y))) {
                    handler.removeCallbacks(lift)
                    handler.postDelayed(lift, LIFT_MS)
                }
                return Touch.PASS
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (swallow) return Touch.CONSUME
                val was = drag.capturing
                handle(drag.secondFinger())
                handler.removeCallbacks(lift)
                return if (was) Touch.CONSUME else Touch.PASS
            }
            MotionEvent.ACTION_MOVE -> {
                if (swallow) return Touch.CONSUME
                val index = event.findPointerIndex(pointer)
                if (index < 0) return if (drag.capturing) Touch.CONSUME else Touch.PASS
                lastX = event.getX(index) - offsetX; lastY = event.getY(index) - offsetY
                handle(drag.move(pointer, lastX, lastY, now))
                return if (drag.capturing) Touch.CONSUME else Touch.PASS
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (swallow) { if (event.actionMasked == MotionEvent.ACTION_UP) swallow = false; return Touch.CONSUME }
                if (event.getPointerId(event.actionIndex) != pointer) return if (drag.capturing) Touch.CONSUME else Touch.PASS
                handler.removeCallbacks(lift)
                val wasTracked = drag.phase != ShelfDrag.Phase.IDLE
                val result = drag.up(pointer, x, y, now)
                if (result == null) return if (wasTracked) Touch.CONSUME else Touch.PASS
                host.cancelChildren() // the tile itself never clicks
                handle(result)
                return Touch.CONSUME
            }
            MotionEvent.ACTION_CANCEL -> {
                swallow = false
                handler.removeCallbacks(lift)
                handle(drag.cancel())
                return Touch.PASS
            }
        }
        return if (drag.capturing) Touch.CONSUME else Touch.PASS
    }

    private fun handle(event: ShelfDrag.Event?) {
        when (event) {
            null -> {}
            ShelfDrag.Event.Scroll -> handler.removeCallbacks(lift)
            is ShelfDrag.Event.Tap -> when (val grab = event.grab) {
                is ShelfDrag.Grab.Folder -> openFolder(grab.folder)
                is ShelfDrag.Grab.Shot -> Shelf.shot(grab.shot)?.let { host.insert(it) }
            }
            is ShelfDrag.Event.Lift -> {
                Shelf.hold(context, Settings.getValues(), true) // nothing moves under the lifted tile (review, finding 5)
                host.cancelChildren() // from here on the list neither scrolls nor clicks
                host.haptic(HapticEvent.KEY_LONG_PRESS)
                tileOf(event.grab)?.animate()?.scaleX(1.05f)?.scaleY(1.05f)?.setDuration(110L)?.start()
            }
            is ShelfDrag.Event.Menu -> {
                tileOf(event.grab)?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(110L)?.start()
                itemOf(event.grab)?.let { showMenu(it) }
                Shelf.hold(context, Settings.getValues(), false) // after the row found its tile (re-review N3)
            }
            is ShelfDrag.Event.Start -> startDrag(event.grab)
            is ShelfDrag.Event.Hover -> hover(event.target)
            is ShelfDrag.Event.Dwell -> if (event.target == ShelfTarget.Out && openFolder != null) {
                // held over "In die Ablage": the folder closes, the drag goes on in the list (E9)
                closeFolder()
                showZones()
                hover(targetAt(lastX, lastY))
            }
            is ShelfDrag.Event.Drop -> drop(event.grab, event.target)
            is ShelfDrag.Event.Cancel -> {
                Shelf.hold(context, Settings.getValues(), false)
                tileOf(event.grab)?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(110L)?.start()
                if (event.grab is ShelfDrag.Grab.Shot && shadow != null) { endDrag(changed = false); refresh() }
            }
        }
    }

    private fun grabAt(x: Float, y: Float): ShelfDrag.Grab? {
        if (renaming) return null
        val open = openFolder
        if (open != null) {
            val view = childUnder(grid, x, y) ?: return null
            val key = view.tag as? String ?: return null
            return if (key.startsWith("s")) ShelfDrag.Grab.Shot(key.drop(1).toLong(), open) else null
        }
        val view = childUnder(list, x, y) ?: return null
        val key = view.tag as? String ?: return null
        val id = key.drop(1).toLongOrNull() ?: return null
        return if (key.startsWith("f")) ShelfDrag.Grab.Folder(id) else ShelfDrag.Grab.Shot(id, null)
    }

    private fun childUnder(recycler: RecyclerView, x: Float, y: Float): View? {
        val origin = rectIn(recycler)
        if (!origin.contains(x, y)) return null
        val rx = x - origin.left
        val ry = y - origin.top
        // the topmost child under the finger that still stands for an item (not one that animates away)
        for (i in recycler.childCount - 1 downTo 0) {
            val child = recycler.getChildAt(i)
            if (recycler.getChildViewHolder(child)?.bindingAdapterPosition == RecyclerView.NO_POSITION) continue
            val left = child.left + child.translationX
            val top = child.top + child.translationY
            if (rx >= left && rx <= left + child.width && ry >= top && ry <= top + child.height) return child
        }
        return null
    }

    /** [view]'s bounds in [container] coordinates. */
    private fun rectIn(view: View): RectF {
        var left = 0f
        var top = 0f
        var current: View? = view
        while (current != null && current !== container) {
            left += current.left + current.translationX - (if (current !== view) current.scrollX else 0)
            top += current.top + current.translationY - (if (current !== view) current.scrollY else 0)
            current = current.parent as? View
        }
        return RectF(left, top, left + view.width, top + view.height)
    }

    private fun mainTileView(key: String): View? = tileIn(list, key)
    private fun folderTileView(key: String): View? = tileIn(grid, key)
    private fun tileIn(recycler: RecyclerView, key: String): View? = (0 until recycler.childCount).map { recycler.getChildAt(it) }
        .firstOrNull { it.tag == key && recycler.getChildViewHolder(it)?.bindingAdapterPosition != RecyclerView.NO_POSITION }

    private fun tileOf(grab: ShelfDrag.Grab): View? = when (grab) {
        is ShelfDrag.Grab.Folder -> mainTileView(ShelfAdapter.folderKey(grab.folder))
        is ShelfDrag.Grab.Shot -> if (grab.folder != null && openFolder != null) folderTileView(ShelfAdapter.shotKey(grab.shot))
            else mainTileView(ShelfAdapter.shotKey(grab.shot))
    }

    private fun itemOf(grab: ShelfDrag.Grab): ShelfItem? = when (grab) {
        is ShelfDrag.Grab.Folder -> mainAdapter.items.firstOrNull { it is ShelfItem.Folder && it.folder.id == grab.folder }
        is ShelfDrag.Grab.Shot -> (if (openFolder != null) folderAdapter.items else mainAdapter.items)
            .firstOrNull { it is ShelfItem.Card && it.shot.id == grab.shot }
    }

    /** What lies under the finger while a screenshot is dragged (container coordinates). */
    private fun targetAt(x: Float, y: Float): ShelfTarget {
        if (x < 0 || y < 0 || x > container.width || y > container.height) return ShelfTarget.None
        val grabbed = drag.grab as? ShelfDrag.Grab.Shot
        val open = openFolder
        if (open != null) {
            if (y < m(HEADER_DP)) {
                return when {
                    rectIn(outZone).contains(x, y) -> ShelfTarget.Out
                    rectIn(newInFolderZone).contains(x, y) -> ShelfTarget.NewFolder
                    else -> ShelfTarget.None
                }
            }
            val shot = grabbed?.shot ?: return ShelfTarget.None
            val members = folderAdapter.items.mapNotNull { (it as? ShelfItem.Card)?.shot?.id }
            // the slot under the finger from the grid's geometry, not from the views: while the others make room they are
            // animated, and a view moving away under the finger would flip the order back and forth (device test run 45802882053)
            val slot = slotAt(x, y, members.size) ?: return (drag.target as? ShelfTarget.Order) ?: ShelfTarget.Order(open, members)
            val order = members.filter { it != shot }.toMutableList()
            order.add(slot.coerceIn(0, order.size), shot)
            return ShelfTarget.Order(open, order)
        }
        if (newFolderZone.visibility == View.VISIBLE && rectIn(newFolderZone).contains(x, y)) return ShelfTarget.NewFolder
        val view = childUnder(list, x, y)
        val key = view?.tag as? String
        if (key != null) {
            val id = key.drop(1).toLongOrNull() ?: return ShelfTarget.Out
            return if (key.startsWith("f")) ShelfTarget.Folder(id) else ShelfTarget.Card(id)
        }
        return if (rectIn(list).contains(x, y)) ShelfTarget.Out else ShelfTarget.None
    }

    /** The cell of the folder grid under (x, y): rows of [GRID_COLUMNS] square cells, counting what is scrolled away. */
    private fun slotAt(x: Float, y: Float, count: Int): Int? {
        if (count == 0) return null
        val bounds = rectIn(grid)
        if (!bounds.contains(x, y)) return null
        val cell = (grid.width - grid.paddingLeft - grid.paddingRight) / GRID_COLUMNS.toFloat()
        if (cell <= 0f) return null
        val first = (0 until grid.childCount).map { grid.getChildAt(it) }.firstOrNull()
        val row = first?.let { child ->
            val params = child.layoutParams as ViewGroup.MarginLayoutParams
            (child.height + params.topMargin + params.bottomMargin).toFloat()
        }?.takeIf { it > 0f } ?: cell
        val column = ((x - bounds.left - grid.paddingLeft) / cell).toInt().coerceIn(0, GRID_COLUMNS - 1)
        val line = ((y - bounds.top - grid.paddingTop + grid.computeVerticalScrollOffset()) / row).toInt().coerceAtLeast(0)
        return (line * GRID_COLUMNS + column).coerceAtMost(count - 1)
    }

    private fun startDrag(grab: ShelfDrag.Grab.Shot) {
        Shelf.hold(context, Settings.getValues(), true)
        val tile = tileOf(grab)
        tile?.animate()?.cancel()
        tile?.scaleX = 1f; tile?.scaleY = 1f
        val bounds = tile?.let { rectIn(it) } ?: RectF(lastX - m(40), lastY - m(40), lastX + m(40), lastY + m(40))
        grabDx = lastX - bounds.left
        grabDy = lastY - bounds.top
        val bitmap = if (tile == null) null else runCatching {
            Bitmap.createBitmap(tile.width.coerceAtLeast(1), tile.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { tile.draw(Canvas(it)) }
        }.getOrNull()
        val view = ImageView(context).apply {
            id = R.id.agent_shelf_shadow
            setImageBitmap(bitmap)
            elevation = 10 * dp
            alpha = 0.92f
            scaleX = 1.05f; scaleY = 1.05f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        container.addView(view, FrameLayout.LayoutParams(bounds.width().toInt(), bounds.height().toInt()))
        shadow = view
        moveShadow()
        val key = ShelfAdapter.shotKey(grab.shot)
        if (grab.folder != null && openFolder != null) folderAdapter.lifted = key else mainAdapter.lifted = key
        showZones()
        handler.postDelayed(frame, FRAME_MS)
    }

    private fun showZones() {
        if (openFolder != null) {
            header.visibility = View.INVISIBLE
            zones.visibility = View.VISIBLE
            newFolderZone.visibility = View.GONE
        } else {
            header.visibility = View.VISIBLE
            zones.visibility = View.GONE
            newFolderZone.alpha = 0f
            newFolderZone.visibility = View.VISIBLE
            newFolderZone.animate().alpha(1f).setDuration(120L).start()
        }
    }

    private fun moveShadow() {
        val view = shadow ?: return
        view.translationX = lastX - grabDx
        view.translationY = lastY - grabDy
    }

    private fun hover(target: ShelfTarget) {
        moveShadow()
        val wasNew = newFolderZone.tag == true || newInFolderZone.tag == true
        val isNew = target == ShelfTarget.NewFolder
        newFolderZone.background = zoneBackground(isNew && openFolder == null)
        newInFolderZone.background = zoneBackground(isNew && openFolder != null)
        outZone.background = zoneBackground(target == ShelfTarget.Out && openFolder != null)
        newFolderZone.tag = isNew && openFolder == null
        newInFolderZone.tag = isNew && openFolder != null
        if (isNew && !wasNew) host.haptic(HapticEvent.KEY_PRESS)
        val grabbed = (drag.grab as? ShelfDrag.Grab.Shot)?.shot
        mainAdapter.hover = when (target) {
            is ShelfTarget.Folder -> ShelfAdapter.folderKey(target.folder)
            is ShelfTarget.Card -> if (target.shot == grabbed) null else ShelfAdapter.shotKey(target.shot)
            else -> null
        }
        if (target is ShelfTarget.Order && openFolder == target.folder) {
            // the others make room while the finger is down (live order, saved only on the drop)
            val byId = folderAdapter.items.associateBy { (it as ShelfItem.Card).shot.id }
            folderAdapter.submit(target.members.mapNotNull { byId[it] })
        }
    }

    private fun drop(grab: ShelfDrag.Grab.Shot, target: ShelfTarget) {
        val before = Shelf.state
        val (next, made) = ScreenshotShelf.drop(before, grab.shot, target)
        val changed = next !== before
        if (made != null) mainAdapter.grows = ShelfAdapter.folderKey(made)
        if (changed) {
            host.haptic(HapticEvent.KEY_PRESS)
            // out of the folder or into a new one: the list shows where it went
            if (openFolder != null && (target == ShelfTarget.NewFolder || target == ShelfTarget.Out)) closeFolder()
        }
        // the new state first: endDrag releases the answers that waited (review, finding 2), they build on it
        Shelf.apply(next)
        endDrag(changed, target)
        if (!changed) refresh()
    }

    /** Ends a drag: the shadow flies to its target (or back), the place holder comes back; the list takes answers again. */
    private fun endDrag(changed: Boolean, target: ShelfTarget = ShelfTarget.None) {
        handler.removeCallbacks(frame)
        val view = shadow
        shadow = null
        val lifted = folderAdapter.lifted ?: mainAdapter.lifted
        folderAdapter.lifted = null
        mainAdapter.lifted = null
        mainAdapter.hover = null
        header.visibility = View.VISIBLE
        zones.visibility = View.GONE
        newFolderZone.animate().cancel()
        newFolderZone.visibility = View.GONE
        newFolderZone.tag = false
        newInFolderZone.tag = false
        Shelf.hold(context, Settings.getValues(), false)
        if (view != null) {
            val goal = when {
                changed && target is ShelfTarget.Folder -> mainTileView(ShelfAdapter.folderKey(target.folder))
                !changed && lifted != null -> mainTileView(lifted) ?: folderTileView(lifted)
                else -> null
            }?.let { rectIn(it) }
            val animation = view.animate().setDuration(150L).withEndAction { container.removeView(view) }
            if (goal != null) animation.translationX(goal.left).translationY(goal.top).scaleX(if (changed) 0.5f else 1f)
                .scaleY(if (changed) 0.5f else 1f).alpha(if (changed) 0f else 1f)
            else animation.alpha(0f).scaleX(0.8f).scaleY(0.8f)
            animation.start()
        }
    }

    private fun autoScroll() {
        val recycler = if (openFolder != null) grid else list
        val bounds = rectIn(recycler)
        val band = 40 * dp
        val bottomEdge = if (openFolder == null) bounds.bottom - m(ZONE_DP + 6) else bounds.bottom
        val speed = when {
            lastY < bounds.top + band && lastY > bounds.top - band -> -Math.round(10 * dp)
            lastY > bottomEdge - band && lastY < bottomEdge -> Math.round(10 * dp)
            else -> 0
        }
        if (speed != 0) recycler.scrollBy(0, speed)
    }

    companion object {
        private const val HEADER_DP = 48
        private const val ZONE_DP = 48
        const val LIFT_MS = 350L
        private const val FRAME_MS = 16L
        private const val GRID_COLUMNS = 3
    }
}
