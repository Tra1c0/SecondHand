// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.Seen
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
import helium314.keyboard.latin.common.default
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.FixedBar
import helium314.keyboard.latin.suggestions.StripIconColors
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.EnumMap
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 88, part A: every tool icon of the bar is visible in every design. The bug of build 41 was an untinted drawable:
 * the fork's own icons were black and the bar tints by multiplication, so they were black in every design (a hole on a
 * dark one). So this test does not only compute colours: it draws every [ToolbarKey] icon with the real arrangement code
 * ([FixedBar.arrange]) into a bitmap and measures the colour of the pixels the icon really has.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class BarIconVisibilityTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)

    @Before fun icons() {
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        KeyboardIconsSet.instance.loadIcons(context)
    }

    // ---- designs ----

    private fun design(background: Int, text: Int, keyBackground: Int, functional: Int, accent: Int, accentIcon: Int = Color.WHITE, strip: Int = background): Colors {
        val map = EnumMap<ColorType, Int>(ColorType::class.java)
        ColorType.entries.forEach { map[it] = it.default() }
        map[ColorType.MAIN_BACKGROUND] = background
        map[ColorType.STRIP_BACKGROUND] = strip
        map[ColorType.KEY_BACKGROUND] = keyBackground
        map[ColorType.FUNCTIONAL_KEY_BACKGROUND] = functional
        map[ColorType.ACTION_KEY_BACKGROUND] = accent
        map[ColorType.ACTION_KEY_ICON] = accentIcon
        listOf(ColorType.KEY_TEXT, ColorType.KEY_ICON, ColorType.TOOL_BAR_KEY, ColorType.TOOL_BAR_EXPAND_KEY, ColorType.FUNCTIONAL_KEY_TEXT).forEach { map[it] = text }
        map[ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND] = functional
        return AllColors(map, "Material", true, null)
    }

    /** The design of the owner's phone, measured on his build-43 screenshot (88c): ground #090001, symbols white, accent #DA3658, left disc #500C0D. */
    private val owner = design(0xFF090001.toInt(), 0xFFFFFFFF.toInt(), 0xFFA4A9A1.toInt(), 0xFF500C0D.toInt(), 0xFFDA3658.toInt())
    private val light = design(0xFFF4F4F4.toInt(), 0xFF202020.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDDDDD.toInt(), 0xFF3366CC.toInt())
    /** A design whose icon colour is almost the background: the rule must lift it. */
    private val faint = design(0xFF0A0001.toInt(), 0xFF141414.toInt(), 0xFF202020.toInt(), 0xFF303030.toInt(), 0xFF404040.toInt())
    private val midGrey = design(0xFF777777.toInt(), 0xFF808080.toInt(), 0xFF888888.toInt(), 0xFF999999.toInt(), 0xFFAAAAAA.toInt())
    private val flat get() = FlatStyle.colors("Flat", true, null)

    private val designs get() = listOf("owner" to owner, "light" to light, "faint" to faint, "mid grey" to midGrey, "flat preset" to flat)

    // ---- the colour rule ----

    @Test fun theRuleLiftsEveryColourToTheContrastAndNeverBelowTheFloor() {
        for ((name, colors) in designs) {
            val bg = StripIconColors.background(colors)
            val tint = StripIconColors.guaranteed(colors, StripIconColors.themeTint(colors, context))
            for (state in listOf(intArrayOf(), intArrayOf(android.R.attr.state_pressed), intArrayOf(android.R.attr.state_activated), intArrayOf(android.R.attr.state_pressed, android.R.attr.state_activated))) {
                val c = tint.getColorForState(state, tint.defaultColor)
                assertTrue(RgbColors.contrast(c, bg) >= StripIconColors.NORMAL_CONTRAST - 0.05, "$name ${state.toList()} contrast ${RgbColors.contrast(c, bg)}")
            }
            val normal = StripIconColors.normalOf(tint)
            val off = StripIconColors.off(normal, Seen.of(bg))
            assertTrue(RgbColors.contrast(off, bg) >= StripIconColors.OFF_CONTRAST - 0.05, "$name off contrast ${RgbColors.contrast(off, bg)}")
            assertTrue(RgbColors.contrast(off, bg) < RgbColors.contrast(normal, bg), "$name: off is fainter than normal")
        }
    }

    @Test fun aGoodColourIsLeftAloneAndAFaintOneKeepsItsHueWhereItCan() {
        val bg = 0xFF0A0001.toInt()
        val mint = 0xFFC8FFEE.toInt()
        assertEquals(mint, StripIconColors.ensureContrast(mint, Seen.of(bg)))
        val dark = 0xFF1A2A24.toInt()
        val lifted = StripIconColors.ensureContrast(dark, Seen.of(bg))
        assertTrue(RgbColors.contrast(lifted, bg) >= 4.5)
        assertTrue(RgbColors.green(lifted) > RgbColors.red(lifted), "still greenish")
        // black on black becomes light, white on white becomes dark
        assertTrue(RgbColors.luminance(StripIconColors.ensureContrast(Color.BLACK, Seen.of(bg))) > 0.15)
        assertTrue(RgbColors.luminance(StripIconColors.ensureContrast(Color.WHITE, Seen.of(0xFFFFFFFF.toInt()))) < 0.2)
    }

    // ---- real pixels ----

    private class Row(val wrapper: LinearLayout, val toolbar: LinearLayout, val buttons: List<ImageButton>, val bg: Int)

    private fun row(keys: List<ToolbarKey>, colors: Colors, flat: Boolean = false): Row {
        val bg = StripIconColors.background(colors)
        val wrapper = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val expand = ImageButton(context)
        wrapper.addView(expand, LinearLayout.LayoutParams(0, 0))
        val toolbar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val container = FrameLayout(context)
        container.addView(toolbar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        wrapper.addView(container, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        val buttons = keys.map { createToolbarKey(context, it, FixedBar.newButton(context)) }
        FixedBar.populate(toolbar, buttons)
        FixedBar.arrange(toolbar, expand, flat, colors)
        return Row(wrapper, toolbar, buttons, bg).also { layout(it) }
    }

    private fun layout(row: Row) {
        row.wrapper.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY))
        row.wrapper.layout(0, 0, WIDTH, HEIGHT)
    }

    private fun render(row: Row): Bitmap {
        val picture = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawColor(row.bg)
        row.wrapper.draw(canvas)
        return picture
    }

    /** The colour of the core of the ink in [rect] (the pixels farthest from [behind]; thin edges blend and are left out); null without ink. */
    private fun inkColour(picture: Bitmap, rect: android.graphics.Rect, behind: Int): Pair<Int, Int>? {
        fun dist(c: Int) = maxOf(abs(Color.red(c) - Color.red(behind)), abs(Color.green(c) - Color.green(behind)), abs(Color.blue(c) - Color.blue(behind)))
        var strongest = 0
        for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) strongest = maxOf(strongest, dist(picture.getPixel(x, y)))
        if (strongest < 24) return null
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (y in rect.top until rect.bottom) for (x in rect.left until rect.right) {
            val c = picture.getPixel(x, y)
            if (dist(c) >= strongest * 0.9) { r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++ }
        }
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()) to n
    }

    /** The glyph area of [button] (not the circle behind it), in the coordinates of the picture. */
    private fun glyphRect(row: Row, button: ImageButton): android.graphics.Rect {
        val offset = (row.toolbar.parent as View).left + row.toolbar.left
        return android.graphics.Rect(button.left + button.paddingLeft + offset, button.top + button.paddingTop,
            button.right - button.paddingRight + offset, button.bottom - button.paddingBottom)
    }

    private val every = BarTools.offered

    @Test fun everyIconIsClearlyVisibleInEveryDesignNormalAndUnavailable() {
        for ((name, colors) in designs) {
            for (batch in every.chunked(5)) {
                val row = row(batch, colors)
                val normal = render(row)
                batch.forEachIndexed { i, key ->
                    val rect = glyphRect(row, row.buttons[i])
                    val ink = inkColour(normal, rect, row.bg)
                    assertTrue(ink != null, "$name $key: no icon is drawn at all")
                    val contrast = RgbColors.contrast(ink.first, row.bg)
                    assertTrue(contrast >= StripIconColors.NORMAL_CONTRAST - 0.3, "$name $key: icon colour ${Integer.toHexString(ink.first)} has contrast $contrast against ${Integer.toHexString(row.bg)}")
                    assertTrue(ink.second > 12, "$name $key: only ${ink.second} core pixels")
                }
                // the same icons unavailable: still recognisable, but clearly off
                batch.forEachIndexed { i, key -> FixedBar.setAvailable(row.buttons[i], BarTools.Reason.PRIVATE_FIELD, false, key.name) { it } }
                layout(row)
                val off = render(row)
                batch.forEachIndexed { i, key ->
                    val rect = glyphRect(row, row.buttons[i])
                    val before = inkColour(normal, rect, row.bg)!!
                    val ink = inkColour(off, rect, row.bg)
                    assertTrue(ink != null, "$name $key: the unavailable icon vanished")
                    val contrast = RgbColors.contrast(ink.first, row.bg)
                    assertTrue(contrast >= StripIconColors.OFF_CONTRAST - 0.3, "$name $key: unavailable icon contrast $contrast")
                    assertTrue(contrast < RgbColors.contrast(before.first, row.bg) - 0.2, "$name $key: unavailable looks like available")
                    assertTrue(!normal.sameAs(off), "$name $key: no visible change")
                }
            }
        }
    }

    @Test fun theLastMicrophoneCircleHasItsIconVisibleOnItsFill() {
        for ((name, colors) in designs) {
            for (key in listOf(ToolbarKey.VOICE, ToolbarKey.DICTATE)) {
                val row = row(listOf(ToolbarKey.UNDO, key), colors)
                val button = row.buttons.last()
                assertTrue(FixedBar.isCircle(button))
                val fill = StripIconColors.background(colors).let { _ -> if (colors === flat) FlatStyle.STRIP_CIRCLE else colors.get(ColorType.ACTION_KEY_BACKGROUND) }
                val picture = render(row)
                val rect = glyphRect(row, button)
                val ink = inkColour(picture, rect, fill)
                assertTrue(ink != null, "$name $key: no icon on the circle")
                assertTrue(RgbColors.contrast(ink.first, fill) >= StripIconColors.MIN_CONTRAST - 0.2, "$name $key: icon ${Integer.toHexString(ink.first)} on circle ${Integer.toHexString(fill)}: ${RgbColors.contrast(ink.first, fill)}")
            }
        }
    }

    private fun accentOf(colors: Colors) = colors.get(ColorType.ACTION_KEY_BACKGROUND)

    /**
     * Stage 88c, review B (FixedBar): a translucent accent colour: the icon is judged against the circle as it is DRAWN. The reviewer
     * suspected the contrast was computed against the raw colour; the badge paints its colour opaque, so raw and drawn agree. This test
     * guards exactly that (it does not fail on the old code; it would fail if the badge ever started to paint the alpha).
     */
    @Test fun theMicrophoneCircleWithATranslucentAccentColourIsReadableAgainstWhatIsDrawn() {
        val translucent = listOf("dark red 25 % on light" to design(0xFFF4F4F4.toInt(), 0xFF202020.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDDDDD.toInt(), 0x40800020, accentIcon = Color.WHITE),
            "pink 40 % on light" to design(0xFFF4F4F4.toInt(), 0xFF202020.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDDDDD.toInt(), 0x66F0145A.toInt(), accentIcon = Color.WHITE))
        for ((name, colors) in translucent) for (key in listOf(ToolbarKey.VOICE, ToolbarKey.DICTATE)) {
            val row = row(listOf(ToolbarKey.UNDO, key), colors)
            val button = row.buttons.last()
            assertTrue(FixedBar.isCircle(button))
            val picture = render(row)
            val offset = (row.toolbar.parent as View).left + row.toolbar.left
            val whole = android.graphics.Rect(button.left + offset, button.top, button.right + offset, button.bottom)
            // the circle as drawn: the most frequent colour in the button that is not the strip
            val counts = HashMap<Int, Int>()
            for (y in whole.top until whole.bottom) for (x in whole.left until whole.right) picture.getPixel(x, y).let { if (it != row.bg) counts.merge(it, 1, Int::plus) }
            val drawn = counts.maxByOrNull { it.value }?.key ?: throw AssertionError("$name $key: no circle is drawn")
            val ink = inkColour(picture, glyphRect(row, button), drawn) ?: throw AssertionError("$name $key: no icon on the circle")
            // (CircleBadge paints the colour opaque: the circle as drawn is the RGB of the accent colour, whatever alpha the design gave it)
            assertEquals(accentOf(colors) or RgbColors.BLACK, drawn, "$name $key: the circle is drawn opaque")
            assertTrue(RgbColors.contrast(ink.first, drawn) >= StripIconColors.MIN_CONTRAST - 0.2, "$name $key: icon ${Integer.toHexString(ink.first)} on the drawn circle ${Integer.toHexString(drawn)}: ${RgbColors.contrast(ink.first, drawn)}")
        }
    }

    @Test fun aSwitchedOnToolStaysApartFromASwitchedOffOneAfterTheLift() {
        // themes that tell the two apart: the flat preset (grey on, darker grey off) and the default colours (light and dark)
        val dark = helium314.keyboard.latin.common.DefaultColors("Material", true, 0xFF8AB4F8.toInt(), 0xFF101010.toInt(), 0xFF303030.toInt(), 0xFF404040.toInt(), 0xFF303030.toInt(), 0xFFE0E0E0.toInt(), 0xFFB0B0B0.toInt())
        val bright = helium314.keyboard.latin.common.DefaultColors("Material", true, 0xFF1A73E8.toInt(), 0xFFF2F2F2.toInt(), 0xFFFFFFFF.toInt(), 0xFFDDDDDD.toInt(), 0xFFFFFFFF.toInt(), 0xFF202020.toInt(), 0xFF606060.toInt())
        val lowOff = helium314.keyboard.latin.common.DefaultColors("Material", true, 0xFF8AB4F8.toInt(), 0xFF202020.toInt(), 0xFF303030.toInt(), 0xFF404040.toInt(), 0xFF303030.toInt(), 0xFF707070.toInt(), 0xFF606060.toInt())
        for ((name, colors) in listOf("flat" to flat, "dark default" to dark, "light default" to bright, "dim text" to lowOff)) {
            val theme = StripIconColors.themeTint(colors, context)
            val adjusted = StripIconColors.guaranteed(colors, theme)
            val bg = StripIconColors.background(colors)
            val on = adjusted.getColorForState(intArrayOf(android.R.attr.state_activated), adjusted.defaultColor)
            val off = StripIconColors.normalOf(adjusted)
            val originalsDiffer = theme != null && theme.getColorForState(intArrayOf(android.R.attr.state_activated), theme.defaultColor) != theme.getColorForState(intArrayOf(), theme.defaultColor)
            if (originalsDiffer) assertTrue(RgbColors.contrast(on, off) >= StripIconColors.MIN_STATE_STEP - 0.02, "$name: on ${Integer.toHexString(on)} and off ${Integer.toHexString(off)} must differ (${RgbColors.contrast(on, off)})")
            assertTrue(RgbColors.contrast(on, bg) >= StripIconColors.MIN_CONTRAST - 0.05, "$name on vs ground")
            assertTrue(RgbColors.contrast(off, bg) >= StripIconColors.MIN_CONTRAST - 0.05, "$name off vs ground ${RgbColors.contrast(off, bg)}")
        }
        // a design without a difference (the owner's: one colour for both) has none to keep
        val ownerTheme = StripIconColors.themeTint(owner, context)
        val a = StripIconColors.guaranteed(owner, ownerTheme)
        assertEquals(a.getColorForState(intArrayOf(android.R.attr.state_activated), 0), StripIconColors.normalOf(a))
    }

    @Test fun theSwitchedOnToolIsNeverTheWeakerOneAndKeepsItsSide() {
        val cases = listOf(Triple(0xFFF2F2F2.toInt(), 0xFF989898.toInt(), 0xFFA0A0A0.toInt()), Triple(0xFF777777.toInt(), 0xFF7A7A7A.toInt(), 0xFF808080.toInt()),
            Triple(0xFF0A0001.toInt(), 0xFF202020.toInt(), 0xFF1A1A1A.toInt()), Triple(0xFF0A0001.toInt(), 0xFFC8FFEE.toInt(), 0xFF4D4D4D.toInt()))
        for ((ground, onColour, offColour) in cases) {
            val colors = design(ground, onColour, ground, ground, ground)
            val theme = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()), intArrayOf(onColour, offColour))
            val list = StripIconColors.guaranteed(colors, theme)
            val on = list.getColorForState(intArrayOf(android.R.attr.state_activated), 0)
            val off = StripIconColors.normalOf(list)
            val label = "ground ${Integer.toHexString(ground)} on ${Integer.toHexString(onColour)} off ${Integer.toHexString(offColour)} -> on ${Integer.toHexString(on)} off ${Integer.toHexString(off)}"
            assertTrue(RgbColors.contrast(on, ground) >= RgbColors.contrast(off, ground) - 0.01, "on is not the weaker: $label")
            // 1.25:1 apart where that is possible with both at least 4.5:1 (on a mid grey even black is not enough: then the minimum contrast wins)
            assertTrue(RgbColors.contrast(on, off) >= StripIconColors.MIN_STATE_STEP - 0.02 || on == Color.BLACK || on == Color.WHITE, "on and off differ: $label")
            assertTrue(RgbColors.contrast(on, ground) >= StripIconColors.MIN_CONTRAST - 0.05 && RgbColors.contrast(off, ground) >= StripIconColors.MIN_CONTRAST - 0.05, "floor: $label")
            // on stays on the side of the ground where the design put it
            // (unless that side cannot reach the minimum contrast at all: then the minimum contrast wins, as on a mid grey)
            val side = if (RgbColors.luminance(onColour) >= RgbColors.luminance(ground)) Color.WHITE else Color.BLACK
            if (RgbColors.contrast(side, ground) >= StripIconColors.NORMAL_CONTRAST)
                assertEquals(RgbColors.luminance(onColour) >= RgbColors.luminance(ground), RgbColors.luminance(on) >= RgbColors.luminance(ground), "side: $label")
        }
    }

    /** The rule holds on a whole grid, not only in the cases somebody thought of: every output is at least 3:1, normal and switched-on at least 4.5:1, apart where that is possible. */
    @Test fun theRuleHoldsOverAGridOfGroundsAndColours() {
        val steps = (0..255 step 16).toList() + 255
        val greys = steps.map { Color.rgb(it, it, it) }
        val others = listOf(0xFF0A0001.toInt(), 0xFFF0145A.toInt(), 0xFF1A73E8.toInt(), 0xFF2E7D32.toInt(), 0xFFC8FFEE.toInt(), 0xFFF2F2F2.toInt(), 0xFFFAFAFA.toInt(), 0xFFE8E8E8.toInt(), 0xFF080808.toInt(), 0xFF101010.toInt())
        val grounds = greys + others
        val colourSet = greys + others
        val activated = intArrayOf(android.R.attr.state_activated)
        var checked = 0
        for (ground in grounds) {
            val colors = design(ground, 0xFF808080.toInt(), ground, ground, ground)
            for (onColour in colourSet) for (offColour in colourSet) {
                val theme = android.content.res.ColorStateList(arrayOf(activated, intArrayOf()), intArrayOf(onColour, offColour))
                val list = StripIconColors.guaranteed(colors, theme)
                val bg = StripIconColors.background(colors)
                val on = list.getColorForState(activated, 0)
                val off = StripIconColors.normalOf(list)
                val label = "ground ${Integer.toHexString(ground)} on ${Integer.toHexString(onColour)} off ${Integer.toHexString(offColour)} -> on ${Integer.toHexString(on)} off ${Integer.toHexString(off)}"
                for (state in listOf(intArrayOf(), activated, intArrayOf(android.R.attr.state_pressed), intArrayOf(android.R.attr.state_pressed, android.R.attr.state_activated)))
                    assertTrue(RgbColors.contrast(list.getColorForState(state, 0), bg) >= StripIconColors.MIN_CONTRAST - 0.02, "floor ${state.toList()}: $label")
                assertTrue(RgbColors.contrast(off, bg) >= StripIconColors.NORMAL_CONTRAST - 0.05, "normal: $label")
                assertTrue(RgbColors.contrast(on, bg) >= StripIconColors.NORMAL_CONTRAST - 0.05, "switched on: $label")
                if (onColour != offColour) {
                    // nothing more is possible only when "on" is at white/black AND "off" has come down to the minimum contrast
                    val exhausted = (on == Color.WHITE || on == Color.BLACK) && RgbColors.contrast(off, bg) <= StripIconColors.NORMAL_CONTRAST + 0.06
                    val apart = RgbColors.contrast(on, bg) >= RgbColors.contrast(off, bg) - 0.01 && RgbColors.contrast(on, off) >= StripIconColors.MIN_STATE_STEP - 0.02
                    assertTrue(apart || exhausted, "on and off apart where possible: $label")
                }
                checked++
            }
            // an icon on an accent circle: 3:1 against the circle, whatever the colour
            for (icon in colourSet) assertTrue(RgbColors.contrast(StripIconColors.ensureContrast(icon, Seen.of(ground), StripIconColors.MIN_CONTRAST), ground) >= StripIconColors.MIN_CONTRAST - 0.02, "circle icon ${Integer.toHexString(icon)} on ${Integer.toHexString(ground)}")
        }
        assertTrue(checked > 5000)
    }

    @Test fun aSwitchedOffToolAtTheExtremeDoesNotSwallowTheSwitchedOnOne() {
        for ((ground, on, off) in listOf(Triple(0xFF101010.toInt(), 0xFF8AB4F8.toInt(), 0xFFFFFFFF.toInt()), Triple(0xFFF2F2F2.toInt(), 0xFF606060.toInt(), 0xFF000000.toInt()))) {
            val colors = design(ground, on, ground, ground, ground)
            val list = StripIconColors.guaranteed(colors, android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()), intArrayOf(on, off)))
            val gotOn = list.getColorForState(intArrayOf(android.R.attr.state_activated), 0)
            val gotOff = StripIconColors.normalOf(list)
            val label = "ground ${Integer.toHexString(ground)} on ${Integer.toHexString(on)} off ${Integer.toHexString(off)} -> on ${Integer.toHexString(gotOn)} off ${Integer.toHexString(gotOff)}"
            assertTrue(RgbColors.contrast(gotOn, gotOff) >= StripIconColors.MIN_STATE_STEP - 0.02, "on and off stay apart: $label")
            assertTrue(RgbColors.contrast(gotOff, ground) >= StripIconColors.NORMAL_CONTRAST - 0.05, "off keeps the minimum: $label")
            assertTrue(RgbColors.contrast(gotOn, ground) >= RgbColors.contrast(gotOff, ground) - 0.01, "on is not the weaker: $label")
        }
    }

    @Test fun theThreeCasesOfTheSecondReviewStayInRange() {
        for ((ground, on, off) in listOf(Triple(0xFFF2F2F2.toInt(), 0xFFFAFAFA.toInt(), 0xFF202020.toInt()), Triple(0xFF101010.toInt(), 0xFF080808.toInt(), 0xFFE0E0E0.toInt()), Triple(0xFFE8E8E8.toInt(), 0xFFFFFFFF.toInt(), 0xFF303030.toInt()))) {
            val colors = design(ground, on, ground, ground, ground)
            val list = StripIconColors.guaranteed(colors, android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()), intArrayOf(on, off)))
            val gotOn = list.getColorForState(intArrayOf(android.R.attr.state_activated), 0)
            assertTrue(RgbColors.contrast(gotOn, ground) >= 4.45, "ground ${Integer.toHexString(ground)} on ${Integer.toHexString(on)} off ${Integer.toHexString(off)} -> on ${Integer.toHexString(gotOn)} ${RgbColors.contrast(gotOn, ground)}")
        }
    }

    // ---- pictures to look at (not versioned: app/build/owner-bars/*.png) ----

    /** The strip as it is drawn in the owner-like design: the bar with the left key (disc and grid icon), at 3.25 px per dp, 1200 px wide, twice as high as the strip for a margin. */
    private fun picture(name: String, keys: List<ToolbarKey>, off: Set<ToolbarKey> = emptySet(), colors: Colors = owner) {
        val row = row(keys, colors)
        val expand = row.wrapper.getChildAt(0) as ImageButton
        val bg = row.bg
        // the left key: its disc (toolbar_expand_key_background in the colour of the design) and the grid icon with the contrast of the rule
        val disc = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.toolbar_expand_key_background)!!.mutate()
        disc.colorFilter = android.graphics.PorterDuffColorFilter(colors.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND), android.graphics.PorterDuff.Mode.SRC_IN)
        expand.background = disc
        expand.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.sym_keyboard_grid_flat)!!.mutate())
        expand.scaleType = android.widget.ImageView.ScaleType.CENTER
        val behind = RgbColors.over(colors.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND), bg)
        expand.colorFilter = android.graphics.PorterDuffColorFilter(StripIconColors.ensureContrast(colors.get(ColorType.TOOL_BAR_EXPAND_KEY), Seen.of(behind)), android.graphics.PorterDuff.Mode.SRC_IN)
        row.buttons.forEachIndexed { i, b -> if (keys[i] in off) FixedBar.setAvailable(b, BarTools.Reason.PRIVATE_FIELD, false, keys[i].name) { it } }
        layout(row)
        val strip = render(row)
        val picture = Bitmap.createBitmap(WIDTH, HEIGHT * 2, Bitmap.Config.ARGB_8888)
        Canvas(picture).apply { drawColor(bg); drawBitmap(strip, 0f, HEIGHT / 2f, null) }
        java.io.File("build/owner-bars").apply { mkdirs() }.resolve("owner-$name.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun theOwnerLikeBarsAreWrittenAsPicturesToLookAt() {
        picture("5-standard", listOf(ToolbarKey.AGENT, ToolbarKey.SETTINGS, ToolbarKey.CLIPBOARD, ToolbarKey.UNDO, ToolbarKey.VOICE))
        picture("6-owner", listOf(ToolbarKey.AGENT, ToolbarKey.SETTINGS, ToolbarKey.CLIPBOARD, ToolbarKey.UNDO, ToolbarKey.SKILL_1, ToolbarKey.VOICE))
        picture("8-compact", listOf(ToolbarKey.AGENT, ToolbarKey.SETTINGS, ToolbarKey.CLIPBOARD, ToolbarKey.UNDO, ToolbarKey.SKILL_1, ToolbarKey.EMOJI, ToolbarKey.CHAR_ART, ToolbarKey.VOICE))
        val keys = listOf(ToolbarKey.AGENT, ToolbarKey.SETTINGS, ToolbarKey.SKILL_1, ToolbarKey.UNDO, ToolbarKey.DICTATE)
        picture("5-unavailable", keys, off = setOf(ToolbarKey.AGENT, ToolbarKey.SKILL_1, ToolbarKey.DICTATE))
        picture("8-unavailable", listOf(ToolbarKey.AGENT, ToolbarKey.SETTINGS, ToolbarKey.CLIPBOARD, ToolbarKey.UNDO, ToolbarKey.SKILL_1, ToolbarKey.PROMPTS, ToolbarKey.CHAR_ART, ToolbarKey.DICTATE),
            off = setOf(ToolbarKey.AGENT, ToolbarKey.SKILL_1, ToolbarKey.PROMPTS, ToolbarKey.CHAR_ART, ToolbarKey.DICTATE))
        assertTrue(java.io.File("build/owner-bars/owner-5-standard.png").length() > 1000)
    }

    /**
     * Stage 99: the strip while the middle shows words, in the owner's design (ground #090001, symbols white, accent #DA3658, left disc
     * #500C0D) at 320, 360 and 411 dp: the left key, three words, and the circle at the right end, where the tool row has it
     * ([FixedBar.arrangeEnd]). Written to `build/owner-bars/owner-99-words-<dp>dp.png` to look at; the claims: the circle beside the
     * words covers the pixels of the row's circle, its fill is the accent and its icon is readable on it.
     */
    @Test fun theWordsBesideTheFixedCircleAreWrittenAsPicturesToLookAt() {
        val keys = listOf(ToolbarKey.SETTINGS, ToolbarKey.CLIPBOARD, ToolbarKey.UNDO, ToolbarKey.VOICE)
        for (dp in listOf(320, 360, 411)) {
            val width = Math.round(dp * 3.25f)
            val row = row(keys, owner)
            val wrapper = row.wrapper
            val expand = wrapper.getChildAt(0) as ImageButton
            val disc = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.toolbar_expand_key_background)!!.mutate()
            disc.colorFilter = android.graphics.PorterDuffColorFilter(owner.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND), android.graphics.PorterDuff.Mode.SRC_IN)
            expand.background = disc
            expand.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_arrow_left)!!.mutate())
            expand.scaleType = android.widget.ImageView.ScaleType.CENTER
            expand.colorFilter = android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
            fun measure() {
                wrapper.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY))
                wrapper.layout(0, 0, width, HEIGHT)
            }
            measure()
            val container = row.toolbar.parent as View
            val rowCircle = row.buttons.last()
            assertTrue(FixedBar.isCircle(rowCircle))
            val rowLeft = container.left + rowCircle.left
            val rowTop = container.top + rowCircle.top
            // the middle shows words: the tool row goes, the words area and the circle beside it come (as SuggestionStripView.applyBarState does)
            container.visibility = View.GONE
            val words = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER }
            listOf("Hallo", "Hallöchen", "Halle").forEach { w ->
                words.addView(android.widget.TextView(context).apply { text = w; setTextColor(Color.WHITE); textSize = 18f; gravity = android.view.Gravity.CENTER; maxLines = 1 },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            }
            wrapper.addView(words, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            val end = createToolbarKey(context, ToolbarKey.VOICE, FixedBar.newButton(context))
            wrapper.addView(end)
            FixedBar.arrangeEnd(end, false, owner, keys.size)
            measure()
            assertEquals(rowLeft, end.left, "$dp dp: the circle beside the words stands where the row has it")
            assertEquals(rowTop, end.top, "$dp dp: and at the same height")
            assertTrue(FixedBar.isCircle(end))
            assertTrue(words.right <= end.left && words.left >= expand.right, "$dp dp: the words lie between the left key and the circle")
            val picture = Bitmap.createBitmap(width, HEIGHT, Bitmap.Config.ARGB_8888)
            Canvas(picture).apply { drawColor(row.bg); wrapper.draw(this) }
            val accent = owner.get(ColorType.ACTION_KEY_BACKGROUND)
            // a point inside the circle (diameter 34 dp) and outside the glyph box (24 dp): the accent, opaque
            val fillSeen = picture.getPixel(end.left + end.width / 2, end.top + end.height / 2 + Math.round(14.5f * 3.25f))
            assertTrue(maxOf(abs(Color.red(fillSeen) - Color.red(accent)), abs(Color.green(fillSeen) - Color.green(accent)), abs(Color.blue(fillSeen) - Color.blue(accent))) <= 8,
                "$dp dp: the accent circle is drawn, seen ${Integer.toHexString(fillSeen)}")
            val glyph = android.graphics.Rect(end.left + end.paddingLeft, end.top + end.paddingTop, end.right - end.paddingRight, end.bottom - end.paddingBottom)
            val ink = inkColour(picture, glyph, accent or 0xFF000000.toInt())
            assertTrue(ink != null && RgbColors.contrast(ink.first, accent or 0xFF000000.toInt()) >= StripIconColors.MIN_CONTRAST - 0.2, "$dp dp: the icon on the circle")
            val out = Bitmap.createBitmap(width, HEIGHT * 2, Bitmap.Config.ARGB_8888)
            Canvas(out).apply { drawColor(row.bg); drawBitmap(picture, 0f, HEIGHT / 2f, null) }
            java.io.File("build/owner-bars").apply { mkdirs() }.resolve("owner-99-words-${dp}dp.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun theForkIconsThatWereBlackAreTintedLikeAnyOther() {
        // the build 41 bug: AGENT and SKILL_1 were pure black in the owner's design
        val row = row(listOf(ToolbarKey.AGENT, ToolbarKey.SETTINGS, ToolbarKey.SKILL_1, ToolbarKey.UNDO, ToolbarKey.DICTATE, ToolbarKey.CHAR_ART, ToolbarKey.SKILL_2, ToolbarKey.SKILL_3), owner)
        val picture = render(row)
        val colours = row.buttons.map { inkColour(picture, glyphRect(row, it), row.bg)!!.first }
        colours.forEachIndexed { i, c -> assertTrue(c != Color.BLACK && RgbColors.luminance(c) > 0.5, "${row.buttons[i].tag}: ${Integer.toHexString(c)}") }
        // all of them the same mint, whichever drawable
        colours.forEach { assertTrue(abs(Color.green(it) - Color.green(colours.first())) <= 6, "${Integer.toHexString(it)}") }
    }

    private companion object {
        const val WIDTH = 1200
        const val HEIGHT = 130
    }
}
