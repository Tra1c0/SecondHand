/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.BarTools
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.agent.ToolGridView
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min
import androidx.core.view.isGone
import helium314.keyboard.latin.utils.onClickToolbarKey
import helium314.keyboard.latin.utils.onLongClickToolbarKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("InflateParams")
class SuggestionStripView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle), View.OnClickListener, OnLongClickListener, OnSharedPreferenceChangeListener {

    /** Construct a [SuggestionStripView] for showing suggestions to be picked by the user. */
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.suggestionStripViewStyle)

    interface Listener {
        fun pickSuggestionManually(word: SuggestedWordInfo?)
        fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean)
        fun removeSuggestion(word: String?)
        fun removeExternalSuggestions()
        fun onSwipeDownOnToolbar()
    }

    private val moreSuggestionsContainer: View
    private val wordViews = ArrayList<TextView>()
    private val debugInfoViews = ArrayList<TextView>()
    private val dividerViews = ArrayList<View>()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.suggestions_strip, this)
        moreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null)

        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        repeat(SuggestedWords.MAX_SUGGESTIONS) {
            val word = TextView(context, null, R.attr.suggestionWordStyle)
            word.contentDescription = resources.getString(R.string.spoken_empty_suggestion)
            word.setOnClickListener(this)
            word.setOnLongClickListener(this)
            colors.setBackground(word, ColorType.STRIP_BACKGROUND)
            wordViews.add(word)
            val divider = inflater.inflate(R.layout.suggestion_divider, null)
            dividerViews.add(divider)
            val info = TextView(context, null, R.attr.suggestionWordStyle)
            info.setTextColor(colors.get(ColorType.KEY_TEXT))
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP)
            debugInfoViews.add(info)
        }

        DEBUG_SUGGESTIONS = context.prefs().getBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, Defaults.PREF_SHOW_SUGGESTION_INFOS)
    }

    // toolbar views, drawables and setup
    private val toolbar: ViewGroup = findViewById(R.id.toolbar)
    private var toolbarContainer: View = findViewById(R.id.toolbar_container)
    private val pinnedKeys: ViewGroup = findViewById(R.id.pinned_keys)
    private val suggestionsStrip: ViewGroup = findViewById(R.id.suggestions_strip)
    private val toolbarExpandKey = findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val incognitoIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.INCOGNITO.name, context)
    private val toolbarArrowIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context)
    private val defaultToolbarBackground: Drawable = toolbarExpandKey.background
    private val enabledToolKeyBackground = GradientDrawable()
    private var direction = 1 // 1 if LTR, -1 if RTL
    /** In the Flat style the pinned keys stay visible beside the toolbar, see [FlatStrip]. */
    private val flatStyle = FlatStyle.isActive(Settings.getValues().mColors)
    /**
     * Fork, stage 85: the mode "toolbar keys and suggestions" shows the fixed bar in every style: grid key at the
     * left, the tools in equal cells, no scrolling and no expand arrow; suggestions and chips replace the tools,
     * see [BarState] and [FixedBar]. The other modes keep their old look.
     */
    private val fixedBar = !isGone && Settings.getValues().mFixedBar
    private val barState = BarState()

    // ---- fork, stage 89: the writing session (BarState.calm/writing), the debounce of the words and the counters of the diagnose ----

    /** The neutral update that is on its way says the field is empty (set by `LatinIME` before it, only while a session is on). */
    private var fieldEmpty = false
    fun setFieldEmpty(empty: Boolean) { fieldEmpty = empty }
    /** The words are outdated: a neutral update arrived while they stand and the time of the debounce has not run out; taps on them are dropped. */
    private var staleWords = false
    private var heldRtl = false
    // a Handler, not View.postDelayed: that one waits for a window (a view without one, as in tests or while hidden, would never run it)
    private val calmHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var releaseScheduled = false
    private val releaseWords = Runnable { releaseScheduled = false; staleWords = false; applyNeutral(heldRtl) }

    /** Whether the session rule applies in this field: the fixed bar, and a field that can show words (not a password, number or phone field, suggestions on). */
    private fun updateCalm() {
        val values = Settings.getValues()
        barState.setCalm(fixedBar && values.mInputAttributes.mShouldShowSuggestions && values.mSuggestionsEnabled
            && !values.mInputAttributes.mApplicationSpecifiedCompletionOn)
    }

    /** The session is on (read by `LatinIME` for "Leiste automatisch anzeigen" after Enter). */
    fun isWritingSession(): Boolean = barState.calmWriting

    /** A character key, a wipe or a delete went to the field (the hook of the listener); starts the session, ends a pinned choice of the tools. */
    fun onTyped() {
        if (!fixedBar) return
        updateCalm()
        barState.onTyped()
        // the words that wait for the next list get their time from the last key, not from the neutral update: at a normal typing pace the
        // bar does not blink empty between two words, only in a real pause
        if (staleWords) {
            calmHandler.removeCallbacks(releaseWords)
            calmHandler.postDelayed(releaseWords, BarState.CALM_DEBOUNCE_MS)
        }
    }

    /** An emptied field ends the writing session (see `LatinIME.setSuggestedWords`). */
    fun endSession() { barState.endSession() }

    private fun cancelHeldWords() {
        staleWords = false
        releaseScheduled = false
        calmHandler.removeCallbacks(releaseWords)
    }

    private fun protectedField() = Settings.getValues().mInputAttributes.mIsPasswordField || Settings.getValues().mIncognitoModeEnabled ||
        helium314.keyboard.agent.LockState.isLocked(context)

    /** What the owner sees now, for the counters of the diagnose. */
    private fun noteShown() {
        val tools = barState.show == BarState.Show.TOOLS || gridOpen()
        val kind = when {
            tools -> helium314.keyboard.agent.BarCalmCounters.Kind.TOOLS
            isExternalSuggestionVisible -> helium314.keyboard.agent.BarCalmCounters.Kind.CHIP
            suggestedWords.isEmpty -> helium314.keyboard.agent.BarCalmCounters.Kind.EMPTY
            else -> helium314.keyboard.agent.BarCalmCounters.Kind.WORDS
        }
        helium314.keyboard.agent.BarCalmCounters.onShown(kind, protectedField())
    }
    private val gridIcon = ContextCompat.getDrawable(context, R.drawable.sym_keyboard_grid_flat)
    private val backIcon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_left)
    private val closeIcon = ContextCompat.getDrawable(context, R.drawable.ic_close)
    private val doneIcon = ContextCompat.getDrawable(context, R.drawable.ic_setup_check)
    /** Stage 86: set while the bar is being edited (see [BarEditor]). */
    private var editor: BarEditor? = null
    /** Stage 86: the tool being dragged is drawn half transparent where it would land. */
    private var ghost: ToolbarKey? = null
    private var renderedBar: List<ToolbarKey>? = null
    private var gridRoot: View? = null
    private var gridCache: helium314.keyboard.agent.ToolGridView? = null
    private val labels = HashMap<ToolbarKey, String>()
    private val unavailableLabels = HashMap<Pair<ToolbarKey, BarTools.Reason>, String>()
    /** Whether the keyboard is a number layout (the store is not offered there); tests replace the probe. */
    internal var numberLayoutProbe: () -> Boolean = {
        runCatching { KeyboardSwitcher.getInstance().keyboard?.mId?.element?.isNumberLayout == true }.getOrDefault(false)
    }

    /** The number-layout state the tools were last dimmed for (stage 79b). */
    private var numberLayoutShown = false
    private var voiceShown: Boolean? = null
    private var leftKeyText = 0
    private var leftKeyCircle = false
    private val undoOfferListener: () -> Unit = { updateUndoOffer() } // called on the main thread by the key handling
    private var leftKeyIconBase = 0
    private var leftKeyIconBehind = 0
    /** What the strip tells the diagnose card ([helium314.keyboard.agent.LastField.onStripView]); tests read it. */
    internal var stripReporter: (newContent: Boolean, wordsOffered: Boolean, external: Boolean, toolbar: Boolean, emojis: Boolean) -> Unit =
        { newContent, words, external, toolbar, emojis -> helium314.keyboard.agent.LastField.onStripView(newContent, words, external, toolbar, emojis) }
    /** Flat style: recent emojis shown instead of tools and words while a symbol page is open (stage 23b). */
    private var emojiRow: android.view.ViewGroup? = null
    private var toolbarBeforeEmojis = false

    /** Shows the recent emojis while a symbol page is open, and the previous strip afterwards. */
    fun showRecentEmojis(show: Boolean) {
        val row = emojiRow ?: return
        if (show == row.isVisible) return
        if (show) {
            toolbarBeforeEmojis = toolbarContainer.isVisible
            FlatStrip.fillEmojiRow(row, FlatStrip.emojisToShow(runCatching { helium314.keyboard.keyboard.emoji.RecentEmojis.get() }.getOrDefault(emptyList())))
            row.isVisible = true
            setToolbarVisibility(toolbarBeforeEmojis)
        } else {
            row.isVisible = false
            setToolbarVisibility(toolbarBeforeEmojis)
        }
    }

    private val toolbarKeyLayoutParams = LinearLayout.LayoutParams(
        resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
        LinearLayout.LayoutParams.MATCH_PARENT
    )

    init {
        val colors = Settings.getValues().mColors

        // expand key
        // weird way of setting size (default is config_suggestions_strip_edge_key_width)
        // but better not change it or people will complain
        val toolbarHeight = min(toolbarExpandKey.layoutParams.height, resources.getDimension(R.dimen.config_suggestions_strip_height).toInt())
        toolbarExpandKey.layoutParams.height = toolbarHeight
        toolbarExpandKey.layoutParams.width = toolbarHeight // we want it square
        colors.setBackground(toolbarExpandKey, ColorType.STRIP_BACKGROUND) // necessary because background is re-used for defaultToolbarBackground
        colors.setColor(toolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY)
        colors.setColor(toolbarExpandKey.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)

        // background indicator for pinned keys
        val color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha (in Java this is more readable 0xFF000000)
        enabledToolKeyBackground.colors = intArrayOf(color, Color.TRANSPARENT)
        enabledToolKeyBackground.gradientType = GradientDrawable.RADIAL_GRADIENT
        enabledToolKeyBackground.gradientRadius = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) / 2.1f

        val mToolbarMode = if (isGone) ToolbarMode.HIDDEN else Settings.getValues().mToolbarMode
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS) {
            setToolbarVisibility(true)
        }

        // toolbar keys setup (no need to hide them any more when locked, because then suggestion strip is gone anyway
        for (key in if (fixedBar) emptyList() else getEnabledToolbarKeys(context.prefs())) {
            val button = createToolbarKey(context, key)
            button.layoutParams = toolbarKeyLayoutParams
            setupKey(button, colors)
            toolbar.addView(button)
        }
        // the fixed bar has no pinned keys: its tools are one list (BarTools)
        for (pinnedKey in if (fixedBar) emptyList() else getPinnedToolbarKeys(context.prefs())) {
            val button = createToolbarKey(context, pinnedKey)
            button.layoutParams = toolbarKeyLayoutParams
            setupKey(button, colors)
            pinnedKeys.addView(button)
            val pinnedKeyInToolbar = toolbar.findViewWithTag<View>(pinnedKey)
            if (pinnedKeyInToolbar != null && Settings.getValues().mQuickPinToolbarKeys)
                pinnedKeyInToolbar.background = enabledToolKeyBackground
        }
        if (!flatStyle && !fixedBar) toolbarContainer.doOnNextLayout { // FlatStrip spreads the keys with spacers instead
            // set min with of the toolbar so the weight of the toolbar keys actually does something
            // todo: results in requestLayout() improperly called by android.widget.LinearLayout during layout: running second layout pass
            toolbar.minimumWidth = toolbarContainer.width
        }
        if (fixedBar) {
            toolbarContainer = FixedBar.install(findViewById(R.id.suggestions_strip_wrapper), toolbarContainer, toolbar)
            pinnedKeys.visibility = GONE
            if (flatStyle) FlatStrip.arrangeLeftKey(this, toolbarExpandKey) else FixedBar.arrangeLeftKey(toolbarExpandKey)
            populateBar(BarTools.read(context.prefs()))
            helium314.keyboard.agent.UndoOffer.onChange = undoOfferListener
        }
        if (flatStyle) {
            if (!fixedBar) FlatStrip.arrange(this, findViewById(R.id.suggestions_strip_wrapper), toolbarExpandKey, toolbarContainer, toolbar, pinnedKeys)
            emojiRow = FlatStrip.createEmojiRow(findViewById(R.id.suggestions_strip_wrapper), toolbarContainer) { emoji ->
                if (::listener.isInitialized) (listener as? helium314.keyboard.latin.LatinIME)?.onTextInput(emoji)
                runCatching { helium314.keyboard.keyboard.emoji.RecentEmojis.add(emoji) }
            }
        }

        updateKeys()
    }

    private lateinit var listener: Listener
    private var suggestedWords = SuggestedWords.getEmptyInstance()
    private var startIndexOfMoreSuggestions = 0
    var isExternalSuggestionVisible = false // Required to disable the more suggestions if other suggestions are visible
        private set // fork (stage 70, Z6): LatinIME reads it
    private val layoutHelper = SuggestionStripLayoutHelper(context, attrs, defStyle, wordViews, dividerViews, debugInfoViews)
    private val moreSuggestionsView = moreSuggestionsContainer.findViewById<MoreSuggestionsView>(R.id.more_suggestions_view).apply {
        val slidingListener = object : SimpleOnGestureListener() {
            override fun onScroll(down: MotionEvent?, me: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
                if (down == null) return false
                val dy = me.y - down.y
                val dx = me.x - down.x

                if (Settings.getValues().mToolbarSwipeDownToHide && dy > 50.dpToPx(resources) && abs(dy) > abs(dx)) {
                    listener.onSwipeDownOnToolbar()
                    return true
                }

                return if (!staleWords && !isExternalSuggestionVisible && toolbarContainer.visibility != VISIBLE && deltaY > 0 && dy < (-10).dpToPx(resources)) showMoreSuggestions()
                else false
            }
        }
        gestureDetector = GestureDetector(context, slidingListener)
    }

    // public stuff

    val isShowingMoreSuggestionPanel get() = moreSuggestionsView.isShowingInParent

    /** A connection back to the input method. */
    fun setListener(newListener: Listener, inputView: View) {
        listener = newListener
        moreSuggestionsView.listener = newListener
        moreSuggestionsView.mainKeyboardView = inputView.findViewById(R.id.keyboard_view)
    }

    fun setRtl(isRtlLanguage: Boolean) {
        val newLayoutDirection: Int
        if (!Settings.getValues().mVarToolbarDirection)
            newLayoutDirection = LAYOUT_DIRECTION_LOCALE
        else {
            newLayoutDirection = if (isRtlLanguage) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            direction = if (isRtlLanguage) -1 else 1
            if (fixedBar) updateLeftKey()
            else toolbarExpandKey.scaleX = (if (toolbarContainer.visibility != VISIBLE) 1f else -1f) * direction
        }
        layoutDirection = newLayoutDirection
        suggestionsStrip.layoutDirection = newLayoutDirection
    }

    fun setToolbarVisibility(toolbarVisible: Boolean) {
        if (fixedBar) {
            if (toolbarVisible) barState.onToolsRequested() else barState.onContentRequested()
            applyBarState()
            reportToLastField(newContent = false)
            return
        }
        val emojis = emojiRow?.isVisible == true // stage 23b: neither tools nor words while emojis are shown
        pinnedKeys.isVisible = !toolbarVisible || flatStyle
        suggestionsStrip.isVisible = !toolbarVisible && !emojis
        toolbarContainer.isVisible = toolbarVisible && !emojis

        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) {
                view.visibility = suggestionsStrip.visibility
            }
        }

        toolbarExpandKey.scaleX = (if (toolbarVisible) -1f else 1f) * direction
        reportToLastField(newContent = false)
    }

    /**
     * Fork, stage 55 (review M3): what the strip shows, for the diagnose card "Letztes Feld"; no words
     * or texts. runCatching: the init block can call setToolbarVisibility before suggestedWords is set.
     */
    private fun reportToLastField(newContent: Boolean) {
        runCatching {
            // the fixed bar shows the tools when there is nothing else: that is an empty strip, not a toolbar covering words;
            // tools over content (chosen by the user or forced, e.g. auto show) are a toolbar, and then the content is covered
            val tools = if (fixedBar) barState.show == BarState.Show.TOOLS else toolbarContainer.isVisible
            stripReporter(newContent,
                !isExternalSuggestionVisible && !suggestedWords.isEmpty && !suggestedWords.isPunctuationSuggestions, // words offered
                isExternalSuggestionVisible && !(fixedBar && tools && barState.hasContent), // external
                tools && (!fixedBar || barState.hasContent), // toolbar
                emojiRow?.isVisible == true) // emojis
        }
    }

    fun setSuggestions(suggestions: SuggestedWords, isRtlLanguage: Boolean) {
        if (fixedBar) {
            updateCalm()
            // stage 89 (critic K1): while the user writes, "nothing to offer" does not empty the bar before the next list is known
            if (suggestions.isEmpty && barState.calmWriting && !fieldEmpty && barState.show == BarState.Show.CONTENT
                && !isExternalSuggestionVisible && !suggestedWords.isEmpty) {
                heldRtl = isRtlLanguage
                staleWords = true
                if (!releaseScheduled) { releaseScheduled = true; calmHandler.postDelayed(releaseWords, BarState.CALM_DEBOUNCE_MS) }
                return
            }
            cancelHeldWords()
        }
        applyNeutralOrWords(suggestions, isRtlLanguage)
    }

    private fun applyNeutral(isRtlLanguage: Boolean) {
        fieldEmpty = false
        applyNeutralOrWords(SuggestedWords.getEmptyInstance(), isRtlLanguage)
    }

    private fun applyNeutralOrWords(suggestions: SuggestedWords, isRtlLanguage: Boolean) {
        clear()
        setRtl(isRtlLanguage)
        suggestedWords = suggestions
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
            context, suggestedWords, suggestionsStrip, this
        )
        isExternalSuggestionVisible = false
        if (fixedBar) {
            // words, and punctuation the user asked for, replace the tools; an empty list gives the tools back
            if (suggestions.isEmpty) barState.onNeutral(fieldEmpty) else barState.onContent()
            fieldEmpty = false
        }
        reportToLastField(newContent = true)
        updateKeys()
        if (fixedBar) noteShown()
    }

    /** [selection]: the view is the chips of a selected text; a user's choice of the tools then holds for that selection (see [BarState]). */
    @JvmOverloads
    fun setExternalSuggestionView(view: View?, addCloseButton: Boolean, selection: Boolean = false) {
        helium314.keyboard.agent.TypingPerf.work(helium314.keyboard.agent.TypingPerf.Work.STRIP_EXTERNAL) // fork, stage 84
        if (fixedBar) { cancelHeldWords(); updateCalm() }
        clear()
        isExternalSuggestionVisible = true
        reportToLastField(newContent = true)

        if (addCloseButton) {
            val wrapper = LinearLayout(context)
            suggestionsStrip.doOnNextLayout {
                wrapper.layoutParams = LinearLayout.LayoutParams(suggestionsStrip.width - 30.dpToPx(resources), LayoutParams.MATCH_PARENT)
            }
            wrapper.addView(view)
            suggestionsStrip.addView(wrapper)

            val closeButton = createToolbarKey(context, ToolbarKey.CLOSE_HISTORY)
            closeButton.layoutParams = toolbarKeyLayoutParams
            setupKey(closeButton, Settings.getValues().mColors)
            closeButton.setOnClickListener {
                listener.removeExternalSuggestions()
            }
            suggestionsStrip.addView(closeButton)
        } else {
            suggestionsStrip.addView(view)
        }

        // The fixed bar: chips and every other external view replace the tools (the left key brings them back).
        if (fixedBar) { barState.onContent(selection); applyBarState(); reportToLastField(newContent = false); noteShown(); return }
        // In the Flat style the left key opens the menu and cannot collapse the toolbar, so an external
        // suggestion (inline autofill, clipboard) always takes its place.
        if (Settings.getValues().mAutoHideToolbar || flatStyle) setToolbarVisibility(false)
    }

    fun setMoreSuggestionsHeight(remainingHeight: Int) {
        layoutHelper.setMoreSuggestionsHeight(remainingHeight)
    }

    fun dismissMoreSuggestionsPanel() {
        moreSuggestionsView.dismissPopupKeysPanel()
    }

    // overrides: necessarily public, but not used from outside

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(pinnedKeys, key)
        setToolbarButtonsActivatedStateOnPrefChange(toolbar, key)
        if (key == Settings.PREF_ALWAYS_INCOGNITO_MODE)
            GlobalScope.launch { delay(10); withContext(Dispatchers.Main) { updateKeys() } }
    }

    override fun onVisibilityChanged(view: View, visibility: Int) {
        super.onVisibilityChanged(view, visibility)
        // workaround for a bug with inline suggestions views that just keep showing up otherwise, https://github.com/HeliBorg/HeliBoard/pull/386
        if (view === this) {
            suggestionsStrip.visibility = visibility
            if (fixedBar && visibility == VISIBLE) applyBarState() // the workaround above must not show words next to the tools
        }
        // the strip goes away for the emoji and clipboard views: an open menu must not stay over them
        if (visibility != VISIBLE && flatStyle && !fixedBar) FlatStrip.closeMenu(this)
        if (visibility != VISIBLE && fixedBar) closeGrid()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (helium314.keyboard.agent.UndoOffer.onChange === undoOfferListener) helium314.keyboard.agent.UndoOffer.onChange = null
        dismissMoreSuggestionsPanel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overridden by showing suggestions later, if applicable.
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        // Don't populate accessibility event with suggested words and voice key.
        return true
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        // Detecting sliding up finger to show MoreSuggestionsView.
        return moreSuggestionsView.shouldInterceptTouchEvent(motionEvent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        moreSuggestionsView.touchEvent(motionEvent)
        return true
    }

    override fun onClick(view: View) {
        val tag = view.tag
        if (tag is ToolbarKey && editor != null) { // stage 86: in the edit mode a tap on a slot takes the tool out of the bar
            afterEdit(editor?.remove(tag))
            return
        }
        if (tag is ToolbarKey) {
            if (fixedBar) FixedBar.hideHint(toolbarContainer as ViewGroup)
            if (fixedBar && FixedBar.reasonOf(view) != null) { showUnavailableHint(view); return } // a tool in the "off" look: say why
            if (!view.isEnabled) return
            if (flatStyle && !fixedBar) FlatStrip.closeMenu(this)
            closeGrid()
            if (fixedBar) barState.onToolTapped() // stage 89: the tools stay until the next typed key
            onClickToolbarKey(view) { listener.onCodeInput(it, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false) }
            return
        }
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
        if (view === toolbarExpandKey) {
            if (fixedBar) onLeftKey()
            else if (flatStyle) // the left key opens the menu; toolbar and suggestions switch on their own
                FlatStrip.toggleMenu(this, toolbarExpandKey, toolbarContainer) { listener.onCodeInput(it, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false) }
            else setToolbarVisibility(toolbarContainer.visibility != VISIBLE)
        }

        // tag for word views is set in SuggestionStripLayoutHelper (setupWordViewsTextAndColor, layoutPunctuationSuggestions)
        if (tag is Int) {
            if (staleWords) { helium314.keyboard.agent.BarCalmCounters.onDroppedTap(protectedField()); return } // stage 89: outdated words insert nothing
            if (tag >= suggestedWords.size()) {
                return
            }
            val wordInfo = suggestedWords.getInfo(tag)
            listener.pickSuggestionManually(wordInfo)
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (fixedBar && view === toolbarExpandKey) {
            if (editor == null && (barState.show == BarState.Show.TOOLS || gridOpen())) enterEdit()
            return true
        }
        // only the slots of the bar customize (the close cross of the inline autofill is also a ToolbarKey button, in the suggestion area)
        if (fixedBar && view.tag is ToolbarKey && view.parent === toolbar) {
            val key = view.tag as ToolbarKey
            // customizing: dragging a slot, or (no own long press action) starting the edit mode from the slot
            if (editor != null) return true // the container of the edit mode takes the touches of the slots (BarDrag)
            if (getCodeForToolbarKeyLongClick(key) == KeyCode.UNSPECIFIED) { enterEdit(); return true }
        }
        if (view.tag is ToolbarKey) {
            if (fixedBar && FixedBar.reasonOf(view) != null) { showUnavailableHint(view); return true }
            if (!view.isEnabled) return true
            if (flatStyle && !fixedBar) FlatStrip.closeMenu(this)
            closeGrid()
            onLongClickToolbarKey(view)
            return true
        }
        if (staleWords) return true // stage 89: outdated words, no long press and no list of more words
        AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
        return if (view is TextView && wordViews.contains(view)) {
            onLongClickSuggestion(view)
        } else {
            showMoreSuggestions()
        }
    }

    // actually private stuff

    private fun onLongClickToolbarKey(view: View) {
        val tag = view.tag as? ToolbarKey ?: return
        if (!Settings.getValues().mQuickPinToolbarKeys || view.parent === pinnedKeys) {
            onLongClickToolbarKey(view) { code, isRepeat -> listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, isRepeat) }
        } else if (view.parent === toolbar) {
            AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
            val pinnedKeyView = pinnedKeys.findViewWithTag<View>(tag)
            if (pinnedKeyView == null) {
                addKeyToPinnedKeys(tag)
                toolbar.findViewWithTag<View>(tag).background = enabledToolKeyBackground
                addPinnedKey(context.prefs(), tag)
            } else {
                removePinnedKey(context.prefs(), tag)
                toolbar.findViewWithTag<View>(tag).background = defaultToolbarBackground.constantState?.newDrawable(resources)
                pinnedKeys.removeView(pinnedKeyView)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we only return false mostly anyway
    private fun onLongClickSuggestion(wordView: TextView): Boolean {
        var showIcon = true
        if (wordView.tag is Int) {
            val index = wordView.tag as Int
            val type = suggestedWords.getInfo(index).mSourceDict
            if (type == Dictionary.DICTIONARY_USER_TYPED || type == Dictionary.DICTIONARY_HARDCODED)
                showIcon = false
        }
        if (showIcon) {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_BIN, context)!!
            Settings.getValues().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON)
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            wordView.ellipsize = TextUtils.TruncateAt.END
            val downOk = AtomicBoolean(false)
            wordView.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP && downOk.get()) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        removeSuggestion(wordView)
                        wordView.cancelLongPress()
                        wordView.isPressed = false
                        return@setOnTouchListener true
                    }
                } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        downOk.set(true)
                    }
                }
                false
            }
        }
        if (DebugFlags.DEBUG_ENABLED && (isShowingMoreSuggestionPanel || !showMoreSuggestions())) {
            showSourceDict(wordView)
            return true
        }
        return showMoreSuggestions()
    }

    private fun showMoreSuggestions(): Boolean {
        if (suggestedWords.size() <= startIndexOfMoreSuggestions) {
            return false
        }
        if (!moreSuggestionsView.show(
                suggestedWords, startIndexOfMoreSuggestions, moreSuggestionsContainer, layoutHelper, this
        ))
            return false
        for (i in 0..<startIndexOfMoreSuggestions) {
            wordViews[i].isPressed = false
        }
        return true
    }

    private fun showSourceDict(wordView: TextView) {
        val word = wordView.text.toString()
        val index = wordView.tag as? Int ?: return
        if (index >= suggestedWords.size()) return
        val info = suggestedWords.getInfo(index)
        if (info.word != word) return

        val text = info.mSourceDict.mDictType + ":" + info.mSourceDict.mLocale
        if (isShowingMoreSuggestionPanel) {
            moreSuggestionsView.dismissPopupKeysPanel()
        }
        KeyboardSwitcher.getInstance().showToast(text, true)
    }

    private fun removeSuggestion(wordView: TextView) {
        val word = wordView.text.toString()
        listener.removeSuggestion(word)
        moreSuggestionsView.dismissPopupKeysPanel()
        // show suggestions, but without the removed word
        val suggestedWordInfos = ArrayList<SuggestedWordInfo>()
        for (i in 0..<suggestedWords.size()) {
            val info = suggestedWords.getInfo(i)
            if (info.word != word) suggestedWordInfos.add(info)
        }
        suggestedWords.mRawSuggestions?.removeFirst { it.word == word }

        val newSuggestedWords = SuggestedWords(
            suggestedWordInfos, suggestedWords.mRawSuggestions, suggestedWords.typedWordInfo, suggestedWords.mTypedWordValid,
            suggestedWords.mWillAutoCorrect, suggestedWords.mIsObsoleteSuggestions, suggestedWords.mInputStyle, suggestedWords.mSequenceNumber
        )
        setSuggestions(newSuggestedWords, direction != 1)
        if (fixedBar) applyBarState() else suggestionsStrip.isVisible = true

        // Show the toolbar if no suggestions are left and the "Auto show toolbar" setting is enabled
        if (this.suggestedWords.isEmpty && Settings.getValues().mAutoShowToolbar) {
            setToolbarVisibility(true)
        }
    }

    private fun clear() {
        suggestionsStrip.removeAllViews()
        if (DEBUG_SUGGESTIONS) removeAllDebugInfoViews()
        if (!toolbarContainer.isVisible && !fixedBar)
            suggestionsStrip.isVisible = true
        dismissMoreSuggestionsPanel()
        for (word in wordViews) {
            word.setOnTouchListener(null)
        }
    }

    private fun removeAllDebugInfoViews() {
        for (debugInfoView in debugInfoViews) {
            val parent = debugInfoView.parent
            if (parent is ViewGroup) {
                parent.removeView(debugInfoView)
            }
        }
    }

    fun updateVoiceKey() {
        val show = Settings.getValues().mShowsVoiceInputKey
        toolbar.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = show
        pinnedKeys.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = show
        // a hidden voice key leaves its cell to the others and the last microphone key may become the circle
        if (fixedBar && show != voiceShown) { voiceShown = show; arrangeBar() }
    }


    /** The geometry of the slots; in the edit mode they keep their outlines (every update of the strip comes through here). */
    private fun arrangeBar() {
        BarCounters.arranges++
        val colors = Settings.getValues().mColors
        FixedBar.arrange(toolbar, toolbarExpandKey, flatStyle, colors)
        if (editor != null) FixedBar.markEditing(toolbar, flatStyle, colors, ghost)
    }

    private fun updateKeys() {
        updateVoiceKey()
        val settingsValues = Settings.getValues()

        val toolbarIsExpandable = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
        if (fixedBar) {
            // the left key always shows; its icon and function follow the state of the bar
            toolbarExpandKey.isVisible = true
            toolbarExpandKey.setOnClickListener(this)
            toolbarExpandKey.setOnLongClickListener(this)
            pinnedKeys.visibility = GONE
            isExternalSuggestionVisible = false
            applyBarState()
            helium314.keyboard.agent.RgbLighting.onStripChanged(this)
            return
        }
        if (settingsValues.mIncognitoModeEnabled) {
            toolbarExpandKey.setImageDrawable(incognitoIcon)
            toolbarExpandKey.isVisible = true
        } else {
            toolbarExpandKey.setImageDrawable(toolbarArrowIcon)
            toolbarExpandKey.isVisible = toolbarIsExpandable
        }
        if (flatStyle) FlatStrip.keepMenuIcon(this, toolbarExpandKey)

        toolbarExpandKey.setOnClickListener(if (!toolbarIsExpandable) null else this)
        pinnedKeys.visibility = if (flatStyle) VISIBLE else suggestionsStrip.visibility
        isExternalSuggestionVisible = false
        helium314.keyboard.agent.RgbLighting.onStripChanged(this) // fork: RGB lighting of words and buttons
    }

    // ---- fork, stage 85: the fixed bar (BarState, FixedBar) ----

    /** The keyboard goes away: the next time the bar starts with the tools (or, in the other modes, with the suggestions). */
    fun onKeyboardHidden() {
        if (!fixedBar) { setToolbarVisibility(false); return }
        exitEdit(andClose = false) // what was dropped is saved; a drag in progress saves nothing
        helium314.keyboard.agent.UndoOffer.reset()
        closeGrid()
        cancelHeldWords()
        barState.reset()
        applyBarState()
    }


    /** The grid of this window; looked up once per root view, not on every update of the strip. */
    private fun toolGrid(): helium314.keyboard.agent.ToolGridView? {
        val root = rootView
        if (root !== gridRoot) {
            gridRoot = root
            gridCache = root.findViewById(R.id.agent_tool_grid)
            BarCounters.gridLookups++
        }
        return gridCache
    }

    private fun gridOpen() = toolGrid()?.isOpen == true

    private fun emojisShown() = emojiRow?.isVisible == true


    private fun labelOf(key: ToolbarKey) = labels.getOrPut(key) {
        BarCounters.labelLookups++
        key.name.lowercase(java.util.Locale.ROOT).getStringResourceOrName("", context)
    }

    private fun unavailableLabelOf(key: ToolbarKey, reason: BarTools.Reason) =
        unavailableLabels.getOrPut(key to reason) { context.getString(R.string.agent_bar_tool_unavailable_why, labelOf(key), reasonText(reason)) }

    /** The user's own switch "Always incognito" is on (it makes every field incognito). */
    private fun alwaysIncognito() = context.prefs().getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)

    /** The real reason a tool does nothing in this field (the check that blocks it), as a short sentence. */
    private fun reasonText(reason: BarTools.Reason) = context.getString(when (reason) {
        BarTools.Reason.PRIVATE_FIELD -> R.string.agent_bar_hint_private
        BarTools.Reason.INCOGNITO_SWITCH -> R.string.agent_bar_hint_incognito
        BarTools.Reason.LOCKED_DEVICE -> R.string.agent_bar_hint_locked
        BarTools.Reason.NUMBER_LAYOUT -> R.string.agent_bar_hint_number
    })

    /** A tap on a tool in the "off" look: not a dead tap, the strip says why (stage 88). */
    private fun showUnavailableHint(view: View) {
        val reason = FixedBar.reasonOf(view) ?: return
        FixedBar.showHint(toolbarContainer as ViewGroup, reasonText(reason), Settings.getValues().mColors)
    }


    /** A new field starts without the keyboard having been hidden: no grid and no edit mode over it (nothing pending is saved). */
    fun onFieldStarted() {
        if (!fixedBar) return
        exitEdit(andClose = false)
        helium314.keyboard.agent.UndoOffer.reset()
        closeGrid()
        // stage 79b: what the last field left in the bar state (a sticky choice of the tools for a selection, content that is
        // gone) must not hold the chips of the new field back; its own content comes with its first strip update
        cancelHeldWords()
        barState.reset()
        applyBarState()
    }

    /**
     * Stage 79b: the main keyboard changed its layout (the tool bar and the keyboard switcher call this after a switch, also
     * to a number layout): the tools that a number layout does not offer (the store) are dimmed or enabled again at once,
     * without waiting for the next update of the strip.
     */
    fun onKeyboardLayoutChanged() {
        if (!fixedBar) return
        if (numberLayoutProbe() != numberLayoutShown) updateToolAvailability()
    }

    /** One consistent set of visibilities for the state of the bar; the only place that decides it in the fixed bar. */
    private fun applyBarState() {
        if (!fixedBar) return
        val emojis = emojiRow?.isVisible == true // stage 23b: neither tools nor words while emojis are shown
        // an open grid needs the tools in view: the edit mode of stage 86 works on their slots
        val tools = barState.show == BarState.Show.TOOLS || gridOpen()
        toolbarContainer.isVisible = tools && !emojis
        suggestionsStrip.isVisible = !tools && !emojis
        pinnedKeys.visibility = GONE
        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) view.visibility = suggestionsStrip.visibility
        }
        updateToolAvailability()
        updateLeftKey()
        noteShown()
    }

    /** Tools that the field does not allow stay in their cell, dimmed; their own controllers still check again on a tap. */
    private fun updateToolAvailability() {
        val values = Settings.getValues()
        val number = numberLayoutProbe()
        numberLayoutShown = number
        toolbar.forEachChild { button ->
            val key = button.tag as? ToolbarKey ?: return@forEachChild
            val reason = BarTools.unavailableReason(key, values.mIncognitoModeEnabled, values.mIsLocked, number, alwaysIncognito())
            FixedBar.setAvailable(button, reason, editor != null, labelOf(key)) { reason?.let { unavailableLabelOf(key, it) } }
            if (key == ToolbarKey.UNDO) applyUndoOffer(button)
        }
    }

    /**
     * Stage 88 ("Kontextwerkzeuge"): after an undo the undo tool carries a small redo arrow (and says so to a screen reader) until
     * the text changes, the field changes or the keyboard hides ([helium314.keyboard.agent.UndoOffer]). Redo itself is the long press
     * of the undo tool (as before) or, for a screen reader, a custom action. Nothing is offered when REDO is a tool of the bar itself
     * or while the bar is edited. The slot keeps its size and place.
     */
    private fun applyUndoOffer(button: View) {
        val offer = editor == null && helium314.keyboard.agent.UndoOffer.active && ToolbarKey.REDO !in (renderedBar ?: emptyList())
        FixedBar.setRedoOffered(button, offer)
        if (offer && FixedBar.reasonOf(button) == null)
            button.contentDescription = context.getString(R.string.agent_bar_undo_offers_redo, labelOf(ToolbarKey.UNDO))
    }

    private fun updateUndoOffer() {
        if (!fixedBar) return
        toolbar.forEachChild { if (it.tag == ToolbarKey.UNDO) { FixedBar.setAvailable(it, BarTools.unavailableReason(ToolbarKey.UNDO, false, false), editor != null, labelOf(ToolbarKey.UNDO)) { null }; applyUndoOffer(it) } }
    }

    private inline fun ViewGroup.forEachChild(action: (View) -> Unit) {
        for (i in 0 until childCount) action(getChildAt(i))
    }

    /** The left key: grid while the tools show, a back arrow while content shows, a cross while the grid is open. */
    private fun updateLeftKey() {
        val incognito = Settings.getValues().mIncognitoModeEnabled
        val grid = gridOpen()
        // the recent emojis (flat) cover tools and content alike: then the key can only open the grid
        val function = if (emojisShown()) BarState.LeftKey.GRID else barState.leftKey
        val toGrid = function == BarState.LeftKey.GRID
        val toContent = function == BarState.LeftKey.SHOW_CONTENT
        val icon: Drawable?
        val text: Int
        when {
            editor != null -> { icon = doneIcon; text = R.string.agent_bar_edit_done }
            grid -> { icon = closeIcon; text = R.string.agent_bar_close_tools }
            toContent -> { icon = if (incognito) incognitoIcon else backIcon
                text = if (incognito) R.string.agent_bar_show_actions_incognito else R.string.agent_bar_show_actions }
            toGrid -> { icon = if (incognito) incognitoIcon else gridIcon
                text = if (incognito) R.string.agent_bar_all_tools_incognito else R.string.agent_bar_all_tools }
            else -> { icon = if (incognito) incognitoIcon else backIcon
                text = if (incognito) R.string.agent_bar_show_tools_incognito else R.string.agent_bar_show_tools }
        }
        if (toolbarExpandKey.drawable !== icon) toolbarExpandKey.setImageDrawable(icon)
        // the back arrow points to the start of the strip, the other icons are symmetric
        // (the arrow of "show the chips" points the other way)
        toolbarExpandKey.scaleX = if (!grid && !toGrid && !toContent && !incognito && editor == null) -direction.toFloat() else direction.toFloat()
        if (text != leftKeyText) { // not on every update of the strip: the same text and background as before need no work
            leftKeyText = text
            toolbarExpandKey.contentDescription = context.getString(text)
        }
        val circle = grid || editor != null
        // stage 88: the icon of the left key has the guaranteed contrast against what is behind it (its disc, or the strip)
        val colors = Settings.getValues().mColors
        val behind = StripIconColors.leftKeySurface(colors, flatStyle, circle)
        val base = colors.get(ColorType.TOOL_BAR_EXPAND_KEY)
        if (base != leftKeyIconBase || behind.argb != leftKeyIconBehind || toolbarExpandKey.colorFilter == null) {
            leftKeyIconBase = base
            leftKeyIconBehind = behind.argb
            toolbarExpandKey.colorFilter = android.graphics.PorterDuffColorFilter(StripIconColors.ensureContrast(base, behind), android.graphics.PorterDuff.Mode.SRC_IN)
        }
        if (flatStyle && circle != leftKeyCircle) {
            leftKeyCircle = circle
            toolbarExpandKey.background = if (!circle) null else GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Settings.getValues().mColors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
            }
        }
    }

    private fun onLeftKey() {
        if (editor != null) { exitEdit(andClose = true); return } // the check button: the places are saved already
        val grid = toolGrid()
        if (grid?.isOpen == true) { grid.close(); return }
        if (emojisShown()) { openGrid(); return }
        when (barState.onLeftKey()) {
            BarState.LeftKey.GRID -> openGrid()
            BarState.LeftKey.SHOW_TOOLS -> { applyBarState(); reportToLastField(newContent = false) } // the tools now cover the content
            // uncovering is a new report for the diagnose card (it never takes a "covered" back without new content)
            BarState.LeftKey.SHOW_CONTENT -> { applyBarState(); reportToLastField(newContent = true) }
        }
    }

    /** All tools in the order of the grid; the voice key only with a voice input, the data gathering key only once it was ever used. */
    private fun gridTools(): List<helium314.keyboard.agent.ToolGridView.Tool> {
        val values = Settings.getValues()
        // a tool that destroys something with one tap (clear the clipboard) is only offered to a user who had it in one of the lists before
        val prefs = context.prefs()
        val usedBefore = BarTools.read(prefs) + getEnabledToolbarKeys(prefs) + getPinnedToolbarKeys(prefs)
        return BarTools.gridOrder.filter { key ->
            (key != ToolbarKey.VOICE || values.mShowsVoiceInputKey)
                && (key != ToolbarKey.BACKGROUND_GATHERING || helium314.keyboard.latin.utils.GestureDataGatheringSettings.hasBackgroundGatheringPref(context.prefs()))
                && (key !in BarTools.DESTRUCTIVE || key in usedBefore)
        }.map {
            val reason = BarTools.unavailableReason(it, values.mIncognitoModeEnabled, values.mIsLocked, numberLayoutProbe(), alwaysIncognito())
            helium314.keyboard.agent.ToolGridView.Tool(it, reason == null, reason)
        }
    }

    private fun openGrid() {
        val grid = toolGrid() ?: return
        val keys = rootView.findViewById<View>(R.id.keyboard_view_wrapper)
        dismissMoreSuggestionsPanel()
        grid.onTool = { key -> runTool(key) }
        grid.onUnavailable = { _, reason -> reason?.let { FixedBar.showHint(toolbarContainer as ViewGroup, reasonText(it), Settings.getValues().mColors) } }
        grid.onEditRequested = { enterEdit() }
        grid.onClosed = {
            keys?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            if (editor != null) exitEdit(andClose = false) else applyBarState()
        }
        // the keys under the grid must not be reachable for screen readers while it is open
        keys?.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        grid.open(rootView.findViewById<View>(R.id.strip_container)?.height ?: height, gridTools())
        applyBarState()
    }

    // ---- stage 86, rebuilt in stage 88: customizing the bar in the keyboard (see BarDrag and docs/LEISTE-KONZEPT.md) ----

    /** What the bar shows: the tools as the edit mode has them, else as saved. Builds the slots again when they changed. */
    private fun populateBar(keys: List<ToolbarKey>) {
        val colors = Settings.getValues().mColors
        val before = if (editor != null && isAttachedToWindow) slotLefts() else null
        FixedBar.populate(toolbar, keys.map { key ->
            createToolbarKey(context, key, FixedBar.newButton(context)).also { it.layoutParams = toolbarKeyLayoutParams; setupKey(it, colors) }
        })
        renderedBar = keys
        BarCounters.barBuilds++
        voiceShown = null // the new buttons have not been arranged
        updateVoiceKey() // hides the voice key without a voice input and arranges the slots
        val ed = editor
        if (ed != null) {
            toolbar.forEachChild { button ->
                val key = button.tag as? ToolbarKey ?: return@forEachChild
                androidx.core.view.ViewCompat.addAccessibilityAction(button, context.getString(R.string.agent_bar_action_remove)) { _, _ -> afterEdit(ed.remove(key)); true }
                if (ed.canShift(key, -1)) androidx.core.view.ViewCompat.addAccessibilityAction(button, context.getString(R.string.agent_bar_action_left)) { _, _ -> afterEdit(ed.shift(key, -1)); true }
                if (ed.canShift(key, 1)) androidx.core.view.ViewCompat.addAccessibilityAction(button, context.getString(R.string.agent_bar_action_right)) { _, _ -> afterEdit(ed.shift(key, 1)); true }
            }
        }
        if (ed == null && ToolbarKey.REDO !in keys) toolbar.forEachChild { button ->
            if (button.tag == ToolbarKey.UNDO) androidx.core.view.ViewCompat.addAccessibilityAction(button, context.getString(R.string.agent_bar_action_redo)) { _, _ ->
                listener.onCodeInput(KeyCode.REDO, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false); true }
        }
        updateToolAvailability()
        helium314.keyboard.agent.RgbLighting.onStripChanged(this) // the buttons are new: the light finds them again
        if (before != null) glideSlots(before)
    }

    /** The left edge of every slot by tool, to let the slots glide from there to their new places (the others make room for the gap in at most 150 ms). */
    private fun slotLefts(): Map<ToolbarKey, Float> = FixedBar.slots(toolbar).mapNotNull { b -> (b.tag as? ToolbarKey)?.let { it to b.left + b.translationX } }.toMap()

    private fun glideSlots(before: Map<ToolbarKey, Float>) {
        toolbar.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                toolbar.viewTreeObserver.removeOnPreDrawListener(this)
                FixedBar.slots(toolbar).forEach { b ->
                    val old = before[b.tag as? ToolbarKey] ?: return@forEach
                    val delta = old - b.left
                    if (Math.abs(delta) < 1f) return@forEach
                    b.translationX = delta
                    b.animate().translationX(0f).setDuration(SLOT_GLIDE_MS).start()
                }
                return true
            }
        })
    }

    /** A short shake of the slots: the bar is full. */
    private fun shakeSlots() {
        if (!isAttachedToWindow) return
        val d = 6f * resources.displayMetrics.density
        FixedBar.slots(toolbar).forEach { b ->
            b.animate().cancel()
            b.translationX = 0f
            b.animate().translationX(d).setDuration(50).withEndAction { b.animate().translationX(-d).setDuration(80).withEndAction { b.animate().translationX(0f).setDuration(50).start() }.start() }.start()
        }
    }

    /** The edit mode: the grid shows the hint, the bar its outlined slots, the left key becomes the check button. */
    private fun enterEdit() {
        if (!fixedBar || editor != null) return
        if (!gridOpen()) openGrid()
        val grid = toolGrid() ?: return
        val ed = BarEditor({ BarTools.read(context.prefs()) }, { BarTools.write(context.prefs(), it) }, ::hasNoSlot)
        editor = ed
        grid.setEditing(gridEditListener)
        grid.dragLogic = BarDrag(ed, barGeometry, BarDrag.Config(slopPx = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()))
        grid.slotAt = ::slotKeyAt
        grid.slotCentre = ::slotCentreOf
        grid.onDragEvents = ::onDragEvents
        grid.setMarked(ed.shown.toSet())
        populateBar(ed.shown)
        applyBarState()
    }

    private fun exitEdit(andClose: Boolean) {
        val ed = editor
        if (ed != null) {
            ed.dragEnd()
            editor = null
            ghost = null
            toolGrid()?.setEditing(null)
            populateBar(BarTools.read(context.prefs()))
            applyBarState()
        }
        if (andClose) closeGrid()
    }

    /** The bar and the grid as the editor has them now; the line of the grid says when the bar was full. */
    private fun afterEdit(result: BarEditor.Result?) {
        val ed = editor ?: return
        toolGrid()?.setHint(if (result == BarEditor.Result.FULL) context.getString(R.string.agent_grid_full) else null)
        refreshEdit(ed)
    }

    private fun refreshEdit(ed: BarEditor) {
        ghost = ed.dragged?.takeIf { it in ed.shown }
        if (renderedBar != ed.shown) populateBar(ed.shown)
        else updateToolAvailability()
        if (editor != null) FixedBar.markEditing(toolbar, flatStyle, Settings.getValues().mColors, ghost)
        toolGrid()?.setMarked(ed.shown.toSet())
        updateLeftKey()
    }

    private val gridEditListener = object : ToolGridView.EditListener {
        override fun onTap(key: ToolbarKey) { announce(editor?.toggle(key), key) ; editor?.let { afterEdit(null) } }
        override fun canUndo() = editor?.canUndo == true
        override fun onUndo() { editor?.undo(); editor?.let { afterEdit(null) } }
        override fun onReset() { editor?.reset(); editor?.let { afterEdit(null) } }
        override fun actions(key: ToolbarKey): List<Pair<Int, () -> Boolean>> {
            val inBar = editor?.bar?.contains(key) == true
            return listOf((if (inBar) R.string.agent_bar_action_remove else R.string.agent_bar_action_add) to { afterEdit(editor?.toggle(key)); true })
        }
    }

    /** Tells a screen reader what a change did (the tap alternative and the drops). */
    private fun announce(result: BarEditor.Result?, key: ToolbarKey) {
        if (result != BarEditor.Result.CHANGED) return
        val ed = editor ?: return
        val message = if (key in ed.bar) context.getString(R.string.agent_bar_announce_added, labelOf(key))
            else context.getString(R.string.agent_bar_announce_removed, labelOf(key))
        announceForAccessibility(message)
    }

    // geometry of the drag, in the coordinates of the grid (the container of the edit mode) ----

    /** The offset of [view] from the grid: the bar and the grid are both in the frame, so the sums of the offsets up to the frame are subtracted. */
    private fun originIn(view: View, container: View): PointF {
        fun chain(v: View): PointF {
            var x = 0f; var y = 0f
            var current: View? = v
            while (current != null && current !== container.parent) {
                x += current.left + current.translationX - ((current.parent as? View)?.scrollX ?: 0)
                y += current.top + current.translationY - ((current.parent as? View)?.scrollY ?: 0)
                current = current.parent as? View
            }
            return PointF(x, y)
        }
        val a = chain(view)
        val b = chain(container)
        return PointF(a.x - b.x, a.y - b.y)
    }

    private val barGeometry = object : BarDrag.Geometry {
        override fun inBarZone(x: Float, y: Float): Boolean {
            val grid = toolGrid() ?: return false
            val stripBottom = originIn(toolbarContainer, grid).y + toolbarContainer.height
            return y <= stripBottom + BAR_ZONE_BELOW_DP * resources.displayMetrics.density
        }

        override fun slotAt(x: Float, count: Int): Int {
            val grid = toolGrid() ?: return 0
            val left = originIn(toolbar, grid).x
            // right to left: the first tool is at the right, the places are counted in the order of the list
            val rel = if (toolbar.layoutDirection == LAYOUT_DIRECTION_RTL) toolbar.width - (x - left) else x - left
            return FixedBar.slotIndexAt(toolbar, flatStyle, rel, count)
        }
    }

    /** The tool of the slot whose cell holds the point (grid coordinates). The whole height of the strip counts. */
    private fun slotKeyAt(x: Float, y: Float): ToolbarKey? {
        val grid = toolGrid() ?: return null
        if (!toolbarContainer.isVisible) return null
        val origin = originIn(toolbar, grid)
        val top = originIn(toolbarContainer, grid).y
        if (y < top - 4 * resources.displayMetrics.density || y > top + toolbarContainer.height) return null
        for (b in FixedBar.slots(toolbar)) {
            val left = origin.x + b.left + b.translationX
            if (x >= left && x < left + b.width) return b.tag as? ToolbarKey
        }
        return null
    }

    /** Where the slot of [key] is after a drop (grid coordinates), from the count alone so that no layout pass is needed. */
    private fun slotCentreOf(key: ToolbarKey): PointF? {
        val grid = toolGrid() ?: return null
        val ed = editor ?: return null
        val shown = ed.bar.filterNot(::hasNoSlot)
        val index = shown.indexOf(key)
        if (index < 0) return null
        val centres = FixedBar.slotCentres(toolbar, flatStyle, shown.size)
        val origin = originIn(toolbar, grid)
        val c = if (toolbar.layoutDirection == LAYOUT_DIRECTION_RTL) toolbar.width - centres[index] else centres[index]
        return PointF(origin.x + c, originIn(toolbarContainer, grid).y + toolbarContainer.height / 2f)
    }

    /** What the touch logic found: the model has been updated by it; this updates the bar, the haptics, the hints and the announcements. */
    private fun onDragEvents(events: List<BarDrag.Event>) {
        val ed = editor ?: return
        val haptics = AudioAndHapticFeedbackManager.getInstance()
        for (e in events) when (e) {
            is BarDrag.Event.PickedUp -> { haptics.performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS); refreshEdit(ed) }
            is BarDrag.Event.GapMoved -> { haptics.performHapticFeedback(this, HapticEvent.KEY_PRESS); toolGrid()?.setHint(null); refreshEdit(ed) }
            is BarDrag.Event.Full -> {
                haptics.performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
                toolGrid()?.setHint(context.getString(R.string.agent_bar_dropped_full))
                shakeSlots()
                announceForAccessibility(context.getString(R.string.agent_bar_dropped_full))
                refreshEdit(ed)
            }
            is BarDrag.Event.Dropped -> {
                haptics.performHapticFeedback(this, HapticEvent.KEY_PRESS)
                toolGrid()?.setHint(if (e.result == BarEditor.Result.FULL) context.getString(R.string.agent_bar_dropped_full) else null)
                refreshEdit(ed)
                if (e.result == BarEditor.Result.CHANGED) announce(e.result, e.key)
            }
            is BarDrag.Event.Cancelled -> { toolGrid()?.setHint(null); refreshEdit(ed) }
            is BarDrag.Event.Tapped -> { announce(if (e.onPill) ed.toggle(e.key) else ed.remove(e.key), e.key); afterEdit(null) }
            is BarDrag.Event.Scroll -> Unit
        }
    }

    /** A tool of the list that has no slot: the voice key where there is no voice input. */
    private fun hasNoSlot(key: ToolbarKey) = key == ToolbarKey.VOICE && !Settings.getValues().mShowsVoiceInputKey

    private fun closeGrid() {
        if (!fixedBar) return
        toolGrid()?.close()
    }

    /** Runs a tool of the grid exactly like its key in the bar: the same code through the same listener. */
    private fun runTool(key: ToolbarKey) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
        val code = getCodeForToolbarKey(key)
        if (code != KeyCode.UNSPECIFIED)
            listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
    }

    private fun addKeyToPinnedKeys(pinnedKey: ToolbarKey) {
        val original = toolbar.findViewWithTag<ImageButton>(pinnedKey) ?: return
        // copy the original key to a new ImageButton
        val copy = ImageButton(context, null, R.attr.suggestionWordStyle)
        copy.tag = pinnedKey
        copy.scaleType = original.scaleType
        copy.scaleX = original.scaleX
        copy.scaleY = original.scaleY
        copy.contentDescription = original.contentDescription
        copy.setImageDrawable(original.drawable)
        copy.layoutParams = original.layoutParams
        copy.isActivated = original.isActivated
        setupKey(copy, Settings.getValues().mColors)
        pinnedKeys.addView(copy)
        if (flatStyle) FlatStrip.sizePinnedCopy(original, copy)
    }

    private fun setupKey(view: ImageButton, colors: Colors) {
        view.setOnClickListener(this)
        view.setOnLongClickListener(this)
        (view.layoutParams as LinearLayout.LayoutParams).weight = 1f
        colors.setColor(view, ColorType.TOOL_BAR_KEY)
        colors.setBackground(view, ColorType.STRIP_BACKGROUND)
    }

    companion object {
        @JvmField
        var DEBUG_SUGGESTIONS = false
        private const val DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f
        private const val BAR_ZONE_BELOW_DP = 40f
        private const val SLOT_GLIDE_MS = 140L
        private val TAG = SuggestionStripView::class.java.simpleName
    }
}
