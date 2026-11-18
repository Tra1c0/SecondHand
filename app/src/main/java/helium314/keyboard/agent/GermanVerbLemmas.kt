// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context

/** The bundled verb vocabulary, independent of the user's dictionaries and learning. */
object GermanVerbLemmas {
    private const val ASSET = "agent/german_verb_lemmas.txt"
    @Volatile private var lemmas: Set<String>? = null

    fun contains(context: Context?, lemma: String): Boolean {
        if (context == null) return false
        val terms = lemmas ?: synchronized(this) {
            lemmas ?: runCatching {
                context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith('#') }.toSet()
                }
            }.getOrDefault(emptySet()).also { lemmas = it }
        }
        return lemma in terms
    }
}
