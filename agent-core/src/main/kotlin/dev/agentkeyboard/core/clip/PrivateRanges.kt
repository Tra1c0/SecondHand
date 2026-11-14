// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import java.security.SecureRandom
import kotlin.math.abs

/**
 * Where private text the user inserted (a vault secret, a code, a "Meine Daten" value) sits in the
 * editor (second review of stages 44/45). Keeps no text: per value only its start, its length and a
 * salted hash of its first [HEAD] characters, at most [MAX] values (the oldest goes first).
 *
 * Positions follow the keyboard's own edits ([beforeEdit], [edited]); for other changes (an edit by
 * the app, a gesture or a picked suggestion before the value) [touches] finds the value again by its
 * hash near its last known place, and forgets it only after a complete read without it.
 *
 * Third review: an edit before a value only moves it and keeps its hash (nothing is read), so a
 * value moved unseen before is still noticed by its hash and found again. Only an edit in its first
 * [HEAD] characters hashes it anew, after [beforeEdit] made sure the position is right.
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
        /** Its head was edited but could not be read: hashed anew at the next check, until then private when near. */
        var headUnread = false
        /** Its head stands more than once near its place: every one of them is treated as private. */
        var ambiguous = false
    }

    /** The result of reading a value's head at its place (UNREADABLE also when the field ends before it). */
    private enum class Check { THERE, NOT_THERE, UNREADABLE }

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
     * The keyboard is about to edit [from, to) at the cursor: a value whose head is near the edit is
     * checked first (and found again when it moved unseen), so that [edited] hashes the right text
     * when the edit changes the head. Reads only for values whose head is within [HEAD] characters.
     */
    fun beforeEdit(from: Int, to: Int, editor: Editor) {
        if (ranges.isEmpty() || from < 0 || to < from) return
        val iterator = ranges.iterator()
        while (iterator.hasNext()) {
            val range = iterator.next()
            if (range.headUnread || to < range.start - HEAD || from > range.start + range.headLength + HEAD) continue
            if (!range.ambiguous && check(range, editor) == Check.THERE) continue
            val found = find(range, editor) ?: continue // unreadable: left as it is
            if (found.isEmpty()) { iterator.remove(); continue }
            moveTo(range, found)
        }
    }

    /**
     * The keyboard removed the characters in [removeFrom, removeTo) and then inserted [inserted]
     * characters at [removeFrom]: moves the values after the edit and shortens or lengthens a value
     * it touched (an insertion at a value's end is not part of it). Only an edit in a value's head
     * reads (that head, at the cursor); a pure shift keeps the hash.
     */
    fun edited(removeFrom: Int, removeTo: Int, inserted: Int, editor: Editor) {
        if (ranges.isEmpty() || removeFrom < 0 || removeTo < removeFrom || inserted < 0) return
        if (removeTo == removeFrom && inserted == 0) return
        val iterator = ranges.iterator()
        while (iterator.hasNext()) {
            val range = iterator.next()
            fun afterRemoval(x: Int) = x - maxOf(0, minOf(removeTo, x) - removeFrom)
            val headEnd = range.start + range.headLength
            // the removed characters overlap the head, or the insertion is inside it (at its start it is before the value)
            val inHead = removeFrom < headEnd && (removeTo > range.start || removeFrom > range.start)
            var start = afterRemoval(range.start)
            var end = afterRemoval(range.end)
            if (inserted > 0) {
                if (removeFrom <= start) { start += inserted; end += inserted }
                else if (removeFrom < end) end += inserted
            }
            if (end <= start) { iterator.remove(); continue }
            range.start = start
            range.length = end - start
            if (inHead || range.headUnread) range.headUnread = !rehash(range, editor)
        }
    }

    /**
     * True when a private value overlaps or borders the characters in [from, to] (absolute
     * positions). Each value near them is first checked by its hash; one that moved is looked for
     * near its last place, one that is gone (a complete read without it) is forgotten. One that
     * cannot be read counts as touching.
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
            if (range.headUnread) {
                range.headUnread = !rehash(range, editor)
                if (range.headUnread) { touched = true; continue } // near and unreadable: better refuse
            }
            if (!range.ambiguous) when (check(range, editor)) {
                Check.THERE -> { if (overlaps(range.start, range.length, from, to)) touched = true; continue }
                Check.UNREADABLE, Check.NOT_THERE -> Unit // looked for below
            }
            val found = find(range, editor)
            if (found == null) { touched = true; continue } // near and unreadable: better refuse, nothing forgotten
            if (found.isEmpty()) { iterator.remove(); continue }
            moveTo(range, found)
            if (found.any { overlaps(it, range.length, from, to) }) touched = true
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

    private fun overlaps(start: Int, length: Int, from: Int, to: Int) = from <= start + length && to >= start

    private fun check(range: Range, editor: Editor): Check {
        val head = textAt(range.start, range.start + range.headLength, editor) ?: return Check.UNREADABLE
        return if (hash(head, 0, head.length) == range.head) Check.THERE else Check.NOT_THERE
    }

    /**
     * Where the head of [range] stands within its search window around its last place (third review:
     * [WINDOW], for values shorter than [MIN_RELOCATE] only [SHORT_WINDOW], so a short value does not
     * jump to equal text further away); empty after a complete read without it, null when unreadable.
     */
    private fun find(range: Range, editor: Editor): List<Int>? {
        val cursor = editor.cursor
        if (cursor < 0) return null
        val n = range.headLength
        val window = if (range.length < MIN_RELOCATE) SHORT_WINDOW else WINDOW
        val lower = maxOf(0, range.start - window)
        val upper = range.start + n + window
        if (lower < cursor && cursor - lower > READ_LIMIT) return null
        if (upper > cursor && upper - cursor > READ_LIMIT) return null
        val text = StringBuilder()
        val offset: Int
        if (lower < cursor) {
            val count = cursor - lower
            val before = editor.before(count) ?: return null
            if (before.length != count) return null // the app gave less than there is: not a complete read
            text.append(before, 0, minOf(count, maxOf(0, upper - lower)))
            offset = lower
        } else offset = lower
        if (upper > cursor) {
            val from = maxOf(lower, cursor) - cursor
            val after = editor.after(upper - cursor) ?: return null
            // fewer characters after the cursor: the field ends there, unless the answer stops before the
            // value's last known head and is not empty — then the app cut the read short (some apps limit
            // getTextAfterCursor): not readable, forget nothing (review 79a66c8, finding 1)
            if (after.length < upper - cursor && after.isNotEmpty() && cursor + after.length < range.start + n) return null
            if (after.length > from) text.append(after, from, after.length)
        }
        if (text.length < n) return emptyList()
        var power = 1L
        repeat(n - 1) { power *= base }
        var h = hash(text, 0, n)
        val found = ArrayList<Int>(1)
        for (i in 0..text.length - n) {
            if (i > 0) h = (h - text[i - 1].code * power) * base + text[i + n - 1].code
            if (h == range.head) found.add(offset + i)
        }
        return found
    }

    /** Moves [range] to the found place nearest to its last one; several places make it ambiguous. */
    private fun moveTo(range: Range, found: List<Int>) {
        val last = range.start
        range.start = found.minBy { abs(it - last) }
        range.ambiguous = found.size > 1
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

    /**
     * The editor text in [start, end), read relative to the cursor; null when not readable as a whole
     * (unreadable, fewer characters than asked, or farther than [READ_LIMIT] from the cursor).
     */
    private fun textAt(start: Int, end: Int, editor: Editor): CharSequence? {
        val cursor = editor.cursor
        if (cursor < 0 || start < 0 || end < start) return null
        if (cursor - start > READ_LIMIT || end - cursor > READ_LIMIT) return null
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
        /** A moved value is looked for this far around its last place. */
        const val WINDOW = 1024
        /** Values shorter than this are looked for only [SHORT_WINDOW] around their last place (third review). */
        const val MIN_RELOCATE = 8
        const val SHORT_WINDOW = 64
        /** At most this many characters are read from the cursor at once; farther counts as unreadable. */
        const val READ_LIMIT = 8192
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
