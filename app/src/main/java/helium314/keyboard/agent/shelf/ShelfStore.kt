// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.shelf

import android.util.AtomicFile
import dev.agentkeyboard.core.clip.ScreenshotShelf
import dev.agentkeyboard.core.clip.ShelfFolder
import dev.agentkeyboard.core.clip.ShelfState
import helium314.keyboard.latin.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stage 102: the screenshot shelf on disk (`docs/STORAGE.md` → „Screenshot-Ablage (102)“): one strict JSON file, written
 * whole with [AtomicFile]. Only ids, times, folder names and pins; never a picture or a path. A file this version cannot
 * read is moved aside to `<name>.bad` (once; an older `.bad` is replaced) so that nothing overwrites it unseen, and the
 * shelf starts empty. Not thread-safe: the shelf calls it from its one writer thread and at the first read.
 */
class ShelfStore(private val file: File) {
    /** The state on disk, an empty one without a file, or an empty one after moving an unreadable file aside. */
    fun load(): Loaded {
        if (!file.exists()) return Loaded(ShelfState(), migrated = false, wasBad = false)
        val bytes = try { AtomicFile(file).readFully() } catch (e: Exception) {
            Log.w(TAG, "could not read the shelf: ${e.javaClass.simpleName}")
            return Loaded(ShelfState(), migrated = false, wasBad = false, readable = false)
        }
        val decoded = decode(String(bytes, Charsets.UTF_8))
        if (decoded != null) return decoded
        Log.w(TAG, "the shelf file is not readable, moved aside")
        val bad = File(file.parentFile, file.name + ".bad")
        bad.delete()
        if (!file.renameTo(bad)) file.delete()
        return Loaded(ShelfState(), migrated = false, wasBad = true)
    }

    /** Writes the whole state; false on an error (then the old file stays). */
    fun save(state: ShelfState, migrated: Boolean): Boolean {
        val atomic = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        return try {
            file.parentFile?.mkdirs()
            stream = atomic.startWrite()
            stream.write(encode(state, migrated).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not write the shelf: ${e.javaClass.simpleName}")
            stream?.let { atomic.failWrite(it) }
            false
        }
    }

    class Loaded(val state: ShelfState, val migrated: Boolean, val wasBad: Boolean, val readable: Boolean = true)

    companion object {
        private const val TAG = "ShelfStore"
        const val FILE_NAME = "agent_screenshot_shelf.json"
        const val VERSION = 1
        /** Far more than any phone has screenshots; a longer list is a broken file. */
        const val MAX_IDS = 200_000
        const val MAX_FOLDERS = 2_000
        private val TOP_KEYS = setOf("version", "clearedBefore", "nextFolderId", "fresh", "loose", "pinned", "folders", "migrated")
        private val FOLDER_KEYS = setOf("id", "name", "number", "pinned", "auto", "members")

        fun encode(state: ShelfState, migrated: Boolean): String {
            val json = JSONObject()
            json.put("version", VERSION)
            json.put("clearedBefore", state.clearedBefore)
            json.put("nextFolderId", state.nextFolderId)
            json.put("fresh", JSONArray(state.fresh))
            json.put("loose", JSONArray(state.loose))
            json.put("pinned", JSONArray(state.pinnedShots.sorted()))
            json.put("folders", JSONArray().apply {
                for (f in state.folders) put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name ?: JSONObject.NULL)
                    put("number", f.number)
                    put("pinned", f.pinned)
                    put("auto", f.auto)
                    put("members", JSONArray(f.members))
                })
            })
            json.put("migrated", migrated)
            return json.toString()
        }

        /** Strict: exactly the known keys, version 1, every screenshot in one place only, names clean, at most one automatic folder. */
        fun decode(text: String): Loaded? = try {
            val json = JSONObject(text)
            require(json.keys().asSequence().toSet() == TOP_KEYS)
            require(json.getInt("version") == VERSION)
            val placed = HashSet<Long>()
            fun ids(array: JSONArray, unique: Boolean): List<Long> {
                require(array.length() <= MAX_IDS)
                return (0 until array.length()).map { i ->
                    val value = array.get(i)
                    require(value is Number && value.toDouble() == value.toLong().toDouble())
                    value.toLong().also { require(it > 0); if (unique) require(placed.add(it)) }
                }
            }
            val fresh = ids(json.getJSONArray("fresh"), true)
            val loose = ids(json.getJSONArray("loose"), true)
            val array = json.getJSONArray("folders")
            require(array.length() <= MAX_FOLDERS)
            val folderIds = HashSet<Long>()
            val folders = (0 until array.length()).map { i ->
                val f = array.getJSONObject(i)
                require(f.keys().asSequence().toSet() == FOLDER_KEYS)
                val id = f.getLong("id")
                require(id > 0 && folderIds.add(id))
                val name = if (f.isNull("name")) null else f.getString("name").also { require(ScreenshotShelf.cleanName(it) == it) }
                ShelfFolder(id, name, f.getInt("number").also { require(it >= 0) }, f.getBoolean("pinned"), f.getBoolean("auto"),
                    ids(f.getJSONArray("members"), true))
            }
            require(folders.count { it.auto } <= 1)
            val pinned = ids(json.getJSONArray("pinned"), false).toSet()
            require(pinned.all { it in placed })
            val next = json.getLong("nextFolderId")
            require(folders.all { it.id < next })
            val clearedBefore = json.getLong("clearedBefore").also { require(it >= 0) }
            Loaded(ShelfState(folders, loose, fresh, pinned, clearedBefore, next), json.getBoolean("migrated"), wasBad = false)
        } catch (_: Exception) {
            null
        }
    }
}
