// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.os.Build
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.clipboard.FlatClipboard
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.isValidNumber
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardSuggestionView: View? = null
    private var clipboardDao: ClipboardDao? = null
    private var tempPrimaryClip = false
    // the screenshot chip (stage 23g) and the screenshot it shows, reused while the strip is rebuilt
    private var screenshotView: View? = null
    private var screenshotId = -1L
    // the colours the chip was made with: a new theme or design makes a new chip
    private var screenshotColors: helium314.keyboard.latin.common.Colors? = null

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        clipboardDao = ClipboardDao.getInstance(latinIME)
        // stage 44: the vault may read the current clip for "fresh" queries, never keeps it with the history off
        helium314.keyboard.agent.ClipVault.currentClip = { currentTextClip() }
        // review of stages 44/45: the stage 24f hashes leave the disk once; secrets stored before the
        // guard leave the history once per app version, sorted off the main thread (second review N8)
        val removed = helium314.keyboard.agent.commands.SensitiveClips.migrateLegacy(latinIME, clipboardDao)
        if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
            startOldHistoryCleanup()
            fetchPrimaryClip()
        }
        // deleted rows may remain in free pages: rewrite the database file once after such a cleanup
        if (removed) clipboardDao?.compactInBackground()
        helium314.keyboard.agent.ScreenshotChip.onChange = { onScreenshotChange() }
        helium314.keyboard.agent.ScreenshotChip.start(latinIME)
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
        helium314.keyboard.agent.ScreenshotChip.stop(latinIME) // observer and posted reports, not only the callback
    }

    /**
     * A new screenshot (or an offered one expired): add it to the history if that is on, and show
     * or remove the chip right away, but only between words; while a word is typed the next word
     * boundary shows it (see [getScreenshotSuggestionView]).
     */
    internal fun onScreenshotChange() {
        // the copy and its checksum are made in the background, the entry on the main thread like
        // fetchPrimaryClip, so the history view is only changed from here
        helium314.keyboard.agent.ScreenshotChip.addToHistory(latinIME, latinIME.mSettings.current, clipboardDao) {
            maySaveFromUri(it, latinIME)
        }
        if (!latinIME.isInputViewShown) return
        val shown = screenshotView?.takeIf { it.parent != null && !it.isGone }
        val shot = helium314.keyboard.agent.ScreenshotChip.current(latinIME, latinIME.currentInputEditorInfo)
        if (shot == null) {
            // expired or deleted: the strip as without the chip, through the normal update
            // (hideSuggestionView resumes suggestions, which brings next-word predictions back)
            if (shown != null) hideSuggestionView(shown)
            return
        }
        if (shown != null && screenshotId == shot.id) return
        if (!screenshotMayReplaceStrip()) return
        latinIME.setNeutralSuggestionStrip() // typed command chips still come first
        // like the command chips: the chip lives in the suggestion area, which the toolbar may cover
        if (screenshotView?.parent != null && latinIME.mSettings.current.mToolbarMode == ToolbarMode.EXPANDABLE)
            latinIME.showSuggestionStrip()
    }

    /**
     * The chip for a fresh screenshot, shown before word suggestions and clipboard content, but
     * only between words: while a word is being typed the strip keeps its suggestions.
     */
    fun getScreenshotSuggestionView(editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        if (parent == null || !screenshotMayReplaceStrip()) return null
        val shot = helium314.keyboard.agent.ScreenshotChip.current(latinIME, editorInfo) ?: return null
        val colors = latinIME.mSettings.current.mColors
        val view = screenshotView?.takeIf { screenshotId == shot.id && !it.isGone && screenshotColors === colors }
            ?: helium314.keyboard.agent.ScreenshotChip.createView(latinIME, parent, shot, onDone = { hideSuggestionView() })
            ?: return null
        (view.parent as? ViewGroup)?.removeView(view)
        screenshotView = view
        screenshotId = shot.id
        screenshotColors = colors
        clipboardSuggestionView = view
        helium314.keyboard.agent.ScreenshotChip.scheduleExpiry(shot)
        return view
    }

    /**
     * Between words: no composition, and the field is empty before the cursor or the character
     * before it is a space or a word separator. Composition alone is not enough: fields without
     * suggestions, a cursor touching a word and languages without spaces do not compose. Inline
     * autofill and the app's own completions are kept; the chip comes only without them.
     */
    private fun screenshotMayReplaceStrip(): Boolean {
        val logic = latinIME.mInputLogic
        if (logic.hasActiveComposition() || latinIME.isShowingInlineAutofill) return false
        val settings = latinIME.mSettings.current
        val words = logic.mSuggestedWords
        if (settings.mInputAttributes.mApplicationSpecifiedCompletionOn && !words.isEmpty
            && words.mInputStyle == helium314.keyboard.latin.SuggestedWords.INPUT_STYLE_APPLICATION_SPECIFIED) return false
        val before = logic.mConnection.codePointBeforeCursor
        return before == Constants.NOT_A_CODE || Character.isWhitespace(before)
            || settings.mSpacingAndPunctuations.isWordSeparator(before)
    }

    override fun onPrimaryClipChanged() {
        applyOldHistoryCleanup()
        // Make sure we read clipboard content only if history settings is set
        if (latinIME.mSettings.current.mClipboardHistoryEnabled) {
            fetchPrimaryClip()
            dontShowCurrentSuggestion = false
        }
    }

    // todo for later
    //  setting whether to store sensitive clip data?
    //  care about other clip items than first?
    private fun fetchPrimaryClip() {
        if (tempPrimaryClip) return // avoid updating history
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0) return
        val clipItem = clipData.getItemAt(0) ?: return
        val description = clipData.description ?: return
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)

        if (description.hasMimeType("text/*")) {
            val content = clipItem.coerceToText(latinIME)
            if (TextUtils.isEmpty(content)) return
            // stage 44: secrets and bare one-time codes stay in the memory-only vault, never in the
            // database; a code in a message stays in the history as it is (the vault has the code too)
            val settings = latinIME.mSettings.current
            val marked = ClipboardManagerCompat.getClipSensitivity(description) == true
                || InputTypeUtils.isAnyPasswordInputType(latinIME.currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)
            val verdict = helium314.keyboard.agent.ClipVault.offer(content.toString(), marked, timeStamp,
                helium314.keyboard.agent.ClipVault.region(settings), helium314.keyboard.agent.ClipVault.mayKeep(latinIME, settings))
            if (verdict.keepsOutOfHistory) return
            clipboardDao?.addClip(timeStamp, false, content.toString())
        } else if (maySaveFromUri(clipItem.uri, latinIME)) {
            clipboardDao?.addClipUri(timeStamp, false, clipItem.uri, description, latinIME)
        }
    }

    /** Text, copy time and sensitive mark of the current clip, for [helium314.keyboard.agent.ClipVault.freshest]. */
    private fun currentTextClip(): Triple<String, Long, Boolean>? {
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") != true) return null
        val text = clipData.getItemAt(0)?.coerceToText(latinIME)?.toString()?.takeIf { it.isNotEmpty() } ?: return null
        return Triple(text, ClipboardManagerCompat.getClipTimestamp(clipData),
            ClipboardManagerCompat.getClipSensitivity(clipData.description) == true)
    }

    fun getPrimaryClipIfText(): String? {
        if (tempPrimaryClip) return null // avoid updating history
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0) return null
        val clipItem = clipData.getItemAt(0) ?: return null
        return if (clipData.description?.hasMimeType("text/*") == true)
            clipItem.coerceToText(latinIME).toString().takeIf { it.isNotEmpty() }
        else null
    }

    // fallback method because in some apps there is no supported mime type and commitContend does nothing,
    // but KeyEvent.KEYCODE_PASTE for pasting from primary clip works fine
    // (actually we do change the primary clip, but (try to) revert immediately)
    fun pasteWithoutChangingClips(content: InputContentInfoCompat) {
        Log.d(TAG, "trying fallback pasting with system clipboard")
        val primaryClip = clipboardManager.primaryClip
        val tempClip = ClipData(content.description, ClipData.Item(content.contentUri))
        tempPrimaryClip = true
        clipboardManager.setPrimaryClip(tempClip)
        latinIME.onEvent(Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_PASTE, 0,
            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        tempPrimaryClip = false
        if (primaryClip == null)
            return
        // we need to wait a little before switching back to the original primary clip
        // a. it can happen that we switch back before the pasting has started, in that case we only past the primary clip
        // b. if we switch while the clip is pasted, it might crash the app (tested with joplin and logseq)
        // todo: replacing the current primary clip is far from ideal, try finding a different way
        GlobalScope.launch {
            delay(500)
            try {
                clipboardManager.setPrimaryClip(primaryClip)
            } catch (e: Exception) {
                Log.i(TAG, "could not go back to old primary clip", e)
                // happens wen the clip was a file
                // try to find it in out clipboard entries
                val clip = clipboardDao?.getAll()?.firstOrNull { it.timeStamp == ClipboardManagerCompat.getClipTimestamp(primaryClip) }
                if (clip?.filename != null)
                    clipboardManager.setPrimaryClip(ClipData(
                        ClipDescription(clip.text, clip.mimeTypes?.toTypedArray()),
                        ClipData.Item(clip.getContentUri(latinIME))
                    ))
                else if (clip != null)
                    clipboardManager.setPrimaryClip(ClipData(
                        ClipDescription("", arrayOf("text/*")),
                        ClipData.Item(clip.text)
                    ))
            }
        }
    }

    fun toggleClipPinned(id: Long) {
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        clipboardDao?.clearNonPinned()
        helium314.keyboard.agent.ClipVault.clear()
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        removeClipboardSuggestion()
    }

    fun canRemove(index: Int) = clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int) {
        if (canRemove(index))
            clipboardDao?.deleteClipAt(index)
    }

    fun sortHistoryEntries() {
        clipboardDao?.sort()
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() {
        clipboardDao?.clearOldClips(true)
        val cleaned = oldHistoryCleaned()
        applyOldHistoryCleanup()
        // the sorting at the start has not finished (or not run): once here, before the history is shown
        if (!cleaned && removeVaultClipsFromHistory()) clipboardDao?.compactInBackground()
        if (clipboardDao != null) markOldHistoryCleaned()
    }

    // second review of stages 44/45 (N8): the ids of old secrets found off the main thread, deleted on it
    private val pendingOldSecrets = java.util.concurrent.atomic.AtomicReference<Set<Long>?>(null)

    private fun oldHistoryCleaned() =
        latinIME.prefs().getInt(PREF_OLD_HISTORY_CLEANED, 0) == helium314.keyboard.latin.BuildConfig.VERSION_CODE

    private fun markOldHistoryCleaned() =
        latinIME.prefs().edit().putInt(PREF_OLD_HISTORY_CLEANED, helium314.keyboard.latin.BuildConfig.VERSION_CODE).apply()

    /** Once per app version: sorts the unpinned history texts in a background thread (only reads a copy). */
    private fun startOldHistoryCleanup() {
        val dao = clipboardDao ?: return
        if (oldHistoryCleaned()) return
        val wasSensitive = helium314.keyboard.agent.commands.SensitiveClips.matcher(latinIME)
        val region = helium314.keyboard.agent.ClipVault.region(latinIME.mSettings.current)
        val entries = dao.getAll().filter { !it.isPinned && it.filename == null && !it.text.isNullOrEmpty() }.map { it.id to it.text!! }
        Thread({
            runCatching {
                pendingOldSecrets.set(entries.filter { (_, text) ->
                    dev.agentkeyboard.core.clip.ClipGuard.classify(text, wasSensitive(text), region).kind == dev.agentkeyboard.core.clip.ClipKind.SECRET
                }.mapTo(HashSet()) { it.first })
            }
        }, "clip-history-cleanup").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** On the main thread, while the history view is not shown: deletes what [startOldHistoryCleanup] found. */
    private fun applyOldHistoryCleanup() {
        if (pendingOldSecrets.get() == null) return
        val dao = clipboardDao ?: return
        if (dao.listener != null) return
        val ids = pendingOldSecrets.getAndSet(null) ?: return
        var removed = false
        for (index in dao.count() - 1 downTo 0) {
            val entry = dao.getAt(index)
            if (entry.id in ids && !entry.isPinned) { dao.deleteClipAt(index); removed = true }
        }
        markOldHistoryCleaned()
        if (removed) dao.compactInBackground()
    }

    /** For tests: the background sorting has a result that is not applied yet. */
    @androidx.annotation.VisibleForTesting
    fun hasPendingOldHistoryCleanup() = pendingOldSecrets.get() != null

    /**
     * Stage 44: secrets stored before the guard existed leave the history (pinned entries stay, shown
     * masked); one-time codes stay, the history keeps the full text the user copied (review B2). Only
     * while the view is not shown, like [ClipboardDao.clearOldClips]. True if an entry was deleted.
     */
    private fun removeVaultClipsFromHistory(): Boolean {
        val dao = clipboardDao ?: return false
        val wasSensitive = helium314.keyboard.agent.commands.SensitiveClips.matcher(latinIME)
        val region = helium314.keyboard.agent.ClipVault.region(latinIME.mSettings.current)
        var removed = false
        for (index in dao.count() - 1 downTo 0) {
            val entry = dao.getAt(index)
            val text = entry.text
            if (entry.isPinned || entry.filename != null || text.isNullOrEmpty()) continue
            if (dev.agentkeyboard.core.clip.ClipGuard.classify(text, wasSensitive(text), region).kind == dev.agentkeyboard.core.clip.ClipKind.SECRET) {
                dao.deleteClipAt(index)
                removed = true
            }
        }
        return removed
    }

    fun getHistorySize() = clipboardDao?.count() ?: 0

    fun getHistoryEntry(position: Int) = clipboardDao?.getAt(position)

    fun getHistoryEntryContent(id: Long) = clipboardDao?.get(id)

    fun setHistoryChangeListener(listener: ClipboardDao.Listener?) {
        clipboardDao?.listener = listener
    }

    private fun isClipSensitive(inputType: Int): Boolean {
        ClipboardManagerCompat.getClipSensitivity(clipboardManager.primaryClip?.description)?.let { return it }
        return InputTypeUtils.isPasswordInputType(inputType)
    }

    fun getClipboardSuggestionView(editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        applyOldHistoryCleanup()
        // maybe no need to create a new view
        // but a cache has to consider a few possible changes, so better don't implement without need
        clipboardSuggestionView = null
        // a fresh screenshot comes first (opt-in, see ScreenshotChip)
        getScreenshotSuggestionView(editorInfo, parent)?.let { return it }

        // get the content, or return null
        if (!latinIME.mSettings.current.mSuggestClipboardContent) return null
        if (dontShowCurrentSuggestion) return null
        if (parent == null) return null
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0) return null
        val clipItem = clipData.getItemAt(0) ?: return null
        val hasText = clipData.description?.hasMimeType("text/*") == true
        val hasImage = clipData.description?.hasMimeType("image/*") == true && clipItem.uri != null
        if (!hasText && !hasImage) return null
        val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)
        if (System.currentTimeMillis() - timeStamp > RECENT_TIME_MILLIS) return null
        val content = clipItem.coerceToText(latinIME)

        // create the view
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.clipboardSuggestionText
        val clipIcon = KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.PASTE.name.lowercase())
        clipIcon?.setBounds(0, 0, textView.lineHeight, textView.lineHeight) // scale the icon to the text
        textView.setCompoundDrawablesRelative(clipIcon, null, null, null)
        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        if (hasText) {
            if (TextUtils.isEmpty(content)) return null
            // stage 44: a secret is never offered as a normal strip suggestion, also not one copied
            // in a password field or sorted as secret by the vault (review B1)
            if (helium314.keyboard.agent.ClipVault.isSecretClip(content.toString(), timeStamp,
                    ClipboardManagerCompat.getClipSensitivity(clipData.description) == true)) return null
            if (InputTypeUtils.isNumberInputType(inputType) && !content.isValidNumber()) return null
            val shortenedContent = content.take(200) // for display only
            KeyboardTypeface.applyToTextView(textView)
            textView.text = (if (isClipSensitive(inputType)) "*".repeat(shortenedContent.length) else shortenedContent)
        }
        // stage 40: the pill alone, or the row of the pill and its Smart Paste chips
        var shownView: View = binding.root
        val onClickListener = View.OnClickListener {
            dontShowCurrentSuggestion = true
            if (hasText) latinIME.onTextInput(content.toString())
            else latinIME.onEvent(Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_PASTE, 0,
                Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            shownView.isGone = true
        }
        textView.setOnClickListener(onClickListener)

        if (hasImage) {
            if (InputTypeUtils.isNumberInputType(inputType)) return null
            val imageView = binding.clipboardSuggestionImage
            imageView.isVisible = true
            try {
                // decoded once per clip at a small size instead of at full size on every strip update
                // before Android 8 the clip has no time of its own, so nothing can be reused safely
                val stamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) timeStamp else null
                val thumbnail = ClipboardThumbnails.fromUri(latinIME.contentResolver, clipItem.uri, stamp,
                    latinIME.resources.displayMetrics.widthPixels / 4)
                if (thumbnail != null) imageView.setImageBitmap(thumbnail)
                else imageView.setImageURI(clipItem.uri)
            } catch (e: Exception) {
                Log.w(TAG, "error setting clipboard image", e) // happens with SecurityException: Permission Denial
                return null
            }
            imageView.setOnClickListener(onClickListener)
        }

        val closeButton = binding.clipboardSuggestionClose
        closeButton.setImageDrawable(KeyboardIconsSet.instance.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.layoutParams.width = textView.lineHeight // scale the icon to the text
        closeButton.layoutParams.height = textView.lineHeight
        closeButton.setOnClickListener { removeClipboardSuggestion() }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        clipIcon?.let { colors.setColor(it, ColorType.CLIPBOARD_SUGGESTION_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
        if (FlatStyle.isActive(colors)) {
            val label = clipData.description?.label?.toString()?.trim().orEmpty()
            FlatClipboard.styleChip(binding.root, textView, binding.clipboardSuggestionImage, closeButton, hasImage,
                if (label.length in 1..FlatClipboard.MAX_CHIP_LABEL && !isClipSensitive(inputType)) label else null
            ) { removeClipboardSuggestion() }
        }

        // stage 40 (Smart Paste): chips for the recognised text after the pill, each acting only on its own tap
        if (hasText) helium314.keyboard.agent.SmartPasteChips.stripRow(helium314.keyboard.agent.SmartPasteChips.imeHost(latinIME, editorInfo), parent,
            binding.root, textView, content.toString(), timeStamp, ClipboardManagerCompat.getClipSensitivity(clipData.description) == true
        ) { removeClipboardSuggestion() }?.let { shownView = it }
        clipboardSuggestionView = shownView
        return clipboardSuggestionView
    }

    private fun removeClipboardSuggestion() {
        dontShowCurrentSuggestion = true
        hideSuggestionView()
    }

    private fun hideSuggestionView(view: View? = clipboardSuggestionView) {
        val csv = view ?: return
        if (csv.parent != null && !csv.isGone) {
            // clipboard view is shown ->
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        csv.isGone = true
    }

    companion object {
        private val TAG = "ClipboardHistoryManager"
        /** Fork (second review N8): the app version whose old history was cleaned of secrets. */
        const val PREF_OLD_HISTORY_CLEANED = "agent_clip_history_cleaned_version"

        // avoid showing the current suggestion because it has been dismissed or pasted
        private var dontShowCurrentSuggestion: Boolean = false

        const val RECENT_TIME_MILLIS = 3 * 60 * 1000L // 3 minutes (for clipboard suggestions)

        private fun maySaveFromUri(uri: Uri?, context: Context): Boolean {
            val maxSize = context.prefs().getInt(Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT, Defaults.PREF_CLIPBOARD_FILES_SIZE_LIMIT)
            val saveUriData = context.prefs().getBoolean(Settings.PREF_CLIPBOARD_USE_FILES, Defaults.PREF_CLIPBOARD_USE_FILES)
            if (uri == null || !saveUriData) return false
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null).use {
                    if (it?.moveToFirst() != true) return false
                    val size = it.getLong(0)
                    return size <= maxSize * 1000000 // maxSize is megabytes
                }
            } catch (e: Exception) {
                Log.w(TAG, "error checking clip size", e) // happens with SecurityException: Permission Denial
                return false
            }
        }
    }
}
