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
 * subtype or a second language of one, except the second languages of the subtype in use, which
 * HeliBoard's multilingual typing covers) and the main dictionary of the current language does not
 * know it in any case (so a case correction of stage 48 is never a matter of this store). It is
 * remembered
 *  - at once when the owner kept it against an automatic correction (undone with backspace, the typed
 *    form tapped, or changed back later in the field: the paths of [RejectedCorrections.remember]),
 *  - otherwise when it was committed exactly as typed, in a field with autocorrection, a second time
 *    at another place (another field or another position), like the field corrections of stage 49.
 * A remembered word the owner replaces by another word (picked in the strip, changed later in the
 * field) is forgotten in that language ([onReplaced]).
 *
 * Learning follows "Personalisierte Vorschläge" (HeliBoard's learning switch; switching it off clears
 * this store) and never happens while learning is paused, in incognito (which covers password and
 * no-learning fields), in fields the memory excludes or blocks, or while the device is locked. Like
 * HeliBoard's learning, no word the other dictionary marks as possibly offensive while those are
 * blocked. Kept on this device (`agent_foreign_words`, device-protected like the learned
 * dictionaries), never logged.
 *
 * The dictionaries are opened only when a word needs a check, on [worker], read-only like the second
 * languages of HeliBoard's multilingual typing: the main dictionary of the current language and those
 * of the other languages, one per enabled language. They stay open across a switch of the keyboard
 * language; a language no longer enabled is closed, all are closed with [closeDictionaries] (low
 * memory, end of the keyboard) and [clear].
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
    /** Review N2: a remembered word was used again; written with the next field change (like [Memory]). Under [lock]. */
    private var dirty = false

    /** The last word the suggestion thread saw, its language and whether a main dictionary of that language knows it. */
    @Volatile private var lastTyped: Typed? = null
    private var pending: Pending? = null
    private val sessions = AtomicLong(System.currentTimeMillis())
    @Volatile private var session = sessions.incrementAndGet()

    /**
     * Changes when input starts in another field or ends ([onFieldChanged] from the editor controller),
     * not when the keyboard language changes; the field corrections of stage 49 tie their language to it.
     */
    @JvmStatic val fieldSession: Long get() = session

    /** Checks against the dictionaries run here, in order; replaced in tests. */
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

    /**
     * The suggestion thread built suggestions for [typed] in [language]; [known]: a main dictionary of that
     * language has it in some case (true as well when that could not be told, review N5).
     */
    @JvmStatic fun onTyped(typed: String, language: String, known: Boolean) { lastTyped = Typed(typed, language, known) }

    /**
     * Input starts in another field or ends: a new place, and an undone correction waiting there is dropped.
     * With [context], remembered words used since are written (review N2).
     */
    @JvmStatic @JvmOverloads fun onFieldChanged(context: Context? = null) {
        synchronized(lock) {
            pending = null
            if (context != null && dirty) save(context)
        }
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
        // the strip saw the typed word: a word of this language (in any case) is no foreign word; the
        // check on the worker asks the dictionary again for the paths where the strip saw another word
        val seen = lastTyped
        if (seen != null && seen.word == typed && seen.language == language && seen.known) return
        synchronized(lock) { pending = Pending(typed, at, language) }
    }

    /**
     * Review M2: the owner replaced [typed] by [replacement] (another word picked in the strip, or the
     * word changed later in the field): a remembered word, or a candidate, of the keyboard language is
     * forgotten there. A change of case or a word finished ("hol" -> "hola") is no replacement. Like
     * learning, nothing changes while learning is paused, in a blocked field or while the device is locked.
     */
    @JvmStatic fun onReplaced(context: Context, settings: SettingsValues?, typed: String, replacement: String) {
        if (settings == null || typed.isEmpty() || typed.equals(replacement, ignoreCase = true)
            || FieldCorrectionWatcher.isCompletion(typed, replacement)) return
        if (settings.mLearningPaused || Memory.isBlocked(context, settings) || Memory.deviceLocked(context)) return
        forget(context, languageOf(settings), typed)
    }

    /** [word] is no longer remembered (nor a candidate) for [language]. */
    @JvmStatic fun forget(context: Context, language: String, word: String) {
        synchronized(lock) {
            val c = loaded(context)
            val k = key(language, word)
            var changed = c.remove(k) != null
            if (remembered.containsKey(k)) {
                remembered = LinkedHashMap(remembered).apply { remove(k) }
                rebuildLookup()
                changed = true
            }
            if (changed) save(context)
        }
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
        if (settings == null || !mayLearn(context, settings, editorInfo)) return
        val language = languageOf(settings)
        if (language.isEmpty()) return
        // kept against an automatic correction: the restored word still stands where the undo put it
        var keptAt = -1
        if (waiting != null && waiting.language == language && cursor >= 0) {
            val text = before()?.toString()
            if (text != null && isWordAt(text, waiting.word, waiting.at - (cursor - text.length))) keptAt = waiting.at
        }
        if (waiting != null && keptAt >= 0) schedule(context, settings, language, waiting.word, true, keptAt)
        // review N8: the word committed now counts as well, when it was committed exactly as typed where the
        // autocorrection had its say and no main dictionary of this language knew it when the strip was built
        val seen = lastTyped
        val place = if (cursor >= 0) cursor - word.length else -1
        val plain = typed != null && typed == word && settings.mAutoCorrectEnabled &&
            seen != null && seen.word == typed && seen.language == language && !seen.known
        if (plain && !(keptAt >= 0 && keptAt == place && waiting?.word == word)) schedule(context, settings, language, word, false, place)
    }

    /** [word] of [language] at [place] goes to the check, unless it is remembered already (then only refreshed). */
    private fun schedule(context: Context, settings: SettingsValues, language: String, word: String, explicit: Boolean, place: Int) {
        if (!isCandidate(word)) return
        if (refreshed(context, language, word)) return
        val current = settings.mLocale ?: return
        val locales = enabledLocales()
        // review N2: the second languages of the subtype in use are HeliBoard's multilingual typing
        val excluded = settings.mSecondaryLocales.orEmpty().map { it.language }.toSet()
        val blockOffensive = settings.mBlockPotentiallyOffensive
        val field = session
        val scheduled = generation
        // review N4: nothing on the worker may end the keyboard's process
        worker.execute {
            runCatching { check(context, current, language, word, explicit, field, place, locales, excluded, blockOffensive, scheduled) }
        }
    }

    /** Review N2: a remembered word used again only gets a new time in memory (written with the next field change). */
    private fun refreshed(context: Context, language: String, word: String): Boolean = synchronized(lock) {
        loaded(context)
        val k = key(language, word)
        val known = remembered[k] ?: return false
        remembered.remove(k)
        remembered[k] = Stored(known.word, known.from, known.session, known.position, clock())
        dirty = true
        true
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

    /** On [worker]: [word] counts if another enabled language has it and the current one does not. */
    private fun check(context: Context, current: Locale, language: String, word: String, explicit: Boolean, field: Long,
                      position: Int, locales: List<Locale>, excluded: Set<String>, blockOffensive: Boolean, scheduled: Int) {
        val (from, form) = Dictionaries.find(context, current, word, locales, excluded, blockOffensive) ?: return
        synchronized(lock) {
            if (scheduled != generation) return // cleared in between
            val c = loaded(context)
            val k = key(language, form)
            val now = clock()
            val known = remembered[k]
            if (known != null) { // remembered meanwhile (another check): only a new time
                remembered.remove(k)
                remembered[k] = Stored(known.word, known.from, field, position, now)
                dirty = true
                return
            }
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
            dirty = false
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
        dirty = false
    }

    /** For the page "Was ich mir gemerkt habe": remembered words, most recent first (candidates with [withCandidates]). */
    fun all(context: Context, withCandidates: Boolean = false): List<Entry> = synchronized(lock) {
        val c = loaded(context)
        fun entries(map: Map<String, Stored>, isRemembered: Boolean) =
            map.map { (k, v) -> Entry(k.substringBefore(SEPARATOR), v.word, v.from, isRemembered, v.seen) }
        (entries(remembered, true) + if (withCandidates) entries(c, false) else emptyList()).sortedByDescending { it.seen }
    }

    /** The owner deletes [entry] on the page. */
    fun delete(context: Context, entry: Entry) = forget(context, entry.language, entry.word)

    /** Forgets everything ("Gelernte Wörter löschen", "Personalisierte Vorschläge" switched off); checks still waiting are dropped. */
    fun clear(context: Context) {
        synchronized(lock) {
            generation++
            pending = null
            runCatching { prefs(context).edit().clear().commit() }
            candidates = LinkedHashMap()
            remembered = LinkedHashMap()
            lookup = emptyMap()
            dirty = false
        }
        lastTyped = null
        closeDictionaries()
    }

    /** Review N3: closes the opened dictionaries (low memory, the keyboard ends); a later check opens what it needs again. */
    @JvmStatic fun closeDictionaries() {
        runCatching { worker.execute { runCatching { Dictionaries.close() } } }
    }

    internal fun resetForTest() = synchronized(lock) {
        candidates = null; remembered = LinkedHashMap(); lookup = emptyMap(); pending = null; lastTyped = null; dirty = false
    }

    /**
     * The main dictionaries of the current language and of the other enabled languages, each opened on
     * first need and kept per locale (review N3: a switch of the keyboard language reopens nothing).
     * Only used on [worker].
     */
    private object Dictionaries {
        private val open = HashMap<Locale, Dictionary>()

        private fun of(context: Context, locale: Locale): Dictionary? {
            open[locale]?.let { return it }
            val dictionary = runCatching { DictionaryFactory.createMainDictionaryCollection(context, locale, false) }.getOrNull()
                ?.takeIf { it.isInitialized || run { runCatching { it.close() }; false } } ?: return null
            open[locale] = dictionary
            return dictionary
        }

        /** [word] as typed, in lower case and with a capital (review N6: "hund" is German "Hund"). */
        private fun forms(word: String, locale: Locale): List<String> {
            val lower = word.lowercase(locale)
            return listOf(word, lower, lower.replaceFirstChar { it.titlecase(locale) }).distinct()
        }

        /**
         * The language of another enabled language (not in [excluded]) that has [word] and the form it has
         * there; null when the main dictionary of [current] knows it in any case (review M1), when that
         * dictionary is missing, or when the form is possibly offensive and those are blocked (review N1).
         */
        fun find(context: Context, current: Locale, word: String, enabled: List<Locale>, excluded: Set<String>,
                 blockOffensive: Boolean): Pair<String, String>? {
            // a language no longer enabled is closed; the others stay open
            val keep = enabled.toSet() + current
            open.keys.filter { it !in keep }.forEach { locale -> open.remove(locale)?.let { runCatching { it.close() } } }
            val own = of(context, current) ?: return null // unknown whether the current language has it
            if (forms(word, current).any { own.isValidWord(it) }) return null
            for (locale in enabled) {
                if (locale.language == current.language || locale.language in excluded) continue
                val dictionary = of(context, locale) ?: continue
                val form = forms(word, locale).firstOrNull { dictionary.isValidWord(it) } ?: continue
                if (blockOffensive && dictionary.getFrequency(form) == 0) continue
                return locale.language to form
            }
            return null
        }

        fun close() {
            open.values.forEach { runCatching { it.close() } }
            open.clear()
        }
    }
}
