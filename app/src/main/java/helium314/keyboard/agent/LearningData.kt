// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import helium314.keyboard.latin.personalization.PersonalizationHelper
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One word the keyboard learned from typing, with the engine's 0..255 likelihood. */
data class LearnedWord(val word: String, val probability: Int) {
    override fun toString() = "LearnedWord(chars=${word.length})"
}

/** Where learned words come from. The real source needs the native engine; tests use a list. */
interface LearningSource {
    /** Language tags that have a typing-history file, sorted. */
    fun languages(): List<String>
    fun words(language: String): List<LearnedWord>
    fun deleteAll()
}

/**
 * The user's control over what the keyboard learned on this device: pause, look, export, delete.
 * Nothing here sends data anywhere; an export goes only to a document the user picks.
 */
object LearningData {
    const val FORMAT = "agent-keyboard-learning"
    const val VERSION = 1
    const val MAX_EXPORT_BYTES = 1_048_576

    /** The single rule the engine asks before it stores a typed word. */
    @JvmStatic fun blocksLearning(paused: Boolean): Boolean = paused

    fun isPaused(context: Context): Boolean = context.prefs().getBoolean(AgentPreferences.LEARNING_PAUSED, false)

    fun setPaused(context: Context, paused: Boolean) {
        check(context.prefs().edit().putBoolean(AgentPreferences.LEARNING_PAUSED, paused).commit())
    }

    fun counts(source: LearningSource): Map<String, Int> =
        source.languages().associateWith { source.words(it).size }

    /** Complete or nothing: an export that does not fit is refused instead of silently cut. */
    fun export(source: LearningSource): String {
        val languages = JSONArray()
        source.languages().forEach { language ->
            val words = JSONArray()
            source.words(language).sortedWith(compareByDescending<LearnedWord> { it.probability }.thenBy { it.word })
                .forEach { words.put(JSONObject().put("word", it.word).put("probability", it.probability)) }
            languages.put(JSONObject().put("language", language).put("words", words))
        }
        val encoded = JSONObject().put("format", FORMAT).put("version", VERSION).put("languages", languages).toString(2)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_EXPORT_BYTES) { "TOO_LARGE" }
        return encoded
    }
}

/** Reads the engine's per-language typing history. Call off the main thread. */
class UserHistoryLearningSource(context: Context) : LearningSource {
    // Same storage context as the engine that writes these files.
    private val storage = AgentStorageContexts.keyboard(context.applicationContext)

    override fun languages(): List<String> = (storage.filesDir.list() ?: emptyArray())
        .filter { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
        .map { it.removePrefix(PREFIX).removeSuffix(SUFFIX) }
        .filter { it.isNotEmpty() && it.matches(Regex("[A-Za-z0-9-]{1,40}")) }
        .distinct().sorted()

    override fun words(language: String): List<LearnedWord> {
        require(language in languages())
        val dictionary = PersonalizationHelper.getUserHistoryDictionary(storage, Locale.forLanguageTag(language))
        return dictionary.wordPropertiesForSyncing
            .filter { !it.mIsNotAWord && !it.mIsBeginningOfSentence && it.mWord.isNotEmpty() }
            .map { LearnedWord(it.mWord, it.mProbabilityInfo.mProbability.coerceIn(0, 255)) }
    }

    override fun deleteAll() = PersonalizationHelper.removeAllUserHistoryDictionaries(storage)

    private companion object {
        const val PREFIX = "UserHistoryDictionary."
        const val SUFFIX = ".dict"
    }
}
