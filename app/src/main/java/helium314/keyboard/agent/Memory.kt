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

/**
 * Stage 46 "Gedächtnis": the glue between the engine and the pure memory of agent-core
 * (`dev.agentkeyboard.core.memory`). Learns only from words the owner committed (the engine's
 * user-history hook, [onWordCommitted]) and from undone corrections ([onCorrectionUndone]).
 *
 * Opt-in ([AgentPreferences.MEMORY], default off). Nothing is learned in password fields, incognito
 * (which covers no-learning fields), while learning is paused, while the device is locked or in an
 * excluded app; nothing is shown in such fields either (pausing only stops learning). Stored in the
 * device-protected storage like the other learned data (`agent_phrase_memory`), as readable text the
 * owner sees and deletes on the page "Gedächtnis". Nothing is logged.
 *
 * The memory never changes text: the strip offers ([MemoryChips]) and only a tap appends after the
 * cursor. Own words only keep the autocorrection off themselves; it stays a single-word correction.
 */
object Memory {
    const val FILE = "agent_phrase_memory"
    internal const val PHRASES = "phrases"
    internal const val OWN_WORDS = "own_words"
    private const val SHORTCUTS = "shortcuts"
    private const val EXCLUDED = "excluded_apps"
    const val MAX_EXCLUDED = 50
    /** Text before the cursor read after a commit: the longest phrase plus room for commas. */
    const val LOOKBACK = PhraseBook.MAX_TEXT + 8
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    /** Learned data is written at most this often while typing, and when the field is left. */
    private const val SAVE_EVERY_MILLIS = 30_000L
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
    /** Own words for lock-free reads from the suggestion thread, lower case. */
    @Volatile private var ownLookup: Set<String>? = null
    @Volatile private var excluded: Set<String>? = null
    private var dirty = false
    private var lastSave = 0L

    /** The words of the current sentence part the owner committed one after another in this field (surface forms). */
    private var run: List<String> = emptyList()
    private val tracker = ReplacementTracker()
    /** The last typed word the suggestion thread saw, and whether a main dictionary knows it. */
    @Volatile private var lastTyped: Pair<String, Boolean>? = null

    private fun today() = clock() / DAY_MILLIS
    private fun prefs(context: Context) = AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.MEMORY, false)
    fun snippetsEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.MEMORY_SNIPPETS, true)

    // ---- rules ----

    private fun isTextField(inputType: Int) = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        !InputTypeUtils.isAnyPasswordInputType(inputType)

    /** No offers without settings, in incognito (also no-learning and password fields), while locked or in an excluded app. */
    fun isBlocked(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean {
        val info = editorInfo ?: return true
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true
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
        ownLookup = ownWords!!.words.map { it.lowercase() }.toSet()
    }

    private fun <T> locked(context: Context, block: () -> T): T = synchronized(lock) { loaded(context); block() }

    private fun changed(context: Context, now: Boolean = false) {
        dirty = true
        ownLookup = ownWords!!.words.map { it.lowercase() }.toSet()
        if (now || clock() - lastSave >= SAVE_EVERY_MILLIS) save(context)
    }

    private fun save(context: Context) {
        if (!dirty) return
        val p = phrases ?: return
        runCatching {
            prefs(context).edit {
                putString(PHRASES, p.encode())
                putString(OWN_WORDS, ownWords!!.encode())
                putString(SHORTCUTS, shortcuts!!.encode())
            }
        }
        dirty = false
        lastSave = clock()
    }

    /** Writes what was learned (the field was left or the keyboard closes). */
    @JvmStatic fun flush(context: Context) = synchronized(lock) { if (phrases != null) save(context) }

    /** A new field starts, or the field ends: the run of words and the in-field replacement follow start again. */
    @JvmStatic fun onFieldChanged(context: Context) {
        synchronized(lock) {
            run = emptyList()
            tracker.reset()
            lastTyped = null
            if (phrases != null) save(context)
        }
        MemoryChips.forget()
    }

    // ---- learning ----

    /**
     * Called by `InputLogic.performAdditionToUserHistoryDictionary` for every word the owner
     * committed that the engine would learn (no command, no generated text, not in incognito):
     * [word] as committed, [typed] the word as typed (null for a gesture or a resumed word), [before]
     * the text before the cursor ending with the word, [cursor] the cursor after it.
     */
    @JvmStatic fun onWordCommitted(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?, word: String,
                                   typed: String?, before: CharSequence?, cursor: Int) {
        if (!mayLearn(context, settings, editorInfo)) {
            synchronized(lock) { run = emptyList(); tracker.reset() }
            return
        }
        val text = before?.toString() ?: return
        synchronized(lock) {
            loaded(context)
            val today = today()
            if (!text.endsWith(word)) { run = emptyList(); tracker.reset(); return }
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
            if (typed != null && typed == word && seen != null && seen.first == typed && !seen.second) ownWords!!.noteUse(word, today)
            // abbreviations: replaced in place by a plausible expansion
            if (cursor >= 0) tracker.onCommit(word, cursor, text)?.let { (short, expansion) -> shortcuts!!.note(short, expansion) }
            changed(context)
        }
    }

    /** An automatic correction from [typed] was undone (backspace or a tap on the typed word): one use of it as an own word. */
    @JvmStatic fun onCorrectionUndone(context: Context, typed: String, settings: SettingsValues?) {
        if (!isEnabled(context) || settings == null || settings.mLearningPaused || isBlocked(context, settings) || deviceLocked(context)) return
        locked(context) {
            ownWords!!.noteUse(typed, today())
            changed(context)
        }
    }

    // ---- the engine asks (suggestion thread) ----

    /** True if [word] is an own word: no automatic correction of it. Lock-free. */
    @JvmStatic fun isOwnWord(context: Context?, word: String): Boolean {
        if (context == null || !isEnabled(context)) return false
        val lookup = ownLookup ?: synchronized(lock) { loaded(context); ownLookup } ?: return false
        return word.lowercase() in lookup
    }

    /**
     * The suggestion thread built suggestions for [typed] (not empty) and knows whether a main
     * dictionary has it; remembered for the next commit.
     */
    @JvmStatic fun onTyped(typed: String, knownToMainDictionary: Boolean) { lastTyped = typed to knownToMainDictionary }

    /** An own word that [typed] starts, for the strip; null if none, blocked or off. */
    @JvmStatic fun ownCompletion(context: Context?, settings: SettingsValues?, typed: String): String? {
        if (context == null || !isEnabled(context) || isBlocked(context, settings)) return null
        if ((ownLookup ?: return null).isEmpty()) return null
        return locked(context) { ownWords!!.completions(typed).firstOrNull() }
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
        return locked(context) { phrases!!.predictKeys(words, today())?.next }
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

    // ---- abbreviation offers ----

    fun dueOffer(context: Context): ShortcutBook.Offer? = if (!isEnabled(context)) null else locked(context) { shortcuts!!.due() }

    fun markOffer(context: Context, offer: ShortcutBook.Offer, state: ShortcutBook.State) =
        locked(context) { shortcuts!!.mark(offer.short, offer.expansion, state); changed(context, now = true) }

    fun offers(context: Context): List<ShortcutBook.Offer> = locked(context) { shortcuts!!.offers() }

    fun removeOffer(context: Context, offer: ShortcutBook.Offer) =
        locked(context) { shortcuts!!.remove(offer.short, offer.expansion); changed(context, now = true) }

    /** Creates the shortcut in the personal dictionary; replaced in tests. */
    @JvmStatic var createShortcut: (Context, String, String) -> Boolean = { context, short, expansion ->
        runCatching { android.provider.UserDictionary.Words.addWord(context, expansion, 250, short, null) }.isSuccess
    }

    /** The tap on an offer: the shortcut is created, the offer is done. */
    fun acceptOffer(context: Context, offer: ShortcutBook.Offer): Boolean {
        if (!createShortcut(context, offer.short, offer.expansion)) return false
        markOffer(context, offer, ShortcutBook.State.CREATED)
        return true
    }

    // ---- the page ----

    fun phrases(context: Context, minWords: Int = 3): List<Phrase> = locked(context) { phrases!!.phrases(today(), minWords) }

    fun deletePhrase(context: Context, key: String) = locked(context) { phrases!!.delete(key).also { changed(context, now = true) } }

    fun pinPhrase(context: Context, key: String, pinned: Boolean) =
        locked(context) { phrases!!.pin(key, pinned, today()).also { changed(context, now = true) } }

    fun ownWords(context: Context): List<String> = locked(context) { ownWords!!.words.reversed() }

    fun removeOwnWord(context: Context, word: String) = locked(context) { ownWords!!.remove(word).also { changed(context, now = true) } }

    /** Forgets everything the memory learned (phrases, own words, offers); the excluded apps stay. */
    fun clear(context: Context) {
        synchronized(lock) {
            loaded(context)
            phrases!!.clear(); ownWords!!.clear(); shortcuts!!.clear()
            run = emptyList(); tracker.reset()
            dirty = true
            ownLookup = emptySet()
            runCatching { prefs(context).edit(commit = true) { remove(PHRASES); remove(OWN_WORDS); remove(SHORTCUTS) } }
            dirty = false
        }
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

    /** Tests only: forget what is held in memory so the next access reads storage again. */
    internal fun resetForTest() = synchronized(lock) {
        phrases = null; ownWords = null; shortcuts = null; ownLookup = null; excluded = null
        run = emptyList(); tracker.reset(); lastTyped = null; dirty = false; lastSave = 0L
    }
}
