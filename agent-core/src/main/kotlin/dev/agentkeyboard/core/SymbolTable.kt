// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Symbols found by name (stage 24f, `@zeichen` and `@emoji`). Each entry is one symbol with
 * German and English names. Names and queries are compared folded: lower case, umlauts as plain
 * vowels (ä → a, ß → ss), without spaces or punctuation, so "Pfeil rechts" matches "pfeilrechts".
 */
class SymbolTable(entries: List<Entry>) {
    class Entry(val symbol: String, names: List<String>) {
        /** Folded names, see [fold]. */
        val names: List<String> = names.map(::fold).filter { it.isNotEmpty() }.distinct()
        /** The first name as written, for descriptions. */
        val label: String = names.first()
    }

    val entries: List<Entry> = entries.distinctBy { it.symbol }

    /**
     * Entries with a name equal to the folded [query] first, then those with a name starting with
     * it, each in table order; at most [limit]. A query without letters or digits finds nothing.
     */
    fun find(query: String, limit: Int = 12): List<Entry> {
        val folded = fold(query)
        if (folded.isEmpty()) return emptyList()
        val exact = entries.filter { folded in it.names }
        val prefix = entries.filter { entry -> entry !in exact && entry.names.any { it.startsWith(folded) } }
        return (exact + prefix).take(limit)
    }

    companion object {
        fun fold(text: String): String {
            val builder = StringBuilder(text.length)
            for (char in text.lowercase()) when (char) {
                'ä' -> builder.append('a')
                'ö' -> builder.append('o')
                'ü' -> builder.append('u')
                'ß' -> builder.append("ss")
                else -> if (char.isLetterOrDigit()) builder.append(char)
            }
            return builder.toString()
        }

        /** Short form for the tables: a symbol and its names. */
        internal fun e(symbol: String, vararg names: String) = Entry(symbol, names.toList())
    }
}
