// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.util.Log
import android.widget.Toast
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.WorkflowDefinition
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.WorkflowStore
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsDestination
import java.util.Locale

/**
 * `@wf name` (stage 24f): the saved workflows whose name matches, as chips. A tap changes nothing
 * in the field; it hands the workflow to the text actions ([KeyboardCommands.Host.openWorkflow]).
 * A typed command means nothing is selected, so the panel remembers the choice and asks for a
 * selection; opening the text actions again with selected text in the same field prepares the
 * workflow like a skill key would. Every run, cloud steps included, still needs its own approval.
 */
object WorkflowCommand : KeyboardCommands.Command {
    private const val TAG = "WorkflowCommand"
    const val MAX_CHIPS = 10

    /** Where workflows come from; replaced in tests. */
    fun interface Source { fun read(context: Context): List<WorkflowDefinition> }

    @JvmStatic var source: Source = Source { WorkflowStore(it).read() }

    /** Read once per typed command; dropped when the command is gone. */
    private var cached: List<WorkflowDefinition>? = null

    override val name = "wf"
    override val protectsArgument = true

    override fun description(context: Context) =
        context.getString(R.string.agent_command_wf, KeyboardCommands.prefix(context).toString())

    override fun reset() { cached = null }

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { reset(); return emptyList() }
        val context = host.context
        // unreadable storage shows nothing, not an invitation to create workflows
        val workflows = cached ?: (read(context) ?: return emptyList()).also { cached = it }
        if (workflows.isEmpty()) return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_wf_add),
            context.getString(R.string.agent_command_wf_add_description)) {
            SnipCommand.openSettings(context, SettingsDestination.AgentWorkflows)
        })
        return match(call.argument, workflows).map { workflow ->
            KeyboardCommands.Chip(context.getString(R.string.agent_command_wf_label, workflow.name),
                context.getString(R.string.agent_command_wf_open, workflow.name)) {
                // the stored definition is read again when the panel prepares it
                if (!host.openWorkflow(workflow.id))
                    Toast.makeText(context, R.string.agent_command_wf_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun read(context: Context): List<WorkflowDefinition>? = try {
        source.read(context)
    } catch (e: Exception) {
        Log.w(TAG, "workflows could not be read: ${e.javaClass.simpleName}")
        null
    }

    /** Workflows for [query]: same name, name starts with it, a word starts with it, name contains it; all without a query. */
    fun match(query: String, workflows: List<WorkflowDefinition>): List<WorkflowDefinition> {
        val typed = query.trim().lowercase(Locale.ROOT)
        return workflows.mapNotNull { workflow ->
            val name = workflow.name.lowercase(Locale.ROOT)
            val score = when {
                typed.isEmpty() -> 1
                name == typed -> 0
                name.startsWith(typed) -> 1
                name.split(' ', '-').any { it.startsWith(typed) } -> 2
                name.contains(typed) -> 3
                else -> return@mapNotNull null
            }
            Triple(workflow, score, name)
        }.sortedWith(compareBy({ it.second }, { it.third })).take(MAX_CHIPS).map { it.first }
    }
}
