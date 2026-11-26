// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ClipboardThumbnails
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getEnabledClipboardToolbarKeys
import helium314.keyboard.latin.utils.onClickToolbarKey
import helium314.keyboard.latin.utils.onLongClickToolbarKey
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange

@SuppressLint("CustomViewStyleable")
class ClipboardHistoryView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int = R.attr.clipboardHistoryViewStyle
) : LinearLayout(context, attrs, defStyle), View.OnClickListener,
    ClipboardDao.Listener, OnKeyEventListener,
    View.OnLongClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private val clipboardLayoutParams = ClipboardLayoutParams(context)
    private val pinIconId: Int
    private val keyBackgroundId: Int

    private lateinit var clipboardRecyclerView: ClipboardHistoryRecyclerView
    private lateinit var placeholderView: TextView
    private val toolbarKeys = mutableListOf<ImageButton>()
    /** Flat style: header in the strip, section label, no bottom row; see [FlatClipboard]. */
    private val flatStyle = FlatStyle.isActive(Settings.getValues().mColors)
            // the back button lives in the strip; with a hidden strip the bottom row must stay
            && Settings.getValues().isSecondaryStripVisible
    private var flatLabelHeight = 0
    /** List height without the vault rows (stage 44), set in [startClipboardHistory]. */
    private var listHeightWithoutVault = 0
    private var editorInfo: EditorInfo? = null
    private var vaultTextColor = 0
    private var vaultTypeface: android.graphics.Typeface? = null
    private lateinit var clipboardAdapter: ClipboardAdapter
    /** Stage 102: the screenshot shelf, made when it is first shown; [activeShelf] while this visit shows it. */
    private var shelfUi: helium314.keyboard.agent.shelf.ShelfUi? = null
    private var activeShelf: helium314.keyboard.agent.shelf.ShelfUi? = null
    private var withShelf: androidx.recyclerview.widget.ConcatAdapter? = null
    private var listBottomPadding = 0

    lateinit var keyboardActionListener: KeyboardActionListener
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager

    init {
        val clipboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.ClipboardHistoryView, defStyle, R.style.ClipboardHistoryView)
        pinIconId = clipboardViewAttr.getResourceId(R.styleable.ClipboardHistoryView_iconPinnedClip, 0)
        clipboardViewAttr.recycle()
        @SuppressLint("UseKtx") // suggestion does not work
        val keyboardViewAttr = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView)
        keyBackgroundId = keyboardViewAttr.getResourceId(R.styleable.KeyboardView_keyBackground, 0)
        keyboardViewAttr.recycle()
        // stage 106: the row of tools is off by default; the chosen keys stay stored for when it is switched on
        if (helium314.keyboard.agent.AgentPreferences.clipboardToolbarShown(context.prefs()))
            getEnabledClipboardToolbarKeys(context.prefs()).forEach { toolbarKeys.add(createToolbarKey(context, it)) }
        fitsSystemWindows = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        // The main keyboard expands to the entire this {@link KeyboardView}.
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) + paddingLeft + paddingRight
        val height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues()) + paddingTop + paddingBottom + stripRoom()
        setMeasuredDimension(width, height)
    }

    /**
     * Stage 106: without the row of tools (and not in the Flat style, whose header holds the back button) the strip above the history is
     * hidden ([hidesStrip], `KeyboardSwitcher.setClipboardKeyboard`); the history takes its height, so the keyboard keeps its size.
     */
    fun hidesStrip(): Boolean = toolbarKeys.isEmpty() && !flatStyle && Settings.getValues().isSecondaryStripVisible

    private fun stripRoom(): Int = if (hidesStrip()) resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) else 0

    @SuppressLint("ClickableViewAccessibility")
    private fun initialize() { // needs to be delayed for access to ClipboardStrip, which is not a child of this view
        if (this::clipboardAdapter.isInitialized) return
        val colors = Settings.getValues().mColors
        clipboardAdapter = ClipboardAdapter(clipboardLayoutParams, this).apply {
            itemBackgroundId = keyBackgroundId
            pinnedIconResId = pinIconId
        }
        placeholderView = findViewById(R.id.clipboard_empty_view)
        clipboardRecyclerView = findViewById<ClipboardHistoryRecyclerView>(R.id.clipboard_list).apply {
            val colCount = resources.getInteger(R.integer.config_clipboard_keyboard_col_count)
            layoutManager = StaggeredGridLayoutManager(colCount, StaggeredGridLayoutManager.VERTICAL)
            @Suppress("deprecation") // "no cache" should be fine according to warning in https://developer.android.com/reference/android/view/ViewGroup#setPersistentDrawingCache(int)
            persistentDrawingCache = PERSISTENT_NO_CACHE
            clipboardLayoutParams.setListProperties(this)
            placeholderView = this@ClipboardHistoryView.placeholderView
        }
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        if (flatStyle) {
            toolbarKeys.forEach {
                it.setOnClickListener(this@ClipboardHistoryView)
                it.setOnLongClickListener(this@ClipboardHistoryView)
            }
            clipboardAdapter.flatStyle = true
            FlatClipboard.buildHeader(clipboardStrip, toolbarKeys, colors) {
                keyboardActionListener.onCodeInput(KeyCode.CLIPBOARD, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
            flatLabelHeight = FlatClipboard.arrangeList(this, findViewById(R.id.bottom_row_keyboard), colors)
            return
        }
        toolbarKeys.forEach {
            clipboardStrip.addView(it)
            it.setOnClickListener(this@ClipboardHistoryView)
            it.setOnLongClickListener(this@ClipboardHistoryView)
            colors.setColor(it, ColorType.TOOL_BAR_KEY)
            colors.setBackground(it, ColorType.STRIP_BACKGROUND)
        }
    }

    private fun setupClipKey(params: KeyDrawParams) {
        clipboardAdapter.apply {
            itemBackgroundId = keyBackgroundId
            itemTypeFace = params.mTypeface
            itemTextColor = params.mTextColor
            itemTextSize = params.mLabelSize.toFloat()
        }
    }

    private fun setupToolbarKeys() {
        if (flatStyle) return // the header set fixed sizes
        // set layout params
        val toolbarKeyLayoutParams = LayoutParams(resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width), LayoutParams.MATCH_PARENT)
        toolbarKeys.forEach { it.layoutParams = toolbarKeyLayoutParams }
    }

    private fun setupBottomRowKeyboard(editorInfo: EditorInfo, listener: KeyboardActionListener) {
        val keyboardView = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        keyboardView.setKeyboardActionListener(listener)
        PointerTracker.switchTo(keyboardView)
        val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
        val keyboard = kls.getKeyboard(KeyboardElement.CLIPBOARD_BOTTOM_ROW)
        keyboardView.setKeyboard(keyboard)
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        // no layer in the Flat style: a scrolling list inside a hardware layer is rendered twice per frame
        if (!enabled || flatStyle) return
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startClipboardHistory(
            historyManager: ClipboardHistoryManager,
            keyVisualAttr: KeyVisualAttributes?,
            editorInfo: EditorInfo,
            keyboardActionListener: KeyboardActionListener
    ) {
        clipboardHistoryManager = historyManager
        initialize()
        setupToolbarKeys()
        historyManager.prepareClipboardHistory()
        historyManager.setHistoryChangeListener(this)
        clipboardAdapter.clipboardHistoryManager = historyManager
        // stage 102 (review, finding 1): the one ConcatAdapter remembers the clip count of the last visit; the history changed
        // meanwhile without telling it (expired or new clips), so it is told once here
        clipboardAdapter.notifyDataSetChanged()

        val params = KeyDrawParams()
        params.updateParams(clipboardLayoutParams.bottomRowKeyboardHeight, keyVisualAttr)
        val settings = Settings.getInstance()
        KeyboardTypeface.customTypeface()?.let { params.mTypeface = it }
        setupClipKey(params)
        setupBottomRowKeyboard(editorInfo, keyboardActionListener)

        placeholderView.apply {
            KeyboardTypeface.applyToTextView(this)
            setTextColor(params.mTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize.toFloat() * 2)
        }
        // stage 102: the screenshot tiles come first in the same list (opt-in, not when locked, incognito or in a password field)
        val shelf = if (helium314.keyboard.agent.shelf.Shelf.isShown(context, Settings.getValues(), editorInfo)) shelf() else null
        activeShelf = shelf
        clipboardRecyclerView.apply {
            adapter = if (shelf == null) clipboardAdapter else (withShelf ?: androidx.recyclerview.widget.ConcatAdapter(
                androidx.recyclerview.widget.ConcatAdapter.Config.Builder().setIsolateViewTypes(true).build(),
                shelf.mainAdapter, clipboardAdapter).also { withShelf = it }) // one, so no old one keeps listening
            val keyboardWidth = ResourceUtils.getKeyboardWidth(context, settings.current)
            layoutParams.width = keyboardWidth
            // new ClipboardLayoutParams means ClipboardAdapter has wrong gaps, but that's ok (only relevant when resizing floating keyboard)
            ClipboardLayoutParams(context).setListProperties(this)
            layoutParams.height += stripRoom() // stage 106: the room of the hidden strip goes to the list
            if (flatStyle) // the list runs down to the lower edge
                layoutParams.height = ResourceUtils.getSecondaryKeyboardHeight(resources, settings.current) - flatLabelHeight

            // set side padding
            val keyboardAttr = context.obtainStyledAttributes(
                null, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard)
            val leftPadding = (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardLeftPadding,
                keyboardWidth, keyboardWidth, 0f)
                    * settings.current.mSidePaddingScale).toInt()
            val rightPadding =  (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardRightPadding,
                keyboardWidth, keyboardWidth, 0f)
                    * settings.current.mSidePaddingScale).toInt()
            keyboardAttr.recycle()
            if (listBottomPadding == 0) listBottomPadding = paddingBottom + 1 // + 1: remembered even when it is 0
            // stage 102: room at the end, so that the last row can scroll above the drop area (always, so nothing jumps when a drag starts)
            val bottom = (listBottomPadding - 1) + (shelf?.bottomRoom ?: 0)
            clipToPadding = shelf == null
            setPadding(leftPadding, paddingTop, rightPadding, bottom)
        }
        shelf?.start()

        // absurd workaround so Android sets the correct color from stateList (depending on "activated")
        toolbarKeys.forEach { it.isEnabled = false; it.isEnabled = true }

        // stage 44: vault rows above the list; refreshed when items expire while the view is open
        this.editorInfo = editorInfo
        vaultTextColor = params.mTextColor
        vaultTypeface = params.mTypeface
        listHeightWithoutVault = clipboardRecyclerView.layoutParams.height
        helium314.keyboard.agent.ClipVault.onChange = { post { refreshVault() } }
        refreshVault()
        // stage 40: Smart Paste chips on the cards; a code or an IBAN goes in like a vault pick (private), a link like a card
        clipboardAdapter.smartPaste = helium314.keyboard.agent.SmartPasteChips.History(context, { this.editorInfo }) { text, private ->
            onSmartInsert(text, private)
        }
        // stage 102: measuring point, from the clipboard key to the first frame of this view
        helium314.keyboard.agent.UiPerf.reportAtNextDraw(helium314.keyboard.agent.UiPerf.CLIPBOARD_OPEN, this) { perfCounts() }
    }

    /** Stage 102: counts for the measuring lines (never content). */
    private fun perfCounts(): String {
        val entries = clipboardHistoryManager.getHistorySize()
        val images = (0 until entries).count { clipboardHistoryManager.getHistoryEntry(it)?.filename != null }
        return "entries=$entries images=$images"
    }

    /** Stage 40: the tap on an inserting Smart Paste chip of a card, like a tap on a vault chip ([privateText]) or on the card. */
    private fun onSmartInsert(text: String, privateText: Boolean): Boolean {
        keyboardActionListener.onPressKey(KeyCode.NOT_SPECIFIED, 0, 1, HapticEvent.KEY_PRESS)
        if (privateText) keyboardActionListener.onPrivateTextInput(text) // never composed or learned (P178)
        else keyboardActionListener.onTextInput(text) // like onKeyUp of a card (review of stage 40, B2)
        keyboardActionListener.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
        if (Settings.getValues().mAlphaAfterClipHistoryEntry)
            keyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        return true
    }

    /** Shows the vault rows (nothing when locked, incognito or in a password field) and shortens the list by their height. */
    internal fun refreshVault() {
        if (!this::clipboardAdapter.isInitialized || clipboardAdapter.clipboardHistoryManager == null) return
        val blocked = helium314.keyboard.agent.ClipVault.isBlocked(context, Settings.getValues(), editorInfo)
        val chipColor = Settings.getValues().mColors.get(ColorType.KEY_BACKGROUND)
        val height = helium314.keyboard.agent.ClipVaultSection.update(this, blocked, vaultTextColor, chipColor, vaultTypeface) { onVaultPick(it) }
        if (listHeightWithoutVault > 0) clipboardRecyclerView.layoutParams = clipboardRecyclerView.layoutParams.apply {
            this.height = (listHeightWithoutVault - height).coerceAtLeast(listHeightWithoutVault / 3)
        }
    }

    /** A tap on a vault chip: the user's own insertion, like a history entry; a secret is used up and leaves the system clipboard. */
    private fun onVaultPick(item: helium314.keyboard.agent.ClipVault.Item) {
        val vault = helium314.keyboard.agent.ClipVault
        if (vault.isBlocked(context, Settings.getValues(), editorInfo)) { refreshVault(); return }
        val taken = vault.take(item.id) ?: run { refreshVault(); return }
        keyboardActionListener.onPressKey(KeyCode.NOT_SPECIFIED, 0, 1, HapticEvent.KEY_PRESS)
        keyboardActionListener.onPrivateTextInput(taken.insertText) // never composed or learned (review B4)
        keyboardActionListener.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
        if (taken.kind == dev.agentkeyboard.core.clip.ClipKind.SECRET) vault.clearSystemClip(context, taken)
        refreshVault()
        if (Settings.getValues().mAlphaAfterClipHistoryEntry)
            keyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    fun stopClipboardHistory() {
        if (!this::clipboardAdapter.isInitialized) return
        activeShelf?.stop() // stage 102: a drag ends without a change, the folder window closes
        activeShelf = null
        clipboardRecyclerView.adapter = null
        helium314.keyboard.agent.ClipVault.onChange = null
        editorInfo = null
        ClipboardThumbnails.clear() // pictures are not kept beyond the visit of this view
        clipboardHistoryManager.setHistoryChangeListener(null)
        clipboardAdapter.clipboardHistoryManager = null
        clipboardAdapter.smartPaste = null
    }

    override fun onClick(view: View) {
        if (view.tag is ToolbarKey) {
            onClickToolbarKey(view) {
                keyboardActionListener.onCodeInput(it, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (view.tag is ToolbarKey) {
            onLongClickToolbarKey(view) { code, isRepeat ->
                keyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, isRepeat)
            }
            return true
        }
        return false
    }

    override fun onKeyDown(clipId: Long) {
        keyboardActionListener.onPressKey(KeyCode.NOT_SPECIFIED, 0, 1, HapticEvent.KEY_PRESS)
    }

    override fun onKeyUp(clipId: Long) {
        val clipContent = clipboardHistoryManager.getHistoryEntryContent(clipId)
        if (clipContent?.filename != null) keyboardActionListener.onContent(clipContent.getContentInfo(context))
        else keyboardActionListener.onTextInput(clipContent?.text)
        keyboardActionListener.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
        if (Settings.getValues().mAlphaAfterClipHistoryEntry)
            keyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }

    /** Stage 102: the clips come after the screenshot tiles in the list. */
    private fun clipOffset() = activeShelf?.mainAdapter?.itemCount ?: 0

    /**
     * Stage 102: the screenshot shelf: created once, with the colours of the keyboard's design (E6) and the
     * clipboard's pin icon; it inserts like an image clip (a copy in the keyboard's file provider, made off the main thread).
     */
    private fun shelf(): helium314.keyboard.agent.shelf.ShelfUi {
        shelfUi?.let { return it }
        val colors = Settings.getValues().mColors
        val shelfColors = helium314.keyboard.agent.shelf.ShelfColors(colors.get(ColorType.KEY_BACKGROUND), colors.get(ColorType.KEY_TEXT),
            colors.get(ColorType.ACTION_KEY_BACKGROUND), colors.get(ColorType.CLIPBOARD_PIN), vaultTypeface ?: KeyboardTypeface.customTypeface())
        val container = clipboardRecyclerView.parent as android.widget.FrameLayout
        val host = object : helium314.keyboard.agent.shelf.ShelfUi.Host {
            override fun insert(shot: helium314.keyboard.agent.shelf.Shelf.Shot) = insertScreenshot(shot)
            override fun cancelChildren() {
                val now = android.os.SystemClock.uptimeMillis()
                val cancel = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                super@ClipboardHistoryView.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            override fun haptic(event: HapticEvent) {
                helium314.keyboard.latin.AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this@ClipboardHistoryView, event)
            }
        }
        return helium314.keyboard.agent.shelf.ShelfUi(host, container, clipboardRecyclerView, shelfColors, pinIconId) {
            colors.setBackground(it, ColorType.MAIN_BACKGROUND)
        }.also { shelfUi = it }
    }

    /** Stage 102: a tap on a screenshot, like a tap on an image clip ([onKeyDown], [onKeyUp]). */
    private fun insertScreenshot(shot: helium314.keyboard.agent.shelf.Shelf.Shot) {
        keyboardActionListener.onPressKey(KeyCode.NOT_SPECIFIED, 0, 1, HapticEvent.KEY_PRESS)
        val field = editorInfo
        val chipShot = helium314.keyboard.agent.ScreenshotChip.Shot(shot.id, shot.uri, shot.mimeType, shot.addedMillis)
        SCREENSHOT_COPIES.execute {
            val content = helium314.keyboard.agent.ScreenshotChip.copyForEditor(context, chipShot)
            mainHandler.post {
                // only into the field that was shown when tapped
                if (content != null && editorInfo != null && helium314.keyboard.agent.ScreenshotChip.isSameField(field, editorInfo))
                    keyboardActionListener.onContent(content)
                keyboardActionListener.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
                if (Settings.getValues().mAlphaAfterClipHistoryEntry)
                    keyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
        }
    }

    private companion object {
        /** Stage 102 (review, finding 10): one daemon thread for all views (a view is rebuilt with every new design). */
        val SCREENSHOT_COPIES: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "clipboard-screenshot-copy").apply { isDaemon = true }
        }
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Stage 102: every touch goes to the screenshot shelf first; it keeps the touches of a lifted or dragged tile. */
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        val shelf = activeShelf ?: return super.dispatchTouchEvent(event)
        val container = clipboardRecyclerView.parent as View
        if (shelf.onTouch(event, container.left.toFloat(), container.top.toFloat()) == helium314.keyboard.agent.shelf.ShelfUi.Touch.CONSUME) return true
        return super.dispatchTouchEvent(event)
    }

    override fun onClipInserted(position: Int) {
        // stage 102: measuring point, from the media change of a new screenshot to the frame that shows it
        if (clipboardHistoryManager.getHistoryEntry(position)?.filename != null)
            helium314.keyboard.agent.UiPerf.reportAtNextDraw(helium314.keyboard.agent.UiPerf.SHOT_VISIBLE, this) { perfCounts() }
        clipboardAdapter.notifyItemInserted(position)
        clipboardRecyclerView.smoothScrollToPosition(position + clipOffset())
    }

    override fun onClipsRemoved(position: Int, count: Int) {
        clipboardAdapter.notifyItemRangeRemoved(position, count)
    }

    override fun onClipMoved(oldPosition: Int, newPosition: Int) {
        clipboardAdapter.notifyItemMoved(oldPosition, newPosition)
        clipboardAdapter.notifyItemChanged(newPosition)
        if (newPosition < oldPosition) clipboardRecyclerView.smoothScrollToPosition(newPosition + clipOffset())
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(KeyboardSwitcher.getInstance().clipboardStrip, key)

        // The setting can only be changed from a settings screen, but adding it to this listener seems necessary: https://github.com/HeliBorg/HeliBoard/pull/1903#issuecomment-3478424606
        if (::clipboardHistoryManager.isInitialized && key == Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST) {
            // Ensure settings are reloaded first
            Settings.getInstance().onSharedPreferenceChanged(prefs, key)
            clipboardHistoryManager.sortHistoryEntries()
            clipboardAdapter.notifyDataSetChanged()
        }
    }
}
