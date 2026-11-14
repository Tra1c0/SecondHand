// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryCollection
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stage 51: the main dictionaries as plain word lists, for the real [DictionaryFacilitatorImpl] in
 * unit tests (there is no native dictionary library). Only the native word lookup is replaced: the
 * facilitator with its dictionary groups, locales and language confidence, [Suggest] and the input
 * logic run as on the device. [lists] maps a language ("de", "es", "en") to its words with a
 * frequency (0 to 255); a language without a list gets an empty, uninitialized main dictionary as
 * before.
 */
object WordLists {
    @Volatile var lists: Map<String, Map<String, Int>> = emptyMap()
    /** Words reported with frequency 0, which the engine reads as "possibly offensive" (review of stage 51). */
    @Volatile var offensive: Set<String> = emptySet()
    /** The locales main dictionaries were created for, in order (also those without a list), and the thread that opened them. */
    val opened = CopyOnWriteArrayList<Pair<Locale, Thread>>()

    /** [words] with frequency 0, or "word:frequency". */
    fun of(vararg words: String): Map<String, Int> =
        words.associate { if (':' in it) it.substringBefore(':') to it.substringAfter(':').toInt() else it to 0 }

    fun reset() { lists = emptyMap(); offensive = emptySet(); opened.clear() }
}

@Implements(DictionaryFactory::class)
class ShadowWordListDictionaries {
    @Implementation
    fun createMainDictionaryCollection(context: Context, locale: Locale, useEmojiDict: Boolean): DictionaryCollection {
        WordLists.opened += locale to Thread.currentThread()
        val dictionaries = WordLists.lists[locale.language]?.let { listOf(WordListDictionary(locale, it)) }.orEmpty()
        return DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictionaries, FloatArray(dictionaries.size) { 1f })
    }
}

/**
 * A main dictionary from a word list, scored roughly like the native one: the exact word 1.5 million,
 * a word one edit away (three letters or more) 600 000, a word the typed letters start 300 000, plus
 * 1000 per frequency point, all times the weight of the language (multilingual typing). Exact
 * matches are case-sensitive like the native lookup.
 */
class WordListDictionary(locale: Locale, private val words: Map<String, Int>) : Dictionary(TYPE_MAIN, locale) {
    override fun getSuggestions(
        composedData: ComposedData, ngramContext: NgramContext?, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?, sessionId: Int, weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?,
    ): ArrayList<SuggestedWordInfo> {
        val typed = composedData.mTypedWord.lowercase(mLocale)
        val result = ArrayList<SuggestedWordInfo>()
        if (typed.isEmpty()) return result
        for ((word, frequency) in words) {
            val lower = word.lowercase(mLocale)
            val (score, kind) = when {
                lower == typed -> 1_500_000 to (SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_EXACT_MATCH)
                typed.length >= 3 && distance(typed, lower) == 1 -> 600_000 to SuggestedWordInfo.KIND_CORRECTION
                typed.length >= 2 && lower.startsWith(typed) -> 300_000 to SuggestedWordInfo.KIND_COMPLETION
                else -> continue
            }
            result += SuggestedWordInfo(word, "", ((score + 1000 * frequency) * weightForLocale).toInt(),
                kind or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION, this,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        }
        result.sortByDescending { it.mScore }
        return result
    }

    override fun isInDictionary(word: String): Boolean = word in words

    // never 0, the engine reads 0 as "possibly offensive" (only the words in [WordLists.offensive])
    override fun getFrequency(word: String): Int =
        words[word]?.let { if (word in WordLists.offensive) 0 else it.coerceAtLeast(1) } ?: NOT_A_PROBABILITY

    override fun getMaxFrequencyOfExactMatches(word: String): Int = getFrequency(word)

    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }
}
