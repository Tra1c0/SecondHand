// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.util.AtomicFile
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.agent.security.readBounded
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
 * shown; [title] is optional free text of one line; [text] is stored exactly as typed. Stage 77: [folderId] is the
 * folder the prompt is in, or null for "no folder". [toString] shows no content.
 */
data class Prompt(val id: String, val title: String, val text: String, val folderId: String? = null) {
    override fun toString() = "Prompt(title ${title.length} chars, text ${text.length} chars)"
}

/** Stage 77: a folder of the prompt store (one level, no nesting). [name] is one trimmed line. [toString] shows no content. */
data class PromptFolder(val id: String, val name: String) {
    override fun toString() = "PromptFolder(name ${name.length} chars)"
}

/**
 * Stage 77: what the store holds: the [folders] in the user's order and all [prompts] in ONE global order. The view of a
 * folder shows its prompts in their relative order in [prompts].
 */
class PromptLibrary(val folders: List<PromptFolder>, val prompts: List<Prompt>) {
    fun isEmpty() = folders.isEmpty() && prompts.isEmpty()

    /** Stage 76 rule for the backup: only prompts count; folders alone are neither saved nor restored. */
    fun hasPrompts() = prompts.isNotEmpty()

    /** The prompts a view with [filter] shows, in the global relative order. */
    fun promptsIn(filter: PromptFilter): List<Prompt> = when (filter) {
        PromptFilter.All -> prompts
        PromptFilter.NoFolder -> prompts.filter { it.folderId == null }
        is PromptFilter.Folder -> prompts.filter { it.folderId == filter.id }
    }

    /** Whether the chip "no folder" is offered: only when folders exist and some prompt has none. */
    fun offersNoFolder() = folders.isNotEmpty() && prompts.any { it.folderId == null }

    /** [filter] if it can be shown for this content, else [PromptFilter.All] (the folder is gone, or "no folder" is not offered). */
    fun effective(filter: PromptFilter): PromptFilter = when (filter) {
        PromptFilter.All -> filter
        PromptFilter.NoFolder -> if (offersNoFolder()) filter else PromptFilter.All
        is PromptFilter.Folder -> if (folders.any { it.id == filter.id }) filter else PromptFilter.All
    }
}

/** Stage 77: which prompts a list shows: all, those without folder, or those of one folder. */
sealed class PromptFilter {
    object All : PromptFilter()
    object NoFolder : PromptFilter()
    data class Folder(val id: String) : PromptFilter() {
        override fun toString() = "Folder"
    }

    /** The folder a new prompt gets while this filter is shown: the shown folder, else none. */
    val folderIdForNew: String? get() = (this as? Folder)?.id

    /** A string for saved UI state; [decode] reads it back. */
    fun encode(): String = when (this) {
        All -> "a"
        NoFolder -> "n"
        is Folder -> "f:$id"
    }

    companion object {
        fun decode(encoded: String): PromptFilter = when {
            encoded == "n" -> NoFolder
            encoded.startsWith("f:") -> Folder(encoded.substring(2))
            else -> All
        }
    }
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
    /** Stage 77: folder name problems (empty after trim, over 40 chars, control character or lone surrogate, already used ignoring case). */
    FOLDER_NAME_EMPTY, FOLDER_NAME_TOO_LONG, FOLDER_NAME_INVALID, FOLDER_NAME_TAKEN,
    /** Stage 77: 50 folders exist. */
    FOLDERS_FULL,
    /** Stage 77: the folder does not exist (any more). */
    FOLDER_NOT_FOUND,
    /** [PromptStore.reset] found a readable file and deleted nothing. */
    READABLE
}

/** What the store can be used for now: [UNUSABLE] is an existing file that cannot be read (corrupt or too large). */
enum class PromptStoreState { READY, LOCKED, UNUSABLE }

/**
 * Outcome of a write: [prompt] is the stored prompt for add and update, [folder] the stored folder for addFolder and
 * renameFolder, [moved] the number of prompts a deleted folder gave back to "no folder"; [error] is set when [ok] is false.
 */
class PromptResult private constructor(val prompt: Prompt?, val error: PromptError?, val folder: PromptFolder? = null, val moved: Int = 0) {
    val ok get() = error == null
    override fun toString() = "PromptResult(${error ?: "ok"})"

    companion object {
        internal fun ok(prompt: Prompt? = null, folder: PromptFolder? = null, moved: Int = 0) = PromptResult(prompt, null, folder, moved)
        internal fun failed(error: PromptError) = PromptResult(null, error)
    }
}

/**
 * The user's prompts in credential-encrypted, not backed-up storage ("agent_prompts.json"), modelled on
 * [helium314.keyboard.agent.commands.SnippetStore]. The order in the file is the user's order (newest
 * first when added). Nothing is readable or writable while the device is locked. Only the settings page
 * writes. Nothing here logs titles or texts, uses the network or learns from prompts.
 *
 * Stage 77: folders (one level). A prompt has an optional folder; the prompts stay in ONE global order. The file is
 * format 1 (no folders, as before stage 77) or format 2 (folders); the store writes format 1 as long as there is no
 * folder, so a file without folders stays byte-compatible with older app versions.
 *
 * A file that is too large, not valid or not understood reads as an empty list ([read]); the store itself
 * never rewrites or deletes such a file, and every write first needs a readable file, so it stays as it is and
 * writes fail with [PromptError.IO] (as in the snippet store). "Not understood" includes a higher format version and a
 * prompt that points to a folder the file does not have: loading it as "no folder" would change data on the next save.
 * One thing a read can do: [AtomicFile.openRead]
 * first finishes an interrupted write, i.e. when a backup file ("agent_prompts.json.bak") exists it deletes the
 * main file and renames the backup in its place, so a read may restore the last completed write.
 */
class PromptStore(context: Context, private val accessible: () -> Boolean = { credentialAccessAllowed(context) }) {
    private val storage = credentialContext(context.applicationContext)
    private val file get() = AtomicFile(File(storage.noBackupFilesDir, FILE_NAME))
    private fun isAccessible() = runCatching(accessible).getOrDefault(false)

    /** All prompts in the user's order; empty when nothing is stored, the device is locked or the file is unusable. */
    fun read(): List<Prompt> = load() ?: emptyList()

    /** Like [read], but null when the device is locked or the file cannot be used (the page says so instead of "empty"). */
    fun load(): List<Prompt>? = loadLibrary()?.prompts

    /** Stage 77: folders and prompts; null when the device is locked or the file cannot be used. */
    fun loadLibrary(): PromptLibrary? = synchronized(lock) { if (isAccessible()) runCatching { readFile() }.getOrNull() else null }

    /** Stage 77: the folders in the user's order; empty when there are none, the device is locked or the file is unusable. */
    fun readFolders(): List<PromptFolder> = loadLibrary()?.folders ?: emptyList()

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

    /** Inserts a new prompt at position 0 (so first in its folder), in [folderId] (null: no folder; a missing folder is refused). */
    fun add(title: String, text: String, folderId: String? = null): PromptResult {
        val clean = validate(title, text) ?: return PromptResult.failed(validationError(title, text)!!)
        return modify { current ->
            if (current.prompts.size >= MAX_PROMPTS) return@modify PromptResult.failed(PromptError.FULL)
            if (folderId != null && current.folders.none { it.id == folderId }) return@modify PromptResult.failed(PromptError.FOLDER_NOT_FOUND)
            var id = UUID.randomUUID().toString()
            while (current.prompts.any { it.id == id }) id = UUID.randomUUID().toString()
            val prompt = Prompt(id, clean, text, folderId)
            write(PromptLibrary(current.folders, listOf(prompt) + current.prompts)).let { if (it.ok) PromptResult.ok(prompt) else it }
        }
    }

    /** Replaces title and text of the prompt [id], keeping its position and its folder. */
    fun update(id: String, title: String, text: String): PromptResult = updateIn(id, title, text, keepFolder = true, folderId = null)

    /** Like the three-argument [update], and moves the prompt to [folderId] (null: no folder). It keeps its place in the global order. */
    fun update(id: String, title: String, text: String, folderId: String?): PromptResult = updateIn(id, title, text, keepFolder = false, folderId = folderId)

    private fun updateIn(id: String, title: String, text: String, keepFolder: Boolean, folderId: String?): PromptResult {
        val clean = validate(title, text) ?: return PromptResult.failed(validationError(title, text)!!)
        return modify { current ->
            val old = current.prompts.firstOrNull { it.id == id } ?: return@modify PromptResult.failed(PromptError.NOT_FOUND)
            val target = if (keepFolder) old.folderId else folderId
            if (target != null && current.folders.none { it.id == target }) return@modify PromptResult.failed(PromptError.FOLDER_NOT_FOUND)
            val prompt = Prompt(id, clean, text, target)
            write(PromptLibrary(current.folders, current.prompts.map { if (it.id == id) prompt else it })).let { if (it.ok) PromptResult.ok(prompt) else it }
        }
    }

    fun delete(id: String): PromptResult = modify { current ->
        if (current.prompts.none { it.id == id }) return@modify PromptResult.failed(PromptError.NOT_FOUND)
        write(PromptLibrary(current.folders, current.prompts.filterNot { it.id == id }))
    }

    /** Accepted only when [ids] is exactly the stored ids (no more, no fewer, no duplicates); otherwise nothing changes. */
    fun reorder(ids: List<String>): PromptResult = modify { current ->
        // nothing stored and nothing to order: a successful no-op, no file is created
        if (current.prompts.isEmpty() && ids.isEmpty()) return@modify PromptResult.ok()
        val byId = current.prompts.associateBy { it.id }
        if (ids.size != current.prompts.size || ids.toSet().size != ids.size || !byId.keys.containsAll(ids))
            return@modify PromptResult.failed(PromptError.INVALID_ORDER)
        write(PromptLibrary(current.folders, ids.map { byId.getValue(it) }))
    }

    /**
     * Stage 77: orders the prompts of one folder ([folderId] null: those without folder). [ids] must be exactly the ids of
     * the prompts that folder holds now; they take the places those prompts have in the global order, in the new order, so
     * every other prompt keeps its place. Otherwise [PromptError.INVALID_ORDER] (or [PromptError.FOLDER_NOT_FOUND]) and nothing changes.
     */
    fun reorderInFolder(folderId: String?, ids: List<String>): PromptResult = modify { current ->
        if (folderId != null && current.folders.none { it.id == folderId }) return@modify PromptResult.failed(PromptError.FOLDER_NOT_FOUND)
        val inFolder = current.prompts.filter { it.folderId == folderId }
        if (inFolder.isEmpty() && ids.isEmpty()) return@modify PromptResult.ok()
        val byId = inFolder.associateBy { it.id }
        if (ids.size != inFolder.size || ids.toSet().size != ids.size || !byId.keys.containsAll(ids))
            return@modify PromptResult.failed(PromptError.INVALID_ORDER)
        val queue = ids.map { byId.getValue(it) }.iterator()
        write(PromptLibrary(current.folders, current.prompts.map { if (it.folderId == folderId) queue.next() else it }))
    }

    /** Stage 77: appends a new folder (last in the user's order). The name is trimmed; see [folderNameError]. */
    fun addFolder(name: String): PromptResult {
        val clean = cleanFolderName(name)
        return modify { current ->
            folderNameError(clean, current.folders, null)?.let { return@modify PromptResult.failed(it) }
            if (current.folders.size >= MAX_FOLDERS) return@modify PromptResult.failed(PromptError.FOLDERS_FULL)
            var id = UUID.randomUUID().toString()
            while (current.folders.any { it.id == id }) id = UUID.randomUUID().toString()
            val folder = PromptFolder(id, clean)
            write(PromptLibrary(current.folders + folder, current.prompts)).let { if (it.ok) PromptResult.ok(folder = folder) else it }
        }
    }

    /** Stage 77: renames a folder, keeping its place and its prompts. A different spelling of its own name is allowed. */
    fun renameFolder(id: String, name: String): PromptResult {
        val clean = cleanFolderName(name)
        return modify { current ->
            if (current.folders.none { it.id == id }) return@modify PromptResult.failed(PromptError.FOLDER_NOT_FOUND)
            folderNameError(clean, current.folders, id)?.let { return@modify PromptResult.failed(it) }
            val folder = PromptFolder(id, clean)
            write(PromptLibrary(current.folders.map { if (it.id == id) folder else it }, current.prompts)).let { if (it.ok) PromptResult.ok(folder = folder) else it }
        }
    }

    /**
     * Stage 77: deletes the folder; its prompts are NOT deleted, they become "no folder" and keep their places. The
     * result's [PromptResult.moved] is how many.
     */
    fun deleteFolder(id: String): PromptResult = modify { current ->
        if (current.folders.none { it.id == id }) return@modify PromptResult.failed(PromptError.FOLDER_NOT_FOUND)
        val moved = current.prompts.count { it.folderId == id }
        write(PromptLibrary(current.folders.filterNot { it.id == id }, current.prompts.map { if (it.folderId == id) it.copy(folderId = null) else it }))
            .let { if (it.ok) PromptResult.ok(moved = moved) else it }
    }

    /** Stage 77: like [reorder], for the folders: accepted only when [ids] is exactly the stored folder ids. */
    fun reorderFolders(ids: List<String>): PromptResult = modify { current ->
        if (current.folders.isEmpty() && ids.isEmpty()) return@modify PromptResult.ok()
        val byId = current.folders.associateBy { it.id }
        if (ids.size != current.folders.size || ids.toSet().size != ids.size || !byId.keys.containsAll(ids))
            return@modify PromptResult.failed(PromptError.INVALID_ORDER)
        write(PromptLibrary(ids.map { byId.getValue(it) }, current.prompts))
    }

    /** Runs a change on the stored content: not while locked, not on a file that cannot be read (it is never overwritten). */
    private fun modify(change: (PromptLibrary) -> PromptResult): PromptResult = synchronized(lock) {
        if (!isAccessible()) return@synchronized PromptResult.failed(PromptError.LOCKED)
        val current = runCatching { readFile() }.getOrNull() ?: return@synchronized PromptResult.failed(PromptError.IO)
        change(current)
    }

    /**
     * Stage 76 (backup): exactly the bytes of the stored file when it exists, is readable and valid and the device
     * is unlocked; otherwise null (nothing to save: no file, no prompts in it, locked, too large, not understood). A file
     * without prompts is no entry, folders alone included: restoring it later would wipe prompts made since.
     */
    fun exportBytes(): ByteArray? = synchronized(lock) {
        if (!isAccessible()) return@synchronized null
        runCatching {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@runCatching null
            val bytes = file.openRead().use { it.readBounded(MAX_STORAGE_BYTES) }
            if (decodeBytes(bytes).prompts.isEmpty()) null else bytes
        }.getOrNull()
    }

    /**
     * Stage 76 (restore): replaces all stored prompts and folders by the ones in [bytes] (the content of a backed-up file,
     * format 1 or 2). The bytes go through the same strict decoder and size limit as the stored file BEFORE anything is
     * touched; invalid bytes give [PromptError.INVALID_TEXT] and change nothing. Same lock, same write ([AtomicFile],
     * version bump) and same refusals as the other writes (locked, unreadable current file).
     */
    fun replaceAll(bytes: ByteArray): PromptResult {
        val library = runCatching { decodeBytes(bytes) }.getOrNull() ?: return PromptResult.failed(PromptError.INVALID_TEXT)
        return modify { write(library) }
    }

    private fun readFile(): PromptLibrary {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return PromptLibrary(emptyList(), emptyList())
        val bytes = file.openRead().use { it.readBounded(MAX_STORAGE_BYTES) }
        return decodeBytes(bytes)
    }

    private fun write(library: PromptLibrary): PromptResult {
        val bytes = encode(library).toByteArray(Charsets.UTF_8)
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
        /** The newest format (with folders). Format 1 (no folders) is still read, and written while there is no folder. */
        const val STORAGE_VERSION = 2
        const val MAX_PROMPTS = 200
        const val MAX_TITLE = 60
        const val MAX_TEXT = 8000
        const val MAX_FOLDERS = 50
        const val MAX_FOLDER_NAME = 40
        const val MAX_STORAGE_BYTES = 2 * 1024 * 1024
        private const val MAX_ID = 64
        private val lock = Any()

        /** Counts the successful writes of this process, so a cache of the prompts knows when to read again. */
        @Volatile @JvmStatic var version = 0
            private set

        /** A title is one line: line breaks become spaces; then it is trimmed. */
        fun cleanTitle(title: String) = title.replace(Regex("[\\r\\n\\u2028\\u2029]+"), " ").trim()

        /** A folder name is trimmed; nothing else is changed (a control character is an error, see [folderNameError]). */
        fun cleanFolderName(name: String) = name.trim()

        /** The key two folder names are compared by: equal keys are the same name ignoring case. */
        private fun folderKey(name: String) = name.lowercase()

        /**
         * What is wrong with the (already cleaned) folder [name] given the other [folders], or null. [ownId] is the folder being
         * renamed, whose own name does not count as taken. Lengths are UTF-16 characters; nothing is ever cut.
         */
        fun folderNameError(name: String, folders: List<PromptFolder>, ownId: String?): PromptError? = when {
            name.isEmpty() -> PromptError.FOLDER_NAME_EMPTY
            hasLoneSurrogate(name) || name.any { Character.isISOControl(it) } -> PromptError.FOLDER_NAME_INVALID
            name.length > MAX_FOLDER_NAME -> PromptError.FOLDER_NAME_TOO_LONG
            folders.any { it.id != ownId && folderKey(it.name) == folderKey(name) } -> PromptError.FOLDER_NAME_TAKEN
            else -> null
        }

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

        /** Format 1 while there are no folders (as before stage 77), else format 2. A prompt of a missing folder is a bug: it throws. */
        fun encode(library: PromptLibrary): String {
            val folders = library.folders
            require(library.prompts.all { p -> p.folderId == null || folders.any { it.id == p.folderId } })
            return buildJsonObject {
                if (folders.isEmpty()) {
                    put("storageVersion", 1)
                    put("prompts", buildJsonArray {
                        library.prompts.forEach { prompt ->
                            add(buildJsonObject { put("id", prompt.id); put("title", prompt.title); put("text", prompt.text) })
                        }
                    })
                } else {
                    put("storageVersion", STORAGE_VERSION)
                    put("folders", buildJsonArray {
                        folders.forEach { folder -> add(buildJsonObject { put("id", folder.id); put("name", folder.name) }) }
                    })
                    put("prompts", buildJsonArray {
                        library.prompts.forEach { prompt ->
                            add(buildJsonObject {
                                put("id", prompt.id); put("title", prompt.title); put("text", prompt.text); put("folderId", prompt.folderId)
                            })
                        }
                    })
                }
            }.toString()
        }

        /** Prompts without folders, as format 1. */
        fun encode(prompts: List<Prompt>): String = encode(PromptLibrary(emptyList(), prompts))

        /** The content of a backed-up file, or null when [bytes] are not valid (too large, not UTF-8, not understood). */
        fun decodeBackup(bytes: ByteArray): PromptLibrary? = runCatching { decodeBytes(bytes) }.getOrNull()

        /** Strict: at most [MAX_STORAGE_BYTES], valid UTF-8, then [decode]; throws otherwise. */
        private fun decodeBytes(bytes: ByteArray): PromptLibrary {
            require(bytes.size <= MAX_STORAGE_BYTES)
            return decode(Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString())
        }

        /** Strict reader of format 1 (no folders) and format 2; throws for anything else (higher version, a prompt of a missing folder, ...). */
        fun decode(encoded: String): PromptLibrary {
            val root = Json.parseToJsonElement(encoded).jsonObject
            val format = root.getValue("storageVersion").jsonPrimitive.int
            require(format == 1 || format == 2)
            require(root.keys == if (format == 1) setOf("storageVersion", "prompts") else setOf("storageVersion", "folders", "prompts"))
            val folders = if (format == 1) emptyList() else root.getValue("folders").jsonArray.map { element ->
                val row = element.jsonObject
                require(row.keys == setOf("id", "name"))
                PromptFolder(row.string("id"), row.string("name")).also {
                    require(it.id.isNotEmpty() && it.id.length <= MAX_ID)
                    require(it.name == cleanFolderName(it.name) && folderNameError(it.name, emptyList(), null) == null)
                }
            }
            require(folders.size <= MAX_FOLDERS)
            require(folders.map { it.id }.toSet().size == folders.size)
            // two names equal ignoring case are NOT refused here: the case tables differ between Android versions, and refusing
            // would make a whole store unreadable. Uniqueness is enforced when a folder is created or renamed.
            val rows = root.getValue("prompts").jsonArray
            require(rows.size <= MAX_PROMPTS)
            val folderIds = folders.map { it.id }.toSet()
            val prompts = rows.map { element ->
                val row = element.jsonObject
                require(row.keys == if (format == 1) setOf("id", "title", "text") else setOf("id", "title", "text", "folderId"))
                val folderId = if (format == 1) null else row.getValue("folderId").let { if (it is JsonNull) null else row.string("folderId") }
                Prompt(row.string("id"), row.string("title"), row.string("text"), folderId).also {
                    require(it.id.isNotEmpty() && it.id.length <= MAX_ID)
                    require(it.title == cleanTitle(it.title) && it.title.length <= MAX_TITLE)
                    require(it.text.isNotBlank() && it.text.length <= MAX_TEXT)
                    require(!hasLoneSurrogate(it.title) && !hasLoneSurrogate(it.text))
                    require(it.folderId == null || it.folderId in folderIds)
                }
            }
            require(prompts.map { it.id }.toSet().size == prompts.size)
            return PromptLibrary(folders, prompts)
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
