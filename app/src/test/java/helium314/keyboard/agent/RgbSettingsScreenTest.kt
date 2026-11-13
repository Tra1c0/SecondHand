// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings.Global
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    @Test fun framesRedrawOnlyThePreview() {
        RgbLighting.write(prefs, RgbSettings(enabled = true, periodMs = 2000))
        compose.mainClock.autoAdvance = false
        compose.setContent { Theme(true) { AgentRgbScreen {} } }
        compose.mainClock.advanceTimeBy(100)
        val first = keyPixel()
        val before = helium314.keyboard.settings.screens.optionsCompositions
        compose.mainClock.advanceTimeBy(1000) // about 30 frames
        assertNotEquals(first, keyPixel(), "the preview moved")
        val recomposed = helium314.keyboard.settings.screens.optionsCompositions - before
        assertTrue(recomposed <= 1, "the option list was recomposed $recomposed times during one second of frames")
    }

    @Test fun aPreviewWithoutWidthDoesNotCrash() {
        val engine = dev.agentkeyboard.core.RgbEngines(RgbSettings(enabled = true, pattern = RgbPattern.CIRCLE,
            layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND, RgbLayer.LABEL), areas = RgbArea.entries.toSet()))
        compose.setContent {
            Theme(true) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val design = androidx.compose.runtime.remember { MyDesigns.newDesign(context, "") }
                androidx.compose.foundation.layout.Row {
                    helium314.keyboard.settings.screens.DesignPreview(design, null,
                        androidx.compose.ui.Modifier.width(androidx.compose.ui.unit.Dp(0f)),
                        light = helium314.keyboard.settings.screens.PreviewLight(engine, { 1000L }))
                }
            }
        }
        compose.waitForIdle()
        screenshot() // draws the zero-width preview
        assertEquals(0, compose.onNodeWithTag("agent_design_preview").fetchSemanticsNode().size.width)
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

    // --- stage 29 ---

    /** Position of curve point ([x], [y]) inside the curve editor node, in its own pixels. */
    private fun curveAt(x: Float, y: Float): androidx.compose.ui.geometry.Offset {
        val node = compose.onNodeWithTag("agent_rgb_curve").fetchSemanticsNode()
        val pad = 22f * app.resources.displayMetrics.density
        return androidx.compose.ui.geometry.Offset(pad + x * (node.size.width - 2 * pad), pad + (1 - y) * (node.size.height - 2 * pad))
    }

    private fun curve() = saved.curve

    @Test fun theCurveIsEditedByTouch() {
        RgbLighting.write(prefs, RgbSettings(enabled = true))
        stillPage()
        compose.onNodeWithTag("agent_rgb_curve").performScrollTo()
        compose.onNodeWithTag("agent_rgb_wave_SAWTOOTH").assertIsSelected()
        assertEquals(2, curve().points.size)
        // tap on an empty spot: a new point exactly there
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput { click(curveAt(0.5f, 0.85f)) }
        compose.waitForIdle()
        assertEquals(3, curve().points.size)
        assertEquals(0.5f, curve().points[1].x, 0.02f)
        assertEquals(0.85f, curve().points[1].y, 0.02f)
        compose.onNodeWithTag("agent_rgb_wave_SAWTOOTH").assertIsNotSelected()
        compose.onNode(hasText("Eigene Kurve")).assertExists()
        // drag it: saved when the finger lifts, ends keep their place
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput {
            down(curveAt(0.5f, 0.85f)); moveTo(curveAt(0.4f, 0.6f)); moveTo(curveAt(0.3f, 0.2f)); up()
        }
        compose.waitForIdle()
        assertEquals(0.3f, curve().points[1].x, 0.02f)
        assertEquals(0.2f, curve().points[1].y, 0.02f)
        assertEquals(0f, curve().points.first().x)
        assertEquals(1f, curve().points.last().x)
        // the left end moves only up and down
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput { down(curveAt(0f, 0f)); moveTo(curveAt(0.2f, 0.4f)); moveTo(curveAt(0.25f, 0.5f)); up() }
        compose.waitForIdle()
        assertEquals(0f, curve().points.first().x)
        assertEquals(0.5f, curve().points.first().y, 0.02f)
        // long press removes a middle point, never an end
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput { longClick(curveAt(0.3f, 0.2f)) }
        compose.waitForIdle()
        assertEquals(2, curve().points.size)
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput { longClick(curveAt(1f, 1f)) }
        compose.waitForIdle()
        assertEquals(2, curve().points.size, "the end stays")
        // "+" adds a point on the curve in the widest gap and selects it; dragged out of the area it is gone
        click("agent_rgb_curve_add")
        assertEquals(3, curve().points.size)
        val added = curve().points[1]
        assertEquals(0.5f, added.x, 0.001f)
        compose.onNodeWithTag("agent_rgb_curve_remove").assertIsEnabled()
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput {
            down(curveAt(added.x, added.y)); moveTo(curveAt(added.x, -0.4f)); moveTo(curveAt(added.x, -1.2f)); up()
        }
        compose.waitForIdle()
        assertEquals(2, curve().points.size, "dragged out: removed")
        // the remove button acts on the selected point
        click("agent_rgb_curve_add")
        click("agent_rgb_curve_remove")
        assertEquals(2, curve().points.size)
        compose.onNodeWithTag("agent_rgb_curve_remove").assertIsNotEnabled()
        // presets load their points; steps get their slider
        click("agent_rgb_wave_PULSE")
        assertEquals(dev.agentkeyboard.core.RgbCurve.preset(dev.agentkeyboard.core.RgbWaveform.PULSE), curve())
        compose.onNodeWithTag("agent_rgb_steps").assertDoesNotExist()
        click("agent_rgb_wave_STEPS")
        assertEquals(4, dev.agentkeyboard.core.RgbCurve.stepCount(curve()))
        compose.onNodeWithTag("agent_rgb_steps").assertExists()
        // the keyboard reads exactly what the page wrote
        assertEquals(saved, RgbSettings.parse(prefs.getString(RgbLighting.PREF, null)))
        saveNode("agent_rgb_options", "rgb-settings-curve.png")
    }

    @Test fun everyElementGetsItsOwnLook() {
        RgbLighting.write(prefs, RgbSettings(enabled = true))
        stillPage()
        compose.onNodeWithTag("agent_rgb_target_KEYS").assertDoesNotExist()
        click("agent_rgb_same_everywhere")
        assertTrue(saved.separate)
        click("agent_rgb_target_BACKGROUND")
        // the background is not lit yet: the page says so
        compose.onNode(hasText("„Hintergrund“ leuchtet gerade nicht.", substring = true)).assertExists()
        click("agent_rgb_pattern_INK")
        click("agent_rgb_wave_SINE")
        assertEquals(RgbPattern.INK, saved.forTarget(dev.agentkeyboard.core.RgbTarget.BACKGROUND).pattern)
        assertEquals(dev.agentkeyboard.core.RgbCurve.preset(dev.agentkeyboard.core.RgbWaveform.SINE), saved.forTarget(dev.agentkeyboard.core.RgbTarget.BACKGROUND).curve)
        assertEquals(RgbPattern.WAVE_HORIZONTAL, saved.forTarget(dev.agentkeyboard.core.RgbTarget.KEYS).pattern, "the keys keep theirs")
        click("agent_rgb_target_KEYS")
        compose.onNodeWithTag("agent_rgb_pattern_WAVE_HORIZONTAL").assertIsSelected()
        click("agent_rgb_pattern_LED")
        assertEquals(RgbPattern.LED, saved.forTarget(dev.agentkeyboard.core.RgbTarget.KEYS).pattern)
        // back to one look for everything: the shared one (the own looks stay stored)
        click("agent_rgb_same_everywhere")
        assertFalse(saved.separate)
        assertEquals(RgbPattern.WAVE_HORIZONTAL, saved.forTarget(dev.agentkeyboard.core.RgbTarget.BACKGROUND).pattern)
        click("agent_rgb_pattern_TOPO")
        assertEquals(RgbPattern.TOPO, saved.pattern)
        click("agent_rgb_solid_keys")
        assertTrue(saved.solidKeys)
    }

    @Test fun theNewPatternsMoveInThePreview() {
        val previews = File("build/agent-previews").apply { mkdirs() }
        // one page, opened again for each pattern (the page reads the preference when it opens)
        var shown by androidx.compose.runtime.mutableStateOf(RgbPattern.INK)
        compose.mainClock.autoAdvance = false
        fun write(p: RgbPattern) = RgbLighting.write(prefs, RgbSettings(enabled = true, pattern = p, periodMs = 3000,
            layers = setOf(RgbLayer.KEY_BACKGROUND, RgbLayer.KEYBOARD_BACKGROUND), areas = RgbArea.entries.toSet()))
        write(shown)
        compose.setContent { Theme(true) { androidx.compose.runtime.key(shown) { AgentRgbScreen {} } } }
        for (pattern in RgbPattern.entries.filter { it.textured }) {
            write(pattern)
            shown = pattern
            compose.mainClock.advanceTimeBy(1500)
            val first = previewPicture()
            compose.mainClock.advanceTimeBy(600)
            val later = previewPicture()
            assertFalse(first.sameAs(later), "$pattern moves")
            File(previews, "rgb-preview-${pattern.name.lowercase()}.png").outputStream().use { later.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        saveNode("agent_rgb_options", "rgb-settings-top.png")
    }

    private fun previewPicture(): Bitmap {
        val bounds = compose.onNodeWithTag("agent_rgb_preview").fetchSemanticsNode().boundsInRoot
        val full = screenshot()
        return Bitmap.createBitmap(full, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt())
    }

    private fun saveNode(@Suppress("SameParameterValue") tag: String, name: String) {
        val full = screenshot()
        File("build/agent-previews").mkdirs()
        File("build/agent-previews/$name").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag(tag).assertExists()
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
