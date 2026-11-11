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

/**
 * Reads the engine's per-language typing history. Call off the main thread.
 * It works on the same dictionary objects the running keyboard uses, so a read shows what the
 * keyboard holds and a delete empties exactly those objects.
 */
class UserHistoryLearningSource(context: Context) : LearningSource {
    // Same storage context as the engine that writes these files.
    private val storage = AgentStorageContexts.keyboard(context.applicationContext)

    private fun historyEntries() = (storage.filesDir.list() ?: emptyArray()).filter { it.startsWith(PREFIX) }

    override fun languages(): List<String> = historyEntries()
        .filter { it.endsWith(SUFFIX) }
        .map { it.removePrefix(PREFIX).removeSuffix(SUFFIX) }
        .filter { it.isNotEmpty() && it.matches(Regex("[A-Za-z0-9-]{1,40}")) }
        .distinct().sorted()

    private fun dictionary(language: String) =
        PersonalizationHelper.getUserHistoryDictionary(storage, Locale.forLanguageTag(language))

    /** Complete or an exception: a slow or unloaded dictionary is never reported as "no words". */
    override fun words(language: String): List<LearnedWord> {
        require(language in languages())
        val properties = checkNotNull(dictionary(language).readAllWordProperties(READ_TIMEOUT_MS)) { "UNREADABLE" }
        return properties
            .filter { !it.mIsNotAWord && !it.mIsBeginningOfSentence && it.mWord.isNotEmpty() }
            .map { LearnedWord(it.mWord, it.mProbabilityInfo.mProbability.coerceIn(0, 255)) }
    }

    /**
     * Empties every history in place and waits for it. The dictionaries stay registered, so the
     * running keyboard and this screen keep sharing one object per language.
     */
    override fun deleteAll() {
        val known = languages()
        known.forEach { language -> check(dictionary(language).clearAndWait(READ_TIMEOUT_MS)) { "NOT_CLEARED" } }
        // Leftovers that are not a language's dictionary (temporary or malformed entries).
        val live = known.map { PREFIX + it + SUFFIX }.toSet()
        historyEntries().filter { it !in live }.forEach { java.io.File(storage.filesDir, it).deleteRecursively() }
    }

    private companion object {
        const val PREFIX = "UserHistoryDictionary."
        const val SUFFIX = ".dict"
        const val READ_TIMEOUT_MS = 10_000L
    }
}
