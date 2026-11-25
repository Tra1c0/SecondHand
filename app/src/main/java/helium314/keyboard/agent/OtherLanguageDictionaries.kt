// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stage 101: read-only lookups in the main dictionaries of the owner's OTHER enabled keyboard languages (an enabled
 * layout's language or a second language of one, without the languages the keyboard uses right now, which the engine
 * has anyway). The owner types English on the German layout, where no English dictionary is active: "God", "sucks",
 * "cooled" were unknown and replaced by German words.
 *
 * The dictionaries are opened on [ForeignWords.worker] (never on the typing or suggestion thread), like the checks of
 * stage 51, and published as a whole; until they are open every lookup answers "unknown" (the old behaviour). They are
 * memory-mapped files (en-US about 3 MB); [close] is called with [ForeignWords.closeDictionaries] (low memory, keyboard in
 * the background, a dictionary added or removed, learning cleared), a later lookup opens them again. Reading a closed
 * dictionary is safe: [helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary] locks and answers "unknown".
 * Nothing is stored and nothing is logged.
 */
object OtherLanguageDictionaries {
    /** What the suggestion thread asks; replaced in tests. */
    interface Source {
        /** The languages ("en", "es") of the other enabled keyboard languages whose dictionary is open, without [active]. */
        fun languages(context: Context?, active: Set<String>): List<String>
        /** Frequency 0..255 of [word] exactly as written in the main dictionary of [language]; below zero when unknown or not open. */
        fun frequency(language: String, word: String): Int
    }

    @JvmStatic @Volatile var source: Source = Loaded

    /** How often at most the enabled languages are compared with the open dictionaries. */
    private const val RECHECK_MILLIS = 5_000L

    @JvmStatic fun languages(context: Context?, active: Set<String>): List<String> =
        runCatching { source.languages(context, active) }.getOrDefault(emptyList())

    @JvmStatic fun frequency(language: String, word: String): Int =
        if (word.isEmpty()) Dictionary.NOT_A_PROBABILITY
        else runCatching { source.frequency(language, word) }.getOrDefault(Dictionary.NOT_A_PROBABILITY)

    /**
     * The highest frequency of [word] in the main dictionary of [language] as typed, in lower case or with a capital first
     * letter ("God", "god"). An entry in capitals only ("ICJ") does not count for a word typed with lower case letters.
     */
    @JvmStatic fun frequencyOfForms(language: String, word: String): Int {
        val locale = Locale.forLanguageTag(language)
        val lower = word.lowercase(locale)
        return listOf(word, lower, lower.replaceFirstChar { it.titlecase(locale) }).distinct()
            .maxOf { frequency(language, it) }
    }

    /** Closes the open dictionaries on the worker; a later lookup opens them again. */
    @JvmStatic fun close() {
        Loaded.close()
    }

    private object Loaded : Source {
        /** language -> dictionary, replaced as a whole. */
        @Volatile private var open: Map<String, Dictionary> = emptyMap()
        @Volatile private var lastCheck = 0L
        @Volatile private var lastActive: Set<String> = emptySet()
        private val loading = AtomicBoolean(false)
        /** Languages whose dictionary could not be opened; not tried again until [close]. Only used on the worker. */
        private val failed = HashSet<String>()
        /** Set by [close]: the next [load] tries the failed languages again (a dictionary may have been added). */
        @Volatile private var retryFailed = false

        override fun languages(context: Context?, active: Set<String>): List<String> {
            ensure(context, active)
            return open.keys.filter { it !in active }
        }

        override fun frequency(language: String, word: String): Int =
            open[language]?.getFrequency(word) ?: Dictionary.NOT_A_PROBABILITY

        private fun ensure(context: Context?, active: Set<String>) {
            if (context == null) return
            val now = System.currentTimeMillis()
            // at once after a switch of the keyboard language, otherwise at most every few seconds
            if (now - lastCheck < RECHECK_MILLIS && active == lastActive) return
            lastCheck = now
            lastActive = active
            if (!loading.compareAndSet(false, true)) return
            val app = context.applicationContext ?: context
            try {
                ForeignWords.worker.execute {
                    try {
                        runCatching { load(app, active) }
                    } finally {
                        loading.set(false)
                    }
                }
            } catch (e: Throwable) {
                loading.set(false)
            }
        }

        /** On the worker: opens the wanted languages that are not open, closes those no longer wanted. */
        private fun load(context: Context, active: Set<String>) {
            val wanted = ForeignWords.enabledLocales().filter { it.language.isNotEmpty() && it.language !in active }
                .distinctBy { it.language }
            if (retryFailed) { retryFailed = false; failed.clear() }
            val current = open
            if (wanted.map { it.language }.filterTo(HashSet()) { it !in failed } == current.keys) return
            val next = HashMap<String, Dictionary>()
            for (locale in wanted) {
                val existing = current[locale.language]
                if (existing != null) { next[locale.language] = existing; continue }
                if (locale.language in failed) continue
                val dictionary = runCatching { DictionaryFactory.createMainDictionaryCollection(context, locale, false) }.getOrNull()
                    ?.takeIf { it.isInitialized || run { runCatching { it.close() }; false } }
                if (dictionary == null) { failed += locale.language; continue }
                next[locale.language] = dictionary
            }
            open = next
            current.filterKeys { it !in next }.values.forEach { runCatching { it.close() } }
        }

        /** Closing runs on the worker, so that it cannot overlap a [load] (the worker runs one task at a time). */
        fun close() {
            lastCheck = 0L
            retryFailed = true
            if (open.isEmpty()) return
            runCatching {
                ForeignWords.worker.execute {
                    val current = open
                    open = emptyMap()
                    current.values.forEach { runCatching { it.close() } }
                }
            }
        }
    }
}
