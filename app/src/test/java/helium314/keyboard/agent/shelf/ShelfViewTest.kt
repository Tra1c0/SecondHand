// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.RecyclerView
import dev.agentkeyboard.core.clip.ScreenshotShelf
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.agent.ScreenshotChip
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.clipboard.ClipboardHistoryView
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 102 (`docs/SCREENSHOTS-KONZEPT.md`, A1–A12, A18, A20): the shelf in the real clipboard view, with real touch events
 * through `ClipboardHistoryView.dispatchTouchEvent` (down, held with the looper's clock, moved, lifted or cancelled).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(qualifiers = "de-w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class ShelfViewTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_LXX_Base)
    private val now = System.currentTimeMillis()
    private val looper get() = shadowOf(android.os.Looper.getMainLooper())
    private val field = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT
        androidx.core.view.inputmethod.EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/*"))
    }
    private lateinit var image: File
    private val inserted = ArrayList<InputContentInfoCompat>()

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        Shelf.resetForTest()
        ShelfThumbnails.background = java.util.concurrent.Executor { it.run() }
        ShelfThumbnails.decoder = { _, _, size -> Bitmap.createBitmap(size.coerceIn(1, 64), size.coerceIn(1, 64), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) } }
        File(AgentStorageContexts.keyboard(latinIME).filesDir, ShelfStore.FILE_NAME).delete()
        latinIME.prefs().edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
            .putBoolean(AgentPreferences.SCREENSHOT_CHIP, true).putInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, 121).commit()
        shadowOf(latinIME.application).grantPermissions(ScreenshotChip.permission())
        load(field)
        image = File(latinIME.cacheDir, "Screenshot_shelf.png")
        Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @After fun tearDown() {
        Shelf.resetForTest()
        latinIME.prefs().edit().clear().commit()
        load(EditorInfo())
        KeyboardSwitcher::class.java.getDeclaredField("mClipboardStripView").apply { isAccessible = true }.set(KeyboardSwitcher.getInstance(), null)
    }

    private fun load(editorInfo: EditorInfo) {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editorInfo, false, latinIME.packageName))
        KeyboardIconsSet.instance.loadIcons(context)
    }

    private fun shots(count: Int) = (1..count).map { Shelf.Shot(it.toLong(), Uri.fromFile(image), "image/png", now - (count - it + 1) * 1000L) }

    private fun open(count: Int, editorInfo: EditorInfo = field, manager: ClipboardHistoryManager = ClipboardHistoryManager(latinIME)): ClipboardHistoryView {
        Shelf.finder = { shots(count) }
        val strip = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        KeyboardSwitcher::class.java.getDeclaredField("mClipboardStripView").apply { isAccessible = true }
            .set(KeyboardSwitcher.getInstance(), strip.findViewById<LinearLayout>(R.id.clipboard_strip))
        val view = LayoutInflater.from(context).inflate(R.layout.clipboard_history_view, FrameLayout(context), false) as ClipboardHistoryView
        view.keyboardActionListener = object : KeyboardActionListener.Adapter() {
            override fun onContent(content: InputContentInfoCompat) { inserted += content }
        }
        view.startClipboardHistory(manager, null, editorInfo, view.keyboardActionListener)
        view.visibility = View.VISIBLE
        layout(view)
        return view
    }

    private fun layout(view: View) {
        looper.idle()
        list(view).itemAnimator?.endAnimations() // views that animate away are not what the list shows
        view.findViewById<RecyclerView>(R.id.agent_shelf_grid)?.itemAnimator?.endAnimations()
        view.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1200, 1400)
        looper.idle()
        view.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1200, 1400)
    }

    private fun list(view: View) = view.findViewById<RecyclerView>(R.id.clipboard_list)
    private fun tiles(view: View): List<View> = list(view).let { rv -> (0 until rv.childCount).map { rv.getChildAt(it) }
        .filter { it.tag is String && rv.getChildViewHolder(it).bindingAdapterPosition != RecyclerView.NO_POSITION } }
    private fun folderTiles(view: View): List<View> = view.findViewById<RecyclerView>(R.id.agent_shelf_grid).let { rv ->
        (0 until rv.childCount).map { rv.getChildAt(it) } }

    /** The centre of [child] in the coordinates of [view] (summed up the parents: the views are not in a window). */
    private fun centre(view: View, child: View): Pair<Float, Float> {
        var x = child.width / 2f
        var y = child.height / 2f
        var current: View = child
        while (current !== view) {
            x += current.left + current.translationX
            y += current.top + current.translationY
            val parent = current.parent as View
            x -= parent.scrollX; y -= parent.scrollY
            current = parent
        }
        return x to y
    }

    private var downTime = 0L
    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = time
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun hold(millis: Long) = looper.idleFor(Duration.ofMillis(millis))

    /** Long press, then glide in steps to (x, y) with the looper's clock running; [up] lifts there. */
    private fun dragTo(view: View, from: Pair<Float, Float>, to: Pair<Float, Float>, up: Boolean = true, dwell: Long = 0) {
        touch(view, MotionEvent.ACTION_DOWN, from.first, from.second)
        hold(ShelfUi.LIFT_MS + 50)
        for (i in 1..8) {
            touch(view, MotionEvent.ACTION_MOVE, from.first + (to.first - from.first) * i / 8, from.second + (to.second - from.second) * i / 8)
            hold(20)
        }
        if (dwell > 0) { hold(dwell); touch(view, MotionEvent.ACTION_MOVE, to.first + 1, to.second); hold(20) }
        if (up) touch(view, MotionEvent.ACTION_UP, to.first, to.second)
        hold(400)
        layout(view)
    }

    @Test fun twoScreenshotsAreCardsAndTheThirdBundlesThemInOneFolder() {
        val view = open(2)
        assertEquals(listOf("Screenshot", "Screenshot"), tiles(view).map { it.contentDescription.toString().substringBefore(",") })
        Shelf.finder = { shots(3) }
        Shelf.refresh(latinIME, Settings.getValues())
        layout(view)
        val shown = tiles(view)
        assertEquals(1, shown.size)
        assertEquals("Ordner Screenshots, 3 Screenshots", shown.single().contentDescription)
        assertEquals(R.id.agent_shelf_folder, shown.single().id)
    }

    @Test fun reopeningAfterTheHistoryChangedShowsEveryClipAndDoesNotCrash() {
        // review of 102, finding 1: the one ConcatAdapter kept the clip count of the last visit
        val dao = assertNotNull(helium314.keyboard.latin.database.ClipboardDao.getInstance(latinIME))
        dao.clear()
        dao.addClip(now, false, "eins")
        val manager = ClipboardHistoryManager(latinIME)
        manager.onCreate()
        val view = open(3, manager = manager)
        val rv = list(view)
        assertEquals(1 + 1, rv.adapter!!.itemCount) // the folder and one clip
        view.stopClipboardHistory()
        dao.addClip(now + 1, false, "zwei")
        dao.addClip(now + 2, false, "drei")
        view.startClipboardHistory(manager, null, field, view.keyboardActionListener)
        layout(view)
        assertEquals(1 + 3, rv.adapter!!.itemCount)
        view.stopClipboardHistory()
        dao.clearNonPinned() // as clips that expired while the view was closed
        view.startClipboardHistory(manager, null, field, view.keyboardActionListener)
        layout(view) // bound with the old count this threw IndexOutOfBoundsException
        assertEquals(1, rv.adapter!!.itemCount)
        view.stopClipboardHistory()
        manager.onDestroy()
    }

    @Test fun swipesHideUnpinnedScreenshotsOnlyAndSurviveARestart() {
        // stage 102b, at the hook the clipboard's ItemTouchHelper calls (the real swipe: emulator scenario screenshot_shelf_swipe)
        val view = open(2) // two cards: 2, 1
        val rv = list(view)
        val shelf = assertNotNull(rv.let { (it as helium314.keyboard.keyboard.clipboard.ClipboardHistoryRecyclerView).shelfSwipes })
        fun holder(i: Int) = assertNotNull(rv.findViewHolderForAdapterPosition(i))
        assertEquals(androidx.recyclerview.widget.ItemTouchHelper.LEFT, shelf.swipeDirs(holder(0)))
        Shelf.apply(ScreenshotShelf.togglePinShot(Shelf.state, 1))
        layout(view)
        val pinned = (0 until 2).first { tiles(view)[it].contentDescription.endsWith(", angeheftet") }
        assertEquals(0, shelf.swipeDirs(holder(pinned)), "a pinned screenshot is not swiped, as pinned clips")
        val free = 1 - pinned
        shelf.swiped(holder(free))
        layout(view)
        assertEquals(1, tiles(view).size)
        assertEquals(setOf(2L), Shelf.state.hidden)
        // a folder tile is not swiped
        Shelf.finder = { shots(5) }
        Shelf.refresh(latinIME, Settings.getValues())
        layout(view)
        val folder = (0 until rv.adapter!!.itemCount).firstOrNull { rv.findViewHolderForAdapterPosition(it)?.itemView?.id == R.id.agent_shelf_folder }
        if (folder != null) assertEquals(0, shelf.swipeDirs(holder(folder)))
        // after a restart the swiped one stays away
        Shelf.resetForTest()
        Shelf.finder = { shots(2) }
        Shelf.refresh(latinIME, Settings.getValues())
        assertTrue(2L in Shelf.state.hidden && 2L !in Shelf.state.placed())
    }

    @Test fun aRealSwipeInsideAFolderHidesTheScreenshot() {
        val view = open(4)
        // in a window, so that what the ItemTouchHelper posts after its animation runs
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity).apply { addView(view, FrameLayout.LayoutParams(1200, 1400)) })
        layout(view)
        val (fx, fy) = centre(view, tiles(view).single())
        touch(view, MotionEvent.ACTION_DOWN, fx, fy)
        touch(view, MotionEvent.ACTION_UP, fx, fy)
        layout(view)
        val cell = folderTiles(view)[0]
        val (cx, cy) = centre(view, cell)
        val startX = cx + cell.width / 2f - 10
        touch(view, MotionEvent.ACTION_DOWN, startX, cy)
        for (i in 1..10) { hold(25); touch(view, MotionEvent.ACTION_MOVE, startX - (startX - 5f) * i / 10, cy) }
        touch(view, MotionEvent.ACTION_UP, 5f, cy)
        hold(1000)
        layout(view)
        assertEquals(1, Shelf.state.hidden.size, "the swipe hid one screenshot")
    }

    @Test fun noScreenshotTilesInAPasswordFieldOrIncognito() {
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        load(password)
        assertTrue(tiles(open(3, password)).isEmpty())
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(field)
        assertTrue(tiles(open(3)).isEmpty())
    }

    @Test fun aTapOpensTheFolderAtOnceWithTheBackButtonTopLeftAndBackCloses() {
        val view = open(4)
        val folder = tiles(view).single()
        val (x, y) = centre(view, folder)
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
        val window = view.findViewById<View>(R.id.agent_shelf_window)
        assertEquals(View.VISIBLE, window.visibility) // at once, before any animation frame
        layout(view)
        assertEquals(4, folderTiles(view).size)
        val back = view.findViewById<View>(R.id.agent_shelf_back)
        val (bx, by) = centre(view, back)
        assertTrue(bx < 200 && by < 200, "the back button sits top left ($bx, $by)")
        assertEquals("Screenshots", view.findViewById<android.widget.TextView>(R.id.agent_shelf_title).text.toString())
        // a tap on a screenshot inside inserts it, through the keyboard's own file provider
        val (sx, sy) = centre(view, folderTiles(view).first())
        touch(view, MotionEvent.ACTION_DOWN, sx, sy)
        touch(view, MotionEvent.ACTION_UP, sx, sy)
        val deadline = System.currentTimeMillis() + 5000 // the copy is made on a thread of its own
        while (inserted.isEmpty() && System.currentTimeMillis() < deadline) { Thread.sleep(20); looper.idle() }
        assertEquals(1, inserted.size)
        assertEquals(latinIME.getString(R.string.clipboard_provider_authority), inserted.single().contentUri.authority)
        back.performClick()
        // closing lets touches through at once (E9): a tap on the folder tile opens it again while the window still fades
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        touch(view, MotionEvent.ACTION_UP, x, y)
        assertEquals(View.VISIBLE, window.visibility)
    }

    @Test fun aHeldTileLetGoOnTheSpotOnlyShowsTheMenuAndPinsOnItsTap() {
        val view = open(2)
        val card = tiles(view).first()
        val (x, y) = centre(view, card)
        val before = Shelf.state
        touch(view, MotionEvent.ACTION_DOWN, x, y)
        hold(ShelfUi.LIFT_MS + 50)
        touch(view, MotionEvent.ACTION_UP, x, y)
        assertEquals(before, Shelf.state, "letting go changes nothing")
        assertTrue(inserted.isEmpty(), "and inserts nothing")
        val pin = assertNotNull(view.findViewById<View>(R.id.agent_shelf_menu_pin))
        pin.performClick()
        assertEquals(setOf(2L), Shelf.state.pinnedShots) // the newest is first
        assertNull(view.findViewById<View>(R.id.agent_shelf_menu))
        layout(view)
        assertTrue(tiles(view).first().contentDescription.endsWith(", angeheftet"))
    }

    @Test fun droppingOnTheNewFolderAreaMakesAFolderWithJustThatScreenshot() {
        val view = open(2)
        val card = tiles(view).first()
        val zone = view.findViewById<View>(R.id.agent_shelf_new_folder)
        touch(view, MotionEvent.ACTION_DOWN, centre(view, card).first, centre(view, card).second)
        hold(ShelfUi.LIFT_MS + 50)
        touch(view, MotionEvent.ACTION_MOVE, centre(view, card).first + 40, centre(view, card).second + 40)
        hold(20)
        assertEquals(View.VISIBLE, zone.visibility, "the drop area shows while dragging")
        assertNotNull(view.findViewById<View>(R.id.agent_shelf_shadow))
        layout(view) // the next frame lays the area out
        val target = centre(view, zone)
        touch(view, MotionEvent.ACTION_MOVE, target.first, target.second)
        hold(20)
        touch(view, MotionEvent.ACTION_UP, target.first, target.second)
        hold(400)
        val folder = Shelf.state.folders.single()
        assertEquals(listOf(2L), folder.members)
        assertFalse(folder.auto)
        assertEquals(listOf(1L), Shelf.state.fresh)
        assertEquals(View.GONE, zone.visibility)
        layout(view)
        assertTrue(tiles(view).any { it.contentDescription == "Ordner Ordner 1, 1 Screenshots" })
    }

    @Test fun droppingOnAnotherCardMakesAFolderWithBoth() {
        val view = open(2)
        val (first, second) = tiles(view)
        dragTo(view, centre(view, first), centre(view, second))
        assertEquals(listOf(1L, 2L), Shelf.state.folders.single().members)
    }

    @Test fun aCancelledDragOrOneBackToItsStartChangesNothing() {
        val view = open(2)
        val (first, second) = tiles(view)
        val before = Shelf.state
        // the system takes the touch
        dragTo(view, centre(view, first), centre(view, second), up = false)
        touch(view, MotionEvent.ACTION_CANCEL, centre(view, second).first, centre(view, second).second)
        hold(400)
        assertEquals(before, Shelf.state)
        assertNull(view.findViewById<View>(R.id.agent_shelf_shadow).takeIf { it?.parent != null })
        // the finger comes back to where it started
        val start = centre(view, tiles(view).first())
        touch(view, MotionEvent.ACTION_DOWN, start.first, start.second)
        hold(ShelfUi.LIFT_MS + 50)
        touch(view, MotionEvent.ACTION_MOVE, start.first + 200, start.second + 60)
        hold(20)
        touch(view, MotionEvent.ACTION_MOVE, start.first, start.second)
        hold(20)
        touch(view, MotionEvent.ACTION_UP, start.first, start.second)
        hold(400)
        assertEquals(before, Shelf.state)
        assertTrue(inserted.isEmpty())
        // a second finger ends a drag without a change
        touch(view, MotionEvent.ACTION_DOWN, start.first, start.second)
        hold(ShelfUi.LIFT_MS + 50)
        touch(view, MotionEvent.ACTION_MOVE, centre(view, second).first, centre(view, second).second)
        val time = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0 }, MotionEvent.PointerProperties().apply { id = 1 })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = centre(view, second).first; y = centre(view, second).second },
            MotionEvent.PointerCoords().apply { x = 10f; y = 10f })
        val pointerDown = MotionEvent.obtain(downTime, time, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        view.dispatchTouchEvent(pointerDown)
        touch(view, MotionEvent.ACTION_UP, centre(view, second).first, centre(view, second).second)
        hold(400)
        assertEquals(before, Shelf.state)
    }

    @Test fun insideAFolderTheOthersMakeRoomAndTheDropKeepsTheNewOrderAlsoAfterARestart() {
        val view = open(4) // automatic folder: 4 3 2 1
        val (fx, fy) = centre(view, tiles(view).single())
        touch(view, MotionEvent.ACTION_DOWN, fx, fy)
        touch(view, MotionEvent.ACTION_UP, fx, fy)
        layout(view)
        val cells = folderTiles(view)
        dragTo(view, centre(view, cells[3]), centre(view, cells[0]))
        val auto = Shelf.state.autoFolder!!
        assertEquals(listOf(1L, 4L, 3L, 2L), auto.members)
        val saved = Shelf.state
        Shelf.resetForTest()
        Shelf.finder = { shots(4) }
        Shelf.refresh(latinIME, Settings.getValues())
        assertEquals(saved, Shelf.state)
    }

    @Test fun insideAFolderAwayAndBackToTheStartChangesNothingAlsoWithTheViewsRelaidOut() {
        // device test run 45802882053: the slot came from the view under the finger, which moved while the others made room
        val view = open(4) // 4 3 2 1
        val (fx, fy) = centre(view, tiles(view).single())
        touch(view, MotionEvent.ACTION_DOWN, fx, fy)
        touch(view, MotionEvent.ACTION_UP, fx, fy)
        layout(view)
        val cells = folderTiles(view)
        val start = centre(view, cells[0])
        val away = centre(view, cells[2])
        val before = Shelf.state
        touch(view, MotionEvent.ACTION_DOWN, start.first, start.second)
        hold(ShelfUi.LIFT_MS + 50)
        for (i in 1..6) { touch(view, MotionEvent.ACTION_MOVE, start.first + (away.first - start.first) * i / 6, start.second); hold(20) }
        layout(view) // the others have made room: the views now stand elsewhere
        for (i in 5 downTo 0) { touch(view, MotionEvent.ACTION_MOVE, start.first + (away.first - start.first) * i / 6, start.second); hold(20) }
        layout(view)
        touch(view, MotionEvent.ACTION_UP, start.first, start.second)
        hold(400)
        assertEquals(before, Shelf.state)
    }

    @Test fun heldOverToTheClipboardTheFolderClosesAndTheDropMakesTheScreenshotLoose() {
        val view = open(4)
        val (fx, fy) = centre(view, tiles(view).single())
        touch(view, MotionEvent.ACTION_DOWN, fx, fy)
        touch(view, MotionEvent.ACTION_UP, fx, fy)
        layout(view)
        val cell = folderTiles(view)[1] // screenshot 3
        val out = view.findViewById<View>(R.id.agent_shelf_out)
        touch(view, MotionEvent.ACTION_DOWN, centre(view, cell).first, centre(view, cell).second)
        hold(ShelfUi.LIFT_MS + 50)
        touch(view, MotionEvent.ACTION_MOVE, centre(view, cell).first + 90, centre(view, cell).second - 60)
        hold(20)
        assertEquals(View.VISIBLE, (out.parent as View).visibility, "the header turns into the drop areas")
        layout(view) // the next frame lays the drop areas out
        val (ox, oy) = centre(view, out)
        touch(view, MotionEvent.ACTION_MOVE, ox, oy)
        hold(500) // longer than the dwell time
        touch(view, MotionEvent.ACTION_MOVE, ox + 2, oy)
        hold(300)
        layout(view)
        // the list again, the finger still down; lifted on the list outside every tile (right column, the folder is left)
        val rv = list(view)
        val free = centre(view, rv).let { (cx, _) -> (cx + rv.width / 4f) to (rv.top + rv.height * 0.4f) }
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.agent_shelf_new_folder).visibility, "the list's drop area after the folder closed")
        touch(view, MotionEvent.ACTION_MOVE, free.first, free.second)
        hold(20)
        touch(view, MotionEvent.ACTION_UP, free.first, free.second)
        hold(400)
        assertEquals(listOf(3L), Shelf.state.loose)
        assertEquals(listOf(4L, 2L, 1L), Shelf.state.autoFolder!!.members)
    }

    @Test fun renamingUsesTheOwnKeysAndNothingReachesTheApp() {
        val view = open(3)
        val (fx, fy) = centre(view, tiles(view).single())
        touch(view, MotionEvent.ACTION_DOWN, fx, fy)
        touch(view, MotionEvent.ACTION_UP, fx, fy)
        layout(view)
        view.findViewById<View>(R.id.agent_shelf_title).performClick()
        val keys = view.findViewById<ViewGroup>(R.id.agent_char_art_keys)
        assertEquals(View.VISIBLE, keys.visibility)
        val delete = findText(keys, "⌫")!!
        repeat(20) { delete.performClick() }
        listOf("u", "r", "l").forEach { findText(keys, it)!!.performClick() }
        view.findViewById<View>(R.id.agent_shelf_name_done).performClick()
        val folder = Shelf.state.folders.single()
        assertEquals("url", folder.name)
        assertFalse(folder.auto) // the user's now: new screenshots start fresh
        assertTrue(inserted.isEmpty())
    }

    private fun findText(group: ViewGroup, text: String): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is android.widget.TextView && child.text.toString() == text) return child
            if (child is ViewGroup) findText(child, text)?.let { return it }
        }
        return null
    }

    @Test fun pinnedFolderProtectsItsContentAgainstTheRetention() {
        latinIME.prefs().edit().putInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, 10).commit()
        load(field)
        Shelf.clock = { now }
        open(4)
        val auto = Shelf.state.autoFolder!!.id
        Shelf.apply(ScreenshotShelf.togglePinFolder(Shelf.state, auto))
        Shelf.clock = { now + 60 * 60_000L }
        Shelf.prepare(latinIME, Settings.getValues())
        assertEquals(4, Shelf.state.folder(auto)!!.members.size)
    }
}
