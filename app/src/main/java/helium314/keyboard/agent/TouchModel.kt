// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Rect
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.TouchDynamics
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
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
 *
 * Stage 50 (opt-in, [dynamic]): these values are the base. Typed words then no longer change it;
 * they feed the short-term layer and the scene profiles of [TouchDynamics], and the base moves only
 * when the engine promotes a deviation that came back regularly. The applied offset is base plus
 * short-term, bounded to [MAX_SHIFT] again. Learning from a word waits until the next key shows the
 * word was kept: a delete first throws its touches away.
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

    // stage 50: dynamic hit zones
    private const val DYNAMICS_KEY = "dynamics"
    private const val PREVIOUS_KEY = "previous"
    /** A key that takes part in a promotion counts at least this many touches afterwards. */
    private const val PROMOTED_SAMPLES = 3 * MIN_SAMPLES
    /** Short-term layer and scenes switched on (both switches); read on every touch. */
    @Volatile @JvmStatic var dynamic = false
    /** The current field allows it: no password, incognito, no-learning field, paused learning or locked device. */
    @Volatile private var dynamicField = false
    private val dynamics = TouchDynamics(::localStamp).also { it.onPromote = ::promote }
    /** Region (0 until [TouchDynamics.REGIONS]) of each letter on the last keyboard used for learning, or -1. */
    private val regionOf = IntArray(CODES) { -1 }
    /** The last keyboard looked at by [updateRegions] and the geometry of its letters (positions and sizes). */
    private var regionKeyboard = WeakReference<helium314.keyboard.keyboard.Keyboard>(null)
    private var regionGeometry: List<Int>? = null
    /**
     * Confirmed touches per letter in the short-term layer since the process started (never stored). A
     * promotion only moves letters with a trained base or this many touches, see [promote]. Sessions are not
     * stored either, so the touches of every session that leads to a promotion were counted here.
     */
    private val dynamicTouches = IntArray(CODES)
    /** A keyboard with fewer letter keys is no letter layout (symbols, numbers) and keeps the regions. */
    private const val MIN_LETTER_KEYS = 10
    /** Touches of the last committed word, waiting for the next key: code, offset x, offset y. */
    private var pending: List<Triple<Int, Float, Float>>? = null
    private var appContext: Context? = null
    private var lastPackage: String? = null
    private var lastFieldId = 0
    /** Wall clock for the scenes; replaced by tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    private fun localStamp(millis: Long): TouchDynamics.Stamp {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        val offset = calendar.get(java.util.Calendar.ZONE_OFFSET) + calendar.get(java.util.Calendar.DST_OFFSET)
        return TouchDynamics.Stamp(Math.floorDiv(millis + offset, TouchDynamics.DAY_MILLIS), calendar.get(java.util.Calendar.HOUR_OF_DAY))
    }

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

    /**
     * Stage 50: a field was opened. [settings] may still be those of the previous field, so the field's
     * own restrictions come from [editorInfo] and the switches from the preferences.
     */
    @JvmStatic
    fun load(context: Context, settings: SettingsValues, editorInfo: EditorInfo?, restarting: Boolean) {
        load(context, settings)
        appContext = context.applicationContext ?: context
        val prefs = context.prefs()
        dynamic = prefs.getBoolean(AgentPreferences.ADAPT_TOUCH, false) && prefs.getBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, false)
        val restricted = editorInfo == null || InputTypeUtils.isAnyPasswordInputType(editorInfo.inputType)
            || (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
            || prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)
            || prefs.getBoolean(AgentPreferences.LEARNING_PAUSED, false)
            || AgentPreferences.TOUCH_CALIBRATION_FIELD == editorInfo.privateImeOptions
            || helium314.keyboard.compat.isDeviceLocked(context)
        val packageName = editorInfo?.packageName
        val sameField = restarting && packageName == lastPackage && editorInfo?.fieldId == lastFieldId
        lastPackage = packageName; lastFieldId = editorInfo?.fieldId ?: 0
        synchronized(lock) {
            pending = null // the next key decides about a word only in the same field
            dynamicField = dynamic && !restricted
            when {
                dynamicField -> dynamics.startField(packageName, sameField, clock())
                // switched on, restricted field: the session so far ends normally; nothing of this field reaches a scene
                dynamic -> dynamics.endSession()
                // switched off: exactly stages 22a/22b, so the session is dropped unseen (no scene, no promotion)
                else -> dynamics.discardSession()
            }
            saveDynamicsIfChanged(context)
        }
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
            runCatching { readDynamics(prefs(context).getString(DYNAMICS_KEY, null)) }.onFailure { dynamics.clear() }
            dynamics.dirty = false
            loaded = true
        }
    }

    private fun save(context: Context) {
        prefs(context).edit().putString(KEY, offsetsJson().toString()).apply()
    }

    private fun offsetsJson(): JSONObject {
        val json = JSONObject()
        for (i in 0 until CODES) if (count[i] > 0)
            json.put(i.toString(), JSONArray().put(dx[i].toDouble()).put(dy[i].toDouble()).put(count[i]))
        return json
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
     * Stage 50: with [dynamic] the touches go to the short-term layer, after the next key.
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
        if (dynamic) {
            // stage 50: the base stays as calibrated; the touches wait for the next key (see onKeyInput)
            if (!dynamicField || settings.mInputAttributes.mIsPasswordField) return
            updateRegions(keyboard)
            appContext?.let { synchronized(lock) { saveDynamicsIfChanged(it) } } // a new geometry may have ended a session
            val touches = ArrayList<Triple<Int, Float, Float>>()
            for (i in 0 until size) {
                if (xs[i] < 0 || ys[i] < 0) continue
                val box = boxes[meant[i]] ?: continue
                touches += Triple(meant[i], (xs[i] - paddingLeft - box.exactCenterX()) / box.width(),
                    (ys[i] - paddingTop - box.exactCenterY()) / box.height())
            }
            synchronized(lock) { pending = touches.takeIf { it.isNotEmpty() } }
            return
        }
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

    /**
     * Learned offset for [code] as applied: zero below [MIN_SAMPLES], damped while few, bounded.
     * Stage 50: plus the short-term layer of the key's region while [dynamic] works in this field.
     */
    @JvmStatic
    fun offsetX(code: Int): Float = applied(code, dx, true)
    @JvmStatic
    fun offsetY(code: Int): Float = applied(code, dy, false)

    /** The base alone (calibration, learning without stage 50, promotions). */
    @JvmStatic fun baseOffsetX(code: Int): Float = effective(code, dx)
    @JvmStatic fun baseOffsetY(code: Int): Float = effective(code, dy)

    private fun applied(code: Int, values: FloatArray, horizontal: Boolean): Float {
        val base = effective(code, values)
        if (!dynamic || !dynamicField) return base
        val i = index(code)
        if (i < 0 || regionOf[i] < 0) return base
        val short = if (horizontal) dynamics.shortX(regionOf[i]) else dynamics.shortY(regionOf[i])
        return (base + short).coerceIn(-MAX_SHIFT, MAX_SHIFT)
    }

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
            keepPrevious(context) // stage 50: one step back
            val before = appliedBase()
            values.forEach { (code, v) ->
                val i = index(code)
                if (i >= 0) { dx[i] = v.first; dy[i] = v.second; count[i] = v.third }
            }
            save(context)
            rebaseScenes(before, context)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            dx.fill(0f); dy.fill(0f); count.fill(0)
            dynamics.clear(); dynamics.dirty = false; pending = null; dynamicTouches.fill(0)
            check(prefs(context).edit().clear().commit())
        }
    }

    internal fun resetForTest() = synchronized(lock) {
        dx.fill(0f); dy.fill(0f); count.fill(0); loaded = false; enabled = false
        dynamic = false; dynamicField = false; dynamics.clear(); dynamics.dirty = false; pending = null
        regionOf.fill(-1); regionKeyboard = WeakReference(null); regionGeometry = null; lastPackage = null; lastFieldId = 0
        dynamicTouches.fill(0)
        clock = System::currentTimeMillis
    }

    // ---- stage 50: dynamic hit zones ----

    /** Keys that take back what was just typed: like a delete, they throw the touches of the last word away. */
    private fun discards(code: Int) = code == KeyCode.DELETE || code == KeyCode.UNDO || code == KeyCode.CLIPBOARD_CUT

    /**
     * Every key code (from KeyboardActionListenerImpl.onCodeInput, before it is handled). A delete, undo or cut
     * throws the touches of the last committed word away, any other key confirms them: the word was kept.
     * Letters and deletes also count for typing speed and correction rate; a held delete counts once
     * ([isKeyRepeat]). [keyboard] is the keyboard shown now: when its geometry differs from the one the
     * regions were made for (rotation, one-handed, split, another layout), the session ends. No code is stored.
     * A cursor move without a new field (a tap into the text) is not seen here: the next key confirms the word.
     */
    @JvmStatic
    @JvmOverloads
    fun onKeyInput(code: Int, isKeyRepeat: Boolean = false, keyboard: helium314.keyboard.keyboard.Keyboard? = null) {
        if (!dynamic || !dynamicField) return
        val settings = Settings.getValues()
        if (settings == null || settings.mIncognitoModeEnabled || settings.mLearningPaused || settings.mIsLocked) {
            synchronized(lock) { pending = null }
            return
        }
        if (isKeyRepeat) return // the first press of the held key already decided about the word and counted
        if (keyboard != null) updateRegions(keyboard)
        val now = clock()
        val discard = discards(code)
        synchronized(lock) {
            val word = pending
            pending = null
            if (!discard && word != null) word.forEach { (c, ox, oy) ->
                val i = index(c)
                if (i >= 0 && dynamics.onTap(regionOf[i], ox, oy, effective(c, dx), effective(c, dy), now))
                    dynamicTouches[i] = min(dynamicTouches[i] + 1, 1000)
            }
            dynamics.onKey(letter = index(code) >= 0, delete = code == KeyCode.DELETE, now = now)
            appContext?.let { saveDynamicsIfChanged(it) }
        }
    }

    /**
     * Letter keys in 3 rows times 3 columns of the letter area of [keyboard]. The shifted and the normal
     * keyboard share one geometry; another geometry (rotation, one-handed, split, another layout) ends the
     * session, so a scene stays in one geometry, and the regions are made again.
     */
    private fun updateRegions(keyboard: helium314.keyboard.keyboard.Keyboard) {
        if (regionKeyboard.get() === keyboard) return
        val letters = keyboard.sortedKeys.filter { index(it.code) >= 0 }
        if (letters.size < MIN_LETTER_KEYS) return // symbols (maybe with a µ), emoji: the letters keep their regions
        val geometry = ArrayList<Int>(letters.size * 5)
        letters.sortedBy { Character.toLowerCase(it.code) }.forEach { key ->
            val box = key.hitBox
            geometry += Character.toLowerCase(key.code); geometry += box.left; geometry += box.top
            geometry += box.width(); geometry += box.height()
        }
        val minX = letters.minOf { it.hitBox.exactCenterX() }; val maxX = letters.maxOf { it.hitBox.exactCenterX() }
        val minY = letters.minOf { it.hitBox.exactCenterY() }; val maxY = letters.maxOf { it.hitBox.exactCenterY() }
        fun third(v: Float, min: Float, max: Float) = if (max <= min) 1 else ((v - min) / (max - min) * 3).toInt().coerceIn(0, 2)
        synchronized(lock) {
            regionKeyboard = WeakReference(keyboard)
            if (geometry == regionGeometry) return
            if (regionGeometry != null) { dynamics.endSession(); pending = null }
            regionGeometry = geometry
            regionOf.fill(-1)
            letters.forEach { key ->
                regionOf[Character.toLowerCase(key.code)] = third(key.hitBox.exactCenterY(), minY, maxY) * 3 +
                    third(key.hitBox.exactCenterX(), minX, maxX)
            }
        }
    }

    /** Region of [code] on the last keyboard used for learning, or -1 (for tests and the page). */
    internal fun region(code: Int): Int = index(code).let { if (it < 0) -1 else regionOf[it] }

    /**
     * The engine found the same deviation regularly: the base of each trained key moves by its region's step.
     * Trained: a base from [MIN_SAMPLES] touches, or [MIN_SAMPLES] confirmed touches in the short-term layer
     * ([dynamicTouches]). Letters never typed keep their base and their count. The engine only sees
     * deviations of at least [TouchDynamics.MIN_SHIFT], so the base approaches a lasting deviation up to about
     * that much; the short-term layer carries the rest.
     */
    private fun promote(promotion: TouchDynamics.Promotion) {
        val context = appContext
        if (context != null) keepPrevious(context)
        for (i in 0 until CODES) {
            val region = regionOf[i]
            if (region < 0) continue
            if (count[i] < MIN_SAMPLES && dynamicTouches[i] < MIN_SAMPLES) continue
            val n = maxOf(count[i], PROMOTED_SAMPLES)
            val ax = (effective(i, dx) + promotion.regionDx[region]).coerceIn(-MAX_SHIFT, MAX_SHIFT)
            val ay = (effective(i, dy) + promotion.regionDy[region]).coerceIn(-MAX_SHIFT, MAX_SHIFT)
            // stored values are undamped: applied = stored * n / (n + MIN_SAMPLES)
            dx[i] = ax * (n + MIN_SAMPLES) / n; dy[i] = ay * (n + MIN_SAMPLES) / n; count[i] = n
        }
        if (context != null) save(context)
    }

    /** One step back for "Vorherige Kalibrierung": the base before the last calibration or promotion. */
    private fun keepPrevious(context: Context) {
        prefs(context).edit().putString(PREVIOUS_KEY, offsetsJson().toString()).apply()
    }

    fun hasPrevious(context: Context): Boolean = prefs(context).contains(PREVIOUS_KEY)

    /**
     * Restores the base kept by the last calibration or promotion. The scenes stay and move back by the
     * change of the base ([rebaseScenes]); the observations are dropped.
     */
    fun restorePrevious(context: Context): Boolean = synchronized(lock) {
        val stored = prefs(context).getString(PREVIOUS_KEY, null) ?: return false
        val json = runCatching { JSONObject(stored) }.getOrNull() ?: return false
        val before = appliedBase()
        dx.fill(0f); dy.fill(0f); count.fill(0)
        json.keys().forEach { k ->
            val i = k.toIntOrNull()?.let(::index) ?: -1
            if (i < 0) return@forEach
            val v = json.getJSONArray(k)
            dx[i] = v.getDouble(0).toFloat(); dy[i] = v.getDouble(1).toFloat(); count[i] = v.getInt(2)
        }
        save(context)
        check(prefs(context).edit().remove(PREVIOUS_KEY).commit())
        rebaseScenes(before, context)
        true
    }

    /** The applied base of every letter, x then y (for [rebaseScenes]). */
    private fun appliedBase(): Pair<FloatArray, FloatArray> =
        FloatArray(CODES) { effective(it, dx) } to FloatArray(CODES) { effective(it, dy) }

    /**
     * Stage 50: the base changed outside a promotion (calibration, back to the previous one). Scenes are
     * relative to the base, so the scene values of each region move back by the mean change of the applied
     * base over the letters of that region (unchanged letters count as 0). The scene totals, and regions not
     * known yet (no word typed with the dynamic zones since the process started), use the mean change over
     * all letters with a base before or after. Observations are dropped.
     */
    private fun rebaseScenes(before: Pair<FloatArray, FloatArray>, context: Context) {
        val (bx, by) = before
        val regions = TouchDynamics.REGIONS
        val sumX = DoubleArray(regions); val sumY = DoubleArray(regions); val n = IntArray(regions)
        var allX = 0.0; var allY = 0.0; var all = 0
        for (i in 0 until CODES) {
            val cx = effective(i, dx) - bx[i]; val cy = effective(i, dy) - by[i]
            val r = regionOf[i]
            if (r >= 0) { sumX[r] += cx; sumY[r] += cy; n[r]++ }
            if (bx[i] != 0f || by[i] != 0f || effective(i, dx) != 0f || effective(i, dy) != 0f) { allX += cx; allY += cy; all++ }
        }
        val meanX = if (all > 0) (allX / all).toFloat() else 0f
        val meanY = if (all > 0) (allY / all).toFloat() else 0f
        dynamics.rebase(FloatArray(regions) { if (n[it] > 0) (sumX[it] / n[it]).toFloat() else meanX },
            FloatArray(regions) { if (n[it] > 0) (sumY[it] / n[it]).toFloat() else meanY }, meanX, meanY)
        saveDynamicsIfChanged(context)
    }

    /** A scene for the settings page: what it looks like, never text. */
    class SceneInfo(val id: Int, val bucket: TouchDynamics.Bucket, val sessions: Int, val traits: List<TouchDynamics.Trait>)

    fun scenes(): List<SceneInfo> = synchronized(lock) {
        dynamics.scenes.map { SceneInfo(it.id, it.bucket, it.sessions, dynamics.traits(it)) }
    }

    fun promotions(): Int = dynamics.promotions

    fun deleteScene(context: Context, id: Int) = synchronized(lock) {
        dynamics.deleteScene(id)
        saveDynamicsIfChanged(context)
    }

    /** For tests: the engine behind the short-term layer. */
    internal val engine: TouchDynamics get() = dynamics

    private fun saveDynamicsIfChanged(context: Context) {
        if (!dynamics.dirty) return
        dynamics.dirty = false
        prefs(context).edit().putString(DYNAMICS_KEY, writeDynamics().toString()).apply()
    }

    /** Scene numbers are stored with 3 decimals (a thousandth of a key is far below a pixel): a smaller, faster JSON. */
    private fun round3(value: Float): Double = Math.round(value * 1000.0) / 1000.0
    private fun floats(values: FloatArray) = JSONArray().also { a -> values.forEach { a.put(round3(it)) } }
    private fun ints(values: IntArray) = JSONArray().also { a -> values.forEach { a.put(it) } }
    private fun JSONObject.putNumber(key: String, value: Float) = apply { if (!value.isNaN()) put(key, round3(value)) }
    private fun JSONObject.number(key: String) = if (has(key)) getDouble(key).toFloat() else Float.NaN
    private fun JSONArray.floats() = FloatArray(TouchDynamics.REGIONS) { if (it < length()) getDouble(it).toFloat() else 0f }
    private fun JSONArray.ints() = IntArray(TouchDynamics.REGIONS) { if (it < length()) getInt(it) else 0 }

    private fun writeDynamics(): JSONObject {
        val json = JSONObject().put("v", 1).put("next", dynamics.nextSceneId).put("promotions", dynamics.promotions)
            .putNumber("spreadRef", dynamics.spreadRef).putNumber("speedRef", dynamics.speedRef)
            .putNumber("correctionRef", dynamics.correctionRef)
        json.put("scenes", JSONArray().also { array -> dynamics.scenes.forEach { s ->
            array.put(JSONObject().put("id", s.id).put("bucket", s.bucket.name).put("sessions", s.sessions)
                .put("first", s.firstDay).put("last", s.lastDay).put("rx", floats(s.regionDx)).put("ry", floats(s.regionDy))
                .put("rn", ints(s.regionN)).putNumber("dx", s.dx).putNumber("dy", s.dy).putNumber("spread", s.spread)
                .putNumber("speed", s.speed).putNumber("corrections", s.corrections))
        } })
        json.put("observations", JSONArray().also { array -> dynamics.observations.forEach { o ->
            array.put(JSONObject().put("day", o.day).put("bucket", o.bucket.name).put("scene", o.sceneId)
                .putNumber("dx", o.dx).putNumber("dy", o.dy).put("rx", floats(o.regionDx)).put("ry", floats(o.regionDy))
                .put("rn", ints(o.regionN)))
        } })
        return json
    }

    private fun readDynamics(text: String?) {
        dynamics.clear()
        if (text == null) return
        val json = JSONObject(text)
        if (json.optInt("v") != 1) return
        dynamics.nextSceneId = json.optInt("next", 1)
        dynamics.promotions = json.optInt("promotions", 0)
        dynamics.spreadRef = json.number("spreadRef"); dynamics.speedRef = json.number("speedRef")
        dynamics.correctionRef = json.number("correctionRef")
        val scenes = json.optJSONArray("scenes") ?: JSONArray()
        for (i in 0 until min(scenes.length(), TouchDynamics.MAX_SCENES)) {
            val s = scenes.getJSONObject(i)
            dynamics.scenes += TouchDynamics.Scene(s.getInt("id"), TouchDynamics.Bucket.valueOf(s.getString("bucket")),
                s.getInt("sessions"), s.getLong("first"), s.getLong("last"), s.getJSONArray("rx").floats(),
                s.getJSONArray("ry").floats(), s.getJSONArray("rn").ints(), s.number("dx"), s.number("dy"),
                s.number("spread"), s.number("speed"), s.number("corrections"))
        }
        val observations = json.optJSONArray("observations") ?: JSONArray()
        for (i in 0 until min(observations.length(), TouchDynamics.MAX_OBSERVATIONS)) {
            val o = observations.getJSONObject(i)
            dynamics.observations += TouchDynamics.Observation(o.getLong("day"), TouchDynamics.Bucket.valueOf(o.getString("bucket")),
                o.getInt("scene"), o.number("dx"), o.number("dy"), o.getJSONArray("rx").floats(), o.getJSONArray("ry").floats(),
                o.getJSONArray("rn").ints())
        }
    }
}
