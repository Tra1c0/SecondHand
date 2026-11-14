// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.CorrectionRules
import dev.agentkeyboard.core.KeyAdjacency
import dev.agentkeyboard.core.KeyBox
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.SettingsValues
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage 49: corrections the user makes later anywhere in the field, not only right after typing a
 * word: after more words, after backspacing over spaces, after a picked suggestion, after tapping
 * back into the word. One instance per [helium314.keyboard.latin.inputlogic.InputLogic].
 *
 * At every checkpoint (a committed word, a cursor move by the user) the text around the cursor is
 * compared with the state at the previous checkpoint ([CorrectionRules.diff]). If exactly one word
 * changed, [CorrectionRules.classify] decides typo or revision; typos become candidates in
 * [FieldCorrectionMemory]. An automatic correction of this field that the user changes back
 * ("X" -> "Y" -> "X") is remembered at once as "do not correct X to Y" ([RejectedCorrections]).
 *
 * The field text is held only in memory, only while learning is allowed (opt-in switch, not paused,
 * no incognito, no password or no-learning field, not locked, no generated text in this session),
 * dropped when input starts in a field and never stored or logged. A selection, a paste or inserted
 * text drops the reference state, so select-and-overtype and pasting are never learned.
 */
class FieldCorrectionWatcher {
    private class Snapshot(val offset: Int, val text: String, val endComplete: Boolean)
    private class AutoCorrection(val typed: String, val corrected: String)

    private var reference: Snapshot? = null
    private val autoCorrections = ArrayDeque<AutoCorrection>()
    private var session = nextSession()
    // review N1: the field positions the user's own input (letters, delete, a picked suggestion)
    // touched since the reference, as one range: without a cursor move (a checkpoint) the input
    // stays at one place. Changes outside it were made by the app.
    private var ownFrom = -1
    private var ownTo = -1
    // review N2: the letters of the last alphabet keyboard, also while symbols or the numpad show.
    // Second review: only the key positions are kept; the keyboard (its id holds the editor info with
    // the field's initial text) only weakly, to see a language or layout change.
    private var adjacencySource: WeakReference<Keyboard>? = null
    private var layoutAdjacency: KeyAdjacency? = null

    /** Input starts in a field: everything of the previous field is forgotten. */
    fun start() {
        reference = null
        autoCorrections.clear()
        session = nextSession()
        clearOwnEdits()
        adjacencySource = null
        layoutAdjacency = null
    }

    /**
     * Text was pasted or inserted, text is typed over a selection, or undo/redo let the app change the
     * text: this change is not learned.
     */
    fun untrusted() {
        reference = null
        clearOwnEdits()
    }

    /**
     * Review N1: the user's own input (a character key, delete, a picked suggestion) at field position
     * [position], before it is applied. Only changes of a word touching such positions count.
     */
    fun ownEdit(position: Int) {
        if (reference == null || position < 0) return
        if (ownFrom < 0) { ownFrom = position; ownTo = position; return }
        ownFrom = minOf(ownFrom, position)
        ownTo = maxOf(ownTo, position)
    }

    private fun clearOwnEdits() { ownFrom = -1; ownTo = -1 }

    /** [typed] was automatically replaced by [corrected] in this field. */
    fun autoCorrected(typed: String, corrected: String, settings: SettingsValues) {
        if (!mayObserve(settings, false) || typed.isEmpty() || typed == corrected) return
        autoCorrections.addLast(AutoCorrection(typed, corrected))
        while (autoCorrections.size > MAX_AUTO_CORRECTIONS) autoCorrections.removeFirst()
    }

    /**
     * Compare the field with the previous checkpoint. [atCommit]: a word was just committed and the
     * cursor stands right after it; if it is at the end of the field, the text that followed the word
     * before may have been deleted to fix it. [committedFrom] -> [committedTo]: the keyboard's own
     * automatic correction of the word just committed, never the user's change (review M1).
     * [composing]: a word is being composed; then nothing is compared and no reference is taken, a
     * half-typed word is no state of the field (review M1).
     */
    fun checkpoint(context: Context, connection: RichInputConnection, settings: SettingsValues,
                   dictionary: DictionaryFacilitator, historySuppressed: Boolean, atCommit: Boolean,
                   composing: Boolean, committedFrom: String?, committedTo: String?) {
        if (!mayObserve(settings, historySuppressed)) {
            reference = null
            autoCorrections.clear()
            clearOwnEdits()
            return
        }
        // second review: the keyboard shown now, so a language or layout change is seen (cheap when unchanged)
        noteKeyboard(runCatching { helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().keyboard }.getOrNull())
        if (composing) return // the last reference stays, the next checkpoint compares
        val cursor = connection.expectedSelectionStart
        // review M2: a slow app gets no extra reads; nothing is learned there
        if (cursor < 0 || connection.hasSelection() || connection.hasSlowInputConnection()) { untrusted(); return }
        val before = connection.getTextBeforeCursor(LIMIT_BEFORE, 0)?.toString()
        val after = connection.getTextAfterCursor(LIMIT_AFTER, 0)?.toString()
        if (before == null || after == null) { untrusted(); return }
        val now = Snapshot(cursor - before.length, before + after, after.length < LIMIT_AFTER)
        val old = reference
        val ownRange = if (ownFrom < 0) null else ownFrom..ownTo
        reference = now
        clearOwnEdits()
        if (now.text.isEmpty()) {
            // review N3: the field was emptied (a messenger after sending): the next message is another place
            if (old == null || old.text.isNotEmpty()) {
                session = nextSession()
                autoCorrections.clear()
            }
            return
        }
        if (old == null) return
        val from = maxOf(old.offset, now.offset)
        if (from - old.offset > old.text.length || from - now.offset > now.text.length) return
        val diff = CorrectionRules.diff(old.text.substring(from - old.offset), now.text.substring(from - now.offset),
            old.endComplete, now.endComplete, tailMayBeDeleted = atCommit && after.isEmpty())
        if (diff !is CorrectionRules.Diff.Single) return
        val change = diff.change
        // review N1: only a word the user's own input touched; the app's autocorrect, autofill or a
        // collaborator change words without that
        val wordStart = from + change.start
        if (ownRange == null || ownRange.first > wordStart + change.new.length || ownRange.last < wordStart) return
        // review M1: the keyboard's own automatic correction of the word just committed
        if (committedFrom != null && committedTo != null && change.old.equals(committedFrom, ignoreCase = true)
            && change.new == committedTo) return
        // X -> Y automatically, now Y -> X by the user: do not correct X to Y any more, from the first time
        val reverted = autoCorrections.lastOrNull {
            it.corrected.equals(change.old, ignoreCase = true) && it.typed.equals(change.new, ignoreCase = true)
        }
        if (reverted != null) {
            RejectedCorrections.remember(context, reverted.typed, reverted.corrected, settings)
            TypoMemory.forget(context, reverted.typed, reverted.corrected)
            return
        }
        if (!TypoMemory.mayLearn(settings)) return
        // review M1: finishing a word ("Bahnho" -> "Bahnhof", also "Mad" -> "Mädchen") is a completion
        if (isCompletion(change.old, change.new)) return
        fun real(word: String) = dictionary.isValidSpellingWord(word)
            || dictionary.isValidSpellingWord(word.lowercase(settings.mLocale ?: Locale.ROOT))
        val kind = CorrectionRules.classify(change.old, change.new, real(change.old), real(change.new), adjacency(null, settings))
        if (kind != CorrectionRules.Kind.TYPO) return
        // a capital that only comes from the sentence start is not kept ("Ihc" -> "Ich" means "ich")
        val lower = change.new.lowercase(settings.mLocale ?: Locale.ROOT)
        val form = if (change.new.first().isUpperCase() && change.old.first().isUpperCase() && lower != change.new && real(lower))
            lower else change.new
        FieldCorrectionMemory.observed(context, change.old, form, session, wordStart, settings)
    }

    /**
     * The neighbouring keys of the last alphabet keyboard (review N2): the symbols keyboard or the
     * numpad shown at a checkpoint keeps the letters of the layout typed on. Without any (tests, a
     * hardware keyboard) the phone layout of the language.
     */
    internal fun adjacency(keyboard: Keyboard?, settings: SettingsValues): KeyAdjacency {
        noteKeyboard(keyboard)
        layoutAdjacency?.let { return it }
        return if (settings.mLocale?.language == "de") KeyAdjacency.QWERTZ else KeyAdjacency.QWERTY
    }

    /** Takes the key positions of [keyboard] when it is another alphabet keyboard than the last one. */
    private fun noteKeyboard(keyboard: Keyboard?) {
        if (keyboard == null || keyboard === adjacencySource?.get() || keyboard.mId?.element?.isAlphabet != true) return
        val keys = keyboard.sortedKeys.filter { it.code > 0 && Character.isLetter(it.code) && it.code < 0x10000 }
            .map { KeyBox(it.code.toChar(), it.x + it.width / 2f, it.y + it.height / 2f, it.width.toFloat(), it.height.toFloat()) }
        adjacencySource = WeakReference(keyboard)
        layoutAdjacency = if (keys.size >= MIN_LAYOUT_KEYS) KeyAdjacency.fromKeys(keys) else null
    }

    companion object {
        /**
         * Characters before the cursor that are compared: at most what the connection keeps cached
         * (review M2), so a checkpoint usually reads nothing from the app there.
         */
        const val LIMIT_BEFORE = Constants.EDITOR_CONTENTS_CACHE_SIZE
        /** Characters after the cursor that are compared (one read from the app, review M2). */
        const val LIMIT_AFTER = 300
        const val MAX_AUTO_CORRECTIONS = 200
        private const val MIN_LAYOUT_KEYS = 10
        private val sessions = AtomicLong(System.currentTimeMillis())
        private fun nextSession() = sessions.incrementAndGet()

        private fun plain(word: String) =
            Normalizer.normalize(word.lowercase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD)
                .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

        /** [new] goes on where [old] ends, accents ignored: a finished word, no typo fix (as in [TypoMemory.confirm]). */
        /**
         * The typed word when the commit was the keyboard's own automatic correction ([decided]) that
         * really changed it, exact case (second review: changing an automatic "Ich" back to "ich" is
         * no automatic correction of the keyboard, though the letters are the same); else null.
         */
        @JvmStatic
        fun keyboardCorrectionFrom(decided: Boolean, typed: String, chosen: String): String? =
            if (decided && typed != chosen) typed else null

        @JvmStatic
        fun isCompletion(old: String, new: String): Boolean {
            val a = plain(old)
            val b = plain(new)
            return b.length > a.length && b.startsWith(a)
        }

        @JvmStatic
        fun mayObserve(settings: SettingsValues, historySuppressed: Boolean): Boolean =
            !historySuppressed && (TypoMemory.mayLearn(settings) || RejectedCorrections.mayRemember(settings))
    }
}

/**
 * Stage 49: typo corrections the user made later in a field. A detected correction is first a
 * candidate; it is learned when it is seen a second time at another place (another field, or another
 * position in the field). Learned pairs feed the automatic correction through [TypoMemory.meantWord],
 * only for typed forms that are no dictionary word. Opt-in like [TypoMemory], only on this device,
 * at most [MAX_CANDIDATES] candidates and [MAX_LEARNED] learned pairs, oldest dropped first, never logged.
 */
object FieldCorrectionMemory {
    const val FILE = "agent_field_corrections"
    private const val KEY = "data"
    const val MAX_CANDIDATES = 200
    const val MAX_LEARNED = 500
    private const val MAX_WORD = 48

    /** [typed] as it stood in the field (lower case), [meant] in the spelling to use. */
    data class Entry(val typed: String, val meant: String, val learned: Boolean, val seen: Long)

    private class Candidate(val meant: String, val session: Long, val position: Int, val seen: Long)
    private class Learned(val meant: String, val seen: Long)

    @Volatile private var candidates: LinkedHashMap<String, Candidate>? = null
    @Volatile private var learned: LinkedHashMap<String, Learned> = LinkedHashMap()
    private val lock = Any()
    @JvmField var clock: () -> Long = System::currentTimeMillis

    private fun key(word: String) = word.lowercase(Locale.ROOT)

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun loaded(context: Context): LinkedHashMap<String, Candidate> {
        candidates?.let { return it }
        synchronized(lock) {
            candidates?.let { return it }
            val c = LinkedHashMap<String, Candidate>()
            val l = LinkedHashMap<String, Learned>()
            runCatching {
                val root = JSONObject(prefs(context).getString(KEY, "{}")!!)
                root.optJSONArray("candidates")?.let { a ->
                    for (i in 0 until a.length()) a.getJSONObject(i).let {
                        c[it.getString("typed")] = Candidate(it.getString("meant"), it.getLong("session"), it.getInt("position"), it.getLong("seen"))
                    }
                }
                root.optJSONArray("learned")?.let { a ->
                    for (i in 0 until a.length()) a.getJSONObject(i).let { l[it.getString("typed")] = Learned(it.getString("meant"), it.getLong("seen")) }
                }
            }
            learned = l
            candidates = c
            return c
        }
    }

    private fun save(context: Context) {
        val c = JSONArray()
        candidates.orEmpty().forEach { (typed, v) ->
            c.put(JSONObject().put("typed", typed).put("meant", v.meant).put("session", v.session).put("position", v.position).put("seen", v.seen))
        }
        val l = JSONArray()
        learned.forEach { (typed, v) -> l.put(JSONObject().put("typed", typed).put("meant", v.meant).put("seen", v.seen)) }
        prefs(context).edit().putString(KEY, JSONObject().put("candidates", c).put("learned", l).toString()).apply()
    }

    /**
     * A typo correction [typed] -> [meant] was seen in field [session] at [position]. Returns true when
     * the pair is (now) learned. The same place again keeps it a candidate.
     */
    @JvmStatic
    fun observed(context: Context, typed: String, meant: String, session: Long, position: Int, settings: SettingsValues): Boolean {
        if (!TypoMemory.mayLearn(settings)) return false
        if (typed.length < 2 || typed.length > MAX_WORD || meant.isEmpty() || meant.length > MAX_WORD) return false
        val k = key(typed)
        synchronized(lock) {
            val c = loaded(context)
            val now = clock()
            val known = learned[k]
            if (known != null && known.meant.equals(meant, ignoreCase = true)) {
                learn(k, known.meant, now); save(context); return true
            }
            val first = c[k]
            if (first != null && first.meant.equals(meant, ignoreCase = true) && (first.session != session || first.position != position)) {
                c.remove(k)
                learn(k, meant, now)
                save(context)
                return true
            }
            if (first != null && first.meant.equals(meant, ignoreCase = true)) return false // the same place again
            c.remove(k)
            c[k] = Candidate(meant, session, position, now)
            while (c.size > MAX_CANDIDATES) c.remove(c.keys.first())
            save(context)
            return false
        }
    }

    private fun learn(k: String, meant: String, now: Long) {
        val l = LinkedHashMap(learned)
        l.remove(k)
        l[k] = Learned(meant, now)
        while (l.size > MAX_LEARNED) l.remove(l.keys.first())
        learned = l
    }

    /** The learned meaning of [typed], or null. Read from the suggestion thread. */
    @JvmStatic
    fun learnedFor(context: Context, typed: String): String? {
        loaded(context)
        return learned[key(typed)]?.meant
    }

    /** An automatic correction from a learned pair was undone, or the user deleted it: dropped. */
    @JvmStatic
    fun forget(context: Context, typed: String, meant: String) {
        synchronized(lock) {
            val c = loaded(context)
            val k = key(typed)
            var changed = false
            if (learned[k]?.meant?.equals(meant, ignoreCase = true) == true) { learned = LinkedHashMap(learned).apply { remove(k) }; changed = true }
            if (c[k]?.meant?.equals(meant, ignoreCase = true) == true) { c.remove(k); changed = true }
            if (changed) save(context)
        }
    }

    /** For the page "Was ich mir gemerkt habe": candidates and learned pairs, most recent first. */
    fun all(context: Context): List<Entry> = synchronized(lock) {
        val c = loaded(context)
        (c.map { Entry(it.key, it.value.meant, false, it.value.seen) } + learned.map { Entry(it.key, it.value.meant, true, it.value.seen) })
            .sortedByDescending { it.seen }
    }

    /** The user adopts a candidate on the page: learned without a second occurrence. */
    fun adopt(context: Context, entry: Entry) {
        synchronized(lock) {
            val c = loaded(context)
            val candidate = c.remove(entry.typed) ?: return
            learn(entry.typed, candidate.meant, clock())
            save(context)
        }
    }

    fun delete(context: Context, entry: Entry) = forget(context, entry.typed, entry.meant)

    fun clear(context: Context) {
        synchronized(lock) {
            check(prefs(context).edit().clear().commit())
            candidates = LinkedHashMap()
            learned = LinkedHashMap()
        }
    }

    internal fun resetForTest() = synchronized(lock) { candidates = null; learned = LinkedHashMap() }
}
