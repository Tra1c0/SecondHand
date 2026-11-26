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
 * so the strip can show the chip without a new tap into the field. Since stage 102 the clipboard
 * shows the screenshots from the media store itself ([helium314.keyboard.agent.shelf.Shelf]); no
 * copy goes into the history any more. The system clipboard is never touched. The media store query
 * and the clean-up of old copies run on [background]; the main thread only reads the cached result.
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
    /** At most this many screenshots from one query, newest first. */
    private const val MAX_SHOTS = 10
    /** Before stage 102: "entry id:timestamp" of the history copies made from screenshots, read once by the shelf's migration. */
    private const val HISTORY_ENTRIES = "agent_screenshot_history_entries"
    /** Before stage 102: the decisions about the history copies, removed with the migration. */
    private val OLD_HISTORY_KEYS = listOf("agent_screenshot_history_id", "agent_screenshot_history_ids", "agent_screenshot_history_files")
    /** Before stage 102 the copy for the history was made here; leftovers are removed by [cleanUp]. */
    private const val PENDING_DIRECTORY = "agent_screenshot_pending"

    class Shot(val id: Long, val uri: Uri, val mimeType: String, val addedMillis: Long)

    /**
     * The screenshots of the last [RECENT_MILLIS], newest first (several changes within the debounce
     * time are one query). Replaced in tests, which have no media store. Runs on [background].
     */
    @JvmStatic var finder: (ContentResolver, Long) -> List<Shot> = ::recentScreenshots

    /** Tests: a finder that knows only the newest screenshot (or none). */
    @JvmStatic fun findOnly(newest: (ContentResolver, Long) -> Shot?) { finder = { resolver, now -> listOfNotNull(newest(resolver, now)) } }
    /** Wall clock for ages and expiry; replaced in tests. */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    /**
     * Runs the media store query and the clean-up, never on the main
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
        // stage 102: measuring point (the observer runs on a binder thread)
        onMain { UiPerf.mark(UiPerf.SHOT_VISIBLE, keepEarliest = true) }
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
            val found = try { finder(resolver, now).takeIf { it.isNotEmpty() } } catch (e: Exception) {
                Log.w(TAG, "could not look for a screenshot: ${e.javaClass.simpleName}")
                null
            }
            onMain { deliver(context, found, viaChange) }
        }
    }

    /** Main thread: keeps the query result (the newest is offered) and reports a change to [onChange]. */
    private fun deliver(context: Context, found: List<Shot>?, viaChange: Boolean) {
        querying = false
        val newest = found?.first()
        val changed = newest?.id != cached?.id
        cached = newest
        if (changePending) { refresh(context, clock(), fromChange = true); return } // reported after the newer query
        if (viaChange || changed) postIn(notifier, 0L) // not in place: the caller may be building the strip
    }

    /** Stage 102: the old history copies made from screenshots ("entry id:timestamp"), for the shelf's migration. */
    fun oldHistoryEntries(context: Context): Set<String> = context.prefs().getStringSet(HISTORY_ENTRIES, null).orEmpty().toSet()

    /** Stage 102: the migration is done; the old tracking and decisions go. */
    fun forgetOldHistoryEntries(context: Context) {
        val prefs = context.prefs()
        if ((OLD_HISTORY_KEYS + HISTORY_ENTRIES).none { prefs.contains(it) }) return
        prefs.edit().apply { (OLD_HISTORY_KEYS + HISTORY_ENTRIES).forEach { remove(it) } }.apply()
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
            Log.w(TAG, "could not show the screenshot: ${e.javaClass.simpleName}") // deleted in the meantime, or no longer allowed
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
        Log.w(TAG, "could not copy the screenshot: ${e.javaClass.simpleName}")
        null
    }

    /** The copies sit in the keyboard's device protected storage, so they are kept only briefly. */
    private fun directory(context: Context) = File(AgentStorageContexts.keyboard(context).filesDir, DIRECTORY)

    /**
     * Deletes copies older than [KEEP_MILLIS], or all of them with [all] (switch off). Also deletes
     * copies for the history left in [PENDING_DIRECTORY] (the keyboard stopped between copy and entry):
     * a copy being made is far younger. Lists files: not on the main thread in the keyboard.
     */
    fun cleanUp(context: Context, now: Long, all: Boolean = false) {
        val files = directory(context).listFiles().orEmpty().toList() + pendingFiles()
        val old = files.filter { all || now - it.lastModified() > KEEP_MILLIS }
        old.forEach { it.delete() }
        copiesMayExist = files.size > old.size
    }

    private fun pendingDirectory() = File(helium314.keyboard.latin.database.ClipboardDao.clipFilesDir.parentFile, PENDING_DIRECTORY)

    private fun pendingFiles(): List<File> =
        try { pendingDirectory().listFiles().orEmpty().toList() } catch (_: UninitializedPropertyAccessException) { emptyList() } // no history yet

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
            Log.w(TAG, "could not watch for screenshots: ${e.javaClass.simpleName}")
            observerFailed = true
        }
        stale = true
    }

    /**
     * The keyboard is destroyed: no observer, no posted report or expiry and no handler are left
     * behind. Entries and copies stay (the switch is still on); [start] watches again.
     */
    fun stop(context: Context) {
        onChange = null
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
        stale = true
        main.removeCallbacks(notifier)
        main.removeCallbacks(settle)
        main.removeCallbacks(expiry)
    }

    private fun stopWatching(context: Context) {
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

    /** The images in a screenshot folder added within [RECENT_MILLIS] before [now], newest first. */
    private fun recentScreenshots(resolver: ContentResolver, now: Long): List<Shot> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val path = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND $path LIKE ?"
        val arguments = arrayOf(((now - RECENT_MILLIS) / 1000).toString(), "%screenshot%")
        val shots = ArrayList<Shot>()
        // DATE_ADDED has whole seconds: the id orders screenshots taken within one second
        resolver.query(collection, projection, selection, arguments,
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC")?.use { cursor ->
            while (shots.size < MAX_SHOTS && cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val mimeType = cursor.getString(1) ?: "image/png"
                shots += Shot(id, ContentUris.withAppendedId(collection, id), mimeType, cursor.getLong(2) * 1000)
            }
        }
        return shots
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
        copiesMayExist = true
        expiringShot = null
        finder = ::recentScreenshots
        clock = { System.currentTimeMillis() }
        background = java.util.concurrent.Executor { it.run() }
        main.removeCallbacks(notifier)
        main.removeCallbacks(settle)
        main.removeCallbacks(expiry)
    }
}
