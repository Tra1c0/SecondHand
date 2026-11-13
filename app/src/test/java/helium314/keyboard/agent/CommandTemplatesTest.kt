// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.agentkeyboard.core.AiTextAction
import dev.agentkeyboard.core.CivilDate
import dev.agentkeyboard.core.ClockTime
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.CommandNames
import dev.agentkeyboard.core.DateFormatting
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.CommandAiRequest
import helium314.keyboard.agent.commands.TranslateCommand
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
import java.util.TimeZone
import kotlin.test.*

/**
 * Command templates: a command without argument shows its own chips, then its argument patterns;
 * a tap on a template only writes "@name <template>" in place of the typed command, and the
 * command then shows its chips for that argument as usual.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class CommandTemplatesTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val text = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private var editor = text
    private val replaced = mutableListOf<Pair<String, String>>()
    private val sent = mutableListOf<String>()
    private val launched = mutableListOf<Intent>()

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override var language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            replaced += command.text to replacement
            return true
        }
        override fun openCalculator(command: CommandCall) = true
        override var canOpenCalculator = false
        override val aiProvider = "Gemini"
        override fun sendAi(command: CommandCall, request: String, action: AiTextAction): CommandAiRequest {
            sent += request
            return CommandAiRequest(command.text, 1, action, request)
        }
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        // Thursday, 12 November 2026, 18:45 in Berlin
        DateCommand.clock = { NOW }
        DateCommand.zone = { TimeZone.getTimeZone("Europe/Berlin") }
        EventCommand.launcher = SearchCommand.Launcher { _, intent -> launched += intent; true }
    }

    @After fun tearDown() {
        DateCommand.clock = { System.currentTimeMillis() }
        DateCommand.zone = { TimeZone.getDefault() }
        EventCommand.launcher = SearchCommand.SYSTEM_LAUNCHER
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun find(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor)
    private fun chips(before: String) = KeyboardCommands.chipsFor(find(before)!!, host)
    private fun labels(before: String) = chips(before).map { it.label.toString() }
    private fun templates(before: String) = chips(before).filter { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE }
    private fun templateLabels(before: String) = templates(before).map { it.label.toString() }

    /** Taps the template [label] of [before] and returns the text that replaces the typed command. */
    private fun tap(before: String, label: String): String {
        replaced.clear()
        templates(before).single { it.label.toString() == label }.onTap()
        val (typed, replacement) = replaced.single()
        assertEquals((find(before) as CommandCall).text, typed) // exactly the typed command
        assertTrue(sent.isEmpty() && launched.isEmpty()) // a template runs nothing
        return replacement
    }

    @Test fun characterGroupsAreTemplatesAndAGroupShowsAllItsCharacters() {
        val groups = listOf("pfeile", "mathe", "währung", "anführungszeichen", "häkchen", "typografie", "symbole", "griechisch")
        assertEquals(groups, labels("@zeichen")) // nothing found for an empty name, so only the templates
        assertEquals(groups, labels("Hallo @zeichen "))
        val first = templates("@zeichen").first()
        assertEquals(latinIME.getString(R.string.agent_command_template, "pfeile"), first.description)
        assertEquals("@zeichen pfeile", tap("Hallo @zeichen ", "pfeile"))
        assertEquals("@zeichen pfeile", tap("@zeichen", "pfeile"))

        val arrows = chips("Hallo @zeichen pfeile")
        assertEquals(listOf("→", "←", "↑", "↓", "↔", "↕", "↗", "↘", "↩", "⇒", "⇐", "⇔"), arrows.map { it.label.toString() })
        assertTrue(arrows.none { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE }) // an argument: no templates
        replaced.clear()
        arrows[3].onTap()
        assertEquals(listOf("@zeichen pfeile" to "↓"), replaced)
        assertTrue(labels("@zeichen währung").containsAll(listOf("€", "$", "£", "¥")))
        assertTrue(labels("@zeichen typografie").containsAll(listOf("–", "—", "…", "·", "•")))
        assertTrue(labels("@zeichen symbole").containsAll(listOf("§", "©", "®", "™", "°", "‰")))
        for (group in groups) assertTrue(labels("@zeichen ${tap("@zeichen", group).substringAfter(' ')}").isNotEmpty(), group)
        // the name search stays as it was
        assertEquals(listOf("€"), labels("@zeichen euro"))
    }

    @Test fun emojiGroupsAreTemplates() {
        val groups = listOf("gesichter", "herzen", "hände", "tiere", "essen", "natur", "wetter", "sport", "reisen", "symbole")
        assertEquals(groups, labels("@emoji"))
        assertEquals("@emoji tiere", tap("@emoji ", "tiere"))
        val animals = labels("@emoji tiere")
        assertEquals(listOf("🐶", "🐱", "🐭", "🐰"), animals.take(4))
        assertTrue("🦄" in animals)
        for (group in groups) assertTrue(labels("@emoji ${tap("@emoji", group).substringAfter(' ')}").isNotEmpty(), group)
    }

    @Test fun conversionTemplatesWriteThePairAndTheNumberIsTypedAfterIt() {
        assertEquals(listOf("oz in g", "°F in °C", "mi in km", "cup in ml", "lb in kg", "in in cm", "ft in m", "km/h in mph"),
            labels("@conv"))
        val written = tap("@conv ", "oz in g")
        assertEquals("@conv oz in g ", written)
        // the pair alone converts one unit, the number typed after it is the value
        assertEquals(listOf("1 oz = 28,35 g"), labels(written))
        val five = chips("${written}5")
        assertEquals(listOf("5 oz = 141,7 g"), five.map { it.label.toString() })
        replaced.clear()
        five.single().onTap()
        assertEquals(listOf("@conv oz in g 5" to "5 oz = 141,7 g"), replaced)
        assertEquals(listOf("100 °F = 37,78 °C"), labels("${tap("@conv", "°F in °C")}100"))
        assertEquals(listOf("12 in = 30,48 cm"), labels("${tap("@conv", "in in cm")}12"))
        assertEquals(listOf("100 km/h = 62,14 mph"), labels("${tap("@conv", "km/h in mph")}100"))
        for (pair in ConvertCommand.PAIRS) assertEquals(1, labels(tap("@conv", pair)).size, pair)
        host.language = "en"
        assertEquals(listOf("2.5 lb = 1.134 kg"), labels("${tap("@conv", "lb in kg")}2.5"))
    }

    @Test fun dateShowsTodayFirstThenTheDaysAsTemplates() {
        val today = labels("@datum")
        val templates = listOf("heute", "morgen", "übermorgen", "+7", "freitag")
        assertEquals(DateCommand.texts("", "de") + templates, today)
        assertEquals("@datum morgen", tap("@datum", "morgen"))
        assertEquals("13.11.2026", labels("@datum morgen").first())
        assertEquals("14.11.2026", labels("@datum übermorgen").first())
        assertEquals("19.11.2026", labels("@datum +7").first())
        assertEquals("13.11.2026", labels("@datum freitag").first())
        assertEquals("12.11.2026", labels("@datum heute").first())
        host.language = "en"
        assertEquals(listOf("today", "tomorrow", "+7", "friday"), templateLabels("@datum"))
        assertEquals("11/13/2026", labels("@datum ${tap("@datum", "friday").substringAfter(' ')}").first())
        host.language = "es"
        assertEquals(listOf("hoy", "mañana", "+7", "viernes"), templateLabels("@datum"))
        assertEquals("13/11/2026", labels("@datum ${tap("@datum", "mañana").substringAfter(' ')}").first())
    }

    @Test fun eventTemplatesWriteADayAndATimeAndTheTitleFollows() {
        assertEquals(listOf("morgen 18 Uhr", "heute abend", "samstag halb acht"), labels("@termin"))
        val written = tap("@termin", "morgen 18 Uhr")
        assertEquals("@termin morgen 18 Uhr ", written)
        val de = DateFormatting.Language.DE
        assertEquals(listOf(latinIME.getString(R.string.agent_command_event_chip_untitled,
            DateFormatting.short(CivilDate(2026, 11, 13), ClockTime(18, 0), de))), labels(written))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_event_chip,
            DateFormatting.short(CivilDate(2026, 11, 13), ClockTime(18, 0), de), "Zahnarzt")), labels("${written}Zahnarzt"))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_event_chip,
            DateFormatting.short(CivilDate(2026, 11, 14), ClockTime(19, 30), de), "Kino")),
            labels("${tap("@termin", "samstag halb acht")}Kino"))
        val evening = labels("${tap("@termin", "heute abend")}Essen").single()
        assertTrue(evening.contains(DateFormatting.short(CivilDate(2026, 11, 12), null, de)), evening)
        assertTrue(evening.endsWith("Essen"), evening)
        for (language in listOf("en", "es")) {
            host.language = language
            for (template in templateLabels("@termin"))
                assertEquals(1, labels("${tap("@termin", template)}Kino").size, "$language: $template")
        }
        host.language = "en"
        assertEquals(listOf(latinIME.getString(R.string.agent_command_event_chip,
            DateFormatting.short(CivilDate(2026, 11, 14), ClockTime(19, 30), DateFormatting.Language.EN), "Kino")),
            labels("${tap("@termin", "saturday half past seven")}Kino"))
        host.language = "es"
        assertEquals(listOf(latinIME.getString(R.string.agent_command_event_chip,
            DateFormatting.short(CivilDate(2026, 11, 14), ClockTime(19, 30), DateFormatting.Language.ES), "Kino")),
            labels("${tap("@termin", "sábado a las siete y media")}Kino"))
        assertTrue(launched.isEmpty())
    }

    @Test fun translateTemplatesOpenAQuoteThatShowsTheHintUntilItIsClosed() {
        // the hint first (the command's own chip), then the templates
        assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_hint), "spanisch \"…\"", "englisch \"…\"",
            "natürliches slang spanisch \"…\"", "deutsch \"…\""), labels("@translate"))
        val written = tap("@translate ", "spanisch \"…\"")
        assertEquals("@translate spanisch \"", written)
        // still open: the hint, no send chip and no error
        assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_hint)), labels(written))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_hint)), labels("${written}hola"))
        val send = chips("${written}hola\"")
        assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_chip, "Gemini", "spanisch")), send.map { it.label.toString() })
        assertTrue(sent.isEmpty()) // nothing is sent before the tap on the send chip
        assertEquals("@translate natürliches slang spanisch \"", tap("@translate", "natürliches slang spanisch \"…\""))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_translate_chip, "Gemini", "natürliches slang spanisch")),
            labels("@translate natürliches slang spanisch \"wie geht's\""))
        assertTrue(TranslateCommand.templates(host).isNotEmpty())
    }

    @Test fun mathShowsTheCalculatorFirstThenItsTemplates() {
        host.canOpenCalculator = true
        val chips = chips("@math")
        assertEquals(listOf(latinIME.getString(R.string.agent_command_math_open), "√", "π"), chips.map { it.label.toString() })
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, chips[0].kind)
        assertEquals("@math √", tap("@math", "√"))
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, chips("@math √16").first().kind)
        assertEquals("√16 = 4", labels("@math √16").first())
        assertEquals("@math π", tap("@math ", "π"))
        assertTrue(labels("@math π").first().startsWith("π ≈ 3,14"))
    }

    @Test fun aNameChipCompletesTheNameAndThenTheTemplatesShow() {
        val names = find("Hallo @zei") as CommandNames
        val chip = KeyboardCommands.chipsFor(names, host).single()
        chip.onTap()
        assertEquals(listOf("@zei" to "@zeichen "), replaced)
        assertEquals("pfeile", templateLabels("Hallo @zeichen ").first())
        // the bare prefix lists names only, no templates
        assertTrue(KeyboardCommands.chipsFor(find("@")!!, host).none { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE })
    }

    @Test fun anotherPrefixIsKeptAndCommandsWithoutTemplatesStayEmpty() {
        KeyboardCommands.setPrefix(latinIME, '#')
        assertEquals("#conv oz in g ", tap("x #conv", "oz in g"))
        KeyboardCommands.setPrefix(latinIME, '@')
        for (name in listOf("such", "ki", "qr")) assertTrue(chips("@$name").isEmpty(), name)
        // only an empty argument shows templates
        assertTrue(templates("@zeichen pf").isEmpty())
        assertTrue(templates("@conv 5 oz").isEmpty())
        assertTrue(templates("@datum morgen").isEmpty())
    }

    @Test fun protectedFieldsShowNoTemplates() {
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(find("@zeichen"))
        val call = KeyboardCommands.find("@zeichen", Settings.getValues(), text) as CommandCall
        assertTrue(KeyboardCommands.templateChips(CharacterCommand, call, host).isEmpty())
        assertTrue(KeyboardCommands.chipsFor(call, host).isEmpty())
    }

    @Test fun aTemplateChipIsOutlinedAndNamedForTalkBack() {
        val parent = LinearLayout(latinIME)
        val chips = templates("@zeichen").take(2) + KeyboardCommands.Chip("→") {}
        val row = (KeyboardCommands.chipRow(parent, chips) as HorizontalScrollView).getChildAt(0) as ViewGroup
        val views = (0 until row.childCount).map { row.getChildAt(it) as TextView }
        assertEquals(listOf("pfeile", "mathe", "→"), views.map { it.text.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_template, "mathe"), views[1].contentDescription)
        assertEquals(android.graphics.Color.TRANSPARENT, (views[0].background as GradientDrawable).color?.defaultColor)
        assertNotEquals(android.graphics.Color.TRANSPARENT, (views[2].background as GradientDrawable).color?.defaultColor)
        replaced.clear()
        views[1].performClick()
        assertEquals(listOf("@zeichen" to "@zeichen mathe"), replaced)
    }

    private companion object {
        const val NOW = 1_794_505_500_000L // 2026-11-12T17:45Z, as in DatePlaceCommandsTest
    }
}
