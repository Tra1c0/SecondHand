// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import helium314.keyboard.latin.settings.SettingsValues

/**
 * Three short ticks after undo or redo by a space gesture. The owner asked for "left to right" and
 * back; a phone has one vibration motor, so the direction is felt as strength instead: redo rises
 * (weak, medium, strong), undo falls. The pattern ([pattern]) and the choice of what the vibrator
 * can play ([plan]) are plain functions; [Device] is the seam for tests.
 */
object HapticChain {
    enum class Direction { RISING, FALLING }

    /** Milliseconds between the starts of two ticks. */
    const val GAP_MS = 50L
    /** Length of one tick where it is a plain pulse. */
    const val PULSE_MS = 12L
    /** Strength of the ticks from first to last, 0..1. */
    private val RISING = floatArrayOf(0.3f, 0.6f, 1f)

    /** One tick: when it starts (ms after the first) and how strong it is (0..1). */
    data class Tick(val atMs: Long, val strength: Float)

    fun pattern(direction: Direction): List<Tick> {
        val strengths = if (direction == Direction.RISING) RISING.toList() else RISING.reversed()
        return strengths.mapIndexed { i, strength -> Tick(i * GAP_MS, strength) }
    }

    /** What the vibrator can play, best first. */
    sealed class Plan {
        /** API 30+: haptic primitives with a scale each, [delaysMs] before each primitive. */
        data class Primitives(val primitive: Int, val scales: List<Float>, val delaysMs: List<Int>) : Plan()
        /** API 26+ with amplitude control: a waveform, off/on timings with amplitudes 1..255. */
        data class Waveform(val timings: LongArray, val amplitudes: IntArray) : Plan() {
            override fun equals(other: Any?) = other is Waveform && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)
            override fun hashCode() = timings.contentHashCode() * 31 + amplitudes.contentHashCode()
        }
        /** Anything else: three equal pulses (off/on timings, the first is the start delay). */
        data class Pulses(val timings: LongArray) : Plan() {
            override fun equals(other: Any?) = other is Pulses && timings.contentEquals(other.timings)
            override fun hashCode() = timings.contentHashCode()
        }
    }

    /** What a vibrator offers, and how to play a [Plan] on it. */
    interface Device {
        val sdk: Int
        val hasVibrator: Boolean
        val hasAmplitudeControl: Boolean
        /** Only asked from API 30 on. */
        fun supportsPrimitive(primitive: Int): Boolean
        fun play(plan: Plan)
    }

    /** `VibrationEffect.Composition.PRIMITIVE_TICK` / `PRIMITIVE_CLICK` (API 30), as constants for any API. */
    const val PRIMITIVE_CLICK = 1
    const val PRIMITIVE_TICK = 7

    fun plan(direction: Direction, device: Device): Plan {
        val ticks = pattern(direction)
        if (device.sdk >= Build.VERSION_CODES.R) {
            // the tick is the lightest primitive; a click is the fallback where the tick is missing
            val primitive = listOf(PRIMITIVE_TICK, PRIMITIVE_CLICK).firstOrNull { device.supportsPrimitive(it) }
            if (primitive != null) {
                // a delay counts from the end of the previous primitive; a tick lasts about 10 ms,
                // so the gap is a little shorter than GAP_MS (close enough to feel the same rhythm)
                val delays = ticks.mapIndexed { i, _ -> if (i == 0) 0 else (GAP_MS - PULSE_MS).toInt() }
                return Plan.Primitives(primitive, ticks.map { it.strength }, delays)
            }
        }
        val timings = LongArray(ticks.size * 2) { i -> if (i == 0) 0L else if (i % 2 == 1) PULSE_MS else GAP_MS - PULSE_MS }
        if (device.sdk >= Build.VERSION_CODES.O && device.hasAmplitudeControl) {
            val amplitudes = IntArray(timings.size) { i ->
                if (i % 2 == 0) 0 else (ticks[i / 2].strength * 255).toInt().coerceIn(1, 255)
            }
            return Plan.Waveform(timings, amplitudes)
        }
        return Plan.Pulses(timings)
    }

    /**
     * Plays the chain if key vibration is on in the keyboard settings (which also means the phone
     * has a vibrator), and not in Do Not Disturb unless vibrating there is allowed, like
     * HeliBoard's key vibration. Like that vibration it ignores the system's touch-feedback
     * switch: the keyboard's own setting decides.
     */
    fun play(context: Context, settings: SettingsValues?, direction: Direction, device: Device? = null) {
        if (!allowed(context, settings)) return
        val target = device ?: deviceFor(context) ?: return
        if (!target.hasVibrator) return
        runCatching { target.play(plan(direction, target)) } // a vendor vibrator may refuse: no feedback then
    }

    internal fun allowed(context: Context, settings: SettingsValues?): Boolean {
        if (settings == null || !settings.mVibrateOn) return false
        return settings.mVibrateInDndMode || !doNotDisturb(context)
    }

    /** The same source as LatinIME's ringer-mode receiver. */
    private fun doNotDisturb(context: Context) =
        runCatching { android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode") != 0 }.getOrDefault(false)

    /** Replaced in tests: the vibrator the chain plays on. */
    @JvmStatic var deviceForTest: Device? = null

    /** The phone's vibrator ([deviceForTest] in tests); also read by the diagnose page (stage 30). */
    internal fun deviceFor(context: Context): Device? {
        deviceForTest?.let { return it }
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return null
        return AndroidDevice(vibrator)
    }

    private class AndroidDevice(private val vibrator: Vibrator) : Device {
        override val sdk = Build.VERSION.SDK_INT
        override val hasVibrator get() = vibrator.hasVibrator()
        override val hasAmplitudeControl get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()
        override fun supportsPrimitive(primitive: Int) =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(primitive)

        override fun play(plan: Plan) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                val timings = when (plan) {
                    is Plan.Pulses -> plan.timings
                    is Plan.Waveform -> plan.timings
                    is Plan.Primitives -> return
                }
                @Suppress("DEPRECATION") vibrator.vibrate(timings, -1)
                return
            }
            val effect = when (plan) {
                is Plan.Primitives -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
                    val composition = VibrationEffect.startComposition()
                    plan.scales.forEachIndexed { i, scale -> composition.addPrimitive(plan.primitive, scale, plan.delaysMs[i]) }
                    composition.compose()
                }
                is Plan.Waveform -> VibrationEffect.createWaveform(plan.timings, plan.amplitudes, -1)
                is Plan.Pulses -> VibrationEffect.createWaveform(plan.timings, -1)
            }
            // No touch usage: like the keyboard's own key vibration (AudioAndHapticFeedbackManager.vibrate),
            // the chain follows the keyboard's vibration setting, not the system's touch-feedback switch.
            vibrator.vibrate(effect)
        }
    }
}
