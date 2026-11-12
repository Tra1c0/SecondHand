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
    /** True while the panel serves a typed `@ki` question (stage 24f); the selection actions stay off. */
    private var asking = false
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
        || calculator?.visibility == View.VISIBLE
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

    init {
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
        needsCursorRefresh = false
        editor.startEditor(info.inputType, info.imeOptions, info.initialSelStart, info.initialSelEnd)
    }
    fun invalidate() = editor.invalidate()
    fun finishEditor() {
        close()
        needsCursorRefresh = false
        editor.finishEditor()
    }
    fun selectionChanged(start: Int, end: Int, compositionStart: Int, compositionEnd: Int) {
        editor.selectionChanged(start, end, compositionStart, compositionEnd)
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
        wheel?.onAction = { action -> open(); if (action != null) runLocal(action) }
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
            ime.startActivity(android.content.Intent(ime, helium314.keyboard.settings.SettingsActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AgentPreferences.OPEN_AI_SETTINGS, true))
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
        open()
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
     * key bound to it does. Without selected text the panel shows its usual message; the run still
     * needs the approval in the panel. The field is not changed. False if there is no panel now.
     */
    fun openWorkflow(workflowId: String): Boolean {
        if (panel == null || needsCursorRefresh) return false
        open()
        val source = snapshot ?: return true
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
        return true
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

    /**
     * Stage 24f `@ki`: opens the panel with a send preview of exactly [question] and the provider
     * and model from the AI settings. Nothing is sent before "Send", nothing from the field is part
     * of the request, and the field stays unchanged until "Insert" replaces [commandText] ending at
     * [commandEnd] by the answer (or inserts it at the cursor once the command is gone). False if
     * there is no panel now.
     */
    fun openQuestion(question: String, commandText: String, commandEnd: Int): Boolean {
        val target = panel ?: return false
        if (needsCursorRefresh) return false
        close()
        KeyboardSwitcher.getInstance().mainKeyboardView?.closing()
        target.rootView.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)?.dismissMoreSuggestionsPanel()
        asking = true
        questionCommand = if (commandText.isEmpty() || commandEnd < 0) null else commandText to commandEnd
        when (val captured = editor.captureQuestion(question)) {
            is CaptureResult.Captured -> {
                snapshot = captured.snapshot
                target.showQuestion(captured.snapshot.text)
                editor.onInvalidated = {
                    val source = snapshot
                    if (source != null && !editor.isCurrent(source)) discardStaleContext()
                }
                prepareQuestion()
            }
            is CaptureResult.Denied -> target.showStatus(captured.reason.questionMessageResource(), true)
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
            CloudApproval(provider, settings.model(provider), AiTextAction.ASK, settings.translationLanguage, settings.writingTone)
        }.getOrNull()
        if (approval == null || !hasCredential(approval.provider)) {
            panel?.showStatus(R.string.agent_command_ki_credentials, true)
            return
        }
        if (!CloudProviders.validModel(approval.provider, approval.model)) {
            panel?.showStatus(if (approval.model.isEmpty()) R.string.agent_cloud_model_missing else R.string.agent_cloud_invalid_request, true)
            return
        }
        preparedResult = null
        canApply = false
        pendingApproval = approval
        panel?.showQuestionConfirmation(approval.model, source.text.length, CloudProviders.displayName(approval.provider))
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
                Toast.makeText(ime, R.string.agent_command_ki_insert_failed, Toast.LENGTH_SHORT).show()
            }
            ApplyOutcome.UNKNOWN -> {
                needsCursorRefresh = true
                panel?.showStatus(R.string.agent_panel_apply_unknown, true)
                runCatching { ime.currentInputConnection?.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE) }
            }
        }
    }

    fun open() {
        val target = panel ?: return
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
            is CaptureResult.Denied -> target.showStatus(captured.reason.messageResource(), true)
        }
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
        if (asking || action == AiTextAction.ASK) return // a question has its own preview (prepareQuestion)
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
            AiTextAction.ASK -> return
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
                    panel?.showAnswer(result)
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
            if (asking) panel?.showQuestion(source.text) else panel?.showSource(source.text)
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
        asking = false
        questionCommand = null
        editor.invalidate()
    }

    fun destroy() { finishEditor(); actionScope.cancel() }
}

private fun Throwable.workflowMessage(): Int = when (this) {
    is WorkflowFailure -> when (error) {
        WorkflowError.UNSUPPORTED_PROVIDER -> R.string.agent_workflow_provider_unavailable
        WorkflowError.INVALID_INPUT, WorkflowError.INVALID_OUTPUT, WorkflowError.TEXT_BUDGET -> R.string.agent_workflow_limits
    }
    is CloudFailure -> error.cloudMessage()
    else -> R.string.agent_cloud_network
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

private fun DenialReason.questionMessageResource() = when (this) {
    DenialReason.PASSWORD, DenialReason.INCOGNITO, DenialReason.NO_LEARNING, DenialReason.LOCKED -> R.string.agent_panel_protected
    else -> R.string.agent_command_ki_unavailable
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
