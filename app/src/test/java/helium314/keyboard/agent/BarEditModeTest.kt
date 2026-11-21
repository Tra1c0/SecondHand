// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.os.SystemClock
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import helium314.keyboard.latin.suggestions.BarDrag
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.FixedBar
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarKey.*
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stages 86 and 88: customizing the bar inside the keyboard. Since stage 88 the drags are done with the keyboard's own touch
 * handling, and these tests send real `MotionEvent`s (DOWN, MOVE, UP, CANCEL, with event times and a real hold) to the
 * container of the edit mode; there is no `DragEvent` and no system drag anywhere. No finger on a device has touched any of this
 * (the emulator scenarios `bar_edit_*` of the device tests do that).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class BarEditModeTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
    private val prefs get() = latinIME.prefs()

    private class Harness(val root: FrameLayout, val strip: SuggestionStripView, val grid: ToolGridView, val keys: View,
                          val codes: MutableList<Int>) {
    }

    @Before fun clean() {
        prefs.edit().clear().commit()
        reload()
    }

    @After fun restoreDefaults() {
        prefs.edit().clear().commit()
        reload()
    }

    private fun reload(attributes: InputAttributes = InputAttributes(EditorInfo(), false, latinIME.packageName)) {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, attributes)
        KeyboardIconsSet.instance.loadIcons(context)
    }

    private var initialBar: List<ToolbarKey>? = null
    private fun setBar(keys: List<ToolbarKey>) { initialBar = keys; prefs.edit { putString(AgentPreferences.BAR_TOOLS, BarTools.format(keys)) } }
    /** Nothing was written since the bar was set up (or, without a set up bar, nothing at all). */
    private fun assertUnsaved() = assertEquals(initialBar, saved())
    private fun saved() = BarTools.parse(prefs.getString(AgentPreferences.BAR_TOOLS, null))

    private fun build(): Harness {
        val root = FrameLayout(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, root, false) as ViewGroup
        container.id = R.id.strip_container
        container.findViewById<View>(R.id.emoji_tab_strip).visibility = View.GONE
        container.findViewById<View>(R.id.clipboard_strip_scroll_view).visibility = View.GONE
        val keys = FrameLayout(context).apply { id = R.id.keyboard_view_wrapper }
        keys.addView(MainKeyboardView(context, null).apply { id = R.id.keyboard_view })
        val grid = ToolGridView(context, null).apply { id = R.id.agent_tool_grid }
        root.addView(container); root.addView(keys); root.addView(grid)
        val strip = container.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        val codes = mutableListOf<Int>()
        strip.setListener(object : SuggestionStripView.Listener {
            override fun pickSuggestionManually(word: SuggestedWordInfo?) = Unit
            override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) { codes += primaryCode }
            override fun removeSuggestion(word: String?) = Unit
            override fun removeExternalSuggestions() = Unit
            override fun onSwipeDownOnToolbar() = Unit
        }, root)
        val h = Harness(root, strip, grid, keys, codes)
        layout(h)
        return h
    }

    private fun layout(h: Harness) {
        h.root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(STRIP + KEYBOARD, View.MeasureSpec.EXACTLY))
        h.root.layout(0, 0, WIDTH, STRIP + KEYBOARD)
    }

    private fun toolbar(h: Harness) = h.strip.findViewById<ViewGroup>(R.id.toolbar)
    private fun container(h: Harness) = h.strip.findViewById<ViewGroup>(R.id.toolbar_container)
    private fun leftKey(h: Harness) = h.strip.findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private fun slots(h: Harness) = FixedBar.slots(toolbar(h))
    private fun tags(h: Harness) = slots(h).map { it.tag }
    private fun View.centreIn(root: View): Float = generateSequence(this) { it.parent as? View }.takeWhile { it !== root }.sumOf { it.left } + width / 2f
    private fun centres(h: Harness) = slots(h).map { it.centreIn(h.root) }

    // ---- real touches (stage 88): MotionEvents to the container of the edit mode, no DragEvent anywhere ----

    private var downTime = 0L
    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    private fun send(h: Harness, action: Int, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        return try { h.grid.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    private fun down(h: Harness, x: Float, y: Float) { downTime = SystemClock.uptimeMillis(); send(h, MotionEvent.ACTION_DOWN, x, y) }
    private fun moveTo(h: Harness, x: Float, y: Float, ms: Long = 16) { idle(ms); send(h, MotionEvent.ACTION_MOVE, x, y) }
    private fun up(h: Harness, x: Float, y: Float) { idle(16); send(h, MotionEvent.ACTION_UP, x, y); idle(400) }
    private fun cancel(h: Harness, x: Float, y: Float) { idle(16); send(h, MotionEvent.ACTION_CANCEL, x, y); idle(400) }

    /** The finger moves in steps (a real finger sends many events), from its place to (x, y). */
    private fun glide(h: Harness, from: Pair<Float, Float>, x: Float, y: Float, steps: Int = 6) {
        for (i in 1..steps) moveTo(h, from.first + (x - from.first) * i / steps, from.second + (y - from.second) * i / steps)
    }

    private fun assertUnsavedOrEqual(expected: List<ToolbarKey>) = assertTrue(saved() == expected || (saved() == null && BarTools.read(prefs) == expected))

    private val barY get() = STRIP / 2f
    private fun flat() = helium314.keyboard.keyboard.FlatStyle.isActive(Settings.getValues().mColors)
    /** Where [count] slots are after a drop, in the coordinates of the root. */
    private fun finalCentres(h: Harness, count: Int): List<Float> {
        val left = toolbar(h).let { t -> generateSequence<View>(t) { it.parent as? View }.takeWhile { it !== h.root }.sumOf { it.left } }
        return FixedBar.slotCentres(toolbar(h), flat(), count).map { it + left }
    }
    private fun pillPoint(h: Harness, key: ToolbarKey): Pair<Float, Float> {
        h.grid.revealPill(key)
        return h.grid.pillCentre(key)!!.let { it.x to it.y }
    }

    /** Holds the pill of [key] until the tool is in the hand; returns where the finger is. */
    private fun holdPill(h: Harness, key: ToolbarKey): Pair<Float, Float> {
        val at = pillPoint(h, key)
        down(h, at.first, at.second)
        idle(BarDrag.Config(slopPx = 1f).holdMs + 40)
        assertNotNull(h.grid.floatingCopy, "the tool is picked up after the hold")
        return at
    }

    /** Takes the tool of a slot in the hand with a short move (no hold needed on the bar). */
    private fun grabSlot(h: Harness, index: Int): Pair<Float, Float> {
        val x = centres(h)[index]
        down(h, x, barY)
        moveTo(h, x + 3f, barY)
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop + 20f
        moveTo(h, x + slop, barY) // beyond the touch slop
        assertNotNull(h.grid.floatingCopy)
        return (x + slop) to barY
    }

    private fun startEdit(h: Harness) {
        leftKey(h).performClick()
        assertTrue(h.grid.isOpen)
        h.grid.findViewById<View>(R.id.agent_tool_grid_edit).performClick()
        layout(h)
        assertNotNull(h.grid.editListener)
    }

    // ---- entering and leaving ----

    @Test fun theButtonInTheGridStartsTheEditModeWithTheHintTheOutlinesAndTheCheckButton() {
        setBar(listOf(UNDO, REDO, COPY, DICTATE))
        reload()
        val h = build()
        leftKey(h).performClick()
        // the standard grid has the entry, not the hint
        val edit = h.grid.findViewById<View>(R.id.agent_tool_grid_edit)
        assertNotNull(edit)
        assertEquals("Customize", (edit as android.widget.Button).text.toString())
        assertNull(h.grid.hint)
        assertNull(h.grid.editListener)
        edit.performClick()
        layout(h)

        assertNull(h.grid.findViewById<View>(R.id.agent_tool_grid_edit))
        assertEquals("Hold and drag to customize", h.grid.hint!!.text.toString())
        assertEquals(latinIME.getString(R.string.agent_bar_edit_done), leftKey(h).contentDescription) // the check button
        assertTrue(h.grid.isOpen)
        // every slot is outlined (the circle of the microphone is outline enough), none is dimmed
        slots(h).forEach {
            if (!FixedBar.isCircle(it)) assertTrue(it.background is GradientDrawable, "outline of ${it.tag}")
            assertEquals(1f, it.alpha)
        }
        // the tools that are in the bar are dimmed in the grid, the others are not
        listOf(UNDO, REDO, COPY, DICTATE).forEach { assertTrue(h.grid.pill(it)!!.alpha < 1f, it.name) }
        listOf(PASTE, CUT, AGENT).forEach { assertEquals(1f, h.grid.pill(it)!!.alpha, it.name) }
        // nothing was written by looking
        assertUnsaved()

        // the check button ends the mode and closes the grid
        leftKey(h).performClick()
        assertFalse(h.grid.isOpen)
        assertNull(h.grid.editListener)
        assertEquals(listOf(UNDO, REDO, COPY, DICTATE), tags(h))
        slots(h).forEach { assertFalse(it.background is GradientDrawable, "outline of ${it.tag} gone") }
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools), leftKey(h).contentDescription)
        assertUnsaved() // no edit, nothing stored
    }

    @Test fun theGermanTextsAreThere() {
        val german = latinIME.createConfigurationContext(Configuration(latinIME.resources.configuration).apply { setLocale(Locale.GERMAN) })
        assertEquals("Zum Personalisieren halten und ziehen", german.getString(R.string.agent_grid_edit_hint))
        assertEquals("Anpassen", german.getString(R.string.agent_grid_edit))
        assertEquals("Zur Leiste hinzufügen", german.getString(R.string.agent_bar_action_add))
        assertEquals("Aus der Leiste entfernen", german.getString(R.string.agent_bar_action_remove))
        assertEquals("Nach links", german.getString(R.string.agent_bar_action_left))
        assertEquals("Nach rechts", german.getString(R.string.agent_bar_action_right))
        assertEquals("Alle Werkzeuge", german.getString(R.string.agent_bar_all_tools))
        assertEquals("Werkzeuge zeigen", german.getString(R.string.agent_bar_show_tools))
        assertEquals("Anpassen beenden", german.getString(R.string.agent_bar_edit_done))
        assertEquals("Ablage", german.getString(R.string.prompts))
        assertEquals("Zeichen-Kunst", german.getString(R.string.char_art))
    }

    @Test fun aLongPressStartsTheEditModeFromASlotWithoutAnActionOfItsOwnAndFromTheGridKey() {
        setBar(listOf(AGENT, UNDO, SETTINGS))
        reload()
        val h = build()
        // undo has a long press action (redo): it keeps it and does not start the edit mode
        assertTrue(slots(h).first { it.tag == UNDO }.performLongClick())
        assertNull(h.grid.editListener)
        assertEquals(listOf(KeyCode.REDO), h.codes)
        assertFalse(h.grid.isOpen)
        // the text actions key has none: customizing starts, with the grid
        assertTrue(slots(h).first { it.tag == AGENT }.performLongClick())
        assertNotNull(h.grid.editListener)
        assertTrue(h.grid.isOpen)
        assertEquals(listOf(KeyCode.REDO), h.codes) // nothing was run
        leftKey(h).performClick()
        assertFalse(h.grid.isOpen)
        // the grid key held down does the same
        assertTrue(leftKey(h).performLongClick())
        assertNotNull(h.grid.editListener)
        // while words show, the back arrow has no edit mode
        leftKey(h).performClick()
        h.strip.setSuggestions(words("hallo"), false)
        assertTrue(leftKey(h).performLongClick())
        assertNull(h.grid.editListener)
    }

    // ---- dragging with real touches ----

    @Test fun aToolHeldInTheGridAndDraggedIntoTheBarMakesRoomAtOnceAndIsSavedOnTheDrop() {
        setBar(listOf(UNDO, REDO, COPY, CUT))
        reload()
        val h = build()
        startEdit(h)
        val final5 = finalCentres(h, 5)
        var at = holdPill(h, PASTE)
        // the copy of the tool is above everything and follows the finger
        assertTrue(h.grid.indexOfChild(h.grid.floatingCopy) == h.grid.childCount - 1)
        assertEquals(at.first, h.grid.floatingCopy!!.x + h.grid.floatingCopy!!.layoutParams.width / 2f, 1f)
        assertUnsaved()

        // over the gap between undo and redo: the paste tool takes the place, the others move over, nothing is saved yet
        glide(h, at, final5[1] + 3f, barY); at = final5[1] + 3f to barY
        layout(h)
        assertEquals(listOf(UNDO, PASTE, REDO, COPY, CUT), tags(h))
        assertEquals(0, slots(h)[1].imageAlpha) // the empty slot where it would land
        assertUnsaved()
        assertEquals(at.first, h.grid.floatingCopy!!.x + h.grid.floatingCopy!!.layoutParams.width / 2f, 1f)
        assertTrue(h.grid.pill(PASTE)!!.alpha < 1f)

        // along the bar: the last place
        glide(h, at, final5.last() + 20f, barY); at = final5.last() + 20f to barY
        layout(h)
        assertEquals(listOf(UNDO, REDO, COPY, CUT, PASTE), tags(h))
        // leaving the zone closes the gap
        glide(h, at, at.first, 600f); at = at.first to 600f
        layout(h)
        assertEquals(listOf(UNDO, REDO, COPY, CUT), tags(h))
        assertUnsaved()
        // and back, onto the gap before the first tool, then lift
        glide(h, at, final5[0] - 20f, barY); at = final5[0] - 20f to barY
        layout(h)
        assertEquals(listOf(PASTE, UNDO, REDO, COPY, CUT), tags(h))
        up(h, at.first, at.second)
        layout(h)
        assertEquals(listOf(PASTE, UNDO, REDO, COPY, CUT), saved())
        assertEquals(listOf(PASTE, UNDO, REDO, COPY, CUT), tags(h))
        assertNull(h.grid.floatingCopy)
        assertEquals(1, 1)
        assertEquals(0.5f, h.grid.pill(PASTE)!!.alpha) // marked: it is in the bar now
        assertEquals(255, slots(h)[0].imageAlpha)
        assertEquals(emptyList(), h.codes) // arranging never runs a tool
    }

    @Test fun aToolDroppedBetweenTwoToolsIsInsertedThereAtEveryGapIncludingTheEnds() {
        for (gap in 0..4) {
            prefs.edit().clear().commit()
            setBar(listOf(UNDO, REDO, COPY, CUT))
            reload()
            val h = build()
            startEdit(h)
            val final5 = finalCentres(h, 5)
            val at = holdPill(h, PASTE)
            glide(h, at, final5[gap], barY)
            up(h, final5[gap], barY)
            val expected = listOf(UNDO, REDO, COPY, CUT).toMutableList().also { it.add(gap, PASTE) }
            assertEquals(expected, saved(), "gap $gap")
        }
    }

    @Test fun aToolDraggedWithinTheBarIsMovedAndAToolDraggedOutIsRemoved() {
        setBar(listOf(UNDO, REDO, COPY, CUT))
        reload()
        val h = build()
        startEdit(h)

        // the first tool to the last place
        var at = grabSlot(h, 0)
        assertEquals(0, slots(h)[0].imageAlpha) // its slot is the gap while it is in the hand
        val last = finalCentres(h, 4).last()
        glide(h, at, last, barY)
        layout(h)
        assertEquals(listOf(REDO, COPY, CUT, UNDO), tags(h))
        assertUnsaved()
        up(h, last, barY)
        layout(h)
        assertEquals(listOf(REDO, COPY, CUT, UNDO), saved())
        assertEquals(listOf(REDO, COPY, CUT, UNDO), tags(h))

        // a tool dragged down into the grid is gone from the bar, saved on the lift
        at = grabSlot(h, 1)
        glide(h, at, at.first, 550f)
        layout(h)
        assertEquals(listOf(REDO, CUT, UNDO), tags(h)) // the others have closed up
        assertEquals(listOf(REDO, COPY, CUT, UNDO), saved()) // not yet
        up(h, at.first, 550f)
        layout(h)
        assertEquals(listOf(REDO, CUT, UNDO), saved())
        assertEquals(listOf(REDO, CUT, UNDO), tags(h))
        assertEquals(1f, h.grid.pill(COPY)!!.alpha) // its pill is not marked any more
        assertNull(h.grid.floatingCopy)
    }

    @Test fun aCancelledTouchGivesBackTheStateBeforeTheDragAndSavesNothing() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        startEdit(h)
        val before = centres(h)
        val final4 = finalCentres(h, 4)
        var at = holdPill(h, PASTE)
        glide(h, at, final4[1], barY); at = final4[1] to barY
        layout(h)
        assertEquals(4, slots(h).size)
        cancel(h, at.first, at.second)
        layout(h)
        assertEquals(listOf(UNDO, REDO, COPY), tags(h))
        assertEquals(before, centres(h))
        assertUnsaved()
        assertNull(h.grid.floatingCopy)
        assertEquals(1f, h.grid.pill(PASTE)!!.alpha)
        // a bar tool in the hand, out of the bar, cancelled: back in the bar
        at = grabSlot(h, 1)
        glide(h, at, at.first, 550f)
        assertEquals(listOf(UNDO, COPY), tags(h))
        cancel(h, at.first, 550f)
        assertEquals(listOf(UNDO, REDO, COPY), tags(h))
        assertUnsaved()
    }

    @Test fun theKeyboardClosedMidDragLeavesTheSavedBarAndNoEditMode() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        startEdit(h)
        val at = holdPill(h, PASTE)
        glide(h, at, finalCentres(h, 4)[0], barY)
        layout(h)
        assertEquals(4, slots(h).size)
        h.strip.onKeyboardHidden()
        assertFalse(h.grid.isOpen)
        assertNull(h.grid.editListener)
        assertNull(h.grid.floatingCopy)
        assertEquals(listOf(UNDO, REDO, COPY), tags(h))
        assertEquals(listOf(UNDO, REDO, COPY), saved())
        // the rest of the touch arrives late: nothing happens
        send(h, MotionEvent.ACTION_UP, finalCentres(h, 3)[0], barY)
        assertEquals(listOf(UNDO, REDO, COPY), saved())
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools), leftKey(h).contentDescription)
    }

    @Test fun whatWasDroppedIsStillThereAfterTheKeyboardWasHiddenAndShownAndInANewStrip() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        val at = holdPill(h, EMOJI)
        val x = finalCentres(h, 3).last()
        glide(h, at, x, barY)
        up(h, x, barY)
        assertEquals(listOf(UNDO, REDO, EMOJI), saved())
        h.strip.onKeyboardHidden() // the keyboard goes away
        assertEquals(listOf(UNDO, REDO, EMOJI), tags(h))
        assertNull(h.grid.editListener) // no edit mode on the next opening
        reload()
        assertEquals(listOf(UNDO, REDO, EMOJI), tags(build())) // a new strip (new theme, new start) reads it
        assertEquals(BarTools.format(listOf(UNDO, REDO, EMOJI)), BarTools.currentFormatted(prefs))
    }

    @Test fun aFullBarRefusesTheNinthToolShakesAndSaysSo() {
        val eight = BarTools.offered.filter { it !in BarTools.MIC_KEYS }.take(BarTools.MAX)
        setBar(eight)
        reload()
        val h = build()
        startEdit(h)
        val extra = BarTools.offered.first { it !in eight && it !in BarTools.MIC_KEYS }
        assertEquals(BarTools.MAX, slots(h).size)
        val at = holdPill(h, extra)
        val x = centres(h)[2]
        glide(h, at, x, barY)
        assertEquals(eight, tags(h)) // no room: no gap, nothing moves
        assertEquals(latinIME.getString(R.string.agent_bar_dropped_full), h.grid.hint!!.text.toString())
        up(h, x, barY)
        assertEquals(eight, saved())
        assertEquals(eight, tags(h))
        // a tool of the full bar can still be moved
        val grab = grabSlot(h, 0)
        glide(h, grab, finalCentres(h, 8).last(), barY)
        up(h, finalCentres(h, 8).last(), barY)
        assertEquals(eight.drop(1) + eight.first(), saved())
        // the tap alternative says the same, and after taking one out there is room
        h.grid.pill(extra)!!.performClick()
        assertEquals(eight.drop(1) + eight.first(), saved())
        slots(h).first().performClick()
        assertEquals(eight.drop(2) + eight.first(), saved())
        h.grid.pill(extra)!!.performClick()
        assertEquals(BarTools.MAX, saved()!!.size)
    }

    @Test fun anEmptyBarTakesTheFirstTool() {
        setBar(emptyList())
        reload()
        val h = build()
        startEdit(h)
        assertEquals(0, slots(h).size)
        val at = holdPill(h, UNDO)
        val x = (finalCentres(h, 1)[0])
        glide(h, at, x, barY)
        up(h, x, barY)
        assertEquals(listOf(UNDO), saved())
    }

    @Test fun aSecondFingerDuringTheDragIsIgnored() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        startEdit(h)
        val final4 = finalCentres(h, 4)
        val at = holdPill(h, PASTE)
        glide(h, at, final4[1], barY)
        layout(h)
        assertEquals(listOf(UNDO, PASTE, REDO, COPY), tags(h))
        // a second pointer goes down far away and moves: the drag does not follow it
        fun two(action: Int, index: Int, x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
                MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER })
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = x0; y = y0 }, MotionEvent.PointerCoords().apply { x = x1; y = y1 })
            val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            return try { h.grid.dispatchTouchEvent(e) } finally { e.recycle() }
        }
        idle(16)
        two(MotionEvent.ACTION_POINTER_DOWN, 1, final4[1], barY, final4[3], barY)
        idle(16)
        two(MotionEvent.ACTION_MOVE, 0, final4[1], barY, final4[3] + 40f, barY) // only the second finger moves
        layout(h)
        assertEquals(listOf(UNDO, PASTE, REDO, COPY), tags(h))
        idle(16)
        two(MotionEvent.ACTION_POINTER_UP, 1, final4[1], barY, final4[3], barY)
        assertNotNull(h.grid.floatingCopy) // the drag goes on with the first finger
        up(h, final4[1], barY)
        assertEquals(listOf(UNDO, PASTE, REDO, COPY), saved())
    }

    @Test fun aVerticalMoveOnTheGridBeforeTheHoldScrollsAndNeverStartsADrag() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        val at = pillPoint(h, COPY)
        down(h, at.first, at.second)
        moveTo(h, at.first, at.second - 60f, 10)
        moveTo(h, at.first, at.second - 120f, 10)
        assertNull(h.grid.floatingCopy)
        idle(400) // the hold time passes with the finger in the scroll
        assertNull(h.grid.floatingCopy)
        up(h, at.first, at.second - 120f)
        assertUnsaved()
        assertEquals(listOf(UNDO, REDO), tags(h))
    }

    /** A real scroll: the finger goes up the grid (the panel scrolls down), then lifts. */
    private fun scrollGrid(h: Harness, dy: Float = 180f) {
        val at = pillPoint(h, COPY)
        down(h, at.first, at.second)
        glide(h, at, at.first, at.second - dy, steps = 8)
        up(h, at.first, at.second - dy)
    }

    @Test fun afterScrollingTheGridAPillCanStillBeDraggedIntoTheBarAndThisRepeats() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        var expected = listOf(UNDO, REDO)
        for (key in listOf(PASTE, CUT, SELECT_ALL)) {
            scrollGrid(h)
            assertUnsavedOrEqual(expected)
            val at = holdPill(h, key)
            val x = finalCentres(h, expected.size + 1).last()
            glide(h, at, x, barY)
            up(h, x, barY)
            expected = expected + key
            assertEquals(expected, saved(), "after scrolling, $key dragged in")
            layout(h)
        }
    }

    @Test fun afterScrollingTheGridASlotCanStillBeReordered() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        startEdit(h)
        for (round in 1..2) {
            scrollGrid(h)
            val grab = grabSlot(h, 0)
            val x = finalCentres(h, 3).last()
            glide(h, grab, x, barY)
            up(h, x, barY)
            layout(h)
        }
        assertEquals(listOf(COPY, UNDO, REDO), saved()) // twice the first to the end: UNDO,REDO,COPY -> REDO,COPY,UNDO -> COPY,UNDO,REDO
    }

    @Test fun aScrollThatTheContainerNeverSeesTheEndOfDoesNotBlockTheNextTouch() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        // the gesture goes to the scroll view: the container gets the moves up to the first and then nothing (the scroll view asks not to be intercepted)
        val at = pillPoint(h, COPY)
        down(h, at.first, at.second)
        moveTo(h, at.first, at.second - 120f)
        idle(600) // the hold time passes while the panel scrolls: no pick-up
        assertNull(h.grid.floatingCopy)
        // no UP, no CANCEL ever arrives; the next finger starts
        val next = pillPoint(h, PASTE)
        down(h, next.first, next.second)
        idle(250)
        assertNotNull(h.grid.floatingCopy, "a new touch is never blocked by an old one")
        val x = finalCentres(h, 3).last()
        glide(h, next, x, barY)
        up(h, x, barY)
        assertEquals(listOf(UNDO, REDO, PASTE), saved())
    }

    private fun fling(h: Harness) = (h.grid.getChildAt(0) as android.widget.ScrollView).fling(6000)

    @Test fun aTouchThatStopsAFlingPicksNothingUpAndTheNextDragWorks() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        val before = h.grid.pill(AGENT)!!.alpha
        // the panel is still running from a fling (in Robolectric the scroller does not end by itself: that is the case); a finger lands on a pill to stop it
        fling(h)
        val at = pillPoint(h, AGENT)
        fling(h)
        down(h, at.first, at.second)
        idle(400) // the hold time passes with the finger resting
        assertNull(h.grid.floatingCopy, "no tool is picked up by a finger that only stops the panel")
        assertEquals(before, h.grid.pill(AGENT)!!.alpha, "the pill is not dimmed")
        up(h, at.first, at.second)
        assertUnsaved()
        // the same while the finger holds and moves towards the bar: nothing hangs
        fling(h)
        val p = pillPoint(h, PASTE)
        fling(h)
        down(h, p.first, p.second)
        idle(250)
        assertNull(h.grid.floatingCopy)
        glide(h, p, finalCentres(h, 3).last(), barY)
        up(h, finalCentres(h, 3).last(), barY)
        assertUnsaved()
        // and an ordinary drag after it works (the scroller has been stopped by the test)
        (h.grid.getChildAt(0) as android.widget.ScrollView).let { it.scrollTo(0, 0) }
        val q = holdPill(h, CUT)
        val x = finalCentres(h, 3).last()
        glide(h, q, x, barY)
        up(h, x, barY)
        assertEquals(listOf(UNDO, REDO, CUT), saved())
    }

    @Test fun aSecondFingerOnAnotherPillDuringTheFirstHundredAndEightyMsDoesNothing() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        val a = pillPoint(h, AGENT)
        val b = pillPoint(h, DICTATE) // both in the first row: no scrolling between the two points
        var secondTouched = 0
        h.grid.pill(DICTATE)!!.setOnTouchListener { _, _ -> secondTouched++; false }
        assertFalse(h.grid.isMotionEventSplittingEnabled)
        down(h, a.first, a.second)
        fun two(action: Int, index: Int): Boolean {
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
                MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER })
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = a.first; y = a.second }, MotionEvent.PointerCoords().apply { x = b.first; y = b.second })
            val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            return try { h.grid.dispatchTouchEvent(e) } finally { e.recycle() }
        }
        idle(30)
        two(MotionEvent.ACTION_POINTER_DOWN, 1)
        idle(30)
        two(MotionEvent.ACTION_POINTER_UP, 1)
        idle(30)
        up(h, a.first, a.second)
        // only the first finger acted: a tap on its pill; the pill of the second finger never got a touch
        assertEquals(0, secondTouched)
        assertEquals(listOf(UNDO, REDO, AGENT), saved())
    }

    @Test fun aShortTouchOnAPillIsATapThatAppendsAndOnASlotRemoves() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        val at = pillPoint(h, PASTE)
        down(h, at.first, at.second)
        idle(40)
        up(h, at.first, at.second)
        assertEquals(listOf(UNDO, REDO, PASTE), saved())
        layout(h)
        val x = centres(h)[0]
        down(h, x, barY)
        idle(40)
        up(h, x, barY)
        assertEquals(listOf(REDO, PASTE), saved())
        assertEquals(emptyList(), h.codes)
    }

    @Test fun inARightToLeftBarThePlacesFollowTheVisualOrder() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        h.strip.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout(h)
        startEdit(h)
        layout(h)
        // the first tool of the list is at the right
        assertTrue(centres(h)[0] > centres(h)[1] && centres(h)[1] > centres(h)[2])
        val at = holdPill(h, PASTE)
        val farLeft = 30f
        glide(h, at, farLeft, barY)
        layout(h)
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), tags(h)) // the visual left end is the end of the list
        up(h, farLeft, barY)
        assertEquals(listOf(UNDO, REDO, COPY, PASTE), saved())
        layout(h)
        assertTrue(centres(h)[3] < centres(h)[0])
        // a tool dragged to the far right is first
        val grab = grabSlot(h, 3)
        glide(h, grab, WIDTH - 40f, barY)
        up(h, WIDTH - 40f, barY)
        assertEquals(listOf(PASTE, UNDO, REDO, COPY), saved())
    }

    @Test fun aHeldPillThatIsLiftedWithoutMovingChangesNothing() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        val at = holdPill(h, PASTE)
        up(h, at.first, at.second) // picked up by the hold, put back: not a tap, nothing added
        assertUnsaved()
        assertNull(h.grid.floatingCopy)
        assertEquals(listOf(UNDO, REDO), tags(h))
    }

    @Test fun theEditContainerStillLetsTheLeftKeyAndTheStripThrough() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        // a touch on the left key's place is not taken by the container (it is the check button)
        val consumed = send(h, MotionEvent.ACTION_DOWN, 20f, barY)
        send(h, MotionEvent.ACTION_UP, 20f, barY)
        assertFalse(consumed)
    }

    // ---- without dragging ----

    @Test fun aTapOnAPillAddsTheToolAndATapOnASlotOrAnInstalledPillTakesItOut() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        h.grid.pill(PASTE)!!.performClick()
        assertEquals(listOf(UNDO, REDO, PASTE), saved())
        assertEquals(listOf(UNDO, REDO, PASTE), tags(h))
        assertTrue(h.grid.isOpen) // a tap in the edit mode does not run the tool and does not close the grid
        assertEquals(emptyList(), h.codes)
        slots(h).first { it.tag == REDO }.performClick() // a tap on a slot takes it out and does not run it
        assertEquals(listOf(UNDO, PASTE), saved())
        assertEquals(emptyList(), h.codes)
        h.grid.pill(UNDO)!!.performClick() // an installed pill: out again
        assertEquals(listOf(PASTE), saved())
        assertEquals(listOf(PASTE), tags(h))
        h.grid.pill(PASTE)!!.performClick()
        assertEquals(emptyList(), saved()) // an empty bar is a choice
        assertEquals(emptyList(), tags(h))
        assertEquals(emptyList(), h.codes)
    }

    @Test fun screenReadersGetActionsForPillsAndSlots() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        startEdit(h)
        fun labels(view: View) = view.createAccessibilityNodeInfo().actionList.mapNotNull { it.label?.toString() }
        assertTrue("Add to bar" in labels(h.grid.pill(PASTE)!!))
        assertFalse("Remove from bar" in labels(h.grid.pill(PASTE)!!))
        assertTrue("Remove from bar" in labels(h.grid.pill(UNDO)!!))
        val first = labels(slots(h)[0]); val middle = labels(slots(h)[1]); val last = labels(slots(h)[2])
        assertTrue("Remove from bar" in first && "Move right" in first && "Move left" !in first)
        assertTrue(listOf("Remove from bar", "Move left", "Move right").all { it in middle })
        assertTrue("Move left" in last && "Move right" !in last)

        // the actions do what they say, and are saved
        val action = slots(h)[1].createAccessibilityNodeInfo().actionList.first { it.label?.toString() == "Move right" }
        assertTrue(slots(h)[1].performAccessibilityAction(action.id, null))
        assertEquals(listOf(UNDO, COPY, REDO), saved())
        val add = h.grid.pill(PASTE)!!.createAccessibilityNodeInfo().actionList.first { it.label?.toString() == "Add to bar" }
        assertTrue(h.grid.pill(PASTE)!!.performAccessibilityAction(add.id, null))
        assertEquals(listOf(UNDO, COPY, REDO, PASTE), saved())
        val remove = slots(h)[0].createAccessibilityNodeInfo().actionList.first { it.label?.toString() == "Remove from bar" }
        assertTrue(slots(h)[0].performAccessibilityAction(remove.id, null))
        assertEquals(listOf(COPY, REDO, PASTE), saved())
        // every pill and slot has a description
        h.grid.listed.forEach { assertTrue(!h.grid.pill(it)!!.contentDescription.isNullOrBlank(), it.name) }
        slots(h).forEach { assertTrue(!it.contentDescription.isNullOrBlank()) }
    }

    @Test fun theSettingsListAndTheEditModeEditTheSameStoredOrder() {
        // a user who never edited: the settings dialog shows the migration, an edit in the keyboard stores the list
        assertUnsaved()
        val before = BarTools.read(prefs)
        reload()
        val h = build()
        startEdit(h)
        h.grid.pill(PASTE)!!.performClick()
        assertEquals(before + PASTE, saved()) // appended to what the migration gave
        // the settings entry exists for the mode and reads the same key
        val keys = helium314.keyboard.settings.screens.createToolbarSettings(latinIME).map { it.key }
        assertTrue(AgentPreferences.BAR_TOOLS in keys && AgentPreferences.FIXED_BAR in keys)
        // an edit through the settings dialog (the same format) is what the keyboard shows next
        prefs.edit { putString(AgentPreferences.BAR_TOOLS, "REDO:true|UNDO:true|COPY:false") }
        reload()
        assertEquals(listOf(REDO, UNDO), tags(build()))
    }

    // ---- the fields ----

    @Test fun theEditModeWorksInAPasswordFieldWhereToolsAreDimmedAndRunsNothing() {
        setBar(listOf(AGENT, UNDO, DICTATE))
        reload(InputAttributes(EditorInfo().apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }, false, latinIME.packageName))
        assertTrue(Settings.getValues().mIncognitoModeEnabled)
        val h = build()
        assertNotNull(FixedBar.reasonOf(slots(h).first { it.tag == AGENT })) // off
        startEdit(h) // the edit mode does not read or write anything of the field
        assertNotNull(FixedBar.reasonOf(slots(h).first { it.tag == AGENT })) // customizing: it still looks off ...
        slots(h).first { it.tag == AGENT }.performClick()
        assertEquals(listOf(UNDO, DICTATE), saved())
        h.grid.pill(SKILL_1)!!.performClick() // a protected tool can be placed too ...
        assertEquals(listOf(UNDO, DICTATE, SKILL_1), saved())
        assertEquals(emptyList(), h.codes) // ... and nothing was run
        leftKey(h).performClick()
        // outside the edit mode the dimmed slots are dimmed again and do nothing
        assertNotNull(FixedBar.reasonOf(slots(h).first { it.tag == SKILL_1 }))
        slots(h).first { it.tag == SKILL_1 }.performClick() // ... and outside the edit mode a tap only says why
        assertEquals(emptyList(), h.codes)
    }

    // ---- review of stages 85/86 ----

    @Test fun theCloseCrossOfTheInlineAutofillNeverStartsTheEditMode() {
        val h = build()
        h.strip.setExternalSuggestionView(android.widget.TextView(context).apply { text = "Autofill" }, true)
        val suggestions = h.strip.findViewById<ViewGroup>(R.id.suggestions_strip)
        val close = (0 until suggestions.childCount).map { suggestions.getChildAt(it) }.first { it.tag == CLOSE_HISTORY }
        close.performLongClick()
        assertNull(h.grid.editListener)
        assertFalse(h.grid.isOpen)
        // a real slot still does it
        h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false)
        assertTrue(slots(h).first { it.tag == AGENT }.performLongClick())
        assertNotNull(h.grid.editListener)
    }

    @Test fun aHiddenVoiceKeyInTheListDoesNotShiftTheDropPlace() {
        setBar(listOf(UNDO, VOICE, REDO, COPY)) // no voice input here: the voice key has no slot
        reload()
        assertFalse(Settings.getValues().mShowsVoiceInputKey)
        val h = build()
        startEdit(h)
        assertEquals(listOf(UNDO, REDO, COPY), tags(h))
        val f = finalCentres(h, 4)
        // between the slots of redo and copy
        var at = holdPill(h, PASTE)
        glide(h, at, f[2], barY)
        layout(h)
        assertEquals(listOf(UNDO, REDO, PASTE, COPY), tags(h))
        up(h, f[2], barY)
        assertEquals(listOf(UNDO, VOICE, REDO, PASTE, COPY), saved()) // the list index is behind the hidden key
        layout(h)
        assertEquals(listOf(UNDO, REDO, PASTE, COPY), tags(h))
        // a move of the first tool behind the second visible one
        at = grabSlot(h, 0)
        val x = finalCentres(h, 4)[1]
        glide(h, at, x, barY)
        up(h, x, barY)
        assertEquals(listOf(VOICE, REDO, UNDO, PASTE, COPY), saved())
        layout(h)
        assertEquals(listOf(REDO, UNDO, PASTE, COPY), tags(h))
        // the screen reader's "move right" skips the hidden key too
        val moveRight = slots(h)[0].createAccessibilityNodeInfo().actionList.first { it.label?.toString() == "Move right" }
        assertTrue(slots(h)[0].performAccessibilityAction(moveRight.id, null))
        assertEquals(listOf(VOICE, UNDO, REDO, PASTE, COPY), saved())
    }

    @Test fun theDesignToolDoesNotOpenTheSettingsWhileTheDeviceIsLocked() {
        val keyguard = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        try {
            helium314.keyboard.agent.LockState.drop()
            org.robolectric.Shadows.shadowOf(keyguard).setIsDeviceLocked(true) // locked after the field started: asked live
            assertFalse(Settings.getValues().mIsLocked)
            latinIME.mKeyboardActionListener.onCodeInput(KeyCode.AGENT_DESIGN, -1, -1, false)
            assertNull(org.robolectric.Shadows.shadowOf(latinIME.application).nextStartedActivity)
            helium314.keyboard.agent.LockState.drop()
            org.robolectric.Shadows.shadowOf(keyguard).setIsDeviceLocked(false)
            helium314.keyboard.agent.LockState.clock = { System.nanoTime() / 1_000_000 + 10_000 }
            latinIME.mKeyboardActionListener.onCodeInput(KeyCode.AGENT_DESIGN, -1, -1, false)
            val intent = org.robolectric.Shadows.shadowOf(latinIME.application).nextStartedActivity
            assertEquals(helium314.keyboard.settings.SettingsDestination.AgentDesign, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        } finally {
            org.robolectric.Shadows.shadowOf(keyguard).setIsDeviceLocked(false)
            helium314.keyboard.agent.LockState.resetForTest()
        }
    }

    private fun words(vararg word: String) = SuggestedWords(ArrayList(word.map { SuggestedWordInfo(it, "", 100, SuggestedWordInfo.KIND_CORRECTION, null, 0, 0) }),
        null, null, false, false, false, SuggestedWords.INPUT_STYLE_PREDICTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER)

    private companion object {
        const val WIDTH = 1200
        const val STRIP = 130
        const val KEYBOARD = 793
    }
}
