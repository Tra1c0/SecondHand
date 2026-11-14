// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateRangesTest {

    private class Field(text: String) : PrivateRanges.Editor {
        val text = StringBuilder(text)
        override var cursor = text.length
        override fun before(n: Int): CharSequence = text.substring(maxOf(0, cursor - n), cursor)
        override fun after(n: Int): CharSequence = text.substring(cursor, minOf(text.length, cursor + n))
        /** Edits at the cursor as the keyboard does and reports it like InputLogic. */
        fun type(s: String, ranges: PrivateRanges) {
            text.insert(cursor, s); val at = cursor; cursor += s.length
            ranges.edited(at, at, s.length, this)
        }
        fun backspace(ranges: PrivateRanges) {
            text.deleteCharAt(cursor - 1); cursor--
            ranges.edited(cursor, cursor + 1, 0, this)
        }
    }

    private fun PrivateRanges.touchesAt(field: Field, position: Int) = touches(position, position, field)

    @Test fun keepsNoTextOnlyFindsTheValueByItsHash() {
        val field = Field("PIN Geheim42x")
        val ranges = PrivateRanges(salt = 7)
        assertTrue(ranges.add(4, 9, field))
        assertTrue(ranges.touchesAt(field, 13))
        assertTrue(ranges.touchesAt(field, 4)) // its start
        assertFalse(ranges.touches(0, 2, field)) // "PIN"
        // the app replaced the field: the value is gone and forgotten
        field.text.setLength(0); field.text.append("ganz neuer Text"); field.cursor = field.text.length
        assertFalse(ranges.touchesAt(field, 6))
        assertTrue(ranges.isEmpty)
    }

    @Test fun editsBeforeTheValueMoveItAndDeletingInItShortensIt() {
        val field = Field("Geheim42x")
        val ranges = PrivateRanges(salt = 11)
        ranges.add(0, 9, field)
        field.cursor = 0
        field.type("Hallo ", ranges)
        assertEquals("Hallo Geheim42x", field.text.toString())
        assertFalse(ranges.touches(0, 4, field)) // "Hallo"
        assertTrue(ranges.touches(6, 6, field))
        field.backspace(ranges) // "Hallo" + secret
        assertTrue(ranges.touches(5, 5, field))
        field.cursor = field.text.length
        field.backspace(ranges) // "Geheim42"
        assertTrue(ranges.touches(field.cursor, field.cursor, field))
        assertEquals(1, ranges.size)
        repeat(8) { field.backspace(ranges) }
        assertTrue(ranges.isEmpty, "deleted completely")
    }

    @Test fun aValueMovedByAnUnseenEditIsFoundAgain() {
        val field = Field("Code 551177 ok")
        val ranges = PrivateRanges(salt = 3)
        ranges.add(5, 6, field)
        field.text.insert(0, "Neu: "); field.cursor = field.text.length // the app inserted text, the keyboard did not see it
        assertTrue(ranges.touches(10, 10, field))
        assertFalse(ranges.touches(0, 3, field))
    }

    @Test fun upToThreeValuesTheOldestGoesFirst() {
        val field = Field("Max Muster, Hauptstr 5, 0151 123, max@example.com")
        val ranges = PrivateRanges(salt = 5)
        ranges.add(0, 10, field) // name
        ranges.add(12, 10, field) // address
        assertTrue(ranges.touches(1, 1, field))
        assertTrue(ranges.touches(14, 14, field))
        ranges.add(24, 8, field)
        ranges.add(34, 15, field)
        assertEquals(PrivateRanges.MAX, ranges.size)
        assertFalse(ranges.touches(1, 1, field), "the name was the oldest")
        assertTrue(ranges.touches(14, 14, field))
    }

    @Test fun longValuesAreCheckedByTheirHead() {
        val pem = "-----BEGIN PRIVATE KEY-----\n" + "A".repeat(3000) + "\n-----END PRIVATE KEY-----"
        val field = Field(pem + " und dann")
        val ranges = PrivateRanges(salt = 9)
        assertTrue(ranges.add(0, pem.length, field))
        assertTrue(ranges.touches(pem.length - 5, pem.length - 5, field))
        assertFalse(ranges.touches(pem.length + 2, field.text.length, field))
    }

    @Test fun theLearningContextAreTheLastWords() {
        assertEquals("c d".length, PrivateRanges.contextLength("a b c d", 2))
        assertEquals("c d ".length, PrivateRanges.contextLength("a b c d ", 2))
        assertEquals("a b".length, PrivateRanges.contextLength("a b", 4))
        val field = Field("Geheim42x a b c")
        val ranges = PrivateRanges(salt = 13)
        ranges.add(0, 9, field)
        assertTrue(ranges.touchesLearningContext(field, 4), "the secret is the third previous word of c")
        field.type(" d", ranges)
        assertFalse(ranges.touchesLearningContext(field, 4), "learning resumes with the fourth word")
    }

    @Test fun anUnknownCursorRefuses() {
        val field = Field("Geheim42x")
        val ranges = PrivateRanges(salt = 1)
        ranges.add(0, 9, field)
        field.cursor = -1
        assertTrue(ranges.touches(100, 100, field))
        assertEquals(1, ranges.size)
    }
}
