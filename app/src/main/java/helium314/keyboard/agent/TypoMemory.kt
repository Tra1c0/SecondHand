// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.SettingsValues
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Stage 20b: the user's own recurring typos. A pair "typed -> meant" is counted only on a clear
 * signal: after typing, the user picked a different word from the suggestion strip. Optionally
 * per previous word, so "ich mochte" and "ich möchte" can be told apart.
 *
 * Applying a pair is cautious: three equal confirmations and at least [DOMINANCE] of all
 * confirmations for that typed form; a typed form that is itself a known word is corrected only
 * in a context where the user corrected it three times. Opt-in, only on this device, at most
 * [MAX_TYPED_FORMS] typed forms, oldest dropped first, never logged.
 */
object TypoMemory {
    const val FILE = "agent_typo_memory"
    private const val KEY = "pairs"
    const val MAX_TYPED_FORMS = 1000
    private const val MAX_CONTEXTS = 20
    const val MIN_CONFIRMATIONS = 3
    const val DOMINANCE = 0.8
    private const val MAX_WORD = 48

    /** Source of a learned word that no dictionary offered at that moment. */
    @JvmField val DICTIONARY = Dictionary.PhonyDictionary("agent_learned_typo")

    /**
     * Confirmations of one meant word: in total and per previous word. [form] is its spelling;
     * an initial capital is kept only when it cannot come from the start of a sentence.
     */
    private class Target(var form: String, var total: Int = 0, val contexts: LinkedHashMap<String, Int> = LinkedHashMap())
    private const val MAX_MEANT = 5

    /** typed form -> (meant word -> confirmations); access order is insertion order, re-inserted on use. */
    @Volatile private var pairs: LinkedHashMap<String, LinkedHashMap<String, Target>>? = null
    private val lock = Any()

    private fun norm(word: String) = word.lowercase(Locale.ROOT)
    private fun plain(word: String) = java.text.Normalizer.normalize(norm(word).replace("ß", "ss"), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun loaded(context: Context): LinkedHashMap<String, LinkedHashMap<String, Target>> {
        pairs?.let { return it }
        synchronized(lock) {
            pairs?.let { return it }
            val result = LinkedHashMap<String, LinkedHashMap<String, Target>>()
            runCatching {
                val array = JSONArray(prefs(context).getString(KEY, "[]"))
                for (i in 0 until array.length()) {
                    val entry = array.getJSONObject(i)
                    val targets = LinkedHashMap<String, Target>()
                    val meant = entry.getJSONArray("meant")
                    for (j in 0 until meant.length()) {
                        val t = meant.getJSONObject(j)
                        val contexts = LinkedHashMap<String, Int>()
                        val c = t.optJSONObject("contexts")
                        c?.keys()?.forEach { contexts[it] = c.getInt(it) }
                        targets[t.getString("word")] = Target(t.optString("form", t.getString("word")), t.getInt("total"), contexts)
                    }
                    result[entry.getString("typed")] = targets
                }
            }
            pairs = result
            return result
        }
    }

    private fun save(context: Context, data: Map<String, Map<String, Target>>) {
        val array = JSONArray()
        data.forEach { (typed, targets) ->
            val meant = JSONArray()
            targets.forEach { (word, target) ->
                meant.put(JSONObject().put("word", word).put("form", target.form).put("total", target.total).put("contexts", JSONObject(target.contexts as Map<*, *>)))
            }
            array.put(JSONObject().put("typed", typed).put("meant", meant))
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    @JvmStatic
    fun mayLearn(settings: SettingsValues): Boolean =
        settings.mLearnTypos && !settings.mLearningPaused && !settings.mIncognitoModeEnabled && !settings.mIsLocked

    /** The user typed [typed] and then picked [meant] from the strip. [previous] is the word before, if any. */
    @JvmStatic
    fun confirm(context: Context, typed: String, meant: String, previous: String?, settings: SettingsValues) {
        if (!mayLearn(settings)) return
        if (typed.length < 2 || typed.length > MAX_WORD || meant.isBlank() || meant.length > MAX_WORD) return
        if (typed.equals(meant, ignoreCase = true) || typed.any { it.isDigit() || it.isWhitespace() }) return
        // picking a longer word that starts with what was typed is a completion, not a typo (also Mad -> Mädchen)
        val plainMeant = plain(meant); val plainTyped = plain(typed)
        if (plainMeant.length > plainTyped.length && plainMeant.startsWith(plainTyped)) return
        val key = norm(typed)
        synchronized(lock) {
            val data = loaded(context)
            val targets = data.remove(key) ?: LinkedHashMap()
            // typed in lower case but picked with a capital: a noun or name, keep the capital
            val form = if (typed.first().isUpperCase()) meant.replaceFirstChar { it.lowercase(Locale.ROOT) } else meant
            val target = targets.getOrPut(norm(meant)) { Target(form) }
            if (form.first().isUpperCase()) target.form = form
            target.total++
            while (targets.size > MAX_MEANT) targets.remove(targets.minByOrNull { it.value.total }!!.key)
            previous?.let(::norm)?.takeIf { it.isNotBlank() && it.length <= MAX_WORD }?.let { prev ->
                target.contexts[prev] = (target.contexts.remove(prev) ?: 0) + 1
                while (target.contexts.size > MAX_CONTEXTS) target.contexts.remove(target.contexts.keys.first())
            }
            data[key] = targets // most recently used last
            while (data.size > MAX_TYPED_FORMS) data.remove(data.keys.first())
            save(context, data)
        }
    }

    /** An automatic correction from a learned pair was undone: the pair is dropped completely, also in [FieldCorrectionMemory]. */
    @JvmStatic
    fun forget(context: Context, typed: String, meant: String) {
        FieldCorrectionMemory.forget(context, typed, meant) // stage 49: the same pair learned later in a field
        synchronized(lock) {
            val data = loaded(context)
            val targets = data[norm(typed)] ?: return
            if (targets.remove(norm(meant)) == null) return
            if (targets.isEmpty()) data.remove(norm(typed))
            save(context, data)
        }
    }

    /**
     * The meant word for [typed] in its learned spelling, or null. [previous] is the word before;
     * [typedIsDictionaryWord] restricts learned corrections of real words to their context.
     */
    @JvmStatic
    fun meantWord(context: Context, typed: String, previous: String?, typedIsDictionaryWord: Boolean): String? =
        ownMeantWord(context, typed, previous, typedIsDictionaryWord)
            // stage 49: corrections learned later in the field, only for typed forms that are no word
            ?: if (typedIsDictionaryWord) null else FieldCorrectionMemory.learnedFor(context, typed)

    private fun ownMeantWord(context: Context, typed: String, previous: String?, typedIsDictionaryWord: Boolean): String? {
        loaded(context)
        synchronized(lock) {
            val targets = pairs?.get(norm(typed)) ?: return null
            val prev = previous?.let(::norm)
            if (prev != null) {
                val inContext = targets.mapValues { it.value.contexts[prev] ?: 0 }
                decide(inContext)?.let { return targets[it]?.form }
            }
            if (typedIsDictionaryWord) return null
            return decide(targets.mapValues { it.value.total })?.let { targets[it]?.form }
        }
    }

    private fun decide(counts: Map<String, Int>): String? {
        val best = counts.maxByOrNull { it.value } ?: return null
        val sum = counts.values.sum()
        return best.key.takeIf { best.value >= MIN_CONFIRMATIONS && best.value >= DOMINANCE * sum }
    }

    /** For the settings page: typed form, meant word and confirmations, most recent first. */
    fun all(context: Context): List<Triple<String, String, Int>> = synchronized(lock) {
        loaded(context).entries.reversed().flatMap { (typed, targets) -> targets.map { Triple(typed, it.key, it.value.total) } }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            check(prefs(context).edit().clear().commit())
            pairs = LinkedHashMap()
        }
    }

    internal fun resetForTest() = synchronized(lock) { pairs = null }
}
