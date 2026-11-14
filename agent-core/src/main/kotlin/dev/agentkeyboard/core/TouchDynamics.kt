// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Stage 50: the short-term layer and the scene profiles of the personal hit zones. The base (the
 * calibrated offsets per key, kept by the app) never changes here; this class only says how far the
 * zones of a key region move on top of it right now, and when a deviation came back often enough to
 * move the base a little ([onPromote]).
 *
 * Inputs are residuals: where a confirmed touch landed relative to the centre of the meant key, in
 * key sizes, minus the base offset of that key. No letters, no text, no order of keys and no
 * location are kept; a scene is a handful of numbers (offsets per region, spread, speed,
 * correction rate) and a time-of-day bucket.
 *
 * All times are wall clock milliseconds passed in by the caller; [local] turns one into the local
 * day number and hour, so the tests can run without a time zone.
 */
class TouchDynamics(private val local: (Long) -> Stamp = { Stamp(Math.floorDiv(it, DAY_MILLIS), ((it / HOUR_MILLIS) % 24).toInt()) }) {
    data class Stamp(val day: Long, val hour: Int)

    enum class Bucket {
        MORNING, DAY, EVENING, NIGHT;

        companion object {
            /** 5–11 morning, 11–17 day, 17–22 evening, 22–5 night. */
            fun of(hour: Int): Bucket = when (hour) {
                in 5 until 11 -> MORNING
                in 11 until 17 -> DAY
                in 17 until 22 -> EVENING
                else -> NIGHT
            }
        }

        /** 0 for the same bucket, 1 for a neighbour (cyclic), 2 for the opposite one. */
        fun steps(other: Bucket): Int = min(abs(ordinal - other.ordinal), 4 - abs(ordinal - other.ordinal))
    }

    /** What a scene looks like, compared with the usual typing; for the settings page. */
    enum class Trait { LESS_PRECISE, MORE_PRECISE, LOWER, HIGHER, LEFT, RIGHT, FASTER, SLOWER, MORE_CORRECTIONS }

    class Scene(
        val id: Int, val bucket: Bucket, var sessions: Int, var firstDay: Long, var lastDay: Long,
        val regionDx: FloatArray, val regionDy: FloatArray, val regionN: IntArray,
        var dx: Float, var dy: Float, var spread: Float, var speed: Float, var corrections: Float,
    )

    /** One session that clearly moved the touches; evidence for a promotion. */
    class Observation(
        val day: Long, val bucket: Bucket, val sceneId: Int, val dx: Float, val dy: Float,
        val regionDx: FloatArray, val regionDy: FloatArray, val regionN: IntArray,
    )

    /** How far the base of each region moves, in key sizes (already damped and bounded). */
    class Promotion(val regionDx: FloatArray, val regionDy: FloatArray)

    private class Session(val start: Long, var last: Long) {
        var n = 0
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0
        val rCount = IntArray(REGIONS); val rx = DoubleArray(REGIONS); val ry = DoubleArray(REGIONS)
        var blockN = 0; var blockX = 0.0; var blockY = 0.0
        val blocks = ArrayList<FloatArray>()
        var letters = 0; var deletes = 0
        var lastLetter = -1L; var intervals = 0; var intervalSum = 0L
        var sceneId = -1
        var recognised = false
    }

    // persisted state, read and written by the app
    val scenes = ArrayList<Scene>()
    val observations = ArrayList<Observation>()
    var spreadRef = Float.NaN
    var speedRef = Float.NaN
    var correctionRef = Float.NaN
    var nextSceneId = 1
    var promotions = 0
    /** Set when the persisted state changed; the app saves and clears it. */
    var dirty = false

    var onPromote: ((Promotion) -> Unit)? = null

    // short-term layer, never persisted
    private var gx = 0f; private var gy = 0f
    private val rx = FloatArray(REGIONS); private val ry = FloatArray(REGIONS); private val rn = IntArray(REGIONS)
    private var session: Session? = null
    private var app: String? = null

    /** The scene the current session started from, or -1. */
    val currentScene: Int get() = session?.sceneId ?: -1
    val sessionTaps: Int get() = session?.n ?: 0

    /** Short-term offset of [region] in key widths (any other region: the global value). */
    fun shortX(region: Int): Float = short(region, gx, rx)
    fun shortY(region: Int): Float = short(region, gy, ry)

    private fun short(region: Int, global: Float, values: FloatArray): Float {
        if (region !in 0 until REGIONS) return global.coerceIn(-MAX_SHORT, MAX_SHORT)
        val w = rn[region].toFloat() / (rn[region] + REGION_PRIOR)
        return (global + w * values[region]).coerceIn(-MAX_SHORT, MAX_SHORT)
    }

    /**
     * A text field was opened. Another app or a pause longer than [PAUSE_MILLIS] ends the session (and the
     * short-term layer); another field of the same app halves the short-term layer.
     */
    fun startField(packageName: String?, sameField: Boolean, now: Long) {
        val current = session
        if (current != null && (packageName != app || now - current.last > PAUSE_MILLIS)) endSession()
        else if (!sameField) decay(FIELD_DECAY)
        app = packageName
    }

    /** A key was pressed: counts for typing speed and correction rate. */
    fun onKey(letter: Boolean, delete: Boolean, now: Long) {
        val s = ensureSession(now)
        if (letter) {
            if (s.lastLetter >= 0) {
                val gap = now - s.lastLetter
                if (gap in 1 until MAX_INTERVAL_MILLIS) { s.intervals++; s.intervalSum += gap }
            }
            s.lastLetter = now
            s.letters++
        } else if (delete) s.deletes++
        s.last = now
    }

    /**
     * One confirmed touch in [region], [offsetX]/[offsetY] key sizes from the centre of the meant key, whose
     * base offset is [baseX]/[baseY]. Against feedback loops only touches inside the normal key count
     * ([IN_KEY]): a touch that reached the key only through a moved zone, or a wrong letter accepted as
     * right, never pushes the zone further. Each touch moves the short-term layer by at most [MAX_STEP].
     * Returns whether the touch counted.
     */
    fun onTap(region: Int, offsetX: Float, offsetY: Float, baseX: Float, baseY: Float, now: Long): Boolean {
        if (abs(offsetX) > IN_KEY || abs(offsetY) > IN_KEY) return false
        val s = ensureSession(now)
        val x = (offsetX - baseX).coerceIn(-MAX_RESIDUAL, MAX_RESIDUAL)
        val y = (offsetY - baseY).coerceIn(-MAX_RESIDUAL, MAX_RESIDUAL)
        gx += (ALPHA * (x - gx)).coerceIn(-MAX_STEP, MAX_STEP)
        gy += (ALPHA * (y - gy)).coerceIn(-MAX_STEP, MAX_STEP)
        if (region in 0 until REGIONS) {
            rx[region] += (REGION_ALPHA * (x - gx - rx[region])).coerceIn(-MAX_STEP, MAX_STEP)
            ry[region] += (REGION_ALPHA * (y - gy - ry[region])).coerceIn(-MAX_STEP, MAX_STEP)
            rn[region] = min(rn[region] + 1, 1000)
            s.rCount[region]++; s.rx[region] += x; s.ry[region] += y
        }
        s.n++
        s.sx += x; s.sy += y; s.sxx += x * x; s.syy += y * y
        s.blockN++; s.blockX += x; s.blockY += y
        if (s.blockN == BLOCK) {
            s.blocks += floatArrayOf((s.blockX / BLOCK).toFloat(), (s.blockY / BLOCK).toFloat())
            s.blockN = 0; s.blockX = 0.0; s.blockY = 0.0
        }
        s.last = now
        if (!s.recognised && s.n >= RECOGNISE_AT) recognise(s, now)
        return true
    }

    private fun ensureSession(now: Long): Session {
        session?.let { if (now - it.last <= PAUSE_MILLIS) return it }
        endSession()
        return Session(now, now).also { session = it }
    }

    private fun decay(factor: Float) {
        gx *= factor; gy *= factor
        for (r in 0 until REGIONS) { rx[r] *= factor; ry[r] *= factor; rn[r] = (rn[r] * factor).toInt() }
    }

    private fun resetShort() {
        gx = 0f; gy = 0f; rx.fill(0f); ry.fill(0f); rn.fill(0)
    }

    private class Features(val dx: Float, val dy: Float, val spread: Float, val speed: Float, val corrections: Float, val bucket: Bucket)

    private fun features(s: Session): Features {
        val n = s.n.toDouble()
        val mx = s.sx / n; val my = s.sy / n
        val spread = sqrt(max(0.0, s.sxx / n - mx * mx + s.syy / n - my * my)).toFloat()
        val speed = if (s.intervals >= MIN_INTERVALS) (60_000.0 * s.intervals / s.intervalSum).toFloat() else Float.NaN
        val corrections = if (s.letters >= MIN_LETTERS) s.deletes.toFloat() / s.letters else Float.NaN
        return Features(mx.toFloat(), my.toFloat(), spread, speed, corrections, Bucket.of(local(s.start).hour))
    }

    private fun distance(f: Features, scene: Scene): Float {
        fun sq(v: Float) = v * v
        var d = sq((f.dx - scene.dx) / 0.08f) + sq((f.dy - scene.dy) / 0.08f) + sq((f.spread - scene.spread) / 0.06f)
        if (f.speed > 0f && scene.speed > 0f) d += sq(ln(f.speed / scene.speed) / 0.35f)
        if (!f.corrections.isNaN() && !scene.corrections.isNaN()) d += sq((f.corrections - scene.corrections) / 0.06f)
        d += when (f.bucket.steps(scene.bucket)) { 0 -> 0f; 1 -> 1f; else -> 3f }
        return d
    }

    private fun closest(f: Features): Scene? =
        scenes.map { it to distance(f, it) }.filter { it.second <= MATCH }.minByOrNull { it.second }?.first

    /** After [RECOGNISE_AT] touches: a session like a stored scene starts its short-term layer there. */
    private fun recognise(s: Session, now: Long) {
        s.recognised = true
        val scene = closest(features(s)) ?: return
        s.sceneId = scene.id
        gx = scene.dx; gy = scene.dy
        for (r in 0 until REGIONS) {
            rx[r] = scene.regionDx[r] - scene.dx; ry[r] = scene.regionDy[r] - scene.dy
            rn[r] = max(rn[r], min(scene.regionN[r], REGION_PRIOR.toInt()))
        }
        scene.lastDay = local(now).day
        dirty = true
    }

    /** Ends the session: the short-term layer is gone, a sustained deviation becomes a scene. */
    fun endSession() {
        val s = session ?: return
        session = null
        resetShort()
        if (s.n < MIN_SESSION) return
        val f = features(s)
        val shift = hypot(f.dx, f.dy)
        val standardError = f.spread / sqrt(s.n.toFloat())
        val agreeing = s.blocks.count { it[0] * f.dx + it[1] * f.dy > 0f }
        val shifted = shift >= MIN_SHIFT && shift >= 3f * standardError && s.blocks.size >= 2 &&
            agreeing >= ceil(CONSISTENT * s.blocks.size).toInt()
        val wider = !spreadRef.isNaN() && f.spread >= SPREAD_FACTOR * spreadRef && f.spread - spreadRef >= 0.04f
        val sloppier = !correctionRef.isNaN() && !f.corrections.isNaN() &&
            f.corrections >= CORRECTION_FACTOR * correctionRef && f.corrections - correctionRef >= 0.04f
        if (!(shifted || wider || sloppier)) { updateReferences(f); return }
        val day = local(s.start).day
        val regionDx = FloatArray(REGIONS) { if (s.rCount[it] > 0) (s.rx[it] / s.rCount[it]).toFloat() else f.dx }
        val regionDy = FloatArray(REGIONS) { if (s.rCount[it] > 0) (s.ry[it] / s.rCount[it]).toFloat() else f.dy }
        val scene = storeScene(f, day, regionDx, regionDy, s.rCount)
        dirty = true
        if (shifted) observe(Observation(day, f.bucket, scene.id, f.dx, f.dy, regionDx, regionDy, s.rCount.copyOf()))
    }

    private fun updateReferences(f: Features) {
        fun blend(ref: Float, value: Float) = if (value.isNaN()) ref else if (ref.isNaN()) value else ref + REF_ALPHA * (value - ref)
        spreadRef = blend(spreadRef, f.spread)
        speedRef = blend(speedRef, f.speed)
        correctionRef = blend(correctionRef, f.corrections)
        dirty = true
    }

    private fun storeScene(f: Features, day: Long, regionDx: FloatArray, regionDy: FloatArray, regionN: IntArray): Scene {
        val match = closest(f)
        if (match != null) {
            val w = min(match.sessions, MAX_SCENE_WEIGHT).toFloat()
            fun mix(old: Float, new: Float) = if (new.isNaN()) old else if (old.isNaN()) new else (old * w + new) / (w + 1f)
            match.dx = mix(match.dx, f.dx); match.dy = mix(match.dy, f.dy); match.spread = mix(match.spread, f.spread)
            match.speed = mix(match.speed, f.speed); match.corrections = mix(match.corrections, f.corrections)
            for (r in 0 until REGIONS) {
                val oldN = min(match.regionN[r], MAX_REGION_WEIGHT); val newN = regionN[r]
                if (oldN + newN == 0) continue
                match.regionDx[r] = (match.regionDx[r] * oldN + regionDx[r] * newN) / (oldN + newN)
                match.regionDy[r] = (match.regionDy[r] * oldN + regionDy[r] * newN) / (oldN + newN)
                match.regionN[r] = oldN + newN
            }
            match.sessions++
            match.lastDay = max(match.lastDay, day)
            return match
        }
        val scene = Scene(nextSceneId++, f.bucket, 1, day, day, regionDx.copyOf(), regionDy.copyOf(), regionN.copyOf(),
            f.dx, f.dy, f.spread, f.speed, f.corrections)
        scenes += scene
        while (scenes.size > MAX_SCENES) scenes.remove(scenes.minWith(compareBy<Scene>({ it.lastDay }, { it.sessions })))
        return scene
    }

    private fun observe(o: Observation) {
        observations += o
        observations.removeAll { o.day - it.day > WINDOW_DAYS }
        while (observations.size > MAX_OBSERVATIONS) observations.removeAt(0)
        val cluster = observations.filter { hypot(it.dx - o.dx, it.dy - o.dy) <= CLUSTER }
        if (cluster.size < PROMOTE_SESSIONS || cluster.map { it.day }.distinct().size < PROMOTE_DAYS ||
            cluster.map { it.bucket }.distinct().size < PROMOTE_BUCKETS) return
        val gdx = cluster.map { it.dx }.average().toFloat()
        val gdy = cluster.map { it.dy }.average().toFloat()
        fun step(v: Float) = (v * PROMOTE_FRACTION).coerceIn(-MAX_PROMOTION, MAX_PROMOTION)
        val px = FloatArray(REGIONS); val py = FloatArray(REGIONS)
        for (r in 0 until REGIONS) {
            val total = cluster.sumOf { it.regionN[r] }
            px[r] = step(if (total >= MIN_REGION_EVIDENCE) cluster.sumOf { it.regionDx[r].toDouble() * it.regionN[r] }.toFloat() / total else gdx)
            py[r] = step(if (total >= MIN_REGION_EVIDENCE) cluster.sumOf { it.regionDy[r].toDouble() * it.regionN[r] }.toFloat() / total else gdy)
        }
        observations.removeAll(cluster.toSet())
        // the scenes are relative to the base, which just moved
        val sx = step(gdx); val sy = step(gdy)
        scenes.forEach { scene ->
            scene.dx -= sx; scene.dy -= sy
            for (r in 0 until REGIONS) { scene.regionDx[r] -= px[r]; scene.regionDy[r] -= py[r] }
        }
        promotions++
        dirty = true
        onPromote?.invoke(Promotion(px, py))
    }

    /** How [scene] differs from the usual typing, for the settings page. */
    fun traits(scene: Scene): List<Trait> {
        val result = ArrayList<Trait>()
        if (!spreadRef.isNaN() && scene.spread > spreadRef * 1.15f) result += Trait.LESS_PRECISE
        if (!spreadRef.isNaN() && scene.spread < spreadRef / 1.15f) result += Trait.MORE_PRECISE
        if (scene.dy > TRAIT_SHIFT) result += Trait.LOWER
        if (scene.dy < -TRAIT_SHIFT) result += Trait.HIGHER
        if (scene.dx < -TRAIT_SHIFT) result += Trait.LEFT
        if (scene.dx > TRAIT_SHIFT) result += Trait.RIGHT
        if (!speedRef.isNaN() && scene.speed > speedRef * 1.15f) result += Trait.FASTER
        if (!speedRef.isNaN() && scene.speed > 0f && scene.speed < speedRef / 1.15f) result += Trait.SLOWER
        if (!correctionRef.isNaN() && scene.corrections > correctionRef + 0.03f) result += Trait.MORE_CORRECTIONS
        return result
    }

    /** Forgets [id] and the sessions it was seen in. */
    fun deleteScene(id: Int) {
        scenes.removeAll { it.id == id }
        observations.removeAll { it.sceneId == id }
        session?.let { if (it.sceneId == id) it.sceneId = -1 }
        dirty = true
    }

    /** Everything, including the running session. */
    fun clear() {
        session = null
        resetShort()
        scenes.clear(); observations.clear()
        spreadRef = Float.NaN; speedRef = Float.NaN; correctionRef = Float.NaN
        nextSceneId = 1; promotions = 0
        dirty = true
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L
        const val HOUR_MILLIS = 3_600_000L
        /** 3 rows times 3 columns of the letter keys. */
        const val REGIONS = 9
        /** The short-term layer alone moves a zone at most this far; the sum with the base is bounded again. */
        const val MAX_SHORT = 1f / 3f
        /** Weight of a new touch: after 30 touches about 95 % of a new grip is followed. */
        const val ALPHA = 0.1f
        const val REGION_ALPHA = 0.2f
        /** Touches in a region before its own value counts half. */
        const val REGION_PRIOR = 8f
        /** One touch moves the short-term layer by at most this much. */
        const val MAX_STEP = 0.03f
        const val MAX_RESIDUAL = 0.5f
        /** Only touches at most this far from the centre of the meant key (inside the normal key) count. */
        const val IN_KEY = 0.5f
        const val PAUSE_MILLIS = 5 * 60_000L
        const val FIELD_DECAY = 0.5f
        const val MAX_INTERVAL_MILLIS = 2_000L
        const val MIN_INTERVALS = 10
        const val MIN_LETTERS = 20
        const val BLOCK = 20
        /** A session is compared with the scenes after this many touches. */
        const val RECOGNISE_AT = 15
        const val MATCH = 4f
        /** Shortest session that can make a scene. */
        const val MIN_SESSION = 40
        /** Mean shift that counts as a deviation, in key sizes. */
        const val MIN_SHIFT = 0.12f
        /** Share of 20-touch blocks that must point the same way. */
        const val CONSISTENT = 0.75f
        const val SPREAD_FACTOR = 1.3f
        const val CORRECTION_FACTOR = 1.5f
        const val REF_ALPHA = 0.2f
        const val MAX_SCENES = 8
        const val MAX_SCENE_WEIGHT = 9
        const val MAX_REGION_WEIGHT = 200
        const val MAX_OBSERVATIONS = 60
        const val WINDOW_DAYS = 30
        const val CLUSTER = 0.08f
        /** Promotion: the same deviation in this many sessions, on this many days, in this many time buckets. */
        const val PROMOTE_SESSIONS = 5
        const val PROMOTE_DAYS = 3
        const val PROMOTE_BUCKETS = 2
        const val PROMOTE_FRACTION = 0.5f
        /** The base moves at most this far per promotion and axis. */
        const val MAX_PROMOTION = 0.06f
        const val MIN_REGION_EVIDENCE = 20
        const val TRAIT_SHIFT = 0.05f
    }
}
