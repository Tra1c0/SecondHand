// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.util.AtomicFile
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.agent.security.readBounded
import dev.agentkeyboard.core.SnippetApps
import dev.agentkeyboard.core.SnippetTemplate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * A text snippet of `@snip` (stage 24f). Stage 42: [text] is a [SnippetTemplate]; [apps] limits the
 * apps it is offered in (package names, none = all); [literal] marks a snippet stored before stage 42,
 * inserted as typed until it is saved again in the editor. [toString] never shows the text.
 */
class Snippet(val name: String, val text: String, val apps: List<String> = emptyList(), val literal: Boolean = false) {
    override fun toString() = "Snippet(${text.length} chars, ${apps.size} apps${if (literal) ", literal" else ""})"

    /** What a tap inserts: the template, or for a [literal] snippet its text as typed. */
    val template: SnippetTemplate get() = if (literal) SnippetTemplate.literal(text) else SnippetTemplate.parse(text)
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
        require(isValidName(snippet.name) && isValidText(snippet.text) && isValidApps(snippet.apps))
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
        const val STORAGE_VERSION = 2
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

        /** Stage 42: at most [SnippetApps.MAX_APPS] distinct valid package names. */
        fun isValidApps(apps: List<String>) =
            apps.size <= SnippetApps.MAX_APPS && apps.all { SnippetApps.isValidPackage(it) } && apps.toSet().size == apps.size

        /** Stage 42 writes version 2: `apps` and `literal` only when set, so a plain snippet looks as in version 1. */
        fun encode(snippets: List<Snippet>): String = buildJsonObject {
            put("storageVersion", STORAGE_VERSION)
            put("snippets", buildJsonArray {
                snippets.sortedBy { it.name.lowercase(Locale.ROOT) }.forEach { snippet ->
                    add(buildJsonObject {
                        put("name", snippet.name)
                        put("text", snippet.text)
                        if (snippet.apps.isNotEmpty()) put("apps", buildJsonArray { snippet.apps.forEach { add(JsonPrimitive(it)) } })
                        if (snippet.literal) put("literal", true)
                    })
                }
            })
        }.toString()

        /**
         * Reads version 1 (stage 24f) and 2 (stage 42). A version 1 snippet is [Snippet.literal]: its
         * text is inserted exactly as before, braces included, until the user saves it again.
         */
        fun decode(encoded: String): List<Snippet> {
            val root = Json.parseToJsonElement(encoded).jsonObject
            require(root.keys == setOf("storageVersion", "snippets"))
            val version = root.getValue("storageVersion").jsonPrimitive.int
            require(version == 1 || version == STORAGE_VERSION)
            val rows = root.getValue("snippets").jsonArray
            require(rows.size <= MAX_SNIPPETS)
            val snippets = rows.map { element ->
                val row = element.jsonObject
                if (version == 1) {
                    require(row.keys == setOf("name", "text"))
                    return@map Snippet(row.string("name"), row.string("text"), literal = true)
                        .also { require(isValidName(it.name) && isValidText(it.text)) }
                }
                require(row.keys.containsAll(setOf("name", "text")) && (row.keys - setOf("name", "text", "apps", "literal")).isEmpty())
                val apps = row["apps"]?.let { value ->
                    require(value is JsonArray && value.isNotEmpty()) // written only when not empty
                    value.map { (it as? JsonPrimitive)?.takeIf { app -> app.isString }?.content ?: throw IllegalArgumentException("app") }
                }.orEmpty()
                val literal = row["literal"]?.let { value ->
                    require(value is JsonPrimitive && !value.isString && value.boolean) // written only when true
                    true
                } ?: false
                Snippet(row.string("name"), row.string("text"), apps, literal)
                    .also { require(isValidName(it.name) && isValidText(it.text) && isValidApps(it.apps)) }
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
