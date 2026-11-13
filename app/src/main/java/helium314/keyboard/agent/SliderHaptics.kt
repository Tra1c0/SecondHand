// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * The impulses of the settings sliders (stage 28): a light tick and a slightly stronger detent.
 * Like the keyboard's own key vibration (and HapticChain) they follow "Vibration bei Tastendruck"
 * and Do Not Disturb, not the system's touch-feedback switch. [plan] is a plain function; [Device]
 * is the seam for tests. Kept apart from HapticChain on purpose: same tiers (primitives, waveform
 * with amplitude, plain pulse), own constants.
 */
object SliderHaptics {
    /** What the vibrator plays for one impulse. */
    sealed class Plan {
        /** API 30+: one haptic primitive with a scale 0..1. */
        data class Primitive(val primitive: Int, val scale: Float) : Plan()
        /** API 26+ with amplitude control: one pulse with an amplitude 1..255. */
        data class OneShot(val ms: Long, val amplitude: Int) : Plan()
        /** Anything else: a plain pulse; only its length differs. */
        data class Pulse(val ms: Long) : Plan()
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

    /** `VibrationEffect.Composition.PRIMITIVE_CLICK` / `PRIMITIVE_TICK` (API 30), as constants for any API. */
    const val PRIMITIVE_CLICK = 1
    const val PRIMITIVE_TICK = 7
    const val TICK_SCALE = 0.5f
    const val DETENT_SCALE = 0.8f
    const val TICK_MS = 8L
    const val DETENT_MS = 16L
    const val TICK_AMPLITUDE = 70
    const val DETENT_AMPLITUDE = 180

    fun plan(impulse: SliderFeel.Impulse, device: Device): Plan {
        val detent = impulse == SliderFeel.Impulse.DETENT
        if (device.sdk >= Build.VERSION_CODES.R) {
            if (!detent && device.supportsPrimitive(PRIMITIVE_TICK)) return Plan.Primitive(PRIMITIVE_TICK, TICK_SCALE)
            if (detent && device.supportsPrimitive(PRIMITIVE_CLICK)) return Plan.Primitive(PRIMITIVE_CLICK, DETENT_SCALE)
            // without a click the detent is the tick at full scale: still stronger than a tick
            if (detent && device.supportsPrimitive(PRIMITIVE_TICK)) return Plan.Primitive(PRIMITIVE_TICK, 1f)
        }
        if (device.sdk >= Build.VERSION_CODES.O && device.hasAmplitudeControl)
            return if (detent) Plan.OneShot(DETENT_MS, DETENT_AMPLITUDE) else Plan.OneShot(TICK_MS, TICK_AMPLITUDE)
        // no amplitude control: a longer pulse is the only honest way to feel stronger (P67)
        return Plan.Pulse(if (detent) DETENT_MS else TICK_MS)
    }

    /** "Vibration bei Tastendruck" is on, and Do Not Disturb is off or vibrating there is allowed. */
    fun allowed(context: Context): Boolean {
        val prefs = context.prefs()
        if (!prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON)) return false
        if (prefs.getBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, Defaults.PREF_VIBRATE_IN_DND_MODE)) return true
        return !doNotDisturb(context)
    }

    /** The same source as LatinIME's ringer-mode receiver. */
    private fun doNotDisturb(context: Context) =
        runCatching { android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode") != 0 }.getOrDefault(false)

    /** The sink of the sliders in [context]: reads the settings once per drag ([SliderFeel.Sink.start]). */
    fun sink(context: Context): SliderFeel.Sink = object : SliderFeel.Sink {
        private var device: Device? = null

        override fun start() {
            device = if (allowed(context)) deviceFor(context)?.takeIf { runCatching { it.hasVibrator }.getOrDefault(false) } else null
        }

        override fun play(impulse: SliderFeel.Impulse) {
            val target = device ?: return
            runCatching { target.play(plan(impulse, target)) } // a vendor vibrator may refuse: no feedback then
        }
    }

    /** Replaced in tests: the vibrator the sliders play on. */
    @JvmStatic var deviceForTest: Device? = null

    private fun deviceFor(context: Context): Device? {
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

        // No touch usage, like the keyboard's key vibration: the keyboard setting decides, not the system switch.
        override fun play(plan: Plan) {
            when (plan) {
                is Plan.Primitive -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(plan.primitive, plan.scale).compose())
                is Plan.OneShot -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    vibrator.vibrate(VibrationEffect.createOneShot(plan.ms, plan.amplitude))
                is Plan.Pulse -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    vibrator.vibrate(VibrationEffect.createOneShot(plan.ms, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vibrator.vibrate(plan.ms)
            }
        }
    }
}
