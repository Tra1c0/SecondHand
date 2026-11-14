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
        var reads = 0
        /** The app answers nothing. */
        var unreadable = false
        /** The app gives at most this many characters per read. */
        var limit = Int.MAX_VALUE
        override fun before(n: Int): CharSequence? {
            reads++
            return if (unreadable) null else text.substring(maxOf(0, cursor - minOf(n, limit)), cursor)
        }
        override fun after(n: Int): CharSequence? {
            reads++
            return if (unreadable) null else text.substring(cursor, minOf(text.length, cursor + minOf(n, limit)))
        }
        /** Edits at the cursor as the keyboard does and reports it like InputLogic. */
        fun type(s: String, ranges: PrivateRanges) {
            ranges.beforeEdit(cursor, cursor, this)
            text.insert(cursor, s); val at = cursor; cursor += s.length
            ranges.edited(at, at, s.length, this)
        }
        fun backspace(ranges: PrivateRanges) {
            ranges.beforeEdit(cursor, cursor, this)
            text.deleteCharAt(cursor - 1); cursor--
            ranges.edited(cursor, cursor + 1, 0, this)
        }
        /** A change the keyboard does not report (a picked suggestion, a gesture, the app). */
        fun unseen(at: Int, s: String) {
            text.insert(at, s)
            if (cursor >= at) cursor += s.length
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

    @Test fun aReadCutShortAfterTheCursorForgetsNothing() {
        // review 79a66c8, finding 1: the value lies far after the cursor and the app answers at most 500 characters
        val field = Field("a".repeat(900) + "Geheim42xyz" + " Rest")
        val ranges = PrivateRanges(salt = 7)
        assertTrue(ranges.add(900, 11, field))
        field.unseen(0, "neu ") // an unreported shift, so the hash at the stored place no longer matches
        field.cursor = 0
        field.limit = 500
        ranges.touchesAt(field, 904)
        assertFalse(ranges.isEmpty, "a short answer is no proof that the value is gone")
        // with the whole field readable again, the value is found at its new place
        field.limit = Int.MAX_VALUE
        field.cursor = field.text.length
        assertTrue(ranges.touchesAt(field, 908))
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

    // third review of stages 44/45/49

    @Test fun anEditAfterAnUnseenShiftDoesNotHashTheWrongText() {
        // finding 1: "Hal" picked as "Hallochenfreunde" before the value, then a space: the mark must follow the value
        val value = "Max Peter Mustermann"
        val field = Field(value)
        val ranges = PrivateRanges(salt = 17)
        ranges.add(0, value.length, field)
        field.cursor = 0
        field.type("Hal", ranges)
        field.unseen(3, "lochenfreunde") // the pick, not reported
        field.type(" ", ranges)
        assertEquals("Hallochenfreunde Max Peter Mustermann", field.text.toString())
        val start = field.text.indexOf("Max")
        assertTrue(ranges.touches(field.text.length - 2, field.text.length - 2, field), "the last characters of the value")
        assertTrue(ranges.touches(start, start, field))
        assertFalse(ranges.touches(0, start - 2, field), "the picked word is no private text")
    }

    @Test fun typingFarBeforeAValueReadsNothing() {
        // finding 1: a pure shift keeps the hash, no read per key
        val field = Field("a".repeat(500) + "Geheim42x")
        val ranges = PrivateRanges(salt = 19)
        ranges.add(500, 9, field)
        field.cursor = 0
        field.reads = 0
        field.type("Hallo ", ranges)
        repeat(3) { field.backspace(ranges) }
        assertEquals(0, field.reads)
        assertTrue(ranges.touches(503, 503, field))
        assertEquals(1, ranges.size)
    }

    @Test fun anUnreadableOrShortReadForgetsNothing() {
        // finding 3: only a complete read without the value forgets it
        val field = Field("Code Geheim42x ok")
        val ranges = PrivateRanges(salt = 23)
        ranges.add(5, 9, field)
        field.unreadable = true
        assertTrue(ranges.touches(16, 16, field), "unreadable and near: better refuse")
        assertEquals(1, ranges.size)
        field.unreadable = false
        field.limit = 3 // the app gives fewer characters than there are
        assertTrue(ranges.touches(16, 16, field))
        assertEquals(1, ranges.size)
        field.limit = Int.MAX_VALUE
        assertFalse(ranges.touches(16, 16, field), "readable again: \"ok\" is no private text")
        assertTrue(ranges.touches(8, 8, field))
        assertEquals(1, ranges.size)
        // an edit in the head that cannot be read: the value stays private until it can be hashed again
        field.cursor = 7
        field.unreadable = true
        field.type("x", ranges)
        assertTrue(ranges.touches(8, 8, field))
        field.unreadable = false
        assertTrue(ranges.touches(8, 8, field))
        assertFalse(ranges.touches(0, 3, field))
    }

    @Test fun equalTextNearbyIsPrivateTooAShortValueDoesNotJumpFar() {
        // finding 5: a moved value with an equal text nearby: both count, neither is forgotten
        val field = Field("ab 4711 cd 4711")
        val ranges = PrivateRanges(salt = 29)
        ranges.add(3, 4, field)
        field.unseen(0, "x")
        assertTrue(ranges.touches(5, 5, field))
        assertTrue(ranges.touches(13, 13, field), "ambiguous: the other 4711 too")
        assertFalse(ranges.touches(9, 10, field), "\"cd\"")
        // a short value moved farther than its small window is not looked for there
        val far = Field("ab 4711 cd")
        val short = PrivateRanges(salt = 31)
        short.add(3, 4, far)
        far.unseen(0, "y".repeat(PrivateRanges.SHORT_WINDOW + 10))
        assertFalse(short.touches(far.text.length - 5, far.text.length - 5, far))
        assertTrue(short.isEmpty)
        // a long value is still found a long way off
        val long = Field("ab Geheim42x cd")
        val longRanges = PrivateRanges(salt = 37)
        longRanges.add(3, 9, long)
        long.unseen(0, "y".repeat(500))
        assertTrue(longRanges.touches(506, 506, long))
    }
}
