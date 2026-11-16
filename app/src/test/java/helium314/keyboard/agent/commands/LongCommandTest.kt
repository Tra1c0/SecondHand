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
 * Stage 59: `@ki` and `@translate` take 2000 characters, also over several lines, and the editor is read
 * further back than before only while such a command is typed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class LongCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val normal = CommandSyntax.LOOKBACK + 1

    /** A field with a counter of the characters each read asked for, like `getTextBeforeCursor(n)`. */
    private class Field {
        val text = StringBuilder()
        var cursor = 0
        val requests = mutableListOf<Int>()
        fun type(s: String) { text.insert(cursor, s); cursor += s.length }
        fun backspace(count: Int = 1) { text.delete(cursor - count, cursor); cursor -= count }
        fun before(n: Int): CharSequence { requests += n; return text.substring(maxOf(0, cursor - n), cursor) }
    }

    /** What `LatinIME.tryShowCommandChips` does on every strip update. */
    private fun update(field: Field): Int {
        val n = KeyboardCommands.readLength(field.cursor)
        KeyboardCommands.updateGuard(field.before(n))
        return n
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    @After fun tearDown() { KeyboardCommands.load(latinIME) }

    private fun find(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor)

    // --- syntax through the registry ---

    @Test fun kiAndTranslateTakeTwoThousandCharactersOthersKeepTheirLimits() {
        val long = "a".repeat(CommandSyntax.MAX_AI_ARGUMENT)
        assertEquals(long, (find("@ki $long") as CommandCall).argument)
        assertNull(find("@ki ${long}a"))
        assertNotNull(find("@translate \"${"a".repeat(1990)}\""))
        assertNull(find("@translate \"${"a".repeat(2000)}\""))
        // every other command keeps its limit
        assertNull(find("@math " + "1+".repeat(CommandSyntax.MAX_ARGUMENT)))
        assertNull(find("@contact " + "a".repeat(CommandSyntax.MAX_ARGUMENT + 1)))
        assertNotNull(find("@snip " + "a".repeat(CommandSyntax.MAX_LONG_ARGUMENT)))
        assertNull(find("@snip " + "a".repeat(CommandSyntax.MAX_LONG_ARGUMENT + 1)))
    }

    @Test fun aQuestionWithTwoParagraphsKeepsItsCommand() {
        val question = "Erster Absatz mit einer langen Frage.\n\nZweiter Absatz,\nmit einem Umbruch."
        val found = find("Notiz davor\n@ki $question") as CommandCall
        assertEquals("ki", found.name)
        assertEquals(question, found.argument)
        val translate = find("@translate \"Hallo Anna,\n\nbis morgen\" spanisch") as CommandCall
        assertEquals("translate", translate.name)
        // the chip of the multi-line question is the send chip
        val chips = KeyboardCommands.chipsFor(found, object : KeyboardCommands.Host {
            override val context: Context get() = latinIME
            override val editorInfo: EditorInfo get() = editor
            override val language = "de"
            override fun replace(command: CommandContext, replacement: String) = true
            override fun openCalculator(command: CommandCall) = false
            override val canOpenCalculator = false
            override val aiProvider = "Gemini"
            override fun aiRequest(command: CommandCall): CommandAiRequest? = null
            override fun sendAi(command: CommandCall, request: String, action: AiTextAction): CommandAiRequest =
                CommandAiRequest(command.text, 1, action, request)
        })
        assertEquals(1, chips.size)
    }

    @Test fun aLineBreakStillEndsEveryOtherCommand() {
        for (typed in listOf("@math 3+4\n5", "@url heise.de\nweiter", "@contact Anna\nBerg", "@snip brief\nx", "@conv 5 km\nin", "@lang de\nx")) {
            val found = find(typed)
            assertTrue(found == null || (found as? CommandCall)?.name !in setOf("math", "url", "contact", "snip", "conv", "lang"), typed)
        }
        assertEquals("math", (find("@ki frage\n@math 3+4") as CommandCall).name) // the command on the last line wins
        assertEquals("url", (find("@ki frage\n\nnoch eine Zeile\n@url x") as CommandCall).name)
    }

    @Test fun enterInsideAQuestionIsNeitherSwallowedNorSendsIt() {
        val sent = mutableListOf<String>()
        val host = object : KeyboardCommands.Host {
            override val context: Context get() = latinIME
            override val editorInfo: EditorInfo get() = editor
            override val language = "de"
            override fun replace(command: CommandContext, replacement: String) = true
            override fun openCalculator(command: CommandCall) = false
            override val canOpenCalculator = false
            override val aiProvider = "Gemini"
            override fun aiRequest(command: CommandCall): CommandAiRequest? = null
            override fun sendAi(command: CommandCall, request: String, action: AiTextAction): CommandAiRequest {
                sent += request; return CommandAiRequest(command.text, 1, action, request)
            }
        }
        for (typed in listOf("@ki erste Zeile", "@ki erste Zeile\n\nzweite", "@translate \"hallo\" spanisch", "@translate \"a\nb\"")) {
            val call = find(typed) as CommandCall
            assertFalse(call.name == "ki" && AskCommand.onEnter(host, call), typed)
            assertFalse(call.name == "translate" && TranslateCommand.onEnter(host, call), typed)
            assertFalse(KeyboardCommands.registry.first { it.name == call.name }.onEnter(host, call), typed)
        }
        assertTrue(sent.isEmpty())
    }

    // --- editor reads ---

    @Test fun normalTypingAndShortCommandsNeverAskForMoreThanBefore() {
        val field = Field()
        // 3000 characters of ordinary text, then short commands, every keystroke updates the strip
        for (ch in "Das ist ein ganz normaler Text. ".repeat(95)) { field.type(ch.toString()); assertEquals(normal, update(field)) }
        for (ch in "\n@math 3+4 und @url heise.de dann @contact Anna") { field.type(ch.toString()); assertEquals(normal, update(field)) }
        for (ch in "\n@ki kurze Frage") { field.type(ch.toString()); assertEquals(normal, update(field)) }
        field.backspace(field.cursor) // deleting everything
        assertEquals(normal, update(field))
        assertTrue(field.requests.all { it <= normal }, "largest read ${field.requests.max()}")
    }

    @Test fun onlyAnActiveAiArgumentExtendsTheWindowAndOnlyToItsStart() {
        val field = Field()
        field.type("x".repeat(3000) + " Vorspann ")
        val start = field.cursor
        for (ch in "@ki ") { field.type(ch.toString()); update(field) }
        assertEquals(normal, update(field)) // short question: still the normal window
        var biggest = 0
        for (i in 1..1800) {
            field.type("w")
            val n = update(field)
            biggest = maxOf(biggest, n)
            // never more than the command and one character before it
            assertTrue(n <= maxOf(normal, field.cursor - start + 1) && n >= normal, "n=$n at $i")
            // the engine reads the short window: the words of the long question stay protected
            assertTrue(KeyboardCommands.guardsTextBefore(field.text.substring(field.cursor - normal, field.cursor)), "guard at $i")
            assertTrue(KeyboardCommands.suppressesCorrection(), "suppress at $i")
        }
        assertEquals(1800 + 4 + 1, biggest)
        assertTrue(biggest <= CommandSyntax.LONG_LOOKBACK + 1)
        // lines inside the question keep it going
        field.type("\n\nZweiter Absatz")
        assertEquals(field.cursor - start + 1, update(field))
        // past 2000 characters it is no command any more: the window falls back and nothing is protected
        field.type("z".repeat(300))
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.suppressesCorrection())
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.guardsTextBefore(field.text.substring(field.cursor - normal, field.cursor)))
    }

    @Test fun theLongStateEndsWhenTheCommandIsGoneTheCursorLeavesOrTheFieldChanges() {
        fun typeLong(field: Field) {
            for (ch in "Text @ki ") { field.type(ch.toString()); update(field) }
            field.type("q".repeat(900)) // a paste: the start is known from the keystrokes before
            update(field)
        }
        // the command is edited away
        var field = Field(); typeLong(field)
        assertTrue(update(field) > normal)
        // the edit happens now: the next read still reaches back (nothing knows yet), the parse then drops the state
        field.text.replace(field.text.indexOf("@ki"), field.text.indexOf("@ki") + 3, "ki"); field.cursor = field.text.length
        assertTrue(update(field) > normal) // the read before the parse still was the long one
        assertEquals(normal, update(field)) // not found: dropped
        // the cursor moves in front of the command
        field = Field(); typeLong(field)
        field.cursor = 2
        assertEquals(normal, KeyboardCommands.readLength(field.cursor))
        assertEquals(normal, update(field))
        field.cursor = field.text.length
        assertEquals(normal, update(field)) // and does not come back by itself: the old command is not recognised
        // text is selected (no plain cursor)
        field = Field(); typeLong(field)
        KeyboardCommands.updateGuard(null)
        assertEquals(normal, KeyboardCommands.readLength(field.cursor))
        // a new input view is another field
        field = Field(); typeLong(field)
        assertTrue(KeyboardCommands.readLength(field.cursor) > normal)
        KeyboardCommands.load(latinIME)
        assertEquals(normal, KeyboardCommands.readLength(field.cursor))
        assertFalse(KeyboardCommands.guardsTextBefore(field.text.substring(field.cursor - normal, field.cursor)))
    }

    @Test fun anOldLongCommandOutsideTheShortWindowIsNotRecognised() {
        val field = Field()
        field.type("@ki " + "q".repeat(1200) + " ")
        // never typed here: the start lies outside the window the keyboard reads
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.suppressesCorrection())
        assertNull(find(field.before(normal).toString()))
    }

    @Test fun translateExtendsTheWindowToo() {
        val field = Field()
        field.type("x".repeat(2000) + "\n@translate \"")
        update(field)
        field.type("abc ".repeat(300))
        update(field)
        val n = update(field)
        assertTrue(n > normal && n <= CommandSyntax.LONG_LOOKBACK + 1, "n=$n")
        assertTrue(KeyboardCommands.suppressesCorrection())
    }

    @Test fun readLengthNeverThrows() {
        for (cursor in listOf(-1, 0, 1, Int.MAX_VALUE, Int.MIN_VALUE)) assertTrue(KeyboardCommands.readLength(cursor) >= normal)
    }
}
