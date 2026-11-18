// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.InformalWords

/**
 * Stage 71: loads the built-in list of informal words (see [InformalWords]) from the app's assets
 * once and answers whether the automatic correction must leave a typed word alone. Static data, no
 * learning and nothing stored; a missing or unreadable file means an empty list for that call (nothing protected).
 */
object InformalWordsStore {
    const val ASSET = "agent/informal_words.txt"

    @Volatile private var words: Set<String>? = null

    private fun loaded(context: Context): Set<String> {
        words?.let { return it }
        synchronized(this) {
            words?.let { return it }
            // a failed read is not remembered: the next call tries again (a missing asset is a packaging error, not a state)
            val read = runCatching {
                context.applicationContext.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { InformalWords.parse(it.readText()) }
            }.getOrNull() ?: return emptySet()
            words = read
            return read
        }
    }

    /**
     * [typed] stays as typed: it is on the list and the list applies to one of [languages] (the keyboard's
     * language and the language of the correction). Read-only, for the suggestion thread; false without a context.
     */
    @JvmStatic fun protects(context: Context?, typed: String, vararg languages: String?): Boolean {
        if (context == null || typed.isEmpty()) return false
        return runCatching { InformalWords.protects(loaded(context), typed, languages.asList()) }.getOrDefault(false)
    }

    /** The number of words on the list, for the tests and the docs. */
    @JvmStatic fun size(context: Context): Int = loaded(context).size

    internal fun resetForTest() { words = null }
}
