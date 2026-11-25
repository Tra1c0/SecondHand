// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary

/**
 * Stage 96 (owner, 2026-11-25, replaces the order of stage 94): "Beim Zurückgehen auf das Wort oder Zeichenlöschen bis und im Wort
 * wird LINKS das alte Wort jederzeit angezeigt, außer: werden Zeichen von dem Wort aktiv gelöscht, wird das ebenfalls links im
 * aktuellen Zustand angezeigt."
 *
 * While a word is resumed or edited with the delete key, the strip shows [left] at index 2 (left of the centre, right in RTL
 * scripts): the caller passes the old word (the typed form of a corrected word, stage 48, or the word itself) when nothing of the
 * word was deleted, and the word as it stands ("lösch") when characters were deleted. The centre (index 1) and the right side get
 * the engine's other candidates. If there are none, index 1 is the word as it stands, so that the strip is never empty and [left]
 * is still shown left (a list of two would be laid out as one word over the whole strip). Index 0 stays the word in the editor
 * (never shown in the typing style). A pending autocorrection keeps its own order (the correction in the centre, the typed word
 * left of it, see Suggest).
 */
object CurrentWordShown {
    /** Index of [left] in the result: left of the centre in the typing style, see `SuggestionStripLayoutHelper`. */
    const val LEFT_INDEX = 2

    /**
     * [current] is the word in the editor, [left] the word for the left slot, [typedFirst] the typed form of a resumed corrected
     * word (only used for the orders this function does not change).
     */
    @JvmStatic
    fun arrange(words: SuggestedWords, current: String, left: String, typedFirst: String?): SuggestedWords {
        if (current.isEmpty() || left.isEmpty() || words.mWillAutoCorrect || words.isPunctuationSuggestions
            || (!words.isEmpty && words.mInputStyle != SuggestedWords.INPUT_STYLE_TYPING && words.mInputStyle != SuggestedWords.INPUT_STYLE_RECORRECTION))
            return if (typedFirst != null) TypedWordFirst.arrange(words, typedFirst) else words
        val infos = (0 until words.size()).map { words.getInfo(it) }
        val inEditor = infos.firstOrNull()?.takeIf { it.mWord == current } ?: typed(current)
        val others = infos.filter { it.mWord != current && it.mWord != left }.distinctBy { it.mWord }
        val list = ArrayList<SuggestedWordInfo>(others.size + 3)
        list.add(inEditor)
        list.add(others.firstOrNull() ?: typed(current))
        list.add(typed(left))
        list.addAll(others.drop(1))
        return SuggestedWords(list, words.mRawSuggestions, words.mTypedWordInfo ?: inEditor, words.mTypedWordValid, false,
            words.mIsObsoleteSuggestions, SuggestedWords.INPUT_STYLE_TYPING, words.mSequenceNumber)
    }

    private fun typed(word: String) = SuggestedWordInfo(word, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
        Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
}
