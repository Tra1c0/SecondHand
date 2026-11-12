// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import dev.agentkeyboard.core.Places.Place
import org.junit.Test
import kotlin.test.*

class PlacesTest {
    @Test fun linksEncodeTheQuery() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=Hauptstra%C3%9Fe+1%2C+10115+Berlin",
            Places.mapsLink("Hauptstraße 1, 10115 Berlin"))
        assertEquals("https://www.google.com/maps/search/?api=1&query=A%26B+%3Fx%3D1%23",
            Places.mapsLink("A&B ?x=1#"))
        assertEquals("geo:0,0?q=Hauptstra%C3%9Fe%201%2C%2010115%20Berlin", Places.geoUri("Hauptstraße 1, 10115 Berlin"))
    }

    @Test fun placesAreCleanedAndBounded() {
        assertEquals(Place("Zuhause", "Hauptstraße 1, Berlin"), Places.place("  Zuhause ", "Hauptstraße 1,\n  Berlin"))
        assertNull(Places.place("", "Berlin"))
        assertNull(Places.place("Zuhause", "  "))
        assertNull(Places.place("x".repeat(Places.MAX_NAME + 1), "Berlin"))
        assertNull(Places.place("Zuhause", "x".repeat(Places.MAX_ADDRESS + 1)))
        assertNull(Places.place("Zu\u0000hause", "Berlin"))
    }

    @Test fun findIgnoresCaseAndSpaces() {
        val places = listOf(Place("Zuhause", "A"), Place("Oma Hilde", "B"))
        assertEquals("A", Places.find(places, "zuhause")?.address)
        assertEquals("B", Places.find(places, " oma   hilde ")?.address)
        assertNull(Places.find(places, "zu"))
        assertNull(Places.find(emptyList(), "zuhause"))
    }

    @Test fun addingReplacesTheSameNameAndStopsWhenFull() {
        var places = listOf(Place("Zuhause", "A"))
        places = Places.with(places, Place("ZUHAUSE", "B"))!!
        assertEquals(listOf(Place("ZUHAUSE", "B")), places)
        places = (1 until Places.MAX_PLACES).fold(places) { list, i -> Places.with(list, Place("P$i", "x"))!! }
        assertEquals(Places.MAX_PLACES, places.size)
        assertNull(Places.with(places, Place("new", "x")))
        assertNotNull(Places.with(places, Place("p1", "y")))
        assertEquals(Places.MAX_PLACES - 1, Places.without(places, "zuhause").size)
    }
}
