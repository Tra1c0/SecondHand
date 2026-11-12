// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R

/**
 * A typed AI request (`@ki`, `@translate`) sent from the strip with one tap on its chip. It belongs to
 * exactly the typed command [commandText] ending at the cursor [commandEnd]; any other text or cursor
 * shows the send chip again. Created and changed only by
 * [helium314.keyboard.agent.AgentEditorController] on the main thread. Holds the request and the
 * answer in memory only, never on disk or in a log.
 */
class CommandAiRequest internal constructor(
    val commandText: String,
    val commandEnd: Int,
    val action: AiTextAction,
    /** Exactly what was sent (the question, or style and text of a translation). */
    val request: String,
) {
    sealed interface State
    /** Sent; the answer is awaited. */
    object Running : State
    /** The answer; it replaces the command only after its own tap. */
    class Answer(val text: String) : State
    /**
     * Nothing to insert: [chip] is the short text for the strip, [details] the explanation in the
     * panel. [needsSettings]: the tap opens the AI settings (missing key or model) instead of the panel.
     */
    class Failed(val chip: Int, val details: Int, val needsSettings: Boolean = false) : State

    var state: State = Running
        internal set

    /** Rebuilds the chips that show this request; set by the chips built last. */
    internal var onChange: (() -> Unit)? = null

    fun matches(text: String, end: Int) = text == commandText && end == commandEnd
}

/**
 * The chips of a typed AI request (Variante A, two taps): a send chip that names the provider and
 * sends exactly [request] when tapped; while waiting a loading chip with moving dots and "Cancel";
 * then the answer as a chip that replaces the command when tapped, with "↻" (send again, a new tap
 * and a new request) and "✕" (discard); after a failure one muted chip that opens the details.
 */
internal object AiCommandChips {
    /** Characters of the typed text shown on a send chip; the field shows all of it. */
    const val SHOWN = 40
    /** Characters of an answer shown on its chip; a tap inserts all of it. */
    const val ANSWER_SHOWN = 300

    fun chips(host: KeyboardCommands.Host, call: CommandCall, action: AiTextAction, request: String,
              sendLabel: (provider: String) -> CharSequence, sendDescription: (provider: String) -> CharSequence): List<KeyboardCommands.Chip> {
        val context = host.context
        val provider = host.aiProvider ?: return emptyList()
        val current = host.aiRequest(call)?.takeIf { it.action == action && it.request == request }
        fun send() {
            host.sendAi(call, request, action)
            host.refresh()
        }
        return when (val state = current?.state) {
            null -> listOf(KeyboardCommands.Chip(sendLabel(provider), sendDescription(provider), onTap = ::send))
            CommandAiRequest.Running -> listOf(
                KeyboardCommands.Chip(context.getString(if (action == AiTextAction.TRANSLATE_STYLED) R.string.agent_command_ai_translating
                    else R.string.agent_command_ai_asking), kind = KeyboardCommands.Chip.Kind.LOADING) {},
                KeyboardCommands.Chip(context.getString(R.string.agent_command_ai_cancel),
                    context.getString(R.string.agent_command_ai_cancel_description), KeyboardCommands.Chip.Kind.MUTED) {
                    host.dropAi(call)
                },
            )
            is CommandAiRequest.Answer -> listOf(
                KeyboardCommands.Chip(shorten(state.text.replace(LINE_BREAKS, " "), ANSWER_SHOWN),
                    context.getString(R.string.agent_command_ai_insert_description, state.text), KeyboardCommands.Chip.Kind.RESULT) {
                    host.insertAi(call)
                },
                KeyboardCommands.Chip("↻", context.getString(R.string.agent_command_ai_retry_description, provider),
                    KeyboardCommands.Chip.Kind.MUTED, onTap = ::send),
                KeyboardCommands.Chip("✕", context.getString(R.string.agent_command_ai_discard_description),
                    KeyboardCommands.Chip.Kind.MUTED) { host.dropAi(call) },
            )
            is CommandAiRequest.Failed -> listOf(KeyboardCommands.Chip(context.getString(state.chip),
                context.getString(state.details), KeyboardCommands.Chip.Kind.MUTED) { host.aiDetails(call) })
        }
    }

    /** At most [length] characters, cut with "…" and never inside a surrogate pair. */
    fun shorten(text: String, length: Int): String {
        if (text.length <= length) return text
        return text.take(length - 1).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }.trimEnd() + "…"
    }

    private val LINE_BREAKS = Regex("\\s*[\\r\\n]+\\s*")
}
