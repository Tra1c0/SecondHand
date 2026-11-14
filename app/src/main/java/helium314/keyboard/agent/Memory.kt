// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.content.edit
import dev.agentkeyboard.core.memory.OwnWordBook
import dev.agentkeyboard.core.memory.Phrase
import dev.agentkeyboard.core.memory.PhraseBook
import dev.agentkeyboard.core.memory.PhrasePrediction
import dev.agentkeyboard.core.memory.PhraseText
import dev.agentkeyboard.core.memory.ReplacementTracker
import dev.agentkeyboard.core.memory.ShortcutBook
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.prefs
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Stage 46 "Gedächtnis": the glue between the engine and the pure memory of agent-core
 * (`dev.agentkeyboard.core.memory`). Learns only from words the owner committed (the engine's
 * user-history hook, [onWordCommitted]) and from undone corrections ([onCorrectionUndone]).
 *
 * Opt-in ([AgentPreferences.MEMORY], default off). Nothing is learned in password fields, incognito
 * (which covers no-learning fields), while learning is paused, while the device is locked or in an
 * excluded app; nothing is shown in such fields either (pausing only stops learning). Stored in the
 * device-protected storage like the other learned data (`agent_phrase_memory`), as readable text the
 * owner sees and deletes on the page "Was ich mir gemerkt habe". Nothing is logged.
 *
 * The memory never changes text: the strip offers ([MemoryChips]) and only a tap appends after the
 * cursor (or, for a remembered abbreviation, replaces the one abbreviation word just typed). Own
 * words and remembered abbreviations only keep the autocorrection off themselves; it stays a
 * single-word correction.
 */
object Memory {
    const val FILE = "agent_phrase_memory"
    internal const val PHRASES = "phrases"
    internal const val OWN_WORDS = "own_words"
    internal const val SHORTCUTS = "shortcuts"
    private const val EXCLUDED = "excluded_apps"
    const val MAX_EXCLUDED = 50
    /** Text before the cursor read after a commit: the longest phrase plus room for commas. */
    const val LOOKBACK = PhraseBook.MAX_TEXT + 8
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    /** Learned data is written at most this often while typing, and when the field is left. */
    private const val SAVE_EVERY_MILLIS = 30_000L
    /** Committed words remembered as "no main dictionary knows it" for the abbreviation rule. */
    private const val MAX_UNKNOWN = 16
    /** Counted uses remembered per field to count each place once. */
    private const val MAX_COUNTED = 200
    /** Source of the next word and own words in the strip. */
    @JvmField val DICTIONARY = Dictionary.PhonyDictionary("agent_memory")

    /** Wall clock, replaced in tests. */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }
    /** Replaced in tests: whether the device (or the user) is locked right now. */
    @JvmStatic var deviceLocked: (Context) -> Boolean = {
        helium314.keyboard.compat.isDeviceLocked(it) || runCatching { helium314.keyboard.compat.isUserLocked(it) }.getOrDefault(false)
    }

    private val lock = Any()
    private var phrases: PhraseBook? = null
    private var ownWords: OwnWordBook? = null
    private var shortcuts: ShortcutBook? = null
    /**
     * Own words and remembered abbreviations for lock-free reads from the suggestion thread, lower
     * case; null until first needed, rebuilt only when one of them changes (review N1).
     */
    @Volatile private var ownLookup: Set<String>? = null
    @Volatile private var excluded: Set<String>? = null
    private var dirty = false
    private var lastSave = 0L
    /** Writes run on one background thread in order (review N1); [clear] makes older pending writes void. */
    private var saver: Executor = Executors.newSingleThreadExecutor { Thread(it, "agent-memory-save").apply { isDaemon = true } }
    private var generation = 0

    /** The words of the current sentence part the owner committed one after another in this field (surface forms). */
    private var run: List<String> = emptyList()
    private val tracker = ReplacementTracker()
    /** The last typed word the suggestion thread saw, and whether a main dictionary knows it. */
    @Volatile private var lastTyped: Pair<String, Boolean>? = null
    /** Recently committed words, lower case, that no main dictionary knew when typed (review M2). */
    private val unknownCommits = ArrayDeque<String>()
    /**
     * Review M3: an undone correction waits for the next commit in the field and counts only if the
     * typed word is still there. [countedAt] holds the uses counted in this field as "word@position",
     * so the same word at the same place counts once, however many paths report its undo (backspace,
     * the pick of stage 48, the field checkpoint of stage 49).
     */
    private var pendingUndone: String? = null
    private val countedAt = HashSet<String>()

    private fun today() = clock() / DAY_MILLIS
    private fun prefs(context: Context) = AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun lower(word: String) = word.lowercase(Locale.ROOT)

    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.MEMORY, false)
    fun snippetsEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.MEMORY_SNIPPETS, true)

    // ---- rules ----

    private fun isTextField(inputType: Int) = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        !InputTypeUtils.isAnyPasswordInputType(inputType)

    /** The keyboard's own fields without learning ("Meine Daten", P180), whatever the settings say. */
    private fun isNoLearningField(info: EditorInfo) =
        info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ||
            info.privateImeOptions?.split(',')?.any { it.trim() == AgentPreferences.NO_LEARNING_FIELD } == true

    /** No offers without settings, in incognito (also no-learning and password fields), while locked or in an excluded app. */
    fun isBlocked(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean {
        val info = editorInfo ?: return true
        if (isNoLearningField(info)) return true
        return isBlocked(context, settings, info.inputType, info.packageName)
    }

    /** As above for the field the settings were made for (the suggestion thread has no EditorInfo). */
    fun isBlocked(context: Context, settings: SettingsValues?): Boolean {
        val attributes = settings?.mInputAttributes ?: return true
        return isBlocked(context, settings, attributes.mInputType, attributes.mTargetApplicationPackageName)
    }

    private fun isBlocked(context: Context, settings: SettingsValues?, inputType: Int, packageName: String?): Boolean {
        if (settings == null || settings.mIncognitoModeEnabled || settings.mIsLocked) return true
        if (!isTextField(inputType)) return true
        return isExcluded(context, packageName)
    }

    /** Learning additionally needs the switch, no pause and the live lock state. */
    fun mayLearn(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean =
        isEnabled(context) && settings != null && !settings.mLearningPaused && !isBlocked(context, settings, editorInfo) &&
            !deviceLocked(context)

    // ---- loading and saving ----

    private fun loaded(context: Context) {
        if (phrases != null) return
        val stored = runCatching { prefs(context) }.getOrNull()
        val today = today()
        phrases = PhraseBook.parse(stored?.let { runCatching { it.getString(PHRASES, null) }.getOrNull() }, today)
        ownWords = OwnWordBook.parse(stored?.let { runCatching { it.getString(OWN_WORDS, null) }.getOrNull() }, today)
        shortcuts = ShortcutBook.parse(stored?.let { runCatching { it.getString(SHORTCUTS, null) }.getOrNull() })
        ownLookup = null
    }

    private fun <T> locked(context: Context, block: () -> T): T = synchronized(lock) { loaded(context); block() }

    /** Own words and remembered abbreviations, built when first asked after a change. Under [lock]. */
    private fun lookup(): Set<String> =
        ownLookup ?: (ownWords!!.words.mapTo(HashSet()) { lower(it) } + shortcuts!!.remembered).also { ownLookup = it }

    /** Something was learned; [words]: the own words or the remembered abbreviations changed. */
    private fun changed(context: Context, now: Boolean = false, words: Boolean = false) {
        dirty = true
        if (words) ownLookup = null
        if (now || clock() - lastSave >= SAVE_EVERY_MILLIS) save(context)
    }

    /** Under [lock]: the phrases are copied here and written out on [saver]. */
    private fun save(context: Context) {
        if (!dirty) return
        val p = phrases ?: return
        val copy = p.copy()
        val own = ownWords!!.encode()
        val short = shortcuts!!.encode()
        val written = generation
        dirty = false
        lastSave = clock()
        saver.execute {
            val text = copy.encode()
            synchronized(lock) {
                // "Alles vergessen" came in between: this older state is never written back
                if (written != generation) return@synchronized
                runCatching { prefs(context).edit { putString(PHRASES, text); putString(OWN_WORDS, own); putString(SHORTCUTS, short) } }
            }
        }
    }

    /** Writes what was learned (the field was left or the keyboard closes). */
    @JvmStatic fun flush(context: Context) = synchronized(lock) { if (phrases != null) save(context) }

    /** A new field starts, or the field ends: the run of words and the in-field replacement follow start again. */
    @JvmStatic fun onFieldChanged(context: Context) {
        synchronized(lock) {
            resetField()
            if (phrases != null) save(context)
        }
        MemoryChips.forget()
    }

    /** Under [lock]: nothing of the field carries over. */
    private fun resetField() {
        run = emptyList()
        tracker.reset()
        lastTyped = null
        unknownCommits.clear()
        pendingUndone = null
        countedAt.clear()
    }

    // ---- learning ----

    /**
     * Called by `InputLogic.performAdditionToUserHistoryDictionary` for every word the owner
     * committed that the engine would learn (no command, no generated text, not in incognito):
     * [word] as committed, [typed] the word as typed (null for a gesture or a resumed word), [before]
     * reads the text before the cursor ending with the word (only when learning is allowed, review
     * N1), [cursor] the cursor after it.
     */
    @JvmStatic fun onWordCommitted(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?, word: String,
                                   typed: String?, before: () -> CharSequence?, cursor: Int) {
        if (!mayLearn(context, settings, editorInfo)) {
            synchronized(lock) { resetField() }
            return
        }
        val text = before()?.toString()
        synchronized(lock) {
            if (text == null) { resetField(); return }
            loaded(context)
            val today = today()
            if (!text.endsWith(word)) { resetField(); return }
            var ownChanged = false
            // the text read starts at this position of the field (unknown cursor: positions within the text read)
            val offset = if (cursor >= 0) cursor - text.length else 0
            // review M3: an undone correction counts once the next word comes and the typed word is still
            // there (this commit may be the restored word itself), once per word and place
            pendingUndone?.let { undone ->
                pendingUndone = null
                val at = lastWordAt(text, undone)
                if (at >= 0 && countedAt.add(lower(undone) + '@' + (offset + at))) ownChanged = ownWords!!.noteUse(undone, today)
            }
            // phrases: the longest run of the owner's commits that the text before the cursor still ends with
            val tokens = PhraseText.currentSentence(text)
            val expected = run + word
            var same = 0
            while (same < tokens.size && same < expected.size &&
                PhraseText.key(tokens[tokens.size - 1 - same]) != null &&
                PhraseText.key(tokens[tokens.size - 1 - same]) == PhraseText.key(expected[expected.size - 1 - same])) same++
            run = tokens.takeLast(same).takeLast(PhraseBook.MAX_WORDS)
            phrases!!.learn(run, today)
            // own words: committed as typed although no main dictionary knows the word
            val seen = lastTyped
            val unknown = typed != null && seen != null && seen.first == typed && !seen.second
            if (unknown) {
                unknownCommits.addLast(lower(typed!!))
                while (unknownCommits.size > MAX_UNKNOWN) unknownCommits.removeFirst()
            }
            if (unknown && typed == word && countedAt.add(lower(word) + '@' + (offset + text.length - word.length)))
                ownChanged = ownWords!!.noteUse(word, today) || ownChanged
            while (countedAt.size > MAX_COUNTED) countedAt.remove(countedAt.first())
            // abbreviations: replaced in place by a plausible expansion, and no main dictionary knows the short form (review M2)
            if (cursor >= 0) tracker.onCommit(word, cursor, text)?.let { (short, expansion) ->
                if (lower(short) in unknownCommits) shortcuts!!.note(short, expansion)
            }
            changed(context, words = ownChanged)
        }
    }

    /** Where the last whole-word occurrence of [word] in [text] starts, or -1. */
    private fun lastWordAt(text: String, word: String): Int {
        var at = text.lastIndexOf(word)
        while (at >= 0) {
            val end = at + word.length
            val startOk = at == 0 || !text[at - 1].isLetterOrDigit()
            val endOk = end == text.length || !(text[end].isLetterOrDigit() || text[end] == '\'' || text[end] == '-')
            if (startOk && endOk) return at
            at = if (at == 0) -1 else text.lastIndexOf(word, at - 1)
        }
        return -1
    }

    /**
     * An automatic correction from [typed] was undone (backspace, a tap on the typed word, or changed
     * back later in the field): one use of it as an own word, counted at the next commit if the word
     * is still there (review M3), and only once even when two paths report the same undo.
     */
    @JvmStatic fun onCorrectionUndone(context: Context, typed: String, settings: SettingsValues?) {
        if (!isEnabled(context) || settings == null || settings.mLearningPaused || isBlocked(context, settings) || deviceLocked(context)) return
        if (!OwnWordBook.isCandidate(typed)) return
        synchronized(lock) {
            // a second report before the next commit is the same undo; one already counted is caught by its place
            pendingUndone = typed
        }
    }

    // ---- the engine asks (suggestion thread) ----

    /** True if [word] is an own word or a remembered abbreviation: no automatic correction of it. Lock-free once built. */
    @JvmStatic fun isOwnWord(context: Context?, word: String): Boolean {
        if (context == null || !isEnabled(context)) return false
        val lookup = ownLookup ?: locked(context) { lookup() }
        return lower(word) in lookup
    }

    /**
     * The suggestion thread built suggestions for [typed] (not empty) and knows whether a main
     * dictionary has it; remembered for the next commit.
     */
    @JvmStatic fun onTyped(typed: String, knownToMainDictionary: Boolean) { lastTyped = typed to knownToMainDictionary }

    /** An own word that [typed] starts, for the strip; null if none, blocked, locked (review N4) or off. */
    @JvmStatic fun ownCompletion(context: Context?, settings: SettingsValues?, typed: String): String? {
        if (context == null || !isEnabled(context) || isBlocked(context, settings)) return null
        if ((ownLookup ?: locked(context) { lookup() }).isEmpty()) return null
        val completion = locked(context) { ownWords!!.completions(typed).firstOrNull() }
        return completion?.takeIf { !deviceLocked(context) }
    }

    /** The memory's next word after the words the engine knows ([ngramContext], up to three), or null. */
    @JvmStatic fun nextWord(context: Context?, settings: SettingsValues?, ngramContext: NgramContext): String? {
        if (context == null || !isEnabled(context) || isBlocked(context, settings)) return null
        val words = ArrayList<String>()
        for (n in 1..ngramContext.prevWordCount) {
            if (ngramContext.isNthPrevWordBeginningOfSentence(n)) break
            val word = ngramContext.getNthPrevWord(n)?.toString() ?: break
            words.add(PhraseText.key(word) ?: break)
        }
        if (words.size < 2) return null
        words.reverse()
        val next = locked(context) { phrases!!.predictKeys(words, today())?.next }
        // review N4: the live lock state, asked only when there is something to show
        return next?.takeIf { !deviceLocked(context) }
    }

    /** The prediction for the text before the cursor (ending with a space); null when blocked or off. */
    fun predict(context: Context, before: CharSequence): PhrasePrediction? {
        if (!isEnabled(context)) return null
        return locked(context) { phrases!!.predict(before, today()) }
    }

    /** The owner tapped the rest of [key]: it counts as used once more. */
    fun accepted(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?, key: String) {
        if (!mayLearn(context, settings, editorInfo)) return
        locked(context) {
            val phrase = phrases!!.phrases(today()).firstOrNull { it.key == key } ?: return@locked
            phrases!!.reinforce(key, phrase.text, today(), 1.0)
            changed(context)
        }
    }

    // ---- abbreviations ----

    fun dueOffer(context: Context): ShortcutBook.Offer? = if (!isEnabled(context)) null else locked(context) { shortcuts!!.due() }

    fun markOffer(context: Context, offer: ShortcutBook.Offer, state: ShortcutBook.State) =
        locked(context) { shortcuts!!.mark(offer.short, offer.expansion, state); changed(context, now = true, words = true) }

    fun offers(context: Context): List<ShortcutBook.Offer> = locked(context) { shortcuts!!.offers() }

    fun removeOffer(context: Context, offer: ShortcutBook.Offer) =
        locked(context) { shortcuts!!.remove(offer.short, offer.expansion); changed(context, now = true, words = true) }

    /**
     * The tap on an offer: the abbreviation is remembered here, never written to the personal
     * dictionary (review M1: the autocorrection could apply a dictionary shortcut by itself at the
     * space). After the short form the strip offers the expansion ([expansionOf]); only a tap uses it.
     */
    fun acceptOffer(context: Context, offer: ShortcutBook.Offer): Boolean {
        markOffer(context, offer, ShortcutBook.State.CREATED)
        return true
    }

    /** The expansion of the remembered abbreviation [short], or null (off or none). */
    fun expansionOf(context: Context, short: String): String? =
        if (!isEnabled(context)) null else locked(context) { shortcuts!!.expansionOf(short) }

    // ---- the page ----

    fun phrases(context: Context, minWords: Int = 3): List<Phrase> = locked(context) { phrases!!.phrases(today(), minWords) }

    fun deletePhrase(context: Context, key: String) = locked(context) { phrases!!.delete(key).also { changed(context, now = true) } }

    fun pinPhrase(context: Context, key: String, pinned: Boolean) =
        locked(context) { phrases!!.pin(key, pinned, today()).also { changed(context, now = true) } }

    fun ownWords(context: Context): List<String> = locked(context) { ownWords!!.words.reversed() }

    fun removeOwnWord(context: Context, word: String) =
        locked(context) { ownWords!!.remove(word).also { changed(context, now = true, words = true) } }

    /**
     * Forgets everything the memory learned (phrases, own words, abbreviations, what this field
     * held); the excluded apps stay. Called by "Alles vergessen", by "Gelernte Wörter löschen" and when
     * "Personalisierte Vorschläge" is switched off (review H1). Writes still waiting are void.
     */
    fun clear(context: Context) {
        synchronized(lock) {
            loaded(context)
            phrases!!.clear(); ownWords!!.clear(); shortcuts!!.clear()
            resetField()
            generation++
            ownLookup = emptySet()
            runCatching { prefs(context).edit(commit = true) { remove(PHRASES); remove(OWN_WORDS); remove(SHORTCUTS) } }
            dirty = false
        }
        MemoryChips.forget()
    }

    // ---- excluded apps ----

    fun excludedApps(context: Context): Set<String> =
        excluded ?: runCatching { prefs(context).getStringSet(EXCLUDED, emptySet()).orEmpty().toSet() }.getOrDefault(emptySet())
            .also { excluded = it }

    fun isExcluded(context: Context, packageName: String?): Boolean = packageName != null && packageName in excludedApps(context)

    fun setExcluded(context: Context, packageName: String, exclude: Boolean): Boolean {
        if (!dev.agentkeyboard.core.SnippetApps.isValidPackage(packageName)) return false
        val current = excludedApps(context).toMutableSet()
        if (exclude) { if (current.size >= MAX_EXCLUDED && packageName !in current) return false; current.add(packageName) }
        else current.remove(packageName)
        runCatching { prefs(context).edit { putStringSet(EXCLUDED, current) } }
        excluded = current
        return true
    }

    /** What is stored, for the tests: readable text, no hashes. */
    internal fun stored(context: Context, key: String = PHRASES): String? = prefs(context).getString(key, null)

    /** Tests only: counted uses of words that are not own yet. */
    internal fun candidates(context: Context): List<Pair<String, Int>> = locked(context) { ownWords!!.candidates }

    /** Tests only: forget what is held in memory so the next access reads storage again; writes run at once. */
    internal fun resetForTest() = synchronized(lock) {
        phrases = null; ownWords = null; shortcuts = null; ownLookup = null; excluded = null
        resetField(); dirty = false; lastSave = 0L
        saver = Executor { it.run() }
    }
}
