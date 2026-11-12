// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.Places
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's saved places for `@ort zuhause` (stage 24f): name and address, at most
 * [Places.MAX_PLACES]. Personal data, so they live in credential-encrypted preferences
 * (`agent_places`, readable only after the first unlock) and not in the keyboard's
 * device-protected settings or its exports. Never logged.
 */
object SavedPlaces {
    const val FILE = "agent_places"
    private const val KEY = "places"

    /** Whether the storage can be read now; replaced in tests. */
    @JvmStatic var available: (Context) -> Boolean = { credentialAccessAllowed(it) }

    private fun prefs(context: Context) = credentialContext(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The saved places; empty while the device is locked or nothing is saved. */
    fun load(context: Context): List<Places.Place> {
        if (!available(context)) return emptyList()
        return runCatching {
            val array = JSONArray(prefs(context).getString(KEY, null) ?: return emptyList())
            (0 until minOf(array.length(), Places.MAX_PLACES)).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                Places.place(item.optString("name"), item.optString("address"))
            }
        }.getOrDefault(emptyList())
    }

    private fun store(context: Context, places: List<Places.Place>): Boolean {
        if (!available(context)) return false
        val array = JSONArray()
        places.forEach { array.put(JSONObject().put("name", it.name).put("address", it.address)) }
        val stored = runCatching { prefs(context).edit().putString(KEY, array.toString()).commit() }.getOrDefault(false)
        PlaceCommand.reset() // `@ort` reads the list again
        return stored
    }

    enum class SaveResult { SAVED, INVALID, FULL, UNAVAILABLE }

    /** Adds a place or replaces the one with the same name. */
    fun save(context: Context, name: String, address: String): SaveResult {
        if (!available(context)) return SaveResult.UNAVAILABLE
        val place = Places.place(name, address) ?: return SaveResult.INVALID
        val places = Places.with(load(context), place) ?: return SaveResult.FULL
        return if (store(context, places)) SaveResult.SAVED else SaveResult.UNAVAILABLE
    }

    fun delete(context: Context, name: String): Boolean = store(context, Places.without(load(context), name))
}
