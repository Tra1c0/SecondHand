// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import java.security.SecureRandom

/**
 * Where private text the user inserted (a vault secret, a code, a "Meine Daten" value) sits in the
 * editor (second review of stages 44/45). Keeps no text: per value only its start, its length and a
 * salted hash of its first [HEAD] characters, at most [MAX] values (the oldest goes first).
 *
 * Positions follow the keyboard's own edits ([edited]); for other changes (an edit by the app, a
 * gesture or a picked suggestion before the value) [touches] finds the value again by its hash near
 * the cursor, and forgets it only when it is not there any more.
 */
class PrivateRanges(private val salt: Long = SecureRandom().nextLong()) {

    /** Read access to the editor around the cursor, as the input connection gives it. */
    interface Editor {
        /** The cursor, or a negative value when unknown. */
        val cursor: Int
        /** Up to [n] characters before the cursor (fewer at the start of the field), null when unreadable. */
        fun before(n: Int): CharSequence?
        /** Up to [n] characters after the cursor, null when unreadable. */
        fun after(n: Int): CharSequence?
    }

    private class Range(var start: Int, var length: Int, var headLength: Int, var head: Long) {
        val end get() = start + length
    }

    private val ranges = ArrayList<Range>(MAX)
    private val base = salt or 1L // odd multiplier of the polynomial hash

    val isEmpty get() = ranges.isEmpty()
    val size get() = ranges.size

    fun clear() = ranges.clear()

    /** Marks [length] characters from [start] as private; false (and nothing marked) when they cannot be read. */
    fun add(start: Int, length: Int, editor: Editor): Boolean {
        if (start < 0 || length <= 0) return false
        val range = Range(start, length, 0, 0)
        if (!rehash(range, editor)) return false
        ranges.removeAll { it.start < start + length && start < it.end } // the new value replaced them
        if (ranges.size >= MAX) ranges.removeAt(0)
        ranges.add(range)
        return true
    }

    /**
     * The keyboard removed the characters in [removeFrom, removeTo) and then inserted [inserted]
     * characters at [removeFrom]: moves the values after the edit and shortens or lengthens a value
     * it touched (an insertion at a value's end is not part of it).
     */
    fun edited(removeFrom: Int, removeTo: Int, inserted: Int, editor: Editor) {
        if (ranges.isEmpty() || removeFrom < 0 || removeTo < removeFrom || inserted < 0) return
        if (removeTo == removeFrom && inserted == 0) return
        val iterator = ranges.iterator()
        while (iterator.hasNext()) {
            val range = iterator.next()
            fun afterRemoval(x: Int) = x - maxOf(0, minOf(removeTo, x) - removeFrom)
            var start = afterRemoval(range.start)
            var end = afterRemoval(range.end)
            if (inserted > 0) {
                if (removeFrom <= start) { start += inserted; end += inserted }
                else if (removeFrom < end) end += inserted
            }
            if (end <= start) { iterator.remove(); continue }
            val moved = start != range.start || end != range.end
            range.start = start
            range.length = end - start
            if (moved && !rehash(range, editor)) iterator.remove()
        }
    }

    /**
     * True when a private value overlaps or borders the characters in [from, to] (absolute
     * positions). Each value near them is first checked by its hash; one that moved is looked for
     * near the cursor, one that is gone is forgotten.
     */
    fun touches(from: Int, to: Int, editor: Editor): Boolean {
        if (ranges.isEmpty()) return false
        if (editor.cursor < 0) return true // a selection or an unknown cursor: nothing is checked, better refuse
        var touched = false
        val iterator = ranges.iterator()
        while (iterator.hasNext()) {
            val range = iterator.next()
            // far from the asked characters: it cannot touch them (unless moved this far unseen), checked when near
            if (range.end + WINDOW < from || range.start - WINDOW > to) continue
            if (!stillThere(range, editor) && !relocate(range, editor)) { iterator.remove(); continue }
            if (from <= range.end && to >= range.start) touched = true
        }
        return touched
    }

    /** [touches] for the words the user dictionary would learn now: the word before the cursor and its n-gram context. */
    fun touchesLearningContext(editor: Editor, words: Int): Boolean {
        if (ranges.isEmpty()) return false
        val cursor = editor.cursor
        if (cursor < 0) return true // unknown: better not learn
        val before = editor.before(minOf(cursor, CONTEXT_LIMIT)) ?: return true
        return touches(cursor - contextLength(before, words), cursor, editor)
    }

    private fun stillThere(range: Range, editor: Editor): Boolean {
        val head = textAt(range.start, range.start + range.headLength, editor) ?: return false
        return hash(head, 0, head.length) == range.head
    }

    private fun relocate(range: Range, editor: Editor): Boolean {
        val cursor = editor.cursor
        if (cursor < 0) return false
        val before = editor.before(minOf(cursor, range.length + WINDOW)) ?: return false
        val after = editor.after(range.length + WINDOW) ?: ""
        val text = StringBuilder(before.length + after.length).append(before).append(after)
        val offset = cursor - before.length
        val n = range.headLength
        if (text.length < n) return false
        var power = 1L
        repeat(n - 1) { power *= base }
        var h = hash(text, 0, n)
        var best = -1
        for (i in 0..text.length - n) {
            if (i > 0) h = (h - text[i - 1].code * power) * base + text[i + n - 1].code
            if (h == range.head && (best < 0 || kotlin.math.abs(offset + i - range.start) < kotlin.math.abs(offset + best - range.start))) best = i
        }
        if (best < 0) return false
        range.start = offset + best
        return true
    }

    private fun rehash(range: Range, editor: Editor): Boolean {
        val n = minOf(range.length, HEAD)
        val head = textAt(range.start, range.start + n, editor) ?: return false
        range.headLength = n
        range.head = hash(head, 0, n)
        return true
    }

    private fun hash(text: CharSequence, from: Int, length: Int): Long {
        var h = 0L
        for (i in from until from + length) h = h * base + text[i].code
        return h
    }

    /** The editor text in [start, end), read relative to the cursor; null when not readable as a whole. */
    private fun textAt(start: Int, end: Int, editor: Editor): CharSequence? {
        val cursor = editor.cursor
        if (cursor < 0 || start < 0 || end < start) return null
        val builder = StringBuilder(end - start)
        if (start < cursor) {
            val before = editor.before(cursor - start) ?: return null
            if (before.length != cursor - start) return null
            builder.append(before, 0, minOf(end, cursor) - start)
        }
        if (end > cursor) {
            val from = maxOf(start, cursor) - cursor
            val after = editor.after(end - cursor) ?: return null
            if (after.length != end - cursor) return null
            builder.append(after, from, end - cursor)
        }
        return builder
    }

    companion object {
        /** At most this many private values per field (name, address, phone …). */
        const val MAX = 3
        /** Characters hashed per value: enough to find it again, cheap to check for long values (PEM keys). */
        const val HEAD = 32
        /** A moved value is looked for this far around the cursor (plus its length). */
        const val WINDOW = 1024
        /** The n-gram context is looked for at most this far before the cursor. */
        const val CONTEXT_LIMIT = 512

        /**
         * Characters at the end of [before] that hold the last [words] whitespace-separated words
         * (the word just written and the previous ones of its n-gram context), trailing whitespace
         * included; all of [before] when it has fewer.
         */
        fun contextLength(before: CharSequence, words: Int): Int {
            var i = before.length
            var found = 0
            while (i > 0 && found < words) {
                while (i > 0 && before[i - 1].isWhitespace()) i--
                if (i == 0) break
                while (i > 0 && !before[i - 1].isWhitespace()) i--
                found++
            }
            return before.length - i
        }
    }
}
