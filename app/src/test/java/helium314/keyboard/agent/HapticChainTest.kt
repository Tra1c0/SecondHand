// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.HapticChain.Feel
import helium314.keyboard.agent.HapticChain.Plan
import helium314.keyboard.agent.HapticChain.Step
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The feel of the space bar detents (stage 33): a light tick per detent, a stronger and longer
 * stretch at the last one, and the fallback by what the vibrator can do.
 */
class HapticChainTest {
    private class FakeDevice(
        override val sdk: Int,
        override val hasAmplitudeControl: Boolean = false,
        private val primitives: Set<Int> = emptySet(),
    ) : HapticChain.Device {
        override val hasVibrator = true
        val asked = mutableListOf<Int>()
        override fun supportsPrimitive(primitive: Int): Boolean { asked += primitive; return primitive in primitives }
        override fun play(plan: Plan) = Unit
    }

    private val all = setOf(HapticChain.PRIMITIVE_TICK, HapticChain.PRIMITIVE_CLICK, HapticChain.PRIMITIVE_QUICK_RISE, HapticChain.PRIMITIVE_THUD)

    @Test fun primitivesWhereTheVibratorHasThem() {
        val device = FakeDevice(31, hasAmplitudeControl = true, primitives = all)
        assertEquals(Plan.Primitives(listOf(Step(HapticChain.PRIMITIVE_TICK, 0.8f))), HapticChain.plan(Feel.DETENT, device))
        // the stretch: rising resistance, then the heavy thud
        assertEquals(Plan.Primitives(listOf(Step(HapticChain.PRIMITIVE_QUICK_RISE, 0.7f), Step(HapticChain.PRIMITIVE_THUD, 1f))),
            HapticChain.plan(Feel.STRETCH, device))
    }

    @Test fun theThudOnlyFromApi31AndTheClickInItsPlace() {
        val api30 = FakeDevice(30, hasAmplitudeControl = true, primitives = all)
        assertEquals(Plan.Primitives(listOf(Step(HapticChain.PRIMITIVE_QUICK_RISE, 0.7f), Step(HapticChain.PRIMITIVE_CLICK, 1f))),
            HapticChain.plan(Feel.STRETCH, api30))
        assertFalse(HapticChain.PRIMITIVE_THUD in api30.asked)
        val noThud = FakeDevice(33, hasAmplitudeControl = true, primitives = all - HapticChain.PRIMITIVE_THUD)
        assertEquals(HapticChain.PRIMITIVE_CLICK, (HapticChain.plan(Feel.STRETCH, noThud) as Plan.Primitives).steps.last().primitive)
    }

    @Test fun aSofterClickWhereTheTickIsMissing() {
        val device = FakeDevice(33, primitives = setOf(HapticChain.PRIMITIVE_CLICK))
        assertEquals(Plan.Primitives(listOf(Step(HapticChain.PRIMITIVE_CLICK, 0.5f))), HapticChain.plan(Feel.DETENT, device))
    }

    @Test fun theStretchFallsBackToTheWaveformWithoutARise() {
        // a heavy primitive alone is no "longer" impulse: the rising waveform is closer to the stretch
        val device = FakeDevice(33, hasAmplitudeControl = true, primitives = setOf(HapticChain.PRIMITIVE_TICK, HapticChain.PRIMITIVE_CLICK, HapticChain.PRIMITIVE_THUD))
        assertTrue(HapticChain.plan(Feel.STRETCH, device) is Plan.Waveform)
        assertTrue(HapticChain.plan(Feel.DETENT, device) is Plan.Primitives)
    }

    @Test fun aWaveformWithAmplitudesWithoutPrimitives() {
        val device = FakeDevice(30, hasAmplitudeControl = true)
        assertEquals(Plan.Waveform(longArrayOf(0, 12), intArrayOf(0, 153)), HapticChain.plan(Feel.DETENT, device))
        // rising strength over 90 ms
        assertEquals(Plan.Waveform(longArrayOf(0, 20, 20, 20, 30), intArrayOf(0, 63, 114, 178, 255)), HapticChain.plan(Feel.STRETCH, device))
        // below API 30 primitives are not even asked for
        val old = FakeDevice(29, hasAmplitudeControl = true, primitives = all)
        assertTrue(HapticChain.plan(Feel.DETENT, old) is Plan.Waveform)
        assertTrue(HapticChain.plan(Feel.STRETCH, old) is Plan.Waveform)
        assertTrue(old.asked.isEmpty())
    }

    @Test fun plainPulsesWithoutAmplitudeControlOrBeforeApi26() {
        for (device in listOf(FakeDevice(30), FakeDevice(25, hasAmplitudeControl = true))) {
            assertEquals(Plan.Pulses(longArrayOf(0, 12)), HapticChain.plan(Feel.DETENT, device))
            assertEquals(Plan.Pulses(longArrayOf(0, 70)), HapticChain.plan(Feel.STRETCH, device))
        }
    }

    @Test fun theStretchIsStrongerAndLongerThanATickOnEveryTier() {
        fun onMs(plan: Plan) = when (plan) {
            is Plan.Waveform -> plan.timings.filterIndexed { i, _ -> plan.amplitudes[i] > 0 }.sum()
            is Plan.Pulses -> plan.timings.filterIndexed { i, _ -> i % 2 == 1 }.sum()
            is Plan.Primitives -> plan.steps.size.toLong() // primitives: count of impulses
        }
        fun peak(plan: Plan) = when (plan) {
            is Plan.Waveform -> plan.amplitudes.max() / 255f
            is Plan.Pulses -> 1f
            is Plan.Primitives -> plan.steps.maxOf { it.scale }
        }
        for (device in listOf(FakeDevice(31, true, all), FakeDevice(30, true), FakeDevice(30), FakeDevice(25))) {
            val tick = HapticChain.plan(Feel.DETENT, device)
            val stretch = HapticChain.plan(Feel.STRETCH, device)
            assertTrue(onMs(stretch) > onMs(tick), "sdk ${device.sdk}")
            assertTrue(peak(stretch) >= peak(tick), "sdk ${device.sdk}")
        }
        val waveTick = HapticChain.plan(Feel.DETENT, FakeDevice(30, true))
        val waveStretch = HapticChain.plan(Feel.STRETCH, FakeDevice(30, true))
        assertTrue(peak(waveStretch) > peak(waveTick))
        assertTrue(onMs(waveStretch) >= 5 * onMs(waveTick))
    }
}
