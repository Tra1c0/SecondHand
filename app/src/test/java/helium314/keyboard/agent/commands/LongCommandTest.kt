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
import helium314.keyboard.agent.FieldKey
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
            assertTrue(n <= maxOf(normal, field.cursor - start + 1 + KeyboardCommands.START_SLACK) && n >= normal, "n=$n at $i")
            // the engine reads the short window: the words of the long question stay protected
            assertTrue(KeyboardCommands.guardsTextBefore(field.text.substring(field.cursor - normal, field.cursor)), "guard at $i")
            assertTrue(KeyboardCommands.suppressesCorrection(), "suppress at $i")
        }
        assertEquals(1800 + 4 + 1 + KeyboardCommands.START_SLACK, biggest)
        assertTrue(biggest <= CommandSyntax.LONG_LOOKBACK + KeyboardCommands.OVERFLOW_GRACE + KeyboardCommands.START_SLACK + 1)
        // lines inside the question keep it going
        field.type("\n\nZweiter Absatz")
        assertEquals(field.cursor - start + 1 + KeyboardCommands.START_SLACK, update(field))
        // past 2000 characters it is no command any more: nothing is protected, the hint chip says why
        field.type("z".repeat(300))
        update(field)
        assertTrue(KeyboardCommands.overflowHint)
        assertFalse(KeyboardCommands.suppressesCorrection())
        assertFalse(KeyboardCommands.guardsTextBefore(field.text.substring(field.cursor - normal, field.cursor)))
        // and far beyond that the window falls back to the normal one
        field.type("z".repeat(1000))
        update(field)
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.overflowHint)
    }

    private fun typeLong(field: Field, argument: Int = 900) {
        for (ch in "Text @ki ") { field.type(ch.toString()); update(field) }
        field.type("q".repeat(argument)) // a paste: the start is known from the keystrokes before
        update(field)
    }

    @Test fun theLongStateEndsWhenTheCommandIsEditedAway() {
        val field = Field(); typeLong(field)
        assertTrue(update(field) > normal)
        // the edit happens now: the next read still reaches back (nothing knows yet), the parse then drops the state
        field.text.replace(field.text.indexOf("@ki"), field.text.indexOf("@ki") + 3, "ki"); field.cursor = field.text.length
        assertTrue(update(field) > normal)
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.suppressesCorrection())
    }

    @Test fun aStartedNameOrAnotherCommandInsideALongQuestionDoesNotEndIt() {
        // review F1: an "@" typed (and deleted) in the middle of a question longer than the short window
        val field = Field(); typeLong(field, 1000)
        for (typed in listOf("@", "@t", "@math", "@contact Anna", "@ki zweite")) {
            field.type(" ")
            update(field)
            for (ch in typed) { field.type(ch.toString()); update(field) }
            assertTrue(update(field) > normal, "window after $typed") // the start is still known
            field.backspace(typed.length + 1)
            val n = update(field)
            assertTrue(n > normal, "window after deleting $typed")
            val found = KeyboardCommands.find(field.before(n).toString(), Settings.getValues(), editor) as CommandCall
            assertEquals("ki", found.name, typed)
            assertEquals(1000 + 0, found.argument.length, typed)
            assertTrue(KeyboardCommands.suppressesCorrection(), typed)
        }
    }

    @Test fun aSelectionOrACursorInFrontOfTheCommandOnlyMakesItDormant() {
        // review F2
        val field = Field(); typeLong(field)
        val end = field.text.length
        // the cursor moves into the middle of the command: the parse still finds it, the start stays
        field.cursor = end - 100
        assertTrue(update(field) > normal)
        assertTrue(KeyboardCommands.suppressesCorrection())
        field.cursor = end
        assertTrue(update(field) > normal)
        assertTrue(KeyboardCommands.suppressesCorrection())
        // the cursor goes in front of the command: normal reads, nothing protected
        field.cursor = 2
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.suppressesCorrection())
        assertFalse(KeyboardCommands.guardsTextBefore(field.text.substring(0, 2)))
        // and back to the end: the long-window parse decides and finds it again
        field.cursor = end
        assertTrue(KeyboardCommands.readLength(field.cursor) > normal)
        assertTrue(update(field) > normal)
        assertTrue(KeyboardCommands.suppressesCorrection())
        assertTrue(KeyboardCommands.guardsTextBefore(field.text.substring(end - normal, end)))
        // a word is selected (no plain cursor), then replaced: the command goes on
        KeyboardCommands.updateGuard(null)
        assertFalse(KeyboardCommands.guardsTextBefore(field.text.substring(end - normal, end))) // dormant: not protected
        field.backspace(5); field.type("neues")
        assertTrue(update(field) > normal)
        assertTrue(KeyboardCommands.suppressesCorrection())
        // a dormant state that no longer fits is dropped by the parse, not kept for ever
        field.text.setLength(0); field.text.append("ganz anderer Text ".repeat(100)); field.cursor = field.text.length
        update(field)
        assertEquals(normal, update(field))
    }

    @Test fun textDeletedInFrontOfALongQuestionDoesNotLoseIt() {
        // review M3: the start is an absolute offset; the read keeps some slack in front, so the parse finds the
        // command at its new start and re-anchors
        for (deleted in listOf(1, 3, 40, KeyboardCommands.START_SLACK)) {
            val field = Field()
            field.type("v".repeat(300) + " ")
            for (ch in "@ki ") { field.type(ch.toString()); update(field) }
            field.type("q".repeat(1000)); update(field)
            field.cursor = deleted; field.backspace(deleted); update(field) // at the start of the field, deleting there
            field.cursor = field.text.length
            val n = update(field)
            assertTrue(n > normal, "deleted=$deleted")
            val found = KeyboardCommands.find(field.before(n).toString(), Settings.getValues(), editor) as CommandCall
            assertEquals("ki", found.name, "deleted=$deleted")
            assertEquals(1000, found.argument.length, "deleted=$deleted")
            assertTrue(KeyboardCommands.suppressesCorrection(), "deleted=$deleted")
            // and it keeps working afterwards: the next keystroke still reads back to the new start
            field.type("w")
            assertTrue(update(field) > normal, "deleted=$deleted")
            assertTrue(KeyboardCommands.suppressesCorrection(), "deleted=$deleted")
        }
        // inserting in front works too (the parse finds the command further right)
        val inserted = Field()
        inserted.type("v".repeat(300) + " ")
        for (ch in "@ki ") { inserted.type(ch.toString()); update(inserted) }
        inserted.type("q".repeat(1000)); update(inserted)
        inserted.cursor = 5; inserted.type("neu eingefuegt "); inserted.cursor = inserted.text.length
        assertTrue(update(inserted) > normal)
        assertTrue(KeyboardCommands.suppressesCorrection())
        // more than the slack deleted in front: the head is outside the window, the state ends (documented, P244)
        val tooMuch = Field()
        tooMuch.type("v".repeat(300) + " ")
        for (ch in "@ki ") { tooMuch.type(ch.toString()); update(tooMuch) }
        tooMuch.type("q".repeat(1000)); update(tooMuch)
        val gone = KeyboardCommands.START_SLACK + 10
        tooMuch.cursor = gone; tooMuch.backspace(gone); update(tooMuch)
        tooMuch.cursor = tooMuch.text.length
        update(tooMuch)
        assertEquals(normal, update(tooMuch))
        assertFalse(KeyboardCommands.suppressesCorrection())
    }

    @Test fun theSameFieldRestartedKeepsTheCommandAnotherFieldDropsIt() {
        val first = FieldKey("com.example", 7, InputType.TYPE_CLASS_TEXT)
        KeyboardCommands.load(latinIME, first)
        var field = Field(); typeLong(field)
        assertTrue(KeyboardCommands.readLength(field.cursor) > normal)
        KeyboardCommands.load(latinIME, first) // the app restarted the same field, or the screen turned
        assertTrue(KeyboardCommands.readLength(field.cursor) > normal)
        assertTrue(update(field) > normal)
        assertTrue(KeyboardCommands.suppressesCorrection())
        KeyboardCommands.load(latinIME, FieldKey("com.example", 8, InputType.TYPE_CLASS_TEXT)) // another field
        assertEquals(normal, KeyboardCommands.readLength(field.cursor))
        assertFalse(KeyboardCommands.guardsTextBefore(field.text.substring(field.cursor - normal, field.cursor)))
        KeyboardCommands.load(latinIME, first); field = Field(); typeLong(field)
        KeyboardCommands.load(latinIME, FieldKey("other.app", 7, InputType.TYPE_CLASS_TEXT))
        assertEquals(normal, KeyboardCommands.readLength(field.cursor))
        KeyboardCommands.load(latinIME, first); field = Field(); typeLong(field)
        KeyboardCommands.load(latinIME, null) // a field that cannot be named is another field
        assertEquals(normal, KeyboardCommands.readLength(field.cursor))
    }

    @Test fun twoEmptyLinesEndTheQuestionOneDoesNot() {
        val field = Field()
        for (ch in "@ki ") { field.type(ch.toString()); update(field) }
        field.type("q".repeat(700)); update(field)
        field.type("\n\n" + "r".repeat(300)); // one empty line: paragraphs
        val n = update(field)
        assertTrue(n > normal)
        assertEquals("ki", (KeyboardCommands.find(field.before(n).toString(), Settings.getValues(), editor) as CommandCall).name)
        assertTrue(KeyboardCommands.suppressesCorrection())
        // a second empty line in a row: the question ends, what follows is normal text again
        field.type("\n\n\nDanach kommt ganz normaler Text.")
        update(field)
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.suppressesCorrection())
        assertNull(KeyboardCommands.find(field.before(normal).toString(), Settings.getValues(), editor))
        assertFalse(KeyboardCommands.overflowHint)
        // the same for the text of @translate
        val other = Field()
        for (ch in "@translate \"") { other.type(ch.toString()); update(other) }
        other.type("abc ".repeat(200)); update(other)
        other.type("\n \n\" spanisch"); // one empty line
        assertTrue(update(other) > normal)
        other.type("\n\n\nweiter")
        update(other)
        assertEquals(normal, update(other))
    }

    @Test fun aQuestionBeyondTheLimitShowsAHintNormalTextAfterAnEndedOneDoesNot() {
        val field = Field()
        for (ch in "Vorher @ki ") { field.type(ch.toString()); update(field) }
        for (i in 1..(CommandSyntax.MAX_AI_ARGUMENT / 100)) { field.type("x".repeat(100)); update(field) }
        assertFalse(KeyboardCommands.overflowHint) // exactly the limit still is a command
        assertNotNull(KeyboardCommands.find(field.before(KeyboardCommands.readLength(field.cursor)).toString(), Settings.getValues(), editor))
        field.type("y"); val n = update(field)
        assertTrue(n > normal)
        assertTrue(KeyboardCommands.overflowHint) // 2001: no command, one hint chip
        assertNull(KeyboardCommands.find(field.before(n).toString(), Settings.getValues(), editor))
        assertNotEquals(0, latinIME.getString(helium314.keyboard.latin.R.string.agent_command_ai_over_limit, CommandSyntax.MAX_AI_ARGUMENT).length)
        field.backspace(1); update(field)
        assertFalse(KeyboardCommands.overflowHint)
        // far beyond (limit + grace): the window stops growing, the hint is gone
        field.type("z".repeat(CommandSyntax.MAX_AI_ARGUMENT)); update(field)
        assertEquals(normal, update(field))
        assertFalse(KeyboardCommands.overflowHint)
        // normal text after a question that two empty lines ended is no overflow, however long it gets
        val ended = Field()
        for (ch in "@ki ") { ended.type(ch.toString()); update(ended) }
        ended.type("Frage\n\n\n"); update(ended)
        for (i in 1..30) { ended.type("normal ".repeat(40)); assertEquals(normal, update(ended)); assertFalse(KeyboardCommands.overflowHint) }
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
        assertTrue(n > normal && n <= CommandSyntax.LONG_LOOKBACK + KeyboardCommands.START_SLACK + 1, "n=$n")
        assertTrue(KeyboardCommands.suppressesCorrection())
    }

    @Test fun readLengthNeverThrows() {
        for (cursor in listOf(-1, 0, 1, Int.MAX_VALUE, Int.MIN_VALUE)) assertTrue(KeyboardCommands.readLength(cursor) >= normal)
    }
}
