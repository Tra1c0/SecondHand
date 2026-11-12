// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Rect
import helium314.keyboard.keyboard.Key
import helium314.keyboard.latin.settings.SettingsValues
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

/**
 * Stage 22: where the user really hits each letter key. For every letter it keeps the mean
 * offset of the touches from the key centre, in key widths and key heights, so the values hold
 * for every keyboard height and screen. Nothing else is kept: no text, no order of touches.
 *
 * Hit detection uses these offsets to move the invisible hit box of a letter key towards where
 * the user aims; the drawn keys never move. A box moves at most [MAX_SHIFT] of its size and only
 * after [MIN_SAMPLES] touches, so a touch near the middle of a key always stays on that key.
 */
object TouchModel {
    const val FILE = "agent_touch_model"
    private const val KEY = "offsets"
    /** Letters up to U+024F: Latin with umlauts and accents. */
    private const val CODES = 0x250
    const val MIN_SAMPLES = 15
    const val MAX_SHIFT = 1f / 3f
    /** A touch further away than this from the intended key centre is a slip to another key, not aim. */
    const val OUTLIER = 0.9f
    /** New touches weigh at least 1/[MEMORY] so that the model can follow a changed grip. */
    private const val MEMORY = 200

    private val dx = FloatArray(CODES)
    private val dy = FloatArray(CODES)
    private val count = IntArray(CODES)
    private val lock = Any()
    @Volatile private var loaded = false

    /** Read on every touch; set from the settings. */
    @Volatile @JvmStatic var enabled = false

    private fun index(code: Int): Int {
        val lower = Character.toLowerCase(code)
        return if (lower in 0 until CODES && Character.isLetter(lower)) lower else -1
    }

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @JvmStatic
    fun load(context: Context, settings: SettingsValues) {
        enabled = settings.mAdaptTouch
        loadStored(context)
    }

    /** Reads the stored values once, e.g. for the settings page before the keyboard was shown. */
    @JvmStatic
    fun loadStored(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching {
                val json = JSONObject(prefs(context).getString(KEY, "{}")!!)
                json.keys().forEach { k ->
                    val i = index(k.toInt())
                    if (i < 0) return@forEach
                    val v = json.getJSONArray(k)
                    dx[i] = v.getDouble(0).toFloat(); dy[i] = v.getDouble(1).toFloat(); count[i] = v.getInt(2)
                }
            }
            loaded = true
        }
    }

    private fun save(context: Context) {
        val json = JSONObject()
        for (i in 0 until CODES) if (count[i] > 0)
            json.put(i.toString(), org.json.JSONArray().put(dx[i].toDouble()).put(dy[i].toDouble()).put(count[i]))
        prefs(context).edit().putString(KEY, json.toString()).apply()
    }

    /** Adds one touch at ([offsetX], [offsetY]) key sizes from the centre of the key for [code]. */
    @JvmStatic
    fun record(code: Int, offsetX: Float, offsetY: Float): Boolean {
        val i = index(code)
        if (i < 0 || abs(offsetX) > OUTLIER || abs(offsetY) > OUTLIER) return false
        synchronized(lock) {
            val n = min(count[i], MEMORY - 1) + 1
            dx[i] += (offsetX - dx[i]) / n
            dy[i] += (offsetY - dy[i]) / n
            count[i] = min(count[i] + 1, Int.MAX_VALUE - 1)
        }
        return true
    }

    @JvmStatic fun persist(context: Context) = synchronized(lock) { save(context) }

    private var unsaved = 0
    private const val SAVE_EVERY = 10 // words

    /**
     * Stage 22b: learns from a word that was just committed after tap typing. Each touch counts for
     * the letter at the same position of the committed word, so a corrected slip ("wprd" -> "word")
     * teaches where the user aimed. Only words of the same length with at most two different letters.
     * [xs]/[ys] are keyboard coordinates as WordComposer keeps them (the keyboard view already
     * subtracted its padding); [paddingLeft]/[paddingTop] are for callers with view coordinates.
     */
    @JvmStatic
    fun learnFromWord(context: Context, typed: String, committed: String, xs: IntArray, ys: IntArray, size: Int,
                      keyboard: helium314.keyboard.keyboard.Keyboard?, paddingLeft: Int, paddingTop: Int, settings: SettingsValues) {
        if (!settings.mAdaptTouch || settings.mLearningPaused || settings.mIncognitoModeEnabled || settings.mIsLocked) return
        if (keyboard == null) return
        val typedCodes = helium314.keyboard.latin.common.StringUtils.toCodePointArray(typed)
        val meant = helium314.keyboard.latin.common.StringUtils.toCodePointArray(committed.lowercase())
        if (typedCodes.size != size || meant.size != size || size < 2) return
        var differences = 0
        for (i in 0 until size) if (Character.toLowerCase(typedCodes[i]) != meant[i]) differences++
        if (differences > 2) return
        val boxes = HashMap<Int, Rect>()
        keyboard.sortedKeys.forEach { key -> if (index(key.code) >= 0) boxes[Character.toLowerCase(key.code)] = key.hitBox }
        var learned = false
        for (i in 0 until size) {
            if (xs[i] < 0 || ys[i] < 0) continue
            val box = boxes[meant[i]] ?: continue
            val ox = (xs[i] - paddingLeft - box.exactCenterX()) / box.width()
            val oy = (ys[i] - paddingTop - box.exactCenterY()) / box.height()
            learned = record(meant[i], ox, oy) || learned
        }
        if (learned && ++unsaved >= SAVE_EVERY) { unsaved = 0; persist(context) }
    }

    /** Learned offset for [code] as applied: zero below [MIN_SAMPLES], damped while few, bounded. */
    @JvmStatic
    fun offsetX(code: Int): Float = effective(code, dx)
    @JvmStatic
    fun offsetY(code: Int): Float = effective(code, dy)

    private fun effective(code: Int, values: FloatArray): Float {
        val i = index(code)
        if (i < 0) return 0f
        val n = count[i]
        if (n < MIN_SAMPLES) return 0f
        val damped = values[i] * n / (n + MIN_SAMPLES)
        return damped.coerceIn(-MAX_SHIFT, MAX_SHIFT)
    }

    @JvmStatic fun samples(code: Int): Int = index(code).let { if (it < 0) 0 else count[it] }

    /**
     * Hit detection with learned offsets. [primary] is the key the normal detection found. Among
     * the letter keys in [nearby] whose hit box, moved by its offset, contains the touch, the one
     * whose moved centre is closest wins. Without offsets the moved boxes are the normal boxes, so
     * nothing changes; non-letter keys never take part.
     */
    @JvmStatic
    fun adjust(primary: Key?, nearby: List<Key>, x: Int, y: Int): Key? {
        if (!enabled || primary == null || index(primary.code) < 0) return primary
        var best: Key? = null
        var bestDistance = Float.MAX_VALUE
        for (key in nearby) {
            if (index(key.code) < 0) continue
            val box: Rect = key.hitBox
            val w = box.width().toFloat(); val h = box.height().toFloat()
            if (w <= 0f || h <= 0f) continue
            val left = box.left + offsetX(key.code) * w
            val top = box.top + offsetY(key.code) * h
            if (x < left || x >= left + w || y < top || y >= top + h) continue
            val nx = (x - (left + w / 2)) / w; val ny = (y - (top + h / 2)) / h
            val distance = nx * nx + ny * ny
            if (distance < bestDistance) { bestDistance = distance; best = key }
        }
        if (best == null || best === primary) return primary
        // boxes overlap by a pixel: where neither key has learned anything, the normal rule decides
        if (!hasOffset(best.code) && !hasOffset(primary.code)) return primary
        return best
    }

    private fun hasOffset(code: Int) = offsetX(code) != 0f || offsetY(code) != 0f

    /** Offsets for display, letters with at least one touch: code to (dx, dy, samples). */
    fun all(): Map<Int, Triple<Float, Float, Int>> = synchronized(lock) {
        (0 until CODES).filter { count[it] > 0 }.associateWith { Triple(dx[it], dy[it], count[it]) }
    }

    /** Replaces the learned values of the given letters, e.g. from a calibration. */
    fun replace(context: Context, values: Map<Int, Triple<Float, Float, Int>>) {
        synchronized(lock) {
            values.forEach { (code, v) ->
                val i = index(code)
                if (i >= 0) { dx[i] = v.first; dy[i] = v.second; count[i] = v.third }
            }
            save(context)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            dx.fill(0f); dy.fill(0f); count.fill(0)
            check(prefs(context).edit().clear().commit())
        }
    }

    internal fun resetForTest() = synchronized(lock) { dx.fill(0f); dy.fill(0f); count.fill(0); loaded = false; enabled = false }
}
