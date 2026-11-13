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
 * so the strip can show the chip without a new tap into the field, and, only with the clipboard
 * history switched on, it is added to the keyboard's own clipboard history ([addToHistory]). The
 * system clipboard is never touched.
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
    /** Id of the last screenshot offered to the clipboard history: each one is tried once. */
    private const val HISTORY_LAST = "agent_screenshot_history_id"
    /** File names of the history entries made from screenshots, removed when the switch goes off. */
    private const val HISTORY_FILES = "agent_screenshot_history_files"

    class Shot(val id: Long, val uri: Uri, val mimeType: String, val addedMillis: Long)

    /** Replaced in tests, which have no media store. */
    @JvmStatic var finder: (ContentResolver, Long) -> Shot? = ::newestScreenshot

    private var cached: Shot? = null
    @Volatile private var stale = true
    private var observer: ContentObserver? = null
    private var observerFailed = false
    private var lastQuery = 0L
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Called on the main thread after the media store changed (debounced) and when an offered
     * screenshot expires; set by the clipboard history manager of the running keyboard.
     */
    @JvmStatic var onChange: (() -> Unit)? = null
    private val notifier = Runnable { onChange?.invoke() }
    private val expiry = Runnable { onChange?.invoke() }

    /** Posted with an absolute time, see [mediaChanged]. */
    private fun postIn(runnable: Runnable, delay: Long) {
        main.removeCallbacks(runnable)
        main.postAtTime(runnable, android.os.SystemClock.uptimeMillis() + delay)
    }

    /** The observer saw a change: query again, and tell [onChange] once the changes have settled. */
    fun mediaChanged() {
        stale = true
        postIn(notifier, DEBOUNCE_MILLIS)
    }

    /** Lets [onChange] remove the chip of [shot] when it gets too old to be offered. */
    fun scheduleExpiry(shot: Shot, now: Long = System.currentTimeMillis()) {
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

    /** The screenshot to offer now, or null. */
    fun current(context: Context, editorInfo: EditorInfo?, now: Long = System.currentTimeMillis()): Shot? {
        cleanUp(context, System.currentTimeMillis()) // also when the permission was taken away
        if (!isEnabled(context) || !hasPermission(context)) { stopWatching(context); return null }
        if (isBlocked(Settings.getValues(), editorInfo)) return null
        val shot = newest(context, now) ?: return null
        if (!editorAccepts(editorInfo, shot.mimeType)) return null
        // kept in the settings, so that a used screenshot does not come back after a restart
        if (context.prefs().getLong(DISMISSED, -1L) == shot.id) return null
        return shot
    }

    /** The newest screenshot within [RECENT_MILLIS], whatever the field; callers check switch and permission. */
    private fun newest(context: Context, now: Long): Shot? {
        watch(context)
        if (observerFailed && now - lastQuery >= MIN_QUERY_INTERVAL) stale = true
        if (stale) {
            stale = false // a change during the query marks it stale again
            lastQuery = now
            cached = try { finder(context.contentResolver, now) } catch (e: Exception) {
                Log.w(TAG, "could not look for a screenshot", e)
                null
            }
        }
        val shot = cached ?: return null
        return if (now - shot.addedMillis in 0..RECENT_MILLIS) shot else null
    }

    /**
     * Adds a new screenshot to the keyboard's own clipboard history, never to the system clipboard.
     * Only with the screenshot switch, the permission and the clipboard history on, and not while
     * the device is locked or the keyboard is in incognito mode. The history keeps its own copy of
     * the file (served by the clipboard provider) and deletes it with the entry. [mayStore] applies
     * the history's file settings (files allowed, size limit). Each screenshot is tried only once.
     */
    fun addToHistory(
        context: Context, settings: SettingsValues?, dao: helium314.keyboard.latin.database.ClipboardDao?,
        now: Long = System.currentTimeMillis(), mayStore: (Uri) -> Boolean,
    ): Boolean {
        if (dao == null || settings == null || !settings.mClipboardHistoryEnabled || settings.mIncognitoModeEnabled) return false
        if (!isEnabled(context) || !hasPermission(context) || helium314.keyboard.compat.isDeviceLocked(context)) return false
        val shot = newest(context, now) ?: return false
        val prefs = context.prefs()
        if (prefs.getLong(HISTORY_LAST, -1L) == shot.id) return false // also when it was removed from the history since
        prefs.edit().putLong(HISTORY_LAST, shot.id).apply()
        if (!mayStore(shot.uri)) return false
        val before = dao.getAll().mapNotNullTo(HashSet()) { it.filename }
        dao.addClipUri(shot.addedMillis, false, shot.uri,
            ClipDescription(context.getString(R.string.agent_screenshot_chip), arrayOf(shot.mimeType)), context)
        val present = dao.getAll().mapNotNullTo(HashSet()) { it.filename }
        val added = present - before // the same picture already in the history stays an entry of its own
        val tracked = prefs.getStringSet(HISTORY_FILES, null).orEmpty().filterTo(HashSet()) { it in present } + added
        prefs.edit().putStringSet(HISTORY_FILES, tracked).apply()
        return added.isNotEmpty()
    }

    /** Switch off: removes the history entries made from screenshots, and their files; pinned ones stay. */
    fun removeFromHistory(context: Context) {
        val prefs = context.prefs()
        val names = prefs.getStringSet(HISTORY_FILES, null)
        if (names.isNullOrEmpty()) return
        val dao = helium314.keyboard.latin.database.ClipboardDao.getInstance(context) ?: return // locked: later
        if (dao.listener != null) return // never while the history is shown, see ClipboardDao.clearOldClips
        for (index in dao.count() - 1 downTo 0) {
            val entry = dao.getAt(index)
            if (entry.filename in names && !entry.isPinned) dao.deleteClipAt(index)
        }
        prefs.edit().remove(HISTORY_FILES).apply()
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

    /** Deletes copies older than [KEEP_MILLIS], or all of them with [all] (switch off). */
    fun cleanUp(context: Context, now: Long, all: Boolean = false) {
        directory(context).listFiles()?.filter { all || now - it.lastModified() > KEEP_MILLIS }?.forEach { it.delete() }
    }

    private fun watch(context: Context) {
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
        cleanUp(context, System.currentTimeMillis(), all = true)
        if (current != null) context.applicationContext.contentResolver.unregisterContentObserver(current)
        observer = null
        cached = null
        stale = true
        main.removeCallbacks(notifier)
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

    @JvmStatic fun resetForTest() {
        cached = null
        stale = true
        observer = null
        observerFailed = false
        lastQuery = 0L
        finder = ::newestScreenshot
        main.removeCallbacks(notifier)
        main.removeCallbacks(expiry)
    }
}
