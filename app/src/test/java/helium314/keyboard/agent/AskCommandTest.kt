// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.AskCommand
import helium314.keyboard.agent.commands.CommandAiRequest
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

/**
 * Stage 24f `@ki` (Variante A): the chip is the send button; its states in the strip. The run itself,
 * the journal and the insertion are in InputLogicTest (typedQuestion*).
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class AskCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<String>()
    private val sent = mutableListOf<Triple<String, String, AiTextAction>>()
    private val calls = mutableListOf<String>()
    private var request: CommandAiRequest? = null
    private var provider: String? = "Gemini"

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean { replaced += replacement; return true }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override val aiProvider: String? get() = provider
        override fun aiRequest(command: CommandCall) = request?.takeIf { it.matches(command.text, 1) }
        override fun sendAi(command: CommandCall, request: String, action: AiTextAction): CommandAiRequest {
            sent += Triple(command.text, request, action)
            return CommandAiRequest(command.text, 1, action, request).also { this@AskCommandTest.request = it }
        }
        override fun dropAi(command: CommandCall) { calls += "drop"; request = null }
        override fun insertAi(command: CommandCall): Boolean { calls += "insert"; return true }
        override fun aiDetails(command: CommandCall): Boolean { calls += "details"; return true }
        override fun refresh() { calls += "refresh" }
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

    @Test fun oneChipThatNamesTheProviderAndATapSendsExactlyTheQuestion() {
        val chips = KeyboardCommands.chipsFor(call("x @ki  wie spät ist es "), host)
        assertEquals(listOf(latinIME.getString(R.string.agent_command_ki_chip, "Gemini", "wie spät ist es")), chips.map { it.label.toString() })
        assertTrue(chips.single().label.toString().contains("Gemini")) // says where it goes
        assertEquals(latinIME.getString(R.string.agent_command_ki_open, "Gemini", "wie spät ist es"), chips.single().description)
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, chips.single().kind)
        assertTrue(sent.isEmpty()) // nothing is sent without a tap
        chips.single().onTap()
        assertEquals(listOf(Triple("@ki  wie spät ist es ", "wie spät ist es", AiTextAction.ASK)), sent)
        assertEquals(listOf("refresh"), calls) // the strip shows the waiting chips right away
        assertTrue(replaced.isEmpty()) // the field stays unchanged
    }

    @Test fun aLongQuestionIsShortOnTheChipButSentComplete() {
        val question = "wie hoch ist der höchste Berg Europas und wie heißt er eigentlich genau"
        val chip = KeyboardCommands.chipsFor(call("@ki $question"), host).single()
        val label = chip.label.toString()
        assertTrue(label.endsWith("…") && label.length < latinIME.getString(R.string.agent_command_ki_chip, "Gemini", question).length)
        assertTrue(chip.description.toString().endsWith(question)) // TalkBack reads all of it
        chip.onTap()
        assertEquals(question, sent.single().second)
    }

    @Test fun whileWaitingALoadingChipAndCancel() {
        KeyboardCommands.chipsFor(call("@ki hallo"), host).single().onTap()
        val chips = KeyboardCommands.chipsFor(call("@ki hallo"), host)
        assertEquals(listOf(KeyboardCommands.Chip.Kind.LOADING, KeyboardCommands.Chip.Kind.MUTED), chips.map { it.kind })
        assertEquals(listOf(latinIME.getString(R.string.agent_command_ai_asking), latinIME.getString(R.string.agent_command_ai_cancel)),
            chips.map { it.label.toString() })
        chips[0].onTap() // the loading chip does nothing
        assertEquals(1, sent.size)
        chips[1].onTap()
        assertEquals(listOf("refresh", "drop"), calls)
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, KeyboardCommands.chipsFor(call("@ki hallo"), host).single().kind)
        assertEquals(1, sent.size)
    }

    @Test fun theAnswerIsAChipThatInsertsOnlyOnTapRetryNeedsANewTap() {
        KeyboardCommands.chipsFor(call("@ki hallo"), host).single().onTap()
        val answer = "Eine lange Antwort\nmit zwei Zeilen " + "und viel Text ".repeat(40)
        request!!.state = CommandAiRequest.Answer(answer)
        val chips = KeyboardCommands.chipsFor(call("@ki hallo"), host)
        assertEquals(listOf(KeyboardCommands.Chip.Kind.RESULT, KeyboardCommands.Chip.Kind.MUTED, KeyboardCommands.Chip.Kind.MUTED),
            chips.map { it.kind })
        val label = chips[0].label.toString()
        assertTrue(label.startsWith("Eine lange Antwort mit zwei Zeilen") && label.endsWith("…") && '\n' !in label)
        assertEquals(latinIME.getString(R.string.agent_command_ai_insert_description, answer), chips[0].description)
        assertEquals(listOf("↻", "✕"), chips.drop(1).map { it.label.toString() })
        assertEquals(listOf("refresh"), calls) // nothing inserted yet
        chips[0].onTap()
        assertEquals(listOf("refresh", "insert"), calls)
        chips[1].onTap() // send again: a new request with the same text
        assertEquals(2, sent.size)
        assertEquals(sent[0], sent[1])
        chips[2].onTap()
        assertEquals("drop", calls.last())
        assertTrue(replaced.isEmpty())
    }

    @Test fun aFailureIsOneMutedChipThatOpensTheDetails() {
        KeyboardCommands.chipsFor(call("@ki hallo"), host).single().onTap()
        request!!.state = CommandAiRequest.Failed(R.string.agent_command_ai_no_key, R.string.agent_command_ki_credentials, true)
        val chip = KeyboardCommands.chipsFor(call("@ki hallo"), host).single()
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, chip.kind)
        assertEquals(latinIME.getString(R.string.agent_command_ai_no_key), chip.label.toString())
        assertEquals(latinIME.getString(R.string.agent_command_ki_credentials), chip.description)
        chip.onTap()
        assertEquals("details", calls.last())
        assertEquals(1, sent.size)
    }

    @Test fun aRequestForOtherTextShowsTheSendChip() {
        KeyboardCommands.chipsFor(call("@ki hallo"), host).single().onTap()
        // the same command text and cursor but another question can't happen; another text is another request
        val chip = KeyboardCommands.chipsFor(call("@ki hallo du"), host).single()
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, chip.kind)
    }

    @Test fun withoutAQuestionOrProviderThereIsNoChip() {
        assertTrue(KeyboardCommands.chipsFor(call("x @ki"), host).isEmpty())
        assertTrue(KeyboardCommands.chipsFor(call("x @ki   "), host).isEmpty())
        provider = null
        assertTrue(KeyboardCommands.chipsFor(call("x @ki hallo"), host).isEmpty())
    }

    @Test fun blockedFieldsOfferNothing() {
        val typed = call("@ki hallo")
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(KeyboardCommands.find("@ki hallo", Settings.getValues(), editor))
        assertTrue(AskCommand.chips(host, typed).isEmpty())
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        KeyboardCommands.setCommandEnabled(latinIME, "ki", false)
        assertNull(KeyboardCommands.find("@ki hallo", Settings.getValues(), editor))
        assertTrue(sent.isEmpty())
    }

    @Test fun everyJournaledTextActionHasATitleIncludingTheQuestion() {
        // the run list on the AI settings page must not crash after an @ki run (ASK has no built-in action)
        for (action in AiTextAction.entries)
            assertTrue(latinIME.getString(helium314.keyboard.settings.screens.textActionTitle(action)).isNotBlank())
        assertEquals(R.string.agent_command_ki_task, helium314.keyboard.settings.screens.textActionTitle(AiTextAction.ASK))
    }
}
