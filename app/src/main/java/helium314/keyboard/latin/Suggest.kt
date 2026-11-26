/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin

import helium314.keyboard.agent.CorrectionGuard
import helium314.keyboard.agent.EmojiSuggestions
import helium314.keyboard.agent.ForeignWords
import helium314.keyboard.agent.RejectedCorrections
import helium314.keyboard.agent.TypoMemory
import helium314.keyboard.agent.GermanVerbLemmas
import helium314.keyboard.agent.OtherLanguageDictionaries
import dev.agentkeyboard.core.EnglishI
import dev.agentkeyboard.core.GermanVerbAgreement
import dev.agentkeyboard.core.GermanShortForms
import dev.agentkeyboard.core.GermanVerbForms
import dev.agentkeyboard.core.NeighborKeys
import dev.agentkeyboard.core.OtherLanguageWord
import dev.agentkeyboard.core.RareWordWeights
import dev.agentkeyboard.core.SentenceLanguage
import dev.agentkeyboard.core.SwappedLetters
import android.text.TextUtils
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiDefaultVersion
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.define.DecoderSpecificConstants.SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION
import helium314.keyboard.latin.define.DecoderSpecificConstants.SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.AutoCorrectionUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.BackgroundGatheringCache
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.WordData
import helium314.keyboard.latin.utils.useBackgroundGathering
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
class Suggest(private val mDictionaryFacilitator: DictionaryFacilitator) {
    private var mAutoCorrectionThreshold = 0f
    private val mPlausibilityThreshold = 0f
    private val nextWordSuggestionsCache = HashMap<NgramContext, SuggestionResults>()

    // cache cleared whenever LatinIME.loadSettings is called, notably on changing layout and switching input fields
    fun clearNextWordSuggestionsCache() = nextWordSuggestionsCache.clear()

    /**
     * Set the normalized-score threshold for a suggestion to be considered strong enough that we
     * will auto-correct to this.
     * @param threshold the threshold
     */
    fun setAutoCorrectionThreshold(threshold: Float) {
        mAutoCorrectionThreshold = threshold
    }

    // todo: remove when InputLogic is ready
    interface OnGetSuggestedWordsCallback {
        fun onGetSuggestedWords(suggestedWords: SuggestedWords?)
    }

    @JvmOverloads
    fun getSuggestedWords(wordComposer: WordComposer, ngramContext: NgramContext, keyboard: Keyboard,
                          settingsValuesForSuggestion: SettingsValuesForSuggestion, isCorrectionEnabled: Boolean,
                          inputStyle: Int, sequenceNumber: Int, followingText: () -> CharSequence? = { null }): SuggestedWords =
        if (wordComposer.isBatchMode) {
            getSuggestedWordsForBatchInput(wordComposer, ngramContext, keyboard, settingsValuesForSuggestion,
                inputStyle, isCorrectionEnabled, sequenceNumber)
        } else {
            getSuggestedWordsForNonBatchInput(wordComposer, ngramContext, keyboard, settingsValuesForSuggestion,
                inputStyle, isCorrectionEnabled, sequenceNumber, followingText)
        }

    // Retrieves suggestions for non-batch input (typing, recorrection, predictions...)
    // and calls the callback function with the suggestions.
    private fun getSuggestedWordsForNonBatchInput(wordComposer: WordComposer, ngramContext: NgramContext, keyboard: Keyboard,
                      settingsValuesForSuggestion: SettingsValuesForSuggestion, inputStyleIfNotPrediction: Int,
                      isCorrectionEnabled: Boolean, sequenceNumber: Int, followingText: () -> CharSequence?): SuggestedWords {
        val typedWordString = wordComposer.typedWord
        startRuleClassCleanup()
        val resultsArePredictions = !wordComposer.isComposingWord
        val suggestionResults = if (typedWordString.isEmpty())
                getNextWordSuggestions(ngramContext, keyboard, inputStyleIfNotPrediction, settingsValuesForSuggestion)
            else mDictionaryFacilitator.getSuggestionResults(wordComposer.composedDataSnapshot, ngramContext, keyboard,
                settingsValuesForSuggestion, SESSION_ID_TYPING, inputStyleIfNotPrediction)
        val trailingSingleQuotesCount = StringUtils.getTrailingSingleQuotesCount(typedWordString)
        val capsMode = getCapsModeForTyping(wordComposer, keyboard)
        val suggestionsContainer = ArrayList(suggestionResults)
        capitalizeAndAddTrailingSingleQuotes(suggestionsContainer, capsMode, trailingSingleQuotesCount, mDictionaryFacilitator.mainLocale)
        val capitalizedTypedWord = capitalize(typedWordString, capsMode, mDictionaryFacilitator.mainLocale)

        // store the original SuggestedWordInfo for typed word, as it will be removed
        // we may want to re-add it in case auto-correction happens, so that the original word can at least be selected
        // we check against the capitalizedTypedWord because getTransformedSuggestedWordInfoList adjusts for capsMode
        val typedWordFirstOccurrenceWordInfo = suggestionsContainer.firstOrNull { it.mWord == capitalizedTypedWord }
        val firstOccurrenceOfTypedWordInSuggestions = SuggestedWordInfo.removeDupsAndTypedWord(capitalizedTypedWord, suggestionsContainer)
        makeFirstTwoSuggestionsNonEmoji(suggestionsContainer)

        val after by lazy(followingText)
        val normalFirst = suggestionsContainer.firstOrNull()
        val preferred = if (isCorrectionEnabled && Settings.getValues().mContextualAutocorrection &&
            !resultsArePredictions && normalFirst?.isGermanVerbCandidate() == true)
            preferredGermanVerb(typedWordString, normalFirst.mWord, suggestionsContainer, suggestionResults, ngramContext) { after }
        else null
        if (preferred != null && preferred != typedWordString) {
            val index = suggestionsContainer.indexOfFirst { it.mWord == preferred && it.isGermanVerbCandidate() }
            if (index > 0) suggestionsContainer.add(0, suggestionsContainer.removeAt(index))
        }

        val (allowsToBeAutoCorrected, rawHasAutoCorrection) = shouldBeAutoCorrected(
            trailingSingleQuotesCount,
            capitalizedTypedWord,
            suggestionsContainer.firstOrNull(),
            {
                val first = suggestionsContainer.firstOrNull() ?: suggestionResults.first()
                val suggestions = getNextWordSuggestions(ngramContext, keyboard, inputStyleIfNotPrediction, settingsValuesForSuggestion)
                val suggestionForFirstInContainer = suggestions.firstOrNull { it.mWord == first.word }
                val suggestionForTypedWord = suggestions.firstOrNull { it.mWord == capitalizedTypedWord }
                suggestionForFirstInContainer to suggestionForTypedWord
            },
            isCorrectionEnabled,
            wordComposer,
            suggestionResults,
            firstOccurrenceOfTypedWordInSuggestions,
            typedWordFirstOccurrenceWordInfo
        )
        // fork, stage 20a: protect known words and corrections the user undid before
        val guardedAutoCorrection = rawHasAutoCorrection && preferred != typedWordString && guardAllows(typedWordString, suggestionsContainer.firstOrNull(),
            typedWordFirstOccurrenceWordInfo, suggestionResults, ngramContext)
        // fork, stage 20b: a typo the user corrected by hand three times is corrected automatically
        val learnedCorrection = applyLearnedCorrection(typedWordString, capsMode, ngramContext, suggestionResults,
            suggestionsContainer, isCorrectionEnabled, wordComposer) { after }
        // fork, stage 75: a very common short word with two swapped letters ("idr" -> "dir"); a learned pair keeps priority
        // (only when the engine has no correction of its own, or the same one; the German verb preference keeps priority)
        val engineCorrection = if (guardedAutoCorrection) suggestionsContainer.firstOrNull()?.mWord else null
        // fork, stage 101: the English "i" in an English sentence, and a very common word typed with one neighbouring key
        // ("icj" -> "ich") that only the typing history knows
        // fork, stages 107/108: "gibts" -> "gibt's", and a rare dictionary word -> its very common neighbour by the owner's weights
        val nextWords by lazy { getNextWordSuggestions(ngramContext, keyboard, inputStyleIfNotPrediction, settingsValuesForSuggestion) }
        val hasAutoCorrection = learnedCorrection
            || applyEnglishI(typedWordString, ngramContext, suggestionsContainer, isCorrectionEnabled, wordComposer)
            || applyShortForm(typedWordString, capsMode, ngramContext, suggestionResults, suggestionsContainer, isCorrectionEnabled, wordComposer)
            || applySwappedLetters(typedWordString, capsMode, ngramContext, suggestionResults,
                suggestionsContainer, isCorrectionEnabled, wordComposer, engineCorrection) { after }
            || applyNeighborKey(typedWordString, capsMode, ngramContext, suggestionResults,
                suggestionsContainer, isCorrectionEnabled, wordComposer, engineCorrection) { after }
            || applyRareWord(typedWordString, capsMode, ngramContext, suggestionResults,
                suggestionsContainer, isCorrectionEnabled, wordComposer, engineCorrection) { nextWords }
            || guardedAutoCorrection
        // fork, stage 46: own words of the memory (offered while typing, behind the correction) and its next word
        addFromMemory(typedWordString, resultsArePredictions, suggestionResults, suggestionsContainer, ngramContext, hasAutoCorrection)
        // fork, stage 51: words of other languages the owner kept (offered while typing, behind the correction)
        addForeignWord(typedWordString, resultsArePredictions, suggestionResults, suggestionsContainer, capsMode, hasAutoCorrection)
        // fork, stage 35: tolerant emoji suggestions behind the first two words, never the autocorrection
        if (typedWordString.isNotEmpty() && !resultsArePredictions) {
            val mainLocale = mDictionaryFacilitator.mainLocale
            // the language of the dictionary that knows the typed word, if one does
            val wordLanguage = suggestionResults.firstOrNull { it.mWord.equals(typedWordString, ignoreCase = true) && !it.isEmoji }
                ?.let { it.mSourceDict.mLocale?.language ?: mainLocale.language }
            EmojiSuggestions.addTo(suggestionsContainer, typedWordString, mainLocale, wordLanguage,
                hasAutoCorrection, Settings.getValues(), Settings.getCurrentContext())
        }
        val typedWordInfo = SuggestedWordInfo(typedWordString, "", SuggestedWordInfo.MAX_SCORE,
            SuggestedWordInfo.KIND_TYPED, typedWordFirstOccurrenceWordInfo?.mSourceDict ?: Dictionary.DICTIONARY_USER_TYPED,
            SuggestedWordInfo.NOT_AN_INDEX , SuggestedWordInfo.NOT_A_CONFIDENCE)
        if (typedWordString.isNotEmpty()) {
            suggestionsContainer.add(0, typedWordInfo)
        }
        val suggestionsList = if (SuggestionStripView.DEBUG_SUGGESTIONS && suggestionsContainer.isNotEmpty())
                getSuggestionsInfoListWithDebugInfo(capitalizedTypedWord, suggestionsContainer)
            else suggestionsContainer

        val inputStyle = if (resultsArePredictions) {
            if (suggestionResults.mIsBeginningOfSentence) SuggestedWords.INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION
            else SuggestedWords.INPUT_STYLE_PREDICTION
        } else {
            inputStyleIfNotPrediction
        }

        useDefaultEmojiSkinTone(suggestionsList)

        // If there is an incoming autocorrection, make sure typed word is shown, so user is able to override it.
        // Otherwise, if the relevant setting is enabled, show the typed word in the middle.
        val typedWordWasCapitalized = capitalizedTypedWord != typedWordString
        val correctToCapitalizedWord = typedWordWasCapitalized && isCorrectionEnabled && Settings.getValues().mAutoCorrectCapitalizedSuggestion
            && !wordComposer.isCursorFrontOrMiddleOfComposingWord && typedWordString.drop(1).none { it.isUpperCase() }
        val indexOfTypedWord = 1 + if (hasAutoCorrection) SuggestedWords.INDEX_OF_AUTO_CORRECTION else SuggestedWords.INDEX_OF_TYPED_WORD
        if (
            (hasAutoCorrection
                || (Settings.getValues().mCenterSuggestionTextToEnter && !wordComposer.isResumed)
                || typedWordWasCapitalized
            ) && suggestionsList.size >= indexOfTypedWord && capitalizedTypedWord.isNotEmpty()) {
            if (typedWordFirstOccurrenceWordInfo != null) {
                addDebugInfo(typedWordFirstOccurrenceWordInfo, capitalizedTypedWord)
                suggestionsList.add(indexOfTypedWord, typedWordFirstOccurrenceWordInfo)
            } else {
                suggestionsList.add(indexOfTypedWord,
                    SuggestedWordInfo(capitalizedTypedWord, "", 0, SuggestedWordInfo.KIND_TYPED,
                        Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
                )
            }
        }
        val isTypedWordValid = firstOccurrenceOfTypedWordInSuggestions > -1 || (!resultsArePredictions && !allowsToBeAutoCorrected)
        return SuggestedWords(suggestionsList, suggestionResults.mRawSuggestions, typedWordInfo,
            isTypedWordValid, hasAutoCorrection || correctToCapitalizedWord, false, inputStyle, sequenceNumber)
    }

    private fun SuggestedWordInfo.isGermanVerbCandidate() =
        (mSourceDict.mDictType == Dictionary.TYPE_MAIN || mSourceDict.mDictType == Dictionary.TYPE_USER_HISTORY) &&
            mSourceDict.mLocale?.language == "de" &&
            !isEmoji && !isKindOf(SuggestedWordInfo.KIND_WHITELIST) && !isKindOf(SuggestedWordInfo.KIND_SHORTCUT)

    private fun preferredGermanVerb(typed: String, first: String, suggestions: List<SuggestedWordInfo>, raw: SuggestionResults,
                                    context: NgramContext, after: () -> CharSequence?): String? {
        val previousWords = (1..context.prevWordCount).asSequence()
            .takeWhile { !context.isNthPrevWordBeginningOfSentence(it) }
            .mapNotNull { context.getNthPrevWord(it)?.toString() }
        val candidates = suggestions.asSequence().filter { it.isGermanVerbCandidate() }.map { it.mWord }
        val settingsContext = Settings.getCurrentContext()
        return GermanVerbAgreement.prefer(typed, first, candidates, previousWords,
            isInfinitive = { GermanVerbLemmas.contains(settingsContext, it) }, typedIsKnown = {
            raw.any { it.mWord == typed && it.mSourceDict.mDictType == Dictionary.TYPE_MAIN && it.mSourceDict.mLocale?.language == "de" } ||
                (mDictionaryFacilitator as? DictionaryFacilitatorImpl)?.mainLocalesKnowing(typed)?.any { it.language == "de" } == true
        }, after = after)
    }

    /**
     * Puts the learned meant word of [typed] first in [suggestions] and returns true, or changes
     * nothing and returns false. Respects the autocorrect setting and undone corrections.
     */
    private fun applyLearnedCorrection(
        typed: String, capsMode: CapsMode, ngramContext: NgramContext, results: SuggestionResults,
        suggestions: ArrayList<SuggestedWordInfo>, isCorrectionEnabled: Boolean, wordComposer: WordComposer,
        followingText: () -> CharSequence?,
    ): Boolean {
        val settings = Settings.getValues()
        // the same exclusions as the normal autocorrection
        if (!settings.mLearnTypos || !isCorrectionEnabled || typed.isEmpty() || !wordComposer.isComposingWord
            || wordComposer.isResumed || wordComposer.hasDigits() || wordComposer.isCursorFrontOrMiddleOfComposingWord
            || (wordComposer.isMostlyCaps && !wordComposer.isAllUpperCase) || typed.contains('@') || typed.contains('.')
            || helium314.keyboard.agent.KeyboardCommands.isCommandWord(typed)
            || helium314.keyboard.agent.KeyboardCommands.suppressesCorrection()
            || !mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()) return false
        val context = Settings.getCurrentContext() ?: return false
        val previous = if (ngramContext.prevWordCount >= 1 && !ngramContext.isNthPrevWordBeginningOfSentence(1))
            ngramContext.getNthPrevWord(1)?.toString() else null
        // known if any main dictionary has it, not only the best-ranked source
        val typedIsMainWord = results.any { it.mWord.equals(typed, ignoreCase = true) && it.mSourceDict.mDictType == Dictionary.TYPE_MAIN }
        // "never change correct words" also covers learned pairs; the umlaut rule does not: three own picks in this context are a clear wish
        if (typedIsMainWord && settings.mKeepValidWords) return false
        // stage 46: an own word of the memory is never corrected automatically
        if (helium314.keyboard.agent.Memory.isOwnWord(context, typed)) return false
        // stage 51: nor a word of another language the owner kept (learned data belongs to the keyboard language)
        val language = ForeignWords.languageOf(settings)
        if (ForeignWords.isRemembered(context, settings, language, typed)) return false
        // stage 51: pairs of the current language; one from before stage 51 only if this language knows its word
        val meant = TypoMemory.meantWord(context, typed, previous, typedIsMainWord, language) { word ->
            mDictionaryFacilitator.isValidSuggestionWord(word) || mDictionaryFacilitator.isValidSuggestionWord(word.lowercase(mDictionaryFacilitator.mainLocale))
        } ?: return false
        if (settings.mRememberRejectedCorrections && RejectedCorrections.contains(context, typed, meant)) return false
        val wanted = capitalize(meant, capsMode, mDictionaryFacilitator.mainLocale)
        if (settings.mContextualAutocorrection && language == "de" &&
            preferredGermanVerb(typed, wanted, suggestions, results, ngramContext, followingText) != null)
            return false
        val index = suggestions.indexOfFirst { it.mWord == wanted }.takeIf { it >= 0 }
            ?: suggestions.indexOfFirst { it.mWord.equals(meant, ignoreCase = true) && !(meant.first().isUpperCase() && it.mWord.first().isLowerCase()) }
        val info = if (index >= 0) suggestions.removeAt(index)
            else SuggestedWordInfo(wanted, "", SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_CORRECTION, TypoMemory.DICTIONARY, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        suggestions.add(0, info)
        return true
    }

    /**
     * Stage 75: puts the word that [typed] becomes by swapping one pair of adjacent letters first in
     * [suggestions] and returns true, or changes nothing and returns false. Only for a typed word that no
     * main dictionary knows, when exactly one swap gives a very common main-dictionary word
     * ([SwappedLetters]); the same exclusions as the other automatic corrections (own words, slang, words
     * of another language the owner kept, undone corrections, commands, dictionary shortcuts), and only
     * with the switch "Swapped letters" on. The engine's own results are not needed: the word is added
     * when the engine did not offer it. The result is an ordinary automatic correction, so Backspace
     * and the rejected-pair memory treat it like every other one.
     */
    private fun applySwappedLetters(
        typed: String, capsMode: CapsMode, ngramContext: NgramContext, results: SuggestionResults,
        suggestions: ArrayList<SuggestedWordInfo>, isCorrectionEnabled: Boolean, wordComposer: WordComposer,
        engineCorrection: String?, followingText: () -> CharSequence?,
    ): Boolean {
        val settings = Settings.getValues()
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return false
        if (!settings.mSwappedLettersCorrection || !isCorrectionEnabled || typed.isEmpty() || !wordComposer.isComposingWord
            || wordComposer.isResumed || wordComposer.hasDigits() || wordComposer.isCursorFrontOrMiddleOfComposingWord
            || (wordComposer.isMostlyCaps && !wordComposer.isAllUpperCase)
            || helium314.keyboard.agent.KeyboardCommands.isCommandWord(typed)
            || helium314.keyboard.agent.KeyboardCommands.suppressesCorrection()
            || !facilitator.hasAtLeastOneInitializedMainDictionary()) return false
        // a dictionary shortcut or whitelist entry is a wish of the owner or the dictionary
        if (suggestions.firstOrNull()?.let { it.isKindOf(SuggestedWordInfo.KIND_SHORTCUT) || it.isKindOf(SuggestedWordInfo.KIND_WHITELIST) } == true) return false
        // the engine's first suggestion is the typed word in another case ("ort" -> "Ort"): a known word, stays
        if (suggestions.firstOrNull()?.mWord.equals(typed, ignoreCase = true)) return false
        // the typed word is known to a main dictionary of any active language as typed, in lower case, with a
        // capital first letter or in upper case ("Ort", "BSI"), or a main-dictionary result is the typed word in
        // some case, or the personal dictionary, the contacts or the apps know it (the typing history does not
        // count: it also keeps words typed wrongly). Asked only for 3 to 5 letters ([SwappedLetters.candidate]).
        val locale = facilitator.mainLocale
        val known = {
            listOf(typed, typed.replaceFirstChar { it.uppercase(locale) }, typed.uppercase(locale)).any { facilitator.mainLocalesKnowing(it).isNotEmpty() } ||
            results.any {
                it.mWord.equals(typed, ignoreCase = true) && (it.mSourceDict.mDictType == Dictionary.TYPE_MAIN
                    || it.mSourceDict.mDictType == Dictionary.TYPE_USER || it.mSourceDict.mDictType == Dictionary.TYPE_CONTACTS
                    || it.mSourceDict.mDictType == Dictionary.TYPE_APPS)
            }
        }
        // the candidate is judged only in the dictionary of the keyboard language
        val language = locale.language
        val meant = SwappedLetters.candidate(typed, language, known) { facilitator.mainFrequency(it, language) } ?: return false
        // the engine's own correction wins unless it is the same word
        if (engineCorrection != null && !engineCorrection.equals(meant, ignoreCase = true)) return false
        // stage 101 (A): a regular form of a German verb stays as typed
        if (isUnknownGermanVerbForm(typed)) return false
        val context = Settings.getCurrentContext()
        // stage 46 / 71 / 51: own words, slang and kept words of another language stay as typed
        if (helium314.keyboard.agent.Memory.isOwnWord(context, typed)) return false
        val learnLanguage = ForeignWords.languageOf(settings)
        if (helium314.keyboard.agent.InformalWordsStore.protects(context, typed, learnLanguage, language)) return false
        if (ForeignWords.isRemembered(context, settings, learnLanguage, typed)) return false
        // stage 20a: a correction the owner undid is not applied again
        if (settings.mRememberRejectedCorrections && context != null && RejectedCorrections.contains(context, typed, meant)) return false
        val wanted = capitalize(meant, capsMode, facilitator.mainLocale)
        // stage 72: the German verb preference keeps priority, as over a learned correction
        if (settings.mContextualAutocorrection && learnLanguage == "de" &&
            preferredGermanVerb(typed, wanted, suggestions, results, ngramContext, followingText) != null) return false
        val index = suggestions.indexOfFirst { it.mWord == wanted }
        val info = if (index >= 0) suggestions.removeAt(index)
            else SuggestedWordInfo(wanted, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION,
                SWAPPED_LETTERS_DICTIONARY, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        suggestions.add(0, info)
        return true
    }

    /**
     * Stage 46: tells the memory whether a main dictionary knows the typed word (an own word is counted
     * only when none does), offers an own word that the typed word starts (never as the automatic
     * correction: behind it, or first without one) and puts the memory's next word first among the
     * predictions. Nothing changes without the memory switched on or in a blocked field.
     */
    private fun addFromMemory(typed: String, predictions: Boolean, results: SuggestionResults,
                              suggestions: ArrayList<SuggestedWordInfo>, ngramContext: NgramContext, hasAutoCorrection: Boolean) {
        val context = Settings.getCurrentContext() ?: return
        if (!helium314.keyboard.agent.Memory.isEnabled(context)) return
        val settings = Settings.getValues()
        if (typed.isNotEmpty() && !predictions) {
            helium314.keyboard.agent.Memory.onTyped(typed,
                results.any { it.mWord.equals(typed, ignoreCase = true) && it.mSourceDict.mDictType == Dictionary.TYPE_MAIN })
            val own = helium314.keyboard.agent.Memory.ownCompletion(context, settings, typed) ?: return
            if (suggestions.any { it.mWord.equals(own, ignoreCase = true) }) return
            suggestions.add(if (hasAutoCorrection) minOf(1, suggestions.size) else 0, SuggestedWordInfo(own, "", SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_COMPLETION, helium314.keyboard.agent.Memory.DICTIONARY, SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWordInfo.NOT_A_CONFIDENCE))
        } else if (typed.isEmpty() && predictions && settings.mBigramPredictionEnabled) {
            val next = helium314.keyboard.agent.Memory.nextWord(context, settings, ngramContext) ?: return
            val index = suggestions.indexOfFirst { it.mWord.equals(next, ignoreCase = true) }
            val info = if (index >= 0) suggestions.removeAt(index) else SuggestedWordInfo(next, "", SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_PREDICTION, helium314.keyboard.agent.Memory.DICTIONARY, SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWordInfo.NOT_A_CONFIDENCE)
            suggestions.add(0, info)
        }
    }

    /**
     * Stage 51: tells [ForeignWords] whether a main dictionary of the current language knows the typed
     * word in some case (only a word it does not know can be a word of another language) and offers a
     * remembered word of another language that the typed word starts: behind the automatic correction,
     * first without one, never as the correction. Nothing without "Personalisierte Vorschläge", in a
     * blocked field or while locked.
     */
    private fun addForeignWord(typed: String, predictions: Boolean, results: SuggestionResults,
                               suggestions: ArrayList<SuggestedWordInfo>, capsMode: CapsMode, hasAutoCorrection: Boolean) {
        if (typed.isEmpty() || predictions) return
        val settings = Settings.getValues()
        val language = ForeignWords.languageOf(settings)
        // review N5: only when these results come from the keyboard language with a loaded main dictionary;
        // otherwise (a language command just switched, the dictionary still loads) treated as known
        val canTell = mDictionaryFacilitator.mainLocale.language == language && mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()
        ForeignWords.onTyped(typed, language, !canTell || results.any { it.mSourceDict.mDictType == Dictionary.TYPE_MAIN
            && it.mSourceDict.mLocale?.language == language && it.mWord.equals(typed, ignoreCase = true) })
        val word = ForeignWords.completion(Settings.getCurrentContext(), settings, language, typed) ?: return
        val mainLocale = mDictionaryFacilitator.mainLocale
        if (suggestions.any { it.mWord.equals(word, ignoreCase = true) }) return
        suggestions.add(if (hasAutoCorrection) minOf(1, suggestions.size) else 0, SuggestedWordInfo(capitalize(word, capsMode, mainLocale), "",
            SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_COMPLETION, ForeignWords.DICTIONARY, SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE))
    }

    /**
     * The typed word counts as known only if it is in a main dictionary of the same language as the
     * correction: a word from another active language, the typing history, contacts or the personal
     * dictionary does not protect it (e.g. English "fur" must not keep German "für" from being set).
     * Exception (stage 48): a correction that changes only the case is blocked as soon as the main
     * dictionary or the typing history of the correction's language knows the exact typed form, unless
     * the dictionary itself demands it (whitelist, "i" -> "I"). Another active language does not count
     * (English "hand" must not keep German "Hand" from being set). Stage 51: a word of another language
     * counts once the owner kept it ([ForeignWords]: undone correction, or written twice at other places);
     * until then English "fur" still becomes German "für". Stage 60: a typed word of another active language
     * also stays when the sentence so far is of that language ([sentenceIsOfOtherLanguage]: English "name"
     * in an English sentence), the old rule applies without such evidence.
     */
    private fun guardAllows(typed: String, first: SuggestedWordInfo?, typedInfo: SuggestedWordInfo?,
                            results: Collection<SuggestedWordInfo>, ngramContext: NgramContext): Boolean {
        val correction = first?.mWord ?: return true
        val typedIsDictionaryWord = typedInfo != null && typedInfo.mSourceDict.mDictType == Dictionary.TYPE_MAIN
            && typedInfo.mSourceDict.mLocale == first.mSourceDict.mLocale
        val settings = Settings.getValues()
        val context = Settings.getCurrentContext()
        // stage 46: an own word of the memory (slang, a deliberate spelling) stays as typed; the correction stays a suggestion
        if (helium314.keyboard.agent.Memory.isOwnWord(context, typed)) return false
        // stage 71: so does a built-in informal word (slang, chat abbreviation, Denglish, gaming/tech/music term). Not for a
        // correction that only changes the case (stage 48 decides that) and not for a personal shortcut or a dictionary whitelist
        // entry: both are wishes of the owner or the dictionary, not guesses of the engine.
        if (!first.isKindOf(SuggestedWordInfo.KIND_SHORTCUT) && !first.isKindOf(SuggestedWordInfo.KIND_WHITELIST)
            && !CorrectionGuard.changesOnlyCase(typed, correction)
            && helium314.keyboard.agent.InformalWordsStore.protects(context, typed, ForeignWords.languageOf(settings), first.mSourceDict.mLocale?.language)) return false
        // stage 51: as does a word of another language the owner kept in this language
        if (ForeignWords.isRemembered(context, settings, ForeignWords.languageOf(settings), typed)) return false
        // stage 101 (A, owner: "ich übermittle" -> "Übermittler"): a regular form of a known German verb that the dictionary
        // lacks stays as typed; a personal shortcut or a whitelist entry still applies, a change of case is stage 48's
        if (!first.isKindOf(SuggestedWordInfo.KIND_SHORTCUT) && !first.isKindOf(SuggestedWordInfo.KIND_WHITELIST)
            && !CorrectionGuard.changesOnlyCase(typed, correction) && typedInfo?.mSourceDict?.mDictType != Dictionary.TYPE_MAIN
            && isUnknownGermanVerbForm(typed)) return false
        // stage 48: a known word never gets only another case automatically ("essen" stays, "Essen" is offered)
        if (!first.isKindOf(SuggestedWordInfo.KIND_WHITELIST) && CorrectionGuard.changesOnlyCase(typed, correction)
            && results.any { it.mWord == typed && it.mSourceDict.mLocale == first.mSourceDict.mLocale
                && (it.mSourceDict.mDictType == Dictionary.TYPE_MAIN || it.mSourceDict.mDictType == Dictionary.TYPE_USER_HISTORY) })
            return false
        val allowed = CorrectionGuard.allowsAutoCorrection(typed, correction, typedIsDictionaryWord,
            settings.mKeepValidWords, settings.mKeepUmlautVariants
        ) { t, c -> settings.mRememberRejectedCorrections && context != null && RejectedCorrections.contains(context, t, c) }
        if (!allowed) return false
        // stage 92 (owner's finding 4, "nichtmal" -> "nicbmal"; Codex analysis P1): a target only the owner's passive typing made
        // (typing history, or the personal dictionary, which keeps typos automatically when that option is on) is no evidence for
        // a silent replacement: it stays a suggestion unless a main dictionary or the built-in informal list knows it.
        if (passiveOnlyTarget(first, results)) return false
        // stage 101 (B, owner: "God" -> "Tod" on the German layout): a common word of another enabled language stays
        if (!first.isKindOf(SuggestedWordInfo.KIND_SHORTCUT) && !first.isKindOf(SuggestedWordInfo.KIND_WHITELIST)
            && isCommonWordOfOtherLanguage(typed, correction, ngramContext)) return false
        // stage 60: asked last, only when the correction would be applied; a correction without a locale stays outside
        val correctionLocale = first.mSourceDict.mLocale ?: return true
        return !sentenceIsOfOtherLanguage(typed, ngramContext, correctionLocale)
    }

    /**
     * Fork, stage 101 (A): [typed] is a regular form of a verb of the bundled German verb list ([GermanVerbForms]) that the German
     * main dictionary does not know ("übermittle", "marschiere", "faselte", "fiedel"). Only with German as the keyboard language;
     * a capital is allowed only as the first letter (beginning of a sentence).
     */
    private fun isUnknownGermanVerbForm(typed: String): Boolean {
        if (typed.length < GermanVerbForms.MIN_LETTERS || mDictionaryFacilitator.mainLocale.language != "de") return false
        val lower = typed.lowercase(Locale.GERMAN)
        if (typed.drop(1) != lower.drop(1)) return false
        val context = Settings.getCurrentContext() ?: return false
        if (!GermanVerbForms.isRegularForm(lower) { GermanVerbLemmas.contains(context, it) }) return false
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return true
        return facilitator.mainFrequency(typed, "de") < 0 && facilitator.mainFrequency(lower, "de") < 0
    }

    /**
     * Fork, stage 110: once per app version and keyboard language, takes the learned words that a rule class corrects now out of
     * the own words, kept foreign words, personal dictionary and typing history ([helium314.keyboard.agent.RuleClassCleanup]).
     * Waits for a loaded main dictionary; cheap once done.
     */
    private fun startRuleClassCleanup() {
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return
        if (!facilitator.hasAtLeastOneInitializedMainDictionary()) return
        val context = Settings.getCurrentContext() ?: return
        val language = facilitator.mainLocale.language
        // the other enabled languages must be open: a word one of them knows is not a typo of this one
        val ready = {
            val active = activeLanguages()
            val wanted = ForeignWords.enabledLocales().map { it.language }.filter { it.isNotEmpty() && it !in active }.toSet()
            OtherLanguageDictionaries.languages(context, active).toSet().containsAll(wanted)
        }
        helium314.keyboard.agent.RuleClassCleanup.maybeRun(context, language, ready, { word -> ruleClassTarget(word, language) }) {
            facilitator.removeFromUserHistory(it)
        }
    }

    /**
     * Fork, stage 110: the correction a rule class makes of [word] typed in lower case on the keyboard of [language], or null.
     * The same classes and exclusions as while typing, without the typing state: the short forms ("gibts" -> "gibt's", stage
     * 108), a very common word with one neighbouring key and two swapped letters (stages 101, 75), each only with its switch.
     * The learned stores themselves (own words, foreign words, personal dictionary, history) do not count; a main dictionary
     * of an active or enabled language that knows the word in any case form does, and so do slang and German verb forms.
     */
    internal fun ruleClassTarget(word: String, language: String): String? {
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return null
        val locale = facilitator.mainLocale
        if (locale.language != language || !facilitator.hasAtLeastOneInitializedMainDictionary()) return null
        val settings = Settings.getValues() ?: return null
        val context = Settings.getCurrentContext() ?: return null
        // a word kept with a capital (a name) is left alone; rule classes correct lower case words
        if (word.isEmpty() || word.first().isUpperCase() || !word.all { it.isLetter() }) return null
        val lower = word.lowercase(locale)
        if (listOf(lower, lower.replaceFirstChar { it.uppercase(locale) }, lower.uppercase(locale))
                .any { facilitator.mainLocalesKnowing(it).isNotEmpty() }) return null
        for (other in OtherLanguageDictionaries.languages(context, activeLanguages()))
            if (OtherLanguageDictionaries.frequencyOfForms(other, lower) >= 0) return null
        if (settings.mContextualAutocorrection && language == "de") {
            GermanShortForms.target(lower, { facilitator.mainFrequency(it, "de") >= 0 }) { GermanVerbLemmas.contains(context, it) }
                ?.let { return it }
        }
        if (isUnknownGermanVerbForm(lower)) return null
        if (helium314.keyboard.agent.InformalWordsStore.protects(context, lower, language, language)) return null
        if (settings.mContextualAutocorrection)
            NeighborKeys.candidate(lower, language) { facilitator.mainFrequency(it, language) }?.let { return it }
        if (settings.mSwappedLettersCorrection)
            SwappedLetters.candidate(lower, language, { false }) { facilitator.mainFrequency(it, language) }?.let { return it }
        return null
    }

    /** Fork, stage 101: the languages the engine uses now (keyboard language and the layout's second languages). */
    private fun activeLanguages(): Set<String> =
        (listOf(mDictionaryFacilitator.mainLocale) + Settings.getValues().mSecondaryLocales.orEmpty()).mapTo(HashSet()) { it.language }

    /**
     * Fork, stage 101: the highest frequency (0..255, below zero unknown) of [word] as written, in lower case or with a capital
     * first letter in the main dictionary of [language], whether the engine uses it now or it is another enabled language
     * ([OtherLanguageDictionaries], unknown until it is loaded).
     */
    private fun frequencyIn(word: String, language: String): Int {
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl
        val locale = Locale.forLanguageTag(language)
        val lower = word.lowercase(locale)
        val active = listOf(word, lower, lower.replaceFirstChar { it.titlecase(locale) }).distinct()
            .maxOf { facilitator?.mainFrequency(it, language) ?: Dictionary.NOT_A_PROBABILITY }
        return max(active, OtherLanguageDictionaries.frequencyOfForms(language, word))
    }

    /**
     * Fork, stage 101: how many of the previous words of the sentence (at most three, up to its beginning) count for the
     * keyboard language [keyboard] and how many for [other] ([SentenceLanguage.evidenceOfFrequencies]).
     */
    private fun sentenceLean(ngramContext: NgramContext, keyboard: String, other: String): Pair<Int, Int> {
        var forKeyboard = 0
        var forOther = 0
        for (n in 1..min(SentenceLanguage.MAX_PREVIOUS_WORDS, ngramContext.prevWordCount)) {
            if (ngramContext.isNthPrevWordBeginningOfSentence(n)) break
            val word = ngramContext.getNthPrevWord(n)?.toString()?.takeIf { it.isNotEmpty() } ?: break
            when (SentenceLanguage.evidenceOfFrequencies(frequencyIn(word, keyboard), frequencyIn(word, other))) {
                SentenceLanguage.Evidence.ONLY_L1 -> forKeyboard++
                SentenceLanguage.Evidence.ONLY_L2 -> forOther++
                SentenceLanguage.Evidence.NEUTRAL -> {}
            }
        }
        return forKeyboard to forOther
    }

    /**
     * Fork, stage 101 (B): [typed] is unknown to every active main dictionary (as typed, in lower case, with a capital first
     * letter) but a common word of another of the owner's enabled languages ([OtherLanguageWord]): its replacement by
     * [correction] stays a suggestion. Asked only when a correction is about to be applied.
     */
    private fun isCommonWordOfOtherLanguage(typed: String, correction: String, ngramContext: NgramContext): Boolean {
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return false
        if (typed.length < 2 || !typed.all { it.isLetter() || it == '\'' || it == '’' || it == '-' }) return false
        if (OtherLanguageWord.isAccentTwin(typed, correction)) return false
        val others = OtherLanguageDictionaries.languages(Settings.getCurrentContext(), activeLanguages())
        if (others.isEmpty()) return false
        val locale = facilitator.mainLocale
        if (listOf(typed, typed.replaceFirstChar { it.titlecase(locale) }).any { facilitator.mainLocalesKnowing(it).isNotEmpty() }) return false
        val keyboard = locale.language
        for (other in others) {
            val frequency = OtherLanguageDictionaries.frequencyOfForms(other, typed)
            if (frequency < 0) continue
            val (forKeyboard, forOther) = sentenceLean(ngramContext, keyboard, other)
            if (OtherLanguageWord.protects(frequency, false, forKeyboard, forOther)) return true
        }
        return false
    }

    /**
     * Fork, stage 101 (owner: "the i in English is still lower case"): puts "I" ("I'm", "I'll", "I'd", "I've") first for the
     * typed lower case form and returns true ([EnglishI]): on the English keyboard when "Richtige Wörter nie ändern" blocks the
     * dictionary's whitelist entry (otherwise the engine sets it itself), on another keyboard only with
     * English active or enabled and an English sentence so far. With "satzbezogene Autokorrektur" on, like the verb rule.
     */
    private fun applyEnglishI(typed: String, ngramContext: NgramContext, suggestions: ArrayList<SuggestedWordInfo>,
                              isCorrectionEnabled: Boolean, wordComposer: WordComposer): Boolean {
        val settings = Settings.getValues()
        if (!settings.mContextualAutocorrection || !isCorrectionEnabled || !wordComposer.isComposingWord || wordComposer.isResumed
            || wordComposer.isCursorFrontOrMiddleOfComposingWord
            || helium314.keyboard.agent.KeyboardCommands.suppressesCorrection()
            || !mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()) return false
        val wanted = EnglishI.capitalized(typed) ?: return false
        val context = Settings.getCurrentContext()
        val keyboard = mDictionaryFacilitator.mainLocale.language
        // on the English keyboard the dictionary's own whitelist entry sets "I"; only "Richtige Wörter nie ändern" blocks it there
        // (the dictionary knows "i" as a word), so the rule steps in only then and leaves the engine's behaviour alone otherwise
        if (keyboard == "en" && !settings.mKeepValidWords) return false
        if (keyboard != "en") {
            val active = activeLanguages()
            if ("en" !in active && "en" !in OtherLanguageDictionaries.languages(context, active)) return false
            val (forKeyboard, forEnglish) = sentenceLean(ngramContext, keyboard, "en")
            if (!EnglishI.applies(keyboard, forEnglish, forKeyboard)) return false
        }
        if (helium314.keyboard.agent.Memory.isOwnWord(context, typed)) return false
        val index = suggestions.indexOfFirst { it.mWord == wanted }
        val info = if (index >= 0) suggestions.removeAt(index)
            else SuggestedWordInfo(wanted, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION,
                ENGLISH_I_DICTIONARY, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        suggestions.add(0, info)
        return true
    }

    /**
     * Fork, stage 101 (D, owner: "icj" stays): puts the word that [typed] becomes by replacing one letter with a neighbouring
     * key first and returns true ([NeighborKeys]: 3 to 5 letters, exactly one very common result in the keyboard language).
     * Only for a typed word that nothing deliberate knows: no main dictionary of an active language in any case (also not as
     * an abbreviation), no personal dictionary, contacts or apps, no dictionary of another enabled language; the typing
     * history does not count (it keeps typos). The same exclusions as the swapped letters (own words, slang, kept words of
     * another language, undone corrections, verb forms), not in a sentence that leans to another language, and the
     * engine's own other correction wins. With "satzbezogene Autokorrektur" on.
     */
    private fun applyNeighborKey(
        typed: String, capsMode: CapsMode, ngramContext: NgramContext, results: SuggestionResults,
        suggestions: ArrayList<SuggestedWordInfo>, isCorrectionEnabled: Boolean, wordComposer: WordComposer,
        engineCorrection: String?, followingText: () -> CharSequence?,
    ): Boolean {
        val settings = Settings.getValues()
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return false
        if (!settings.mContextualAutocorrection || !isCorrectionEnabled || !wordComposer.isComposingWord
            || typed.length < NeighborKeys.MIN_LETTERS || typed.length > NeighborKeys.MAX_LETTERS
            || wordComposer.isResumed || wordComposer.hasDigits() || wordComposer.isCursorFrontOrMiddleOfComposingWord
            || (wordComposer.isMostlyCaps && !wordComposer.isAllUpperCase)
            || helium314.keyboard.agent.KeyboardCommands.isCommandWord(typed)
            || helium314.keyboard.agent.KeyboardCommands.suppressesCorrection()
            || !facilitator.hasAtLeastOneInitializedMainDictionary()) return false
        // a capital first letter only at the beginning of a sentence (a name inside a sentence stays)
        if (typed.first().isUpperCase() && !ngramContext.isBeginningOfSentenceContext) return false
        val first = suggestions.firstOrNull()
        if (first != null && (first.isKindOf(SuggestedWordInfo.KIND_SHORTCUT) || first.isKindOf(SuggestedWordInfo.KIND_WHITELIST))) return false
        if (first?.mWord.equals(typed, ignoreCase = true)) return false
        val locale = facilitator.mainLocale
        val language = locale.language
        val meant = NeighborKeys.candidate(typed, language) { facilitator.mainFrequency(it, language) } ?: return false
        if (engineCorrection != null && !engineCorrection.equals(meant, ignoreCase = true)) return false
        val known = listOf(typed, typed.replaceFirstChar { it.uppercase(locale) }, typed.uppercase(locale))
            .any { facilitator.mainLocalesKnowing(it).isNotEmpty() } ||
            results.any {
                it.mWord.equals(typed, ignoreCase = true) && (it.mSourceDict.mDictType == Dictionary.TYPE_MAIN
                    || it.mSourceDict.mDictType == Dictionary.TYPE_USER || it.mSourceDict.mDictType == Dictionary.TYPE_CONTACTS
                    || it.mSourceDict.mDictType == Dictionary.TYPE_APPS)
            }
        if (known) return false
        val context = Settings.getCurrentContext()
        if (helium314.keyboard.agent.Memory.isOwnWord(context, typed)) return false
        val learnLanguage = ForeignWords.languageOf(settings)
        if (helium314.keyboard.agent.InformalWordsStore.protects(context, typed, learnLanguage, language)) return false
        if (ForeignWords.isRemembered(context, settings, learnLanguage, typed)) return false
        if (isUnknownGermanVerbForm(typed)) return false
        for (other in OtherLanguageDictionaries.languages(context, activeLanguages())) {
            if (OtherLanguageDictionaries.frequencyOfForms(other, typed) >= 0) return false
            val (forKeyboard, forOther) = sentenceLean(ngramContext, language, other)
            if (forOther > forKeyboard) return false
        }
        if (settings.mRememberRejectedCorrections && context != null && RejectedCorrections.contains(context, typed, meant)) return false
        val wanted = capitalize(meant, capsMode, locale)
        if (learnLanguage == "de" && preferredGermanVerb(typed, wanted, suggestions, results, ngramContext, followingText) != null) return false
        val index = suggestions.indexOfFirst { it.mWord == wanted }
        val info = if (index >= 0) suggestions.removeAt(index)
            else SuggestedWordInfo(wanted, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION,
                NEIGHBOR_KEY_DICTIONARY, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        suggestions.add(0, info)
        return true
    }

    /** Fork, stages 107/108: the common exclusions of the rule corrections (typing state, commands, a loaded dictionary). */
    private fun ruleMayCorrect(typed: String, isCorrectionEnabled: Boolean, wordComposer: WordComposer): Boolean {
        val settings = Settings.getValues()
        return settings.mContextualAutocorrection && isCorrectionEnabled && typed.isNotEmpty() && wordComposer.isComposingWord
            && !wordComposer.isResumed && !wordComposer.hasDigits() && !wordComposer.isCursorFrontOrMiddleOfComposingWord
            && !(wordComposer.isMostlyCaps && !wordComposer.isAllUpperCase)
            && !helium314.keyboard.agent.KeyboardCommands.isCommandWord(typed)
            && !helium314.keyboard.agent.KeyboardCommands.suppressesCorrection()
            && mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()
    }

    /** Fork, stages 107/108: whether the sentence so far leans to another active or enabled language than the keyboard's. */
    private fun sentenceLeansToOtherLanguage(ngramContext: NgramContext, keyboard: String): Boolean {
        val others = (activeLanguages() - keyboard) + OtherLanguageDictionaries.languages(Settings.getCurrentContext(), activeLanguages())
        return others.distinct().any { other -> sentenceLean(ngramContext, keyboard, other).let { (k, o) -> o > k } }
    }

    /** Fork, stages 107/108: puts [wanted] first (the engine's entry when it has one, else a new one from [source]) and remembers the pair. */
    private fun putRuleCorrectionFirst(typed: String, wanted: String, suggestions: ArrayList<SuggestedWordInfo>, source: Dictionary) {
        val index = suggestions.indexOfFirst { it.mWord == wanted }
        val info = if (index >= 0) suggestions.removeAt(index)
            else SuggestedWordInfo(wanted, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_CORRECTION,
                source, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        suggestions.add(0, info)
        helium314.keyboard.agent.RuleCorrections.offered(typed, wanted)
    }

    /**
     * Fork, stage 108 (owner: "gibt's, wird's, dreht's … werden nur vorgeschlagen"): a German verb form typed with "s" for "es"
     * and without the apostrophe becomes "gibt's" ([GermanShortForms]). Only on the German keyboard, when no active main
     * dictionary, contacts or apps know the typed word in any case (nichts, rechts, Amts, Guts stay; the typing history and the
     * personal dictionary do not count: they keep such forms automatically), a capital only at the beginning of a sentence, not
     * in a sentence that leans to another language ("wars" in an English sentence), not for an own word, a kept word of another
     * language or an undone pair. The slang list does not stop it (the owner's wish, as planned in stage 90). Also with
     * "Richtige Wörter nie ändern": the typed word is no word.
     */
    private fun applyShortForm(
        typed: String, capsMode: CapsMode, ngramContext: NgramContext, results: SuggestionResults,
        suggestions: ArrayList<SuggestedWordInfo>, isCorrectionEnabled: Boolean, wordComposer: WordComposer,
    ): Boolean {
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return false
        if (facilitator.mainLocale.language != "de" || typed.length <= GermanShortForms.MIN_LETTERS || !typed.endsWith("s")) return false
        if (!ruleMayCorrect(typed, isCorrectionEnabled, wordComposer)) return false
        val lower = typed.lowercase(Locale.GERMAN)
        if (typed.drop(1) != lower.drop(1)) return false
        if (typed.first().isUpperCase() && !ngramContext.isBeginningOfSentenceContext) return false
        val context = Settings.getCurrentContext() ?: return false
        val meant = GermanShortForms.target(lower, { facilitator.mainFrequency(it, "de") >= 0 }) { GermanVerbLemmas.contains(context, it) }
            ?: return false
        val locale = facilitator.mainLocale
        val known = listOf(lower, lower.replaceFirstChar { it.uppercase(locale) }, lower.uppercase(locale))
            .any { facilitator.mainLocalesKnowing(it).isNotEmpty() } ||
            results.any {
                it.mWord.equals(typed, ignoreCase = true) && (it.mSourceDict.mDictType == Dictionary.TYPE_MAIN
                    || it.mSourceDict.mDictType == Dictionary.TYPE_CONTACTS || it.mSourceDict.mDictType == Dictionary.TYPE_APPS)
            }
        if (known) return false
        val settings = Settings.getValues()
        if (helium314.keyboard.agent.Memory.isOwnWord(context, typed)) return false
        if (ForeignWords.isRemembered(context, settings, ForeignWords.languageOf(settings), typed)) return false
        if (sentenceLeansToOtherLanguage(ngramContext, "de")) return false
        if (RejectedCorrections.contains(context, typed, meant)) return false
        putRuleCorrectionFirst(typed, capitalize(meant, capsMode, locale), suggestions, SHORT_FORM_DICTIONARY)
        return true
    }

    /**
     * Fork, stage 107 (owner: "mur" -> "nur", "aif" -> "auf" by weights): a typed word of 3 to 5 letters that the main dictionary
     * of the keyboard language knows only rarely (any case form, also only as "AIF") becomes its one very common neighbouring-key
     * word ([NeighborKeys], or the engine's correction when it is that word) when the owner's weights say so ([RareWordWeights]:
     * typing history of both words, or without a usable history the next-word scores of this sentence [nextWords]). Also with
     * "Richtige Wörter nie ändern" (that is its purpose). Not for own words, slang, kept words of another language, a common
     * word of another active or enabled language, a sentence leaning to another language, a capital inside a sentence, and never
     * again after Backspace undid it ([RejectedCorrections], also without its switch). With "satzbezogene Autokorrektur" on.
     */
    private fun applyRareWord(
        typed: String, capsMode: CapsMode, ngramContext: NgramContext, results: SuggestionResults,
        suggestions: ArrayList<SuggestedWordInfo>, isCorrectionEnabled: Boolean, wordComposer: WordComposer,
        engineCorrection: String?, nextWords: () -> SuggestionResults,
    ): Boolean {
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return false
        // only letters (asked before any lookup: a word it cannot change costs nothing)
        if (typed.length < NeighborKeys.MIN_LETTERS || typed.length > NeighborKeys.MAX_LETTERS || !typed.all { it.isLetter() }) return false
        if (typed.drop(1).any { it.isUpperCase() }) return false
        if (!ruleMayCorrect(typed, isCorrectionEnabled, wordComposer)) return false
        if (typed.first().isUpperCase() && !ngramContext.isBeginningOfSentenceContext) return false
        val first = suggestions.firstOrNull()
        if (first != null && (first.isKindOf(SuggestedWordInfo.KIND_SHORTCUT) || first.isKindOf(SuggestedWordInfo.KIND_WHITELIST))) return false
        val locale = facilitator.mainLocale
        val language = locale.language
        val lower = typed.lowercase(locale)
        val forms = listOf(typed, lower, lower.replaceFirstChar { it.uppercase(locale) }, lower.uppercase(locale)).distinct()
        val typedFrequency = forms.maxOf { facilitator.mainFrequency(it, language) }
        if (typedFrequency < 0 || typedFrequency > RareWordWeights.RARE_MAX) return false
        val minimum = RareWordWeights.minCandidate(language)
        val meant = NeighborKeys.candidate(typed, language, minimum) { facilitator.mainFrequency(it, language) }
            ?: engineCorrection?.lowercase(locale)?.takeIf { it in NeighborKeys.replacements(lower, language) } ?: return false
        if (engineCorrection != null && !engineCorrection.equals(meant, ignoreCase = true)) return false
        val context = Settings.getCurrentContext()
        val settings = Settings.getValues()
        if (helium314.keyboard.agent.Memory.isOwnWord(context, typed)) return false
        val learnLanguage = ForeignWords.languageOf(settings)
        if (helium314.keyboard.agent.InformalWordsStore.protects(context, typed, learnLanguage, language)) return false
        if (ForeignWords.isRemembered(context, settings, learnLanguage, typed)) return false
        // a common word of another active or enabled language (English "nut") stays, as in stage 101 (B)
        val others = ((activeLanguages() - language) + OtherLanguageDictionaries.languages(context, activeLanguages())).distinct()
        for (other in others) {
            val (forKeyboard, forOther) = sentenceLean(ngramContext, language, other)
            if (forOther > forKeyboard) return false
            if (OtherLanguageWord.protects(frequencyIn(typed, other), false, forKeyboard, forOther)) return false
        }
        val rejected = context != null && RejectedCorrections.contains(context, typed, meant)
        val historyTyped = forms.maxOf { facilitator.historyFrequency(it, language) }
        val historyCandidate = facilitator.historyFrequency(meant, language)
        val predicted = nextWords()
        val contextCandidate = predicted.firstOrNull { it.mWord == meant }?.mScore
        val contextTyped = predicted.filter { it.mWord.equals(typed, ignoreCase = true) }.maxOfOrNull { it.mScore }
        if (!RareWordWeights.corrects(typedFrequency, facilitator.mainFrequency(meant, language), language, rejected,
                facilitator.hasHistory(language), historyTyped, historyCandidate, contextCandidate, contextTyped)) return false
        putRuleCorrectionFirst(typed, capitalize(meant, capsMode, locale), suggestions, RARE_WORD_DICTIONARY)
        return true
    }

    /** Fork, stage 92: [first] comes from the typing history or the personal dictionary only, and no main dictionary or informal list knows it. */
    private fun passiveOnlyTarget(first: SuggestedWordInfo, results: Collection<SuggestedWordInfo>): Boolean {
        val type = first.mSourceDict.mDictType
        if (type != Dictionary.TYPE_USER_HISTORY && type != Dictionary.TYPE_USER) return false
        if (first.isKindOf(SuggestedWordInfo.KIND_WHITELIST) || first.isKindOf(SuggestedWordInfo.KIND_SHORTCUT)) return false
        val word = first.mWord
        if (results.any { it.mSourceDict.mDictType == Dictionary.TYPE_MAIN && it.mWord.equals(word, ignoreCase = true) }) return false
        if ((mDictionaryFacilitator as? DictionaryFacilitatorImpl)?.mainLocalesKnowing(word)?.isNotEmpty() == true) return false
        val language = first.mSourceDict.mLocale?.language
        return !helium314.keyboard.agent.InformalWordsStore.protects(Settings.getCurrentContext(), word, language, ForeignWords.languageOf(Settings.getValues()))
    }

    /**
     * Fork, stage 60: whether the previous words of this sentence (at most three, up to the beginning of
     * the sentence, from [ngramContext]) show that it is not written in the language of the correction
     * ([correctionLocale]): at least one is known to a language that also knows [typed] (and not to the
     * correction's), none only to the correction's language (main dictionaries, [SentenceLanguage]). Runs on the suggestion thread, reads
     * only, and only when a correction is about to be applied ([typed] must be a main-dictionary word of another
     * active language and not of the correction's).
     */
    private fun sentenceIsOfOtherLanguage(typed: String, ngramContext: NgramContext, correctionLocale: Locale): Boolean {
        val facilitator = mDictionaryFacilitator as? DictionaryFacilitatorImpl ?: return false
        // the typed word is a word of another active language and not one of the correction's (asked of the
        // main dictionaries, not of the first occurrence in the results, which may be a history entry)
        val typedKnownIn = facilitator.mainLocalesKnowing(typed)
        if (typedKnownIn.none { it != correctionLocale } || correctionLocale in typedKnownIn) return false
        var onlyL2 = 0
        for (n in 1..min(SentenceLanguage.MAX_PREVIOUS_WORDS, ngramContext.prevWordCount)) {
            if (ngramContext.isNthPrevWordBeginningOfSentence(n)) break
            val word = ngramContext.getNthPrevWord(n)?.toString()?.takeIf { it.isNotEmpty() } ?: break
            val known = facilitator.mainLocalesKnowing(word)
            // evidence for the typed word's language: a language that knows the typed word (a third language does not count)
            when (SentenceLanguage.evidenceOf(known.any { it == correctionLocale }, known.any { it != correctionLocale && it in typedKnownIn })) {
                SentenceLanguage.Evidence.ONLY_L1 -> return false // one word of the correction's language: the old rule
                SentenceLanguage.Evidence.ONLY_L2 -> onlyL2++
                SentenceLanguage.Evidence.NEUTRAL -> {}
            }
        }
        return SentenceLanguage.blocksCorrection(0, onlyL2) // a word of L1 has returned above
    }

    // returns [allowsToBeAutoCorrected, hasAutoCorrection]
    // public for testing
    // todo: now we can do better tests, maybe make it private again and test via getSuggestedWords (and simplify if possible)
    fun shouldBeAutoCorrected(
        trailingSingleQuotesCount: Int,
        typedWordString: String,
        firstSuggestionInContainer: SuggestedWordInfo?,
        getEmptyWordSuggestions: () -> Pair<SuggestedWordInfo?, SuggestedWordInfo?>,
        isCorrectionEnabled: Boolean,
        wordComposer: WordComposer,
        suggestionResults: SuggestionResults,
        firstOccurrenceOfTypedWordInSuggestions: Int,
        typedWordInfo: SuggestedWordInfo?
    ): Pair<Boolean, Boolean> {
        val consideredWord = typedWordString.dropLast(trailingSingleQuotesCount)
        val firstAndTypedEmptyInfos by lazy { getEmptyWordSuggestions() }

        val scoreLimit = Settings.getValues().mScoreLimitForAutocorrect
        // We allow auto-correction if whitelisting is not required or the word is whitelisted,
        // or if the word had more than one char and was not suggested.
        val allowsToBeAutoCorrected: Boolean
        if (SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION
                || firstSuggestionInContainer?.isKindOf(SuggestedWordInfo.KIND_WHITELIST) == true
                || (consideredWord.length > 1
                    && typedWordInfo?.mSourceDict == null // more than 1 letter and not in dictionary
                    // if the typed word contains @ or ., the suggestion also needs to contain it
                    // (avoid autocorrecting mail addresses, URLs & similar to something different)
                    && (!typedWordString.contains('@') || firstSuggestionInContainer?.mWord?.contains('@') == true)
                    && (!typedWordString.contains('.') || firstSuggestionInContainer?.mWord?.contains('.') == true)
                    )
            ) {
            allowsToBeAutoCorrected = true
        } else if (firstSuggestionInContainer != null && typedWordString.isNotEmpty()) {
            // maybe allow autocorrect, depending on scores and emptyWordSuggestions
            val first = firstAndTypedEmptyInfos.first
            val typed = firstAndTypedEmptyInfos.second
            allowsToBeAutoCorrected = when {
                firstSuggestionInContainer.mScore > scoreLimit -> true // suggestion has good score, allow
                first == null -> false // no autocorrect if first suggestion unknown in this ngram context
                typed == null -> true // allow autocorrect if typed word not known in this ngram context, todo: this may be too aggressive
                else -> first.mScore - typed.mScore > 20 // autocorrect if suggested word has clearly higher score for empty word suggestions
            }
        } else {
            allowsToBeAutoCorrected = false
        }
        // If correction is not enabled, we never auto-correct. This is for example for when
        // the setting "Auto-correction" is "off": we still suggest, but we don't auto-correct.
        val hasAutoCorrection: Boolean
        if (!isCorrectionEnabled
            || !allowsToBeAutoCorrected // If the word does not allow to be auto-corrected, then we don't auto-correct.
            || !wordComposer.isComposingWord // If we are doing prediction, then we never auto-correct of course
            || suggestionResults.isEmpty() // If we don't have suggestion results, we can't evaluate the first suggestion for auto-correction
            || wordComposer.hasDigits() // If the word has digits, we never auto-correct because it's likely the word was type with a lot of care // todo: but what if user touched the number row?
            || (wordComposer.isMostlyCaps && !wordComposer.isAllUpperCase) // If the word is mostly caps, we never auto-correct because this is almost certainly intentional
            // fork, stage 91: acronyms and exchanged German prefixes stay a suggestion (rules of AdaptKey)
            || helium314.keyboard.agent.CorrectionGuards.blocks(typedWordString, firstSuggestionInContainer?.mWord, firstSuggestionInContainer?.mSourceDict?.mLocale)
            // fork, stage 101 (C): no silent "aif" -> "AIF" or "lame" -> "Lamé" (unless a shortcut or the dictionary's whitelist wants it)
            || helium314.keyboard.agent.CorrectionGuards.blocksTarget(typedWordString, firstSuggestionInContainer?.mWord,
                firstSuggestionInContainer?.mSourceDict?.mLocale, firstSuggestionInContainer?.let {
                    it.isKindOf(SuggestedWordInfo.KIND_WHITELIST) || it.isKindOf(SuggestedWordInfo.KIND_SHORTCUT) } == true)
            || wordComposer.isResumed // We never auto-correct when suggestions are resumed because it would be unexpected
            // a typed command like "@math" and the words of its argument stay as typed
            || helium314.keyboard.agent.KeyboardCommands.isCommandWord(typedWordString)
            || helium314.keyboard.agent.KeyboardCommands.suppressesCorrection()
            // If we don't have a main dictionary, we never want to auto-correct. The reason
            // for this is, the user may have a contact whose name happens to match a valid
            // word in their language, and it will unexpectedly auto-correct. For example, if
            // the user types in English with no dictionary and has a "Will" in their contact
            // list, "will" would always auto-correct to "Will" which is unwanted. Hence, no
            // main dict => no auto-correct. Also, it would probably get obnoxious quickly.
            // TODO: now that we have personalization, we may want to re-evaluate this decision
            || !mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()
        ) {
            hasAutoCorrection = false
        } else {
            val firstSuggestion = firstSuggestionInContainer ?: suggestionResults.first()
            if (suggestionResults.mFirstSuggestionExceedsConfidenceThreshold && firstOccurrenceOfTypedWordInSuggestions != 0) {
                // mFirstSuggestionExceedsConfidenceThreshold is always set to false, so currently this branch is useless
                return true to true
            }
            if (!AutoCorrectionUtils.suggestionExceedsThreshold(firstSuggestion, consideredWord, mAutoCorrectionThreshold)) {
                // Score is too low for autocorrect
                // todo: maybe also do something here depending on ngram context?
                return true to false
            }
            // We have a high score, so we need to check if this suggestion is in the correct
            // form to allow auto-correcting to it in this language. For details of how this
            // is determined, see #isAllowedByAutoCorrectionWithSpaceFilter.
            val allowed = isAllowedByAutoCorrectionWithSpaceFilter(firstSuggestion)
            if (allowed && typedWordInfo != null && typedWordInfo.mScore > scoreLimit) {
                // typed word is valid and has good score
                // do not auto-correct if typed word is better match than first suggestion
                val dictLocale = mDictionaryFacilitator.currentLocale
                if (firstSuggestion.mScore < scoreLimit) {
                    // don't allow if suggestion has too low score
                    return true to false
                }
                if (firstSuggestion.mSourceDict.mLocale !== typedWordInfo.mSourceDict.mLocale) {
                    // dict locale different -> return the better match
                    return true to (dictLocale == firstSuggestion.mSourceDict.mLocale)
                }
                // the score difference may need tuning, but so far it seems alright
                val firstWordBonusScore =
                    ((if (firstSuggestion.isKindOf(SuggestedWordInfo.KIND_WHITELIST)) 20 else 0) // large bonus because it's wanted by dictionary
                            + (if (StringUtils.isLowerCaseAscii(typedWordString)) 5 else 0) // small bonus because typically only lower case ascii is typed (applies to latin keyboards only)
                            + if (firstSuggestion.mScore > typedWordInfo.mScore) 5 else 0) // small bonus if score is higher
                val firstScoreForEmpty = firstAndTypedEmptyInfos.first?.mScore ?: 0
                val typedScoreForEmpty = firstAndTypedEmptyInfos.second?.mScore ?: 0
                if (firstScoreForEmpty + firstWordBonusScore >= typedScoreForEmpty + 20) {
                    // first word is clearly better match for this ngram context
                    return true to true
                }
                hasAutoCorrection = false
            } else {
                hasAutoCorrection = allowed
            }
        }
        return allowsToBeAutoCorrected to hasAutoCorrection
    }

    // Retrieves suggestions for the batch input
    // and calls the callback function with the suggestions.
    private fun getSuggestedWordsForBatchInput(
        wordComposer: WordComposer,
        ngramContext: NgramContext, keyboard: Keyboard,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        inputStyle: Int, isCorrectionEnabled: Boolean, sequenceNumber: Int
    ): SuggestedWords {
        val suggestionResults = mDictionaryFacilitator.getSuggestionResults(
            wordComposer.composedDataSnapshot, ngramContext, keyboard,
            settingsValuesForSuggestion, SESSION_ID_GESTURE, inputStyle
        )

        // For transforming words that don't come from a dictionary, because it's our best bet
        val locale = mDictionaryFacilitator.mainLocale
        val capsMode = getCapsModeForGesture(wordComposer, keyboard)
        val suggestionsContainer = ArrayList(suggestionResults)
        replaceSingleLetterFirstSuggestion(suggestionsContainer)

        val rejected: SuggestedWordInfo?
        if (SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION && suggestionsContainer.size > 1 && TextUtils.equals(
                suggestionsContainer[0].mWord,
                wordComposer.rejectedBatchModeSuggestion
            )
        ) {
            rejected = suggestionsContainer.removeAt(0)
            suggestionsContainer.add(1, rejected)
        } else {
            rejected = null
        }
        SuggestedWordInfo.removeDupsAndTypedWord(null, suggestionsContainer)
        makeFirstTwoSuggestionsNonEmoji(suggestionsContainer)
        val pseudoTypedWord = suggestionsContainer.firstOrNull() // unchanged first suggestion, but considering adjusted order
        capitalizeAndAddTrailingSingleQuotes(suggestionsContainer, capsMode, 0, locale)

        // For some reason some suggestions with MIN_VALUE are making their way here.
        // TODO: Find a more robust way to detect distracters.
        for (i in suggestionsContainer.indices.reversed()) {
            if (suggestionsContainer[i].mScore < SUPPRESS_SUGGEST_THRESHOLD) {
                suggestionsContainer.removeAt(i)
            }
        }

        val capitalizedTypedWord = capitalize(wordComposer.typedWord, capsMode, locale)
        val addCapitalizedSuggestion = capitalizedTypedWord != wordComposer.typedWord && suggestionsContainer.drop(1).none { it.mWord == capitalizedTypedWord }
        if (addCapitalizedSuggestion) {
            suggestionsContainer.add(min(1, suggestionsContainer.size),
                SuggestedWordInfo(capitalizedTypedWord, "", 0, SuggestedWordInfo.KIND_TYPED,
                    Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
            )
        }

        useDefaultEmojiSkinTone(suggestionsContainer)

        // In the batch input mode, the most relevant suggested word should act as a "typed word"
        // (typedWordValid=true), not as an "auto correct word" (willAutoCorrect=false).
        // Exception is when using shift to change capitalization of suggestions.
        // Note that because this method is never used to get predictions, there is no need to
        // modify inputType such in getSuggestedWordsForNonBatchInput.
        val pseudoTypedWordInfo = preferNextWordSuggestion(
            pseudoTypedWord, suggestionsContainer,
            getNextWordSuggestions(ngramContext, keyboard, inputStyle, settingsValuesForSuggestion), rejected
        )
        val suggestionsList = if (SuggestionStripView.DEBUG_SUGGESTIONS && suggestionsContainer.isNotEmpty()) {
            getSuggestionsInfoListWithDebugInfo(suggestionResults.first().mWord, suggestionsContainer)
        } else {
            suggestionsContainer
        }

        if (useBackgroundGathering && inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH) {
            val wordData = WordData(null, suggestionResults, wordComposer.composedDataSnapshot,
                ngramContext, keyboard, inputStyle, false, pseudoTypedWordInfo)
            BackgroundGatheringCache.addWord(wordData)
        }

        val autocorrectCapitalization = addCapitalizedSuggestion && Settings.getValues().mAutoCorrectCapitalizedSuggestion && isCorrectionEnabled && !wordComposer.isCursorFrontOrMiddleOfComposingWord
        return SuggestedWords(suggestionsList, suggestionResults.mRawSuggestions, pseudoTypedWordInfo, true,
            autocorrectCapitalization, false, inputStyle, sequenceNumber)
    }

    private fun useDefaultEmojiSkinTone(suggestionsList: ArrayList<SuggestedWordInfo>) {
        for (i in suggestionsList.indices) {
            suggestionsList[i] = useDefaultEmojiSkinTone(suggestionsList[i])
        }
    }

    /** get suggestions based on the current ngram context, with an empty typed word (that's what next word suggestions do)  */
    private fun getNextWordSuggestions(ngramContext: NgramContext, keyboard: Keyboard, inputStyle: Int,
                                       settingsValuesForSuggestion: SettingsValuesForSuggestion): SuggestionResults {
        val cachedResults = nextWordSuggestionsCache[ngramContext]
        if (cachedResults != null) return cachedResults
        val newResults = mDictionaryFacilitator.getSuggestionResults(ComposedData(InputPointers(1),
            false, ""), ngramContext, keyboard, settingsValuesForSuggestion, SESSION_ID_TYPING, inputStyle)
        nextWordSuggestionsCache[ngramContext] = newResults
        return newResults
    }

    companion object {
        private val TAG: String = Suggest::class.java.simpleName

        // Session id for {@link #getSuggestedWords(WordComposer,String,ProximityInfo,boolean,int)}.
        // We are sharing the same ID between typing and gesture to save RAM footprint.
        const val SESSION_ID_TYPING = 0
        const val SESSION_ID_GESTURE = 0

        /** Fork, stage 75: source of a swapped-letters correction that the engine did not offer itself. */
        private val SWAPPED_LETTERS_DICTIONARY = Dictionary.PhonyDictionary("agent_swapped_letters")
        /** Fork, stage 101: sources of a neighbouring-key correction and of the English "I" that the engine did not offer itself. */
        private val NEIGHBOR_KEY_DICTIONARY = Dictionary.PhonyDictionary("agent_neighbor_key")
        private val ENGLISH_I_DICTIONARY = Dictionary.PhonyDictionary("agent_english_i")
        /** Fork, stages 107/108: sources of a rare-word and of a short-form correction that the engine did not offer itself. */
        private val RARE_WORD_DICTIONARY = Dictionary.PhonyDictionary("agent_rare_word")
        private val SHORT_FORM_DICTIONARY = Dictionary.PhonyDictionary("agent_short_form")

        // Close to -2**31
        private const val SUPPRESS_SUGGEST_THRESHOLD = -2000000000

        private const val MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN = 12
        // TODO: should we add Finnish here?
        private val sLanguageToMaximumAutoCorrectionWithSpaceLength = hashMapOf(Locale.GERMAN.language to MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN)

        private fun capitalizeAndAddTrailingSingleQuotes(
            suggestions: ArrayList<SuggestedWordInfo>, capsMode: CapsMode, trailingSingleQuotesCount: Int, defaultLocale: Locale
        ) {
            val suggestionsCount = suggestions.size
            if (capsMode != CapsMode.OFF || 0 != trailingSingleQuotesCount) {
                for (i in 0 until suggestionsCount) {
                    val wordInfo = suggestions[i]
                    val wordLocale = wordInfo.mSourceDict.mLocale
                    val transformedWordInfo = capitalizeAndAddTrailingSingleQuotes(
                        wordInfo, wordLocale ?: defaultLocale, capsMode, trailingSingleQuotesCount
                    )
                    suggestions[i] = transformedWordInfo
                }
            }
        }

        private fun capitalizeAndAddTrailingSingleQuotes(
            wordInfo: SuggestedWordInfo, locale: Locale, capsMode: CapsMode, trailingSingleQuotesCount: Int
        ): SuggestedWordInfo {
            var capitalizedWord = capitalize(wordInfo.mWord, capsMode, locale)
            // Appending quotes is here to help people quote words. However, it's not helpful
            // when they type words with quotes toward the end like "it's" or "didn't", where
            // it's more likely the user missed the last character (or didn't type it yet).
            val quotesToAppend = trailingSingleQuotesCount - if (wordInfo.mWord.contains('\'')) 1 else 0
            repeat(max(0, quotesToAppend)) { capitalizedWord += '\'' }
            return SuggestedWordInfo(
                capitalizedWord, wordInfo.mPrevWordsContext,
                wordInfo.mScore, wordInfo.mKindAndFlags,
                wordInfo.mSourceDict, wordInfo.mIndexOfTouchPointOfSecondWord,
                wordInfo.mAutoCommitFirstWordConfidence
            )
        }

        private fun getSuggestionsInfoListWithDebugInfo(
            typedWord: String, suggestions: ArrayList<SuggestedWordInfo>
        ): ArrayList<SuggestedWordInfo> {
            val suggestionsSize = suggestions.size
            val suggestionsList = ArrayList<SuggestedWordInfo>(suggestionsSize)
            for (cur in suggestions) {
                addDebugInfo(cur, typedWord)
                suggestionsList.add(cur)
            }
            return suggestionsList
        }

        @JvmStatic
        fun addDebugInfo(wordInfo: SuggestedWordInfo?, typedWord: String) {
            if (!SuggestionStripView.DEBUG_SUGGESTIONS)
                return
            val normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(typedWord, wordInfo.toString(), wordInfo!!.mScore)
            val scoreInfoString: String
            val dict = wordInfo.mSourceDict.mDictType + ":" + wordInfo.mSourceDict.mLocale
            scoreInfoString = if (normalizedScore > 0) {
                String.format(Locale.ROOT, "%d (%4.2f), %s", wordInfo.mScore, normalizedScore, dict)
            } else {
                String.format(Locale.ROOT, "%d, %s", wordInfo.mScore, dict)
            }
            wordInfo.debugString = scoreInfoString
        }

        @JvmStatic
        fun useDefaultEmojiSkinTone(suggestion: SuggestedWordInfo): SuggestedWordInfo {
            val defaultVersion = getEmojiDefaultVersion(suggestion.mWord)
            if (defaultVersion == suggestion.mWord) {
                return suggestion
            }

            return SuggestedWordInfo(defaultVersion, suggestion.mPrevWordsContext, suggestion.mScore, suggestion.mKindAndFlags,
                suggestion.mSourceDict, suggestion.mIndexOfTouchPointOfSecondWord, suggestion.mAutoCommitFirstWordConfidence)
        }

        /**
         * Computes whether this suggestion should be blocked or not in this language
         *
         * This function implements a filter that avoids auto-correcting to suggestions that contain
         * spaces that are above a certain language-dependent character limit. In languages like German
         * where it's possible to concatenate many words, it often happens our dictionary does not
         * have the longer words. In this case, we offer a lot of unhelpful suggestions that contain
         * one or several spaces. Ideally we should understand what the user wants and display useful
         * suggestions by improving the dictionary and possibly having some specific logic. Until
         * that's possible we should avoid displaying unhelpful suggestions. But it's hard to tell
         * whether a suggestion is useful or not. So at least for the time being we block
         * auto-correction when the suggestion is long and contains a space, which should avoid the
         * worst damage.
         * This function is implementing that filter. If the language enforces no such limit, then it
         * always returns true. If the suggestion contains no space, it also returns true. Otherwise,
         * it checks the length against the language-specific limit.
         *
         * @param info the suggestion info
         * @return whether it's fine to auto-correct to this.
         */
        private fun isAllowedByAutoCorrectionWithSpaceFilter(info: SuggestedWordInfo): Boolean {
            val locale = info.mSourceDict.mLocale ?: return true
            val maximumLengthForThisLanguage = sLanguageToMaximumAutoCorrectionWithSpaceLength[locale.language]
                ?: return true // This language does not enforce a maximum length to auto-correction
            return (info.mWord.length <= maximumLengthForThisLanguage
                    || -1 == info.mWord.indexOf(Constants.CODE_SPACE.toChar()))
        }

        /** returns CapsMode.MANUAL, CapsMode.MANUAL_LOCKED, or CAPS_MODE_OFF */
        private fun getCapsModeForTyping(wordComposer: WordComposer, keyboard: Keyboard): CapsMode {
            val capsMode = keyboard.mId.element.capsMode
            if (capsMode == CapsMode.MANUAL || capsMode == CapsMode.MANUAL_LOCKED)
                return capsMode
            // we have some auto-mode which we ignore
            // instead we determine mode from the typed word (that's how it was done for a long time, todo: maybe adjust if necessary?)
            if (wordComposer.isAllUpperCase && !wordComposer.isResumed) return CapsMode.MANUAL_LOCKED
            if (wordComposer.isOrWillBeOnlyFirstCharCapitalized) return CapsMode.MANUAL
            return CapsMode.OFF
        }

        /** returns CapsMode.MANUAL, CapsMode.MANUAL_LOCKED, or CAPS_MODE_OFF */
        // maybe could check the details in differences to getCapsModeForTyping and unify?
        private fun getCapsModeForGesture(wordComposer: WordComposer, keyboard: Keyboard): CapsMode {
            val capsMode = keyboard.mId.element.capsMode
            if (capsMode == CapsMode.MANUAL || capsMode == CapsMode.MANUAL_LOCKED)
                return capsMode
            if (wordComposer.isAllUpperCase) return CapsMode.MANUAL_LOCKED
            if (wordComposer.wasShiftedNoLock()) return CapsMode.MANUAL
            return CapsMode.OFF
        }

        private fun capitalize(word: String, capsMode: CapsMode, locale: Locale) = when (capsMode) {
            CapsMode.MANUAL_LOCKED -> word.uppercase(locale)
            CapsMode.MANUAL -> StringUtils.capitalizeFirstCodePoint(word, locale)
            else -> word
        }

        private fun makeFirstTwoSuggestionsNonEmoji(words: MutableList<SuggestedWordInfo>) {
            for (i in 0..1) {
                if (words.size > 2 && words[i].isEmoji) {
                    val relativeIndex = words.subList(2, words.size).indexOfFirst { !it.isEmoji }
                    if (relativeIndex < 0) break
                    val firstNonEmojiIndex = relativeIndex + 2
                    if (firstNonEmojiIndex > i) {
                        words.add(i, words.removeAt(firstNonEmojiIndex))
                    }
                }
            }
        }

        /** reduces score of the first suggestion if next one is close and has more than a single letter */
        private fun replaceSingleLetterFirstSuggestion(suggestionResults: MutableList<SuggestedWordInfo>) {
            if (suggestionResults.size < 2 || suggestionResults.first().mWord.length != 1) return
            // suppress single letter suggestions if next suggestion is close and has more than one letter
            val first = suggestionResults[0]
            val second = suggestionResults[1]
            if (second.mWord.length > 1 && second.mScore > 0.94 * first.mScore) {
                suggestionResults.remove(first) // remove and re-add with lower score
                val modifiedFirst = SuggestedWordInfo(
                    first.mWord, first.mPrevWordsContext, (first.mScore * 0.93).toInt(),
                    first.mKindAndFlags, first.mSourceDict, first.mIndexOfTouchPointOfSecondWord, first.mAutoCommitFirstWordConfidence
                )
                val insertIndex = suggestionResults.indexOfFirst { it.mScore < modifiedFirst.mScore }
                if (insertIndex == -1) suggestionResults.add(modifiedFirst)
                else suggestionResults.add(insertIndex, modifiedFirst)

                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "reduced score of ${first.mWord} from ${first.mScore}, new first: ${suggestionResults.first().mWord} (${suggestionResults.first().mScore})")
            }
        }

        /** returns new pseudoTypedWordInfo, puts it in suggestionsContainer, modifies nextWordSuggestions */
        private fun preferNextWordSuggestion(
            pseudoTypedWordInfo: SuggestedWordInfo?,
            suggestionsContainer: ArrayList<SuggestedWordInfo>,
            nextWordSuggestions: SuggestionResults, rejected: SuggestedWordInfo?
        ): SuggestedWordInfo? {
            if (pseudoTypedWordInfo == null || !Settings.getValues().mUsePersonalizedDicts
                || pseudoTypedWordInfo.mSourceDict.mDictType != Dictionary.TYPE_MAIN || suggestionsContainer.size < 2
            ) return pseudoTypedWordInfo
            val goodNextSuggestions = nextWordSuggestions.filter { it.mScore >= 170 } // we only want reasonably often typed words, value may require tuning
            if (goodNextSuggestions.isEmpty()) return pseudoTypedWordInfo

            // for each suggestion, check whether the word was already typed in this ngram context (i.e. is nextWordSuggestion)
            for (suggestion in suggestionsContainer) {
                if (suggestion.mScore < pseudoTypedWordInfo.mScore * 0.93) break // we only want reasonably good suggestions, value may require tuning
                if (suggestion === rejected) continue  // ignore rejected suggestions
                for (nextWordSuggestion in goodNextSuggestions) {
                    if (nextWordSuggestion.mWord != suggestion.mWord) continue
                    // if we have a high scoring suggestion in next word suggestions, take it (because it's expected that user might want to type it again)
                    suggestionsContainer.remove(suggestion)
                    suggestionsContainer.add(0, suggestion)
                    if (DebugFlags.DEBUG_ENABLED)
                        Log.d(TAG, "replaced batch word $pseudoTypedWordInfo with $suggestion")
                    return suggestion
                }
            }
            return pseudoTypedWordInfo
        }
    }
}
