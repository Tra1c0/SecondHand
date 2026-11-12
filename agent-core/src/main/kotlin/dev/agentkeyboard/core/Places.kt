// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.net.URLEncoder

/**
 * `@ort` (stage 24f): map links for a typed address and the user's saved places ("Zuhause",
 * "Arbeit"). Pure; storing the places is the app's job. Nothing here contacts a server: the link
 * is only text until someone opens it.
 */
object Places {
    const val MAX_PLACES = 10
    const val MAX_NAME = 40
    const val MAX_ADDRESS = 200
    const val MAPS_SEARCH = "https://www.google.com/maps/search/?api=1&query="

    data class Place(val name: String, val address: String)

    /** Google Maps search link (the documented "Maps URLs" form, no API key). */
    fun mapsLink(query: String): String = MAPS_SEARCH + URLEncoder.encode(query, "UTF-8")

    /** A geo: URI that maps apps resolve by search. */
    fun geoUri(query: String): String = "geo:0,0?q=" + URLEncoder.encode(query, "UTF-8").replace("+", "%20")

    private val SPACES = Regex("\\s+")

    private fun clean(text: String) = text.replace(SPACES, " ").trim()

    /** A valid place from user input, or null: both parts present, within limits, no control characters. */
    fun place(name: String, address: String): Place? {
        val n = clean(name)
        val a = clean(address)
        if (n.isEmpty() || a.isEmpty() || n.length > MAX_NAME || a.length > MAX_ADDRESS) return null
        if ((n + a).any { Character.isISOControl(it) }) return null
        return Place(n, a)
    }

    private fun same(a: String, b: String) = clean(a).lowercase() == clean(b).lowercase()

    /** The saved place whose name is exactly [argument], ignoring case and extra spaces. */
    fun find(places: List<Place>, argument: String): Place? = places.firstOrNull { same(it.name, argument) }

    /** [places] with [place] added, or replacing the one with the same name; null when full. */
    fun with(places: List<Place>, place: Place): List<Place>? {
        val index = places.indexOfFirst { same(it.name, place.name) }
        if (index >= 0) return places.toMutableList().also { it[index] = place }
        if (places.size >= MAX_PLACES) return null
        return places + place
    }

    fun without(places: List<Place>, name: String): List<Place> = places.filterNot { same(it.name, name) }
}
