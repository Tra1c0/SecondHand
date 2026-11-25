// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Stage 97: positions of the continuous emoji list with a heading above and a divider below the recent emojis. */
class EmojiListPositionsTest {
    // recents (1 page), smileys (3 pages), people (2 pages)
    private val counts = intArrayOf(1, 3, 2)

    @Test fun withRecentEmojisHeadingAndDividerSurroundTheirPages() {
        val p = EmojiListPositions(counts, 0, true)
        // 0 heading, 1 recents, 2 divider, 3..5 smileys, 6..7 people
        assertEquals(1, p.pagePosition(0, 0))
        assertEquals(3, p.pagePosition(1, 0))
        assertEquals(5, p.pagePosition(1, 2))
        assertEquals(6, p.pagePosition(2, 0))
        assertEquals(0, p.scrollTarget(0, 0)) // a tab on the recents shows their heading
        assertEquals(3, p.scrollTarget(1, 0))
        assertEquals(4, p.scrollTarget(1, 1))
        assertArrayEquals(intArrayOf(0, 0), p.at(0)) // heading
        assertArrayEquals(intArrayOf(0, 0), p.at(1))
        assertArrayEquals(intArrayOf(0, 0), p.at(2)) // divider: still the recents
        assertArrayEquals(intArrayOf(1, 0), p.at(3))
        assertArrayEquals(intArrayOf(1, 2), p.at(5))
        assertArrayEquals(intArrayOf(2, 1), p.at(7))
        assertNull(p.at(8))
    }

    @Test fun withoutRecentEmojisThePlainListOfStage93b() {
        val p = EmojiListPositions(counts, 0, false)
        assertEquals(0, p.scrollTarget(0, 0))
        assertEquals(1, p.scrollTarget(1, 0))
        assertEquals(4, p.pagePosition(2, 0))
        assertArrayEquals(intArrayOf(0, 0), p.at(0))
        assertArrayEquals(intArrayOf(1, 2), p.at(3))
        assertArrayEquals(intArrayOf(2, 1), p.at(5))
        assertNull(p.at(6))
    }

    @Test fun theDividerBelongsToTheLastPageOfALongerRecentsBlock() {
        val p = EmojiListPositions(intArrayOf(2, 1), 0, true)
        // 0 heading, 1..2 recents, 3 divider, 4 next
        assertArrayEquals(intArrayOf(0, 1), p.at(3))
        assertArrayEquals(intArrayOf(1, 0), p.at(4))
        assertEquals(2, p.scrollTarget(0, 1))
    }
}
