// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 49: which later change of a word in a field is the user fixing a typo, and which is a
 * revision. The rules were agreed with the owner (docs/ROADMAP.md, item 49). Pure functions, no text
 * is kept here.
 *
 * A typo correction needs all of: the old word is not a real word, the new word is, the change looks
 * like mistyping (a neighbouring key on the current layout, a missing or doubled letter, two swapped
 * letters, an umlaut or other diacritic; at most [MAX_STEPS] such steps, at most [MAX_STEPS_SHORT]
 * for words of up to [SHORT_WORD] letters) and only this one word changed ([diff]). Everything else
 * is a revision and is never learned.
 */
object CorrectionRules {
    enum class Kind { TYPO, REVISION }

    const val MAX_STEPS = 2
    const val MAX_STEPS_SHORT = 1
    const val SHORT_WORD = 4
    private const val NO = 1_000

    /** One word of a text replaced by another, at [start] in both texts. */
    data class Change(val start: Int, val old: String, val new: String)

    sealed interface Diff {
        /** Nothing changed, or only text was added or removed around words that stayed the same. */
        data object None : Diff
        /** Exactly one word changed, all text around it is the same (or, see [diff], deleted after it). */
        data class Single(val change: Change) : Diff
        /** More than one word, or words and the space between them changed: a revision. */
        data object Several : Diff
    }

    /** Letters, combining marks, digits and the apostrophe and hyphen inside words. */
    @JvmStatic
    fun isWordChar(c: Char): Boolean =
        Character.isLetterOrDigit(c) || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
            || c == '\'' || c == '’' || c == '-'

    /**
     * Compares two states of the same field. [old] and [new] start at the same position of the field.
     * [oldComplete]/[newComplete]: the text reaches the end of the field (not cut off after a limit).
     * With [tailMayBeDeleted] (the new word was just committed at the end of the field) the text after
     * the changed word may be missing in [new]: the user deleted back to the word over spaces and
     * fixed it, the rest is not typed again yet.
     */
    @JvmStatic
    fun diff(old: String, new: String, oldComplete: Boolean = true, newComplete: Boolean = true,
             tailMayBeDeleted: Boolean = false): Diff {
        if (old == new) return Diff.None
        var p = 0
        val shorter = min(old.length, new.length)
        while (p < shorter && old[p] == new[p]) p++
        if (p == shorter && !(oldComplete && newComplete)) return Diff.None // only the cut-off ends differ
        var start = p
        while (start > 0 && isWordChar(old[start - 1])) start--
        var oldEnd = start
        while (oldEnd < old.length && isWordChar(old[oldEnd])) oldEnd++
        var newEnd = start
        while (newEnd < new.length && isWordChar(new[newEnd])) newEnd++
        val oldWord = old.substring(start, oldEnd)
        val newWord = new.substring(start, newEnd)
        val oldRest = old.substring(oldEnd)
        val newRest = new.substring(newEnd)
        if (oldWord == newWord) {
            // the words agree, the difference is after them: text was added or removed there
            return Diff.None
        }
        if (oldWord.isEmpty() || newWord.isEmpty()) {
            // a word was inserted or deleted, or only separators changed
            return if (sameRest(oldRest, newRest, oldComplete, newComplete)) Diff.None else Diff.Several
        }
        val change = Change(start, oldWord, newWord)
        if (sameRest(oldRest, newRest, oldComplete, newComplete)) return Diff.Single(change)
        if (tailMayBeDeleted && newComplete && newRest.isEmpty()) return Diff.Single(change)
        return Diff.Several
    }

    private fun sameRest(oldRest: String, newRest: String, oldComplete: Boolean, newComplete: Boolean): Boolean {
        if (oldComplete && newComplete) return oldRest == newRest
        val n = min(oldRest.length, newRest.length)
        if (!oldRest.regionMatches(0, newRest, 0, n)) return false
        // the longer one goes on where the shorter one was cut off
        return when {
            oldRest.length == newRest.length -> true
            oldRest.length < newRest.length -> !oldComplete
            else -> !newComplete
        }
    }

    /**
     * TYPO or REVISION for a change from [old] to [new] where only this word changed.
     * [isRealOld]/[isRealNew]: the word is in a dictionary or among the user's own words.
     */
    @JvmStatic
    fun classify(old: String, new: String, isRealOld: Boolean, isRealNew: Boolean, adjacency: KeyAdjacency): Kind {
        if (old.isEmpty() || new.isEmpty() || old.equals(new, ignoreCase = true)) return Kind.REVISION
        if (!old.all(Character::isLetter) || !new.all(Character::isLetter)) return Kind.REVISION
        if (isRealOld || !isRealNew) return Kind.REVISION
        val a = old.lowercase(Locale.ROOT)
        val b = new.lowercase(Locale.ROOT)
        val limit = if (max(a.length, b.length) <= SHORT_WORD) MAX_STEPS_SHORT else MAX_STEPS
        val steps = mistypingSteps(a, b, adjacency)
        return if (steps in 1..limit) Kind.TYPO else Kind.REVISION
    }

    /**
     * The fewest mistyping steps from [old] to [new] (both lower case), or a large number when the
     * change cannot be explained by mistyping. Steps: a neighbouring key or a diacritic variant
     * instead of the letter, a letter missing, a letter doubled, two neighbouring letters swapped.
     */
    @JvmStatic
    fun mistypingSteps(old: String, new: String, adjacency: KeyAdjacency): Int {
        val n = old.length
        val m = new.length
        if (abs(n - m) > MAX_STEPS) return NO
        val d = Array(n + 1) { IntArray(m + 1) { NO } }
        d[0][0] = 0
        for (i in 0..n) for (j in 0..m) {
            if (i == 0 && j == 0) continue
            var best = NO
            if (i > 0 && j > 0) {
                val cost = substitution(old[i - 1], new[j - 1], adjacency)
                if (cost < NO) best = min(best, d[i - 1][j - 1] + cost)
            }
            // a letter of the old word too much: only a doubled letter counts as mistyping
            if (i > 0 && isDoubled(old, i - 1)) best = min(best, d[i - 1][j] + 1)
            // a letter missing in the old word
            if (j > 0) best = min(best, d[i][j - 1] + 1)
            if (i > 1 && j > 1 && old[i - 1] == new[j - 2] && old[i - 2] == new[j - 1] && old[i - 1] != old[i - 2])
                best = min(best, d[i - 2][j - 2] + 1)
            d[i][j] = min(best, NO)
        }
        return d[n][m]
    }

    private fun isDoubled(word: String, i: Int) =
        (i > 0 && word[i - 1] == word[i]) || (i + 1 < word.length && word[i + 1] == word[i])

    private fun substitution(a: Char, b: Char, adjacency: KeyAdjacency): Int = when {
        a == b -> 0
        sameBaseLetter(a, b) -> 1
        adjacency.adjacent(a, b) -> 1
        else -> NO
    }

    /** "u" and "ü", "e" and "é", "s" and "ß": the same letter with or without a diacritic. */
    @JvmStatic
    fun sameBaseLetter(a: Char, b: Char): Boolean {
        if (a == b) return true
        return base(a) == base(b)
    }

    private fun base(c: Char): String =
        if (c == 'ß') "s" else Normalizer.normalize(c.toString(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}

/** Whether two letters lie next to each other on the keyboard (also diagonally). */
fun interface KeyAdjacency {
    fun adjacent(a: Char, b: Char): Boolean

    companion object {
        /**
         * From key centres and sizes, in any unit: keys of one row (vertical distance below half a key)
         * are neighbours up to 1.5 key widths apart, keys of the rows above and below up to one key
         * width horizontally.
         */
        @JvmStatic
        fun fromKeys(keys: List<KeyBox>): KeyAdjacency {
            val byChar = HashMap<Char, KeyBox>()
            keys.forEach { byChar.putIfAbsent(it.char.lowercaseChar(), it) }
            return KeyAdjacency { a, b ->
                val ka = byChar[a.lowercaseChar()]
                val kb = byChar[b.lowercaseChar()]
                if (ka == null || kb == null || ka === kb) false
                else {
                    val w = (ka.width + kb.width) / 2
                    val h = (ka.height + kb.height) / 2
                    val dx = abs(ka.centerX - kb.centerX)
                    val dy = abs(ka.centerY - kb.centerY)
                    when {
                        dy < h * 0.5f -> dx <= w * 1.5f
                        dy <= h * 1.5f -> dx <= w * 1.0f
                        else -> false
                    }
                }
            }
        }

        /** Rows of letters with the indent of each row in key widths, like a phone keyboard. */
        @JvmStatic
        fun fromRows(rows: List<String>, indents: List<Float>): KeyAdjacency = fromKeys(rows.flatMapIndexed { r, row ->
            row.mapIndexed { i, c -> KeyBox(c, indents.getOrElse(r) { 0f } + i + 0.5f, r + 0.5f, 1f, 1f) }
        })

        /** Used when no keyboard is shown (for example in tests): the German phone layout. */
        @JvmField val QWERTZ: KeyAdjacency = fromRows(listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"), listOf(0f, 0f, 1.5f))
        @JvmField val QWERTY: KeyAdjacency = fromRows(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), listOf(0f, 0.5f, 1.5f))
    }
}

/** One letter key: its letter, centre and size. */
data class KeyBox(val char: Char, val centerX: Float, val centerY: Float, val width: Float, val height: Float)
