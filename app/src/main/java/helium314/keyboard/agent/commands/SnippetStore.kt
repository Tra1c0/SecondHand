// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.util.AtomicFile
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.agent.security.readBounded
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** A text snippet of `@snip` (stage 24f). [toString] never shows the text. */
class Snippet(val name: String, val text: String) {
    override fun toString() = "Snippet(${text.length} chars)"
}

/**
 * The user's snippets in credential-encrypted, not backed-up storage ("agent_snippets.json"),
 * like the workflows: they may hold addresses or other personal text. Only the settings page
 * writes; the keyboard reads while `@snip` is typed. Nothing here logs names or texts.
 */
class SnippetStore(context: Context, private val accessible: () -> Boolean = { credentialAccessAllowed(context) }) {
    private val storage = credentialContext(context.applicationContext)
    private val file get() = AtomicFile(File(storage.noBackupFilesDir, FILE_NAME))
    private fun checkAccess() { check(runCatching(accessible).getOrDefault(false)) { "Snippets unavailable" } }

    /** Sorted by name; empty when nothing is stored. Throws when locked or unreadable. */
    fun read(): List<Snippet> = synchronized(lock) {
        checkAccess()
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized emptyList()
        val bytes = file.openRead().use { it.readBounded(MAX_STORAGE_BYTES) }
        decode(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString())
    }

    /** Adds [snippet], or replaces the one called [previousName] (renaming it). Names are unique regardless of case. */
    fun put(snippet: Snippet, previousName: String? = null) = synchronized(lock) {
        require(isValidName(snippet.name) && isValidText(snippet.text))
        val current = read()
        // a new snippet never replaces an existing one; an edited one replaces only itself
        if (previousName != null) require(current.any { it.name.equals(previousName, ignoreCase = true) }) { "Snippet gone" }
        val others = if (previousName == null) current else current.filterNot { it.name.equals(previousName, ignoreCase = true) }
        require(others.none { it.name.equals(snippet.name, ignoreCase = true) }) { "Name taken" }
        require(others.size < MAX_SNIPPETS) { "Too many snippets" }
        write(others + snippet)
    }

    fun delete(name: String) = synchronized(lock) {
        write(read().filterNot { it.name.equals(name, ignoreCase = true) })
    }

    private fun write(snippets: List<Snippet>) {
        checkAccess()
        val bytes = encode(snippets).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STORAGE_BYTES)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    companion object {
        const val FILE_NAME = "agent_snippets.json"
        const val MAX_SNIPPETS = 50
        const val MAX_NAME = 24
        const val MAX_TEXT = 2000
        // all texts at their longest with escaped multi-byte characters stay far below this
        private const val MAX_STORAGE_BYTES = 1_048_576
        private val lock = Any()

        /** One word of letters (any script), at most [MAX_NAME]. */
        fun isValidName(name: String) = name.length in 1..MAX_NAME && name.all { it.isLetter() }

        /** Not blank, at most [MAX_TEXT] characters; line breaks are allowed. */
        fun isValidText(text: String) = text.isNotBlank() && text.length <= MAX_TEXT

        fun encode(snippets: List<Snippet>): String = buildJsonObject {
            put("storageVersion", 1)
            put("snippets", buildJsonArray {
                snippets.sortedBy { it.name.lowercase(Locale.ROOT) }.forEach { snippet ->
                    add(buildJsonObject { put("name", snippet.name); put("text", snippet.text) })
                }
            })
        }.toString()

        fun decode(encoded: String): List<Snippet> {
            val root = Json.parseToJsonElement(encoded).jsonObject
            require(root.keys == setOf("storageVersion", "snippets") && root.getValue("storageVersion").jsonPrimitive.int == 1)
            val rows = root.getValue("snippets").jsonArray
            require(rows.size <= MAX_SNIPPETS)
            val snippets = rows.map { element ->
                val row = element.jsonObject
                require(row.keys == setOf("name", "text"))
                Snippet(row.string("name"), row.string("text")).also { require(isValidName(it.name) && isValidText(it.text)) }
            }
            require(snippets.map { it.name.lowercase(Locale.ROOT) }.toSet().size == snippets.size)
            return snippets.sortedBy { it.name.lowercase(Locale.ROOT) }
        }

        private fun JsonObject.string(key: String): String {
            val value = getValue(key) as? JsonPrimitive
            require(value != null && value.isString)
            return value.content
        }
    }
}
