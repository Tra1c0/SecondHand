// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.text.InputType
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.CommandGrammar.AppName
import dev.agentkeyboard.core.CommandGrammar.Role
import dev.agentkeyboard.core.CommandNames
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.Memory
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.test.*

/**
 * Stage 52: the unified command system with a test host (the enabled languages and the apps come
 * from replaced sources). InputLogicTest.unifiedCommand* runs it with the real host and engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class CommandSystemTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = field("com.example.chat")
    private val replaced = mutableListOf<Pair<String, String>>()
    private val switched = mutableListOf<InputMethodSubtype>()
    private var current: InputMethodSubtype? = null
    private var cursor = 100_000 // every replacement moves it, as in the field
    private var refreshed = 0
    private var canReplace = true
    private var now = 1_000L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

    private fun field(pkg: String?, type: Int = InputType.TYPE_CLASS_TEXT) = EditorInfo().apply { inputType = type; packageName = pkg }
    private fun resource(tag: String): InputMethodSubtype = SubtypeSettings.getResourceSubtypesForLocale(tag.constructLocale()).first()
    private val german by lazy { resource("de") }
    private val english by lazy { resource("en-US") }
    private val spanish by lazy { resource("es") }
    private fun stored(subtype: InputMethodSubtype) = subtype.toSettingsSubtype().toPref()

    private val whatsapp = AppName("com.whatsapp", "WhatsApp")
    private val business = AppName("com.whatsapp.w4b", "WhatsApp Business")
    private val duolingo = AppName("com.duolingo", "Duolingo")
    private val signal = AppName("org.thoughtcrime.securesms", "Signal")
    private val chat = AppName("com.example.chat", "Chat")

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            if (!canReplace) return false
            replaced += command.text to replacement
            this@CommandSystemTest.cursor += replacement.length - command.text.length
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override val cursor: Int get() = this@CommandSystemTest.cursor
        override fun refresh() { refreshed++ }
        override fun switchSubtype(subtype: InputMethodSubtype): Boolean { switched += subtype; current = subtype; return true }
        override val currentSubtype: InputMethodSubtype? get() = current
        override val canReplaceNow: Boolean get() = canReplace
    }

    private val defaultClock = CommandSystem.clock
    private val defaultSchedule = CommandSystem.schedule
    private val defaultStore = CommandSystem.snippetStore

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        CommandAppSettings.clearForTest(latinIME)
        Memory.excludedApps(latinIME).forEach { Memory.setExcluded(latinIME, it, false) }
        InstalledApps.source = InstalledApps.Source { listOf(whatsapp, business, duolingo, signal, chat) }
        LanguageCommand.subtypes = { listOf(german, english, spanish) }
        // enabled for the keyboard too: a stored language is resolved as HeliBoard does
        for (subtype in listOf(german, english, spanish)) SubtypeSettings.addEnabledSubtype(latinIME.prefs(), subtype)
        current = english
        CommandSystem.clock = { now }
        CommandSystem.schedule = { delay, block -> scheduled += delay to block }
        CommandSystem.snippetStore = { SnippetStore(it) { true } }
        SnipCommand.source = SnipCommand.Source { SnippetStore(it) { true }.read() }
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    @After fun tearDown() {
        CommandSystem.dropUndo()
        CommandSystem.clock = defaultClock
        CommandSystem.schedule = defaultSchedule
        CommandSystem.snippetStore = defaultStore
        SnipCommand.source = SnipCommand.Source { SnippetStore(it).read() }
        runCatching { SnippetStore(latinIME) { true }.read().forEach { SnippetStore(latinIME) { true }.delete(it.name) } }
        InstalledApps.source = InstalledApps.SYSTEM
        LanguageCommand.subtypes = LanguageCommand.defaultSubtypes
        for (subtype in listOf(german, spanish)) SubtypeSettings.removeEnabledSubtype(latinIME, subtype)
        CommandAppSettings.clearForTest(latinIME)
        KeyboardCommands.load(latinIME)
    }

    private fun find(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor)
    private fun call(before: String) = find(before) as CommandCall
    private fun chips(before: String) = find(before)?.let { KeyboardCommands.chipsFor(it, host) }.orEmpty()
    private fun labels(before: String) = chips(before).map { it.label.toString() }
    private fun enter(before: String) = (find(before) as? CommandCall)?.let { call ->
        KeyboardCommands.registry.first { it.name == call.name }.onEnter(host, call) }
    private fun string(id: Int, vararg args: Any) = latinIME.getString(id, *args)
    private val dark get() = CommandColors.isDark(KeyboardCommands.chipBackground())
    private fun colour(role: Role) = CommandColors.of(role, dark)
    private fun label(subtype: InputMethodSubtype) = LanguageCommand.label(latinIME, subtype, LanguageCommand.subtypes()).removePrefix("🌐 ")

    /** The colour of the span that covers [part] of [label], or null. */
    private fun colourOf(label: CharSequence, part: String): Int? {
        val spanned = label as Spanned
        val start = label.toString().indexOf(part)
        assertTrue(start >= 0, "$part in $label")
        return spanned.getSpans(start, start + part.length, ForegroundColorSpan::class.java)
            .firstOrNull { spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= start + part.length }?.foregroundColor
    }

    @Test fun aChainIsOneCommandPreviewedInColouredPlainWords() {
        val typed = "@lang whatsapp german; duolingo spanish; @guard signal off"
        val found = call("Hi $typed")
        assertEquals("lang", found.name)
        assertEquals(typed, found.text)
        assertEquals(3, found.start)
        assertTrue(KeyboardCommands.guardsTextBefore("Hi $typed")) // nothing in it is corrected
        val preview = chips("Hi $typed").single()
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, preview.kind)
        val text = preview.label
        for (part in listOf("WhatsApp", label(german), "Duolingo", label(spanish), string(R.string.agent_unified_area_guard), "Signal"))
            assertTrue(part in text.toString(), "$part in $text")
        assertEquals(colour(Role.AREA), colourOf(text, string(R.string.agent_unified_area_lang)))
        assertEquals(colour(Role.TARGET), colourOf(text, "WhatsApp"))
        assertEquals(colour(Role.VALUE), colourOf(text, label(german)))
        assertEquals(colour(Role.SEPARATOR), colourOf(text, ";"))
        assertEquals(colour(Role.ACTION), colourOf(text, string(R.string.agent_unified_off)))
        assertEquals(6, Role.entries.map { colour(it) }.distinct().size) // one colour per role
        assertEquals(6, Role.entries.map { CommandColors.of(it, !dark) }.distinct().size)
        // nothing changes before the tap
        assertTrue(CommandAppSettings.languages(latinIME).isEmpty())
        assertTrue(CommandAppSettings.guards(latinIME).isEmpty())
        assertTrue(replaced.isEmpty())
        // a word that names nothing is coloured as unknown and the preview cannot run
        val unknown = chips("@guard banking off").single()
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, unknown.kind)
        assertEquals(colour(Role.UNKNOWN), colourOf(unknown.label, "banking?"))
        unknown.onTap()
        assertEquals(string(R.string.agent_unified_unknown_toast, "banking"), ShadowToast.getTextOfLatestToast())
        assertTrue(replaced.isEmpty())
        assertTrue(CommandAppSettings.guards(latinIME).isEmpty())
    }

    @Test fun aTapRunsTheChainOnceAndUndoPutsEverythingBack() {
        val typed = "@lang whatsapp german; duolingo spanish; @guard signal off"
        val preview = chips(typed).single()
        preview.onTap()
        assertEquals(listOf(typed to ""), replaced)
        assertEquals(mapOf(whatsapp.packageName to stored(german), duolingo.packageName to stored(spanish)), CommandAppSettings.languages(latinIME))
        assertEquals(mapOf(signal.packageName to false), CommandAppSettings.guards(latinIME))
        assertFalse(CommandAppSettings.guardAllows(latinIME, signal.packageName))
        assertTrue(CommandAppSettings.guardAllows(latinIME, whatsapp.packageName))
        // a second tap does nothing
        preview.onTap()
        assertEquals(1, replaced.size)
        // "Undo" where the command was, for a few seconds
        val undo = CommandSystem.undoChips(host).single()
        assertEquals(string(R.string.agent_unified_undo), undo.label.toString())
        assertEquals(CommandSystem.UNDO_MILLIS, scheduled.single().first)
        undo.onTap()
        assertTrue(CommandAppSettings.languages(latinIME).isEmpty())
        assertTrue(CommandAppSettings.guards(latinIME).isEmpty())
        assertEquals(string(R.string.agent_unified_undone), ShadowToast.getTextOfLatestToast())
        assertEquals(1, refreshed)
        assertTrue(CommandSystem.undoChips(host).isEmpty())
        undo.onTap() // once only
        assertTrue(CommandAppSettings.languages(latinIME).isEmpty())
        // the time runs out: no undo, and the strip is built again
        chips("@lang whatsapp german").single().onTap()
        assertEquals(1, CommandSystem.undoChips(host).size)
        cursor += 1 // the cursor moved: no undo there
        assertTrue(CommandSystem.undoChips(host).isEmpty())
        cursor -= 1
        assertEquals(1, CommandSystem.undoChips(host).size)
        now += CommandSystem.UNDO_MILLIS
        scheduled.last().second()
        assertEquals(2, refreshed)
        assertTrue(CommandSystem.undoChips(host).isEmpty())
        assertEquals(stored(german), CommandAppSettings.language(latinIME, whatsapp.packageName)) // still set
        // a field that changed in between: nothing runs
        canReplace = false
        chips("@lang duolingo german").single().onTap()
        assertNull(CommandAppSettings.language(latinIME, duolingo.packageName))
    }

    @Test fun enterRunsOnlyExactlyOneHarmlessReading() {
        assertEquals(true, enter("@lang whatsapp german"))
        assertEquals(listOf("@lang whatsapp german" to ""), replaced)
        assertEquals(stored(german), CommandAppSettings.language(latinIME, whatsapp.packageName))
        // deleting, all apps, two readings, switching learning on: Enter only says to tap
        for (typed in listOf("@lang whatsapp del", "@lang all german", "@lang whatsap spanish", "@mem whatsapp on",
                "@guard all off", "@clip on", "@lang whatsapp", "@lang ?", "@guard banking off")) {
            ShadowToast.reset()
            assertEquals(true, enter(typed), typed)
            assertEquals(string(R.string.agent_unified_tap_to_run), ShadowToast.getTextOfLatestToast(), typed)
        }
        assertEquals(1, replaced.size)
        assertEquals(stored(german), CommandAppSettings.language(latinIME, whatsapp.packageName))
        assertEquals(2, chips("@lang whatsap spanish").size) // both apps, as two chips
        assertFalse(Memory.isExcluded(latinIME, whatsapp.packageName))
        assertTrue(latinIME.prefs().getBoolean(AgentPreferences.FORMAT_GUARD, true))
        assertEquals(true, enter("@mem whatsapp off"))
        assertTrue(Memory.isExcluded(latinIME, whatsapp.packageName))
        // a gesture still being processed: the key is taken, nothing runs yet
        canReplace = false
        assertEquals(true, enter("@guard signal off"))
        assertNull(CommandAppSettings.guard(latinIME, signal.packageName))
        canReplace = true
        // the old forms: a language word with exactly one chip switches; the bare command leaves Enter alone
        assertEquals(true, enter("@deutsch"))
        assertEquals(listOf(german), switched)
        assertEquals(false, enter("@lang"))
        assertEquals(false, enter("@snip"))
        assertEquals(false, enter("@clip"))
    }

    @Test fun aLanguagePerAppIsReadAtTheFieldStart() {
        val settings = Settings.getInstance()
        chips("@lang whatsapp spanish").single().onTap()
        // "Sprache pro App" is off (the default): the command's language still applies
        assertFalse(latinIME.prefs().getBoolean(Settings.PREF_SAVE_SUBTYPE_PER_APP, false))
        assertEquals(spanish, CommandAppSettings.subtypeForApp(latinIME, settings, whatsapp.packageName)?.rawSubtype)
        assertNull(CommandAppSettings.subtypeForApp(latinIME, settings, duolingo.packageName))
        // with the switch on HeliBoard's record of the app comes first; a command removes it, so the latest choice wins
        latinIME.prefs().edit().putBoolean(Settings.PREF_SAVE_SUBTYPE_PER_APP, true).commit()
        CommandAppSettings.setSavedSubtype(latinIME, whatsapp.packageName, stored(german))
        assertEquals(german, CommandAppSettings.subtypeForApp(latinIME, settings, whatsapp.packageName)?.rawSubtype)
        chips("@sprache whatsapp englisch").single().onTap()
        assertNull(CommandAppSettings.savedSubtype(latinIME, whatsapp.packageName))
        assertEquals(english, CommandAppSettings.subtypeForApp(latinIME, settings, whatsapp.packageName)?.rawSubtype)
        latinIME.prefs().edit().putBoolean(Settings.PREF_SAVE_SUBTYPE_PER_APP, false).commit()
        // the app typed in switches at once
        switched.clear()
        editor = field(chat.packageName)
        chips("@lang chat spanish").single().onTap()
        assertEquals(listOf(spanish), switched)
        // all apps: one language for every app, the own ones go
        chips("@lang all german").single().onTap()
        assertEquals(mapOf(CommandAppSettings.ALL to stored(german)), CommandAppSettings.languages(latinIME))
        assertEquals(german, CommandAppSettings.subtypeForApp(latinIME, settings, duolingo.packageName)?.rawSubtype)
        CommandSystem.undoChips(host).single().onTap()
        assertEquals(english, CommandAppSettings.subtypeForApp(latinIME, settings, whatsapp.packageName)?.rawSubtype)
        assertEquals(spanish, CommandAppSettings.subtypeForApp(latinIME, settings, chat.packageName)?.rawSubtype)
        assertNull(CommandAppSettings.language(latinIME, CommandAppSettings.ALL))
        // show, list and delete
        assertEquals("WhatsApp: ${label(english)}", labels("@lang whatsapp").first())
        assertEquals(setOf("Chat: ${label(spanish)}", "WhatsApp: ${label(english)}"), labels("@lang apps").toSet())
        chips("@lang whatsapp del").single().onTap()
        assertNull(CommandAppSettings.language(latinIME, whatsapp.packageName))
        // a language switched off since: nothing for that app
        LanguageCommand.subtypes = { listOf(german, english) }
        KeyboardCommands.load(latinIME)
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, chips("@lang duolingo spanish").single().kind)
    }

    @Test fun formatGuardAndMemoryPerApp() {
        chips("@guard all off").single().onTap()
        assertFalse(latinIME.prefs().getBoolean(AgentPreferences.FORMAT_GUARD, true))
        // an app's own choice comes before the switch
        chips("@wächter signal an").single().onTap()
        assertTrue(CommandAppSettings.guardAllows(latinIME, signal.packageName))
        assertFalse(CommandAppSettings.guardAllows(latinIME, whatsapp.packageName))
        chips("@guard signal del").single().onTap()
        assertFalse(CommandAppSettings.guardAllows(latinIME, signal.packageName))
        // without an app the app typed in
        editor = field(whatsapp.packageName)
        chips("@guard on").single().onTap()
        assertEquals(mapOf(whatsapp.packageName to true), CommandAppSettings.guards(latinIME))
        assertTrue(labels("@guard list").any { it.startsWith("WhatsApp: ") })
        // the memory's own excluded apps
        chips("@gedächtnis duolingo aus").single().onTap()
        assertTrue(Memory.isExcluded(latinIME, duolingo.packageName))
        chips("@mem duolingo on").single().onTap()
        assertFalse(Memory.isExcluded(latinIME, duolingo.packageName))
        chips("@mem all on").single().onTap()
        assertTrue(Memory.isEnabled(latinIME))
        CommandSystem.undoChips(host).single().onTap()
        assertFalse(Memory.isEnabled(latinIME))
    }

    @Test fun groupsAreMadeUsedChangedAndDeleted() {
        chips("@group messenger whatsapp signal").single().onTap()
        assertEquals(mapOf("messenger" to listOf(whatsapp.packageName, signal.packageName)), CommandAppSettings.groups(latinIME))
        KeyboardCommands.load(latinIME)
        chips("@lang messenger german").single().onTap()
        assertEquals(stored(german), CommandAppSettings.language(latinIME, whatsapp.packageName))
        assertEquals(stored(german), CommandAppSettings.language(latinIME, signal.packageName))
        chips("@gruppe messenger neu duolingo").single().onTap()
        assertEquals(3, CommandAppSettings.groups(latinIME).getValue("messenger").size)
        chips("@group messenger del signal").single().onTap()
        assertEquals(listOf(whatsapp.packageName, duolingo.packageName), CommandAppSettings.groups(latinIME).getValue("messenger"))
        assertEquals(listOf("messenger: WhatsApp, Duolingo"), labels("@group list"))
        chips("@group messenger del").single().onTap()
        assertTrue(CommandAppSettings.groups(latinIME).isEmpty())
        CommandSystem.undoChips(host).single().onTap()
        assertEquals(2, CommandAppSettings.groups(latinIME).getValue("messenger").size)
        CommandAppSettings.setGroup(latinIME, "messenger", null)
        assertEquals(listOf(string(R.string.agent_unified_no_groups)), labels("@group list"))
    }

    @Test fun snippetsAreAddedAndDeletedByCommand() {
        val add = chips("@snip add gruss Viele Grüße; bis dann").single()
        assertTrue("„Viele Grüße; bis dann“" in add.label.toString(), add.label.toString())
        add.onTap()
        assertEquals("Viele Grüße; bis dann", SnippetStore(latinIME) { true }.read().single().text)
        // the old "@snip name" inserts it
        assertTrue(labels("@snip gru").single().startsWith("gruss: "))
        // replacing and deleting need the tap; undo brings it back
        assertEquals(true, enter("@snip del gruss"))
        assertEquals(1, SnippetStore(latinIME) { true }.read().size)
        chips("@baustein weg gruss").single().onTap()
        assertTrue(SnippetStore(latinIME) { true }.read().isEmpty())
        CommandSystem.undoChips(host).single().onTap()
        assertEquals("Viele Grüße; bis dann", SnippetStore(latinIME) { true }.read().single().text)
        // a new snippet runs on Enter
        assertEquals(true, enter("@snip neu tschuess Tschüss!"))
        assertEquals(2, SnippetStore(latinIME) { true }.read().size)
    }

    @Test fun theClipboardHistorySwitchByCommand() {
        assertFalse(ClipCommand.historyEnabled(latinIME))
        chips("@clip on").first().onTap()
        assertTrue(ClipCommand.historyEnabled(latinIME))
        chips("@ablage aus").first().onTap()
        assertFalse(ClipCommand.historyEnabled(latinIME))
        // a search word stays the old search
        assertEquals(listOf(string(R.string.agent_command_clip_enable)), labels("@clip Termin"))
    }

    @Test fun helpListsTheAreasAndTheSlotsOfOne() {
        val help = find("Hi @?") as CommandNames
        val chips = KeyboardCommands.chipsFor(help, host)
        assertEquals(listOf("@lang", "@snip", "@clip", "@guard", "@mem", "@group"), chips.map { it.label.toString().substringBefore(' ') })
        assertEquals(colour(Role.AREA), colourOf(chips.first().label, "@lang"))
        chips.first().onTap()
        assertEquals(listOf("@?" to "@lang "), replaced)
        // "@lang ?": how it is written, then what is set
        val syntax = chips("@lang ?").first()
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, syntax.kind)
        assertEquals(colour(Role.TARGET), colourOf(syntax.label, string(R.string.agent_unified_slot_targets)))
        assertEquals(colour(Role.VALUE), colourOf(syntax.label, string(R.string.agent_unified_slot_language)))
        assertEquals(string(R.string.agent_unified_nothing_set), labels("@lang ?")[1])
        // slot suggestions: apps while one is typed, languages after it
        val apps = chips("@lang whats").filter { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE }
        assertEquals(listOf("WhatsApp", "WhatsApp Business"), apps.map { it.label.toString() })
        apps.first().onTap()
        assertEquals("@lang whats" to "@lang whatsapp ", replaced.last())
        // after the app: what it has now, then the languages to choose
        assertEquals(listOf("WhatsApp: ${string(R.string.agent_unified_none)}", "Deutsch", "English", "Español"), labels("@lang whatsapp "))
        val on = string(R.string.agent_unified_on)
        assertEquals(listOf("${string(R.string.agent_unified_main_switch)}: $on", "Signal: $on", "on", "off"), labels("@guard signal "))
    }

    @Test fun theOldSyntaxKeepsItsChips() {
        assertEquals(listOf("🌐 Deutsch (DE)", "🌐 Español (ES)"), labels("@lang")) // English is in use
        assertEquals(labels("@lang"), labels("@sprache"))
        assertEquals(listOf("🌐 Deutsch (DE)"), labels("@deutsch"))
        assertEquals(listOf("🌐 Español (ES)"), labels("@lang spanisch"))
        assertTrue(labels("@lang xyz").isEmpty())
        // the German second names are command words and protect their words
        for (word in listOf("@sprache", "@wächter", "@waechter", "@gedächtnis", "@gruppe", "@baustein", "@ablage"))
            assertTrue(KeyboardCommands.isCommandWord(word), word)
        assertEquals("guard", call("@wächter signal aus").name)
        assertTrue(KeyboardCommands.guardsTextBefore("@lang whatsapp germ"))
        // the bare prefix lists the new areas, not their German names
        val all = (find("@") as CommandNames).candidates
        assertTrue(all.containsAll(listOf("guard", "mem", "group")))
        assertTrue(listOf("sprache", "gruppe", "wachter").none { it in all })
        // other commands keep their argument: an area word inside it is no chain
        assertEquals("lang", call("@ki frag @lang whatsapp").name) // the later command wins, as before
        assertEquals("@lang whatsapp", call("@ki frag @lang whatsapp").text)
    }

    @Test fun nothingInProtectedFields() {
        editor = field("com.whatsapp", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertNull(find("@lang whatsapp german"))
        assertNull(find("@?"))
        val typed = CommandCall(0, "@lang whatsapp german", '@', "lang", "whatsapp german")
        assertEquals(emptyList(), CommandSystem.chips(host, typed))
        assertNull(CommandSystem.onEnter(host, typed))
        editor = field("com.whatsapp")
        chips("@lang whatsapp german").single().onTap()
        editor = field("com.whatsapp", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertTrue(CommandSystem.undoChips(host).isEmpty())
        // incognito
        editor = field("com.whatsapp")
        fun loadSettings() = Settings.getInstance().loadSettings(latinIME, java.util.Locale.GERMANY,
            helium314.keyboard.latin.InputAttributes(editor, false, latinIME.packageName))
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        loadSettings()
        try {
            assertNull(find("@guard signal off"))
            assertEquals(emptyList(), CommandSystem.chips(host, CommandCall(0, "@guard signal off", '@', "guard", "signal off")))
            assertTrue(CommandSystem.undoChips(host).isEmpty())
        } finally {
            latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
            loadSettings()
        }
        // switched off: no command and no command word
        KeyboardCommands.setCommandEnabled(latinIME, GuardCommand.name, false)
        assertNull(find("@guard signal off"))
        assertFalse(KeyboardCommands.isCommandWord("@wächter"))
        assertFalse("guard" in (find("@?") as CommandNames).candidates)
    }
}
