// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.Locale
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class UrlCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val text = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    /** Everything that happens, in order: "start …", "replace …", "show …". */
    private val events = mutableListOf<String>()
    private val started = mutableListOf<Intent>()
    private var startFailure: RuntimeException? = null
    private var before: String? = null
    private var after: String? = null
    private var clip: String? = null
    private var fieldReads = 0
    private var clipReads = 0
    private val shown = mutableListOf<List<KeyboardCommands.Chip>>()

    private val screen = object : ScreenTextReader {
        override var isAvailable = false
        var reads = 0
        var result: List<CharSequence>? = emptyList()
        override fun read(onResult: (List<CharSequence>?) -> Unit) { reads++; onResult(result) }
    }

    private val context = object : ContextWrapper(latinIME) {
        override fun startActivity(intent: Intent) {
            startFailure?.let { throw it }
            started += intent
            events += "start ${intent.dataString ?: intent.component?.className}"
        }
    }

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = this@UrlCommandTest.context
        override var editorInfo: EditorInfo? = text
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            events += "replace ${command.text} -> $replacement"
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override var cursor = -1
        override fun textBeforeCursor(length: Int): CharSequence? { fieldReads++; return before?.takeLast(length) }
        override fun textAfterCursor(length: Int): CharSequence? { fieldReads++; return after?.take(length) }
        override fun showChips(chips: List<KeyboardCommands.Chip>): Boolean {
            shown += chips
            events += "show ${chips.size}"
            return true
        }
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        load(text)
        UrlCommand.clipboard = { clipReads++; clip }
        UrlCommand.screen = screen
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    @After fun tearDown() {
        UrlCommand.clipboard = UrlCommand::readClipboard
        UrlCommand.screen = ScreenReaderService
        latinIME.prefs().edit().clear().commit()
        load(EditorInfo())
        KeyboardCommands.load(latinIME)
    }

    private fun load(editorInfo: EditorInfo) =
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editorInfo, false, latinIME.packageName))

    private fun call(typed: String, editor: EditorInfo = text) =
        KeyboardCommands.find(typed, Settings.getValues(), editor) as CommandCall

    private fun chips(typed: String) = KeyboardCommands.chipsFor(call(typed), host)

    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    @Test fun isBuiltInAndItsArgumentIsNotCorrected() {
        assertTrue(UrlCommand in KeyboardCommands.BUILT_IN)
        assertTrue(KeyboardCommands.guardsTextBefore("Lies @url heise.de"))
        assertTrue(KeyboardCommands.isCommandWord("@url"))
        assertEquals(latinIME.getString(R.string.agent_command_url, "@"), UrlCommand.description(latinIME))
    }

    @Test fun typedAddressOpensTheBrowserBeforeTheCommandGoes() {
        val chips = chips("Lies @url heise.de")
        assertEquals(listOf(latinIME.getString(R.string.agent_command_url_open, "https://heise.de")), labels(chips))
        assertTrue(events.isEmpty()) // nothing happens before the tap
        assertEquals(0, fieldReads + clipReads) // a typed address reads neither field nor clipboard
        chips.single().onTap()
        assertEquals(listOf("start https://heise.de", "replace @url heise.de -> "), events)
        val intent = started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("https://heise.de"), intent.data)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(intent.component)

        assertEquals("start http://example.com/a?b=1", chips("@url http://example.com/a?b=1").single().let { it.onTap(); events[2] })
    }

    @Test fun aFailedStartLeavesTheField() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            startFailure = failure
            chips("@url heise.de").single().onTap()
        }
        assertTrue(events.isEmpty())
    }

    @Test fun onlyRealAddressesGetAChip() {
        for (argument in listOf("foto.jpg", "bericht.pdf", "1.2.3", "a.b", "hei", "javascript:alert(1)", "file:///sdcard/x",
            "heise.de und mehr", "fertig.Dann"))
            assertTrue(chips("@url $argument").isEmpty(), argument)
        assertTrue(events.isEmpty())
    }

    @Test fun withoutArgumentSuggestsClipboardThenFieldAddresses() {
        clip = "Kopiert: https://t.co/x und foto.jpg"
        before = "Siehe heise.de und example.com/a, nicht bericht.pdf @url"
        after = " danach www.spiegel.de. Und heise.de nochmal"
        val chips = chips(before!!)
        assertEquals(listOf("t.co/x", "↗", "example.com/a", "↗", "heise.de", "↗", "www.spiegel.de", "↗",
            latinIME.getString(R.string.agent_command_url_read_screen_off)), labels(chips))
        assertEquals(latinIME.getString(R.string.agent_command_url_insert, "https://t.co/x"), chips[0].description)
        assertEquals(latinIME.getString(R.string.agent_command_url_open_short, "https://t.co/x"), chips[1].description)
        assertTrue(events.isEmpty())

        chips[0].onTap() // replaces exactly the command text, once
        assertEquals(listOf("replace @url -> https://t.co/x"), events)
        assertTrue(started.isEmpty())
        chips[5].onTap() // the small chip opens, then removes the command
        assertEquals(listOf("start https://heise.de", "replace @url -> "), events.drop(1))
    }

    @Test fun commandTextItselfAndAFieldWithoutAddressesGiveOnlyTheScreenChip() {
        before = "Hallo @url"
        after = ""
        assertEquals(listOf(latinIME.getString(R.string.agent_command_url_read_screen_off)), labels(chips("Hallo @url")))
        before = null; after = null; clip = null
        assertEquals(1, chips("@url").size)
        // a space after the name is the same command without argument
        assertEquals(1, chips("@url ").size)
    }

    @Test fun suggestionsAreBounded() {
        before = (1..20).joinToString(" ") { "seite$it.de" } + " @url"
        assertEquals(UrlCommand.MAX_URLS, UrlCommand.suggestions(host, call(before!!)).size)
        assertEquals("https://seite20.de", UrlCommand.suggestions(host, call(before!!)).first()) // nearest first
    }

    @Test fun screenIsReadOnlyAfterATapAndOncePerTap() {
        screen.isAvailable = true
        clip = "heise.de"
        val chips = chips("@url")
        assertEquals(listOf("heise.de", "↗", latinIME.getString(R.string.agent_command_url_read_screen)), labels(chips))
        assertEquals(0, screen.reads) // showing the chips reads nothing from the screen

        screen.result = listOf("Besuche https://news.example.org/x heute", "foto.jpg", "1.2.3")
        chips.last().onTap()
        assertEquals(1, screen.reads)
        val next = shown.single()
        assertEquals(listOf("news.example.org/x", "↗", "heise.de", "↗"), labels(next))
        assertTrue(events.none { it.startsWith("replace") })
        next[0].onTap()
        assertEquals("replace @url -> https://news.example.org/x", events.last())

        screen.result = listOf("kein Link hier")
        chips.last().onTap()
        screen.result = null // the service went away
        chips.last().onTap()
        assertEquals(3, screen.reads)
        assertEquals(1, shown.size) // nothing found, nothing shown
    }

    @Test fun suggestionsAreReadOncePerCommandAtOneCursor() {
        clip = "heise.de"; before = "Siehe example.com @url"; after = ""
        host.cursor = 22
        chips("Siehe example.com @url")
        chips("Siehe example.com @url") // a strip update for the same command
        assertEquals(1, clipReads)
        assertEquals(2, fieldReads)
        host.cursor = 30 // the same text elsewhere: read again
        chips("Siehe example.com @url")
        assertEquals(2, clipReads)
        KeyboardCommands.forget() // the command is gone: nothing is kept
        host.cursor = 22
        chips("Siehe example.com @url")
        assertEquals(3, clipReads)
    }

    @Test fun aScreenReadDoesNotRepeatAddressesTheRowAlreadyOffers() {
        screen.isAvailable = true
        clip = "heise.de"
        val chips = chips("@url")
        screen.result = listOf("heise.de und https://news.example.org/x", "noch einmal heise.de")
        chips.last().onTap()
        assertEquals(listOf("news.example.org/x", "↗", "heise.de", "↗"), labels(shown.single()))
        screen.result = listOf("nur heise.de") // nothing new on the screen
        chips.last().onTap()
        assertEquals(1, shown.size)
        assertEquals(latinIME.getString(R.string.agent_command_url_screen_none), ShadowToast.getTextOfLatestToast())
    }

    @Test fun switchedOffScreenReaderOpensTheCommandsPage() {
        val chip = chips("@url").last()
        assertEquals(latinIME.getString(R.string.agent_command_url_read_screen_off), chip.label)
        chip.onTap()
        val intent = started.single()
        assertEquals(SettingsActivity::class.java.name, intent.component?.className)
        assertEquals(AgentPreferences.COMMANDS_DESTINATION, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(AgentPreferences.COMMANDS_DESTINATION in AgentPreferences.SETTINGS_DESTINATIONS)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0) // P39
        assertEquals(0, screen.reads)
        assertTrue(events.none { it.startsWith("replace") })
    }

    @Test fun nothingIsReadInPasswordFieldsIncognitoOrWithoutSettings() {
        clip = "heise.de"; before = "heise.de @url"; after = "heise.de"
        screen.isAvailable = true
        val bare = CommandCall(0, "@url", '@', "url", "")
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        load(password)
        assertNull(KeyboardCommands.find("@url", Settings.getValues(), password))
        host.editorInfo = password
        assertTrue(UrlCommand.chips(host, bare).isEmpty())
        assertEquals(0, fieldReads + clipReads)
        host.editorInfo = text
        load(text)
        val screenChip = UrlCommand.chips(host, bare).last()
        assertEquals(latinIME.getString(R.string.agent_command_url_read_screen), screenChip.label)
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        load(text)
        assertNull(KeyboardCommands.find("@url", Settings.getValues(), text))
        val readsBefore = fieldReads + clipReads
        assertTrue(UrlCommand.chips(host, CommandCall(0, "@url", '@', "url", "")).isEmpty())
        assertTrue(UrlCommand.chips(host, CommandCall(0, "@url heise.de", '@', "url", "heise.de")).isEmpty())
        screenChip.onTap() // a chip from before incognito does not read either
        assertEquals(readsBefore, fieldReads + clipReads)
        assertEquals(0, screen.reads)
        assertTrue(KeyboardCommands.isBlocked(null, text)) // no settings, e.g. locked before the first unlock
    }

    @Test fun clipboardGivesOnlyNonSensitiveText() {
        val manager = latinIME.getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("", "Link: heise.de"))
        assertEquals("Link: heise.de", UrlCommand.readClipboard(latinIME).toString())

        val sensitive = ClipData.newPlainText("", "https://bank.example.com/reset?token=1")
        sensitive.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        manager.setPrimaryClip(sensitive)
        assertNull(UrlCommand.readClipboard(latinIME))

        manager.setPrimaryClip(ClipData(ClipDescription("Bild", arrayOf("image/png")), ClipData.Item(Uri.parse("content://media/external/images/1"))))
        assertNull(UrlCommand.readClipboard(latinIME))
        // a text clip whose item is a URI is never resolved
        manager.setPrimaryClip(ClipData(ClipDescription("Link", arrayOf(ClipDescription.MIMETYPE_TEXT_URILIST)),
            ClipData.Item(Uri.parse("content://example/heise.de"))))
        assertNull(UrlCommand.readClipboard(latinIME))

        manager.setPrimaryClip(ClipData.newPlainText("", "x".repeat(UrlCommand.FIELD_CHARS + 50)))
        assertEquals(UrlCommand.FIELD_CHARS, UrlCommand.readClipboard(latinIME)!!.length)
    }
}
