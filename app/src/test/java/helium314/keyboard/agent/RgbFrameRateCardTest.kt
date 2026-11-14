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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agentkeyboard.core.RgbFramePacer
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentRgbScreen
import helium314.keyboard.settings.screens.AgentSettingsTokens
import helium314.keyboard.settings.screens.CHOICE_DIM_ALPHA
import helium314.keyboard.settings.screens.FRAME_RATES
import helium314.keyboard.settings.screens.FRAME_RATE_AREA_TAG
import helium314.keyboard.settings.screens.FRAME_RATE_COLOURS
import helium314.keyboard.settings.screens.FRAME_RATE_HEADING_MAX
import helium314.keyboard.settings.screens.FRAME_RATE_HEADING_MIN
import helium314.keyboard.settings.screens.FRAME_RATE_HEADING_STYLE
import helium314.keyboard.settings.screens.FRAME_RATE_HEADING_TAG
import helium314.keyboard.settings.screens.FRAME_RATE_TAG
import helium314.keyboard.settings.screens.FrameRateCard
import helium314.keyboard.settings.screens.LIGHT_TRIP_MS
import helium314.keyboard.settings.screens.STILL_PHASE
import helium314.keyboard.settings.screens.choiceDim
import helium314.keyboard.settings.screens.frameRateCompositions
import helium314.keyboard.settings.screens.frameRatePhases
import helium314.keyboard.settings.screens.frameRateRunning
import helium314.keyboard.settings.screens.frameRateTrips
import helium314.keyboard.settings.screens.lightPhase
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
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 37: the frame rate of the RGB page as two colour areas with a moving light. Tapping writes
 * the same preference as the chips before; the light moves only on screen with motion allowed.
 * Both lights run from one clock at one speed, 30 Hz in 33 ms steps, 60 Hz in 17 ms steps (owner,
 * 2026-11-14). The text of the dimmed area stays readable in both themes.
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
        frameRatePhases.fill(-1f)
    }

    /** The whole RGB page without motion (the test clock can idle, P62), [width] dp wide at [fontScale]. */
    private fun stillPage(width: Int? = null, fontScale: Float = 1f) {
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Theme(true) { Box(if (width == null) Modifier else Modifier.width(width.dp)) { AgentRgbScreen {} } }
            }
        }
        compose.waitForIdle()
        // to the end of the options: the whole card (the last one) is on screen
        compose.onNodeWithTag("agent_rgb_options").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        compose.waitForIdle()
    }

    /** The card alone on a page of the theme; [above] dp of space before it (off screen when large). */
    private fun card(above: Int = 0, scroll: ScrollState? = null, dark: Boolean = true) {
        compose.setContent {
            Theme(dark) {
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
        // the heading belongs to the card
        compose.onNode(hasTestTag(FRAME_RATE_TAG) and hasAnyDescendant(hasText("Bildwiederholrate")), useUnmergedTree = true).assertExists()
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
        // both areas side by side would be too narrow at 390 dp: stacked, each the full width of the card,
        // the same width and left edge, so both drops run on the same track
        assertTrue(thirty.width > card.width * 0.8f)
        assertEquals(thirty.left, sixty.left, 0.5f)
        assertEquals(thirty.width, sixty.width, 0.5f)
        // touch targets
        val min = with(compose.density) { 44.dp.toPx() } - 1
        for (tag in listOf(FRAME_RATE_AREA_TAG + 30, FRAME_RATE_AREA_TAG + 60, "agent_rgb_power_save"))
            assertTrue(bounds(tag).height >= min && bounds(tag).width >= min, "$tag at least 44 dp")
        // at 390 dp and font scale 1 the heading keeps its full 21 sp: as wide as the word measured at 21 sp
        val heading = headingLayout()
        assertEquals(1, heading.lineCount)
        val full = headingLine(FRAME_RATE_HEADING_MAX, 1f)
        println("heading at 390 dp: %.0f px, at 21 sp alone %.0f px".format(heading.getLineRight(0), full))
        assertEquals(full, heading.getLineRight(0), 1f, "heading at 21 sp")
    }

    @Test fun theHeadingStaysOneLineAt360DpWithFontScale13() {
        stillPage(width = 360, fontScale = 1.3f)
        val (line, available) = headingOnOneLine()
        // Compose scales large text less than small text (non-linear font scale): 21 sp at 1.3 still fit here
        val full = headingLine(FRAME_RATE_HEADING_MAX, 1.3f)
        println("heading at 360 dp, font scale 1.3: %.0f px of %d, at 21 sp alone %.0f px".format(line, available, full))
        assertTrue(line <= available, "fits: $line of $available")
    }

    @Test fun at360DpWithFontScale2TheHeadingShrinksInsteadOfBreaking() {
        stillPage(width = 360, fontScale = 2f)
        val (line, available) = headingOnOneLine()
        // the layout result names the style's size, not the auto size: compare widths with the word measured alone
        val full = headingLine(FRAME_RATE_HEADING_MAX, 2f)
        println("heading at 360 dp, font scale 2: %.0f px of %d, at 21 sp it would be %.0f px".format(line, available, full))
        assertTrue(full > available, "at 21 sp the word would not fit ($full > $available): the case is real")
        assertTrue(line <= available && line < full, "smaller to fit: $line of $available")
        assertTrue(line >= headingLine(FRAME_RATE_HEADING_MIN, 2f), "not below the least size: $line")
    }

    /** The heading at the page's width: one line, the whole word, inside the card; its width and the room it has. */
    private fun headingOnOneLine(): Pair<Float, Int> {
        val heading = headingLayout()
        assertEquals("Bildwiederholrate", heading.layoutInput.text.text)
        assertEquals(1, heading.lineCount, "not broken inside the word")
        assertFalse(heading.isLineEllipsized(0), "not cut off")
        assertFalse(heading.hasVisualOverflow, "the whole word is drawn")
        val card = bounds(FRAME_RATE_TAG)
        val text = bounds(FRAME_RATE_HEADING_TAG)
        assertTrue(text.left >= card.left && heading.size.width + text.left <= card.right, "inside the card")
        assertTrue(card.width <= with(compose.density) { 360.dp.toPx() }, "laid out at 360 dp: ${card.width}")
        return heading.getLineRight(0) to heading.size.width
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

    @Test fun bothLightsRunFromOneClockAtTheSameSpeed() {
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
        assertTrue(abs(sixty - expected) < 0.02f, "60 Hz light moved $sixty of its trip in 300 ms, the same speed")
        // one clock: both areas read the same phase and stay above each other (60 Hz at most one 30 Hz step ahead)
        assertEquals(frameRatePhases[0], frameRatePhases[1])
        assertTrue(forward(trips()[0], trips()[1]) < 1f / 72 + 1e-4f, "60 Hz ${trips()[1]} above 30 Hz ${trips()[0]}")
        assertTrue(!picture.sameAs(later), "the light moved on screen")
        assertEquals(composed, frameRateCompositions, "frames draw the light and compose nothing")
    }

    @Test fun thirtyHertzMovesEvery33MsAndSixtyEvery17() {
        compose.mainClock.autoAdvance = false
        card()
        frames(200)
        assertTrue(frameRateRunning)
        // 120 frames of the test clock (16 ms each, 1.92 s): where each light was drawn after each frame
        val samples = (0 until 120).map {
            frames(FRAME_MS)
            assertEquals(frameRatePhases[0], frameRatePhases[1], "one clock for both areas")
            val p = frameRatePhases[0]
            // each light shows the shared clock in its own steps: whole 30 Hz or 60 Hz frames of the trip
            assertEquals(wrap(lightPhase(p, 30)), frameRateTrips[0], 1e-5f)
            assertEquals(wrap(lightPhase(p, 60)), frameRateTrips[1], 1e-5f)
            trips()
        }
        for ((index, places) in listOf(0 to 72, 1 to 144)) for (s in samples) {
            val step = forward(STILL_PHASE, s[index]) * places
            assertTrue(abs(step - step.roundToInt()) < 2e-3f, "${FRAME_RATES[index]} Hz light at a whole step of 1/$places: $step")
        }
        val moves30 = samples.zipWithNext().map { (a, b) -> forward(a[0], b[0]) }
        val moves60 = samples.zipWithNext().map { (a, b) -> forward(a[1], b[1]) }
        // 30 Hz: one step of 1/72 trip (33 ms) at a time, each place held for at least two frames of 16 ms
        assertTrue(moves30.all { it < 1e-5f || abs(it - 1f / 72) < 1e-4f }, "30 Hz moves in steps of 1/72: $moves30")
        assertTrue(moves30.zipWithNext().none { (a, b) -> a > 1e-5f && b > 1e-5f }, "30 Hz never moves in two frames in a row")
        val count30 = moves30.count { it > 1e-5f }
        assertTrue(abs(count30 - 119 * 16 / (1000f / 30)) <= 1.5f, "30 Hz: $count30 steps in ${119 * 16} ms")
        // 60 Hz: one step of 1/144 trip (17 ms), so in nearly every frame of 16 ms
        assertTrue(moves60.all { it < 1e-5f || abs(it - 1f / 144) < 1e-4f }, "60 Hz moves in steps of 1/144: $moves60")
        val count60 = moves60.count { it > 1e-5f }
        assertTrue(abs(count60 - 119 * 16 / (1000f / 60)) <= 1.5f, "60 Hz: $count60 steps in ${119 * 16} ms")
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

    @Test fun theBatterySaverReceiverGoesWithTheCard() {
        fun receivers() = shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
        val before = receivers()
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        var shown by mutableStateOf(true)
        compose.setContent { Theme(true) { if (shown) FrameRateCard(30, {}) } }
        compose.waitForIdle()
        assertEquals(before + 1, receivers(), "registered while the card is shown")
        shown = false
        compose.waitForIdle()
        assertEquals(before, receivers(), "unregistered when the card leaves")
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
        // owner's colours: 30 Hz orange at full colour (chosen), 60 Hz turquoise with its gradient dimmed over the card
        // (bottom left, below the text)
        assertClose(0xFFFFA23F.toInt(), image.getPixel(thirty.left.toInt() + (8 * px).toInt(), thirty.bottom.toInt() - (8 * px).toInt()), 40)
        val (from60, to60) = FRAME_RATE_COLOURS[1]
        assertClose(blend(0xFF00C2A8.toInt(), cardColor.toArgb(), choiceDim(false, from60, to60, cardColor)),
            image.getPixel(sixty.left.toInt() + (8 * px).toInt(), sixty.bottom.toInt() - (8 * px).toInt()), 40)
        // the glowing drops: near white at their still place, 28 dp below the top, directly above each other
        val drop = brightestInRow(image, thirty, (thirty.top + 28 * px).toInt())
        assertTrue(drop.second > 230 * 3, "the 30 Hz drop glows: brightness ${drop.second}")
        val left = thirty.left + (18 + 14 * 1.6f) * px
        val right = thirty.right - (18 + 14 * 1.6f) * px
        val expected = left + (right - left) * (0.5f - 0.5f * cos(2 * Math.PI.toFloat() * STILL_PHASE))
        assertTrue(abs(drop.first - expected) < 6 * px, "drop at ${drop.first}, expected about $expected")
        // the dimmed 60 Hz drop is not white enough for its brightest pixels to mark its middle (the trail adds up
        // on its left): compare the right edges of the two cores instead, where no trail lies
        val edge30 = dropEdge(image, thirty, (thirty.top + 28 * px).toInt(), drop.first)
        val edge60 = dropEdge(image, sixty, (sixty.top + 28 * px).toInt(), drop.first)
        assertTrue(abs(edge30 - (expected + 14 * px)) < 2 * px, "30 Hz core ends at $edge30, expected ${expected + 14 * px}")
        assertTrue(abs(edge60 - edge30) <= 1.5f * px, "60 Hz drop ends at $edge60, directly below the 30 Hz drop ($edge30)")
    }

    @Test fun theTextOfBothAreasKeepsItsContrastInBothThemes() {
        // Theme(…) below Android 12: the default schemes; the areas lie on surfaceContainerHigh
        for ((name, scheme) in listOf("dark" to darkColorScheme(), "light" to lightColorScheme())) {
            val base = scheme.surfaceContainerHigh.toArgb()
            FRAME_RATE_COLOURS.forEachIndexed { i, (from, to) ->
                for (selected in listOf(true, false)) {
                    val alpha = choiceDim(selected, from, to, Color(base))
                    if (!selected) assertEquals(CHOICE_DIM_ALPHA, alpha, "$name, ${FRAME_RATES[i]} Hz not chosen: the gradient at 70 %")
                    // the dark text at full opacity over every point of the gradient as drawn over the card
                    val least = (0..20).minOf { contrast(AgentSettingsTokens.OnGradient.toArgb(), blend(mix(from.toArgb(), to.toArgb(), it / 20f), base, alpha)) }
                    println("contrast $name ${FRAME_RATES[i]} Hz ${if (selected) "chosen" else "not chosen"}: %.2f".format(least))
                    // WCAG AA: 4.5:1 for the 12.5 sp line (the 19 sp bold label needs 3:1)
                    assertTrue(least >= 4.5, "$name, ${FRAME_RATES[i]} Hz, chosen $selected: contrast $least")
                }
            }
        }
    }

    @Test fun inTheLightThemeTheTextOfTheOtherAreaIsDrawnOpaque() = textOfTheOtherArea(dark = false)

    @Test fun inTheDarkThemeTheTextOfTheOtherAreaIsDrawnOpaque() = textOfTheOtherArea(dark = true)

    /** The picture of the card in a theme: the line of the 60 Hz area (not chosen) in full text colour on its dimmed gradient. */
    private fun textOfTheOtherArea(dark: Boolean) {
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        card(dark = dark)
        compose.waitForIdle()
        val image = shot()
        val px = compose.density.density
        val base = (if (dark) darkColorScheme() else lightColorScheme()).surfaceContainerHigh
        val area = bounds(FRAME_RATE_AREA_TAG + 60)
        val line = compose.onNode(hasText("60 Bilder/s · flüssiger"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // the darkest pixel of the line is the text colour itself, not the text faded into the card
        val darkest = (line.left.toInt() until line.right.toInt()).flatMap { x -> (line.top.toInt() until line.bottom.toInt()).map { y -> image.getPixel(x, y) } }
            .minBy { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
        assertClose(AgentSettingsTokens.OnGradient.toArgb(), darkest, 24)
        // the gradient beside the line: dimmed to choiceDim over the card
        val (from, to) = FRAME_RATE_COLOURS[1]
        val alpha = choiceDim(false, from, to, base)
        val x = area.left + 8 * px
        val y = (line.top + line.bottom) / 2
        val beside = image.getPixel(x.toInt(), y.toInt())
        assertClose(blend(mix(from.toArgb(), to.toArgb(), gradientAt(area, x, y)), base.toArgb(), alpha), beside, 12)
        val measured = contrast(darkest, beside)
        println("measured contrast ${if (dark) "dark" else "light"} 60 Hz not chosen: %.2f (#%08X on #%08X)".format(measured, darkest, beside))
        assertTrue(measured >= 4.5, "measured contrast $measured")
    }

    private fun trips() = frameRateTrips.copyOf()

    /** Right end of the heading word laid out alone at [size] and [fontScale] (the same fonts as the page). */
    private fun headingLine(size: TextUnit, fontScale: Float): Float {
        var right = 0f
        compose.runOnUiThread {
            val measurer = TextMeasurer(createFontFamilyResolver(app), Density(compose.density.density, fontScale), LayoutDirection.Ltr)
            right = measurer.measure("Bildwiederholrate", FRAME_RATE_HEADING_STYLE.copy(fontSize = size), softWrap = false, maxLines = 1)
                .getLineRight(0)
        }
        return right
    }

    private fun headingLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(FRAME_RATE_HEADING_TAG, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private companion object {
        const val FRAME_MS = 16L
    }

    /** How far a light went forwards in its trip from [from] to [to] (both 0..1, wrapping). */
    private fun forward(from: Float, to: Float) = ((to - from) % 1f + 1f) % 1f

    private fun wrap(phase: Float) = (phase % 1f + 1f) % 1f

    private fun assertStill() {
        assertEquals(STILL_PHASE, frameRateTrips[0], 1e-4f)
        assertEquals(STILL_PHASE, frameRateTrips[1], 1e-4f)
    }

    /** Bounds in the root, not clipped by the scroll container. */
    private fun bounds(tag: String): Rect {
        val node = compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode()
        return Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    private fun visible(tag: String) = compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** Middle x and summed RGB of the brightest pixels of row [y] inside [area] (the core of the drop is evenly bright). */
    private fun brightestInRow(image: Bitmap, area: Rect, y: Int): Pair<Float, Int> {
        fun sum(x: Int) = image.getPixel(x, y).let { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
        val xs = area.left.toInt() until area.right.toInt()
        val max = xs.maxOf { sum(it) }
        val brightest = xs.filter { sum(it) >= max - 3 }
        return (brightest.first() + brightest.last()) / 2f to max
    }

    /**
     * x of the right edge of the core oval of a drop in row [y] of [area]: the steepest fall of
     * brightness over two pixels within 24 dp right of [from] (the core is the most opaque oval).
     */
    private fun dropEdge(image: Bitmap, area: Rect, y: Int, from: Float): Float {
        fun sum(x: Int) = image.getPixel(x, y).let { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }
        val start = from.toInt()
        val end = minOf(area.right.toInt() - 3, start + (24 * compose.density.density).toInt())
        return (start until end).maxBy { sum(it) - sum(it + 2) } + 1f
    }

    /** Where (0..1) the 120° CSS gradient of [area] is at ([x], [y]), as `cssGradient` lays it out. */
    private fun gradientAt(area: Rect, x: Float, y: Float): Float {
        val rad = Math.toRadians(120.0)
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val length = abs(area.width * dx) + abs(area.height * dy)
        return (((x - area.center.x) * dx + (y - area.center.y) * dy) / length + 0.5f).coerceIn(0f, 1f)
    }

    /** sRGB channels from [from] to [to] at [t], as the gradient shader mixes them. */
    private fun mix(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = ((from shr shift and 0xFF) + ((to shr shift and 0xFF) - (from shr shift and 0xFF)) * t).roundToInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun blend(top: Int, bottom: Int, alpha: Float): Int {
        fun ch(shift: Int) = ((top shr shift and 0xFF) * alpha + (bottom shr shift and 0xFF) * (1 - alpha)).toInt()
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
