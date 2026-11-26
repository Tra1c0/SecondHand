// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.shelf

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.clip.ScreenshotShelf
import dev.agentkeyboard.core.clip.ShelfShot
import dev.agentkeyboard.core.clip.ShelfState
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.agent.ClipVault
import helium314.keyboard.agent.ScreenshotChip
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Stage 102: the screenshot shelf of the running keyboard (`docs/SCREENSHOTS-KONZEPT.md`). Holds the model
 * ([ScreenshotShelf], in agent-core) and the last complete answer of the media store, both read and changed on the main
 * thread only. The media store is asked on [background] ([refresh]), the file is written on its own thread, always the
 * newest state, one write after the other ([persist]). Views listen with [listen]; while a finger drags ([hold]), new
 * answers wait and are applied afterwards, so that nothing moves under the finger.
 */
object Shelf {
    private const val TAG = "Shelf"
    /** Retention above this many minutes means "keep" (as `ClipboardDao.clearOldClips`). */
    private const val RETENTION_UNLIMITED_ABOVE = 120

    class Shot(val id: Long, val uri: Uri, val mimeType: String, val addedMillis: Long)

    // main thread
    var state = ShelfState()
        private set
    /** The last complete answer, newest first. */
    var shots: List<ShelfShot> = emptyList()
        private set
    private var details = HashMap<Long, Shot>()
    private var loaded = false
    private var migrated = false
    private var store: ShelfStore? = null
    private var querying = false
    private var queryAgain = false
    private var holding = false
    private var heldAnswer: List<Shot>? = null
    private val listeners = LinkedHashSet<() -> Unit>()
    /** Wall-clock times of media changes seen while the keyboard was in a private state (E10); kept for two windows. */
    private val privateChanges = ArrayList<Long>()

    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "shelf-writer").apply { isDaemon = true } }
    private val pendingWrite = AtomicReference<Pair<ShelfState, Boolean>?>(null)
    /** The media store query; replaced in tests (they run it in place). */
    @JvmStatic var background: Executor = Executors.newSingleThreadExecutor { Thread(it, "shelf-query").apply { isDaemon = true } }
    /** Writes run here; tests run them in place. */
    @JvmStatic var writes: Executor = writer
    /** The query; replaced in tests, which have no media store. Null: the query failed. */
    @JvmStatic var finder: (ContentResolver) -> List<Shot>? = ::queryScreenshots
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }
    /** The checksum search of the migration (K4); replaced in tests. */
    @JvmStatic var checksumOf: (ContentResolver, Uri) -> String? = { resolver, uri ->
        runCatching { resolver.openInputStream(uri)?.use { helium314.keyboard.latin.utils.ChecksumCalculator.checksum(it) } }.getOrNull()
    }

    /** Everything the shelf needs: the clipboard history, "Screenshots anbieten" and the permission to read images (all opt-in). */
    fun isAvailable(context: Context, settings: SettingsValues?): Boolean =
        settings != null && settings.mClipboardHistoryEnabled && ScreenshotChip.isEnabled(context) && ScreenshotChip.hasPermission(context)

    /** Shown in this field: available, and not locked, incognito or in a password field (as the vault rows, E10). */
    fun isShown(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean =
        isAvailable(context, settings) && !ClipVault.isBlocked(context, settings, editorInfo)

    /**
     * E10 (decision of the lead, 2026-11-26): a screenshot taken while the device is locked, in incognito (also "always
     * incognito" and fields without learning) or while the keyboard is active in a password field is never shown. The keyboard
     * can only know that while it runs: [mediaChanged] notes the time of such a change, [answer] hides the screenshots it reported.
     */
    fun isPrivateNow(context: Context, settings: SettingsValues?): Boolean =
        settings == null || settings.mIncognitoModeEnabled || !ClipVault.mayKeep(context, settings)

    /** Main thread, for every media change the screenshot observer sees (also with the shelf not shown). */
    fun mediaChanged(context: Context, settings: SettingsValues?) {
        if (isPrivateNow(context, settings)) privateChanges += clock()
    }

    fun retentionMillis(settings: SettingsValues?): Long? {
        val minutes = settings?.mClipboardHistoryRetentionTime ?: return null
        return if (minutes > RETENTION_UNLIMITED_ABOVE) null else minutes * 60_000L
    }

    fun shot(id: Long): Shot? = details[id]

    fun listen(listener: () -> Unit) { listeners += listener }
    fun unlisten(listener: () -> Unit) { listeners -= listener }

    /** Reads the file once (small: ids and names). */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val file = File(AgentStorageContexts.keyboard(context).filesDir, ShelfStore.FILE_NAME)
        val fileStore = ShelfStore(file)
        val read = fileStore.load()
        state = read.state
        migrated = read.migrated
        // an unreadable (not broken) file is never overwritten: the shelf then lives in memory only
        store = if (read.readable) fileStore else null
    }

    /**
     * The clipboard view opens: forget what is no longer visible (E3; never while it is shown) and ask the media store
     * again. Returns at once with what is known; a new answer comes to the listeners.
     */
    fun prepare(context: Context, settings: SettingsValues?) {
        ensureLoaded(context)
        apply(ScreenshotShelf.sync(state, shots, clock(), retentionMillis(settings), expire = shots.isNotEmpty()))
        refresh(context, settings, expire = true)
    }

    /** Asks the media store in the background; the answer is applied on the main thread (expire only while no view is shown). */
    fun refresh(context: Context, settings: SettingsValues?, expire: Boolean = false) {
        ensureLoaded(context)
        if (querying) { queryAgain = true; return }
        querying = true
        val resolver = context.applicationContext.contentResolver
        background.execute {
            val found = try { finder(resolver) } catch (e: Exception) {
                Log.w(TAG, "could not ask for screenshots: ${e.javaClass.simpleName}")
                null
            }
            onMain {
                querying = false
                if (found != null) answer(context, found, settings, expire)
                if (queryAgain) { queryAgain = false; refresh(context, settings, expire) }
            }
        }
    }

    private fun answer(context: Context, found: List<Shot>, settings: SettingsValues?, expire: Boolean) {
        if (holding) { heldAnswer = found; return }
        details = found.associateByTo(HashMap()) { it.id }
        shots = found.map { ShelfShot(it.id, it.addedMillis) }
        val now = clock()
        privateChanges.removeAll { now - it > 2 * ScreenshotShelf.PRIVATE_WINDOW_MILLIS }
        val taken = ScreenshotShelf.takenPrivately(state, shots, privateChanges)
        apply(ScreenshotShelf.sync(ScreenshotShelf.hide(state, taken), shots, now, retentionMillis(settings), expire))
        if (!migrated) migrate(context)
    }

    /** While a finger drags: answers wait ([answer]). Released, the newest waiting answer is applied. */
    fun hold(context: Context, settings: SettingsValues?, on: Boolean) {
        holding = on
        if (on) return
        val waiting = heldAnswer ?: return
        heldAnswer = null
        answer(context, waiting, settings, expire = false)
    }

    /** Takes a new state from an operation of the user; the same instance changes nothing. */
    fun apply(next: ShelfState) {
        if (next === state) return
        state = next
        persist()
        listeners.toList().forEach { it() }
    }

    /** "Zwischenablage leeren" (E11): unprotected screenshots up to now leave the clipboard. */
    fun clear(context: Context, settings: SettingsValues?) {
        ensureLoaded(context)
        apply(ScreenshotShelf.sync(ScreenshotShelf.clear(state, clock()), shots, clock(), retentionMillis(settings), expire = true))
    }

    /** Switch or history off: only pinned screenshots and pinned folders stay (E11). */
    fun keepPinnedOnly(context: Context) {
        ensureLoaded(context)
        apply(ScreenshotShelf.keepPinnedOnly(state, clock()))
    }

    private fun persist() {
        val target = store ?: return
        pendingWrite.set(state to migrated)
        writes.execute {
            val next = pendingWrite.getAndSet(null) ?: return@execute
            target.save(next.first, next.second)
        }
    }

    // ---- migration of the screenshot copies of the history before stage 102 (E13, K4)

    private fun migrate(context: Context) {
        val dao = helium314.keyboard.latin.database.ClipboardDao.getInstance(context) ?: return // locked: later
        if (dao.listener != null) return // never while the history is shown (as ClipboardDao.clearOldClips)
        val tracked = ScreenshotChip.oldHistoryEntries(context)
        if (tracked.isEmpty()) { finishMigration(context); return }
        val trackedIds = tracked.mapNotNullTo(HashSet()) { it.substringBefore(':').toLongOrNull() }
        val copies = dao.getAll().filter { it.id in trackedIds && it.filename != null }.map {
            ScreenshotShelf.OldCopy(it.id, it.timeStamp, it.isPinned, "${it.id}:${it.timeStamp}" in tracked,
                it.filename?.substringBefore('.')?.takeIf { name -> name.length == 64 })
        }
        val (drop, search) = ScreenshotShelf.planMigration(copies, shots)
        removeCopies(dao, drop.map { it.entryId })
        if (search.isEmpty()) { finishMigration(context); return }
        // the checksum of up to 300 of the newest screenshots, once, in the background
        val candidates = details.values.sortedByDescending { it.addedMillis }.take(300)
        val wanted = search.associateBy { it.checksum!! }
        val resolver = context.applicationContext.contentResolver
        migrated = true // not started twice; written with the result
        background.execute {
            val found = HashMap<Long, Long>() // entry id -> screenshot id
            for (shot in candidates) {
                if (found.size == wanted.size) break
                val sum = checksumOf(resolver, shot.uri) ?: continue
                wanted[sum]?.let { found[it.entryId] = shot.id }
            }
            onMain {
                val daoNow = helium314.keyboard.latin.database.ClipboardDao.getInstance(context)
                if (daoNow != null && daoNow.listener == null && found.isNotEmpty()) {
                    var next = state
                    for (id in found.values) next = ScreenshotShelf.adoptPinned(next, id)
                    removeCopies(daoNow, found.keys.toList())
                    apply(next)
                }
                finishMigration(context)
            }
        }
    }

    private fun removeCopies(dao: helium314.keyboard.latin.database.ClipboardDao, ids: List<Long>) {
        if (ids.isEmpty()) return
        val gone = ids.toHashSet()
        for (index in dao.count() - 1 downTo 0) if (dao.getAt(index).id in gone) dao.deleteClipAt(index)
    }

    private fun finishMigration(context: Context) {
        migrated = true
        ScreenshotChip.forgetOldHistoryEntries(context)
        persist()
    }

    // ---- the media store

    /** Every screenshot the media store has (E2): `screenshot` in the folder path, not pending, newest first. Null on failure. */
    fun queryScreenshots(resolver: ContentResolver): List<Shot>? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val path = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_ADDED)
        val selection = "$path LIKE ?" + if (Build.VERSION.SDK_INT >= 29) " AND ${MediaStore.MediaColumns.IS_PENDING} = 0" else ""
        val shots = ArrayList<Shot>()
        resolver.query(collection, projection, selection, arrayOf("%screenshot%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                shots += Shot(id, ContentUris.withAppendedId(collection, id), cursor.getString(1) ?: "image/png", cursor.getLong(2) * 1000)
            }
        } ?: return null
        return shots
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** Tests: forget everything; queries and writes then run in place. */
    @JvmStatic fun resetForTest() {
        state = ShelfState()
        shots = emptyList()
        details = HashMap()
        loaded = false
        migrated = false
        store = null
        querying = false
        queryAgain = false
        holding = false
        heldAnswer = null
        listeners.clear()
        privateChanges.clear()
        pendingWrite.set(null)
        background = Executor { it.run() }
        writes = Executor { it.run() }
        finder = ::queryScreenshots
        clock = { System.currentTimeMillis() }
        checksumOf = { resolver, uri -> runCatching { resolver.openInputStream(uri)?.use { helium314.keyboard.latin.utils.ChecksumCalculator.checksum(it) } }.getOrNull() }
    }
}
