// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stage 79b: the card "Autofill-Vorschläge" on the Diagnose page shows the counters as numbers, nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h2000dp")
class InlineAutofillCardTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Test fun theCardSaysNoneBeforeAnyResponse() {
        val card = Diagnose.autofill(app, InlineAutofillCounters().snapshot())
        assertEquals(Diagnose.Part.AUTOFILL, card.part)
        val rows = card.rows.associate { it.label to it.value }
        assertTrue(rows["Antworten seit dem Start"]!!.startsWith("noch keine"))
        assertEquals("noch keine Anfrage", rows["Leistenbreite bei der Anfrage"])
        assertFalse(card.rows.any { it.label.startsWith("Letzte Antwort") })
    }

    @Test fun theCardShowsTheLastResponseAsNumbers() {
        val counters = InlineAutofillCounters()
        counters.generationOpen = true
        counters.onRequest(1080)
        counters.onEmpty()
        counters.onRefused()
        var now = 0L
        val response = InlineAutofillResponse(3, { true }, { }, { now })
        counters.onAccepted(response)
        now = 340
        response.onResult(0, true) { }
        response.onResult(2, false) { }
        val rows = Diagnose.autofill(app, counters.snapshot()).rows.associate { it.label to it.value }
        assertEquals("1 angenommen, 1 leer, 1 abgelehnt", rows["Antworten seit dem Start"])
        assertEquals("3", rows["Letzte Antwort: Vorschläge angefragt"])
        assertEquals("1", rows["Letzte Antwort: Chips angekommen"])
        assertEquals("1", rows["Letzte Antwort: leer angekommen"])
        assertEquals("0", rows["Letzte Antwort: verworfen (veraltet)"])
        assertEquals("1", rows["Letzte Antwort: noch nicht beantwortet"])
        assertEquals("1", rows["Letzte Antwort: Chips in der Leiste"])
        assertEquals("340 ms", rows["Zeit bis zur ersten Rückmeldung"])
        assertEquals("ja", rows["Autofill-Kasten gezeigt"])
        assertEquals("1080 px", rows["Leistenbreite bei der Anfrage"])
        assertEquals("aktiv", rows["Eingabe"])
    }
}
