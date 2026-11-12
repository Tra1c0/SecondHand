// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.LocalTextAction
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The action panel in the Flat style: same controls and callbacks as before, new look.
 * Rendered at the size of the reference screenshot (1200 px wide, 3.25 px per dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FlatPanelTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)

    @Before fun applyPreset() {
        latinIME.prefs().edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        FlatPreset(latinIME).apply()
        reloadSettings()
    }

    /** The settings singleton outlives a test; later tests must not inherit the Flat style. */
    @After fun restoreDefaults() {
        latinIME.prefs().edit().clear().commit()
        reloadSettings()
    }

    private fun reloadSettings() {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    private fun panelInFrame(): Pair<AgentPanelView, FrameLayout> {
        val panel = AgentPanelView(context)
        val frame = FrameLayout(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR; addView(panel) }
        return panel to frame
    }

    private fun layout(view: View, height: Int = HEIGHT) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, WIDTH, height)
    }

    private fun render(frame: View, fileName: String, height: Int = HEIGHT): Bitmap {
        layout(frame, height)
        val picture = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        frame.draw(Canvas(picture))
        File("build/reports/flat-style").apply { mkdirs() }.resolve(fileName).outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return picture
    }

    @Test fun controlsKeepIdsTypesAndCallbacksInTheNewLook() {
        val (panel, frame) = panelInFrame()
        var closed = 0; var settings = 0; var applied = 0; var copied = 0; var sent = 0; var cancelled = 0
        val local = mutableListOf<LocalTextAction>()
        val cloud = mutableListOf<AiTextAction>()
        panel.onClose = { closed++ }; panel.onSettings = { settings++ }; panel.onApply = { applied++ }; panel.onCopy = { copied++ }
        panel.onSendCloud = { sent++ }; panel.onCancelCloud = { cancelled++ }
        panel.onAction = { local += it; panel.showResult(it.transform("Hallo")) }
        panel.onPrepareCloud = { cloud += it; panel.showCloudConfirmation("modell", 5) }
        panel.showSource("Hallo")
        layout(frame)

        listOf(R.id.agent_close, R.id.agent_cloud_settings, R.id.agent_apply, R.id.agent_copy, R.id.agent_cloud_send,
            R.id.agent_cloud_cancel, R.id.agent_uppercase, R.id.agent_lowercase, R.id.agent_clean_spaces, R.id.agent_cloud_correct,
            R.id.agent_cloud_translate, R.id.agent_cloud_rephrase, R.id.agent_cloud_summarize, R.id.agent_cloud_reply,
            R.id.agent_cloud_analyze, R.id.agent_workflows, R.id.agent_tool_test, R.id.agent_tool_calendar, R.id.agent_dictate)
            .forEach { assertTrue(panel.findViewById<View>(it) is Button, "id $it") }
        listOf(R.id.agent_result, R.id.agent_source, R.id.agent_status, R.id.agent_cloud_confirmation)
            .forEach { id -> panel.findViewById<View>(id).let { assertTrue(it is TextView && it !is Button) } }

        // nothing is applied or sent without its own click
        panel.findViewById<Button>(R.id.agent_uppercase).performClick()
        assertEquals(listOf(LocalTextAction.UPPERCASE), local)
        assertEquals("HALLO", panel.findViewById<TextView>(R.id.agent_result).text.toString())
        assertEquals(0, applied)
        panel.findViewById<Button>(R.id.agent_apply).performClick()
        assertEquals(1, applied)
        panel.findViewById<Button>(R.id.agent_copy).performClick()
        assertEquals(1, copied)
        panel.findViewById<Button>(R.id.agent_cloud_correct).performClick()
        assertEquals(listOf(AiTextAction.CORRECT), cloud)
        assertEquals(0, sent)
        assertEquals(View.VISIBLE, panel.findViewById<View>(R.id.agent_cloud_send).visibility)
        panel.findViewById<Button>(R.id.agent_cloud_send).performClick()
        assertEquals(1, sent)
        panel.findViewById<Button>(R.id.agent_cloud_cancel).performClick()
        assertEquals(1, cancelled)
        panel.findViewById<Button>(R.id.agent_close).performClick()
        panel.findViewById<Button>(R.id.agent_cloud_settings).performClick()
        assertEquals(1 to 1, closed to settings)

        // the round buttons carry their name for screen readers instead of a visible text
        listOf(R.id.agent_close, R.id.agent_cloud_settings).forEach { id ->
            panel.findViewById<Button>(id).let { assertTrue(it.text.isEmpty() && !it.contentDescription.isNullOrBlank()) }
        }
    }

    @Test fun headerCardAndChipsFollowTheReference() {
        val (panel, frame) = panelInFrame()
        panel.showSource("Hallo Welt")
        panel.showResult("HALLO WELT")
        val picture = render(frame, "panel-result.png")
        fun bounds(view: View): List<Int> {
            val left = generateSequence(view) { it.parent as? View }.takeWhile { it !== frame }.sumOf { it.left - (it.parent as View).scrollX }
            val top = generateSequence(view) { it.parent as? View }.takeWhile { it !== frame }.sumOf { it.top - (it.parent as View).scrollY }
            return listOf(left, top, left + view.width, top + view.height)
        }
        val close = panel.findViewById<Button>(R.id.agent_close)
        val heading = close.parent as LinearLayout
        assertSame(close, heading.getChildAt(0))
        // reference: back circle of 111 px at x 52..163, title starting at x 204, card from x 52 to 1148
        bounds(close).let { assertTrue(abs(it[0] - 52) <= 1 && abs(it[2] - 163) <= 2 && abs((it[3] - it[1]) - 111) <= 1, "$it") }
        assertTrue(abs(bounds(heading.getChildAt(1))[0] - 204) <= 2, "title at ${bounds(heading.getChildAt(1))[0]}")
        // status, source and result start directly below the header; description and dictation come last
        assertSame(panel.findViewById<View>(R.id.agent_status), (panel.getChildAt(0) as ViewGroup).getChildAt(1))
        assertSame(panel.findViewById<View>(R.id.agent_dictate), (panel.getChildAt(0) as ViewGroup).let { it.getChildAt(it.childCount - 1) })
        val result = panel.findViewById<TextView>(R.id.agent_result)
        bounds(result).let { assertTrue(abs(it[0] - 52) <= 1 && abs(it[2] - 1148) <= 1, "$it") }
        // the result block sits above the action chips
        val body = panel.getChildAt(0) as ViewGroup
        val uppercaseRow = panel.findViewById<View>(R.id.agent_uppercase).parent as View
        assertTrue(body.indexOfChild(result) < body.indexOfChild(uppercaseRow))
        assertTrue(body.indexOfChild(result) > body.indexOfChild(panel.findViewById(R.id.agent_cloud_cancel)))
        assertTrue(body.indexOfChild(panel.findViewById<View>(R.id.agent_apply).parent as View) == body.indexOfChild(result) + 1)
        // chips are 36 dp high
        assertTrue(abs(panel.findViewById<View>(R.id.agent_uppercase).height - 117) <= 1)

        fun hex(x: Int, y: Int) = String.format("#%06X", picture.getPixel(x, y) and 0xFFFFFF)
        val card = bounds(result)
        assertEquals("#000000", hex(600, 4))
        assertEquals("#4C4C4C", hex(card[2] - 60, (card[1] + card[3]) / 2))   // card fill
        assertEquals("#000000", hex(card[0] + 3, card[1] + 3))                // rounded corner
        // border: darker and yellowish at the upper left, lighter and reddish at the lower right
        val topLeft = picture.getPixel(card[0] + 120, card[1] + 2)
        val bottomRight = picture.getPixel(card[2] - 120, card[3] - 3)
        assertTrue(Color.red(bottomRight) > Color.red(topLeft) + 40 && Color.blue(bottomRight) > Color.blue(topLeft) + 40)
        // the apply chip is the accent colour, an ordinary chip is the key colour
        val apply = bounds(panel.findViewById(R.id.agent_apply))
        assertEquals("#6A96EF", hex(apply[0] + 40, apply[1] + 20))
        val chip = bounds(panel.findViewById(R.id.agent_uppercase))
        assertEquals("#4C4C4C", hex(chip[0] + 40, chip[1] + 20))
    }

    @Test fun busyShowsBarsInsteadOfTextAndEveryOtherStateRemovesThem() {
        val (panel, frame) = panelInFrame()
        val result = panel.findViewById<TextView>(R.id.agent_result)
        panel.showSource("Hallo Welt")
        layout(frame)
        val idle = result.background
        panel.showCloudBusy()
        assertNotSame(idle, result.background)
        assertEquals("", result.text.toString())
        val picture = render(frame, "panel-busy.png")
        // first bar: 16 dp from the card's left edge, 22.5 dp below its top
        val left = generateSequence<View>(result) { it.parent as? View }.takeWhile { it !== frame }.sumOf { it.left }
        val top = generateSequence<View>(result) { it.parent as? View }.takeWhile { it !== frame }.sumOf { it.top - (it.parent as View).scrollY }
        assertEquals("#514745", String.format("#%06X", picture.getPixel(left + 52 + 60, top + 73 + 30) and 0xFFFFFF))
        assertFalse(panel.findViewById<Button>(R.id.agent_apply).isEnabled)
        assertEquals(View.VISIBLE, panel.findViewById<View>(R.id.agent_cloud_cancel).visibility)

        panel.showResult("fertig")
        assertSame(idle, result.background)
        assertEquals("fertig", result.text.toString())
        panel.showCloudBusy()
        panel.clear()
        assertSame(idle, result.background)
    }

    @Test fun otherStylesKeepThePlainPanel() {
        latinIME.prefs().edit().clear().commit()
        reloadSettings()
        val panel = AgentPanelView(ContextThemeWrapper(latinIME, R.style.KeyboardTheme_LXX_Base_Border))
        val close = panel.findViewById<Button>(R.id.agent_close)
        assertTrue(close.text.isNotEmpty())
        assertNotSame(close, (close.parent as ViewGroup).getChildAt(0))
        val body = panel.getChildAt(0) as ViewGroup
        val uppercaseRow = panel.findViewById<View>(R.id.agent_uppercase).parent as View
        assertTrue(body.indexOfChild(panel.findViewById(R.id.agent_result)) > body.indexOfChild(uppercaseRow))
    }

    private companion object {
        const val WIDTH = 1200
        const val HEIGHT = 2600 // tall enough to show the whole scrolling content; the reference shows 923 px
    }
}
