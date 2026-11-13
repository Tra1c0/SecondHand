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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import helium314.keyboard.settings.screens.AgentSettingsTheme
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.AreaHero
import helium314.keyboard.settings.screens.BackHeader
import helium314.keyboard.settings.screens.CHIP_TAG
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
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
        // chosen at full opacity: its gradient start; the others at 55 % over the card
        val chosenStart = image.getPixel(chosen.left.toInt() + (6 * px).toInt(), chosen.bottom.toInt() - (30 * px).toInt())
        assertClose(gestures.from.toArgb(), chosenStart, 40)
        val otherStart = image.getPixel(other.left.toInt() + (6 * px).toInt(), other.bottom.toInt() - (30 * px).toInt())
        val blended = blend(0xFF5B8CFF.toInt(), AgentSettingsTokens.Card.toArgb(), 0.55f)
        assertClose(blended, otherStart, 40)
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
