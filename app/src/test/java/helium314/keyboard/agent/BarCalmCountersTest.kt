// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 89: the counters of the card "Leiste" hold numbers only, count changes (not states) and nothing in a protected field. */
@RunWith(RobolectricTestRunner::class)
class BarCalmCountersTest {
    @Before fun reset() = BarCalmCounters.resetForTest()

    @Test fun theFirstKindIsNoChangeAndEveryChangeIsCountedOnce() {
        val c = BarCalmCounters
        c.onShown(BarCalmCounters.Kind.TOOLS, false)
        c.onShown(BarCalmCounters.Kind.TOOLS, false)
        c.onShown(BarCalmCounters.Kind.WORDS, false)
        c.onShown(BarCalmCounters.Kind.EMPTY, false)
        c.onShown(BarCalmCounters.Kind.WORDS, false)
        assertEquals(3, c.total.changes())
        assertEquals(2, c.total.changesTo[BarCalmCounters.Kind.WORDS.ordinal])
        assertEquals(3, c.field.changes())
    }

    @Test fun aNewFieldStartsTheFieldCountsAndARestartDoesNot() {
        val c = BarCalmCounters
        c.onFieldStart(false, false)
        c.onShown(BarCalmCounters.Kind.TOOLS, false); c.onShown(BarCalmCounters.Kind.WORDS, false)
        c.onFieldStart(true, false)
        assertEquals(1, c.field.changes())
        assertEquals(1, c.total.restarts)
        c.onFieldStart(false, false)
        assertEquals(0, c.field.changes())
        assertEquals(1, c.total.changes())
        assertEquals(2, c.total.fieldStarts)
    }

    @Test fun aProtectedFieldCountsNothing() {
        val c = BarCalmCounters
        c.onFieldStart(false, true)
        c.onShown(BarCalmCounters.Kind.WORDS, true)
        c.onDelete(BarCalmCounters.Delete.SLOW, true, true)
        c.onDroppedTap(true)
        assertEquals(0, c.total.fieldStarts + c.total.changes() + c.total.deletes.sum() + c.total.droppedTaps)
        assertFalse(c.slowSeen)
    }

    @Test fun theCardHoldsNumbersAndWordsOfSettingsOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        BarCalmCounters.onDelete(BarCalmCounters.Delete.RESUME, true, false)
        val section = Diagnose.barCalm(context, context.getSharedPreferences("t", Context.MODE_PRIVATE),
            BarCalmCounters.total, BarCalmCounters.field, BarCalmCounters.slowSeen)
        assertTrue(section.rows.size >= 10)
        for (row in section.rows) {
            assertFalse(row.label.contains(": "), "a label has no colon (P454): " + row.label)
            assertTrue(row.value.none { it.isLetter() } || row.value.length < 90, row.value)
        }
        assertEquals("slow", section.rows.first { it.label.contains("connection") }.value)
    }
}
