// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

class ToolTest {
    @Test fun testToolIsDeterministicAndStaysInsideTheKeyboard() {
        assertFalse(ToolId.TEST_COUNT.external)
        val request = Tools.prepare(ToolId.TEST_COUNT, "one  two\nthree")
        assertEquals("chars=14 words=3 lines=2", Tools.runTest(request))
        assertEquals(Tools.runTest(request), Tools.runTest(Tools.prepare(ToolId.TEST_COUNT, "one  two\nthree")))
    }

    @Test fun calendarDraftUsesOnlyTheSelectionAndBoundsEveryField() {
        assertTrue(ToolId.CALENDAR_INSERT.external)
        val request = Tools.prepare(ToolId.CALENDAR_INSERT, "\n  Dentist\tTuesday \nBring the referral.  ")
        assertEquals(listOf("title", "description"), request.fields.map { it.key })
        assertEquals("Dentist Tuesday", request.value("title"))
        assertEquals("Dentist\tTuesday \nBring the referral.", request.value("description"))

        val long = Tools.prepare(ToolId.CALENDAR_INSERT, "x".repeat(119) + "😀" + "y".repeat(4000))
        assertEquals("x".repeat(119), long.value("title")) // never half a surrogate pair
        assertEquals(Tools.MAX_DESCRIPTION_CHARS, long.value("description").length)
    }

    @Test fun invalidSelectionsNeverBecomeRequests() {
        for (tool in ToolId.entries) {
            assertEquals(ToolError.INVALID_INPUT, assertFailsWith<ToolFailure> { Tools.prepare(tool, "") }.error)
            assertFailsWith<ToolFailure> { Tools.prepare(tool, "x".repeat(8_193)) }
            assertFailsWith<ToolFailure> { Tools.prepare(tool, "broken \uD83D") }
        }
        assertFailsWith<ToolFailure> { Tools.prepare(ToolId.CALENDAR_INSERT, " \n\t ") }
    }

    @Test fun requestsAreImmutableAndDoNotPrintTheirText() {
        val request = Tools.prepare(ToolId.CALENDAR_INSERT, "Private appointment")
        assertFails { (request.fields as MutableList<ToolField>).add(ToolField("extra", "x")) }
        assertFalse(request.toString().contains("Private"))
        assertFalse(request.fields.first().toString().contains("Private"))
        assertFails { ToolField("Bad Key", "x") }
        assertNull(ToolId.fromId("calendar.delete"))
    }
}
