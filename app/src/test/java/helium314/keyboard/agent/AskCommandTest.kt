// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.AskCommand
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/** Stage 24f `@ki`: the chip only opens the send preview; the panel flow is in InputLogicTest. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class AskCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<String>()
    private val asked = mutableListOf<Pair<String, String>>()

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean { replaced += replacement; return true }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override fun askAi(command: CommandCall, question: String): Boolean { asked += command.text to question; return true }
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    @After fun tearDown() { KeyboardCommands.load(latinIME) }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall

    @Test fun isBuiltInAndProtectsItsArgument() {
        assertTrue(AskCommand in KeyboardCommands.BUILT_IN)
        assertTrue(AskCommand.protectsArgument)
        assertTrue(KeyboardCommands.guardsTextBefore("x @ki wie spät ist es"))
        assertTrue(AskCommand.description(latinIME).contains("@ki"))
    }

    @Test fun oneChipWithTheExactQuestionAndATapOnlyOpensThePreview() {
        val chips = KeyboardCommands.chipsFor(call("x @ki  wie spät ist es "), host)
        assertEquals(listOf(latinIME.getString(R.string.agent_command_ki_chip, "wie spät ist es")), chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_ki_open, "wie spät ist es"), chips.single().description)
        assertTrue(asked.isEmpty()) // nothing happens without a tap
        chips.single().onTap()
        assertEquals(listOf("@ki  wie spät ist es " to "wie spät ist es"), asked)
        assertTrue(replaced.isEmpty()) // the field stays unchanged: the panel opens first (P43)
    }

    @Test fun withoutAQuestionThereIsNoChip() {
        assertTrue(KeyboardCommands.chipsFor(call("x @ki"), host).isEmpty())
        assertTrue(KeyboardCommands.chipsFor(call("x @ki   "), host).isEmpty())
    }

    @Test fun blockedFieldsOfferNothing() {
        val typed = call("@ki hallo")
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(KeyboardCommands.find("@ki hallo", Settings.getValues(), editor))
        assertTrue(AskCommand.chips(host, typed).isEmpty())
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        KeyboardCommands.setCommandEnabled(latinIME, "ki", false)
        assertNull(KeyboardCommands.find("@ki hallo", Settings.getValues(), editor))
        assertTrue(asked.isEmpty())
    }

    @Test fun everyJournaledTextActionHasATitleIncludingTheQuestion() {
        // the run list on the AI settings page must not crash after an @ki run (ASK has no built-in action)
        for (action in dev.agentkeyboard.core.AiTextAction.entries)
            assertTrue(latinIME.getString(helium314.keyboard.settings.screens.textActionTitle(action)).isNotBlank())
        assertEquals(R.string.agent_command_ki_task,
            helium314.keyboard.settings.screens.textActionTitle(dev.agentkeyboard.core.AiTextAction.ASK))
    }
}
