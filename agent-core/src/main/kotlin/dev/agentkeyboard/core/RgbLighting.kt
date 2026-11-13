// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Spatial shape of the light: the stage 26 waves and the animated textures of stage 29c. */
enum class RgbPattern {
    WAVE_HORIZONTAL, WAVE_VERTICAL, WAVE_DIAGONAL, CIRCLE, BREATHE, STATIC_RAINBOW,
    /** Ink swirling in water (domain-warped noise), dark where there is no ink. */
    INK,
    /** Diagonal rainbow with glowing diamond outlines. */
    DIAMONDS,
    /** Diagonal slats with a hard colour change along a wandering line. */
    SLATS,
    /** Rainbow around the centre with fine diagonal hatching and a dark vignette. */
    HATCH,
    /** LED dot matrix with a flowing rainbow. */
    LED,
    /** Contour lines over soft rainbow hills. */
    TOPO;

    /** Texture patterns (29c) compute a colour field and may have a detail tile ([RgbOverlay]). */
    val textured: Boolean get() = ordinal >= INK.ordinal
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

/**
 * The elements that can each have their own look (stage 29, wish of the owner): key backgrounds,
 * keyboard background, key labels, word suggestions and strip buttons. [code] prefixes the fields
 * of an own look in the stored text.
 */
enum class RgbTarget(val code: String) {
    KEYS("k"), BACKGROUND("b"), LABELS("l"), SUGGESTIONS("s"), TOOLBAR("t");
}

/** What a key press does to the light. */
enum class RgbTapReaction { NONE, FLASH, RIPPLE, SPEED_UP }

/** The part of the settings one element can have on its own (stage 29): pattern, curve, speed, colours. */
data class RgbLook(
    val pattern: RgbPattern = RgbPattern.WAVE_HORIZONTAL,
    val curve: RgbCurve = RgbCurve.DEFAULT,
    val periodMs: Int = 4000,
    val reverse: Boolean = false,
    val waveWidth: Float = 1f,
    val rainbow: Boolean = true,
    val palette: List<Int> = RgbSettings.DEFAULT_PALETTE,
    val saturation: Int = 100,
    val brightness: Int = 100,
    val mix: Int = 100,
) {
    fun applyTo(settings: RgbSettings) = settings.copy(pattern = pattern, curve = curve, periodMs = periodMs, reverse = reverse,
        waveWidth = waveWidth, rainbow = rainbow, palette = palette, saturation = saturation, brightness = brightness, mix = mix)

    companion object {
        fun of(s: RgbSettings) = RgbLook(s.pattern, s.curve, s.periodMs, s.reverse, s.waveWidth, s.rainbow, s.palette,
            s.saturation, s.brightness, s.mix)
    }
}

/**
 * Every option of the RGB lighting. Construct only through [normalized] values: [parse] never
 * throws and clamps every number, so an imported or damaged value can never break drawing.
 *
 * The look fields ([pattern] to [mix]) are the shared look. With [separate] each [RgbTarget] uses
 * its entry in [looks] instead (a missing entry uses the shared look); [forTarget] gives the
 * settings an element is drawn with. Whether an element is lit at all stays in [layers] and [areas].
 */
data class RgbSettings(
    val enabled: Boolean = false,
    val pattern: RgbPattern = RgbPattern.WAVE_HORIZONTAL,
    /** Own "Verlauf" (stage 29d); replaces the fixed waveform of stage 26. */
    val curve: RgbCurve = RgbCurve.DEFAULT,
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
    /** Every key in one colour (its centre), as in stage 26; otherwise the light runs smoothly across the keys (29b). */
    val solidKeys: Boolean = false,
    /** Each element has its own look (from [looks]) instead of the shared one. */
    val separate: Boolean = false,
    val looks: Map<RgbTarget, RgbLook> = emptyMap(),
) {
    fun normalized(): RgbSettings = normalLook().copy(
        glowMs = glowMs.coerceIn(GLOW_RANGE),
        fps = if (fps >= 60) 60 else 30,
        looks = looks.mapValues { (_, look) -> RgbLook.of(look.applyTo(this).normalLook()) },
    )

    private fun normalLook(): RgbSettings = copy(
        periodMs = periodMs.coerceIn(PERIOD_RANGE),
        waveWidth = (if (waveWidth.isFinite()) waveWidth else 1f).coerceIn(WIDTH_MIN, WIDTH_MAX),
        palette = palette.map { it or OPAQUE }.take(MAX_COLORS).let { if (it.size < MIN_COLORS) DEFAULT_PALETTE else it },
        saturation = saturation.coerceIn(0, 100),
        brightness = brightness.coerceIn(BRIGHTNESS_MIN, 100),
        mix = mix.coerceIn(0, 100),
    )

    /** The look [target] uses: its own one when [separate], otherwise the shared one. */
    fun lookFor(target: RgbTarget): RgbLook = if (separate) looks[target] ?: RgbLook.of(this) else RgbLook.of(this)

    /** These settings with the look of [target] in the look fields (what the engine for that element runs). */
    fun forTarget(target: RgbTarget): RgbSettings = if (!separate) this else lookFor(target).applyTo(this)

    /** These settings with [look] for [target] (the shared look unless [separate]). */
    fun withLook(target: RgbTarget, look: RgbLook): RgbSettings =
        if (!separate) look.applyTo(this) else copy(looks = looks + (target to look))

    /** Switches [separate]; switching on gives every element without an own look a copy of the shared one. */
    fun withSeparate(on: Boolean): RgbSettings = if (!on) copy(separate = false) else
        copy(separate = true, looks = RgbTarget.entries.associateWith { looks[it] ?: RgbLook.of(this) })

    /** True when [target] is lit at all (light on, its layer or strip area chosen, and some keys for key targets). */
    fun lights(target: RgbTarget): Boolean = enabled && when (target) {
        RgbTarget.KEYS -> RgbLayer.KEY_BACKGROUND in layers && areas.any { !it.inStrip }
        RgbTarget.BACKGROUND -> RgbLayer.KEYBOARD_BACKGROUND in layers
        RgbTarget.LABELS -> RgbLayer.LABEL in layers && areas.any { !it.inStrip }
        RgbTarget.SUGGESTIONS -> RgbArea.SUGGESTIONS in areas
        RgbTarget.TOOLBAR -> RgbArea.TOOLBAR in areas
    }

    /** True when anything on the keyboard view is lit (the keyboard background does not depend on the areas). */
    fun lightsKeys(): Boolean = enabled && (RgbLayer.KEYBOARD_BACKGROUND in layers ||
        (layers.any { it != RgbLayer.KEYBOARD_BACKGROUND } && areas.any { !it.inStrip }))
    fun lightsStrip(): Boolean = enabled && areas.any { it.inStrip }

    /** True if some lit element moves by itself (not only a still rainbow or light that waits for taps). */
    fun moves(): Boolean = !reactiveOnly && RgbTarget.entries.any { lights(it) && lookFor(it).pattern != RgbPattern.STATIC_RAINBOW }

    /** Compact, stable text form for one preference and for profiles. */
    fun encode(): String {
        val n = normalized()
        val fields = mutableListOf("v" to "2", "on" to flag(n.enabled))
        fields += lookFields("", RgbLook.of(n))
        fields += listOf(
            "layers" to RgbLayer.entries.filter { it in n.layers }.joinToString(",") { it.name },
            "areas" to RgbArea.entries.filter { it in n.areas }.joinToString(",") { it.name },
            "tap" to n.tapReaction.name,
            "reactive" to flag(n.reactiveOnly),
            "glow" to n.glowMs.toString(),
            "fps" to n.fps.toString(),
            "saver" to flag(n.pauseInPowerSave),
            "keys" to if (n.solidKeys) "solid" else "smooth",
            "sep" to flag(n.separate),
        )
        for (target in RgbTarget.entries) n.looks[target]?.let { fields += lookFields(target.code + ".", it) }
        return fields.joinToString(";") { (k, v) -> "$k=$v" }
    }

    companion object {
        val DEFAULT_PALETTE = listOf(0xFFFF0050.toInt(), 0xFF00C8FF.toInt(), 0xFF9B4DFF.toInt())
        /** Steps of the "Stufen" preset (and of `steps=` in stage 26 text). */
        val STEPS_RANGE = 2..12
        val PERIOD_RANGE = 500..30_000
        val GLOW_RANGE = 200..5_000
        const val WIDTH_MIN = 0.25f
        const val WIDTH_MAX = 4f
        const val BRIGHTNESS_MIN = 10
        const val MIN_COLORS = 2
        const val MAX_COLORS = 6
        /** Room for the shared look and five own looks with full curves. */
        const val MAX_ENCODED_LENGTH = 4096
        private const val OPAQUE = 0xFF000000.toInt()
        /** Declared after [DEFAULT_PALETTE]: companion values initialise in order. */
        val DEFAULT = RgbSettings()

        private fun lookFields(prefix: String, look: RgbLook) = listOf(
            "pattern" to look.pattern.name,
            "curve" to look.curve.encode(),
            "period" to look.periodMs.toString(),
            "reverse" to flag(look.reverse),
            "width" to ((look.waveWidth * 100).roundToInt() / 100f).toString(),
            "palette" to if (look.rainbow) "rainbow" else "own",
            "colors" to look.palette.joinToString(",") { hex(it) },
            "sat" to look.saturation.toString(),
            "bright" to look.brightness.toString(),
            "mix" to look.mix.toString(),
        ).map { (k, v) -> prefix + k to v }

        /**
         * Reads [encoded]; anything missing, unknown or out of range falls back to the default of
         * that one field (numbers are clamped). Null, empty or oversized text gives [DEFAULT].
         * Stage 26 text has no `curve`: its `wave` (and `steps`) becomes the matching preset curve,
         * and its one look becomes the shared look of every element.
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
            val shared = readLook(map, "", RgbLook.of(d))
            val looks = RgbTarget.entries.mapNotNull { target ->
                val prefix = target.code + "."
                if (map.keys.none { it.startsWith(prefix) }) null else target to readLook(map, prefix, shared)
            }.toMap()
            return shared.applyTo(RgbSettings(
                enabled = bool("on", d.enabled),
                layers = map["layers"]?.let { enumSet<RgbLayer>(it) } ?: d.layers,
                areas = map["areas"]?.let { enumSet<RgbArea>(it) } ?: d.areas,
                tapReaction = enumOr(map["tap"], d.tapReaction),
                reactiveOnly = bool("reactive", d.reactiveOnly),
                glowMs = int("glow", d.glowMs),
                fps = int("fps", d.fps),
                pauseInPowerSave = bool("saver", d.pauseInPowerSave),
                solidKeys = when (map["keys"]) { "solid" -> true; "smooth" -> false; else -> d.solidKeys },
                separate = bool("sep", d.separate),
                looks = looks,
            )).normalized()
        }

        /** One look from the fields with [prefix]; missing or broken fields come from [base]. */
        private fun readLook(map: Map<String, String>, prefix: String, base: RgbLook): RgbLook {
            fun get(key: String) = map[prefix + key]
            fun int(key: String, default: Int) = get(key)?.toIntOrNull() ?: default
            val curve = RgbCurve.parse(get("curve"))
                ?: get("wave")?.let { name -> RgbWaveform.entries.firstOrNull { it.name == name } }
                    ?.let { RgbCurve.preset(it, int("steps", 4).coerceIn(STEPS_RANGE)) }
                ?: base.curve
            return RgbLook(
                pattern = enumOr(get("pattern"), base.pattern),
                curve = curve,
                periodMs = int("period", base.periodMs),
                reverse = when (get("reverse")) { "1" -> true; "0" -> false; else -> base.reverse },
                waveWidth = get("width")?.toFloatOrNull()?.takeIf { it.isFinite() } ?: base.waveWidth,
                rainbow = when (get("palette")) { "own" -> false; "rainbow" -> true; else -> base.rainbow },
                palette = get("colors")?.split(',')?.mapNotNull { parseHex(it) } ?: base.palette,
                saturation = int("sat", base.saturation),
                brightness = int("bright", base.brightness),
                mix = int("mix", base.mix),
            )
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

    // --- OKLab and OKLCH (stage 29b): perceptual blending without grey or dark middles ---

    private val toLinear = FloatArray(256) { i ->
        val v = i / 255f
        if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun fromLinear(v: Float): Int {
        val c = v.coerceIn(0f, 1f)
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
        return (s * 255f).roundToInt().coerceIn(0, 255)
    }

    /** L, a, b of the opaque colour [c] written to [out] (index [at]..[at]+2). */
    fun toOklab(c: Int, out: FloatArray, at: Int = 0) {
        val r = toLinear[red(c)]
        val g = toLinear[green(c)]
        val b = toLinear[blue(c)]
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        out[at] = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        out[at + 1] = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        out[at + 2] = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    }

    /** Opaque colour from OKLab; out-of-gamut values are clipped per channel. */
    fun fromOklab(lightness: Float, a: Float, b: Float): Int {
        val l = lightness + 0.3963377774f * a + 0.2158037573f * b
        val m = lightness - 0.1055613458f * a - 0.0638541728f * b
        val s = lightness - 0.0894841775f * a - 1.2914855480f * b
        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s
        val r = 4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
        val g = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
        val bl = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
        return argb(255, fromLinear(r), fromLinear(g), fromLinear(bl))
    }

    /**
     * Blend of the opaque colours [a] and [b] in OKLCH (lightness, chroma and hue of OKLab, hue the
     * short way round): the middle keeps its colour and brightness, unlike sRGB (dark) or plain
     * OKLab (greyish between opposite colours). [t] 0 gives exactly [a], 1 exactly [b].
     */
    fun mixOklch(a: Int, b: Int, t: Float): Int {
        val f = if (t.isNaN()) 0f else t.coerceIn(0f, 1f)
        if (f <= 0f) return a or BLACK
        if (f >= 1f) return b or BLACK
        val x = FloatArray(6)
        toOklab(a, x, 0)
        toOklab(b, x, 3)
        val ca = sqrt(x[1] * x[1] + x[2] * x[2])
        val cb = sqrt(x[4] * x[4] + x[5] * x[5])
        val lightness = x[0] + (x[3] - x[0]) * f
        // a grey end has no hue of its own: it takes the hue of the other end
        if (ca < GREY_CHROMA || cb < GREY_CHROMA)
            return fromOklab(lightness, x[1] + (x[4] - x[1]) * f, x[2] + (x[5] - x[2]) * f)
        val ha = kotlin.math.atan2(x[2], x[1])
        var dh = kotlin.math.atan2(x[5], x[4]) - ha
        if (dh > Math.PI) dh -= (2 * Math.PI).toFloat()
        if (dh < -Math.PI) dh += (2 * Math.PI).toFloat()
        val h = ha + dh * f
        val c = ca + (cb - ca) * f
        return fromOklab(lightness, c * kotlin.math.cos(h), c * kotlin.math.sin(h))
    }

    private const val GREY_CHROMA = 0.02f

    /**
     * [colors] (a colour table) smoothed with two box passes of [radius] entries per sRGB channel:
     * the corners of the HSV rainbow at yellow, cyan and magenta become round (no bright creases),
     * and every value stays a real colour. [cyclic] tables wrap (their last entry repeats the first).
     */
    fun smoothTable(colors: IntArray, radius: Int, cyclic: Boolean): IntArray {
        val n = colors.size
        if (n < 3 || radius < 1) return colors.copyOf()
        val count = if (cyclic) n - 1 else n
        var ch = FloatArray(count * 3)
        for (i in 0 until count) { ch[i * 3] = red(colors[i]).toFloat(); ch[i * 3 + 1] = green(colors[i]).toFloat(); ch[i * 3 + 2] = blue(colors[i]).toFloat() }
        repeat(2) {
            val next = FloatArray(count * 3)
            for (i in 0 until count) for (c in 0..2) {
                var sum = 0f
                for (k in -radius..radius) {
                    val j = if (cyclic) Math.floorMod(i + k, count) else (i + k).coerceIn(0, count - 1)
                    sum += ch[j * 3 + c]
                }
                next[i * 3 + c] = sum / (2 * radius + 1)
            }
            ch = next
        }
        return IntArray(n) { i ->
            val j = if (cyclic && i == n - 1) 0 else i
            argb(255, ch[j * 3].roundToInt(), ch[j * 3 + 1].roundToInt(), ch[j * 3 + 2].roundToInt())
        }
    }
}

/**
 * Colour of the light for every point and time. Coordinates are 0..1 across the lit area
 * ([aspect] = height / width keeps circles round). Single-threaded: call from the UI thread.
 * Allocation-free per call, so it can run for every key and every cell of the background grid in
 * every frame.
 *
 * Smoothness (stage 29b): the cycle position goes through the own curve ([RgbCurve], monotone, no
 * jumps unless drawn), colours come from a palette table made once per engine: own colours are
 * blended in OKLCH (no grey or dark middle between two colours), the rainbow is HSV with rounded
 * corners (no bright creases at yellow, cyan and magenta). Time only enters through [phase], which
 * grows with the real time between frames, so the speed does not depend on the frame rate.
 */
class RgbEngine(settings: RgbSettings) {
    val settings: RgbSettings = settings.normalized()
    /** Height / width of the lit area; NaN or infinite values (a view without size) are ignored, others clamped. */
    var aspect = 0.45f
        set(value) { if (value.isFinite()) field = value.coerceIn(ASPECT_MIN, ASPECT_MAX) }

    /** Position in cycles; grows with time (and faster after a tap with [RgbTapReaction.SPEED_UP]). */
    var phase = 0.0
        private set
    /**
     * Time of the flow of texture patterns in cycles: grows like [phase], but wraps at the
     * pattern's [RgbPatterns.flowPeriod], where the flow repeats exactly (no jump at the wrap).
     */
    var flow = 0.0
        private set
    private val flowPeriod = RgbPatterns.flowPeriod(this.settings.pattern)
    private var lastAdvanceMs = Long.MIN_VALUE

    private val tapX = FloatArray(MAX_TAPS)
    private val tapY = FloatArray(MAX_TAPS)
    private val tapKey = IntArray(MAX_TAPS)
    private val tapTime = LongArray(MAX_TAPS) { Long.MIN_VALUE }
    private var nextTap = 0
    private val sample = RgbPatterns.Sample()

    private val palette: IntArray = this.settings.let { s ->
        s.palette.map { c ->
            val hsv = RgbColors.toHsv(c)
            RgbColors.hsv(hsv[0], hsv[1] * s.saturation / 100f, hsv[2] * s.brightness / 100f)
        }.toIntArray()
    }
    // shared between engines with the same colours (a curve point being dragged makes a new engine per frame)
    private val tableCyclic: IntArray by lazy { RgbPaletteTables.get(tableKey(true)) { table(true) } }
    private val tableOpen: IntArray by lazy { RgbPaletteTables.get(tableKey(false)) { table(false) } }

    private fun tableKey(cyclic: Boolean): List<Any> = settings.let { s ->
        listOf(cyclic, s.rainbow, s.saturation, s.brightness) + (if (s.rainbow) emptyList() else s.palette)
    }

    /**
     * Takes over the moving state of [other] (same settings): phase, flow, aspect and taps. The
     * light of this engine is then the light of [other] at that moment; used to compute the light
     * grid on a background thread from a copy while the UI thread keeps advancing [other].
     */
    fun copyStateFrom(other: RgbEngine) {
        phase = other.phase
        flow = other.flow
        aspect = other.aspect
        System.arraycopy(other.tapX, 0, tapX, 0, MAX_TAPS)
        System.arraycopy(other.tapY, 0, tapY, 0, MAX_TAPS)
        System.arraycopy(other.tapKey, 0, tapKey, 0, MAX_TAPS)
        System.arraycopy(other.tapTime, 0, tapTime, 0, MAX_TAPS)
        nextTap = other.nextTap
    }

    /** True if the light (without tap effects) changes over time: phase or flow move. */
    val moves: Boolean get() = settings.enabled && settings.pattern != RgbPattern.STATIC_RAINBOW

    /** Moves the animation to [nowMs]. Long gaps (paused keyboard) do not jump. */
    fun advance(nowMs: Long) {
        val last = lastAdvanceMs
        lastAdvanceMs = nowMs
        if (last == Long.MIN_VALUE || settings.pattern == RgbPattern.STATIC_RAINBOW) return
        val dt = (nowMs - last).coerceIn(0L, MAX_STEP_MS)
        val boost = if (settings.tapReaction == RgbTapReaction.SPEED_UP) 1f + SPEED_BOOST * globalFade(nowMs) else 1f
        val step = dt.toDouble() / settings.periodMs * boost
        phase += step
        // whole drift periods only: colours (whole cycles) and tile drift go on without a jump
        if (phase > PHASE_WRAP_AT) phase -= floor(phase / RgbOverlay.DRIFT_PERIOD) * RgbOverlay.DRIFT_PERIOD
        flow += step
        if (flow >= flowPeriod) flow -= flowPeriod * floor(flow / flowPeriod)
    }

    /** Restarts timing without changing the phase (after a pause). */
    fun resetClock() { lastAdvanceMs = Long.MIN_VALUE }

    /** Takes over the phase of [other] (a changed setting goes on where the light was, no restart). */
    fun continueFrom(other: RgbEngine) {
        if (settings.pattern == RgbPattern.STATIC_RAINBOW) return
        phase = other.phase
        // the same pattern goes on exactly; another pattern has another period (it changes anyway)
        flow = other.flow % flowPeriod
    }

    /** Puts the clock at [cycles] of phase and flow (tests: the wraps after hours of running). */
    internal fun jumpTo(cycles: Double) {
        phase = cycles
        flow = cycles % flowPeriod
    }

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
     * The light at ([x0], [y0]) for element [keyId] (a key's id, -1 for the keyboard background
     * and the strip, [FIELD_KEYS] for the smooth light under all keys). The colour is opaque RGB in
     * the low bits; alpha is the strength 0..255 including the mix setting. Alpha 0 means "theme
     * colour unchanged".
     */
    fun light(x0: Float, y0: Float, keyId: Int, nowMs: Long): Int {
        if (!settings.enabled) return 0
        // never throws: a position from a view without size (NaN, infinite) counts as 0
        val x = if (x0.isFinite()) x0 else 0f
        val y = if (y0.isFinite()) y0 else 0f
        return effects(base(x, y), x, y, keyId, nowMs)
    }

    /** Pattern colour and strength (alpha, before the mix) at ([x], [y]), without tap effects; [effects] finishes it. */
    fun base(x: Float, y: Float): Int {
        val s = settings
        if (!s.enabled) return 0
        val color: Int
        var strength = 1f
        if (s.pattern.textured) {
            val fp = (phase - floor(phase)).toFloat()
            val time = flow.toFloat() * (if (s.reverse) -1f else 1f)
            RgbPatterns.field(s.pattern, x, y, aspect, time, if (s.reverse) fp else -fp, s.waveWidth, sample)
            val u = sample.u
            var c = paletteAt(s.curve.value(u - floor(u)), s.curve.cyclic)
            if (sample.grey > 0f) c = RgbColors.lerp(c, GREY, sample.grey)
            if (sample.whiten > 0f) c = RgbColors.lerp(c, RgbColors.WHITE, sample.whiten)
            if (sample.brightness < 1f) c = RgbColors.lerp(RgbColors.BLACK, c, sample.brightness)
            color = c
        } else {
            val spatial = when (s.pattern) {
                RgbPattern.WAVE_VERTICAL -> y
                RgbPattern.WAVE_DIAGONAL -> (x + y) / 2f
                RgbPattern.CIRCLE -> {
                    val dx = x - 0.5f
                    val dy = (y - 0.5f) * aspect
                    sqrt(dx * dx + dy * dy) / sqrt(0.25f + 0.25f * aspect * aspect)
                }
                RgbPattern.BREATHE -> 0f
                else -> x
            } * s.waveWidth
            // forward: the colours travel towards +x (+y, outwards); reverse: the other way
            val raw = if (s.reverse) phase + spatial else phase - spatial
            val v = s.curve.value((raw - floor(raw)).toFloat())
            color = paletteAt(v, s.curve.cyclic)
            if (s.pattern == RgbPattern.BREATHE) strength = 0.1f + 0.9f * v
        }
        return ((strength * 255f).roundToInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
    }

    /** [base] with the tap effects for element [keyId] at ([x], [y]) (see [light]). */
    fun effects(base: Int, x: Float, y: Float, keyId: Int, nowMs: Long): Int {
        val s = settings
        var color = base and 0xFFFFFF
        var strength = RgbColors.alpha(base) / 255f
        when (s.tapReaction) {
            RgbTapReaction.FLASH -> if (keyId != FIELD_KEYS) {
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

    /** For the smooth light under the keys ([FIELD_KEYS]): strength factor of key [keyId] (reactive flash). */
    fun keyFactor(keyId: Int, nowMs: Long): Float =
        if (settings.reactiveOnly && settings.tapReaction == RgbTapReaction.FLASH) keyFade(keyId, nowMs) else 1f

    /** For the smooth light under the keys: share of white over key [keyId] (flash without reactive-only). */
    fun keyHighlight(keyId: Int, nowMs: Long): Float =
        if (!settings.reactiveOnly && settings.tapReaction == RgbTapReaction.FLASH) keyFade(keyId, nowMs) * HIGHLIGHT else 0f

    /** Strength 0..1 of the detail tile over the keyboard background (mix, reactive-only fade). */
    fun overlayStrength(nowMs: Long): Float {
        if (!settings.enabled) return 0f
        val mix = settings.mix / 100f
        if (!settings.reactiveOnly) return mix
        return when (settings.tapReaction) {
            RgbTapReaction.NONE, RgbTapReaction.SPEED_UP -> mix * globalFade(nowMs)
            else -> 0f
        }
    }

    /** Palette colour at [pos] 0..1: cyclic wraps last to first, otherwise first to last. */
    fun paletteAt(pos: Float, cyclic: Boolean): Int {
        val p = if (pos.isNaN()) 0f else pos.coerceIn(0f, 1f)
        val t = if (cyclic) tableCyclic else tableOpen
        val scaled = p * (t.size - 1)
        val i = min(floor(scaled).toInt(), t.size - 2)
        val f = scaled - i
        return if (f <= 0f) t[i] else RgbColors.lerp(t[i], t[i + 1], f)
    }

    /**
     * The palette as a table with [SEGMENT] entries per segment, so every own colour sits exactly
     * on an entry. Own colours: OKLCH blend. Rainbow: HSV (cyclic 360°, otherwise 300° as in stage
     * 26) with its corners rounded over about ±4 % of the cycle.
     */
    private fun table(cyclic: Boolean): IntArray {
        val s = settings
        if (s.rainbow) {
            val range = if (cyclic) 360f else 300f
            val n = (if (cyclic) 6 else 5) * SEGMENT + 1
            val raw = IntArray(n) { RgbColors.hsv(it * range / (n - 1), s.saturation / 100f, s.brightness / 100f) }
            return RgbColors.smoothTable(raw, SMOOTH_RADIUS, cyclic)
        }
        val k = palette.size
        val segments = if (cyclic) k else k - 1
        return IntArray(segments * SEGMENT + 1) { i ->
            val seg = min(i / SEGMENT, segments - 1)
            val f = (i - seg * SEGMENT) / SEGMENT.toFloat()
            RgbColors.mixOklch(palette[seg % k], palette[(seg + 1) % k], f)
        }
    }

    private fun fade(age: Long): Float {
        if (age < 0 || age >= settings.glowMs) return 0f
        val t = (1f - age.toFloat() / settings.glowMs).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
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
        /** Element id of the smooth light under all keys: per-key flashes are drawn per key ([keyFactor], [keyHighlight]). */
        const val FIELD_KEYS = -2
        /** Palette table entries per palette segment (per 60° of the rainbow). */
        const val SEGMENT = 256
        /** Rainbow smoothing radius in table entries (about ±2 % of the cycle in two passes). */
        private const val SMOOTH_RADIUS = 28
        private const val GREY = 0xFF8A8A8A.toInt()
        /** Above this many cycles whole [RgbOverlay.DRIFT_PERIOD]s are taken off the phase (double precision). */
        private const val PHASE_WRAP_AT = 1e6
    }
}

/**
 * Palette tables shared by engines with the same colours (build-14 lag: a new engine per frame
 * while a curve point is dragged rebuilt the table each time). Thread-safe; tables are never
 * changed after they are made.
 */
object RgbPaletteTables {
    private const val KEEP = 16
    private val tables = object : LinkedHashMap<List<Any>, IntArray>(KEEP, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Any>, IntArray>?) = size > KEEP
    }
    /** Tables made so far (tests). */
    @Volatile var made = 0
        private set

    fun get(key: List<Any>, make: () -> IntArray): IntArray {
        synchronized(tables) { tables[key]?.let { return it } }
        val table = make()
        synchronized(tables) {
            tables[key]?.let { return it }
            tables[key] = table
            made++
        }
        return table
    }
}

/**
 * How much lighting work the keyboard does per frame (build-14 lag): the UI thread's share and the
 * background grid, averaged and at most over the last [windowMs]. Thread-safe; [summary] is the
 * small API the Diagnose page can show. Times are given by the caller, so it is testable on the JVM.
 */
class RgbFrameStats(private val windowMs: Long = 5000, private val capacity: Int = 512) {
    /**
     * [uiAvgMs]/[uiMaxMs]: lighting work on the UI thread per frame; [drawAvgMs]/[drawMaxMs]: the
     * whole keyboard draw of those frames (keys, labels and light); [jobs] grids made in the
     * background, [backgroundAvgMs]/[backgroundMaxMs] their time on the lighting thread.
     */
    data class Summary(val frames: Int, val uiAvgMs: Double, val uiMaxMs: Double, val drawAvgMs: Double, val drawMaxMs: Double,
                       val jobs: Int, val backgroundAvgMs: Double, val backgroundMaxMs: Double)

    private val uiAt = LongArray(capacity)
    private val uiNanos = LongArray(capacity)
    private val drawNanos = LongArray(capacity)
    private var uiCount = 0
    private var uiNext = 0
    private val bgAt = LongArray(capacity)
    private val bgNanos = LongArray(capacity)
    private var bgCount = 0
    private var bgNext = 0

    /** UI-thread lighting work of one frame, [nanos] long, in a keyboard draw of [drawNanos], finished at [atMs]. */
    @Synchronized fun recordUi(atMs: Long, nanos: Long, drawNanos: Long = nanos) {
        uiAt[uiNext] = atMs; uiNanos[uiNext] = nanos; this.drawNanos[uiNext] = drawNanos
        uiNext = (uiNext + 1) % capacity
        if (uiCount < capacity) uiCount++
    }

    /** One light grid made in the background, [nanos] long, finished at [atMs]. */
    @Synchronized fun recordBackground(atMs: Long, nanos: Long) {
        bgAt[bgNext] = atMs; bgNanos[bgNext] = nanos
        bgNext = (bgNext + 1) % capacity
        if (bgCount < capacity) bgCount++
    }

    @Synchronized fun clear() { uiCount = 0; uiNext = 0; bgCount = 0; bgNext = 0 }

    /**
     * Like [summary], but for the [windowMs] before the newest entry: the last seconds the keyboard
     * was drawn with light, also when it is hidden now (the Diagnose page is opened afterwards).
     */
    @Synchronized fun summaryOfLastActivity(): Summary {
        var newest = Long.MIN_VALUE
        for (i in 0 until uiCount) if (uiAt[i] > newest) newest = uiAt[i]
        for (i in 0 until bgCount) if (bgAt[i] > newest) newest = bgAt[i]
        return summary(if (newest == Long.MIN_VALUE) 0L else newest)
    }

    /** Average and maximum in ms over the entries of the last [windowMs] before [nowMs]. */
    @Synchronized fun summary(nowMs: Long): Summary {
        fun stats(at: LongArray, nanos: LongArray, count: Int): Triple<Int, Double, Double> {
            var n = 0; var sum = 0L; var max = 0L
            for (i in 0 until count) if (nowMs - at[i] in 0..windowMs) { n++; sum += nanos[i]; if (nanos[i] > max) max = nanos[i] }
            return Triple(n, if (n == 0) 0.0 else sum / 1e6 / n, max / 1e6)
        }
        val ui = stats(uiAt, uiNanos, uiCount)
        val draw = stats(uiAt, drawNanos, uiCount)
        val bg = stats(bgAt, bgNanos, bgCount)
        return Summary(ui.first, ui.second, ui.third, draw.second, draw.third, bg.first, bg.second, bg.third)
    }
}

/**
 * One engine per distinct look (stage 29): the elements that share a look share an engine, so a
 * pattern is computed once per frame however many elements use it. All engines run on one clock
 * and get every tap.
 */
class RgbEngines(settings: RgbSettings) {
    val settings: RgbSettings = settings.normalized()
    private val byTarget: Map<RgbTarget, RgbEngine>
    /** Every distinct engine once. */
    val all: List<RgbEngine>

    init {
        val cache = HashMap<RgbSettings, RgbEngine>()
        byTarget = RgbTarget.entries.associateWith { t -> this.settings.forTarget(t).let { s -> cache.getOrPut(s) { RgbEngine(s) } } }
        all = cache.values.toList()
    }

    fun forTarget(target: RgbTarget): RgbEngine = byTarget.getValue(target)

    var aspect: Float
        get() = all.first().aspect
        set(value) { for (e in all) e.aspect = value }

    fun advance(nowMs: Long) { for (e in all) e.advance(nowMs) }
    fun resetClock() { for (e in all) e.resetClock() }
    fun tap(x: Float, y: Float, keyId: Int, nowMs: Long) { for (e in all) e.tap(x, y, keyId, nowMs) }
    fun clearTaps() { for (e in all) e.clearTaps() }
    fun hasActiveEffect(nowMs: Long) = all.any { it.hasActiveEffect(nowMs) }

    /** Every element goes on with the phase it had in [old] (the settings page while options change). */
    fun continueFrom(old: RgbEngines) { for (t in RgbTarget.entries) forTarget(t).continueFrom(old.forTarget(t)) }
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
        return if (settings.moves() || effectActive) Mode.RUNNING else Mode.PAUSED
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
