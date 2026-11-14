// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import helium314.keyboard.settings.screens.AgentSettingsTheme
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.AreaHero
import helium314.keyboard.settings.screens.BackHeader
import helium314.keyboard.settings.screens.CHIP_TAG
import helium314.keyboard.settings.screens.CHOICE_DIM_ALPHA
import helium314.keyboard.settings.screens.CHOICE_TAG
import helium314.keyboard.settings.screens.ChipItem
import helium314.keyboard.settings.screens.ChipToggles
import helium314.keyboard.settings.screens.Choice
import helium314.keyboard.settings.screens.ChoiceCards
import helium314.keyboard.settings.screens.GradientSwitch
import helium314.keyboard.settings.screens.SectionCard
import helium314.keyboard.settings.screens.SettingRow
import helium314.keyboard.settings.screens.SettingSwitchRow
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.choiceDim
import helium314.keyboard.settings.screens.choiceText
import helium314.keyboard.settings.screens.flatProfileGradient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 38a: the components of the new settings (switch semantics, choice cards, chips) and a picture of them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h1400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsKitTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var row by mutableStateOf(true)
    private var own by mutableStateOf(false)
    private var choice by mutableIntStateOf(1)
    private var chipOn by mutableStateOf(true)
    private var opened = 0

    private val gestures = SettingsArea.GESTURES

    private fun show() {
        compose.setContent {
            AgentSettingsTheme {
                Column(
                    Modifier.fillMaxSize().background(AgentSettingsTokens.Background).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BackHeader({})
                    AreaHero(gestures)
                    ChoiceCards("Über die Buchstaben wischen", listOf(
                        Choice("Nichts", "Wischen über Buchstaben tut nichts", Color(0xFF3A3A42), Color(0xFF6B6B76), "none"),
                        Choice("Symbole malen", "→ ≠ € ✓ ♥ und eigene Symbole", gestures.from, gestures.to, "draw"),
                        Choice("Gestenschreiben", "Wörter wischen (braucht Bibliothek)", Color(0xFF5B8CFF), Color(0xFFA9C7FF), "gesture"),
                    ), choice, { choice = it })
                    SectionCard("Vibration & Ton") {
                        SettingSwitchRow("Vibration bei Tastendruck", "Auch für Rastpunkte und Regler", gestures.dot, row, { row = it },
                            gestures.from, gestures.to, Modifier.testTag("row_switch"))
                        SettingRow("Vibrationsdauer", "Systemstandard oder eigene", gestures.dot, { opened++ }, Modifier.testTag("row_nav"), value = "System")
                    }
                    GradientSwitch(own, { own = it }, gestures.from, gestures.to, Modifier.testTag("own_switch"), description = "RGB-Licht an oder aus")
                    ChipToggles(listOf(ChipItem("@math", chipOn, { chipOn = it }, "math"), ChipItem("@ki", false, {}, "ki")), area = SettingsArea.BAR)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun toggle(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    @Test fun switchRowsAreSwitchesForTalkBack() {
        show()
        compose.onNodeWithTag("row_switch").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        assertEquals(ToggleableState.On, toggle("row_switch"))
        compose.onNodeWithTag("row_switch").performClick()
        compose.waitForIdle()
        assertEquals(false, row)
        assertEquals(ToggleableState.Off, toggle("row_switch"))

        // a standalone switch: own label, Role.Switch, at least 44 dp to touch
        compose.onNodeWithTag("own_switch").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(hasContentDescription("RGB-Licht an oder aus"))
        val min = with(compose.density) { 44.dp.toPx() } - 1
        assertTrue(compose.onNodeWithTag("own_switch").fetchSemanticsNode().size.height >= min)
        compose.onNodeWithTag("own_switch").performClick()
        compose.waitForIdle()
        assertEquals(true, own)
        assertEquals(ToggleableState.On, toggle("own_switch"))

        // a navigation row is one button
        compose.onNodeWithTag("row_nav").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(1, opened)
    }

    @Test fun choiceCardsSelectOneAsRadioButtons() {
        show()
        compose.onNodeWithTag(CHOICE_TAG + "draw").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithTag(CHOICE_TAG + "none").assertIsNotSelected()
        compose.onNodeWithTag(CHOICE_TAG + "gesture").performClick()
        compose.waitForIdle()
        assertEquals(2, choice)
        compose.onNodeWithTag(CHOICE_TAG + "gesture").assertIsSelected()
        compose.onNodeWithTag(CHOICE_TAG + "draw").assertIsNotSelected()
    }

    @Test fun chipsAreSwitches() {
        show()
        compose.onNodeWithTag(CHIP_TAG + "math").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        assertEquals(ToggleableState.On, toggle(CHIP_TAG + "math"))
        assertEquals(ToggleableState.Off, toggle(CHIP_TAG + "ki"))
        compose.onNodeWithTag(CHIP_TAG + "math").performClick()
        compose.waitForIdle()
        assertEquals(false, chipOn)
        val min = with(compose.density) { 44.dp.toPx() } - 1
        assertTrue(compose.onNodeWithTag(CHIP_TAG + "ki").fetchSemanticsNode().size.height >= min)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun renderKitAndCheckTheChosenCard() {
        show()
        val image = capture("settings-kit")
        val chosen = compose.onNodeWithTag(CHOICE_TAG + "draw").fetchSemanticsNode().boundsInRoot
        val other = compose.onNodeWithTag(CHOICE_TAG + "gesture").fetchSemanticsNode().boundsInRoot
        val px = compose.density.density
        // white outline 2–5 dp outside the chosen card, nothing outside the others
        val outline = image.getPixel(chosen.left.toInt() + (60 * px).toInt(), (chosen.top - 3.5f * px).toInt())
        assertClose(AgentSettingsTokens.Text.toArgb(), outline, 30)
        val noOutline = image.getPixel(other.left.toInt() + (60 * px).toInt(), (other.top - 3.5f * px).toInt())
        assertClose(AgentSettingsTokens.Card.toArgb(), noOutline, 12)
        // chosen at full opacity: its gradient start; the others with the gradient dimmed over the card (choiceDim)
        val chosenStart = image.getPixel(chosen.left.toInt() + (6 * px).toInt(), chosen.bottom.toInt() - (30 * px).toInt())
        assertClose(gestures.from.toArgb(), chosenStart, 40)
        val otherStart = image.getPixel(other.left.toInt() + (6 * px).toInt(), other.bottom.toInt() - (30 * px).toInt())
        val dim = choiceDim(false, Color(0xFF5B8CFF), Color(0xFFA9C7FF), AgentSettingsTokens.Card)
        assertTrue(dim < 1f, "the other card is dimmed: $dim")
        val blended = blend(0xFF5B8CFF.toInt(), AgentSettingsTokens.Card.toArgb(), dim)
        assertClose(blended, otherStart, 40)
        // its text is drawn in full colour, not faded with the card: the darkest pixel of the hint
        val hint = compose.onNode(hasText("Wörter wischen (braucht Bibliothek)"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val darkest = (hint.left.toInt() until hint.right.toInt()).flatMap { x -> (hint.top.toInt() until hint.bottom.toInt()).map { y -> image.getPixel(x, y) } }
            .minBy { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
        assertClose(AgentSettingsTokens.OnGradient.toArgb(), darkest, 3)
        // the grey card NICHTS (owner, 2026-11-14): light text, its gradient dimmed like the others
        val grey = compose.onNodeWithTag(CHOICE_TAG + "none").fetchSemanticsNode().boundsInRoot
        val greyStart = image.getPixel(grey.left.toInt() + (6 * px).toInt(), grey.bottom.toInt() - (30 * px).toInt())
        assertClose(blend(0xFF3A3A42.toInt(), AgentSettingsTokens.Card.toArgb(), CHOICE_DIM_ALPHA), greyStart, 40)
        val greyHint = compose.onNode(hasText("Wischen über Buchstaben tut nichts"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val brightest = (greyHint.left.toInt() until greyHint.right.toInt()).flatMap { x -> (greyHint.top.toInt() until greyHint.bottom.toInt()).map { y -> image.getPixel(x, y) } }
            .maxBy { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
        assertClose(AgentSettingsTokens.Text.toArgb(), brightest, 3)
    }

    /**
     * The dark text of every coloured choice card of the new pages keeps 4.5:1 on its gradient,
     * chosen or dimmed. The pages of the kit are always dark (AgentSettingsTheme): the cards lie on
     * [AgentSettingsTokens.Card]. Gradients: ChoiceCards of Tippen, Leiste, Gesten, KI, the
     * language cards (Tasten) and the profile cards (System).
     */
    @Test fun choiceTextKeepsItsContrastOnEveryColouredGradient() {
        val card = AgentSettingsTokens.Card.toArgb()
        val gradients = listOf(
            0xFF00C2A8 to 0xFF7CF0C8, 0xFFFFC94D to 0xFFFF9F43, // Tippen: behutsam, mutig
            0xFFFFC94D to 0xFFFFE7A3, 0xFFFF9F43 to 0xFFFFD29A, // Leiste: ausklappbar, nur Vorschläge
            0xFFA78BFA to 0xFFE9D5FF, 0xFF5B8CFF to 0xFFA9C7FF, // Gesten: Symbole malen, Gestenschreiben (also language 1)
            0xFF22D3EE to 0xFFA5F3FC, 0xFFFFB547 to 0xFFFFE0A3, 0xFF818CF8 to 0xFFC7D2FE, // KI (also language 2, 3, new profile)
            0xFF94A3B8 to 0xFFE2E8F0, // System: saved profile
        )
        for ((from, to) in gradients) for (selected in listOf(true, false)) {
            val alpha = choiceDim(selected, Color(from), Color(to), Color(card))
            if (!selected) assertTrue(alpha in CHOICE_DIM_ALPHA..0.9f, "#%08X dimmed to $alpha".format(from))
            val least = (0..20).minOf { contrast(AgentSettingsTokens.OnGradient.toArgb(), blend(mix(from.toInt(), to.toInt(), it / 20f), card, alpha)) }
            println("contrast kit #%08X %s at %.2f: %.2f".format(from, if (selected) "chosen" else "not chosen", alpha, least))
            assertTrue(least >= 4.5, "#%08X chosen $selected at $alpha: contrast $least".format(from))
        }
        // every coloured gradient keeps the dark text
        for ((from, to) in gradients) assertEquals(AgentSettingsTokens.OnGradient, choiceText(Color(from), Color(to)))
        // a card that cannot be chosen fades as a whole (inactive), its gradient is not dimmed on top
        assertEquals(1f, choiceDim(false, Color(0xFF5B8CFF), Color(0xFFA9C7FF), Color(card), enabled = false))
    }

    /**
     * The grey cards (AUS, NICHTS, AUSGEBLENDET, FLACHER LOOK): dark text reaches only 1.74:1 on them
     * even at full opacity (open point of VALIDATION "Nachprüfung 37"); since the owner's decision
     * (2026-11-14) they have light text, which keeps 4.5:1 chosen and dimmed. The flat look card ends
     * in the same grey as the others: light text on the mockup's #9C9CA6 would stay below 4.5:1.
     */
    @Test fun greyCardsHaveLightTextWithEnoughContrast() {
        val card = AgentSettingsTokens.Card.toArgb()
        fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
        // AUS, NICHTS, AUSGEBLENDET; FLACHER LOOK
        val greys = listOf(0xFF3A3A42 to 0xFF6B6B76, argb(flatProfileGradient.first) to argb(flatProfileGradient.second))
        for ((from, to) in greys) {
            // dark text would fail on them: that is why they get the light one
            val dark = (0..20).minOf { contrast(AgentSettingsTokens.OnGradient.toArgb(), mix(from.toInt(), to.toInt(), it / 20f)) }
            assertTrue(dark < 4.5, "dark text on grey #%08X: $dark".format(from))
            assertEquals(AgentSettingsTokens.Text, choiceText(Color(from), Color(to)))
            for (selected in listOf(true, false)) {
                val alpha = choiceDim(selected, Color(from), Color(to), Color(card))
                if (!selected) assertEquals(CHOICE_DIM_ALPHA, alpha, "a grey card is dimmed like the others")
                val least = (0..20).minOf { contrast(AgentSettingsTokens.Text.toArgb(), blend(mix(from.toInt(), to.toInt(), it / 20f), card, alpha)) }
                println("contrast kit grey #%08X → #%08X %s at %.2f, light text: %.2f".format(from, to, if (selected) "chosen" else "not chosen", alpha, least))
                assertTrue(least >= 4.5, "light text on grey #%08X chosen $selected at $alpha: contrast $least".format(from))
            }
        }
        assertEquals(Color(0xFF6B6B76), flatProfileGradient.second)
        // the mockup's lighter end of the flat look would not do with light text
        val mockupEnd = contrast(AgentSettingsTokens.Text.toArgb(), 0xFF9C9CA6.toInt())
        assertTrue(mockupEnd < 4.5, "light text on #9C9CA6: $mockupEnd")
        // dark text keeps choosing full opacity where nothing reaches 4.5:1 (unchanged rule for dark text)
        assertEquals(1f, choiceDim(false, Color(0xFF3A3A42), Color(0xFF6B6B76), Color(card), text = AgentSettingsTokens.OnGradient))
    }

    /** sRGB channels from [from] to [to] at [t], as the gradient shader mixes them. */
    private fun mix(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = ((from shr shift and 0xFF) + ((to shr shift and 0xFF) - (from shr shift and 0xFF)) * t).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** WCAG 2 contrast ratio, written out here independently of the app's `contrastRatio`. */
    private fun contrast(a: Int, b: Int): Double {
        fun luminance(c: Int): Double {
            fun ch(shift: Int) = ((c shr shift and 0xFF) / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
        }
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun blend(top: Int, bottom: Int, alpha: Float): Int {
        fun ch(shift: Int) = ((top shr shift and 0xFF) * alpha + (bottom shr shift and 0xFF) * (1 - alpha)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun assertClose(expected: Int, actual: Int, tolerance: Int) {
        val d = listOf(16, 8, 0).maxOf { shift -> kotlin.math.abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF)) }
        assertTrue(d <= tolerance, "expected ~#%08X, got #%08X".format(expected, actual))
    }

    // captureToImage waits for a redraw that never comes in Robolectric (Fallstrick P15)
    private fun capture(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val file = File("build/agent-previews/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
