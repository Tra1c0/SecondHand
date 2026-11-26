// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import helium314.keyboard.latin.settings.SettingsValues
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import dev.agentkeyboard.core.CorrectionWeights
import java.util.Locale

/**
 * Rules the engine applies before it replaces a typed word automatically (stage 20a). They only
 * ever turn an automatic correction into a suggestion; they never correct anything themselves.
 */
object CorrectionGuard {
    /**
     * "mochte" and "möchte", "schon" and "schön", "wurde" and "würde": equal once the umlaut dots
     * are removed, but not equal. Only the umlaut counts: Spanish or French accents (esta/está)
     * are usually left out by habit, so correcting them stays allowed.
     */
    @JvmStatic
    fun differsOnlyByUmlaut(a: String, b: String): Boolean {
        val left = a.lowercase(Locale.ROOT)
        val right = b.lowercase(Locale.ROOT)
        if (left == right) return false
        return strip(left) == strip(right)
    }

    /** [correction] differs from [typed] only in upper and lower case ("essen" and "Essen"), stage 48. */
    @JvmStatic
    fun changesOnlyCase(typed: String, correction: String): Boolean =
        typed != correction && typed.lowercase(Locale.ROOT) == correction.lowercase(Locale.ROOT)

    private fun strip(word: String) = Normalizer.normalize(word, Normalizer.Form.NFD).replace("\u0308", "")

    /**
     * Whether an automatic correction from [typed] to [correction] may happen.
     * [typedIsDictionaryWord]: the typed word itself is a known word.
     */
    @JvmStatic
    fun allowsAutoCorrection(
        typed: String, correction: String, typedIsDictionaryWord: Boolean,
        keepValidWords: Boolean, keepUmlautVariants: Boolean, isRejected: (String, String) -> Boolean,
    ): Boolean {
        if (isRejected(typed, correction)) return false
        if (!typedIsDictionaryWord) return true
        if (keepValidWords) return false
        // both words exist and only the umlaut tells them apart: often the sentence cannot decide that
        if (keepUmlautVariants && differsOnlyByUmlaut(typed, correction)) return false
        return true
    }
}

/**
 * Corrections the user undid (Backspace right after it, or changed later in the field: stage 49 / 107b) and those he
 * kept, as weights per pair "typed -> corrected" (stage 107, owner 2026-11-26: "erst bei mehrfachem Zurücknehmen … nicht
 * endgültig"). A pair is held back ([contains]) while its weights say so ([CorrectionWeights]: one rejection weighs as much
 * as three confirmations, both fade with a half-life of 30 days, a faded rejection is forgotten). Before stage 107 one
 * rejection held a pair back for ever; old stored pairs count as one rejection. Opt-in, kept only on this device in the
 * keyboard's own storage, never logged. At most [MAX_PAIRS]; the least recently changed are dropped first.
 */
object RejectedCorrections {
    const val FILE = "agent_correction_memory"
    private const val KEY = "rejected"
    private const val KEY_WEIGHTS = "weights"
    const val MAX_PAIRS = 500
    private const val SEPARATOR = '\u0000'

    /** The weights of a pair at [time] (milliseconds), before fading. */
    private class Entry(val confirmed: Double, val rejected: Double, val time: Long)
    /** Weights of a pair now, faded: [confirmed] times kept or chosen, [rejected] times taken back. */
    data class Weights(val confirmed: Double, val rejected: Double)

    /** Ordered by last change; null until loaded. Under [lock]. */
    @Volatile private var pairs: LinkedHashMap<String, Entry>? = null
    /** Immutable copy for lock-free reads from the suggestion thread; replaced as a whole. */
    @Volatile private var lookup: Map<String, Entry> = emptyMap()
    private val lock = Any()
    @JvmField var clock: () -> Long = System::currentTimeMillis

    private fun key(typed: String, correction: String) =
        typed.lowercase(Locale.ROOT) + SEPARATOR + correction.lowercase(Locale.ROOT)

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun loaded(context: Context): LinkedHashMap<String, Entry> {
        pairs?.let { return it }
        synchronized(lock) {
            pairs?.let { return it }
            val stored = LinkedHashMap<String, Entry>()
            val now = clock()
            runCatching {
                val prefs = prefs(context)
                val weights = prefs.getString(KEY_WEIGHTS, null)
                if (weights != null) {
                    val array = JSONArray(weights)
                    for (i in 0 until array.length()) array.getJSONObject(i).let {
                        stored[it.getString("k")] = Entry(it.optDouble("c", 0.0), it.optDouble("r", 0.0), it.optLong("t", now))
                    }
                } else {
                    // before stage 107: a set of rejected pairs, each one rejection now
                    val array = JSONArray(prefs.getString(KEY, "[]"))
                    for (i in 0 until array.length()) stored[array.getString(i)] = Entry(0.0, 1.0, now)
                }
            }
            lookup = HashMap(stored)
            pairs = stored
            return stored
        }
    }

    private fun faded(entry: Entry, now: Long): Weights {
        val age = maxOf(0L, now - entry.time)
        val r = CorrectionWeights.faded(entry.rejected, age)
        return Weights(CorrectionWeights.faded(entry.confirmed, age), if (r < CorrectionWeights.FORGET_BELOW) 0.0 else r)
    }

    /**
     * Whether the pair is held back now (its weights speak against it). Read by the suggestion engine on its own thread;
     * loads from storage once. The name stays from before stage 107, when one rejection held a pair back for ever.
     */
    @JvmStatic
    fun contains(context: Context, typed: String, correction: String): Boolean {
        loaded(context)
        val entry = lookup[key(typed, correction)] ?: return false
        val w = faded(entry, clock())
        return CorrectionWeights.blocks(w.confirmed, w.rejected)
    }

    /** The faded weights of a pair, or null when nothing is stored (for tests and the settings page). */
    @JvmStatic
    fun weights(context: Context, typed: String, correction: String): Weights? {
        loaded(context)
        return lookup[key(typed, correction)]?.let { faded(it, clock()) }
    }

    /** Whether the settings allow storing a pair now: switch on, not paused, not incognito, not locked. */
    @JvmStatic
    fun mayRemember(settings: SettingsValues): Boolean =
        settings.mRememberRejectedCorrections && !settings.mLearningPaused
            && !settings.mIncognitoModeEnabled && !settings.mIsLocked

    /** Stages 107/108: a pair may be stored with the switch, or as a correction of the weighted rules with learning on. */
    private fun mayStore(settings: SettingsValues, typed: String, correction: String): Boolean =
        mayRemember(settings) || (RuleCorrections.wasOffered(typed, correction) && RuleCorrections.mayLearn(settings))

    /**
     * Called when an automatic correction was undone (also tells [Memory] and [ForeignWords]); [at] is the field position
     * where the restored [typed] word starts, or -1 when unknown. Pairs that differ only in case are not stored.
     * Adds one rejection to the pair's weights.
     */
    @JvmStatic @JvmOverloads
    fun remember(context: Context, typed: String, correction: String, settings: SettingsValues, at: Int = -1) {
        // stage 46: an undone correction is one use of the typed word as an own word (the memory has its
        // own switch), counted only at the place given (second review)
        if (!typed.equals(correction, ignoreCase = true)) Memory.onCorrectionUndone(context, typed, settings, at)
        // stage 51: a word of another language kept against the correction is remembered (its own rules)
        ForeignWords.onCorrectionUndone(context, typed, correction, settings, at)
        if (RuleCorrections.wasOffered(typed, correction)) RuleCorrections.undone.incrementAndGet()
        add(context, typed, correction, settings, confirmed = 0.0, rejected = 1.0)
    }

    /**
     * Stage 107: the owner kept a correction of [typed] to [correction] (an automatic one committed, or the word picked in the
     * strip): one confirmation. Completions ("Bahnho" -> "Bahnhof") and changes of case only are not counted.
     */
    @JvmStatic
    fun confirm(context: Context, typed: String, correction: String, settings: SettingsValues?) {
        if (settings == null || FieldCorrectionWatcher.isCompletion(typed, correction)) return
        add(context, typed, correction, settings, confirmed = 1.0, rejected = 0.0)
    }

    /**
     * Stage 107b: the same undo is often seen twice (Backspace right after the correction, then the field watcher of stage 49
     * at the next word sees the restored word): a rejection of the same pair within this time counts once.
     */
    const val SAME_REJECTION_MILLIS = 60_000L
    private var lastRejection: String? = null
    private var lastRejectionTime = 0L

    private fun add(context: Context, typed: String, correction: String, settings: SettingsValues, confirmed: Double, rejected: Double) {
        if (!mayStore(settings, typed, correction)) return
        if (typed.isBlank() || correction.isBlank() || typed.equals(correction, ignoreCase = true)) return
        val key = key(typed, correction)
        synchronized(lock) {
            val current = loaded(context)
            val now = clock()
            if (rejected > 0.0) {
                if (key == lastRejection && now - lastRejectionTime in 0 until SAME_REJECTION_MILLIS) return
                lastRejection = key
                lastRejectionTime = now
            }
            val old = current.remove(key)?.let { faded(it, now) } ?: Weights(0.0, 0.0)
            current[key] = Entry(old.confirmed + confirmed, old.rejected + rejected, now)
            while (current.size > MAX_PAIRS) current.remove(current.keys.first())
            lookup = HashMap(current)
            save(context, current)
        }
    }

    private fun save(context: Context, current: Map<String, Entry>) {
        val array = JSONArray()
        current.forEach { (k, e) -> array.put(JSONObject().put("k", k).put("c", e.confirmed).put("r", e.rejected).put("t", e.time)) }
        prefs(context).edit().putString(KEY_WEIGHTS, array.toString()).remove(KEY).apply()
    }

    /** For the settings page: typed and corrected word of every pair the owner took back at least once (not faded), oldest first. */
    fun all(context: Context): List<Pair<String, String>> = synchronized(lock) {
        val now = clock()
        loaded(context).filter { faded(it.value, now).rejected > 0.0 }
            .map { it.key.substringBefore(SEPARATOR) to it.key.substringAfter(SEPARATOR) }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            check(prefs(context).edit().clear().commit())
            pairs = LinkedHashMap()
            lookup = emptyMap()
            lastRejection = null
        }
    }

    /** Tests only: forget what is held in memory so the next access reads storage again. */
    internal fun resetForTest() = synchronized(lock) { pairs = null; lookup = emptyMap(); lastRejection = null }
}
