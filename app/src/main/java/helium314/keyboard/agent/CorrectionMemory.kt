// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import helium314.keyboard.latin.settings.SettingsValues
import org.json.JSONArray
import java.text.Normalizer
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
 * Corrections the user undid with backspace right after they happened: "typed -> corrected" is
 * never applied automatically again. Opt-in, kept only on this device in the keyboard's own
 * storage, never logged. At most [MAX_PAIRS]; the oldest are dropped first.
 */
object RejectedCorrections {
    const val FILE = "agent_correction_memory"
    private const val KEY = "rejected"
    const val MAX_PAIRS = 500
    private const val SEPARATOR = '\u0000'

    /** Insertion-ordered keys; null until loaded. */
    @Volatile private var pairs: LinkedHashSet<String>? = null
    /** Immutable copy for lock-free reads from the suggestion thread; replaced as a whole. */
    @Volatile private var lookup: Set<String> = emptySet()
    private val lock = Any()

    private fun key(typed: String, correction: String) =
        typed.lowercase(Locale.ROOT) + SEPARATOR + correction.lowercase(Locale.ROOT)

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun loaded(context: Context): LinkedHashSet<String> {
        pairs?.let { return it }
        synchronized(lock) {
            pairs?.let { return it }
            val stored = LinkedHashSet<String>()
            runCatching {
                val array = JSONArray(prefs(context).getString(KEY, "[]"))
                for (i in 0 until array.length()) stored.add(array.getString(i))
            }
            lookup = HashSet(stored)
            pairs = stored
            return stored
        }
    }

    /** Read by the suggestion engine on its own thread; loads from storage once. */
    @JvmStatic
    fun contains(context: Context, typed: String, correction: String): Boolean {
        loaded(context)
        return key(typed, correction) in lookup
    }

    /** Whether the settings allow storing a pair now: switch on, not paused, not incognito, not locked. */
    @JvmStatic
    fun mayRemember(settings: SettingsValues): Boolean =
        settings.mRememberRejectedCorrections && !settings.mLearningPaused
            && !settings.mIncognitoModeEnabled && !settings.mIsLocked

    /** Called when an automatic correction was undone (also tells [Memory]). Pairs that differ only in case are not stored. */
    @JvmStatic
    fun remember(context: Context, typed: String, correction: String, settings: SettingsValues) {
        // stage 46: an undone correction is one use of the typed word as an own word (the memory has its own switch)
        if (!typed.equals(correction, ignoreCase = true)) Memory.onCorrectionUndone(context, typed, settings)
        if (!mayRemember(settings)) return
        if (typed.isBlank() || correction.isBlank() || typed.equals(correction, ignoreCase = true)) return
        val key = key(typed, correction)
        synchronized(lock) {
            val current = loaded(context)
            current.remove(key)
            current.add(key)
            while (current.size > MAX_PAIRS) current.remove(current.first())
            lookup = HashSet(current)
            prefs(context).edit().putString(KEY, JSONArray(current.toList()).toString()).apply()
        }
    }

    /** For the settings page: typed and corrected word of every stored pair, oldest first. */
    fun all(context: Context): List<Pair<String, String>> = synchronized(lock) {
        loaded(context).map { it.substringBefore(SEPARATOR) to it.substringAfter(SEPARATOR) }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            check(prefs(context).edit().clear().commit())
            pairs = LinkedHashSet()
            lookup = emptySet()
        }
    }

    /** Tests only: forget what is held in memory so the next access reads storage again. */
    internal fun resetForTest() = synchronized(lock) { pairs = null; lookup = emptySet() }
}
