// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import dev.agentkeyboard.core.CursorTrack
import helium314.keyboard.agent.CursorTrackCounters
import helium314.keyboard.agent.CursorTrackpad
import helium314.keyboard.agent.TouchCalibration
import android.text.InputType
import helium314.keyboard.agent.HapticChain
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SpaceAction
import helium314.keyboard.agent.SpaceDetents
import helium314.keyboard.agent.SpaceGesture
import helium314.keyboard.agent.SymbolInk
import android.os.SystemClock
import android.util.SparseArray
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodSubtype
import androidx.core.util.forEach
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.event.Event
import helium314.keyboard.event.HangulEventDecoder
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.event.HardwareEventDecoder
import helium314.keyboard.event.HardwareKeyboardEventDecoder
import helium314.keyboard.keyboard.internal.LayoutDirective
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.EmojiAltPhysicalKeyDetector
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.combiningRange
import helium314.keyboard.latin.common.moveStepsToCharCount
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.BackgroundGatheringCache
import helium314.keyboard.latin.utils.GestureDataGatheringSettings
import helium314.keyboard.latin.utils.RecapitalizeMode
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import kotlin.math.abs

class KeyboardActionListenerImpl(private val latinIME: LatinIME, private val inputLogic: InputLogic) : KeyboardActionListener {

    private val connection = inputLogic.mConnection
    private val emojiAltPhysicalKeyDetector by lazy { EmojiAltPhysicalKeyDetector(latinIME.resources) }

    // We expect to have only one decoder in almost all cases, hence the default capacity of 1.
    // If it turns out we need several, it will get grown seamlessly.
    private val hardwareEventDecoders: SparseArray<HardwareEventDecoder> = SparseArray(1)

    private val keyboardSwitcher = KeyboardSwitcher.getInstance()
    private val settings = Settings.getInstance()
    private val audioAndHapticFeedbackManager = AudioAndHapticFeedbackManager.getInstance()

    // stage 27: finished drawings go to this keyboard (SymbolDrawing), held weakly by SymbolInk
    private val symbolSink = helium314.keyboard.agent.SymbolDrawing.attach(latinIME)

    // language slide state
    private var initialSubtype: InputMethodSubtype? = null
    private var subtypeSwitchCount = 0

    override fun onPressKey(primaryCode: Int, repeatCount: Int, pointerCount: Int, hapticEvent: HapticEvent) {
        if (latinIME.agentEditorController.isBlockingInput) return;
        helium314.keyboard.agent.SymbolDrawing.beforeOtherInput() // a waiting drawn symbol comes first (stage 27)
        spaceKeyDown = primaryCode == Constants.CODE_SPACE
        latinIME.invalidateAgentEditor()
        metaOnPressKey(primaryCode)
        keyboardSwitcher.onPressKey(primaryCode, pointerCount, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
        // we need to use LatinIME for handling of key-down audio and haptics
        latinIME.hapticAndAudioFeedback(primaryCode, repeatCount, hapticEvent)
    }

    override fun onLongPressKey(primaryCode: Int) {
        if (latinIME.agentEditorController.isBlockingInput) return;
        metaOnLongPressKey(primaryCode)
        performHapticFeedback(HapticEvent.KEY_LONG_PRESS)
    }

    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        if (primaryCode == Constants.CODE_SPACE) spaceKeyDown = false
        metaOnReleaseKey(primaryCode)
        keyboardSwitcher.onReleaseKey(primaryCode, withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
    }

    /** Stage 42: the hardware Enter whose key down a snippet field took; its key up must not reach the app either. */
    private var agentEnterDown = false

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        // the key code only: a modifier pressed while Enter is held must not let its key up reach the app
        if (agentEnterDown && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            agentEnterDown = false
            return true
        }
        emojiAltPhysicalKeyDetector.onKeyUp(keyEvent)
        if (!settings.current.mEnableHardwareKeyboard)
            return false

        val keyIdentifier = keyEvent.deviceId.toLong() shl 32 + keyEvent.keyCode
        return inputLogic.mCurrentlyPressedHardwareKeys.remove(keyIdentifier)
    }

    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        latinIME.invalidateAgentEditor()
        if (latinIME.agentEditorController.isBlockingInput) {
            latinIME.requestHideSelf(0)
            agentEnterDown = false // a key up lost while the editor changed must not swallow the next one
            return false // Let the host handle hardware keys while the IME reconnects.
        }
        // stage 42 (P142): while a snippet field is asked, Enter moves on instead of sending the command
        // text; also with the hardware keyboard setting off, where the app would get the key itself
        if (isPlainEnter(keyCode, keyEvent)) {
            // a held key: its repeats belong to the press the field took, never one field per repeat
            if (keyEvent.repeatCount > 0 && agentEnterDown) return true
            agentEnterDown = keyEvent.repeatCount == 0 && KeyboardCommands.onEnter(latinIME)
            if (agentEnterDown) return true
        }
        emojiAltPhysicalKeyDetector.onKeyDown(keyEvent)
        // fork, stage 49 second review: Ctrl+Z / Ctrl+Y of a hardware keyboard let the app change the text
        if (keyEvent.isCtrlPressed && (keyCode == KeyEvent.KEYCODE_Z || keyCode == KeyEvent.KEYCODE_Y))
            inputLogic.mFieldCorrections.untrusted()
        if (!settings.current.mEnableHardwareKeyboard)
            return false

        val event: Event
        if (settings.current.mLocale.language == "ko") { // todo: this does not appear to be the right place
            val subtype = keyboardSwitcher.keyboard?.mId?.subtype ?: RichInputMethodManager.getInstance().currentSubtype
            event = HangulEventDecoder.decodeHardwareKeyEvent(subtype, keyEvent) {
                getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent)
            }
        } else {
            event = getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent)
        }

        if (event.isHandled) {
            inputLogic.onCodeInput(
                settings.current, event,
                keyboardSwitcher.getKeyboardCapsMode(), // TODO: this is not necessarily correct for a hardware keyboard right now
                keyboardSwitcher.getCurrentKeyboardScript(),
                latinIME.mHandler
            )
            return true
        }
        return false
    }

    private fun isPlainEnter(keyCode: Int, keyEvent: KeyEvent) =
        (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && keyEvent.hasNoModifiers()

    /**
     * Stage 89 ("ruhige Leiste"): a character key (code >= 0, space and Enter included) or the delete key starts or continues the writing session
     * of the bar, ends a pinned choice of the tools, and counts the way of a delete for the diagnose (numbers only). Read before the key is
     * handled, so the state is the one the key finds. `InputLogic` is not touched.
     */
    private fun barSessionKey(code: Int) {
        if (code != KeyCode.DELETE && code < 0) return
        if (code == KeyCode.DELETE) {
            val slow = connection.hasSlowInputConnection()
            helium314.keyboard.agent.BarCalmCounters.onDelete(
                if (slow) helium314.keyboard.agent.BarCalmCounters.Delete.SLOW
                else if (inputLogic.hasActiveComposition()) helium314.keyboard.agent.BarCalmCounters.Delete.IN_WORD
                else helium314.keyboard.agent.BarCalmCounters.Delete.RESUME, slow, isProtectedField())
        }
        latinIME.onAgentTyped()
    }

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        if (latinIME.agentEditorController.isBlockingInput) return;
        helium314.keyboard.agent.SymbolDrawing.beforeOtherInput() // codes without a key press, e.g. from the strip
        helium314.keyboard.agent.SymbolInk.onTypedInput() // stage 100: a typed key ends the watch for a late stroke
        helium314.keyboard.agent.UndoOffer.onCode(primaryCode) // stage 88: after an undo the bar offers a redo until the text changes
        barSessionKey(primaryCode) // stage 89: a typed key starts the writing session of the bar
        if (TouchCalibration.active) keyboardSwitcher.mainKeyboardView?.let { view ->
            // view to keyboard coordinates, as KeyDetector does
            TouchCalibration.onKey(primaryCode, x, y, view.paddingLeft, view.paddingTop, keyboardSwitcher.keyboard)
        }
        // stage 50: a delete drops the last word's touches; a held delete counts once; the keyboard shows geometry changes
        helium314.keyboard.agent.TouchModel.onKeyInput(primaryCode, isKeyRepeat, keyboardSwitcher.keyboard)
        latinIME.invalidateAgentEditor()
        val spaceTap = primaryCode == Constants.CODE_SPACE && spaceKeyDown
        spaceKeyDown = false // codes sent while handling this one are no taps
        if (consumeSpaceDoubleTap(spaceTap)) return
        when (primaryCode) {
            KeyCode.AGENT_ACTIONS -> {
                // the Flat style has the panel behind its pen key; the wheel stays on the space bar long press
                if (FlatStyle.isActive(settings.current.mColors)) latinIME.agentEditorController.open()
                else latinIME.agentEditorController.openWheel()
                return
            }
            KeyCode.SKILL_1 -> { latinIME.agentEditorController.openSkill(1); return }
            KeyCode.SKILL_2 -> { latinIME.agentEditorController.openSkill(2); return }
            KeyCode.SKILL_3 -> { latinIME.agentEditorController.openSkill(3); return }
            KeyCode.AGENT_DICTATE -> { latinIME.agentEditorController.openDictation(); return }
            KeyCode.AGENT_EMOJI_MIXER -> { helium314.keyboard.agent.EmojiMixPalette.toggleCurrent(); return } // stage 25a
            KeyCode.AGENT_CHAR_ART -> { latinIME.agentEditorController.openCharArt(); return } // stage 53
            KeyCode.AGENT_PROMPTS -> { latinIME.agentEditorController.openPrompts(); return } // stage 74b
            KeyCode.AGENT_DESIGN -> { // stage 85: the design tool of the bar; like KeyCode.SETTINGS, not with a locked device (asked live)
                if (Settings.getValues().mIsLocked || helium314.keyboard.agent.LockState.isLocked(latinIME)) return
                helium314.keyboard.agent.SettingsLauncher.open(latinIME, helium314.keyboard.settings.SettingsDestination.AgentDesign)
                return
            }
            KeyCode.TOGGLE_AUTOCORRECT -> return settings.toggleAutoCorrect()
            KeyCode.TOGGLE_INCOGNITO_MODE -> {
                settings.toggleAlwaysIncognitoMode()
                BackgroundGatheringCache.clear()
                latinIME.setGestureDataGatheringMode(latinIME.currentInputEditorInfo, false)
                return
            }
            KeyCode.BACKGROUND_GATHERING -> {
                if (BackgroundGatheringCache.isEmpty) {
                    // only enable, no toggle
                    GestureDataGatheringSettings.setBackgroundGatheringEnabled(latinIME.prefs(), true)
                    latinIME.setGestureDataGatheringMode(latinIME.currentInputEditorInfo, false)
                } else {
                    if (GestureDataGatheringSettings.isDiscardByDefault(latinIME))
                        BackgroundGatheringCache.save(latinIME)
                    else
                        BackgroundGatheringCache.clear()
                }
                return
            }
            KeyCode.BACKGROUND_GATHERING_TEMP_OFF -> {
                GestureDataGatheringSettings.tempDisableBackgroundGathering(latinIME.prefs())
                BackgroundGatheringCache.clear()
                latinIME.setGestureDataGatheringMode(latinIME.currentInputEditorInfo, false)
                return
            }
        }
        if (Settings.getValues().mIsLocked && KeyCode.isIsBlockedWhenLocked(primaryCode))
            return
        val mkv = keyboardSwitcher.mainKeyboardView

        // checking if the character is a combining accent
        val event = if (primaryCode in combiningRange) { // todo: should this be done later, maybe in inputLogic?
            Event.createSoftwareDeadEvent(primaryCode, 0, metaState, mkv.getKeyX(x), mkv.getKeyY(y), null)
        } else {
            // todo:
            //  setting meta shift should only be done for arrow and similar cursor movement keys
            //  should only be enabled once it works more reliably (currently depends on app for some reason)
//            if (mkv.keyboard?.mId?.isAlphabetShiftedManually == true)
//                Event.createSoftwareKeypressEvent(primaryCode, metaState or KeyEvent.META_SHIFT_ON, mkv.getKeyX(x), mkv.getKeyY(y), isKeyRepeat)
//            else Event.createSoftwareKeypressEvent(primaryCode, metaState, mkv.getKeyX(x), mkv.getKeyY(y), isKeyRepeat)
            Event.createSoftwareKeypressEvent(primaryCode, metaState, mkv.getKeyX(x), mkv.getKeyY(y), isKeyRepeat)
        }
        latinIME.onEvent(event)
        metaAfterCodeInput(primaryCode)
        if (spaceTap) rememberSpaceTap()
    }

    override fun onTextInput(text: String?) {
        helium314.keyboard.agent.SymbolDrawing.beforeOtherInput()
        helium314.keyboard.agent.SymbolInk.onTypedInput() // stage 100
        latinIME.onTextInput(text)
    }

    override fun onPrivateTextInput(text: String?) {
        helium314.keyboard.agent.SymbolDrawing.beforeOtherInput()
        latinIME.onPrivateTextInput(text)
    }

    override fun onContent(content: InputContentInfoCompat) {
        if (latinIME.agentEditorController.isBlockingInput) return;
        helium314.keyboard.agent.UndoOffer.onTextChange() // stage 88: a sticker or GIF is new content
        latinIME.invalidateAgentEditor()
        val editorInfo = latinIME.currentInputEditorInfo
        val editorMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        if (editorMimeTypes.any { content.description.hasMimeType(it) }) {
            connection.commitContent(content, editorInfo)
        } else if (editorMimeTypes.isEmpty()) { // make the fallback optional?
            latinIME.clipboardHistoryManager.pasteWithoutChangingClips(content)
        }
    }

    // stage 27: with "Symbole malen" the gesture is ink for SymbolInk, never a gesture-typed word;
    // LatinIME, InputLogic and the batch gate (P5) see nothing of it
    override fun onStartBatchInput() {
        helium314.keyboard.agent.UndoOffer.onTextChange() // stage 88: a gesture is typing
        latinIME.onAgentTyped() // stage 89: and it starts the writing session of the bar
        if (!SymbolInk.isEnabled()) return latinIME.onStartBatchInput()
        if (!latinIME.agentEditorController.isBlockingInput) SymbolInk.onBatchStart()
    }

    override fun onUpdateBatchInput(batchPointers: InputPointers?) {
        if (!SymbolInk.isEnabled()) latinIME.onUpdateBatchInput(batchPointers)
    }

    override fun onEndBatchInput(batchPointers: InputPointers?) {
        if (!SymbolInk.isEnabled()) return latinIME.onEndBatchInput(batchPointers)
        if (!latinIME.agentEditorController.isBlockingInput) SymbolInk.onBatchEnd(batchPointers)
    }

    override fun onCancelBatchInput() {
        if (SymbolInk.isEnabled()) SymbolInk.onBatchCancel() else latinIME.onCancelBatchInput()
    }

    // User released a finger outside any key
    override fun onCancelInput() { }

    override fun onFinishSlidingInput() =
        keyboardSwitcher.onFinishSlidingInput(latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)

    override fun onCustomRequest(request: KeyboardActionListener.CustomAction) = when (request) {
        // the input method picker stays with PointerTracker's existing fallback (mSpaceForLangChange)
        KeyboardActionListener.CustomAction.SPACE_HOLD -> settings.current.mSpaceGestures[SpaceGesture.HOLD].let {
            lastSpaceTapTime = 0L // a hold is no tap and must not start a double tap
            it != SpaceAction.SWITCH_INPUT_METHOD && runSpaceAction(it)
        }
        KeyboardActionListener.CustomAction.SHOW_INPUT_METHOD_PICKER -> latinIME.showInputPickerDialog()
        KeyboardActionListener.CustomAction.TOUCHPAD_ON -> {
            keyboardSwitcher.mainKeyboardView?.alpha = 0.5f
            true
        }
        KeyboardActionListener.CustomAction.TOUCHPAD_OFF -> {
            keyboardSwitcher.mainKeyboardView?.alpha = 1f
            true
        }
        KeyboardActionListener.CustomAction.PERFORM_HAPTIC -> {
            performHapticFeedback(HapticEvent.KEY_LONG_PRESS)
            true
        }
    }

    override fun onSpaceSwipe(gesture: SpaceGesture, steps: Int): Boolean = if (resetSpaceTap() && latinIME.agentEditorController.isBlockingInput) true
    else when (val action = settings.current.mSpaceGestures[gesture]) {
        SpaceAction.MOVE_CURSOR -> if (gesture.isHorizontal) onMoveCursorHorizontally(steps) else onMoveCursorVertically(steps)
        SpaceAction.SWITCH_LANGUAGE -> onLanguageSlide(steps)
        SpaceAction.TOUCHPAD_MODE -> {
            // Activate touchpad mode - the actual cursor movement will be handled in PointerTracker

            // Activation and ensure enough room for navigation.
            val requiredSteps = 8

            if (abs(steps) >= requiredSteps) {
                TouchpadHandler.setTouchpadModeActive(true)
                true
            } else {
                false
            }
        }
        else -> runSpaceAction(action)
    }

    // stage 33: a sideways swipe with a one-shot action (undo, redo, …) runs it at the last detent
    private val spaceDetents = SpaceDetents()

    override fun onSpaceSwipeDistance(gesture: SpaceGesture, distance: Int, keyWidth: Int) {
        resetSpaceTap()
        if (latinIME.agentEditorController.isBlockingInput) return
        val action = settings.current.mSpaceGestures[gesture]
        if (action == SpaceAction.NONE || action.isContinuous) return
        val unit = SpaceDetents.unitPx(keyWidth, latinIME.resources.displayMetrics.density)
        when (spaceDetents.move(gesture == SpaceGesture.SWIPE_LEFT, distance, unit)) {
            SpaceDetents.Event.DETENT -> HapticChain.play(latinIME, settings.current, HapticChain.Feel.DETENT)
            // the strong impulse says "done": only when the action really did something (review)
            SpaceDetents.Event.COMPLETE -> if (runSpaceAction(action)) HapticChain.play(latinIME, settings.current, HapticChain.Feel.STRETCH)
            SpaceDetents.Event.NONE -> Unit
        }
    }

    override fun onEndSpaceSwipe() {
        spaceDetents.end() // lifted before the last detent: nothing happened
        if (latinIME.agentEditorController.isBlockingInput) return;
        initialSubtype = null
        subtypeSwitchCount = 0
    }

    /**
     * Runs a one-shot space bar action (swipe, hold or double tap) through the existing key codes and
     * controller entry points, so locked device, password and incognito keep their restrictions.
     * Returns whether something was done.
     */
    private fun runSpaceAction(action: SpaceAction): Boolean {
        if (latinIME.agentEditorController.isBlockingInput) return false
        fun code(code: Int): Boolean {
            onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            return true
        }
        fun toggle(layout: LayoutDirective.Utility): Boolean {
            toggleLayout(layout, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
            return true
        }
        return when (action) {
            SpaceAction.NONE, SpaceAction.MOVE_CURSOR, SpaceAction.TOUCHPAD_MODE -> false
            SpaceAction.SWITCH_LANGUAGE -> code(KeyCode.LANGUAGE_SWITCH)
            SpaceAction.SWITCH_INPUT_METHOD -> code(KeyCode.SYSTEM_INPUT_METHOD_PICKER)
            SpaceAction.ACTION_WHEEL -> latinIME.agentEditorController.openWheel()
            SpaceAction.ACTION_PANEL -> { latinIME.agentEditorController.open(); true }
            SpaceAction.DICTATION -> code(KeyCode.AGENT_DICTATE)
            SpaceAction.CLIPBOARD -> code(KeyCode.CLIPBOARD)
            SpaceAction.EMOJI -> code(KeyCode.EMOJI)
            SpaceAction.COMMAND_LIST -> typeCommandPrefix()
            SpaceAction.UNDO -> code(KeyCode.UNDO)
            SpaceAction.REDO -> code(KeyCode.REDO)
            SpaceAction.SELECT_ALL -> code(KeyCode.CLIPBOARD_SELECT_ALL)
            SpaceAction.TOGGLE_NUMPAD -> toggle(LayoutDirective.Utility.NUMPAD)
            SpaceAction.TOGGLE_SYMBOLS -> toggle(LayoutDirective.Utility.SYMBOLS)
            SpaceAction.TOGGLE_DPAD -> toggle(LayoutDirective.Utility.DPAD)
            SpaceAction.HIDE_KEYBOARD -> {
                latinIME.requestHideSelf(0)
                true
            }
        }
    }

    /**
     * Types the command prefix at a word start (a space first if the cursor follows a word), so the
     * strip lists all commands. Typed like keys, so commands see exactly what a user would type.
     * Nothing happens where commands are off or blocked (password field, incognito, locked device).
     */
    private fun typeCommandPrefix(): Boolean {
        if (!KeyboardCommands.isEnabled(latinIME) || KeyboardCommands.isBlocked(settings.current, latinIME.currentInputEditorInfo)) return false
        if (connection.hasSelection()) return false
        val before = connection.getTextBeforeCursor(1, 0) ?: return false
        val last = before.lastOrNull()
        if (last != null && !last.isWhitespace() && last != '(')
            onCodeInput(Constants.CODE_SPACE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        onCodeInput(KeyboardCommands.prefix(latinIME).code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        return true
    }

    // double tap on space: the first tap types its space, a second tap at the same cursor within the
    // double-space-period timeout runs the action instead of typing (double space period is off then).
    // Only a code between onPressKey and onReleaseKey of the space key is a tap; the strip, popup
    // keys and the actions' own codes never press a key.
    private var spaceKeyDown = false
    private var lastSpaceTapTime = 0L

    /** A swipe is no tap: the next tap starts a new double tap. Always true. */
    private fun resetSpaceTap(): Boolean {
        lastSpaceTapTime = 0L
        return true
    }
    private var lastSpaceTapCursor = -1

    private fun consumeSpaceDoubleTap(spaceTap: Boolean): Boolean {
        val previous = lastSpaceTapTime
        lastSpaceTapTime = 0L
        if (previous == 0L || !spaceTap) return false
        val action = settings.current.mSpaceGestures[SpaceGesture.DOUBLE_TAP]
        if (action == SpaceAction.NONE) return false
        if (SystemClock.uptimeMillis() - previous >= settings.current.mDoubleSpacePeriodTimeout) return false
        if (connection.hasSelection() || connection.expectedSelectionStart != lastSpaceTapCursor) return false
        runSpaceAction(action)
        return true
    }

    private fun rememberSpaceTap() {
        if (settings.current.mSpaceGestures[SpaceGesture.DOUBLE_TAP] == SpaceAction.NONE) return
        lastSpaceTapTime = SystemClock.uptimeMillis()
        lastSpaceTapCursor = connection.expectedSelectionStart
    }

    override fun toggleLayout(layout: LayoutDirective.Utility, autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?) {
        keyboardSwitcher.toggleLayout(layout, autoCapsFlags, recapitalizeMode)
    }

    override fun onLongPressAlphaSymbolForNumpad() {
        keyboardSwitcher.onLongPressAlphaSymbolForNumpad()
    }

    /** The field a delete slide started in (null: none going); a lift in another field deletes nothing. */
    private var deleteSlideField: Any? = null

    override fun onMoveDeletePointer(steps: Int) {
        if (latinIME.agentEditorController.isBlockingInput) return;
        if (deleteSlideField == null) deleteSlideField = settings.current.mInputAttributes
        inputLogic.finishInput()
        val end = connection.expectedSelectionEnd
        val actualSteps = actualSteps(steps)
        val start = connection.expectedSelectionStart + actualSteps
        if (start > end) return
        gestureMoveBackHaptics()
        connection.setSelection(start, end)
    }

    private fun actualSteps(steps: Int): Int {
        val text = if (steps > 0) connection.getSelectedText(0) ?: return steps
        else connection.getTextBeforeCursor(-steps * 4, 0) ?: return steps
        return moveStepsToCharCount(text, steps)
    }

    override fun onUpWithDeletePointerActive() {
        val tracked = deleteClaimed // stage 87b: a slide the trackpad took (counts only)
        deleteClaimed = false
        // the length of the selection is a partial fact about the field's text: not read at all in a protected field (the counters know it from the claim)
        val diag = CursorTrackCounters.shared
        val selected = if (tracked && !diag.isProtectedSwipe(CursorTrackCounters.Key.DELETE)) (connection.expectedSelectionEnd - connection.expectedSelectionStart).coerceAtLeast(0) else 0
        val deleted = deleteAtLift()
        if (deleted) latinIME.onAgentTyped() // stage 89: the wipe-delete is a delete
        if (tracked) diag.onDeleteLift(selected, deleted)
    }

    /** Stage 87b (counters only): a field whose text must leave no trace in the diagnosis: password, incognito or no-learning field, locked device. */
    private fun isProtectedField(): Boolean {
        val values = settings.current
        // the live lock question the project uses for such decisions (shared, answers for 1 s / 5 s; not a cached settings value)
        return values.mInputAttributes?.mIsPasswordField == true || values.mIncognitoModeEnabled || helium314.keyboard.agent.LockState.isLocked(latinIME)
    }

    /** Deletes what the slide selected; false where nothing was deleted. */
    private fun deleteAtLift(): Boolean {
        // the selection to delete is the one the slide made: a field that started meanwhile has another one (a URL bar selects all on focus)
        val slideField = deleteSlideField
        deleteSlideField = null
        if (slideField != null && slideField !== settings.current.mInputAttributes) return false
        if (latinIME.agentEditorController.isBlockingInput) return false
        if (!connection.hasSelection()) return false
        inputLogic.finishInput()
        onCodeInput(KeyCode.DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        return true
    }

    override fun resetMetaState() {
        metaState = 0
    }

    private fun onLanguageSlide(steps: Int): Boolean {
        if (abs(steps) < settings.current.mLanguageSwipeDistance) return false
        val subtypes = SubtypeSettings.getEnabledSubtypes(true)
        if (subtypes.size <= 1) { // only allow if we have more than one subtype
            return false
        }
        // decide next or previous dependent on up or down
        val current = RichInputMethodManager.getInstance().currentSubtype.rawSubtype
        var wantedIndex = subtypes.indexOf(current) + if (steps > 0) 1 else -1
        wantedIndex %= subtypes.size
        if (wantedIndex < 0) {
            wantedIndex += subtypes.size
        }
        val newSubtype = subtypes[wantedIndex]

        // do not switch if we would switch to the initial subtype after cycling all other subtypes
        if (initialSubtype == null) initialSubtype = current
        if (initialSubtype == newSubtype) {
            if ((subtypeSwitchCount > 0 && steps > 0) || (subtypeSwitchCount < 0 && steps < 0)) {
                return true
            }
        }
        if (steps > 0) subtypeSwitchCount++ else subtypeSwitchCount--

        keyboardSwitcher.switchToSubtype(newSubtype)
        return true
    }

    private fun onMoveCursorVertically(steps: Int): Boolean {
        if (steps == 0) return false
        val code = if (steps < 0) {
            gestureMoveBackHaptics()
            KeyCode.ARROW_UP
        } else {
            gestureMoveForwardHaptics()
            KeyCode.ARROW_DOWN
        }
        onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        return true
    }

    private fun onMoveCursorHorizontally(rawSteps: Int): Boolean {
        if (rawSteps == 0) return false
        // for RTL languages we want to invert pointer movement
        val rtl = RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
        val steps = if (rtl) -rawSteps else rawSteps
        val moveSteps: Int
        if (steps < 0) {
            val text = connection.getTextBeforeCursor(-steps * 4, 0) ?: return false
            moveSteps = moveStepsToCharCount(text, steps)
            if (moveSteps == 0) {
                // some apps don't return any text via input connection, and the cursor can't be moved
                // we fall back to virtually pressing the left/right key one or more times instead
                repeat(-steps) {
                    onCodeInput(if (rtl) KeyCode.ARROW_RIGHT else KeyCode.ARROW_LEFT, Constants.NOT_A_COORDINATE,
                        Constants.NOT_A_COORDINATE, false)
                }
                if (text.isNotEmpty()) {
                    gestureMoveBackHaptics()
                }
                return true
            }
            gestureMoveBackHaptics()
        } else {
            val text = connection.getTextAfterCursor(steps * 4, 0) ?: return false
            moveSteps = moveStepsToCharCount(text, steps)
            if (moveSteps == 0) {
                // some apps don't return any text via input connection, and the cursor can't be moved
                // we fall back to virtually pressing the left/right key one or more times instead
                repeat(steps) {
                    onCodeInput(if (rtl) KeyCode.ARROW_LEFT else KeyCode.ARROW_RIGHT, Constants.NOT_A_COORDINATE,
                        Constants.NOT_A_COORDINATE, false)
                }
                if (text.isNotEmpty()) {
                    gestureMoveForwardHaptics(true)
                }
                return true
            }
            gestureMoveForwardHaptics(text.isNotEmpty())
        }
        return applyHorizontalMove(moveSteps)
    }

    /** Moves the cursor by [moveSteps] chars (signed) through the editor: the end of the old horizontal swipe step, unchanged (stage 87 extracted it). */
    private fun applyHorizontalMove(moveSteps: Int): Boolean {
        inputLogic.setExpectCursorMove()

        // the shortcut below causes issues due to horrible handling of text fields by Firefox and forks
        // issues:
        //  * setSelection "will cause the editor to call onUpdateSelection", see: https://developer.android.com/reference/android/view/inputmethod/InputConnection#setSelection(int,%20int)
        //     but Firefox is simply not doing this within the same word... WTF?
        //     https://github.com/HeliBorg/HeliBoard/issues/1139#issuecomment-2588169384
        //  * inputType is NOT of variant InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT (variant appears to always be 0)
        //     -> this is "fixed" now using AppWorkarounds.adjustInputType
        val variation = InputType.TYPE_MASK_VARIATION and Settings.getValues().mInputAttributes.mInputType
        if (variation != InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                && inputLogic.moveCursorByAndReturnIfInsideComposingWord(moveSteps)) {
            // no need to finish input and restart suggestions if we're still in the word
            // this is a noticeable performance improvement when moving through long words
            val newPosition = connection.expectedSelectionStart + moveSteps
            connection.setSelection(newPosition, newPosition)
            return true
        }

        inputLogic.finishInput()
        // fork, stage 49 second review: the selection update of this move is expected and skips the checkpoint
        inputLogic.checkpointFieldCorrectionsBeforeOwnCursorMove(settings.current)
        val newPosition = connection.expectedSelectionStart + moveSteps
        connection.setSelection(newPosition, newPosition)
        inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current, keyboardSwitcher.currentKeyboardScript)
        return true
    }

    // ---- stage 87: cursor trackpad (space bar: all directions; delete slider: word-aware), see CursorTrack in agent-core

    // One state per finger (pointer id); at most one finger owns the track (trackOwner). No press, repeat or end of another
    // finger touches a state: a state is dropped only by its own finger's end (onCursorTrackEnd) or by a new touch of that finger.
    private enum class TrackMode { UNDECIDED, OFF, PENDING, REFUSED, ACTIVE }
    private class TrackSample(val t: Long, val dx: Double, val dy: Double)
    private class TrackState {
        var token = Long.MIN_VALUE // downTime of the touch this state belongs to
        var code = 0
        var mode = TrackMode.UNDECIDED
        val pending = ArrayList<TrackSample>()
        var originX = 0.0 // finger position (dp from touch-down) where the track (re)started: travel counts from here
        var originY = 0.0
        var rtl = false
        var track: CursorTrack? = null
        var needsRestart = false
        var awaitSelection = false // an arrow key was sent: nothing else is sent and horizontal moves wait for the editor's answer
        var selectionAtArrow = 0
        var arrowDir = 0
        var arrowTime = 0L
        var pendingLines = 0 // lines the finger asked for and no arrow was sent for yet (at most MAX_PENDING_LINES either way)
        var edgeUp = false // an arrow in this direction moved nothing (or the cursor was at the start): none is sent for the rest of the touch
        var edgeDown = false
        var field: Any? = null // the field the track started in: a new field ends it
        var suppressedLines = 0 // stage 87b (counters only): vertical steps a finger made in a field that is not multi-line
        var runsAction = false // stage 87c review: this finger's start direction decided an old action (OFF) and the finger is still down: no other finger takes the trackpad meanwhile
        var startDecides = false // stage 87c: some direction has an action: the start direction of the swipe decides between trackpad and that action
        fun begin(token: Long, code: Int) {
            this.token = token; this.code = code; mode = TrackMode.UNDECIDED; pending.clear()
            originX = 0.0; originY = 0.0; track = null; needsRestart = false; awaitSelection = false
            pendingLines = 0; edgeUp = false; edgeDown = false; field = null; startDecides = false; runsAction = false; suppressedLines = 0
        }
    }
    private val spaceTrack = CursorTrack()
    private val flatTrack = CursorTrack(horizontalOnly = true) // delete slider and space bar in a single-line field
    private val trackStates = Array(10) { TrackState() }
    private var trackOwner = -1

    override fun onCursorTrackEnd(pointerId: Int) {
        if (pointerId < 0) { // -1: the field starts or the keyboard hides (LatinIME): a touch that ended without up or cancel must leave no owner
            trackOwner = -1
            deleteClaimed = false // a slide claimed in the old field: its lift must not count the selection of the new one (stage 87b)
            for (st in trackStates) when (st.mode) {
                TrackMode.ACTIVE -> st.track = null // an active touch has nothing to move in the new field
                TrackMode.OFF -> st.runsAction = false // a swipe that began as the old action stays that: it must not turn into the trackpad of the new field (stage 87c); it stops locking out other fingers, so a lock whose end never came cannot stay
                else -> { countNeverClaimed(st); st.begin(Long.MIN_VALUE, 0) }
            }
            return
        }
        val st = trackStates.getOrNull(pointerId) ?: return
        countNeverClaimed(st)
        if (st.code == KeyCode.DELETE && st.mode == TrackMode.ACTIVE) deleteClaimed = false // only the claiming finger's end (a lift has read it before); a refused second finger leaves it
        st.begin(Long.MIN_VALUE, 0)
        if (trackOwner == pointerId) trackOwner = -1
    }

    override fun onCursorTrack(pointerId: Int, code: Int, dx: Int, dy: Int, eventTime: Long, downTime: Long): Int {
        val st = trackStates.getOrNull(pointerId) ?: return KeyboardActionListener.TRACK_OFF
        if (st.token != downTime || st.code != code) { // a new touch of this finger: whatever it had before is gone
            if (trackOwner == pointerId) trackOwner = -1
            countNeverClaimed(st)
            if (st.code == KeyCode.DELETE && st.mode == TrackMode.ACTIVE) deleteClaimed = false // its end never came (stage 87b)
            st.begin(downTime, code)
            trackKey(code)?.let { CursorTrackCounters.shared.onTouch(it) } // stage 87b
        }
        if (st.mode == TrackMode.UNDECIDED) st.mode = decideTrackMode(pointerId, code)
        if (st.mode == TrackMode.OFF) return KeyboardActionListener.TRACK_OFF
        val density = latinIME.resources.displayMetrics.density.toDouble()
        val sample = TrackSample(eventTime, dx / density, dy / density)
        // the old swipe begins at PointerTracker.sPointerStep px (floor(10 * density) at class load): the raw px are compared with exactly that value, not dp (stage 87c review N-1)
        val stepPx = PointerTracker.getPointerStep().toDouble() // the tracker's own value: it is read once at class load, a formula here could drift from it (review N-2)
        if (st.mode == TrackMode.REFUSED) { // a refused finger stays a tap or a hold until it drags: then its touch is dropped
            return if (CursorTrackpad.claims(code == KeyCode.DELETE, dx.toDouble(), dy.toDouble(), stepPx)) KeyboardActionListener.TRACK_REFUSED_DRAG
            else KeyboardActionListener.TRACK_PENDING
        }
        if (st.mode == TrackMode.PENDING) {
            if (st.pending.size >= CursorTrackpad.MAX_PENDING) st.pending.removeAt(0) // a finger resting on the key for long
            st.pending += sample
            var legacy = false
            if (st.startDecides) {
                // stage 87c: the start decides, at the first report that reaches 10 dp (the old swipe's own moment and comparison: no waiting zone)
                val gestures = settings.current.mSpaceGestures
                val start = CursorTrackpad.decideStart(gestures, dx.toDouble(), dy.toDouble(), isMultiLineField(), stepPx)
                if (start == CursorTrackpad.Start.UNDECIDED) return KeyboardActionListener.TRACK_PENDING
                legacy = start == CursorTrackpad.Start.LEGACY
                // only a swipe that runs a bound action counts for the other fingers (one that is swallowed, "nothing" in a single-line field, does not)
                st.runsAction = legacy && gestures[CursorTrackpad.startDirection(dx.toDouble(), dy.toDouble(), stepPx)] != SpaceAction.NONE
            } else if (!CursorTrackpad.claims(code == KeyCode.DELETE, dx.toDouble(), dy.toDouble(), stepPx)) return KeyboardActionListener.TRACK_PENDING
            // one finger owns the track, or another finger on the space bar is running an old action that began with its start direction (its
            // continuing action must not run beside a cursor drag): a swipe that would take the trackpad, or run an action, is dropped
            if (trackOwner != -1 && trackOwner != pointerId || code == Constants.CODE_SPACE && !legacy && otherFingerRunsAnAction(pointerId)) { // one finger owns the track: this one's touch is dropped, a swipe that would run an action included
                st.mode = TrackMode.REFUSED; st.pending.clear()
                trackKey(code)?.let { CursorTrackCounters.shared.onRefused(it, if (trackOwner != -1 && trackOwner != pointerId) CursorTrackCounters.RefusedWhy.OTHER_OWNER else CursorTrackCounters.RefusedWhy.OTHER_ACTION) } // stage 87b
                return KeyboardActionListener.TRACK_REFUSED_DRAG
            }
            // LEGACY is possible only here, before the claim: the old code then runs from the touch-down point
            if (legacy) {
                // stage 87b (counters only): the start direction and the action it runs; this is a decision, not a failure
                val direction = CursorTrackpad.startDirection(dx.toDouble(), dy.toDouble(), stepPx)
                CursorTrackCounters.shared.onAction(direction, settings.current.mSpaceGestures[direction])
                st.mode = TrackMode.OFF; st.pending.clear(); return KeyboardActionListener.TRACK_OFF
            } // runsAction (set above) stays until this finger's end
            // the drag is a swipe now: read the text once, then replay what the finger did so far. The track starts at the first
            // sample that arrived (zero travel, zero speed): what the finger did before (the fast-typing guard swallows the first
            // moves) is not replayed as one jump
            trackOwner = pointerId
            st.mode = TrackMode.ACTIVE
            st.field = settings.current.mInputAttributes
            if (code == KeyCode.DELETE) { deleteSlideField = st.field; deleteClaimed = true }
            trackKey(code)?.let { CursorTrackCounters.shared.onClaimed(it, settings.current.mInputAttributes?.mInputType, isProtectedField()) } // stage 87b: counts and flags only
            resetSpaceTap()
            val first = st.pending.first()
            if (code == Constants.CODE_SPACE && connection.hasSelection()) { collapseSelection(dx.toDouble(), dy.toDouble(), stepPx); CursorTrackCounters.shared.onCollapsed() } // (87b: counted)
            st.originX = first.dx; st.originY = first.dy
            startTrack(st, code, first.t, eventTime)
            val replay = ArrayList(st.pending)
            st.pending.clear()
            for (s in replay) feedTrack(st, code, s)
            return KeyboardActionListener.TRACK_ACTIVE
        }
        feedTrack(st, code, sample)
        return KeyboardActionListener.TRACK_ACTIVE
    }

    /** Another finger on the space bar is still running an old action that its start direction decided: it ends with that finger's end (onCursorTrackEnd). */
    private fun otherFingerRunsAnAction(pointerId: Int) = trackStates.withIndex().any { (i, st) -> i != pointerId && st.mode == TrackMode.OFF && st.runsAction }

    private fun decideTrackMode(pointerId: Int, code: Int): TrackMode {
        val values = settings.current
        val diag = CursorTrackCounters.shared
        val key = trackKey(code)
        if (!values.mCursorTrackpad) { key?.let { diag.onOff(it, CursorTrackCounters.OffReason.SWITCH) }; return TrackMode.OFF }
        if (latinIME.agentEditorController.isBlockingInput) { key?.let { diag.onOff(it, CursorTrackCounters.OffReason.BLOCKED) }; return TrackMode.OFF }
        // one owner for every swipe on space and delete: while a finger owns a track (or a delete slide is going) no other finger swipes
        if ((code == Constants.CODE_SPACE || code == KeyCode.DELETE) && trackOwner != -1 && trackOwner != pointerId) {
            key?.let { diag.onRefused(it, CursorTrackCounters.RefusedWhy.OTHER_OWNER) }
            return TrackMode.REFUSED
        }
        var startDecides = false
        val candidate = when {
            // a selection does not matter here (stage 87c): the trackpad collapses it when it starts
            code == Constants.CODE_SPACE -> {
                val reason = CursorTrackpad.spaceReason(values.mSpaceGestures, true)
                startDecides = reason == CursorTrackpad.SpaceReason.QUALIFIES_START_DECIDES
                if (!reason.qualifies) diag.onOff(CursorTrackCounters.Key.SPACE, CursorTrackCounters.OffReason.NO_CURSOR_DIRECTION)
                reason.qualifies
            }
            code == KeyCode.DELETE -> true
            else -> false
        }
        if (!candidate) return TrackMode.OFF
        trackStates.getOrNull(pointerId)?.startDecides = startDecides
        return TrackMode.PENDING
    }

    private fun trackKey(code: Int): CursorTrackCounters.Key? = when (code) {
        Constants.CODE_SPACE -> CursorTrackCounters.Key.SPACE
        KeyCode.DELETE -> CursorTrackCounters.Key.DELETE
        else -> null
    }

    /** Stage 87b: a finger that rested or dragged short of the claim distance and is gone (or replaced) never became a swipe. */
    private fun countNeverClaimed(st: TrackState) {
        if (st.mode == TrackMode.PENDING) trackKey(st.code)?.let { CursorTrackCounters.shared.onNeverClaimed(it) }
    }

    /** Stage 87b: a delete slide went through the trackpad (read and reset by its lift). */
    private var deleteClaimed = false

    /**
     * The trackpad starts with a selection (for instance after "select all" on hold): like arrow keys in a text editor, a swipe that starts
     * towards the left or up collapses it to its start, towards the right or down to its end; the free movement goes on from there.
     * Nothing is typed, deleted or replaced.
     */
    private fun collapseSelection(dx: Double, dy: Double, claim: Double) {
        val rtl = RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
        val toStart = CursorTrackpad.collapsesToStart(CursorTrackpad.startDirection(dx, dy, claim), rtl)
        val position = if (toStart) connection.expectedSelectionStart else connection.expectedSelectionEnd
        inputLogic.setExpectCursorMove() // like applyHorizontalMove: the selection update of this move is the keyboard's own
        inputLogic.finishInput()
        inputLogic.checkpointFieldCorrectionsBeforeOwnCursorMove(settings.current)
        connection.setSelection(position, position)
    }

    /** Vertical moves only where the field is known to be multi-line: in a single-line field DPAD_DOWN leaves the field or steps through suggestions. */
    private fun isMultiLineField(): Boolean {
        return CursorTrackpad.isMultiLine(settings.current.mInputAttributes?.mInputType)
    }

    private fun startTrack(st: TrackState, code: Int, begin: Long, time: Long) {
        val track = if (code == Constants.CODE_SPACE && isMultiLineField()) spaceTrack else flatTrack
        st.track = track
        st.rtl = code == Constants.CODE_SPACE && RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
        track.magnetism = CursorTrack.magnetismOfPercent(settings.current.mCursorMagnetism)
        track.begin(begin)
        provideTrackText(st, code, time)
    }

    /**
     * The text around the cursor for word edges; for the delete slider around the start of the selection, the selected part included.
     * An editor may return less than asked, so a short answer is no text start or end: only an empty answer is.
     */
    private fun provideTrackText(st: TrackState, code: Int, time: Long) {
        val track = st.track ?: return
        val n = CursorTrackpad.WINDOW_CHARS
        val before = connection.getTextBeforeCursor(n, 0)
        val after = connection.getTextAfterCursor(n, 0)
        val selected = if (code == KeyCode.DELETE) connection.getSelectedText(0) else null
        // the length of the selection comes from the keyboard's own idea of it, not from getSelectedText (an editor may answer null)
        val selectedLength = if (code == KeyCode.DELETE) (connection.expectedSelectionEnd - connection.expectedSelectionStart).coerceAtLeast(0) else 0
        if (before == null || after == null || selectedLength > 0 && selected?.length != selectedLength
            || before.isEmpty() && after.isEmpty() && selected.isNullOrEmpty()) {
            track.setTextUnavailable(time) // an app that gives no text: one cluster per step through the old path
        } else {
            // a shorter answer than asked may be the real start or end or a cut: one more character is asked for; only if the editor has
            // nothing more (an empty answer for the further read) the window reaches the start/end of the field
            val atStart = before.isEmpty() || before.length < n && (connection.getTextBeforeCursor(before.length + 1, 0)?.length ?: 0) <= before.length
            val atEnd = after.isEmpty() || after.length < n && (connection.getTextAfterCursor(after.length + 1, 0)?.length ?: 0) <= after.length
            track.setText(before, (selected?.toString() ?: "") + after, atStart, atEnd, time)
        }
        if (code == KeyCode.DELETE) track.forwardLimitChars = selectedLength
    }

    /** The real cursor position, read from the editor (one IPC), or null where it cannot be read. */
    private fun readEditorSelection(): Int? = try {
        latinIME.currentInputConnection?.getExtractedText(ExtractedTextRequest(), 0)?.let { it.selectionStart + it.startOffset }
    } catch (e: Exception) { null }

    private fun feedTrack(st: TrackState, code: Int, s: TrackSample) {
        val track = st.track ?: return
        if (st.field !== settings.current.mInputAttributes) { // a new field (or the window closed): the touch has nothing to move any more
            if (trackOwner == trackStates.indexOf(st)) trackOwner = -1
            st.track = null
            return
        }
        if (latinIME.agentEditorController.isBlockingInput) { st.needsRestart = true; return }
        if (st.needsRestart) {
            // nothing was fed meanwhile: go on from where the finger is now, with no travel from before (never one lump)
            st.needsRestart = false; st.awaitSelection = false; st.pendingLines = 0
            st.originX = s.dx; st.originY = s.dy; st.suppressedLines = 0 // (87b: the travel for the count starts here too)
            track.begin(s.t - 10)
            provideTrackText(st, code, s.t)
        }
        if (st.awaitSelection) resolveArrow(st, s.t)
        // not before the editor has moved its cursor after an arrow key: the text would be read at the old place
        if (!st.awaitSelection && track.needsText(s.t)) provideTrackText(st, code, s.t)
        val fx = s.dx - st.originX
        val x = if (st.rtl) -fx else fx // for RTL languages the finger follows the visual direction (as the old swipe)
        val steps = track.moveTo(x, s.dy - st.originY, s.t)
        val diag = CursorTrackCounters.shared
        val key = if (code == KeyCode.DELETE) CursorTrackCounters.Key.DELETE else CursorTrackCounters.Key.SPACE
        for (run in CursorTrackpad.runs(steps)) {
            if (run.detent) diag.onDetent(key)
            if (code == KeyCode.DELETE) { if (moveDeleteRun(run)) diag.onSteps(key, run.count) else st.needsRestart = true } else moveCursorRun(st, run)
        }
        if (code == Constants.CODE_SPACE && track === flatTrack) { // stage 87b: arrows a finger asked for in a field that is not multi-line (counted per 26 dp of vertical travel)
            val lines = (Math.abs(s.dy - st.originY) / CursorTrack.VERTICAL_STEP_DP).toInt()
            if (lines > st.suppressedLines) { diag.onSuppressedArrows(key, lines - st.suppressedLines); st.suppressedLines = lines }
        }
        pumpArrows(st, s.t)
    }

    /**
     * After an arrow key: has the editor answered (its cursor position changed)? After [CursorTrackpad.SELECTION_WAIT_MS] the real position
     * is read from the editor: moved (the answer is only late: the keyboard's idea of the cursor is corrected) or not moved (the end of
     * the field in that direction: no further arrow that way in this touch, an unhandled arrow would move the focus to another field).
     */
    private fun resolveArrow(st: TrackState, time: Long) {
        var moved = connection.expectedSelectionStart != st.selectionAtArrow
        if (!moved && time - st.arrowTime >= CursorTrackpad.SELECTION_WAIT_MS) {
            val real = readEditorSelection()
            if (real != null && real != st.selectionAtArrow) {
                connection.resetCachesUponCursorMoveAndReturnSuccess(real, real, false)
                moved = true
            } else {
                if (st.arrowDir < 0) st.edgeUp = true else st.edgeDown = true
                st.pendingLines = 0
                st.awaitSelection = false
                return
            }
        }
        if (moved) {
            st.awaitSelection = false
            if (st.arrowDir < 0) st.edgeDown = false else st.edgeUp = false
        }
    }

    /** Sends at most one arrow key, and only when the editor has answered the last one (a flick cannot queue a burst). */
    private fun pumpArrows(st: TrackState, time: Long) {
        if (st.awaitSelection || st.pendingLines == 0) return
        val dir = if (st.pendingLines < 0) -1 else 1
        val atEnd = if (dir < 0) st.edgeUp || connection.expectedSelectionStart <= 0
        else st.edgeDown || !connection.hasTextAfterCursor()
        if (atEnd) { // known first/last position: no arrow (the next one would leave the field)
            if (dir < 0) st.edgeUp = true else st.edgeDown = true
            st.pendingLines = 0
            return
        }
        if (dir < 0) gestureMoveBackHaptics() else gestureMoveForwardHaptics()
        st.pendingLines -= dir
        st.awaitSelection = true; st.selectionAtArrow = connection.expectedSelectionStart; st.arrowDir = dir; st.arrowTime = time
        st.track?.holdForEditor(time) // the text read before this arrow is out of date until the editor has answered it
        onCodeInput(if (dir < 0) KeyCode.ARROW_UP else KeyCode.ARROW_DOWN, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        CursorTrackCounters.shared.onVerticalArrow(CursorTrackCounters.Key.SPACE)
    }

    private fun moveCursorRun(st: TrackState, run: CursorTrackpad.Run) {
        if (run.axis == CursorTrack.Axis.VERTICAL) {
            // the editor moves by visual line and scrolls by itself: the lines are asked for here and sent one arrow at a time (pumpArrows)
            st.pendingLines = (st.pendingLines + run.direction * run.count).coerceIn(-CursorTrackpad.MAX_PENDING_LINES, CursorTrackpad.MAX_PENDING_LINES)
            return
        }
        if (run.chars == 0) { // no text known: the old path moves by clusters and falls back to arrow keys
            onMoveCursorHorizontally(if (st.rtl) -run.direction * run.count else run.direction * run.count)
            CursorTrackCounters.shared.onSteps(CursorTrackCounters.Key.SPACE, run.count)
            return
        }
        if (st.awaitSelection) { st.needsRestart = true; return } // never from the old position while an arrow is on its way (the track holds these back; defensive)
        trackTick(run, forward = run.direction > 0)
        applyHorizontalMove(run.chars)
        CursorTrackCounters.shared.onSteps(CursorTrackCounters.Key.SPACE, run.count)
    }

    /** Returns false when the step could not be applied (the track and the field disagree: the caller restarts the track). */
    private fun moveDeleteRun(run: CursorTrackpad.Run): Boolean {
        if (run.chars == 0) { onMoveDeletePointer(run.direction * run.count); return true }
        // as onMoveDeletePointer: the selection start moves, the end stays; never past the end
        inputLogic.finishInput()
        val end = connection.expectedSelectionEnd
        val start = connection.expectedSelectionStart + run.chars
        if (start > end || start < 0) return false
        trackTick(run, forward = false)
        connection.setSelection(start, end)
        return true
    }

    /** One tick per run, the stronger detent where a slow finger reached a word edge (the haptic settings decide whether it is felt). */
    private fun trackTick(run: CursorTrackpad.Run, forward: Boolean) {
        if (run.detent) HapticChain.play(latinIME, settings.current, HapticChain.Feel.DETENT)
        else if (forward) gestureMoveForwardHaptics(true) else gestureMoveBackHaptics()
    }

    private fun gestureMoveBackHaptics() {
        if (connection.canDeleteCharacters()) {
            performHapticFeedback(HapticEvent.GESTURE_MOVE)
        }
    }

    // hasTextAfterCursor is used because text before the cursor is cached, going through the InputConnection can be slow
    private fun gestureMoveForwardHaptics(hasTextAfterCursor: Boolean? = null) {
        if (hasTextAfterCursor ?: connection.hasTextAfterCursor()) {
            performHapticFeedback(HapticEvent.GESTURE_MOVE)
        }
    }

    private fun performHapticFeedback(hapticEvent: HapticEvent) {
        audioAndHapticFeedbackManager.performHapticFeedback(keyboardSwitcher.visibleKeyboardView, hapticEvent)
    }

    private fun getHardwareKeyEventDecoder(deviceId: Int): HardwareEventDecoder {
        hardwareEventDecoders.get(deviceId)?.let { return it }

        // TODO: create the decoder according to the specification
        val newDecoder = HardwareKeyboardEventDecoder(deviceId)
        hardwareEventDecoders.put(deviceId, newDecoder)
        return newDecoder
    }

    // -------------------------- meta state handling -----------------------------

    // current state
    // press enables meta
    // release keeps meta enabled, unless there was a onCodeInput for a different key in between
    // onCodeInput ends the meta if it was enabled
    // long press on meta key also ends meta so popups are handled properly
    // sliding from a meta key to some other words too, though this was not intended (and there are no sliding key input graphics)

    // todo: move meta state tracking to KeyboardState? seems more suitable, also for handling sliding input
    //  but the issue is that meta state is used in Event to determine whether it's a functional Event (does not add a character)
    //  (and also it's in the hardware keyEvents which are handled by onKeyUp/Down, but that should be manageable)

    /** actual Android metaState like in KeyEvent */
    private var metaState = 0

    /** keeps track of the state of meta keys by (HeliBoard) KeyCodes */
    private val metaPressStates = SparseArray<MetaPressState>(4)

    // todo: lock and non-lock versions interact badly: when any of them is released, the meta state is removed
    //  this is not wanted, especially because the state of the other key is not affected (still looks pressed)
    private fun metaOnPressKey(primaryCode: Int) {
        val metaCode = primaryCode.toMetaState() ?: return
        if (primaryCode.isMetaLock()) {
            // if unset -> lock, otherwise set to UNSET_ON_RELEASE so it's unset on release
            if (metaPressStates[primaryCode] != MetaPressState.LOCKED) {
                metaPressStates[primaryCode] = MetaPressState.LOCKED
                keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, true)
                metaState = metaState or metaCode
            } else {
                metaPressStates[primaryCode] = MetaPressState.UNSET_ON_RELEASE
            }
            return
        }
        if (metaPressStates[primaryCode] == MetaPressState.RELEASED_BUT_ACTIVE) {
            // meta key is pressed again without other input -> should be disabled on release
            metaPressStates[primaryCode] = MetaPressState.UNSET_ON_RELEASE
        } else {
            // otherwise just press it normally
            metaPressStates[primaryCode] = MetaPressState.PRESSED
        }
        metaState = metaState or metaCode
        // pressed graphics are set anyway, no need to lock it
    }

    // looks like this is not called if there are no popups
    private fun metaOnLongPressKey(primaryCode: Int) {
        if (metaPressStates[primaryCode] != MetaPressState.PRESSED) return
        // we long-pressed a meta key that has popups -> disable so the meta state is not used for the popup
        metaPressStates[primaryCode] = MetaPressState.UNSET
        keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, false)
        val metaCode = primaryCode.toMetaState() ?: return
        metaState = metaState and metaCode.inv()
    }

    private fun metaOnReleaseKey(primaryCode: Int) {
        val metaCode = primaryCode.toMetaState() ?: return
        val metaPressState = metaPressStates[primaryCode]
        if (metaPressState == MetaPressState.UNSET_ON_RELEASE) {
            metaPressStates[primaryCode] = MetaPressState.UNSET
            metaState = metaState and metaCode.inv()
            keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, false)
        } else if (metaPressState == MetaPressState.PRESSED) {
            metaPressStates[primaryCode] = MetaPressState.RELEASED_BUT_ACTIVE
            keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, true)
        }
    }

    private fun metaAfterCodeInput(primaryCode: Int) {
        val metaCode = primaryCode.toMetaState()
        if (metaCode != null) {
            // meta key might be a popup key, we just toggle between set and unset
            val metaPressState = metaPressStates[primaryCode] ?: MetaPressState.UNSET
            if (metaPressState == MetaPressState.UNSET) {
                metaPressStates[primaryCode] = MetaPressState.SET
                metaState = metaState or metaCode
                keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, true)
            } else if (metaPressState == MetaPressState.SET) {
                metaPressStates[primaryCode] = MetaPressState.UNSET
                metaState = metaState and metaCode.inv()
                keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, false)
            }
        } else if (metaState != 0) {
            // non-meta key -> unset all set / released_but_active, and mark pressed as UNSET_ON_RELEASE
            metaPressStates.forEach { key, value ->
                if (value == MetaPressState.RELEASED_BUT_ACTIVE || value == MetaPressState.SET) {
                    metaPressStates[key] = MetaPressState.UNSET
                    keyboardSwitcher.mainKeyboardView?.updateLockState(key, false)
                    val metaCode = key.toMetaState() ?: return@forEach
                    metaState = metaState and metaCode.inv()
                } else if (value == MetaPressState.PRESSED) {
                    metaPressStates[key] = MetaPressState.UNSET_ON_RELEASE
                }
            }
        }
    }

    companion object {
        private enum class MetaPressState {
            UNSET, // default state, not active
            SET, // enabled without onPressKey (e.g. in popup)
            PRESSED, // key is pressed
            UNSET_ON_RELEASE, // key is pressed, but state will be unset on release
            RELEASED_BUT_ACTIVE, // key was released without UNSET_ON_RELEASE state, meta state is still set
            LOCKED, // key is locked and will be released only by pressing the same key again
        }

        private fun Int.toMetaState() = when (this) {
            KeyCode.CTRL, KeyCode.CTRL_LOCK -> KeyEvent.META_CTRL_ON
            KeyCode.CTRL_LEFT               -> KeyEvent.META_CTRL_LEFT_ON
            KeyCode.CTRL_RIGHT              -> KeyEvent.META_CTRL_RIGHT_ON
            KeyCode.ALT, KeyCode.ALT_LOCK   -> KeyEvent.META_ALT_ON
            KeyCode.ALT_LEFT                -> KeyEvent.META_ALT_LEFT_ON
            KeyCode.ALT_RIGHT               -> KeyEvent.META_ALT_RIGHT_ON
            KeyCode.FN, KeyCode.FN_LOCK     -> KeyEvent.META_FUNCTION_ON
            KeyCode.META, KeyCode.META_LOCK -> KeyEvent.META_META_ON
            KeyCode.META_LEFT               -> KeyEvent.META_META_LEFT_ON
            KeyCode.META_RIGHT              -> KeyEvent.META_META_RIGHT_ON
            else -> null
        }

        private fun Int.isMetaLock() = this == KeyCode.CTRL_LOCK || this == KeyCode.ALT_LOCK || this == KeyCode.FN_LOCK || this == KeyCode.META_LOCK
    }
}
