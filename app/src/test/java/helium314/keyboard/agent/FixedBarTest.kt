// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import androidx.core.content.edit
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.isKeyPinned
import helium314.keyboard.latin.utils.pinKey
import helium314.keyboard.latin.utils.unpinKey
import helium314.keyboard.latin.suggestions.BarCounters
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
import helium314.keyboard.latin.utils.ToolbarMode
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
 * Stage 85: the fixed bar of the mode "toolbar keys and suggestions" in every style, at the size of the reference
 * screenshot (1200 px wide, 3.25 px per dp, strip 130 px): slot geometry, the state machine on the real strip, the
 * grid of all tools, the migration of the old toolbar preferences, the field gating and the other modes.
 * Nothing here has been seen on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FixedBarTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
    private val prefs get() = latinIME.prefs()

    private class Harness(val root: FrameLayout, val container: ViewGroup, val strip: SuggestionStripView, val grid: ToolGridView,
                          val keys: View, val codes: MutableList<Int>)

    @Before fun clean() {
        prefs.edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        reload()
    }

    @After fun restoreDefaults() {
        prefs.edit().clear().commit()
        reload()
    }

    private fun reload() {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        KeyboardIconsSet.instance.loadIcons(context)
    }

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
        return Harness(root, container, strip, grid, keys, codes).also { layout(it) }
    }

    private fun layout(h: Harness) {
        h.root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(STRIP + KEYBOARD, View.MeasureSpec.EXACTLY))
        h.root.layout(0, 0, WIDTH, STRIP + KEYBOARD)
    }

    private fun View.leftIn(root: View): Int = generateSequence(this) { it.parent as? View }.takeWhile { it !== root }.sumOf { it.left }
    private fun View.centreIn(root: View) = leftIn(root) + width / 2f

    private fun toolbar(h: Harness) = h.strip.findViewById<ViewGroup>(R.id.toolbar)
    private fun leftKey(h: Harness) = h.strip.findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private fun slots(h: Harness) = FixedBar.slots(toolbar(h))

    private fun setBar(keys: List<ToolbarKey>) { prefs.edit { putString(AgentPreferences.BAR_TOOLS, BarTools.format(keys)) } }

    private fun flatPreset() {
        FlatPreset(latinIME).apply()
        reload()
    }

    private fun words(vararg word: String) = SuggestedWords(ArrayList(word.map { SuggestedWordInfo(it, "", 100, SuggestedWordInfo.KIND_CORRECTION, null, 0, 0) }),
        null, null, false, false, false, SuggestedWords.INPUT_STYLE_PREDICTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER)

    private fun suggestionsVisible(h: Harness) = h.strip.findViewById<View>(R.id.suggestions_strip).visibility == View.VISIBLE
    private fun toolsVisible(h: Harness) = h.strip.findViewById<View>(R.id.toolbar_container).visibility == View.VISIBLE

    // ---- geometry ----

    private val sets = listOf(
        listOf(UNDO, REDO, COPY, PASTE, CUT, SELECT_ALL, SELECT_WORD, CLIPBOARD),
        listOf(AGENT, EMOJI, SKILL_1, SKILL_2, SKILL_3, PROMPTS, CHAR_ART, DESIGN),
    )

    private fun checkGeometry(flat: Boolean) {
        val patterns = mutableMapOf<Int, List<Float>>()
        for (count in listOf(0, 1, 2, 3, 4, 5, 6, 8)) {
            for ((variant, tools) in sets.withIndex()) {
                // the last tool is a microphone key in the second variant, to see that the circle does not move anything
                val bar = tools.take(count).let { if (variant == 1 && count > 0) it.dropLast(1) + DICTATE else it }
                setBar(bar)
                reload()
                val h = build()
                h.strip.setToolbarVisibility(true)
                layout(h)
                val label = "flat=$flat count=$count variant=$variant"
                val tools2 = slots(h)
                assertEquals(bar, tools2.map { it.tag }, label)
                val left = leftKey(h)
                val centres = (listOf(left) + tools2).map { it.centreIn(h.root) }
                // left key first, then the tools in order, evenly spread from the left margin to the right margin
                assertEquals(centres.sorted(), centres, label)
                centres.zipWithNext { a, b -> if (centres.size > 2) assertTrue(abs((b - a) - (centres[1] - centres[0])) <= 1.5f, "$label pitch ${b - a}") }
                if (count > 0) assertTrue(abs(centres.first() - (WIDTH - centres.last())) <= 2.5f, "$label symmetric ${centres.first()} / ${centres.last()}")
                // the same count gives the same places whichever tools are in it
                val previous = patterns.put(count, centres)
                if (previous != null) assertTrue(previous.zip(centres).all { (a, b) -> abs(a - b) <= 1f }, "$label equal places: $previous vs $centres")
                // no scrolling and no expand arrow in the strip
                assertNoScrolling(h.strip)
                assertNull(h.strip.findViewById<View>(R.id.pinned_keys)?.takeIf { (it as ViewGroup).childCount > 0 })
                // compact from six tools: smaller buttons
                tools2.forEach { assertEquals(tools2.first().width, it.width, label) }
                if (count > 0) {
                    val full = Math.round((if (flat) 34f else 40f) * 3.25f)
                    val compact = Math.round(30f * 3.25f)
                    assertEquals(if (BarTools.isCompact(count)) compact else full, tools2.first().width, label)
                }
                // the last microphone key is the circle, no other slot is
                assertEquals(variant == 1 && count > 0, tools2.lastOrNull()?.let { FixedBar.isCircle(it) } ?: false, label)
                assertEquals(0, tools2.count { FixedBar.isCircle(it) } - (if (variant == 1 && count > 0) 1 else 0), label)
                // every slot has a description; the left key says what it does
                tools2.forEach { assertTrue(!it.contentDescription.isNullOrBlank(), "$label ${it.tag}") }
                assertEquals(latinIME.getString(R.string.agent_bar_all_tools), left.contentDescription)
            }
        }
    }

    private fun assertNoScrolling(view: View) {
        assertFalse(view is HorizontalScrollView, "scroll view in the strip")
        if (view is ViewGroup) (0 until view.childCount).forEach { assertNoScrolling(view.getChildAt(it)) }
    }

    @Test fun slotsAreEvenlySpreadAndStayWhereTheyAreInTheDefaultLook() = checkGeometry(flat = false)

    @Test fun slotsAreEvenlySpreadAndStayWhereTheyAreInTheFlatLook() {
        flatPreset()
        checkGeometry(flat = true)
    }

    @Test fun theDefaultModeIsTheFixedBarWithoutAnExpandArrow() {
        assertEquals(ToolbarMode.EXPANDABLE, Settings.getValues().mToolbarMode)
        val h = build()
        assertNoScrolling(h.strip)
        assertTrue(toolsVisible(h)) // nothing typed: the tools
        assertFalse(suggestionsVisible(h))
        val left = leftKey(h)
        assertEquals(View.VISIBLE, left.visibility)
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools), left.contentDescription)
        // a long press on a tool never pins it (there are no pinned keys): the strip's own long press path
        assertFalse(Settings.getValues().mQuickPinToolbarKeys)
    }

    // ---- state machine on the real strip ----

    @Test fun nothingTypedTheToolsTypingTheSuggestionsTheLeftKeyTheToolsAndTheNextKeyTheSuggestionsAgain() {
        val h = build()
        val left = leftKey(h)
        h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false)
        assertTrue(toolsVisible(h)); assertFalse(suggestionsVisible(h))

        h.strip.setSuggestions(words("hallo", "hallo!"), false) // typing
        layout(h)
        assertFalse(toolsVisible(h)); assertTrue(suggestionsVisible(h))
        assertEquals(latinIME.getString(R.string.agent_bar_show_tools), left.contentDescription)
        assertFalse(h.grid.isOpen)

        assertTrue(left.performClick()) // back to the tools; the grid does not open
        assertTrue(toolsVisible(h)); assertFalse(suggestionsVisible(h))
        assertFalse(h.grid.isOpen)
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools), left.contentDescription)

        h.strip.setSuggestions(words("halli"), false) // the next key
        assertFalse(toolsVisible(h)); assertTrue(suggestionsVisible(h))

        h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false) // the word is done
        assertTrue(toolsVisible(h)); assertFalse(suggestionsVisible(h))

        // hiding the keyboard starts the next time with the tools
        h.strip.setSuggestions(words("hallo"), false)
        h.strip.onKeyboardHidden()
        assertTrue(toolsVisible(h)); assertFalse(suggestionsVisible(h))
    }

    @Test fun anExternalViewReplacesTheToolsLikeWordsAndHasTheSameWayBack() {
        val h = build()
        val chip = android.widget.TextView(context).apply { text = "Chip" }
        h.strip.setExternalSuggestionView(chip, false)
        assertFalse(toolsVisible(h)); assertTrue(suggestionsVisible(h))
        assertTrue(h.strip.isExternalSuggestionVisible)
        assertTrue(leftKey(h).performClick())
        assertTrue(toolsVisible(h))
        // the old calls of LatinIME: "show the toolbar" forces the tools, "hide" shows the content when there is some
        h.strip.setToolbarVisibility(false)
        assertTrue(suggestionsVisible(h))
        h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false)
        h.strip.setToolbarVisibility(false) // nothing to show: the bar must not go blank
        assertTrue(toolsVisible(h))
        h.strip.setToolbarVisibility(true)
        assertTrue(toolsVisible(h))
    }

    // ---- the grid ----

    @Test fun theLeftKeyOpensAGridWithEveryToolAndATapRunsTheToolAfterClosing() {
        val h = build()
        val left = leftKey(h)
        val openIcon = left.drawable
        assertTrue(left.performClick())
        assertTrue(h.grid.isOpen)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, h.keys.importantForAccessibility)
        assertEquals(latinIME.getString(R.string.agent_bar_close_tools), left.contentDescription)
        assertTrue(left.drawable !== openIcon)
        assertTrue(toolsVisible(h)) // the tools stay in view while the grid is open
        layout(h)

        // every tool of the fork, except the voice key without a voice input, the data gathering key never used and the
        // clipboard clearing tool (one tap destroys the history, the vault and the clip) that the user never had in a list
        val expected = BarTools.gridOrder.filter { it != VOICE && it != BACKGROUND_GATHERING && it != CLEAR_CLIPBOARD }
        assertEquals(expected, h.grid.listed)
        assertTrue(expected.size >= 37)
        // two columns, scrollable, tools listed in rows of two
        val pills = expected.map { h.grid.pill(it)!! }
        pills.forEach { assertTrue(!it.contentDescription.isNullOrBlank() && it.text.isNotBlank(), "label of ${it.tag}") }
        val first = pills[0]; val second = pills[1]; val third = pills[2]
        assertTrue(second.leftIn(h.grid) > first.leftIn(h.grid) && third.leftIn(h.grid) == first.leftIn(h.grid))
        assertTrue(h.grid.getChildAt(0).let { it is android.widget.ScrollView && it.getChildAt(0).height > it.height })
        // the strip above stays free
        assertEquals(STRIP, (h.grid.getChildAt(0).layoutParams as FrameLayout.LayoutParams).topMargin)

        // a tap on a pill: the grid closes first, then the same code a bar key sends goes to the listener
        pills.first { it.tag == UNDO }.performClick()
        assertFalse(h.grid.isOpen)
        assertEquals(listOf(KeyCode.UNDO), h.codes)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, h.keys.importantForAccessibility)
        assertTrue(left.drawable === openIcon)
        // every tool runs with the code of its key (custom codes of the settings included)
        for (key in expected) {
            h.codes.clear()
            left.performClick()
            h.grid.pill(key)!!.performClick()
            assertEquals(listOf(helium314.keyboard.latin.utils.getCodeForToolbarKey(key)).filter { it != KeyCode.UNSPECIFIED }, h.codes, key.name)
        }
        // the cross closes it again, a bar key too
        left.performClick(); assertTrue(h.grid.isOpen)
        left.performClick(); assertFalse(h.grid.isOpen)
        left.performClick()
        slots(h).first().performClick()
        assertFalse(h.grid.isOpen)
    }

    @Test fun theGridOffersTheVoiceKeyOnlyWithAVoiceInput() {
        val h = build()
        // no voice input on the test device (the shortcut key is off)
        assertFalse(Settings.getValues().mShowsVoiceInputKey)
        leftKey(h).performClick()
        assertNull(h.grid.pill(VOICE))
        assertNotNull(h.grid.pill(DICTATE))
    }

    // ---- migration ----

    @Test fun aUserWhoNeverCustomisedKeepsWhatHeUsesAndNothingIsWritten() {
        assertFalse(prefs.contains(AgentPreferences.BAR_TOOLS))
        assertEquals(listOf(AGENT, SETTINGS, CLIPBOARD, UNDO, VOICE), BarTools.read(prefs)) // the first five of the old order, the voice key last
        val before = prefs.all.toMap()
        val h = build()
        assertEquals(listOf(AGENT, SETTINGS, CLIPBOARD, UNDO), slots(h).map { it.tag }) // the voice key is hidden without a voice input
        assertEquals(before, prefs.all.toMap()) // nothing was written by building the strip
        assertFalse(prefs.contains(AgentPreferences.BAR_TOOLS))
        // the rest of the old order is still there, in the grid
        leftKey(h).performClick()
        listOf(REDO, SELECT_WORD, COPY, PASTE, LEFT, RIGHT).forEach { assertNotNull(h.grid.pill(it), it.name) }
    }

    @Test fun aUserWhoCustomisedTheOrderBeforeKeepsThatOrder() {
        prefs.edit {
            putString(Settings.PREF_TOOLBAR_KEYS, "PASTE:true|COPY:true|UNDO:false|AGENT:true|SKILL_2:true|CUT:true|EMOJI:true|REDO:true|SETTINGS:false")
            putString(Settings.PREF_PINNED_TOOLBAR_KEYS, "SKILL_3:true|AGENT:false")
        }
        // first five enabled: PASTE, COPY, AGENT, SKILL_2, CUT; the pinned skill key was always visible, so it joins
        assertEquals(listOf(PASTE, COPY, AGENT, SKILL_2, CUT, SKILL_3), BarTools.read(prefs))
        reload()
        assertEquals(listOf(PASTE, COPY, AGENT, SKILL_2, CUT, SKILL_3), slots(build()).map { it.tag })
        assertFalse(prefs.contains(AgentPreferences.BAR_TOOLS))
    }

    @Test fun theFlatPresetKeepsItsThreeToolsAndTheDictationCircle() {
        flatPreset()
        assertEquals(listOf(CLIPBOARD, EMOJI, AGENT, DICTATE), BarTools.read(prefs))
        val h = build()
        assertEquals(listOf(CLIPBOARD, EMOJI, AGENT, DICTATE), slots(h).map { it.tag })
        assertTrue(FixedBar.isCircle(slots(h).last()))
        // since the review the preset stores its bar (and its backup restores the previous one), see the preset test below
        assertEquals(listOf(CLIPBOARD, EMOJI, AGENT, DICTATE), BarTools.parse(prefs.getString(AgentPreferences.BAR_TOOLS, null)))
    }

    @Test fun aStoredChoiceIsNeverOverwrittenByTheMigration() {
        setBar(listOf(UNDO, REDO))
        prefs.edit { putString(Settings.PREF_TOOLBAR_KEYS, "AGENT:true|UNDO:true") }
        val stored = prefs.getString(AgentPreferences.BAR_TOOLS, null)
        reload()
        assertEquals(listOf(UNDO, REDO), BarTools.read(prefs))
        assertEquals(listOf(UNDO, REDO), slots(build()).map { it.tag })
        assertEquals(stored, prefs.getString(AgentPreferences.BAR_TOOLS, null))
        // an empty choice is a choice too: no tools, not the migration
        setBar(emptyList())
        assertEquals(emptyList(), BarTools.read(prefs))
    }

    // ---- gating ----

    @Test fun toolsThatAFieldDoesNotAllowStayInPlaceDimmedAndDoNothing() {
        setBar(listOf(AGENT, UNDO, SKILL_1, CLIPBOARD, DICTATE))
        reload()
        val open = build()
        val placesOpen = slots(open).map { it.centreIn(open.root) }
        slots(open).forEach { assertTrue(it.isEnabled && it.alpha == 1f, "${it.tag}") }

        // a password field: the settings are reloaded for the field the way the keyboard does at a field start
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY,
            InputAttributes(EditorInfo().apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }, false, latinIME.packageName))
        assertTrue(Settings.getValues().mIncognitoModeEnabled)
        val closed = build()
        val slotsClosed = slots(closed)
        assertEquals(listOf(AGENT, UNDO, SKILL_1, CLIPBOARD, DICTATE), slotsClosed.map { it.tag })
        layout(closed)
        // same places
        assertTrue(placesOpen.zip(slotsClosed.map { it.centreIn(closed.root) }).all { (a, b) -> abs(a - b) <= 1f })
        val dimmed = slotsClosed.filter { !it.isEnabled }.map { it.tag }
        assertEquals(listOf(AGENT, SKILL_1, DICTATE), dimmed)
        slotsClosed.filter { !it.isEnabled }.forEach {
            assertTrue(it.alpha < 0.5f)
            assertTrue("not available" in it.contentDescription.toString(), it.contentDescription.toString())
            it.performClick() // a dimmed slot does not react (a touch never reaches a disabled view; the strip also ignores the call)
        }
        assertEquals(emptyList(), closed.codes)
        slotsClosed.first { it.tag == UNDO }.performClick()
        assertEquals(listOf(KeyCode.UNDO), closed.codes)
        // the grid: the same tools dimmed, none removed
        leftKey(closed).performClick()
        assertEquals(BarTools.PROTECTED.size, closed.grid.listed.count { !closed.grid.pill(it)!!.isEnabled })
        assertTrue(BarTools.PROTECTED.all { closed.grid.pill(it) != null })
        closed.grid.pill(AGENT)!!.performClick()
        assertEquals(listOf(KeyCode.UNDO), closed.codes)
        assertTrue(closed.grid.isOpen) // a dimmed pill neither runs nor closes
        // the field is left: the tools are available again
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        val again = build()
        assertTrue(slots(again).all { it.isEnabled && it.alpha == 1f })
    }

    @Test fun incognitoShowsItsIconOnTheLeftKeyAndKeepsItsFunction() {
        prefs.edit { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true) }
        reload()
        val h = build()
        val left = leftKey(h)
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools_incognito), left.contentDescription)
        left.performClick()
        assertTrue(h.grid.isOpen)
        h.grid.close()
        h.strip.setSuggestions(words("hallo"), false)
        assertEquals(latinIME.getString(R.string.agent_bar_show_tools_incognito), left.contentDescription)
    }

    // ---- the other modes keep their look ----

    @Test fun theOtherExplicitModesStayAsTheyWere() {
        prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.TOOLBAR_KEYS.name) }
        reload()
        var h = build()
        assertTrue(h.strip.findViewById<View>(R.id.toolbar_container) is HorizontalScrollView)
        assertEquals(View.GONE, leftKey(h).visibility.let { if (Settings.getValues().mToolbarMode == ToolbarMode.EXPANDABLE) View.VISIBLE else View.GONE })
        // the old toolbar list, not the bar list, and the pinned keys beside it
        assertEquals(helium314.keyboard.latin.utils.getEnabledToolbarKeys(prefs), toolbar(h).let { t -> (0 until t.childCount).map { t.getChildAt(it).tag }.filterNotNull() })
        val pinned = h.strip.findViewById<ViewGroup>(R.id.pinned_keys)
        assertEquals(helium314.keyboard.latin.utils.getPinnedToolbarKeys(prefs), (0 until pinned.childCount).map { pinned.getChildAt(it).tag })

        prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.SUGGESTION_STRIP.name) }
        reload()
        h = build()
        assertTrue(h.strip.findViewById<View>(R.id.toolbar_container) is HorizontalScrollView)
        assertEquals(View.GONE, leftKey(h).visibility) // no expand arrow in this mode, as before
        assertEquals(1, h.strip.findViewById<ViewGroup>(R.id.pinned_keys).childCount)
        // the old arrow still toggles in the old expandable look only where it ever did: nothing opens a grid here
        assertFalse(h.grid.isOpen)
    }

    @Test fun theSwitchBringsBackTheExpandableBarOfBefore() {
        prefs.edit { putBoolean(AgentPreferences.FIXED_BAR, false) }
        reload()
        assertEquals(ToolbarMode.EXPANDABLE, Settings.getValues().mToolbarMode)
        assertFalse(Settings.getValues().mFixedBar)
        val h = build()
        // the scrolling toolbar with all enabled keys, the pinned key beside the suggestions, the arrow toggling the toolbar
        assertTrue(h.strip.findViewById<View>(R.id.toolbar_container) is HorizontalScrollView)
        assertEquals(helium314.keyboard.latin.utils.getEnabledToolbarKeys(prefs), toolbar(h).let { t -> (0 until t.childCount).map { t.getChildAt(it).tag }.filterNotNull() })
        assertEquals(listOf(AGENT), h.strip.findViewById<ViewGroup>(R.id.pinned_keys).let { p -> (0 until p.childCount).map { p.getChildAt(it).tag } })
        assertFalse(toolsVisible(h))
        assertTrue(leftKey(h).performClick())
        assertTrue(toolsVisible(h))
        assertFalse(h.grid.isOpen) // no grid in the old bar
        assertEquals(latinIME.getString(R.string.more_keys_strip_description), leftKey(h).contentDescription)
        // a settings choice that was made for the old bar works again: long press pins
        prefs.edit { putBoolean(Settings.PREF_QUICK_PIN_TOOLBAR_KEYS, true) }
        reload()
        assertTrue(Settings.getValues().mQuickPinToolbarKeys)
    }

    @Test fun theSwitchDoesNotChangeAnythingForTheOtherModes() {
        prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.SUGGESTION_STRIP.name) }
        reload()
        assertFalse(Settings.getValues().mFixedBar)
        prefs.edit { putBoolean(AgentPreferences.FIXED_BAR, true) }
        reload()
        assertFalse(Settings.getValues().mFixedBar)
        assertTrue(build().strip.findViewById<View>(R.id.toolbar_container) is HorizontalScrollView)
    }


    // ---- review of stages 85/86 ----

    @Test fun theClipboardClearingToolIsOnlyInTheGridForAUserWhoHadItInAList() {
        var h = build()
        leftKey(h).performClick()
        assertNull(h.grid.pill(CLEAR_CLIPBOARD)) // never by default: one tap would clear history, vault and clip
        // no other tool of the grid is irreversible: the rest toggles a mode, opens a window or edits text (undoable)
        assertEquals(setOf(CLEAR_CLIPBOARD), BarTools.DESTRUCTIVE)
        // in the old toolbar list ...
        prefs.edit { putString(Settings.PREF_TOOLBAR_KEYS, "CLEAR_CLIPBOARD:true|UNDO:true") }
        h = build(); leftKey(h).performClick()
        assertNotNull(h.grid.pill(CLEAR_CLIPBOARD))
        // ... or in the bar
        prefs.edit { remove(Settings.PREF_TOOLBAR_KEYS) }
        setBar(listOf(UNDO, CLEAR_CLIPBOARD)); reload()
        h = build(); leftKey(h).performClick()
        assertNotNull(h.grid.pill(CLEAR_CLIPBOARD))
    }

    @Test fun pinningAKeyToAFullBarSaysSoAndChangesNeitherList() {
        val eight = BarTools.offered.filter { it !in BarTools.MIC_KEYS && it != SKILL_1 }.take(BarTools.MAX)
        setBar(eight)
        val pinnedBefore = getPinnedToolbarKeys(prefs)
        assertFalse(pinKey(prefs, SKILL_1)) // the skill screen shows a message for this
        assertEquals(pinnedBefore, getPinnedToolbarKeys(prefs))
        assertEquals(eight, BarTools.parse(prefs.getString(AgentPreferences.BAR_TOOLS, null)))
        assertFalse(isKeyPinned(prefs, SKILL_1))
        // with room it pins in both lists, and unpinning takes it out of both
        setBar(eight.drop(1))
        assertTrue(pinKey(prefs, SKILL_1))
        assertTrue(isKeyPinned(prefs, SKILL_1))
        assertTrue(SKILL_1 in getPinnedToolbarKeys(prefs))
        unpinKey(prefs, SKILL_1)
        assertFalse(isKeyPinned(prefs, SKILL_1))
        assertFalse(SKILL_1 in getPinnedToolbarKeys(prefs))
    }

    @Test fun aKeyTakenOutOfTheBarNoLongerCountsAsPinned() {
        setBar(listOf(SKILL_1, UNDO))
        helium314.keyboard.latin.utils.addPinnedKey(prefs, SKILL_1) // the old list says pinned
        assertTrue(isKeyPinned(prefs, SKILL_1))
        BarTools.write(prefs, listOf(UNDO)) // taken out in the edit mode
        assertTrue(SKILL_1 in getPinnedToolbarKeys(prefs)) // the old list is not touched ...
        assertFalse(isKeyPinned(prefs, SKILL_1)) // ... but the bar is what counts
        // the other modes still read the old list
        prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.SUGGESTION_STRIP.name) }
        assertTrue(isKeyPinned(prefs, SKILL_1))
    }

    @Test fun aGarbledStoredBarFallsBackToTheMigrationAndIsLeftAloneUntilTheUserEdits() {
        val migrated = BarTools.read(prefs)
        prefs.edit { putString(AgentPreferences.BAR_TOOLS, "UNDO:true|COPY:maybe|???") }
        assertEquals(migrated, BarTools.read(prefs)) // not an empty bar
        assertFalse(BarTools.isCustomised(prefs))
        reload()
        assertEquals(migrated.filter { it != VOICE }, slots(build()).map { it.tag }) // the voice key is hidden here
        assertEquals("UNDO:true|COPY:maybe|???", prefs.getString(AgentPreferences.BAR_TOOLS, null)) // not overwritten by looking
        prefs.edit { putString(AgentPreferences.BAR_TOOLS, "") }
        assertEquals(migrated, BarTools.read(prefs))
        // an edit writes a good value
        BarTools.write(prefs, listOf(UNDO))
        assertEquals(listOf(UNDO), BarTools.read(prefs))
    }

    @Test fun theFlatPresetSetsTheBarToItsFourToolsAndItsBackupRestoresThePreviousBar() {
        setBar(listOf(UNDO, REDO))
        val preset = FlatPreset(latinIME)
        assertFalse(preset.isApplied())
        preset.apply()
        assertEquals(listOf(CLIPBOARD, EMOJI, AGENT, DICTATE), BarTools.read(prefs)) // a stored bar did not survive the preset's toolbar
        assertTrue(preset.isApplied())
        // changing the bar by hand makes the card "not applied" like any other value of the preset
        BarTools.write(prefs, listOf(UNDO))
        assertFalse(preset.isApplied())
        preset.apply()
        preset.restore()
        assertEquals(listOf(UNDO, REDO), BarTools.read(prefs))
        // a user without a stored bar gets none back: the migration is his bar again
        prefs.edit().clear().commit()
        val migrated = BarTools.read(prefs)
        preset.apply()
        preset.restore()
        assertFalse(prefs.contains(AgentPreferences.BAR_TOOLS))
        assertEquals(migrated, BarTools.read(prefs))
    }

    @Test fun theDiagnoseReportMatchesWhatIsOnScreen() {
        val h = build()
        class Report(val words: Boolean, val external: Boolean, val toolbar: Boolean, val emojis: Boolean)
        var last: Report? = null
        h.strip.stripReporter = { _, words, external, toolbar, emojis -> last = Report(words, external, toolbar, emojis) }
        h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false)
        assertEquals(false, last!!.toolbar) // the neutral tools are an empty strip
        h.strip.setSuggestions(words("hallo"), false)
        assertTrue(last!!.words); assertFalse(last!!.toolbar)
        // the tools forced over words (auto show): words under the toolbar, not "words"
        h.strip.setToolbarVisibility(true)
        assertTrue(last!!.toolbar); assertTrue(last!!.words)
        h.strip.setSuggestions(words("halli"), false)
        leftKey(h).performClick() // chosen by hand: the same
        assertTrue(last!!.toolbar)
        // tools over an external view (the pill): the toolbar covers it, it is not reported as shown
        h.strip.setExternalSuggestionView(android.widget.TextView(context), false)
        assertFalse(last!!.toolbar); assertTrue(last!!.external)
        h.strip.setToolbarVisibility(true)
        assertTrue(last!!.toolbar); assertFalse(last!!.external)
    }

    @Test fun whileTheRecentEmojisShowTheLeftKeyOpensTheGridInsteadOfPromisingTheTools() {
        flatPreset()
        helium314.keyboard.keyboard.emoji.RecentEmojis.set(listOf("🤠"))
        val h = build()
        h.strip.setSuggestions(words("hallo"), false) // content: the key would say "Werkzeuge zeigen"
        assertEquals(latinIME.getString(R.string.agent_bar_show_tools), leftKey(h).contentDescription)
        h.strip.showRecentEmojis(true)
        // the emojis cover tools and words alike: a tap on "show the tools" would change nothing visible
        assertEquals(latinIME.getString(R.string.agent_bar_all_tools), leftKey(h).contentDescription)
        assertTrue(leftKey(h).performClick())
        assertTrue(h.grid.isOpen)
        h.grid.close()
        h.strip.showRecentEmojis(false)
        assertEquals(latinIME.getString(R.string.agent_bar_show_tools), leftKey(h).contentDescription) // back to what is on screen
    }

    @Test fun aStripUpdateWhileTypingBuildsLooksUpAndArrangesNothingForTheBar() {
        val h = build()
        h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false) // warm up: the first update finds the grid of this window
        h.strip.setSuggestions(words("a"), false)
        BarCounters.reset()
        repeat(30) {
            h.strip.setSuggestions(words("ha", "hal", "hallo"), false)
            h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false)
        }
        assertEquals(0, BarCounters.gridLookups, "grid looked up")
        assertEquals(0, BarCounters.barBuilds, "slots built")
        assertEquals(0, BarCounters.arranges, "slots arranged")
        assertEquals(0, BarCounters.labelLookups, "descriptions read from the resources")
    }

    @Test fun theStoreIsDimmedInANumberLayoutAndTheCharacterArtIsNot() {
        setBar(listOf(PROMPTS, CHAR_ART, UNDO))
        reload()
        val h = build()
        assertTrue(slots(h).all { it.isEnabled })
        h.strip.numberLayoutProbe = { true }
        h.strip.setSuggestions(SuggestedWords.getEmptyInstance(), false)
        assertEquals(listOf(PROMPTS), slots(h).filter { !it.isEnabled }.map { it.tag })
        leftKey(h).performClick()
        assertFalse(h.grid.pill(PROMPTS)!!.isEnabled)
        assertTrue(h.grid.pill(CHAR_ART)!!.isEnabled)
        h.grid.pill(PROMPTS)!!.performClick()
        assertTrue(h.grid.isOpen); assertEquals(emptyList(), h.codes)
    }

    private companion object {
        const val WIDTH = 1200
        const val STRIP = 130
        const val KEYBOARD = 793
    }
}
