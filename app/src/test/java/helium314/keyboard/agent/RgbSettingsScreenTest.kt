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
import kotlin.test.assertNotNull
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
        // long press only selects (review 29: a resting finger must not delete); removing is the button's
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput { longClick(curveAt(0.3f, 0.2f)) }
        compose.waitForIdle()
        assertEquals(3, curve().points.size, "a long press removes nothing")
        compose.onNodeWithTag("agent_rgb_curve").assert(SemanticsMatcher("point 2 selected") {
            it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { null }?.startsWith("Punkt 2 gewählt") == true
        })
        compose.onNodeWithTag("agent_rgb_curve_remove").assertIsEnabled()
        click("agent_rgb_curve_remove")
        assertEquals(2, curve().points.size)
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput { longClick(curveAt(1f, 1f)) }
        compose.waitForIdle()
        assertEquals(2, curve().points.size, "the end stays")
        compose.onNodeWithTag("agent_rgb_curve_remove").assertIsNotEnabled()
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

    /** Review 29 (L6): TalkBack moves and removes the selected point through custom actions. */
    @OptIn(ExperimentalTestApi::class)
    @Test fun theSelectedPointHasTalkBackActions() {
        RgbLighting.write(prefs, RgbSettings(enabled = true))
        stillPage()
        val editor = compose.onNodeWithTag("agent_rgb_curve")
        editor.performScrollTo()
        fun actions() = editor.fetchSemanticsNode().config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.CustomActions) { null }
            ?.map { it.label }.orEmpty()
        assertEquals(emptyList(), actions(), "nothing selected: no actions")
        click("agent_rgb_curve_add") // a middle point at 0.5, selected
        val start = curve().points[1]
        assertEquals(listOf("Punkt nach oben", "Punkt nach unten", "Punkt nach links", "Punkt nach rechts", "Punkt entfernen"), actions())
        editor.performCustomAccessibilityActionWithLabel("Punkt nach oben")
        compose.waitForIdle()
        assertEquals(start.y + 0.05f, curve().points[1].y, 0.002f)
        editor.performCustomAccessibilityActionWithLabel("Punkt nach rechts")
        compose.waitForIdle()
        editor.performCustomAccessibilityActionWithLabel("Punkt nach rechts")
        compose.waitForIdle()
        assertEquals(start.x + 0.1f, curve().points[1].x, 0.002f)
        editor.performCustomAccessibilityActionWithLabel("Punkt nach links")
        compose.waitForIdle()
        editor.performCustomAccessibilityActionWithLabel("Punkt nach unten")
        compose.waitForIdle()
        editor.performCustomAccessibilityActionWithLabel("Punkt nach unten")
        compose.waitForIdle()
        assertEquals(start.x + 0.05f, curve().points[1].x, 0.002f)
        assertEquals(start.y - 0.05f, curve().points[1].y, 0.002f)
        editor.performCustomAccessibilityActionWithLabel("Punkt entfernen")
        compose.waitForIdle()
        assertEquals(2, curve().points.size)
        assertEquals(emptyList(), actions(), "removed: nothing selected")
        // an end moves only up and down and cannot be removed
        editor.performTouchInput { click(curveAt(0f, 0f)) }
        compose.waitForIdle()
        assertEquals(listOf("Punkt nach oben", "Punkt nach unten"), actions())
        editor.performCustomAccessibilityActionWithLabel("Punkt nach oben")
        compose.waitForIdle()
        assertEquals(0f, curve().points.first().x)
        assertEquals(0.05f, curve().points.first().y, 0.002f)
    }

    /** Review 29 (M2): gallery pictures depend on pattern and colours only and are made in the background. */
    @Test fun theGalleryIsNotRebuiltWhileACurvePointIsDragged() {
        RgbLighting.write(prefs, RgbSettings(enabled = true))
        RgbTiles.clear()
        val before = helium314.keyboard.settings.screens.galleryPictures.get()
        stillPage()
        val patterns = RgbPattern.entries.size
        waitUntil("the first pictures") { helium314.keyboard.settings.screens.galleryPictures.get() - before >= patterns }
        RgbTiles.awaitIdle()
        waitUntil("every tile") { RgbTiles.cached() == RgbTiles.TILED.toSet() }
        val made = helium314.keyboard.settings.screens.galleryPictures.get()
        val tiles = RgbTiles.builds
        // drag a curve point (many intermediate curves), then let any delayed work run
        click("agent_rgb_curve_add")
        val p = curve().points[1]
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput {
            down(curveAt(p.x, p.y)); for (i in 1..12) moveTo(curveAt(p.x - i * 0.01f, p.y + i * 0.02f)); up()
        }
        compose.waitForIdle()
        assertNotEquals(p, curve().points[1], "the point moved")
        compose.mainClock.advanceTimeBy(2000)
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals(made, helium314.keyboard.settings.screens.galleryPictures.get(), "no gallery picture made for a curve change")
        // other colours: new pictures, but no tile made again
        click("agent_rgb_palette_own")
        compose.mainClock.advanceTimeBy(2000)
        waitUntil("pictures in the own colours") { helium314.keyboard.settings.screens.galleryPictures.get() - made >= patterns }
        assertEquals(tiles, RgbTiles.builds, "the tiles stay cached")
    }

    /**
     * Build-14 lag: dragging a curve point recomposes neither the page nor the gallery per move;
     * only the editor redraws and the preview follows at most once per frame.
     */
    @Test fun draggingACurvePointDoesNotRecomposeThePage() {
        RgbLighting.write(prefs, RgbSettings(enabled = true))
        stillPage()
        click("agent_rgb_curve_add")
        val p = curve().points[1]
        val pages = helium314.keyboard.settings.screens.optionsCompositions
        val engines = helium314.keyboard.settings.screens.previewEngines
        val startMs = compose.mainClock.currentTime
        val moves = 24
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput {
            down(curveAt(p.x, p.y))
            for (i in 1..moves / 2) moveTo(curveAt(p.x - i * 0.005f, p.y + i * 0.01f))
        }
        compose.waitForIdle()
        // still held: the preview shows the dragged curve, the page was not composed for it
        assertTrue(helium314.keyboard.settings.screens.previewEngines > engines, "the preview follows the finger")
        // (the point was already selected by "+": the drag changes nothing the page shows)
        assertEquals(0, helium314.keyboard.settings.screens.optionsCompositions - pages, "no page composition while dragging")
        assertEquals(p, curve().points[1], "nothing saved before lifting")
        compose.onNodeWithTag("agent_rgb_curve").performTouchInput {
            for (i in moves / 2 + 1..moves) moveTo(curveAt(p.x - i * 0.005f, p.y + i * 0.01f))
            up()
        }
        compose.waitForIdle()
        assertEquals(p.x - moves * 0.005f, curve().points[1].x, 0.02f)
        val pageCompositions = helium314.keyboard.settings.screens.optionsCompositions - pages
        val previewChanges = helium314.keyboard.settings.screens.previewEngines - engines
        val frames = ((compose.mainClock.currentTime - startMs) / 16 + 1).toInt()
        println("drag of $moves moves over $frames frames: page composed $pageCompositions times, preview engines $previewChanges")
        // only saving on lifting composes the page
        assertTrue(pageCompositions <= 1, "the page was composed $pageCompositions times for $moves moves")
        assertTrue(previewChanges <= frames + 1, "the preview made $previewChanges engines in $frames frames")
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < end, "timed out waiting for $what")
            Thread.sleep(20)
            compose.mainClock.advanceTimeBy(50)
            compose.waitForIdle()
        }
    }

    /**
     * Review 29 (L3): the light in the preview is the 48-column grid filtered bilinearly (±4 per
     * channel), not blocks. API 33+, where the shader's own filter mode counts (Robolectric's SDK 28
     * draws a bitmap shader unfiltered).
     */
    @Config(sdk = [36])
    @Test fun thePreviewLightIsSmoothLikeTheGrid() {
        RgbLighting.write(prefs, RgbSettings(enabled = true, waveWidth = 2f, layers = setOf(RgbLayer.KEY_BACKGROUND),
            areas = RgbArea.entries.toSet()))
        stillPage()
        val keys = assertNotNull(helium314.keyboard.settings.screens.lastPreviewLight).keys
        val bounds = compose.onNodeWithTag("agent_rgb_preview").fetchSemanticsNode().boundsInRoot
        val picture = screenshot()
        val density = app.resources.displayMetrics.density
        // the space bar of the preview (bottom row: 1.5 + 1 + 5 + 1 + 1.5 units of 11), its left fifth, middle height
        val width = bounds.width
        val strip = 40 * density
        val padding = 6 * density
        val rowHeight = (bounds.height - strip - 2 * padding) / 4
        val unit = (width - 2 * padding) / 11
        val spaceLeft = (width - 10 * unit) / 2 + 2.5f * unit
        val y = (strip + padding + 3.5f * rowHeight).roundToInt()
        val cols = keys.gridColumns
        val rows = keys.gridRows
        var compared = 0
        var differsFromBlocks = 0
        var worst = 0
        var worstBlocks = 0
        for (x in (spaceLeft + 0.2f * unit).roundToInt()..(spaceLeft + 1.2f * unit).roundToInt()) {
            val gx = (x + 0.5f - keys.areaLeft) / keys.areaWidth * cols - 0.5f
            val gy = (y + 0.5f - keys.areaTop) / keys.areaHeight * rows - 0.5f
            val c0 = kotlin.math.floor(gx).toInt()
            val r0 = kotlin.math.floor(gy).toInt()
            val fx = gx - c0
            val fy = gy - r0
            for (c in c0..c0 + 1) for (r in r0..r0 + 1) assertEquals(255, keys.gridPixel(c, r) ushr 24, "opaque light")
            fun ch(color: Int, shift: Int) = (color shr shift) and 0xFF
            fun expected(shift: Int): Int {
                val top = ch(keys.gridPixel(c0, r0), shift) * (1 - fx) + ch(keys.gridPixel(c0 + 1, r0), shift) * fx
                val bottom = ch(keys.gridPixel(c0, r0 + 1), shift) * (1 - fx) + ch(keys.gridPixel(c0 + 1, r0 + 1), shift) * fx
                return (top * (1 - fy) + bottom * fy).roundToInt()
            }
            val actual = picture.getPixel((bounds.left + x).roundToInt(), (bounds.top + y).roundToInt())
            worst = maxOf(worst, listOf(16, 8, 0).maxOf { kotlin.math.abs(expected(it) - ch(actual, it)) })
            for (shift in listOf(16, 8, 0))
                assertTrue(kotlin.math.abs(expected(shift) - ch(actual, shift)) <= 4,
                    "at $x,$y channel $shift: expected ${expected(shift)} was ${ch(actual, shift)} (bilinear)")
            // nearest-neighbour blocks would show the cell's own value
            val nearest = keys.gridPixel(Math.round(gx), Math.round(gy))
            val blockError = listOf(16, 8, 0).maxOf { kotlin.math.abs(ch(nearest, it) - ch(actual, it)) }
            worstBlocks = maxOf(worstBlocks, blockError)
            if (blockError > 8) differsFromBlocks++
            compared++
        }
        println("preview vs bilinear grid: largest difference $worst over $compared pixels; vs blocks up to $worstBlocks ($differsFromBlocks differ by more than 8)")
        assertTrue(compared >= 20, "compared $compared pixels")
        assertTrue(differsFromBlocks >= compared / 4, "the light runs between the cells: $differsFromBlocks of $compared differ from blocks")
        save(picture, "rgb-preview-smooth.png")
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

    @Config(sdk = [36]) // API 33+: the shaders filter as on a phone (P105)
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
            // the tile is made in the background (review 29, M1): the pictures show it
            RgbTiles.awaitIdle()
            compose.mainClock.advanceTimeBy(100)
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
