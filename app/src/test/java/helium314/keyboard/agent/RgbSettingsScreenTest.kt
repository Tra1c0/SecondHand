// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings.Global
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.agentkeyboard.core.RgbArea
import dev.agentkeyboard.core.RgbLayer
import dev.agentkeyboard.core.RgbPattern
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTapReaction
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentDesignScreen
import helium314.keyboard.settings.screens.AgentRgbScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The "RGB lighting" page (stage 26): every change is saved at once, the preview moves and reacts to taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RgbSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private val saved get() = RgbLighting.read(prefs)

    @Before @After fun clear() {
        prefs.edit().clear().commit()
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun stillPage() {
        // without animations the preview has no frame loop, so the test clock can idle
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { Theme(true) { AgentRgbScreen {} } }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun theDesignPageLeadsHere() {
        compose.setContent { Theme(true) { AgentDesignScreen {} } }
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_rgb").performScrollTo().assertExists()
        compose.onNode(hasText("RGB-Beleuchtung")).assertExists()
    }

    @Test fun everyOptionIsSavedAtOnce() {
        stillPage()
        assertFalse(saved.enabled)
        assertEquals(null, prefs.getString(RgbLighting.PREF, null), "default stays unwritten")
        click("agent_rgb_enabled")
        assertTrue(saved.enabled)
        click("agent_rgb_pattern_CIRCLE")
        assertEquals(RgbPattern.CIRCLE, saved.pattern)
        click("agent_rgb_wave_STEPS")
        compose.onNodeWithTag("agent_rgb_steps").assertExists()
        click("agent_rgb_reverse")
        assertTrue(saved.reverse)
        click("agent_rgb_layer_LABEL")
        assertEquals(setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.LABEL), saved.layers)
        click("agent_rgb_layer_KEY_BACKGROUND")
        assertEquals(setOf(RgbLayer.LABEL), saved.layers)
        click("agent_rgb_area_SUGGESTIONS")
        assertTrue(RgbArea.SUGGESTIONS in saved.areas)
        click("agent_rgb_palette_own")
        assertFalse(saved.rainbow)
        compose.onNodeWithTag("agent_rgb_color_2").assertExists()
        click("agent_rgb_color_add")
        assertEquals(4, saved.palette.size)
        click("agent_rgb_color_remove")
        click("agent_rgb_color_remove")
        assertEquals(2, saved.palette.size)
        compose.onNodeWithTag("agent_rgb_color_remove").assertDoesNotExist()
        click("agent_rgb_tap_RIPPLE")
        assertEquals(RgbTapReaction.RIPPLE, saved.tapReaction)
        click("agent_rgb_reactive")
        assertTrue(saved.reactiveOnly)
        click("agent_rgb_fps_60")
        assertEquals(60, saved.fps)
        click("agent_rgb_power_save")
        assertFalse(saved.pauseInPowerSave)
        compose.onNode(hasText("Android-Animationen sind aus: Die Beleuchtung bleibt stehen.")).assertExists()
        // what the page wrote is exactly what the keyboard reads
        assertEquals(saved, RgbSettings.parse(prefs.getString(RgbLighting.PREF, null)))
    }

    @Test fun thePreviewMovesAndReactsToTaps() {
        RgbLighting.write(prefs, RgbSettings(enabled = true, periodMs = 2000))
        compose.mainClock.autoAdvance = false
        compose.setContent { Theme(true) { AgentRgbScreen {} } }
        compose.mainClock.advanceTimeBy(100)
        val first = keyPixel()
        compose.mainClock.advanceTimeBy(700)
        val later = keyPixel()
        assertNotEquals(first, later, "the light moves")
        save(screenshot(), "rgb-preview.png")
    }

    @Test fun reactivePreviewLightsUpWhereTapped() {
        RgbLighting.write(prefs, RgbSettings(enabled = true, tapReaction = RgbTapReaction.FLASH, reactiveOnly = true, glowMs = 5000))
        compose.mainClock.autoAdvance = false
        compose.setContent { Theme(true) { AgentRgbScreen {} } }
        compose.mainClock.advanceTimeBy(100)
        val before = keyPixel()
        val node = compose.onNodeWithTag("agent_rgb_preview")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        node.performTouchInput { click(androidx.compose.ui.geometry.Offset(KEY_X * bounds.width, bounds.height - KEY_FROM_BOTTOM * density)) }
        compose.mainClock.advanceTimeBy(100)
        val after = keyPixel()
        assertNotEquals(before, after, "tapped key lights up")
    }

    // a point inside the "y" key of the third row: 3/22 of the width, 2.5 rows (plus padding) from the bottom
    private fun keyPixel(): Int {
        val bounds = compose.onNodeWithTag("agent_rgb_preview").fetchSemanticsNode().boundsInRoot
        val density = app.resources.displayMetrics.density
        val bitmap = screenshot()
        return bitmap.getPixel((bounds.left + bounds.width * KEY_X).roundToInt(), (bounds.bottom - KEY_FROM_BOTTOM * density).roundToInt())
    }

    private fun screenshot(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/rgb-lighting/$name").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** Second letter of the third row ("y" after shift), towards its lower left, away from the label. */
        const val KEY_X = 0.205f
        /** dp from the bottom of the preview: padding 6 + a row and a quarter of 50 */
        const val KEY_FROM_BOTTOM = 6f + 50f + 12f
    }
}
