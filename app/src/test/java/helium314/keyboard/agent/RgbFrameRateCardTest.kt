// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.PowerManager
import android.provider.Settings.Global
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import dev.agentkeyboard.core.RgbFramePacer
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentRgbScreen
import helium314.keyboard.settings.screens.FRAME_RATES
import helium314.keyboard.settings.screens.FRAME_RATE_AREA_TAG
import helium314.keyboard.settings.screens.FRAME_RATE_TAG
import helium314.keyboard.settings.screens.FrameRateCard
import helium314.keyboard.settings.screens.LIGHT_TRIP_MS
import helium314.keyboard.settings.screens.STILL_PHASE
import helium314.keyboard.settings.screens.frameRateCompositions
import helium314.keyboard.settings.screens.frameRateRunning
import helium314.keyboard.settings.screens.frameRateTrips
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 37: the frame rate of the RGB page as two colour areas with a moving light. Tapping writes
 * the same preference as the chips before; the light moves only on screen with motion allowed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w390dp-h1400dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RgbFrameRateCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()
    private val saved get() = RgbLighting.read(prefs)
    private var chosen by mutableIntStateOf(30)

    @Before @After fun clear() {
        prefs.edit().clear().commit()
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(false)
        frameRateRunning = false
        frameRateTrips.fill(-1f)
    }

    /** The whole RGB page without motion (the test clock can idle, P62). */
    private fun stillPage() {
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent { Theme(true) { AgentRgbScreen {} } }
        compose.waitForIdle()
        // to the end of the options: the whole card (the last one) is on screen
        compose.onNodeWithTag("agent_rgb_options").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
    }

    /** The card alone on a dark page; [above] dp of space before it (off screen when large). */
    private fun card(above: Int = 0, scroll: ScrollState? = null) {
        compose.setContent {
            Theme(true) {
                val state = scroll ?: androidx.compose.foundation.rememberScrollState()
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(state).padding(16.dp)) {
                    Spacer(Modifier.height(above.dp))
                    FrameRateCard(chosen, { chosen = it })
                }
            }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /**
     * [ms] of frames as on a phone: what is posted runs between them, and each frame is laid out and
     * drawn. Robolectric does neither by itself when the test clock moves (P198), so a scroll would
     * never reach `onGloballyPositioned` and the light would not be drawn.
     */
    private fun frames(ms: Long) {
        var left = ms
        while (left > 0) {
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(minOf(FRAME_MS, left))
            compose.runOnUiThread { compose.activity.window.decorView.draw(Canvas(pixel)) }
            left -= FRAME_MS
        }
        ShadowLooper.idleMainLooper()
    }

    /** Frames are drawn into one pixel: layout and every draw call run, nothing is kept. */
    private val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    @Test fun theCardShowsBothRatesAndKeepsTheEnergySaverRow() {
        stillPage()
        compose.onNodeWithTag(FRAME_RATE_TAG).assertExistsWith(hasText("Bildwiederholrate"))
        compose.onNode(hasText("Bildwiederholrate")).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        for (rate in FRAME_RATES) {
            compose.onNodeWithTag(FRAME_RATE_AREA_TAG + rate)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assert(hasContentDescription("$rate Hertz"))
        }
        compose.onNode(hasText("30 Bilder/s · spart Akku")).assertExists()
        compose.onNode(hasText("60 Bilder/s · flüssiger")).assertExists()
        // the default rate is chosen
        compose.onNodeWithTag(FRAME_RATE_AREA_TAG + 30).assertIsSelected()
        compose.onNodeWithTag(FRAME_RATE_AREA_TAG + 60).assertIsNotSelected()
        // "Im Energiesparmodus anhalten" stays, inside the card, below both areas
        val card = bounds(FRAME_RATE_TAG)
        val sixty = bounds(FRAME_RATE_AREA_TAG + 60)
        val thirty = bounds(FRAME_RATE_AREA_TAG + 30)
        val saver = bounds("agent_rgb_power_save")
        assertTrue(thirty.bottom <= sixty.top, "30 Hz above 60 Hz")
        assertTrue(saver.top >= sixty.bottom && saver.bottom <= card.bottom, "energy saver row below the areas, inside the card")
        compose.onNode(hasText("Im Energiesparmodus anhalten")).assertExists()
        // both areas side by side would be too narrow at 390 dp: stacked, each the full width of the card
        assertTrue(thirty.width > card.width * 0.8f)
        // touch targets
        val min = with(compose.density) { 44.dp.toPx() } - 1
        for (tag in listOf(FRAME_RATE_AREA_TAG + 30, FRAME_RATE_AREA_TAG + 60, "agent_rgb_power_save"))
            assertTrue(bounds(tag).height >= min && bounds(tag).width >= min, "$tag at least 44 dp")
    }

    @Test fun tappingWritesTheRealPreferenceAndTheSelectionFollows() {
        stillPage()
        assertNull(prefs.getString(RgbLighting.PREF, null), "default stays unwritten")
        click(FRAME_RATE_AREA_TAG + 60)
        // the same preference and value as the chips before, nothing else changed
        assertEquals(RgbSettings.DEFAULT.copy(fps = 60), saved)
        assertEquals(60, RgbSettings.parse(prefs.getString(RgbLighting.PREF, null)).fps)
        assertEquals(1_000_000_000L / 60, RgbFramePacer(saved.fps).intervalNanos, "the keyboard paces at 60 frames/s")
        compose.onNodeWithTag(FRAME_RATE_AREA_TAG + 60).assertIsSelected()
        compose.onNodeWithTag(FRAME_RATE_AREA_TAG + 30).assertIsNotSelected()
        click(FRAME_RATE_AREA_TAG + 30)
        assertEquals(30, saved.fps)
        assertNull(prefs.getString(RgbLighting.PREF, null), "back to the default: the preference is removed as before")
        compose.onNodeWithTag(FRAME_RATE_AREA_TAG + 30).assertIsSelected()
        compose.onNodeWithTag(FRAME_RATE_AREA_TAG + 60).assertIsNotSelected()
        // the energy saver switch below still writes its option
        click("agent_rgb_power_save")
        assertEquals(RgbSettings.DEFAULT.copy(pauseInPowerSave = false), saved)
    }

    @Test fun theLightsMoveAndSixtyIsTwiceAsFast() {
        compose.mainClock.autoAdvance = false // infinite animations only run with a manual clock (P198)
        card()
        frames(200)
        assertTrue(frameRateRunning, "on screen with animations on: the light moves")
        val composed = frameRateCompositions
        val picture = shot()
        val first = trips()
        frames(300)
        val later = shot()
        val thirty = forward(first[0], trips()[0])
        val sixty = forward(first[1], trips()[1])
        val expected = 300f / LIGHT_TRIP_MS
        assertTrue(abs(thirty - expected) < 0.02f, "30 Hz light moved $thirty of its trip in 300 ms, expected $expected")
        assertTrue(abs(sixty - 2 * thirty) < 0.02f, "60 Hz light moved $sixty, twice $thirty")
        assertTrue(!picture.sameAs(later), "the light moved on screen")
        assertEquals(composed, frameRateCompositions, "frames draw the light and compose nothing")
    }

    @Test fun withoutAnimationsTheLightsStandStill() {
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.mainClock.autoAdvance = false
        card()
        frames(200)
        assertFalse(frameRateRunning, "Android animations off: no frames")
        val picture = shot()
        frames(1000)
        assertTrue(picture.sameAs(shot()), "the picture stands still")
        assertStill()
    }

    @Test fun theBatterySaverStopsTheLightUntilItEnds() {
        val power = shadowOf(app.getSystemService(PowerManager::class.java))
        power.setIsPowerSaveMode(true)
        compose.mainClock.autoAdvance = false
        card()
        frames(200)
        assertFalse(frameRateRunning, "battery saver: no frames")
        val picture = shot()
        frames(1000)
        assertTrue(picture.sameAs(shot()), "the picture stands still")
        assertStill()
        // the saver ends while the card is shown: the light goes on
        power.setIsPowerSaveMode(false)
        app.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        ShadowLooper.idleMainLooper()
        frames(100)
        assertTrue(frameRateRunning, "moving again after the battery saver")
        shot()
        val first = trips()
        frames(500)
        shot()
        assertTrue(forward(first[0], trips()[0]) > 0.1f, "the 30 Hz light moved")
    }

    @Test fun offScreenTheLightStops() {
        val scroll = ScrollState(0)
        compose.mainClock.autoAdvance = false
        card(above = 3000, scroll = scroll)
        frames(200)
        assertFalse(frameRateRunning, "scrolled out of view: no frames")
        frames(1000)
        shot()
        assertStill()
        // scrolled into view it moves
        compose.runOnUiThread { scroll.dispatchRawDelta(with(compose.density) { 2600.dp.toPx() }) }
        frames(100)
        assertTrue(frameRateRunning, "on screen: the light moves")
        shot()
        val first = trips()
        frames(500)
        shot()
        assertTrue(forward(first[0], trips()[0]) > 0.1f, "the 30 Hz light moved")
        // and out of view it stops again
        compose.runOnUiThread { scroll.dispatchRawDelta(-with(compose.density) { 2600.dp.toPx() }) }
        frames(100)
        assertFalse(frameRateRunning, "scrolled away again: no frames")
    }

    @Test fun renderTheCardAndCheckTheChosenArea() {
        stillPage()
        val image = shot()
        val px = compose.density.density
        val card = bounds(FRAME_RATE_TAG)
        val shown = visible(FRAME_RATE_TAG)
        assertTrue(abs(shown.top - card.top) < 1 && abs(shown.bottom - card.bottom) < 1, "the whole card is on screen: $shown, $card")
        val margin = (12 * px).roundToInt()
        val crop = Bitmap.createBitmap(image, 0, (card.top - margin).roundToInt().coerceAtLeast(0), image.width,
            (card.height + 2 * margin).roundToInt().coerceAtMost(image.height - (card.top - margin).roundToInt().coerceAtLeast(0)))
        File("build/agent-previews").mkdirs()
        File("build/agent-previews/rgb-framerate-card.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val thirty = bounds(FRAME_RATE_AREA_TAG + 30)
        val sixty = bounds(FRAME_RATE_AREA_TAG + 60)
        // Theme(true) below Android 12: the default dark scheme
        val outline = darkColorScheme().onSurface
        val cardColor = darkColorScheme().surfaceContainerHigh
        // the chosen area (30 Hz) has the outline 2–5 dp outside, the other one none
        assertClose(outline.toArgb(), image.getPixel(thirty.left.toInt() + (90 * px).toInt(), (thirty.top - 3.5f * px).toInt()), 30)
        assertClose(cardColor.toArgb(), image.getPixel(sixty.left.toInt() + (90 * px).toInt(), (sixty.top - 3.5f * px).toInt()), 12)
        // full colour for the chosen area, 55 % over the card for the other (bottom left, below the text)
        assertClose(0xFF00C2A8.toInt(), image.getPixel(thirty.left.toInt() + (8 * px).toInt(), thirty.bottom.toInt() - (8 * px).toInt()), 40)
        assertClose(blend(0xFFFFA23F.toInt(), cardColor.toArgb(), 0.55f),
            image.getPixel(sixty.left.toInt() + (8 * px).toInt(), sixty.bottom.toInt() - (8 * px).toInt()), 40)
        // the glowing drop: near white at its still place, 28 dp below the top of the area
        val drop = brightestInRow(image, thirty, (thirty.top + 28 * px).toInt())
        assertTrue(drop.second > 230 * 3, "the 30 Hz drop glows: brightness ${drop.second}")
        val left = thirty.left + (18 + 14 * 1.6f) * px
        val right = thirty.right - (18 + 14 * 1.6f) * px
        val expected = left + (right - left) * (0.5f - 0.5f * kotlin.math.cos(2 * Math.PI.toFloat() * STILL_PHASE))
        assertTrue(abs(drop.first - expected) < 6 * px, "drop at ${drop.first}, expected about $expected")
    }

    private fun trips() = frameRateTrips.copyOf()

    private companion object {
        const val FRAME_MS = 16L
    }

    /** How far a light went forwards in its trip from [from] to [to] (both 0..1, wrapping). */
    private fun forward(from: Float, to: Float) = ((to - from) % 1f + 1f) % 1f

    private fun assertStill() {
        assertEquals(STILL_PHASE, frameRateTrips[0], 1e-4f)
        assertEquals(STILL_PHASE * 2, frameRateTrips[1], 1e-4f)
    }

    /** Bounds in the root, not clipped by the scroll container. */
    private fun bounds(tag: String): Rect {
        val node = compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode()
        return Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    private fun visible(tag: String) = compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsWith(matcher: SemanticsMatcher) {
        assertExists()
        compose.onNode(matcher).assertExists()
    }

    /** x and summed RGB of the brightest pixel of [y] inside [area]. */
    /** Middle x and summed RGB of the brightest pixels of row [y] inside [area] (the core of the drop is evenly bright). */
    private fun brightestInRow(image: Bitmap, area: Rect, y: Int): Pair<Float, Int> {
        fun sum(x: Int) = image.getPixel(x, y).let { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
        val xs = area.left.toInt() until area.right.toInt()
        val max = xs.maxOf { sum(it) }
        val brightest = xs.filter { sum(it) >= max - 3 }
        return (brightest.first() + brightest.last()) / 2f to max
    }

    private fun blend(top: Int, bottom: Int, alpha: Float): Int {
        fun ch(shift: Int) = ((top shr shift and 0xFF) * alpha + (bottom shr shift and 0xFF) * (1 - alpha)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun assertClose(expected: Int, actual: Int, tolerance: Int) {
        val d = listOf(16, 8, 0).maxOf { shift -> abs((expected shr shift and 0xFF) - (actual shr shift and 0xFF)) }
        assertTrue(d <= tolerance, "expected ~#%08X, got #%08X".format(expected, actual))
    }

    // captureToImage waits for a redraw that never comes in Robolectric (P15)
    private fun shot(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }
}
