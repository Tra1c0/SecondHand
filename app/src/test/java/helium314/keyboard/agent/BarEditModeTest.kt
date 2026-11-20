// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.DragEvent
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
 * Stage 86: customizing the bar inside the keyboard. The drags are simulated: a view without a window cannot start a
 * system drag, so the tests replace the starter of the strip, take the drag state it is handed and send
 * `DragEvent`s to the bar and to the grid, as the system would. No real finger has touched any of this.
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
        var dragState: Any? = null
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

    private fun setBar(keys: List<ToolbarKey>) { prefs.edit { putString(AgentPreferences.BAR_TOOLS, BarTools.format(keys)) } }
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
        strip.dragStarter = { _, _, _, state, _ -> h.dragState = state; true }
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

    /** A drag event as the system builds it, found by its parameter types (the signature differs between Android versions). */
    private fun dragEvent(action: Int, x: Float, y: Float, state: Any?): DragEvent {
        val method = DragEvent::class.java.declaredMethods.filter { it.name == "obtain" && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .maxByOrNull { it.parameterCount }!!
        var firstInt = true
        var floats = 0
        val args = method.parameterTypes.map { type ->
            when (type) {
                Int::class.javaPrimitiveType -> if (firstInt) { firstInt = false; action } else 0
                Float::class.javaPrimitiveType -> when (floats++) { 0 -> x; 1 -> y; else -> 0f }
                Boolean::class.javaPrimitiveType -> false
                Any::class.java -> state
                ClipDescription::class.java -> ClipDescription("tool", arrayOf("text/plain"))
                ClipData::class.java -> ClipData.newPlainText("tool", "x")
                else -> null
            }
        }
        method.isAccessible = true
        return method.invoke(null, *args.toTypedArray()) as DragEvent
    }

    /** The grid's drop area: the content of its scroll view. */
    private fun gridDropArea(h: Harness): View = (h.grid.getChildAt(0) as ViewGroup).getChildAt(0)

    private fun send(view: View, action: Int, x: Float, state: Any?) = view.dispatchDragEvent(dragEvent(action, x, 20f, state))

    /** A position of the root, as the bar (whose events are relative to its own container) gets it. */
    private fun Harness.bx(rootX: Float) = rootX - (container(this).centreIn(root) - container(this).width / 2f)
    private fun Harness.toBar(action: Int, rootX: Float, state: Any?) = send(container(this), action, bx(rootX), state)

    private fun startEdit(h: Harness) {
        leftKey(h).performClick()
        assertTrue(h.grid.isOpen)
        h.grid.findViewById<View>(R.id.agent_tool_grid_edit).performClick()
        layout(h)
        assertNotNull(h.grid.editListener)
    }

    /** Takes [key] from the grid in the hand: the pill is held (long click), the strip starts the drag. */
    private fun holdPill(h: Harness, key: ToolbarKey): Any {
        h.dragState = null
        assertTrue(h.grid.pill(key)!!.performLongClick())
        return h.dragState!!
    }

    private fun holdSlot(h: Harness, key: ToolbarKey): Any {
        h.dragState = null
        assertTrue(slots(h).first { it.tag == key }.performLongClick())
        return h.dragState!!
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
        assertNull(saved())

        // the check button ends the mode and closes the grid
        leftKey(h).performClick()
        assertFalse(h.grid.isOpen)
        assertNull(h.grid.editListener)
        assertEquals(listOf(UNDO, REDO, COPY, DICTATE), tags(h))
        slots(h).forEach { assertFalse(it.background is GradientDrawable, "outline of ${it.tag} gone") }
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools), leftKey(h).contentDescription)
        assertNull(saved()) // no edit, nothing stored
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

    // ---- drag and drop ----

    @Test fun aToolDraggedFromTheGridIntoTheBarMakesRoomAtOnceAndIsSavedOnTheDrop() {
        setBar(listOf(UNDO, REDO, COPY, CUT))
        reload()
        val h = build()
        startEdit(h)
        val before = centres(h)
        val bar = container(h)
        val state = holdPill(h, PASTE)
        assertTrue(h.toBar(DragEvent.ACTION_DRAG_STARTED, 0f, state))

        // over the second slot: the paste tool is between undo and redo, the others have moved over, nothing is saved yet
        h.toBar(DragEvent.ACTION_DRAG_ENTERED, before[1] - 1f, state) // just left of the centre of slot 1: inserted before it
        h.toBar(DragEvent.ACTION_DRAG_LOCATION, before[1] - 1f, state)
        layout(h)
        assertEquals(listOf(UNDO, PASTE, REDO, COPY, CUT), tags(h))
        assertEquals(0.45f, slots(h)[1].alpha) // the ghost of the dragged tool
        assertTrue(centres(h).size == 5 && centres(h)[0] < before[0] + 1000f)
        assertNotEquals(before.take(4), centres(h).take(4), "the others moved over")
        assertNull(saved())
        assertTrue(h.grid.pill(PASTE)!!.alpha < 1f) // already counted as in the bar

        // moving to the far right slot puts it last
        h.toBar(DragEvent.ACTION_DRAG_LOCATION, centres(h).last() + 30f, state)
        layout(h)
        assertEquals(listOf(UNDO, REDO, COPY, CUT, PASTE), tags(h))

        // the drop saves, exactly once, and the strip shows the saved bar
        assertTrue(h.toBar(DragEvent.ACTION_DROP, centres(h).last(), state))
        assertTrue(h.toBar(DragEvent.ACTION_DRAG_ENDED, 0f, state))
        layout(h)
        assertEquals(listOf(UNDO, REDO, COPY, CUT, PASTE), saved())
        assertEquals(listOf(UNDO, REDO, COPY, CUT, PASTE), tags(h))
        assertEquals(1f, slots(h).first { it.tag == PASTE }.alpha) // no ghost any more
        // five tools: still full size; a sixth makes the row compact (positions follow the count only)
        val hold = holdPill(h, SELECT_ALL)
        h.toBar(DragEvent.ACTION_DRAG_LOCATION, centres(h).first() - 10f, hold)
        h.toBar(DragEvent.ACTION_DROP, centres(h).first() - 10f, hold)
        layout(h)
        assertEquals(listOf(SELECT_ALL, UNDO, REDO, COPY, CUT, PASTE), saved())
        assertTrue(slots(h).all { it.width == Math.round(30f * 3.25f) })
        assertEquals(BarTools.read(prefs), tags(h))
    }

    @Test fun aToolDraggedWithinTheBarIsMovedAndAToolDraggedOutIsRemoved() {
        setBar(listOf(UNDO, REDO, COPY, CUT))
        reload()
        val h = build()
        startEdit(h)
        val bar = container(h)

        // undo to the last place
        var state = holdSlot(h, UNDO)
        h.toBar(DragEvent.ACTION_DRAG_STARTED, 0f, state)
        assertEquals(0.45f, slots(h).first { it.tag == UNDO }.alpha, 0.001f) // the dragged slot is shown as a ghost
        h.toBar(DragEvent.ACTION_DRAG_LOCATION, centres(h).last(), state)
        layout(h)
        assertEquals(listOf(REDO, COPY, CUT, UNDO), tags(h))
        assertNull(saved())
        h.toBar(DragEvent.ACTION_DROP, centres(h).last(), state)
        h.toBar(DragEvent.ACTION_DRAG_ENDED, 0f, state)
        assertEquals(listOf(REDO, COPY, CUT, UNDO), saved())

        // copy out of the bar onto the grid: it leaves the bar while the finger is outside, the drop saves
        state = holdSlot(h, COPY)
        h.toBar(DragEvent.ACTION_DRAG_STARTED, 0f, state)
        h.toBar(DragEvent.ACTION_DRAG_EXITED, 0f, state)
        layout(h)
        assertEquals(listOf(REDO, CUT, UNDO), tags(h))
        assertEquals(listOf(REDO, COPY, CUT, UNDO), saved()) // not yet
        assertTrue(send(gridDropArea(h), DragEvent.ACTION_DROP, 50f, state))
        send(gridDropArea(h), DragEvent.ACTION_DRAG_ENDED, 0f, state)
        layout(h)
        assertEquals(listOf(REDO, CUT, UNDO), saved())
        assertEquals(listOf(REDO, CUT, UNDO), tags(h))
        assertEquals(1f, h.grid.pill(COPY)!!.alpha) // the pill is not dimmed any more
    }

    @Test fun aDragThatEndsWithoutADropSavesNothingAndTheBarIsAsBefore() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        startEdit(h)
        val bar = container(h)
        val before = centres(h)
        // from the grid, over the bar, then the finger lets go somewhere else (the drag ends without a drop)
        var state = holdPill(h, PASTE)
        h.toBar(DragEvent.ACTION_DRAG_STARTED, 0f, state)
        h.toBar(DragEvent.ACTION_DRAG_LOCATION, before[0], state)
        layout(h)
        assertEquals(4, slots(h).size)
        h.toBar(DragEvent.ACTION_DRAG_ENDED, 0f, state)
        layout(h)
        assertEquals(listOf(UNDO, REDO, COPY), tags(h))
        assertEquals(before, centres(h))
        assertNull(saved())
        // out of the bar and gone
        state = holdSlot(h, REDO)
        h.toBar(DragEvent.ACTION_DRAG_STARTED, 0f, state)
        h.toBar(DragEvent.ACTION_DRAG_EXITED, 0f, state)
        assertEquals(listOf(UNDO, COPY), tags(h))
        h.toBar(DragEvent.ACTION_DRAG_ENDED, 0f, state)
        assertEquals(listOf(UNDO, REDO, COPY), tags(h))
        assertNull(saved())
    }

    @Test fun theKeyboardClosedMidDragLeavesTheSavedBarAndNoEditMode() {
        setBar(listOf(UNDO, REDO, COPY))
        reload()
        val h = build()
        startEdit(h)
        val state = holdPill(h, PASTE)
        h.toBar(DragEvent.ACTION_DRAG_STARTED, 0f, state)
        h.toBar(DragEvent.ACTION_DRAG_LOCATION, centres(h)[0], state)
        assertEquals(4, slots(h).size)
        h.strip.onKeyboardHidden()
        assertFalse(h.grid.isOpen)
        assertNull(h.grid.editListener)
        assertEquals(listOf(UNDO, REDO, COPY), tags(h))
        assertEquals(listOf(UNDO, REDO, COPY), saved())
        // a late drop (the system sends it, the strip does not listen any more): nothing happens
        h.toBar(DragEvent.ACTION_DROP, centres(h)[0], state)
        assertEquals(listOf(UNDO, REDO, COPY), saved())
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools), leftKey(h).contentDescription)
    }

    @Test fun whatWasDroppedIsStillThereAfterTheKeyboardWasHiddenAndShownAndInANewStrip() {
        setBar(listOf(UNDO, REDO))
        reload()
        val h = build()
        startEdit(h)
        val state = holdPill(h, EMOJI)
        h.toBar(DragEvent.ACTION_DROP, centres(h).last() + 20f, state)
        h.toBar(DragEvent.ACTION_DRAG_ENDED, 0f, state)
        assertEquals(listOf(UNDO, REDO, EMOJI), saved())
        h.strip.onKeyboardHidden() // the keyboard goes away
        assertEquals(listOf(UNDO, REDO, EMOJI), tags(h))
        assertNull(h.grid.editListener) // no edit mode on the next opening
        reload()
        assertEquals(listOf(UNDO, REDO, EMOJI), tags(build())) // a new strip (new theme, new start) reads it
        // the settings dialog shows the same list (it reads the same preference)
        assertEquals(BarTools.format(listOf(UNDO, REDO, EMOJI)), BarTools.currentFormatted(prefs))
    }

    @Test fun aFullBarRefusesTheNinthToolAndSaysSo() {
        val eight = BarTools.offered.filter { it !in BarTools.MIC_KEYS }.take(BarTools.MAX)
        setBar(eight)
        reload()
        val h = build()
        startEdit(h)
        val extra = BarTools.offered.first { it !in eight && it !in BarTools.MIC_KEYS }
        assertEquals(BarTools.MAX, slots(h).size)
        val state = holdPill(h, extra)
        h.toBar(DragEvent.ACTION_DRAG_STARTED, 0f, state)
        h.toBar(DragEvent.ACTION_DRAG_LOCATION, centres(h)[2], state)
        assertEquals(eight, tags(h)) // no room: no ghost, nothing moves
        assertEquals(latinIME.getString(R.string.agent_grid_full), h.grid.hint!!.text.toString())
        h.toBar(DragEvent.ACTION_DROP, centres(h)[2], state)
        assertEquals(eight, saved())
        // the tap alternative says the same, and after taking one out there is room
        h.grid.pill(extra)!!.performClick()
        assertEquals(eight, saved())
        slots(h).first().performClick() // takes the first tool out
        assertEquals(eight.drop(1), saved())
        assertEquals("Hold and drag to customize", h.grid.hint!!.text.toString())
        h.grid.pill(extra)!!.performClick()
        assertEquals(eight.drop(1) + extra, saved())
        assertEquals(BarTools.MAX, saved()!!.size)
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
        assertNull(saved())
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
        assertFalse(slots(h).first { it.tag == AGENT }.isEnabled) // dimmed
        startEdit(h) // the edit mode does not read or write anything of the field
        assertTrue(slots(h).first { it.tag == AGENT }.isEnabled) // customizing: a dimmed tool can still be taken out
        assertTrue(slots(h).first { it.tag == AGENT }.alpha < 0.5f) // but still looks dimmed
        slots(h).first { it.tag == AGENT }.performClick()
        assertEquals(listOf(UNDO, DICTATE), saved())
        h.grid.pill(SKILL_1)!!.performClick() // a protected tool can be placed too ...
        assertEquals(listOf(UNDO, DICTATE, SKILL_1), saved())
        assertEquals(emptyList(), h.codes) // ... and nothing was run
        leftKey(h).performClick()
        // outside the edit mode the dimmed slots are dimmed again and do nothing
        assertFalse(slots(h).first { it.tag == SKILL_1 }.isEnabled)
        slots(h).first { it.tag == SKILL_1 }.performClick()
        assertEquals(emptyList(), h.codes)
    }

    private fun words(vararg word: String) = SuggestedWords(ArrayList(word.map { SuggestedWordInfo(it, "", 100, SuggestedWordInfo.KIND_CORRECTION, null, 0, 0) }),
        null, null, false, false, false, SuggestedWords.INPUT_STYLE_PREDICTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER)

    private companion object {
        const val WIDTH = 1200
        const val STRIP = 130
        const val KEYBOARD = 793
    }
}
