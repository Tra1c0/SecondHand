// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.*
import dev.agentkeyboard.core.LocalTextAction
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.ToolId
import dev.agentkeyboard.core.WorkflowDefinition
import dev.agentkeyboard.core.WorkflowResult
import dev.agentkeyboard.core.WorkflowStep
import helium314.keyboard.latin.R

/** Read-only controls inside the IME window: never creates a second input connection. */
class AgentPanelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    var onClose: (() -> Unit)? = null
    var onAction: ((LocalTextAction) -> Unit)? = null
    var onApply: (() -> Unit)? = null
    var onCopy: (() -> Unit)? = null
    var onPrepareCloud: ((AiTextAction) -> Unit)? = null
    var onSendCloud: (() -> Unit)? = null
    var onCancelCloud: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onWorkflows: (() -> Unit)? = null
    var onWorkflow: ((WorkflowDefinition) -> Unit)? = null
    var onTool: ((ToolId) -> Unit)? = null
    var onDictate: (() -> Unit)? = null
    private val source: TextView
    private val result: TextView
    private val status: TextView
    private val resultHeading: TextView
    private val applyButton: Button
    private val copyButton: Button
    private val cloudConfirmation: TextView
    private val sendButton: Button
    private val cancelButton: Button
    private val workflowChoices: LinearLayout
    private val dictateButton: Button
    private val actionButtons = mutableListOf<Button>()
    /** Look of the Flat key style; null in every other style. */
    private var flatStyle: FlatPanelStyle? = null
    private val dp = resources.displayMetrics.density

    init {
        isFillViewport = true
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
        val colors = context.obtainStyledAttributes(R.styleable.AgentSurface)
        setBackgroundColor(colors.getColor(R.styleable.AgentSurface_android_colorBackground, Color.WHITE))
        val foreground = colors.getColor(R.styleable.AgentSurface_android_textColorPrimary, Color.BLACK)
        colors.recycle()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(8), px(12), px(12))
            isClickable = true
        }
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        fun label(stringId: Int, viewId: Int = View.NO_ID) = TextView(context).apply {
            id = viewId
            setText(stringId)
            setTextColor(foreground)
            textSize = 14f
            setPadding(0, px(4), 0, px(4))
            isFocusable = false
            setTextIsSelectable(false)
        }
        fun button(textId: Int, viewId: Int, click: () -> Unit) = Button(context).apply {
            id = viewId
            setText(textId)
            isAllCaps = false
            textSize = 13f
            minimumWidth = 0
            minimumHeight = px(48)
            setOnClickListener { click() }
        }
        val heading = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        heading.addView(label(R.string.agent_panel_title), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(button(R.string.agent_ai_title, R.id.agent_cloud_settings) { onSettings?.invoke() })
        heading.addView(button(R.string.agent_panel_close, R.id.agent_close) { onClose?.invoke() })
        body.addView(heading)
        body.addView(label(R.string.agent_panel_local_description))
        dictateButton = button(R.string.agent_dictation_start, R.id.agent_dictate) { onDictate?.invoke() }
        body.addView(dictateButton)
        status = label(R.string.agent_panel_select_text, R.id.agent_status)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        body.addView(status)
        body.addView(label(R.string.agent_panel_source))
        source = label(R.string.agent_panel_empty, R.id.agent_source).apply {
            maxLines = 6
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { maxLines = Int.MAX_VALUE; ellipsize = null }
        }
        body.addView(source)
        cloudConfirmation = label(R.string.agent_panel_empty, R.id.agent_cloud_confirmation)
        body.addView(cloudConfirmation)
        sendButton = button(R.string.agent_workflow_run_local, R.id.agent_cloud_send) { onSendCloud?.invoke() }
        cancelButton = button(R.string.agent_cloud_cancel, R.id.agent_cloud_cancel) { onCancelCloud?.invoke() }
        body.addView(sendButton)
        body.addView(cancelButton)
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val entries = listOf(
            Triple(LocalTextAction.UPPERCASE, R.string.agent_action_uppercase, R.id.agent_uppercase),
            Triple(LocalTextAction.LOWERCASE, R.string.agent_action_lowercase, R.id.agent_lowercase),
            Triple(LocalTextAction.CLEAN_SPACES, R.string.agent_action_spaces, R.id.agent_clean_spaces),
        )
        entries.forEach { (action, title, id) ->
            val view = button(title, id) { onAction?.invoke(action) }
            actionButtons.add(view)
            actions.addView(view, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(actions)
        body.addView(label(R.string.agent_cloud_actions))
        listOf(
            Triple(AiTextAction.CORRECT, R.string.agent_cloud_correct, R.id.agent_cloud_correct),
            Triple(AiTextAction.TRANSLATE, R.string.agent_cloud_translate, R.id.agent_cloud_translate),
            Triple(AiTextAction.REPHRASE, R.string.agent_cloud_rephrase, R.id.agent_cloud_rephrase),
            Triple(AiTextAction.SUMMARIZE, R.string.agent_cloud_summarize, R.id.agent_cloud_summarize),
            Triple(AiTextAction.REPLY, R.string.agent_cloud_reply, R.id.agent_cloud_reply),
            Triple(AiTextAction.ANALYZE, R.string.agent_cloud_analyze, R.id.agent_cloud_analyze),
        ).chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (action, title, id) ->
                val view = button(title, id) { onPrepareCloud?.invoke(action) }
                actionButtons.add(view)
                row.addView(view, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            }
            body.addView(row)
        }
        val workflows = button(R.string.agent_workflows_title, R.id.agent_workflows) { onWorkflows?.invoke() }
        actionButtons.add(workflows)
        body.addView(workflows)
        workflowChoices = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(workflowChoices)
        body.addView(label(R.string.agent_tools_title))
        val tools = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            Triple(ToolId.TEST_COUNT, R.string.agent_tool_test, R.id.agent_tool_test),
            Triple(ToolId.CALENDAR_INSERT, R.string.agent_tool_calendar, R.id.agent_tool_calendar),
        ).forEach { (tool, title, id) ->
            val view = button(title, id) { onTool?.invoke(tool) }
            actionButtons.add(view)
            tools.addView(view, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        body.addView(tools)
        resultHeading = label(R.string.agent_panel_result)
        body.addView(resultHeading)
        result = label(R.string.agent_panel_empty, R.id.agent_result).apply {
            maxLines = 6
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { maxLines = Int.MAX_VALUE; ellipsize = null }
        }
        body.addView(result)
        val footer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        applyButton = button(R.string.agent_panel_apply, R.id.agent_apply) { onApply?.invoke() }
        copyButton = button(R.string.agent_panel_copy, R.id.agent_copy) { onCopy?.invoke() }
        footer.addView(applyButton, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(copyButton, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        body.addView(footer)
        flatStyle = FlatPanelStyle.attachIfActive(this, body)
        clear()
    }

    private fun px(value: Int) = (value * dp).toInt()

    fun showSource(text: String) {
        hideWorkflowChoices()
        hideCloudControls()
        resultHeading.setText(R.string.agent_panel_result)
        source.text = text
        dictateButton.isEnabled = false
        source.maxLines = 6
        source.ellipsize = TextUtils.TruncateAt.END
        result.setText(R.string.agent_panel_empty)
        status.text = context.getString(R.string.agent_panel_source_count, text.length)
        actionButtons.forEach { it.isEnabled = true }
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        visibility = View.VISIBLE
        scrollTo(0, 0)
    }

    fun showResult(text: String, allowsReplacement: Boolean = true) {
        hideWorkflowChoices()
        hideCloudControls()
        actionButtons.forEach { it.isEnabled = true }
        result.text = text.ifEmpty { context.getString(R.string.agent_panel_empty_result) }
        result.maxLines = 6
        result.ellipsize = TextUtils.TruncateAt.END
        resultHeading.setText(if (allowsReplacement) R.string.agent_panel_result else R.string.agent_cloud_analysis_result)
        status.setText(if (allowsReplacement) R.string.agent_panel_ready else R.string.agent_cloud_analysis_ready)
        applyButton.isEnabled = allowsReplacement
        copyButton.isEnabled = true
        post { if (copyButton.isEnabled) smoothScrollTo(0, resultHeading.top) }
    }

    /** A tool report can be read or copied; it never offers to replace the selection. */
    fun showToolResult(text: String) {
        showResult(text, false)
        resultHeading.setText(R.string.agent_tool_test_result)
        status.setText(R.string.agent_tool_test_ready)
    }

    fun showToolConfirmation(summary: String, external: Boolean) {
        showWorkflowConfirmation(summary, false)
        sendButton.setText(if (external) R.string.agent_tool_hand_over else R.string.agent_tool_run_test)
        cancelButton.setText(R.string.agent_tool_cancel)
    }

    /** The other app may never appear; the panel must stay usable after a hand-over. */
    fun showHandedOver(message: Int) {
        showStatus(message)
        actionButtons.forEach { it.isEnabled = true }
        scrollTo(0, 0)
    }

    fun showWorkflowResult(outcome: WorkflowResult, definition: WorkflowDefinition) {
        showResult(outcome.text, outcome.allowsReplacement)
        val modelCalls = definition.steps.count { it is WorkflowStep.Ai && it.id in outcome.executedSteps }
        status.text = context.getString(R.string.agent_workflow_finished, modelCalls, outcome.skippedSteps.size) + " " + status.text
    }

    fun showStatus(message: Int, disableActions: Boolean = false) {
        hideWorkflowChoices()
        hideCloudControls()
        status.setText(message)
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        if (disableActions) actionButtons.forEach { it.isEnabled = false }
        visibility = View.VISIBLE
    }

    fun showExpired() {
        source.text = ""
        result.text = ""
        showStatus(R.string.agent_panel_stale, true)
        scrollTo(0, 0) // the explanation and the Close button are at the top
    }

    fun clear() {
        hideWorkflowChoices()
        hideCloudControls()
        resultHeading.setText(R.string.agent_panel_result)
        source.text = ""
        result.text = ""
        status.setText(R.string.agent_panel_select_text)
        actionButtons.forEach { it.isEnabled = false }
        dictateButton.isEnabled = false
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        visibility = View.GONE
    }

    /** Back to the neutral labels. Every state calls this first and then sets its own. */
    private fun hideCloudControls() {
        flatStyle?.setBusy(false)
        cancelButton.setText(R.string.agent_cloud_cancel)
        applyButton.setText(R.string.agent_panel_apply)
        keepScreenOn = false
        // Dictation needs a plain cursor. With a selection on show it could only fail, and starting
        // it would throw away the prepared result.
        dictateButton.isEnabled = source.text.isEmpty()
        cloudConfirmation.text = ""
        cloudConfirmation.visibility = View.GONE
        sendButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
    }

    fun showCloudConfirmation(model: String, characters: Int, task: String = context.getString(R.string.agent_cloud_correct),
                              provider: String = "OpenAI") {
        hideWorkflowChoices()
        sendButton.text = context.getString(R.string.agent_cloud_send, provider)
        cancelButton.setText(R.string.agent_cloud_cancel)
        resultHeading.setText(R.string.agent_panel_result)
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        result.text = ""
        cloudConfirmation.text = context.getString(R.string.agent_cloud_confirm, model, characters, task, provider)
        cloudConfirmation.visibility = View.VISIBLE
        sendButton.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
        sendButton.isEnabled = true
        post { if (sendButton.visibility == View.VISIBLE) smoothScrollTo(0, (cloudConfirmation.top - source.height - px(8)).coerceAtLeast(0)) }
    }

    fun showCloudBusy(message: Int = R.string.agent_cloud_working) {
        hideWorkflowChoices()
        hideCloudControls()
        status.setText(message)
        actionButtons.forEach { it.isEnabled = false }
        dictateButton.isEnabled = false
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        cancelButton.visibility = View.VISIBLE
        flatStyle?.let { result.text = ""; it.setBusy(true) }
    }

    /** The microphone is on. Text actions need a selection and stay off; only done and cancel work. */
    fun showDictationListening(partial: String) {
        hideWorkflowChoices()
        hideCloudControls()
        source.text = ""
        result.text = ""
        resultHeading.setText(R.string.agent_panel_result)
        status.setText(R.string.agent_dictation_listening)
        // Shown directly above the two buttons, so the words and the controls are visible together.
        cloudConfirmation.text = partial
        cloudConfirmation.visibility = View.VISIBLE
        actionButtons.forEach { it.isEnabled = false }
        dictateButton.isEnabled = false
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        sendButton.setText(R.string.agent_dictation_done)
        sendButton.isEnabled = true
        sendButton.visibility = View.VISIBLE
        cancelButton.setText(R.string.agent_tool_cancel)
        cancelButton.visibility = View.VISIBLE
        keepScreenOn = true // a screen timeout must not leave the microphone running unseen
        visibility = View.VISIBLE
        scrollTo(0, 0)
    }

    /** "Done" was tapped: wait for the final text. Only cancel is possible. */
    fun showDictationProcessing() {
        status.setText(R.string.agent_dictation_processing)
        sendButton.isEnabled = false
    }

    /** The microphone is off. The heard text is shown; inserting it is a separate click. */
    fun showDictationReview(text: String) {
        hideWorkflowChoices()
        hideCloudControls()
        source.text = ""
        resultHeading.setText(R.string.agent_dictation_heard)
        result.text = text
        result.maxLines = 6
        result.ellipsize = TextUtils.TruncateAt.END
        status.setText(R.string.agent_dictation_review)
        actionButtons.forEach { it.isEnabled = false }
        applyButton.setText(R.string.agent_dictation_insert)
        applyButton.isEnabled = true
        copyButton.isEnabled = true
        visibility = View.VISIBLE
        post { if (applyButton.isEnabled) smoothScrollTo(0, resultHeading.top) }
    }

    /** Dictation did not start or ended without text: the microphone is off and nothing is kept. */
    fun showDictationStatus(message: Int) {
        showStatus(message, true)
        source.text = ""
        result.text = ""
        resultHeading.setText(R.string.agent_panel_result)
        scrollTo(0, 0)
    }

    private fun hideWorkflowChoices() {
        workflowChoices.removeAllViews()
        workflowChoices.visibility = View.GONE
    }

    fun showWorkflowChoices(definitions: List<WorkflowDefinition>) {
        hideCloudControls()
        hideWorkflowChoices()
        result.text = ""
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        actionButtons.forEach { it.isEnabled = true }
        status.setText(if (definitions.isEmpty()) R.string.agent_workflow_empty else R.string.agent_workflow_choose)
        definitions.forEach { definition ->
            workflowChoices.addView(Button(context).apply {
                text = context.getString(R.string.agent_workflow_choice, definition.name, definition.revision)
                tag = definition.id
                isAllCaps = false
                minimumHeight = px(48)
                setOnClickListener { onWorkflow?.invoke(definition) }
            }.also { flatStyle?.styleChip(it) })
        }
        workflowChoices.visibility = View.VISIBLE
        post { if (workflowChoices.visibility == View.VISIBLE) smoothScrollTo(0, workflowChoices.top) }
    }

    fun showWorkflowConfirmation(summary: String, usesCloud: Boolean) {
        hideWorkflowChoices()
        hideCloudControls()
        result.text = ""
        applyButton.isEnabled = false
        copyButton.isEnabled = false
        cloudConfirmation.text = summary
        cloudConfirmation.visibility = View.VISIBLE
        sendButton.setText(if (usesCloud) R.string.agent_workflow_run_cloud else R.string.agent_workflow_run_local)
        sendButton.isEnabled = true
        sendButton.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
        post { if (sendButton.visibility == View.VISIBLE) smoothScrollTo(0, cloudConfirmation.top) }
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        (parent as? AgentKeyboardFrame)?.updateOverlayAccessibility()
    }
}
