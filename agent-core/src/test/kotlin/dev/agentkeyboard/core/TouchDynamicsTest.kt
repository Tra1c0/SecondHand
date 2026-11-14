// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.TouchDynamics.Bucket
import dev.agentkeyboard.core.TouchDynamics.Companion.DAY_MILLIS
import dev.agentkeyboard.core.TouchDynamics.Companion.HOUR_MILLIS
import dev.agentkeyboard.core.TouchDynamics.Trait
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 50: short-term layer, scene profiles and promotion of the dynamic hit zones (pure model). */
class TouchDynamicsTest {
    private val rng = Random(50)
    private var promoted = ArrayList<TouchDynamics.Promotion>()
    private fun engine() = TouchDynamics().also { it.onPromote = { p -> promoted += p } }
    private fun at(day: Int, hour: Int) = day * DAY_MILLIS + hour * HOUR_MILLIS

    /**
     * A synthetic user: [taps] confirmed touches spread over all regions, [dx]/[dy] key sizes off the key
     * centre with noise [sd], one letter every 250 ms, a delete after every [deleteEvery] letters.
     * Returns the time after the last touch.
     */
    private fun type(e: TouchDynamics, start: Long, taps: Int, dx: Float = 0f, dy: Float = 0f, sd: Float = 0.06f,
                     deleteEvery: Int = 0): Long {
        var t = start
        repeat(taps) { i ->
            e.onKey(letter = true, delete = false, now = t)
            e.onTap(i % TouchDynamics.REGIONS, dx + sd * rng.nextGaussian().toFloat(), dy + sd * rng.nextGaussian().toFloat(), 0f, 0f, t)
            if (deleteEvery > 0 && i % deleteEvery == deleteEvery - 1) e.onKey(letter = false, delete = true, now = t + 100)
            t += 250
        }
        return t
    }

    /** One finished session in app "chat". */
    private fun session(e: TouchDynamics, start: Long, taps: Int = 60, dx: Float = 0f, dy: Float = 0f, sd: Float = 0.06f, deleteEvery: Int = 0) {
        e.startField("chat", false, start)
        type(e, start, taps, dx, dy, sd, deleteEvery)
        e.endSession()
    }

    private fun meanShortY(e: TouchDynamics) = (0 until TouchDynamics.REGIONS).map { e.shortY(it) }.average().toFloat()

    @Test fun followsAShiftedUserWithinThirtyTaps() {
        val e = engine()
        e.startField("chat", false, at(1, 9))
        assertEquals(0f, e.shortY(0))
        type(e, at(1, 9), 30, dy = 0.2f)
        val y = meanShortY(e)
        assertTrue(y in 0.15f..0.25f, "short-term after 30 taps: $y")
        assertTrue(abs(e.shortX(4)) < 0.05f)
    }

    @Test fun decaysAfterAPauseAnotherAppAndHalvesOnAnotherField() {
        val e = engine()
        e.startField("chat", false, at(1, 9))
        val end = type(e, at(1, 9), 40, dy = 0.2f)
        val before = e.shortY(-1)
        e.startField("chat", false, end + 1000) // another field of the same app
        assertTrue(abs(e.shortY(-1) - before / 2) < 1e-4f)
        e.startField("chat", true, end + 2000) // the same field again (restart): unchanged
        assertTrue(abs(e.shortY(-1) - before / 2) < 1e-4f)
        e.startField("mail", false, end + 3000) // another app ends the session
        assertEquals(0f, e.shortY(-1))
        val later = type(e, end + 4000, 40, dy = 0.2f)
        assertTrue(e.shortY(-1) > 0.1f)
        e.onKey(letter = true, delete = false, now = later + TouchDynamics.PAUSE_MILLIS + 1) // after a pause
        assertEquals(0f, e.shortY(-1))
        assertEquals(0, e.sessionTaps)
    }

    @Test fun shortSessionsNeverMoveTheBaseNorMakeScenes() {
        val e = engine()
        for (day in 0 until 20) for (hour in listOf(8, 13, 20)) session(e, at(day, hour), taps = TouchDynamics.MIN_SESSION - 1, dy = 0.25f)
        assertTrue(promoted.isEmpty())
        assertTrue(e.scenes.isEmpty())
        assertTrue(e.observations.isEmpty())
    }

    @Test fun aSustainedDeviationBecomesASceneThatIsRecognisedLater() {
        val e = engine()
        session(e, at(1, 10), sd = 0.06f) // usual typing: the reference
        assertTrue(e.scenes.isEmpty())
        session(e, at(1, 20), dy = 0.2f, sd = 0.12f) // evening: lower and less precise
        assertEquals(1, e.scenes.size)
        val scene = e.scenes.single()
        assertEquals(Bucket.EVENING, scene.bucket)
        assertTrue(abs(scene.dy - 0.2f) < 0.05f, "scene dy ${scene.dy}")
        val traits = e.traits(scene)
        assertTrue(Trait.LOWER in traits && Trait.LESS_PRECISE in traits, "$traits")
        assertFalse(Trait.HIGHER in traits)

        // next evening: after RECOGNISE_AT touches the short-term layer starts from the scene
        val fresh = engine() // the same touches without scenes, for comparison
        e.startField("chat", false, at(2, 21)); fresh.startField("chat", false, at(2, 21))
        val seed = Random(7)
        repeat(TouchDynamics.RECOGNISE_AT) { i ->
            val x = 0.12f * seed.nextGaussian().toFloat(); val y = 0.2f + 0.12f * seed.nextGaussian().toFloat()
            val t = at(2, 21) + i * 250L
            listOf(e, fresh).forEach { it.onKey(true, false, t); it.onTap(i % 9, x, y, 0f, 0f, t) }
        }
        assertEquals(scene.id, e.currentScene)
        assertEquals(-1, fresh.currentScene)
        assertTrue(meanShortY(e) > meanShortY(fresh), "${meanShortY(e)} vs ${meanShortY(fresh)}")
        assertTrue(abs(e.shortY(-1) - scene.dy) < 0.02f)
        e.endSession()
        assertEquals(1, e.scenes.size) // merged, not a second scene
        assertEquals(1, e.scenes.single().sessions) // fewer than MIN_SESSION touches: nothing merged

        // a morning session that hits too far left does not look like the evening scene
        e.startField("chat", false, at(3, 8))
        type(e, at(3, 8), TouchDynamics.RECOGNISE_AT, dx = -0.2f)
        assertEquals(-1, e.currentScene)
    }

    @Test fun moreCorrectionsMakeASceneToo() {
        val e = engine()
        session(e, at(1, 10), deleteEvery = 20)
        session(e, at(1, 23), deleteEvery = 4) // night: a delete every four letters
        val scene = e.scenes.single()
        assertEquals(Bucket.NIGHT, scene.bucket)
        assertTrue(Trait.MORE_CORRECTIONS in e.traits(scene))
        assertTrue(e.observations.isEmpty()) // no shift: nothing that could move the base
    }

    @Test fun promotionNeedsSessionsOnSeveralDaysAndTimes() {
        // many sessions on one day: no promotion
        val sameDay = engine()
        repeat(8) { session(sameDay, at(1, 8 + it * 2), dy = 0.2f) }
        assertTrue(promoted.isEmpty())
        // five sessions on three days, but all in the evening: only one scene of the day, no promotion
        val evenings = engine()
        listOf(1, 1, 2, 3, 3, 4).forEachIndexed { i, day -> session(evenings, at(day, 18 + i % 3), dy = 0.2f) }
        assertTrue(promoted.isEmpty())
        assertEquals(6, evenings.observations.size)
        // five sessions on three days in the morning and the evening: the base moves, damped and bounded
        val regular = engine()
        listOf(at(1, 8), at(1, 20), at(2, 9), at(3, 19), at(3, 8)).forEach { session(regular, it, dy = 0.2f) }
        assertEquals(1, promoted.size)
        val p = promoted.single()
        p.regionDy.forEach { assertEquals(TouchDynamics.MAX_PROMOTION, it) } // half of 0.2, bounded to 0.06
        p.regionDx.forEach { assertTrue(abs(it) < 0.03f) }
        assertEquals(1, regular.promotions)
        assertTrue(regular.observations.isEmpty()) // used up
        regular.scenes.forEach { assertTrue(abs(it.dy - (0.2f - TouchDynamics.MAX_PROMOTION)) < 0.05f, "scene relative to the new base ${it.dy}") }
    }

    @Test fun boundsAndStepsHold() {
        val e = engine()
        e.startField("chat", false, 0)
        var last = 0f
        var t = 0L
        repeat(300) { i ->
            val counted = e.onTap(-1, 0.49f, -0.49f, -0.3f, 0.3f, t) // residual 0.79: clamped
            assertTrue(counted)
            val now = e.shortX(-1)
            assertTrue(now - last <= TouchDynamics.MAX_STEP + 1e-6f, "step ${now - last} at $i")
            last = now
            assertTrue(abs(e.shortX(i % 9)) <= TouchDynamics.MAX_SHORT && abs(e.shortY(i % 9)) <= TouchDynamics.MAX_SHORT)
            t += 200
        }
        assertEquals(TouchDynamics.MAX_SHORT, e.shortX(-1))
        // outside the normal key: does not count
        assertFalse(e.onTap(0, 0.6f, 0f, 0f, 0f, t))
        assertFalse(e.onTap(0, 0f, -0.55f, 0f, 0f, t))
    }

    /**
     * Feedback loop: zones moved by the short-term layer turn touches into other letters, and in the worst
     * case every such letter is accepted as meant. One row of keys at 0, 1, 2 …; an unbiased user aims at
     * key centres; the detected key is the one whose moved zone holds the touch. The layer must return to
     * zero instead of feeding itself.
     */
    @Test fun movedZonesDoNotTeachThemselves() {
        val e = engine()
        e.startField("chat", false, 0)
        var t = 0L
        repeat(60) { e.onTap(0, 0.3f, 0f, 0f, 0f, t); t += 200 } // a shifted start
        assertTrue(e.shortX(0) > 0.25f)
        val random = Random(3)
        var late = 0.0
        repeat(600) { i ->
            val aimed = random.nextInt(10)
            val x = aimed + 0.3f * random.nextGaussian().toFloat()
            val detected = (x - e.shortX(0)).roundToInt() // zones moved right by the layer
            e.onTap(0, x - detected, 0f, 0f, 0f, t) // worst case: detected letter taken as meant
            if (i >= 300) late += e.shortX(0)
            t += 200
        }
        late /= 300
        assertTrue(abs(late) < 0.03f, "layer after unbiased typing: $late") // about 0.05 without the IN_KEY rule
    }

    @Test fun scenesCanBeDeletedAndEverythingCleared() {
        val e = engine()
        session(e, at(1, 20), dy = 0.2f)
        session(e, at(2, 8), dx = -0.2f)
        assertEquals(2, e.scenes.size)
        val evening = e.scenes.first { it.bucket == Bucket.EVENING }
        e.deleteScene(evening.id)
        assertEquals(1, e.scenes.size)
        assertTrue(e.observations.none { it.sceneId == evening.id })
        e.clear()
        assertTrue(e.scenes.isEmpty() && e.observations.isEmpty())
        assertTrue(e.spreadRef.isNaN())
        assertNull(e.scenes.firstOrNull())
    }

    @Test fun aDiscardedSessionLeavesNoTrace() {
        val e = engine()
        e.startField("chat", false, at(1, 20))
        type(e, at(1, 20), 80, dy = 0.25f)
        assertTrue(e.sessionTaps >= 80 && meanShortY(e) > 0.1f)
        e.dirty = false
        e.discardSession()
        assertEquals(0, e.sessionTaps)
        assertEquals(0f, meanShortY(e))
        assertTrue(e.scenes.isEmpty() && e.observations.isEmpty())
        assertTrue(e.spreadRef.isNaN() && e.correctionRef.isNaN())
        assertFalse(e.dirty)
        e.endSession() // nothing left to evaluate
        assertTrue(e.scenes.isEmpty() && e.observations.isEmpty() && promoted.isEmpty())
    }

    @Test fun aMovedBaseRebasesScenesAndDropsObservations() {
        val e = engine()
        session(e, at(1, 20), dy = 0.2f)
        val scene = e.scenes.single()
        val before = scene.dy; val regionBefore = scene.regionDy.copyOf()
        assertEquals(1, e.observations.size)
        e.startField("chat", false, at(2, 20))
        type(e, at(2, 20), 20, dy = 0.2f)
        val step = FloatArray(TouchDynamics.REGIONS) { 0.01f * it }
        e.rebase(FloatArray(TouchDynamics.REGIONS), step, 0f, 0.05f)
        assertEquals(before - 0.05f, scene.dy, 1e-6f)
        for (r in 0 until TouchDynamics.REGIONS) assertEquals(regionBefore[r] - step[r], scene.regionDy[r], 1e-6f)
        assertTrue(e.observations.isEmpty())
        assertEquals(0, e.sessionTaps) // the session was relative to the old base
        assertEquals(0f, meanShortY(e))
        assertTrue(e.dirty)
    }

    @Test fun observationsLeftAfterAPromotionMoveWithTheBase() {
        val e = engine()
        session(e, at(1, 12), dx = -0.25f) // another deviation: stays as an observation
        val other = e.observations.single()
        val otherDy = other.dy; val otherRegionDy = other.regionDy.copyOf()
        listOf(at(1, 8), at(1, 20), at(2, 9), at(3, 19), at(3, 8)).forEach { session(e, it, dy = 0.2f) }
        assertEquals(1, promoted.size)
        assertEquals(listOf(other), e.observations)
        val p = promoted.single()
        assertTrue(abs(other.dy - (otherDy - TouchDynamics.MAX_PROMOTION)) < 1e-4f, "${other.dy}")
        for (r in 0 until TouchDynamics.REGIONS) assertEquals(otherRegionDy[r] - p.regionDy[r], other.regionDy[r], 1e-6f)
    }

    @Test fun bucketsFollowTheHour() {
        assertEquals(Bucket.MORNING, Bucket.of(5))
        assertEquals(Bucket.DAY, Bucket.of(11))
        assertEquals(Bucket.EVENING, Bucket.of(21))
        assertEquals(Bucket.NIGHT, Bucket.of(22))
        assertEquals(Bucket.NIGHT, Bucket.of(3))
        assertEquals(1, Bucket.NIGHT.steps(Bucket.MORNING))
        assertEquals(2, Bucket.DAY.steps(Bucket.NIGHT))
    }
}
