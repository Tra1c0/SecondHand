// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shape of one layer of an own light pattern (stage 34). Every shape is a pure function of the
 * place, the layer time (whole cycles repeat exactly) and the taps; see [RgbOwnRenderer].
 */
enum class RgbGenerator(
    /** True when the colour position runs round (only its fraction counts); false: 0..1, clamped. */
    val cyclic: Boolean = true,
    /** True when the shape needs taps (it shows nothing before the first tap). */
    val usesTaps: Boolean = false,
) {
    /** One colour over everything; time runs through the colour stops. */
    FILL,
    LINEAR,
    RADIAL,
    CONIC,
    WAVES(cyclic = false),
    PLASMA(cyclic = false),
    NOISE(cyclic = false),
    STRIPES,
    /** Rings from every tap. */
    RIPPLE(usesTaps = true),
    /** A soft spot that glides to the last tapped key. */
    SPOTLIGHT(usesTaps = true),
    /** Contour lines ("Höhenlinien") over moving hills. */
    CONTOUR(cyclic = false),
    SPARKLE,
}

/** How a layer is combined with the layers below it (W3C compositing, separable blend modes). */
enum class RgbBlend { NORMAL, ADD, MULTIPLY, SCREEN }

/** What typing does to a layer. */
enum class RgbLayerReaction {
    NONE,
    /** The layer shows only while a tap fades (a pulse on every key). */
    PULSE,
    /** The layer shows only in a ring that runs out from the tapped key. */
    RIPPLE,
    /** The layer shows only around the tapped key and fades. */
    KEY_GLOW;

    val usesTaps get() = this != NONE
}

/** Colours of a layer: as chosen, or with the hue turning once per cycle. */
enum class RgbPaletteMode { FIXED, HUE_CYCLE }

/** One colour stop: [color] (opaque RGB) at [position] 0..1. */
data class RgbStop(val position: Float, val color: Int)

/**
 * One layer of an own pattern. Every number has a range ([normalized] clamps and rounds): speed in
 * cycles of the look's "Dauer" (negative runs the other way, steps of [SPEED_STEP]: whole cycles stay
 * whole across the phase wrap, P107), angle in degrees, scale (repeats across the keyboard), offset
 * of the centre in keyboard widths, opacity in percent, the motion [curve] of stage 29 from the
 * colour position to the place in the colour stops.
 */
data class RgbPatternLayer(
    val generator: RgbGenerator = RgbGenerator.LINEAR,
    val stops: List<RgbStop> = DEFAULT_STOPS,
    val blend: RgbBlend = RgbBlend.NORMAL,
    val speed: Float = 1f,
    val angle: Int = 0,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val opacity: Int = 100,
    val curve: RgbCurve = RgbCurve.DEFAULT,
    val reaction: RgbLayerReaction = RgbLayerReaction.NONE,
    val palette: RgbPaletteMode = RgbPaletteMode.FIXED,
) {
    fun normalized(): RgbPatternLayer = copy(
        stops = normalStops(stops),
        speed = step(finite(speed, 1f).coerceIn(-MAX_SPEED, MAX_SPEED), SPEED_STEP),
        angle = Math.floorMod(angle, 360),
        scale = step(finite(scale, 1f).coerceIn(MIN_SCALE, MAX_SCALE), SCALE_STEP),
        offsetX = step(finite(offsetX, 0f).coerceIn(-1f, 1f), OFFSET_STEP),
        offsetY = step(finite(offsetY, 0f).coerceIn(-1f, 1f), OFFSET_STEP),
        opacity = opacity.coerceIn(0, 100),
    )

    /** True when the layer changes by itself (not only on taps). */
    val moves: Boolean get() = opacity > 0 && (speed != 0f || palette == RgbPaletteMode.HUE_CYCLE)
    val usesTaps: Boolean get() = opacity > 0 && (generator.usesTaps || reaction.usesTaps)

    companion object {
        const val MIN_STOPS = 2
        const val MAX_STOPS = 8
        const val MAX_SPEED = 4f
        const val SPEED_STEP = 0.05f
        const val MIN_SCALE = 0.25f
        const val MAX_SCALE = 8f
        const val SCALE_STEP = 0.05f
        const val OFFSET_STEP = 0.01f
        /** The default palette of the settings, evenly round (the default curve runs round). */
        val DEFAULT_STOPS = listOf(RgbStop(0f, 0xFFFF0050.toInt()), RgbStop(0.333f, 0xFF00C8FF.toInt()), RgbStop(0.667f, 0xFF9B4DFF.toInt()))

        private fun finite(v: Float, default: Float) = if (v.isFinite()) v else default
        /** [v] rounded to a multiple of [step] (1/[step] a whole number). */
        internal fun step(v: Float, step: Float): Float {
            val inv = (1f / step).roundToInt()
            return (v * inv).roundToInt().toFloat() / inv
        }

        /** Opaque colours, positions 0..1 on 1/1000, sorted (stable), 2..[MAX_STOPS]; fewer than two give [DEFAULT_STOPS]. */
        fun normalStops(stops: List<RgbStop>): List<RgbStop> {
            val list = stops.take(MAX_STOPS).map { s ->
                val p = if (s.position.isFinite()) s.position.coerceIn(0f, 1f) else 0f
                RgbStop((p * 1000f).roundToInt() / 1000f, s.color or OPAQUE)
            }.sortedBy { it.position }
            return if (list.size < MIN_STOPS) DEFAULT_STOPS else list
        }

        private const val OPAQUE = 0xFF000000.toInt()
    }
}

/**
 * An own light pattern (stage 34): a name and 1..[MAX_LAYERS] layers, drawn bottom first. [id] is
 * the key in the library and in the settings text (`own=`); a pattern read from the settings alone
 * is a [reference] without layers until the library resolves it ([RgbSettings.resolve]).
 */
data class RgbOwnPattern(val id: String, val name: String, val layers: List<RgbPatternLayer>) {
    /** True when there is something to draw (a bare [reference] has no layers). */
    val usable: Boolean get() = layers.isNotEmpty()
    val moves: Boolean get() = layers.any { it.moves }
    val usesTaps: Boolean get() = layers.any { it.usesTaps }

    fun normalized(): RgbOwnPattern = copy(
        name = cleanName(name) ?: DEFAULT_NAME,
        layers = layers.take(MAX_LAYERS).map { it.normalized() },
    )

    companion object {
        const val MAX_LAYERS = 4
        const val MAX_NAME = 32
        const val DEFAULT_NAME = "Muster"
        private val ID = Regex("[a-z0-9]{1,12}")

        fun validId(id: String?): Boolean = id != null && ID.matches(id)

        /** The pattern [id] as named in the settings text, before the library is read. */
        fun reference(id: String) = RgbOwnPattern(id, "", emptyList())

        /**
         * [name] trimmed, or null when it is empty, too long, has control or markup characters or
         * looks like an address (`://`, `www.`): a name is only a label, never a link.
         */
        fun cleanName(name: String?): String? {
            val n = name?.trim() ?: return null
            if (n.isEmpty() || n.codePointCount(0, n.length) > MAX_NAME) return null
            if (n.any { it.isISOControl() || it == '<' || it == '>' || Character.getType(it) == Character.FORMAT.toInt() }) return null
            val lower = n.lowercase(java.util.Locale.ROOT)
            if ("://" in lower || "www." in lower) return null
            return n
        }

        /**
         * A starting point that looks like the built-in [look] (stage 34: "duplicate a built-in
         * pattern"): the waves and the circle exactly as gradients, the texture patterns of 29c as
         * layers that come close (they have no fine detail tile). Colours from the look's palette
         * or the rainbow, its curve and wave width.
         */
        fun from(look: RgbLook, id: String, name: String): RgbOwnPattern {
            val stops = evenStops(if (look.rainbow) RAINBOW else look.palette, look.curve.cyclic)
            // the look's "Rückwärts" still applies to the own pattern: the layers run forward
            val speed = 1f
            val scale = look.waveWidth
            val base = RgbPatternLayer(stops = stops, speed = speed, scale = scale, curve = look.curve)
            val layers = when (look.pattern) {
                RgbPattern.WAVE_HORIZONTAL -> listOf(base)
                RgbPattern.WAVE_VERTICAL -> listOf(base.copy(angle = 90))
                RgbPattern.WAVE_DIAGONAL -> listOf(base.copy(angle = 45))
                RgbPattern.CIRCLE -> listOf(base.copy(generator = RgbGenerator.RADIAL))
                RgbPattern.BREATHE -> listOf(base.copy(generator = RgbGenerator.FILL))
                RgbPattern.STATIC_RAINBOW -> listOf(base.copy(speed = 0f))
                RgbPattern.INK -> listOf(base.copy(generator = RgbGenerator.NOISE, scale = scale * 0.7f, curve = RgbCurve.DEFAULT),
                    // dark where there is no ink: noise from black to white, multiplied
                    base.copy(generator = RgbGenerator.NOISE, stops = listOf(RgbStop(0f, BLACK), RgbStop(1f, WHITE)), angle = 120,
                        scale = scale * 1.3f, opacity = 55, blend = RgbBlend.MULTIPLY, curve = RgbCurve.DEFAULT))
                RgbPattern.DIAMONDS -> listOf(base.copy(angle = 45),
                    base.copy(generator = RgbGenerator.STRIPES, stops = WHITES, angle = 135, scale = 1.5f, opacity = 35, blend = RgbBlend.SCREEN))
                RgbPattern.SLATS -> listOf(base.copy(angle = 30),
                    base.copy(generator = RgbGenerator.STRIPES, stops = listOf(RgbStop(0f, BLACK), RgbStop(1f, BLACK)), angle = 30,
                        scale = 2f, speed = 0f, opacity = 45, blend = RgbBlend.MULTIPLY))
                RgbPattern.HATCH -> listOf(base.copy(generator = RgbGenerator.CONIC),
                    base.copy(generator = RgbGenerator.RADIAL, stops = listOf(RgbStop(0f, WHITE), RgbStop(1f, BLACK)), speed = 0f,
                        opacity = 60, blend = RgbBlend.MULTIPLY, curve = RgbCurve.DEFAULT))
                RgbPattern.LED -> listOf(base.copy(angle = 20), base.copy(generator = RgbGenerator.SPARKLE, stops = WHITES, scale = 2f,
                    opacity = 70, blend = RgbBlend.SCREEN, curve = RgbCurve.DEFAULT))
                RgbPattern.TOPO -> listOf(base.copy(generator = RgbGenerator.PLASMA, scale = scale * 0.6f, speed = speed * 0.25f, curve = RgbCurve.DEFAULT),
                    base.copy(generator = RgbGenerator.CONTOUR, stops = WHITES, speed = speed * 0.25f, opacity = 80, blend = RgbBlend.SCREEN,
                        curve = RgbCurve.DEFAULT))
            }
            return RgbOwnPattern(id, name, layers).normalized()
        }

        /**
         * [colors] as evenly spaced stops: [cyclic] like the palettes of 29b that run round (the
         * last colour leads back to the first past 1), otherwise from the first at 0 to the last at 1.
         */
        fun evenStops(colors: List<Int>, cyclic: Boolean): List<RgbStop> =
            colors.mapIndexed { i, c -> RgbStop(i / (if (cyclic) colors.size else colors.size - 1).toFloat(), c) }

        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private val WHITES = listOf(RgbStop(0f, WHITE), RgbStop(1f, WHITE))
        /** The six corners of the rainbow (red, yellow, green, cyan, blue, magenta). */
        val RAINBOW: List<Int> = (0 until 6).map { RgbColors.hsv(it * 60f, 1f, 1f) }
    }
}

/**
 * The library of own patterns ([MAX_PATTERNS] at most), in the order they were made. Ids are
 * `p1`, `p2`, …; a new one never reuses an id in the library.
 */
data class RgbPatternLibrary(val patterns: List<RgbOwnPattern> = emptyList()) {
    operator fun get(id: String): RgbOwnPattern? = patterns.firstOrNull { it.id == id }
    val full: Boolean get() = patterns.size >= MAX_PATTERNS

    fun nextId(): String {
        val used = patterns.mapNotNull { it.id.removePrefix("p").toIntOrNull() }.maxOrNull() ?: 0
        return "p${used + 1}"
    }

    /** [name] made unique in the library ("Name 2", "Name 3", …), cut to [RgbOwnPattern.MAX_NAME]. */
    fun uniqueName(name: String, except: String? = null): String {
        val base = RgbOwnPattern.cleanName(name) ?: RgbOwnPattern.DEFAULT_NAME
        val taken = patterns.filter { it.id != except }.map { it.name }.toSet()
        if (base !in taken) return base
        var n = 2
        while (true) {
            val suffix = " $n"
            val cut = base.take(RgbOwnPattern.MAX_NAME - suffix.length).trimEnd() + suffix
            if (cut !in taken) return cut
            n++
        }
    }

    /** Adds [pattern] with a new id and a unique name; null when the library is full. */
    fun add(pattern: RgbOwnPattern): Pair<RgbPatternLibrary, RgbOwnPattern>? {
        if (full) return null
        val made = pattern.normalized().let { it.copy(id = nextId(), name = uniqueName(it.name)) }
        return RgbPatternLibrary(patterns + made) to made
    }

    fun replace(pattern: RgbOwnPattern): RgbPatternLibrary =
        RgbPatternLibrary(patterns.map { if (it.id == pattern.id) pattern.normalized() else it })

    fun rename(id: String, name: String): RgbPatternLibrary? {
        val clean = RgbOwnPattern.cleanName(name) ?: return null
        return RgbPatternLibrary(patterns.map { if (it.id == id) it.copy(name = uniqueName(clean, id)) else it })
    }

    fun remove(id: String) = RgbPatternLibrary(patterns.filter { it.id != id })

    companion object {
        const val MAX_PATTERNS = 24
        val EMPTY = RgbPatternLibrary()
    }
}

/**
 * The compiled form of an own pattern for one engine: colour tables per layer (shared through
 * [RgbPaletteTables]), the direction of every layer as sine and cosine, and holders for the hue
 * turn of the frame. [sample] is allocation-free and only reads its inputs, so the light of a place
 * is a pure function of place, phase, clock and taps (deterministic, testable on the JVM).
 * Single-threaded like [RgbEngine].
 */
class RgbOwnRenderer(val pattern: RgbOwnPattern, saturation: Int = 100, brightness: Int = 100) {
    private val layers = pattern.layers.toTypedArray()
    private val n = layers.size
    private val cosA = FloatArray(n) { cos(layers[it].angle * PI / 180.0).toFloat() }
    private val sinA = FloatArray(n) { sin(layers[it].angle * PI / 180.0).toFloat() }
    /** Speed in whole [RgbPatternLayer.SPEED_STEP]s: the layer time is exact in double (0.35f is not 0.35, P214). */
    private val speedSteps = IntArray(n) { (layers[it].speed / RgbPatternLayer.SPEED_STEP).roundToInt() }
    private val tables: Array<IntArray> = Array(n) { i ->
        val layer = layers[i]
        val cyclic = layer.curve.cyclic
        val key = listOf<Any>("own", cyclic, saturation, brightness) + layer.stops.flatMap { listOf(it.position, it.color) }
        RgbPaletteTables.get(key) { stopTable(layer.stops, cyclic, saturation, brightness) }
    }
    // hue turn of the present phase, per frame (not per sample)
    private var huePhase = Double.NaN
    private val hue = FloatArray(9)

    val moves: Boolean = pattern.moves
    val usesTaps: Boolean = pattern.usesTaps

    /**
     * Light at ([x], [y]) (0..1 across the area, [aspect] = height / width) at [phase] (cycles of
     * the look) and clock [nowMs]: opaque RGB in the low bits, alpha = coverage of all layers. The
     * taps are the engine's ring buffers ([tapTime] [Long.MIN_VALUE] = empty); [glowMs] is the fade
     * of the tap reactions. [reverse] runs every layer the other way.
     */
    fun sample(x: Float, y: Float, aspect: Float, phase: Double, reverse: Boolean, nowMs: Long,
               tapX: FloatArray, tapY: FloatArray, tapTime: LongArray, glowMs: Int): Int {
        var cr = 0f
        var cg = 0f
        var cb = 0f
        var ca = 0f
        for (i in 0 until n) {
            val layer = layers[i]
            if (layer.opacity <= 0) continue
            var cov = layer.opacity / 100f
            when (layer.reaction) {
                RgbLayerReaction.NONE -> {}
                RgbLayerReaction.PULSE -> cov *= newestFade(tapTime, nowMs, glowMs)
                RgbLayerReaction.RIPPLE -> cov *= ring(x, y, aspect, nowMs, tapX, tapY, tapTime, glowMs, RING_REACH, RING_WIDTH)
                RgbLayerReaction.KEY_GLOW -> cov *= glow(x, y, aspect, nowMs, tapX, tapY, tapTime, glowMs)
            }
            if (cov <= 0f) continue
            val steps = if (reverse) -speedSteps[i] else speedSteps[i]
            val lt = phase * steps / STEPS_PER_CYCLE
            val t = (lt - floor(lt)).toFloat()
            // generator: colour position and coverage
            val px = x - 0.5f - layer.offsetX
            val py = (y - 0.5f - layer.offsetY) * aspect
            val a = px * cosA[i] + py * sinA[i]
            val b = -px * sinA[i] + py * cosA[i]
            val extA = abs(cosA[i]) + abs(sinA[i]) * aspect
            val extB = abs(sinA[i]) + abs(cosA[i]) * aspect
            val s = layer.scale
            var u: Float
            var g = 1f
            when (layer.generator) {
                // like the built-in waves: the colours run forward (along the angle, outwards) as time grows
                RgbGenerator.FILL -> u = t
                RgbGenerator.LINEAR -> u = t - (a / extA + 0.5f) * s
                RgbGenerator.RADIAL -> u = t - sqrt(px * px + py * py) / halfDiagonal(aspect) * s
                RgbGenerator.CONIC -> u = t - (atan2(b, a) / TWO_PI) * max(1, s.roundToInt())
                RgbGenerator.WAVES -> u = 0.5f + 0.5f * sin(TWO_PI * (a / extA * s * 2f - t) + 1.5f * sin(TWO_PI * (b / extB * s + t)))
                RgbGenerator.PLASMA -> {
                    val r = sqrt(px * px + py * py)
                    val v = sin(TWO_PI * (a * s * 1.6f + t)) + sin(TWO_PI * (b * s * 1.3f - t)) +
                        sin(TWO_PI * ((a + b) * s * 1.1f + t)) + sin(TWO_PI * (r * s * 2f - t))
                    u = 0.5f + v / 8f
                }
                RgbGenerator.NOISE -> {
                    val f = RgbNoise.fbm(a * s * 3f + NOISE_ORBIT * cos(TWO_PI * t) + 40f, b * s * 3f + NOISE_ORBIT * sin(TWO_PI * t) + 40f)
                    u = (f - 0.5f) * 2.2f + 0.5f
                }
                RgbGenerator.STRIPES -> {
                    val count = STRIPES * s
                    val w = (a / extA + 0.5f) * count - t
                    val f = w - floor(w)
                    g = RgbPatterns.smooth(0f, STRIPE_EDGE, f) * (1f - RgbPatterns.smooth(0.5f, 0.5f + STRIPE_EDGE, f))
                    u = floor(w) / max(1f, count)
                }
                RgbGenerator.RIPPLE -> {
                    g = ring(x, y, aspect, nowMs, tapX, tapY, tapTime, glowMs, RING_REACH, RING_WIDTH / s)
                    u = t - ringDistance
                }
                RgbGenerator.SPOTLIGHT -> {
                    spotCentre(nowMs, tapX, tapY, tapTime, 0.5f + layer.offsetX, 0.5f + layer.offsetY)
                    val dx = x - spotX
                    val dy = (y - spotY) * aspect
                    val d = sqrt(dx * dx + dy * dy) / (SPOT_RADIUS / s)
                    g = 1f - RgbPatterns.smooth(0f, 1f, d)
                    u = t - d * 0.5f
                }
                RgbGenerator.CONTOUR -> {
                    val h = RgbNoise.fbm(a * s * 2.2f + NOISE_ORBIT * cos(TWO_PI * t) + 20f, b * s * 2.2f + NOISE_ORBIT * sin(TWO_PI * t) + 20f)
                    val level = h * CONTOUR_LEVELS
                    val f = level - floor(level)
                    val dl = min(f, 1f - f)
                    g = 1f - RgbPatterns.smooth(0f, CONTOUR_WIDTH, dl)
                    u = (h - 0.5f) * 2.2f + 0.5f
                }
                RgbGenerator.SPARKLE -> {
                    val cells = SPARKLE_CELLS * s
                    val gx = a / extA * cells + 1000f
                    val gy = b / extA * cells + 1000f
                    val ix = floor(gx).toInt()
                    val iy = floor(gy).toInt()
                    val h1 = hash(ix, iy, 1)
                    val h2 = hash(ix, iy, 2)
                    val rate = 1 + (hash(ix, iy, 3) * 3f).toInt()
                    val dx = gx - ix - 0.5f
                    val dy = gy - iy - 0.5f
                    val fall = 1f - RgbPatterns.smooth(0f, 0.5f, sqrt(dx * dx + dy * dy))
                    val tw = max(0f, sin(TWO_PI * (t * rate + h1)))
                    val tw2 = tw * tw
                    g = fall * tw2 * tw2 * tw2
                    u = h2
                }
            }
            cov *= g
            if (cov <= 0f) continue
            val pos = if (layer.generator.cyclic) u - floor(u) else u.coerceIn(0f, MAX_BOUNDED)
            val v = layer.curve.value(if (pos.isNaN()) 0f else pos)
            var c = lookup(tables[i], v)
            if (layer.palette == RgbPaletteMode.HUE_CYCLE) c = turnHue(c, phase, reverse)
            val sr = RgbColors.red(c) / 255f
            val sg = RgbColors.green(c) / 255f
            val sb = RgbColors.blue(c) / 255f
            val sa = min(1f, cov)
            // W3C compositing: Cs' = (1 - ab) Cs + ab B(Cb, Cs); co = as Cs' + (1 - as) ab Cb; ao = as + ab (1 - as)
            val ao = sa + ca * (1f - sa)
            if (ao <= 0f) continue
            val mr = (1f - ca) * sr + ca * blend(layer.blend, cr, sr)
            val mg = (1f - ca) * sg + ca * blend(layer.blend, cg, sg)
            val mb = (1f - ca) * sb + ca * blend(layer.blend, cb, sb)
            cr = (sa * mr + (1f - sa) * ca * cr) / ao
            cg = (sa * mg + (1f - sa) * ca * cg) / ao
            cb = (sa * mb + (1f - sa) * ca * cb) / ao
            ca = ao
        }
        if (ca <= 0f) return 0
        return RgbColors.argb((ca * 255f).roundToInt(), (cr * 255f).roundToInt(), (cg * 255f).roundToInt(), (cb * 255f).roundToInt())
    }

    /** True while a tap still changes the picture: reactions fade over [glowMs], the spot glides for [SPOT_GLIDE_MS]. */
    fun tapActive(nowMs: Long, tapTime: LongArray, glowMs: Int): Boolean {
        if (!usesTaps) return false
        val life = max(glowMs.toLong(), SPOT_GLIDE_MS)
        for (time in tapTime) if (time != Long.MIN_VALUE && nowMs - time in 0 until life) return true
        return false
    }

    // results of the tap shapes beside their return value (this renderer's own, like the engine: one thread)
    private var ringDistance = 0f
    private var spotX = 0.5f
    private var spotY = 0.5f

    /** Strongest ring of the fading taps at ([x], [y]); [ringDistance] is its distance to the tap. */
    private fun ring(x: Float, y: Float, aspect: Float, nowMs: Long, tapX: FloatArray, tapY: FloatArray, tapTime: LongArray,
                     glowMs: Int, reach: Float, width: Float): Float {
        var best = 0f
        var dist = 0f
        for (i in tapTime.indices) {
            if (tapTime[i] == Long.MIN_VALUE) continue
            val age = nowMs - tapTime[i]
            if (age < 0 || age >= glowMs) continue
            val progress = age.toFloat() / glowMs
            val dx = x - tapX[i]
            val dy = (y - tapY[i]) * aspect
            val d = sqrt(dx * dx + dy * dy)
            val band = 1f - abs(d - progress * reach) / width
            if (band > 0f) {
                val v = band * (1f - progress)
                if (v > best) { best = v; dist = d }
            }
        }
        ringDistance = dist
        return best
    }

    /** A soft disk of [GLOW_RADIUS] keyboard widths around each fading tap. */
    private fun glow(x: Float, y: Float, aspect: Float, nowMs: Long, tapX: FloatArray, tapY: FloatArray, tapTime: LongArray, glowMs: Int): Float {
        var best = 0f
        for (i in tapTime.indices) {
            if (tapTime[i] == Long.MIN_VALUE) continue
            val f = fade(nowMs - tapTime[i], glowMs)
            if (f <= 0f) continue
            val dx = x - tapX[i]
            val dy = (y - tapY[i]) * aspect
            val d = sqrt(dx * dx + dy * dy) / GLOW_RADIUS
            best = max(best, f * (1f - RgbPatterns.smooth(0.4f, 1f, d)))
        }
        return best
    }

    /** Centre of the spotlight ([spotX], [spotY]): [restX]/[restY] before any tap, then gliding from the previous tap to the newest. */
    private fun spotCentre(nowMs: Long, tapX: FloatArray, tapY: FloatArray, tapTime: LongArray, restX: Float, restY: Float) {
        var newest = -1
        var previous = -1
        for (i in tapTime.indices) {
            val time = tapTime[i]
            if (time == Long.MIN_VALUE || time > nowMs) continue
            if (newest < 0 || time > tapTime[newest]) { previous = newest; newest = i }
            else if (previous < 0 || time > tapTime[previous]) previous = i
        }
        if (newest < 0) { spotX = restX; spotY = restY; return }
        val fromX = if (previous >= 0) tapX[previous] else restX
        val fromY = if (previous >= 0) tapY[previous] else restY
        val p = ((nowMs - tapTime[newest]).toFloat() / SPOT_GLIDE_MS).coerceIn(0f, 1f)
        val e = p * p * (3f - 2f * p)
        spotX = fromX + (tapX[newest] - fromX) * e
        spotY = fromY + (tapY[newest] - fromY) * e
    }

    private fun turnHue(c: Int, phase: Double, reverse: Boolean): Int {
        if (phase != huePhase) {
            huePhase = phase
            val turn = (phase - floor(phase)) * (if (reverse) -2 * PI else 2 * PI)
            val k = cos(turn).toFloat()
            val q = sin(turn).toFloat()
            // the hue rotation matrix of CSS filter hue-rotate (luminance kept)
            hue[0] = 0.213f + k * 0.787f - q * 0.213f; hue[1] = 0.715f - k * 0.715f - q * 0.715f; hue[2] = 0.072f - k * 0.072f + q * 0.928f
            hue[3] = 0.213f - k * 0.213f + q * 0.143f; hue[4] = 0.715f + k * 0.285f + q * 0.140f; hue[5] = 0.072f - k * 0.072f - q * 0.283f
            hue[6] = 0.213f - k * 0.213f - q * 0.787f; hue[7] = 0.715f - k * 0.715f + q * 0.715f; hue[8] = 0.072f + k * 0.928f + q * 0.072f
        }
        val r = RgbColors.red(c).toFloat()
        val g = RgbColors.green(c).toFloat()
        val b = RgbColors.blue(c).toFloat()
        return RgbColors.argb(255, (hue[0] * r + hue[1] * g + hue[2] * b).roundToInt(), (hue[3] * r + hue[4] * g + hue[5] * b).roundToInt(),
            (hue[6] * r + hue[7] * g + hue[8] * b).roundToInt())
    }

    companion object {
        private const val TWO_PI = (2 * PI).toFloat()
        private const val STEPS_PER_CYCLE = 20.0
        /** Highest colour position of a bounded shape: 1 itself would wrap to 0 in the curve. */
        private const val MAX_BOUNDED = 0.9999f
        const val RING_REACH = 1.2f
        const val RING_WIDTH = 0.12f
        const val STRIPES = 6f
        const val STRIPE_EDGE = 0.08f
        const val SPOT_RADIUS = 0.35f
        const val SPOT_GLIDE_MS = 180L
        const val SPARKLE_CELLS = 10f
        const val CONTOUR_LEVELS = 7f
        const val CONTOUR_WIDTH = 0.14f
        private const val NOISE_ORBIT = 0.6f
        const val TABLE = 512

        const val GLOW_RADIUS = 0.08f

        /** Smoothstep fade of a tap [age] ms old over [glowMs] (1 at the tap, 0 from [glowMs] on). */
        fun fade(age: Long, glowMs: Int): Float {
            if (age < 0 || age >= glowMs) return 0f
            val t = (1f - age.toFloat() / glowMs).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** The strongest [fade] of all taps. */
        fun newestFade(tapTime: LongArray, nowMs: Long, glowMs: Int): Float {
            var f = 0f
            for (time in tapTime) if (time != Long.MIN_VALUE) f = max(f, fade(nowMs - time, glowMs))
            return f
        }

        fun halfDiagonal(aspect: Float) = 0.5f * sqrt(1f + aspect * aspect)

        /** Separable blend of backdrop [b] and source [s] (0..1). */
        fun blend(mode: RgbBlend, b: Float, s: Float): Float = when (mode) {
            RgbBlend.NORMAL -> s
            RgbBlend.ADD -> min(1f, b + s)
            RgbBlend.MULTIPLY -> b * s
            RgbBlend.SCREEN -> b + s - b * s
        }

        /** Colour of [table] at [v] 0..1, linear between entries. */
        fun lookup(table: IntArray, v: Float): Int {
            val p = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)
            val scaled = p * (table.size - 1)
            val i = min(floor(scaled).toInt(), table.size - 2)
            val f = scaled - i
            return if (f <= 0f) table[i] else RgbColors.lerp(table[i], table[i + 1], f)
        }

        /**
         * The colour stops as a table of [TABLE] + 1 entries, blended in OKLCH like the palettes of
         * 29b, saturation and brightness of the look applied to every stop. [cyclic]: the last stop
         * runs on to the first one past 1 (the place before the first stop comes from the last);
         * otherwise the ends hold their colour.
         */
        fun stopTable(stops: List<RgbStop>, cyclic: Boolean, saturation: Int = 100, brightness: Int = 100): IntArray {
            val colors = stops.map { s ->
                val hsv = RgbColors.toHsv(s.color)
                RgbColors.hsv(hsv[0], hsv[1] * saturation / 100f, hsv[2] * brightness / 100f)
            }
            val pos = stops.map { it.position }
            val k = stops.size
            return IntArray(TABLE + 1) { i ->
                val p = i / TABLE.toFloat()
                val after = pos.indexOfFirst { it > p }
                when {
                    after == 0 -> if (!cyclic) colors[0] else {
                        val from = pos[k - 1] - 1f
                        mix(colors[k - 1], colors[0], (p - from) / (pos[0] - from))
                    }
                    after < 0 -> if (!cyclic) colors[k - 1] else {
                        val to = pos[0] + 1f
                        mix(colors[k - 1], colors[0], (p - pos[k - 1]) / (to - pos[k - 1]))
                    }
                    else -> mix(colors[after - 1], colors[after], (p - pos[after - 1]) / (pos[after] - pos[after - 1]))
                }
            }
        }

        private fun mix(a: Int, b: Int, t: Float) = RgbColors.mixOklch(a, b, if (t.isFinite()) t else 0f)

        /** Deterministic 0..1 per cell and [salt] (integer hash, no state). */
        fun hash(ix: Int, iy: Int, salt: Int): Float {
            var h = ix * 0x27d4eb2d xor (iy * 0x165667b1) xor (salt * 0x3c6ef372)
            h = (h xor (h ushr 15)) * 0x2c1b3c6d
            h = (h xor (h ushr 12)) * 0x297a2d39
            h = h xor (h ushr 15)
            return (h ushr 8) / 16777216f
        }
    }
}
