// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.CommandNames
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.*

/**
 * Stage 24g: `@lang` and the language words. The enabled languages come from a replaced source and
 * the test host records the switch; InputLogicTest.typedCommandLanguage* covers the real host
 * (`LatinIME.switchToSubtype`) end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class LanguageCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val events = mutableListOf<String>()
    private var replaceWorks = true
    private val switched = mutableListOf<InputMethodSubtype>()

    private fun resource(tag: String): InputMethodSubtype = SubtypeSettings.getResourceSubtypesForLocale(tag.constructLocale()).first()
    private val german by lazy { resource("de") }
    private val english by lazy { resource("en-US") }
    private val spanish by lazy { resource("es") }

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            if (!replaceWorks) return false
            events += "replace ${command.text} by '$replacement'"
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override fun switchSubtype(subtype: InputMethodSubtype): Boolean {
            events += "switch ${subtype.locale().toLanguageTag()}"
            switched += subtype
            return true
        }
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        enable(german, english, spanish)
    }

    @After fun tearDown() {
        LanguageCommand.subtypes = { runCatching { SubtypeSettings.getEnabledSubtypes(true) }.getOrDefault(emptyList()) }
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun enable(vararg subtypes: InputMethodSubtype) {
        LanguageCommand.subtypes = { subtypes.toList() }
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun find(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor)
    private fun chips(before: String) = find(before)?.let { KeyboardCommands.chipsFor(it, host) }.orEmpty()
    private fun labels(before: String) = chips(before).map { it.label.toString() }

    @Test fun isBuiltInWithADescriptionOfItsWords() {
        assertTrue(LanguageCommand in KeyboardCommands.BUILT_IN)
        val description = LanguageCommand.description(latinIME)
        for (part in listOf("@lang", "@sp", "@ger", "@eng", "Deutsch (de, ger)", "English (en, eng)", "Español (es, sp)"))
            assertTrue(part in description, part)
    }

    @Test fun ownerExamplesOfferTheirLanguage() {
        val spanishLabel = labels("x @sp").single()
        assertTrue(spanishLabel.startsWith("🌐 Español ("), spanishLabel)
        for (typed in listOf("sp", "spain", "spanisch", "spnaish", "espanol", "español", "spa"))
            assertEquals(listOf(spanishLabel), labels("x @$typed"), typed)
        for (typed in listOf("ger", "deutsch", "deustch", "germany", "Deutschland"))
            assertTrue(labels("x @$typed").single().startsWith("🌐 Deutsch ("), typed)
        for (typed in listOf("eng", "english", "englisch", "inglés", "usa"))
            assertEquals(listOf("🌐 English (US)"), labels("x @$typed"), typed)
        // a trailing space still offers the chip; another word makes it text again
        assertEquals(listOf(spanishLabel), labels("x @sp "))
        assertTrue(labels("x @sp hola").isEmpty())
        assertTrue(events.isEmpty()) // nothing happens before the tap
    }

    @Test fun aTapRemovesTheCommandAndThenSwitches() {
        chips("Hola @spain").single().onTap()
        assertEquals(listOf("replace @spain by ''", "switch es"), events)
        assertEquals(listOf(spanish), switched)
        // a field that changed in between: the text stays and the language too
        replaceWorks = false
        events.clear()
        chips("x @deutsch").single().onTap()
        assertTrue(events.isEmpty())
        assertEquals(listOf(spanish), switched)
    }

    @Test fun commandNamesAreNeverShadowed() {
        assertEquals("math", (find("@math") as CommandCall).name)
        assertEquals("url", (find("@url") as CommandCall).name)
        assertEquals("ki", (find("@ki") as CommandCall).name)
        assertEquals(listOf("url"), (find("@u") as CommandNames).candidates)
        assertEquals(listOf("emoji"), (find("@e") as CommandNames).candidates)
        // mentions and mail addresses stay text
        for (typed in listOf("@lewin", "@max", "a@spain", "mail@deutsch", "@lewin hallo"))
            assertNull(find(typed), typed)
        // the bare prefix lists one entry for all languages, not every word
        val all = (find("@") as CommandNames).candidates
        assertTrue("lang" in all)
        assertTrue(listOf("sp", "ger", "eng", "deutsch", "spain").none { it in all })
    }

    @Test fun typosOfCommandNamesOnlyComplete() {
        val names = find("x @mtah") as CommandNames
        assertTrue(names.fuzzy)
        val chip = KeyboardCommands.chipsFor(names, host).single()
        assertEquals("@math", chip.label.toString())
        chip.onTap()
        assertEquals(listOf("replace @mtah by '@math '"), events) // completes, never runs
        assertEquals(listOf("translate"), (find("@tranlate") as CommandNames).candidates)
        assertNull(find("@mtah 3+4"))
    }

    @Test fun langListsAndFiltersTheEnabledLanguages() {
        val all = labels("@lang")
        assertEquals(3, all.size)
        assertTrue(all.all { it.startsWith("🌐 ") })
        assertTrue(chips("@lang").none { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE })
        assertEquals(labels("@sp"), labels("@lang spa"))
        assertTrue(labels("@lang xyz").isEmpty())
        assertEquals(all, labels("@sprache")) // a generic word lists all as well
    }

    @Test fun twoLayoutsOfOneLanguageGetOneChipEach() {
        // Russian has several layouts for the same locale in the resources
        val russian = SubtypeSettings.getResourceSubtypesForLocale("ru".constructLocale()).take(2)
        assertEquals(2, russian.size)
        enable(german, *russian.toTypedArray())
        val chips = labels("@russisch")
        assertEquals(2, chips.size)
        assertEquals(2, chips.distinct().size) // the layout tells them apart
        assertTrue(chips.all { it.startsWith("🌐 Русский (RU) · ") }, chips.toString())
        assertEquals(chips, labels("@russian"))
        assertTrue(labels("@deutsch").single().let { " · " !in it })
    }

    @Test fun oneLanguageShowsAHintToTheLanguageSettings() {
        enable(german)
        val chip = chips("@ger").single()
        assertEquals(latinIME.getString(R.string.agent_command_lang_single), chip.label.toString())
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, chip.kind)
        chip.onTap()
        val started: Intent = Shadows.shadowOf(latinIME.application).nextStartedActivity
        assertEquals(SettingsDestination.Languages, started.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(SettingsDestination.Languages in AgentPreferences.SETTINGS_DESTINATIONS)
        assertTrue(events.isEmpty())
        // a language that is not enabled is no command
        assertNull(find("@sp"))
    }

    @Test fun protectedFieldsSwitchedOffCommandAndLearning() {
        assertTrue(KeyboardCommands.isCommandWord("@sp"))
        assertTrue(KeyboardCommands.isCommandWord("@Deutsch,"))
        assertFalse(KeyboardCommands.isCommandWord("@lewin"))
        assertTrue(KeyboardCommands.guardsTextBefore("x @spnaish"))
        assertFalse(KeyboardCommands.guardsTextBefore("x @sp hola"))
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(find("@sp"))
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        fun loadSettings() = Settings.getInstance().loadSettings(latinIME, java.util.Locale.GERMANY,
            helium314.keyboard.latin.InputAttributes(editor, false, latinIME.packageName))
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        loadSettings()
        try {
            assertNull(find("@sp"))
            assertTrue(LanguageCommand.chips(host, CommandCall(0, "@sp", '@', "lang", "", claimed = "sp")).isEmpty())
        } finally {
            latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
            loadSettings()
        }
        assertEquals(1, labels("@sp").size)
        KeyboardCommands.setCommandEnabled(latinIME, LanguageCommand.name, false)
        assertNull(find("@sp"))
        assertNull(find("@deutsch"))
        assertFalse(KeyboardCommands.isCommandWord("@sp"))
        assertFalse("lang" in (find("@") as CommandNames).candidates)
    }
}
