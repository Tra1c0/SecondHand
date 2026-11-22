// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary

/**
 * Stage 94 (owner, 2026-11-22): "Wenn man ein Wort Buchstabe für Buchstabe löscht, wird das aktuelle Wort auch angezeigt – in seinem
 * Zustand." While a word is edited with the delete key or is resumed, the strip shows the word as it stands in the centre (index 1),
 * the typed form of a corrected word left of it (index 2, stage 48) and the other candidates after them. Before, the strip hid the
 * word being edited (index 0 is never shown in the typing style), so a resumed valid word without alternatives left the strip empty.
 * A pending autocorrection keeps its own order (the correction in the centre, the typed word left of it, see Suggest).
 */
object CurrentWordShown {
    @JvmStatic
    fun arrange(words: SuggestedWords, current: String, typedFirst: String?): SuggestedWords {
        if (current.isEmpty() || words.mWillAutoCorrect || words.isPunctuationSuggestions
            || (!words.isEmpty && words.mInputStyle != SuggestedWords.INPUT_STYLE_TYPING && words.mInputStyle != SuggestedWords.INPUT_STYLE_RECORRECTION))
            return if (typedFirst != null) TypedWordFirst.arrange(words, typedFirst) else words
        val infos = (0 until words.size()).map { words.getInfo(it) }
        val inEditor = infos.firstOrNull()?.takeIf { it.mWord == current } ?: typed(current)
        val others = infos.filter { it.mWord != current && it.mWord != typedFirst }
        val list = ArrayList<SuggestedWordInfo>(others.size + 3)
        list.add(inEditor)
        list.add(typed(current))
        if (typedFirst != null && typedFirst != current) list.add(typed(typedFirst))
        list.addAll(others)
        return SuggestedWords(list, words.mRawSuggestions, words.mTypedWordInfo ?: inEditor, words.mTypedWordValid, false,
            words.mIsObsoleteSuggestions, SuggestedWords.INPUT_STYLE_TYPING, words.mSequenceNumber)
    }

    private fun typed(word: String) = SuggestedWordInfo(word, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
        Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
}
