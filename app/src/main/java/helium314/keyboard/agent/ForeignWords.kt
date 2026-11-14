// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage 51: words of another enabled language the owner wrote while typing in the current one
 * ("hola" in a German message). Such a word is remembered for the current language only: its
 * autocorrection no longer replaces the word, and the strip offers it while its start is typed
 * (behind the automatic correction, never as one, like the own words of stage 46).
 *
 * A word counts when it is a word of the main dictionary of another enabled language (any enabled
 * subtype or a second language of one) and no main dictionary of the current language knows it in any
 * case (so a case correction of stage 48 is never a matter of this store). It is remembered
 *  - at once when the owner kept it against an automatic correction (undone with backspace, the typed
 *    form tapped, or changed back later in the field: the paths of [RejectedCorrections.remember]),
 *  - otherwise when it was committed exactly as typed, in a field with autocorrection, a second time
 *    at another place (another field or another position), like the field corrections of stage 49.
 *
 * Learning follows "Personalisierte Vorschläge" (HeliBoard's learning switch; switching it off clears
 * this store) and never happens while learning is paused, in incognito (which covers password and
 * no-learning fields), in fields the memory excludes or blocks, or while the device is locked. Kept on
 * this device (`agent_foreign_words`, device-protected like the learned dictionaries), never logged.
 *
 * The dictionaries of the other languages are opened only when a word needs a check, on [worker],
 * read-only like the second languages of HeliBoard's multilingual typing; they stay open while the
 * enabled languages and the current language are the same.
 */
object ForeignWords {
    const val FILE = "agent_foreign_words"
    private const val KEY = "data"
    const val MAX_REMEMBERED = 500
    const val MAX_CANDIDATES = 200
    const val MAX_WORD = 32
    const val MIN_PREFIX = 2
    private const val SEPARATOR = '\u0000'

    /** Source of a remembered word in the strip. */
    @JvmField val DICTIONARY = Dictionary.PhonyDictionary("agent_foreign_word")

    /** A word for the page: remembered for [language], a word of [from]. */
    data class Entry(val language: String, val word: String, val from: String, val remembered: Boolean, val seen: Long)

    private class Stored(val word: String, val from: String, val session: Long, val position: Int, val seen: Long)
    private class Typed(val word: String, val language: String, val known: Boolean)
    private class Pending(val word: String, val at: Int, val language: String)

    @Volatile private var candidates: LinkedHashMap<String, Stored>? = null
    @Volatile private var remembered: LinkedHashMap<String, Stored> = LinkedHashMap()
    /** language -> (lower case -> form) of the remembered words, for lock-free reads from the suggestion thread. */
    @Volatile private var lookup: Map<String, Map<String, String>> = emptyMap()
    private val lock = Any()
    /** Raised by [clear]: checks scheduled before are dropped. */
    @Volatile private var generation = 0

    /** The last word the suggestion thread saw, its language and whether a main dictionary of that language knows it. */
    @Volatile private var lastTyped: Typed? = null
    private var pending: Pending? = null
    private val sessions = AtomicLong(System.currentTimeMillis())
    @Volatile private var session = sessions.incrementAndGet()

    /** Checks against the other languages' dictionaries run here, in order; replaced in tests. */
    @JvmStatic var worker: Executor = Executors.newSingleThreadExecutor { Thread(it, "agent-foreign-words").apply { isDaemon = true } }
    @JvmField var clock: () -> Long = System::currentTimeMillis

    private fun key(language: String, word: String) = language + SEPARATOR + word.lowercase(Locale.ROOT)

    /** The language whose data a commit or a lookup belongs to: the keyboard's current language. */
    @JvmStatic fun languageOf(settings: SettingsValues?): String = settings?.mLocale?.language?.lowercase(Locale.ROOT).orEmpty()

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- rules ----

    /** A word that can be remembered: letters (any script) with inner `'` or `-`, 2 to [MAX_WORD] characters. */
    @JvmStatic fun isCandidate(word: String): Boolean =
        word.length in 2..MAX_WORD && word.first().isLetter() && word.last().isLetter() &&
            word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }

    /** Learning: "Personalisierte Vorschläge" on, not paused, no blocked field (incognito, password, no-learning, excluded app), not locked. */
    @JvmStatic fun mayLearn(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean =
        settings != null && settings.mUsePersonalizedDicts && !settings.mLearningPaused &&
            !Memory.isBlocked(context, settings, editorInfo) && !Memory.deviceLocked(context)

    // ---- learning ----

    /** The suggestion thread built suggestions for [typed] in [language]; [known]: a main dictionary of that language has it in some case. */
    @JvmStatic fun onTyped(typed: String, language: String, known: Boolean) { lastTyped = Typed(typed, language, known) }

    /** Input starts in another field or ends: a new place, and an undone correction waiting there is dropped. */
    @JvmStatic fun onFieldChanged() {
        synchronized(lock) { pending = null }
        session = sessions.incrementAndGet()
    }

    /**
     * An automatic correction from [typed] was undone and [typed] now starts at field position [at]
     * (see [RejectedCorrections.remember]). It counts at the next commit if the word still stands
     * there. A change of case only is no foreign word; an unknown place counts nothing.
     */
    @JvmStatic fun onCorrectionUndone(context: Context, typed: String, correction: String, settings: SettingsValues?, at: Int) {
        if (at < 0 || typed.equals(correction, ignoreCase = true) || !isCandidate(typed)) return
        if (settings == null || !settings.mUsePersonalizedDicts || settings.mLearningPaused ||
            Memory.isBlocked(context, settings) || Memory.deviceLocked(context)) return
        val language = languageOf(settings)
        // the strip saw the typed word: a word of this language (in any case) is no foreign word
        val seen = lastTyped
        if (seen != null && seen.word == typed && seen.language == language && seen.known) return
        synchronized(lock) { pending = Pending(typed, at, language) }
    }

    /**
     * Called for every word the owner committed that the engine learns (from [Memory.onWordCommitted]:
     * no command, no generated or private text, not in incognito, no slow app): [word] as committed,
     * [typed] as typed (null for a gesture or a resumed word), [before] the text before the cursor
     * (read only while an undone correction waits), [cursor] the cursor after the word.
     */
    @JvmStatic fun onWordCommitted(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?, word: String,
                                   typed: String?, before: () -> CharSequence?, cursor: Int) {
        val waiting = synchronized(lock) { pending.also { pending = null } }
        if (!mayLearn(context, settings, editorInfo)) return
        val language = languageOf(settings)
        if (language.isEmpty()) return
        var kept: String? = null
        var place = -1
        var explicit = false
        // kept against an automatic correction: the restored word still stands where the undo put it
        if (waiting != null && waiting.language == language && cursor >= 0) {
            val text = before()?.toString()
            if (text != null && isWordAt(text, waiting.word, waiting.at - (cursor - text.length))) {
                kept = waiting.word
                place = waiting.at
                explicit = true
            }
        }
        // otherwise committed exactly as typed where the autocorrection had its say, and no main
        // dictionary of this language knew it when the strip was built
        if (kept == null) {
            val seen = lastTyped
            val plain = typed != null && typed == word && settings!!.mAutoCorrectEnabled &&
                seen != null && seen.word == typed && seen.language == language && !seen.known
            if (!plain) return
            kept = word
            place = if (cursor >= 0) cursor - word.length else -1
        }
        if (!isCandidate(kept)) return
        val locales = enabledLocales()
        val field = session
        val scheduled = generation
        worker.execute { check(context, language, kept, explicit, field, place, locales, scheduled) }
    }

    private fun isWordAt(text: String, word: String, at: Int): Boolean {
        if (at < 0 || !text.startsWith(word, at)) return false
        val end = at + word.length
        val startOk = at == 0 || !text[at - 1].isLetterOrDigit()
        val endOk = end == text.length || !(text[end].isLetterOrDigit() || text[end] == '\'' || text[end] == '-')
        return startOk && endOk
    }

    /** The main locales of the enabled subtypes and their second languages, one per language; replaced in tests. */
    @JvmStatic var enabledLocales: () -> List<Locale> = {
        runCatching {
            SubtypeSettings.getEnabledSubtypes(true).flatMap { listOf(it.locale()) + getSecondaryLocales(it.extraValue) }
                .filter { it.language.isNotEmpty() }.distinctBy { it.language }
        }.getOrDefault(emptyList())
    }

    /** On [worker]: [word] counts if another enabled language has it. */
    private fun check(context: Context, language: String, word: String, explicit: Boolean, field: Long, position: Int,
                      locales: List<Locale>, scheduled: Int) {
        val (from, form) = OtherLanguages.find(context, language, word, locales) ?: return
        synchronized(lock) {
            if (scheduled != generation) return // cleared in between
            val c = loaded(context)
            val k = key(language, form)
            val now = clock()
            val known = remembered[k]
            if (known != null) { remember(k, Stored(known.word, known.from, field, position, now)); save(context); return }
            val first = c[k]
            if (explicit || (first != null && (first.session != field || first.position != position))) {
                c.remove(k)
                remember(k, Stored(form, from, field, position, now))
                save(context)
                return
            }
            if (first != null) return // the same place again
            c[k] = Stored(form, from, field, position, now)
            while (c.size > MAX_CANDIDATES) c.remove(c.keys.first())
            save(context)
        }
    }

    /** Under [lock]. */
    private fun remember(k: String, entry: Stored) {
        val r = LinkedHashMap(remembered)
        r.remove(k)
        r[k] = entry
        while (r.size > MAX_REMEMBERED) r.remove(r.keys.first())
        remembered = r
        rebuildLookup()
    }

    private fun rebuildLookup() {
        val byLanguage = HashMap<String, HashMap<String, String>>()
        remembered.forEach { (k, v) -> byLanguage.getOrPut(k.substringBefore(SEPARATOR)) { HashMap() }[k.substringAfter(SEPARATOR)] = v.word }
        lookup = byLanguage
    }

    // ---- the engine asks (suggestion thread) ----

    /** Whether [word] is remembered for [language]: no automatic correction of it. */
    @JvmStatic fun isRemembered(context: Context?, settings: SettingsValues?, language: String, word: String): Boolean {
        if (context == null || settings == null || !settings.mUsePersonalizedDicts || word.isEmpty()) return false
        loaded(context)
        return lookup[language]?.containsKey(word.lowercase(Locale.ROOT)) == true
    }

    /** A remembered word of [language] that [typed] starts and that is longer, for the strip; null when none, blocked or locked. */
    @JvmStatic fun completion(context: Context?, settings: SettingsValues?, language: String, typed: String): String? {
        if (context == null || settings == null || !settings.mUsePersonalizedDicts || typed.length < MIN_PREFIX) return null
        if (Memory.isBlocked(context, settings)) return null
        loaded(context)
        val words = lookup[language] ?: return null
        val start = typed.lowercase(Locale.ROOT)
        val found = words.entries.filter { it.key.length > start.length && it.key.startsWith(start) }.minByOrNull { it.key.length }?.value
        return found?.takeIf { !Memory.deviceLocked(context) }
    }

    // ---- storage ----

    private fun loaded(context: Context): LinkedHashMap<String, Stored> {
        candidates?.let { return it }
        synchronized(lock) {
            candidates?.let { return it }
            val c = LinkedHashMap<String, Stored>()
            val r = LinkedHashMap<String, Stored>()
            runCatching {
                val root = JSONObject(prefs(context).getString(KEY, "{}")!!)
                fun read(name: String, into: LinkedHashMap<String, Stored>) = root.optJSONArray(name)?.let { a ->
                    for (i in 0 until a.length()) a.getJSONObject(i).let {
                        val word = it.getString("word")
                        if (!isCandidate(word)) return@let
                        into[key(it.getString("lang"), word)] = Stored(word, it.getString("from"), it.optLong("session"), it.optInt("position", -1), it.getLong("seen"))
                    }
                }
                read("candidates", c)
                read("remembered", r)
            }
            remembered = r
            rebuildLookup()
            candidates = c
            return c
        }
    }

    /** Under [lock]. */
    private fun save(context: Context) {
        fun write(map: Map<String, Stored>, withPlace: Boolean) = JSONArray().also { a ->
            map.forEach { (k, v) ->
                val o = JSONObject().put("lang", k.substringBefore(SEPARATOR)).put("word", v.word).put("from", v.from).put("seen", v.seen)
                if (withPlace) o.put("session", v.session).put("position", v.position)
                a.put(o)
            }
        }
        val root = JSONObject().put("candidates", write(candidates.orEmpty(), true)).put("remembered", write(remembered, false))
        runCatching { prefs(context).edit().putString(KEY, root.toString()).apply() }
    }

    /** For the page "Was ich mir gemerkt habe": remembered words, most recent first (candidates with [withCandidates]). */
    fun all(context: Context, withCandidates: Boolean = false): List<Entry> = synchronized(lock) {
        val c = loaded(context)
        fun entries(map: Map<String, Stored>, isRemembered: Boolean) =
            map.map { (k, v) -> Entry(k.substringBefore(SEPARATOR), v.word, v.from, isRemembered, v.seen) }
        (entries(remembered, true) + if (withCandidates) entries(c, false) else emptyList()).sortedByDescending { it.seen }
    }

    /** The owner deletes [entry] on the page. */
    fun delete(context: Context, entry: Entry) {
        synchronized(lock) {
            val c = loaded(context)
            val k = key(entry.language, entry.word)
            c.remove(k)
            if (remembered.containsKey(k)) { remembered = LinkedHashMap(remembered).apply { remove(k) }; rebuildLookup() }
            save(context)
        }
    }

    /** Forgets everything ("Gelernte Wörter löschen", "Personalisierte Vorschläge" switched off); checks still waiting are dropped. */
    fun clear(context: Context) {
        synchronized(lock) {
            generation++
            pending = null
            runCatching { prefs(context).edit().clear().commit() }
            candidates = LinkedHashMap()
            remembered = LinkedHashMap()
            lookup = emptyMap()
        }
        lastTyped = null
        worker.execute { OtherLanguages.close() }
    }

    internal fun resetForTest() = synchronized(lock) {
        candidates = null; remembered = LinkedHashMap(); lookup = emptyMap(); pending = null; lastTyped = null
    }

    /**
     * The main dictionaries of the enabled languages other than the current one, opened on first need.
     * Only used on [worker].
     */
    private object OtherLanguages {
        private var opened: List<Locale> = emptyList()
        private var dictionaries: List<Dictionary> = emptyList()

        /** The language of another enabled language that has [word] (also in lower case) and the form it has there. */
        fun find(context: Context, current: String, word: String, enabled: List<Locale>): Pair<String, String>? {
            val wanted = enabled.filter { it.language != current }
            if (wanted != opened) {
                close()
                dictionaries = wanted.mapNotNull { locale ->
                    runCatching { DictionaryFactory.createMainDictionaryCollection(context, locale, false) }
                        .getOrNull()?.takeIf { it.isInitialized || run { it.close(); false } }
                }
                opened = wanted
            }
            for (dictionary in dictionaries) {
                val language = dictionary.mLocale?.language ?: continue
                if (dictionary.isValidWord(word)) return language to word
                val lower = word.lowercase(dictionary.mLocale)
                if (lower != word && dictionary.isValidWord(lower)) return language to lower
            }
            return null
        }

        fun close() {
            dictionaries.forEach { runCatching { it.close() } }
            dictionaries = emptyList()
            opened = emptyList()
        }
    }
}
