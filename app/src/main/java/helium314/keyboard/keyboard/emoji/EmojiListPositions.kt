// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

/**
 * Fork, stage 97 (owner: "die zuletzt verwendeten Emojis klar abgegrenzt von den restlichen"): positions in the continuous emoji list
 * of stage 93b when the recent emojis have a heading above and a divider below them. The list is, in the shown order of the
 * categories, the pages of each category; with [marks], one heading item comes right before the pages of the category at
 * [recentsIndex] and one divider item right after them. Without marks (no recent emojis) it is the plain list of 93b.
 *
 * [pageCounts] are the page counts of the shown categories in their order; indices are indices into that order.
 */
class EmojiListPositions(private val pageCounts: IntArray, private val recentsIndex: Int, private val marks: Boolean) {
    private fun hasMarks(index: Int) = marks && index == recentsIndex

    /** The list position of page [page] of the category at [index]. */
    fun pagePosition(index: Int, page: Int): Int {
        var position = 0
        for (i in pageCounts.indices) {
            if (hasMarks(i)) position += 1 // the heading
            if (i == index) return position + page
            position += pageCounts[i]
            if (hasMarks(i)) position += 1 // the divider
        }
        return 0
    }

    /** Where a tab jumps to: page 0 of a category includes its heading, other pages are the pages themselves. */
    fun scrollTarget(index: Int, page: Int): Int =
        if (page == 0 && hasMarks(index)) pagePosition(index, 0) - 1 else pagePosition(index, page)

    /**
     * The category index and the page within it of list position [position], or null past the end. The heading belongs to the first
     * page of its category, the divider to the last one (it is part of the recents block, so the tab stays on the recent emojis).
     */
    fun at(position: Int): IntArray? {
        var start = 0
        for (i in pageCounts.indices) {
            if (hasMarks(i)) {
                if (position == start) return intArrayOf(i, 0)
                start += 1
            }
            if (position < start + pageCounts[i]) return intArrayOf(i, position - start)
            start += pageCounts[i]
            if (hasMarks(i)) {
                if (position == start) return intArrayOf(i, maxOf(0, pageCounts[i] - 1))
                start += 1
            }
        }
        return null
    }
}
