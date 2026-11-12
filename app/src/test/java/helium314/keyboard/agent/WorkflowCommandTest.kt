// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.LocalTextAction
import dev.agentkeyboard.core.WorkflowDefinition
import dev.agentkeyboard.core.WorkflowInput
import dev.agentkeyboard.core.WorkflowStep
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.WorkflowCommand
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class WorkflowCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private val opened = mutableListOf<String>()

    private fun workflow(name: String) = WorkflowDefinition(UUID.randomUUID().toString(), 1, name,
        listOf(WorkflowStep.Local("upper", WorkflowInput.Selection, LocalTextAction.UPPERCASE)))
    private val feedback = workflow("Feedback geben")
    private val mail = workflow("Mail kürzen")
    private val shortMail = workflow("Kurze Antwort")
    private var workflows = listOf(feedback, mail, shortMail)
    private var reads = 0
    private var failing = false

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            replaced += command.text to replacement
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override fun openWorkflow(workflowId: String): Boolean { opened += workflowId; return true }
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        WorkflowCommand.source = WorkflowCommand.Source {
            reads++
            if (failing) error("locked")
            workflows
        }
    }

    @After fun tearDown() {
        WorkflowCommand.source = WorkflowCommand.Source { WorkflowStore(it).read() }
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall
    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }
    private fun label(name: String) = latinIME.getString(R.string.agent_command_wf_label, name)

    @Test fun isBuiltInAndProtectsItsArgument() {
        assertTrue(WorkflowCommand in KeyboardCommands.BUILT_IN)
        assertTrue(WorkflowCommand.protectsArgument)
        assertTrue(KeyboardCommands.guardsTextBefore("@wf Feedb"))
    }

    @Test fun namesMatchByStartThenWordThenAnyPart() {
        assertEquals(listOf("Feedback geben", "Kurze Antwort", "Mail kürzen"), WorkflowCommand.match("", workflows).map { it.name })
        assertEquals(listOf("Kurze Antwort", "Mail kürzen", "Feedback geben"), WorkflowCommand.match("K", workflows).map { it.name })
        assertEquals(listOf("Feedback geben"), WorkflowCommand.match("eben", workflows).map { it.name })
        assertTrue(WorkflowCommand.match("xyz", workflows).isEmpty())
    }

    @Test fun chipsSayTheyNeedSelectedTextAndATapOpensThePanelWithoutChangingTheField() {
        val chips = KeyboardCommands.chipsFor(call("@wf feed"), host)
        assertEquals(listOf(label("Feedback geben")), labels(chips))
        assertTrue(chips.single().label.contains(latinIME.getString(R.string.agent_command_wf_label, "").trim()))
        assertEquals(latinIME.getString(R.string.agent_command_wf_open, "Feedback geben"), chips.single().description)
        assertTrue(opened.isEmpty()) // nothing happens without a tap
        chips.single().onTap()
        assertEquals(listOf(feedback.id), opened)
        assertTrue(replaced.isEmpty()) // the command text stays: the panel opens first (P43)
    }

    @Test fun withoutWorkflowsOneChipOpensTheWorkflowSettings() {
        workflows = emptyList()
        val chip = KeyboardCommands.chipsFor(call("@wf"), host).single()
        assertEquals(latinIME.getString(R.string.agent_command_wf_add), chip.label)
        chip.onTap()
        val intent = shadowOf(latinIME.application).nextStartedActivity
        assertEquals(SettingsDestination.AgentWorkflows, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(SettingsDestination.AgentWorkflows in AgentPreferences.SETTINGS_DESTINATIONS)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(opened.isEmpty())
    }

    @Test fun unreadableStorageShowsNothingAndReadsOncePerCommand() {
        failing = true
        assertTrue(KeyboardCommands.chipsFor(call("@wf"), host).isEmpty())
        failing = false
        KeyboardCommands.chipsFor(call("@wf f"), host)
        KeyboardCommands.chipsFor(call("@wf fe"), host)
        assertEquals(2, reads)
        KeyboardCommands.forget()
        KeyboardCommands.chipsFor(call("@wf fe"), host)
        assertEquals(3, reads)
    }

    @Test fun blockedFieldsReadNothing() {
        val typed = call("@wf feed")
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(KeyboardCommands.find("@wf feed", Settings.getValues(), editor))
        assertTrue(WorkflowCommand.chips(host, typed).isEmpty())
        assertEquals(0, reads)
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        KeyboardCommands.setCommandEnabled(latinIME, "wf", false)
        assertNull(KeyboardCommands.find("@wf feed", Settings.getValues(), editor))
    }
}
