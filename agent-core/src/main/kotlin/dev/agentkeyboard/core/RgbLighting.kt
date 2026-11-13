// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Spatial shape of the light (stage 26). */
enum class RgbPattern { WAVE_HORIZONTAL, WAVE_VERTICAL, WAVE_DIAGONAL, CIRCLE, BREATHE, STATIC_RAINBOW }

/** Temporal shape: how the colour moves through the palette during one cycle. */
enum class RgbWaveform(val cyclic: Boolean) {
    /** Soft back and forth through the palette. */
    SINE(false),
    /** Linear back and forth (zigzag). */
    TRIANGLE(false),
    /** Rises through the whole palette, then starts over. */
    SAWTOOTH(true),
    /** Like [SAWTOOTH], but in hard steps. */
    STEPS(true),
    /** Eased rise, short hold, eased fall, rest. */
    PULSE(false),
}

/** What the light colours. */
enum class RgbLayer { KEY_BACKGROUND, KEYBOARD_BACKGROUND, LABEL }

/** Which parts of the keyboard are lit. The two strip areas only colour text and icons. */
enum class RgbArea(val inStrip: Boolean = false) {
    LETTERS, FUNCTIONAL, SPACE, NUMBER_ROW, SUGGESTIONS(true), TOOLBAR(true);

    companion object {
        val KEYS = setOf(LETTERS, FUNCTIONAL, SPACE, NUMBER_ROW)
    }
}

/** What a key press does to the light. */
enum class RgbTapReaction { NONE, FLASH, RIPPLE, SPEED_UP }

/**
 * Every option of the RGB lighting. Construct only through [normalized] values: [parse] never
 * throws and clamps every number, so an imported or damaged value can never break drawing.
 */
data class RgbSettings(
    val enabled: Boolean = false,
    val pattern: RgbPattern = RgbPattern.WAVE_HORIZONTAL,
    val waveform: RgbWaveform = RgbWaveform.SAWTOOTH,
    val steps: Int = 4,
    val periodMs: Int = 4000,
    val reverse: Boolean = false,
    /** Colour cycles across the keyboard width (or height, or radius). */
    val waveWidth: Float = 1f,
    val rainbow: Boolean = true,
    /** Own palette, opaque RGB, used when [rainbow] is false. */
    val palette: List<Int> = DEFAULT_PALETTE,
    val saturation: Int = 100,
    val brightness: Int = 100,
    /** Share of the light against the theme colour, in percent. */
    val mix: Int = 100,
    val layers: Set<RgbLayer> = setOf(RgbLayer.KEY_BACKGROUND),
    val areas: Set<RgbArea> = RgbArea.KEYS,
    val tapReaction: RgbTapReaction = RgbTapReaction.NONE,
    /** Light is invisible until a key is tapped, then appears and fades out. */
    val reactiveOnly: Boolean = false,
    /** Fade time of tap effects (flash, ripple, speed-up, reactive-only). */
    val glowMs: Int = 800,
    val fps: Int = 30,
    val pauseInPowerSave: Boolean = true,
) {
    fun normalized(): RgbSettings = copy(
        steps = steps.coerceIn(STEPS_RANGE),
        periodMs = periodMs.coerceIn(PERIOD_RANGE),
        waveWidth = (if (waveWidth.isFinite()) waveWidth else 1f).coerceIn(WIDTH_MIN, WIDTH_MAX),
        palette = palette.map { it or OPAQUE }.take(MAX_COLORS).let { if (it.size < MIN_COLORS) DEFAULT_PALETTE else it },
        saturation = saturation.coerceIn(0, 100),
        brightness = brightness.coerceIn(BRIGHTNESS_MIN, 100),
        mix = mix.coerceIn(0, 100),
        glowMs = glowMs.coerceIn(GLOW_RANGE),
        fps = if (fps >= 60) 60 else 30,
    )

    /** True when anything on the keyboard view is lit (the keyboard background does not depend on the areas). */
    fun lightsKeys(): Boolean = enabled && (RgbLayer.KEYBOARD_BACKGROUND in layers ||
        (layers.any { it != RgbLayer.KEYBOARD_BACKGROUND } && areas.any { !it.inStrip }))
    fun lightsStrip(): Boolean = enabled && areas.any { it.inStrip }

    /** Compact, stable text form for one preference and for profiles. */
    fun encode(): String {
        val n = normalized()
        return listOf(
            "v" to "1",
            "on" to flag(n.enabled),
            "pattern" to n.pattern.name,
            "wave" to n.waveform.name,
            "steps" to n.steps.toString(),
            "period" to n.periodMs.toString(),
            "reverse" to flag(n.reverse),
            "width" to ((n.waveWidth * 100).roundToInt() / 100f).toString(),
            "palette" to if (n.rainbow) "rainbow" else "own",
            "colors" to n.palette.joinToString(",") { hex(it) },
            "sat" to n.saturation.toString(),
            "bright" to n.brightness.toString(),
            "mix" to n.mix.toString(),
            "layers" to RgbLayer.entries.filter { it in n.layers }.joinToString(",") { it.name },
            "areas" to RgbArea.entries.filter { it in n.areas }.joinToString(",") { it.name },
            "tap" to n.tapReaction.name,
            "reactive" to flag(n.reactiveOnly),
            "glow" to n.glowMs.toString(),
            "fps" to n.fps.toString(),
            "saver" to flag(n.pauseInPowerSave),
        ).joinToString(";") { (k, v) -> "$k=$v" }
    }

    companion object {
        val DEFAULT_PALETTE = listOf(0xFFFF0050.toInt(), 0xFF00C8FF.toInt(), 0xFF9B4DFF.toInt())
        val STEPS_RANGE = 2..12
        val PERIOD_RANGE = 500..30_000
        val GLOW_RANGE = 200..5_000
        const val WIDTH_MIN = 0.25f
        const val WIDTH_MAX = 4f
        const val BRIGHTNESS_MIN = 10
        const val MIN_COLORS = 2
        const val MAX_COLORS = 6
        const val MAX_ENCODED_LENGTH = 1024
        private const val OPAQUE = 0xFF000000.toInt()
        /** Declared after [DEFAULT_PALETTE]: companion values initialise in order. */
        val DEFAULT = RgbSettings()

        /**
         * Reads [encoded]; anything missing, unknown or out of range falls back to the default of
         * that one field (numbers are clamped). Null, empty or oversized text gives [DEFAULT].
         */
        fun parse(encoded: String?): RgbSettings {
            if (encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_LENGTH) return DEFAULT
            val map = encoded.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            val d = DEFAULT
            fun bool(key: String, default: Boolean) = when (map[key]) { "1" -> true; "0" -> false; else -> default }
            fun int(key: String, default: Int) = map[key]?.toIntOrNull() ?: default
            return RgbSettings(
                enabled = bool("on", d.enabled),
                pattern = enumOr(map["pattern"], d.pattern),
                waveform = enumOr(map["wave"], d.waveform),
                steps = int("steps", d.steps),
                periodMs = int("period", d.periodMs),
                reverse = bool("reverse", d.reverse),
                waveWidth = map["width"]?.toFloatOrNull()?.takeIf { it.isFinite() } ?: d.waveWidth,
                rainbow = when (map["palette"]) { "own" -> false; "rainbow" -> true; else -> d.rainbow },
                palette = map["colors"]?.split(',')?.mapNotNull { parseHex(it) } ?: d.palette,
                saturation = int("sat", d.saturation),
                brightness = int("bright", d.brightness),
                mix = int("mix", d.mix),
                layers = map["layers"]?.let { enumSet<RgbLayer>(it) } ?: d.layers,
                areas = map["areas"]?.let { enumSet<RgbArea>(it) } ?: d.areas,
                tapReaction = enumOr(map["tap"], d.tapReaction),
                reactiveOnly = bool("reactive", d.reactiveOnly),
                glowMs = int("glow", d.glowMs),
                fps = int("fps", d.fps),
                pauseInPowerSave = bool("saver", d.pauseInPowerSave),
            ).normalized()
        }

        private fun flag(value: Boolean) = if (value) "1" else "0"
        fun hex(color: Int) = String.format(java.util.Locale.ROOT, "%06X", color and 0xFFFFFF)
        fun parseHex(text: String): Int? {
            val t = text.trim().removePrefix("#")
            if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return t.toInt(16) or OPAQUE
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default

        private inline fun <reified E : Enum<E>> enumSet(list: String): Set<E> =
            list.split(',').mapNotNull { name -> enumValues<E>().firstOrNull { it.name == name.trim() } }.toSet()
    }
}

/** Pure ARGB colour arithmetic (no android.graphics). */
object RgbColors {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    /** Minimum contrast of a key label against what is behind it while the light is on (WCAG large text). */
    const val MIN_LABEL_CONTRAST = 3.0

    fun alpha(c: Int) = c ushr 24
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Linear blend of all four channels; [t] 0 gives [a], 1 gives [b]. */
    fun lerp(a: Int, b: Int, t: Float): Int {
        val f = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * f).roundToInt()
        return argb(ch(alpha(a), alpha(b)), ch(red(a), red(b)), ch(green(a), green(b)), ch(blue(a), blue(b)))
    }

    /** [h] in degrees, [s] and [v] 0..1; opaque. Never throws: NaN or infinite input counts as 0. */
    fun hsv(h: Float, s: Float, v: Float): Int {
        val hue = ((finite(h) % 360f) + 360f) % 360f / 60f
        val sat = finite(s).coerceIn(0f, 1f)
        val value = finite(v).coerceIn(0f, 1f)
        val i = floor(hue).toInt()
        val f = hue - i
        val p = value * (1 - sat)
        val q = value * (1 - sat * f)
        val t = value * (1 - sat * (1 - f))
        val (r, g, b) = when (i) {
            0 -> Triple(value, t, p)
            1 -> Triple(q, value, p)
            2 -> Triple(p, value, t)
            3 -> Triple(p, q, value)
            4 -> Triple(t, p, value)
            else -> Triple(value, p, q)
        }
        return argb(255, (r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
    }

    /** Hue (degrees), saturation and value of [c]. */
    fun toHsv(c: Int): FloatArray {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, if (mx == 0f) 0f else d / mx, mx)
    }

    private fun finite(x: Float) = if (x.isFinite()) x else 0f

    /** [top] painted over an opaque [bottom]; the result is opaque. */
    fun over(top: Int, bottom: Int): Int = lerp(bottom or BLACK, top or BLACK, alpha(top) / 255f)

    /**
     * A light value from [RgbEngine.light] (alpha = strength) applied on top of a theme colour.
     * A (semi-)transparent theme colour is blended with premultiplied alpha, so painting the result
     * over a background gives the same as the light over the theme over that background: a
     * transparent theme colour gives the light itself with the strength as alpha (not darkened).
     */
    fun applyLight(theme: Int, light: Int): Int {
        val strength = alpha(light) / 255f
        if (strength <= 0f) return theme
        val themeAlpha = alpha(theme)
        if (themeAlpha == 255) return lerp(theme, light or BLACK, strength)
        val ta = themeAlpha / 255f
        val a = ta + (1f - ta) * strength
        fun ch(t: Int, l: Int) = ((t * ta * (1f - strength) + l * strength) / a).roundToInt()
        return argb((a * 255f).roundToInt(), ch(red(theme), red(light)), ch(green(theme), green(light)), ch(blue(theme), blue(light)))
    }

    fun luminance(c: Int): Double {
        fun lin(x: Int): Double { val v = x / 255.0; return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(red(c)) + 0.7152 * lin(green(c)) + 0.0722 * lin(blue(c))
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /**
     * [fg] moved towards black or white (whichever contrasts more with [bg]) just far enough to
     * reach [minContrast] against the opaque [bg]. Alpha of [fg] is kept, and the contrast is that
     * of the colour actually seen: [fg] with its alpha painted over [bg]. A fully transparent [fg]
     * is returned unchanged; one so transparent that even black or white cannot reach
     * [minContrast] becomes black or white (the best possible with that alpha).
     */
    fun readable(fg: Int, bg: Int, minContrast: Double = MIN_LABEL_CONTRAST): Int {
        val back = bg or BLACK
        if (alpha(fg) == 0) return fg
        fun seen(rgb: Int) = over((rgb and 0xFFFFFF) or (fg and BLACK), back)
        if (contrast(seen(fg), back) >= minContrast) return fg
        val target = if (contrast(WHITE, back) >= contrast(BLACK, back)) WHITE else BLACK
        var lo = 0f
        var hi = 1f
        repeat(10) {
            val mid = (lo + hi) / 2
            if (contrast(seen(lerp(fg or BLACK, target, mid)), back) >= minContrast) hi = mid else lo = mid
        }
        return (lerp(fg or BLACK, target, hi) and 0xFFFFFF) or (fg and BLACK)
    }

    /**
     * Colour of a label (or icon) in front of the opaque [behind]: lit with [light] when labels are
     * a layer ([labelLayer]), then made readable as seen with its alpha. Alpha of [theme] is kept.
     */
    fun label(theme: Int, light: Int, labelLayer: Boolean, behind: Int): Int {
        val color = if (labelLayer) applyLight(theme or BLACK, light) else theme or BLACK
        return readable((color and 0xFFFFFF) or (theme and BLACK), behind)
    }

    /** Visible feedback for a pressed key whose colour comes from the light. */
    fun pressed(c: Int): Int {
        val a = c and BLACK
        return if (luminance(c) > 0.4) lerp(c, a, 0.25f) else lerp(c, 0xFFFFFF or a, 0.3f)
    }
}

/** The value of a waveform at cycle position [u] (0..1), result 0..1. */
object RgbWave {
    fun value(waveform: RgbWaveform, u: Float, steps: Int): Float {
        val x = u - floor(u)
        return when (waveform) {
            RgbWaveform.SINE -> (0.5 - 0.5 * cos(2 * Math.PI * x)).toFloat()
            RgbWaveform.TRIANGLE -> 1f - abs(2f * x - 1f)
            RgbWaveform.SAWTOOTH -> x
            RgbWaveform.STEPS -> min(floor(x * steps) / steps, 1f)
            RgbWaveform.PULSE -> when {
                x < 0.3f -> smooth(x / 0.3f)
                x < 0.45f -> 1f
                x < 0.75f -> 1f - smooth((x - 0.45f) / 0.3f)
                else -> 0f
            }
        }
    }

    fun smooth(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
}

/**
 * Colour of the light for every point and time. Coordinates are 0..1 across the lit area
 * ([aspect] = height / width keeps circles round). Single-threaded: call from the UI thread.
 * Allocation-free per call, so it can run for every key in every frame.
 */
class RgbEngine(settings: RgbSettings) {
    val settings: RgbSettings = settings.normalized()
    /** Height / width of the lit area; NaN or infinite values (a view without size) are ignored, others clamped. */
    var aspect = 0.45f
        set(value) { if (value.isFinite()) field = value.coerceIn(ASPECT_MIN, ASPECT_MAX) }

    /** Position in cycles; grows with time (and faster after a tap with [RgbTapReaction.SPEED_UP]). */
    var phase = 0.0
        private set
    private var lastAdvanceMs = Long.MIN_VALUE

    private val tapX = FloatArray(MAX_TAPS)
    private val tapY = FloatArray(MAX_TAPS)
    private val tapKey = IntArray(MAX_TAPS)
    private val tapTime = LongArray(MAX_TAPS) { Long.MIN_VALUE }
    private var nextTap = 0

    private val palette: IntArray = this.settings.let { s ->
        s.palette.map { c ->
            val hsv = RgbColors.toHsv(c)
            RgbColors.hsv(hsv[0], hsv[1] * s.saturation / 100f, hsv[2] * s.brightness / 100f)
        }.toIntArray()
    }

    /** Moves the animation to [nowMs]. Long gaps (paused keyboard) do not jump. */
    fun advance(nowMs: Long) {
        val last = lastAdvanceMs
        lastAdvanceMs = nowMs
        if (last == Long.MIN_VALUE || settings.pattern == RgbPattern.STATIC_RAINBOW) return
        val dt = (nowMs - last).coerceIn(0L, MAX_STEP_MS)
        val boost = if (settings.tapReaction == RgbTapReaction.SPEED_UP) 1f + SPEED_BOOST * globalFade(nowMs) else 1f
        phase += dt.toDouble() / settings.periodMs * boost
        if (phase > 1e6) phase -= floor(phase)
    }

    /** Restarts timing without changing the phase (after a pause). */
    fun resetClock() { lastAdvanceMs = Long.MIN_VALUE }

    fun tap(x: Float, y: Float, keyId: Int, nowMs: Long) {
        tapX[nextTap] = x
        tapY[nextTap] = y
        tapKey[nextTap] = keyId
        tapTime[nextTap] = nowMs
        nextTap = (nextTap + 1) % MAX_TAPS
    }

    fun clearTaps() { tapTime.fill(Long.MIN_VALUE) }

    /** True while any tap effect is still fading. */
    fun hasActiveEffect(nowMs: Long): Boolean =
        (settings.tapReaction != RgbTapReaction.NONE || settings.reactiveOnly) &&
            tapTime.any { it != Long.MIN_VALUE && nowMs - it in 0 until settings.glowMs }

    /**
     * The light at ([x], [y]) for element [keyId] (a key's id, or -1 for the keyboard background
     * and the strip). The colour is opaque RGB in the low bits; alpha is the strength 0..255
     * including the mix setting. Alpha 0 means "theme colour unchanged".
     */
    fun light(x0: Float, y0: Float, keyId: Int, nowMs: Long): Int {
        val s = settings
        if (!s.enabled) return 0
        // never throws: a position from a view without size (NaN, infinite) counts as 0
        val x = if (x0.isFinite()) x0 else 0f
        val y = if (y0.isFinite()) y0 else 0f
        val spatial = when (s.pattern) {
            RgbPattern.WAVE_HORIZONTAL, RgbPattern.STATIC_RAINBOW -> x
            RgbPattern.WAVE_VERTICAL -> y
            RgbPattern.WAVE_DIAGONAL -> (x + y) / 2f
            RgbPattern.CIRCLE -> {
                val dx = x - 0.5f
                val dy = (y - 0.5f) * aspect
                sqrt(dx * dx + dy * dy) / sqrt(0.25f + 0.25f * aspect * aspect)
            }
            RgbPattern.BREATHE -> 0f
        } * s.waveWidth
        // forward: the colours travel towards +x (+y, outwards); reverse: the other way
        val raw = if (s.reverse) phase + spatial else phase - spatial
        val u = (raw - floor(raw)).toFloat()
        val v = RgbWave.value(s.waveform, u, s.steps)
        var color = paletteAt(v, s.waveform.cyclic)
        var strength = if (s.pattern == RgbPattern.BREATHE) 0.1f + 0.9f * v else 1f
        when (s.tapReaction) {
            RgbTapReaction.FLASH -> {
                val f = keyFade(keyId, nowMs)
                if (s.reactiveOnly) strength *= f
                else if (f > 0f) {
                    color = RgbColors.lerp(color, RgbColors.WHITE, f * HIGHLIGHT)
                    strength = max(strength, f)
                }
            }
            RgbTapReaction.RIPPLE -> {
                val r = ring(x, y, nowMs)
                if (s.reactiveOnly) strength *= r
                else if (r > 0f) {
                    color = RgbColors.lerp(color, RgbColors.WHITE, r * HIGHLIGHT)
                    strength = max(strength, r)
                }
            }
            RgbTapReaction.NONE, RgbTapReaction.SPEED_UP -> if (s.reactiveOnly) strength *= globalFade(nowMs)
        }
        val alpha = (strength * s.mix / 100f * 255f).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0xFFFFFF)
    }

    /** Palette colour at [pos] 0..1: cyclic wraps last to first, otherwise first to last. */
    fun paletteAt(pos: Float, cyclic: Boolean): Int {
        val p = if (pos.isNaN()) 0f else pos.coerceIn(0f, 1f)
        if (settings.rainbow)
            return RgbColors.hsv(p * (if (cyclic) 360f else 300f), settings.saturation / 100f, settings.brightness / 100f)
        val n = palette.size
        val segments = if (cyclic) n else n - 1
        val scaled = p * segments
        val i = min(floor(scaled).toInt(), segments - 1).coerceAtLeast(0)
        return RgbColors.lerp(palette[i % n], palette[(i + 1) % n], scaled - i)
    }

    private fun fade(age: Long): Float {
        if (age < 0 || age >= settings.glowMs) return 0f
        return RgbWave.smooth(1f - age.toFloat() / settings.glowMs)
    }

    private fun globalFade(nowMs: Long): Float {
        var f = 0f
        for (i in 0 until MAX_TAPS) if (tapTime[i] != Long.MIN_VALUE) f = max(f, fade(nowMs - tapTime[i]))
        return f
    }

    private fun keyFade(keyId: Int, nowMs: Long): Float {
        if (keyId < 0) return 0f
        var f = 0f
        for (i in 0 until MAX_TAPS) if (tapTime[i] != Long.MIN_VALUE && tapKey[i] == keyId) f = max(f, fade(nowMs - tapTime[i]))
        return f
    }

    private fun ring(x: Float, y: Float, nowMs: Long): Float {
        var best = 0f
        for (i in 0 until MAX_TAPS) {
            if (tapTime[i] == Long.MIN_VALUE) continue
            val age = nowMs - tapTime[i]
            if (age < 0 || age >= settings.glowMs) continue
            val progress = age.toFloat() / settings.glowMs
            val dx = x - tapX[i]
            val dy = (y - tapY[i]) * aspect
            val d = sqrt(dx * dx + dy * dy)
            val band = 1f - abs(d - progress * RIPPLE_REACH) / RING_WIDTH
            if (band > 0f) best = max(best, band * (1f - progress))
        }
        return best
    }

    companion object {
        const val MAX_TAPS = 8
        /** Longest time step taken at once; a longer gap (paused, hidden) does not jump ahead. */
        const val MAX_STEP_MS = 100L
        const val SPEED_BOOST = 3f
        const val HIGHLIGHT = 0.7f
        /** Distance in keyboard widths a ripple travels during the fade time. */
        const val RIPPLE_REACH = 1.2f
        const val RING_WIDTH = 0.12f
        const val ASPECT_MIN = 0.01f
        const val ASPECT_MAX = 100f
    }
}

/** Whether and how the light runs, from the outside state. Pure, so the battery rules are testable. */
object RgbRun {
    enum class Mode {
        /** No light at all; drawing is unchanged. */
        OFF,
        /** One fixed frame, no frame callbacks, taps ignored (animations off or power saving). */
        STATIC,
        /** Light is drawn, but no frame callbacks are needed right now. */
        PAUSED,
        /** Frame callbacks at the configured rate. */
        RUNNING,
    }

    fun mode(settings: RgbSettings, visible: Boolean, powerSave: Boolean, animationsOn: Boolean, effectActive: Boolean): Mode {
        if (!settings.enabled || (!settings.lightsKeys() && !settings.lightsStrip())) return Mode.OFF
        if (!animationsOn || (powerSave && settings.pauseInPowerSave)) return Mode.STATIC
        if (!visible) return Mode.PAUSED
        val moves = settings.pattern != RgbPattern.STATIC_RAINBOW && !settings.reactiveOnly
        return if (moves || effectActive) Mode.RUNNING else Mode.PAUSED
    }
}

/** Caps the frame rate on displays faster than [fps]. */
class RgbFramePacer(fps: Int) {
    val intervalNanos: Long = 1_000_000_000L / (if (fps >= 60) 60 else 30)
    private var lastNanos = Long.MIN_VALUE

    /** True if a frame at [frameNanos] should be drawn; a little slack absorbs vsync jitter. */
    fun shouldRender(frameNanos: Long): Boolean {
        if (lastNanos != Long.MIN_VALUE && frameNanos - lastNanos < intervalNanos - SLACK_NANOS) return false
        lastNanos = frameNanos
        return true
    }

    fun reset() { lastNanos = Long.MIN_VALUE }

    /**
     * Time from [nowNanos] until the next frame [shouldRender] would accept (0 = the next frame).
     * The caller asks for its next frame only then instead of on every vsync.
     */
    fun nanosUntilDue(nowNanos: Long): Long =
        if (lastNanos == Long.MIN_VALUE) 0L else (lastNanos + intervalNanos - SLACK_NANOS - nowNanos).coerceAtLeast(0L)

    companion object {
        const val SLACK_NANOS = 4_000_000L
    }
}
