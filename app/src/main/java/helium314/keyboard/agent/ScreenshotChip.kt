// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.Manifest
import android.content.ClipDescription
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.clipboard.FlatClipboard
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.ClipboardThumbnails
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.prefs
import java.io.File

/**
 * Offers a screenshot taken in the last minutes as a chip in the strip; tapping it inserts the
 * image. Opt-in: needs the switch and the permission to read images. Nothing is inserted without
 * the tap, and no chip appears in password or number fields, in incognito mode or while the
 * device is locked. The image is copied into the keyboard's own file provider, because the
 * receiving app may not be allowed to read the media store.
 *
 * Stage 23g: a new screenshot is reported to [onChange] on the main thread right away (debounced),
 * so the strip can show the chip without a new tap into the field, and, only with the clipboard
 * history switched on, it is added to the keyboard's own clipboard history ([addToHistory]). The
 * system clipboard is never touched. The media store query, the copy for the history and the
 * clean-up of old copies run on [background]; the main thread only reads the cached result.
 */
object ScreenshotChip {
    private const val TAG = "ScreenshotChip"
    const val RECENT_MILLIS = 3 * 60 * 1000L
    /** Served by the clipboard provider (see clipboard_provider_path.xml), outside the clip files. */
    private const val DIRECTORY = "agent_screenshot"
    /** Long enough for the receiving app to read the copy, see [cleanUp]. */
    const val KEEP_MILLIS = 10 * 60 * 1000L
    private const val DISMISSED = "agent_screenshot_dismissed"
    /** Without a working observer the media store is asked at most this often. */
    private const val MIN_QUERY_INTERVAL = 5_000L
    /** The media store reports a screenshot several times while it is written. */
    const val DEBOUNCE_MILLIS = 500L
    /** A screenshot goes into the history only if a media change reported it this soon after it was taken. */
    const val HISTORY_MAX_AGE = 60_000L
    /** Id of the last screenshot decided for the clipboard history (added, refused or excluded): each one once. */
    private const val HISTORY_LAST = "agent_screenshot_history_id"
    /** "entry id:timestamp" of the history entries made from screenshots, removed when the switch goes off. */
    private const val HISTORY_ENTRIES = "agent_screenshot_history_entries"
    /** Former tracking by file name, which could not tell a later copy of the same picture by the user. */
    private const val LEGACY_HISTORY_FILES = "agent_screenshot_history_files"
    /** Next to the clip files: the copy for the history is made here before it gets its entry. */
    private const val PENDING_DIRECTORY = "agent_screenshot_pending"

    class Shot(val id: Long, val uri: Uri, val mimeType: String, val addedMillis: Long)

    /** Replaced in tests, which have no media store. Runs on [background]. */
    @JvmStatic var finder: (ContentResolver, Long) -> Shot? = ::newestScreenshot
    /** Wall clock for ages and expiry; replaced in tests. */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    /**
     * Runs the media store query, the copy for the history and the clean-up, never on the main
     * thread in the keyboard. Not ExecutorUtils.KEYBOARD: FileUtils waits on that one with a latch.
     * [resetForTest] makes it run in place; tests that check the threads set their own.
     */
    @JvmStatic var background: java.util.concurrent.Executor = worker
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    // read and written on the main thread only
    private var cached: Shot? = null
    @Volatile private var stale = true
    private var querying = false
    /** A media change asked for a query (and a report to [onChange]) while one was running. */
    private var changePending = false
    private var observer: ContentObserver? = null
    private var observerFailed = false
    private var lastQuery = 0L
    @Volatile private var appContext: Context? = null
    /** The newest screenshot seen so far; the history decision is made when a new one is first seen. */
    private var lastSeenId = -1L
    /** The screenshot that may go into the history, see [deliver]. */
    private var historyCandidate = -1L
    /** The screenshot whose copy for the history is being made. */
    private var historyPending = -1L
    private val historyFailures = HashMap<Long, Int>()
    /** Last media change while the history was not allowed (locked, incognito, unknown field): older screenshots never go in. */
    @Volatile private var privacyBlockMillis = 0L
    /** Unknown at the start; false after a clean-up left no copy, so [current] need not look again. */
    @Volatile private var copiesMayExist = true
    private var expiringShot: Shot? = null

    /**
     * Called on the main thread after the media store changed (debounced, after the new query) and
     * when an offered screenshot expires; set by the clipboard history manager of the running keyboard.
     */
    @JvmStatic var onChange: (() -> Unit)? = null
    private val notifier = Runnable { onChange?.invoke() }
    /** The media store has settled: query it in the background, [deliver] then reports to [onChange]. */
    private val settle = Runnable {
        val context = appContext
        if (context == null || !isEnabled(context) || !hasPermission(context)) onChange?.invoke()
        else refresh(context, clock(), fromChange = true)
    }
    /** Too old by the wall clock, which may run apart from the uptime the runnable was posted with: else wait again. */
    private val expiry = Runnable {
        val shot = expiringShot
        if (shot != null && clock() - shot.addedMillis in 0..RECENT_MILLIS) scheduleExpiry(shot)
        else onChange?.invoke()
    }

    /** Posted with an absolute time, see [mediaChanged]. */
    private fun postIn(runnable: Runnable, delay: Long) {
        main.removeCallbacks(runnable)
        main.postAtTime(runnable, android.os.SystemClock.uptimeMillis() + delay)
    }

    /** Runs [block] on the main thread: in place when already there, else posted (postAtTime, see P51). */
    private fun onMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
        else main.postAtTime({ block() }, android.os.SystemClock.uptimeMillis())
    }

    /** The observer saw a change: query again, and tell [onChange] once the changes have settled. */
    fun mediaChanged() {
        stale = true
        // decided now, not when the query is done: a screenshot taken while locked or in an
        // incognito field never goes into the history, also when the query fails until later
        appContext?.let { if (!privacyAllowsHistory(it, Settings.getValues())) privacyBlockMillis = clock() }
        postIn(settle, DEBOUNCE_MILLIS)
    }

    /** Lets [onChange] remove the chip of [shot] when it gets too old to be offered. */
    fun scheduleExpiry(shot: Shot, now: Long = clock()) {
        expiringShot = shot
        postIn(expiry, (shot.addedMillis + RECENT_MILLIS - now).coerceAtLeast(0L) + 100L)
    }

    /** Starts watching when the keyboard starts, so that a new screenshot is noticed before the next tap. */
    fun start(context: Context) {
        if (isEnabled(context) && hasPermission(context)) watch(context)
    }

    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.SCREENSHOT_CHIP, false)

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

    /** Android 14 and later: access to selected photos only, which never includes new screenshots. */
    fun hasOnlySelectedPhotos(context: Context) = Build.VERSION.SDK_INT >= 34 && !hasPermission(context) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    /** Only fields that declare they take this kind of image get the chip: no detour over the system clipboard. */
    fun editorAccepts(editorInfo: EditorInfo?, mimeType: String): Boolean {
        if (editorInfo == null) return false
        return androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(editorInfo)
            .any { ClipDescription.compareMimeTypes(mimeType, it) }
    }

    fun isBlocked(settings: SettingsValues?, editorInfo: EditorInfo?): Boolean {
        if (settings == null || settings.mIncognitoModeEnabled || settings.mIsLocked) return true
        val inputType = editorInfo?.inputType ?: return true
        return InputTypeUtils.isAnyPasswordInputType(inputType) || InputTypeUtils.isNumberInputType(inputType)
    }

    /**
     * Whether a screenshot seen now may go into the history: the device is unlocked and the last
     * field is known and not incognito (no-learning and password fields count as incognito). The
     * keyboard need not be shown: then the last field decides, and without one nothing goes in.
     */
    fun privacyAllowsHistory(context: Context, settings: SettingsValues?): Boolean =
        settings != null && !settings.mIncognitoModeEnabled && settings.mInputAttributes.mInputType != InputType.TYPE_NULL
            && !helium314.keyboard.compat.isDeviceLocked(context)

    /** The screenshot to offer now, or null. Never queries on the calling thread, see [newest]. */
    fun current(context: Context, editorInfo: EditorInfo?, now: Long = clock()): Shot? {
        scheduleCleanUp(context) // also when the permission was taken away
        if (!isEnabled(context) || !hasPermission(context)) { stopWatching(context); return null }
        if (isBlocked(Settings.getValues(), editorInfo)) return null
        val shot = newest(context, now) ?: return null
        if (!editorAccepts(editorInfo, shot.mimeType)) return null
        // kept in the settings, so that a used screenshot does not come back after a restart
        if (context.prefs().getLong(DISMISSED, -1L) == shot.id) return null
        return shot
    }

    /**
     * The newest screenshot within [RECENT_MILLIS] as last found, whatever the field; callers check
     * switch and permission. When the media store changed, a new query is started in the background,
     * and [deliver] reports a different result to [onChange].
     */
    private fun newest(context: Context, now: Long): Shot? {
        watch(context)
        if (observerFailed && now - lastQuery >= MIN_QUERY_INTERVAL) stale = true
        if (stale) refresh(context, now, fromChange = false)
        val shot = cached ?: return null
        return if (now - shot.addedMillis in 0..RECENT_MILLIS) shot else null
    }

    /** Starts a query on [background] unless one is running; a change meanwhile queries again afterwards. */
    private fun refresh(context: Context, now: Long, fromChange: Boolean) {
        if (fromChange) changePending = true
        if (querying) return
        querying = true
        stale = false // a change during the query marks it stale again
        val viaChange = changePending
        changePending = false
        lastQuery = now
        val resolver = context.contentResolver
        background.execute {
            val found = try { finder(resolver, now) } catch (e: Exception) {
                Log.w(TAG, "could not look for a screenshot", e)
                null
            }
            onMain { deliver(context, found, viaChange) }
        }
    }

    /**
     * Main thread: keeps the query result. A screenshot seen for the first time is decided for the
     * history here, once: it may go in only if a media change reported it soon after it was taken,
     * no change since it was taken came while the history was not allowed, and the history is
     * allowed now ([privacyAllowsHistory]). Otherwise it is marked as handled for good, so that it
     * is not added later, e.g. after unlocking.
     */
    private fun deliver(context: Context, found: Shot?, viaChange: Boolean) {
        querying = false
        val changed = found?.id != cached?.id
        cached = found
        if (found != null && found.id != lastSeenId) {
            lastSeenId = found.id
            val age = clock() - found.addedMillis
            val allowed = viaChange && age in 0..HISTORY_MAX_AGE && found.addedMillis > privacyBlockMillis
                && privacyAllowsHistory(context, Settings.getValues())
            historyCandidate = if (allowed) found.id else -1L
            if (!allowed) context.prefs().edit().putLong(HISTORY_LAST, found.id).apply()
        }
        if (changePending) { refresh(context, clock(), fromChange = true); return } // reported after the newer query
        if (viaChange || changed) postIn(notifier, 0L) // not in place: the caller may be building the strip
    }

    /**
     * Adds a new screenshot to the keyboard's own clipboard history, never to the system clipboard.
     * Only with the screenshot switch, the permission and the clipboard history on, not while the
     * device is locked or the keyboard is in incognito mode, and only for a screenshot that [deliver]
     * let through when it was first seen. [mayStore] applies the history's file settings (files
     * allowed, size limit) and runs on [background] like the copy and its checksum; the entry is
     * then made on the main thread. The history keeps its own copy of the file (served by the
     * clipboard provider) and deletes it with the entry. Returns whether the copy was started.
     */
    fun addToHistory(
        context: Context, settings: SettingsValues?, dao: helium314.keyboard.latin.database.ClipboardDao?,
        now: Long = clock(), mayStore: (Uri) -> Boolean,
    ): Boolean {
        if (dao == null || settings == null || !settings.mClipboardHistoryEnabled || settings.mIncognitoModeEnabled) return false
        if (!isEnabled(context) || !hasPermission(context) || helium314.keyboard.compat.isDeviceLocked(context)) return false
        val shot = newest(context, now) ?: return false
        if (shot.id != historyCandidate || shot.id == historyPending) return false
        if (context.prefs().getLong(HISTORY_LAST, -1L) == shot.id) return false // also when it was removed from the history since
        historyPending = shot.id
        val pending = File(helium314.keyboard.latin.database.ClipboardDao.clipFilesDir.parentFile, PENDING_DIRECTORY)
        val resolver = context.contentResolver
        background.execute {
            var file: File? = null
            var checksum: String? = null
            var failed = false
            val copy = File(pending, "screenshot-${shot.id}")
            try {
                if (mayStore(shot.uri)) {
                    pending.mkdirs()
                    resolver.openInputStream(shot.uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                        ?: throw IllegalStateException("no stream")
                    checksum = helium314.keyboard.latin.utils.ChecksumCalculator.checksum(copy)
                        ?: throw IllegalStateException("no checksum")
                    file = copy
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not copy the screenshot for the history", e)
                copy.delete()
                failed = true
            }
            val copied = file
            val name = checksum
            onMain { finishHistory(context, dao, shot, copied, name, failed) }
        }
        return true
    }

    /** Main thread: makes the history entry for the copy, see [addToHistory]. */
    private fun finishHistory(
        context: Context, dao: helium314.keyboard.latin.database.ClipboardDao,
        shot: Shot, copy: File?, checksum: String?, failed: Boolean,
    ) {
        historyPending = -1L
        val prefs = context.prefs()
        fun handled() { prefs.edit().putLong(HISTORY_LAST, shot.id).apply() }
        if (failed) {
            // tried once more on the next change; then given up
            val failures = (historyFailures[shot.id] ?: 0) + 1
            historyFailures[shot.id] = failures
            if (failures >= 2) handled()
            return
        }
        handled()
        if (copy == null || checksum == null) return // the history takes no files of this size
        val settings = Settings.getValues()
        if (!isEnabled(context) || settings == null || !settings.mClipboardHistoryEnabled) { copy.delete(); return } // switched off meanwhile
        // like ClipboardDao.addClipUri: named by the checksum of the content
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(shot.mimeType)
        val name = checksum + if (extension == null) "" else ".$extension"
        if (dao.getAll().any { it.filename == name }) { copy.delete(); return } // the same picture is there already: left as it is
        val target = File(helium314.keyboard.latin.database.ClipboardDao.clipFilesDir, name)
        if (!copy.renameTo(target)) { copy.delete(); return }
        dao.clearOldClips()
        dao.insertNewEntry(shot.addedMillis, false, context.getString(R.string.agent_screenshot_chip), name, listOf(shot.mimeType), context)
        val present = dao.getAll().mapTo(HashSet()) { entryKey(it) }
        val entry = dao.getAll().firstOrNull { it.filename == name } ?: return
        val tracked = prefs.getStringSet(HISTORY_ENTRIES, null).orEmpty().filterTo(HashSet()) { it in present }
        tracked += entryKey(entry)
        prefs.edit().putStringSet(HISTORY_ENTRIES, tracked).apply()
    }

    /** Id and time: a copy of the same picture by the user moves the entry to a new time, and it is then the user's. */
    private fun entryKey(entry: helium314.keyboard.latin.ClipboardHistoryEntry) = "${entry.id}:${entry.timeStamp}"

    /** Switch off: removes the history entries made from screenshots, and their files; pinned ones stay. */
    fun removeFromHistory(context: Context) {
        val prefs = context.prefs()
        if (prefs.contains(LEGACY_HISTORY_FILES)) prefs.edit().remove(LEGACY_HISTORY_FILES).apply()
        val keys = prefs.getStringSet(HISTORY_ENTRIES, null)
        if (keys.isNullOrEmpty()) return
        val dao = helium314.keyboard.latin.database.ClipboardDao.getInstance(context) ?: return // locked: later
        if (dao.listener != null) return // never while the history is shown, see ClipboardDao.clearOldClips
        for (index in dao.count() - 1 downTo 0) {
            val entry = dao.getAt(index)
            if (entryKey(entry) in keys && !entry.isPinned) dao.deleteClipAt(index)
        }
        prefs.edit().remove(HISTORY_ENTRIES).apply()
    }

    fun dismiss(context: Context, shot: Shot) { context.prefs().edit().putLong(DISMISSED, shot.id).apply() }

    /** The chip for the strip; [onDone] removes it after a tap or dismissal, [insert] hands the image to the editor. */
    fun createView(
        ime: LatinIME, parent: ViewGroup, shot: Shot, onDone: () -> Unit,
        insert: (InputContentInfoCompat) -> Unit = { ime.mKeyboardActionListener.onContent(it) },
        currentField: () -> EditorInfo? = { ime.currentInputEditorInfo },
    ): View? {
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(ime), parent, false)
        val text = binding.clipboardSuggestionText
        val image = binding.clipboardSuggestionImage
        val close = binding.clipboardSuggestionClose
        val label = ime.getString(R.string.agent_screenshot_chip)
        try {
            val thumbnail = ClipboardThumbnails.fromUri(ime.contentResolver, shot.uri, shot.addedMillis,
                ime.resources.displayMetrics.widthPixels / 4) ?: return null
            image.setImageBitmap(thumbnail)
        } catch (e: Exception) {
            Log.w(TAG, "could not show the screenshot", e) // deleted in the meantime, or no longer allowed
            return null
        }
        image.visibility = View.VISIBLE
        image.contentDescription = label
        text.text = label
        val insert = View.OnClickListener {
            val field = currentField()
            dismiss(ime, shot)
            onDone()
            // copying a full screenshot can take a moment, so not on the main thread
            worker.execute {
                val content = copyForEditor(ime, shot) ?: return@execute
                main.post {
                    // only into the field that was tapped in, and only if it still takes the image
                    val now = currentField()
                    if (isSameField(field, now) && editorAccepts(now, shot.mimeType))
                        insert(content)
                }
            }
        }
        text.setOnClickListener(insert)
        image.setOnClickListener(insert)
        close.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        close.layoutParams.width = text.lineHeight
        close.layoutParams.height = text.lineHeight
        close.setOnClickListener { dismiss(ime, shot); onDone() }

        val colors = Settings.getValues().mColors
        text.setTextColor(colors.get(ColorType.KEY_TEXT))
        colors.setColor(close, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
        if (FlatStyle.isActive(colors))
            FlatClipboard.styleChip(binding.root, text, image, close, true, label) { dismiss(ime, shot); onDone() }
        return binding.root
    }

    fun isSameField(tapped: EditorInfo?, now: EditorInfo?) = tapped != null && now != null &&
        tapped.packageName == now.packageName && tapped.fieldId == now.fieldId && tapped.inputType == now.inputType

    /** Copies the screenshot to the keyboard's provider, so the editor can be granted access. */
    fun copyForEditor(context: Context, shot: Shot): InputContentInfoCompat? = try {
        val directory = directory(context).apply { mkdirs() }
        cleanUp(context, System.currentTimeMillis())
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(shot.mimeType) ?: "img"
        val file = File(directory, "screenshot-${shot.id}.$extension")
        copiesMayExist = true
        context.contentResolver.openInputStream(shot.uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("no stream")
        val uri = FileProvider.getUriForFile(context, context.getString(R.string.clipboard_provider_authority), file)
        InputContentInfoCompat(uri, ClipDescription(context.getString(R.string.agent_screenshot_chip), arrayOf(shot.mimeType)), null)
    } catch (e: Exception) {
        Log.w(TAG, "could not copy the screenshot", e)
        null
    }

    /** The copies sit in the keyboard's device protected storage, so they are kept only briefly. */
    private fun directory(context: Context) = File(AgentStorageContexts.keyboard(context).filesDir, DIRECTORY)

    /** Deletes copies older than [KEEP_MILLIS], or all of them with [all] (switch off). Lists files: not on the main thread in the keyboard. */
    fun cleanUp(context: Context, now: Long, all: Boolean = false) {
        val files = directory(context).listFiles().orEmpty()
        val old = files.filter { all || now - it.lastModified() > KEEP_MILLIS }
        old.forEach { it.delete() }
        copiesMayExist = files.size > old.size
    }

    /** [cleanUp] on [background], only while copies may be left. */
    private fun scheduleCleanUp(context: Context, all: Boolean = false) {
        if (!copiesMayExist) return
        copiesMayExist = false // set again by a copy or by a clean-up that leaves one
        background.execute { cleanUp(context, System.currentTimeMillis(), all) }
    }

    private fun watch(context: Context) {
        if (appContext == null) appContext = context.applicationContext ?: context
        if (observer != null) return
        val newObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { mediaChanged() }
        }
        try {
            context.applicationContext.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, newObserver)
            observer = newObserver
        } catch (e: Exception) {
            Log.w(TAG, "could not watch for screenshots", e)
            observerFailed = true
        }
        stale = true
    }

    private fun stopWatching(context: Context) {
        removeFromHistory(context)
        val current = observer
        if (current == null && cached == null) return
        copiesMayExist = true
        scheduleCleanUp(context, all = true)
        if (current != null) context.applicationContext.contentResolver.unregisterContentObserver(current)
        observer = null
        cached = null
        stale = true
        main.removeCallbacks(notifier)
        main.removeCallbacks(settle)
        main.removeCallbacks(expiry)
    }

    /** The newest image in a screenshot folder added within [RECENT_MILLIS] before [now]. */
    private fun newestScreenshot(resolver: ContentResolver, now: Long): Shot? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val path = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND $path LIKE ?"
        val arguments = arrayOf(((now - RECENT_MILLIS) / 1000).toString(), "%screenshot%")
        resolver.query(collection, projection, selection, arguments, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(0)
            val mimeType = cursor.getString(1) ?: "image/png"
            return Shot(id, ContentUris.withAppendedId(collection, id), mimeType, cursor.getLong(2) * 1000)
        }
        return null
    }

    /** Also makes [background] run in place, so that tests stay in step; tests of the threads set their own. */
    @JvmStatic fun resetForTest() {
        cached = null
        stale = true
        querying = false
        changePending = false
        observer = null
        observerFailed = false
        lastQuery = 0L
        appContext = null
        lastSeenId = -1L
        historyCandidate = -1L
        historyPending = -1L
        historyFailures.clear()
        privacyBlockMillis = 0L
        copiesMayExist = true
        expiringShot = null
        finder = ::newestScreenshot
        clock = { System.currentTimeMillis() }
        background = java.util.concurrent.Executor { it.run() }
        main.removeCallbacks(notifier)
        main.removeCallbacks(settle)
        main.removeCallbacks(expiry)
    }
}
