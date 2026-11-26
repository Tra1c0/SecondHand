// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.provider.UserDictionary
import androidx.core.content.edit
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.prefs
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Stage 110 (owner, 2026-11-26: "gibts wird nicht korrigiert … nach jedem Update das Wörterbuch, die eigenen Wörter etc. nur
 * für solche Fälle automatisch überschreiben; die anderen Wörter dürfen nicht erwischt werden"). Before a rule class existed
 * the owner typed such words uncorrected, and the keyboard learned them: as own words ([Memory]: three commits of a word no
 * main dictionary knows), as kept words of another language ([ForeignWords]), in the personal dictionary (auto-add) and the
 * typing history. Learned, they block the new rule for good ("gibts" stays while "drehts" becomes "dreht's").
 *
 * Once per app update ([markDue], from the version check at the app start; not after a fresh install) and keyboard
 * language, after the main dictionary is loaded, every learned word is asked whether a
 * rule class corrects it now ([run]'s `target`: the short forms of stage 108, a very common word with one neighbouring key
 * and two swapped letters of stages 101/75, each with its own exclusions and switch, and only when no main dictionary of an
 * active or enabled language knows the word). Only those words leave the four stores; all others stay untouched. A word
 * whose correction the owner took back at least once ([RejectedCorrections] has a rejection for the pair) stays as well:
 * that is a decision of the owner, not a leftover. Runs on its own thread, logs nothing, counts only (diagnosis card).
 */
object RuleClassCleanup {
    private const val FILE = "agent_rule_cleanup"
    private const val REMOVED = "removed_"
    private val done = HashSet<String>()
    private val lock = Any()
    @JvmStatic var executor: Executor = Executors.newSingleThreadExecutor()
    /** In the app's settings: the build that still has to run the cleanup. */
    const val DUE = "agent_rule_cleanup_due"

    /** Called by the version check when the app was updated: every keyboard language cleans up once for this build. */
    @JvmStatic fun markDue(appPrefs: android.content.SharedPreferences) = appPrefs.edit { putInt(DUE, BuildConfig.VERSION_CODE) }

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Words removed by the last run for [language] (diagnosis card); -1 before the first run. */
    @JvmStatic fun lastRemoved(context: Context, language: String): Int = prefs(context).getInt(REMOVED + language, -1)

    /**
     * Starts the cleanup for [language] when the update marked due ([markDue]) has not run it yet and [ready] says the dictionaries it asks are
     * open (otherwise it is tried again on a later call). [target] answers with the rule correction of a word (null: no rule
     * class corrects it); [removeFromHistory] takes a word out of the typing history. Both are called on the cleanup thread.
     * Cheap when done: one set lookup.
     */
    @JvmStatic
    fun maybeRun(context: Context, language: String, ready: () -> Boolean, target: (String) -> String?, removeFromHistory: (String) -> Unit) {
        if (language.isEmpty()) return
        val key = language.lowercase(Locale.ROOT)
        synchronized(lock) { if (key in done) return }
        val app = context.applicationContext ?: context
        val due = app.prefs().getInt(DUE, -1)
        if (due < 0 || prefs(app).getInt(key, -1) == due) { synchronized(lock) { done.add(key) }; return }
        if (!ready()) return
        synchronized(lock) { if (!done.add(key)) return }
        executor.execute { runCatching { run(app, key, target, removeFromHistory, due) } }
    }

    /** One cleanup for [language] now (the caller's thread); returns the number of words removed. */
    @JvmStatic
    fun run(context: Context, language: String, target: (String) -> String?, removeFromHistory: (String) -> Unit,
            build: Int = BuildConfig.VERSION_CODE): Int {
        val removed = LinkedHashSet<String>()
        fun corrected(word: String): Boolean {
            val meant = target(word) ?: return false
            // the owner took this correction back at least once: the word is a decision of the owner
            val weights = RejectedCorrections.weights(context, word, meant)
            return weights == null || weights.rejected <= 0.0
        }
        // own words and their counted uses (both would make the word own again)
        val ownWords = Memory.ownWords(context) + Memory.candidates(context).map { it.first }
        for (word in ownWords.distinct()) if (corrected(word)) { Memory.removeOwnWord(context, word); removed += word.lowercase(Locale.ROOT) }
        // kept words of another language typed on this keyboard
        for (entry in ForeignWords.all(context, withCandidates = true)) {
            if (entry.language != language || !corrected(entry.word)) continue
            ForeignWords.forget(context, entry.language, entry.word)
            removed += entry.word.lowercase(Locale.ROOT)
        }
        // the personal dictionary: entries for this language or for all languages
        runCatching {
            val resolver = context.contentResolver
            val columns = arrayOf(UserDictionary.Words._ID, UserDictionary.Words.WORD, UserDictionary.Words.LOCALE)
            val hits = ArrayList<Pair<Long, String>>()
            resolver.query(UserDictionary.Words.CONTENT_URI, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val word = cursor.getString(1) ?: continue
                    val locale = cursor.getString(2)
                    val forLanguage = locale.isNullOrEmpty() || locale.lowercase(Locale.ROOT).substringBefore('_').substringBefore('-') == language
                    if (forLanguage && corrected(word)) hits += cursor.getLong(0) to word
                }
            }
            for ((id, word) in hits) {
                resolver.delete(UserDictionary.Words.CONTENT_URI, "${UserDictionary.Words._ID}=?", arrayOf(id.toString()))
                removed += word.lowercase(Locale.ROOT)
            }
        }
        // the typing history: the words found above (it keeps typed words and offers them again)
        for (word in removed) runCatching { removeFromHistory(word) }
        prefs(context).edit().putInt(language, build).putInt(REMOVED + language, removed.size).apply()
        return removed.size
    }

    internal fun resetForTest() = synchronized(lock) { done.clear() }
}
