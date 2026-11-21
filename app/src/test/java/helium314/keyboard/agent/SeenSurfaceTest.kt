// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.Seen
import helium314.keyboard.latin.common.AllColors
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.default
import helium314.keyboard.latin.suggestions.StripIconColors
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.EnumMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 88c, review (root fix): the contrast functions cannot be given a translucent background. The class of error of build 43 (a
 * contrast computed against the raw, translucent design colour, while the colour is painted over a ground) is closed at the function:
 * the background parameter is a [Seen] colour (opaque by its type, built only with [Seen.of] or [Seen.over]).
 */
@RunWith(RobolectricTestRunner::class)
class SeenSurfaceTest {
    private fun design(main: Int, strip: Int, functional: Int = 0xFF303030.toInt(), expandBackground: Int = 0xFF303030.toInt()): Colors {
        val map = EnumMap<ColorType, Int>(ColorType::class.java)
        ColorType.entries.forEach { map[it] = it.default() }
        map[ColorType.MAIN_BACKGROUND] = main; map[ColorType.STRIP_BACKGROUND] = strip
        map[ColorType.FUNCTIONAL_KEY_BACKGROUND] = functional; map[ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND] = expandBackground
        return AllColors(map, "Material", true, null)
    }

    @Test fun noContrastFunctionTakesARawIntAsItsBackground() {
        // a function with an Int background would be named plainly; those with a Seen are mangled by the compiler ("name-<hash>")
        fun plain(cls: Class<*>, name: String) = cls.declaredMethods.filter { it.name == name }
        for (name in listOf("ensureContrast", "off")) assertEquals(emptyList(), plain(StripIconColors::class.java, name).map { it.toString() }, "StripIconColors.$name")
        for (name in listOf("readable", "label")) assertEquals(emptyList(), plain(RgbColors::class.java, name).map { it.toString() }, "RgbColors.$name")
        // and the mangled ones exist (the test looks at the right class)
        assertTrue(StripIconColors::class.java.declaredMethods.any { it.name.startsWith("ensureContrast-") })
        assertTrue(RgbColors::class.java.declaredMethods.any { it.name.startsWith("readable-") })
    }

    @Test fun aSeenColourIsAlwaysOpaqueAndMeansWhatIsDrawn() {
        // nothing known behind: a translucent colour over black, an opaque one unchanged
        assertEquals(0xFF808080.toInt(), Seen.of(0x80FFFFFF.toInt()).argb)
        assertEquals(0xFF123456.toInt(), Seen.of(0xFF123456.toInt()).argb)
        assertEquals(RgbColors.BLACK, Seen.of(0x00FFFFFF).argb)
        // the key colour of the owner (white at about half) over his ground: the pill he sees
        val ground = Seen.of(0xFF090001.toInt())
        val pill = Seen.over(0x80EDF8EB.toInt(), ground).argb
        assertEquals(255, RgbColors.alpha(pill))
        assertTrue(Math.abs(RgbColors.red(pill) - 0x7B) <= 2 && Math.abs(RgbColors.green(pill) - 0x7C) <= 2, Integer.toHexString(pill))
        // a translucent colour behind a translucent colour: both are laid over, the alpha of the ground is not dropped
        assertEquals(0xFF404040.toInt(), Seen.over(0x00000000, Seen.of(0x80808080.toInt())).argb)
    }

    @Test fun theExactBuild43CaseIsJudgedAgainstWhatIsDrawn() {
        val ground = Seen.of(0xFF090001.toInt())
        val key = 0x80EDF8EB.toInt()
        val drawn = Seen.over(key, ground)
        // white text on his pill: the rule must move it until it reads on the DRAWN pill
        val text = StripIconColors.ensureContrast(RgbColors.WHITE, drawn)
        assertTrue(RgbColors.contrast(text, drawn.argb) >= StripIconColors.NORMAL_CONTRAST - 0.05, "contrast ${RgbColors.contrast(text, drawn.argb)}")
        // what build 43 did (the translucent colour taken as opaque) leaves the text unreadable on the drawn pill: this is the class of error
        val asIfOpaque = StripIconColors.ensureContrast(RgbColors.WHITE, Seen.of(key or RgbColors.BLACK))
        assertTrue(RgbColors.contrast(asIfOpaque, drawn.argb) < 2.5, "the old way: ${RgbColors.contrast(asIfOpaque, drawn.argb)}")
    }

    @Test fun theBackgroundBehindTheStripIconsCountsATranslucentMainBackgroundOverBlackNotByItsRgb() {
        // a translucent keyboard background (white at 50 %) under a translucent strip colour (black at 50 %)
        val colors = design(main = 0x80FFFFFF.toInt(), strip = 0x80000000.toInt())
        val seen = StripIconColors.seenBackground(colors).argb
        assertEquals(0xFF404040.toInt(), seen) // 0x80 white over black = #808080, then 50 % black over it
        assertEquals(seen, StripIconColors.background(colors))
    }

    @Test fun theLeftKeyIsJudgedAgainstItsDiscAsDrawnOverTheStrip() {
        val colors = design(main = 0xFF202020.toInt(), strip = 0xFF202020.toInt(), functional = 0x80FFFFFF.toInt())
        // flat style with the disc: white at half over the strip, not white
        val disc = StripIconColors.leftKeySurface(colors, flatStyle = true, circle = true).argb
        assertEquals(0xFF909090.toInt(), disc)
        assertEquals(0xFF202020.toInt(), StripIconColors.leftKeySurface(colors, flatStyle = true, circle = false).argb)
        // the icon colour of the disc reads on the disc
        val icon = StripIconColors.ensureContrast(RgbColors.WHITE, StripIconColors.leftKeySurface(colors, true, true))
        assertTrue(RgbColors.contrast(icon, disc) >= StripIconColors.NORMAL_CONTRAST - 0.05, "contrast ${RgbColors.contrast(icon, disc)}")
    }

    @Test fun pillColorsTakeTheirGroundAsSeenAndAreOpaque() {
        val look = PillColors.normal(0x80EDF8EB.toInt(), Seen.of(0x80090001.toInt()), RgbColors.WHITE, RgbColors.WHITE)
        assertEquals(255, RgbColors.alpha(look.fill))
        assertTrue(RgbColors.contrast(look.text, look.fill) >= StripIconColors.NORMAL_CONTRAST - 0.05)
        assertTrue(RgbColors.contrast(look.icon, look.fill) >= StripIconColors.NORMAL_CONTRAST - 0.05)
    }
}
