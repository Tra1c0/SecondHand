// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import dev.agentkeyboard.core.RgbOwnPattern
import dev.agentkeyboard.core.RgbPatternError
import dev.agentkeyboard.core.RgbPatternJson
import dev.agentkeyboard.core.RgbPatternLibrary
import dev.agentkeyboard.core.RgbPatternRead
import helium314.keyboard.latin.utils.prefs

/**
 * The own light patterns of stage 34: one preference [PREF] in the keyboard settings next to
 * [RgbLighting.PREF], the library as strict JSON ([RgbPatternJson.encodeLibrary]). The lighting
 * setting only names a pattern by its id; [RgbLighting.read] resolves it from here. Nothing is
 * logged; a broken text reads as an empty library (the elements then draw their built-in pattern).
 */
object RgbOwnPatterns {
    const val PREF = "agent_rgb_patterns"

    fun library(prefs: SharedPreferences): RgbPatternLibrary = RgbPatternJson.decodeLibrary(prefs.getString(PREF, null))

    fun write(prefs: SharedPreferences, library: RgbPatternLibrary) {
        if (library.patterns.isEmpty()) prefs.edit().remove(PREF).apply()
        else prefs.edit().putString(PREF, RgbPatternJson.encodeLibrary(library)).apply()
    }

    /** Adds [pattern] (new id, unique name); null when the library is full. */
    fun add(prefs: SharedPreferences, pattern: RgbOwnPattern): RgbOwnPattern? {
        val (library, made) = library(prefs).add(pattern) ?: return null
        write(prefs, library)
        return made
    }

    /** Saves a changed pattern; the lighting picks it up by its id. */
    fun save(prefs: SharedPreferences, pattern: RgbOwnPattern) = write(prefs, library(prefs).replace(pattern))

    /** Renames [id]; false for a name that is not allowed ([RgbOwnPattern.cleanName]). */
    fun rename(prefs: SharedPreferences, id: String, name: String): Boolean {
        val library = library(prefs).rename(id, name) ?: return false
        write(prefs, library)
        return true
    }

    /** Deletes [id]; every element that used it draws its built-in pattern again (the setting is rewritten). */
    fun delete(prefs: SharedPreferences, id: String) {
        val settings = RgbLighting.read(prefs)
        val library = library(prefs).remove(id)
        write(prefs, library)
        if (id in settings.ownIds()) RgbLighting.write(prefs, settings.resolve(library))
    }

    /** What [importFile] did. */
    sealed interface Imported {
        data class Added(val pattern: RgbOwnPattern) : Imported
        data class Refused(val error: RgbPatternError) : Imported
        data object Full : Imported
    }

    /**
     * Reads a user-picked document ([AgentDocumentIO]: content URIs only, at most
     * [RgbPatternJson.MAX_FILE_BYTES] read) and adds the pattern with a new id. The file can only
     * hold shapes, colours and numbers ([RgbPatternJson.decode]); nothing else is taken over.
     */
    fun importFile(context: Context, uri: Uri): Imported {
        if (uri.scheme != "content") return Imported.Refused(RgbPatternError.WRONG_FORMAT)
        val text = try {
            AgentDocumentIO.readJson(context, uri, RgbPatternJson.MAX_FILE_BYTES)
        } catch (_: IllegalArgumentException) {
            return Imported.Refused(RgbPatternError.TOO_LARGE)
        } catch (_: java.nio.charset.CharacterCodingException) {
            return Imported.Refused(RgbPatternError.NOT_JSON)
        }
        return when (val read = RgbPatternJson.decode(text)) {
            is RgbPatternRead.Failed -> Imported.Refused(read.error)
            is RgbPatternRead.Ok -> add(context.prefs(), read.pattern)?.let { Imported.Added(it) } ?: Imported.Full
        }
    }

    /** Writes [pattern] as a small JSON file to a user-picked document (no id, see [RgbPatternJson.encode]). */
    fun exportFile(context: Context, uri: Uri, pattern: RgbOwnPattern) =
        AgentDocumentIO.writeJson(context, uri, RgbPatternJson.encode(pattern), RgbPatternJson.MAX_FILE_BYTES)
}
