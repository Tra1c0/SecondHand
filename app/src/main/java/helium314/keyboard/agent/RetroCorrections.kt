// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.ApplyOutcome
import dev.agentkeyboard.core.sentence.GermanNouns
import dev.agentkeyboard.core.sentence.GermanVerbs
import dev.agentkeyboard.core.sentence.RetroCheck
import dev.agentkeyboard.core.sentence.SentenceContext
import dev.agentkeyboard.core.sentence.SentenceLexicon
import helium314.keyboard.latin.DictionaryFacilitatorImpl
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stages 104/105: corrections of the sentence after a word ended (docs/NACHKORREKTUR-KONZEPT.md). One per [InputLogic].
 *
 * At the end of a key that ends a word ([afterKey], main thread, inside the key's batch edit) the text before the cursor is
 * copied from the input connection's cache and checked ([RetroCheck], pure, well under a millisecond). The change is applied
 * in the same batch only if nothing is composed or selected, no gesture is pending, no private text is touched and the editor
 * still shows the old text there ([InputLogic.applyRetroEdit] reads it again). Otherwise nothing happens and the next word end
 * checks again. The edit is one unit: Backspace or the keyboard's Undo right after it
 * ([revert]) restores the old text and counts as a rejection of every changed pair ([RejectedCorrections], and in this field
 * for good). A pair the keyboard set before at the same place that is back to its old form (the owner changed it back by
 * hand) also counts as a rejection.
 *
 * Never in password fields, without autocorrection, in incognito or no-learning fields, on the locked device, in links or
 * e-mail addresses; offline. No text is logged; the counters are numbers only.
 */
class RetroCorrections(private val logic: InputLogic) {
    private class Undo(val cursorAfter: Int, val original: String, val replacement: String, val pairs: List<Pair<String, String>>)

    private var undo: Undo? = null
    /** Pairs rejected in this field (lower case "old\u0000new"); main thread writes, the check reads a copy. */
    private val rejectedHere = HashSet<String>()
    /** Pairs the keyboard set in this field: field position of the changed word -> "old\u0000new". */
    private val appliedHere = HashMap<Int, String>()
    @Volatile private var preloading = false
    /**
     * Main dictionary frequencies already asked (language + word -> frequency), main thread only. Each word end checks the
     * whole sentence again, so nearly every word was asked at the word end before; a lookup crosses into native code.
     * Dropped when the dictionaries change (another facilitator object).
     */
    private val frequencies = object : LinkedHashMap<String, Int>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > MAX_CACHED_WORDS
    }
    private var frequenciesOf: Any? = null

    /** A new field (or the same one restarted): forget everything of the old one. */
    fun fieldStarted() {
        undo = null
        rejectedHere.clear()
        appliedHere.clear()
    }

    /** The user moved the cursor (InputLogic.onUpdateSelection, not the keyboard's own change): no take-back any more. */
    fun cursorMoved() { undo = null }

    /** Whether an edit could be taken back now (for tests and the diagnosis). */
    fun canRevert(): Boolean = undo != null

    /**
     * Backspace or Undo right after an edit (inside the key's handling in [InputLogic]): restores the old text and returns
     * true, or returns false when there is nothing to take back here (the key then works as usual).
     */
    fun revert(ime: LatinIME, settings: SettingsValues): Boolean {
        val u = undo ?: return false
        undo = null
        if (logic.mConnection.expectedSelectionStart != u.cursorAfter || logic.mConnection.hasSelection()) return false
        val outcome = logic.applyRetroEdit(u.replacement, u.original, u.cursorAfter)
        if (outcome != ApplyOutcome.SUBMITTED) return false
        for ((old, new) in u.pairs) {
            rejectedHere.add(key(old, new))
            RejectedCorrections.remember(ime, old, new, settings)
        }
        undone.incrementAndGet()
        return true
    }

    /**
     * At the end of every key's handling in [InputLogic.onCodeInput], inside its batch edit (main thread): after a key that
     * ends a word the check runs at once and its change goes into the same batch as the key. The editor then reports one
     * selection change for both, so its report never reaches the keyboard after the next key (emulator run 45794233933:
     * a late report of a shorter text made "eein"). The check costs well under a millisecond; the lexicon is read on the
     * suggestion thread the first time, until then nothing is checked.
     */
    fun afterKey(ime: LatinIME, codePoint: Int, keyCode: Int, settings: SettingsValues) {
        // a key other than the ones that take an edit back ends the chance to take it back
        if (keyCode != KEY_DELETE && keyCode != KEY_UNDO) undo = null
        if (!endsWord(codePoint)) return
        val repair = settings.mRetroRepair
        val punctuate = settings.mAutoPunctuation
        if (!repair && !punctuate || !eligible(ime, settings)) return
        if (!logic.mayCheckRetro()) return
        // Enter that sends or goes on (an editor action) put no line break into this text: nothing to check
        if (codePoint == Constants.CODE_ENTER && InputTypeUtils.getImeOptionsActionIdFromEditorInfo(
                ime.currentInputEditorInfo ?: return) != EditorInfo.IME_ACTION_NONE) return
        val language = ForeignWords.languageOf(settings)
        val german = language == "de"
        if (!german && codePoint != Constants.CODE_ENTER) return // other languages: only the mark at the end of a line
        val context = ime.applicationContext ?: ime
        val lexicon = loaded ?: run { preload(context); return }
        val text = try { logic.mConnection.getTextBeforeCursor(MAX_TEXT, 0)?.toString() } catch (_: RuntimeException) { null } ?: return
        if (text.isEmpty()) return
        val cursor = logic.mConnection.expectedSelectionStart
        if (cursor < text.length) return
        val lineBreak = codePoint == Constants.CODE_ENTER && text.endsWith("\n") &&
            settings.mInputAttributes.mInputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val options = RetroCheck.Options(repair = repair && german, punctuate = punctuate, german = german, lineBreak = lineBreak)
        if (!options.repair && !options.punctuate) return
        val started = System.nanoTime()
        val edit = try {
            val facilitator = logic.mDictionaryFacilitator
            if (facilitator !== frequenciesOf) { frequencies.clear(); frequenciesOf = facilitator }
            RetroCheck.check(text, lexicon, Checked(context, settings, language, facilitator, rejectedHere, frequencies), options)
        } catch (_: Throwable) { null }
        val nanos = System.nanoTime() - started
        checked.incrementAndGet()
        if (edit == null) { KeyLatency.retro(nanos, "none"); return }
        apply(ime, settings, edit, text, cursor, nanos)
    }

    private fun apply(ime: LatinIME, settings: SettingsValues, edit: RetroCheck.Edit, text: String, cursor: Int, nanos: Long) {
        val fieldStart = cursor - text.length
        val from = fieldStart + edit.start
        if (logic.retroTouchesPrivateText(from, cursor)) { KeyLatency.retro(nanos, "private"); return }
        // a pair set here before and now back to the old form: the owner changed it back by hand
        val reverted = edit.pairs.withIndex().filter { (i, pair) -> appliedHere[fieldStart + edit.pairStarts[i]] == key(pair.first, pair.second) }
        if (reverted.isNotEmpty()) {
            for ((_, pair) in reverted) {
                rejectedHere.add(key(pair.first, pair.second))
                RejectedCorrections.remember(ime, pair.first, pair.second, settings)
            }
            undone.incrementAndGet(); KeyLatency.retro(nanos, "reverted")
            return
        }
        // the editor itself must still show the old text there (an app that changed it meanwhile: nothing is replaced)
        val outcome = logic.applyRetroEdit(edit.old, edit.replacement, cursor)
        if (outcome != ApplyOutcome.SUBMITTED) { stale.incrementAndGet(); KeyLatency.retro(nanos, "refused"); return }
        val cursorAfter = from + edit.replacement.length
        undo = Undo(cursorAfter, edit.old, edit.replacement, edit.pairs)
        edit.pairs.forEachIndexed { i, (old, new) ->
            appliedHere[fieldStart + edit.pairStarts[i]] = key(old, new)
            RuleCorrections.offered(old, new) // a rejection is learning data like that of the other rule corrections
        }
        applied.incrementAndGet()
        KeyLatency.retro(nanos, "applied")
    }

    /** The first word end reads the lexicon on the suggestion thread; the check starts at the next one. */
    private fun preload(context: Context) {
        if (preloading) return
        preloading = true
        // review N5: a failed read may be tried again at a later word end
        logic.postToSuggestionThread { if (lexicon(context) == null) preloading = false }
    }

    private fun eligible(ime: LatinIME, settings: SettingsValues): Boolean {
        val attributes = settings.mInputAttributes
        if (!settings.mAutoCorrectEnabled || attributes.mIsPasswordField || attributes.mNoLearning) return false
        // review N6: only text fields ("more autocorrection" also reaches number and phone fields)
        if (attributes.mInputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (settings.mIncognitoModeEnabled || settings.mIsLocked || LockState.isLocked(ime)) return false
        return !InputTypeUtils.isUriOrEmailType(attributes.mInputType)
    }

    /** What the check asks of the keyboard, on the suggestion thread. */
    private class Checked(
        private val context: Context, private val settings: SettingsValues, private val language: String,
        private val facilitator: Any?, private val rejected: Set<String>, private val cache: MutableMap<String, Int>,
    ) : SentenceContext {
        override fun frequency(word: String): Int {
            val key = language + '\u0000' + word
            cache[key]?.let { return it }
            val value = (facilitator as? DictionaryFacilitatorImpl)?.mainFrequency(word, language) ?: -1
            // an unknown word is not kept: the dictionary may still be loading
            if (value >= 0) cache[key] = value
            return value
        }
        override fun isProtected(word: String): Boolean =
            Memory.isOwnWord(context, word) || InformalWordsStore.protects(context, word, language) ||
                ForeignWords.isRemembered(context, settings, language, word)
        override fun isRejected(old: String, new: String): Boolean =
            key(old, new) in rejected || RejectedCorrections.contains(context, old, new)
    }

    companion object {
        /** The text before the cursor that is checked (the last sentence lies in it). */
        const val MAX_TEXT = 300
        /** Frequencies kept between the word ends of a sentence (a few sentences' words). */
        const val MAX_CACHED_WORDS = 512
        private const val KEY_DELETE = helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.DELETE
        private const val KEY_UNDO = helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.UNDO

        /** Counters since the keyboard started, for the diagnosis card (numbers only). */
        @JvmField val applied = AtomicInteger()
        @JvmField val undone = AtomicInteger()
        @JvmField val stale = AtomicInteger()
        @JvmField val checked = AtomicInteger()

        private fun key(old: String, new: String) = old.lowercase(Locale.ROOT) + '\u0000' + new.lowercase(Locale.ROOT)

        /** A key that ends a word: space, a mark that ends or divides a sentence, a line break. */
        @JvmStatic fun endsWord(codePoint: Int): Boolean =
            codePoint == ' '.code || codePoint == Constants.CODE_ENTER || codePoint in ".,!?;:".map { it.code }

        @Volatile private var loaded: SentenceLexicon? = null
        private val loadLock = Any()

        /** The bundled lexicon (verb forms, nouns), read once on the suggestion thread; null if the files cannot be read. */
        @JvmStatic fun lexicon(context: Context): SentenceLexicon? {
            loaded?.let { return it }
            synchronized(loadLock) {
                loaded?.let { return it }
                return try {
                    val assets = context.assets
                    val verbs = assets.open("agent/german_verb_forms.txt").bufferedReader(Charsets.UTF_8).use { r ->
                        GermanVerbs({ GermanVerbLemmas.contains(context, it) }, r.readLines().asSequence())
                    }
                    val nouns = assets.open("agent/german_nouns.txt").bufferedReader(Charsets.UTF_8).use { r ->
                        GermanNouns(r.lineSequence())
                    }
                    // review M2: the stores the check asks are read here too, not on the main thread at the first word end
                    runCatching {
                        Memory.isOwnWord(context, "x"); InformalWordsStore.protects(context, "x", "de")
                        ForeignWords.isRemembered(context, helium314.keyboard.latin.settings.Settings.getValues(), "de", "x"); RejectedCorrections.contains(context, "x", "y")
                    }
                    SentenceLexicon(verbs, nouns).also { verbs.readings("macht"); loaded = it }
                } catch (_: Throwable) { null }
            }
        }
    }
}
