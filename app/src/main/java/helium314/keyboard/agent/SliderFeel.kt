// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Stage 28: how a settings slider feels while a finger moves it. A light tick each time the value
 * crosses a tick boundary, and detents (default value, both ends, round values) that pull the value
 * in when the finger comes close and hold it until the finger has gone past the zone. Plain code
 * without Android: [SliderDrag] follows one drag, [Sink] plays the impulses (SliderHaptics on the
 * phone, a list in tests).
 *
 * - Ticks: a discrete slider ticks on its own steps (on every k-th step if it has more than
 *   [MAX_TICKS]); a continuous one on the multiples of 1, 2 or 5 × 10^n that give at most
 *   [MAX_TICKS] (so 20 to 40) ticks over the range; whole numbers only for an [integer] slider.
 * - Round values: the multiples of 1 or 5 × 10^n that give at most [MAX_ROUND_INTERVALS] intervals
 *   (0..1 → 0.1, 0.3..1.5 → 0.5, 0..100 → 10, 100..700 → 100), at least four steps of a discrete
 *   slider; [roundStep] overrides that.
 * - Zone: [ZONE] of the range on each side of a detent. A discrete slider whose step is wider than
 *   the zone only gets the stronger impulse on the detent step.
 */
class SliderFeel(
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int = 0,
    defaultValue: Float? = null,
    val integer: Boolean = false,
    roundStep: Float? = null,
) {
    enum class Impulse { TICK, DETENT }

    /** Plays the impulses of a drag; [start] is called when a finger touches the slider. */
    fun interface Sink {
        fun play(impulse: Impulse)
        fun start() {}
    }

    val span: Float = range.endInclusive - range.start
    /** Distance of two values of a discrete slider, 0 for a continuous one. */
    val stepSize: Float = if (steps > 0 && span > 0) span / (steps + 1) else 0f
    val tickStep: Float
    private val tickOrigin: Float
    val roundStep: Float
    /** Sorted, inside the range, on a step of a discrete slider. */
    val detents: List<Float>
    /** How close the finger must come to pull the value in, and how far it must go to let it go. */
    val radius: Float = if (span > 0) span * ZONE else 0f
    /** A detent the value just left (or started next to) pulls again only beyond this distance. */
    val rearm: Float = radius * 2
    /** A start value this close to a detent counts as on it. */
    val onDetent: Float = max(radius / 4, span * 1e-4f)

    init {
        if (span <= 0f) {
            tickStep = 1f
            tickOrigin = range.start
            this.roundStep = 0f
            detents = emptyList()
        } else {
            if (stepSize > 0f) {
                val every = ceil((steps + 1) / MAX_TICKS.toDouble()).toInt().coerceAtLeast(1)
                tickStep = stepSize * every
                tickOrigin = range.start
            } else {
                val nice = niceStep(span / MAX_TICKS.toDouble(), TICK_FACTORS)
                tickStep = (if (integer) max(nice, 1.0) else nice).toFloat()
                tickOrigin = 0f
            }
            var round = roundStep?.toDouble()?.takeIf { it > 0 } ?: run {
                var auto = niceStep(span / MAX_ROUND_INTERVALS.toDouble(), ROUND_FACTORS)
                if (stepSize > 0f) auto = max(auto, niceStep(4.0 * stepSize, ROUND_FACTORS))
                auto
            }
            if (integer) round = max(round, 1.0)
            this.roundStep = round.toFloat()
            detents = collectDetents(defaultValue, round)
        }
    }

    private fun collectDetents(defaultValue: Float?, round: Double): List<Float> {
        val start = range.start.toDouble()
        val end = range.endInclusive.toDouble()
        val values = mutableListOf(start, end)
        if (defaultValue != null && defaultValue >= range.start && defaultValue <= range.endInclusive)
            values += defaultValue.toDouble()
        var i = ceil(start / round - EPSILON).toLong()
        while (i * round <= end + round * EPSILON) {
            values += i * round
            i++
        }
        val placed = values.map { value ->
            val onStep = if (stepSize > 0f) start + ((value - start) / stepSize).roundToLong() * stepSize.toDouble() else value
            // 0.0 instead of -0.0, and the float of the decimal value (0.30000000000000004 → 0.3f)
            (onStep.coerceIn(start, end) + 0.0).toFloat()
        }.sorted()
        val result = mutableListOf<Float>()
        for (value in placed) if (result.isEmpty() || value - result.last() > span * 1e-4f) result += value
        return result
    }

    /** Which tick interval [value] lies in; a tick plays when this changes. */
    fun bucket(value: Float): Long = floor((value - tickOrigin) / tickStep.toDouble() + EPSILON).toLong()

    companion object {
        /** Half the width of a detent's zone, as a fraction of the range. */
        const val ZONE = 0.015f
        const val MAX_TICKS = 40
        const val MAX_ROUND_INTERVALS = 11
        /** At most one tick per this many milliseconds (a detent may follow a tick at once). */
        const val MIN_GAP_MS = 25L
        private const val EPSILON = 1e-4
        private val TICK_FACTORS = doubleArrayOf(1.0, 2.0, 5.0, 10.0)
        private val ROUND_FACTORS = doubleArrayOf(1.0, 5.0, 10.0)

        /** The smallest factor × 10^n that is at least [min]. */
        fun niceStep(min: Double, factors: DoubleArray): Double {
            if (min <= 0.0 || min.isNaN() || min.isInfinite()) return 1.0
            val base = 10.0.pow(floor(log10(min)))
            for (factor in factors) if (factor * base >= min * (1 - 1e-9)) return factor * base
            return 10 * base
        }
    }
}

/**
 * One drag on a slider with [feel]: [begin] when a finger touches it, then [next] for every value
 * the slider reports from the finger; [next] returns the value to show and plays the impulses on
 * [sink]. Values that do not come from a finger (TalkBack, keys) bypass this class, so every value
 * stays reachable; by finger, a value next to a detent is reached by leaving the zone and coming
 * back (the detent then pulls only after the finger was [SliderFeel.rearm] away).
 */
class SliderDrag(
    private val feel: SliderFeel,
    private val sink: SliderFeel.Sink,
    private val clock: () -> Long = { clockForTest?.invoke() ?: android.os.SystemClock.uptimeMillis() },
) {
    private var held: Int = -1
    private val disarmed = HashSet<Int>()
    private var lastRaw = Float.NaN
    private var lastBucket = 0L
    private var lastTickAt = Long.MIN_VALUE / 2
    private var lastDetentAt = Long.MIN_VALUE / 2

    /** The detent the value is held at, or null. */
    val heldAt: Float? get() = feel.detents.getOrNull(held)

    fun begin(value: Float) {
        held = -1
        disarmed.clear()
        feel.detents.forEachIndexed { i, detent ->
            val distance = abs(value - detent)
            if (distance <= feel.onDetent && (held < 0 || distance < abs(value - feel.detents[held]))) held = i
            else if (distance <= feel.rearm) disarmed += i
        }
        if (held >= 0) disarmed.remove(held)
        lastRaw = value
        lastBucket = feel.bucket(heldAt ?: value)
        sink.start()
    }

    companion object {
        /** Replaced in UI tests, whose input events do not move the system clock. */
        @JvmStatic var clockForTest: (() -> Long)? = null
    }

    /** The value to show for the finger at [raw]. */
    fun next(raw: Float): Float {
        val detents = feel.detents
        disarmed.removeAll { abs(raw - detents[it]) > feel.rearm }
        var entered = false
        val shown: Float
        if (held >= 0 && abs(raw - detents[held]) <= feel.radius) {
            shown = detents[held]
        } else {
            if (held >= 0) {
                disarmed += held
                held = -1
            }
            var nearest = -1
            detents.forEachIndexed { i, detent ->
                val distance = abs(raw - detent)
                if (i !in disarmed && distance <= feel.radius && (nearest < 0 || distance < abs(raw - detents[nearest]))) nearest = i
            }
            if (nearest >= 0) {
                held = nearest
                entered = true
                shown = detents[nearest]
            } else shown = raw
        }
        // a detent crossed in one move (a quick drag or a tap far away) is felt, though it does not hold
        val passed = !entered && held < 0 && !lastRaw.isNaN() &&
            detents.indices.any { it !in disarmed && (lastRaw - detents[it]) * (raw - detents[it]) < 0 }
        val bucket = feel.bucket(shown)
        val now = clock()
        if (entered || passed) {
            if (now - lastDetentAt >= SliderFeel.MIN_GAP_MS) {
                sink.play(SliderFeel.Impulse.DETENT)
                lastDetentAt = now
                lastTickAt = now
            }
        } else if (bucket != lastBucket && now - lastTickAt >= SliderFeel.MIN_GAP_MS) {
            sink.play(SliderFeel.Impulse.TICK)
            lastTickAt = now
        }
        lastBucket = bucket
        lastRaw = raw
        return shown
    }
}
