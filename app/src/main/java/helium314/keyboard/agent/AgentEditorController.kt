// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.inputmethod.InputConnection
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import dev.agentkeyboard.core.ApplyOutcome
import dev.agentkeyboard.core.AnchorResult
import dev.agentkeyboard.core.CaptureResult
import dev.agentkeyboard.core.DenialReason
import dev.agentkeyboard.core.LocalTextAction
import dev.agentkeyboard.core.TextSnapshot
import dev.agentkeyboard.core.ModelProvider
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.TranslationLanguage
import dev.agentkeyboard.core.WritingTone
import dev.agentkeyboard.core.WorkflowDefinition
import dev.agentkeyboard.core.WorkflowStep
import dev.agentkeyboard.core.WorkflowProvider
import dev.agentkeyboard.core.WorkflowFailure
import dev.agentkeyboard.core.WorkflowError
import dev.agentkeyboard.core.BuiltinAction
import dev.agentkeyboard.core.ToolId
import dev.agentkeyboard.core.ToolRequest
import dev.agentkeyboard.core.Tools
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.compat.isUserLocked
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.agent.providers.*
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.commands.CommandAiRequest
import kotlinx.coroutines.*

/** Owns only the IME's action session; it never owns the host application's text field. */
class AgentEditorController @JvmOverloads constructor(
    private val ime: LatinIME,
    private val inputLogic: InputLogic,
    private val journalFactory: (() -> ActionRunJournal)? = null,
    private val providerFactory: (() -> ModelProvider)? = null,
) {
    /** Replaceable in tests; production hands over to the user's calendar app. */
    internal var toolLauncher: ToolLauncher = CalendarToolLauncher(ime)
    private var panel: AgentPanelView? = null
    private var wheel: AgentWheelView? = null
    private var calculator: CalculatorView? = null
    private var charArt: helium314.keyboard.agent.charart.CharArtView? = null
    private var snapshot: TextSnapshot? = null
    private var preparedResult: String? = null
    private var needsCursorRefresh = false
    private var canApply = false
    private var skillJob: Job? = null
    private class CloudApproval(val provider: WorkflowProvider, val model: String, val action: AiTextAction, val language: TranslationLanguage, val tone: WritingTone)
    private var pendingApproval: CloudApproval? = null
    private var pendingWorkflow: WorkflowDefinition? = null
    private var pendingTool: ToolRequest? = null
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /**
     * The workflow chosen with a typed `@wf` while nothing was selected (stage 24f): the next time the
     * panel opens with selected text in this editor it prepares this workflow first. Kept when the
     * panel closes (the user closes it to select text), dropped when the editor starts or finishes,
     * when the panel is denied for a reason other than "nothing selected" (incognito, lock, password
     * field), and once it was offered. Only an id: the definition is read again from the store.
     */
    private var chosenWorkflowId: String? = null
    /** True while the panel serves a typed `@ki` question or `@translate` (stage 24f); the selection actions stay off. */
    private var asking = false
    /** What the typed request asks for while [asking]: [AiTextAction.ASK] or [AiTextAction.TRANSLATE_STYLED]. */
    private var questionAction = AiTextAction.ASK
    private val translating: Boolean get() = questionAction == AiTextAction.TRANSLATE_STYLED
    /** The typed `@ki` command that "Insert" replaces, and where it ended. */
    private var questionCommand: Pair<String, Int>? = null
    /** Replaceable in tests: whether a key is stored for the provider. Reads no key, only whether one exists. */
    internal var credentialCheck: ((WorkflowProvider) -> Boolean)? = null
    private val cloudProviders by lazy { CloudProviders(ime) }
    private val injectedProvider: ModelProvider? by lazy { providerFactory?.invoke() }
    private val cloudRunner by lazy { ApprovedActionRunner(actionScope, { injectedProvider ?: cloudProviders.get(it) }, editor::isCurrent,
        journalFactory?.invoke() ?: EncryptedActionRunStore(ime)) }
    val isAwaitingCursorRefresh: Boolean get() = needsCursorRefresh
    val isBlockingInput: Boolean get() = needsCursorRefresh || panel?.visibility == View.VISIBLE || wheel?.visibility == View.VISIBLE
        || calculator?.visibility == View.VISIBLE || charArt?.visibility == View.VISIBLE
    val editor = AndroidEditorAdapter(
        connection = { ime.currentInputConnection },
        liveAccess = { access -> access.copy(
            isIncognito = ime.prefs().getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE),
            isLocked = isDeviceLocked(ime) || isUserLocked(ime),
            isComposing = inputLogic.hasActiveComposition(),
        ) },
        commitSelection = { source, result ->
            inputLogic.applyGeneratedText(source.text, result, source.selectionStart, source.selectionEnd).also {
                if (it != ApplyOutcome.REJECTED) ime.onAgentTextSubmitted()
            }
        },
    )

    /**
     * The typed `@ki`/`@translate` request sent from the strip (Variante A), or null. Separate from the
     * panel's [snapshot]: the panel is closed while it runs, and opening the panel drops it.
     */
    private var commandRequest: CommandAiRequest? = null
    /** The question snapshot [commandRequest] runs with; never shown or stored. */
    private var commandSource: TextSnapshot? = null

    init {
        KeyboardCommands.aiRequests = java.lang.ref.WeakReference(this)
        editor.commitInsertion = { target, text ->
            inputLogic.insertGeneratedText(text, target.position).also {
                if (it != ApplyOutcome.REJECTED) ime.onAgentTextSubmitted()
            }
        }
    }

    /** Replaceable in tests. Production only ever uses the system's on-device recognizer. */
    internal var speechEngine: SpeechEngine? = null
    private val deviceSpeechEngine by lazy { OnDeviceSpeechEngine(ime) }
    private val dictation = DictationSession(
        engine = { speechEngine ?: deviceSpeechEngine },
        captureAnchor = { editor.captureAnchor() },
        isCurrent = { editor.isCurrent(it) },
        insertAt = { target, text -> editor.insert(target, text) },
        microphoneGranted = {
            androidx.core.content.ContextCompat.checkSelfPermission(ime, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        },
        languageTag = { Settings.getValues().mLocale.toLanguageTag() },
        ui = object : DictationSession.Ui {
            override fun listening(partial: String) { panel?.showDictationListening(partial) }
            override fun processing() { panel?.showDictationProcessing() }
            override fun review(text: String) { panel?.showDictationReview(text) }
            override fun failed(error: DictationError) { panel?.showDictationStatus(error.messageResource()) }
            override fun denied(reason: DenialReason) { panel?.showDictationStatus(reason.dictationMessageResource()) }
        },
    )

    /**
     * Opens the panel in dictation mode. Needs a plain cursor in an editor that allows processing;
     * the microphone starts only after that check and only with the on-device recognizer.
     */
    fun openDictation() {
        val target = panel ?: return
        if (needsCursorRefresh) return
        close()
        KeyboardSwitcher.getInstance().mainKeyboardView?.closing()
        target.rootView.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)?.dismissMoreSuggestionsPanel()
        editor.onInvalidated = { if (dictation.editorChanged()) panel?.showExpired() }
        dictation.start()
    }

    private fun insertDictation() {
        editor.onInvalidated = null
        when (dictation.insert()) {
            ApplyOutcome.SUBMITTED -> close()
            ApplyOutcome.REJECTED -> panel?.showExpired()
            ApplyOutcome.UNKNOWN -> {
                needsCursorRefresh = true
                panel?.showStatus(R.string.agent_panel_apply_unknown, true)
                runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
            }
        }
    }

    fun startEditor(info: EditorInfo) {
        close()
        Memory.onFieldChanged(ime) // stage 46: no run of words carries over to another field
        ForeignWords.onFieldChanged() // stage 51: another place for a word of another language
        chosenWorkflowId = null
        needsCursorRefresh = false
        editor.startEditor(info.inputType, info.imeOptions, info.initialSelStart, info.initialSelEnd)
    }
    fun invalidate() = editor.invalidate()
    /** Stage 46: the suggestions the engine holds for the strip now (the memory's chip row shows its predictions). */
    internal val suggestedWords: helium314.keyboard.latin.SuggestedWords get() = inputLogic.mSuggestedWords
    /** Stage 46, second review: whether a dictionary of the keyboard knows [word] as written (the case of a remembered expansion). */
    internal fun isKnownWord(word: String): Boolean = runCatching { inputLogic.mDictionaryFacilitator?.isValidSpellingWord(word) == true }.getOrDefault(false)
    fun finishEditor() {
        close()
        Memory.onFieldChanged(ime, finishing = true) // stage 46: and what was learned is written, at once (second review)
        ForeignWords.onFieldChanged()
        chosenWorkflowId = null
        needsCursorRefresh = false
        editor.finishEditor()
    }
    fun selectionChanged(start: Int, end: Int, compositionStart: Int, compositionEnd: Int) {
        editor.selectionChanged(start, end, compositionStart, compositionEnd)
        // typed, moved or selected: a request from the strip ends, a late answer is never shown (P2)
        commandRequest?.let { if (start != end || start != it.commandEnd) dropCommandRequest() }
        if (needsCursorRefresh && start >= 0 && end >= 0 && inputLogic.refreshAgentEditorCaches(start, end))
            needsCursorRefresh = false
    }

    fun attach(view: View) {
        close()
        panel = view.findViewById(R.id.agent_panel)
        wheel = view.findViewById(R.id.agent_wheel)
        wheel?.onClose = ::close
        calculator = view.findViewById(R.id.agent_calculator)
        calculator?.onClose = ::close
        calculator?.onInsert = ::insertCalculation
        charArt = view.findViewById(R.id.agent_char_art)
        charArt?.onClose = ::close
        charArt?.onInsert = ::insertCharArt
        charArt?.onReplace = ::replaceWithCharArt
        charArt?.onUndo = ::undoCharArt
        charArt?.onCopy = ::copyCharArt
        charArt?.onSaveSnippet = ::saveCharArtSnippet
        charArt?.onAllowPictures = ::openPictureSettings
        wheel?.onAction = { action -> if (action == null) open() else { capture(); runLocal(action) } }
        panel?.onClose = {
            val hideKeyboard = needsCursorRefresh
            close()
            if (hideKeyboard) ime.requestHideSelf(0)
        }
        panel?.onAction = ::runLocal
        panel?.onApply = ::applyPrepared
        panel?.onCopy = ::copyPrepared
        panel?.onPrepareCloud = ::prepareCloud
        panel?.onSendCloud = ::sendCloud
        panel?.onCancelCloud = ::cancelCloud
        panel?.onWorkflows = ::chooseWorkflows
        panel?.onWorkflow = ::prepareWorkflow
        panel?.onTool = ::prepareTool
        panel?.onDictate = ::openDictation
        panel?.onSettings = {
            close()
            openAiSettings()
        }
    }

    fun openWheel(): Boolean {
        val target = wheel ?: return false
        if (needsCursorRefresh) return false
        close()
        KeyboardSwitcher.getInstance().mainKeyboardView?.closing()
        target.rootView.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)?.dismissMoreSuggestionsPanel()
        target.visibility = View.VISIBLE
        target.announceForAccessibility(ime.getString(R.string.agent_wheel_title))
        return true
    }

    fun openSkill(slot: Int) {
        if (slot !in 1..3) return
        capture()
        val source = snapshot ?: return
        panel?.showStatus(R.string.agent_skill_loading, true)
        skillJob = actionScope.launch {
            val (binding, workflow) = withContext(Dispatchers.IO) {
                val binding = SkillBindingStore(ime).forTyping(slot)
                val workflow = if (binding.action == BuiltinAction.WORKFLOW) runCatching {
                    WorkflowStore(ime).read().singleOrNull { it.id == binding.workflowId }
                }.getOrNull() else null
                binding to workflow
            }
            if (snapshot !== source) return@launch
            if (!editor.isCurrent(source)) { discardStaleContext(); return@launch }
            skillJob = null
            panel?.showSource(source.text)
            when {
                binding.action == BuiltinAction.WORKFLOW -> if (workflow != null) prepareWorkflow(workflow)
                    else panel?.showStatus(R.string.agent_skill_workflow_missing)
                binding.action.local != null -> runLocal(binding.action.local!!)
                binding.action.ai != null -> prepareCloud(binding.action.ai!!)
                else -> panel?.showStatus(R.string.agent_skill_unavailable)
            }
        }
    }

    /**
     * Stage 24f `@wf`: opens the panel and prepares the saved workflow [workflowId] the way a skill
     * key bound to it does; the run still needs the approval in the panel and the field is not
     * changed. A typed command means nothing is selected: then the panel only says to select the text
     * now, and the workflow is remembered ([chosenWorkflowId]) until the panel is opened again with
     * selected text. False if there is no panel now or input waits for the editor (P3).
     */
    fun openWorkflow(workflowId: String): Boolean {
        if (panel == null || needsCursorRefresh) return false
        val denied = capture()
        val source = snapshot
        if (source == null) {
            if (denied in WAITS_FOR_SELECTION) {
                chosenWorkflowId = workflowId
                panel?.showStatus(R.string.agent_command_wf_pending, true)
            } else chosenWorkflowId = null
            return true
        }
        chosenWorkflowId = null
        prepareStoredWorkflow(source, workflowId)
        return true
    }

    /** Reads the workflow [workflowId] off the main thread and prepares it for [source] (approval in the panel). */
    private fun prepareStoredWorkflow(source: TextSnapshot, workflowId: String) {
        panel?.showStatus(R.string.agent_workflow_loading, true)
        skillJob = actionScope.launch {
            val workflow = withContext(Dispatchers.IO) {
                runCatching { WorkflowStore(ime).read().singleOrNull { it.id == workflowId } }.getOrNull()
            }
            if (snapshot !== source) return@launch
            if (!editor.isCurrent(source)) { discardStaleContext(); return@launch }
            skillJob = null
            panel?.showSource(source.text)
            if (workflow != null) prepareWorkflow(workflow) else panel?.showStatus(R.string.agent_skill_workflow_missing)
        }
    }

    /** An edit outside the panel (a typed command) ended unclear: block input until the editor reports (P3). */
    fun onUnclearEdit() {
        needsCursorRefresh = true
        Toast.makeText(ime, R.string.agent_panel_apply_unknown, Toast.LENGTH_LONG).show()
        runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
    }

    /** Whether [openCalculator] would open now. */
    val canOpenCalculator: Boolean get() = calculator != null && !needsCursorRefresh

    /** The typed command the calculator replaces on "Insert", and where it ended. */
    private var calculatorCommand: Pair<String, Int>? = null

    /**
     * Stage 24b: the calculator of `@math` in place of the keys, starting with [initial]. The field
     * stays as it is until "Insert": an app may restart the input when its text changes (e.g. when
     * it becomes empty), and a restart closes every overlay. [commandText] ending at [commandEnd]
     * is then replaced by the result in one commit.
     */
    fun openCalculator(initial: String, commandText: String? = null, commandEnd: Int = -1): Boolean {
        val target = calculator ?: return false
        if (needsCursorRefresh) return false
        close()
        calculatorCommand = if (commandText.isNullOrEmpty() || commandEnd < 0) null else commandText to commandEnd
        KeyboardSwitcher.getInstance().mainKeyboardView?.closing()
        target.rootView.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)?.dismissMoreSuggestionsPanel()
        target.show(initial, Settings.getValues().mLocale.language)
        return true
    }

    /** The tap on "Insert": at the cursor as it is now, through the same checks as the dictation. */
    private fun insertCalculation(text: String) {
        calculatorCommand?.let { (command, end) ->
            when (inputLogic.replaceCommandText(command, text, end)) {
                ApplyOutcome.SUBMITTED -> { close(); return }
                ApplyOutcome.UNKNOWN -> { close(); onUnclearEdit(); return }
                // the command is gone (deleted or changed elsewhere): insert at the cursor instead
                ApplyOutcome.REJECTED -> calculatorCommand = null
            }
        }
        val captured = editor.captureAnchor()
        if (captured !is AnchorResult.Captured) {
            Toast.makeText(ime, R.string.agent_calculator_expired, Toast.LENGTH_SHORT).show()
            return
        }
        when (editor.insert(captured.anchor, text)) {
            ApplyOutcome.SUBMITTED -> close()
            ApplyOutcome.REJECTED -> Toast.makeText(ime, R.string.agent_calculator_expired, Toast.LENGTH_SHORT).show()
            ApplyOutcome.UNKNOWN -> {
                needsCursorRefresh = true
                close()
                Toast.makeText(ime, R.string.agent_panel_apply_unknown, Toast.LENGTH_LONG).show()
                runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
            }
        }
    }

    /** Stage 53: the whole field text before the cursor when the window opened (nothing after it), or null. */
    private var charArtField: String? = null
    /** The selection read when the window opened, which "Replace selection" replaces (review of stage 53, N8), or null. */
    private var charArtSelection: TextSnapshot? = null
    /** Whether "Replace" would replace [charArtField] now: not after an insertion until it is undone. */
    private var charArtFieldInPlace = false
    /** The last insertion of the window: the art in the field and what it replaced ("" for an insertion). */
    private var charArtUndo: Pair<String, String>? = null
    /** Where the cursor was right after that insertion; an undo needs it there. */
    private var charArtUndoEnd = -1
    /** Saves snippets off the main thread; replaceable in tests. */
    internal var charArtWorker: (Runnable) -> Unit = { Thread(it, "char-art-snippet").start() }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Stage 53: the character art window in place of the keys, opened by the pen in the emoji key's
     * popup. Never in password fields, incognito, fields without learning or while the device is
     * locked; there nothing of the field is read. Otherwise a word still being composed is left as
     * typed (as when the settings open), and up to [CharArtView.MAX_TEXT] characters around the
     * cursor (or the selection) become the window's text. The field changes only by a tap in the
     * window. False if the window cannot open now.
     */
    fun openCharArt(): Boolean {
        val target = charArt ?: return false
        if (needsCursorRefresh) return false
        if (editor.protection() != null) {
            Toast.makeText(ime, R.string.agent_char_art_protected, Toast.LENGTH_SHORT).show()
            return false
        }
        if (inputLogic.hasActiveComposition()) {
            if (inputLogic.hasPendingBatchInput()) return false // a gesture is still being processed
            inputLogic.commitTyped(Settings.getValues(), helium314.keyboard.latin.LastComposedWord.NOT_A_SEPARATOR)
        }
        close()
        KeyboardSwitcher.getInstance().mainKeyboardView?.closing()
        target.rootView.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)?.dismissMoreSuggestionsPanel()
        val limit = helium314.keyboard.agent.charart.CharArtView.MAX_TEXT
        val connection = ime.currentInputConnection
        var text = ""
        var whole = false
        charArtSelection = null
        if (inputLogic.mConnection.hasSelection()) {
            // through the capture of the panel (review of stage 53, N8): the selection is read only
            // up to the policy's limit, and the snapshot is what "Replace selection" may replace
            when (val captured = editor.capture()) {
                is CaptureResult.Captured -> {
                    text = captured.snapshot.text.take(limit)
                    // replace only what the window shows: a longer selection is only read in part
                    if (captured.snapshot.text.length <= limit && captured.snapshot.text.isNotBlank()) charArtSelection = captured.snapshot
                }
                is CaptureResult.Denied -> if (captured.reason == DenialReason.TOO_LARGE)
                    Toast.makeText(ime, R.string.agent_char_art_selection_too_long, Toast.LENGTH_SHORT).show()
            }
        } else {
            val before = runCatching { connection?.getTextBeforeCursor(limit + 1, 0)?.toString() }.getOrNull().orEmpty()
            val after = runCatching { connection?.getTextAfterCursor(limit, 0)?.toString() }.getOrNull().orEmpty()
            // the whole field before the cursor, and the cursor where the keyboard expects it (N2)
            whole = after.isEmpty() && before.isNotBlank() && before.length <= limit &&
                inputLogic.mConnection.expectedSelectionStart == before.length
            text = (before.takeLast(limit) + after).take(limit)
        }
        charArtField = if (whole) text else null
        charArtFieldInPlace = whole
        val app = dev.agentkeyboard.core.charart.TargetApp.forPackage(ime.currentInputEditorInfo?.packageName)
        val selection = charArtSelection != null
        target.show(text, whole || selection, app, Settings.getValues()?.mLocale?.language.orEmpty(), selection)
        return true
    }

    /** Art longer than the editor accepts in one commit: said as that, not as "the field changed" (N7). */
    private fun charArtTooLarge(art: String): Boolean {
        if (art.length <= editor.policy.maxSelectionChars) return false
        Toast.makeText(ime, ime.getString(R.string.agent_char_art_too_large, editor.policy.maxSelectionChars), Toast.LENGTH_LONG).show()
        return true
    }

    /** The tap on "Insert": once, at the cursor as it is now, through the same checks as the calculator. */
    private fun insertCharArt(art: String) {
        if (charArtTooLarge(art)) return
        when (val captured = editor.captureAnchor()) {
            is AnchorResult.Captured -> when (editor.insert(captured.anchor, art)) {
                ApplyOutcome.SUBMITTED -> charArtInserted(art, "")
                ApplyOutcome.REJECTED -> Toast.makeText(ime, R.string.agent_char_art_expired, Toast.LENGTH_SHORT).show()
                ApplyOutcome.UNKNOWN -> charArtUnclear()
            }
            is AnchorResult.Denied -> Toast.makeText(ime, when (captured.reason) {
                DenialReason.NO_CURSOR -> R.string.agent_char_art_selection
                DenialReason.PASSWORD, DenialReason.INCOGNITO, DenialReason.NO_LEARNING, DenialReason.LOCKED -> R.string.agent_char_art_protected
                else -> R.string.agent_char_art_expired
            }, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The tap on "Replace": the field text read at opening, if it is still exactly that with the
     * cursor right after it and nothing behind it (review of stage 53, N2), becomes the art in one
     * commit; with a selection, the selection read at opening ("Replace selection", N8).
     */
    private fun replaceWithCharArt(art: String) {
        charArtSelection?.let { replaceSelectionWithCharArt(it, art); return }
        val original = charArtField?.takeIf { charArtFieldInPlace } ?: return
        if (editor.protection() != null) {
            Toast.makeText(ime, R.string.agent_char_art_protected, Toast.LENGTH_SHORT).show()
            return
        }
        if (charArtTooLarge(art)) return
        val position = inputLogic.mConnection.expectedSelectionStart
        val after = runCatching { ime.currentInputConnection?.getTextAfterCursor(1, 0) }.getOrNull()
        val outcome = if (position != original.length || after == null || after.isNotEmpty()) ApplyOutcome.REJECTED
            else inputLogic.replaceCommandText(original, art, position, true)
        if (outcome != ApplyOutcome.REJECTED) ime.onAgentTextSubmitted() // N3, like the other paths
        when (outcome) {
            ApplyOutcome.SUBMITTED -> charArtInserted(art, original)
            ApplyOutcome.REJECTED -> {
                charArtFieldInPlace = false
                charArt?.setReplaceable(false)
                Toast.makeText(ime, R.string.agent_char_art_expired, Toast.LENGTH_SHORT).show()
            }
            ApplyOutcome.UNKNOWN -> charArtUnclear()
        }
    }

    /** "Replace selection": the selection read at opening, while it is unchanged, becomes the art (generated text, one commit). */
    private fun replaceSelectionWithCharArt(selection: TextSnapshot, art: String) {
        if (editor.protection() != null) {
            Toast.makeText(ime, R.string.agent_char_art_protected, Toast.LENGTH_SHORT).show()
            return
        }
        if (charArtTooLarge(art)) return
        // one tap, one attempt: the snapshot is used up either way
        charArtSelection = null
        // the editor adapter checks the selection again and calls onAgentTextSubmitted (commitSelection)
        when (editor.apply(selection, art)) {
            ApplyOutcome.SUBMITTED -> charArtInserted(art, selection.text)
            ApplyOutcome.REJECTED -> {
                charArt?.setReplaceable(false)
                Toast.makeText(ime, R.string.agent_char_art_expired, Toast.LENGTH_SHORT).show()
            }
            ApplyOutcome.UNKNOWN -> charArtUnclear()
        }
    }

    private fun charArtInserted(art: String, replaced: String) {
        charArtUndo = art to replaced
        charArtUndoEnd = inputLogic.mConnection.expectedSelectionStart
        charArtFieldInPlace = false
        charArt?.setReplaceable(false)
        charArt?.offerUndo()
    }

    /** The tap on "Undo": the art goes again, only while it is unchanged right before the same cursor. */
    private fun undoCharArt() {
        val (art, replaced) = charArtUndo ?: return
        charArtUndo = null
        val position = inputLogic.mConnection.expectedSelectionStart
        if (editor.protection() != null || position != charArtUndoEnd) {
            Toast.makeText(ime, R.string.agent_char_art_undo_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val outcome = inputLogic.replaceCommandText(art, replaced, position)
        if (outcome != ApplyOutcome.REJECTED) ime.onAgentTextSubmitted() // N3, like the other paths
        when (outcome) {
            ApplyOutcome.SUBMITTED -> {
                Toast.makeText(ime, R.string.agent_char_art_undone, Toast.LENGTH_SHORT).show()
                // the field is as it was when the window opened: "Replace" works again
                if (charArtField != null) { charArtFieldInPlace = true; charArt?.setReplaceable(true) }
            }
            ApplyOutcome.REJECTED -> Toast.makeText(ime, R.string.agent_char_art_undo_failed, Toast.LENGTH_SHORT).show()
            ApplyOutcome.UNKNOWN -> charArtUnclear()
        }
    }

    private fun charArtUnclear() {
        needsCursorRefresh = true
        close()
        Toast.makeText(ime, R.string.agent_panel_apply_unknown, Toast.LENGTH_LONG).show()
        runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
    }

    /** Character art is the user's own picture, not a secret: copied like normal text, so the clipboard view shows it. */
    private fun copyCharArt(art: String) {
        if (editor.protection() != null) return
        (ime.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText(ime.getString(R.string.agent_char_art_title), art))
        Toast.makeText(ime, R.string.agent_char_art_copied, Toast.LENGTH_SHORT).show()
    }

    /** Saves the art as a snippet ([name] letters only), inserted as it is (`literal`), off the main thread. */
    private fun saveCharArtSnippet(name: String, art: String) {
        // like copying and inserting: nothing is saved once the field forbids it (locked, incognito …), N10
        if (editor.protection() != null) {
            Toast.makeText(ime, R.string.agent_char_art_protected, Toast.LENGTH_SHORT).show()
            return
        }
        val store = helium314.keyboard.agent.commands.SnippetStore
        if (art.length > store.MAX_TEXT) {
            Toast.makeText(ime, ime.getString(R.string.agent_char_art_snippet_too_long, store.MAX_TEXT), Toast.LENGTH_SHORT).show()
            return
        }
        if (!store.isValidName(name) || !store.isValidText(art)) return
        val context = ime.applicationContext
        charArtWorker(Runnable {
            val message = runCatching {
                val snippets = helium314.keyboard.agent.commands.SnippetStore(context)
                if (snippets.read().any { it.name.equals(name, ignoreCase = true) }) R.string.agent_char_art_snippet_taken
                else {
                    snippets.put(helium314.keyboard.agent.commands.Snippet(name, art, literal = true))
                    R.string.agent_char_art_snippet_saved
                }
            }.getOrDefault(R.string.agent_char_art_snippet_failed)
            mainHandler.post { Toast.makeText(ime, ime.getString(message, name), Toast.LENGTH_SHORT).show() }
        })
    }

    /** The app's system page, where the user may allow pictures; the window closes when the keyboard hides. */
    private fun openPictureSettings() {
        close()
        runCatching {
            ime.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", ime.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Stage 24f `@ki`: opens the panel with a send preview of exactly [question] and the provider
     * and model from the AI settings. Nothing is sent before "Send", nothing from the field is part
     * of the request, and the field stays unchanged until "Insert" replaces [commandText] ending at
     * [commandEnd] by the answer (or inserts it at the cursor once the command is gone). False if
     * there is no panel now. `@translate` comes the same way with [action]
     * [AiTextAction.TRANSLATE_STYLED] and its whole request as [question]; no other action can.
     */
    fun openQuestion(question: String, commandText: String, commandEnd: Int, action: AiTextAction = AiTextAction.ASK): Boolean {
        if (action != AiTextAction.ASK && action != AiTextAction.TRANSLATE_STYLED) return false
        val target = panel ?: return false
        if (needsCursorRefresh) return false
        close()
        KeyboardSwitcher.getInstance().mainKeyboardView?.closing()
        target.rootView.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)?.dismissMoreSuggestionsPanel()
        asking = true
        questionAction = action
        questionCommand = if (commandText.isEmpty() || commandEnd < 0) null else commandText to commandEnd
        when (val captured = editor.captureQuestion(question)) {
            is CaptureResult.Captured -> {
                snapshot = captured.snapshot
                target.showQuestion(captured.snapshot.text, translating)
                editor.onInvalidated = {
                    val source = snapshot
                    if (source != null && !editor.isCurrent(source)) discardStaleContext()
                }
                prepareQuestion()
            }
            is CaptureResult.Denied -> target.showStatus(captured.reason.questionMessageResource(translating), true)
        }
        return true
    }

    private fun hasCredential(provider: WorkflowProvider): Boolean = credentialCheck?.invoke(provider)
        ?: (providerFactory != null || runCatching { EncryptedCredentialStore(ime).contains(CloudProviders.slot(provider)) }.getOrDefault(false))

    /** The send preview of the question; the approval is consumed by the next "Send" like every cloud action's. */
    private fun prepareQuestion() {
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        if (cloudRunner.isRunning) return
        val approval = runCatching {
            val settings = ModelSettings(ime)
            val provider = settings.provider
            CloudApproval(provider, settings.model(provider), questionAction, settings.translationLanguage, settings.writingTone)
        }.getOrNull()
        if (approval == null || !hasCredential(approval.provider)) {
            panel?.showStatus(if (translating) R.string.agent_command_translate_credentials else R.string.agent_command_ki_credentials, true)
            return
        }
        if (!CloudProviders.validModel(approval.provider, approval.model)) {
            panel?.showStatus(if (approval.model.isEmpty()) R.string.agent_cloud_model_missing else R.string.agent_cloud_invalid_request, true)
            return
        }
        preparedResult = null
        canApply = false
        pendingApproval = approval
        panel?.showQuestionConfirmation(approval.model, source.text.length, CloudProviders.displayName(approval.provider), translating)
    }

    /** Display name of the provider a typed request would go to now, or null if the AI settings cannot be read. */
    val commandProviderName: String?
        get() = runCatching { CloudProviders.displayName(ModelSettings(ime).provider) }.getOrNull()

    /**
     * The request sent from the strip for exactly [commandText] ending at [commandEnd], or null. A
     * request for any other command or cursor is cancelled and dropped here: the chips are being
     * built for a changed field, so its answer must never show up (P2).
     */
    fun commandRequest(commandText: String, commandEnd: Int): CommandAiRequest? {
        val current = commandRequest ?: return null
        // a running or answered request also ends when the editor is no longer the same or turned
        // protected (incognito, lock, password)
        val source = commandSource
        if (current.matches(commandText, commandEnd) && (source == null || editor.isCurrent(source))) return current
        dropCommandRequest()
        return null
    }

    /**
     * The tap on the send chip of a typed `@ki` or `@translate` (Variante A). The tap is the one-time
     * approval of exactly [request], the text the chip names and the field shows: the approval is
     * created here and consumed by starting one run through the same [cloudRunner] (journal, budget,
     * generation, cancellation) as the panel's "Send". Nothing of the field is changed or sent. The
     * returned request is running, or failed with the reason nothing was sent; [onChange] rebuilds the
     * chips when the answer or a failure arrives. Null for an action a typed command cannot ask for.
     */
    fun sendCommandRequest(request: String, commandText: String, commandEnd: Int, action: AiTextAction,
                           onChange: () -> Unit): CommandAiRequest? {
        if (action != AiTextAction.ASK && action != AiTextAction.TRANSLATE_STYLED) return null
        if (commandText.isEmpty() || commandEnd < 0) return null
        dropCommandRequest()
        // one run at a time: an open panel, wheel or calculator (and its run) is closed by this tap
        if (!needsCursorRefresh && (isBlockingInput || cloudRunner.isRunning)) close()
        val entry = CommandAiRequest(commandText, commandEnd, action, request)
        entry.onChange = onChange
        commandRequest = entry
        val translation = action == AiTextAction.TRANSLATE_STYLED
        fun fail(chip: Int, details: Int, needsSettings: Boolean = false): CommandAiRequest {
            entry.state = CommandAiRequest.Failed(chip, details, needsSettings)
            return entry
        }
        if (needsCursorRefresh) return fail(R.string.agent_command_ai_not_sent, R.string.agent_panel_apply_unknown)
        val source = when (val captured = editor.captureQuestion(request)) {
            is CaptureResult.Captured -> captured.snapshot
            is CaptureResult.Denied -> return fail(R.string.agent_command_ai_not_sent, captured.reason.questionMessageResource(translation))
        }
        val approval = runCatching {
            val settings = ModelSettings(ime)
            val provider = settings.provider
            CloudApproval(provider, settings.model(provider), action, settings.translationLanguage, settings.writingTone)
        }.getOrNull()
        if (approval == null || !hasCredential(approval.provider))
            return fail(R.string.agent_command_ai_no_key,
                if (translation) R.string.agent_command_translate_credentials else R.string.agent_command_ki_credentials, true)
        if (!CloudProviders.validModel(approval.provider, approval.model))
            return fail(R.string.agent_command_ai_no_model,
                if (approval.model.isEmpty()) R.string.agent_cloud_model_missing else R.string.agent_cloud_invalid_request, true)
        commandSource = source
        // the approval is used exactly once, right here; a second run needs a new tap
        val started = cloudRunner.start(source, approval.model, approval.action.instruction(approval.language, approval.tone),
                approval.action, approval.provider) { outcome ->
            if (commandRequest !== entry) return@start // dropped meanwhile: never shown (P2)
            outcome.onSuccess { result ->
                entry.state = if (result.isNotBlank() && editor.policy.resultDenial(result) == null) CommandAiRequest.Answer(result)
                    else CommandAiRequest.Failed(R.string.agent_command_ai_unusable, R.string.agent_panel_result_invalid)
            }.onFailure { failure ->
                val error = (failure as? CloudFailure)?.error
                entry.state = CommandAiRequest.Failed(error.commandChip(), error.cloudMessage(),
                    error == CloudError.CREDENTIALS || error == CloudError.AUTHENTICATION)
            }
            entry.onChange?.invoke()
        }
        if (!started && commandRequest === entry) {
            commandSource = null
            return fail(R.string.agent_command_ai_not_sent, R.string.agent_panel_stale)
        }
        return entry
    }

    /** Cancels the strip's request of exactly [commandText] at [commandEnd] (or discards its answer) and shows the send chip again. */
    fun dropCommandRequest(commandText: String, commandEnd: Int) {
        val current = commandRequest ?: return
        if (!current.matches(commandText, commandEnd)) return
        dropCommandRequest()
        current.onChange?.invoke()
    }

    /** Cancels a request from the strip, if any, and forgets it and its answer. */
    fun dropCommandRequest() {
        val current = commandRequest ?: return
        commandRequest = null
        commandSource = null
        if (current.state == CommandAiRequest.Running) cloudRunner.cancel()
    }

    /**
     * The tap on the answer chip: replaces exactly the typed command by the answer in one commit
     * (generated text, so learning stops as after other AI results), or inserts it at the cursor when
     * the command is no longer directly before it. Nothing is inserted without this tap.
     */
    fun insertCommandAnswer(commandText: String, commandEnd: Int): Boolean {
        val current = commandRequest ?: return false
        val answer = (current.state as? CommandAiRequest.Answer)?.text ?: return false
        if (!current.matches(commandText, commandEnd) || needsCursorRefresh) return false
        val source = commandSource
        if (source == null || !editor.isCurrent(source)) { dropCommandRequest(); current.onChange?.invoke(); return false }
        // one tap, one attempt: forgotten before the field changes
        commandRequest = null
        commandSource = null
        val replaced = inputLogic.replaceCommandText(commandText, answer, commandEnd, true)
        if (replaced != ApplyOutcome.REJECTED) ime.onAgentTextSubmitted()
        when (replaced) {
            ApplyOutcome.SUBMITTED -> return true
            ApplyOutcome.UNKNOWN -> { onUnclearEdit(); return false }
            ApplyOutcome.REJECTED -> {} // the command is gone (changed elsewhere): insert at the cursor instead
        }
        val captured = editor.captureAnchor()
        val inserted = if (captured is AnchorResult.Captured) editor.insert(captured.anchor, answer) else ApplyOutcome.REJECTED
        when (inserted) {
            ApplyOutcome.SUBMITTED -> return true
            ApplyOutcome.UNKNOWN -> {
                needsCursorRefresh = true
                Toast.makeText(ime, R.string.agent_panel_apply_unknown, Toast.LENGTH_LONG).show()
                runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
            }
            ApplyOutcome.REJECTED -> Toast.makeText(ime, if (current.action == AiTextAction.TRANSLATE_STYLED)
                R.string.agent_command_ai_translation_expired else R.string.agent_command_ai_answer_expired, Toast.LENGTH_SHORT).show()
        }
        current.onChange?.invoke()
        return false
    }

    /**
     * The tap on a failed request: a missing key or model opens the AI settings; anything else opens
     * the panel with the send preview of the same request and the reason it failed. Sending from
     * there needs its own tap on "Send" again.
     */
    fun openCommandRequestDetails(commandText: String, commandEnd: Int): Boolean {
        val current = commandRequest ?: return false
        val failed = current.state as? CommandAiRequest.Failed ?: return false
        if (!current.matches(commandText, commandEnd)) return false
        if (failed.needsSettings) {
            dropCommandRequest()
            openAiSettings()
            return true
        }
        if (!openQuestion(current.request, commandText, commandEnd, current.action)) return false
        if (pendingApproval != null) panel?.showQuestionNote(failed.details)
        return true
    }

    private fun openAiSettings() {
        ime.startActivity(android.content.Intent(ime, helium314.keyboard.settings.SettingsActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AgentPreferences.OPEN_AI_SETTINGS, true))
    }

    /** The tap on "Insert": replaces the typed command in one commit, else inserts at the cursor. */
    private fun insertAnswer() {
        val source = snapshot ?: return
        val answer = preparedResult ?: return
        if (!canApply) return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        canApply = false // one tap, one attempt
        editor.onInvalidated = null
        questionCommand?.let { (command, end) ->
            // generated text: stops learning for the rest of the session like an applied AI result
            val outcome = inputLogic.replaceCommandText(command, answer, end, true)
            if (outcome != ApplyOutcome.REJECTED) ime.onAgentTextSubmitted()
            when (outcome) {
                ApplyOutcome.SUBMITTED -> { close(); return }
                ApplyOutcome.UNKNOWN -> { close(); onUnclearEdit(); return }
                // the command is gone (deleted or changed elsewhere): insert at the cursor instead
                ApplyOutcome.REJECTED -> questionCommand = null
            }
        }
        val captured = editor.captureAnchor()
        val outcome = if (captured is AnchorResult.Captured) editor.insert(captured.anchor, answer) else ApplyOutcome.REJECTED
        when (outcome) {
            ApplyOutcome.SUBMITTED -> close()
            ApplyOutcome.REJECTED -> {
                // the answer stays readable and copyable; inserting needs a fresh question
                panel?.disableApply()
                Toast.makeText(ime, if (translating) R.string.agent_command_translate_insert_failed
                    else R.string.agent_command_ki_insert_failed, Toast.LENGTH_SHORT).show()
            }
            ApplyOutcome.UNKNOWN -> {
                needsCursorRefresh = true
                panel?.showStatus(R.string.agent_panel_apply_unknown, true)
                runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
            }
        }
    }

    /**
     * Opens the text actions (pen key, wheel). A workflow chosen with `@wf` comes first: with selected
     * text it is prepared like a skill key; without, the panel says the workflow waits for a selection.
     */
    fun open() {
        val denied = capture()
        val chosen = chosenWorkflowId ?: return
        val source = snapshot
        if (source == null) {
            if (panel != null && denied in WAITS_FOR_SELECTION) panel?.showStatus(R.string.agent_command_wf_pending, true)
            else chosenWorkflowId = null
            return
        }
        chosenWorkflowId = null
        prepareStoredWorkflow(source, chosen)
    }

    /** Opens the panel and captures the selection; returns why the capture was denied, or null. */
    private fun capture(): DenialReason? {
        val target = panel ?: return null
        close()
        KeyboardSwitcher.getInstance().mainKeyboardView?.closing()
        target.rootView.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)?.dismissMoreSuggestionsPanel()
        when (val captured = editor.capture()) {
            is CaptureResult.Captured -> {
                snapshot = captured.snapshot
                target.showSource(captured.snapshot.text)
                editor.onInvalidated = {
                    val source = snapshot
                    if (source != null && !editor.isCurrent(source)) {
                        discardStaleContext()
                    }
                }
            }
            is CaptureResult.Denied -> {
                target.showStatus(captured.reason.messageResource(), true)
                return captured.reason
            }
        }
        return null
    }

    private fun discardStaleContext() {
        dictation.cancel()
        skillJob?.cancel()
        skillJob = null
        cloudRunner.cancel()
        pendingApproval = null
        pendingWorkflow = null
        pendingTool = null
        preparedResult = null
        snapshot = null
        canApply = false
        panel?.showExpired()
    }

    private fun runLocal(action: LocalTextAction) {
        if (asking) return // nothing is selected: the question is no text of the field
        if (cloudRunner.isRunning || skillJob?.isActive == true) return
        pendingApproval = null
        pendingWorkflow = null
        pendingTool = null
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) {
            discardStaleContext()
            return
        }
        val result = action.transform(source.text)
        if (editor.policy.resultDenial(result) != null) {
            panel?.showStatus(R.string.agent_panel_result_invalid, true)
            return
        }
        preparedResult = result
        canApply = true
        panel?.showResult(result)
    }

    private fun prepareCloud(action: AiTextAction) {
        // a typed question or translation has its own preview (prepareQuestion)
        if (asking || action == AiTextAction.ASK || action == AiTextAction.TRANSLATE_STYLED) return
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        if (cloudRunner.isRunning || skillJob?.isActive == true) return
        pendingWorkflow = null
        pendingTool = null
        val approval = runCatching {
            val settings = ModelSettings(ime)
            val provider = settings.provider
            CloudApproval(provider, settings.model(provider), action, settings.translationLanguage, settings.writingTone)
        }.getOrNull()
        if (approval == null) {
            panel?.showStatus(R.string.agent_cloud_credentials, true)
            return
        }
        if (!CloudProviders.validModel(approval.provider, approval.model)) {
            panel?.showStatus(if (approval.model.isEmpty()) R.string.agent_cloud_model_missing else R.string.agent_cloud_invalid_request, true)
            return
        }
        preparedResult = null
        canApply = false
        pendingApproval = approval
        val task = when (action) {
            AiTextAction.CORRECT -> ime.getString(R.string.agent_cloud_correct)
            AiTextAction.TRANSLATE -> ime.getString(R.string.agent_cloud_translate_to,
                java.util.Locale.forLanguageTag(approval.language.code).getDisplayLanguage(java.util.Locale.getDefault()))
            AiTextAction.REPHRASE -> ime.getString(R.string.agent_cloud_rephrase_tone, ime.getString(approval.tone.titleResource()))
            AiTextAction.SUMMARIZE -> ime.getString(R.string.agent_cloud_summarize)
            AiTextAction.REPLY -> ime.getString(R.string.agent_cloud_reply_tone, ime.getString(approval.tone.titleResource()))
            AiTextAction.ANALYZE -> ime.getString(R.string.agent_cloud_analyze)
            AiTextAction.ASK, AiTextAction.TRANSLATE_STYLED -> return
        }
        panel?.showCloudConfirmation(approval.model, source.text.length, task, CloudProviders.displayName(approval.provider))
    }

    private fun prepareTool(tool: ToolId) {
        if (asking) return
        if (cloudRunner.isRunning || skillJob?.isActive == true) return
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        pendingApproval = null; pendingWorkflow = null; pendingTool = null; preparedResult = null; canApply = false
        val request = runCatching { Tools.prepare(tool, source.text) }.getOrNull()
        if (request == null) {
            panel?.showStatus(R.string.agent_tool_invalid)
            return
        }
        pendingTool = request
        panel?.showToolConfirmation(toolApprovalSummary(ime, request), request.tool.external)
    }

    private fun sendTool(request: ToolRequest) {
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        panel?.showCloudBusy(R.string.agent_tool_running)
        if (!cloudRunner.startTool(source, request, toolLauncher) { outcome ->
            outcome.onSuccess { result ->
                when (result) {
                    is ToolOutcome.Text -> {
                        preparedResult = result.text
                        canApply = false // A tool report is information, never a replacement.
                        panel?.showToolResult(result.text)
                    }
                    ToolOutcome.HandedOff -> panel?.showHandedOver(R.string.agent_tool_handed_off)
                }
            }.onFailure { failure -> panel?.showStatus(when (failure) {
                is ToolUnavailable -> R.string.agent_tool_unavailable
                is CloudFailure -> failure.error.cloudMessage()
                else -> R.string.agent_tool_failed
            }, true) }
        }) discardStaleContext()
    }

    private fun sendCloud() {
        if (dictation.isActive) { // the same button means "done speaking" while the microphone is on
            dictation.finish()
            return
        }
        val tool = pendingTool
        if (tool != null) {
            pendingTool = null // One approval for exactly the previewed request, consumed before start.
            sendTool(tool)
            return
        }
        val workflow = pendingWorkflow
        if (workflow != null) {
            pendingWorkflow = null // One approval for the immutable complete definition, consumed before start.
            val source = snapshot ?: return
            if (!editor.isCurrent(source)) { discardStaleContext(); return }
            panel?.showCloudBusy(R.string.agent_workflow_running)
            if (!cloudRunner.startWorkflow(source, workflow) { outcome ->
                outcome.onSuccess { result ->
                    preparedResult = result.text
                    canApply = result.allowsReplacement
                    panel?.showWorkflowResult(result, workflow)
                }.onFailure { failure -> panel?.showStatus(failure.workflowMessage(), true) }
            }) discardStaleContext()
            return
        }
        val approval = pendingApproval ?: return
        pendingApproval = null // Confirmation is consumed once, including failures.
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        panel?.showCloudBusy()
        if (!cloudRunner.start(source, approval.model, approval.action.instruction(approval.language, approval.tone), approval.action,
                approval.provider) { outcome ->
            outcome.onSuccess { result ->
                if (editor.policy.resultDenial(result) == null && asking) {
                    preparedResult = result
                    canApply = result.isNotBlank() // inserting is still a separate tap (insertAnswer)
                    panel?.showAnswer(result, translating)
                } else if (editor.policy.resultDenial(result) == null) {
                    preparedResult = result
                    canApply = approval.action.allowsReplacement
                    panel?.showResult(result, approval.action.allowsReplacement)
                } else panel?.showStatus(R.string.agent_panel_result_invalid, true)
            }.onFailure { failure -> panel?.showStatus((failure as? CloudFailure)?.error.cloudMessage(), true) }
        }) panel?.showStatus(R.string.agent_panel_stale, true)
    }

    private fun cancelCloud() {
        if (dictation.cancel()) {
            panel?.showDictationStatus(R.string.agent_dictation_cancelled)
            return
        }
        val toolOnly = pendingTool != null && !cloudRunner.isRunning
        cloudRunner.cancel()
        pendingApproval = null
        pendingWorkflow = null
        pendingTool = null
        preparedResult = null
        canApply = false
        val source = snapshot ?: return
        if (editor.isCurrent(source)) {
            if (asking) panel?.showQuestion(source.text, translating) else panel?.showSource(source.text)
            panel?.showStatus(if (toolOnly) R.string.agent_tool_cancelled else R.string.agent_cloud_cancelled)
        } else discardStaleContext()
    }

    private fun chooseWorkflows() {
        if (asking) return
        if (cloudRunner.isRunning || skillJob?.isActive == true) return
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        pendingApproval = null; pendingWorkflow = null; pendingTool = null; preparedResult = null; canApply = false
        panel?.showStatus(R.string.agent_workflow_loading, true)
        skillJob = actionScope.launch {
            val definitions = try { withContext(Dispatchers.IO) { WorkflowStore(ime).read() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (snapshot !== source) return@launch
            if (!editor.isCurrent(source)) { discardStaleContext(); return@launch }
            skillJob = null
            panel?.showSource(source.text)
            if (definitions == null) panel?.showStatus(R.string.agent_workflow_error)
            else panel?.showWorkflowChoices(definitions)
        }
    }

    private fun prepareWorkflow(definition: WorkflowDefinition) {
        if (asking) return
        if (cloudRunner.isRunning || skillJob?.isActive == true) return
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        pendingApproval = null; pendingWorkflow = null; pendingTool = null; preparedResult = null; canApply = false
        if (definition.steps.filterIsInstance<WorkflowStep.Ai>().any { it.provider !in CloudProviders.SUPPORTED }) {
            panel?.showStatus(R.string.agent_workflow_provider_unavailable)
            return
        }
        if (definition.steps.filterIsInstance<WorkflowStep.Ai>().any { !CloudProviders.validModel(it.provider, it.model) }) {
            panel?.showStatus(R.string.agent_workflow_model_invalid)
            return
        }
        pendingWorkflow = definition
        panel?.showWorkflowConfirmation(workflowApprovalSummary(ime, definition, source.text.length),
            definition.steps.any { it is WorkflowStep.Ai })
    }

    private fun applyPrepared() {
        if (dictation.isActive) {
            if (!dictation.isListening) insertDictation() // nothing can be inserted while the microphone is on
            return
        }
        if (asking) { insertAnswer(); return }
        val source = snapshot ?: return
        val result = preparedResult ?: return
        if (!canApply) return
        canApply = false
        editor.onInvalidated = null
        when (editor.apply(source, result)) {
            ApplyOutcome.SUBMITTED -> close()
            ApplyOutcome.REJECTED -> discardStaleContext()
            ApplyOutcome.UNKNOWN -> {
                needsCursorRefresh = true
                panel?.showStatus(R.string.agent_panel_apply_unknown, true)
                runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
            }
        }
    }

    fun cursorPositionUpdated(start: Int, end: Int) {
        if (needsCursorRefresh && start >= 0 && end >= 0 && inputLogic.refreshAgentEditorCaches(start, end)) {
            needsCursorRefresh = false
            editor.selectionChanged(start, end, -1, -1)
        }
    }

    private fun copyPrepared() {
        if (dictation.isActive) {
            if (dictation.editorChanged()) { panel?.showExpired(); return }
            copyToClipboard(dictation.reviewText ?: return)
            return
        }
        val source = snapshot ?: return
        if (!editor.isCurrent(source)) { discardStaleContext(); return }
        copyToClipboard(preparedResult ?: return)
    }

    private fun copyToClipboard(text: String) {
        val clip = ClipData.newPlainText(ime.getString(R.string.english_ime_name), text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        (ime.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
        Toast.makeText(ime, R.string.agent_panel_copied, Toast.LENGTH_SHORT).show()
    }

    fun close() {
        dropCommandRequest()
        dictation.cancel()
        skillJob?.cancel()
        skillJob = null
        cloudRunner.cancel()
        pendingApproval = null
        pendingWorkflow = null
        pendingTool = null
        editor.onInvalidated = null
        snapshot = null
        preparedResult = null
        canApply = false
        panel?.clear()
        wheel?.visibility = View.GONE
        calculator?.hide()
        calculatorCommand = null
        charArt?.hide()
        charArtField = null
        charArtSelection = null
        charArtFieldInPlace = false
        charArtUndo = null
        charArtUndoEnd = -1
        asking = false
        questionAction = AiTextAction.ASK
        questionCommand = null
        editor.invalidate()
    }

    fun destroy() {
        finishEditor()
        actionScope.cancel()
        if (KeyboardCommands.aiRequests?.get() === this) KeyboardCommands.aiRequests = null
    }
}

/** Denials that only mean "select the text now": a workflow chosen with `@wf` waits for them to pass. */
private val WAITS_FOR_SELECTION = setOf(DenialReason.NO_SELECTION, DenialReason.COMPOSITION)

private fun Throwable.workflowMessage(): Int = when (this) {
    is WorkflowFailure -> when (error) {
        WorkflowError.UNSUPPORTED_PROVIDER -> R.string.agent_workflow_provider_unavailable
        WorkflowError.INVALID_INPUT, WorkflowError.INVALID_OUTPUT, WorkflowError.TEXT_BUDGET -> R.string.agent_workflow_limits
    }
    is CloudFailure -> error.cloudMessage()
    else -> R.string.agent_cloud_network
}

/** The short text of a failed typed request on its chip; the panel shows [cloudMessage]. */
private fun CloudError?.commandChip() = when (this) {
    CloudError.CREDENTIALS -> R.string.agent_command_ai_no_key
    CloudError.AUTHENTICATION -> R.string.agent_command_ai_key_rejected
    CloudError.REFUSED -> R.string.agent_command_ai_refused
    CloudError.TOO_LARGE -> R.string.agent_command_ai_too_long
    CloudError.TIMEOUT -> R.string.agent_command_ai_timeout
    CloudError.INVALID_RESPONSE -> R.string.agent_command_ai_unusable
    else -> R.string.agent_command_ai_failed
}

internal fun CloudError?.cloudMessage() = when (this) {
    CloudError.STORAGE -> R.string.agent_runs_unavailable
    CloudError.CREDENTIALS -> R.string.agent_cloud_credentials
    CloudError.AUTHENTICATION -> R.string.agent_cloud_authentication
    CloudError.INVALID_REQUEST -> R.string.agent_cloud_invalid_request
    CloudError.RATE_LIMIT -> R.string.agent_cloud_rate_limit
    CloudError.TIMEOUT -> R.string.agent_cloud_timeout
    CloudError.REFUSED -> R.string.agent_cloud_refused
    CloudError.INVALID_RESPONSE -> R.string.agent_cloud_invalid_response
    CloudError.TOO_LARGE -> R.string.agent_cloud_too_long
    CloudError.BUSY -> R.string.agent_cloud_busy
    else -> R.string.agent_cloud_network
}

fun WritingTone.titleResource() = when (this) {
    WritingTone.NEUTRAL -> R.string.agent_tone_neutral
    WritingTone.FRIENDLY -> R.string.agent_tone_friendly
    WritingTone.PROFESSIONAL -> R.string.agent_tone_professional
}

private fun DenialReason.messageResource() = when (this) {
    DenialReason.PASSWORD, DenialReason.INCOGNITO, DenialReason.NO_LEARNING, DenialReason.LOCKED -> R.string.agent_panel_protected
    DenialReason.TOO_LARGE -> R.string.agent_panel_too_large
    DenialReason.COMPOSITION -> R.string.agent_panel_composing
    DenialReason.STALE_CONTEXT -> R.string.agent_panel_stale
    else -> R.string.agent_panel_select_text
}

private fun DenialReason.questionMessageResource(translating: Boolean) = when (this) {
    DenialReason.PASSWORD, DenialReason.INCOGNITO, DenialReason.NO_LEARNING, DenialReason.LOCKED -> R.string.agent_panel_protected
    else -> if (translating) R.string.agent_command_translate_unavailable else R.string.agent_command_ki_unavailable
}

private fun DictationError.messageResource() = when (this) {
    DictationError.UNAVAILABLE -> R.string.agent_dictation_unavailable
    DictationError.PERMISSION -> R.string.agent_dictation_permission
    DictationError.LANGUAGE -> R.string.agent_dictation_language
    DictationError.NO_SPEECH -> R.string.agent_dictation_no_speech
    DictationError.BUSY -> R.string.agent_dictation_busy
    DictationError.FAILED -> R.string.agent_dictation_failed
}

private fun DenialReason.dictationMessageResource() = when (this) {
    DenialReason.PASSWORD, DenialReason.INCOGNITO, DenialReason.NO_LEARNING, DenialReason.LOCKED -> R.string.agent_panel_protected
    DenialReason.COMPOSITION -> R.string.agent_dictation_composing
    DenialReason.STALE_CONTEXT -> R.string.agent_panel_stale
    DenialReason.TOO_LARGE -> R.string.agent_dictation_too_long
    DenialReason.NO_CURSOR -> R.string.agent_dictation_no_cursor
    else -> R.string.agent_dictation_no_field
}
