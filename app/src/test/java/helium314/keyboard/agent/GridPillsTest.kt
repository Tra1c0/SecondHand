// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import dev.agentkeyboard.core.RgbColors
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.AllColors
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors
import helium314.keyboard.latin.common.default
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.BarDrag
import helium314.keyboard.latin.suggestions.BarEditor
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.EnumMap
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 88c: the pills of the tool grid are readable in every design and in every state, measured on real pixels against the
 * surface the pill really has. Build 43 on the owner's phone showed pill text at 1.1:1 against its own pill: the design's key
 * colour is translucent (white at about 47 %), and the contrast rule of stage 88 computed against that colour as if it were
 * opaque (white) instead of against what the pill looks like over the grid. The same test draws the pictures of the owner's design
 * (`app/build/owner-bars/raster-*.png`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
open class GridPillsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
    /** The picture name prefix; the narrow subclass uses another. */
    open val tag = "wide"
    open val width = 1200

    @Before fun icons() {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        KeyboardIconsSet.instance.loadIcons(context)
    }

    private fun design(background: Int, pill: Int, text: Int, icon: Int = text): Colors {
        val map = EnumMap<ColorType, Int>(ColorType::class.java)
        ColorType.entries.forEach { map[it] = it.default() }
        map[ColorType.MAIN_BACKGROUND] = background; map[ColorType.STRIP_BACKGROUND] = background
        map[ColorType.KEY_BACKGROUND] = pill
        map[ColorType.KEY_TEXT] = text; map[ColorType.KEY_ICON] = icon
        return AllColors(map, "Material", true, null)
    }

    /** The owner's design as measured on his build-43 screenshot: ground #090001, pills a translucent light colour (#7B7C76 over the ground), white symbols. */
    private val owner get() = design(0xFF090001.toInt(), 0x80EDF8EB.toInt(), 0xFFFFFFFF.toInt())
    private val ownerOpaque get() = design(0xFF090001.toInt(), 0xFF7B7C76.toInt(), 0xFFFFFFFF.toInt())
    /** The earlier estimate (mint symbols), kept because the in-bar pill must also work with a coloured text. */
    private val ownerMint get() = design(0xFF090001.toInt(), 0x80EDF8EB.toInt(), 0xFFC8FFEE.toInt())
    private val darkDefault get() = DefaultColors("Material", true, 0xFF8AB4F8.toInt(), 0xFF101010.toInt(), 0xFF303030.toInt(), 0xFF404040.toInt(), 0xFF303030.toInt(), 0xFFE0E0E0.toInt(), 0xFFB0B0B0.toInt())
    private val lightDefault get() = DefaultColors("Material", true, 0xFF1A73E8.toInt(), 0xFFF2F2F2.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDDDDD.toInt(), 0xFFFFFFFF.toInt(), 0xFF202020.toInt(), 0xFF606060.toInt())
    private val flatLook get() = FlatStyle.colors("Flat", true, null)
    private val faint get() = design(0xFF101010.toInt(), 0x40FFFFFF, 0xFF404040.toInt())
    private val midGrey get() = design(0xFF777777.toInt(), 0xFF888888.toInt(), 0xFF8A8A8A.toInt())
    /** Light keys with dark text on a dark ground (the grid ground is not the ground of the keys). */
    private val lightOnDark get() = design(0xFF101010.toInt(), 0xFFDDDDDD.toInt(), 0xFF202020.toInt())
    /** Keys with the colour of the ground (flat designs): nothing tells a pill from the ground except what the grid adds. */
    private val keyIsGround get() = design(0xFF202020.toInt(), 0xFF202020.toInt(), 0xFFE0E0E0.toInt())
    private val keyIsLightGround get() = design(0xFFF2F2F2.toInt(), 0xFFF2F2F2.toInt(), 0xFF202020.toInt())
    /** White at about 47 % on a light ground. */
    private val whiteOnLight get() = design(0xFFEEEEEE.toInt(), 0x78FFFFFF, 0xFF202020.toInt())
    private val designs get() = listOf("owner" to owner, "owner opaque" to ownerOpaque, "owner mint" to ownerMint, "dark default" to darkDefault, "light default" to lightDefault, "flat" to flatLook,
        "faint" to faint, "mid grey" to midGrey, "light keys on dark ground" to lightOnDark, "key is ground" to keyIsGround, "key is light ground" to keyIsLightGround, "white on light" to whiteOnLight)

    private class Shown(val frame: FrameLayout, val grid: ToolGridView)

    private val tools = listOf(ToolbarKey.AGENT, ToolbarKey.DICTATE, ToolbarKey.SKILL_1, ToolbarKey.PROMPTS, ToolbarKey.CHAR_ART, ToolbarKey.EMOJI, ToolbarKey.CLIPBOARD, ToolbarKey.VOICE)

    private var stored: List<ToolbarKey> = listOf(ToolbarKey.UNDO, ToolbarKey.REDO, ToolbarKey.COPY)

    private fun relayout(frame: FrameLayout) {
        // as a window does frame after frame: the first pass lets the pills fit their names (a new text size asks for a new layout), and it
        // goes on until nothing asks for a layout any more
        var passes = 0
        do {
            frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1300, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, width, 1300)
            passes++
        } while ((frame.isLayoutRequested || passes < 2) && passes < 8)
    }

    private fun show(colors: Colors, editing: Boolean, unavailable: Set<ToolbarKey> = emptySet(), inBar: Set<ToolbarKey> = emptySet(),
                     german: Boolean = false, drag: Boolean = false, list: List<ToolbarKey> = tools): Shown {
        val ctx = if (!german) context else ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat).also {
            it.applyOverrideConfiguration(android.content.res.Configuration(latinIME.resources.configuration).apply { setLocale(Locale.GERMAN) })
        }
        val grid = ToolGridView(ctx, null)
        grid.colorsOverride = colors
        val frame = FrameLayout(ctx)
        // behind the grid: the strip of the keyboard (the floating copy is also drawn over it)
        frame.setBackgroundColor(colors.get(ColorType.STRIP_BACKGROUND) or RgbColors.BLACK)
        frame.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        grid.open(130, list.map { ToolGridView.Tool(it, it !in unavailable, if (it in unavailable) BarTools.Reason.PRIVATE_FIELD else null) })
        if (editing) {
            grid.setEditing(object : ToolGridView.EditListener {
                override fun onTap(key: ToolbarKey) = Unit
                override fun canUndo() = false
                override fun onUndo() = Unit
                override fun onReset() = Unit
                override fun actions(key: ToolbarKey) = emptyList<Pair<Int, () -> Boolean>>()
            })
            grid.setMarked(inBar)
            if (drag) {
                val geometry = object : BarDrag.Geometry {
                    override fun inBarZone(x: Float, y: Float) = y <= 130f
                    override fun slotAt(x: Float, count: Int) = 0
                }
                grid.dragLogic = BarDrag(BarEditor({ stored }, { stored = it }), geometry, BarDrag.Config(holdMs = 180, slopPx = 20f))
                grid.slotAt = { _, _ -> null }
            }
        }
        // a view without a window never resolves its start/end drawables: give the pills their direction (a phone does it by itself)
        grid.listed.forEach { grid.pill(it)!!.layoutDirection = View.LAYOUT_DIRECTION_LTR }
        relayout(frame)
        return Shown(frame, grid)
    }

    /** A real finger: down on the pill of [key], a sideways move (picks the tool up), then on to ([x], [y]). */
    private fun pickUp(shown: Shown, key: ToolbarKey, x: Float, y: Float) {
        val box = boxOf(shown, shown.grid.pill(key)!!)
        val t0 = SystemClock.uptimeMillis()
        fun send(action: Int, px: Float, py: Float, dt: Long) { val e = MotionEvent.obtain(t0, t0 + dt, action, px, py, 0); shown.frame.dispatchTouchEvent(e); e.recycle() }
        send(MotionEvent.ACTION_DOWN, box.centerX().toFloat(), box.centerY().toFloat(), 0)
        send(MotionEvent.ACTION_MOVE, box.centerX() + 80f, box.centerY().toFloat(), 16)
        send(MotionEvent.ACTION_MOVE, x, y, 32)
        relayout(shown.frame)
    }

    private fun render(shown: Shown, name: String? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(width, 1300, Bitmap.Config.ARGB_8888)
        // the keyboard behind the grid is dark in the owner's design: the grid paints its own ground over it
        shown.frame.draw(Canvas(bitmap))
        if (name != null) File("build/owner-bars").apply { mkdirs() }.resolve("raster-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun boxOf(shown: Shown, view: View): Rect {
        var x = 0; var y = 0; var v: View = view
        while (v !== shown.frame) { val p = v.parent as View; x += v.left - p.scrollX; y += v.top - p.scrollY; v = p }
        return Rect(x, y, x + view.width, y + view.height)
    }

    /** The surface of the pill (its most frequent colour inside) and the core colour of the ink in [area] (the pixels farthest from it). */
    private fun measure(bitmap: Bitmap, box: Rect, area: Rect): Triple<Int, Int, Int>? {
        val counts = HashMap<Int, Int>()
        for (y in box.top + box.height() / 4 until box.bottom - box.height() / 4) for (x in box.left + box.width() / 3 until box.right - box.height() / 2) counts.merge(bitmap.getPixel(x, y), 1, Int::plus)
        val ground = counts.maxByOrNull { it.value }!!.key
        fun dist(c: Int) = maxOf(abs(Color.red(c) - Color.red(ground)), abs(Color.green(c) - Color.green(ground)), abs(Color.blue(c) - Color.blue(ground)))
        var strongest = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) strongest = maxOf(strongest, dist(bitmap.getPixel(x, y)))
        if (strongest < 16) return null
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            val c = bitmap.getPixel(x, y)
            if (dist(c) >= strongest * 0.9) { r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++ }
        }
        return Triple(ground, Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()), n)
    }

    /** The core colour of the ink in [area] against the surface [surface] (null without ink). */
    private fun inkOn(bitmap: Bitmap, area: Rect, surface: Int): Int? {
        fun dist(c: Int) = maxOf(abs(Color.red(c) - Color.red(surface)), abs(Color.green(c) - Color.green(surface)), abs(Color.blue(c) - Color.blue(surface)))
        var strongest = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) strongest = maxOf(strongest, dist(bitmap.getPixel(x, y)))
        if (strongest < 16) return null
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            val c = bitmap.getPixel(x, y)
            if (dist(c) >= strongest * 0.9) { r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++ }
        }
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    private fun check(label: String, shown: Shown, bitmap: Bitmap, key: ToolbarKey, minimum: Double, maximum: Double = 99.0) {
        val pill = shown.grid.pill(key)!!
        val box = boxOf(shown, pill)
        // the text lies right of the icon, the icon at the left
        val iconArea = Rect(box.left + pill.paddingLeft, box.top + box.height() / 4, box.left + pill.paddingLeft + (box.height() * 0.5f).toInt(), box.bottom - box.height() / 4)
        val textArea = Rect(box.left + pill.paddingLeft + (box.height() * 0.9f).toInt(), box.top + box.height() / 4, box.right - box.height() / 2, box.bottom - box.height() / 4)
        for ((what, area) in listOf("text" to textArea, "icon" to iconArea)) {
            val m = measure(bitmap, box, area) ?: throw AssertionError("$label $key: no $what is drawn (area $area, box $box, text size ${pill.textSize} colour ${hex(pill.currentTextColor)}, pill ${pill.width} x ${pill.height})")
            val contrast = RgbColors.contrast(m.second, m.first)
            assertTrue(contrast >= minimum, "$label $key $what ${hex(m.second)} on its pill ${hex(m.first)}: contrast $contrast, wanted at least $minimum")
            assertTrue(contrast <= maximum, "$label $key $what ${hex(m.second)} on ${hex(m.first)}: $contrast, expected fainter than $maximum")
        }
    }

    @Test fun everyPillIsReadableOnItsOwnSurfaceInEveryStateAndDesign() {
        for ((name, colors) in designs) {
            // not in the bar, ordinary
            var shown = show(colors, editing = false); var bitmap = render(shown)
            for (key in listOf(ToolbarKey.AGENT, ToolbarKey.DICTATE, ToolbarKey.EMOJI)) check("$name normal", shown, bitmap, key, 4.4)
            // the edit mode: tools outside and inside the bar
            shown = show(colors, editing = true, inBar = setOf(ToolbarKey.CLIPBOARD, ToolbarKey.VOICE)); bitmap = render(shown)
            for (key in listOf(ToolbarKey.AGENT, ToolbarKey.PROMPTS)) check("$name edit, outside the bar", shown, bitmap, key, 4.4)
            for (key in listOf(ToolbarKey.CLIPBOARD, ToolbarKey.VOICE)) check("$name edit, in the bar", shown, bitmap, key, 4.4)
            // not available: clearly fainter, still recognisable
            shown = show(colors, editing = false, unavailable = setOf(ToolbarKey.AGENT, ToolbarKey.SKILL_1)); bitmap = render(shown)
            check("$name unavailable", shown, bitmap, ToolbarKey.AGENT, 2.9, 12.0)
            // pressed
            shown = show(colors, editing = false); shown.grid.pill(ToolbarKey.AGENT)!!.isPressed = true; bitmap = render(shown)
            check("$name pressed", shown, bitmap, ToolbarKey.AGENT, 4.2)
        }
    }

    @Test fun anInBarPillCanBeToldFromAnOutsidePillAndBothFromTheGround() {
        for ((name, colors) in designs) {
            val shown = show(colors, editing = true, inBar = setOf(ToolbarKey.CLIPBOARD)); val bitmap = render(shown)
            val outBox = boxOf(shown, shown.grid.pill(ToolbarKey.AGENT)!!); val inBox = boxOf(shown, shown.grid.pill(ToolbarKey.CLIPBOARD)!!)
            // the SURFACES (the most frequent colour inside each pill), not the thin outline at the edge
            fun surfaceOf(box: Rect) = measure(bitmap, box, Rect(box.left, box.top, box.left + 1, box.top + 1))?.first
                ?: bitmap.getPixel(box.right - box.height(), box.centerY())
            val outside = surfaceOf(outBox); val inside = surfaceOf(inBox)
            val ground = bitmap.getPixel(4, 1200)
            // the surfaces are measured as the exact interior pixel (no blending), so the only slack is the rounding of the colour channels (0.01)
            assertTrue(RgbColors.contrast(outside, inside) >= 1.15 - 0.01, "$name: the surface of a pill in the bar differs from the others (${hex(outside)} / ${hex(inside)}: ${RgbColors.contrast(outside, inside)})")
            assertTrue(RgbColors.contrast(outside, ground) >= 1.25, "$name: the pill stands out from the ground of the grid (${hex(outside)} on ${hex(ground)})")
            assertTrue(RgbColors.contrast(inside, ground) >= 1.1 - 0.01, "$name: the pill in the bar is still no hole in the ground (${hex(inside)} on ${hex(ground)}: ${RgbColors.contrast(inside, ground)})")
            // and the outline is a second cue: its pixel at the edge differs from the ground clearly
            val edge = bitmap.getPixel(inBox.left + 3, inBox.centerY())
            assertTrue(RgbColors.contrast(edge, ground) >= 1.5, "$name: the outline of a pill in the bar shows (${hex(edge)} on ${hex(ground)}: ${RgbColors.contrast(edge, ground)})")
        }
    }

    /** Stage 88c, review A: the floating copy of a tool in the hand: its icon is readable against the disc as it is DRAWN, over the grid and over the bar. */
    @Test fun theFloatingCopyIsReadableOnItsOwnDiscOverTheGridAndOverTheBar() {
        for ((name, colors) in designs) for ((where, at) in listOf("grid" to (width / 2f to 900f), "bar" to (width / 2f to 60f))) {
            val shown = show(colors, editing = true, drag = true)
            pickUp(shown, ToolbarKey.AGENT, at.first, at.second)
            val copy = shown.grid.floatingCopy ?: throw AssertionError("$name: no floating copy after the pick-up")
            val bitmap = render(shown, if (name == "owner") "$tag-4-ziehen-$where" else null)
            val box = Rect(copy.x.toInt(), copy.y.toInt(), (copy.x + copy.width).toInt(), (copy.y + copy.height).toInt())
            val disc = bitmap.getPixel(box.centerX(), box.top + box.height() * 12 / 100)
            val inner = Rect(box.centerX() - box.width() / 5, box.centerY() - box.height() / 5, box.centerX() + box.width() / 5, box.centerY() + box.height() / 5)
            val ink = inkOn(bitmap, inner, disc) ?: throw AssertionError("$name over the $where: no icon on the floating copy (disc ${hex(disc)})")
            assertTrue(RgbColors.contrast(ink, disc) >= 4.4, "$name over the $where: the icon ${hex(ink)} on its disc ${hex(disc)}: ${RgbColors.contrast(ink, disc)}")
        }
    }

    /** Review C: the hint of the edit mode and the question of the reset stand on the GROUND of the grid, not on a pill. */
    @Test fun theHintAndTheResetQuestionAreReadableOnTheGroundOfTheGrid() {
        for ((name, colors) in designs) {
            val shown = show(colors, editing = true); var bitmap = render(shown)
            val ground = bitmap.getPixel(4, 1200)
            fun check(what: String, view: View) {
                val box = boxOf(shown, view)
                val area = Rect(box.left + box.width() / 4, box.top, box.right - box.width() / 4, box.bottom)
                val ink = inkOn(bitmap, area, ground) ?: throw AssertionError("$name: no $what is drawn in $box")
                assertTrue(RgbColors.contrast(ink, ground) >= 4.4, "$name: the $what ${hex(ink)} on the ground ${hex(ground)}: ${RgbColors.contrast(ink, ground)}")
            }
            check("hint", shown.grid.hint!!)
            shown.grid.findViewById<View>(R.id.agent_tool_grid_reset).performClick()
            relayout(shown.frame); bitmap = render(shown)
            check("question", (shown.grid.findViewById<android.view.ViewGroup>(R.id.agent_tool_grid_reset_ask)).getChildAt(0))
        }
    }

    private fun assertNoLabelIsCut(name: String, shown: Shown, keys: List<ToolbarKey>) {
        for (key in keys) {
            val pill = shown.grid.pill(key)!!
            // a text size set by fitText in the last pass asks for a new layout, which a window builds in its next frame: build it here
            if (pill.layout == null) { pill.forceLayout(); pill.measure(View.MeasureSpec.makeMeasureSpec(pill.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(pill.height, View.MeasureSpec.EXACTLY)) }
            val layout = pill.layout ?: throw AssertionError("$name $key has no layout: text '${pill.text}', size ${pill.width} x ${pill.height} at ${pill.left},${pill.top}, visibility ${pill.visibility}, textSize ${pill.textSize}, row ${(pill.parent as View).width} x ${(pill.parent as View).height}")
            val available = pill.width - pill.paddingLeft - pill.paddingRight - pill.compoundDrawablesRelative[0].intrinsicWidth - pill.compoundDrawablePadding
            assertTrue(layout.lineCount <= 2, "$name $key '${pill.text}': ${layout.lineCount} lines at ${pill.textSize} px")
            for (line in 0 until layout.lineCount) {
                assertEquals(0, layout.getEllipsisCount(line), "$name $key: line $line is ellipsized: '${pill.text}' at ${pill.textSize / pill.resources.displayMetrics.scaledDensity} sp")
                assertTrue(layout.getLineWidth(line) <= available + 1f, "$name $key: line $line is ${layout.getLineWidth(line)} px wide, the room is $available px ('${pill.text}')")
            }
            assertTrue(layout.height <= pill.height, "$name $key: the text is ${layout.height} px high in a pill of ${pill.height} px")
            // the row must not push a two-line pill down (baseline alignment): it would be cut off at the bottom of the row
            val row = pill.parent as View
            assertTrue(pill.top == 0 && pill.bottom <= row.height, "$name $key: the pill lies at ${pill.top}..${pill.bottom} in a row of ${row.height} px, its lower part is cut off")
        }
    }

    @Test fun noLabelIsCutOff() {
        for ((name, colors) in designs) for (editing in listOf(false, true))
            assertNoLabelIsCut("$name edit=$editing", show(colors, editing = editing, inBar = setOf(ToolbarKey.CLIPBOARD)).also { render(it) }, tools)
    }

    /** Review F: the real GERMAN names of every tool, as the owner reads them; the measuring of fitText and the display break lines by the same rule. */
    @Test fun noGermanLabelOfAnyToolIsCutOff() {
        val all = BarTools.offered
        for (editing in listOf(false, true)) {
            val shown = show(owner, editing = editing, german = true, list = all)
            for (key in all) assertTrue(shown.grid.pill(key) != null, "$key has a pill")
            assertNoLabelIsCut("de edit=$editing", shown, all)
        }
    }

    /** The button that starts the edit mode is an ordinary button like Undo/Reset: same height, width after its content, centred, readable. */
    @Test fun theCustomizeButtonIsAsHighAsTheSmallButtonsCentredAndNarrowerThanTheGrid() {
        for ((name, colors) in designs.take(2)) {
            val editing = show(colors, editing = true)
            val undo = editing.grid.findViewById<View>(R.id.agent_tool_grid_undo)
            val shown = show(colors, editing = false)
            val button = shown.grid.findViewById<android.widget.Button>(R.id.agent_tool_grid_edit)
            assertTrue(undo.height > 0 && button.height > 0, "$name: both buttons are laid out")
            assertEquals(undo.height, button.height, "$name: the button is as high as the small buttons")
            assertTrue(button.width < shown.grid.width * 0.6f, "$name: the button is ${button.width} px wide in a grid of ${shown.grid.width} px")
            val box = boxOf(shown, button)
            assertTrue(abs(box.centerX() - shown.grid.width / 2) <= 2, "$name: the button is centred (centre ${box.centerX()} of ${shown.grid.width})")
            button.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val bitmap = render(shown)
            val area = Rect(box.left + box.height() / 2, box.top + box.height() / 4, box.right - box.height() / 2, box.bottom - box.height() / 4)
            val m = measure(bitmap, box, area) ?: throw AssertionError("$name: no text is drawn on the button")
            assertTrue(RgbColors.contrast(m.second, m.first) >= 4.4, "$name: the text ${hex(m.second)} on the button ${hex(m.first)}: ${RgbColors.contrast(m.second, m.first)}")
            // the text is in the middle of the button, not at its top
            val textRows = (box.top until box.bottom).filter { y -> (area.left until area.right).any { x -> abs(Color.red(bitmap.getPixel(x, y)) - Color.red(m.first)) > 60 } }
            assertTrue(textRows.isNotEmpty() && abs((textRows.first() + textRows.last()) / 2 - box.centerY()) <= box.height() / 8, "$name: the text is centred vertically (rows ${textRows.firstOrNull()}..${textRows.lastOrNull()} in $box)")
        }
    }

    /** The pictures of the owner's design for looking at (not versioned). */
    @Test fun theOwnerGridIsWrittenAsPictures() {
        render(show(owner, editing = false), "$tag-1-offen")
        render(show(owner, editing = true, inBar = setOf(ToolbarKey.CLIPBOARD, ToolbarKey.VOICE)), "$tag-2-anpassen")
        render(show(owner, editing = false, unavailable = setOf(ToolbarKey.AGENT, ToolbarKey.DICTATE, ToolbarKey.SKILL_1, ToolbarKey.PROMPTS, ToolbarKey.CHAR_ART)), "$tag-3-nicht-verfuegbar")
        render(show(owner, editing = false, german = true, list = BarTools.offered), "$tag-5-deutsch-offen")
        assertTrue(File("build/owner-bars/raster-$tag-1-offen.png").length() > 1300)
    }
}

/** The same checks on a 360 dp wide screen (the narrowest the bar is meant for). */
@Config(qualifiers = "w360dp-h780dp-420dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class GridPillsNarrowTest : GridPillsTest() {
    override val tag = "360dp"
    override val width = 945
}

/** And at the width of the owner's phone: 1200 px at a density of 2.75. */
@Config(qualifiers = "w436dp-h970dp-440dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class GridPillsOwnerPhoneTest : GridPillsTest() {
    override val tag = "xiaomi"
    override val width = 1200
}

/** And the narrowest screen of all, 320 dp (840 px at 420 dpi). */
@Config(qualifiers = "w320dp-h700dp-420dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class GridPills320Test : GridPillsTest() {
    override val tag = "320dp"
    override val width = 840
}
