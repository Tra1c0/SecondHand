// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.agent.HapticChain.Direction
import helium314.keyboard.agent.HapticChain.Plan
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tick chain after undo and redo: pattern and the fallback by what the vibrator can do. */
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

    @Test fun redoRisesAndUndoFallsInThreeEvenTicks() {
        val rising = HapticChain.pattern(Direction.RISING)
        val falling = HapticChain.pattern(Direction.FALLING)
        assertEquals(listOf(0L, 50L, 100L), rising.map { it.atMs })
        assertEquals(rising.map { it.atMs }, falling.map { it.atMs })
        assertEquals(listOf(0.3f, 0.6f, 1f), rising.map { it.strength })
        assertEquals(rising.map { it.strength }.reversed(), falling.map { it.strength })
        // the owner asked for 40 to 60 ms apart
        assertTrue(HapticChain.GAP_MS in 40..60)
    }

    @Test fun primitivesWithScalesWhereTheTickIsSupported() {
        val device = FakeDevice(30, hasAmplitudeControl = true, primitives = setOf(HapticChain.PRIMITIVE_TICK, HapticChain.PRIMITIVE_CLICK))
        val plan = HapticChain.plan(Direction.RISING, device)
        assertEquals(Plan.Primitives(HapticChain.PRIMITIVE_TICK, listOf(0.3f, 0.6f, 1f), listOf(0, 38, 38)), plan)
        assertEquals(listOf(1f, 0.6f, 0.3f), (HapticChain.plan(Direction.FALLING, device) as Plan.Primitives).scales)
    }

    @Test fun theClickWhenOnlyTheClickIsSupported() {
        val plan = HapticChain.plan(Direction.FALLING, FakeDevice(33, primitives = setOf(HapticChain.PRIMITIVE_CLICK)))
        assertEquals(HapticChain.PRIMITIVE_CLICK, (plan as Plan.Primitives).primitive)
    }

    @Test fun aWaveformWithAmplitudesWithoutPrimitives() {
        val device = FakeDevice(30, hasAmplitudeControl = true)
        assertEquals(Plan.Waveform(longArrayOf(0, 12, 38, 12, 38, 12), intArrayOf(0, 76, 0, 153, 0, 255)),
            HapticChain.plan(Direction.RISING, device))
        assertEquals(Plan.Waveform(longArrayOf(0, 12, 38, 12, 38, 12), intArrayOf(0, 255, 0, 153, 0, 76)),
            HapticChain.plan(Direction.FALLING, device))
        // below API 30 primitives are not even asked for
        val old = FakeDevice(29, hasAmplitudeControl = true, primitives = setOf(HapticChain.PRIMITIVE_TICK))
        assertTrue(HapticChain.plan(Direction.RISING, old) is Plan.Waveform)
        assertTrue(old.asked.isEmpty())
    }

    @Test fun equalPulsesWithoutAmplitudeControlOrBeforeApi26() {
        val pulses = Plan.Pulses(longArrayOf(0, 12, 38, 12, 38, 12))
        assertEquals(pulses, HapticChain.plan(Direction.RISING, FakeDevice(30)))
        assertEquals(pulses, HapticChain.plan(Direction.FALLING, FakeDevice(30)))
        assertEquals(pulses, HapticChain.plan(Direction.RISING, FakeDevice(25, hasAmplitudeControl = true)))
    }
}
