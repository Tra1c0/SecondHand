// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary

/**
 * Stage 48: when the user goes back to an automatically corrected word, the word as it was typed
 * (same upper and lower case) stands in the first slot of the strip, the one HeliBoard uses for the
 * typed word while a correction is pending: left of the centre, right of it in RTL scripts. The
 * centre and the other side get the similar words. Going back means backspace right after the
 * correction (HeliBoard's revert) or moving the cursor into the corrected word.
 *
 * One instance per [helium314.keyboard.latin.inputlogic.InputLogic]. The corrections of the current
 * field are kept only in memory (at most [MAX_ENTRIES]), never stored or logged, and dropped when
 * input starts in a field. Written on the UI thread; [typedFor] is read from the suggestion thread.
 */
class TypedWordFirst {
    private class Entry(val start: Int, val inEditor: String, val typed: String)

    private val entries = ArrayDeque<Entry>()
    @Volatile private var active: Entry? = null

    /** [committed], starting at [start] in the editor, automatically replaced [typed]. */
    fun corrected(start: Int, committed: String, typed: String) {
        if (start < 0 || typed.isEmpty() || committed == typed) return
        put(Entry(start, committed, typed))
    }

    /**
     * The correction at [start] was undone with backspace: [typed] stands there again. Only for a
     * correction [corrected] remembered, so not for gesture words (review N4).
     */
    fun reverted(start: Int, typed: String) {
        if (start < 0 || typed.isEmpty() || entries.none { it.start == start }) return
        put(Entry(start, typed, typed))
    }

    /**
     * A word was committed at [start]: an older entry there belongs to text that is gone (the field
     * was cleared by backspace or by the app without restarting input), review N1. Called before
     * [corrected] adds the new correction.
     */
    fun committedAt(start: Int) {
        entries.removeAll { it.start == start }
        if (active?.start == start) active = null
    }

    private fun put(entry: Entry) {
        entries.removeAll { it.start == entry.start }
        entries.addLast(entry)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    /**
     * Suggestions resume on [word] starting at [start]: returns its typed form if it is a corrected
     * (or just reverted) word of this field and keeps it for [typedFor], otherwise forgets any.
     */
    fun resumed(start: Int, word: String): String? {
        val entry = entries.lastOrNull { it.start == start && it.inEditor == word }
        active = entry
        return entry?.typed
    }

    /** The typed form for the word being composed, only while it is the resumed word, unchanged. */
    fun typedFor(composing: String, isResumed: Boolean): String? =
        active?.takeIf { isResumed && it.inEditor == composing }?.typed

    /**
     * The automatic correction that picking [picked] undoes, or null: [picked] is the typed form of
     * the resumed, still unchanged corrected word [composing].
     */
    fun correctionUndoneBy(picked: String, composing: String, isResumed: Boolean): String? {
        val entry = active ?: return null
        if (!isResumed || entry.inEditor != composing || entry.typed != picked || entry.inEditor == entry.typed) return null
        return entry.inEditor
    }

    fun clear() {
        entries.clear()
        active = null
    }

    companion object {
        const val MAX_ENTRIES = 32

        /**
         * [words] for the resumed word with [typed] at index 2. In the typing style the strip hides
         * index 0 (the word in the editor), shows index 1 in the centre and index 2 left of it (right
         * in RTL), see `SuggestionStripLayoutHelper.getPositionInSuggestionStrip`. [typed] keeps its
         * case and is not repeated elsewhere. Pending autocorrections, predictions, punctuation and
         * gesture results stay as they are.
         */
        @JvmStatic
        fun arrange(words: SuggestedWords, typed: String): SuggestedWords {
            if (typed.isEmpty() || words.isEmpty || words.mWillAutoCorrect || words.isPunctuationSuggestions) return words
            if (words.mInputStyle != SuggestedWords.INPUT_STYLE_TYPING && words.mInputStyle != SuggestedWords.INPUT_STYLE_RECORRECTION) return words
            val inEditor = words.getInfo(0)
            val others = (1 until words.size()).map { words.getInfo(it) }
                .filter { it.mWord != typed && it.mWord != inEditor.mWord }
            val list = ArrayList<SuggestedWordInfo>(others.size + 2)
            list.add(inEditor)
            others.firstOrNull()?.let { list.add(it) }
            list.add(SuggestedWordInfo(typed, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
                Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
            list.addAll(others.drop(1))
            return SuggestedWords(list, words.mRawSuggestions, words.mTypedWordInfo, words.mTypedWordValid, false,
                words.mIsObsoleteSuggestions, SuggestedWords.INPUT_STYLE_TYPING, words.mSequenceNumber)
        }
    }
}
