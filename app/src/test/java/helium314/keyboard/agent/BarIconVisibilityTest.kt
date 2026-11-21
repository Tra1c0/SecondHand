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

    /** The design of the owner's phone as far as the screenshot shows it. */
    private val owner = design(0xFF0A0001.toInt(), 0xFFC8FFEE.toInt(), 0xFFA4A9A1.toInt(), 0xFF4A0006.toInt(), 0xFFF0145A.toInt())
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
            val off = StripIconColors.off(normal, bg)
            assertTrue(RgbColors.contrast(off, bg) >= StripIconColors.OFF_CONTRAST - 0.05, "$name off contrast ${RgbColors.contrast(off, bg)}")
            assertTrue(RgbColors.contrast(off, bg) < RgbColors.contrast(normal, bg), "$name: off is fainter than normal")
        }
    }

    @Test fun aGoodColourIsLeftAloneAndAFaintOneKeepsItsHueWhereItCan() {
        val bg = 0xFF0A0001.toInt()
        val mint = 0xFFC8FFEE.toInt()
        assertEquals(mint, StripIconColors.ensureContrast(mint, bg))
        val dark = 0xFF1A2A24.toInt()
        val lifted = StripIconColors.ensureContrast(dark, bg)
        assertTrue(RgbColors.contrast(lifted, bg) >= 4.5)
        assertTrue(RgbColors.green(lifted) > RgbColors.red(lifted), "still greenish")
        // black on black becomes light, white on white becomes dark
        assertTrue(RgbColors.luminance(StripIconColors.ensureContrast(Color.BLACK, bg)) > 0.15)
        assertTrue(RgbColors.luminance(StripIconColors.ensureContrast(Color.WHITE, 0xFFFFFFFF.toInt())) < 0.2)
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
            assertTrue(RgbColors.contrast(on, off) >= StripIconColors.MIN_STATE_STEP - 0.02, "on and off differ: $label")
            assertTrue(RgbColors.contrast(on, ground) >= StripIconColors.MIN_CONTRAST - 0.05 && RgbColors.contrast(off, ground) >= StripIconColors.MIN_CONTRAST - 0.05, "floor: $label")
            // on stays on the side of the ground where the design put it
            assertEquals(RgbColors.luminance(onColour) >= RgbColors.luminance(ground), RgbColors.luminance(on) >= RgbColors.luminance(ground), "side: $label")
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
        expand.colorFilter = android.graphics.PorterDuffColorFilter(StripIconColors.ensureContrast(colors.get(ColorType.TOOL_BAR_EXPAND_KEY), behind), android.graphics.PorterDuff.Mode.SRC_IN)
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
