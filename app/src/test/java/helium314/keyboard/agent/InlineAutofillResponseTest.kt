// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 79b: the autofill container is shown when the first chip has been placed, never on the response, and the
 * counters say what happened to every response. The harness repeats the wiring of `LatinIME.onInlineSuggestionsResponse`
 * (generation, counters, first-chip action) without the platform's remote views.
 */
class InlineAutofillResponseTest {
    private class Harness {
        val generation = InlineAutofillGeneration().also { it.open() }
        val counters = InlineAutofillCounters().also { it.generationOpen = true }
        var now = 0L
        /** How often the container was shown, and the order of the events (a chip must be placed before it shows). */
        var shown = 0
        val events = ArrayList<String>()

        /** `onInlineSuggestionsResponse`: null when the response was empty or refused. */
        fun respond(count: Int): InlineAutofillResponse? {
            if (count == 0) { counters.onEmpty(); return null }
            val token = generation.beginResponse()
            if (token == InlineAutofillGeneration.NONE) { counters.onRefused(); return null }
            val response = InlineAutofillResponse(count, { generation.isCurrent(token) }, { shown++; events += "show" }, { now })
            counters.onAccepted(response)
            return response
        }

        fun arrive(response: InlineAutofillResponse, index: Int, hasView: Boolean = true) =
            response.onResult(index, hasView) { events += "place$index@$it" }
    }

    @Test fun theFirstChipShowsTheContainerExactlyOnceAndAfterItIsPlaced() {
        val h = Harness()
        val r = h.respond(3)!!
        assertEquals(0, h.shown) // the response alone shows nothing
        assertFalse(r.shown)
        assertTrue(h.arrive(r, 1))
        assertEquals(1, h.shown)
        assertEquals(listOf("place1@0", "show"), h.events)
        assertFalse(h.arrive(r, 0))
        assertFalse(h.arrive(r, 2))
        assertEquals(1, h.shown)
        assertTrue(r.shown)
        assertEquals(3, r.placed)
    }

    @Test fun nullViewsOnlyNeverShowTheContainer() {
        val h = Harness()
        val r = h.respond(3)!!
        for (i in 0..2) assertFalse(h.arrive(r, i, hasView = false))
        assertEquals(0, h.shown)
        assertFalse(r.shown)
        assertEquals(3, r.nullViews)
        assertEquals(0, r.pending)
        assertEquals(0, r.placed)
    }

    @Test fun aResponseThatIsNeverAnsweredLeavesNoTraceInTheStrip() {
        val h = Harness()
        val r = h.respond(3)!!
        h.now = 14_000
        assertEquals(0, h.shown)
        assertEquals(3, r.pending)
        assertEquals(-1L, r.firstCallbackMillis)
        assertTrue(h.events.isEmpty())
    }

    @Test fun aNullBeforeTheFirstRealChipDoesNotBlockTheShow() {
        val h = Harness()
        val r = h.respond(2)!!
        assertFalse(h.arrive(r, 0, hasView = false))
        assertTrue(h.arrive(r, 1))
        assertEquals(1, h.shown)
    }

    @Test fun aLateChipAfterANewerResponseDoesNothing() {
        val h = Harness()
        val old = h.respond(3)!!
        val newer = h.respond(1)!!
        assertFalse(h.arrive(old, 0))
        assertEquals(0, h.shown)
        assertEquals(1, old.staleDrops)
        assertEquals(0, old.placed)
        assertTrue(h.arrive(newer, 0))
        assertEquals(1, h.shown)
        assertFalse(old.shown)
        assertTrue(newer.shown)
    }

    @Test fun aChipAfterTheInputFinishedDoesNothing() {
        val h = Harness()
        val r = h.respond(3)!!
        h.generation.close()
        assertFalse(h.arrive(r, 0))
        assertEquals(0, h.shown)
        assertEquals(1, r.staleDrops)
    }

    @Test fun aChipAfterTheUserClosedTheChipsDoesNothing() {
        val h = Harness()
        val r = h.respond(3)!!
        h.generation.invalidate() // removeExternalSuggestions
        assertFalse(h.arrive(r, 0))
        assertEquals(0, h.shown)
    }

    @Test fun aRestartOfTheSameInputBeforeTheFirstChipStillShows() {
        val h = Harness()
        val r = h.respond(3)!!
        h.generation.open() // onStartInput(restarting) / a second request
        assertTrue(h.arrive(r, 2))
        assertEquals(1, h.shown)
    }

    @Test fun theMillisecondsToTheFirstCallbackAreTakenFromTheResponseAndKeptFromTheFirstOne() {
        val h = Harness()
        h.now = 1000
        val r = h.respond(3)!!
        h.now = 1250
        h.arrive(r, 2, hasView = false)
        h.now = 9000
        h.arrive(r, 0)
        assertEquals(250L, r.firstCallbackMillis)
    }

    @Test fun everyClassOfOutcomeIsCounted() {
        val h = Harness()
        assertNull(h.respond(0)) // empty
        assertNull(h.respond(0))
        h.generation.close()
        h.counters.generationOpen = false
        assertNull(h.respond(2)) // refused while closed
        h.generation.open()
        h.counters.generationOpen = true
        val old = h.respond(3)!!
        val last = h.respond(4)!!
        h.arrive(old, 0) // stale
        h.arrive(old, 1) // stale
        h.arrive(last, 0)
        h.arrive(last, 1, hasView = false)
        h.arrive(last, 3)
        val s = h.counters.snapshot()
        assertEquals(2, s.accepted)
        assertEquals(2, s.empty)
        assertEquals(1, s.refused)
        assertTrue(s.generationOpen)
        val l = assertNotNull(s.last)
        assertEquals(4, l.count)
        assertEquals(2, l.withView)
        assertEquals(1, l.nullViews)
        assertEquals(0, l.staleDrops)
        assertEquals(1, l.pending)
        assertEquals(2, l.placed)
        assertEquals(2, old.staleDrops)
        assertEquals(1, h.shown)
    }

    @Test fun theDumpLineHasNumbersOnly() {
        val h = Harness()
        assertEquals("InlineAutofill: responses accepted=0 empty=0 refused=0 last=none; stripWidthPx=-1 generation=open", h.counters.line())
        h.counters.onRequest(1080)
        h.now = 10
        val r = h.respond(3)!!
        h.now = 130
        h.arrive(r, 0)
        h.arrive(r, 1, hasView = false)
        assertEquals("InlineAutofill: responses accepted=1 empty=0 refused=0 last: suggestions=3 withView=1 null=1 stale=0 " +
            "pending=1 placed=1 firstCallbackMs=120 shown=true; stripWidthPx=1080 generation=open", h.counters.line())
    }
}
