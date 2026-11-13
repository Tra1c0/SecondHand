// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.SliderFeel.Impulse
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.SliderDialog
import helium314.keyboard.settings.screens.AgentSlider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 28: the shared settings slider with ticks and detents, and the adopted upstream slider dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class AgentSliderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val played = mutableListOf<Impulse>()
    private val sink = SliderFeel.Sink { played += it }

    private class FakeDevice(override val sdk: Int = 30, private val primitives: Boolean = true,
                             override val hasAmplitudeControl: Boolean = true) : SliderHaptics.Device {
        val plans = mutableListOf<SliderHaptics.Plan>()
        override val hasVibrator = true
        override fun supportsPrimitive(primitive: Int) = primitives
        override fun play(plan: SliderHaptics.Plan) { plans += plan }
    }

    private fun reset() {
        app.prefs().edit().clear().commit()
        android.provider.Settings.Global.putInt(app.contentResolver, "zen_mode", 0)
        SliderHaptics.deviceForTest = null
        SliderDrag.clockForTest = null
    }

    @Before fun setUp() {
        reset()
        // every impulse 100 ms after the last: the rate limit is tested in SliderFeelTest
        var time = 0L
        SliderDrag.clockForTest = { time += 100; time }
    }

    @After fun tearDown() = reset()

    /** Every value the slider reported, in order. */
    private val changes = mutableListOf<Float>()

    private fun show(range: ClosedFloatingPointRange<Float>, default: Float?, initial: Float): MutableFloatState {
        val value = mutableFloatStateOf(initial)
        compose.setContent {
            Theme(true) {
                Box(Modifier.width(300.dp)) {
                    AgentSlider(value.floatValue, { changes += it; value.floatValue = it }, Modifier.fillMaxWidth().testTag("slider"),
                        valueRange = range, defaultValue = default, sink = sink)
                }
            }
        }
        compose.waitForIdle()
        return value
    }

    private val slider get() = compose.onNodeWithTag("slider")

    /** The finger moves by [dp] (a drag lags behind the finger by the touch slop, so tests follow the value). */
    private fun moveBy(dp: Float) {
        slider.performTouchInput { moveBy(Offset(dp.dp.toPx(), 0f)) }
        compose.waitForIdle()
    }

    @Test fun aDragSnapsToTheDetentAndLetsGoBeyondItsZone() {
        val value = show(0f..1f, default = 0.5f, initial = 0.24f)
        slider.performTouchInput { down(Offset(centerX - 75.dp.toPx(), centerY)) }
        // slowly to the right until the value has left the zone of 0.5
        var steps = 0
        while (value.floatValue < 0.5f + 0.02f && steps++ < 200) moveBy(1f)
        val forward = changes.toList()
        assertTrue(0.5f in forward, "the middle is reached exactly: $forward")
        assertTrue(forward.none { it != 0.5f && kotlin.math.abs(it - 0.5f) <= 0.015f }, "no value inside the zone but the detent: $forward")
        assertTrue(Impulse.TICK in played)
        assertTrue(Impulse.DETENT in played)
        // back a little: the fine values next to the detent are reachable now
        moveBy(-2f)
        moveBy(-2f)
        assertTrue(value.floatValue > 0.5f && value.floatValue < 0.515f, "fine value next to the detent: ${changes.drop(forward.size)}")
        val fine = value.floatValue
        slider.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(fine, value.floatValue, "the release keeps it")
    }

    // A tap on the track does not reach even a plain Material slider under Robolectric (checked), so the
    // tap path (the finger still counts as down while the slider handles the release) is untested here.
    @Test fun aDragToTheEndGivesExactlyTheEnd() {
        val value = show(0f..100f, default = 30f, initial = 80f)
        slider.performTouchInput { down(Offset(width - 60.dp.toPx(), centerY)) }
        var steps = 0
        while (value.floatValue < 98.5f && steps++ < 200) moveBy(1f)
        assertEquals(100f, value.floatValue, "$changes")
        assertEquals(Impulse.DETENT, played.last(), "entering the end detent is the stronger impulse")
        moveBy(20f)
        slider.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(100f, value.floatValue)
        assertTrue(changes.none { it > 98.5f && it < 100f }, "$changes")
    }

    @Test fun talkBackSetsExactValuesWithoutSnappingOrImpulses() {
        val value = show(0f..1f, default = 0.5f, initial = 0.2f)
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(0.2f, 0f..1f))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.49f) }
        compose.waitForIdle()
        assertEquals(0.49f, value.floatValue, "a value next to the detent stays reachable")
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(0.49f, 0f..1f))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.505f) }
        compose.waitForIdle()
        assertEquals(0.505f, value.floatValue)
        assertEquals(emptyList(), played)
    }

    private fun dialog(onDone: (Float) -> Unit) {
        compose.setContent {
            Theme(true) {
                SliderDialog(onDismissRequest = {}, onDone = onDone, initialValue = 20f, range = 0f..100f,
                    positionString = { "${it.toInt()} ms" }, defaultValue = 50f, integerValues = true)
            }
        }
        compose.waitForIdle()
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        fun current() = node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        node.performTouchInput { down(Offset(centerX - 50.dp.toPx(), centerY)) }
        var steps = 0
        // slowly to the right: the first value at 48.5 or more is the default itself
        while (current() < 48.5f && steps++ < 200) {
            node.performTouchInput { moveBy(Offset(1.dp.toPx(), 0f)) }
            compose.waitForIdle()
        }
        node.performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithText("50 ms").assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
    }

    @Test fun theUpstreamSliderDialogSnapsToItsDefaultAndVibratesWithKeyVibration() {
        app.prefs().edit().putBoolean(Settings.PREF_VIBRATE_ON, true).commit()
        val device = FakeDevice()
        SliderHaptics.deviceForTest = device
        var done: Float? = null
        dialog { done = it }
        assertEquals(50f, done)
        assertTrue(SliderHaptics.Plan.Primitive(SliderHaptics.PRIMITIVE_CLICK, SliderHaptics.DETENT_SCALE) in device.plans, "${device.plans}")
        assertTrue(SliderHaptics.Plan.Primitive(SliderHaptics.PRIMITIVE_TICK, SliderHaptics.TICK_SCALE) in device.plans, "${device.plans}")
    }

    @Test fun withoutKeyVibrationTheDialogStillSnapsButStaysSilent() {
        val device = FakeDevice()
        SliderHaptics.deviceForTest = device
        var done: Float? = null
        dialog { done = it }
        assertEquals(50f, done)
        assertEquals(emptyList(), device.plans)
    }

    @Test fun impulsesFollowKeyVibrationAndDoNotDisturb() {
        assertFalse(SliderHaptics.allowed(app), "key vibration is off by default")
        app.prefs().edit().putBoolean(Settings.PREF_VIBRATE_ON, true).commit()
        assertTrue(SliderHaptics.allowed(app))
        android.provider.Settings.Global.putInt(app.contentResolver, "zen_mode", 1)
        assertFalse(SliderHaptics.allowed(app))
        app.prefs().edit().putBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, true).commit()
        assertTrue(SliderHaptics.allowed(app))
    }

    @Test fun eachVibratorGetsWhatItCanPlay() {
        val tick = Impulse.TICK
        val detent = Impulse.DETENT
        FakeDevice().let {
            assertEquals(SliderHaptics.Plan.Primitive(SliderHaptics.PRIMITIVE_TICK, SliderHaptics.TICK_SCALE), SliderHaptics.plan(tick, it))
            assertEquals(SliderHaptics.Plan.Primitive(SliderHaptics.PRIMITIVE_CLICK, SliderHaptics.DETENT_SCALE), SliderHaptics.plan(detent, it))
        }
        val onlyTick = object : SliderHaptics.Device {
            override val sdk = 30
            override val hasVibrator = true
            override val hasAmplitudeControl = true
            override fun supportsPrimitive(primitive: Int) = primitive == SliderHaptics.PRIMITIVE_TICK
            override fun play(plan: SliderHaptics.Plan) {}
        }
        assertEquals(SliderHaptics.Plan.Primitive(SliderHaptics.PRIMITIVE_TICK, 1f), SliderHaptics.plan(detent, onlyTick))
        FakeDevice(sdk = 28, primitives = false).let {
            assertEquals(SliderHaptics.Plan.OneShot(SliderHaptics.TICK_MS, SliderHaptics.TICK_AMPLITUDE), SliderHaptics.plan(tick, it))
            assertEquals(SliderHaptics.Plan.OneShot(SliderHaptics.DETENT_MS, SliderHaptics.DETENT_AMPLITUDE), SliderHaptics.plan(detent, it))
        }
        FakeDevice(sdk = 28, primitives = false, hasAmplitudeControl = false).let {
            assertEquals(SliderHaptics.Plan.Pulse(SliderHaptics.TICK_MS), SliderHaptics.plan(tick, it))
            assertEquals(SliderHaptics.Plan.Pulse(SliderHaptics.DETENT_MS), SliderHaptics.plan(detent, it))
        }
    }
}
