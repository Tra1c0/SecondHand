// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.CommandNames
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
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

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class KeyboardCommandsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val text = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private val calls = mutableListOf<CommandCall>()
    private val calculators = mutableListOf<String>()
    private var replaceWorks = true

    private val fake = object : KeyboardCommands.Command {
        override val name = "math"
        override fun description(context: Context) = "calculator"
        override fun chips(host: KeyboardCommands.Host, call: CommandCall) =
            listOf(KeyboardCommands.Chip("open") { calls += call; host.replace(call, "") })
    }
    private val other = object : KeyboardCommands.Command {
        override val name = "contact"
        override fun description(context: Context) = "contacts"
        override fun chips(host: KeyboardCommands.Host, call: CommandCall) = emptyList<KeyboardCommands.Chip>()
    }
    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = text
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            if (!replaceWorks) return false
            replaced += command.text to replacement
            return true
        }
        override fun openCalculator(command: CommandCall): Boolean { calculators += command.argument; return true }
        override var canOpenCalculator = true
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = listOf(fake, other)
        KeyboardCommands.load(latinIME)
    }

    @After fun tearDown() {
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun find(before: String, editor: EditorInfo = text) = KeyboardCommands.find(before, Settings.getValues(), editor)

    @Test fun namesAreCompletedAndCompleteCommandsAskTheCommand() {
        val names = find("Hallo @") as CommandNames
        val chips = KeyboardCommands.chipsFor(names, host)
        assertEquals(listOf("@contact", "@math"), chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_complete, "@math"), chips[1].description)
        chips[1].onTap()
        assertEquals(listOf("@" to "@math "), replaced)

        val call = find("@math 3+4") as CommandCall
        val callChips = KeyboardCommands.chipsFor(call, host)
        assertEquals(listOf("open"), callChips.map { it.label.toString() })
        assertTrue(calls.isEmpty()) // nothing runs before the tap
        callChips.single().onTap()
        assertEquals(listOf(call), calls)
        assertEquals("@math 3+4" to "", replaced.last())
        // a command without chips leaves the normal strip
        assertTrue(KeyboardCommands.chipsFor(find("@contact") as CommandCall, host).isEmpty())
    }

    @Test fun passwordFieldsAndSwitchedOffCommandsShowNothing() {
        assertNull(find("@math", EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }))
        assertNull(find("@math", EditorInfo().apply { inputType = InputType.TYPE_CLASS_PHONE }))
        assertNull(find("@math", EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }))
        assertNull(find("@math", EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }))
        assertNull(KeyboardCommands.find("@math", null, text))
        KeyboardCommands.setCommandEnabled(latinIME, "math", false)
        assertNull(find("@math"))
        assertEquals(listOf("contact"), (find("@") as CommandNames).candidates)
        KeyboardCommands.setCommandEnabled(latinIME, "math", true)
        KeyboardCommands.setEnabled(latinIME, false)
        assertNull(find("@"))
        assertFalse(KeyboardCommands.isCommandWord("@math"))
    }

    @Test fun ownPrefixAndCommandWords() {
        assertTrue(KeyboardCommands.isCommandWord("@math"))
        assertTrue(KeyboardCommands.isCommandWord("@Math,"))
        assertFalse(KeyboardCommands.isCommandWord("@mathe"))
        assertFalse(KeyboardCommands.isCommandWord(null))
        KeyboardCommands.setPrefix(latinIME, '#')
        assertEquals('#', KeyboardCommands.prefix(latinIME))
        assertNull(find("@math"))
        assertTrue(find("#math") is CommandCall)
        assertTrue(KeyboardCommands.isCommandWord("#math"))
        assertFalse(KeyboardCommands.isCommandWord("@math"))
        assertFailsWith<IllegalArgumentException> { KeyboardCommands.setPrefix(latinIME, '/') }
        latinIME.prefs().edit().putString(AgentPreferences.COMMAND_PREFIX, "/").commit()
        assertEquals('@', KeyboardCommands.prefix(latinIME)) // a stored separator falls back to the default
    }

    @Test fun chipRowHasOneClickableChipPerEntry() {
        var tapped = 0
        val parent = LinearLayout(latinIME)
        val view = KeyboardCommands.chipRow(parent, listOf(
            KeyboardCommands.Chip("@math", "complete") { tapped++ }, KeyboardCommands.Chip("@url") { tapped += 10 }))
        val row = (view as HorizontalScrollView).getChildAt(0) as ViewGroup
        assertEquals(R.id.agent_command_chips, row.id)
        val labels = (0 until row.childCount).map { row.getChildAt(it) as TextView }
        assertEquals(listOf("@math", "@url"), labels.map { it.text.toString() })
        assertEquals("complete", labels[0].contentDescription)
        labels[0].performClick()
        labels[1].performClick()
        assertEquals(11, tapped)
    }

    @Test fun mathOffersTheResultAndTheCalculator() {
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        val call = find("Preis: @math 12*3,5") as CommandCall
        val chips = KeyboardCommands.chipsFor(call, host)
        assertEquals(listOf("12 × 3,5 = 42", latinIME.getString(R.string.agent_command_math_open)), chips.map { it.label.toString() })
        chips[0].onTap()
        assertEquals("@math 12*3,5" to "12 × 3,5 = 42", replaced.single())
        chips[1].onTap()
        assertEquals(1, replaced.size) // the calculator leaves the field alone until "Insert"
        assertEquals(listOf("12*3,5"), calculators)
        // the stored output format is used, and an incomplete calculation only offers the calculator
        latinIME.prefs().edit().putString(AgentPreferences.CALCULATOR_STYLE, "LATEX").commit()
        assertEquals("12 \\cdot 3{,}5 = 42", KeyboardCommands.chipsFor(call, host)[0].label.toString())
        assertEquals(1, KeyboardCommands.chipsFor(find("@math 12*") as CommandCall, host).size)
        assertEquals(1, KeyboardCommands.chipsFor(find("@math") as CommandCall, host).size)
        // without a calculator (or while input is blocked) there is no calculator chip
        host.canOpenCalculator = false
        assertTrue(KeyboardCommands.chipsFor(find("@math") as CommandCall, host).isEmpty())
    }
}
