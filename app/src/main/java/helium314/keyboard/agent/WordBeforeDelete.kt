// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

/**
 * Stage 96 (owner, 2026-11-25): "Ein Longpress auf das Wort, während Dinge gelöscht wurden, … holt das Wort in seiner ursprünglichen
 * Form zurück." Remembers the word as it was before the delete key started to remove its characters ("löschen" while the editor
 * shows "lösch"), so that a long press on the word left in the strip can put it back.
 *
 * One instance per [helium314.keyboard.latin.inputlogic.InputLogic]. The word is kept only in memory, never stored or logged, and
 * forgotten on any key that is not delete, on a new field, when the cursor leaves the word, when the word is deleted completely and
 * after it was put back. Written on the UI thread; [originalFor] is also read from the suggestion thread.
 */
class WordBeforeDelete {
    /** [word] is the form before deleting began, [now] the form the last delete left. */
    private class Original(val start: Int, val word: String, val now: String)

    @Volatile private var original: Original? = null

    /**
     * The delete key is about to remove a character of [word], the composing word that starts at [start] in the editor (cursor at
     * its end). Only the first delete of a run at a word is remembered: while the word is still what the last delete left, the
     * following deletes keep the form it had before deleting began. A word changed in between (a picked suggestion) starts anew.
     */
    fun beforeDelete(start: Int, word: String) {
        if (start < 0 || word.isEmpty()) { original = null; return }
        val o = original
        if (o != null && o.start == start && o.now == word) return
        original = Original(start, word, word)
    }

    /** The delete left [now] (empty: the word is gone, forget it). */
    fun afterDelete(now: String) {
        val o = original ?: return
        original = if (now.isEmpty()) null else Original(o.start, o.word, now)
    }

    /**
     * The original form of [composing] starting at [start], or null when no character of this word was deleted, the word changed
     * since the last delete or it is the original again.
     */
    fun originalFor(start: Int, composing: String): String? {
        val o = original ?: return null
        return o.word.takeIf { o.start == start && composing.isNotEmpty() && composing == o.now && composing != o.word }
    }

    /** The cursor moved to [position] (not by a delete of the keyboard): forget the word when the cursor left it. */
    fun cursorMovedTo(position: Int) {
        val o = original ?: return
        if (position < o.start || position > o.start + o.word.length) original = null
    }

    fun clear() {
        original = null
    }
}
