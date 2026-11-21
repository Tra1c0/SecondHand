// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.RgbColors
import dev.agentkeyboard.core.Seen
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Stage 88c, second review: the rules of PillColors as a property over many colours, not only over the designs somebody thought of:
 * where the normal surface is at least 1.3:1 from the ground, the "in the bar" surface is at least 1.15:1 from the NORMAL surface
 * (the finished one, after the text rule has moved it), at least 1.1:1 from the ground, and the text and the icon of both are at least 4.5:1.
 */
class PillColorsTest {
    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    private fun check(key: Int, groundColour: Int, text: Int, icon: Int, label: String = "") {
        val ground = Seen.of(groundColour)
        val g = ground.argb
        val normal = PillColors.normal(key, ground, text, icon)
        val inBar = PillColors.inBar(key, ground, text, icon)
        val where = "key ${hex(key)}/${key ushr 24} ground ${hex(g)} text ${hex(text)} icon ${hex(icon)} $label: normal ${hex(normal.fill)}, in the bar ${hex(inBar.fill)}"
        for ((what, look) in listOf("normal" to normal, "in the bar" to inBar)) {
            assertTrue(RgbColors.contrast(look.text, look.fill) >= 4.5 - 0.02, "$what text ${RgbColors.contrast(look.text, look.fill)}: $where")
            assertTrue(RgbColors.contrast(look.icon, look.fill) >= 4.5 - 0.02, "$what icon ${RgbColors.contrast(look.icon, look.fill)}: $where")
        }
        if (RgbColors.contrast(normal.fill, g) >= 1.3) {
            assertTrue(RgbColors.contrast(inBar.fill, normal.fill) >= 1.15 - 0.01, "in the bar vs normal ${RgbColors.contrast(inBar.fill, normal.fill)}: $where")
            assertTrue(RgbColors.contrast(inBar.fill, g) >= 1.1 - 0.01, "in the bar vs ground ${RgbColors.contrast(inBar.fill, g)}: $where")
        }
    }

    @Test fun theReviewersExample() {
        // the surfaces came out equal (#B091D4 both) because the step was measured against the surface before the text rule moved it
        check(0xD9D713A8.toInt(), 0xFF9DCDE9.toInt(), 0xFF7F9EDB.toInt(), 0xFF7F9EDB.toInt(), "example")
    }

    @Test fun overAGridAndOverRandomColours() {
        val steps = intArrayOf(0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0, 0xFF)
        var n = 0
        // a grid: grey grounds, keys of several greys and alphas, texts of several greys
        for (ground in steps) for (key in steps) for (alpha in intArrayOf(0x40, 0x80, 0xD9, 0xFF)) for (text in steps) {
            check((alpha shl 24) or (key * 0x010101), 0xFF000000.toInt() or (ground * 0x010101), 0xFF000000.toInt() or (text * 0x010101), 0xFF000000.toInt() or (text * 0x010101), "grid")
            n++
        }
        // random colours, a fixed seed
        val random = java.util.Random(88)
        repeat(20_000) {
            fun colour() = 0xFF000000.toInt() or random.nextInt(0x1000000)
            val key = ((random.nextInt(256)) shl 24) or (random.nextInt(0x1000000))
            check(key, colour(), colour(), colour(), "random $it")
            n++
        }
        assertTrue(n > 20_000)
    }
}
