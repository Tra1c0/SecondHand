// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.CommandSyntax
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.textActionTitle
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

/**
 * `@translate` (Variante A): the chip names the provider and the style and its tap sends exactly the
 * request; the run and the insertion are in InputLogicTest (typedTranslation*), the states of the
 * chips after the tap in AskCommandTest (shared [AiCommandChips]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class TranslateCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<String>()
    private val translated = mutableListOf<Pair<String, String>>()
    private val asked = mutableListOf<String>()
    private var request: CommandAiRequest? = null

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean { replaced += replacement; return true }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override val aiProvider = "Gemini"
        override fun aiRequest(command: CommandCall) = request?.takeIf { it.matches(command.text, 1) }
        override fun sendAi(command: CommandCall, request: String, action: AiTextAction): CommandAiRequest {
            if (action == AiTextAction.TRANSLATE_STYLED) translated += command.text to request else asked += request
            return CommandAiRequest(command.text, 1, action, request).also { this@TranslateCommandTest.request = it }
        }
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    @After fun tearDown() { KeyboardCommands.load(latinIME) }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall

    @Test fun isBuiltInAndProtectsItsArgument() {
        assertTrue(TranslateCommand in KeyboardCommands.BUILT_IN)
        assertTrue(TranslateCommand.protectsArgument)
        assertTrue(KeyboardCommands.guardsTextBefore("x @translate \"wann treffen wir uns\" spanisch"))
        assertTrue(KeyboardCommands.guardsTextBefore("x @translate \"wann treffen")) // still typing the text
        assertTrue(TranslateCommand.description(latinIME).contains("@translate"))
    }

    @Test fun oneChipWithProviderAndStyleAndATapSendsExactlyTheRequest() {
        val typed = "@translate \"wann treffen wir uns morgen du geile sau\" natürliches slang spanisch"
        val chips = KeyboardCommands.chipsFor(call("x $typed"), host)
        assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_chip, "Gemini", "natürliches slang spanisch")),
            chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_translate_open, "Gemini", "natürliches slang spanisch"), chips.single().description)
        assertTrue(translated.isEmpty()) // nothing is sent without a tap
        chips.single().onTap()
        assertEquals(listOf(typed to "Translation style:\nnatürliches slang spanisch\n\nText to translate:\nwann treffen wir uns morgen du geile sau"),
            translated)
        assertTrue(replaced.isEmpty()) // the field stays unchanged until the answer is tapped
        assertTrue(asked.isEmpty())
        // the same command now waits for its translation
        assertEquals(latinIME.getString(R.string.agent_command_ai_translating),
            KeyboardCommands.chipsFor(call("x $typed"), host).first().label.toString())
    }

    @Test fun withoutAStyleTheChipSaysNaturalEnglish() {
        val chips = KeyboardCommands.chipsFor(call("@translate „bis morgen“"), host)
        assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_chip, "Gemini", "natural English")), chips.map { it.label.toString() })
        chips.single().onTap()
        assertEquals("Translation style:\nnatural English\n\nText to translate:\nbis morgen", translated.single().second)
    }

    @Test fun aLongStyleIsShortOnTheChipButCompleteInTheRequest() {
        val style = "sehr locker wie unter Freunden in Madrid mit viel Slang und Emojis"
        val chip = KeyboardCommands.chipsFor(call("@translate «hallo» $style"), host).single()
        val label = chip.label.toString()
        assertTrue(label.endsWith("…") && label.length < latinIME.getString(R.string.agent_command_translate_chip, "Gemini", style).length)
        chip.onTap()
        assertTrue(translated.single().second.startsWith("Translation style:\n$style\n\n"))
    }

    @Test fun withoutQuotesOnlyAHintThatRunsNothing() {
        for (typed in listOf("@translate", "@translate wann treffen wir uns spanisch", "@translate \"wann treffen", "@translate \"\" spanisch")) {
            val chips = KeyboardCommands.chipsFor(call(typed), host)
            assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_hint)), chips.map { it.label.toString() }, typed)
            chips.single().onTap()
        }
        assertTrue(translated.isEmpty() && replaced.isEmpty() && asked.isEmpty())
    }

    @Test fun takesLongerArgumentsThanOtherCommandsButNotUnlimited() {
        val text = "a".repeat(CommandSyntax.MAX_LONG_ARGUMENT - 20)
        val typed = "@translate \"$text\" spanisch"
        assertTrue(typed.length - "@translate ".length > CommandSyntax.MAX_ARGUMENT)
        val chip = KeyboardCommands.chipsFor(call(typed), host).single()
        chip.onTap()
        assertTrue(translated.single().second.endsWith(text))
        assertNull(KeyboardCommands.find("@translate \"${"a".repeat(CommandSyntax.MAX_LONG_ARGUMENT)}\"", Settings.getValues(), editor))
        // other commands keep the short limit
        assertNull(KeyboardCommands.find("@ki " + "a".repeat(CommandSyntax.MAX_ARGUMENT + 1), Settings.getValues(), editor))
    }

    @Test fun blockedFieldsOfferNothing() {
        val typed = call("@translate \"hallo\" spanisch")
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(KeyboardCommands.find("@translate \"hallo\" spanisch", Settings.getValues(), editor))
        assertTrue(TranslateCommand.chips(host, typed).isEmpty())
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        KeyboardCommands.setCommandEnabled(latinIME, "translate", false)
        assertNull(KeyboardCommands.find("@translate \"hallo\" spanisch", Settings.getValues(), editor))
        assertTrue(translated.isEmpty())
    }

    @Test fun theRunListHasATitleForTheTranslation() {
        // the run list on the AI settings page must not crash after a @translate run (no built-in action)
        assertEquals(R.string.agent_command_translate_task, textActionTitle(AiTextAction.TRANSLATE_STYLED))
        assertEquals(R.string.agent_command_ki_task, textActionTitle(AiTextAction.ASK))
        // the panel's own translation keeps its built-in title
        assertNotEquals(R.string.agent_command_translate_task, textActionTitle(AiTextAction.TRANSLATE))
    }
}
