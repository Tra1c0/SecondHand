// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The menu of the Flat style and its connection to the strip, at the size of the reference
 * screenshot (1200 px wide, 3.25 px per dp, strip 130 px, keyboard view 793 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FlatMenuTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)

    @Before fun applyPreset() {
        latinIME.prefs().edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        FlatPreset(latinIME).apply()
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        KeyboardIconsSet.instance.loadIcons(context)
    }


    /** The settings singleton outlives a test; later tests must not inherit the Flat style. */
    @After fun restoreDefaults() {
        latinIME.prefs().edit().clear().commit()
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    @Test fun pillsSitWhereTheReferenceHasThemAndTheStripStaysFree() {
        val menu = FlatMenuView(context, null)
        val frame = ltrFrame().apply { addView(menu) }
        assertFalse(menu.isOpen)
        menu.open(STRIP)
        layout(frame)
        assertTrue(menu.isOpen)

        fun bounds(id: Int) = menu.findViewById<View>(id).let { view ->
            val left = generateSequence(view) { it.parent as? View }.takeWhile { it !== frame }.sumOf { it.left }
            val top = generateSequence(view) { it.parent as? View }.takeWhile { it !== frame }.sumOf { it.top }
            listOf(left, top, left + view.width, top + view.height)
        }
        fun assertBounds(expected: List<Int>, id: Int) = bounds(id).let { actual ->
            assertTrue(expected.zip(actual).all { (e, a) -> abs(e - a) <= 2 }, "expected $expected, was $actual")
        }
        // reference: columns x 78..587 and 613..1122, rows 208 px below the upper edge of the strip, 182 px apart
        assertBounds(listOf(78, 208, 587, 364), R.id.agent_menu_design)
        assertBounds(listOf(613, 208, 1122, 364), R.id.agent_menu_undo)
        assertBounds(listOf(78, 390, 587, 546), R.id.agent_menu_one_handed)
        assertBounds(listOf(613, 390, 1122, 546), R.id.agent_menu_emoji)
        assertBounds(listOf(78, 572, 587, 728), R.id.agent_menu_incognito)
        assertBounds(listOf(613, 572, 1122, 728), R.id.agent_menu_floating)
        assertBounds(listOf(888, 806, 1122, 910), R.id.agent_menu_edit)

        val picture = Bitmap.createBitmap(WIDTH, STRIP + KEYBOARD, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(picture))
        File("build/reports/flat-style").apply { mkdirs() }.resolve("menu.png").outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        fun hex(x: Int, y: Int) = String.format("#%06X", picture.getPixel(x, y) and 0xFFFFFF)
        assertEquals(0, picture.getPixel(600, STRIP / 2) ushr 24, "the strip area is not painted")
        assertEquals("#000000", hex(600, STRIP + 20))
        assertEquals("#4C4C4C", hex(440, 286)) // inside the first pill, between label and arrow
        assertTrue((90 until 160).any { x -> (250 until 320).any { y -> picture.getPixel(x, y) and 0xFFFFFF == 0xB7B7B7 } }, "icon of the first pill")
        assertEquals("#000000", hex(80, 210)) // rounded corner of the first pill
        assertEquals("#000000", hex(600, 286)) // gap between the columns

        // six real buttons with labels, in reading order
        val labels = listOf(R.id.agent_menu_design, R.id.agent_menu_undo, R.id.agent_menu_one_handed,
            R.id.agent_menu_emoji, R.id.agent_menu_incognito, R.id.agent_menu_floating).map { menu.findViewById<Button>(it).text.toString() }
        assertEquals(6, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
        assertNotNull(menu.findViewById<ImageButton>(R.id.agent_menu_edit).contentDescription)
    }

    @Test fun menuScrollsWhenTheKeyAreaIsLowerThanItsContent() {
        val menu = FlatMenuView(context, null)
        val frame = ltrFrame().apply { addView(menu) }
        menu.open(STRIP)
        // key area of 400 px, the content needs about 780 px
        frame.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(STRIP + 400, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, WIDTH, STRIP + 400)
        val scroll = menu.getChildAt(0) as android.widget.ScrollView
        assertEquals(400, scroll.height)
        assertTrue(scroll.getChildAt(0).height > 400)
        assertTrue(scroll.canScrollVertically(1))
    }

    @Test fun everyPillClosesTheMenuFirstAndThenReportsItsAction() {
        val menu = FlatMenuView(context, null)
        ltrFrame().apply { addView(menu) }.also { menu.open(STRIP); layout(it) }
        val seen = mutableListOf<Pair<Boolean, FlatMenuView.Action>>()
        var closed = 0
        menu.onAction = { seen += menu.isOpen to it }
        menu.onClosed = { closed++ }
        val expected = listOf(
            R.id.agent_menu_design to FlatMenuView.Action.OpenSettings(SettingsDestination.Appearance),
            R.id.agent_menu_undo to FlatMenuView.Action.Code(KeyCode.UNDO),
            R.id.agent_menu_one_handed to FlatMenuView.Action.Code(KeyCode.TOGGLE_ONE_HANDED_MODE),
            R.id.agent_menu_emoji to FlatMenuView.Action.Code(KeyCode.EMOJI),
            R.id.agent_menu_incognito to FlatMenuView.Action.Code(KeyCode.TOGGLE_INCOGNITO_MODE),
            R.id.agent_menu_floating to FlatMenuView.Action.Code(KeyCode.TOGGLE_FLOATING_WINDOW),
            R.id.agent_menu_edit to FlatMenuView.Action.OpenSettings(SettingsDestination.Toolbar),
        )
        expected.forEach { (id, _) ->
            menu.open(STRIP)
            assertTrue(menu.findViewById<View>(id).performClick())
        }
        assertEquals(expected.map { false to it.second }, seen)
        assertEquals(expected.size, closed)
        // only destinations the settings activity accepts from an intent
        seen.map { it.second }.filterIsInstance<FlatMenuView.Action.OpenSettings>()
            .forEach { assertTrue(it.destination in AgentPreferences.SETTINGS_DESTINATIONS) }
        // closing an already closed menu reports nothing
        menu.close()
        assertEquals(expected.size, closed)
    }

    @Test fun leftStripKeyTogglesTheMenuAndAnyToolbarKeyClosesIt() {
        val root = ltrFrame()
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, root, false) as ViewGroup
        container.id = R.id.strip_container
        container.findViewById<View>(R.id.emoji_tab_strip).visibility = View.GONE
        container.findViewById<View>(R.id.clipboard_strip_scroll_view).visibility = View.GONE
        val keys = FrameLayout(context).apply { id = R.id.keyboard_view_wrapper }
        keys.addView(MainKeyboardView(context, null).apply { id = R.id.keyboard_view })
        val menu = FlatMenuView(context, null).apply { id = R.id.agent_menu }
        root.addView(container); root.addView(keys); root.addView(menu)
        val strip = container.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        val codes = mutableListOf<Int>()
        strip.setListener(object : SuggestionStripView.Listener {
            override fun pickSuggestionManually(word: SuggestedWordInfo?) = Unit
            override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) { codes += primaryCode }
            override fun removeSuggestion(word: String?) = Unit
            override fun removeExternalSuggestions() = Unit
            override fun onSwipeDownOnToolbar() = Unit
        }, root)
        strip.setToolbarVisibility(true)
        layout(root)
        val leftKey = strip.findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
        val gridIcon = leftKey.drawable
        val openLabel = leftKey.contentDescription

        leftKey.performClick()
        assertTrue(menu.isOpen)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, keys.importantForAccessibility)
        assertTrue(leftKey.drawable !== gridIcon)
        assertNotNull(leftKey.background)
        assertTrue(leftKey.contentDescription != openLabel)
        // the toolbar stays where it was: the left key no longer switches to the suggestions
        assertEquals(View.VISIBLE, strip.findViewById<View>(R.id.toolbar_container).visibility)

        leftKey.performClick()
        assertFalse(menu.isOpen)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, keys.importantForAccessibility)
        assertTrue(leftKey.drawable === gridIcon)
        assertNull(leftKey.background)
        assertEquals(openLabel, leftKey.contentDescription)

        // a pill sends its key code through the strip's listener, after the menu is closed
        leftKey.performClick()
        menu.findViewById<View>(R.id.agent_menu_undo).performClick()
        assertFalse(menu.isOpen)
        assertEquals(listOf(KeyCode.UNDO), codes)

        // a toolbar key closes the menu before it acts
        leftKey.performClick()
        strip.findViewById<ViewGroup>(R.id.toolbar).findViewWithTag<View>(ToolbarKey.CLIPBOARD).performClick()
        assertFalse(menu.isOpen)
        assertEquals(listOf(KeyCode.UNDO, KeyCode.CLIPBOARD), codes)

        // with the toolbar hidden (suggestions showing), the open menu shows the tools and closing hides them again
        strip.setToolbarVisibility(false)
        leftKey.performClick()
        assertEquals(View.VISIBLE, strip.findViewById<View>(R.id.toolbar_container).visibility)
        leftKey.performClick()
        assertEquals(View.GONE, strip.findViewById<View>(R.id.toolbar_container).visibility)
        strip.setToolbarVisibility(true)

        // a long press on a toolbar key closes the menu too (the same happens when the strip is hidden
        // for the emoji or clipboard view; views without a window get no visibility callback to test it)
        leftKey.performClick()
        strip.findViewById<ViewGroup>(R.id.toolbar).findViewWithTag<View>(ToolbarKey.EMOJI).performLongClick()
        assertFalse(menu.isOpen)

        // design and edit open the settings at the right page
        leftKey.performClick()
        menu.findViewById<View>(R.id.agent_menu_edit).performClick()
        val intent = shadowOf(latinIME.application).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, intent.component?.className)
        assertEquals(SettingsDestination.Toolbar, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(intent.flags and android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
    }

    /** A root without a window cannot resolve the layout direction on its own. */
    private fun ltrFrame() = FrameLayout(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(STRIP + KEYBOARD, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, WIDTH, STRIP + KEYBOARD)
    }

    private companion object {
        const val WIDTH = 1200
        const val STRIP = 130
        const val KEYBOARD = 793
    }
}
