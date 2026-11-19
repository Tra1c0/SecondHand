// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

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
import java.util.UUID

/**
 * Stage 74a: one prompt of the prompt store (a text for AI chats). [id] is made by the store and never
 * shown; [title] is optional free text of one line; [text] is stored exactly as typed. [toString] shows no content.
 */
data class Prompt(val id: String, val title: String, val text: String) {
    override fun toString() = "Prompt(title ${title.length} chars, text ${text.length} chars)"
}

/** Why a write of [PromptStore] did not happen. */
enum class PromptError {
    EMPTY_TEXT, TEXT_TOO_LONG, TITLE_TOO_LONG,
    /** A title or text with an unpaired UTF-16 surrogate (it could not be stored as UTF-8). */
    INVALID_TEXT,
    /** 200 prompts are stored. */
    FULL,
    /** The file would get larger than [PromptStore.MAX_STORAGE_BYTES] (many multi-byte characters). */
    FILE_FULL,
    LOCKED, IO, NOT_FOUND, INVALID_ORDER,
    /** [PromptStore.reset] found a readable file and deleted nothing. */
    READABLE
}

/** What the store can be used for now: [UNUSABLE] is an existing file that cannot be read (corrupt or too large). */
enum class PromptStoreState { READY, LOCKED, UNUSABLE }

/** Outcome of a write: [prompt] is the stored prompt for add and update, [error] is set when [ok] is false. */
class PromptResult private constructor(val prompt: Prompt?, val error: PromptError?) {
    val ok get() = error == null
    override fun toString() = "PromptResult(${error ?: "ok"})"

    companion object {
        internal fun ok(prompt: Prompt? = null) = PromptResult(prompt, null)
        internal fun failed(error: PromptError) = PromptResult(null, error)
    }
}

/**
 * The user's prompts in credential-encrypted, not backed-up storage ("agent_prompts.json"), modelled on
 * [helium314.keyboard.agent.commands.SnippetStore]. The order in the file is the user's order (newest
 * first when added). Nothing is readable or writable while the device is locked. Only the settings page
 * writes. Nothing here logs titles or texts, uses the network or learns from prompts.
 *
 * A file that is too large, not valid or not understood reads as an empty list ([read]); the store itself
 * never rewrites or deletes such a file, and every write first needs a readable file, so it stays as it is and
 * writes fail with [PromptError.IO] (as in the snippet store). One thing a read can do: [AtomicFile.openRead]
 * first finishes an interrupted write, i.e. when a backup file ("agent_prompts.json.bak") exists it deletes the
 * main file and renames the backup in its place, so a read may restore the last completed write.
 */
class PromptStore(context: Context, private val accessible: () -> Boolean = { credentialAccessAllowed(context) }) {
    private val storage = credentialContext(context.applicationContext)
    private val file get() = AtomicFile(File(storage.noBackupFilesDir, FILE_NAME))
    private fun isAccessible() = runCatching(accessible).getOrDefault(false)

    /** The prompts in the user's order; empty when nothing is stored, the device is locked or the file is unusable. */
    fun read(): List<Prompt> = load() ?: emptyList()

    /** Like [read], but null when the device is locked or the file cannot be used (the page says so instead of "empty"). */
    fun load(): List<Prompt>? = synchronized(lock) { if (isAccessible()) runCatching { readFile() }.getOrNull() else null }

    fun state(): PromptStoreState = synchronized(lock) {
        when {
            !isAccessible() -> PromptStoreState.LOCKED
            runCatching { readFile() }.isSuccess -> PromptStoreState.READY
            else -> PromptStoreState.UNUSABLE
        }
    }

    /**
     * Deletes the file and its backup (all stored prompts are lost) to get out of an unusable file. Refused while
     * locked. The file is read again first, under the lock: a readable file (also "no file") is never deleted, the
     * result is [PromptError.READABLE] and the version does not change (the file may have been repaired meanwhile,
     * e.g. by a restored backup, or the page showed an old state).
     */
    fun reset(): PromptResult = synchronized(lock) {
        if (!isAccessible()) return@synchronized PromptResult.failed(PromptError.LOCKED)
        if (runCatching { readFile() }.isSuccess) return@synchronized PromptResult.failed(PromptError.READABLE)
        runCatching { file.delete() }
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) return@synchronized PromptResult.failed(PromptError.IO)
        version++
        PromptResult.ok()
    }

    /** Inserts a new prompt at position 0. */
    fun add(title: String, text: String): PromptResult {
        val clean = validate(title, text) ?: return PromptResult.failed(validationError(title, text)!!)
        return modify { current ->
            if (current.size >= MAX_PROMPTS) return@modify PromptResult.failed(PromptError.FULL)
            var id = UUID.randomUUID().toString()
            while (current.any { it.id == id }) id = UUID.randomUUID().toString()
            val prompt = Prompt(id, clean, text)
            write(listOf(prompt) + current).let { if (it.ok) PromptResult.ok(prompt) else it }
        }
    }

    /** Replaces title and text of the prompt [id], keeping its position. */
    fun update(id: String, title: String, text: String): PromptResult {
        val clean = validate(title, text) ?: return PromptResult.failed(validationError(title, text)!!)
        return modify { current ->
            if (current.none { it.id == id }) return@modify PromptResult.failed(PromptError.NOT_FOUND)
            val prompt = Prompt(id, clean, text)
            write(current.map { if (it.id == id) prompt else it }).let { if (it.ok) PromptResult.ok(prompt) else it }
        }
    }

    fun delete(id: String): PromptResult = modify { current ->
        if (current.none { it.id == id }) return@modify PromptResult.failed(PromptError.NOT_FOUND)
        write(current.filterNot { it.id == id })
    }

    /** Accepted only when [ids] is exactly the stored ids (no more, no fewer, no duplicates); otherwise nothing changes. */
    fun reorder(ids: List<String>): PromptResult = modify { current ->
        // nothing stored and nothing to order: a successful no-op, no file is created
        if (current.isEmpty() && ids.isEmpty()) return@modify PromptResult.ok()
        val byId = current.associateBy { it.id }
        if (ids.size != current.size || ids.toSet().size != ids.size || !byId.keys.containsAll(ids))
            return@modify PromptResult.failed(PromptError.INVALID_ORDER)
        write(ids.map { byId.getValue(it) })
    }

    /** Runs a change on the stored prompts: not while locked, not on a file that cannot be read (it is never overwritten). */
    private fun modify(change: (List<Prompt>) -> PromptResult): PromptResult = synchronized(lock) {
        if (!isAccessible()) return@synchronized PromptResult.failed(PromptError.LOCKED)
        val current = runCatching { readFile() }.getOrNull() ?: return@synchronized PromptResult.failed(PromptError.IO)
        change(current)
    }

    /**
     * Stage 76 (backup): exactly the bytes of the stored file when it exists, is readable and valid and the device
     * is unlocked; otherwise null (nothing to save: no file, locked, too large, not understood).
     */
    fun exportBytes(): ByteArray? = synchronized(lock) {
        if (!isAccessible()) return@synchronized null
        runCatching {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@runCatching null
            val bytes = file.openRead().use { it.readBounded(MAX_STORAGE_BYTES) }
            decodeBytes(bytes)
            bytes
        }.getOrNull()
    }

    /**
     * Stage 76 (restore): replaces all stored prompts by the ones in [bytes] (the content of a backed-up file). The
     * bytes go through the same strict decoder and size limit as the stored file BEFORE anything is touched; invalid
     * bytes give [PromptError.INVALID_TEXT] and change nothing. Same lock, same write ([AtomicFile], version bump)
     * and same refusals as the other writes (locked, unreadable current file).
     */
    fun replaceAll(bytes: ByteArray): PromptResult {
        val prompts = runCatching { decodeBytes(bytes) }.getOrNull() ?: return PromptResult.failed(PromptError.INVALID_TEXT)
        return modify { write(prompts) }
    }

    private fun readFile(): List<Prompt> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { it.readBounded(MAX_STORAGE_BYTES) }
        return decodeBytes(bytes)
    }

    private fun write(prompts: List<Prompt>): PromptResult {
        val bytes = encode(prompts).toByteArray(Charsets.UTF_8)
        // more than a file may hold (many multi-byte characters): it could not be read again
        if (bytes.size > MAX_STORAGE_BYTES) return PromptResult.failed(PromptError.FILE_FULL)
        val target = file
        val output = try { target.startWrite() } catch (_: Exception) { return PromptResult.failed(PromptError.IO) }
        try { output.write(bytes); target.finishWrite(output) }
        catch (_: Exception) { target.failWrite(output); return PromptResult.failed(PromptError.IO) }
        version++
        return PromptResult.ok()
    }

    companion object {
        const val FILE_NAME = "agent_prompts.json"
        const val STORAGE_VERSION = 1
        const val MAX_PROMPTS = 200
        const val MAX_TITLE = 60
        const val MAX_TEXT = 8000
        const val MAX_STORAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_ID = 64
        private val lock = Any()

        /** Counts the successful writes of this process, so a cache of the prompts knows when to read again. */
        @Volatile @JvmStatic var version = 0
            private set

        /** A title is one line: line breaks become spaces; then it is trimmed. */
        fun cleanTitle(title: String) = title.replace(Regex("[\\r\\n\\u2028\\u2029]+"), " ").trim()

        /** What is wrong with a title and text (before saving), or null. Lengths are UTF-16 characters; nothing is ever cut. */
        fun validationError(title: String, text: String): PromptError? = when {
            hasLoneSurrogate(title) || hasLoneSurrogate(text) -> PromptError.INVALID_TEXT
            cleanTitle(title).length > MAX_TITLE -> PromptError.TITLE_TOO_LONG
            text.isBlank() -> PromptError.EMPTY_TEXT
            text.length > MAX_TEXT -> PromptError.TEXT_TOO_LONG
            else -> null
        }

        /** True when [s] has a high or low surrogate that is not part of a pair. */
        fun hasLoneSurrogate(s: String): Boolean {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 < s.length && Character.isLowSurrogate(s[i + 1])) i++ else return true
                } else if (Character.isLowSurrogate(c)) return true
                i++
            }
            return false
        }

        /** The cleaned title when title and text are valid, else null. */
        private fun validate(title: String, text: String): String? =
            if (validationError(title, text) == null) cleanTitle(title) else null

        fun encode(prompts: List<Prompt>): String = buildJsonObject {
            put("storageVersion", STORAGE_VERSION)
            put("prompts", buildJsonArray {
                prompts.forEach { prompt ->
                    add(buildJsonObject { put("id", prompt.id); put("title", prompt.title); put("text", prompt.text) })
                }
            })
        }.toString()

        /** Strict: at most [MAX_STORAGE_BYTES], valid UTF-8, then [decode]; throws otherwise. */
        private fun decodeBytes(bytes: ByteArray): List<Prompt> {
            require(bytes.size <= MAX_STORAGE_BYTES)
            return decode(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString())
        }

        fun decode(encoded: String): List<Prompt> {
            val root = Json.parseToJsonElement(encoded).jsonObject
            require(root.keys == setOf("storageVersion", "prompts"))
            require(root.getValue("storageVersion").jsonPrimitive.int == STORAGE_VERSION)
            val rows = root.getValue("prompts").jsonArray
            require(rows.size <= MAX_PROMPTS)
            val prompts = rows.map { element ->
                val row = element.jsonObject
                require(row.keys == setOf("id", "title", "text"))
                Prompt(row.string("id"), row.string("title"), row.string("text")).also {
                    require(it.id.isNotEmpty() && it.id.length <= MAX_ID)
                    require(it.title == cleanTitle(it.title) && it.title.length <= MAX_TITLE)
                    require(it.text.isNotBlank() && it.text.length <= MAX_TEXT)
                    require(!hasLoneSurrogate(it.title) && !hasLoneSurrogate(it.text))
                }
            }
            require(prompts.map { it.id }.toSet().size == prompts.size)
            return prompts
        }

        private fun JsonObject.string(key: String): String {
            val value = getValue(key) as? JsonPrimitive
            require(value != null && value.isString)
            return value.content
        }
    }
}

private const val PREVIEW_CHARS = 300

/** At most the first [PREVIEW_CHARS] characters (not cut inside a surrogate pair): a row shows two lines, never 8000 characters. */
fun promptPreview(text: String): String {
    val trimmed = text.trim()
    if (trimmed.length <= PREVIEW_CHARS) return trimmed
    val end = if (Character.isHighSurrogate(trimmed[PREVIEW_CHARS - 1])) PREVIEW_CHARS - 1 else PREVIEW_CHARS
    return trimmed.substring(0, end)
}

/** The title of a prompt, or the first non-blank line of its text when it has none. */
fun promptLabel(prompt: Prompt): String =
    prompt.title.ifEmpty { promptPreview(prompt.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()) }
