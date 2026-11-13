// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import helium314.keyboard.latin.settings.SettingsValues

/**
 * The feel of the space bar detents (stage 33, [SpaceDetents]): a light tick at each detent and a
 * stronger, longer "stretch" at the last one, where the swipe's action runs. A phone has one motor
 * (P67), so the two differ in strength and length only. Until build 12 this object played three
 * ticks right after undo or redo; the owner wanted the ticks on the swipe path instead.
 * The choice of what the vibrator can play ([plan]) is a plain function, best first: haptic
 * primitives (API 30+), a waveform with amplitudes (API 26+ with amplitude control), plain on/off
 * pulses. [Device] is the seam for tests.
 */
object HapticChain {
    enum class Feel {
        /** A detent on the way: one light, short tick. */
        DETENT,
        /** The last detent, where the action runs: rising strength into a heavy impulse, clearly longer. */
        STRETCH,
    }

    /** `VibrationEffect.Composition` primitives as constants for any API; THUD exists from API 31 on. */
    const val PRIMITIVE_CLICK = 1
    const val PRIMITIVE_THUD = 2
    const val PRIMITIVE_QUICK_RISE = 4
    const val PRIMITIVE_TICK = 7

    /** Length of a detent tick where it is a pulse or a waveform. */
    const val TICK_MS = 12L
    /** Strength of a detent tick where the strength can be set, 0..1. */
    const val TICK_STRENGTH = 0.6f
    /** The stretch where only on/off works: one long pulse. */
    const val STRETCH_MS = 70L
    /** The stretch as a waveform: four parts (ms, strength 0..1) rising to full strength, 90 ms in all. */
    private val STRETCH_RAMP = listOf(20L to 0.25f, 20L to 0.45f, 20L to 0.7f, 30L to 1f)

    /** One haptic primitive with its scale (0..1) and the delay after the end of the previous one (P67). */
    data class Step(val primitive: Int, val scale: Float, val delayMs: Int = 0)

    /** What the vibrator can play, best first. */
    sealed class Plan {
        /** API 30+: haptic primitives. */
        data class Primitives(val steps: List<Step>) : Plan()
        /** API 26+ with amplitude control: a waveform, off/on timings with amplitudes 0..255. */
        data class Waveform(val timings: LongArray, val amplitudes: IntArray) : Plan() {
            override fun equals(other: Any?) = other is Waveform && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)
            override fun hashCode() = timings.contentHashCode() * 31 + amplitudes.contentHashCode()
        }
        /** Anything else: plain pulses (off/on timings, the first is the start delay). */
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
        /** Only asked from API 30 on (THUD from API 31 on). */
        fun supportsPrimitive(primitive: Int): Boolean
        fun play(plan: Plan)
    }

    fun plan(feel: Feel, device: Device): Plan = when (feel) {
        Feel.DETENT -> detent(device)
        Feel.STRETCH -> stretch(device)
    }

    private fun amplitude(strength: Float) = (strength * 255).toInt().coerceIn(1, 255)

    private fun detent(device: Device): Plan {
        if (device.sdk >= Build.VERSION_CODES.R) {
            // the tick is the lightest primitive; a softer click where the tick is missing
            if (device.supportsPrimitive(PRIMITIVE_TICK)) return Plan.Primitives(listOf(Step(PRIMITIVE_TICK, 0.8f)))
            if (device.supportsPrimitive(PRIMITIVE_CLICK)) return Plan.Primitives(listOf(Step(PRIMITIVE_CLICK, 0.5f)))
        }
        if (device.sdk >= Build.VERSION_CODES.O && device.hasAmplitudeControl)
            return Plan.Waveform(longArrayOf(0, TICK_MS), intArrayOf(0, amplitude(TICK_STRENGTH)))
        return Plan.Pulses(longArrayOf(0, TICK_MS))
    }

    private fun stretch(device: Device): Plan {
        if (device.sdk >= Build.VERSION_CODES.R && device.supportsPrimitive(PRIMITIVE_QUICK_RISE)) {
            // the rise is the resistance, the heavy impulse the give at the end; without both the waveform is closer
            val heavy = when {
                device.sdk >= Build.VERSION_CODES.S && device.supportsPrimitive(PRIMITIVE_THUD) -> PRIMITIVE_THUD
                device.supportsPrimitive(PRIMITIVE_CLICK) -> PRIMITIVE_CLICK
                else -> null
            }
            if (heavy != null) return Plan.Primitives(listOf(Step(PRIMITIVE_QUICK_RISE, 0.7f), Step(heavy, 1f)))
        }
        if (device.sdk >= Build.VERSION_CODES.O && device.hasAmplitudeControl) {
            val timings = LongArray(STRETCH_RAMP.size + 1) { i -> if (i == 0) 0L else STRETCH_RAMP[i - 1].first }
            val amplitudes = IntArray(STRETCH_RAMP.size + 1) { i -> if (i == 0) 0 else amplitude(STRETCH_RAMP[i - 1].second) }
            return Plan.Waveform(timings, amplitudes)
        }
        return Plan.Pulses(longArrayOf(0, STRETCH_MS))
    }

    /**
     * Plays [feel] if key vibration is on in the keyboard settings (which also means the phone has a
     * vibrator), and not in Do Not Disturb unless vibrating there is allowed, like HeliBoard's key
     * vibration. Like that vibration it ignores the system's touch-feedback switch: the keyboard's
     * own setting decides.
     */
    fun play(context: Context, settings: SettingsValues?, feel: Feel, device: Device? = null) {
        if (!allowed(context, settings)) return
        val target = device ?: deviceFor(context) ?: return
        if (!target.hasVibrator) return
        runCatching { target.play(plan(feel, target)) } // a vendor vibrator may refuse: no feedback then
    }

    internal fun allowed(context: Context, settings: SettingsValues?): Boolean {
        if (settings == null || !settings.mVibrateOn) return false
        return settings.mVibrateInDndMode || !doNotDisturb(context)
    }

    /** The same source as LatinIME's ringer-mode receiver. */
    private fun doNotDisturb(context: Context) =
        runCatching { android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode") != 0 }.getOrDefault(false)

    /** Replaced in tests: the vibrator the feedback plays on. */
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
                    plan.steps.forEach { composition.addPrimitive(it.primitive, it.scale, it.delayMs) }
                    composition.compose()
                }
                is Plan.Waveform -> VibrationEffect.createWaveform(plan.timings, plan.amplitudes, -1)
                is Plan.Pulses -> VibrationEffect.createWaveform(plan.timings, -1)
            }
            // No touch usage: like the keyboard's own key vibration (AudioAndHapticFeedbackManager.vibrate),
            // the feedback follows the keyboard's vibration setting, not the system's touch-feedback switch.
            vibrator.vibrate(effect)
        }
    }
}
