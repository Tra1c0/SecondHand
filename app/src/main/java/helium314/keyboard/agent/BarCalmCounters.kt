// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

/**
 * Fork, stage 89 (`docs/LEISTE-RUHE-KONZEPT.md` 8a): what the bar of the keyboard showed and why, as numbers: visible changes of the
 * content of the bar by kind, the way of each backspace, field starts with and without a restart, discarded taps on outdated words.
 * In memory only (nothing stored, nothing logged), no text, no app name, no position. In a protected field (password, incognito,
 * locked device) nothing is counted at all. Written by `SuggestionStripView`, `KeyboardActionListenerImpl` and `LatinIME`,
 * read by the card "Leiste" of the diagnose page (`Diagnose.barCalm`).
 */
object BarCalmCounters {
    /** What the bar shows: the tools, words, nothing in the words area, a chip or pill. */
    enum class Kind { TOOLS, WORDS, EMPTY, CHIP }
    /** The way of a backspace: inside the composing word, the keyboard finds the word again, or the connection is slow (every delete asks for the neutral bar). */
    enum class Delete { IN_WORD, RESUME, SLOW }

    class Counts {
        val changesTo = IntArray(Kind.entries.size)
        val deletes = IntArray(Delete.entries.size)
        var fieldStarts = 0
        var restarts = 0
        var droppedTaps = 0
        fun changes() = changesTo.sum()
    }

    /** Since the process started. */
    val total = Counts()
    /** In the last field (a start without restart begins it). */
    var field = Counts()
        private set
    /** Whether the connection was slow at the last backspace. */
    var slowSeen = false
        private set
    private var last: Kind? = null

    fun resetForTest() { total.changesTo.fill(0); total.deletes.fill(0); total.fieldStarts = 0; total.restarts = 0; total.droppedTaps = 0; field = Counts(); slowSeen = false; last = null }

    fun onFieldStart(restarting: Boolean, protectedField: Boolean) {
        if (protectedField) return
        if (restarting) { total.restarts++; field.restarts++ } else { total.fieldStarts++; field = Counts(); field.fieldStarts = 1 }
    }

    /** The bar now shows [kind]; counts only a change. [protectedField]: nothing is counted (and the last kind is forgotten). */
    fun onShown(kind: Kind, protectedField: Boolean) {
        if (protectedField) { last = null; return }
        if (kind == last) return
        // the first kind after a start is a start, not a change
        if (last != null) { total.changesTo[kind.ordinal]++; field.changesTo[kind.ordinal]++ }
        last = kind
    }

    fun onDelete(way: Delete, slow: Boolean, protectedField: Boolean) {
        if (protectedField) return
        slowSeen = slow
        total.deletes[way.ordinal]++
        field.deletes[way.ordinal]++
    }

    fun onDroppedTap(protectedField: Boolean) {
        if (protectedField) return
        total.droppedTaps++
        field.droppedTaps++
    }
}
