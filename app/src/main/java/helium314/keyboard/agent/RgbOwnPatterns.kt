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
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.agent.security.readBounded
import helium314.keyboard.latin.utils.prefs
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The own light patterns of stage 34: one preference [PREF] in the keyboard settings next to
 * [RgbLighting.PREF], the library as strict JSON ([RgbPatternJson.encodeLibrary]). The lighting
 * setting names a pattern by its id and content check; [RgbLighting.read] resolves it from here.
 * Every change of the library also rewrites the lighting setting and the saved design profile, so
 * their references follow an edit, a deletion or new ids ([RgbSettings.restamp]). A stored text
 * this version cannot read (newer version, too large, broken) is kept as it is: [locked], and
 * every write is refused. Nothing is logged.
 */
object RgbOwnPatterns {
    const val PREF = "agent_rgb_patterns"

    /** The library; empty for a text that cannot be read (see [locked]). */
    fun library(prefs: SharedPreferences): RgbPatternLibrary = RgbPatternJson.decodeLibrary(prefs.getString(PREF, null))

    /** True when a stored text exists that this version cannot read: it is kept and not written over. */
    fun locked(prefs: SharedPreferences): Boolean = RgbPatternJson.readLibrary(prefs.getString(PREF, null)) == null

    /**
     * Stores [library] and lets the lighting setting and the saved design profile follow (same
     * pattern, new content check or new id from [renamed]; a deleted one falls back to the
     * built-in pattern). False (nothing written) while [locked].
     */
    fun write(context: Context, library: RgbPatternLibrary, renamed: Map<String, String> = emptyMap()): Boolean {
        val prefs = context.prefs()
        val old = RgbPatternJson.readLibrary(prefs.getString(PREF, null)) ?: return false
        if (library.patterns.isEmpty()) prefs.edit().remove(PREF).apply()
        else prefs.edit().putString(PREF, RgbPatternJson.encodeLibrary(library)).apply()
        val text = prefs.getString(RgbLighting.PREF, null)
        if (text != null) {
            val stored = RgbSettings.parse(text)
            if (stored.ownIds().isNotEmpty()) {
                val follows = stored.restamp(old, library, renamed)
                if (follows.encode() != stored.encode()) RgbLighting.write(prefs, follows)
            }
        }
        runCatching { KeyboardProfileStore(context).updateSavedRgb { it.restamp(old, library, renamed) } }
        return true
    }

    /**
     * Gives every pattern of the first build of stage 34 (`p1`, `p2`, … counted up, so a deleted
     * id could come back for another pattern) a random id; the lighting and the saved design
     * profile follow. Does nothing when there is none or the text is [locked].
     */
    fun migrate(context: Context) {
        val library = RgbPatternJson.readLibrary(context.prefs().getString(PREF, null)) ?: return
        if (library.patterns.none { RgbOwnPattern.isLegacyId(it.id) }) return
        val (changed, renamed) = library.withNewIds()
        write(context, changed, renamed)
    }

    /** Adds [pattern] (new id, unique name); null when the library is full or [locked]. */
    fun add(context: Context, pattern: RgbOwnPattern, fallbackName: String = RgbOwnPattern.DEFAULT_NAME): RgbOwnPattern? {
        val prefs = context.prefs()
        if (locked(prefs)) return null
        val (library, made) = library(prefs).add(pattern, fallbackName) ?: return null
        return made.takeIf { write(context, library) }
    }

    /** Saves a changed pattern; the lighting picks it up by its id. */
    fun save(context: Context, pattern: RgbOwnPattern) = write(context, library(context.prefs()).replace(pattern))

    /** Deletes [id]; every element that used it draws its built-in pattern again (the setting is rewritten). */
    fun delete(context: Context, id: String) = write(context, library(context.prefs()).remove(id))

    /** What [importFile] did. */
    sealed interface Imported {
        data class Added(val pattern: RgbOwnPattern) : Imported
        data class Refused(val error: RgbPatternError) : Imported
        data object Full : Imported
        /** The picked document gave no data or broke off while reading. */
        data object Unreadable : Imported
        /** The document is gone or the app may not read it (the picker's grant ended). */
        data object NoAccess : Imported
        /** The stored patterns are [locked]: nothing is added. */
        data object Locked : Imported
    }

    /**
     * Reads a user-picked document (content URIs only, at most [RgbPatternJson.MAX_FILE_BYTES]
     * read) and adds the pattern with a new id. The file can only hold shapes, colours and numbers
     * ([RgbPatternJson.decode]); nothing else is taken over.
     */
    fun importFile(context: Context, uri: Uri, fallbackName: String = RgbOwnPattern.DEFAULT_NAME): Imported {
        if (uri.scheme != "content") return Imported.Refused(RgbPatternError.WRONG_FORMAT)
        if (locked(context.prefs())) return Imported.Locked
        val bytes = try {
            val stream = context.contentResolver.openInputStream(uri) ?: return Imported.Unreadable
            stream.use { it.readBounded(RgbPatternJson.MAX_FILE_BYTES) }
        } catch (_: IllegalArgumentException) {
            return Imported.Refused(RgbPatternError.TOO_LARGE)
        } catch (_: SecurityException) {
            return Imported.NoAccess
        } catch (_: FileNotFoundException) {
            return Imported.NoAccess
        } catch (_: IOException) {
            return Imported.Unreadable
        }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (_: java.nio.charset.CharacterCodingException) {
            return Imported.Refused(RgbPatternError.NOT_JSON)
        }
        return when (val read = RgbPatternJson.decode(text)) {
            is RgbPatternRead.Failed -> Imported.Refused(read.error)
            is RgbPatternRead.Ok -> add(context, read.pattern, fallbackName)?.let { Imported.Added(it) } ?: Imported.Full
        }
    }

    /** Writes [pattern] as a small JSON file to a user-picked document (no id, see [RgbPatternJson.encode]). */
    fun exportFile(context: Context, uri: Uri, pattern: RgbOwnPattern) =
        AgentDocumentIO.writeJson(context, uri, RgbPatternJson.encode(pattern), RgbPatternJson.MAX_FILE_BYTES)
}
